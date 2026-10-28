# 인라인 댓글 UI·게시글별 재조회 보완

## 기준 / 기존 작업 보존
브랜치 feat/mypage-activity-redesign, HEAD 416fabd176918ef32d2cb33798c603024ad57b02.
시작 상태는 /private/tmp/iyum-inline-polish-start에 기록했다. 기존 미커밋 마이페이지 설정 분리·활동 피드·게시글 관리·PostEdit busy·상대 시간·CommentItem·인라인 댓글 구현은 보존했다. 이번 범위의 파일은 review/this-task-files.txt 및 this-task.patch, 누적은 HEAD-to-current.patch로 구분한다. 백엔드/DB/API 변경, 커밋/스테이징/푸시/배포 없음. 사용자 서버 8443 PID10098을 종료/재시작하지 않았다.

## UI
InlineComments의 보이는 댓글 제목, 정렬 안내, 두 개의 별도 상세 링크를 제거했다. h4는 sr-only로 남겨 ‘게시글 제목 게시글의 댓글’ 영역 이름을 연결한다. 제목 링크와 기존 onNavigate는 변경하지 않았다. 빈 목록은 빈 상태만 표시하며 마지막 댓글 안내를 중복 표시하지 않는다. 새로고침·더 보기·재시도·자연스러운 스크롤·44px 버튼을 유지했다. 상세 Comments의 제목/설명/공개 앵커는 그대로다.

## 변경 흐름
이전: 각 InlineComments가 전역 member-comments 버전을 구독 → A 좋아요 성공/불확실 응답의 memberCommentsChanged() → 펼친 B의 누적 페이지까지 재조회.
현재: `comments:${postId}`를 별도로 구독한다. `postCommentsChanged(postId)`는 상세 버전/활동 요약을 건드리지 않는다.
- 확정 좋아요: 유효한 session+operation에서 공유 store.commit(result). 화면을 이미 떠났어도 같은 인증 세션이면 반영한다. memberCommentsChanged는 유지해 내 댓글 탭을 갱신하지만 인라인 목록 GET은 발생시키지 않는다.
- 불확실 쓰기, COMMENT_NOT_FOUND, POST_NOT_FOUND: 기존 오류 분류 및 operation.valid/session.isCurrent를 확인하고 공유 상태를 uncertain으로 표시한다. 해당 게시글 comments 버전과 내 댓글 탭만 무효화한다. 기존 누적 범위/대상 댓글까지 재조회한다. 쓰기 재전송 없음.
- CSRF 준비 실패는 불확실 쓰기로 분류하지 않으므로 불필요한 무효화 없음. 기존 회귀 검사로 확인했다.
- 상세 댓글 POST/DELETE: 같은 인증 세션에서 게시글별 comments 버전 + 기존 postListChanged(게시글 수/내 댓글 요약) 발행.
- 상세 댓글 PUT: 게시글별 comments 버전 + memberCommentsChanged. 상세 버전을 변경하지 않으므로 폼을 재마운트하지 않는다.
- 상세 CRUD의 불확실 결과/없어진 댓글: 같은 인증 세션에서 게시글별/기존 목록 알림을 함께 발행한다. 이전 인증의 응답은 새 버전을 갱신하지 않는다.
- 부모 postChanged는 해당 comments 키도 무효화하며, 부모 목록 재조회로 삭제가 확인되면 기존 MyActivity의 snapshot pruning을 통해 제거한다.
- 인증/공개 사용자 정보 변경은 기존 identityVersion과 인증 revision 보호를 유지한다.

## 캐시·경합
InlineCommentsSnapshot의 과거 전역 `version`을 별도 `commentsVersion`으로 이름 변경했다. 조회 전 조건, 응답 적용, 캐시 쓰기, 재조회 effect, 좋아요 ready 모두 동일한 게시글별 키를 사용한다. 전역 숫자와 게시글별 숫자를 비교하지 않는다. 기존 캐시는 영구 저장하지 않으며 인증 변경 시 기존 Provider 정책에 따라 폐기한다.
접힌 영역은 변경 버전을 감지하되 즉시 읽지 않고 다음 펼침에 갱신한다. 탭/상세 왕복의 기존 lease·목록·위치 복원은 유지한다. atomic prefix refresh와 원본 마이크로초 커서를 유지한다. 공유 저장소의 read ticket/order 보호가 오래된 GET의 좋아요 값을 거부하므로 확정 좋아요 뒤 같은 목록을 다시 읽을 필요가 없다.

## 변경 파일
- web/src/lib/community-mutations.ts: 게시글별 댓글 무효화 발행 추가.
- web/src/lib/inline-comments.ts: 캐시 버전 필드의 의미 분리.
- web/src/components/member/InlineComments.tsx: 게시글별 구독/캐시/ready 조건과 UI 정리.
- web/src/components/community/CommentLikeButton.tsx: 확정 결과는 화면 수명과 분리하여 같은 세션 공유 상태에 반영; 불확실 결과의 게시글별 알림.
- web/src/components/community/Comments.tsx: 상세 CRUD 발행 지점에 게시글별 키 및 인증 소유권 검사.
- web/src/styles/mypage.css: 제거된 UI 전용 스타일 정리, 새로고침 우측 배치.
- web/tests/community-mutations.test.mjs: 게시글별/상세/개인목록 버전 경계 2개 단위 검사.
- web/tests/inline-comments-browser.mjs: A/B 요청 수, 지연 GET, 늦은 성공/불확실 응답, 접힌 영역의 상세 CRUD 복귀 및 UI 검사.
- web/tests/post-reactions-navigation-browser.mjs: 마이페이지는 펼침 버튼과 제목 링크, 공개 피드는 기존 댓글 앵커를 검증. 기존 터치 크기·복귀 href·좋아요·무JS assertion 유지.

## 실제 실행 결과
Node24.21.0 / pnpm10.34.3. mock API + 프로덕션 Next/Chrome headless. 실제 Spring·개발 DB 변경 없음.
- pnpm test: 409/409, skipped0.
- pnpm lint: 최종 경고/오류 없음. 최초 미사용 테스트 인자 경고는 제거 후 재검사.
- pnpm typecheck: 통과. 자동 생성 next-env.d.ts는 시작본으로 복원.
- pnpm build: /private/tmp/iyum-inline-build 격리 복사본에서 통과. 최종 src 바이트 일치 확인.
- git diff --check: 통과.
- node tests/inline-comments-browser.mjs: 26/26 (results.json의 GET counts 3개는 추가 증거 레코드이며 별도 테스트로 중복 집계하지 않음).
- node tests/member-posts-browser.mjs: 74/74.
- node tests/member-comments-browser.mjs: 67/67. 내 댓글 likeCount/liked, 지연 성공, 불확실 응답 이후 3페이지 갱신, logout/same/other 인증 전환, CSRF 사전 실패 미전송 검증 포함.
- node tests/comment-likes-browser.mjs: 25/25.
- node tests/community-comments-browser.mjs: 61/62.
- node tests/post-reactions-navigation-browser.mjs: 10/10.
브라우저 합계 263/264. 실제 시나리오 실패를 감추지 않았고 각 로그/JSON을 evidence에 포함했다.

### 실제 GET 횟수 (A/B 모두 40개 = 처음 2회씩)
| 조건 | 수정 전 B | 수정 후 A | 수정 후 B |
|---|---:|---:|---:|
| A 좋아요 확정 등록·취소 | 2→4 (등록 시 재현) | 2→2 | 2→2 |
| A 잘못된200 응답 | 2→4 | 2→4 | 2→2 |
| A 네트워크 오류 | 2→4 | 2→4 | 2→2 |
B의 innerHTML/누적 목록/커서 버튼 상태도 그대로인지 비교했다. A의 2회 추가는 읽던 두 페이지를 원자적으로 갱신한 것이다. 이전 GET 보류 후 좋아요 확정 → 오래된 GET 해제 검사에서도 최신 하트/개수가 유지되며 추가 GET은 없다.

### 수정 전·중간 실행 기록
수정 전 빌드(/private/tmp/iyum-inline-build의 이전 빌드)를 8467에서 실행하고, 임시 외부 스크립트로 A/B 3개 시나리오만 실행했다. 3개 모두 B GET 4!==2로 실패했다. 최종 저장소 테스트에는 필터 없음.
첫 수정 후 전체 신규 검사에서는 POST fixture를 큰 ID로 목록 앞에 삽입해 작성순 커서에 맞지 않는 중복이 생겼고 39!==40으로 실패했다. 실제 작성순과 같이 끝에 추가하도록 fixture를 고쳤다. 이미 읽던 40개 유지와 전체 댓글 수46 반영을 검증하며, 재펼침 시 다음 페이지 응답 finished 후 DOM 완료를 확인한다. 제품 로직·오차·assertion을 완화하지 않았다. 최종 26개 전체 재실행 통과.

### 남은 상세 댓글 실패
`logout failure: detail URL/body, comment drafts, focus and dirty protection remain`에서 `Math.abs(scrollY-before)<4` 실패가 재현됐다. 이번 시작 전 결과에서도 같은 assertion이 실패한 기록이 있다. URL/본문/초안/초점 확인 뒤 실패했으며, 같은 시나리오의 그 뒤 이탈 방지 검증은 실행되지 않았다. 나머지 독립 시나리오는 계속 실행했다. 원인은 이번 작업에서 확정하지 않았으며 해결 또는 무관함을 단정하지 않는다.

## 재실행 방법
web에서 pnpm install --frozen-lockfile 및 scripts 실행. 테스트는 기존 Playwright 외부 모듈 방식 사용.
격리 빌드:
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8467
BASE_URL=http://127.0.0.1:8467 COMMUNITY_SSR_MOCK_PORT=8858 MEMBER_ARTIFACT_DIR=/tmp/inline-results node tests/inline-comments-browser.mjs
다른 스크립트의 결과 경로는 MEMBER_ARTIFACT_DIR / COMMUNITY_ARTIFACT_DIR / LIKE_ARTIFACT_DIR를 사용한다. 상세 댓글/좋아요는 SSR mock8856·서버8466, 최종 인라인은8859·8469, 기존 마이페이지/내비게이션은8858·8467로 실행했다. 시작한 서버3개는 종료했다.
PLAYWRIGHT_MODULE wrapper와 PLAYWRIGHT_REAL_MODULE을 사용했으며 BROWSER_FONT_CSS로 기존 Google font CSS를 공급해 외부 stylesheet 지연을 피했다. helpers에 wrapper/CSS 포함. 제품 폰트 변경 없음.

## 화면/수동 확인과 제한
1440/390/320 캡처는 evidence/inline-final. A/B 펼침→각 더 보기→A 좋아요 등록/취소 시 네트워크 GET이 늘지 않는지, 불확실 응답이면 A만 읽는지 확인한다. 제목으로 상세 이동→댓글 변경→뒤로/복귀 링크→접힌 영역 재펼침 확인. 새로고침·더 보기·재시도와 빈 상태를 확인한다.
실제 Spring, 실기기 Safari/소프트 키보드, 실제 화면낭독기는 미실행이다. DOM 접근성 이름/키보드/ID/320px 가로 넘침 및 noJS 공개 링크는 브라우저 검사 범위다.
정렬 선택과 댓글 작성/수정/삭제 입력 UI는 미구현으로 유지한다. 정렬은 후속 백엔드 계약 후 연결하며, 작성은 게시글별 초안/집계 이탈 방지 설계가 필요하다.

## ZIP 포함·제외
최신 web/src·tests·public·설정·lockfile·패치, 적용 AGENTS, 이번 보고서·검증 결과·캡처, 시작/누적 diff 포함. 기존 변경과 이번9개 파일 변경은 별도로 기록. .git/node_modules/.next/build/cache/.env/실제 인증정보/DB/기존ZIP/무관한 디자인은 제외한다. mock-only 및 *.example.test는 테스트 전용 가상 값이다. 압축 CRC와 최신 원본 바이트 일치 검사 후 생성한다.
