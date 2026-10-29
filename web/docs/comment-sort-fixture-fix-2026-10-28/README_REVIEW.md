# 댓글 정렬 fixture 보완 검토

## 작업 기준과 범위
- 브랜치: feat/mypage-activity-redesign
- HEAD/백엔드 기준: 5745419bca8d402b3b9f0656a96db73dab1ef798
- 기존 댓글 정렬·애니메이션·조회 취소 미커밋 구현을 보존했다. 제품 코드/백엔드/API/디자인 변경 없음. 커밋/푸시/병합/배포 없음.
- 이번 변경: web/tests/inline-comments-browser.mjs 및 이 보고서. 작업 전 git status와 기존 diff를 별도로 보존했다.

## 문제와 수정
로그아웃 뒤 전달하는 과거 POPULAR 응답은 items의 likeCount를99로 변경했지만 nextCursor.likeCount는2 등 이전 값이었다. 실제 isCommentPage는 마지막 항목과 커서의 좋아요 수/ID/시각 및 요청 정렬의 일치를 요구하므로 기존 fixture는 유효한 성공 응답이 아니었다.

기존 hasNext=true를 유지하고 수정한 items의 마지막 항목으로 nextCursor의 createdAt, commentId, likeCount를 구성하며 sort='POPULAR'를 지정했다. 시각 원문을 변환하지 않았다. 기존 tests/helpers/load-typescript.mjs로 실제 src/lib/community-comments.ts를 로드하여 응답 전달 전에 다음을 assertion한다.
- stale.hasNext === true
- isCommentPage(stale, 'POPULAR') === true
검증 로직을 복제하거나 제품 검증 함수를 완화하지 않았다.

전송 계층은 여전히 abort 신호를 관찰하되 실제 fetch에는 이를 전달하지 않아 늦은 응답 전달을 강제한다. 보류 GET→로그아웃→취소 신호 및 새 조회 loading 종료→유효한 과거 응답 전달 순서를 유지한다. 기존 fetch settled 관찰에 실제 Response.json 소비 완료 관찰을 추가했다. settled+consumed 후 requestAnimationFrame 체크포인트를 거쳐 현재 하트 false/좋아요2 및 작성 초안을 검사한다. route.fulfill만으로 완료를 판단하지 않고 고정 sleep을 추가하지 않았다.

## 이번 실행
Node24.21.0 / pnpm10.34.3, web/에서 실행.
- pnpm test: 415/415 통과.
- pnpm lint: 통과.
- inline-comments-browser.mjs: 43/43 통과. 결과 JSON과 로그는 review/evidence/inline/results.json 및 inline.log에 포함.
- git diff --check: 통과.
실행 래퍼는 review/run-fixture-browser-local.sh에 포함한다. 외부 환경에서는 Playwright 모듈 경로를 해당 환경의 설치 경로로 수정한다.

브라우저는 별도127.0.0.1:8467 프로덕션 Next와8858 mock API, 브라우저 route mock을 사용했다. 기존 격리 빌드의 전체 src와 현재 src의 바이트 일치를 확인해 재사용했다. 이번에는 제품 소스 변경이 없어 빌드/타입 검사/무관한 브라우저 suite는 재실행하지 않았다. 사용자8443 개발 서버는 보존한다. 실제 Spring 및 실제 계정/DB 검증은 미실행이다.

## 이전 기록과 남은 제한
이전 ZIP의 상세 댓글61/62 및 빌드/타입 검사 결과는 이번 실행 결과가 아니다. 기존 logout failure: detail URL/body, comment drafts, focus and dirty protection remain의 스크롤 유지 assertion 실패는 후속 항목으로 남긴다. 이번에는 그 테스트를 실행하거나 수정하지 않았다. 이전 실패 뒤 실행되지 않은 assertion을 통과했다고 간주하지 않는다. 실기기/화면낭독기 검증은 미실행.

## 패키지
수정 테스트 전체, 기존 TS loader, 단위 테스트, 최신 관련 소스(검토 편의를 위해 web/src 전체), 설정/lockfile, AGENTS, 작업 시작 대비 patch, 작업 전후 status, 기존 변경 patch, 실제 이번 로그/결과 JSON을 포함한다. 이전 ZIP을 덮어쓰지 않는다. .env, 인증정보, 쿠키, 실제 토큰, node_modules, .next, .git, DB/빌드 산출물은 제외한다. mock 테스트용 데이터는 실제 계정 정보가 아니다.
