# 관리자 목록 재조회 안정화 검토 — 2026-11-07

## 범위·기준

브랜치 feat/admin-operation-log, 시작 HEAD 674a1a4690642b014ebac97a1556572c8bbd97f5.
기존 다중 상태 연동·칩·초기화 버튼 보완은 미커밋 상태로 존재했다. 기존 변경을 보존했고 task-only.patch는 HEAD가 아닌 이번 시작 시 실제 파일 대비 diff다. status-before.txt와 status-after.txt로 구분한다.

백엔드, API 계약, 운영 기록, 일반 사용자 화면을 수정하지 않았다. 커밋·푸시·병합하지 않았다. 이전 보고서의 ‘대기 중 행을 숨긴다’는 정책은 이번 후속 변경으로 대체하며 과거 검증 기록은 그대로 둔다.

## 원인과 구현

기존 active는 현재 query/retry/revision의 완료 여부를 나타내는데, 목록과 부모 안내도 active.data를 사용하여 debounce부터 사라졌다.

- AdminPosts / AdminComments: 마지막 성공한 목록+조회 조건을 별도로 보존한다. 현재 요청 상태와 분리하여 필터·검색·초기화·페이지·운영 조치 후 GET 동안 이전 행과 개수/페이지 정보를 유지한다. 유효한 응답을 받은 뒤 상태를 함께 교체한다.
- 작은 갱신 안내 한 줄을 항상 같은 공간에 두고 비갱신 중 visibility:hidden으로 숨긴다. 결과 전체의 고정 높이는 만들지 않는다. aria-busy 및 role=status를 유지하고 개수 라벨은 ‘이전 결과’로 바꾼다. 성공 후 실제 결과가 짧아지면 자연스럽게 줄어든다.
- 일반 조회 실패: 이전 목록 + ‘갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.’ + 기존 오류/재시도. 조치/페이지 잠금은 성공까지 유지. 재시도는 현재 적용 조건.
- 댓글 조치 버튼은 disabled와 이벤트 핸들러 양쪽에서 stale/locked/parentUnavailable 확인. 페이지는 기존 busy/failed 잠금 및 핸들러 방어 재사용. 칩/검색/초기화는 활성 유지.
- 전체 해제는 표시만 숨기지 않고 마지막 성공 결과 자체를 제거하므로 재선택 대기 중 옛 목록이 재등장하지 않는다. 401/403/404도 기존 정책에 따라 보관 결과를 제거한다. 인증/화면/요청 세대와 GET abort 방어 유지.
- AdminPostDetail: 댓글 컴포넌트에 게시글 key 및 확인된 부모 상태 전달. 같은 대상의 필터 조회 중 부모 숨김/삭제 안내를 유지하고, 부모 상세에서 새 상태를 확인하면 반영한다. 이 prop 변경으로 별도의 GET을 추가하지 않는다.
- AdminComments의 목록은 응답 postId 일치도 확인한다. 기존 라우트의 postId key 및 AdminAccess 회원/인증 세대 key가 다른 게시글/계정의 메모리 재사용을 막는다.
- 운영 조치 POST의 소유권/취소 구조는 변경하지 않았다. 후속 조회 중 조건 변경·전체 해제 경합과 페이지 보정 테스트 유지.

## 변경 파일

- web/src/features/AdminPosts.tsx: 이전 결과 표시, 오류/즉시 정리.
- web/src/components/admin/AdminComments.tsx: 이전 결과/부모 안내, stale 조치 잠금.
- web/src/features/AdminPostDetail.tsx: 부모 상태 및 대상 key 전달.
- web/src/styles/admin.css: 갱신 안내 한 줄 스타일만 추가. 기존 칩/초기화 버튼 스타일 유지.
- web/tests/helpers/admin-stable-list-scenarios.mjs: 공통 지연 응답·실패·보안·레이아웃 회귀.
- web/tests/admin-posts-browser.mjs, admin-detail-browser.mjs: 신규 시나리오 실행 연결. 기존 assertion 변경/삭제 없음.

## 최종 실행 결과

Node 24, pnpm 10.34.3. 최종 production 빌드를 별도 8479 포트에서 실행했다. 사용자 8443 개발 서버는 종료하지 않았다. 브라우저 응답은 mock이며 실제 Spring 세션/DB/계정을 변경하지 않았다.

| 명령 | 결과 |
|---|---|
| pnpm test | 483/483 |
| pnpm lint | 오류·경고 없음 |
| pnpm typecheck | 통과 |
| pnpm build | 통과 |
| node tests/admin-posts-browser.mjs | 28/28 (기존 24 + 신규 4) |
| node tests/admin-detail-browser.mjs | 49/49 (기존 44 + 신규 5) |
| node tests/admin-moderation-browser.mjs | 31/31 |
| git diff --check | 통과 |

브라우저 명령 환경은 `PATH=/opt/homebrew/opt/node@24/bin:$PATH`, `PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs`. BASE_URL 기본 8479, 각 ADMIN_ARTIFACT_DIR / ADMIN_DETAIL_ARTIFACT_DIR / ADMIN_MODERATION_ARTIFACT_DIR를 별도 결과 폴더로 지정했다.

초기 lint에서 페이지 보정 조건이 표시용 displayQuery를 참조한 오류를 발견하여 현재 요청 query로 정정했다. 초기 lint는 logs/initial/에 분리했고, 위 결과와 캡처는 그 정정과 최종 ‘이전 결과’ 라벨 변경 후 실행한 것이다. 최초 실행 로그를 최종 결과로 대체하지 않았다.

## 회귀 검증 내용

- fake clock으로 칩 클릭 후 32ms와 200ms를 분리, 응답 gate로 실제 GET도 보류. 양 시점 모두 20개 행 유지, 위치/높이/스크롤/키보드 포커스 비교.
- 페이지 하단에서 다음 페이지 요청을 보류해 긴 목록 높이와 scrollY, 실행 버튼 포커스 유지. Enter 재입력으로 추가 GET 없음.
- 갱신 중 조치 disabled 및 DOM click 무효, 페이지 aria-disabled/중복 실행 차단. 필터 조작은 활성.
- 500 오류 후 이전 결과 표시·잠금·적용 검색어로 재시도 및 복구.
- 전체 해제 → 재선택의 응답 대기에도 옛 행 없음. 404/401/403에서 보관 행과 페이지 영역 제거. 403은 세션 유지.
- 숨김 부모 안내(1440/320) 및 삭제 부모 안내 유지.
- 기존 abort 무시 응답 gate의 늦은 성공/401, debounce·전체 해제, 로그아웃·계정 전환·탐색 회귀와 운영 조치 후속 GET 경합을 그대로 실행.

## 실측 및 캡처

각 측정은 하나의 browser evaluate에서 결과 높이, 필터 y, scrollY를 함께 수집했다. 허용 오차 2px 미만으로 검증했고 아래 최종 측정 변화는 모두 0이었다.

| 화면 | 결과 높이(px) | 필터 y(px) | scrollY |
|---|---:|---:|---:|
| 게시글 1440 | 1623 → 1623 | 281 → 281 | 0 → 0 |
| 게시글 320 | 1455 → 1455 | 449 → 449 | 0 → 0 |
| 댓글 1440 | 3442.75 → 3442.75 | 395.078125 → 395.078125 | 578 → 578 |
| 댓글 320 | 6770.25 → 6770.25 | 396.765625 → 396.765625 | 1331 → 1331 |

logs/retained-*.json은 실제 좌표. screenshots/retained-*.png는 최신 조건 GET 응답 보류 중 화면이며 기존 목록과 갱신 안내/‘이전 결과’ 개수, 포커스가 함께 보인다. 두 너비에서 전체 페이지 가로 넘침 없음을 검증했다.

## 남은 제한

실제 Spring 서버/실제 계정 연동은 실행하지 않았다. 별도 운영 기록·일반 사용자 전체 브라우저 스위트는 변경 범위 밖이라 재실행하지 않았다. 최신 결과의 항목 수 변화 및 실패 오류 안내의 등장으로 필요한 최종 화면 크기 변화는 허용한다. 목록 전체에 최소 높이를 고정하거나 자동 스크롤/포커스 이동으로 이를 감추지 않는다.
