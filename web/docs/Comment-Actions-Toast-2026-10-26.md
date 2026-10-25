# 댓글 수정·삭제 성공 토스트 (2026-10-26)

feat/community-post-like / HEAD 7b0fe586886c64bd45d17ced58e781385b9c6de8. 기존 미커밋 좋아요·댓글 등록 토스트·정보 메뉴 작업 보존.

이번 변경: Comments.tsx, CommentSuccessToast.tsx, community-comments-browser.mjs. 토스트에 message prop을 추가하고 POST/PUT/DELETE 성공 직후의 공통 경로에서 각각 등록/수정/삭제 성공 문구를 전달한다. 수정·삭제의 기존 inline 성공 문구는 제거했다. 오류·없어진 댓글 안내·로딩은 유지한다. 등록과 같은 서버 성공/화면요청 유효성/인증 snapshot 검사, event id, 5000ms+250ms 타이머를 그대로 사용한다. 연속 수정→삭제 성공은 마지막 성공부터 수명을 갱신하며 중복 live 메시지가 없다. 기존 입력초기화/수정버튼 초점복귀/삭제 확인창/목록 갱신은 변경하지 않았다. CSS·백엔드·정책 변경 없음.

이번 검사: pnpm lint, pnpm typecheck, pnpm test 351/351, 별도 임시 경로 pnpm build, git diff --check 통과. 최종 프로덕션8465 + 모의 API8856에서 community-comments-browser.mjs 57/57 통과. 새1개는 수정·삭제 문구/inline 중복 부재/수정 후 초점/연속 성공 타이머 갱신/자동제거 검증. 나머지56개는 등록토스트·댓글 조회/작성/수정/삭제·오류·초안·무JS·인증/이탈 회귀다. 실제 Spring/DB/실기기/화면낭독기는 이번에 검증하지 않았다. 로그/results는 별도 폴더에 보존했다.

기존8443 서버 보존. 직접 시작한8465 검증 서버만 종료. 실제 회원/댓글 데이터 변경 및 커밋·push 없음.
