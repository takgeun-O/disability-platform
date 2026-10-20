# 회원 탈퇴 UI 후속 수정 — 2026-10-20T23:15:37.173460+09:00

브랜치 feat/member-withdrawal, HEAD 3a0c7c9504a98e25abd8624d5feb067f4e9bca3b.
시작 시 이미 회원 탈퇴 API/UI 연동이 미커밋 상태였다. start-status.txt로 기존 작업을 구분하며 이번 변경은 source-start.diff에 기록한다. 이전 기록은 덮어쓰지 않았다.

## 변경과 검토 시작점
- OverlayDialog: closeOnBackdropClick 기본 true. 탈퇴 모달만 false. 기존 Escape/초점/스크롤 잠금은 유지.
- WithdrawalDialog: 위 옵션 및 PasswordInput toggleAppearance=icon만 적용. 요청/검증/성공/실패 로직 유지.
- AuthFields: toggleAppearance 기본 text. 탈퇴에서만 currentColor 눈 SVG, 접근성 이름과 type=button·disabled 유지. 외부 아이콘 패키지 없음.
- MyPage/mypage.css: 계정 관리 이메일 아래 구분선·전체 행 버튼·화살표. 기존 위치와 목록 유지. PC 폭510px 유지, 동의/버튼 간격 축소, 모바일 아이콘44px/상단간격 축소. keep-all + overflow-wrap:anywhere로 안내/동의의 단어 줄바꿈. 글자 크기 축소/강제 br/넘침 숨김 없음.

## 이번 직접 실행 결과
- pnpm lint / pnpm typecheck / pnpm test (290/290) / pnpm build: 통과.
- withdrawal-browser.mjs: 11/11 통과. 1440/390/320, 바깥 클릭/터치 상태 보존, X/취소/Escape 초기화·초점 복귀, 요청 중 닫기·중복 제출 방지, 눈 토글 원문 보존/제출 없음, 닉네임 모달 기본 바깥 클릭 닫힘, 204/401/403/500/네트워크/필드 오류.
- member-menu-browser.mjs: 22/22 통과. 기존 메뉴/닉네임/이탈 보호/화면 폭 전환 회귀.
- git diff --check 통과.
- 실제 계정·DB 변경 없음. 사용자가 실제 탈퇴 및 재가입을 확인했다는 보고는 이번 자동화 검증과 별개다.
- Chrome + production Next 8457 + 모의 API. 기존8443 서버 보존, 직접 시작한8457은 종료.

## 재현
Node24, pnpm10.34.3. web에서 pnpm lint, pnpm typecheck, pnpm test, pnpm build.
별도 포트: pnpm exec next start --hostname 127.0.0.1 --port 8457.
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs node tests/withdrawal-browser.mjs
동일 환경으로 node tests/member-menu-browser.mjs. WITHDRAWAL_ARTIFACT_DIR/MEMBER_ARTIFACT_DIR로 결과 위치 지정 가능.

## 미검증/제한
실기기 iPhone Safari와 실제 소프트 키보드 미검증. 낮은 viewport 420px·터치 입력은 Chrome 에뮬레이션이며 실제 키보드 검증을 대신하지 않는다. 실제 Spring 탈퇴는 실행하지 않았다. 커뮤니티의 다른 모달 전체 브라우저 검사는 반복하지 않았다(옵션 기본값 보존, 닉네임·모바일 메뉴 회귀 확인).

## 캡처
withdrawal-polish-2026-10-20/page-1440.png, page-390.png, page-320.png: 닫힌 마이페이지와 진입 행.
dialog-1440.png, dialog-390.png, dialog-320.png: PC/하단시트.
password-error.png: 비밀번호 오류. short-*.png: 낮은 높이에서 입력/하단버튼으로 내부 스크롤.

## 이번 변경 파일
- web/src/components/OverlayDialog.tsx
- web/src/components/auth/AuthFields.tsx
- web/src/components/member/WithdrawalDialog.tsx
- web/src/features/MyPage.tsx
- web/src/styles/mypage.css
- web/tests/withdrawal-browser.mjs

## 패키징
현재 작업 트리의 전체 web/src/tests/public/config 및 관련 backend Java 소스/테스트를 포함. 이전 미커밋 구현도 포함하며 HEAD.diff와 이번 source-start.diff를 구분한다. .env·실제 계정 파일·비밀번호·쿠키·토큰·DB덤프·.git·node_modules·.next·build·기존ZIP 제외. 테스트 전용 dummy 암호/토큰/example.test만 재현용으로 유지한다. 실제 비밀값 발견/마스킹 없음. 원본 소스는 마스킹하지 않았다.
