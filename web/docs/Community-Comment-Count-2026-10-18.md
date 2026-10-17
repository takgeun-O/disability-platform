# 게시글 목록 댓글 수 연동 — 2026-10-18

## 작업 기준과 계약

시작 상태: `feat/community-comment-count`, HEAD `82eb8f4`(게시글 목록 응답에 댓글 개수 추가), 작업 트리 깨끗함. 루트/web AGENTS와 설치된 Next16.3.6 데이터 조회 문서를 확인했다. 백엔드 PostSummaryResponse/PostReadService/CommentRepository의 집계 필드를 읽어 목록에만 commentCount가 있는 것을 확인했다. 백엔드·API·DB·배포 설정·의존성은 변경하지 않았다.

`GET /api/v1/community/posts`의 items.commentCount는 논리 삭제되지 않은 전체 댓글 수이며 현재 로드된 댓글 배열 길이가 아니다. 실제 상세 응답에는 해당 필드가 없다.

## 구현

- `src/lib/community-api.ts`: 공통 필드 `PostCommon`/`isCommon`, 목록 `PostSummary`/`isSummary`, 상세 `PostDetail`을 분리. 목록에만 0 이상의 안전한 정수 검증을 추가한다. 누락/null/문자열/음수/소수/범위 초과를 기존 UNEXPECTED_RESPONSE로 거절한다. 0 대체는 하지 않는다. 상세는 commentCount 없이 정상 조회된다.
- `src/features/PostList.tsx`: 공통 목록의 작성자·날짜 메타 영역에 `댓글 {post.commentCount}`. 전체/주제별 SSR 및 무JS에 동일 적용. 상세 댓글 총수 표시는 추가하지 않았다.
- `src/styles/community.css`: 숫자 영역 nowrap/tabular-nums만 추가. 기존 메타 영역 flex-wrap/overflow-wrap을 유지한다.
- `src/lib/community-mutations.ts`: `postListChanged()`는 list 버전만 증가시킨다. 기존 postChanged의 목록+상세 동작은 보존한다.
- `src/components/community/Comments.tsx`: POST201/DELETE204 성공이면 목록 버전을 갱신한 후 요청 수명 확인을 거쳐 현 화면을 갱신한다. 이미 떠난 화면에는 늦은 응답을 적용하지 않지만 목록에는 성공 사실을 알린다. PUT 성공은 개수를 바꾸지 않는다. COMMENT_NOT_FOUND 또는 결과 불확실 오류도 목록 재조회 대상으로 표시한다. 어떤 경우도 변경 요청을 자동 재전송하지 않는다.

목록 숫자를 클라이언트에서 +1/-1 하지 않는다. 반환되는 서버 목록이 최종 값이다. 기존 PostList의 요청 키에 list 버전이 들어 있으며 이전 GET을 abort하고 generation 확인 후 최신 응답만 반영한다. 목록 재조회 실패는 목록 오류로 안내하며 앞선 댓글 저장 성공을 실패로 바꾸지 않는다.

상세 버전을 바꾸지 않기 때문에 상세 본문 재조회/Comments 재마운트를 유발하지 않는다. 기존 작성/수정 초안과 편집 기준값·초점 및 이탈 확인을 유지한다. 초기 댓글 await SSR 복구도 유지하여 무JS 댓글 열람을 보존한다. 목록별 댓글 API 조회나 모든 댓글 페이지 집계는 추가하지 않는다.

## 테스트 변경

- `community-posts.test.mjs`: 목록 0/양수/MAX_SAFE_INTEGER 허용, 6가지 비정상 count 거절. 상세 fixture에는 count가 없으며 정상 상세 검사는 그대로 통과. 다른 기존 오류 fixture는 정상 count를 부여해 엉뚱한 이유로 실패하지 않게 했다.
- `community-comments-browser.mjs`: 목록 응답에 실제 계약과 같은 count를 반환. 무JS 전체/주제 목록 표시, 목록에서 댓글 API 추가 호출 0회, POST/PUT/DELETE 후 목록 링크/뒤로 복귀, 위치·필터 유지, 별도 작성·수정 초안 보존, 늦은 성공 및 이전 GET 무시, COMMENT_NOT_FOUND/불확실 오류 재조회, 자동 재전송 없음 검사 추가. 모의 POST 작성자도 실제 로그인 회원으로 설정했다.
- `community-topics-browser.mjs`: 목록 준비 후 각 행의 ID에 대응하는 서버 count를 확인하여 주제/필터/페이지 이동에서도 연결이 맞는지 검사한다.
- `community-browser.mjs`, `community-edit-delete-browser.mjs`: 목록 fixture에 count를 추가했다. 상세 fixture의 계약은 그대로다.
- `community-comments-local.mjs`: 이번에 만든 글에 대해 실제 목록 API와 UI count를 0→1→수정 후1→삭제 후0으로 확인한다.

## 실제 검증과 데이터

기존 프런트8443, Spring8082, Mailpit8025 실행을 확인해 재사용했다. `IYUM_RUN_LOCAL_COMMENTS_TEST=true node tests/community-comments-local.mjs`로 실제 회원가입→메일 인증→로그인→새 글/댓글 작성·수정·삭제와 목록 수를 확인했다. 실제 검사는 5개 묶음 통과했다.

생성 데이터: 회원33(ACTIVE), 글13, 댓글6. 댓글6과 글13은 UI로 논리 삭제 완료했다. 테스트 회원은 남아 있다. 기존 사용자 글/댓글 또는 DB를 직접 변경·초기화하지 않았다. 계정/비밀번호/토큰/메일 원문은 보고서에 싣지 않는다. `community-comment-count-2026-10-18/real/real-comments-results.json`은 테스트 표시값과 ID/상태만 기록한다.

## 실행 환경과 명령

Node24.21.0, pnpm10.34.3, Next16.3.6. 기존 dev 산출물을 방해하지 않도록 소스/설정이 동일한 `/private/tmp/iyum-edit-build-final/web`에서 typecheck/build 후 production Next를 사용했다. next/package/lock/TS/CSS 설정의 원본 일치를 확인했다.

- `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`, `git diff --check`.
- 모의 댓글: `BASE_URL=http://127.0.0.1:8451 COMMUNITY_ARTIFACT_DIR=.../mock node tests/community-comments-browser.mjs`; Next 서버 조회는 모의8856.
- 모의 주제/게시글: `BASE_URL=http://127.0.0.1:8453 COMMUNITY_SSR_MOCK_PORT=8857 COMMUNITY_ARTIFACT_DIR=.../topics` 또는 `.../posts`로 해당 browser.mjs 실행. Next 서버 조회는 모의8857.
- 실제 공개404: `BASE_URL=http://127.0.0.1:8452 COMMUNITY_404_ARTIFACT_DIR=.../404-regression node tests/community-404-browser.mjs`; 서버 조회는 실제8082.
- 모든 브라우저 명령은 설치된 Playwright를 PLAYWRIGHT_MODULE로 지정했다.

기존 무JS 게시글404 표시 2건은 별도 알려진 실패다. 이번에도 HTTP404/noindex는 통과했으나 무JS 제목/복귀 링크 표시 2건은 실패했다. 댓글의 무JS 성공/빈/오류 열람은 별도 검사로 유지한다. 실물 스마트폰/Safari는 실행하지 않았고 Chrome viewport320/390/1440으로 검증했다.

## 직접 확인 순서

1. 전체 커뮤니티와 주제 목록에서 댓글0/양수 확인.
2. 본인 검증용 글 상세에서 댓글 작성 후 ‘목록으로’ 및 브라우저 뒤로 가기로 숫자 확인.
3. 댓글 내용만 수정하면 숫자가 같고, 삭제하면 감소하는지 확인.
4. 목록 필터·페이지·복귀 위치가 유지되는지 확인.
5. 오류/초안 보호는 모의 결과와 캡처를 참고. 실제 사용자 데이터에 오류 재현을 시도하지 않는다.

캡처와 기계 판독 결과는 `community-comment-count-2026-10-18/`에 저장했다. mock/topics/posts는 모의이고 real/404-regression은 실제 API이다. 이전 문서/캡처는 덮어쓰지 않았다.

## 최종 검증 결과

| 검사 | 이번 결과 |
|---|---|
| pnpm lint / typecheck / build / git diff --check | 통과 |
| pnpm test | 226 통과 / 0 실패 |
| 댓글·댓글 수 모의 브라우저 | 37 통과 / 0 실패 |
| 긴 제목·긴 작성자·MAX_SAFE_INTEGER 배치 추가 실행 | 1개 검사 통과 (1440/390/320, 전체/주제 목록) |
| 주제 모의 회귀 | 23 통과 / 0 실패 |
| 기존 게시글 모의 회귀 | 49 통과 / 0 실패 |
| 실제 가입·인증·댓글/목록 count 연동 | 5개 묶음 통과 |
| 실제 공개 조회/404 | 5 통과 / 기존 무JS 404 표시 2 실패 |

첫 브라우저 시도에서는 수정 완료 전에 textarea 텍스트를 완료로 오인해 뒤로 간 테스트와, 모의 신규 댓글의 작성자 ID가 다른 회원인 fixture를 발견했다. 저장 완료 알림을 기다리고 신규 POST 작성자를 로그인 회원으로 맞춘 뒤 전체 댓글 검사를 재실행했다. 기존 게시글 모의 서버는 댓글 경로까지 상세 지연 설정에 넣어 연속 timeout을 만들었으므로 댓글 경로를 빈 댓글 계약으로 정확히 분리한 뒤 49개를 다시 실행했다. 시간 제한 증가나 실패 skip은 하지 않았다.

`layout/count-all-320.png`, `count-all-390.png`, `count-all-1440.png`와 대응 `count-topic-*`는 최대 숫자·긴 문구 검사용 모의 화면이다. `real/real-list-count-0.png`, `real-list-count-1.png`는 실제 새 검증 데이터의 화면이다.

수정·삭제 모의 회귀도 29개 통과했다. 최종 상태는 `feat/community-comment-count`의 미커밋 변경이며 백엔드 diff 없음. 직접 실행한 검증 서버8451/8452/8453만 종료하고 기존8443 프런트와8082 Spring은 유지했다. 커밋·push·병합·배포는 하지 않았다.
