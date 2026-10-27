# 내 댓글 좋아요 위치 조정 — 2026-10-27

기존 미커밋 좋아요 개수 연동 위에 표시 위치만 수정했다. MyComments.tsx에서 날짜 옆 표시를 댓글 본문 옆으로 옮기고 mypage.css에 반응형 본문/개수 배치를 적용했다. 읽기 전용 의미, 기존 색상, 링크와 조회 로직은 유지한다. 좁은 폭이나 긴 숫자는 줄바꿈을 허용한다.

## 이번 실행
- pnpm lint: 통과
- pnpm typecheck: 통과
- pnpm build: 별도 /private/tmp/iyum-member-count-build 복사본에서 통과
- git diff --check: 통과
- 현재 member-comments-browser.mjs의 관련 9개 시나리오: 모두 통과. 임시 복사본에서 readonly like counts, detail like PUT/DELETE, activity/account layout, missing original, long content/title 시나리오를 선택했다. 원본 테스트에는 실행 필터를 추가하지 않았다.
- BASE_URL=http://127.0.0.1:8465, mock API:8858. 프로덕션 Next + 모의 API 검증이며 실제 Spring/DB 변경 없음.
- 1440/390/320px 화면, 긴 본문/제목, 읽기 전용 표시, 3페이지 목록 및 좋아요 변경 후 복귀, 삭제 원문 복귀 위치 확인.

단위 테스트 전체 및 전체 브라우저 스위트는 이번 표시 위치 변경에서 재실행하지 않았다. 실기기/화면낭독기 검증은 미실행. 기존 검증 기록은 유지한다.
