# 댓글 프런트엔드 리뷰 후속 — 2026-10-18

## 기준과 범위

- 브랜치 `feat/community-comments`, HEAD `4a8e2468c62ee927f51fb47b2ff29b40e75ce59b`의 기존 미커밋 댓글 구현을 이어서 보완했다.
- 백엔드/API/DB/인증 계약은 변경하지 않았다. 기존 CORS 파일·테스트의 작업 전후 SHA-256 일치를 확인했다. 목록 댓글 개수는 추가하지 않았다.
- 이전 기록 `Community-Comments-Integration-2026-10-18.md`와 캡처는 보존했다. 이번 결과는 `community-comments-2026-10-18/review-followup/`에 별도 저장했다.

## 원인과 구현

### 입력 오류와 새로고침

`Comments.tsx`는 기존 `error.message`와 필드 오류의 동시 출력을, 입력별 `fields` 상태와 일반 `error`로 나눴다. `mutate()`의 로컬 검증 및 서버 content 오류는 작성/수정 textarea 직후 한 곳에서만 표시한다. 고유 오류 ID, aria-invalid, aria-describedby, 오류 입력 초점을 연결했다. `clearField()`는 수정된 값을 재검증해 유효한 입력에서 과거 오류를 지운다. 로그인·권한·통신 오류는 일반 안내로 유지한다.

새로고침은 aria-hidden 이모지와 ‘목록 새로고침’ 텍스트로 구성하고 scoped CSS의 inline-flex/gap으로 간격을 준다. 기존 로딩·변경 요청 중 비활성화는 유지한다.

### 목록에서 사라진 편집 대상

`editor`의 원본·입력값과 조회 목록을 분리한다. `detached`일 때 동일 `editForm()`을 별도 초안 보관 영역에 표시한다. 목록에 없다는 사실만 안내하고 서버 삭제로 단정하지 않는다. PUT의 COMMENT_NOT_FOUND도 `removeItem(id, true)`로 초안을 보존하며 서버 응답에 맞는 안내를 한다.

사용자는 ‘초안 전체 선택’ 후 시스템 복사 기능을 쓰거나 ‘편집 종료’로 명시적으로 종료할 수 있다. 변경된 초안을 끝낼 때 기존 폐기 확인을 사용한다. 종료 후 다른 댓글 편집과 초점 복귀가 가능하다. 새 댓글 초안은 별도 상태이므로 지워지지 않고 이탈 보호도 남는다. 백그라운드 재조회는 editor 기준값을 덮어쓰지 않는다.

### 본문과 댓글 조회 분리

상세 `page.tsx`는 게시글 조회/404 판단을 먼저 끝낸 후 댓글 Promise를 전달한다. `PostDetail.tsx`의 `DeferredComments`는 React `use()`로 Promise를 읽고, 댓글 영역만 Suspense 로딩 상태를 가진다. 첫 댓글은 서버에서 HTML로 생성하며 초기 브라우저 GET을 중복하지 않는다. 이후 재조회는 기존 Comments 내부에서 수행하므로 로딩 때문에 폼을 교체하지 않는다. 서버 오류는 기존 PublicResult로 댓글 영역에서 재시도한다.

설치된 Next 16.3.6 문서 `node_modules/next/dist/docs/01-app/01-getting-started/06-fetching-data.md`의 Promise/use/Suspense 패턴과 `03-api-reference/03-file-conventions/loading.md`의 스트리밍 전 상태 코드 결정 원칙을 확인했다. metadata, 검색 차단, htmlLimitedBots, 쿠키 없는 공개 조회는 유지했다.

## 이번 실행 결과

Node 24.21.0 / pnpm 10.34.3 / Next 16.3.6. 기존 8443 개발 서버의 .next 산출물을 건드리지 않도록 최신 소스를 `/private/tmp/iyum-edit-build-final/web`에 복사하여 타입 검사·프로덕션 빌드를 수행했다.

| 명령 / 조건 | 이번 결과 |
|---|---|
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 |
| `pnpm test` | 217 통과, 0 실패 |
| `pnpm build` | 통과 |
| `git diff --check` | 통과 |
| `BASE_URL=http://127.0.0.1:8451 node tests/community-comments-browser.mjs` | 모의 API: 28 통과, 1 실패 |
| `BASE_URL=http://127.0.0.1:8443 node tests/community-public-browser.mjs` | 실제 Spring 공개 읽기: 6 통과 |
| `BASE_URL=http://127.0.0.1:8452 node tests/community-404-browser.mjs` | 실제 Spring 공개 읽기/프로덕션: 5 통과, 기존 2 실패 |

브라우저 명령은 `PLAYWRIGHT_MODULE`에 설치된 Playwright 경로를 지정하고, `COMMUNITY_ARTIFACT_DIR`(404는 `COMMUNITY_404_ARTIFACT_DIR`)로 위 후속 폴더를 지정했다. 8451은 서버 공개 조회를 모의 API 8856으로 연결했고 브라우저 요청도 모의했다. 8452는 서버 공개 조회를 실제 8082로 연결했다. 모의 결과를 실제 Spring 변경 요청 성공으로 해석하면 안 된다.

추가 회귀는 빈/공백/2,001자/서버 content 오류의 횟수·위치·ARIA·초점, 정상 입력 후 오류 해제, 320/390 배치, 목록 누락 및 PUT404 초안 보존·선택·종료·새 작성 초안 보호를 검사한다. 댓글 응답을 제어 가능한 Promise로 보류하여 본문이 먼저 보이는지 검사하고, 성공 시 댓글 서버 HTML과 초기 브라우저 GET 0회, 실패 시 본문 유지·재시도를 확인했다. 커서 원문 정밀도·더 보기·204·권한·CSRF·늦은 응답·저장 성공 후 조회 실패 등의 기존 검사도 유지했다.

## 남은 실패 — 구분 필수

### 신규: 무JavaScript 댓글 표시 1건

프로덕션의 일반 브라우저 UA, JavaScript 비활성, 정상 게시글/댓글 응답에서 본문은 보이지만 댓글은 숨겨진 React 스트리밍 세그먼트에 남았다. 실제 DOM 진단은 `bodyVisible: true`, `commentElements: 1`, `commentVisible: false`이다. 서버 HTML에 댓글이 있다는 것만으로 표시 성공 처리하지 않았다. 기존 `SSR visible comments without JS; isolated comments failure`의 표시 assertion을 유지하여 실패로 기록했다. 이 검사 뒤에 연결된 무JS 댓글 오류 화면 assertion은 첫 실패 때문에 미실행이다.

Suspense 완성 영역을 교체하는 실행 스크립트가 없는 조건에서 재현된다. fallback에 무JS 안내를 추가했지만 댓글 표시 해결은 아니다. JS 활성 상태의 스트리밍/초기 SSR 검사는 별도로 통과한다. 숨김 영역 강제 노출, 검사의 skip 또는 문자열 검사 대체는 하지 않았다. 무JS 댓글 열람까지 보장할 별도 서버 응답 설계는 남은 작업이다.

### 기존: 무JS 게시글 404 안내 2건

일반 브라우저/크롤러 UA 모두 HTTP404·noindex는 통과하지만 표시되는 404 제목·복귀 링크 검사가 기존처럼 실패한다. 정상 목록/상세의 무JS 본문과 JS 복귀는 통과했다. 이번 댓글 문제와 원인·결과를 합치거나 해결됐다고 보고하지 않는다.

## 실제 API 검증과 미검증

이번 실제 검증은 기존 Spring/DB 공개 읽기만 수행했다. 로그인 후 댓글 POST/PUT/DELETE의 실제 API 재검증은 이번에 실행하지 않았으며 이전 통과 기록을 재사용하지 않는다. 새 계정·게시글·댓글 등 테스트 데이터는 생성하지 않았다. 모바일은 Chromium viewport 320/390 및 데스크톱 1440 모의 검사이며 실물 스마트폰/Safari 검증은 하지 않았다.

## 직접 확인

1. 로그인 후 본인 댓글에서 빈값·공백·2,000자 초과로 저장: 입력 바로 아래 오류 한 번, 유효한 내용 입력 후 오류 해제.
2. 다른 탭에서 편집 중 댓글을 삭제한 뒤 원래 탭에서 새로고침: 보관 초안 확인·복사·편집 종료. 새 댓글 초안이 있으면 이탈 확인 유지.
3. 개발 도구에서 댓글 조회만 지연: 게시글 본문이 먼저 보이고 댓글 영역에 로딩 표시. 실패 시 해당 영역에서 재시도.
4. `review-followup/mock/`의 `review-fields-320.png`, `review-fields-390.png`, `review-detached-refresh.png`, `review-detached-PUT404.png`, `comments-streaming-body-first.png`, `comments-1440.png` 참고. 모든 해당 화면 데이터는 모의이다. `real-public/` 캡처만 실제 공개 읽기이다.

## 최종 회귀와 작업 상태

- `BASE_URL=http://127.0.0.1:8451 node tests/community-edit-delete-browser.mjs`: 모의 API **29 통과, 0 실패**. 기존 작성자 권한·늦은 PUT·입력 보호·유형/주제 변경·삭제 모달 흐름을 이번에도 실행했다.
- 이번 변경 파일: `src/components/community/Comments.tsx`, `src/features/PostDetail.tsx`, `src/app/community/posts/[postId]/page.tsx`, `src/styles/community.css`, `tests/community-comments-browser.mjs`, 본 문서와 후속 검증 산출물. 다른 Git 변경은 기존 작업에서 유지된 것이다.
- 브랜치와 HEAD 유지, 기존 미커밋·미추적 자료 보존. 커밋·push·병합·배포 없음.
- 검증용 8451/8452 서버만 종료. 기존 프런트엔드 8443과 Spring 8082는 유지한다. 접속 주소: http://127.0.0.1:8443/community
