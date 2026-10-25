// Production Next + controlled mock API. This does not validate real Spring CORS.
import http from "node:http"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8451",
  api = "http://127.0.0.1:8082"
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
const item = (id) => ({
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
          ? { createdAt: last.createdAt, commentId: last.commentId }
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
  }
}
async function until(fn) {
  for (let n = 0; n < 100; n++) {
    if (fn()) return
    await new Promise((r) => setTimeout(r, 30))
  }
  throw Error("controlled request was not reached")
}
try {
for(const width of [1440,390,320]) await check(`toast header, hit testing and accessibility ${width}`,async()=>{
 const {ctx,p}=await fixture(member,0)
 try {
  await p.setViewportSize({width,height:850})
  await p.locator('#comment-draft').fill('위치 검증 댓글')
  await p.getByRole('button',{name:'댓글 등록',exact:true}).click()
  await p.locator('.comment-success-toast').waitFor()
  await p.evaluate(()=>window.scrollTo(0,0))
  await p.waitForFunction(()=>{const h=document.querySelector('.iyum-header').getBoundingClientRect(),t=document.querySelector('.comment-success-toast').getBoundingClientRect();return t.top>=h.bottom+11})
  const layout=await p.locator('.comment-success-toast').evaluate(el=>{const r=el.getBoundingClientRect();return {left:r.left,right:r.right,top:r.top,width:innerWidth,pointer:getComputedStyle(el).pointerEvents}})
  assert(layout.left>=16 && layout.right<=layout.width-16);assert.equal(layout.pointer,'none')
  const tree=await p.locator('.comment-toast-announcer').ariaSnapshot()
  assert(tree.includes('status'));assert.equal(tree.split('댓글을 등록했습니다.').length-1,1)
  await fs.writeFile(output+`/toast-accessibility-${width}.txt`,tree)
  await fs.writeFile(output+`/toast-position-${width}.json`,JSON.stringify(layout,null,2))
  await p.screenshot({path:output+`/toast-below-header-${width}.png`})
 }finally{await ctx.close()}
})
}finally{await fs.writeFile(output+'/position-results.json',JSON.stringify(results,null,2));await browser.close();await new Promise(r=>server.close(r))}
