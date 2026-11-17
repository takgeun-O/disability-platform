# 관리자 대시보드 프론트 연동

## 구현과 범위

- `/admin`을 대시보드로 제공한다. 상단 관리자 링크와 사이드바 대시보드 메뉴가 이 경로를 사용한다. 기존 `/admin/posts`, `/admin/operation-logs`, `/admin/members`, `/admin/reports`와 로그인 복귀·권한 가드는 유지한다.
- `admin-dashboard.ts`가 기존 `adminGet`을 사용해 세션 쿠키 포함·no-store GET을 수행한다. 필수·non-null 필드, 음수가 아닌 안전한 정수, 사유 enum, POST/COMMENT, 날짜·시각, 최대 5개 목록을 검증한다. 잘못된 응답을 0·빈 배열로 바꾸지 않는다.
- JavaScript에서 정확히 표현할 수 없는 정수는 기존 관리자 클라이언트와 같이 응답 오류로 처리한다. 서버 int64 계약을 변경하지 않는다.
- 요약 수치는 서버 counts를 그대로 표시하고 대기 전체 건수와 표시 목록 수를 구분한다. 목록은 서버 순서를 유지한다. 동시 변경에 따른 건수·목록 차이를 검증 오류로 취급하지 않는다.
- baseDate는 날짜 문자열 그대로 표시한다. queriedAt·createdAt은 기존 writtenTime 유틸리티로 한국 시간을 표시한다. 클라이언트 시각으로 조회 기준을 대체하지 않는다.
- 신고 이동은 기존 reportHref/defaultReportQuery로 전체 기간·전체 대상·사유 없음·RECEIVED·OLDEST·page=0 URL을 만든다. 상세에는 targetId가 아닌 reportId를 selected로 전달한다. 목록에 없는 신고도 기존 상세 조회가 열린다.
- 새 처리 버튼·폴링·공유 캐시·localStorage·통계 필터·백엔드 변경은 없다.

## 상태와 접근성

- 최초 로딩, 정상 빈 결과, 최초 실패, 재조회 중, 재조회 실패를 구분한다.
- 수동 재조회 실패 시 이전 데이터와 조회 시각을 유지하고 이전 결과임을 알린다. 401은 기존 세션 만료 처리, 403은 데이터 제거 후 권한 안내다.
- 기존 captureMemberRequest/useNavigationRequest와 AbortController로 세션·화면·요청 수명을 확인한다. 화면을 벗어난 응답은 반영하지 않는다. 재진입 시 새 GET을 수행한다.
- 새로고침은 aria-disabled와 요청 잠금으로 중복 실행을 막으며 버튼 포커스를 유지한다. 최초 값 0을 잠시 표시하거나 자동으로 포커스를 이동하지 않는다.
- 데스크톱 표는 열/행 헤더를 유지한다. 모바일은 같은 표 데이터를 카드형으로 표시하고 보조 라벨은 aria-hidden으로 중복 낭독을 피한다.

## 이번 검증

최종 소스 기준:

- `pnpm typecheck`: 통과.
- `pnpm lint`: 오류 0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고 3개.
- `pnpm test`: 516건 통과, 실패·스킵 0.
- `pnpm build`: 통과.
- `node tests/admin-dashboard-browser.mjs`: 13/13 통과, 합성 API.
- `node tests/content-reports-browser.mjs`: 기존 신고 회귀 70/70 통과, 합성 API.
- `BASE_URL=http://127.0.0.1:8469 pnpm test:smoke`: 통과. 실제 로컬 공개 API 및 SSR의 읽기 전용 확인이며 관리자 인증 성공 검증이 아님.
- 실제 `GET http://127.0.0.1:8082/api/v1/admin/dashboard` 비로그인 요청: 401 AUTHENTICATION_REQUIRED.

브라우저: Chrome headless, 1920/1440/390/320px, 높이 960px, America/Los_Angeles 시간대. 한국 날짜 및 시각, 숫자 4개, 전체 7건/표시 5건, 서버 순서, 카드/표, 가로 넘침 없음, 키보드 링크 이동, 관리자 메뉴 활성, 초기·재조회 오류, 401/403, 늦은 응답, 목록 밖·종결 신고 상세, 처리 후 재진입을 확인했다. 최종 스크린샷은 직접 검토했다. 폰트 외부 네트워크는 차단하고 시스템 대체 폰트를 사용했다.

중간 검증에서 발견한 새로고침 버튼 포커스 손실은 disabled 대신 aria-disabled와 요청 잠금을 적용해 해결했다. 비로그인 모의 응답 누락(message·fieldErrors)은 실제 인증 계약에 맞게 수정했다. assertion은 유지했으며 중간 실패 실행은 최종 통과 건수에 합산하지 않는다.

## 실제 관리자 연동 미실행 및 수동 확인

관리자 자격 증명을 제공받지 않아 실제 관리자 세션의 대시보드 200 응답과 브라우저 연동은 미실행이다. 실제 운영·개발 DB의 신고/회원/콘텐츠를 변경하지 않았다. 이번 백엔드 테스트 재실행은 범위 밖이며 직전 백엔드 검증 건수를 이번 결과로 합산하지 않는다.

사용자 로컬 관리자 세션으로 `/admin`에 접속하여 Network의 dashboard 200 응답과 카드·목록·기준 시각을 대조한다. 접수 신고 링크가 기존 날짜·대상·사유 조건 없이 열리는지, 상세 링크가 reportId로 열리는지 확인한다. 신고 처리 후 재진입 확인은 사용자가 정한 테스트 데이터에서만 기존 처리 화면을 통해 수행한다. 실제 계정이나 DB를 임의 변경하지 않는다.
