# 운영 기록 메뉴 이동·패널 배치 후속 보완 (2026-11-09)

## 기준과 보존 범위
- HEAD: c72144ff34ccea6f6e86ad0dabac7417c7bd7275 / feat/admin-operation-log
- 시작 시 currentTarget 프론트 연동 등 기존 미커밋 변경이 있었습니다. 시작 소스 복사본 대비 task-only.patch로 이번 수정만 분리했습니다.
- 백엔드, DB, API, 공용 NavigationGuard/AppLink 및 게시글 관리 모달 기본 배치는 변경하지 않았습니다.

## 메뉴 이동 재현과 원인
실제 .admin-sidebar의 게시글 관리 링크를 Chrome에서 클릭했습니다. 빈 조치 폼은 이동했으나 사유 선택 후 입력 폐기를 승인하면 원래 운영 기록 URL에 남았습니다. history.pushState/replaceState 계측에서도 출발 화면 URL을 다시 replaceState하는 호출을 확인했습니다.

AdminOperationLogs가 NavigationGuard의 승인 버전을 곧바로 React key로 사용하여, 이동 승인 직후 아직 출발 URL인 상태에서 목록을 다시 마운트했습니다. 새 목록의 URL 동기화가 승인된 이동과 경쟁했습니다. 승인 즉시 요청 수명은 기존대로 무효화하되, 실제 pathname/search 변경이 관찰된 시점의 버전만 목록 재마운트에 사용하도록 변경했습니다. 출발 화면의 URL 동기화와 조회 시작에도 현재 pathname을 확인합니다.

운영 기록 전용 exitDialog 옵션으로 입력 유실 확인만 기존 OverlayDialog에 표시합니다. 콘텐츠 상세는 모달이 아닙니다. 빈 폼은 확인 없이 이동, 사유 입력은 계속 작성/입력 버리고 이동, 요청 중에는 진행 안내와 이동 제한을 적용합니다. 처리 중 안내는 요청 종료 후 닫고, 보류한 목적지로 자동 이동하지 않습니다. 기존 게시글 모달은 기본 inline 확인 구조를 유지합니다.

## 배치 및 스크롤
- 조치 전후 목록/상세의 2열 비율은 동일합니다. action 존재 여부에 따른 목록 축소·카드 전환·본문과 폼 2열 규칙을 제거했습니다.
- ResizeObserver로 사이드바와 여백을 제외한 작업 영역 content width를 측정합니다. 1100px 미만에서는 목록/상세 보기 전환을 사용합니다. 1100px는 뷰포트 해상도 기준이 아닙니다.
- 기본 상세에는 현재 본문 위, 사유 폼 아래 순서를 사용합니다. 과거 조치 시각·사유 요약을 유지하고 전체 사유를 펼칠 수 있습니다.
- 실제 폼/헤더/요약 높이를 측정하여 본문에 최소 240px의 읽기 공간을 확보할 수 있을 때만 본문 스크롤+아래 폼 배치를 사용합니다. 그렇지 않으면 패널 전체의 단일 스크롤로 전환합니다. 짧은 본문에 최소 높이는 강제하지 않습니다.
- 상세 넓게 보기는 사용자가 선택할 때만 목록을 감추고 상세를 확장합니다. DOM을 교체하지 않으며 사유/오류/선택을 유지합니다. 배치별 내부 스크롤을 렌더링 후 복원합니다.
- 좁은 화면의 목록으로 돌아가기는 선택/입력을 유지하는 배치 전환입니다. 상세 닫기는 기존 폐기 확인을 거쳐 선택을 해제합니다.
- 목록 복귀는 초기 목록 문서 위치와 행 포커스를 복원합니다. 좁은 화면의 빈 선택 패널을 제거하여 문서 높이 감소로 복원 위치가 잘리는 문제를 방지했습니다. 이 영역의 브라우저 scroll anchoring에 의한 추가 보정도 방지합니다.
- OTHER 표시로 고정 배치가 해제되거나 필드에 포커스가 들어올 때, 해당 필드가 잘린 경우 패널 내부만 최소한으로 이동합니다. 입력마다 focus를 재지정하지 않습니다.

## 변경 파일
- web/src/features/AdminOperationLogs.tsx: 승인/URL 수명 분리, 실제 가용 폭 분기, 상세 확장·목록 복귀·스크롤/포커스.
- web/src/components/admin/AdminLogPanel.tsx: 세로 조치 배치, 과거 요약, 측정 기반 읽기 공간, 배치별 스크롤 보존.
- web/src/components/admin/AdminModeration.tsx: 운영 기록 전용 유실 확인 대화상자 옵션, 처리 중 안내 정리, stacked workspace 옵션, 공통 필드 노출 함수 재사용.
- web/src/styles/admin-operation-logs.css: 충돌하는 action별 grid/card 규칙 제거 및 운영 기록에 한정한 스타일.
- web/tests/admin-log-panel-followup-browser.mjs: 신규 메뉴·배치 회귀.
- web/tests/admin-log-current-target-browser.mjs: 의도된 숨김 목록 대기, 세로 배치 및 새 확인 대화상자에 맞춘 검증. API/인증/조치 assertion은 유지합니다.
- 이 문서.

## 검증 실행 환경과 명령
Node 24 / pnpm 10.34.3 / production http://127.0.0.1:8479. 사용자 개발 서버 8443은 건드리지 않았습니다.

```sh
pnpm build
pnpm typecheck
pnpm lint
pnpm test
PLAYWRIGHT_MODULE=/private/tmp/iyum-master-detail/playwright-offline.mjs node tests/admin-log-panel-followup-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-master-detail/playwright-offline.mjs node tests/admin-log-current-target-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-master-detail/playwright-offline.mjs node tests/admin-operation-logs-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-master-detail/playwright-offline.mjs TEST_FILTER='comment list location|short viewport|short content post-list' node tests/admin-action-workspace-browser.mjs
BASE_URL=http://127.0.0.1:8479 pnpm test:smoke
```

브라우저 검증은 mock API와 Chrome을 사용하며 외부 폰트 요청을 차단합니다. 실제 운영 기록 관리자 세션·조치 API 통합 테스트가 아닙니다. 스모크의 공개 Spring GET/SSR 비교만 실제 로컬 백엔드를 읽기 전용으로 사용합니다.

최종 실행 결과와 측정 JSON, 화면 캡처는 /private/tmp/iyum-log-panel-followup/review에 정리합니다. 조사 중 실패 기록은 investigation으로 구분합니다. 이전 작업 통과 로그를 이번 결과로 재사용하지 않습니다.

## 제한
실제 관리자 계정으로 숨김/해제를 실행하지 않았습니다. 외부 글꼴과 다른 브라우저 엔진의 미세한 줄바꿈 차이는 검증하지 않았습니다. 과거 운영 기록 모달 전환을 기대하는 모든 역사적 브라우저 스크립트를 실행한 것은 아니며, 게시글 관리 공유 컴포넌트 회귀 6건을 선택 실행했습니다. 확대 시 화면의 가용 폭/높이로 자연스럽게 전환하지만 모든 OS 확대/접근성 조합까지 검증한 것은 아닙니다.

## 최종 실행 결과
- 신규 메뉴·배치 브라우저: 26/26 통과.
- currentTarget 브라우저: 10/10 통과.
- 운영 기록 전체 브라우저: 32/32 통과.
- 게시글 관리 공유 컴포넌트 선택 회귀: 6/6 통과. 총 74건, 모두 mock API 기반.
- 단위 테스트: 494/494 통과, 실패·건너뛰기 0.
- pnpm typecheck, pnpm build, pnpm test:smoke 통과.
- pnpm lint: 오류 0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고 3건.
- 1440×900에서 조치 전후 목록 너비 619.140625px, 표 형식 동일. 1280×720에서는 실제 작업 폭 기준으로 목록/상세 전환을 적용하므로 상세 화면에서 목록은 의도적으로 숨깁니다.
- 최종 7개 크기 모두 필드의 패널/뷰포트 내 위치, 본문 끝 접근, 스크롤 복원 검증을 통과했습니다.
- 1280×720 조사 중 실패는 필드 노출 함수가 패널 경계만 사용하고 뷰포트 경계를 고려하지 않던 문제였습니다. 가시 영역을 두 경계의 교집합으로 제한한 후 전체 관련 검증을 다시 실행했습니다.
