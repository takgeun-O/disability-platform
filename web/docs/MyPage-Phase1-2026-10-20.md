# 마이페이지 1차 구현 — 2026-10-20

## 기준 및 구현 범위

- 시작 브랜치 `feat/member-profile`, HEAD `fcbb48e093830d4a6d785587f81e668b129bdb28`. 시작 작업 트리는 깨끗했다. 기존 커뮤니티·정보 화면의 커밋된 구현을 기준으로 작업했다.
- `/mypage` 신규 경로. 공통 헤더의 로그인 회원 이름을 마이페이지 링크로 연결했다. 공통 로고·메뉴·로그아웃·이탈 확인 구조는 재사용한다.
- 기본 아바타, 닉네임, 서울 시간 기준 가입 연월, 실제 게시글/댓글 개수, 읽기 전용 이메일, 활동 탭 배치.
- 활동 목록은 **준비 중** 안내다. 실제 목록 API·커서·가짜 예시 게시글·더 보기·정렬 드롭다운은 구현하지 않았다.

## 코드로 확인한 API 계약

| API | 성공 계약 | 구현 |
| --- | --- | --- |
| GET `/api/v1/members/me` | 200, memberId/email/nickname/createdAt | 현재 인증 회원 ID와 응답 ID 일치 검사 |
| GET `/api/v1/members/me/activity-summary` | 200, postCount/commentCount | 0 이상 안전한 정수 검증; 누락/오류를0으로 보정하지 않음 |
| PUT `/api/v1/members/me/nickname` | `{nickname}` → 200 최신 프로필 | 204가 아님. 세션 및 기존 CSRF 발급/헤더 사용 |

백엔드 MemberProfileController, MemberActivityController, MemberNicknameUpdateController, DTO, MemberInputPolicy, MemberNicknameUpdateService, 검증기, GlobalExceptionHandler, DevCorsConfig, 관련 컨트롤러/세션 테스트를 확인했다. 별도 백엔드 수정은 하지 않았다.

닉네임은 Java strip 기준 앞뒤 공백 정리 후 한글 완성형/영문/숫자/밑줄2~20자다. 관리자/운영자/admin/administrator/moderator는 대소문자 무관 예약어다. 프런트도 이를 반영하며 최종 판정은 서버가 한다. NBSP처럼 Java strip이 제거하지 않는 문자를 JS trim으로 더 넓게 허용하지 않는다. 중복409 NICKNAME_ALREADY_EXISTS,400 VALIDATION_FAILED/INVALID_NICKNAME은 입력칸 아래 한 번만 표시한다.403 CSRF_TOKEN_INVALID와 회원 상태/권한 오류,401 세션 만료,네트워크 불확실 응답은 구분한다.

## 주요 파일과 학습 순서

1. `src/app/mypage/page.tsx`: Next 라우트와 메타데이터/스타일 연결. 개인 정보를 서버/정적HTML에 넣지 않는다. 초기HTML은 안전한 제목/로그인 확인 안내뿐이며 개인정보 조회는 클라이언트에서 수행한다. 기존 noindex/nofollow 유지.
2. `src/features/MyPage.tsx`: 디자인과 화면 상태. `MyPage()`가 인증 확인/오류/비로그인/회원 화면을 구분한다. `MemberPage()`는 프로필과 활동 요약을 독립 조회하여 한 영역의 실패가 다른 결과를 지우지 않게 한다. `NicknameEditor()`는 기존 값으로 열기→검증→CSRF GET→PUT→정상프로필 확인→저장완료 흐름이다.
3. `src/lib/member-api.ts`: 실제 데이터 연결. 기존 NEXT_PUBLIC_API_BASE_URL, getAuthCsrf를 재사용하고 credentials include/no-store를 적용한다. GET에 CSRF를 추가하지 않는다. 성공응답의 필드와 현재 회원 ID를 검증한다. 변경 요청의 자동 재전송은 없다.
4. `src/lib/auth-session.ts`, `components/AuthProvider.tsx`: 기존 인증 store에 `updateNickname(memberId,nickname)`과 `expire(memberId)`만 추가했다. 검증된 현재 회원 프로필로 헤더 닉네임을 갱신하며, 다른 계정이나 진행 중 인증 작업에는 적용하지 않는다.401은 현재 회원의 인증 상태만 비로그인으로 바꾼다. 기존 로그인/로그아웃/API 클라이언트는 재작성하지 않았다.
5. `components/AuthControls.tsx`, `styles/iyum.css`: 회원 이름에 AppLink와44px 터치 영역/hover를 적용했다. 기존 전역 focus-visible 유지.
6. `styles/mypage.css`: 기존 네이비/청록/화이트 토큰 사용. 최대960px, 모바일1열, 카드·구분선·안내 영역. 큰 숫자/긴 닉네임에서도 가로 넘침을 방지한다.
7. `tests/member-api.test.mjs`, `member-browser.mjs`, `member-local.mjs`: 순수 계약/상태, 모의 UI, opt-in 실제 테스트를 구분한다.

## 개인정보·늦은 응답·초점

회원 화면은 인증 확인/로그아웃 대기/계정 전환 시 해제되며, 회원 ID별로 컴포넌트 키를 분리한다. GET은 AbortController와 기존 NavigationRequest scope로 늦은 결과를 차단한다. PUT은 이탈 확정/언마운트 후 UI 반영을 무효화하며 CSRF 대기 후 이탈했다면 PUT 자체를 시작하지 않는다. 서버에서 이미 처리한 변경을 취소했다고 표현하지 않는다. 사용자 정보나 초안을 localStorage/sessionStorage에 저장하지 않는다.

닉네임은 모달 대신 카드 내부 폼으로 편집한다. 버튼 클릭 때만 입력에 초점을 주며 저장/취소 후 프로필 수정 버튼으로 돌려준다. 저장 중 입력·저장·취소를 비활성화하여 중복 저장과 닫힘 경합을 방지한다. 헤더 탐색/로그아웃은 기존 정책을 유지한다. 실패 시 값을 보존하고 필드 오류는 입력 아래의 고유 ID/aria-invalid/describedby로 연결한다. 완료 안내는 role=status로 전달하며 강제 페이지 이동은 없다. 별도 영구 초안/자동 저장/닉네임 이탈 경고는 추가하지 않았다.

탭은 좌우/Home/End 키로 이동한다. 아직 목록을 조회하지 않았으므로 빈 목록이라고 단정하지 않는다. 이메일 인증 여부는 API에 없으므로 배지를 표시하지 않는다.

## 이번에 실행한 검증

| 실행 | 결과 |
| --- | --- |
| `pnpm test` | 274/274 통과 (신규 회원 API/인증 상태 검사9개 포함) |
| `pnpm lint` | 통과, 경고 없음 |
| `pnpm typecheck` | 통과 |
| `pnpm build` | 통과, `/mypage` 생성 |
| `git diff --check` | 통과 |
| `BASE_URL=http://127.0.0.1:8451 ... node tests/member-browser.mjs` | 프로덕션 Next + 모의 API20/20 통과 |
| `BASE_URL=http://127.0.0.1:8451 ... node tests/login-browser.mjs` | 기존 로그인·로그아웃 모의 회귀38/38 통과 |
| 실제8443/8082 브라우저 | 테스트 회원 로그인/프로필·활동 요약,CSRF PUT200/헤더·프로필·auth/me 일치,새로고침 반영,원래닉네임 복원4개 통과 |

브라우저 테스트는 PLAYWRIGHT_MODULE에 설치된 Playwright 경로를 지정했다. `member-local.mjs`의 재실행은 `IYUM_RUN_MEMBER_LOCAL=true` opt-in이 필요하다. 실제 테스트는 비공개 seed manifest/계정의 소유 관계를 확인한 회원34만 사용했다. 새 회원/글/댓글은 생성하지 않았다. 닉네임을 임시 변경한 뒤 finally에서 원래 값으로 복원했고, 정상200/복원값까지 확인했다. updatedAt은 정상 수정/복원에 따라 갱신될 수 있다. 비밀번호·세션·토큰은 기록하지 않았다. DB 직접 변경/인증 우회 없음.

20개 모의 검사는 정상/0건/독립로딩·실패·재시도,키보드·취소·미변경요청 차단,중복요청 방지,200 헤더동기화,400/409/403/401/네트워크 오류,로그인 복귀,로그아웃 후 늦은PUT,계정 전환 후 늦은GET,CSRF 대기 중 이탈,1440/390/320과긴문자·큰숫자를 포함한다. GET 목록 API는 호출하지 않는 것도 확인했다.

첫 UI 검사에서 큰 숫자의 모바일 탭 넘침을 찾아 수정했다. 일부 테스트의 탭 접근명/라우트 완료 대기/중복 로고 선택자를 바로잡은 후 최종20개 모두 재실행했다. 외부 빌드 복사본의 node_modules 외부 심볼릭링크가 Turbopack에 거부되어 의존성을 복사해 검증했다. 설치된 oxfmt가 신규 타입 구문의 세미콜론을 잘못 변환한 출력은 폐기하고 복구했으며, 최종 lint/typecheck/build가 통과한 소스만 남겼다.

기존 개발 `.next`에 영향을 주지 않도록 빌드와 타입 검사는 외부 복사본에서 실행했다. 최종src가 원본과 같은지 확인한다. 프로덕션 검증용8451만 종료하며 기존8443/8082는 유지한다. 이번 변경과 무관한 전체 커뮤니티/무JS404 전용 검사는 반복하지 않았다. 기존 상세 API 중복조회/무JS404 관련 기록을 이번 변경의 해결로 보고하지 않는다. 실물 스마트폰 대신 Chrome viewport1440/390/320을 사용했다. 모의 캡처에 표시한 회원·숫자는 테스트 데이터이며 서비스 하드코딩이 아니다.

## 캡처·직접 확인

`mypage-2026-10-20/mock/`의 mypage-1440.png, mypage-390.png, mypage-320.png, nickname-*.png 및 mypage-long-320.png. 실제 검증 결과는 actual/results.json, 로그인 회귀는 login/result.json에 구분했다.

1. `http://127.0.0.1:8443/mypage` → 비로그인이면 로그인하기 → 로그인 후 마이페이지 복귀.
2. 회원 이름/가입 연월/개수/이메일 확인 → 프로필 수정 → 닉네임 입력 → 저장 → 카드와 헤더 동시 반영.
3. 수정 후 취소/잘못된 값 입력/활동 탭 키보드 이동 확인. 헤더 회원 이름으로 언제든 진입할 수 있다.
4. 로그아웃하면 같은 경로에서 개인정보가 사라지고 로그인 안내가 표시된다.

## 다음 범위

내 게시글·내 댓글 목록 및 커서/더보기/댓글 위치 이동은 후속 작업이다. 사진 업로드·이메일 변경·비밀번호 변경·탈퇴·좋아요·북마크와 인증완료 추정 배지는 구현하지 않았다. 백엔드 계약의 부족이나 실제 연동 차단은 발견하지 않았다. 커밋·push·PR·병합·배포 없이 로컬 변경 상태로 남긴다.
