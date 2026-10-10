// 명시적으로 실행하는 브라우저 검증. 기본 실행에서는 API를 모의한다.
// IYUM_RUN_LOCAL_RESEND_TEST=true일 때만 새 개발 회원과 Mailpit 메일을 생성한다.
// 토큰·전체 인증 URL·메일 본문·비밀번호는 출력하거나 스크린샷에 저장하지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const mailpit = process.env.MAILPIT_BASE_URL ?? 'http://127.0.0.1:8025';
const artifacts = process.env.RESEND_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-resend-'));
await fs.mkdir(artifacts, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });
const results = [];
const accepted = '입력한 이메일이 인증 대기 상태이고 재전송 조건을 충족하면 인증 메일이 발송됩니다. 메일함과 스팸함을 확인해 주세요.';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const resendButton = page => page.getByRole('button', { name: '인증 메일 재전송 요청', exact: true });
let currentCheck;
async function check(name, run) { currentCheck = name; await run(); results.push(name); console.log('PASS', name); }
async function newPage() {
  const context = await browser.newContext();
  const page = await context.newPage();
  page.setDefaultTimeout(15_000);
  return { context, page };
}
async function mockCsrf(page, calls) {
  await page.route(api + '/api/v1/auth/csrf', route => {
    calls.push('GET');
    return route.fulfill({ status: 200, json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf-' + calls.length } });
  });
}
async function fillResend(page, email = 'test@example.com') {
  await page.goto(base + '/register/resend');
  await page.getByLabel('이메일', { exact: true }).fill(email);
}
async function waitFor(checkValue, description, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { const result = await checkValue(); if (result) return result; await delay(200); }
  throw new Error(description); // URL이나 응답 본문을 출력하지 않는다.
}
async function mailList(email) {
  const response = await fetch(mailpit + '/api/v1/search?query=' + encodeURIComponent('to:' + email));
  assert.equal(response.status, 200);
  return (await response.json()).messages ?? [];
}
async function verificationToken(message) {
  const response = await fetch(mailpit + '/api/v1/message/' + message.ID);
  assert.equal(response.status, 200);
  const body = await response.json();
  const token = String(body.Text).match(/token=([A-Za-z0-9_-]{43})/)?.[1];
  assert.ok(Boolean(token), 'Mailpit message contains a verification token');
  return token;
}
function dbSummary(email) {
  assert.match(email, /^iyum-resend-[a-z0-9]+@example\.test$/);
  const sql = `select m.status, count(t.id), count(t.used_at), count(t.revoked_at), min(t.created_at) from members m left join email_verification_tokens t on t.member_id=m.id where m.email='${email}' group by m.status`;
  const output = execFileSync('docker', ['exec', process.env.IYUM_DB_CONTAINER ?? 'iyum-local-postgres-1', 'sh', '-c',
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -A -t -c "$1"', 'sh', sql], { encoding: 'utf8' }).trim();
  const [status, tokens, used, revoked, firstIssued] = output.split('|');
  return { status, tokens: Number(tokens), used: Number(used), revoked: Number(revoked), firstIssued };
}
let local;
try {
  if (process.env.IYUM_RUN_LOCAL_RESEND_TEST === 'true') {
    await check('실제 브라우저 회원가입 → Mailpit 최초 메일 → 즉시 재전송 제한', async () => {
      const { context, page } = await newPage();
      const suffix = Date.now().toString(36);
      const email = `iyum-resend-${suffix}@example.test`;
      const nickname = `RS_${suffix}`;
      await page.goto(base + '/register');
      await page.getByRole('checkbox', { name: /이용약관/ }).check();
      await page.getByRole('checkbox', { name: /개인정보 수집/ }).check();
      await page.getByRole('button', { name: '다음', exact: true }).click();
      await page.waitForURL('**/register/info');
      await page.locator('input[autocomplete="email"]').fill(email);
      // 로컬 테스트 전용 값. 저장·로그 출력하지 않는다.
      const password = 'LocalOnly' + crypto.randomUUID() + '7!';
      await page.locator('input[autocomplete="new-password"]').nth(0).fill(password);
      await page.locator('input[autocomplete="new-password"]').nth(1).fill(password);
      await page.locator('input[autocomplete="nickname"]').fill(nickname);
      const responsePromise = page.waitForResponse(api + '/api/v1/auth/signup');
      await page.getByRole('button', { name: '가입하기', exact: true }).click();
      const response = await responsePromise;
      assert.equal(response.status(), 201);
      await page.waitForURL('**/register/verify');
      await page.getByRole('heading', { name: '회원가입 요청이 완료되었습니다' }).waitFor();
      const mail = await waitFor(async () => (await mailList(email))[0], 'First local mail did not arrive');
      console.log('Created local test member:', email);
      local = { context, page, email, nickname, original: await verificationToken(mail), originalMailId: mail.ID };
      await page.getByRole('link', { name: '인증 메일 다시 요청하기' }).click();
      await page.getByLabel('이메일', { exact: true }).fill(email);
      const post = page.waitForResponse(api + '/api/v1/auth/email/resend');
      await resendButton(page).click();
      assert.equal((await post).status(), 202);
      await page.getByText(accepted, { exact: true }).waitFor();
      assert.equal(dbSummary(email).tokens, 1);
      assert.equal((await mailList(email)).length, 1);
    });
  }

  await check('직접 진입·새로고침·새 탭에서는 POST가 없고 입력 오류에 초점과 aria-invalid를 제공', async () => {
    const { context, page } = await newPage();
    let posts = 0;
    context.on('request', request => { if (request.method() === 'POST' && request.url().startsWith(api)) posts++; });
    await page.goto(base + '/register/resend');
    await page.reload();
    const second = await context.newPage();
    await second.goto(base + '/register/resend');
    await resendButton(page).click();
    await page.getByRole('main').getByRole('alert').waitFor();
    const input = page.getByLabel('이메일', { exact: true });
    assert.equal(await input.getAttribute('aria-invalid'), 'true');
    assert.equal(await input.evaluate(element => document.activeElement === element), true);
    assert.equal(posts, 0);
    await context.close();
  });

  await check('연속 submit 차단, 접수 안내 및 초점, 수동 재시도 때 새 CSRF 조회', async () => {
    const { context, page } = await newPage();
    const calls = [];
    await mockCsrf(page, calls);
    let finish;
    const gate = new Promise(resolve => { finish = resolve; });
    await page.route(api + '/api/v1/auth/email/resend', async route => {
      calls.push('POST');
      assert.deepEqual(route.request().postDataJSON(), { email: 'test@example.com' });
      assert.equal(route.request().headers()['x-csrf-token'], 'test-csrf-' + (calls.length - 1));
      await gate;
      await route.fulfill({ status: 202, json: { status: 'ACCEPTED' } });
    });
    await fillResend(page);
    await page.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
    await page.getByRole('button', { name: '요청 중…', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '요청 중…', exact: true }).isDisabled(), true);
    await waitFor(() => calls.length === 2, 'POST not observed');
    assert.deepEqual(calls, ['GET', 'POST']);
    finish();
    await page.getByText(accepted, { exact: true }).waitFor();
    assert.equal(await page.locator('#resend-feedback').evaluate(element => document.activeElement === element), true);
    assert.equal(await page.locator('#resend-feedback').getAttribute('role'), 'status');
    await page.screenshot({ path: path.join(artifacts, 'resend-accepted.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true);
    await page.getByRole('main').screenshot({ path: path.join(artifacts, 'resend-accepted-mobile.png') });
    await resendButton(page).click();
    await page.getByText(accepted, { exact: true }).waitFor();
    assert.deepEqual(calls, ['GET', 'POST', 'GET', 'POST']);
    const stored = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }));
    assert.equal(stored.includes('test@example.com'), false);
    assert.equal(page.url(), base + '/register/resend');
    await context.close();
  });

  for (const [name, status, body, contentType, expected] of [
    ['CSRF 오류', 403, '{"code":"CSRF_TOKEN_INVALID","message":"private detail"}', 'application/json', /보안 확인 정보가 유효하지/],
    ['입력 오류', 400, '{"code":"VALIDATION_FAILED","message":"private detail"}', 'application/json', /이메일 입력값/],
    ['서버 오류', 500, '<html>private detail</html>', 'text/html', /서버 오류/],
    ['HTML 202', 202, '<html>private detail</html>', 'text/html', /접수 여부를 알 수/],
    ['깨진 JSON', 202, '{broken', 'application/json', /접수 여부를 알 수/],
    ['잘못된 200', 200, '{"status":"ACCEPTED"}', 'application/json', /접수 여부를 알 수/],
    ['잘못된 상태값', 202, '{"status":"PENDING"}', 'application/json', /접수 여부를 알 수/],
  ]) {
    await check(name + ' 안내, 임의 본문 비노출, 자동 재시도 없음', async () => {
      const { context, page } = await newPage();
      const calls = [];
      await mockCsrf(page, calls);
      await page.route(api + '/api/v1/auth/email/resend', route => {
        calls.push('POST');
        return route.fulfill({ status, contentType, body });
      });
      await fillResend(page);
      await resendButton(page).click();
      await page.getByRole('main').getByRole('alert').waitFor();
      assert.match(await page.getByRole('main').getByRole('alert').innerText(), expected);
      assert.doesNotMatch(await page.getByRole('main').getByRole('alert').innerText(), /private detail/);
      assert.equal(await resendButton(page).isEnabled(), true);
      assert.equal(await page.getByText(accepted).count(), 0);
      await delay(250);
      assert.deepEqual(calls, ['GET', 'POST']);
      await context.close();
    });
  }

  await check('네트워크 결과 불확실성 안내 및 자동 POST 반복 없음', async () => {
    const { context, page } = await newPage();
    const calls = [];
    await mockCsrf(page, calls);
    await page.route(api + '/api/v1/auth/email/resend', route => { calls.push('POST'); return route.abort('failed'); });
    await fillResend(page);
    await resendButton(page).click();
    await page.getByRole('main').getByRole('alert').waitFor();
    assert.match(await page.getByRole('main').getByRole('alert').innerText(), /이미 처리되었을 수/);
    assert.equal(await page.getByRole('main').getByRole('alert').evaluate(element => document.activeElement === element), true);
    await delay(250);
    assert.deepEqual(calls, ['GET', 'POST']);
    await context.close();
  });

  await check('307 리디렉션을 따라 POST를 다시 보내지 않는다', async () => {
    const { context, page } = await newPage();
    const calls = [];
    await mockCsrf(page, calls);
    let redirectedPosts = 0;
    await page.route(api + '/api/v1/auth/email/redirect-target', route => {
      redirectedPosts++;
      return route.fulfill({ status: 202, json: { status: 'ACCEPTED' } });
    });
    await page.route(api + '/api/v1/auth/email/resend', route => {
      calls.push('POST');
      return route.fulfill({ status: 307, headers: { Location: api + '/api/v1/auth/email/redirect-target' } });
    });
    await fillResend(page);
    await resendButton(page).click();
    await page.getByRole('main').getByRole('alert').waitFor();
    assert.equal(redirectedPosts, 0);
    assert.deepEqual(calls, ['GET', 'POST']);
    await context.close();
  });

  await check('CSRF 조회 실패에는 POST 없음, 화면을 떠난 뒤에도 POST 없음', async () => {
    const { context, page } = await newPage();
    let posts = 0;
    page.on('request', request => { if (request.method() === 'POST' && request.url() === api + '/api/v1/auth/email/resend') posts++; });
    await page.route(api + '/api/v1/auth/csrf', route => route.abort('failed'));
    await fillResend(page);
    await resendButton(page).click();
    await page.getByRole('main').getByRole('alert').waitFor();
    assert.match(await page.getByRole('main').getByRole('alert').innerText(), /요청은 보내지 않았습니다/);
    assert.equal(posts, 0);
    await page.unroute(api + '/api/v1/auth/csrf');
    let release;
    let seen = false;
    const gate = new Promise(resolve => { release = resolve; });
    await page.route(api + '/api/v1/auth/csrf', async route => {
      seen = true;
      await gate;
      await route.fulfill({ status: 200, json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } });
    });
    await resendButton(page).click();
    await waitFor(() => seen, 'CSRF request not seen');
    await page.getByRole('link', { name: '로그인 화면으로 이동' }).click();
    await page.waitForURL('**/login');
    release();
    await delay(400);
    assert.equal(posts, 0);
    await context.close();
  });

  await check('잘못된 링크·토큰 누락·EMAIL_VERIFICATION_INVALID에서 재전송으로 이동', async () => {
    const { context, page } = await newPage();
    for (const suffix of ['', '?token=invalid']) {
      await page.goto(base + '/register/verify' + suffix);
      await page.getByRole('link', { name: '인증 메일 다시 요청하기' }).click();
      await page.waitForURL('**/register/resend');
    }
    await mockCsrf(page, []);
    await page.route(api + '/api/v1/auth/email/verify', route => route.fulfill({ status: 400, json: { code: 'EMAIL_VERIFICATION_INVALID' } }));
    await page.goto(base + '/register/verify?token=' + 'A'.repeat(43));
    await page.getByRole('button', { name: '이메일 인증하기', exact: true }).click();
    await page.getByRole('main').getByRole('alert').waitFor();
    await page.getByRole('link', { name: '인증 메일 다시 요청하기' }).click();
    await page.waitForURL('**/register/resend');
    await context.close();
  });

  if (local) {
    await check('기본 60초 경계 후 재전송 → 새 Mailpit 메일 → 이전 링크 실패 → 새 링크 성공 → DB ACTIVE', async () => {
      const { page, email, original, originalMailId } = local;
      const firstIssued = new Date(dbSummary(email).firstIssued).getTime();
      const remaining = Math.max(0, firstIssued + 60_500 - Date.now());
      if (remaining > 0) { console.log('Waiting for the unchanged 60-second server policy'); await delay(remaining); }
      await page.reload();
      await page.getByLabel('이메일', { exact: true }).fill(email);
      const posts = [];
      page.on('request', request => { if (request.method() === 'POST' && request.url() === api + '/api/v1/auth/email/resend') posts.push(request); });
      const responsePromise = page.waitForResponse(api + '/api/v1/auth/email/resend');
      await page.locator('form').evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
      const response = await responsePromise;
      assert.equal(response.status(), 202);
      assert.equal(response.headers()['cache-control'], 'no-store');
      assert.equal(response.headers()['access-control-allow-credentials'], 'true');
      await page.getByText(accepted, { exact: true }).waitFor();
      assert.equal(posts.length, 1);
      const headers = await posts[0].allHeaders();
      assert.ok(headers['x-csrf-token']);
      assert.match(headers.cookie ?? '', /JSESSIONID=/);
      const freshMail = await waitFor(async () => (await mailList(email)).find(mail => mail.ID !== originalMailId), 'Replacement local mail did not arrive');
      const fresh = await verificationToken(freshMail);
      assert.deepEqual(Object.fromEntries(Object.entries(dbSummary(email)).filter(([key]) => key !== 'firstIssued')), { status: 'PENDING', tokens: 2, used: 0, revoked: 1 });
      await resendButton(page).click();
      await page.getByText(accepted, { exact: true }).waitFor();
      assert.equal(dbSummary(email).tokens, 2);
      assert.equal((await mailList(email)).length, 2);
      await page.goto(base + '/register/verify?token=' + original);
      await page.getByRole('button', { name: '이메일 인증하기', exact: true }).click();
      await page.getByRole('main').getByRole('alert').waitFor();
      assert.match(await page.getByRole('main').getByRole('alert').innerText(), /유효하지 않거나 사용할 수 없는/);
      assert.equal(await page.getByRole('link', { name: '인증 메일 다시 요청하기' }).count(), 1);
      await page.goto(base + '/register/verify?token=' + fresh);
      await page.getByRole('button', { name: '이메일 인증하기', exact: true }).click();
      await page.getByText('이메일 인증이 완료되었습니다.', { exact: false }).waitFor();
      assert.deepEqual(Object.fromEntries(Object.entries(dbSummary(email)).filter(([key]) => key !== 'firstIssued')), { status: 'ACTIVE', tokens: 2, used: 1, revoked: 1 });
      await page.getByRole('link', { name: '로그인 화면으로 이동' }).click();
      await page.waitForURL('**/login');
      assert.equal((await mailList(email)).length, 2);
      console.log('Created local test member:', email, 'ACTIVE; 2 token records, 2 Mailpit messages retained');
    });
  }
  await fs.writeFile(path.join(artifacts, 'result.json'), JSON.stringify({ checks: results, createdMember: local ? { email: local.email, nickname: local.nickname, ...dbSummary(local.email) } : null }, null, 2));
  console.log('Browser checks passed:', results.length, 'Artifacts:', artifacts);
} catch (error) {
  // Playwright 오류에는 전체 인증 URL이 포함될 수 있어 메시지는 출력하지 않는다.
  console.error('Failed check:', currentCheck, 'Error kind:', error.name);
  console.error(String(error.stack).split('\n').filter(line => /at .*email-verification-resend-browser\.mjs:\d+/.test(line)).join('\n'));
  console.error('Browser verification failed after', results.length, 'completed checks. No sensitive diagnostics saved.');
  process.exitCode = 1;
} finally { await browser.close(); }
