# 로그인·로그아웃 Spring API 통합 — 2026-10-12

## 범위와 실행 구조

`feat/login`의 프론트엔드를 기존 Spring 세션 인증에 연결했다. 로그인 화면의 모의 계정·타이머·프로토타입 결과 쿼리와 로그인 유지 체크박스를 제거했다. 결과는 서버 응답으로 결정한다. 커뮤니티의 모의 글·댓글, 기존 디자인과 공개 페이지 내용은 유지했다.

```text
브라우저 http://127.0.0.1:8443
  Login.handleSubmit → validateLoginForm
  → useAuth().signIn → auth-session.signIn
  → getAuthCsrf → GET /api/v1/auth/csrf
  → auth-api.login → POST /api/v1/auth/login
  ← 200 + 검증된 Member
  → 전역 회원 상태 반영 + 비밀번호 입력 초기화
  → GET /api/v1/auth/csrf (로그인 후 새 세션용)
  → safeReturnPath로 확인한 내부 경로 이동

모든 API 요청은 브라우저 → http://127.0.0.1:8082 로 직접 전송한다.
```

기존 `NEXT_PUBLIC_API_BASE_URL`을 사용한다. 인증 라이브러리·JWT·Next 중계 API는 추가하지 않았다. 백엔드 소스와 API 계약도 변경하지 않았다. `app/layout.tsx`는 Server Component와 기존 metadata를 유지하고, `AuthProvider`와 헤더의 `AuthControls`만 인증 상태를 읽는 Client Component로 연결했다. Provider는 children을 그대로 렌더링하므로 `/me` 확인 중에도 공개 본문을 볼 수 있다.

## 먼저 읽을 파일과 핵심 함수

아래 경로는 `web/` 기준이다. 처음에는 1 → 2 → 3 → 4 → 5 → 6 순서로 읽고, 마지막에 Spring 구현과 대조하면 된다.

| 순서 | 파일 | 핵심 함수·역할 |
| --- | --- | --- |
| 1 | `src/features/Login.tsx` | 입력 state, `handleSubmit`, `navigateAfterLogin`, `prepareAndContinue`, 결과 JSX |
| 2 | `src/lib/login-form.ts` | `validateLoginForm`: 로그인에 필요한 검증만 수행 |
| 3 | `src/lib/auth-session.ts` | `createAuthSession`, `signIn`, `signOut`, `refresh`, `readCurrent`: 요청 순서와 공유 상태 변경 |
| 4 | `src/lib/auth-api.ts` | `getAuthCsrf`, `login`, `getCurrentMember`, `logout`, 내부 `request`, `responseError`: HTTP 계약과 안전한 오류 변환 |
| 5 | `src/components/AuthProvider.tsx` | `AuthProvider`, `useAuth`: 하나의 세션 상태를 화면과 헤더에 공급 |
| 6 | `src/components/AuthControls.tsx` | 닉네임·회원 메뉴, `handleLogout`, 작성 중 확인, 로그아웃 피드백 |
| 7 | `src/lib/signup-api.ts` | 공유 `getCsrfContext`, 기존 가입·인증용 `getCsrfToken` 인터페이스 유지 |
| 8 | `src/lib/navigation.ts` | `safeReturnPath`: 외부·실행 URL·인코딩 우회·로그인 반복 이동 방지 |
| 9 | `src/components/Shell.tsx`, `src/app/layout.tsx` | 헤더 Client 영역과 Provider 배치; Server 구조 유지 |
| 10 | `tests/auth-api.test.mjs`, `tests/auth-session.test.mjs`, `tests/navigation.test.mjs`, `tests/login-browser.mjs` | HTTP 응답 검사, 비동기 상태 경합, 브라우저 UI와 실제 세션 검증 |

백엔드는 `server/iyum-api/src/main/java/io/github/takgeun/iyum/` 아래 `auth/api/LoginController.java`, `CurrentMemberController.java`, `auth/api/dto/LoginRequest.java` 및 `global/config/SecurityConfig.java`, `SessionSecurityConfig.java`, `DevCorsConfig.java`를 함께 읽는다. 로그아웃은 Spring Security 필터 설정에서 처리하므로 별도 LogoutController를 찾을 필요가 없다.

## 질문 1. 이메일과 비밀번호는 어디에 저장되는가?

`Login.tsx`의 `const [email, setEmail] = useState('')`, `const [password, setPassword] = useState('')`가 입력값을 보관한다. input의 `value`가 state를 읽고, `onChange`가 `setEmail` 또는 `setPassword`로 새 값을 넣는다. React가 바뀐 값으로 화면을 다시 그린다. 이것을 제어 입력이라고 한다.

비밀번호는 로그인 화면 메모리에만 둔다. 요청 객체도 처리 중인 함수의 지역 변수다. 비밀번호·회원 정보·로그인 여부·CSRF를 localStorage/sessionStorage에 저장하지 않는다. 세션 쿠키는 브라우저와 Spring이 관리하며 프론트엔드 코드에서 쿠키 값을 읽거나 조립하지 않는다.

## 질문 2. 로그인 버튼을 누르면 어떤 함수가 실행되는가?

form의 `onSubmit={handleSubmit}`이 실행된다. 버튼 클릭뿐 아니라 Enter 제출도 같은 경로다. `event.preventDefault()`는 브라우저의 기본 폼 전송과 페이지 새로고침을 막는다.

1. `validateLoginForm`으로 기본 오류를 확인한다. 오류가 있으면 요청 없이 메시지를 표시하고 첫 오류 입력으로 초점을 옮긴다.
2. `inFlightRef`를 즉시 잠근다. React가 다시 그리기 전에 들어오는 두 번째 submit도 차단한다.
3. `auth.signIn`을 기다린다. 전역 `pending`이 버튼과 입력을 비활성화하고 헤더에도 진행 상태를 알린다.
4. 성공하면 검증된 경로로 이동한다. 실패하면 catch에서 fieldErrors/feedback state를 설정한다.
5. finally는 재제출 잠금을 해제한다.

비밀번호 필수 여부와 UTF-8 72바이트 제한은 `LoginRequest`와 맞췄다. 한글은 문자 수와 바이트 수가 다르므로 `TextEncoder`를 사용한다. 회원가입의 최소 길이·문자 조합 규칙은 로그인에 적용하지 않는다. 공백 여부를 검사할 때만 trim을 사용하며 서버에는 원문을 보낸다. 이메일 정규화의 최종 책임도 서버에 있다.

## 질문 3. 서버에 보낼 객체는 어디서 만드는가?

`Login.handleSubmit`의 `const values = { email, password }`가 입력을 하나의 객체로 묶는다. 이 객체가 `auth.signIn(values, callback)` → `client.login(values, csrf)`로 이동한다.

`auth-api.login`은 `JSON.stringify({ email: values.email, password: values.password })`로 HTTP 요청 본문을 만든다. 비밀번호를 trim하거나 소문자로 바꾸거나 클라이언트에서 해시하지 않는다. 비밀번호 확인값과 회원가입 약관은 로그인 DTO에 없으므로 보내지 않는다.

## 질문 4. 실제 fetch는 어느 함수에서 실행되는가?

- login/me/logout: `auth-api.ts`의 내부 `request` 함수.
- csrf: `auth-api.getAuthCsrf` → 기존 `signup-api.ts`의 `getCsrfContext` 안의 fetch.

| 설정 | 이유 |
| --- | --- |
| `credentials: 'include'` | 다른 포트의 API에도 Spring 세션 쿠키를 주고받음 |
| `cache: 'no-store'` | 회원·인증 응답을 HTTP 캐시에서 재사용하지 않음 |
| `redirect: 'error'` | 리디렉션을 따라 POST를 재전송하거나 HTML 화면을 성공으로 오인하지 않음 |
| `referrerPolicy: 'no-referrer'` | 현재 페이지 URL을 인증 요청 Referer로 보내지 않음 |
| `Accept: application/json` | JSON 응답 계약 명시 |
| `AbortSignal.timeout(15_000)` | 응답 지연이 무한한 로딩이 되지 않도록 제한 |
| CSRF 응답의 `headerName`, `token` | 현재 서버 계약인 `X-CSRF-TOKEN`을 검증하고 해당 이름으로 POST 헤더 구성 |

fetch는 400·401·403·500에서도 보통 Response로 완료된다. 따라서 정확한 상태 코드와 본문을 직접 확인한다. TypeScript의 `Member` 타입만으로 외부 JSON이 안전해지지는 않는다. 런타임에 양의 정수 memberId, 이메일, 비어 있지 않은 nickname, USER/ADMIN role을 검사하며 추가 필드는 회원 상태에 복사하지 않는다.

## 질문 5. 성공 후 무엇이 바뀌고 헤더에는 어떻게 반영되는가?

`auth-session.signIn`은 정확한 200과 Member를 받은 즉시 `setMember(member)`를 호출해 status를 authenticated로 바꾼다. 이어서 Login이 전달한 callback이 `setPassword('')`, `setShowPw(false)`를 실행한다. 이 두 처리는 후속 CSRF GET을 기다리기 전에 끝난다.

`publish`가 구독자에게 변경을 알리면 `AuthProvider`의 `useSyncExternalStore`가 새 상태를 읽는다. `useAuth()`를 사용하는 Login과 AuthControls가 다시 그려지고 헤더에는 닉네임과 로그아웃 버튼이 표시된다.

Spring은 로그인 때 세션 ID를 바꾸고 이전 CSRF 토큰을 폐기한다. 그래서 다음 단계에서 새 CSRF를 조회한다. 이 GET이 실패해도 이미 확인한 회원은 유지하고 비밀번호를 다시 표시하지 않는다. 화면은 “로그인은 완료되었습니다”와 후속 보안 정보 조회 실패를 구분해 안내하며, 계속 이동하거나 GET만 수동으로 다시 할 수 있다. 로그인 POST를 자동 반복하지 않는다.

CSRF는 장기 저장하거나 캐시하지 않는다. 로그인 직후 갱신을 확인한 뒤에도, 다음 로그인·로그아웃 POST는 자신의 실행 시점에 새 GET으로 토큰을 확보한다.

## 질문 6. 새로고침하면 어떻게 복원되는가?

페이지가 새로 만들어지면 프론트엔드 회원 메모리는 사라진다. `AuthProvider`의 `useEffect`가 `session.start()` → `refresh()` → `getCurrentMember()`를 실행한다. 브라우저는 자신이 가진 HttpOnly 세션 쿠키를 `/me` 요청에 붙인다.

| status | 의미와 헤더 |
| --- | --- |
| checking | 아직 서버 확인 중. 확인 문구만 표시 |
| authenticated | 유효한 200 Member 확인. 닉네임·로그아웃 표시 |
| anonymous | 정확한 401/AUTHENTICATION_REQUIRED 확인. 로그인·회원가입 표시 |
| error | 네트워크·500·비정상 본문 등으로 확인 실패. 상태 다시 확인 버튼 표시 |

네트워크 실패는 비로그인의 증거가 아니다. `/me`의 다른 401 코드도 anonymous로 바꾸지 않는다. 공개 페이지 본문은 네 상태와 독립적으로 계속 렌더링한다. 서버 렌더링과 첫 클라이언트 렌더링의 snapshot도 checking으로 맞춘다.

### 느린 요청이 최신 상태를 덮어쓰지 않는 이유

각 조회와 로그인·로그아웃은 증가하는 `generation` 번호를 받는다. 응답을 적용하기 전에 “이 요청의 번호가 현재 번호인가, Provider가 아직 살아 있는가”를 검사한다. 예를 들어 초기 `/me`가 오래 걸리는 동안 로그인에 성공했다면 초기 요청의 401·다른 회원·오류는 모두 무시한다. 로그아웃 후 이전 회원 조회가 돌아와도 같은 원리로 회원을 복원하지 않는다. 진행 중인 인증 변경이 있으면 새 mutation/refresh도 시작하지 않는다.

Provider의 effect cleanup은 `session.stop()`을 실행해 이전 요청을 무효화한다. 개발 모드의 effect 재실행에서도 새 요청만 상태를 갱신한다. 이 순서를 React 없이도 검증하도록 작은 상태 조정 함수를 `auth-session.ts`로 분리했다. 별도 상태 관리 라이브러리는 사용하지 않는다.

## 질문 7. 로그아웃할 때 서버와 화면은 각각 무엇을 하는가?

1. `AuthControls`가 `NavigationGuard.request`를 먼저 호출한다. 작성 중인 글이 있으면 기존 확인창을 띄우고, 취소하면 HTTP 요청도 보내지 않는다.
2. 허용되면 `handleLogout` → `auth.signOut`이 현재 CSRF를 조회하고 logout POST를 한 번 보낸다. ref와 pending으로 중복 클릭을 막는다.
3. Spring Security가 세션과 인증 정보, 기존 CSRF를 정리하고 204를 반환한다.
4. `auth-api.logout`은 204에서 즉시 반환한다. JSON 파싱을 하지 않는다.
5. `signOut`은 메모리 member를 null, status를 anonymous로 바꾼다. 헤더는 로그인·회원가입 링크로 바뀌고 공개 홈으로 이동한다.
6. 다음 POST에서 새 CSRF를 가져온다. 성공 후 별도 CSRF GET을 하지 않으므로 후속 실패 때문에 이전 회원을 복원하는 경로도 없다.

CSRF 오류에는 GET 갱신만 시도하고 수동 재제출을 안내한다. 네트워크·서버 오류처럼 결과가 불확실하면 `/me`로 현재 세션을 조회한다. 회원이 확인되면 로그인 상태를 유지하고 오류를 안내한다. 정상 401이면 “로그아웃 응답은 확인하지 못했지만 현재 비로그인 상태를 확인했다”고 구분해 안내한다. `/me`도 실패하면 상태 확인 실패로 표시하며 로그아웃 성공이라고 주장하지 않는다.

기존 guard의 proceed는 이동 전에 dirty 표시를 해제한다. 로그아웃이 실패해 작성 화면에 남으면 AuthControls가 dirty 표시를 복원한다. 글 내용은 유지되고 다음 이동에도 기존 확인창이 작동한다. 요청을 기다리는 동안 이미 다른 페이지로 이동했다면 경로 변경 번호를 확인해 이전 글의 dirty를 새 화면에 복원하지 않는다.

## 질문 8. 실패 응답은 어떤 과정을 거쳐 JSX에 표시되는가?

```text
fetch Response
 → readJson (HTML·빈 본문·손상 JSON 방어)
 → responseError (요청 종류 + HTTP 상태 + 오류 code)
 → AuthApiError { message, fieldErrors, uncertain }
 → signIn의 필요한 CSRF 갱신 또는 /me 확인
 → Login.handleSubmit의 catch
 → setEmailError / setPwError / setFeedback
 → aria-invalid / aria-describedby / role=alert JSX + 초점 이동
```

서버의 임의 message나 내부 상세 내용을 그대로 출력하지 않고, 클라이언트의 정해진 안전한 문구로 바꾼다.

| 응답 | 처리 |
| --- | --- |
| login 401/LOGIN_FAILED | 이메일 또는 비밀번호 확인 안내 |
| login 403/EMAIL_VERIFICATION_REQUIRED | 인증 필요 안내와 기존 `/register/resend` 링크 |
| login 403/ACCOUNT_UNAVAILABLE | 현재 로그인할 수 없는 계정 안내 |
| login 400/VALIDATION_FAILED | email/password 필드를 해당 오류 state에 연결 |
| login/logout 403/CSRF_TOKEN_INVALID | GET 갱신만 수행, 수동 재제출 안내 |
| 비정상 200·HTML·손상 JSON·알 수 없는 응답 | 성공으로 처리하지 않고 안전한 공통 안내; POST 결과가 불확실하면 /me로 확인 |
| POST 네트워크·500 | 자동 재전송 없이 /me로 세션 확인 |

로그인 응답을 잃었지만 `/me`로 회원을 확인한 경우도 정확한 login 200 성공과 구분한다. 화면은 현재 세션의 회원을 보여주고 사용자가 확인한 뒤 계속하도록 한다. 어떤 경우에도 클라이언트가 암호 오류라고 추측하지 않는다.

## React 문법이 이번 코드에서 맡은 역할

| 문법 | 이 구현에서의 역할 |
| --- | --- |
| `useState` | Login의 이메일·비밀번호·오류·표시 상태를 보관한다. AuthProvider에서는 세션 조정 객체를 렌더마다 새로 만들지 않고 하나만 유지한다. setter를 호출하면 React가 변경된 화면을 렌더링한다. |
| Context / `useAuth` | 부모에서 여러 단계로 props를 넘기지 않고 Login과 헤더가 같은 회원 상태와 함수를 읽게 한다. 데이터 저장·인증을 서버 대신 수행하는 기능은 아니다. |
| `useEffect` | 화면이 연결된 뒤 초기 /me 조회를 시작하고 해제 때 오래된 응답을 무효화한다. 오류나 피드백이 실제 DOM에 반영된 뒤 초점도 이동한다. |
| `async` / `await` | CSRF → POST → 상태 반영 → 새 CSRF 순서를 읽기 쉽게 표현한다. await 동안 UI 전체를 멈추지 않으며, 다른 요청이 먼저 끝날 수 있어 generation 검사가 필요하다. |
| `useRef` | 렌더를 기다리지 않는 중복 제출 잠금, DOM 입력·피드백 초점, 화면이 연결돼 있는지 여부를 보관한다. |
| `useSyncExternalStore` | auth-session의 subscribe/getSnapshot에 React를 연결한다. 서버용 snapshot도 지정해 SSR과 hydration의 초기 상태를 맞춘다. |

## 검증 실행 방법

실제 프로젝트 스크립트는 `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm build`, `pnpm test:smoke`다. `web/`에서 실행한다. 프로덕션 smoke는 실행 중인 프로덕션 서버를 대상으로 한다.

브라우저 검증 파일은 기본적으로 모의 API만 사용한다. Playwright는 기존 검증 방식처럼 외부 설치 또는 도구 런타임에서 가져오며 package.json에 새 의존성을 추가하지 않았다. 모듈 위치가 자동 탐색되지 않으면 `PLAYWRIGHT_MODULE`에 설치된 모듈의 절대 경로를 지정한다. Chrome 채널은 `PLAYWRIGHT_CHANNEL`, 스크린샷·결과 저장 위치는 `LOGIN_ARTIFACT_DIR`로 바꿀 수 있다.

```sh
# 모의 장애·경합·UI 검사 (새 회원을 만들지 않음)
node tests/login-browser.mjs

# 실제 로컬 가입·Mailpit 인증·로그인 검증 포함 (실행할 때 새 회원 생성)
IYUM_RUN_LOCAL_LOGIN_TEST=true node tests/login-browser.mjs

# 별도 프로덕션 서버가 18443에 실행 중인 경우
BASE_URL=http://127.0.0.1:18443 pnpm test:smoke
BASE_URL=http://127.0.0.1:18443 node tests/login-browser.mjs
```

실제 검증은 8443/8082/8025 주소와 기존 PostgreSQL 컨테이너를 사용한다. dev CORS의 기본 Origin은 8443이므로 18443에서는 API를 모의한 UI 검증만 실행했다. 운영 CORS를 넓히지 않았다. `localhost`와 `127.0.0.1`을 섞지 않는다. 테스트 스크립트는 쿠키·비밀번호·토큰을 메모리에서 비교하며 로그·문서·trace에 남기지 않는다. 실패 시에도 민감값이 섞일 수 있는 원본 예외 메시지 대신 검사명과 행 번호만 기록한다.

## 한계와 유지한 계약

- `/me`는 로그인 당시 세션에 저장된 회원 정보다. DB의 닉네임·상태를 매번 다시 읽는 API로 바꾸지 않았다.
- 회원 상태는 탭 메모리에 있다. 다른 탭의 로그아웃을 즉시 방송하거나 주기적으로 조회하는 기능은 추가하지 않았다. 새로고침과 명시적 조회에서 서버 상태를 확인한다.
- 커뮤니티는 기존 모의 구현이며 이번 작업으로 서버의 글·댓글 권한 검사가 구현된 것은 아니다. 기존 글 작성 화면에는 실제 로그인 접근 제한이 없었다. 임의로 새로운 인증 차단을 추가하지 않았다.
- 헤더에서 버튼을 표시하거나 숨기는 것은 접근 권한 검사가 아니다. Spring의 인증·인가·CSRF 설정을 유지했다.
- 장기 로그인·비밀번호 찾기 API·운영 배포는 범위에 포함하지 않았다.
- 상태 번호는 프론트엔드의 오래된 응답 적용을 막는다. 네트워크 단절 당시 서버 처리 여부 자체는 `/me` 확인 전까지 확정할 수 없다.


## 이번 실행의 검증 결과

| 구분 | 실행 결과 |
| --- | --- |
| `pnpm test` | 132개 통과. 기존 가입·인증·재전송·P03 회귀 포함 |
| `pnpm typecheck` | 통과 |
| `pnpm lint` | 오류 0개. 기존 PostCreate/PostDetail의 img 관련 경고 2개 유지 |
| `pnpm build` | 통과. 기존 공개 경로와 Server 렌더링 유지 |
| 프로덕션 `pnpm test:smoke` | 공개 경로 16개 초기 HTML, 미등록 경로 404, robots 정책 통과 |
| 개발 브라우저 `login-browser.mjs` | 실제 API 2개 통과. 최종 모의 23개 통과(실제 연동 완료 후 이탈 방지 경합 검사 1개 추가) |
| 프로덕션 브라우저 `login-browser.mjs` | 최종 모의 23개 시나리오 통과 |
| 기존 `email-verification-resend-browser.mjs` | 모의 회귀 13개 통과 |
| 변경 검토 | `git diff --check` 통과. 백엔드 소스·의존성·커뮤니티 파일 변경 없음 |

실제 Chrome에서 ACTIVE 회원 가입 → Mailpit 인증 → 잘못된 비밀번호 401 → 연속 제출 시 login POST 한 번 → 200 → 닉네임 헤더 → 새로고침 복원 → 로그아웃 204 → 새로고침 비로그인 → 재로그인을 확인했다. 로그인 때 세션 ID가 바뀌고 로그인 전 CSRF로 보낸 요청은 403/CSRF_TOKEN_INVALID로 거절됐다. 실제 요청의 쿠키·CSRF와 응답의 CORS 자격 증명 허용·no-store도 확인했다. PENDING 회원은 403/EMAIL_VERIFICATION_REQUIRED로 거절되고 기존 재전송 화면으로 이동했다.

ACCOUNT_UNAVAILABLE, HTML·손상 JSON, 네트워크·500, 후속 CSRF 실패, 늦은 /me, 응답 유실, 작성 중 이탈 취소·실패 후 복원은 모의 API와 단위 테스트로 검증했다. 실제 계정 상태를 강제로 바꾸거나 운영 장애를 발생시키지 않았다. 모바일 390px 화면에서 가로 넘침이 없고 오류·완료 안내와 버튼이 표시되는 것도 확인했다. 실제 브라우저는 로컬 Chrome으로 검증했으며 Safari/Firefox·운영 환경에서는 실행하지 않았다. 백엔드 변경이 없어 이번 작업에서 백엔드 테스트는 추가 실행하지 않았다.

기존 프론트엔드 8443, 백엔드 8082, Mailpit 8025를 먼저 확인했다. 최초 실행 중인 API에는 현재 소스의 /me·logout CORS 설정이 반영되지 않아 /me의 401을 브라우저가 읽지 못했다. 소스 수정 없이 기존 Java 프로세스만 정상 종료하고 현재 소스를 dev 프로필로 재시작해 해결했다. 데이터베이스와 Mailpit 데이터를 삭제하지 않았다. 프로덕션 검증은 임시 18443 서버에서 수행하고 종료했다. 기존 프론트엔드와 재시작한 dev API, DB·Mailpit은 유지했다. 자동 생성된 next-env.d.ts의 빌드 경로 변경은 원상복구했다.

실제 검증 중 Chrome 자동화의 204 body 조회가 지원되지 않아 테스트가 한 번 중단되었다. API 클라이언트는 원래부터 204 본문을 파싱하지 않았다. 브라우저 검사도 상태·헤더를 확인하도록 수정한 뒤 전체 흐름이 통과했다. 단위 테스트에서는 204에서 json()이 호출되지 않는 것도 별도로 확인한다.

### 생성한 로컬 테스트 데이터

중간 중단 두 번의 계정도 포함해 다음 네 회원과 메일·인증 기록을 그대로 보존했다. 암호는 무작위 생성해 실행 중 메모리에서만 사용했고 기록하지 않았다. 기존 회원·데이터는 수정하거나 삭제하지 않았다.

| 이메일 | 닉네임 | 상태 | 인증 기록 / 사용됨 | Mailpit 메일 | 용도 |
| --- | --- | --- | --- | --- | --- |
| `iyum-login-active-mv591mkg@example.test` | `LG_a_mv591mkg` | ACTIVE | 1 / 1 | 1 | 이전 API 프로세스 확인 중 중단 |
| `iyum-login-active-mv594ta9@example.test` | `LG_a_mv594ta9` | ACTIVE | 1 / 1 | 1 | 브라우저 204 검사 수정 전 중단 |
| `iyum-login-active-mv596g0r@example.test` | `LG_a_mv596g0r` | ACTIVE | 1 / 1 | 1 | 최종 실제 흐름 통과 |
| `iyum-login-pending-mv596if1@example.test` | `LG_p_mv596if1` | PENDING | 1 / 0 | 1 | 최종 실제 흐름 통과 |

검증 산출물은 로컬 임시 경로에 있다. `result.json`은 검사명과 생성 회원 요약만 포함한다. 스크린샷에는 비밀번호·쿠키·토큰·메일·전체 인증 URL을 저장하지 않았다.

- 개발·실제 브라우저: `/private/tmp/iyum-login-browser-20261012/`
- 최종 개발 모의 브라우저: `/private/tmp/iyum-login-final-mock/`
- 프로덕션 모의 브라우저: `/private/tmp/iyum-login-production-browser/`
- 기존 재전송 회귀: `/private/tmp/iyum-login-resend-regression/`
- 단위·타입·lint·build 기록: `/private/tmp/iyum-login-web-tests.log`, `/private/tmp/iyum-login-web-types.log`, `/private/tmp/iyum-login-web-lint.log`, `/private/tmp/iyum-login-build.log`
- 프로덕션 smoke: `/private/tmp/iyum-login-production-smoke.log`

커밋·push·PR·병합·배포는 수행하지 않았다.
