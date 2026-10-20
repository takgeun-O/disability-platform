# 게시글 렌더 중 404 복귀 주소 — 2026-10-20

브랜치 `feat/member-profile`, HEAD `93578013427b14dc469a264e70817806761e2644`. 시작 시 기존 내 댓글 및 공백/404 복원 미커밋 변경을 보존했다. 백엔드·DB·인증 계약·공통 로그인 안내는 변경하지 않았다.

## 결론

**렌더 중 notFound의 복귀 주소 누락은 수정했다. 다만 모든 요청 조건을 완료한 상태는 아니다.** 프록시200 → 렌더404 경쟁 경로의 무JS HTML은 HTTP404이나 실제 body가 비어 있는 기존 문제가 남았다. 실패 검사는 유지하며 최종 경쟁 검사15/16이다. 프록시가 처음부터404를 확인하는 기존 경로는 무JS 안내/링크까지 정상이다.

## 재현과 근거

`member-404-race-browser.mjs`는 서버 내부 API를8858에서 직접 받는다. 특정 원문 ID를 arm한 뒤 첫 공개 게시글GET에200을 반환하고 모의 원문을 제거한다. 다음 GET부터404 POST_NOT_FOUND를 반환한다. 타이머로 삭제 시점을 추정하지 않는다. 실제 순서는 final/trace-1440-link.json 등에 기록했다.

- 요청1 `/api/v1/community/posts/994`:200 (proxy의 선조회)
- 요청2 같은 경로:404 POST_NOT_FOUND (서버 렌더의 readPublicPost)
- 클라이언트 탐색: HTTP200 `text/x-component`, 문서 identity 유지
- 직접 문서: HTTP404, URL 유지

시작본에서 네 PC/모바일 복귀 링크는 `/community/posts`로 잘못 표시됐다. JS 직접 접근도 잘못된 링크였으며 무JS는 body/heading/link가 비어 실패했다. 첫6개 검사 전부 실패했다. 이는 추측이 아닌 같은 순서의 프로덕션 재현이다.

설치 Next16.3.6 `not-found.md`는 not-found에 props를 전달하지 않는다고 명시한다. 기존 전용 오류 page는 명시적으로 back을 전달했지만 Next가 호출하는 상세 not-found에는 전달자가 없었다.

## 수정 파일

- `src/proxy.ts`: 상세 요청의 from을 safeListReturn으로 검증해 `x-iyum-post-return` **요청 헤더**에 넣는다. 클라이언트가 보낸 동명 헤더는 덮어쓴다. 중복from은 기본 목록으로 처리한다. 응답 헤더나 공개 API에 이를 전달하지 않으며 전역 변수도 사용하지 않는다. 기존 rewrite/RSC·HTML 상태 구분은 유지한다.
- `src/app/community/posts/[postId]/not-found.tsx`: props 없는 async boundary로 변경. `headers()`에서 요청별 값을 읽고 safeListReturn을 다시 적용해 서버에서 링크를 결정한다.
- `src/components/community/PostNotFoundView.tsx`: 기존 동일 디자인의 서버 표시 부분만 분리했다. 일반 컴포넌트이므로 명시적인 back prop을 받을 수 있다.
- `src/app/community/post-not-found/page.tsx`: 기존 searchParams 검증을 유지하고 공유 view로 전달한다. 초기 링크와 클라이언트 링크가 서로 다른 fallback 경쟁을 추가하지 않았다.
- `tests/member-404-race-browser.mjs`: 요청 순서 제어, 실제 RSC/문서 상태, private 탭·3페이지·인접ID·개수·스크롤, public from 및 위조 헤더를 확인한다.

기존 MyActivity/MyComments의 memory lease, 원자적 페이지 재조회, 인접 항목 복원, 키보드 초점과 공백 검증은 수정하지 않았다.

## 남은 무JS 실패와 시도

1. 요청 헤더로 서버 복귀 주소를 전달한 후 JS 문서와 클라이언트 왕복은 통과했다. 무JS는 HTTP404를 유지하지만 `document.body.innerText`가 빈 문자열이고 실제 h2/a DOM도 없다(final/dom-false.json). 문자열이 RSC에 있는 것으로 성공 처리하지 않았다.
2. 격리 빌드에서 metadata에도 notFound()를 호출해 오류 판정을 앞당겨 보았다. 동일하게 무JS 표시 실패가 남았다(metadata/results.json). 이 실험은 실제 서비스 코드에 반영하지 않았다.
3. 직접 문서를 오류 URL로 redirect하면 최초 문서 응답이307로 바뀌므로 현재 요청의404 유지 조건을 만족한 해결책으로 채택하지 않았다. CSS로 내부 숨김 영역을 노출하거나 JS로 본문을 삽입하는 우회도 넣지 않았다.

확인 범위는 현재 설치 버전과 앱 구성의 **렌더 도중 오류 복구 HTML**이다. 모든 Next 버전의 일반적 한계라고 단정하지 않는다. 다음 조사는 이 복구 문서에서 root/layout·메타데이터·프레임워크 오류 렌더가 본문을 누락하는 경로를 분리하는 것이며, 이번에는 전체 라우팅/프레임워크 재설계로 확대하지 않았다.

## 검증

현재 소스 복사본의 프로덕션 Next8452 + 제어한 모의 API8858, Chrome PC1440/모바일390을 사용했다. 현재 개발8443의 산출물이나 DB는 수정하지 않았다.

- `pnpm lint`, `pnpm typecheck`, `pnpm test`286개, `pnpm build`, `git diff --check`: 통과.
- 신규 경쟁 검사:15/16, 무JS 경쟁 문서 본문 표시1건 실패. PC/모바일 댓글 두 복귀 방식, 내 게시글 두 방식, JS 직접문서404, 공개/주제 필터, from누락/외부/잘못된값/위조헤더는 통과.
- 기존404 추가회귀:10/10. 프록시 바로404의 JS/무JS 직접·새로고침·복귀, RSC상태, 갱신 실패/재시도,5xx 비삭제 유지.
- 기존 댓글30/30, 내 게시글41/41 통과. comments/results.json, posts/results.json에 이번 실행 결과를 기록했다.

실제 Spring·DB 검증/데이터 변경은 하지 않았다. 실물 Safari·스크린리더는 미검증이며 모바일은 에뮬레이션이다. 기존 서버와 별도worktree는 보존하고 직접 시작한8452/8858만 정리한다. 커밋·push·PR·병합·배포 없음.

시작본 대비diff와 HEAD 대비diff는 다르다. ZIP에는 둘 다 제공하며 과거 검증 문서는 보존한다. `before`는 시작본 실패, `header`는 첫 수정, `metadata`는 폐기한 격리 실험, `final`은 이번 최종 결과다. 기존 상세 API 중복 조회 문제는 해결하지 않았다.
