# 회원가입 REST API 통합 — 2026-10-05

## 범위와 실행 경로

활성 앱은 `web/`의 Next.js App Router입니다. 기존 `/register` → `/register/info` → `/register/verify` 화면을 재사용했습니다. 기존 임시 성공·중복 이메일 시뮬레이션을 제거하고 Spring API의 실제 결과로 동작하게 했습니다. 커뮤니티·로그인 모의 구현은 변경하지 않았습니다.

```text
브라우저 (http://127.0.0.1:8443)
  → SignupInfo.handleSubmit()
  → signup-api.getCsrfToken(): GET http://127.0.0.1:8082/api/v1/auth/csrf
  → signup-api.signup(): POST http://127.0.0.1:8082/api/v1/auth/signup
  → Spring Security → SignupController → SignupService → PostgreSQL
  ← 201 {"status":"PENDING"} 또는 공통 오류 JSON
  → SignupVerify 완료 표시 또는 입력란/폼 오류 표시
```

Next API route나 프록시는 추가하지 않았습니다. 기존 프록시가 없었고, 첫 통합에서 브라우저가 어떤 REST 요청을 보내는지 Network 탭에서 직접 확인할 수 있는 단순한 구성을 선택했습니다. 서로 다른 포트는 다른 Origin이므로 CORS 설정이 필요합니다.

## 코드 읽는 순서

| 순서 | 파일 | 핵심 함수·역할 |
| --- | --- | --- |
| 1 | `src/features/SignupConsent.tsx` | `handleNext`: 체크박스의 실제 선택을 다음 화면에 전달 |
| 2 | `src/lib/signup-form.ts` | `createAgreements`: 약관 코드·dev-v1·실제 agreed를 구성. `validateSignupForm`: 기본 검증. `buildSignupRequest`: 폼을 서버 DTO 형태로 변환. `mapSignupApiError`: 서버 오류를 입력란/공통 오류로 변환 |
| 3 | `src/app/register/layout.tsx`, `src/features/SignupFlow.tsx` | `SignupFlowProvider`: 가입 경로에서 약관 선택과 확인된 PENDING 상태만 메모리로 공유. 새 상태 관리 라이브러리 없음 |
| 4 | `src/features/SignupInfo.tsx` | `handleSubmit`: 검증 → 로딩 → CSRF → 가입 → 결과 표시. `showErrors`: 메시지와 포커스 대상 설정 |
| 5 | `src/lib/signup-api.ts` | `getCsrfToken`, `signup`: fetch 요청·JSON 응답 검사. `SignupApiError`: HTTP 상태·code·fieldErrors 전달 |
| 6 | `../server/iyum-api/src/main/java/io/github/takgeun/iyum/global/config/DevCorsConfig.java` | `corsConfigurationSource`: dev 전용 정확한 Origin·메서드·헤더·자격 증명 허용. 기존 SecurityConfig의 cors(withDefaults())와 연결 |
| 7 | 백엔드 `auth/api/CsrfController.java`, `SignupController.java` | 세션 CSRF 토큰 조회와 기존 가입 서비스 호출 |
| 8 | `src/features/SignupVerify.tsx` | 실제 API 성공을 확인한 경우에만 이메일 인증 대기 표시 |

`SignupFlowProvider`에는 비밀번호가 없습니다. 비밀번호는 입력 화면의 React state에만 존재하며 성공 직후 두 입력값과 표시 상태를 정리합니다. localStorage, sessionStorage, URL, 콘솔에 기록하지 않습니다. 이메일·닉네임 정규화의 최종 책임은 기존 백엔드에 있습니다. 요청을 만들 때 비밀번호를 trim하지 않습니다.

## 가입 버튼을 누른 뒤의 순서

1. `handleSubmit`이 브라우저의 기본 form 전송을 `preventDefault()`로 막습니다.
2. `validateSignupForm`이 필수값, 기본 이메일 형식, 비밀번호 복잡도·UTF-8 바이트 수, 확인값 일치, 닉네임, 실제 필수 약관 선택을 검사합니다. 최종 판단은 서버가 담당합니다.
3. 오류가 있으면 요청 없이 오류 메시지를 표시하고 첫 오류 입력란으로 초점을 이동합니다.
4. 제출 ref를 잠그고 화면 state를 processing으로 바꿉니다. ref는 React가 다시 그리기 전 연속 제출도 막고, state는 disabled·진행 안내를 제어합니다.
5. 메모리에 CSRF 토큰이 없으면 GET `/api/v1/auth/csrf`를 기다립니다.
6. `buildSignupRequest`가 email/password/passwordConfirm/nickname/agreements를 담은 객체를 만듭니다. 약관은 앞 단계의 실제 선택값입니다.
7. `signup`이 JSON으로 변환한 요청을 POST합니다. 브라우저가 같은 세션 쿠키를 전달합니다.
8. `201`이면서 응답 status가 `PENDING`일 때만 비밀번호를 지우고 `completeSignup()` 후 완료 경로로 이동합니다. 로그인 상태로 전환하지 않습니다.
9. 오류이면 `mapSignupApiError`로 입력란·폼 메시지를 구성합니다. `finally`에서 로딩을 해제합니다. 입력란이 활성화된 뒤 effect에서 오류 위치로 초점을 옮깁니다.

비밀번호 확인값은 HTTP 요청에 포함되어 서버 검증을 받지만, 백엔드 `SignupRequest.toCommand()`에서는 제외됩니다. 서비스는 회원과 약관 동의 기록을 하나의 트랜잭션으로 저장합니다.

## fetch와 관련 문법

| 코드 | 사용하는 이유 |
| --- | --- |
| `fetch(url, options)` | 브라우저에서 HTTP 요청을 보냅니다. 추가 API 라이브러리가 필요하지 않습니다. |
| `async` / `await` | CSRF 응답을 받은 후 가입 요청을 보내도록 순서를 표현합니다. 기다리는 동안 브라우저 UI 전체를 멈추지 않습니다. |
| `JSON.stringify(request)` | JavaScript 객체를 HTTP 본문에 넣을 JSON 문자열로 변환합니다. |
| `Content-Type: application/json` | Spring에 요청 본문이 JSON임을 알립니다. |
| `response.ok` | fetch는 HTTP 400/409/500도 정상적으로 응답 객체를 반환하므로 성공 여부를 따로 검사합니다. |
| `response.status === 201` | 가입 API의 정확한 성공 계약을 확인합니다. |
| `response.json()` + 형태 검사 | 응답 문자열을 객체로 읽고, 런타임에도 PENDING/오류 구조가 맞는지 확인합니다. TypeScript 타입만으로 서버 응답을 검증할 수는 없습니다. |
| `AbortSignal.timeout(15_000)` | 응답이 계속 지연되어도 로딩 상태가 무한히 지속되지 않도록 합니다. |

요청 실패나 시간 초과 시 이미 서버에 가입 요청이 도착했을 가능성이 있습니다. 그래서 가입 POST는 자동 재시도하지 않습니다. 사용자가 메시지를 보고 다시 제출할 수 있도록 버튼을 활성화합니다.

## CSRF, 세션 쿠키, CORS

- CSRF 토큰은 요청을 보호하기 위한 값입니다. GET에서 받은 token을 POST의 `X-CSRF-TOKEN` 헤더에 넣습니다. 이 토큰은 이메일 인증 토큰이 아닙니다.
- `JSESSIONID` 쿠키는 서버가 어느 세션인지 알아보게 합니다. 토큰은 그 세션과 맞아야 하며, 쿠키가 있다고 로그인된 것은 아닙니다.
- 두 fetch 모두 `credentials: 'include'`를 사용합니다. JavaScript가 세션 쿠키를 직접 읽거나 Cookie 헤더를 수동으로 만들지 않습니다.
- JSON Content-Type과 사용자 정의 CSRF 헤더 때문에 브라우저가 POST 전에 OPTIONS preflight를 보낼 수 있습니다.
- dev CORS는 기본 `http://127.0.0.1:8443` 하나를 허용하고 자격 증명을 허용합니다. 허용 경로는 csrf와 signup 두 개입니다. 와일드카드 Origin·전체 인증 해제·CSRF 해제는 사용하지 않았습니다.
- `localhost`와 `127.0.0.1`을 섞으면 쿠키 호스트와 Origin이 달라집니다. 예시 실행 주소처럼 양쪽 호스트를 맞춥니다.
- dev 외 프로필에는 이 CORS 설정이 등록되지 않습니다. 향후 배포 Origin은 별도로 결정해야 합니다.

## 응답별 화면 연결

| 응답 | 표시·후속 동작 |
| --- | --- |
| 201 + PENDING | 완료 화면, 비밀번호 삭제. 메일 미발송·인증 기능 준비 상태를 정확히 표시 |
| 400 + fieldErrors | email/password/passwordConfirm/nickname이면 같은 이름의 오류 state에 연결 |
| 약관 오류·알 수 없는 필드 | 폼 상단의 공통 오류 영역에 표시 |
| 409 EMAIL_ALREADY_EXISTS | 이메일 오류 |
| 409 NICKNAME_ALREADY_EXISTS | 닉네임 오류 |
| 403 CSRF_TOKEN_INVALID | 새 토큰 GET만 한 번 시도하고 재제출 안내. 가입 POST는 자동 반복하지 않음 |
| 네트워크·시간 초과·500 | 결과를 확인하지 못했다는 안내와 수동 재시도 가능 상태 |
| 비JSON·빈 응답·예상하지 못한 성공 구조 | 안전한 일반 오류 안내. HTML·내부 오류 문자열을 그대로 화면에 넣지 않음 |

예를 들어 `fieldErrors: [{field: "passwordConfirm", message: "비밀번호가 일치하지 않습니다."}]`는 `errors.passwordConfirm`으로 변환됩니다. 입력란에는 `aria-invalid`와 오류 요소 ID를 가리키는 `aria-describedby`를 설정합니다. 오류는 텍스트로 표시하며 `role="alert"`, 진행·완료는 `role="status"`를 사용합니다.

현재 API에 마케팅 약관 코드가 없으므로 해당 선택 항목은 준비 중으로 비활성화했습니다. 필수 두 항목의 선택값만 보냅니다. `dev-v1`은 개발용 버전입니다.

가입 결과는 메모리로만 공유합니다. 완료 경로를 직접 열거나 새로고침하면 확인된 결과가 없다는 안내를 표시합니다. URL만 보고 가입 성공을 가정하거나 자동으로 가입을 재요청하지 않습니다.

## 로컬 실행

Node 24(또는 프로젝트 engines 범위 내 Node 22)와 pnpm 10.34.3, Java 21, Docker가 필요합니다.

1. `web/.env.example`의 공개 API 주소를 기존 `.env.local`에 추가합니다. 기존 배포 관련 값을 덮어쓰거나 출력하지 않습니다.
2. 백엔드의 기존 `.env` DB 설정과 Docker를 준비합니다.
3. 백엔드에서 `./gradlew bootRun --args='--spring.profiles.active=dev'`를 실행합니다.
4. 프론트엔드에서 `pnpm dev`를 실행하고 `http://127.0.0.1:8443/register`로 접속합니다.

이미 해당 포트에서 서버가 실행 중이면 중복 실행하지 않습니다. API 주소 변경은 Next 재시작이 필요하고, 프로덕션 실행은 `pnpm build` 후 `pnpm start` 순서입니다. `IYUM_WEB_ORIGIN`을 바꾸면 백엔드도 재시작합니다.

## Network 탭에서 확인하기

개발자 도구 → Network → Preserve log를 켜고 `csrf` 또는 `signup`으로 필터링합니다.

1. GET csrf의 Request URL이 `http://127.0.0.1:8082/api/v1/auth/csrf`인지 확인합니다. 응답에는 headerName/token이 있습니다. 새 세션일 때 Set-Cookie가 내려옵니다.
2. OPTIONS가 보이면 Origin, Access-Control-Request-Method, Access-Control-Request-Headers를 확인합니다. 응답에는 정확한 Access-Control-Allow-Origin과 Allow-Credentials가 있어야 합니다.
3. POST signup에서 Content-Type, X-CSRF-TOKEN, 같은 JSESSIONID 쿠키가 전달되는지 확인합니다.
4. Payload에서 agreements 코드·버전·선택값과 요청 필드명을 확인합니다. 디버깅 화면에는 입력한 비밀번호가 포함되므로 요청 전체를 로그·문서로 복사하지 않습니다.
5. Response에서 201/PENDING 또는 400/409/403과 공통 오류 구조를 확인합니다.
6. CSRF 실패 시 GET이 한 번 더 보이되, 가입 버튼을 다시 누르기 전 두 번째 POST가 없는지 확인합니다.

## 실행한 검증

2026-10-05 로컬에서 다음을 실행했습니다.

- `pnpm lint`: 오류 0, 기존 PostCreate/PostDetail의 img 관련 경고 2개.
- `pnpm typecheck`: 통과.
- `pnpm test`: 25개 통과. 기존 5개 + 새 회원가입 20개.
- `pnpm build`: 통과.
- `pnpm test:smoke`: 기존 공개 경로 15개, 404, robots 확인 통과.
- 백엔드 신규 CORS 테스트 7개 통과 후 `./gradlew test`: 107개 통과, 실패·건너뜀 0. 기존 PostgreSQL Testcontainers 검증 포함.
- 설치된 Google Chrome + 번들 Playwright로 `tests/signup-browser.mjs`의 12개 시나리오 통과.

실제 Spring/개발 DB를 사용한 브라우저 검증: CSRF 후 201/PENDING, 이메일 정규화 중복, 닉네임 대소문자 중복, 서버 DTO 검증, 약관 누락·미동의·버전 불일치, CSRF 오류 후 토큰 갱신·수동 재제출. 서버 오류 경로 확인을 위해 일부 요청값·CSRF 헤더를 브라우저 자동화에서 변경했습니다. 정상 가입은 실제 UI 선택과 입력 그대로 전송했습니다.

브라우저에서 의도적으로 모의한 장애: POST 연결 실패와 HTML 500 응답. 실제 서버를 고장 내지 않고 요청을 중단하거나 응답을 대체해 버튼 재활성화·안전한 메시지·자동 재전송 방지를 검증했습니다.

브라우저 검증에서 생성한 회원은 다음 두 건이며, 기존 데이터는 삭제하지 않았습니다.

| 테스트 이메일 | DB 상태 | 권한 | 약관 |
| --- | --- | --- | --- |
| iyum-integration-muv259jx@example.com | PENDING | USER | SERVICE_TERMS, PRIVACY_COLLECTION_USE 각 1건, dev-v1 |
| iyum-csrf-muv259jx@example.com | PENDING | USER | SERVICE_TERMS, PRIVACY_COLLECTION_USE 각 1건, dev-v1 |

DB 확인은 해당 두 이메일만 조회했으며 비밀번호·해시는 조회/출력하지 않았습니다. 화면 캡처와 비밀값 없는 검증 결과는 이번 실행의 `/tmp/iyum-signup-browser/`에 있습니다. 임시 디렉터리는 영구 보관되지 않을 수 있습니다.

브라우저 스크립트를 다시 실행하면 고유한 테스트 회원이 추가됩니다. 기존 Playwright 설치와 Google Chrome이 있을 때 다음처럼 실행할 수 있습니다. 없으면 위의 Network 탭 절차로 수동 확인하거나 별도 자동화 환경을 준비합니다. 프론트엔드 패키지에는 Playwright를 새 의존성으로 추가하지 않았습니다.

```sh
cd /Users/tackeunoh/Developer/iyum/web
# Playwright가 현재 환경에서 import 가능한 경우
node tests/signup-browser.mjs
# 별도 설치를 사용하는 경우 실제 경로로 지정
PLAYWRIGHT_MODULE='file:///absolute/path/to/playwright/index.mjs' node tests/signup-browser.mjs
```

스크립트의 기본 주소는 프론트 127.0.0.1:8443, API 127.0.0.1:8082입니다. 필요하면 BASE_URL, API_BASE_URL, SIGNUP_ARTIFACT_DIR, PLAYWRIGHT_CHANNEL 환경변수를 지정할 수 있습니다. 기본 채널은 설치된 Google Chrome입니다.

## 이후 기능을 연결할 위치

- 이메일 인증 발급·메일 발송: 백엔드의 가입 후 처리와 별도 인증 API를 구현한 다음 SignupVerify에 실제 발송·인증 상태를 연결합니다. 현재 문구만 미리 발송 완료로 바꾸지 않습니다.
- 이메일 인증 결과: 성공 시 회원을 ACTIVE로 바꾸는 서버 API와 결과 화면을 연결합니다. 현재 회원 엔티티의 상태 변경 메서드는 재사용할 수 있습니다.
- 로그인: 기존 Login 모의 화면을 별도 실제 로그인 API에 연결합니다. 가입 성공을 인증 성공으로 재사용하지 않습니다.
- 인증 후 세션/CSRF 정책이 확정되면 토큰 재조회 시점과 보호된 요청의 오류 처리를 추가합니다.
- 운영 약관 본문·버전 및 배포용 CORS Origin은 실제 서비스 연결 전에 확정해야 합니다.
