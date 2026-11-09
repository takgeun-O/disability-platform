# 관리자 목록·상세 패널 검토 (2026-11-08)

기준 HEAD: `d9decf224a46453c4e7e188a0299bc7096c44eb7`, 브랜치 `feat/admin-operation-log`.
작업 시작 시 날짜/정렬·탐색 복원 관련 미커밋 변경이 있었습니다. 기존 변경은 보존했습니다. `review/task-only.patch`는 작업 시작 파일 스냅샷과의 차이입니다. `review/pre-existing-tracked.patch`와 `review/cumulative-from-head.patch`는 별도 자료입니다. 백엔드, DB, API, 계정, 인증 토스트 코드는 변경하지 않았고 커밋/배포하지 않았습니다.

## 화면과 구현
- 관리자 경로에서만 기존 공용 헤더를 압축하고 푸터를 숨깁니다. 로고·계정·로그아웃·모바일 메뉴를 재사용하며 공개 화면에는 스타일을 적용하지 않습니다.
- 게시글 목록의 기존 제목 링크는 직접 상세 이동/새 탭 용도로 유지합니다. 명시적인 `미리보기` 버튼이 목록 옆 상세를 선택합니다. 날짜·정렬·검색·상태·페이지 조회 정책은 그대로입니다.
- 운영 기록은 logId 단위로 선택합니다. 저장된 reason/상세/LEGACY를 그대로 표시하며, 게시글 대상만 현재 게시글을 별도 영역에서 조회합니다. 댓글 ID로 부모 경로를 추측하지 않습니다.
- 1100px 이하에서는 단일 열로 전환합니다. 사용자가 선택하면 패널 제목으로 포커스를 이동하고 좁은 화면에서는 해당 위치를 즉시 보여줍니다. 복귀 URL로 자동 복원할 때는 강제 이동하지 않습니다.
- API 목록 검색과 분리한 `selected` URL 값으로 선택을 보존합니다. 검증된 목록 복귀 주소에도 포함합니다. 필터·정렬·페이지 변경 또는 최신 목록에서 항목 부재가 확인되면 선택을 해제합니다.
- `AdminModeration`의 기존 확인창/사유 유형/CSRF/중복 실행 방지를 재사용합니다. 성공 후 revision을 갱신해 목록과 상세를 재조회합니다. 필터에서 빠진 항목은 선택을 해제합니다.
- 상세 요청은 선택 ID, AbortController, 인증 요청 소유권, 탐색 수명, 조치 revision을 모두 확인합니다. A→B 전환 후 A 응답, 인증 변경 후 응답은 적용하지 않습니다. 403은 공통 접근 제한 화면으로 전환하며 목록과 패널을 함께 제거하고 세션은 임의 로그아웃하지 않습니다. 401은 기존 만료 흐름을 사용합니다.
- 게시글 조회 실패 시 재시도를 제공합니다. 운영 기록 대상 조회 실패는 기록 자체를 지우지 않습니다. 성공 조치 이후 조회 실패 안내는 변경 재실행과 구분합니다.

## API
기존 `GET /api/v1/admin/posts`, `GET /api/v1/admin/posts/{postId}`, `GET /api/v1/admin/operation-logs` 및 게시글 hide/restore API를 재사용합니다. 선택한 게시글만 조회하며 전체 행의 본문을 선조회하지 않습니다. 반환 ID와 런타임 검증은 기존 admin-detail.ts를 사용합니다. `selected`는 API 요청에 포함하지 않습니다.
운영 기록의 현재 계약에는 댓글 부모 게시글 ID/본문이 없습니다. 댓글 원문 연결은 백엔드 계약 확장 이후 과제이며 이번에는 제공하지 않습니다.

## 시안과의 차이
신고 관리·통계·알림은 만들지 않았습니다. 기존 필터의 적용 시점을 보존하려고 검색/상태와 기간 적용을 구분했습니다. 시안의 오른쪽 신고 조치 대신 게시글 미리보기 및 기존 조치 확인창을 사용합니다. 목록의 원문 제목 링크와 선택 버튼은 별도입니다. 운영 기록에서는 조치를 제공하지 않습니다.

## 검증 환경 및 한계
로컬 production Next 서버 8479 + Chrome Playwright + 모의 Spring API입니다. 실계정 로그인/실제 숨김·복원은 수행하지 않았습니다. 캡처는 가상 데이터이며 개인정보가 아닙니다.
초기 외부 폰트 연결을 허용한 실행 일부에서 페이지 load/DOMContentLoaded/초기 화면 대기가 8초를 넘었습니다. 해당 실패 로그는 별도로 남겼습니다. 폰트가 모든 시간 초과의 원인이라고 확정하지 않습니다. 최종 모의 테스트는 외부 Google Fonts 요청을 차단하는 검토용 Playwright 어댑터로 외부 의존성을 분리하며 제품 CSS의 기존 sans-serif fallback을 사용합니다. 제품 폰트 설정은 바꾸지 않았습니다.
공개 production-smoke는 /community의 과거 문구 `질문과 경험을 함께`가 현재 SSR 본문에 없어서 실패했습니다. 현재 CommunityHome은 PostList를 사용하며 이 파일/테스트는 이번 작업에서 바꾸지 않았습니다. 이 smoke를 통과로 기록하지 않습니다.

## 수동 확인
1. 관리자 로그인 후 게시글/운영 기록에서 기간·정렬·필터·페이지를 변경하고 항목을 선택합니다.
2. 상세 관리로 이동 후 뒤로/앞으로, 화면 내 복귀, 새로고침에서 조건과 선택을 확인합니다.
3. 허용된 검증용 게시글을 숨김/해제하고 목록·상세가 함께 갱신되는지 확인합니다.
4. 320px에서 선택/닫기/키보드 포커스를 확인합니다. 로그의 댓글 대상에는 원문 연결이 없어야 합니다.
5. 실제 브라우저에서 브랜드 웹폰트 로딩, 실서버 세션 만료/권한 회수도 확인해 주세요.


## 회귀 조사에서 확인한 차이
- 초기 게시글 Back 테스트는 새 빈 미리보기 제목 `게시글 상세`를 상세 페이지 진입 완료로 잘못 인식했습니다. 실제 URL 이동 전 goBack이 실행돼 `about:blank`가 됐으며 진단 JSON의 URL/빈 body/요청 이력으로 확인했습니다. 빈 패널 제목은 `게시글 미리보기`로 구분하고, history 테스트는 실제 `/admin/posts/{id}` URL과 페이지 제목을 모두 기다리도록 강화했습니다. assertion을 생략하지 않았습니다.
- 403은 리스트와 미리보기를 함께 제거하는 AdminModeration 접근 제한 화면으로 통일했습니다. 기존 리스트 내부 오류 대기 테스트는 접근 제한 제목과 데이터 제거/유효 세션 유지 검증으로 변경했습니다.
- 운영 기록 사유 검사는 새 선택 버튼의 텍스트를 제외하고 유형 라벨과 본문 p 개수/원문을 직접 검증합니다. 원문 보존·중복 제거 조건은 유지됩니다.
- 페이지 진입 대기에서 실패한 일부 이전 목록 테스트는 DOMContentLoaded 이후 실제 결과 영역이 준비될 때까지 기다리도록 변경했습니다. 타임아웃은 늘리지 않았습니다.

## 변경 파일
- `web/src/features/AdminPosts.tsx`: 선택/URL 복원, 목록과 상세의 조치 후 갱신, 공통 권한 차단.
- `web/src/features/AdminOperationLogs.tsx`: logId 선택, 기록 상세, 원문 미리보기와 현재 내용 구분.
- `web/src/components/admin/AdminSelectionPanel.tsx`: 공통 패널 제목, 선택 시 포커스와 모바일 이동, 닫기.
- `web/src/components/admin/AdminPostPreview.tsx`: 선택 대상 GET, 경합·인증 검증, 기존 moderation 연결.
- `web/src/components/admin/AdminLogPreview.tsx`: 저장된 운영 기록과 선택 게시글 현재 내용.
- `web/src/lib/admin-navigation.ts`: 안전한 selected 파싱과 목록 복귀 주소 전달.
- `web/src/styles/admin.css`: 관리자 전용 chrome/필터/목록·패널/반응형.
- `web/tests/admin-master-detail-browser.mjs`: 새 패널 15개 사용자 흐름 검증.
- `web/tests/admin-history.test.mjs`: 선택 값/안전한 복귀 URL/API 조건 분리 단위 검증.
- `web/tests/admin-history-browser.mjs`: 실제 상세 URL 진입 대기와 실패 진단 기록.
- `web/tests/admin-posts-browser.mjs`, `web/tests/admin-operation-logs-browser.mjs`, `web/tests/helpers/admin-stable-list-scenarios.mjs`: 새 공통 접근 제한 화면 검증, 사유 표시 검증과 목록 준비 대기.
- 이 문서: 작업 범위 및 결과.

## 실행 명령
`web/`에서 Node 24.21.0 / pnpm 10.34.3:
```
pnpm lint
pnpm typecheck
pnpm test
pnpm build
BASE_URL=http://127.0.0.1:8479 pnpm test:smoke
```
브라우저 실행: `BASE_URL=http://127.0.0.1:8479 PLAYWRIGHT_MODULE=/private/tmp/iyum-master-detail/playwright-offline.mjs node tests/<suite>-browser.mjs`.
대상 suite: admin-posts, admin-operation-logs, admin-detail, admin-moderation, admin-history, admin-master-detail. 검토 어댑터는 `verification/playwright-offline.mjs`에 포함합니다. 기본 Playwright 모듈로 실행하면 원래 Google Fonts 로딩을 포함합니다.

## 최종 검증 결과
| 검사 | 결과 |
|---|---|
| pnpm lint | 통과 |
| pnpm typecheck | 통과 |
| pnpm test | 492 / 492 통과 |
| pnpm build | 통과 |
| admin-posts-browser | 32 / 32 통과 |
| admin-operation-logs-browser | 32 / 32 통과 |
| admin-detail-browser | 49 / 49 통과 |
| admin-moderation-browser | 31 / 31 통과 |
| admin-history-browser | 13 / 13 통과 |
| admin-master-detail-browser | 15 / 15 통과 |
| pnpm test:smoke | 실패: 기존 공개 /community SSR 문구 기대값 불일치. 범위 밖으로 수정하지 않음 |

브라우저 총 172개는 mock API 기반입니다. 마지막 선택 URL 정리 후 영향을 받는 posts/history/master-detail을 다시 실행했습니다. 이전 실패 로그는 최종 통과 로그와 별도 디렉터리에 보관합니다. 실계정·실제 Spring 변경 요청 검증은 미실행입니다.

캡처는 `verification/final/final-master/`의 posts/logs 1440px·320px, 정상·숨김·삭제 대표 화면을 참고하세요. 추가적인 1024px·390px 및 기존 상세/모달 캡처는 각 회귀 폴더에 있습니다. 화면 비교에서 데스크톱 목록의 이전 최소 너비가 날짜 열을 가리는 문제를 발견해 전용 테이블 폭을 조정했습니다. 320px은 단일 열이고 긴 본문은 패널 안에서 키보드로 스크롤할 수 있습니다.

선택 상태는 화면 URL에만 존재하며 본문/인증정보를 브라우저 저장소에 새로 저장하지 않습니다. 상태 전체 해제 시 이전 selected 값도 URL에서 제거합니다. 댓글 대상 기록의 원문 연결, 실환경 웹폰트 로딩 및 실제 권한 회수 확인은 위 한계와 수동 절차를 참고하세요.
