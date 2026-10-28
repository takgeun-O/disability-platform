# 게시글별 댓글 정렬 프론트 검토

- 작업 브랜치: `feat/mypage-activity-redesign`
- 시작 HEAD/기준: `5745419bca8d402b3b9f0656a96db73dab1ef798`
- 시작 작업 트리는 clean. 이전 마이페이지·상대 시간·댓글 공통화·인라인 UI 작업은 HEAD에 포함되어 있었으며 보존했다. 이번 patch는 정렬 연결 및 테스트 보완이다.
- 백엔드, DB, 인증 정책, 개인 `/members/me/comments` API는 변경하지 않았다. 커밋·푸시·배포 없음.

## 구현 및 검토 시작점

- `web/src/lib/community-comments.ts`: CommentSort와 확장 커서. 댓글 항목의 id/createdAt 검증을 커서 검증에서 분리. 첫 요청은 size=20/sort, 후속은 원문 afterCreatedAt/afterId/afterSort, 인기순만 afterLikeCount(0 포함). 응답 커서 정렬·타입·마지막 항목과의 일치 확인. 같은 커서 반환 거부. 개인 내 댓글 커서는 별도 타입 그대로다.
- `web/src/components/community/CommentSortMenu.tsx`, `web/src/styles/community.css`: 등록순/최신순/인기순 공통 버튼·메뉴. menuitemradio, 선택 표시, 방향키/Home/End/Escape, 외부 클릭, Tab 이동, 선택/닫기 초점 복귀. 인라인 중복 제목/설명/상세 링크를 복원하지 않았다.
- `web/src/components/community/Comments.tsx`: 상세의 정렬 선택 및 read ticket 무효화. 정렬 변경은 목록·커서·누적 페이지·버퍼만 초기화하고 작성/수정 초안은 유지한다. 이전 요청의 성공/오류/finally는 새로운 요청을 건드리지 않는다. 현재 목록에 없는 수정 초안은 기존 보관 UI를 사용하며 삭제로 단정하지 않는다. POST 결과 보관과 해시 대상 추가 조회는 기존 구조를 사용한다.
- `web/src/lib/inline-comments.ts`, `web/src/components/member/InlineComments.tsx`: 정렬과 커서 이력을 게시글별 메모리 snapshot에 보관. 정렬 전환 시 controller 소유권과 sort를 함께 검사한다. 탭/접기/상세 왕복은 기존 캐시·스크롤 구조를 사용한다. 누적 새로고침은 선택 정렬로 처음부터 읽어 완성본만 반영한다.
- `web/tests/community-comments.test.mjs`, `inline-comments.test.mjs`: null/0 구분, 커서 정렬·안전한 정수·마이크로초, 순환 커서, 중복-only 페이지 검사.
- `web/tests/inline-comments-browser.mjs`: 서로 다른 실제 정렬 결과, 인기순 3페이지, 0인 커서, 동일 정렬 무요청, A/B 격리, 좋아요 이후 배열 유지, 불확실 결과의 선택 정렬 재조회, 실패 동일 커서 재시도, 중복-only 페이지 후 다음 커서, 상세/인라인 응답 gate 경합, 초안 및 복원, 1440/390/320 메뉴·키보드 검사 추가.
- 기존 `community-comments-browser.mjs`, `comment-likes-browser.mjs`, `member-comments-browser.mjs`, `comment-likes.test.mjs`는 공개 댓글 fixture의 확장 커서만 갱신했다. 개인 댓글 fixture 커서는 변경하지 않았다.

## 인기순·좋아요·요청 경합

서버가 반환한 순서를 그대로 유지하며 프론트에서 정렬하지 않는다. ID 중복을 제거해도 hasNext/nextCursor는 원래 서버 페이지 값이다. 커서 식별에는 정렬/원본 시각/ID/좋아요 수를 모두 사용한다. 순환 커서는 오류로 처리하며 정상 목록은 유지한다. 상세의 자동 더 보기에서 새 항목이 0개면 자동 연속 조회를 멈추고 명시적 더 보기로 이어간다. 인기순의 페이지 간 순위 이동으로 생기는 누락까지 해결한다고 주장하지 않는다.

확정된 좋아요는 기존 공유 저장소만 갱신하고 배열을 재정렬하거나 GET을 추가하지 않는다. 불확실 결과는 기존 게시글별 댓글 버전으로 선택된 정렬·필요 범위만 재조회한다. 인증 revision, navigation scope, controller/read ticket 검증을 유지한다. 이전 인증 응답은 현재 상태에 적용하지 않는다.

## 검증 환경과 명령

Node 24.21.0, pnpm 10.34.3. 실제 Spring 연동은 미실행이며 모든 브라우저 데이터는 모의 API다. 사용자 서버 8443을 보존하고 별도 프로덕션 Next 8466/8467, mock SSR 8856/8858을 사용했다. 직접 실행한 검증 서버만 정리했다.

- `pnpm test`, `pnpm lint`, `pnpm typecheck` (web)
- `/private/tmp/iyum-inline-build`에 현재 src를 복사해 `NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8082 IYUM_API_INTERNAL_URL=http://127.0.0.1:8858 pnpm build`. 동일 package/lock/config/node_modules로 사용자 개발 빌드와 분리.
- 해당 빌드 `pnpm exec next start --hostname 127.0.0.1 --port 8467` (SSR8858), 별도 8466 (SSR8856).
- `BASE_URL=... PLAYWRIGHT_MODULE=... node tests/inline-comments-browser.mjs`
- 같은 방식으로 community-comments-browser, comment-likes-browser, member-comments-browser, member-posts-browser, post-reactions-navigation-browser, written-time-browser 실행.
- `git diff --check`

검증 실행 helper와 결과 요약은 `review/`에 포함한다. Playwright는 설치된 runtime의 Chrome을 사용. 외부 폰트 CSS 로딩 지연을 피하기 위해 기존 동일 Google Fonts CSS를 테스트 route로 제공했으며 제품 CSS는 교체하지 않았다. `next typegen`의 next-env.d.ts 자동 경로 변경은 시작본으로 복원했다.

## 중간 실패를 포함한 실행 이력

- 초기 unit 409/409 → 추가 검증 414/414 → 최종 415/415.
- 초기 인라인 34/35: 새 응답 gate 테스트가 로딩 상태의 React 반영 전에 즉시 검사. 로딩 UI 반영을 명시적으로 기다린 후 이전 응답을 해제하도록 순서를 수정했고 35/35 통과.
- 추가 인라인 36/37: 종료 대기 selector가 더 보기 외에 좋아요 버튼의 aria-disabled까지 포함해 timeout. 실제 더 보기/재시도 버튼과 영역 busy 조건으로 대상을 바로잡았다. assertion은 유지했다.
- 최종 결과는 `review/test-summary.json` 및 최종 로그 참조. 최종 제품 소스 변경은 상세 중복-only 페이지의 자동 조회 정지 보완이며, 이후 unit/lint/typecheck/build와 상세·인라인·댓글 좋아요를 재실행했다. 마이페이지 게시글/내 댓글·이동·상대 시간 회귀는 이 마지막 상세 전용 보완 직전에 실행했다.
- 알려진 `logout failure: detail URL/body, comment drafts, focus and dirty protection remain`은 `abs(scrollY-before)<4`에서 다시 실패했다. 이전에도 보고된 동일 assertion이며 정렬 전환 시나리오가 아니다. 이번에 원인 수정 또는 무관함을 단정하지 않는다. 해당 시나리오에서 그 이후 이탈 방지 검사는 도달하지 못했고, 별도 초안/이탈 시나리오의 결과와 구분한다.

## 수동 확인

1. 마이페이지에서 A/B 댓글을 펼친다. A 인기순 선택 후 더 보기로 3페이지를 확인한다. B는 등록순 유지, 추가 조회 없음.
2. A 좋아요 클릭: 하트/개수만 변경되고 위치는 그대로다. 새로고침하면 선택 정렬로 누적 범위를 다시 읽는다.
3. 접기/재펼침, 내 댓글 탭 왕복, 제목으로 상세 방문 후 뒤로가기에 선택/목록이 유지되는지 확인한다.
4. 상세에서 작성·수정 초안을 입력하고 정렬을 바꾼다. 새 목록과 별도로 초안이 유지되는지 확인한다.
5. 메뉴를 키보드 Enter/방향키/Escape로 사용하고 초점 복귀를 확인한다.

## 제한 및 제외

실제 Spring 정렬 API, 실물 iPhone Safari/소프트 키보드, 실제 화면낭독기 음성 출력은 미검증. 접근성 DOM·키보드와 Chrome 모바일 viewport 검사를 수행했다. 사용자 데이터나 DB 변경은 없다. 인라인 작성은 비활성 시안 그대로, 개인 내 댓글 정렬 추가 없음. 이번 ZIP의 mock 계정/토큰 문자열은 테스트 전용 합성 값이며 실제 인증정보는 포함하지 않는다.

## 최종 집계

| 검사 | 결과 |
|---|---:|
| 단위 테스트 | 415/415 |
| lint / typecheck / production build / diff 공백 검사 | 통과 |
| 정렬 + 전체 인라인 댓글 | 38/38 |
| 상세 댓글 CRUD | 61/62 (위 스크롤 검사 1건 실패) |
| 댓글 좋아요 | 25/25 |
| 마이페이지 내 댓글 | 67/67 |
| 마이페이지 내 게시글·관리 메뉴 | 74/74 |
| 게시글 반응·이동 | 10/10 |
| 상대 시간 및 무JS | 6/6 |

최종 실행은 모두 실제 실행이며 UP-TO-DATE 결과를 재실행으로 표기하지 않았다. 화면 캡처는 `review/evidence/inline-latest/`의 sorting-menu-1440/390/320.png, expanded-*.png 및 viewport-*.png를 참조한다. 브라우저 로그와 각 실행 results.json에는 중간 실패도 보존했다.
