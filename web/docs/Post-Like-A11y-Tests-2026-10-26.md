# 좋아요 접근성 및 최신 UI 테스트 보완 — 2026-10-26

## 기준과 변경 구분
브랜치 feat/community-post-like, HEAD 7b0fe586886c64bd45d17ced58e781385b9c6de8.
시작 시 기존 좋아요 프런트 구현, 읽기 전용 목록, 댓글 링크, 복귀 문구 등이 미커밋/미추적으로 존재했다. 이를 보존했다. 이번 제품 코드 변경은 PostListReactions.tsx의 조건부 sr-only 설명 한 곳이다. 백엔드·인증·CSRF·라우팅·복원·디자인 동작 변경 없음. 커밋/stage/push/DB 변경 없음.

## 접근성
본인 liked=true일 때만 `내가 좋아요한 게시글 · ` 숨김 텍스트를 추가. 좋아요 개수는 계속 읽힌다. SVG aria-hidden 유지, tabIndex/aria-pressed/live region 추가 없음. Chrome 접근성 스냅샷으로 선택/취소/로그아웃/다른 회원 상태와 개수 확인. 선택 상태 자료는 likes/selected-a11y.txt, 취소 후 자료는 likes/readonly-a11y.txt. 실물 스크린리더 사용 검증과는 다르다.

## 테스트 갱신
- 상세 복귀 문구만 목적지별 정확한 문자열로 갱신. 작성 화면과 전용 404의 기존 문구는 유지. href/쿼리/탭/스크롤 검증 유지.
- post-likes-browser: 상세에서만 PUT/DELETE. 목록·내 게시글 읽기 전용 상태와 실제 서버 응답 숫자, 직접 GET 재확인, 자동 재전송 없음, 중복 요청/화면 이동 후 잠금, 다른 게시글 독립 처리, 이전 GET, 같은 회원/다른 회원 재로그인 및 로그아웃 뒤 늦은 200/401/finally 검증.
- 동시 게시글 잠금은 화면 이동과 기존 post-likes.test.mjs의 저장소 단위 검사를 함께 사용. 검사용 UI 추가 없음.
- post-reactions-navigation: 전체/마이페이지/주제 각각 1440/390/320, 댓글 링크·초점·상하단 복귀 문구/href, 무JS 실제 링크.
- 기존 feed/member-posts/comments 테스트의 사라진 댓글 span 클래스는 현재 .post-comment-link로 변경. 숫자 로케일 표기와 준비 조건을 현재 UI에 맞춤. 기존 개수·재조회·경합 assertion은 유지.

## 이번 직접 실행 결과
Node /opt/homebrew/opt/node@24/bin/node, pnpm10.34.3 사용.
- pnpm lint: 통과
- pnpm typecheck: 통과
- pnpm test: 348/348, 실패/skip 0
- pnpm build: 통과. 기존 8443 .next를 보호하기 위해 /private/tmp/iyum-like-validation 복사본에서 실행. 이번 최종 제품 src와 동일함.
- git diff --check: 통과
- 프로덕션 모의 브라우저 211개 통과: likes28, navigation10, edit/delete30, feed22, member-posts41, member-comments30, comments50.
- 별도 구형 검사 2파일은 첫 검사에서 실패하고 후속 검사는 진행되지 않음. 아래 제한 참조. 전체 검사가 모두 통과했다고 주장하지 않음.

실행 환경: NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082, IYUM_API_INTERNAL_URL=http://127.0.0.1:8858, Next 8464. feed/edit/comments 및 구형 검사는 internal8856, Next8465. 브라우저 API는 Playwright route, SSR API는 로컬 mock server. 실제 8082 회원 API를 변경하지 않음.
명령: `BASE_URL=http://127.0.0.1:8464 PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs node tests/<suite>.mjs`.
likes/navigation은 LIKE_ARTIFACT_DIR, member는 MEMBER_ARTIFACT_DIR. edit/feed/comments는 BASE_URL=...8465와 COMMUNITY_ARTIFACT_DIR. 구형 community/topics는 COMMUNITY_SSR_MOCK_PORT=8856 추가. 테스트 파일 자체의 기본 포트도 확인할 것. 같은 mock 포트를 사용하는 suite는 순차 실행.

## 실패 진단과 남은 제한
1. 최초 좋아요 재작성 검사에서 404 복귀를 브라우저 GET만 세어 실패. 기존 목록을 먼저 읽은 뒤 삭제 및 복귀 순서를 명확히 하고 SSR+브라우저 조회 모두 기록하여 최종 통과. 누락 항목 및 재조회 assertion 유지.
2. 댓글 초기 실행의 2건: 읽기 전용 전환 후 없어진 data-like-ready 버튼 대기, 큰 댓글 수 쉼표 표기 불일치. 선택자/숫자 기대값을 최신 UI로 수정해 전체 50개 재실행 통과.
3. community-topics-browser.mjs: 구형 제목 `보청기 커뮤니티` 기대에 현재 `보청기` 불일치. 현재 피드 이전의 hero/필터/페이지 번호 UI도 전제함. 이번 변경 전 스냅샷에도 동일 기대값 존재. 정확한 상세 복귀 문구만 수정했고, 구형 UI 전체 이식은 이번 보완 범위에서 하지 않음. 실패를 삭제/skip/완화하지 않음. 최신 주제 왕복은 navigation10 및 feed22에서 검증.
4. community-browser.mjs: mock이 /posts/feed를 게시글 ID로 취급해 404를 반환하므로 첫 SSR 목록 제목 검사 실패. 기존 스냅샷에서도 동일 mock과 페이지 번호 UI 전제. 전체 구형 suite의 피드 이식은 남은 테스트 정비 작업. 현재 생성/조회/필터/복귀는 최신 feed/edit/member 검사로 별도 검증.
5. 실제 Spring 계정 테스트와 실물 모바일 Safari/화면 낭독기는 실행하지 않음. 로컬 실제 계정용 테스트는 문자열/선택자만 맞추고 실행하지 않음. 별도 404 전체 suite는 재실행하지 않음(내 댓글 suite의 삭제 원문 404 왕복은 통과).

## 변경 파일
- web/src/components/community/PostListReactions.tsx
- web/tests/community-browser.mjs
- web/tests/community-comments-browser.mjs
- web/tests/community-edit-delete-browser.mjs
- web/tests/community-edit-delete-local.mjs
- web/tests/community-feed-browser.mjs
- web/tests/community-feed-local.mjs
- web/tests/community-feed-seed-local.mjs
- web/tests/community-local-browser.mjs
- web/tests/community-public-browser.mjs
- web/tests/community-topics-browser.mjs
- web/tests/member-comments-browser.mjs
- web/tests/member-posts-browser.mjs
- web/tests/post-likes-browser.mjs
- web/tests/post-reactions-navigation-browser.mjs
