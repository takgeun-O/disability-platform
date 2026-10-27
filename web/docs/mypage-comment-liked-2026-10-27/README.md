# 내 댓글 liked 연결 검토

## 기준과 범위
브랜치 feat/mypage-comment-like-count, 시작/최종 HEAD 595ef8d8007e8d717c7b0adc6035621d3643b001 (feat(member): 내 댓글 조회 응답에 본인 좋아요 여부 추가).
기존 내 댓글 개수 표시·본문 오른쪽 배치·상세 이탈 후 불확실 응답 무효화는 미커밋 상태로 존재했고 그대로 보존했다. 백엔드/DB/인증 정책/레이아웃 변경 없음. 커밋/push/병합/배포 없음.

## 계약과 구현
현재 MemberCommentItemResult/Response의 boolean liked와 MemberCommentReadService의 getStates(commentIds, memberId), 관련 서비스/세션 테스트에서 현재 회원 기준임을 확인했다.
- web/src/lib/member-api.ts: MemberCommentItem에 liked:boolean 추가. typeof v.liked === 'boolean' 엄격 검사. 누락/null/문자열/숫자를 false로 변환하지 않고 UNEXPECTED_RESPONSE 처리. 기존 개수/커서/문자열/순서 검증 유지.
- web/src/components/member/MyComments.tsx: data-liked 및 fill로 읽기 전용 하트 표시. 기존 post-like-count[data-liked="true"] svg 색상 #c43f50 스타일 재사용. 다른 회원만 좋아요한 양수/false는 빈 하트. 위치/숫자 형식/0 정책 유지.
- 화면낭독기에 보이는 숫자 + 숨김 상태 설명(개, 내가 좋아요한 댓글/좋아요하지 않은 댓글)을 제공. SVG는 aria-hidden. 버튼/탭 포커스/aria-pressed/클릭 동작 없음.
- member-comments.test.mjs: boolean true/false 허용, 누락/null/문자열/숫자/객체/배열 거절 및 개수와 독립적인 상태 검사 추가.
- member-comments-browser.mjs: 3페이지 항목별 상태/채움/접근성 검사, 잘못된 liked 6종 재시도, 양수+빈하트, 모바일 캡처. 기존 등록/취소·정상 지연 성공·불확실 응답·인증 전환에 상태 검사 추가. 접근성 설명을 제외한 visible count를 선택하도록 기존 숫자 assertion 대상을 구체화(삭제/완화 아님).
- member-comments-fix-browser.mjs, member-404-race-browser.mjs, password-change-race-browser.mjs: 내 댓글 mock 응답에 명시적 liked:false 추가.

기존 개인 목록은 응답 객체를 그대로 저장/재조회하여 count와 liked를 함께 갱신한다. 별도 전역 상태/폴링/개수 추측 없음. CommentLikeButton.tsx의 기존 미커밋 재조회 누락 보완은 이번에 수정하지 않았다. 회원별 캐시/요청 세대/3페이지 갱신/스크롤 복원을 유지한다.

## 검증
이번 직접 실행 결과: pnpm test 381/381 (실패/skip0), pnpm lint 통과, pnpm typecheck 통과, pnpm build 통과, git diff --check 통과. 내 댓글 브라우저 전체61/61, 댓글 좋아요25/25, 인증 응답 경합37/37. 이번 실행에서 남은 실패 없음.
캡처: member-comments/liked-1440.png, liked-390.png, liked-320.png (false/0, true/1, false/2). 긴 본문·큰 숫자 기존 캡처도 같은 폴더에 포함.
이번 결과는 같은 폴더 로그와 results.json 참조. 이전 작업의 문서는 과거 기록이며 이번 결과와 혼동하지 않는다.
모든 브라우저 검사는 프로덕션 Next + 모의 API/Chrome이다. 실제 Spring/실제 계정/DB 검증 미실행. 실제 데이터 변경 없음. 모바일 viewport 검증과 실기기 Safari/소프트키보드/화면낭독기 실사용 검증은 다르며 후자는 미실행.
별도 member-comments-fix-browser/member-404-race-browser 전체는 fixture만 갱신하고 미실행. 기존 404 문서 경합, 댓글 조회 오류 안내, 로그아웃 스크롤 문제는 범위 밖이며 해결을 주장하지 않는다.

## 실행 명령
Node24 / pnpm10.34.3. web에서 pnpm test, pnpm lint, pnpm typecheck.
사용자8443 개발 서버를 보존하기 위해 /private/tmp/iyum-member-count-build에 최신 src를 복사한 후:
`NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build`
`IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8465`
- BASE_URL=http://127.0.0.1:8465 node tests/member-comments-browser.mjs
- BASE_URL=http://127.0.0.1:8465 node tests/password-change-race-browser.mjs
- 댓글 좋아요는 내부 mock8856/Next8464의 별도 서버 후 BASE_URL=http://127.0.0.1:8464 node tests/comment-likes-browser.mjs
PLAYWRIGHT_MODULE은 설치된 Playwright 경로. MEMBER_ARTIFACT_DIR/COMMUNITY_ARTIFACT_DIR/PASSWORD_CHANGE_RACE_DIR로 이번 결과를 분리. 테스트 전용 서버만 정리.

## 수동 확인
내 댓글 탭에서 0/false, 양수/false, 양수/true를 비교한다. 더보기2회로 다음 페이지도 확인. 상세에서 등록/취소 후 뒤로가기와 마이페이지 링크로 복귀해 개수·하트·27개 범위·위치를 확인한다. 모의 gate로 응답을 지연해 상세 이탈 후 갱신과 인증 전환 후 이전 응답 무시를 확인한다. 실제 DB에 시험 데이터를 만들 필요 없음.

## 검토 패키지
최신 web/src/tests/public/실행 설정/patches/lock/AGENTS, 백엔드 계약 참고 소스/테스트 포함. 후자는 전체 백엔드 실행 프로젝트가 아닌 참고 파일이다.
review/HEAD-to-current.patch는 현재 HEAD 대비 누적 변경, review/this-task.patch는 작업 시작 스냅샷 대비 이번 7개 파일 변경. review/existing-at-start.patch로 기존 미커밋 구현 구분. 신규 검증 문서/로그/캡처는 실제 파일과 목록 포함.
.env, 인증정보/실제쿠키·토큰/DB덤프, .git/node_modules/.next/빌드산출물/기존ZIP 제외. 테스트의 example.test/test-only 합성 fixture는 재현에 필요한 가짜 데이터로 유지. 원본 마스킹 수정 없음.
