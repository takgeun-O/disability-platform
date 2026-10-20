# Next 16.3.6 — 게시글 404 서버 HTML 복구 패치

`next@16.3.6.patch`는 pnpm의 `patchedDependencies`로 설치하는 **IYUM 전용 고정 버전 패치**다. Next/React 버전을 올리거나 내려 해결했다고 가정하지 않는다. 원인, 최소 재현, 최종 검증은 `../docs/Post-404-NoJS-Fix-2026-10-20.md`를 읽는다.

## 적용 범위

Next의 `getErrorRSCPayload()`가 `errorType === 'not-found'`이고 `ctx.pagePath === '/community/posts/[postId]'`인 경우만 바꾼다. 이 값은 `/page`가 붙는 routeModule.definition.page와 다르다. IYUM의 해당 경로는 단일 children 경로이고 페이지의 공개 조회 결과에서 notFound를 호출한다. 다른 경로, 5xx, redirect, 정상 렌더, RSC 탐색 처리는 바꾸지 않는다. 해당 경로의 layout에서 notFound를 새로 호출하거나 parallel slots를 추가하면 적용 가능성을 재검토해야 한다. 다른 앱에 일반적인 Next 패치로 재사용하지 않는다.

## 읽기 쉬운 핵심 변경

기존 오류 복구는 빈 `<html id="__next_error__"><head/><body/></html>`을 seedData로 사용했다. 수정은 정상 서버 렌더러에 가장 가까운 not-found를 page로 지정한 loader tree를 넘긴다.

```js
if (errorType === 'not-found' && ctx.pagePath === '/community/posts/[postId]') {
  const fallback = createNotFoundRecoveryTree(tree)
  if (fallback) return getRSCPayload(fallback, ctx, { is404: true })
}
// 이외에는 원래 getErrorRSCPayload 구현 그대로 진행

function createNotFoundRecoveryTree(tree) {
  const child = tree[1].children
  const nested = child && createNotFoundRecoveryTree(child)
  if (nested) return [tree[0], { ...tree[1], children: nested }, tree[2], tree[3]]
  const fallback = tree[2]['not-found']
  if (!fallback) return null
  const { page, ...modules } = tree[2]
  return [tree[0], { children: ['__PAGE__', {}, { page: fallback }, null] }, modules, tree[3]]
}
```

기존 root/ancestor layout 및 파라미터 구조를 유지하며 실패한 상세 page를 다시 실행하지 않는다. HTTP 404 설정은 기존 Next 코드가 수행한다. 요청 헤더는 해당 요청의 기존 not-found 컴포넌트가 읽고 재검증한다. 전역 사용자 상태나 복귀 주소를 만들지 않는다.

## 패치 파일이 큰 이유 / 유지보수

npm Next 배포본은 CJS/ESM 소스 외에도 일반/Turbopack, dev/prod, stable/experimental 서버 런타임을 번들로 배포한다. 동일한 조건과 함수를 10개 배포 파일에 반영했다. 압축된 번들 한 줄 전체가 diff에 나타나므로 패치가 크다. 핵심 로직은 위 코드뿐이며 source map은 수정하지 않았다.

- `pnpm install --frozen-lockfile`로 자동 적용한다. node_modules를 수동 편집하지 않는다.
- lockfile의 patch hash 및 정확한 Next 버전이 설치를 검증한다. 이번 검증에서는 별도 폴더에 새로 설치한 뒤 build/start했다.
- Next 업그레이드 또는 상세 라우트/parallel slot/layout 구조 변경 시 반드시 재검토한다. 새 버전에 패치를 강제 적용하지 않는다.
- 업스트림에서 해결되면 패치를 제거한 상태로 `member-404-race-browser.mjs`를 비롯한 회귀를 통과시킨 뒤 제거한다.
- 현재 8443 개발 서버는 보존했으므로 기존에 로드한 미패치 의존성으로 실행 중이다. 사용자가 서버를 안전하게 종료한 뒤 `pnpm install --frozen-lockfile`, `pnpm dev`를 수행해야 그 서버에도 적용된다. 프로덕션은 재설치·재빌드가 필요하다.
