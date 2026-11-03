# 관리자 프론트 1단계 검토 보고서

## 기준과 범위
- 브랜치: feat/admin-content-moderation
- 시작 HEAD: 71969fc67be13d40bc3c80ad75d7273c9cc33646
- 시작 작업 트리는 깨끗했습니다. 이번 diff 전체가 이번 작업이며 기존 미커밋 변경은 없었습니다.
- 백엔드·DB·API·기존 마이페이지 구현은 변경하지 않았습니다. 커밋·푸시·병합·배포하지 않았습니다.

## 구조와 구현
기존 App Router와 루트 AuthProvider/NavigationGuard/Header/Footer를 유지했습니다. 로그인과 현재 회원 응답의 role은 이미 USER/ADMIN으로 검증되고 있었습니다. AuthControls에 확인된 ADMIN 전용 진입점을 추가했습니다. 모바일 메뉴도 같은 AuthControls를 재사용합니다.

/admin은 /admin/posts로 연결됩니다. 관리자 레이아웃에는 게시글 관리와 서비스 복귀만 있습니다. checking/pending/paused 상태에는 개인 목록을 렌더링하지 않으며, 비로그인에는 from=/admin/posts 로그인 링크, USER에는 권한 없음 안내를 표시합니다. 확인 실패는 인증 재확인을 제공합니다.

AdminAccess는 회원 ID와 인증 revision에 따라 목록 인스턴스를 교체합니다. AdminPosts 요청마다 기존 captureMemberRequest와 navigation scope, AbortController를 함께 사용합니다. 조건 변경·인증 변경·이탈 이후 응답은 적용하지 않습니다. 401은 session.expire(), 403은 목록을 지우고 권한 없음 안내를 표시하며 로그아웃시키지 않습니다. 쿠키 이외 별도 인증 저장소나 개인정보 영구 캐시는 없습니다.

입력 검색어와 적용 조건을 구분하고 Enter/검색 실행 시만 조회합니다. 동일 조건 실행은 추가 요청을 만들지 않습니다. 상태·검색·초기화는 page=0으로 이동합니다. 이전/다음과 전체 개수는 서버 페이지 정보를 사용합니다. 정상·숨김·삭제 제목을 그대로 표시하고 상세 링크나 미구현 조작은 만들지 않았습니다. 작성 시각은 WrittenTime으로 상대 시간과 키보드/터치 가능한 정확한 시각을 재사용합니다.

PC는 네이비/청록색의 사이드바와 흰색 목록 패널입니다. 모바일은 메뉴를 위로 배치하고 표 내부만 가로 스크롤합니다. 1440/390/320px에서 문서 가로 넘침 없음과 검색 후 포커스 유지를 검사하고 캡처했습니다.

## 확인한 API 계약
- AdminPostReadController / AdminPostReadQuery / AdminPostItemResponse / AdminPostListResponse / AdminPostAuthorResponse를 확인했습니다.
- GET /api/v1/admin/posts, status=ALL|VISIBLE|HIDDEN|DELETED, page=0, size=20. size 허용 1..50.
- keyword는 Java String.strip 방식 정규화, 최대 100 Unicode code point. 빈 값은 생략합니다. NBSP를 임의로 제거하지 않습니다.
- items의 postId/type/topic/title/contentPreview/author(memberId,nickname,withdrawn)/status/createdAt/updatedAt/hiddenAt/deletedAt 및 page/size/totalElements/totalPages/hasNext/hasPrevious를 검증합니다.
- 탈퇴 작성자 이름은 서버 nickname을 사용합니다. GET은 인증 쿠키와 no-store를 사용하며 CSRF·mutation·자동 재전송을 추가하지 않습니다.
- docs/api/admin-comment-read.md도 확인했으나 게시글 목록 전용 문서는 없어서 실제 컨트롤러·조회 조건·DTO를 우선 근거로 삼았습니다. 필요한 계약 누락은 발견하지 않았습니다.

## 변경 파일 역할
- src/components/AuthControls.tsx: ADMIN 진입 링크.
- src/app/admin/{layout.tsx,page.tsx,posts/page.tsx}: 공통 관리자 영역과 라우트.
- src/components/admin/AdminAccess.tsx: 인증·권한 진입 제어와 인증 세대별 화면 격리.
- src/features/AdminPosts.tsx: 검색·필터·페이지 및 요청 소유권, 목록 상태.
- src/lib/admin-posts.ts: 읽기 전용 API, 요청 정규화, DTO 검증 및 안전한 오류 문구.
- src/styles/admin.css: 관리자 전용 반응형 레이아웃과 배지.
- tests/admin-posts.test.mjs: 계약/검색 정규화/전송/오류 단위 테스트 4개.
- tests/admin-posts-browser.mjs: 접근 제어·조회·경합·레이아웃 모의 브라우저 검사 10개.

## 최종 직접 실행 결과
Node 24, pnpm 10.34.3. 기존 사용자 서버 8443은 변경하지 않았습니다. 빌드와 타입 생성은 소스 복사본 /private/tmp/iyum-admin-web에서 실행하고, 프로덕션 서버는 8479를 사용했습니다.

| 명령 | 결과 |
|---|---|
| pnpm lint | 통과 |
| pnpm test | 426/426, skip 0 |
| NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 pnpm build | 통과 |
| pnpm typecheck | 통과 |
| node tests/admin-posts-browser.mjs | 10/10 |
| node tests/member-browser.mjs | 25/25 |
| node tests/member-menu-browser.mjs | 23/23 |
| git diff --check | 통과 |

브라우저 실행에는 BASE_URL=http://127.0.0.1:8479, PLAYWRIGHT_MODULE=(로컬 Playwright index.mjs), 각각 ADMIN_ARTIFACT_DIR/MEMBER_ARTIFACT_DIR를 지정했습니다. Chromium channel=chrome, 모의 API 사용. 실제 Spring/DB 및 실계정 검증은 미실행입니다.

관리자 경합 검사는 응답 gate를 사용하고 전송 계층이 abort를 무시하도록 하여 이전 응답을 실제로 전달했습니다. 최신 필터 결과, 로그아웃, 같은 회원 재로그인, 다른 USER 로그인 후 이전 응답이 목록/인증을 복구하거나 추가 GET을 만들지 않는지 확인했습니다. 응답 완료 후 렌더 프레임과 최종 UI를 확인했습니다. 403 후 로그인 상태 유지와 401 후 개인 데이터 제거도 확인했습니다.

초기 검증 이력도 최종 통과와 구분합니다. 처음에는 격리 빌드의 외부 node_modules 심볼릭 링크가 Turbopack에 거절되어 복사본으로 바꿨습니다. TS 대상에서 Object.hasOwn이 지원되지 않아 hasOwnProperty로 수정했습니다. 브라우저 최초 실행은 상태 레이블 exact 탐색 3건이 실패하여 명시적 label 연결로 수정했습니다. 재빌드 시 공개 API 주소 누락으로 대기 실패한 실행은 중단하고 주소를 명시해 재실행했습니다. 마지막 결과만 위 표에 기록했으며 이전 실패 로그는 별도 포함했습니다.

## 수동 확인 및 후속 단계
1. ADMIN으로 로그인하여 헤더/모바일 메뉴 관리자 진입, 또는 /admin/posts 직접 접근.
2. 상태, 제목 검색, Enter, 초기화, 이전/다음, 정확한 작성 시각 확인.
3. USER/비로그인 직접 접근과 세션 만료/권한 없음 응답 확인.
4. 좁은 화면에서는 표를 좌우로 스크롤하여 모든 열 확인.

이번 단계의 남은 코드 실패는 없습니다. 실제 Spring 관리자 세션/CORS 연동, 실기기 Safari·화면낭독기는 미검증입니다. 브라우저 회귀는 이번 영향 범위만 실행했으며 다른 댓글/좋아요 전체 테스트를 실행했다고 주장하지 않습니다. 다음 단계는 별도 관리자 상세와 숨김/복원 UI·권한/오류 계약 검토이며 이번에는 제공하지 않습니다.
