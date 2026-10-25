# 마이페이지 배치·댓글 구분 개선 (2026-10-26)

브랜치 feat/community-post-like, HEAD 7b0fe586886c64bd45d17ced58e781385b9c6de8. 기존 미커밋 좋아요·토스트·정보 메뉴·404 검사 수정 보존. 커밋/push/배포 없음.

이번 제품 변경: features/MyPage.tsx, components/member/MyComments.tsx, styles/mypage.css.
PC는 최대1080px에서 활동 minmax(0,1fr) + 계정관리280px, 간격32px. 활동 항목의 반응 표시를 아래로 내려 본문을 좁히지 않는다. 계정 관리는 sticky top24px으로 긴 목록에서도 접근 가능하다. 900px 이하는 한 열이며 계정 관리가 내 활동 앞에 위치한다. DOM도 계정→활동 순서로 모바일 시각 순서와 일치한다. 프로필·모달·계정 처리 로직은 유지한다.
내 댓글은 '내가 쓴 댓글' 라벨+연한 배경의 내용 영역, 아래 '원문 게시글' 라벨+실제 원문 제목 링크로 구분한다. 댓글을 제목처럼 굵게 표시하던 강도를 낮췄다. 커서/정렬/조회/인증/API/개인캐시/복원 함수는 바꾸지 않았다.

검증: lint/typecheck/build(격리 디렉터리) 통과, 단위351/351, git diff --check 통과. member-comments-browser 전체31/31, member-posts-browser 전체41/41 통과. 새 배치 검사1개는 댓글27개 누적 후1440/1024의 양옆 배치 및 스크롤 후 계정 관리 가시성,390/320의 계정→활동 순서,가로 넘침 없음,댓글/원문 라벨을 확인한다. 기존 더 보기/실패/키보드/상세·404복귀/계정전환 회귀 유지.

mock API만 사용, 실제 DB/계정 변경 없음. 실기기 Safari·소프트키보드·화면낭독기는 미검증. 기존8443 서버 보존, 직접 실행한8464 및 mock8858 서버만 정리했다. 캡처는 합성 계정 데이터. 실행: Node24/pnpm10.34.3, BASE_URL=http://127.0.0.1:8464, MEMBER_ARTIFACT_DIR 지정, PLAYWRIGHT_MODULE=설치된Playwright index.mjs; 두 suite를 순차실행. 별도 Next는 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 설정.
