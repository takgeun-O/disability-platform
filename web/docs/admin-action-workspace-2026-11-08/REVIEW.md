# 관리자 게시글·댓글 조치 화면 검토

## 기준 / 작업 분리
- 브랜치 feat/admin-operation-log, HEAD bbacb9d2ce73fe0869f1263032b455e5bbfb828a.
- 기존 모달/카드/폼 오류/운영 기록 복귀 작업은 미커밋 상태로 보존했다.
- task-only.patch는 이번 시작 시점의 파일과 최종 파일 비교다. 기존 변경(existing.patch 및 pre-existing-untracked)과 누적 cumulative-from-head.patch를 별도로 제공한다.
- 백엔드·DB·API 변경, 커밋·푸시·배포, 실제 콘텐츠 조치 없음.

## 구현 / 주요 파일
- AdminContentDialog: 조회 컴포넌트를 hidden 상태로 유지하고 같은 모달 본문에 조치 화면을 렌더링한다. 검색/페이지/접힌 정보는 재마운트하지 않는다. 진입 직전에 저장한 모달 스크롤을 조회 복귀 시 사용한다. URL/history 전환 구조는 변경하지 않는다. 조회용 헤더의 원문/댓글 이동은 조치 화면에서 숨기고 닫기와 운영 기록 복귀는 유지한다.
- AdminModeration: 선택한 대상의 확인된 데이터 스냅샷으로 전체 본문·작성자·상태·작성 시각을 표시한다. UI 데이터는 POST로 전송하지 않는다. 폼 아래 자동 스크롤 대신 조치 제목에 초기 포커스를 둔다. Tab으로 사유 선택에 진입한다. 클라이언트 검증 및 서버 INVALID_REASON은 오류 DOM 반영 뒤 해당 필드에 focus한다. 이미 보이는 오류에는 스크롤하지 않고 필요한 경우 모달 본문만 최소 이동한다. 일반 입력 중에는 이동하지 않는다.
- AdminPostPreview, AdminComments, AdminCommentPreview, 정식 AdminPostDetail: 기존 응답의 전체 데이터를 open에 전달한다. 120자 요약을 본문으로 쓰지 않는다. 정식 상세 페이지는 기존 단독 확인창 동작을 유지한다.
- 완료 안내는 대상의 AdminModerationSlot 위치에 표시한다. 최신 GET 대기 후 대상 안내로 포커스를 복귀한다. 댓글이 현재 필터에서 빠지면 댓글 결과 영역에 완료/결과 제외 안내를 표시하고 해당 영역으로 복귀한다. 취소는 원래 버튼을 사용한다. 단건 404 시 제거된 카드 대신 현재 댓글 영역이 포커스 대상이 될 수 있다.
- admin-moderation.ts: UI용 content 타입만 추가. moderate는 기존 reasonType/reasonDetail만 직렬화한다. 기존 CSRF/인증/불확실 결과/자동 POST 재전송 금지 정책 유지.
- admin.css: 데스크톱 약 62:38 두 열, 오른쪽 sticky 폼, 네이비 제목/청록 실행 버튼. 본문 전체 표시와 한 개의 모달 스크롤 소유자 유지. 900px 이하 또는 높이720px 이하는 단일 열로 전환한다. 보조 수정·처리 시각은 한 열에서 폼 뒤에 배치한다. 댓글 단건의 상단 빈 공간/구분선을 제거한다.

## 시안과 차이 및 이유
- 시안의 예시 표기/사용자/본문을 제품에 넣지 않는다. 캡처에만 한국어 mock 내용을 사용한다.
- 운영 기록 복귀·닫기 등 기존 고정 헤더 기능을 유지한다. 실제 ID와 action으로 폼 제목을 구분한다.
- 짧은 창에서 sticky 두 열을 강제하지 않는다. 화면 높이가 부족할 때 모든 입력/오류/실행 버튼을 한 스크롤로 접근하도록 한 열을 사용한다.
- 긴 본문을 축약하지 않아 모달 높이보다 긴 내용은 모달 전체 본문 스크롤로 읽는다. 작은 본문 전용 스크롤은 없다.
- 조치 진입은 본문 제목에 포커스를 둬 읽기 위치를 먼저 제공한다. 입력란으로 자동 이동해 본문을 밀어내지 않는다. 제출 오류는 별도 필드 포커스 정책을 따른다.

## 검증 구성
신규 admin-action-workspace-browser: 운영 기록 POST/COMMENT × hide/restore ×1440/320, 전체 본문/줄바꿈/이모지/긴 문자열, 두 열 실측·sticky·단일 열, 반복 서버 INVALID_REASON의 필드 포커스/색상/가시성, GET 후 완료 위치, 댓글 목록 취소 스크롤·필터 유지 및 필터 제외 완료, 1440x600/720x500 낮은 화면, 게시글 목록에서 짧은 한국어 본문 조치/취소를 검증한다.
기존 admin-form-header는 반복 미선택/OTHER공백/501코드포인트/noPOST/오류 수정/취소/제출 잠금/운영 기록 복귀를 유지한다. admin-comment-card의 폼 locator는 카드 내부에서 조치 화면으로 변경했다. admin-content-dialog의 초기 포커스는 조치 제목 및 Tab으로 사유 선택을 확인한다. assertion 삭제나 허용 오차 확대는 하지 않았다.

## 실행 환경 / 명령
Node24 / pnpm10.34.3 / production 8479 / Chrome. 사용자 개발서버8443은 유지했다.

```
cd web
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec next start --hostname 127.0.0.1 --port 8479
# BASE_URL=http://127.0.0.1:8479, PLAYWRIGHT_MODULE=동봉 offline wrapper
node tests/admin-action-workspace-browser.mjs
node tests/admin-form-header-browser.mjs
node tests/admin-comment-card-browser.mjs
node tests/admin-modal-followup-browser.mjs
node tests/admin-content-dialog-browser.mjs
node tests/admin-history-browser.mjs
node tests/admin-posts-browser.mjs
node tests/admin-detail-browser.mjs
node tests/admin-operation-logs-browser.mjs
node tests/admin-moderation-browser.mjs
node tests/member-success-toast-browser.mjs
node tests/auth-ui-browser.mjs
BASE_URL=http://127.0.0.1:8479 pnpm test:smoke
```
run-a/run-b와 최종 로그를 동봉한다. wrapper는 외부 Google Fonts 요청만 차단하며 앱 API는 각 테스트의 mock 라우트를 사용한다. Playwright import 경로는 검토 환경 설치 위치에 맞춰야 한다.

## 제한 / 수동 확인
- 관리자 테스트는 mock API와 실제 Chrome 기반이다. 실제 Spring 관리자 세션/숨김·해제는 실행하지 않았다. 공개 Spring 읽기 smoke와 구분한다.
- 화면 너비720px은 데스크톱 200% 확대 시의 레이아웃 폭을 대체해 검증한다. 실제 브라우저 메뉴 확대, OS별 글꼴, 스크린리더 음성 출력은 미검증이다.
- 기존 우측 패널 전용 테스트 대신 현재 모달용 회귀를 실행한다.
- 실제 환경에서는 테스트 콘텐츠로 게시글/댓글 조치 열기→사유 검증→취소→운영 기록 복귀, 처리 후 최신 상태와 필터 제외를 확인한다. 이 과정에서 사용자 판단 없이 운영 데이터를 변경하지 않았다.
- 추천 커밋: feat(admin): show moderation content and reason form side by side

## 최종 실행 결과
- lint / typecheck / build: 모두 종료 코드0. 단위 테스트493/493 통과.
- 브라우저265/265 통과: 조치 화면14, 폼·헤더11, 카드10, 후속 모달18, 콘텐츠 모달30, 탐색13, 게시글32, 상세49, 운영 기록32, 조치31, 회원 토스트15, 인증 UI10.
- production smoke18/18 통과. 관리자 Mock API 검증과 실제 Spring 공개 읽기 검증을 구분한다.
- 마지막 내용 맞춤 높이 CSS를 포함한 빌드에서 전체 위 테스트를 다시 실행했다. 최종 결과에 이전 로그를 재사용하지 않았다.
- 중간 실패는 기존 카드 안에서 폼을 찾던 locator와 초기 사유 필드 포커스 기대값이었다. 새 조치 화면의 폼 및 조치 제목→Tab→사유 선택으로 변경하고 기존 오류/기능 assertion을 유지했다. 중간 실행 로그는 pre-final로 분리했다.
- 직접 캡처 비교: 짧은 본문 모달의 과도한 하단 공백을 height:fit-content로 제거했다. 긴 본문은 기존 최대 높이, 고정 헤더와 본문 스크롤을 유지한다.
- 검토 캡처: short-post-1440.png, action-COMMENT-hide-1440.png, server-error-COMMENT-restore-320.png, completed-COMMENT-hide-1440.png, filtered-complete-320.png 등.
- 빌드 생성 next-env.d.ts는 이번 시작 시점 내용으로 복원한다. 운영 코드의 검사 후 수정은 없다.
