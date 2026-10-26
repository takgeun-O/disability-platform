# 댓글 버튼 정돈·새로고침 토스트 — 2026-10-27

## 작업 기준

- 브랜치 `feat/community-comment-like`, HEAD `64547e3763f509f8d8be515331e170ee991754c6`.
- 직전 댓글 좋아요 연동의 미커밋/미추적 변경이 있는 상태에서 보존하고 이번 UI 보완만 추가했다. 루트/web AGENTS, 설치된 Next 16.3.6 use-client 문서 확인.
- 백엔드/DB/인증 정책/브랜드 색상 변경 없음. 커밋/push/병합/배포 없음.
- 작업 시작 대비 diff: `comment-ui-refresh-2026-10-27/this-followup.patch`. 직전 댓글 좋아요 구현 전체 diff와 구분된다.

## 변경

- `src/components/community/Comments.tsx`: 본문 아래 한 줄 도구 영역. 왼쪽 좋아요, 오른쪽 수정/삭제. 화면에는 짧은 ‘수정’/‘삭제’를 표시하고 접근성 이름 ‘댓글 수정’/‘댓글 삭제’, 기존 ID·권한·편집 전환·초점 복귀는 유지.
- `src/styles/community.css`: 댓글 좋아요만 테두리/알약 배경 제거, 하트16px/글자13px, 선택 시 기존 빨간 하트. 수정/삭제도 차분한 텍스트 버튼과 hover/focus 표시. 세 버튼 모두 최소44px 터치 영역 유지. 게시글 좋아요 버튼 스타일은 변경하지 않음.
- `Comments.tsx`: 명시적인 댓글 목록 새로고침/재시도 성공은 ‘댓글 목록을 새로고침했습니다.’ 토스트. 자동 조회·추가 조회·좋아요 상태 확인에는 성공 토스트를 만들지 않음. 기존 댓글 CRUD 성공 토스트의 이벤트 ID·유효성·5초+250ms 타이머를 공유한다.
- `src/features/PostList.tsx`: 최신 목록 새로고침 성공은 ‘게시글 목록을 새로고침했습니다.’ 토스트. 초기/인증/복귀 자동 조회에는 띄우지 않음. 진행 상태는 버튼의 ‘새로고침 중…’/aria-busy와 숨김 status로 안내해 본문을 밀지 않는다. 추가 로딩 및 오류·재시도 안내는 유지.
- 두 화면 모두 기존 `CommentSuccessToast` 재사용: 헤더 아래 중앙, polite status, 포커스 이동/클릭 가로막음 없음, 동일 문구 연속 성공도 마지막 이벤트부터5초 유지,250ms opacity 퇴장, 모션 감소 지원. 컴포넌트의 기존 타이머 정리와 세대/이탈 검사를 유지한다.
- 피드 재시도 직전 위치를 `load()`에 전달한다. 이번에 로딩 문구를 흐름에서 제거하자 오류 안내가 사라질 때 복원 기준이 달라지는 회귀가 발견됐다. 시작본에서는 해당 검사 통과, 첫 수정본에서는 실패했고, 오류 안내 제거 전 항목 위치를 보관하도록 최소 수정 후 동일 검사 및 전체 피드가 통과했다. 캐시/커서/이력 구조를 재설계하지 않았다.
- `tests/community-comments-browser.mjs`, `tests/community-feed-browser.mjs`: PC/모바일 도구 배치·44px·넘침·새로고침 성공/실패/반복 타이머/화면 이탈 검증 추가. 짧아진 화면 문구에 대한 초점 검사는 기존 접근성 이름으로 명확히 확인한다. 기존 실패 assertion을 삭제하거나 skip하지 않음.

## 이번 직접 검증

Node24.21.0 / pnpm10.34.3. 모의 API, 실제 회원/게시글/댓글 데이터 변경 없음.

| 명령 | 결과 |
|---|---|
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | 379 통과, 실패/skip0 |
| `pnpm build` | 통과 |
| `node tests/community-comments-browser.mjs` | 61/62 통과 |
| `node tests/community-feed-browser.mjs` | 최종26/26 통과 (최초25/26 → 위치 보완) |
| `node tests/comment-likes-browser.mjs` | 25/25 통과 |
| `git diff --check` | 통과 |

새 UI/토스트 전용 추가8개 검사는 모두 통과. 댓글/좋아요 전체 검사는 최종 댓글 UI 코드로 실행했다. 그 후 PostList의 오류 재시도 위치 보완을 추가하고 피드 전체·lint/typecheck/unit/build를 다시 실행했다. 관련 없는 댓글 전체를 다시 실행한 것처럼 기록하지 않는다.

별도 `/private/tmp/iyum-toast-clean-build`에서 최신 src를 동기화해 빌드하고 Next8464→mock8856을 사용했다. `BASE_URL=http://127.0.0.1:8464`, `COMMUNITY_ARTIFACT_DIR`, `PLAYWRIGHT_MODULE`을 지정했다. 시작본 비교는 별도8466→8857의 임시 사본으로 대상1개만 실행했으며, 최종 테스트 파일에는 필터를 남기지 않았다. 사용자8443/8082서버는 보존하고 직접 시작한 검증 서버만 종료했다.

남은 기존 실패: `count POST/PUT/DELETE uses server list; back/link preserve context and drafts`. 수정 초안 포기 확인을 처리하지 않고 삭제 확인창을 기다려 timeout. 직전 작업에서 기준64547e3에서도 재현했던 동일 실패이며, 이번에는 기존 실패를 수정 범위에 넣지 않았다. 이번 실행 결과61/62에 실패가 그대로 포함된다.

## 화면 및 한계

- `comments/compact-controls-{1440,390,320}.png`: 버튼 배치.
- `comments/refresh-toast-{1440,390,320}.png`: 댓글 새로고침 토스트.
- `feed/feed-refresh-toast-{1440,390,320}.png`: 게시글 목록 토스트.
- `likes/likes-390.png`: 선택된 작은 빨간 하트와 키보드 초점.
- 브라우저 가짜 시간으로5초+250ms 제거와 연속 성공 교체 확인. 기존 등록/수정/삭제 토스트·초안·화면 이탈 검사 유지.
- 이번 작업은 mock 검증이며 실제 Spring 변경 요청을 다시 수행하지 않았다. 실물 Safari·소프트 키보드·화면낭독기 미검증. Chrome viewport 및 DOM/접근성 상태 검사와 구분한다.
