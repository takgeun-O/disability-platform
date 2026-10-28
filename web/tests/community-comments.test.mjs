import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const api = await loadTypeScript(
  "../src/lib/community-comments.ts",
  import.meta.url,
)
const item = {
  likeCount: 0, liked: false,
  commentId: 10,
  content: "  본문\n",
  author: { memberId: 3, nickname: "예시" },
  createdAt: "2026-10-17T03:00:00.123456Z",
  updatedAt: "2026-10-17T03:00:00.123456Z",
}
const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  })
const csrf = () => json({ headerName: "X-CSRF-TOKEN", token: "test-only" })
process.env.NEXT_PUBLIC_API_BASE_URL = "http://api.test"
test("Java blank/codepoint policy preserves original whitespace", () => {
  assert.equal(api.validateComment("😀".repeat(2000)), "")
  assert.equal(api.validateComment("😀".repeat(2001)), api.longContent)
  assert.equal(api.validateComment(" \n\t\u3000"), api.requiredContent)
  assert.equal(api.validateComment("\u00a0"), "")
})
test("public first/cursor request omits credentials and preserves microseconds", async (t) => {
  const calls = []
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options })
    return json({ items: [item], hasNext: false, nextCursor: null })
  })
  await api.getComments("7")
  await api.getComments("7", { createdAt: item.createdAt, commentId: 9, sort: "OLDEST", likeCount: null })
  const first = new URL(calls[0].url),
    next = new URL(calls[1].url)
  assert.equal(first.pathname, "/api/v1/community/posts/7/comments")
  assert.equal(first.searchParams.has("afterId"), false)
  assert.equal(next.searchParams.get("afterCreatedAt"), item.createdAt)
  assert.equal(next.searchParams.get("afterId"), "9")
  assert.equal(calls[0].options.credentials, "omit")
  assert.equal(calls[0].options.cache, "no-store")
})
test("201 exact body, CSRF and session; 204 never parsed; DELETE no body/content-type", async (t) => {
  const calls = []
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options })
    if (url.endsWith("/csrf")) return csrf()
    if (options.method === "POST") return json({ commentId: 11 }, 201)
    const response = new Response(null, { status: 204 })
    response.json = () => {
      throw Error("must not parse")
    }
    return response
  })
  assert.equal(
    await api.changeComment("7", "POST", null, "  😀\n", () => true),
    11,
  )
  await api.changeComment("7", "PUT", 11, "수정", () => true)
  await api.changeComment("7", "DELETE", 11, "", () => true)
  assert.equal(calls.length, 6)
  assert.equal(calls[1].options.body, JSON.stringify({ content: "  😀\n" }))
  assert.equal(calls[1].options.credentials, "include")
  assert.equal(calls[1].options.headers["X-CSRF-TOKEN"], "test-only")
  assert.equal(calls[5].options.body, undefined)
  assert.equal(calls[5].options.headers["Content-Type"], undefined)
})
test("departure during CSRF blocks mutation; CSRF failure sends no write", async (t) => {
  let current = true,
    count = 0
  t.mock.method(globalThis, "fetch", async () => {
    count++
    current = false
    return csrf()
  })
  assert.equal(
    await api.changeComment("7", "POST", null, "본문", () => current),
    null,
  )
  assert.equal(count, 1)
})
for (const [status, code] of [
  [401, "AUTHENTICATION_REQUIRED"],
  [403, "CSRF_TOKEN_INVALID"],
  [403, "COMMENT_WRITE_NOT_ALLOWED"],
  [403, "ACCESS_DENIED"],
  [404, "POST_NOT_FOUND"],
  [404, "COMMENT_NOT_FOUND"],
  [400, "INVALID_CURSOR"],
])
  test(`safe ${status}/${code}, no replay`, async (t) => {
    let count = 0
    t.mock.method(globalThis, "fetch", async (url) => {
      count++
      return url.endsWith("/csrf")
        ? csrf()
        : json({ code, message: "private HTML", fieldErrors: [] }, status)
    })
    await assert.rejects(
      api.changeComment("7", "PUT", 10, "본문", () => true),
      (e) => e.code === code && !e.message.includes("private"),
    )
    assert.equal(count, 2)
  })
for (const kind of ["html", "broken", "network", "500", "wrong201", "wrong204"])
  test(`uncertain ${kind} without automatic replay`, async (t) => {
    let count = 0
    t.mock.method(globalThis, "fetch", async (url) => {
      count++
      if (url.endsWith("/csrf")) return csrf()
      if (kind === "network") throw Error("offline")
      if (kind === "html") return new Response("<html>private</html>")
      if (kind === "broken")
        return new Response("{", {
          headers: { "Content-Type": "application/json" },
        })
      if (kind === "500") return json({}, 500)
      return json(
        kind === "wrong201" ? { commentId: -1 } : {},
        kind === "wrong201" ? 201 : 200,
      )
    })
    await assert.rejects(
      api.changeComment(
        "7",
        kind === "wrong204" ? "PUT" : "POST",
        kind === "wrong204" ? 10 : null,
        "본문",
        () => true,
      ),
      (e) => e.uncertain,
    )
    assert.equal(count, 2)
  })
test("field error whitelist, page validation and deduplication", () => {
  assert.equal(
    api.commentError(
      400,
      {
        code: "VALIDATION_FAILED",
        fieldErrors: [{ field: "content", message: api.longContent }],
      },
      true,
    ).field,
    api.longContent,
  )
  assert.equal(
    api.isCommentPage({ items: [], hasNext: false, nextCursor: null }),
    true,
  )
  assert.equal(
    api.isCommentPage({
      items: [item],
      hasNext: true,
      nextCursor: { createdAt: item.createdAt, commentId: 10, sort: "OLDEST", likeCount: null },
    }),
    true,
  )
  assert.equal(
    api.isCommentPage({ items: [], hasNext: true, nextCursor: null }),
    false,
  )
  assert.deepEqual(
    api
      .mergeComments([item], [item, { ...item, commentId: 11 }])
      .map((v) => v.commentId),
    [10, 11],
  )
})

for (const sort of ['OLDEST', 'LATEST', 'POPULAR']) {
  test(`sorting ${sort}: raw cursor, zero popularity and strict cursor contract`, async t => {
    const cursor = {createdAt:item.createdAt,commentId:item.commentId,sort,likeCount:sort==='POPULAR'?0:null}
    const page={items:[item],hasNext:true,nextCursor:cursor}
    assert.equal(api.isCommentPage(page,sort),true)
    for(const bad of [undefined,null,'OLDESTX',0])assert.equal(api.isCommentPage({...page,nextCursor:{...cursor,sort:bad}},sort),false)
    for(const bad of sort==='POPULAR'?[undefined,null,-1,1.5,'0',Number.MAX_SAFE_INTEGER+1]:[undefined,0,'0'])assert.equal(api.isCommentPage({...page,nextCursor:{...cursor,likeCount:bad}},sort),false)
    const calls=[]
    t.mock.method(globalThis,'fetch',async url=>{calls.push(new URL(url));return json({items:[item],hasNext:false,nextCursor:null})})
    await api.getComments('7',null,undefined,undefined,sort)
    await api.getComments('7',cursor,undefined,undefined,sort)
    assert.deepEqual([...calls[0].searchParams],[['size','20'],['sort',sort]])
    assert.equal(calls[1].searchParams.get('afterSort'),sort)
    assert.equal(calls[1].searchParams.get('afterCreatedAt'),item.createdAt)
    assert.equal(calls[1].searchParams.get('afterLikeCount'),sort==='POPULAR'?'0':null)
    await assert.rejects(api.getComments('7',{...cursor,sort:'WRONG'},undefined,undefined,sort),e=>e.code==='INVALID_CURSOR')
    assert.equal(calls.length,2)
  })
}
test('cursor identity includes ranking value; repeated cursor rejected without replay',async t=>{
 const cursor={createdAt:item.createdAt,commentId:10,sort:'POPULAR',likeCount:0}
 assert.notEqual(api.commentCursorKey(cursor),api.commentCursorKey({...cursor,likeCount:1}))
 let calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;return json({items:[item],hasNext:true,nextCursor:cursor})})
 await assert.rejects(api.getComments('7',cursor,undefined,undefined,'POPULAR'),e=>e.code==='UNEXPECTED_RESPONSE');assert.equal(calls,1)
})
