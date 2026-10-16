# 게시글 수정·삭제 검증 자료

[구현·학습 문서](../../Community-Edit-Delete-UI-2026-10-17.md)

- mock-edit-delete-results.json: 최종 Turbopack 프로덕션에서 모의 API 24개 통과.
- community-regression-results.json: 기존 커뮤니티 모의 회귀 49개 통과.
- topics-regression-results.json: 기존 주제 모의 회귀 23개 통과.
- real-results.json: 실제 로컬 Spring/Mailpit 연동 4묶음 통과. 신규 회원 29 / 게시글 8(검증 후 논리 삭제).
- known-post-404-results.json: 기존 게시글 404 검사 5통과/무JS 2실패. 과거 알려진 실패 유지.

| 화면 | PC | 모바일 |
|---|---|---|
| 실제 작성자 상세 버튼 | [1440](real-detail-1440.png) | [390](real-detail-390.png) |
| 수정 폼(모의) | [1440](mock-edit-1440.png) | [390](mock-edit-390.png) / [320](mock-edit-320.png) |
| 삭제 확인(모의) | [1440](mock-delete-1440.png) | [390](mock-delete-390.png) / [320](mock-delete-320.png) |

모의 제목은 검증 예시이며 서비스 게시글이 아니다. 실제 캡처도 이번에 생성한 테스트 게시글만 촬영했다. 비밀번호·쿠키·토큰·메일 원문·원시 로그는 포함하지 않았다.
