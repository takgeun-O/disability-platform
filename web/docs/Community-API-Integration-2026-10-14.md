# 커뮤니티 디자인 및 Spring API 연결 기록

> 후속 변경: [커뮤니티 리뷰 반영 기록](Community-Review-Fixes-2026-10-14.md)에서 SSR 최초 조회·스크롤/초점·범위 밖 페이지·모바일 베스트 숨김을 적용했습니다. 아래 본문은 최초 구현 당시 기록입니다.

- 날짜: 2026-10-14 (Asia/Seoul)
- 저장소: `/Users/tackeunoh/Developer/iyum`
- 브랜치 유지: `feat/community-posts-ui`
- 시작 HEAD: `88650cf1443f635bc0d36e9bd20c83c607576c6f`
- 시작 상태: staged/unstaged/미추적 변경 없음. 현재 변경은 검토용으로 미커밋/unstaged 상태. 기존 브랜치/HEAD 유지, stage/commit/push 없음. typegen/build가 바꾼 생성 선언 next-env.d.ts는 시작 내용으로 되돌려 변경에 포함하지 않음.
- 루트와 web AGENTS 확인. 기존 문서의 모의 인증/커뮤니티 설명보다 최신 로컬 소스를 기준으로 구현.
- 참고: 사용자가 제공한 `IYUM 커뮤니티 개편 시안.png`. 시안의 예시 데이터·랭킹을 실제 데이터로 이식하지 않음.

## 구현 범위와 경로

| 경로 | 역할 | 주요 파일 (`web/` 기준) |
| --- | --- | --- |
| `/community` | 실제 목록에 대한 커뮤니티 진입 화면 | `src/app/community/page.tsx`, `src/features/CommunityHome.tsx` → `PostList` |
| `/community/posts` | 같은 실제 목록, 필터·페이지 URL 공유 | `src/features/PostList.tsx` |
| `/community/posts/[postId]` | 공개 상세·로딩·404·오류·목록 복귀 | `src/features/PostDetail.tsx` |
| `/community/posts/create` | 세션 확인·로그인 안내·입력·등록·오류·이탈 확인 | `src/features/PostCreate.tsx` |

`community/layout.tsx`에서 모의 상태 Provider를 제거하고 전용 CSS를 불러온다. 참조가 없어진 `src/lib/mock-posts.tsx`를 제거했다. 공통 인증 API/상태/NavigationGuard 소스와 Spring 소스는 변경하지 않았다.

새 보조 파일:

- `src/lib/community-posts.ts`: enum 표시, URL·복귀 주소 정규화, Unicode 입력 규칙, 날짜와 스크롤 복원.
- `src/lib/community-api.ts`: 실제 GET/POST, HTTP·JSON 구조 검사, 기능별 오류 변환. 기존 `getCsrfContext` 재사용.
- `src/components/community/CommunityParts.tsx`: 소개 영역, 분류 태그, 독립된 베스트글 준비 영역.
- `src/components/community/CommunityDialog.tsx`: 필터·기존 이탈 확인의 표시, Escape/Tab/초점 복귀. 새 전역 이탈 방지 체계를 만들지 않음.
- `src/styles/community.css`: 기존 IYUM 토큰을 재사용하는 커뮤니티 범위의 스타일.

주변 수정은 `HomePage.tsx`, `SearchResults.tsx`의 예시 게시글 링크·설명만이다. 가짜 ID가 실제 게시글 ID와 우연히 겹치는 문제를 막기 위해 실제 목록으로 연결한다. 홈 전체 데이터 연결, P03 콘텐츠, 인증 화면 개편은 하지 않았다.

## 시안에서 조정한 부분

- 기존 로고·공통 헤더를 유지한다. 모바일 로그인·회원가입/회원 메뉴도 기존 기능대로 남겼다.
- 작은 청색 소개 영역, 네이비 제목, 청록색 버튼, 제목 위 분류 태그, 선으로 나눈 목록을 적용했다. 큰 장식 이미지 대신 기존 심벌을 낮은 불투명도로 사용했다.
- 목록 너비는 기존 1280px 컨테이너, 데스크톱 베스트 열 330px, 상세 읽기 폭 900px, 작성 폭 840px이다. 본문 16~17px, 제목 17~18px, 주요 제목 28~34px를 기준으로 한다.
- 흰색/옅은 청색·`--iyum-ink`, `--iyum-muted`, `--iyum-primary`, `--iyum-border`를 재사용한다. 44px 조작 높이와 명확한 초점선을 유지한다.
- 베스트 API가 없으므로 “베스트글을 준비하고 있어요.”만 표시한다. 모바일에서는 제목과 한 줄 안내로 줄였다. 일반 최신 글·시안 순위를 대신 넣지 않았다.
- 정렬은 최신순 텍스트, 페이지 버튼은 API 총 페이지에 따라 최대 세 번호와 이전/다음이다. 320px에서도 44px 버튼을 유지한다.
- 모의 댓글·좋아요·조회수·해결 상태·이미지 첨부 조작을 제거했다. 상세와 작성에 준비 상태를 짧게 안내한다.
- 내용은 일반 텍스트다. 임의 HTML을 실행하지 않으며 긴 문자열과 줄바꿈을 처리한다.

## 실제 API 계약 확인

`PostController`, 요청/응답 DTO, `PostInputPolicy`, `PostType`, `PostTopic`, `PostReadService`, `GlobalExceptionHandler`, `SecurityConfig`, `DevCorsConfig`, 관련 Controller 테스트를 확인했다. 사용자가 제시한 세 엔드포인트는 실제 소스와 일치했다.

| 요청 | 성공 | 비고 |
| --- | --- | --- |
| `GET /api/v1/community/posts` | 200, `items/page/size/totalElements/totalPages/hasNext` | 공개 조회, page 0부터, 이 UI는 size 20, 최신 작성시각·ID 순 |
| `GET /api/v1/community/posts/{id}` | 200, 상세 | 없는/삭제된 글 404 |
| `POST /api/v1/community/posts` | 201, `{ id }` | 로그인 세션+새 CSRF, 서버에서 현재 ACTIVE 상태 확인 |

요청 필드: `type`, `topic`, `title`, `content`. 작성자 ID는 전송하지 않는다.

- type: `GENERAL` 일반 / `QUESTION` 질문·답변 / `REVIEW` 경험·후기
- topic: `DAILY` 자유·일상 / `HEARING_AID` 보청기 / `COCHLEAR_IMPLANT` 인공와우 / `TREATMENT_REHABILITATION` 치료·재활 / `COMMUNICATION` 의사소통 / `EMPLOYMENT` 취업·직장 / `WELFARE_LIFE` 복지·생활
- author: `{ memberId, nickname }`; 작성일은 UTC Instant 문자열이며 표시에는 Asia/Seoul 날짜를 사용한다.
- 제목: Java strip 후 1~120 코드 포인트. 본문: 공백만 불가, 최대 10,000 코드 포인트, 원문 공백/줄바꿈 유지.
- 오류: `VALIDATION_FAILED`와 `fieldErrors`, `INVALID_REQUEST_BODY`, `INVALID_REQUEST_PARAMETER`, `INVALID_PAGE_REQUEST`, `AUTHENTICATION_REQUIRED`, `CSRF_TOKEN_INVALID`, `POST_WRITE_NOT_ALLOWED`, `POST_NOT_FOUND` 등을 구분한다.

브라우저가 HTTP 성공만으로 저장/조회를 성공 처리하지 않는다. JSON Content-Type·필수 필드·enum·작성자·날짜·페이지 정보·유효 ID를 확인한다. 임의 서버 메시지는 그대로 노출하지 않는다. 읽기 실패를 모의 데이터로 대체하지 않는다.

## 유지한 UX

- 목록의 선택 중 조건은 `FilterForm` 로컬 상태. 적용한 조건은 URL. 표시 중인 결과는 그 결과의 조건과 함께 snapshot으로 보관한다.
- 조회 중/실패 시 이전 목록과 이전 결과의 조건 표시를 유지한다. URL은 시도한 조건이며 별도 안내에서 조회 중/실패 조건을 명시한다. 재시도·취소·빈 결과를 구분한다.
- URL 전환 시 이전 요청 무효화 + AbortController로 지연 성공/실패가 새 결과를 덮지 않게 한다.
- 상세 링크의 검증된 `from`과 sessionStorage의 스크롤 숫자로 필터·페이지·위치를 복원한다. 저장소에는 게시글 본문/회원/토큰을 넣지 않는다.
- 세션 확인 중에는 로그인 필요 화면으로 단정하지 않는다. 비로그인 작성은 기존 로그인 페이지의 안전한 복귀 흐름을 사용한다.
- 전송 중 중복 등록/입력 변경을 막고, 오류 필드 또는 안내에 초점을 준다. 저장 확인 전에는 dirty를 해제하지 않는다.
- 세션 만료/로그아웃 후에도 dirty 폼과 입력을 남긴다. 이동에 동의하면 입력은 사라지며 로그인 왕복 초안 영구 저장은 이번 범위가 아니다.
- POST 결과가 불확실하면 “이미 저장되었을 수 있음”과 새 탭 목록 확인을 제공한다. POST 자동 재전송 없음.
- 기존 회원가입 PENDING→이메일 인증 ACTIVE→사용자 로그인, 이메일 재전송, 세션 복원, CSRF, 지연 로그아웃 보호, 5초 알림 동작은 유지한다.

## 검증 결과

실행 결과는 아래 표와 [캡처 목록](community/2026-10-14/README.md)에 구분한다. 개발/프로덕션 상태나 모의 검증을 실제 DB 검증으로 섞지 않는다.

| 검사 | 결과/범위 |
| --- | --- |
| `pnpm test` | 159 통과. 기존 132 + 커뮤니티 계약/Unicode/오류/자동 재전송 금지 등 27 |
| `pnpm typecheck` | 통과 |
| `pnpm lint` | 오류·경고 없음. 제거된 모의 상세/작성 이미지의 기존 img 경고도 없어짐 |
| `pnpm build` | 통과. 아래 프로덕션 경로 검사 전 최종 재빌드 |
| `community-browser.mjs` | 모의 API 26개 시나리오 통과: 필터, 빈 결과, 요청 경합, 페이지, Back/Forward/스크롤, 오류, 접근성, 입력 보존, 중복 제출, 본문 비실행 |
| `community-local-browser.mjs` | 실제 Spring/PostgreSQL/Mailpit 7개 묶음 시나리오 통과 |
| `login-browser.mjs` | 기존 모의 인증 회귀 38개 통과. 지연 로그아웃 입력 보존·초점·알림 포함. 마지막 dialog 초점 수정 후 영향받는 같은 화면 회귀 4개도 재통과 |
| `ui-refresh-browser.mjs` | 기존 홈/헤더/회원가입/P03 24개 통과. `/me` 비로그인 확인은 실제 GET, 나머지 인증 상태 조작은 모의 |
| `pnpm test:smoke` | 임시 프로덕션 8444 서버에서 16개 공개 경로·없는 경로 404·robots 통과. 실제 인증 API 검사와 별개 |
| `git diff --check` | 통과 |

기존 검사 변경 이유: 저장 API가 없는 모의 이미지 첨부를 제거했으므로 로그인 회귀의 “첨부 삭제 후 재확인”을 “주제 변경 후 재확인”으로 바꿨다. 같은 편집 이벤트 보호를 계속 검증한다. 기존 UI 검사에서 비로그인으로 작성하던 전제는 로그인 모의를 먼저 설정하도록 수정했다. SSR 스모크는 고정 가짜 게시글 제목 대신 초기 상세 로딩 화면을 확인하고, 실제 상세 데이터/404는 브라우저에서 검증한다.

초기 새 브라우저 검사의 대기 조건이 React 개발 모드 초기 요청 수와 Next 경로 알림을 실제 상태로 혼동해 수정했다. 실제 모바일 Tab 순환 문제는 `CommunityDialog`에서 보완했다. 저장소의 oxfmt 0.2.0이 인라인 타입 구분자를 제거하는 문제를 발견해 직접 복구 후 타입·단위·lint를 재검사했다. 포매터 버전이나 설정은 이번 범위에서 바꾸지 않았다.

### 실제 검증과 모의 검증의 경계

실제: 기존 dev 서버의 공개 목록/상세/404, 필터 선택 대기·적용·빈 결과·모바일 취소/적용, 가입·Mailpit 인증·로그인 복귀, POST 201과 중복 제출 방지, 생성 글 상세/목록/새로고침, CSRF 누락 403, 서버 로그아웃 후 작성 401과 입력 보호, 비로그인 공개 조회.

모의: 여러 페이지에 걸친 63개 글, 지연/역순 응답, HTTP 500·네트워크 단절·손상 JSON/HTML, 계정 작성 권한 403, 서버 필드 오류, 성공 응답 유실, 정밀한 초점·장시간 알림 타이머. 실제 장애나 비활성 회원 상태를 만들기 위해 DB를 변경하지 않았다.

실제 DB 글이 적어 여러 페이지/긴 목록 스크롤은 모의 63개 목록으로 검증했다. 실데이터를 대량 생성하지 않았다. 실제 서비스 배포 환경 CORS, 외부 메일, 다른 브라우저, 스크린리더 음성 읽기는 검증하지 않았다. 백엔드 전체 테스트는 서버 코드·설정 변경이 없어 다시 실행하지 않았다. 기존 재전송의 모든 메일 실패 시나리오는 재실행하지 않았으며 관련 단위 테스트는 전체 실행에 포함된다.

## 생성 데이터와 서버

실제 검증으로 다음 데이터만 추가했다. 기존 회원/게시글/메일/볼륨은 삭제하지 않았다.

- 회원 ID `20`, 닉네임 `UI_mv7nnnbi`, 가상 이메일 `iyum-community-mv7nnnbi@example.test`, 최종 ACTIVE
- 인증 메일: 이 검증용 Mailpit 메일 1건
- 게시글 ID `2`, GENERAL / WELFARE_LIFE, 제목 `[로컬 UI 검증] 커뮤니티 작성과 조회 확인`
- CSRF/세션 만료 검사는 저장 거절을 확인했고 추가 글 없음. 검증 종료 시 해당 브라우저 세션은 로그아웃 상태.
- 비밀번호·쿠키·토큰·메일 원문은 결과 파일에 보관하지 않음.

기존 서버: Next dev `127.0.0.1:8443` PID 41303, Spring dev `8082` PID 33858, 로컬 PostgreSQL 컨테이너 포트 5434, Mailpit 1025/8025. 모두 재사용했으며 재시작/종료하지 않았다. 프로덕션 스모크·주요 구간 캡처용으로 추가한 8444 서버는 검사 후 종료했다. 기존 8443/8082 서버는 계속 실행 중이다. 기존 CORS가 커뮤니티 GET/POST와 개발 Origin을 이미 허용하므로 백엔드 설정 변경은 없다.

## 다시 실행하는 방법

`web/`에서 Node 22/24와 pnpm 10.34.3을 사용한다. 브라우저 검사는 기존 검사와 같이 Playwright가 필요하다. 현재 환경은 외부 런타임의 Playwright를 사용했으며 의존성을 새로 설치하지 않았다.

```sh
pnpm test
pnpm typecheck
pnpm lint
pnpm build

PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs \
node tests/community-browser.mjs
```

실제 검증은 추가 데이터 생성에 동의할 때만 `IYUM_RUN_LOCAL_COMMUNITY_TEST=true`와 같은 Playwright 경로를 지정해 `node tests/community-local-browser.mjs`를 실행한다. **실행할 때마다 회원/글 각 1건이 추가**되며 자동 삭제하지 않는다. 기본 실행은 SKIP이다.

직접 확인: `http://127.0.0.1:8443/community`에서 필터 선택→적용→제목→목록 복귀, 개발자 도구 390/320px에서 필터 취소/적용, 기존 계정 로그인 후 글쓰기→필드 오류→등록→새로고침, 저장 전 헤더 이동·로그아웃 취소를 확인한다.

## 남은 범위

베스트 집계, 댓글·좋아요·조회수, 게시글 검색/수정/삭제, 이미지 업로드, 홈 실제 목록 연동은 후속 작업이다. 초안의 로그인 왕복/새로고침 영구 보존도 하지 않았다. 상세/목록의 초기 HTML에는 화면 구조·설명·로딩이 있고 실제 데이터는 브라우저에서 Spring API를 읽는다. SSR 게시글 데이터 선조회/검색엔진 공개 정책은 별도 결정이 필요하다.

학습 설명은 [React 코드 읽기 문서](Community-React-Learning-2026-10-14.md)를 참고한다. 커밋·push·PR·병합·배포는 수행하지 않았다.
