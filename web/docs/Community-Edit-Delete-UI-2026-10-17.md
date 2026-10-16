# 게시글 수정·삭제 프런트엔드 연동 — 2026-10-17

## 기준과 범위

- 브랜치 `feat/community-post-edit-delete`, HEAD `7a3362f95021e9c873bafaa82c265b32612593f7`. 시작 작업 트리 깨끗함.
- 루트/web AGENTS와 설치된 Next16.3.6 useRouter 문서 확인. 기존 8443·8082·Mailpit 서버 재사용. preview worktree 변경 없음.
- 백엔드·DB 스키마·CORS·인증 계약·배포 설정 변경 없음. 커밋/push/PR/병합 없음.
- 사용자의 Swagger/자동화 테스트 확인 보고(본인 수정·삭제/타인403)는 사용자 검증 이력이다. 아래 Codex 직접 검사와 구분한다.

## 코드에서 확인한 계약

- PUT `/api/v1/community/posts/{postId}`: type/topic/title/content 모두 필수, 204 빈 응답.
- DELETE 같은 주소: 본문 없음, 204 빈 응답, 논리 삭제.
- 오류: 401 AUTHENTICATION_REQUIRED, 403 POST_WRITE_NOT_ALLOWED(계정 상태), 403 ACCESS_DENIED(작성자 권한), 403 CSRF_TOKEN_INVALID, 404 POST_NOT_FOUND, 400 VALIDATION_FAILED/기존 형식 오류.
- PostAuthorResponse.memberId와 AuthProvider.member.memberId 비교 가능. 닉네임/email 비교 없음.
- DevCorsConfig의 게시글 상세 경로는 PUT/DELETE를 별도로 허용. 백엔드 추가 변경 필요 없음.

## 화면과 핵심 함수

### 상세와 삭제

`src/features/PostDetail.tsx`는 본문 위에 `components/community/PostActions.tsx`를 배치한다. 작성자 정보 바로 아래에 수정·삭제를 두며, authenticated 상태의 정확한 회원 ID가 일치할 때만 노출한다. checking/로그아웃 중에는 숨긴다. 서버 권한 검증은 그대로다.

`PostActions.remove()`는 기존 CommunityDialog 안에서 DELETE를 보낸다. 처음 초점은 취소, Escape/Tab 순환과 닫을 때 원래 버튼 초점을 기존 컴포넌트가 처리한다. 요청 중에는 취소가 aria-disabled이고 이벤트 가드로 닫히지 않으며 Escape도 무시한다. 취소는 탭 순서에 남으므로 진행 중 초점이 사라지지 않는다. 요청 완료 뒤 오류를 읽고 취소/재판단할 수 있다. 404 이후 재삭제는 비활성화한다. 삭제를 영구 물리 삭제라고 설명하지 않는다.

### 수정

- 신규 라우트 `src/app/community/posts/[postId]/edit/page.tsx`: 기존 공개 서버 조회로 초기 데이터를 전달한다. 인증/권한은 클라이언트에서 확인하고 변경은 서버가 최종 판정한다. 오류/없는 글은 전용 화면에서 구분한다.
- `src/features/PostEdit.tsx`: `PostEdit`는 최초/재시도 조회, `EditForm`은 안정적으로 초기화된 폼을 맡는다. 수정 권한 없는 직접 접근에는 폼을 보여주지 않는다. 비로그인 로그인 복귀 주소도 기존 safeReturnPath를 거친다.
- `EditForm.submit()`: 입력 검증 → CSRF 조회 → PUT → 204 확인 → dirty 해제 → 상세 이동. 네 필드를 복사해 전송하고 실패 시 입력 유지. 401은 auth.refresh로 세션을 확인하며 dirty 폼은 남긴다. 로그인 화면으로 이동하는 경우 기존 이탈 확인이 있으며, 로그인 왕복 초안 영구 저장은 추가하지 않았다.
- `components/community/PostFields.tsx`: 기존 PostCreate의 라벨·유형/주제·제목/본문·코드포인트 길이·필드 오류 연결을 그대로 추출했다. 새 입력 규칙이나 의존성 없음.
- 원본 값과 비교해 dirty를 계산한다. 모두 원상 복구하면 경고하지 않는다. 취소는 상세로, 상세의 목록 복귀는 기존 안전한 from 조건으로 돌아간다. 주제/유형을 변경해도 이전 목록 조건을 자동으로 바꾸지 않으며 새 조건에 맞지 않는 글이 이전 목록에 나온다고 안내하지 않는다.

### API·응답 수명·캐시

- `src/lib/community-api.ts`의 `changePost()`: 기존 URL/credentials/no-store/JSON 오류 검증 재사용. 204에서는 JSON 파싱을 생략한다. 새 CSRF를 매번 조회하되 변경 요청 자동 재전송 없음. 네트워크/비정상 성공은 결과 불확실로 구분하고 새 탭 상세 확인 링크를 제공한다.
- `useNavigationRequest()`와 `useUnsavedChanges()`를 그대로 사용한다. 승인된 이탈은 실제 언마운트보다 빠르다. CSRF 이후 변경 요청 전, 성공/실패 후 UI/초점 처리 전에 current를 검사한다. 계속 수정 선택은 요청을 무효화하지 않는다. 서버 저장을 취소/롤백했다는 안내는 하지 않는다.
- `src/lib/community-mutations.ts`는 이 탭의 변경 버전만 기록한다. 회원/게시글 데이터나 초안을 저장하지 않고 브라우저 저장소도 사용하지 않는다. 성공한 ID와 목록의 버전을 올리며, PostList/PostDetail이 오래된 라우터 캐시의 값을 보여주지 않고 no-store GET을 다시 수행한다. 승인된 이탈 뒤 저장 성공도 데이터 무효화만 하며 이동/오류/초점을 덮어쓰지 않는다.
- 기존 서버 최초 조회와 정상 최초 진입 중복 방지는 유지한다. 이 탭에서 변경한 뒤의 목록/해당 상세 진입은 한 번의 추가 공개 GET으로 캐시를 확인한다. 다른 탭/다른 사용자의 변경을 실시간 푸시하는 기능은 추가하지 않았다.
- 강제 제목 초점/목록 스크롤 추가 없음. 기존 필터/페이지/복귀 정책 유지.

## 검증

- `pnpm test`: 199 통과(신규 변경 API 검증 25 포함), 실패/skip 없음.
- `pnpm typecheck`, `pnpm lint`, `git diff --check`: 통과.
- 기존 dev 산출물을 건드리지 않고 환경파일 없는 임시 복사본에서 `pnpm build`(Turbopack) 통과. 별도 Webpack 빌드도 통과. 첫 회귀는 Webpack 프로덕션, 최종 보강 회귀는 Turbopack 프로덕션에서 수행.
- 신규 브라우저 최종 24개 통과, 런타임 오류 0. 작성자 확인, 직접 진입/로그인 복귀, 입력·CSRF·204·권한/세션/404, 결과 불확실, 중복·이탈, 1440/390/320과 키보드 검사 포함.
- 목적지 RSC 응답을 지연해 폼이 계속 마운트되어 있음을 확인한 뒤 CSRF/PUT 성공/실패를 해제하는 회귀 포함. 기다리는 시간을 늘려 우회하지 않았다.
- 삭제 중 aria-disabled 취소를 일반 Playwright click으로 누르던 첫 검사 방식이 대기하여 중단되었다. 비활성 의미를 유지하고 강제 클릭에도 이벤트 가드가 적용되는 검증으로 바로잡았다. 서비스 실패를 숨기거나 검사 항목을 삭제하지 않았다.
- 기존 커뮤니티 49개 모의 회귀 통과. 기존 주제 23개 모의 회귀도 통과.
- 기존 게시글 무JS404 검사: 5 통과, **2 실패**(일반 브라우저/크롤러 표시 본문·링크). HTTP404/noindex와 정상 SSR은 통과. 과거 알려진 실패와 같은 조건이며 이번 작업에서 해결하거나 검사를 약화하지 않았다.

### 실제 Spring/DB 검증 — Codex 직접 실행

`tests/community-edit-delete-local.mjs`는 명시적 opt-in을 요구하며 새 계정/게시글만 사용한다. 기존 8443·8082·8025에서 모의 응답 없이 다음 4묶음 통과:

1. 회원가입 → PENDING → Mailpit 링크 → 수동 이메일 인증 → ACTIVE → 직접 로그인.
2. 게시글 작성 201/중복 제출 방지 → 상세 → 새로고침.
3. PUT 204 → 최신 제목·본문 → 새로고침 → 원래 목록에서 변경 반영.
4. 삭제 취소 시 상세200 유지 → DELETE204 → 목록 제외 → 상세404 → 삭제된 글 PUT/DELETE404.

생성 계정 memberId **29**, 테스트 주소 `iyum-community-mvb7o0d3@example.test`, 닉네임 `UI_mvb7o0d3`, ACTIVE. 생성 게시글 id **8**, 검증 후 **논리 삭제**. 비밀번호/쿠키/CSRF/인증 토큰/메일 원문은 기록하지 않았다. 기존 데이터 삭제/초기화, 직접 DB 수정 없음. 다른 실제 회원으로 403 검증은 이번에는 실행하지 않았으며 사용자 보고와 모의 오류 검사를 구분한다. 전체 백엔드 테스트·실제 모바일 기기·배포 환경 검증은 미실행.

## 재실행과 확인

단위/타입/lint/build 명령은 web/package.json 기준. 모의 브라우저는 임시 Next 서버의 IYUM_API_INTERNAL_URL을 mock port에 맞춘다. PLAYWRIGHT_MODULE은 기존 외부 런타임 경로를 지정한다.

```sh
BASE_URL=http://127.0.0.1:8453 COMMUNITY_SSR_MOCK_PORT=8857 node tests/community-edit-delete-browser.mjs
# 실제 로컬 계정/게시글을 생성하므로 의도한 경우에만 실행
IYUM_RUN_LOCAL_EDIT_DELETE_TEST=true node tests/community-edit-delete-local.mjs
```

사용자 확인: 8443에서 작성자 계정 로그인 → 본인 상세 → 수정 → 저장 → 상세/목록 반영 → 삭제 버튼 → 취소/확인. 직접 수정 주소는 `/community/posts/{id}/edit?from=...`. 기존 공개 링크와 주제 경로 유지.

[검증 결과·캡처](community/2026-10-17-edit-delete/)의 `real-*`는 실제 로컬 API, `mock-*`는 모의 데이터다. 비밀값 없는 결과 요약만 보관한다. 원시 서버/브라우저 로그는 저장소에 추가하지 않는다.

## 최종 상태

브랜치·HEAD 유지, 프런트 소스/테스트/문서만 미커밋 변경으로 남겼다. 백엔드와 preview worktree는 변경하지 않았다. 검증용 8451·8452·8453 및 모의 8856·8857 종료 확인. 기존 8443(Node PID 97959), 8082(Java PID 88297)는 계속 실행 중이다. 주소: http://127.0.0.1:8443/community .


## 리뷰 후속 — 2026-10-17: 상세 동작 배치·재진입 보호·유형/주제

### 작업 기준과 변경 범위

시작 브랜치 `feat/community-post-edit-delete`, HEAD `7a3362f95021e9c873bafaa82c265b32612593f7` 유지. 이전 수정·삭제 구현이 이미 미커밋/미추적 상태였으며 그 위에 아래 파일만 보완했다. 위 본문은 최초 구현 기록이며 이번 결과는 이 절과 `review-followup/`을 기준으로 읽는다.

- `src/features/PostDetail.tsx`: `PostActions`를 article header의 새 메타 행 안으로 이동.
- `src/components/community/PostActions.tsx`: 헤더 수정/삭제를 가벼운 텍스트 동작으로 변경. 삭제 모달의 강조/안전한 최초 초점/복귀와 권한 판정은 유지.
- `src/styles/community.css`: 상세 전용 flex-wrap 메타 행과 44×44px 이상 동작 영역. PC는 작성자·날짜 왼쪽/동작 오른쪽, 좁으면 동작이 다음 줄 오른쪽. 긴 제목의 줄바꿈 보강. 공통 버튼과 전역 초점 스타일은 변경하지 않음.
- `src/features/PostEdit.tsx`: `PostEdit`의 편집 진입 시점 변경 버전을 고정. 현재 편집 세션이 유지되는 동안 원본 post와 폼을 백그라운드 변경 알림으로 교체하지 않음.
- `tests/community-edit-delete-browser.mjs`: 재진입 지연 응답, 유형 단독/주제 단독/동시 변경, 헤더 배치·긴 콘텐츠·키보드 검증 추가.
- `tests/community-edit-delete-local.mjs`: 새 테스트 게시글의 유형·주제 선택을 실제 브라우저에서 바꾸고 Spring 조회/목록 개수/재진입을 확인. 실제 320px 상세 캡처도 저장.

기존 `PostCreate`, `PostList`, API 클라이언트의 미커밋 변경은 보존했다. 이번 후속에서 `community-mutations.ts`, `NavigationGuard.tsx`, `navigation-request.ts`, 백엔드·DB·API 계약·preview worktree는 변경하지 않았다.

### 재현된 입력 유실과 보완 이유

수정 전 프로덕션 복사본에서 PUT 응답을 보류했다. 이탈을 승인하여 상세로 이동한 후 같은 탭에서 동일 글 수정 화면에 재진입했다. 새 제목·본문·REVIEW/COCHLEAR_IMPLANT를 입력하고 이전 PUT 204를 해제하니 **새 폼의 기존 textarea DOM이 isConnected=false**가 되었다. `reentry-before-fix.json`에 실패 기록을 남겼다. 코드 추정만으로 결함을 확정한 것이 아니다.

이전 요청의 `postChanged(id)`가 공통 변경 버전을 올리면 재진입한 PostEdit의 `loadedVersion !== version` 조건이 true가 되어 EditForm을 로딩 문구로 교체했다. EditForm 내부의 입력 상태가 제거되고 이후 조회가 새 원본/새 폼을 만드는 것이 원인이다. 요청 수명 검사는 이전 요청의 router 이동/오류를 막지만, 공통 데이터 무효화에 반응하는 새 편집 폼까지 보호하지는 못했다.

`mutationVersion`을 읽은 뒤 `useState(mutationVersion)`으로 **해당 편집 화면의 진입 버전**을 고정했다. 이미 알려진 변경이 있는 새 진입에서는 기존 최신 GET을 수행한다. 폼을 연 이후의 변경 알림은 재조회나 원본 교체를 유발하지 않는다. 재시도 버튼은 기존 오류 화면에서만 가능하다. 저장 성공 알림 자체는 제거하지 않았으므로 상세/목록은 계속 최신 버전을 조회한다.

원본 post가 안정적으로 유지되어 dirty 비교 기준도 바뀌지 않는다. 새 편집값을 모두 원래 진입 값으로 되돌리면 dirty가 해제되는 것까지 검증했다. 서버의 저장 취소/롤백, 자동 저장, 영구 초안 저장, 충돌 판정 기능은 추가하지 않았다.

수정 후 같은 순서에서 폼 DOM 연결, 네 입력값, dirty 및 원복 시 clean, textarea 초점, 수정 URL이 유지됐다. 다시 취소할 때 계속 수정/나가기 동작도 유지됐고, 나간 상세와 원래 목록에서는 이전 PUT이 실제 저장한 제목을 확인했다. 응답 도착 이벤트와 렌더링 프레임을 기준으로 검증하며 응답 지연 시간을 늘려 통과시키지 않았다. 최초 재현 스크립트에서 모의 204의 Playwright `response.finished()` 대기가 끝나지 않아, 실제 응답 도착 이벤트를 기다리는 방식으로 검사 수단을 정리했다.

### 유형·주제 검증

모의 프로덕션 검사는 QUESTION/HEARING_AID에서 각각 REVIEW/HEARING_AID, QUESTION/COCHLEAR_IMPLANT, REVIEW/COCHLEAR_IMPLANT로 UI 선택을 변경했다. 네 필드 PUT 본문, 상세 태그와 공개 GET 값, 같은 탭 수정 재진입과 새로고침의 선택값, 원래 복귀 주소 유지, 이전 조건 목록 1→0 및 새 조건 0→1, 실제 목록 표시/빈 상태를 모두 확인했다.

실제 로컬 검사는 이번 생성 게시글 한 건만 사용했다. GENERAL/WELFARE_LIFE → QUESTION/WELFARE_LIFE(유형만) → QUESTION/HEARING_AID(주제만) → REVIEW/COCHLEAR_IMPLANT(동시) 순서로 수행했다. 각 변경 전 두 목록의 전체 개수를 조회하여 이전 조건 -1/새 조건 +1을 확인하고, 상세 태그/GET/새로고침/수정 재진입/목록 표시까지 확인했다. 기존 사용자 게시글이나 DB는 직접 변경하지 않았다.

### 직접 실행한 결과

| 검사 | 이번 결과 |
|---|---|
| `pnpm test` | 199 통과, 실패/skip 0 |
| `pnpm typecheck` / `pnpm lint` | 모두 종료 코드 0 |
| `pnpm build` | 표준 Turbopack 프로덕션 빌드 통과 |
| 신규·기존 수정삭제 모의 브라우저 | 29 통과, pageErrors 0 |
| 기존 community-browser | 49 통과 |
| 기존 community-topics-browser | 23 통과 |
| 실제 Spring/Mailpit 흐름 | 7묶음 통과 |
| 기존 community-404-browser (변경 없음) | 5 통과, **2 실패 유지** |
| `git diff --check` | 통과 |

단위/타입/lint/build는 기존 개발 산출물을 건드리지 않는 `/private/tmp/iyum-edit-build-final/web` 복사본에서 실행했다. 소스·테스트는 현재 저장소와 동일한 바이트인지 확인했다. Next 지침은 설치된 `use-router.md`를 읽고 준수했다.

```sh
# web 또는 현재 소스를 복사한 검증용 web 디렉터리
pnpm test
pnpm typecheck
pnpm lint
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 pnpm build
# 모의 검사용 임시 프로덕션 서버
IYUM_API_INTERNAL_URL=http://127.0.0.1:8857 pnpm exec next start --hostname 127.0.0.1 --port 8453
BASE_URL=http://127.0.0.1:8453 COMMUNITY_SSR_MOCK_PORT=8857 node tests/community-edit-delete-browser.mjs
BASE_URL=http://127.0.0.1:8453 COMMUNITY_SSR_MOCK_PORT=8857 node tests/community-browser.mjs
BASE_URL=http://127.0.0.1:8453 COMMUNITY_SSR_MOCK_PORT=8857 node tests/community-topics-browser.mjs
# 실제 검사는 새 테스트 회원/게시글을 만드는 명시적 opt-in
IYUM_RUN_LOCAL_EDIT_DELETE_TEST=true node tests/community-edit-delete-local.mjs
# 별도 8454 프로덕션은 IYUM_API_INTERNAL_URL=http://127.0.0.1:8082 사용
BASE_URL=http://127.0.0.1:8454 node tests/community-404-browser.mjs
```

브라우저 검사에는 기존 외부 Playwright 런타임을 `PLAYWRIGHT_MODULE`로 지정했다. 원시 로그/쿠키/토큰/메일 원문은 저장소에 넣지 않고 결과 JSON만 추가했다.

실제 추가 데이터: memberId **30**, `iyum-community-mvb8vjxn@example.test`, 닉네임 `UI_mvb8vjxn`, ACTIVE. 게시글 id **10**, 검증 종료 시 DELETE로 **논리 삭제**. 최초 구현 검증의 회원29/글8은 이번에 사용하거나 수정하지 않았다. 실제 다른 회원으로 권한403, 실제 휴대전화, 배포 환경, 전체 백엔드 테스트는 이번에도 미실행이며 모의/기존 사용자 확인 보고와 구분한다.

기존 게시글 무JS 404는 일반 브라우저·크롤러의 표시 본문/링크 검사 두 건이 여전히 실패한다. HTTP404/noindex와 정상 목록·상세 SSR 및 JS 복귀 검사는 통과했다. 이번 수정의 해결 사항으로 보고하지 않으며 검사를 약화하지 않았다.

### 직접 확인 순서와 최종 상태

1. `http://127.0.0.1:8443/community`에서 본인 글 상세로 이동한다. 메타 정보 오른쪽의 수정/삭제와 390/320px 줄바꿈을 확인한다.
2. 키보드로 수정→삭제를 이동하고 삭제 Enter→취소/Escape 후 원래 버튼 초점을 확인한다.
3. 수정에서 유형/주제를 변경하고 저장한 뒤 태그와 새 목록을 확인한다. 기존 목록 복귀 주소는 유지되지만 조건이 달라진 글은 그 목록에서 사라진다.
4. 응답 순서 제어가 필요한 입력 유실 시나리오는 위 모의 브라우저 검사를 실행한다.

[이번 실제·모의 화면과 결과](community/2026-10-17-edit-delete/review-followup/README.md). 기존 기록·캡처는 보존했다. 브랜치/HEAD 유지, 미커밋 변경으로 남김. 커밋·push·PR·병합·배포 없음. 기존 8443 프런트와 8082 Spring/Mailpit 유지, 직접 실행한 임시 8453/8454 프로덕션 및 8857 모의 서버 종료를 확인했다. 기존 8443(Node PID 97959), 8082(Java PID 88297)는 그대로 실행 중이다.

검증용 프로덕션 서버 로그에는 `The destination stream closed early`가 관찰됐다. 의도적으로 이동/요청을 중단하는 시나리오를 포함했으며, 브라우저 런타임 오류 검사에는 기록되지 않았다. 이 서버 로그의 개별 발생 원인을 이번 범위에서 추가 분석하지 않았다.


## 상세 헤더 외곽선 버튼 — 2026-10-17

텍스트 동작의 위치를 유지하고 15px/600의 절제된 외곽선 버튼으로 보완했다. 수정은 청록, 삭제는 차분한 muted 색상이다. 이번 변경은 PostActions.tsx와 community.css의 상세 전용 클래스에 한정한다. [이번 검사 범위·결과 및 세 화면 폭 캡처](community/2026-10-17-edit-delete/outline-buttons/README.md)를 참고한다. 전체 회귀/빌드/실제 API 검사는 이번 스타일 작업에서 재실행하지 않았다.
