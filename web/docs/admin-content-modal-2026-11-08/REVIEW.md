# 관리자 콘텐츠 모달 전환 검토

## 범위와 기준
- 기준 HEAD: bbacb9d2ce73fe0869f1263032b455e5bbfb828a / feat/admin-operation-log.
- 기존 댓글 단건/패널 연동 미커밋 작업 위에 적용. `review/start-status.txt`, `existing.patch`, `pre-existing-untracked/`가 시작 상태이고 `task-only.patch`는 그 상태 대비 변경이다. `cumulative-from-head.patch`는 기존 작업을 포함한다.
- 백엔드/DB/API 변경, 계정 변경, 커밋/푸시/배포 없음.

## 구현
- AdminPosts/AdminOperationLogs의 목록은 전체 폭으로 표시한다. 일반 행/제목 클릭 및 키보드 선택은 넓은 모달을 연다. 제목 앵커의 수정키/가운데 클릭은 기존 정식 상세 URL을 유지한다. 작성 시각은 비탐색 time 요소이다.
- AdminContentDialog: OverlayDialog를 재사용해 고정 헤더, 단일 본문 스크롤, 배경 잠금, 키보드 포커스 제한/복귀를 제공한다. 320px에서는 가장자리 8px을 남긴다. 모달 자체는 overflow:clip으로 프로그램 스크롤도 막고, 댓글 바로가기는 본문 스크롤 컨테이너만 이동한다.
- 게시글 본문 → 상세 정보 → 대상 조치 → 댓글 순서. 댓글은 IntersectionObserver(본문 스크롤 root, 여유 160px) 또는 명시적 바로가기로 최초 로드한다. 짧은 글은 보이는 댓글을 즉시 로드하고, 목록 각 행의 단건 GET은 추가하지 않는다. 다른 게시글은 새 key로 조건/스크롤을 초기화한다.
- 첫 댓글 결과 렌더링 후 바로가기 위치를 보정한다. 사용자가 다른 곳으로 포커스를 옮겼으면 보정하지 않는다. 이후 검색/페이지 재조회에는 이동하지 않는다.
- AdminModeration의 기존 CSRF/검증/중복 실행/불확실 결과 처리를 유지하며 inline 옵션으로 대상 근처 사유 폼을 표시한다. 사유 입력 중 닫기/Back/관리 전환은 같은 모달의 확인 단계에서 처리한다. 처리 중 이탈/재실행을 차단한다. 불확실 결과에는 자동 POST 반복 없이 기존 최신 상태 확인 경로를 제공한다.
- 운영 기록은 읽기 전용. 저장된 reason/LEGACY를 유지하고 정확히 동일한 유형/상세 문구만 중복 표시하지 않는다. 현재 콘텐츠 안내는 한 번 표시한다. 현재 게시글/댓글 관리 및 부모 게시글 관리는 같은 모달 내부에서 전환한다.
- COMMENT는 `/api/v1/admin/comments/{commentId}` 응답의 postId를 사용한다. 부모 관리 화면 직접 URL도 댓글 단건을 먼저 확인하며 URL로 부모 ID를 추측하지 않는다. 부모의 특정 댓글 페이지를 찾기 위해 전체 페이지를 순회하지 않는다.
- 게시글 updatedAt 라벨은 기존 상세 페이지와 모달 모두 ‘최종 변경’. 댓글 contentUpdatedAt은 ‘본문 수정/기록 없음’, 모든 관리 시각은 Asia/Seoul.

## URL과 수명
- 기존 목록 query와 `selected`를 사용하고 운영 기록 내부 화면만 `view=record|manage|parent`로 구분한다. 목록 API에는 선택/화면 상태를 보내지 않는다.
- 네이티브 pushState/replaceState와 기존 NavigationGuard를 사용한다. Next 소유 상태를 직접 복제하지 않는다. 설치된 Next의 native history wrapper는 __NA가 포함된 상태를 내부 호출로 간주하므로 이를 넘기면 useSearchParams가 갱신되지 않는 문제를 확인하고 제거했다.
- Back/Forward는 기존 이력으로 화면을 복원한다. 닫기는 검증된 현재 목록 주소로 replace하여 내부 단계를 여러 번 거쳤어도 한 번에 목록으로 돌아간다. 직접 진입에서도 이전 이력을 가정하지 않는다. 명시적으로 닫은 후 과거 내부 화면 이력 자체를 삭제하지는 않으므로 이후 Back으로 과거 화면을 다시 방문할 수 있다.
- 조치 후 필터에서 행이 사라져도 열린 관리 모달은 최신 상태를 표시한다. 운영 기록의 원본 snapshot은 인증 회원/세션 수명의 메모리 map에만 유지한다. 권한 상실/로그아웃 시 제거하며 저장소에는 기록하지 않는다.
- GET 요청 세대/화면/인증 소유권과 조치 수명을 분리했다. 목록 조건이 바뀌거나 조회가 취소되어도 이미 전송한 POST를 취소됐다고 가정하지 않는다. 닫힌 모달/다른 계정의 응답은 적용하지 않는다.

## 최종 검증
Node 24, pnpm 10.34.3, 생산 빌드 Next 서버 127.0.0.1:8479, 로컬 Chrome 사용. 사용자 개발 서버 8443은 유지했다.

- pnpm lint / pnpm typecheck / pnpm test (493개) / pnpm build.
- admin-content-dialog-browser.mjs: 30개. 긴 20행, 1440/320px, 실제 화면 좌표/헤더/스크롤/포커스, 댓글 지연/페이지/필터, 사유/changed=false/삭제 제한, 401/403/404/CSRF/손상 응답, 취소를 무시한 지연 응답, 로그 snapshot 보존, 같은 모달 내부 히스토리.
- admin-posts-browser.mjs 32개, admin-detail-browser.mjs 49개, admin-operation-logs-browser.mjs 32개, admin-moderation-browser.mjs 31개, admin-history-browser.mjs 13개.
- 공통 OverlayDialog 회귀: member-success-toast-browser.mjs, auth-ui-browser.mjs의 mock 모드. 최종 개수와 통과 결과는 verification/final 로그 및 results-summary.json 참고.
- pnpm test:smoke: /community 현재 문구로 기대값만 수정했다. 공개 본문 초기 HTML/metadata/잘못된 경로404/실제 Spring 공개 게시글 조회 검증은 유지하고 끝까지 실행했다.
- 관리자 브라우저 테스트는 전부 mock API. 실제 관리자 세션 및 Spring 변경 요청을 실행하지 않았다. smoke의 실제 Spring 검증은 공개 읽기 API에 한정한다.
- 오프라인 Playwright wrapper는 로컬 설치 경로를 사용하며 외부 Google fonts 요청만 차단한다. 캡처는 fallback 폰트일 수 있다. ZIP의 wrapper를 재사용하려면 설치 경로를 본인 환경에 맞춰야 하며 일반 환경에서는 PLAYWRIGHT_MODULE=playwright 사용 가능.

## 테스트 조정과 제한
- 과거 우측 패널/탭 전용 admin-master-detail-browser.mjs, admin-panel-scroll-browser.mjs, admin-comment-panel-browser.mjs는 이번 최종 실행 대상이 아니다. 해당 UI가 제거되어 기존 선택자와 기대 구조를 그대로 실행하면 호환되지 않는다. 새 30개 모달 테스트와 기존 목록/정식 상세/조치/히스토리 검증을 수행했으며 구형 스크립트가 통과했다고 주장하지 않는다.
- 기존 조치 회귀의 실제 문서 이동 후 요청은 브라우저가 이전 document를 중지하여 requestfinished가 보장되지 않았다. Mock route 응답 해제/전달 완료와 새 화면 상태를 검증하도록 수정하고, 동일 document에서 CSRF/POST를 보류한 채 인증 상실→재로그인 후 실제 응답 완료까지 검증하는 새 사례를 별도로 추가했다. 단순 abort 확인으로 종료하지 않는다.
- 새 CSRF 경합 fixture가 재로그인 CSRF까지 막던 초기 실패는 최초 요청만 gate로 잡도록 수정했다. 제품 보안을 약화하지 않았다.
- 기존 기록이 현재 페이지에서 밀려나도 같은 세션의 메모리 기록으로 복귀한다. 전체 새로고침/직접 진입에서 selected logId가 현재 페이지 응답에 없으면 단건 운영 기록 API가 없으므로 선택 불가 안내를 표시한다. 다른 기록으로 대체하지 않는다.
- 운영 기록 읽기 전용 화면에서 댓글 목록 전체를 추가 조회하지 않는다. ‘현재 게시글 관리’로 전환하면 기존 지연 댓글 목록을 제공한다.
- 실제 관리자 서버 연동, 다른 브라우저 엔진, 스크린리더 실기 검증은 미실행. 공개 smoke 외 Spring 관리자 요청 성공을 보장하는 자료가 아니다.

## 수동 확인
1. 게시글 목록에 날짜/정렬/상태/페이지를 적용하고 아래 행을 연다. 목록 폭, 모달/닫기, Back/Forward, 제목 새 탭을 확인한다.
2. 긴 본문에서 댓글로 이동 후 헤더 고정, 댓글 필터/페이지의 위치 유지, 댓글 개수/상태를 확인한다.
3. 조치 사유 작성 후 닫기/Back 취소, 일반 사유/OTHER 처리, 실패 후 입력 유지 및 최신 상태 재조회를 확인한다.
4. 운영 기록에서 현재 콘텐츠 관리→부모 관리→원래 기록 복귀 후 기록 ID/사유가 바뀌지 않는지 확인한다.
5. 권한 상실/로그아웃 후 목록/모달 데이터 제거를 확인한다. 실제 변경 조치는 사용자 본인이 허용된 테스트 콘텐츠에서만 수행한다.

## 자료
`review/task-only.patch`, `review/cumulative-from-head.patch`, `review/changed-files.txt`, `review/new-files.txt`, `verification/final/`, `verification/screenshots/`, `FILES.txt`, `SHA256SUMS.txt` 참조. 이전 실패/중간 실행은 verification/pre-final에 분리했으며 최종 통과 근거로 사용하지 않는다.
