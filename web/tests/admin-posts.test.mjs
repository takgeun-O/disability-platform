import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const api = await loadTypeScript("../src/lib/admin-posts.ts", import.meta.url)
const item = {
  postId: 1,
  type: "GENERAL",
  topic: "DAILY",
  title: "검증",
  contentPreview: "본문",
  author: { memberId: 7, nickname: "탈퇴한 회원", withdrawn: true },
  status: "DELETED",
  createdAt: "2026-11-01T00:00:00.123456Z",
  updatedAt: "2026-11-01T00:00:00Z",
  hiddenAt: null,
  deletedAt: "2026-11-01T01:00:00Z",
}
const page = {
  items: [item],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
  hasPrevious: false,
}
test("admin query uses Java strip and 100 code points, not UTF16 units", () => {
  assert.equal(api.normalizeAdminKeyword("　 검색어　"), "검색어")
  assert.equal(
    api.normalizeAdminKeyword("\u00a0검색\u00a0"),
    "\u00a0검색\u00a0",
  )
  assert.equal(api.adminKeywordError("😀".repeat(100)), "")
  assert(api.adminKeywordError("가".repeat(101)))
})
test("admin DTO validates status, author and pagination without hiding malformed values", () => {
  assert(api.isAdminPage(page))
  for (const status of ["VISIBLE", "HIDDEN", "DELETED"])
    assert(api.isAdminPage({ ...page, items: [{ ...item, status }] }))
  for (const value of [null, "1", -1, 1.2, Number.MAX_SAFE_INTEGER + 1])
    assert(!api.isAdminPage({ ...page, totalElements: value }))
  assert(!api.isAdminPage({ ...page, hasNext: true }))
  assert(!api.isAdminPage({ ...page, items: [{ ...item, status: "ALL" }] }))
  assert(
    !api.isAdminPage({
      ...page,
      items: [{ ...item, author: { ...item.author, withdrawn: "true" } }],
    }),
  )
})
test("private admin GET uses precise query/session/no-store and no CSRF or retry", async (t) => {
  process.env.NEXT_PUBLIC_API_BASE_URL = "http://127.0.0.1:8082"
  const calls = []
  t.mock.method(globalThis, "fetch", async (u, o) => {
    calls.push({ u, o })
    return new Response(JSON.stringify(page), {
      headers: { "Content-Type": "application/json" },
    })
  })
  assert.deepEqual(
    await api.getAdminPosts(
      { statuses: ["VISIBLE","HIDDEN","DELETED"], keyword: "　검색　", page: 0 },
      new AbortController().signal,
    ),
    page,
  )
  assert.equal(calls.length, 1)
  const u = new URL(calls[0].u)
  assert.equal(u.pathname, "/api/v1/admin/posts")
  assert.equal(u.searchParams.get("keyword"), "검색")
  assert.equal(u.searchParams.get("size"), "20")
  assert.equal(calls[0].o.credentials, "include")
  assert.equal(calls[0].o.cache, "no-store")
  assert.equal(calls[0].o.method, "GET")
})
test("401 and 403 remain distinguishable; broken response is not empty success", async (t) => {
  for (const status of [401, 403, 500, 200]) {
    t.mock.method(
      globalThis,
      "fetch",
      async () =>
        new Response("{}", {
          status,
          headers: { "Content-Type": "application/json" },
        }),
    )
    await assert.rejects(
      api.getAdminPosts(
        { statuses: ["VISIBLE","HIDDEN","DELETED"], keyword: "", page: 0 },
        new AbortController().signal,
      ),
      (e) => e.status === (status === 200 ? 0 : status),
    )
  }
})

test("multi-status request is canonical, copied, explicit and rejects empty/invalid selections", () => {
  const values = ["HIDDEN", "VISIBLE", "HIDDEN"]
  const params = api.adminQueryParams({statuses:values,keyword:"  검색  ",page:2})
  values.push("DELETED")
  assert.deepEqual(params.getAll("statuses"),["VISIBLE","HIDDEN"])
  assert.equal(params.has("status"),false)
  assert.equal(params.get("page"),"2")
  assert.deepEqual(api.adminQueryParams({statuses:api.ADMIN_CONTENT_STATUSES,keyword:"",page:0}).getAll("statuses"),["VISIBLE","HIDDEN","DELETED"])
  for(const statuses of [[],["ALL"],["hidden"],["VISIBLE,HIDDEN"]]) assert.throws(()=>api.adminQueryParams({statuses,keyword:"",page:0}))
})
