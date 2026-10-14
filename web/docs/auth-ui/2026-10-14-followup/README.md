# 인증 동작 후속 검증 · 2026-10-14

[수정 내용·실행 명령·검증 결과](../../Auth-UI-Review-Fixes-2026-10-14.md)

기존 `../2026-10-14/`의 디자인 작업 캡처와 결과는 그대로 보존했다. 이 폴더는 후속 동작 수정 결과다.

| 파일 | 조건 |
|---|---|
| `mock-production-verification-login-1440.png` | production :8445, 모의 200/ACTIVE 후 로그인, 데스크톱 1440×1000 viewport |
| `mock-production-verification-login-390.png` | 같은 흐름, 모바일 390×844 viewport |
| `mock-production-verification-login-320.png` | 같은 흐름, 모바일 320×844 viewport |
| `real-verification-login-1440.png` | 기존 dev :8443, 실제 Spring·Mailpit 새 탭 인증 직후 로그인, 1440×1000 viewport |
| `real-verification-login-390.png` | 같은 실제 흐름, 390×844 viewport. Next 개발 표시가 보일 수 있음 |

PNG는 전체 페이지 캡처다. 인증 성공 안내가 초점을 받은 상태이며 입력란은 비어 있다. 비밀번호·인증 토큰·쿠키·메일 원문은 포함하지 않았다. 실제 검증의 새 탭은 Playwright의 같은 브라우저 context에서 열었고 원래 가입 탭은 이동시키지 않았다.

결과 JSON:

- `auth-flow-dev-results.json`, `auth-flow-production-results.json`: 각각 모의 26개 통과. API와 목적지 RSC를 함께 지연시킨 검사 포함.
- `signup-results.json`: 기존 회원가입 12개 전부 통과. 실제 API 8개, 입력/표시 2개, 모의 실패 2개. 테스트 회원 2명 생성 기록.
- `auth-ui-real-results.json`: 모의 9개, 실제 흐름 2개, 런타임 오류 검사 1개 통과. 테스트 회원 1명 및 화면 치수/대비 검사 기록.
- `login-results.json`: 모의 38개 통과.
- `resend-results.json`: 모의 13개 통과.
- `community-results.json`: SSR 공개 조회와 브라우저 API 모두 모의, 47개 통과. 실제 DB 쓰기 없음.
- `community-404-results.json`: production 실제 Spring 공개 조회, 5개 통과/2개 실패. 무JS 안내 본문·링크 실패를 그대로 보관.

원시 요청·메일·서버 로그는 포함하지 않았다. 테스트용 가상 이메일·닉네임은 생성 데이터 식별 목적으로만 기록했다. 기존 이미지 Git LFS 규칙을 변경하지 않았다.
