# 관리자 콘텐츠 모달: 댓글 재조회 차단 및 카드 UI

## 기준과 범위
- 브랜치: feat/admin-operation-log
- HEAD: bbacb9d2ce73fe0869f1263032b455e5bbfb828a
- 작업 시작 시 이미 존재한 모달/복구/탐색 변경을 보존했다. `review/start-status.txt`, `existing.patch`, `pre-existing-untracked/`로 분리한다.
- `review/task-only.patch`는 이번 작업 시작 파일 대비 차이이며, `cumulative-from-head.patch`는 기존 작업을 포함한다.
- 프론트만 수정했다. 백엔드·DB·API 계약, 인증 정책 변경 및 커밋·푸시·병합·배포 없음.

## 원인과 수정
`AdminCommentPreview`는 GET 오류에서 이전 data를 유지하지만 변경 버튼은 moderation의 locked만 검사했다. 성공한 POST가 종료되면 locked가 해제되므로 후속 GET 실패에도 이전 상태로 추가 조치를 시작할 수 있었다.

이번에는 요청 대상 ID, retry, revision이 일치하는 active 결과가 존재하고 오류가 없을 때만 조치 가능하다. `actionLocked = locked || !active || !!active.error`를 disabled와 이벤트 핸들러 양쪽에서 사용한다. 대기/실패에는 이전 내용을 표시 중임을 명시한다. POST 성공 안내와 후속 조회 실패 안내를 구분하며 재시도는 GET만 전송한다. 404는 이전 댓글을 제거하고 401/403은 기존 인증/권한 정리를 따른다. 다른 ID의 이전 data를 오류 처리에서 재사용하지 않도록 제한했다.

수정 전 실브라우저 + mock API에서 숨김/숨김 해제 × 503/network 네 사례 모두 활성화된 버튼 때문에 실패했다. 동일 회귀는 수정 후 통과했다. 추가 성공 안내 assertion은 최초 실행에서 Next route announcer까지 선택하는 strict selector 오류가 있었다. 댓글 영역 내 alert로 범위를 한정했고 assertion은 유지했다. 해당 중간 실패 로그는 최종 결과와 분리했다.

## UI와 변경 파일
- AdminCommentPreview.tsx: 최신 상태 조치 차단, 이전 데이터 안내, 단건 댓글 카드, 소속 게시글 이동, ID 포함 접근성 이름.
- AdminComments.tsx: 모달 compact 목록의 카드 경계, 댓글 본문 라벨/배경, 우상단 조치(모바일 하단), 삭제 제한, 접힌 수정·처리 시각. 정식 상세 페이지의 비compact 목록은 기존 동작/표현 유지.
- AdminLogPreview.tsx: 당시 운영 조치 / 현재 콘텐츠 구분, 저장 사유 원문 보존, 카드 내부 관리 동작.
- AdminPostPreview.tsx: 댓글 관리로 이동 문구, 현재 게시글 내 관리 버튼.
- AdminModeration.tsx: 사유 입력 설명의 사용자 용어를 소속 게시글로 통일. 검증/전송/복구 로직 변경 없음.
- AdminContentDialog.tsx: 댓글 #ID 관리 제목, 읽기 영역에서도 같은 모달의 소속 게시글 전환.
- admin.css: 관리자 콘텐츠 모달에 한정한 카드·본문·제목 스타일. 본문 높이 제한/내부 스크롤 없음.
- admin-comment-card-browser.mjs: 신규 회귀. 기존 admin-content-dialog, admin-modal-followup은 새 접근성 이름/문구만 맞추며 기존 검증 유지.

단건 API GET /api/v1/admin/comments/{commentId}의 postId로 관계를 확인한다. URL이나 댓글 ID로 게시글 관계를 추측하지 않는다. 댓글 상태와 postStatus는 별개이며, contentUpdatedAt=null은 기록 없음이다. 이력 reason은 재계산하지 않는다. 숨김/삭제 본문은 기존 관리자 API가 반환한 내용만 표시한다.

## 검증 재현
Node 24, pnpm 10.34.3, 로컬 Chrome. 기존 사용자 개발 서버 8443을 건드리지 않고 별도 프로덕션 서버 8479를 사용했다. 브라우저는 모의 API이며 실제 관리자 계정/콘텐츠를 변경하지 않았다. 외부 Google Fonts만 차단하는 wrapper를 사용해 기존 fallback 폰트로 검증했다.

```sh
cd web
pnpm lint
pnpm typecheck
pnpm test
pnpm build
pnpm exec next start --hostname 127.0.0.1 --port 8479
# 아래 각 테스트: BASE_URL=http://127.0.0.1:8479, PLAYWRIGHT_MODULE=<동봉 wrapper 경로>
node tests/admin-comment-card-browser.mjs
node tests/admin-modal-followup-browser.mjs
node tests/admin-content-dialog-browser.mjs
node tests/admin-posts-browser.mjs
node tests/admin-detail-browser.mjs
node tests/admin-operation-logs-browser.mjs
node tests/admin-moderation-browser.mjs
node tests/admin-history-browser.mjs
node tests/member-success-toast-browser.mjs
node tests/auth-ui-browser.mjs
BASE_URL=http://127.0.0.1:8479 pnpm test:smoke
```
wrapper의 Playwright import 경로는 검토 환경의 설치 위치에 맞춰야 한다. 테스트 기본 artifact 경로 대신 환경변수로 임시 경로를 지정했다. 최종 실행 로그와 결과 집계는 `verification/final/` 참고.

신규 검증: 성공한 숨김/해제 후 503/network, 실패/재시도 중 차단, 강제 이벤트에도 폼 열리지 않음, 재시도 성공 후 상태 복구, POST 1회 유지, 401/403/404 제거, 최신 소속 게시글 삭제 제한. 1440/320px에서 정상·숨김·삭제 카드, 긴 무공백 본문·줄바꿈·이모지, 시각 펼침 키보드, 사유 입력 취소, 소속 게시글 이동, 포커스 복귀, 가로 넘침과 버튼 위치/본문 높이 실측을 확인한다. 응답 gate와 UI 대기를 사용한다.

기존 회귀에는 불확실 결과의 GET 확인, dirty 종료, 부모 관리 중 댓글 조건/페이지 유지, page 범위 보정, 직접 URL/새로고침/Back/Forward, 취소를 무시하는 늦은 응답 및 계정 전환이 포함된다.

## 한계와 수동 확인
- 실제 Spring 관리자 로그인/조치 연동은 수행하지 않았다. mock을 실제 서버 검증으로 간주하지 않는다.
- 이전 우측 패널/탭 구조 전용 admin-master-detail, admin-panel-scroll, admin-comment-panel 스크립트는 이번 실행 대상이 아니다. 모달 전용 회귀로 현재 구조를 검증했다.
- 실제 운영 폰트/OS 및 스크린리더 음성 출력까지 검증하지 않았다. 키보드/접근성 이름/포커스는 Chrome에서 확인했다.
- 사용자 환경에서 운영 기록 댓글을 열어 이 댓글 관리 → 조치 성공 후 GET 실패를 재현하고, 버튼이 잠긴 채 이전 내용 안내와 GET 재시도가 제공되는지 확인한다. 최신 상태 확인 후 소속 게시글 삭제 여부에 따라 제한되는지도 확인한다.
- 모달의 댓글 관리로 이동에서 카드별 사유 입력을 열고 취소, 시각 펼침, 닫기와 복귀를 확인한다. 모달 헤더/본문 스크롤 구조와 날짜·정렬 URL 정책은 유지한다.

캡처에는 테스트용 가상 작성자와 텍스트만 포함한다. 환경변수 파일, 실제 인증정보/쿠키/개인정보, node_modules/.next/.git/빌드 산출물은 검토 ZIP에서 제외한다.

## 최종 실행 결과

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
| production smoke | 18 통과; 실제 Spring 공개 목록/상세 읽기 포함, 관리자 조치 검증 아님 |

브라우저 총 240 통과. 기존 개발 서버 8443은 보존하고 별도 검증 서버 8479만 종료한다. 중간 selector 실패는 검증 영역을 댓글 오류로 좁혀 해결했으며 최종 실패/미해결 항목은 없다. 미실행 범위는 위 한계에 별도 기재했다.
