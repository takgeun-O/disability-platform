# 운영 기록 상세 패널 하단 접근·반응형 후속 보완

## 범위와 원인
기준 HEAD c72144ff34ccea6f6e86ad0dabac7417c7bd7275, feat/admin-operation-log. 기존 currentTarget·메뉴 이동 보완은 작업 시작 스냅샷에 포함되어 있으며 이번 diff와 구분한다. 백엔드/API와 게시글 관리 모달 디자인 및 NavigationGuard는 변경하지 않았다.

기존 패널은 `single`을 자동 단일 화면과 사용자 확대 화면에 공통 사용하면서 확대 버튼도 항상 표시했다. 자동 단일 화면에서 실제 폭이 변하지 않는 확대 동작이 남아 있었다. 이제 `compact`를 버튼의 명시적 판단 기준으로 사용한다.

1440×900에서 OTHER 표시 후 버튼이 화면 아래에 남는 상태를 재현했다. 재현 시 docked=false이고 패널 body는 clientHeight 747.5/scrollHeight 2136이었다. 실행 영역 bottom 934.25px, viewport 900px로 현재 스크롤에서는 보이지 않았다. 이 상태는 전체 패널 스크롤로 접근해야 하는 상태이며, 이전 캡처만으로 영구적인 폼 잘림이나 고정 모드 결함을 단정하지 않는다. 다만 기존 높이 계산은 상수 40px 등으로 박스 모델을 추정하므로 변경된 폼 크기와 정확히 맞는다는 보장이 없었다.

## 변경
- AdminLogPanel.tsx: compact 버튼 구분, 실제 패널 위치와 박스 모델 기반 읽기 공간 산출.
- AdminOperationLogs.tsx: 기존 관찰 대상에서 얻은 compact를 전달. 기존 URL 수명·메뉴 이동 수정 유지.
- admin-operation-logs.css: 패널 높이를 실제 가용 높이로 제한, 본문 고정 모드에서는 패널 body의 별도 스크롤을 차단.
- admin-log-panel-followup-browser.mjs: 새 버튼 정책 반영, 실제 경계 새 페이지 및 작성 중 연속 resize, 키보드 하단 접근·hit testing·textarea 실제 드래그·서버 오류 후 정상 제출.
- 이 문서.

## 모드와 상태
| 모드 | 버튼 |
|---|---|
| 넓은 화면 분할 | 상세 넓게 보기 / 상세 닫기 |
| 넓은 화면 사용자 확대 | 기본 배치로 / 상세 닫기 |
| 작업 폭 1100px 미만 | 목록으로 돌아가기 / 상세 닫기 |

compact가 확대 버튼보다 우선한다. 사용자 expanded 상태는 별도로 보존하며, 다시 넓어지면 이전 확대 선택을 복원한다. 목록 복귀는 detailView만 바꾸어 컴포넌트/입력/선택을 유지한다. 상세 닫기는 기존 requestExit와 폐기 확인을 사용한다. 크기 변경 자체는 요청 또는 이탈 확인을 만들지 않는다.

## 높이 산출 근거
가용 높이 = viewport 높이 - max(상단 여백 16px, 현재 패널 top) - 하단 여백 16px.
이 값에서 실제 패널 padding/border, 헤더 높이와 margin, body 시작부터 본문까지의 실제 거리(과거 사유·feedback 포함), body 하단 padding, 폼 높이, workspace gap/padding을 차감한다. 16px은 CSS sticky 여백, 240px은 유용한 본문 읽기 영역을 보장하는 디자인 최소 기준이며 추정한 헤더/폼 높이가 아니다.
남은 읽기 공간이 240px 이상이고 패널 폭이 400px 이상이며 과거 사유가 접힌 상태에서만 본문 스크롤을 사용한다. 그렇지 않으면 패널 전체가 스크롤된다. 짧은 본문에 최소 높이를 강제하지 않는다. ResizeObserver는 실제 form/header/history/workspace 크기를 관찰하므로 OTHER, 오류, textarea 드래그를 반영한다. 입력값마다 포커스를 재설정하지 않는다.

## 실제 경계
이번 Chrome/사이드바 기준 viewport 1337/1338/1339px에서 관찰하는 workspace content width는 1099/1100/1101px이었다. 1099만 compact=true, 1100/1101은 false다. 브라우저 1099/1101px를 경계라고 간주하던 검증을 교체했다. 같은 페이지에서 경계를 여섯 번 왕복하면서 선택·사유·오류·요청 수를 검사하고, 확대 상태에서 compact와 목록/상세 왕복도 검사했다.

## 실행
Node 24, pnpm 10.34.3, Chrome, 별도 production 포트8479. 사용자 서버8443은 변경하지 않았다.

```
pnpm build
pnpm typecheck
pnpm lint
pnpm test
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs MODAL_ARTIFACT_DIR=/private/tmp/iyum-panel-access/final-browser node tests/admin-log-panel-followup-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs node tests/admin-log-current-target-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs node tests/admin-operation-logs-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs TEST_FILTER='comment list location|short viewport|short content post-list' node tests/admin-action-workspace-browser.mjs
BASE_URL=http://127.0.0.1:8479 pnpm test:smoke
```

검증 도구는 저장소 의존성 변경 없이 임시 폴더에 설치했다. 외부 폰트 요청은 차단했다. 관리자 브라우저 검증은 mock API이고 실제 관리자 숨김/해제를 실행하지 않았다. 스모크의 공개 Spring GET/SSR 비교만 실제 서버를 읽기 전용으로 사용했다.

## 제한
OS/브라우저 자체 확대율을 바꾼 검증, Safari/Firefox 및 외부 글꼴은 수행하지 않았다. 낮은 viewport와 320px 검증을 실제 브라우저 zoom 검증으로 표현하지 않는다. 모든 역사적 브라우저 스크립트를 실행한 것은 아니며 공유 게시글 모달은 명시한 6개 회귀를 선택 실행했다.

## 자료
/private/tmp/iyum-panel-access/review에 이번 작업만의 task-only.patch, 작업 전후 git status, 최종 실행 로그, 실측 JSON 및 전후 캡처를 제공한다. 이전 테스트 통과 수는 이번 검증 수에 합산하지 않는다. 캡처의 모든 콘텐츠는 mock 예시다.

## 최종 결과
- 이번 실행: 패널 후속 30/30, currentTarget 10/10, 운영 기록 32/32, 게시글 모달 선택 회귀 6/6, 총 브라우저 78건 통과.
- 단위 494/494 통과. typecheck/build/smoke 통과. lint 오류 0, 기존 admin-action-workspace-browser 미사용 변수 경고 3건.
- 초기 브라우저 실패 중 하단 접근 실패는 macOS에서 Control+End가 기대한 패널 스크롤을 하지 않는 테스트 입력 문제였다. 실제 PageDown과 Tab으로 대체했으며 좌표·가림 검증을 유지했다.
- 최초 신규 검사 29건에 실제 경계 새 진입 사례를 보강하여 최종 30건으로 실행했다. 이전 세션 수치를 재사용하지 않았다.
