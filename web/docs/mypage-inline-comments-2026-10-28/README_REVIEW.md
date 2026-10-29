# MyPage 게시글별 댓글 펼침 검토

## 기준과 범위
브랜치 feat/mypage-activity-redesign, 시작 HEAD 416fabd176918ef32d2cb33798c603024ad57b02.
기존 미커밋 마이페이지 1·2단계, PostEdit busy 보완, 상대 시간, CommentItem 추출을 보존했다. 이번 변경만은 review/this-task.patch, 누적 변경은 review/HEAD-to-current.patch를 참고한다. 백엔드·DB·사용자 서버 8443은 변경하지 않았으며 커밋/스테이징하지 않았다.

## 구현과 파일 역할
- components/member/InlineComments.tsx: 게시글별 펼침, 최초/추가/새로고침/재시도, 요청 소유권 및 인증 검사, 고유 ID, 공유 좋아요와 상세 링크.
- lib/inline-comments.ts: 원본 커서를 사용하는 누적 범위 재조회. 전체 성공 후에만 반영하며 재확인할 댓글까지 필요 범위를 읽는다.
- lib/member-activity.ts 및 components/member/MyActivity.tsx: 기존 회원별 메모리 캐시에 펼침/댓글 스냅샷 연결. 삭제된 부모 항목의 상태를 제거한다.
- components/community/PostListReactions.tsx: 마이페이지만 펼침 버튼을 주입하고 공개 목록의 기본 링크를 유지한다.
- components/community/CommentLikeButton.tsx: 선택적인 /mypage 로그인 복귀 경로. 상세 기본 경로와 쓰기 처리는 유지한다.
- styles/mypage.css: 자연스러운 페이지 스크롤, 기존 색상, 모바일 여백과 터치 영역.
- tests/inline-comments.test.mjs, inline-comments-browser.mjs: 새 경계/통합 검증. post-reactions-navigation-browser.mjs는 마이페이지의 변경된 버튼과 상세 링크를 각각 검증한다.

## 상태·인증·복원
각 게시글의 open/items/cursor/segments/error와 요청 소유권을 분리한다. 접힌 항목은 최초 조회하지 않고, 진행 중 접어도 완료 결과가 자동으로 펼치거나 초점을 이동하지 않는다. mounted/요청 식별자/AbortSignal/navigation/auth 세대/좋아요 read ticket을 응답 적용 때 검사한다. 취소만을 방어 근거로 삼지 않는다.
탭 및 상세 왕복에서는 기존 캐시 lease와 스크롤 복원 구조를 사용한다. 복원할 펼침 DOM을 먼저 렌더하고 부모 복원 중에는 자동 갱신을 보류한다. 변경 버전/인증 revision이 낡았을 때 필요한 범위를 재조회한다. 실패한 작업은 기존 목록을 보존하고 명시적 재시도로 복구한다. 같은 실패 상태에서 무한 자동 요청하지 않는다.
CommentItem, WrittenTime, CommentLikeButton 및 기존 공유 좋아요 저장소를 재사용한다. 늦은/불확실 쓰기 결과의 기존 변경 알림을 받아 누적 범위를 갱신하며 PUT/DELETE를 재전송하지 않는다. 내 댓글 하트는 읽기 전용이다.
POST_NOT_FOUND로 확인된 부모만 삭제 상태로 처리해 부모 목록을 무효화한다. 일반404/네트워크 오류는 삭제로 추정하지 않는다. 401은 현재 요청의 인증 보호를 거쳐 만료 처리한다.

## 검증 환경과 실행
Node 24.21.0, pnpm 10.34.3. 최종 소스와 동일한 격리 복사본 /private/tmp/iyum-inline-build에서 production build 및 별도 포트 8466/8467/8469 사용. 직접 시작한 서버는 모두 종료했다.
- pnpm test: 407/407 (새 단위 검사 4개 포함)
- pnpm lint: 통과
- pnpm typecheck: 통과
- pnpm build: 통과
- git diff --check: 통과
- node tests/inline-comments-browser.mjs: 최종 19/19
- node tests/member-posts-browser.mjs: 74/74
- node tests/member-comments-browser.mjs: 67/67
- node tests/community-comments-browser.mjs: 61/62
- node tests/comment-likes-browser.mjs: 25/25
- node tests/written-time-browser.mjs: 6/6
- node tests/post-reactions-navigation-browser.mjs: 10/10
최종 브라우저 합계 262/263. 모든 브라우저 검사는 제어 가능한 mock API이며 실제 Spring/DB 검증이 아니다.
실행 시 BASE_URL, COMMUNITY_SSR_MOCK_PORT, MEMBER_ARTIFACT_DIR 등 각 스크립트 상단 환경 변수를 지정했다. Playwright chromium(channel chrome) 사용. 외부 Google font CSS 지연을 피하기 위해 테스트 wrapper가 기존 CSS를 공급했다(helpers/에 포함). 제품 글꼴은 변경하지 않았다.
재실행 예: BASE_URL=http://127.0.0.1:8467 COMMUNITY_SSR_MOCK_PORT=8858 MEMBER_ARTIFACT_DIR=/tmp/inline-results node tests/inline-comments-browser.mjs. Next 서버의 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 로 동일 mock 포트를 설정해야 한다. 테스트 실행 파일 상단과 기존 next 설정의 환경 변수 계약을 참고한다.

## 실행 이력과 남은 실패
초기 신규 11개 통과 후 확대 검사 17개 중 2개가 테스트 동기화/fixture 오류로 실패했다. 최신 GET 응답과 finished를 기다리도록 수정하고 계정 전환 fixture의 post.author를 실제 로그인 계정과 맞췄다. 허용 오차나 제품 인증 검사를 완화하지 않았다. 이후 17/17, 최종 추가 401·부모 삭제 포함 19/19. 잘못된 실행 cwd의 MODULE_NOT_FOUND 로그도 과거 시도 기록으로 보존했다.
기존 알려진 상세 댓글 검사 `logout failure: detail URL/body, comment drafts, focus and dirty protection remain`은 scrollY 차이 <4 assertion에서 다시 실패했다. 그 앞 URL/본문/초안/초점 검사는 실행됐으나 같은 시나리오에서 그 뒤에 있는 이탈 방지 assertion은 실행되지 않았다. 다른 시나리오는 계속 실행됐다. 이번 작업에서 해결했다고 보고하지 않는다.
실제 iPhone/Safari, 소프트 키보드, 실제 화면낭독기, 실제 Spring 연동은 미실행. 접근성은 DOM/키보드/ID/숨김 상태 검사이며 실물 보조기기 검사와 다르다. 무JS 공개 목록의 기본 상세 링크 검사 통과; 마이페이지 개인정보는 기존 클라이언트 인증 흐름을 유지한다.

## 수동 확인
마이페이지 내 게시글에서 댓글 버튼을 누르면 해당 영역만 조회한다. 0개·여러 글 동시 펼침, 접기/재펼침, 더 보기·새로고침·실패 재시도, 탭 왕복/상세 왕복을 확인한다. 댓글 좋아요 변경 후 개수/채움 공유를 확인한다. 1440/390/320 캡처는 evidence/new-complete/에 있다.

## 후속 작성 단계
이번에는 작성/수정/삭제 입력창과 초안·이탈 방지를 추가하지 않았다. 다음 단계에서는 postId별 초안 소유권, 인증 변경 정리, 여러 초안을 합산하는 navigation guard 등록/해제를 먼저 설계해야 한다. 상세 Comments 전체를 복제하지 않는다. 기존 목록/커서/좋아요 API로 이번 범위 구현 가능하며 새 백엔드 계약은 필요하지 않았다. 대상 댓글 단건 조회가 없어 불확실 좋아요 재확인은 기존 커서 prefix를 순회한다.

## 패키징
최신 소스/테스트/public/설정/patches 및 필요한 백엔드 참조만 포함. 실제 .env, 인증정보, 쿠키/토큰, DB, 의존성, 빌드 산출물, 이전 ZIP, 무관한 디자인은 제외. fixture@example.test 등 테스트 전용 가상 데이터는 실제 개인정보가 아니다. CRC 및 현재 소스 바이트 일치 확인 후 생성한다.
