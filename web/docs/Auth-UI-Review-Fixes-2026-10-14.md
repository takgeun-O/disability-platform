# 인증 UI 동작 후속 수정 · 2026-10-14

## 작업 기준과 보존

- 로컬 `refactor/auth-ui`, HEAD `60559e232687c3db5eca82ef3886b31d2dc323d1` 기준. 시작 당시 tracked 수정 10개, 미추적 56개, staged 없음. 이전 디자인 작업을 포함한 현재 파일에서 이어서 수정했다.
- 루트/web AGENTS.md를 적용했다. 현재 실제 API 구현이 오래된 지침의 모의 구현 설명보다 우선한다.
- 기존 디자인 토큰, CSS, AuthFrame/AuthFields, 인증 API 클라이언트, AuthProvider, SignupFlow, 백엔드와 정책을 이번 후속 작업에서 변경하지 않았다. 저장소의 `git diff HEAD`에는 이전 디자인 작업도 함께 표시된다.
- 커밋·push·PR·병합·배포·stash·reset·restore·clean 없음. typegen/build가 만든 next-env.d.ts import 경로 차이만 작업 시작 스냅샷으로 정리했다.

## 원인과 수정

### 1. 가입 화면 이탈과 요청 수명

기존 `SignupInfo.handleSubmit`은 await 후 화면 이탈 여부를 확인하지 않았다. CSRF 대기 중 떠나면 가입 POST가 시작되고, 이미 보낸 POST의 응답은 가입 상태를 갱신하거나 화면을 다시 이동시킬 수 있었다. 언마운트만 보는 것으로는 목적지 응답이 늦어 기존 폼이 남아 있는 구간을 보호할 수 없다.

`NavigationGuard`가 기존에 가진 탐색 승인 버전을 재사용했다. 확인창에서 ‘나가기’를 승인했을 때뿐 아니라 **확인창 없는 AppLink 탐색, guard.request, 허용된 브라우저 뒤로/앞으로 이동**에도 버전을 올린다. 확인창 취소/복원용 pop에는 올리지 않는다. Next의 history 필드는 유지한다. 새 탭/수정 키 링크는 기존 탭 이탈로 처리하지 않는다.

`useNavigationRequest`와 작은 `createNavigationRequestScope`는 마운트 수명, 화면의 탐색 버전, 요청 번호를 함께 확인한다. `SignupInfo.handleSubmit`은 CSRF 완료 후 POST 전, 가입 성공 처리 전, 오류 처리 전, CSRF 재조회 후, finally 및 오류 초점 effect에서 유효성을 확인한다. 확정된 이탈 이후에는 새 POST·completeSignup·이동·오류·초점 처리가 없다. 새 가입 화면은 별도 요청 수명을 사용한다.

**이것은 서버 요청 취소/롤백이 아니다.** 이미 서버로 보낸 가입은 저장될 수 있다. UI의 늦은 후속 처리를 무효화하며, 자동 가입 재전송은 하지 않는다. CSRF 복구 후에도 사용자가 직접 다시 제출해야 한다.

### 2. 기존 회원가입 검사 복구

`tests/signup-browser.mjs` 첫 검사는 과거 문구 “현재 이 화면에서 확인할 수 있는 가입 결과가 없습니다.”를 기다려 중단됐다. 현행 직접 진입 안내를 확인하도록 고치고, 직접 진입과 새로고침 모두에서 가입 완료 제목/PENDING이 없다는 조건도 검사한다.

이전 `/api/v1/auth/` 전체 요청 합산은 헤더의 정상 GET `/me`까지 실패로 계산했다. 이제 메서드와 URL로 POST signup / GET csrf / GET me를 분리한다. 입력 오류·필수 약관 오류일 때 signup과 csrf가 0인지 검사한다. 기존 중복 제출, 실제 약관 정책, 서버 fieldErrors, CSRF 수동 재시도, 네트워크/HTML 응답 검사는 유지했고 **12개 모두 실행했다**.

### 3. 약관 취소

`SignupConsent`의 기존 버튼에 `guard.request(() => router.push('/'))`를 연결했다. 마우스와 Enter로 홈 이동을 확인했다. 로그인/로그아웃 API를 호출하거나 현재 세션을 바꾸지 않는다.

### 4. 수동 인증 성공 → 로그인

`SignupVerify`는 버튼 클릭 전에 “인증이 완료되면 로그인 화면으로 이동합니다.”를 안내한다. `EmailVerificationAction.handleVerify`는 여전히 사용자가 누를 때만 CSRF GET → 인증 POST를 수행한다. 기존 `verifyEmail`이 **HTTP 200 및 status=ACTIVE**를 확인한 경우에만 진행 안내를 표시하고 `router.replace('/login')`한다. 인증 버튼을 없애 중복 인증을 막으며 추가 클릭/카운트다운/자동 로그인을 요구하지 않는다.

확정된 이탈/언마운트에 대한 동일한 요청 수명 검사를 인증에도 적용했다. 실패, 불확실 응답, 201/ACTIVE, 200/PENDING, HTML/손상 JSON에는 완료 안내나 자동 이동이 없다. 유효하지 않은 링크에는 재전송과 “이미 인증을 완료했다면 로그인해 주세요”라는 조건부 로그인 링크가 있다. 오류만으로 이미 인증된 계정이라고 판단하지 않는다.

`VerificationNoticeProvider`는 성공에서 로그인으로 이어지는 **일회성 메모리 상태**만 전달한다. 데이터는 탐색 버전 번호이며 계정/토큰/비밀번호가 아니다. 로그인 마운트 후 소비하고, 그 전에 다른 탐색이 확정되면 버전 불일치로 폐기한다. URL 파라미터·localStorage·sessionStorage를 쓰지 않는다. 새로고침/직접 `/login`/임의 `?verified=true`는 완료 안내를 만들지 않는다. 안내가 사라져도 로그인 폼은 그대로 사용할 수 있다.

인증 전환 안내는 role=status로 읽고 초점을 이동하지 않는다. 도착한 `Login`의 기존 feedback 초점 처리로 완료 안내에 한 번 초점을 주며 다음 Tab은 이메일 입력으로 간다. 로그인된 세션이면 기존 회원 화면을 유지하고 방금 인증한 계정과 동일하다고 추정하지 않는 안내를 추가한다. 로그인/로그아웃 API는 자동 호출하지 않는다.

## 변경 파일과 핵심 함수

| 파일 (web 기준) | 이번 후속 작업 |
|---|---|
| `src/components/NavigationGuard.tsx` | notifyNavigation, request/proceed/popstate, useNavigationRequest |
| `src/components/AppLink.tsx` | onNavigate: 확인창 없는 이탈도 즉시 알림, 기존 취소/새 탭 동작 유지 |
| `src/lib/navigation-request.ts` (신규) | createNavigationRequestScope: 화면/요청 수명 검사 |
| `src/features/SignupInfo.tsx` | handleSubmit, 오류 초점 effect: await 이후 유효성 검사 |
| `src/features/SignupConsent.tsx` | 취소 버튼 홈 이동 |
| `src/features/EmailVerificationAction.tsx` | handleVerify: 유효성 검사·정상 성공만 replace·전환 상태·조건부 로그인 |
| `src/features/SignupVerify.tsx` | 인증 전 다음 동작 안내, 잘못된 링크의 조건부 로그인 |
| `src/components/VerificationNotice.tsx` (신규) | publish/read/consume: 메모리 한 번 전달 |
| `src/app/layout.tsx` | 위 안내 provider 연결 |
| `src/features/Login.tsx` | 일회성 안내 소비·표시, 기존 세션 구분 |
| `tests/navigation-request.test.mjs` (신규) | 이탈 승인/취소/새 요청/재진입 수명 3개 |
| `tests/auth-flow-browser.mjs` (신규) | 지연 API+목적지, 가입·인증·뒤로/앞으로·초점·크기 26개 |
| `tests/signup-browser.mjs` | 과거 문구, 허위 완료, 요청 구분, 로딩 render 대기 |
| `tests/auth-ui-browser.mjs` | 성공 후 로그인 및 실제 Mailpit 새 탭 흐름 |
| `tests/login-browser.mjs`, `tests/email-verification-resend-browser.mjs`, `tests/community-local-browser.mjs` | 실제 계정 준비 helper가 새 인증→로그인 흐름을 기다리도록 갱신 |

디자인 파일은 이전 작업과 동일하다. 원래 테스트의 주요 조건을 삭제하거나 약화하지 않았다. `community-browser.mjs`, `community-404-browser.mjs`, `htmlLimitedBots: /.*/`는 수정하지 않았다.

## 직접 실행한 검증

Node 24.21.0, pnpm 10.34.3, Next 16.3.6. 실제 설치된 Chrome을 Playwright headless로 사용했다.

| 검사 | 결과 | 실행 환경/구분 |
|---|---|---|
| `pnpm test` | **165 통과**, 실패/skip 0 | 기존 162 + 요청 수명 3 |
| `pnpm typecheck` / `pnpm lint` / `pnpm build` | 모두 통과 | 마지막 테스트 변경 후 lint도 재확인 |
| `git diff --check` | 통과 | 이전 사용자 변경 포함 |
| `auth-flow-browser.mjs` | **26 통과 × dev/production** | :8443 및 :8445, API 모의, DB 쓰기 없음 |
| `signup-browser.mjs` | **12 통과** | 실제 Spring 8, 입력/표시 2, 모의 실패 2. 회원 2명 생성 |
| `auth-ui-browser.mjs` | **12 통과** | 모의 UI 9 + 실제 API 흐름 2 + 런타임 1 |
| `login-browser.mjs` | **모의 38 통과** | 기존 로그아웃 이탈 취소·실패·새 입력·알림 자동/수동/초점/교체 포함 |
| `email-verification-resend-browser.mjs` | **모의 13 통과** | 기존 접수·오류·중복/수동 재시도 |
| `community-browser.mjs` | **모의 47 통과** | :8444, SSR mock :8461. 작성 요청+목적지 동시 지연, 헤더/취소/뒤로, 계속 작성, 입력 보존 포함 |
| production `pnpm test:smoke` | 통과 | :8445, 실제 Spring 공개 목록·상세 읽기/초기 HTML/메타데이터/404/robots |
| `community-404-browser.mjs` | **5 통과 / 2 실패** | 실제 Spring 공개 조회, 일반 UA/크롤러 무JS 화면의 표시 제목·목록 링크 실패 |

회귀 검사는 API 응답뿐 아니라 목적지 RSC 응답도 막고 **기존 폼이 계속 보이며 기존 pathname이 유지되는 동안** 늦은 응답을 전달한다. 따라서 단순한 언마운트 후 검사가 아니다. CSRF 대기 이탈은 signup POST 0, POST 대기 이탈은 추가 POST/CSRF 갱신/오류 focus/뒤늦은 자동 이동이 없음을 확인한다. 새 화면 재진입의 입력과 가입 상태도 유지된다.

새 탭 수동 인증에서 진입 시 POST 0, 200/ACTIVE 후 replace, 전환 중 중복 차단, 로그인 호출 0, 원래 가입 탭 유지, 안내 초점과 Tab, 뒤로/앞으로/새로고침의 잘못된 완료 표시 없음, 기존 회원 세션 보존을 확인했다. 1440/390/320px에서 넘침·입력/버튼 치수·키보드·오류 연결·대비를 검사했다. 캡처도 직접 열어 읽기/잘림을 확인했다.

### 재현 명령

아래 주소는 비밀값 없는 로컬 예시다. 기존 서버를 먼저 확인한다. 패키지 설치/환경설정 변경은 이번에 하지 않았다.

```sh
cd /Users/tackeunoh/Developer/iyum/web
export PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs
pnpm test
pnpm typecheck
pnpm lint
pnpm build
BASE_URL=http://127.0.0.1:8443 node tests/auth-flow-browser.mjs
# 실제 DB에 테스트 회원 2명을 생성한다.
node tests/signup-browser.mjs
# 실제 DB에 회원 1명 생성 + Mailpit 새 탭 인증 + 수동 로그인/로그아웃
IYUM_RUN_LOCAL_AUTH_UI_TEST=true node tests/auth-ui-browser.mjs
node tests/login-browser.mjs
node tests/email-verification-resend-browser.mjs
```

별도 production 확인 때는 빌드 후 `IYUM_API_INTERNAL_URL=http://127.0.0.1:8082 pnpm exec next start --hostname 127.0.0.1 --port 8445`를 실행하고 다음을 사용했다. 실제 인증 쓰기는 CORS를 변경하지 않고 기존 :8443에서 수행했다.

```sh
BASE_URL=http://127.0.0.1:8445 node tests/auth-flow-browser.mjs
BASE_URL=http://127.0.0.1:8445 pnpm test:smoke
BASE_URL=http://127.0.0.1:8445 node tests/community-404-browser.mjs
```

커뮤니티 모의 검사에는 별도 서버를 `IYUM_API_INTERNAL_URL=http://127.0.0.1:8461 pnpm exec next start --hostname 127.0.0.1 --port 8444`로 시작하고 `BASE_URL=http://127.0.0.1:8444 COMMUNITY_SSR_MOCK_PORT=8461 node tests/community-browser.mjs`를 실행했다. mock 서버는 스크립트가 종료 시 닫는다.

### 실제로 추가한 데이터

| 테스트 이메일 | 닉네임 | 이번 확인 결과 |
|---|---|---|
| `iyum-integration-mv7zonoo@example.com` | `IT_mv7zonoo` | 실제 가입 201/PENDING; 인증 수행하지 않음 |
| `iyum-csrf-mv7zonoo@example.com` | `CS_mv7zonoo` | CSRF 복구 후 수동 가입 201/PENDING; 인증 수행하지 않음 |
| `iyum-auth-ui-mv7zpon2@example.test` | `AU_mv7zpon2` | 회원 ID 27, ACTIVE; 새 탭 인증→로그인 안내→/me 401→직접 로그인→새로고침 복원→로그아웃·/me 401 |

회원 3명 외 게시글은 생성하지 않았다. 기존 데이터·메일·볼륨을 삭제하지 않았다. 실제 비밀번호는 메모리에서만 사용하고 인증 링크도 해당 테스트 메일에서만 읽었다. 비밀번호·쿠키·토큰·메일 원문은 문서/캡처/결과 JSON에 포함하지 않았다. 이 표는 이번 실행에 추가된 데이터만 기록하며 이전 디자인 검증의 회원은 기존 문서에 남아 있다.

## 캡처·남은 제한·최종 상태

[캡처 및 결과 JSON 목록](auth-ui/2026-10-14-followup/README.md)

- [production 모의 인증 후 로그인 1440](auth-ui/2026-10-14-followup/mock-production-verification-login-1440.png) / [390](auth-ui/2026-10-14-followup/mock-production-verification-login-390.png) / [320](auth-ui/2026-10-14-followup/mock-production-verification-login-320.png)
- [실제 API 인증 후 로그인 1440](auth-ui/2026-10-14-followup/real-verification-login-1440.png) / [390](auth-ui/2026-10-14-followup/real-verification-login-390.png)

**별도 미해결:** 무JS 커뮤니티 404에서 일반 브라우저·크롤러 모두 HTTP404/noindex는 맞지만 표시되는 제목/기본 링크가 없다(2건 실패). 정상 공개 목록·상세의 실제 초기 본문, JS 활성 안전한 복귀는 통과했다. 실패를 숨기거나 테스트를 건너뛰지 않았다.

Safari/Firefox, 모바일 실기기, 스크린리더, 브라우저별 BFCache 동작은 검증하지 않았다. 백엔드 코드를 변경하지 않아 Gradle 전체 검사는 재실행하지 않았다. login/resend의 추가 실제 API opt-in 및 community-local의 실제 글 작성 helper는 새 전환에 맞춰 수정했으나 해당 opt-in 흐름 전체를 재실행하지 않았다. 이번 요구의 실제 가입/인증/수동 로그인은 auth-ui의 실제 검사로 수행했다. 실제 재전송 제한 경계값은 이번에 재검사하지 않았다.

기존 Next :8443(PID 96054), Spring :8082(PID 33858), Docker/Mailpit/Postgres(PID 13993)는 유지했다. 직접 시작한 production :8444(PID 20130), :8445(PID 20102)와 검사 소유 mock :8461은 종료했다. `refactor/auth-ui`와 HEAD는 그대로이며 모든 변경은 기존 수정과 함께 unstaged/미추적 상태다. 검토용으로 남기며 Git 기록을 만들거나 원격으로 전송하지 않았다.
