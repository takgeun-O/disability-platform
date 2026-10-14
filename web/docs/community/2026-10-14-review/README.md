# 커뮤니티 리뷰 개선 캡처 · 검증 (2026-10-14)

[수정 내용·원인·실행 방법](../../Community-Review-Fixes-2026-10-14.md)

Chrome headless, ko-KR, reduced motion. 데스크톱 viewport 1440×1000, 모바일 390×844, 좁은 모바일 320px. 전체 페이지 촬영으로 PNG 높이는 viewport보다 길 수 있다.

| 화면 | 데스크톱 | 모바일 | 조건 |
| --- | --- | --- | --- |
| 실제 목록 | [1440](screenshots/real-list-1440.png) | [390](screenshots/real-list-390.png) · [320](screenshots/real-list-320.png) | 기존 Spring/DB, GENERAL/WELFARE_LIFE |
| 실제 상세 | [1440](screenshots/real-detail-1440.png) | [390](screenshots/real-detail-390.png) | 기존 검증용 게시글 ID 2 |
| 긴 모의 목록 | [1440](screenshots/mock-list-1440.png) | [390](screenshots/mock-list-390.png) | 가상 63개 글, 서버 공개 GET/브라우저 API 모의 |
| 모의 상세 | [1440](screenshots/mock-detail-1440.png) | [390](screenshots/mock-detail-390.png) | HTML 문자열 비실행·긴 문자열 |
| 모의 작성 | [1440](screenshots/mock-create-1440.png) | [390](screenshots/mock-create-390.png) | 모의 로그인·입력 보호 |
| 모바일 필터 | — | [320](screenshots/mock-filter-320.png) | 대화상자·키보드 조작 |

`real-*`는 기존 8443 개발 서버 + 실제 Spring 8082/PostgreSQL 공개 조회다. 기존 검증용 예시 회원/게시글만 촬영했으며 이번에 새 데이터를 만들지 않았다. Next 개발 표시(N)가 포함된다.

`mock-*`는 임시 8444 프로덕션 Next + 8855 로컬 공개 GET 모의 서버 + 브라우저 요청 모의다. 실제 DB 통합 검증으로 해석하지 않는다. `[검증 예시]` 글만 표시한다.

- [커뮤니티 모의 34개](mock-results.json)
- [실제 읽기 전용 6개](real-read-results.json)
- [기존 모의 로그인·로그아웃 회귀 38개](login-regression-results.json)

선정 PNG 12장과 민감값 없는 결과 요약만 보관한다. 원시 로그, trace, 쿠키, 비밀번호, 세션/CSRF/인증 토큰, 메일은 포함하지 않는다. 기존 `2026-10-14/` 자료는 보존했다.
