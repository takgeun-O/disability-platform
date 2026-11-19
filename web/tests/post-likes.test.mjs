import test from 'node:test'
import assert from 'node:assert/strict'
import { loadTypeScript } from './helpers/load-typescript.mjs'
const { createPostLikes } = await loadTypeScript('../src/lib/post-likes.ts', import.meta.url)
const api = await loadTypeScript('../src/lib/community-api.ts', import.meta.url)
const { createAuthSession } = await loadTypeScript('../src/lib/auth-session.ts', import.meta.url)
const initial = { id: 7, liked: false, likeCount: 2 }
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
test('shared post lock, other posts independent; old GET cannot overwrite confirmed mutation', async () => {
  const store = createPostLikes(); store.setContext('A:1'); store.read().accept([initial, { ...initial, id: 8 }])
  const oldRead = store.read(), operation = store.change(7), delayed = deferred()
  assert.equal(store.change(7), null); assert(store.change(8))
  const response = delayed.promise.then(state => { operation.commit(state); operation.finish() })
  const during = store.read(); oldRead.accept([{ ...initial, likeCount: 90 }]); assert.equal(store.get(7).likeCount, 2)
  delayed.resolve({ liked: true, likeCount: 11 }); await response
  for (const read of [oldRead, during]) read.accept([initial])
  assert.equal(store.get(7).likeCount, 11); assert.equal(store.get(7).liked, true); assert.equal(store.get(7).pending, false)
  store.read().accept([{ ...initial, likeCount: 12, liked: true }]); assert.equal(store.get(7).likeCount, 12)
})
test('한 조회에서 확인한 여러 변경을 한 번 알리고 겹친 조회와 동일 값은 제외한다', () => {
  const store = createPostLikes()
  const rows = [initial, { ...initial, id: 8 }]
  assert.equal(store.read().accept(rows), false)
  const markUncertain = () => {
    for (const row of rows) {
      const write = store.change(row.id)
      write.uncertain()
      write.finish()
    }
  }
  markUncertain()
  const earlier = store.read(), batch = store.read(), later = store.read()
  const recovered = [{ ...initial, liked: true }, { ...rows[1], likeCount: 3 }]
  assert.equal(batch.accept(recovered), true)
  assert.equal(earlier.accept(rows), false)
  assert.equal(later.accept(recovered), false)
  markUncertain()
  assert.equal(store.read().accept(recovered), false)
  assert(recovered.every(row => !store.get(row.id).needsRead))
})
for (const next of ['A:new-login', 'B', 'anonymous']) test(`old success/read/finally cannot affect ${next}`, () => {
  const store = createPostLikes(); store.setContext('A:1'); store.read().accept([initial])
  const read = store.read(), mutation = store.change(7)
  store.setContext(next); assert.equal(store.get(7), undefined); assert.equal(read.valid(), false)
  store.read().accept([{ ...initial, likeCount: 6 }]); const fresh = store.change(7)
  mutation.commit({ liked: true, likeCount: 500 }); mutation.finish(); read.accept([initial])
  assert.equal(store.get(7).likeCount, 6); assert.equal(store.get(7).pending, true)
  fresh.finish(); assert.equal(store.get(7).pending, false)
})
test('real auth request snapshot rejects late 401 across same-member login, current 401 expires', async () => {
  const member = { memberId: 7, nickname: '검토회원', email: 'review@example.test', role: 'USER' }
  const session = createAuthSession({ getCurrentMember: async () => member, getAuthCsrf: async () => ({}), login: async () => member, logout: async () => {} })
  session.start(); await session.refresh(); const old = session.captureMemberRequest(7)
  await session.signIn({ email: member.email, password: 'fixture-only' }, () => {})
  assert.equal(old.expire(), false); assert.equal(session.getSnapshot().status, 'authenticated')
  assert(session.captureMemberRequest(7).expire()); assert.equal(session.getSnapshot().status, 'anonymous'); session.stop()
})
async function withFetch(handler, run) {
  const fetch = globalThis.fetch, base = process.env.NEXT_PUBLIC_API_BASE_URL
  process.env.NEXT_PUBLIC_API_BASE_URL = 'http://api.example.test'; globalThis.fetch = handler
  try { await run() } finally { globalThis.fetch = fetch; if (base === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL; else process.env.NEXT_PUBLIC_API_BASE_URL = base }
}
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
for (const liked of [true, false]) test(`explicit ${liked ? 'PUT' : 'DELETE'}, dynamic CSRF, no body/viewer, server count`, async () => {
  const calls = []
  await withFetch(async (url, options) => {
    calls.push([url, options]); if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'fixture-only' })
    assert(url.endsWith('/7/like')); assert.equal(options.method, liked ? 'PUT' : 'DELETE'); assert.equal(options.credentials, 'include'); assert.equal(options.cache, 'no-store'); assert.equal(options.body, undefined); assert.equal(options.headers['X-CSRF-TOKEN'], 'fixture-only')
    return json({ postId: 7, liked, likeCount: 19 })
  }, async () => assert.deepEqual(await api.changePostLike(7, liked, () => true), { liked, likeCount: 19 }))
  assert.equal(calls.length, 2)
})
for (const invalid of [undefined, null, -1, 0.5, '1', Number.MAX_SAFE_INTEGER + 1]) test(`reject invalid count ${String(invalid)} without coercion`, async () => {
  assert.equal(api.isLikeState({ liked: true, likeCount: invalid }), false)
  await withFetch(async url => url.endsWith('/csrf') ? json({ headerName: 'X-CSRF-TOKEN', token: 'fixture-only' }) : json({ postId: 7, liked: true, likeCount: invalid }), async () => {
    await assert.rejects(api.changePostLike(7, true, () => true), e => e.code === 'UNEXPECTED_RESPONSE' && e.uncertain)
  })
})
for (const body of [{ postId: 8, liked: true, likeCount: 1 }, { postId: 7, liked: 'true', likeCount: 1 }]) test('reject mismatched identity/nonboolean mutation response', async () => {
  await withFetch(async url => url.endsWith('/csrf') ? json({ headerName: 'X-CSRF-TOKEN', token: 'fixture-only' }) : json(body), async () => await assert.rejects(api.changePostLike(7, true, () => true)))
})
for (const [status, code] of [[401, 'AUTHENTICATION_REQUIRED'], [403, 'CSRF_TOKEN_INVALID'], [403, 'POST_LIKE_NOT_ALLOWED'], [404, 'POST_NOT_FOUND'], [500, 'SERVER_ERROR']]) test(`${code}: no automatic mutation replay`, async () => {
  let writes = 0
  await withFetch(async url => { if (url.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'fixture-only' }); writes++; return json({ code, message: '', fieldErrors: [] }, status) }, async () => {
    await assert.rejects(api.changePostLike(7, true, () => true), e => e.code === code)
  }); assert.equal(writes, 1)
})
test('auth invalidated while preparing CSRF does not send PUT', async () => {
  const gate = deferred(); let current = true, writes = 0
  await withFetch(async url => { if (url.endsWith('/csrf')) { await gate.promise; return json({ headerName: 'X-CSRF-TOKEN', token: 'fixture-only' }) } writes++; return json({}) }, async () => {
    const request = api.changePostLike(7, true, () => current); current = false; gate.resolve(); assert.equal(await request, null)
  }); assert.equal(writes, 0)
})
test('all GET shapes require count/boolean; detail still needs no commentCount; browser includes session', async () => {
  const post = { id: 7, type: 'QUESTION', topic: 'HEARING_AID', title: '검토 글', content: '본문', author: { memberId: 1, nickname: '회원' }, createdAt: '2026-10-25T00:00:00Z', updatedAt: '2026-10-25T00:00:00Z', likeCount: 3, liked: true }
  const query = { type: '', topic: '', page: 0 }
  let shape, current = post, calls = 0
  const window = globalThis.window; globalThis.window = {}
  try {
    await withFetch(async (_url, options) => {
      calls++; assert.equal(options.method, 'GET'); assert.equal(options.credentials, 'include'); assert.equal(options.cache, 'no-store')
      return json(shape === 'detail' ? current : shape === 'feed' ? { items: [{ ...current, contentPreview: '미리보기', commentCount: 0 }], hasNext: false, nextCursor: null } : { items: [{ ...current, commentCount: 0, matchedContentExcerpt: null }], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false })
    }, async () => {
      for (shape of ['detail', 'feed', 'list']) {
        const read = () => shape === 'detail' ? api.getPost('7') : shape === 'feed' ? api.getFeed(query) : api.getPosts(query)
        current = post; await read()
        for (const change of [{ likeCount: undefined }, { liked: undefined }, { liked: 'false' }, { likeCount: -1 }]) { current = { ...post, ...change }; await assert.rejects(read(), e => e.code === 'UNEXPECTED_RESPONSE') }
      }
    }); assert.equal(calls, 15)
  } finally { if (window === undefined) delete globalThis.window; else globalThis.window = window }
})
test('uncertain mutation remains read-required across screens; fresh GET resolves it', () => {
  const store = createPostLikes(); store.read().accept([initial]); const op = store.change(7)
  op.uncertain(); op.finish(); assert.equal(store.get(7).needsRead, true); assert.equal(store.get(7).pending, false)
  store.read().accept([{ ...initial, liked: true, likeCount: 3 }]); assert.equal(store.get(7).needsRead, undefined); assert.equal(store.get(7).liked, true)
})
