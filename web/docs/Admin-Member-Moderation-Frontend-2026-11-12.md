# 관리자 회원 운영 프론트 연동 (2026-11-12)

기준: `feat/admin-member-moderation-ui`, `3be2ed41990c0c9d59ac4be331a20fabf59c8736`의 기존 회원 조회 화면을 확장했다. 백엔드·DB·인증 정책 변경은 없다.

## 화면과 계약

- 기존 목록/상세 너비와 검색·적용 조건·URL·선택·복귀 구조를 유지한다. 상세의 이메일 다음에 회원 운영, 가입·변경 정보 다음에 회원별 조치 이력을 배치한다.
- USER ACTIVE는 정지, USER SUSPENDED는 해제를 제공한다. 자기 자신/ADMIN/그 외 상태는 제한 이유를 표시한다. 최종 판단은 서버가 수행한다.
- 사유는 기존 `normalizeTitle`의 Java `String.strip` 공백 집합을 재사용한다. 내부 줄바꿈과 NBSP를 보존하고 정규화 후 1~500 코드 포인트를 검증한다. UTF-16 length나 maxlength로 입력을 자르지 않는다.
- POST `/api/v1/admin/members/{id}/suspend`, `/lift-suspension`: `{reason}`만 전송한다. 기존 CSRF 발급 함수와 credentials:include를 사용하고 POST를 자동 재전송하지 않는다.
- GET `/api/v1/admin/members/{id}/moderation-logs?page=0&size=20`: GET에 CSRF 발급이 필요하지 않으며 sort를 추가하지 않는다. 저장된 이전/이후 상태·전체 사유·관리자 ID·한국 시간을 표시한다. 이력 페이지는 회원 목록 페이지와 별개다.
- 상세의 단일 본문 스크롤과 기존 좁은 화면 전환을 유지한다. 시안처럼 목록 카드를 상세 높이까지 늘리지 않는다. 기존 페이지 이동 컴포넌트를 사용하므로 시안의 고정 페이지 숫자를 재현하지 않는다.

## 결과와 복구

- changed=true/false를 구분한다. 성공하면 사유를 정리하고 목록/상세/이력 첫 페이지를 다시 조회한다.
- 현재 상태 필터에서 제외된 회원은 목록 서버 결과에서 빠지지만 선택 상세를 유지하고 이유를 안내한다.
- 마지막 페이지가 범위를 벗어나면 유효한 페이지를 한 번 보정한다. 보정 응답과 URL을 함께 반영하고 동일 GET을 추가하지 않는다. Next의 내부 `__NA`를 직접 재전달하지 않고 기존 History 래퍼가 자체 이력을 보존하도록 한다.
- 성공 후 조회 실패와 POST 실패를 구별한다. 최신 확인이 필요한 동안 추가 조치를 막고 GET만 재시도한다. 이전 조치의 성공 상태는 새 조치 시작 시 초기화한다.
- INVALID_REASON은 필드 오류/포커스로, CSRF 및 통신 오류는 별도 오류로 표시한다. 자기 자신·ADMIN 대상 403은 관리자 권한 상실과 구별한다. 404/409는 최신 목록·상세를 조회한다. 401 또는 관리자 권한 거부는 기존 관리자 데이터 제거 정책을 따른다.

## 입력과 요청 수명

- 입력은 이 화면의 메모리에만 보관한다. 취소/닫기/다른 회원/검색/메뉴/Back/Forward는 기존 NavigationGuard와 폐기 확인을 사용한다. 빈 입력은 확인을 생략하고 제출 중에는 이동 제한 이유를 안내한다.
- 목록 GET, 상세 GET, 이력 GET, POST의 요청 세대를 구분하고 현재 인증 소유자·선택 ID·화면 수명을 확인한다. 늦은 결과로 새 로그인 상태를 만료시키지 않는다. 브라우저 요청 중단은 서버 조치 취소를 의미하지 않는다.
- 이력은 이전 상태를 현재 회원 상태로 다시 계산하지 않는다. 다른 회원 선택 시 이전 사유/이력이 노출되지 않도록 소유 ID로 구분한다.

## 검증 방법과 한계

- `pnpm typecheck`, `pnpm lint`, `pnpm test`, `pnpm build` 및 회원 조회/운영 브라우저 테스트를 사용한다. 실제 실행 건수와 로그는 이번 검토 ZIP의 README 및 logs에 기록한다.
- 브라우저 검증은 production 빌드 + Playwright Chrome + mock API이다. 1920/1440/1280/390/320px에서 너비 유지, 넘침, Tab 포커스, 실행 버튼의 실제 위치와 가림 여부를 확인한다.
- 실제 Spring 세션·DB 조치는 실행하지 않았다. 실제 관리자 자격 증명을 사용하거나 운영 회원 데이터를 바꾸지 않았다. 실서버의 보안·트랜잭션 결과까지 프론트 mock 테스트로 검증했다고 볼 수 없다.
- 공유 화면은 example.test 주소와 합성 회원 정보만 사용한다. 테스트의 mock-only 토큰과 테스트 로그인 문자열은 유효한 인증정보가 아니다.

### 이번 최종 실행 결과

- `pnpm typecheck`: 통과.
- `pnpm lint`: 오류 0, 기존 `admin-action-workspace-browser.mjs`의 미사용 변수 경고 3건.
- `pnpm test`: 504/504 통과 (이번 신규 단위 테스트 6건 포함).
- `pnpm build`: 통과.
- `admin-member-moderation-browser.mjs`: 34/34.
- `admin-members-browser.mjs`: 23/23.
- `admin-members-followup-browser.mjs`: 10/10.
- `admin-members-polish-browser.mjs`: 6/6.
- 브라우저 총 73/73, skip 0. 이전 작업의 통과 결과를 합산하지 않고 최종 빌드에서 재실행했다.
- `git diff --check`: 통과.
