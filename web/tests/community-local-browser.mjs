// 명시적 opt-in: 로컬 dev API/DB에 테스트 회원 1명과 게시글 1건을 생성하고 남긴다.
// 쿠키·비밀번호·CSRF·인증 토큰·메일 원문은 출력하거나 파일에 저장하지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
if (process.env.IYUM_RUN_LOCAL_COMMUNITY_TEST !== 'true') { console.log('SKIP: set IYUM_RUN_LOCAL_COMMUNITY_TEST=true to create one local account and post'); process.exit(0); }
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const mailpit = process.env.MAILPIT_BASE_URL ?? 'http://127.0.0.1:8025';
for (const value of [base, api, mailpit]) assert.equal(new URL(value).hostname, '127.0.0.1', 'local test only');
const output = process.env.COMMUNITY_LOCAL_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-community-local-'));
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', reducedMotion: 'reduce' });
const page = await context.newPage(); page.setDefaultTimeout(15000);
const checks = [], created = {}, errors = [];
page.on('pageerror', error => errors.push(error.message));
const suffix = Date.now().toString(36);
const email = `iyum-community-${suffix}@example.test`, nickname = `UI_${suffix}`;
const password = 'LocalOnly' + crypto.randomUUID() + '7!';
const textTitle = `[로컬 UI 검증 ${suffix}] 커뮤니티 작성과 조회 확인`;
const textBody = '이 게시글은 로컬 연동 검증용 예시입니다.\n\n줄바꿈과 공백을 보존합니다.  \n실제 복지·기기 정보가 아닙니다.';
const listPath = '/community/posts?type=GENERAL&topic=WELFARE_LIFE';
const createPath = '/community/posts/create?type=GENERAL&topic=WELFARE_LIFE&from=' + encodeURIComponent(listPath);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let current;
async function check(name, fn) { current = name; await fn(); checks.push(name); console.log('PASS REAL', name); }
async function waitFor(predicate) { const end = Date.now() + 15000; while (Date.now() < end) { const value = await predicate(); if (value) return value; await delay(150); } throw Error('Condition not reached'); }
async function listReady() { await page.waitForFunction(() => document.querySelector('.community-list-column [aria-busy]')?.getAttribute('aria-busy') === 'false'); }
async function shot(name) { await page.evaluate(() => document.fonts.ready); await page.screenshot({ path: path.join(output, name + '.png'), fullPage: true }); }
try {
  await check('실제 비로그인 목록·상세 공개 조회와 없는 게시글 404', async () => {
    await page.goto(base + '/community'); await listReady(); assert.equal((await page.request.get(api + '/api/v1/auth/me')).status(), 401);
    const response = await page.request.get(api + '/api/v1/community/posts?page=0&size=20'); assert.equal(response.status(), 200);
    const body = await response.json(); assert.equal(await page.locator('.community-post-list > li').count(), body.items.length);
    if (body.items.length) { await page.locator('.community-post-list a').first().click(); await page.locator('.community-post-body').waitFor(); }
    await page.goto(base + '/community/posts/99999999'); await page.getByRole('heading', { name: '게시글을 찾을 수 없어요.' }).waitFor();
  });
  await check('실제 필터 선택 대기·적용·URL·정상 빈 결과·390px 취소', async () => {
    await page.goto(base + '/community/posts'); await listReady(); const previous = await page.locator('.community-post-list').innerText(); const startUrl = page.url();
    await page.getByLabel('글 유형', { exact: true }).selectOption('REVIEW'); await page.getByLabel('주제', { exact: true }).selectOption('WELFARE_LIFE');
    assert.equal(page.url(), startUrl); assert.equal(await page.locator('.community-post-list').innerText(), previous);
    await page.getByRole('button', { name: '적용', exact: true }).click(); await page.waitForURL('**topic=WELFARE_LIFE'); await listReady(); await page.getByText('아직 표시할 게시글이 없어요.').waitFor();
    await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('button', { name: '필터', exact: true }).click(); const dialog = page.getByRole('dialog'); await dialog.getByLabel('글 유형', { exact: true }).selectOption('GENERAL'); await dialog.getByRole('button', { name: '취소', exact: true }).click();
    assert.equal(new URL(page.url()).searchParams.get('type'), 'REVIEW');
  });
  await check('실제 회원가입 PENDING→Mailpit 인증→로그인 수동 수행', async () => {
    await page.setViewportSize({ width: 1440, height: 1000 }); await page.goto(base + '/register');
    await page.getByRole('checkbox', { name: /이용약관/ }).check(); await page.getByRole('checkbox', { name: /개인정보 수집/ }).check(); await page.getByRole('button', { name: '다음', exact: true }).click(); await page.waitForURL('**/register/info');
    await page.locator('input[autocomplete=email]').fill(email); await page.locator('input[autocomplete=new-password]').nth(0).fill(password); await page.locator('input[autocomplete=new-password]').nth(1).fill(password); await page.locator('input[autocomplete=nickname]').fill(nickname);
    const signup = page.waitForResponse(api + '/api/v1/auth/signup'); await page.getByRole('button', { name: '가입하기', exact: true }).click(); assert.equal((await signup).status(), 201); await page.waitForURL('**/register/verify');
    Object.assign(created, { email, nickname, memberStatus: 'PENDING' });
    const messages = await waitFor(async () => { const res = await fetch(mailpit + '/api/v1/search?query=' + encodeURIComponent('to:' + email)); const messages = (await res.json()).messages ?? []; return messages.length ? messages : null; });
    const message = await (await fetch(mailpit + '/api/v1/message/' + messages[0].ID)).json();
    const token = String(message.Text).match(/token=([A-Za-z0-9_-]{43})/)?.[1]; assert.ok(token);
    await page.goto(base + '/register/verify?token=' + token); await page.getByRole('button', { name: '이메일 인증하기', exact: true }).click(); await page.getByText('이메일 인증이 완료되었습니다.', { exact: false }).waitFor();
    created.memberStatus = 'ACTIVE'; created.mailCount = messages.length;
    await page.goto(base + '/login?from=' + encodeURIComponent(createPath)); await page.locator('input[name=email]').fill(email); await page.locator('input[name=password]').fill(password);
    await page.getByRole('main').getByRole('button', { name: '로그인', exact: true }).click(); await page.waitForURL(url => url.pathname === '/community/posts/create'); await page.getByRole('form', { name: '게시글 작성' }).waitFor();
    const me = await page.request.get(api + '/api/v1/auth/me'); assert.equal(me.status(), 200); created.memberId = (await me.json()).memberId;
  });
  await check('실제 CSRF+쿠키 글 작성 201·중복 제출 방지·상세 저장 내용', async () => {
    await page.getByPlaceholder('제목을 입력하세요.').fill('  ' + textTitle + '  '); await page.getByPlaceholder('내용을 입력하세요.').fill(textBody);
    for (const width of [1440, 390]) { await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); await shot(`real-create-${width}`); }
    let count = 0; const listener = request => { if (request.method() === 'POST' && request.url() === api + '/api/v1/community/posts') count++; }; page.on('request', listener);
    const postResponse = page.waitForResponse(response => response.url() === api + '/api/v1/community/posts' && response.request().method() === 'POST');
    await page.getByRole('form', { name: '게시글 작성' }).evaluate(form => { form.requestSubmit(); form.requestSubmit(); });
    const response = await postResponse; assert.equal(response.status(), 201); const body = await response.json(); created.postId = body.id; assert(Number.isSafeInteger(body.id));
    await page.waitForURL(url => url.pathname === '/community/posts/' + body.id); await page.locator('.community-post-body').waitFor(); assert.equal(await page.locator('.community-post-body').textContent(), textBody); assert.equal(count, 1); page.off('request', listener);
    const detail = await (await page.request.get(api + '/api/v1/community/posts/' + body.id)).json(); assert.equal(detail.title, textTitle); assert.equal(detail.author.memberId, created.memberId);
    await page.reload(); await page.locator('.community-post-body').waitFor(); assert.equal(await page.locator('.community-post-body').textContent(), textBody);
    for (const width of [1440, 390, 320]) { await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); if (width !== 320) await shot(`real-detail-${width}`); }
  });
  await check('실제 작성 글 필터 목록·새로고침·모바일 적용·상세 복귀', async () => {
    await page.getByRole('link', { name: '← 목록으로', exact: true }).click(); await listReady(); assert.equal(new URL(page.url()).pathname + new URL(page.url()).search, listPath); await page.getByRole('link', { name: textTitle, exact: true }).waitFor();
    await page.reload(); await listReady(); await page.getByRole('link', { name: textTitle, exact: true }).waitFor();
    for (const width of [1440, 390]) { await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); await shot(`real-list-${width}`); }
    await page.getByRole('button', { name: '필터', exact: true }).click(); await page.getByRole('dialog').getByLabel('글 유형', { exact: true }).selectOption('REVIEW'); await page.getByRole('dialog').getByRole('button', { name: '적용', exact: true }).click(); await page.waitForURL('**type=REVIEW&topic=WELFARE_LIFE'); await listReady(); await page.getByText('아직 표시할 게시글이 없어요.').waitFor();
  });
  await check('실제 CSRF 누락 403 및 서버 로그아웃 후 작성 401·입력 보존', async () => {
    const forbidden = await page.evaluate(async ({ api }) => { const res = await fetch(api + '/api/v1/community/posts', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'GENERAL', topic: 'DAILY', title: '저장되면 안 됨', content: '보안 검증' }) }); return { status: res.status, code: (await res.json()).code }; }, { api }); assert.deepEqual(forbidden, { status: 403, code: 'CSRF_TOKEN_INVALID' });
    await page.goto(base + createPath); await page.getByPlaceholder('제목을 입력하세요.').fill('만료 후 보존할 입력'); await page.getByPlaceholder('내용을 입력하세요.').fill('이 글은 저장되지 않아야 합니다.');
    await page.evaluate(async api => { const csrf = await (await fetch(api + '/api/v1/auth/csrf', { credentials: 'include' })).json(); const res = await fetch(api + '/api/v1/auth/logout', { method: 'POST', credentials: 'include', headers: { [csrf.headerName]: csrf.token } }); if (res.status !== 204) throw Error('logout failed'); }, api);
    const rejected = page.waitForResponse(response => response.url() === api + '/api/v1/community/posts' && response.request().method() === 'POST'); await page.getByRole('button', { name: '등록', exact: true }).click(); assert.equal((await rejected).status(), 401);
    await page.getByRole('link', { name: '로그인하고 글쓰기' }).waitFor(); assert.equal(await page.getByPlaceholder('제목을 입력하세요.').inputValue(), '만료 후 보존할 입력');
    await page.getByRole('form', { name: '게시글 작성' }).getByRole('button', { name: '취소', exact: true }).click(); await page.getByRole('dialog').waitFor(); await page.getByRole('button', { name: '계속 작성', exact: true }).click();
  });
  await check('실제 작성 기록은 로그아웃 후에도 공개 조회·추가 글 저장 없음', async () => {
    const res = await page.request.get(api + '/api/v1/community/posts?type=GENERAL&topic=WELFARE_LIFE&page=0&size=20'); assert.equal(res.status(), 200); const posts = (await res.json()).items;
    assert.equal(posts.filter(p => p.author.memberId === created.memberId).length, 1); assert.equal((await page.request.get(api + '/api/v1/auth/me')).status(), 401); assert.deepEqual(errors, []);
  });
} catch (error) { console.error('FAILED REAL', current, error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')); process.exitCode = 1; }
finally {
  await fs.writeFile(path.join(output, 'real-results.json'), JSON.stringify({ mode: 'real local Spring / PostgreSQL / Mailpit, no response mocking', checks, created, errors, existingServersPreserved: true, deletedData: false }, null, 2));
  await context.close(); await browser.close();
}
