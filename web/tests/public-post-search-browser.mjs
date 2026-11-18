// 실제 모드는 공개 GET만 사용한다. 모의 모드는 오류·경계 입력의 재현용이다.
import assert from "node:assert/strict"
import { mkdir } from "node:fs/promises"
import path from "node:path"
import http from "node:http"

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8443"
const output = process.env.SEARCH_ARTIFACT_DIR
const live = process.env.SEARCH_LIVE === "1"
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome" })
const keyword = "보청기"
const posts = "/api/v1/community/posts"

const review = {
  id: 6, type: "REVIEW", topic: "HEARING_AID", title: "보청기 적응 경험을 나눠주세요",
  content: "첫 착용 경험을 나눕니다.", author: { memberId: 1, nickname: "예시회원" },
  createdAt: "2026-11-18T00:00:00Z", updatedAt: "2026-11-18T00:00:00Z",
  liked: false, likeCount: 0,
}
const bodyOnlyPost = { ...review, id: 2, title: "상담 전에 챙길 질문", content: "상담에서 보청기 설정을 확인했습니다." }
const details = new Map([review, bodyOnlyPost].map(post => [posts + "/" + post.id, post]))
let server
if (!live) {
  const port = Number(process.env.COMMUNITY_SSR_MOCK_PORT)
  assert.ok(port, "모의 상세를 위해 Next의 IYUM_API_INTERNAL_URL과 같은 모의 포트가 필요합니다.")
  server = http.createServer((req, res) => {
    const url = new URL(req.url, "http://localhost")
    const body = details.get(url.pathname) ?? (
      details.has(url.pathname.replace(/\/comments$/, "")) ? { items: [], hasNext: false, nextCursor: null } : null)
    res.writeHead(body ? 200 : 404, { "Content-Type": "application/json" })
    res.end(JSON.stringify(body))
  })
  await new Promise((resolve, reject) => server.once("error", reject).listen(port, "127.0.0.1", resolve))
}

try {
  if (output) await mkdir(output, { recursive: true })
  for (const width of [1440, 390]) {
    const page = await browser.newPage({ hasTouch: width === 390, viewport: { width, height: width === 390 ? 844 : 900 } })
    const input = page.getByRole("searchbox", { name: "커뮤니티 게시글 검색" })
    const calls = []
    const requests = []
    const literal = "aB  %_.()+"
    const bodyMatch = "…상담에서 보청기 설정을 확인했습니다.\n\n새 보청기 착용 후기…"
    const bothMatch = "제 보청기 사용 기록입니다."
    if (!live) await page.route("**/api/v1/auth/me", route => route.fulfill({
      status: 401, json: { code: "AUTHENTICATION_REQUIRED", message: "", fieldErrors: [] },
    }))
    if (!live) for (const [path, post] of details) {
      await page.route("**" + path, route => route.fulfill({ json: post }))
    }
    let fail = false
    let release
    if (!live) await page.route("**/api/v1/community/posts?*", async route => {
      const params = new URL(route.request().url()).searchParams
      const q = params.get("q")
      calls.push(q)
      requests.push(params.toString())
      assert.ok(q)
      if (q === "느린 검색") await new Promise(resolve => { release = resolve })
      if (fail || q === "서버 거절") return route.fulfill({
        status: fail ? 503 : 400, json: { code: fail ? "ERROR" : "INVALID_SEARCH_KEYWORD", message: "", fieldErrors: [] },
      })
      const index = Number(params.get("page")), size = Number(params.get("size"))
      const count = params.get("type") === "GENERAL" ? 0 : [literal, "café", "i"].includes(q) ? 1 : q === keyword ? size + 1 : 0
      const items = Array.from({ length: Math.max(0, Math.min(size, count - index * size)) }, (_, i) => ({
        id: index * size + i + 1, type: params.get("type") || "REVIEW", topic: "HEARING_AID",
        title: q === literal ? "AB  %_.()+와 ab  %_.()+" : ["café", "i"].includes(q) ? "상담 후기" : ["보청기보청기 첫 착용 후기", "상담 전에 챙길 질문", "보청기와 첫 출근", "회의에서 잘 듣는 방법", "착용 후 달라진 일상", "보청기 적응 경험을 나눠주세요"][index * size + i],
        matchedContentExcerpt: q === "café" ? "CAFÉ" : q === "i" ? "İ" :
          q === literal ? "…AB  %_.()+…" : [null, bodyMatch, bothMatch, bodyMatch, bodyMatch, null][index * size + i],
        author: { memberId: 1, nickname: "예시회원" }, likeCount: 0, liked: false,
        commentCount: 0, createdAt: "2026-11-18T00:00:00Z",
      }))
      await route.fulfill({ json: { items, page: index, size, totalElements: count,
        totalPages: Math.ceil(count / size), hasNext: (index + 1) * size < count } })
    })
    const submit = async q => { await input.fill(q); await input.press("Enter") }
    const checkEmpty = async (q, typeLabel = "") => {
      const title = typeLabel ? `${typeLabel}에서 ‘${q}’에 해당하는 글이 없어요.` : `‘${q}’에 해당하는 게시글이 없어요.`
      const section = page.getByRole("region", { name: title, exact: true })
      await section.getByRole("heading", { name: title, exact: true }).waitFor()
      assert.deepEqual(await section.getByRole("status").allTextContents(), [title])
      assert.equal(await input.inputValue(), q)
      if (typeLabel) await section.getByRole("link", { name: "전체 유형에서 검색" }).waitFor()
      else await section.getByText("다른 검색어나 더 짧은 단어로 검색해 보세요.").waitFor()
    }
    const shot = async name => {
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      if (output && (!live || name === "results")) await page.screenshot({ path: path.join(output, name + "-" + width + ".png") })
    }
    await page.goto(base)
    await input.waitFor()
    await submit("  ")
    await page.getByRole("alert").filter({ hasText: "검색어를 입력해주세요" }).waitFor()
    await input.fill(keyword)
    await shot("home")
    const first = page.waitForResponse(r => new URL(r.url()).pathname === posts)
    await page.getByRole("button", { name: "검색", exact: true }).click()
    const data = await (await first).json()
    assert.equal(data.page, 0)
    await page.getByText("총 " + data.totalElements.toLocaleString("ko-KR") + "건", { exact: true }).waitFor()
    await page.getByRole("link", { name: data.items[0].title, exact: true }).waitFor()
    for (const [index, item] of data.items.entries()) {
      const row = page.locator("main ol li").nth(index)
      assert.equal(await row.getByRole("link").count(), 1)
      const rowLink = row.getByRole("link", { name: item.title, exact: true })
      const paragraphs = rowLink.locator("p")
      assert.equal(await paragraphs.count(), item.matchedContentExcerpt === null ? 1 : 2)
      if (item.matchedContentExcerpt !== null) {
        assert.equal(await paragraphs.first().textContent(), item.matchedContentExcerpt)
      }
    }
    await shot("results")
    const typeNav = page.getByRole("navigation", { name: "글 유형" })
    const filteredResponse = page.waitForResponse(r => {
      const url = new URL(r.url())
      return url.pathname === posts && url.searchParams.get("type") === "REVIEW"
    })
    await typeNav.getByRole("link", { name: "경험·후기" }).press("Enter")
    const filtered = await (await filteredResponse).json()
    assert.equal(filtered.page, 0)
    assert.ok(filtered.items.every(item => item.type === "REVIEW"))
    await page.getByText("총 " + filtered.totalElements.toLocaleString("ko-KR") + "건", { exact: true }).waitFor()
    assert.equal(await typeNav.getByRole("link", { name: "경험·후기" }).getAttribute("aria-current"), "page")
    assert.deepEqual(await page.locator("main ol li a > span:not([aria-hidden])").allTextContents(), filtered.items.map(item => item.title))
    if (live) console.log("실제 경험·후기 응답 대조", width, filtered.totalElements, "건")
    if (!live) {
      await input.fill("다음 검색어")
      assert.deepEqual(await page.locator("main ol li").first().locator("a > span mark").allTextContents(), [keyword, keyword])
      assert.equal(await page.locator("main ol li").first().getByText(/본문에/).count(), 0)
      const rows = page.locator("main ol li")
      assert.equal(await rows.first().locator("a > p").count(), 1)
      assert.equal(await rows.nth(1).locator("a > span mark").count(), 0)
      assert.equal(await rows.nth(1).locator("p").first().textContent(), bodyMatch)
      assert.equal(await rows.nth(1).locator("p").first().innerText(), bodyMatch.replace("\n\n", " "))
      assert.deepEqual(await rows.nth(1).locator("p mark").allTextContents(), [keyword, keyword])
      assert.deepEqual(await rows.nth(2).locator("a > span mark").allTextContents(), [keyword])
      assert.equal(await rows.nth(2).locator("p").first().textContent(), bothMatch)
      assert.deepEqual(await rows.nth(2).locator("p mark").allTextContents(), [keyword])
      assert.equal(await rows.getByText("본문에 ‘보청기’ 포함").count(), 0)
      await input.fill(keyword)
      await shot("review-results")
      const bodyLink = rows.nth(1).getByRole("link", { name: bodyOnlyPost.title, exact: true })
      const reviewUrl = page.url()
      for (const action of ["강조 단어", "여백", "화살표", "키보드"]) {
        await bodyLink.evaluate(el => el.scrollIntoView({ block: "center" }))
        if (action === "키보드") {
          await rows.first().getByRole("link").focus()
          await page.keyboard.press("Tab")
          assert.ok(await bodyLink.evaluate(el => el === document.activeElement && el.matches(":focus-visible")))
          await shot("row-focus")
        }
        const before = await page.evaluate(() => scrollY)
        assert.ok(before > 0)
        if (action === "키보드") await page.keyboard.press("Enter")
        else {
          const target = action === "화살표" ? bodyLink.locator('[aria-hidden="true"]').filter({ hasText: /^›$/ })
            : action === "여백" ? bodyLink : bodyLink.locator("p mark").first()
          const options = action === "여백" ? { position: { x: 3, y: 3 } } : {}
          if (width === 390) await target.tap(options)
          else await target.click(options)
        }
        await page.getByRole("heading", { name: bodyOnlyPost.title, exact: true }).waitFor()
        assert.equal(new URL(page.url()).pathname, "/community/posts/2")
        if (action === "키보드") await page.getByRole("link", { name: /검색 결과로 돌아가기/ }).first().click()
        else await page.goBack()
        await bodyLink.waitFor()
        assert.equal(page.url(), reviewUrl)
        await page.waitForFunction(y => Math.abs(scrollY - y) <= 1, before)
      }
    }
    const filteredSecond = filtered.hasNext
    if (!filteredSecond) {
      await typeNav.getByRole("link", { name: "전체", exact: true }).click()
      await page.getByText("총 " + data.totalElements + "건", { exact: true }).waitFor()
    }
    const second = page.waitForResponse(r => new URL(r.url()).pathname === posts && new URL(r.url()).searchParams.get("page") === "1")
    await page.getByRole("link", { name: "다음 페이지" }).click()
    const next = await (await second).json()
    const resultUrl = page.url()
    assert.equal(new URL(resultUrl).searchParams.get("page"), "2")
    assert.equal(new URL(resultUrl).searchParams.get("q"), keyword)
    const title = next.items[0].title
    const link = page.getByRole("link", { name: title, exact: true })
    await page.reload()
    await link.waitFor()
    assert.equal(new URL(resultUrl).searchParams.get("type"), filteredSecond ? "REVIEW" : null)
    for (const back of ["뒤로", "링크"]) {
      await link.scrollIntoViewIfNeeded()
      const before = await page.evaluate(() => scrollY)
      // 링크의 왼쪽 위 안쪽 여백을 눌러 제목만 일치하는 모의 행도 연다.
      const padding = { position: { x: 3, y: 3 } }
      if (width === 390) await link.tap(padding)
      else await link.click(padding)
      await page.getByRole("heading", { name: title, exact: true }).waitFor()
      if (back === "뒤로") await page.goBack()
      else await page.getByRole("link", { name: /검색 결과로 돌아가기/ }).first().click()
      await link.waitFor()
      assert.equal(page.url(), resultUrl)
      await page.waitForFunction(y => Math.abs(scrollY - y) <= 1, before)
      assert.equal(await typeNav.getByRole("link", { name: filteredSecond ? "경험·후기" : "전체", exact: true }).getAttribute("aria-current"), "page")
    }
    console.log(live ? "Spring" : "mock", "PASS 비로그인 검색·2페이지·새로고침·상세·두 복귀 경로", width, "전체", data.totalElements, "게시글 ID", next.items[0].id)
    if (!live) {
      assert.ok((await link.getAttribute("href")).includes("/community/posts/" + next.items[0].id + "?"))
      await typeNav.getByRole("link", { name: "일반" }).click()
      await checkEmpty(keyword, "일반")
      await shot("empty-type")
      assert.equal(new URLSearchParams(requests.at(-1)).get("page"), "0")
      await page.getByRole("link", { name: "전체 유형에서 검색" }).click()
      await page.getByText("총 6건").waitFor()
      assert.deepEqual(Object.fromEntries(new URL(page.url()).searchParams), { q: keyword })
      await page.goto(base + "/search?" + new URLSearchParams({ q: keyword, page: "3" }))
      await page.getByText("이 페이지에 표시할 게시글이 없습니다.").waitFor()
      assert.equal(await page.getByRole("status").filter({ hasText: "총 6건" }).count(), 1)
      await page.getByRole("link", { name: "이전 페이지" }).click()
      await page.getByText("2페이지", { exact: true }).waitFor()
      await typeNav.getByRole("link", { name: "경험·후기" }).click()
      await page.waitForURL(url => url.searchParams.get("type") === "REVIEW" && !url.searchParams.has("page"))
      await page.getByText("총 6건").waitFor()
      await submit(literal)
      await page.getByText("총 1건").waitFor()
      assert.deepEqual(await page.locator("main ol a > span mark").allTextContents(), ["AB  %_.()+", "ab  %_.()+"])
      assert.equal(new URL(page.url()).searchParams.get("type"), "REVIEW")
      assert.deepEqual(await page.locator("main ol p mark").allTextContents(), ["AB  %_.()+"])
      await submit("café")
      await page.locator("main ol p mark").filter({ hasText: /^CAFÉ$/ }).waitFor()
      await submit("i")
      await page.getByText("İ", { exact: true }).waitFor()
      assert.equal(await page.locator("main ol mark").count(), 0)
      const special = "50% &  _보청기"
      await submit(" " + special + " ")
      await checkEmpty(special, "경험·후기")
      assert.equal(calls.at(-1), special)
      assert.equal(new URL(page.url()).searchParams.get("q"), special)
      await submit("😀".repeat(101))
      await page.getByRole("alert").filter({ hasText: "100자" }).waitFor()
      assert.equal(await input.inputValue(), "😀".repeat(101))
      await submit("😀".repeat(100))
      await page.waitForURL(url => url.searchParams.get("q") === "😀".repeat(100))
      await checkEmpty("😀".repeat(100), "경험·후기")
      assert.equal(calls.at(-1), "😀".repeat(100))
      fail = true
      await submit("재시도")
      await page.getByRole("button", { name: "다시 시도" }).waitFor()
      fail = false
      await page.getByRole("button", { name: "다시 시도" }).click()
      await checkEmpty("재시도", "경험·후기")
      assert.deepEqual(calls.slice(-2), ["재시도", "재시도"])
      assert.equal(requests.at(-1), requests.at(-2))
      await submit("서버 거절")
      await page.getByRole("alert").filter({ hasText: "100자" }).waitFor()
      assert.equal(await input.inputValue(), "서버 거절")
      assert.equal(await input.getAttribute("aria-invalid"), "true")
      assert.equal(await page.getByRole("button", { name: "다시 시도" }).count(), 0)
      const slow = page.waitForRequest(req => new URL(req.url()).searchParams.get("q") === "느린 검색" && new URL(req.url()).pathname === posts)
      await submit("느린 검색")
      await slow
      await page.getByText("검색 결과를 불러오는 중입니다.").waitFor()
      await submit(keyword)
      await page.getByText("총 6건").waitFor()
      release()
      await page.getByRole("link", { name: data.items[0].title, exact: true }).waitFor()
      await page.goto(base)
      await page.getByRole("link", { name: "보청기 지원", exact: true }).click()
      await checkEmpty("보청기 지원")
      assert.equal(calls.at(-1), "보청기 지원")
      await submit("ㅇㅋ")
      await checkEmpty("ㅇㅋ")
      await shot("empty-all")
      const before = calls.length
      await page.goto(base + "/search?q=++")
      await page.getByText("검색어를 입력해주세요", { exact: true }).waitFor()
      assert.equal(calls.length, before)
      console.log("PASS 모의 검색·페이지·빈 입력·인코딩·경계·오류·재시도·지연 응답·추천", width)
    }
    await page.close()
  }
} finally {
  await browser.close()
  if (server) await new Promise(resolve => server.close(resolve))
}
