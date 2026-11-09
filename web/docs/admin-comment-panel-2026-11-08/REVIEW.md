# 관리자 상세 패널 댓글 연동 검토

## 기준과 범위
- 브랜치: feat/admin-operation-log
- 기준 HEAD: bbacb9d2ce73fe0869f1263032b455e5bbfb828a
- 작업 시작 git status는 깨끗했다. 이전 목록/상세 및 스크롤 개선은 이 HEAD에 포함되어 있으며 변경을 되돌리지 않았다.
- 프론트 소스·테스트·이 문서만 수정. 백엔드/DB/마이그레이션/API 변경, 커밋/푸시/배포 없음. 삭제 파일 없음.

## 구현과 재사용
- AdminCommentPreview.tsx (추가): COMMENT 기록 선택 시 targetId로 단건 조회, 현재 댓글 영역과 상태/시각/작성자, 오류/재시도, 응답 postId의 부모 관리자 상세 링크.
- AdminLogPreview.tsx: 댓글 원문 미지원 안내를 실제 단건 조회 영역으로 교체. 당시 기록은 그대로 유지.
- AdminPostPreview.tsx: 게시글/댓글 탭. 댓글 첫 진입에만 AdminComments 마운트. 같은 선택에서 탭 전환 시 마운트를 유지해 조건과 페이지가 남는다. 조치 revision 갱신 중 기존 게시글 데이터와 댓글 탭을 유지하되 최신 부모 정보를 확인할 때까지 조치를 잠근다. 404는 데이터 제거.
- AdminComments.tsx: 기존 다중 상태 칩(200ms), 검색, 페이지, 이전 결과 유지, 조치 모달/CSRF/사유 정책/페이지 보정을 그대로 재사용. readOnly 옵션만 추가.
- admin-detail.ts: AdminCommentDetail 타입, 공유 댓글 검증, 단건 GET. 명시적 nullable 필드와 ID/상태/본문/작성자/시각 검증, 요청 commentId 일치 확인.
- admin.css: 패널 전용 탭과 댓글 필터/항목 배치. 기존 sticky 헤더와 패널 단일 스크롤을 유지하고 새 중첩 스크롤을 추가하지 않음. 긴 본문의 scrollIntoView/키보드 포커스가 바깥 패널의 overflow:hidden까지 스크롤하여 제목을 가릴 수 있음을 캡처에서 확인했다. 바깥 패널은 overflow:clip으로 변경하여 프로그램에 의한 스크롤을 막고 실제 내용 영역만 스크롤하게 했다. 닫기의 실제 뷰포트 좌표와 바깥 패널 scrollTop=0을 추가 검증했다.
- admin-detail.test.mjs, admin-comment-panel-browser.mjs: 계약 및 신규 연동 회귀.
- admin-master-detail-browser.mjs, admin-operation-logs-browser.mjs: 기존 댓글 연결 미지원 기대값을 새 단건 조회 계약에 맞춰 보강.

운영 기록은 읽기 전용이다. POST 기록의 현재 게시글에도 댓글 탭은 제공하지만 숨김·복원 버튼은 제공하지 않는다. 조치는 게시글 관리 패널 또는 기존 상세 관리 페이지에서 수행한다. 단건 조회에서 공개 원문 URL은 생성하지 않는다.

## 확인한 계약
문서 admin-comment-detail-read.md와 실제 AdminCommentDetailReadController / AdminCommentDetailResponse / AdminCommentDetailResult를 확인했다.
- GET /api/v1/admin/comments/{commentId}: 댓글 ID는 기록 targetId. 본문, 작성자, 댓글 상태, postId/postStatus 및 시각을 반환.
- GET /api/v1/admin/posts/{postId}/comments: 게시글 상세의 실제 postId 사용. 반복 statuses, keyword, page, size=20. 서버 등록순 및 totalElements 사용. 목록 항목마다 단건 조회하지 않음.
- 공통 adminGet의 credentials=include, cache=no-store, GET, 타임아웃 재사용. GET CSRF 발급 없음.
- contentUpdatedAt은 null 또는 유효한 시각이며 누락과 null을 구분. null은 기존 AdminCommentMeta의 '기록 없음'. updatedAt으로 보충하지 않음.
- 상태 VISIBLE은 '정상'. 부모 상태는 별도 배지와 숨김/삭제 공개 제한 안내. 탈퇴 작성자의 서버 nickname/withdrawn 사용. 본문은 React 일반 텍스트이며 줄바꿈 보존.
- 단건 API 문서와 DTO에 충돌 없음. 기존 admin-comment-read.md 예시에는 contentUpdatedAt이 생략되어 있으나 전용 contentUpdatedAt 문서 및 실제 DTO에 있는 필수 nullable 계약을 우선했다. 다중 상태 문서의 '대기 중 결과 행 숨김' 문장은 기존 최신 프론트의 이전 결과 유지 방식과 차이가 있어, 요청한 현재 재조회 안정화 동작을 보존했다. API 계약/백엔드 문서는 수정하지 않음.

## 오류, 인증, 경합
- 단건 404: '현재 댓글 정보를 찾을 수 없습니다.'; 기타 네트워크/서버/형식 오류는 영역 내 실패와 재시도. 기록 정보는 유지.
- 401은 캡처한 회원 요청의 expire(), 두 종류의 403은 공통 deny()로 관리자 데이터 제거. 별도 인증 알림 체계 없음.
- 각 요청은 AbortController + 해당 컴포넌트의 NavigationRequestScope + 회원 요청 수명 + moderation revision 검사를 통과해야 반영된다. 패널 선택은 기존 logId/postId key로 마운트 수명을 분리한다. 오류도 동일 검증.
- 테스트 전송층은 admin 요청의 abort를 무시하고, 보류 응답을 실제 해제한 뒤 응답 소비 및 React 프레임 이후 상태를 검사했다. 선택 변경/닫기/로그아웃/다른 회원 로그인/화면 이동 후 늦은 200·401과 선택 변경 후 403·503 포함.
- 댓글 조치와 목록 GET의 기존 수명 분리 유지. 조치 성공 revision으로 현재 댓글 조건 재조회; 마지막 페이지 1개가 필터에서 빠지면 한 번 유효 페이지로 보정. 오류에서는 낙관적 상태 변경/자동 POST 반복 없음.

## 탐색 및 접근성
- 부모 게시글 링크는 응답 postId와 기존 from=encodeURIComponent(returnHref)를 사용. 기존 검증된 출발 운영 기록 주소에 필터/날짜/정렬/page/selected logId가 포함된다.
- 실제 링크 → goBack/goForward → 화면의 운영 기록으로 복귀에서 URL, API 조건, 선택을 확인했다.
- 댓글 탭/조건은 해당 패널 인스턴스 내에서만 유지한다. 다른 게시글·다른 기록 선택, 패널 종료, 상세 페이지 이동 후 재마운트 시 기본 게시글 탭/댓글 기본 조건으로 초기화한다. 다른 대상의 조건을 복원하지 않는다. 목록 조건과 선택은 기존 URL 정책으로 유지.
- role=tablist/tab/tabpanel, aria-selected/controls/labelledby, roving tabIndex, 좌우/Home/End 키 지원. 탭 선택으로 문서 스크롤을 강제 이동하지 않음. 1440/320에서 scrollY 비교 확인.

## 최종 검증
Node 24.21.0, pnpm, Next production server 127.0.0.1:8479, Chromium mock API. 기존 사용자 개발 서버 8443은 유지.

| 명령 (web에서) | 결과 |
|---|---|
| pnpm lint | 성공 |
| pnpm typecheck | 성공 |
| pnpm test | 493/493 성공 |
| pnpm build | 성공 |
| node tests/admin-comment-panel-browser.mjs | 27/27 성공 |
| node tests/admin-master-detail-browser.mjs | 26/26 성공 |
| node tests/admin-history-browser.mjs | 13/13 성공 |
| node tests/admin-posts-browser.mjs | 32/32 성공 |
| node tests/admin-operation-logs-browser.mjs | 32/32 성공 |
| node tests/admin-detail-browser.mjs | 49/49 성공 |
| node tests/admin-moderation-browser.mjs | 31/31 성공 |
| node tests/admin-panel-scroll-browser.mjs | 10/10 성공 |
| pnpm test:smoke | 기존 /community 문구 기대값 불일치로 실패 |

브라우저 고유 사례 합계 220/220. 환경변수 및 전체 실행 명령은 verification/commands.txt 참조. 최종 제품 코드에 대해 다시 실행한 로그이며 이전 작업 로그를 재사용하지 않았다. 최초 신규 경합 테스트에서 401/403/503이 JSON을 읽지 않는 공통 API 동작을 놓쳐 대기가 실패했다. 테스트 완료 관측을 비200 fetch 응답에도 추가하여 보완했다. 운영 기록 기존 테스트는 선택한 댓글 GET도 금지하던 이전 정책을 기대하여 한 건 실패했으며, 선택 전 추가 호출 0/선택 후 정확한 댓글 경로 1회/부모 경로 추측 없음으로 강화했다. 초기 실패 로그는 final과 별도 구분한다.

27개 신규 브라우저 사례: PC/320 현재 댓글 및 부모 링크/이력 복귀, HIDDEN/DELETED·부모삭제·탈퇴·본문수정시각, 404/503 재시도, 늦은 성공/오류, 401/403 전체 정리, lazy 탭·키보드·필터·검색·페이지·대상초기화, 댓글 숨김/해제·마지막페이지 보정·읽기전용, 삭제 제한, 종료/계정변경/이동 후 늦은응답, 이전 댓글 페이지와 새 검색 경합·실패재시도, 사유 오류 유지/POST중복없음, 긴 댓글 끝과 부모 링크 실제 뷰포트 접근을 확인했다.

## 제한 및 수동 확인
- 실제 Spring 관리자 로그인/실데이터 연동은 수행하지 않았다. 사용자 계정·콘텐츠 변경 없음. mock 통과를 실제 서버 검증으로 주장하지 않는다.
- 브라우저 wrapper는 외부 Google 폰트 요청을 차단한다. 실제 배포 폰트 및 Chromium 외 브라우저 검증은 미실행.
- smoke는 / 성공 후 /community의 옛 '질문과 경험을 함께' 기대값으로 중단된다. 현행 공개 화면은 '서로의 질문과 경험을 나눠요.'이며 해당 파일은 HEAD 대비 변경 없음. 후속 smoke 단계는 미실행. 무관한 assertion을 수정하지 않았다.
- 현재 댓글은 조치 당시 스냅샷이 아니다. 과거 본문은 API가 없으므로 제공하지 않음. 본 작업을 위해 필요한 백엔드 변경은 발견하지 못함.
- 수동으로 COMMENT 기록 선택 → 현재 댓글/부모 상태 확인 → 부모 상세 이동/복귀, 게시글 패널 댓글 탭 → 검색/필터/페이지 → 승인된 테스트 콘텐츠의 조치를 확인할 수 있다. 운영 기록에서 조치 버튼이 없는지 확인한다.

## 검토 자료
README, task-only.patch, 기준 SHA/작업 전후 status, 변경·신규 목록, 관련 공통 코드/API 문서/읽기용 DTO, 최종 실행 로그, PC·320 댓글 상세/탭 캡처, 긴 댓글 상단/하단 캡처, 파일목록/SHA256 포함. 작업 시작 상태가 clean이라 task-only와 HEAD 기준 diff는 동일 범위다. 실제 인증정보·환경변수·세션·빌드 산출물은 제외한다. 테스트의 mock-only 토큰과 mock@example.test 등은 가짜 fixture다. 기존 ZIP은 보존한다.
