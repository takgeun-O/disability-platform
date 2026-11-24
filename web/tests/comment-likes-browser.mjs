// Production Next + controlled mock API. This does not validate real Spring CORS.
import http from "node:http"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8451",
  api = process.env.COMMUNITY_BROWSER_API ?? "http://127.0.0.1:8082"
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
 await check("삭제 직후 접었다 펼쳐도 남은 내 답글을 수정할 수 있다", async () => {
  const { p, state } = await fixture(member, 1)
  Object.assign(comments[0], { deleted: false, replyCount: 2 })
  const held = gate(), resumed = gate(), calls = []
  let replies = [item(101), item(102)]
  state.get = async (r, url, response) => {
    if (!url.pathname.endsWith("/replies")) return r.fulfill(response)
    calls.push("GET")
    if (calls.length === 3) await held.hold()
    if (calls.length === 4) await resumed.hold()
    return r.fulfill({ json: { items: replies, hasNext: false, nextCursor: null } })
  }
  state.write = r => {
    calls.push(r.request().method())
    assert.ok(r.request().url().endsWith("/comments/101"))
    replies = [item(102)]
    comments[0].replyCount = 1
    return r.fulfill({ status: 204 })
  }
  await p.locator("#comments-refresh").click()
  await p.getByRole("button", { name: "답글 2개 보기" }).click()
  await p.locator("#reply-more-101").click()
  await p.locator('[data-comment-item="101"]').getByRole("button", { name: "삭제", exact: true }).click()
  await p.getByRole("button", { name: "답글 삭제하기" }).click()
  const toast = p.locator(".comment-success-toast").filter({ hasText: "답글을 삭제했습니다." })
  await toast.waitFor()
  const firstToast = await toast.elementHandle()
  await held.reached
  await p.locator("#reply-toggle-1").click()
  await p.getByRole("button", { name: "답글 2개 보기" }).click()
  await p.locator("#reply-more-102").click()
  await p.locator('[data-comment-item="102"]').getByRole("button", { name: "수정", exact: true }).click()
  await p.locator("#reply-edit-102").fill("계속 작성하는 초안")
  await resumed.reached
  resumed.release()
  await p.getByRole("button", { name: "답글 1개 접기" }).waitFor()
  held.release()
  await p.waitForLoadState("networkidle")
  assert.equal(await p.locator("#reply-edit-102").inputValue(), "계속 작성하는 초안")
  assert.equal(await p.locator('[data-comment-item="101"]').count(), 0)
  assert.equal(await toast.evaluate((el, first) => el === first, firstToast), true)
  assert.deepEqual(calls, ["GET", "DELETE", "GET", "GET"])
  console.log("접기·펼치기", calls)
 })

 for (const publicParent of [false, true]) await check(publicParent
   ? "공개 원댓글의 답글 수가 0이 되면 지연된 GET보다 먼저 초안을 복사하고 닫을 수 있다"
   : "답글 재조회보다 원댓글이 먼저 사라져도 제출한 수정 초안을 복사할 수 있다", async () => {
  const { ctx, p, state } = await fixture(member, publicParent ? 2 : 1)
  Object.assign(comments[0], publicParent ? { deleted: false, replyCount: 1 }
    : { deleted: true, author: null, content: "삭제된 댓글입니다.", replyCount: 1 })
  const held = gate(), pendingReads = [], calls = [], draft = "공개 원문 대신 보존한 내 수정 초안"
  const reply = { ...item(101), content: "더 이상 공개되지 않는 원문", author: { ...member, nickname: "숨겨진 작성자" } }
  let replacement = null
  state.get = async (r, url, response) => {
    if (!url.pathname.endsWith("/replies")) {
      if (url.pathname.endsWith("/comments") && calls.includes("PUT")) calls.push("원댓글 GET")
      return r.fulfill(response)
    }
    calls.push("GET")
    if (replacement) return r.fulfill({ json: { items: [replacement], hasNext: false, nextCursor: null } })
    if (calls.includes("PUT")) {
      const stale = calls.filter(method => method === "GET").length === 2
      const response = held.hold().then(() => r.fulfill({ json: {
        items: publicParent && stale ? [reply] : [], hasNext: false, nextCursor: null,
      } }))
      pendingReads.push(response)
      return response
    }
    return r.fulfill({ json: { items: [reply], hasNext: false, nextCursor: null } })
  }
  state.write = r => {
    assert.ok(r.request().url().endsWith("/comments/101"))
    calls.push(r.request().method())
    assert.equal(r.request().postDataJSON().content, draft)
    if (publicParent) comments[0].replyCount = 0
    else comments = []
    return r.abort("failed")
  }
  await p.locator("#comments-refresh").click()
  const other = p.locator("#comment-2")
  if (publicParent) {
    await other.getByRole("button", { name: "답글 달기" }).click()
    await other.getByRole("textbox").fill("다른 원댓글에 작성 중인 답글")
  }
  await p.getByRole("button", { name: "답글 1개 보기" }).click()
  await p.locator("#reply-more-101").click()
  await p.locator('[data-comment-item="101"]').getByRole("button", { name: "수정", exact: true }).click()
  await p.locator("#reply-edit-101").fill(draft)
  await p.getByRole("button", { name: "저장", exact: true }).press("Enter")
  await held.reached
  await p.locator("#comments-refresh").click()
  const parent = p.locator("#comment-1")
  if (publicParent) {
    await parent.getByRole("button", { name: "답글 달기" }).waitFor()
    await parent.locator("#reply-toggle-1").waitFor({ state: "detached" })
  } else await parent.waitFor({ state: "detached" })
  const saved = p.getByRole("textbox", { name: "입력한 수정 초안" })
  await saved.waitFor()
  if (publicParent) {
    assert.equal(await parent.getByRole("textbox", { name: "입력한 수정 초안" }).count(), 1)
    assert.equal(await saved.count(), 1)
  } else {
    held.release()
    await p.waitForLoadState("networkidle")
  }
  assert.equal(await saved.inputValue(), draft)
  assert.equal(await saved.getAttribute("readonly"), "")
  await ctx.grantPermissions(["clipboard-read", "clipboard-write"])
  await saved.focus()
  await saved.press("ControlOrMeta+a")
  await saved.press("ControlOrMeta+c")
  assert.equal(await p.evaluate(() => navigator.clipboard.readText()), draft)
  assert.equal(await p.getByText(reply.content, { exact: true }).count(), 0)
  assert.equal(await p.getByText(reply.author.nickname, { exact: true }).count(), 0)
  await p.screenshot({ path: output + (publicParent ? "/public-parent-draft.png" : "/orphaned-edit.png") })
  await saved.press("Tab")
  await p.keyboard.press("Enter")
  assert.equal(await saved.count(), 0)
  const focus = publicParent ? "reply-write-1" : "comments-title"
  assert.equal(await p.evaluate(() => document.activeElement.id), focus)
  if (publicParent) {
    replacement = { ...item(102), content: "새로 작성한 답글" }
    comments[0].replyCount = 1
    await p.locator("#comments-refresh").click()
    await parent.getByRole("button", { name: "답글 1개 보기" }).click()
    const menu = parent.locator("#reply-more-102")
    await menu.waitFor()
    assert.equal(await parent.getByRole("button", { name: "수정 취소" }).count(), 0, "101의 편집 상태가 남음")
    assert.equal(await parent.getByRole("button", { name: "답글 다시 확인" }).count(), 0)
    assert.equal(await parent.getByText(draft, { exact: true }).count(), 0)
    assert.ok(await menu.isEnabled())
    await menu.press("Enter")
    await parent.getByRole("button", { name: "수정", exact: true }).press("Enter")
    const editor = parent.locator("#reply-edit-102")
    assert.equal(await editor.inputValue(), replacement.content)
    const nextDraft = "102에 새로 입력한 편집 내용"
    await editor.fill(nextDraft)
    const top = await p.evaluate(() => scrollY)
    held.release()
    await Promise.all(pendingReads)
    await p.waitForLoadState("networkidle")
    assert.equal(await saved.count(), 0)
    assert.equal(await parent.locator('[data-comment-item="101"]').count(), 0)
    assert.equal(await p.getByText(reply.content, { exact: true }).count(), 0)
    assert.equal(await p.getByText(reply.author.nickname, { exact: true }).count(), 0)
    assert.equal(await p.evaluate(() => document.activeElement.id), "reply-edit-102")
    assert.equal(await editor.inputValue(), nextDraft)
    assert.equal(await other.getByRole("textbox").inputValue(), "다른 원댓글에 작성 중인 답글")
    assert.equal(await parent.locator("#reply-toggle-1").getAttribute("aria-expanded"), "true")
    assert.equal(await p.evaluate(() => scrollY), top)
    assert.equal(calls.filter(method => method === "DELETE").length, 0)
    await p.screenshot({ path: output + "/reply-102-edit.png" })
  }
  assert.equal(calls.filter(method => method === "PUT").length, 1)
  console.log(publicParent ? "공개 원댓글 유지" : "원댓글 제거", calls)
 })
 for (const method of ["DELETE", "PUT"]) await check(`유실된 ${method} 결과를 다음 커서에서 확인하고 실패한 커서만 재시도한다`, async () => {
  const { ctx, p, state } = await fixture(member, 2)
  await p.setViewportSize({ width: method === "PUT" ? 390 : 1440, height: 900 })
  Object.assign(comments[0], { deleted: false, replyCount: 30 })
  let items = Array.from({length:30}, (_, i) => item(101 + i))
  const targetId = 101, draft = "복사해서 보관할 수정 초안"
  items[0] = { ...items[0], author: { ...member, nickname: "사라진 답글 작성자" }, content: "사라진 원래 답글 본문" }
  const cursors = [], methods = []
  let failNext = true
  state.write = r => {
    assert.ok(r.request().url().endsWith("/comments/101"))
    methods.push(r.request().method())
    items = items.filter(item => item.commentId !== targetId)
    comments[0].replyCount = 29
    return r.abort("failed")
  }
  state.get = (r, u, result) => {
    if (!u.pathname.endsWith("/1/replies")) return r.fulfill(result)
    const after = Number(u.searchParams.get("afterId"))
    cursors.push(after)
    if (after && failNext) { failNext = false; return r.fulfill({ status: 503, json: failure("SERVER_ERROR") }) }
    const rest = items.filter(item => item.commentId > after)
    const batch = rest.slice(0,20), last = batch.at(-1), more = rest.length > 20
    return r.fulfill({json:{items:batch, hasNext:more, nextCursor:more
      ? {sort:"OLDEST", likeCount:null, commentId:last.commentId, createdAt:last.createdAt} : null}})
  }
  await p.locator("#comments-refresh").click()
  const parent = p.locator("#comment-1"), other = p.locator("#comment-2")
  await other.getByRole("button",{name:"답글 달기"}).click()
  await other.getByRole("textbox").fill("다른 원댓글의 답글 초안")
  await parent.getByRole("button",{name:"답글 30개 보기"}).click()
  const target = parent.locator('[data-comment-item="101"]')
  await target.getByRole("button",{name:"답글 더보기"}).click()
  await target.getByRole("button",{name:method === "PUT" ? "수정" : "삭제",exact:true}).click()
  if (method === "PUT") {
    await target.getByRole("textbox").fill(draft)
    await target.getByRole("button",{name:"저장"}).press("Enter")
  } else await p.getByRole("dialog").getByRole("button",{name:"답글 삭제하기"}).press("Enter")
  await target.waitFor({state:"detached"})
  const controls = method === "DELETE" ? p.getByRole("dialog") : parent
  const retry = controls.getByRole("button",{name:"답글 다시 확인",exact:true})
  await retry.evaluate(el => {el.click();el.click()})
  await controls.getByText("답글 목록을 다시 불러와 주세요.",{exact:true}).first().waitFor()
  assert.equal(await parent.locator(".comment-reply-list > li").count(),20)
  await retry.press("Enter")
  await p.locator(".comment-success-toast").getByText("답글이 더 이상 표시되지 않습니다.").waitFor()
  assert.deepEqual(cursors,[0,0,121,121])
  assert.deepEqual(methods,[method])
  assert.equal(await parent.locator(".comment-reply-list > li").count(),29)
  assert.equal(await other.getByRole("textbox").inputValue(),"다른 원댓글의 답글 초안")
  assert.equal(await p.getByText("사라진 원래 답글 본문",{exact:true}).count(),0)
  assert.equal(await p.getByText("사라진 답글 작성자",{exact:true}).count(),0)
  if (method === "PUT") {
    const saved = p.getByRole("textbox",{name:"입력한 수정 초안"})
    await saved.waitFor()
    await ctx.grantPermissions(["clipboard-read","clipboard-write"])
    await saved.focus()
    await saved.press("ControlOrMeta+a")
    await saved.press("ControlOrMeta+c")
    assert.equal(await p.evaluate(() => navigator.clipboard.readText()),draft)
    await saved.evaluate(el => el.scrollIntoView({block:"center"}))
    await p.screenshot({path:output+"/retained-draft.png"})
    await p.getByRole("button",{name:"초안 닫기"}).press("Enter")
    assert.equal(await saved.count(),0)
    assert.equal(await p.evaluate(()=>document.activeElement.dataset.commentItem),"102")
  } else assert.equal(await p.getByRole("dialog").count(),0)
  console.log(`${method} 1회, 답글 GET afterId = ${cursors.join(" → ")}`)
 })
 for (const width of [1440, 390]) await check(`두 번째 페이지의 내 답글만 수정·삭제하고 초안과 펼침을 유지한다 · ${width}px`, async () => {
  const { p, state } = await fixture(member, 2)
  await p.setViewportSize({ width, height: 900 })
  const parent = p.locator("#comment-1"), adjacent = p.locator("#comment-2")
  const myId = 102, other = { ...item(101), author: { memberId: 999, nickname: "다른 회원" } }
  let mine = { ...item(myId), content: "모임 장소는 도서관이에요.", likeCount: 6 }
  Object.assign(comments[0], { deleted: false, replyCount: 2 })
  Object.assign(comments[1], { deleted: false, replyCount: 1 })
  const methods = []
  const lost = width === 390
  let failRead = false
  state.get = (r, u, result) => {
    if (!u.pathname.endsWith("/replies")) return r.fulfill(result)
    if (u.pathname.endsWith("/2/replies")) return r.fulfill({ json: { items: [item(201)], hasNext: false, nextCursor: null } })
    if (failRead) { failRead = false; return r.fulfill({ status: 503, json: failure("SERVER_ERROR") }) }
    const after = u.searchParams.get("afterId")
    return r.fulfill({ json: {
      items: after ? (mine ? [mine] : []) : [other], hasNext: !after && !!mine,
      nextCursor: !after && mine ? { sort: "OLDEST", createdAt: other.createdAt, commentId: 101, likeCount: null } : null,
    } })
  }
  state.write = r => {
    assert.ok(r.request().url().endsWith("/comments/102"))
    const method = r.request().method()
    methods.push(method)
    if (method === "PUT") {
      mine = { ...mine, content: r.request().postDataJSON().content, contentUpdatedAt: "2026-10-18T01:00:00Z" }
      if (lost) { failRead = true; return r.abort("failed") }
    } else {
      assert.equal(method, "DELETE")
      mine = null
      comments[0].replyCount = 1
      if (lost) return r.abort("failed")
    }
    return r.fulfill({ status: 204 })
  }
  await p.locator("#comments-refresh").click()
  await adjacent.getByRole("button", { name: "답글 1개 보기" }).click()
  await parent.getByRole("button", { name: "답글 2개 보기" }).click()
  const otherRow = parent.locator('[data-comment-item="101"]')
  assert.equal(await otherRow.getByRole("button", { name: "답글 더보기" }).count(), 0)
  await parent.getByRole("button", { name: "답글 더 보기" }).click()
  await p.locator("#comment-draft").fill("따로 작성하던 댓글")
  const row = parent.locator('[data-comment-item="102"]'), menu = row.getByRole("button", { name: "답글 더보기" })
  const originalTime = await row.locator("time").getAttribute("datetime")
  await menu.evaluate(el=>el.scrollIntoView({block:"center",behavior:"instant"}))
  const beforeEdit = (await row.boundingBox()).y
  await menu.focus()
  await p.keyboard.press("Enter")
  await row.getByRole("button", { name: "수정", exact: true }).waitFor()
  await row.screenshot({ path: output + "/owner-menu-" + width + ".png" })
  await p.keyboard.press("Enter")
  const editor = row.getByRole("textbox", { name: "답글 수정" })
  assert.equal(await editor.inputValue(), mine.content)
  await row.getByRole("button", { name: "취소" }).press("Enter")
  await p.waitForFunction(() => document.activeElement.id === "reply-more-102")
  await menu.press("Enter")
  await row.getByRole("button", { name: "수정", exact: true }).press("Enter")
  await editor.fill("모임 장소를 도서관 2층으로 변경했어요.")
  await row.screenshot({ path: output + "/owner-edit-" + width + ".png" })
  await row.getByRole("button", { name: "저장" }).press("Enter")
  if (lost) {
    await parent.getByRole("button", { name: "답글 다시 시도" }).waitFor()
    assert.equal(await editor.inputValue(), mine.content)
    await row.getByRole("button", { name: "답글 다시 확인" }).press("Enter")
  }
  await p.locator(".comment-success-toast").getByText(lost ? "수정한 내용을 확인했습니다." : "답글을 수정했습니다.").waitFor()
  await row.getByText("수정됨", { exact: true }).waitFor()
  await p.waitForFunction(() => document.activeElement.id === "reply-more-102")
  assert.equal(await row.locator(".community-comment-body").textContent(), mine.content)
  assert.equal(await row.locator("time").getAttribute("datetime"), originalTime)
  const afterEdit = (await row.boundingBox()).y
  assert.ok(Math.abs(afterEdit - beforeEdit) <= 2)
  await settled(p, myId, false, 6)
  assert.equal(await otherRow.locator(".community-comment-body").textContent(), other.content)
  const beforeDelete = (await otherRow.boundingBox()).y
  await menu.press("Enter")
  await p.keyboard.press("Tab")
  await p.keyboard.press("Enter")
  const dialog = p.getByRole("dialog", { name: "답글을 삭제할까요?" })
  await dialog.waitFor()
  await p.screenshot({ path: output + "/owner-delete-" + width + ".png" })
  await dialog.getByRole("button", { name: "답글 삭제하기" }).press("Enter")
  await parent.getByRole("button", { name: "답글 1개 접기" }).waitFor()
  await row.waitFor({state:"detached"})
  assert.equal(await otherRow.count(), 1)
  const afterDelete = (await otherRow.boundingBox()).y
  assert.ok(Math.abs(afterDelete - beforeDelete) <= 2)
  console.log(`답글 관리 ${width}px: 수정 행 y=${beforeEdit} → ${afterEdit}, 삭제 인접 행 y=${beforeDelete} → ${afterDelete}`)
  await p.waitForFunction(() => document.activeElement.dataset.commentItem === "101")
  assert.equal(await p.locator("#comment-draft").inputValue(), "따로 작성하던 댓글")
  assert.equal(await adjacent.locator('[data-comment-item="201"]').count(), 1)
  assert.deepEqual(methods, ["PUT", "DELETE"])
  await p.locator("#comment-draft").fill("")
  await p.reload()
  await parent.getByRole("button", { name: "답글 1개 보기" }).click()
  await otherRow.waitFor()
  assert.equal(await row.count(), 0)
 })

 await check("수정 204 뒤 목록 오류를 GET만으로 복구하고 성공 토스트는 한 번 표시한다", async () => {
  const { p, state } = await fixture(member, 1)
  Object.assign(comments[0], { deleted:false, replyCount:1 })
  let reply = item(101), fail = false
  const calls = []
  state.get = (r,u,result) => {
    if (!u.pathname.endsWith("/replies")) return r.fulfill(result)
    calls.push("GET")
    if (fail) { fail=false; return r.fulfill({status:503,json:failure("SERVER_ERROR")}) }
    return r.fulfill({json:{items:[reply],hasNext:false,nextCursor:null}})
  }
  state.write = r => {
    calls.push(r.request().method())
    reply = {...reply,content:r.request().postDataJSON().content,contentUpdatedAt:"2026-10-18T01:00:00Z"}
    fail=true
    return r.fulfill({status:204})
  }
  await p.locator("#comments-refresh").click()
  await p.getByRole("button",{name:"답글 1개 보기"}).click()
  const row = p.locator('[data-comment-item="101"]')
  await row.getByRole("button",{name:"답글 더보기"}).click()
  await row.getByRole("button",{name:"수정",exact:true}).click()
  await row.getByRole("textbox").fill("서버가 확인한 답글")
  await row.getByRole("button",{name:"저장"}).press("Enter")
  const toast=p.locator(".comment-success-toast").filter({hasText:"답글을 수정했습니다."})
  await toast.waitFor()
  const original=await toast.elementHandle()
  await p.getByRole("alert").filter({hasText:"답글 목록을 다시 불러와 주세요."}).waitFor()
  await row.getByRole("button",{name:"답글 다시 확인"}).press("Enter")
  await row.getByText("수정됨",{exact:true}).waitFor()
  assert.equal(await toast.evaluate((el,first)=>el===first,original),true)
  assert.deepEqual(calls,["GET","PUT","GET","GET"])
  await toast.waitFor({state:"detached"})
  assert.equal(await p.evaluate(()=>document.activeElement.id),"reply-more-101")
 })
 for (const deleted of [false, true]) await check(`마지막 대댓글 삭제 후 부모 공개 상태와 포커스를 갱신한다 · 삭제된 원댓글 ${deleted}`, async () => {
  const { p, state } = await fixture(member, 1)
  Object.assign(comments[0], deleted ? { deleted, author: null, content: "삭제된 댓글입니다.", replyCount: 1 }
    : { deleted, replyCount: 1 })
  let replies = [item(101)]
  const calls = []
  let failRead = !deleted
  state.get = (r, u, result) => {
    if (!u.pathname.endsWith("/replies")) return r.fulfill(result)
    calls.push("GET")
    if (!replies.length && failRead) { failRead = false; return r.fulfill({status:503,json:failure("SERVER_ERROR")}) }
    return r.fulfill({json:{items:replies,hasNext:false,nextCursor:null}})
  }
  const pending = gate()
  state.write = async r => {
    assert.ok(r.request().url().endsWith("/comments/101"))
    assert.equal(r.request().method(), "DELETE")
    calls.push(r.request().method())
    await pending.hold()
    replies = []
    if (deleted) comments = []
    else comments[0].replyCount = 0
    return r.fulfill({status:204})
  }
  await p.locator("#comments-refresh").click()
  await p.getByRole("button", {name:"답글 1개 보기"}).click()
  const reply = p.locator('[data-comment-item="101"]')
  await reply.getByRole("button", {name:"답글 더보기"}).click()
  await reply.getByRole("button", {name:"삭제", exact:true}).click()
  const confirm = p.getByRole("button", {name:"답글 삭제하기"})
  await confirm.evaluate(el => { el.click(); el.click() })
  await pending.reached
  pending.release()
  const toast = p.locator(".comment-success-toast").filter({hasText:"답글을 삭제했습니다."})
  await toast.waitFor()
  const firstToast = await toast.elementHandle()
  if (!deleted) {
    await p.getByRole("alert").getByText("답글 목록을 다시 불러와 주세요.").waitFor()
    assert.equal(await p.getByText("표시할 답글이 없습니다.", {exact:true}).count(), 0)
    await p.screenshot({path:output+"/success-read-failure.png"})
    await p.getByRole("button",{name:"답글 다시 시도"}).press("Enter")
  }
  await p.locator("#reply-toggle-1").waitFor({state:"detached"})
  const focusId = deleted ? "comments-title" : "reply-write-1"
  await p.waitForFunction(id => document.activeElement.id === id, focusId)
  assert.equal(await toast.evaluate((el, first) => el === first, firstToast), true)
  await toast.waitFor({state:"detached"})
  assert.equal(await p.evaluate(()=>document.activeElement.id),focusId)
  assert.equal(await p.locator("#comment-1").count(), deleted ? 0 : 1)
  assert.deepEqual(calls, deleted ? ["GET","DELETE","GET"] : ["GET","DELETE","GET","GET"])
  console.log("마지막 답글 삭제", calls)
  assert.equal(state.calls.filter(c => c.method === "DELETE").length, 1)
 })

 for (const width of [1440, 390, 320]) await check(`전체 댓글의 작성자와 답글 경계를 구분한다 · ${width}px`, async () => {
  const { p, state } = await fixture(member, 2)
  await p.setViewportSize({ width, height: width === 1440 ? 960 : 844 })
  const author = { memberId: 456, nickname: "동네도서관에서함께책읽는사람" }
  Object.assign(comments[0], { author, content: "모임에 처음 갈 때 어떤 준비를 하셨나요?",
    contentUpdatedAt: comments[0].createdAt, deleted: false, replyCount: 3 })
  Object.assign(comments[1], { author: null, content: "삭제된 댓글입니다.", deleted: true, replyCount: 1 })
  const replies = [
    { ...item(101), author: member, content: "저는 메모장을 챙겼어요." },
    { ...item(102), author: member, content: "물어볼 것도 미리 적었고요." },
    { ...item(103), author, content: "저는 처음에는 작은 모임부터 시작했어요.\n다음 모임 때는 동네에서 같이 읽을 책도 한 권 가져가려고 해요." },
  ]
  state.get = (route, url, result) => url.pathname.endsWith("/replies")
    ? route.fulfill({ json: { items: url.pathname.endsWith("/1/replies") ? replies : [{ ...replies[2], commentId: 201 }], hasNext: false, nextCursor: null } })
    : route.fulfill(result)
  await p.locator("#comments-refresh").click()
  const root = p.locator("#comment-1"), deleted = p.locator("#comment-2")
  const toggle = root.getByRole("button", { name: /^답글 3개 (보기|접기)$/ })
  await toggle.focus()
  const collapsedX = (await toggle.boundingBox()).x
  await p.keyboard.press("Enter")
  await root.locator(".comment-reply-list").waitFor()
  assert.equal(await toggle.evaluate(el => el === document.activeElement), true)
  const bodyX = (await root.locator(":scope > .community-comment-body").boundingBox()).x
  const parentX = (await root.locator(":scope > .community-comment-meta").boundingBox()).x
  const lineX = (await root.locator(".comment-reply-list").boundingBox()).x
  assert.equal((await toggle.boundingBox()).x, collapsedX)
  assert.equal(lineX, collapsedX)
  if (width === 1440) assert.equal(lineX, bodyX)
  else assert.ok(lineX - parentX >= 12 && lineX - parentX <= 16)
  console.log(`Layout ${width}px: parent=${parentX}, body=${bodyX}, toggle=${collapsedX}, line=${lineX}`)
  await p.keyboard.press("Enter")
  assert.equal(await toggle.getAttribute("aria-expanded"), "false")
  assert.equal(await toggle.evaluate(el => el === document.activeElement), true)
  assert.equal((await toggle.boundingBox()).x, collapsedX)
  await p.keyboard.press("Enter")
  await root.locator('[data-comment-item="103"]').waitFor()
  await deleted.getByRole("button", { name: "답글 1개 보기" }).click()
  await deleted.locator(".comment-reply-list strong").waitFor()
  await root.locator(":scope > .community-comment-meta").getByText("수정됨", { exact: true }).waitFor()
  const deletedMeta = deleted.locator(":scope > .community-comment-meta")
  assert.equal(await deletedMeta.locator("strong, .feed-avatar").count(), 0)
  assert.equal(await deleted.locator(".comment-reply-list .feed-avatar").count(), 1)
  assert.equal(await deleted.locator(":scope > .community-comment-body").evaluate(el => getComputedStyle(el).marginInlineStart), "0px")
  for (const [index, row] of [root, ...await root.locator(".comment-reply-list > li").all()].entries()) {
    const meta = row.locator(":scope > .community-comment-meta"), avatar = meta.locator(".feed-avatar")
    assert.equal(await avatar.getAttribute("aria-hidden"), "true")
    const size = await avatar.boundingBox()
    assert.equal(size.width, index ? 26 : 32)
    assert.equal(size.height, size.width)
    const name = await meta.locator("strong").boundingBox()
    const body = await row.locator(":scope > .community-comment-body").boundingBox()
    const like = await row.locator(":scope > .community-comment-toolbar .post-like-button").boundingBox()
    assert.ok(Math.abs(name.x - body.x) <= 1 && Math.abs(name.x - like.x) <= 1)
    const header = await meta.boundingBox()
    assert.ok(body.y - header.y - header.height >= 5 && body.y - header.y - header.height <= 9)
    if (index) assert.equal(await row.evaluate(el => getComputedStyle(el).borderBlockStartWidth), index === 1 ? "0px" : "1px")
  }
  const menuButton = root.getByRole("button", { name: "댓글 더보기" })
  const menuBox = await menuButton.boundingBox(), byline = await root.locator(":scope > .community-comment-meta .comment-byline").boundingBox()
  assert.ok(byline.x + byline.width <= menuBox.x && menuBox.x + menuBox.width <= width)
  await menuButton.click()
  const menu = await root.getByRole("menu").boundingBox()
  assert.ok(menu.x >= 0 && menu.x + menu.width <= width)
  await p.keyboard.press("Escape")
  assert.ok(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
  await p.locator(".comment-success-toast").waitFor({ state: "hidden" })
  await root.evaluate(row => row.scrollIntoView({ block: "start", behavior: "instant" }))
  await p.screenshot({ path: `${output}/viewport-${width}.png` })
  await root.screenshot({ path: `${output}/thread-${width}.png` })
  await deleted.screenshot({ path: `${output}/deleted-thread-${width}.png` })
 })

 for (const lost of [false, true]) await check(`대댓글 좋아요는 해당 ID만 갱신하고 읽은 페이지와 초안을 유지한다 · ${lost ? "응답 유실" : "정상 응답"}`, async () => {
  const { p, state } = await fixture(member, 1)
  const a = p.locator("#comment-1"), b = 102, c = 101
  Object.assign(comments[0], { deleted: false, replyCount: 2, likeCount: 10 })
  const cursors = [], methods = []
  const pending = gate()
  let liked = false
  state.get = (route, url, result) => {
    if (!url.pathname.endsWith("/1/replies")) return route.fulfill(result)
    const after = url.searchParams.get("afterId")
    cursors.push(after)
    const reply = after ? { ...item(b), liked, likeCount: liked ? 10 : 9 } : { ...item(c), likeCount: 2 }
    return route.fulfill({ json: { items: [reply], hasNext: !after, nextCursor: after ? null :
      { createdAt: reply.createdAt, commentId: c, sort: "OLDEST", likeCount: null } } })
  }
  state.write = async route => {
    assert.ok(route.request().url().endsWith(`/comments/${b}/like`))
    const method = route.request().method()
    methods.push(method)
    liked = method === "PUT"
    if (liked && !lost) await pending.hold()
    if (lost) return route.abort("failed")
    return route.fulfill({ json: { postId: 7, commentId: b, liked, likeCount: liked ? 10 : 9 } })
  }
  await p.locator("#comments-refresh").click()
  await a.getByRole("button", { name: "답글 2개 보기" }).click()
  await a.getByRole("button", { name: "답글 더 보기" }).click()
  await settled(p, b, false, 9)
  await a.getByRole("button", { name: "답글 달기" }).click()
  await a.getByRole("textbox").fill("답글 작성 중")
  await btn(p, b).focus()
  for (const selected of [true, false]) {
    await p.keyboard.press("Enter")
    if (selected && !lost) {
      try {
        await pending.reached
        await p.keyboard.press("Enter")
        assert.deepEqual(methods, ["PUT"])
      } finally { pending.release() }
    }
    if (lost) {
      await reaction(p, b).getByRole("button", { name: "댓글 좋아요 상태 다시 확인" }).waitFor()
      await p.keyboard.press("Enter")
    }
    await settled(p, b, selected, selected ? 10 : 9)
    await settled(p, 1, false, 10)
    await settled(p, c, false, 2)
    assert.equal(await btn(p, b).evaluate(el => el === document.activeElement), true)
    assert.equal(await a.getByRole("textbox").inputValue(), "답글 작성 중")
    assert.equal(await a.getByRole("button", { name: "답글 2개 접기" }).getAttribute("aria-expanded"), "true")
    assert.deepEqual(await a.locator(".comment-reply-list > li").evaluateAll(rows => rows.map(el => Number(el.dataset.commentItem))), [c, b])
    assert.deepEqual(await p.locator("[data-best-comment]").evaluateAll(rows => rows.map(el => Number(el.dataset.bestComment))), [1])
  }
  assert.deepEqual(methods, ["PUT", "DELETE"])
  assert.deepEqual(cursors, lost ? [null, "101", null, "101", null, "101"] : [null, "101"])
 })

 await check("대댓글 좋아요 세션 만료 후 비회원 공개 수와 로그인 안내를 표시한다", async () => {
  const { p, state } = await fixture(member, 1)
  Object.assign(comments[0], { deleted: false, replyCount: 1 })
  state.get = (route, url, result) => url.pathname.endsWith("/1/replies")
    ? route.fulfill({ json: { items: [{ ...item(101), likeCount: 9 }], hasNext: false, nextCursor: null } })
    : route.fulfill(result)
  state.write = route => {
    state.identity = null
    return route.fulfill({ status: 401, json: failure("AUTHENTICATION_REQUIRED") })
  }
  await p.locator("#comments-refresh").click()
  const open = p.getByRole("button", { name: "답글 1개 보기" })
  await open.click()
  await settled(p, 101, false, 9)
  await btn(p, 101).click()
  await p.getByRole("link", { name: "로그인", exact: true }).first().waitFor()
  await open.click()
  await settled(p, 101, false, 9)
  await btn(p, 101).click()
  await p.getByRole("dialog").getByRole("button", { name: "로그인하기" }).waitFor()
  assert.deepEqual(writes(state).map(call => call.method), ["PUT"])
 })

 for (const existing of [0, 20]) await check(`공개 원댓글의 답글 등록과 서버 커서 합치기 · 기존 ${existing}개`, async () => {
  const { p, state } = await fixture(member, 2)
  const replies = Array.from({ length: existing }, (_, i) => item(101 + i))
  Object.assign(comments[0], { deleted: false, replyCount: existing })
  const row = p.locator("#comment-1"), other = p.locator("#comment-2")
  const content = "새 답글 원문"
  let failFirstRead = existing > 0, failRootRead = false
  const cursors = []
  state.get = (route, url, result) => {
    if (url.pathname.endsWith("/comments") && failRootRead) {
      failRootRead = false
      return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
    }
    if (!url.pathname.endsWith("/1/replies")) return route.fulfill(result)
    const after = url.searchParams.get("afterId")
    cursors.push(after)
    if (failFirstRead) {
      failFirstRead = false
      return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
    }
    const page = replies.filter(c => c.commentId > Number(after)).slice(0, 20)
    const last = page.at(-1), more = last.commentId !== replies.at(-1).commentId
    return route.fulfill({ json: { items: page, hasNext: more,
      nextCursor: more ? { commentId: last.commentId, createdAt: last.createdAt, sort: "OLDEST", likeCount: null } : null } })
  }
  state.write = route => {
    assert.equal(route.request().method(), "POST")
    assert.ok(route.request().url().endsWith("/comments/1/replies"))
    assert.deepEqual(route.request().postDataJSON(), { content })
    replies.push({ ...item(201), content })
    comments[0].replyCount = replies.length
    failRootRead = existing > 0
    return route.fulfill({ status: 201, json: { commentId: 201 } })
  }
  await p.locator("#comment-draft").fill("독립된 원댓글 초안")
  await row.getByRole("button", { name: "답글 달기" }).click()
  const field = row.getByLabel("검증회원님에게 답글 남기기")
  await p.waitForFunction(() => document.activeElement?.id === "reply-input-1")
  await field.fill(content)
  await other.getByRole("button", { name: "답글 달기" }).click()
  await other.getByRole("textbox").fill("다른 부모의 초안")
  await other.getByRole("button", { name: "취소" }).click()
  await p.waitForFunction(() => document.activeElement?.id === "reply-write-2")
  await p.locator("#comments-refresh").click()
  assert.equal(await field.inputValue(), content)
  await row.getByRole("button", { name: "답글 등록" }).evaluate(b => { b.click(); b.click() })
  if (existing) {
    const receipt = row.locator('[data-created-reply-id="201"]')
    await receipt.getByText(content).waitFor()
    await row.getByRole("button", { name: "답글 다시 시도" }).click()
    await row.getByRole("button", { name: "답글 더 보기" }).waitFor()
    assert.deepEqual(cursors, [null, null])
    assert.equal(await receipt.getByText(content).count(), 1)
    await row.getByRole("button", { name: "답글 더 보기" }).click()
  }
  await row.locator('[data-comment-item="201"]').getByText(content).waitFor()
  assert.deepEqual(cursors, existing ? [null, null, "120"] : [null])
  if (existing) {
    await p.getByRole("button", { name: "댓글 조회 다시 시도" }).click()
    await row.locator('[data-comment-item="201"]').getByText(content).waitFor()
  }
  assert.equal(await row.getByText(content, { exact: true }).count(), 1)
  await row.getByRole("button", { name: `답글 ${existing + 1}개 접기`, exact: true }).waitFor()
  assert.equal(await field.count(), 0)
  assert.equal(await p.locator("#comment-draft").inputValue(), "독립된 원댓글 초안")
  assert.equal(state.calls.filter(c => c.method === "POST").length, 1)
 })

 for (const mode of ["펼침", "접힘", "모두 숨김", "이전 첫 페이지 지연"]) await check(`B 등록 후 A의 공개 답글 목록을 갱신한다 · ${mode}`, async () => {
  const { p, state } = await fixture(member, 2)
  const a = p.locator("#comment-1"), b = p.locator("#comment-2")
  Object.assign(comments[0], { deleted: false, replyCount: 2 })
  Object.assign(comments[1], { deleted: false, replyCount: 0 })
  const delayed = mode === "이전 첫 페이지 지연" ? gate() : null
  let visible = [101, 102], posts = 0, reads = 0
  state.get = async (route, url, result) => {
    if (url.pathname.endsWith("/1/replies")) {
      reads++
      assert.equal(url.searchParams.has("afterId"), false)
      if (delayed && reads === 1) {
        await delayed.hold()
        return route.fulfill({ json: { items: [item(101)], hasNext: true,
          nextCursor: { commentId: 101, createdAt: item(101).createdAt, sort: "OLDEST", likeCount: null } } })
      }
      if (mode === "펼침" && reads === 2) return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
      return route.fulfill({ json: { items: visible.map(item), hasNext: false, nextCursor: null } })
    }
    if (url.pathname.endsWith("/2/replies"))
      return route.fulfill({ json: { items: [item(201)], hasNext: false, nextCursor: null } })
    return route.fulfill(result)
  }
  state.write = route => {
    assert.equal(route.request().method(), "POST")
    assert.ok(route.request().url().endsWith("/2/replies"))
    posts++
    comments[1].replyCount = 1
    return route.fulfill({ status: 201, json: { commentId: 201 } })
  }
  await p.locator("#comments-refresh").click()
  await a.getByRole("button", { name: "답글 2개 보기" }).click()
  if (delayed) await delayed.reached
  else await a.locator('[data-comment-item="102"]').waitFor()
  if (mode === "접힘") await a.getByRole("button", { name: "답글 2개 접기" }).click()
  visible = mode === "모두 숨김" ? [] : delayed ? [102] : [101]
  comments[0].replyCount = visible.length
  await b.getByRole("button", { name: "답글 달기" }).click()
  await b.getByRole("textbox").fill("두 번째 원댓글에 남기는 답글")
  await b.getByRole("button", { name: "답글 등록" }).click()
  await b.getByRole("button", { name: "답글 1개 접기" }).waitFor()
  const ids = () => a.locator(".comment-reply-list > li").evaluateAll(rows => rows.map(row => Number(row.dataset.commentItem)))
  delayed?.release()
  const hiddenId = delayed ? 101 : 102
  assert.ok(!(await ids()).includes(hiddenId), `숨긴 답글 ${hiddenId}가 DOM에 남음`)
  if (mode === "모두 숨김") {
    assert.deepEqual(await ids(), [])
    assert.equal(await a.locator("[aria-expanded][id^=reply-toggle]").count(), 0)
    await a.getByRole("button", { name: "답글 달기" }).waitFor()
    assert.equal(reads, 1)
  } else {
    if (mode === "접힘") {
      assert.equal(reads, 1)
      await a.getByRole("button", { name: "답글 1개 보기" }).click()
    } else if (!delayed) {
      await a.getByRole("button", { name: "답글 다시 시도" }).click()
    }
    await a.locator(`[data-comment-item="${visible[0]}"]`).waitFor()
    await a.getByRole("button", { name: "답글 1개 접기" }).waitFor()
    assert.deepEqual(await ids(), visible)
    assert.equal(reads, mode === "펼침" ? 3 : 2)
  }
  assert.equal(posts, 1)
 })

 for (const collapsed of [false, true]) await check(`부분 조회한 답글의 공개 개수 감소 · ${collapsed ? "접힘" : "펼침"}`, async () => {
  const { p, state } = await fixture(member, 3)
  const a = p.locator("#comment-1"), b = p.locator("#comment-2"), c = p.locator("#comment-3")
  const original = Array.from({ length: 21 }, (_, i) => 101 + i)
  const remaining = original.slice(2)
  let visible = original, reads = 0, otherReads = 0, posts = 0
  const pending = gate()
  Object.assign(comments[0], { deleted: false, replyCount: original.length })
  Object.assign(comments[1], { deleted: false, replyCount: 0 })
  Object.assign(comments[2], { deleted: false, replyCount: 1 })
  state.get = async (route, url, result) => {
    if (url.pathname.endsWith("/1/replies")) {
      assert.equal(url.searchParams.has("afterId"), false)
      assert.equal(url.searchParams.has("afterCreatedAt"), false)
      if (++reads === 2 && !collapsed) {
        await pending.hold()
        return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
      }
      const items = visible.slice(0, 20).map(item)
      const hasNext = items.length < visible.length
      return route.fulfill({ json: { items, hasNext, nextCursor: hasNext
        ? { commentId: 120, createdAt: item(120).createdAt, sort: "OLDEST", likeCount: null } : null } })
    }
    if (url.pathname.endsWith("/3/replies")) {
      otherReads++
      return route.fulfill({ json: { items: [item(301)], hasNext: false, nextCursor: null } })
    }
    if (url.pathname.endsWith("/2/replies"))
      return route.fulfill({ json: { items: [item(201)], hasNext: false, nextCursor: null } })
    return route.fulfill(result)
  }
  state.write = route => {
    assert.equal(route.request().method(), "POST")
    assert.ok(route.request().url().endsWith("/2/replies"))
    posts++
    comments[1].replyCount = 1
    return route.fulfill({ status: 201, json: { commentId: 201 } })
  }
  const ids = () => a.locator(".comment-reply-list > li").evaluateAll(rows => rows.map(el => Number(el.dataset.commentItem)))
  await p.locator("#comments-refresh").click()
  await c.getByRole("button", { name: "답글 1개 보기" }).click()
  await c.locator('[data-comment-item="301"]').waitFor()
  await c.getByRole("button", { name: "답글 달기" }).click()
  await c.getByRole("textbox").fill("그대로 둘 초안")
  await a.getByRole("button", { name: "답글 21개 보기" }).click()
  await a.getByRole("button", { name: "답글 더 보기" }).waitFor()
  assert.deepEqual(await ids(), original.slice(0, 20))
  if (collapsed) await a.getByRole("button", { name: "답글 21개 접기" }).click()

  visible = remaining
  comments[0].replyCount = remaining.length
  await b.getByRole("button", { name: "답글 달기" }).click()
  await b.getByRole("textbox").fill("B에 등록할 답글")
  await b.getByRole("button", { name: "답글 등록" }).click()
  const toggle = a.getByRole("button", { name: `답글 19개 ${collapsed ? "보기" : "접기"}` })
  try {
    await toggle.waitFor()
    assert.deepEqual(await ids(), [], "19개 응답 뒤 첫 페이지 캐시가 남음")
    assert.equal(await p.evaluate(() => document.activeElement.id), "reply-write-2")
    if (collapsed) {
      assert.equal(reads, 1)
      await toggle.click()
    } else {
      await pending.reached
      pending.release()
      const retry = a.getByRole("button", { name: "답글 다시 시도" })
      await retry.waitFor()
      assert.deepEqual(await ids(), [])
      await retry.click()
    }
  } finally { pending.release() }
  await a.locator('[data-comment-item="121"]').waitFor()
  assert.deepEqual(await ids(), remaining)
  assert.equal(await a.getByRole("button", { name: "답글 더 보기" }).count(), 0)
  assert.equal(await a.getByRole("button", { name: "답글 19개 접기" }).getAttribute("aria-expanded"), "true")
  assert.equal(await c.getByRole("textbox").inputValue(), "그대로 둘 초안")
  assert.deepEqual({ reads, otherReads, posts }, { reads: collapsed ? 2 : 3, otherReads: 1, posts: 1 })
 })

 await check("첫 답글 등록 후 원댓글 GET을 기다리지 않고 작성 버튼에 포커스를 복귀한다", async () => {
  const { p, state } = await fixture(member, 2)
  const row = p.locator("#comment-1"), content = "키보드로 쓴 첫 답글"
  const pending = gate()
  let posts = 0
  Object.assign(comments[0], { deleted: false, replyCount: 0 })
  state.write = route => {
    assert.equal(route.request().method(), "POST")
    assert.ok(route.request().url().endsWith("/1/replies"))
    posts++
    comments[0].replyCount = 1
    return route.fulfill({ status: 201, json: { commentId: 201 } })
  }
  state.get = async (route, url, result) => {
    if (posts && url.pathname.endsWith("/comments")) await pending.hold()
    if (url.pathname.endsWith("/1/replies"))
      return route.fulfill({ json: { items: [{ ...item(201), content }], hasNext: false, nextCursor: null } })
    return route.fulfill(result)
  }
  await row.getByRole("button", { name: "답글 달기" }).focus()
  await p.keyboard.press("Enter")
  await p.keyboard.insertText(content)
  await p.keyboard.press("Tab")
  await p.keyboard.press("Tab")
  assert.equal(await row.getByRole("button", { name: "답글 등록" }).evaluate(el => el === document.activeElement), true)
  await p.keyboard.press("Enter")
  try {
    await pending.reached
    await row.locator('[data-comment-item="201"]').waitFor()
    assert.equal(await p.evaluate(() => document.activeElement.id), "reply-write-1")
    await p.keyboard.press("Tab")
    const next = await p.evaluateHandle(() => document.activeElement)
    assert.equal(await next.evaluate(el => el.id !== "reply-write-1" && el !== document.body), true)
    pending.release()
    await row.getByRole("button", { name: "답글 1개 접기" }).waitFor()
    assert.equal(await next.evaluate(el => el === document.activeElement), true)
  } finally { pending.release() }
  assert.equal(await row.getByText(content, { exact: true }).count(), 1)
  assert.equal(posts, 1)
 })

 for (const firstFailure of ["없음", "원댓글", "답글"]) await check(
  `첫 답글 저장 후 응답 유실은 GET으로 확인하고 초안을 보존한다 · 조회 실패 ${firstFailure}`, async () => {
    const { p, state } = await fixture(member, 22)
    const a = p.locator("#comment-1"), b = p.locator("#comment-2")
    const content = "응답을 받지 못한 첫 답글", saved = []
    const pending = gate()
    let roots = 0, reads = 0, otherReads = 0
    Object.assign(comments[0], { deleted: false, replyCount: 0 })
    Object.assign(comments[1], { deleted: false, replyCount: 1 })
    state.get = async (route, url, result) => {
      if (url.pathname.endsWith("/2/replies")) {
        otherReads++
        return route.fulfill({ json: { items: [item(102)], hasNext: false, nextCursor: null } })
      }
      if (saved.length && url.pathname.endsWith("/comments")) {
        roots++
        if (roots === 1 && firstFailure === "원댓글") return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
      }
      if (url.pathname.endsWith("/1/replies")) {
        reads++
        if (reads === 1) await pending.hold()
        if (reads === 1 && firstFailure === "답글") return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
        return route.fulfill({ json: { items: saved, hasNext: false, nextCursor: null } })
      }
      return route.fulfill(result)
    }
    state.write = route => {
      assert.equal(route.request().method(), "POST")
      assert.ok(route.request().url().endsWith("/1/replies"))
      saved.push({ ...item(201), content: route.request().postDataJSON().content })
      comments[0].replyCount = saved.length
      return route.abort("failed")
    }
    await p.locator("#comments-refresh").click()
    await b.getByRole("button", { name: "답글 1개 보기" }).click()
    await b.locator('[data-comment-item="102"]').waitFor()
    await b.getByRole("button", { name: "답글 달기" }).click()
    await b.getByRole("textbox").fill("다른 원댓글의 초안")
    assert.equal(await a.getByRole("button", { name: /답글 .*개 보기/ }).count(), 0)
    await a.getByRole("button", { name: "답글 달기" }).click()
    const field = a.getByRole("textbox")
    await field.fill(content)
    const submit = a.getByRole("button", { name: "답글 등록", exact: true })
    await submit.scrollIntoViewIfNeeded()
    const before = await p.evaluate(() => scrollY)
    await submit.evaluate(button => { button.click(); button.click() })
    try {
      await a.getByText("답글 수와 공개 목록을 확인하고 있어요.").waitFor()
      assert.equal(await submit.count(), 0)
      assert.equal(await a.getByRole("button", { name: "답글 목록 다시 확인" }).isDisabled(), true)
      await field.evaluate(input => input.form.requestSubmit())
      assert.equal(state.calls.filter(c => c.method === "POST").length, 1)
    } finally { pending.release() }
    const recovering = firstFailure !== "없음"
    if (recovering) {
      const retry = a.getByRole("button", { name: "답글 목록 다시 확인" })
      await a.getByText("답글 목록을 확인하지 못했어요.").waitFor()
      assert.equal(await submit.count(), 0)
      await retry.click()
    }
    await a.getByRole("button", { name: "답글 다시 등록" }).waitFor()
    const shown = a.locator('[data-comment-item="201"] .community-comment-body')
    assert.equal(await shown.textContent(), content)
    assert.equal(await shown.count(), 1)
    await a.getByRole("button", { name: "답글 1개 접기", exact: true }).waitFor()
    assert.equal(await field.inputValue(), content)
    assert.equal(await a.locator("[data-created-reply-id]").count(), 0)
    assert.equal(await p.getByText("답글을 등록했습니다.", { exact: true }).count(), 0)
    assert.equal(await b.getByRole("textbox").inputValue(), "다른 원댓글의 초안")
    assert.equal(await b.getByRole("button", { name: /^답글 \d+개 접기$/ }).getAttribute("aria-expanded"), "true")
    assert.equal(otherReads, 1)
    assert.equal(state.calls.filter(c => c.method === "POST").length, 1)
    assert.deepEqual([roots, reads], recovering ? [2, 2] : [1, 1])
    if (!recovering) assert.ok(Math.abs(await p.evaluate(() => scrollY) - before) <= 2)
    console.log(`응답 유실(${firstFailure}): POST 1, 원댓글 GET ${roots}, 답글 GET ${reads}, count 0→1, ID 201 한 번, 초안 유지`)
  })

 await check("마지막 답글 페이지 조회 실패 때 등록 확인을 유지하고 숨긴 ID는 조회 성공 후 제거한다", async () => {
  const { p, state } = await fixture(member, 2)
  const a = p.locator("#comment-1")
  const replies = Array.from({ length: 20 }, (_, i) => item(101 + i))
  Object.assign(comments[0], { replyCount: 20, deleted: false })
  const cursors = []
  state.get = (route, url, result) => {
    if (!url.pathname.endsWith("/1/replies")) return route.fulfill(result)
    const after = url.searchParams.get("afterId")
    cursors.push(after)
    if (after && cursors.length === 2) return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
    const items = replies.filter(c => c.commentId > Number(after)).slice(0, 20)
    const hasNext = !after && replies.length > items.length
    return route.fulfill({ json: { items, hasNext, nextCursor: hasNext
      ? { commentId: 120, createdAt: item(120).createdAt, sort: "OLDEST", likeCount: null } : null } })
  }
  const content = "숨길 답글 본문"
  state.write = route => {
    replies.push({ ...item(201), content })
    comments[0].replyCount = 21
    return route.fulfill({ status: 201, json: { commentId: 201 } })
  }
  await a.getByRole("button", { name: "답글 달기" }).click()
  await a.getByRole("textbox").fill(content)
  await a.getByRole("button", { name: "답글 등록" }).click()
  const more = a.getByRole("button", { name: "답글 더 보기" })
  await more.waitFor()
  const receipt = a.locator('[data-created-reply-id="201"]')
  assert.equal(await receipt.getByText(content).count(), 1)
  assert.deepEqual(cursors, [null])
  await a.getByRole("button", { name: "답글 21개 접기" }).waitFor()
  replies.pop()
  comments[0].replyCount = 20
  await more.click()
  const retry = a.getByRole("button", { name: "답글 다시 시도" })
  await retry.waitFor()
  assert.equal(await receipt.getByText(content).count(), 1)
  await a.getByRole("button", { name: "답글 21개 접기" }).waitFor()
  await retry.click()
  await receipt.waitFor({ state: "detached" })
  assert.equal(await a.getByText(content, { exact: true }).count(), 0)
  assert.equal(await a.locator(".comment-reply-list > li").count(), 20)
  await a.getByRole("button", { name: "답글 20개 접기" }).click()
  await a.getByRole("button", { name: "답글 20개 보기" }).click()
  assert.equal(await a.locator(".comment-reply-list > li:visible").count(), 20)
  assert.deepEqual(cursors, [null, "120", "120"])
  assert.equal(state.calls.filter(c => c.method === "POST").length, 1)
 })

 for (const width of [390, 1440]) await check(`이미 읽은 답글이 숨겨지면 원댓글 개수를 확인하고 작성 위치를 유지한다 · ${width}px`, async () => {
  const { p, state } = await fixture(member, 1)
  await p.setViewportSize({ width, height: 900 })
  const row = p.locator("#comment-1")
  let replies = [101, 102, 103]
  Object.assign(comments[0], { deleted: false, replyCount: replies.length })
  state.get = (route, url, result) => {
    if (!url.pathname.endsWith("/1/replies")) return route.fulfill(result)
    const items = replies.filter(id => id > Number(url.searchParams.get("afterId"))).slice(0, 2).map(item)
    const last = items.at(-1)
    const hasNext = !!last && last.commentId < replies.at(-1)
    return route.fulfill({ json: { items, hasNext, nextCursor: hasNext
      ? { createdAt: last.createdAt, commentId: last.commentId, sort: "OLDEST", likeCount: null } : null } })
  }
  await p.getByRole("button", { name: "목록 새로고침" }).click()
  await row.getByRole("button", { name: "답글 3개 보기" }).click()
  await row.getByRole("button", { name: "답글 더 보기" }).waitFor()
  replies = [102, 103]
  comments[0].replyCount = 2
  await row.getByRole("button", { name: "답글 더 보기" }).click()
  const toggle = row.getByRole("button", { name: "답글 2개 접기" })
  await toggle.waitFor()
  await row.locator('[data-comment-item="103"]').waitFor()
  await row.locator('[data-comment-item="101"]').waitFor({ state: "detached" })
  const write = row.getByRole("button", { name: "답글 달기" })
  assert.equal(await row.locator(":scope > .community-comment-toolbar").getByRole("button", { name: "답글 달기" }).count(), 1)
  const likeBox = await btn(p).boundingBox(), writeBox = await write.boundingBox()
  assert.ok(writeBox.x >= likeBox.x + likeBox.width - 1 && writeBox.y < likeBox.y + likeBox.height)
  await write.focus()
  await p.keyboard.press("Enter")
  await p.waitForFunction(() => document.activeElement?.id === "reply-input-1")
  const toolbarBox = await row.locator(":scope > .community-comment-toolbar").boundingBox()
  const formBox = await row.locator("form").boundingBox(), toggleBox = await toggle.boundingBox()
  assert.ok(formBox.y >= toolbarBox.y + toolbarBox.height - 1 && toggleBox.y >= formBox.y + formBox.height - 1)
  await row.scrollIntoViewIfNeeded()
  await p.screenshot({ path: `${output}/expanded-form-${width}.png` })
  await row.getByRole("button", { name: "취소", exact: true }).click()
  await p.waitForFunction(() => document.activeElement?.id === "reply-write-1")
  await p.screenshot({ path: `${output}/expanded-actions-${width}.png` })
  replies = []
  comments[0].replyCount = 0
  await p.getByRole("button", { name: "목록 새로고침" }).click()
  await toggle.waitFor({ state: "detached" })
  assert.equal(await row.locator('[id^="reply-toggle-"]').count(), 0)
  await write.waitFor()
 })

 await check("비회원 답글 달기는 로그인 안내를 열고 등록을 보내지 않는다", async () => {
  const { p, state } = await fixture(null, 1)
  await p.locator("#comment-1").getByRole("button", { name: "답글 달기" }).click()
  await p.getByRole("dialog").getByRole("button", { name: "로그인하기" }).waitFor()
  assert.equal(state.calls.filter(c => c.method === "POST").length, 0)
 })

 for (const result of ["만료", "부모 숨김", "응답 유실"]) await check(`답글 등록 오류와 초안 보존 · ${result}`, async () => {
  const { p, state } = await fixture(member, 2)
  const row = p.locator("#comment-1"), content = "보관할 답글"
  state.write = route => {
    if (result === "만료") {
      state.identity = null
      return route.fulfill({ status: 401, json: failure("AUTHENTICATION_REQUIRED") })
    }
    if (result === "부모 숨김") {
      comments = comments.filter(c => c.commentId !== 1)
      return route.fulfill({ status: 404, json: failure("PARENT_COMMENT_NOT_FOUND") })
    }
    return route.abort("failed")
  }
  await row.getByRole("button", { name: "답글 달기" }).click()
  const field = p.locator("#reply-input-1")
  await field.fill("가".repeat(2001))
  await row.getByRole("button", { name: "답글 등록" }).click()
  await row.getByText("댓글 내용은 2,000자 이하여야 합니다.").waitFor()
  assert.equal(state.calls.filter(c => c.method === "POST").length, 0)
  await field.fill(content)
  await row.getByRole("button", { name: "답글 등록" }).click()
  await p.getByRole("alert").filter({ hasText: result === "만료" ? "로그인이 만료" : result === "부모 숨김" ? "원댓글이 삭제" : "요청 결과를 확인하지 못했습니다" }).waitFor()
  if (result === "부모 숨김") await row.waitFor({ state: "detached" })
  assert.equal(await field.inputValue(), content)
  assert.equal(state.calls.filter(c => c.method === "POST").length, 1)
 })

 for (const alreadyDeleted of [false, true]) await check(
  alreadyDeleted ? "삭제 404 후 조회를 재시도해 같은 원댓글의 공개 답글을 읽는다" :
    "작성자 삭제 후 현재 조회 범위에서 공개 답글만 남고 답글 없는 행은 사라진다", async () => {
  const { p, state } = await fixture(member, 22)
  const parent = comments[20]
  Object.assign(parent, { deleted: false, replyCount: 1 })
  const original = parent.content
  await p.getByRole("button", { name: "댓글 더 보기", exact: true }).click()
  const row = p.locator("#comment-21")
  await row.getByRole("button", { name: "답글 1개 보기" }).waitFor()
  const rootCursors = []
  let failRead = true
  state.get = (route, url, result) => {
    if (url.pathname.endsWith("/21/replies")) return route.fulfill({ json: { items: [item(101)], hasNext: false, nextCursor: null } })
    if (url.pathname.endsWith("/comments")) rootCursors.push(url.searchParams.get("afterId"))
    if (parent.deleted && url.pathname.endsWith("/comments") && failRead) {
      failRead = false
      return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
    }
    return route.fulfill(result)
  }
  state.write = route => {
    assert.equal(route.request().method(), "DELETE")
    if (route.request().url().endsWith("/21")) {
      Object.assign(parent, { deleted: true, author: null, content: "삭제된 댓글입니다." })
    } else comments = comments.filter(c => c.commentId !== 22)
    return alreadyDeleted
      ? route.fulfill({ status: 404, json: failure("COMMENT_NOT_FOUND") })
      : route.fulfill({ status: 204 })
  }
  const remove = async row => {
    await row.getByRole("button", { name: "댓글 삭제", exact: true }).click()
    await p.getByRole("button", { name: "댓글 삭제하기" }).click()
  }
  await remove(row)
  await p.getByRole("button", { name: "댓글 조회 다시 시도" }).waitFor()
  assert.equal(await row.count(), 0)
  await p.getByRole("button", { name: "댓글 조회 다시 시도" }).click()
  await row.getByText("삭제된 댓글입니다.", { exact: true }).waitFor()
  assert(!(await row.textContent()).includes(original))
  assert.equal(await row.locator("[data-comment-like], .community-comment-meta strong, .community-comment-tools").count(), 0)
  await row.getByRole("button", { name: "답글 1개 보기" }).click()
  await row.locator('[data-comment-item="101"]').waitFor()
  if (alreadyDeleted) {
    assert.equal(await row.locator('.comment-reply-list .community-comment-body').textContent(), item(101).content)
    assert.deepEqual(rootCursors, [null, null, "20"])
    assert.equal(state.calls.filter(call => call.method === "DELETE").length, 1)
    assert.equal(state.calls.filter(call => call.path.endsWith("/21/replies") && call.method === "GET").length, 1)
    console.log("DELETE 404 ×1; 원댓글 GET 커서", rootCursors, "답글 GET ×1; 삭제 원댓글 21 → 답글 101")
    return
  }
  await remove(p.locator("#comment-22"))
  await p.waitForLoadState("networkidle")
  assert.equal(await p.locator("#comment-22").count(), 0)
  assert.equal(state.calls.filter(c => c.method === "DELETE").length, 2)
 })

 for (const failBest of [false, true]) await check(
  `삭제 404 직후 베스트 원문을 가리고 차순위를 반영한다 (${failBest ? "재시도" : "정상 조회"})`, async () => {
    const { p, state } = await fixture(member, 4)
    const parent = comments[0]
    Object.assign(parent, { likeCount: 14, replyCount: 1, deleted: false })
    comments.slice(1).forEach((c, i) => { c.likeCount = 13 - i })
    const row = p.locator("#comment-1")
    const card = id => p.locator(`[data-best-comment="${id}"]`)
    const rank = () => p.locator("[data-best-comment]").evaluateAll(nodes => nodes.map(n => Number(n.dataset.bestComment)))
    await p.locator("#comments-refresh").click()
    await card(1).waitFor()
    assert.deepEqual(await rank(), [1, 2, 3])
    assert.equal(await card(1).locator("p").textContent(), parent.content)
    assert.ok((await card(1).textContent()).includes(parent.author.nickname))

    const oldBest = gate(), nextBest = gate(), roots = gate()
    let bestReads = 0
    state.get = async (route, url, result) => {
      if (url.pathname.endsWith("/comments/best")) {
        bestReads++
        if (bestReads === 1) {
          // Snapshot the public card before the other tab deletes the root.
          const snapshot = structuredClone(result)
          await oldBest.hold()
          return route.fulfill(snapshot)
        }
        if (bestReads === 2) {
          await nextBest.hold()
          if (failBest) return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
        }
      }
      if (url.pathname.endsWith("/comments") && parent.deleted) await roots.hold()
      if (url.pathname.endsWith("/1/replies")) {
        return route.fulfill({ json: { items: [item(101)], hasNext: false, nextCursor: null } })
      }
      return route.fulfill(result)
    }
    state.write = route => {
      assert.equal(route.request().method(), "DELETE")
      assert.ok(route.request().url().endsWith("/comments/1"))
      Object.assign(parent, { deleted: true, author: null, content: "삭제된 댓글입니다.", likeCount: 0 })
      return route.fulfill({ status: 404, json: failure("COMMENT_NOT_FOUND") })
    }
    try {
      await p.locator("#comments-refresh").click()
      await oldBest.reached
      await row.getByRole("button", { name: "댓글 삭제", exact: true }).click()
      await p.getByRole("button", { name: "댓글 삭제하기" }).click()
      await card(1).waitFor({ state: "detached" })
      await row.waitFor({ state: "detached" })
      assert.deepEqual(await rank(), [2, 3])
      await Promise.all([nextBest.reached, roots.reached])
      nextBest.release()
      if (failBest) await p.getByRole("button", { name: "베스트 댓글 다시 시도" }).click()
      await card(4).waitFor()
      assert.deepEqual(await rank(), [2, 3, 4])
      oldBest.release()
      roots.release()
      await row.getByText("삭제된 댓글입니다.", { exact: true }).waitFor()
      await row.getByRole("button", { name: "답글 1개 보기" }).click()
      const body = row.locator('[data-comment-item="101"] .community-comment-body')
      await body.waitFor()
      assert.equal(await body.textContent(), item(101).content)
      assert.deepEqual(await rank(), [2, 3, 4])
      assert.equal(state.calls.filter(c => c.method === "DELETE").length, 1)
      assert.equal(bestReads, failBest ? 3 : 2)
      console.log(`베스트 [1,2,3] → 404 직후 [2,3] → [2,3,4]; DELETE 1회, 404 후 베스트 GET ${bestReads - 1}회; 원댓글 1 → 답글 101`)
    } finally {
      oldBest.release()
      nextBest.release()
      roots.release()
    }
  })

 await check("수동 새로고침은 펼친 답글만 교체하고 이전 페이지 응답을 버린다", async () => {
  const { p, state } = await fixture(null, 22)
  comments.slice(0, 2).forEach(c => Object.assign(c, { deleted: false, replyCount: 2 }))
  const a = p.locator("#comment-1"), b = p.locator("#comment-2")
  const delayed = gate()
  const requests = []
  let paginated = false, refreshed = false, failRefresh = true
  state.get = async (route, url, result) => {
    if (!url.pathname.endsWith("/replies")) return route.fulfill(result)
    const first = url.pathname.includes("/1/replies")
    const after = url.searchParams.get("afterId")
    requests.push([first ? 1 : 2, after])
    if (after) await delayed.hold()
    if (first && refreshed && !after && failRefresh) {
      failRefresh = false
      return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
    }
    const ids = first ? (after ? [102] : paginated ? [101] : [101, 102]) : refreshed ? [203, 204] : [201, 202]
    const more = first && paginated && !refreshed && !after
    return route.fulfill({ json: { items: ids.map(item), hasNext: more,
      nextCursor: more ? { commentId: 101, createdAt: item(101).createdAt, sort: "OLDEST", likeCount: null } : null } })
  }
  await p.locator("#comments-refresh").click()
  await a.getByRole("button", { name: "답글 2개 보기" }).click()
  await b.getByRole("button", { name: "답글 2개 보기" }).click()
  await b.locator('[data-comment-item="202"]').waitFor()
  await b.getByRole("button", { name: /^답글 \d+개 접기$/ }).click()
  await a.locator('[data-comment-item="102"]').waitFor()
  paginated = true
  await p.locator("#comments-refresh").click()
  await a.getByRole("button", { name: "답글 더 보기" }).click()
  await delayed.reached
  refreshed = true
  comments[0].replyCount = 1
  const refresh = p.locator("#comments-refresh")
  await refresh.evaluate(el => el.scrollIntoView({ block: "center", behavior: "instant" }))
  const before = await p.evaluate(() => scrollY)
  await refresh.click()
  await a.getByRole("button", { name: "답글 다시 시도" }).waitFor()
  const after = await p.evaluate(() => scrollY)
  assert.ok(Math.abs(after - before) <= 2, `refresh scroll: ${before} → ${after}`)
  assert.equal(await a.locator(".comment-reply-list li").count(), 0)
  assert.equal(await a.getByRole("button", { name: /^답글 \d+개 접기$/ }).getAttribute("aria-expanded"), "true")
  delayed.release()
  await a.getByRole("button", { name: "답글 다시 시도" }).click()
  await a.locator('[data-comment-item="101"]').waitFor()
  await p.waitForLoadState("networkidle")
  assert.deepEqual(await a.locator(".comment-reply-list li").evaluateAll(rows => rows.map(r => r.dataset.commentItem)), ["101"])
  assert.equal(requests.filter(([parent]) => parent === 2).length, 1)
  await a.getByRole("button", { name: /^답글 \d+개 접기$/ }).click()
  await a.getByRole("button", { name: "답글 1개 보기" }).waitFor()
  await b.getByRole("button", { name: "답글 2개 보기" }).click()
  await b.locator('[data-comment-item="204"]').waitFor()
  assert.deepEqual(requests.filter(([parent]) => parent === 2), [[2, null], [2, null]])
  assert.equal(await b.locator('[data-comment-item="201"]').count(), 0)
 })

 await check("전체 목록 재시도는 열린 답글을 갱신하고 원댓글 더 보기 재시도는 커서만 이어간다", async () => {
  const { p, state } = await fixture(null, 22)
  const a = p.locator("#comment-1"), b = p.locator("#comment-2")
  Object.assign(comments[0], { replyCount: 2, deleted: false })
  Object.assign(comments[1], { replyCount: 1, deleted: false })
  const roots = [], replies = []
  let ids = [101, 102], failNextRoot = false
  state.get = (route, url, result) => {
    if (url.pathname.endsWith("/comments")) {
      roots.push(url.searchParams.get("afterId"))
      if (failNextRoot) {
        failNextRoot = false
        return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
      }
    }
    if (url.pathname.endsWith("/replies")) {
      replies.push(url.pathname.split("/").at(-2))
      return route.fulfill({ json: { items: ids.map(item), hasNext: false, nextCursor: null } })
    }
    return route.fulfill(result)
  }
  await p.locator("#comments-refresh").click()
  await b.getByRole("button", { name: "답글 1개 보기" }).waitFor()
  await a.getByRole("button", { name: "답글 2개 보기" }).click()
  const shown = () => a.locator(".comment-reply-list li").evaluateAll(rows => rows.map(r => Number(r.dataset.commentItem)))
  await a.locator('[data-comment-item="102"]').waitFor()
  assert.deepEqual(await shown(), [101, 102])
  roots.length = 0
  replies.length = 0
  failNextRoot = true
  await p.locator("#comments-refresh").click()
  const retry = p.getByRole("button", { name: "댓글 조회 다시 시도" })
  await retry.waitFor()
  await p.waitForLoadState("networkidle")
  assert.deepEqual(await shown(), [101, 102])
  ids = [101]
  comments[0].replyCount = 1
  await retry.click()
  await retry.waitFor({ state: "hidden" })
  await a.locator('[data-comment-item="102"]').waitFor({ state: "detached" })
  await a.locator('[data-comment-item="101"]').waitFor()
  assert.deepEqual(await shown(), [101])
  assert.equal(await a.getByRole("button", { name: /^답글 \d+개 접기$/ }).getAttribute("aria-expanded"), "true")
  assert.deepEqual(roots, [null, null])
  assert.deepEqual(replies, ["1", "1"])
  failNextRoot = true
  await p.getByRole("button", { name: "댓글 더 보기", exact: true }).click()
  await retry.click()
  await p.locator("#comment-21").waitFor()
  assert.deepEqual(roots, [null, null, "20", "20"])
  assert.deepEqual(replies, ["1", "1"])
  await a.getByRole("button", { name: /^답글 \d+개 접기$/ }).click()
  await a.getByRole("button", { name: "답글 1개 보기" }).waitFor()
  console.log("수동·전체 재시도: 원댓글 GET ×2, 답글 A GET ×2, B ×0; [101,102] → [101]; 더 보기·재시도: 커서 20 ×2, 답글 GET ×0")
 })

 for (const draft of ["", "별도로 작성하는 초안"]) await check(
  `편집 중 삭제되면 원댓글로 포커스를 옮기고 새 초안만 보호한다 (초안 ${draft ? "있음" : "없음"})`, async () => {
    const { p, state } = await fixture(member, 3)
    if (draft) await p.locator("#comment-draft").fill(draft)
    await p.locator("#comment-edit-button-1").click()
    const edit = p.locator("#comment-edit-1")
    await edit.fill("저장하지 않은 수정")
    const wait = gate()
    Object.assign(comments[0], { deleted: true, author: null, content: "삭제된 댓글입니다.", replyCount: 1 })
    state.get = async (route, url, result) => {
      if (url.pathname.endsWith("/comments")) await wait.hold()
      await route.fulfill(result)
    }
    await p.locator("#comments-refresh").click()
    await wait.reached
    await edit.focus()
    wait.release()
    await p.getByText("댓글이 삭제되어 수정을 종료했어요.", { exact: true }).waitFor()
    assert.equal(await edit.count(), 0)
    assert.equal(await p.evaluate(() => document.activeElement.id), "comment-1")
    assert.equal(await p.locator("#comment-draft").inputValue(), draft)
    await p.locator(".community-back").click()
    if (draft) {
      await p.getByRole("button", { name: "계속 작성", exact: true }).click()
      assert.equal(await p.locator("#comment-draft").inputValue(), draft)
    } else await p.waitForURL(url => url.pathname !== "/community/posts/7")
  })
 for (const width of [390, 1440]) await check(`비회원이 삭제 원댓글과 공개 원댓글의 답글을 독립적으로 조회한다 ${width}`, async () => {
  const { p, state } = await fixture(null, 22)
  await p.setViewportSize({ width, height: 900 })
  Object.assign(comments[0], { deleted: true, author: null, content: "삭제된 댓글입니다.", replyCount: 2 })
  Object.assign(comments[1], { deleted: false, replyCount: 2 })
  const requests = []
  const delayed = gate()
  let failMore = true
  state.get = async (route, url, result) => {
    if (!url.pathname.endsWith("/replies")) return route.fulfill(result)
    assert.equal(route.request().method(), "GET")
    const parent = Number(url.pathname.split("/").at(-2))
    const after = url.searchParams.get("afterId")
    requests.push([parent, after])
    if (parent === 2 && requests.filter(([id]) => id === 2).length === 1) await delayed.hold()
    if (after) {
      assert.equal(parent, 1)
      assert.equal(after, "101")
      assert.equal(url.searchParams.get("afterCreatedAt"), item(101).createdAt)
      if (failMore) {
        failMore = false
        return route.fulfill({ status: 503, json: failure("SERVER_ERROR") })
      }
    }
    const ids = parent === 1 ? (after ? [102] : [101]) : [201, 202]
    const hasNext = parent === 1 && !after
    return route.fulfill({ json: { items: ids.map(item), hasNext,
      nextCursor: hasNext ? { sort: "OLDEST", likeCount: null, commentId: 101, createdAt: item(101).createdAt } : null } })
  }
  await p.getByRole("button", { name: "목록 새로고침" }).click()
  const a = p.locator("#comment-1"), b = p.locator("#comment-2")
  const toggle = row => row.getByRole("button", { name: /^답글 2개 (보기|접기)$/ })
  const ids = row => row.locator(".comment-reply-list > li").evaluateAll(els => els.map(el => Number(el.dataset.commentItem)))
  await a.getByText("삭제된 댓글입니다.", { exact: true }).waitFor()
  assert.equal(await toggle(a).getAttribute("aria-expanded"), "false")
  assert.deepEqual(requests, [])
  assert.equal(await p.locator("#comment-3").getByRole("button", { name: /답글 .*개 보기/ }).count(), 0)
  assert.equal(await p.locator("#comment-3").getByRole("button", { name: "답글 달기" }).count(), 1)
  assert.equal(await a.getByRole("button", { name: "답글 달기" }).count(), 0)
  await toggle(a).click()
  await a.getByRole("button", { name: "답글 더 보기" }).waitFor()
  await toggle(b).click()
  await delayed.reached
  await toggle(b).click()
  delayed.release()
  assert.equal(await toggle(b).getAttribute("aria-expanded"), "false")
  await toggle(b).click()
  await b.locator('[data-comment-item="202"]').waitFor()
  assert.deepEqual(await ids(a), [101])
  await a.getByRole("button", { name: "답글 더 보기" }).click()
  await a.getByRole("alert").waitFor()
  assert.deepEqual(await ids(b), [201, 202])
  assert.equal(await p.getByRole("button", { name: "답글 더보기" }).count(), 0)
  await a.getByRole("button", { name: "답글 다시 시도" }).click()
  await a.locator('[data-comment-item="102"]').waitFor()
  assert.deepEqual(await ids(a), [101, 102])
  assert.deepEqual(requests, [[1,null],[2,null],[2,null],[1,"101"],[1,"101"]])
  await toggle(a).click()
  assert.equal(await toggle(b).getAttribute("aria-expanded"), "true")
  assert.deepEqual(await ids(b), [201, 202])
  await p.getByRole("button", { name: "댓글 더 보기", exact: true }).click()
  await p.locator("#comment-22").waitFor()
  assert.deepEqual(await ids(b), [201, 202])
  assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
 })
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
