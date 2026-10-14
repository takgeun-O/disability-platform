# 커뮤니티 코드 리뷰 반영 (2026-10-14)

기준: 로컬 `feat/community-posts-ui`, HEAD `88650cf1443f635bc0d36e9bd20c83c607576c6f`. 기존 미커밋/미추적 커뮤니티 구현과 이전 캡처를 보존한 후 네 가지 개선만 반영했다. stage/commit/push/PR/배포 없음. Spring, 인증 상태/CSRF/CORS, 작성 폼/이탈 보호 코드는 변경하지 않았다.

이 문서는 [최초 구현 기록](Community-API-Integration-2026-10-14.md)의 후속 기록이다. 최초 기록의 CSR 초기 로딩·모바일 베스트 카드 설명은 이번 변경 전 상태이며 아래 내용이 최신이다.

## 원인과 수정

1. **스크롤·초점**: 이전 `router.push(..., {scroll:false})`는 새 조회 성공 후 위치를 바꾸지 않았고, 복귀용 스크롤 저장값과 일반 조회를 구분하지 않았다. 목록 안의 필터/페이지 변경은 Next와 연동되는 `history.pushState`로 URL을 바꾸고 클라이언트 GET 한 번으로 처리한다. 이전 목록/조건은 성공할 때까지 유지한다. 마지막 요청의 성공에만 목록 제목으로 `focus({preventScroll:true})`와 즉시 스크롤을 적용한다. 짧은 페이지는 문서 높이 안에서 제목을 보이게 한다. 제목은 `tabIndex=-1`, 상태 설명과 연결되어 있고 초점선/고정 헤더 여백을 둔다.

   요청 세대 번호·AbortController·취소 가능한 animation frame으로 늦은 응답과 이동을 무효화한다. 같은 목록의 Back/Forward는 history entry별 위치를 기억한다. Next의 history 상태를 지우지 않는다. 상세의 명시적 목록 링크/Back만 복귀 의도를 표시하여 저장된 위치를 복원하고, 주소 직접 입력/초기 진입은 오래된 기록을 소비하지 않는다. 실패 시 이전 결과를 남기고 경고하며 제목으로 스크롤/초점을 강제로 옮기지 않는다.

2. **범위 밖 페이지**: 이전 화면은 빈 items를 모두 첫 게시글 없음으로 취급하고 요청 page를 그대로 총 페이지와 출력했다. `pageIsOutOfRange`로 정상 빈 첫 페이지와 범위 이탈을 구분한다. 페이지 범위를 벗어나거나 마지막 페이지의 글이 사라지면 별도 안내와 **같은 조건의 첫 페이지로** 버튼을 보여준다. 잘못된 페이지 분수와 페이지 번호 버튼은 숨긴다. 모든 글이 사라진 경우에도 page>0은 첫 페이지 확인을 안내하고, page=0은 정상 빈 목록이다. 음수·문자·과대 page 정규화는 유지한다.

3. **최초 HTML·404·metadata**: 이전 목록/상세는 useEffect에서만 조회했다. 두 목록 라우트의 Server Component가 URL 필터를 정규화하고 실제 GET 결과를 `initial`로 전달한다. 상세도 서버에서 제목·작성자·본문을 읽어 HTML에 렌더한다. hydration 직후 같은 API를 다시 요청하지 않으며, 명시적 필터/페이지 조작과 오류 재시도만 브라우저 GET을 수행한다.

   기존 API JSON 검증/오류 변환을 공유한다. HTTP 404이면서 유효한 오류 코드 `POST_NOT_FOUND`인 경우만 Next `notFound()`를 호출한다(형식 자체가 잘못된 ID도 없음 처리). 프록시 HTML 404, 손상 응답, 500, 네트워크 실패/15초 타임아웃은 재시도 가능한 별도 오류다. 공개 GET은 `credentials:omit`; Next 요청의 Cookie/Authorization/CSRF 헤더를 Spring으로 전달하지 않는다. POST와 인증 클라이언트의 세션·CSRF 처리는 유지한다.

   상세별 title, 본문 요약 description(공백 정리 후 160 코드 포인트), Open Graph article/작성자/작성·수정일, Twitter summary를 제공한다. 정해진 사이트 주소가 있을 때만 canonical/공유 URL을 추가하며 `from`은 제외한다. 원래 noindex/nofollow와 robots 차단을 유지한다.

4. **모바일 베스트 카드**: 760px 이하에서 미구현 베스트 카드를 `display:none`으로 숨겼다. 배너 → 필터 → 실제 목록 순서로 이어진다. 데스크톱 오른쪽 배치는 유지했다. 320/390px 글쓰기·필터 버튼, 가로 넘침과 키보드 대화상자를 확인했다.

## 주요 변경 파일

| 파일 (`web/` 기준) | 역할 |
| --- | --- |
| `src/features/PostList.tsx` | 초기 서버 결과, 조회 의도·성공 후 초점/스크롤, 이력 복원, 범위 안내 |
| `src/lib/community-posts.ts` | 명시적 상세 복귀와 새 진입 구분, 스크롤 기록 |
| `src/lib/community-load.ts` | 직렬화 가능한 최초 조회 결과, 정확한 없음/범위 판정 |
| `src/lib/community-api.ts` | 공개 GET의 쿠키 생략과 내부 base 주입; 기존 계약 검사 공유 |
| `src/lib/community-server.ts` | server-only 주소/공개 조회, 안전한 오류 전달, 요청 내 중복 제거 |
| `src/components/community/CommunityListPage.tsx` | 검색 조건을 읽고 실제 최초 목록 조회 |
| `src/app/community/page.tsx`, `src/app/community/posts/page.tsx` | 공통 서버 목록 진입점 |
| `src/app/community/posts/[postId]/page.tsx` | 서버 상세, notFound, generateMetadata |
| `src/app/community/posts/[postId]/not-found.tsx`, `src/components/community/PostNotFound.tsx` | 없음 안내·안전한 필터 복귀 |
| `src/features/PostDetail.tsx` | 초기 상세 결과와 재시도, 명시적 목록 복귀 |
| `src/styles/community.css` | 모바일 베스트 숨김, 목록 제목 초점/스크롤 여백 |
| `next.config.ts` | metadata 스트리밍을 막아 상세 없음 판정 전 HTTP 헤더 전송 방지 |
| `tests/community-posts.test.mjs` | 공개 조회/범위/손상 404 단위 검사 |
| `tests/community-browser.mjs` | 서버 GET 모의까지 포함한 커뮤니티 회귀 34개 |
| `tests/community-public-browser.mjs` | 기존 실제 DB 공개 읽기만 하는 회귀 6개 |
| `tests/production-smoke.mjs` | 로딩 문구 검사를 실제 API 제목·본문·작성자의 HTML 비교로 대체 |

기존 사용자 작업인 작성 폼·홈 링크·이전 로그인/UI 검사 변경과 mock-posts 삭제는 그대로 보존했다. 작업 중 typegen/build가 바꾼 next-env.d.ts의 생성 경로만 시작 내용으로 복원했다.

## 설정과 캐시

다음은 예시이며 실제 `.env` 값은 읽거나 기록하지 않았다. 기존 환경파일은 변경하지 않았다.

```dotenv
# 브라우저가 직접 접근할 공개 Spring Origin. 기존 인증/작성에도 사용.
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082
# Next 서버에서만 사용할 주소. 컨테이너 내부 주소라면 여기에 설정.
IYUM_API_INTERNAL_URL=http://127.0.0.1:8082
# 선택: 이 설정이 없으면 canonical/og:url은 생략. 실제 서비스 주소를 확정한 후 지정.
IYUM_SITE_URL=http://127.0.0.1:8443
```

`IYUM_API_INTERNAL_URL`이 없으면 공개 API 주소를 서버에서도 사용한다. 상대 주소 대신 Next 서버에서 접근 가능한 절대 주소를 쓴다. 비밀값/인증정보를 이 주소에 넣지 않는다. 내부 변수는 server-only 모듈에서 읽으며 클라이언트 정적 번들에 변수 이름도 포함되지 않음을 확인했다. `IYUM_SITE_URL`은 유효한 http(s) 사이트 Origin이어야 한다. 설정해도 검색 차단을 해제하지 않는다.

목록·상세 GET은 `cache:no-store`, redirect:error, 15초 timeout. DB 변경 후 오래된 서버 데이터 캐시를 사용하지 않는다. 상세의 React `cache()`는 **한 렌더 요청 안에서 metadata/page의 동일 ID 조회만 합치며** 요청 사이 데이터를 보관하지 않는다. 필터/페이지 조작은 클라이언트 GET, 새로고침/직접 진입/새 상세 이동은 서버 GET이다. 작성 성공 직후의 새 ID도 서버에서 새로 읽는다. 브라우저 Back/Forward는 탐색 이력의 화면/위치를 복원할 수 있다.

## 직접 실행한 검증

| 검사 | 결과 |
| --- | --- |
| `pnpm test` | 162/162 통과 (기존 159 + 공개 서버 GET/손상 404/범위 판정 3) |
| `pnpm typecheck` | 통과 |
| `pnpm lint` | 통과, 오류·경고 없음 |
| `pnpm build` | 통과, 공개 목록/상세 동적 서버 렌더 |
| 커뮤니티 서버+브라우저 API **모의** | 34개 통과. 하단 다음 페이지/초점, 필터, 목록 간 Back/Forward, 상세 복귀/새 진입, 실패 위치, 역순 응답, 범위/빈 목록, SSR/중복 없음, 404/손상404/500/timeout, 작성·로그인 복귀·입력 보호·중복 등록, 모바일 |
| 기존 인증 **모의** `login-browser.mjs` | 38개 통과, 지연 로그아웃/dirty/알림 포함 |
| 실제 Spring/PostgreSQL 공개 읽기 | 기존 8443에서 6개 묶음 통과. 실제 목록·상세 초기 HTML/무JS, hydration 중복 없음, 필터 복귀, 범위 밖 복구, HTTP404/안내, 1440/390/320, 비로그인 작성 안내 |
| 프로덕션 `test:smoke` | 임시 8445에서 공개 경로 15개, 미등록 경로404/robots, 실제 API와 목록·상세 HTML 비교, metadata, 일반 브라우저/Twitterbot 요청의 없는 글 HTTP404 통과 |
| `git diff --check` | 통과 |

캡처와 민감값 없는 결과는 [후속 캡처 폴더](community/2026-10-14-review/README.md)에 있다. 실제 API 데이터로 HTML을 검사한 것이며, RSC/script 내부 문자열이나 초기 로딩 문구만으로 성공 판정하지 않는다.

첫 검사에서 짧은 1개짜리 페이지의 제목을 항상 200px 이내로 맞추려던 검증 조건과 글쓰기의 장식 +를 접근 가능한 이름에 포함했던 선택자를 수정했다. 실제 기능 검증은 제목 가시성/초점, 긴 목록의 위치 이동으로 분리했다. 임시 8445에서 브라우저 인증을 시도한 검사는 CORS로 실패했다. 기존 CORS는 8443만 허용하므로 정책을 바꾸지 않고 8443에서 6개 전체를 재실행해 통과했다. 8445 결과는 서버 공개 GET/프로덕션 HTML 검증으로 한정한다.

## 제한 사항과 미실행 항목

- Next 16.3.6에서는 스트리밍으로 HTTP 200 헤더를 보낸 뒤 `notFound()`가 발생해도 상태를 404로 바꿀 수 없다. 이번에는 `htmlLimitedBots: /.*/`로 metadata를 기다리도록 해서 dev/production, 일반 브라우저/Twitterbot 모두 실제 HTTP404를 확인했다. 그 대신 API 응답만큼 최초 응답이 늦어질 수 있다.
- 현재 Next 16.3.6의 비스트리밍 notFound 응답은 HTML에 오류 셸/RSC 정보를 보내고, 커스텀 404 안내는 hydration 후 표시한다. **없는 글의 무JS 안내 화면은 완성되지 않는다.** 정상 목록·상세는 무JS에서도 실제 내용을 확인했다. 404 판정과 noindex는 JS와 무관하게 응답에 포함된다. 프레임워크 버전/상태 정책을 별도 검토하기 전 우회 라우트나 보안 정책 변경은 하지 않았다.
- API 장애 안내는 HTTP200의 재시도 가능한 화면이며 404로 바꾸지 않는다. 성공 판정으로 사용하면 안 된다. 장애 이후 클라이언트 재시도는 화면을 복구하고, metadata는 다음 서버 탐색/새로고침 때 다시 생성된다.
- 기존 DB에 필요한 글이 있어 신규 회원/글을 만들지 않았다. 실제 인증/작성/메일 전체 통합 검사는 이번에 재실행하지 않았다. 관련 201/CSRF/401/입력 보호는 모의 회귀로 검증했다. 이전 실제 가입·인증·작성 검증은 최초 구현 기록에만 귀속된다.
- Spring 코드를 바꾸지 않아 백엔드 전체 테스트는 재실행하지 않았다. 외부 배포 환경·다른 브라우저·실제 스크린리더 음성은 확인하지 않았다.
- 댓글·조회수·좋아요·베스트 집계·이미지 업로드·로그인 왕복 초안 복원은 계속 미구현이다.
- 기존 8443 Next/8082 Spring/DB/Mailpit 서버와 데이터는 유지한다. 직접 실행한 검증용 8444/8445 Next와 8855 모의 서버만 종료한다.

## 재실행과 직접 확인

`web/`, Node22/24, pnpm10.34.3. 브라우저 스크립트는 기존과 같이 외부 Playwright 런타임 경로를 `PLAYWRIGHT_MODULE`로 지정하거나 설치된 Playwright를 사용한다. 의존성 추가는 없었다.

```sh
pnpm test
pnpm typecheck
pnpm lint
pnpm build
# 실제 읽기만: 기존 CORS 허용 Origin의 서버 사용
BASE_URL=http://127.0.0.1:8443 node tests/community-public-browser.mjs
# 실제 Spring 조회 + 실행 중인 프로덕션 HTML 검사
BASE_URL=http://127.0.0.1:8445 pnpm test:smoke
```

모의 검사 전용 임시 Next 서버는 기존 서버와 다른 포트를 쓴다. 브라우저 공개 API 주소는 빌드 시 NEXT_PUBLIC 값이며 `API_BASE_URL`로 테스트 라우팅 주소를 맞출 수 있다.

```sh
# 터미널 1 (build 이후)
IYUM_API_INTERNAL_URL=http://127.0.0.1:8855 pnpm exec next start --hostname 127.0.0.1 --port 8444
# 터미널 2: 스크립트가 8855에 로컬 모의 서버를 열고 종료한다.
BASE_URL=http://127.0.0.1:8444 COMMUNITY_SSR_MOCK_PORT=8855 node tests/community-browser.mjs
```

브라우저 직접 확인:
1. `/community/posts`를 열고 글을 선택한 뒤 목록 복귀. 필터·위치가 유지되는지 본다.
2. 충분한 글이 있는 목록에서 하단 다음 페이지/키보드 Enter → 성공 후 제목 초점과 새 목록 시작점을 확인한다. 로컬 DB의 글 수가 적어 이 경우는 위 63개 모의 목록 검사에서 확인했다.
3. `/community/posts?type=GENERAL&topic=WELFARE_LIFE&page=99` → 범위 안내 → 같은 조건의 첫 페이지.
4. 390/320px에서 배너 → 필터 → 목록, 베스트 숨김, 필터 Escape/취소와 초점 복귀 확인.
5. 실제 존재하는 상세의 페이지 소스에서 제목·본문 확인. 없는 ID의 Network 문서 상태는404, 브라우저 안내는 hydration 후 표시된다.

참고한 공식 Next.js 문서: [History API 연동](https://nextjs.org/docs/app/getting-started/linking-and-navigating#native-history-api), [metadata](https://nextjs.org/docs/app/api-reference/functions/generate-metadata), [htmlLimitedBots](https://nextjs.org/docs/app/api-reference/config/next-config-js/htmlLimitedBots), [not-found 상태와 스트리밍](https://nextjs.org/docs/app/api-reference/file-conventions/not-found).


## 2026-10-14 추가 리뷰: 등록 경합·결과 확인·404

이 절은 위 첫 번째 리뷰 결과 이후의 **별도 실행 기록**이다. 기준 브랜치/HEAD는 동일하며 시작 시 이미 있던 미커밋·미추적 작업을 보존했다. **1·2번은 수정 및 검증 완료. 3번은 서버/클라이언트 분리는 반영했지만, 요구한 무JS 404 안내는 아직 완료하지 못했다.** 아래 실패 결과가 이전의 포괄적인 “프레임워크 한계” 표현을 대신한다.

### 1. 이탈 승인 이후 등록 응답

- 원인: `PostCreate.handleSubmit()`의 `mounted.current`는 실제 언마운트만 알려 준다. Next가 목적지의 서버 응답을 기다리는 동안 이전 폼은 마운트된 채 남기 때문에, 확인창에서 이미 “나가기”를 눌렀어도 늦은201이 상세 `router.replace()`를 실행했다.
- `NavigationGuardProvider`에 `approvalVersion` ref와 `getApprovalVersion()`을 추가했다. `proceed()`는 pending 동작을 실행하기 **전에 동기적으로** 번호를 올린다. `cancel()`/“계속 작성”은 올리지 않는다. 별도 전역 저장소나 새 이탈 확인 체계는 없다.
- `useUnsavedChanges()`가 이 getter를 제공한다. `handleSubmit()`은 POST를 시작할 때 번호를 보관하고 성공/실패 처리 전에 현재 번호와 비교한다. 이미 승인된 이탈이면 상세 이동, 오류/필드 안내, 그로 인한 초점 이동,401의 인증 갱신을 실행하지 않는다.
- `finally`는 요청 중 상태와 중복 제출 ref만 정리한다. 로그아웃 승인 후 로그아웃 실패로 같은 폼에 남을 수도 있으므로 끝난 요청의 버튼을 영구 비활성화하지 않는다.
- 취소 버튼, 목록 `AppLink`, 헤더 `AppLink`, 캡처 단계에서 복원하는 브라우저 Back 모두 기존 guard의 `proceed()`를 사용한다. pathname 변화까지 기다리지 않는다. 정상201/id 검증과 기존 dirty 해제/상세 이동은 유지한다.
- 이 처리는 **클라이언트 후속 처리 무효화**다. 서버 저장 취소가 아니다. POST를 abort하거나 자동 재전송하지 않으며, 서버에서 이미 저장되었을 가능성을 기존 안내대로 남긴다.

### 2. 등록 결과 확인 주소

- 원인: 결과 불확실 시 `back`으로 새 탭을 열어, 작성 전에 보던 유형·주제·뒷페이지에서 방금 저장한 글을 찾을 수 없었다.
- `handleSubmit()`이 검증 통과 시 `{ ...values }`를 `submitted`로 보관하고 이 값으로 POST한다. 같은 시점에 기존 `listHref({type, topic, page:0})`로 `submittedListHref`를 저장한다.
- 결과 확인 링크만 이 주소를 사용한다. 목록 일반 복귀와 성공한 상세의 `from`은 기존 `back`을 유지한다. 이후 폼을 수정해도 이전 확인 링크는 변하지 않고, 새로운 유효 요청을 제출하면 그 요청 조건으로 바뀐다.
- URL 도우미는0페이지를 쿼리에서 생략하지만, 실제 공개 GET은 `page=0&size=20`이다. 새 URL 도우미/API 계약은 추가하지 않았다.
- 새 탭(`target=_blank`, `noopener noreferrer`)과 원래 폼 보존을 유지하며, 목록을 봤다는 이유로 저장 여부를 확정하지 않는다.

### 3. 404 서버 표시 분리와 남은 실패

`[postId]/not-found.tsx`에 서버 안내 본문을 두었다. `PostNotFound.tsx`의 기본 함수는 이제 작은 `PostNotFoundReturnLink`이며, `useSearchParams()`/`safeListReturn()`/스크롤 복귀만 담당한다. 이 부분만 Suspense로 감싸고 기본 `/community/posts` Next Link를 fallback으로 제공한다. Next Link도 서버 렌더 시 실제 href가 있는 앵커를 출력한다.

그러나 **이 변경만으로 실제 무JS 문서가 완성되지는 않았다.** 최종 production build + next start에서 `community-404-browser.mjs`로 확인한 결과:

| 조건 | HTTP | 실제 표시 본문/동작 |
| --- | --- | --- |
| 없는 ID99999999, JS 비활성, 일반 Chrome UA | 404/noindex | body.innerText가 빈 값, heading 없음, 목록 링크0개 → 실패 |
| 동일 요청, JS 비활성, Twitterbot/1.0 | 404/noindex | 동일 실패 |
| 동일 요청, JS 활성 | 404 | 안내 표시, 안전한 from/스크롤150px 복귀, 외부 from 차단 통과 |
| 정상 실제 목록·상세, JS 비활성 | 200 | 제목·본문·작성자가 실제 표시 DOM에 존재 |
| 손상404/500/네트워크/timeout/HTML 응답 | 오류 UI | 모의 회귀에서404로 오인하지 않음; 다시 시도 유지 |

개발8443에서도 두 UA의 무JS404 빈 본문을 별도로 재현했다. `.env` 내용을 출력하지 않았으며 API·쿠키·CSRF·CORS 설정은 바꾸지 않았다.

조사 과정과 채택하지 않은 실험:

1. 원래 `notFound()`를 호출하는 상세 page를 유지하고 서버 not-found 본문/링크만 분리: dev와production에서 HTTP404지만 `<html id="__next_error__">` 오류 문서의 표시 body가 비었다. Suspense 내부의 쿼리 처리만이 원인은 아니다.
2. 없음이 확정된 경우에만 `notFound()`를 호출하는 자식에 서버 안내 fallback Suspense를 추가: dev에서 본문은 표시되지만 HTTP200이 되어 **채택하지 않았다**.
3. page는 안내를 반환하고 `generateMetadata()`에서 `notFound()`를 호출: dev에서404지만 빈 오류 문서가 되어 **채택하지 않았다**.
4. 최종 page는 작업 시작 당시 내용과 동일하게 되돌렸으며(이번 실험 부분만), `htmlLimitedBots: /.*/`도 그대로 보존했다. 설치된 Next16.3.6의 `server/app-render/app-render.js`에서 `getErrorRSCPayload()`가 빈 오류 문서 seed를 구성하는 경로를 확인했다. 이 실행 조건에서의 관찰이지 모든 Next 버전/구성에서 불가능하다는 주장으로 확대하지 않는다.

무JS 기본 링크가 실제 문서에 없으므로 클릭해 목록으로 이동하는 검증도 수행할 수 없었다. `community-404-browser.mjs`는 이를 숨기지 않고 비정상 종료(exit1)한다. 라우트 앞단의 추가 조회/리라이트나 프레임워크 패치·버전 교체는 이번에 도입하지 않았다. 해당 응답 경로를 별도로 해결하기 전까지 **3번을 완료로 간주하면 안 된다**.

참고: [Next not-found 규약](https://nextjs.org/docs/app/api-reference/file-conventions/not-found), [useSearchParams와 작은 Suspense 경계](https://nextjs.org/docs/messages/missing-suspense-with-csr-bailout). 문서만으로 성공을 판단하지 않고 위 로컬 재현을 기준으로 보고했다.

### 변경 파일 (이번 추가 작업만)

| web 기준 파일 | 변경 |
| --- | --- |
| `src/components/NavigationGuard.tsx` | 승인 시점 번호/getter, 기존 확인/이력 처리 유지 |
| `src/features/PostCreate.tsx` | `canHandleResult`, 제출 값·확인 URL 보관 |
| `src/app/community/posts/[postId]/not-found.tsx` | 서버 안내·작은 링크 fallback |
| `src/components/community/PostNotFound.tsx` | 쿼리 복귀 링크만 클라이언트 책임으로 축소 |
| `tests/community-browser.mjs` | 목적지까지 지연하는9개 경합 조합, Back/계속작성, 제출 조건 고정 등 총47개 |
| `tests/community-404-browser.mjs` | 새 실제 DOM/무JS/UA별 상태/안전 복귀 검증. 현재2개 실패 |
| `tests/community-local-browser.mjs` | 실제 테스트 글 제목에 실행 식별자 추가, 재실행 시 동일 제목 선택자 충돌 방지 |
| 이 문서, `Community-React-Learning-2026-10-14.md`, `community/2026-10-14-review-2/` | 학습 설명·실제 검증 기록·캡처 |

그 외 API 클라이언트·세션·Spring·DB 스키마·스타일은 이번에 변경하지 않았다. 기존 전체 Git diff에는 이전 커뮤니티 구현 변경도 함께 보인다.

### 새 회귀 검사와 실제 검증

| 검사 | 결과 |
| --- | --- |
| `pnpm test` | 기존 단위162/162 통과 |
| `pnpm typecheck`, `pnpm lint`, `pnpm build`, `git diff --check` | 통과 |
| 커뮤니티 production+API모의 | 47개 통과(기존34+신규13) |
| 기존 로그인·로그아웃 모의 | 38개 통과; 추가 입력/지연 로그아웃/오류/알림 타이머 포함 |
| 실제 Spring·PostgreSQL·Mailpit | 7개 묶음 통과; 가입·이메일 인증·수동 로그인→작성201→상세→새로고침→목록, CSRF누락403·로그아웃 후401 입력 보존 |
| production `test:smoke` | 15경로와robots/미등록404, 실제 API와 초기 HTML/metadata, 두UA404 통과 |
| production 404 전용 DOM | **5통과/2실패**, exit1. 실패는 위 무JS 본문/링크 |

등록 경합 검사는 POST와 목적지 서버 GET에 각각 대기 장치를 둔다. “나가기” 이후 목적지 GET이 시작되었으나 아직 응답하지 않았음을 기다리고, 작성 폼이 표시 중이며 URL도 작성 경로인 것을 확인한 다음 POST의201/401/500을 보낸다. 여전히 폼이 남고, 오류·초점 변경·상세 GET·중복 POST가 없음을 검사한 뒤 목적지를 해제한다. 취소/목록 링크/헤더 링크 × 세 응답의9조합이다. Back은 기존 이력 복귀 동작에서 승인/계속 작성 두 경우를 확인했다. “언마운트 이후 늦은 응답”만 검사한 결과가 아니다.

테스트 코드의 첫 실행에서 작성 링크 클릭 직후 아직 남아 있는 목록 필터에 입력하던 선택자 경합을 발견했다. `fillPost()`가 작성 form을 기다리도록 고친 후47개 전체를 재실행했다. fallback의 일반 앵커에 발생한 Next lint 오류는 실제 href를 유지하는 Next Link로 수정했으며 최종 lint/build를 통과했다. 검증을 비활성화하지 않았다.

실제 생성 데이터(삭제하지 않고 남김):
- 회원22, `iyum-community-mv7rc98w@example.test`, 닉네임 `UI_mv7rc98w`, ACTIVE.
- 게시글4: `[로컬 UI 검증 mv7rc98w] 커뮤니티 작성과 조회 확인`, GENERAL/WELFARE_LIFE.
- Mailpit 검증 메일1건. 비밀번호는 메모리에서만 사용했으며 쿠키·CSRF·인증 토큰·메일 원문은 기록하지 않았다. 기존 계정 비밀번호를 읽거나 변경하지 않았다.
- 실행 종료 시 테스트 회원은 로그아웃 상태다. 실제 작성 POST는1회이며 추가 실패 요청에서는 저장되지 않았다.

[데스크톱·모바일 캡처 및 JSON 결과](community/2026-10-14-review-2/README.md). 실제 캡처는 `real-*`, API모의는 `mock-*`로 구분한다. 실패한 무JS 화면도 포함했다.

실행은 `web/`, Node24/pnpm10.34.3/외부 Playwright 런타임에서 했다. 404 재현은 build 후 실제 Spring을 내부 주소로 사용하는 임시 Next 서버에서:

```sh
IYUM_API_INTERNAL_URL=http://127.0.0.1:8082 pnpm exec next start --hostname 127.0.0.1 --port 8445
BASE_URL=http://127.0.0.1:8445 node tests/community-404-browser.mjs
```

Playwright가 프로젝트에 설치되어 있지 않으면 기존 환경처럼 `PLAYWRIGHT_MODULE`에 설치된 모듈의 절대 경로를 지정한다. 모의47개는 위 첫 리뷰 절의8444/8855 실행 방법을 그대로 사용한다. 실제 작성 검증은 `IYUM_RUN_LOCAL_COMMUNITY_TEST=true node tests/community-local-browser.mjs`이며 실행할 때마다 테스트 회원/게시글 각1개가 추가되므로 목적 없이 반복하지 않는다.

### Git·서버 및 미실행 범위

브랜치 `feat/community-posts-ui`, HEAD `88650cf1443f635bc0d36e9bd20c83c607576c6f` 유지. 기존 staged 항목은 없었고 이번에도 stage/commit/push/PR/병합/배포하지 않았다. 기존 미커밋·미추적 소스/문서·mock-posts 삭제 상태를 유지한다. 생성된 `next-env.d.ts`의 경로 변화만 작업 시작 내용으로 복원했다.

기존 dev8443(PID64537), Spring8082(PID33858), DB/Mailpit를 보존한다. 직접 실행한 임시 production8444/8445와 모의8855만 종료한다. 실제 DB 데이터를 삭제하거나 초기화하지 않았다.

Spring 코드는 바꾸지 않아 백엔드 전체 테스트는 재실행하지 않았다. 배포 환경, Safari/Firefox, 실제 스크린리더 음성은 확인하지 않았다. **무JS404는 실행했지만 실패한 항목이며, 미실행이나 통과로 분류하지 않는다.** 디자인 및 미구현 커뮤니티 기능은 확장하지 않았다.
