# 관리자 게시글 모달 원문 왕복 후 닫기 — 2026-11-10

## 범위와 관찰

기준 브랜치 feat/admin-operation-log, HEAD c72144ff34ccea6f6e86ad0dabac7417c7bd7275. 기존 미커밋 관리자 UI/패널 작업을 유지했다. 백엔드, API, 공통 NavigationGuard, 운영 기록 패널 제품 코드는 수정하지 않았다.

Chrome + Next production + mock 관리자 API에서 실제 메뉴/행/원문 링크와 Back을 사용했다. history.state를 주입하거나 인덱스를 수정하지 않았다. 사용자 보고의 운영 기록으로 넘어가는 현상 자체는 재현되지 않았다. 특정 Next 개발 서버/HMR 상태에서 인덱스가 어긋났다는 가설은 확인되지 않았으므로 확정 원인으로 기술하지 않는다.

확인된 구조적 문제는 명시적인 닫기를 history.go(flow.index - index)로 구현한 것이다. 닫힐 목적지는 현재 목록 URL이 아닌 자체 번호 차이에 의존했다. 수정 전 실제 기록에서 목록 index=2, 모달=3, 원문=4, Back=3, 닫기=2였다. 새 요구사항인 '현재 이력에서 모달 선택만 해제' 검증이 실패했다. 새로고침/직접 진입 후 닫기의 행 포커스 복원도 실패했다. 수정 전 9건 중 3통과/6실패이며 이 중 4건은 명시적 닫기의 이력 이동, 2건은 포커스 문제다. 이 결과를 운영 기록 오이동 6건 재현으로 해석하면 안 된다.

## 수정

- AdminContentDialog: 검증·정규화한 listHref에서 selected/view/parent를 제거한 주소로 native replaceState. Next 자체 이력 필드는 직접 작성하지 않는다. 닫기는 history.go를 사용하지 않으며 새 방문 이력을 추가하지 않는다.
- flows에는 현재 문서에서 연 목록의 주소/스크롤만 보관한다. 과거 인덱스는 저장하거나 참조하지 않는다.
- useAdminDialogReturn: 실제 pathname/search 변경에도 복귀 작업을 수행한다. 준비된 목록의 대상 행 또는 목록 제목으로 preventScroll 포커스를 보내고 목록 스크롤을 복원한다. reload/direct entry도 같은 복귀 절차를 쓴다.
- admin-dialog-return/useAdminUrlQuery: 과거 baseHref로 되돌아간 뒤 URL을 보정하던 우회 경로를 제거했다. 실제 URL이 조회 조건의 기준이다.
- dirty/busy 승인은 기존 requestExit에 유지한다. Back/Forward의 공통 guard와 브라우저 이력은 수정하지 않았다.

수정 후 닫기 index=3 유지, selected만 제거, history.length 유지. Back은 기존 목록 index=2, Forward는 닫힌 목록 index=3이다. 이후 Forward로 원문을 방문했다 Back하면 selected 없는 목록이다. 이력의 URL에 selected가 있으면 모달을 복원한다. 기존 목록 항목 두 개가 연속할 수 있으나 닫기로 새 항목을 추가한 것은 아니다.

## 테스트

web에서 Node 24, pnpm 10.34.3 사용. 브라우저 공통 환경은 BASE_URL=http://127.0.0.1:8479, PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs (기존 Chrome Playwright 설치 및 외부 폰트 요청 차단 도우미). 아래 명령의 로그/결과 JSON은 검토 ZIP에 제공한다.

- node tests/admin-modal-return-browser.mjs: 11/11. 이전 로그 방문, 미방문, 조건/페이지/화면/API 일치, 반복 왕복, reload, direct URL, 행 부재 fallback, dirty close/back 취소·승인, 1440/320 긴 목록 위치.
- TEST_FILTER='post modal|history back|post action|modal 401|modal 403|closed modal|lazy comments|keyboard modal|inline failure|short post|deleted POST|modal closed then|live modal' node tests/admin-content-dialog-browser.mjs: 22/22. 관련 게시글 기능만 선택했으며 전체 스크립트 통과를 주장하지 않는다.
- node tests/admin-log-panel-followup-browser.mjs: 34/34. 메뉴/입력/요청 경합/반응형/높이/읽던 위치 보존.
- node tests/admin-history-browser.mjs: 13/13. 처음 실행에서는 기존 운영 기록 모달/모바일 목록 visibility 대기 2건 실패. 현재 패널이 표시되는지 검증하도록 바꾸고 URL·조건·결과·호출 수 assertion을 유지한 최종 실행 결과다.
- TEST_FILTER='corrected list page' node tests/admin-modal-followup-browser.mjs: 1/1. 페이지 보정 이후 명시적 close와 Back/Forward 모두 실제 URL에 일치. 이전 'whole-flow back' 기대를 현재 닫기 정책으로 교체했다.
- pnpm test: 494/494.
- pnpm typecheck: 성공.
- pnpm lint: 오류 0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고 3 (gate/close/label).
- pnpm build: 성공.
- BASE_URL=http://127.0.0.1:8479 pnpm test:smoke: 성공 (공개 페이지/SSR 기본 검사).

브라우저 81건은 이번 최종 제품 소스로 실행했다. 이전 작업의 통과 수를 합산하지 않았다. 관리자 인증과 관리자 API는 mock 기반이다. 실제 관리자 세션/운영 데이터 조치는 수행하지 않았다. 공개 Spring SSR 읽기 smoke 성공을 관리자 통합 검증으로 해석하지 않는다. Safari/Firefox와 사용자 8443 개발 서버의 HMR 이력 재현은 미검증이다.

## 로컬 수동 확인

1. 최신 프론트로 운영 기록의 임의 행을 선택하고 게시글 관리 메뉴로 이동한다.
2. 검색어·상태·기간·정렬·페이지를 적용하고 공개 열람 가능한 게시글을 연다.
3. 원문 보기 → 브라우저 Back → 닫기를 수행한다. 게시글 목록 조건/페이지와 행 포커스가 유지돼야 한다.
4. 원문 왕복을 반복하거나 Back 후 새로고침한 뒤 닫기도 확인한다.
5. 사유를 입력하고 닫기/Back을 시도해 계속 작성을 선택한다. 입력이 유지되어야 한다. 폐기 승인 시 요청 없이 목적지로 한 번만 이동한다.
6. 실제 운영 데이터의 숨김/해제는 이 확인에 필요하지 않다. 사용자 환경에서 여전히 다른 경로로 이동하면 각 단계 URL/Next 실행 모드/비밀값 없는 history 인덱스를 추가 수집한다.
