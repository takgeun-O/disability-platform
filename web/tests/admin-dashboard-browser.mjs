// All administrator responses are synthetic. No real data or credentials are used.
import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8467"
const out = process.env.DASHBOARD_ARTIFACT_DIR ?? "/private/tmp/iyum-dashboard-frontend-review/browser"
await fs.mkdir(out, { recursive: true })
const browser = await chromium.launch({ channel: "chrome", headless: true })
const results = []
const admin = { memberId: 7, email: "review@example.test", nickname: "검증 관리자", role: "ADMIN" }
const fixture = () => ({
  queriedAt: "2026-11-16T15:01:02Z", baseDate: "2026-11-17",
  counts: { pendingReportCount: 7, todayReportCount: 1234, todayPostCount: 82, todayMemberCount: 15 },
  pendingReports: [91, 18, 203, 56, 120].map((id, i) => ({
    reportId: id, targetType: i % 2 ? "COMMENT" : "POST", targetId: 800 + i,
    reasonType: ["SPAM_ADVERTISING", "ABUSE_HATE", "PERSONAL_INFORMATION", "DUPLICATE_FLOODING", "OTHER"][i],
    createdAt: `2026-11-${String(10 + i).padStart(2, "0")}T01:00:00Z`,
  })),
})
const pageData = () => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false })
const detailData = () => ({
  reportId: 91, targetType: "POST", targetId: 800, status: "RECEIVED", reasonType: "OTHER",
  reasonDetail: "검토 요청", titleSnapshot: "합성 신고 제목", contentSnapshot: "합성 신고 본문", createdAt: "2026-11-10T01:00:00Z",
  reporterMemberId: 19, currentTarget: null, handledByMemberId: null, handledAt: null, handlingReason: null, handlingOutcome: null,
})
const deferred = () => { let release, arrive; return { wait: new Promise(r => { release = r }), arrived: new Promise(r => { arrive = r }), release, arrive } }
const ready = p => p.locator('.admin-dashboard > [aria-busy="false"]').waitFor()
const button = (p, name) => p.getByRole("button", { name, exact: true })
async function setup(width = 1440, timezoneId = "America/Los_Angeles") {
  const context = await browser.newContext({ viewport: { width, height: 960 }, timezoneId })
  await context.route("https://fonts.googleapis.com/**", r => r.fulfill({ body: "", contentType: "text/css" }))
  const p = await context.newPage(); p.setDefaultTimeout(12000)
  const s = { detailRead: null, mutation: null, member: admin, data: fixture(), dashboard: null, detail: detailData(), calls: [], errors: [] }
  p.on("pageerror", e => s.errors.push(e.message))
  await context.route("http://127.0.0.1:8082/**", async r => {
    const u = new URL(r.request().url()), method = r.request().method()
    s.calls.push({ path: u.pathname, query: u.search, method })
    if (u.pathname.endsWith("/auth/me")) return r.fulfill(s.member ? { json: s.member } : { status: 401, json: { code: "AUTHENTICATION_REQUIRED", message: "로그인이 필요합니다.", fieldErrors: [] } })
    if (u.pathname.endsWith("/csrf")) return r.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "synthetic" } })
    if (u.pathname === "/api/v1/admin/dashboard") return s.dashboard ? s.dashboard(r) : r.fulfill({ json: s.data })
    if (u.pathname === "/api/v1/admin/reports/summary") return r.fulfill({ json: { queriedAt: "2026-11-17T03:00:00Z", baseDate: "2026-11-17", counts: { pendingReportCount: s.data.counts.pendingReportCount, todayReportCount: 3, todayHandledReportCount: s.detail.status === "RECEIVED" ? 2 : 3 } } })
    if (u.pathname === "/api/v1/admin/reports") return r.fulfill({ json: pageData() })
    if (/^\/api\/v1\/admin\/reports\/\d+$/.test(u.pathname)) return s.detailRead ? s.detailRead(r, u) : r.fulfill({ json: { ...s.detail, reportId: Number(u.pathname.split("/").pop()) } })
    if (/^\/api\/v1\/admin\/reports\/91\/(resolve|dismiss|hide-and-resolve)$/.test(u.pathname)) {
      if (s.mutation) return s.mutation(r, u)
      const status = u.pathname.endsWith("/dismiss") ? "DISMISSED" : "RESOLVED"
      const outcome = u.pathname.endsWith("/hide-and-resolve") ? "CONTENT_HIDDEN" : "NO_CONTENT_ACTION"
      if (outcome === "CONTENT_HIDDEN") s.detail.currentTarget.hiddenAt = "2026-11-17T01:00:00Z"
      s.detail = { ...s.detail, status, handledByMemberId: 7, handledAt: "2026-11-17T01:00:00Z", handlingReason: "검토 완료", handlingOutcome: outcome }
      s.data = { ...s.data, counts: { ...s.data.counts, pendingReportCount: 6 }, pendingReports: s.data.pendingReports.slice(1) }
      return r.fulfill({ json: { reportId: 91, status, handledByMemberId: 7, handledAt: s.detail.handledAt, handlingReason: "검토 완료", handlingOutcome: outcome } })
    }
    if (method === "POST" && /^\/api\/v1\/admin\/posts\/800(?:\/comments\/801)?\/(hide|restore)$/.test(u.pathname)) {
      const hidden = u.pathname.endsWith("/hide")
      s.detail.currentTarget.hiddenAt = hidden ? "2026-11-17T01:00:00Z" : null
      return r.fulfill({ json: { postId: 800, ...(u.pathname.includes("/comments/") ? { commentId: 801 } : {}), hidden, hiddenAt: s.detail.currentTarget.hiddenAt, changed: true } })
    }
    const content = {
      author: { memberId: 8, nickname: "합성 작성자", withdrawn: false },
      createdAt: "2026-11-10T01:00:00Z", updatedAt: "2026-11-10T01:00:00Z", contentUpdatedAt: null,
      content: "합성 콘텐츠 관리 본문", hiddenAt: s.detail.currentTarget?.hiddenAt ?? null, deletedAt: null,
      status: s.detail.currentTarget?.hiddenAt ? "HIDDEN" : "VISIBLE",
    }
    if (u.pathname === "/api/v1/admin/posts/800") return r.fulfill({ json: { ...content, postId: 800, title: "합성 관리 제목", type: "QUESTION", topic: "HEARING_AID" } })
    if (u.pathname === "/api/v1/admin/comments/801") return r.fulfill({ json: { ...content, commentId: 801, postId: 800, postStatus: "VISIBLE" } })
    if (u.pathname === "/api/v1/admin/posts/800/comments") return r.fulfill({ json: { ...pageData(), postId: 800, postStatus: "VISIBLE" } })
    if (["/api/v1/admin/posts", "/api/v1/admin/members", "/api/v1/admin/operation-logs"].includes(u.pathname)) return r.fulfill({ json: pageData() })
    throw new Error(`unexpected mock API ${method} ${u.pathname}`)
  })
  return { context, p, s }
}
async function run(name, action, width) {
  if (process.env.TEST_FILTER && !new RegExp(process.env.TEST_FILTER).test(name)) return
  const x = await setup(width)
  try { await action(x); assert.deepEqual(x.s.errors, []); results.push({ name, passed: true }); console.log("PASS", name) }
  catch (e) { results.push({ name, passed: false, error: e.message }); console.error("FAIL", name, e.message); await x.p.screenshot({ path: `${out}/failure-${results.length}.png`, fullPage: true }).catch(() => {}) }
  finally { await x.context.close() }
}
const reads = s => s.calls.filter(c => c.path === "/api/v1/admin/dashboard")
try {
  for (const width of [1920, 1440, 390, 320]) await run(`${width}px 수치·서버 정렬·한국 시간·목록·키보드와 화면 확인`, async ({ p, s }) => {
    await p.goto(base + "/admin"); await ready(p)
    assert.deepEqual(await p.locator(".dashboard-count dd:first-of-type").allTextContents(), ["7건", "1,234건", "82건", "15명"])
    assert.deepEqual(await p.locator(".dashboard-pending tbody th").allTextContents(), ["신고 번호#91", "신고 번호#18", "신고 번호#203", "신고 번호#56", "신고 번호#120"])
    assert((await p.locator(".dashboard-dates").innerText()).includes("2026.11.17"))
    assert((await p.locator(".dashboard-dates").innerText()).includes("2026.11.17. 00:01:02 (한국 시간)"))
    assert((await p.locator(".dashboard-pending-heading").innerText()).includes("전체 7건 · 표시 5건"))
    assert.equal(await p.locator('.admin-sidebar [aria-current="page"]').innerText(), "대시보드")
    assert.equal(reads(s).length, 1); assert.equal(reads(s)[0].query, "")
    await button(p, "새로고침").focus(); await p.keyboard.press("Tab")
    assert.equal(await p.evaluate(() => document.activeElement.textContent), "접수 신고 보기 →")
    const dimensions = await p.evaluate(() => ({ viewport: innerWidth, body: document.documentElement.scrollWidth }))
    assert(dimensions.body <= dimensions.viewport, JSON.stringify(dimensions))
    await p.screenshot({ path: `${out}/dashboard-${width}.png`, fullPage: true })
    await p.getByRole("link", { name: "신고 #91 상세 보기" }).focus()
    assert(await p.evaluate(() => getComputedStyle(document.activeElement).outlineStyle !== "none"))
    await p.keyboard.press("Enter")
    await p.locator('.report-detail[aria-busy="false"]').waitFor()
    assert.equal(new URL(p.url()).pathname, "/admin/reports/91")
    assert(!s.calls.some(c => c.path === "/api/v1/admin/reports"))
    assert((await p.locator(".report-detail").innerText()).includes("합성 신고 제목"))
    assert(s.calls.some(c => c.path === "/api/v1/admin/reports/91"))
    assert(!s.calls.some(c => c.path === "/api/v1/admin/reports/800"))
  }, width)

  await run("상단 관리자 진입은 대시보드로 연결되고 비로그인은 기존 복귀 경로를 유지한다", async ({ p, s }) => {
    await p.goto(base + "/admin/posts")
    await p.getByRole("heading", { name: "게시글 관리", exact: true }).waitFor()
    await p.locator(".iyum-member-menu").getByRole("link", { name: "관리자", exact: true }).click()
    await p.waitForURL(u => u.pathname === "/admin"); await ready(p)
    s.member = null
    await p.reload()
    await p.getByRole("heading", { name: "로그인이 필요합니다", exact: true }).waitFor()
    assert.equal(await p.locator(".admin-access-card").getByRole("link", { name: "로그인", exact: true }).getAttribute("href"), "/login?from=%2Fadmin")
    assert.equal(await p.locator(".dashboard-count").count(), 0)
  })

  await run("최초 로딩·빈 결과·실패·잘못된 응답을 구분하고 재시도한다", async ({ p, s }) => {
    const g = deferred(); s.dashboard = async r => { g.arrive(); await g.wait; await r.fulfill({ status: 503, json: {} }) }
    await p.goto(base + "/admin"); await g.arrived
    await p.getByText("대시보드를 불러오고 있습니다…", { exact: true }).waitFor()
    assert.equal(await p.locator(".dashboard-count, .dashboard-empty").count(), 0)
    assert(await button(p, "조회 중…").isDisabled()); g.release()
    await p.locator(".dashboard-error").waitFor(); assert.equal(await p.locator(".dashboard-count").count(), 0)
    s.dashboard = r => r.fulfill({ json: {} }); await button(p, "다시 조회").click()
    await p.getByText("대시보드 응답을 확인하지 못했습니다. 다시 시도해 주세요.").waitFor()
    s.dashboard = null; s.data = { ...fixture(), counts: { pendingReportCount: 0, todayReportCount: 0, todayPostCount: 0, todayMemberCount: 0 }, pendingReports: [] }
    await button(p, "다시 조회").click(); await ready(p)
    assert.deepEqual(await p.locator(".dashboard-count dd:first-of-type").allTextContents(), ["0건", "0건", "0건", "0명"])
    await p.getByText("표시할 처리 대기 신고가 없습니다.").waitFor()
    await p.screenshot({ path: `${out}/empty.png`, fullPage: true })
  })

  await run("새로고침 중복 방지·이전 결과 안내·기준 시각 보존과 복구", async ({ p, s }) => {
    await p.goto(base + "/admin"); await ready(p)
    const g = deferred(); s.dashboard = async r => { g.arrive(); await g.wait; await r.fulfill({ status: 503, json: {} }) }
    await button(p, "새로고침").click(); await g.arrived
    assert(await button(p, "조회 중…").isDisabled())
    await p.keyboard.press("Enter"); assert.equal(reads(s).length, 2)
    assert((await p.locator('[role="status"].dashboard-announcement').innerText()).includes("이전 조회 결과"))
    g.release(); await p.locator(".dashboard-error").waitFor()
    assert.equal(await p.locator('.dashboard-dates time[datetime="2026-11-16T15:01:02Z"]').count(), 1)
    assert((await p.locator(".dashboard-error").innerText()).includes("이전 조회 결과"))
    await p.screenshot({ path: `${out}/refresh-error.png`, fullPage: true })
    s.dashboard = null; s.data.queriedAt = "2026-11-16T16:00:00Z"; s.data.counts.todayPostCount = 83
    await button(p, "새로고침").click(); await ready(p)
    assert.equal(await p.locator(".dashboard-error").count(), 0)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").nth(2).innerText(), "83건")
    assert.equal(await button(p, "새로고침").evaluate(e => e === document.activeElement), true)
  })

  for (const status of [401, 403]) await run(`${status} 응답 후 이전 관리자 데이터를 제거한다`, async ({ p, s }) => {
    await p.goto(base + "/admin"); await ready(p)
    s.dashboard = r => r.fulfill({ status, json: { code: status === 401 ? "AUTHENTICATION_REQUIRED" : "ADMIN_REQUIRED", message: "문자열로 분기하지 않음" } })
    await button(p, "새로고침").click()
    await p.getByRole("heading", { name: status === 401 ? "로그인이 필요합니다" : "관리자 권한이 없습니다", exact: true }).waitFor()
    assert.equal(await p.locator(".dashboard-count, .dashboard-pending, .dashboard-dates").count(), 0)
  })

  await run("이전 필터를 배제한 접수 목록 이동과 메뉴 활성·뒤로가기", async ({ p }) => {
    await p.goto(base + "/admin/reports?status=DISMISSED&targetType=COMMENT&reasonType=OTHER&startDate=2026-01-01&endDate=2026-01-02")
    await p.getByRole("heading", { name: "신고 관리", exact: true }).waitFor()
    await p.locator(".admin-sidebar").getByRole("link", { name: "대시보드", exact: true }).click(); await ready(p)
    await p.getByRole("link", { name: "접수 신고 보기 →", exact: true }).click()
    await p.waitForURL(u => u.pathname === "/admin/reports")
    const q = new URL(p.url()).searchParams
    assert.equal(q.get("status"), "RECEIVED"); assert.equal(q.get("targetType"), "ALL"); assert.equal(q.get("page"), "0")
    for (const k of ["startDate", "endDate", "reasonType", "selected"]) assert(!q.has(k))
    assert.equal(await p.locator('.admin-sidebar [aria-current="page"]').innerText(), "신고 관리")
    await p.goBack(); await ready(p)
    for (const [name, path] of [["게시글 관리", "/admin/posts"], ["운영 기록", "/admin/operation-logs"], ["회원 관리", "/admin/members"]]) {
      await p.locator(".admin-sidebar").getByRole("link", { name, exact: true }).click()
      await p.waitForURL(u => u.pathname === path)
      assert.equal(await p.locator('.admin-sidebar [aria-current="page"]').innerText(), name)
      await p.locator(".admin-sidebar").getByRole("link", { name: "대시보드", exact: true }).click(); await ready(p)
    }
  })

  await run("첫 페이지에 없는 신고를 처리하고 돌아오면 대시보드를 새로 조회한다", async ({ p, s }) => {
    await p.goto(base + "/admin"); await ready(p)
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click()
    await p.locator('.report-detail[aria-busy="false"]').waitFor()
    await button(p, "추가 조치 없이 처리 완료").click()
    await p.getByLabel("처리 사유", { exact: false }).fill("검토 완료")
    await button(p, "처리 완료 확정").click()
    await p.getByRole("heading", { name: "신고 처리 결과", exact: true }).waitFor()
    assert.equal(new URL(p.url()).pathname, "/admin/reports/91")
    await button(p, "신고 상세 닫기").click(); await ready(p)
    assert.equal(reads(s).length, 2)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "6건")
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.endsWith("/resolve")).length, 1)
    assert.equal(await p.getByRole("link", { name: "신고 #91 상세 보기" }).count(), 0)
  })

  await run("종결된 신고도 목록 포함 여부와 무관하게 실제 상세 상태를 확인한다", async ({ p, s }) => {
    s.detail = { ...s.detail, status: "DISMISSED", handledByMemberId: 7, handledAt: "2026-11-17T01:00:00Z", handlingReason: "기각된 신고", handlingOutcome: "NO_CONTENT_ACTION" }
    await p.goto(base + "/admin"); await ready(p)
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click()
    await p.locator('.report-detail[aria-busy="false"]').waitFor()
    assert((await p.locator(".report-status-summary").innerText()).includes("기각"))
    assert.equal(new URL(p.url()).pathname, "/admin/reports/91")
    assert(!s.calls.some(c => c.path === "/api/v1/admin/reports"))
  })

  await run("화면을 떠난 이전 응답은 재진입한 최신 화면을 덮어쓰지 않는다", async ({ p, s }) => {
    // Ignore cancellation in this fixture so the response-generation guard is tested too.
    await p.addInitScript(() => {
      const original = fetch
      window.fetch = (u, options) => original(u, String(u).endsWith("/admin/dashboard") ? { ...options, signal: undefined } : options)
    })
    const g = deferred(), old = fixture(); old.counts.pendingReportCount = 999
    s.dashboard = async r => { g.arrive(); await g.wait; await r.fulfill({ json: old }) }
    await p.goto(base + "/admin"); await g.arrived
    await p.locator(".admin-sidebar").getByRole("link", { name: "신고 관리", exact: true }).click()
    await p.waitForURL(u => u.pathname === "/admin/reports")
    s.dashboard = null
    await p.locator(".admin-sidebar").getByRole("link", { name: "대시보드", exact: true }).click(); await ready(p)
    const finished = p.waitForEvent("requestfinished", { predicate: r => r.url().endsWith("/admin/dashboard") })
    g.release(); await finished
    await p.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))))
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "7건")
  })

  const reportReady = p => p.locator('.report-detail[aria-busy="false"]').waitFor()
  const openModal = async p => {
    await p.goto(base + "/admin"); await ready(p)
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click()
    await p.getByRole("dialog", { name: "신고 #91 상세", exact: true }).waitFor()
    await reportReady(p)
  }
  const target = (type = "POST") => ({
    targetId: type === "POST" ? 800 : 801, parentPostId: type === "POST" ? null : 800,
    author: { memberId: 8, nickname: "합성 작성자", withdrawn: false },
    title: type === "POST" ? "현재 합성 제목" : null, content: "현재 합성 본문\n둘째 문단", hiddenAt: null, deletedAt: null,
  })
  const actionButton = action => action === "resolve" ? "추가 조치 없이 처리 완료" : action === "dismiss" ? "신고 기각" : "숨김·처리 완료"
  const confirmButton = action => action === "resolve" ? "처리 완료 확정" : action === "dismiss" ? "기각 확정" : "숨김·처리 완료 확정"
  async function fillAction(p, action = "resolve") {
    await button(p, actionButton(action)).click()
    await p.getByLabel("처리 사유 (필수)").fill("합성 검토 사유")
  }

  function conclude(s, status = "RESOLVED") {
    s.detail = { ...s.detail, status, handledByMemberId: 99, handledAt: "2026-11-17T01:00:00Z", handlingReason: "다른 관리자 검토", handlingOutcome: "NO_CONTENT_ACTION" }
    s.data = { ...s.data, counts: { ...s.data.counts, pendingReportCount: 6 }, pendingReports: s.data.pendingReports.slice(1) }
  }
  for (const mode of ["충돌", "불확실", "첫 조회"]) await run(`종결 확인 ${mode}: 성공 토스트 없이 배경을 한 번 갱신한다`, async ({ p, s }) => {
    if (mode === "첫 조회") {
      s.detailRead = r => { conclude(s, "DISMISSED"); return r.fulfill({ json: s.detail }) }
      await openModal(p)
    } else {
      await openModal(p)
      assert.equal(reads(s).length, 1)
      s.mutation = r => {
        conclude(s)
        return mode === "충돌"
          ? r.fulfill({ status: 409, json: { code: "REPORT_ALREADY_HANDLED", message: "이미 종결된 신고입니다.", fieldErrors: [] } })
          : r.abort("failed")
      }
      await fillAction(p); await button(p, "처리 완료 확정").click()
      if (mode === "불확실") {
        await p.getByText(/이번 요청의 처리 결과는 확실하지 않습니다/).waitFor()
        await button(p, "최신 정보 다시 조회").click()
      }
    }
    await p.getByRole("heading", { name: "신고 처리 결과", exact: true }).waitFor()
    await reportReady(p); await ready(p)
    assert.equal(reads(s).length, 2)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "6건")
    assert.equal(await p.locator(".report-toast").innerText(), "")
    if (mode === "충돌") assert((await p.locator(".report-detail-modal").innerText()).includes("이번 요청의 성공이 아닙니다"))
    if (mode === "불확실") assert((await p.locator(".report-detail-modal").innerText()).includes("이번 요청의 처리 결과는 확실하지 않습니다"))
    assert.equal(s.calls.filter(c => c.method === "POST").length, mode === "첫 조회" ? 0 : 1)
    await button(p, "신고 상세 닫기").click(); await p.waitForURL(u => u.pathname === "/admin")
    await p.waitForFunction(() => document.activeElement?.id === "dashboard-pending-title")
  })

  await run("확정 성공 뒤 상세 재시도를 반복해도 종결 갱신은 한 번이며 POST를 재전송하지 않는다", async ({ p, s }) => {
    await openModal(p)
    s.detailRead = r => r.fulfill({ status: 503, json: {} })
    await fillAction(p); await button(p, "처리 완료 확정").click()
    await button(p, "최신 정보 다시 조회").waitFor(); await ready(p)
    assert.equal(reads(s).length, 2)
    await button(p, "최신 정보 다시 조회").click(); await reportReady(p)
    assert.equal(reads(s).length, 2)
    s.detailRead = null
    await button(p, "최신 정보 다시 조회").click(); await reportReady(p)
    assert.equal(await p.locator(".report-recovery").count(), 0)
    assert.equal(reads(s).length, 2)
    assert.equal(s.calls.filter(c => c.method === "POST").length, 1)
  })

  for (const path of ["/admin/reports/91", "/admin/reports?selected=91"]) await run(`종결 상태의 ${path} 화면은 대시보드를 요청하지 않는다`, async ({ p, s }) => {
    conclude(s)
    await p.goto(base + path); await reportReady(p)
    assert.equal(reads(s).length, 0)
    await p.getByRole("heading", { name: "신고 처리 결과", exact: true }).waitFor()
  })

  await run("종결 확인의 배경 실패는 자동 반복 없이 이전 시각을 보존하고 수동 복구한다", async ({ p, s }) => {
    await p.goto(base + "/admin"); await ready(p)
    conclude(s)
    s.dashboard = r => r.fulfill({ status: 503, json: {} })
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click()
    await reportReady(p); await p.locator("dialog .dashboard-error").waitFor()
    assert.equal(reads(s).length, 2)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "7건")
    assert.equal(await p.locator('.dashboard-dates time[datetime="2026-11-16T15:01:02Z"]').count(), 1)
    assert.equal(await p.locator(".report-toast").innerText(), "")
    // A failed manual GET rerenders the modal but must not start an automatic GET.
    await button(p, "대시보드 다시 조회").click(); await ready(p)
    await p.locator("dialog .dashboard-error").waitFor()
    assert.equal(reads(s).length, 3)
    s.dashboard = null
    await button(p, "대시보드 다시 조회").click(); await ready(p)
    assert.equal(reads(s).length, 4)
    assert.equal(await p.locator("dialog .dashboard-error").count(), 0)
    assert.equal(await p.locator(".report-detail-modal[open]").count(), 1)
    assert.equal(s.calls.filter(c => c.method === "POST").length, 0)
  })

  for (const hidden of [false, true]) await run(`현재 콘텐츠 ${hidden ? "숨김" : "정상"}의 처리 설명은 상태를 구분한다`, async ({ p, s }) => {
    s.detail.currentTarget = target()
    if (hidden) s.detail.currentTarget.hiddenAt = "2026-11-17T00:00:00Z"
    await openModal(p)
    await p.getByText("현재 콘텐츠 상태를 변경하지 않고 신고를 종결합니다.", { exact: true }).waitFor()
    await p.getByText(hidden ? "기존 숨김을 유지하고 이 신고를 처리 완료합니다." : "콘텐츠를 숨기고 이 신고를 처리 완료합니다.", { exact: true }).waitFor()
    assert.equal(await p.getByText("공개 상태를 유지하고 신고를 종결합니다.", { exact: true }).count(), 0)
    assert.equal(reads(s).length, 1)
  })

  await run("모달 최초 로딩과 오류도 내용 높이이며 헤더를 유지한다", async ({ p, s }) => {
    const g = deferred()
    s.detailRead = async r => { g.arrive(); await g.wait; return r.fulfill({ status: 503, json: {} }) }
    await p.goto(base + "/admin"); await ready(p)
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click(); await g.arrived
    await p.getByText("신고 상세를 불러오고 있습니다…", { exact: true }).waitFor()
    const modal = p.locator(".report-detail-modal")
    assert((await modal.boundingBox()).height < 480)
    await p.screenshot({ path: `${out}/followup-loading.png` })
    g.release()
    await p.getByText("신고 상세를 불러오지 못했습니다.", { exact: true }).waitFor()
    assert((await modal.boundingBox()).height < 480)
    await p.screenshot({ path: `${out}/followup-error.png` })
    s.detailRead = null
    await button(p, "최신 정보 다시 조회").click(); await reportReady(p)
    assert.equal(reads(s).length, 1)
  })

  await run("짧은 댓글은 내용 높이로 표시하고 긴 본문은 헤더를 남긴 단일 스크롤을 사용한다", async ({ p, s }) => {
    await p.setViewportSize({ width: 1440, height: 1400 })
    s.detail = { ...s.detail, targetType: "COMMENT", targetId: 801, titleSnapshot: null, contentSnapshot: "짧은 댓글", currentTarget: target("COMMENT") }
    s.detail.currentTarget.content = "짧은 댓글"
    await openModal(p)
    const modal = p.locator(".report-detail-modal")
    const short = await modal.boundingBox()
    assert(short.height < 1400 * .85, JSON.stringify(short))
    assert(await p.locator(".report-modal-body").evaluate(e => e.scrollHeight <= e.clientHeight + 1))
    await p.screenshot({ path: `${out}/followup-short-comment.png` })
    await button(p, "신고 상세 닫기").click(); await p.waitForURL(u => u.pathname === "/admin")
    s.detail = { ...detailData(), currentTarget: target(), contentSnapshot: "긴 게시글 본문입니다.\n".repeat(250) }
    await openModal(p)
    await button(p, "전체 내용 펼치기").first().click()
    assert(await p.locator(".report-modal-body").evaluate(e => e.scrollHeight > e.clientHeight))
    assert((await modal.boundingBox()).height <= 1261)
    await button(p, "처리 영역으로 이동").click()
    assert(await p.getByRole("heading", { name: "신고 처리", exact: true }).evaluate(e => e === document.activeElement))
    const header = await p.locator(".report-modal-heading").boundingBox()
    assert(header.y >= 0 && header.y + header.height <= 1400)
    await p.screenshot({ path: `${out}/followup-long-post.png` })
    await fs.writeFile(`${out}/height-measurements.json`, JSON.stringify({ viewport: { width: 1440, height: 1400 }, short, expanded: await modal.boundingBox(), header }, null, 2))
    await button(p, "접기").first().click()
    assert(await p.locator(".report-modal-body").evaluate(e => e.scrollTop <= e.scrollHeight - e.clientHeight + 1))
    const collapsedHeader = await p.locator(".report-modal-heading").boundingBox()
    assert(collapsedHeader.y >= 0 && collapsedHeader.y + collapsedHeader.height < 1400)
  })

  for (const width of [1920, 1440, 390, 320]) await run(`모달 ${width}px 배경·단일 스크롤·제목·키보드·닫기 포커스`, async ({ p, s }) => {
    s.detail.contentSnapshot = "합성 긴 본문과 줄바꿈입니다.\n".repeat(80)
    s.detail.currentTarget = target()
    await p.goto(base + "/admin"); await ready(p)
    const link = p.getByRole("link", { name: "신고 #91 상세 보기", exact: true })
    await link.scrollIntoViewIfNeeded(); await link.focus()
    const before = await p.evaluate(() => ({ y: scrollY, length: history.length }))
    await p.keyboard.press("Enter"); await reportReady(p)
    const dialog = p.getByRole("dialog", { name: "신고 #91 상세", exact: true })
    assert.equal(await p.locator(".admin-dashboard").count(), 1)
    assert.equal(await dialog.locator(".report-list, .report-filters, .dashboard-count, .admin-preview-body").count(), 0)
    assert.equal(await dialog.getByRole("heading", { name: "신고 #91 상세", exact: true }).count(), 1)
    assert(await dialog.getByRole("heading", { name: "신고 #91 상세", exact: true }).evaluate(e => e === document.activeElement))
    assert.equal(new URL(p.url()).pathname, "/admin/reports/91")
    assert.equal(await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).count(), 0, "background excluded from accessibility tree")
    assert(!s.calls.some(c => c.path === "/api/v1/admin/reports"))
    const bounds = await dialog.boundingBox()
    assert(bounds.width <= (width > 600 ? 1100 : width))
    assert(bounds.height <= (width > 600 ? 960 * .9 + 2 : 960))
    assert(await dialog.evaluate(e => e.scrollWidth <= e.clientWidth))
    await p.mouse.click(2, 2)
    assert.equal(await p.locator("dialog[open]").count(), 1)
    for (let i = 0; i < 15; i++) {
      await p.keyboard.press(i % 2 ? "Shift+Tab" : "Tab")
      assert(await dialog.evaluate(e => e.contains(document.activeElement)))
    }
    await button(p, "처리 영역으로 이동").click()
    const focus = p.getByRole("heading", { name: "신고 처리", exact: true })
    assert(await focus.evaluate(e => e === document.activeElement))
    const header = await p.locator(".report-modal-heading").boundingBox()
    assert(header.y >= 0 && header.y + header.height < 960)
    await p.locator(".report-modal-body").evaluate(e => { e.scrollTop = 0 })
    await p.screenshot({ path: `${out}/modal-${width}.png` })
    await button(p, "신고 상세 닫기").click()
    await p.waitForURL(u => u.pathname === "/admin")
    assert.equal(await p.locator("dialog[open]").count(), 0)
    await p.waitForFunction(() => document.activeElement?.matches('[data-dashboard-report="91"]'))
    assert(await p.locator('[data-dashboard-report="91"]').evaluate(e => e === document.activeElement))
    assert.equal(await p.evaluate(() => scrollY), before.y)
    assert.equal(await p.evaluate(() => history.length), before.length + 1)
    await p.goForward(); await reportReady(p)
    assert.equal(await p.locator(".report-detail-modal[open]").count(), 1)
    await p.goBack(); await p.waitForURL(u => u.pathname === "/admin")
    assert.equal(await p.locator("dialog[open]").count(), 0)
  }, width)

  await run("모달 직접 진입·새로고침·수정 키 새 탭은 전용 페이지", async ({ p, context }) => {
    await openModal(p)
    await p.reload(); await reportReady(p)
    assert.equal(await p.locator("dialog, .admin-dashboard").count(), 0)
    assert.equal(await p.locator(".report-page").count(), 1)
    await p.goto(base + "/admin/reports/91?from=dashboard"); await reportReady(p)
    assert.equal(await p.locator(".report-page").count(), 1)
    await p.getByRole("link", { name: "대시보드로 돌아가기", exact: true }).click(); await ready(p)
    const popupPromise = context.waitForEvent("page")
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click({ modifiers: ["Meta"] })
    const popup = await popupPromise
    await reportReady(popup)
    assert.equal(await popup.locator(".report-page").count(), 1)
    assert.equal(await popup.locator("dialog").count(), 0)
    assert.equal(new URL(p.url()).pathname, "/admin")
    await popup.close()
  })

  for (const action of ["resolve", "dismiss", "hide-and-resolve"]) await run(`모달 ${action} 성공·배경 재조회·행 제거 후 유지·대체 포커스`, async ({ p, s }) => {
    s.detail.currentTarget = target()
    await openModal(p); await fillAction(p, action)
    await button(p, confirmButton(action)).click()
    await p.getByRole("heading", { name: "신고 처리 결과", exact: true }).waitFor()
    await ready(p)
    assert(await p.getByRole("heading", { name: "신고 처리 결과", exact: true }).evaluate(e => e === document.activeElement))
    assert.equal(await p.locator(".report-detail-modal[open]").count(), 1)
    assert.equal(await p.locator('[data-dashboard-report="91"]').count(), 0)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "6건")
    assert.equal(reads(s).length, 2)
    assert.equal(await p.locator(".report-status-summary .report-badge").innerText(), action === "dismiss" ? "기각" : "처리 완료")
    assert.equal(await p.locator(".report-status-summary .report-content-badge").innerText(), action === "hide-and-resolve" ? "숨김" : "정상")
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.includes("/reports/")).length, 1)
    assert.equal(await p.locator('dialog [role="status"]').getByText(/신고 #91.*완료/).count(), 1)
    await p.screenshot({ path: `${out}/modal-result-${action}.png` })
    await button(p, "신고 상세 닫기").click(); await p.waitForURL(u => u.pathname === "/admin")
    await p.waitForFunction(() => document.activeElement?.id === "dashboard-pending-title")
    assert(await p.locator("#dashboard-pending-title").evaluate(e => e === document.activeElement))
    await p.goForward(); await reportReady(p)
    assert.equal(await p.getByRole("heading", { name: "신고 처리 결과", exact: true }).count(), 1)
    assert.equal(await p.getByLabel("처리 사유 (필수)").count(), 0)
    await button(p, "신고 상세 닫기").click()
    await p.getByRole("link", { name: "신고 관리", exact: true }).click()
    await p.locator('.report-summary [aria-busy="false"]').waitFor()
    assert.deepEqual(await p.locator(".report-summary .dashboard-count dd:first-of-type").allTextContents(), ["6건", "3건", "3건"])
    assert.equal(s.calls.filter(c => c.path === "/api/v1/admin/reports/summary").length, 1)
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.includes("/reports/")).length, 1)
  })

  await run("모달 처리 성공과 대시보드 갱신 실패를 구분하고 닫은 뒤 GET으로 복구", async ({ p, s }) => {
    await openModal(p)
    s.dashboard = r => r.fulfill({ status: 503, json: {} })
    await fillAction(p); await button(p, "처리 완료 확정").click()
    await p.locator('dialog .dashboard-error').waitFor()
    assert.equal(await p.locator(".report-status-summary .report-badge").innerText(), "처리 완료")
    assert.equal(await p.locator(".report-recovery").count(), 0)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "7건")
    assert.equal(await p.locator('.dashboard-dates time[datetime="2026-11-16T15:01:02Z"]').count(), 1)
    await p.screenshot({ path: `${out}/modal-dashboard-failed.png` })
    await button(p, "대시보드 다시 조회").click()
    await p.locator('dialog .dashboard-error').waitFor()
    await button(p, "신고 상세 닫기").click(); await p.waitForURL(u => u.pathname === "/admin")
    await p.locator('.admin-dashboard > .dashboard-error').waitFor()
    s.dashboard = null
    await button(p, "다시 조회").click(); await ready(p)
    assert.equal(await p.locator(".dashboard-error").count(), 0)
    assert.equal(await p.locator(".dashboard-count dd:first-of-type").first().innerText(), "6건")
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.endsWith("/resolve")).length, 1)
  })

  for (const width of [1440, 390]) await run(`모달 ${width}px 입력 보호·중첩 확인창·Esc·스크롤 잠금`, async ({ p }) => {
    await openModal(p); await fillAction(p)
    for (const close of [() => button(p, "신고 상세 닫기").click(), () => p.keyboard.press("Escape"), () => p.goBack()]) {
      await close()
      const confirm = p.getByRole("dialog", { name: "입력한 사유를 버리고 이동할까요?", exact: true })
      await confirm.waitFor()
      assert.equal(await p.locator("dialog[open]").count(), 2)
      await p.keyboard.press("Tab"); await p.keyboard.press("Tab")
      assert(await confirm.evaluate(e => e.contains(document.activeElement)))
      assert.equal(await p.evaluate(() => document.body.style.position), "fixed")
      await p.screenshot({ path: `${out}/modal-confirm-${width}.png` })
      await p.keyboard.press("Escape")
      await confirm.waitFor({ state: "detached" })
      assert.equal(await p.locator("dialog[open]").count(), 1)
      assert.equal(await p.getByLabel("처리 사유 (필수)").inputValue(), "합성 검토 사유")
      assert.equal(await p.evaluate(() => document.body.style.position), "fixed")
      assert(await p.locator(".report-detail-modal").evaluate(e => e.contains(document.activeElement)))
    }
    await button(p, "신고 상세 닫기").click()
    await button(p, "입력 버리고 이동").click()
    await p.waitForURL(u => u.pathname === "/admin")
    assert.equal(await p.locator("dialog[open]").count(), 0)
    assert.equal(await p.evaluate(() => document.body.style.position), "")
    assert.equal(await p.evaluate(() => document.documentElement.style.overflow), "")
  }, width)

  await run("모달 처리 중 닫기 차단과 중복 제출 방지", async ({ p, s }) => {
    const g = deferred()
    s.mutation = async r => { g.arrive(); await g.wait; return r.fulfill({ status: 503, json: { code: "ERROR", message: "합성 실패", fieldErrors: [] } }) }
    await openModal(p); await fillAction(p)
    await button(p, "처리 완료 확정").click(); await g.arrived
    assert(await button(p, "처리 중…").isDisabled())
    await button(p, "신고 상세 닫기").click()
    await p.getByRole("dialog", { name: "처리 중입니다", exact: true }).waitFor()
    assert.equal(await button(p, "입력 버리고 이동").count(), 0)
    await p.keyboard.press("Escape")
    assert.equal(await p.locator(".report-detail-modal[open]").count(), 1)
    g.release()
    await button(p, "최신 정보 다시 조회").waitFor()
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.endsWith("/resolve")).length, 1)
  })

  for (const origin of ["detail", "dashboard"]) for (const status of [401, 403]) await run(`모달 ${origin} ${status} 시 배경과 상세 데이터 함께 제거`, async ({ p, s }) => {
    await openModal(p); await fillAction(p)
    const failure = r => r.fulfill({ status, json: { code: status === 401 ? "AUTHENTICATION_REQUIRED" : "ADMIN_REQUIRED", message: "합성 오류", fieldErrors: [] } })
    if (origin === "detail") s.detailRead = failure
    else s.dashboard = failure
    await button(p, "처리 완료 확정").click()
    await p.getByRole("heading", { name: status === 401 ? "로그인이 필요합니다" : "관리자 권한이 없습니다", exact: true }).waitFor()
    assert.equal(await p.locator(".report-detail, .dashboard-count, .dashboard-pending, dialog[open]").count(), 0)
  })

  for (const status of [404, 500]) await run(`모달 ${status} 상세 오류와 재시도`, async ({ p, s }) => {
    s.detailRead = r => r.fulfill({ status, json: { code: "ERROR", message: "합성 오류", fieldErrors: [] } })
    await p.goto(base + "/admin"); await ready(p)
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click()
    await p.getByRole("dialog", { name: "신고 #91 상세", exact: true }).waitFor()
    await p.getByText(status === 404 ? "신고 기록을 찾을 수 없습니다." : "신고 상세를 불러오지 못했습니다.", { exact: true }).waitFor()
    assert.equal(await p.locator(".report-status-summary").count(), 0)
    s.detailRead = null; await button(p, "최신 정보 다시 조회").click(); await reportReady(p)
    assert(!s.calls.some(c => c.path === "/api/v1/admin/reports"))
  })

  await run("모달 재진입 뒤 이전 상세 지연 응답은 반영되지 않는다", async ({ p, s }) => {
    await p.addInitScript(() => {
      const original = fetch
      window.fetch = (url, options) => original(url, String(url).endsWith("/admin/reports/91") ? { ...options, signal: undefined } : options)
    })
    const g = deferred(), old = { ...s.detail, titleSnapshot: "오래된 이전 응답" }
    s.detailRead = async r => { g.arrive(); await g.wait; return r.fulfill({ json: old }) }
    await p.goto(base + "/admin"); await ready(p)
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click(); await g.arrived
    await button(p, "신고 상세 닫기").click(); await p.waitForURL(u => u.pathname === "/admin")
    s.detailRead = null
    await p.getByRole("link", { name: "신고 #91 상세 보기", exact: true }).click(); await reportReady(p)
    const finished = p.waitForEvent("requestfinished", { predicate: r => r.url().endsWith("/admin/reports/91") })
    g.release(); await finished
    await p.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))))
    assert.equal(await p.getByText("오래된 이전 응답", { exact: true }).count(), 0)
    assert.equal(await p.getByText("합성 신고 제목", { exact: true }).count(), 1)
  })

  await run("모달 작은 가용 높이에서 사유 입력과 버튼 접근", async ({ p, s }) => {
    s.detail.currentTarget = target()
    await openModal(p); await fillAction(p)
    await p.setViewportSize({ width: 390, height: 420 })
    await p.getByLabel("처리 사유 (필수)").focus()
    await p.keyboard.press("Tab")
    assert(await p.locator(".report-detail-modal").evaluate(e => e.contains(document.activeElement)))
    await button(p, "처리 완료 확정").scrollIntoViewIfNeeded()
    const box = await button(p, "처리 완료 확정").boundingBox()
    assert(box.y >= 0 && box.y + box.height <= 420)
    assert(await p.locator(".report-detail-modal").evaluate(e => e.scrollWidth <= e.clientWidth))
    await p.screenshot({ path: `${out}/modal-small-viewport.png` })
  }, 390)

  for (const type of ["POST", "COMMENT"]) await run(`모달 ${type} 콘텐츠 관리 왕복·입력 보호·최신 상태와 출발 맥락`, async ({ p, s }) => {
    s.detail.currentTarget = target(type)
    s.detail.targetType = type
    s.detail.targetId = type === "POST" ? 800 : 801
    s.detail.titleSnapshot = type === "POST" ? "합성 신고 제목" : null
    await openModal(p)
    await fillAction(p)
    const managementLink = p.getByRole("link", { name: type === "POST" ? "게시글 관리로 이동" : "신고된 댓글 관리로 이동", exact: true })
    await managementLink.click()
    await button(p, "계속 작성").click()
    assert.equal(await p.getByLabel("처리 사유 (필수)").inputValue(), "합성 검토 사유")
    await managementLink.click(); await button(p, "입력 버리고 이동").click()
    await p.waitForURL(u => u.pathname === `/admin/${type === "POST" ? "posts/800" : "comments/801"}`)
    assert.equal(new URL(p.url()).searchParams.get("from"), "/admin/reports/91?from=dashboard")
    assert.equal(await p.locator("dialog[open]").count(), 0)
    assert.equal(await p.evaluate(() => document.body.style.position), "")
    await button(p, type === "POST" ? "게시글 숨김 처리" : "댓글 #801 숨김 처리").click()
    await p.getByLabel("처리 사유 (필수)", { exact: true }).selectOption("OTHER")
    await p.getByLabel("상세 사유", { exact: false }).fill("합성 콘텐츠 관리 사유")
    await button(p, "숨김 처리 실행").click()
    await button(p, type === "POST" ? "게시글 숨김 해제" : "댓글 #801 숨김 해제").waitFor()
    await p.getByRole("link", { name: type === "POST" ? "← 신고 관리로" : "← 신고 #91로 돌아가기", exact: true }).click()
    await reportReady(p)
    assert.equal(await p.locator(".report-detail-modal[open]").count(), 1)
    assert.equal(new URL(p.url()).pathname, "/admin/reports/91")
    assert.equal(await p.locator(".report-status-summary .report-content-badge").innerText(), "숨김")
    assert.equal(await p.locator(".report-status-summary .report-badge").innerText(), "접수")
    assert.equal(reads(s).length, 2)
    assert.equal(s.calls.filter(c => c.method === "POST" && c.path.includes("/reports/")).length, 0)
    assert(!s.calls.some(c => c.path === "/api/v1/admin/reports"))
    await button(p, "신고 상세 닫기").click(); await p.waitForURL(u => u.pathname === "/admin")
    assert.equal(await p.locator("dialog[open]").count(), 0)
  })

} finally {
  await fs.writeFile(`${out}/results.json`, JSON.stringify(results, null, 2))
  await browser.close()
}
console.log(`${results.filter(r => r.passed).length}/${results.length} passed`)
if (results.some(r => !r.passed)) process.exitCode = 1
