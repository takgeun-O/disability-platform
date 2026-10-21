# 비밀번호 재설정 토큰 사전 확인 · 2026-10-21

## 작업 기준과 기존 변경

브랜치 `feat/password-reset`, HEAD `13d77fe8e13f3c51d7410ab5a4809e8e93934aa4`.
시작 시 기존 프런트 재설정 UI·서버 액션·CSRF 전달·proxy·검사·문서가 미커밋/미추적 상태였다. staged 변경은 없었다. 시작본을 저장소 밖에 복사하고 그 위에서 수정했다. 별도 iyum-preview worktree, 기존 서버, DB 사용자 데이터는 건드리지 않았다. 커밋·push·병합·배포 없음.
기존 프런트 작업 기록은 `Password-Reset-Frontend-2026-10-21.md`이며, 그 통과 기록을 이번 검증으로 재사용하지 않았다.

## 백엔드 파일별 변경과 흐름

1. `auth/passwordreset/api/PasswordResetController.java`: `GET /api/v1/auth/password-reset/validate?token=…` 추가. token 한 개만 허용하고 누락/중복은 기존 무효 토큰 오류로 처리. 성공 204, 개인정보/토큰 본문 없음, no-store. 기존 두 POST 메서드는 그대로 유지.
2. `application/PasswordResetTokenValidationService.java`: `@Transactional(readOnly=true)`에서 형식 확인 → SHA-256 해시 → `findByTokenHash` → `PasswordResetToken.isUsable(clock.instant())` → `MemberRepository.existsByIdAndStatus(id, ACTIVE)` 순서. 쓰기 잠금·회원 엔티티 변경·토큰 사용/폐기·메일/이벤트 발행·발급 제한기 호출 없음. 현재 시점 판단일 뿐 미래 사용을 보장하지 않는다.
3. `application/PasswordResetTokenInput.java`: 기존 완료 서비스의 정확한 43자 URL-safe 형식 검사만 공통화. 원문을 trim하지 않는다. `PasswordResetService.java`는 이 함수로 대체했으며 회원→토큰 잠금, 해시 생성 후 재검사, 일회용 처리, credentialVersion 증가, flush·이벤트 순서는 변경하지 않았다.
4. `global/config/SecurityConfig.java`: 새 GET 경로만 permitAll. GET은 읽기 전용이고 기존 POST CSRF 필터는 그대로 동작한다.
5. `global/config/DevCorsConfig.java`: 새 정확한 경로에 GET/OPTIONS만 추가. 기존 출처·헤더·쿠키 정책을 복사. POST 경로의 CORS 정책은 변경하지 않음.
6. 기존 `PasswordResetException`/`GlobalExceptionHandler` 재사용: 무효 토큰은 400 `PASSWORD_RESET_TOKEN_INVALID`, `fieldErrors: []`, no-store. 저장소·도메인·DB 스키마 변경 없음.

요청 → 컨트롤러의 단일 파라미터 검사 → 읽기 서비스 → 형식 정책/해시 → 잠금 없는 저장소 조회와 기존 도메인 판단 → 회원 상태 → 204 또는 기존 공통 오류. 원본 토큰을 응답/로그/예외에 넣지 않는다. 백엔드의 설계 이유는 한국어 주석으로 코드에도 남겼다.

사전 확인은 폼을 보여 줄지 결정하는 조회이고, 실제 비밀번호 변경 권한을 예약하는 작업이 아니다. 입력 중 만료·재발급·다른 탭의 사용·회원 상태 변경이 가능하므로 최종 POST는 기존 잠금과 검증을 다시 수행한다. 이전에 재설정한 회원도 새 유효 토큰으로 다시 변경할 수 있다.

## 프런트 파일별 변경

- `src/app/reset-password/page.tsx`: 로컬 형식 검사 후 서버에서 실제 GET 확인. 누락/배열/중복/형식 오류는 호출 생략. 새 서버 조회를 완료한 후 유효한 경우에만 기존 PasswordReset 폼 렌더링. 메타데이터는 고정되어 토큰을 포함하지 않는다.
- `src/lib/password-reset-validation.ts`: 기존 internal/public API 주소 사용, GET·no-store·credentials omit·no-referrer·10초 제한·redirect error. 인증 쿠키/Authorization/CSRF/전달 IP 헤더 없음. 204만 성공, 정확한 400 오류 계약만 무효. 429는 제한, 네트워크·타임아웃·5xx·손상 응답은 확인 실패. 자동 재시도/polling/영구 저장 없음.
- `src/components/auth/ResetLinkStatus.tsx`: “사용할 수 없는 재설정 링크입니다” + 요청한 설명 + 새 메일 요청 버튼 + 로그인 보조 링크. 비밀번호 입력 설명이나 중복 오류 박스 없음. 최종 POST에서 토큰 무효를 받아도 같은 화면 사용.
- `src/features/ResetLinkFailure.tsx`: 조회 장애/제한 안내와 실제 재조회 버튼. JS에서는 router.refresh transition으로 중복 실행을 막고 “재설정 링크를 확인하고 있습니다.”를 표시. 무JS는 일반 GET 폼으로 다시 조회. 입력폼은 확인 전 없음.
- `src/lib/password-reset.ts`: 기본 안내 “8자 이상 · 영문과 숫자 포함”. 빈 값·짧음·영문/숫자 누락·확인 불일치를 구분하고, UTF-8 72바이트 초과는 “비밀번호가 너무 깁니다. 글자 수를 줄여 주세요.”. 비밀번호 원문은 그대로 전달. JS 정규식 `$`의 마지막 개행 허용을 막기 위해 토큰 길이도 정확히 43자로 확인.
- `src/features/PasswordReset.tsx`, `src/lib/password-reset-api.ts`: 새 안내 연결. 서버가 원인을 세분하지 않은 PASSWORD_RESET_PASSWORD_INVALID는 조건 확인 안내로 표시하며 길이 초과라고 추측하지 않음. 필드 아래 한 번만 출력.
- `src/app/password-reset-actions.ts`: 기존 공유 검증을 계속 사용. previousState는 무시하고 FormData를 검증한다는 주석만 정확하게 정정.

## SSR/대기 화면 선택 근거

설치된 Next 16.3.6의 `fetch.md`, `loading.md`와 [공식 fetch 문서](https://nextjs.org/docs/app/api-reference/functions/fetch), [loading 문서](https://nextjs.org/docs/app/api-reference/file-conventions/loading)를 확인했다. 의존성이나 기존 Next patch를 변경하지 않았다.
초기 시도에서 loading.tsx 경계를 사용하면 실제 프로덕션 무JS DOM에 폼이 숨겨지고 JS에는 일시적인 중복 입력이 생겼다. 따라서 경계를 제거하고 이전 무JS 열람 원칙대로 첫 문서는 조회 완료 후 반환한다. 최초 문서 로딩 중에는 브라우저 로딩 상태이며, 앱의 확인 중 문구는 JS 재시도 중 표시된다. 초기 화면에 아직 확인되지 않은 비밀번호 폼을 먼저 내보내지 않는다. 무JS에서도 최종 유효/무효/장애 화면과 재조회 링크가 실제로 보이는 것을 확인했다.

## 로그·캐시·남용 방지

- 새 코드에 요청 URL·토큰 로깅 없음. 네트워크 오류도 URL/원인 객체를 전파하지 않고 안전한 상태만 반환. 기존 Next incomingRequests의 /reset-password 로그 제외 유지. Next fetch URL 로깅은 활성화하지 않았으며 충돌하는 cache/revalidate 옵션도 사용하지 않음. 저장소 기본 설정에서 요청 세부/쿼리 access-log 활성화나 RequestLoggingFilter는 발견되지 않았다.
- 이번 모의 프로덕션 서버 로그와 최종 테스트 결과에는 원본 토큰·비밀번호·CSRF/쿠키 값을 기록하지 않았다. 테스트에는 합성 값만 사용하고 URL/본문/trace/HAR를 수집하지 않음. 백엔드 전체 stdout/XML에는 다른 테스트의 정보가 있을 수 있어 공유 자료에는 클래스별 집계만 포함한다.
- 개발 서버의 환경별 DEBUG/HTTP-client logging, 배포 reverse proxy/APM/CDN access-log는 별도 운영 확인이 필요하다. 전체 URL 쿼리 수집을 제거하거나 token을 마스킹해야 한다. 코드 검사와 모의 서버 로그 확인을 플랫폼 로그 검증 완료로 해석하지 않는다.
- 공개 GET은 인덱스 해시 조회와 상태 조회지만 DB 자원을 사용한다. 이번에는 발급 API의 제한기를 재사용하지 않았다(링크 확인이 발급 횟수를 소모하면 안 됨). 자동 polling도 없음. 외부 공개 전에 별도 조회 전용 요청 제한/edge 제한과 신뢰할 프록시 경계를 정해야 한다. 프런트 사전 확인은 Next 서버에서 호출하므로 IP 기반 제한 시 공유 출발지 문제를 고려해야 한다.
- 기존 무JS 메일 요청의 Next 서버 IP 집계 한계 유지. 임의의 X-Forwarded-For 신뢰나 server.forward-headers-strategy 변경 없음.

## 이번에 직접 실행한 검증

| 검사 | 최종 결과 | 범위 |
| --- | --- | --- |
| `./gradlew test` | 1,284 통과, 0 실패, 1 조건부 제외 | 103 클래스, 실제 Spring/Testcontainers 격리 PostgreSQL 포함 |
| `pnpm lint` | 통과 | 최종 소스·테스트 |
| `pnpm typecheck` | 통과 | Next 타입 생성 + TypeScript |
| `pnpm test` | 307 통과 | 프런트 전체 단위 테스트 |
| `pnpm build` | 통과 | 별도 모의 API 주소로 프로덕션 빌드 |
| `node tests/password-reset-browser.mjs` | 33/33 통과 | 모의 HTTP API, Chrome JS·무JS, 키보드 재시도 |
| 레이아웃 검사 | 33회 통과, 캡처 39장 | 1440·390·320px |
| `git diff --check` | 통과 | 공백 검사 |

조건부 제외 1건은 기존 LocalEmailVerificationMailTest이며 `IYUM_RUN_LOCAL_MAIL_TEST=true`를 설정하지 않아 실행되지 않았다. 새로운 skip/Disabled를 추가하지 않았다.

백엔드 보장:
- 신규 단위 14건: 형식/누락/개행, 존재하지 않는 토큰, 발급 이전, 발급/만료 경계, 사용/폐기, 회원 상태 불일치, 반복 확인 불변성과 조회 외 호출 없음.
- 컨트롤러 32건 전체: 신규 비로그인 GET 204/no-store/쿠키 생성 없음, 단일/중복 파라미터, 오류 개인정보 비노출, CORS GET 허용·POST/외부 Origin 거절. 기존 발급 IP 제한·CSRF 누락/불일치·정규화·204 유지.
- 서비스 통합 12건: DB 전체 회원/토큰 snapshot 불변, 이벤트 없음, 이후 실제 변경 및 같은 회원의 새 토큰 사용, 확인 후 만료/타 요청 사용 거절, 비활성/탈퇴 상태와 완료 서비스 방어 로직.
- API 통합 3건: 실제 Spring 체인/DB를 통해 쿠키·CSRF 없는 사전 GET 반복 → CSRF로 보호된 완료 POST → 사용 토큰 거절. 외부 SMTP 발송 없음.

프런트 보장: 조회 완료 전 문서/폼 비전송, 정확한 무효 응답만 오류 화면, 200/429/503 분리, 실제 재시도와 새로고침 재조회, 새 유효 링크, 개인정보/CSRF 없는 사전 GET, 최종 POST 토큰 무효, 72바이트 경계와 무JS 동일 검증, 중복 요청/불확실 POST 재전송 금지, 기존 완료/로그인 이동, 무JS 쿠키 전달과 오류/성공 흐름.

초기 테스트 실패는 `attempts.json`에 별도 기록했다. 탈퇴 상태 fixture 제약 위반은 기존 실제 withdraw 경로로 검증하도록 바로잡았다. 초기 문서 응답을 보류한 상태에서 Locator/CDP DOM이 동일 탐색 완료를 기다리는 검사 교착은 응답 경계의 관찰로 변경했다. 타임아웃 증가나 성공 조건 완화 없이 최종 전체 33건을 다시 실행했다.

## 실제 환경과 한계

- 실제 개발 회원의 비밀번호 변경·메일 반복 발송을 하지 않았다. 실제 사용자 정상 흐름 확인은 이전 사용자 보고이며 이번 직접 검증과 구분한다.
- 실행 중인 8082에 합성 잘못된 토큰 GET 한 번을 보냈더니 401 AUTHENTICATION_REQUIRED였다. 기존 프로세스에 새 API가 반영되지 않아 실제 UI 테스트 전에 IDE에서 백엔드를 재시작해야 한다. 8443 개발 서버는 PID 21622, 8082는 PID 17798로 보존했다.
- 실제 iPhone/Safari·소프트 키보드·화면 읽기 프로그램·배포 프록시 로그/세션 쿠키는 미검증. 캡처는 Chrome 에뮬레이션이며 외부 폰트 CDN을 테스트에서 차단해 시스템 fallback 폰트를 사용한다.
- 전체 프런트 smoke/커뮤니티 브라우저 테스트는 이번에 재실행하지 않았다. 기존 404 이슈와 patch의 개선/해결로 보고하지 않는다.
- 검증용 프로덕션 빌드는 mock 주소를 포함하므로 배포용으로 재사용하지 말고 실제 환경변수로 다시 빌드해야 한다. 배포하지 않았다.
- 직접 실행한 8457/8857 검증 서버는 종료했다. 초기 문서 확인은 최대 10초까지 기다릴 수 있으며 확인 실패 시 명시적 재시도한다.

## 캡처와 검토 시작점

`password-reset-validation-2026-10-21/final/preflight-{valid,invalid,failure}-{1440,390,320}.png`에 상태별 PC·모바일 화면이 있다. `nojs-preflight-{valid,invalid,failure}.png`는 실제 JS 비활성 브라우저 캡처다. 원본 기록을 자르거나 내부 숨김 DOM을 강제 노출하지 않았다.
백엔드 Controller → ValidationService → TokenInput/domain, 프런트 page → validation.ts → ResetLinkFailure/ResetLinkStatus → 기존 PasswordReset 순서로 검토하면 된다.
