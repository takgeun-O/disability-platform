# PC 정보 찾기 메가 메뉴 (2026-10-26)

브랜치 feat/community-post-like, HEAD 7b0fe586886c64bd45d17ced58e781385b9c6de8. 기존 좋아요/댓글 토스트 미커밋 변경 보존. 이번 변경: GlobalNav.tsx, styles/iyum.css, tests/member-menu-browser.mjs 및 이 기록.

사용자 첨부 시안의 넓은 펼침 패널/왼쪽 안내 블록/오른쪽2열4메뉴를 반영했다. 기존 청록·남색/로고/메뉴 순서 유지. 본문 위 absolute overlay로 페이지를 밀지 않는다. 이용 가이드는 실제 /validation/hearing-aid-health-insurance로 연결하고 보청기 지원 절차임을 명시. 기기/기관 준비 상태 유지. 모바일 메뉴와 무JS 공개 링크는 기존 구성 그대로다.

기존 현재목적지 판별(search/hash 포함), 수정키 새탭, Escape 초점복귀, 바깥클릭, NavigationGuard 유지. 1024/1440 배치·겹침·레이아웃 불변과 키보드 순서 검증 추가. 기존 복지 링크가 두 번째가 되어 키보드 테스트는 이용 가이드→복지 순서를 명시적으로 검증한다. 첫 실행 신규 바깥클릭 검사는 패널에 가려진 본문 제목을 클릭하려 해 실패해 실제 바깥 여백 클릭으로 수정했다. 기대값 완화나 제품 우회 없음. 초기 로그는 browser-initial.log로 구분.

이번 실행: pnpm lint/typecheck 통과, pnpm test 351/351, pnpm build(별도 임시 디렉터리) 통과, git diff --check 통과. member-menu-browser.mjs 전체23/23 모의 API 브라우저 통과: PC/모바일/무JS, 닉네임수정, 메뉴개폐·스크롤·초점복귀·현재페이지선택·이탈취소/승인·로그아웃 등. BASE_URL=http://127.0.0.1:8466, PLAYWRIGHT_MODULE은 설치된 Playwright index.mjs, MEMBER_ARTIFACT_DIR로 결과 저장.

별도8466 검증 서버만 종료, 기존8443 서버 보존. 계정/DB 실제변경 없음. 실기기 Safari/소프트키보드/실제 화면낭독기 미검증. 캡처는 합성 회원 데이터이며 information-panel-*에는 키보드 focus 상태도 포함한다. 커밋/push/배포하지 않음.
