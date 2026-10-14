# 새 주제 경로의 무JavaScript 404 수정 — 2026-10-15

## 결과

`/community/topics/unknown`에서 HTTP 404를 유지하면서 JavaScript 없이도 제목·설명·전체 커뮤니티 링크가 실제 본문에 표시된다. 새로고침과 링크 클릭·키보드 Enter 복귀도 확인했다. **기존 게시글 무JS 404 2건은 재검증 결과 여전히 실패**한다. 두 결과를 혼동하지 않는다.

[프로덕션 무JS PC 캡처](community-topics/2026-10-15-nojs-404/topic-404-nojs-1440.png) · [무JS 모바일 캡처](community-topics/2026-10-15-nojs-404/topic-404-nojs-390.png) · [수정 전 빈 본문](community-topics/2026-10-15-nojs-404/before-nojs-1440.png)

## 확인한 원인과 범위

설치된 Next.js **16.3.6**, 현재 `htmlLimitedBots: /.*/` 설정의 프로덕션 렌더 경로에서, `TopicPage()`가 없는 slug에 `notFound()`를 던지면 HTML 렌더 오류 복구 경로로 들어간다. 그 경로는 상태를 404로 설정하지만 `getErrorRSCPayload()`가 만드는 초기 seed는 `<html id="__next_error__"><head/><body/></html>` 형태다. 원래 컴포넌트 트리의 오류 안내는 Flight 데이터로 전달되어 JS 실행 후 클라이언트 오류 경계에서 표시된다. 따라서 RSC에 제목 문자열이 있어도 표시 HTML은 비어 있었다.

근거는 설치 패키지의 다음 구현과 실제 응답/DOM이다.

- `node_modules/next/dist/server/app-render/app-render.js`: `renderToStream`의 `errorRecovery classification`에서 HTTP fallback 오류의 status를 설정한다. `getErrorRSCPayload()`의 `seedData`는 빈 body의 `__next_error__` 문서를 만든다.
- `node_modules/next/dist/client/components/http-access-fallback/error-boundary.js`: 클라이언트 오류 경계가 `triggeredStatus`에 대응하는 not-found 컴포넌트를 렌더한다.
- `node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/not-found.md`, `04-functions/not-found.md`: notFound는 렌더 경로에서 오류를 던지는 방식이다.
- 같은 docs의 `loading.md` **Status Codes**: 정확한 404가 필요하면 스트리밍 전에 존재 여부를 판정하고, proxy에서 없는 slug를 안내 경로로 rewrite하거나 404 응답을 제공할 수 있다고 안내한다. `proxy.md`는 matcher로 범위를 제한하도록 안내한다.

이는 모든 Next 버전/설정에서 동일하다고 추정한 결론이 아니다. 이번 설치 버전과 아래 재현 조건에서 확인한 경로다. 공통 Provider나 AppLink를 원인으로 단정하지 않았다.

| 실험 (환경파일 없는 임시 복사본) | HTTP | 무JS 표시 | JS 표시 |
|---|---:|---|---|
| 현재 코드 기준 | 404 | body 비어 있음 | 정상 안내 |
| 안내의 AppLink만 기본 `<a>`로 변경 | 404 | 동일 실패 | 정상 안내 |
| 해당 세그먼트에 children만 반환하는 layout 추가 | 404 | 동일 실패 | 정상 안내 |
| Provider·AppLink·스타일·API가 없는 최소 앱: async params + notFound + 기본 앵커 | 404 | `__next_error__` 빈 body 재현 | 원인 격리 목적, JS 별도 검사는 하지 않음 |
| 렌더 전 판정 + 별도 안내 경로 | 404 | 실제 안내/링크 표시 | 실제 안내/링크 표시 |

최소 앱은 동일 Next 의존성으로 Webpack 빌드했다. 외부 node_modules 심볼릭 링크를 사용하는 독립 재현 디렉터리가 Turbopack의 root 제한에 걸렸기 때문이다. 원래 서비스 실험과 최종 검증은 기존 Turbopack 프로덕션 빌드다. 최소 재현의 핵심은 다음과 같다.

```tsx
// root layout: <html><body>{children}</body></html>; Provider 없음
import { notFound } from "next/navigation"
export default async function Page({ params }: { params: Promise<{ slug: string }> }) {
  if ((await params).slug !== "valid") notFound()
  return <h1>Valid topic</h1>
}
// 같은 세그먼트 not-found: <main><h1>Minimal missing topic</h1><a href="/topics/valid">Return</a></main>
```

사전 판정 실험에서 안내 목적지를 주제 matcher 내부에 둔 변형은 요청이 완료되지 않았다. 시간 제한을 늘려 통과시키지 않았으며 채택하지 않았다. 최종 안내 경로는 주제 slug 판정과 분리하고 직접 진입도 별도 분기로 처리한다. 이 중간 실험의 무한 재판정 여부까지 확정한 것은 아니다. 모든 중간 변경은 임시 복사본에만 적용했으며 최종 서비스에 남기지 않았다.

## 최소 수정과 필요한 이유

이번 런타임 변경은 새 파일 **두 개**다. 기존 7개 주제와 미커밋 UI 작업을 보존했다.

1. `web/src/proxy.ts`의 `proxy()`
   - matcher는 `/community/topics/:slug`와 안내 경로 `/community/topic-not-found`뿐이다. 홈·인증·게시글 상세/API에 적용하지 않는다.
   - `topicBySlug()`를 재사용한다. 주제 목록을 복제하지 않고 API/DB/세션 조회도 하지 않는다.
   - 알려진 slug는 `NextResponse.next()`로 기존 페이지에 맡긴다. topic 쿼리 충돌 정규화도 기존 `TopicPage()`가 수행한다.
   - 없는 slug만 `/community/topic-not-found`로 **내부 rewrite + status 404** 한다. 주소창은 원래 잘못된 주소를 유지하고 redirect Location은 없다.
   - 안내 경로를 직접 요청해도 `NextResponse.next({status:404})`를 사용한다. 오류 화면이 200으로 제공되는 우회가 없다.
2. `web/src/app/community/topic-not-found/page.tsx`
   - 기존 `topics/[slug]/not-found.tsx`의 `TopicNotFound`를 그대로 재사용한다. 별도 안내 HTML 복제나 JS 삽입이 아니다.
   - 서버가 정상적으로 안내 컴포넌트를 한 번 렌더하고, HTTP 404는 앞의 판정에서 이미 설정한다. 전용 title·noindex를 제공한다.

상위 layout·Provider·AppLink·TopicPage의 방어용 notFound·공통 스타일·검색 차단 설정은 변경하지 않았다. 기본 앵커로 바꾸는 것만으로는 실패했으므로 기존 AppLink를 불필요하게 교체하지 않았다. AppLink도 정상 서버 렌더 경로에서는 기본 `<a href="/community">`를 내보내므로 무JS 클릭이 가능하다. 정상 화면을 중복 렌더하거나 숨겨진 Next 내부 영역을 CSS로 노출하지 않았다.

Proxy를 추가한 이유는 현재 렌더 중 예외 복구 경로에서는 화면 변경만으로 HTTP404와 표시 HTML을 동시에 얻지 못했기 때문이다. Next 문서가 제시한 렌더 전 존재 판정을 **고정 주제 경로에만** 적용했다. 범용 라우팅·인증 middleware로 확장하지 않는다. Next 자체의 예외 복구 구현을 패치한 것은 아니다.

## 회귀 검사

- `community-topics-browser.mjs`: 기존 무JS 실패를 참고 기록만 남기는 방식에서 **제목·설명·링크의 isVisible, 실제 링크 클릭 후 전체 목록 표시**를 모두 assert하도록 강화했다. 상태 코드만 확인하거나 skip하지 않는다. JS 새로고침의 404도 확인한다. 모의 API route matcher는 문자열 glob 대신 URL origin/pathname 정규식으로 목록과 상세를 명시적으로 구분한다.
- `community-topic-404-browser.mjs` 추가: 프로덕션과 dev에서 1440/390px × JS on/off, 새로고침, 원래 URL 유지, Location 부재, noindex, Tab/Enter 복귀, 가로 넘침, 크롤러 UA, 안내 경로 직접 진입, 정상 주제 초기 HTML/실제 게시글, 충돌 쿼리를 검사한다.
- 기존 `community-404-browser.mjs`는 변경 없이 다시 실행하여 게시글의 두 실패를 확인했다.

| 직접 실행 | 결과 |
|---|---|
| pnpm test | 174 통과, 실패/skip 0 |
| pnpm typecheck, pnpm lint, git diff --check | 통과 |
| pnpm build | 환경파일 없는 임시 복사본, 기존 Turbopack 프로덕션 빌드 통과 |
| 새 404 전용 브라우저 — 프로덕션 | 7 통과, 실제 Spring 공개 조회 기반 |
| 새 404 전용 브라우저 — 기존 8443 dev | 7 통과, 서버 재시작 없이 반영 확인 |
| 주제 브라우저 — 프로덕션 + 모의 공개/인증 API | 23 통과, **무JS 필수 검사 포함**, 종료 코드 0 |
| 기존 커뮤니티 브라우저 — 프로덕션 + 모의 API | 49 통과, 종료 코드 0; 작성·이탈 승인·세션 만료 회귀 포함 |
| 기존 게시글 404 — 프로덕션 + 실제 공개 조회 | 5 통과, **무JS 제목/복귀 링크 2 실패**(일반 브라우저·크롤러) |

새 테스트의 실제 API 검증은 기존 데이터 공개 GET뿐이며 회원/게시글 생성·변경은 **0건**이다. 작성·로그인 복귀·지연 응답 보호는 모의 회귀로 구분한다. 별도 실제 로그인/작성 POST, 전체 백엔드 테스트, 전체 인증 UI 38개 재검사, 배포 환경 검증은 실행하지 않았다. 공통 Provider/인증 코드를 변경하지 않았다.

실행 예시(기존 서버를 종료하지 않고 별도 빌드/포트를 사용):

```sh
pnpm test
pnpm typecheck
pnpm lint
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8082 pnpm exec next start --hostname 127.0.0.1 --port 8451
BASE_URL=http://127.0.0.1:8451 node tests/community-topic-404-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/community-404-browser.mjs
# 모의 검사는 같은 빌드를 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856으로 실행 후 순서대로
BASE_URL=http://127.0.0.1:8451 COMMUNITY_SSR_MOCK_PORT=8856 node tests/community-topics-browser.mjs
BASE_URL=http://127.0.0.1:8451 COMMUNITY_SSR_MOCK_PORT=8856 node tests/community-browser.mjs
```

Playwright는 기존 외부 런타임을 `PLAYWRIGHT_MODULE`로 지정했다. 새 의존성을 설치하지 않았다. 캡처/JSON 출력은 `COMMUNITY_404_ARTIFACT_DIR` 또는 `COMMUNITY_ARTIFACT_DIR`로 지정한다. 결과와 캡처는 [자료 폴더](community-topics/2026-10-15-nojs-404/)에 있다. 이전 무JS 실패 JSON은 역사 기록으로 보존하며 새 결과로 덮어쓰지 않았다.

## 작업 상태

브랜치 `feat/community-topics`, HEAD `919e891f1a776d7f1be6fabf908d9dba3e28f656` 유지. 시작 시 기존 미커밋/미추적 주제 구현이 있었고 그대로 보존했다. 커밋·push·PR·병합·배포 없음. 검증용 8451·8452 및 테스트가 연 8856은 종료했고 최종 LISTEN 검사에서 해당 포트가 없음을 확인했다. 기존 8443(Node PID 97959)·8082(Spring PID 33858)는 실행 중이며 유지했다.
