# 주제별 커뮤니티 공간 — 2026-10-15

## 작업 기반과 범위

작업 시작 브랜치 `feat/community-topics`, HEAD `919e891f1a776d7f1be6fabf908d9dba3e28f656`. 시작 시 staged/unstaged/미추적 파일 모두 없음. `git fetch origin` 후 main·origin/main·현재 HEAD가 일치했고 ahead/behind는 0/0이었다. 정보 페이지와 초점 개선은 PR #11 merge commit으로 main에 포함되었으며 `refactor/info-pages`의 `9f4c627`이 main의 조상임을 확인했다. 기존 작업을 초기화·stash하거나 별도 커밋하지 않았다.

기존 7개 PostTopic을 프론트엔드 공간으로 표현한다. API·DB·인증·CSRF·정책 계산과 기존 무JS 게시글 404 코드는 변경하지 않았다. 게시글 데이터나 공지·인기 순위·회원 수를 만들어 넣지 않았다.

## URL과 구성

`/community`는 전체 공간의 입구와 전체 게시글이다. 기존 `/community/posts?topic=...&type=...&page=...` 및 `/community?topic=...`는 기존 필터 목록으로 그대로 동작한다. 게시글 상세 URL도 유지한다.

| 새 경로 (`/community/topics/` 뒤) | 기존 API enum | 표시 이름 |
|---|---|---|
| hearing-aid | HEARING_AID | 보청기 |
| cochlear-implant | COCHLEAR_IMPLANT | 인공와우 |
| daily | DAILY | 자유·일상 |
| treatment-rehabilitation | TREATMENT_REHABILITATION | 치료·재활 |
| communication | COMMUNICATION | 의사소통 |
| employment | EMPLOYMENT | 취업·직장 |
| welfare-life | WELFARE_LIFE | 복지·생활 |

각 화면은 현재 위치 → 제목·소개·장식 일러스트 → 고정 이용 안내 → 글쓰기·글 유형 → 목록·페이지 순서다. 다른 주제 링크는 PC 오른쪽, 모바일 목록 아래에 배치한다. DOM에서도 목록이 다른 주제 링크보다 먼저라서 키보드로 글 유형에 먼저 접근한다. 링크는 AppLink를 사용해 새 탭과 기존 이탈 확인을 유지한다.

주제 경로가 API topic을 결정한다. 충돌/중복 `topic` 쿼리는 서버에서 경로에 맞춰 제거하며 글 유형과 페이지를 보존한다. 유형 적용은 page=0으로 초기화하고, 다른 주제 링크는 해당 공간의 전체 유형·첫 페이지로 이동한다. 지원되는 유형은 GENERAL/QUESTION/REVIEW, 페이지는 0-based다. 기존 서비스에 검색·정렬 파라미터가 없으므로 새 검색 기능을 만들지 않았으며 최신순 정책을 유지한다.

## 주요 파일과 학습 포인트

- `src/lib/community-topics.ts`: slug, PostTopic, 소개, 이미지, 이용 안내를 명시적으로 연결한다. slug를 대문자로 바꿔 enum으로 추측하지 않는다. `topicByPath()`는 등록된 정확한 경로만 인정한다.
- `src/app/community/topics/[slug]/page.tsx`: 하나의 서버 페이지에서 주제 확인, 메타데이터, URL 정규화, 기존 `readPublicPosts()` 최초 조회를 수행한다. 유효하지 않은 slug는 API 전체 목록을 요청하지 않고 `notFound()`로 처리한다.
- `src/components/community/TopicSpaces.tsx`: 전체 입구·주제 간 링크와 헤더를 공유한다. 제목·소개·이용 안내와 최초 실제 목록은 SSR HTML에 포함된다.
- `src/features/PostList.tsx`: 기존 조회/응답 검증/오류·빈 상태/페이지 범위/재시도 코드를 재사용한다. 주제 페이지에서는 중복 주제 select를 숨기고 `fixedTopic`을 쓴다. 유형 선택과 실제 적용을 구분한다. 기존 request generation·AbortController와 주제별 React key로 이전 응답을 무효화한다. 최초 SSR 결과를 hydration에서 다시 GET하지 않는다.
- `src/lib/community-posts.ts`: `listQuery()`는 경로 주제를 우선하고, `listHref()`는 경로가 주제를 나타내면 중복 query를 생략한다. `safeListReturn()`·history 복귀는 정확한 기존 목록/등록된 주제 경로만 허용한다. `/community`로 시작한다는 이유로 상세·작성·알 수 없는 slug를 허용하지 않는다.
- `PostCreate.tsx`: 주제·유형 기본값을 작성 URL과 안전한 목록 복귀 URL에서 복원한다. `PostDetail.tsx`: from이 없는 직접 상세 진입은 게시글 주제 공간을 기본 복귀처로 쓴다. 유효한 기존 from은 그대로 보존한다.
- `src/styles/community.css`: 기존 IYUM 토큰과 반응형 목록 배치를 재사용한다. 전역 focus 스타일을 변경하지 않는다.
- `HomePage.tsx`: 예시 게시글의 전체 목록 진입을 `/community`로 통일한다. 예시를 실제 게시글처럼 바꾸지 않는다. 공통 메뉴는 이미 `/community`를 사용하므로 재작성하지 않는다.

공개 GET은 기존 서버 내부 주소 `IYUM_API_INTERNAL_URL`과 브라우저용 `NEXT_PUBLIC_API_BASE_URL` 분리를 유지한다. 공개 조회는 credentials omit/no-store이며 사용자 쿠키·인증 헤더를 전달하지 않는다. API 필터는 서버 Repository에서 topic AND type으로 적용한다. 전체 데이터를 프론트에서 임의 필터링하지 않는다. 환경변수·패키지 추가 없음, robots/noindex 및 htmlLimitedBots 설정 유지.

## 작성·복귀 정책

1. 주제 공간 → 글쓰기: 현재 주제와 적용 유형을 기본 선택하며 사용자가 변경할 수 있다. 비로그인이라면 기존 login `from`으로 작성 목적지 전체를 전달한다. 로그인 API를 새로 만들지 않는다.
2. 취소/목록 링크: 원래 주제·유형·페이지로 돌아간다. 목록에서 기록한 스크롤 위치를 복원한다.
3. 등록 성공: 기존 유효한 201/id 검사 후 새 상세로 이동한다. 상세의 목록 복귀 주소는 **작성 전에 보던 원래 주소**다. 주제/유형을 바꾼 글이 그 목록에 포함된다고 안내하지 않는다.
4. 등록 결과 불확실: 주제 공간에서 시작했다면 실제 **전송 당시** 주제와 유형의 첫 페이지를 새 탭에서 확인한다. 폼을 이후 수정해도 이 링크는 바뀌지 않는다. legacy 목록에서 시작한 경우 기존 enum 쿼리 기반 첫 페이지를 유지한다. 확인만으로 성공/실패를 단정하거나 POST를 재전송하지 않는다.
5. 이탈 승인 버전·늦은 응답 무효화·중복 제출 방지·세션 만료 입력 보호는 기존 로직을 유지한다. 로그인 왕복 초안 저장은 이번에 추가하지 않았다.

마우스 유형/페이지 변경은 조회 성공 시 필요한 목록 시작 위치로 이동하되 제목에 강제 초점을 주지 않는다. 키보드는 기존 읽기 초점 유틸리티와 승인 버전 검사를 유지한다. 실패 시 이전 결과를 남기며 이동하지 않는다. 상세 복귀와 뒤로/앞으로는 별도의 복귀 위치를 사용한다.

## 일러스트와 디자인

`public/images/community/*.svg` 7개는 이번 구현에서 직접 작성한 벡터이며 외부 이미지/라이선스 의존성이 없다. 기존 로고를 교체하지 않았다. 보청기(귀·기기), 인공와우(프로파일·코일), 일상(컵·책), 치료·재활(계단), 의사소통(말풍선), 취업(서류가방), 복지·생활(집·체크 목록)을 각각 표현한다. 정확한 제품·치료 방법을 설명하는 의료 도해가 아닌 장식이며 `alt=""`로 처리한다. 모바일 대표 이미지는 72×64 영역으로 축소한다.

## 검증과 한계

최종 실행 결과와 캡처는 [검증 자료](community-topics/2026-10-15/)에 보관한다. 캡처의 `[검증 예시]` 게시글과 회원은 **브라우저/SSR 모의 응답**이다. 서비스 소스나 DB에는 추가하지 않았다.

- 단위 테스트: 174 통과(새 URL/설정/안전 복귀 4개 포함), skip 0.
- 타입 검사, lint, 프로덕션 빌드, `git diff --check`: 통과.
- 기존 커뮤니티 브라우저: 49 통과. 직접 상세 진입의 기본 목록 기대값만 해당 주제 공간으로 변경했다. 기존 오류·SSR·작성 보호·지연 응답 검사 유지.
- 주제 브라우저: **23개 통과, 무JS 주제 404 본문 1개 실패(종료 코드 1)**. 7개 직접 진입·새로고침·무JS 정상 HTML, 복합 필터, 충돌 URL, 지연 응답, 위치 복원, 작성 맥락, 모바일/키보드/폭 변경을 검사한다.
- 로그인·로그아웃 브라우저: **38개 통과**. 실제 인증 서버 검증과 구분되는 모의 검사다.
- 실제 Spring/DB: 기존 8082 서버에 7개 주제 각각 QUESTION과 함께 공개 GET, 모두 200·응답 항목 조건 일치. 8443의 새 보청기 공간을 실제 API 결과와 대조했다. 회원/게시글/DB 변경 **0건**.
- 실제 계정 로그인→작성 POST 흐름은 이번에 실행하지 않았다. 위 해당 시나리오는 모의 응답 검증이다. 전체 백엔드 테스트, P03 전체 조합, 기존 게시글 무JS 404 전용 2개 실패 검사는 이번 범위에서 재실행하지 않았다.

### 새 주제 404에서 추가로 확인한 제한

프로덕션 빌드에서 `/community/topics/unknown`은 HTTP **404**이고 JavaScript 실행 후 “주제 공간을 찾을 수 없어요.”와 전체 커뮤니티 링크를 표시한다. 그러나 JavaScript를 비활성화하면 실제 body에 안내가 표시되지 않는다. script/RSC 문자열만으로 통과시키지 않았다. metadata에서 notFound를 호출하지 않고 페이지에서만 판정해도 재현되었다. `topic-404-nojs.json` 및 `topic-results.json`에 별도 실패로 기록하며, 신규 브라우저 스크립트도 이 실패가 있으면 종료 코드 1을 반환한다. 원인을 프레임워크 한계로 확정하지 않는다.

이는 기존 게시글 무JS 404 미해결 2건을 해결했다는 의미가 아니다. 기존 설정·상세 404를 건드리지 않았고 별도 이슈로 유지한다. 정상 7개 주제의 무JS 제목·안내·게시글 표시는 모두 확인했다.

## 재현 명령

Node 24.21.0 / pnpm 10.34.3 / Chrome headless. 기존 8443 개발 서버와 충돌하지 않게 환경파일 없는 임시 복사본에서 빌드·8451 실행했다. 모의 SSR 서버 8856은 각 브라우저 스크립트가 열고 닫는다.

```sh
pnpm test
pnpm typecheck
pnpm lint
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm exec next start --hostname 127.0.0.1 --port 8451
BASE_URL=http://127.0.0.1:8451 COMMUNITY_SSR_MOCK_PORT=8856 node tests/community-topics-browser.mjs
BASE_URL=http://127.0.0.1:8451 COMMUNITY_SSR_MOCK_PORT=8856 node tests/community-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/login-browser.mjs
# 기존 실제 로컬 서버에 공개 GET만 수행 (데이터 생성 없음)
BASE_URL=http://127.0.0.1:8443 node tests/community-topics-live.mjs
```

Playwright가 프로젝트에 설치되어 있지 않은 경우 `PLAYWRIGHT_MODULE`에 기존 외부 Playwright 모듈의 절대 경로를 지정한다. 이 작업은 로컬 Codex 런타임의 Playwright를 사용했으며 의존성을 추가하지 않았다. 출력 위치는 `COMMUNITY_ARTIFACT_DIR`, `LOGIN_ARTIFACT_DIR`로 설정한다. 실제 개인정보·환경파일·인증 토큰·원시 요청 로그는 문서에 보관하지 않는다.

## 직접 확인

1. `http://127.0.0.1:8443/community` → 주제 카드 선택. 새 탭으로 열어도 고유 주소와 소개가 표시된다.
2. 주제에서 글 유형 선택 후 적용. 새로고침/뒤로/앞으로로 조건 유지 확인.
3. 게시글 상세 → 목록: 같은 주제와 조건으로 복귀한다. 직접 상세 주소를 열면 글의 주제가 기본 복귀처다.
4. 글쓰기 → 필요시 로그인 → 선택 주제 확인. 주제 변경 후 등록의 복귀 정책은 위 설명을 따른다.
5. 390/320px에서 필터를 열고 1440px로 확대해도 선택한 조건과 조작 요소에 접근할 수 있다.

최종 브랜치 `feat/community-topics`, HEAD는 시작 기준 그대로다. 커밋·push·PR·병합·배포하지 않았다. 기존 8443·8082 서버를 유지하며 검증용 8451·8856만 정리한다.

## 후속 보완 — 전체 게시글 진입 (2026-10-15)

기존 주제 화면 상단에 전체 커뮤니티 링크가 있었지만 주제 선택 목록에는 전체 항목이 없어 되돌아갈 곳을 찾기 어려웠다. `TopicSpaces`의 첫 항목에 **전체 게시글**(`/community`)을 추가하고, 상단 현재 위치 링크도 같은 이름으로 명확히 했다. 전체 진입에서는 주제·글 유형·페이지를 초기화한다. 브라우저 뒤로 가기는 직전 주제와 유형·페이지를 보존한다. PC·모바일에서 동일하게 적용하며 기존 AppLink를 유지한다.

`tests/community-all-return-browser.mjs`를 기존 8443·Spring 공개 조회 환경에서 실행: 1440·390·320px 3개 시나리오 통과(클릭/320px 키보드 Enter, 전체 조건 복귀, 뒤로 가기, 가로 넘침). 타입 검사·lint·diff 검사 통과. 데이터 쓰기 없음. 이 작은 링크 보완 후 전체 23/49/38 브라우저 검사와 빌드는 다시 실행하지 않았으며 위 기록은 보완 전 결과다. 새 스크립트와 [최신 캡처·결과](community-topics/2026-10-15/all-posts-return/)를 함께 제공한다. 무JS 404 실패 기록은 그대로 유지한다.

## 후속 보완 — 주제 무JS 404 (2026-10-15)

위 무JS 주제 404 실패는 수정 전 기록이다. 렌더 전 주제 존재 판정과 기존 안내의 서버 렌더로 수정했으며, [원인·변경·최종 검증](Community-Topic-404-Fix-2026-10-15.md)에 근거를 남겼다. 프로덕션/개발 전용 검사 각각 7개, 주제 23개, 기존 커뮤니티 49개가 통과했다. 단위 174개·타입·lint·빌드도 통과했다. 기존 게시글 무JS 404는 재검사 결과 여전히 2건 실패하며 별도 이슈다.
