import assert from "node:assert/strict"
import http from "node:http"
import { mkdir } from "node:fs/promises"
import path from "node:path"

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8443"
const api = process.env.API_URL ?? "http://localhost:8082"
const output = process.env.HOME_ARTIFACT_DIR
const mockPort = Number(process.env.COMMUNITY_SSR_MOCK_PORT)
const postsPath = "/api/v1/community/posts"
const empty = { items: [], page: 0, size: 4, totalElements: 0, totalPages: 0, hasNext: false }
let server, fail = false
const requests = []

async function capture(page, name) {
  if (output) await page.screenshot({ path: path.join(output, name + ".png") })
}

const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome" })
try {
  if (output) await mkdir(output, { recursive: true })
  if (mockPort) {
    server = http.createServer((req, res) => {
      const url = new URL(req.url, base)
      if (url.pathname !== postsPath) { res.writeHead(404).end(); return }
      requests.push([...url.searchParams].sort())
      res.writeHead(fail ? 503 : 200, { "Content-Type": "application/json" })
      res.end(JSON.stringify(fail ? {} : empty))
    })
    await new Promise((resolve, reject) => server.once("error", reject).listen(mockPort, "127.0.0.1", resolve))
  }
  for (const width of [1440, 390]) {
    const mobile = width === 390
    const page = await browser.newPage({ viewport: { width, height: mobile ? 844 : 900 }, hasTouch: mobile })
    const section = page.getByRole("region", { name: "커뮤니티", exact: true })
    const input = page.getByRole("searchbox", { name: "커뮤니티 게시글 검색" })
    if (mockPort) {
      fail = false
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
        await route.fulfill({ json: empty })
      })
      await section.getByRole("button", { name: "다시 시도" }).click()
      await section.getByText("최근 글을 불러오는 중입니다.").waitFor()
      assert.equal(await input.inputValue(), "보청기")
      release()
      await section.getByText("아직 등록된 게시글이 없어요.").waitFor()
      assert.equal(attempts, 1)
      assert.equal(await input.inputValue(), "보청기")
      console.log(width, "모의 빈 목록·실패·재시도 로딩·복구 및 검색 입력 보존 통과")
    } else {
      const response = await fetch(new URL(postsPath + "?page=0&size=4", api))
      assert.equal(response.status, 200)
      const { items } = await response.json()
      assert.ok(items.length > 0 && items.length <= 4, "실제 상세 이동에는 공개 게시글이 필요합니다.")
      await page.goto(base)
      const rows = section.getByRole("listitem")
      await rows.first().waitFor()
      const expected = items.map(({ id, title, commentCount }) => ({
        href: "/community/posts/" + id, title, comments: "댓글 " + commentCount,
      }))
      const actual = await rows.evaluateAll(elements => elements.map(el => ({
        href: el.querySelector("a:only-child")?.getAttribute("href"),
        title: el.querySelector("strong")?.textContent,
        comments: el.querySelector(".iyum-post-comments")?.textContent,
      })))
      assert.deepEqual(actual, expected)
      await section.scrollIntoViewIfNeeded()
      await capture(page, "recent-" + width)
      const count = rows.first().getByText(expected[0].comments, { exact: true })
      if (mobile) await count.tap()
      else await count.click()
      await page.waitForURL(url => url.pathname === expected[0].href)
      await page.getByRole("heading", { name: expected[0].title, exact: true }).first().waitFor()
      await page.goBack()
      await rows.first().getByRole("link").focus()
      const index = Math.min(1, items.length - 1)
      if (index) await page.keyboard.press("Tab")
      assert.equal(await page.locator(":focus").getAttribute("href"), expected[index].href)
      await page.keyboard.press("Enter")
      await page.waitForURL(url => url.pathname === expected[index].href)
      await page.goBack()
      await section.getByRole("link", { name: "전체 보기" }).click()
      await page.waitForURL(url => url.pathname === "/community")
      await page.goBack()
      await input.fill("보청기")
      await input.press("Enter")
      await page.waitForURL(url => url.pathname === "/search" && url.searchParams.get("q") === "보청기")
      console.log(width, "실제 API와 행 일치, 댓글 클릭·상세·키보드·전체 보기·홈 검색 이동 통과", JSON.stringify(actual))
    }
    await page.close()
  }
  if (mockPort) {
    assert.ok(requests.length >= 6)
    for (const params of requests) assert.deepEqual(params, [["page", "0"], ["size", "4"]])
  }
} finally {
  await browser.close()
  if (server) await new Promise(resolve => server.close(resolve))
}
