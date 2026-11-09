# 관리자 모달 본문 너비·관리 우선순위 보완

## 기준 / 변경 범위
브랜치 feat/admin-operation-log, HEAD bbacb9d2ce73fe0869f1263032b455e5bbfb828a.
기존 미커밋 모달·댓글 카드·조회 실패 차단 변경을 보존했다. 이번 작업 시작 파일을 별도 보관하고 그 상태 대비 task-only.patch를 만들었다. 누적 cumulative-from-head.patch와 기존 existing.patch/pre-existing-untracked는 분리했다.
백엔드·API·DB 변경, 커밋·푸시·배포 없음. 8443의 사용자 개발 서버는 유지하고 검증 서버 8479만 별도로 사용/종료했다.

## 변경 파일과 목적
- web/src/components/admin/AdminComments.tsx: compact 댓글 카드의 추가 시각을 조치 뒤로 옮겨 모바일 시각적 순서와 키보드 순서를 맞췄다. 요청/오류/조치 핸들러는 변경하지 않았다.
- web/src/styles/admin.css: 댓글 카드 상단만 정보/조치 2열, 작성 시각과 본문은 전체 열을 사용한다. 기존 일반 div인 copy 래퍼에 display:contents를 적용하며 실제 제목/버튼/본문 의미 요소는 유지한다. 600px 이하는 조치를 본문 다음에 배치한다. 긴 이름과 문자열을 줄바꿈한다. 본문 스크롤은 추가하지 않았다.
- web/src/components/admin/AdminCommentPreview.tsx: 이 댓글 관리에 전용 주요 버튼 클래스 부여. 소속 게시글 영역은 관계 정보와 낮은 강조의 버튼으로 유지.
- web/src/components/admin/AdminPostPreview.tsx: 게시글 본문 라벨/은은한 배경, 이 게시글 관리 주요 버튼, 수정·처리 시각 문구와 중복 작성 시각 제거.
- web/src/components/admin/AdminContentMeta.tsx: omitCreated 옵션을 추가했다. 기본값 false로 정식 상세 페이지 등 기존 호출은 유지하며 모달에서만 사용한다. updatedAt=최종 변경, 숨김/삭제 null 정책 유지. 게시글에는 API가 제공하지 않는 본문 수정 시각을 만들지 않는다. 상단 작성 시각의 한국 시간 안내를 활용해 펼침의 중복 시간대 안내도 제거했다.
- web/tests/admin-comment-card-browser.mjs: 카드별 본문 폭 실측, 긴 작성자명, 키보드 순서, 주요/보조 버튼 computed 배경, 게시글 라벨과 중복 날짜/잘못된 본문 수정 라벨 부재 검증 보강.
- 이 문서 신규 추가.

현재 API와 정책은 그대로다. POST 및 GET, request revision/인증/화면 수명 검증 변경 없음. 기존 성공 POST 후 GET 실패의 이전 데이터 표시와 disabled+핸들러 차단, GET 재시도는 회귀 테스트로 확인한다. 운영 기록 저장 사유와 역사적 조치 값은 수정하지 않는다. 숨김/해제 버튼 전체를 새 주요 버튼 스타일로 바꾸지 않았다.

## 검증 환경 및 명령
Node 24 / pnpm 10.34.3 / 로컬 Chrome. 브라우저 테스트는 mock API 기반이다. wrapper는 외부 Google Fonts만 차단하고 기존 fallback 폰트를 사용한다. wrapper Playwright import 경로는 검토자의 설치 위치에 맞춰 변경해야 한다.

```sh
cd web
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec next start --hostname 127.0.0.1 --port 8479
# BASE_URL=http://127.0.0.1:8479 PLAYWRIGHT_MODULE=<동봉 wrapper>
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
실제 실행 스크립트와 최종 로그는 verification/final 참조. 고정 sleep 대신 응답 gate와 실제 UI 상태를 기다린다. 본문 실측은 카드 clientWidth에서 좌우 padding을 뺀 값과 body rect를 비교한다. 정상/숨김/삭제 각각 확인한다. 1440px/320px 캡처와 좌표 JSON을 포함한다.

기존 테스트는 삭제/skip하지 않았다. 오류 503/network·인증401/403·404·늦은 응답, GET 재시도 중 잠금, 부모 삭제 제한, 모달 내부 관리/복귀, Back/Forward, 필터·페이지 보존, inline 사유 취소, 콘텐츠 시각 구분을 유지한다.

## 한계 / 수동 확인
- 실제 Spring 관리자 인증/조치 연동은 미실행. 실제 계정/콘텐츠를 변경하지 않았다. production smoke의 실제 Spring 공개 읽기와 관리자 mock 테스트는 별개다.
- 이전 우측 패널 전용 admin-master-detail/admin-panel-scroll/admin-comment-panel 스크립트는 현재 모달 구조에 맞지 않아 이번 실행에서 제외했다. 현재 모달 회귀로 해당 흐름을 검증했다.
- OS별 실제 폰트와 스크린리더 음성 출력은 미검증. Chrome의 키보드/포커스와 DOM 접근성 이름을 확인했다.
- 수동 확인: 게시글 모달에서 본문과 작성 시각 확인 → 댓글 관리로 이동 → 각 상태 카드의 전체 폭/시각 펼침/사유 취소 확인 → 운영 기록의 이 댓글 관리/이 게시글 관리 전환 → 소속 게시글 보기와 내부 복귀 → 닫기 후 목록 조건 유지 확인.

추천 커밋 메시지: `style(admin): unify modal content layout and management actions`

검토 ZIP에는 이번 diff와 누적 diff를 분리하며 .env/인증정보/쿠키/실제 개인정보/node_modules/.next/.git/빌드 캐시는 포함하지 않는다. 캡처 데이터는 테스트용이다.

## 이번 최종 소스 실행 결과

| 검사 | 결과 |
|---|---|
| lint / typecheck / build | 모두 exit 0 |
| 단위 테스트 | 493 통과, 실패/skip 0 |
| admin-comment-card (mock API) | 10 통과 |
| admin-modal-followup (mock API) | 18 통과 |
| admin-content-dialog (mock API) | 30 통과 |
| admin-posts (mock API) | 32 통과 |
| admin-detail (mock API) | 49 통과 |
| admin-operation-logs (mock API) | 32 통과 |
| admin-moderation (mock API) | 31 통과 |
| admin-history (mock API) | 13 통과 |
| member-success-toast (mock API) | 15 통과 |
| auth-ui (mock API) | 10 통과 |
| production smoke | 18 통과 (실제 Spring 공개 읽기 포함) |

브라우저 총 240 통과. 최종 실행 실패 없음. 과거 로그를 재사용하지 않았다. 원본 assertion 유지와 추가 실측/키보드/스타일/날짜 assertion을 함께 확인했다.
