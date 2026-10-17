// 명시적 opt-in: 로컬 dev API/DB에 테스트 회원·게시글·댓글을 생성한다. 새 댓글과 게시글만 UI에서 논리 삭제한다.
// 쿠키·비밀번호·CSRF·인증 토큰·메일 원문은 출력하거나 파일에 저장하지 않는다.
import assert from "node:assert/strict"
import fs from "node:fs/promises"
import os from "node:os"
import path from "node:path"
if (process.env.IYUM_RUN_LOCAL_COMMENTS_TEST !== "true") {
  console.log(
    "SKIP: set IYUM_RUN_LOCAL_COMMENTS_TEST=true to create one local account and post (then soft-delete that post)",
  )
  process.exit(0)
}
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8443"
const api = process.env.API_BASE_URL ?? "http://127.0.0.1:8082"
const mailpit = process.env.MAILPIT_BASE_URL ?? "http://127.0.0.1:8025"
for (const value of [base, api, mailpit])
  assert.equal(new URL(value).hostname, "127.0.0.1", "local test only")
const output =
  process.env.COMMUNITY_LOCAL_ARTIFACT_DIR ??
  (await fs.mkdtemp(path.join(os.tmpdir(), "iyum-community-local-")))
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({
  headless: true,
  channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome",
})
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  locale: "ko-KR",
  reducedMotion: "reduce",
})
const page = await context.newPage()
page.setDefaultTimeout(15000)
const checks = [],
  created = {},
  errors = []
page.on("pageerror", (error) => errors.push(error.message))
const suffix = Date.now().toString(36)
const email = `iyum-community-${suffix}@example.test`,
  nickname = `C_${suffix}`
const password = "LocalOnly" + crypto.randomUUID() + "7!"
const textTitle = `[로컬 UI 검증 ${suffix}] 커뮤니티 작성과 조회 확인`
const textBody =
  "이 게시글은 로컬 연동 검증용 예시입니다.\n\n줄바꿈과 공백을 보존합니다.  \n실제 복지·기기 정보가 아닙니다."
const listPath = "/community/posts?type=GENERAL&topic=WELFARE_LIFE"
const createPath =
  "/community/posts/create?type=GENERAL&topic=WELFARE_LIFE&from=" +
  encodeURIComponent(listPath)
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
let current
async function check(name, fn) {
  current = name
  await fn()
  checks.push(name)
  console.log("PASS REAL", name)
}
async function waitFor(predicate) {
  const end = Date.now() + 15000
  while (Date.now() < end) {
    const value = await predicate()
    if (value) return value
    await delay(150)
  }
  throw Error("Condition not reached")
}
// Only reads and navigates around the post created by this run.
async function verifyListCount(expected) {
  const response = await page.request.get(api + "/api/v1/community/posts?type=GENERAL&topic=WELFARE_LIFE&page=0&size=20")
  assert.equal(response.status(), 200)
  const own = (await response.json()).items.find(p => p.id === created.postId)
  assert(own, "new test post must be on latest page")
  assert.equal(own.commentCount, expected)
  await page.locator(".community-back").click()
  const link = page.getByRole("link", { name: textTitle, exact: true })
  await link.waitFor()
  assert(await link.locator("xpath=ancestor::li").getByText(`댓글 ${expected}`, { exact: true }).isVisible())
  await shot(`real-list-count-${expected}`)
  await link.click()
  await page.locator(".community-post-body").waitFor()
}
async function shot(name) {
  await page.evaluate(() => document.fonts.ready)
  await page.screenshot({
    path: path.join(output, name + ".png"),
    fullPage: true,
  })
}
try {
  await check(
    "실제 회원가입 PENDING→Mailpit 인증→로그인 수동 수행",
    async () => {
      await page.setViewportSize({ width: 1440, height: 1000 })
      await page.goto(base + "/register")
      await page.getByRole("checkbox", { name: /이용약관/ }).check()
      await page.getByRole("checkbox", { name: /개인정보 수집/ }).check()
      await page.getByRole("button", { name: "다음", exact: true }).click()
      await page.waitForURL("**/register/info")
      await page.locator("input[autocomplete=email]").fill(email)
      await page
        .locator("input[autocomplete=new-password]")
        .nth(0)
        .fill(password)
      await page
        .locator("input[autocomplete=new-password]")
        .nth(1)
        .fill(password)
      await page.locator("input[autocomplete=nickname]").fill(nickname)
      const signup = page.waitForResponse(api + "/api/v1/auth/signup")
      await page.getByRole("button", { name: "가입하기", exact: true }).click()
      assert.equal((await signup).status(), 201)
      await page.waitForURL("**/register/verify")
      Object.assign(created, { email, nickname, memberStatus: "PENDING" })
      const messages = await waitFor(async () => {
        const res = await fetch(
          mailpit + "/api/v1/search?query=" + encodeURIComponent("to:" + email),
        )
        const messages = (await res.json()).messages ?? []
        return messages.length ? messages : null
      })
      const message = await (
        await fetch(mailpit + "/api/v1/message/" + messages[0].ID)
      ).json()
      const token = String(message.Text).match(/token=([A-Za-z0-9_-]{43})/)?.[1]
      assert.ok(token)
      await page.goto(base + "/register/verify?token=" + token)
      await page
        .getByRole("button", { name: "이메일 인증하기", exact: true })
        .click()
      await page.waitForURL("**/login")
      await page
        .getByText("이메일 인증이 완료되었습니다. 로그인해 주세요.", {
          exact: true,
        })
        .waitFor()
      created.memberStatus = "ACTIVE"
      created.mailCount = messages.length
      await page.goto(base + "/login?from=" + encodeURIComponent(createPath))
      await page.locator("input[name=email]").fill(email)
      await page.locator("input[name=password]").fill(password)
      await page
        .getByRole("main")
        .getByRole("button", { name: "로그인", exact: true })
        .click()
      await page.waitForURL((url) => url.pathname === "/community/posts/create")
      await page.getByRole("form", { name: "게시글 작성" }).waitFor()
      const me = await page.request.get(api + "/api/v1/auth/me")
      assert.equal(me.status(), 200)
      created.memberId = (await me.json()).memberId
    },
  )
  await check(
    "실제 CSRF+쿠키 글 작성 201·중복 제출 방지·상세 저장 내용",
    async () => {
      await page
        .getByPlaceholder("제목을 입력하세요.")
        .fill("  " + textTitle + "  ")
      await page.getByPlaceholder("내용을 입력하세요.").fill(textBody)
      for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 })
        await shot(`real-create-${width}`)
      }
      let count = 0
      const listener = (request) => {
        if (
          request.method() === "POST" &&
          request.url() === api + "/api/v1/community/posts"
        )
          count++
      }
      page.on("request", listener)
      const postResponse = page.waitForResponse(
        (response) =>
          response.url() === api + "/api/v1/community/posts" &&
          response.request().method() === "POST",
      )
      await page.getByRole("form", { name: "게시글 작성" }).evaluate((form) => {
        form.requestSubmit()
        form.requestSubmit()
      })
      const response = await postResponse
      assert.equal(response.status(), 201)
      const body = await response.json()
      created.postId = body.id
      assert(Number.isSafeInteger(body.id))
      await page.waitForURL(
        (url) => url.pathname === "/community/posts/" + body.id,
      )
      await page.locator(".community-post-body").waitFor()
      assert.equal(
        await page.locator(".community-post-body").textContent(),
        textBody,
      )
      assert.equal(count, 1)
      page.off("request", listener)
      const detail = await (
        await page.request.get(api + "/api/v1/community/posts/" + body.id)
      ).json()
      assert.equal(detail.title, textTitle)
      assert.equal(detail.author.memberId, created.memberId)
      await page.reload()
      await page.locator(".community-post-body").waitFor()
      assert.equal(
        await page.locator(".community-post-body").textContent(),
        textBody,
      )
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 })
        assert(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
        )
        await shot(`real-detail-${width}`)
      }
    },
  )

  const resource =
    api + "/api/v1/community/posts/" + created.postId + "/comments"
  await check("실제 브라우저 Origin·preflight 허용", async () => {
    // Explicit preflight read-only evidence; writes below use browser fetch via actual UI.
    for (const [url, method] of [
      [resource, "POST"],
      [resource + "/1", "PUT"],
      [resource + "/1", "DELETE"],
    ]) {
      const r = await fetch(url, {
        method: "OPTIONS",
        headers: {
          Origin: base,
          "Access-Control-Request-Method": method,
          "Access-Control-Request-Headers": "content-type,x-csrf-token",
        },
      })
      assert.equal(r.status, 200)
      assert.equal(r.headers.get("access-control-allow-origin"), base)
      assert.equal(r.headers.get("access-control-allow-credentials"), "true")
    }
  })
  await check(
    "실제 댓글 POST 201 → PUT 204 → SSR 새로고침 → DELETE 204",
    async () => {
      await verifyListCount(0)
      const text = "  [로컬 댓글 검증 예시] 😀\n줄바꿈 보존"
      await page.locator("#comment-draft").fill(text)
      const response = page.waitForResponse(
        (r) => r.url() === resource && r.request().method() === "POST",
      )
      await page.getByRole("button", { name: "댓글 등록", exact: true }).click()
      const r = await response
      assert.equal(r.status(), 201)
      assert.equal(r.headers()["access-control-allow-origin"], base)
      created.commentId = (await r.json()).commentId
      const row = page.locator("#comment-" + created.commentId)
      await row.waitFor()
      assert.equal(
        await row.locator(".community-comment-body").textContent(),
        text,
      )
      await verifyListCount(1)
      await row.getByRole("button", { name: "댓글 수정", exact: true }).click()
      await page
        .locator("#comment-edit-" + created.commentId)
        .fill(text + "\n수정 확인")
      const update = page.waitForResponse(
        (r) =>
          r.url() === resource + "/" + created.commentId &&
          r.request().method() === "PUT",
      )
      await page.getByRole("button", { name: "댓글 저장", exact: true }).click()
      assert.equal((await update).status(), 204)
      await page.getByText(text + "\n수정 확인", { exact: true }).waitFor()
      await verifyListCount(1)
      await page.reload()
      await row.waitFor()
      assert.equal(
        await row.locator(".community-comment-body").textContent(),
        text + "\n수정 확인",
      )
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 950 })
        assert(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
        )
        await shot("real-comments-" + width)
      }
      await row.getByRole("button", { name: "댓글 삭제", exact: true }).click()
      await page.keyboard.press("Escape")
      assert.equal(
        await page.evaluate(() => document.activeElement.textContent),
        "댓글 삭제",
      )
      await row.getByRole("button", { name: "댓글 삭제", exact: true }).click()
      const removal = page.waitForResponse(
        (r) =>
          r.url() === resource + "/" + created.commentId &&
          r.request().method() === "DELETE",
      )
      await page
        .getByRole("button", { name: "댓글 삭제하기", exact: true })
        .click()
      assert.equal((await removal).status(), 204)
      await page.getByText("댓글을 삭제했습니다.", { exact: true }).waitFor()
      assert.equal(await row.count(), 0)
      created.commentStatus = "soft-deleted"
      await verifyListCount(0)
      await page.reload()
      await page
        .getByText("아직 댓글이 없습니다. 첫 이야기를 나눠 주세요.", {
          exact: true,
        })
        .waitFor()
    },
  )
  await check("이번 검증용 게시글만 UI에서 논리 삭제", async () => {
    await page.getByRole("button", { name: "삭제", exact: true }).click()
    const removal = page.waitForResponse(
      (r) =>
        r.url() === api + "/api/v1/community/posts/" + created.postId &&
        r.request().method() === "DELETE",
    )
    await page.getByRole("button", { name: "삭제하기", exact: true }).click()
    assert.equal((await removal).status(), 204)
    created.postStatus = "soft-deleted"
  })
} catch (error) {
  console.error(
    "FAIL REAL",
    current,
    String(error.message).replace(/token=[^\s"']+/g, "token=[REDACTED]"),
  )
  process.exitCode = 1
} finally {
  await fs.writeFile(
    path.join(output, "real-comments-results.json"),
    JSON.stringify({ checks, created, failed: !!process.exitCode }, null, 2),
  )
  await browser.close()
}
