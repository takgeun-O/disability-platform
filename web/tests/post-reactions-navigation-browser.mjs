// Controlled browser + SSR API; no real account, token or DB writes.
import http from 'node:http'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8464'
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
const reaction = (p, id = 1000) => p.locator(`[data-post-like="${id}"]`)
const button = (p, id = 1000) => reaction(p, id).getByRole('button', { name: /좋아요/ })
async function settle(p, id = 1000, liked = false, count = 2) {
  await p.waitForFunction(({ id, liked, count }) => {
    const el = document.querySelector(`[data-post-like="${id}"] button`)
    return el && el.parentElement.dataset.likeReady === 'true' && el.getAttribute('aria-pressed') === String(liked) && el.getAttribute('aria-disabled') === 'false' && el.textContent.includes(`좋아요 ${count.toLocaleString('ko-KR')}`)
  }, { id, liked, count })
}
async function fixture({ width = 1440, anonymous = false, javascript = true, count = 45, path = '/community' } = {}) {
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
  if (javascript) { await p.waitForFunction(() => document.querySelector('.iyum-header')); await p.locator(path === "/mypage" ? ".mypage-post-list h3 a" : ".feed-posts h3 a").first().waitFor() }
  return { ctx, p, state, response }
}
async function check(name, run) {
  try { await run(); results.push({ name, pass: true }); console.log('PASS', name) }
  catch (e) { results.push({ name, pass: false, error: e.message }); console.log('FAIL', name, e.message); process.exitCode = 1; const p = browser.contexts().at(-1)?.pages()[0]; if (p) await p.screenshot({ path: `${out}/failure-${results.length}.png` }).catch(() => {}) }
  finally { for (const c of browser.contexts()) await c.close() }
}
try {
  for (const width of [1440,390,320]) for (const path of ['/community','/mypage','/community/topics/hearing-aid']) await check(`좋아요 버튼과 댓글 이동 ${path} ${width}`, async () => {
    const {p,state} = await fixture({width,path})
    const row = p.locator(path === '/mypage' ? '.mypage-post-list li' : '[data-feed-id]').first()
    await row.locator('[data-post-like] button').waitFor()
    assert.equal(await row.getByRole('button', {name:/좋아요/}).count(),1)
    assert.match(await row.locator('[data-post-like]').innerText(),/좋아요 2/)
    let link=row.locator('.post-comment-link')
    if (path === '/mypage') {
      assert.equal(await link.getAttribute('aria-expanded'),'false')
      await link.click();await row.locator('.mypage-inline-comments:not([hidden])').waitFor()
      assert.equal(await link.getAttribute('aria-expanded'),'true')
      assert(await link.evaluate(e=>e.getBoundingClientRect().height>=44))
      link=row.locator('h3 a')
    }
    await link.focus()
    const href=await link.getAttribute('href'); assert(path==='/mypage'?href.includes('from=%2Fmypage'):href.endsWith('#comments-title'))
    if(path!=='/mypage')assert(await link.evaluate(e=>e.getBoundingClientRect().height>=44))
    assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
    await p.screenshot({path:`${out}/list-${path === '/mypage' ? 'mine' : path.includes('/topics/') ? 'topic' : 'public'}-${width}.png`})
    await p.keyboard.press('Enter'); await p.locator('.community-post-body').waitFor()
    if(path!=='/mypage'){await p.waitForURL(u=>u.hash==='#comments-title');await p.waitForFunction(()=>document.activeElement?.id==='comments-title');await p.waitForFunction(()=> {const r=document.getElementById('comments-title').getBoundingClientRect();return r.top>=0 && r.bottom<innerHeight})}
    await settle(p); await button(p).click(); await settle(p,1000,true,3)
    assert.equal(await button(p).evaluate(e=>getComputedStyle(e).color),'rgb(196, 63, 80)')
    await p.screenshot({path:`${out}/detail-${path === '/mypage' ? 'mine' : path.includes('/topics/') ? 'topic' : 'public'}-${width}.png`})
    const label=path==='/mypage'?'마이페이지로 돌아가기':path.includes('/topics/')?'보청기 게시글 목록으로':'게시글 목록으로'
    for (const name of [`← ${label}`,label]) assert.equal(await p.getByRole('link',{name,exact:true}).getAttribute('href'),path)
    await p.getByRole('link',{name:`← ${label}`,exact:true}).click(); await p.waitForURL(u=>u.pathname===path)
    await p.waitForFunction(()=>document.querySelector('[data-post-like]')?.textContent.includes('좋아요 3'))
    assert.equal(await row.locator('[data-post-like] button').getAttribute('aria-pressed'),'true')
    assert.equal(state.calls.filter(c=>c.path.endsWith('/like')).length,1)
  })
  await check('noJS comments remains a native anchor and likes text',async()=>{
    const {p}=await fixture({javascript:false,anonymous:true})
    assert(await p.locator('.post-like-count').first().isVisible())
    await p.locator('.post-comment-link').first().click()
    await p.waitForURL(u=>u.hash==='#comments-title')
    assert(await p.locator('#comments-title').isVisible())
  })
} finally {
  await fs.writeFile(`${out}/results.json`, JSON.stringify({results},null,2)); await browser.close(); await new Promise(r=>server.close(r))
}
