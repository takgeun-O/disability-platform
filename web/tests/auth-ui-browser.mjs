// 표시/접근성 회귀는 API 모의, IYUM_RUN_LOCAL_AUTH_UI_TEST=true에서만 실제 회원 1명 생성.
// 비밀번호·쿠키·토큰·메일 원문·전체 인증 URL은 결과/로그/trace에 남기지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const mailpit = process.env.MAILPIT_BASE_URL ?? 'http://127.0.0.1:8025';
const output = process.env.AUTH_UI_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-auth-ui-'));
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome', headless: true });
const checks = [], createdMembers = [], errors = [], layout = [];
let current, mode = 'mock';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function check(name, run) { current = name; await run(); checks.push({ mode, name }); console.log('PASS', mode, name); }
async function waitFor(predicate) { const deadline = Date.now() + 15000; while (Date.now() < deadline) { const result = await predicate(); if (result) return result; await delay(50); } throw Error('Condition not reached'); }
async function newContext() {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', reducedMotion: 'reduce' });
  const page = await context.newPage(); page.setDefaultTimeout(15000);
  page.on('pageerror', () => errors.push({ mode, check: current }));
  return { context, page };
}
async function geometry(page, name, width) {
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'horizontal overflow');
  const dimensions = await page.locator('.auth-screen').evaluate(el => {
    const first = el.querySelector('input:not([type=checkbox])'); const primary = el.querySelector('.iyum-button-primary');
    return { headingTop: el.querySelector('h1').getBoundingClientRect().top,
      firstInputTop: first?.getBoundingClientRect().top, inputFont: first && parseFloat(getComputedStyle(first).fontSize),
      inputHeight: first?.getBoundingClientRect().height, primaryHeight: primary?.getBoundingClientRect().height };
  });
  layout.push({ mode, name, width, ...dimensions });
  if (dimensions.inputFont) { assert(dimensions.inputFont >= 16); assert(dimensions.inputHeight >= 44); }
  if (dimensions.primaryHeight) assert(dimensions.primaryHeight >= 44);
  if (width < 600) { assert(dimensions.headingTop < 245); if (dimensions.firstInputTop) assert(dimensions.firstInputTop < 420); }
}
async function capture(page, name) {
  await page.getByText('로그인 확인 중…', { exact: true }).waitFor({ state: 'hidden' });
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
    await page.evaluate(async () => { await document.fonts.ready; await new Promise(requestAnimationFrame); await new Promise(requestAnimationFrame); }); await geometry(page, name, width);
    await page.screenshot({ path: path.join(output, `${mode}-${name}-${width}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 320, height: 740 }); await page.evaluate(() => new Promise(requestAnimationFrame)); await geometry(page, name, 320);
  await page.setViewportSize({ width: 1440, height: 1000 });
}
async function consent(page) {
  await page.goto(base + '/register'); await page.getByRole('checkbox', { name: '전체 동의', exact: true }).check();
  await page.getByRole('button', { name: '다음', exact: true }).click(); await page.waitForURL('**/register/info');
}
async function fillSignup(page, email, password, nickname) {
  await page.locator('input[autocomplete=email]').fill(email);
  await page.locator('input[autocomplete=new-password]').nth(0).fill(password);
  await page.locator('input[autocomplete=new-password]').nth(1).fill(password);
  await page.locator('input[autocomplete=nickname]').fill(nickname);
}
const signupButton = page => page.getByRole('button', { name: '가입하기', exact: true });
const errorBody = code => ({ code, message: '검증용 서버 응답', fieldErrors: [] });
const sample = { memberId: 77, email: 'auth-ui@example.test', nickname: '화면검증', role: 'USER' };
try {
  const { page, context } = await newContext();
  const state = { authenticated: false, signup: null, verify: null, resend: null, calls: { signup: 0, verify: 0, resend: 0, csrf: 0, login: 0, logout: 0 } };
  await context.route(api + '/api/v1/auth/**', async route => {
    const endpoint = new URL(route.request().url()).pathname.split('/').at(-1);
    if (endpoint === 'me') return route.fulfill(state.authenticated ? { json: sample } : { status: 401, json: errorBody('AUTHENTICATION_REQUIRED') });
    state.calls[endpoint]++;
    if (endpoint === 'csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'auth-ui-mock-csrf' } });
    if (endpoint === 'login') { state.authenticated = true; return route.fulfill({ json: sample }); }
    if (endpoint === 'logout') { state.authenticated = false; return route.fulfill({ status: 204 }); }
    if (state[endpoint]) return state[endpoint](route);
    if (endpoint === 'signup') return route.fulfill({ status: 201, json: { status: 'PENDING' } });
    if (endpoint === 'verify') return route.fulfill({ json: { status: 'ACTIVE' } });
    if (endpoint === 'resend') return route.fulfill({ status: 202, json: { status: 'ACCEPTED' } });
    throw Error('Unexpected mocked endpoint');
  });
  await check('로그인 1440/390/320: 입력·버튼 크기·상단 배치·키보드 비밀번호 토글', async () => {
    await page.goto(base + '/login'); await page.getByRole('form').waitFor(); await capture(page, 'login');
    await page.locator('input[name=email]').focus(); await page.keyboard.press('Tab');
    assert.equal(await page.locator('input[name=password]').evaluate(el => el === document.activeElement), true);
    assert.equal(await page.locator('input[name=password]').evaluate(el => getComputedStyle(el).outlineStyle), 'solid');
    await page.keyboard.press('Tab'); const toggle = page.getByRole('button', { name: '비밀번호 표시', exact: true });
    assert(await toggle.evaluate(el => el === document.activeElement)); await page.keyboard.press('Space');
    assert.equal(await page.locator('input[name=password]').getAttribute('type'), 'text');
    assert.equal(await page.getByRole('button', { name: '비밀번호 숨기기' }).getAttribute('aria-pressed'), 'true');
    await page.keyboard.press('Space'); assert.equal(await page.locator('input[name=password]').getAttribute('type'), 'password');
  });
  await check('로그인 입력 오류: 요청 없음·첫 오류 초점·설명 연결', async () => {
    await page.getByRole('form').getByRole('button', { name: '로그인', exact: true }).click();
    const input = page.locator('input[name=email]'); assert.equal(state.calls.login, 0);
    assert(await input.evaluate(el => el === document.activeElement)); assert.equal(await input.getAttribute('aria-invalid'), 'true');
    const id = await input.getAttribute('aria-describedby'); assert(await page.locator(`[id="${id}"]`).isVisible()); await capture(page, 'login-validation');
  });
  await check('본문·입력·placeholder·주 버튼·초점의 대비', async () => {
    const colors = await page.locator('input[name=email]').evaluate(el => {
      const css = getComputedStyle(el); const primary = document.querySelector('main .iyum-button-primary');
      return { text: css.color, background: css.backgroundColor, placeholder: getComputedStyle(el, '::placeholder').color,
        label: getComputedStyle(document.querySelector('.auth-label')).color, primary: getComputedStyle(primary).backgroundColor,
        buttonText: getComputedStyle(primary).color, focus: css.outlineColor };
    });
    const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(v => { v /= 255; return v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4; }).reduce((sum, v, i) => sum + v * [.2126, .7152, .0722][i], 0);
    const ratio = (a, b) => (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05);
    for (const key of ['text', 'placeholder', 'label']) assert(ratio(colors[key], colors.background) >= 4.5, key + ' contrast');
    assert(ratio(colors.primary, colors.buttonText) >= 4.5); assert(ratio(colors.focus, colors.background) >= 3);
  });
  await check('약관 키보드 Space·부분 동의·필수 오류·모바일', async () => {
    await page.goto(base + '/register'); await capture(page, 'consent');
    await page.getByRole('button', { name: '다음', exact: true }).click(); await page.getByRole('main').getByRole('alert').waitFor();
    const all = page.getByRole('checkbox', { name: '전체 동의', exact: true }); await all.focus(); await page.keyboard.press('Space');
    assert(await page.getByRole('checkbox', { name: /이용약관/ }).isChecked());
    const privacy = page.getByRole('checkbox', { name: /개인정보 수집/ }); await privacy.focus(); await page.keyboard.press('Space');
    assert(await all.evaluate(el => el.indeterminate)); await page.keyboard.press('Space');
    assert(await page.getByRole('checkbox', { name: /마케팅/ }).isDisabled());
    await page.getByRole('button', { name: '다음', exact: true }).click(); await page.waitForURL('**/register/info'); await capture(page, 'signup-info');
  });
  await check('가입 오류 필드와 초점 연결·표시 토글·약관 유지', async () => {
    await signupButton(page).click(); const email = page.locator('input[autocomplete=email]');
    assert(await email.evaluate(el => el === document.activeElement)); assert.equal(state.calls.signup, 0);
    for (const input of await page.locator('input[aria-invalid=true]').all()) {
      const id = await input.getAttribute('aria-describedby'); assert(await page.locator(`[id="${id}"]`).isVisible());
    }
    await capture(page, 'signup-validation');
    await page.getByRole('button', { name: '이전', exact: true }).click(); await page.waitForURL('**/register');
    assert(await page.getByRole('checkbox', { name: '전체 동의' }).isChecked());
    await page.getByRole('button', { name: '다음', exact: true }).click(); await page.waitForURL('**/register/info');
  });
  await check('가입 서버 필드 오류·CSRF 복구 후 수동 재시도만 허용', async () => {
    await fillSignup(page, sample.email, 'Example123!', sample.nickname);
    state.signup = route => route.fulfill({ status: 409, json: errorBody('EMAIL_ALREADY_EXISTS') });
    await signupButton(page).click(); await page.locator('input[autocomplete=email][aria-invalid=true]').waitFor();
    assert(await page.locator('input[autocomplete=email]').evaluate(el => el === document.activeElement));
    state.signup = route => route.fulfill({ status: 403, json: errorBody('CSRF_TOKEN_INVALID') });
    const count = state.calls.signup; const csrf = state.calls.csrf; await signupButton(page).click();
    await page.getByRole('alert').filter({ hasText: '보안 확인 정보를 갱신했습니다.' }).waitFor();
    await delay(150); assert.equal(state.calls.signup, count + 1); assert.equal(state.calls.csrf, csrf + 1);
    assert(await signupButton(page).isEnabled()); state.signup = null;
  });
  await check('가입 요청 중 비활성·중복 차단·201/PENDING만 대기·자동 로그인 없음', async () => {
    let release; const gate = new Promise(resolve => { release = resolve; });
    state.signup = async route => {
      const request = route.request().postDataJSON(); assert.equal(request.email, sample.email);
      assert.equal(request.agreements.length, 2); assert(request.agreements.every(item => item.agreed && item.version === 'dev-v1'));
      await gate; return route.fulfill({ status: 201, json: { status: 'PENDING' } });
    };
    const count = state.calls.signup; await page.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
    await waitFor(() => state.calls.signup === count + 1); await page.locator('fieldset[disabled]').waitFor(); assert(await page.locator('input[autocomplete=email]').isDisabled());
    await capture(page, 'signup-processing'); release(); await page.waitForURL('**/register/verify');
    await page.getByRole('heading', { name: '회원가입 요청이 완료되었습니다' }).waitFor();
    assert.equal(state.calls.login, 0); await capture(page, 'signup-pending'); state.signup = null;
    await page.reload(); await page.getByRole('heading', { name: '이메일 인증 안내' }).waitFor();
    assert.equal(await page.getByRole('heading', { name: '회원가입 요청이 완료되었습니다' }).count(), 0);
    await capture(page, 'verification-missing');
  });
  await check('이메일 인증 대기·진행·성공·실패와 초점·재전송 연결', async () => {
    await page.goto(base + '/register/verify?token=invalid'); await page.getByRole('main').getByRole('alert').waitFor(); await capture(page, 'verification-invalid-link');
    const url = base + '/register/verify?token=' + 'A'.repeat(43); await page.goto(url);
    const button = page.getByRole('button', { name: '이메일 인증하기', exact: true }); await button.waitFor(); await capture(page, 'verification-ready');
    let release; const gate = new Promise(resolve => { release = resolve; });
    state.verify = async route => { await gate; return route.fulfill({ json: { status: 'ACTIVE' } }); };
    const count = state.calls.verify; await button.click(); await waitFor(() => state.calls.verify === count + 1);
    assert(await page.getByRole('button', { name: '인증 중…' }).isDisabled()); await capture(page, 'verification-processing');
    release(); await page.waitForURL('**/login'); await page.getByText('이메일 인증이 완료되었습니다. 로그인해 주세요.', { exact: true }).waitFor();
    assert(await page.locator('main [role=status]').evaluate(el => el === document.activeElement)); await capture(page, 'verification-success');
    assert.equal(state.calls.login, 0);
    state.verify = route => route.fulfill({ status: 400, json: { code: 'EMAIL_VERIFICATION_INVALID' } });
    await page.goto(url); await button.click(); await page.getByRole('main').getByRole('alert').waitFor();
    assert(await page.getByRole('main').getByRole('alert').evaluate(el => el === document.activeElement)); await capture(page, 'verification-expired');
    await page.getByRole('link', { name: '인증 메일 다시 요청하기' }).click(); await page.waitForURL('**/register/resend');
  });
  await check('재전송 오류·진행·접수의 구분·새 CSRF·입력 보존', async () => {
    await capture(page, 'resend'); const button = page.getByRole('button', { name: '인증 메일 재전송 요청' });
    await button.click(); assert(await page.getByLabel('이메일', { exact: true }).evaluate(el => el === document.activeElement)); await capture(page, 'resend-validation');
    await page.getByLabel('이메일', { exact: true }).fill(sample.email);
    let release; const gate = new Promise(resolve => { release = resolve; }); state.resend = async route => { await gate; return route.fulfill({ status: 202, json: { status: 'ACCEPTED' } }); };
    await button.click(); await page.getByRole('button', { name: '요청 중…' }).waitFor(); await capture(page, 'resend-processing');
    release(); await page.locator('#resend-feedback[role=status]').waitFor();
    assert.match(await page.locator('#resend-feedback').innerText(), /조건을 충족하면/); assert(await page.locator('#resend-feedback').evaluate(el => el === document.activeElement));
    await capture(page, 'resend-accepted');
    state.resend = route => route.fulfill({ status: 500, json: errorBody('SERVER_ERROR') });
    await button.click(); await page.getByRole('main').getByRole('alert').waitFor(); assert.equal(await page.getByLabel('이메일', { exact: true }).inputValue(), sample.email); await capture(page, 'resend-error');
  });
  await context.close();
  if (process.env.IYUM_RUN_LOCAL_AUTH_UI_TEST === 'true') {
    mode = 'real'; for (const value of [base, api, mailpit]) assert.equal(new URL(value).hostname, '127.0.0.1');
    const session = await newContext();
    const context = session.context; let page = session.page;
    const signupTab = page;
    const suffix = Date.now().toString(36), email = `iyum-auth-ui-${suffix}@example.test`, nickname = `AU_${suffix}`;
    const password = 'LocalOnly' + crypto.randomUUID() + '7!'; let token;
    await check('실제 가입201/PENDING → Mailpit 인증 → ACTIVE', async () => {
      await consent(page); await fillSignup(page, email, password, nickname);
      const responsePromise = page.waitForResponse(api + '/api/v1/auth/signup'); await signupButton(page).click();
      assert.equal((await responsePromise).status(), 201); createdMembers.push({ email, nickname, status: 'PENDING' });
      await page.waitForURL('**/register/verify'); await page.getByRole('heading', { name: '회원가입 요청이 완료되었습니다' }).waitFor(); await capture(page, 'signup-pending');
      const mail = await waitFor(async () => { const response = await fetch(mailpit + '/api/v1/search?query=' + encodeURIComponent('to:' + email)); return (await response.json()).messages?.[0]; });
      const message = await (await fetch(mailpit + '/api/v1/message/' + mail.ID)).json();
      token = String(message.Text).match(/token=([A-Za-z0-9_-]{43})/)?.[1]; assert(Boolean(token), 'verification link available');
      page = await context.newPage();
      await page.goto(base + '/register/verify?token=' + token);
      assert.equal(await page.getByRole('heading', { name: '이메일 인증' }).count(), 1);
      assert.equal(new URL(signupTab.url()).pathname, '/register/verify'); await page.getByRole('button', { name: '이메일 인증하기', exact: true }).click();
      await page.waitForURL('**/login'); await page.getByText('이메일 인증이 완료되었습니다. 로그인해 주세요.', { exact: true }).waitFor(); createdMembers.at(-1).status = 'ACTIVE'; await capture(page, 'verification-success');
      assert.equal((await page.request.get(api + '/api/v1/auth/me')).status(), 401);
    });
    await check('실제 수동 로그인·새로고침 세션 복원·로그아웃 알림·초점 일시정지', async () => {
      await page.waitForURL('**/login');
      await page.locator('input[name=email]').fill(email); await page.locator('input[name=password]').fill(password);
      await page.getByRole('form').getByRole('button', { name: '로그인', exact: true }).click(); await page.waitForURL(base + '/');
      const me = await page.request.get(api + '/api/v1/auth/me'); assert.equal(me.status(), 200); createdMembers.at(-1).memberId = (await me.json()).memberId;
      await page.reload(); await page.getByText(nickname + '님', { exact: true }).waitFor();
      await page.getByRole('button', { name: '로그아웃', exact: true }).click(); await page.getByText('로그아웃되었습니다.', { exact: true }).waitFor();
      const notice = page.locator('.iyum-auth-notice'); assert.equal(await notice.getAttribute('role'), 'status');
      assert.equal(await notice.evaluate(el => el.contains(document.activeElement)), false);
      await notice.getByRole('button', { name: '알림 닫기' }).focus(); await delay(5100); assert(await notice.isVisible());
      for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
        if (width !== 320) await page.screenshot({ path: path.join(output, `real-logout-notice-${width}.png`), fullPage: true });
      }
      await page.keyboard.press('Enter'); await notice.waitFor({ state: 'hidden' });
      assert.equal((await page.request.get(api + '/api/v1/auth/me')).status(), 401); await page.reload();
      await page.getByLabel('회원 메뉴').getByRole('link', { name: '로그인', exact: true }).waitFor();
    });
    await context.close();
  }
  await check('브라우저 런타임 오류 없음', async () => assert.deepEqual(errors, []));
} catch (error) {
  // 원본 오류에 실제 인증 URL/입력값이 포함될 수 있어 검사명과 오류 유형만 기록한다.
  console.error('FAILED', mode, current, error.name); if (mode === 'mock') console.error(error.message, error.stack?.split('\n').slice(1, 3).join('\n')); process.exitCode = 1;
} finally {
  await browser.close();
  await fs.writeFile(path.join(output, 'auth-ui-results.json'), JSON.stringify({ checks, createdMembers, errors, layout, completed: !process.exitCode }, null, 2));
}
