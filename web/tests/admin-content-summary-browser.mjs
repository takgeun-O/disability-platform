import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8478",
  out =
    process.env.MODAL_ARTIFACT_DIR ??
    "/private/tmp/iyum-content-summary-frontend/browser"
await fs.mkdir(out, { recursive: true })
const browser = await chromium.launch({ channel: "chrome", headless: true }),
  results = []
const stamp = "2026-11-01T03:04:05Z",
  member = {
    memberId: 7,
    email: "mock@example.test",
    nickname: "검증 관리자",
    role: "ADMIN",
  }
const author = { memberId: 8, nickname: "검증 작성자", withdrawn: false }
const post = (id, status = "VISIBLE") => ({
  postId: id,
  type: "GENERAL",
  topic: "DAILY",
  title: `보청기 적응 경험을 나눕니다 ${id}`,
  content:
    `새 보청기를 사용한 지 한 달이 되었습니다. 🦻\n조용한 곳에서는 대화가 한결 편해졌지만, 사람이 많은 장소에서는 아직 적응이 필요합니다.\n\n` +
    "착용 시간을 조금씩 늘리며 느낀 점을 기록합니다. 여러분의 경험도 알려 주세요.\n".repeat(
      32,
    ) +
    "마지막 게시글 문장입니다.",
  contentPreview: "미리보기",
  author,
  status,
  createdAt: stamp,
  updatedAt: stamp,
  hiddenAt: status === "HIDDEN" ? stamp : null,
  deletedAt: status === "DELETED" ? stamp : null,
})
const log = (id, targetType = "POST") => ({
  logId: id,
  actorMemberId: 8,
  currentTarget: null,
  targetType,
  targetId: targetType === "POST" ? 101 : 900,
  action: "HIDE",
  reasonType: id === 201 ? "LEGACY" : "OTHER",
  reasonDetail: id === 201 ? null : `상세 사유 ${id}`,
  reason: `저장 원문 ${id}\n` + "긴 사유 ".repeat(30),
  createdAt: stamp,
})
const comment = (id, postId = 101) => ({
  commentId: id,
  postId,
  postStatus: "HIDDEN",
  content:
    `저도 비슷한 경험이 있어요. 🦻\n처음에는 조용한 곳에서 대화를 연습했습니다.\n\n` +
    "꾸준히 착용하면서 조금씩 적응하고 있습니다.\n".repeat(32) +
    "공백없는긴문자열".repeat(50) +
    "\n끝까지 읽어 주셔서 감사합니다.",
  author: { ...author, nickname: "탈퇴한 회원", withdrawn: true },
  status: "VISIBLE",
  createdAt: stamp,
  updatedAt: stamp,
  contentUpdatedAt: null,
  hiddenAt: null,
  deletedAt: null,
})
const btn = (p, name) => p.getByRole("button", { name, exact: true })
const selectRow = (p, name) => p.getByRole("row", { name, exact: true })
const ready = (p) => p.locator('.admin-results[aria-busy="false"]').waitFor()
const preview = (p) =>
  p.locator('.admin-preview-post[aria-busy="false"]').waitFor()
const gate = () => {
  let arrive, release
  return {
    arrived: new Promise((r) => (arrive = r)),
    wait: new Promise((r) => (release = r)),
    arrive: () => arrive(),
    release: () => release(),
  }
}
async function setup(width = 1440) {
  const context = await browser.newContext({
      viewport: { width, height: 1000 },
      timezoneId: "America/Los_Angeles",
    }),
    p = await context.newPage(),
    s = {
      member: { ...member },
      calls: [],
      errors: [],
      posts: [post(101), post(102, "HIDDEN"), post(103, "DELETED")],
      logs: [log(201), log(202), log(203, "COMMENT")],
      detail: null,
      list: null,
      commentDetail: null,
      commentList: null,
      write: null,
      comments: Array.from({ length: 21 }, (_, i) => comment(900 + i)),
    }
  p.setDefaultTimeout(8000)
  p.on("pageerror", (e) => s.errors.push(e.message))
  await p.addInitScript(() => {
    const f = window.fetch
    window.__masterParsed = 0
    window.fetch = async (u, o) => {
      const r = await f(
        u,
        String(u).includes("/api/v1/admin/") ? { ...o, signal: undefined } : o,
      )
      if (String(u).includes("/api/v1/admin/") && r.status !== 200)
        setTimeout(() => window.__masterParsed++, 0)
      if (String(u).includes("/api/v1/admin/")) {
        const json = r.json.bind(r)
        r.json = async () => {
          const v = await json()
          if (String(u).includes("/content/summary"))
            window.__summaryParsed = (window.__summaryParsed ?? 0) + 1
          setTimeout(() => window.__masterParsed++, 0)
          return v
        }
      }
      return r
    }
  })
  await context.route("http://127.0.0.1:8082/**", async (r) => {
    const u = new URL(r.request().url()),
      path = u.pathname,
      method = r.request().method()
    s.calls.push({
      path,
      search: u.search,
      method,
      payload: method === "POST" ? r.request().postDataJSON() : null,
    })
    if (method === "OPTIONS")
      return r.fulfill({
        status: 204,
        headers: {
          "access-control-allow-origin": base,
          "access-control-allow-credentials": "true",
          "access-control-allow-methods": "GET, POST, OPTIONS",
          "access-control-allow-headers": "content-type,x-csrf-token",
        },
      })
    if (path === "/api/v1/admin/content/summary")
      return s.summary ? s.summary(r) : r.fulfill({ json: summary() })
    if (path.endsWith("/auth/me"))
      return r.fulfill(
        s.member
          ? { json: s.member }
          : { status: 401, json: { code: "AUTHENTICATION_REQUIRED" } },
      )
    if (path.endsWith("/csrf"))
      return r.fulfill({
        json: { headerName: "X-CSRF-TOKEN", token: "mock-only" },
      })
    if (path.endsWith("/login")) {
      s.member = { ...member, memberId: 99, nickname: "다른 관리자" }
      return r.fulfill({ json: s.member })
    }
    if (path.endsWith("/logout")) {
      s.member = null
      return r.fulfill({ status: 204 })
    }
    const single = path.match(/^\/api\/v1\/admin\/comments\/(\d+)$/)
    if (single) {
      const id = Number(single[1])
      return s.commentDetail
        ? s.commentDetail(r, id)
        : r.fulfill({
            json: {
              ...(s.comments.find((c) => c.commentId === id) ?? comment(id)),
              postId: 101,
              postStatus: s.posts[0].status,
            },
          })
    }
    const commentWrite = path.match(
      /^\/api\/v1\/admin\/posts\/(\d+)\/comments\/(\d+)\/(hide|restore)$/,
    )
    if (commentWrite && method === "POST") {
      if (s.write) return s.write(r, commentWrite)
      const c = s.comments.find((c) => c.commentId === Number(commentWrite[2]))
      c.status = commentWrite[3] === "hide" ? "HIDDEN" : "VISIBLE"
      c.hiddenAt = c.status === "HIDDEN" ? stamp : null
      return r.fulfill({
        json: {
          postId: Number(commentWrite[1]),
          commentId: c.commentId,
          hidden: c.status === "HIDDEN",
          hiddenAt: c.hiddenAt,
          changed: true,
        },
      })
    }
    const write = path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/(hide|restore)$/)
    if (write && method === "POST") {
      if (s.write) return s.write(r, write)
      const item = s.posts.find((x) => x.postId === Number(write[1]))
      item.status = write[2] === "hide" ? "HIDDEN" : "VISIBLE"
      item.hiddenAt = item.status === "HIDDEN" ? stamp : null
      return r.fulfill({
        json: {
          postId: item.postId,
          hidden: item.status === "HIDDEN",
          hiddenAt: item.hiddenAt,
          changed: true,
        },
      })
    }
    if (
      path === "/api/v1/admin/posts" ||
      path === "/api/v1/admin/operation-logs"
    ) {
      const page = Number(u.searchParams.get("page") ?? 0),
        items = path.endsWith("operation-logs")
          ? s.logs
          : s.posts.filter((x) =>
              u.searchParams.getAll("statuses").includes(x.status),
            )
      const data = {
        items,
        page,
        size: 20,
        totalElements: 41,
        totalPages: 3,
        hasNext: page < 2,
        hasPrevious: page > 0,
      }
      return s.list ? s.list(r, u, data) : r.fulfill({ json: data })
    }
    const detail = path.match(/^\/api\/v1\/admin\/posts\/(\d+)$/)
    if (detail) {
      const id = Number(detail[1])
      return s.detail
        ? s.detail(r, id)
        : r.fulfill({ json: s.posts.find((x) => x.postId === id) ?? post(id) })
    }
    const comments = path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/comments$/)
    if (comments) {
      const postId = Number(comments[1]),
        page = Number(u.searchParams.get("page") ?? 0),
        statuses = u.searchParams.getAll("statuses"),
        keyword = u.searchParams.get("keyword") ?? ""
      const all = s.comments.filter(
          (c) => statuses.includes(c.status) && c.content.includes(keyword),
        ),
        totalPages = Math.ceil(all.length / 20)
      const data = {
        postId,
        postStatus:
          s.posts.find((x) => x.postId === postId)?.status ?? "VISIBLE",
        items: all.slice(page * 20, page * 20 + 20),
        page,
        size: 20,
        totalElements: all.length,
        totalPages,
        hasNext: page + 1 < totalPages,
        hasPrevious: page > 0,
      }
      return s.commentList
        ? s.commentList(r, u, data)
        : r.fulfill({ json: data })
    }
    return r.fulfill({ status: 404, json: { code: "NOT_FOUND" } })
  })
  return { context, p, s }
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
  }
}

const select = async (p, id, logs = false) =>
  selectRow(p, logs ? `기록 #${id} 상세` : `게시글 #${id} 미리보기`)
    .locator(".admin-badge")
    .click()

const dialog = (p) => p.getByRole("dialog")
const noOverflow = async (p) =>
  assert(
    await p.evaluate(
      () =>
        document.documentElement.scrollWidth <= innerWidth &&
        Array.from(
          document.querySelectorAll(".admin-content-dialog,.admin-dialog-body"),
        ).every((e) => e.scrollWidth <= e.clientWidth + 1),
    ),
  )
const close = async (p) => {
  await btn(p, "닫기").click()
  await dialog(p).waitFor({ state: "detached" })
}

const summary = (n = 7) => ({
  queriedAt: "2026-11-16T15:00:00Z",
  baseDate: "2026-11-17",
  counts: {
    todayPostCount: n,
    todayCommentCount: 1011,
    hiddenPostCount: 3,
    hiddenCommentCount: 5,
  },
})
const countReads = (s) =>
  s.calls.filter((c) => c.path === "/api/v1/admin/content/summary")
const summaryReady = (p) =>
  p.locator('.content-summary [aria-busy="false"]').waitFor()
const count = (p) =>
  p.locator(".content-summary .dashboard-count dd:first-of-type")
for (const width of [1920, 1440, 390, 320])
  await run(`요약 네 수치·한국 날짜·반응형·키보드 ${width}`, async () => {
    const { p, context, s } = await setup(width)
    try {
      await p.goto(base + "/admin/posts")
      await ready(p)
      await summaryReady(p)
      assert.deepEqual(await count(p).allTextContents(), [
        "7건",
        "1,011건",
        "3건",
        "5건",
      ])
      assert(
        (await p.locator(".content-summary-dates").textContent()).includes(
          "2026.11.17",
        ),
      )
      assert(
        (await p.locator(".content-summary-dates").textContent()).includes(
          "한국 시간",
        ),
      )
      await noOverflow(p)
      assert.equal(
        await p
          .locator(
            ".content-summary .dashboard-count a, .content-summary .dashboard-count button",
          )
          .count(),
        0,
      )
      await btn(p, "요약 새로고침").focus()
      await p.keyboard.press("Enter")
      await p.waitForFunction(
        () =>
          document
            .querySelector(".content-summary button")
            ?.getAttribute("aria-disabled") === "false",
      )
      assert.equal(countReads(s).length, 2)
      await p.screenshot({ path: `${out}/normal-${width}.png`, fullPage: true })
    } finally {
      await context.close()
    }
  })
await run(
  "최초 로딩·실패·0과 재조회 실패를 구분하고 목록 조건은 독립적이다",
  async () => {
    const { p, context, s } = await setup()
    const g = gate()
    try {
      s.summary = async (r) => {
        g.arrive()
        await g.wait
        await r.fulfill({ status: 500, json: { code: "ERROR" } })
      }
      await p.goto(base + "/admin/posts")
      await g.arrived
      await ready(p)
      assert.deepEqual(await count(p).allTextContents(), ["—", "—", "—", "—"])
      await p.screenshot({ path: `${out}/loading.png` })
      g.release()
      await p.locator('.content-summary [role="alert"]').waitFor()
      await p.screenshot({ path: `${out}/initial-error.png` })
      s.summary = (r) =>
        r.fulfill({
          json: {
            ...summary(),
            counts: {
              todayPostCount: 0,
              todayCommentCount: 0,
              hiddenPostCount: 0,
              hiddenCommentCount: 0,
            },
          },
        })
      await btn(p, "요약 다시 조회").click()
      await summaryReady(p)
      assert.deepEqual(await count(p).allTextContents(), [
        "0건",
        "0건",
        "0건",
        "0건",
      ])
      const before = countReads(s).length
      await p.getByLabel("게시글 제목 검색", { exact: true }).fill("검증")
      await btn(p, "검색").click()
      await ready(p)
      await p.getByLabel("작성 시각 정렬").selectOption("OLDEST")
      await ready(p)
      await btn(p, "다음").click()
      await ready(p)
      assert.equal(countReads(s).length, before)
      const url = p.url()
      s.summary = (r) => r.fulfill({ status: 500, json: { code: "ERROR" } })
      await btn(p, "요약 새로고침").click()
      await p
        .getByText(
          "최신 요약 조회에 실패했습니다. 이전 수치와 조회 시각을 표시합니다.",
        )
        .waitFor()
      assert.deepEqual(await count(p).allTextContents(), [
        "0건",
        "0건",
        "0건",
        "0건",
      ])
      assert.equal(p.url(), url)
      await p.screenshot({ path: `${out}/refresh-error.png` })
    } finally {
      g.release()
      await context.close()
    }
  },
)
for (const kind of ["POST", "COMMENT"])
  for (const action of ["hide", "restore"])
    await run(
      `조치 ${kind} ${action} 성공 후 요약 갱신과 선택 보존`,
      async () => {
        const { p, context, s } = await setup()
        try {
          s.posts[0].status =
            kind === "POST" && action === "restore" ? "HIDDEN" : "VISIBLE"
          s.comments[0].status = action === "restore" ? "HIDDEN" : "VISIBLE"
          await p.goto(base + "/admin/posts")
          await ready(p)
          await summaryReady(p)
          await select(p, 101)
          await preview(p)
          const before = countReads(s).length
          if (kind === "COMMENT") await btn(p, "댓글 관리로 이동").click()
          s.summary = (r) => r.fulfill({ json: summary(99) })
          const operation = action === "hide" ? "숨김 처리" : "숨김 해제"
          await btn(
            p,
            kind === "POST" ? `게시글 ${operation}` : `댓글 #900 ${operation}`,
          ).click()
          await p
            .getByLabel("처리 사유 (필수)", { exact: true })
            .selectOption(
              action === "hide" ? "SPAM_ADVERTISING" : "NO_VIOLATION",
            )
          await btn(p, `${operation} 실행`).click()
          await p
            .locator(".admin-action-workspace")
            .waitFor({ state: "detached" })
          await summaryReady(p)
          assert.equal(await count(p).first().textContent(), "99건")
          assert.equal(countReads(s).length, before + 1)
          assert(p.url().includes("selected=101"))
          assert.equal(s.calls.filter((c) => c.method === "POST").length, 1)
          await close(p)
        } finally {
          await context.close()
        }
      },
    )
await run(
  "성공 조치 뒤 요약 실패는 GET만 재시도하고 지연 응답을 차단한다",
  async () => {
    const { p, context, s } = await setup()
    const g = gate()
    try {
      await p.goto(base + "/admin/posts")
      await ready(p)
      await summaryReady(p)
      await select(p, 101)
      await preview(p)
      s.summary = async (r) => {
        g.arrive()
        await g.wait
        await r.fulfill({ json: summary(1) })
      }
      // 요약 갱신 중에도 콘텐츠 작업을 진행할 수 있다. 조치 성공이 새 요청을 소유한다.
      await p.evaluate(() =>
        document.querySelector(".content-summary button").click(),
      )
      await g.arrived
      s.summary = (r) => r.fulfill({ status: 500, json: { code: "ERROR" } })
      await btn(p, "게시글 숨김 처리").click()
      await p
        .getByLabel("처리 사유 (필수)", { exact: true })
        .selectOption("SPAM_ADVERTISING")
      await btn(p, "숨김 처리 실행").click()
      await p.locator(".admin-action-workspace").waitFor({ state: "detached" })
      await summaryReady(p)
      const parsed = await p.evaluate(() => window.__summaryParsed ?? 0)
      g.release()
      await p.waitForFunction((n) => (window.__summaryParsed ?? 0) > n, parsed)
      await close(p)
      assert.equal(await count(p).first().textContent(), "7건")
      await p.locator('.content-summary [role="alert"]').waitFor()
      s.summary = (r) => r.fulfill({ json: summary(55) })
      await btn(p, "요약 다시 조회").click()
      await summaryReady(p)
      assert.equal(await count(p).first().textContent(), "55건")
      assert.equal(s.calls.filter((c) => c.method === "POST").length, 1)
    } finally {
      g.release()
      await context.close()
    }
  },
)
for (const status of [401, 403])
  await run(`요약 ${status}는 이전 관리자 콘텐츠를 제거한다`, async () => {
    const { p, context, s } = await setup()
    try {
      await p.goto(base + "/admin/posts")
      await ready(p)
      await summaryReady(p)
      s.summary = (r) =>
        r.fulfill({
          status,
          json: {
            code: status === 401 ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED",
          },
        })
      if (status === 401) s.member = null
      await btn(p, "요약 새로고침").click()
      await p.locator(".content-summary").waitFor({ state: "detached" })
      assert.equal(await p.locator(".admin-table-wrap").count(), 0)
    } finally {
      await context.close()
    }
  })
await run("조치 실패는 요약을 갱신하지 않고 입력 사유를 유지한다", async () => {
  const { p, context, s } = await setup()
  try {
    await p.goto(base + "/admin/posts")
    await ready(p)
    await summaryReady(p)
    await select(p, 101)
    await preview(p)
    const before = countReads(s).length
    s.write = (r) =>
      r.fulfill({
        status: 400,
        json: {
          code: "INVALID_REASON",
          message: "처리 사유를 확인해 주세요.",
          fieldErrors: [],
        },
      })
    await btn(p, "게시글 숨김 처리").click()
    await p
      .getByLabel("처리 사유 (필수)", { exact: true })
      .selectOption("OTHER")
    await p
      .getByLabel("상세 사유 (필수)", { exact: true })
      .fill("입력 중인 검토 사유")
    await btn(p, "숨김 처리 실행").click()
    await p.locator('.admin-action-form [role="alert"]').waitFor()
    assert.equal(countReads(s).length, before)
    assert.equal(
      await p.getByLabel("상세 사유 (필수)", { exact: true }).inputValue(),
      "입력 중인 검토 사유",
    )
  } finally {
    await context.close()
  }
})
for (const width of [1440, 1920, 390, 320])
  await run(`긴 제목에도 작성 시각과 상태가 셀 안에 표시된다 ${width}`, async () => {
    const { p, context, s } = await setup(width)
    try {
      s.posts[0].title = "긴 한글 제목의 작성 시각을 확인합니다 ".repeat(12)
      s.posts[1].title = "공백없는긴한글제목".repeat(25)
      await p.goto(base + "/admin/posts")
      await ready(p)
      await summaryReady(p)
      await p.screenshot({ path: `${out}/time-${width}.png`, fullPage: true })
      const geometry = await p.locator(".admin-master-list tbody time").evaluateAll((times) =>
        times.map((time) => {
          const range = document.createRange()
          range.selectNodeContents(time)
          const cell = time.closest("td")
          const bounds = [cell]
          for (let parent = cell.parentElement; parent; parent = parent.parentElement) {
            if (/hidden|clip|auto|scroll/.test(getComputedStyle(parent).overflowX)) bounds.push(parent)
          }
          const rects = [...range.getClientRects()]
          return {
            text: time.textContent,
            dateTime: time.dateTime,
            inside: bounds.every((element) => {
              const b = element.getBoundingClientRect()
              return rects.every((r) => r.left >= b.left - 1 && r.right <= b.right + 1)
            }),
            cellWidth: cell.getBoundingClientRect().width,
            lineWidths: rects.map((r) => r.width),
          }
        }),
      )
      await fs.writeFile(`${out}/time-${width}.json`, JSON.stringify(geometry, null, 2))
      assert.equal(geometry.length, 3)
      for (const item of geometry) {
        assert.equal(item.text, "2026.11.01. 12:04:05")
        assert.equal(item.dateTime, stamp)
        assert(item.inside, "날짜·시간의 실제 텍스트 경계가 셀과 clipping 영역 안에 있어야 한다")
      }
      const badgesInside = await p.locator(".admin-master-list tbody .admin-badge").evaluateAll((badges) =>
        badges.every((badge) => {
          const b = badge.getBoundingClientRect()
          const cell = badge.closest("td").getBoundingClientRect()
          return b.left >= cell.left && b.right <= cell.right
        }),
      )
      assert(badgesInside, "긴 제목이 상태 배지를 셀 밖으로 밀어내지 않아야 한다")
      await noOverflow(p)
      const row = selectRow(p, "게시글 #101 미리보기")
      await row.focus()
      await p.keyboard.press("Enter")
      await preview(p)
      await close(p)
      assert(await row.evaluate((e) => e === document.activeElement))
    } finally {
      await context.close()
    }
  })
await fs.writeFile(`${out}/results.json`, JSON.stringify(results, null, 2))
await browser.close()
if (results.some((r) => !r.passed)) process.exitCode = 1
