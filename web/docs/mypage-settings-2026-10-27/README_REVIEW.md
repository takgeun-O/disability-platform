# 마이페이지 활동 중심 분리 — 검토 안내

## 기준과 범위
- 실제 브랜치: `feat/mypage-activity-redesign`
- 시작 HEAD: `416fabd176918ef32d2cb33798c603024ad57b02`
- 시작 작업 트리: clean. 이번 변경 이전의 미커밋 구현은 없었으며 기존 구현은 HEAD에 포함되어 있습니다.
- 작업 도중 무관한 미추적 `docs/design/IYUM_report_management_dashboard.png`가 생겼습니다. 이번 작업에서 생성/수정하지 않았고 그대로 보존하며 ZIP에서는 제외합니다.
- 백엔드/API/DB 변경, 실제 사용자 변경, 커밋·push·병합·배포 없음.
- Node 24.21.0, pnpm 10.34.3, Next 16.3.6. 루트/web AGENTS 및 PRODUCT-CONTEXT와 설치된 Next pages/layouts 문서 확인.

## 최종 화면과 파일
- `web/src/app/mypage/settings/page.tsx`: `/mypage/settings` 새 경로, noindex metadata. 개인 데이터는 클라이언트 인증 확인 이후 기존 no-store API로만 조회합니다.
- `web/src/features/MyPage.tsx`: 동일 인증 셸/MemberPage를 `settings` 모드로 재사용. 프로필 조회·닉네임 수정·CSRF·성공 토스트·PasswordChangeDialog·WithdrawalDialog 로직을 복제하지 않았습니다. 활동 화면의 설정 진입은 실제 링크입니다. 설정은 프로필, 계정·보안, 구분된 탈퇴 영역으로 구성합니다. 설정에서는 활동 요약/목록 API를 호출하지 않습니다.
- `web/src/components/member/MyActivity.tsx`: 기존 snapshot 저장+markReturn 콜백을 설정 링크에서도 사용하도록 연결했습니다. Next history 필드를 보존합니다.
- `web/src/styles/mypage.css`: 최대840px 중앙 한 열, 프로필/활동 좌우 기준선 통일. 계정 사이드바/고정 위치 제거. 모바일 기존16px여백, 긴 링크 wrapping. 게시글/댓글 카드 및 오른쪽 읽기전용 하트 디자인 유지.
- 관련 브라우저 테스트: 옛 프로필 버튼 진입은 설정 진입으로 이동; 메인 화면 count/tab과 설정 화면 이메일/폼 검증을 구분. 설정3페이지 왕복, 직접 접근·새로고침·비로그인 복귀 검증 추가. 전체 변경 목록은 review/changed-files.txt 참조.

## 기존 구조 확인 및 보존
- 기존 `/mypage`의 NicknameEditor와 비밀번호/탈퇴 대화상자를 설정으로 옮겼습니다. 각각 독립 실행/저장/취소를 유지합니다.
- AuthProvider의 인증 세대 및 captureMemberRequest/memberRequests, useNavigationRequest, AbortController와 유효성 검사를 그대로 재사용합니다. 오래된 성공/401은 현재 인증과 개인 데이터를 덮어쓰지 않습니다. 비밀번호 변경 시 인증 갱신/후속CSRF/입력 초기화, 탈퇴 후 캐시 정리·홈 이동 유지.
- 활동 캐시는 인증 소유자별 메모리 lease이며 로그아웃·계정전환에 폐기됩니다. 브라우저 저장소에 개인 목록/비밀번호를 저장하지 않습니다.
- MyActivity의 history entry/선택탭/누적items/segments/cursor/position, MyComments snapshot 및 anchor/인접행 위치 복원을 유지합니다. 설정 링크에서 snapshot을 저장하고, 명시적 복귀는 기존 requestListScrollRestore, 브라우저 뒤로는 기존 history 복원을 사용합니다. 닉네임 변경만으로 활동을 무효화하지 않습니다.
- 활동은 window 스크롤이며 별도 내부 스크롤 컨테이너가 없습니다. 새 중첩 스크롤을 만들지 않았습니다.
- 댓글 likeCount/liked, 상세 변경과 늦은/불확실한 응답 후 무효화 흐름은 수정하지 않았고 관련 회귀 검사를 실행했습니다.

## 후속 댓글 펼치기 준비 — 이번에는 구현하지 않음
- MemberPostReadService가 반환하는 PostFeedItemResult/Response에는 contentPreview와 commentCount가 이미 있습니다. 이 두 값 표시에는 추가 API가 필요하지 않습니다.
- 기존 `GET /api/v1/community/posts/{postId}/comments`는 댓글 커서조회, POST는 댓글등록을 제공합니다. 선택한 게시글의 댓글을 펼쳐 읽고 쓰는 기본 기능은 기존 API로 가능합니다. 내 게시글 목록 응답 자체에는 댓글 본문 배열이 포함되지 않습니다. 펼칠 때 해당 글 댓글 API를 별도 조회해야 합니다.
- 기존 Comments는 상세 전용 `comments-title`, `comment-draft` 등 고정ID, #해시/포커스, NavigationGuard와 초안·초기서버결과를 함께 관리합니다. 여러 게시글 아래에 그대로 복제 렌더링하면 ID/포커스 충돌 위험이 있어, 후속 작업에서 인스턴스별ID와 초기조회/초안/이탈보호를 분리해야 합니다. API 클라이언트·검증·폼 정책은 재사용 가능하지만 컴포넌트 자체는 무수정 재사용을 보장하지 않습니다.
- 여러 글의 댓글 미리보기를 한 번에 응답하는 API나 배치 데이터는 현재 계약에 없습니다. 그런 요구가 정해지면 별도 백엔드 계약 검토가 필요합니다. 이번에는 추측해 추가하지 않았습니다.

## 검증
최종 제품 코드로 격리된 프로덕션 빌드 및 서버8465를 사용했습니다. 브라우저의 API는 Playwright mock, 서버공개조회는 mock8858입니다. 사용자 개발서버8443과 실제 Spring/DB는 건드리지 않았습니다.

- `pnpm lint`: 통과
- `pnpm typecheck`: 통과
- `pnpm test`: 381/381, fail/skip 0
- `pnpm build`: 통과, `/mypage/settings` 포함. `/private/tmp/iyum-member-count-build`에 동일 src/config/lockfile로 빌드하여 기존 개발 빌드와 분리했습니다.
- `git diff --check`: 통과

| 모의 브라우저 묶음 | 통과/실행 |
|---|---|
| member-final | 25/25 |
| comments | 67/67 |
| posts | 41/41 |
| posts-settings | 2/2 |
| password-final | 32/32 |
| withdrawal | 11/11 |
| toast-final | 15/15 |
| race-final | 37/37 |
| menu-final | 23/23 |

브라우저 합계253/253, 최종 실패/skip 없음. posts는 기존 전체41개+추가 설정왕복2개를 별도 실행한 결과입니다.

### 실행 방법
web에서 `pnpm install --frozen-lockfile` 후 지원 Node/pnpm을 사용하세요. 비밀정보 없는 mock용 환경:
```sh
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm start --port 8465
```
별도 터미널에서 BASE_URL=http://127.0.0.1:8465와 PLAYWRIGHT_MODULE(설치한 Playwright 절대경로)을 설정하고 다음을 순차 실행합니다. 실제 실행 시 Chrome channel을 사용했습니다.
```sh
node tests/member-browser.mjs
node tests/member-comments-browser.mjs
node tests/member-posts-browser.mjs
node tests/password-change-browser.mjs
node tests/withdrawal-browser.mjs
node tests/member-success-toast-browser.mjs
node tests/password-change-race-browser.mjs
node tests/member-menu-browser.mjs
```
comments/posts는 mock8858을 공유하므로 동시에 실행하지 마세요. MEMBER_ARTIFACT_DIR, PASSWORD_CHANGE_ARTIFACT_DIR, WITHDRAWAL_ARTIFACT_DIR, MEMBER_TOAST_DIR, PASSWORD_CHANGE_RACE_DIR로 결과 경로를 지정했습니다. 새 posts 왕복2개는 `/private/tmp/iyum-settings-posts-only.mjs`에서 이름 필터로 선택 실행했으며 최종 저장소 파일에는 필터/skip을 넣지 않았습니다. 선택 실행본도 검토 helper로 포함합니다.

### 중간 실패와 수정
- 첫 댓글 실행은 테스트 서버 내부 API 환경 변수 설정 오류와 검증 서버 재시작 때문에 실패했습니다. 사용자 서버가 아니라 이번 별도8465 서버만 수정하고 전체67개를 재실행하여 통과했습니다. `comments-server-setup-failed.log` 보존.
- 기존 테스트의 뒤로가기 목적지(/login→현재/mypage), 토스트 뒤쪽 레이아웃 대상(activity→settings account), 설정페이지에서 main 탭 검사, 메뉴의 현재경로·클라이언트 탐색 완료 대기를 새 경로에 맞췄습니다. 기존 인증/오류/초점 assertion을 제거하지 않았습니다.
- 인증경합의 현재댓글401은 복원된 탭이 이미 조회를 시작하므로 재클릭 전에 정상 로그아웃되었습니다. 불필요한 탭 클릭만 제거하고 401후 인증/개인정보삭제/호출횟수 assertion은 유지했습니다. 이후37/37.
- 중간 로그와 earlier-runs JSON을 보존했고 최종 결과와 구분했습니다. 테스트 타임아웃 확대/고정sleep 추가/검증완화로 해결하지 않았습니다.

## 화면과 수동 확인
- member-final/settings-1440,390,320.png: 설정 직접 접근/새로고침, 이메일/보안/탈퇴.
- member-final/empty-posts-*.png 및 empty-comments-*.png: 실제0 summary와 빈목록.
- comments/workspace-1440,390,320.png: 댓글이 있는 활동화면, 읽기전용 하트 유지.
- posts/mypage-posts-1440,390,320.png: 게시글이 있는 활동화면.
- comments/settings-filled-*.png, activity-filled-*.png: 3페이지에서 설정왕복과 닉네임수정.
- menu-final 및 password-final/withdrawal: 키보드/대화상자/모바일 화면.

수동: 로그인→내댓글 더보기2회→하단행 위치 확인→프로필·계정 설정→닉네임 저장→마이페이지로 돌아가기 또는 뒤로가기→같은탭27개/위치/새닉네임 확인. 설정 새로고침·로그아웃 시 개인정보가 남지 않는지 확인. 비밀번호/탈퇴는 실제 사용자로 실행하지 말고 모의 테스트를 사용하세요.

## 미실행 및 한계
- 실제 Spring 연동과 실회원 비밀번호 변경/탈퇴: 미실행(요청대로 mock만 사용).
- 실물 iPhone Safari/소프트키보드, 실제 화면낭독기: 미검증. Chrome viewport/키보드/접근성 속성 검사를 실기기 검증으로 간주하지 않습니다.
- 별도 member-404-race/member-comments-fix 전체검사는 이번에 미실행(활동 준비 selector만 새 링크에 맞춤). 기존 no-JS 404, 댓글 오류표시 및 로그아웃실패 스크롤 간헐 이슈를 해결했다고 주장하지 않습니다. 이번 내댓글의 JS 삭제원문 왕복4개는 통과했습니다.
- 내 활동 전면디자인, 댓글 펼치기/작성, 수정삭제메뉴, 새API는 다음 단계입니다.
- 헤더·댓글의 브랜드 색상은 유지. 이메일 인증 완료 표시는 이전 정책 그대로이며 새 응답필드를 추정하지 않았습니다.

## 패키지
최신 web/src/tests/public/patches, 명세/잠금/설정, AGENTS, 제품문서, 필요한 backend 참조 및 이번 검증자료 포함. 시작상태가 clean이므로 HEAD 대비와 이번작업 diff가 같습니다(신규 route 포함). 새파일 실제내용 및 목록 포함.
.env, 실제 계정/비밀번호/쿠키/토큰/개인정보, DB dump, .git/node_modules/.next/build/cache, 과거 ZIP 및 무관한 과거 검증자료 제외. 테스트의 example.test 및 synthetic 문자열은 가상fixture이며 실제 인증정보가 아닙니다. 원본 파일의 민감정보를 변경하지 않았습니다.
