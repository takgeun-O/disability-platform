# 댓글 정렬 전환 애니메이션 후속 검토

기준 브랜치 `feat/mypage-activity-redesign`, HEAD `5745419bca8d402b3b9f0656a96db73dab1ef798`.
이전 정렬 프론트 작업이 미커밋 상태로 존재했으며 보존했다. 이번 작업은 그 위에 댓글 전환 표시만 보완한다. 이전 패키지 `iyum-comment-sorting-frontend-review-20261028-211036.zip`은 덮어쓰지 않는다. 백엔드·DB·API 변경, 커밋·푸시·배포 없음.

## 동작과 파일

- `useCommentTransition.ts`: 서버 응답 적용 직전 목록의 시각적 위치를 캡처하고 React layout effect에서 동일 댓글 ID의 위치 차이를 Web Animations API로 보간한다. 240ms, cubic-bezier(.2,.7,.2,1). 새 항목은 opacity 0→1. 최초 조회/일반 더 보기/좋아요에는 정렬 이동 효과를 추가하지 않는다.
- `CommentItem.tsx`: 공통 표시 요소에 댓글 ID를 제공한다. ID를 바꾸거나 댓글 카드를 강제로 재마운트하지 않는다.
- `Comments.tsx`, `InlineComments.tsx`, `inline-comments.ts`: 정렬을 바꿀 때 이전 항목은 보존하고 커서·누적 진행 상태는 초기화한다. 요청 중/실패 시 이전 정렬의 목록이라는 안내를 표시한다. 유효한 최신 서버 결과에만 전환 효과를 적용한다. 인라인 캐시에 실제 표시 중인 정렬도 저장한다.
- `inline-comments-browser.mjs`: 응답 gate로 목록 보존, 실제 animation keyframes/duration, 새 항목 fade, 모션 감소, 초점 유지와 추가 API 없음 검증. 기존 늦은 응답 테스트는 빈 목록 대신 이전 목록 유지와 새 순서 반영을 확인한다.

서버가 반환한 순서만 사용한다. 서버 결과에서 사라진 댓글의 DOM 복제본을 애니메이션용으로 보관하지 않아 중복 ID·중복 버튼·오래된 개인 데이터가 남지 않는다. 따라서 제거 항목 자체의 별도 fade-out은 하지 않으며, 남은 항목의 이동과 새 항목 fade-in으로 전환한다. 큰 목록에서 과도한 장식이나 화면 전체 이동은 추가하지 않았다.

요청의 인증·화면·정렬·소유권 검사 및 서버 커서 검증은 이전 구현을 유지했다. 애니메이션 완료 콜백은 데이터·로딩 상태를 변경하지 않는다. 중간 전환이 생기면 현재 시각적 위치를 측정하고 이전 animation을 취소한다. unmount 시 정리하고 prefers-reduced-motion 설정에서는 실행하지 않으며, 설정이 실행 중 바뀌어도 취소한다. 포커스나 scrollTo를 호출하지 않으며 새 라이브러리는 추가하지 않았다.

## 검증

Node24.21.0 / pnpm10.34.3. 사용자 개발 서버 8443은 보존했다. 실제 계정·DB를 사용하지 않고 이전과 같은 별도 프로덕션 Next8466/8467와 mock SSR8856/8858로 검증했다.

- `pnpm test`: 415/415.
- `pnpm lint`, `pnpm typecheck`, `pnpm build`: 최종 통과. 초기 lint는 ref를 포함한 반환 객체 접근에 걸렸고, ref와 capture 함수를 구조 분해해 명확히 구분한 뒤 통과했다. typegen의 next-env 자동 경로 변경은 시작 상태로 복원.
- `BASE_URL=... node tests/inline-comments-browser.mjs`: 최종 수치는 `review/animation-test-summary.json` 참조.
- `BASE_URL=... node tests/community-comments-browser.mjs`: 최종 수치는 같은 요약 및 로그 참조.
- `git diff --check`: 통과.

첫 브라우저 실행에서 기존 sorting helper가 기존 20개 항목/갱신 시작 전 aria-busy=false를 새 결과로 오인했다. 실제 API origin·경로·GET·선택 sort의 응답 완료와 첫 항목 ID/aria-busy 반영을 기다리도록 수정했다. 고정 sleep이나 허용 오차 변경 없이 기존 순서 assertion을 유지했다. 중간 실행 기록도 포함한다.

이전 ZIP의 415 단위 검사 외 여러 회귀 결과는 `web/docs/comment-sorting-2026-10-28/README_REVIEW.md`와 이전 증거에 별도 보존했다. 그 이전 결과를 이번 애니메이션 변경 이후 실행했다고 주장하지 않는다.

## 한계와 수동 확인

실제 Spring 서버, 실물 Safari/소프트 키보드, 화면낭독기 음성 출력은 이번에 검증하지 않았다. Chromium 1440/390/320과 reduced-motion을 확인한다. 기존 상세 댓글의 로그아웃 실패 후 스크롤 허용 오차 실패는 별도 문제이며 이번에 해결했다고 간주하지 않는다. 그 실패 이후 같은 시나리오의 assertion은 미실행이다.

1. 상세 또는 마이페이지의 펼친 댓글에서 정렬을 바꾼다. 요청이 느려도 목록이 비워지지 않아야 한다.
2. 서로 겹치는 댓글이 있는 등록순→인기순에서 새 응답이 오면 위치가 짧게 이동하는지 확인한다.
3. 최신순처럼 전혀 다른 항목이 오면 새 항목이 fade-in한다.
4. 빠르게 정렬을 바꿔도 마지막 응답만 반영되고 작성·수정 초안은 유지되는지 확인한다.
5. OS 모션 감소를 켜면 이동 효과 없이 즉시 새 결과를 표시한다.

## 패키지 구분

`review/start-to-current.patch`는 이번 애니메이션 추가만, `review/5745419-to-current.patch`는 기존 정렬 구현까지 포함한 누적 변경이다. `existing-at-start.patch`/작업 전 status로 기존 미커밋 작업을 구분한다. 최신 src/tests/config/public/lock/AGENTS/API 문서와 이전·이번 검증 자료를 포함한다. env, 실제 인증정보·쿠키·토큰, DB, 의존성·빌드·캐시·기존 ZIP은 제외한다.

최종 결과: 인라인·정렬·애니메이션 40/40 통과. 상세 댓글 CRUD 61/62(기존 로그아웃 실패 후 스크롤 검사 1건 실패). 단위 415/415 및 최종 lint/typecheck/build/diff-check 통과. 중간 브라우저 실행은 36/40, 이후 39/40이었으며, 모두 기존 목록 유지 후에도 이전 항목 존재를 완료로 간주하던 검사 순서를 수정했다. 마지막 40/40 결과와 중간 실패 로그를 함께 포함한다. 새 테스트는 애니메이션 완료 promise를 기다리며 고정 sleep을 추가하지 않았다. 직접 시작한 서버 8466/8467은 종료했고 사용자 서버 8443은 보존했다.
