# 게시글 좋아요 프런트엔드 연동 — 2026-10-25

## 시작 상태와 범위
- 브랜치 `feat/community-post-like`, HEAD `7b0fe586886c64bd45d17ced58e781385b9c6de8`.
- 시작 시 staged/unstaged/untracked 모두 없음. 앞선 백엔드·프런트 작업은 이 HEAD에 이미 커밋되어 있었다. 이번 변경은 모두 미커밋 프런트 소스·테스트·기록이다.
- `web/src/app/community/posts/page.tsx`, 백엔드, 보안 정책, 의존성, 별도 preview worktree는 수정하지 않았다. 커밋/스테이징/push/배포 없음.
- 루트/web AGENTS 및 설치된 Next 16.3.6 `use-client` 문서를 읽고 기존 SSR와 인증 Provider 구조를 유지했다.

## 확인한 실제 API 계약
- `PostLikeController`: body 없는 `PUT/DELETE /api/v1/community/posts/{id}/like`, 세션+CSRF 필요. 200 `{postId,liked,likeCount}`. 서버가 인증 객체에서 회원 결정, viewerId 전송 없음.
- `PostController`: 목록/피드/상세에 `likeCount`, `liked`, `Cache-Control: no-store`.
- `MemberPostReadService`: 내 게시글의 동일 필드, 기존 회원 ID와 커서 인자 유지.
- 401 `AUTHENTICATION_REQUIRED`, 403 `CSRF_TOKEN_INVALID`/`POST_LIKE_NOT_ALLOWED`, 404 `POST_NOT_FOUND`를 구분한다.
- `community-api.ts` 공통 필드 검사에 boolean liked와 음이 아닌 안전한 정수 likeCount를 추가했다. 상세에 commentCount를 요구하지 않는다. 변경 응답은 postId 일치도 확인한다. 누락/문자열/음수/소수/초과값을 보정하지 않는다.
- 서버 공개 조회는 기존처럼 자격증명 없이 no-store. 브라우저 GET만 credentials include로 개인 liked를 조회한다. 초기 HTML은 익명 좋아요 수를 표시한다.

## 구현과 검토 시작점
1. `src/lib/post-likes.ts`: AuthProvider별 메모리 상태. 인증 revision/status/pending/member가 바뀌면 동기적으로 폐기한다. 읽기 시작 순번·인증 epoch와 게시글별 변경 토큰으로 오래된 GET/success/finally를 차단한다. 같은 ID 중복 변경만 막고 다른 글은 허용한다.
2. `src/components/AuthProvider.tsx`: 기존 captureMemberRequest의 generation+revision 검사를 재사용한다. 좋아요에서 beginCredentialChange를 호출하지 않는다.
3. `src/components/community/PostLikeButton.tsx`: 세 화면이 공유하는 SVG/button, aria-pressed, 최소 44px, 초점 표시. 서버 확정값만 반영하며 성공 토스트/낙관적 +/- 계산은 없다. 비로그인 시 기존 CommunityDialog/NavigationGuard 패턴, 로그인 복귀 위치 기억, 자동 좋아요 등록 없음.
4. `src/features/PostList.tsx`: 첫 인증 확인 및 인증 전환 시 기존 커서 범위를 재조회하여 개인 상태를 읽는다. 별도 게시글 GET을 N번 보내지 않는다. 좋아요 변경 자체는 목록을 재마운트/재조회하지 않고 공유 상태만 갱신한다.
5. `src/features/PostDetail.tsx`: 본문·댓글을 유지하며 개인 좋아요를 별도 GET으로 읽는다. 본문 재조회 경로도 인증 문맥을 검사한다. 인증 확인/변경으로 재시작할 때 로딩이 고착되지 않도록 처리했다.
6. `src/components/member/MyActivity.tsx`: 기존 회원 요청/캐시 lease 검사를 그대로 사용하고, 읽은 게시글의 좋아요 상태를 공유한다. 탭/커서/목록/위치 로직 보존.
7. `src/lib/community-feed.ts`: 공개 복귀 캐시에는 개인 liked를 보관하지 않고 false로 제거한다. 실제 버튼은 Provider 상태를 사용한다. 개인 목록은 기존 회원 소유 캐시를 계속 사용하며 인증 변경 시 폐기한다.
8. `src/styles/community.css`: 기존 토큰을 사용한 작은 청록색 하트와 숫자. 제목 링크와 버튼을 형제 요소로 배치한다.

401은 현재 화면·인증 요청일 때만 expire한다. CSRF 실패는 변경을 자동 재전송하지 않고 다음 명시적 클릭에서 새 CSRF를 조회한다. 불확실한 실패는 공통 상태에도 재확인 필요를 표시하여 다른 화면에서도 다음 클릭은 GET 확인만 한다. 404는 기존 postChanged 갱신 경로를 사용한다. 실패 안내는 클릭한 버튼 아래 한 곳에 표시한다.

## 실제 실행 결과
Node 24, pnpm 10.34.3, Playwright Chrome. 기존 8443/8082 서버를 종료하지 않았고 소스 복사본 `/private/tmp/iyum-like-validation`에서 build 및 8464/8465 검증 서버를 사용했다.

| 명령/검사 | 결과 |
|---|---|
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | 348 통과, 실패/skip 0 |
| `pnpm build` | 격리 복사본의 최종 코드로 통과 |
| `node tests/post-likes-browser.mjs` | 최종 코드 25/25 통과 |
| `node tests/community-feed-browser.mjs` | 최종 코드 22/22 통과 |
| `node tests/community-comments-browser.mjs` | 50/50 통과 |
| `node tests/community-edit-delete-browser.mjs` | 30/30 통과 |
| `node tests/member-posts-browser.mjs` | 41/41 통과 |
| `node tests/member-comments-browser.mjs` | 30/30 통과 |
| `node tests/password-change-race-browser.mjs` | 37/37 통과 |
| `node tests/post-likes-local.mjs` (명시적 opt-in) | 실제 Spring 1/1 통과 |
| `git diff --check` | 통과 |

브라우저 모의 검사 합계 235개. 댓글/수정삭제/개인활동/인증 회귀는 이번 작업에서 실행했다. 이후 최종 상세 문맥 방어 보완의 직접 영향은 최종 좋아요/피드 검사에서 다시 확인했다. 이전 문서의 과거 통과 결과를 현재 결과로 합산하지 않았다.

## 실제 Spring 검증과 데이터 보호
- 처음 실행 중인 8082는 시드 게시글 85개 중 소유 회원의 29개 모두 likeCount/liked가 없어 변경 요청을 보내지 않고 중단했다.
- 사용자가 백엔드 재시작을 알린 뒤 기존 소유 검증·잠금 helper로 계정/게시글을 확인했다.
- 8443 브라우저에서 실제 로그인 → 자신의 시드 게시글 14번 좋아요 PUT200 → GET liked=true/count1 → DELETE200 → GET liked=false/count0 확인.
- 원래 0/false로 복원했다. 계정/글/댓글 생성·수정·삭제, 비밀번호 변경, 개발 DB 초기화 없음. 테스트 자체의 좋아요 등록/취소만 수행했다.
- private seed `like-journal`에 원래 상태와 복원 완료 receipt를 보관한다. 이 파일과 계정 파일은 ZIP에 포함하지 않는다. 중단 시 receipt의 memberId/postId/originalLiked를 확인하고 동일 테스트 회원의 해당 관계만 확인·복원한다. 무차별 삭제하지 않는다.
- 실제 로그인 세션/CSRF/CORS와 200 응답을 확인한 것이며, 계정 전환·지연 응답·오류 주입은 모의 검증이다.

## 초기 실패와 보완 기록
- 최초 단위 325개 중 10개 실패: 기존 정상 응답 fixture에 likeCount/liked가 없던 계약 변경 영향. 기존 assertion을 유지하고 실제 필드를 추가했다.
- 최초 좋아요 브라우저의 중복 클릭 검사: Playwright가 aria-disabled 버튼을 클릭하지 않고 기다림. 두 번째 클릭 이벤트를 직접 보내 요청 횟수를 정확히 검사했다.
- 기존 피드: 첫 개인화 GET 1회가 추가되어 전체 GET 횟수 기대값을 분리했다. 커서 없는 첫 요청 1회와 커서 있는 추가 요청 횟수를 각각 확인하며 per-post GET 금지 검사는 유지했다.
- 기존 자동 피드 조회가 초기 개인화 refresh에서 중단되는 실제 회귀를 수정했다. 최초 인증 조회는 자동 추가 조회 플래그를 끄지 않으며, 기존 복귀/갱신 정지 정책은 유지했다.
- 이전 모바일 메뉴 selector는 PC/패널 두 요소를 함께 선택했다. 열린 dialog 내부로 한정했다. 무JS 정보 링크는 이미 별도 fallback 영역에 있으므로 header 안의 실제 표시 링크를 확인하도록 했다. GlobalNav/UI는 수정하지 않았다.
- 댓글 늦은 POST 검사는 새 초기 개인화 GET이 끝난 뒤 의도한 지연 GET을 설치하도록 바꿨다. 초점/커서/중복 요청/응답 순서 assertion은 보존했다.
- 빈 주제 목록은 좋아요 버튼이 없으므로 초기 준비 대기를 정상 빈 상태와 구분했다.
- 초기 실패 로그는 `initial-diagnostics/`에 보존했다. 최종 결과는 `results/`, `logs/`로 분리했다.

## 실행 방법
```sh
cd web
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8464
# 별도 터미널, 이미 설치한 Playwright 모듈 경로 사용 (새 의존성 추가 없음)
BASE_URL=http://127.0.0.1:8464 PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs node tests/post-likes-browser.mjs
```
내 게시글/내 댓글 harness는 SSR mock 8858, 기존 피드/댓글/수정삭제 harness는 8856을 사용한다. 같은 포트 harness는 순차 실행한다. 실제 검사는 `IYUM_RUN_LIKE_LOCAL=true`와 소유 검증 가능한 기존 로컬 seed 파일이 필요하다. ZIP에는 실제 계정 자격증명이 없다.

## 캡처와 제한
`post-like-frontend-2026-10-25/screenshots/`의 feed/detail/mypage-1440/390/320.png, nojs.png 참조. 긴 제목/큰 숫자/키보드 초점 포함. 계정/문구는 모의 데이터다.
- 실물 iPhone Safari/Android, 실제 소프트 키보드 및 배포 프록시 환경은 미검증.
- 기존 게시글 404 전체 브라우저 suite는 이번 범위에서 새로 실행하지 않았다. 좋아요 404 오류와 무JS 정상 좋아요 수 표시를 별도로 검증했다.
- 댓글 좋아요·인기순·좋아요한 글 목록은 구현하지 않았다.
- HTTP no-store 외에 UI 공유용 메모리 상태는 Provider 수명 동안 보관된다. 다른 탭의 변경은 다음 정상 조회/목록 새로고침으로 반영하며 실시간 동기화는 이번 범위가 아니다.
