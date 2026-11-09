# 운영 기록 출발 상세의 복귀 — 2026-11-08

## 범위와 기준

브랜치 feat/admin-operation-log, HEAD d9decf224a46453c4e7e188a0299bc7096c44eb7. 기존 날짜/정렬/탐색 복원 미커밋 변경을 보존했다. 이번에는 출발 목록 전달, 안전한 복귀 URL, 상세 링크/브레드크럼, 게시글·운영 기록의 '전체 초기화' 문구 및 테스트만 변경했다. 백엔드·DB·API·공용 인증/탐색 관리 변경과 커밋·푸시·배포 없음.

## 두 경로의 수정 전 조사

### A. 브라우저 뒤로가기

현재 작업 트리와 직전 production 빌드에서는 사용자 보고의 **조건 초기화를 재현하지 못했다**. 수정 전 production에서 날짜/정렬/대상/조치/사유/page=1 적용 → 실제 게시글 링크 → Back, Forward/Back, reload, 직접 진입까지 URL·필터·요청·결과 일치를 통과했다. 이후 새로 추가한 명시적 복귀 링크 assertion에서 실패했다.

기존 사용자 dev 서버 8443에서도 mock API로 같은 동선을 실행했다. Back 후 URL·날짜·OLDEST·page=1·표시 결과는 유지됐지만 같은 조건 GET이 2개 발생해 production 기준의 '정확히 1회' assertion이 실패했다. before-dev JSON에 두 요청의 동일 조건을 보존했다. 개발 모드 effect 재실행과 일치하는 현상이나, 실제 사용자 세션의 초기화 원인으로 단정하지 않는다. 요청 개수 assertion을 완화하지 않았다.

현재 useAdminUrlQuery는 useSearchParams와 기존 NavigationGuard의 탐색 수명을 읽는다. 같은 화면의 replaceState에는 내부 state를 유지하고, 실제 복귀에는 새 수명에서 URL로 초기화한다. scope가 무효인 이전 컴포넌트는 URL을 쓰거나 응답을 적용하지 않는다. 이 기존 구조에서 Back 오류를 입증하지 못했으므로 임의 재설계하지 않았다.

### B. 상세의 명시적 복귀 링크

실제 결함을 확인하고 수정했다. AdminOperationLogs의 링크는 `/admin/posts/{id}`만 전달했다. 상세 라우트는 `from`을 adminReturnHref로 검증하지만 이전 함수는 `/admin/posts?`만 허용했다. 따라서 운영 기록에서 왔다는 정보가 없고 상세 링크/브레드크럼은 항상 '목록으로'/'게시글 관리', 목적지는 기본 `/admin/posts`였다. 이는 운영 기록의 조건 복귀가 아니라 다른 목록으로 이동하는 동작이다.

기존 테스트는 운영 기록의 Back은 검증했으나 **화면 내 복귀 링크는 posts 분기에서만 검증했다**. 이전 보고서도 상세의 '목록으로'가 게시글 목록을 유지한다고 기록했다. 이번 정책에는 맞지 않는 검증 누락이었다. 이제 양쪽 출발 화면에서 링크 라벨·href·브레드크럼·실제 클릭 복귀까지 검증한다.

## 수정

- AdminOperationLogs: 실제 적용 query로 adminLogHref를 만든 뒤 encodeURIComponent하여 상세 링크의 from에 전달. 미적용 input이나 날짜 draft를 사용하지 않는다.
- admin-navigation: 복귀 주소는 `/admin/posts`, `/admin/operation-logs` 정확한 경로만 허용. 외부/프로토콜 상대/하위 경로/해시는 안전한 기본 게시글 목록으로 처리한다. 원문 주소를 사용하지 않고 각 목록의 기존 query parser + href 생성기로 재구성한다. 반복 상태와 단일 필드 검증을 유지한다.
- AdminPostDetail: 검증된 returnHref의 목록 종류에 따라 '운영 기록으로' 및 '운영 기록' 브레드크럼을 표시한다. 게시글 출발/직접 진입은 기존 '목록으로'/'게시글 관리'. 링크는 실제 href이며 history.back()에 의존하지 않는다.
- AdminPosts, AdminOperationLogs: 공통 필터 버튼을 '전체 초기화'로 변경. aria-hidden 아이콘 외 visible text가 접근 가능한 이름이다. 댓글 전용 검색 초기화는 범위 밖이므로 유지했다.
- URL 동기화·인증·요청 세대·페이지 보정 코드는 변경하지 않았다. URL에는 적용 조건만 있고 목록 응답이나 인증정보는 없다.

## 변경 파일

- web/src/features/AdminOperationLogs.tsx
- web/src/features/AdminPostDetail.tsx
- web/src/features/AdminPosts.tsx
- web/src/lib/admin-navigation.ts
- web/tests/admin-history-browser.mjs
- web/tests/admin-history.test.mjs
- web/tests/admin-operation-logs-browser.mjs
- web/tests/admin-posts-browser.mjs
- web/tests/helpers/admin-date-scenarios.mjs
- web/tests/helpers/admin-chip-scenarios.mjs
- 본 문서

## 검증

Node 24 / pnpm 10.34.3, web 디렉터리. production은 별도 8479, 사용자 dev 8443은 보존했다.

```
pnpm test
pnpm lint
pnpm build
pnpm typecheck
PLAYWRIGHT_MODULE=<installed playwright/index.mjs> node tests/admin-history-browser.mjs
PLAYWRIGHT_MODULE=<installed playwright/index.mjs> node tests/admin-operation-logs-browser.mjs
PLAYWRIGHT_MODULE=<installed playwright/index.mjs> node tests/admin-posts-browser.mjs
PLAYWRIGHT_MODULE=<installed playwright/index.mjs> node tests/admin-detail-browser.mjs
git diff --check
```

브라우저는 BASE_URL=http://127.0.0.1:8479와 개별 artifact 환경 변수를 사용했다. 최초 dev 비교만 BASE_URL=8443, TEST_FILTER='logs history restores applied conditions 1440'. 실제 링크·Back/Forward를 사용했으며 URL은 최초 직접 진입/reload 시험 외 임의 변경하지 않았다. mock은 날짜·정렬·페이지·검색/사유별로 다른 본문을 반환한다. gate로 보류한 조회는 abort를 무시하고 파싱 완료 후 화면이 바뀌지 않는지 확인한다.

최종 결과는 verification/final 로그/JSON에 기록한다. 단위 491개, 탐색 13개, 운영 기록 32개, 게시글 32개, 상세·댓글 49개모두 통과했고 lint/typecheck/build 및 git diff --check도 통과했다. 수정 전 기록은 verification/before-*에 분리해 최종 통과와 혼동하지 않도록 했다.

추가 검증: 실제 적용 조건 전체와 2페이지 보존, 기본 요청 없음, 미적용 입력 제외, Back/Forward/reload/direct/link-return, 정상/잘못된 복귀 주소, 로그아웃/401 이후 Back 데이터 미노출, 이전 응답 무시. 1440px/320px 상세 복귀 링크 및 복귀 전후 캡처 포함. 초기화 문구 외 추가 디자인 변경 없음.

## 실제 환경과 남은 제한

로컬 8082 관리자 API는 인증정보 없는 연결 확인에서 HTTP 401을 반환했다. 실제 관리자 세션/비밀번호/쿠키를 수집하지 않았으므로 **인증된 Spring 연동 검증은 미실행**이다. dev/production 브라우저 모두 API를 mock으로 대체했다. 실제 계정·역할·콘텐츠 변경 없음.

Back 초기화는 현재 소스에서 재현하지 못했으므로 해결했다고 단정할 수 없다. 실환경에서는 최신 프론트 재시작/새로고침 후 적용된 운영 기록 URL, 상세 URL의 from, 실제 Back 직후 URL과 GET 조건을 비교해야 한다. 여전히 Back만 초기화되면 이 네 값과 실행 프론트 버전이 추가 조사 근거다. 쿠키/토큰/본문은 필요하지 않다.

## 검토 자료

- task-only.patch: 작업 시작 당시 미커밋 코드 대비 이번 변경만.
- pre-existing.patch 및 status-before: 이전 누적 변경 구분. 이전 untracked 소스의 시작 원본도 별도로 포함한다.
- cumulative-from-head.patch: 누적 변경, 이번 작업 diff와 분리.
- verification/before-dev, before-prod: 수정 전 경로별 증거와 실패 원인.
- verification/final/history/*-history.json: 각 단계 URL·API 조건·입력·결과 및 detail-return 목적지.
- verification/final/history/*-before/back/return-link/detail-return.png: 두 복귀 경로 캡처.
- FILES/SHA256SUMS: 파일 목록과 무결성. 이전 ZIP은 보존한다.

.env, 실제 인증정보·쿠키·토큰·개인정보, node_modules, .next, .git, 빌드 산출물은 제외한다. example.test와 mock-only는 가상 테스트 fixture다.
