# 마이페이지 내 게시글 — 실패·재시도 키보드 초점 보완

## 작업 기준과 범위

- 2026-10-20, `feat/member-profile`, HEAD `306d26993fddd31dd440c1072c6d338b3543e95f`.
- 작업 전 루트/web AGENTS와 현재 구현을 확인했다. 이전 내 게시글 구현의 수정 추적 파일9개·미추적 파일21개를 보존했다.
- 설치된 Next16.3.6의 `dist/docs/03-architecture/accessibility.md`, `01-app/02-guides/server-and-client-boundary.md`를 읽었다. 기존 Client Component/라우팅/접근성 알림 구조를 유지했다.
- 서비스 코드 변경은 `src/components/member/MyActivity.tsx` 한 파일. 브라우저 검사는 `tests/member-posts-browser.mjs`에 추가했다. 백엔드·API·DB·메뉴·디자인 토큰은 변경하지 않았다.

## 원인과 수정 전 재현

기존 더 보기 버튼과 오류 영역의 재시도 버튼은 서로 다른 DOM이었다. 더 보기 실패 시 현재 초점 버튼이 제거됐고, 재시도 시작 시 `setError(null)`이 오류 영역과 재시도 버튼을 제거했다. 정상 완료의 `document.activeElement === moreTrigger.current` 조건은 이미 제거된 버튼과 일치하지 않아 새 결과로 이어지지 않았다. 최초 조회 재시도도 같은 문제였다.

수정 전 프로덕션 빌드에서 기존18개는 통과하고 새23개는 실패했다. 새 검사는 응답을 테스트가 직접 해제할 때까지 보류하고, 요청 중/실패 후/성공 후 실제 `document.activeElement`와 원래 버튼의 `isConnected`를 검사했다. 단순 문자열이나 기다리는 시간 증가로 판정하지 않았다.

- [수정 전 결과](member-posts-2026-10-20/retry-focus/before/results.json)
- [수정 전 더 보기 실패 화면](member-posts-2026-10-20/retry-focus/before/failure-5.png)
- [수정 전 최초 재시도 화면](member-posts-2026-10-20/retry-focus/before/failure-7.png)

## 처리 방식

`MyActivity`의 목록 동작 버튼을 한 DOM으로 합쳤다. 같은 위치의 동일 버튼이 더 보기 → 재시도 → 불러오는 중 → 재실패 상태를 유지한다. 오류는 성공 시 해제하므로 요청 중 버튼이 사라지지 않는다. 디자인은 기존 오류 영역과 더 보기의 클래스/문구를 그대로 사용한다.

- `load(kind, source?)`: 사용자 동작의 버튼/키보드 여부를 **유효한 요청 시작 시점**에 받는다. 중복 요청은 기존 request 참조로 동기 차단하고, 요청 중에는 `aria-disabled`를 사용하여 버튼을 Tab 순서와 현재 초점에서 제거하지 않는다. 자동 목록 갱신에는 키보드 초점 의도를 부여하지 않는다.
- 실패/재실패: 같은 버튼에서 재시도할 수 있다. 기존 목록·커서는 유지하며 오류는 alert, 로딩·성공은 status로 안내한다. 같은 커서 재시도와 중복 요청 검사를 유지했다.
- 키보드 성공: 응답 시점에도 요청 버튼에 초점이 있고 게시글 탭이 활성인 경우 새 첫 항목 링크로 연결한다. 추가 항목이 없는 마지막 페이지는 `my-posts-end`, 최초 조회 재시도의 실제 빈 목록은 `my-posts-empty`에 연결한다. 빈 안내의 `tabIndex=-1`은 프로그램 초점용이며 Tab 순서를 늘리지 않는다.
- `onBlur`/`chooseTab`: 사용자가 다른 요소로 이동하거나 탭을 바꾸면 해당 요청의 초점 이동 의도를 취소한다. 기다리는 동안 다른 곳을 거쳐 버튼으로 돌아와도 자동 초점 이동을 다시 활성화하지 않는다. 마우스 재시도도 제목 강제 초점을 만들지 않는다.
- 완료 layout effect: 요청 수명(scope), 회원 lease, 목록 버전, 현재 탭, 실제 버튼 초점/DOM 연결, 대상의 표시 여부를 다시 확인한다. 버튼이 마지막 응답으로 제거되는 경우에만 완료 직전의 초점 소유를 연결한다. `focus({preventScroll:true})`를 유지하며 새 타이머/무조건 scrollTo를 넣지 않았다.

기존 NavigationGuard 이탈 승인·AbortController·request identity·세션별 cache/lease 검사를 유지했다. 페이지 이탈·로그아웃 뒤 늦은 응답은 이전 목록이나 초점으로 돌아오지 않는다. Next history와 커서 원문, 목록 복원 계산은 변경하지 않았다.

## 이번에 직접 실행한 검사

Node24.21.0, pnpm10.34.3. 아래 결과는 이 후속 작업에서 새로 실행했다.

| 명령/검사 | 결과 |
|---|---|
| `pnpm lint` (web) | 통과 |
| `pnpm test` (web) | 280/280 통과 |
| `pnpm typecheck` (동일 소스 임시 web 복사본) | 통과 |
| `pnpm build` (동일 소스 임시 web 복사본) | 최종 통과 |
| `node tests/member-posts-browser.mjs` | 프로덕션 Next + 모의 API, 41/41 통과 |
| `git diff --check` 및 이번 두 파일의 시작본 대비 공백 검사 | 통과 |

기존 8443 서버의 `.next`를 건드리지 않도록 `/private/tmp/iyum-member-retry-focus-build`에 현재 src/public/설정/lockfile과 동일 의존성을 복사했다. 첫 빌드는 외부 node_modules 심볼릭 링크가 Turbopack 프로젝트 루트 밖을 가리켜 실패했다. 임시 폴더에 실제 의존성 파일을 복사한 뒤 빌드/타입 검사가 통과했다. 서비스 코드를 우회 수정하지 않았으며 [초기 환경 오류](member-posts-2026-10-20/retry-focus/build-initial-environment-error.txt)도 보존했다.

브라우저 실행:

```sh
# 격리 Next build/start에 사용한 값; 비밀값 없음
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858
# 임시 프로덕션 Next는 8452, 테스트 자체의 공개 SSR 모의 API는 8858
BASE_URL=http://127.0.0.1:8452 \
MEMBER_ARTIFACT_DIR=/private/tmp/iyum-member-retry-focus-final \
PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs \
node tests/member-posts-browser.mjs
```

새23개: 키보드 더 보기 실패/재실패/성공, 마지막 응답0개, 최초 오류 후 성공/빈 목록, 최초·추가 조회 각각에서 Tab/Shift+Tab/클릭 중 성공/실패(12개), 댓글 탭 전환 중 성공/실패, 초점을 떠났다가 돌아오는 경우, 마우스 재시도2개, 이탈·로그아웃 중 늦은 응답2개. 각 시나리오의 요청 중·응답 후 activeElement를 직접 검사했다.

기존18개도 삭제/skip 없이 실행했다. 원문 마이크로초/같은 커서 재시도, 중복 클릭, 3페이지 누적 및1440·390·320 상세 최상단/뒤로/목록 복귀, 긴 텍스트, 댓글·게시글 변경 후 목록·개수 갱신, 계정 전환/401/늦은 응답, 닉네임과 모바일 메뉴 검사를 유지했다. 새 성공 초점 검사에는 불필요한 scrollY 변화가 없는 조건도 포함했다.

- [최종41개 결과](member-posts-2026-10-20/retry-focus/after/results.json)
- [명령 결과 요약](member-posts-2026-10-20/retry-focus/checks.json)
- [PC](member-posts-2026-10-20/retry-focus/after/mypage-posts-1440.png), [390px](member-posts-2026-10-20/retry-focus/after/mypage-posts-390.png), [320px](member-posts-2026-10-20/retry-focus/after/mypage-posts-320.png), [긴 제목/키보드](member-posts-2026-10-20/retry-focus/after/long-keyboard-320.png)

## 검증 한계와 최종 상태

- 실제 Spring/API와 DB는 이번 검사에서 사용하지 않았다. 테스트가 브라우저 요청을 모의 처리하며, 서버의 공개 조회는 임시8858 모의 서버가 처리했다. 개발 DB 생성/수정/삭제0건. 이전 실제 API 통과 기록을 이번 결과로 재사용하지 않았다.
- 실물 스마트폰/소프트 키보드와 스크린리더 음성 출력은 미검증이다. Chrome 키보드 및 화면 크기 에뮬레이션 결과다.
- 과거 게시글 무JS404 전용 검사·상세 중복 GET 문제는 이번 범위에서 재검증/수정하지 않았다.
- 기존8443(PID38875), 8082(PID36509) 서버와 별도 preview worktree는 보존했다. 직접 시작한8452/8858 검증 프로세스만 정리했다.
- 기존 작업 파일을 초기화/덮어쓰지 않았다. 이번 서비스 변경1개·테스트 변경1개와 새 문서/검증 자료를 미커밋 상태로 남겼다. commit/push/PR/병합/배포 없음.
