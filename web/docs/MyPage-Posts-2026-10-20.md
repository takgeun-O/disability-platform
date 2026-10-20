# 마이페이지 내 게시글 커서 연동 — 2026-10-20

## 기준과 범위

- 브랜치 `feat/member-profile`, 시작 HEAD `306d26993fddd31dd440c1072c6d338b3543e95f`, 시작 작업 트리는 clean.
- 루트/web AGENTS 및 현재 소스 기준. 기존 마이페이지 1차·메뉴 문서와 캡처는 보존했다.
- 내 게시글과 관련 갱신만 추가. 프로필/닉네임/공통 메뉴 배치, 백엔드/DB/보안/의존성은 그대로다. 내 댓글 목록 API는 호출하지 않는다.
- Next 16.3.6 설치 문서 `node_modules/next/dist/docs/01-app/03-api-reference/02-components/link.md`의 scroll/onNavigate 계약 확인. AppLink의 수정 키/새 탭/NavigationGuard 처리와 Next history 필드를 유지했다.

## 실제 API 확인

읽은 서버 파일(수정 없음):

- `server/iyum-api/src/main/java/io/github/takgeun/iyum/member/api/MemberActivityController.java`
- `server/iyum-api/src/main/java/io/github/takgeun/iyum/member/application/MemberPostReadService.java`
- `server/iyum-api/src/main/java/io/github/takgeun/iyum/community/post/api/dto/PostFeedResponse.java`, `PostFeedItemResponse.java`
- member의 `MemberPostReadControllerTest`, `MemberPostSessionIntegrationTest`, `MemberPostReadServiceTest`.

`GET /api/v1/members/me/posts?size=10` → 200, `Cache-Control: no-store`.
회원 ID는 요청에서 선택하지 않고 세션 principal로 서버가 결정한다. size 기본10/최대50, createdAt DESC + id DESC, 삭제 제외.

항목 `id/type/topic/title/contentPreview/author/createdAt/commentCount`.
응답 `items/hasNext/nextCursor`. nextCursor의 ID는 **postId**다.
`nextCursor.createdAt`을 Date로 바꾸지 않고 `afterCreatedAt`에, `postId`를 `afterId`에 `URLSearchParams`로 전달한다. 테스트의 `.123456Z` 원문을 그대로 검증했다.

공개 FeedItem 검사(미리보기·enum·작성자·날짜·안전 정수 댓글 수)를 재사용하되, 개인 응답은 추가로 현재 회원 ID, 순서, 커서 진행과 마지막 항목 일치를 검증한다. `hasNext:true`+누락/잘못된 커서는 오류다. 정상 마지막 페이지는 `nextCursor:null`. 오류나 잘못된 댓글 수를 0/빈 목록으로 바꾸지 않는다.

브라우저 기존 API base와 request 함수를 사용한다. `credentials:include`, `cache:no-store`, GET에는 CSRF를 추가하지 않는다. 상세 응답에 commentCount를 요구하지 않으며 기존 metadata/공개 조회는 바꾸지 않았다.

## 핵심 파일과 동작

| 파일 | 핵심 변경 |
|---|---|
| `src/lib/member-api.ts` | `getMemberPosts`: 인증된 10개 커서 조회와 계약 검사 |
| `src/lib/community-api.ts` | 기존 피드 항목 검사만 `isFeedItem`으로 추출/공유 |
| `src/components/member/MyActivity.tsx` | 두 탭, 실제 목록, 더 보기, 독립 상태/재시도, 키보드와 위치 복원 |
| `src/lib/member-activity.ts` | 개인 메모리 캐시·세션별 lease·원자적 누적 구간 재조회 |
| `src/components/AuthProvider.tsx` | 인증 상태 변경에 맞춰 개인 캐시 소유자를 동기적으로 갱신/폐기 |
| `src/features/MyPage.tsx` | MyActivity 연결, 기존 list 변경 버전으로 활동 요약 갱신 |
| `src/lib/community-posts.ts` | `safeListReturn`에 정확한 `/mypage`만 추가. 임의 하위 경로/쿼리/해시는 허용하지 않음 |
| `src/styles/mypage.css` | 활동 목록 전용 구분선, 제목/두 줄 미리보기, 실제 댓글 수, 반응형 더 보기 |
| `src/index.css` | 기존 auto 규칙의 설명 주석만 갱신(아래 개발 서버 CSS 진단 참고) |

추가 항목은 ID로 중복 제거하고 서버 순서와 응답 커서를 유지한다. 요청 중 버튼은 `aria-disabled`와 동기 요청 guard로 중복을 차단한다. 추가 조회 실패 시 기존 항목·마지막 성공 커서는 그대로 두고 같은 커서로 재시도한다. 끝이면 더 보기를 숨긴다.

키보드 더 보기 성공 시 새 첫 제목 링크로 초점을 옮긴다. 추가 항목 없이 끝난 경우에도 보이는 마지막 안내로 연결한다. 마우스에서는 제목 강제 초점을 주지 않는다. 진행/완료 안내는 status, 오류는 alert로 분리했다. 탭은 단일 상태와 기존 화살표/Home/End 조작을 사용한다.

## 복귀, 갱신, 개인정보

- 개인 캐시는 AuthProvider 인스턴스 내부 메모리, 최대 4개 방문 기록이다. 공개 피드 캐시와 별개다. local/sessionStorage나 서버에는 개인 목록·커서를 저장하지 않는다.
- `history.replaceState({...history.state, iyumMemberEntry})`로 **비식별 방문 키**만 추가한다. Next의 history 필드는 보존한다.
- 각 기록은 선택 탭, items, 서버 nextCursor/hasNext, 누적 페이지 수, 변경 버전, 스크롤 기준 행·앞/뒤 인접 ID·화면 내 offset을 가진다.
- 상세 제목은 AppLink이며 `/community/posts/{id}?from=%2Fmypage`. 단순 뒤로/목록 링크 복귀는 캐시를 재사용한다. 프로필·요약 높이와 목록 DOM이 준비된 뒤 목록 DOM을 기준으로 위치를 복원한다. 화면 이탈 시 예약 작업과 요청은 취소/무효화한다.
- 기존 `postChanged`와 `postListChanged`를 그대로 구독한다. 게시글 생성/수정/삭제, 댓글 작성/삭제 등 목록 버전이 바뀌면 **읽었던 페이지 수만큼 첫 페이지부터 서버 커서로 재조회**한다. 새로 작성한 글도 포함한다. 성공한 전체 묶음을 한 번에 교체하고, 실패하면 기존 목록을 유지한다. DOM 항목으로 임의 커서를 만들지 않는다.
- 갱신 후 같은 행을 기준으로 복원하고, 삭제됐으면 다음/이전 행을 사용한다. 문서 끝에서는 브라우저의 최대 스크롤 범위까지만 복원할 수 있다.
- 활동 요약은 독립 조회/재시도이고 실패가 목록을 가리지 않는다. 0건 요약도 목록 요청을 막지 않는다. 댓글 내용 수정은 기존 계약처럼 댓글 수 증감을 추정하지 않는다.
- logout/checking/세션 만료/계정 전환 시 캐시와 복귀 기록, 기존 lease를 폐기한다. 같은 ID가 다시 로그인해도 이전 요청은 쓸 수 없다. 이탈 승인·AbortController·요청 식별자·변경 버전 검사를 함께 적용했다.
- 회원/커뮤니티의 기존 토큰·세션·초안 처리 구조는 재작성하지 않았다. 캐시에 비밀번호/쿠키/CSRF 토큰을 넣지 않는다.

## 직접 실행한 검사

Node 24.21.0 / pnpm 10.34.3. 실제 명령은 web 디렉터리 기준이며 시스템 PATH에 Node24를 지정했다.

| 명령 | 이번 결과 |
|---|---|
| `pnpm test` | 280/280 통과(내 게시글 신규 6개 포함) |
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm build` | 통과. 기존 8443 `.next` 보존을 위해 현재 소스·설정·동일 node_modules/lockfile을 임시 폴더로 복사하여 빌드 |
| `git diff --check` | 통과 |
| `node tests/member-posts-browser.mjs` | 최종 18/18 통과, 모의 API + 프로덕션 Next |
| `node tests/member-browser.mjs` | 20/20 통과, 기존 프로필·닉네임·세션 회귀 |
| `node tests/member-menu-browser.mjs` | 22/22 통과, 메뉴·이탈 확인·모달·무JS 공개 메뉴 회귀 |
| `IYUM_RUN_MEMBER_LOCAL=true node tests/member-posts-local.mjs` | 실제 Spring 읽기 전용 2개 시나리오 통과 |

브라우저 실행 환경:

```sh
# 설치된 Playwright 경로를 사용. 새 패키지를 추가하지 않았다.
export PLAYWRIGHT_MODULE=/path/to/installed/playwright/index.mjs
# 내 게시글 모의 검사: 별도 Next를 8452에 실행; script가 SSR용 API 8858을 실행/정리.
# Next build/start의 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858
# NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 (브라우저 요청은 Playwright에서 모의 처리)
BASE_URL=http://127.0.0.1:8452 node tests/member-posts-browser.mjs
BASE_URL=http://127.0.0.1:8452 node tests/member-browser.mjs
BASE_URL=http://127.0.0.1:8452 node tests/member-menu-browser.mjs
# 기존 소유 확인된 seed 계정/로컬 8443+8082, 회원·게시글·댓글에 쓰기 없음
IYUM_RUN_MEMBER_LOCAL=true node tests/member-posts-local.mjs
```

모의 검사는 최소 27개/3페이지로 처음/빈 목록/영역별 실패/재시도, 마이크로초, 중복 클릭, 마지막 키보드 초점, 탭 유지, 1440·390·320 왕복, 긴 텍스트/큰 숫자, 게시글 생성·유형/주제 수정·삭제 후 갱신, 댓글 작성/수정/삭제, 오래된 두 저장 성공과 GET 응답 경합, logout/다른 계정/401/늦은 응답을 확인했다. Mutation UI에서 실제 요청 코드를 실행하되 응답만 모의 처리한 것이다.

실제 검사는 기존 manifest의 회원 **34**, 시드 계정으로 로그인하고 API 소유자를 대조했다. **29개/3페이지**, 원문 커서, 세션/CORS/200/no-store, 상세 첫 20프레임 모두 `scrollY=0`/`auto`, 뒤로 복귀 29개 및 위치, 1440·390·320 캡처 확인. 회원·게시글·댓글의 생성/수정/삭제는 **0건**. 활동 요약/목록 GET과 로그인 세션만 사용했다. API 변경 성공 검사는 모의 결과이며 실제 쓰기를 수행한 것으로 보고하지 않는다.

## 검증 중 구분한 사항

1. 개발 서버 8443이 소스의 `auto`와 다른 오래된 `smooth` CSS를 제공했다. 최초 실제 검사에서는 531→529→520… 프레임이 관찰됐다. mtime만 변경해도 갱신되지 않아 기존 CSS 설명 주석을 보완하여 재컴파일을 유도했다. 최종 실제 응답은 auto, 첫 20프레임 모두0이었다. 모든 라우트에 scrollTo를 추가하지 않았고 기존 서버도 종료하지 않았다.
2. 삭제 복원 테스트의 처음 선택 행이 문서 끝에 너무 가까워 목표 offset까지 스크롤할 여지가 없었다. 중간 행 삭제로 인접 행 복원 요구를 직접 검증하도록 수정했다. 모바일 메뉴 검사는 offscreen 헤더에 대한 Playwright 자동 스크롤과 패널 자체 스크롤 잠금을 분리했다.
3. 실제 테스트가 응답 상태만 받은 즉시 다음 클릭을 보내면 정상 중복 제출 차단에 걸렸다. 목록 DOM 반영/버튼 활성까지 기다리도록 고쳤다. 작성 전환 검사도 이전 화면의 ‘글 유형’ nav가 아니라 현재 폼이 준비된 뒤 선택한다. 시간 제한을 늘리거나 실패 검사를 제거하지 않았다.

## 기록·캡처와 남은 범위

- [모의 결과](member-posts-2026-10-20/mock/results.json), [실제 결과](member-posts-2026-10-20/actual/results.json), [명령 결과](member-posts-2026-10-20/checks.json).
- 기존 회귀 JSON: `member-posts-2026-10-20/regression/`.
- [PC 1440](member-posts-2026-10-20/mock/mypage-posts-1440.png), [모바일 390](member-posts-2026-10-20/mock/mypage-posts-390.png), [모바일 320](member-posts-2026-10-20/mock/mypage-posts-320.png), [긴 텍스트/키보드 320](member-posts-2026-10-20/mock/long-keyboard-320.png).
- 실제 시드 계정의 화면은 `member-posts-2026-10-20/actual/actual-{1440,390,320}.png`. 모의 캡처의 예시 글을 서비스 데이터에 넣지 않았다.
- 실물 iPhone/Safari·소프트 키보드, 실제 API 쓰기 회귀, 백엔드 전체 테스트는 이번에 실행하지 않았다.
- 과거 게시글 무JS404 실패/상세 중복 GET 기록은 별도 이력으로 유지한다. 이번에 해결 또는 신규 실패로 주장하지 않으며 해당 재설계/전용 검사는 수행하지 않았다. 무JS **공개 메뉴** 회귀는 별도로 통과했다.
- 캐시는 한 탭의 현재 인증 세션에 한정한다. 전체 새로고침/새 탭은 첫 10개부터 조회한다. 다른 탭/외부 클라이언트의 변경은 자동 push하지 않으며 새로고침으로 확인한다.
- 내 댓글은 준비 상태. 다음 단계에서 활동 탭/독립 오류 패턴, 인증별 lease/방문 키, 원문 커서/중복 guard/복원 방식을 재사용할 수 있다. 댓글 DTO·commentId 커서는 별도 계약 검사로 연결해야 한다.
- 기존 8443/8082 서버와 preview worktree는 보존. 검증용 8452/8858 프로세스는 종료했고 해당 포트의 리스너가 없음을 확인했다. 커밋·push·PR·병합·배포 없음.
