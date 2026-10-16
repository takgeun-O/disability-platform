# 수정·삭제 리뷰 후속 검증 (2026-10-17)

상위 폴더의 이전 검사/캡처는 보존하고 이번 결과만 별도로 추가했습니다.

- `reentry-before-fix.json`: 수정 전 지연 PUT/재진입 결함 재현. 실제 폼 DOM이 분리됨.
- `edit-delete-results.json`: 수정 후 프로덕션 모의 브라우저 **29 통과**.
- `real-edit-delete-results.json`: 기존 개발 서버/Spring/Mailpit **7묶음 통과**. 이번 테스트 memberId 30 / postId 10(논리 삭제).
- `mock-results.json`: 기존 커뮤니티 **49 통과**.
- `topic-results.json`: 기존 주제 **23 통과**.
- `known-post-404-results.json`: 실제 Spring 공개 조회/프로덕션 검사 **5 통과·기존 무JS 2 실패**.

| 화면 | 1440px | 390px | 320px |
|---|---|---|---|
| 일반 상세 헤더, 모의 | [PC](header-1440.png) | [390](header-390.png) | [320](header-320.png) |
| 긴 제목·닉네임과 키보드 초점, 모의 | [PC](header-long-keyboard-1440.png) | [390](header-long-keyboard-390.png) | [320](header-long-keyboard-320.png) |
| 이번 생성 게시글, 실제 API | [PC](real-detail-1440.png) | [390](real-detail-390.png) | [320](real-detail-320.png) |

전체 과정/명령/한계는 [구현 문서의 후속 기록](../../../Community-Edit-Delete-UI-2026-10-17.md)을 참고하세요. 비밀번호·쿠키·토큰·메일 원문은 포함하지 않았습니다.
