# 비밀번호 변경과 개인 조회 응답 경합 보완 — 2026-10-24

## 기준과 기존 변경 구분

- 브랜치 `feat/member-password-change`, HEAD 및 백엔드 기준 `bcdeaa31d7c090ab0ad8a5acc1fed86f186addbb`.
- 시작 상태 수집: **2026-10-24 02:50:29 KST**. 기존 비밀번호 변경 UI/API/테스트, 입력 오류 후 글자마다 초점이 이동하던 문제의 수정, 기존 검증 자료가 이미 미커밋·미추적 상태였다.
- 시작 스냅샷 `/private/tmp/iyum-password-change-race-start`를 기준으로 이번 차이를 별도 산출했다. 기존 소스/테스트는 초기화하지 않았다.
- 이번에는 프론트 인증 수명/개인 조회 처리 및 관련 테스트만 보완했다. 백엔드 소스는 시작 스냅샷과 바이트 비교해 변경 0건. 정책·DB·설정·의존성 변경 및 커밋·push·병합·배포 없음.
- 적용 지침: 루트 및 web AGENTS. 설치된 Next 16.3.6 문서 `node_modules/next/dist/docs/01-app/01-getting-started/05-server-and-client-components.md` 확인. 기존 Provider의 클라이언트 메모리와 외부 저장소 구독 방식을 유지했다.

## 원인과 수정 전 재현

기존 `captureMemberRequest()`는 비밀번호 변경 POST의 로그인 세대를 보호했지만, 프로필·활동 요약·내 게시글·내 댓글의 401 처리는 `expire(memberId)`에 의존했다. 비밀번호 변경은 동일 회원을 유지하므로 과거 GET의 401과 현재 로그인 만료를 구분하지 못했다. 오래된 성공 GET도 개인 정보·개수·목록을 덮을 수 있었다.

백엔드 `MemberSessionStatusFilter.clearInvalidAuthentication()`은 조회 중 교체된 세션 ID/보안 컨텍스트를 보존하고 `finally`에서 현재 요청만 익명화할 수 있다. 따라서 해당 요청의 401을 현재 세션의 무효로 단정할 수 없다. 백엔드 구현을 읽어 확인했으며 이번에 코드를 변경하거나 실제 서버에서 그 타이밍을 강제하지 않았다.

프로덕션 Next(8459) + 응답 해제 장치가 있는 브라우저 모의 API로 **수정 전 동일 시나리오 37개 중 16 통과·21 실패**를 기록했다. 사용자 보고 경로에서 실제 순서는 다음과 같다.

1. 프로필 GET 시작, 응답 보류
2. 비밀번호 변경 전 CSRF GET → 변경 POST 시작
3. 변경 POST200/false → 후속 CSRF GET 완료
4. 보류한 프로필 GET401 도착
5. 기존 코드에서 마이페이지 로그인 안내로 전환되고 모달이 제거됨

`before-production/response-order.json`의 첫 사례에 이 순서가 남아 있다. 반대 순서에서는 POST 응답 전 이전 GET401만으로 모달이 사라졌다. 실패 직후 테스트 정리용으로 해제한 POST503 기록은 실제 성공 결과로 해석하면 안 된다. 초기 개발 서버 진단은 Strict Mode 중복 effect 영향이 있어 최종 전후 비교에 사용하지 않았다.

## 해결 방식

### 로그인 세대와 개인 조회 세대를 함께 검사

`auth-session.ts`의 `captureMemberRequest(memberId)`가 로그인 generation과 개인 조회 revision을 모두 캡처한다. 응답의 데이터 적용·닉네임 변경·인증 만료는 해당 요청이 아직 현재인지 검사한다. 회원 ID가 같은 재로그인도 generation으로 구분한다. AbortController는 네트워크 정리용이며 단독 방어가 아니다.

`beginCredentialChange(memberId)`는 실제 비밀번호 변경 절차를 시작하기 직전에 이전 개인 조회를 무효화하고 새 개인 GET을 잠시 보류한다. 인증 상태를 authenticated로 유지하므로 현재 모달·폼·개인 목록을 제거하지 않는다. 이것은 성공 판정이 아니며, 성공 안내는 검증된 POST200 응답을 받은 경우에만 표시한다.

변경 작업용 핸들은 개인 GET revision과 별도로 로그인 generation에 묶인다. 덕분에 자기 자신의 조회 무효화가 후속 CSRF 처리 및 finally를 무효화하지 않는다. `finish()`는 모든 결과 및 승인된 이탈 후에도 자신이 소유한 보류만 해제하고 현재 조회를 재개한다. 다른 로그인/로그아웃/만료 뒤의 오래된 finish는 새 인증에 영향을 주지 않는다.

- POST200/false: 입력을 즉시 지우고 완료 안내 유지 → 새 CSRF GET → finally에서 현재 데이터 조회 재개.
- 후속 CSRF GET 실패: 이미 확인한 변경 성공을 유지하고 보안 정보 조회 실패를 따로 알림. 저장 상태 종료.
- POST200/true 또는 현재 POST401: 세대가 일치할 때만 재로그인 안내 및 인증 만료.
- 이전 GET401이 POST 응답보다 먼저 도착: 이미 무효화된 조회이므로 무시하고 POST 결과를 기다림. POST400/503에서는 성공을 표시하지 않고 입력/오류를 보존한 채 새 조회 재개.
- **변경 뒤 시작한 새 GET401은 정상적으로 비로그인 처리**한다. 모든 401을 무시하거나 `/auth/me` 확인으로 변경 성공을 추정하지 않는다. 추가 인증 재확인 루프·POST 자동 재전송·자동 로그아웃 POST 없음.

### 개인 화면과 캐시

프로필/요약의 effect는 paused 상태에서 요청하지 않고 revision 변경 후 새 요청을 보낸다. 성공/실패 양쪽 모두 요청 핸들을 검사한다. 닉네임 편집의 늦은 완료도 같은 방식으로 검사한다.

내 게시글/내 댓글은 기존 소유자 lease와 navigation scope에 인증 요청 검사를 추가했다. 스냅샷에 `requestRevision`을 남겨 인증이 바뀌기 전의 목록은 동일 계정 복귀 시 새로 확인한다. 이미 읽은 페이지 수와 서버 커서로 재조회하고, 기존 누적 목록·선택 탭을 유지한다. 비활성 탭은 선택될 때 갱신한다. 로딩 정리는 새 요청과 이전 요청의 controller를 구분한다. 초점 예약도 이전 revision에서 실행되지 않는다.

캐시는 여전히 AuthProvider별 메모리이며 저장소에 개인 정보를 영구 보관하지 않는다. 로그인/로그아웃/계정 전환 시 기존 소유권 폐기 정책을 유지한다. 비밀번호 변경만으로 현재 회원 캐시를 초기화하지 않는다. 기존 명시적 동작용 `expire(memberId)` API를 삭제하지 않았지만, 이번 네 개인 조회 경로는 해당 ID 전용 만료 호출을 사용하지 않는다.

## 이번 변경 파일

| 파일 | 변경 |
| --- | --- |
| `src/lib/auth-session.ts` | 세대에 묶인 개인 요청 핸들, 비밀번호 변경 중 조회 보류/해제 |
| `src/components/AuthProvider.tsx` | 개인 조회 snapshot 구독 및 변경 핸들 제공 |
| `src/components/member/PasswordChangeDialog.tsx` | 변경 핸들 시작과 finally 해제; 기존 CSRF/이탈/입력 초점 수정 유지 |
| `src/features/MyPage.tsx` | 프로필·요약·닉네임 응답의 세대 검사 및 새 조회 재개 |
| `src/components/member/MyActivity.tsx` | 내 게시글 응답 검사/이전 요청 정리/재조회·초점 수명 |
| `src/components/member/MyComments.tsx` | 내 댓글의 동일 처리 |
| `src/lib/member-activity.ts` | 메모리 스냅샷의 선택적 requestRevision |
| `tests/auth-request-race.test.mjs` | AbortController 없이 오래된 응답·같은 회원 재로그인·캐시 수명 단위 검사 7개 |
| `tests/password-change-race-browser.mjs` | 응답 순서 제어 모의 브라우저 검사 37개 |

기존 `password-change-api.ts`, `password-change.ts`, `styles/mypage.css`와 이전 비밀번호 변경 검사 파일은 이번 보완에서 변경하지 않았다. `next-env.d.ts`는 검사 도구가 생성한 참조 변경만 시작 상태로 복원했다.

## 최종 실행 결과

모두 이번에 직접 실행했다. 브라우저 검사는 **프로덕션 Next + 모의 API**이다.

| 검사 | 결과 |
| --- | --- |
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | 325/325 통과, 실패·skip 0 |
| `pnpm build` | 통과 (Next 16.3.6) |
| `git diff --check` | 통과 |
| 새 경합 브라우저 | 수정 전 16/37 → 수정 후 **37/37** |
| 기존 비밀번호 변경 브라우저 | 32/32 |
| 인증 흐름 브라우저 | 26/26 |
| 회원 정보·닉네임 브라우저 | 20/20 |
| 공통 메뉴 브라우저 | 22/22 |
| 내 게시글 브라우저 | 41/41 |
| 내 댓글 브라우저 | 30/30 |

수정 후 브라우저 합계 **208/208**. 새 검사에는 프로필·요약·내 게시글·내 댓글 각각의 이전401(POST 전/후), 이전성공, 현재401, 같은회원/다른회원 재로그인 후 이전401/성공, CSRF 후속실패, POSTtrue/401/503/400을 포함했다. 이전 요청 보류를 `deferred`로 해제하고 실제 응답 순서를 기록한다. 고정 sleep으로 순서를 추측하지 않는다.

브라우저 fixture는 취소된 GET도 응답이 전달되도록 mock fetch의 signal을 분리한다. UI의 abort/lifecycle 검사 자체는 그대로다. 별도 단위 검사는 AbortController 없이 요청 핸들만으로 이전 expire/updateNickname이 거부됨을 확인한다.

기존 내 게시글·댓글 검사는 3페이지 누적, 마이크로초 커서, 키보드 실패→재시도, 상세 왕복 위치, 삭제 원문 복귀, 갱신 실패, 계정 전환, 1440/390/320px을 포함한다. 새 경합 fixture는 2항목으로 응답 최신성·재사용 가능 상태를 확인한다. 단위 캐시 검사에는 3페이지 스냅샷을 사용했다. 서버 장애나 불확실한 변경 응답을 성공으로 판정하지 않았다.

## 실행 방법

기존 실행 중인 8443/8082는 종료·재시작하지 않았다. 이번 검증용 8459 서버만 시작/종료했다. 내 게시글/댓글 harness의 mock 서버는 8858을 사용하며 두 검사를 순차 실행했다.

```sh
cd web
pnpm lint
pnpm typecheck
pnpm test
pnpm build
# 별도 터미널, 검증용 프로덕션 서버
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8459
# Playwright 설치 경로는 환경에 맞게 설정한다 (새 패키지 설치 없음).
export BASE_URL=http://127.0.0.1:8459
export PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs
PASSWORD_CHANGE_RACE_DIR=/tmp/iyum-race node tests/password-change-race-browser.mjs
PASSWORD_CHANGE_ARTIFACT_DIR=/tmp/iyum-password node tests/password-change-browser.mjs
AUTH_FLOW_ARTIFACT_DIR=/tmp/iyum-auth node tests/auth-flow-browser.mjs
MEMBER_ARTIFACT_DIR=/tmp/iyum-member node tests/member-browser.mjs
MEMBER_ARTIFACT_DIR=/tmp/iyum-menu node tests/member-menu-browser.mjs
MEMBER_ARTIFACT_DIR=/tmp/iyum-posts node tests/member-posts-browser.mjs
MEMBER_ARTIFACT_DIR=/tmp/iyum-comments node tests/member-comments-browser.mjs
```

실행된 Node는 24, pnpm은 10.34.3. 의존성·브라우저 실행 파일은 검토 ZIP에 포함하지 않는다. 브라우저 인증/개인 API는 위 모의 harness가 가로채며 서버 공개 조회도 8858 mock으로 처리한다.

## 기록·캡처·한계

- `password-change-race-2026-10-24/summary.json`: 전후 결과 및 검사 요약.
- `before-production/`과 `after/`: 동일 새 검사 결과와 요청/응답 순서. 이전 실패 기록 삭제 없음.
- `regression-*/`: 최종 코드에서 실제 실행한 기존 회귀 결과·캡처.
- `checks/`: 실제 명령 출력. 비밀번호·쿠키·CSRF 원문·요청 본문은 수집하지 않았다.
- `after/comments-retained.png`: 모바일, 이전401 뒤에도 선택 탭·회원·성공 안내·목록 유지.
- `after/profile-retained.png`: PC, 이전401 이후 계정 관리 및 목록 접근 유지.
- `regression-password/dialog-1440.png`, `dialog-390.png`, `dialog-320.png`: 이번 실행의 모달 화면.

**실제 Spring 경합/비밀번호 변경은 이번에 실행하지 않았다.** 실제 계정·DB·비밀번호 변경 0건. 기존 문서의 6/6 실제 연동(비밀번호 복원 포함)은 이전 작업 기록이며 이번 결과로 계산하지 않는다. 백엔드 전체 테스트, 실제 iPhone Safari/소프트 키보드, 브라우저 비밀번호 관리자는 이번에 미검증이다. 이 작업에서 과거 모든 공개 404 문제나 다른 기능 전체를 재검증·해결했다고 주장하지 않는다.

사용자 개발 서버 8443/8082와 별도 iyum-preview worktree를 보존했다. 검토 ZIP에는 기존 구현의 HEAD 대비 diff와 이번 시작 상태 대비 diff를 분리한다. 비밀값은 패키징 복사본에서만 제거하고 원본은 수정하지 않는다.
