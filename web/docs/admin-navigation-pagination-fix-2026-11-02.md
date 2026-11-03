# 관리자 동일 경로 탐색 및 페이지 포커스 보완

## 작업 기준
- 브랜치 feat/admin-content-moderation, HEAD 71969fc67be13d40bc3c80ad75d7273c9cc33646.
- 관리자 1단계·디자인 보완의 미커밋 작업이 존재했다. 시작 src/tests를 별도 임시 폴더에 보존하고 이번 차이만 산출했다.
- 백엔드·DB·역할·계정·API는 변경하지 않았다. 인증정보를 수집하거나 실제 계정으로 로그인하지 않았다. 커밋/푸시/병합 없음.

## 실제 재현
프로덕션 Next 8479 + 브라우저 route mock으로 수정 전에 실행했다. 관리자 15개 검사 중 12 통과, 3 실패.
1. 로딩 중 사이드바 재클릭: 정상 API 응답을 해제해도 aria-busy=false가 되지 않아 실패.
2. 로딩 중 데스크톱 헤더 관리자 재클릭: 동일하게 실패.
3. 페이지 입력 후 Enter 및 정상 응답 완료: 입력창 포커스 소실.
모바일 동일 메뉴 검사는 수정 전에도 통과했다. GlobalNav의 현재 메뉴 처리 경로가 있으므로 모바일까지 재현됐다고 주장하지 않는다.

원인: AppLink는 onNavigate가 취소되지 않으면 NavigationGuard.notifyNavigation을 호출한다. 같은 경로에서 컴포넌트가 유지되면 navigation-request의 screenVersion은 이전 값인 채 새 탐색 버전과 불일치하며 다음 요청도 무효가 된다. 페이지 포커스는 로딩 분기에서 페이지 영역을 제거하고 페이지별 key로 다시 만드는 구조 때문에 사라졌다.

## 변경 파일과 책임
- web/src/components/admin/AdminPostsLink.tsx (신규): 사이드바 전용 클라이언트 링크. pathname이 /admin/posts일 때 onNavigate.preventDefault. 실제 다른 경로 이동은 AppLink 정책 유지.
- web/src/app/admin/layout.tsx: 사이드바에서 해당 링크 사용.
- web/src/components/AuthControls.tsx: 헤더 관리자 동일 경로 클릭을 취소하고 모바일 onCurrentPage 콜백 유지.
- web/src/features/AdminPosts.tsx: 마지막 정상 페이지 정보를 로딩/일반 오류 동안 보관해 pagination을 조건부 목록 밖에서 유지. 요청 query와 retry attempt를 구분. 403은 보관 데이터도 제거하고 401/인증 변경은 기존 인증 흐름 및 AdminAccess로 정리.
- web/src/components/admin/AdminPagination.tsx: key 재생성 제거, 안정된 입력·버튼 DOM. 로딩/오류 시 aria-disabled와 핸들러 검사로 추가 이동 차단, 입력은 readOnly로 포커스 유지. 수락된 서버 응답이 바뀔 때 입력 페이지 값 갱신. 현재 포커스가 영역 내 경계 버튼일 때만 현재 페이지 status로 이동하며 검색창 등 외부 포커스는 건드리지 않음. 현재 페이지/로딩/오류 안내 제공.
- web/src/styles/admin.css: aria-disabled 페이지 버튼을 기존 비활성 스타일로 표시.
- web/tests/admin-posts-browser.mjs: 새 회귀 6개 추가. 기존 인증/401/403/검색/레이아웃 검증 유지.
- 본 보고서.

AppLink, NavigationGuard, navigation-request는 수정하지 않았다. onNavigate는 수정 키/새 탭 클릭에서 실행되지 않는 Next 동작을 유지한다. 요청의 인증 세대·화면 소유권·AbortController 검사를 삭제하지 않았다.

## 테스트 범위
수정 전 추가한 4개: 사이드바/헤더/모바일 로딩 중 및 완료 후 재클릭, 검색/필터/페이지 이동; 입력 Enter 후 포커스.
수정 후 추가한 2개: 다음/이전 버튼 유지, 처음/마지막 경계 대체 포커스, 요청 대기 중 검색창으로 이동, 오류 상태 동일 메뉴 재클릭+키보드 재시도; 실제 홈 이동 후 늦은 응답 차단 및 Meta 클릭 새 탭.
입력 요청을 gate로 보류한 동안 Enter를 반복해도 추가 GET이 없음을 확인한다. 경계에서는 현재 페이지 status에 포커스가 있고, 입력 이동에서는 응답 후에도 입력창과 최신 값이 유지된다. 일반 오류에서는 초점과 입력을 유지하고 재시도 가능하다. 화면 이탈·로그아웃·같은 회원 재로그인·다른 USER 로그인 이후 이전 응답 격리 검증도 유지했다.
고정 sleep, assertion 삭제, 허용 오차 확대 없음. 기존 팝오버 위치 테스트의 오차는 변경하지 않았다.

## 최종 실제 실행
Node 24 / pnpm 10.34.3. 사용자 8443 서버는 유지. 소스 복사본 /private/tmp/iyum-admin-web에서 프로덕션 빌드·타입 검사, 별도 8479 서버에서 브라우저 검사.

| 실행 | 결과 |
|---|---|
| pnpm test | 426/426, skip 0 |
| pnpm lint | 통과 |
| NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 pnpm build | 통과 |
| pnpm typecheck | 통과 |
| node tests/admin-posts-browser.mjs | 수정 전 12/15(위 3건 실패), 수정 후 17/17 |
| node tests/member-browser.mjs | 25/25 |
| node tests/member-menu-browser.mjs | 23/23 |
| git diff --check | 통과 |

브라우저 공통 환경: BASE_URL=http://127.0.0.1:8479, PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs. ADMIN_ARTIFACT_DIR 또는 MEMBER_ARTIFACT_DIR는 /private/tmp/iyum-admin-followup 하위로 지정. Chromium channel=chrome. 1440/390/320 캡처 포함.

## 한계 및 재확인
실제 Spring 관리자 세션·실기기 Safari·화면낭독기는 미검증이다. 브라우저 API는 mock이고 계정/DB 쓰기는 없었다. 이번 범위의 최종 실패 없음. 유형 필터·정렬·상세·숨김/복원 UI는 구현하지 않았다.
수동 확인: 로딩 중 및 완료 후 두 관리자 링크 재클릭 → 조건 유지 확인. 페이지 번호 Enter → 입력 포커스 유지. 다음/이전 반복 → 경계에서는 페이지 안내 포커스. 응답 대기 중 검색 입력으로 이동 → 응답 후에도 검색 포커스 유지. 조회 실패 → 재시도. 로그아웃 → 관리자 데이터 제거.
