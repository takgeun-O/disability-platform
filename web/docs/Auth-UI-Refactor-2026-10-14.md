# 인증 화면 디자인 통일 · 2026-10-14

## 기준과 범위

- 작업 브랜치: `refactor/auth-ui`. 기준 HEAD: `60559e232687c3db5eca82ef3886b31d2dc323d1`.
- 작업 시작 시 staged/unstaged/미추적 파일 없이 깨끗했다. 로컬 소스가 기준이며 커밋·push·PR·병합·배포는 하지 않았다.
- 루트와 web의 AGENTS.md를 읽었다. 지침에 남은 “Git 미초기화”, “인증·게시글 모의 구현” 설명은 현재 HEAD와 다르므로 현재 실제 API 구현과 README를 기준으로 기능을 보존했다.
- 로그인, 약관 동의, 회원정보, 가입 후 인증 대기, 이메일 인증, 재전송, 로그아웃 알림의 **표현만** 변경했다. 커뮤니티·P03·백엔드·API 클라이언트·인증 상태 관리·NavigationGuard는 수정하지 않았다.
- 패키지 추가, 환경변수 변경, 인증 API 대체 없음. `next-env.d.ts`의 typegen/build에 따른 생성 경로 차이는 작업 시작 내용으로 복원했다.

## 현재 화면과 디자인 기준

| 경로 | 내용 |
|---|---|
| `/login` | 로그인 폼, 입력/서버 오류, 로그인 완료 후 보안 정보 재확인, 안전한 복귀 |
| `/register` | 필수 약관, 전체/부분 동의, 준비 중인 마케팅 동의 |
| `/register/info` | 이메일·비밀번호·확인·닉네임, 필드 오류, 처리 중 비활성 |
| `/register/verify` | 가입 직후 PENDING / 직접 진입 안내 / 링크 오류 / 수동 인증 대기·진행·성공·실패 |
| `/register/resend` | 이메일 입력, 접수·오류·처리 중 안내 |
| 공통 헤더 | 인증 상태 안내, 로그아웃 성공/오류 알림 |

홈·커뮤니티의 `iyum.css` 토큰과 `iyum-button`, `iyum-card`, `iyum-text-link`를 재사용한다.

| 기준 | 적용 |
|---|---|
| 배경·본문 | 흰색 `--iyum-surface`, 옅은 청회색 `--iyum-mist`, 네이비 `--iyum-ink` |
| 강조·보조·오류 | 청록 `--iyum-primary`, 보조 글자 `--iyum-muted`, 오류 `--iyum-danger` |
| 글꼴 | 기존 Noto Sans KR와 sans-serif 대체 글꼴 유지 |
| 크기 | 제목 28px/모바일 26px, 라벨 15px, 입력·주 버튼 16px, 설명·오류 14px |
| 조작 | 입력/주 버튼 최소 48px, 비밀번호 표시 버튼 최소 60×44px |
| 간격 | 필드 사이 20px, 주요 구역 24px, 모서리 기존 6px 토큰 |
| 데스크톱 | 최대 520px 흰 카드, 안쪽 32px, 바깥 위쪽 40px |
| 모바일 ≤600px | 카드 테두리·그림자·안쪽 여백 제거, 좌우 20px/위 24px. 큰 장식 영역 없음 |
| 초점 | 입력의 기존 inline `outline: none` 제거. 3px 파랑 outline, 오류 안내의 프로그램 초점도 표시 |
| 상태 안내 | 실제 문구와 role=status/alert 유지, 색 외에 문구로 결과 설명 |

1440/390/320px에서 가로 넘침, 입력/버튼 크기, 상단 배치, 글자 잘림을 검사했다. 390px 로그인 첫 입력은 화면 위에서 약 266px, 회원정보 첫 입력은 약 395px다. 가입 화면의 필수 안내를 삭제하지 않고 입력 중심으로 배치했다. 본문·입력·placeholder·버튼의 텍스트 대비 4.5:1 이상, 초점 색과 흰 배경 대비 3:1 이상을 계산해 확인했다. 브라우저 색상 검사이며 정식 접근성 인증은 아니다.

## 학습: 디자인 코드와 데이터 연결 코드

| 파일/함수 | 이번 디자인 변경 | 유지한 데이터 연결 |
|---|---|---|
| `src/components/auth/AuthFrame.tsx` | 공통 폭·카드·제목·선택적 가입 단계 표시 | 입력/API/인증 상태를 소유하지 않음. `step`은 부모가 전달한 화면 단계 |
| `src/components/auth/AuthFields.tsx` | `FieldLabel`, `FieldError`, `PasswordInput`으로 반복 폼 표현 공통화 | 부모의 value/onChange/ref/disabled/aria-describedby/자동완성을 그대로 입력에 전달 |
| `src/styles/auth.css` | 인증 화면에 한정된 간격·글자·필드·안내·모바일 스타일 | API 로직 없음. 다른 페이지에 공통 폼 스타일을 덮어쓰지 않음 |
| `src/features/Login.tsx` | 공통 폼·카드·안내 | `handleSubmit` → 기존 `auth.login` → `navigateAfterLogin`; `prepareAndContinue`의 CSRF 재확인, 안전한 복귀, 오류 focus 유지 |
| `src/features/SignupConsent.tsx` | 약관 행·단계·버튼 | `handleNext`의 필수 동의 검사, `createAgreements`와 `chooseAgreements`, `indeterminate` 유지 |
| `src/features/SignupInfo.tsx` | 동일한 입력·오류·버튼 기준 | `handleSubmit`의 validate → CSRF → signup, 201/PENDING 확인, 비밀번호 정리, `completeSignup`, 화면 이동 유지 |
| `src/features/SignupVerify.tsx` | 결과 안내와 단계 표시 통일 | `readEmailVerificationLink`, 실제 가입 직후 PENDING과 직접 URL 진입 구분 유지 |
| `src/features/EmailVerificationAction.tsx` | 진행·성공·실패 안내와 버튼 | `handleVerify`의 수동 POST, 토큰/CSRF/오류 분기, 늦은 응답 및 중복 요청 방지 유지 |
| `src/features/EmailVerificationResend.tsx` | 입력·접수·오류 안내 | `handleSubmit`의 새 CSRF 조회, 202/ACCEPTED 확인, unmount 후 무효화 유지 |
| `src/app/register/verify/page.tsx` | Suspense 대기 화면에도 AuthFrame 적용 | no-referrer, 기존 인증 화면 분리 유지 |
| `src/components/AuthControls.tsx`, `src/styles/iyum.css` | 안내 색·모서리·줄바꿈, 헤더 상태 글자/재시도 버튼 스타일 | `handleLogout`, `FeedbackNotice` 타이머·초점 일시정지·닫기·오류 보존 로직 유지 |

예를 들어 이메일 입력의 `onChange`는 기존 `setEmail`을 호출하고, 새 `auth-input` 클래스는 모양만 결정한다. 제출할 값이나 요청 URL을 `AuthFrame`/`AuthFields`로 옮기지 않았다. 비밀번호 표시 버튼은 키보드 조작, `aria-pressed`/`aria-controls`, 요청 중 비활성을 제공한다.

수정 전 파일과 수정 후 파일의 TypeScript 구문 트리를 비교해 위 feature의 주요 handler 및 useEffect와 `AuthControls.handleLogout`/effect 본문이 동일함을 확인했다. 인증 클라이언트, AuthProvider, SignupFlow, NavigationGuard, AppLink에는 diff가 없다.

## 보존한 사용자 흐름

- 가입 201/PENDING → 메일 링크의 수동 인증 → ACTIVE → **사용자가 직접 로그인**. 대기/인증 완료를 자동 로그인으로 바꾸지 않았다.
- 인증 메일 재전송은 **접수**다. “인증 대기 상태이고 재전송 조건을 충족하면 인증 메일이 발송됩니다” 문구를 그대로 사용한다. 실제 배달 완료로 표시하지 않는다.
- 입력 오류의 id/aria-describedby/ref와 focus, 일반 서버 오류의 alert, 성공 안내의 status를 유지했다.
- 버튼 disabled/fieldset disabled/aria-busy와 ref 기반 중복 차단, 현재의 늦은 응답 무효화, 수동 재시도 정책을 유지했다.
- 로그인 후 회원 헤더·새로고침 복원, 로그아웃 시 이탈 확인·새 입력 보호·비로그인 갱신은 기존 코드가 수행한다.
- 로그아웃 성공 알림은 5초 자동 닫기, 직접 닫기, 닫기 버튼 초점 중 정지, 교체 시 타이머 정리를 유지한다. 오류 알림은 자동 삭제하지 않고, 알림을 닫아도 경로·인증 상태는 바꾸지 않는다.

## 실행한 검증

| 검사 | 결과 / 구분 |
|---|---|
| `pnpm test` | 162개 통과, 0 실패/skip |
| `pnpm typecheck` | 통과 |
| `pnpm lint` | 새 브라우저 검사 포함 통과 |
| `pnpm build` | Next 16.3.6 프로덕션 빌드 통과 |
| `git diff --check` | 통과 |
| `tests/login-browser.mjs` | **모의 38개 통과**. 실패/불확실 응답, 안전한 복귀, 늦은 /me, 작성 중 이탈 취소/실패·추가 입력, 알림 타이머/초점/교체 포함 |
| `tests/email-verification-resend-browser.mjs` | **모의 13개 통과**. 입력 오류·중복 차단·접수·손상 응답·리디렉션·CSRF 실패·화면 이탈 포함 |
| 새 `tests/auth-ui-browser.mjs` (:8443) | 모의 화면 검사 9개 + **실제 API 흐름 2개** + 런타임 오류 없음 1개 = 12개 통과 |
| 동일 새 UI 검사 (:8445, production) | **모의 10개 통과**. 1440/390/320, Tab/Space, 오류 focus, 대비, 약관 유지, CSRF 수동 복구, 중복 제출, 인증 결과·재전송 상태 |
| production `pnpm test:smoke` (:8445) | 통과. 공개 경로 초기 본문, 기존 Spring 목록·상세 본문/메타데이터, HTTP404·robots. DB 쓰기 없음 |
| 기존 `tests/community-404-browser.mjs` (:8445) | **5개 통과 / 2개 실패**. 아래 미해결 항목 참조. 테스트 수정/비활성화 없음 |
| 기존 `tests/signup-browser.mjs` | 첫 검사에서 **중단**. 현재/수정 전 소스 모두에 없는 과거 문구를 기다림. 이후 검사 미실행, 회원 생성 0명. 아래 설명 참조 |

기존 signup 스크립트는 `/register/verify`에 “현재 이 화면에서 확인할 수 있는 가입 결과가 없습니다.”를 기대한다. 작업 시작 시점 코드도 이미 “메일에 포함된 이메일 인증 링크로 접속해 주세요. 이미 가입을 요청했다면 이 안내만으로 가입 실패를 의미하지는 않습니다.”였다. 이번 작업에서는 오래된 통합 검사 전체를 재작성하거나 통과로 기록하지 않았다. 새 검사는 실제 현재 안내와 **확인되지 않은 가입을 성공으로 표시하지 않는 조건**, 입력 오류·약관·CSRF·중복 제출 및 실제 가입/인증 흐름을 직접 검증한다.

새 테스트는 기본값에서 모두 모의 응답을 사용한다. 실제 계정 생성은 명시적인 `IYUM_RUN_LOCAL_AUTH_UI_TEST=true`일 때만 수행하며 세 주소가 `127.0.0.1`인지 검사한다. 환경 변수와 결과 디렉터리 예시(비밀값 없음):

```sh
# web 디렉터리, Playwright가 있는 환경에서 실행
PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs \
AUTH_UI_ARTIFACT_DIR=/tmp/iyum-auth-ui-check \
node tests/auth-ui-browser.mjs

# 기존 :8443, Spring :8082, Mailpit :8025를 사용하며 테스트 회원 1명을 새로 만든다.
IYUM_RUN_LOCAL_AUTH_UI_TEST=true \
PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs \
AUTH_UI_ARTIFACT_DIR=/tmp/iyum-auth-ui-real \
node tests/auth-ui-browser.mjs
```

실제 API 검증은 기존 개발 서버 :8443에서 수행했다. :8445는 공개 조회/모의 UI만 확인했으며 인증 CORS 정책을 넓히지 않았다. 재전송 제한·메일 발송 횟수의 실제 경계값 검사는 이번에 재실행하지 않았다. Safari/Firefox·모바일 실기기·스크린리더는 미검증이다. 백엔드 코드는 변경하지 않아 Gradle 테스트를 새로 실행하지 않았다.

### 실제 로컬 데이터

이번 실행에서 회원 **1명**을 만들었다. 회원 ID `23`, 이메일 `iyum-auth-ui-mv7uv8r6@example.test`, 닉네임 `AU_mv7uv8r6`, 최종 ACTIVE. Mailpit에서 해당 테스트 메일만 조회해 토큰은 메모리에서 사용했다. 201/PENDING → 인증 완료 → /me 401 → 직접 로그인 → /me 200 → 새로고침 닉네임 복원 → 로그아웃 → /me 401/새로고침 비로그인을 확인했다. 기존 회원/게시글/볼륨을 삭제하거나 초기화하지 않았다. 실제 비밀번호·쿠키·토큰·메일 원문은 문서나 캡처에 보관하지 않았다.

## 캡처와 파일 목록

[캡처 목록](auth-ui/2026-10-14/README.md)에 각 화면·상태·크기·실제/모의 조건을 기록했다. **45개 PNG**, 결과 JSON 5개를 보관한다. 대부분은 개발 표시가 없는 production 캡처다.

- [로그인 데스크톱](auth-ui/2026-10-14/mock-login-1440.png) / [모바일](auth-ui/2026-10-14/mock-login-390.png)
- [약관 모바일](auth-ui/2026-10-14/mock-consent-390.png)
- [회원정보 데스크톱](auth-ui/2026-10-14/mock-signup-info-1440.png) / [모바일](auth-ui/2026-10-14/mock-signup-info-390.png)
- [실제 가입 인증 대기](auth-ui/2026-10-14/real-signup-pending-390.png) / [실제 인증 성공](auth-ui/2026-10-14/real-verification-success-390.png)
- [인증 실패](auth-ui/2026-10-14/mock-verification-expired-390.png) / [재전송 접수](auth-ui/2026-10-14/mock-resend-accepted-390.png)
- [로그아웃 알림 구간](auth-ui/2026-10-14/mock-logout-notice-390.png)

변경한 소스는 위 표의 **기존 9파일 + 신규 3파일**이다. 추가로 `tests/auth-ui-browser.mjs`, 이 문서, 캡처 폴더, README의 안내 링크를 추가했다. 이미지의 기존 LFS 규칙은 유지한다. 아직 git add/커밋하지 않았으며 실제 PNG 파일을 로컬에 남겼다.

## 남은 제한과 직접 확인 순서

커뮤니티의 JavaScript 비활성 404는 별도 미해결이다. production에서 일반 브라우저/크롤러 모두 HTTP404·noindex는 반환하지만 JavaScript가 없으면 커스텀 제목·목록 링크가 표시되지 않는다(2개 실패). JS 활성 복귀와 정상 목록·상세의 실제 초기 HTML은 통과했다. `htmlLimitedBots: /.*/`, 상세 라우트, 404 컴포넌트, 기존 검사는 변경하지 않았다.

약관 화면의 기존 ‘취소’ 버튼은 아직 목적지 동작이 없고, 마케팅 동의·비밀번호 찾기는 기존 준비 상태다. 이번 UI 작업을 통해 새 기능이 구현된 것으로 표시하지 않았다. 약관 개발용 버전 안내도 유지한다.

1. 기존 `http://127.0.0.1:8443/login`에서 1440/390px로 확인한다. 빈 제출 후 이메일 초점, Tab으로 비밀번호와 표시 버튼 이동, Space로 표시 전환을 확인한다.
2. `/register`에서 일부/전체 동의와 필수 미동의 오류를 확인한 뒤 ‘다음’으로 회원정보를 확인한다. 제출하지 않고 모양만 검토할 수 있다.
3. 실제 가입을 진행하려면 새 테스트 이메일을 사용한다. PENDING은 대기이며, Mailpit의 링크 → 인증하기 → 직접 로그인 순서로 확인한다.
4. `/register/verify` 직접 진입과 `/register/resend`에서 안내 차이를 확인한다. 재전송 접수는 메일 도착 보장이 아니다.
5. 로그인 후 헤더 닉네임, 새로고침, 로그아웃 알림을 확인한다. 닫기 버튼에 키보드 초점을 유지한 동안 5초가 지나도 안내가 남고, 수동 닫기 후 비로그인 상태를 유지한다.

기존 :8443(Next PID 96054), :8082(Spring PID 33858), Docker/Mailpit/Postgres는 유지했다. 검증용으로 직접 실행한 :8445 production 서버만 종료했다. 최종 브랜치는 `refactor/auth-ui`, 변경은 모두 unstaged/미추적 상태로 검토할 수 있다.

## 2026-10-14 후속 인증 동작 수정

위 내용은 디자인 작업 당시 기록으로 보존한다. 이후 [인증 UI 동작 후속 수정](Auth-UI-Review-Fixes-2026-10-14.md)에서 가입 요청의 확정된 이탈 무효화, 약관 취소 홈 이동, 수동 이메일 인증 성공 후 로그인 replace 및 일회성 안내를 구현했다. 기존 signup-browser의 중단 원인을 고쳐 12개 전부 실행·통과했다. 단위 검사는 165개, 새 동작 브라우저 검사는 dev/production 각각 26개 통과했다. 기존 무JS 커뮤니티 404 2건은 여전히 실패하며 별도 미해결이다. 후속 문서에 변경 파일·실제 생성 회원 3명·검증 명령·새 캡처를 기록했다. 앞의 “표현만 수정”, “NavigationGuard 변경 없음”, “취소 미구현”, “가입 검사 중단”은 이전 디자인 단계에만 해당한다.
