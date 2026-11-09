// Deterministic API response ordering. No real account, database or password changes.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8459';
const out = process.env.PASSWORD_CHANGE_RACE_DIR ?? '/private/tmp/iyum-password-change-race';
await fs.mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const results = [], traces = [];
const member = { memberId: 7, nickname: '검토회원', email: 'review@example.test', role: 'USER' };
const paths = { profile: '/api/v1/members/me', summary: '/api/v1/members/me/activity-summary', posts: '/api/v1/members/me/posts', comments: '/api/v1/members/me/comments' };
const failure = (status, code) => ({ status, json: { code, message: 'synthetic error', fieldErrors: [] } });
const gate = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const tick = p => p.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
function data(kind, who, fresh = true) {
  const stamp = '2026-10-24T00:00:00.123456Z';
  if (kind === 'profile') return { ...who, nickname: fresh ? who.nickname : '오래된회원', createdAt: stamp };
  if (kind === 'summary') return { postCount: fresh ? 2 : 999, commentCount: fresh ? 2 : 999 };
  return { items: [2, 1].map(id => kind === 'posts' ? { visibility: 'VISIBLE', id, type: 'QUESTION', topic: 'HEARING_AID', title: `${fresh ? who.nickname : '오래된회원'} 게시글 ${id}`, contentPreview: '검토 미리보기', likeCount: 0, liked: false, author: who, createdAt: stamp, commentCount: 0 } : { contentUpdatedAt: null, visibility: 'VISIBLE', liked: false, likeCount: 0, commentId: id, content: `${fresh ? who.nickname : '오래된회원'} 댓글 ${id}`, createdAt: stamp, updatedAt: stamp, post: { postId: id, title: '원문', type: 'QUESTION', topic: 'HEARING_AID' } }), hasNext: false, nextCursor: null };
}
async function fixture(kind, width = 1440) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 } });
  const p = await ctx.newPage(); p.setDefaultTimeout(6000);
  // Let withheld GETs actually finish even if the UI aborts: their lifetime is checked separately.
  await p.addInitScript(() => { const fetch = window.fetch; window.fetch = (url, options) => {
    const path = new URL(typeof url === 'string' ? url : url.url ?? String(url), location.href).pathname;
    return fetch(url, path.startsWith('/api/v1/members/me') && (options?.method ?? 'GET') === 'GET' ? { ...options, signal: undefined } : options);
  }; });
  const oldArrived = gate(), old = gate(), oldDone = gate(), postArrived = gate(), post = gate();
  const state = { who: { ...member }, loginWho: { ...member }, counts: {}, log: [], csrf: 0, posts: 0, authReads: 0, logout: 0, fresh401: null, csrfFail: false, errors: [] };
  const record = entry => state.log.push({ order: state.log.length + 1, ...entry });
  p.on('pageerror', e => state.errors.push(e.name));
  await ctx.route(u => u.pathname.startsWith('/api/v1/'), async r => {
    const path = new URL(r.request().url()).pathname;
    if (path === '/api/v1/auth/me') { state.authReads++; return r.fulfill(state.who ? { json: state.who } : failure(401, 'AUTHENTICATION_REQUIRED')); }
    if (path.endsWith('/auth/csrf')) { state.csrf++; record({ kind: 'csrf', count: state.csrf }); return r.fulfill(state.csrfFail && state.csrf === 2 ? failure(503, 'SERVICE_UNAVAILABLE') : { json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } }); }
    if (path.endsWith('/auth/logout')) { state.logout++; state.who = null; return r.fulfill({ status: 204 }); }
    if (path.endsWith('/auth/login')) { state.who = { ...state.loginWho }; return r.fulfill({ json: state.who }); }
    if (path === '/api/v1/members/me/password') { state.posts++; record({ kind: 'password', event: 'request' }); postArrived.resolve(); const answer = await post.promise; record({ kind: 'password', event: 'response', status: answer.status ?? 200 }); return r.fulfill(answer); }
    const key = Object.keys(paths).find(k => paths[k] === path);
    if (key) {
      const n = state.counts[key] = (state.counts[key] ?? 0) + 1;
      record({ kind: key, event: 'request', count: n });
      if (key === kind && n === 1) {
        oldArrived.resolve(); const answer = await old.promise;
        record({ kind: key, event: 'old-response', status: answer.status ?? 200 });
        try { await r.fulfill(answer); } finally { oldDone.resolve(); } return;
      }
      const answer = state.fresh401 === key ? failure(401, 'AUTHENTICATION_REQUIRED') : { json: data(key, state.who) };
      record({ kind: key, event: 'current-response', status: answer.status ?? 200 }); return r.fulfill(answer);
    }
    throw Error('Unexpected endpoint: ' + path);
  });
  await p.goto(base + (kind === 'profile' ? '/mypage/settings' : '/mypage'));
  if (kind === 'comments') await p.getByRole('tab', { name: /내 댓글/ }).click();
  await oldArrived.promise;
  if (kind !== 'profile') await p.getByRole('link', { name: '프로필·계정 설정', exact: true }).click();
  await p.locator('#password-change-entry').waitFor();
  return { ctx, p, state, kind, old, oldDone, post, postArrived };
}
async function begin(f) {
  await f.p.locator('#password-change-entry').click();
  for (const [key, value] of Object.entries({ currentPassword: 'Old12345!', newPassword: 'New12345!', newPasswordConfirm: 'New12345!' })) await f.p.locator('#change-' + key).fill(value);
  const d = f.p.getByRole('dialog', { name: '비밀번호 변경', exact: true });
  await d.getByRole('button', { name: '비밀번호 변경', exact: true }).click(); await f.postArrived.promise; return d;
}
async function complete(f, d) {
  f.post.resolve({ json: { reloginRequired: false } });
  await d.getByText('비밀번호가 변경되었습니다. 현재 로그인은 유지됩니다.', { exact: true }).waitFor();
  await f.p.waitForFunction(() => document.querySelector('.password-change-actions button')?.disabled === false);
}
async function releaseOld(f, outcome = '401') {
  f.old.resolve(outcome === '401' ? failure(401, 'AUTHENTICATION_REQUIRED') : { json: data(f.kind, member, false) });
  await f.oldDone.promise; await tick(f.p);
}
async function usable(f) {
  if (new URL(f.p.url()).pathname === '/mypage/settings') await f.p.getByRole('link', { name: '← 마이페이지로 돌아가기', exact: true }).click();
  await f.p.locator('.mypage-name h2').waitFor();
  assert.equal(await f.p.getByRole('heading', { name: '로그인이 필요해요' }).count(), 0);
  assert.equal(await f.p.locator('.mypage-name h2').innerText(), f.state.who.nickname);
  assert.equal(await f.p.locator('.mypage-counts strong').first().innerText(), '2');
  if (f.kind === 'comments') {
    await f.p.getByRole('tab', { name: /내 댓글/ }).click();
    await f.p.locator('[data-member-comment-id]').first().waitFor();
    assert.equal(await f.p.locator('[data-member-comment-id]').count(), 2);
  } else { await f.p.locator('[data-member-post-id]').first().waitFor(); assert.equal(await f.p.locator('[data-member-post-id]').count(), 2); }
  assert(!(await f.p.locator('main').innerText()).includes('오래된회원'));
  assert(!(await f.p.locator('main').innerText()).includes('불러오고 있습니다.'));
  assert.equal(await f.p.locator('button[aria-disabled=true]').count(), 0);
  assert.deepEqual(f.state.errors, []);
}
async function check(name, fn) {
  let f;
  try { await fn(async (...args) => (f = await fixture(...args))); results.push({ name, pass: true }); console.log('PASS', name); }
  catch (e) { results.push({ name, pass: false, error: e.message }); console.error('FAIL', name, e.message); process.exitCode = 1; }
  finally { if (f) { traces.push({ name, counts: f.state.counts, events: f.state.log }); f.old.resolve(failure(401, 'AUTHENTICATION_REQUIRED')); f.post.resolve(failure(503, 'SERVICE_UNAVAILABLE')); } for (const c of browser.contexts()) await c.close(); }
}
try {
  for (const kind of Object.keys(paths)) for (const timing of ['after', 'before']) await check(`${kind}: old 401 ${timing} password response cannot expire retained login`, async setup => {
    const f = await setup(kind, kind === 'comments' ? 390 : 1440), d = await begin(f);
    if (timing === 'before') { await releaseOld(f); assert(await d.isVisible()); assert.equal(await d.getByText('비밀번호가 변경되었습니다. 현재 로그인은 유지됩니다.', { exact: true }).count(), 0); }
    await complete(f, d);
    if (timing === 'after') await releaseOld(f);
    assert(await d.isVisible()); await d.getByRole('button', { name: '닫기', exact: true }).click(); await usable(f);
    assert(f.state.counts[kind] >= 2); assert.equal(f.state.posts, 1); assert.equal(f.state.logout, 0); assert.equal(f.state.authReads, 1);
    if (timing === 'after') await f.p.screenshot({ path: `${out}/${kind}-retained.png` });
  });
  for (const kind of Object.keys(paths)) await check(`${kind}: old success cannot overwrite renewed personal data`, async setup => {
    const f = await setup(kind), d = await begin(f); await complete(f, d); await releaseOld(f, 'success');
    await d.getByRole('button', { name: '닫기', exact: true }).click(); await usable(f); assert(f.state.counts[kind] >= 2);
  });
  for (const kind of Object.keys(paths)) await check(`${kind}: new generation 401 still expires current session`, async setup => {
    const f = await setup(kind); await begin(f); f.state.fresh401 = kind; await releaseOld(f); f.post.resolve({ json: { reloginRequired: false } });
    if (kind !== 'profile') { const d=f.p.getByRole('dialog', {name:'비밀번호 변경',exact:true});await f.p.waitForFunction(()=>document.querySelector('.password-change-actions button')?.disabled===false && !!document.querySelector('.password-change-success'));await d.getByRole('button',{name:'닫기',exact:true}).click();await f.p.getByRole('link',{name:'← 마이페이지로 돌아가기',exact:true}).click();/* Saved comments tab restores and starts its current-generation GET. */ }
    await f.p.getByRole('heading', { name: '로그인이 필요해요' }).waitFor(); assert.equal(await f.p.locator('.mypage-profile').count(), 0); assert.equal(await f.p.locator('[data-member-post-id],[data-member-comment-id]').count(), 0); assert.equal(f.state.posts, 1); assert(f.state.counts[kind] >= 2);
  });
  for (const same of [true, false]) for (const outcome of ['401', 'success']) for (const kind of Object.keys(paths)) await check(`${kind}: ${outcome} after ${same ? 'same-member' : 'other-member'} login is stale`, async setup => {
    const f = await setup(kind); await f.p.getByRole('button', { name: '로그아웃', exact: true }).click(); await f.p.getByRole('heading', { name: '로그인이 필요해요' }).waitFor();
    f.state.loginWho = { ...member, memberId: same ? 7 : 8, nickname: '새로그인', email: 'new@example.test' };
    await f.p.getByRole('link', { name: '로그인하기', exact: true }).click();
    await f.p.locator('input[name=email]').fill(f.state.loginWho.email); await f.p.locator('input[name=password]').fill('Synthetic123!');
    await f.p.locator('main').getByRole('button', { name: '로그인', exact: true }).click(); await f.p.waitForURL(u => u.pathname === '/mypage/settings');
    await usable(f); await releaseOld(f, outcome); await usable(f);
  });
  await check('confirmed change survives follow-up CSRF failure and resumed reads finish', async setup => {
    const f = await setup('profile'), d = await begin(f); f.state.csrfFail = true; await complete(f, d); await releaseOld(f); await d.getByRole('alert').waitFor();
    assert((await d.getByRole('alert').innerText()).includes('변경은 완료')); await d.getByRole('button', { name: '닫기', exact: true }).click(); await usable(f); assert.equal(f.state.posts, 1);
  });
  for (const result of ['relogin', '401', '503', '400']) await check(`old 401 first then password ${result} uses only confirmed outcome`, async setup => {
    const f = await setup('summary'), d = await begin(f); await releaseOld(f); assert(await d.isVisible());
    f.post.resolve(result === 'relogin' ? { json: { reloginRequired: true } } : failure(Number(result), result === '401' ? 'AUTHENTICATION_REQUIRED' : result === '400' ? 'PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH' : 'SERVICE_UNAVAILABLE'));
    if (result === 'relogin' || result === '401') await f.p.getByRole('heading', { name: '로그인이 필요해요' }).waitFor();
    else { await d.getByRole('alert').waitFor(); await f.p.waitForFunction(() => !document.querySelector('form[aria-busy=true]')); assert.equal(await d.locator('input').count(), 3); assert(!(await d.innerText()).includes('비밀번호가 변경되었습니다.')); await d.getByRole('button', { name: '취소', exact: true }).click(); await usable(f); }
    assert.equal(f.state.posts, 1); assert.equal(f.state.logout, 0);
  });
} finally {
  await fs.writeFile(`${out}/results.json`, JSON.stringify({ mode: 'controlled mock API only; deferred GET/POST release; no real DB/account writes', results }, null, 2));
  await fs.writeFile(`${out}/response-order.json`, JSON.stringify(traces, null, 2)); await browser.close();
}
