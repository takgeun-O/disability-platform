import { checkCommentAuthTypography } from './helpers/comment-auth-typography.mjs'
// Production Next + controlled mock API. This does not validate real Spring CORS.
import http from "node:http"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const { isCommentPage } = await loadTypeScript("../src/lib/community-comments.ts", import.meta.url)
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8451",
  api = process.env.API_BASE_URL ?? "http://127.0.0.1:8082"
const output =
  process.env.COMMUNITY_ARTIFACT_DIR ?? "/private/tmp/iyum-comments-browser"
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
let listCountOverride = null, ssrCommentRequests = 0
let comments = [],
  readFail = false
function read(url) {
  if (url.pathname === "/api/v1/community/posts/feed") {
    const legacy = read(new URL(url.href.replace("/posts/feed", "/posts")))
    return { ...legacy, json: { items: legacy.json.items.map(p => ({...p, contentPreview: p.content})), hasNext: false, nextCursor: null } }
  }

  if (url.pathname.endsWith("/comments")) {
    if (readFail) return { status: 503, json: failure("SERVER_ERROR") }
    const after = Number(url.searchParams.get("afterId") ?? 0)
    const rest = comments.filter((c) => c.commentId > after),
      items = rest.slice(0, 20),
      hasNext = rest.length > 20
    const last = items.at(-1)
    return {
      status: 200,
      json: {
        items,
        hasNext,
        nextCursor: hasNext
          ? { createdAt: last.createdAt, commentId: last.commentId, sort: "OLDEST", likeCount: null }
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
let ssrGate = null, ssrReadReached = false
const server = http.createServer(async (req, res) => {
  if (req.url.includes("/comments")) {
    assert.equal(req.headers.cookie, undefined)
    assert.equal(req.headers.authorization, undefined)
    ssrCommentRequests++
    ssrReadReached = true
    if (ssrGate) await ssrGate
  }
  const result = read(new URL(req.url, "http://mock"))
  res.writeHead(result.status, { "Content-Type": "application/json" })
  res.end(JSON.stringify(result.json))
})
await new Promise((r) => server.listen(8856, "127.0.0.1", r))
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
  const state = { identity, calls: [], csrf: null, write: null, get: null }
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
      if(u.pathname.endsWith('/login')) { state.identity=state.loginMember??member; return r.fulfill({json:state.identity}) }
      if (method === "GET")
        return state.get ? state.get(r, u) : r.fulfill(read(u))
      assert.equal(req.headers()["x-csrf-token"], "test-only")
      if (state.write) return state.write(r)
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
                contentUpdatedAt: c.content === req.postDataJSON().content ? c.contentUpdatedAt : "2026-10-18T01:00:00Z",
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
  // Observe consumed JSON, not a response event that an aborted fetch may never emit.
  await ctx.addInitScript(() => {
    window.commentReads = []
    const original = window.fetch
    window.fetch = async (input, options) => {
      if (!new URL(String(input), location.href).pathname.endsWith('/comments') || options?.method !== 'GET') return original(input, options)
      const record = { consumed: false, aborted: false }
      window.commentReads.push(record)
      options.signal?.addEventListener('abort', () => { record.aborted = true })
      const response = await original(input, options)
      const json = response.json.bind(response)
      response.json = async () => { try { return await json() } finally { record.consumed = true } }
      return response
    }
  })
  try {
  await p.goto(base + "/community/posts/7", { waitUntil: 'domcontentloaded' })
  if (identity) {
    await p.getByRole("button", { name: "댓글 등록", exact: true }).waitFor()
    assert.equal(await p.locator('#comment-draft').getAttribute('placeholder'), '댓글로 이야기를 나눠보세요')
  }
  else await p.getByPlaceholder("로그인 후 댓글을 남길 수 있어요", {exact:true}).waitFor()
  await p.waitForFunction(() => window.commentReads.some(r => r.consumed) && document.querySelector("#comments-refresh")?.disabled === false)
  // Geometry checks must run after the rendered fonts settle, not merely after
  // response delivery. This is a resource/state wait, not a time allowance.
  await p.evaluate(async () => { await document.fonts.ready; await new Promise(requestAnimationFrame) })
  return { ctx, p, state }
  } catch (error) { await ctx.close(); throw error }
}
// Explicit focused run; the default still runs the entire suite.
const controlsOnly = process.env.COMMENT_CONTROLS_ONLY === '1'
async function commentControlsGeometry(p) {
  return p.evaluate(() => {
    const result={viewport:innerWidth,documentWidth:document.documentElement.scrollWidth,scrollY,fonts:document.fonts.status}
    const selectors={like:'#comment-1 [data-comment-like] button',edit:'#comment-edit-button-1',del:'#comment-1 .community-comment-tools button:last-child',toolbar:'#comment-1 .community-comment-toolbar',tools:'#comment-1 .community-comment-tools'}
    for(const [name,selector] of Object.entries(selectors)){
      const el=document.querySelector(selector),rect=el.getBoundingClientRect(),style=getComputedStyle(el)
      result[name]={x:rect.x,y:rect.y,width:rect.width,height:rect.height,documentY:rect.y+scrollY,text:el.textContent,borderTopWidth:style.borderTopWidth,display:style.display,gap:style.gap,alignItems:style.alignItems,gridTemplateColumns:style.gridTemplateColumns,flexWrap:style.flexWrap,minHeight:style.minHeight,font:style.font}
    }
    return result
  })
}
async function settleCommentControls(p,width) {
  await p.setViewportSize({width,height:900})
  await p.locator('#comment-edit-button-1').waitFor()
  // A resize may activate additional font faces even after the fixture's fonts.ready.
  // Use real rendering frames here, before installing any virtual clock.
  await p.waitForFunction(expected=>{
    const elements=[...document.querySelectorAll('#comment-1 .community-comment-toolbar, #comment-1 button')]
    const signature=JSON.stringify(elements.map(e=>e.getBoundingClientRect().toJSON()))+scrollY
    const last=window.__commentControlsGeometry
    const stable=last?.signature===signature?last.stable+1:0
    window.__commentControlsGeometry={signature,stable}
    return innerWidth===expected && document.fonts.status==='loaded' && elements.length>=4 && stable>=2 && document.querySelector('#comments-refresh')?.disabled===false
  },width,{polling:'raf',timeout:8000})
  await p.evaluate(()=>{delete window.__commentControlsGeometry})
}

async function check(name, fn) {
  if (controlsOnly && !/^(compact comment controls layout|refresh toast lifecycle)/.test(name)) return
  try {
    await fn()
    results.push({ name, status: "passed" })
    console.log("PASS", name)
  } catch (e) {
    results.push({ name, status: "failed", message: e.message })
    console.error("FAIL", name, e.message)
    for (const context of browser.contexts()) {
      for (const page of context.pages()) {
        await page.screenshot({path: `${output}/failure-${results.length}.png`, fullPage:true}).catch(() => {})
      }
    }
    process.exitCode = 1
  } finally {
    // A failed assertion must not leak a context into following scenarios.
    await Promise.all(browser.contexts().map(context => context.close()))
    await fs.writeFile(output + "/results.json", JSON.stringify({kind:"mock",results}, null, 2))
  }
}
async function until(fn) {
  for (let n = 0; n < 100; n++) {
    if (fn()) return
    await new Promise((r) => setTimeout(r, 30))
  }
  throw Error("controlled request was not reached")
}
// Controlled transport deliberately ignores abort; consumed JSON + render boundary
// distinguishes request cancellation from stale-response ownership protection.
async function ignoreReadAbort(p) {
  await p.evaluate(() => {
    const original = window.fetch
    window.fetch = (input, options) => original(input,
      new URL(String(input), location.href).pathname.endsWith('/comments') && options?.method === 'GET'
        ? { ...options, signal: undefined } : options)
  })
}
function holdRead(state) {
  let release, reached
  const ready = new Promise(r => { reached = r })
  const pending = new Promise(r => { release = r })
  state.get = async (route, url) => {
    if (!url.pathname.endsWith('/comments')) return route.fulfill(read(url))
    state.get = null
    reached(url)
    const snapshot = structuredClone(read(url))
    assert.equal(isCommentPage(snapshot.json, "OLDEST"), true)
    await pending
    await route.fulfill(snapshot)
  }
  return { ready, release: () => release() }
}
async function settledRead(p, index) {
  await p.waitForFunction(i => window.commentReads[i]?.consumed, index)
  await p.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
}
async function failEdit(p, state, id) {
  await p.locator(`#comment-edit-button-${id}`).click()
  await p.locator(`#comment-edit-${id}`).fill('보관할 수정 초안')
  state.write = r => { comments=comments.filter(c=>c.commentId!==id); return r.fulfill({ status: 404, json: failure('COMMENT_NOT_FOUND') }) }
  await p.getByRole('button', { name: '댓글 저장', exact: true }).click()
  await p.getByText('현재 이 댓글을 수정할 수 없어요.',{exact:true}).waitFor()
  await p.waitForFunction(()=>document.querySelector('#comments-refresh')?.disabled===false)
  assert.equal(await p.locator(`#comment-${id}`).count(), 0)
  assert.equal(await p.locator(`#comment-edit-${id}`).count(),0)
  assert.equal(await p.locator('.community-comment-detached,#comment-recovery-title').count(),0)
  assert.equal(await p.getByRole('button', { name: '댓글 저장', exact: true }).count(), 0)
  assert.equal(state.calls.filter(c => c.method === 'PUT').length, 1)
}
try {
  for(const input of ['A','C']) await check(`latest GET B while user types ${input}: shared dirty baseline`,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      const A=item(1).content,B='B: 저장된 본문',C='C: 더 최근 입력'
      await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill(B)
      state.write=r=>{comments[0]={...comments[0],content:B,contentUpdatedAt:'2026-11-05T01:00:00Z'};return r.abort('failed')}
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await p.locator('#comment-edit-1-error-request').waitFor()
      const index=await p.evaluate(()=>window.commentReads.length),gate=holdRead(state)
      await p.locator('#comments-refresh').click();await gate.ready
      await p.locator('#comment-edit-1').fill(input==='A'?A:C)
      gate.release();await settledRead(p,index)
      assert.equal(await p.locator('#comment-edit-1').inputValue(),input==='A'?A:C)
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isEnabled())
      await p.getByRole('button',{name:'수정 취소',exact:true}).click();await p.getByRole('button',{name:'계속 수정',exact:true}).click()
      await p.locator('#comment-edit-button-3').click();await p.getByRole('button',{name:'계속 수정',exact:true}).click()
      await p.locator('.community-back').click();await p.getByRole('button',{name:'계속 작성',exact:true}).click()
      assert.equal(await p.locator('#comment-edit-1').inputValue(),input==='A'?A:C)
      // An absent page and a failed GET must preserve the last confirmed B.
      comments=comments.slice(1);await p.locator('#comments-refresh').click()
      await p.getByText('현재 목록에 없는 댓글을 수정하고 있어요. 입력한 내용은 유지했어요.',{exact:true}).waitFor()
      state.get=r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})
      await p.locator('#comments-refresh').click();await p.getByRole('button',{name:'댓글 조회 다시 시도'}).waitFor()
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isEnabled())
      await p.locator('#comment-edit-1').fill(B)
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isDisabled())
      await p.locator('#comment-draft').fill('별도 작성 초안')
      await p.locator('.community-back').click();await p.getByRole('button',{name:'계속 작성',exact:true}).click()
      await p.locator('#comment-draft').fill('');await p.locator('.community-back').click()
      await p.waitForURL(u=>u.pathname==='/community/topics/hearing-aid')
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
    }finally{await ctx.close()}
  })
  for(const width of [1440,390,320]) for(const close of ['그대로 머무르기','닫기','Escape']) await check(`create expiry one modal retains input ${width} ${close}`,async()=>{
    const {ctx,p,state}=await fixture(member,1)
    try{
      await p.setViewportSize({width,height:900})
      await p.locator('#comment-draft').fill('만료되어도 유지할 입력\n둘째 줄')
      state.write=r=>{state.identity=null;return r.fulfill({status:401,json:failure('AUTHENTICATION_REQUIRED')})}
      await p.getByRole('button',{name:'댓글 등록',exact:true}).click()
      const dialog=p.getByRole('dialog',{name:'다시 로그인이 필요해요',exact:true})
      await dialog.waitFor()
      await dialog.getByText('로그인 시간이 만료되어 댓글을 등록하지 못했어요.',{exact:true}).waitFor()
      await dialog.getByText('이 화면에 머무르면 입력 내용은 유지돼요. 로그인 화면으로 이동하면 입력 내용은 사라져요.',{exact:true}).waitFor()
      assert.equal(await p.getByRole('dialog').count(),1)
      assert.equal(await p.locator('.iyum-auth-notice,.community-comments [role=alert]').count(),0)
      await p.waitForFunction(()=>document.activeElement?.textContent==='그대로 머무르기')
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      if(close==='그대로 머무르기')await checkCommentAuthTypography(p,output,`detail-create-${width}`)
      if(close==='그대로 머무르기')await p.screenshot({path:`${output}/create-expired-${width}.png`})
      if(close==='Escape')await p.keyboard.press('Escape');else await dialog.getByRole('button',{name:close,exact:true}).click()
      await p.waitForFunction(()=>document.activeElement?.id==='comment-draft')
      assert.equal(await p.locator('#comment-draft').inputValue(),'만료되어도 유지할 입력\n둘째 줄')
      assert.equal(state.calls.filter(c=>c.method==='POST').length,1)
      await p.getByRole('button',{name:'댓글 등록',exact:true}).click()
      await p.getByRole('dialog',{name:'로그인이 필요해요',exact:true}).waitFor()
      if(close==='그대로 머무르기')await checkCommentAuthTypography(p,output,`detail-login-${width}`)
      assert.equal(state.calls.filter(c=>c.method==='POST').length,1)
    }finally{await ctx.close()}
  })
  for(const width of [1440,390,320]) for(const close of ['닫기','Escape']) await check(`edit expiry modal once, focus and layout ${width} ${close}`,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try{
      await p.setViewportSize({width,height:900})
      await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('만료시 정리할 입력')
      state.write=r=>{state.identity=null;return r.fulfill({status:401,json:failure('AUTHENTICATION_REQUIRED')})}
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      const dialog=p.getByRole('dialog',{name:'다시 로그인이 필요해요',exact:true})
      await dialog.waitFor();assert.equal(await p.getByRole('dialog').count(),1)
      assert.equal(await p.locator('#comment-edit-1').count(),0)
      assert.equal(await p.locator('.comment-success-toast,.iyum-auth-notice').count(),0)
      assert.equal(await dialog.getAttribute('aria-describedby'),'comment-expired-description')
      await p.waitForFunction(()=>document.activeElement?.textContent==='그대로 머무르기')
      await p.keyboard.press('Tab');assert.equal(await p.evaluate(()=>document.activeElement?.textContent),'로그인하기')
      await p.keyboard.press('Tab');assert.equal(await p.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'닫기')
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await checkCommentAuthTypography(p,output,`detail-edit-${width}-${close}`)
      await p.screenshot({path:`${output}/edit-expired-${width}-${close}.png`})
      if(close==='Escape')await p.keyboard.press('Escape');else await dialog.getByRole('button',{name:'닫기',exact:true}).click()
      await p.waitForFunction(()=>document.activeElement?.id==='comments-title')
      const index=await p.evaluate(()=>window.commentReads.length)
      await p.locator('#comments-refresh').click();await settledRead(p,index)
      assert.equal(await p.getByRole('dialog').count(),0)
      assert.equal(new URL(p.url()).pathname,'/community/posts/7')
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
    }finally{await ctx.close()}
  })
  for(const otherDraft of [false,true]) await check(`edit expiry login returns without automatic edit; other draft=${otherDraft}`,async()=>{
    const {ctx,p,state}=await fixture(member,1)
    try{
      if(otherDraft)await p.locator('#comment-draft').fill('다른 새 댓글 초안')
      await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('자동 복원 금지')
      state.write=r=>{state.identity=null;return r.fulfill({status:401,json:failure('AUTHENTICATION_REQUIRED')})}
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await p.getByRole('dialog',{name:'다시 로그인이 필요해요'}).getByRole('button',{name:'로그인하기',exact:true}).click()
      await p.waitForURL(u=>u.pathname==='/login')
      assert.equal(new URL(p.url()).searchParams.get('from'),'/community/posts/7?from='+encodeURIComponent('/community/topics/hearing-aid'))
      await p.getByRole('form',{name:'로그인 양식'}).locator('input[name="email"]').fill('example@example.test')
      await p.getByRole('form',{name:'로그인 양식'}).locator('input[name="password"]').fill('Mock-test-123!')
      await p.getByRole('button',{name:'로그인',exact:true}).click()
      await p.waitForURL(u=>u.pathname==='/community/posts/7')
      await p.locator('#comment-edit-button-1').waitFor()
      assert.equal(await p.locator('#comment-edit-1,#comment-expired-title').count(),0)
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
    }finally{await ctx.close()}
  })
  for(const method of ['PUT','POST']) for(const navigation of ['leave','logout','relogin']) await check(`late ${method} 401 ignored after ${navigation}`,async()=>{
    const {ctx,p,state}=await fixture(member,1)
    let release
    try{
      let reach;const reached=new Promise(r=>reach=r),gate=new Promise(r=>release=r)
      await p.evaluate(method=>{window.editConsumed=false;const original=window.fetch;window.fetch=async(...args)=>{const r=await original(...args);if(args[1]?.method===method){const json=r.json.bind(r);r.json=async()=>{try{return await json()}finally{window.editConsumed=true}}}return r}},method)
      if(method==='PUT'){await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('오래된 입력')}else await p.locator('#comment-draft').fill('오래된 등록 입력')
      state.write=async r=>{reach();await gate;await r.fulfill({status:401,json:failure('AUTHENTICATION_REQUIRED')})}
      await p.getByRole('button',{name:method==='PUT'?'댓글 저장':'댓글 등록',exact:true}).click();await reached
      if(navigation==='leave'){
        await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/community/topics/hearing-aid')
      }else{
        await p.getByLabel('회원 메뉴').getByRole('button',{name:'로그아웃',exact:true}).click()
        await p.getByPlaceholder('로그인 후 댓글을 남길 수 있어요',{exact:true}).waitFor()
        assert.equal(await p.locator('#comment-expired-title').count(),0)
        if(navigation==='relogin'){
          state.loginMember={...member,memberId:456,nickname:'새 검증회원'}
          await p.getByRole('button',{name:'댓글 등록',exact:true}).click()
          await p.getByRole('dialog').getByRole('button',{name:'로그인하기',exact:true}).click()
          await p.waitForURL(u=>u.pathname==='/login')
          await p.getByRole('form',{name:'로그인 양식'}).locator('input[name="email"]').fill('new@example.test')
          await p.getByRole('form',{name:'로그인 양식'}).locator('input[name="password"]').fill('Mock-test-123!')
          await p.getByRole('button',{name:'로그인',exact:true}).click()
          await p.waitForURL(u=>u.pathname==='/community/posts/7')
          await p.getByRole('button',{name:'댓글 등록',exact:true}).waitFor()
        }
      }
      release();await p.waitForFunction(()=>window.editConsumed)
      await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
      assert.equal(await p.locator('#comment-expired-title').count(),0)
      if(navigation==='relogin')assert(await p.getByRole('button',{name:'댓글 등록',exact:true}).isVisible())
    }finally{release?.();await ctx.close()}
  })
  for(const mode of ['network','timeout','server','generic404','denied','csrf','parent']) await check(`edit failure classification: ${mode}`,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await p.locator('#comment-draft').fill('별도 새 댓글')
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-edit-1').fill('수정 입력\n공백  유지')
      state.write=r=>{
        if(mode==='network'||mode==='timeout')return r.abort(mode==='timeout'?'timedout':'failed')
        if(mode==='parent')comments=[]
        return r.fulfill({status:mode==='server'?503:['parent','generic404'].includes(mode)?404:403,
          json:failure(mode==='parent'?'POST_NOT_FOUND':mode==='generic404'?'NOT_FOUND':mode==='server'?'SERVER_ERROR':mode==='csrf'?'CSRF_TOKEN_INVALID':'ACCESS_DENIED')})
      }
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      if(mode==='parent'){
        await p.getByText('현재 이 댓글을 수정할 수 없어요.',{exact:true}).waitFor()
        await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
        assert.equal(await p.locator('#comment-edit-1').count(),0)
      }else{
        await p.locator('#comment-edit-1-error-request').waitFor()
        assert.equal(await p.locator('#comment-1 textarea').inputValue(),'수정 입력\n공백  유지')
        assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isEnabled())
        if(['network','timeout','server','generic404'].includes(mode))assert.equal(await p.locator('#comment-edit-1-error-request').textContent(),'수정 결과를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.')
      }
      assert.equal(await p.locator('.community-comment-detached,#comment-recovery-title').count(),0)
      assert.equal(await p.locator('#comment-expired-title').count(),0)
      assert.equal(await p.locator('#comment-draft').inputValue(),'별도 새 댓글')
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
    }finally{await ctx.close()}
  })
  for (const width of [1440,320]) await check(`definite failure clears edit only, restores focus without recovery UI ${width}`,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await p.setViewportSize({width,height:900})
      await failEdit(p,state,1)
      assert.equal(await p.locator('.community-comment-detached,#comment-recovery-title').count(),0)
      assert.equal(await p.getByRole('dialog').count(),0)
      await p.waitForFunction(()=>document.activeElement?.id==='comments-title')
      await p.screenshot({path:`${output}/unavailable-${width}.png`})
      comments.unshift({...item(1),content:'보관할 수정 초안',contentUpdatedAt:'2026-11-05T01:00:00Z'})
      await p.locator('#comments-refresh').click()
      await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled&&document.querySelector('#comment-1')?.textContent.includes('보관할 수정 초안'))
      assert.equal(await p.locator('#comment-edit-1').count(),0)
      assert.equal(await p.getByRole('button',{name:'수정 이어가기'}).count(),0)
      await p.screenshot({path:`${output}/matching-${width}.png`})
      assert.equal(await p.locator('.community-comment-detached').count(),0)
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
      await p.locator('.community-back').click()
      await p.waitForURL(u=>u.pathname==='/community/topics/hearing-aid')
    } finally {await ctx.close()}
  })
  await check('temporary edit input clears on confirmed logout without exposing old draft',async()=>{
    const {ctx,p,state}=await fixture(member,3)
    let release
    try {
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-edit-1').fill('유지할 수정 초안')
      state.write=r=>r.abort('failed')
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await p.locator('#comment-edit-1-error-request').waitFor()
      let reached=false
      const gate=new Promise(r=>{release=r})
      state.logout=async r=>{reached=true;await gate;state.identity=null;return r.fulfill({status:204})}
      await p.getByLabel('회원 메뉴').getByRole('button',{name:'로그아웃',exact:true}).click()
      await until(()=>reached)
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'유지할 수정 초안')
      release()
      await p.getByRole('button',{name:'댓글 등록',exact:true}).waitFor()
      await p.locator('#comment-edit-1').waitFor({state:'detached'})
      assert.equal(await p.locator('.community-comment-detached').count(),0)
      assert.equal(await p.locator('#comment-edit-1').count(),0)
    } finally {release?.();await ctx.close()}
  })
  await check('unchanged edit save is not loading; changed draft submits once', async () => {
    const {p,state}=await fixture(member,2)
    await p.locator('#comment-1').getByRole('button',{name:'댓글 수정',exact:true}).click()
    const save=p.getByRole('button',{name:'댓글 저장',exact:true}),input=p.locator('#comment-edit-1')
    await save.hover()
    assert.equal(await save.isDisabled(),true)
    assert.equal(await save.getAttribute('aria-busy'),'false')
    assert.equal(await save.evaluate(el=>getComputedStyle(el).cursor),'default')
    assert.equal(await p.getByText('내용을 변경하면 저장할 수 있어요.',{exact:true}).isVisible(),true)
    assert.equal(state.calls.filter(c=>c.method==='PUT').length,0)
    const original=await input.inputValue()
    await input.fill(original+' 변경')
    assert.equal(await save.isEnabled(),true)
    await input.fill(original)
    assert.equal(await save.isDisabled(),true)
    await input.fill(original+' 변경')
    let release,reached=false
    const gate=new Promise(resolve=>{release=resolve})
    state.write=async r=>{reached=true;await gate;return r.fulfill({status:204})}
    try {
      await save.click()
      await until(()=>reached)
      assert.equal(await p.locator('#comment-1').getByRole('button',{name:'처리 중…',exact:true}).getAttribute('aria-busy'),'true')
      assert.equal(await save.count(),0)
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
    } finally {release()}
    await p.waitForFunction(()=>!document.querySelector('#comment-edit-1'))
    assert.equal(await p.locator('#comment-1').getByRole('button',{name:'댓글 수정',exact:true}).isEnabled(),true)
  })
  for (const width of [1440,320]) await check(`content timestamp: moderation, real edit and identical edit ${width}`,async()=>{
    const {ctx,p}=await fixture(member,2)
    try {
      await p.setViewportSize({width,height:900})
      comments[0].updatedAt='2026-11-05T03:00:00Z'
      comments[1].contentUpdatedAt='2026-10-28T01:02:03.123456Z'
      comments[1].updatedAt='2026-11-05T04:00:00Z'
      const refresh=async()=>{
        const response=p.waitForResponse(r=>r.url().includes('/posts/7/comments?')&&r.request().method()==='GET')
        await p.locator('#comments-refresh').click();await (await response).finished()
        await p.waitForFunction(()=>document.querySelector('#comments-refresh')?.disabled===false)
      }
      await refresh()
      assert.equal(await p.locator('#comment-1 .community-comment-meta').getByText('수정됨',{exact:true}).count(),0)
      await p.locator('#comment-2 .community-comment-meta').getByText('수정됨',{exact:true}).waitFor()
      // Mock moderation removes and restores the row without altering body-edit time.
      const restored=comments;comments=[];await refresh();await p.locator('#comment-2').waitFor({state:'detached'})
      comments=restored.map(c=>({...c,updatedAt:'2026-11-05T05:00:00Z'}));await refresh()
      assert.equal(await p.locator('#comment-1 .community-comment-meta').getByText('수정됨',{exact:true}).count(),0)
      await p.locator('#comment-2 .community-comment-meta').getByText('수정됨',{exact:true}).waitFor()
      const save=async(content)=>{
        await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill(content)
        const response=p.waitForResponse(r=>r.url().includes('/posts/7/comments?')&&r.request().method()==='GET')
        await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await (await response).finished()
        await p.locator('#comment-edit-1').waitFor({state:'detached'})
      }
      await save('실제 변경한 본문\n공백  유지')
      await p.locator('#comment-1 .community-comment-meta').getByText('수정됨',{exact:true}).waitFor()
      assert.equal(comments[0].contentUpdatedAt,'2026-10-18T01:00:00Z')
      const edited=comments[0].contentUpdatedAt
      await p.locator('#comment-edit-button-1').click()
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isDisabled())
      await p.getByRole('button',{name:'수정 취소',exact:true}).click()
      await refresh();assert.equal(comments[0].contentUpdatedAt,edited)
      assert.equal(await p.locator('#comment-1 .community-comment-meta').getByText('수정됨',{exact:true}).count(),1)
      assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
      await p.locator('#comment-1').scrollIntoViewIfNeeded();await p.screenshot({path:`${output}/content-time-${width}.png`})
    } finally {await ctx.close()}
  })

  for (const width of [1440,320]) for (const changed of [false,true]) await check(`temporary edit stays inline after refresh ${width} changed=${changed}`,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await p.setViewportSize({width,height:900})
      await p.locator('#comment-draft').fill('다른 새 댓글 입력')
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-edit-1').fill('보관할 수정 초안')
      state.write=r=>r.abort('failed')
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await p.locator('#comment-edit-1-error-request').waitFor()
      if(changed) comments[0]={...comments[0],content:'다른 기기에서 변경한 서버 본문',contentUpdatedAt:'2026-11-05T01:00:00Z'}
      const index=await p.evaluate(()=>window.commentReads.length)
      await p.locator('#comments-refresh').click();await settledRead(p,index)
      assert.equal(await p.locator('#comment-1 textarea').count(),1)
      if(changed)await p.getByText('댓글 내용이 변경됐어요. 입력한 내용은 유지했어요.',{exact:true}).waitFor()
      await p.locator('#comment-1').scrollIntoViewIfNeeded();await p.screenshot({path:`${output}/retained-${width}-${changed}.png`})
      await p.locator('#comment-edit-1').focus()
      const input=p.locator('#comment-1 textarea');assert.equal(await input.inputValue(),'보관할 수정 초안')
      await p.waitForFunction(()=>{const r=document.querySelector('#comment-1 textarea')?.getBoundingClientRect();return r&&r.top>=0&&r.bottom<=innerHeight})
      assert.equal(await p.getByRole('dialog').count(),0);assert.equal(await p.locator('#comment-draft').inputValue(),'다른 새 댓글 입력')
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
      state.write=null
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await p.locator('#comment-1 .community-comment-body').getByText('보관할 수정 초안',{exact:true}).waitFor()
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,2)
    } finally {await ctx.close()}
  })
  await check('unknown page, failed retry and ownership preserve input with ordinary cancel protection',async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('없어지면 안 되는 입력')
      const original=comments[0];comments=comments.slice(1)
      await p.locator('#comments-refresh').click()
      await p.getByText('현재 목록에 없는 댓글을 수정하고 있어요. 입력한 내용은 유지했어요.',{exact:true}).waitFor()
      assert.equal(await p.getByRole('dialog').count(),0)
      state.get=r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})
      await p.locator('#comments-refresh').click();await p.getByRole('button',{name:'댓글 조회 다시 시도'}).waitFor()
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'없어지면 안 되는 입력')
      state.get=null;comments.unshift({...original,author:{memberId:999,nickname:'다른 작성자'}})
      const index=await p.evaluate(()=>window.commentReads.length)
      await p.locator('#comments-refresh').click();await settledRead(p,index)
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isDisabled())
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'없어지면 안 되는 입력')
      assert.equal(await p.getByRole('button',{name:'내용 복사',exact:true}).count(),0)
      await p.getByRole('button',{name:'수정 취소',exact:true}).click();await p.getByRole('button',{name:'계속 수정',exact:true}).click()
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'없어지면 안 되는 입력')
      await p.getByRole('button',{name:'수정 취소',exact:true}).click();await p.getByRole('button',{name:'수정 내용 버리기',exact:true}).click();await p.locator('#comment-edit-1').waitFor({state:'detached'})
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,0)
    } finally {await ctx.close()}
  })
  await check('draft typing during delayed GET remains newest without automatic submit',async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('조회 전 초안')
      await ignoreReadAbort(p)
      const gate=holdRead(state),index=await p.evaluate(()=>window.commentReads.length)
      await p.locator('#comments-refresh').click();await gate.ready
      await p.locator('#comment-edit-1').fill('조회 대기 중 더 최근 입력')
      gate.release();await settledRead(p,index)
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'조회 대기 중 더 최근 입력')
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,0)
      // The next GET contains B, but the user types C after it starts.
      comments[0]={...comments[0],content:'조회 대기 중 더 최근 입력'}
      const nextIndex=await p.evaluate(()=>window.commentReads.length),next=holdRead(state)
      await p.locator('#comments-refresh').click();await next.ready
      await p.locator('#comment-edit-1').fill('C: 조회 이후 추가 입력')
      next.release();await settledRead(p,nextIndex)
      assert.equal(await p.getByRole('dialog').count(),0)
      await p.getByText('댓글 내용이 변경됐어요. 입력한 내용은 유지했어요.',{exact:true}).waitFor()
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'C: 조회 이후 추가 입력')
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isEnabled())
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,0)

    } finally {await ctx.close()}
  })
  await check('uncertain edit GET matches submitted body: no redundant save or success claim',async()=>{
    const {ctx,p,state}=await fixture(member,1)
    try {
      await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('서버에 도달한 입력')
      state.write=r=>{comments[0]={...comments[0],content:'서버에 도달한 입력',contentUpdatedAt:'2026-11-05T01:00:00Z'};return r.abort('failed')}
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await p.locator('.community-error[role=alert]').filter({hasText:/확인/}).waitFor()
      await p.locator('#comments-refresh').click();await p.getByText(/현재 댓글에 같은 내용이 반영되어 있어요/).waitFor()
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isDisabled());assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
      assert.equal(await p.getByText('댓글을 수정했습니다.',{exact:true}).count(),0)
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'서버에 도달한 입력')
      await p.locator('#comment-edit-1').fill(item(1).content)
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isEnabled())
      await p.locator('.community-back').click()
      await p.getByRole('button',{name:'계속 작성',exact:true}).click()
      assert.equal(await p.locator('#comment-edit-1').inputValue(),item(1).content)
      state.write=null
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await p.locator('#comment-1 .community-comment-body').waitFor()
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,2)

    } finally {await ctx.close()}
  })
  for (const width of [1440, 320]) await check(`restoration A/B: fresh body, cleared draft, explicit new edit ${width}`, async () => {
    const {ctx,p,state} = await fixture(member, 3)
    try {
      await p.setViewportSize({width,height:1000})
      await p.locator('#comment-draft').fill('독립적인 새 댓글 초안')
      await failEdit(p,state,1)
      assert.equal(await p.locator('#comment-edit-1').count(),0)
      await p.waitForFunction(()=>document.activeElement?.id==='comments-title')
      assert.equal(await p.getByRole('dialog').count(),0)
      await p.locator('.comment-success-toast.is-notice').waitFor()
      comments.unshift({...item(1),content:'복원 후 최신 서버 본문'})
      await p.locator('#comments-refresh').click()
      await p.waitForFunction(() => !document.querySelector('#comments-refresh').disabled && document.querySelector('#comment-1')?.textContent.includes('복원 후 최신 서버 본문'))
      assert.equal(await p.locator('#comment-1').count(),1)
      assert.equal(await p.locator('#comment-1 textarea').count(),0)
      assert.equal(await p.locator('.community-comment-detached,#comment-recovery-title').count(),0)
      assert(await p.evaluate(() => {const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);return new Set(ids).size===ids.length}))
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.screenshot({path:`${output}/restored-${width}.png`,fullPage:true})
      await p.locator('#comment-edit-button-1').click()
      assert.equal(await p.getByRole('dialog').count(),0)
      await p.waitForFunction(()=>document.activeElement?.id==='comment-edit-1')
      assert.equal(await p.locator('#comment-1 textarea').inputValue(),'복원 후 최신 서버 본문')
      assert.equal(await p.locator('.community-comment-detached').count(),0)
      assert.equal(await p.locator('#comment-draft').inputValue(),'독립적인 새 댓글 초안')
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
      await p.locator('.community-back').click()
      await p.getByRole('button',{name:'계속 작성',exact:true}).click()
      assert.equal(await p.locator('#comment-draft').inputValue(),'독립적인 새 댓글 초안')
    } finally {await ctx.close()}
  })
  await check('restoration C: ignored abort old GET cannot restore or finish a newer GET',async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await ignoreReadAbort(p)
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-edit-1').fill('경합 중 수정 초안')
      const oldIndex=await p.evaluate(()=>window.commentReads.length), old=holdRead(state)
      await p.locator('#comments-refresh').click();await old.ready
      const fresh=holdRead(state)
      state.write=r=>{comments=comments.map(c=>c.commentId===1?{...c,content:'새 요청의 복원 본문'}:c);return r.fulfill({status:404,json:failure('COMMENT_NOT_FOUND')})}
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await fresh.ready
      assert.equal(await p.locator('#comment-1').count(),0)
      old.release();await settledRead(p,oldIndex)
      assert.equal(await p.locator('#comment-1').count(),0)
      assert.equal(await p.locator('#comments-refresh').getAttribute('aria-busy'),'true')
      assert.equal(await p.locator('#comment-edit-1').count(),0)
      assert.equal(await p.locator('.community-comments [role="alert"]').count(),0)
      fresh.release()
      await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled&&document.querySelector('#comment-1')?.textContent.includes('새 요청의 복원 본문'))
      assert.equal(await p.locator('#comment-1 textarea').count(),0)
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
    } finally {await ctx.close()}
  })
  await check('restoration D: more only confirms fetched page; prefix refresh confirms restored ID',async()=>{
    const {ctx,p,state}=await fixture(member,45)
    try {
      await failEdit(p,state,1)
      comments.unshift({...item(1),content:'첫 페이지 복원'})
      await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click()
      await p.locator('#comment-40').waitFor()
      assert.equal(await p.locator('#comment-1').count(),0)
      assert.equal(await p.locator('.community-comment-list > li').count(),40)
      await p.locator('#comments-refresh').click()
      await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled&&document.querySelector('#comment-1')?.textContent.includes('첫 페이지 복원'))
      assert.equal(await p.locator('.community-comment-list > li').count(),40)
      assert.equal(await p.locator('#comment-1').count(),1)
      await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await p.locator('#comment-45').waitFor()
      assert.equal(await p.getByRole('button',{name:'댓글 더 보기',exact:true}).count(),0)
    } finally {await ctx.close()}
  })
  await check('restoration D: later page itself confirms a removed ID after prefix contracts',async()=>{
    const {ctx,p,state}=await fixture(member,22)
    try {
      await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await p.locator('#comment-22').waitFor()
      await failEdit(p,state,21)
      comments=comments.filter(c=>c.commentId<=20)
      await p.locator('#comments-refresh').click()
      await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled&&document.querySelectorAll('.community-comment-list > li').length===20)
      assert.equal(await p.getByRole('button',{name:'댓글 더 보기',exact:true}).count(),0)
      comments.push({...item(21),content:'다음 페이지에서 복원된 서버 본문'},item(22),item(23))
      await p.locator('#comments-refresh').click()
      await p.getByRole('button',{name:'댓글 더 보기',exact:true}).waitFor()
      await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
      assert.equal(await p.locator('#comment-21').count(),0)
      let cursor
      state.get=(r,u)=>{if(u.pathname.endsWith('/comments'))cursor=Object.fromEntries(u.searchParams);return r.fulfill(read(u))}
      await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click()
      await p.waitForFunction(()=>document.querySelector('#comment-21')?.textContent.includes('다음 페이지에서 복원된 서버 본문')&&!document.querySelector('#comments-refresh').disabled)
      assert.equal(cursor.afterId,'20');assert.equal(cursor.afterCreatedAt,item(20).createdAt);assert.equal(cursor.afterSort,'OLDEST')
      assert.equal(await p.locator('#comment-21').count(),1)
      assert.equal(await p.locator('#comment-21 textarea').count(),0)
      assert.equal(await p.locator('#comment-edit-21').count(),0)
      assert.equal(await p.locator('.community-comment-list > li').count(),23)
      assert.equal(await p.getByRole('button',{name:'댓글 더 보기',exact:true}).count(),0)
    } finally {await ctx.close()}
  })
  for(const width of [1440,390,320])for(const mode of ['csrf','denied','network','malformed'])await check(`creation error adjacent to composer ${width} ${mode}`,async()=>{
    const {ctx,p,state}=await fixture(member,45)
    try {
      await p.setViewportSize({width,height:1000});await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await p.locator('#comment-40').waitFor()
      const draft=p.locator('#comment-draft');await draft.fill('오류 후에도 남는 작성 초안')
      await p.locator('.community-comment-composer').evaluate(el=>window.scrollTo({top:window.scrollY+el.getBoundingClientRect().top-130,behavior:'instant'}))
      let release,reach;const reached=new Promise(r=>reach=r)
      const handler=async r=>{reach();await new Promise(r=>release=r);if(mode==='network')await r.abort('failed');else if(mode==='malformed')await r.fulfill({status:201,json:{malformed:true}});else await r.fulfill({status:403,json:failure(mode==='csrf'?'CSRF_TOKEN_INVALID':'ACCESS_DENIED')})}
      if(mode==='csrf')state.csrf=handler;else state.write=handler
      await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await reached
      const before=await p.evaluate(()=>scrollY);release()
      await p.locator('#comment-create-error').waitFor();await p.waitForFunction(()=>!document.querySelector('.community-comment-composer button').disabled)
      const position=await p.locator('#comment-create-error').evaluate(el=>{
        const r=el.getBoundingClientRect(),d=document.querySelector('#comment-draft').getBoundingClientRect()
        return {top:r.top,bottom:r.bottom,left:r.left,right:r.right,draftBottom:d.bottom,scroll:scrollY,height:innerHeight,width:innerWidth}
      })
      assert(position.top>=position.draftBottom&&position.top-position.draftBottom<60);assert(position.top>=0&&position.bottom<=position.height, JSON.stringify(position));assert(position.left>=0&&position.right<=position.width);assert.equal(position.scroll,before)
      assert.equal(await draft.inputValue(),'오류 후에도 남는 작성 초안');assert((await draft.getAttribute('aria-describedby')).includes('comment-create-error'))
      assert.equal(await p.locator('.community-comments [role="alert"]').count(),1);assert.equal(state.calls.filter(c=>c.method==='POST').length,mode==='csrf'?0:1)
      if(mode==='network'||mode==='malformed')assert((await p.locator('#comment-create-error').innerText()).includes('자동 재등록하지 않습니다.'))
      await p.screenshot({path:output+`/create-error-${width}-${mode}.png`})
    }finally{await ctx.close()}
  })
  for(const method of ['PUT','DELETE'])await check(`mutation error ownership ${method} excludes creation region`,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      state.write=r=>r.fulfill({status:403,json:failure('ACCESS_DENIED')})
      if(method==='PUT'){await p.locator('#comment-edit-button-1').click();await p.locator('#comment-edit-1').fill('수정 초안');await p.getByRole('button',{name:'댓글 저장',exact:true}).click()}
      else{await p.locator('#comment-1').getByRole('button',{name:'댓글 삭제',exact:true}).click();await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click()}
      await p.locator('.community-comments [role="alert"]').waitFor();assert.equal(await p.locator('#comment-create-error').count(),0)
      if(method==='PUT')assert.equal(await p.locator('#comment-edit-1').inputValue(),'수정 초안')
      else assert(await p.getByRole('dialog').getByRole('alert').isVisible())
    }finally{await ctx.close()}
  })
  await check('editor transitions: clean switch, dirty cancel/confirm, delete staging and independent draft',async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-edit-button-3').click()
      await p.locator('#comment-edit-3').waitFor()
      assert.equal(await p.locator('#comment-edit-1').count(),0)
      await p.waitForFunction(()=>document.activeElement?.id==='comment-edit-3')
      await p.locator('#comment-edit-3').fill('보존할 수정 초안')
      await p.locator('#comment-draft').fill('별도의 등록 초안')
      await p.locator('#comment-edit-button-1').click()
      await p.getByRole('button',{name:'계속 수정',exact:true}).click()
      assert.equal(await p.locator('#comment-edit-3').inputValue(),'보존할 수정 초안')
      await p.locator('#comment-edit-button-1').click()
      await p.getByRole('button',{name:'수정 내용 버리기',exact:true}).click()
      await p.waitForFunction(()=>document.activeElement?.id==='comment-edit-1')
      assert.equal(await p.locator('#comment-edit-3').count(),0)
      assert.equal(await p.locator('#comment-draft').inputValue(),'별도의 등록 초안')
      await p.locator('#comment-edit-1').fill('삭제 전 보관할 초안')
      await p.locator('#comment-3').getByRole('button',{name:'댓글 삭제',exact:true}).click()
      await p.keyboard.press('Escape')
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'삭제 전 보관할 초안')
      assert.equal(await p.locator('#comment-delete-title').count(),0)
      await p.locator('#comment-3').getByRole('button',{name:'댓글 삭제',exact:true}).click()
      await p.getByRole('button',{name:'수정 내용 버리기',exact:true}).click()
      await p.locator('#comment-delete-title').waitFor()
      assert.equal(await p.locator('textarea[id^="comment-edit-"]').count(),0)
      assert.equal(state.calls.filter(c=>c.method==='DELETE').length,0)
      await p.getByRole('button',{name:'취소',exact:true}).click()
      assert.equal(await p.locator('#comment-draft').inputValue(),'별도의 등록 초안')
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-3').getByRole('button',{name:'댓글 삭제',exact:true}).click()
      await p.locator('#comment-delete-title').waitFor()
      assert.equal(await p.locator('#comment-edit-1').count(),0)
      await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click()
      await p.locator('#comment-3').waitFor({state:'detached'})
      assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1)
    } finally {await ctx.close()}
  })
  await check('edit/delete use one success toast, replace lifetime, retain focus and no inline duplicate',async()=>{
    const {ctx,p}=await fixture(member,1)
    try {
      await p.clock.install()
      await p.locator('#comment-edit-button-1').click()
      await p.locator('#comment-edit-1').fill('수정 토스트 검증')
      await p.getByRole('button',{name:'댓글 저장',exact:true}).click()
      await p.locator('.comment-success-toast').getByText('댓글을 수정했습니다.',{exact:true}).waitFor()
      assert.equal(await p.getByText('댓글을 수정했습니다.',{exact:true}).count(),1)
      assert(!(await p.locator('.community-comments-status').textContent()).includes('댓글을 수정했습니다.'))
      await p.waitForFunction(()=>document.activeElement?.id==='comment-edit-button-1')
      await p.clock.runFor(4000)
      await p.locator('#comment-1').getByRole('button',{name:'댓글 삭제',exact:true}).click()
      await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click()
      await p.locator('.comment-success-toast').getByText('댓글을 삭제했습니다.',{exact:true}).waitFor()
      assert.equal(await p.getByText('댓글을 삭제했습니다.',{exact:true}).count(),1)
      assert.equal(await p.getByText('댓글을 수정했습니다.',{exact:true}).count(),0)
      assert(!(await p.locator('.community-comments-status').textContent()).includes('댓글을 삭제했습니다.'))
      await p.clock.runFor(1500);assert.equal(await p.locator('.comment-success-toast').count(),1)
      await p.clock.runFor(3500);await p.clock.runFor(250)
      assert.equal(await p.locator('.comment-success-toast').count(),0)
    } finally {await ctx.close()}
  })
  for (const width of [1440,390,320]) await check(`registration toast ${width}: single live announcement, layout and timed removal`,async()=>{
    const {ctx,p}=await fixture(member,0)
    try {
      await p.setViewportSize({width,height:850});await p.emulateMedia({reducedMotion:width===320?'reduce':'no-preference'})
      await p.locator('#comment-draft').fill('토스트 모의 댓글')
      const submit=p.getByRole('button',{name:'댓글 등록',exact:true});await submit.focus()
      await p.keyboard.press('Enter');await p.locator('.comment-success-toast').waitFor()
      assert.equal(await p.getByText('댓글을 등록했습니다.',{exact:true}).count(),1)
      assert.equal(await p.locator('.community-comments-status').textContent(),'')
      assert(await p.locator('.comment-toast-announcer').getAttribute('role')==='status')
      assert.equal(await p.locator('.comment-success-toast button').count(),0)
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      const before=await p.evaluate(()=>({y:scrollY,focus:document.activeElement.id,top:document.querySelector('#comment-draft').getBoundingClientRect().top}))
      await p.screenshot({path:output+`/toast-visible-${width}.png`})
      await p.locator('.comment-success-toast').waitFor({state:'detached',timeout:7000})
      const after=await p.evaluate(()=>({y:scrollY,focus:document.activeElement.id,top:document.querySelector('#comment-draft').getBoundingClientRect().top}))
      assert.deepEqual(after,before)
      await p.screenshot({path:output+`/toast-removed-${width}.png`})
    } finally {await ctx.close()}
  })
  await check('consecutive registration replaces timer; old exit cannot remove fresh toast',async()=>{
    const {ctx,p}=await fixture(member,0)
    try {
      await p.clock.install()
      const submit=p.getByRole('button',{name:'댓글 등록',exact:true})
      await p.locator('#comment-draft').fill('첫 성공');await submit.click();await p.locator('.comment-success-toast').waitFor()
      await p.clock.runFor(5000)
      await p.locator('#comment-draft').fill('다음 성공');await submit.click()
      await p.waitForFunction(()=>document.querySelector('.comment-success-toast')?.className==='comment-success-toast')
      await p.clock.runFor(300);assert.equal(await p.locator('.comment-success-toast').count(),1)
      await p.clock.runFor(4950);assert.equal(await p.locator('.comment-success-toast').count(),0)
    } finally {await ctx.close()}
  })
  await check("automatic in-flight response is buffered while typing; manual append reuses it once",async()=>{
    const {ctx,p,state}=await fixture(member,65,true)
    let release
    try{
      let gets=0
      state.get=async(r,u)=>{gets++;await new Promise(resolve=>release=resolve);await r.fulfill(read(u))}
      await p.locator(".community-comment-sentinel").evaluate(el=>window.scrollTo(0,el.getBoundingClientRect().top+scrollY-innerHeight-120))
      await until(()=>!!release)
      assert(await p.getByRole("button",{name:"댓글 더 보기"}).isDisabled())
      await p.locator("#comment-draft").fill("도착 전에 입력한 초안")
      const before=await p.evaluate(()=>scrollY)
      release()
      await p.waitForFunction(()=>!document.querySelector("#comments-refresh").disabled)
      assert.equal(await p.locator(".community-comment-list > li").count(),20)
      assert.equal(await p.locator("#comment-draft").inputValue(),"도착 전에 입력한 초안")
      assert.equal(await p.evaluate(()=>document.activeElement.id),"comment-draft")
      assert(Math.abs(await p.evaluate(()=>scrollY)-before)<4)
      await p.getByRole("button",{name:"댓글 더 보기"}).click()
      await p.locator("#comment-40").waitFor()
      assert.equal(gets,1)
    }finally{release?.();await ctx.close()}
  })
  await check("login confirmation preserves compose draft and clears prior member edit; one confirmation only",async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try{
      await p.locator("#comment-edit-button-1").click();await p.locator("#comment-edit-1").fill("편집 초안")
      await p.locator("#comment-draft").fill("작성 초안")
      await p.getByLabel("회원 메뉴").getByRole("button",{name:"로그아웃",exact:true}).click()
      const cta=p.getByRole("button",{name:"댓글 등록",exact:true});await cta.waitFor()
      const before=state.calls.length
      await cta.click()
      assert(await p.getByRole("dialog").getByText("로그인 화면으로 이동하면 입력한 내용은 보관되지 않아요.",{exact:true}).isVisible())
      await p.getByRole("button",{name:"그대로 머무르기"}).click()
      assert.equal(await p.locator("#comment-edit-1").count(),0)
      assert.equal(await p.locator("#comment-draft").inputValue(),"작성 초안")
      assert.equal(state.calls.slice(before).filter(c=>c.method!=="GET"||c.path.endsWith("/csrf")).length,0)
      await p.locator(".community-back").click();await p.getByRole("button",{name:"계속 작성",exact:true}).click()
      await cta.click();await p.getByRole("button",{name:"로그인하기",exact:true}).click()
      await p.waitForURL(u=>u.pathname==="/login");assert.equal(await p.getByRole("dialog").count(),0)
    }finally{await ctx.close()}
  })
  // One default run per viewport; the explicit focused plan fixes 320px to five runs.
  const controlsPlan = controlsOnly ? [1440,390,320,320,320,320,320] : [1440,390,320]
  for (const [run,width] of controlsPlan.entries()) {
    await check(`compact comment controls layout ${width} run ${run+1}`,async()=>{
      const {ctx,p}=await fixture(member,3)
      try {
        await settleCommentControls(p,width)
        // All positions and scrollY belong to one browser evaluation, never two frames.
        const geometry=await commentControlsGeometry(p)
        await fs.writeFile(`${output}/controls-${width}-${run+1}.json`,JSON.stringify(geometry,null,2))
        await p.locator('#comment-1').screenshot({path:`${output}/compact-controls-${width}-${run+1}.png`})
        for(const name of ['like','edit','del']){const box=geometry[name];assert(box.width>=44&&box.height>=44)}
        assert(Math.abs(geometry.like.y-geometry.edit.y)<2)
        assert(Math.abs(geometry.edit.y-geometry.del.y)<2)
        assert(geometry.like.x+geometry.like.width<=geometry.edit.x)
        assert(geometry.del.x+geometry.del.width<=geometry.viewport)
        assert(geometry.documentWidth<=geometry.viewport)
        assert.equal(geometry.like.borderTopWidth,'0px')
        assert.equal(geometry.edit.text,'수정');assert.equal(geometry.del.text,'삭제')
      } catch(error) {
        await p.screenshot({path:`${output}/controls-failure-${width}-${run+1}.png`,fullPage:true}).catch(()=>{})
        throw error
      } finally {await ctx.close()}
    })
    await check(`refresh toast lifecycle ${width} run ${run+1}`,async()=>{
      const {ctx,p,state}=await fixture(member,3)
      try {
        await settleCommentControls(p,width)
        // Virtual time starts only after real rendering/fonts/resize have settled.
        await p.clock.install()
      await p.locator('#comment-draft').fill('새로고침 후에도 남는 초안')
      const button=p.locator('#comments-refresh');await button.focus()
      let release,reach;const reached=new Promise(r=>reach=r)
      state.get=async(r,u)=>{if(u.pathname.endsWith("/comments")){reach();await new Promise(r=>release=r)}await r.fulfill(read(u))}
      await p.keyboard.press('Enter');await reached
      assert.equal(await p.locator('.comment-success-toast').count(),0)
      // The loading button is natively disabled. A toast must not steal the
      // user's current focus if they continue interacting while the GET waits.
      await p.locator('#comment-draft').focus()
      const y=await p.evaluate(()=>scrollY);release()
      const toast=p.locator('.comment-success-toast');await toast.waitFor()
      assert.equal(await p.getByText('댓글 목록을 새로고침했습니다.',{exact:true}).count(),1)
      assert(!(await p.locator('.community-comments-status').textContent()).includes('새로고침했습니다'))
      assert.equal(await p.locator('#comment-draft').inputValue(),'새로고침 후에도 남는 초안')
      assert(Math.abs(await p.evaluate(()=>scrollY)-y)<2)
      assert(await p.locator('#comment-draft').evaluate(e=>document.activeElement===e))
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.screenshot({path:`${output}/refresh-toast-${width}-${run+1}.png`})
      await p.clock.runFor(4000);state.get=null;const oldToast=await toast.elementHandle();await button.click();await p.waitForFunction(e=>!e.isConnected,oldToast);await toast.waitFor()
      await p.clock.runFor(1500);assert.equal(await toast.count(),1)
      await p.clock.runFor(3750);assert.equal(await toast.count(),0)
      } finally {await ctx.close()}
    })
  }
  await check('refresh failure remains inline; departed refresh cannot show success',async()=>{
    const {ctx,p,state}=await fixture(member,3)
    try {
      state.get=r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})
      await p.locator('#comments-refresh').click();await p.locator('.community-comments .community-error').waitFor()
      assert.equal(await p.locator('.comment-success-toast').count(),0)
      let release,reach;const reached=new Promise(r=>reach=r)
      state.get=async(r,u)=>{if(u.pathname.endsWith("/comments")){reach();await new Promise(r=>release=r)}await r.fulfill(read(u))}
      await p.getByRole('button',{name:'댓글 조회 다시 시도',exact:true}).click();await reached
      // Navigation now aborts this GET. Wait for its actual cancellation instead
      // of an impossible response; forced late delivery is covered in inline-comments-browser.
      const cancelled=p.waitForEvent('requestfailed',{predicate:r=>new URL(r.url()).origin===api&&new URL(r.url()).pathname==='/api/v1/community/posts/7/comments'&&r.method()==='GET'})
      await p.locator('.community-back').click();await cancelled
      await p.waitForURL(u=>!u.pathname.endsWith('/posts/7'))
      release();assert.equal(await p.locator('.comment-success-toast').count(),0)
    } finally {await ctx.close()}
  })
  await check("automatic boundaries 0/1/20/21 keep ASC timestamps and finite requests",async()=>{
    for(const count of [0,1,20,21]){
      const {ctx,p,state}=await fixture(member,count,true)
      try{
        if(count===21){await p.locator(".community-comment-sentinel").evaluate(el=>window.scrollTo(0,el.getBoundingClientRect().top+scrollY-innerHeight-120));await p.locator("#comment-21").waitFor()}
        assert.equal(await p.locator(".community-comment-list > li").count(),count)
        assert.equal(state.calls.filter(c=>c.method==="GET"&&c.path.endsWith("/comments")).length,count===21?2:1)
      }finally{await ctx.close()}
    }
  })
  await check("automatic 65 ASC comments: 20/40/60/65, exact cursor, no duplicates, stop", async()=>{
    const {ctx,p,state}=await fixture(member,65,true)
    try {
      for(const count of [40,60,65]){
        await p.locator(".community-comment-sentinel").evaluate(el=>window.scrollTo(0,el.getBoundingClientRect().top+scrollY-innerHeight-120))
        await p.waitForFunction(n=>document.querySelectorAll(".community-comment-list > li").length===n,count)
      }
      const ids=await p.locator(".community-comment-list > li").evaluateAll(els=>els.map(el=>el.id))
      assert.deepEqual(ids,Array.from({length:65},(_,i)=>`comment-${i+1}`))
      assert.equal(state.calls.filter(c=>c.method==="GET"&&c.path.endsWith("/comments")).length,4)
      assert.equal(await p.getByRole("button",{name:"댓글 더 보기"}).count(),0)
    } finally {await ctx.close()}
  })
  await check("automatic error stops; explicit more retry preserves cursor; draft pauses observer",async()=>{
    const {ctx,p,state}=await fixture(member,65,true)
    try{
      let gets=0
      state.get=(r,u)=>{gets++;return r.fulfill(gets===1?{status:503,json:failure("SERVER_ERROR")}:read(u))}
      await p.locator(".community-comment-sentinel").evaluate(el=>window.scrollTo(0,el.getBoundingClientRect().top+scrollY-innerHeight-120))
      await p.getByRole("button",{name:"댓글 조회 다시 시도"}).waitFor()
      assert.equal(await p.locator(".community-comment-list > li").count(),20)
      await p.locator("#comment-draft").fill("자동 조회 중지 초안")
      await p.getByRole("button",{name:"댓글 조회 다시 시도"}).click()
      await p.locator("#comment-40").waitFor()
      await p.locator(".community-comment-sentinel").scrollIntoViewIfNeeded()
      assert.equal(gets,2)
      assert.equal(await p.locator("#comment-draft").inputValue(),"자동 조회 중지 초안")
    }finally{await ctx.close()}
  })
  await check("receipts show consecutive confirmed posts immediately, merge only at real cursor end",async()=>{
    const {ctx,p,state}=await fixture(member,65)
    try{
      for(const text of ["방금 등록한 첫 내용","방금 등록한 두 번째 내용"]){
        await p.locator("#comment-draft").fill(text)
        await p.getByRole("button",{name:"댓글 등록",exact:true}).click()
        await p.locator(".community-comment-receipts").getByText(text,{exact:true}).waitFor()
        await p.waitForFunction(()=>!document.querySelector("#comments-refresh").disabled)
      }
      assert.equal(await p.locator("[data-created-comment-id]").count(),2)
      assert.equal(await p.locator(".community-comment-list > li").count(),20)
      assert.equal(state.calls.filter(c=>c.method==="GET"&&c.path.endsWith("/comments")).length,3)
      for(const width of [1440,390,320]){
        await p.setViewportSize({width,height:950})
        await p.locator(".community-comment-receipts").screenshot({path:`${output}/created-receipts-${width}.png`})
        assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      }
      for(const n of [40,60,67]){await p.getByRole("button",{name:"댓글 더 보기"}).click();await p.locator(`#comment-${n}`).waitFor()}
      assert.equal(await p.locator("[data-created-comment-id]").count(),0)
      assert.equal(await p.getByText("방금 등록한 첫 내용",{exact:true}).count(),1)
    }finally{await ctx.close()}
  })
  await check("confirmed receipt survives following GET failure without POST retry",async()=>{
    const {ctx,p,state}=await fixture(member,65)
    try{
      state.get=r=>r.fulfill({status:503,json:failure("SERVER_ERROR")})
      await p.locator("#comment-draft").fill("등록 완료 후 조회 오류")
      await p.getByRole("button",{name:"댓글 등록",exact:true}).click()
      await p.getByRole("button",{name:"댓글 조회 다시 시도"}).waitFor()
      assert(await p.locator(".community-comment-receipts").getByText("등록 완료 후 조회 오류",{exact:true}).isVisible())
      assert.equal(await p.locator("#comment-draft").inputValue(),"")
      assert.equal(state.calls.filter(c=>c.method==="POST").length,1)
    }finally{await ctx.close()}
  })
  await check("mobile noJS all topic and public navigation links visible and usable",async()=>{
    comments=[item(1)];readFail=false
    const ctx=await browser.newContext({javaScriptEnabled:false}),p=await ctx.newPage()
    try{for(const width of [320,390]){
      await p.setViewportSize({width,height:900});await p.goto(base+"/community")
      const links=p.locator(".feed-tabs a")
      assert.equal(await links.count(),8)
      for(const link of await links.all()) assert(await link.isVisible())
      for(const name of ["IYUM 소개","복지·지원정보","기기 정보","병원·전문기관"])
        assert(await p.locator(".iyum-header").getByRole("link",{name,exact:true}).isVisible())
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.screenshot({path:`${output}/nojs-navigation-${width}.png`,fullPage:true})
      await links.filter({hasText:"인공와우"}).click()
      assert.equal(new URL(p.url()).pathname,"/community/topics/cochlear-implant")
    }}finally{await ctx.close()}
  })
  await check("list count SSR/noJS: zero, positive, large; no per-post comment fetch",async()=>{
    for(const count of [0,3,Number.MAX_SAFE_INTEGER]){
      comments=Array.from({length:count===3?3:0},(_,i)=>item(i+1));readFail=false
      listCountOverride=count
      const originalTitle=post.title,originalNickname=post.author.nickname
      if(count===Number.MAX_SAFE_INTEGER){post.title="긴 제목 ".repeat(24);post.author.nickname="긴작성자이름".repeat(8)}
      const ctx=await browser.newContext({javaScriptEnabled:false}),p=await ctx.newPage()
      try {for(const route of ["/community","/community/topics/hearing-aid"]){
        ssrCommentRequests=0
        await p.goto(base+route)
        assert(await p.getByText(`댓글 ${count.toLocaleString("ko-KR")}`,{exact:true}).isVisible())
        assert.equal(ssrCommentRequests,0)
        for(const width of [1440,390,320]){
          await p.setViewportSize({width,height:950})
          assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
          if(count===Number.MAX_SAFE_INTEGER)await p.screenshot({path:output+`/count-${route.includes("topics")?"topic":"all"}-${width}.png`,fullPage:true})
        }
      }}finally{listCountOverride=null;post.title=originalTitle;post.author.nickname=originalNickname;await ctx.close()}
    }
  })
  await check("count POST/PUT/DELETE uses server list; back/link preserve context and drafts",async()=>{
    const {ctx,p,state}=await fixture(member,1)
    try{
      await p.goto(base+"/community/topics/hearing-aid?type=QUESTION")
      await p.getByText("댓글 1",{exact:true}).waitFor()
      await p.locator(".feed-posts h3 a").first().scrollIntoViewIfNeeded()
      const returnY=await p.evaluate(()=>scrollY)
      await p.locator(".feed-posts h3 a").first().click()
      await p.locator("#comment-draft").fill("추가 댓글")
      await p.getByRole("button",{name:"댓글 등록",exact:true}).click()
      await p.getByText("댓글을 등록했습니다.",{exact:true}).waitFor()
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("내용만 수정")
      await p.getByRole("button",{name:"댓글 저장",exact:true}).click()
      await p.getByText("댓글을 수정했습니다.",{exact:true}).waitFor()
      await p.goBack()
      await p.getByText("댓글 2",{exact:true}).waitFor()
      await p.waitForFunction(y=>Math.abs(scrollY-y)<5,returnY)
      assert.equal(new URL(p.url()).searchParams.get("type"),"QUESTION")
      await p.locator(".feed-posts h3 a").first().click()
      await p.locator("#comment-draft").fill("보존할 작성 초안")
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("보존할 수정 초안")
      await p.locator("#comment-2").getByRole("button",{name:"댓글 삭제",exact:true}).click()
      const deletes=()=>state.calls.filter(c=>c.method==="DELETE").length
      const beforeDelete=deletes()
      await p.getByRole("button",{name:"계속 수정",exact:true}).click()
      assert.equal(await p.locator("#comment-edit-1").inputValue(),"보존할 수정 초안")
      assert.equal(await p.locator("#comment-draft").inputValue(),"보존할 작성 초안")
      assert.equal(deletes(),beforeDelete)
      assert.equal(await p.getByRole("button",{name:"댓글 삭제하기",exact:true}).count(),0)
      await p.locator("#comment-2").getByRole("button",{name:"댓글 삭제",exact:true}).click()
      await p.getByRole("button",{name:"수정 내용 버리기",exact:true}).click()
      await p.getByRole("button",{name:"댓글 삭제하기",exact:true}).waitFor()
      assert.equal(await p.locator("#comment-edit-1").count(),0)
      assert.equal(deletes(),beforeDelete)
      assert.equal(await p.locator("#comment-draft").inputValue(),"보존할 작성 초안")
      await p.getByRole("button",{name:"댓글 삭제하기",exact:true}).click()
      await p.getByText("댓글을 삭제했습니다.",{exact:true}).waitFor()
      assert.equal(deletes(),beforeDelete+1)
      assert.equal(await p.locator("#comment-draft").inputValue(),"보존할 작성 초안")
      assert.equal(await p.locator("#comment-edit-1").count(),0)
      await p.locator(".community-back").click()
      await p.getByRole("button",{name:"나가기",exact:true}).click()
      await p.getByText("댓글 1",{exact:true}).waitFor()
      assert.equal(new URL(p.url()).searchParams.get("type"),"QUESTION")
      assert.equal(state.calls.filter(c=>c.method==="POST").length,1)
    }finally{await ctx.close()}
  })
  await check("late POST success invalidates list; superseded GET cannot overwrite count",async()=>{
    const {ctx,p,state}=await fixture(member,1)
    let release,releaseRead
    try{
      state.write=async r=>{await new Promise(resolve=>release=resolve);comments.push(item(2));await r.fulfill({status:201,json:{commentId:2}})}
      await p.locator("#comment-draft").fill("늦은 저장")
      await p.getByRole("button",{name:"댓글 등록",exact:true}).click();await until(()=>release)
      await p.locator(".community-back").click();await p.getByRole("button",{name:"나가기",exact:true}).click()
      await p.getByText("댓글 1",{exact:true}).waitFor()
      const location=p.url()
      // Complete the new authenticated likes read before controlling the explicit refresh.
      await p.waitForFunction(()=>document.querySelector('[data-like-ready="true"]')&&document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false')
      state.get=async(r,u)=>{
        if(u.pathname!=="/api/v1/community/posts/feed")return r.fulfill(read(u))
        const old=read(u);state.get=null
        await new Promise(resolve=>releaseRead=resolve)
        await r.fulfill(old).catch(()=>{})
      }
      // Explicit refresh starts an old feed GET; late mutation must supersede it.
      await p.getByRole("button",{name:"최신 목록 새로고침",exact:true}).click();await until(()=>releaseRead)
      release();await p.getByText("댓글 2",{exact:true}).waitFor()
      releaseRead();await p.waitForTimeout(100)
      assert(await p.getByText("댓글 2",{exact:true}).isVisible())
      assert.equal(await p.locator(".comment-success-toast").count(),0)
      assert.equal(new URL(p.url()).pathname,new URL(location).pathname)
      assert.equal(state.calls.filter(c=>c.method==="POST").length,1)
    }finally{release?.();releaseRead?.();await ctx.close()}
  })
  for(const code of ["COMMENT_NOT_FOUND","SERVER_ERROR"]) await check(`count reconciliation after ${code}; no automatic mutation replay`,async()=>{
    const {ctx,p,state}=await fixture(member,1)
    try{
      await p.goto(base+"/community/topics/hearing-aid")
      await p.getByText("댓글 1",{exact:true}).waitFor()
      await p.locator(".feed-posts h3 a").first().click()
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("유지할 초안")
      state.write=async r=>{comments=[];await r.fulfill({status:code === "COMMENT_NOT_FOUND"?404:500,json:failure(code)})}
      await p.getByRole("button",{name:"댓글 저장",exact:true}).click()
      if(code === "COMMENT_NOT_FOUND")await p.getByText("현재 이 댓글을 수정할 수 없어요.",{exact:true}).waitFor()
      else await p.locator(".community-comments .community-error").waitFor()
      if(code === "COMMENT_NOT_FOUND")assert.equal(await p.locator("#comment-edit-1").count(),0)
      else assert.equal(await p.locator("#comment-edit-1").inputValue(),"유지할 초안")
      await p.locator(".community-back").click();if(code !== "COMMENT_NOT_FOUND")await p.getByRole("button",{name:"나가기",exact:true}).click()
      await p.getByText("댓글 0",{exact:true}).waitFor()
      assert.equal(state.calls.filter(c=>c.method === "PUT").length,1)
    }finally{await ctx.close()}
  })
  // Design change: await first comments on the server so no-JS readers see real content.
  for (const fail of [false, true]) await check(`delayed server comments resolve before render; failure=${fail}`, async () => {
    comments=[item(1)];readFail=false;ssrReadReached=false
    let release
    ssrGate=new Promise(resolve=>release=resolve)
    const ctx=await browser.newContext(),p=await ctx.newPage()
    let commentReads=0
    await p.route(u=>u.origin===api&&u.pathname.endsWith("/comments"),r=>r.fulfill(read(new URL(r.request().url()))))
    p.on("request",r=>{if(new URL(r.url()).origin===api&&new URL(r.url()).pathname.endsWith("/comments"))commentReads++})
    await p.route(u=>u.origin===api&&u.pathname.endsWith("/me"),r=>r.fulfill({status:401,json:failure("AUTHENTICATION_REQUIRED")}))
    try {
      const navigation=p.goto(base+"/community/posts/7")
      await until(()=>ssrReadReached)
      readFail=fail;ssrGate=null;release()
      const response=await navigation
      assert.equal(response.status(),200)
      assert(await p.getByText(post.content,{exact:true}).isVisible())
      await p.waitForFunction(() => document.querySelector("#comments-refresh")?.disabled === false)
      assert.equal(commentReads,1)
      if(fail){
        assert(await p.locator(".community-comments [role=alert]").isVisible())
        await p.route(u=>u.origin===api&&u.pathname.endsWith("/comments"),r=>r.fulfill({json:{items:[item(1)],hasNext:false,nextCursor:null}}))
        await p.getByRole("button",{name:"댓글 조회 다시 시도"}).click()
        await p.locator("#comment-1").waitFor()
      } else assert(await p.locator("#comment-1").isVisible())
    } finally {ssrGate=null;release();readFail=false;await ctx.close()}
  })
  await check("empty creation shows notice toast; other field errors remain below textarea", async()=>{
    const {ctx,p,state}=await fixture(member,3)
    const draft=p.locator("#comment-draft")
    for(const value of [""," \n\t","😀".repeat(2001)]){
      await draft.fill(value)
      await p.getByRole("button",{name:"댓글 등록",exact:true}).click()
      if (!value.includes("😀")) {
        await p.locator('.comment-success-toast.is-notice').getByText('내용을 입력해 주세요.',{exact:true}).waitFor()
        assert.equal(await p.getByText('댓글 내용은 필수입니다.',{exact:true}).count(),0)
        assert.equal(await p.locator('#comment-draft-error').count(),0)
        assert.equal(await draft.inputValue(),value)
        continue
      }
      const expected="댓글 내용은 2,000자 이하여야 합니다."
      assert.equal(await p.getByText(expected,{exact:true}).count(),1)
      assert(await draft.evaluate((el,msg)=>el.nextElementSibling?.textContent===msg,expected))
      assert.equal(await draft.getAttribute("aria-invalid"),"true")
      assert((await draft.getAttribute("aria-describedby")).includes("comment-draft-error"))
      assert.equal(await p.evaluate(()=>document.activeElement.id),"comment-draft")
    }
    assert.equal(state.calls.filter(c=>c.method==="POST").length,0)
    await draft.fill("유효한 작성 초안")
    assert.equal(await p.locator("#comment-draft-error").count(),0)
    await p.locator("#comment-edit-button-1").click()
    const edit=p.locator("#comment-edit-1")
    for(const value of [""," \n","😀".repeat(2001)]){
      await edit.fill(value)
      await p.getByRole("button",{name:"댓글 저장",exact:true}).click()
      const expected=value.includes("😀")?"댓글 내용은 2,000자 이하여야 합니다.":"댓글 내용은 필수입니다."
      assert.equal(await p.getByText(expected,{exact:true}).count(),1)
      assert(await edit.evaluate((el,msg)=>el.nextElementSibling?.textContent===msg,expected))
      assert.equal(await edit.getAttribute("aria-describedby"),"comment-edit-1-error")
    }
    state.write=r=>r.fulfill({status:400,json:{code:"VALIDATION_FAILED",message:"일반 메시지는 중복 표시하지 않음",fieldErrors:[{field:"content",message:"댓글 내용은 필수입니다."}]}})
    for(const [input,button,errorId] of [[edit,"댓글 저장","comment-edit-1-error"],[draft,"댓글 등록","comment-draft-error"]]){
      await input.fill("서버 검증용 초안")
      await p.getByRole("button",{name:button,exact:true}).click()
      await p.locator("#"+errorId).waitFor()
      assert(await input.evaluate(el=>el.nextElementSibling.id===el.getAttribute("aria-describedby").split(" ").at(-1)))
      assert.equal(await p.getByText("댓글 내용은 필수입니다.",{exact:true}).count(),1)
      await input.fill("수정 후 유효한 초안")
      assert.equal(await p.locator("#"+errorId).count(),0)
      assert.equal(await input.getAttribute("aria-invalid"),"false")
    }
    for(const width of [390,320]){await p.setViewportSize({width,height:900});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.locator("#comments-refresh").focus();assert.equal(await p.locator("#comments-refresh svg[aria-hidden=true]").getAttribute("focusable"),"false");await p.screenshot({path:output+"/review-fields-"+width+".png",fullPage:true})}
    await ctx.close()
  })
  for(const method of ["refresh","PUT404"]) await check("list absence retains edit; definitive PUT failure clears only its dirty state: "+method,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    await p.locator("#comment-draft").fill("별도의 새 댓글 초안")
    await p.locator("#comment-edit-button-1").click()
    await p.locator("#comment-edit-1").fill("사라지면 안 되는 수정 초안")
    if(method==="refresh"){
      comments=comments.filter(c=>c.commentId!==1)
      await p.locator("#comments-refresh").click()
    }else{
      state.write=r=>{comments=comments.filter(c=>c.commentId!==1);return r.fulfill({status:404,json:failure("COMMENT_NOT_FOUND")})}
      await p.getByRole("button",{name:"댓글 저장",exact:true}).click()
    }
    await p.waitForFunction(()=>document.querySelector('#comments-refresh')?.disabled===false&&!document.querySelector('#comment-1'))
    assert.equal(await p.locator('.community-comment-detached,#comment-recovery-title').count(),0)
    assert.equal(await p.getByRole('dialog').count(),0)
    if(method==="refresh") {
      assert.equal(await p.locator('#comment-edit-1').inputValue(),'사라지면 안 되는 수정 초안')
      assert(await p.getByRole('button',{name:'댓글 저장',exact:true}).isEnabled())
    }
    if(method==="PUT404") {
      const toast=p.locator('.comment-success-toast.is-notice')
      await toast.waitFor()
      assert.equal(await toast.textContent(),"현재 이 댓글을 수정할 수 없어요.")
      assert.equal(await p.locator('.community-comments-status').textContent(),"")
      assert.equal(await p.getByRole('button',{name:'댓글 저장',exact:true}).count(),0)
      assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
      assert.equal(await p.locator('#comment-1').count(),0)
      for(const width of [390,320]) {
        await p.setViewportSize({width,height:900})
        assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
        const bounds=await toast.boundingBox()
        assert(bounds.x>=0&&bounds.x+bounds.width<=width)
      }
      await p.screenshot({path:output+'/unavailable-edit-toast-320.png',fullPage:true})
    }
    if(method==="refresh") {
      await p.getByRole("button",{name:"수정 취소",exact:true}).click()
      await p.getByRole("button",{name:"계속 수정",exact:true}).click()
      assert.equal(await p.locator("#comment-edit-1").inputValue(),"사라지면 안 되는 수정 초안")
      await p.getByRole("button",{name:"수정 취소",exact:true}).click()
      await p.getByRole("button",{name:"수정 내용 버리기",exact:true}).click()
    }
    assert.equal(await p.locator("#comment-edit-1").count(),0)
    assert(await p.locator("#comment-edit-button-3").isEnabled())
    assert.equal(await p.locator("#comment-draft").inputValue(),"별도의 새 댓글 초안")
    await p.locator(".community-back").click();await p.getByRole("button",{name:"계속 작성",exact:true}).click()
    await p.locator("#comment-draft").fill("")
    await p.locator(".community-back").click();await p.waitForURL(u=>u.pathname==="/community/topics/hearing-aid")
    assert.equal(await p.getByRole("dialog").count(),0)
    await ctx.close()
  })
  // Independent cases: a failed assertion must not prevent error/empty coverage.
  for (const mode of ["success", "empty", "failure"]) await check(`no-JS visible comments: ${mode}`, async()=>{
    comments=mode === "empty" ? [] : [item(1)];readFail=mode === "failure"
    const ctx=await browser.newContext({javaScriptEnabled:false}),p=await ctx.newPage()
    try {
      const response=await p.goto(base+"/community/posts/7")
      assert.equal(response.status(),200)
      assert(await p.getByText(post.content,{exact:true}).isVisible())
      if(mode === "success") assert(await p.getByText("댓글 예시 1",{exact:false}).isVisible())
      if(mode === "empty") assert(await p.getByText("아직 댓글이 없습니다. 첫 이야기를 나눠 주세요.",{exact:true}).isVisible())
      if(mode === "failure") assert(await p.getByText(/댓글을 불러오지 못했습니다/).isVisible())
      await p.screenshot({path:output+`/nojs-comments-${mode}.png`,fullPage:true})
    } finally {readFail=false;await ctx.close()}
  })
  await check("refresh SVG and keyboard access at 1440/390/320", async()=>{
    const {ctx,p}=await fixture(member,3)
    try {for(const width of [1440,390,320]){
      await p.setViewportSize({width,height:900})
      const button=p.locator("#comments-refresh"),svg=button.locator("svg")
      assert.equal(await button.innerText(),"목록 새로고침")
      assert.equal(await svg.getAttribute("aria-hidden"),"true")
      assert.equal(await svg.getAttribute("focusable"),"false")
      assert.equal(await svg.getAttribute("stroke"),"currentColor")
      assert.equal((await svg.boundingBox()).width,16)
      await p.locator("#comment-draft").focus()
      await button.focus();await p.keyboard.press("Tab");await p.keyboard.press("Shift+Tab")
      assert(await button.evaluate(el=>el===document.activeElement && el.matches(":focus-visible")))
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.screenshot({path:output+`/refresh-${width}.png`,fullPage:true})
    }} finally {await ctx.close()}
  })
  await check(
    "cursor precision, more dedup/duplicate click, failed more retains list",
    async () => {
      const { ctx, p, state } = await fixture()
      let release
      state.get = async (r, u) => {
        assert.equal(u.searchParams.get("afterCreatedAt"), item(1).createdAt)
        await new Promise((resolve) => (release = resolve))
        await r.fulfill({ status: 503, json: failure("SERVER_ERROR") })
      }
      await p.getByRole("button", { name: "댓글 더 보기", exact: true }).click()
      await until(() => release)
      assert(
        await p
          .getByRole("button", { name: "댓글 더 보기", exact: true })
          .isDisabled(),
      )
      assert.equal(
        state.calls.filter((c) => c.path.endsWith("/comments")).length,
        2,
      )
      release()
      await p.locator(".community-comments [role=alert]").waitFor()
      assert.equal(await p.locator(".comment-success-toast").count(),0)
      assert.equal(await p.locator(".community-comment-list > li").count(), 20)
      state.get = null
      await p.getByRole("button", { name: "댓글 더 보기", exact: true }).click()
      await p.locator("#comment-22").waitFor()
      assert.equal(await p.locator(".community-comment-list > li").count(), 22)
      await ctx.close()
    },
  )
  await check(
    "own controls; create >20, update, cancel/delete/focus, responsive",
    async () => {
      const { ctx, p } = await fixture()
      assert.equal(await p.locator("#comment-2").getByRole("button", { name: /^댓글 (수정|삭제)$/, exact: true }).count(), 0)
      await p.locator("#comment-draft").fill("  새 댓글\n줄바꿈 유지")
      await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
      await p.getByText("댓글을 등록했습니다.", { exact: true }).waitFor()
      assert.equal(await p.locator("#comment-draft").inputValue(), "")
      await p.getByRole("button", { name: "댓글 더 보기", exact: true }).click()
      await p.locator("#comment-23").waitFor()
      assert.equal(
        await p.locator("#comment-23 .community-comment-body").textContent(),
        "  새 댓글\n줄바꿈 유지",
      )
      await p
        .locator("#comment-23")
        .getByRole("button", { name: "댓글 수정", exact: true })
        .click()
      await p.locator("#comment-edit-23").fill("수정된 댓글")
      await p.getByRole("button", { name: "댓글 저장", exact: true }).click()
      await p.getByText("수정된 댓글", { exact: true }).waitFor()
      await p
        .locator("#comment-23")
        .getByRole("button", { name: "댓글 삭제", exact: true })
        .click()
      assert.equal(
        await p.evaluate(() => document.activeElement.textContent),
        "취소",
      )
      await p.keyboard.press("Escape")
      assert.equal(
        await p.evaluate(() => document.activeElement.getAttribute("aria-label")),
        "댓글 삭제",
      )
      await p
        .locator("#comment-23")
        .getByRole("button", { name: "댓글 삭제", exact: true })
        .click()
      await p
        .getByRole("button", { name: "댓글 삭제하기", exact: true })
        .click()
      await p.getByText("댓글을 삭제했습니다.", { exact: true }).waitFor()
      assert.equal(await p.locator("#comment-23").count(), 0)
      assert.equal(
        await p.evaluate(() => document.activeElement.id),
        "comments-refresh",
      )
      for (const width of [1440, 390, 320]) {
        await p.setViewportSize({ width, height: 950 })
        assert(
          await p.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
        )
        await p
          .locator(".community-comments")
          .screenshot({ path: `${output}/comments-${width}.png` })
      }
      await ctx.close()
    },
  )
  await check("anonymous login CTA: pointer, keyboard, 1440/390/320 and safe return; no writes", async () => {
    const { ctx, p, state } = await fixture(null, 1)
    const link = p.getByRole("button", { name: "댓글 등록", exact: true })
    assert.equal(await p.locator(".community-comment-list").getByRole("button", { name: /^댓글 (수정|삭제)$/, exact: true }).count(), 0)
    const destination = "/login?from=" + encodeURIComponent("/community/posts/7?from=" + encodeURIComponent("/community/topics/hearing-aid"))
    assert(destination.startsWith("/login?from="))
    for (const width of [1440, 390, 320]) {
      await p.setViewportSize({ width, height: 950 })
      await link.hover()
      assert.equal(await link.evaluate(el => getComputedStyle(el).cursor), "pointer")
      assert.equal(await p.locator("#comment-auth-help").count(), 0)
      assert.equal(await p.getByRole('dialog').count(), 0)
      assert.equal(await p.locator('#comment-draft').getAttribute('placeholder'), '로그인 후 댓글을 남길 수 있어요')
      assert.equal(await p.getByLabel('댓글 남기기', {exact:true}).count(), 1)
      assert(!(await p.locator('#comment-draft').getAttribute('aria-describedby')).includes('comment-auth-help'))
      await p.screenshot({ path: `${output}/anonymous-compose-${width}.png` })
      assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      await link.click()
      const before = await p.evaluate(() => scrollY)
      assert.equal(new URL(p.url()).pathname, "/community/posts/7")
      assert.equal(await p.getByText('로그인 화면으로 이동하면 입력한 내용은 보관되지 않아요.', {exact:true}).count(), 0)
      await p.getByRole("dialog").screenshot({ path: `${output}/login-confirm-${width}.png` })
      await p.keyboard.press("Escape")
      assert(await link.evaluate(el => document.activeElement === el))
      assert(Math.abs(await p.evaluate(() => scrollY) - before) < 4)
    }
    await link.click()
    await p.getByRole("button", {name:"로그인하기",exact:true}).click()
    await p.waitForURL(u => u.pathname === "/login" && u.searchParams.get("from") === "/community/posts/7?from=" + encodeURIComponent("/community/topics/hearing-aid"))
    await p.goBack(); await link.waitFor(); await link.focus(); await p.keyboard.press("Enter")
    await p.getByRole("button", {name:"로그인하기",exact:true}).click()
    await p.waitForURL(u => u.pathname === "/login")
    assert.equal(state.calls.filter(c => c.method !== "GET").length, 0)
    assert.equal(state.calls.filter(c => c.path.endsWith("/csrf")).length, 0)
    await ctx.close()
  })
  await check("anonymous login with draft: cancel preserves draft/focus; explicit leave allows login", async () => {
    const { ctx, p, state } = await fixture(null, 1)
    const link = p.getByRole("button", { name: "댓글 등록", exact: true })
    await p.locator("#comment-draft").fill("로그인 전에 작성한 초안")
    await link.click(); await p.getByRole("dialog").waitFor()
    assert(await p.getByRole('dialog').getByText('로그인 화면으로 이동하면 입력한 내용은 보관되지 않아요.', {exact:true}).isVisible())
    await p.getByRole("button", { name: "그대로 머무르기", exact: true }).click()
    assert.equal(await p.locator("#comment-draft").inputValue(), "로그인 전에 작성한 초안")
    assert(await link.evaluate(el => document.activeElement === el))
    await link.click(); await p.getByRole('dialog').waitFor(); await p.keyboard.press('Escape')
    assert.equal(await p.locator('#comment-draft').inputValue(), '로그인 전에 작성한 초안')
    assert(await link.evaluate(el => document.activeElement === el))
    assert.equal(new URL(p.url()).pathname, "/community/posts/7")
    await link.focus(); await p.keyboard.press("Enter")
    await p.getByRole("button", { name: "로그인하기", exact: true }).click()
    await p.waitForURL(u => u.pathname === "/login")
    assert.equal(state.calls.filter(c => c.method !== "GET").length, 0)
    await ctx.close()
  })
  for (const outcome of ["success", "recovered", "failure"]) {
    await check(`logout ${outcome}: detail URL/body, compose draft and focus remain; edit survives only failed logout`, async () => {
      const { ctx, p, state } = await fixture(member, 1)
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("편집 중인 댓글 초안")
      await p.locator("#comment-draft").fill("새 댓글 초안")
      let release
      const gate = new Promise(r => { release = r })
      state.logout = async r => {
        await gate
        if (outcome === "failure") return r.fulfill({ status: 403, json: failure("CSRF_TOKEN_INVALID") })
        state.identity = null
        return outcome === "recovered" ? r.abort("failed") : r.fulfill({ status: 204 })
      }
      const original = p.url()
      await p.getByLabel("회원 메뉴").getByRole("button", { name: "로그아웃", exact: true }).click()
      await until(() => state.calls.some(c => c.path.endsWith("/logout")))
      assert.equal(await p.getByRole("dialog").count(), 0)
      await p.locator("#comment-draft").fill("로그아웃 대기 중 추가한 초안")
      const before = await p.evaluate(() => scrollY)
      release()
      if (outcome === "failure") await p.getByRole("banner").getByRole("alert").waitFor()
      else await p.getByPlaceholder("로그인 후 댓글을 남길 수 있어요", {exact:true}).waitFor()
      assert.equal(p.url(), original)
      assert(await p.locator(".community-post-body").isVisible())
      assert.equal(await p.locator("#comment-draft").inputValue(), "로그아웃 대기 중 추가한 초안")
      if (outcome === "failure") assert.equal(await p.locator("#comment-edit-1").inputValue(), "편집 중인 댓글 초안")
      else assert.equal(await p.locator("#comment-edit-1").count(),0)
      assert(await p.locator("#comment-draft").evaluate(el => document.activeElement === el))
      assert(Math.abs(await p.evaluate(() => scrollY) - before) < 4)
      assert.equal(state.calls.filter(c => c.path.endsWith("/logout")).length, 1)
      assert.equal(state.calls.filter(c => c.path.endsWith("/comments") && c.method === "POST").length, 0)
      if (outcome === "success") await p.locator(".community-comments").screenshot({ path: `${output}/logout-drafts-preserved.png` })
      await p.getByRole("banner").getByRole("link", { name: "IYUM 홈으로 이동", exact: true }).click()
      await p.getByRole("dialog").waitFor()
      await p.getByRole("button", { name: "계속 작성", exact: true }).click()
      assert.equal(await p.locator("#comment-draft").inputValue(), "로그아웃 대기 중 추가한 초안")
      await ctx.close()
    })
  }
  await check("session check pending/error: honest pending label, manual retry, no comment write", async () => {
    const { ctx, p, state } = await fixture(null, 1)
    let release, failed = true
    const gate = new Promise(r => { release = r })
    await p.route(u => u.origin === api && u.pathname.endsWith("/me"), async r => {
      await gate
      return r.fulfill(failed ? {status:503,json:failure("SERVER_ERROR")} : {status:401,json:failure("AUTHENTICATION_REQUIRED")})
    })
    await p.reload()
    const waiting = p.locator(".community-comments").getByRole("button", {name:"로그인 확인 중…",exact:true})
    await waiting.waitFor(); assert(await waiting.isDisabled())
    release()
    const retry = p.locator(".community-comments").getByRole("button", {name:"로그인 상태 다시 확인",exact:true})
    await retry.waitFor(); failed = false; await retry.click()
    await p.getByRole("button", {name:"댓글 등록",exact:true}).waitFor()
    assert.equal(state.calls.filter(c => c.method !== "GET").length, 0)
    await ctx.close()
  })
  await check(
    "write success + refresh failure stays success and clears only submitted draft",
    async () => {
      const { ctx, p, state } = await fixture(member, 1)
      state.write = async (r) => {
        readFail = true
        await r.fulfill({ status: 201, json: { commentId: 2 } })
      }
      await p.locator("#comment-draft").fill("성공")
      await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
      await p.getByText("댓글을 등록했습니다.", { exact: true }).waitFor()
      await p.locator(".community-comments [role=alert]").waitFor()
      assert.equal(await p.locator(".comment-success-toast").count(),1)
      assert.equal(await p.locator("#comment-draft").inputValue(), "")
      assert.equal(state.calls.filter((c) => c.method === "POST").length, 1)
      assert.equal(await p.locator("#comment-1").count(), 1)
      await ctx.close()
    },
  )
  await check(
    "refresh never overwrites inline draft; combined dirty and discard",
    async () => {
      const { ctx, p } = await fixture(member, 1)
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("편집 초안")
      await p.locator("#comment-draft").fill("새 댓글 초안")
      comments = [{ ...item(1), content: "서버 변경" }]
      await p.locator("#comments-refresh").click()
      await p.waitForFunction(
        () => !document.getElementById("comments-refresh").disabled,
      )
      assert.equal(await p.locator("#comment-edit-1").inputValue(), "편집 초안")
      await p.getByRole("button", { name: "수정 취소", exact: true }).click()
      await p
        .getByRole("button", { name: "수정 내용 버리기", exact: true })
        .click()
      await p.locator(".community-back").click()
      await p.getByRole("button", { name: "계속 작성", exact: true }).click()
      assert.equal(
        await p.locator("#comment-draft").inputValue(),
        "새 댓글 초안",
      )
      await ctx.close()
    },
  )
  for (const phase of ["csrf", "success", "failure"])
    await check(
      "approved departure while destination delayed: " + phase,
      async () => {
        const { ctx, p, state } = await fixture(member, 1)
        let release, destination
        const delayed = async (r) => {
          await new Promise((resolve) => (release = resolve))
          await r.fulfill(
            phase === "csrf"
              ? { json: { headerName: "X-CSRF-TOKEN", token: "test-only" } }
              : phase === "success"
                ? { status: 201, json: { commentId: 2 } }
                : { status: 500, json: failure("SERVER_ERROR") },
          )
        }
        if (phase === "csrf") state.csrf = delayed
        else state.write = delayed
        await p.route(
          (u) =>
            u.origin === new URL(base).origin &&
            u.pathname === "/community/topics/hearing-aid",
          async (r) => {
            await new Promise((resolve) => (destination = resolve))
            await r.continue()
          },
        )
        await p.locator("#comment-draft").fill("이탈 초안")
        await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
        await until(() => release)
        await p.locator(".community-back").click()
        await p.getByRole("button", { name: "나가기", exact: true }).click()
        await until(() => destination)
        assert.equal(
          await p.locator("#comment-draft").inputValue(),
          "이탈 초안",
        )
        release()
        await new Promise((r) => setTimeout(r, 150))
        assert.equal(
          await p.locator("#comment-draft").inputValue(),
          "이탈 초안",
        )
        if (phase === "csrf")
          assert.equal(state.calls.filter((c) => c.method === "POST").length, 0)
        destination()
        await p.waitForURL(
          (u) => u.pathname === "/community/topics/hearing-aid",
        )
        await ctx.close()
      },
    )
  for (const [status, code] of [
    [401, "AUTHENTICATION_REQUIRED"],
    [403, "CSRF_TOKEN_INVALID"],
    [403, "COMMENT_WRITE_NOT_ALLOWED"],
    [403, "ACCESS_DENIED"],
    [404, "POST_NOT_FOUND"],
    [500, "SERVER_ERROR"],
  ])
    await check("write error preserves input " + code, async () => {
      const { ctx, p, state } = await fixture(member, 1)
      state.write = (r) => r.fulfill({ status, json: failure(code) })
      await p.locator("#comment-draft").fill("보존할 댓글")
      await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
      if(status===401){
        await p.getByRole('dialog',{name:'다시 로그인이 필요해요'}).waitFor()
        assert.equal(await p.locator('.iyum-auth-notice,.community-comments [role=alert]').count(),0)
      }else await p.locator(".community-comments [role=alert]").waitFor()
      assert.equal(await p.locator(".comment-success-toast").count(),0)
      assert.equal(
        await p.locator("#comment-draft").inputValue(),
        "보존할 댓글",
      )
      assert(
        !(await p.locator(".community-comments").textContent()).includes(
          "do not expose",
        ),
      )
      assert.equal(state.calls.filter((c) => c.method === "POST").length, 1)
      await ctx.close()
    })
  await check(
    "404 deleted comment removed without deleting other comments",
    async () => {
      const { ctx, p, state } = await fixture(member, 2)
      state.write = (r) => {
        comments = comments.filter(c => c.commentId !== 1)
        return r.fulfill({ status: 404, json: failure("COMMENT_NOT_FOUND") })
      }
      await p
        .locator("#comment-1")
        .getByRole("button", { name: "댓글 삭제", exact: true })
        .click()
      await p
        .getByRole("button", { name: "댓글 삭제하기", exact: true })
        .click()
      await p
        .getByText("댓글이 없거나 이미 삭제되었습니다.", { exact: true })
        .waitFor()
      assert.equal(await p.locator("#comment-1").count(), 0)
      assert.equal(await p.locator("#comment-2").count(), 1)
      await ctx.close()
    },
  )

  await check(
    "old successful PUT after reentry cannot overwrite new draft",
    async () => {
      const { ctx, p, state } = await fixture(member, 1)
      let release
      state.write = async (r) => {
        await new Promise((resolve) => (release = resolve))
        comments = [{ ...item(1), content: "이전 저장 결과" }]
        await r.fulfill({ status: 204 })
      }
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("이전 요청")
      await p.getByRole("button", { name: "댓글 저장", exact: true }).click()
      await until(() => release)
      await p.locator(".community-back").click()
      await p.getByRole("button", { name: "나가기", exact: true }).click()
      await p.waitForURL((u) => u.pathname === "/community/topics/hearing-aid")
      await p.getByRole("link", { name: post.title, exact: true }).click()
      await p.locator("#comment-edit-button-1").click()
      await p.locator("#comment-edit-1").fill("새 편집 초안")
      const url = p.url()
      release()
      await new Promise((r) => setTimeout(r, 200))
      assert.equal(
        await p.locator("#comment-edit-1").inputValue(),
        "새 편집 초안",
      )
      assert.equal(p.url(), url)
      assert.equal(
        await p.evaluate(() => document.activeElement.id),
        "comment-edit-1",
      )
      await p.locator(".community-back").click()
      await p.getByRole("button", { name: "계속 작성", exact: true }).click()
      assert.equal(
        await p.locator("#comment-edit-1").inputValue(),
        "새 편집 초안",
      )
      await ctx.close()
    },
  )
  await check("late GET cannot resurrect deleted item", async () => {
    const { ctx, p, state } = await fixture(member, 2)
    let release
    state.get = async (r, u) => {
      const snapshot = read(u)
      await new Promise((resolve) => (release = resolve))
      await r.fulfill(snapshot)
    }
    await p.locator("#comments-refresh").click()
    await until(() => release)
    await p
      .locator("#comment-1")
      .getByRole("button", { name: "댓글 삭제", exact: true })
      .click()
    await p.getByRole("button", { name: "댓글 삭제하기", exact: true }).click()
    await p.getByText("댓글을 삭제했습니다.", { exact: true }).waitFor()
    release()
    await new Promise((r) => setTimeout(r, 150))
    assert.equal(await p.locator("#comment-1").count(), 0)
    assert.equal(await p.locator("#comment-2").count(), 1)
    await ctx.close()
  })
  await check(
    "unicode validation, server field focus, cancel departure keeps request alive",
    async () => {
      const { ctx, p, state } = await fixture(member, 1)
      await p.locator("#comment-draft").fill("😀".repeat(2001))
      await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
      assert.equal(state.calls.filter((c) => c.method === "POST").length, 0)
      assert.equal(
        await p.evaluate(() => document.activeElement.id),
        "comment-draft",
      )
      state.write = (r) =>
        r.fulfill({
          status: 400,
          json: {
            code: "VALIDATION_FAILED",
            message: "private",
            fieldErrors: [
              {
                field: "content",
                message: "댓글 내용은 2,000자 이하여야 합니다.",
              },
            ],
          },
        })
      await p.locator("#comment-draft").fill("😀".repeat(2000))
      await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
      await until(() => state.calls.some((c) => c.method === "POST"))
      await p.waitForFunction(
        () =>
          !document.querySelector(".community-comment-form button").disabled,
      )
      assert.equal(
        await p.locator("#comment-draft").inputValue(),
        "😀".repeat(2000),
      )
      let release
      state.write = async (r) => {
        await new Promise((resolve) => (release = resolve))
        await r.fulfill({ status: 201, json: { commentId: 2 } })
      }
      await p.getByRole("button", { name: "댓글 등록", exact: true }).click()
      await until(() => release)
      await p.locator(".community-back").click()
      await p.getByRole("button", { name: "계속 작성", exact: true }).click()
      release()
      await p.getByText("댓글을 등록했습니다.", { exact: true }).waitFor()
      assert.equal(await p.locator("#comment-draft").inputValue(), "")
      await ctx.close()
    },
  )

  for (const kind of ["html", "broken-json", "network", "csrf-failure"]) await check("browser malformed/transport preserves draft: " + kind, async () => {
    const { ctx, p, state } = await fixture(member, 1)
    if (kind === "csrf-failure") state.csrf = r => r.fulfill({status:503,json:failure("SERVER_ERROR")})
    else state.write = r => kind === "network" ? r.abort("failed") : r.fulfill({status:201,contentType:kind === "html" ? "text/html" : "application/json",body:kind === "html" ? "<h1>private details</h1>" : "{"})
    await p.locator("#comment-draft").fill("결과 확인 전 초안")
    await p.getByRole("button",{name:"댓글 등록",exact:true}).click()
    await p.locator(".community-comments [role=alert]").waitFor()
    assert.equal(await p.locator("#comment-draft").inputValue(),"결과 확인 전 초안")
    assert.equal(state.calls.filter(c=>c.method==="POST").length,kind === "csrf-failure" ? 0 : 1)
    assert(!(await p.locator(".community-comments").textContent()).includes("private details"))
    await ctx.close()
  })
  await check("long content 1440/390/320, keyboard focus and dialog loop", async () => {
    const { ctx,p } = await fixture(member,1)
    comments=[{...item(1),content:"긴댓글".repeat(600),author:{memberId:123,nickname:"긴작성자이름".repeat(8)}}]
    await p.locator("#comments-refresh").click()
    await p.getByText("긴댓글".repeat(600),{exact:true}).waitFor()
    for (const width of [1440,390,320]) {
      await p.setViewportSize({width,height:950})
      assert(await p.locator('#comment-draft').evaluate(el => {
        const list=document.querySelector('.community-comment-list')
        return !!(el.compareDocumentPosition(list) & Node.DOCUMENT_POSITION_FOLLOWING) && el.getBoundingClientRect().bottom < list.getBoundingClientRect().top
      }))
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.locator("#comment-edit-button-1").focus();await p.keyboard.press("Tab")
      assert.equal(await p.evaluate(()=>document.activeElement.getAttribute("aria-label")),"댓글 삭제")
      await p.screenshot({path:output+"/comments-keyboard-long-"+width+".png",fullPage:true})
      await p.keyboard.press("Enter");await p.keyboard.press("Shift+Tab")
      assert.equal(await p.evaluate(()=>document.activeElement.textContent),"댓글 삭제하기")
      await p.keyboard.press("Escape");assert.equal(await p.evaluate(()=>document.activeElement.getAttribute("aria-label")),"댓글 삭제")
    }
    await ctx.close()
  })
  await check('pending and late POST after logout never show a success toast',async()=>{
    const {ctx,p,state}=await fixture(member,0)
    let release,complete
    const completed=new Promise(resolve=>complete=resolve)
    try {
      state.write=async r=>{await new Promise(resolve=>release=resolve);await r.fulfill({status:201,json:{commentId:1}});complete()}
      await p.locator('#comment-draft').fill('늦은 계정 응답')
      await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await until(()=>release)
      assert.equal(await p.locator('.comment-success-toast').count(),0)
      await p.getByRole('button',{name:'로그아웃',exact:true}).first().click()
      await p.getByPlaceholder('로그인 후 댓글을 남길 수 있어요',{exact:true}).waitFor()
      release();await completed
      await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
      assert.equal(await p.locator('.comment-success-toast').count(),0)
    } finally {release?.();await ctx.close()}
  })
  await check('visible toast is disposed on departure',async()=>{
    const {ctx,p}=await fixture(member,0)
    try {
      await p.clock.install()
      await p.locator('#comment-draft').fill('이탈 전 성공')
      await p.getByRole('button',{name:'댓글 등록',exact:true}).click()
      await p.locator('.comment-success-toast').waitFor()
      await p.locator('.community-back').click()
      await p.waitForURL(u=>!u.pathname.includes('/posts/7'))
      assert.equal(await p.locator('.comment-success-toast').count(),0)
      await p.clock.runFor(6000)
      assert.equal(await p.locator('.comment-success-toast').count(),0)
    } finally {await ctx.close()}
  })
} finally {
  await fs.writeFile(
    output + "/results.json",
    JSON.stringify({ kind: "mock", results }, null, 2),
  )
  await browser.close()
  await new Promise((r) => server.close(r))
}
