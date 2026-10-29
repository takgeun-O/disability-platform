# 인라인 댓글 등록 토스트 및 재펼침 페이지 초기화

- 브랜치: feat/mypage-activity-redesign
- 시작/최종 HEAD: 63b12f4041e79d273cba1ed169383fa9ef34abb7
- 기존 미커밋 인라인 작성·초안 보존·오류 위치 및 직전 등록 성공 기록 구현을 보존한 후속 수정. 커밋/푸시/백엔드/DB 변경 없음.

## 변경

- InlineComments: 작성창 아래 영구적인 '방금 등록한 댓글' 영역 제거. 명시적인 접기/펼치기 시 기존 조회를 abort하고 controller 소유권을 해제한다. emptyInlineComments의 목록/페이지/커서/오류 상태로 되돌리되 선택 정렬은 유지한다. 다시 펼치면 기존 자동 조회 경로로 첫 20개를 받아 서버 nextCursor로 더 보기를 진행한다.
- InlineCommentForm: 성공이 확정되고 화면/인증/요청이 유효할 때 실제 제출 원문을 기존 onSuccess로 전달한다. 현재 입력값 또는 추측한 데이터로 성공 표시하지 않는다.
- MyActivity: 성공 토스트 하나에 최신 제출 원문을 전달한다. 기존 식별자 기반 5초 수명과 250ms fade, 새 성공 시 타이머 재시작을 재사용한다.
- CommentSuccessToast / community.css: 선택적 detail 미리보기(최대 3줄)를 추가. 본문 흐름 밖의 기존 상단 중앙 토스트, 클릭 차단 없음. 미리보기는 aria-hidden이며 polite region은 성공 문구만 안내한다. 다른 기능의 토스트는 detail 없이 기존 동작을 유지한다.
- inline-comments-browser.mjs: 새 UX에 맞춘 검증, 세 정렬의 45→접기→첫20→다음20, 원문 커서/인기순 likeCount, 초안 유지, 늦은 추가조회 소유권, toast lifetime 및 1440/390/320 캡처.

직전 comment-created 저장소의 ID 합류·인증/화면 수명 코드는 유지한다. UI에서 기록을 다시 영구 표시하지 않는다. 성공 기록은 초안과 달리 이탈 보호를 발생시키지 않는다. 기존 단위 테스트에 record/confirm 양방향 순서·삭제·계정 격리 검증이 포함되어 있다.

## 정책 구분

- 직접 댓글 버튼으로 접기/재펼치기: 첫 페이지 재조회. 접기 중 새 GET 없음. 이전 more/refresh catch/finally는 controller 소유권 불일치로 새 요청을 변경하지 못함.
- 활동 탭 전환/상세 왕복: 직접 접기와 다르므로 기존 누적 범위·펼침·스크롤 복원 유지.
- 초안·선택 정렬: 접어도 보존. 서버 cursor 문자열을 임의 생성하거나 날짜로 재작성하지 않음.
- 등록 확정 성공: 토스트 표시/해당 초안 초기화/기존 무효화. GET 실패는 별도 목록 오류. 불확실한 POST는 성공 미리보기 없음. 자동 쓰기 재전송 없음.
- 토스트는 새 성공 이벤트에만 표시. 목록 재조회나 접기·탭 복원으로 과거 안내를 재발행하지 않음.

## 실행

Node24.21.0, pnpm10.34.3. 원본 소스와 바이트가 동일한 /private/tmp/iyum-inline-build에서 빌드 및 서버 실행. 사용자 8443 유지, 자체 8467/8466과 mock8858/8856만 사용.

- pnpm test: 422/422
- pnpm lint / pnpm exec tsc --noEmit: 통과
- pnpm typecheck (격리 복사본): 통과
- pnpm build (Turbopack): 환경 실패. PostCSS worker 포트 바인딩 Operation not permitted. 권한 요청 후에도 동일. 실패 로그 보존.
- NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build --webpack: 통과. 최종 제품 소스 기준 프로덕션 브라우저 검증.
- zsh review/run-browser-local.sh tests/inline-comments-browser.mjs 8467 inline-verified: 최종 결과는 아래 및 results.json 참조.
- zsh review/run-browser-local.sh tests/community-comments-browser.mjs 8466 detail: 76/76 통과.
- git diff --check: 통과.

브라우저 helper는 로컬 경로 예시다. 실제 실행은 저장소 web에서 PLAYWRIGHT_MODULE/PLAYWRIGHT_REAL_MODULE, BASE_URL, MEMBER_ARTIFACT_DIR 또는 COMMUNITY_ARTIFACT_DIR을 설치 환경에 맞춰 지정한다. 공개 API 주소는 브라우저 route mock이 처리하며 실제 API로 쓰기 요청하지 않았다.

## 중간 실패 보존

- inline 첫 실행: 69/73. 실패 4건은 영구 기록을 기다리던 로그아웃 검사 1건, 재펼침 후 GET 1회 누적을 기대하던 기존 검사 3건. 새 요구사항에 맞춰 영구 표시 부재/인증·초안 보존 및 첫 페이지 GET 1회 추가를 검사하도록 변경.
- inline-final 중간 실행: 71/74. 세 화면 폭의 이전 GET 횟수 기대값을 갱신하기 전 실행. 로그 보존. 새 늦은 more 응답 소유권 테스트 포함.
- 최종 결과: inline-verified 74/74 통과. 실패 없음.

과거 70/70 등 직전 작업의 검증은 이번 실행 결과가 아니다. 관련 기존 보고서는 별도 폴더로 포함.

## 제한 / 수동 확인

실제 Spring/DB·실계정 쓰기, 실기기 Safari/소프트 키보드/실제 화면낭독기 미검증. mock Chrome 및 DOM 접근성 속성 검사. 상세 로그아웃 실패 후 스크롤 간헐 항목은 이번 실행에서 통과했지만 원인 해결이라고 주장하지 않는다. 별도 댓글 좋아요 suite의 취소된 과거 GET 대기 문제는 이번에 별도 실행/수정하지 않음.

댓글이 45개 이상인 내 게시글을 펼쳐 등록순/최신순/인기순 각각 끝까지 조회한 후 접고 다시 펼친다. 20개와 더 보기만 표시되고 다음 서버 커서로 추가 조회된다. 초안은 보존되어야 한다. 댓글 등록 시 상단 성공 토스트와 원문 미리보기가 뜨고 약5초 후 사라진다. 긴 본문은 최대3줄 미리보기, 영구 등록 영역 없음.

## 패키징

start-to-current.patch는 이번 요청 직전 대비, HEAD-to-current-tracked.patch는 이전 미커밋 구현을 포함한다. 미추적 파일 실제 내용, 작업 전후 status, 검증 로그/JSON/캡처, SHA-256 목록 포함. .env/실제 인증정보/쿠키/세션/DB/node_modules/.next/.git/기존ZIP 제외. mock-only 문자열은 테스트 fixture이며 실제 인증정보가 아니다.
