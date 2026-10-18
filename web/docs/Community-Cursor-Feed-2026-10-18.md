# 커뮤니티 커서 피드 적용 — 2026-10-18

## 기준과 범위

- 브랜치 `feat/community-post-cursor`, HEAD `2adf11bff2e58394899d33157aa0a556df3306fa`. 작업 시작 시 변경 없음. 현재 작업 트리에 프런트 변경을 남겼으며 커밋·push·병합·배포하지 않았다.
- 원본 `/Users/tackeunoh/Developer/iyum` 기준. `iyum-preview` worktree는 변경하지 않았다.
- 루트/web AGENTS, PRODUCT-CONTEXT, 실제 Spring PostController/PostReadService/Feed DTO, 현재 작성·수정·삭제·댓글·이탈 보호를 확인했다.
- 설치된 Next 16.3.6의 `node_modules/next/dist/docs/01-app/01-getting-started/06-fetching-data.md`, `04-linking-and-navigating.md`를 확인했다. 서버 최초 조회를 유지하며 전체 피드를 클라이언트 전용 렌더링으로 바꾸지 않았다.
- 백엔드·API·DB·인증 계약, 댓글 SSR, 게시글 상세 metadata, 검색 차단은 변경하지 않았다.

## 화면과 API

공통 헤더/원본 로고 아래 제목·글쓰기 → 카테고리 링크 → 작은 베스트 준비 안내 → 글 유형 → 한 열 피드로 구성했다. 오른쪽 사이드바와 큰 주제 배너를 제거했다. `일반`, `질문·답변`, `경험·후기`의 실제 enum/표시 이름을 유지한다.

- 전체 및 기존 7개 주제 URL 유지. 모바일 탭은 가로 스크롤, 펼침 버튼은 스크롤 밖에 있다. Escape로 접고 버튼에 초점이 돌아온다. 모바일 탭만 sticky이며 베스트는 고정하지 않는다.
- 베스트는 선택 범위 이름 + 준비 안내만 표시한다. `이번 주 · 준비 중`은 disabled이며 집계 요청이 없다. 프로필은 내부 기본 SVG이며 업로드/프로필 링크가 없다.
- 제목은 실제 AppLink, 미리보기는 React 텍스트로 출력하여 HTML을 해석하지 않는다. 아바타·닉네임·주제/유형/날짜·제목·미리보기·실제 댓글 수 순서. 댓글 0도 표시한다.
- 새 `getFeed()`만 `/api/v1/community/posts/feed`를 사용한다. type/topic, size=20; 다음 요청에 직전 `createdAt` 원문과 postId를 `afterCreatedAt`/`afterId`로 전송한다. Date 재직렬화하지 않는다.
- items/ID/작성자/enum/미리보기(Unicode 200자)/댓글 수(0 이상 안전한 정수)/정렬/커서/hasNext를 검증한다. 잘못된 값을 빈 목록이나 댓글 0으로 보정하지 않는다. 반복 커서·역순·중복 ID가 있는 손상 응답을 오류로 처리한다.
- 공개 요청은 credentials=omit, no-store. 항목별 상세/댓글 API를 추가 호출하지 않는다. 페이지 API `getPosts()`와 상세 응답 검사는 그대로 유지한다.
- 첫 피드를 서버에서 기다려 HTML에 넣는다. hydration 직후 중복 첫 GET을 하지 않는다. 무JS에는 현재 조건과 원문 커서를 담은 `다음 게시글 목록` 링크가 있다.
- 옛 `page` 쿼리는 유형·주제를 보존한 첫 피드로 정규화한다. `safeListReturn()`은 알려진 내부 목록/주제 경로 및 유효한 커서만 보존한다. 경로의 주제가 쿼리보다 우선하며 중복/충돌 topic 쿼리는 제거한다. 새 카테고리·유형 링크는 커서를 초기화한다.

## 핵심 파일과 상태 흐름

| 파일 | 역할 |
|---|---|
| `src/lib/community-api.ts` | FeedItem/FeedPage, getFeed/olderThan; 기존 페이지·상세 API와 구분 |
| `src/lib/community-server.ts`, `community-load.ts` | server-only 공개 첫 피드, InitialFeed |
| `src/components/community/CommunityListPage.tsx` | URL 해석·정규화·첫 SSR 결과 전달 |
| `src/app/community/page.tsx`, `posts/page.tsx`, `topics/[slug]/page.tsx` | 기존 경로를 공통 피드에 연결, 주제 metadata/404 유지 |
| `src/features/PostList.tsx` | 누적 조회, 요청 세대·취소·오류, observer/수동 추가, 복귀 복원 |
| `src/lib/community-feed.ts` | 방문별 제한 메모리 캐시, ID 병합, reconcileFeed 재검증 |
| `src/components/community/FeedNavigation.tsx` | 공통 카테고리 링크, 준비 상태 베스트, SVG 아바타 |
| `src/lib/community-posts.ts` | 커서 URL/safeListReturn/복귀 의도 소비. 옛 페이지 API용 query 파서는 유지 |
| `src/lib/community-mutations.ts` | 기존 list/detail 버전 유지, async 조회용 getPostVersion 추가 |
| `src/components/community/PostActions.tsx` | 삭제 성공 시 목록 복귀 의도를 전달하고 scroll:false로 기존 위치 복원 |
| `src/styles/community.css` | 피드 전용 스타일. 다른 화면 버튼·인증 스타일 변경 없음 |

### 추가 조회와 오래된 응답

하단 120px 접근 시 observer가 추가 조회하며 수동 `게시글 더 보기`도 제공한다. busy ref로 동시 요청을 막고 AbortController/NavigationRequest/조회 당시 list version을 모두 확인한다. 실패하면 기존 항목·커서를 유지하고 observer를 멈춰 명시적 재시도 전까지 오류 요청을 반복하지 않는다. 마지막 페이지의 items도 합치고 hasNext=false에서 멈춘다.

추가 조회는 초점·스크롤을 강제로 옮기지 않는다. 더 보기 버튼은 요청 중 aria-disabled와 요청 가드로 중복 제출을 막아 키보드 초점이 body로 떨어지지 않는다. 마지막 구간에서도 같은 버튼을 비활성 상태로 유지한다. 새 글을 처음부터 읽는 `최신 목록 새로고침`은 명시적 동작이다.

### 긴 목록 복귀와 제한

`history.state.iyumFeedEntry`만 병합하여 Next/NavigationGuard의 state를 보존한다. 같은 URL의 서로 다른 방문도 별도 ID이다. 캐시에 조회 조건(URL), 이미 받은 순서/항목, 커서, hasNext, 읽은 구간 수, list version, 보이는 글 ID·이웃 ID·화면 상대 위치를 저장한다. 인증/초안은 저장하지 않는다.

상세 링크 이동 전에 캐시를 저장한다. 뒤로/앞으로 가기는 방문 ID, 상세의 목록 링크는 복귀 의도와 직전 방문 ID를 사용한다. 충분한 항목을 렌더링한 다음 위치를 복원한다. 복원 중 observer와 변경 재조회는 기다린다. 그 뒤 필요한 버전 재검증을 시작하여 복원 좌표를 맨 위 좌표로 덮어쓰지 않는다. 삭제된 기준 글은 다음/이전 이웃을 우선한다.

- 메모리에만 최대 8개 방문, 방문당 50구간(최대 1,000개). 이후에는 다음 구간 링크를 제공한다. 무제한 누적/가상화는 추가하지 않았다.
- 새로고침·탭 재시작·모바일 메모리 회수·캐시 퇴출 후에는 URL에 해당하는 첫 20개를 새로 읽는다. 이전 60개 복원을 보장하지 않는다. 커서 URL 직접 진입이라면 그 커서부터 20개이다.
- 한 방문에 들어온 이후 외부 탭/다른 사용자의 변경을 실시간 구독하지 않는다. 명시적인 최신 목록 새로고침으로 확인할 수 있다.

### 댓글 수와 게시글 변경

기존 `postListChanged()`/`postChanged()`를 재사용한다. 댓글 변경은 상세 version을 건드리지 않아 본문/Comments 및 작성·수정 초안을 재마운트하지 않는다. 늦게 성공한 변경 요청도 list version은 올리되 이전 화면 UI는 기존 NavigationRequest가 보호한다.

목록 복귀 시 `reconcileFeed()`는 첫 커서부터 이미 읽었던 마지막 글까지 확인한다. 최대 기존 구간+2, 전체 상한 52회이며 새 글이 매우 많이 생겨 범위에 도달하지 못하면 기존 캐시를 남기고 명시적 새로고침을 안내한다. 기존 ID만 유지하여 새 글을 위에 삽입하지 않고, 서버의 댓글 수/내용/분류로 갱신하며 삭제·조건 밖 글은 제거한다. 이미 받은 60개를 첫 20개로 축소하지 않는다. 재조회 실패는 조회 오류이며 이미 완료된 저장 실패로 표시하거나 POST/PUT/DELETE를 재전송하지 않는다.

서버 cursor API에는 snapshot ID나 다중 ID 조회가 없어, 여러 GET 사이의 DB 상태가 원자적으로 동일하다고 보장할 수는 없다. 변경량이 한도를 넘으면 전체 최신 새로고침이 필요하다. 백엔드 계약을 추가하지 않았다.

## 직접 실행한 검증

현재 작업에서 실행한 결과이며 과거 통과 기록과 구분한다. 임시 프로덕션 빌드는 사용자 8443 서버의 `.next` 충돌을 피하려고 `/private/tmp/iyum-edit-build-final/web`에 동일 소스를 복사해 실행했다. 인증/환경 비밀값은 복사하거나 기록하지 않았다.

| 검사/명령 | 결과 |
|---|---|
| `pnpm lint` | 통과, 경고 없음 |
| `pnpm typecheck` | 통과 (격리된 동일 소스) |
| `pnpm test` | 252개 통과, 실패/skip 0 |
| `pnpm build` | 환경 실패: Turbopack PostCSS worker의 port bind `Operation not permitted`. 승인 실행에서도 동일 |
| `pnpm build --webpack` | Next 공식 대체 빌더로 프로덕션 빌드 통과. 앱 설정·의존성 변경 없음 |
| `node tests/community-feed-browser.mjs` | 프로덕션 + 모의 SSR/브라우저 API 16개 통과 |
| `node tests/community-comments-browser.mjs` | 프로덕션 + 모의 API 37개 통과 |
| `node tests/community-edit-delete-browser.mjs` | 프로덕션 + 모의 API 29개 통과 |
| `node tests/community-feed-local.mjs` | 실제 Spring/DB 공개 읽기 4개 통과; 8443 브라우저의 첫·후속 API CORS 포함, 쓰기 0 |
| `node tests/community-topic-404-browser.mjs` | 실제 Spring 공개 읽기, 정상/없는 주제·JS/무JS·키보드 7개 통과 |
| `node tests/community-404-browser.mjs` | 실제 Spring 공개 읽기, 5개 통과/기존 무JS 본문 2개 실패 |
| `git diff --check` | 통과 |

브라우저 실행 환경: Node24, Chrome/Playwright, `PLAYWRIGHT_MODULE`로 기존 설치 경로 사용. mock 프로덕션 BASE_URL `http://127.0.0.1:8451`, 서버 측 공개 API는 테스트가 생성하는 8856, 브라우저의 8082 요청만 route로 모의. 실제 검사 BASE_URL `http://127.0.0.1:8452`, 내부/브라우저 API 모두 실행 중인 Spring8082. 서버 실행 시에만 테스트용 비밀값 없는 주소를 주입한다.

피드 검사에는 무JS 본문·댓글0·다음 링크, 3구간 누적, 중복 클릭, observer 종료/오류 재시도, 느린 이전 응답, 60개 상세 왕복/앞으로가기/동일 URL 별도 방문, 댓글 변경 후 전체 범위 갱신, 늦은 성공, 삭제 후 이웃 복원, legacy 링크 정규화, 새 글 작성 기본값/dirty 취소/201, 캐시 없는 새로고침, 1440/390/320px·긴 글·큰 수·키보드/폭 변경을 포함한다.

기존 댓글 검사의 페이지 목록 CSS와 선택상자 트리거는 피드 항목/명시적 새로고침으로 변경했다. 저장 중 이전 GET과 늦은 POST의 순서 제어, 입력 오류, orphan 편집 초안, 무JS 댓글 성공/빈 목록/오류, CSRF/권한·중복·204·이탈 보호 검사를 유지한다. 수정/삭제 검사의 구 total 표시 대신 피드의 실제 행 수를 확인하며, 이전 페이지 API의 totalElements 검증도 그대로 둔다.

구 페이지 번호·모바일 필터 모달/주제 배너를 전제로 한 `community-browser.mjs`, `community-topics-browser.mjs`, `community-public-browser.mjs` 전체를 현재 피드의 통과 결과로 보고하지 않는다. 해당 목록 UX는 이번 신규 피드 검사로 검증했고, 나머지 오래된 스크립트 전체의 이전 UI 전제 정리는 후속 유지보수 사항이다. 인증 전체/E2E 실제 쓰기/백엔드 전체 테스트는 이번 작업에서 실행하지 않았다. 실제 DB에 테스트 회원·게시글·댓글을 생성/수정/삭제하지 않았다.

### 알려진 별도 실패

일반 브라우저 및 크롤러 UA의 기존 게시글 `/community/posts/99999999`: HTTP404와 noindex는 유지되지만 무JS에서 안내 제목/목록 링크가 실제로 보이지 않는 2건이 재현됐다. 피드·정상 상세·첫 댓글 무JS 열람은 통과했다. 이 실패를 skip하거나 문자열 검사로 바꾸지 않았고, 이번 기능의 해결로 보고하지 않는다.

### 검증 중 찾아 보완한 부분

- 비활성화된 더 보기 버튼에서 키보드 초점 소실 → aria-disabled + busy ref, 마지막 구간에서도 같은 요소 유지.
- 복귀 위치 복원보다 버전 재조회가 먼저 끝나 좌표를 덮어쓰는 경합 → 복원 완료 후 재조회, 복원/오래된 version 중 observer 중지.
- 매우 빠른 이동 중 예약된 캐시 적용 → frame 취소 및 NavigationRequest 확인, 초기화 전 캐시 덮어쓰기 방지.
- 라우트에서 확장된 컴포넌트 prop을 직접 re-export하던 빌드 타입 오류 → PageProps만 받는 얇은 경로 wrapper.

## 캡처와 결과 파일

`community-cursor-feed-2026-10-18/` 아래에 보관한다. `mock/` 캡처는 현재 프로덕션 UI + 명시적 검증 예시 데이터이며 실제 게시글/인기 자료가 아니다.

- `mock/feed-all-{1440,390,320}.png`: 전체 피드
- `mock/feed-hearing-aid-{1440,390,320}.png`: 보청기 피드
- `mock/feed-all-long-{1440,390,320}.png`: 긴 제목·닉네임·안전한 정수 최댓값 댓글 수
- `mock/feed-nojs.png`: 무JS 첫/후속 목록 화면
- 각 `*-results.json`: 이번 검사 결과. `known-404/`: 별도 404 재현 캡처/결과

## 직접 확인 순서

1. 기존 `http://127.0.0.1:8443/community`에서 전체/주제 탭과 글 유형을 선택한다. 작은 화면에서는 탭 펼침과 Escape를 확인한다.
2. 데이터가 20개 이상인 조건에서 하단으로 이동하거나 더 보기를 누른다. 60개 이상 읽고 중간 글 → 목록 링크 / 뒤로·앞으로 가기로 돌아와 위치를 확인한다. 현재 로컬 DB는 첫 조회에 6개여서 긴 목록은 모의 검사로 검증했다.
3. 본인 검증용 글에서 댓글을 작성/삭제하고 돌아와 서버 댓글 수를 확인한다. 이번 모의 검사에서는 실제 서버 쓰기를 대신했다고 주장하지 않는다.
4. 새로고침은 첫 구간부터 새로 읽는다. 전체 조건의 최신 글은 명시적 최신 목록 새로고침으로 확인한다.
5. JavaScript를 끄고 피드의 실제 제목/미리보기/댓글 수와 다음 목록 링크를 확인한다.

기존 8443 개발 서버와 8082 Spring은 보존했다. 직접 시작한 8451/8452 프로덕션 검증 서버와 모의8856은 검사 종료 후 정리한다. 현재 변경은 사용자 검토를 위한 미커밋 상태다.
