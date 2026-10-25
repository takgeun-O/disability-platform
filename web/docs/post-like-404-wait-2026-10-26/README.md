# IYUM 좋아요 일반404 복귀 대기 수정

수집: 2026-10-26T01:14:56.921855+09:00 / Asia/Seoul
브랜치: feat/community-post-like
HEAD: 7b0fe586886c64bd45d17ced58e781385b9c6de8

## 범위와 기존 작업 구분
시작 때 이미 미커밋·미추적 좋아요 구현, 댓글 토스트, PC 정보 메뉴와 기존 테스트 변경이 있었다. 대상 post-likes-browser.mjs 자체도 미추적이었다. 기존 파일을 보존하며 이번에는 이 파일의 일반404 대기 및 apiOrigin 선언만 수정했다. `__review__/from-start.diff`가 이번 변경 전체다. 시작 시 존재한 모든 Git tracked/untracked 파일 해시를 대조해 대상 이외 파일이 바뀌지 않은 것을 확인했다. 새 작업 기록만 docs에 추가한다. 커밋/push/병합 없음.

## 수정 전후 순서
이전: 404안내 → 복귀 → DOM조건 → 삭제부재/조회증가 assertion.
서버 최신HTML → 브라우저의 이전 캐시 → 브라우저 재조회 순서에서 첫 DOM조건만 일찍 충족될 수 있었다. 이는 검토에서 보고된 원인이고, 이번에는 수정 전 실패를 별도로 재현하지 않았다.
이후: 404안내 → waitForResponse 등록 → 복귀 → 응답200 및 response.finished() 완료 → 기존 DOM조건 → 기존 삭제부재/조회증가 assertion.
응답 조건은 `http://127.0.0.1:8082` origin, GET, 정확한 `/api/v1/community/posts/feed`, type/topic/afterCreatedAt/afterId 없음. 현재 fixture는 /community의 필터 없는 첫 페이지다. Next HTML/RSC나 상세 GET/SSR 내부 호출을 대신 기다리지 않는다. 상수를 명시적으로 선언해 미정의 api 참조가 없다.
게이트404 시나리오 본문은 시작본과 바이트 단위 동일하다. 요청도달·보류중 미완료·해제후 최종목록 검증 유지. 제품 코드/캐시/인증/댓글/백엔드/DB/디자인 변경 없음. sleep/재시도/검증완화 없음.

## 실제 실행
Node24 / pnpm10.34.3. 기존 격리 프로덕션 빌드의 전체 src가 현재 web/src와 바이트 일치함을 확인해 재사용(이번 새 build는 실행하지 않음).
서버: `/private/tmp/iyum-toast-clean-build`에서 `IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 node node_modules/next/dist/bin/next start -p 8464`.
브라우저 환경: BASE_URL=http://127.0.0.1:8464; PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs (설치된 Chrome channel).
반복: `LIKE_ARTIFACT_DIR=<run>/repeat-N node <run>/targeted.mjs` N=1,2,3 순차. 각 실행 새 브라우저/모의 API. targeted.mjs는 최종 파일을 복사한 뒤 check() 입구에서 두 정확한 이름만 선택한 임시 실행본이다. 실행본을 __review__에 포함했다. 최종 web/tests 파일에는 필터가 없다. 실패를 성공할 때까지 재시도한 것이 아니라 사전에 정한3회 전부의 결과를 기록했다.
전체: `LIKE_ARTIFACT_DIR=<run>/full node web/tests/post-likes-browser.mjs` 필터 없이1회.
lint: `pnpm --dir web exec eslint tests/post-likes-browser.mjs` 통과.
`git diff --check` 통과.

결과: 일반404 3/3, 게이트404 3/3, 전체29/29(전체에서도 두404 추가통과). 총35회 시나리오 실행 모두통과. 로그/exits.txt 및 각 results.json 포함. 다른 좋아요 시나리오 실패 없음.
실제계정/DB/Spring 검증 없음, mock만 사용. 기존8443 서버 유지, 직접 시작한8464 및 mock8858 종료. 전체 unit/typecheck/build/댓글 테스트는 이번 테스트 대기 변경 범위에서 재실행하지 않았다. 댓글의 로그아웃 실패 시 스크롤 유지 문제는 사용자가 알린 별도 기존 이슈이며 이번에 수정/재검증하지 않았다. 유한 반복3회의 통과가 모든 스케줄의 절대적 보장은 아니지만 DOM만 보던 앞선 경합은 네트워크 완료를 명시적으로 기다리도록 개선했다.

## 포함/제외 및 재현
최종 테스트는 자체 HTTP mock/fixture/check/helper가 한 파일에 있어 추가 로컬 import helper가 없다. 실행에는 기존 프로젝트의 Next 서버와 외부 Playwright/Chrome이 필요하다. 테스트 단독 ZIP이 전체 실행 앱을 포함하지는 않는다.
원래 상대 경로의 최종 테스트, AGENTS, package.json, 이번diff, 시작/종료상태, 분기/HEAD, README, 실행로그/반복JSON 및 임시필터 실행본 포함. .env/실제계정정보/쿠키/실제토큰/의존성/.next/.git/DB 제외. 테스트의 example.test·fixture-only는 합성 데이터이며 유지했다. 기존 ZIP을 덮어쓰지 않았다.
