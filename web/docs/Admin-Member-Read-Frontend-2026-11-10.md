# 관리자 회원 목록·상세 프론트 검토

## 기준과 범위
- 브랜치 feat/admin-member-read, 시작 HEAD/백엔드 기준 21844af7c5293ae3ce808052092e5b2880aced98.
- 시작 시 미커밋 변경은 사용자가 첨부한 docs/design 회원 관리 PNG뿐이었다. 그대로 보존했고 ZIP에는 사용자 파일을 포함하지 않았다.
- 이번 변경은 web 회원 목록·상세 읽기 전용 화면과 관리자 메뉴 1개 추가, 테스트 및 이 문서다.
- 백엔드·DB·인증·CORS·API 계약, 기존 운영 기록/게시글 구현은 변경하지 않았다. 커밋·푸시·PR·배포 없음.

## 화면과 API
- /admin/members: 초기에는 전체 너비 목록. 선택하면 같은 페이지에서 우측 상세. 모달·dim·포커스 트랩 없음.
- 실제 회원 응답의 닉네임과 이메일을 표시. WITHDRAWN 이메일 유지, null 이메일만 ‘이메일 정보 없음’. 단건 응답의 createdAt/updatedAt/withdrawnAt을 KST로 표시.
- GET /api/v1/admin/members 및 /api/v1/admin/members/{memberId}; 기존 adminGet(credentials include, no-store, GET, CSRF 사전 요청 없음) 재사용.
- 목록 예: /api/v1/admin/members?searchType=NICKNAME&keyword=%EC%86%8C%EB%A6%AC&status=ACTIVE&role=USER&sort=OLDEST&page=0&size=20
- 전체 상태/권한 및 빈 검색은 파라미터 생략. 정렬·페이지는 서버 응답을 사용한다.
- MEMBER_ID는 문자열/BigInt 검증만 사용하고 Number로 전송하지 않는다. Java strip, 19자리와 Long 상한, 닉네임100코드포인트, 이메일 소문자·254 UTF16 길이를 적용. 별도 이메일 형식 검사 없음.
- 응답 검증은 null과 누락을 구별하고 enum·시간·페이지 정보·선택 ID 일치도 확인한다.

## 탐색·요청 수명
- 회원 전용 URL에서 searchType/keyword/status/role/sort/page/selected를 복원한다. selected는 목록 API에 전달하지 않는다.
- 적용된 검색과 입력 중 검색을 분리. 상태·권한·정렬 변경은 마지막 적용 검색을 유지하고 page=0, 선택 해제. 페이지 변경도 선택 해제.
- 적용/선택은 Next 네이티브 history.pushState 연동, 명시적 닫기는 현재 회원 목록 주소로 replaceState. 임의 history.go/back 이동 없음.
- 뒤로/앞으로 실제 탐색 수명 변경 시 URL에서 복원. 미적용 입력은 마지막 적용값으로 복원해도 된다는 요청 정책 적용.
- 목록/상세/복사에 독립 요청 scope. effect 수명·AbortController·기존 인증 세션 snapshot·NavigationGuard 수명을 함께 검사. Abort를 무시한 늦은 성공/실패도 검증.
- 401은 기존 session.expire, 403 ACCESS_DENIED/ADMIN_REQUIRED는 공통 HTTP 분류로 관리자 권한 안내. 둘 다 목록·상세 제거. 이전 계정 응답은 세션 소유권 검사로 배제.
- 닫기/좁은 화면 목록 복귀는 현재 회원 버튼으로 포커스와 기억한 목록 스크롤 복원. 실제 버튼이 없으면 회원 관리 제목으로 복원한다. 개인 응답 데이터는 컴포넌트 메모리에만 존재한다.

## 반응형
실제 .admin-members 콘텐츠 폭 <1050px이면 단일 화면으로 전환한다. 선택한 상세를 보여주는 동안 목록/필터는 hidden, 돌아가기는 동일 검색·정렬·페이지를 유지한다. 크기 변경 자체는 URL·API 요청을 바꾸지 않는다.

| 브라우저 너비 | 작업 영역 너비 | 모드 |
|---|---:|---|
|1920|1682|목록·상세|
|1440|1202|목록·상세|
|1280|1042|단일 화면|
|1024|786|단일 화면|
|768|530|단일 화면|
|390|366|단일 화면|
|320|296|단일 화면|
모든 측정에서 가로 넘침 없음. 좁은 목록은 행 내부 라벨을 가진 카드형 표로 표시한다. 화면 캡처는 전부 example.test 기반 모의 회원 데이터다.

## 이번 실제 검증
Node24 및 pnpm10.34.3. 프로덕션 빌드 서버 http://127.0.0.1:8478, 브라우저 API는 Playwright route mock이다. 실제 Spring 관리자 세션 연동 검증이 아니다.

- pnpm typecheck: 통과.
- pnpm lint: 오류0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고3. 신규 소스 경고 없음.
- pnpm test: 497/497 통과, 실패/건너뛰기0.
- pnpm build: 통과.
- BASE_URL=http://127.0.0.1:8478 PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/tools/node_modules/playwright/index.mjs node tests/admin-members-browser.mjs: 기존21건 전체 실행 21/21 통과.
- 같은 실행에 TEST_FILTER='long member fields', MEMBER_ARTIFACT_DIR=/private/tmp/iyum-member-frontend/long-fields: 뒤이어 추가한 긴 필드2건 2/2 통과. 최종 테스트 파일23건 중 이 2건은 별도 실행했으며 합쳐 23개 고유 사례 검증.
- 같은 BASE_URL/PLAYWRIGHT_MODULE로 node tests/admin-modal-return-browser.mjs: 11/11 통과.
- 같은 환경으로 node tests/admin-history-browser.mjs: 13/13 통과.
- git diff --check 통과.

최종 제품 소스 변경 후 build/typecheck/unit 및 브라우저 검증을 실행했다. 이후 추가된 것은 브라우저 테스트2건과 문서뿐이다. Next가 생성한 next-env.d.ts의 경로 변화는 작업 전 내용으로 정리했으며 제품 변경에 포함하지 않는다.

초기 시도 기록은 별도 intermediate에 보관: 개발 Strict Mode effect 재실행의 중복 요청 횟수 실패로 프로덕션에서 검증. 실제 접근성 이름 문제가 확인된 select에 aria-label을 명시했고, Next route announcer를 회원 폼 오류로 잘못 집계하던 테스트는 회원 영역으로 한정했다. 긴 이메일은 복사 버튼과 동일 dd에 있어 exact text 선택 대신 첫 텍스트 노드 원문과 영역 가시성을 함께 확인했다. assertion 삭제/완화나 고정 sleep으로 해결하지 않았다.

## 미검증 및 제한
- 실제 Spring 로그인 세션과 실제 회원 데이터의 UI 연결은 실행하지 않았다. 테스트 계정 자격증명을 사용하지 않았고 운영 데이터 변경 없음. 백엔드 통합 테스트 재실행도 이번 프론트 범위에서 제외.
- 브라우저 검증은 Chrome만 실행. Safari/Firefox, 실제 모바일 기기, 스크린리더 직접 청취는 미실행.
- ID 검색은 Long 최대까지 정확히 보낸다. JSON 응답의 ID/전체 개수가 JS 안전 정수 범위를 넘으면 기존 관리자 응답 정책처럼 잘못된 값을 표시하지 않고 응답 오류로 처리한다. 숫자 JSON을 손실 없이 파싱하는 새 시스템은 추가하지 않았다.
- 회원 상태/권한 변경, 활동/최근 로그인, 날짜 필터는 제공하지 않는다.

## 수동 확인
1. 로컬 Spring 최신 소스 실행 후 관리자 로그인 → 회원 관리 메뉴.
2. 닉네임/전체 이메일/회원 ID 검색, 상태·권한·정렬 변경, 2페이지 이동 후 회원 선택.
3. 닫기, Back/Forward, 새로고침으로 조건/선택 확인. 1280px 이하에서는 목록 복귀 후 위치 확인.
4. WITHDRAWN 저장 이메일, DELETED null 이메일과 상세 시각 확인. 복사 후 안내 확인.
5. 실제 데이터에 변경 요청 없이 읽기만 수행한다.

## 파일 구성
- 이번 파일은 원래 저장소 상대 경로 유지. 관련 공통 코드는 읽기 검토용으로 포함하며 수정하지 않았다.
- diff/this-task.patch는 시작 HEAD 대비 이번 변경 + 신규 파일 전체. 기존 변경은 사용자 이미지뿐이므로 이 diff에서 제외.
- verification/final은 이번 실행 로그/결과, verification/intermediate는 완료 결과로 집계하지 않은 초기 시도.
- screenshots는 테스트 데이터 화면, measurements는 실제 콘텐츠 폭·위치 기록.
- FILES.txt 및 SHA256SUMS.txt로 내용 확인 가능.
