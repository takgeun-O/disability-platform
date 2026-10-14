# 2차 리뷰 검증 캡처 (2026-10-14)

원본 소스: `feat/community-posts-ui`, HEAD `88650cf1443f635bc0d36e9bd20c83c607576c6f`와 현재 미커밋 작업.

**등록 관련 2건 완료, 무JS 404 안내는 미해결.** 404 실패 이미지도 증거로 포함한다. 원시 메일·비밀번호·쿠키·토큰·trace·서버 원시 로그는 포함하지 않는다. 실제 테스트 회원의 가상 닉네임과 예시 글만 표시한다.

| 파일 | 캡처 조건/결과 |
| --- | --- |
| `real-create-1440.png`, `real-create-390.png` | 실제 8443 + Spring8082, 가상 테스트 회원으로 작성 전 |
| `real-detail-1440.png`, `real-detail-390.png` | 실제 저장된 게시글 ID4, 새로고침 후 |
| `real-list-1440.png`, `real-list-390.png` | 실제 GENERAL/WELFARE_LIFE 목록, ID4 포함 |
| `mock-create-1440.png`, `mock-create-390.png` | 프로덕션8444 + SSR8855/브라우저 API 모의, 작성 화면 |
| `mock-list-1440.png`, `mock-list-390.png` | 모의 목록, 실제 DB 자료 아님 |
| `404-js-1440.png`, `404-js-390.png` | 프로덕션8445 + 실제 Spring, JS 활성: 안내/안전한 복귀 표시 |
| `browser-404-nojs-1440.png`, `crawler-404-nojs-1440.png` | 프로덕션8445 + 실제 Spring, JS 비활성: **빈 본문으로 실패**, UA별 HTTP404 |
| `mock-results.json` | 47개 모의 회귀 통과 |
| `login-mock-results.json` | 기존 인증 모의 38개 통과, 실제 로그인 통합 검사와 별도 |
| `real-results.json` | 실제 Spring/DB/Mailpit 7개 묶음 통과, 생성 회원22/게시글4 기록 |
| `404-results.json` | 5통과/2실패. 무JS 링크가 없으므로 클릭 이동은 실행 불가 |
| `verification-results.json` | 이번 검사 요약; 전체 성공으로 표시하지 않음 |

Chrome headless/ko-KR, 1440px 또는390px, 원본 전체 페이지 PNG. 실제 작성 흐름에서320px도 가로 넘침을 검사했다. 이전 검증 폴더는 그대로 보존한다.
