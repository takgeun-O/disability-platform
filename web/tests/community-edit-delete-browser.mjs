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
  author: { memberId: 123, nickname: "검증회원" },
  createdAt: "2026-10-17T01:00:00Z",
  updatedAt: "2026-10-17T01:00:00Z",
}
const back = "/community/topics/hearing-aid?type=QUESTION",
  detail = "/community/posts/7?from=" + encodeURIComponent(back),
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
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
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
  await fn()
  checks.push(name)
  console.log("PASS", name)
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
try {
  await check("late previous PUT preserves reentered form, baseline, focus and dirty", async () => {
    const { ctx, p, state } = await fixture()
    await p.goto(base + edit)
    await formReady(p)
    await editTitle(p, "이전 화면에서 저장한 제목")
    let release
    state.put = async (r) => {
      const submitted = r.request().postDataJSON()
      await new Promise(resolve => { release = resolve })
      post = { ...post, ...submitted }
      await r.fulfill({ status: 204 })
    }
    await save(p)
    while (!release) await pause(10)
    await p.getByRole("button", { name: "취소", exact: true }).click()
    await p.getByRole("button", { name: "나가기", exact: true }).click()
    await p.waitForURL(u => u.pathname === "/community/posts/7")
    await p.getByRole("link", { name: "수정", exact: true }).click()
    await formReady(p)
    await editTitle(p, "재진입 후 새 제목")
    await p.getByLabel("글 유형", { exact: true }).selectOption("REVIEW")
    await p.getByLabel("주제", { exact: true }).selectOption("COCHLEAR_IMPLANT")
    const input = p.getByLabel("내용", { exact: true })
    await input.fill("재진입 후 새 본문")
    const element = await input.elementHandle()
    const arrived = p.waitForResponse(r => r.request().method() === "PUT")
    release()
    await arrived
    // Drain rendering tasks, not an arbitrary network delay. The prior response has arrived.
    await p.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
    assert(await element.evaluate(e => e.isConnected), "late PUT must not unmount the new editor")
    assert.equal(await input.inputValue(), "재진입 후 새 본문")
    assert.equal(await p.getByLabel("제목", { exact: true }).inputValue(), "재진입 후 새 제목")
    assert.equal(await p.getByLabel("글 유형", { exact: true }).inputValue(), "REVIEW")
    assert.equal(await p.getByLabel("주제", { exact: true }).inputValue(), "COCHLEAR_IMPLANT")
    assert(await input.evaluate(e => e === document.activeElement))
    assert.equal(new URL(p.url()).pathname, "/community/posts/7/edit")
    const dirty = () => p.evaluate(() => {
      const e = new Event("beforeunload", { cancelable: true }); window.dispatchEvent(e); return e.defaultPrevented
    })
    assert(await dirty())
    // Revert to this editing session's original snapshot, not the late server result.
    await editTitle(p, seed.title)
    await input.fill(seed.content)
    await p.getByLabel("글 유형", { exact: true }).selectOption(seed.type)
    await p.getByLabel("주제", { exact: true }).selectOption(seed.topic)
    assert.equal(await dirty(), false)
    await editTitle(p, "남겨둘 새 초안")
    await p.getByRole("button", { name: "취소", exact: true }).click()
    await p.getByRole("button", { name: "계속 수정", exact: true }).click()
    assert.equal(await p.getByLabel("제목", { exact: true }).inputValue(), "남겨둘 새 초안")
    await p.getByRole("button", { name: "취소", exact: true }).click()
    await p.getByRole("button", { name: "나가기", exact: true }).click()
    await p.getByRole("heading", { name: "이전 화면에서 저장한 제목", exact: true }).waitFor()
    await p.getByRole("link", { name: "← 목록으로", exact: true }).click()
    await p.getByRole("link", { name: "이전 화면에서 저장한 제목", exact: true }).waitFor()
    assert.equal(state.count.PUT, 1)
    await ctx.close()
  })
  await check(
    "author id only; checking/anonymous/other member hidden",
    async () => {
      for (const identity of [null, { ...member, memberId: 124 }, member]) {
        const { ctx, p, state } = await fixture(identity)
        let release
        state.me = async (r) => {
          await new Promise((resolve) => (release = resolve))
          return r.fulfill(
            identity
              ? { json: identity }
              : { status: 401, json: failure("AUTHENTICATION_REQUIRED") },
          )
        }
        await p.goto(base + detail)
        await p.locator(".community-post-body").waitFor()
        assert.equal(
          await p.getByRole("button", { name: "삭제", exact: true }).count(),
          0,
        )
        while (!release) await pause(10)
        release()
        if (identity?.memberId === 123) {
          await p.getByRole("button", { name: "삭제", exact: true }).waitFor()
          await shot(p, "detail-owner-1440")
        } else {
          await pause(150)
          assert.equal(
            await p.getByRole("link", { name: "수정", exact: true }).count(),
            0,
          )
        }
        await ctx.close()
      }
    },
  )
  await check(
    "direct edit/reload; original fields; revert dirty; cancel",
    async () => {
      const { ctx, p } = await fixture()
      await p.goto(base + edit)
      await formReady(p)
      assert.equal(
        await p.getByLabel("제목", { exact: true }).inputValue(),
        seed.title,
      )
      assert.equal(
        await p.getByLabel("내용", { exact: true }).inputValue(),
        seed.content,
      )
      await p.reload()
      await formReady(p)
      await editTitle(p)
      await editTitle(p, seed.title)
      await p.getByRole("button", { name: "취소", exact: true }).click()
      await p.waitForURL((u) => u.pathname === "/community/posts/7")
      assert.equal(await p.getByRole("dialog").count(), 0)
      await ctx.close()
    },
  )
  await check(
    "anonymous login return and non-author direct edit denied",
    async () => {
      let f = await fixture(null)
      await f.p.goto(base + edit)
      await f.p.getByRole("link", { name: "로그인하고 수정하기" }).click()
      await f.p.locator("input[name=email]").fill("review@example.test")
      await f.p.locator("input[name=password]").fill("test-only-password")
      await f.p
        .getByRole("main")
        .getByRole("button", { name: "로그인", exact: true })
        .click()
      await f.p.waitForURL((u) => u.pathname === "/community/posts/7/edit")
      await formReady(f.p)
      await f.ctx.close()
      f = await fixture({ ...member, memberId: 124 })
      await f.p.goto(base + edit)
      await f.p.getByText("작성자만 이 게시글을 수정할 수 있습니다.").waitFor()
      assert.equal(await f.p.getByRole("form").count(), 0)
      await f.ctx.close()
    },
  )
  await check(
    "PUT all fields + 204, duplicate prevention, latest detail and topic list",
    async () => {
      const { ctx, p, state } = await fixture()
      await p.goto(base + back)
      await p.getByRole("link", { name: seed.title, exact: true }).click()
      await p.getByRole("link", { name: "수정", exact: true }).click()
      await formReady(p)
      await editTitle(p)
      await p.getByLabel("내용", { exact: true }).fill("수정 내용\n보존")
      state.put = async (r) => {
        await pause(200)
        post = { ...post, ...r.request().postDataJSON() }
        await r.fulfill({ status: 204 })
      }
      await p.getByRole("form", { name: "게시글 수정" }).evaluate((f) => {
        f.requestSubmit()
        f.requestSubmit()
      })
      await p.waitForURL((u) => u.pathname === "/community/posts/7")
      await p
        .getByRole("heading", { name: "새로운 제목", exact: true })
        .waitFor()
      assert.equal(
        await p.locator(".community-post-body").textContent(),
        "수정 내용\n보존",
      )
      assert.equal(state.count.PUT, 1)
      await p.getByRole("link", { name: "← 목록으로", exact: true }).click()
      await p.getByRole("link", { name: "새로운 제목", exact: true }).waitFor()
      await ctx.close()
    },
  )
  await check(
    "dirty cancel, logo leave, beforeunload and keyboard keep editing",
    async () => {
      const { ctx, p } = await fixture()
      await p.goto(base + edit)
      await formReady(p)
      await editTitle(p)
      assert(
        await p.evaluate(() => {
          const e = new Event("beforeunload", { cancelable: true })
          window.dispatchEvent(e)
          return e.defaultPrevented
        }),
      )
      await p.getByRole("button", { name: "취소", exact: true }).click()
      const dialog = p.getByRole("dialog")
      await dialog.waitFor()
      assert(
        await dialog
          .getByRole("button", { name: "계속 수정" })
          .evaluate((e) => e === document.activeElement),
      )
      await p.keyboard.press("Enter")
      assert.equal(
        await p.getByLabel("제목", { exact: true }).inputValue(),
        "새로운 제목",
      )
      await p.getByRole("button", { name: "취소", exact: true }).click()
      await p.getByRole("button", { name: "나가기", exact: true }).click()
      await p.waitForURL((u) => u.pathname === "/community/posts/7")
      await ctx.close()
    },
  )
  for (const [status, code] of [
    [400, "VALIDATION_FAILED"],
    [401, "AUTHENTICATION_REQUIRED"],
    [403, "ACCESS_DENIED"],
    [403, "POST_WRITE_NOT_ALLOWED"],
    [403, "CSRF_TOKEN_INVALID"],
    [404, "POST_NOT_FOUND"],
  ])
    await check("PUT " + code + " input preserved", async () => {
      const { ctx, p, state } = await fixture()
      await p.goto(base + edit)
      await formReady(p)
      await editTitle(p)
      state.put = async (r) => {
        if (status === 401) state.identity = null
        return r.fulfill({
          status,
          json: failure(
            code,
            code === "VALIDATION_FAILED"
              ? [{ field: "title", message: "제목은 필수입니다." }]
              : [],
          ),
        })
      }
      await save(p)
      await p.locator("form [role=alert]").waitFor()
      assert.equal(
        await p.getByLabel("제목", { exact: true }).inputValue(),
        "새로운 제목",
      )
      assert.equal(state.count.PUT, 1)
      if (code === "VALIDATION_FAILED") {
        assert.equal(
          await p
            .getByLabel("제목", { exact: true })
            .getAttribute("aria-invalid"),
          "true",
        )
        assert(
          await p
            .getByLabel("제목", { exact: true })
            .evaluate((e) => e === document.activeElement),
        )
      }
      await ctx.close()
    })
  await check(
    "uncertain PUT/DELETE no automatic retry, new-tab confirmation",
    async () => {
      const { ctx, p, state } = await fixture()
      await p.goto(base + edit)
      await formReady(p)
      await editTitle(p)
      state.put = (r) => r.abort()
      await save(p)
      await p.getByRole("link", { name: "새 탭에서 게시글 확인" }).waitFor()
      assert.equal(state.count.PUT, 1)
      await p.getByRole("button", { name: "취소", exact: true }).click()
      await p.getByRole("button", { name: "나가기", exact: true }).click()
      await p.waitForURL((u) => u.pathname === "/community/posts/7")
      await p.getByRole("button", { name: "삭제", exact: true }).click()
      state.del = (r) => r.abort()
      await p.getByRole("button", { name: "삭제하기", exact: true }).click()
      await p
        .getByRole("dialog")
        .getByRole("link", { name: "새 탭에서 게시글 확인" })
        .waitFor()
      assert.equal(state.count.DELETE, 1)
      await ctx.close()
    },
  )
  await check(
    "delete modal safe focus, Escape, retry error, busy close and successful list exclusion",
    async () => {
      const { ctx, p, state } = await fixture()
      await p.goto(base + detail)
      const remove = p.getByRole("button", { name: "삭제", exact: true })
      await remove.click()
      let d = p.getByRole("dialog")
      assert(
        await d
          .getByRole("button", { name: "취소", exact: true })
          .evaluate((e) => e === document.activeElement),
      )
      await p.keyboard.press("Escape")
      assert(await remove.evaluate((e) => e === document.activeElement))
      assert.equal(state.count.DELETE, 0)
      await remove.click()
      state.del = (r) =>
        r.fulfill({ status: 403, json: failure("ACCESS_DENIED") })
      await d.getByRole("button", { name: "삭제하기" }).click()
      await d.getByRole("alert").waitFor()
      assert.equal(state.count.DELETE, 1)
      await d.getByRole("button", { name: "취소", exact: true }).click()
      await remove.click()
      let release
      state.del = async (r) => {
        await new Promise((resolve) => (release = resolve))
        post = null
        return r.fulfill({ status: 204 })
      }
      await d.getByRole("button", { name: "삭제하기" }).click()
      await d.getByRole("button", { name: "삭제 중…" }).waitFor()
      await p.keyboard.press("Escape")
      assert(await d.isVisible())
      await d
        .getByRole("button", { name: "취소", exact: true })
        .click({ force: true })
      assert(await d.isVisible())
      while (!release) await pause(10)
      release()
      await p.waitForURL((u) => u.pathname === "/community/topics/hearing-aid")
      await p.getByText("아직 표시할 게시글이 없어요.").waitFor()
      assert.equal(
        await p.getByRole("link", { name: seed.title, exact: true }).count(),
        0,
      )
      assert.equal(state.count.DELETE, 2)
      await ctx.close()
    },
  )
  for (const phase of ["csrf", "put-success", "put-failure"])
    await check(
      "approved leave while destination delayed: " + phase,
      async () => {
        const { ctx, p, state } = await fixture()
        await p.goto(base + edit)
        await formReady(p)
        await editTitle(p)
        let release, responseDone
        const done = new Promise((resolve) => (responseDone = resolve))
        const handler = async (r) => {
          await new Promise((resolve) => (release = resolve))
          await r.fulfill(
            phase === "csrf"
              ? { json: { headerName: "X-CSRF-TOKEN", token: "test-only" } }
              : phase === "put-success"
                ? { status: 204 }
                : { status: 403, json: failure("ACCESS_DENIED") },
          )
          responseDone()
        }
        if (phase === "csrf") state.csrf = handler
        else state.put = handler
        let destinationRelease
        await p.route(
          (u) =>
            u.origin === new URL(base).origin &&
            u.pathname === "/community/posts/7" &&
            u.searchParams.has("_rsc"),
          async (r) => {
            await new Promise((resolve) => (destinationRelease = resolve))
            await r.continue()
          },
        )
        await save(p)
        while (!release) await pause(10)
        await p.getByRole("button", { name: "취소", exact: true }).click()
        await p.getByRole("button", { name: "나가기", exact: true }).click()
        while (!destinationRelease) await pause(10)
        assert(await p.getByRole("form", { name: "게시글 수정" }).isVisible())
        release()
        await done
        await pause(100)
        assert.equal(new URL(p.url()).pathname, "/community/posts/7/edit")
        if (phase === "csrf") assert.equal(state.count.PUT, 0)
        destinationRelease()
        await p.waitForURL((u) => u.pathname === "/community/posts/7")
        await ctx.close()
      },
    )
  await check(
    "continue editing preserves pending successful request",
    async () => {
      const { ctx, p, state } = await fixture()
      await p.goto(base + edit)
      await formReady(p)
      await editTitle(p)
      let release
      state.put = async (r) => {
        await new Promise((resolve) => (release = resolve))
        post = { ...post, ...r.request().postDataJSON() }
        await r.fulfill({ status: 204 })
      }
      await save(p)
      while (!release) await pause(10)
      await p.getByRole("button", { name: "취소", exact: true }).click()
      await p.getByRole("button", { name: "계속 수정" }).click()
      release()
      await p.waitForURL((u) => u.pathname === "/community/posts/7")
      await p
        .getByRole("heading", { name: "새로운 제목", exact: true })
        .waitFor()
      await ctx.close()
    },
  )
  await check("edit missing post has no editable form", async () => {
    const { ctx, p } = await fixture()
    post = null
    await p.goto(base + edit)
    await p.getByRole("heading", { name: "게시글을 찾을 수 없어요." }).waitFor()
    assert.equal(await p.getByRole("form").count(), 0)
    await ctx.close()
  })
  await check(
    "read outage distinguished from 404; retry fills form once",
    async () => {
      const { ctx, p } = await fixture()
      readFailure = true
      await p.goto(base + edit)
      await p
        .getByRole("heading", { name: "게시글을 불러오지 못했어요." })
        .waitFor()
      assert.equal(await p.getByRole("form").count(), 0)
      readFailure = false
      await p.getByRole("button", { name: "다시 시도", exact: true }).click()
      await formReady(p)
      await editTitle(p, "다시 조회 후 입력")
      await pause(100)
      assert.equal(
        await p.getByLabel("제목", { exact: true }).inputValue(),
        "다시 조회 후 입력",
      )
      await ctx.close()
    },
  )
  for (const [status, code] of [
    [401, "AUTHENTICATION_REQUIRED"],
    [403, "POST_WRITE_NOT_ALLOWED"],
    [403, "CSRF_TOKEN_INVALID"],
    [404, "POST_NOT_FOUND"],
  ])
    await check(
      "DELETE " + code + " keeps dialog and does not replay",
      async () => {
        const { ctx, p, state } = await fixture()
        await p.goto(base + detail)
        await p.getByRole("button", { name: "삭제", exact: true }).click()
        state.del = (r) => {
          if (status === 401) state.identity = null
          return r.fulfill({ status, json: failure(code) })
        }
        await p
          .getByRole("dialog")
          .getByRole("button", { name: "삭제하기", exact: true })
          .click()
        await p.getByRole("dialog").getByRole("alert").waitFor()
        assert.equal(state.count.DELETE, 1)
        assert.equal(new URL(p.url()).pathname, "/community/posts/7")
        await p
          .getByRole("dialog")
          .getByRole("button", { name: "취소", exact: true })
          .click()
        await ctx.close()
      },
    )
  await check("1440/390/320 edit + delete layout and Tab loop", async () => {
    for (const width of [1440, 390, 320]) {
      const { ctx, p } = await fixture()
      await p.setViewportSize({ width, height: 900 })
      await p.goto(base + edit)
      await formReady(p)
      assert(
        await p.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      )
      await shot(p, "edit-" + width)
      await p.getByRole("button", { name: "취소", exact: true }).click()
      await p.waitForURL((u) => u.pathname === "/community/posts/7")
      await p.getByRole("button", { name: "삭제", exact: true }).click()
      await shot(p, "delete-" + width)
      const d = p.getByRole("dialog")
      await p.keyboard.press("Shift+Tab")
      assert(
        await d
          .getByRole("button", { name: "삭제하기" })
          .evaluate((e) => e === document.activeElement),
      )
      await p.keyboard.press("Tab")
      assert(
        await d
          .getByRole("button", { name: "취소", exact: true })
          .evaluate((e) => e === document.activeElement),
      )
      assert(
        await p.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      )
      await ctx.close()
    }
  })
  for (const [type, topic] of [["REVIEW", "HEARING_AID"], ["QUESTION", "COCHLEAR_IMPLANT"], ["REVIEW", "COCHLEAR_IMPLANT"]]) {
    await check(`change classification ${type}/${topic}, tags, reload, list totals and original return`, async () => {
      const { ctx, p } = await fixture()
      await p.goto(base + back)
      await p.getByRole("link", { name: seed.title, exact: true }).click()
      await p.getByRole("link", { name: "수정", exact: true }).click()
      await formReady(p)
      await p.getByLabel("글 유형", { exact: true }).selectOption(type)
      await p.getByLabel("주제", { exact: true }).selectOption(topic)
      const sent = p.waitForRequest(r => r.method() === "PUT")
      await save(p)
      assert.deepEqual((await sent).postDataJSON(), { type, topic, title: seed.title, content: seed.content })
      await p.waitForURL(u => u.pathname === "/community/posts/7")
      const labels = { REVIEW: "경험·후기", QUESTION: "질문·답변", HEARING_AID: "보청기", COCHLEAR_IMPLANT: "인공와우" }
      for (const label of [labels[type], labels[topic]]) await p.locator("article .community-tags").getByText(label, { exact: true }).waitFor()
      const readApi = url => p.evaluate(async url => (await fetch(url)).json(), api + url)
      const actual = await readApi("/api/v1/community/posts/7")
      assert.equal(actual.type, type); assert.equal(actual.topic, topic)
      const oldList = await readApi("/api/v1/community/posts?type=QUESTION&topic=HEARING_AID")
      const newList = await readApi(`/api/v1/community/posts?type=${type}&topic=${topic}`)
      assert.equal(oldList.totalElements, 0); assert.equal(newList.totalElements, 1)
      assert.equal(oldList.items.length, 0); assert.equal(newList.items[0].id, 7)
      // Reentry in the same document must also see the completed mutation.
      await p.getByRole("link", { name: "수정", exact: true }).click()
      await formReady(p)
      assert.equal(await p.getByLabel("글 유형", { exact: true }).inputValue(), type)
      assert.equal(await p.getByLabel("주제", { exact: true }).inputValue(), topic)
      await p.reload(); await formReady(p)
      assert.equal(await p.getByLabel("글 유형", { exact: true }).inputValue(), type)
      assert.equal(await p.getByLabel("주제", { exact: true }).inputValue(), topic)
      await p.getByRole("button", { name: "취소", exact: true }).click()
      await p.waitForURL(u => u.pathname === "/community/posts/7")
      const returnLink = p.getByRole("link", { name: "← 목록으로", exact: true })
      assert.equal(await returnLink.getAttribute("href"), back)
      await returnLink.click()
      await p.getByText("아직 표시할 게시글이 없어요.").waitFor()
      assert.equal(new URL(p.url()).searchParams.get("type"), "QUESTION")
      assert.equal(await p.getByRole("link", { name: seed.title, exact: true }).count(), 0)
      assert.equal(await p.locator("[data-feed-id]").count(), 0)
      const slug = topic === "HEARING_AID" ? "hearing-aid" : "cochlear-implant"
      await p.goto(base + `/community/topics/${slug}?type=${type}`)
      await p.getByRole("link", { name: seed.title, exact: true }).waitFor()
      assert.equal(await p.locator("[data-feed-id]").count(), 1)
      await ctx.close()
    })
  }
  await check("header text actions at 1440/390/320, long content, touch targets and keyboard return", async () => {
    for (const width of [1440, 390, 320]) {
      const { ctx, p } = await fixture()
      await p.setViewportSize({ width, height: 900 })
      await p.goto(base + detail)
      const remove = p.getByRole("button", { name: "삭제", exact: true })
      await remove.waitFor()
      await shot(p, `header-${width}`)
      post = { ...post, title: "긴제목".repeat(35), author: { ...post.author, nickname: "긴닉네임".repeat(5) } }
      await p.reload(); await remove.waitFor()
      assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      assert(await p.locator("article header .community-owner-actions").count())
      const header = await p.locator("article header").boundingBox()
      for (const action of [remove, p.getByRole("link", { name: "수정", exact: true })]) {
        const box = await action.boundingBox()
        assert(box.width >= 44 && box.height >= 44)
        assert(box.y + box.height <= header.y + header.height && box.x + box.width <= header.x + header.width + 1)
      }
      await p.getByRole("link", { name: "수정", exact: true }).focus()
      await p.keyboard.press("Tab")
      assert(await remove.evaluate(e => e === document.activeElement && e.matches(":focus-visible")))
      await shot(p, `header-long-keyboard-${width}`)
      await p.keyboard.press("Enter")
      await p.getByRole("dialog").waitFor()
      assert(await p.getByRole("dialog").getByRole("button", { name: "취소", exact: true }).evaluate(e => e === document.activeElement))
      await p.keyboard.press("Escape")
      assert(await remove.evaluate(e => e === document.activeElement))
      await ctx.close()
    }
  })
  assert.deepEqual(pageErrors, [])
} catch (e) {
  console.error(e)
  process.exitCode = 1
} finally {
  await fs.writeFile(
    path.join(output, "edit-delete-results.json"),
    JSON.stringify(
      {
        checks,
        pageErrors,
        passed: checks.length,
        failed: !!process.exitCode,
        mocked: true,
      },
      null,
      2,
    ),
  )
  await browser.close()
  await new Promise((resolve) => server.close(resolve))
}
