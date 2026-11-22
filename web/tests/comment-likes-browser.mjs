// Production Next + controlled mock API. This does not validate real Spring CORS.
import http from "node:http"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8451",
  api = "http://127.0.0.1:8082"
const output =
  process.env.COMMUNITY_ARTIFACT_DIR ?? "/private/tmp/iyum-comment-likes-browser"
await fs.mkdir(output, { recursive: true })
const member = {
  memberId: 123,
  email: "example@example.test",
  nickname: "검증회원",
  role: "USER",
}
const post = {
  id: 7,
  type: "QUESTION",
  topic: "HEARING_AID",
  title: "댓글 연동 검증 예시",
  content: "게시글 본문은 댓글 조회와 독립적입니다.",
  likeCount: 0, liked: false,
  author: { memberId: 123, nickname: "게시글 작성자" },
  createdAt: "2026-10-17T00:00:00Z",
  updatedAt: "2026-10-17T00:00:00Z",
}
const item = (id) => ({ contentUpdatedAt: null,
  likeCount: 0, liked: false,
  commentId: id,
  content: `댓글 예시 ${id}\n공백  유지`,
  author: {
    memberId: id === 2 ? 999 : 123,
    nickname: id === 2 ? "다른 작성자" : "검증회원",
  },
  createdAt: `2026-10-17T03:00:00.123456Z`,
  updatedAt: "2026-10-17T03:00:00.123456Z",
})
const failure = (code) => ({
  code,
  message: "do not expose private message",
  fieldErrors: [],
})
let listCountOverride = null
let comments = [],
  readFail = false
function read(url) {
  if (url.pathname === "/api/v1/community/posts/feed") {
    const legacy = read(new URL(url.href.replace("/posts/feed", "/posts")))
    return { ...legacy, json: { items: legacy.json.items.map(p => ({...p, contentPreview: p.content})), hasNext: false, nextCursor: null } }
  }

  if (url.pathname.endsWith("/comments/best")) {
    const items = comments.filter(c => c.likeCount >= 10).sort((a,b) => b.likeCount - a.likeCount || a.commentId - b.commentId).slice(0,3)
    return {status:200,json:{items}}
  }
  if (url.pathname.endsWith("/comments")) {
    if (readFail) return { status: 503, json: failure("SERVER_ERROR") }
    const after = Number(url.searchParams.get("afterId") ?? 0)
    const sort = url.searchParams.get("sort") ?? "OLDEST"
    const ordered = sort === "LATEST" ? [...comments].reverse() : comments
    const rest = after ? ordered.slice(ordered.findIndex(c => c.commentId === after) + 1) : ordered,
      items = rest.slice(0, 20),
      hasNext = rest.length > 20
    const last = items.at(-1)
    return {
      status: 200,
      json: {
        items,
        hasNext,
        nextCursor: hasNext
          ? { createdAt: last.createdAt, commentId: last.commentId, sort, likeCount: null }
          : null,
      },
    }
  }
  if (url.pathname === "/api/v1/community/posts")
    return {
      status: 200,
      json: {
        items: [{ ...post, commentCount: listCountOverride ?? comments.length }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      },
    }
  return { status: 200, json: post }
}

const server = http.createServer(async (req, res) => {
  if (req.url.includes("/comments")) {
    assert.equal(req.headers.cookie, undefined)
    assert.equal(req.headers.authorization, undefined)
  }
  const result = read(new URL(req.url, "http://mock"))
  res.writeHead(result.status, { "Content-Type": "application/json" })
  res.end(JSON.stringify(result.json))
})
await new Promise((r) => server.listen(Number(process.env.COMMUNITY_SSR_MOCK_PORT ?? 8856), "127.0.0.1", r))
const browser = await chromium.launch({ headless: true, channel: "chrome" })
const results = []
async function fixture(identity = member, count = 22, automatic = false) {
  comments = Array.from({ length: count }, (_, i) => item(i + 1))
  readFail = false
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: "reduce",
  })
  // Existing manual-load regressions also exercise the unsupported-observer fallback.
  if (!automatic) await ctx.addInitScript(() => { delete window.IntersectionObserver })
  const p = await ctx.newPage()
  p.setDefaultTimeout(8000)
  const state = { identity, calls: [], csrf: null, write: null, get: null, liked: new Set() }
  await p.route(
    (u) => u.origin === api && u.pathname.startsWith("/api/v1/"),
    async (r) => {
      const req = r.request(),
        u = new URL(req.url()),
        method = req.method()
      state.calls.push({ path: u.pathname, method })
      if (u.pathname.endsWith("/me"))
        return r.fulfill(
          state.identity
            ? { json: state.identity }
            : { status: 401, json: failure("AUTHENTICATION_REQUIRED") },
        )
      if (u.pathname.endsWith("/csrf"))
        return state.csrf
          ? state.csrf(r)
          : r.fulfill({
              json: { headerName: "X-CSRF-TOKEN", token: "test-only" },
            })
      if (u.pathname.endsWith("/logout")) {
        assert.equal(req.headers()["x-csrf-token"], "test-only")
        if (state.logout) return state.logout(r)
        state.identity = null
        return r.fulfill({ status: 204 })
      }
      if (u.pathname.endsWith('/login')) { state.identity={...member,memberId:req.postDataJSON().email.startsWith('other')?456:123};return r.fulfill({json:state.identity}) }
      if (method === "GET") {
        const result=read(u)
        if(u.pathname.endsWith('/comments')&&result.json?.items) result.json.items=result.json.items.map(c=>({...c,liked:!!state.identity&&state.liked.has(`${state.identity.memberId}:${c.commentId}`)}))
        return state.get ? state.get(r, u, result) : r.fulfill(result)
      }
      assert.equal(req.headers()["x-csrf-token"], "test-only")
      if (state.write) return state.write(r)
      if(u.pathname.endsWith('/like')) {
        assert.equal(req.postData(),null)
        const id=Number(u.pathname.split('/').at(-2)),c=comments.find(c=>c.commentId===id),key=`${state.identity.memberId}:${id}`
        if(method==='PUT'&&!state.liked.has(key)){state.liked.add(key);c.likeCount++}
        if(method==='DELETE'&&state.liked.has(key)){state.liked.delete(key);c.likeCount--}
        return r.fulfill({json:{postId:7,commentId:id,liked:state.liked.has(key),likeCount:c.likeCount}})
      }
      const id = Number(u.pathname.split("/").at(-1))
      if (method === "POST") {
        const created = comments.length
          ? Math.max(...comments.map((c) => c.commentId)) + 1
          : 1
        comments.push({ ...item(created), author: { memberId: member.memberId, nickname: member.nickname }, content: req.postDataJSON().content })
        return r.fulfill({ status: 201, json: { commentId: created } })
      }
      if (method === "PUT")
        comments = comments.map((c) =>
          c.commentId === id
            ? {
                ...c,
                content: req.postDataJSON().content,
                updatedAt: "2026-10-18T01:00:00Z",
              }
            : c,
        )
      if (method === "DELETE") {
        assert.equal(req.postData(), null)
        assert.equal(req.headers()["content-type"], undefined)
        comments = comments.filter((c) => c.commentId !== id)
      }
      return r.fulfill({ status: 204 })
    },
  )
  await p.goto(base + "/community/posts/7")
  if (identity) await p.getByRole("button", { name: "댓글 등록", exact: true }).waitFor()
  else await p.getByPlaceholder("로그인 후 댓글을 남길 수 있어요", {exact:true}).waitFor()
  return { ctx, p, state }
}
async function check(name, fn) {
  try {
    await fn()
    results.push({ name, status: "passed" })
    console.log("PASS", name)
  } catch (e) {
    results.push({ name, status: "failed", message: e.message })
    console.error("FAIL", name, e.message)
    process.exitCode = 1
    const p=browser.contexts().at(-1)?.pages()[0];if(p)await p.screenshot({path:`${output}/failure-${results.length}.png`}).catch(()=>{})
  } finally { for(const c of browser.contexts()) await c.close() }
}
const reaction=(p,id=1)=>p.locator(`[data-comment-like="${id}"]`)
const btn=(p,id=1)=>reaction(p,id).getByRole('button')
const writes=s=>s.calls.filter(c=>c.path.endsWith('/like')&&['PUT','DELETE'].includes(c.method))
async function settled(p,id=1,liked=false,count=0){await p.waitForFunction(({id,liked,count})=>{const e=document.querySelector(`[data-comment-like="${id}"]`),b=e?.querySelector('button');return e?.dataset.likeReady==='true'&&b.getAttribute('aria-disabled')==='false'&&b.getAttribute('aria-pressed')===String(liked)&&b.textContent.includes(`좋아요 ${count}`)},{id,liked,count})}
function gate(){let release,reach;const reached=new Promise(r=>reach=r),promise=new Promise(r=>release=r);return{reached,release,hold:async()=>{reach();await promise}}}
async function logout(p){await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByRole('link',{name:'로그인',exact:true}).first().waitFor()}
async function login(p,email='review@example.test'){await btn(p).click();await p.getByRole('button',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill(email);await p.locator('input[name=password]').fill('Fixture-only-1!');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/community/posts/7')}
async function selectSort(p, label, value) {
  await p.getByRole('button', { name: /^댓글 정렬:/ }).click()
  const response = p.waitForResponse(r => {
    const url = new URL(r.url())
    return url.pathname.endsWith('/comments') && url.searchParams.get('sort') === value
  })
  await p.getByRole('menuitemradio', { name: label }).click()
  await (await response).finished()
  await p.waitForLoadState('networkidle')
}
try {
 for (const width of [390, 1440]) await check(`베스트 댓글 경계·차순위·두 줄 미리보기와 일반 목록 보존 ${width}`,async()=>{
  const {p,state}=await fixture(member,45)
  await p.setViewportSize({width,height:900})
  const best=p.getByRole('region',{name:'베스트 댓글',exact:true})
  assert.equal(await best.count(),0)
  assert.equal(await p.getByRole("heading", {name:"전체 댓글", exact:true}).count(), 0)
  for(const [id,count] of [[1,10],[2,10],[40,15],[41,10]]){
    comments[id-1].likeCount=count
    comments[id-1].content='한 줄에 담기 어려운 긴 상담 경험을 나눕니다. '.repeat(24)
  }
  state.liked.add('123:1')
  await p.getByRole('button',{name:'목록 새로고침',exact:true}).click()
  await settled(p,1,true,10)
  const ids=()=>best.locator('li').evaluateAll(nodes=>nodes.map(el=>Number(el.dataset.bestComment)))
  assert.deepEqual(await ids(),[40,1,2])
  assert.equal(await p.locator('#comment-40').count(),0)
  for(const preview of await best.locator('li p').all()){
    assert.ok(await preview.evaluate(el=>el.scrollHeight>el.clientHeight && el.clientHeight<=parseFloat(getComputedStyle(el).lineHeight)*2+1))
  }
  await p.locator('#comment-draft').fill('작성 중인 내용')
  const listReads=()=>state.calls.filter(call=>call.method==='GET'&&call.path.endsWith('/comments')).length
  const before=listReads()
  await btn(p).click()
  await best.locator('[data-best-comment="41"]').waitFor()
  assert.deepEqual(await ids(),[40,2,41])
  assert.equal(await p.locator('#comment-draft').inputValue(),'작성 중인 내용')
  assert.equal(listReads(),before)
  await btn(p).click()
  await best.locator('[data-best-comment="1"]').waitFor()
  assert.deepEqual(await ids(),[40,1,2])
 })
 await check('베스트 댓글의 수정·삭제와 수동 조회 실패 후 복구',async()=>{
  const {p,state}=await fixture(member,3)
  comments[0].likeCount=12
  const best=p.getByRole('region',{name:'베스트 댓글',exact:true})
  const refresh=p.getByRole('button',{name:'목록 새로고침',exact:true})
  await refresh.click()
  await best.waitFor()
  await p.locator('#comment-edit-button-1').click()
  await p.locator('#comment-edit-1').fill('수정한 베스트 댓글 본문')
  await p.getByRole('button',{name:'댓글 저장'}).click()
  await best.getByText('수정한 베스트 댓글 본문',{exact:true}).waitFor()
  state.get=(route,u,result)=>u.pathname.endsWith('/best')
    ? route.fulfill({status:503,json:failure('SERVER_ERROR')}) : route.fulfill(result)
  await refresh.click()
  const retry=p.getByRole('button',{name:'베스트 댓글 다시 시도'})
  await retry.waitFor()
  await p.locator('#comment-draft').fill('조회 실패 중에도 유지되는 초안')
  assert.equal(await p.locator('.community-comment-list li[id]').count(),3)
  state.get=null
  await retry.click()
  await best.waitFor()
  await p.locator('#comment-1').getByRole('button',{name:'댓글 삭제',exact:true}).click()
  await p.getByRole('button',{name:'댓글 삭제하기'}).click()
  await best.waitFor({state:'detached'})
  assert.equal(await p.locator('#comment-draft').inputValue(),'조회 실패 중에도 유지되는 초안')
 })
 await check('뒤늦은 베스트 조회가 최신 순위를 덮지 않는다',async()=>{
  const {p,state}=await fixture(member,2)
  comments[0].likeCount=9
  const delayed=gate()
  let first=true
  state.get=async(route,u,result)=>{
    if(first && u.pathname.endsWith('/best')){
      first=false
      await delayed.hold()
    }
    return route.fulfill(result)
  }
  await p.getByRole('button',{name:'목록 새로고침',exact:true}).click()
  await delayed.reached
  try {
    await settled(p,1,false,9)
    await btn(p).click()
    await p.locator('[data-best-comment="1"]').waitFor()
  } finally { delayed.release() }
  await p.waitForLoadState('networkidle')
  assert.deepEqual(await p.locator('[data-best-comment]').allTextContents(),
    [comments[0].author.nickname+comments[0].content+'좋아요 10개해당 댓글로 이동'])
 })
 for (const width of [390, 1440]) await check(`베스트 원문 이동·반복 선택·마지막 링크 포커스 ${width}px`, async () => {
  const { p, state } = await fixture(member, 45)
  await p.setViewportSize({ width, height: 900 })
  await settled(p)
  for (const id of [1, 21, 42]) comments[id - 1].likeCount = 10
  await p.locator('#comments-refresh').click()
  const link = id => p.locator(`[data-best-comment="${id}"]`).getByRole('link', { name: '해당 댓글로 이동' })
  const focused = id => p.waitForFunction(id => document.activeElement?.id === 'comment-' + id, id)
  await link(42).waitFor()
  await selectSort(p, '최신순', 'LATEST')
  const draft = p.locator('#comment-draft')
  await draft.fill('이동해도 유지할 초안')
  const cursors = []
  const last = gate()
  state.get = async (route, url, result) => {
    if (url.pathname.endsWith('/comments')) {
      assert.equal(url.searchParams.get('sort'), 'LATEST')
      const cursor = url.searchParams.get('afterId')
      cursors.push(cursor)
      if (cursor === '6') await last.hold()
    }
    return route.fulfill(result)
  }
  const marked = () => p.locator('.community-comment-list .comment-arrived').evaluateAll(nodes => nodes.map(n => n.id))
  const heading = p.getByRole('heading', {name:'전체 댓글', exact:true})
  const sort = p.getByRole('button', {name:'댓글 정렬: 최신순'})
  const headerBox = await heading.boundingBox(), sortBox = await sort.boundingBox()
  assert.ok(headerBox.x + headerBox.width < sortBox.x && headerBox.y < sortBox.y + sortBox.height && headerBox.y + headerBox.height > sortBox.y)
  const actions = link(42).locator('..')
  const countBox = await actions.locator('span').boundingBox(), linkBox = await link(42).boundingBox()
  assert.ok(countBox.x + countBox.width < linkBox.x && linkBox.height >= 44)
  await p.locator('.community-best-comments').evaluate(el => scrollBy(0, el.getBoundingClientRect().top - 110))
  await p.screenshot({path: `${output}/best-and-list-${width}.png`})
  await link(42).focus()
  await p.keyboard.press('Enter')
  await focused(42)
  assert.deepEqual(await marked(), ['comment-42'])
  const geometry = () => p.locator('#comment-42').evaluate(el => {
    const r = el.getBoundingClientRect(), body = el.querySelector('p').getBoundingClientRect()
    return [r.height, body.x - r.x, body.y - r.y]
  })
  const highlighted = await geometry()
  await p.screenshot({path: `${output}/arrival-${width}.png`})
  await p.locator('#comment-42.comment-arrived').waitFor({state:'detached',timeout:3500})
  assert.deepEqual(await geometry(), highlighted)
  assert.equal(await p.evaluate(() => document.activeElement.id), 'comment-42')
  assert.ok(await p.locator('#comment-42').evaluate(el => el.matches(':focus-visible') && getComputedStyle(el).outlineStyle !== 'none'))
  assert.deepEqual(cursors, [])
  await link(21).click()
  await focused(21)
  assert.deepEqual(cursors, ['26'])
  assert.equal(new URL(p.url()).hash, '#comment-21')
  assert.deepEqual(await marked(), ['comment-21'])
  // Cross the first arrival's deadline while the second one is still active.
  await p.waitForTimeout(1100)
  await link(21).focus()
  await p.keyboard.press('Enter')
  await focused(21)
  assert.deepEqual(cursors, ['26'])
  await p.waitForTimeout(1100)
  assert.deepEqual(await marked(), ['comment-21'])
  await link(1).click()
  await last.reached
  try {
    await link(42).click()
    await focused(42)
  } finally { last.release() }
  await settled(p, 1, false, 10)
  await p.waitForLoadState('networkidle')
  assert.equal(await p.evaluate(() => document.activeElement.id), 'comment-42')
  assert.deepEqual(await marked(), ['comment-42'])
  assert.equal(new URL(p.url()).hash, '#comment-42')
  assert.deepEqual(cursors, ['26', '6'])
  assert.equal(await draft.inputValue(), '이동해도 유지할 초안')
  await p.locator('[data-best-comment="42"]').scrollIntoViewIfNeeded()
  await p.screenshot({path: `${output}/best-comment-link-${width}.png`})
 })
 for (const key of ['Tab', 'Shift+Tab']) await check(`다음 원문을 기다리다 ${key}으로 이동하면 사용자의 포커스와 스크롤을 유지한다`, async () => {
  const { p, state } = await fixture(member, 45)
  await settled(p)
  for (const id of [1, 21, 42]) comments[id - 1].likeCount = 10
  await p.locator('#comments-refresh').click()
  const link = p.locator('[data-best-comment="21"] a')
  await link.waitFor()
  const pending = gate()
  state.get = async (route, url, result) => {
    if (url.searchParams.get('afterId') === '20') await pending.hold()
    return route.fulfill(result)
  }
  await link.focus()
  await p.keyboard.press('Enter')
  await pending.reached
  const chosen = p.locator(`[data-best-comment="${key === 'Tab' ? 42 : 1}"] a`)
  let y
  try {
    await p.keyboard.press(key)
    assert.ok(await chosen.evaluate(el => el === document.activeElement))
    y = await p.evaluate(() => scrollY)
  } finally { pending.release() }
  await settled(p, 21, false, 10)
  await p.waitForLoadState('networkidle')
  assert.ok(await chosen.evaluate(el => el === document.activeElement))
  assert.equal(await p.evaluate(() => scrollY), y)
  assert.equal(await p.locator('.comment-arrived').count(), 0)
 })
 for (const direction of ['Tab', 'Shift+Tab']) await check(`페이지 조회 실패 후 ${direction}으로 재시도에 접근해 원문 강조까지 복구한다`, async () => {
  const { p, state } = await fixture(member, 25)
  await settled(p)
  comments[20].likeCount = 12
  await p.locator('#comments-refresh').click()
  const link = p.locator('[data-best-comment="21"] a')
  await link.waitFor()
  const cursors = []
  state.get = (route, url, result) => {
    if (url.pathname.endsWith('/comments')) {
      cursors.push(url.searchParams.get('afterId'))
      if (cursors.length === 1) return route.fulfill({status:503, json:failure('SERVER_ERROR')})
    }
    return route.fulfill(result)
  }
  const hasFocus = locator => locator.evaluate(el => el === document.activeElement)
  async function tabTo(locator) {
    for (let i = 0; i < 60 && !await hasFocus(locator); i++) await p.keyboard.press('Tab')
    assert.ok(await hasFocus(locator))
  }
  await tabTo(link)
  await p.keyboard.press('Enter')
  const retry = p.getByRole('button', {name:'댓글 조회 다시 시도'})
  await retry.waitFor()
  await tabTo(retry)
  if (direction === 'Shift+Tab') {
    await p.keyboard.press('Tab')
    assert.ok(!await hasFocus(retry))
    assert.ok(await p.evaluate(() => document.activeElement.matches('button, a[href], input, textarea, select, [tabindex="0"]')))
    await p.keyboard.press('Shift+Tab')
    assert.ok(await hasFocus(retry))
  }
  await p.keyboard.press('Enter')
  await settled(p, 21, false, 12)
  assert.deepEqual(cursors, ['20','20'])
  console.log(direction, 'retry loaded comment-21; GET afterId:', cursors.join(' → '))
  await p.waitForFunction(() => document.activeElement.id === 'comment-21')
  await p.locator('#comment-21.comment-arrived:focus').waitFor()
  console.log(direction, 'highlighted focus:', await p.evaluate(() => document.activeElement.id))
 })
 await check('베스트 원문 조회 실패는 커서에서 재시도하고 마지막 페이지 누락 때만 순위를 갱신한다', async () => {
  const { p, state } = await fixture(member, 45)
  await settled(p)
  for (const [id, count] of [[42, 15], [3, 13], [2, 12], [1, 10]]) comments[id - 1].likeCount = count
  await p.locator('#comments-refresh').click()
  const link = p.locator('[data-best-comment="42"] a')
  await link.waitFor()
  comments = comments.filter(c => c.commentId !== 42)
  const reads = []
  state.get = (route, url, result) => {
    if (url.pathname.endsWith('/best')) reads.push('best')
    if (url.pathname.endsWith('/comments')) {
      reads.push(url.searchParams.get('afterId'))
      if (reads.length === 1) return route.fulfill({status:503, json:failure('SERVER_ERROR')})
    }
    return route.fulfill(result)
  }
  await link.click()
  const retry = p.getByRole('button', { name: '댓글 조회 다시 시도' })
  await retry.waitFor()
  const missing = p.getByText('해당 댓글을 찾을 수 없어요.', {exact:false})
  assert.equal(await missing.count(), 0)
  assert.deepEqual(reads, ['20'])
  await retry.focus()
  await p.keyboard.press('Enter')
  await missing.waitFor()
  await link.waitFor({state:'detached'})
  assert.deepEqual(reads, ['20', '20', '40', 'best'])
  assert.deepEqual(await p.locator('[data-best-comment]').evaluateAll(nodes => nodes.map(n => Number(n.dataset.bestComment))), [3, 2, 1])
 })
 await check('해시 직접 진입과 정렬 변경 중 이전 원문 탐색 취소', async () => {
  const { p, state } = await fixture(member, 45)
  comments[41].likeCount = 12
  await p.goto('about:blank')
  await p.goto(base + '/community/posts/7#comment-21')
  await p.waitForFunction(() => document.activeElement?.id === 'comment-21')
  const pending = gate()
  state.get = async (route, url, result) => {
    if (url.searchParams.get('afterId') === '40') await pending.hold()
    return route.fulfill(result)
  }
  await p.locator('[data-best-comment="42"] a').click()
  await pending.reached
  try {
    await p.getByRole('button', {name:/^댓글 정렬:/}).click()
    await p.getByRole('menuitemradio', {name:'최신순'}).click()
    await settled(p, 45)
  } finally { pending.release() }
  await p.waitForLoadState('networkidle')
  assert.notEqual(await p.evaluate(() => document.activeElement.id), 'comment-42')
  assert.equal(await p.locator('.community-comment-list > li').count(), 20)
 })
 await check('noJS anonymous comment content/count remain visible without personalized SSR',async()=>{
  comments=[{...item(1),likeCount:12}]
  const ctx=await browser.newContext({javaScriptEnabled:false}),p=await ctx.newPage()
  const response=await p.goto(base+'/community/posts/7');assert.equal(response.status(),200)
  assert(await p.locator('#comment-1 .community-comment-body').isVisible())
  assert.equal(await btn(p).getAttribute('aria-pressed'),'false')
  assert(await reaction(p).getByText('좋아요 12',{exact:true}).isVisible())
  await p.locator('#comment-1').screenshot({path:`${output}/likes-nojs.png`})
 })
 await check('PUT/DELETE server count, independent locks, no optimistic state; drafts and focus survive',async()=>{
  const{p,state}=await fixture(member,3);await settled(p);await p.locator('#comment-draft').fill('별도 작성 초안');await p.locator('#comment-edit-button-3').click();await p.locator('#comment-edit-3').fill('수정 초안')
  const g=gate();state.write=async r=>{const id=Number(new URL(r.request().url()).pathname.split('/').at(-2));if(id===1)await g.hold();await r.fulfill({json:{postId:7,commentId:id,liked:r.request().method()==='PUT',likeCount:id===1?17:8}})}
  await btn(p).focus();await p.keyboard.press('Enter');await g.reached;await p.keyboard.press('Enter');assert.equal(writes(state).length,1);assert.equal(await btn(p).getAttribute('aria-pressed'),'false')
  await btn(p,3).click();await settled(p,3,true,8);g.release();await settled(p,1,true,17);assert.equal(await p.locator('#comment-draft').inputValue(),'별도 작성 초안');assert.equal(await p.locator('#comment-edit-3').inputValue(),'수정 초안')
  await btn(p).click();await settled(p,1,false,17);assert.deepEqual(writes(state).map(c=>c.method),['PUT','PUT','DELETE'])
 })
 await check('anonymous prompt cancel/login; no automatic write after login',async()=>{
  const{p,state}=await fixture(null,1);await btn(p).click();await p.getByRole('button',{name:'그대로 머무르기',exact:true}).click();assert.equal(writes(state).length,0)
  await btn(p).click();await p.getByRole('button',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill('review@example.test');await p.locator('input[name=password]').fill('Fixture-only-1!');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/community/posts/7');await settled(p);assert.equal(writes(state).length,0)
 })
 for(const [status,code] of [[401,'AUTHENTICATION_REQUIRED'],[403,'CSRF_TOKEN_INVALID'],[403,'COMMENT_LIKE_NOT_ALLOWED'],[404,'COMMENT_NOT_FOUND'],[404,'POST_NOT_FOUND']])await check(`current ${code} distinguished`,async()=>{
  const{p,state}=await fixture(member,1);await settled(p);state.write=r=>r.fulfill({status,json:failure(code)});await btn(p).click()
  if(status===401){await p.getByRole('heading',{name:'로그인이 필요해요',exact:true}).waitFor()}else{await reaction(p).getByRole('alert').waitFor();assert.equal(await p.locator('.community-comment-form [role=alert]').count(),0)}
  assert.equal(writes(state).length,1);assert.equal(new URL(p.url()).pathname,'/community/posts/7')
 })
 for(const mode of ['network','500','bad-id','bad-json'])await check(`uncertain ${mode}: next click reads prefix, never retries mutation`,async()=>{
  const{p,state}=await fixture(member,1);await settled(p);state.write=async r=>{comments[0].likeCount=12;state.liked.add('123:1');if(mode==='network')return r.abort();if(mode==='500')return r.fulfill({status:500,json:failure('SERVER_ERROR')});if(mode==='bad-json')return r.fulfill({status:200,contentType:'text/html',body:'bad'});return r.fulfill({json:{postId:8,commentId:1,liked:true,likeCount:12}})}
  await btn(p).click();await reaction(p).getByRole('alert').waitFor();assert.equal(await btn(p).getAttribute('aria-pressed'),'false');await btn(p).click();await settled(p,1,true,12);assert.equal(writes(state).length,1)
 })
 for (const width of [390, 1440]) await check(`응답 유실 후 재확인으로 베스트 댓글과 하트 위치를 복구한다 ${width}px`, async () => {
  const { p, state } = await fixture(member, 45)
  await p.setViewportSize({ width, height: 900 })
  await settled(p)
  const target = comments[20]
  comments[0].likeCount = 12
  target.likeCount = 9
  await p.locator('#comments-refresh').click()
  const best = p.getByRole('region', { name: '베스트 댓글', exact: true })
  await best.locator('[data-best-comment="1"]').waitFor()
  await p.getByRole('button', { name: '댓글 더 보기', exact: true }).click()
  await settled(p, 21, false, 9)
  const rows = () => p.locator('.community-comment-list > li').evaluateAll(nodes => nodes.map(n => n.id))
  const loaded = await rows()
  assert.equal(loaded.length, 40)
  const draft = p.locator('#comment-draft')
  await draft.fill('재확인 중에도 보존할 초안')
  const button = btn(p, 21)
  const top = () => button.evaluate(el => el.getBoundingClientRect().top)
  const endpoint = '/api/v1/community/posts/7/comments'
  const ranking = () => best.locator('li').evaluateAll(nodes => nodes.map(n => Number(n.dataset.bestComment)))
  for (const liked of [true, false]) {
    const method = liked ? 'PUT' : 'DELETE'
    state.write = route => {
      assert.equal(route.request().method(), method)
      assert.equal(new URL(route.request().url()).pathname, endpoint + '/21/like')
      const key = `${member.memberId}:21`
      if (liked) state.liked.add(key)
      else state.liked.delete(key)
      target.likeCount += liked ? 1 : -1
      return route.abort('connectionreset')
    }
    await button.evaluate(el => el.scrollIntoView({ block: 'center', behavior: 'instant' }))
    const before = await top()
    assert.ok(before > 0 && before < 850)
    const writeStart = writes(state).length
    await button.click()
    const retry = reaction(p, 21).getByRole('button', { name: '댓글 좋아요 상태 다시 확인' })
    await retry.waitFor()
    assert.deepEqual(await ranking(), liked ? [1] : [1, 21])
    const beforeRead = await top()
    const start = state.calls.length
    await retry.click()
    await best.locator('[data-best-comment="21"]').waitFor({ state: liked ? 'visible' : 'detached' })
    await settled(p, 21, liked, liked ? 10 : 9)
    assert.deepEqual(await ranking(), liked ? [1, 21] : [1])
    assert.deepEqual(state.calls.slice(start), [
      { method: 'GET', path: endpoint }, { method: 'GET', path: endpoint },
      { method: 'GET', path: endpoint + '/best' },
    ])
    assert.equal(writes(state).length - writeStart, 1)
    assert.deepEqual(await rows(), loaded)
    assert.equal(await draft.inputValue(), '재확인 중에도 보존할 초안')
    const after = await top()
    console.log(`${width}px ${method}: before=${before}, beforeRead=${beforeRead}, after=${after}`)
    assert.ok(Math.abs(after - before) <= 2 && Math.abs(after - beforeRead) <= 2)
  }
 })
 for (const method of ['PUT', 'DELETE']) await check(`응답이 유실된 ${method}를 정렬 변경으로 복구한다`, async () => {
  const { p, state } = await fixture(member, 22)
  const endpoint = '/api/v1/community/posts/7/comments'
  const liked = method === 'PUT'
  const id = 8
  const target = comments[id - 1]
  const key = `${member.memberId}:${id}`
  const rank = () => p.locator('[data-best-comment]').evaluateAll(nodes => nodes.map(node => Number(node.dataset.bestComment)))
  await settled(p)
  comments[0].likeCount = 12
  target.likeCount = liked ? 9 : 10
  if (!liked) state.liked.add(key)
  await p.locator('#comments-refresh').click()
  await settled(p, id, !liked, target.likeCount)
  await p.locator('[data-best-comment="1"]').waitFor()
  assert.deepEqual(await rank(), liked ? [1] : [1, id])
  const draft = p.locator('#comment-draft')
  await draft.fill('정렬 중에도 남는 초안')
  state.write = route => {
    assert.equal(route.request().method(), method)
    assert.equal(new URL(route.request().url()).pathname, endpoint + '/' + id + '/like')
    if (liked) state.liked.add(key)
    else state.liked.delete(key)
    target.likeCount += liked ? 1 : -1
    return route.abort('connectionreset')
  }
  await btn(p, id).click()
  await reaction(p, id).getByRole('button', { name: '댓글 좋아요 상태 다시 확인' }).waitFor()
  const start = state.calls.length
  await selectSort(p, '최신순', 'LATEST')
  await settled(p, id, liked, target.likeCount)
  assert.deepEqual({
    best: await rank(), label: await btn(p, id).getAttribute('aria-label'),
    alerts: await reaction(p, id).getByRole('alert').count(),
    requests: state.calls.slice(start),
  }, {
    best: liked ? [1, id] : [1], label: liked ? '댓글 좋아요 취소' : '댓글 좋아요 등록',
    alerts: 0, requests: [{ method: 'GET', path: endpoint }, { method: 'GET', path: endpoint + '/best' }],
  })
  assert.deepEqual(writes(state), [{ method, path: endpoint + '/' + id + '/like' }])
  assert.equal(await draft.inputValue(), '정렬 중에도 남는 초안')
  const normal = state.calls.length
  await selectSort(p, '등록순', 'OLDEST')
  assert.deepEqual(state.calls.slice(normal), [{method: 'GET', path: endpoint}])
 })
 await check('실패한 정렬 조회와 대상이 빠진 페이지에서는 복구를 보류하고 수동 새로고침은 베스트를 한 번만 읽는다', async () => {
  const { p, state } = await fixture(member, 22)
  await settled(p)
  comments[0].likeCount = 9
  await p.locator('#comments-refresh').click()
  await settled(p, 1, false, 9)
  const bestReads = () => state.calls.filter(c => c.path.endsWith('/best')).length
  const before = bestReads()
  state.write = route => {
    comments[0].likeCount = 10
    state.liked.add(`${member.memberId}:1`)
    return route.abort('connectionreset')
  }
  await btn(p).click()
  await reaction(p).getByRole('alert').waitFor()
  readFail = true
  await selectSort(p, '최신순', 'LATEST')
  await p.getByRole('button', { name: '댓글 조회 다시 시도' }).waitFor()
  assert.equal(bestReads(), before)
  readFail = false
  await p.getByRole('button', { name: '댓글 조회 다시 시도' }).click()
  await settled(p, 22)
  assert.equal(await reaction(p).count(), 0)
  assert.equal(bestReads(), before)
  await p.getByRole('button', { name: '댓글 더 보기', exact: true }).click()
  await settled(p, 1, true, 10)
  await p.locator('[data-best-comment="1"]').waitFor()
  assert.equal(bestReads(), before + 1)
  state.write = route => {
    comments[0].likeCount = 9
    state.liked.delete(`${member.memberId}:1`)
    return route.abort('connectionreset')
  }
  await btn(p).click()
  await reaction(p).getByRole('alert').waitFor()
  await p.locator('#comments-refresh').click()
  await settled(p, 1, false, 9)
  await p.locator('[data-best-comment="1"]').waitFor({ state: 'detached' })
  await p.waitForLoadState('networkidle')
  assert.equal(await btn(p).getAttribute('aria-label'), '댓글 좋아요 등록')
  assert.equal(await reaction(p).getByRole('alert').count(), 0)
  assert.equal(bestReads(), before + 2)
  assert.deepEqual(writes(state).map(c => c.method), ['PUT', 'DELETE'])
 })
 await check('older refresh response cannot undo a successful like',async()=>{
  const{p,state}=await fixture(member,3);await settled(p);const g=gate();state.get=async(r,u,result)=>{if(u.pathname.endsWith('/comments'))await g.hold();await r.fulfill(result)}
  await p.locator('#comments-refresh').click();await g.reached;await btn(p).click();await settled(p,1,true,1);g.release();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled);await settled(p,1,true,1)
 })
 await check('다음 페이지의 COMMENT_NOT_FOUND 재확인 후 베스트 차순위와 편집 초안을 유지한다', async () => {
  const { p, state } = await fixture(member, 45)
  await settled(p)
  for (const [id, count] of [[40, 15], [21, 13], [2, 12], [42, 10]]) {
    comments[id - 1].likeCount = count
  }
  const endpoint = '/api/v1/community/posts/7/comments'
  const best = p.getByRole('region', { name: '베스트 댓글', exact: true })
  const ranking = () => best.locator('li').evaluateAll(nodes => nodes.map(node => Number(node.dataset.bestComment)))
  await p.locator('#comments-refresh').click()
  await best.locator('[data-best-comment="21"]').waitFor()
  assert.deepEqual(await ranking(), [40, 21, 2])
  const previousBody = await best.locator('[data-best-comment="21"] p').textContent()
  await p.getByRole('button', { name: '댓글 더 보기', exact: true }).click()
  await settled(p, 21, false, 13)
  await p.locator('#comment-edit-button-21').click()
  await p.locator('#comment-edit-21').fill('보관해야 할 편집')
  state.write = route => {
    assert.equal(route.request().method(), 'PUT')
    assert.equal(new URL(route.request().url()).pathname, endpoint + '/21/like')
    comments = comments.filter(c => c.commentId !== 21)
    return route.fulfill({ status: 404, json: failure('COMMENT_NOT_FOUND') })
  }
  await btn(p, 21).click()
  await reaction(p, 21).getByRole('alert').waitFor()
  const start = state.calls.length
  await reaction(p, 21).getByRole('button', { name: '댓글 좋아요 상태 다시 확인' }).click()
  await p.getByText('현재 목록에 없는 댓글을 수정하고 있어요. 입력한 내용은 유지했어요.', { exact: true }).waitFor()
  await p.waitForLoadState('networkidle')
  assert.equal(await p.locator('#comment-edit-21').inputValue(), '보관해야 할 편집')
  assert.equal(await p.getByRole('dialog').count(), 0)
  assert.ok(await p.locator('.community-post-body').isVisible())
  assert.deepEqual(await p.locator('.community-comment-list > li').evaluateAll(nodes => nodes.map(n => n.id)),
    comments.map(c => 'comment-' + c.commentId))
  assert.deepEqual(await ranking(), [40, 2, 42])
  assert.equal(await best.getByText(previousBody, { exact: true }).count(), 0)
  assert.deepEqual(state.calls.slice(start), [
    { method: 'GET', path: endpoint }, { method: 'GET', path: endpoint },
    { method: 'GET', path: endpoint }, { method: 'GET', path: endpoint + '/best' },
  ])
 })
 for(const response of ['success','401','403','404'])await check(`old ${response} ignored after logout and same-member login`,async()=>{
  const{p,state}=await fixture(member,1);await settled(p);const g=gate();state.write=async r=>{await g.hold();await r.fulfill(response==='success'?{json:{postId:7,commentId:1,liked:true,likeCount:99}}:{status:Number(response),json:failure(response==='401'?'AUTHENTICATION_REQUIRED':response==='403'?'COMMENT_LIKE_NOT_ALLOWED':'COMMENT_NOT_FOUND')})}
  await btn(p).click();await g.reached;await logout(p);assert.equal(await btn(p).getAttribute('aria-pressed'),'false');await login(p);await settled(p);const done=p.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/like'));g.release();await(await done).finished();await settled(p);assert.equal(await reaction(p).getByRole('alert').count(),0);assert(await p.getByRole('button',{name:'로그아웃',exact:true}).isVisible())
 })
 await check('previous personalized GET rejected after other-account login',async()=>{
  const{p,state}=await fixture(member,1);await settled(p)
  // Deliver one genuinely stale success despite abort. A cancelled native
  // request has no response event, and proves cancellation rather than ownership.
  await p.evaluate(()=>{
    const original=window.fetch;let first=true
    window.staleLikeReadConsumed=false
    window.fetch=async(input,options)=>{
      if(!first||options?.method!=='GET'||!new URL(String(input),location.href).pathname.endsWith('/comments'))return original(input,options)
      first=false
      const response=await original(input,{...options,signal:undefined}),json=response.json.bind(response)
      response.json=async()=>{try{return await json()}finally{window.staleLikeReadConsumed=true}}
      return response
    }
  })
  const g=gate();let once=true;state.get=async(r,u,result)=>{if(once&&u.pathname.endsWith('/comments')){once=false;result.json.items[0].liked=true;result.json.items[0].likeCount=99;await g.hold()}await r.fulfill(result)}
  await p.locator('#comments-refresh').click();await g.reached;await logout(p);await login(p,'other@example.test');await settled(p);g.release();await p.waitForFunction(()=>window.staleLikeReadConsumed);await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));await settled(p)
 })
 await check('unknown personalized state cannot send a PUT; later confirmed state determines DELETE',async()=>{
  const{p,state}=await fixture(member,1);await settled(p);await logout(p);const g=gate();state.liked.add('123:1');comments[0].likeCount=1;state.get=async(r,u,result)=>{if(u.pathname.endsWith('/comments'))await g.hold();await r.fulfill(result)}
  await login(p);await g.reached;await btn(p).click();assert.equal(writes(state).length,0);g.release();await settled(p,1,true,1);await btn(p).click();await settled(p,1,false,0);assert.equal(writes(state)[0].method,'DELETE')
 })
 await check('append overlaps mutation: existing state retained and no duplicate rows',async()=>{
  const{p,state}=await fixture(member,45);await settled(p);const g=gate();state.get=async(r,u,result)=>{if(u.searchParams.has('afterId'))await g.hold();await r.fulfill(result)}
  await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await g.reached;await btn(p).click();await settled(p,1,true,1);const done=p.waitForResponse(r=>new URL(r.url()).searchParams.has('afterId'));g.release();await(await done).finished();await settled(p,40);await settled(p,1,true,1);assert.equal(await p.locator('.community-comment-list > li').count(),40)
 })
 await check('late mutation after leaving cannot alter new screen; return fetches confirmed server state',async()=>{
  const{p,state}=await fixture(member,1);await settled(p);const g=gate();state.write=async r=>{await g.hold();comments[0].likeCount=9;state.liked.add('123:1');await r.fulfill({json:{postId:7,commentId:1,liked:true,likeCount:9}})}
  await btn(p).click();await g.reached;await p.locator('.community-back').click();await p.waitForURL(u=>!u.pathname.endsWith('/7'));const done=p.waitForResponse(r=>r.url().endsWith('/like')&&r.request().method()==='PUT');g.release();await(await done).finished();assert.equal(await p.locator('[data-comment-like]').count(),0);await p.goBack();await settled(p,1,true,9)
 })
 for(const width of [1440,390,320])await check(`layout and keyboard ${width}`,async()=>{
  const{p}=await fixture(member,3);await settled(p);await p.setViewportSize({width,height:900});await btn(p).focus();await p.keyboard.press('Enter');await settled(p,1,true,1);assert(await btn(p).evaluate(e=>e===document.activeElement));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert((await btn(p).ariaSnapshot()).includes('pressed'));assert((await btn(p).getAttribute('aria-describedby')).includes('count'));await fs.writeFile(`${output}/likes-${width}-a11y.txt`,await reaction(p).ariaSnapshot());await reaction(p).scrollIntoViewIfNeeded();await p.screenshot({path:`${output}/likes-${width}.png`})
 })
} finally {await fs.writeFile(`${output}/results.json`,JSON.stringify({kind:'controlled mock API; no real DB changes',results},null,2));await browser.close();server.close()}
