# 상세 헤더 외곽선 버튼 — 2026-10-17

브랜치 feat/community-post-edit-delete, HEAD 7a3362f95021e9c873bafaa82c265b32612593f7. 기존 미커밋 작업 보존.

변경 소스는 PostActions.tsx(수정 링크의 전용 클래스)와 community.css(상세 헤더 전용 규칙)뿐이다. 헤더 배치/오른쪽 정렬/모바일 줄바꿈 유지. 15px/600, 1px 테두리, 8px 간격, 8×12px 내부 여백, 최소 44×44px. 수정은 기존 primary, 삭제는 muted 색상이며 최종 삭제 모달의 위험 강조는 그대로다. 공통 스타일, API/세션/CSRF, 늦은 응답 보호/조회 로직, 백엔드·preview worktree 변경 없음.

## 이번 실행 결과

- 기존 community-edit-delete-browser.mjs에서 관련 세 항목을 임시 스크립트로 선별해 실행: 작성자 ID 표시 조건, 1440/390/320 수정·삭제 레이아웃 및 Tab 순환, 긴 제목·닉네임/헤더/키보드 초점과 모달 복귀. 3개 통과, pageErrors 0.
- 같은 헤더 검사를 활용한 임시 계산 스타일 확인: 세 폭의 font-size 15px/font-weight 600/border 1px 및 hover 배경 확인 통과. 최초 hover 즉시 비교는 스타일 적용 중간 상태에서 실패했으므로, 고정 대기나 시간 제한 증가 대신 :hover와 최종 계산 색상 조건을 기다려 확인했다.
- pnpm typecheck, pnpm lint, git diff --check 통과.
- 현재 소스/테스트를 복사한 /private/tmp/iyum-edit-build-final/web에서 검사. 브라우저는 임시 Next dev 8453 + SSR/브라우저 모의 API 8857 사용. 실제 API 검증이 아니다.
- 이번에는 전체 단위/수정삭제/커뮤니티 회귀, 프로덕션 빌드, 실제 Spring 검증, 무JS404 검사를 재실행하지 않았다. 이전 검사 결과를 이번 통과로 합산하지 않는다. 기존 무JS404 실패 2건은 별도 알려진 이슈다.
- 저장소의 테스트 파일/의존성은 변경하거나 추가하지 않았다. 8443 기존 개발 서버 유지, 직접 실행한 임시 서버만 종료. 커밋/push 없음.

## 캡처 (현재 UI + 모의 데이터)

| 화면 | 1440 | 390 | 320 |
|---|---|---|---|
| 일반 헤더 | [PC](header-1440.png) | [390](header-390.png) | [320](header-320.png) |
| 긴 제목·닉네임 및 삭제 키보드 초점 | [PC](header-long-keyboard-1440.png) | [390](header-long-keyboard-390.png) | [320](header-long-keyboard-320.png) |

작업 중 화면은 실제 소스 렌더링이며 글/로그인 정보만 모의다. 원래 프런트 서버에서는 본인 게시글 상세를 열어 같은 스타일을 확인할 수 있다.
