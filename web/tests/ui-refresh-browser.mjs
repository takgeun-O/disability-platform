// 기존 서버에서 홈/공통 UI를 검증한다. 쓰기 인증 요청은 전부 브라우저에서 모의한다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const output = process.env.UI_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-ui-refresh-'));
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', reducedMotion: 'reduce' });
const checks = [], errors = [], screenshots = [];
page.on('pageerror', error => errors.push(error.message));
async function check(name, fn) { await fn(); checks.push(name); console.log('PASS', name); }
async function visit(route = '/') {
  await page.goto(base + route, { waitUntil: 'networkidle' });
  await page.waitForFunction(() => {
    const header = getComputedStyle(document.querySelector('.iyum-header-inner'));
    const brand = getComputedStyle(document.querySelector('.iyum-wordmark'));
    return header.display === 'grid' && brand.gap === (innerWidth <= 600 ? '8px' : '14px');
  });
  await page.evaluate(() => document.fonts.ready);
}
async function shot(name, fullPage = false) {
  await page.locator('img').evaluateAll(images => Promise.all(images.map(async image => {
    image.loading = 'eager';
    await image.decode();
  })));
  await page.screenshot({ path: path.join(output, name + '.png'), fullPage });
  screenshots.push({ file: name + '.png', viewport: page.viewportSize(), fullPage });
}
async function noOverflow() {
  const size = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  assert.ok(size.document <= size.viewport, JSON.stringify(size));
}
const geometry = () => page.evaluate(() => Object.fromEntries(['shortcuts', 'deadlines', 'community-preview', 'guides'].map(name => {
  const rect = document.querySelector('.iyum-' + name).getBoundingClientRect();
  return [name, { x: rect.x, y: rect.y + scrollY, width: rect.width, height: rect.height }];
})));
let realAnonymous;
try {
  await visit();
  const response = await page.request.get(api + '/api/v1/auth/me');
  realAnonymous = { status: response.status(), code: (await response.json()).code };
  await check('실제 API /me GET의 비로그인 응답 확인', async () => assert.deepEqual(realAnonymous, { status: 401, code: 'AUTHENTICATION_REQUIRED' }));
  await check('1440px 2열 배치·오른쪽 마감 위 가이드 아래·가로 넘침 없음', async () => {
    await noOverflow(); const g = await geometry();
    assert(g.deadlines.x > g['community-preview'].x); assert(g.guides.y >= g.deadlines.y + g.deadlines.height);
    assert.equal(await page.getByRole('button', { name: '메뉴 열기' }).isVisible(), false);
  });
  await check('로고·유리 나무·가이드 이미지 로드와 실제 HTML 제목/검색 유지', async () => {
    await page.locator('.iyum-hero-art img').evaluate(image => image.decode());
    assert(await page.locator('.iyum-hero-art img').evaluate(image => image.naturalWidth > 0 && image.alt === ''));
    assert.equal(await page.locator('.iyum-brand-symbol').count(), 2);
    assert.equal(await page.locator('.iyum-guide-art').count(), 2);
    assert(await page.getByRole('heading', { level: 1 }).isVisible());
    assert(await page.getByRole('searchbox', { name: '통합 검색' }).isVisible());
  });
  await shot('after-home-1440', true); await shot('after-home-1440-top');
  await check('마감 공고 빈 상태가 기본·가상 항목은 명시적으로 펼쳐 보기', async () => {
    assert(await page.getByText('아직 표시할 공고가 없어요.').isVisible());
    assert.equal(await page.getByRole('heading', { name: '예시 지원사업 A' }).isVisible(), false);
    await page.locator('.iyum-deadline-examples > summary').click();
    assert.equal(await page.locator('.iyum-deadline-list > li').count(), 3);
    assert((await page.locator('.iyum-example-note').innerText()).includes('실제 지원사업이나 신청 정보가 아닙니다'));
    await page.locator('.iyum-example-detail > summary').first().click();
    assert(await page.locator('.iyum-example-detail[open] p').isVisible());
  });
  await shot('after-home-1440-deadline-examples', true);
  await visit();
  await check('정보 찾기 Enter/Space/Escape·초점 복귀·바깥 클릭', async () => {
    const button = page.getByRole('button', { name: '정보 찾기' });
    await button.focus(); await page.keyboard.press('Enter');
    assert.equal(await page.locator('#iyum-information-menu a:visible').count(), 4); await shot('after-menu-1440');
    await page.keyboard.press('Tab'); await page.keyboard.press('Escape');
    assert.equal(await button.getAttribute('aria-expanded'), 'false'); assert(await button.evaluate(el => el === document.activeElement));
    await page.keyboard.press('Space'); await page.locator('#shortcuts-heading').click();
    assert.equal(await button.getAttribute('aria-expanded'), 'false');
  });
  await check('빈 검색 오류·초점 및 기존 쿼리 경로 유지·예시 결과 안내', async () => {
    await page.getByRole('button', { name: '검색', exact: true }).click();
    assert.equal(await page.locator('#hero-search').getAttribute('aria-invalid'), 'true');
    assert(await page.locator('#hero-search').evaluate(el => el === document.activeElement));
    await page.locator('#hero-search').fill('보청기 지원'); await page.keyboard.press('Enter'); await page.waitForURL('**/search?**');
    assert.equal(new URL(page.url()).searchParams.get('q'), '보청기 지원');
    assert(await page.locator('.iyum-search-results-notice').isVisible());
  });
  await page.setViewportSize({ width: 390, height: 844 }); await visit();
  await check('390px 바로가기→마감→커뮤니티→가이드 순서와 넘침 없음', async () => {
    await noOverflow(); const g = await geometry();
    assert(g.shortcuts.y < g.deadlines.y && g.deadlines.y < g['community-preview'].y && g['community-preview'].y < g.guides.y);
    assert.equal(await page.locator('.iyum-nav-items').isVisible(), false);
  });
  await shot('after-home-390', true); await shot('after-home-390-top');
  await check('모바일 메뉴 닫힘·하위 메뉴·Escape 초점 복귀·경로 이동', async () => {
    const toggle = page.getByRole('button', { name: '메뉴 열기' }); await toggle.click();
    const info = page.getByRole('button', { name: '정보 찾기' }); await info.click(); await shot('after-menu-390');
    await page.keyboard.press('Escape'); assert(await info.evaluate(el => el === document.activeElement));
    await page.keyboard.press('Escape'); assert(await toggle.evaluate(el => el === document.activeElement));
    await toggle.click(); await info.click();
    await page.getByRole('link', { name: /기기 정보/ }).click(); await page.waitForURL('**/devices');
    assert(await page.getByRole('main').getByText('준비 중입니다.', { exact: false }).isVisible());
    assert.equal(await page.locator('.iyum-nav-items').isVisible(), false);
  });
  await visit();
  await check('IYUM 소개는 실제 홈 소개로 이동', async () => {
    await page.getByRole('button', { name: '메뉴 열기' }).click();
    await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: 'IYUM 소개' }).click();
    await page.waitForURL('**/#iyum-about'); assert(await page.locator('#about-heading').isVisible());
  });
  for (const width of [1440, 390]) for (const [name, route] of [['login', '/login'], ['community', '/community/posts'], ['p03', '/validation/hearing-aid-health-insurance']]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 }); await visit(route);
    await check(`${width}px 기존 ${name} 화면과 공통 헤더`, async () => { await noOverflow(); assert.equal(await page.getByRole('banner').count(), 1); assert.equal(await page.getByLabel('회원 메뉴').count(), 1); });
    await shot(`after-${name}-${width}`);
  }
  await check('홈·로그인·P03 본문 바로가기 키보드 초점 이동', async () => {
    for (const route of ['/', '/login', '/validation/hearing-aid-health-insurance']) {
      await visit(route);
      await page.getByRole('link', { name: '본문 바로가기', exact: true }).focus();
      await page.keyboard.press('Enter');
      assert(await page.locator('#main-content-start').evaluate(el => el === document.activeElement));
    }
  });
  await check('가입 약관→정보 화면의 기존 흐름 유지', async () => {
    await visit('/register'); await page.getByRole('checkbox', { name: /이용약관/ }).check();
    await page.getByRole('checkbox', { name: /개인정보 수집/ }).check(); await page.getByRole('button', { name: '다음', exact: true }).click();
    await page.waitForURL('**/register/info'); assert(await page.locator('input[autocomplete=nickname]').isVisible());
  });
  await check('모바일 메뉴에서 작성 중 이탈 취소 시 입력·링크 초점 유지', async () => {
    await visit('/community/posts/create');
    const title = page.getByPlaceholder('제목을 입력하세요.'); await title.fill('보존할 작성 내용');
    await page.getByRole('button', { name: '메뉴 열기' }).click();
    await page.getByRole('button', { name: '정보 찾기' }).click();
    const guide = page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: /이용 가이드/ });
    for (const cancel of ['keyboard', 'pointer']) {
      await guide.focus(); await page.keyboard.press('Enter'); await page.getByRole('dialog').waitFor();
      if (cancel === 'keyboard') await page.keyboard.press('Escape');
      else await page.getByRole('button', { name: '계속 작성', exact: true }).click();
      assert.equal(await title.inputValue(), '보존할 작성 내용');
      assert(await guide.isVisible()); assert(await guide.evaluate(el => el === document.activeElement));
    }
    await guide.click(); await page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true }).click();
    await page.waitForURL('**/validation/hearing-aid-health-insurance');
    assert.equal(await page.locator('.iyum-nav-items').isVisible(), false);
  });
  let authenticated = true;
  await page.route(api + '/api/v1/auth/**', route => {
    const endpoint = new URL(route.request().url()).pathname.split('/').at(-1);
    if (endpoint === 'csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'mock-ui-only' } });
    if (endpoint === 'logout') { authenticated = false; return route.fulfill({ status: 204 }); }
    return route.fulfill(authenticated ? { json: { memberId: 123, email: 'ui@example.test', nickname: '긴닉네임디자인검토회원', role: 'USER' } } : {
      status: 401,
      json: { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] },
    });
  });
  for (const width of [390, 320, 1440]) {
    await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); await visit();
    await check(`${width}px 복원된 로그인 헤더·긴 닉네임`, async () => { await page.getByRole('button', { name: '로그아웃', exact: true }).waitFor(); await noOverflow(); });
    if (width !== 320) await shot(`after-header-${width}-authenticated`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await check('모바일 로그아웃 성공 알림과 닫기·비로그인 상태 유지', async () => {
    await page.getByRole('button', { name: '로그아웃', exact: true }).click();
    await page.getByText('로그아웃되었습니다.', { exact: true }).waitFor(); await noOverflow();
    const box = await page.locator('.iyum-auth-notice').boundingBox(); assert(box.x >= 0 && box.x + box.width <= 390);
    await shot('after-header-390-logout'); await page.getByRole('button', { name: '알림 닫기' }).click();
    assert(await page.getByLabel('회원 메뉴').getByRole('link', { name: '로그인', exact: true }).isVisible());
  });
  await check('로그아웃 후 새로고침해도 비로그인 회원 메뉴 복원', async () => {
    for (const width of [390, 1440]) {
      await page.setViewportSize({ width, height: width === 390 ? 844 : 1000 });
      await page.reload({ waitUntil: 'domcontentloaded' });
      const memberMenu = page.getByLabel('회원 메뉴');
      await memberMenu.getByRole('link', { name: '로그인', exact: true }).waitFor({ timeout: 10_000 });
      assert(await memberMenu.getByRole('link', { name: '회원가입', exact: true }).isVisible());
      assert.equal(await memberMenu.getByRole('button', { name: '로그아웃', exact: true }).count(), 0);
      assert.equal(await memberMenu.getByText('로그인 상태를 확인하지 못했습니다.', { exact: true }).count(), 0);
      await noOverflow();
    }
  });
  await check('브라우저 런타임 오류 없음', async () => assert.deepEqual(errors, []));
  await fs.writeFile(path.join(output, 'ui-results.json'), JSON.stringify({ checks, realAnonymous, authScenarios: 'API mocked; no account creation or mail', screenshots }, null, 2));
  console.log('Browser checks passed:', checks.length);
} finally { await browser.close(); }
