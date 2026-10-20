# 마이페이지 내 댓글 리뷰 보완 — 2026-10-20

브랜치 `feat/member-profile`, HEAD `93578013427b14dc469a264e70817806761e2644`. 기존 내 댓글 미커밋/미추적 구현을 보존했다. 백엔드·DB·인증 계약·공통 로그인 안내는 변경하지 않았다.

## 공백 판정

`CommentInputPolicy`는 Java `String.isBlank()`와 코드포인트 2,000자 제한을 적용하고 원문을 보존한다. `PostInputPolicy`는 제목을 `strip()`한 값의 비어 있음과 코드포인트 120자 제한을 검사한다. Java의 whitespace에는 U+001C가 포함되고 U+00A0/U+2007/U+202F/U+FEFF는 포함되지 않는다. JS trim과 다르다.

`member-api.isMemberComment()`는 기존 `validateComment`, `normalizeTitle`, `characterCount`를 재사용한다. 제목 정규화는 검증용 임시 값에만 적용하며 응답 객체/내용을 수정하지 않는다. ID·enum·날짜·순서·커서는 기존 검사를 유지했다. 단위 검사에 일반/빈/탭·줄바꿈/특수 공백, 혼합 페이지와 원문 보존, 최대 길이를 추가했다. 시작본에 새 검사를 실행하면 U+001C 거절부터 실패했다(`before-unit.txt`). 최종 286개 통과.

## 전체 문서 전환 원인과 수정

설치된 Next 16.3.6의 `fetch-server-response.js`는 `!res.ok`인 Flight 응답을 MPA 이동으로 처리한다. 기존 proxy가 RSC까지404로 반환해 AuthProvider 메모리가 초기화됐다. 설치 문서 `proxy.md`와 `web/adapter.js`에서 기본 정규화가 Flight 헤더를 제거하는 것도 확인했다.

- `next.config.ts`: `skipProxyUrlNormalize: true`로 프록시가 실제 RSC 헤더를 구별하도록 한다. 검색 차단/인증 설정 유지.
- `proxy.ts`: 게시글 없음의 **RSC 전송만 200**, 직접 HTML 문서/새로고침/무JS는404. `sec-fetch-dest=document`는 항상404. `_rsc` 쿼리만으로 판단하지 않는다. 이는 없는 게시글을 정상 게시글로 반환하는 처리가 아니라 오류 화면의 Flight 전송이다. 실제 공개 API의 `404 POST_NOT_FOUND`만 판정하며 5xx는 기존 오류 화면을 유지한다.
- `post-not-found/page.tsx`: 서버에서 `safeListReturn(from)`을 적용해 첫 렌더부터 올바른 링크를 전달한다. 클라이언트 전용 query/Suspense fallback을 제거하여 잠깐 `/community/posts`로 이동하는 경쟁을 막는다. 이전 서버 링크 실험만으로는 해결되지 않았으며 이번에는 Flight 전송 수정과 함께 적용했다.
- `PostNotFound.tsx`: 오류 화면 커밋 시 목록 버전을 무효화한다. 실제 제거/개수 변경은 서버 재조회 결과만 반영한다. 즉시 뒤로 가기에도 invalidation이 먼저 실행되도록 layout effect를 사용한다.
- `MyActivity`/`MyComments`: 저장된 목록과 위치를 복원한 다음 stale 목록을 이전 페이지 수만큼 서버 커서로 원자적으로 다시 읽는다. 중간 실패에는 기존 목록을 유지하고 재시도한다. 인증 lease/abort/version 보호는 그대로다.
- `activity-position.ts`: 기준 항목과 양옆 모두 삭제되는 경우를 위해 조회된 인접 ID 후보를 메모리에 유지한다. 다음으로 남아 있는 항목, 없으면 이전 항목을 사용한다. 개인 목록을 브라우저 저장소에 저장하지 않는다.

## 이번 실행 결과

- `pnpm lint`, `pnpm typecheck`, `pnpm test`(286), `pnpm build`, `git diff --check` 통과. 빌드/typecheck는 기존8443 서버 산출물을 보존하기 위해 현재 소스 복사본에서 실행했다.
- `member-comments-browser.mjs`: 수정 전26/30(새404복귀4건 실패), 최종30/30. PC1440·모바일390, 27개/3페이지에서 같은 원문의 댓글3개 제거,24개 유지, 탭/문서 identity/재조회3회/인접ID1979의 실제 top 오차10px 미만 확인. 기존 정상 상세, 키보드 실패·재시도, 계정 전환·401·늦은응답 검사 유지.
- `member-comments-fix-browser.mjs`:10/10. 내 게시글27→26개의 두 복귀 경로, 실패 중 기존27개 유지·재시도24개, RSC200 `text/x-component` 및 문서404, JS/무JS 직접접속·새로고침·복귀, 악성from, 안전한 마이페이지from 초기 링크,5xx 비삭제를 확인했다.
- `member-posts-browser.mjs`:41/41 통과. 기존 내 게시글 키보드 실패·재시도·초점 이탈·상세 왕복·갱신·개인 캐시 회귀를 확인했다. `posts/results.json` 참고.
- 긴 제목 모의 값은 백엔드 최대120자에 맞췄다. 요약 응답과 목록 응답은 독립이므로 삭제 검사는 요약 완료만 기다리지 않고 실제 목록 제거까지 기다린다. timeout 증가는 하지 않았다.

`before/`는 시작본, `diagnostic-*`는 중간 시도의 실패, `comments/`와 `404-return/`은 최종 결과다. 기존 `MyPage-Comments-2026-10-20.md`와 과거 개발 진단 기록은 덮어쓰지 않았다. 옛 무JS404 실패 기록은 역사 자료로 유지하고 이번 직접 문서 검증과 구분한다.

## 범위와 한계

이번은 프로덕션 Next + 통제된 모의 API 검증이다. 실제 Spring API·개발 DB 변경/삭제 검증은 실행하지 않았다(외부 삭제·오류 응답을 제어하는 작업이며 실제 데이터 변경 불필요). iPhone Safari/실기기·스크린리더는 미검증이고 모바일은 Chrome 에뮬레이션이다. 새로고침/새 탭은 의도적으로 새로운 세션 메모리 조회이며 개인 목록 영구 저장을 하지 않는다. 상세 중복 GET은 이번에 해결하지 않았다. 직접 404가 초기 public API 확인을 기다리는 기존 구조도 유지한다.

기존8443/8082 서버와 별도 worktree는 보존한다. 검증용8452 서버와8858 모의 서버만 종료한다. 커밋·push·PR·병합·배포 없음. 리뷰 ZIP에는 HEAD diff와 이번 시작 미커밋 상태 대비 diff를 별도로 제공한다.
