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
const topics = ['HEARING_AID', 'COCHLEAR_IMPLANT', 'DAILY', 'EMPLOYMENT', 'COMMUNICATION', 'TREATMENT_REHABILITATION', 'WELFARE_LIFE'];
const titles = ['처음 보청기를 맞출 때 무엇부터 확인하면 좋을까요?', '인공와우 적응 중, 일상에서 도움이 된 작은 습관', '오늘 나누고 싶은 소소한 일상 이야기', '직장에서 소통할 때 어떤 방법을 사용하시나요?', '가족과 함께 자막 앱을 사용해 본 경험', '집에서 꾸준히 할 수 있는 청취 연습이 궁금해요'];
const items = Array.from({ length: 441 }, (_, index) => ({ id: index + 1, type: types[index % 3], topic: topics[index % 7],
  title: `[검증 예시] ${titles[index % 6]} ${index + 1}`, author: { memberId: 123, nickname: '예시회원' }, createdAt: '2026-10-14T01:00:00Z' }));
function listBody(url) {
  const q = new URL(url).searchParams, page = Number(q.get('page') ?? 0), size = Number(q.get('size') ?? 20);
  const filtered = items.filter(item => (!q.get('type') || item.type === q.get('type')) && (!q.get('topic') || item.topic === q.get('topic')));
  return { items: filtered.slice(page * size, (page + 1) * size).map(item => ({ ...item, commentCount: item.id % 5 })), page, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size), hasNext: (page + 1) * size < filtered.length };
}
async function mock(options = {}) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', reducedMotion: 'reduce' });
  const page = await context.newPage(); page.setDefaultTimeout(30000); page.on('pageerror', error => errors.push(error.message));
  const state = { authenticated: !!options.authenticated, list: null, detail: null, post: null, me: null, csrf: null, calls: { list: [], detail: [], post: [], csrf: 0, login: 0 } };
  await page.route(url=>url.origin===new URL(api).origin && /^\/api\/v1\/auth\/[^/]+$/.test(url.pathname), async route => {
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
  await page.route(url=>url.origin===new URL(api).origin && /^\/api\/v1\/community\/posts(?:\/[^/]+)?$/.test(url.pathname), handlePost);
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
async function listReady(page) {
  await page.waitForFunction(() => document.querySelector('.community-list-column [aria-busy]')?.getAttribute('aria-busy') === 'false');
  for (const row of await posts(page).all()) {
    const id = Number(new URL(await row.locator('a').getAttribute('href'), base).pathname.split('/').at(-1));
    assert.equal(await row.locator('.community-comment-count').innerText(), `댓글 ${id % 5}`);
  }
}
async function noOverflow(page) { assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)); }
async function shot(page, name) { await page.evaluate(() => document.fonts.ready); await page.screenshot({ path: path.join(output, name + '.png'), fullPage: true }); await page.evaluate(()=>scrollTo(0,0)); await page.screenshot({path:path.join(output,name+'-viewport.png')}); }
async function fillPost(page) {
  await form(page).waitFor();
  await page.getByLabel('글 유형', { exact: true }).selectOption('QUESTION'); await page.getByLabel('주제', { exact: true }).selectOption('HEARING_AID');
  await title(page).fill('  [검증 예시] 함께 나누고 싶은 경험  '); await content(page).fill('  첫 줄\n\n둘째 줄  ');
}
const spaces=[['hearing-aid','HEARING_AID','보청기'],['cochlear-implant','COCHLEAR_IMPLANT','인공와우'],['daily','DAILY','자유·일상'],['treatment-rehabilitation','TREATMENT_REHABILITATION','치료·재활'],['communication','COMMUNICATION','의사소통'],['employment','EMPLOYMENT','취업·직장'],['welfare-life','WELFARE_LIFE','복지·생활']];
const topicURL='/community/topics/hearing-aid';
async function apply(page,type){await page.getByLabel('글 유형',{exact:true}).selectOption(type);await page.getByRole('button',{name:'적용',exact:true}).click();}
try {
 const {page,context,state}=await mock();
 for(const [slug,topic,name] of spaces) await check(`직접 진입·새로고침·무JS 초기 HTML ${slug}`,async()=>{
  const isolated=await browser.newContext({javaScriptEnabled:false});const nojs=await isolated.newPage();const response=await nojs.goto(base+'/community/topics/'+slug+'?type=QUESTION');
  assert.equal(response.status(),200);assert.equal(await nojs.locator('main h1').innerText(),name+' 커뮤니티');assert(await nojs.locator('.topic-guidance').isVisible());assert(await posts(nojs).count()>0);assert.match(await nojs.title(),new RegExp(name));
  assert(state.serverCalls.some(c=>c.url.includes('topic='+topic)&&c.url.includes('type=QUESTION')));await isolated.close();
  await page.goto(base+'/community/topics/'+slug);await listReady(page);await page.reload();await listReady(page);assert.equal(await page.getByLabel('주제',{exact:true}).count(),0);assert(await page.locator('.topic-hero img').evaluate(el=>el.complete&&el.naturalWidth>0));assert.notEqual(await page.evaluate(()=>document.activeElement?.tagName),'H1');
 });
 await check('잘못된 slug HTTP404·무JS 표시 본문/링크/복귀·JS 새로고침',async()=>{
  const isolated=await browser.newContext({javaScriptEnabled:false});const nojs=await isolated.newPage();
  const response=await nojs.goto(base+'/community/topics/unknown');assert.equal(response.status(),404);
  assert(await nojs.getByRole('heading',{name:'주제 공간을 찾을 수 없어요.'}).isVisible());
  assert(await nojs.getByText('주소를 확인하거나 전체 커뮤니티에서 주제를 선택해 주세요.',{exact:true}).isVisible());
  const link=nojs.getByRole('link',{name:'전체 커뮤니티로',exact:true});assert(await link.isVisible());assert.equal(await link.getAttribute('href'),'/community');
  await link.click();await nojs.waitForURL(url=>url.pathname==='/community'&&url.search==='');assert(await nojs.getByRole('heading',{name:'전체 게시글',exact:true}).isVisible());assert(await posts(nojs).count()>0);
  await fs.writeFile(path.join(output,'topic-404-nojs.json'),JSON.stringify({httpStatus:404,visibleGuidance:true,visibleLink:true,returnToCommunity:true,result:'PASS'},null,2));await isolated.close();
  const jsResponse=await page.goto(base+'/community/topics/unknown');assert.equal(jsResponse.status(),404);assert.equal((await page.reload()).status(),404);await page.getByRole('heading',{name:'주제 공간을 찾을 수 없어요.'}).waitFor();await page.getByRole('link',{name:'전체 커뮤니티로'}).click();await listReady(page);assert.equal(new URL(page.url()).pathname,'/community');
 });
 await check('충돌 topic 정규화·유형/페이지 유지·기존 쿼리 호환',async()=>{
  await page.goto(base+topicURL+'?topic=DAILY&type=QUESTION&page=1');await listReady(page);assert.equal(new URL(page.url()).search,'?type=QUESTION&page=1');assert.equal(await posts(page).count(),1);
  await page.goto(base+'/community?topic=COCHLEAR_IMPLANT&type=REVIEW');await listReady(page);assert.equal(await page.getByLabel('주제',{exact:true}).inputValue(),'COCHLEAR_IMPLANT');assert.equal(await page.getByRole('navigation',{name:'주제별 커뮤니티'}).getByRole('link').count(),8);
 });
 await check('hydration 중복 GET 없음·실제 링크로 다른 주제 열기',async()=>{
  let calls=0;const listener=req=>{if(req.url().includes('/api/v1/community/posts'))calls++;};page.on('request',listener);await page.goto(base+topicURL);await listReady(page);await delay(200);assert.equal(calls,0);page.off('request',listener);
  const href=await page.getByRole('navigation',{name:'주제별 커뮤니티'}).getByRole('link',{name:'인공와우'}).getAttribute('href');const tab=await context.newPage();await tab.goto(base+href);assert.equal(await tab.locator('main h1').innerText(),'인공와우 커뮤니티');await tab.close();
 });
 await check('빠른 유형 변경·늦은 응답 무효화·주제 AND 유형',async()=>{
  await page.goto(base+topicURL);await listReady(page);let release,held=false;const gate=new Promise(r=>release=r);
  state.list=async route=>{const u=new URL(route.request().url());if(u.searchParams.get('type')==='QUESTION'){held=true;await gate;}return route.fulfill({json:listBody(u)});};
  await apply(page,'QUESTION');await waitFor(()=>held);await apply(page,'REVIEW');await listReady(page);const before=await posts(page).allTextContents();release();await delay(150);assert.deepEqual(await posts(page).allTextContents(),before);assert(state.calls.list.slice(-2).every(u=>new URL(u).searchParams.get('topic')==='HEARING_AID'));state.list=null;
 });
 await check('주제 전환 뒤 이전 주제의 지연 조회가 새 목록을 덮어쓰지 않음',async()=>{
  await page.goto(base+topicURL);await listReady(page);let release,held=false;const gate=new Promise(r=>release=r);
  state.list=async route=>{const u=new URL(route.request().url());if(u.searchParams.get('topic')==='HEARING_AID'&&u.searchParams.get('type')==='QUESTION'){held=true;await gate;}return route.fulfill({json:listBody(u)});};
  await apply(page,'QUESTION');await waitFor(()=>held);await page.getByRole('navigation',{name:'주제별 커뮤니티'}).getByRole('link',{name:'인공와우'}).click();await page.getByRole('heading',{name:'인공와우 커뮤니티',exact:true}).waitFor();await listReady(page);const before=await posts(page).allTextContents();release();await delay(150);assert.deepEqual(await posts(page).allTextContents(),before);assert.equal(new URL(page.url()).pathname,'/community/topics/cochlear-implant');state.list=null;
  await page.goto(base+topicURL);await listReady(page);
 });
 await check('조회 오류 이전 결과·재시도·빈 상태·범위 밖',async()=>{
  const before=await posts(page).allTextContents();state.list=route=>route.fulfill({status:500,json:failure('SERVER_ERROR')});await apply(page,'GENERAL');await page.getByRole('main').getByRole('alert').waitFor();assert.deepEqual(await posts(page).allTextContents(),before);
  state.list=null;await page.getByRole('button',{name:'다시 시도',exact:true}).click();await listReady(page);
  state.list=route=>route.fulfill({json:{...listBody(route.request().url()),items:[],totalElements:0,totalPages:0,hasNext:false}});await apply(page,'QUESTION');await listReady(page);assert(await page.getByRole('link',{name:'이 주제로 글쓰기'}).isVisible());state.list=null;
  await page.goto(base+topicURL+'?page=99');await listReady(page);await page.getByRole('button',{name:'같은 조건의 첫 페이지로'}).click();await listReady(page);assert.equal(new URL(page.url()).pathname,topicURL);
 });
 await check('페이지 변경 최소 이동·상세 왕복·뒤로 앞으로 위치 복원',async()=>{
  await page.getByRole('button',{name:'다음 페이지',exact:true}).click();await listReady(page);await waitFor(async()=>Math.abs((await page.locator('#community-results-heading').boundingBox()).y)<150);assert.notEqual(await page.evaluate(()=>document.activeElement?.id),'community-results-heading');
  const post=posts(page).nth(10).getByRole('link');await post.scrollIntoViewIfNeeded();const y=await page.evaluate(()=>scrollY);await post.click();await page.locator('.community-post-body').waitFor();assert.equal(await page.getByRole('link',{name:'← 목록으로',exact:true}).getAttribute('href'),topicURL+'?page=1');await page.getByRole('link',{name:'← 목록으로',exact:true}).click();await listReady(page);await waitFor(async()=>Math.abs(await page.evaluate(()=>scrollY)-y)<5);
  await page.goBack();await page.locator('.community-post-body').waitFor();await page.goForward();await listReady(page);await waitFor(async()=>Math.abs(await page.evaluate(()=>scrollY)-y)<5);
 });
 await check('키보드 페이지 변경·본문 바로가기',async()=>{
  const next=page.getByRole('button',{name:'다음 페이지',exact:true});await next.focus();await next.press('Enter');await listReady(page);await waitFor(()=>page.locator('#community-results-heading').evaluate(el=>el===document.activeElement));
  await page.goto(base+topicURL);await page.keyboard.press('Tab');await page.getByRole('link',{name:'본문 바로가기'}).press('Enter');assert(await page.locator('#main-content-start').evaluate(el=>el===document.activeElement));
 });
 await check('비로그인 → 로그인 → 주제·유형 기본값과 취소 복귀',async()=>{
  await page.goto(base+topicURL+'?type=QUESTION&page=1');await listReady(page);await page.getByRole('link',{name:'＋ 글쓰기',exact:true}).click();await page.getByRole('link',{name:'로그인하고 글쓰기'}).click();await page.locator('input[name=email]').fill(member.email);await page.locator('input[name=password]').fill('example-only');await page.getByRole('main').getByRole('button',{name:'로그인',exact:true}).click();await form(page).waitFor();
  assert.equal(await page.getByLabel('주제',{exact:true}).inputValue(),'HEARING_AID');assert.equal(await page.getByLabel('글 유형',{exact:true}).inputValue(),'QUESTION');assert.equal(await page.getByRole('link',{name:'← 목록으로',exact:true}).getAttribute('href'),topicURL+'?type=QUESTION&page=1');await form(page).getByRole('button',{name:'취소',exact:true}).click();await listReady(page);assert.equal(new URL(page.url()).search,'?type=QUESTION&page=1');
 });
 await check('주제 변경·이탈 취소·등록 성공 상세의 원래 목록 보존',async()=>{
  await page.getByRole('link',{name:'＋ 글쓰기',exact:true}).click();await form(page).waitFor();await page.getByLabel('주제',{exact:true}).selectOption('EMPLOYMENT');await title(page).fill('[검증 예시] 주제 변경');await content(page).fill('모의 요청 본문');await page.getByRole('link',{name:'← 목록으로',exact:true}).click();await page.getByRole('button',{name:'계속 작성',exact:true}).click();assert.equal(await title(page).inputValue(),'[검증 예시] 주제 변경');await register(page).click();await page.locator('.community-post-body').waitFor();assert.equal(state.calls.post.at(-1).body.topic,'EMPLOYMENT');assert.equal(await page.getByRole('link',{name:'← 목록으로',exact:true}).getAttribute('href'),topicURL+'?type=QUESTION&page=1');
 });
 await check('불확실 결과 확인은 전송 당시 주제 첫 페이지·일반 복귀 분리',async()=>{
  const back=topicURL+'?type=GENERAL&page=2';await page.goto(base+'/community/posts/create?from='+encodeURIComponent(back));await fillPost(page);await page.getByLabel('주제',{exact:true}).selectOption('COCHLEAR_IMPLANT');state.post=route=>route.fulfill({status:500,json:failure('SERVER_ERROR')});await register(page).click();const link=page.getByRole('link',{name:'새 탭에서 목록 확인'});await link.waitFor();assert.equal(await link.getAttribute('href'),'/community/topics/cochlear-implant?type=QUESTION');assert.equal(await link.getAttribute('target'),'_blank');await page.getByLabel('주제',{exact:true}).selectOption('DAILY');assert.equal(await link.getAttribute('href'),'/community/topics/cochlear-implant?type=QUESTION');assert.equal(await page.getByRole('link',{name:'← 목록으로',exact:true}).getAttribute('href'),back);state.post=null;
 });
 for(const width of [1440,390,320]) await check(`배치·필터·대표 이미지 ${width}px`,async()=>{
  await page.setViewportSize({width,height:900});await page.goto(base+topicURL);await listReady(page);await noOverflow(page);
  if(width<600){await page.getByRole('button',{name:'필터',exact:false}).click();const dialog=page.getByRole('dialog');assert.equal(await dialog.getByLabel('주제',{exact:true}).count(),0);await dialog.getByLabel('글 유형',{exact:true}).selectOption('REVIEW');await dialog.getByRole('button',{name:'적용',exact:true}).click();await listReady(page);}
  await shot(page,`topics-hearing-aid-${width}`);await page.goto(base+'/community');await listReady(page);await noOverflow(page);await shot(page,`topics-entrance-${width}`);
 });
 await check('폭 변경 후 필터 접근·선택/적용 조건 보존',async()=>{
  await page.goto(base+topicURL+'?type=REVIEW');await listReady(page);await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'필터',exact:false}).click();const dialog=page.getByRole('dialog');await dialog.getByLabel('글 유형',{exact:true}).selectOption('GENERAL');await page.setViewportSize({width:1440,height:1000});assert.equal(await dialog.getByLabel('글 유형',{exact:true}).inputValue(),'GENERAL');await dialog.getByRole('button',{name:'적용',exact:true}).click();await listReady(page);assert.equal(await page.getByLabel('글 유형',{exact:true}).inputValue(),'GENERAL');await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'필터',exact:false}).click();assert.equal(await page.getByRole('dialog').getByLabel('글 유형',{exact:true}).inputValue(),'GENERAL');await page.keyboard.press('Escape');
 });
 await context.close();await check('런타임 오류 없음',async()=>assert.deepEqual(errors,[]));await fs.writeFile(path.join(output,'topic-results.json'),JSON.stringify({mode:'SSR + browser API mocks; no DB writes',checks,errors},null,2));console.log(`${checks.length} topic browser checks passed`);
}catch(error){console.error('FAILED',currentCheck,error.stack);process.exitCode=1;}
finally{await browser.close();if(ssrServer){ssrServer.closeAllConnections();await new Promise(resolve=>ssrServer.close(resolve));}}
