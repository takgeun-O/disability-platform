> 후속 변경(2026-11-07): 아래 기록은 검색 버튼으로 상태·검색어를 함께 적용하던 당시 검증이다. 현재 상태 칩의 즉시 적용 정책과 최종 검증은 [후속 보고서](../admin-status-chips-2026-11-07/README.md)를 참고한다. 과거 실행 결과는 그대로 보존했다.

# 관리자 다중 상태 필터 프론트 연동 — 2026-11-07

## 기준과 범위
- 브랜치: feat/admin-operation-log
- 시작 HEAD: 674a1a4690642b014ebac97a1556572c8bbd97f5
- 작업 시작 Git 상태는 깨끗했습니다. 이전 백엔드 다중 상태 작업은 현재 HEAD에 포함되어 있습니다.
- 백엔드·DB·API 문서·운영 기록 필터·일반 사용자 화면은 변경하지 않았습니다. 커밋·푸시·병합 없음.
- 기존 8443 개발 서버 유지, 별도 8479 프로덕션 서버에서 mock API 브라우저 검증.

## 변경 파일과 동작
- `src/components/admin/AdminStatusFilter.tsx`: fieldset/legend 및 실제 checkbox/label 공용 컴포넌트. 기본 세 상태, 빈 선택 오류, 키보드 조작.
- `src/features/AdminPosts.tsx`, `src/components/admin/AdminComments.tsx`: 입력용 statuses와 검색어를 적용 query와 분리. 변경만으로 조회하지 않으며 검색/Enter에 새 배열 복사 후 page=0 적용. 초기화는 모두 선택·검색어 비우기·첫 페이지 실제 조회(이미 기본 조건이어도 조회).
- `src/lib/admin-posts.ts`: AdminQuery.statuses, 고정 순서 정규화·중복 제거·빈/잘못된 집합 요청 차단. 반복 statuses만 전송. 기존 AdminStatus와 표시 라벨은 유지.
- `src/lib/admin-navigation.ts`: 목록 URL 반복 상태 저장 및 상세 복귀 시 getAll로 복원. 기존 단일 status 링크는 읽어서 새 다중 상태 주소로 변환. 새로운 URL 상태 시스템은 추가하지 않음. 댓글 검색은 기존처럼 상세 화면 내부 메모리 상태.
- `src/styles/admin.css`: 신규 상태 그룹에만 한정한 반응형 스타일. 기존 테이블 내부 가로 스크롤은 유지.
- 테스트: admin-posts.test.mjs, admin-detail.test.mjs, admin-posts-browser.mjs, admin-detail-browser.mjs, admin-moderation-browser.mjs.

## API 확인
`docs/api/admin-content-status-filter.md`와 실제 AdminContentStatusRequestParser/조회 컨트롤러의 반복 파라미터 계약을 확인했습니다. `admin-comment-read.md`는 기존 단일 status 설명을 유지하고 있으며, 다중 상태 확장은 별도 상태 필터 문서를 기준으로 연결했습니다. 다중 상태 문서와 실제 파서 간 충돌은 발견하지 못했습니다.

요청 예시(한글은 실제 URL에서 인코딩됨):
```
GET /api/v1/admin/posts?statuses=VISIBLE&statuses=HIDDEN&page=0&size=20&keyword=보청기
GET /api/v1/admin/posts/123/comments?statuses=VISIBLE&statuses=HIDDEN&page=0&size=20&keyword=보청기
```
세 상태 선택도 VISIBLE, HIDDEN, DELETED를 모두 보냅니다. status/ALL/쉼표 값은 보내지 않습니다. 서버 순서와 페이지 메타데이터를 그대로 사용합니다.

## 요청 수명·조치 후 갱신
기존 인증 요청 소유권, 화면 이동 범위, AbortController 및 관리자 조치 revision 검증을 유지했습니다. query 객체는 상태 배열까지 복사하며 기존 요청의 current 검사로 늦은 결과를 거절합니다. 댓글 조회는 postId를 포함합니다.
페이지 이동·오류 재시도·조치 후 재조회는 편집 중 필터가 아닌 적용 query를 사용합니다. 게시글 목록 복귀 시 범위 밖 페이지는 기존 1회 보정, 댓글은 조치 revision당 기존 1회 보정을 유지합니다. 새로운 자동 페이지 순회나 폴링은 없습니다.
운영 조치 테스트에서는 적용 VISIBLE/검색어를 둔 뒤 숨김을 추가 체크하고 다른 검색어를 입력만 한 상태로 조치했습니다. 후속 GET 두 건이 기존 필터를 유지하고 page=1→0 순서로 보정되는 것을 확인했습니다.

## 검증 결과
Node 24.21.0 / pnpm 10.34.3. web에서 실행:
```
pnpm typecheck
pnpm lint
pnpm test
pnpm build
PLAYWRIGHT_MODULE=<기존 외부 playwright/index.mjs> node tests/admin-posts-browser.mjs
PLAYWRIGHT_MODULE=<기존 외부 playwright/index.mjs> node tests/admin-detail-browser.mjs
PLAYWRIGHT_MODULE=<기존 외부 playwright/index.mjs> node tests/admin-moderation-browser.mjs
```
브라우저 기본 BASE_URL=http://127.0.0.1:8479. 결과 디렉토리는 ADMIN_ARTIFACT_DIR, ADMIN_DETAIL_ARTIFACT_DIR, ADMIN_MODERATION_ARTIFACT_DIR로 설정 가능.
- 타입 검사, lint, 프로덕션 build 통과.
- 단위 테스트 483/483 통과.
- 게시글 브라우저 19/19 통과.
- 상세·댓글 브라우저 39/39 통과.
- 운영 조치 브라우저 29/29 통과.
- 총 브라우저 87건 통과, skip/assertion 완화 없음.
- git diff --check 통과.

새 테스트: 1440/320px 각각 두 화면에서 초기 상태/요청, 두 상태·한 상태 검색, 검색 전 변경의 무요청, 페이지 이동의 적용값 유지, Enter 적용과 page=0, 빈 선택 차단, 오류 해제, 초기화 재조회, 키보드 Space와 가로 넘침을 검증합니다. URL 복귀에는 두 statuses 값을 사용했습니다. 기존 지연 gate/취소 무시 전송·계정 전환·권한/만료·페이지 포커스·숨김/해제 테스트를 보존했습니다.
새 브라우저 테스트를 작성하는 도중 URLSearchParams 문자열 오타로 최초 실행 두 건이 구문 오류로 종료했습니다. 이를 수정한 뒤 최종 전체 실행 결과가 위 결과이며 미해결 실패는 없습니다. 기존 실패를 skip하거나 대기 시간을 늘리지 않았습니다.

## 캡처 및 직접 확인
- captures/posts-1440.png, posts-320.png: 게시글 필터
- captures/detail-1440.png, detail-320.png: 댓글 필터
캡처는 키보드로 정상 체크를 해제하고 아직 검색하지 않은 상태이므로 화면 결과는 이전 적용 조건(전체)을 유지합니다. 320px 그룹/검색/버튼에 잘림이나 겹침이 없으며 긴 댓글은 줄바꿈합니다. 기존 상세 레이아웃 테스트의 1024/390px도 통과했습니다.

실제 Spring 계정으로 연동하거나 운영 콘텐츠를 변경하지 않았습니다. API 요청 파라미터와 UI 검증은 전부 모의 응답 기반입니다. 수동 확인은 관리자 로그인 후 삭제 해제→검색, 다음 페이지 이동, 댓글 상태 선택 후 숨김 처리 및 필터 제외를 개발 콘텐츠로 확인할 수 있습니다. 실제 운영 데이터 변경은 이번 작업에서 수행하지 않았습니다.

운영 기록 브라우저 및 일반 사용자 전체 브라우저 모음은 별도로 재실행하지 않았습니다(해당 필터/로직 변경 없음). 전역 단위 테스트는 전체 실행했습니다.
