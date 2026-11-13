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
})
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
          status: u.pathname.endsWith("/resolve") ? "RESOLVED" : "DISMISSED",
          handledByMemberId: 7,
          handledAt: "2026-11-13T02:00:00Z",
          handlingReason: r.request().postDataJSON().handlingReason,
        })
        return r.fulfill({
          json: {
            reportId: id,
            status: value.status,
            handledByMemberId: 7,
            handledAt: value.handledAt,
            handlingReason: value.handlingReason,
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
    if (u.pathname === "/api/v1/admin/comments/82") return r.fulfill({json:{
      ...comment(82),postId:71,postStatus:"VISIBLE",status:"VISIBLE",hiddenAt:null,deletedAt:null,
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
          status: "VISIBLE",
          author: { ...post.author, withdrawn: false },
          createdAt: stamp,
          updatedAt: stamp,
          contentUpdatedAt: null,
          hiddenAt: null,
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
    assert(
      s.calls
        .find((c) => c.path.startsWith("/api/v1/admin/reports?"))
        .path.includes("status=RECEIVED"),
    )
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
    await p.goto(base + "/admin/reports?selected=13")
    await ready(p)
    await detail(p)
    await p
      .getByText("현재 대상을 찾을 수 없습니다.", { exact: false })
      .waitFor()
    await btn(p, "추가 조치 없이 처리 완료").click()
    await btn(p, "처리 완료 확정").click()
    assert.equal(posts(s).length, 0)
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
    await p.getByText("신고 처리를 완료했습니다.", { exact: false }).waitFor()
    await detail(p)
    await ready(p)
    assert.equal(posts(s).length, 1)
    assert.equal(await btn(p, "추가 조치 없이 처리 완료").count(), 0)
    assert(
      await p
        .getByText("이 신고는 현재 목록 조건에 포함되어 있지 않습니다.")
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
      .getByText("최신 정보를 불러오지 못했습니다.", { exact: false })
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
    await p.getByText("신고 처리를 완료했습니다.", { exact: false }).waitFor()
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
      .getByText("이 신고는 현재 목록 조건에 포함되어 있지 않습니다.")
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
        }))
      if (received) {
        g.arrive()
        await g.wait
      }
      return r.fulfill({ json: page(items) })
    }
    await p.goto(base + "/admin/reports")
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
  await p.getByText("신고 처리를 완료했습니다.",{exact:false}).waitFor()
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
  await p.getByRole("link",{name:"← 신고 관리로"}).click();await detail(p)
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
await fs.writeFile(out + "/results.json", JSON.stringify(results, null, 2))
await browser.close()
await new Promise((r) => server.close(r))
console.log(
  `${results.filter((r) => r.passed).length}/${results.length} passed`,
)
if (results.some((r) => !r.passed)) process.exitCode = 1
