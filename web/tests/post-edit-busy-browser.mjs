// 프로덕션 Next + SSR/브라우저 모의 API. 실제 Spring 검증과 구분한다.
import http from "node:http"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
import path from "node:path"
import os from "node:os"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8451",
  api = process.env.API_BASE_URL ?? "http://127.0.0.1:8082"
const output =
  process.env.COMMUNITY_ARTIFACT_DIR ??
  (await fs.mkdtemp(path.join(os.tmpdir(), "iyum-edit-")))
await fs.mkdir(output, { recursive: true })
const member = {
  memberId: 123,
  email: "review@example.test",
  nickname: "검증회원",
  role: "USER",
}
const seed = {
  id: 7,
  type: "QUESTION",
  topic: "HEARING_AID",
  title: "[검증 예시] 수정 전 제목",
  content: "첫 줄\n둘째 줄",
  likeCount: 0, liked: false,
  author: { memberId: 123, nickname: "검증회원" },
  createdAt: "2026-10-17T01:00:00Z",
  updatedAt: "2026-10-17T01:00:00Z",
}
const back = "/community/topics/hearing-aid?type=QUESTION",
  edit = "/community/posts/7/edit?from=" + encodeURIComponent(back)
const failure = (code, fields = []) => ({
  code,
  message: "private details",
  fieldErrors: fields,
})
let post, readFailure
function read(url) {
  if (url.pathname === "/api/v1/community/posts/feed") {
    const legacy = read(new URL(url.href.replace("/posts/feed", "/posts")))
    return { ...legacy, json: { items: legacy.json.items.map(p => ({...p, contentPreview: p.content})), hasNext: false, nextCursor: null } }
  }

  // 게시글 회귀 fixture: 댓글은 별도 계약의 빈 목록으로 응답한다.
  if (url.pathname.endsWith("/comments")) return { status: 200, json: { items: [], hasNext: false, nextCursor: null } }
  if (readFailure) return { status: 503, json: failure("SERVER_ERROR") }
  if (url.pathname === "/api/v1/community/posts") {
    const page = Number(url.searchParams.get("page") ?? 0),
      size = 20
    const items =
      post &&
      (!url.searchParams.get("type") ||
        post.type === url.searchParams.get("type")) &&
      (!url.searchParams.get("topic") ||
        post.topic === url.searchParams.get("topic"))
        ? [{ ...post, commentCount: 0 }]
        : []
    return {
      status: 200,
      json: {
        items: page === 0 ? items : [],
        page,
        size,
        totalElements: items.length,
        totalPages: items.length ? 1 : 0,
        hasNext: false,
      },
    }
  }
  return post
    ? { status: 200, json: post }
    : { status: 404, json: failure("POST_NOT_FOUND") }
}
const server = http.createServer((req, res) => {
  const data = read(new URL(req.url, "http://mock"))
  res.writeHead(data.status, { "Content-Type": "application/json" })
  res.end(JSON.stringify(data.json))
})
await new Promise((resolve) =>
  server.listen(
    Number(process.env.COMMUNITY_SSR_MOCK_PORT ?? 8856),
    "127.0.0.1",
    resolve,
  ),
)
const browser = await chromium.launch({ headless: true, channel: "chrome" }),
  checks = [],
  pageErrors = []
async function fixture(identity = member) {
  post = structuredClone(seed)
  readFailure = false
  const ctx = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
      reducedMotion: "reduce",
    }),
    p = await ctx.newPage()
  p.setDefaultTimeout(10000)
  p.on("pageerror", (e) => pageErrors.push(e.message))
  const state = {
    identity,
    put: null,
    del: null,
    csrf: null,
    me: null,
    logout: null,
    calls: [],
    count: { PUT: 0, DELETE: 0, csrf: 0 },
  }
  await p.route(
    (url) =>
      url.origin === new URL(api).origin && url.pathname.startsWith("/api/v1/"),
    async (r) => {
      const req = r.request(),
        url = new URL(req.url()),
        method = req.method()
      state.calls.push({ path: url.pathname, method })
      if (url.pathname.endsWith("/me"))
        return state.me
          ? state.me(r)
          : r.fulfill(
              state.identity
                ? { json: state.identity }
                : { status: 401, json: failure("AUTHENTICATION_REQUIRED") },
            )
      if (url.pathname.endsWith("/csrf")) {
        state.count.csrf++
        return state.csrf
          ? state.csrf(r)
          : r.fulfill({
              json: { headerName: "X-CSRF-TOKEN", token: "test-only" },
            })
      }
      if (url.pathname.endsWith("/logout")) {
        if (state.logout) return state.logout(r)
        state.identity = null
        return r.fulfill({status:204})
      }
      if (url.pathname.endsWith("/login")) {
        state.identity = member
        return r.fulfill({ json: member })
      }
      if (method === "PUT" || method === "DELETE") {
        state.count[method]++
        assert.equal(req.headers()["x-csrf-token"], "test-only")
        const handler = method === "PUT" ? state.put : state.del
        if (handler) return handler(r)
        if (method === "PUT") {
          const body = req.postDataJSON()
          assert.deepEqual(Object.keys(body).sort(), [
            "content",
            "title",
            "topic",
            "type",
          ])
          post = { ...post, ...body, updatedAt: "2026-10-17T02:00:00Z" }
        } else {
          assert.equal(req.postData(), null)
          post = null
        }
        return r.fulfill({ status: 204 })
      }
      return r.fulfill(read(url))
    },
  )
  return { ctx, p, state }
}
async function check(name, fn) {
  try { await fn(); checks.push({name,pass:true}); console.log("PASS",name) }
  catch (e) { checks.push({name,pass:false,error:String(e)}); console.error("FAIL",name,e); process.exitCode=1 }
}
async function formReady(p) {
  await p.getByRole("form", { name: "게시글 수정", exact: true }).waitFor()
}
async function editTitle(p, text = "새로운 제목") {
  await p.getByLabel("제목", { exact: true }).fill(text)
}
async function save(p) {
  await p.getByRole("button", { name: "수정 저장", exact: true }).click()
}
async function shot(p, name) {
  await p.screenshot({ path: path.join(output, name + ".png"), fullPage: true })
}

// Explicit route gates: no fixed network sleeps. Completion is observed at response + DOM.
function gate() {
  let arrived, release, delivered
  const request = new Promise(r => { arrived=r }), response = new Promise(r => { release=r }), done = new Promise(r => { delivered=r })
  return {request, release, done, handle: async route => { arrived(route); await route.fulfill(await response); delivered() }}
}
const draft = "경합 중에도 유지할 초안"
const title = p => p.getByLabel("제목", {exact:true})
const submitButton = p => p.getByRole("button", {name:"수정 저장",exact:true})
async function settled(p) {
  await p.waitForFunction(() => !document.querySelector('form[aria-label="게시글 수정"]')?.textContent.includes("수정 내용을 저장하고 있습니다."))
  assert.equal(await p.getByRole("button", {name:"저장 중…",exact:true}).count(),0)
}
async function drain(p) { await p.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))) }
async function open(p, path) { await p.goto(base+path); await formReady(p); await editTitle(p,draft) }
const paths = [edit, "/community/posts/7/edit?from=%2Fmypage&activity=1"]
try {
  for (const path of paths) {
    const entry = path.includes("activity=1") ? "mypage" : "detail"
    await check(entry+": PUT 401 ends busy, retains draft, blocks anonymous resubmit", async () => {
      const {ctx,p,state}=await fixture()
      try {
        await open(p,path)
        state.put=async r=>{state.identity=null;await r.fulfill({status:401,json:failure("AUTHENTICATION_REQUIRED")})}
        await save(p)
        await p.getByRole("heading",{name:"로그인 후 게시글을 수정할 수 있어요."}).waitFor()
        await settled(p)
        assert.equal(await title(p).inputValue(),draft)
        assert(await submitButton(p).isDisabled()); assert(await title(p).isDisabled())
        await p.getByRole("form",{name:"게시글 수정",exact:true}).evaluate(f=>f.requestSubmit())
        await drain(p); assert.equal(state.count.PUT,1)
        await shot(p,"401-"+entry)
      } finally {await ctx.close()}
    })
    await check(entry+": failed logout invalidates old PUT but releases busy for explicit retry", async () => {
      const {ctx,p,state}=await fixture(),pending=gate()
      try {
        await open(p,path);state.put=pending.handle;await save(p);await pending.request
        state.logout=r=>r.fulfill({status:403,json:failure("CSRF_TOKEN_INVALID")})
        await p.getByRole("button",{name:"로그아웃",exact:true}).click()
        await p.getByRole("button",{name:"로그아웃",exact:true}).waitFor()
        assert(await p.getByRole("link",{name:"마이페이지",exact:true}).isVisible())
        const complete=p.waitForResponse(r=>r.request().method()==="PUT")
        pending.release({status:403,json:failure("ACCESS_DENIED")});await complete;await pending.done
        await settled(p);assert(await title(p).isEnabled());assert(await submitButton(p).isEnabled())
        assert.equal(await title(p).inputValue(),draft);assert.equal(state.count.PUT,1)
        assert.equal(await p.locator(".community-create-form [role=alert]").count(),0)
        const csrfBefore=state.count.csrf;state.put=null
        await shot(p,"logout-failed-"+entry)
        await save(p)
        await p.waitForURL(u=>u.pathname===(entry==="mypage"?"/mypage":"/community/posts/7"))
        assert.equal(state.count.PUT,2);assert.equal(state.count.csrf,csrfBefore+1)
        assert.equal(post.title,draft)
      } finally { pending.release({status:403,json:failure("ACCESS_DENIED")});await ctx.close() }
    })
  }
  await check("old form finally cannot release new form PUT; draft/focus/dirty retained",async()=>{
    const {ctx,p,state}=await fixture(),old=gate(),fresh=gate()
    try {
      await open(p,edit);state.put=old.handle;await save(p);await old.request
      await p.getByRole("button",{name:"취소",exact:true}).click();await p.getByRole("button",{name:"나가기",exact:true}).click()
      await p.waitForURL(u=>u.pathname==="/community/posts/7")
      await p.getByRole("link",{name:"수정",exact:true}).click();await formReady(p)
      await editTitle(p,"새 편집 화면 초안");state.put=fresh.handle;await save(p);await fresh.request
      const cancel=p.getByRole("button",{name:"취소",exact:true});await cancel.focus()
      const response=p.waitForResponse(r=>r.request().method()==="PUT")
      old.release({status:204});await response;await old.done;await drain(p)
      assert(await p.getByRole("button",{name:"저장 중…",exact:true}).isDisabled())
      assert(await title(p).isDisabled());assert.equal(await title(p).inputValue(),"새 편집 화면 초안")
      assert(await cancel.evaluate(e=>e===document.activeElement));assert.equal(new URL(p.url()).pathname,"/community/posts/7/edit")
      assert(await p.evaluate(()=>{const e=new Event("beforeunload",{cancelable:true});window.dispatchEvent(e);return e.defaultPrevented}))
      fresh.release({status:403,json:failure("ACCESS_DENIED")});await fresh.done;await settled(p)
      assert(await submitButton(p).isEnabled());assert.equal(state.count.PUT,2)
    } finally {old.release({status:204});fresh.release({status:403,json:failure("ACCESS_DENIED")});await ctx.close()}
  })
  for (const status of [204,401]) await check("logout success ignores late PUT "+status+" and settles original form",async()=>{
    const {ctx,p,state}=await fixture(),pending=gate()
    try {
      await open(p,edit);state.put=pending.handle;await save(p);await pending.request
      await p.getByRole("button",{name:"로그아웃",exact:true}).click()
      await p.getByRole("heading",{name:"로그인 후 게시글을 수정할 수 있어요."}).waitFor()
      const response=p.waitForResponse(r=>r.request().method()==="PUT")
      pending.release(status===204?{status}:{status,json:failure("AUTHENTICATION_REQUIRED")});await response;await pending.done;await settled(p)
      assert.equal(new URL(p.url()).pathname,"/community/posts/7/edit");assert.equal(await title(p).inputValue(),draft)
      assert(await submitButton(p).isDisabled());assert.equal(state.count.PUT,1)
      assert.equal(await p.locator(".community-create-form [role=alert]").count(),0)
    }finally{pending.release({status:204});await ctx.close()}
  })
  await check("CSRF gate invalidated by failed logout sends no PUT and permits explicit retry",async()=>{
    const {ctx,p,state}=await fixture(),pending=gate()
    try {
      await open(p,edit);let first=true
      state.csrf=r=>{if(first){first=false;return pending.handle(r)}return r.fulfill({json:{headerName:"X-CSRF-TOKEN",token:"test-only"}})}
      await save(p);await pending.request
      state.logout=r=>r.fulfill({status:403,json:failure("CSRF_TOKEN_INVALID")})
      await p.getByRole("button",{name:"로그아웃",exact:true}).click();await p.getByRole("button",{name:"로그아웃",exact:true}).waitFor()
      pending.release({json:{headerName:"X-CSRF-TOKEN",token:"test-only"}});await pending.done;await settled(p)
      assert.equal(state.count.PUT,0);assert(await submitButton(p).isEnabled());assert.equal(await title(p).inputValue(),draft)
      const n=state.count.csrf;await save(p);await p.waitForURL(u=>u.pathname==="/community/posts/7")
      assert.equal(state.count.PUT,1);assert.equal(state.count.csrf,n+1)
    }finally{pending.release({json:{headerName:"X-CSRF-TOKEN",token:"test-only"}});await ctx.close()}
  })
  assert.deepEqual(pageErrors,[])
} finally {
  await fs.writeFile(path.join(output,"edit-busy-results.json"),JSON.stringify({mocked:true,checks,pageErrors},null,2))
  await browser.close();await new Promise(r=>server.close(r))
}
