// SSR 공개 GET용 로컬 모의 서버 + 브라우저 API 모의. 실제 Spring/DB 검증과 구분한다.
// 임시 Next 서버에 IYUM_API_INTERNAL_URL=http://127.0.0.1:$COMMUNITY_SSR_MOCK_PORT 설정 필요.
import http from 'node:http';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api = process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const output = process.env.COMMUNITY_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-community-'));
await fs.mkdir(output, { recursive: true });
const mockPort = Number(process.env.COMMUNITY_SSR_MOCK_PORT);
assert(mockPort > 0, 'COMMUNITY_SSR_MOCK_PORT와 같은 주소를 사용한 임시 Next 서버가 필요합니다.');
let ssrServer;
const browser = await chromium.launch({ headless: true, channel: process.env.PLAYWRIGHT_CHANNEL ?? 'chrome' });
const checks = [], errors = [];
let currentCheck;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const failure = (code, fieldErrors = []) => ({ code, message: 'private server details', fieldErrors });
const member = { memberId: 123, email: 'community@example.test', nickname: '검증회원', role: 'USER' };
const types = ['QUESTION', 'REVIEW', 'GENERAL'];
const topics = ['HEARING_AID', 'COCHLEAR_IMPLANT', 'DAILY', 'EMPLOYMENT', 'COMMUNICATION', 'TREATMENT_REHABILITATION'];
const titles = ['처음 보청기를 맞출 때 무엇부터 확인하면 좋을까요?', '인공와우 적응 중, 일상에서 도움이 된 작은 습관', '오늘 나누고 싶은 소소한 일상 이야기', '직장에서 소통할 때 어떤 방법을 사용하시나요?', '가족과 함께 자막 앱을 사용해 본 경험', '집에서 꾸준히 할 수 있는 청취 연습이 궁금해요'];
const items = Array.from({ length: 63 }, (_, index) => ({ id: index + 1, type: types[index % 3], topic: topics[index % 6],
  title: `[검증 예시] ${titles[index % 6]} ${index + 1}`, author: { memberId: 123, nickname: '예시회원' }, createdAt: '2026-10-14T01:00:00Z' }));
function listBody(url) {
  const q = new URL(url).searchParams, page = Number(q.get('page') ?? 0), size = Number(q.get('size') ?? 20);
  const filtered = items.filter(item => (!q.get('type') || item.type === q.get('type')) && (!q.get('topic') || item.topic === q.get('topic')));
  return { items: filtered.slice(page * size, (page + 1) * size), page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size), hasNext: (page + 1) * size < filtered.length };
}
async function mock(options = {}) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', reducedMotion: 'reduce' });
  const page = await context.newPage(); page.setDefaultTimeout(30000); page.on('pageerror', error => errors.push(error.message));
  const state = { authenticated: !!options.authenticated, list: null, detail: null, post: null, me: null, csrf: null, calls: { list: [], detail: [], post: [], csrf: 0, login: 0 } };
  await page.route(api + '/api/v1/auth/**', async route => {
    const endpoint = new URL(route.request().url()).pathname.split('/').at(-1);
    if (endpoint === 'csrf') { state.calls.csrf++; if (state.csrf) return state.csrf(route); return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf-' + state.calls.csrf } }); }
    if (endpoint === 'login') { state.calls.login++; state.authenticated = true; return route.fulfill({ json: member }); }
    if (endpoint === 'logout') { state.authenticated = false; return route.fulfill({ status: 204 }); }
    if (state.me) return state.me(route);
    return route.fulfill(state.authenticated ? { json: member } : { status: 401, json: failure('AUTHENTICATION_REQUIRED') });
  });
  const handlePost = async route => {
    const request = route.request(); const url = new URL(request.url());
    if (request.method() === 'POST') {
      state.calls.post.push({ body: request.postDataJSON(), headers: request.headers() });
      return state.post ? state.post(route) : route.fulfill({ status: 201, json: { id: 1 } });
    }
    const id = url.pathname.split('/').at(-1);
    if (id === 'posts') { state.calls.list.push(url.toString()); return state.list ? state.list(route) : route.fulfill({ json: listBody(url) }); }
    state.calls.detail.push(id);
    if (state.detail) return state.detail(route);
    const item = items.find(item => item.id === Number(id));
    return item ? route.fulfill({ json: { ...item, content: '첫 줄\n\n둘째 줄\n<script>window.communityXss=true</script>\n' + '긴문자열'.repeat(90), updatedAt: item.createdAt } })
      : route.fulfill({ status: 404, json: failure('POST_NOT_FOUND') });
  };
  await page.route(api + '/api/v1/community/posts**', handlePost);
  state.serverCalls = [];
  ssrServer = http.createServer(async (req, res) => {
    state.serverCalls.push({url:req.url,headers:req.headers});
    const route = {
      request:()=>({url:()=>`http://127.0.0.1:${mockPort}${req.url}`,method:()=>req.method,headers:()=>req.headers}),
      fulfill:async({status=200,json,body='',contentType='application/json'})=>{res.writeHead(status,{'Content-Type':contentType});res.end(json === undefined ? body : JSON.stringify(json));},
      abort:async()=>res.destroy(),
    };
    try { await handlePost(route); } catch { if (!res.destroyed) { res.writeHead(500);res.end(); } }
  });
  await new Promise((resolve,reject)=>{ssrServer.once('error',reject);ssrServer.listen(mockPort,'127.0.0.1',resolve);});
  return { context, page, state };
}
async function check(name, run) { currentCheck = name; await run(); checks.push(name); console.log('PASS', name); }
async function waitFor(predicate) { const end = Date.now() + 12000; while (Date.now() < end) { if (await predicate()) return; await delay(50); } throw Error('condition timed out: ' + currentCheck); }
const posts = page => page.locator('.community-post-list > li');
const form = page => page.getByRole('form', { name: '게시글 작성' });
const title = page => page.getByPlaceholder('제목을 입력하세요.');
const content = page => page.getByPlaceholder('내용을 입력하세요.');
const register = page => form(page).getByRole('button', { name: '등록', exact: true });
async function listReady(page) { await page.waitForFunction(() => document.querySelector('.community-list-column [aria-busy]')?.getAttribute('aria-busy') === 'false'); }
async function noOverflow(page) { assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); }
async function shot(page, name) { await page.evaluate(() => document.fonts.ready); await page.screenshot({ path: path.join(output, name + '.png'), fullPage: true }); }
async function fillPost(page) {
  await form(page).waitFor();
  await page.getByLabel('글 유형', { exact: true }).selectOption('QUESTION'); await page.getByLabel('주제', { exact: true }).selectOption('HEARING_AID');
  await title(page).fill('  [검증 예시] 함께 나누고 싶은 경험  '); await content(page).fill('  첫 줄\n\n둘째 줄  ');
}
try {
  const { page, context, state } = await mock();
  await check('SSR 목록/상세 HTML·metadata·쿠키 비전달·없는 글 HTTP 404', async () => {
    const body = html=>html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,'');
    const listing=await fetch(base+'/community',{headers:{Cookie:'review-only=fake'}});assert.equal(listing.status,200);
    assert.ok(body(await listing.text()).includes(items[0].title));
    const detail=await fetch(base+'/community/posts/1');assert.equal(detail.status,200);const html=body(await detail.text());
    assert.ok(html.includes(items[0].title));assert.ok(html.includes('첫 줄'));assert.ok(html.includes('예시회원'));
    assert.ok(html.includes('property="og:title"'));assert.ok(html.includes('name="description"'));assert.ok(html.includes('noindex'));
    const missing=await fetch(base+'/community/posts/99999999');assert.equal(missing.status,404);await missing.text(); // 무JS 본문은 community-404-browser.mjs가 실제 DOM으로 별도 검사 (현재 실패)
    assert(state.serverCalls.every(call=>!call.headers.cookie&&!call.headers.authorization&&!call.headers['x-csrf-token']));
    const isolated=await browser.newContext({javaScriptEnabled:false});const nojs=await isolated.newPage();await nojs.goto(base+'/community/posts/1');assert(await nojs.locator('.community-post-body').isVisible());await isolated.close();
  });
  await check('초기 hydration에서 목록/상세 API 중복 조회 없음·초점 강제 이동 없음',async()=>{
    let reads=0;const listener=req=>{if(req.url().includes('/api/v1/community/posts'))reads++;};page.on('request',listener);
    await page.goto(base+'/community');await listReady(page);await delay(200);assert.equal(reads,0);assert.equal(await page.evaluate(()=>scrollY),0);
    assert.equal(await page.locator('#community-results-heading').evaluate(el=>el===document.activeElement),false);
    await page.goto(base+'/community/posts/1');await page.locator('.community-post-body').waitFor();await delay(200);assert.equal(reads,0);page.off('request',listener);
  });
  await check('범위 밖 99페이지/마지막 페이지 소멸·필터 유지 첫 페이지·실제 빈 조건 구분',async()=>{
    await page.goto(base+'/community/posts?type=QUESTION&topic=HEARING_AID&page=99');await listReady(page);
    await page.getByRole('heading',{name:'요청한 페이지를 표시할 수 없어요.'}).waitFor();assert.equal(await page.locator('.community-pagination').count(),0);
    assert(!/100 \/ 1/.test(await page.locator('.community-request-status').innerText()));assert.equal(await page.getByText('다른 조건으로 살펴보거나 첫 이야기를 나눠 주세요.').count(),0);
    await page.getByRole('button',{name:'같은 조건의 첫 페이지로'}).click();await listReady(page);await page.waitForURL('**type=QUESTION&topic=HEARING_AID');assert.equal(await posts(page).count(),11);
    await page.goto(base+'/community/posts?type=REVIEW&topic=HEARING_AID');await page.getByRole('heading',{name:'아직 표시할 게시글이 없어요.'}).waitFor();
    await page.goto(base+'/community/posts?type=REVIEW&topic=HEARING_AID&page=1');await page.getByRole('heading',{name:'요청한 페이지를 표시할 수 없어요.'}).waitFor();
    for(const value of ['-1','abc','999999999999999999999']){await page.goto(base+'/community/posts?page='+value);await listReady(page);assert.equal(await posts(page).count(),20);}
  });
  await check('비로그인 진입·실제 목록 계약·공개 GET에 CSRF 없음·베스트 준비', async () => {
    await page.goto(base + '/community'); await listReady(page); assert.equal(await posts(page).count(), 20); assert.equal(state.calls.csrf, 0);
    assert(await page.getByText('베스트글을 준비하고 있어요.', { exact: true }).isVisible());
    assert.equal(await page.getByRole('button', { name: '최신순', exact: true }).count(), 0);
    const boxes = await page.evaluate(() => ['.community-list-column', '.community-best'].map(s => document.querySelector(s).getBoundingClientRect().x)); assert(boxes[1] > boxes[0]);
    await noOverflow(page); await shot(page, 'mock-list-1440');
  });
  await check('선택만 하면 요청·URL·기존 목록 불변', async () => {
    const calls = state.calls.list.length; await page.getByLabel('글 유형', { exact: true }).selectOption('QUESTION');
    await delay(120); assert.equal(state.calls.list.length, calls); assert.equal(new URL(page.url()).search, ''); assert.equal(await posts(page).count(), 20);
  });
  await check('적용 전환 동안 기존 목록·조건 보존, 성공 후 URL·새 목록 일치', async () => {
    let release; const gate = new Promise(resolve => { release = resolve; }); state.list = async route => { await gate; return route.fulfill({ json: listBody(route.request().url()) }); };
    await page.getByRole('button', { name: '적용', exact: true }).click(); await page.waitForURL('**type=QUESTION'); await page.waitForFunction(() => document.querySelector('.community-list-column [aria-busy]')?.getAttribute('aria-busy') === 'true');
    assert.equal(await posts(page).count(), 20); assert.match(await page.locator('.community-applied').innerText(), /전체 유형/); assert.match(await page.locator('.community-request-status').innerText(), /이전 결과/);
    release(); await listReady(page); await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el!==document.activeElement && el.getBoundingClientRect().top>=0 && el.getBoundingClientRect().top<200)); assert.equal(new URL(page.url()).searchParams.get('type'), 'QUESTION');
    assert.match(await page.locator('.community-applied').innerText(), /질문/); assert.equal(await posts(page).count(), 20); state.list = null;
  });
  await check('페이지 이동·새로고침·신규 조건 적용 시 첫 페이지', async () => {
    await page.getByRole('button', { name: '다음 페이지', exact: true }).scrollIntoViewIfNeeded(); assert((await page.evaluate(()=>scrollY))>500);
    await page.getByRole('button', { name: '다음 페이지', exact: true }).click(); await page.waitForURL('**page=1'); await listReady(page); assert.equal(await posts(page).count(), 1); await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el!==document.activeElement && el.getBoundingClientRect().top>=0 && el.getBoundingClientRect().bottom<innerHeight));
    await page.reload(); await listReady(page); assert.equal(await posts(page).count(), 1);
    await page.getByLabel('주제', { exact: true }).selectOption('HEARING_AID'); await page.getByRole('button', { name: '적용', exact: true }).click(); await page.waitForURL('**topic=HEARING_AID'); await listReady(page);
    assert.equal(new URL(page.url()).searchParams.get('page'), null); assert.equal(await posts(page).count(), 11);
  });
  await check('상세 왕복 필터·페이지·스크롤 복원 / 브라우저 Back·Forward', async () => {
    const savedUrl = page.url(); const link = posts(page).nth(7).getByRole('link'); await link.scrollIntoViewIfNeeded(); const y = await page.evaluate(() => scrollY); assert(y > 200);
    await link.click(); await page.locator('.community-post-body').waitFor(); await page.getByRole('link', { name: '← 목록으로', exact: true }).click(); await listReady(page);
    assert.equal(page.url(), savedUrl); await waitFor(async () => Math.abs((await page.evaluate(() => scrollY)) - y) < 6);
    await posts(page).nth(7).getByRole('link').click(); await page.locator('.community-post-body').waitFor(); await page.goBack(); await listReady(page); assert.equal(page.url(), savedUrl);
    await waitFor(async () => Math.abs((await page.evaluate(() => scrollY)) - y) < 6);
    await page.goForward(); await page.locator('.community-post-body').waitFor(); await page.goBack(); await listReady(page);
  });
  await check('직접 진입은 상세 진입 때 남긴 과거 스크롤을 복원하지 않음',async()=>{
    const url=page.url();await posts(page).nth(7).getByRole('link').click();await page.locator('.community-post-body').waitFor();
    await page.goto(url);await listReady(page);await delay(120);assert.equal(await page.evaluate(()=>scrollY),0);
  });
  await check('필터 500은 기존 목록·표시 조건·선택값 보존 및 재시도', async () => {
    const previous = await posts(page).first().innerText(); state.list = route => route.fulfill({ status: 500, json: failure('SERVER_ERROR') });
    await page.getByLabel('글 유형', { exact: true }).selectOption('REVIEW'); await page.getByRole('button', { name: '적용', exact: true }).click(); await page.getByRole('main').getByRole('alert').waitFor();
    assert.equal(await posts(page).first().innerText(), previous); assert.match(await page.locator('.community-applied').innerText(), /질문/);
    assert.equal(await page.getByLabel('글 유형', { exact: true }).inputValue(), 'REVIEW'); state.list = null;
    await page.getByRole('button', { name: '다시 시도', exact: true }).click(); await listReady(page); await page.getByText('아직 표시할 게시글이 없어요.', { exact: true }).waitFor(); assert.equal(await page.getByRole('main').getByRole('alert').count(), 0);
  });
  await check('빠른 적용 시 느린 이전 성공/실패가 최신 결과를 덮지 않음', async () => {
    for (const status of [200, 500]) {
      let release; const gate = new Promise(resolve => { release = resolve; }); let held = false;
      state.list = async route => { const url = new URL(route.request().url()); if (url.searchParams.get('type') === 'QUESTION') { held = true; await gate; return route.fulfill(status === 200 ? { json: listBody(url) } : { status, json: failure('SERVER_ERROR') }).catch(() => {}); } return route.fulfill({ json: listBody(url) }); };
      await page.getByLabel('글 유형', { exact: true }).selectOption('QUESTION'); await page.getByLabel('주제', { exact: true }).selectOption(''); await page.getByRole('button', { name: '적용', exact: true }).click(); await waitFor(() => held);
      await page.getByLabel('글 유형', { exact: true }).selectOption('REVIEW'); await page.getByRole('button', { name: '적용', exact: true }).click(); await page.waitForURL(url => url.searchParams.get('type') === 'REVIEW'); await listReady(page);
      await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el!==document.activeElement && el.getBoundingClientRect().top>=0 && el.getBoundingClientRect().top<200));await page.evaluate(()=>scrollTo(0,500));await delay(80);release(); await delay(120);assert(Math.abs(await page.evaluate(()=>scrollY)-500)<6); assert.match(await page.locator('.community-applied').innerText(), /경험/); assert.equal(await page.getByRole('main').getByRole('alert').count(), 0); assert.equal(await posts(page).count(), 20);
    } state.list = null;
  });
  await check('조회 중 뒤로 가면 늦은 응답을 무시하고 이전 URL 결과 복원', async () => {
    let release; const gate = new Promise(resolve => { release = resolve; }); let held = false;
    state.list = async route => { if (new URL(route.request().url()).searchParams.get('type') === 'GENERAL') { held = true; await gate; } return route.fulfill({ json: listBody(route.request().url()) }).catch(() => {}); };
    await page.getByLabel('글 유형', { exact: true }).selectOption('GENERAL'); await page.getByRole('button', { name: '적용', exact: true }).click(); await waitFor(() => held);
    await page.goBack(); await listReady(page); release(); await delay(100); assert.match(await page.locator('.community-applied').innerText(), /경험/); state.list = null;
  });
  await check('긴 목록 하단 다음 페이지·키보드 초점·목록 간 Back/Forward 위치',async()=>{
    await page.goto(base+'/community/posts');await listReady(page);
    const next=page.getByRole('button',{name:'다음 페이지',exact:true});await next.scrollIntoViewIfNeeded();const originalY=await page.evaluate(()=>scrollY);
    await next.focus();await page.keyboard.press('Enter');await page.waitForURL('**page=1');await listReady(page);
    await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el===document.activeElement && el.getBoundingClientRect().top<200));
    await page.evaluate(()=>scrollTo(0,600));await delay(100);await page.goBack();await listReady(page);await waitFor(async()=>Math.abs(await page.evaluate(()=>scrollY)-originalY)<6);
    await page.goForward();await listReady(page);await waitFor(async()=>Math.abs(await page.evaluate(()=>scrollY)-600)<6);
  });
  await check('페이지 조회 실패는 이전 결과를 유지하고 목록 시작으로 이동/초점 강제 이동 안 함',async()=>{
    await page.goto(base+'/community/posts');await listReady(page);
    const previous=await posts(page).first().innerText();state.list=route=>route.fulfill({status:500,json:failure('SERVER_ERROR')});
    const next=page.getByRole('button',{name:'다음 페이지',exact:true});await next.scrollIntoViewIfNeeded();await next.focus();await page.keyboard.press('Enter');await page.getByRole('main').getByRole('alert').waitFor();
    assert.equal(await posts(page).first().innerText(),previous);assert((await page.evaluate(()=>scrollY))>500);assert.equal(await page.locator('#community-results-heading').evaluate(el=>el===document.activeElement),false);
    state.list=null;await page.getByRole('button',{name:'다시 시도',exact:true}).click();await listReady(page);await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el!==document.activeElement && el.getBoundingClientRect().top>=0 && el.getBoundingClientRect().top<200));
  });
  await check('390px 모바일 베스트 숨김·필터 취소·Escape·초점·Tab 가두기', async () => {
    await page.setViewportSize({ width: 390, height: 844 }); await page.goto(base + '/community/posts'); await listReady(page); await noOverflow(page); await shot(page, 'mock-list-390');
    assert.equal(await page.locator('.community-best').isVisible(),false);
    assert(await page.getByRole('link',{name:'글쓰기',exact:true}).isVisible());
    const calls = state.calls.list.length; const filter = page.getByRole('button', { name: '필터', exact: true }); await filter.click();
    const dialog = page.getByRole('dialog'); await dialog.getByLabel('글 유형', { exact: true }).selectOption('QUESTION');
    await dialog.getByRole('button', { name: '적용', exact: true }).focus(); await page.keyboard.press('Tab'); assert(await dialog.getByLabel('글 유형', { exact: true }).evaluate(el => el === document.activeElement));
    await page.keyboard.press('Escape'); assert(await filter.evaluate(el => el === document.activeElement)); assert.equal(state.calls.list.length, calls); assert.equal(new URL(page.url()).search, '');
    await filter.click(); assert.equal(await dialog.getByLabel('글 유형', { exact: true }).inputValue(), ''); await dialog.getByLabel('주제', { exact: true }).selectOption('DAILY'); await dialog.getByRole('button', { name: '취소', exact: true }).click(); assert.equal(state.calls.list.length, calls);
  });
  await check('모바일 필터 적용과 320px 목록/패널 가로 넘침 없음', async () => {
    await page.getByRole('button', { name: '필터', exact: true }).click(); const dialog = page.getByRole('dialog');
    await dialog.getByLabel('글 유형', { exact: true }).selectOption('REVIEW'); await dialog.getByRole('button', { name: '적용', exact: true }).click(); await page.waitForURL('**type=REVIEW'); await listReady(page); assert.equal(await dialog.count(), 0);
    await page.setViewportSize({ width: 320, height: 800 }); await noOverflow(page);assert.equal(await page.locator('.community-best').isVisible(),false);assert(await page.getByRole('link',{name:'글쓰기',exact:true}).isVisible()); await page.getByRole('button', { name: '필터', exact: true }).click(); await noOverflow(page); await shot(page, 'mock-filter-320'); await page.keyboard.press('Escape');
  });
  await check('모바일 키보드 필터 적용: 패널 닫기 후 최신 결과에 작은 읽기 초점',async()=>{
    await page.goto(base+'/community/posts');await listReady(page);
    const filter=page.getByRole('button',{name:'필터',exact:true});await filter.focus();await filter.press('Enter');
    const dialog=page.getByRole('dialog');await dialog.getByLabel('글 유형',{exact:true}).selectOption('QUESTION');
    const apply=dialog.getByRole('button',{name:'적용',exact:true});await apply.focus();await apply.press('Enter');await listReady(page);
    await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el===document.activeElement));
    assert.match(await page.locator('#community-results-heading').evaluate(el=>getComputedStyle(el).boxShadow),/-4px/);
    await page.keyboard.press('Tab');assert(await page.evaluate(()=>document.activeElement.matches('a,button')));
  });
  await check('목록 조회 중 다른 화면 이탈 승인: 늦은 결과가 남아 있는 목록의 초점/스크롤을 바꾸지 않음',async()=>{
    await page.setViewportSize({width:1440,height:1000});
    let releaseResult, releaseDestination, requested=false, destinationRequested=false;
    const resultGate=new Promise(resolve=>{releaseResult=resolve;});
    const destinationGate=new Promise(resolve=>{releaseDestination=resolve;});
    const match=url=>url.origin===base&&url.pathname==='/about';
    const hold=async route=>{if(route.request().headers().rsc==='1'){destinationRequested=true;await destinationGate;}await route.continue();};
    await context.route(match,hold);
    await page.goto(base+'/community/posts');await listReady(page);
    state.list=async route=>{requested=true;await resultGate;return route.fulfill({json:listBody(route.request().url())});};
    const next=page.getByRole('button',{name:'다음 페이지',exact:true});await next.focus();await next.press('Enter');await waitFor(()=>requested);
    await page.locator('#iyum-navigation a[href="/about"]').click();await waitFor(()=>destinationRequested);
    assert(await page.locator('#community-results-heading').isVisible());const y=await page.evaluate(()=>scrollY);
    releaseResult();await listReady(page);await delay(100);
    assert(await page.locator('#iyum-navigation a[href="/about"]').evaluate(el=>el===document.activeElement));assert.equal(await page.evaluate(()=>scrollY),y);
    releaseDestination();await page.waitForURL(base+'/about');state.list=null;await context.unroute(match,hold);
  });
  await check('공개 상세의 줄바꿈·HTML 비실행·긴 문자열·직접 진입 기본 복귀', async () => {
    await page.goto(base + '/community/posts/1'); await page.locator('.community-post-body').waitFor();
    assert.match(await page.locator('.community-post-body').innerText(), /첫 줄\n\n둘째 줄/); assert.equal(await page.evaluate(() => window.communityXss), undefined); assert.equal(await page.locator('.community-post-body script').count(), 0);
    assert.equal(await page.getByRole('link', { name: '← 목록으로', exact: true }).getAttribute('href'), '/community/posts');
    for (const width of [320, 390, 1440]) { await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); await noOverflow(page); if (width !== 320) await shot(page, `mock-detail-${width}`); }
  });
  for (const [name, handler, expected] of [
    ['404', route => route.fulfill({ status: 404, json: failure('POST_NOT_FOUND') }), '게시글을 찾을 수 없어요.'],
    ['손상 404', route => route.fulfill({ status: 404, contentType: 'text/html', body:'proxy missing' }), '잠시 불러오지 못했어요.'],
    ['500', route => route.fulfill({ status: 500, json: failure('SERVER_ERROR') }), '잠시 불러오지 못했어요.'],
    ['네트워크', route => route.abort('failed'), '잠시 불러오지 못했어요.'],
    ['HTML', route => route.fulfill({ status: 200, contentType: 'text/html', body: '<h1>로그인</h1>' }), '잠시 불러오지 못했어요.'],
  ]) await check(`상세 ${name} 오류 및 모의 본문 대체 없음`, async () => {
    state.detail = handler; const response=await page.goto(base + '/community/posts/1'); assert.equal(response.status(),name==='404'?404:200); await page.getByRole('heading', { name: expected }).waitFor(); assert.equal(await page.locator('.community-post-body').count(), 0);
  });
  await check('서버 타임아웃은 404가 아닌 재시도 가능한 오류',async()=>{
    state.detail=async()=>new Promise(()=>{});const response=await page.goto(base+'/community/posts/1');assert.equal(response.status(),200);
    await page.getByRole('heading',{name:'잠시 불러오지 못했어요.'}).waitFor();state.detail=null;await page.getByRole('button',{name:'다시 시도',exact:true}).click();await page.locator('.community-post-body').waitFor();
  });
  state.detail = null;
  await check('세션 확인 중 비로그인으로 오인하지 않음·로그인 후 작성 복귀', async () => {
    let release; const gate = new Promise(resolve => { release = resolve; }); state.me = async route => { await gate; return route.fulfill({ status: 401, json: failure('AUTHENTICATION_REQUIRED') }); };
    await page.goto(base + '/community/posts/create?type=QUESTION'); await page.getByText('로그인 상태를 확인하고 있습니다.', { exact: true }).waitFor(); assert.equal(await page.getByRole('link', { name: '로그인하고 글쓰기' }).count(), 0);
    release(); state.me = null; await page.getByRole('link', { name: '로그인하고 글쓰기' }).click(); await page.waitForURL('**/login?**');
    await page.locator('input[name=email]').fill(member.email); await page.locator('input[name=password]').fill('example-only'); await page.getByRole('main').getByRole('button', { name: '로그인', exact: true }).click();
    await page.waitForURL('**/community/posts/create?type=QUESTION'); await form(page).waitFor(); assert.equal(await page.getByLabel('글 유형', { exact: true }).inputValue(), 'QUESTION');
  });
  await check('작성 필드 오류와 초점·연결·Unicode 길이 검증', async () => {
    await register(page).click(); assert.equal(await page.getByLabel('주제', { exact: true }).getAttribute('aria-invalid'), 'true'); assert(await page.getByLabel('주제', { exact: true }).evaluate(el => el === document.activeElement));
    await fillPost(page); await title(page).fill('😀'.repeat(121)); await register(page).click(); assert.match(await page.locator('#post-title-error').innerText(), /120자/); assert(await title(page).evaluate(el => el === document.activeElement)); assert.equal(state.calls.post.length, 0);
    await title(page).fill('[검증 예시] 함께 나누고 싶은 경험');
    for (const width of [1440, 390, 320]) { await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); await noOverflow(page); }
  });
  for (const [name, status, code, fields, expected] of [
    ['입력 오류', 400, 'VALIDATION_FAILED', [{ field: 'title', message: '제목은 120자 이하여야 합니다.' }], '입력값'],
    ['CSRF 오류', 403, 'CSRF_TOKEN_INVALID', [], '보안 확인'],
    ['권한 부족', 403, 'POST_WRITE_NOT_ALLOWED', [], '계정 상태'],
    ['서버 오류', 500, 'SERVER_ERROR', [], '이미 저장'],
  ]) await check(`작성 ${name}: 자동 재전송 없음·입력 및 dirty 보존`, async () => {
    state.post = route => route.fulfill({ status, json: failure(code, fields) }); const count = state.calls.post.length; await register(page).click(); await form(page).getByRole('alert').waitFor();
    assert.match(await form(page).getByRole('alert').innerText(), new RegExp(expected)); await delay(100); assert.equal(state.calls.post.length, count + 1); assert.equal(await content(page).inputValue(), '  첫 줄\n\n둘째 줄  ');
    await form(page).getByRole('button', { name: '취소', exact: true }).click(); await page.getByRole('dialog').waitFor(); await page.getByRole('button', { name: '계속 작성', exact: true }).click();
  });
  await check('작성 응답 손상 및 네트워크 장애: 불확실 안내·새 탭 확인·입력 보존', async () => {
    for (const handler of [route => route.fulfill({ status: 201, json: {} }), route => route.abort('failed')]) {
      state.post = handler; const count = state.calls.post.length; await register(page).click(); await form(page).getByRole('link', { name: '새 탭에서 목록 확인' }).waitFor(); assert.equal(state.calls.post.length, count + 1); assert.equal(await title(page).inputValue(), '[검증 예시] 함께 나누고 싶은 경험');
    }
  });
  await check('세션 만료 401: 로그인 상태 갱신·작성 내용 보존', async () => {
    state.post = route => { state.authenticated = false; return route.fulfill({ status: 401, json: failure('AUTHENTICATION_REQUIRED') }); };
    await register(page).click(); await page.getByRole('link', { name: '로그인하고 글쓰기' }).waitFor(); assert.equal(await content(page).inputValue(), '  첫 줄\n\n둘째 줄  '); assert(await register(page).isDisabled());
    await form(page).getByRole('button', { name: '취소', exact: true }).click(); await page.getByRole('dialog').waitFor(); await page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true }).click(); await page.waitForURL('**/community/posts');
  });
  await check('유효한 201 ID만 상세 이동·중복 등록 방지·성공 후 이탈 확인 해제', async () => {
    state.authenticated = true; await page.goto(base + '/community/posts/create'); await fillPost(page);
    for (const width of [1440, 390]) { await page.setViewportSize({ width, height: width < 600 ? 844 : 1000 }); await shot(page, `mock-create-${width}`); }
    let release; const gate = new Promise(resolve => { release = resolve; }); state.post = async route => { await gate; return route.fulfill({ status: 201, json: { id: 1 } }); };
    const count = state.calls.post.length; await register(page).click(); await waitFor(() => state.calls.post.length === count + 1);
    await form(page).evaluate(el => { el.requestSubmit(); el.requestSubmit(); }); assert.equal(state.calls.post.length, count + 1); assert(await page.getByRole('button', { name: '등록 중…' }).isDisabled());
    const request = state.calls.post.at(-1); assert.deepEqual(request.body, { type: 'QUESTION', topic: 'HEARING_AID', title: '[검증 예시] 함께 나누고 싶은 경험', content: '  첫 줄\n\n둘째 줄  ' }); assert.ok(request.headers['x-csrf-token']);
    release(); await page.waitForURL('**/community/posts/1?**'); await page.locator('.community-post-body').waitFor(); assert.equal(await page.getByRole('dialog').count(), 0);
  });
  await check('결과 확인은 제출 당시 유형·주제 첫 페이지, 일반 복귀는 이전 조건·뒷페이지', async () => {
    const back = '/community/posts?type=GENERAL&topic=DAILY&page=2';
    await page.goto(base + '/community/posts/create?from=' + encodeURIComponent(back)); await fillPost(page);
    state.post = route => route.fulfill({ status: 201, json: {} });
    await register(page).click(); const verify = page.getByRole('link', { name: '새 탭에서 목록 확인' }); await verify.waitFor();
    const sent = '/community/posts?type=QUESTION&topic=HEARING_AID';
    assert.equal(await verify.getAttribute('href'), sent); assert.equal(await verify.getAttribute('target'), '_blank');
    assert.equal(await page.getByRole('link', { name: '← 목록으로', exact: true }).getAttribute('href'), back);
    await page.getByLabel('글 유형', { exact: true }).selectOption('REVIEW'); await page.getByLabel('주제', { exact: true }).selectOption('EMPLOYMENT');
    assert.equal(await verify.getAttribute('href'), sent);
    const popupPromise = page.waitForEvent('popup'); await verify.click(); const popup = await popupPromise; await popup.waitForLoadState();
    assert.equal(new URL(popup.url()).pathname + new URL(popup.url()).search, sent);
    assert.equal(await title(page).inputValue(), '  [검증 예시] 함께 나누고 싶은 경험  '); assert.equal(await form(page).getByRole('alert').count(), 1);
    assert(state.calls.list.some(url => { const q = new URL(url).searchParams; return q.get('type') === 'QUESTION' && q.get('topic') === 'HEARING_AID' && q.get('page') === '0'; }));
    await popup.close(); await register(page).click(); await verify.waitFor();
    assert.equal(await verify.getAttribute('href'), '/community/posts?type=REVIEW&topic=EMPLOYMENT');
    assert.equal(await page.getByRole('link', { name: '← 목록으로', exact: true }).getAttribute('href'), back);
  });
  // 목적지 GET까지 막는다. 이탈 승인 후에도 이전 form DOM이 남아 있는 창을 반드시 검사한다.
  for (const action of ['취소', '목록 링크', '헤더 링크']) for (const status of [201, 401, 500]) {
    await check(`${action} 이탈 승인 후 목적지 지연 중 늦은 ${status}: 기존 폼 마운트·선택한 이동 유지`, async () => {
      state.authenticated = true; state.post = null; state.list = null;
      const back = '/community/posts?type=GENERAL&page=1';
      await page.setViewportSize({ width: 1440, height: 1000 });
      await page.goto(base + '/community/posts/create?from=' + encodeURIComponent(back)); await fillPost(page);
      let releasePost, releaseList, destinationStarted = false;
      const postGate = new Promise(resolve => { releasePost = resolve; });
      const listGate = new Promise(resolve => { releaseList = resolve; });
      state.post = async route => { await postGate; return route.fulfill(status === 201 ? { status, json: { id: 1 } } : { status, json: failure(status === 401 ? 'AUTHENTICATION_REQUIRED' : 'SERVER_ERROR') }); };
      state.list = async route => { destinationStarted = true; await listGate; return route.fulfill({ json: listBody(route.request().url()) }); };
      const count = state.calls.post.length; const detailCount = state.calls.detail.length;
      await register(page).click(); await waitFor(() => state.calls.post.length === count + 1);
      if (action === '취소') await form(page).getByRole('button', { name: '취소', exact: true }).click();
      else if (action === '목록 링크') await page.getByRole('link', { name: '← 목록으로', exact: true }).click();
      else await page.getByRole('navigation', { name: '주 메뉴', exact: true }).getByRole('link', { name: '커뮤니티', exact: true }).click();
      await page.getByRole('button', { name: '나가기 — 입력한 내용이 사라집니다', exact: true }).click();
      await waitFor(() => destinationStarted); assert(await form(page).isVisible());
      assert.equal(new URL(page.url()).pathname, '/community/posts/create');
      const activeBefore = await page.evaluate(() => document.activeElement?.outerHTML);
      releasePost(); await waitFor(async () => !(await register(page).isDisabled())); await delay(150);
      assert(await form(page).isVisible()); assert.equal(await form(page).getByRole('alert').count(), 0);
      assert.equal(await page.evaluate(() => document.activeElement?.outerHTML), activeBefore);
      assert.equal(state.calls.detail.length, detailCount); assert.equal(state.calls.post.length, count + 1);
      releaseList(); await listReady(page); await page.waitForURL(base + (action === '헤더 링크' ? '/community' : back));
      state.list = null; state.post = null;
    });
  }
  for (const approve of [false, true]) await check(`브라우저 뒤로 가기 ${approve ? '이탈 승인' : '계속 작성'} 후 늦은 등록 결과`, async () => {
    state.authenticated = true; state.post = null;
    await page.goto(base + '/community/posts?type=QUESTION'); await listReady(page);
    await page.getByRole('link', { name: /글쓰기/ }).click(); await fillPost(page);
    let release; const gate = new Promise(resolve => { release = resolve; });
    state.post = async route => { await gate; return route.fulfill({ status: 201, json: { id: 1 } }); };
    const count = state.calls.post.length; await register(page).click(); await waitFor(() => state.calls.post.length === count + 1);
    await page.evaluate(() => history.back()); await page.getByRole('dialog').waitFor();
    await page.getByRole('button', { name: approve ? '나가기 — 입력한 내용이 사라집니다' : '계속 작성', exact: true }).click();
    if (approve) await page.waitForURL(base + '/community/posts?type=QUESTION');
    release();
    if (approve) { await delay(300); assert.equal(new URL(page.url()).pathname + new URL(page.url()).search, '/community/posts?type=QUESTION'); }
    else { await page.waitForURL('**/community/posts/1?**'); await page.locator('.community-post-body').waitFor(); }
    assert.equal(state.calls.post.length, count + 1); state.post = null;
  });
  await check('취소 확인창에서 계속 작성하면 지연 등록 성공이 정상 상세 이동', async () => {
    await page.goto(base + '/community/posts/create'); await fillPost(page);
    let release; const gate = new Promise(resolve => { release = resolve; }); state.post = async route => { await gate; return route.fulfill({ status: 201, json: { id: 1 } }); };
    const count = state.calls.post.length; await register(page).click(); await waitFor(() => state.calls.post.length === count + 1);
    await form(page).getByRole('button', { name: '취소', exact: true }).click(); await page.getByRole('button', { name: '계속 작성', exact: true }).click();
    release(); await page.waitForURL('**/community/posts/1?**'); await page.locator('.community-post-body').waitFor(); state.post = null;
  });
  await check('홈·검색 예시 게시글에서 가짜 ID 상세로 연결하지 않음', async () => {
    for (const path of ['/', '/search?q=보청기']) { await page.goto(base + path); assert.equal(await page.locator('main a[href]').evaluateAll(links => links.filter(a => /^\/community\/posts\/\d/.test(a.getAttribute('href'))).length), 0); }
  });
  await context.close();
  await check('브라우저 런타임 오류 없음', async () => assert.deepEqual(errors, []));
  await fs.writeFile(path.join(output, 'mock-results.json'), JSON.stringify({ mode: 'server public GET + browser API mocks, not real DB verification', checks, errors }, null, 2));
  console.log(`${checks.length} community browser checks passed`);
} catch (error) { console.error('FAILED', currentCheck, error.message); process.exitCode = 1; }
finally { await browser.close(); if(ssrServer){ssrServer.closeAllConnections();await new Promise(resolve=>ssrServer.close(resolve));} }
