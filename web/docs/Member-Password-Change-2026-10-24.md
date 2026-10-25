# 마이페이지 비밀번호 변경 프런트 연동 — 2026-10-24

## 작업 기준과 범위

- 브랜치 `feat/member-password-change`, HEAD `bcdeaa31d7c090ab0ad8a5acc1fed86f186addbb`.
- 시작 시 작업 트리 clean. 이번 소스·테스트·문서만 미커밋으로 남겼다. 백엔드·DB 스키마·배포 설정·별도 iyum-preview worktree 변경 없음.
- Next 16.3.6 설치 문서 `node_modules/next/dist/docs/01-app/01-getting-started/05-server-and-client-components.md` 확인. 기존 마이페이지 Client Component에 상태·이벤트를 추가했으며 서버 라우팅·공개 조회 구조는 바꾸지 않았다.
- 사용자 요청대로 ZIP을 만들지 않았다. 이전 작업 결과를 이번 검증으로 계산하지 않았다.

## 구현과 검토 시작점

| 파일 | 변경 목적 |
|---|---|
| `src/features/MyPage.tsx` | 계정 관리에 비밀번호 변경 행 추가. 인증 하위 화면이 사라져도 완료/재로그인 안내는 상위 마이페이지에 유지 |
| `src/components/member/PasswordChangeDialog.tsx` | 기존 OverlayDialog, PasswordInput, FieldError 재사용. PC 모달/모바일 하단 시트, 3개 필드, 표시 토글, 오류 초점, 중복 제출·바깥 클릭 보호 |
| `src/lib/password-change.ts` | 현재/새/확인 비밀번호 검증, Retry-After 종료 시각·남은 시간 계산 |
| `src/lib/password-change-api.ts` | POST 전용 전송·엄격한 200 응답 검사·허용 필드 오류 매핑·불확실한 결과 안내. 탈퇴 API 오류 문구와 분리 |
| `src/lib/auth-session.ts`, `src/components/AuthProvider.tsx` | `captureMemberRequest(memberId)`가 로그인 세대 스냅샷을 보유. 같은 회원 재로그인까지 구분하고 해당 세대의 401/reloginRequired만 만료 처리 |
| `src/styles/mypage.css` | 기존 색상·간격·모달에 맞는 필드/버튼 스타일. 커뮤니티의 기존 이탈 확인창 CSS도 컴포넌트에서 불러옴 |
| `tests/password-change.test.mjs` | 검증·전송·응답 오류·인증 세대 11개 단위 테스트 |
| `tests/password-change-browser.mjs` | 지연 응답을 제어하는 모의 브라우저 28개 시나리오 |
| `tests/password-change-local.mjs` | 명시적 opt-in, 소유 manifest와 일치하는 전용 시드 계정에서만 실제 검증. 성공 시 원래 비밀번호 복원 |

## 확인한 API 계약과 처리

`MemberPasswordChangeController`, `MemberPasswordChangeRequest`, `MemberPasswordChangeRequestValidator`, `MemberPasswordChangeService`, 관련 컨트롤러 테스트 및 `DevCorsConfig`를 확인했다.

- `POST /api/v1/members/me/password`, 정확히 `currentPassword`, `newPassword`, `newPasswordConfirm`만 전송. 설정된 API base·include 쿠키·no-store 사용.
- 매 제출 직전 기존 `getAuthCsrf()`의 GET으로 받은 headerName/token 사용. CSRF 값 캐시 없음. GET 실패 시 POST 없음.
- 성공은 **200 + 실제 boolean reloginRequired**만 인정. HTML/깨진 JSON/필드 누락/문자열 boolean/204는 성공이 아니다.
- 현재 비밀번호는 Java `String.isBlank` 호환 공백 판정(`normalizeTitle`을 판정 용도로만 재사용)과 UTF-8 72바이트 검사. 새 비밀번호에는 기존 재설정의 UTF-16 최소 8자, ASCII 영문+숫자, UTF-8 72바이트 검증 재사용. 입력 원문은 변환하지 않는다. 확인값은 Java 공백 필수 검사와 정확한 원문 비교.
- 400의 필드 오류는 허용된 필드 아래 한 번만 표시. 서버 원문은 표시하지 않는다. 401, CSRF 403, 일반 403, 429를 구분한다.
- 유효한 Retry-After delta-seconds만 종료 시각으로 변환한다. 없거나 잘못된 헤더는 임의 대기시간을 만들지 않는다. 시간 만료 후 자동 제출하지 않는다.
- 500/통신/타임아웃/잘못된 성공 응답은 **이미 변경되었을 가능성**을 안내한다. POST 자동 재전송 없음. `/auth/me`로 변경 성공을 추측하지 않는다.

### 성공·초점·요청 수명

- false: 입력/표시 상태를 즉시 지우고 완료 안내를 표시한 뒤 CSRF GET만 새로 수행한다. `auth.refresh()`를 부르지 않으므로 마이페이지를 checking으로 바꾸거나 폼/완료 안내를 지우지 않는다. 후속 GET 실패는 별도 준비 오류이며 이미 확인된 성공은 유지한다.
- true: 비밀번호 변경 완료 + 재로그인 안내, 해당 요청의 인증 세대만 만료시킨다. 기존 AuthProvider 구독으로 개인 활동 캐시도 폐기한다. logout POST는 보내지 않는다.
- `useNavigationRequest`와 인증 세대를 함께 검사한다. 승인된 이탈·언마운트·로그아웃·새 로그인 뒤 늦은 응답은 화면/인증/초점을 바꾸지 못한다.
- 입력/요청 중 NavigationGuard 확인을 유지한다. 이탈 취소 시 입력 유지, 확정 시 이미 전송한 요청이 처리되었을 수 있음을 안내한다.
- 바깥 클릭은 닫지 않는다. 요청 전 닫기/취소/Escape는 컴포넌트를 제거하여 입력·오류·표시 상태를 초기화하고 진입 버튼으로 초점을 돌린다. 처리 중 닫기와 중복 제출을 차단한다.
- 비밀번호는 일시적 폼 상태에만 둔다. URL/브라우저 저장소/전역 인증 상태/로그에 저장하지 않는다.

## 직접 실행한 검증 (최종 소스 기준)

Node 24 + pnpm 10.34.3 사용. 기존 8443/8082 서버 유지. 새 기능·기존 회귀는 별도 프로덕션 서버 8459에서 실행했다.

| 실행 | 결과 |
|---|---|
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | **318/318 통과**, 실패·skip 0 |
| `pnpm build` | 통과 |
| `git diff --check` | 통과 |
| `node tests/password-change-browser.mjs` | **28/28 모의 시나리오 통과** |
| `node tests/auth-flow-browser.mjs` | 26/26 통과: 로그인/인증 결과·오래된 응답·탐색 |
| `node tests/member-browser.mjs` | 20/20 통과: 프로필/개수·닉네임·세션·계정 전환 |
| `node tests/member-menu-browser.mjs` | 22/22 통과: 모달·모바일 메뉴·스크롤·초점·로그아웃·무JS 메뉴 |
| `node tests/withdrawal-browser.mjs` | 11/11 통과: 탈퇴 모달·취소·오류·204·닉네임 기본 바깥 클릭 |
| `node tests/password-reset-browser.mjs` | 33/33 통과: 재설정 JS/무JS·사전 확인·204·오류·늦은 응답 |

재설정 검사는 기존 harness의 서버 조회까지 모의하기 위해 `.env` 없이 임시 소스 복사본을 `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8857 IYUM_API_INTERNAL_URL=http://127.0.0.1:8857 next build --webpack`으로 별도 빌드하고 8460에서 실행했다. 원본 최종 프로덕션 빌드는 기존 `pnpm build` 그대로 통과했다. 기존 서버는 재시작하지 않았다.

브라우저 실행 시 `PLAYWRIGHT_MODULE`에 로컬 Playwright 모듈 경로, `BASE_URL`에 해당 검증 서버 주소, 각 테스트의 artifact 환경변수를 지정했다. 외부 폰트가 없어도 일반 레이아웃/검증은 동작한다.

실행 로그와 구조화 결과: [`password-change-2026-10-24/`](password-change-2026-10-24/). 초기 테스트 도구 오류(`t.mock.env` 미지원), 모의 CSRF 헤더 이름/노출 헤더, 로그인 상태·모바일 링크 선택 조건은 실제 계약과 UI에 맞춰 수정했다. 서비스 기대값을 낮추거나 실패 검사를 제거하지 않았다.

## 실제 Spring 검증과 남은 제한

`IYUM_RUN_PASSWORD_CHANGE_LOCAL=true node tests/password-change-local.mjs`를 실행했다.

- manifest 소유 회원과 응답 ID/이메일을 대조한 전용 시드 계정으로 **독립 브라우저 컨텍스트 A/B의 로그인 및 보호된 회원 GET 200**까지 확인했다.
- 기존 8082 서버의 `/api/v1/members/me/password` **OPTIONS는 200이지만 Access-Control-Allow-Origin 등 허용 헤더가 없었다**. 실제 브라우저에서는 CSRF GET 이후 POST가 도달하지 않았다.
- 같은 세션으로 직접 API POST를 확인한 결과 **404**였다. 현재 체크아웃에는 컨트롤러·CORS 등록이 있지만 실행 중인 서버에는 해당 경로가 제공되지 않는다. 소스와 실행 상태 차이가 확인되었으며, 원인을 운영 코드 결함으로 단정하지 않았다.
- 비밀번호 변경 성공이 없으므로 **실제 비밀번호 변경/복원 및 인증 버전 변경 없음**. 기존 사용자 계정·게시글·댓글 변경/생성/삭제 없음. 테스트 소유 잠금은 해제했다.
- 실제 A 로그인 유지, B 다음 요청 401, 변경 전 CSRF403/새 CSRF 사용 가능, 이전 비밀번호 실패/새 비밀번호 성공은 **미검증**이다. 성공 결과나 통과로 표시하지 않는다.
- 백엔드 소스 수정은 필요하다고 확인되지 않았다. 사용자가 현재 브랜치로 백엔드를 다시 실행한 뒤 아래 절차를 수행해야 한다. 자동으로 사용자 서버를 종료/재시작하지 않았다.
- 실기기 iPhone Safari, 실제 소프트 키보드 및 비밀번호 관리자 자동완성은 미검증. Chrome의 1440/390/320px 및 낮은 높이 에뮬레이션만 확인했다.

### 실제 서버 재확인 순서

1. IDE에서 현재 체크아웃의 Spring Boot 서버를 재시작하고 새 password 경로/CORS가 반영됐는지 확인한다.
2. 전용 테스트 계정만 사용해 독립 A/B 컨텍스트로 로그인한다. 마이페이지 → 계정 관리 → 비밀번호 변경에서 A가 변경한다.
3. A의 보호 GET200, B의 다음 보호 GET401을 확인한다. A의 기존 CSRF로 변경 요청은 403, 새 CSRF를 이용한 빈 DTO 요청은 400 VALIDATION_FAILED여야 한다(추가 비밀번호 변경 없이 CSRF 통과 확인).
4. 이전 비밀번호 로그인401, 새 비밀번호 로그인200을 확인하고 **전용 계정의 원래 비밀번호로 복원**한다. 변경·복원으로 credentialVersion은 증가하며 기존 세션 무효화는 정책상 유지된다.
5. 재현 스크립트는 명시적 opt-in으로 실행한다. 일반 사용자 계정에 대입하지 않는다.

## 대표 캡처

모두 합성 사용자·모의 API 화면이며 비밀번호는 마스킹된 상태다.

- [PC 계정 관리](password-change-2026-10-24/mock/account-1440.png) · [PC 모달](password-change-2026-10-24/mock/dialog-1440.png)
- [390px 계정 관리](password-change-2026-10-24/mock/account-390.png) · [390px 시트](password-change-2026-10-24/mock/dialog-390.png)
- [320px 시트](password-change-2026-10-24/mock/dialog-320.png) · [낮은 높이](password-change-2026-10-24/mock/short-320.png)
- [현재 비밀번호 오류](password-change-2026-10-24/mock/field-error-PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH.png)

## 추가 기록: 백엔드 재시작 후 실제 연동 재검증 (2026-10-24 02:21 KST)

사용자가 백엔드를 재시작한 뒤 동일 브랜치/HEAD의 기존 로컬 검증 스크립트를 다시 실행했다. 위의 최초 404/CORS 실패 기록은 당시 결과로 보존한다. 이번 재실행에서는 CORS 및 password 경로가 정상 응답했다.

- 실행: `IYUM_RUN_PASSWORD_CHANGE_LOCAL=true node tests/password-change-local.mjs` (Node 24, 기존 Playwright 경로와 이번 실행 전용 결과 폴더 지정).
- 실제 프런트 `http://127.0.0.1:8443`, 실제 Spring `http://127.0.0.1:8082`. API 모의/가로채기 없음.
- seed manifest 소유 ID와 로그인 후 응답 ID/이메일을 대조한 전용 테스트 계정만 사용. 독립 브라우저 컨텍스트 A/B에 각각 로그인.
- 결과: **6/6 통과, 종료 코드 0**.

| 확인 항목 | 실제 결과 |
|---|---|
| 재시작된 CORS 설정 | OPTIONS 200, 8443 Origin·POST·CSRF 헤더·credentials 허용, Retry-After 노출 |
| A의 마이페이지 비밀번호 변경 | 실제 UI 제출 POST 200, `reloginRequired: false`, 완료 안내와 후속 CSRF GET 완료 |
| 현재 로그인 유지 | A의 후속 `/members/me` GET 200 |
| 다른 로그인 세션 만료 | 변경 전에 로그인한 B의 다음 `/members/me` GET 401 |
| 변경 전 CSRF | 빈 DTO POST가 403 `CSRF_TOKEN_INVALID` |
| 새 CSRF | 같은 빈 DTO POST가 400 `VALIDATION_FAILED` — CSRF 검증을 통과해 DTO 검증에 도달. 이 요청으로 비밀번호를 추가 변경하지 않음 |
| 로그인 자격 변경 | 이전 비밀번호 로그인 401, 변경한 비밀번호 로그인 200 |
| 테스트 후 복원 | 원래 비밀번호로 복원 POST 200 및 `reloginRequired: false`, 원래 비밀번호 로그인 200 재확인 |

비밀번호 변경과 복원은 테스트 계정에만 수행했다. 비밀번호 자체는 원래 값으로 복원했지만, 정상 변경 2회에 따른 인증 버전 증가/기존 세션 무효화 정책을 되돌리지는 않았다(버전 숫자의 직접 DB 조회는 수행하지 않음). 회원 탈퇴·회원 생성·게시글/댓글 수정·DB 직접 변경 없음. 비밀번호·세션 쿠키·CSRF 값은 결과 파일에 저장하지 않았다.

프런트/백엔드 운영 소스 및 테스트 스크립트는 이번 재검증에서 수정하지 않았다. 전체 lint/typecheck/build/단위·모의 브라우저 검사는 코드 변경이 없어 다시 실행하지 않았으며, 위의 최초 작업 결과와 구분한다. 실기기 Safari·소프트 키보드 검증은 계속 미실행이다.

- [이번 구조화 결과](password-change-2026-10-24/local-rerun-20261024-022128/results.json)
- [이번 실행 로그](password-change-2026-10-24/local-rerun-20261024-022128/run.log)
- 기존 개발 서버 두 개는 그대로 유지. 테스트 브라우저 컨텍스트와 시드 소유 잠금 해제 확인. 커밋·push·병합·ZIP 생성 없음.


## 추가 기록: 빈 제출 후 입력 초점 이동 수정 (2026-10-24)

사용자 제보: 빈 상태로 비밀번호 변경을 누른 뒤 타이핑하면 입력하는 칸이 바뀌는 현상.

- 수정 전 실제 브라우저 + 모의 API로 재현했다. 빈 제출은 세 필드의 오류를 설정하고 첫 칸에 초점을 준다. 이후 `onChange`가 입력한 칸의 오류를 지울 때 `fields` 의존 effect가 다시 첫 남은 오류 칸에 초점을 주었다. Tab 키나 공통 모달의 초점 순환이 원인이 아니었다.
- 1440/390/320px에서 첫 문자 입력 후 `change-currentPassword` → `change-newPassword`로 이동함을 `document.activeElement`로 확인했다. 서버가 여러 필드 오류를 돌려준 뒤 다른 칸에 입력할 때도 같은 문제가 재현됐다.
- `PasswordChangeDialog.tsx`: `reportFieldErrors()`에서 제출 결과에 따른 초점 대상을 한 번 기록하고 effect에서 소비한다. 저장 중에는 입력칸이 활성화될 때까지 기다리고, 요청 수명 검사는 유지한다. 타이핑으로 오류를 지우는 경로에는 초점 요청을 만들지 않는다. 기존 첫 오류 안내, `preventScroll`, API/세션/CSRF/오류 문구는 유지한다. 공통 OverlayDialog/PasswordInput과 백엔드는 변경하지 않았다.
- `password-change-browser.mjs`: 4개 회귀 시나리오 추가. 빈 제출 후 3개 화면 폭에서 한 글자씩 실제 키 입력을 보내 매번 활성 요소와 누적 입력값을 확인한다. 다른 칸에 오류가 남은 상태에서 역순으로 입력하는 흐름, 실제 Tab/Shift+Tab, 서버 다중 필드 오류 후 편집·Backspace도 확인한다. 기존 `fill()` 방식은 칸마다 초점을 다시 지정하므로 이번 버그를 검출하지 못했다.

이번 실행 결과:

| 실행 | 결과 |
|---|---|
| 수정 전 브라우저(기존 8443 개발 서버, 모의 API) | 기존 28개 통과, 새 초점 회귀 4개 실패 — 재현 기록 보존 |
| 수정 후 브라우저(최종 프로덕션 빌드, 별도 8459, 모의 API) | **32/32 통과**, 기존 검사 삭제/완화 없음 |
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | **318/318 통과**, 실패/skip 0 |
| `pnpm build` | 통과 |
| `git diff --check` | 통과 |

결과·수정 전후 캡처·이번 변경 diff: [focus-followup-20261024/](password-change-2026-10-24/focus-followup-20261024/).

이번은 초점 UI 회귀를 모의 API로 검증했다. 실제 Spring 비밀번호 변경은 다시 실행하지 않았으며, 앞 절의 실제 연동 6/6은 이전 실행 기록이다. 테스트 계정/DB/비밀번호 변경 없음. 실물 스마트폰·iOS 소프트 키보드는 미검증이다. 기존 8443/8082 서버를 보존하고 직접 시작한 8459 검증 서버만 종료한다. 기존 미커밋 구현을 보존했으며 커밋·push·병합·배포 없음.
