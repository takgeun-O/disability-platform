# 마이페이지 내 댓글 연동 — 2026-10-20

## 작업 기준과 계약

시작 브랜치 `feat/member-profile`, HEAD `93578013427b14dc469a264e70817806761e2644`. 시작 작업 트리는 깨끗했다. 이전 내 게시글·키보드 재시도 개선을 기준으로 이어서 작업했다. 백엔드, DB, 보안 정책, 별도 iyum-preview worktree는 변경하지 않았다. 커밋·push·배포하지 않았다.

실제 `MemberActivityController`, `MemberCommentReadService`, `MemberCommentListResponse/ItemResponse/PostResponse`, `CommentRepository` 및 해당 테스트를 읽었다. `GET /api/v1/members/me/comments`의 size=10, 커서 createdAt/commentId를 afterCreatedAt/afterId로 그대로 전달한다. 세션이 회원을 결정하며 회원 ID 파라미터는 없다. credentials=include/no-store, GET에는 CSRF를 발급하지 않는다. createdAt DESC, commentId DESC이며 논리 삭제된 댓글과 원문 게시글 모두 제외한다.

DTO는 commentId, content, createdAt, updatedAt, post(postId/title/type/topic)이다. author, contentPreview, commentCount를 요구하지 않는다. 안전한 양수 ID, 필수 문자열·날짜·enum, 내림차순, 마지막 항목과 nextCursor 일치, hasNext와 null 커서 규칙을 검사한다. 원문 커서의 마이크로초는 Date 변환 없이 URLSearchParams로 전달한다. 잘못된 응답은 오류로 처리하며 빈 목록으로 대체하지 않는다.

## 핵심 변경

- `src/lib/member-api.ts`: `getMemberComments`와 개인 댓글 DTO 검증.
- `src/components/member/MyComments.tsx`: 댓글 탭 첫 활성화 시 조회, 독립적인 로딩·실패·커서·더 보기, 안정적인 동일 버튼 DOM과 초점 소유 검사.
- `src/components/member/MyActivity.tsx`: 기존 게시글 상태를 보존하며 댓글 상태를 같은 방문 기록에 별도로 보관. 탭 선택·상세 복귀 연결.
- `src/lib/member-activity.ts`: commentId 중복 제거, 이전 서버 페이지 수만큼 원자적 재조회. 회원·세션별 기존 메모리 캐시 안에 comments 스냅샷 추가.
- `src/components/member/activity-position.ts`: 선택된 탭의 postId/commentId별 화면 기준점과 인접 항목 복원. Next history 필드는 보존한다.
- `src/lib/community-mutations.ts`, `src/components/community/Comments.tsx`: 댓글 POST/DELETE와 게시글 변경은 개인 댓글 목록도 무효화. 댓글 PUT에는 `memberCommentsChanged`만 알리고 개수·상세 버전은 바꾸지 않는다. 이탈 후 성공한 요청도 목록 변경 알림은 보내고 이전 폼 상태는 변경하지 않는다.
- `src/styles/mypage.css`: 기존 구분선·너비를 재사용. 댓글 3줄 미리보기, 별도 원문 링크, 긴 문자열 줄바꿈만 추가.

## 두 탭·초점·복귀

내 게시글/내 댓글은 items/cursor/hasNext/busy/error를 분리한다. 댓글 탭을 처음 선택하기 전에는 개인 댓글 API를 호출하지 않는다. 같은 게시글의 여러 댓글도 commentId가 다르면 각각 보여준다. 탭 전환은 진행 중인 독립 조회 결과를 보관하되 숨겨진 영역으로 초점을 옮기지 않는다.

더 보기→실패→재시도 중 하나의 버튼 DOM을 유지한다. aria-disabled와 요청 가드가 중복 실행을 막는다. 키보드 요청이 계속 초점을 소유할 때만 새 첫 원문 링크, 빈 안내 또는 마지막 안내로 preventScroll 초점을 옮긴다. blur 후 다시 돌아와도 자동 초점을 재개하지 않는다. 마우스/다른 탭/이탈/로그아웃에는 초점을 가져오지 않는다.

원문 링크는 AppLink와 정확한 `from=/mypage`를 사용한다. 상세는 최상단에서 시작하며 특정 댓글 탐색이나 해시·자동 전체 댓글 순회는 추가하지 않았다. 돌아오면 선택 탭·누적 항목·커서와 읽던 commentId 위치를 복원한다. 기준 항목이 없으면 다음/이전 항목을 사용한다. 단순 복귀에는 재조회하지 않는다. 변경이 있으면 이전에 읽은 서버 페이지 수만큼 첫 페이지부터 다시 조회하고 전체 성공 시 교체한다. 실패 중간 결과를 기존 스냅샷에 섞지 않는다.

캐시는 AuthProvider의 세션별 메모리에만 있다. 로그아웃/401/계정 변경은 두 목록·복귀 기록을 폐기하며 이전 lease를 무효화한다. 전체 새로고침·새 탭에서는 기존 정책대로 첫 탭/첫 페이지부터 다시 읽는다. localStorage나 쿠키에 개인 목록을 저장하지 않는다. 다른 탭의 외부 변경을 실시간 동기화하는 기능은 추가하지 않았다.

## 실행 검증

최종 수치와 명령별 결과는 `member-comments-2026-10-20/checks.json` 및 각 폴더 `results.json`에 기록한다. 프로덕션 빌드는 별도 `/private/tmp/iyum-member-comments-build`에서 현재 src/public/config와 물리 복사한 동일 의존성으로 수행했다. 기존 8443 개발 서버의 산출물과 프로세스를 교체하지 않았다.

- `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`
- `node tests/member-comments-browser.mjs`: 프로덕션 Next(8452), 제어 가능한 모의 Spring(8858), 27개 댓글/3페이지. 같은 원문 여러 댓글·마이크로초·재시도/재실패·중복·초점 이탈·탭 전환·상세 왕복·PUT/POST/DELETE·원문 변경·중간 재조회 실패·늦은 PUT/GET·계정 교체·320/390/1440 캡처.
- 기존 `member-posts-browser.mjs`, `member-browser.mjs`, `member-menu-browser.mjs`를 별도 실행. 기존 게시글 키보드 검사 조건을 유지했으며 댓글 준비 안내를 실제 빈 목록 모의 응답으로 변경했다.
- `IYUM_RUN_MEMBER_LOCAL=true node tests/member-comments-local.mjs`: 기존 시드 회원34의 소유 기록에 있는 댓글 30개를 3페이지로 읽기 검증. 실제 HTTP200/no-store/CORS/세션과 원문 커서, 상세 최상단 및 뒤로 복귀·재조회 없음, 1440/390/320 캡처. 생성/수정/삭제한 비즈니스 데이터 없음. 로그인에 따른 세션 생성만 수행했다. 비밀번호·쿠키·토큰은 산출물에 기록하지 않았다.

검증 중 TypeScript의 기존 ES 라이브러리 범위에서 Object.hasOwn이 허용되지 않아 hasOwnProperty.call로 수정했다. 초기 브라우저 검사에는 현재 DOM에 없는 상세 선택자가 있었고, textarea의 입력 문자열을 저장 완료로 판단하는 대기도 있었다. 실제 상세 본문과 저장 완료 안내/편집 폼 제거를 기다리도록 교정했다. 검사 시간을 늘리거나 이탈 보호를 우회하지 않았다.

## 범위와 제한

실제 Spring의 변경 요청은 이번 작업에서 수행하지 않았다. 작성·수정·삭제에 따른 갱신/레이스는 모의 응답 검증이다. 실제 기기 iPhone Safari/소프트 키보드는 미검증이며 모바일 결과는 Chrome viewport 에뮬레이션이다. 정상 공개 메뉴 무JS 회귀는 기존 메뉴 검사에 포함된다. 과거 상세 무JS404 및 상세 중복 GET 기록은 이번 구현의 해결 사항으로 주장하지 않는다. 개인 페이지는 기존 인증 클라이언트 조회 정책을 유지한다. 댓글 위치 바로가기, 새 편집 UI, 아바타/좋아요/영구 초안/다른 탭 실시간 동기화는 범위 밖이다.

기존 서버 8443/8082는 보존하고 자체 임시 검증 서버만 종료한다. ChatGPT 검토 ZIP에는 현재 미커밋·미추적 소스, 계약 코드, 이 문서와 검증 결과·캡처를 포함하며 원본 환경파일·시드 계정·비밀값·산출물을 제외한다.

## 삭제된 원문 복귀의 추가 확인과 제한

전체 검사에서 기존 404를 거친 뒤 누적27개가 복원될 것이라는 초기 검사가 실패했다. 처음에는 기본 링크의 클라이언트 보강 시점을 의심했으나, 스택은 링크 확인이 아니라 복귀 후 목록 수 대기에서 실패했다. 별도 문서 전역 표식으로 전체 문서 전환 여부를 기록하도록 검사를 보완했다. 기존 proxy404 이동은 전체 문서 fallback을 일으킬 수 있으며 AuthProvider 메모리 캐시가 새로 생성된다. 따라서 이때는 `/mypage`로 안전하게 돌아오지만 기본 내 게시글 탭에서 시작하고, 내 댓글을 선택하면 최신 첫10개를 새로 읽는다. 정상 상세의 SPA 왕복과는 다르다.

시험적으로 오류 전용 페이지에서 복귀 주소를 서버 전달했으나 이 제한을 해결하지 못하므로 해당3개 파일의 실험 수정은 제거했다. 기존 404 코드/정책을 최종 변경에 포함하지 않았다. 개인 목록을 영구 저장하거나 HTTP404를200으로 바꾸는 우회는 추가하지 않았다. `mock/missing-post-return.json`이 직접 관찰한 문서 전환 근거다. 초기27개 복원 기대의 실패는 development-diagnostics에 보존했다. 이번 검사는 안전한 복귀와 문서 전환에 따른 초기화를 구분하며, 404에서도 누적 위치를 유지하는 것은 남은 제한이다.

## 최종 결과

단위285/285, 내 댓글 모의27/27, 내 게시글 회귀41/41, 프로필20/20, 메뉴22/22, 실제 읽기2/2 통과. lint/typecheck/build/diff 검사 통과. 이번 소스 변경은 미커밋 상태다. 8443(PID38875),8082(PID36509)는 보존했고 자체8452/8858 검증 서버는 종료했다. 캡처는 actual/actual-comments-1440.png, actual-comments-390.png, actual-comments-320.png 및 mock/comments-*.png에 있다.
