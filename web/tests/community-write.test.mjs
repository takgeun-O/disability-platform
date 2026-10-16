import assert from "node:assert/strict"
import { test } from "node:test"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const { changePost, CommunityApiError } = await loadTypeScript(
  "../src/lib/community-api.ts",
  import.meta.url,
)
const values = {
  type: "REVIEW",
  topic: "DAILY",
  title: "  수정 제목  ",
  content: "  내용\n두 줄  ",
}
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  })
async function run(handler, fn) {
  const fetch = globalThis.fetch,
    base = process.env.NEXT_PUBLIC_API_BASE_URL
  process.env.NEXT_PUBLIC_API_BASE_URL = "https://api.example.test"
  globalThis.fetch = handler
  try {
    await fn()
  } finally {
    globalThis.fetch = fetch
    if (base === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL
    else process.env.NEXT_PUBLIC_API_BASE_URL = base
  }
}
for (const method of ["PUT", "DELETE"]) {
  test(`${method}: session/CSRF, exact body and 204 without JSON parsing`, async () => {
    let reads = 0,
      writes = 0
    await run(
      async (url, options) => {
        if (url.endsWith("/csrf")) {
          reads++
          return json({ headerName: "X-CSRF-TOKEN", token: "test-only" })
        }
        writes++
        assert.equal(url, "https://api.example.test/api/v1/community/posts/7")
        assert.equal(options.method, method)
        assert.equal(options.credentials, "include")
        assert.equal(options.headers["X-CSRF-TOKEN"], "test-only")
        assert.equal(options.cache, "no-store")
        if (method === "PUT")
          assert.deepEqual(JSON.parse(options.body), {
            ...values,
            title: "수정 제목",
          })
        else assert.equal(options.body, undefined)
        const response = new Response(null, {
          status: 204,
          headers: { "Content-Type": "application/json" },
        })
        response.json = () => {
          throw new Error("204 must not parse JSON")
        }
        return response
      },
      async () =>
        assert.equal(
          await changePost("7", method, method === "PUT" ? values : undefined),
          true,
        ),
    )
    assert.equal(reads, 1)
    assert.equal(writes, 1)
  })
  for (const [status, code] of [
    [401, "AUTHENTICATION_REQUIRED"],
    [403, "ACCESS_DENIED"],
    [403, "POST_WRITE_NOT_ALLOWED"],
    [403, "CSRF_TOKEN_INVALID"],
    [404, "POST_NOT_FOUND"],
    [400, "VALIDATION_FAILED"],
  ]) {
    test(`${method}: ${code} preserves fields, no automatic replay`, async () => {
      let writes = 0
      await run(
        async (url) =>
          url.endsWith("/csrf")
            ? json({ headerName: "X-CSRF-TOKEN", token: "test-only" })
            : (writes++,
              json(
                {
                  code,
                  message: "private",
                  fieldErrors:
                    code === "VALIDATION_FAILED"
                      ? [{ field: "title", message: "제목은 필수입니다." }]
                      : [],
                },
                status,
              )),
        async () => {
          await assert.rejects(
            changePost("7", method, values),
            (e) =>
              e instanceof CommunityApiError &&
              e.code === code &&
              !e.uncertain &&
              (code !== "VALIDATION_FAILED" ||
                e.fieldErrors.title === "제목은 필수입니다."),
          )
        },
      )
      assert.equal(writes, 1)
    })
  }
  for (const [name, response] of [
    ["HTML", () => new Response("<html/>")],
    ["unexpected200", () => json({})],
    [
      "brokenJSON",
      () =>
        new Response("{", { headers: { "Content-Type": "application/json" } }),
    ],
    [
      "network",
      () => {
        throw new TypeError("network")
      },
    ],
    ["server", () => json({}, 500)],
  ])
    test(`${method}: ${name} uncertain without retry`, async () => {
      let writes = 0
      await run(
        async (url) =>
          url.endsWith("/csrf")
            ? json({ headerName: "X-CSRF-TOKEN", token: "test-only" })
            : (writes++, response()),
        async () => {
          await assert.rejects(
            changePost("7", method, values),
            (e) => e.uncertain && !e.message.includes("등록"),
          )
        },
      )
      assert.equal(writes, 1)
    })
}
test("leave during CSRF: no mutation; CSRF failure: no mutation", async () => {
  let writes = 0,
    current = true
  await run(
    async (url) => {
      if (url.endsWith("/csrf")) {
        current = false
        return json({ headerName: "X-CSRF-TOKEN", token: "test-only" })
      }
      writes++
    },
    async () =>
      assert.equal(await changePost("7", "PUT", values, () => current), false),
  )
  assert.equal(writes, 0)
  await run(
    async () => {
      throw new TypeError("offline")
    },
    async () =>
      assert.rejects(
        changePost("7", "DELETE"),
        (e) => e.code === "CSRF_PREPARATION_FAILED" && !e.uncertain,
      ),
  )
})
