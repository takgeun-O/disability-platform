import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8459';
const out = process.env.PASSWORD_CHANGE_ARTIFACT_DIR ?? '/private/tmp/iyum-password-change-browser';
await fs.mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true }), results = [];
const member = { memberId: 7, email: 'review@example.test', nickname: '검토회원', role: 'USER' };
const values = { currentPassword: ' old ', newPassword: ' New12345! ', newPasswordConfirm: ' New12345! ' };
const gate = () => { let release; const promise = new Promise(r => release = r); return { promise, release }; };
const fail = (status, code, extra = {}) => ({ status, json: { code, message: 'untrusted server message', fieldErrors: [], ...extra } });
async function fixture(width = 1440) {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, hasTouch: width < 600 });
  await ctx.addCookies([{ name: 'mock-session', value: 'synthetic', domain: '127.0.0.1', path: '/', httpOnly: true }]);
  const p = await ctx.newPage(); p.setDefaultTimeout(10000);
  const state = { member: { ...member }, csrfCount: 0, posts: 0, logout: 0, change: null, csrf: null, pageErrors: [] };
  p.on('pageerror', e => state.pageErrors.push(e.name));
  await ctx.route(u => u.pathname.startsWith('/api/v1/'), async r => {
    const req = r.request(), path = new URL(req.url()).pathname;
    if (path.endsWith('/auth/me')) return r.fulfill(state.member ? { json: state.member } : fail(401, 'AUTHENTICATION_REQUIRED'));
    if (path.endsWith('/auth/csrf')) { state.csrfCount++; return state.csrf ? state.csrf(r) : r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } }); }
    if (path.endsWith('/members/me/password')) {
      state.posts++; assert.equal(req.method(), 'POST'); assert.equal(req.headers()['x-csrf-token'], 'synthetic'); assert(req.headers().cookie.includes('mock-session='));
      assert.deepEqual(req.postDataJSON(), values);
      return state.change ? state.change(r) : r.fulfill({ json: { reloginRequired: false } });
    }
    if (path.endsWith('/auth/login')) { state.member = { ...member }; return r.fulfill({ json: state.member }); }
    if (path.endsWith('/auth/logout')) { state.logout++; state.member = null; return r.fulfill({ status: 204 }); }
    if (path.endsWith('/members/me')) return r.fulfill({ json: { ...state.member, createdAt: '2026-10-01T00:00:00Z' } });
    if (path.endsWith('/activity-summary')) return r.fulfill({ json: { postCount: 0, commentCount: 0 } });
    if (path.endsWith('/posts') || path.endsWith('/comments')) return r.fulfill({ json: { items: [], hasNext: false, nextCursor: null } });
    throw Error('Unexpected mock endpoint ' + path);
  });
  await p.goto(base + '/login');
  if (width < 901) { await p.getByRole('button',{name:'메뉴 열기',exact:true}).click(); await p.getByRole('dialog',{name:'전체 메뉴',exact:true}).getByRole('link',{name:'마이페이지',exact:true}).click(); }
  else await p.locator('a[href="/mypage"]').first().click(); await p.getByRole('link',{name:'프로필·계정 설정',exact:true}).click(); await p.locator('#password-change-entry').click();
  return { p, ctx, state, d: p.getByRole('dialog', { name: '비밀번호 변경', exact: true }) };
}
const submit = f => f.d.getByRole('button', { name: '비밀번호 변경', exact: true });
async function fill(f) { for (const [key, value] of Object.entries(values)) await f.p.locator('#change-' + key).fill(value); }
async function check(name, fn) { try { await fn(); results.push({ name, pass: true }); console.log('PASS', name); } catch (e) { results.push({ name, pass: false, error: e.message }); console.error('FAIL', name, e.message); process.exitCode = 1; } finally { for (const c of browser.contexts()) await c.close(); } }
async function typeKeepingFocus(f, field, text) {
  const input = f.p.locator('#change-' + field);
  await input.click();
  await f.p.keyboard.press('End');
  const initial = await input.inputValue();
  for (const [index, character] of [...text].entries()) {
    // Real successive key input: fill() would refocus each field and hide this regression.
    await f.p.keyboard.type(character);
    await f.p.evaluate(() => new Promise(resolve => requestAnimationFrame(resolve)));
    assert.equal(await f.p.evaluate(() => document.activeElement?.id), 'change-' + field,
      `typing character ${index + 1} must keep focus in ${field}`);
    assert.equal(await input.inputValue(), initial + [...text].slice(0, index + 1).join(''));
  }
}
try {
  for (const width of [1440, 390, 320]) await check(`empty submit then consecutive typing keeps chosen field focus ${width}`, async () => {
    const f = await fixture(width), { p, d, state } = f;
    await submit(f).click();
    await p.waitForFunction(() => document.activeElement?.id === 'change-currentPassword');
    assert.equal(await d.getByRole('alert').count(), 3);
    try {
      await typeKeepingFocus(f, 'currentPassword', values.currentPassword);
      assert.equal(await d.getByRole('alert').count(), 2);
      // Even when a preceding field still has an error, the user chooses where to type.
      await typeKeepingFocus(f, 'newPasswordConfirm', values.newPasswordConfirm);
      assert.equal(await d.getByRole('alert').count(), 1);
      await typeKeepingFocus(f, 'newPassword', values.newPassword);
      assert.equal(await d.getByRole('alert').count(), 0);
      await p.keyboard.press('Tab');
      assert(await d.locator('[aria-controls="change-newPassword"]').evaluate(el => el === document.activeElement));
      await p.keyboard.press('Shift+Tab');
      assert.equal(await p.evaluate(() => document.activeElement?.id), 'change-newPassword');
      for (const [field, value] of Object.entries(values)) assert.equal(await p.locator('#change-' + field).inputValue(), value);
      assert.equal(state.posts, 0); assert.equal(state.csrfCount, 0);
      assert.deepEqual(state.pageErrors, []);
      await p.screenshot({ path: `${out}/typing-after-errors-${width}.png` });
    } catch (e) {
      await fs.writeFile(`${out}/typing-focus-${width}.json`, JSON.stringify(await p.evaluate(() => ({
        activeId: document.activeElement?.id,
        inputs: [...document.querySelectorAll('.password-change-field input')].map(el => ({ id: el.id, length: el.value.length })),
      })), null, 2));
      await p.screenshot({ path: `${out}/typing-after-errors-${width}.png` });
      throw e;
    }
  });
  await check('server field errors focus once; editing a different field does not jump back', async () => {
    const f = await fixture();
    f.state.change = r => r.fulfill(fail(400, 'VALIDATION_FAILED', { fieldErrors: Object.keys(values).map(field => ({ field, message: 'untrusted' })) }));
    await fill(f); await submit(f).click();
    await f.p.waitForFunction(() => !document.querySelector('form[aria-busy=true]') && document.activeElement?.id === 'change-currentPassword');
    assert.equal(await f.d.getByRole('alert').count(), 3);
    await typeKeepingFocus(f, 'newPasswordConfirm', 'x');
    assert.equal(await f.d.getByRole('alert').count(), 2);
    await f.p.keyboard.press('Backspace');
    assert.equal(await f.p.evaluate(() => document.activeElement?.id), 'change-newPasswordConfirm');
    assert.equal(await f.p.locator('#change-newPasswordConfirm').inputValue(), values.newPasswordConfirm);
    assert.equal(f.state.posts, 1);
  });
  for (const width of [1440, 390, 320]) await check(`layout, raw input, backdrop, keyboard, reset and short viewport ${width}`, async () => {
    const f = await fixture(width), { p, d, state } = f;
    assert(await p.locator('#password-change-close').evaluate(el => el === document.activeElement));
    await submit(f).click(); await p.locator('#change-currentPassword-error').waitFor(); assert(await p.locator('#change-currentPassword').evaluate(el => el === document.activeElement));
    assert.equal(await p.locator('#change-currentPassword-error').count(), 1); await fill(f);
    for (const key of Object.keys(values)) {
      const input = p.locator('#change-' + key), button = d.locator(`[aria-controls="change-${key}"]`);
      assert.equal(await input.getAttribute('autocomplete'), key === 'currentPassword' ? 'current-password' : 'new-password');
      await button.click(); assert.equal(await input.getAttribute('type'), 'text'); await button.click(); assert.equal(await input.inputValue(), values[key]);
    }
    if (width < 600) await p.touchscreen.tap(2, 2); else await p.mouse.click(2, 2);
    assert(await d.isVisible()); assert.equal(await p.locator('#change-currentPassword').inputValue(), values.currentPassword); assert.equal(state.posts, 0);
    await p.locator('#password-change-close').focus(); await p.keyboard.press('Shift+Tab'); assert(await submit(f).evaluate(el => el === document.activeElement));
    await p.keyboard.press('Tab'); assert(await p.locator('#password-change-close').evaluate(el => el === document.activeElement));
    await p.evaluate(() => document.fonts.ready); await p.screenshot({ path: `${out}/dialog-${width}.png` });
    assert(await d.evaluate(el => el.scrollWidth <= el.clientWidth)); assert(await p.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    for (const how of ['escape', 'cancel', 'close']) {
      if (how === 'escape') await p.keyboard.press('Escape'); else await d.getByRole('button', { name: how === 'cancel' ? '취소' : '비밀번호 변경 닫기', exact: true }).click();
      assert(await p.locator('#password-change-entry').evaluate(el => el === document.activeElement));
      await p.locator('#password-change-entry').click(); assert.equal(await p.locator('#change-currentPassword').inputValue(), ''); assert.equal(await d.getByRole('alert').count(), 0); await fill(f);
    }
    await p.setViewportSize({ width, height: 420 }); await submit(f).scrollIntoViewIfNeeded(); await p.screenshot({ path: `${out}/short-${width}.png` }); assert(await d.evaluate(el => el.scrollWidth <= el.clientWidth));
    await p.keyboard.press('Escape'); await p.setViewportSize({ width, height: 900 }); await p.locator('#password-change-entry').scrollIntoViewIfNeeded(); await p.screenshot({ path: `${out}/account-${width}.png` });
  });
  await check('duplicate submit and close blocked; successful fields clear before delayed follow-up CSRF; no auth remount', async () => {
    const f = await fixture(), response = gate(), arrived = gate(), follow = gate(), followArrived = gate();
    f.state.change = async r => { arrived.release(); await response.promise; return r.fulfill({ json: { reloginRequired: false } }); };
    f.state.csrf = async r => { if (f.state.csrfCount === 2) { followArrived.release(); await follow.promise; } return r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } }); };
    await fill(f); await submit(f).click(); await Promise.race([arrived.promise, new Promise((_, reject) => setTimeout(() => reject(Error('POST did not arrive')), 10000))]);
    await f.p.keyboard.press('Enter'); await f.p.keyboard.press('Escape'); await f.p.locator('#password-change-close').focus(); await f.p.keyboard.press('Enter'); await f.p.mouse.click(2, 2);
    assert(await f.d.isVisible()); assert(await f.d.getByRole('button', { name: '취소', exact: true }).isDisabled()); assert.equal(f.state.posts, 1);
    response.release(); await followArrived.promise; await f.d.getByText('비밀번호가 변경되었습니다. 현재 로그인은 유지됩니다.', { exact: true }).waitFor();
    assert.equal(await f.d.locator('input').count(), 0); assert(await f.p.locator('.mypage-profile').count());
    follow.release(); await f.p.waitForFunction(() => !document.querySelector('form[aria-busy=true]'));
    for (const width of [1440, 390, 320]) {
      await f.p.setViewportSize({ width, height: 900 });
      const hierarchy = await f.d.evaluate(dialog => {
        const success = dialog.querySelector('.password-change-success'), primary = success.querySelector('strong'), secondary = success.querySelector('span'), policy = dialog.querySelector('#password-change-policy');
        return { first: !!(success.compareDocumentPosition(policy) & Node.DOCUMENT_POSITION_FOLLOWING),
          primaryBottom: primary.getBoundingClientRect().bottom, secondaryTop: secondary.getBoundingClientRect().top,
          primarySize: parseFloat(getComputedStyle(primary).fontSize), secondarySize: parseFloat(getComputedStyle(secondary).fontSize),
          fits: dialog.scrollWidth <= dialog.clientWidth };
      });
      assert(hierarchy.first && hierarchy.primaryBottom <= hierarchy.secondaryTop);
      assert(hierarchy.primarySize > hierarchy.secondarySize && hierarchy.fits);
      await f.p.screenshot({ path: `${out}/completed-${width}.png` });
    }
    await f.d.getByRole('button', { name: '닫기', exact: true }).click(); assert(await f.p.locator('#password-change-entry').evaluate(el => el === document.activeElement)); assert.equal(f.state.posts, 1); assert.equal(f.state.csrfCount, 2);
  });
  await check('pre-submit CSRF failure sends no POST and preserves fields', async () => {
    const f=await fixture(); f.state.csrf=r=>r.fulfill(fail(503,'SERVICE_UNAVAILABLE'));
    await fill(f); await submit(f).click(); await f.d.getByRole('alert').waitFor();
    assert.equal(f.state.posts,0); assert.equal(await f.p.locator('#change-currentPassword').inputValue(),values.currentPassword);
    assert((await f.d.getByRole('alert').innerText()).includes('요청을 보내지 않았습니다'));
  });
  await check('CSRF rejection renews GET only; next manual submit gets a fresh token', async () => {
    const f=await fixture(); f.state.change=r=>r.fulfill(fail(403,'CSRF_TOKEN_INVALID'));
    await fill(f); await submit(f).click(); await f.d.getByRole('alert').waitFor();
    await f.p.waitForFunction(()=>!document.querySelector('form[aria-busy=true]'));
    assert.equal(f.state.posts,1); assert.equal(f.state.csrfCount,2);
    f.state.change=r=>r.fulfill({json:{reloginRequired:false}});
    await submit(f).click(); await f.d.getByRole('button',{name:'닫기',exact:true}).click();
    assert.equal(f.state.posts,2); assert.equal(f.state.csrfCount,4);
  });
  await check('confirmed success survives follow-up CSRF failure with separate warning', async () => {
    const f = await fixture(); f.state.csrf = r => f.state.csrfCount === 2 ? r.fulfill(fail(500, 'INTERNAL_SERVER_ERROR')) : r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } });
    await fill(f); await submit(f).click(); await f.d.getByRole('alert').waitFor(); assert((await f.d.getByRole('alert').innerText()).includes('변경은 완료')); assert.equal(await f.d.locator('input').count(), 0); assert.equal(f.state.posts, 1);
    await f.d.getByRole('button', { name: '닫기', exact: true }).click(); await f.p.locator('.mypage-toast').getByText('비밀번호가 변경되었습니다.', { exact: true }).waitFor();
  });
  for (const [status, code, field] of [[400,'VALIDATION_FAILED','newPasswordConfirm'],[400,'PASSWORD_CHANGE_INVALID_CURRENT_PASSWORD','currentPassword'],[400,'PASSWORD_CHANGE_INVALID_NEW_PASSWORD','newPassword'],[400,'PASSWORD_CHANGE_CURRENT_PASSWORD_MISMATCH','currentPassword'],[400,'PASSWORD_CHANGE_PASSWORD_UNCHANGED','newPassword'],[403,'CSRF_TOKEN_INVALID'],[403,'ACCESS_DENIED'],[500,'INTERNAL_SERVER_ERROR']]) await check(`safe error ${code}, inputs and no replay`, async () => {
    const f = await fixture(); f.state.change = r => r.fulfill(fail(status, code, { fieldErrors: [{ field: 'newPasswordConfirm', message: 'untrusted' }] })); await fill(f); await submit(f).click(); await f.d.getByRole('alert').waitFor();
    if (field) { assert.equal(await f.d.getByRole('alert').count(), 1); await f.p.waitForFunction(id => document.activeElement?.id === id, 'change-' + field); }
    else if (status === 500) assert((await f.d.getByRole('alert').innerText()).includes('이미 변경되었을 수'));
    assert(!(await f.d.innerText()).includes('untrusted')); assert.equal(await f.p.locator('#change-currentPassword').inputValue(), values.currentPassword); assert.equal(f.state.posts, 1);
    if (code === 'CSRF_TOKEN_INVALID') { await f.p.waitForFunction(() => !document.querySelector('form[aria-busy=true]')); assert.equal(f.state.csrfCount, 2); }
    if (field === 'currentPassword') await f.p.screenshot({ path: `${out}/field-error-${code}.png` });
  });
  for (const relogin of [true, false]) await check(relogin ? '200 relogin expires private state with success notice, no logout POST' : '401 expires current generation and offers safe login return', async () => {
    const f = await fixture(); f.state.change = r => { f.state.member = null; return r.fulfill(relogin ? { json: { reloginRequired: true } } : fail(401, 'AUTHENTICATION_REQUIRED')); };
    await fill(f); await submit(f).click(); await f.p.getByRole('heading', { name: '로그인이 필요해요' }).waitFor(); assert.equal(await f.p.locator('.mypage-profile').count(), 0);
    assert((await f.p.getByRole('link', { name: '로그인하기', exact: true }).getAttribute('href')).includes('from=%2Fmypage')); assert.equal(f.state.logout, 0);
    assert((await f.p.locator('.mypage-notice').innerText()).includes(relogin ? '변경되었습니다' : '만료'));
  });
  for (const header of ['1', null, 'bad']) await check(`429 Retry-After ${header}, no automatic retry`, async () => {
    const f = await fixture(); f.state.change = r => r.fulfill({ ...fail(429,'PASSWORD_CHANGE_RATE_LIMITED'), headers: header === null ? {} : { 'Retry-After': header, 'Access-Control-Expose-Headers': 'Retry-After' } });
    await fill(f); await submit(f).click(); await f.d.getByRole('alert').waitFor();
    if (header === '1') { assert(await submit(f).isDisabled()); await f.p.waitForFunction(() => !document.querySelector('.password-change-actions button[type="submit"]:disabled') && !document.querySelector('.password-change-actions button:last-child').disabled); }
    else assert.equal(await submit(f).isDisabled(), false);
    assert.equal(f.state.posts, 1);
  });
  for (const kind of ['html','json','missing','string','network']) await check(`uncertain ${kind} response never guessed or retried`, async () => {
    const f = await fixture(); f.state.change = r => kind === 'network' ? r.abort('failed') : r.fulfill(kind === 'html' ? { status:200, contentType:'text/html', body:'<html>untrusted</html>' } : kind === 'json' ? { status:200, contentType:'application/json', body:'{' } : { json: kind === 'missing' ? {} : { reloginRequired:'false' } });
    await fill(f); await submit(f).click(); await f.d.getByRole('alert').waitFor(); assert((await f.d.getByRole('alert').innerText()).includes('이미 변경되었을 수')); assert.equal(f.state.posts,1); assert.equal(await f.p.locator('#change-newPassword').inputValue(), values.newPassword);
  });
  for (const late of ['401','relogin','success']) await check(`approved departure and same-member login ignore late ${late}`, async () => {
    const f = await fixture(), arrived = gate(), response = gate(), finished = gate();
    f.state.change = async r => { arrived.release(); await response.promise; await r.fulfill(late === '401' ? fail(401,'AUTHENTICATION_REQUIRED') : { json:{reloginRequired:late==='relogin'} }); finished.release(); };
    await fill(f); await submit(f).click(); await Promise.race([arrived.promise, new Promise((_, reject) => setTimeout(() => reject(Error('POST did not arrive')), 10000))]); await f.p.goBack();
    const leave = f.p.getByRole('dialog', {name:'비밀번호 변경 화면을 나갈까요?',exact:true}); await leave.waitFor(); await leave.getByRole('button',{name:'계속 입력'}).click(); assert.equal(await f.p.locator('#change-newPassword').inputValue(),values.newPassword);
    await f.p.goBack(); await leave.getByRole('button',{name:'나가기',exact:true}).click(); await f.p.waitForURL(u=>u.pathname==='/mypage');
    await f.p.getByRole('button',{name:'로그아웃',exact:true}).click();
    await f.p.getByRole('link',{name:'로그인하기',exact:true}).click();await f.p.waitForURL(u=>u.pathname==='/login');
    await f.p.locator('input[name=email]').fill('review@example.test'); await f.p.locator('input[name=password]').fill('synthetic-login'); await f.p.locator('main').getByRole('button',{name:'로그인',exact:true}).click(); await f.p.waitForURL(u=>u.pathname!=='/login');
    // A new document is not needed: use an actual same-origin AppLink back to mypage.
    await f.p.locator('a[href="/mypage"]').first().click(); await f.p.getByRole('link',{name:'프로필·계정 설정',exact:true}).click(); await f.p.locator('#password-change-entry').waitFor(); response.release(); await finished.promise;
    assert(await f.p.locator('#password-change-entry').isVisible()); assert.equal(await f.p.getByRole('heading',{name:'로그인이 필요해요'}).count(),0); assert.equal(await f.p.locator('.mypage-notice.has-notice').count(),0); assert.equal(f.state.logout,1);
  });
} finally { await fs.writeFile(`${out}/results.json`, JSON.stringify({ mode:'mock API only', results }, null, 2)); await browser.close(); }
