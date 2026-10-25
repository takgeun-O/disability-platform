// Opt-in actual Spring/browser check. Only verified seed-owned account/post; restore original like.
import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fixture, withSeedResources, atomicWrite } from './helpers/seed-ownership.mjs'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
if (process.env.IYUM_RUN_LIKE_LOCAL !== 'true') throw Error('Explicit local seed verification required')
const api = 'http://127.0.0.1:8082', base = 'http://127.0.0.1:8443'
const out = process.env.LIKE_ARTIFACT_DIR ?? '/private/tmp/iyum-post-like-actual'
await fs.mkdir(out, { recursive: true })
const results = [], requests = []
try {
  await withSeedResources(async resources => {
    const seed = await fixture(api)
    await resources.launch(chromium, { channel: 'chrome', headless: true })
    const ctx = await resources.context({ viewport: { width: 1440, height: 1000 } }), p = await ctx.newPage(); p.setDefaultTimeout(15000)
    p.on('response', response => { const u = new URL(response.url()); if (u.pathname.endsWith('/like')) requests.push({ path: u.pathname, method: response.request().method(), status: response.status() }) })
    await p.goto(base + '/login?from=%2Fmypage'); await p.locator('input[name=email]').fill(seed.accounts[0].email); await p.locator('input[name=password]').fill(seed.accounts[0].password)
    await p.locator('main').getByRole('button', { name: '로그인', exact: true }).click(); await p.waitForURL(u => u.pathname === '/mypage')
    const me = await (await ctx.request.get(api + '/api/v1/members/me')).json(); assert.equal(me.memberId, seed.members[0])
    let original
    const candidates = { checked: 0, owned: 0, missingLikeContract: 0, alreadyLiked: 0 }
    for (const id of seed.posts) {
      const r = await ctx.request.get(`${api}/api/v1/community/posts/${id}`)
      if (r.status() !== 200) continue
      const post = await r.json()
      candidates.checked++
      if (post.author.memberId === me.memberId && post.title.startsWith('[개발 테스트]')) {
        candidates.owned++
        if (typeof post.liked !== 'boolean' || !Number.isSafeInteger(post.likeCount)) candidates.missingLikeContract++
        if (post.liked === true) candidates.alreadyLiked++
      }
      if (post.author.memberId === me.memberId && post.title.startsWith('[개발 테스트]') && post.liked === false && Number.isSafeInteger(post.likeCount)) { original = post; break }
    }
    assert(original, 'No verified, unliked seed-owned post available: ' + JSON.stringify(candidates))
    const dir = path.join(seed.directory, 'like-journal'); await fs.mkdir(dir, { recursive: true, mode: 0o700 })
    const file = path.join(dir, randomUUID() + '.json')
    const receipt = { kind: 'post-like-frontend-verification', memberId: me.memberId, postId: original.id, originalLiked: false, originalCount: original.likeCount, restored: false }
    await atomicWrite(file, JSON.stringify(receipt))
    try {
      await p.goto(`${base}/community/posts/${original.id}?from=%2Fmypage`)
      const button = p.locator(`[data-post-like="${original.id}"] button`)
      await p.waitForFunction(id => document.querySelector(`[data-post-like="${id}"]`)?.dataset.likeReady === 'true', original.id)
      const write = p.waitForResponse(r => new URL(r.url()).pathname.endsWith('/like') && r.request().method() === 'PUT')
      await button.click(); const response = await write; assert.equal(response.status(), 200)
      const liked = await response.json(); assert.equal(liked.postId, original.id); assert.equal(liked.liked, true); assert.equal(liked.likeCount, original.likeCount + 1)
      await p.waitForFunction(id => document.querySelector(`[data-post-like="${id}"] button`)?.getAttribute('aria-pressed') === 'true', original.id)
      const confirmed = await (await ctx.request.get(`${api}/api/v1/community/posts/${original.id}`)).json(); assert.equal(confirmed.liked, true); assert.equal(confirmed.likeCount, liked.likeCount)
      const cancel = p.waitForResponse(r => new URL(r.url()).pathname.endsWith('/like') && r.request().method() === 'DELETE')
      await button.click(); assert.equal((await cancel).status(), 200)
      const final = await (await ctx.request.get(`${api}/api/v1/community/posts/${original.id}`)).json(); assert.equal(final.liked, false); assert.equal(final.likeCount, original.likeCount)
      receipt.restored = true; await atomicWrite(file, JSON.stringify(receipt))
      results.push({ name: 'actual Chrome 8443 → Spring 8082 session/CORS/CSRF PUT/read/DELETE/read', pass: true, postId: original.id, originalCount: original.likeCount, restored: true })
    } finally {
      if (!receipt.restored) {
        // Explicit cleanup of this test's known originally-unliked pair; never another member's like.
        const current = await (await ctx.request.get(`${api}/api/v1/community/posts/${original.id}`)).json()
        if (current.liked) { const csrf = await (await ctx.request.get(api + '/api/v1/auth/csrf')).json(); const r = await ctx.request.delete(`${api}/api/v1/community/posts/${original.id}/like`, { headers: { [csrf.headerName]: csrf.token } }); assert.equal(r.status(), 200) }
        const currentAfter = await (await ctx.request.get(`${api}/api/v1/community/posts/${original.id}`)).json(); assert.equal(currentAfter.liked, false)
        receipt.restored = true; await atomicWrite(file, JSON.stringify(receipt))
      }
    }
  })
} catch (e) { results.push({ name: 'actual verification', pass: false, error: e.message }); process.exitCode = 1 }
await fs.writeFile(out + '/results.json', JSON.stringify({ results, requests, notes: 'No account credentials, tokens, cookies or account identifiers included. Seed intent/receipt retained privately; no posts/comments/accounts created or deleted.' }, null, 2))
console.log(JSON.stringify(results))
