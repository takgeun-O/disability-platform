# 게시글 수정 busy 회귀 보완

## 기준 / 기존 변경 구분
- 브랜치: feat/mypage-activity-redesign
- HEAD: 416fabd176918ef32d2cb33798c603024ad57b02
- 작업 시작 시 마이페이지 1·2단계 및 관련 테스트가 미커밋 상태였다. 모두 유지했다.
- 이번 제품 변경은 web/src/features/PostEdit.tsx 한 파일이다. web/tests/post-edit-busy-browser.mjs를 새로 추가했다. 이 보고서와 실행 자료를 추가했다.
- backend/API/DB/디자인/상대 시간/댓글 펼치기 변경 없음. 커밋·푸시·병합·배포 없음. 무관한 디자인 이미지는 수정하지 않았으며 ZIP에서 제외했다.

## 원인과 최소 수정
기존 finally는 navigation() && session.isCurrent()인 경우에만 busy를 정리했다. 현재 PUT 401 처리에서 session.expire()가 인증 세대를 바꾸거나, 실패한 로그아웃 시도가 세대를 바꾸면 이 조건이 false가 되어 저장이 끝나도 입력/버튼이 잠겼다.

inFlight를 boolean에서 요청별 Symbol | null로 변경했다. 요청이 소유한 Symbol과 현재 ref가 일치하는지 ownsRequest()로 확인한다.
- 인증 반영/데이터 무효화: 기존 session.isCurrent() 유지. 같은 세션의 화면 이탈 후 확정 성공·불확실 결과는 기존대로 postChanged() 처리한다. 다른 인증 세대는 무시한다.
- 화면의 성공·오류·이동: 요청 소유권 + navigation() + session.isCurrent(). CSRF 준비 후 PUT 전에도 동일한 current 검사를 재사용한다.
- 진행 상태 정리: 해당 요청의 소유권을 먼저 확인해 inFlight를 해제한다. 같은 화면의 navigation()이 유효할 때만 busy를 해제한다. 이 정리는 인증 만료와 독립적이다.
따라서 401에서도 초안과 로그인 안내를 유지하며 저장 중은 종료된다. 실패한 로그아웃 후 작성자 인증이 유지되면 명시적 재저장이 가능하다. 떠난 화면/재진입한 폼 또는 새 요청에는 이전 finally가 영향을 주지 않는다. PUT 자동 재전송은 없다.

## 수정 전 재현 / 수정 후
제어 가능한 모의 API와 production Next를 사용했다. 브라우저 API를 가로채며 서버 공개 조회는 전용 mock8858이다. 실제 Spring/DB 변경이 아니다.
수정 전의 직전 2단계 production 빌드(소스 시작 상태)에서 새 검사: 1/8 통과, 7/8 실패. 실패는 busy 정리 대기 시간 초과였다. A/B 모두 상세 출발·마이페이지 직접 출발에서 재현했다. 기존 새 폼 보호 검사 1개는 원래 통과했다. before 결과/로그 보존.
최종 코드로 동일 검사 8/8 통과:
1. 상세 출발 PUT401: 로그인 안내, 저장 중 종료, 초안 유지, 비로그인 저장/추가 PUT 차단.
2. 상세 출발 보류 PUT → 로그아웃403 → PUT403: 이전 오류 무시, busy 종료, 초안 유지, 사용자 재저장 때만 새 CSRF+PUT으로 성공.
3–4. 같은 A/B를 from=/mypage&activity=1에서도 검증.
5. 이전 폼 PUT 보류 → 이탈/재진입 → 새 폼 PUT 보류 → 이전 응답 완료: 새 busy/입력/초점/dirty 유지.
6–7. 로그아웃 성공 이후 지연 PUT204/401: 이전 성공/오류/이동 무시, 현재 폼 busy 종료, 비로그인 저장 차단.
8. CSRF 보류 중 실패한 로그아웃으로 인증 세대 변경: PUT 미전송, busy 종료, 명시적 재시도만 새 CSRF+PUT.
고정 sleep을 사용하지 않고 요청 gate 도착/해제·응답·DOM 상태를 기다린다. 렌더링 확인에 requestAnimationFrame을 사용한다. 기존 테스트 assertion 삭제/완화 없음.

## 실행 환경과 명령
Node24.21.0 / pnpm10.34.3. web 기준 pnpm lint, pnpm typecheck, pnpm test 실행.
동일 최종 src를 /private/tmp/iyum-edit-busy-build에 복사하여 다음 실행:
```
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build
IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm exec next start --hostname 127.0.0.1 --port 8466
```
브라우저 공통 환경:
```
BASE_URL=http://127.0.0.1:8466
PLAYWRIGHT_MODULE=/Users/tackeunoh/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/index.mjs
```
다른 환경에서는 PLAYWRIGHT_MODULE을 실제 설치된 Playwright 모듈로 지정한다. Chrome 필요. API8858 서버를 사용하는 검사는 순차 실행한다.
```
COMMUNITY_SSR_MOCK_PORT=8858 COMMUNITY_ARTIFACT_DIR=<output> node tests/post-edit-busy-browser.mjs
COMMUNITY_SSR_MOCK_PORT=8858 COMMUNITY_ARTIFACT_DIR=<output> node tests/community-edit-delete-browser.mjs
MEMBER_ARTIFACT_DIR=<output> node tests/member-posts-browser.mjs
PASSWORD_CHANGE_RACE_DIR=<output> node tests/password-change-race-browser.mjs
```

## 최종 결과
| 검사 | 결과 |
|---|---|
| pnpm lint | 통과, 경고 없음 |
| pnpm typecheck | 통과 |
| pnpm test | 381/381, 실패/skip 0 |
| pnpm build | 통과 |
| post-edit-busy-browser | 8/8 |
| community-edit-delete-browser | 30/30 |
| member-posts-browser | 74/74 |
| password-change-race-browser | 37/37 |
| git diff --check | 통과 |
브라우저 총149개, 모두 이번 실행 결과. 각 JSON/로그 참조. 최종 production src와 현재 web/src가 동일함을 확인했다.
최초 lint의 새 테스트 unused 변수 경고는 제거 후 재실행하여 경고 없이 통과. 첫 sandbox 빌드는 컴파일 진행 중 종료하여 미완료로 기록하며, 권한이 허용된 격리 빌드의 최종 성공 로그와 구분한다. 테스트 기대값을 낮추거나 운영 코드/의존성을 바꾸지 않았다.

## 수동 확인
1. 상세 또는 마이페이지 관리 메뉴에서 수정 진입 → 제목 변경 → PUT401 모의 응답: 초안/로그인 안내 유지, 저장 중 종료, 추가 저장 불가.
2. PUT gate 대기 중 로그아웃403 → PUT403 해제: 초안/작성자 인증 유지, 저장 버튼 활성화. 직접 재저장 시 새 CSRF 확인 후 PUT1회.
3. 이전 PUT 보류 후 이탈 확인으로 나가 재진입하고 새 저장 시작 → 이전 응답 해제: 새 요청의 저장 중/초점/dirty가 유지되는지 확인.

## 한계 / 환경 보존
실제 Spring, 개발 DB, 실제 사용자 글 수정·삭제는 미실행(모의 검증 지정 범위). 실기기 Safari·화면낭독기 검증도 미실행. 기존 별도 404 경합/댓글 조회 오류/로그아웃 실패 스크롤 간헐 문제의 전체 검사는 범위 밖이며 해결했다고 주장하지 않는다. 이번 최종 관련 검사에는 남은 실패 없음.
직접 시작한8466 검증 서버만 정리하고 사용자8443(PID10098)를 유지했다.

## 패키징
최신 web/src/tests/public/필요 설정/patches/의존성 명세 및 지침 포함. review/this-task.patch는 작업 시작 src/tests 대비 이번2파일 변경만 담는다. review/HEAD-to-current.patch는 이전 미커밋 변경과 신규 구현 파일을 포함한다. review/existing-at-start.patch 및 전후 status로 구분한다. 이 보고서/로그/모의 화면 캡처 포함.
.env·실제 인증정보·개인정보·쿠키·토큰·DB·빌드·의존성폴더·기존ZIP 제외. example.test와 test-only 등은 검증용 합성 fixture이다. 실제 파일의 내용/비밀값 마스킹을 위한 소스 변경은 하지 않았다. 압축 무결성과 최신 소스 포함 여부를 검증한다.
