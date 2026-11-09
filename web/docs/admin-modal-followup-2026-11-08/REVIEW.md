# 관리자 콘텐츠 모달 후속 보완 검토

## 기준과 변경 구분
- 브랜치 feat/admin-operation-log, 시작 HEAD bbacb9d2ce73fe0869f1263032b455e5bbfb828a.
- 직전 iyum-admin-content-modal-review-20261108-183545.zip 이후의 작업 트리를 기준으로 시작했다. 기존 미커밋 댓글 단건/모달/화면 변경을 보존했다.
- `review/task-only.patch`는 이번 시작 스냅샷 대비 차이이며 `review/cumulative-from-head.patch`는 기존 작업까지 포함한다. `review/start-status.txt`, `existing.patch`, `pre-existing-untracked/`로 시작 상태를 구분한다.
- 백엔드/DB/API/공개 정책 변경, 커밋/푸시/병합 없음. 사용자 개발 서버 8443 유지, 별도 생산 서버 8479로 검증했다.

## 재현과 원인
1. **불확실 결과 후 목록 잠금: 수정 전 실패 재현.** needsCheck는 남지만 최신 상태 확인 버튼이 모달 안에만 있었고, 목록 읽기까지 locked로 막혔다. 500 → 닫기 취소 → 닫기 승인 후 목록의 확인 버튼을 기다리는 테스트가 실패했다.
2. **부모 관리 재마운트: 수정 전 실패 재현.** revision 변경 시 댓글 단건의 active가 비면서 AdminPostPreview가 제거됐다. 조치 직후 조회 완료를 기다리지 않는 초기 테스트는 통과했으나, 재조회 GET을 gate로 보류하도록 고친 테스트에서는 댓글 검색 입력 자체가 사라져 실패했다. 이 지연 테스트를 최종에도 유지한다.
3. **명시적 닫기 후 내부 모달 재등장: 수정 전 실패 재현.** 마지막 이력만 replace하여 이전 관리 단계가 Back 목적지로 남았다. 운영 기록→댓글 관리→부모 관리→닫기→Back에서 dialog 1개가 남는 assertion 실패를 확인했다.
- 추가로 사유 버리기 승인 시 NavigationGuard의 승인 버전이 실제 pop URL 반영보다 먼저 바뀌면서 내부 selection state가 구 URL로 재생성되는 경로를 확인했다. 목록 선택을 URL의 selected를 유일한 기준으로 삼아 수정했다. 공용 NavigationGuard 자체는 변경하지 않았다.

재현 로그: `verification/pre-final/before.log`, `parent-before.log`. 최종 테스트는 요청 완료/실제 UI를 기다리며 임의 sleep이나 허용 오차 확대를 추가하지 않았다.

## 1. 불확실 결과 복구
- AdminModeration에 인증 수명 안의 미확인 대상(postId/commentId/action)을 전달한다. AdminPosts/AdminOperationLogs의 상위 메모리 상태에 보관하므로 내부 Back으로 provider가 재마운트되어도 맥락을 유지한다.
- 모달을 닫거나 사유를 취소해도 확인 필요 안내와 GET 재시도 경로를 목록에서 제공한다. 입력 버리기는 사유 입력만 버리고 미확인 조치를 성공 처리하지 않는다.
- 목록 읽기용 readLocked와 변경용 locked를 분리한다. 확인 전 다른 상세를 읽을 수 있지만 추가 숨김/해제는 차단한다.
- 최신 상태 확인은 기존 게시글 상세 또는 댓글 단건 GET만 수행한다. 정상 응답 검증 후 미확인 상태를 해제하고 revision을 증가시켜 목록/상세/댓글을 현재 조건으로 재조회한다. 이전 POST의 성공을 단정하지 않는다.
- 404/409 변경 오류도 같은 확인 경로를 사용한다. 확인 GET 자체가 404이면 현재 대상 부재를 안내하고 최신 목록/상세를 다시 읽는다. 네트워크/5xx/응답 오류이면 확인 필요 상태와 재시도를 유지한다. 401은 기존 만료, 403은 관리자 데이터 제거로 연결한다.
- POST 자동 재전송 없음. 사유는 사용자가 버리기를 승인한 경우만 정리한다. 영구 저장이나 브라우저 저장소는 추가하지 않았다. 다른 화면으로 완전히 떠나거나 인증 수명이 끝나면 메모리 상태는 정리된다.

## 2. 부모 관리 상태 유지
- AdminCommentPreview는 같은 댓글에서 확인한 부모 ID를 최신 요청 active 여부와 분리해 유지한다. pending/일반 오류에서도 같은 key의 AdminPostPreview를 계속 렌더링한다.
- 재확인 중/실패 안내를 표시하고 blocked로 조치를 막는다. 단건 재조회 실패는 해당 영역에서 재시도한다. 404는 이전 부모 화면을 제거하며 401/403은 기존 접근 정책을 따른다.
- 댓글 상태, 적용 검색어, 페이지, 펼친 UI를 유지한다. 실제 결과의 페이지 범위가 줄어든 경우에만 기존 페이지 보정을 수행한다. 다른 댓글/부모/회원은 key와 인증 소유권으로 분리한다. 부모 ID는 단건 응답에서만 취한다.

## 3. 닫기/Back/Forward
- 이번 브라우저 문서에서 모달을 연 경우 출발 목록의 검증된 경로, 기존 NavigationGuard index, 스크롤 위치를 임시 기록한다. Next 소유 state는 직접 복제/삭제하지 않는다.
- 일반 Back은 이전 모달 화면, Forward는 다음 모달 화면을 방문한다. 명시적 닫기는 확인된 출발 index로 history.go 하여 여러 내부 단계를 한 번에 종료한다. 닫기 중복 실행도 막는다.
- 닫은 직후 Back은 원래 목록 이전 페이지로 이동한다. **Forward는 과거 모달 화면 재방문을 허용**한다. 브라우저 이력을 삭제한 것으로 가정하지 않는다.
- 직접 URL/새로고침은 현 문서에서 시작한 흐름이 아니므로 이전 목록 index를 신뢰하지 않고 기존 allowlist/조건 정규화를 거친 목록 URL로 replace한다. 이 fallback은 임의의 외부 이력이나 과거 내부 이력을 삭제하지 않는다.
- 조치 후 마지막 페이지가 줄어든 경우 출발 entry로 돌아오면서 승인된 최신 목록 query로 초기화한다. `admin-dialog-return.ts`는 이 짧은 복귀 transaction만 보관하며 API 파라미터나 영구 저장에 사용하지 않는다.
- 모달 종료 후 결과 렌더링을 관찰해 선택 행(없으면 목록 제목)에 포커스를 복귀시키고 문서 스크롤을 복원한다. 취소한 Back은 URL/입력/모달을 유지한다.

## 4. 정보 배치
- 모달 댓글 제목은 ‘댓글’ 하나. 정식 상세 페이지는 기존 ‘댓글 관리’ 제목과 시각 표시를 유지한다.
- 댓글 툴바/결과 간격과 중복 구분선을 줄였다. 작성자/ID/상태/작성 시각/본문은 기본 표시하고, 본문 수정·최종 변경·숨김·삭제는 접힌 상세 정보에 넣었다. 키보드 summary 조작과 ‘기록 없음’ 의미를 유지한다.
- 운영 기록 ID는 헤더에만 표시한다. 대상·당시 조치·처리 시각·관리자와 저장 사유를 작은 요약으로 묶고, 현재 콘텐츠 제목 옆에 관리 버튼을 배치한다. 현재 내용 안내는 제목 바로 아래 한 번만 표시한다.
- 현재 유형명으로 저장 reason을 재생성하지 않는다. 동일 문자열만 중복 제거하며 다른 reasonDetail 및 LEGACY 구분을 보존한다.
- 고정 모달 헤더, 단일 본문 스크롤, 44px 조작 영역, 320px 줄바꿈 유지. 긴 본문을 자르거나 별도 내부 스크롤로 바꾸지 않았다.

## 주요 파일
- AdminModeration.tsx: 미확인 대상 확인 GET, 읽기/변경 잠금 분리, 목록 복구 안내.
- AdminPosts.tsx / AdminOperationLogs.tsx: 복구 상태 수명, URL 기준 선택, 모달 종료 후 목록 경로.
- AdminContentDialog.tsx / admin-dialog-return.ts / useAdminUrlQuery.ts: 확인된 흐름 종료, 직접 진입 fallback, 페이지 보정 및 포커스 복귀.
- AdminCommentPreview.tsx: 부모 관리 재조회 시 subtree 유지, 추가 시각 접기.
- AdminPostPreview.tsx / AdminComments.tsx / AdminContentMeta.tsx / AdminLogPreview.tsx / admin.css: 댓글/기록 정보 밀도와 제목 정리.
- admin-modal-followup-browser.mjs: 18개 후속 회귀. 기존 모달 및 운영 기록 테스트는 새 정보 위치/실제 목록 복귀 재조회 의미에 맞춰 조정했다.

## 최종 검증 명령과 결과
환경: Node 24, pnpm 10.34.3, 로컬 Chrome, 생산 빌드 127.0.0.1:8479.

| 명령 (web에서) | 결과 |
|---|---|
| pnpm lint | 통과, 경고 없음 |
| pnpm typecheck | 통과 |
| pnpm test | 493 통과 |
| pnpm build | 통과 |
| node tests/admin-modal-followup-browser.mjs | 18 통과 |
| node tests/admin-content-dialog-browser.mjs | 30 통과 |
| node tests/admin-posts-browser.mjs | 32 통과 |
| node tests/admin-detail-browser.mjs | 49 통과 |
| node tests/admin-operation-logs-browser.mjs | 32 통과 |
| node tests/admin-moderation-browser.mjs | 31 통과 |
| node tests/admin-history-browser.mjs | 13 통과 |
| node tests/member-success-toast-browser.mjs | 15 통과 |
| node tests/auth-ui-browser.mjs (mock) | 10 통과 |
| pnpm test:smoke | 18 PASS 출력, 끝까지 완료 |

브라우저 합계 230개. 실행 환경변수/서버 명령은 `verification/commands.txt`, 최종 로그는 `verification/final/` 참조.

## 검증 구분과 제한
- 관리자 브라우저 검증은 모두 mock API다. 기존 지연 요청 테스트는 AbortSignal을 무시하도록 전송을 구성하여 늦은 응답 처리도 확인한다. 실제 관리자 계정/콘텐츠/권한을 변경하지 않았다.
- smoke의 실제 Spring 연동은 공개 게시글 목록/본문 읽기, 초기 HTML/metadata/404 확인에 한정된다. 실제 Spring 관리자 조치 성공을 검증한 것으로 해석하면 안 된다.
- 외부 폰트 요청을 차단한 로컬 Playwright wrapper를 사용했으므로 캡처는 fallback 폰트일 수 있다. 일반 환경은 설치한 playwright 모듈을 사용하면 된다. ZIP wrapper의 로컬 경로는 환경에 맞춰 수정해야 한다.
- 직전부터 남은 우측 패널/탭 전용 admin-master-detail-browser, admin-panel-scroll-browser, admin-comment-panel-browser는 구조가 폐기된 구형 테스트여서 이번에도 실행하지 않았다. 통과로 보고하지 않는다. 새 모달/후속 테스트와 실제 정식 상세 회귀를 수행했다.
- 실제 관리자 세션, 다른 브라우저 엔진, 스크린리더 실기 검증은 미실행. 모달 흐름 및 복구 정보는 현 화면/인증 수명의 메모리 보관이며 전체 새로고침 후 유지한다고 약속하지 않는다.
- 부모 댓글 위치 검색 API가 없으므로 전체 댓글을 순회하거나 페이지를 추정하지 않는다.

## 수동 재현
1. mock 회귀 스크립트를 실행하여 수정 전 로그와 최종 로그를 비교한다. 실패했던 세 경로는 각 followup 이름으로 식별할 수 있다.
2. 게시글 조치 오류 후 닫기/사유 취소/Back을 각각 확인한다. 목록에서 대상 ID를 확인하고 최신 상태 확인 GET 실패→재시도를 수행한다. Network에서 POST가 추가되지 않아야 한다.
3. 댓글 기록→현재 댓글 관리→부모 관리에서 댓글 검색/페이지 후 조치한다. GET 대기/실패 중에도 검색 조건이 유지되고 추가 조치는 잠겨 있어야 한다.
4. 운영 기록→댓글 관리→부모 관리→닫기→Back은 원래 목록 이전 페이지로 가야 한다. Forward 재방문과 새로고침 후 안전한 닫기를 별도로 확인한다.
5. 320px에서 댓글 상세 정보 summary를 Tab/Enter로 열고 닫는다. 현재 댓글/부모 상태와 당시 기록을 구분하여 읽는다.

자료는 저장소 상대 경로를 유지한다. `FILES.txt`, `SHA256SUMS.txt`로 무결성을 확인할 수 있다. 이전 ZIP을 덮어쓰지 않았다.
