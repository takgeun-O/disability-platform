# 이메일 인증 메일 재전송 구현 및 코드 읽기 안내

작성일: 2026-10-10 (Asia/Seoul)  
작업 브랜치: `feat/email-verification-resend`  
저장소: `/Users/tackeunoh/Developer/iyum`

## 1. 이번에 구현한 기능

메일을 받지 못했거나 링크를 사용할 수 없는 사용자가 `/register/resend`에서 가입한 이메일을 직접 입력하고 인증 메일 재전송을 요청할 수 있습니다. 새 탭이나 새로고침 후에도 사용할 수 있습니다. 가입 직후의 `SignupFlow` 메모리에는 이메일을 추가하지 않았습니다.

기존 흐름은 그대로입니다.

`회원가입 → PENDING → 사용자가 이메일 인증 버튼 클릭 → ACTIVE → 사용자가 직접 로그인`

이번 변경은 재전송만 추가합니다. 다른 게시글·로그인 화면 등의 모의 데이터를 실제 서버 인증으로 바꾸지 않았으며 자동 로그인도 없습니다. 인증 대기·링크 누락·잘못된 링크 안내에서 재전송으로 이동하고, 정상 형식의 링크여도 API가 `EMAIL_VERIFICATION_INVALID`를 반환하면 이동 링크가 표시됩니다. 인증 성공 화면의 로그인 이동도 유지합니다.

## 2. 적용한 정책과 정확한 경계

저장소에서 별도의 확정된 재전송 정책을 찾지 못해 요청의 기본값을 적용했습니다.

| 항목 | 정책 |
| --- | --- |
| 발급 대상 | 현재 DB 상태가 PENDING인 회원 |
| 최소 간격 | 마지막 발급부터 60초. 정확히 60초면 허용 |
| 시간당 제한 | 최근 1시간 최대 5회. 최초 가입 발급도 포함 |
| 시간 구간 | `(현재 시각 − 1시간, 현재 시각]`. 정확히 1시간 전 발급은 제외 |
| 발급 이력 | 사용·폐기·만료된 토큰도 횟수 계산에 포함 |
| 시간 기준 | 회원 잠금을 획득한 뒤 기존 `Clock`에서 읽은 시각 |
| 유효 기간 | 기존 30분 유지. 실제 값은 `token-ttl` 설정 |
| 허용된 재전송 | 미사용·미폐기 토큰을 모두 폐기하고 새 토큰 한 건 발급 |
| 제한된 요청 | 기존 토큰 유지, 새 토큰 및 메일 이벤트 없음 |
| 사용 완료 이력 | 삭제하거나 폐기 상태로 바꾸지 않음 |

예를 들어 최초 발급 후 60·120·180·240초에 네 번 재전송했다면 총 5회입니다. 300초 요청은 간격 조건을 만족하지만 시간당 제한에 걸립니다. 최초 발급 시각에서 정확히 3600초가 되면 최초 기록은 계산 구간에서 빠집니다.

`resend-min-interval`은 0 초 초과부터 1시간 이하, `resend-max-per-hour`는 1 이상의 정수여야 합니다. 잘못된 값은 서버 시작 시 설정 바인딩/생성자 검증으로 거절합니다. 서버 시간이 뒤로 이동해 최신 발급이 미래로 보이면 최소 간격 조건이 재전송을 막습니다.

## 3. API 계약과 202의 의미

`POST /api/v1/auth/email/resend`

```json
{"email":"user@example.com"}
```

정상 접수는 HTTP **202**, `Cache-Control: no-store`, 다음 본문입니다.

```json
{"status":"ACCEPTED"}
```

발급 가능한 PENDING·없는 이메일·PENDING 이외 상태·발급 제한에 해당하는 요청은 모두 이 계약을 사용합니다. 원본 토큰·회원 ID·회원 상태·계정별 제한 사유는 반환하지 않습니다. 이메일 공백 제거와 소문자화는 회원가입과 공유하는 `MemberInputPolicy.canonicalizeEmail`을 사용하고, `@NotBlank`, `@Email`, 정규화 후 최대 254자 검증도 기존 가입 정책과 같습니다.

202는 **정상적으로 요청을 처리했다는 외부 접수 응답**입니다. 토큰을 실제 발급했는지, 메일을 큐에 넣었는지, SMTP가 수락했는지, 사용자가 메일을 받았는지를 보장하지 않습니다. 따라서 화면은 다음과 같이 안내합니다.

> 입력한 이메일이 인증 대기 상태이고 재전송 조건을 충족하면 인증 메일이 발송됩니다. 메일함과 스팸함을 확인해 주세요.

입력 오류는 기존 `VALIDATION_FAILED` / `INVALID_REQUEST_BODY`, CSRF 오류는 `CSRF_TOKEN_INVALID` 계약을 사용합니다. DB 장애 등의 내부 실패는 `INTERNAL_SERVER_ERROR`이며 202로 숨기지 않습니다. 커밋 후 SMTP 실패는 이미 성공한 DB 트랜잭션과 별개의 실패로 기록합니다.

비로그인 호출은 허용하지만 CSRF 보호는 유지합니다. 개발 CORS에는 `/api/v1/auth/email/resend` 한 경로만 추가했습니다. 기존 허용 출처와 쿠키·헤더 설정을 사용합니다. Swagger는 dev 프로필의 `/swagger-ui/index.html`, OpenAPI는 `/v3/api-docs`에서 볼 수 있습니다.

## 4. 코드를 읽을 순서와 주요 파일

아래 파일 경로는 저장소 루트 기준입니다. 처음에는 1~4번으로 화면 동작을 파악하고, 이후 서버로 내려가면 좋습니다.

| 순서 | 파일 / 핵심 함수 | 역할 |
| --- | --- | --- |
| 1 | `web/src/app/register/resend/page.tsx` / `Page` | Next.js URL과 실제 화면 컴포넌트 연결 |
| 2 | `web/src/features/EmailVerificationResend.tsx` / `handleSubmit` | 이메일 입력, 상태, 중복 제출 방지, CSRF→POST 순서, 초점 처리 |
| 3 | `web/src/lib/signup-form.ts` / `validateEmail` | 가입·재전송이 공유하는 이메일 입력 안내 |
| 4 | `web/src/lib/email-verification-resend-api.ts` / `resendEmailVerification` | 요청 구성, 정확한 상태·JSON 계약 검사, 안전한 오류 분류 |
| 5 | `web/src/lib/signup-api.ts` / `getCsrfToken` | 기존 세션 CSRF 조회 기능 재사용 |
| 6 | `server/iyum-api/src/main/java/io/github/takgeun/iyum/auth/api/EmailVerificationResendController.java` / `resend` | 입력 검증, 서비스 호출, 공통 접수 응답, OpenAPI 설명 |
| 7 | 같은 `auth/api/dto/EmailVerificationResendRequest.java`, `EmailVerificationResendResponse.java` | 요청·응답 모양 |
| 8 | 같은 `auth/application/EmailVerificationResendService.java` / `resend` | 회원 잠금, 제한 판단, 토큰 폐기·발급, 이벤트 발행 |
| 9 | 같은 `auth/infrastructure/EmailVerificationTokenRepository.java` | 마지막 발급 시각, 최근 발급 횟수, 미사용 토큰 잠금 조회 |
| 10 | 같은 패키지 루트의 `member/infrastructure/MemberRepository.java` / `findByEmailForUpdate` | 이메일로 회원을 조회하면서 DB 행 잠금 |
| 11 | `auth/application/EmailVerificationTokenIssueService.java` / `issue` | 기존 난수 생성·해시 저장·토큰 유효 기간 계산 재사용 |
| 12 | `auth/application/EmailVerificationMailRequestedListener.java` / `on`, `send` | 성공 커밋 후 작업 큐 제출, SMTP 실패 분리 |
| 13 | `global/config/EmailVerificationMailDispatchConfig.java` | 메일 전용 작업 스레드 2개, 대기 큐 100개, 큐 초과 거절 |
| 14 | `global/config/EmailVerificationProperties.java`, `src/main/resources/application.yaml` | 유효 기간 및 재전송 정책 설정·검증 |
| 15 | `src/main/resources/db/migration/V4__index_email_verification_issuance.sql` | 회원·발급 시각 복합 인덱스 추가. 기존 V1~V3는 수정하지 않음 |

이동 링크는 `SignupVerify.tsx`, `EmailVerificationAction.tsx`에 있습니다. `web/next.config.ts`는 토큰이 포함될 수 있는 인증 화면 경로를 개발 요청 로그에서 제외합니다.

주요 검증 코드는 `EmailVerificationResendIntegrationTest.java`, `EmailVerificationMailRequestedListenerTest.java`, `EmailVerificationPropertiesTest.java`, `web/tests/email-verification-resend-api.test.mjs`, `web/tests/email-verification-resend-browser.mjs`에서 읽을 수 있습니다.

## 5. JavaScript 다음 단계로 이해하는 React

### 컴포넌트와 JSX

`EmailVerificationResend`는 화면을 설명하는 JSX를 반환하는 JavaScript 함수입니다. JSX의 `<form>`·`<input>`은 HTML과 닮았지만 이벤트 핸들러와 현재 상태를 연결할 수 있습니다. `onSubmit={handleSubmit}`은 함수 자체를 전달하며, 화면을 그릴 때 함수를 실행하는 `handleSubmit()`과 다릅니다.

파일의 `'use client'`는 입력·클릭·React Hooks를 쓰는 경계입니다. 컴포넌트 자체는 서버에서도 초기 HTML로 렌더링될 수 있으므로 브라우저 API를 파일 상단에서 무조건 실행하지 않습니다. 이 화면은 로드나 새로고침만으로 POST하지 않습니다.

### useState: 화면에 보이는 값

`const [email, setEmail] = useState('')`는 현재 입력값과 그 값을 바꾸는 함수를 만듭니다. `setEmail(...)`을 호출하면 React가 새 값으로 화면을 다시 그립니다. `<input value={email} onChange={...}>`는 입력창과 상태를 연결합니다.

`state.kind`는 `idle`, `submitting`, `accepted`, `error` 중 하나입니다. `'accepted'`는 회원 상태가 아니라 이번 요청의 접수 화면 상태입니다. TypeScript의 타입은 개발 중 잘못된 사용을 찾아주지만 서버가 보낸 JSON의 내용까지 보장하지는 않습니다. API 함수가 `unknown`으로 받은 값을 직접 검사하는 이유입니다.

### useRef: 화면을 다시 그리지 않고 기억하는 값

버튼의 `disabled`는 화면이 갱신된 뒤 반영됩니다. 아주 빠른 두 submit은 그 전에 들어올 수 있습니다. `inFlightRef.current = true`는 즉시 저장되므로 두 번째 실행을 바로 막습니다. 이 값은 한 화면의 편의 장치이며 다른 탭·다른 서버로 들어오는 요청은 백엔드 DB 잠금이 처리합니다.

`mountedRef`는 화면을 떠났는지 확인합니다. CSRF 조회 중 이동했다면 POST를 시작하지 않습니다. POST를 이미 보낸 뒤 화면을 떠났다면 서버 처리를 되돌렸다고 가정하지 않고 화면 상태 갱신만 피합니다.

### useEffect: 표시 이후 초점과 수명 관리

`useEffect`는 컴포넌트가 준비되거나 상태가 바뀐 뒤 동작할 수 있습니다. 이 화면의 effect는 화면 존재 여부와 키보드 초점을 관리하며 POST를 보내지 않습니다. 입력 오류는 `aria-invalid`와 `aria-describedby`로 입력창에 연결하고 이메일 입력으로 초점을 이동합니다. 처리 결과는 `role="status"`, 오류는 `role="alert"`로 알리며 결과 문단에 `tabIndex={-1}`을 주어 코드로 초점을 이동할 수 있게 합니다.

### async/await와 fetch

`await getCsrfToken()`이 끝난 다음 그 결과로 `await resendEmailVerification(...)`를 호출합니다. 두 호출 모두 `credentials: 'include'`로 같은 세션 쿠키를 사용합니다. `fetch`는 400·403·500에도 정상적으로 응답 객체를 돌려주므로 `try/catch`만으로 성공 여부를 판단할 수 없습니다.

HTTP 202와 JSON의 `status === 'ACCEPTED'`를 모두 확인해야 접수로 바뀝니다. HTML·잘못된 Content-Type·깨진 JSON·다른 HTTP 상태·다른 status 값은 오류입니다. 서버의 임의 `message`나 `fieldErrors` 문장을 그대로 화면에 넣지 않습니다.

`finally`는 성공·실패 모두에서 실행되어 중복 제출 잠금을 해제합니다. 네트워크 오류는 요청이 서버에서 처리된 뒤 응답만 사라졌을 수도 있으므로 결과가 불확실하다고 안내합니다. 자동 POST 재시도는 없고, HTTP 307/308 리디렉션을 따라 POST를 다시 보내는 것도 `redirect: error`로 차단합니다. 사용자가 수동 재시도하면 CSRF도 다시 조회합니다.

## 6. 입력부터 화면 표시까지

1. 사용자가 이메일을 입력하고 제출합니다. 공백·이메일 형식·길이를 먼저 확인합니다.
2. 화면은 제출 상태로 바뀌고 입력과 버튼을 비활성화합니다. ref로 연속 실행을 막습니다.
3. 기존 `GET /api/v1/auth/csrf`에서 현재 세션의 CSRF 값을 가져옵니다.
4. 같은 쿠키와 `X-CSRF-TOKEN` 헤더로 재전송 POST를 보냅니다.
5. Spring Security가 CSRF를 검사하고, DTO가 가입과 같은 방식으로 이메일을 정규화·검증합니다.
6. 서비스 트랜잭션에서 이메일에 해당하는 회원 행을 잠급니다. 잠금 확보 후 최신 회원 상태·현재 시각·DB 발급 이력을 확인합니다.
7. 허용되면 미사용·미폐기 토큰을 잠그고 폐기합니다. 기존 발급 서비스를 호출해 새 난수 토큰의 **해시**를 저장하고 메일 요청 이벤트를 발행합니다.
8. 트랜잭션이 성공적으로 커밋된 다음 리스너가 메일 작업 큐에 제출합니다. 롤백되면 제출하지 않습니다.
9. 요청 스레드는 SMTP 완료를 기다리지 않고 202/ACCEPTED를 반환합니다. 작업 스레드는 기존 발송기로 SMTP를 시도합니다.
10. 브라우저는 정확한 접수 응답을 확인하면 조건부 안내를 표시하고 해당 안내로 초점을 이동합니다.

DB에는 원본 토큰 대신 해시만 남습니다. 원본은 메일 작성에 필요한 발급 결과·이벤트·발송 작업 메모리에만 잠시 존재합니다. 애플리케이션 로그에는 토큰·전체 인증 URL·메일 본문·SMTP 예외 메시지를 기록하지 않습니다.

## 7. 왜 기존 토큰을 폐기하고 회원부터 잠그나요?

새 메일을 요청한 뒤 예전 메일까지 모두 유효하면 어떤 링크가 최신인지 모호하고 사용 가능한 인증 자격이 여러 개 남습니다. 재전송을 허용할 때 기존 미사용 링크를 폐기하여 최신 발급 토큰만 사용할 수 있게 합니다. 제한에 걸린 요청은 사용자가 가진 링크를 망가뜨리지 않습니다.

폐기와 새 저장은 **같은 트랜잭션**입니다. 새 토큰 저장이 실패하면 이미 SQL로 반영된 폐기까지 롤백됩니다. 사용 완료 기록은 이력으로 보존합니다.

같은 회원에게 동시에 재전송 두 건이 들어오면 첫 요청이 회원 잠금을 보유합니다. 두 번째는 기다린 뒤 첫 요청이 커밋한 새 발급 기록을 읽고 60초 제한에 걸립니다. DB의 행 잠금과 이력을 사용하므로 여러 애플리케이션 인스턴스나 재시작에도 적용됩니다.

인증과 재전송도 같은 회원 잠금을 먼저 얻습니다.

- 인증이 먼저 완료되면 회원이 ACTIVE가 되어 뒤의 재전송은 발급하지 않습니다.
- 재전송이 먼저 완료되면 기존 토큰이 폐기되어 뒤의 기존 링크 인증이 실패합니다. 새 링크로 인증할 수 있습니다.

둘 다 `회원 → 토큰` 순서이므로 서로 반대 순서로 잠금을 잡아 기다리는 상황을 피합니다. 인증 서비스는 처음에 토큰 해시로 회원 ID만 읽고, 회원 잠금 후 토큰 엔티티를 읽는 기존 구조를 유지합니다.

## 8. 응답 시간과 발송의 한계

기존 `AFTER_COMMIT` 리스너는 요청 스레드에서 SMTP를 동기 실행했습니다. AFTER_COMMIT은 실행 시점만 정하며 자동 비동기는 아닙니다. 발급 가능한 이메일에만 SMTP 대기가 생겨 계정 추측에 도움이 되는 응답 시간 차이가 발생할 수 있었습니다.

이번에는 커밋 후 전용 `ThreadPoolTaskExecutor`로 작업을 넘깁니다. 스레드 2개, 대기 작업 최대 100개입니다. 가득 찼을 때 요청 스레드에서 SMTP를 실행하지 않고 등록을 거절해 안전한 오류 로그를 남깁니다. DB는 이미 커밋됐으므로 토큰·발급 횟수는 유지되고 재전송 요청의 202 계약도 유지됩니다. 실제 발송기의 SMTP 연결·읽기·쓰기 타임아웃은 기존 5초 설정입니다.

이것만으로 계정 추측 방지가 완성되지는 않습니다. 없는 회원 조회와 존재하는 회원의 잠금·조회·갱신·이벤트 작업은 서로 다른 비용을 가지며, 동시 요청의 잠금 대기나 부하 차이도 남습니다. 응답 시간의 균일성이나 외부 공격 저항성을 측정·보장한 작업은 아닙니다.

다음은 이번에 구현하지 않았습니다.

- **IP/네트워크 단위 남용 제한**: 계정별 제한과 별개입니다. 존재하지 않는 이메일을 대량 입력하는 요청이나 SMTP 큐 전체를 채우는 공격도 고려해야 합니다. 신뢰 가능한 프록시/IP 처리와 별도 방어 정책이 후속 과제입니다.
- **내구성 있는 메일 작업·자동 재시도**: 큐는 프로세스 메모리에 있습니다. 커밋 후 큐 등록 전 장애, 큐 초과, 재시작·강제 종료, SMTP 실패에서 메일이 유실될 수 있습니다. 종료 시 최대 10초 대기하지만 영속 저장이나 전달 보장은 아닙니다. 다음 후보는 DB outbox와 제한된 재시도·운영 관찰입니다.
- **발송 순서·정확히 한 번 전달 보장**: 큐에 있던 오래된 메일이 늦게 도착할 수 있습니다. DB에서는 폐기된 링크를 거절합니다. 원본 토큰은 해시에서 복원할 수 없어 후속 outbox 설계에도 별도 고려가 필요합니다.
- **상용 메일**: SPF/DKIM/DMARC, 반송·스팸·평판 처리, 운영 자격 증명 설정 및 실제 외부 전달은 범위 밖입니다. Mailpit만 사용했습니다.
- **운영 로그 경로 전체 점검**: Next 개발 요청 로그는 인증 경로를 제외했지만 향후 프록시·CDN·APM의 URL·요청 본문 수집도 별도 설정이 필요합니다.
- **토큰 이력 정리**: 지금은 보존합니다. 추후 청소 정책이 최근 1시간 발급 이력을 지우면 제한이 깨질 수 있으므로 보존 구간을 함께 설계해야 합니다.

Redis나 외부 메시지 브로커는 추가하지 않았습니다.

## 9. 실행과 설정

기존 `.env`와 개발 컨테이너 설정을 사용합니다. 비밀번호 등 설정값을 문서에 복사하지 않습니다.

```bash
cd /Users/tackeunoh/Developer/iyum/server/iyum-api
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun --args='--spring.profiles.active=dev --spring.docker.compose.enabled=false'
```

위 명령은 기존 PostgreSQL·Mailpit 컨테이너가 실행 중일 때의 예입니다. 서버 시작 시 Flyway V4가 인덱스를 추가합니다. 이미 적용한 V1~V3를 변경하거나 Docker 볼륨을 삭제할 필요가 없습니다.

```yaml
iyum:
  auth:
    email-verification:
      token-ttl: 30m
      resend-min-interval: 60s
      resend-max-per-hour: 5
```

프론트엔드는 기존 공개 API 주소 `NEXT_PUBLIC_API_BASE_URL` 설정을 그대로 사용합니다. 로컬 기본 주소는 프론트 8443, API 8082, Mailpit 8025입니다. 서버 간격과 횟수를 바꾸면 위 YAML 또는 Spring 명령행 속성으로 변경합니다. 브라우저 카운트다운은 추가하지 않았습니다.

```bash
cd /Users/tackeunoh/Developer/iyum/web
pnpm dev
```

이미 8443 서버가 실행 중이면 재사용합니다.

## 10. 테스트 실행 방법

```bash
cd /Users/tackeunoh/Developer/iyum/server/iyum-api
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test

cd /Users/tackeunoh/Developer/iyum/web
pnpm test
pnpm lint
pnpm typecheck
pnpm build
```

백엔드는 기존 Testcontainers 방식으로 별도 PostgreSQL을 만들며 개발 DB와 분리합니다. 새 통합 테스트는 시간을 조절한 Clock과 실제 행 잠금으로 경계·동시성·롤백을 검증합니다. 기존 회원가입·인증 테스트도 함께 실행합니다.

선택 실행 브라우저 검증은 기존 방식처럼 Playwright 설치를 사용하며 프로젝트 의존성을 추가하지 않습니다.

```bash
# PLAYWRIGHT_MODULE에 사용 가능한 Playwright의 index.mjs 절대 경로 지정
PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs node tests/email-verification-resend-browser.mjs

# 개발 서버·Mailpit·Docker가 준비된 경우에만 실행. 새로운 테스트 회원과 메일을 남깁니다.
IYUM_RUN_LOCAL_RESEND_TEST=true PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs node tests/email-verification-resend-browser.mjs
```

기본 실행은 화면의 API를 모의하여 안내·접근성·중복 제출·비정상 응답·재시도를 확인합니다. 로컬 모드는 실제 회원가입과 Mailpit, DB 조회를 추가합니다. 정책을 줄이지 않고 최초 발급부터 60초를 기다립니다. 토큰은 메모리에서만 사용하며 실패 진단에서도 전체 인증 URL이 나올 수 있는 Playwright 오류 본문을 저장하지 않습니다.

프로덕션 서버에서 `pnpm test:smoke`를 실행하면 새 재전송 경로를 포함한 기존 공개 화면의 초기 HTML을 점검합니다. 별도 포트라면 `BASE_URL`을 지정합니다.

## 11. 이번 실행 결과와 남긴 데이터

검증 날짜는 2026-10-10이며 모든 아래 항목은 실제로 실행했습니다.

| 검증 | 결과 |
| --- | --- |
| 백엔드 `./gradlew test` | 총 223개: 222 통과, 1 건너뜀, 실패 0 |
| 새 백엔드 검사 | 재전송 통합 29개, 설정 9개, 메일 리스너·큐 3개 통과 |
| 기존 회원가입·인증 회귀 | 백엔드 전체 및 프론트엔드 전체 테스트에 포함, 통과 |
| 프론트엔드 `pnpm test` | 70개 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm lint` | 오류 0, 기존 게시글 화면 `<img>` 경고 2개 |
| `pnpm build` | 통과, `/register/resend` 정적 경로 생성 |
| 개발 환경 Chrome 검사 | 실제 API·Mailpit·DB 흐름을 포함한 14개 시나리오 통과 |
| 프로덕션 Chrome 검사 | 모의 응답·접근성·수동 재시도·307 차단 등 13개 시나리오 통과, 390px 모바일 가로 넘침 없음 |
| 프로덕션 `pnpm test:smoke` | 공개 경로 16개 초기 HTML, 알 수 없는 경로 404, robots 정책 통과 |
| 변경 파일 검사 | `git diff --check` 통과 |

건너뛴 백엔드 1개는 환경 변수로 선택 실행하는 기존 `LocalEmailVerificationMailTest`입니다. 대신 브라우저 통합 검증에서 실제 Mailpit SMTP 발송을 확인했습니다. 테스트 중 생긴 신규 Java deprecated 사용은 수정하여 제거했습니다. JVM의 기존 class sharing 경고는 남으며 테스트 실패가 아닙니다.

Lint 경고는 변경하지 않은 `PostCreate.tsx:689`, `PostDetail.tsx:465`의 기존 `<img>` 사용입니다. 재전송 구현에는 새 Lint 경고가 없습니다. 기존 production smoke의 인증 화면 기대값은 이미 구현되어 있던 Suspense 초기 안내 문구와 맞췄고, 새 재전송 경로를 추가했습니다. 화면의 기존 문구는 변경하지 않았습니다.

실제 개발 환경에서는 다음을 확인했습니다.

1. 브라우저에서 약관 동의·회원가입 후 PENDING 안내와 Mailpit 최초 메일 확인.
2. 즉시 재전송은 202지만 토큰 1건·메일 1통 유지.
3. 기본 60초를 실제로 기다린 후 재전송. 빠른 submit 두 번에도 POST 한 번.
4. 새 Mailpit 메일 확인. DB 토큰 2건 중 기존 1건 폐기.
5. 바로 다시 요청해도 토큰·메일 각 2개 유지.
6. 이전 링크 인증은 `EMAIL_VERIFICATION_INVALID`, 재전송 이동 링크 표시.
7. 새 링크 인증 성공. DB ACTIVE, 사용 토큰 1건·폐기 토큰 1건 확인.
8. 인증 성공 화면에서 사용자가 로그인 화면으로 이동.

브라우저의 서버 오류·네트워크 단절·HTML·깨진 JSON·CSRF 오류 시나리오는 요청을 모의하거나 차단해서 검증했습니다. 실제 개발 DB나 SMTP를 고장 내지는 않았습니다. 백엔드 SMTP 실패·큐 거절·느린 SMTP·DB 롤백·동시 실행은 자동 테스트로 따로 검증했습니다.

남긴 테스트 데이터:

| 이메일 | 최종 상태 | 토큰 | Mailpit 메일 | 설명 |
| --- | --- | --- | --- | --- |
| `iyum-resend-mv1wly7f@example.test` | PENDING | 1 | 1 | 첫 브라우저 검증의 가입·즉시 제한 확인용. 이후 테스트 선택자 수정 과정에서 해당 실행 종료 |
| `iyum-resend-mv1woe8e@example.test` | ACTIVE | 2 (사용 1, 폐기 1) | 2 | 전체 실제 인증 흐름 완료용 |

기존 데이터나 Docker 볼륨을 삭제하지 않았습니다. 상용 메일은 발송하지 않았습니다. 기존 8443 개발 프론트엔드·PostgreSQL·Mailpit을 재사용했고, 8082 개발 백엔드는 변경 코드로 재시작하여 유지했습니다. 개발 DB에는 Flyway V4 인덱스가 적용됐습니다. 18443 프로덕션 서버는 이번 검증에만 사용하고 종료했습니다.

기계 판독 결과와 민감정보 없는 화면 캡처는 `/private/tmp/iyum-resend-browser/`, `/private/tmp/iyum-resend-production-browser/`에 있습니다. 임시 폴더는 운영체제 정리 시 사라질 수 있습니다. 결과에는 원본 토큰·전체 인증 URL·메일 본문·비밀번호가 없습니다.

커밋·push·PR 생성·병합은 하지 않았고 변경은 작업 브랜치에 남겼습니다.
