// 선택적 실제 브라우저 검증: 실행 중인 개발 API/DB에 고유한 테스트 회원 2명을 만듭니다.
// 별도 Playwright 설치가 이미 있는 경우 PLAYWRIGHT_MODULE로 그 모듈 경로를 지정할 수 있습니다.
// 패키지 의존성을 추가하지 않으며 기존 개발 데이터는 삭제하지 않습니다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const artifactDir = process.env.SIGNUP_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-signup-'));
await fs.mkdir(artifactDir, { recursive: true });
const suffix = Date.now().toString(36);
const member = { email: `iyum-integration-${suffix}@example.com`, nickname: `IT_${suffix}` };
const csrfMember = { email: `iyum-csrf-${suffix}@example.com`, nickname: `CS_${suffix}` };
const fakePassword = ' Example123! ';
const results = [];
const createdMembers = [];
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });

async function check(name, run) {
  await run();
  results.push(name);
  console.log('PASS', name);
}
async function begin() {
  const context = await browser.newContext();
  const page = await context.newPage();
  const posts = [];
  const responses = [];
  page.on('request', request => {
    if (request.url() === api + '/api/v1/auth/signup' && request.method() === 'POST') posts.push(request);
  });
  page.on('response', response => {
    if (response.url().startsWith(api + '/api/v1/auth/')) responses.push(response);
  });
  await page.goto(base + '/register');
  await page.getByRole('checkbox', { name: /이용약관/ }).check();
  await page.getByRole('checkbox', { name: /개인정보 수집/ }).check();
  await page.getByRole('button', { name: '다음', exact: true }).click();
  await page.waitForURL('**/register/info');
  return { context, page, posts, responses };
}
async function fill(page, values = member) {
  await page.locator('input[autocomplete="email"]').fill(values.email);
  await page.locator('input[autocomplete="new-password"]').nth(0).fill(fakePassword);
  await page.locator('input[autocomplete="new-password"]').nth(1).fill(fakePassword);
  await page.locator('input[autocomplete="nickname"]').fill(values.nickname);
}
async function submit(page) {
  const response = page.waitForResponse(response => response.url() === api + '/api/v1/auth/signup' && response.request().method() === 'POST');
  await page.getByRole('button', { name: '가입하기', exact: true }).click();
  return response;
}
async function fieldError(page, field, text) {
  const input = page.locator(`input[autocomplete="${field}"]`);
  await page.getByText(text, { exact: true }).waitFor();
  assert.equal(await input.getAttribute('aria-invalid'), 'true');
  const id = await input.getAttribute('aria-describedby');
  assert.ok(id);
  assert.equal(await page.locator(`[id="${id}"]`).textContent(), text);
  assert.equal(await page.getByRole('button', { name: '가입하기', exact: true }).isEnabled(), true);
}

try {
  await check('direct completion URL does not claim an unconfirmed signup', async () => {
    const page = await browser.newPage();
    await page.goto(base + '/register/verify');
    await page.getByText('현재 이 화면에서 확인할 수 있는 가입 결과가 없습니다.', { exact: false }).waitFor();
    assert.equal(await page.getByRole('heading', { name: '회원가입 요청이 완료되었습니다' }).count(), 0);
    await page.close();
  });
  await check('required consent and invalid input prevent requests', async () => {
    const page = await browser.newPage();
    let requests = 0;
    page.on('request', request => { if (request.url().startsWith(api + '/api/v1/auth/')) requests++; });
    await page.goto(base + '/register');
    await page.getByRole('button', { name: '다음', exact: true }).click();
    await page.getByRole('main').getByRole('alert').waitFor();
    assert.match(await page.getByRole('main').getByRole('alert').innerText(), /필수 약관/);
    assert.equal(requests, 0);
    await page.goto(base + '/register/info');
    await fill(page);
    await page.locator('input[autocomplete="new-password"]').nth(1).fill('Different456!');
    await page.getByRole('button', { name: '가입하기', exact: true }).click();
    await page.getByText('비밀번호가 일치하지 않습니다.', { exact: true }).waitFor();
    assert.equal(requests, 0);
    await page.close();
  });
  await check('real CSRF + signup returns 201/PENDING; double submit is blocked; no password persistence', async () => {
    const { page, context, posts, responses } = await begin();
    await fill(page);
    const responsePromise = page.waitForResponse(response => response.url() === api + '/api/v1/auth/signup');
    await page.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
    assert.equal(await page.getByRole('button', { name: '가입 중…', exact: true }).isDisabled(), true);
    const response = await responsePromise;
    assert.equal(response.status(), 201);
    assert.deepEqual(await response.json(), { status: 'PENDING' });
    createdMembers.push(member);
    await page.waitForURL('**/register/verify');
    await page.getByRole('heading', { name: '회원가입 요청이 완료되었습니다' }).waitFor();
    assert.equal(posts.length, 1);
    assert.equal(responses.filter(response => response.url().endsWith('/csrf')).length, 1);
    const headers = await posts[0].allHeaders();
    assert.ok(headers['x-csrf-token']);
    assert.match(headers.cookie, /JSESSIONID=/);
    assert.equal(response.headers()['access-control-allow-origin'], base);
    assert.equal(response.headers()['access-control-allow-credentials'], 'true');
    // 요청 본문은 검증만 하고 출력·파일 저장하지 않습니다.
    const body = posts[0].postDataJSON();
    assert.equal(body.password, fakePassword);
    assert.equal(body.passwordConfirm, fakePassword);
    assert.deepEqual(body.agreements.map(item => [item.termsCode, item.version, item.agreed]), [
      ['SERVICE_TERMS', 'dev-v1', true], ['PRIVACY_COLLECTION_USE', 'dev-v1', true],
    ]);
    const storage = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }));
    assert.ok(!storage.includes(fakePassword));
    assert.ok(!page.url().includes(fakePassword));
    assert.equal(await page.locator('input[type="password"]').count(), 0);
    await page.screenshot({ path: path.join(artifactDir, 'signup-pending.png'), fullPage: true });
    await context.close();
  });
  await check('real normalized email conflict is shown on email input', async () => {
    const { page, context } = await begin();
    await fill(page, { email: ` ${member.email.toUpperCase()} `, nickname: `Other_${suffix}` });
    assert.equal((await submit(page)).status(), 409);
    await fieldError(page, 'email', '이미 가입된 이메일입니다.');
    await page.screenshot({ path: path.join(artifactDir, 'signup-email-conflict.png'), fullPage: true });
    await context.close();
  });
  await check('real nickname conflict ignores case and is shown on nickname input', async () => {
    const { page, context } = await begin();
    await fill(page, { email: `other-${suffix}@example.com`, nickname: member.nickname.toUpperCase() });
    assert.equal((await submit(page)).status(), 409);
    await fieldError(page, 'nickname', '이미 사용 중인 닉네임입니다.');
    await context.close();
  });
  await check('real server validation fieldErrors connect to password confirmation', async () => {
    const { page, context } = await begin();
    await fill(page);
    await page.route('**/api/v1/auth/signup', route => {
      const payload = route.request().postDataJSON();
      payload.passwordConfirm = 'Different456!';
      return route.continue({ postData: JSON.stringify(payload) });
    });
    const response = await submit(page);
    assert.equal(response.status(), 400);
    assert.equal((await response.json()).code, 'VALIDATION_FAILED');
    const input = page.locator('input[autocomplete="new-password"]').nth(1);
    await page.getByText('비밀번호가 일치하지 않습니다.', { exact: true }).waitFor();
    assert.equal(await input.getAttribute('aria-invalid'), 'true');
    assert.ok(await input.getAttribute('aria-describedby'));
    await context.close();
  });
  for (const [scenario, code] of [['missing', 'REQUIRED_AGREEMENT_MISSING'], ['notAccepted', 'REQUIRED_AGREEMENT_NOT_ACCEPTED'], ['version', 'TERMS_VERSION_MISMATCH']]) {
    await check(`real terms policy ${scenario} is displayed in form alert`, async () => {
      const { page, context } = await begin();
      await fill(page);
      await page.route('**/api/v1/auth/signup', route => {
        const payload = route.request().postDataJSON();
        if (scenario === 'missing') payload.agreements.pop();
        if (scenario === 'notAccepted') payload.agreements[0].agreed = false;
        if (scenario === 'version') payload.agreements[0].version = 'dev-v0';
        return route.continue({ postData: JSON.stringify(payload) });
      });
      const response = await submit(page);
      assert.equal(response.status(), 400);
      const body = await response.json();
      assert.equal(body.code, code);
      await page.getByRole('main').getByRole('alert').filter({ hasText: body.message }).waitFor();
      assert.equal(await page.getByRole('button', { name: '가입하기', exact: true }).isEnabled(), true);
      await context.close();
    });
  }
  await check('real CSRF 403 refreshes token once; only manual resubmit sends the second POST', async () => {
    const { page, context, posts, responses } = await begin();
    await fill(page, csrfMember);
    let attempts = 0;
    await page.route('**/api/v1/auth/signup', route => {
      attempts++;
      return attempts === 1 ? route.continue({ headers: { ...route.request().headers(), 'x-csrf-token': 'invalid-token' } }) : route.continue();
    });
    assert.equal((await submit(page)).status(), 403);
    await page.getByRole('main').getByRole('alert').filter({ hasText: '보안 확인 정보를 갱신했습니다.' }).waitFor();
    assert.equal(posts.length, 1);
    assert.equal(responses.filter(response => response.url().endsWith('/csrf')).length, 2);
    assert.equal((await submit(page)).status(), 201);
    createdMembers.push(csrfMember);
    await page.waitForURL('**/register/verify');
    assert.equal(posts.length, 2);
    await context.close();
  });
  await check('aborted POST releases loading state without automatic retry', async () => {
    const { page, context, posts } = await begin();
    await fill(page);
    await page.route('**/api/v1/auth/signup', route => route.abort('connectionfailed'));
    await page.getByRole('button', { name: '가입하기', exact: true }).click();
    await page.getByRole('main').getByRole('alert').filter({ hasText: '서버와 통신하지 못해 가입 결과를 확인할 수 없습니다.' }).waitFor();
    assert.equal(await page.getByRole('button', { name: '가입하기', exact: true }).isEnabled(), true);
    assert.equal(posts.length, 1);
    await context.close();
  });
  await check('HTML 500 response displays safe error and enables manual retry', async () => {
    const { page, context, posts } = await begin();
    await fill(page);
    await page.route('**/api/v1/auth/signup', route => route.fulfill({ status: 500, contentType: 'text/html', body: '<html>internal SQL details</html>' }));
    assert.equal((await submit(page)).status(), 500);
    await page.getByRole('main').getByRole('alert').filter({ hasText: '서버 오류로 가입 결과를 확인하지 못했습니다.' }).waitFor();
    assert.ok(!(await page.getByRole('main').getByRole('alert').innerText()).includes('SQL'));
    assert.equal(await page.getByRole('button', { name: '가입하기', exact: true }).isEnabled(), true);
    assert.equal(posts.length, 1);
    await context.close();
  });
} finally {
  await browser.close();
  // 비밀번호·CSRF 토큰·쿠키·요청 본문은 검증 기록에 저장하지 않습니다.
  await fs.writeFile(path.join(artifactDir, 'results.json'), JSON.stringify({ results, createdMembers }, null, 2));
  console.log('Artifacts:', artifactDir);
  console.log('Created test members:', JSON.stringify(createdMembers));
}
