import assert from "node:assert/strict"
import http from "node:http"
import { mkdir } from "node:fs/promises"
import path from "node:path"

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8443"
const api = process.env.API_URL ?? "http://localhost:8082"
const output = process.env.HOME_ARTIFACT_DIR
const mockPort = Number(process.env.COMMUNITY_SSR_MOCK_PORT)
const postsPath = "/api/v1/community/posts/feed"
const empty = { items: [], hasNext: false, nextCursor: null }
let server, fail = false, feed = empty
const homeReturn = "/#home-recent-posts"
const weeklyReturn = "/?tab=weekly#home-recent-posts"
const weeklyPath = "/api/v1/community/posts/weekly-best"
let weeklyItems = [], weeklyFailed = false
const weeklyRequests = []
const requests = []
const story = {
  id: 2, type: "GENERAL", topic: "DAILY", title: "첫 출근에 동료에게 전하고 싶었던 이야기 ".repeat(4).trim(),
  author: { memberId: 1, nickname: "이움회원" }, createdAt: "2026-10-01T05:00:00Z",
  contentPreview: "오늘은   함께 일하는 동료와\n\n첫 회의에서 나눴던 이야기를 소개합니다. ".repeat(4),
  liked: false, likeCount: 0, commentCount: 1,
}

const blankStories = [4, 3, 2, 1].map(id => ({
  ...story, id, title: "미리보기 없는 글 " + id, contentPreview: id % 2 ? "" : "  ",
}))

async function checkHomeReturn(page, section, width) {
  const back = page.getByRole("link", { name: "← 홈으로 돌아가기", exact: true })
  assert.equal(await back.getAttribute("href"), homeReturn)
  await back.click()
  await page.waitForURL(new URL(homeReturn, base).href)
  const first = section.getByRole("listitem").first()
  await first.waitFor()
  await page.waitForFunction(() => {
    const heading = document.getElementById("community-heading").getBoundingClientRect()
    const first = document.querySelector("#home-recent-posts li")?.getBoundingClientRect()
    const header = document.querySelector(".iyum-header").getBoundingClientRect()
    return heading.top >= Math.max(0, header.bottom) && first && first.bottom <= innerHeight && first.top >= heading.bottom
  })
  await capture(page, "return-" + width)
}

async function capture(page, name) {
  if (output) await page.screenshot({ path: path.join(output, name + ".png") })
}

const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome" })
try {
  if (output) await mkdir(output, { recursive: true })
  if (mockPort) {
    server = http.createServer((req, res) => {
      const url = new URL(req.url, base)
      const detail = /^\/api\/v1\/community\/posts\/(\d+)(\/comments)?$/.exec(url.pathname)
      if (detail) {
        const item = (feed === empty ? [story] : feed.items).find(p => p.id === Number(detail[1]))
        if (!item) { res.writeHead(404).end(); return }
        const body = detail[2] ? empty : { ...item, content: item.title, updatedAt: item.createdAt }
        res.writeHead(200, { "Content-Type": "application/json" }).end(JSON.stringify(body))
        return
      }
      if (url.pathname === weeklyPath) {
        weeklyRequests.push(url.search)
        res.writeHead(weeklyFailed ? 503 : 200, { "Content-Type": "application/json" })
        res.end(JSON.stringify(weeklyFailed ? {} : { items: weeklyItems }))
        return
      }
      if (url.pathname !== postsPath) { res.writeHead(404).end(); return }
      requests.push([...url.searchParams].sort())
      res.writeHead(fail ? 503 : 200, { "Content-Type": "application/json" })
      res.end(JSON.stringify(fail ? {} : feed))
    })
    await new Promise((resolve, reject) => server.once("error", reject).listen(mockPort, "127.0.0.1", resolve))
  }
  for (const width of [1440, 390]) {
    const mobile = width === 390
    const context = await browser.newContext({ viewport: { width, height: mobile ? 844 : 900 }, hasTouch: mobile })
    const page = await context.newPage()
    const section = page.getByRole("region", { name: "커뮤니티", exact: true })
    const input = page.getByRole("searchbox", { name: "커뮤니티 게시글 검색" })
    if (mockPort) {
      fail = false
      feed = empty
      await page.goto(base)
      await section.getByText("아직 등록된 게시글이 없어요.").waitFor()
      fail = true
      await page.reload()
      await section.getByText("최근 글을 불러오지 못했어요.").waitFor()
      await input.fill("보청기")
      let release
      const waiting = new Promise(resolve => { release = resolve })
      let attempts = 0
      await page.route("**" + postsPath + "?*", async route => {
        requests.push([...new URL(route.request().url()).searchParams].sort())
        attempts++
        await waiting
        await route.fulfill({ json: { ...empty, items: [story, { ...story, id: 1, contentPreview: "   " }] } })
      })
      await section.getByRole("button", { name: "다시 시도" }).click()
      await section.getByText("최근 글을 불러오는 중입니다.").waitFor()
      assert.equal(await input.inputValue(), "보청기")
      release()
      const rows = section.getByRole("listitem")
      const preview = rows.first().locator(".iyum-post-preview")
      await preview.waitFor()
      assert.equal(await preview.textContent(), story.contentPreview)
      assert.equal(await rows.nth(1).locator(".iyum-post-preview").count(), 0)
      assert.ok(await preview.evaluate(el => el.scrollWidth > el.clientWidth && getComputedStyle(el).textOverflow === "ellipsis"))
      const copy = await rows.first().locator(".iyum-post-copy").boundingBox()
      const counts = await rows.first().locator(".iyum-post-reactions").boundingBox()
      assert.ok(mobile ? counts.y >= copy.y + copy.height - 1 : counts.x >= copy.x + copy.width - 1)
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      await section.scrollIntoViewIfNeeded()
      await capture(page, "long-" + width)
      assert.equal(attempts, 1)
      assert.equal(await input.inputValue(), "보청기")
      if (mobile) await preview.tap()
      else await preview.click()
      await page.waitForURL(url => url.pathname === "/community/posts/" + story.id)
      await page.getByRole("heading", { name: story.title.trim(), exact: true }).first().waitFor()
      fail = false
      feed = { ...empty, items: blankStories }
      await page.goto(base)
      for (const [i, item] of blankStories.entries()) {
        await rows.nth(3).waitFor()
        assert.equal(await rows.count(), 4)
        assert.equal(await section.locator(".iyum-post-preview").count(), 0)
        await rows.nth(i).getByRole("link").click()
        await page.waitForURL(url => url.pathname === "/community/posts/" + item.id)
        await page.getByRole("heading", { name: item.title, exact: true }).first().waitFor()
        await page.getByRole("link", { name: "← 홈으로 돌아가기" }).click()
      }
      const recent = section.getByRole("link", { name: "최근 글", exact: true })
      const weekly = section.getByRole("link", { name: "주간 인기", exact: true })
      const weeklyRows = section.getByRole("list", { name: "주간 인기 게시글 목록" }).getByRole("listitem")
      weeklyItems = [story, blankStories[0], blankStories[1], blankStories[3]].map((post, index) => ({ ...post, likeCount: 4 - index }))
      const before = weeklyRequests.length
      await page.goto(base)
      await rows.first().waitFor()
      assert.equal(weeklyRequests.length, before)
      assert.match(await rows.first().innerText(), /미리보기 없는 글 4/)
      const toolbar = section.getByRole("navigation", { name: "홈 커뮤니티 목록" })
      await toolbar.evaluate(el => window.scrollBy({
        top: el.getBoundingClientRect().top - innerHeight / 2, behavior: "instant",
      }))
      const toolbarTop = () => toolbar.evaluate(el => el.getBoundingClientRect().top)
      for (const [tab, href, list] of [
        [weekly, "/?tab=weekly", "주간 인기 게시글 목록"],
        [recent, "/", "최근 게시글 목록"],
      ]) {
        await tab.focus()
        const beforeTop = await toolbarTop()
        assert.ok(Math.abs(beforeTop - (mobile ? 844 : 900) / 2) <= 2)
        await page.keyboard.press("Enter")
        await page.waitForURL(new URL(href, base).href)
        await section.getByRole("list", { name: list }).getByRole("listitem").first().waitFor()
        const afterTop = await toolbarTop()
        console.log(width, href, "탭 상단 px", beforeTop, "→", afterTop)
        assert.ok(Math.abs(afterTop - beforeTop) <= 2)
        assert.equal(await tab.getAttribute("aria-current"), "page")
        assert.ok(await tab.evaluate(el => document.activeElement === el))
      }
      await weekly.click()
      await weeklyRows.nth(3).waitFor()
      assert.equal(await weekly.getAttribute("aria-current"), "page")
      await page.reload()
      await weeklyRows.nth(3).waitFor()
      assert.equal(page.url(), new URL("/?tab=weekly", base).href)
      assert.deepEqual(await weeklyRows.locator(".iyum-post-title").allTextContents(),
        weeklyItems.map((item, i) => (i + 1) + "위 " + item.title))
      assert.equal(weeklyRequests.at(-1), "?size=4")
      const title = await weeklyRows.first().locator("strong").boundingBox()
      const reactions = await weeklyRows.first().locator(".iyum-post-reactions").boundingBox()
      assert.ok(mobile ? reactions.y >= title.y + title.height - 1 : reactions.x >= title.x + title.width - 1)
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
      await section.scrollIntoViewIfNeeded()
      await capture(page, "weekly-" + width)
      const open = weeklyRows.first().getByRole("link")
      for (const back of ["browser", "link"]) {
        await open.scrollIntoViewIfNeeded()
        const fromUrl = page.url()
        const fromY = await page.evaluate(() => scrollY)
        assert.ok(fromY > 0)
        if (mobile) await open.tap()
        else await open.click()
        const home = page.getByRole("link", { name: "← 홈으로 돌아가기", exact: true })
        assert.equal(await home.getAttribute("href"), weeklyReturn)
        if (back === "browser") await page.goBack()
        else await home.click()
        await page.waitForURL(back === "browser" ? fromUrl : new URL(weeklyReturn, base).href)
        await weeklyRows.first().waitFor()
        assert.equal(await weekly.getAttribute("aria-current"), "page")
        if (back === "browser") {
          const restoredY = await page.evaluate(() => scrollY)
          console.log(width, "뒤로 가기", fromUrl, fromY, "→", restoredY)
          assert.ok(Math.abs(fromY - restoredY) <= 2)
        } else assert.ok(await section.locator("h2").evaluate(el => {
          const rect = el.getBoundingClientRect()
          return rect.top >= 0 && rect.bottom <= innerHeight
        }))
      }
      weeklyItems = weeklyItems.slice(0, 1)
      await page.reload()
      await weeklyRows.first().waitFor()
      assert.equal(await weeklyRows.count(), 1)
      weeklyItems = []
      await page.reload()
      await section.getByText("최근 7일 동안 작성된 글 중 아직 좋아요를 받은 글이 없어요.").waitFor()
      weeklyFailed = true
      await page.reload()
      const retry = section.getByRole("button", { name: "다시 시도" })
      await retry.waitFor()
      let finish
      const delayedRetry = new Promise(resolve => { finish = resolve })
      await page.route("**" + weeklyPath + "?*", async route => {
        assert.equal(new URL(route.request().url()).search, "?size=4")
        await delayedRetry
        await route.fulfill({ json: { items: [{ ...story, likeCount: 1 }] } })
      })
      const pending = page.waitForRequest(req => new URL(req.url()).pathname === weeklyPath)
      await retry.click()
      await pending
      await section.getByText("주간 인기 글을 불러오는 중입니다.").waitFor()
      await recent.click()
      await section.getByRole("list", { name: "최근 게시글 목록" }).waitFor()
      finish()
      await page.waitForLoadState("networkidle")
      assert.equal(page.url(), new URL("/", base).href)
      assert.equal(await recent.getAttribute("aria-current"), "page")
      assert.match(await rows.first().innerText(), /미리보기 없는 글 4/)
      await page.goto(new URL(weeklyReturn, base).href)
      await page.unroute("**" + weeklyPath + "?*")
      await page.route("**" + weeklyPath + "?*", route => route.fulfill({ json: { items: [{ ...story, likeCount: 1 }] } }))
      await retry.click()
      await weeklyRows.first().waitFor()
      assert.equal(await weeklyRows.count(), 1)
      weeklyFailed = false
      console.log(width, "주간 인기 4건·1건·0건, 순위, 오류 복구, 탭 전환, 상세 왕복 통과")
      console.log(width, "모의 빈 목록·실패·재시도 로딩·복구 및 검색 입력 보존 통과")
    } else {
      const response = await fetch(new URL(postsPath + "?size=4", api))
      assert.equal(response.status, 200)
      const { items } = await response.json()
      assert.ok(items.length > 0 && items.length <= 4, "실제 상세 이동에는 공개 게시글이 필요합니다.")
      await page.goto(base)
      const rows = section.getByRole("listitem")
      await rows.first().waitFor()
      const expected = items.map(({ id, title, author, createdAt, contentPreview, likeCount, commentCount }) => ({
        href: "/community/posts/" + id + "?from=" + encodeURIComponent(homeReturn), title, nickname: author.nickname, createdAt,
        preview: contentPreview.trim() ? contentPreview : null,
        likes: "좋아요 " + likeCount + "개", comments: "댓글 " + commentCount + "개",
      }))
      const actual = await rows.evaluateAll(elements => elements.map(el => ({
        href: el.querySelector("a:only-child")?.getAttribute("href"),
        title: el.querySelector("strong")?.textContent,
        nickname: el.querySelector(".iyum-post-byline > span")?.textContent,
        createdAt: el.querySelector("time")?.dateTime,
        preview: el.querySelector(".iyum-post-preview")?.textContent ?? null,
        likes: el.querySelector(".iyum-post-reactions > :first-child")?.textContent,
        comments: el.querySelector(".iyum-post-reactions > :last-child")?.textContent,
      })))
      assert.deepEqual(actual, expected)
      await section.scrollIntoViewIfNeeded()
      await capture(page, "recent-" + width)
      for (const selector of [".iyum-post-byline > span", ".iyum-post-preview", ".iyum-post-reactions"]) {
        const i = selector === ".iyum-post-preview" ? items.findIndex(item => item.contentPreview.trim()) : 0
        if (i < 0) continue
        const row = rows.nth(i)
        const link = row.getByRole("link")
        const href = await link.getAttribute("href")
        const title = await row.locator("strong").textContent()
        if (mobile) await row.locator(selector).tap()
        else await row.locator(selector).click()
        await page.waitForURL(new URL(href, base).href)
        await page.getByRole("heading", { name: title, exact: true }).first().waitFor()
        await page.goBack()
      }
      await rows.first().getByRole("link").focus()
      const index = Math.min(1, items.length - 1)
      if (index) await page.keyboard.press("Tab")
      assert.equal(await page.locator(":focus").getAttribute("href"), expected[index].href)
      await page.keyboard.press("Enter")
      await page.waitForURL(new URL(expected[index].href, base).href)
      await page.reload()
      await checkHomeReturn(page, section, width)
      const separate = await page.context().newPage()
      await separate.goto(new URL(expected[index].href, base).href)
      await checkHomeReturn(separate, separate.getByRole("region", { name: "커뮤니티", exact: true }), width + "-new-tab")
      await separate.close()
      await rows.first().getByRole("link").click()
      await page.waitForURL(new URL(expected[0].href, base).href)
      await page.goBack()
      assert.equal(await section.getByRole("link", { name: "전체 보기" }).count(), 1)
      assert.equal(await section.getByRole("link", { name: "질문 남기기" }).count(), 0)
      await section.getByRole("link", { name: "전체 보기" }).click()
      await page.waitForURL(url => url.pathname === "/community")
      const write = page.getByRole("link", { name: "＋ 글쓰기" })
      assert.equal(new URL(await write.getAttribute("href"), base).pathname, "/community/posts/create")
      await write.click()
      await page.waitForURL(url => ["/community/posts/create", "/login"].includes(url.pathname))
      await page.goto(base)
      await input.fill("보청기")
      await input.press("Enter")
      await page.waitForURL(url => url.pathname === "/search" && url.searchParams.get("q") === "보청기")
      const detailPath = "/community/posts/" + items[0].id
      for (const suffix of ["", "?from=unknown"]) {
        await page.goto(base + detailPath + suffix)
        const back = page.locator(".community-back")
        const topic = "/community/topics/" + items[0].topic.toLowerCase().replaceAll("_", "-")
        assert.equal(await back.getAttribute("href"), topic)
        await back.click()
        await page.waitForURL(new URL(topic, base).href)
        const topicRow = page.locator("[data-feed-id]").first()
        await topicRow.locator("h3 a").click()
        const topicBack = page.locator(".community-back")
        assert.equal(await topicBack.getAttribute("href"), topic)
        await topicBack.click()
        await page.waitForURL(new URL(topic, base).href)
      }
      const rankedResponse = await fetch(new URL(weeklyPath + "?size=4", api))
      assert.equal(rankedResponse.status, 200)
      const ranked = (await rankedResponse.json()).items
      await page.goto(base)
      await section.getByRole("link", { name: "주간 인기" }).click()
      const rankedRows = section.getByRole("listitem")
      if (ranked.length) {
        await section.getByRole("list", { name: "주간 인기 게시글 목록" }).waitFor()
        assert.deepEqual(await rankedRows.locator("strong").allTextContents(),
          ranked.map((item, index) => (index + 1) + "위 " + item.title))
        assert.deepEqual(await rankedRows.locator("a").evaluateAll(links => links.map(a => a.pathname)),
          ranked.map(item => "/community/posts/" + item.id))
      } else await section.getByText("최근 7일 동안 작성된 글 중 아직 좋아요를 받은 글이 없어요.").waitFor()
      await section.scrollIntoViewIfNeeded()
      await capture(page, "weekly-live-" + width)
      console.log(width, "실제 최근 글 / 주간 인기 ID", items.map(item => item.id), ranked.map(item => item.id))
      console.log(width, "실제 API와 행 일치, 댓글 클릭·상세·키보드·전체 보기·홈 검색 이동 통과", JSON.stringify(items.map(({ id, likeCount, commentCount }) => ({ id, likeCount, commentCount }))))
    }
    await context.close()
  }
  if (mockPort) {
    assert.ok(requests.length >= 6)
    for (const params of requests) assert.deepEqual(params, [["size", "4"]])
  }
} finally {
  await browser.close()
  if (server) await new Promise(resolve => server.close(resolve))
}
