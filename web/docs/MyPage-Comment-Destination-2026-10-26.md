# 내 댓글 목적지와 이메일 인증 안내

## 변경
- MyComments: 원문 링크에 #comment-{commentId}를 추가. Next 기본 해시 점프 대신 Comments에서 부드러운 이동을 처리한다. 실제 링크·새 탭·from=/mypage 유지.
- Comments: 지정 댓글이 없으면 기존 커서를 사용해 추가 조회한다. DOM 준비 후 preventScroll 초점 지정과 smooth 이동. 모션 감소에서는 즉시 이동. 사용자 클릭/키보드 조작과 화면 이탈 시 이동 중단. 조회 오류는 기존 재시도 안내 유지; 목록 끝에도 대상이 없으면 삭제 가능성을 안내한다. 댓글 등록/수정/삭제 로직은 유지.
- MyPage/mypage.css: 정상 프로필 조회 후 이메일 인증 완료를 기존 청록색으로 표시한다. 백엔드 Member는 PENDING으로 가입하고 activeAfterEmailVerification에서 ACTIVE가 된다. MemberPrincipal은 ACTIVE만 enabled이며 MemberSessionStatusFilter가 ACTIVE와 인증 버전을 확인한다. 따라서 현재 로그인 정책에 근거한 표시다. 백엔드 변경 없음.
- community.css: 댓글의 키보드 초점을 표시한다.

## 이번 검증
- pnpm lint, pnpm exec tsc --noEmit, 별도 복사본 pnpm build: 통과.
- pnpm test: 351개 통과.
- member-comments-browser.mjs: 34개 통과. PC/모바일 3페이지 이후 정확한 댓글 초점, 삭제 댓글 안내, 이메일 문구, 기존 목록 복귀/CRUD/인증 경합 검사 포함.
- 실행 후 두 왕복 검사의 이름만 detail top에서 exact comment focus로 정정했다. 실행 로그의 이전 이름은 보존했다.
- git diff --check 통과.

모든 브라우저 검사는 모의 API 기반. 실제 DB/계정 변경 없음. 실기기 Safari, 실제 화면낭독기는 미검증. 기존 8443 서버는 유지하고 별도 8464 검증 서버만 정리했다. 기존 미커밋 작업 보존, 커밋/푸시 없음.
