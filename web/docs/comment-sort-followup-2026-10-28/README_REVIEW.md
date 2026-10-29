# 댓글 정렬 후속 검토 (2026-10-28)

## 기준과 변경 구분
- 브랜치: feat/mypage-activity-redesign
- HEAD 및 백엔드 기준: 5745419bca8d402b3b9f0656a96db73dab1ef798
- 기존 미커밋 댓글 서버 정렬, 인라인 정렬, 전환 애니메이션 및 문서를 보존했다. 이번에는 제품 소스 Comments.tsx 한 파일과 브라우저 테스트 두 파일만 보완했다. 별도 보고서를 추가했다.
- 작업 시작 스냅샷의 src/tests와 현재 파일을 비교한 patch, 시작 당시 diff/status, 기준 커밋 대비 누적 patch를 ZIP review/에 제공한다. 신규 소스의 실제 내용도 포함한다.
- 백엔드/DB/API 및 디자인 변경 없음. 커밋/푸시/병합/배포 없음.

## 원인과 변경
1. inline-comments-browser.mjs: 유지 중인 20개는 새 응답 완료의 증거가 아니다. 최종 gate를 해제한 뒤 post1000 영역의 aria-busy=false와 인기순 첫 ID100002를 함께 기다리고, 서버 mock 데이터로 계산한 첫 페이지 전체 ID 순서를 비교한다. 이전 응답 도착 시 로딩과 이전 목록 유지 assertion은 보존했다. 다른 정렬 검사의 첫 ID/버튼 상태 대기도 검토했다.
2. Comments.tsx: 상세 GET은 이전에 ticket만 증가시키고 네트워크 조회는 계속했다. 이제 load 작업별 AbortController를 만들고 최초/추가/누적 범위 조회 모두 같은 signal을 전달한다. cancelRead는 ticket을 증가시키고 controller를 취소하며 소유권·buffer를 정리한다. 정렬 변경, 인증 요청 컨텍스트 변경, 공개 신원 갱신, 댓글 쓰기 시작, unmount 및 승인된 NavigationGuard 이동에 연결한다. 이탈 취소는 subscribeNavigation이 발행되지 않아 조회를 유지한다.
3. API request는 AbortError도 CommentError로 변환한다. 따라서 이름 검사 대신 controller.signal.aborted와 controller 동일성, ticket, 인증, 화면 scope, 공유 좋아요 read 유효성을 함께 검사한다. 실제 timeout/네트워크 오류는 소유 controller가 취소되지 않은 경우 기존 오류 경로를 따른다. finally는 자신이 소유한 ticket/controller일 때만 진행 상태를 정리한다. 다른 요청의 controller나 loading을 건드리지 않는다. 변경 POST/PUT/DELETE/좋아요 요청에는 이 controller를 전달하지 않는다.
4. community-comments-browser.mjs: 이탈 후 응답을 기다리던 검사를 취소 기능에 맞췄다. 응답이 더 이상 도착하지 않는 GET의 requestfailed 이벤트를 승인된 이탈 전에 등록해 실제 취소 완료를 확인하고, 목적지 화면에 성공 토스트가 없음을 검사한다. 취소를 무시하는 늦은 응답 방어는 inline-comments-browser의 별도 상세 화면 테스트에서 확인한다.

## 새 회귀 검증
- native abort / transport ignores abort 두 경우를 각각 검증: 최신순 gate A→인기순 gate B, A 취소 확인, A의 늦은503 이후에도 B 로딩/오류/목록 소유권 보존, B 최신 결과 적용.
- 실제503 후 기존 목록/작성 초안 유지 및 명시적 재시도 성공.
- abort를 무시하는 전송에서도 로그아웃 이후 이전 GET의 liked=true/count99가 현재 공유 좋아요 표시를 덮어쓰지 않음.
- 45개 누적 조회 후 재조회 두 번째 페이지를 보류하고 정렬 변경. 이전 응답을 전달해도 세 번째 페이지를 요청하지 않고 새 정렬 로딩을 유지.
- dirty 이탈 취소 시 signal 유지, 승인 시 abort 발생.
- 기존 인라인 정렬/커서/애니메이션/모바일/인증 격리 검사 유지. 고정 sleep 및 허용 오차 확대 없음.

## 실행 방법과 최종 결과
Node24.21.0, pnpm10.34.3. 아래 명령은 web/ 기준이다.
- pnpm test: 415/415 통과.
- pnpm lint: 통과 (최종 테스트 변경 후 실행).
- pnpm typecheck: 통과.
- pnpm build: 통과. 사용자 .next와8443 서버를 보존하기 위해 /private/tmp/iyum-inline-build에 동일한 최종 src를 복사해 프로덕션 빌드. 공개 API http://127.0.0.1:8082는 브라우저 route mock, 내부 API는 독립 mock8858/8856 사용.
- inline-comments-browser.mjs: 최종43/43 통과. 실제 취소 및 취소를 무시한 늦은 응답 조건 포함.
- community-comments-browser.mjs: 최종61/62 통과. 아래 알려진 logout failure 스크롤 assertion 1건 실패. 이탈 GET 취소 및320px 버튼 정렬은 최종 실행에서 통과. 모든 실행 JSON/log는 review/test-summary.json 및 evidence/ 참조.
- git diff --check: 통과.
브라우저 실행 스크립트와 환경 경로는 review/run-followup-browser-local.sh에 있다. 외부 환경에서는 PLAYWRIGHT_MODULE을 설치된 Playwright 경로로 바꾸고 별도 프로덕션 Next를 실행한다. 두 테스트를 같은 API 포트로 동시에 실행하지 않는다. 스크립트 자체에 테스트 필터를 남기지 않았다.

## 중간 결과와 알려진 제한
- 인라인 초기43/43, 인증 성공 응답 방어 추가 전43/43, 추가 후 최종43/43. 각각의 JSON/log를 구분해 보존한다.
- 상세 첫 실행60/62: 기존 logout scroll 실패 및 취소된 응답을 기다리던 검사의 timeout. 두 번째60/62: 보강한 검사에서 목적지 문서 전환 뒤 window 상태를 읽어 undefined. 이후 목적지 gate 방식 두 번은 대기 정체로 직접 중단했고, 최종적으로 실제 requestfailed를 기다리는 검사로 정리했다. 중간 실패/중단 로그도 삭제하지 않았다. 중단한 네 번째 실행에서는320px 버튼 세로 위치 assertion 실패도 관찰되어 기록을 보존했다. CSS/UI를 변경하지 않았으며 원인은 이번 범위에서 확정하지 않았다.
- 알려진 logout failure: detail URL/body, comment drafts, focus and dirty protection remain 시나리오는 스크롤 차이<4 assertion에서 실패한다. 앞선 URL/본문/초안/포커스 검증은 수행됨. 이후 로그아웃 요청 횟수/댓글 POST 미발생 및 홈 이동 시 dirty 확인 assertion은 이 실패 분기에서 실행되지 않았다. 해당 assertion을 삭제하거나 허용 오차를 바꾸지 않았다.
- 이전 animation ZIP의415/40/61 결과는 과거 기록이며 이번 결과로 합산하지 않는다.
- 실제 Spring, 실제 계정/DB 변경, 실기기 Safari, 화면낭독기는 미실행. 모의 API + 프로덕션 Next + Chromium만 검증. 서버 DB 작업까지 취소된다고 주장하지 않는다.
- 전용 timeout 경과와 모든 페이지 해제 조합을 개별 브라우저 시나리오로 전수 검사하지는 않았다. 같은 취소 범위/소유권 경로를 공유하며 기존 네트워크 오류 검사 및 누적 조회 취소 검사를 수행했다.

## 수동 확인
상세 댓글에 초안을 입력한 뒤 최신순→인기순을 빠르게 선택한다. 이전 목록이 보이는 동안 새로고침 중 표시가 유지되고 최종 인기순만 반영되어야 한다. 여러 페이지를 불러온 뒤 새로고침 중 정렬을 바꿔도 이전 요청이 후속 페이지를 읽지 않아야 한다. 이동 확인에서 계속 작성을 선택하면 요청이 계속되고, 나가기를 선택하면 취소된다. 네트워크 실패 시 초안/기존 목록 및 재시도 경로가 유지되어야 한다.
