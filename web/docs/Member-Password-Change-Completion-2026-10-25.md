# 비밀번호 변경 완료 안내 우선순위 — 2026-10-25

브랜치 `feat/member-password-change`, HEAD `bcdeaa31d7c090ab0ad8a5acc1fed86f186addbb`. 기존 비밀번호 변경·인증 응답 경합 관련 미커밋 작업을 보존했다. 시작본은 저장소 밖 `/private/tmp/iyum-password-success-20261025-start`에 기록했다.

## 변경

입력 전 정책 안내가 완료 상태에서도 성공 문구보다 위에 남아 있었다. `PasswordChangeDialog.tsx`에서 완료 문구를 모달 본문 맨 위로 옮기고, 로그인 유지 안내와 다른 로그인 세션 안내를 뒤에 배치했다. 완료 전 입력 화면 안내는 그대로다.

`mypage.css`의 완료 상태 전용 스타일로 “비밀번호가 변경되었습니다.”를 24px·700·기존 청록색으로 강조한다. “현재 로그인은 유지됩니다.”는 다음 줄의 16px 보조 문구, 다른 세션 안내는 14px 보조 문구로 표시한다. 모바일에서도 단어 단위 줄바꿈과 기존 하단 시트 구조를 유지한다. 공통 버튼/모달·인증·API·CSRF·백엔드는 변경하지 않았다.

기존 `password-change-browser.mjs`의 성공 검사 안에서 안내의 DOM 순서·실제 세로 위치·글자 크기 우선순위·넘침을 확인하고 1440/390/320px 완료 화면을 캡처했다. 새 테스트 파일이나 기능을 추가하지 않았다.

## 이번에 실행한 검사

- `pnpm lint`: 통과.
- `pnpm typecheck`: 통과.
- `pnpm test`: 325/325 통과, 실패·skip 0.
- `pnpm build`: 통과.
- `tests/password-change-browser.mjs`: 32/32 통과.
- `tests/password-change-race-browser.mjs`: 37/37 통과.
- `git diff --check`: 통과.

브라우저는 별도 8459 프로덕션 서버와 Playwright Chrome, 모의 API를 사용했다. 실제 계정 비밀번호·DB 데이터는 변경하지 않았다. 원래 8443/8082 서버는 보존하고 직접 실행한 8459만 종료했다. 실기기 Safari·소프트 키보드는 이번에 검증하지 않았다.

`password-change-completion-2026-10-25/`에 이번 검사 출력·JSON·완료 화면 3개를 보존했다. 이전 검증 기록은 덮어쓰지 않았다. 커밋·push·병합·배포 없음.
