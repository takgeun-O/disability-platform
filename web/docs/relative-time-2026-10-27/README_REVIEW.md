# 공통 작성 상대 시간 — 검토 보고

## 기준과 기존 작업 보존
브랜치 feat/mypage-activity-redesign / HEAD 416fabd176918ef32d2cb33798c603024ad57b02.
마이페이지 1·2단계, 내 댓글 liked/스크롤/인증 경합 처리와 PostEdit busy 보완은 시작 시 미커밋 상태였다. 모두 유지했다. 특히 PostEdit.tsx는 시작 스냅샷과 바이트 단위로 동일하다. 백엔드/DB/API 계약 변경, 커밋·푸시·병합·배포 없음. 무관한 디자인 자료도 건드리지 않았다.

## 변경 파일 / 적용 화면
- web/src/lib/written-time.ts: 기준 now를 주입하는 순수 포맷 함수. 원문 문자열은 수정하지 않는다. 시간대 없는/유효하지 않은 문자열과 불가능한 달력 날짜는 안전하게 거부한다.
- web/src/lib/written-clock.ts: 공통 구독 시계. 항목 수에 관계없이 브라우저 모듈의 타이머1개. 마지막 구독 해제 때 타이머/이벤트 리스너 정리.
- web/src/components/WrittenTime.tsx, web/src/styles/written-time.css: time/dateTime 원문, 상대 시간, 정확한 작성 시각 팝오버. 현재 디자인 토큰/글자 크기 계승.
- PostList.tsx, PostDetail.tsx, Comments.tsx, MyActivity.tsx, MyComments.tsx: 작성일5곳을 공통 컴포넌트로 연결.
- web/tests/written-time.test.mjs: 경계/날짜/미래/유효성/공통 시계22개.
- web/tests/written-time-browser.mjs: 5개 화면 일관성, 모바일/키보드/터치, 시간 전환, 가시성 복귀, 무JS 검사6개.

가입일 joinedMonth와 MyPage의 가입 time, 정보 콘텐츠 기준일/DeadlinePanel, 홈·검색의 정적 예시 날짜는 변경하지 않았다. 기존 postDate 함수는 다른 용도로 사용할 수 있는 기존 API로 남겼으며 이번5곳에서만 대체했다. updatedAt으로 작성일을 바꾸지 않았고 댓글의 기존 수정됨 표시는 유지했다.

## 표시 정책
실제 경과 시간을 내림한다: 1분 미만 방금 전 / 1시간 미만 N분 전 / 24시간 미만 N시간 전 / 7일 미만 N일 전 / 7일 이상 YYYY.MM.DD.
24시간은 자정 경계가 아니다. 정확한 60분·24시간·7일 경계 검증. 미래1분 이내는 방금 전, 그 이상은 한국 기준 날짜+HH:mm:ss를 표시한다. 잘못된 입력은 작성일 확인 불가. 기준 시계값이 잘못되면 안전한 절대 날짜로 돌아간다.
Asia/Seoul을 명시하고 날짜 부분을 조합하므로 기기 시간대와 상관없이 같은 YYYY.MM.DD.를 표시한다. Date.parse가 버리는 소수 밀리초는 상대 경계 계산에서만 보정한다. 원본 createdAt/updatedAt, dateTime 및 커서의 마이크로초 문자열을 그대로 보존한다.

## SSR / hydration / 갱신
useSyncExternalStore의 getServerSnapshot은 항상 null이다. 서버 HTML과 첫 hydration은 같은 절대 날짜를 표시한다. 구독이 시작되면 현재 시각으로 상대 표시한다. suppressHydrationWarning 없음. 무JS에서 본문/절대 날짜를 볼 수 있고 네이티브 팝오버도 사용할 수 있다.
화면이 보일 때 60초 간격으로 갱신하고 hidden이면 타이머를 중단한다. visibilitychange 또는 window focus에서 현재 시간을 즉시 반영한다. 마지막 구독이 사라지면 리스너와 타이머를 모두 정리하며 Strict Mode 재구독도 검사했다. API 요청/원본 데이터/커서/부모 목록 상태와 무관한 시계라 갱신을 위해 API를 호출하거나 폼을 재마운트하지 않는다. 간격 내 최대1분의 표시 지연은 분 단위 갱신 정책에 따른다.

## 정확한 시각 / 접근성
점선 밑줄 날짜는 type=button으로 키보드 Enter/Space와 터치로 조작한다. HTML popover=auto / popoverTarget을 사용하며 기존 공통 팝오버 구현은 없었다. 작은 팝오버에 한국 시간의 정확한 날짜·시·분·초를 표시한다. Escape/바깥 선택/버튼 재선택으로 닫는다. time의 dateTime은 API 원문이다. aria-describedby로 정확한 시각을 제공하고 aria-live를 추가하지 않는다. 항목 링크/좋아요 버튼 안에 중첩하지 않는다. 팝오버는 top layer라 목록 흐름을 차지하지 않으며 320px에서도 화면 폭 안에 표시한다.
참조: [MDN Popover](https://developer.mozilla.org/en-US/docs/Web/API/Popover_API/Using), 설치된 Next16.3.6 use-client 문서. 실제 화면낭독기/실물 스마트폰의 검증은 하지 않았다.

## 실행 환경 / 명령
Node24.21.0, pnpm10.34.3. web에서 pnpm test / pnpm lint / pnpm typecheck 실행.
최종 web/src와 동일한 /private/tmp/iyum-relative-build에서:
```
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8466
IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm exec next start --hostname 127.0.0.1 --port 8467
```
모든 브라우저 검사는 production Next + 모의 API, 실제 Spring/DB는 사용하지 않았다. PLAYWRIGHT_MODULE은 설치된 Playwright 경로로 지정(이번 실행: /Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs), Chrome headless 사용.
```
BASE_URL=http://127.0.0.1:8466 TIME_ARTIFACT_DIR=<out> node tests/written-time-browser.mjs
BASE_URL=http://127.0.0.1:8466 MEMBER_ARTIFACT_DIR=<out> node tests/member-posts-browser.mjs
BASE_URL=http://127.0.0.1:8466 MEMBER_ARTIFACT_DIR=<out> node tests/member-comments-browser.mjs
BASE_URL=http://127.0.0.1:8466 LIKE_ARTIFACT_DIR=<out> node tests/post-likes-browser.mjs
BASE_URL=http://127.0.0.1:8466 COMMUNITY_SSR_MOCK_PORT=8858 COMMUNITY_ARTIFACT_DIR=<out> node tests/post-edit-busy-browser.mjs
BASE_URL=http://127.0.0.1:8467 COMMUNITY_ARTIFACT_DIR=<out> node tests/community-feed-browser.mjs
BASE_URL=http://127.0.0.1:8467 COMMUNITY_ARTIFACT_DIR=<out> node tests/comment-likes-browser.mjs
BASE_URL=http://127.0.0.1:8467 COMMUNITY_SSR_MOCK_PORT=8856 COMMUNITY_ARTIFACT_DIR=<out> node tests/community-edit-delete-browser.mjs
```
같은 mock 포트의 스크립트는 순차 실행한다. *.log와 결과JSON은 이번 실행 자료이다.

## 검증 결과
- 단위403/403 (신규22), 실패/skip 0.
- lint 경고 없이 통과, typecheck 통과, production build 통과, git diff --check 통과.
- 새 브라우저6/6: 1440/390/320에서5개표시화면·정확시각·접근성, 320미래긴날짜, Playwright 시계로 방금전→1분전/6일전→절대날짜, 제어된 visibility복귀, API 호출 수/초안/DOM/초점/스크롤 유지, hydration오류없음, 무JS본문과댓글링크.

| 실행 폴더 | 결과 |
|---|---|
| time-final | 완료 6, 통과 6, 실패 0 |
| member-posts | 완료 74, 통과 73, 실패 1 |
| member-comments | 완료 67, 통과 67, 실패 0 |
| post-likes | 완료 29, 통과 29, 실패 0 |
| edit-busy | 완료 8, 통과 8, 실패 0 |
| feed | 완료 28, 통과 26, 실패 2 |
| comment-likes | 완료 25, 통과 25, 실패 0 |
| edit-delete | 완료 4, 통과 4, 실패 1 |
| feed-isolated | 완료 28, 통과 26, 실패 2 |
| edit-delete-isolated | 완료 30, 통과 30, 실패 0 |
| member-posts-isolated | 완료 74, 통과 74, 실패 0 |
| feed-font-css | 완료 28, 통과 28, 실패 0 |

### 실패 및 재실행 구분
첫 회귀 실행 중 피드2개, 내 게시글1개에서 페이지 goto의 load 완료가 기존 제한을 넘겼다. 상세 수정/삭제도5번째 시나리오의 goto에서 중단(4개 완료, 이후 미실행). assertion이나 timeout을 변경하지 않았다.
단독 진단 실행에서는 피드에서 외부 폰트 관련 스크린샷 대기와 goto 시간 초과가 기록됐다. navigation-diagnostics.jsonl의 미완료 요청은 https://fonts.googleapis.com/css2 stylesheet였다. 모든 최초 실패의 개별 원인을 확정한 것은 아니며, 진단으로 확인한 외부 폰트 요청 지연과 구분한다. 단독 실행의 수정/삭제는30개 통과했다.
폰트 자체를 없애거나 다른 폰트로 대체하지 않고 동일 Google Fonts CSS URL을 사전에 다운로드해 공급하는 검증 전용 wrapper로 피드 전체를 다시 실행했다(feed-font-css). 폰트 파일 URL·제품 CSS·테스트 assertion·대기시간은 그대로다. 진단 wrapper와 CSS를 포함했다. 다음 환경으로 재현:
```
PLAYWRIGHT_MODULE=<이 보고서 폴더>/browser-diagnostics.mjs
PLAYWRIGHT_REAL_MODULE=<설치된 Playwright 경로>
BROWSER_DIAGNOSTIC_LOG=<out>/navigation-diagnostics.jsonl
BROWSER_FONT_CSS=<이 보고서 폴더>/google-fonts.css
```
기존 실패 결과를 마지막 성공으로 덮어쓰지 않았다. 위 표에서 최초·단독·CSS사전공급 실행을 각각 확인해야 한다. 최종 확인 기준으로 새 시간6 + 피드28 + 내 게시글74 + 내 댓글67 + 댓글 좋아요25 + 게시글 좋아요29 + 수정/삭제30 + busy회귀8 = 267개 통과다. 피드28개는 CSS 사전공급 조건이며 외부 폰트 네트워크의 안정성까지 검증했다는 뜻은 아니다. 화면날짜 기대값이나 위치 허용오차를 완화하지 않았다.

## 수동 확인 / 제한
1. 커뮤니티/상세/내 활동에서 날짜를 확인하고 키보드 Enter 또는 터치로 한국 시간 팝오버를 연다.
2. 무JS 직접 접근은 상대 시간이 아닌 절대 날짜와 본문이 보여야 한다.
3. 상세의 댓글 초안을 유지한 상태에서 시간 경계를 넘기거나 탭에서 돌아와 표시만 갱신되는지 확인한다.
4. 내 활동3페이지→상세→복귀 및 좋아요/댓글 링크/수정 경합은 기존 회귀 결과 참조.
실제 Spring API, 실기기 Safari·소프트 키보드·화면낭독기 검증 미실행. 화면폭은 브라우저 에뮬레이션이고 백그라운드 전환은 제어된 visibility 이벤트이다. 별도 구형 전체 community-browser/topics-browser 이식, 무JS404 재설계, 댓글CRUD 전체의 알려진 간헐 실패는 이번에 재검증·해결한 것으로 표시하지 않는다. 최신 브라우저의 네이티브 Popover 지원을 전제로 하며 구형 브라우저 전체 호환성은 검증하지 않았다.
직접 실행한8466/8467만 정리하고 사용자8443개발서버는 보존한다.

## 패키지
최신 web/src/tests/public/설정/patches/package.json/lockfile/AGENTS와 이 보고서·로그·캡처. review/this-task.patch는 시작 스냅샷 대비 이번 변경, HEAD-to-current.patch는 기존 미커밋 구현을 포함한 누적 변경이다. 기존 상태 patch와 전후status로 구분한다. 신규 파일 실제 내용 포함. .env/실제 인증정보/DB/.git/node_modules/.next/빌드/이전ZIP/무관한 이미지 제외. 모의 example.test/test-only는 실제 계정이나 토큰이 아니다. 소스와 ZIP의 바이트 일치/압축무결성 검증.
