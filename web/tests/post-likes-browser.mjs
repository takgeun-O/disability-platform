// Controlled browser + SSR API; no real account, token or DB writes.
import http from 'node:http'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8464'
const apiOrigin = 'http://127.0.0.1:8082'
const out = process.env.LIKE_ARTIFACT_DIR ?? '/private/tmp/iyum-post-like-browser'
await fs.mkdir(out, { recursive: true })
const stamp = '2026-10-25T00:00:00.123456Z'
const member = { memberId: 321, nickname: '검토회원', email: 'review@example.test', role: 'USER' }
const make = id => ({ visibility: "VISIBLE", id, type: 'QUESTION', topic: 'HEARING_AID', title: `[검토 예시] 경험 나누기 ${id}`, content: '게시글 본문입니다.\n'.repeat(20), contentPreview: '같은 경험을 가진 사람들과 이야기를 나누어 보세요.', author: { memberId: 321, nickname: '검토회원' }, createdAt: stamp, updatedAt: stamp, commentCount: 3, likeCount: 2, liked: false })
let posts = [], serverCalls = []
const failure = code => ({ code, message: 'test only', fieldErrors: [] })
function read(u, who = null, liked = new Set()) {
  if (u.pathname.endsWith('/comments')) return { json: { items: [], hasNext: false, nextCursor: null } }
  const rows = posts.map(p => ({ ...p, liked: !!who && liked.has(`${who.memberId}:${p.id}`) }))
  if (u.pathname.endsWith('/feed') || u.pathname === '/api/v1/members/me/posts') {
    const size = Number(u.searchParams.get('size') || 20), filtered = rows.filter(p => (!u.searchParams.has('afterId') || p.id < Number(u.searchParams.get('afterId'))) && (!u.searchParams.get('topic') || p.topic === u.searchParams.get('topic')) && (!u.searchParams.get('type') || p.type === u.searchParams.get('type')))
    const items = filtered.slice(0, size), hasNext = filtered.length > size
    return { json: { items, hasNext, nextCursor: hasNext ? { createdAt: stamp, postId: items.at(-1).id } : null } }
  }
  const p = rows.find(p => p.id === Number(u.pathname.split('/').at(-1)))
  if (!p) return { status: 404, json: failure('POST_NOT_FOUND') }
  const detail = { ...p }; delete detail.commentCount; delete detail.contentPreview
  return { json: detail }
}
const server = http.createServer((req, res) => {
  assert(!req.headers.cookie); assert(!req.headers.authorization)
  const u = new URL(req.url, 'http://mock'); serverCalls.push({ path: u.pathname, query: u.search })
  const reply = read(u); res.writeHead(reply.status ?? 200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(reply.json))
})
await new Promise(r => server.listen(8858, '127.0.0.1', r))
const browser = await chromium.launch({ channel: 'chrome', headless: true }), results = []
const gate = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const reaction = (p, id = 1000) => p.locator(`[data-post-like="${id}"]`)
const button = (p, id = 1000) => reaction(p, id).getByRole('button', { name: /좋아요/ })
async function settle(p, id = 1000, liked = false, count = 2) {
  await p.waitForFunction(({ id, liked, count }) => {
    const el = document.querySelector(`[data-post-like="${id}"] button`)
    return el && el.parentElement.dataset.likeReady === 'true' && el.getAttribute('aria-pressed') === String(liked) && el.getAttribute('aria-disabled') === 'false' && el.textContent.includes(`좋아요 ${count.toLocaleString('ko-KR')}`)
  }, { id, liked, count })
}
async function fixture({ width = 1440, anonymous = false, javascript = true, count = 45, path = '/community/posts/1000?from=%2Fcommunity' } = {}) {
  posts = Array.from({ length: count }, (_, i) => make(1000 - i)); serverCalls = []
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, javaScriptEnabled: javascript, reducedMotion: 'no-preference' }), p = await ctx.newPage(); p.setDefaultTimeout(10000)
  const state = { member: anonymous ? null : { ...member }, liked: new Set(), calls: [], write: null, get: null, me: null }
  await p.route(u => u.pathname.startsWith('/api/v1/'), async r => {
    const req = r.request(), u = new URL(req.url()), method = req.method(); state.calls.push({ path: u.pathname, query: u.search, method })
    assert.equal(u.searchParams.has('viewerId'), false)
    if (u.pathname.endsWith('/auth/me')) return state.me ? state.me(r) : r.fulfill(state.member ? { json: state.member } : { status: 401, json: failure('AUTHENTICATION_REQUIRED') })
    if (u.pathname.endsWith('/csrf')) return r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-only' } })
    if (u.pathname.endsWith('/logout')) { state.member = null; return r.fulfill({ status: 204 }) }
    if (u.pathname.endsWith('/login')) { state.member = { ...member, memberId: req.postDataJSON().email.startsWith('other') ? 322 : 321 }; return r.fulfill({ json: state.member }) }
    if (u.pathname === '/api/v1/members/me') return r.fulfill({ json: { ...state.member, createdAt: stamp } })
    if (u.pathname.endsWith('/activity-summary')) return r.fulfill({ json: { postCount: posts.length, commentCount: 0 } })
    if (method === 'GET') return state.get ? state.get(r, u) : r.fulfill(read(u, state.member, state.liked))
    if (u.pathname.endsWith('/like')) {
      assert.equal(req.postData(), null); assert.equal(req.headers()['x-csrf-token'], 'fixture-only')
      const id = Number(u.pathname.split('/').at(-2)), post = posts.find(p => p.id === id)
      if (state.write) return state.write(r, id, method)
      const key = `${state.member.memberId}:${id}`
      if (method === 'PUT' && !state.liked.has(key)) { state.liked.add(key); post.likeCount++ }
      if (method === 'DELETE' && state.liked.has(key)) { state.liked.delete(key); post.likeCount-- }
      return r.fulfill({ json: { postId: id, liked: state.liked.has(key), likeCount: post.likeCount } })
    }
    return r.fulfill({ status: 500, json: failure('UNEXPECTED') })
  })
  const response = await p.goto(base + path)
  if (javascript) { await p.waitForFunction(() => document.querySelector('.iyum-header')); if (path.startsWith("/community/posts/1000")) await settle(p); else await listState(p) }
  return { ctx, p, state, response }
}
async function login(p, email = 'review@example.test') {
  await p.locator('input[name=email]').fill(email); await p.locator('input[name=password]').fill('Fixture-only-1!')
  await p.locator('main').getByRole('button', { name: '로그인', exact: true }).click()
}
async function check(name, run) {
  try { await run(); results.push({ name, pass: true }); console.log('PASS', name) }
  catch (e) { results.push({ name, pass: false, error: e.message }); console.log('FAIL', name, e.message); process.exitCode = 1; const p = browser.contexts().at(-1)?.pages()[0]; if (p) await p.screenshot({ path: `${out}/failure-${results.length}.png` }).catch(() => {}) }
  finally { for (const c of browser.contexts()) await c.close() }
}
function row(p,id=1000) { return p.locator(`[data-feed-id="${id}"], [data-member-post-id="${id}"]`) }
async function listState(p,id=1000,liked=false,count=2) {
  const r=row(p,id), label=r.locator('.post-like-count')
  await p.waitForFunction(({id,liked,count})=>{
    const e=document.querySelector(`[data-feed-id="${id}"] .post-like-count, [data-member-post-id="${id}"] .post-like-count`)
    return e && e.dataset.liked===String(liked) && e.textContent.includes(`좋아요 ${count.toLocaleString('ko-KR')}`)
  },{id,liked,count})
  assert.equal(await r.getByRole('button',{name:/좋아요/}).count(),0)
  assert.equal(await label.getAttribute('tabindex'),null); assert.equal(await label.getAttribute('aria-pressed'),null)
  assert.equal(await label.evaluate(e=>e.tabIndex),-1)
  const ax=await label.ariaSnapshot(); assert.equal(ax.includes('내가 좋아요한 게시글'),liked)
  assert(ax.includes(`좋아요 ${count.toLocaleString('ko-KR')}`))
  return label
}
async function openPost(p,id=1000) {await row(p,id).locator('h3 a').click();await p.locator('.community-post-body').waitFor()}
async function back(p,path='/community') {
 const name=path==='/mypage'?'← 마이페이지로 돌아가기':'← 게시글 목록으로'
 const link=p.getByRole('link',{name,exact:true});assert.equal(await link.getAttribute('href'),path);await link.click();await p.waitForURL(u=>u.pathname===path)
}

try {
  await check('anonymous SSR/noJS readonly count; no cookies to SSR', async()=>{
    const {p,response}=await fixture({javascript:false,anonymous:true,path:'/community'})
    assert.equal(response.status(),200);await listState(p);await p.screenshot({path:`${out}/nojs.png`})
  })
  await check('guest detail prompt, login return position, no auto-register',async()=>{
    const {p,state}=await fixture({anonymous:true});await button(p).scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY)
    await button(p).click();await p.getByRole('dialog').getByRole('button',{name:'로그인하기',exact:true}).click()
    await p.waitForURL(u=>u.pathname==='/login');await login(p);await p.waitForURL(u=>u.pathname==='/community/posts/1000');await settle(p)
    await p.waitForFunction(y=>Math.abs(scrollY-y)<5,y);assert.equal(state.calls.filter(c=>c.path.endsWith('/like')).length,0)
  })
  await check('PUT DELETE PUT, readonly public/own count and accessible selected state',async()=>{
    const {p,state}=await fixture()
    for(const [liked,count] of [[true,3],[false,2],[true,3]]) {await button(p).click();await settle(p,1000,liked,count)}
    assert.deepEqual(state.calls.filter(c=>c.path.endsWith('/like')).map(c=>c.method),['PUT','DELETE','PUT'])
    await back(p);await listState(p,1000,true,3)
    await fs.writeFile(`${out}/selected-a11y.txt`,await row(p).ariaSnapshot());await p.screenshot({path:`${out}/selected-list.png`})
    await p.getByRole('link',{name:'마이페이지',exact:true}).first().click();await listState(p,1000,true,3)
    await openPost(p);await settle(p,1000,true,3);await button(p).click();await settle(p,1000,false,2);await back(p,'/mypage');await listState(p)
    await fs.writeFile(`${out}/readonly-a11y.txt`,await row(p).ariaSnapshot())
    assert(state.calls.some(c=>c.path==='/api/v1/members/me/posts'))
  })
  await check('duplicate blocked, other post independent through navigation, server count 17',async()=>{
    const {p,state}=await fixture();const requested=gate(),release=gate(),completed=gate()
    state.write=async(r,id)=>{
      if(id===1000){requested.resolve();await release.promise}
      // Subsequent feed GETs must observe the same confirmed server state as PUT.
      // Leaving the mock at 2/false can overwrite 17/true between DOM and AX checks.
      posts.find(post=>post.id===id).likeCount=17
      state.liked.add(`${state.member.memberId}:${id}`)
      await r.fulfill({json:{postId:id,liked:true,likeCount:17}})
      if(id===1000)completed.resolve()
    }
    await button(p).click();await requested.promise;await button(p).dispatchEvent('click');await back(p);await openPost(p,999);await settle(p,999)
    await button(p,999).click();await settle(p,999,true,17);assert.equal(state.calls.filter(c=>c.path==='/api/v1/community/posts/1000/like').length,1)
    release.resolve();await completed.promise;await back(p);await listState(p,1000,true,17)
  })
  await check('old detail GET after confirmed mutation does not overwrite',async()=>{
    const {p,state}=await fixture();await back(p);await listState(p);const requested=gate(),release=gate(),completed=gate()
    state.get=async(r,u)=>{const reply=read(u,state.member,state.liked);if(u.pathname==='/api/v1/community/posts/1000'){requested.resolve();await release.promise}await r.fulfill(reply);if(u.pathname==='/api/v1/community/posts/1000')completed.resolve()}
    await openPost(p);await requested.promise;await button(p).click();await settle(p,1000,true,3);release.resolve();await completed.promise
    await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));await settle(p,1000,true,3)
  })
  for (const [code, status] of [['CSRF_TOKEN_INVALID', 403], ['POST_LIKE_NOT_ALLOWED', 403], ['SERVER_ERROR', 500], ['INVALID_SUCCESS', 200], ['NETWORK', 0]]) await check(`error ${code}, no replay, manual recovery`, async () => {
    const { p, state } = await fixture()
    state.write = r => status === 0 ? r.abort('failed') : r.fulfill({ status, json: status === 200 ? { postId: 1000, liked: 'yes', likeCount: -1 } : failure(code) })
    await button(p).click(); await reaction(p).getByRole('alert').waitFor(); assert.equal(await button(p).getAttribute('aria-pressed'), 'false'); assert.equal(await button(p).getAttribute('aria-disabled'), 'false')
    assert.equal(state.calls.filter(c => c.path.endsWith('/like')).length, 1)
    state.write = null
    if ([0, 200, 500].includes(status)) { await button(p).click(); await p.waitForFunction(() => !document.querySelector('[data-post-like="1000"] [role=alert]')); assert.equal(state.calls.filter(c => c.path.endsWith('/like')).length, 1) }
    await button(p).click(); await settle(p, 1000, true, 3)
  })
  await check('current 401 expires session and prompts login', async () => {
    const { p, state } = await fixture(); state.write = r => r.fulfill({ status: 401, json: failure('AUTHENTICATION_REQUIRED') })
    await button(p).click(); await p.getByRole('dialog').waitFor(); assert.equal(await button(p).getAttribute('aria-pressed'), 'false')
    assert(await p.getByRole('dialog').getByRole('button', { name: '로그인하기', exact: true }).isVisible())
  })

  await check('404 informs; return rereads and removes missing list item',async()=>{
    const {p,state}=await fixture({path:'/community'});await openPost(p);await settle(p);const readsBefore=state.calls.filter(c=>c.path.endsWith('/feed')).length+serverCalls.filter(c=>c.path.endsWith('/feed')).length;state.write=r=>{posts=posts.filter(x=>x.id!==1000);return r.fulfill({status:404,json:failure('POST_NOT_FOUND')})}
    await button(p).click();await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).waitFor()
    // SSR can briefly show fresh rows before the browser restores its stale cache.
    // Register BEFORE returning, and wait for the browser API body before checking DOM.
    const feedResponse = p.waitForResponse(response => {
      const url = new URL(response.url())
      return url.origin === apiOrigin && response.request().method() === 'GET'
        && url.pathname === '/api/v1/community/posts/feed'
        && ['type', 'topic', 'afterCreatedAt', 'afterId'].every(key => !url.searchParams.has(key))
    })
    await back(p)
    const response = await feedResponse
    assert.equal(response.status(), 200)
    assert.equal(await response.finished(), null)

    await p.waitForFunction(()=>document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false' && document.querySelector('[data-feed-id="999"]') && !document.querySelector('[data-feed-id="1000"]'));assert.equal(await row(p).count(),0);assert(state.calls.filter(c=>c.path.endsWith('/feed')).length+serverCalls.filter(c=>c.path.endsWith('/feed')).length>readsBefore)
  })
  await check('404 return waits for gated feed completion, not cached row 999',async()=>{
    const {p,state}=await fixture({path:'/community'});await openPost(p);await settle(p)
    const requested=gate(),release=gate()
    const readsBefore=state.calls.filter(c=>c.path.endsWith('/feed')).length
    state.write=r=>{posts=posts.filter(x=>x.id!==1000);return r.fulfill({status:404,json:failure('POST_NOT_FOUND')})}
    state.get=async(r,u)=>{if(u.pathname.endsWith('/feed')){requested.resolve();await release.promise}return r.fulfill(read(u,state.member,state.liked))}
    await button(p).click();await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).waitFor();await back(p)
    try {
      await requested.promise
      assert(state.calls.filter(c=>c.path.endsWith('/feed')).length>readsBefore)
      assert.equal(await p.evaluate(()=>document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false' && !document.querySelector('[data-feed-id="1000"]')),false)
    } finally { release.resolve() }
    await p.waitForFunction(()=>document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false' && document.querySelector('[data-feed-id="999"]') && !document.querySelector('[data-feed-id="1000"]'))
    assert.equal(await row(p).count(),0)
  })
  for(const next of ['same','other']) for(const result of ['success','401']) await check(`late ${result} after ${next} login incl old finally`,async()=>{
    const {p,state}=await fixture();const requested=gate(),release=gate(),completed=gate(),freshRequested=gate(),freshRelease=gate()
    await p.evaluate(()=>window.__likesDocument='preserved')
    state.write=async r=>{requested.resolve();await release.promise;await r.fulfill(result==='401'?{status:401,json:failure('AUTHENTICATION_REQUIRED')}:{json:{postId:1000,liked:true,likeCount:900}});completed.resolve()}
    await button(p).click();await requested.promise
    await p.getByRole('button',{name:'로그아웃',exact:true}).first().click();await p.getByRole('link',{name:'로그인',exact:true}).first().click();await login(p,next==='same'?'review@example.test':'other@example.test')
    await p.waitForURL(u=>u.pathname!=='/login');if(new URL(p.url()).pathname!=='/community/posts/1000'){await p.getByRole('link',{name:'커뮤니티',exact:true}).first().click();await listState(p);await openPost(p)}await settle(p)
    state.write=async r=>{freshRequested.resolve();await freshRelease.promise;return r.fulfill({json:{postId:1000,liked:true,likeCount:3}})}
    await button(p).click();await freshRequested.promise;release.resolve();await completed.promise
    await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
    assert.equal(await p.evaluate(()=>window.__likesDocument),'preserved');assert.equal(await button(p).getAttribute('aria-disabled'),'true');assert.equal(await p.getByRole('dialog').count(),0)
    assert.equal(await reaction(p).getByRole('alert').count(),0);freshRelease.resolve();await settle(p,1000,true,3)
  })
  for(const result of ['success','401']) await check(`late ${result} after logout alone cannot restore selected description`,async()=>{
    const{p,state}=await fixture();const requested=gate(),release=gate(),completed=gate()
    state.write=async r=>{requested.resolve();await release.promise;await r.fulfill(result==='401'?{status:401,json:failure('AUTHENTICATION_REQUIRED')}:{json:{postId:1000,liked:true,likeCount:900}});completed.resolve()}
    await button(p).click();await requested.promise;await p.getByRole('button',{name:'로그아웃',exact:true}).first().click()
    await p.getByRole('link',{name:'로그인',exact:true}).first().waitFor();release.resolve();await completed.promise
    await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
    await settle(p);assert.equal(await p.getByRole('dialog').count(),0);await back(p);await listState(p)
  })
  await check('logout and other account remove selected a11y state without document reload',async()=>{
    const{p}=await fixture();await button(p).click();await settle(p,1000,true,3);await back(p);await listState(p,1000,true,3)
    await p.getByRole('button',{name:'로그아웃',exact:true}).first().click();await listState(p,1000,false,3)
    await p.getByRole('link',{name:'로그인',exact:true}).first().click();await login(p,'other@example.test');await p.waitForURL(u=>u.pathname!=='/login')
    if(new URL(p.url()).pathname!=='/community')await p.getByRole('link',{name:'커뮤니티',exact:true}).first().click();await listState(p,1000,false,3)
  })
  await check('pending detail -> list -> same detail lock and late success',async()=>{
    const{p,state}=await fixture();const requested=gate(),release=gate()
    state.write=async r=>{requested.resolve();await release.promise;return r.fulfill({json:{postId:1000,liked:true,likeCount:17}})}
    await button(p).click();await requested.promise;await back(p);await openPost(p)
    assert.equal(await button(p).getAttribute('aria-disabled'),'true');await button(p).dispatchEvent('click');assert.equal(state.calls.filter(c=>c.path.endsWith('/like')).length,1)
    release.resolve();await settle(p,1000,true,17)
  })
  for (const width of [1440, 390]) await check(`60 loaded posts/detail toggle/back preserves rows and position ${width}`, async () => {
    const { p } = await fixture({ width, count: 65, path: '/community' })
    for (const count of [40, 60]) { await p.getByRole('button', { name: '게시글 더 보기', exact: true }).click(); await p.waitForFunction(n => document.querySelectorAll('[data-feed-id]').length >= n, count) }
    const row = p.locator('[data-feed-id="950"]'); await row.evaluate(e => scrollBy(0, e.getBoundingClientRect().top - 120))
    const y = await p.evaluate(() => scrollY), count = await p.locator('[data-feed-id]').count()
    await row.locator('h3 a').click(); await p.locator('.community-post-body').waitFor(); assert.equal(await p.evaluate(() => scrollY), 0)
    await settle(p, 950); await button(p, 950).click(); await settle(p, 950, true, 3)
    await p.goBack(); await p.waitForFunction(n => document.querySelectorAll('[data-feed-id]').length === n, count); await p.waitForFunction(y => Math.abs(scrollY - y) < 12, y); await listState(p, 950, true, 3)
  })

  for(const width of [1440,390,320]) for(const path of ['/community','/mypage']) await check(`readonly a11y, layout and detail key action ${path} ${width}`,async()=>{
    const {p,state}=await fixture({width,path});posts[0].title='긴 제목 '.repeat(20);posts[0].likeCount=Number.MAX_SAFE_INTEGER
    await p.reload();await listState(p,1000,false,Number.MAX_SAFE_INTEGER);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
    const link=row(p).locator('.post-comment-link');await link.focus();await p.keyboard.press('Shift+Tab');assert(!await row(p).locator('.post-like-count').evaluate(e=>e.contains(document.activeElement)))
    await p.screenshot({path:`${out}/list-${path==='/mypage'?'mine':'public'}-${width}.png`})
    await openPost(p);await settle(p,1000,false,Number.MAX_SAFE_INTEGER);const b=button(p);state.write=r=>r.fulfill({json:{postId:1000,liked:true,likeCount:12}});await b.focus();await p.keyboard.press('Enter');await settle(p,1000,true,12);assert(await b.evaluate(e=>e===document.activeElement));assert.equal(await b.evaluate(e=>getComputedStyle(e).color),'rgb(196, 63, 80)');await p.screenshot({path:`${out}/detail-${path==='/mypage'?'mine':'public'}-${width}.png`})
  })
} finally {
  await fs.writeFile(`${out}/results.json`,JSON.stringify({results,serverCalls},null,2));await browser.close();await new Promise(r=>server.close(r))
}
