# 운영 기록 currentTarget 및 우측 조치 패널 (2026-11-09)

## 기준과 범위
- 브랜치: feat/admin-operation-log
- 시작 HEAD: c72144ff34ccea6f6e86ad0dabac7417c7bd7275
- 시작 git status는 깨끗했습니다. 기존 구현은 이 커밋에 포함되어 있으며, 백엔드·DB·API 문서는 변경하지 않았습니다.
- 게시글 관리 모달은 유지합니다. 운영 기록의 모달/관리 화면 전환만 동일 페이지 우측 패널로 대체했습니다.

## API 및 화면
- GET /api/v1/admin/operation-logs: 필수 nullable currentTarget, 작성자 필드, 게시글 제목/string, 댓글 제목/null을 검증합니다. 누락은 null로 보정하지 않습니다.
- 목록은 서버의 작성자와 미리보기 그대로 표시합니다. currentTarget:null도 행과 전체 개수를 유지합니다. 처리 관리자와 콘텐츠 작성자를 구분합니다.
- 선택한 POST는 GET /api/v1/admin/posts/{id}, COMMENT는 GET /api/v1/admin/comments/{id}로 현재 전체 내용을 읽습니다. 목록 행별 상세 호출이나 게시글의 댓글 목록 자동 조회는 없습니다.
- 조치는 기존 POST /api/v1/admin/posts/{postId}/{hide|restore}, /api/v1/admin/posts/{postId}/comments/{commentId}/{hide|restore}를 그대로 사용합니다. 댓글의 부모 ID는 단건 조회 응답을 사용합니다.
- 과거 기록은 logId별 스냅샷으로 유지하며 현재 콘텐츠와 구분합니다. 성공 후 현재 콘텐츠와 운영 기록 목록을 기존 조건으로 재조회합니다.
- 선택은 URL selected로 보존합니다. 조건 변경 시 선택과 조회 조건을 같은 렌더에서 변경합니다. 테스트 중 발견한 URL 동기화에 의한 이전 selected 복원 오류도 수정했습니다.
- 요청 수명·인증 소유권·AbortController·조치 revision 검증을 재사용합니다. 조치 후 상세 재조회 실패 시 기존 내용과 조치 잠금, GET 재시도를 유지합니다.
- 다른 기록으로 이동/닫기에는 기존 사유 폐기 확인을 사용합니다. 완료 안내는 다음 선택으로 이월하지 않습니다.

## 레이아웃
- 1100px 초과: 목록/상세 2열, sticky 상세 헤더와 단일 본문 스크롤.
- 1300px 이상 조치 중: 패널을 확장하고 왼쪽 목록은 카드형, 현재 전체 본문과 폼은 나란히 배치합니다.
- 좁은 화면: 동일 페이지의 세로 배치. 긴 본문은 전부 유지하며 폼까지 문서 스크롤합니다.
- 사유 오류 스타일과 포커스 이동은 패널 내부에 적용합니다. 게시글 관리 모달의 기본 배치는 바꾸지 않습니다.
- 페이지 조작 잠금은 별도 disabled 옵션으로 표현하여 사유 입력 중을 조회 실패/로딩으로 오인하게 하지 않습니다.

## 변경 파일
- lib/admin-operation-logs.ts: 계약 타입 및 런타임 검증.
- features/AdminOperationLogs.tsx: 작성자/내용 목록, 선택·조건 처리, 우측 패널.
- components/admin/AdminLogPanel.tsx (신규): 패널 헤더, 스크롤, 기존 조치 워크스페이스 연결.
- AdminLogPreview.tsx, AdminPostPreview.tsx, AdminCommentPreview.tsx: 직접 조치와 불필요한 댓글 조회 차단 옵션. 기존 기본 동작 유지.
- AdminModeration.tsx: 패널 오류 필드 노출, 종료 시 이전 완료 안내 정리.
- AdminPagination.tsx: 오류/로딩과 구별한 조작 잠금 옵션.
- styles/admin-operation-logs.css: 운영 기록에만 한정한 패널/폼/반응형 스타일.
- tests/admin-operation-logs.test.mjs: currentTarget 계약.
- tests/admin-log-current-target-browser.mjs (신규): 10개 동작·레이아웃 회귀.
- tests/admin-operation-logs-browser.mjs: 패널 닫기와 작성자 열 검증 변경.
- 그 외 관리자 브라우저 fixture의 currentTarget:null 추가는 신규 필수 응답 계약 호환만 변경했습니다.

## 최종 검증
Node 24, pnpm 10.34.3, production server 127.0.0.1:8479 사용.
- pnpm typecheck: 통과
- pnpm lint: 오류 0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고 3개
- pnpm test: 494 통과 / 실패 0 / skip 0
- pnpm build: 통과
- node tests/admin-log-current-target-browser.mjs: 10/10 통과
- node tests/admin-operation-logs-browser.mjs: 전체 32/32 통과
- TEST_FILTER='comment list location|short viewport|short content post-list' node tests/admin-action-workspace-browser.mjs: 선택한 게시글 모달 회귀 6/6 통과 (전체 스크립트 통과 주장 아님)
- BASE_URL=http://127.0.0.1:8479 pnpm test:smoke: 통과. 공개 페이지 SSR와 로컬 Spring 공개 목록/상세를 읽기 전용으로 비교했습니다. 관리자 실제 세션 통합 검증은 아닙니다.

브라우저 명령은 BASE_URL=http://127.0.0.1:8479, PLAYWRIGHT_MODULE=/private/tmp/iyum-master-detail/playwright-offline.mjs로 실행했습니다. 외부 글꼴 네트워크 요청을 차단한 Chrome 환경입니다. 운영 기록·조치 API는 Playwright mock이며 운영 데이터는 변경하지 않았습니다.

새 테스트는 1920/1440/1100/320px에서 null/탈퇴 작성자, 단건 API ID, 직접 조치, 필드 오류의 실제 테두리/포커스/위치, 입력 유실 확인, 조치 후 상태/과거 기록 유지, 페이지/정렬, 사라진 선택, Abort 무시 늦은 응답, Back/Forward, 404/503 및 401/403 정리를 확인합니다. 서버 INVALID_REASON과 조건 변경 선택 해제도 검증합니다.

## 제한과 자료
- 실제 관리자 세션으로 숨김·해제를 실행하지 않았습니다. 실제 Spring 관리자 API 통합 확인은 별도로 필요합니다.
- 이전 운영 기록 모달 전환을 기대하는 다른 과거 브라우저 스크립트 전체는 이번에 실행하지 않았습니다. 계약 fixture만 갱신했으며 현재 동선은 신규 10건 및 운영 기록 32건으로 검증했습니다.
- 외부 폰트가 로드되는 사용자 환경에서의 미세한 줄바꿈 차이는 남습니다.
- 캡처는 mock 데이터입니다. 실사용자의 본문이나 인증정보는 포함하지 않습니다.
- 검토 자료: /private/tmp/iyum-current-target-front/review (task-only.patch, sources/, logs/, screenshots/, 변경 목록과 git 상태).
- 커밋·푸시·배포하지 않았습니다.
