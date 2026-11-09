# 관리자 작성/처리 기간 및 정렬 프론트 연동

## 기준과 범위
- 브랜치: `feat/admin-operation-log`
- 시작 HEAD: `d9decf224a46453c4e7e188a0299bc7096c44eb7`
- 시작 작업 트리는 깨끗했습니다. 기존 다중 상태 칩, 초기화, 안정적인 재조회, 인증/초안 관련 작업은 이 커밋에 이미 포함되어 있습니다. 이번 patch는 시작 HEAD 이후 프론트 변경만 담습니다.
- 백엔드, 마이그레이션, API 계약, 계정/DB 데이터는 변경하지 않았습니다. 커밋/푸시/병합/배포하지 않았습니다.

## 계약 확인
`docs/api/admin-post-read.md`, `docs/Admin-Operation-Log-API.md` 및 현재 백엔드 조회 Controller/Query를 확인했습니다.
- 게시글은 반복 `statuses`, `keyword`, `startDate`, `endDate`, `sort=LATEST|OLDEST`, `page`, `size=20`을 사용합니다. 댓글 API에는 날짜/정렬을 추가하지 않습니다.
- 운영 기록의 기존 targetType/action/reasonType/startDate/endDate/sort/page/size 계약을 유지합니다.
- 날짜는 0001-01-01~9999-12-31의 유효한 달력 날짜입니다. 시작/종료 중 하나만 가능하며 같은 날짜도 가능합니다. 입력 문자열을 그대로 전송하고 종료일 포함/한국 시간 경계 처리는 서버가 담당합니다.
- 게시글 응답에는 sort 필드를 요구하지 않습니다. 두 화면 모두 서버 배열 순서와 페이지 메타데이터를 사용합니다.

예:
```text
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN&keyword=보청기&page=0&size=20&sort=OLDEST&startDate=2026-11-01&endDate=2026-11-08
GET /api/v1/admin/operation-logs?targetType=COMMENT&action=HIDE&page=0&size=20&startDate=2026-11-01&endDate=2026-11-08&sort=OLDEST
```

## UI와 조건 관리
- 공통 `AdminDateRange`는 날짜 편집값을 보유합니다. 직접 입력/달력 선택/날짜별 지우기는 요청하지 않고 ‘기간 적용’으로 두 날짜를 함께 적용합니다. 미적용 변경은 간결하게 표시합니다.
- ‘오늘/최근 7일/최근 30일/전체 기간’은 즉시 적용합니다. `Intl.DateTimeFormat`의 Asia/Seoul 날짜와 순수 달력 계산을 사용하여 브라우저 시간대에 영향을 받지 않습니다. 7일은 오늘-6, 30일은 오늘-29입니다.
- 기존 `OverlayDialog`를 재사용한 달력입니다. 월/연도 이동, 방향키/Home/End, Enter 선택, Escape 닫기, 포커스 제한/복귀를 제공합니다. 새 라이브러리는 없습니다.
- 기간/정렬 적용은 적용된 다른 조건을 유지하며 page=0으로 이동합니다. 게시글 상태 칩의 200ms 디바운스, 미제출 검색어, 미적용 날짜를 구분합니다. 검색/기간/정렬의 즉시 동작은 대기 중 칩 요청을 취소하고 최신 선택을 한 요청에 반영합니다.
- 운영 기록의 대상/조치/사유는 검색으로 적용됩니다. 날짜/정렬 변경이 미제출 필터를 소비하지 않습니다.
- 전체 초기화는 입력/적용 상태를 모두 기본값으로 돌립니다. 동일한 정상 조건의 검색/기간 적용/초기화는 중복 GET을 생략합니다. 오류 후에는 같은 조건으로 재시도할 수 있습니다.
- 결과 개수와 정렬 선택을 같은 행에 배치했습니다. 게시글 ID/날짜는 글자 단위 줄바꿈을 막고, 좁은 화면에서는 표 컨테이너 안에서만 가로 스크롤합니다.
- 목록→상세→목록 복귀 URL에 날짜/정렬을 기존 상태/검색어/page와 함께 보존합니다. 별도 저장소는 없습니다.

## 재조회 및 보안
- 게시글의 기존 lastSuccess, 요청 세대, AbortController, 인증 세션/탐색 수명 검증을 유지합니다.
- 운영 기록도 최근 성공 결과를 재조회 중 유지하고, 이전 결과임을 안내하며 페이지 이동을 잠급니다. 새 조건 성공 시 데이터/개수/페이지를 함께 교체합니다. 일반 오류에는 이전 결과+실패 안내+조회 재시도를 제공합니다.
- 새 조건 선택 시 기존 GET 소유권을 동기적으로 무효화합니다. 취소를 무시한 전송에서도 이전 성공/401이 최신 목록·인증을 덮어쓰지 못합니다.
- 인증/권한 상실 및 확인된 대상 없음에는 이전 결과를 지웁니다. 상태 전체 해제 및 댓글의 부모 상태 안내 정책은 기존대로 유지합니다.
- 동일 조건 중복 조회 생략으로 기존 테스트의 무조건 ‘검색’ 클릭이 요청을 발생시키지 않게 되었습니다. 해당 401/403/404 테스트는 새 검색 조건을 명시하고 기존 데이터 제거/보안 검증을 그대로 수행합니다.

## 파일 역할
- `src/components/admin/AdminDateRange.tsx`: 공유 기간 입력/달력/빠른 선택/입력 검증 UI.
- `src/lib/admin-dates.ts`: 공유 날짜 검증, KST 오늘, 달력 이동과 빠른 범위.
- `src/lib/admin-posts.ts`: 게시글 query 확장 및 URL 전송 검증.
- `src/lib/admin-navigation.ts`: 안전한 목록 복귀 조건 확장.
- `src/lib/admin-operation-logs.ts`: 기존 날짜 검증을 공유 함수로 연결.
- `src/lib/useAdminContentFilters.ts`: 날짜/정렬 보존, 동일 조건 중복 조회 방지. 댓글 사용처는 기존 동작을 기본값으로 유지.
- `src/features/AdminPosts.tsx`, `AdminOperationLogs.tsx`: 편집/적용 조건 연결, 정렬 및 재조회 표시.
- `src/components/admin/AdminComments.tsx`: reset 함수의 선택적 인자 도입에 따른 이벤트 래퍼만 변경.
- `src/styles/admin.css`: 관리자 전용 기간/달력/결과 도구 행/표 너비.
- 테스트: `admin-dates.test.mjs`, `admin-detail.test.mjs`, `admin-posts-browser.mjs`, `admin-operation-logs-browser.mjs`, `admin-detail-browser.mjs`, `helpers/admin-date-scenarios.mjs`, `helpers/admin-stable-list-scenarios.mjs`.

## 검증
최종 결과와 실행 명령은 검토 ZIP의 `review/verification.md`, `review/logs/`, 각 suite의 `results.json`에 기록합니다. 이전 시점의 통과 로그를 최종 결과로 재사용하지 않습니다.
- 단위: 윤년/경계/단독·동일 기간/잘못된 날짜, 시간대별 KST 빠른 범위, 실제 요청 파라미터 및 목록 복귀 URL.
- 브라우저: 수동 입력 무요청, 기간 적용, 미적용 검색/날짜 보존, 칩 변경, 정렬/page=0, 달력 키보드/포커스, 빠른 기간, 날짜만 제거, 오류/초기화 재시도, 취소 무시 이전 200/401, 조회 중 이전 결과와 잠금, 1440px/320px 화면.
- 기존 게시글·상세 댓글·운영 기록·운영 조치 전체 브라우저 회귀도 실행합니다.
- 브라우저는 Chrome + mock API입니다. 실제 Spring 서버의 인증된 요청과 DB 결과는 검증하지 않았습니다. 백엔드 날짜 경계/정렬의 실제 데이터 결과는 서버 측 테스트와 사용자 수동 확인이 필요합니다.
- 캡처는 합성 테스트 데이터입니다. 실제 회원 정보/쿠키는 사용하지 않습니다.
- 달력의 키보드 및 DOM 접근성은 자동화로 확인하며, 실제 스크린리더 음성 출력 및 모든 브라우저 엔진은 미검증입니다.

## 수동 확인
1. 최신 백엔드/프론트로 관리자 로그인 후 게시글 관리에서 시작일만, 종료일만, 같은 날짜를 적용합니다.
2. Network에서 날짜 문자열, 반복 statuses, sort 및 page=0을 확인합니다. 숨김/삭제 포함 실제 결과와 순서를 확인합니다.
3. 미제출 제목과 날짜를 입력하고 정렬/상태만 바꿔 입력값이 보존되는지 확인합니다.
4. 상세 진입 후 목록으로 돌아와 날짜/정렬/페이지 복원을 확인합니다.
5. 운영 기록에서도 대상/조치/사유 미제출 상태에서 기간·정렬을 바꾸고, 검색할 때만 해당 필터가 적용되는지 확인합니다.
6. 320px에서 달력 열기→방향키→Enter, Escape와 날짜별 지우기/기간 적용을 확인합니다.

## 최종 실행 결과
| 명령 (web에서 실행) | 결과 |
| --- | --- |
| `pnpm lint` | 통과, 종료 0 |
| `pnpm typecheck` | 통과, 종료 0 |
| `pnpm test` | 487/487 통과, skip 없음 |
| `pnpm build` | 프로덕션 빌드 통과, 종료 0 |
| `node tests/admin-posts-browser.mjs` | 32/32 통과 |
| `node tests/admin-operation-logs-browser.mjs` | 32/32 통과 |
| `node tests/admin-detail-browser.mjs` | 49/49 통과 |
| `node tests/admin-moderation-browser.mjs` | 31/31 통과 |
| `git diff --check` (저장소 루트) | 통과 |

브라우저 환경: Node 24, pnpm 10.34.3, Chrome headless, Next production `127.0.0.1:8479`, API `127.0.0.1:8082` 요청은 Playwright route로 모의 응답. 별도 검증 서버만 사용했습니다. 공통 날짜 시나리오는 게시글/운영 기록 각각 4개이며 기존 테스트의 보안 검증을 삭제하지 않았습니다. 게시글 기본 요청에는 새 `sort=LATEST` 기대값을 추가했습니다.

PC 및 320px 최종 캡처를 직접 확인했습니다. 결과 개수/정렬의 간격을 같은 행으로 조정한 뒤 최종 빌드와 네 개 브라우저 suite를 다시 실행했습니다. 320px에서는 기간 입력과 결과 도구 행이 자연스럽게 여러 줄로 배치되고 달력은 뷰포트 안에 표시됩니다. 표만 내부 가로 스크롤을 허용합니다.

미실행: 인증된 실제 Spring/DB 통합 검증, 실제 Spring 공개 데이터를 전제하는 전체 production-smoke, 수동 스크린리더/다른 브라우저 엔진 검증. 이번 날짜/정렬 계약과 구현에서 백엔드 보완이 필요한 불일치는 발견하지 못했습니다.
