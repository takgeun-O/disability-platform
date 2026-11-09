# 관리자 탐색 상태 복원 및 필터 디자인 — 2026-11-08

## 기준 및 범위

- 브랜치: feat/admin-operation-log
- 시작 HEAD: d9decf224a46453c4e7e188a0299bc7096c44eb7
- 이전 날짜·정렬 프론트 작업이 미커밋 상태였다. 이를 보존하고 그 시작 스냅샷 대비 이번 변경만 task-only.patch로 분리했다.
- 백엔드·DB·API 계약, 공용 NavigationGuard/AppLink/AuthProvider는 수정하지 않았다. 커밋·푸시·병합·배포 없음.

## 재현 및 원인

수정 전 새 브라우저 테스트로 두 화면에서 실제 상세 링크 → page.goBack()을 실행했다. 게시글은 적용된 상태/검색어/날짜/OLDEST/page=1 URL이 기본 전체/LATEST/page=0으로 바뀌었다. 운영 기록은 복귀 후 날짜 입력이 비어 있었다. 두 테스트 모두 탐색 복원 assertion까지 도달해 실패했다. verification/before에 URL·필터·결과 JSON, 로그, 캡처를 보존했다.

게시글은 replaceState(window.history.state, ...)로 Next 내부 __NA 상태를 다시 전달했다. 설치된 Next app-router의 native history 패치는 __NA/_N이 있는 호출을 내부 라우터 호출로 보고 URL 동기화를 건너뛴다. 주소 표시줄은 바뀌지만 라우터가 보관한 searchParams와 서버 initialQuery는 기본값으로 남고, 복귀 시 초기값을 쓰는 effect가 URL까지 덮어썼다. 운영 기록은 적용 조건을 로컬 state에만 저장했고 URL에 기록하지 않았다.

## 수정 방식

- 두 화면 모두 useSearchParams로 현재 URL의 적용 조건을 해석한다. 라우트 진입점은 Suspense 경계를 둔다.
- 필터 적용은 replaceState(null, '', href). Next가 자체 이력 상태를 복사하고 기존 NavigationGuard가 자신의 인덱스를 유지한다. 내부 플래그를 임의 복제하거나 브라우저 Back을 별도로 가로채지 않는다.
- useAdminUrlQuery는 기존 NavigationGuard의 탐색 수명 값을 재사용한다. 실제 화면 이동 때만 결과 컴포넌트가 URL 조건으로 초기화되고, 같은 화면에서 native replace로 조건을 적용할 때는 미적용 검색어·날짜 입력이 유지된다.
- 게시글/운영 기록 각각의 URL 해석·생성 함수를 사용한다. URL에는 조건만 포함하며 응답 본문/회원/세션은 저장하지 않는다. 잘못된 enum·날짜·페이지는 기존 안전한 기본값으로 정규화한다.
- 복귀 시 page를 보존한다. 범위를 벗어난 서버 페이지의 기존 1회 보정, 인증/탐색/요청 세대 검증, 마지막 성공 목록 유지와 조작 잠금은 보존했다.
- 운영 기록에서 상세로 이동 후 Back은 운영 기록 조건을 복원한다. 상세의 '목록으로'는 기존 게시글 목록 복귀 경로를 유지하며 두 검색 조건을 혼합하지 않는다.

## 디자인

공유 AdminDateRange에 직접 입력 + 내부 달력 아이콘 + 값이 있을 때 지우기 버튼을 배치했다. 데스크톱 입력은 과도하게 늘리지 않고 모바일에서는 세로로 배치한다. 기간 적용은 그룹 옆, 빠른 기간은 작은 칩으로 표시하고 실제 적용 범위와 일치할 때 선택 상태(aria-pressed)를 표시한다.

적용 기간은 전체 기간 / YYYY.MM.DD부터 / YYYY.MM.DD까지 / YYYY.MM.DD – YYYY.MM.DD로 요약한다. 미적용 안내는 '변경한 기간을 적용해 주세요'. 날짜 오류는 입력 바로 아래의 작은 아이콘·텍스트와 입력 테두리로 표시하며 aria-invalid/aria-describedby를 유지한다. 전체 초기화는 상태 전용 행에서 분리하고 회전 화살표와 텍스트로 표시한다. 결과 수와 정렬 도구 행은 유지했다.

1440px 및 320px 정상·오류·달력 화면을 캡처하고 확인했다. 달력 월/연도·방향키/Enter·Escape·포커스 복귀와 페이지 가로 넘침을 검증했다. 표의 내부 스크롤은 유지한다. 큰 고정 높이나 자동 스크롤은 추가하지 않았다.

## 변경 파일별 역할

- web/src/lib/useAdminUrlQuery.ts (신규): URL 읽기와 기존 탐색 수명 연결.
- web/src/lib/admin-navigation.ts: 운영 기록 조건 해석/안전한 URL 생성.
- web/src/features/AdminPosts.tsx, AdminOperationLogs.tsx: URL 기반 진입 복원 및 유효 화면에서의 이력 교체, 전체 초기화 위치.
- web/src/app/admin/posts/page.tsx, operation-logs/page.tsx: 공통 client URL 복원용 Suspense 경계.
- web/src/components/admin/AdminDateRange.tsx: 공유 기간 컨트롤·오류·선택 표시.
- web/src/lib/admin-dates.ts: 오류 문구와 기간 요약.
- web/src/styles/admin.css: 관리자 범위의 간결한 날짜/초기화 스타일.
- web/tests/admin-history.test.mjs (신규): 조건 왕복·잘못된 값·기간 표시 3건.
- web/tests/admin-history-browser.mjs (신규): 실제 이력 복원 및 보안/경합 12건.
- web/tests/admin-operation-logs-browser.mjs, helpers/admin-date-scenarios.mjs: 새 문구·조건부 지우기 버튼 키보드 순서·장식 아이콘을 제외한 정확한 오류 문구 검증. 기존 assertion을 제거하지 않았다.

## 검증 방법

Node 24, pnpm 10.34.3. web에서 pnpm build 후 별도 production 서버 http://127.0.0.1:8479 사용. 사용자 개발 서버 8443은 보존했다.

브라우저 환경:
```
PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs
BASE_URL=http://127.0.0.1:8479
```
각 suite는 ADMIN_ARTIFACT_DIR, OPERATION_ARTIFACT_DIR, ADMIN_DETAIL_ARTIFACT_DIR, ADMIN_MODERATION_ARTIFACT_DIR, HISTORY_ARTIFACT_DIR로 독립 결과 경로를 사용했다.

새 이력 테스트는 날짜·정렬·페이지·검색 조건에 따라 다른 mock 본문을 반환한다. 검색/상태/날짜/정렬/page=1 적용 → 실제 상세 링크 → Back → Forward/Back → reload → 직접 URL을 검증한다. 게시글은 화면 내 목록 링크도 확인한다. URL·입력·페이지·실제 요청·표시 결과가 일치하고 복귀 때 기본 요청 없이 1회 조회되는지 확인한다. 미적용 입력은 URL에 섞이지 않는다.

추가로 잘못된 URL, 로그아웃/401 이후 Back의 관리자 데이터 제거, abort를 무시하고 완료되는 오래된 조회 응답을 검증한다. 지연은 gate로 제어하고 파싱 완료 및 UI 렌더 이후 상태를 확인한다. 고정 긴 sleep/타임아웃 확대 없음.

## 최종 실행 결과

| 명령 (web/) | 결과 |
|---|---|
| pnpm test | 490/490 통과 |
| pnpm lint | 통과 |
| pnpm typecheck | 통과 |
| pnpm build | 통과 |
| node tests/admin-history-browser.mjs | 12/12 통과 |
| node tests/admin-posts-browser.mjs | 32/32 통과 |
| node tests/admin-operation-logs-browser.mjs | 32/32 통과 |
| node tests/admin-detail-browser.mjs | 49/49 통과 |
| node tests/admin-moderation-browser.mjs | 31/31 통과 |
| git diff --check | 통과 |

중간 실행에서 새 오류 아이콘의 '!'까지 포함한 textContent 때문에 이전 exact text 선택자가 오류를 찾지 못했다. aria-hidden 장식만 제외한 실제 문구의 정확한 일치와 aria-invalid를 검증하도록 테스트를 보완했다. 초기 TypeScript lib 대상에서 replaceAll이 지원되지 않아 정규식 replace로 변경한 뒤 빌드/타입 검사가 통과했다. 중간 브라우저 실패 로그는 verification/intermediate에 분리했다.

## 한계 및 수동 확인

관리자 브라우저 검증은 모두 **mock API**이며 실제 Spring 로그인·관리자 데이터 연동은 실행하지 않았다. 기존 사용자 계정/역할/실제 콘텐츠를 변경하지 않았다. 일반 사용자 전체 브라우저 suite 및 실제 Spring을 요구하는 production smoke는 이번 범위에서 실행하지 않았다.

실서버에서는 관리자로 두 화면에서 조건·2페이지를 적용하고 상세로 이동한 뒤 브라우저 Back/Forward, 새로고침, URL 새 탭 진입으로 조건·요청·결과 일치를 확인하면 된다. 미적용 입력은 화면을 떠났다 돌아오면 적용 조건으로 복원되는 정책이다. 이 자료는 검토용 부분 소스이며 전체 저장소 복제본은 아니다.

## ZIP 검토 순서

1. README_REVIEW.md (본 문서)
2. review/task-only.patch 및 task-files.txt: 작업 시작 스냅샷 대비 이번 보완만.
3. review/pre-existing-tracked.patch, pre-existing-untracked/: 작업 전 변경과 당시 신규 파일 원본. 과거 로그는 최종 결과로 재사용하지 않음.
4. review/cumulative-from-head.patch: 기존 날짜/정렬 작업까지 포함한 누적 변경, 이번 diff와 구분.
5. verification/before: 수정 전 실패. verification/intermediate: 수정 중 테스트 선택자 실패. verification/final: 최종 로그·JSON·캡처.
6. verification/final/browser/*-history.json: 뒤로가기 전후 URL·필터·요청·실제 mock 결과 증거.
7. FILES.txt / SHA256SUMS.txt: 포함 파일과 무결성 목록.

.env, 실제 인증정보/토큰/쿠키/개인정보, node_modules, .next, .git, 빌드 산출물, 이전 ZIP은 제외한다. 테스트의 example.test 회원과 고정 CSRF 값은 실행용 가상 fixture다. 이전 Downloads ZIP은 보존한다.
