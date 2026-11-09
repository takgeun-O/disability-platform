# 운영 기록 상세 높이·읽기 위치 후속 수정

## 작업 기준
feat/admin-operation-log / c72144ff34ccea6f6e86ad0dabac7417c7bd7275. 작업 시작 시 currentTarget, 메뉴 이동, 상세 패널 및 하단 접근성 변경이 미커밋 상태였다. 그 소스를 복사한 시작 스냅샷 대비 task-only.patch로 이번 변경만 구분한다. 백엔드/DB/API, CSS 디자인, 공용 NavigationGuard 및 게시글 관리 모달은 변경하지 않았다.

## 재현한 사실
1. 낮은 창(1440×550)에서 댓글 조치를 취소하거나 성공시킨 뒤 창을 1440×1080으로 확대했다. 수정 전 두 경로 모두 CSS 높이 제한과 실제 높이가 518px에 남았다. 이때 패널 top=307px로 가용 높이는 757px이었다. 조치가 끝나면 높이를 측정하는 effect가 반환하지만 panelHeight 상태/인라인 CSS가 유지되는 것이 원인이다.
2. 긴 본문을 휠로 중간까지 읽은 뒤 1920×1200(본문 스크롤)에서 1440×650(패널 스크롤)로 바꿨다. 수정 전 기준 구절 '문단 10:'은 top=1131.515625, bottom=1149.515625로 가시 범위 115.5~613px 밖이었다. 별도 본문 스크롤이 풀리면서 해당 요소의 scrollTop이 소실되고 바깥 패널에는 의미상 위치를 전달하지 않던 것이 원인이다.
3. 초기 프로덕션 재현 외에도 작업 전 AdminLogPanel 파일을 임시 복사 앱에서 독립 실행했다. 이 추가 재현은 webpack 개발 서버8481/테스트용 공개 API 주소/mock 응답을 사용했다. 원래 작업 트리나 사용자 서버를 되돌리지 않았다. 관련 환경 설정을 누락한 준비 실행은 검증 결과에 포함하지 않는다.
4. 초기 높이 assertion의 고정 증가량은 실제 패널 top 위치를 반영하지 못했다. 가용 화면 높이에 대한 검사로 수정했고, 그 정확한 검사도 작업 전 소스에서는 실패하는 것을 재확인했다. 입력·오류 resize 사례는 수정 전에도 통과했으며 기존 동작 보존 회귀로 분류한다.

## 수정
### 높이
AdminLogPanel의 기존 측정 effect를 일반 상세에도 유지한다. 조치용 form/history/workspace가 없어도 패널 위치와 viewport의 가용 높이는 계속 갱신한다. 조치 폼이 있을 때만 기존 박스 모델 기반 본문 공간 계산을 추가한다. 상태를 새로 늘리거나 상세 전체를 재마운트하지 않는다.

### 읽기 위치
조치 대상의 안전한 plain text DOM에서 현재 가시 영역의 첫 문자 위치(UTF-16 offset)와 상단 여백을 기억한다. supplementary-plane 문자의 중간 offset은 보정한다. 픽셀 scrollTop만 복사하지 않는다.

ResizeObserver가 패널/본문 크기와 실제 scroll mode 변화를 감지하면, 변경된 줄바꿈에서 같은 문자 범위의 실제 좌표를 구해 현재 스크롤 컨테이너를 이동한다. DOM 텍스트는 추가 분할·가공하거나 수정하지 않는다. 위치는 ref/effect 내부에만 보관하며 저장소나 URL에 쓰지 않는다.

스크롤 복원은 레이아웃 형태가 달라질 때만 예약하며 사용자의 일반 스크롤마다 실행하지 않는다. 크기가 변경되는 과정의 브라우저 자동 scroll 이벤트는 shape 검사로 이전 앵커를 덮어쓰지 않게 한다. 복원 직후라는 이유로 정상 사용자 스크롤까지 무시하는 잠금은 사용하지 않는다.

### 우선순위와 수명
활성 input/select/textarea가 있으면 본문 앵커 복원 대신 기존 revealModerationField를 사용한다. 필드와 aria-describedby 오류 영역을 함께 노출하며 focus나 입력값을 새로 지정하지 않는다. 제출 검증과 서버 오류의 기존 focus 로직은 변경하지 않는다. 조치 종료/대상 변경/컴포넌트 해제 시 observer·리스너·예약 frame을 정리한다. 기존 명시적 확대 및 목록 복귀 스크롤 보존 코드는 유지한다.

## 변경 파일
- web/src/components/admin/AdminLogPanel.tsx: 일반 상세 높이 측정과 문자 앵커 복원. 유일한 운영 코드 수정.
- web/tests/admin-log-panel-followup-browser.mjs: 취소/성공 높이, 일반 상세 본문 끝 접근, 양방향 구절 보존, resize 시 입력·오류 우선 및 정상 제출 테스트 4건 추가.
- 이 문서.

## 검증 명령
Node24/pnpm10.34.3, production http://127.0.0.1:8479, Chrome.
```
pnpm build
pnpm typecheck
pnpm lint
pnpm test
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs MODAL_ARTIFACT_DIR=/private/tmp/iyum-panel-anchor/final-browser node tests/admin-log-panel-followup-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs node tests/admin-log-current-target-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs node tests/admin-operation-logs-browser.mjs
PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/playwright.mjs TEST_FILTER='comment list location|short viewport|short content post-list' node tests/admin-action-workspace-browser.mjs
BASE_URL=http://127.0.0.1:8479 pnpm test:smoke
```

## 환경과 제한
관리자 브라우저 테스트는 모두 모의 API 기반이며 실제 관리자 계정/콘텐츠를 변경하지 않았다. 공개 Spring GET/SSR 스모크만 실제 서버를 읽기 전용으로 사용했다. 외부 폰트 요청은 차단했다. Safari/Firefox, 브라우저 자체 zoom, 모든 OS 보조기기 조합은 미검증이다. 문자 앵커는 현재 전체 본문이 하나의 Text 노드로 렌더링되는 구조를 기준으로 하며 향후 리치 텍스트 렌더러로 변경하면 별도 검토가 필요하다. 본문 밖 메타데이터나 과거 사유의 읽기 위치까지 문자 단위 복원하는 기능은 추가하지 않았다.

검토 ZIP에 시작 스냅샷 대비 patch, 관련 전체 소스, 실제 이번 실행 로그, baseline/final 구분 캡처 및 높이/문자 범위 JSON을 포함한다. 초기 조사·실패 결과는 최종 통과 로그와 분리한다. 이전 작업의 통과 수치는 합산하지 않는다.

## 최종 실행 결과
- 신규 4건을 포함한 패널 브라우저 34/34, currentTarget 10/10, 운영 기록 32/32, 공유 게시글 모달 선택 회귀 6/6: 이번 실행 총 82건 통과.
- 단위 테스트 494/494 통과. typecheck/build/smoke 통과. lint 오류 0, 기존 admin-action-workspace-browser 미사용 변수 경고 3건.
- 작업 전 소스에 대한 정확한 회귀 4건은 1건 통과/3건 실패(취소 높이·성공 높이·본문 구절). 실패는 의도한 결함 재현이며 최종 결과와 분리한다.
- 수정 후 1080px 창에서 패널 높이757px, 650px 창에서327px로 실제 위치에 맞춰 갱신됐다. 읽던 문단10은 본문 스크롤 top514.34 → 패널 스크롤 top436.52 → 본문 스크롤 top514.34로 세 경우 모두 가시 범위 안이었다. 정확한 각 실행 수치는 ZIP의 JSON을 기준으로 한다.
- 성공 후 버튼 이름은 화면 문구가 아닌 실제 aria-label인 ‘댓글 #900 숨김 해제’로 검증했다. 상태 검증을 삭제하거나 약화하지 않았다.
