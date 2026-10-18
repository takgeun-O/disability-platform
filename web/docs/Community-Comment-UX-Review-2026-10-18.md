# 댓글 UX 후속 검토 — 2026-10-18

## 기준과 범위

- 브랜치 `feat/community-post-cursor`, HEAD `2adf11bff2e58394899d33157aa0a556df3306fa`.
- 시작 시 피드/커서/404/시드 도구/로그아웃 개선 등 미커밋·미추적 변경이 있었다. 이를 유지했다. `iyum-preview`는 수정하지 않았다.
- 이번 변경은 프런트엔드와 테스트·기록에 한정. Spring/API/DB 스키마·정책·인증 계약 수정 없음. Git 커밋·push·병합·배포 없음.
- 현재 설치된 Next 16.3.6의 `node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/{not-found,proxy}.md`와 렌더 오류 복구 구현을 확인했다. React 19.2.4의 effect event를 사용한다.

## 적용 내용과 주요 파일

| 파일 | 변경 및 보존 이유 |
| --- | --- |
| `src/components/community/Comments.tsx` | 로그인 확인창, `autoLoad`/`pauseAuto`/`load` 자동 추가 조회, 요청 중 입력 시 응답 보류, `created` 등록 확인 목록. 기존 입력 검증·편집 초안·늦은 응답·CSRF·댓글 수 갱신을 재사용 |
| `src/components/NavigationGuard.tsx` | `confirmNavigation(action)` 추가. 자체 확인창에서 초안 유실까지 승인한 경우에만 기존 승인 버전/dirty 해제/탐색 처리를 한 번 실행. 기존 `proceed()`도 같은 처리 사용 |
| `src/components/community/CommunityDialog.tsx` | 닫을 때 원래 요소에 `preventScroll`로 초점 복귀. dialog/Tab/Escape 유지 |
| `src/components/community/FeedNavigation.tsx` | 무JS에서는 기존 전체 주제 링크를 노출하고 동작하지 않는 펼침 버튼 숨김. JS의 접힘/펼침·Tab 순서 유지 |
| `src/components/GlobalNav.tsx` | 무JS 공개 메뉴와 기본 `<a>` 정보 링크 제공. JS에서 noscript 내용은 활성 메뉴가 되지 않음 |
| `src/styles/community.css` | 간결한 등록 확인 영역, 감시 요소, 상태 문구 높이 확보. 로딩 문구 제거로 입력 중 폼 위치가 바뀌는 회귀 수정 |
| `tests/community-comments-browser.mjs` | 기존 수동 조회 검사는 Observer 미지원 환경으로 유지하고, 실제 Observer 자동 조회/경합/등록 확인/무JS 메뉴 검사를 별도 추가 |
| `tests/community-post-404-errors-browser.mjs` | 정상 상세 요청 횟수 실측, 실제 404/503/HTML/손상 JSON/타임아웃 구분. DOM 표시 검사 유지 |
| `tests/community-comments-ux-local.mjs` | 명시적 opt-in 실제 시드/계정 검증. 새로 만든 댓글 ID만 기록·삭제. 자격증명은 파일에서 읽되 출력하지 않음 |

### 로그인 확인창

- 익명 등록 버튼은 이제 탐색 링크가 아니라 확인창을 여는 버튼이다. 열기/취소는 POST·CSRF를 보내지 않는다.
- ‘그대로 머무르기’/Escape는 URL·스크롤·작성/수정 초안과 dirty를 유지한다.
- 초안 유실 경고를 같은 창에 표시한다. ‘로그인하기’에서만 안전한 상세 `from`을 포함해 이동하며 기존 이탈 확인창을 다시 띄우지 않는다.
- checking/pending에서는 이동을 막고 error에서는 상태 재조회, 이미 인증됐으면 현재 화면에서 작성 계속하기로 처리한다. 임의 로그인·계정 전환은 하지 않는다.
- 로그인 왕복 초안 영구 보관은 추가하지 않았다.

### 자동 조회와 입력 보호

- 실제 `hasNext`/`nextCursor`를 사용하고 원문 `createdAt`을 유지한다. ASC 순서와 ID 중복 제거는 기존 도우미를 사용한다.
- 목록 끝의 감시 요소가 320px 이내로 접근하면 다음 구간을 읽는다. 작성 폼이 보이거나 입력/편집/대화상자/변경 요청 중이면 자동 조회를 멈춘다.
- 감시 영역 안에서 폼 쪽에서 다시 위로 이동할 때도 scroll 이벤트로 조건을 재평가한다. 읽기 ref가 같은 구간의 중복 요청을 막는다.
- 자동 GET 대기 중 작성 폼을 사용하기 시작하면 응답은 한 구간만 메모리에 보류한다. 즉시 목록에 붙여 폼을 밀지 않으며, 명시적 더 보기 또는 안전한 자동 재개 때 같은 응답을 사용한다. 새 변경 요청·새로고침 시 보류 응답은 버린다.
- 실패는 자동 재시도를 멈춘다. ‘댓글 조회 다시 시도’는 실패했던 추가 조회 커서로 재시도한다. 수동 더 보기는 유지한다.
- Observer 없는 환경도 수동으로 동일 API를 사용한다. hasNext=false이면 감시 종료. 초점·스크롤을 강제로 이동하지 않는다.

### 등록 즉시 확인

- 정상 201/유효 ID 확인 후 서버 반환 ID와 실제 제출 문자열만 등록 확인 영역에 담는다. 임의 작성 시각/새 커서/마지막 구간 추정 없음.
- 여러 건을 보관하며, 기존 커서 조회에서 해당 ID가 등장하면 별도 표시를 제거한다. 정상 목록의 미조회 중간 구간을 건너뛰지 않는다.
- GET은 기존에 읽은 prefix만 갱신한다. 등록 하나를 표시하기 위해 전체 댓글을 새로 훑지 않는다.
- 후속 GET 실패에도 등록 확인과 상태 알림은 유지한다. 불확실 응답은 기존처럼 초안을 보존하고 POST를 재전송하지 않는다.
- 목록 댓글 수 invalidation은 기존 `postListChanged()` 사용. 상세/Comments를 재마운트하지 않는다.

## 404 중복 상세 조회: 미해결 비용을 유지한 이유

프로덕션 별도 복사본에서 사전 `getPost`만 제거하여 비교했다. 원본에는 이 실험을 반영하지 않았다.

| 상태 | 정상 상세 GET | 없는 글 HTTP | 무JS 안내 본문/링크 | JS 안내 |
| --- | ---: | ---: | --- | --- |
| 유지한 구현 | 2회 | 404 | 보임 | 보임 |
| 사전 조회 제거 실험 | 1회 | 404 | 제목이 실제로 보이지 않아 실패 | 보임 |

- metadata/page 사이의 React `cache()`는 렌더 요청 내 중복을 합치지만 Proxy와 렌더 사이에는 적용되지 않는다.
- 이 설치 버전의 렌더 도중 notFound 복구에서 무JS 본문이 빈 상태가 재현됐다. script/RSC 문자열 존재는 성공으로 판단하지 않았다.
- 요청 헤더에 본문을 그대로 전달하는 방식은 최대 게시글의 헤더 크기 제한 및 신뢰 경계 문제를 만들며, 사용자 입력 헤더를 존재 판정으로 신뢰할 수 없다. 전역 캐시/UA 분기/내부 숨김 영역 강제 노출 우회는 추가하지 않았다.
- 따라서 **중복 조회 제거 목표는 완료하지 못했다.** 정상 상세는 2회, 반복 타임아웃에서는 각 요청의 기존 15초 제한 때문에 약 30초까지 사전 조회+렌더 대기가 남는다. 제한 시간을 늘리지 않았다.
- 503/타임아웃/손상 JSON/HTML/다른 404 코드는 POST_NOT_FOUND로 바꾸지 않는다. 공개 GET에 쿠키/Authorization을 전달하지 않는다.
- 이전 문서의 무JS 게시글 404 실패 2건은 당시 기록으로 보존한다. 이번 유지된 구현에서는 실제 Spring·프로덕션의 일반 브라우저/크롤러 모두 안내·복귀 링크가 통과했다. 사전 조회 제거 실험의 실패를 최종 서비스의 성공으로 기록하지 않는다.

## 검증 환경·실행 결과

Node 24.21.0 / pnpm 10.34.3. 기존 8443/8082를 보존했다. 프로덕션 빌드와 타입 검사는 저장소 밖 복사본에서 수행하여 실행 중인 개발 서버의 `.next`를 덮어쓰지 않았다. `.env`는 복사하지 않고 공개 로컬 API 주소만 명시했다.

- `pnpm lint`: 통과.
- `pnpm typecheck`: 통과.
- `pnpm test`: 252/252 통과.
- `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8856 pnpm build`: 통과.
- `git diff --check`: 통과.
- 모의 프로덕션 결과: 아래 수치와 캡처 폴더의 구조화 결과 파일에 기록했다.
- `BASE_URL=http://127.0.0.1:8451 node tests/community-comments-browser.mjs`: 50/50 통과.
- `BASE_URL=http://127.0.0.1:8451 node tests/login-browser.mjs`: 38 통과. 실제 계정 생성 옵션 미사용.
- `node tests/community-edit-delete-browser.mjs`: 29/29 통과.
- `node tests/community-feed-browser.mjs`: 20/20 통과.
- `node tests/community-post-404-errors-browser.mjs`: 14/14 통과.
- `BASE_URL=http://127.0.0.1:8452 node tests/community-404-browser.mjs`: 실제 Spring 공개 읽기 7 통과. DB 쓰기 없음.
- `node /private/tmp/comment-ux-local.mjs`: 실제 Spring+8443 9개 흐름 통과. 재현용 보관본은 `IYUM_RUN_LOCAL_COMMENT_UX=true node tests/community-comments-ux-local.mjs`.
- 브라우저 도구는 `PLAYWRIGHT_MODULE`로 설치된 Playwright를 사용했고 캡처/결과 경로는 `COMMUNITY_ARTIFACT_DIR` 등으로 분리했다. 브라우저 회귀의 모의 응답을 실제 인증/API 검증으로 취급하지 않았다.

### 이번 실행 중 발견·수정한 사항

- 자동 GET 완료 후 로딩 상태 문구 높이가 사라져 입력 중 scrollY가 변하던 검사를 실제로 실패시킨 후, 상태 영역 높이를 유지하여 재검증했다.
- 무JS 캡처에서 정보 하위 링크가 숨겨진 것을 확인하고 기본 링크 fallback으로 변경했다. 최종 검사는 소개/복지/기기/기관 링크를 각각 확인한다.
- 실제 18번 글은 현재 댓글이 22개다. 21개로 가정했던 초기 실제 검사는 대기 실패했다. 기존 데이터를 건드리지 않고 22개로 확인했으며 21개 경계는 모의 테스트에서 별도로 통과했다.
- 실제 모바일 캡처 직후 접힌 헤더의 로그아웃을 누르려던 검사도 초기 실패했다. 데스크톱 폭으로 복원 후 전체 흐름을 다시 실행했다. 이때 생성했던 댓글도 정리했다.
- 최초 제한 환경 빌드는 중단했고, 다른 빌드가 락 때문에 한 번 거절됐다. 직접 시작한 빌드만 정리 후 최종 빌드가 통과했다.

## 실제 데이터 변경

기존 테스트 계정과 시드 게시글 14~18을 사용했다. 기존 시드/사용자 댓글은 삭제·수정하지 않았다. 이번 실제 검증이 만든 댓글 **117,118,119,120**만 각각 DELETE 204로 정리했다. 서버는 논리 삭제 방식이며 영구 삭제라고 표현하지 않는다. 새 계정·게시글 생성 없음. 테스트 비밀번호·토큰·쿠키는 기록/캡처에 저장하지 않았다.

## 캡처 및 직접 확인

캡처와 구조화 결과: `community-comment-ux-2026-10-18/`.
- `comments/login-confirm-{1440,390,320}.png`: 모의 로그인 안내.
- `comments/created-receipts-{1440,390,320}.png`: 모의 연속 등록 표시.
- `comments/nojs-navigation-{320,390}.png`: 무JS 카테고리/공개 메뉴.
- `actual/actual-login-confirm-*.png`, `actual/actual-receipts-*.png`: 실제 Spring 검사 캡처.
- `actual-404/`: 일반 브라우저/크롤러 실제 무JS 오류 화면.
- `no-probe-experiment/`: **반영하지 않은** 사전 조회 제거 비교 실험(무JS 실패 포함).

확인 순서:
1. `http://127.0.0.1:8443/community/posts/14`에서 익명 등록 버튼 → 머무르기/Escape → 현재 위치 유지.
2. 입력 후 같은 버튼 → 유실 안내 → 로그인하기 → 중복 확인 없이 로그인 화면과 상세 from 확인.
3. 로그인해 댓글 하단 가까이 스크롤 → 자동 추가. 폼 접근/입력 시 중지, 더 보기는 수동 사용 가능.
4. 본인 테스트 댓글 등록 → 바로 제출 내용 확인 → 끝까지 조회하면 별도 표시 합침. 이후 본인이 만든 댓글만 삭제.
5. JS를 끄고 320/390px에서 `/community`의 8개 주제/전체 링크와 헤더 공개 링크 확인.

## 남은 제한

- 정상 상세의 2회 GET은 남는다. 최종 조회 1회·HTTP404·무JS 안내를 동시에 보장할 안전한 렌더 경계 재설계는 별도 검토가 필요하다.
- 첫 댓글 SSR은 이전 복구 방식대로 서버 조회를 기다린다. 댓글 첫 조회 지연을 숨기기 위한 스트리밍은 다시 넣지 않았다.
- 작성/편집 중 자동 조회는 의도적으로 멈춘다. 직접 더 보기 또는 입력 종료 후 안전한 위치에서 재개한다.
- 로그인 후 초안 영구 복원, 알림/댓글 수 API 변경, 백엔드/실제 배포 검증은 범위 밖이다.
- 모의 브라우저는 Chrome/Playwright, 실제 검증도 로컬 Chrome이다. 실제 iOS Safari/Android 기기 및 다중 탭에서 외부 로그인 상태가 바뀌는 시나리오는 직접 검사하지 않았다. 컴포넌트는 현재 AuthProvider 상태를 기준으로 분기하지만 새로운 세션 동기화 체계는 만들지 않았다.

## 최종 상태

기존 미커밋·미추적 변경과 새 변경을 보존. `feat/community-post-cursor` / HEAD 동일. 기존 개발 서버 `http://127.0.0.1:8443`, Spring8082 유지. 직접 시작한 8451/8452/8453 임시 서버만 종료.
