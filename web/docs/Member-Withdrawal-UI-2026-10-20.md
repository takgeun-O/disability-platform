# 회원 탈퇴 UI/API 연동 검토 — 2026-10-20

## 기준과 범위
- 브랜치: feat/member-withdrawal, HEAD: 3a0c7c9504a98e25abd8624d5feb067f4e9bca3b
- 수집: 2026-10-20T22:50:29.880876+09:00 (Asia/Seoul)
- 시작 시 미추적 자료는 docs/design/IYUM_member_withraw.png 한 개였다. 이전 백엔드 작업은 이미 커밋된 상태였다. 디자인 파일과 기존 구현을 보존했다.
- 이번 변경은 프런트 소스/테스트/문서만이다. 백엔드, API 정책, DB, 의존성, 배포 설정 변경 없음. 커밋/푸시/병합 없음.

## 구현과 검토 시작점
1. src/components/member/WithdrawalDialog.tsx: 계정 관리 하단 진입, PC 510px 모달/모바일 하단 시트. 기존 OverlayDialog의 native dialog, 배경 잠금, 초점 복귀, visualViewport/safe-area 동작 사용. 최초 초점은 닫기 버튼으로 모바일 키보드를 자동으로 열지 않는다. 처리 중 닫기/취소/중복 제출 차단.
2. src/lib/member-api.ts: 기존 request의 credentials/include, no-store, API 환경변수, 발급된 CSRF headerName을 재사용. POST /api/v1/members/me/withdrawal에 currentPassword 원문과 confirmed:true만 전송. 204에서 JSON을 읽지 않는다. 자동 POST 재시도 없음.
3. src/lib/login-form.ts: 기존 로그인용 현재 비밀번호 검사를 재사용 가능하게 분리. 공백/빈 값과 UTF-8 72바이트를 검사하며 전송값은 trim하지 않는다. 가입용 최소 길이/조합 규칙을 추가하지 않는다.
4. 비밀번호/동의 필드 오류는 해당 입력 아래 한 번만 표시하고 일반 오류와 분리한다. PASSWORD_MISMATCH, INVALID_PASSWORD, VALIDATION_FAILED, 401, CSRF_TOKEN_INVALID, MEMBER_NOT_ACTIVE를 구분한다. 401은 기존 인증 만료/로그인 복귀 UI로 이어진다. CSRF 오류 후 다음 명시적 제출에서 새 토큰을 조회하며 POST를 자동 재전송하지 않는다. 네트워크/5xx/비정상 응답은 처리 불확실성을 안내하고 로그인 상태 재확인 또는 수동 재제출을 제공한다.
5. AuthProvider.completeWithdrawal: 확인된 204 후 현재 회원만 expire하여 진행 중 인증 조회를 무효화하고 개인 활동 cache owner를 제거한다. 프로필/내 게시글/내 댓글은 인증 화면 해제로 제거되고 이전 lease는 무효다. 다른 계정이나 이미 해제된 상태는 완료로 바꾸지 않는다.
6. 기존 AuthControls의 FeedbackNotice를 사용해 홈 이동 후 완료 안내를 표시한다. 별도 알림 UI를 중복 구현하지 않았다. 비밀번호/동의는 닫기 및 성공에서 비우며 unmount로 폐기한다. 저장소/URL/로그에 넣지 않는다.
7. community-mutations.publicIdentityChanged와 Comments: 탈퇴 후 공개 목록/상세/댓글의 작성자 표시를 재조회한다. 일반 댓글 수정/목록 갱신 정책은 유지한다. 기존 서버 초기 HTML·무JS 처리 변경 없음.

## 실제 백엔드 계약 확인
MemberWithdrawalController/MemberWithdrawalRequest/MemberWithdrawalService/GlobalExceptionHandler를 읽었다. POST, 현재 세션 회원 식별, currentPassword/confirmed 검증, UTF-8 72바이트, 성공 후 서버 세션 및 JSESSIONID 만료, 204/no-store 계약이 요청 설명과 일치한다. 회원 행과 게시글·댓글은 유지하며 개인정보를 제거한다. 운영 코드는 수정하지 않았다.

## 이번 실행 결과
- Node 24 / pnpm 10.34.3. 초기 기본 Node23 타입 검사 경고 후 최종 검사는 Node24로 실행.
- pnpm lint: 통과.
- pnpm typecheck: 통과.
- pnpm test: 290/290 통과.
- pnpm build: 통과 (Next 16.3.6, 기존 patch 유지).
- git diff --check: 통과.
- 브라우저: production Next 8457 + Playwright Chrome의 모의 API. Spring/DB 데이터 변경 없음.
- member: 20 통과 / 0 실패.
- menu: 22 통과 / 0 실패.
- posts: 41 통과 / 0 실패.
- comments-final: 30 통과 / 0 실패.
- withdrawal: 10 통과 / 0 실패.

### 재현 명령
```sh
cd web
pnpm lint
pnpm typecheck
pnpm test
pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8457
# 별도 터미널: 설치된 Playwright 경로를 지정한다. 새 패키지 설치 없음.
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs node tests/withdrawal-browser.mjs
# 아래 테스트는 순차 실행 (posts/comments는 모의 API 포트8858 사용)
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs node tests/member-browser.mjs
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs node tests/member-menu-browser.mjs
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs node tests/member-posts-browser.mjs
BASE_URL=http://127.0.0.1:8457 PLAYWRIGHT_MODULE=<installed-playwright>/index.mjs node tests/member-comments-browser.mjs
```

## 중간 실패와 최종 검증 구분
- 첫 탈퇴 검사: aria-disabled 닫기 버튼을 Playwright click으로 누르려다 대기했다. 키보드로 활성화를 시도해 닫히지 않는 것을 검증하도록 수정했다.
- 다음 검사: 모의 /auth/me 401에 실제 계약의 message/fieldErrors 누락으로 로그인 재진입 검사가 실패했다. fixture 계약을 보완했다. UI 조건을 약화하지 않았다.
- 내 댓글 최초 실행 30건 중 6건 실패: 검증 서버가 실행되는 중 CSS 토큰 변경 빌드가 있었고 실패 캡처에서 CSS가 빠진 화면을 확인했다. 최종 빌드 후 서버 재시작, 같은 왕복 2건 재확인 및 전체 내 댓글 재실행으로 구분한다. 기존 검사 기대값/서비스 복원 코드는 변경하지 않았다.

## 미검증/제한
- 실제 Spring에 대한 회원 탈퇴는 실행하지 않았다. 일반 사용자/개발 계정은 탈퇴시키지 않았고 테스트 DB/시드 생성도 없음. 서버 실제 쿠키 만료와 운영 CORS는 백엔드 계약 확인 및 모의 브라우저 검증 범위이며 실연동 통과로 주장하지 않는다.
- iPhone Safari 실기기/실제 소프트 키보드 미검증. 1440/390/320 및 낮은 viewport 높이, 키보드 Tab/Escape, focus 복귀는 데스크톱 Chrome 브라우저 에뮬레이션 검증이다.
- 모의 자료의 회원명/이메일/개수는 검증용이며 서비스 데이터에 추가하지 않았다.
- 기존 개발 서버 8443은 보존. 직접 시작한 8457 검증 프로세스만 작업 후 종료한다.

## 화면
- withdrawal-ui-2026-10-20/dialog-1440.png
- withdrawal-ui-2026-10-20/dialog-390.png
- withdrawal-ui-2026-10-20/dialog-320.png
- withdrawal-ui-2026-10-20/completed-1440.png / completed-390.png
- short-*.png: 낮은 높이에서 시트 내부 스크롤 상태

## 이번 변경 파일
- `web/src/components/AuthControls.tsx`
- `web/src/components/AuthProvider.tsx`
- `web/src/components/OverlayDialog.tsx`
- `web/src/components/community/Comments.tsx`
- `web/src/components/member/WithdrawalDialog.tsx`
- `web/src/features/MyPage.tsx`
- `web/src/lib/auth-session.ts`
- `web/src/lib/community-mutations.ts`
- `web/src/lib/login-form.ts`
- `web/src/lib/member-api.ts`
- `web/src/styles/mypage.css`
- `web/tests/withdrawal-browser.mjs`
- `web/tests/withdrawal.test.mjs`

## 패키징
현재 작업 트리의 web/src·tests·public·설정/lockfile/Next patch, 관련 backend src 및 Gradle 파일, AGENTS, 본 기록과 캡처를 원래 상대 경로로 포함한다. source-start.diff는 이번 시작본 대비, HEAD.diff는 추적 파일의 HEAD 대비다. 미추적 신규 구현 파일은 실제 파일과 start diff에 포함한다. 기존 사용자 자료인 디자인 PNG는 참고 자료로 포함한다.
실제 .env, 계정 자격증명/쿠키/토큰, DB 덤프, 원시 요청 로그, node_modules/.next/build/.gradle/.git 및 기존 ZIP은 제외한다. 명시적 테스트 fixture의 dummy 비밀번호/토큰/example 이메일은 실제 비밀값이 아니며 재현을 위해 테스트 코드에 유지한다. 원본 파일의 민감정보 마스킹/수정은 하지 않는다.
