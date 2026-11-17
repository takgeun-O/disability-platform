import { memberSummaryFixture } from "./helpers/member-summary-fixture.mjs"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8443",
  out =
    process.env.MEMBER_ARTIFACT_DIR ??
    "/private/tmp/iyum-member-summary-frontend/browser"
await fs.mkdir(out, { recursive: true })
const browser = await chromium.launch({ channel: "chrome", headless: true }),
  results = [],
  contexts = []
const path = "/api/v1/admin/members",
  stamp = "2026-11-12T01:00:00Z"
const admin = {
  memberId: 7,
  nickname: "검증관리자",
  email: "admin@example.test",
  role: "ADMIN",
}
const item = (id, status = "ACTIVE") => ({
  memberId: id,
  nickname: `소리나무 ${id}`,
  email: `member.${id}@example.test`,
  status,
  role: "USER",
  createdAt: stamp,
  updatedAt: stamp,
  withdrawnAt: null,
})
const page = (items, n = 0, total = items.length) => ({
  items,
  page: n,
  size: 20,
  totalElements: total,
  totalPages: Math.ceil(total / 20),
  hasNext: n + 1 < Math.ceil(total / 20),
  hasPrevious: n > 0,
})
const failure = (status, code) => ({
  status,
  json: { code, message: "mock failure", fieldErrors: [] },
})
const gate = () => {
  let arrive, release
  return {
    arrived: new Promise((r) => (arrive = r)),
    promise: new Promise((r) => (release = r)),
    arrive,
    release,
  }
}
const btn = (p, name) => p.getByRole("button", { name, exact: true })
const field = (p) =>
  p.getByRole("textbox", { name: "처리 사유 (필수)", exact: true })
const ready = (p) =>
  p.locator('.admin-members-list[aria-busy="false"]').waitFor()
const detailReady = (p) =>
  p.locator('#admin-member-detail[aria-busy="false"]').waitFor()
const historyReady = (p) =>
  p.locator('.admin-member-history[aria-busy="false"]').waitFor()
const frames = (p) =>
  p.evaluate(
    () =>
      new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))),
  )
const posts = (s) =>
  s.calls.filter((c) => c.method === "POST" && c.url.startsWith(path))
async function setup(width = 1920, height = 1080) {
  const c = await browser.newContext({
    viewport: { width, height },
    timezoneId: "America/Los_Angeles",
  })
  contexts.push(c)
  const p = await c.newPage()
  p.setDefaultTimeout(10000)
  const s = {
    members: [item(100), item(99, "SUSPENDED")],
    logs: [],
    summary: null,
    calls: [],
    post: null,
    list: null,
    detail: null,
    history: null,
    member: admin,
    errors: [],
  }
  p.on("pageerror", (e) => s.errors.push(e.message))
  await p.addInitScript(() => {
    const original = fetch
    window.__handled = []
    window.fetch = async (u, o) => {
      const r = await original(
        u,
        String(u).includes("/admin/members") ? { ...o, signal: undefined } : o,
      )
      if (!r.ok) queueMicrotask(() => window.__handled.push(String(u)))
      const json = r.json.bind(r)
      r.json = async () => {
        const value = await json()
        queueMicrotask(() => window.__handled.push(String(u)))
        return value
      }
      return r
    }
  })
  await c.route("http://127.0.0.1:8082/**", async (r) => {
    const u = new URL(r.request().url()),
      method = r.request().method()
    s.calls.push({
      url: u.pathname + u.search,
      method,
      body: method === "POST" ? r.request().postDataJSON() : undefined,
    })
    if (u.pathname.endsWith("/auth/me"))
      return r.fulfill(
        s.member ? { json: s.member } : failure(401, "AUTHENTICATION_REQUIRED"),
      )
    if (u.pathname.endsWith("/csrf"))
      return r.fulfill({
        json: { headerName: "X-CSRF-TOKEN", token: "mock-only" },
      })
    if (u.pathname.endsWith("/login")) {
      s.member = { ...admin, memberId: 8, nickname: "새 관리자" }
      return r.fulfill({ json: s.member })
    }
    if (u.pathname.endsWith("/logout")) {
      s.member = null
      return r.fulfill({ status: 204 })
    }
    if (u.pathname === path + "/summary")
      return s.summary
        ? s.summary(r, u)
        : r.fulfill({ json: memberSummaryFixture() })
    if (u.pathname === path) {
      if (s.list) return s.list(r, u)
      const items = s.members.filter(
        (m) =>
          !u.searchParams.get("status") ||
          m.status === u.searchParams.get("status"),
      )
      return r.fulfill({
        json: page(items, Number(u.searchParams.get("page"))),
      })
    }
    if (u.pathname.endsWith("/moderation-logs")) {
      assert.equal(u.searchParams.has("sort"), false)
      if (s.history) return s.history(r, u)
      const id = Number(u.pathname.split("/").at(-2))
      return r.fulfill({
        json: page(
          s.logs.filter((l) => l.targetMemberId === id),
          Number(u.searchParams.get("page")),
        ),
      })
    }
    if (method === "POST" && u.pathname.startsWith(path + "/")) {
      assert(r.request().headers()["x-csrf-token"])
      assert.deepEqual(Object.keys(r.request().postDataJSON()), ["reason"])
      if (s.post) return s.post(r, u)
      const id = Number(u.pathname.split("/").at(-2)),
        m = s.members.find((m) => m.memberId === id),
        status = u.pathname.endsWith("/suspend") ? "SUSPENDED" : "ACTIVE",
        changed = m.status !== status,
        before = m.status
      m.status = status
      m.updatedAt = "2026-11-12T02:00:00Z"
      if (changed)
        s.logs.unshift({
          logId: s.logs.length + 1,
          actorMemberId: 7,
          targetMemberId: id,
          action: status === "SUSPENDED" ? "SUSPEND" : "LIFT_SUSPENSION",
          beforeStatus: before,
          afterStatus: status,
          reason: r.request().postDataJSON().reason,
          createdAt: m.updatedAt,
        })
      return r.fulfill({ json: { memberId: id, status, changed } })
    }
    if (u.pathname.startsWith(path + "/")) {
      if (s.detail) return s.detail(r, u)
      const m = s.members.find(
        (m) => m.memberId === Number(u.pathname.split("/").at(-1)),
      )
      return r.fulfill(m ? { json: m } : failure(404, "MEMBER_NOT_FOUND"))
    }
    if (u.pathname === "/api/v1/admin/posts")
      return r.fulfill({ json: page([]) })
    return r.fulfill(failure(404, "NOT_FOUND"))
  })
  return { p, s, c }
}
async function open(p, id = 100) {
  await p.goto(base + "/admin/members")
  await ready(p)
  await p.locator(`[data-member-select="${id}"]`).click()
  await detailReady(p)
  await historyReady(p)
}
async function form(p, action = "이용 정지") {
  await btn(p, action).click()
  await field(p).waitFor()
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
    results.push({ name, passed: false, error: e.stack })
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
const summaryReady = (p) =>
  p.locator('.member-summary [aria-busy="false"]').waitFor()
const counts = (p) =>
  p.locator(".member-summary .dashboard-count dd:first-of-type")
const summaryReads = (s) => s.calls.filter((c) => c.url === path + "/summary")
const snapshot = (p, name) =>
  p.screenshot({ path: `${out}/${name}.png`, fullPage: true })
async function noClipping(p) {
  assert(
    await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    "문서 가로 넘침 없음",
  )
  assert(
    await counts(p).evaluateAll((nodes) =>
      nodes.every((node) => {
        const range = document.createRange()
        range.selectNodeContents(node)
        const box = node.closest(".dashboard-count").getBoundingClientRect()
        return [...range.getClientRects()].every(
          (r) => r.left >= box.left && r.right <= box.right,
        )
      }),
    ),
    "숫자 전체가 카드 안에 표시되어야 한다",
  )
}
for (const width of [1920, 1440, 900, 390, 320]) {
  await run(
    `회원 요약 카드 매핑·한국 날짜·긴 숫자·키보드 ${width}`,
    async () => {
      const { p, s } = await setup(width)
      await p.goto(base + "/admin/members")
      await ready(p)
      await summaryReady(p)
      assert.deepEqual(
        await p.locator(".member-summary dt").allTextContents(),
        ["현재 회원", "오늘 가입", "인증 대기", "이용 정지"],
      )
      assert.deepEqual(await counts(p).allTextContents(), [
        "2,000명",
        "7명",
        "3명",
        "5명",
      ])
      assert.equal(
        await p.locator(".member-summary time").first().textContent(),
        "2026.11.18",
      )
      assert.match(
        await p.locator(".member-summary time").last().textContent(),
        /2026.11.18. 00:00:00 \(한국 시간\)/,
      )
      assert.equal(await p.locator(".member-summary a").count(), 0)
      assert.equal(
        await p
          .locator('.member-summary [role="status"]')
          .getAttribute("class"),
        "sr-only",
      )
      await noClipping(p)
      await snapshot(p, `normal-${width}`)
      s.summary = (r) =>
        r.fulfill({
          json: {
            ...memberSummaryFixture(),
            counts: {
              currentMemberCount: Number.MAX_SAFE_INTEGER,
              todayMemberCount: 3000000000,
              pendingMemberCount: 999999999999,
              suspendedMemberCount: 999999999999,
            },
          },
        })
      await btn(p, "요약 새로고침").focus()
      await p.keyboard.press("Enter")
      await summaryReady(p)
      assert(
        await btn(p, "요약 새로고침").evaluate(
          (e) => e === document.activeElement,
        ),
      )
      assert.equal(
        await counts(p).first().textContent(),
        "9,007,199,254,740,991명",
      )
      await noClipping(p)
      await snapshot(p, `long-${width}`)
      if (width === 1440) {
        await p.evaluate(() => (document.documentElement.style.zoom = "2"))
        await noClipping(p)
        await snapshot(p, "zoom-200")
      }
      assert.deepEqual(s.errors, [])
    },
  )
}
await run(
  "요약 최초 로딩과 오류는 목록을 막지 않고 정상 0과 구분된다",
  async () => {
    const { p, s } = await setup()
    const g = gate()
    s.summary = async (r) => {
      g.arrive()
      await g.promise
      return r.fulfill(failure(500, "FAILED"))
    }
    await p.goto(base + "/admin/members")
    await g.arrived
    await ready(p)
    assert.deepEqual(await counts(p).allTextContents(), ["—", "—", "—", "—"])
    assert.equal(
      await btn(p, "요약 새로고침").getAttribute("aria-disabled"),
      "true",
    )
    await btn(p, "요약 새로고침").focus()
    await p.keyboard.press("Enter")
    await frames(p)
    assert.equal(summaryReads(s).length, 1)
    await snapshot(p, "loading")
    g.release()
    await p.locator('.member-summary [role="alert"]').waitFor()
    await snapshot(p, "initial-error")
    assert.equal(await p.locator('[data-member-select="100"]').count(), 1)
    s.summary = (r) =>
      r.fulfill({
        json: {
          ...memberSummaryFixture(),
          counts: {
            currentMemberCount: 0,
            todayMemberCount: 0,
            pendingMemberCount: 0,
            suspendedMemberCount: 0,
          },
        },
      })
    await btn(p, "요약 다시 조회").click()
    await summaryReady(p)
    assert.deepEqual(await counts(p).allTextContents(), [
      "0명",
      "0명",
      "0명",
      "0명",
    ])
    assert.equal(await p.locator('.member-summary [role="alert"]').count(), 0)
  },
)
await run(
  "목록 조건은 요약과 독립적이며 수동 조회는 상세와 입력 사유를 보존한다",
  async () => {
    const { p, s } = await setup()
    s.list = (r, u) =>
      r.fulfill({
        json: page(s.members, Number(u.searchParams.get("page")), 40),
      })
    await p.goto(base + "/admin/members")
    await ready(p)
    await summaryReady(p)
    await p.getByLabel("검색어", { exact: true }).fill("소리")
    await btn(p, "검색").click()
    await ready(p)
    for (const [name, value] of [
      ["회원 상태", "ACTIVE"],
      ["권한", "USER"],
      ["가입일 정렬", "OLDEST"],
    ]) {
      await p.getByLabel(name, { exact: true }).selectOption(value)
      await ready(p)
    }
    await btn(p, "다음").click()
    await ready(p)
    assert.equal(summaryReads(s).length, 1)
    await p.locator('[data-member-select="100"]').click()
    await detailReady(p)
    await historyReady(p)
    await form(p)
    await field(p).fill("유지할 사유")
    const url = p.url(),
      listReads = s.calls.filter((c) => c.url.startsWith(path + "?")).length
    await btn(p, "요약 새로고침").click()
    await summaryReady(p)
    assert.equal(p.url(), url)
    assert.equal(await field(p).inputValue(), "유지할 사유")
    assert.equal(
      s.calls.filter((c) => c.url.startsWith(path + "?")).length,
      listReads,
    )
    assert.equal(summaryReads(s).length, 2)
  },
)
for (const action of ["suspend", "lift-suspension"])
  await run(
    `회원 ${action} 성공 후 서버 요약 재조회와 상세·이력 보존`,
    async () => {
      const { p, s } = await setup()
      if (action === "lift-suspension") s.members[0].status = "SUSPENDED"
      await open(p)
      await summaryReady(p)
      s.summary = (r) =>
        r.fulfill({
          json: {
            ...memberSummaryFixture(),
            counts: {
              ...memberSummaryFixture().counts,
              suspendedMemberCount: action === "suspend" ? 6 : 4,
            },
          },
        })
      const label = action === "suspend" ? "이용 정지" : "이용 정지 해제"
      await form(p, label)
      await field(p).fill("요약 검증 사유")
      await btn(p, label + " 실행").click()
      await p.getByText(label + "를 완료했습니다.", { exact: true }).waitFor()
      await historyReady(p)
      await summaryReady(p)
      assert.equal(summaryReads(s).length, 2)
      assert.equal(posts(s).length, 1)
      assert.equal(await counts(p).first().textContent(), "2,000명")
      assert.equal(
        await counts(p).last().textContent(),
        action === "suspend" ? "6명" : "4명",
      )
      assert(p.url().includes("selected=100"))
      assert.equal(await p.locator(".admin-member-history li").count(), 1)
    },
  )
await run(
  "성공 조치 후 요약 실패는 이전 시각을 유지하고 GET만 재시도한다",
  async () => {
    const { p, s } = await setup()
    await open(p)
    await summaryReady(p)
    const time = await p
      .locator(".member-summary time")
      .last()
      .getAttribute("datetime")
    s.summary = (r) => r.fulfill(failure(500, "FAILED"))
    await form(p)
    await field(p).fill("검증 사유")
    await btn(p, "이용 정지 실행").click()
    await p.getByText("이용 정지를 완료했습니다.", { exact: true }).waitFor()
    await p.locator('.member-summary [role="alert"]').waitFor()
    assert.equal(
      await p.locator(".member-summary time").last().getAttribute("datetime"),
      time,
    )
    assert.equal(await counts(p).first().textContent(), "2,000명")
    await snapshot(p, "refresh-error")
    s.summary = (r) =>
      r.fulfill({
        json: {
          ...memberSummaryFixture(),
          counts: { ...memberSummaryFixture().counts, suspendedMemberCount: 6 },
        },
      })
    await btn(p, "요약 다시 조회").click()
    await summaryReady(p)
    assert.equal(await counts(p).last().textContent(), "6명")
    assert.equal(posts(s).length, 1)
    assert.equal(await p.locator('.member-summary [role="alert"]').count(), 0)
  },
)
await run(
  "조치 실패는 요약 재조회나 수치 변경 없이 사유를 보존한다",
  async () => {
    const { p, s } = await setup()
    await open(p)
    await summaryReady(p)
    s.post = (r) => r.fulfill(failure(400, "INVALID_REASON"))
    await form(p)
    await field(p).fill("유지할 사유")
    await btn(p, "이용 정지 실행").click()
    await p.locator(".admin-member-action-error").waitFor()
    assert.equal(summaryReads(s).length, 1)
    assert.equal(await field(p).inputValue(), "유지할 사유")
    assert.equal(await counts(p).last().textContent(), "5명")
  },
)
await run(
  "조치 이전의 늦은 요약 응답은 조치 이후 수치를 덮어쓰지 않는다",
  async () => {
    const { p, s } = await setup()
    await open(p)
    await summaryReady(p)
    const g = gate()
    s.summary = async (r) => {
      g.arrive()
      await g.promise
      return r.fulfill({
        json: {
          ...memberSummaryFixture(),
          counts: { ...memberSummaryFixture().counts, currentMemberCount: 999 },
        },
      })
    }
    await btn(p, "요약 새로고침").click()
    await g.arrived
    s.summary = (r) =>
      r.fulfill({
        json: {
          ...memberSummaryFixture(),
          counts: { ...memberSummaryFixture().counts, suspendedMemberCount: 6 },
        },
      })
    await form(p)
    await field(p).fill("순서 검증")
    await btn(p, "이용 정지 실행").click()
    await p.getByText("이용 정지를 완료했습니다.", { exact: true }).waitFor()
    await summaryReady(p)
    const handled = await p.evaluate(() => window.__handled.length)
    g.release()
    await p.waitForFunction((n) => window.__handled.length > n, handled)
    await frames(p)
    assert.equal(await counts(p).first().textContent(), "2,000명")
    assert.equal(await counts(p).last().textContent(), "6명")
    assert.equal(posts(s).length, 1)
  },
)
for (const status of [401, 403])
  await run(
    `요약 ${status} 응답은 기존 요약·회원 정보를 제거한다`,
    async () => {
      const { p, s } = await setup()
      await open(p)
      await summaryReady(p)
      s.summary = (r) =>
        r.fulfill(
          failure(
            status,
            status === 401 ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED",
          ),
        )
      if (status === 401) s.member = null
      await btn(p, "요약 새로고침").click()
      await p.locator(".member-summary").waitFor({ state: "detached" })
      assert.equal(await p.locator("#admin-member-detail").count(), 0)
      assert.equal(await p.locator("[data-member-select]").count(), 0)
    },
  )
await run("메뉴 이동 이후 늦은 요약은 화면을 바꾸지 않는다", async () => {
  const { p, s } = await setup()
  await p.goto(base + "/admin/members")
  await ready(p)
  await summaryReady(p)
  const g = gate()
  s.summary = async (r) => {
    g.arrive()
    await g.promise
    return r.fulfill(failure(403, "ACCESS_DENIED"))
  }
  await btn(p, "요약 새로고침").click()
  await g.arrived
  await p.getByRole("link", { name: "게시글 관리", exact: true }).click()
  await p.getByRole("heading", { name: "게시글 관리", exact: true }).waitFor()
  const handled = await p.evaluate(() => window.__handled.length)
  g.release()
  await p.waitForFunction((n) => window.__handled.length > n, handled)
  await frames(p)
  assert.equal(await p.locator(".member-summary").count(), 0)
  assert.equal(
    await p
      .getByRole("heading", { name: "관리자 권한이 없습니다", exact: true })
      .count(),
    0,
  )
})
await fs.writeFile(out + "/results.json", JSON.stringify(results, null, 2))
await browser.close()
if (results.some((r) => !r.passed)) process.exitCode = 1
