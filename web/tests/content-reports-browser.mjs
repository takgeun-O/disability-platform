// Controlled API fixtures, including SSR reads; never mutates a real backend.
import assert from "node:assert/strict"
import fs from "node:fs/promises"
import http from "node:http"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8467",
  out =
    process.env.REPORT_ARTIFACT_DIR ??
    "/private/tmp/iyum-report-hardening/browser"
await fs.mkdir(out, { recursive: true })
const stamp = "2026-11-13T01:00:00Z",
  admin = {
    memberId: 7,
    email: "review@example.test",
    nickname: "검증 관리자",
    role: "ADMIN",
  }
const post = {
  id: 71,
  type: "QUESTION",
  topic: "HEARING_AID",
  title: "보청기 적응 경험을 나눕니다",
  content:
    "새 보청기에 적응하며 느낀 경험입니다.\n서로의 경험을 존중해 주세요. 😀",
  author: { memberId: 8, nickname: "소리나무" },
  createdAt: stamp,
  updatedAt: stamp,
  likeCount: 0,
  liked: false,
}
const comment = (id) => ({
  commentId: id,
  content: "함께 경험을 나눠 주셔서 감사합니다.\n좋은 하루 되세요 😀",
  author: { memberId: id === 81 ? 7 : 8, nickname: "댓글 작성자" },
  createdAt: stamp,
  updatedAt: stamp,
  contentUpdatedAt: null,
  likeCount: 0,
  liked: false,
})
const comments = {
  items: [comment(81), comment(82)],
  hasNext: false,
  nextCursor: null,
}
const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "application/json" })
  res.end(JSON.stringify(req.url.includes("/comments") ? comments : post))
})
await new Promise((resolve, reject) => {
  server.once("error", reject)
  server.listen(18856, "127.0.0.1", resolve)
})
const browser = await chromium.launch({ channel: "chrome", headless: true }),
  contexts = [],
  results = []
const record = (id, type = "POST", missing = false) => ({
  reportId: id,
  reporterMemberId: 22,
  targetType: type,
  targetId: type === "POST" ? 71 : 82,
  status: "RECEIVED",
  reasonType: "OTHER",
  reasonDetail: "게시판 이용 목적과 맞지 않는 반복 게시입니다.",
  titleSnapshot: type === "POST" ? post.title : null,
  contentSnapshot:
    "신고 당시 본문입니다.\n다른 사람의 의견도 존중하는 공간이 되면 좋겠습니다. 😀",
  createdAt: stamp,
  currentTarget: missing
    ? null
    : {
        targetId: type === "POST" ? 71 : 82,
        parentPostId: type === "POST" ? null : 71,
        author: { ...post.author, withdrawn: false },
        title: type === "POST" ? post.title : null,
        content: post.content,
        hiddenAt: null,
        deletedAt: null,
      },
  handledByMemberId: null,
  handledAt: null,
  handlingReason: null,
  handlingOutcome: null,
})
// Mock backend derives the list field from current content, never from report status/outcome.
const targetStatus = (i) => !i.currentTarget ? null
  : i.currentTarget.deletedAt ? "DELETED"
    : i.currentTarget.hiddenAt ? "HIDDEN" : "NORMAL"
const page = (items, p = 0, total = items.length) => ({
  items,
  page: p,
  size: 20,
  totalElements: total,
  totalPages: Math.ceil(total / 20),
  hasNext: p + 1 < Math.ceil(total / 20),
  hasPrevious: p > 0,
})
const fail = (status, code) => ({
  status,
  json: { code, message: "synthetic failure", fieldErrors: [] },
})
const gate = () => {
  let release, arrive
  return {
    wait: new Promise((r) => (release = r)),
    arrived: new Promise((r) => (arrive = r)),
    release,
    arrive,
  }
}
const btn = (p, name) => p.getByRole("button", { name, exact: true })
const ready = (p) =>
  p.locator(
    '.report-list[aria-busy="false"] [data-report-select], .report-list[aria-busy="false"] table',
  )
    .first()
    .waitFor()
const detail = (p) => p.locator('.report-detail[aria-busy="false"]').waitFor()
const posts = (s) =>
  s.calls.filter((c) => c.method === "POST" && c.path.includes("/reports"))
async function setup(width = 1440) {
  const c = await browser.newContext({ viewport: { width, height: 900 } })
  contexts.push(c)
  // External font availability is not part of the API/UI contract. Use system
  // fallback fonts so page load never depends on the Google Fonts network.
  await c.route("https://fonts.googleapis.com/**", route => route.fulfill({ contentType: "text/css", body: "" }))
  const p = await c.newPage()
  p.setDefaultTimeout(10000)
  const s = {
    records: [record(11), record(12, "COMMENT"), record(13, "COMMENT", true)],
    calls: [],
    member: admin,
    list: null,
    detail: null,
    mutation: null,
    publicMutation: null,
    errors: [],
  }
  p.on("pageerror", (e) => s.errors.push(e.message))
  await p.addInitScript(() => {
    const original = fetch
    window.__reportsRead = []
    window.fetch = async (u, o) => {
      const r = await original(
        u,
        String(u).includes("/admin/reports") ? { ...o, signal: undefined } : o,
      )
      const json = r.json.bind(r)
      r.json = async () => {
        const v = await json()
        queueMicrotask(() => window.__reportsRead.push(String(u)))
        return v
      }
      return r
    }
  })
  await c.route("http://127.0.0.1:8082/**", async (r) => {
    const u = new URL(r.request().url()),
      method = r.request().method()
    s.calls.push({
      path: u.pathname + u.search,
      method,
      body: method === "POST" ? r.request().postDataJSON() : undefined,
    })
    if (u.pathname.endsWith("/auth/me"))
      return r.fulfill(
        s.member ? { json: s.member } : fail(401, "AUTHENTICATION_REQUIRED"),
      )
    if (u.pathname.endsWith("/csrf"))
      return r.fulfill({
        json: { headerName: "X-CSRF-TOKEN", token: "synthetic" },
      })
    if (u.pathname === "/api/v1/admin/reports") {
      if (s.list) return s.list(r, u)
      const items = s.records
        .filter(
          (i) =>
            (!u.searchParams.get("status") ||
              i.status === u.searchParams.get("status")) &&
            (u.searchParams.get("targetType") === "ALL" ||
              i.targetType === u.searchParams.get("targetType")) &&
            (!u.searchParams.get("reasonType") ||
              i.reasonType === u.searchParams.get("reasonType")),
        )
        .map((i) => ({
          ...i,
          contentSnapshotPreview: i.contentSnapshot,
          currentAuthor: i.currentTarget?.author ?? null,
          currentTargetStatus: targetStatus(i),
        }))
      return r.fulfill({
        json: page(items, Number(u.searchParams.get("page"))),
      })
    }
    if (u.pathname.startsWith("/api/v1/admin/reports/")) {
      const id = Number(u.pathname.split("/")[5]),
        value = s.records.find((i) => i.reportId === id)
      if (method === "POST") {
        assert(r.request().headers()["x-csrf-token"])
        assert.deepEqual(Object.keys(r.request().postDataJSON()), [
          "handlingReason",
        ])
        if (s.mutation) return s.mutation(r, u, value)
        Object.assign(value, {
          status: u.pathname.endsWith("/dismiss") ? "DISMISSED" : "RESOLVED",
          handlingOutcome: u.pathname.endsWith("/hide-and-resolve") ? (value.currentTarget.hiddenAt ? "CONTENT_ALREADY_HIDDEN" : "CONTENT_HIDDEN") : "NO_CONTENT_ACTION",
          handledByMemberId: 7,
          handledAt: "2026-11-13T02:00:00Z",
          handlingReason: r.request().postDataJSON().handlingReason,
        })
        if (u.pathname.endsWith("/hide-and-resolve")) value.currentTarget.hiddenAt ??= stamp
        return r.fulfill({
          json: {
            reportId: id,
            status: value.status,
            handledByMemberId: 7,
            handledAt: value.handledAt,
            handlingReason: value.handlingReason,
            handlingOutcome: value.handlingOutcome,
          },
        })
      }
      return s.detail
        ? s.detail(r, u)
        : r.fulfill(value ? { json: value } : fail(404, "REPORT_NOT_FOUND"))
    }
    if (u.pathname.includes("/community/posts")) {
      if (method === "POST" && u.pathname.endsWith("/reports"))
        return s.publicMutation
          ? s.publicMutation(r, u)
          : r.fulfill({
              status: 201,
              json: { reportId: 99, status: "RECEIVED", createdAt: stamp },
            })
      if (u.pathname.endsWith("/comments")) return r.fulfill({ json: comments })
      if (u.pathname.endsWith("/likes"))
        return r.fulfill({ json: { likeCount: 0, liked: false } })
      return r.fulfill({ json: post })
    }
    if (
      u.pathname === "/api/v1/admin/members" ||
      u.pathname === "/api/v1/admin/posts"
    )
      return r.fulfill({ json: page([]) })
    if (method === "POST" && /^\/api\/v1\/admin\/posts\/71(?:\/comments\/82)?\/(?:restore|hide)$/.test(u.pathname)) {
      const isComment = u.pathname.includes("/comments/"), hidden = u.pathname.endsWith("/hide")
      for (const value of s.records) if (value.targetType === (isComment ? "COMMENT" : "POST") && value.currentTarget) value.currentTarget.hiddenAt = hidden ? stamp : null
      return r.fulfill({ json: { postId: 71, ...(isComment ? { commentId: 82 } : {}), hidden, hiddenAt: hidden ? stamp : null, changed: true } })
    }
    if (u.pathname === "/api/v1/admin/comments/82") return r.fulfill({json:{
      ...comment(82),postId:71,postStatus:"VISIBLE",status:s.records[1].currentTarget?.hiddenAt ? "HIDDEN" : "VISIBLE",hiddenAt:s.records[1].currentTarget?.hiddenAt ?? null,deletedAt:null,
      author:{memberId:8,nickname:"댓글 작성자",withdrawn:false}, content:"첫 페이지 밖 신고 댓글의 전체 본문입니다."
    }})
    if (u.pathname === "/api/v1/admin/posts/71")
      return r.fulfill({
        json: {
          postId: 71,
          title: post.title,
          content: post.content,
          type: post.type,
          topic: post.topic,
          status: s.records[0].currentTarget?.hiddenAt ? "HIDDEN" : "VISIBLE",
          author: { ...post.author, withdrawn: false },
          createdAt: stamp,
          updatedAt: stamp,
          contentUpdatedAt: null,
          hiddenAt: s.records[0].currentTarget?.hiddenAt ?? null,
          deletedAt: null,
        },
      })
    if (u.pathname.endsWith("/comments"))
      return r.fulfill({
        json: { ...page([]), postId: 71, postStatus: "VISIBLE" },
      })
    return r.fulfill(fail(404, "NOT_FOUND"))
  })
  return { p, s, c }
}
async function run(name, fn) {
  if (
    process.env.TEST_FILTER &&
    !new RegExp(process.env.TEST_FILTER).test(name)
  )
    return
  try {
    await fn()
    results.push({ name, passed: true })
    console.log("PASS", name)
  } catch (e) {
    results.push({ name, passed: false, error: e.message })
    console.log("FAIL", name, e.message)
    for (const c of contexts)
      for (const p of c.pages())
        await p
          .screenshot({
            path: `${out}/failure-${results.length}.png`,
            fullPage: true,
          })
          .catch(() => {})
  } finally {
    await Promise.all(contexts.splice(0).map((c) => c.close()))
  }
}
async function openReport(p, target) {
  await btn(p, `${target} 더보기`).click()
  await p.getByRole("menuitem",{name:"신고",exact:true}).click()
  await p.getByRole("dialog").waitFor()
  assert.equal(await p.getByRole("menu").count(),0)
}
await run(
  "public post/comment report, own action absent, validation, payload and success",
  async () => {
    const { p, s } = await setup()
    await p.goto(base + "/community/posts/71")
    await openReport(p, "게시글")
    await btn(p, "신고하기").click()
    await p.getByText("신고 사유를 선택해 주세요.", { exact: false }).waitFor()
    assert.equal(posts(s).length, 0)
    await p.locator('input[type="radio"][value="OTHER"]').check()
    await btn(p, "신고하기").click()
    assert.equal(posts(s).length, 0)
    await p
      .getByLabel("상세 사유 (필수)")
      .fill("　함께 지켜야 할 이용 규칙을 확인해 주세요. 😀　")
    await p.screenshot({ path: out + "/public-report.png" })
    await btn(p, "신고하기").click()
    await p.getByText("신고가 접수되었습니다.", { exact: false }).waitFor()
    assert.deepEqual(posts(s)[0].body, {
      reasonType: "OTHER",
      reasonDetail: "함께 지켜야 할 이용 규칙을 확인해 주세요. 😀",
    })
    assert.equal(await p.getByRole("dialog").count(), 0)
    assert(await btn(p,"게시글 더보기").evaluate(e=>e===document.activeElement))
    assert.equal(await btn(p, "댓글 더보기").count(), 1)
    await openReport(p, "댓글")
    await p.locator('input[type="radio"][value="SPAM_ADVERTISING"]').check()
    await btn(p, "신고하기").click()
    await p.getByText("신고가 접수되었습니다.", { exact: false }).waitFor()
    await p.waitForFunction(()=>!document.querySelector('.content-report-dialog'))
    assert.equal(posts(s).length,2)
    assert(posts(s).at(-1).path.endsWith("/posts/71/comments/82/reports"))
    assert.deepEqual(posts(s).at(-1).body, { reasonType: "SPAM_ADVERTISING" })
    assert.deepEqual(s.errors, [])
  },
)
await run(
  "public anonymous, duplicate and uncertain errors do not invent success",
  async () => {
    const { p, s } = await setup(390)
    s.member = null
    await p.goto(base + "/community/posts/71")
    await openReport(p, "게시글")
    await p.getByRole("heading", { name: "로그인이 필요해요" }).waitFor()
    assert.equal(posts(s).length, 0)
    await btn(p, "그대로 머무르기").click()
    s.member = admin
    await p.reload()
    s.publicMutation = (r) => r.fulfill(fail(409, "REPORT_ALREADY_EXISTS"))
    await openReport(p, "게시글")
    await p.locator('input[type="radio"][value="OFF_TOPIC"]').check()
    await btn(p, "신고하기").click()
    await p.getByText("이미 신고한 콘텐츠입니다.", { exact: false }).waitFor()
    await btn(p, "닫기").click()
    s.publicMutation = (r) => r.abort()
    await openReport(p, "게시글")
    await p.locator('input[type="radio"][value="OFF_TOPIC"]').check()
    await btn(p, "신고하기").click()
    await p
      .getByText("처리 결과를 확인하지 못했습니다.", { exact: false })
      .waitFor()
    assert(await btn(p, "신고하기").isDisabled())
    const before = posts(s).length
    await p.locator('input[value="OTHER"]').check()
    await p.getByLabel("상세 사유 (필수)").fill("입력을 바꿔도 접수 결과는 여전히 알 수 없습니다.")
    assert(await btn(p, "신고하기").isDisabled())
    assert.equal(posts(s).length,before)
    assert.equal(await p.getByText("신고가 접수되었습니다.",{exact:false}).count(),0)
  },
)
await run(
  "admin filters, URL, selection, close focus and back-forward",
  async () => {
    const { p, s } = await setup(1920)
    await p.goto(base + "/admin/reports")
    await ready(p)
    assert(!new URL(s.calls.find(c => c.path.startsWith("/api/v1/admin/reports?")).path, base).searchParams.has("status"))
    await p.getByLabel("신고 상태", { exact: true }).selectOption("ALL")
    await p.waitForURL("**status=ALL*")
    await ready(p)
    assert(
      !s.calls
        .filter((c) => c.path.startsWith("/api/v1/admin/reports?"))
        .at(-1)
        .path.includes("status="),
    )
    await p.getByLabel("접수 시작일").fill("2026-11-01")
    await p.getByLabel("접수 종료일").fill("2026-11-13")
    await btn(p, "기간 적용").click()
    await ready(p)
    await p.getByLabel("접수일 정렬").selectOption("OLDEST")
    await ready(p)
    await p.locator('[data-report-select="11"]').click()
    await detail(p)
    assert(p.url().includes("selected=11"))
    await p.screenshot({ path: out + "/admin-detail-1920.png" })
    await btn(p, "상세 닫기").click()
    await ready(p)
    assert(!p.url().includes("selected"))
    assert(p.url().includes("startDate=2026-11-01"))
    assert(
      await p
        .locator('[data-report-select="11"]')
        .evaluate((e) => e === document.activeElement),
    )
    await p.goBack()
    await ready(p)
    await p.goForward()
    await ready(p)
    assert.equal(await p.locator(".report-detail").count(), 0)
    assert.deepEqual(s.errors, [])
  },
)
await run(
  "missing current target can resolve; invalid reason blocked; terminal detail retained",
  async () => {
    const { p, s } = await setup(1440)
    await p.goto(base + "/admin/reports?status=RECEIVED&selected=13")
    await ready(p)
    await detail(p)
    await p
      .getByText("현재 대상을 찾을 수 없습니다.", { exact: false })
      .waitFor()
    await btn(p, "추가 조치 없이 처리 완료").click()
    await btn(p, "처리 완료 확정").click()
    assert.equal(posts(s).length, 0)
    // Validation focuses after React renders the error in the next frame.
    await p.waitForFunction(() => document.activeElement?.matches('textarea[aria-invalid="true"]'))
    assert(
      await p
        .getByLabel("처리 사유 (필수)")
        .evaluate((e) => e === document.activeElement),
    )
    await p
      .getByLabel("처리 사유 (필수)")
      .fill(
        "내용을 확인하고 검토를 마쳤습니다.\n콘텐츠 조치와 별도로 처리합니다.",
      )
    await p.screenshot({ path: out + "/admin-handling-1440.png" })
    await btn(p, "처리 완료 확정").click()
    await p.locator(".report-toast .comment-success-toast").waitFor()
    await detail(p)
    await ready(p)
    assert.equal(posts(s).length, 1)
    assert.equal(await btn(p, "추가 조치 없이 처리 완료").count(), 0)
    assert(
      await p
        .getByText("처리가 완료되어 미처리 목록에서 제외됐습니다.")
        .isVisible(),
    )
    await p.screenshot({ path: out + "/admin-result.png" })
  },
)
await run(
  "success then read failure recovery uses GET only; conflict and uncertain guarded",
  async () => {
    const { p, s } = await setup()
    await p.goto(base + "/admin/reports?selected=11")
    await ready(p)
    await detail(p)
    await btn(p, "신고 기각").click()
    await p
      .getByLabel("처리 사유 (필수)")
      .fill("위반 사항을 확인하지 못했습니다.")
    s.list = (r) => r.fulfill(fail(503, "ERROR"))
    await btn(p, "기각 확정").click()
    await p
      .getByText("상세 정보는 갱신됐지만 목록을 갱신하지 못했습니다. 다시 조회해 주세요.", { exact: true })
      .waitFor()
    assert.equal(posts(s).length, 1)
    s.list = null
    await btn(p, "최신 정보 다시 조회").click()
    await detail(p)
    await ready(p)
    assert.equal(await btn(p, "최신 정보 다시 조회").count(), 0)
    assert.equal(posts(s).length, 1)
    await p.goto(base + "/admin/reports?selected=12")
    await detail(p)
    s.mutation = async (r, u, v) => {
      Object.assign(v, {
        status: "RESOLVED",
        handledByMemberId: 9,
        handledAt: stamp,
        handlingReason: "다른 관리자의 검토",
        handlingOutcome: "NO_CONTENT_ACTION",
      })
      return r.fulfill(fail(409, "REPORT_ALREADY_HANDLED"))
    }
    await btn(p, "추가 조치 없이 처리 완료").click()
    await p.getByLabel("처리 사유 (필수)").fill("검토")
    await btn(p, "처리 완료 확정").click()
    await p.getByText("다른 관리자의 검토", { exact: true }).waitFor()
    assert.equal(posts(s).length, 2)
  },
)
await run(
  "dirty navigation cancel preserves input and approval leaves once",
  async () => {
    const { p } = await setup()
    await p.goto(base + "/admin/reports?selected=11")
    await detail(p)
    await btn(p, "신고 기각").click()
    await p.getByLabel("처리 사유 (필수)").fill("작성 중인 검토 사유")
    await p.getByRole("link", { name: "회원 관리", exact: true }).click()
    await btn(p, "계속 작성").click()
    assert.equal(
      await p.getByLabel("처리 사유 (필수)").inputValue(),
      "작성 중인 검토 사유",
    )
    await p.getByRole("link", { name: "회원 관리", exact: true }).click()
    await btn(p, "입력 버리고 이동").click()
    await p.waitForURL("**/admin/members")
    await p.getByRole("heading", { name: "회원 관리", exact: true }).waitFor()
    assert.equal(new URL(p.url()).pathname, "/admin/members")
  },
)
await run("late detail cannot overwrite newly selected report", async () => {
  const { p, s } = await setup(1920),
    g = gate()
  s.detail = async (r, u) => {
    if (u.pathname.endsWith("/11")) {
      g.arrive()
      await g.wait
    }
    return r.fulfill({
      json: s.records.find(
        (i) => i.reportId === Number(u.pathname.split("/").at(-1)),
      ),
    })
  }
  await p.goto(base + "/admin/reports")
  await ready(p)
  await p.locator('[data-report-select="11"]').click()
  await Promise.race([
    g.arrived,
    new Promise((_, reject) =>
      setTimeout(() => reject(Error("delayed request not reached")), 10000),
    ),
  ])
  await ready(p)
  await p.locator('[data-report-select="12"]').click()
  await detail(p)
  g.release()
  await p.waitForFunction(() =>
    window.__reportsRead.some((u) => u.endsWith("/reports/11")),
  )
  assert(
    await p
      .getByRole("heading", { name: "신고 #12 상세", exact: true })
      .isVisible(),
  )
  assert(p.url().includes("selected=12"))
})
await run(
  "original and management return preserve report selection",
  async () => {
    const { p } = await setup()
    await p.goto(base + "/admin/reports?status=ALL&sort=OLDEST&selected=11")
    await detail(p)
    await p.getByRole("link", { name: "원문 보기", exact: true }).click()
    await p.waitForURL("**/community/posts/71")
    await p.goBack()
    await detail(p)
    assert(p.url().includes("selected=11"))
    await p.getByRole("link", { name: "게시글 관리로 이동" }).click()
    await p.waitForURL("**/admin/posts/71?*")
    await p.getByRole("link", { name: "← 신고 관리로" }).click()
    await detail(p)
    assert(p.url().includes("sort=OLDEST"))
    assert(p.url().includes("selected=11"))
  },
)
for (const width of [1440, 320])
  await run(`layout and keyboard ${width}`, async () => {
    const { p, s } = await setup(width)
    s.records[0].contentSnapshot =
      "긴 내용도 끝까지 읽을 수 있어야 합니다.\n줄바꿈과 이모지 😀를 보존합니다.\n".repeat(
        40,
      ) + "본문 마지막 구절"
    await p.goto(base + "/admin/reports?selected=11")
    await detail(p)
    await btn(p, "신고 기각").click()
    await p.getByLabel("처리 사유 (필수)").fill("검토 내용을 기록합니다.")
    await p.getByLabel("처리 사유 (필수)").press("Tab")
    await p.keyboard.press("Tab")
    assert(
      await btn(p, "기각 확정").evaluate((e) => e === document.activeElement),
    )
    const b = await btn(p, "기각 확정").boundingBox()
    assert(b.y >= 0 && b.y + b.height <= 900)
    assert(
      await p.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
    )
    await p.screenshot({ path: `${out}/handling-${width}.png` })
    if (width === 320) {
      await btn(p, "목록으로 돌아가기").click()
      await btn(p, "선택한 상세 보기").click()
      assert.equal(
        await p.getByLabel("처리 사유 (필수)").inputValue(),
        "검토 내용을 기록합니다.",
      )
    }
    assert.deepEqual(s.errors, [])
  })
await run("authorization rejection removes report data", async () => {
  const { p, s } = await setup()
  s.detail = (r) => r.fulfill(fail(403, "ACCESS_DENIED"))
  await p.goto(base + "/admin/reports?selected=11")
  await p.getByRole("heading", { name: "관리자 권한이 없습니다" }).waitFor()
  assert.equal(await p.locator(".report-list").count(), 0)
  assert.equal(await p.locator(".report-detail").count(), 0)
})
await run(
  "public 401 expires session while CSRF error preserves authenticated input",
  async () => {
    const { p, s } = await setup()
    await p.goto(base + "/community/posts/71")
    await openReport(p, "게시글")
    await p.locator('input[type="radio"][value="OTHER"]').check()
    await p.getByLabel("상세 사유 (필수)").fill("확인할 내용")
    s.publicMutation = (r) => r.fulfill(fail(403, "CSRF_TOKEN_INVALID"))
    await btn(p, "신고하기").click()
    await p.getByText("보안 확인에 실패했습니다.", { exact: false }).waitFor()
    assert.equal(
      await p.getByLabel("상세 사유 (필수)").inputValue(),
      "확인할 내용",
    )
    s.publicMutation = (r) => r.fulfill(fail(401, "AUTHENTICATION_REQUIRED"))
    await btn(p, "신고하기").click()
    await p
      .getByRole("heading", { name: "로그인이 필요해요", exact: true })
      .waitFor()
    assert.equal(posts(s).length, 2)
  },
)
await run(
  "busy blocks duplicate and departure; server field error focuses input",
  async () => {
    const { p, s } = await setup()
    await p.goto(base + "/admin/reports?selected=11")
    await detail(p)
    await btn(p, "추가 조치 없이 처리 완료").click()
    await p.getByLabel("처리 사유 (필수)").fill("검토 사유")
    s.mutation = (r) => r.fulfill(fail(400, "INVALID_HANDLING_REASON"))
    await btn(p, "처리 완료 확정").click()
    await p.locator(".report-error").waitFor()
    assert(
      await p
        .getByLabel("처리 사유 (필수)")
        .evaluate((e) => e === document.activeElement),
    )
    assert.equal(
      await p.getByLabel("처리 사유 (필수)").inputValue(),
      "검토 사유",
    )
    await p.getByLabel("처리 사유 (필수)").fill("수정한 검토 사유")
    const g = gate()
    s.mutation = async (r, u, v) => {
      g.arrive()
      await g.wait
      Object.assign(v, {
        status: "RESOLVED",
        handledByMemberId: 7,
        handledAt: stamp,
        handlingReason: "수정한 검토 사유",
        handlingOutcome: "NO_CONTENT_ACTION",
      })
      return r.fulfill({ json: v })
    }
    await btn(p, "처리 완료 확정").click()
    await g.arrived
    assert(await btn(p, "처리 중…").isDisabled())
    await p.getByRole("link", { name: "회원 관리", exact: true }).click()
    await p.getByRole("heading", { name: "처리 중입니다" }).waitFor()
    assert.equal(await btn(p, "입력 버리고 이동").count(), 0)
    await btn(p, "머무르기").click()
    g.release()
    await p.locator(".report-toast .comment-success-toast").waitFor()
    assert.equal(posts(s).length, 2)
  },
)
await run(
  "last page correction preserves processed selection and current filters",
  async () => {
    const { p, s } = await setup()
    s.records = Array.from({ length: 21 }, (_, i) => record(100 + i))
    s.list = (r, u) => {
      const items = s.records.filter((i) => i.status === "RECEIVED"),
        n = Number(u.searchParams.get("page"))
      return r.fulfill({
        json: page(
          items.slice(n * 20, n * 20 + 20).map((i) => ({
            ...i,
            contentSnapshotPreview: i.contentSnapshot,
            currentAuthor: i.currentTarget.author,
            currentTargetStatus: targetStatus(i),
          })),
          n,
          items.length,
        ),
      })
    }
    await p.goto(
      base + "/admin/reports?status=RECEIVED&sort=OLDEST&page=1&selected=120",
    )
    await detail(p)
    await btn(p, "신고 기각").click()
    await p
      .getByLabel("처리 사유 (필수)")
      .fill("검토 결과 위반 사항이 없습니다.")
    await btn(p, "기각 확정").click()
    await p.waitForURL("**page=0*")
    await detail(p)
    await ready(p)
    assert(p.url().includes("selected=120"))
    assert(p.url().includes("sort=OLDEST"))
    await p
      .getByText("처리가 완료되어 미처리 목록에서 제외됐습니다.")
      .waitFor()
    assert.equal(posts(s).length, 1)
  },
)
await run(
  "late list response cannot overwrite a newer status filter",
  async () => {
    const { p, s } = await setup(1920),
      g = gate()
    s.records.push({ ...record(99), status: "DISMISSED" })
    s.list = async (r, u) => {
      const received = u.searchParams.get("status") === "RECEIVED"
      const items = s.records
        .filter((i) => !received || i.status === "RECEIVED")
        .map((i) => ({
          ...i,
          contentSnapshotPreview: i.contentSnapshot,
          currentAuthor: i.currentTarget?.author ?? null,
          currentTargetStatus: targetStatus(i),
        }))
      if (received) {
        g.arrive()
        await g.wait
      }
      return r.fulfill({ json: page(items) })
    }
    await p.goto(base + "/admin/reports?status=RECEIVED")
    await g.arrived
    await p.getByLabel("신고 상태", { exact: true }).selectOption("ALL")
    await p.waitForURL("**status=ALL*")
    await ready(p)
    await p.locator('[data-report-select="99"]').waitFor()
    g.release()
    await p.waitForFunction(() =>
      window.__reportsRead.some((u) => u.includes("status=RECEIVED")),
    )
    assert(await p.locator('[data-report-select="99"]').isVisible())
    assert.equal(
      await p.getByLabel("신고 상태", { exact: true }).inputValue(),
      "ALL",
    )
  },
)
await run("twenty rows retain list position when opening last report", async () => {
  const {p,s} = await setup(1920)
  s.records = Array.from({length:20},(_,i)=>record(200+i))
  await p.goto(base+"/admin/reports")
  await ready(p)
  const row=p.locator('[data-report-select="219"]')
  await row.scrollIntoViewIfNeeded()
  const before=await p.evaluate(()=>scrollY)
  assert(before > 300)
  await row.click()
  await detail(p); await ready(p)
  const heading=p.getByRole("heading",{name:"신고 #219 상세",exact:true})
  await p.waitForFunction(()=>{const h=document.querySelector('.admin-preview-heading');if(!h)return false;const r=h.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})
  const after=await p.evaluate(()=>scrollY)
  assert(Math.abs(after-before)<3)
  assert(await heading.isVisible())
  await p.screenshot({path:out+"/bottom-row-selection.png"})
})
await run("identical navigation keeps handling scope live", async()=>{
  const {p,s}=await setup()
  await p.goto(base+"/admin/reports?targetType=ALL&sort=LATEST&page=0&status=RECEIVED");await ready(p)
  await btn(p,"기간 적용").click()
  await p.locator('[data-report-select="11"]').click();await detail(p);await ready(p)
  const href=p.url(), n=await p.evaluate(()=>history.length)
  await p.locator('[data-report-select="11"]').click()
  assert.equal(p.url(),href);assert.equal(await p.evaluate(()=>history.length),n)
  await btn(p,"추가 조치 없이 처리 완료").click()
  await p.getByLabel("처리 사유 (필수)").fill("작성자가 수정하여 추가 조치 없이 종결합니다.")
  await btn(p,"처리 완료 확정").click()
  await p.locator(".report-toast .comment-success-toast").waitFor()
  assert.equal(posts(s).length,1)
})
for(const width of [1440,320]) await run(`direct report exit dialog centered ${width}`,async()=>{
  const {p}=await setup(width)
  await p.goto(base+"/admin/reports?selected=11");await detail(p)
  await btn(p,"신고 기각").click();await p.getByLabel("처리 사유 (필수)").fill("검토 중인 사유")
  await p.getByRole("link",{name:"회원 관리",exact:true}).click()
  const d=p.getByRole("dialog");await d.waitFor()
  const b=await d.boundingBox(),v=p.viewportSize()
  assert(Math.abs(b.x+b.width/2-v.width/2)<2)
  assert(Math.abs(b.y+b.height/2-v.height/2)<2)
  assert(b.x>=0&&b.y>=0&&b.y+b.height<=v.height)
  await p.screenshot({path:out+`/exit-${width}.png`})
  await btn(p,"계속 작성").click()
  assert.equal(await p.getByLabel("처리 사유 (필수)").inputValue(),"검토 중인 사유")
})
await run("reported comment direct lookup does not depend on first list page",async()=>{
  const {p,s}=await setup()
  await p.goto(base+"/admin/reports?targetType=ALL&sort=LATEST&page=0&status=RECEIVED&selected=12");await detail(p)
  const original=p.url()
  await p.getByRole("link",{name:"신고된 댓글 관리로 이동"}).click()
  await p.getByRole("heading",{name:"댓글 #82 관리",exact:true}).waitFor()
  await p.getByText("첫 페이지 밖 신고 댓글의 전체 본문입니다.").waitFor()
  assert(s.calls.some(c=>c.path==="/api/v1/admin/comments/82"))
  assert(!s.calls.some(c=>c.path.startsWith("/api/v1/admin/posts/71/comments?")))
  await p.screenshot({path:out+"/exact-comment.png"})
  await p.getByRole("link",{name:"← 신고 #12로 돌아가기"}).click();await detail(p)
  assert.equal(p.url(),original)
})
for(const width of [1440,320,390]) await run(`report menu and radio keyboard ${width}`,async()=>{
  const {p}=await setup(width)
  await p.goto(base+"/community/posts/71")
  await btn(p,"게시글 더보기").focus();await p.keyboard.press("ArrowDown")
  await p.getByRole("menuitem",{name:"신고",exact:true}).waitFor()
  await p.screenshot({path:out+`/menu-${width}.png`})
  await p.keyboard.press("Enter")
  const radios=p.getByRole("radio");assert.equal(await radios.count(),6)
  await p.keyboard.press("ArrowDown");assert.equal(await p.locator('input[type="radio"]:checked').count(),1)
  await p.screenshot({path:out+`/report-form-${width}.png`})
  await btn(p,"취소").click()
  assert(await btn(p,"게시글 더보기").evaluate(e=>e===document.activeElement))
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
})

for (const [width, id, already] of [[1920, 11, false], [1440, 12, false], [390, 12, true], [320, 11, false]]) {
  await run(`atomic hide outcome and accessible form ${width} ${id} ${already}`, async () => {
    const { p, s } = await setup(width)
    const item = s.records.find(r => r.reportId === id)
    if (already) item.currentTarget.hiddenAt = stamp
    await p.goto(base + `/admin/reports?selected=${id}`)
    await detail(p); await p.locator('.report-list[aria-busy="false"]').waitFor({ state: "attached" })
    await btn(p, "숨김·처리 완료").focus()
    await p.screenshot({ path: out + `/actions-${width}.png` })
    await p.keyboard.press("Enter")
    const field = p.getByLabel("처리 사유 (필수)")
    await field.fill("　정책 위반을 확인했습니다.\n신고한 콘텐츠만 처리합니다.　")
    assert(await p.getByText(`처리 대상: ${id === 11 ? "게시글 #71" : "댓글 #82"}`, { exact: true }).count())
    // Keyboard navigation, without click auto-scroll or a forced click, reaches final action.
    await field.focus(); await p.keyboard.press("Tab"); await p.keyboard.press("Tab")
    const submit = btn(p, "숨김·처리 완료 확정")
    assert(await submit.evaluate(e => e === document.activeElement))
    assert(await submit.evaluate(e => {
      const r = e.getBoundingClientRect(), hit = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
      return r.top >= 0 && r.bottom <= innerHeight && (hit === e || e.contains(hit))
    }))
    assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await p.screenshot({ path: out + `/hide-form-${width}.png` })
    await p.keyboard.press("Enter")
    const text = already ? "처리 결과: 기존 숨김 유지" : "처리 결과: 콘텐츠 숨김"
    await p.getByText(text, { exact: true }).waitFor()
    await detail(p); await p.locator('.report-list[aria-busy="false"]').waitFor({ state: "attached" })
    assert.equal(posts(s).length, 1)
    assert.equal(posts(s)[0].path, `/api/v1/admin/reports/${id}/hide-and-resolve`)
    assert.deepEqual(posts(s)[0].body, { handlingReason: "정책 위반을 확인했습니다.\n신고한 콘텐츠만 처리합니다." })
    await p.getByText(text, { exact: true }).scrollIntoViewIfNeeded()
    await p.screenshot({ path: out + `/outcome-${width}.png` })
    assert.equal(await btn(p, "숨김·처리 완료").count(), 0)
    // Later restoration must not rewrite the stored handling result.
    item.currentTarget.hiddenAt = null
    await p.reload(); await detail(p)
    assert.equal(await p.getByText(text, { exact: true }).count(), 1)
    assert.equal(await p.locator(".report-current-heading .report-content-badge").getByText("정상", { exact: true }).count(), 1)
    assert.deepEqual(s.errors, [])
  })
}
await run("missing/deleted hide disabled and legacy result remains distinct", async () => {
  const { p, s } = await setup()
  for (const id of [13, 11]) {
    if (id === 11) s.records[0].currentTarget.deletedAt = stamp
    await p.goto(base + `/admin/reports?selected=${id}`); await detail(p)
    assert(await btn(p, "숨김·처리 완료").isDisabled())
    assert(await btn(p, "추가 조치 없이 처리 완료").isEnabled())
    assert(await btn(p, "신고 기각").isEnabled())
  }
  Object.assign(s.records[0], { status: "RESOLVED", handledByMemberId: 9, handledAt: stamp, handlingReason: "과거 검토 기록", handlingOutcome: "LEGACY_UNKNOWN" })
  await p.reload(); await detail(p)
  const result = p.getByText("처리 결과: 과거 기록 · 조치 여부 확인 불가", { exact: true })
  await result.scrollIntoViewIfNeeded()
  await p.screenshot({ path: out + "/legacy-outcome.png" })
  assert.equal(posts(s).length, 0)
})
for (const code of ["POST_DELETED", "COMMENT_DELETED", "POST_NOT_FOUND", "COMMENT_NOT_FOUND"]) {
  await run(`hide server target error ${code} preserves reason and only rereads`, async () => {
    const { p, s } = await setup()
    await p.goto(base + "/admin/reports?selected=12"); await detail(p)
    await btn(p, "숨김·처리 완료").click()
    await p.getByLabel("처리 사유 (필수)").fill("복구 가능한 검토 사유")
    s.mutation = r => {
      if (code === "COMMENT_DELETED") s.records[1].currentTarget.deletedAt = stamp
      if (code === "COMMENT_NOT_FOUND") s.records[1].currentTarget = null
      return r.fulfill(fail(code.endsWith("DELETED") ? 409 : 404, code))
    }
    const before = s.calls.length
    await btn(p, "숨김·처리 완료 확정").click()
    const message = code === "POST_DELETED" ? "대상 게시글 또는 댓글의 부모 게시글이 삭제되어 숨길 수 없습니다." : code === "COMMENT_DELETED" ? "대상 댓글이 삭제되어 숨길 수 없습니다." : code === "POST_NOT_FOUND" ? "대상 게시글 또는 댓글의 부모 게시글을 찾을 수 없습니다." : "대상 댓글을 찾을 수 없습니다."
    await p.getByText(message, { exact: true }).waitFor()
    await detail(p)
    if (code.startsWith("COMMENT")) assert(await btn(p, "숨김·처리 완료 확정").isDisabled())
    assert.equal(posts(s).length, 1)
    assert(s.calls.slice(before).some(c => c.method === "GET" && c.path === "/api/v1/admin/reports/12"))
    assert.equal(await p.getByLabel("처리 사유 (필수)").inputValue(), "복구 가능한 검토 사유")
    assert.equal(await p.locator(".report-toast .comment-success-toast").count(), 0)
  })
}

for (const width of [1440, 390]) await run(`상세와 목록 부분 실패를 구분하고 GET만으로 복구한다 ${width}`, async () => {
  const { p, s } = await setup(width)
  await p.goto(base + "/admin/reports?selected=11"); await detail(p)
  await btn(p, "숨김·처리 완료").click()
  await p.getByLabel("처리 사유 (필수)").fill("검토 완료")
  s.list = r => r.fulfill(fail(503, "ERROR"))
  s.detail = r => r.fulfill(fail(503, "ERROR"))
  await btn(p, "숨김·처리 완료 확정").click()
  await p.getByText("조치는 완료됐습니다. 최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.", { exact: true }).waitFor()
  assert.equal(await p.getByText("처리 결과: 콘텐츠 숨김", { exact:true }).count(), 1)
  assert.equal(await p.getByText("최신 조회 전 정보입니다. 처리 후 현재 콘텐츠 상태를 다시 확인해 주세요.", { exact:true }).count(), 1)
  const badges = () => p.locator(".report-status-summary .report-content-badge, .report-current-heading .report-content-badge")
  assert.deepEqual(await badges().allTextContents(), ["정상", "정상"])
  assert.equal(await p.locator(".report-status-freshness").count(), 1)
  s.detail = null
  await btn(p, "최신 정보 다시 조회").click(); await detail(p)
  assert.deepEqual(await badges().allTextContents(), ["숨김", "숨김"])
  await p.getByText("상세 정보는 갱신됐지만 목록을 갱신하지 못했습니다. 다시 조회해 주세요.", { exact: true }).waitFor()
  assert.equal(await p.locator(".report-status-freshness").count(), 0)
  assert.equal(await p.getByText("최신 조회 전 정보입니다. 처리 후 현재 콘텐츠 상태를 다시 확인해 주세요.", { exact: true }).count(), 0)
  assert.equal(await p.getByText("상세 정보는 갱신됐습니다. 목록과 상세가 일시적으로 다를 수 있습니다.", { exact: true }).count(), 1)
  await p.locator(".admin-preview-body").evaluate(e => { e.scrollTop = 0 })
  await p.screenshot({ path: `${out}/refresh-list-only-failed-${width}.png` })
  // A later detail failure must invalidate the previous successful read's freshness.
  s.detail = r => r.fulfill(fail(503, "ERROR"))
  s.list = null
  s.records[0].currentTarget.hiddenAt = null
  await btn(p, "최신 정보 다시 조회").click(); await detail(p)
  assert.deepEqual(await badges().allTextContents(), ["숨김", "숨김"])
  assert.equal(await p.locator(".report-status-freshness").count(), 1)
  assert.equal(await p.getByText("최신 조회 전 정보입니다. 처리 후 현재 콘텐츠 상태를 다시 확인해 주세요.", { exact: true }).count(), 1)
  const listRow = p.locator(".report-list tr").filter({ has: p.locator('[data-report-select="11"]') })
  assert.equal(await listRow.locator(".report-content-badge").innerText(), "정상")
  await p.locator(".admin-preview-body").evaluate(e => { e.scrollTop = 0 })
  await p.screenshot({ path: `${out}/refresh-detail-failed-${width}.png` })
  s.detail = null
  assert.equal(await btn(p, "최신 정보 다시 조회").count(), 1)
  assert.equal(posts(s).length, 1)
  s.list = null
  await btn(p, "최신 정보 다시 조회").click(); await detail(p); await p.locator('.report-list[aria-busy="false"]').waitFor({ state: "attached" })
  await p.getByText("처리 결과: 콘텐츠 숨김", { exact: true }).waitFor()
  assert.deepEqual(await badges().allTextContents(), ["정상", "정상"])
  assert.equal(await p.locator(".report-status-freshness").count(), 0)
  assert.equal(await p.getByText("상세 정보는 갱신됐지만 목록을 갱신하지 못했습니다. 다시 조회해 주세요.", { exact: true }).count(), 0)
  await p.locator(".admin-preview-body").evaluate(e => { e.scrollTop = 0 })
  await p.screenshot({ path: `${out}/refresh-recovered-${width}.png` })
  assert.equal(await btn(p, "최신 정보 다시 조회").count(), 0)
  assert.equal(await p.getByText("조치는 완료됐습니다. 최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.", { exact: true }).count(), 0)
  assert.equal(posts(s).length, 1)
})
await run("hide uncertain response rereads without inventing success or losing reason", async () => {
  const { p, s } = await setup()
  await p.goto(base + "/admin/reports?selected=11"); await detail(p)
  await btn(p, "숨김·처리 완료").click()
  await p.getByLabel("처리 사유 (필수)").fill("보존할 사유")
  s.mutation = r => r.abort("failed")
  await btn(p, "숨김·처리 완료 확정").click()
  await btn(p, "최신 정보 다시 조회").waitFor()
  assert(await btn(p, "숨김·처리 완료 확정").isDisabled())
  await btn(p, "최신 정보 다시 조회").click(); await detail(p)
  assert.equal(await p.getByLabel("처리 사유 (필수)").inputValue(), "보존할 사유")
  assert.equal(posts(s).length, 1)
  assert.equal(await p.locator(".report-toast .comment-success-toast").count(), 0)
  assert.equal(await p.getByText("이번 요청의 처리 결과는 확실하지 않습니다. 다시 조회한 현재 상태와 저장된 처리 이력을 확인해 주세요.", { exact: true }).count(), 1)
})
await run("hide pending request blocks repeat submission and report switch; 401 clears data", async () => {
  const { p, s } = await setup(1920), g = gate()
  await p.goto(base + "/admin/reports?selected=11"); await detail(p); await ready(p)
  await btn(p, "숨김·처리 완료").click()
  await p.getByLabel("처리 사유 (필수)").fill("검토 사유")
  s.mutation = async r => { g.arrive(); await g.wait; return r.fulfill(fail(401, "AUTHENTICATION_REQUIRED")) }
  await btn(p, "숨김·처리 완료 확정").click(); await g.arrived
  assert(await btn(p, "처리 중…").isDisabled())
  await p.keyboard.press("Enter")
  await p.locator('[data-report-select="12"]').click()
  await p.getByRole("heading", { name: "처리 중입니다" }).waitFor()
  assert(p.url().includes("selected=11")); assert.equal(posts(s).length, 1)
  await btn(p, "머무르기").click(); g.release()
  await p.waitForFunction(() => !document.querySelector(".report-detail"))
  assert.equal(posts(s).length, 1)
})

await run("default/reset all and explicit status URL history", async () => {
  const { p, s } = await setup()
  await p.goto(base + "/admin/reports"); await ready(p)
  assert.equal(await p.getByLabel("신고 상태", { exact: true }).inputValue(), "ALL")
  assert(!new URL(s.calls.find(c => c.path.startsWith("/api/v1/admin/reports?")).path, base).searchParams.has("status"))
  for (const status of ["RECEIVED", "RESOLVED", "DISMISSED"]) {
    await p.getByLabel("신고 상태", { exact: true }).selectOption(status)
    await p.waitForURL(`**status=${status}*`); await ready(p)
    await p.reload(); await ready(p)
    assert.equal(await p.getByLabel("신고 상태", { exact: true }).inputValue(), status)
  }
  await btn(p, "전체 초기화").click(); await p.waitForURL("**status=ALL*"); await ready(p)
  await p.goBack(); await ready(p)
  assert.equal(await p.getByLabel("신고 상태", { exact: true }).inputValue(), "DISMISSED")
  await p.goForward(); await ready(p)
  assert.equal(await p.getByLabel("신고 상태", { exact: true }).inputValue(), "ALL")
})
for (const [action, already, width] of [["hide-and-resolve", false, 1920], ["hide-and-resolve", true, 390], ["resolve", false, 1440], ["dismiss", false, 320]]) {
  await run(`UX all retains row, fixed toast and saved summary ${action} ${already} ${width}`, async () => {
    const { p, s } = await setup(width)
    await p.clock.install()
    if (already) s.records[1].currentTarget.hiddenAt = stamp
    await p.goto(base + "/admin/reports?sort=OLDEST&selected=12"); await detail(p)
    const order = await p.locator("[data-report-select]").evaluateAll(es => es.map(e => e.dataset.reportSelect))
    const actionLabel = action === "hide-and-resolve" ? "숨김·처리 완료" : action === "resolve" ? "추가 조치 없이 처리 완료" : "신고 기각"
    const confirm = action === "hide-and-resolve" ? "숨김·처리 완료 확정" : action === "resolve" ? "처리 완료 확정" : "기각 확정"
    await btn(p, actionLabel).click()
    const field = p.getByLabel("처리 사유 (필수)")
    await field.fill("확인한 위반 사항에 대한 검토 결과입니다.")
    await field.focus(); await p.keyboard.press("Tab"); await p.keyboard.press("Tab")
    assert(await btn(p, confirm).evaluate(e => e === document.activeElement))
    await p.keyboard.press("Enter"); await detail(p)
    const toast = p.locator(".report-toast .comment-success-toast")
    await toast.waitFor()
    const expected = `신고 #12 ${action === "dismiss" ? "기각 완료" : "처리 완료"} · ${action === "hide-and-resolve" ? already ? "댓글 #82의 기존 숨김을 유지했습니다." : "댓글 #82을 숨겼습니다." : "콘텐츠 공개 상태는 변경하지 않았습니다."}`
    assert.equal(await toast.innerText(), expected)
    assert.equal(await toast.count(), 1)
    assert(await toast.evaluate(e => { const r=e.getBoundingClientRect(); return r.top>=0 && r.bottom<=innerHeight && r.left>=0 && r.right<=innerWidth && getComputedStyle(e).pointerEvents==="none" && !e.contains(document.activeElement) }))
    assert.equal(await p.locator('.report-toast[role="status"][aria-atomic="true"]').count(), 1)
    await p.locator(".report-handling-summary").waitFor()
    assert.deepEqual(await p.locator("[data-report-select]").evaluateAll(es => es.map(e => e.dataset.reportSelect)), order)
    const row = p.locator('tr[data-selected="true"]')
    assert.equal(await row.getAttribute("data-status"), action === "dismiss" ? "DISMISSED" : "RESOLVED")
    assert.equal(await row.locator("td").count(), 5)
    assert.equal(await p.locator(".report-filter-notice").count(), 0)
    assert.equal(posts(s).length, 1)
    assert(!s.calls.some(c => c.method === "GET" && /^\/api\/v1\/admin\/reports\/(11|13)$/.test(c.path)))
    await p.screenshot({ path: out + `/ux-success-${width}.png` })
    await p.clock.runFor(5300)
    await toast.waitFor({ state: "detached" })
    assert.equal(await p.locator(".report-handling-summary").count(), 1)
    if (width < 1201) {
      await btn(p, "목록으로 돌아가기").click()
      await row.waitFor()
    }
    assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await p.screenshot({ path: out + `/ux-all-list-${width}.png` })
    assert.equal(posts(s).length, 1)
  })
}
for (const action of ["resolve", "dismiss"]) {
  await run(`UX received exclusion and explicit destination ${action}`, async () => {
    const { p, s } = await setup()
    await p.goto(base + "/admin/reports?targetType=COMMENT&reasonType=OTHER&status=RECEIVED&sort=OLDEST&startDate=2026-11-01&endDate=2026-11-16&selected=12")
    await detail(p); await ready(p)
    await btn(p, action === "resolve" ? "추가 조치 없이 처리 완료" : "신고 기각").click()
    await p.getByLabel("처리 사유 (필수)").fill("검토 결과")
    await btn(p, action === "resolve" ? "처리 완료 확정" : "기각 확정").click()
    await p.locator(".report-filter-notice").waitFor(); await ready(p)
    assert.equal(await p.locator('[data-report-select="12"]').count(), 0)
    assert.equal(new URL(p.url()).searchParams.get("status"), "RECEIVED")
    await p.locator(".report-filter-notice").scrollIntoViewIfNeeded()
    await p.screenshot({ path: out + `/ux-filter-exclusion-${action}.png` })
    await btn(p, action === "resolve" ? "처리 완료 목록 보기" : "기각 목록 보기").click()
    await detail(p); await ready(p)
    const q = new URL(p.url()).searchParams
    assert.equal(q.get("status"), action === "resolve" ? "RESOLVED" : "DISMISSED")
    for (const [key, value] of Object.entries({ targetType:"COMMENT", reasonType:"OTHER", sort:"OLDEST", startDate:"2026-11-01", endDate:"2026-11-16", page:"0", selected:"12" })) assert.equal(q.get(key), value)
    assert.equal(await p.locator('[data-report-select="12"][aria-pressed="true"]').count(), 1)
    assert.equal(posts(s).length, 1)
  })
}
for (const id of [11, 12]) {
  await run(`UX exact content restore and report return ${id}`, async () => {
    const { p, s } = await setup()
    const item = s.records.find(r => r.reportId === id)
    Object.assign(item, { status:"RESOLVED", handlingOutcome:"CONTENT_HIDDEN", handledAt:stamp, handledByMemberId:7, handlingReason:"숨김 당시 사유" })
    item.currentTarget.hiddenAt = stamp
    await p.goto(base + `/admin/reports?targetType=ALL&status=RESOLVED&sort=OLDEST&page=0&selected=${id}`); await detail(p)
    const original = p.url()
    const link = p.getByRole("link", { name: id === 11 ? "게시글 관리로 이동" : "신고된 댓글 관리로 이동", exact:true })
    await link.scrollIntoViewIfNeeded()
    assert.equal(await p.getByText("숨김 해제는 콘텐츠 관리 화면에서 할 수 있습니다.", { exact:true }).count(), 1)
    await p.screenshot({ path: out + `/ux-management-${id}.png` })
    await link.click()
    await btn(p, id === 11 ? "게시글 숨김 해제" : "댓글 #82 숨김 해제").click()
    await p.getByLabel("처리 사유 (필수)", { exact:true }).selectOption("OTHER")
    await p.getByLabel("상세 사유", { exact:false }).fill("재검토 후 복원")
    await btn(p, "숨김 해제 실행").click()
    await btn(p, id === 11 ? "게시글 숨김 처리" : "댓글 #82 숨김 처리").waitFor()
    if (id === 12) assert(await p.getByText("댓글 숨김을 해제했습니다. 기존 신고 처리 이력은 유지됩니다.",{exact:true}).count()>0)
    await p.getByRole("link", { name:id === 12 ? "← 신고 #12로 돌아가기" : "← 신고 관리로", exact:true }).click(); await detail(p)
    assert.equal(new URL(p.url()).pathname, new URL(original).pathname)
    assert.deepEqual(Object.fromEntries(new URL(p.url()).searchParams), Object.fromEntries(new URL(original).searchParams))
    assert.equal(await p.locator(".report-current-heading .report-content-badge").getByText("정상", { exact:true }).count(), 1)
    assert.equal(await p.getByText("처리 결과: 콘텐츠 숨김", { exact:true }).count(), 1)
    assert.equal(await p.getByText("숨김 해제는 콘텐츠 관리 화면에서 할 수 있습니다.", { exact:true }).count(), 0)
    assert.equal(posts(s).length, 0)
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.endsWith("/restore")).length, 1)
  })
}

for (const width of [1440, 390]) {
  await run(`UX mixed row status, hover and selected keyboard emphasis ${width}`, async () => {
    const { p, s } = await setup(width)
    for (const [i, status] of [[0, "RESOLVED"], [1, "DISMISSED"]]) Object.assign(s.records[i], {
      status, handledByMemberId: 7, handledAt: stamp, handlingReason: "검토 결과", handlingOutcome: "NO_CONTENT_ACTION",
    })
    await p.goto(base + "/admin/reports"); await ready(p)
    const rows = p.locator(".report-list tbody tr")
    assert.equal(await rows.count(), 3)
    for (const [i, label] of [[0, "처리 완료"], [1, "기각"], [2, "접수"]]) {
      assert.equal(await rows.nth(i).locator("td").count(), 5)
      assert.equal(await rows.nth(i).locator('td[data-label="신고 상태"] .report-badge').innerText(), label)
    }
    const bg = row => row.evaluate(e => getComputedStyle(e).backgroundColor)
    assert.notEqual(await bg(rows.nth(0)), await bg(rows.nth(1)))
    await p.locator('[data-report-select="11"]').focus(); await p.keyboard.press("Enter"); await detail(p)
    if (width < 1201) await btn(p, "목록으로 돌아가기").click()
    await ready(p)
    const selected = p.locator('tr[data-selected="true"]')
    const selectedColor = await bg(selected)
    await selected.hover(); assert.equal(await bg(selected), selectedColor)
    await rows.nth(1).hover(); assert.notEqual(await bg(rows.nth(1)), selectedColor)
    assert.equal(await selected.locator('td[data-label="신고 상태"] .report-badge').innerText(), "처리 완료")
    await p.locator('[data-report-select="11"]').focus()
    assert(await p.locator('[data-report-select="11"]').evaluate(e => e === document.activeElement))
    assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await p.screenshot({ path: out + `/ux-mixed-status-${width}.png` })
  })
}

const section = (p, title) => p.locator(".report-detail > section").filter({ has: title === "현재 콘텐츠" ? p.locator(".report-current-heading") : p.getByRole("heading", { name:title, exact:true }) })
for (const width of [1920, 390, 320]) {
  await run(`followup content folding keyboard and result focus ${width}`, async () => {
    const {p,s} = await setup(width)
    const long = Array.from({length:40}, (_,i) => `문단 ${i+1}: 원문 줄바꿈 유지 https://example.test/${"longword".repeat(15)}`).join("\n")
    s.records[0].titleSnapshot = "긴 제목 ".repeat(35)
    s.records[0].contentSnapshot = long
    s.records[0].currentTarget.content = long
    s.records[0].currentTarget.title = s.records[0].titleSnapshot
    await p.goto(base + "/admin/reports"); await ready(p)
    const title = p.locator(".report-list-title").first(), preview = p.locator(".report-list-preview").first()
    for (const [locator,lines] of [[title,2],[preview,3]]) {
      assert(await locator.evaluate((e,n) => e.clientHeight <= parseFloat(getComputedStyle(e).lineHeight)*n+2 && e.scrollHeight>e.clientHeight,lines))
    }
    await p.screenshot({path:out+`/followup-list-${width}.png`})
    await p.locator('[data-report-select="11"]').click(); await detail(p)
    const snapshot = section(p,"신고 당시 콘텐츠"), current = section(p,"현재 콘텐츠")
    assert.equal(await snapshot.locator(".report-body").textContent(),long)
    await p.getByText("신고 당시와 내용이 같습니다.",{exact:true}).waitFor()
    assert.equal(await current.locator(".report-content-viewport").getAttribute("aria-hidden"),"true")
    assert.equal(await current.locator(".report-current-heading .report-content-badge").getByText("정상",{exact:true}).count(),1)
    assert.equal(await current.getByRole("link",{name:"게시글 관리로 이동",exact:true}).count(),1)
    const expand = snapshot.getByRole("button",{name:"전체 내용 펼치기",exact:true})
    await expand.focus(); await p.keyboard.press("Enter")
    assert.equal(await snapshot.getByRole("button",{name:"접기",exact:true}).getAttribute("aria-expanded"),"true")
    assert(await snapshot.locator(".report-content-viewport").evaluate(e=>e.clientHeight>=e.scrollHeight-1))
    await p.screenshot({path:out+`/followup-expanded-${width}.png`})
    await p.keyboard.press("Enter")
    assert.equal(await expand.getAttribute("aria-expanded"),"false")
    await p.screenshot({path:out+`/followup-collapsed-${width}.png`})
    const count=posts(s).length
    await btn(p,"처리 영역으로 이동").click()
    assert.equal(await p.evaluate(()=>document.activeElement?.textContent),"신고 처리")
    const h=p.getByRole("heading",{name:"신고 처리",exact:true})
    assert(await h.evaluate(e=>{const r=e.getBoundingClientRect(),b=e.closest(".admin-preview-body").getBoundingClientRect();return r.top>=b.top&&r.bottom<=b.bottom}))
    assert.equal(posts(s).length,count)
    await btn(p,"추가 조치 없이 처리 완료").click()
    await p.getByLabel("처리 사유 (필수)",{exact:true}).fill("내용 확인 후 추가 조치 없음")
    await btn(p,"처리 완료 확정").click(); await detail(p)
    await p.waitForFunction(()=>document.activeElement?.textContent==="신고 처리 결과")
    assert.equal(await p.locator(".report-handling-summary").count(),1)
    assert.equal(await btn(p,"처리 영역으로 이동").count(),0)
    const headings=await p.locator(".report-detail > section > h3").allTextContents()
    assert.equal(headings[1],"신고 처리 결과")
    assert.equal(await snapshot.getByRole("button",{name:"전체 내용 펼치기"}).getAttribute("aria-expanded"),"false")
    assert.equal(posts(s).length,1)
    assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
    await p.screenshot({path:out+`/followup-result-${width}.png`})
  })
}
await run("followup comparison exact values missing and failed refresh",async()=>{
  const {p,s}=await setup()
  s.records[0].contentSnapshot="짧은 본문"
  s.records[0].currentTarget.content="짧은 본문 "
  await p.goto(base+"/admin/reports?selected=11");await detail(p)
  await p.getByText("신고 당시와 내용이 다릅니다.",{exact:true}).waitFor()
  assert.equal(await section(p,"신고 당시 콘텐츠").getByRole("button").count(),0)
  assert.equal(await section(p,"현재 콘텐츠").getByRole("button").count(),0)
  s.detail=r=>r.fulfill(fail(500,"ERROR"))
  await btn(p,"처리 영역으로 이동").click();await btn(p,"추가 조치 없이 처리 완료").click()
  await p.getByLabel("처리 사유 (필수)").fill("확인 완료");await btn(p,"처리 완료 확정").click()
  await btn(p,"최신 정보 다시 조회").waitFor()
  assert.equal(await p.locator(".report-comparison").count(),0)
  assert.equal(await p.getByText("최신 조회 전 정보입니다. 처리 후 현재 콘텐츠 상태를 다시 확인해 주세요.",{exact:true}).count(),1)
  s.detail=null
  await btn(p,"최신 정보 다시 조회").click();await detail(p)
  await p.getByText("신고 당시와 내용이 다릅니다.",{exact:true}).waitFor()
  await p.locator('[data-report-select="13"]').click();await detail(p)
  assert.equal(await p.locator(".report-comparison").count(),0)
})
await run("followup expansion survives same report refresh and resets selection",async()=>{
  const {p,s}=await setup()
  s.records[0].contentSnapshot="길게 읽는 본문\n".repeat(60)
  s.records[0].currentTarget.content=s.records[0].contentSnapshot
  await p.goto(base+"/admin/reports?selected=11");await detail(p)
  const snapshot=section(p,"신고 당시 콘텐츠")
  await snapshot.getByRole("button",{name:"전체 내용 펼치기"}).click()
  await section(p,"현재 콘텐츠").getByRole("button",{name:"전체 내용 펼치기"}).click()
  await btn(p,"처리 영역으로 이동").focus()
  await p.keyboard.press("Tab");await p.keyboard.press("Shift+Tab");await p.keyboard.press("Enter")
  assert(await p.getByRole("heading",{name:"신고 처리",exact:true}).evaluate(e=>e===document.activeElement&&getComputedStyle(e).outlineStyle!=="none"))
  await btn(p,"추가 조치 없이 처리 완료").click()
  await p.getByLabel("처리 사유 (필수)").fill("유지 확인");await btn(p,"처리 완료 확정").click();await detail(p)
  assert.equal(await snapshot.getByRole("button",{name:"접기"}).getAttribute("aria-expanded"),"true")
  assert.equal(await section(p,"현재 콘텐츠").getByRole("button",{name:"접기"}).getAttribute("aria-expanded"),"true")
  await p.locator('[data-report-select="12"]').click();await detail(p)
  await p.locator('[data-report-select="11"]').click();await detail(p)
  assert.equal(await section(p,"신고 당시 콘텐츠").getByRole("button",{name:"전체 내용 펼치기"}).getAttribute("aria-expanded"),"false")
})
for(const width of [1440,390,320]) await run(`followup comment moderation leaves report received ${width}`,async()=>{
  const {p,s}=await setup(width)
  await p.goto(base+"/admin/reports?targetType=COMMENT&status=RECEIVED&sort=OLDEST&page=0&selected=12");await detail(p)
  const original=p.url()
  await p.screenshot({path:out+`/followup-comment-received-${width}.png`})
  await p.getByRole("link",{name:"신고된 댓글 관리로 이동",exact:true}).click()
  await p.getByText("콘텐츠 숨김·숨김 해제를 관리하는 화면입니다. 신고 처리 상태는 변경되지 않습니다.",{exact:true}).waitFor()
  await p.screenshot({path:out+`/followup-comment-management-${width}.png`})
  await btn(p,"댓글 #82 숨김 처리").click()
  await p.getByLabel("처리 사유 (필수)",{exact:true}).selectOption("OTHER")
  await p.getByLabel("상세 사유",{exact:false}).fill("콘텐츠만 숨김")
  await btn(p,"숨김 처리 실행").click()
  await btn(p,"댓글 #82 숨김 해제").waitFor()
  assert(await p.getByText("댓글을 숨겼습니다. 신고 종결은 신고 관리에서 진행해 주세요.",{exact:true}).count()>0)
  assert.equal(posts(s).length,0);assert.equal(s.records[1].status,"RECEIVED")
  await p.screenshot({path:out+`/followup-comment-hidden-${width}.png`})
  await p.getByRole("link",{name:"← 신고 #12로 돌아가기",exact:true}).click();await detail(p)
  assert.deepEqual(Object.fromEntries(new URL(p.url()).searchParams),Object.fromEntries(new URL(original).searchParams))
  await p.locator(".report-current-heading .report-content-badge").getByText("숨김",{exact:true}).waitFor()
  if (width <= 600) await btn(p, "목록으로 돌아가기").click()
  await ready(p)
  assert.equal(await p.locator('tr').filter({has:p.locator('[data-report-select="12"]')}).locator('td[data-label="현재 콘텐츠"] .report-content-badge').textContent(), "숨김")
  if (width <= 600) await btn(p, "선택한 상세 보기").click()
  await btn(p,"처리 영역으로 이동").click();await btn(p,"추가 조치 없이 처리 완료").click()
  await p.getByLabel("처리 사유 (필수)",{exact:true}).fill("숨김 확인 후 종결")
  await btn(p,"처리 완료 확정").click();await detail(p)
  await p.getByRole("heading",{name:"신고 처리 결과",exact:true}).waitFor()
  await p.screenshot({path:out+`/followup-comment-completed-${width}.png`})
})

for (const width of [1920, 1440, 1201, 1200, 1199, 390, 320]) {
  await run(`현재 콘텐츠 상태와 신고 상태를 독립 표시하고 재조회한다 ${width}`, async () => {
    const { p, s } = await setup(width)
    s.records = [record(11), record(12, "COMMENT"), record(13, "COMMENT", true), record(14), record(15)]
    s.records[1].currentTarget.hiddenAt = stamp
    s.records[3].currentTarget.deletedAt = stamp
    Object.assign(s.records[4], {
      status: "RESOLVED", handlingOutcome: "CONTENT_HIDDEN",
      handledByMemberId: 7, handledAt: stamp, handlingReason: "과거 숨김 후 복원",
    })
    s.records[0].titleSnapshot = "긴 제목 ".repeat(80)
    s.records[0].contentSnapshot = "긴미리보기".repeat(200)
    await p.goto(base + "/admin/reports")
    await ready(p)
    const row = (id) => p.locator("tr").filter({ has: p.locator(`[data-report-select="${id}"]`) })
    for (const [id, label, state] of [[11,"정상","접수"],[12,"숨김","접수"],[13,"확인 불가","접수"],[14,"삭제","접수"],[15,"정상","처리 완료"]]) {
      await row(id).locator('td[data-label="현재 콘텐츠"] .report-content-badge').getByText(label, { exact: true }).waitFor()
      assert.equal(await row(id).locator('td[data-label="신고 상태"] .report-badge').innerText(), state)
    }
    assert.deepEqual(await p.locator(".report-list th").allTextContents(), ["신고·대상", "신고 사유", "신고 상태", "현재 콘텐츠", "접수 시각"])
    const headers = await p.locator(".report-list th").evaluateAll(es => es.map(e => ({ id: e.id, scope: e.scope })))
    assert(headers.every(h => h.id && h.scope === "col"))
    assert.deepEqual(await row(11).locator("td").evaluateAll(es => es.map(e => e.getAttribute("headers"))), headers.map(h => h.id))
    assert.equal(await row(11).locator("td").first().getByText(/현재 콘텐츠:/).count(), 0)
    for (const id of [11,12,13,14,15]) {
      const cells = row(id).locator('td[data-label="신고 상태"], td[data-label="현재 콘텐츠"]')
      assert.equal(await cells.locator("button, a, [tabindex]").count(), 0)
      if (width <= 600) {
        for (const label of ["신고 상태:", "현재 콘텐츠:"]) assert(await cells.getByText(label, { exact: true }).isVisible())
      }
      assert(await cells.evaluateAll(es => es.every(e => {
        const badge = e.querySelector(".report-badge, .report-content-badge"), b = badge.getBoundingClientRect(), c = e.getBoundingClientRect()
        return b.left >= c.left && b.right <= c.right && badge.getClientRects().length === 1 && b.height < 40
      })))
    }
    assert.equal(await p.locator(".report-workspace").evaluate(e => getComputedStyle(e).display), width > 1200 ? "grid" : "block")
    assert.equal(s.calls.filter(c => /^\/api\/v1\/admin\/reports\/\d/.test(c.path)).length, 0)
    const style = (id) => row(id).evaluate(e => ({ bg: getComputedStyle(e).backgroundColor, shadow: getComputedStyle(e).boxShadow }))
    assert.deepEqual(await style(11), await style(12))
    assert.deepEqual(await style(11), await style(14))
    assert.notDeepEqual(await style(11), await style(15))
    assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    for (const id of [11,12,13,14,15]) {
      const label = row(id).locator('td[data-label="현재 콘텐츠"] .report-content-badge')
      assert(await label.evaluate(e => e.scrollWidth <= e.clientWidth && e.getBoundingClientRect().height > 0))
    }
    await p.screenshot({ path: `${out}/current-status-${width}.png`, fullPage: true })
    await p.getByLabel("신고 상태", { exact: true }).selectOption("RECEIVED")
    await ready(p)
    await row(12).locator('td[data-label="현재 콘텐츠"] .report-content-badge').getByText("숨김", { exact: true }).waitFor()
    assert.equal(await row(15).count(), 0)
    // A new backend response changes only content state, not the report's RECEIVED state.
    s.records[1].currentTarget.hiddenAt = null
    await p.reload()
    await ready(p)
    await row(12).locator('td[data-label="현재 콘텐츠"] .report-content-badge').getByText("정상", { exact: true }).waitFor()
    assert.equal(await row(12).locator('td[data-label="신고 상태"] .report-badge').innerText(), "접수")
    await row(12).getByRole("button").click()
    await detail(p)
    if (width <= 1200) {
      await btn(p, "목록으로 돌아가기").click()
      await p.waitForFunction(() => document.activeElement?.getAttribute("data-report-select") === "12")
    }
    assert.equal(await row(12).getAttribute("data-selected"), "true")
    assert.notDeepEqual(await style(11), await style(12))
    if (width > 1200) {
      assert(await p.locator(".report-detail-container").isVisible())
      const states = row(12).locator('td[data-label="신고 상태"], td[data-label="현재 콘텐츠"]')
      const boxes = await states.evaluateAll(es => es.map(e => e.getBoundingClientRect().toJSON()))
      assert.equal(boxes[0].y, boxes[1].y)
      assert(boxes[0].right <= boxes[1].left + 1)
    }
    await p.screenshot({ path: `${out}/current-status-selected-${width}.png`, fullPage: true })
    assert.equal(posts(s).length, 0)
    assert.deepEqual(s.errors, [])
  })
}

for (const width of [1920, 1440, 390, 320]) {
  await run(`상세 요약과 본문 제목 배지가 같은 현재 상태를 표시한다 ${width}`, async () => {
    const { p, s } = await setup(width)
    const cases = [
      [null, null, "정상"], [stamp, null, "숨김"], [null, stamp, "삭제"],
      [stamp, stamp, "삭제"], [null, null, "확인 불가"],
    ]
    for (const [index, [hiddenAt, deletedAt, label]] of cases.entries()) {
      const value = record(11)
      value.contentSnapshot = "긴 신고 당시 본문입니다.\n".repeat(100)
      value.reasonDetail = "긴 신고 사유입니다. ".repeat(60)
      if (index === 4) value.currentTarget = null
      else Object.assign(value.currentTarget, { hiddenAt, deletedAt })
      s.records[0] = value
      await p.goto(base + `/admin/reports?selected=11&case=${index}`)
      await detail(p)
      const summary = p.locator(".report-status-summary")
      assert.equal(await summary.locator(".report-badge").innerText(), "접수")
      assert.equal(await summary.locator(".report-content-badge").innerText(), label)
      assert.equal(await p.locator(".report-current-heading .report-content-badge").innerText(), label)
      assert.equal(await p.getByText(/^대상 상태:/).count(), 0)
      assert.equal(await summary.locator("button, [tabindex], a").count(), 0)
      if (width <= 600) {
        // Existing mobile filters precede the panel. Scroll the page, not the detail body.
        await p.mouse.move(5, 400)
        await p.mouse.wheel(0, 700)
        await p.waitForFunction(() => {
          const r = document.querySelector(".report-status-summary").getBoundingClientRect()
          return r.top >= 0 && r.bottom <= innerHeight
        })
        assert.equal(await p.locator(".admin-preview-body").evaluate(e => e.scrollTop), 0)
      }
      const bounds = await summary.boundingBox()
      assert(bounds.y >= 0 && bounds.y + bounds.height <= 900)
      assert(await summary.evaluate(e => e.compareDocumentPosition(document.querySelector(".report-content")) & Node.DOCUMENT_POSITION_FOLLOWING))
      assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      assert(await summary.evaluate(e => [...e.querySelectorAll("dd")].every(d => d.scrollWidth <= d.clientWidth)))
      if ([1,4].includes(index)) await p.screenshot({ path: `${out}/detail-status-${index}-${width}.png` })
    }
    Object.assign(s.records[0], record(11), {
      status: "RESOLVED", handlingOutcome: "CONTENT_HIDDEN", handledByMemberId: 7,
      handledAt: stamp, handlingReason: "긴 처리 사유 ".repeat(60),
    })
    await p.goto(base + "/admin/reports?selected=11&case=resolved")
    await detail(p)
    assert.equal(await p.locator(".report-status-summary .report-badge").innerText(), "처리 완료")
    assert.equal(await p.locator(".report-status-summary .report-content-badge").innerText(), "정상")
    assert.equal(await p.locator(".report-current-heading .report-content-badge").innerText(), "정상")
    assert(await p.locator(".report-handling-summary").innerText().then(t => t.includes("처리 결과: 콘텐츠 숨김")))
    assert.equal(await p.locator(".report-status-freshness").count(), 0)
    if (width <= 600) {
      await p.mouse.move(5, 400); await p.mouse.wheel(0, 700)
      await p.waitForFunction(() => document.querySelector(".report-status-summary").getBoundingClientRect().bottom <= innerHeight)
    }
    await p.screenshot({ path: `${out}/detail-status-resolved-${width}.png` })
    assert.deepEqual(s.errors, [])
  })
  await run(`최초 로딩과 실패를 구분하고 이전 상태 안내를 재조회 성공까지 유지한다 ${width}`, async () => {
    const { p, s } = await setup(width), initial = gate()
    s.detail = async r => { initial.arrive(); await initial.wait; return r.fulfill(fail(500,"ERROR")) }
    await p.goto(base + "/admin/reports?selected=11")
    await initial.arrived
    await p.getByText("신고 상세를 불러오고 있습니다…", { exact: true }).waitFor()
    assert.equal(await p.locator(".report-status-summary").count(), 0)
    initial.release()
    await btn(p, "최신 정보 다시 조회").waitFor()
    assert.equal(await p.locator(".report-status-summary").count(), 0)
    s.records[0].currentTarget.hiddenAt = stamp
    s.detail = null
    await btn(p, "최신 정보 다시 조회").click(); await detail(p)
    const badges = () => p.locator(".report-status-summary .report-content-badge, .report-current-heading .report-content-badge")
    assert.deepEqual(await badges().allTextContents(), ["숨김","숨김"])
    // Trigger the existing post-success read; no new refresh route or polling.
    const refresh = gate()
    s.detail = async r => { refresh.arrive(); await refresh.wait; return r.fulfill(fail(500,"ERROR")) }
    await btn(p, "처리 영역으로 이동").click()
    await btn(p, "추가 조치 없이 처리 완료").click()
    await p.getByLabel("처리 사유 (필수)").fill("내용 유지 후 종결")
    await btn(p, "처리 완료 확정").click(); await refresh.arrived
    await p.locator(".report-status-freshness").getByText("최신 상태를 확인하고 있습니다. 이전 조회 정보입니다.", { exact: true }).waitFor({ state: "attached" })
    assert.deepEqual(await badges().allTextContents(), ["숨김","숨김"])
    // Scroll back using the panel, without altering focus or triggering another request.
    await p.locator(".admin-preview-body").evaluate(e => { e.scrollTop = 0 })
    await p.screenshot({ path: `${out}/detail-status-loading-${width}.png` })
    refresh.release()
    await p.locator(".report-status-freshness").getByText("최신 상태를 확인하지 못했습니다. 현재 콘텐츠는 이전 조회 정보입니다.", { exact: true }).waitFor()
    assert.deepEqual(await badges().allTextContents(), ["숨김","숨김"])
    assert.equal(await p.locator(".report-status-summary .report-badge").innerText(), "처리 완료")
    await p.screenshot({ path: `${out}/detail-status-failed-${width}.png` })
    s.records[0].currentTarget.hiddenAt = null; s.detail = null
    await btn(p, "최신 정보 다시 조회").click(); await detail(p)
    assert.deepEqual(await badges().allTextContents(), ["정상","정상"])
    assert.equal(await p.locator(".report-status-freshness").count(), 0)
    assert.equal(await btn(p,"최신 정보 다시 조회").count(), 0)
    assert.equal(posts(s).length, 1)
    assert.deepEqual(s.errors, [])
  })
}

await fs.writeFile(out + "/results.json", JSON.stringify(results, null, 2))
await browser.close()
await new Promise((r) => server.close(r))
console.log(
  `${results.filter((r) => r.passed).length}/${results.length} passed`,
)
if (results.some((r) => !r.passed)) process.exitCode = 1
