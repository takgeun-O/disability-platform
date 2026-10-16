// 명시적 opt-in: 로컬 dev API/DB에 테스트 회원 1명과 게시글 1건을 생성하며 이 게시글만 수정·논리 삭제한다.
// 쿠키·비밀번호·CSRF·인증 토큰·메일 원문은 출력하거나 파일에 저장하지 않는다.
import assert from "node:assert/strict"
import fs from "node:fs/promises"
import os from "node:os"
import path from "node:path"
if (process.env.IYUM_RUN_LOCAL_EDIT_DELETE_TEST !== "true") {
  console.log(
    "SKIP: set IYUM_RUN_LOCAL_EDIT_DELETE_TEST=true to create one local account and post (then soft-delete that post)",
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
  nickname = `UI_${suffix}`
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
async function listReady() {
  await page.waitForFunction(
    () =>
      document
        .querySelector(".community-list-column [aria-busy]")
        ?.getAttribute("aria-busy") === "false",
  )
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

  await check(
    "실제 PUT 204·수정 내용·새로고침·원래 주제 목록 반영",
    async () => {
      await page.getByRole("link", { name: "수정", exact: true }).click()
      await page
        .getByRole("form", { name: "게시글 수정", exact: true })
        .waitFor()
      assert.equal(
        await page.getByLabel("제목", { exact: true }).inputValue(),
        textTitle,
      )
      const title = textTitle + " 수정 완료"
      await page.getByLabel("제목", { exact: true }).fill(title)
      await page
        .getByLabel("내용", { exact: true })
        .fill(textBody + "\n수정 검증 추가")
      const updated = page.waitForResponse(
        (r) =>
          r.url() === api + "/api/v1/community/posts/" + created.postId &&
          r.request().method() === "PUT",
      )
      await page.getByRole("button", { name: "수정 저장", exact: true }).click()
      assert.equal((await updated).status(), 204)
      await page.waitForURL(
        (u) => u.pathname === "/community/posts/" + created.postId,
      )
      await page.getByRole("heading", { name: title, exact: true }).waitFor()
      await page.reload()
      await page.getByRole("heading", { name: title, exact: true }).waitFor()
      assert.equal(
        await page.locator(".community-post-body").textContent(),
        textBody + "\n수정 검증 추가",
      )
      await page.getByRole("link", { name: "← 목록으로", exact: true }).click()
      await listReady()
      await page.getByRole("link", { name: title, exact: true }).click()
      await page.locator(".community-post-body").waitFor()
    },
  )
  for (const [type, topic] of [["QUESTION", "WELFARE_LIFE"], ["QUESTION", "HEARING_AID"], ["REVIEW", "COCHLEAR_IMPLANT"]]) {
    await check(`실제 유형·주제 변경 ${type}/${topic} 및 목록 개수`, async () => {
      const get = async url => { const r = await page.request.get(api + url); assert.equal(r.status(), 200); return r.json() }
      const resource = "/api/v1/community/posts/" + created.postId
      const before = await get(resource)
      const oldQuery = `?type=${before.type}&topic=${before.topic}`
      const newQuery = `?type=${type}&topic=${topic}`
      const oldCount = (await get("/api/v1/community/posts" + oldQuery)).totalElements
      const newCount = (await get("/api/v1/community/posts" + newQuery)).totalElements
      await page.getByRole("link", { name: "수정", exact: true }).click()
      await page.getByRole("form", { name: "게시글 수정", exact: true }).waitFor()
      await page.getByLabel("글 유형", { exact: true }).selectOption(type)
      await page.getByLabel("주제", { exact: true }).selectOption(topic)
      const changed = page.waitForResponse(r => r.request().method() === "PUT" && new URL(r.url()).pathname === resource)
      await page.getByRole("button", { name: "수정 저장", exact: true }).click()
      assert.equal((await changed).status(), 204)
      await page.waitForURL(u => u.pathname === "/community/posts/" + created.postId)
      const labels = { QUESTION: "질문·답변", REVIEW: "경험·후기", WELFARE_LIFE: "복지·생활", HEARING_AID: "보청기", COCHLEAR_IMPLANT: "인공와우" }
      for (const label of [labels[type], labels[topic]]) await page.locator("article .community-tags").getByText(label, { exact: true }).waitFor()
      const updated = await get(resource)
      assert.equal(updated.type, type); assert.equal(updated.topic, topic)
      const oldList = await get("/api/v1/community/posts" + oldQuery)
      const newList = await get("/api/v1/community/posts" + newQuery)
      assert.equal(oldList.totalElements, oldCount - 1)
      assert.equal(newList.totalElements, newCount + 1)
      assert(!oldList.items.some(p => p.id === created.postId))
      assert(newList.items.some(p => p.id === created.postId))
      await page.reload(); await page.locator(".community-post-body").waitFor()
      await page.getByRole("link", { name: "수정", exact: true }).click()
      await page.getByRole("form", { name: "게시글 수정", exact: true }).waitFor()
      assert.equal(await page.getByLabel("글 유형", { exact: true }).inputValue(), type)
      assert.equal(await page.getByLabel("주제", { exact: true }).inputValue(), topic)
      await page.getByRole("button", { name: "취소", exact: true }).click()
      await page.waitForURL(u => u.pathname === "/community/posts/" + created.postId)
      const backLink = page.getByRole("link", { name: "← 목록으로", exact: true })
      assert.equal(await backLink.getAttribute("href"), listPath)
      await backLink.click(); await listReady()
      assert.equal(await page.getByRole("link", { name: textTitle + " 수정 완료", exact: true }).count(), 0)
      const slug = { WELFARE_LIFE: "welfare-life", HEARING_AID: "hearing-aid", COCHLEAR_IMPLANT: "cochlear-implant" }[topic]
      await page.goto(base + `/community/topics/${slug}?type=${type}`)
      await listReady()
      await page.getByRole("link", { name: textTitle + " 수정 완료", exact: true }).waitFor()
      // Keep original return context for the next mutation and deletion.
      await page.goto(base + "/community/posts/" + created.postId + "?from=" + encodeURIComponent(listPath))
      await page.locator(".community-post-body").waitFor()
    })
  }
  await check(
    "실제 DELETE 204·취소·목록 제외·상세404·재삭제/수정404",
    async () => {
      await page.getByRole("button", { name: "삭제", exact: true }).click()
      await page
        .getByRole("dialog")
        .getByRole("button", { name: "취소", exact: true })
        .click()
      assert.equal(
        (
          await page.request.get(
            api + "/api/v1/community/posts/" + created.postId,
          )
        ).status(),
        200,
      )
      await page.getByRole("button", { name: "삭제", exact: true }).click()
      const deleted = page.waitForResponse(
        (r) =>
          r.url() === api + "/api/v1/community/posts/" + created.postId &&
          r.request().method() === "DELETE",
      )
      await page
        .getByRole("dialog")
        .getByRole("button", { name: "삭제하기", exact: true })
        .click()
      assert.equal((await deleted).status(), 204)
      created.postStatus = "soft-deleted"
      await page.waitForURL((u) => u.pathname === "/community/posts")
      await listReady()
      assert.equal(
        await page
          .getByRole("link", { name: textTitle + " 수정 완료", exact: true })
          .count(),
        0,
      )
      assert.equal(
        (
          await page.request.get(
            api + "/api/v1/community/posts/" + created.postId,
          )
        ).status(),
        404,
      )
      for (const method of ["PUT", "DELETE"]) {
        const csrf = await (
          await page.request.get(api + "/api/v1/auth/csrf")
        ).json()
        const response = await page.request.fetch(
          api + "/api/v1/community/posts/" + created.postId,
          {
            method,
            headers: { [csrf.headerName]: csrf.token },
            ...(method === "PUT"
              ? {
                  data: {
                    type: "GENERAL",
                    topic: "WELFARE_LIFE",
                    title: textTitle,
                    content: textBody,
                  },
                }
              : {}),
          },
        )
        assert.equal(response.status(), 404)
        assert.equal((await response.json()).code, "POST_NOT_FOUND")
      }
    },
  )
} catch (error) {
  console.error(
    "FAIL REAL",
    current,
    String(error.message).replace(/token=[^\s"']+/g, "token=[REDACTED]"),
  )
  process.exitCode = 1
} finally {
  await fs.writeFile(
    path.join(output, "real-edit-delete-results.json"),
    JSON.stringify({ checks, created, failed: !!process.exitCode }, null, 2),
  )
  await browser.close()
}
