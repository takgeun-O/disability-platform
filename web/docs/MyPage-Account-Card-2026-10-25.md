# 마이페이지 계정 관리 카드 — 2026-10-25

브랜치 `feat/member-password-change`, HEAD `bcdeaa31d7c090ab0ad8a5acc1fed86f186addbb`에서 기존 미커밋 구현을 보존했다. 이번 서비스 코드 수정은 `src/features/MyPage.tsx`, `src/styles/mypage.css` 두 파일의 계정 영역 마크업·스타일에 한정한다. [이번 시작본 대비 diff](mypage-account-card-2026-10-25/this-followup.patch).

## 변경

- 첨부 IYUM_mypage.png처럼 이메일과 비밀번호를 얇은 테두리의 한 카드로 통합했다.
- 비밀번호는 왼쪽 제목, 오른쪽 청록색 `변경 ›`이며 행 전체가 버튼이다. 접근성 이름은 기존 `비밀번호 변경`을 유지했다.
- 회원 탈퇴는 카드 아래 중앙의 차분한 버튼으로 분리했다. 클릭 영역은 44px 이상이며 hover·focus-visible을 유지했다.
- 이메일의 실제 값과 읽기 전용 표시는 유지한다. 시안의 예시 인증 완료 배지나 개인정보를 추가하지 않았다.
- 버튼 ID·클릭 처리·모달·초점 복귀·토스트·인증/API/CSRF 로직 변경 없음. 백엔드·DB·의존성 변경 없음.

## 이번 실행 결과

web에서 `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`를 실행했다. 모두 통과하며 단위 테스트 325개 통과(skip 0). `git diff --check` 통과.

별도 8459 프로덕션 Next + 모의 API + Chrome headless:

| 검사 | 결과 |
| --- | --- |
| 기존 `tests/password-change-browser.mjs` | 32/32 통과 |
| 기존 `tests/withdrawal-browser.mjs` | 11/11 통과 |
| 기존 `tests/member-success-toast-browser.mjs` | 15/15 통과 |
| 이번 화면 확인 스크립트 | 1440·390·320px 3개 모두 통과 |

화면 확인은 카드 내부 이메일·비밀번호, 탈퇴 버튼의 분리·중앙 배치, 44px 이상 터치 영역, 긴 이메일 넘침 없음, Tab/Shift+Tab/Enter/Escape 및 두 모달 종료 후 초점 복귀를 직접 확인했다. 기능 테스트를 새로 늘리지 않고 기존 회귀 검사를 실행했다. 화면 확인 스크립트는 [visual-check.mjs](mypage-account-card-2026-10-25/visual-check.mjs)이며 GET 모의 응답만 사용한다. Playwright 모듈 경로를 `PLAYWRIGHT_MODULE`로 지정하고 8459 서버에 실행했다.

## 캡처

| 화면 | PC 1440 | 모바일 390 | 모바일 320 |
| --- | --- | --- | --- |
| 전체 | [보기](mypage-account-card-2026-10-25/page-1440.png) | [보기](mypage-account-card-2026-10-25/page-390.png) | [보기](mypage-account-card-2026-10-25/page-320.png) |
| 계정 관리 | [보기](mypage-account-card-2026-10-25/account-1440.png) | [보기](mypage-account-card-2026-10-25/account-390.png) | [보기](mypage-account-card-2026-10-25/account-320.png) |
| 키보드 초점 | [보기](mypage-account-card-2026-10-25/keyboard-1440.png) | [보기](mypage-account-card-2026-10-25/keyboard-390.png) | [보기](mypage-account-card-2026-10-25/keyboard-320.png) |

[긴 이메일 320px](mypage-account-card-2026-10-25/long-email-320.png). 캡처는 실제 계정 대신 모의 데이터다.

## 한계와 보존

실제 Spring 변경 요청·실제 계정의 비밀번호 변경/탈퇴를 실행하지 않았다. 모바일 검증은 브라우저 에뮬레이션이며 실물 iPhone Safari·소프트 키보드는 미검증이다. 이전 테스트 기록을 이번 결과로 사용하지 않았다.

기존 8443 개발 서버·8082 백엔드·preview worktree를 보존하고, 직접 시작한 8459 서버만 검증 후 종료한다. 빌드가 갱신한 next-env.d.ts는 이번 시작본으로 복원했다. 커밋·푸시·병합·배포 없음.
