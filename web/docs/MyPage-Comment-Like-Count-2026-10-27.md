# IYUM 내 댓글 좋아요 개수 — 외부 검토

## 기준
- 브랜치: feat/mypage-comment-like-count
- 시작/최종 HEAD: 9be526497a1090846d45600af6b80aa3b8fda098. 요청한9be5264와 동일.
- 시작 작업 트리: clean. 이번 작업 전 미커밋 변경 없음. 따라서 기준 대비 patch와 이번 작업 patch는 동일합니다.
- 커밋·push·병합·배포 없음. 백엔드/DB 수정 없음.

## 구현 및 파일
- `web/src/lib/member-api.ts`: MemberCommentItem에 likeCount:number 추가. 기존 count()로0이상 안전한 정수 검증. 누락/null/문자열/음수/소수/초과값은 UNEXPECTED_RESPONSE. 원문·공백·ID·정렬·커서 검증 유지.
- `web/src/components/member/MyComments.tsx`: 날짜 보조 정보 옆에 기존 하트 SVG/읽기 전용 스타일 재사용. 좋아요0도표시, ko-KR 숫자 그룹화. span이며 role/button/tabIndex/aria-pressed/liked 없음. 장식 SVG aria-hidden. 기존 원문 댓글 링크 유지.
- `web/src/styles/mypage.css`: 메타 정보 행의 줄바꿈/간격/16px아이콘·13px글자. 긴 숫자에 줄바꿈 허용. 기존 브랜드/화면 구조 유지.
- `web/src/components/community/CommentLikeButton.tsx`: 성공 시 기존 memberCommentsChanged() 알림 사용. 확인되지 않은 결과에도 현재 세션에서 재조회 필요 표시. 개인 목록 데이터에 숫자를 직접 더하지 않음. 상세를 떠난 뒤 성공한 요청은 session.isCurrent()와 operation.valid()가 모두 맞을 때만 무효화. 예전 화면/다른 계정은 변경하지 않음.
- `web/tests/member-comments.test.mjs`: 새 필드의 허용/거부, 문자열 원문 보존 및 liked 없는 정상 응답 검사.
- `web/tests/member-comments-browser.mjs`:3페이지별개수,읽기전용접근성,잘못된개수·다음페이지오류/동일커서재시도,상세PUT/DELETE후27개범위와위치복원,늦은성공후개수갱신,큰숫자화면캡처 추가.
- `member-comments-fix-browser.mjs`, `member-404-race-browser.mjs`, `password-change-race-browser.mjs`: 개인 댓글 fixture에 새 계약 likeCount 반영. assertion 제거 없음.

## 계약과 최신성
GET /api/v1/members/me/comments: size10, nextCursor.createdAt/ commentId를 afterCreatedAt/afterId로 원문 전달. credentials include/no-store 유지. GET에CSRF추가안함. 목록응답만사용하며 댓글별추가API없음. 백엔드MemberCommentItemResult/Response,MemberCommentReadService,MemberActivityController와컨트롤러/세션테스트를읽어확인했습니다. 서버는 getStates(ids,null)로 개수만 집계하고 liked를노출하지않습니다.

기존 MyComments의 member-comments 버전, AuthProvider의회원별메모리lease, 요청세대검사를그대로사용합니다. 변경 후 복귀하면 기존refreshMemberComments로 이전조회페이지수만큼 서버커서를따라재조회하고 전체성공시에만교체합니다. 중간실패시기존목록보존. 첫페이지로축소하지않습니다. 활동요약/공개피드/상세버전은댓글좋아요만으로무효화하지않습니다. 실시간구독/폴링/새전역저장소없음.

## 실행 결과
Node24.21.0 / pnpm10.34.3 / Next16.3.6 / Chrome headless + Playwright.

| 검사 | 직접 실행 결과 |
|---|---|
| pnpm test |380/380, 실패/skip0 |
| pnpm lint |통과, 최종테스트/스타일변경후재실행 |
| pnpm typecheck |통과 |
| pnpm build |최종격리복사본통과 |
| git diff --check |통과 |
| 댓글좋아요 기존전체 |25/25 |
| 비밀번호변경 인증응답경합 전체 |37/37 |
| 내댓글 첫실행 |38/43 (로딩시간초과2, 새정렬영향의404앵커실패3) |
| 늦은성공/추가조회오류검사추가후 |41/45 (404앵커4실패) |
| 기준HEAD제품코드 404비교 |4/4통과 |
| 메타행center정렬격리비교 |4/4통과 |
| 최종내댓글전체 |**44/45**, 아래reload시간초과1건 |
| 남은시나리오추적사본 단독1회 |1/1통과. 전체실행결과를45/45로바꾸지않음 |

단위/lint/typecheck는실제실행했습니다. 최초build시도는이전임시폴더에package.json이없어실패(환경준비문제). 새격리복사본으로빌드했고최종스타일수정후다시빌드했습니다. 테스트결과와과정로그는모두보존했습니다.

### 발견 후 수정한 신규 회귀
새 메타 행의 baseline 정렬에서 삭제된 원문 복귀 앵커 검사가실패했습니다. 기준HEAD의같은4개시나리오는통과했고,정렬만center로변경한격리검증도통과했습니다. 새메타행정렬만수정한최종전체실행에서PC/390px × 뒤로/링크4개모두통과했습니다. 목록복원코드나허용오차/기대값은변경하지않았습니다.

### 남은 실패/한계
최종전체의 `zero summary and summary error do not hide comments; actual empty remains distinct`가 /mypage 재로딩시 `page.reload: Timeout 8000ms exceeded (waiting until load)`로실패했습니다. 데이터0/빈목록 assertion실패가아니라문서load대기단계입니다. 요청추적만추가한임시사본에서동일조건/제한시간으로단독1회실행은통과했습니다. 간헐원인은확정하지못했으며전체통과라고보고하지않습니다. 시간제한확대/skip/검증완화없음.

요청에서분리한 기존댓글조회오류안내와로그아웃실패스크롤문제는수정하지않았습니다. 댓글CRUD전체는이번에미실행이며이두시나리오의해결을주장하지않습니다. 변경한별도404전용스위트2개도fixture수정만했고전체실행은하지않았습니다(내댓글기존스위트의404왕복4개는실행).

모든이번브라우저검증은모의API입니다. 실제Spring서버/실제회원/DB검증미실행,실제데이터변경없음. 실물iPhone/Safari·소프트키보드·화면낭독기미검증. 접근성트리/DOM,Chrome viewport1440/390/320을확인했습니다. 댓글좋아요/인증경합스위트는최종기능코드로실행했고이후개인목록에만적용되는CSS정렬수정후에는내댓글전체와build/lint를재실행했습니다.

## 실행 방법
```sh
cd web
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8465
# 별도 터미널
BASE_URL=http://127.0.0.1:8465 node tests/member-comments-browser.mjs
BASE_URL=http://127.0.0.1:8465 node tests/password-change-race-browser.mjs
# 댓글좋아요는 별도서버와mock8856사용
IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm exec next start --hostname 127.0.0.1 --port 8464
BASE_URL=http://127.0.0.1:8464 node tests/comment-likes-browser.mjs
```
Chrome와Playwright필요. 이번PLAYWRIGHT_MODULE은 `/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs`. MEMBER_ARTIFACT_DIR,COMMUNITY_ARTIFACT_DIR,PASSWORD_CHANGE_RACE_DIR로결과경로지정. 실제실행로그는web/docs/mypage-comment-like-count-2026-10-27/에있습니다. 기존8443서버를보존하기위해 .env없이 `/private/tmp/iyum-member-count-build`에현재코드/의존성을복사했습니다. baseline은별도사본/8466/mock8857,기준HEAD의4개제품파일을사용했습니다. 시작한검증서버만정리합니다.

수동확인: 로그인→마이페이지→내댓글→0/양수확인→더보기2회→중간댓글원문이동→상세에서좋아요등록/취소→뒤로또는마이페이지로돌아가기→27개범위·위치·개수확인. 잘못된응답은모의환경에서검사하며실제DB에시험데이터를만들지마세요.

## 자료와 패키지
- 최신캡처: `web/docs/mypage-comment-like-count-2026-10-27/member-comments-release/comments-{1440,390,320}.png` (긴본문/큰숫자), workspace캡처(기본개수).
- 각폴더results.json 및동명.log는실행시점별결과. 초기실패기록을삭제하지않았습니다.
- `review/9be5264-to-current.patch`, `review/this-task.patch`, 변경/신규파일목록,작업전후git status포함.
- web/src/tests/public/설정/lock/AGENTS와계약검토용백엔드소스·테스트포함. 백엔드참조는전체실행프로젝트가아님.
- .git/node_modules/.next/build/cache/.env/실제인증자료/쿠키·토큰/DB덤프/이전ZIP제외. 테스트명시적fixture값(example.test,test-only등)은실제계정이나비밀이아닙니다. 원본마스킹변경없음.
- ZIP재개봉CRC/원본파일SHA/신규파일및캡처포함검사수행.
