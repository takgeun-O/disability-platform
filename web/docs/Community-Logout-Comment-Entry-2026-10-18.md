# 로그아웃 화면 유지·댓글 로그인 진입 — 2026-10-18

## 기준과 원인

`feat/community-post-cursor`, HEAD `2adf11bff2e58394899d33157aa0a556df3306fa`. 기존 피드·404·시드 미커밋 변경과 미추적 파일을 보존했다. 이번 변경은 프런트 컴포넌트2개/브라우저 검사2개와 이 기록이다. 백엔드·API·DB·기존 디자인·별도 preview worktree를 변경하지 않았다.

- `AuthControls.handleLogout()`이 성공 뒤 `guard.request(() => router.replace('/'))`를 실행해 현재 화면에서 홈으로 이동했다. 요청 전에도 guard를 거쳐 dirty를 지우고 탐색 승인 버전을 변경했다.
- `Comments`의 등록 버튼은 비로그인 상태에서도 `disabled={!permitted || busy}`였고, `.iyum-button:disabled`의 `cursor: wait`가 적용됐다. 로그인 안내는 별도로 있었지만 버튼 자체는 클릭/키보드 조작이 불가능해 처리 중처럼 보였다.

## 변경 방식

### 로그아웃은 인증 상태만 변경

`web/src/components/AuthControls.tsx`의 handleLogout에서 경로 변경과 NavigationGuard 조작을 제거했다. `auth.signOut()`의 세션·CSRF·중복 요청 제어 및 `/me`로 불확실한 결과를 확인하는 기존 흐름은 그대로 사용한다. 성공/오류 알림, 성공 안내5초 자동 닫기·초점 중 일시정지·수동 닫기를 유지한다.

로그아웃 자체는 페이지 이탈이 아니므로 이탈 확인을 띄우거나 dirty를 초기화하지 않는다. 현재 URL·폼·스크롤과 요청 수명을 그대로 유지한다. 실제 링크/뒤로가기로 이동할 때는 기존 AppLink/NavigationGuard의 입력 보호가 계속 적용된다. 이는 과거 ‘로그아웃 전 이탈 확인’ 요구를 이번 ‘로그아웃만 하고 현재 화면 유지’ 요청에 맞게 변경한 것이다. 인증 성공을 모의 처리하거나 서버에 보낸 요청을 취소한 것으로 표시하지 않는다.

### 댓글 버튼은 로그인으로 연결

`web/src/components/community/Comments.tsx`에서 인증 상태에 따라 마지막 조작을 구분했다.

- 비로그인: 기존 AppLink와 primary 스타일로 **로그인하고 댓글 등록**. 정상 pointer와 키보드 Enter, 안전한 상세 복귀주소를 유지한다. 등록 API를 호출하지 않는다.
- 확인/인증 처리 중: 실제 상태에 맞는 진행 문구와 비활성 버튼.
- 인증 조회 오류: 입력을 보존하고 **로그인 상태 다시 확인** 버튼으로 기존 auth.refresh를 호출한다.
- 로그인 완료: 기존 댓글 등록/중복 방지/필드 오류 처리 유지.

`comment-auth-help`를 입력/로그인 링크의 aria-describedby에 연결하고 상태 안내를 제공한다. 초안이 있는 상태에서 로그인 링크를 누르면 기존 이탈 확인이 열린다. 취소하면 초안 유지, 나가기를 선택하면 로그인으로 이동한다. **로그인 왕복 후 초안 영구 복원은 새로 추가하지 않았다.** 화면을 떠나면 입력이 보관되지 않는다는 안내를 표시한다.

## 이번에 실행한 검증

Node24.19.0/pnpm10.34.3/Next16.3.6. 기존8443 개발 서버와8082 Spring을 재시작하지 않고 사용했다. `.next` 충돌을 피하려고 `/private/tmp/iyum-logout-comments-20261018/web`에 현재 소스/설정/의존성을 복사해 빌드와 타입 검사를 실행했다. 실제 .env는 복사하지 않았다.

| 검사 | 결과 |
|---|---|
| pnpm lint | 통과, 경고 없음 |
| pnpm test | 252 통과, 실패/skip0 |
| pnpm build | 기본 Turbopack 프로덕션 빌드 통과 |
| pnpm typecheck | 통과 |
| git diff --check | 통과 |
| login-browser.mjs | 모의38 통과 |
| community-comments-browser.mjs | 모의42 통과 |
| 실제 Spring UI 확인 | 4 통과, 커뮤니티 변경 요청0 |

```sh
# web (지원 Node24 PATH 사용)
pnpm lint
pnpm test
# 격리된 현재 소스 복사본
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm build
pnpm typecheck
# PLAYWRIGHT_MODULE은 기존 외부 설치 경로, 새 의존성 없음
BASE_URL=http://127.0.0.1:8443 LOGIN_ARTIFACT_DIR=<출력폴더> node tests/login-browser.mjs
BASE_URL=http://127.0.0.1:8451 COMMUNITY_ARTIFACT_DIR=<출력폴더> node tests/community-comments-browser.mjs
```

댓글 검사는8856 모의 공개API와8451 임시 프로덕션 서버를 사용했다. 실제 Spring 통합 검증과 구분한다. 목록·댓글 무JS 성공/빈목록/실패, 더보기, 댓글CRUD·CSRF/권한/204, 이탈·늦은 응답·초안 보호가 통과했다. 새 검사는 로그아웃 성공/응답 유실 뒤 me확인/실패 각각에서 URL·본문·작성/편집 초안·초점·스크롤 유지, 이후 실제 이탈 확인을 검증한다. 비로그인 버튼의 마우스·키보드/320·390·1440 배치, 로그인 확인 지연과 장애/재시도, 댓글POST·CSRF 미발생을 검증했다.

로그인 검사의 과거 ‘로그아웃→이탈 승인→홈’을 현재 ‘로그아웃→현재 화면 유지→실제 이동 때만 확인’ 조건으로 교체했다. 중복 요청·오류·지연·알림 타이머 검사를 삭제하거나 skip하지 않았다. opt-in 실제 가입 E2E 내부의 홈 기대도 현재 URL 유지로 맞췄지만 **이번에 해당 전체 가입 E2E를 실행하지는 않았다**.

### 실제 API 확인

기존 로컬 시드 계정1과 게시글14를 사용했다. 비밀번호는 저장소 밖 비공개 파일에서 읽어 새 독립 브라우저 컨텍스트에서만 사용했으며 출력·캡처하지 않았다. `/private/tmp/iyum-logout-comments-20261018/actual.mjs`로 수행했다.

1. 직접 로그인200 → 공개 상세 표시.
2. 전송하지 않은 댓글 초안 입력 → 실제 로그아웃204 → 같은 상세/본문/초안 유지, me401.
3. 비로그인 댓글 버튼 클릭 → 이탈 취소 후 초안 유지,1440/390/320 가로 넘침 없음. 초안을 비운 뒤 키보드 Enter로 상세 복귀주소를 포함한 로그인 화면 진입.
4. 상세 직접 진입/새로고침 후 비로그인 유지. 실제 게시글/댓글 쓰기 요청0, 새 데이터0.

실제 가입/메일 인증·실물 스마트폰·백엔드 전체 검사는 실행하지 않았다. 게시글/주제404 별도 스크립트도 재실행하지 않았다. 이전404 수정 전 실패와 수정 후 결과는 종전 기록에 그대로 남아 있으며 이번 작업의 신규 해결 결과로 합산하지 않는다.

## 결과와 직접 확인

원본 결과: [집계](community-logout-comment-entry-2026-10-18/SUMMARY.json), 같은 폴더 login/comments는 모의, actual은 실제API 결과.

- [1440px 실제 댓글 입력 영역](community-logout-comment-entry-2026-10-18/actual/anonymous-comments-1440.png)
- [390px](community-logout-comment-entry-2026-10-18/actual/anonymous-comments-390.png)
- [320px](community-logout-comment-entry-2026-10-18/actual/anonymous-comments-320.png)
- [로그아웃 후 작성·수정 초안 보존(모의)](community-logout-comment-entry-2026-10-18/comments/logout-drafts-preserved.png)

8443에서 로그인한 채 게시글을 열고 로그아웃하면 현재 상세가 유지된다. 댓글 영역의 ‘로그인하고 댓글 등록’을 눌러 로그인 안내를 확인한다. 초안을 입력했다면 이탈 확인의 ‘계속 작성’으로 내용이 남는지도 확인한다.

기존8443/8082 유지, 직접 시작한8451 종료,8856 모의 서버는 테스트 종료 시 정리했다. Git 변경은 미커밋으로 남겼으며 커밋·push·PR·병합·배포 없음.
