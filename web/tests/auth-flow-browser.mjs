// 이탈 의도/지연된 RSC/인증→로그인 전달 회귀. 응답은 전부 모의하며 DB를 변경하지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const output = process.env.AUTH_FLOW_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-auth-flow-'));
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome', headless: true });
const results = [], errors = [];
let current;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const gate = () => { let release; const promise = new Promise(resolve => { release = resolve; }); return { promise, release }; };
async function waitFor(fn) { const until = Date.now() + 15000; while (Date.now() < until) { if (await fn()) return; await delay(30); } throw Error('condition timed out'); }
async function check(name, fn) { current = name; await fn(); results.push(name); console.log('PASS', name); }
const member = { memberId: 77, email: 'existing@example.test', nickname: '기존회원', role: 'USER' };
const token = 'A'.repeat(43), verifyUrl = base + '/register/verify?token=' + token;
const completed = '이메일 인증이 완료되었습니다. 로그인해 주세요.';
const failure = code => ({ code, message: 'private details', fieldErrors: [] });
async function setup(authenticated = false) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', reducedMotion: 'reduce' });
  const page = await context.newPage();
  context.on('page', page => page.on('pageerror', error => errors.push(error.name)));
  page.on('pageerror', error => errors.push(error.name));
  page.setDefaultTimeout(15000);
  const state = { authenticated, csrf: null, signup: null, verify: null, calls: { csrf: 0, signup: 0, verify: 0, me: 0, login: 0, logout: 0 } };
  await context.route(api + '/api/v1/auth/**', async route => {
    const endpoint = new URL(route.request().url()).pathname.split('/').at(-1); state.calls[endpoint]++;
    if (endpoint === 'me') return route.fulfill(state.authenticated ? { json: member } : { status: 401, json: failure('AUTHENTICATION_REQUIRED') });
    if (state[endpoint]) return state[endpoint](route);
    if (endpoint === 'csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf' } });
    if (endpoint === 'signup') return route.fulfill({ status: 201, json: { status: 'PENDING' } });
    if (endpoint === 'verify') return route.fulfill({ json: { status: 'ACTIVE' } });
    if (endpoint === 'login') { state.authenticated = true; return route.fulfill({ json: member }); }
    if (endpoint === 'logout') { state.authenticated = false; return route.fulfill({ status: 204 }); }
    throw Error('unexpected mocked endpoint');
  });
  return { context, page, state };
}
const signup = p => p.getByRole('button', { name: '가입하기', exact: true });
const verify = p => p.getByRole('button', { name: '이메일 인증하기', exact: true });
const logo = p => p.getByRole('link', { name: 'IYUM 홈으로 이동' });
async function form(page) {
  await page.goto(base + '/register'); await page.getByRole('checkbox', { name: '전체 동의', exact: true }).check();
  await page.getByRole('button', { name: '다음', exact: true }).click(); await page.waitForURL('**/register/info');
  await page.locator('input[autocomplete=email]').fill('signup-flow@example.test');
  await page.locator('input[autocomplete=new-password]').nth(0).fill('Example123!');
  await page.locator('input[autocomplete=new-password]').nth(1).fill('Example123!');
  await page.locator('input[autocomplete=nickname]').fill('흐름검증');
}
async function holdDestination(context, pathname) {
  const held = gate(); let started = false;
  await context.route(url => url.origin === base && url.pathname === pathname, async route => {
    if (route.request().headers().rsc === '1') { started = true; await held.promise; }
    await route.continue();
  });
  return { release: held.release, started: () => started };
}
async function screenshot(page, name) {
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
    await page.evaluate(async () => { await document.fonts.ready; await new Promise(requestAnimationFrame); await new Promise(requestAnimationFrame); });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: path.join(output, `${name}-${width}.png`), fullPage: true });
  }
}
try {
  for (const stage of ['csrf', 'signup-success', 'signup-failure', 'signup-csrf-error', 'csrf-refresh']) {
    await check(`가입 ${stage} 대기 중 로고 이탈: 목적지 RSC 지연·기존 폼 마운트·후속 처리 무효`, async () => {
      const { context, page, state } = await setup(); const held = gate(); let replied = false;
      const destination = await holdDestination(context, '/');
      await form(page);
      if (stage === 'csrf') state.csrf = async route => { await held.promise; await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf' } }); replied = true; };
      else if (stage === 'csrf-refresh') {
        state.signup = route => route.fulfill({ status: 403, json: failure('CSRF_TOKEN_INVALID') });
        state.csrf = async route => { if (state.calls.csrf > 1) await held.promise; await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf' } }); if (state.calls.csrf > 1) replied = true; };
      } else state.signup = async route => { await held.promise; await route.fulfill(stage === 'signup-success' ? { status: 201, json: { status: 'PENDING' } } : { status: stage === 'signup-csrf-error' ? 403 : 500, json: failure(stage === 'signup-csrf-error' ? 'CSRF_TOKEN_INVALID' : 'SERVER_ERROR') }); replied = true; };
      await signup(page).click(); await waitFor(() => stage === 'csrf' ? state.calls.csrf === 1 : stage === 'csrf-refresh' ? state.calls.csrf === 2 : state.calls.signup === 1);
      await logo(page).click(); await waitFor(destination.started);
      assert(await page.locator('form').isVisible()); assert.equal(new URL(page.url()).pathname, '/register/info');
      const active = await page.evaluate(() => document.activeElement?.outerHTML);
      held.release(); await waitFor(() => replied); await delay(200);
      assert.equal(state.calls.signup, stage === 'csrf' ? 0 : 1); assert.equal(state.calls.csrf, stage === 'csrf-refresh' ? 2 : 1);
      assert(await page.locator('form').isVisible()); assert.equal(new URL(page.url()).pathname, '/register/info');
      assert.equal(await page.getByRole('main').getByRole('alert').count(), 0); assert.equal(await page.evaluate(() => document.activeElement?.outerHTML), active);
      destination.release(); await page.waitForURL(base + '/'); await delay(100); assert.equal(page.url(), base + '/');
      await context.close();
    });
  }
  await check('가입 이탈·새 가입 화면 재진입 후 이전 성공이 새 입력/가입 상태를 변경하지 않음', async () => {
    const { context, page, state } = await setup(); await form(page); const held = gate(); let replied = false;
    state.signup = async route => { await held.promise; await route.fulfill({ status: 201, json: { status: 'PENDING' } }); replied = true; };
    await signup(page).click(); await waitFor(() => state.calls.signup === 1); await logo(page).click(); await page.waitForURL(base + '/');
    await form(page); await page.locator('input[autocomplete=email]').fill('new-entry@example.test');
    held.release(); await waitFor(() => replied); await delay(150);
    assert.equal(new URL(page.url()).pathname, '/register/info'); assert.equal(await page.locator('input[autocomplete=email]').inputValue(), 'new-entry@example.test'); assert(await signup(page).isEnabled());
    await page.goto(base + '/register/verify'); assert.equal(await page.getByText('이메일 인증 대기 · PENDING', { exact: true }).count(), 0); await context.close();
  });
  await check('가입 CSRF 대기 중 브라우저 뒤로 가기: 새 POST 없음', async () => {
    const { context, page, state } = await setup(); await form(page); const held = gate(); let replied = false;
    state.csrf = async route => { await held.promise; await route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf' } }); replied = true; };
    await signup(page).click(); await waitFor(() => state.calls.csrf === 1); await page.goBack(); await page.waitForURL(base + '/register');
    held.release(); await waitFor(() => replied); await delay(100); assert.equal(state.calls.signup, 0); await context.close();
  });
  for (const keyboard of [false, true]) await check(`약관 취소 ${keyboard ? '키보드' : '마우스'}: 홈 이동·기존 세션 유지`, async () => {
    const { context, page, state } = await setup(true); await page.goto(base + '/register'); await page.getByText('기존회원님', { exact: true }).waitFor();
    const cancel = page.getByRole('button', { name: '취소', exact: true }); if (keyboard) { await cancel.focus(); await page.keyboard.press('Enter'); } else await cancel.click();
    await page.waitForURL(base + '/'); assert.equal(state.calls.logout, 0); assert.equal(state.calls.login, 0); await page.getByText('기존회원님', { exact: true }).waitFor(); await context.close();
  });
  await check('새 탭 수동 인증 → 200/ACTIVE → replace 로그인·일회성 안내·키보드·320/390/1440', async () => {
    const { context, page: original, state } = await setup(); const destination = await holdDestination(context, '/login'); await original.goto(base + '/register');
    const page = await context.newPage(); await page.goto(base + '/'); await page.goto(verifyUrl); await verify(page).waitFor();
    await page.getByText('인증이 완료되면 로그인 화면으로 이동합니다.', { exact: false }).waitFor();
    assert.equal(state.calls.verify, 0); assert.equal(state.calls.csrf, 0);
    await verify(page).focus(); await page.keyboard.press('Enter'); await waitFor(destination.started);
    await page.getByText('이메일 인증을 확인했습니다. 로그인 화면으로 이동하고 있습니다.').waitFor();
    assert.equal(state.calls.verify, 1); assert.equal(await page.getByRole('button', { name: /인증하기|인증 중/ }).count(), 0);
    destination.release(); await page.waitForURL(base + '/login'); await page.getByText(completed, { exact: true }).waitFor();
    const status = page.locator('main [role=status]'); assert(await status.evaluate(el => el === document.activeElement));
    assert.equal(state.calls.login, 0); assert.equal(new URL(original.url()).pathname, '/register');
    assert(!page.url().includes(token)); const storage = await page.evaluate(() => JSON.stringify({ session: { ...sessionStorage }, local: { ...localStorage } })); assert(!storage.includes(token)); assert(!storage.includes(completed));
    await screenshot(page, 'verification-login-success'); await page.keyboard.press('Tab'); assert(await page.locator('input[name=email]').evaluate(el => el === document.activeElement));
    await page.goBack(); await page.waitForURL(base + '/'); assert.equal(state.calls.verify, 1);
    await page.goForward(); await page.waitForURL(base + '/login'); assert.equal(await page.getByText(completed, { exact: true }).count(), 0);
    await page.reload(); assert.equal(await page.getByText(completed, { exact: true }).count(), 0); assert.equal(state.calls.verify, 1); assert.equal(state.calls.login, 0);
    await context.close();
  });
  await check('기존 로그인 세션: 인증 계정과 동일하다고 추정하지 않음·자동 로그인/로그아웃 없음', async () => {
    const { context, page, state } = await setup(true); await page.goto(verifyUrl); await verify(page).click(); await page.waitForURL(base + '/login');
    await page.getByText(completed, { exact: true }).waitFor(); await page.getByText('기존회원님으로 로그인되어 있습니다.').waitFor();
    await page.getByText('방금 인증한 계정과 같은 계정인지 이 안내만으로 확인할 수 없습니다.', { exact: false }).waitFor();
    assert.equal(state.calls.login, 0); assert.equal(state.calls.logout, 0); await context.close();
  });
  const bad = [
    ['만료', r => r.fulfill({ status: 400, json: failure('EMAIL_VERIFICATION_INVALID') })],
    ['CSRF', r => r.fulfill({ status: 403, json: failure('CSRF_TOKEN_INVALID') })],
    ['네트워크', r => r.abort('connectionfailed')],
    ['HTML', r => r.fulfill({ status: 200, contentType: 'text/html', body: '<html>private details</html>' })],
    ['손상 JSON', r => r.fulfill({ status: 200, contentType: 'application/json', body: '{' })],
    ['201 ACTIVE', r => r.fulfill({ status: 201, json: { status: 'ACTIVE' } })],
    ['200 PENDING', r => r.fulfill({ status: 200, json: { status: 'PENDING' } })],
    ['200 빈 객체', r => r.fulfill({ json: {} })],
  ];
  for (const [name, response] of bad) await check(`인증 ${name}: 오류 초점·전환 없음·완료 안내 없음·자동 재시도 없음`, async () => {
    const { context, page, state } = await setup(); state.verify = response;
    await page.goto(verifyUrl); await verify(page).click(); const alert = page.getByRole('main').getByRole('alert'); await alert.waitFor();
    assert(await alert.evaluate(el => el === document.activeElement)); assert.equal(new URL(page.url()).pathname, '/register/verify'); assert(!(await alert.innerText()).includes('private details'));
    assert.equal(state.calls.verify, 1); assert.equal(state.calls.login, 0);
    if (name === '만료') { await page.getByText('이미 인증을 완료했다면 로그인해 주세요.', { exact: false }).waitFor(); assert.equal(await page.getByRole('link', { name: '로그인 화면으로 이동' }).getAttribute('href'), '/login'); }
    await page.goto(base + '/login'); assert.equal(await page.getByText(completed, { exact: true }).count(), 0); await context.close();
  });
  await check('인증 CSRF 조회 실패: 인증 POST 없음·수동 재시도 유지', async () => {
    const { context, page, state } = await setup(); state.csrf = r => r.fulfill({ status: 500, json: failure('SERVER_ERROR') });
    await page.goto(verifyUrl); await verify(page).click(); await page.getByRole('main').getByRole('alert').waitFor(); assert.equal(state.calls.verify, 0);
    state.csrf = null; await page.getByRole('button', { name: '다시 인증하기' }).click(); await page.waitForURL(base + '/login'); assert.equal(state.calls.verify, 1); await context.close();
  });
  await check('형식 오류 링크: 재전송·조건부 로그인 안내, 인증 POST 없음', async () => {
    const { context, page, state } = await setup(); await page.goto(base + '/register/verify?token=invalid'); await page.getByRole('main').getByRole('alert').waitFor();
    await page.getByText('이미 인증을 완료했다면 로그인해 주세요.', { exact: false }).waitFor(); assert(await page.getByRole('link', { name: '인증 메일 다시 요청하기' }).isVisible());
    await page.getByRole('link', { name: '로그인 화면으로 이동' }).click(); await page.waitForURL(base + '/login'); assert.equal(await page.getByText(completed, { exact: true }).count(), 0); assert.equal(state.calls.verify, 0); await context.close();
  });
  for (const stage of ['csrf', 'success', 'failure']) await check(`인증 ${stage} 대기 중 이탈: 목적지 지연·폼 마운트 상태에도 늦은 응답 무효`, async () => {
    const { context, page, state } = await setup(); const held = gate(); let replied = false; const destination = await holdDestination(context, '/');
    if (stage === 'csrf') state.csrf = async r => { await held.promise; await r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf' } }); replied = true; };
    else state.verify = async r => { await held.promise; await r.fulfill(stage === 'success' ? { json: { status: 'ACTIVE' } } : { status: 500, json: failure('SERVER_ERROR') }); replied = true; };
    await page.goto(verifyUrl); await verify(page).click(); await waitFor(() => stage === 'csrf' ? state.calls.csrf === 1 : state.calls.verify === 1);
    await logo(page).click(); await waitFor(destination.started); assert(await page.getByRole('button', { name: '인증 중…' }).isVisible());
    const active = await page.evaluate(() => document.activeElement?.outerHTML);
    held.release(); await waitFor(() => replied); await delay(150); assert.equal(new URL(page.url()).pathname, '/register/verify');
    assert.equal(await page.getByRole('main').getByRole('alert').count(), 0); assert.equal(await page.evaluate(() => document.activeElement?.outerHTML), active); assert.equal(state.calls.verify, stage === 'csrf' ? 0 : 1);
    destination.release(); await page.waitForURL(base + '/'); await page.goto(base + '/login'); assert.equal(await page.getByText(completed, { exact: true }).count(), 0); assert.equal(state.calls.login, 0); await context.close();
  });
  await check('직접 /login 또는 임의 verified 쿼리는 완료 안내를 만들지 않음', async () => {
    const { context, page } = await setup(); await page.goto(base + '/login?verified=true'); assert.equal(await page.getByText(completed, { exact: true }).count(), 0); await context.close();
  });
  await check('런타임 오류 없음', async () => assert.deepEqual(errors, []));
} catch (error) { console.error('FAILED', current, error.message, error.stack?.split('\n').slice(1, 3).join('\n')); process.exitCode = 1; }
finally { await browser.close(); await fs.writeFile(path.join(output, 'results.json'), JSON.stringify({ mode: 'browser API mocks; no real DB writes', checks: results, errors, completed: !process.exitCode }, null, 2)); }
