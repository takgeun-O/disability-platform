# 관리자 상태 칩 및 즉시 조회 — 2026-11-07

## 범위와 기준

브랜치 `feat/admin-operation-log`, 시작 HEAD `674a1a4690642b014ebac97a1556572c8bbd97f5`.
이전 다중 상태 API 연동, URL 복원, 단위 테스트, 체크박스와 브라우저 테스트가 미커밋 상태였다. `status-before.txt`에 시작 상태를 보존했다. `task-only.patch`는 HEAD가 아니라 작업 시작 당시 실제 파일을 기준으로 비교한다. 기존 다중 상태 연동 변경을 되돌리지 않았다. 새 파일은 useAdminContentFilters.ts, helpers/admin-chip-scenarios.mjs와 이 보고 자료다. AdminStatusFilter는 이번 작업 전에 이미 존재한 미추적 파일이다.

백엔드·API 계약·운영 기록 화면·일반 사용자 화면은 변경하지 않았다. 두 API 문서는 프론트 동작 설명만 갱신했다. 커밋·푸시·병합·배포하지 않았다.

## 구현

- 공통 AdminStatusFilter: 실제 checkbox + label, 그룹 이름, 고정 너비 체크 영역, 42px 칩, 선택/비선택 색과 키보드 포커스. 복수 선택 안내.
- AdminPosts: 검색 첫 줄, 상태/작은 초기화 둘째 줄. AdminComments: 상태 왼쪽, 검색 오른쪽, 좁은 화면에서는 분리. 큰 빈 결과 고정 높이 제거.
- useAdminContentFilters: 입력 검색어/적용 검색어를 분리. 칩 선택은 즉시 표시하고 200ms 이후 마지막 상태 조합으로 page=0 조회. 검색/초기화는 예약 취소 후 즉시 조회. IME Enter 방어.
- 전체 해제는 예약과 GET을 즉시 취소·무효화하고 결과·개수·페이지 이동을 숨긴다. `표시할 상태를 선택해 주세요.`만 표시하며 빈 선택을 전체 조회로 변환하지 않는다. 다시 선택하면 마지막 적용 검색어 사용.
- 클릭 즉시 요청 세대를 증가시켜 debounce 중 도착한 과거 성공·오류를 차단한다. 기존 인증·내비게이션·운영 조치 revision 검사를 유지한다. 목록 GET의 AbortController를 POST와 공유하지 않는다.
- 조회 중 이전 결과 행은 숨긴다. 칩/검색은 활성, 여러 페이지의 페이지 이동은 유지하되 잠금 처리. 0~1페이지는 페이지 이동 영역을 숨긴다.
- 조치 완료 후 최신 조건으로 조회. 빈 선택에는 조회하지 않는다. 기존 게시글 범위 밖 페이지 보정과 댓글 조치 revision당 1회 보정을 유지한다.
- 게시글 URL 복원은 마지막 적용 조건을 사용한다. 전체 해제 자체는 서버에 보낼 수 없는 로컬 선택 상태이며 URL을 빈 조건으로 저장하지 않는다.

예: `/api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN&page=0&size=20`. 댓글도 `/api/v1/admin/posts/{postId}/comments`에 동일한 반복 파라미터. `status`, `ALL`, 쉼표 배열을 보내지 않는다.

## 최종 검증

Node 24, pnpm 10.34.3. 별도 프로덕션 서버 8479에서 Playwright/Chrome으로 검증했다. 사용자 개발 서버 8443은 종료하지 않았다.

| 명령 | 결과 |
|---|---|
| `pnpm test` | 483/483 통과 |
| `pnpm lint` | 오류·경고 없음 |
| `pnpm typecheck` | 통과 |
| `pnpm build` | 통과 |
| `node tests/admin-posts-browser.mjs` | 24/24 통과 |
| `node tests/admin-detail-browser.mjs` | 44/44 통과 |
| `node tests/admin-moderation-browser.mjs` | 31/31 통과 |
| `git diff --check` | 통과 |

브라우저 실행 환경: `PATH=/opt/homebrew/opt/node@24/bin:$PATH`, `PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs`. BASE_URL 기본값 `http://127.0.0.1:8479`. 결과 위치는 각 `ADMIN_ARTIFACT_DIR`, `ADMIN_DETAIL_ARTIFACT_DIR`, `ADMIN_MODERATION_ARTIFACT_DIR`로 별도 지정했다.

기존 일괄 적용 시나리오 2개씩을 새 정책 7개씩으로 대체했다. 게시글 19→24, 상세 39→44. 운영 조치 29→31. 검증을 삭제하여 실패를 우회하지 않았다.

신규 공통 브라우저 시나리오는 실제 선택 상태에 따라 27개 모의 데이터의 서버 응답과 페이지 정보를 구성한다. 초기 세 상태, 연속 선택 200ms 1회, 검색/초기화 예약 취소, 적용 검색어 분리, 전체 해제, 재선택, IME, 키보드, 0~1페이지 숨김을 확인한다. fake clock 및 응답 gate로 취소를 무시하는 오래된 성공/401의 도착까지 기다리고 debounce/빈 선택 중 상태가 오염되지 않음을 검증했다. 로그아웃 시 예약 정리도 확인했다.

운영 조치 확인 모달은 배경 조작을 막으므로 POST 중 배경 칩 변경을 내부 변수 조작으로 만들지 않았다. 실제 가능한 경로인 POST 성공 후 후속 GET 대기 중 상태 변경/전체 해제를 검증했고 기존 POST 진행 중 이동·인증 변경 및 중복 실행 검증을 유지했다.

초기 운영 조치 실행은 30/31이었다. 원인은 1페이지 결과에서 이번 정책으로 숨겨진 페이지 입력창을 기다린 이전 assertion이다. 결과 행, URL page=0, 정확한 GET 페이지 순서 [1,0] 및 페이지 입력창 미노출로 대체한 뒤 전체 31/31 통과했다. 초기 결과는 `logs/initial/`에 분리했다. 초기 lint의 의존성 경고와 신규 테스트 미사용 인자도 정리하고 최종 lint를 다시 실행했다.

## 화면 확인

`screenshots/chips-posts-*`: 게시글 일부 상태 선택.
`screenshots/chips-comments-*`: 댓글 일부 선택(부모 숨김 안내 유지).
`screenshots/chips-empty-*`: 전체 해제.
각각 1440px와 320px 실제 브라우저 캡처다. 전체 화면 가로 넘침 assertion 및 키보드 Space 선택/포커스를 검증했다. 댓글 캡처가 긴 것은 기존 긴 본문 반응형 fixture를 유지했기 때문이다. 모바일 칩은 한 줄에 배치되고 검색·초기화는 자연스럽게 다음 줄로 이동한다.

## 한계 및 수동 확인

이번 브라우저 결과는 모의 API 검증이다. 실제 Spring 세션/운영 데이터로 조회·조치하지 않았으며 실제 계정·DB도 변경하지 않았다. 실서버에서는 정상/숨김만 선택하고 Network에 반복 statuses와 마지막 적용 keyword가 전달되는지, 응답 items 및 totalElements가 조건에 맞는지 확인하면 된다. 프론트에서 서버 결과를 임의로 걸러내지 않는다.

공통 인증·토스트 소스와 운영 기록 화면은 변경하지 않았다. 해당 전체 별도 브라우저 스위트는 이번 범위에서 재실행하지 않았고 관리자 관련 인증·경합·401/403 회귀는 위 세 스위트에 포함됐다.
