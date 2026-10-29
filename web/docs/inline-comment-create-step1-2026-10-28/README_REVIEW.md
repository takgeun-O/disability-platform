# 마이페이지 인라인 댓글 작성 1단계

## 기준과 보존
- 실제 브랜치 feat/mypage-activity-redesign, 시작 HEAD 63b12f4041e79d273cba1ed169383fa9ef34abb7.
- 시작 git status는 깨끗했다. 앞선 정렬/전환 애니메이션/상세 조회 취소 및 fixture 보완은 이미 커밋되어 있었다. 이번 작업은 그 위에 추가했다. 백엔드/DB/API 변경, 커밋/푸시/병합/배포 없음.
- 사용자8443 서버는 종료하지 않았다. 테스트는 별도8467/8466 프로덕션 Next와8858/8856 mock API에서 실행했다.

## 확인한 계약과 구조
- CommentController.create: POST /api/v1/community/posts/{postId}/comments, {content}, 인증 principal, 201 {commentId}, no-store. 기존 changeComment가 validateComment 및 CSRF 준비/후속 유효성 검사/응답 ID 검증/오류 분류를 제공한다. 입력은 Java 호환 공백/코드포인트 규칙과2,000자 제한을 사용한다.
- InlineComments는 게시글별 조회 snapshot/controller/sort/version을 관리하고 MyActivity가 회원별 활동 캐시와 스크롤 복원을 담당한다. 댓글 변경 버전은 comments:{postId}, 활동 목록/요약은 list 및 member-comments이다.
- AuthProvider의 기존 활동 캐시는 인증 pending에서 폐기되고 MyPage도 일시적으로 해제된다. 따라서 초안을 이 캐시나 InlineComments 로컬 상태에만 넣으면 로그아웃 실패에도 소실된다.
- NavigationGuard.setDirty는 단일 boolean이며 useUnsavedChanges만으로 확인창 UI가 만들어지지는 않는다. 상세 Comments는 별도 CommunityDialog를 함께 렌더링한다.

## 구현
- lib/comment-drafts.ts: 인증 Provider 인스턴스에만 존재하는 게시글ID→원문 초안 저장소. 같은 owner 재확인은 보존, 확정 anonymous/새 로그인/다른 회원은 폐기. checking/error는 이전 초안을 노출하지 않고 보관하여 확인 복귀 시 유지한다. revision만으로 폐기하지 않는다. 영구 저장 없음.
- AuthProvider.tsx: 인증 알림에서 초안 owner를 동기적으로 갱신한다. 기존 activityCache/like/auth 정책은 변경하지 않는다.
- MyPage.tsx의 DraftProtection: 설정 페이지를 제외한 마이페이지 전체 초안을 합산해 하나의 useUnsavedChanges와 기존 CommunityDialog를 사용한다. 인증 확인 중에도 이 보호 주체는 유지된다. 접기/탭은 이동 알림을 발생시키지 않으며 초안을 보존한다. 승인된 탐색 알림에서는 전체 초안을 폐기한다. 취소는 그대로 보존한다.
- MyActivity.tsx: 초안을 구독해 각 게시글에 전달한다. 성공 후 해당 게시글만 초기화한다. 성공적으로 다시 조회한 이전 게시글 범위에서 사라진 항목만 prune한다. 일시적인 조회 실패/인증 확인 후 첫 페이지 복원만으로 아직 조회하지 않은 다른 페이지 초안을 제거하지 않는다. 실제 영구 삭제가 아직 확인되지 않은 범위는 후속 조회 때 정리된다.
- InlineCommentForm.tsx: 독립적인 제출 소유권/진행/필드 오류/일반 오류. textarea Enter는 줄바꿈, 별도 등록 버튼만 제출. 기존 validateComment/characterCount/changeComment 재사용. 한 게시글 등록 중 다른 게시글 입력 가능. CSRF 완료 후에도 화면/인증/컴포넌트/요청 소유권을 확인한다.
- InlineComments.tsx: 비활성 시안을 실제 폼으로 교체. 고유 useId label/count/error 연결, 현재 조회/정렬/애니메이션/좋아요 로직은 유지.
- 기존 CommentSuccessToast를 MyActivity 단일 status 영역에서 사용한다. 연속 성공은 새 key로 수명 갱신하고 이전 dismiss는 ID 비교로 보호한다. 포커스 이동 없음.

## 성공과 실패
- 유효한201만 성공. 제출한 해당 초안을 비우고 성공 토스트. 새 댓글을 목록에 임의 삽입하거나 댓글 수를 로컬 가산하지 않는다.
- 같은 인증 세션의 확정 성공 또는 불확실 결과는 postCommentsChanged(postId)+postListChanged로 기존 재조회 경로를 사용한다. 다른 게시글 comments 버전은 변경하지 않는다. 정렬과 누적 조회 범위를 기존 구조대로 유지한다.
- 등록 성공 뒤 GET 실패는 성공을 취소하지 않고 조회 오류로 별도 표시한다. 초안 복원/POST 자동 재시도 없음.
- 확정 실패는 초안과 오류를 유지. CSRF 준비 실패는 쓰기 미전송으로 분리. 불확실한 결과는 초안을 유지하고 기존 댓글 새로고침으로 확인할 수 있다. POST_NOT_FOUND는 기존 조회/삭제 확인 정책으로 전달한다.
- 화면 이탈 후에도 같은 인증의 이미 보낸 쓰기 결과는 필요한 데이터 무효화만 수행한다. 이전 화면의 토스트/오류/초안은 변경하지 않는다. 이전 인증 응답은 무효화조차 수행하지 않는다. finally는 자신이 소유한 작업만 정리한다.

## 검증
실행 환경 Node24/pnpm10.34.3. 아래 명령은 web 기준.
- pnpm test: 결과 로그 review/evidence/unit.log.
- pnpm lint, pnpm typecheck: 최종 소스 통과.
- pnpm build: 사용자 .next 보존을 위해 /private/tmp/iyum-inline-build의 동일한 src로 프로덕션 빌드 통과.
- zsh /private/tmp/run-create-browser.sh inline-comments-browser.mjs 8467 inline
- zsh /private/tmp/run-create-browser.sh community-comments-browser.mjs 8466 comments
- zsh /private/tmp/run-create-browser.sh comment-likes-browser.mjs 8466 likes
- git diff --check.
정확한 최종 건수/실패는 ZIP의 review/test-summary.json과 각 results.json에 기록한다. 전체 실행과 중간 실패를 구분해 보존한다.

신규 모의 브라우저 검사: 두 초안 독립성/한쪽 등록, collapse/tab 유지, 한쪽 성공 후 다른 초안의 이탈 보호, 취소/승인 및 뒤로가기에 초안이 폐기되는지,1440/390/320px, 필수/2,001자/한글 문자·줄바꿈, 제출 중 disabled 및 요청1회,403/불확실201/CSRF 준비 실패, 등록 성공+조회503,CSRF/쓰기 지연 중 승인된 이탈,CSRF/쓰기 지연 중 다른 계정 로그인,로그아웃 실패 보존 및 성공 뒤 같은 회원 재로그인 격리. 기존43개 조회·정렬·애니메이션·좋아요·인증 경합도 유지.

## 중간 실패와 한계
- 첫 실행에서는 DraftProtection의 확인창 UI 누락을 발견해 기존 CommunityDialog로 수정했다. Playwright press('가')도 insertText로 수정했다.
- 두 번째 실행에서 뒤로가기로 열린 댓글이 이미 복원됐는데 테스트가 다시 눌러 닫았다. 펼침 상태와 초안 폐기를 직접 기다리고 검증하도록 수정했다. assertion을 삭제하거나 대기 시간을 늘리지 않았다.
- 기존 상세 댓글 logout failure 스크롤 차이 assertion은 이번에도 실패했다. URL/본문/초안/포커스 이후 스크롤 검사에서 중단되며 그 뒤 요청 수/dirty 검증은 해당 실패 분기에서 미실행이다. 허용 오차를 바꾸지 않았다.
- 실제 Spring/DB/실사용 계정 요청은 미실행. 실제 IME 조합기·소프트 키보드·Safari 실기기·화면낭독기는 미검증이며 Chromium 한글 문자 삽입/Enter 및 DOM 접근성 연결 검사와 구분한다.
- 네트워크 단절/모든 서버 오류 코드의 새 폼 전용 조합을 전수 검증한 것은 아니다. 기존 API 단위 및 상세 회귀에 더해 핵심 등록·인증 경합을 검증했다.

## 후속 단계
방금 등록한 댓글 별도 영수증/목록 합류 표시는 구현하지 않았다. 선택 정렬의 기존 조회 범위에 새 댓글이 없을 수 있다. 등록 후 자동 스크롤/위치 강조, 요약·목록 갱신 최적화, 실시간 감지/폴링은 후속이다. 인라인 수정/삭제도 추가하지 않았다. 현재 등록/조회 계약으로 이번 범위 구현이 가능해 백엔드 보완은 필요하지 않았다.

## 최종 결과
- 단위416/416, ESLint/타입 검사/프로덕션 빌드/공백 검사 통과.
- 인라인56/56 통과 (신규13 + 기존43).
- 상세 댓글61/62: 기존 logout failure 스크롤 assertion1건 실패.
- 댓글 좋아요24/25: previous personalized GET rejected after other-account login에서 계정 변경으로 취소된 이전 GET의 response를 기다리다 timeout. 이번 제품 소스의 이전 요청 취소는 기존 Comments 코드 그대로이다.
- 시작 스냅샷의 전체 src를 /private/tmp/iyum-create-baseline에 별도 빌드하여 같은 실패1건만 원래 assertion 그대로 실행한 결과 동일 timeout(0/1). 따라서 이번 초안 저장 구현 이전에도 재현된다. 비교용 임시 필터는 review/baseline-like-target.mjs에만 있으며 최종 저장소 테스트에는 필터가 없다. 이 테스트를 고치거나 약화하지 않았다.
- 비교 환경 최초 빌드는 외부 node_modules symlink가 Turbopack filesystem root 밖이라는 환경 오류로 실패했다. 로컬 의존성 복사 후 baseline 빌드는 통과했다. 실패 로그도 보존한다.
- 초기 인라인48/54(확인창 연결 및 한글 테스트 도구 문제), 두 번째53/56(뒤로가기로 펼침 복원 후 테스트가 다시 닫는 문제), 최종56/56. 중간 결과를 최종 통과로 덮지 않고 별도 폴더에 보존한다.
- 대표 화면은 review/evidence/inline/create-1440.png, create-390.png, create-320.png. 실제 사용자 개인정보가 아닌 모의 데이터다.
