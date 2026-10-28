# 인라인 댓글 디자인 및 마이페이지 게시글 좋아요

브랜치 feat/mypage-activity-redesign, HEAD 416fabd176918ef32d2cb33798c603024ad57b02. 기존 미커밋 변경 보존. 시작본 /private/tmp/iyum-inline-design-start.

마이페이지 인라인 마지막 댓글 안내를 제거하고, 더 보기 종료 시 키보드 초점은 펼침 버튼으로 preventScroll 복귀한다. 상세 댓글 안내에는 적용하지 않았다.
게시글/댓글 사이에 비활성 textarea와 등록 버튼 시안 및 준비 중 설명을 배치했다. 실제 입력·등록·초안 기능은 없다. 간격과 들여쓰기, 마지막 구분선을 정리했다.
PostListReactions에 likeControl 슬롯을 추가하고 마이페이지에서 기존 PostLikeButton을 전달한다. 공유 상태·CSRF·중복 방지·확정/불확실 오류·로그인 처리 그대로 재사용. 공개 피드와 내 댓글 탭의 읽기 전용 표시는 유지한다. 마이페이지 좋아요는 서버 liked=true일 때 빨간 채움이다.
변경: InlineComments.tsx, PostListReactions.tsx, mypage.css, inline-comments-browser.mjs, post-reactions-navigation-browser.mjs.

검증: pnpm test 409/409, pnpm lint, pnpm typecheck, pnpm build, git diff --check 통과. 격리 프로덕션 빌드 src와 최종 src 바이트 일치. Node24/pnpm10.34.3.
모의 브라우저 inline-comments-browser 27/27, post-reactions-navigation-browser 10/10. 1440/390/320 배치·키보드·빈 상태·접기복원·기존 재조회 경합 및 새 게시글 PUT/DELETE/서버 개수/빨간 하트 검증. 작성 시안에서 댓글 POST 없음. 외부 폰트 CSS는 이전 검사와 같은 Playwright wrapper로 공급.
실제 Spring/사용자 데이터 변경 및 실기기 Safari/화면낭독기 검증은 미실행. 기존 상세 댓글 CRUD 전체는 이번에 재실행하지 않았으며, 이전 로그아웃 실패 후 스크롤 assertion 실패를 해결했다고 주장하지 않는다.
사용자 서버8443 보존. 직접 시작한8467 종료. 백엔드 수정/커밋/푸시 없음.
