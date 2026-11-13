# 신고 접수 및 관리자 신고 관리 프론트

기준: feat/content-report / cae07bb. 백엔드·DB·인증 정책은 변경하지 않는다.

## 연결 API

- 공개 게시글/댓글: `POST /api/v1/community/posts/{postId}/reports`, `POST /api/v1/community/posts/{postId}/comments/{commentId}/reports`.
- 관리자: `GET /api/v1/admin/reports`, `GET /api/v1/admin/reports/{reportId}`, `POST .../{reportId}/resolve`, `POST .../{reportId}/dismiss`.
- 변경 요청 전에 기존 `getCsrfContext`로 동일 세션의 토큰을 확인한다. 모든 요청은 credentials를 포함한다. POST를 자동 반복하지 않는다.
- 계약 근거: 루트 docs/api/content-report-create.md, admin-report-read.md, admin-report-handling.md 및 현재 Controller/DTO/정책 클래스.

## 화면과 정책

공개 게시글·댓글의 보조 신고 버튼에서 기존 CommunityDialog 기반 폼을 연다. 본인 콘텐츠 버튼은 표시하지 않는다. 비로그인은 로그인 안내, 인증 확인 실패는 재확인으로 구분한다. 성공 201과 중복 409는 서로 다른 문구를 사용한다. 서버가 제공하지 않는 내 신고 여부를 조회한 것처럼 표시하지 않는다.

접수 상세 사유는 Java String.strip과 동일한 기존 normalizeTitle을 사용한다. 관리자 처리 사유는 Unicode White_Space 기준으로 양끝만 제거한다. 모두 정규화 후 코드 포인트 수를 센다. 기타 상세는 필수이며 일반 사유 상세는 선택이다. 500자를 넘거나 필수 사유가 비면 POST하지 않는다.

관리자 메뉴 `/admin/reports`는 기존 AdminAccess를 사용한다. 화면 기본값은 RECEIVED이며 전체 상태를 선택하면 API status를 생략한다. UI URL의 status=ALL은 전체 선택 복원용이며 API로 보내지 않는다. targetType, reasonType, 접수 기간, sort, page와 selected를 URL에 보존하며 selected는 목록 API에 전달하지 않는다. 기간 입력은 기간 적용 시 반영한다. 필터·정렬 변경은 다른 적용 조건을 유지하고 page=0 및 선택 해제를 수행한다.

선택은 같은 페이지의 오른쪽 비모달 상세로 표시한다. 좁은 화면에서는 목록/상세를 전환하며 작성한 사유를 유지한다. 상세에는 신고 당시 스냅샷과 현재 콘텐츠를 별도 표시한다. 현재 대상이 없더라도 신고 자체가 RECEIVED이면 처리할 수 있다. 댓글의 현재 상태는 부모 게시글 상태로 해석하지 않는다. 댓글은 소속 게시글의 댓글 관리로 연결하며 댓글 ID를 게시글 ID로 사용하지 않는다.

resolve/dismiss는 해당 신고의 검토 결과만 기록한다. 성공 또는 409 후 목록과 상세를 재조회한다. 성공 후 재조회 실패는 별도 안내하고 재시도는 GET만 실행한다. 결과 불확실 시 처리를 잠그고 최신 정보를 확인한다. 서버 페이지 범위를 벗어나면 마지막 유효 페이지로 한 번 보정하며 선택 신고는 유지한다. 필터에서 제외된 처리 상세에는 맥락 안내를 표시한다.

요청에는 세션 소유권과 NavigationGuard 요청 수명을 적용한다. 승인된 이동/계정 변경 이후 응답은 반영하지 않는다. 401 또는 관리자 권한 거부 시 보호된 데이터를 제거한다. 입력 중 이탈은 기존 확인창으로 보호하고 요청 중에는 이동을 막는다.

## 탐색 및 화면 보호

Next 내부 history.state를 그대로 push/replace에 전달하면 __NA 때문에 useSearchParams 갱신을 건너뛸 수 있다. 신고 화면은 앱 메타데이터만 전달하며 기존 공통 NavigationGuard를 수정하지 않는다. 명시적 닫기는 현재 신고 목록 URL에서 selected만 제거하는 replace이며 임의 history.go를 사용하지 않는다. 콘텐츠 관리의 허용된 복귀 주소에 정규화된 `/admin/reports`를 추가했다.

동일 조건에서 선택만 바뀔 때는 세션 범위의 마지막 목록을 표시해 로딩으로 목록 높이가 무너지지 않게 한다. 새 조건의 결과로 오인하지 않도록 갱신 안내와 조작 잠금을 사용한다. 상세는 매 선택마다 새 요청으로 시작한다. 긴 본문은 일반 텍스트로 렌더링하고 패널 본문 한 곳에서 스크롤한다.

## 검증과 한계

기존 node:test/Playwright 기반 검증을 사용한다. 신규 tests/content-reports.test.mjs 및 content-reports-browser.mjs에 API payload, 정규화, nullable 응답, 신고 접수·처리, 오류·경합·이탈·페이지 보정·반응형 시나리오가 있다. 정확한 실행 수와 최종 캡처는 이번 검토 ZIP의 README_REVIEW.md와 로그에 기록한다.

실제 Spring 서버의 관리자/사용자 세션으로 신고를 생성하거나 처리하지 않았다. 브라우저 검증은 모의 API와 SSR 공개 조회 fixture 기반이며 실제 CORS, DB 저장·잠금 검증을 대체하지 않는다. 운영 회원·콘텐츠는 변경하지 않았다.

로컬 수동 확인: 전용 테스트 계정으로 다른 테스트 회원의 공개 게시글/댓글 신고 → 관리자 신고 관리의 접수 목록/스냅샷 확인 → 처리 완료 또는 기각 → 목록 제외/저장된 처리 정보 확인. 동일 신고자의 재신고가 409인지 확인하고, 콘텐츠 공개 상태가 처리만으로 바뀌지 않는지 확인한다. 기존 운영 콘텐츠를 검증 대상으로 사용하지 않는다.
