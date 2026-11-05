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
const item = (id) => ({
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
  else await p.getByRole("button", { name: "로그인하고 댓글 등록", exact: true }).waitFor()
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
try {
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
 await check('older refresh response cannot undo a successful like',async()=>{
  const{p,state}=await fixture(member,3);await settled(p);const g=gate();state.get=async(r,u,result)=>{if(u.pathname.endsWith('/comments'))await g.hold();await r.fulfill(result)}
  await p.locator('#comments-refresh').click();await g.reached;await btn(p).click();await settled(p,1,true,1);g.release();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled);await settled(p,1,true,1)
 })
 await check('later-page COMMENT_NOT_FOUND rereads loaded prefix, removes only missing comment, preserves editor',async()=>{
  const{p,state}=await fixture(member,45);await settled(p);await p.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await settled(p,21);await p.locator('#comment-edit-button-21').click();await p.locator('#comment-edit-21').fill('보관해야 할 편집')
  state.write=r=>{comments=comments.filter(c=>c.commentId!==21);return r.fulfill({status:404,json:failure('COMMENT_NOT_FOUND')})};await btn(p,21).click();await reaction(p,21).getByRole('alert').waitFor();const before=state.calls.filter(c=>c.path.endsWith('/comments')&&c.method==='GET').length;await btn(p,21).click();await p.locator('.community-comment-detached').waitFor();assert.equal(await p.locator('#comment-edit-21').inputValue(),'보관해야 할 편집');assert(await p.locator('.community-post-body').isVisible());assert(state.calls.filter(c=>c.path.endsWith('/comments')&&c.method==='GET').length>=before+3)
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
