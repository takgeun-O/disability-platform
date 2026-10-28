import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8459';
const out = process.env.MEMBER_TOAST_DIR ?? '/private/tmp/iyum-member-success-toast';
await fs.mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true }), results = [];
const member = { memberId: 7, nickname: '검토회원', email: 'review@example.test', role: 'USER' };
async function fixture(width = 1440, reducedMotion = 'no-preference') {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion });
  const p = await ctx.newPage(); p.setDefaultTimeout(7000);
  const state = { member: { ...member }, posts: 0, puts: 0, csrf: 0, passwordStatus: 200, relogin: false, nicknameFail: false, csrfFail: false };
  await ctx.route(u => u.pathname.startsWith('/api/v1/'), async r => {
    const path = new URL(r.request().url()).pathname;
    const failure = (status, code) => r.fulfill({ status, json: { code, message: 'mock error', fieldErrors: [] } });
    if (path === '/api/v1/auth/me') return state.member ? r.fulfill({ json: state.member }) : failure(401, 'AUTHENTICATION_REQUIRED');
    if (path.endsWith('/auth/csrf')) { state.csrf++; return state.csrfFail && state.csrf === 2 ? failure(503, 'SERVER_ERROR') : r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } }); }
    if (path.endsWith('/auth/logout')) { state.member = null; return r.fulfill({ status: 204 }); }
    if (path === '/api/v1/members/me') return r.fulfill({ json: { ...state.member, createdAt: '2026-10-01T00:00:00Z' } });
    if (path.endsWith('/activity-summary')) return r.fulfill({ json: { postCount: 0, commentCount: 0 } });
    if (path.endsWith('/posts') || path.endsWith('/comments')) return r.fulfill({ json: { items: [], hasNext: false, nextCursor: null } });
    if (path.endsWith('/nickname')) {
      state.puts++; if (state.nicknameFail) return failure(409, 'NICKNAME_ALREADY_EXISTS');
      state.member.nickname = r.request().postDataJSON().nickname;
      return r.fulfill({ json: { ...state.member, createdAt: '2026-10-01T00:00:00Z' } });
    }
    if (path.endsWith('/password')) {
      state.posts++; return state.passwordStatus === 401 ? failure(401, 'AUTHENTICATION_REQUIRED') : r.fulfill({ json: { reloginRequired: state.relogin } });
    }
    throw Error('Unexpected API path: ' + path);
  });
  await p.goto(base + '/mypage/settings'); await p.locator('.mypage-name h2').waitFor();
  await p.clock.install();
  return { p, ctx, state };
}
const toast = p => p.locator('.mypage-toast');
async function nickname(f, name = '새로운회원') {
  await f.p.getByRole('button', { name: '프로필 수정', exact: true }).click();
  await f.p.locator('#my-nickname').fill(name);
  await f.p.getByRole('button', { name: '저장', exact: true }).click();
}
async function password(f) {
  await f.p.locator('#password-change-entry').click();
  const d = f.p.getByRole('dialog', { name: '비밀번호 변경', exact: true });
  for (const [key, value] of Object.entries({ currentPassword: 'Old12345!', newPassword: 'New12345!', newPasswordConfirm: 'New12345!' })) await f.p.locator('#change-' + key).fill(value);
  await d.getByRole('button', { name: '비밀번호 변경', exact: true }).click(); return d;
}
async function capture(p, path) {
  // Let the browser finish its CSS transition on the real animation clock before capture.
  // Advancing the mock JS clock alone does not guarantee a finished compositor frame.
  await p.clock.resume();
  await p.waitForFunction(() => {
    const card = document.querySelector('.mypage-toast-card');
    return card && getComputedStyle(card).opacity === '1' && card.getAnimations().every(a => a.playState === 'finished');
  });
  await p.screenshot({ path });
}
async function gone(p) { await p.clock.runFor(6000); await toast(p).waitFor({ state: 'detached' }); }
async function check(name, fn) {
  try { await fn(); results.push({ name, pass: true }); console.log('PASS', name); }
  catch (e) { results.push({ name, pass: false, error: e.message }); console.error('FAIL', name, e.message); process.exitCode = 1; }
  finally { for (const c of browser.contexts()) await c.close(); }
}
try {
  for (const width of [1440, 390, 320]) await check(`nickname toast visible without layout/focus jump and auto-dismisses ${width}`, async () => {
    const f = await fixture(width), { p } = f;
    const before = await p.locator('.mypage-account').boundingBox();
    await nickname(f); await toast(p).waitFor();
    assert.equal(await p.locator('.mypage-name h2').innerText(), '새로운회원');
    assert.equal(await p.locator('.mypage-notice.has-notice').count(), 0);
    assert.equal(await toast(p).getByRole('status').innerText(), '닉네임을 변경했습니다.');
    assert.equal(await p.locator('.mypage-identity > button').evaluate(e => e === document.activeElement), true);
    const after = await p.locator('.mypage-account').boundingBox(); assert(Math.abs(before.y - after.y) < 2);
    await p.clock.runFor(250); assert(await toast(p).evaluate(e => e.scrollWidth <= e.clientWidth));
    assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await capture(p, `${out}/nickname-${width}.png`);
    await p.clock.runFor(3000); assert(await toast(p).isVisible()); await gone(p);
    assert.equal(await p.locator('.mypage-name h2').innerText(), '새로운회원'); assert.equal(f.state.puts, 1);
  });
  for (const width of [1440, 390, 320]) await check(`password success modal stays; toast begins after close ${width}`, async () => {
    const f = await fixture(width), { p } = f; const d = await password(f);
    await d.locator('.password-change-success').waitFor(); await p.waitForFunction(() => !document.querySelector('form[aria-busy=true]'));
    await p.clock.runFor(6000); assert(await d.isVisible()); assert.equal(await toast(p).count(), 0);
    assert.equal(await p.locator('.mypage-notice.has-notice').count(), 0);
    await d.getByRole('button', { name: '닫기', exact: true }).click(); await toast(p).waitFor();
    assert.equal(await p.locator('#password-change-entry').evaluate(e => e === document.activeElement), true);
    assert((await toast(p).innerText()).includes('비밀번호가 변경되었습니다.'));
    const y = await p.evaluate(() => scrollY); await p.clock.runFor(250);
    assert(await toast(p).evaluate(e => e.scrollWidth <= e.clientWidth)); await capture(p, `${out}/password-${width}.png`);
    await gone(p); assert.equal(await p.evaluate(() => scrollY), y); assert.equal(f.state.posts, 1); assert.equal(f.state.csrf, 2);
  });
  await check('hover and keyboard focus pause lifetime; manual close restores trigger without scrolling', async () => {
    const f = await fixture(), { p } = f; await nickname(f); await toast(p).waitFor(); await p.clock.runFor(250);
    await toast(p).hover(); await p.clock.runFor(10000); assert(await toast(p).isVisible());
    const close = toast(p).getByRole('button', { name: '알림 닫기' }); await close.focus(); await p.mouse.move(1, 1);
    await p.clock.runFor(10000); assert(await toast(p).isVisible());
    const y = await p.evaluate(() => scrollY); await p.keyboard.press('Enter'); await p.clock.runFor(250);
    assert.equal(await toast(p).count(), 0); assert(await p.locator('.mypage-identity > button').evaluate(e => e === document.activeElement)); assert.equal(await p.evaluate(() => scrollY), y);
    await nickname(f, '두번째회원'); await toast(p).waitFor(); await p.clock.runFor(250); await toast(p).hover(); await p.clock.runFor(10000);
    await p.mouse.move(1, 1); await gone(p);
  });
  await check('background visibility pauses timeout; returning resumes remaining lifetime', async () => {
    const f = await fixture(), { p } = f; await nickname(f); await toast(p).waitFor();
    await p.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); document.dispatchEvent(new Event('visibilitychange')); });
    await p.clock.runFor(10000); assert(await toast(p).isVisible());
    await p.evaluate(() => { delete document.hidden; document.dispatchEvent(new Event('visibilitychange')); }); await gone(p);
  });
  await check('new success has a fresh timer and a single toast', async () => {
    const f = await fixture(), { p } = f; await nickname(f); await toast(p).waitFor(); await p.clock.runFor(3000);
    await nickname(f, '두번째회원'); await toast(p).waitFor(); assert.equal(await toast(p).count(), 1);
    await p.clock.runFor(3000); assert(await toast(p).isVisible()); await gone(p); assert.equal(f.state.puts, 2);
  });
  await check('navigation and logout clear toast; old timeout cannot recreate it', async () => {
    const f = await fixture(), { p } = f; await nickname(f); await toast(p).waitFor();
    await p.getByRole('link', { name: 'IYUM 홈으로 이동', exact: true }).click(); await p.waitForURL(u => u.pathname === '/');
    await gone(p); await p.locator('.iyum-desktop-auth').getByRole('link', { name: '마이페이지', exact: true }).click(); await p.locator('.mypage-name h2').waitFor();
    await p.getByRole('link',{name:'프로필·계정 설정',exact:true}).click();await p.getByRole('button',{name:'프로필 수정',exact:true}).waitFor();await nickname(f, '다시변경'); await toast(p).waitFor(); await p.getByRole('button', { name: '로그아웃', exact: true }).click();
    await p.getByRole('heading', { name: '로그인이 필요해요' }).waitFor(); await gone(p);
  });
  await check('nickname failure remains inline and never produces success toast', async () => {
    const f = await fixture(); f.state.nicknameFail = true; await nickname(f);
    await f.p.locator('#nickname-editor [role=alert]').waitFor(); await f.p.clock.runFor(6000);
    assert.equal(await toast(f.p).count(), 0); assert.equal(await f.p.locator('#my-nickname').inputValue(), '새로운회원');
  });
  for (const relogin of [true, false]) await check(`${relogin ? 'reloginRequired' : 'current 401'} keeps actionable inline notice`, async () => {
    const f = await fixture(); f.state.relogin = relogin; f.state.passwordStatus = relogin ? 200 : 401;
    await password(f); await f.p.getByRole('heading', { name: '로그인이 필요해요' }).waitFor(); await f.p.clock.runFor(6000);
    assert.equal(await toast(f.p).count(), 0); assert((await f.p.locator('.mypage-notice.has-notice').innerText()).includes(relogin ? '다시 로그인' : '만료'));
  });
  await check('confirmed password success still toasts after follow-up CSRF failure', async () => {
    const f = await fixture(); f.state.csrfFail = true; const d = await password(f);
    await d.getByRole('alert').waitFor(); await d.getByRole('button', { name: '닫기', exact: true }).click(); await toast(f.p).waitFor();
    assert((await toast(f.p).innerText()).includes('비밀번호가 변경되었습니다.')); assert.equal(f.state.posts, 1);
  });
  await check('reduced motion disables entrance/exit motion and keeps mobile text in bounds', async () => {
    const f = await fixture(320, 'reduce'); await nickname(f); await toast(f.p).waitFor();
    assert.equal(await f.p.locator('.mypage-toast-card').evaluate(e => getComputedStyle(e).animationName), 'none'); await gone(f.p);
  });
} finally { await browser.close(); await fs.writeFile(`${out}/results.json`, JSON.stringify({ mode: 'production Next + mock API; Playwright clock timers; no real account writes', results }, null, 2)); }
