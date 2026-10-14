// 실제 표시 DOM 검사. script/RSC 문자열을 성공으로 간주하지 않는다.
// production build + next start + Spring(공개 읽기만)이 필요하다. 알려진 미해결 항목도 실패로 보고한다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8445';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const output = process.env.COMMUNITY_404_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-community-404-'));
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });
const results = [], errors = [];
async function check(name, run) {
  try { await run(); results.push({ name, status: 'passed' }); console.log('PASS', name); }
  catch (error) { results.push({ name, status: 'failed', message: error.message }); console.error('FAIL', name, error.message); process.exitCode = 1; }
}
const missing = '/community/posts/99999999';
const agents = { browser: 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140.0.0.0 Safari/537.36', crawler: 'Twitterbot/1.0' };
try {
  for (const [name, userAgent] of Object.entries(agents)) {
    const context = await browser.newContext({ javaScriptEnabled: false, userAgent, viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage(); page.setDefaultTimeout(5000);
    await check(`${name}: 무JS 실제 HTTP404·noindex`, async () => {
      const response = await page.goto(base + missing); assert.equal(response.status(), 404);
      assert((await page.locator('meta[name=robots]').evaluateAll(nodes => nodes.map(n => n.content))).some(value => value.includes('noindex')));
    });
    await check(`${name}: 무JS 표시 본문·목록 앵커·클릭 이동`, async () => {
      await page.screenshot({ path: path.join(output, `${name}-404-nojs-1440.png`), fullPage: true });
      assert(await page.getByRole('heading', { name: '게시글을 찾을 수 없어요.' }).isVisible(), '실제 표시되는 404 제목이 없습니다.');
      const link = page.getByRole('link', { name: '목록으로 돌아가기' });
      assert(await link.isVisible()); assert.equal(await link.getAttribute('href'), '/community/posts');
      await link.click(); await page.waitForURL(base + '/community/posts');
      assert(await page.getByRole('heading', { name: '전체 게시글' }).isVisible());
    });
    await context.close();
  }
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
  await check('JS 활성: 안전한 from 조건·스크롤 복귀 및 외부 주소 차단', async () => {
    const back = '/community/posts?type=GENERAL&topic=WELFARE_LIFE';
    await page.goto(base + missing + '?from=' + encodeURIComponent(back));
    const link = page.getByRole('link', { name: '목록으로 돌아가기' }); await link.waitFor(); assert.equal(await link.getAttribute('href'), back);
    await page.evaluate(href => sessionStorage.setItem('iyum-community-scroll:' + href, '150'), back);
    await page.screenshot({ path: path.join(output, '404-js-1440.png'), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 }); await page.screenshot({ path: path.join(output, '404-js-390.png'), fullPage: true });
    await link.click(); await page.waitForURL(base + back);
    await page.waitForFunction(href => sessionStorage.getItem('iyum-community-scroll:' + href) === null, back);
    assert(Math.abs(await page.evaluate(() => scrollY) - 150) < 5);
    await page.goto(base + missing + '?from=' + encodeURIComponent('https://example.test/private'));
    await link.waitFor(); assert.equal(await link.getAttribute('href'), '/community/posts');
  });
  await check('무JS 정상 목록·상세는 실제 Spring 제목·본문·작성자 표시', async () => {
    const data = await (await fetch(api + '/api/v1/community/posts?page=0&size=20')).json(); assert(data.items.length > 0, '기존 실제 글이 필요합니다.');
    const post = await (await fetch(api + '/api/v1/community/posts/' + data.items[0].id)).json();
    const isolated = await browser.newContext({ javaScriptEnabled: false }); const nojs = await isolated.newPage();
    try {
      assert.equal((await nojs.goto(base + '/community/posts')).status(), 200);
      assert(await nojs.getByRole('link', { name: post.title, exact: true }).isVisible());
      assert.equal((await nojs.goto(base + '/community/posts/' + post.id)).status(), 200);
      assert.equal(await nojs.locator('.community-post-body').innerText(), post.content);
      assert((await nojs.locator('.community-article').innerText()).includes(post.author.nickname));
    } finally { await isolated.close(); }
  });
  await check('JS 404 복귀의 런타임 오류 없음', async () => assert.deepEqual(errors, []));
  await context.close();
} finally {
  await browser.close();
  await fs.writeFile(path.join(output, '404-results.json'), JSON.stringify({ mode: 'real Spring public reads, production browser DOM with/without JavaScript', base, results, errors, writes: 0 }, null, 2));
}
