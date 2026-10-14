// 기본 실행: 모의 API 장애·경합 UI 검증. 실제 계정 생성은 아래 opt-in만 허용한다.
// IYUM_RUN_LOCAL_LOGIN_TEST=true: 기존 가입 및 Mailpit 인증으로 ACTIVE/PENDING 각 1명 생성.
// 비밀번호·쿠키·토큰·메일·전체 인증 URL은 로그/스크린샷/trace에 기록하지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const mailpit = process.env.MAILPIT_BASE_URL ?? 'http://127.0.0.1:8025';
const artifacts = process.env.LOGIN_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-login-'));
await fs.mkdir(artifacts, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });
const results = [], createdMembers = [];
const member = { memberId: 123, email: 'test@example.test', nickname: '세션테스트', role: 'USER' };
const errorBody = (code, fieldErrors = []) => ({ code, message: 'private server detail', fieldErrors });
const anonymous = { status: 401, json: errorBody('AUTHENTICATION_REQUIRED') };
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const main = page => page.getByRole('main');
const menu = page => page.getByLabel('회원 메뉴');
const submit = page => main(page).getByRole('button', { name: '로그인', exact: true });
const signOut = page => menu(page).getByRole('button', { name: '로그아웃', exact: true });
let currentCheck;
async function check(name, run) {
  if (process.env.IYUM_LOGIN_CHECK_FILTER && !name.includes(process.env.IYUM_LOGIN_CHECK_FILTER)) return;
  currentCheck = name; await run(); results.push(name); console.log('PASS', name);
}
async function waitFor(predicate) {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) { const value = await predicate(); if (value) return value; await delay(100); }
  throw new Error('Condition not reached');
}
async function newPage() {
  const context = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  return { context, page };
}
async function fillLogin(page, email = member.email, password = 'x') {
  await main(page).locator('input[name=email]').fill(email);
  await main(page).locator('input[name=password]').fill(password);
}
async function mock(page, options = {}) {
  const calls = { csrf: 0, login: 0, logout: 0, me: 0 };
  let authenticated = !!options.authenticated;
  await page.route(api + '/api/v1/auth/**', async route => {
    const endpoint = new URL(route.request().url()).pathname.split('/').at(-1);
    if (!(endpoint in calls)) return route.abort();
    calls[endpoint]++;
    if (options[endpoint]) return options[endpoint](route, calls, value => { authenticated = value; });
    if (endpoint === 'csrf') return route.fulfill({ status: 200, json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf-' + calls.csrf } });
    if (endpoint === 'me') return route.fulfill(authenticated ? { status: 200, json: member } : anonymous);
    if (endpoint === 'login') { authenticated = true; return route.fulfill({ status: 200, json: member }); }
    authenticated = false;
    return route.fulfill({ status: 204 });
  });
  return calls;
}
function dbSummary(email) {
  assert.match(email, /^iyum-login-(?:active|pending)-[a-z0-9]+@example\.test$/);
  const sql = `select m.status,count(t.id),count(t.used_at) from members m left join email_verification_tokens t on t.member_id=m.id where m.email='${email}' group by m.status`;
  const output = execFileSync('docker', ['exec', process.env.IYUM_DB_CONTAINER ?? 'iyum-local-postgres-1', 'sh', '-c',
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -A -t -c "$1"', 'sh', sql], { encoding: 'utf8' }).trim();
  const [status, tokens, used] = output.split('|');
  return { status, tokens: Number(tokens), used: Number(used) };
}
async function createMember(kind) {
  const { context, page } = await newPage();
  const suffix = Date.now().toString(36);
  const email = `iyum-login-${kind}-${suffix}@example.test`;
  const nickname = `LG_${kind[0]}_${suffix}`;
  const password = 'LocalOnly' + crypto.randomUUID() + '7!';
  await page.goto(base + '/register');
  await page.getByRole('checkbox', { name: /이용약관/ }).check();
  await page.getByRole('checkbox', { name: /개인정보 수집/ }).check();
  await page.getByRole('button', { name: '다음', exact: true }).click();
  await page.waitForURL('**/register/info');
  await page.locator('input[autocomplete=email]').fill(email);
  await page.locator('input[autocomplete=new-password]').nth(0).fill(password);
  await page.locator('input[autocomplete=new-password]').nth(1).fill(password);
  await page.locator('input[autocomplete=nickname]').fill(nickname);
  const signupResponse = page.waitForResponse(api + '/api/v1/auth/signup');
  await page.getByRole('button', { name: '가입하기', exact: true }).click();
  assert.equal((await signupResponse).status(), 201);
  await page.waitForURL('**/register/verify');
  createdMembers.push({ email, nickname, status: 'PENDING' });
  console.log('Created local test member:', email);
  const messages = await waitFor(async () => {
    const response = await fetch(mailpit + '/api/v1/search?query=' + encodeURIComponent('to:' + email));
    assert.equal(response.status, 200);
    const messages = (await response.json()).messages ?? [];
    return messages.length ? messages : null;
  });
  if (kind === 'active') {
    const response = await fetch(mailpit + '/api/v1/message/' + messages[0].ID);
    assert.equal(response.status, 200);
    const token = String((await response.json()).Text).match(/token=([A-Za-z0-9_-]{43})/)?.[1];
    assert.ok(!!token);
    await page.goto(base + '/register/verify?token=' + token);
    await page.getByRole('button', { name: '이메일 인증하기', exact: true }).click();
    await page.waitForURL('**/login'); await page.getByText('이메일 인증이 완료되었습니다. 로그인해 주세요.', { exact: true }).waitFor();
  }
  Object.assign(createdMembers.at(-1), dbSummary(email), { mails: messages.length });
  return { context, page, email, nickname, password };
}
try {
  await check('확인 중 공개 콘텐츠 표시, /me 장애와 정상 401 구분 및 수동 복구', async () => {
    const { context, page } = await newPage();
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    let recovered = false;
    await mock(page, { me: async route => {
      await gate;
      return route.fulfill(recovered ? anonymous : { status: 500, body: 'unavailable' });
    } });
    await page.goto(base + '/');
    await menu(page).getByText('로그인 확인 중…').waitFor();
    assert.equal(await menu(page).getByRole('link').count(), 0);
    await main(page).getByText('지금 나에게 필요한', { exact: false }).waitFor();
    release();
    await menu(page).getByText('로그인 상태를 확인하지 못했습니다.').waitFor();
    assert.equal(await menu(page).getByRole('link').count(), 0);
    recovered = true;
    await menu(page).getByRole('button', { name: '상태 다시 확인' }).click();
    await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
    await context.close();
  });
  await check('필수값·UTF-8 길이 검증, 오류 초점, 로그인 유지·모의 계정 제거', async () => {
    const { context, page } = await newPage();
    const calls = await mock(page);
    await page.goto(base + '/login?state=success');
    await submit(page).click();
    assert.equal(await main(page).locator('[name=email]').getAttribute('aria-invalid'), 'true');
    assert.equal(await main(page).locator('[name=email]').evaluate(el => document.activeElement === el), true);
    await fillLogin(page, member.email, '가'.repeat(25));
    await submit(page).click();
    assert.equal(await main(page).locator('[name=password]').getAttribute('aria-invalid'), 'true');
    assert.equal(await main(page).locator('[name=password]').evaluate(el => document.activeElement === el), true);
    assert.equal(calls.login + calls.csrf, 0);
    assert.equal(await main(page).getByRole('checkbox').count(), 0);
    await main(page).locator('[name=password]').fill('');
    await page.screenshot({ path: path.join(artifacts, 'login-validation.png'), fullPage: true });
    await context.close();
  });
  await check('연속 제출은 POST 1회, 새 CSRF 실패 후 회원·비밀번호 정리, 수동 GET 복구와 복귀 경로', async () => {
    const { context, page } = await newPage();
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const calls = await mock(page, {
      login: async (route, _calls, setAuth) => { await gate; setAuth(true); return route.fulfill({ status: 200, json: member }); },
      csrf: (route, calls) => route.fulfill(calls.csrf === 2 ? { status: 500, body: 'unavailable' }
        : { status: 200, json: { headerName: 'X-CSRF-TOKEN', token: 'mock-csrf-' + calls.csrf } }),
    });
    await page.goto(base + '/login?from=' + encodeURIComponent('/community?type=test#posts'));
    await fillLogin(page);
    await page.getByRole('form', { name: '로그인 양식' }).evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
    await waitFor(() => calls.login === 1);
    assert.equal(await main(page).locator('[name=password]').isDisabled(), true);
    release();
    await main(page).getByText(/로그인은 완료되었습니다/).waitFor();
    await signOut(page).waitFor();
    assert.equal(await main(page).locator('[name=password]').count(), 0);
    assert.equal(calls.login, 1);
    assert.equal(calls.csrf, 2);
    const storage = await page.evaluate(() => [Object.keys(localStorage), Object.keys(sessionStorage)]);
    assert.deepEqual(storage, [[], []]);
    await page.screenshot({ path: path.join(artifacts, 'login-csrf-recovery.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await page.screenshot({ path: path.join(artifacts, 'login-csrf-recovery-mobile.png'), fullPage: true });
    await main(page).getByRole('button', { name: '보안 정보 다시 확인' }).click();
    await page.waitForURL(base + '/community?type=test#posts');
    assert.equal(calls.login, 1);
    assert.equal(calls.csrf, 3);
    await context.close();
  });
  for (const outcome of ['anonymous', 'member', 'failure']) {
    await check('늦은 초기 /me (' + outcome + ')가 로그인·로그아웃 결과를 덮어쓰지 않음', async () => {
      const { context, page } = await newPage();
      let release;
      const gate = new Promise(resolve => { release = resolve; });
      const calls = await mock(page, { me: async route => { await gate; return route.fulfill(outcome === 'anonymous' ? anonymous
        : outcome === 'member' ? { status: 200, json: member } : { status: 500, body: 'unavailable' }); } });
      await page.goto(base + '/login?from=' + encodeURIComponent('/login?from=//example.com'));
      await menu(page).getByText('로그인 확인 중…').waitFor();
      await fillLogin(page);
      await submit(page).click();
      await page.waitForURL(base + '/');
      await signOut(page).waitFor();
      if (outcome === 'member') {
        await signOut(page).click();
        await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
      }
      release();
      await delay(350);
      if (outcome === 'member') await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
      else await signOut(page).waitFor();
      assert.equal(calls.login, 1);
      await context.close();
    });
  }
  for (const [label, response, expected, csrfCount] of [
    ['잘못된 비밀번호', { status: 401, json: errorBody('LOGIN_FAILED') }, /이메일 또는 비밀번호/, 1],
    ['인증 대기', { status: 403, json: errorBody('EMAIL_VERIFICATION_REQUIRED') }, /이메일 인증/, 1],
    ['로그인 불가', { status: 403, json: errorBody('ACCOUNT_UNAVAILABLE') }, /현재 이 계정/, 1],
    ['CSRF 오류', { status: 403, json: errorBody('CSRF_TOKEN_INVALID') }, /보안 확인 정보가 유효하지/, 2],
    ['입력 오류', { status: 400, json: errorBody('VALIDATION_FAILED', [{ field: 'password', message: 'private validation' }]) }, /입력값/, 1],
    ['HTML 200', { status: 200, contentType: 'text/html', body: '<html>private server detail</html>' }, /처리 결과를 알 수/, 1],
    ['손상 JSON', { status: 200, contentType: 'application/json', body: '{broken' }, /처리 결과를 알 수/, 1],
    ['불완전 회원', { status: 200, json: { email: member.email } }, /처리 결과를 알 수/, 1],
    ['서버 장애', { status: 500, body: 'private server detail' }, /서버 오류/, 1],
    ['네트워크 장애', null, /이미 처리되었을 수/, 1],
  ]) {
    await check(label + ' 안전한 안내, 자동 POST 재시도 없음', async () => {
      const { context, page } = await newPage();
      const calls = await mock(page, { login: route => response ? route.fulfill(response) : route.abort('failed') });
      await page.goto(base + '/login');
      await fillLogin(page);
      await submit(page).click();
      await main(page).getByRole('alert').first().waitFor();
      assert.match(await main(page).getByRole('alert').first().innerText(), expected);
      assert.doesNotMatch(await main(page).innerText(), /private server|private validation/);
      await waitFor(() => calls.csrf === csrfCount);
      assert.equal(calls.login, 1);
      await waitFor(() => submit(page).isEnabled());
      if (label === '입력 오류') {
        assert.equal(await main(page).locator('[name=password]').getAttribute('aria-invalid'), 'true');
        assert.equal(await main(page).locator('[name=password]').evaluate(el => document.activeElement === el), true);
      }
      if (label === '인증 대기') {
        await main(page).getByRole('link', { name: '인증 메일 다시 요청하기' }).click();
        await page.waitForURL('**/register/resend');
      }
      await delay(150);
      assert.equal(calls.login, 1);
      await context.close();
    });
  }
  await check('로그인 응답 유실 시 /me로 회원 확인, 로그인 POST 자동 반복 없음', async () => {
    const { context, page } = await newPage();
    const calls = await mock(page, { login: (route, _calls, setAuth) => { setAuth(true); return route.abort('failed'); } });
    await page.goto(base + '/login');
    await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
    await fillLogin(page);
    await submit(page).click();
    await main(page).getByText(/로그인 응답을 확인하지 못했지만/).waitFor();
    await signOut(page).waitFor();
    assert.equal(calls.login, 1);
    assert.equal(await main(page).locator('[name=password]').count(), 0);
    await context.close();
  });
  await check('로그아웃 CSRF 오류는 로그인 유지, GET 갱신 후 수동 재시도만 204 처리', async () => {
    const { context, page } = await newPage();
    const calls = await mock(page, { authenticated: true, logout: (route, calls, setAuth) => {
      if (calls.logout === 1) return route.fulfill({ status: 403, json: errorBody('CSRF_TOKEN_INVALID') });
      setAuth(false); return route.fulfill({ status: 204 });
    } });
    await page.goto(base + '/');
    await signOut(page).click();
    await page.getByRole('banner').getByRole('alert').waitFor();
    await waitFor(() => calls.csrf === 2);
    assert.equal(calls.logout, 1);
    await signOut(page).click();
    await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
    assert.equal(calls.logout, 2);
    assert.equal(calls.csrf, 3);
    await context.close();
  });
  for (const outcome of ['authenticated', 'anonymous', 'unknown']) {
    await check('로그아웃 응답 유실 → /me ' + outcome + '에 맞는 상태와 안내', async () => {
      const { context, page } = await newPage();
      const calls = await mock(page, { authenticated: true,
        logout: route => route.abort('failed'),
        me: (route, calls) => route.fulfill(calls.logout === 0 || outcome === 'authenticated' ? { status: 200, json: member }
          : outcome === 'anonymous' ? anonymous : { status: 500, body: 'unavailable' }),
      });
      await page.goto(base + '/');
      await signOut(page).click();
      if (outcome === 'authenticated') { await page.getByRole('banner').getByRole('alert').waitFor(); await signOut(page).waitFor(); }
      else if (outcome === 'anonymous') {
        await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
        await page.getByRole('banner').getByText(/현재 비로그인 상태임을 확인/).waitFor();
      } else { await menu(page).getByText('로그인 상태를 확인하지 못했습니다.').waitFor(); assert.equal(await menu(page).getByRole('link').count(), 0); }
      assert.equal(calls.logout, 1);
      await context.close();
    });
  }
  await check('작성 중 로그아웃 취소는 POST 없음, 실패 후에도 이탈 경고 유지, 중복 로그아웃 차단', async () => {
    const { context, page } = await newPage();
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const calls = await mock(page, { authenticated: true, logout: async (route, calls, setAuth) => {
      if (calls.logout === 1) return route.fulfill({ status: 500, body: 'unavailable' });
      await gate; setAuth(false); return route.fulfill({ status: 204 });
    } });
    await page.goto(base + '/community/posts/create');
    await signOut(page).waitFor();
    await page.getByPlaceholder('제목을 입력하세요.').fill('저장하지 않은 글');
    await signOut(page).click();
    await page.getByRole('dialog').waitFor();
    assert.equal(calls.logout, 0);
    await page.getByRole('button', { name: '계속 작성', exact: true }).click();
    assert.equal(calls.logout, 0);
    await signOut(page).click();
    await page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true }).click();
    await page.getByRole('banner').getByRole('alert').waitFor();
    assert.equal(await page.getByPlaceholder('제목을 입력하세요.').inputValue(), '저장하지 않은 글');
    await page.getByRole('banner').getByRole('link', { name: 'IYUM 홈으로 이동', exact: true }).click();
    await page.getByRole('dialog').waitFor();
    await page.getByRole('button', { name: '계속 작성', exact: true }).click();
    await signOut(page).click();
    await page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true }).click();
    await waitFor(() => calls.logout === 2);
    const button = menu(page).getByRole('button', { name: '로그아웃 중…', exact: true });
    assert.equal(await button.isDisabled(), true);
    await button.evaluate(el => { el.click(); el.click(); });
    assert.equal(calls.logout, 2);
    release();
    await page.waitForURL(base + '/');
    await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
    await context.close();
  });
  await check('로그아웃 대기 중 다른 화면으로 이동한 뒤 실패해도 이전 글의 이탈 방지를 되살리지 않음', async () => {
    const { context, page } = await newPage();
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const calls = await mock(page, { authenticated: true, logout: async route => {
      await gate; return route.fulfill({ status: 500, body: 'unavailable' });
    } });
    await page.goto(base + '/community/posts/create');
    await signOut(page).waitFor();
    await page.getByPlaceholder('제목을 입력하세요.').fill('이탈을 허용한 글');
    await signOut(page).click();
    await page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true }).click();
    await waitFor(() => calls.logout === 1);
    await page.getByRole('banner').getByRole('link', { name: 'IYUM 홈으로 이동', exact: true }).click();
    await page.waitForURL(base + '/');
    release();
    await page.getByRole('banner').getByRole('alert').waitFor();
    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '커뮤니티', exact: true }).click();
    await page.waitForURL(base + '/community');
    assert.equal(await page.getByRole('dialog').count(), 0);
    await context.close();
  });
  // 지연 응답과 알림 시간은 실제 API 장애·계정 생성 없이 재현한다.
  const titleInput = page => page.getByPlaceholder('제목을 입력하세요.');
  const bodyInput = page => page.getByPlaceholder('내용을 입력하세요.');
  const closeNotice = page => page.getByRole('banner').getByRole('button', { name: '알림 닫기', exact: true });
  const logoutNotice = page => page.getByRole('banner').getByRole('status').filter({ has: page.getByRole('button', { name: '알림 닫기', exact: true }) });
  const anonymousMenu = page => menu(page).getByRole('link', { name: '로그인', exact: true });
  const leave = page => page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true });
  const keepWriting = page => page.getByRole('button', { name: '계속 작성', exact: true });
  async function clockPage(options = {}) {
    const result = await newPage();
    const calls = await mock(result.page, { authenticated: true, ...options });
    await result.page.clock.install({ time: new Date('2030-01-01T00:00:00Z') });
    await result.page.goto(base + '/');
    await signOut(result.page).waitFor();
    await result.page.clock.pauseAt(new Date('2030-01-02T00:00:00Z'));
    return { ...result, calls };
  }
  for (const confirmedBy of ['logout', 'me']) {
    await check('로그아웃 회귀: ' + confirmedBy + ' 지연 성공 중 다른 작성 화면의 입력·초점 보존', async () => {
      const { context, page } = await newPage();
      let release;
      const gate = new Promise(resolve => { release = resolve; });
      const calls = await mock(page, { authenticated: true, logout: async (route, _calls, setAuth) => {
        await gate; setAuth(false);
        return confirmedBy === 'logout' ? route.fulfill({ status: 204 }) : route.abort('failed');
      } });
      await page.goto(base + '/');
      await signOut(page).click();
      await waitFor(() => calls.logout === 1);
      await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '커뮤니티', exact: true }).click();
      await main(page).getByRole('link', { name: '글쓰기', exact: true }).click();
      await page.waitForURL(url => url.pathname === '/community/posts/create');
      await titleInput(page).fill('응답 대기 중 시작한 글');
      await bodyInput(page).fill('이 내용과 현재 초점이 유지되어야 합니다.');
      release();
      await anonymousMenu(page).waitFor();
      await logoutNotice(page).waitFor();
      assert.equal(new URL(page.url()).pathname, '/community/posts/create');
      assert.equal(await titleInput(page).inputValue(), '응답 대기 중 시작한 글');
      assert.equal(await bodyInput(page).inputValue(), '이 내용과 현재 초점이 유지되어야 합니다.');
      assert.equal(await bodyInput(page).evaluate(el => document.activeElement === el), true);
      assert.equal(await page.getByRole('dialog').count(), 0);
      assert.equal(calls.logout, 1);
      if (confirmedBy === 'logout') {
        await page.setViewportSize({ width: 390, height: 844 });
        await page.evaluate(() => window.scrollTo(0, 0));
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await page.screenshot({ path: path.join(artifacts, 'logout-notice-mobile.png'), clip: { x: 0, y: 0, width: 390, height: 250 } });
      }
      await closeNotice(page).click();
      assert.equal(new URL(page.url()).pathname, '/community/posts/create');
      await anonymousMenu(page).waitFor();
      await page.getByRole('banner').getByRole('link', { name: 'IYUM 홈으로 이동', exact: true }).click();
      await page.getByRole('dialog').waitFor();
      await keepWriting(page).click();
      await context.close();
    });
  }
  for (const change of ['추가 본문', '추가 본문 후 이동 허용', '주제 변경', '빈 폼의 첫 입력']) {
    await check('로그아웃 회귀: 같은 작성 화면의 ' + change + ' 후 이탈 확인과 비로그인 상태 유지', async () => {
      const { context, page } = await newPage();
      let release;
      const gate = new Promise(resolve => { release = resolve; });
      const calls = await mock(page, { authenticated: true, logout: async (route, _calls, setAuth) => {
        await gate; setAuth(false); return route.fulfill({ status: 204 });
      } });
      await page.goto(base + '/community/posts/create');
      await signOut(page).waitFor();
      if (change !== '빈 폼의 첫 입력') await titleInput(page).fill('동의 전 입력');
      await signOut(page).click();
      if (change !== '빈 폼의 첫 입력') await leave(page).click();
      await waitFor(() => calls.logout === 1);
      if (change === '주제 변경') await page.getByLabel('주제', { exact: true }).selectOption('HEARING_AID');
      else await bodyInput(page).fill('처음 확인한 뒤 새로 쓴 내용');
      release();
      await anonymousMenu(page).waitFor();
      await page.getByRole('dialog').waitFor();
      assert.equal(new URL(page.url()).pathname, '/community/posts/create');
      assert.equal(await keepWriting(page).evaluate(el => document.activeElement === el), true);
      if (change === '추가 본문 후 이동 허용') {
        await leave(page).click();
        await page.waitForURL(base + '/');
        await anonymousMenu(page).waitFor();
        assert.equal(calls.logout, 1);
        await context.close();
        return;
      }
      await keepWriting(page).click();
      if (change === '주제 변경') assert.equal(await page.getByLabel('주제', { exact: true }).inputValue(), 'HEARING_AID');
      else assert.equal(await bodyInput(page).inputValue(), '처음 확인한 뒤 새로 쓴 내용');
      await anonymousMenu(page).waitFor();
      assert.equal(calls.logout, 1);
      // 추가 확인 취소 뒤에도 작성 화면은 dirty이며, 정상적인 이탈 허용은 계속 가능하다.
      await page.getByRole('banner').getByRole('link', { name: 'IYUM 홈으로 이동', exact: true }).click();
      await page.getByRole('dialog').waitFor();
      await leave(page).click();
      await page.waitForURL(base + '/');
      await anonymousMenu(page).waitFor();
      assert.equal(calls.logout, 1);
      await context.close();
    });
  }
  await check('로그아웃 회귀: 깨끗한 폼에서 시작한 요청이 실패해도 대기 중 새 입력 보호', async () => {
    const { context, page } = await newPage();
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const calls = await mock(page, { authenticated: true, logout: async route => {
      await gate; return route.fulfill({ status: 500, body: 'unavailable' });
    } });
    await page.goto(base + '/community/posts/create');
    await signOut(page).click();
    await waitFor(() => calls.logout === 1);
    await bodyInput(page).fill('요청이 실패해도 남아야 하는 내용');
    release();
    await page.getByRole('banner').getByRole('alert').waitFor();
    await signOut(page).waitFor();
    await page.getByRole('banner').getByRole('link', { name: 'IYUM 홈으로 이동', exact: true }).click();
    await page.getByRole('dialog').waitFor();
    await keepWriting(page).click();
    assert.equal(await bodyInput(page).inputValue(), '요청이 실패해도 남아야 하는 내용');
    assert.equal(calls.logout, 1);
    await context.close();
  });
  for (const confirmedBy of ['logout', 'me']) {
    await check('로그아웃 회귀: ' + confirmedBy + ' 성공 알림은 5초 후 닫히고 비로그인 유지', async () => {
      const { context, page, calls } = await clockPage(confirmedBy === 'me' ? {
        logout: (route, _calls, setAuth) => { setAuth(false); return route.abort('failed'); },
      } : {});
      await signOut(page).click();
      await logoutNotice(page).waitFor();
      await anonymousMenu(page).waitFor();
      assert.equal(await closeNotice(page).evaluate(el => document.activeElement === el), false);
      await page.clock.runFor(4999);
      assert.equal(await logoutNotice(page).isVisible(), true);
      await page.clock.runFor(1);
      await closeNotice(page).waitFor({ state: 'detached' });
      await anonymousMenu(page).waitFor();
      assert.equal(new URL(page.url()).pathname, '/');
      assert.equal(calls.logout, 1);
      await context.close();
    });
  }
  for (const outcome of ['성공', '오류', '결과 미확인']) {
    await check('로그아웃 회귀: ' + outcome + ' 알림 수동 닫기는 인증·경로를 바꾸지 않음', async () => {
      const { context, page, calls } = await clockPage(outcome === '오류' ? {
        logout: route => route.fulfill({ status: 403, json: errorBody('CSRF_TOKEN_INVALID') }),
      } : outcome === '결과 미확인' ? {
        logout: route => route.abort('failed'),
        me: (route, calls) => route.fulfill(calls.logout ? { status: 500, body: 'unavailable' } : { status: 200, json: member }),
      } : {});
      await signOut(page).click();
      await closeNotice(page).waitFor();
      if (outcome !== '성공') {
        await page.clock.runFor(10000);
        await page.getByRole('banner').getByRole('alert').waitFor();
      }
      await closeNotice(page).focus();
      await page.keyboard.press('Enter');
      await closeNotice(page).waitFor({ state: 'detached' });
      await page.clock.runFor(6000);
      if (outcome === '성공') await anonymousMenu(page).waitFor();
      else if (outcome === '오류') await signOut(page).waitFor();
      else await menu(page).getByRole('button', { name: '상태 다시 확인' }).waitFor();
      assert.equal(new URL(page.url()).pathname, '/');
      assert.equal(calls.logout, 1);
      await context.close();
    });
  }
  await check('로그아웃 회귀: 닫기 버튼 키보드 초점 동안 타이머 일시 정지 후 남은 시간 재개', async () => {
    const { context, page } = await clockPage();
    await signOut(page).click();
    await logoutNotice(page).waitFor();
    await page.clock.runFor(2000);
    await closeNotice(page).focus();
    await page.clock.runFor(10000);
    assert.equal(await closeNotice(page).evaluate(el => document.activeElement === el), true);
    assert.equal(await logoutNotice(page).isVisible(), true);
    await page.keyboard.press('Tab');
    assert.equal(await closeNotice(page).evaluate(el => document.activeElement === el), false);
    await page.clock.runFor(2999);
    assert.equal(await logoutNotice(page).isVisible(), true);
    await page.clock.runFor(1);
    await closeNotice(page).waitFor({ state: 'detached' });
    await anonymousMenu(page).waitFor();
    await context.close();
  });
  for (const replacement of ['성공', '오류']) {
    await check('로그아웃 회귀: 이전 성공 타이머가 새 ' + replacement + ' 알림을 지우지 않음', async () => {
      const { context, page, calls } = await clockPage({ logout: (route, calls, setAuth) => {
        if (calls.logout === 2 && replacement === '오류') return route.fulfill({ status: 403, json: errorBody('CSRF_TOKEN_INVALID') });
        setAuth(false); return route.fulfill({ status: 204 });
      } });
      await signOut(page).click();
      await logoutNotice(page).waitFor();
      await page.clock.runFor(2000);
      await anonymousMenu(page).click();
      await fillLogin(page);
      await submit(page).click();
      await page.waitForURL(base + '/');
      await signOut(page).click();
      await closeNotice(page).waitFor();
      await page.clock.runFor(3000); // 이전 알림의 만료 시각
      assert.equal(await closeNotice(page).isVisible(), true);
      if (replacement === '성공') {
        await page.clock.runFor(1999);
        assert.equal(await logoutNotice(page).isVisible(), true);
        await page.clock.runFor(1);
        await closeNotice(page).waitFor({ state: 'detached' });
        await anonymousMenu(page).waitFor();
      } else {
        await page.clock.runFor(10000);
        await page.getByRole('banner').getByRole('alert').waitFor();
        await signOut(page).waitFor();
      }
      assert.equal(calls.logout, 2);
      await context.close();
    });
  }
  if (process.env.IYUM_RUN_LOCAL_LOGIN_TEST === 'true') {
    await check('실제 ACTIVE 가입·Mailpit 인증·잘못된 암호·연속 제출·세션 교체·새로고침·로그아웃·재로그인', async () => {
      const { context, page, email, nickname, password } = await createMember('active');
      await page.goto(base + '/login?from=' + encodeURIComponent('/community?type=test#posts'));
      await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
      await fillLogin(page, email, password + 'wrong');
      let responsePromise = page.waitForResponse(api + '/api/v1/auth/login');
      await submit(page).click();
      assert.equal((await responsePromise).status(), 401);
      await main(page).getByRole('alert').first().getByText(/이메일 또는 비밀번호/).waitFor();
      await main(page).locator('[name=password]').fill(password);
      const csrfBefore = await page.evaluate(async endpoint => {
        const response = await fetch(endpoint, { credentials: 'include', cache: 'no-store' });
        return response.json();
      }, api + '/api/v1/auth/csrf');
      const previousSession = (await context.cookies(api)).find(cookie => cookie.name === 'JSESSIONID')?.value;
      assert.ok(!!previousSession);
      let posts = 0;
      page.on('request', request => { if (request.method() === 'POST' && request.url() === api + '/api/v1/auth/login') posts++; });
      responsePromise = page.waitForResponse(api + '/api/v1/auth/login');
      await page.getByRole('form', { name: '로그인 양식' }).evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
      const response = await responsePromise;
      assert.equal(response.status(), 200);
      assert.equal(response.headers()['cache-control'], 'no-store');
      assert.equal(response.headers()['access-control-allow-origin'], base);
      assert.equal(response.headers()['access-control-allow-credentials'], 'true');
      const headers = await response.request().allHeaders();
      assert.ok(!!headers['x-csrf-token']);
      assert.ok(/JSESSIONID=/.test(headers.cookie ?? ''));
      await page.waitForURL(base + '/community?type=test#posts');
      await menu(page).getByText(nickname + '님', { exact: true }).waitFor();
      assert.equal(posts, 1);
      const session = (await context.cookies(api)).find(cookie => cookie.name === 'JSESSIONID')?.value;
      assert.ok(!!session && session !== previousSession);
      await page.reload();
      await menu(page).getByText(nickname + '님', { exact: true }).waitFor();
      assert.equal(await page.evaluate(() => localStorage.length + sessionStorage.length), 0);
      const rejectedOldCsrf = await page.evaluate(async ({ endpoint, csrf }) => {
        const response = await fetch(endpoint, { method: 'POST', credentials: 'include', cache: 'no-store', headers: { [csrf.headerName]: csrf.token } });
        return { status: response.status, code: (await response.json()).code };
      }, { endpoint: api + '/api/v1/auth/logout', csrf: csrfBefore });
      assert.deepEqual(rejectedOldCsrf, { status: 403, code: 'CSRF_TOKEN_INVALID' });
      await page.screenshot({ path: path.join(artifacts, 'actual-authenticated.png'), fullPage: true });
      responsePromise = page.waitForResponse(api + '/api/v1/auth/logout');
      await signOut(page).click();
      const logoutResponse = await responsePromise;
      assert.equal(logoutResponse.status(), 204);
      // Chromium은 204 응답의 body 조회를 제공하지 않을 수 있다. 본문을 읽지 않는다.
      assert.ok(!logoutResponse.headers()['content-length'] || logoutResponse.headers()['content-length'] === '0');
      await page.waitForURL(base + '/');
      await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
      await page.reload();
      await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
      await menu(page).getByRole('link', { name: '로그인', exact: true }).click();
      await fillLogin(page, email, password);
      await submit(page).click();
      await page.waitForURL(base + '/');
      await menu(page).getByText(nickname + '님', { exact: true }).waitFor();
      await signOut(page).click();
      await menu(page).getByRole('link', { name: '로그인', exact: true }).waitFor();
      await context.close();
    });
    await check('실제 PENDING 가입 → 로그인 403 → 인증 메일 재전송 화면', async () => {
      const { context, page, email, password } = await createMember('pending');
      await page.goto(base + '/login');
      await fillLogin(page, email, password);
      const responsePromise = page.waitForResponse(api + '/api/v1/auth/login');
      await submit(page).click();
      const response = await responsePromise;
      assert.equal(response.status(), 403);
      assert.equal((await response.json()).code, 'EMAIL_VERIFICATION_REQUIRED');
      await main(page).getByRole('link', { name: '인증 메일 다시 요청하기' }).click();
      await page.waitForURL('**/register/resend');
      await main(page).getByRole('heading', { name: '인증 메일 재전송' }).waitFor();
      await context.close();
    });
  }
  await fs.writeFile(path.join(artifacts, 'result.json'), JSON.stringify({ checks: results, createdMembers }, null, 2));
  console.log('Browser checks passed:', results.length, 'Artifacts:', artifacts);
} catch (error) {
  // Assertion/Playwright 메시지에 민감값이 포함될 수 있으므로 이름과 이 파일의 행 번호만 출력한다.
  console.error('Failed check:', currentCheck, 'Error kind:', error.name);
  console.error(String(error.stack).split('\n').filter(line => /at .*login-browser\.mjs:\d+/.test(line)).join('\n'));
  await fs.writeFile(path.join(artifacts, 'result.json'), JSON.stringify({ checks: results, failedCheck: currentCheck, createdMembers }, null, 2));
  process.exitCode = 1;
} finally { await browser.close(); }
