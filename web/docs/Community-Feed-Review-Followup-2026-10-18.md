# 커뮤니티 피드 검토 후속 — 2026-10-18

## 기준과 보존

- `feat/community-post-cursor`, HEAD `2adf11bff2e58394899d33157aa0a556df3306fa`. 기존 피드 미커밋 변경 18개 및 미추적 소스/검증 자료를 보존해 이어서 수정했다. 별도 `iyum-preview` worktree는 변경하지 않았다. 커밋·push·병합·배포 없음.
- 루트/web AGENTS, PRODUCT-CONTEXT, 최신 피드 기록, 실제 API/스키마/엔티티/비밀번호 인코더를 확인했다. 기존 API·DB 스키마·인증·CSRF·CORS는 변경하지 않았다.
- 설치된 Next 16.3.6의 `docs/01-app/03-api-reference/03-file-conventions/not-found.md`, `proxy.md`, `04-functions/not-found.md`, fetching-data 문서와 app-render 구현을 확인했다. Node 24.19.0/pnpm 10.34.3에서 최종 검증했다.
- 종전 [피드 구현 기록](Community-Cursor-Feed-2026-10-18.md)의 가로 스크롤/알려진 404 실패는 과거 결과다. 이번 변경과 구분해 그대로 보존한다.

## 화면 변경

- `components/GlobalNav.tsx`, `Shell.tsx`, `styles/iyum.css`: PC 현재 메뉴는 청록 글자+항상 공간이 예약된 밑줄. 모바일은 로고와 같은 행의 44px SVG 햄버거/닫기 버튼. 인증 컴포넌트는 한 번만 마운트하고 회원 메뉴를 펼친 패널 안에 배치했다. 로그아웃 알림은 패널이 닫혀도 표시된다. Escape·접근성 이름·이탈 취소·알림 타이머 유지.
- `components/community/FeedNavigation.tsx`, `styles/community.css`: PC 카테고리 밑줄, 둥근 글 유형 버튼 유지. 320px 접힌 상태는 전체+현재 주제(전체 선택이면 보청기), 390px는 대표 주제 하나 추가. 숨긴 링크는 display:none으로 Tab 순서에서도 제외. 펼치면 모든 8개 링크가 여러 줄로 표시되고 Escape는 토글로 복귀한다. 가로 스크롤 없음. 태블릿/PC는 모든 링크를 표시한다.
- `features/PostList.tsx`: 안내 토글을 없애고 제목 아래 실제 주제별 이용 안내를 항상 표시. 전체는 경험/출처/개인정보 원칙을 표시. 새로고침은 SVG+텍스트로 목록 제목 옆에 배치해 별도 빈 행을 줄였다.
- PC 게시글은 아바타 열 / 내용 열로 나눠 닉네임·제목·미리보기·댓글 수의 시작점을 맞췄다. 모바일은 아바타+작성자 행 아래 본문이 전체 폭을 사용한다. 메타는 `주제 · 유형 · 날짜`로 구분하며 긴 닉네임은 줄바꿈한다. 우측에는 소량의 여유만 두고 동작 없는 더보기 버튼은 만들지 않았다.
- 실제 enum/URL/본문 일반 텍스트/서버 commentCount 유지. 베스트·이번 주는 준비 상태, 기본 SVG 아바타 유지. 가짜 순위/인기 글/프로필 사진 없음.
- 첨부 디자인 보드와 PC/390/320 캡처를 비교했다. 흰색·네이비·청록, 단일 열 피드, PC 아바타 정렬을 반영했다. 사용자 요구가 시안보다 우선하므로 가짜 베스트 대신 준비 안내, 모바일 가로 스크롤 대신 펼침, 유형 이름은 실제 계약의 이름을 사용한다.

## 조회 오류와 실제 복귀 결함

### 요청 의도 보존

`PostList.load(kind, latest)`의 오류 상태가 kind만 저장해 최신 첫 구간 실패 후 재시도가 reconcileFeed로 바뀌었다. error에 latest를 함께 보관하고 그대로 재호출한다.

- 최신 조회 재시도: 여러 구간을 읽었더라도 커서 없이 최신 첫 20개. 새 글 포함.
- 추가 조회 재시도: 실패한 다음 커서를 그대로 이어 간다.
- 기존 범위 재시도: 읽던 ID 범위와 화면의 기준 글/상대 위치를 유지하며 새 상단 글을 강제로 끼워 넣지 않는다.

오류 안내가 목록 위에 추가되면 문서의 절대 scrollY는 달라질 수 있다. 회귀 검사는 화면에서 읽던 글의 상대 위치를 확인한다. 단순 대기 시간 증가는 사용하지 않았다.

### 개발 Strict Mode에서 복귀 의도가 사라지던 문제

실제 8443에서 67개 → 상세 → 목록 복귀가 20개로 돌아오는 것을 재현했다. 프로덕션 모의 검사는 통과했지만 개발 Strict Mode의 effect setup→cleanup→setup에서 첫 setup이 복귀 의도를 소비한 후 예약 초기화가 취소됐다.

`useLayoutEffect`에서 복귀 의도 소비/방문 ID 선택/history 병합을 취소 가능한 RAF 안으로 옮겼다. 실제 실행되는 초기화만 소비하므로 재실행 때 캐시를 잃지 않는다. 검증 후 같은 탭의 목록 링크/뒤로가기에서 67개와 위치가 유지됐으며, 댓글 생성 후 목록 66 → 삭제 후 65의 갱신도 확인했다. 전체 앱 상태나 영구 초안 저장은 추가하지 않았다.

새로고침·탭 메모리 회수는 메모리 캐시를 보장하지 않는다. 실제 새로고침은 첫 20개부터 시작하며 이를 복원 성공으로 보고하지 않는다.

## 무JavaScript 게시글 404

수정 전 프로덕션에서 일반 UA·Twitterbot 모두 HTTP404/noindex지만 실제 제목·목록 링크가 보이지 않았다. `next/dist/server/app-render/app-render.js`의 오류 복구 `getErrorRSCPayload`는 `html#__next_error__`에 빈 body를 만들고 원래 Flight 데이터를 넘긴다. 이번 경로의 렌더 중 `notFound()`가 이 복구 경로에 들어가므로 링크 종류나 CSS 문제가 아니었다.

`src/proxy.ts`의 기존 주제 404 처리를 최소 확장했다. 상세 경로에서 기존 getPost의 **계약이 확인된 404 POST_NOT_FOUND만** `/community/post-not-found`로 rewrite하고 HTTP404를 지정한다. 새 route는 기존 서버 not-found 화면을 정상 렌더한다. 원래 URL/from을 유지하며 기본 앵커는 무JS에서, 안전한 from/위치 복원은 JS에서 동작한다. `/posts/create`는 명시적으로 제외한다. 편집/댓글/API 경로는 이 matcher에 해당하지 않는다.

- 404를 HTTP200이나 리다이렉트로 감추지 않는다. noindex/htmlLimitedBots를 유지한다.
- 사용자 Cookie/Authorization을 공개 API로 전달하지 않는다. HTML/손상 JSON/503/다른 오류 코드의 404는 없는 글로 바꾸지 않는다.
- 일반 상세 요청에는 존재 여부 확인 GET이 추가되어 proxy+page의 공개 GET이 최대 2회다(metadata/page는 기존 cache로 합친다). 타임아웃 때에도 추가 대기가 가능하다. API/프레임워크 교체 없이 무JS 안내를 우선한 명시적 비용이다. 두 GET 사이에 삭제되는 극히 짧은 경합은 기존 route의 notFound fallback에 남는다. 이 경합을 해결했다고 주장하지 않는다.
- 내부 오류 route 직접 접근도 404다. 정상 7개 주제와 정상 상세/댓글의 서버 HTML을 확인했다.

## 실제 개발 데이터

`server/iyum-api/tools/community-seed.init.gradle` + `tools/community-seed/CommunitySeed.java`를 추가했다. 일반 서버 실행/운영 마이그레이션에 연결하지 않았다. localhost 설정과 실제 스키마/제약을 확인하고 수동으로 실행했다.

| 항목 | 최종 구성 |
|---|---|
| 회원 | ID 34,35,36 / 합성 ACTIVE USER 3명 |
| 게시글 | ID 14–98, 85개; 보청기 65, 다른 기존 주제 20 |
| 보청기 유형 | QUESTION 45, GENERAL 10, REVIEW 10 |
| 대표 댓글 | 글14:65 / 글15:0 / 글16:1 / 글17:20 / 글18:21 |
| 댓글 표시 합계 | 107개 |
| 추가 검증 댓글 | ID114,115는 이번 검사로 생성 후 논리 삭제. manifest에 포함. 물리 행은 109개, 공개 107개 |
| 기존 데이터 | 기존 보청기 글1,3 보존. 따라서 실제 보청기 목록은 67개, 커서 누적은20→40→60→67 |

대표 링크: [댓글65개](http://127.0.0.1:8443/community/posts/14), [보청기](http://127.0.0.1:8443/community/topics/hearing-aid), [질문45개 이상](http://127.0.0.1:8443/community/topics/hearing-aid?type=QUESTION).

계정은 `feed-seed-1@iyum.test`, `feed-seed-2@iyum.test`, `feed-seed-3@iyum.test`. 비밀번호는 저장소 밖 `~/.local/share/iyum/feed-seed-v1/accounts.json`에서 직접 확인한다(0600, 디렉터리0700). 문서/로그/캡처/ZIP에 비밀번호를 넣지 않는다. 회원가입 인증 E2E를 수행한 것으로 해석하지 않는다.

생성·재실행·정리 명령과 제한은 [개발 도구 설명](../../server/iyum-api/tools/README.md). 동일 create 재실행은 0행 생성, 확인 인자 없는 실행은 DB 접근 전 거부됨을 확인했다. cleanup은 검토용 데이터를 남기기 위해 실행하지 않았다. 기존 글·회원·댓글 삭제/초기화 없음.

## 검증 기록과 명령

기존 8443/8082를 유지했다. 별도 `/private/tmp/iyum-feed-followup/web`에 소스/의존성을 복사해 빌드했다. 실제 env 파일을 복사하지 않고 공개 테스트 주소만 명시했다. 모의 API8856용8451, 실제 Spring용8452의 임시 프로덕션 서버를 사용했다.

```sh
# web, Node24 PATH 사용
pnpm lint
pnpm test
# 격리된 web 복사본
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8082 pnpm build
pnpm typecheck
# Playwright는 기존 설치 모듈을 PLAYWRIGHT_MODULE로 지정, 새 패키지 없음
BASE_URL=http://127.0.0.1:8451 node tests/community-feed-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/community-comments-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/community-edit-delete-browser.mjs
BASE_URL=http://127.0.0.1:8451 node tests/community-post-404-errors-browser.mjs
BASE_URL=http://127.0.0.1:8452 node tests/login-browser.mjs
BASE_URL=http://127.0.0.1:8452 node tests/community-404-browser.mjs
BASE_URL=http://127.0.0.1:8452 node tests/community-topic-404-browser.mjs
BASE_URL=http://127.0.0.1:8443 node tests/community-public-browser.mjs
IYUM_RUN_FEED_SEED_TEST=true node tests/community-feed-seed-local.mjs
```

모의 서버8856을 사용하는 스크립트는 직렬로 실행한다. 실제 쓰기는 개발 CORS가 허용한8443에서만 수행했다.8452에서 인증까지 요청한 첫 공개 검사 시도는 CORS로 완료하지 못했으며, 정책을 변경하지 않고8443에서 전체를 다시 검증했다.

최종 브라우저 검사는 모의134개, 실제 API/시드/프로덕션34개가 통과했다. 집계와 원본 결과는 아래 검증 디렉터리의 SUMMARY.json을 참조한다. before-404는 수정 전 기록이며 구 빌드의 피드 선택자 불일치도 포함한다. 최종 결과와 합산하지 않는다.

- 기본 Turbopack 빌드: 초기 원본 경로 실행 시도는 .next/trace-build 쓰기 EPERM으로 중단됐고, 외부 node_modules 심볼릭 링크 격리 복사본은 루트 외부 링크 오류가 있었다. Node23 임시 실행은 지원 범위가 아니므로 중단했다. 의존성 실제 복사/지원 Node24/허용된 임시 실행 환경으로 기본 `pnpm build` 통과. 이번에는 webpack 대체 빌드가 필요 없었다. 기존 환경 실패를 현재 성공으로 덮어쓰지 않는다.
- 타입/lint/단위252 통과. 모의 피드20·댓글37·수정삭제29·로그인38, 장애구분10 및 실제 공개13·시드7·게시글404 7·주제404 7 검사.
- 실제 시드 검사는 커서 순서/중복/동일 시각, 65 댓글, 0/1/20/21 경계, 60개 이상 복귀, 실제 로그인과 댓글 CRUD/개수, 가로 넘침·키보드·홈/소개/복지/로그인/가입 헤더를 확인했다. 기존 데이터 전체를 수정하지 않았다.
- 기존 댓글/수정삭제 검사는 초안/이탈·늦은 응답·권한/CSRF/204를 유지했다. 공개 검사와 로그인 검사의 구 UI 선택자/안내 위치/복귀 조건을 갱신했다. HTML 문자열 존재로 실제 표시 검사를 대체하지 않았다.
- 구 `community-browser.mjs`, `community-topics-browser.mjs`의 페이지 번호·필터 적용 모달·주제 배너 전제 전체 실행은 이번 결과에 포함하지 않는다. 해당 UI가 제거됐으므로 새 피드/7주제/실제 공개/CRUD 검사로 현재 동작을 검증했다. 이 오래된 두 스크립트의 전체 변환은 남은 테스트 유지보수 항목이다. 테스트를 삭제/skip하거나 통과로 표시하지 않았다.
- 백엔드 전체 테스트, 실제 회원가입/이메일 인증, 실제 스마트폰 기기, 탭 메모리 강제 회수, destructive cleanup은 미실행. 프런트 폭은 데스크톱 Chrome1440/390/320로 검사했다.

## 결과 파일과 직접 확인

`community-feed-followup-2026-10-18/`: mock-final(모의 예시), real(실제 시드 자료), final-404(실제 무JS), topic-404, public, error-contract, 각 회귀 결과 JSON. 이전 작업의 캡처/실패 기록은 수정하지 않았다.

1. 8443 `/community`에서 PC 밑줄/메타/정렬과 유형을 확인한다. 모바일 메뉴·카테고리 펼침/Escape도 확인한다.
2. 보청기에서 더보기로67개까지 읽고 중간 글을 연 뒤 목록 링크/뒤로가기로 돌아온다. 새로고침은 첫20개다.
3. 글14에서 댓글을65개까지 읽는다. 나머지 경계 사례는15–18이다.
4. 로컬 accounts.json의 계정1로 로그인해 테스트 소유 글/댓글과 타인 글/댓글 권한을 확인한다. 추가로 남긴 댓글이 있으면 공개 개수가 달라진다.
5. 없는 글99999999를 무JS로 열어 실제 안내와 목록 링크를 확인한다. 게시글404 기존 두 실패는 이번에 직접 재검증해 해결한 범위다.

최종 개발 서버8443과 Spring8082는 기존 프로세스를 유지한다. 검증용8451/8452/8856은 종료한다. 변경 내용은 미커밋 상태로 남긴다.
