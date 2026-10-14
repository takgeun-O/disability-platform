# 인증 UI 캡처 목록 · 2026-10-14

실제 로컬 Chrome, 데스크톱 1440×1000 / 모바일 390×844 뷰포트. PNG는 표시 본문만 저장하며 대부분 전체 페이지다. 320px에서도 레이아웃을 검사했으나 별도 캡처는 저장하지 않았다.

`mock-`는 현재 UI에 브라우저 모의 API 응답을 제공한 캡처다. 기본 36장은 `next start :8445` 프로덕션 결과이며, 로그인 CSRF 복구 2장과 로그아웃 알림 구간 1장은 기존 `login-browser.mjs`를 실행한 개발 서버 :8443 결과다. `real-`는 :8443 → 실제 Spring :8082 → 로컬 DB/Mailpit 검증 결과다. 실제 통합 검증과 모의 상태를 구분한다.

검증에 사용한 이메일은 example.test 가상 계정이며 비밀번호·쿠키·토큰·메일 본문·전체 인증 URL은 포함하지 않았다. 파일명은 상태를 나타내며 실제 토큰을 표현하지 않는다. `mock-logout-notice-390.png`는 읽기 쉬운 상단 구간 캡처이고 실제 로그아웃 캡처는 전체 홈이다. 개발 서버 캡처에는 Next 개발 표시가 보일 수 있다.

| 파일 | 경로 | 상태 | PNG 크기 |
|---|---|---|---|
| [mock-consent-1440.png](mock-consent-1440.png) | `/register` | 약관 | 1440×1000 |
| [mock-consent-390.png](mock-consent-390.png) | `/register` | 약관 | 390×1004 |
| [mock-login-1440.png](mock-login-1440.png) | `/login` | 기본 로그인 | 1440×1000 |
| [mock-login-390.png](mock-login-390.png) | `/login` | 기본 로그인 | 390×898 |
| [mock-login-csrf-recovery-1440.png](mock-login-csrf-recovery-1440.png) | `/login?from=…` | 로그인 후 CSRF 재확인 안내 / 모의 회원 | 1280×843 |
| [mock-login-csrf-recovery-390.png](mock-login-csrf-recovery-390.png) | `/login?from=…` | 로그인 후 CSRF 재확인 안내 / 모의 회원 | 390×907 |
| [mock-login-validation-1440.png](mock-login-validation-1440.png) | `/login` | 필수값 오류·첫 입력 초점 | 1440×1000 |
| [mock-login-validation-390.png](mock-login-validation-390.png) | `/login` | 필수값 오류·첫 입력 초점 | 390×958 |
| [mock-logout-notice-390.png](mock-logout-notice-390.png) | `/ 또는 /community/posts/create` | 비차단 로그아웃 알림 | 390×250 |
| [mock-resend-1440.png](mock-resend-1440.png) | `/register/resend` | 기본 재전송 | 1440×1000 |
| [mock-resend-390.png](mock-resend-390.png) | `/register/resend` | 기본 재전송 | 390×856 |
| [mock-resend-accepted-1440.png](mock-resend-accepted-1440.png) | `/register/resend` | 202/ACCEPTED 조건부 발송 안내 | 1440×1000 |
| [mock-resend-accepted-390.png](mock-resend-accepted-390.png) | `/register/resend` | 202/ACCEPTED 조건부 발송 안내 | 390×978 |
| [mock-resend-error-1440.png](mock-resend-error-1440.png) | `/register/resend` | 서버 오류, 입력 보존 | 1440×1000 |
| [mock-resend-error-390.png](mock-resend-error-390.png) | `/register/resend` | 서버 오류, 입력 보존 | 390×953 |
| [mock-resend-processing-1440.png](mock-resend-processing-1440.png) | `/register/resend` | 재전송 요청 중 | 1440×1000 |
| [mock-resend-processing-390.png](mock-resend-processing-390.png) | `/register/resend` | 재전송 요청 중 | 390×891 |
| [mock-resend-validation-1440.png](mock-resend-validation-1440.png) | `/register/resend` | 이메일 입력 오류 | 1440×1000 |
| [mock-resend-validation-390.png](mock-resend-validation-390.png) | `/register/resend` | 이메일 입력 오류 | 390×927 |
| [mock-signup-info-1440.png](mock-signup-info-1440.png) | `/register/info` | 빈 회원정보 | 1440×1143 |
| [mock-signup-info-390.png](mock-signup-info-390.png) | `/register/info` | 빈 회원정보 | 390×1254 |
| [mock-signup-pending-1440.png](mock-signup-pending-1440.png) | `/register/verify` | 201/PENDING 응답 직후 | 1440×1000 |
| [mock-signup-pending-390.png](mock-signup-pending-390.png) | `/register/verify` | 201/PENDING 응답 직후 | 390×904 |
| [mock-signup-processing-1440.png](mock-signup-processing-1440.png) | `/register/info` | 지연된 가입 응답·중복 제출 방지 | 1440×1143 |
| [mock-signup-processing-390.png](mock-signup-processing-390.png) | `/register/info` | 지연된 가입 응답·중복 제출 방지 | 390×1254 |
| [mock-signup-validation-1440.png](mock-signup-validation-1440.png) | `/register/info` | 필수값 오류 | 1440×1203 |
| [mock-signup-validation-390.png](mock-signup-validation-390.png) | `/register/info` | 필수값 오류 | 390×1266 |
| [mock-verification-expired-1440.png](mock-verification-expired-1440.png) | `/register/verify` | 사용할 수 없는 링크 | 1440×1000 |
| [mock-verification-expired-390.png](mock-verification-expired-390.png) | `/register/verify` | 사용할 수 없는 링크 | 390×887 |
| [mock-verification-invalid-link-1440.png](mock-verification-invalid-link-1440.png) | `/register/verify` | 형식 오류 | 1440×1000 |
| [mock-verification-invalid-link-390.png](mock-verification-invalid-link-390.png) | `/register/verify` | 형식 오류 | 390×844 |
| [mock-verification-missing-1440.png](mock-verification-missing-1440.png) | `/register/verify` | 직접 진입, 가입 성공 단정 없음 | 1440×1000 |
| [mock-verification-missing-390.png](mock-verification-missing-390.png) | `/register/verify` | 직접 진입, 가입 성공 단정 없음 | 390×844 |
| [mock-verification-processing-1440.png](mock-verification-processing-1440.png) | `/register/verify` | 인증 요청 중 | 1440×1000 |
| [mock-verification-processing-390.png](mock-verification-processing-390.png) | `/register/verify` | 인증 요청 중 | 390×844 |
| [mock-verification-ready-1440.png](mock-verification-ready-1440.png) | `/register/verify` | 토큰 검사 후 수동 인증 대기 | 1440×1000 |
| [mock-verification-ready-390.png](mock-verification-ready-390.png) | `/register/verify` | 토큰 검사 후 수동 인증 대기 | 390×844 |
| [mock-verification-success-1440.png](mock-verification-success-1440.png) | `/register/verify` | 인증 완료·직접 로그인 안내 | 1440×1000 |
| [mock-verification-success-390.png](mock-verification-success-390.png) | `/register/verify` | 인증 완료·직접 로그인 안내 | 390×887 |
| [real-logout-notice-1440.png](real-logout-notice-1440.png) | `/ 또는 /community/posts/create` | 비차단 로그아웃 알림 | 1440×1874 |
| [real-logout-notice-390.png](real-logout-notice-390.png) | `/ 또는 /community/posts/create` | 비차단 로그아웃 알림 | 390×3888 |
| [real-signup-pending-1440.png](real-signup-pending-1440.png) | `/register/verify` | 201/PENDING 응답 직후 | 1440×1000 |
| [real-signup-pending-390.png](real-signup-pending-390.png) | `/register/verify` | 201/PENDING 응답 직후 | 390×904 |
| [real-verification-success-1440.png](real-verification-success-1440.png) | `/register/verify` | 인증 완료·직접 로그인 안내 | 1440×1000 |
| [real-verification-success-390.png](real-verification-success-390.png) | `/register/verify` | 인증 완료·직접 로그인 안내 | 390×887 |

JSON 결과 파일에는 검사명·화면 크기·실제 생성한 가상 테스트 회원 식별 정보만 보관했다. `community-404-results.json`의 2개 실패도 원본 결과 그대로 포함한다. 전체 HTML·메일·인증 로그는 복사하지 않았다.
