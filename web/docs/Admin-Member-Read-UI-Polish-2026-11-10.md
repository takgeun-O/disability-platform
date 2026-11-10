# 회원 관리 UI 후속 보완 검토

## 기준과 보존
feat/admin-member-read / HEAD 21844af7c5293ae3ce808052092e5b2880aced98, 2026-11-10.
기존 미커밋 회원 조회 구현·후속 검색/상세 보완 및 백엔드 Swagger 변경은 그대로 보존했다. 이번 작업은 화면 설명, 배지, 열 너비, 관련 테스트와 문서 보완이다. API 클라이언트, 검색 정규화/검증, 인증/탐색 공통 코드, 백엔드 실행 코드·DB·enum·세션은 변경하지 않았다. 커밋/푸시/PR 없음.

## 변경 파일과 목적
- web/src/features/AdminMembers.tsx: 입력 중 검색 기준에 맞는 도움말, 닉네임 표시 이름 안내, 도움말과 오류를 함께 aria-describedby로 연결. PENDING/WITHDRAWN/DELETED 설명을 프로필 바로 아래로 이동, DELETED 문구 변경. 정상 상태 설명 없음.
- web/src/styles/admin-members.css: ID 열 76px, 상태 124px, 권한 94px, 가입일 110px, 회원 열은 나머지 공간. PENDING 옅은 파랑, WITHDRAWN 차분한 보라, DELETED 중립 회색, 권한 낮은 강조. 프로필 긴 이름의 min-width 보완. 기존 정상/제한/정지 색상 유지.
- web/tests/admin-members-polish-browser.mjs: 신규 사용자 흐름/접근성/실제 좌표 검증 6건.
- web/tests/admin-members-followup-browser.mjs: 요청된 DELETED 문구 기대값 변경, 기존 회귀 assertion 유지.
- docs/api/admin-member-read.md: 이미 있던 저장 닉네임과 표시 이름의 구분에 검색어 비우기 + DELETED, AND 조건, 자동 변환/삭제 없음만 보충.
- web/docs/Admin-Member-Read-UI-Polish-2026-11-10.md: 이번 결과 기록.

## 유지한 정책
검색은 저장 닉네임 포함 검색/이메일 전체 일치/회원 ID 일치 규칙 그대로다. ‘탈퇴한 회원’은 표시용 이름이며 검색 조건으로 변환하지 않는다. 상태/권한 변경 시 마지막 적용 검색어를 유지한다. 미적용 입력은 적용 조건 표시와 구분한다.
일반 탈퇴는 즉시 DELETED로 계정 식별 정보를 정리하되 회원 행/ID 유지. WITHDRAWN은 예약 상태. 이번 작업에 탈퇴 실행·복구·물리 삭제·상태 변경 기능은 없다. 이메일 null이면 복사 없음.

## 이번 실행 결과와 명령
web/에서 Node24, pnpm10.34.3.
- pnpm typecheck: 통과
- pnpm lint: 오류0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고3
- pnpm test: 498 통과, 실패/skip0
- pnpm build: 통과
- BASE_URL=http://127.0.0.1:8478 node tests/admin-members-polish-browser.mjs: 프로덕션 mock 6/6
- BASE_URL=http://127.0.0.1:8443 node tests/admin-members-polish-browser.mjs: 개발 mock 6/6
- BASE_URL=http://127.0.0.1:8478 node tests/admin-members-browser.mjs: 기존 mock 23/23
- BASE_URL=http://127.0.0.1:8478 node tests/admin-members-followup-browser.mjs: 기존 mock 10/10
브라우저는 PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/tools/node_modules/playwright/index.mjs를 사용했다. 합계45건은 이번 실제 실행이며 이전 작업 결과를 합산하지 않았다.
- BASE_URL=http://127.0.0.1:8478 pnpm test:smoke: 결과는 evidence/smoke.log 참조. 공개 Spring 읽기와 SSR 검증이며 관리자 회원 API 실세션 검증이 아니다.
처음 개발 실행은5/6: 잘못된 ID 요청 검증이 초기 GET을1회라고 가정해 Strict Mode의 초기2회 조회에서 실패. 기준을 제출 직전 요청 수로 바꿔 추가 GET0회를 정확히 확인했다. 검증을 제거하거나 허용 오차를 늘리지 않았다. 최초 로그와 최종 로그를 모두 보관한다.

## 화면·측정 확인
1920/1440 전체·분할, 390/320 카드형 목록과 단독 상세를 확인. 긴 ID/연속 닉네임/긴 이메일은 텍스트를 보존하며 줄바꿈, 페이지와 각 셀 가로 넘침 없음. 1440 분할 회원 열263.1875px, ID76px. 숫자와 캡처는 browser/long-*.json/png에 있다. 기본 데이터의 전체 목록은 regression/list-1920.png, 정상/가입 대기/탈퇴 완료는 browser/ACTIVE-detail.png, PENDING-detail.png, DELETED-detail.png. 테스트 fixture만 사용했으며 실제 회원 개인정보는 없다.

## 한계·수동 확인
브라우저는 mock API 검증이며 실제 DB 닉네임 검색 정책 검증이 아니다. 테스트 관리자 자격 증명이 없어 관리자 실세션의 닉네임/이메일/ID/DELETED 읽기는 미실행. 로컬 테스트 관리자로 로그인해 기존 회원만 읽기 조회하고 Network 파라미터와 결과를 확인할 수 있다. ‘탈퇴’ 검색 유지 상태에서 DELETED를 선택해도 keyword가 유지되는지, 검색어를 비우면 탈퇴 완료 목록인지 확인한다. 회원 변경/탈퇴는 하지 않는다. 이번에 실제 OS IME 추가 검증은 하지 않았으며 기존 합성 조합 이벤트 회귀는 실행했다.

## 자료 구조
sources/는 최종 파일의 저장소 상대 경로를 유지한다. task-only.patch는 이번 시작 스냅샷 대비 변경만 담고, 기존 untracked 구현을 전부 이번 신규 코드로 표시하지 않는다. related 소스는 검토용으로 포함했다. 시작/종료 git status로 기존 변경과 구분한다. 기존 ZIP은 보존했다. next-env.d.ts 빌드 경로 차이는 시작 시점(HEAD와 동일)으로 복원했다. API/보안 코드 변경 없음.
