# 댓글 무JS 열람 복구와 새로고침 SVG — 2026-10-18

## 기준

`feat/community-comments`, HEAD `4a8e2468c62ee927f51fb47b2ff29b40e75ce59b`. 기존 미커밋/미추적 변경을 유지했다. 백엔드/API/DB/댓글 개수는 변경하지 않았다. 기존 CORS 코드와 테스트는 작업 전후 해시가 일치한다. 이전 후속 보고와 실패 기록은 보존한다.

## 확인한 원인과 결정

이전 프로덕션 진단에서 bodyVisible=true, commentElements=1, commentVisible=false였다. `PostDetail`의 Suspense/use가 댓글 Promise를 기다리는 동안 fallback을 내보내고, 완료 댓글은 숨겨진 세그먼트 및 교체 스크립트로 전송됐다. JavaScript 비활성 상태에서는 이 교체가 수행되지 않았다.

설치된 Next 16.3.6의 `01-app/01-getting-started/06-fetching-data.md`(Promise/use/Suspense), `01-app/03-api-reference/03-file-conventions/loading.md`(스트리밍과 HTTP 상태 결정)를 확인했다. 설치된 번들 `next/dist/compiled/react-dom/cjs/react-dom-server.node.production.js`의 completeBoundaryScript도 확인했다. [React 공식 설명](https://react.dev/reference/react-dom/server/renderToPipeableStream#streaming-more-content-as-it-loads)은 Suspense 완료 내용을 inline script로 fallback과 교체하는 동작을 설명한다. 단순 Server Component 분리로 해결된다고 판단하지 않았다.

**무JS 공개 열람을 우선해 댓글 스트리밍만 철회했다.** 상세 page에서 게시글/404 판단 후 첫 댓글을 await하고 완성된 PublicResult를 기존 Comments에 전달한다. PostDetail의 Suspense/use/DeferredComments와 ‘JavaScript를 켜 주세요’ fallback은 제거했다. 실제 표시 검사는 그대로 유지한다. CSS나 DOM으로 React 숨김 영역을 강제 노출하지 않는다.

### 지연과 병렬화 검토

게시글과 댓글 조회의 기술적 병렬 시작은 가능하다. 그러나 현재는 없는 게시글/장애에도 불필요한 댓글 요청을 보내지 않고 기존 notFound 순서를 그대로 보존하기 위해 순차 조회를 유지했다. 첫 화면은 댓글 응답을 기다리며 기존 댓글 요청 타임아웃 15초에 더해 게시글 조회/렌더링 시간이 걸릴 수 있다. **본문 선표시 최적화는 보류**했다. metadata의 동일 렌더 조회 중복 제거, no-store, 쿠키/Authorization 미전달 및 검색 차단은 변경하지 않았다. 초기 댓글을 브라우저에서 중복 조회하지 않는다.

## 변경 파일

- `src/app/community/posts/[postId]/page.tsx`: 첫 댓글 await 복구. 게시글 404 결정 먼저 유지.
- `src/features/PostDetail.tsx`: 해결된 댓글 데이터를 Comments에 직접 전달. 재조회는 기존 Comments 내부 상태로 처리.
- `src/components/community/Comments.tsx`: 새로고침 SVG만 교체. 16×16, stroke=currentColor, 1.8px, round cap/join, aria-hidden=true, focusable=false. 기존 inline SVG 방식에 맞추고 의존성 추가 없음.
- `tests/community-comments-browser.mjs`: 아래 검증 보완.

기존 입력 오류 단일 표시/ARIA/초점, 수정 초안 보관/명시적 종료, 별도 작성 초안의 dirty 보호는 유지했다. SVG 간격은 기존 scoped CSS를 재사용하고 비활성화/키보드 표시를 유지했다.

## 검증 설계 변경

- 이전 ‘댓글이 지연되어도 본문 선표시’ 기대는 승인된 설계 변경에 따라 제거하고 **제어 가능한 지연 응답 후 완성 본문/댓글 또는 오류가 표시되는지** 확인한다. 타임아웃을 늘리지 않았다. 초기 브라우저 댓글 GET 0회와 오류 후 재시도도 검사한다.
- 무JS 성공/빈 목록/API 실패는 서로 독립적인 check로 분리했다. 각 검사에서 실제 visible DOM과 HTTP200을 확인한다. script/RSC 문자열로 대체하지 않았다.
- SVG 속성/16px, 1440·390·320px 넘침 없음, 키보드 Tab/Shift+Tab 후 focus-visible 및 캡처 확인.
- 첫 실행에서는 과거 이모지 textContent를 기대하던 검사가 실패했다. 요구된 SVG로 바뀐 것을 검사하도록 갱신했고 나머지 필드 오류 검증은 그대로 보존했다. 최종 실행 결과는 아래에 별도 기록한다.

## 실행 환경 및 남은 범위

Node24.21.0 / pnpm10.34.3 / Next16.3.6. 기존 8443 .next를 방해하지 않도록 `/private/tmp/iyum-edit-build-final/web`에 최신 소스를 복사해 typecheck/build하고 8451(모의 SSR8856), 8452(실제 공개API8082)에서 검증했다. 브라우저에는 설치된 Playwright를 PLAYWRIGHT_MODULE로 지정했다.

- 모의 검사: `COMMUNITY_ARTIFACT_DIR=.../nojs-recovery/mock node tests/community-comments-browser.mjs` (BASE_URL 기본8451).
- 실제 공개 조회/404: `BASE_URL=http://127.0.0.1:8452 COMMUNITY_404_ARTIFACT_DIR=.../nojs-recovery/404-regression node tests/community-404-browser.mjs`.
- 실제 댓글 POST/PUT/DELETE 재검증, 실물 스마트폰 및 Safari는 이번에 실행하지 않았다. 모의 성공을 실제 변경 API 성공으로 보고하지 않는다. 신규 테스트 데이터 생성 없음.
- 기존 무JS 게시글 404 제목/복귀 링크 실패 2건은 별개이며 여전히 남는다. HTTP404/noindex 및 JS 복귀, 실제 Spring의 정상 목록/상세 무JS 표시 검사는 통과했다.

캡처/결과: `community-comments-2026-10-18/nojs-recovery/`. `mock/nojs-comments-success.png`, `nojs-comments-empty.png`, `nojs-comments-failure.png`, `refresh-1440.png`, `refresh-390.png`, `refresh-320.png`는 모의 API 화면이다. 이전 `review-followup/`의 실패 기록은 과거 재현 자료다.

## 최종 실행 결과

| 실행 명령 | 결과 |
|---|---|
| `pnpm lint` | 통과 |
| `pnpm typecheck` | 통과 (격리 복사본) |
| `pnpm test` | 217 통과 / 0 실패 |
| `pnpm build` | 프로덕션 빌드 통과 (격리 복사본) |
| `git diff --check` | 통과 |
| `node tests/community-comments-browser.mjs` | 모의 API **32 통과 / 0 실패** |
| `node tests/community-404-browser.mjs` | 실제 공개 API **5 통과 / 기존 2 실패** |

신규 무JS 댓글 표시 실패는 해결됐다. 정상/빈/실패 모두 각자 실행되어 표시 여부를 확인했다. 초기 지연과 재시도, 입력 오류 횟수·위치, COMMENT_NOT_FOUND/목록 누락 초안 보관, 커서 정밀도, 더 보기, 작성·수정·삭제, 늦은 응답, CSRF/204, 성공 후 조회 실패 구분을 이번 댓글 suite에서 다시 확인했다. 실제 변경 API는 미실행이다.

기존 프런트 8443(PID38875), Spring8082(PID67568)는 유지하고 직접 시작한 8451/8452만 종료했다. 브랜치와 HEAD는 유지하며 기존 미커밋 변경에 이번 소스·테스트·문서를 추가했다. 커밋·push·병합·배포 없음.
