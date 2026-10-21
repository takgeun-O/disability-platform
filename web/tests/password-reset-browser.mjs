// All API responses are mock. No SMTP or development DB writes. Do not record secrets/URLs/headers.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8457';
const out = process.env.RESET_ARTIFACT_DIR ?? '/private/tmp/iyum-reset-ui-browser';
await fs.mkdir(out, { recursive: true });
const token = 'A'.repeat(43), password = ' Test1234! ', csrf = 'mock-only-csrf';
const accepted = '입력하신 이메일이 재설정 가능한 계정이면 비밀번호 재설정 안내 메일을 보내드립니다.';
const member = { memberId: 99, email: 'review@example.test', nickname: '검토회원', role: 'USER' };
const error = (code, field) => ({ code, message: 'private details must not be displayed', fieldErrors: field ? [{ field, message: 'private field' }, { field, message: 'private field' }] : [] });
let state;
function fresh() { state = { status: 200, code: null, field: null, delay: null, received: null, posts: [], csrfCalls: 0, authenticated: false, csrfFail: false, network: false, sessionChecks: [], me: 0, validationStatus: 204, validationCalls: 0, validationDelay: null, validationArrived: null, validationPrivate: [] }; }
fresh();
const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, 'http://mock');
  const origin = req.headers.origin;
  if (origin === base) { res.setHeader('Access-Control-Allow-Origin', origin); res.setHeader('Access-Control-Allow-Credentials', 'true'); }
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Type', 'application/json');
  if (req.method === 'OPTIONS') { res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS'); res.setHeader('Access-Control-Allow-Headers', 'Content-Type,X-CSRF-TOKEN'); res.writeHead(204); res.end(); return; }
  const reply = (status, body) => { res.writeHead(status); res.end(body ? JSON.stringify(body) : undefined); };
  if (u.pathname.endsWith('/csrf')) {
    state.csrfCalls++;
    if (state.csrfFail) return reply(503, error('SERVER_ERROR'));
    res.setHeader('Set-Cookie', 'JSESSIONID=mock-reset-session; Path=/; HttpOnly; SameSite=Lax');
    return reply(200, { headerName: 'X-CSRF-TOKEN', token: csrf });
  }
  if (u.pathname.endsWith('/me')) { state.me++; return reply(state.authenticated ? 200 : 401, state.authenticated ? member : error('AUTHENTICATION_REQUIRED')); }
  if (u.pathname === '/api/v1/auth/password-reset/validate') {
    state.validationCalls++;
    state.validationPrivate.push({cookie:!!req.headers.cookie,authorization:!!req.headers.authorization,csrf:!!req.headers['x-csrf-token']});
    state.validationArrived?.();
    if(state.validationDelay) await state.validationDelay;
    return reply(state.validationStatus, state.validationStatus === 204 ? undefined : error('PASSWORD_RESET_TOKEN_INVALID'));
  }
  if (u.pathname.includes('/password-reset/')) {
    let body = ''; for await (const chunk of req) body += chunk;
    const data = JSON.parse(body);
    const kind = u.pathname.split('/').at(-1);
    const validSession = req.headers.cookie?.includes('JSESSIONID=mock-reset-session') && req.headers['x-csrf-token'] === csrf;
    state.sessionChecks.push(!!validSession);
    state.posts.push({ kind, data });
    state.received?.();
    if (state.delay) await state.delay;
    if (!validSession) return reply(403, error('CSRF_TOKEN_INVALID'));
    if (state.network) { res.destroy(); return; }
    if (state.code) return reply(state.status, error(state.code, state.field));
    if (kind === 'request') return reply(202, { message: accepted });
    state.authenticated = false; return reply(204);
  }
  return reply(404, error('NOT_FOUND'));
});
await new Promise(resolve => server.listen(8857, '127.0.0.1', resolve));
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const results = [], layouts = [];
async function setup(js = true, width = 1440) {
  const context = await browser.newContext({ javaScriptEnabled: js, viewport: { width, height: width === 1440 ? 1000 : 844 }, locale: 'ko-KR' });
  // Keep mock tests independent of external font CDN latency. The app CSS is unchanged.
  await context.route(u => ['fonts.googleapis.com', 'fonts.gstatic.com'].includes(u.hostname), route => route.abort());
  const page = await context.newPage(); page.setDefaultTimeout(10000);
  return { context, page };
}
async function check(name, fn) {
  if (process.env.CASE_FILTER && !name.includes(process.env.CASE_FILTER)) return;
  fresh();
  try { await fn(); results.push({ name, pass: true }); console.log('PASS', name); }
  catch (e) { results.push({ name, pass: false, error: e.name }); console.log('FAIL', name, e.name, e.message.replaceAll(token, '[REDACTED]').replaceAll(password, '[REDACTED]').replaceAll(csrf, '[REDACTED]')); process.exitCode = 1; }
  finally { state.validationRelease?.(); state.release?.(); for (const c of browser.contexts()) await c.close(); }
}
async function capture(page, name) {
  for (const width of [1440,390,320]) {
    await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
    await page.evaluate(async () => { await document.fonts.ready; await new Promise(requestAnimationFrame); });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await page.screenshot({ path: path.join(out, `${name}-${width}.png`), fullPage: true, style: 'input[name=newPassword],input[name=newPasswordConfirm]{color:transparent!important;caret-color:transparent!important}' });
    layouts.push({ name, width, overflow: false });
  }
}
const submit = p => p.getByRole('button', { name: '비밀번호 변경', exact: true });
async function fill(p, pw = password, confirm = pw) {
  await p.locator('[name=newPassword]').fill(pw); await p.locator('[name=newPasswordConfirm]').fill(confirm);
}
async function open(p) { await p.goto(base + '/reset-password?token=' + token, { waitUntil: 'domcontentloaded' }); await p.locator('#reset-password').waitFor(); }
function hold() { let release; state.delay = new Promise(r => { release = r; }); state.release = release; return release; }
try {
  await check('login link, email field single error, 202 generic notice, manual retry', async () => {
    const { page:p } = await setup(); await p.goto(base + '/login');
    await p.getByRole('link', { name:'비밀번호를 잊으셨나요?' }).click(); await p.waitForURL(u => u.pathname === '/forgot-password');
    await capture(p, 'request');
    await p.getByRole('button', { name:'재설정 메일 요청', exact:true }).click();
    assert.equal(await p.getByText('이메일을 입력해 주세요.', { exact:true }).count(),1);
    assert(await p.locator('#reset-email').evaluate(e => e === document.activeElement)); assert.equal(state.posts.length,0);
    await p.locator('#reset-email').fill('missing@example.test'); await p.getByRole('button', { name:'재설정 메일 요청', exact:true }).click();
    await p.getByText(accepted, {exact:true}).waitFor(); assert.deepEqual(state.sessionChecks,[true]); await capture(p,'accepted');
    await p.getByRole('link',{name:'이메일 다시 입력하기'}).click(); await p.locator('#reset-email').fill('review@example.test');
    await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click(); await p.getByText(accepted,{exact:true}).waitFor(); assert.equal(state.posts.length,2);
  });
  for (const [status,code] of [[429,'PASSWORD_RESET_RATE_LIMITED'],[403,'CSRF_TOKEN_INVALID'],[503,'SERVER_ERROR'],[400,'INVALID_REQUEST_BODY']]) await check(`request ${code}, safe common error and explicit recovery`,async()=>{
    const {page:p}=await setup();state.status=status;state.code=code;await p.goto(base+'/forgot-password');
    await p.locator('#reset-email').fill('review@example.test');await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();
    await p.locator('form [role=alert]').waitFor();assert.equal(state.posts.length,1);assert(!await p.getByText('private details must not be displayed').count());
    if(status===429)await capture(p,'rate-limit');state.code=null;await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();await p.getByText(accepted,{exact:true}).waitFor();assert.equal(state.posts.length,2);assert.equal(state.csrfCalls,2);
  });
  for(const js of [true,false])await check(`initial validation gate JS=${js}: no password before server result`,async()=>{
    const{page:p}=await setup(js);await p.goto(base+'/forgot-password');
    let release;state.validationDelay=new Promise(r=>{release=r;});state.validationRelease=release;
    let arrived;const request=new Promise(r=>{arrived=r;});state.validationArrived=arrived;
    // While the upstream is held, the server must not send an editable document.
    // Inspect the response boundary instead of asking a locator/CDP DOM command
    // to wait for that very navigation (which would deadlock this controlled gate).
    let documentReceived=false;
    const observe=response=>{if(response.request().isNavigationRequest() && new URL(response.url()).pathname==='/reset-password')documentReceived=true;};
    p.on('response',observe);
    const navigation=p.goto(base+'/reset-password?token='+token).then(()=>null,e=>e);
    await request;assert.equal(documentReceived,false);
    release();const failure=await navigation;if(failure)throw failure;
    p.off('response',observe);assert.equal(documentReceived,true);
    assert(await p.locator('#reset-password').isVisible());assert.equal(state.validationCalls,1);
  });
  for (const js of [true,false]) {
    await check(`preflight JS=${js}: used/expired/revoked/missing token rejected before inputs`,async()=>{
      const{page:p}=await setup(js);state.validationStatus=400;
      await p.goto(base+'/reset-password?token='+token);
      assert(await p.getByRole('heading',{name:'사용할 수 없는 재설정 링크입니다',exact:true}).isVisible());
      assert.equal(await p.locator('input[name=newPassword]').count(),0);
      assert.equal(await p.getByText('새로 사용할 비밀번호를 입력해 주세요.',{exact:true}).count(),0);
      assert.equal(state.posts.length,0);assert.equal(state.csrfCalls,0);assert.equal(state.validationCalls,1);
      assert.deepEqual(state.validationPrivate,[{cookie:false,authorization:false,csrf:false}]);
      if(js)await capture(p,'preflight-invalid');else await p.screenshot({path:path.join(out,'nojs-preflight-invalid.png'),fullPage:true});
      // Another newly issued token for this account must be checked independently.
      state.validationStatus=204;await p.goto(base+'/reset-password?token='+'B'.repeat(43));
      assert(await p.locator('#reset-password').isVisible());assert.equal(state.validationCalls,2);
    });
    await check(`preflight JS=${js}: server error visible, retry really rechecks, valid SSR form`,async()=>{
      const{page:p}=await setup(js);state.validationStatus=503;
      const response=await p.goto(base+'/reset-password?token='+token);
      assert.match(response.headers()['cache-control'],/no-store/);
      assert(await p.getByText('링크를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.',{exact:true}).isVisible());
      assert.equal(await p.locator('input[name=newPassword]').count(),0);
      assert.equal(state.validationCalls,1);
      if(js)await capture(p,'preflight-failure');else await p.screenshot({path:path.join(out,'nojs-preflight-failure.png'),fullPage:true});
      state.validationStatus=204;
      let release;state.validationDelay=new Promise(r=>{release=r;});state.validationRelease=release;
      let arrived;const request=new Promise(r=>{arrived=r;});state.validationArrived=arrived;
      const retry=p.getByRole('button',{name:'다시 확인',exact:true});
      await retry.focus();const click=retry.press('Enter',{noWaitAfter:true}).then(()=>null,e=>e);
      await request;
      if(js){assert(await p.getByText('재설정 링크를 확인하고 있습니다.',{exact:true}).isVisible());assert.equal(await p.locator('input[name=newPassword]').count(),0);}
      release();const clickFailure=await click;if(clickFailure)throw clickFailure;await p.locator('#reset-password').waitFor();assert(await p.locator('#reset-password').isVisible());
      assert.equal(state.validationCalls,2);assert.equal(state.csrfCalls,0);
      assert.equal(await p.getByText('8자 이상 · 영문과 숫자 포함',{exact:true}).count(),1);
      assert(!await p.getByText('UTF-8 최대 72바이트',{exact:true}).count());
      if(js)await capture(p,'preflight-valid');else await p.screenshot({path:path.join(out,'nojs-preflight-valid.png'),fullPage:true});
      state.validationStatus=400;await p.reload();assert(await p.getByRole('heading',{name:'사용할 수 없는 재설정 링크입니다',exact:true}).isVisible());assert.equal(state.validationCalls,3);
    });
  }
  await check('no-JS byte limit uses same friendly field error and preserves token',async()=>{
    const{page:p}=await setup(false);await open(p);
    const tooLong='가'.repeat(22)+'Ab1234 ';
    await fill(p,tooLong);await submit(p).click();
    assert(await p.locator('#reset-password-error').isVisible());
    assert.equal(await p.locator('#reset-password-error').textContent(),'비밀번호가 너무 깁니다. 글자 수를 줄여 주세요.');
    assert.equal(state.posts.length,0);assert.equal(state.csrfCalls,0);
    await fill(p,'가'.repeat(22)+'Ab1234');await submit(p).click();await p.waitForURL(u=>u.pathname==='/reset-password/complete');
    assert.equal(state.posts.length,1);
  });
  for(const status of [200,429])await check(`preflight unexpected/limited ${status} is never expired or editable`,async()=>{
    const{page:p}=await setup();state.validationStatus=status;await p.goto(base+'/reset-password?token='+token);
    assert(await p.getByRole('button',{name:'다시 확인',exact:true}).isVisible());assert.equal(await p.locator('input[name=newPassword]').count(),0);
    assert.equal(await p.getByRole('heading',{name:'사용할 수 없는 재설정 링크입니다',exact:true}).count(),0);
    if(status===429)assert(await p.getByText('확인 요청이 많습니다. 잠시 후 다시 시도해 주세요.',{exact:true}).isVisible());
  });
  await check('missing, malformed, repeated token; no completion call on entry or reload; privacy headers',async()=>{
    const {page:p}=await setup();for(const suffix of ['', '?token=bad', `?token=${token}&token=${token}`]){
      const response=await p.goto(base+'/reset-password'+suffix);assert.match(response.headers()['cache-control'],/no-store/);assert.equal(response.headers()['referrer-policy'],'no-referrer');
      await p.getByRole('link',{name:'새 재설정 메일 요청'}).waitFor();assert.equal(await p.locator('input[name=newPassword]').count(),0);
    }assert.equal(state.validationCalls,0);await capture(p,'invalid-link');await open(p);await p.reload();assert.equal(state.posts.length,0);assert.equal(state.csrfCalls,0);
  });
  await check('password validation, keyboard toggle, mismatch, raw whitespace and byte limit',async()=>{
    const {page:p}=await setup();await open(p);await capture(p,'password');
    await fill(p,'abc123');await submit(p).click();assert.equal(await p.locator('#reset-password-error').count(),1);assert(await p.locator('#reset-password').evaluate(e=>e===document.activeElement));
    await fill(p,'가'.repeat(23)+'Ab12');await submit(p).click();assert.equal(state.posts.length,0);
    await fill(p,password,password.trim());await submit(p).click();await p.locator('#reset-confirm-error').waitFor();assert.equal(state.posts.length,0);
    await p.locator('#reset-password').focus();await p.keyboard.press('Tab');await p.keyboard.press('Space');assert.equal(await p.locator('#reset-password').getAttribute('type'),'text');assert.equal(state.posts.length,0);
    await capture(p,'field-error');
  });
  for(const [code,field] of [['PASSWORD_RESET_PASSWORD_UNCHANGED','newPassword'],['PASSWORD_RESET_PASSWORD_INVALID','newPassword'],['VALIDATION_FAILED','newPasswordConfirm']])await check(`complete ${code}: single field error, value retained`,async()=>{
    const{page:p}=await setup();state.status=400;state.code=code;state.field=field;await open(p);await fill(p);await submit(p).click();
    await p.locator('.auth-field-error').waitFor();assert.equal(await p.locator('.auth-field-error').count(),1);assert.equal(await p.locator('form > [role=alert]').count(),0);assert.equal(await p.locator('[name=newPassword]').inputValue(),password);
    state.code=null;await submit(p).click();await p.waitForURL(u=>u.pathname==='/reset-password/complete');
  });
  await check('expired/used token becomes link error with new request entry',async()=>{
    const{page:p}=await setup();state.status=400;state.code='PASSWORD_RESET_TOKEN_INVALID';await open(p);await fill(p);await submit(p).click();
    await p.getByRole('link',{name:'새 재설정 메일 요청'}).waitFor();assert.equal(await p.locator('input[name=newPassword]').count(),0);await capture(p,'expired');
  });
  await check('pending duplicate prevention, 204 cleanup and authenticated header refresh',async()=>{
    const{page:p}=await setup();state.authenticated=true;await open(p);await p.getByText('검토회원',{exact:true}).first().waitFor();await fill(p);
    const release=hold();let received;const arrived=new Promise(r=>{received=r;});state.received=received;
    await submit(p).click();await arrived;assert(await p.getByRole('button',{name:'처리 중…',exact:true}).isDisabled());await p.locator('form').evaluate(e=>e.requestSubmit());assert.equal(state.posts.length,1);
    release();await p.waitForURL(u=>u.pathname==='/reset-password/complete');await p.getByRole('heading',{name:'비밀번호가 변경되었습니다',exact:true}).waitFor();
    assert.equal(state.posts[0].data.newPassword,password);assert.equal(state.posts[0].data.newPasswordConfirm,password);
    assert(!p.url().includes('token'));assert.equal(await p.locator('input').count(),0);await p.getByText('검토회원',{exact:true}).first().waitFor({state:'hidden'});await capture(p,'complete');
    await p.getByRole('link',{name:'로그인으로 이동',exact:true}).click();await p.waitForURL(u=>u.pathname==='/login'&&!u.search);
  });
  await check('CSRF preparation failure sends no POST; subsequent explicit retry works',async()=>{
    const{page:p}=await setup();state.csrfFail=true;await open(p);await fill(p);await submit(p).click();await p.locator('form > [role=alert]').waitFor();assert.equal(state.posts.length,0);
    state.csrfFail=false;await submit(p).click();await p.waitForURL(u=>u.pathname==='/reset-password/complete');
  });
  await check('network failure does not replay or lose password; explicit retry',async()=>{
    const{page:p,context}=await setup();await open(p);await fill(p);let attempts=0;
    const match=u=>u.pathname==='/api/v1/auth/password-reset/complete';
    await context.route(match,async route=>{attempts++;await route.abort('failed');});
    await submit(p).click();await p.locator('form > [role=alert]').waitFor();assert.equal(attempts,1);assert.equal(await p.locator('[name=newPassword]').inputValue(),password);
    await context.unroute(match);await submit(p).click();await p.waitForURL(u=>u.pathname==='/reset-password/complete');
  });
  await check('late completion cannot navigate away from current page',async()=>{
    const{page:p}=await setup();await open(p);await fill(p);const release=hold();let resolve;const arrived=new Promise(r=>{resolve=r;});state.received=resolve;
    await submit(p).click();await arrived;await p.getByRole('link',{name:'로그인 화면으로 돌아가기'}).click();await p.waitForURL(u=>u.pathname==='/login');release();await p.getByRole('form',{name:'로그인 양식'}).waitFor();assert.equal(new URL(p.url()).pathname,'/login');
  });
  await check('server email field error appears once and successful user retry clears it',async()=>{
    const{page:p}=await setup();state.status=400;state.code='VALIDATION_FAILED';state.field='email';await p.goto(base+'/forgot-password');
    await p.locator('#reset-email').fill('review@example.test');await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();
    await p.locator('#reset-email-error').waitFor();assert.equal(await p.locator('.auth-field-error').count(),1);assert.equal(await p.locator('form > [role=alert]').count(),0);
    state.code=null;await p.locator('#reset-email').fill('retry@example.test');await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();await p.getByText(accepted,{exact:true}).waitFor();
  });
  for(const code of ['CSRF_TOKEN_INVALID','SERVER_ERROR'])await check(`completion ${code}: values preserved and manual recovery`,async()=>{
    const{page:p}=await setup();state.status=code==='SERVER_ERROR'?503:403;state.code=code;await open(p);await fill(p);await submit(p).click();
    await p.locator('form > [role=alert]').waitFor();assert.equal(state.posts.length,1);assert.equal(await p.locator('[name=newPassword]').inputValue(),password);
    state.code=null;await submit(p).click();await p.waitForURL(u=>u.pathname==='/reset-password/complete');assert.equal(state.posts.length,2);
  });
  for(const site of ['cross-site','same-site','missing'])await check(`HTTP null Origin with ${site} metadata is rejected before API`,async()=>{
    const{page:p}=await setup(false);await p.goto(base+'/forgot-password');await p.locator('#reset-email').fill('review@example.test');
    // CDP route.continue cannot reliably override browser-owned Sec-Fetch headers.
    // Send the actual generated native form/action fields with explicit transport headers.
    const entries=await p.locator('form').evaluate(form=>Array.from(new FormData(form).entries()));
    const data=new FormData();for(const [key,value] of entries)data.append(key,value);
    const headers={Origin:'null','Sec-Fetch-Mode':'navigate','Sec-Fetch-Dest':'document'};
    if(site!=='missing')headers['Sec-Fetch-Site']=site;
    const response=await fetch(base+'/forgot-password',{method:'POST',body:data,headers});
    assert.equal(response.status,500);assert.equal(state.posts.length,0);assert.equal(state.csrfCalls,0);
  });
  for(const js of [false]) {
    await check(`no-JS request validation, error, retry, accepted and cookie forwarding`,async()=>{
      const{page:p}=await setup(js);await p.goto(base+'/forgot-password');await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();await p.locator('#reset-email-error').waitFor();assert.equal(state.posts.length,0);
      state.status=429;state.code='PASSWORD_RESET_RATE_LIMITED';await p.locator('#reset-email').fill('review@example.test');await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();await p.locator('form > [role=alert]').waitFor();assert.equal(state.posts.length,1);
      state.code=null;await p.locator('#reset-email').fill('unknown@example.test');await p.getByRole('button',{name:'재설정 메일 요청',exact:true}).click();await p.getByText(accepted,{exact:true}).waitFor();assert.deepEqual(state.sessionChecks,[true,true]);
      const cookie=(await p.context().cookies()).find(c=>c.name==='JSESSIONID');assert(cookie?.httpOnly);assert.equal(cookie.path,'/');assert.equal(cookie.sameSite,'Lax');
      await p.screenshot({path:path.join(out,'nojs-accepted.png'),fullPage:true});await p.getByRole('link',{name:'이메일 다시 입력하기'}).click();await p.locator('#reset-email').waitFor();
    });
    await check('no-JS password field error, retry, 204 redirect and token-free completion document',async()=>{
      const{page:p}=await setup(false,390);await open(p);await fill(p,password,'different');await submit(p).click();await p.locator('#reset-confirm-error').waitFor();assert.equal(state.posts.length,0);
      state.status=400;state.code='PASSWORD_RESET_PASSWORD_UNCHANGED';await fill(p);await submit(p).click();await p.locator('#reset-password-error').waitFor();assert.equal(await p.locator('.auth-field-error').count(),1);
      state.code=null;await fill(p);await submit(p).click();await p.waitForURL(u=>u.pathname==='/reset-password/complete');assert(await p.getByRole('heading',{name:'비밀번호가 변경되었습니다',exact:true}).isVisible());
      assert(!(await p.content()).includes(token));assert(!(await p.content()).includes(password));assert.deepEqual(state.sessionChecks,[true,true]);await p.screenshot({path:path.join(out,'nojs-complete-390.png'),fullPage:true});
      await p.getByRole('link',{name:'로그인으로 이동',exact:true}).click();await p.waitForURL(u=>u.pathname==='/login');
    });
    await check('no-JS invalid token from API and malformed initial link remain visible',async()=>{
      const{page:p}=await setup(false);await p.goto(base+'/reset-password?token=bad');assert(await p.getByRole('link',{name:'새 재설정 메일 요청'}).isVisible());
      state.status=400;state.code='PASSWORD_RESET_TOKEN_INVALID';await open(p);await fill(p);await submit(p).click();assert(await p.getByRole('link',{name:'새 재설정 메일 요청'}).isVisible());await p.screenshot({path:path.join(out,'nojs-expired.png'),fullPage:true});
    });
  }
} finally {
  await fs.writeFile(path.join(out,'results.json'),JSON.stringify({results,layouts,mode:'Mock API only; no real SMTP/accounts changed'},null,2));
  await browser.close(); server.closeAllConnections(); await new Promise(r=>server.close(r));
}
