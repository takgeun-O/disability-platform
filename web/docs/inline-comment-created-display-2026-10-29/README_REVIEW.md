# 마이페이지 인라인 댓글: 방금 등록한 댓글 표시

## 기준 및 기존 작업
- 브랜치 feat/mypage-activity-redesign, 작업 시작 HEAD 63b12f4041e79d273cba1ed169383fa9ef34abb7.
- 시작부터 인라인 작성, 초안 수명/이탈 보호, 페이지 경계 초안 보존, 상세 작성란 위치와 오류 위치, 주의 토스트 등 미커밋 변경이 있었습니다. 그대로 보존했습니다.
- 이번 변경은 인라인 등록 성공 기록의 표시·합류·수명 관리와 해당 테스트입니다. 상세 Comments 등록 구현은 이번에 변경하지 않았습니다.
- 백엔드/DB/API, 실제 사용자 데이터 수정 없음. 커밋/푸시/병합/배포 없음. 사용자 개발 서버8443은 종료하지 않고8466/8467 별도 서버에서 mock 검사했습니다.

## 계약과 기록
기존 CommentController POST /api/v1/community/posts/{postId}/comments는201과 commentId를 반환합니다. 기존 changeComment는 입력/CSRF 준비 및 요청 유효성/성공 commentId 검증/오류 분류를 담당합니다. 유효한 반환 ID 이후에만 실제 제출 당시 text를 기록합니다. 서버 시각, likeCount/liked, 수정 여부는 생성하지 않습니다. 불확실한 응답에는 성공 기록이 없습니다.

## 저장 위치와 수명
- `web/src/lib/comment-created.ts`: 별도 memory-only 저장소. owner와 activity generation, post별 삭제 generation을 사용합니다. records는 postId별 {commentId,content} 배열이고, seen은 유효한 목록에서 확인한 ID 집합입니다.
- `AuthProvider.tsx`: 초안과 동일한 인증 수명을 연결합니다. 확정 anonymous, 새 로그인/계정 교체는 초기화합니다. 일시 checking/error 및 실패한 logout은 폐기하지 않습니다. private UI는 기존 인증 조건으로 감춥니다.
- `MyPage.tsx`의 기존 DraftProtection: 승인된 navigation에서 초안과 성공 기록을 함께 clear합니다. dirty 합산은 여전히 미등록 drafts만 사용합니다. 기록만 남았을 때 이탈 확인하지 않습니다.
- `MemberPostActions.tsx`, `InlineComments.tsx`, `InlineCommentForm.tsx`: 기존 삭제 확정 근거(DELETE 성공 또는404 POST_NOT_FOUND)에만 해당 게시글 기록도 정리합니다. 일반404/네트워크/목록 누락은 정리 근거가 아닙니다.
- 접기/탭/페이지 경계 이동은 저장소를 지우지 않습니다. 화면에 다시 나타나면 미확인 기록이 복원됩니다. 영구 저장/폴링은 없습니다.

## 생성과 합류, 응답 순서
- Form은 요청 시작 시 저장소 ticket을 잡고, 유효한 성공 후 기존 화면/인증/인스턴스 current 검사를 통과해야만 ticket.record(result,text)를 호출합니다. 기존 성공 토스트와 해당 초안 초기화도 유지합니다.
- InlineComments는 accepted GET의 요청/AbortSignal/정렬/인증/댓글 변경 버전 검사를 모두 통과하고 커서 검증까지 완료한 다음 ticket.confirm(data.items IDs)를 호출합니다. 실패하거나 거절된 응답, 캐시 복원만으로 확인 처리하지 않습니다.
- record→confirm이면 records에서 실제 제거합니다. confirm→record이면 seen에 이미 있는 ID라 기록을 추가하지 않습니다. 이후 다른 정렬/범위로 이동해도 제거한 기록은 다시 나타나지 않습니다.
- 명시적으로 clear되거나 다른 owner/게시글 삭제 generation으로 넘어가면 기존 ticket의 record/confirm은 무시됩니다. 외부 기존 요청 소유권 검사도 함께 유지합니다.
- 같은 본문이라도 다른 commentId이면 독립 기록입니다. 같은 ID의 중복 성공은 기록을 중복 추가하거나 원문을 바꾸지 않습니다.

## UI 및 기존 갱신 유지
- InlineComments 작성창 아래, 정렬 메뉴/서버 목록 위에 별도 section. 제목 '방금 등록한 댓글', 안내 '등록이 완료됐어요. 목록에서 확인되면 이 표시가 사라져요.'와 원문만 표시합니다.
- 기존 community-comment-receipts/body 스타일 사용. 제목 h4도 기존 제목 스타일을 적용합니다. 줄바꿈/긴 문자열 표시를 보존합니다.
- 기록에 버튼/time/좋아요/수정/삭제 없음. 별도 live region도 추가하지 않고 기존 성공 토스트에서만 성공을 안내합니다. 강제 스크롤/초점 이동 없음.
- records를 서버 정렬 배열에 삽입하지 않습니다. 현재 sort/원문 커서/누적 페이지 수를 유지하고 기존 postCommentsChanged(postId), postListChanged를 그대로 사용합니다. 댓글 수를 따로 더하지 않으며 다른 post의 댓글 GET을 발행하지 않습니다.
- 등록 확정 뒤 GET실패는 records와 성공 상태를 유지하고 조회 오류를 표시합니다. 재시도에서 ID가 확인되면 정리합니다. 새 ID를 찾으려고 자동으로 마지막 페이지까지 조회하지 않습니다.

## 변경 파일
제품: comment-created.ts(신규), AuthProvider.tsx, MyPage.tsx, InlineCommentForm.tsx, InlineComments.tsx, MemberPostActions.tsx, community.css.
테스트: comment-created.test.mjs(신규), inline-comments-browser.mjs.
기존 MyActivity/Comments/comment-drafts 등은 패키지에 포함되지만 이번 기능을 위해 수정하지 않았습니다.

## 실제 검증
Node24.21.0, pnpm10.34.3. 실제 Spring/DB가 아닌 프로덕션 Next + controllable mock API.
- pnpm test: 422/422 통과.
- pnpm lint: 통과 (최종 테스트 보강 후 재실행 로그 lint-final.log).
- pnpm exec tsc --noEmit: 통과.
- pnpm typecheck: 동일 최종 src의 별도 빌드 폴더에서 통과.
- pnpm build: /private/tmp/iyum-inline-build의 동일 최종 src로 통과.
- 상세 community-comments-browser.mjs:76/76 통과.
- 인라인 첫 실행69/69. 이후 취소를 무시하는 이전 정렬 응답 검사1개 및 기존 삭제 검사에 성공 기록 검증을 추가했고 최종 70/70 통과 (inline-final/results.json).
- git diff --check 통과.

단위5개: record/confirm 양쪽 순서, ID별부분합류/같은본문/게시글격리, clear·인증변경·동일owner, 명시삭제 및 oldticket 차단.
브라우저 추가9개: 연속동일본문2개+더보기합류/재등장방지, 인기순긴본문1440/390/320+접기탭+최신순합류, accepted GET-before-POST gate, transport가 abort무시하는 이전정렬 GET의 확인 금지, 성공+GET503+복구, 페이지경계복원 및 기록만남으면 이탈확인없음, 로그아웃실패보존/확정로그아웃정리.
기존 실패·불확실·지연응답·이탈·계정변경 검사에는 기록 부재 assertion을 추가했습니다. 기존 삭제4분기에 확정/일반404/네트워크 결과별 기록 정리 여부도 확인합니다. 이전 초안 유실 방지와 등록 오류 위치 검사도 실행됩니다.

## 알려진 항목/미검증
- 상세 logout failure 스크롤 검사는 이번에는 통과했고 이후 assertion도 실행되었습니다. 간헐 문제 원인을 해결한 것으로 간주하지 않습니다.
- 댓글 좋아요 suite의 취소된 이전GET 응답 대기 문제는 이번에 별도 실행하지 않았습니다. 인라인 suite의 좋아요/인증/정렬 경합은 실행했지만 별도 suite 전체 통과라고 표현하지 않습니다.
- 실제 Spring 연동, 실기기 Safari/소프트키보드/실제 화면낭독기 미실행. Chromium viewport와 DOM접근성 확인입니다.
- 승인된 이탈 이후 records는 폐기됩니다. 서버 목록 범위 밖의 댓글을 찾는 자동 조회나 실시간 감지는 추가하지 않았습니다.

## 실행 및 수동 확인
web에서 pnpm install --frozen-lockfile 후 lint/typecheck/test/build. 브라우저는 Playwright+Chrome 필요.
실행 명령: `zsh /private/tmp/run-created.sh tests/inline-comments-browser.mjs 8467 inline-final`, `zsh /private/tmp/run-created.sh tests/community-comments-browser.mjs 8466 detail`.
Next public API base=http://127.0.0.1:8082. SSR용 IYUM_API_INTERNAL_URL은 인라인 mock8858/상세mock8856. 각 테스트가 mock을 시작합니다. 동봉한 실행 helper의 로컬 절대 경로는 검토 환경에 맞게 바꿔야 합니다.
수동: 댓글45개인 게시글을 등록순으로20개 조회→등록→기록표시→같은내용등록→별개ID기록→더보기끝에서 서버ID확인시 제거→인기순/최신순전환 후 재등장없음. GET실패 상태에서는 성공기록 유지, 다시조회시합류. 다른 게시글 초안은 보존됩니다.

## 패키지
최신 web/src/tests/public, 설정·lockfile·patch·AGENTS, 계약참조, 시작HEAD/status, 시작대비patch(신규 포함), HEAD누적patch, 검증 로그/JSON/모의 캡처, SHA-256 포함.
시작status의 미커밋 변경은 이전 작업입니다. start-to-current.patch는 이번 보완만 구분합니다. 기존검토ZIP/환경변수/인증정보/실제세션/쿠키/토큰/DB/node_modules/.next/.git/빌드산출물 제외. 테스트의 고정 mock 문자열은 실제 인증정보가 아닙니다.
