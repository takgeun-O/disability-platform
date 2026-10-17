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
let comments = [],
  readFail = false
function read(url) {
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
        items: [post],
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
async function fixture(identity = member, count = 22) {
  comments = Array.from({ length: count }, (_, i) => item(i + 1))
  readFail = false
  const ctx = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: "reduce",
  })
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
      if (method === "GET")
        return state.get ? state.get(r, u) : r.fulfill(read(u))
      assert.equal(req.headers()["x-csrf-token"], "test-only")
      if (state.write) return state.write(r)
      const id = Number(u.pathname.split("/").at(-1))
      if (method === "POST") {
        const created = comments.length
          ? Math.max(...comments.map((c) => c.commentId)) + 1
          : 1
        comments.push({ ...item(created), content: req.postDataJSON().content })
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
  await p.getByRole("button", { name: "댓글 등록", exact: true }).waitFor()
  if (identity)
    await p.getByRole("button", { name: "댓글 등록", exact: true }).isEnabled()
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
  // Design change: await first comments on the server so no-JS readers see real content.
  for (const fail of [false, true]) await check(`delayed server comments resolve before render; failure=${fail}`, async () => {
    comments=[item(1)];readFail=false;ssrReadReached=false
    let release
    ssrGate=new Promise(resolve=>release=resolve)
    const ctx=await browser.newContext(),p=await ctx.newPage()
    let commentReads=0
    p.on("request",r=>{if(new URL(r.url()).origin===api&&new URL(r.url()).pathname.endsWith("/comments"))commentReads++})
    await p.route(u=>u.origin===api&&u.pathname.endsWith("/me"),r=>r.fulfill({status:401,json:failure("AUTHENTICATION_REQUIRED")}))
    try {
      const navigation=p.goto(base+"/community/posts/7")
      await until(()=>ssrReadReached)
      readFail=fail;ssrGate=null;release()
      const response=await navigation
      assert.equal(response.status(),200)
      assert(await p.getByText(post.content,{exact:true}).isVisible())
      assert.equal(commentReads,0)
      if(fail){
        assert(await p.locator(".community-comments [role=alert]").isVisible())
        await p.route(u=>u.origin===api&&u.pathname.endsWith("/comments"),r=>r.fulfill({json:{items:[item(1)],hasNext:false,nextCursor:null}}))
        await p.getByRole("button",{name:"댓글 조회 다시 시도"}).click()
        await p.locator("#comment-1").waitFor()
      } else assert(await p.locator("#comment-1").isVisible())
    } finally {ssrGate=null;release();readFail=false;await ctx.close()}
  })
  await check("field errors appear once immediately below each textarea; clear when valid", async()=>{
    const {ctx,p,state}=await fixture(member,3)
    const draft=p.locator("#comment-draft")
    for(const value of [""," \n\t","😀".repeat(2001)]){
      await draft.fill(value)
      await p.getByRole("button",{name:"댓글 등록",exact:true}).click()
      const expected=value.includes("😀")?"댓글 내용은 2,000자 이하여야 합니다.":"댓글 내용은 필수입니다."
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
  for(const method of ["refresh","PUT404"]) await check("missing edited comment retains accessible draft and releases only its dirty state: "+method,async()=>{
    const {ctx,p,state}=await fixture(member,3)
    await p.locator("#comment-draft").fill("별도의 새 댓글 초안")
    await p.locator("#comment-edit-button-1").click()
    await p.locator("#comment-edit-1").fill("사라지면 안 되는 수정 초안")
    if(method==="refresh"){
      comments=comments.filter(c=>c.commentId!==1)
      await p.locator("#comments-refresh").click()
    }else{
      state.write=r=>r.fulfill({status:404,json:failure("COMMENT_NOT_FOUND")})
      await p.getByRole("button",{name:"댓글 저장",exact:true}).click()
    }
    await p.getByRole("heading",{name:"수정 초안을 보관하고 있어요"}).waitFor()
    assert.equal(await p.locator("#comment-edit-1").inputValue(),"사라지면 안 되는 수정 초안")
    if(method==="refresh")assert(await p.getByText(/이 결과만으로 삭제 여부를 확정할 수는 없습니다/).isVisible())
    await p.getByRole("button",{name:"초안 전체 선택"}).click()
    assert(await p.locator("#comment-edit-1").evaluate(el=>el.selectionStart===0&&el.selectionEnd===el.value.length))
    await p.screenshot({path:output+"/review-detached-"+method+".png",fullPage:true})
    await p.getByRole("button",{name:"편집 종료"}).click()
    await p.getByRole("button",{name:"계속 수정"}).click()
    assert.equal(await p.locator("#comment-edit-1").inputValue(),"사라지면 안 되는 수정 초안")
    await p.getByRole("button",{name:"편집 종료"}).click()
    await p.getByRole("button",{name:"수정 내용 버리기"}).click()
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
        1,
      )
      release()
      await p.locator(".community-comments [role=alert]").waitFor()
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
      assert.equal(await p.locator("#comment-2 button").count(), 0)
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
        await p.evaluate(() => document.activeElement.textContent),
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
  await check(
    "anonymous cannot manage or submit; safe login return",
    async () => {
      const { ctx, p } = await fixture(null, 1)
      await p.locator(".community-comments a").waitFor()
      assert.equal(await p.locator(".community-comment-list button").count(), 0)
      assert(
        await p
          .getByRole("button", { name: "댓글 등록", exact: true })
          .isDisabled(),
      )
      assert.equal(
        await p.locator(".community-comments a").getAttribute("href"),
        "/login?from=" + encodeURIComponent("/community/posts/7?from=" + encodeURIComponent("/community/topics/hearing-aid")),
      )
      await ctx.close()
    },
  )
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
      await p.locator(".community-comments [role=alert]").waitFor()
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
      state.write = (r) =>
        r.fulfill({ status: 404, json: failure("COMMENT_NOT_FOUND") })
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
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.locator("#comment-edit-button-1").focus();await p.keyboard.press("Tab")
      assert.equal(await p.evaluate(()=>document.activeElement.textContent),"댓글 삭제")
      await p.screenshot({path:output+"/comments-keyboard-long-"+width+".png",fullPage:true})
      await p.keyboard.press("Enter");await p.keyboard.press("Shift+Tab")
      assert.equal(await p.evaluate(()=>document.activeElement.textContent),"댓글 삭제하기")
      await p.keyboard.press("Escape");assert.equal(await p.evaluate(()=>document.activeElement.textContent),"댓글 삭제")
    }
    await ctx.close()
  })
} finally {
  await fs.writeFile(
    output + "/results.json",
    JSON.stringify({ kind: "mock", results }, null, 2),
  )
  await browser.close()
  await new Promise((r) => server.close(r))
}
