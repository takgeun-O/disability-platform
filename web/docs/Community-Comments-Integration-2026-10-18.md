# 커뮤니티 댓글 API 연동 — 2026-10-18

## 기준과 범위

- 브랜치 `feat/community-comments`, HEAD `4a8e2468c62ee927f51fb47b2ff29b40e75ce59b`.
- 시작 시 작업 트리는 깨끗했다. 현재 변경은 이 작업의 미커밋 변경이며 커밋·push·병합·배포하지 않았다.
- `iyum-preview` worktree는 변경하지 않았다. API 계약·도메인 로직·DB 스키마·인증 정책도 유지했다.
- 백엔드는 `global/config/DevCorsConfig.java` 및 `DevCorsConfigTest.java`만 변경했다.

## 변경 파일과 읽는 순서

1. `src/lib/community-comments.ts`: 댓글 DTO/응답 검사, `validateComment`, `getComments`, `changeComment`, `mergeComments`, 안전한 오류 안내.
2. `src/lib/community-server.ts`의 `readPublicComments`: 서버 공개 조회. 인증 정보 없이 no-store, 실패는 댓글 영역의 오류로 반환.
3. `src/app/community/posts/[postId]/page.tsx`: 첫 댓글 페이지를 서버 조회하여 상세에 전달. 게시글 metadata/notFound 흐름은 유지.
4. `src/features/PostDetail.tsx`: 게시글과 별도 상태를 가진 Comments 연결. 안전하게 검증한 목록 복귀 주소를 댓글 로그인 복귀 주소에도 전달.
5. `src/components/community/Comments.tsx`: 첫 목록, 커서 추가 조회, 작성·단일 인라인 편집·삭제 모달, 집계된 dirty, 요청 수명과 초점 복귀.
6. `src/styles/community.css`: 댓글 영역에 한정한 스타일. 기존 토큰·버튼 재사용.
7. `tests/community-comments.test.mjs`, `community-comments-browser.mjs`, `community-comments-local.mjs`: 단위·모의 브라우저·opt-in 실제 검증.
8. `tests/community-edit-delete-browser.mjs`: 기존 게시글 모의 서버가 댓글 요청에 게시글 DTO를 반환하지 않도록 빈 댓글 응답을 별도 분기. 기존 검증 조건은 유지.
9. `AGENTS.md`: 댓글 실제 연동 범위 명시. 좋아요·인기글 집계는 미구현으로 유지.

## 계약과 화면 상태

- GET `/api/v1/community/posts/{postId}/comments?size=20`: 공개, credentials omit, no-store. 최초 페이지는 HTML에 표시하며 hydration 후 중복 조회하지 않는다.
- `nextCursor.createdAt`은 원문 문자열로 유지한다. 날짜 표시에만 Date를 쓰고 요청은 URLSearchParams로 원문 시각과 ID를 전달한다.
- 오래된 순, 같은 시각의 ID 순서를 유지한다. 추가 조회는 commentId로 중복 제거한다. 로드한 개수를 전체 댓글 수로 표현하지 않는다.
- POST는 `{content}`와 201/양수 ID, PUT·DELETE는 204를 검증한다. 204 JSON 파싱은 하지 않는다. DELETE에는 본문·Content-Type을 붙이지 않는다.
- 매 변경 요청 전 기존 `getCsrfContext()`를 호출하고 세션 쿠키를 포함한다. 자동 재전송은 없다. CSRF 준비 실패와 서버 처리 결과 불확실 상태를 구분한다.
- 입력은 Java 공백 규칙과 코드 포인트 2,000자를 검사하며 앞뒤 공백·줄바꿈을 그대로 보낸다. HTML로 해석하지 않는다.
- 내 회원 ID와 댓글 작성자 ID가 같은 경우만 관리 버튼을 표시한다. 게시글 작성자에게 타인의 댓글 관리 권한을 부여하지 않는다.
- 작성 성공 후 제출 초안을 정리하고 성공 안내를 먼저 표시한다. 이어지는 목록 조회 실패는 작성 실패가 아니다.
- 첫 20개 뒤에 생성된 댓글은 서버 순서에 임의 삽입하지 않는다. 등록 성공 안내와 ‘댓글 더 보기’로 확인한다. 매우 긴 목록은 여러 번 더 보기가 필요하다.
- 수정 성공 후 보이는 페이지 범위만 다시 읽는다. 서버가 반환한 updatedAt만 표시한다. 새 조회가 실패하면 기존 목록과 성공 안내를 유지한다.
- DELETE 성공 또는 COMMENT_NOT_FOUND일 때 해당 ID만 제거하며, 삭제된 버튼 대신 목록 새로고침으로 초점을 옮긴다. 취소/Escape는 원래 삭제 버튼으로 복귀한다.

## 요청 수명과 입력 보호

`Comments`가 새 작성 초안과 한 개의 수정 초안을 함께 관리하고 **useUnsavedChanges를 한 번만 호출**한다. 수정 원본 스냅샷은 서버 목록과 분리되어 새 조회가 dirty 기준을 바꾸지 않는다.

`useNavigationRequest`는 언마운트 이전이라도 공통 이탈 승인이 발생하면 무효가 된다. CSRF 이후 POST/PUT/DELETE 전과 응답 처리 전에 유효성을 확인한다. 이탈 확인을 취소하면 요청은 여전히 유효하다. 이미 서버에 전송한 요청을 취소·롤백했다고 안내하지 않는다.

조회에는 별도 증가 번호를 사용한다. 변경을 시작하면 이전 GET을 무효화하고, 삭제한 ID도 기억하여 오래된 응답이 댓글을 되살리지 않게 한다. 댓글 변경은 게시글 mutation version을 올리지 않으므로 전체 PostDetail을 재마운트하지 않는다. 재진입 시에는 새 컴포넌트 상태이며 이전 요청이 새 초안을 변경하지 않는다.

초안은 메모리에만 존재한다. 로그인 왕복·탭 종료 후 복원은 제공하지 않으며 안내한다. 댓글 로그인 링크는 안전한 상세/원래 목록 맥락을 전달한다.

## CORS

개발 프로필에 아래 두 경로만 추가했다. 기존 Origin, credentials, 허용 헤더를 복사한다.

| 경로 | 메서드 |
|---|---|
| `/api/v1/community/posts/*/comments` | GET, POST, OPTIONS |
| `/api/v1/community/posts/*/comments/*` | PUT, DELETE, OPTIONS |

Origin 와일드카드·API 프록시·CSRF 해제는 추가하지 않았다. Mockito/MockMvc에서 허용 preflight, 다른 Origin/메서드 거절, 실제 GET CORS 헤더, 로그인한 변경 요청의 CSRF 거절을 확인했다.

## 이번 실행 결과

| 실행 | 결과 |
|---|---|
| `pnpm test` | 217 통과, 0 실패/skip |
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm build` | 프로덕션 빌드 통과 |
| `./gradlew test --tests '*DevCorsConfigTest'` | 7 통과 |
| `community-comments-browser.mjs` | 프로덕션 모의 검사 24 통과 |
| `community-edit-delete-browser.mjs` | 기존 모의 회귀 29 통과 |
| `community-topics-browser.mjs` | 기존 모의 회귀 23 통과 |
| `login-browser.mjs` | 모의 인증·로그아웃 회귀 38 통과, 실제 계정 옵션은 별도 댓글 검사에서 실행 |
| `community-comments-local.mjs` | 재시작 후 실제 검증 5개 묶음 통과 |
| `community-public-browser.mjs` (8443) | 실제 공개 조회/SSR/목록 복귀 6 통과 |
| `community-404-browser.mjs` (production) | 5 통과, 기존 무JS 본문·복귀 링크 실패 2건 유지 |
| `git diff --check` | 통과 |

타입 검사·빌드는 `/private/tmp/iyum-edit-build-final/web`의 최신 소스 복사본에서 실행했다. 기존 8443 개발 서버의 .next를 빌드로 덮어쓰지 않았다. Node 24.21.0, pnpm 10.34.3, Next 16.3.6. 전체 백엔드 테스트는 이번 범위에 불필요하여 실행하지 않았다.

### 실패 및 재검증을 구분한 기록

- 첫 실제 실행: 회원가입·메일 인증·로그인·게시글 생성까지 성공. 실행 중인 백엔드는 이전 CORS 설정이어서 preflight에 허용 Origin 헤더가 없었다. 성공으로 기록하지 않았다. 사용자에게 재시작을 요청했고, 사용자가 재시작한 후 전체 댓글 흐름이 통과했다.
- 초기 모의 검사에서 Next 자체 route announcer와 댓글 오류의 role=alert가 겹쳤다. 선택자를 댓글 영역으로 한정한 뒤 검사했다. 기능 검증 조건을 삭제하지 않았다.
- 임시 production 8452에서 실제 공개 목록 최초 SSR은 통과했지만, 페이지 변경 후 브라우저 GET이 CORS에 막혔다. 정확한 DOM을 기다리는 추가 확인도 시간 초과했다. 허용 Origin인 **8443에서 동일한 기존 검사 6건이 통과**했다. CORS를 넓히지 않았다.
- 기존 게시글 무JS 404: 일반 브라우저·크롤러 UA 모두 HTTP404/noindex 유지. JS 사용 시 안내와 안전한 복귀는 통과. 무JS 표시 제목·링크 2건은 여전히 실패다. 댓글 기능이 해결한 것으로 표시하지 않는다.

### 실제 생성한 로컬 데이터

| 실행 | 회원 | 게시글 | 댓글 | 최종 상태 |
|---|---|---|---|---|
| 재시작 전 중단 | memberId 31 (`iyum-community-mvcjoid8@example.test`) | 11 | 미생성 | 테스트 회원 ACTIVE, 테스트 글 남아 있음 |
| 재시작 후 성공 | memberId 32 (`iyum-community-mvcjr8cl@example.test`) | 12 | 3 | 테스트 회원 ACTIVE, 게시글·댓글은 UI로 논리 삭제 |

기존 사용자 데이터는 변경하지 않았고 DB를 직접 수정하거나 초기화하지 않았다. 비밀번호·쿠키·CSRF·메일 원문·인증 토큰을 기록하지 않았다. 실제 브라우저는 8443 → 8082로 요청하며 Playwright route mock을 사용하지 않았다. preflight 응답 헤더 및 실제 UI 요청/상태를 확인했다.

## 재실행 방법

일반 명령은 web에서 `pnpm test`, `pnpm lint`, `pnpm typecheck`, `pnpm build`.

모의 브라우저 검사는 SSR mock(기본 8856)과 같은 주소를 `IYUM_API_INTERNAL_URL`로 지정한 별도 Next production 서버(기본 8451)가 필요하다. 브라우저 API 공개 주소는 로컬 8082. `PLAYWRIGHT_MODULE`은 환경에 설치된 playwright의 절대 모듈 경로로 지정할 수 있다. 새 프로젝트 의존성을 추가하지 않았다.

```sh
BASE_URL=http://127.0.0.1:8451 node tests/community-comments-browser.mjs
# 실제 검사는 새 계정·게시글·댓글이 생기는 명시적 opt-in이다.
IYUM_RUN_LOCAL_COMMENTS_TEST=true BASE_URL=http://127.0.0.1:8443 \
  API_BASE_URL=http://127.0.0.1:8082 node tests/community-comments-local.mjs
```

실제 검사는 Spring dev/DB/Mailpit 8025가 실행 중이고, 새 CORS 설정을 로드한 상태여야 한다. 별도 Origin의 production 서버에서 브라우저 API를 검사하면 현재 CORS 정책상 거절된다.

## 브라우저 직접 확인

1. `http://127.0.0.1:8443/community`에서 게시글을 연다. 비로그인 상태의 댓글과 로그인 안내를 확인한다.
2. 로그인한 뒤 댓글 등록 → 인라인 수정 → 수정 취소/저장 → 삭제 취소/확정을 확인한다.
3. 댓글 입력 중 로고나 목록 링크를 누르고 계속 작성/나가기를 확인한다.
4. 다른 작성자 댓글에는 관리 버튼이 없는지 확인한다. 댓글이 20개를 넘으면 더 보기로 확인한다.
5. 390/320px에서도 줄바꿈·버튼·모달을 확인하고 Tab/Shift+Tab/Escape를 사용한다.

## 검증 자료

- [모의 댓글 결과](community-comments-2026-10-18/mock/results.json)
- [실제 결과](community-comments-2026-10-18/real-after-restart/real-comments-results.json)
- [실제 데스크톱](community-comments-2026-10-18/real-after-restart/real-comments-1440.png)
- [실제 모바일 390](community-comments-2026-10-18/real-after-restart/real-comments-390.png)
- [실제 모바일 320](community-comments-2026-10-18/real-after-restart/real-comments-320.png)
- `edit-delete-regression/`, `topics-regression/`, `auth-regression/`, `404-regression/`, `public-allowed-origin/`은 같은 검증 폴더 아래에 있다.

운영 배포, 실제 외부 SMTP, 다중 탭 동시 편집/서버 충돌 감지는 이번 검증 범위가 아니다. API에 없는 전체 댓글 수·단건 댓글 GET·좋아요·인기 집계는 추가하지 않았다.

## 종료 상태

- 8443 기존 Next 개발 서버 유지: http://127.0.0.1:8443 (PID 38875).
- 사용자가 재시작한 8082 Spring 서버 유지 (PID 67568).
- 직접 실행한 8451/8452 production 검증 서버와 각 테스트의 8856 mock 서버는 종료했다.
- 브랜치/HEAD 변경 없음. 변경 파일과 새 테스트·문서·캡처는 미커밋 상태로 남겼다.
