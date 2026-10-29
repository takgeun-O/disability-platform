# 댓글 표시 공통화 — 2026-10-28

## 기준과 범위
- branch: feat/mypage-activity-redesign
- HEAD: 416fabd176918ef32d2cb33798c603024ad57b02
- 시작 시 마이페이지 설정 분리/목록 관리, PostEdit busy 방어, 상대 시간 소스·테스트·문서가 미커밋 상태였다. status-before 및 existing-at-start.patch로 구분한다. 해당 기존 작업을 보존했다.
- 이번 제품 변경은 `web/src/components/community/CommentItem.tsx` 신규, `Comments.tsx`의 행 렌더링 위임뿐이다. UI/스타일/API/상태 로직은 변경하지 않았다.
- 추가 검증: `web/tests/comment-items-browser.mjs`, `web/tests/fixtures/comment-items.tsx`. 이 문서와 실행 자료를 새 폴더에 추가했다.
- 백엔드, 실제 데이터, 브랜치, 커밋, staging 변경 없음. 무관한 디자인 PNG와 과거 검증 문서는 보존했다.

## 확인한 API 계약
근거: `server/iyum-api/src/main/java/io/github/takgeun/iyum/community/comment/`의 `api/CommentController.java`, `api/CommentLikeController.java`, `application/CommentReadService.java`, `infrastructure/CommentRepository.java`, `domain/CommentInputPolicy.java`; 프론트 `web/src/lib/community-comments.ts`.
- GET /api/v1/community/posts/{postId}/comments: createdAt ASC, id ASC. 커서는 afterCreatedAt/afterId 한 쌍, 서버 기본20/최대50, 프론트20. hasNext이면 마지막 반환 항목의 createdAt/commentId, 종료 nextCursor=null. 원본 마이크로초 문자열을 URLSearchParams로 전달한다.
- POST content: 201 + commentId. PUT content, DELETE: 204 빈 응답. 좋아요 PUT/DELETE /{commentId}/like: 200 + postId/commentId/likeCount/liked 검증. 서버 목록에는 본문·작성자·createdAt/updatedAt·likeCount/liked가 포함된다.
- 공개 GET, 브라우저 include+no-store; 서버 초기 댓글 GET은 인증쿠키 미전달. 쓰기는 CSRF API가 준 헤더/토큰을 사용하고 전송 전 유효성을 재검사. 입력은 Java 호환 공백/2,000 코드포인트 검증.
- 401 인증, 403 CSRF/권한, POST_NOT_FOUND/COMMENT_NOT_FOUND 구별. 네트워크/손상 응답/5xx 쓰기는 불확실 상태로 안내하고 자동 재전송하지 않는다.
- 현재 API로 다음 단계 게시글별 조회·커서 추가 조회·작성 가능. 추가 API 계약은 이번 준비에 필요하지 않다. 특정 댓글 바로 조회 API는 없으며 현재 상세 해시 이동은 커서를 순회한다.

## 이번 공통 경계
`CommentItem`은 li, 작성자, WrittenTime, 수정됨, 본문, 반응/작업 도구줄만 렌더링한다. API/인증/폼/모달/dirty/초안/타이머를 자체 생성하지 않는다. body/reactions/actions 슬롯으로 기존 부모의 수정 폼과 CommentLikeButton·관리 버튼을 받는다. 조회 전용 사용은 슬롯 없이 가능하다.
- default DOM ID는 useId 기반이라 같은 commentId의 복수 표시도 충돌하지 않는다. 시간 팝업과 좋아요의 설명/모달 ID는 기존 각 컴포넌트 useId를 사용한다.
- 상세는 anchorId=comment-{commentId}를 명시해 기존 공개 URL과 tabIndex=-1, 포커스 및 CSS를 유지한다. 원래 li/메타/본문/도구줄 DOM 구조와 클래스가 동일하다. 실제 상세에서 추출 컴포넌트를 사용한다.
- React client component도 SSR한다. 원본 페이지가 readPublicComments를 await한 뒤 PostDetail에 초기값을 넘기는 흐름을 보존했다. WrittenTime은 서버/첫 렌더 절대 날짜 후 공통 시계로 전환한다.

## 상세에 남긴 책임 및 충돌 조사
`Comments.tsx`: initial page, postId, reads/writing/pageCount, buffered cursor, draft/editor, receipts, removeItem, mutation/refresh, toast, navigation와 owner 확인은 그대로 유지했다. 별도 목록 훅으로 복제하지 않았다.
- comments-title, comments-refresh, comment-draft/count/auth-help, comment-edit-{id}/error/button, receipts/detached 제목, login/delete/discard/leave dialog IDs는 상세 전용 고정 네임스페이스다. 전체 Comments를 여러 번 사용하면 충돌한다. 이번 공통 표시에는 이 ID나 작성 폼이 없다.
- Comments의 hashchange/pointerdown/keydown 및 document.getElementById 이동과 PostDetail의 #comments-title 처리는 상세에만 남겼다. 복수 인라인 영역에 이 전역 해시 처리를 복제하면 안 된다.
- NavigationGuard.useUnsavedChanges는 setDirty(boolean)와 cleanup(false)를 사용한다. 여러 폼이 직접 등록하면 마지막 갱신/언마운트가 다른 초안을 덮어쓸 수 있다. 이번에는 변경하지 않았다.
- AuthProvider.commentLikes(createPostLikes)는 commentId별 Provider 메모리 상태다. epoch/operation token/read order로 중복 쓰기·오래된 GET을 막고 인증 revision/status/pending/member가 바뀌면 비운다. 동일 댓글의 두 CommentLikeButton은 같은 저장소를 구독한다.
- CommentLikeButton의 same-session 지연 성공/불확실 응답은 memberCommentsChanged를 호출하고 화면 부수효과는 navigation+session으로 제한한다. CSRF 준비 실패와 불확실 쓰기 구분을 보존했다.
- Comments의 일반 CRUD는 현재 navigation scope, 쓰기 잠금, 조회 ticket, refreshIdentity 및 생성 결과 보관을 사용한다. 전체 Comments를 다중 폼 컨트롤러로 간주하면 안 된다. 다음 단계 CRUD 분리 시 인증 세대별 데이터 무효화와 폼 소유권을 명시적으로 재검토해야 한다(이번에 해당 정책을 재작성하지 않음).
- postListChanged는 목록+내댓글, memberCommentsChanged는 내댓글만 변경하므로 상세 폼을 강제 재마운트하지 않는다.
- member-activity.ts의 회원별 lease/segments/cursor/tab/position, activity-position.ts의 기준 항목·인접 항목 복원, MyActivity/MyComments의 원자적 prefix 재조회는 수정하지 않았다. PostEdit 요청 소유권 및 busy 정리는 시작본 그대로다.

## 다음 단계 연결 메모
1. MyActivity 각 postId에 조회 상태/커서/segments/error/loading을 소유하는 작은 컨트롤러를 배치하고 CommentItem 목록을 렌더링한다. 처음 펼칠 때 GET; 닫을 때 상태를 버리지 않도록 부모의 postId별 상태 또는 유지된 자식 수명을 선택한다. 추가 조회는 getComments/mergeComments 원본커서를 재사용한다.
2. 요청에는 postId + 인증 revision + 인스턴스/읽기 ticket을 캡처한다. abort뿐 아니라 적용 직전 소유권을 검사한다. 인증 변경은 초안과 개인 상태를 우선 폐기한다. 실패는 기존 목록/커서를 유지한다.
3. compose/edit/error IDs는 인스턴스 useId로 생성하고 label/description을 동일 값에 연결한다. 인라인 항목에 공개 comment-{id}를 중복 부여하지 않는다. 공개 해시 탐색은 상세만 담당한다.
4. 여러 초안은 먼저 마이페이지 소유자가 postId별로 보유하고 dirty를 OR로 합쳐 단 하나의 useUnsavedChanges에 등록하는 최소 설계를 권장한다. 확장이 필요해지면 guard 등록 토큰의 Set을 쓰되 이번 단계에서는 구현하지 않는다. 접기는 이탈이 아니며 해당 초안을 보존한다.
5. CommentLikeButton 자체는 현재 로그인 복귀 URL에 location+comment hash를 사용한다. 실제 인라인 연결 시 마이페이지 복귀/재펼침 맥락을 부모가 전달하도록 검토해야 한다. 현재 테스트에서 해당 버튼을 재사용하되 사용자용 마이페이지에 연결하지 않았다.
6. 조회 전용 단계에는 작성·수정·삭제·guard를 활성화하지 않는다. 슬롯이 필요한 행동만 선택적으로 전달한다. 새로운 전역 상태/폴링 없이 기존 좋아요 저장소와 무효화 이벤트를 사용한다.

## 실행 환경과 재현 방법
Node24.21.0 / pnpm10.34.3, Chrome headless, 모의 API만 사용. 실제 Spring/개발 DB/실회원 변경 없음. 기존 8443 서버(PID10098)는 보존했다. 별도 Next8466(SSR8856),8467(SSR8858) 및 fixture8468을 사용하고 직접 시작한 서버만 종료한다.
- web: `pnpm lint`, `pnpm test`, `pnpm typecheck` (next typegen이 생성한 next-env.d.ts는 시작본으로 복원)
- 임시 빌드에 src/public/patches와 설정·잠금파일, 같은 설치 의존성을 복사. `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm build`; 최종 제품 빌드는 fixture route 제거 후 별도 확인.
- `node tests/comment-items-browser.mjs --install /private/tmp/<isolated-web>`는 저장소 외부 임시 디렉터리에만 테스트 route를 설치한다. 빌드/서버 후 BASE_URL, COMMUNITY_ARTIFACT_DIR를 지정해 `node tests/comment-items-browser.mjs` 실행. 제품 src/app에는 테스트 페이지가 없다.
- `BASE_URL=http://127.0.0.1:8466 COMMUNITY_ARTIFACT_DIR=<out> node tests/community-comments-browser.mjs`
- 같은 8466으로 `node tests/comment-likes-browser.mjs`
- `BASE_URL=http://127.0.0.1:8467 MEMBER_ARTIFACT_DIR=<out> node tests/member-comments-browser.mjs`
- Playwright는 설치된 codex runtime의 index.mjs를 PLAYWRIGHT_REAL_MODULE로 지정. PLAYWRIGHT_MODULE은 동봉 browser-diagnostics.mjs, BROWSER_FONT_CSS는 google-fonts.css로 지정했다. 기존 검증에서 외부 Google CSS 응답 지연이 있었으므로 동일 CSS를 브라우저 라우트로 공급한다(제품 폰트/코드 변경 없음). 파일의 폰트 URL은 원본이며 네트워크 연결 필요.
- `git diff --check` 통과.

## 실제 결과
- unit: 403/403, skip0.
- lint 및 typecheck: 통과.
- production build: 통과. production-build-final.log는 테스트 route를 제거한 최종 제품 빌드이며 items-build-final.log는 복수 인스턴스 전용 빌드다.
- 복수 표시 신규 검사: 6/6 (1440/390/320 × JS on/off). 같은 댓글 5회, 중복ID 없음, SSR 본문 가시성, 시간dateTime/팝업 대상 격리, 독립 action, 실제 CommentLikeButton PUT/DELETE 공유 상태, 공개 앵커 focus, 가로 넘침 검사. 첫 display-only run과 실제 좋아요 강화 run도 각각 별도 기록; 최종 CSS 적용 증거는 items-styled.
- 상세 댓글 CRUD: 61/62. noJS success/empty/failure 및 초안/토스트/입력 연결/해시와 늦은 요청 검사 통과.
- 댓글 좋아요: 25/25.
- 마이페이지 내댓글: 67/67. 세 페이지·설정/상세 왕복·인접 앵커·지연/불확실 응답·인증 전환 포함.
- 남은 실패: `logout failure: detail URL/body, comment drafts, focus and dirty protection remain`의 `abs(scrollY-before)<4` 실패. 기존 docs/post-like-404-wait-2026-10-26 및 직전 작업 문서에 기록된 동일 시나리오/스크롤 조건이다. 이번 변경은 행 표시 추출뿐이며 요청·scroll 복원 코드는 변경하지 않았다. 이번에 시작본을 별도로 빌드해 A/B 재실행한 것은 아니므로 재현 원인을 새로 확정했다고 주장하지 않는다. assertion/대기를 수정하지 않고 실패 원문을 포함했다.
- 실제 Safari/소프트 키보드/화면낭독기, 실제 Spring 연동, 다른 전체 피드/게시글 수정 스위트 및 404 경합 전용 검사는 미실행. 변경하지 않은 PostEdit는 시작본과 바이트 비교했다. 기존 404/댓글 조회 오류 간헐 문제를 해결했다고 보고하지 않는다.

## 수동 확인 및 패키지
상세 페이지에서 공개 #comments-title 및 #comment-ID 링크, 시간 버튼 Enter/터치, 댓글 수정·취소·삭제 확인·등록 토스트를 확인한다. 내 댓글 3페이지 후 상세 좋아요 조작/복귀 시 항목·위치를 확인한다. 실제 데이터 대신 제공 테스트의 mock을 사용한다.
ZIP은 현재 web/src/tests/public/patches와 필요한 설정/AGENTS, 본 보고서·로그·캡처 및 계약 참조 Java 소스를 포함한다. this-task.patch는 시작 스냅샷 대비이며 HEAD-to-current.patch는 기존 미커밋 구현+이번 변경(신규 소스 포함)이다. 실제 .env/인증정보/DB/.git/node_modules/.next/build/과거ZIP/무관한 디자인 이미지는 제외한다. 테스트의 example.test 및 fixture-token은 합성 데이터이며 실제 계정/토큰이 아니다. 원본 파일을 마스킹하거나 변경하지 않았다.
