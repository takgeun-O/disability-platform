# 관리자 화면 후속 보완

기준: feat/admin-content-moderation / 71969fc67be13d40bc3c80ad75d7273c9cc33646.
기존 관리자 1단계 미커밋 작업을 보존했다. 백엔드·DB 변경, 커밋·푸시 없음.

- AdminAccess: 비로그인/권한 오류 안내를 흰색 카드와 명확한 로그인·서비스 복귀 동작으로 정리.
- WrittenTime: 기본 중앙 팝오버는 기존대로 유지. 관리자에서만 anchored 옵션으로 해당 시각 근처에 배치하고 화면 경계 보정, 스크롤·리사이즈 시 닫음. Escape/외부 클릭은 기존 native popover 동작 유지.
- AdminPagination: 서버 페이지 정보를 사용해 맨 처음/이전/다음/맨 마지막, 번호 입력+Enter/이동 제공. 범위 밖·소수 등 입력 거절, 빈 목록은 이동 불가. 인증/요청 소유권 검사 유지.
- AdminPosts/admin.css: 처리 상태 레이블과 반응형 배치.
- admin-posts-browser: 페이지 번호 검증 및 팝오버 실제 위치 검사 추가, 변경 레이블 반영.

## 유형 필터 제약
AdminPostReadController와 AdminPostReadQuery는 status/keyword/page/size만 지원한다. 유형별 정확한 전체 개수·페이지를 얻으려면 서버 type 필터(예: GENERAL/QUESTION/REVIEW) 계약이 먼저 필요하다. 현재 20개만 클라이언트 필터링하거나 작동하지 않는 유형 선택기를 추가하지 않았다. 처리 상태는 기존 ALL/VISIBLE/HIDDEN/DELETED와 연결되어 있다.

## 실제 검증
- pnpm test: 426/426
- pnpm lint: 통과
- pnpm build 및 pnpm typecheck: 격리 복사본 /private/tmp/iyum-admin-web에서 통과. 빌드 시 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 지정.
- admin-posts-browser.mjs: 11/11 (모의 API). 직접 접근/권한/검색/페이지/늦은 응답/같은 회원 재로그인/다른 계정/1440·390·320 레이아웃 포함.
- 추가 비로그인 카드 1440·320 화면 캡처와 가로 넘침 검사.
- git diff --check 통과.
- 증거: /private/tmp/iyum-admin-polish-browser/, /private/tmp/iyum-admin-polish-*.log.
- 실제 Spring 연동, Safari 실기기, 공통 작성 시각의 기존 전체 브라우저 테스트는 이번에 미실행. 작성 시각 옵션의 기본 동작은 변경하지 않았다.
- 사용자 8443 서버는 유지하고, 직접 실행한 검증용 8479 서버만 종료.
