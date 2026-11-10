# 관리자 회원 조회 후속 보완 검토 (2026-11-10)

## 기준과 범위
브랜치 feat/admin-member-read, HEAD 21844af7c5293ae3ce808052092e5b2880aced98.
기존 미커밋 회원 조회 구현과 관리자 메뉴 추가는 보존했다. 이번 변경은 검색 재조회, 상세 재진입/가시성, 회원 상태 표현과 문서 설명이다. 백엔드 실행 로직·DTO 구조·직렬화·DB·보안 정책과 의존성은 변경하지 않았다. 커밋/푸시/PR 없음.

## 원인과 수정
- 빈 검색: 수정 전 실제 submit 1회와 이력 증가(2→3)가 있었지만 GET 수는 1회 그대로였다. 조건 문자열이 같아 effect가 재실행되지 않는 문제. 같은 조건은 retry를 올리고 선택 해제는 replaceState, 변경 조건은 pushState와 조건 effect로 한 번만 요청한다. 명시적 검색은 page=0. 새 검색 시 이전 요청 소유권을 즉시 무효화한다.
- 새로운 검색어의 무반응은 이번 환경에서 재현하지 못했다. 최종 테스트에서 URL·GET·결과 변경, page 초기화, 메뉴/Back/Forward 이후 검색을 확인했다. 같은 원인으로 단정하지 않는다.
- IME: 지속 composing ref에 의존하던 제출 방어를 제거하고 키 이벤트의 isComposing/keyCode 229로 조합 Enter만 차단한다. 조합 이벤트 자동화, 종료 후 Enter, 조합 플래그가 남은 상황의 클릭을 검증했다. 실제 OS 한글 입력기로 직접 입력하지는 않았다.
- 상세 재진입: 같은 ID의 이전 완료 상태가 로딩을 가렸다. 선택 전환 때 상세 결과를 비워 새 요청 로딩/오류만 표시한다. A→B→A와 취소를 무시한 늦은 응답도 검증한다.
- 가시성: 수정 전 낮은 600px 창에서 목록 하단 선택 후 상세 top=-1255, bottom=-451. 수정 후 document scrollY=1655 유지, 상세 top=16/bottom=575.5. 실제 헤더 가시 높이와 패널 위치로 최대 높이를 측정하고 header는 고정, body 한 곳만 스크롤한다. 좁은 화면은 기존 단독 상세와 목록 복귀 유지.
- WITHDRAWN은 탈퇴·정리 대기, DELETED는 탈퇴 완료. 이메일은 응답 그대로 표시하며 null이면 복사 없음. 기존 Member.withdraw()의 즉시 DELETED/개인정보 정리/회원 행 유지 정책은 변경하지 않았다. 문서와 Swagger 설명에 예약 상태와 물리 삭제의 차이를 명시했다.
- 상세 요약의 회원 ID/닉네임/상태/권한 중복을 제거하고 이메일 옆 복사 및 KST 안내 1회로 정리했다. 입력 검색어와 실제 적용 조건은 분리한다.

## 이번 최종 소스 검증
web/에서 Node 24, pnpm 10.34.3 사용.
- pnpm typecheck: 통과
- pnpm lint: 오류 0, 기존 admin-action-workspace-browser.mjs 미사용 변수 경고 3개
- pnpm test: 498 통과, 실패/skip 0
- pnpm build: 통과
- BASE_URL=http://127.0.0.1:8478 node tests/admin-members-followup-browser.mjs: production mock 10/10
- BASE_URL=http://127.0.0.1:8443 node tests/admin-members-followup-browser.mjs: development mock 10/10
- node tests/admin-members-browser.mjs: production mock 23/23
- node tests/admin-history-browser.mjs: production mock 13/13
- node tests/admin-modal-return-browser.mjs: production mock 11/11
브라우저 명령은 PLAYWRIGHT_MODULE=/private/tmp/iyum-panel-access/tools/node_modules/playwright/index.mjs 설정. 각 결과 JSON과 실행 로그 포함. 개발 Strict Mode의 초기 취소 조회와 사용자 제출 후 중복 적용을 구분했다.
- BASE_URL=http://127.0.0.1:8478 pnpm test:smoke: 통과. 최초 sandbox EPERM 실패 로그도 별도 포함. 권한을 얻어 재실행했다. 공개 Spring 읽기/SSR 검증은 수행했지만 관리자 회원 API 실세션 검증은 아니다.
- Java 21, server/iyum-api에서 ./gradlew compileJava test --tests '*AdminMemberOpenApiDocumentationTest' --tests '*AdminMemberReadControllerTest' --tests '*OpenApiDocumentationTest' --tests '*OpenApiDisabledTest': 82 통과(6/70/2/4), 실패/skip 0. 실제 springdoc 문서 생성 검증. DB 통합 테스트는 실행하지 않음.

## 수정 전 증거
baseline/의 4건 실패는 의도한 수정 전 재현이다(빈 검색 1, 재진입 1, 하단 가시성 2). 최종 통과 수에 합산하지 않았다. 마지막 소스와 ZIP 소스의 SHA256을 대조했다. next-env.d.ts의 빌드 생성 경로 차이는 제품 변경이 아니므로 시작 상태로 복원했다.

## 한계와 수동 확인
테스트용 관리자 자격 증명을 사용하지 않아 회원 API의 실제 로그인 세션 닉네임/이메일/ID 검색은 미검증이다. 로컬 관리자 로그인 후 각 검색 방식으로 기존 테스트 회원만 조회하고, Network의 query와 응답 및 동일 조건 클릭 시 GET 1회 여부를 확인할 수 있다. 회원 변경/탈퇴는 수행하지 않았다. 새 검색어 문제가 계속되면 submit 여부, URL, GET 여부, 응답 상태를 인증정보 없이 비교해야 한다. 실제 OS 한글 조합 Enter와 조합 종료 클릭도 수동 확인 대상이다.

## 리뷰 자료 구성
changes/task-only.patch는 시작 시점 스냅샷 대비이며 기존 untracked 프론트 전체를 신규 구현으로 계산하지 않는다. changes/cumulative-code.patch는 HEAD 대비 관련 소스 전체(기존 미커밋 구현 포함)로 별도 제공한다. sources/는 원래 저장소 상대 경로를 보존한다. baseline/은 수정 전, evidence/는 이번 최종 실행 로그·테스트 데이터 캡처다. 이전 ZIP은 보존한다. 실제 개인정보/환경변수/인증정보/캐시/빌드 산출물은 포함하지 않는다. 생성 OpenAPI JSON은 요청된 문서 검토 산출물만 별도 포함한다.
