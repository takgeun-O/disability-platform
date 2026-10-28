# 마이페이지 활동 리팩토링 2단계 검토

## 기준과 범위
브랜치 feat/mypage-activity-redesign, HEAD 416fabd176918ef32d2cb33798c603024ad57b02.
시작 당시 1단계 설정 페이지 분리·중앙 정렬과 관련 테스트 변경이 미커밋 상태였다. 이를 유지했다. 별도 디자인 이미지 IYUM_report_management_dashboard.png는 수정하지 않았으며 패키지에서도 제외한다. 백엔드·DB 변경, 커밋·푸시·병합·배포 없음.

## 이번 파일과 처리
- web/src/components/member/MemberPostActions.tsx (신규): 작성자 전용 관리 메뉴, 기존 CommunityDialog/changePost를 통한 삭제. 인증 요청 세대와 화면 수명을 별도로 확인한다. 한 번에 하나의 메뉴, 방향키/Home/End/Escape, 외부 클릭, 초점 복귀, 44px 진입 버튼.
- web/src/components/member/MyActivity.tsx: 관리 메뉴 연결, 삭제 후 다음/이전 항목 또는 빈 안내로 초점 이동. 사용자가 다른 곳으로 초점을 옮겼으면 빼앗지 않는다. 기존 누적 범위 재조회·커서·앵커 복원을 사용한다.
- web/src/features/PostEdit.tsx: 검증된 from=/mypage 및 activity=1로 직접 수정 진입을 구분한다. 기존 상세 출발 경로는 그대로 상세로 돌아간다. 실제 전체 본문 조회와 기존 폼/이탈 확인을 재사용한다. 인증 세대와 탐색 수명 검사로 오래된 결과의 UI 변경을 방지한다.
- web/src/styles/mypage.css: 활동 행의 여백/구분선, 제목과 2줄 미리보기, 우측 관리 메뉴. 내 댓글의 중첩 박스를 줄이고 본문·원문 맥락·오른쪽 읽기 전용 하트를 유지한다. 1단계 중앙 너비/설정 화면 유지.
- web/tests/member-posts-browser.mjs: 관리·삭제·수정 경합 31개 추가, 기존 재시도 버튼 대기를 새 관리 버튼과 구분하도록 정확한 선택자로 변경. 기존 검증 삭제 없음.

## 기존 처리 재사용 / 계약
목록은 기존 contentPreview를 사용하며 각 게시글 상세를 추가 호출하지 않는다. 수정에서만 기존 상세 조회를 사용한다. DELETE는 기존 changePost와 CSRF/오류 분류를 사용한다. 성공 확인 후 postChanged()로 게시글·연관 내 댓글·활동 요약을 서버 기준으로 갱신한다. 개수 추측이나 자동 쓰기 재전송은 없다.
확정적인 403은 오류/재시도를 제공한다. POST_NOT_FOUND는 재조회 대상으로 표시하되 삭제 성공으로 단정하지 않는다. 일반 404/네트워크/5xx 등 결과 불확실 상태는 기존 계약에 따라 안내하고 목록 재확인을 제공한다. 현재 401은 인증 만료 처리한다. 같은 인증의 늦은 성공/불확실 응답은 데이터를 무효화할 수 있지만 떠난 화면의 모달·초점은 변경하지 않는다. 로그아웃/재로그인/다른 계정의 이전 응답은 무시한다.
기존 PostController, PostDeleteService, PostUpdateService 및 MemberPostReadService 계약으로 이번 범위 구현 가능. 새 API나 전체 본문 확장 기능을 만들지 않았다. 내 댓글 관리/댓글 펼치기는 구현하지 않았다.

## 최종 검증 (이번에 직접 실행)
Node 24.21.0, pnpm 10.34.3. 아래 기본 명령은 web 기준:
- pnpm lint: 통과
- pnpm typecheck: 통과
- pnpm test: 381/381, 실패/skip 0
- pnpm build: 통과 (동일 최종 소스의 격리 디렉터리 /private/tmp/iyum-phase2-final-build)
- git diff --check: 통과

프로덕션 Next 8466 + 제어 가능한 모의 API. 브라우저는 Chrome headless이며 실제 Spring/DB 요청 검증이 아니다. 브라우저 API는 8082 URL을 가로채고 서버 공개 조회는 전용 mock 8858을 사용한다. 아래 스크립트를 순차 실행했다.

| 명령 | 결과 |
|---|---|
| node tests/member-posts-browser.mjs | 74/74 |
| node tests/member-comments-browser.mjs | 67/67 |
| node tests/community-edit-delete-browser.mjs | 30/30 |
| node tests/member-browser.mjs | 25/25 |
| node tests/password-change-browser.mjs | 32/32 |
| node tests/withdrawal-browser.mjs | 11/11 |

총 239개 브라우저 검사 통과. 각 폴더 결과 JSON 및 로그 참조. PC1440/390/320 목록·메뉴·삭제 확인·긴 문자열/큰 숫자·빈 상태, 키보드, 3페이지 범위와 위치, 첫/후속/마지막 게시글 삭제, 관련 댓글 갱신, 오류/CSRF/불확실 응답, 중복 방지, 이탈/인증 변경, 수정 취소/실패/성공, 기존 상세 수정, 설정/비밀번호/탈퇴 주요 회귀 포함.

### 실행 환경 재현
빌드: NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
서버: IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8466
브라우저 공통: BASE_URL=http://127.0.0.1:8466 PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs
다른 환경에서는 PLAYWRIGHT_MODULE을 설치된 playwright 모듈 경로로 지정한다. Chrome 필요.
결과 경로: MEMBER_ARTIFACT_DIR / COMMUNITY_ARTIFACT_DIR / PASSWORD_CHANGE_ARTIFACT_DIR / WITHDRAWAL_ARTIFACT_DIR는 각 스크립트 상단 참조. 상세 수정 검사에는 COMMUNITY_SSR_MOCK_PORT=8858 사용. mock 포트 충돌을 피하기 위해 순차 실행한다.

## 중간 실패와 최종 결과의 구분
- 최초 lint: 렌더 중 ref 할당 지적 → layout effect로 수명 관리 수정 후 통과.
- 초기 내 게시글: 64/66. 기존 broad first-button 대기가 새 관리 버튼을 선택 → 기존 재시도 버튼을 정확히 지정하고 assertion 유지.
- 중간 내 게시글: 71/74. 새 다른 계정 fixture의 author ID가 이전 회원으로 잘못 지정되어 응답 검사에서 거부 → 새 계정 소유 ID와 게시글로 fixture 수정. 최종 전체 74/74.
- 첫 내 댓글 실행은 빌드 환경 전환 중 중단된 부분 기록이며 전체 결과로 취급하지 않음. 최종 전체 67/67.
- 격리 빌드 첫 시도에서 외부 node_modules 심볼릭 링크가 Turbopack 경계에 걸림. 같은 설치 의존성을 격리 폴더에 복사하여 빌드 완료. 제품 설정/의존성 변경 없음.
기존 기록은 삭제하지 않고 earlier-runs로 보존했다.

## 수동 확인
1. 마이페이지에서 3페이지까지 더 보기 → 후속 항목 관리 메뉴를 키보드로 열고 닫기.
2. 수정 진입 → 원문 전체 입력/변경 → 취소 시 계속 수정/나가기 → 성공 후 마이페이지 범위·앵커 확인.
3. 삭제 메뉴 → 제목 확인 → 취소/최종 삭제 → 서버 갱신된 목록/개수·이웃 항목 초점 확인.
4. 삭제 실패/불확실 응답은 행 유지·안내·재확인 동작 확인. 내 댓글 원문/하트, 설정 왕복을 확인.

## 제한 / 미실행
실제 Spring 연동, 실제 회원 데이터 수정/삭제, 실제 Safari·소프트 키보드·화면낭독기는 실행하지 않았다. Chrome 모바일 viewport와 접근성/키보드 검사로 대체한 범위만 보고한다. 기존 별도 무JS 404 경합 재설계와 댓글 CRUD의 조회 오류/로그아웃 실패 스크롤 간헐 문제는 이번 범위 밖이며 해결했다고 주장하지 않는다. 이번 관련 최종 실행에서는 남은 실패 없음.
직접 시작한 8465/8466 검증 서버를 종료했으며 사용자 개발 서버8443(PID10098)는 유지했다.

## 패키지
원래 상대 경로로 최신 web/src/tests/public/필요 설정/lockfile, 관련 백엔드 참조 코드와 이 보고서·캡처·최종 로그를 포함한다. HEAD 누적 patch와 작업 시작 스냅샷 대비 phase2 patch를 별도로 제공한다. 전후 git status와 미추적 목록에 기존 1단계 변경이 표시된다. 실제 .env/인증정보/DB/빌드/의존성/기존 ZIP/무관한 이미지 제외. 테스트 안의 example.test 및 test-only 문자열은 모의 fixture이며 실제 비밀값이 아니다.
