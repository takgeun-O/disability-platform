# 마이페이지 변경 완료 알림 — 2026-10-25

## 범위와 시작 상태

- 브랜치 `feat/member-password-change`, HEAD `bcdeaa31d7c090ab0ad8a5acc1fed86f186addbb`.
- 기존 미커밋 비밀번호 변경 구현·인증 응답 경합 방어·완료 모달 문구 계층을 보존했다.
- 이번 변경만의 차이: [this-followup.patch](member-success-toast-2026-10-25/this-followup.patch).
- 백엔드·DB·의존성·API·CSRF 계약 변경 없음. 커밋·푸시·배포 없음.

## 동작과 이유

프로필 아래에 계속 남던 성공 문구를 화면 상단 중앙의 흰색/청록색 토스트로 변경했다. 본문 배치와 스크롤은 움직이지 않는다.

- 닉네임: 저장 성공 직후 `닉네임을 변경했습니다.` 표시.
- 비밀번호: 기존 완료 모달의 큰 완료 제목과 로그인 유지 설명은 그대로 둔다. 완료 모달을 닫은 뒤 `비밀번호가 변경되었습니다.` 토스트를 표시한다. 모달 뒤에서 알림 시간이 소진되지 않는다.
- 약 5초 후 180ms 동안 부드럽게 사라지며, 44px 닫기 버튼으로 즉시 닫기 시작 가능.
- 마우스 올림, 키보드 초점, 비활성 브라우저 탭에서는 남은 시간을 일시정지한다. 모션 감소 설정에서는 이동 애니메이션을 없앤다.
- `role=status`로 안내하되 초점을 강제로 옮기거나 배경을 잠그지 않는다. 닫기 버튼 사용 시 원래 편집 진입 버튼으로 스크롤 없이 초점을 복원한다.
- 알림은 한 번에 하나, 다음 성공은 새 타이머로 시작한다. 화면 이탈·로그아웃·계정 화면 해제 시 타이머와 이벤트를 정리한다.
- 재로그인 필요, 현재 세션 만료, 필드 오류 등 조치가 필요한 안내는 기존 위치에 유지하며 자동으로 없애지 않는다.

## 변경 파일

- `src/components/member/SuccessToast.tsx`: 표시·닫기·타이머·일시정지 및 정리.
- `src/features/MyPage.tsx`: 닉네임/비밀번호 성공 알림 연결, 영구 성공 문구 제거.
- `src/components/member/PasswordChangeDialog.tsx`: 닫을 때 완료 여부 전달. 변경 요청/인증/CSRF 처리 유지.
- `src/styles/mypage.css`: 마이페이지에 한정한 토스트 스타일과 반응형/모션 감소 처리.
- `tests/member-success-toast-browser.mjs`: 관련 동작 15개 모의 브라우저 검사.
- `tests/password-change-browser.mjs`: 후속 CSRF 조회 실패 후 닫기에서 새로운 토스트를 기대하도록 한 검증 변경.

## 이번에 직접 실행한 검사

Node 24, pnpm 10.34.3. `web`에서 실행. 설치된 Next 16.3.6의 Server/Client Components 문서를 확인했다.

| 명령 | 결과 |
| --- | --- |
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | 325/325 통과, skip 0 |
| `pnpm build` | 통과 |
| `node tests/member-success-toast-browser.mjs` | 15/15 통과 |
| `node tests/password-change-browser.mjs` | 32/32 통과 |
| `node tests/password-change-race-browser.mjs` | 37/37 통과 |
| `node tests/member-browser.mjs` | 20/20 통과 |
| `node tests/member-menu-browser.mjs` | 22/22 통과 |
| `git diff --check` | 통과 |

브라우저 검사는 별도 8459 포트의 실제 프로덕션 Next 서버와 Chrome headless에서 API 응답만 모의 처리했다. 공통 `BASE_URL=http://127.0.0.1:8459`, `PLAYWRIGHT_MODULE`은 환경의 설치된 Playwright 모듈을 지정했다. 새 검사의 출력은 `MEMBER_TOAST_DIR`로 지정한다. 총 126개 브라우저 검사 통과. 이번 검사 결과 JSON·명령 출력은 [결과 폴더](member-success-toast-2026-10-25/)에 있다. 과거 결과를 이번 결과로 재사용하지 않았다.

알림 5초 타이머는 Playwright clock으로 확인했다. 최초 캡처에서는 가상 JS 시간을 진행시켜도 CSS 애니메이션의 실제 프레임이 끝나기 전 촬영되어 흐리게 나타났다. 최종 캡처는 실제 애니메이션 시계를 재개하고 opacity=1 및 애니메이션 종료를 확인한 뒤 촬영했다. 서비스 스타일로 숨기거나 이미지를 편집하지 않았다. 백그라운드 탭 동작은 `document.hidden`과 visibilitychange의 모의 전환으로 검사했다.

## 화면 캡처

| 크기 | 닉네임 변경 | 비밀번호 완료 모달 닫은 후 |
| --- | --- | --- |
| PC 1440px | [보기](member-success-toast-2026-10-25/nickname-1440.png) | [보기](member-success-toast-2026-10-25/password-1440.png) |
| 모바일 390px | [보기](member-success-toast-2026-10-25/nickname-390.png) | [보기](member-success-toast-2026-10-25/password-390.png) |
| 모바일 320px | [보기](member-success-toast-2026-10-25/nickname-320.png) | [보기](member-success-toast-2026-10-25/password-320.png) |

모바일 캡처는 브라우저 뷰포트 에뮬레이션이다. 문구/닫기 버튼의 넘침, 프로필·활동 영역 배치 유지, 초점·스크롤 유지, 모션 감소를 검사했다.

## 미실행과 환경 보존

이번 UI 보완에서 실제 Spring API의 비밀번호/닉네임 변경은 실행하지 않았다. 실제 계정·개발 DB를 변경하지 않았다. 실물 iPhone Safari, 실제 소프트 키보드, 화면 읽기 프로그램의 실제 음성 출력은 미검증이다.

기존 8443 프런트 개발 서버와 8082 백엔드 및 별도 preview worktree는 보존한다. 직접 시작한 8459 검증 서버만 종료한다. 빌드가 갱신한 next-env.d.ts는 작업 시작본으로 복원했다.
