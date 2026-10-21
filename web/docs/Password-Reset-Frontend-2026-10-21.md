# 비밀번호 재설정 프런트엔드 — 2026-10-21

## 기준과 범위

- 브랜치 `feat/password-reset`, 시작 HEAD `13d77fe8e13f3c51d7410ab5a4809e8e93934aa4`.
- 시작 시 staged/unstaged/untracked 변경 없음. 이번 변경은 web 소스·테스트·이 문서와 캡처다. backend, DB, 의존성, 별도 iyum-preview worktree 변경 없음. 커밋·푸시·병합·배포 없음.
- 사용자가 확인한 Spring 단위/통합 테스트·실제 메일·비밀번호 변경 결과는 사용자 보고다. 이번 작업의 직접 검증은 아래 프런트 단위/모의 API 검증이다.
- 설치된 Next 16.3.6의 `node_modules/next/dist/docs/01-app/02-guides/forms.md`, `server-actions.md`, cookies 문서와 `server/app-render/action-handler.js`를 확인했다. 기존 Next patch/검색 차단 정책 유지.

## 화면과 구현

| 파일 | 변경 이유 |
|---|---|
| `src/features/Login.tsx` | 기존 비밀번호 찾기 링크 문구를 “비밀번호를 잊으셨나요?”로 연결 |
| `src/app/forgot-password/page.tsx` | 준비 중 화면을 실제 이메일 요청 화면으로 교체; 동적 렌더링 |
| `src/app/reset-password/page.tsx` | 단일 43자 토큰 형식 검사; 누락/중복/형식 오류는 폼 대신 안내; 메타데이터는 고정 |
| `src/features/PasswordReset.tsx` | 기존 AuthFrame/FieldError/PasswordInput 재사용, 요청·완료 폼, 진행 상태, 중복 방지, 필드 초점, 입력 보존 |
| `src/app/reset-password/complete/page.tsx`, `src/features/PasswordResetComplete.tsx` | 토큰 없는 완료 페이지, 인증 상태 재조회, 명시적인 로그인 링크. 자동 로그인/로그인 강제 이동 없음 |
| `src/lib/password-reset.ts`, `password-reset-api.ts` | 원문 비밀번호·UTF-8 72바이트·확인 값 검증, 엄격한 202/204 처리, 오류 매핑 |
| `src/app/password-reset-actions.ts`, `src/lib/password-reset-server.ts` | 새 무JS 제출 대안. 기존에는 재사용할 인증 Server Action이 없었음 |
| `src/lib/signup-api.ts` | 공통 getCsrfContext에 선택적 base/fetch 주입만 추가. 인자 없는 기존 호출 동작 유지 |
| `src/lib/password-reset-request.ts`, `src/proxy.ts` | no-referrer 네이티브 폼의 Origin:null/Next 액션 호환을 동일 출처의 지정된 두 경로에 한정 |
| `next.config.ts`, `src/lib/navigation.ts` | no-referrer/no-store, 개발 요청 로그에서 토큰 URL 제외, 토큰 경로를 로그인 복귀 주소로 사용하지 않음 |
| `tests/password-reset.test.mjs`, `password-reset-browser.mjs`, `production-smoke.mjs` | 단위·브라우저/무JS/HTTP 검사 추가, 준비 중 대신 실제 화면의 초기 HTML 기대값 갱신 |

`GET /api/v1/auth/csrf`로 받은 headerName/token을 사용한 뒤 `POST /api/v1/auth/password-reset/request` 또는 `/complete`를 호출한다. JS 경로는 기존 NEXT_PUBLIC_API_BASE_URL·credentials:include를 사용한다. 메일 요청 202는 백엔드와 동일한 공통 안내이며 실제 발송/가입 여부를 보장하지 않는다. 이메일 다시 입력하기는 JS에서 같은 주소로 불필요한 탐색을 발생시키지 않고 폼을 열며, 무JS에서는 일반 링크로 새 문서를 연다.

새 비밀번호는 UTF-16 길이 8 이상, ASCII 영문·숫자 포함, UTF-8 72바이트 이하 및 정확한 확인 값 일치로 검사한다. 원문 공백을 trim하지 않는다. 204에는 JSON 파싱하지 않는다. 성공 시 입력 폼을 제거하고 토큰 없는 완료 주소로 replace/redirect한다. 최종 토큰 유효성은 API가 판단하며 링크 방문/미리보기에는 소비 API를 호출하지 않는다.

400의 token-invalid는 재요청 링크 안내, password-invalid/unchanged·VALIDATION_FAILED는 입력 바로 아래 한 번, 429·403 CSRF·통신/서버 문제는 폼 안 공통 영역에 표시한다. 알 수 없는 서버 원문은 노출하지 않는다. POST 자동 재시도 없음. 다음 사용자 제출에서 CSRF를 다시 받는다. 탐색 승인 이후 늦은 요청 결과로 이전 화면의 상태·초점·주소를 변경하지 않는다. 성공한 변경의 인증 상태는 기존 AuthProvider.refresh로 확인하며 개인 캐시 폐기 정책을 유지한다.

## 무JS 연결과 보호

- 기존 로그인·가입 폼은 클라이언트 핸들러 방식으로, 재사용할 서버 액션은 없었다. 기존 화면의 JS 필요 동작은 이번 범위에서 재작성하지 않았다. 새 재설정 두 폼에 useActionState/form action 대안을 추가했다.
- 무JS 액션은 IYUM_API_INTERNAL_URL(없으면 공개 API base)을 사용하며, 설정된 서버 주소만 사용한다. 현재 Spring 기본 JSESSIONID만 요청별로 전달한다. CSRF 응답의 Set-Cookie를 같은 POST와 브라우저에 전달하고 HttpOnly/Path/SameSite 등의 속성을 Next의 쿠키 파서로 보존한다. 모든 쿠키나 Authorization/전달 IP 헤더를 무조건 전달하지 않는다.
- 실제 확인: no-referrer 문서의 네이티브 폼 POST에 Chrome이 Origin:null을 보냄 → Next 16.3.6 액션 Origin 검사에서 거절되어 오류 문서. 설치된 action-handler와 서버 로그로 확인했다.
- 전역 allowedOrigins에 null을 추가하거나 Origin 검사를 끄지 않았다. `/forgot-password`, `/reset-password`의 POST, multipart, Origin:null, Sec-Fetch-Site:same-origin, Mode:navigate, Dest:document, Host 일치가 모두 맞을 때만 내부 Next 요청 Origin을 복원한다. 실제 Host를 사용해 NextURL의 localhost 정규화와 127.0.0.1 차이를 피한다. 기존 커뮤니티 proxy 분기는 그대로다.
- cross-site/same-site/메타데이터 누락은 이 보정에서 제외된다. 실제 생성된 폼 필드와 명시적 HTTP 헤더로 Next 거절 및 CSRF/API 미호출을 검증했다. 이 비정상 요청의 500은 기존 Next 액션 거절 응답이며 정상 폼 오류 UI의 기대값이 아니다.
- 이 처리는 브라우저가 관리하는 Fetch Metadata에 의존한다. 해당 헤더를 제공하지 않는 오래된 브라우저의 무JS 제출은 미지원/미검증이다. 프록시의 실제 Host/전달 헤더 구성은 배포 환경에서 추가 검증해야 한다. 임의의 클라이언트 전달 헤더를 백엔드 IP로 신뢰하지 않는다.
- `server.forward-headers-strategy=none`을 유지하므로 무JS 액션의 IP 제한 기준은 **Next 서버에서 Spring에 연결한 IP**다. 이 경로의 사용자는 제한을 공유할 수 있다. 브라우저 직접 API 경로와 다른 점이며 사용자별 실제 IP 제한 또는 배포 프록시 보안을 검증했다고 주장하지 않는다.
- 무JS 오류 재렌더에서는 비밀번호를 응답 상태에 돌려주지 않으므로 다시 입력해야 한다. JS 오류에서는 입력을 유지한다.

## 민감정보

토큰·비밀번호·쿠키·CSRF를 저장소/분석/로그에 새로 기록하지 않는다. 토큰은 입력 화면의 요청 처리에만 사용하며 완료 후 URL/폼에서 제거한다. 초기 토큰을 미리 지우지 않아 새로고침·무JS 제출이 유지된다. 재설정 페이지는 no-referrer/no-store이며 고정 메타데이터를 사용한다. 서버 액션은 입력값을 반환 상태에 포함하지 않는다. 검토 자료는 가상 .test 이메일만 사용하고 비밀번호 입력 텍스트는 캡처 CSS로 숨겼다(제어 요소·테두리·크기는 유지). trace/HAR/쿠키/원본 인증 URL을 저장하지 않는다. 배포 플랫폼의 별도 액세스 로그 설정은 이번 범위에서 변경/검증하지 않았다.

## 직접 실행한 검증

Node 24.21.0, pnpm 10.34.3. 개발 서버 8443은 종료하지 않고 별도 프로덕션 8457과 모의 API 8857을 사용했다. 실행 후 직접 시작한 임시 서버만 정리했다.

| 명령/검사 | 결과 | 범위 |
|---|---|---|
| `pnpm lint` | 통과 | src/tests |
| `pnpm typecheck` | 통과 | Next typegen + tsc |
| `pnpm test` | 304 통과, 실패/skip 0 | 프런트 전체 단위; 재설정 새 검사 14 포함 |
| `pnpm build` | 통과 | Next 프로덕션 빌드, 두 폼/완료 경로 동적 렌더 |
| `node tests/password-reset-browser.mjs` | 24/24 통과 | JS 18, 무JS 3, 명시적 HTTP 출처 거절 3 |
| `node tests/auth-ui-browser.mjs` | 10/10 통과 | 기존 인증 화면·키보드·가입/인증/재전송, mock만 |
| `node tests/auth-flow-browser.mjs` | 26/26 통과 | 기존 인증·이탈·지연 응답 흐름, mock만 |
| `git diff --check` | 통과 | 추가/수정 공백 검사 |

새 검사에서 1440/390/320px 각 8개 상태(총 24개 배치 검사)의 가로 넘침 없음, 이메일 요청/접수/비밀번호/완료/주요 오류 캡처 및 무JS 3개 캡처를 남겼다. 단순 HTML 문자열이 아니라 JS 비활성 브라우저에서 안내·입력·링크의 실제 표시와 제출을 확인했다. 202/204, 정확한 세션/CSRF 전달, 429, token/password/field/CSRF/네트워크/5xx 오류, 중복 제출 차단, 늦은 응답, 비밀번호 원문 보존을 검사했다.

### 실패 발견 및 테스트 조정 이력

- 최초: 이메일 다시 입력 링크가 같은 경로에서도 탐색 수명을 끝냄 → onNavigate에서 같은 폼 표시로 처리.
- 무JS Origin:null/Next 거절 → 위의 제한적인 동일 출처 처리 추가. 무JS 실패 3개 모두 최종 통과.
- 완료 문구는 Next route announcer에도 존재함 → 실제 h1 역할로 선택하도록 테스트 명확화.
- 응답 소켓 강제 종료는 브라우저 네트워크 계층의 동작과 섞임 → 클라이언트 route.abort로 명시적 네트워크 실패를 주입하고 앱 요청 1회·자동 재전송 없음 유지.
- CDP route.continue는 브라우저가 관리하는 Sec-Fetch 헤더 변경을 신뢰성 있게 유지하지 않음 → 실제 생성된 폼을 명시적 HTTP 요청으로 전송하여 거절 경로 확인. 허용 기대값으로 변경하지 않음.
- 최종 전 실행 두 차례는 각각 초기 페이지 load/domcontentloaded 시간 초과 1개. 제한 시간을 늘리지 않았다. 최종 모의 브라우저 검사는 외부 폰트 CDN을 차단해 외부 네트워크와 분리했고 24개 모두 실행·통과했다. 캡처의 기본 폰트는 OS fallback이며 앱의 폰트 설정은 변경하지 않았다. 이전 결과 JSON은 별도 보존한다.

### 재실행

```sh
# web에서; 실제 개발 서버와 다른 포트를 사용
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8857 IYUM_API_INTERNAL_URL=http://127.0.0.1:8857 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8857 pnpm exec next start --hostname 127.0.0.1 --port 8457
# 다른 터미널: 테스트 자체가 임시 mock API 8857을 열고 닫음
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs node tests/password-reset-browser.mjs
# 기존 인증 검사도 BASE_URL=8457, API_BASE_URL=8857, PLAYWRIGHT_MODULE 설정
```

Playwright는 기존 번들 런타임을 사용했으며 의존성을 추가하지 않았다. 필요하면 RESET_ARTIFACT_DIR/AUTH_UI_ARTIFACT_DIR/AUTH_FLOW_ARTIFACT_DIR로 결과 경로를 지정한다. 테스트용 API 주소로 만든 빌드이므로 배포에 재사용하지 말고 실제 환경값으로 다시 빌드한다. 기존 개발 서버는 자신의 기존 환경값을 사용한다.

## 미실행/남은 제한

- 이번 프런트에서 실제 Spring/SMTP를 통한 메일 요청·비밀번호 변경은 실행하지 않음. 사용자 계정·개발 DB 변경 없음. 사용자 보고의 backend 검증과 구분한다.
- 실물 iPhone Safari, 실제 소프트 키보드, 스크린리더 및 배포 환경 쿠키/프록시 구성은 미검증. Chrome 데스크톱과 viewport 변경 결과다.
- 전체 `test:smoke`는 실제 공개 게시글 데이터와 내부 API 설정을 함께 요구하므로 이번 재설정 mock 환경에서는 실행하지 않았다. 초기 HTML의 새 화면과 무JS 제출은 새 검사로 검증했다. 관련 없는 커뮤니티 404/전체 백엔드 테스트도 재실행하지 않았다.
- 기존 로그인·가입 전체를 무JS로 전환하지 않았다. 새 재설정 흐름에서 로그인 링크로 이동하는 데까지 무JS로 검증했다.
- 메일 재요청에 별도 60초 UX 타이머는 추가하지 않았다. 서버 429 판단과 수동 재요청을 따른다.
- 완료 페이지는 안내 화면이다. 주소 직접 접근만으로 실제 비밀번호 변경 사실을 검증하는 API가 추가된 것은 아니다.

## 사용자가 확인할 순서

1. 기존 8443의 `/login`에서 “비밀번호를 잊으셨나요?” → 이메일 입력 → 공통 접수 안내/메일함 확인.
2. 본인이 관리하는 테스트 계정의 메일 링크를 열어 새 비밀번호·확인 값을 입력. 빈/불일치/동일 비밀번호 오류를 먼저 확인.
3. 성공 후 토큰 없는 완료 화면과 로그인 버튼을 확인하고 새 비밀번호로 수동 로그인.
4. 사용한 링크를 다시 제출하면 새 메일 요청 안내가 나오는지 확인. 실제 사용자 계정은 변경하지 않는다.
5. 브라우저 JS를 끄고 새 링크의 오류/성공 흐름을 확인한 뒤 JS를 다시 켠다. 반복 발송은 서버 제한을 준수한다.

## 대표 캡처

[메일 요청 PC](password-reset-frontend-2026-10-21/final/request-1440.png) · [새 비밀번호 PC](password-reset-frontend-2026-10-21/final/password-1440.png) · [새 비밀번호 390](password-reset-frontend-2026-10-21/final/password-390.png) · [320](password-reset-frontend-2026-10-21/final/password-320.png) · [입력 오류](password-reset-frontend-2026-10-21/final/field-error-390.png) · [완료](password-reset-frontend-2026-10-21/final/complete-390.png) · [무JS 완료](password-reset-frontend-2026-10-21/final/nojs-complete-390.png)
