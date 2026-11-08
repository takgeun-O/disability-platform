# 관리자 조치 폼 및 운영 기록 복귀 헤더 보완

## 기준과 범위
- feat/admin-operation-log / HEAD bbacb9d2ce73fe0869f1263032b455e5bbfb828a.
- 시작 시 기존 모달·카드·조회 실패 방어·레이아웃 미커밋 작업을 보존했다. 이번 task-only.patch와 누적 cumulative-from-head.patch, 시작 기존 변경 existing.patch 및 pre-existing-untracked를 분리한다.
- 프론트만 변경. 백엔드·DB·API·운영 정책 변경 및 커밋·푸시·배포 없음.

## 변경 내용 / 주요 파일
- AdminModeration.tsx: 필드별 즉시 아래 오류, 붉은 테두리/고대비 문구/경고 기호/role alert, aria 연결. 서버 INVALID_REASON은 해당 필드에 유지하고 나머지 서버·보안·통신 오류는 상단에 표시한다. 기타 상세의 공백/500 코드포인트 정책을 유지하며 글자 수 도움말을 오류와 구분한다.
- 검증 제출마다 증가하는 attempt와 ref로 첫 오류 입력란을 렌더링 후 focus한다. 일반 입력 변경은 포커스를 움직이지 않는다. 모달 본문 또는 정식 조치 대화상자만 scrollTo하고 배경 문서는 움직이지 않는다. 라벨부터 오류까지 헤더 아래에 표시되도록 기준을 잡았다.
- 폼 제목은 대상 종류/#ID/조치를 포함한다. 대상 요약만 한 줄 말줄임하며 카드의 실제 본문은 유지한다. 조치별 영향 설명과 기록 안내를 분리했다. 소속 게시글 숨김 안내는 단건/목록에서 확인된 postStatus가 HIDDEN인 댓글 해제에만 표시한다.
- AdminActionLabel: 열린 대상은 사유 입력 중/처리 중을 표시하며 잠금 로직은 유지한다. 접근성 이름의 대상·동작은 유지한다. 제출 중 안내는 기존 status로 제공한다. 취소는 해당 버튼에 포커스 복원하며 없거나 잠겼다면 현재 모달 제목/기존 관리 안내로 복귀한다. 성공 시 기존 관리 안내 복귀 정책을 보존한다.
- admin-moderation.ts: 빈 기타 상세/길이 초과 문구만 명확화. ModerationTarget의 optional postStatus는 UI 설명용이며 요청 payload에 추가하지 않는다. DTO reasonType/reasonDetail, Unicode 코드포인트/trim 정책 및 GET/POST 흐름 변경 없음.
- AdminComments.tsx / AdminCommentPreview.tsx / AdminPostPreview.tsx / AdminPostDetail.tsx: 대상의 입력·처리 표시와 확인된 소속 게시글 상태 전달. 기존 disabled와 핸들러 차단 유지.
- AdminContentDialog.tsx: 기존 transition/requestExit를 그대로 호출하는 복귀 버튼을 고정 헤더로 이동. 접근성 이름은 운영 기록으로 돌아가기, 화면 문구는 ← 운영 기록 #ID. 소속 게시글 관리에서는 댓글 관리 복귀도 유지한다. 본문 중복 nav 제거. 관리 제목에 대상 ID를 표시하고 소속 게시글 ID는 기존 댓글 단건 응답을 통해 확인된 값만 전달한다. history 구현은 추가하지 않는다.
- admin.css: inline/정식 대화상자의 오류 스타일, 채움 실행 버튼, 한 줄 요약, 모바일 헤더 배치, 제목 가까운 상태 배지.
- admin-form-header-browser.mjs 신규: 폼/실제 가시성/포커스/실측색상/헤더/dirty 복귀 검증. 기존 admin-moderation, admin-comment-card, admin-content-dialog 테스트는 문구·관리 제목·오류/도움말 연결 기대값을 맞추되 기존 기능 검증을 유지한다.

## 검증 과정
첫 브라우저 실측에서 기본 입력 CSS가 오류 테두리를 덮는 것을 검출했고 모달 종류를 명시한 범위로 해결했다. 이후 기존 테스트에서 성공 후 안내 포커스가 달라진 회귀를 발견해 기존 성공 복귀를 보존했다. 오류 설명과 글자 수 도움말이 함께 연결되는 변경은 두 ID를 정확히 검증하도록 보완했다. 중간 로그는 최종 결과와 분리한다.

데스크톱1440/320px, POST/COMMENT × hide/restore에서 미선택 반복 제출, OTHER 빈 값/공백/이모지501자, 요청0회, 실제 border/color, 입력란·라벨·오류의 모달 내부 위치, focus, 배경 scrollY, 수정 후 오류 해제, 취소 확인/취소 후 원래 버튼, 제출 잠금/POST1회, 고정 헤더 복귀를 검증한다. 정식 상세 페이지 양 크기에서도 오류/취소 focus를 확인한다. 헤더 복귀를 취소/확정한 뒤 Back/Forward와 목록 sort/page 보존을 확인한다.

기존 회귀로 503/network 후 GET 재시도 잠금, 401/403/404, 인증/응답 경합, 이유 선택/CSRF, changed=false, 부모 관리 댓글조건 보존, 모달 닫기/탐색, 최근 카드 전체 폭/날짜/스타일을 확인한다. 고정 sleep 대신 response gate와 UI 상태를 사용한다.

## 실행
Node24 / pnpm10.34.3 / Chrome, production 포트8479. 사용자 개발서버8443 유지.

```sh
cd web
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec next start --hostname 127.0.0.1 --port 8479
# BASE_URL=http://127.0.0.1:8479 PLAYWRIGHT_MODULE=<동봉 wrapper 경로>
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
동봉 run-a/run-b는 실제 실행 스크립트다. wrapper는 외부 Google Fonts만 차단하고 기존 fallback 글꼴을 사용하며 Playwright import 경로는 검토 환경의 설치 경로로 바꿔야 한다. 최종 검증 로그·종료 코드와 결과 집계를 동봉한다.

## 수동 확인 / 제한
1. 게시글 및 댓글의 숨김/해제 폼을 열고 미선택 제출, OTHER 공백 및501자 제출. 필드/오류/포커스가 헤더 아래에 보여야 하며 요청은 없어야 한다.
2. 유효한 입력 후 취소를 취소/확정, 대상 버튼 포커스를 확인한다. 실제 조치는 테스트 콘텐츠/관리자 계정에서 사용자 판단으로 수행한다.
3. 운영 기록에서 관리로 진입하고 아래까지 스크롤해 고정 복귀 버튼을 사용한다. 작성 중 복귀 확인, 제출 중 제한, 닫기 후 목록 조건을 확인한다.
- 관리자 브라우저는 mock API 검증이다. 실제 Spring 관리자 세션/조치는 실행하지 않았다. 실제 계정/콘텐츠 변경 없음. smoke의 Spring 공개 읽기는 관리자 통합 검증이 아니다.
- 이전 우측 패널 전용 admin-master-detail/admin-panel-scroll/admin-comment-panel 스크립트는 실행하지 않았다. 현재 모달용 회귀를 사용했다.
- 실제 OS별 폰트와 스크린리더 음성 출력은 미검증. Chrome 키보드/포커스/접근성 이름/가시성을 확인했다.
- 추천 커밋 메시지: `fix(admin): clarify moderation validation and pin record navigation`

ZIP은 이번 diff와 기존 누적 작업을 구분하고 실제 계정/인증정보/.env/node_modules/.next/.git/빌드 캐시를 제외한다. 모든 캡처는 mock 데이터다.

## 최종 소스 실행 결과
- lint / typecheck / production build: 모두 종료 코드 0.
- 단위 테스트: 493/493 통과.
- 브라우저: 251/251 통과 (신규 폼·헤더 11, 카드 10, 후속 모달 18, 콘텐츠 모달 30, 탐색 13, 게시글 32, 상세 49, 운영 기록 32, 조치 31, 회원 토스트 15, 인증 UI 10).
- production smoke: 18/18 통과. 실제 Spring 공개 조회만 포함하며 관리자 변경 API 실연동은 미검증.
- 최종 검증 후 운영 코드 변경 없음. 빌드가 생성한 next-env.d.ts는 작업 시작 시점의 내용으로 복원했다.
- 데스크톱/320px 캡처는 verification/screenshots에 포함했다. 320px에서 상세 사유 라벨·입력·오류가 고정 헤더 아래에 있고, 복귀/닫기 버튼과 실행 버튼이 겹치지 않는 것을 확인했다.
