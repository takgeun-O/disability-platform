// Production Next UI. Authentication responses are mocked; no account/DB writes.
// Run alongside hearing-aid-dialog-browser.mjs and community-browser.mjs.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const output = process.env.FOCUS_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(),'iyum-focus-'));
await fs.mkdir(output,{recursive:true});
const browser = await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL ?? 'chrome',headless:true});
const results=[],errors=[];let current;
const guide='/validation/hearing-aid-health-insurance';
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const gate=()=>{let release;const promise=new Promise(resolve=>{release=resolve;});return {promise,release};};
async function until(fn){const end=Date.now()+10000;while(Date.now()<end){if(await fn())return;await pause(30);}throw Error('condition timed out');}
async function check(name,fn){current=name;await fn();results.push({name,status:'passed'});console.log('PASS',name);}
async function setup({height=1000,width=1440,touch=false}={}){
 const context=await browser.newContext({viewport:{width,height},hasTouch:touch,locale:'ko-KR',reducedMotion:'reduce'});
 const state={login:null,resend:null};
 await context.route('**/api/v1/auth/**',async route=>{
  const endpoint=new URL(route.request().url()).pathname.split('/').at(-1);
  if(state[endpoint])return state[endpoint](route);
  if(endpoint==='csrf')return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-csrf'}});
  return route.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED',message:'로그인이 필요합니다.',fieldErrors:[]}});
 });
 const page=await context.newPage();page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));
 return {context,page,state};
}
async function instrument(page){
 await page.evaluate(()=>{
  window.focusAudit={focus:[],scroll:[],into:[]};
  if(window.focusAuditInstalled)return;window.focusAuditInstalled=true;
  const focus=HTMLElement.prototype.focus,to=window.scrollTo.bind(window),into=Element.prototype.scrollIntoView;
  HTMLElement.prototype.focus=function(options){window.focusAudit.focus.push({id:this.id,tag:this.tagName});return focus.call(this,options);};
  window.scrollTo=(...args)=>{window.focusAudit.scroll.push(args);return to(...args);};
  Element.prototype.scrollIntoView=function(...args){window.focusAudit.into.push(this.id);return into.apply(this,args);};
 });
}
const audit=p=>p.evaluate(()=>window.focusAudit);
const q1=p=>p.locator('.p03-question[aria-labelledby="p03-question-1"]');
const result=p=>p.locator('#p03-result-heading');
async function fresh(p){await p.goto(base+guide);await p.locator('#p03-question-1').waitFor();await pause(150);}
async function shot(p,name){await p.evaluate(()=>document.fonts.ready);await p.screenshot({path:path.join(output,name+'.png')});}
try{
 await check('일반 진입·새로고침: 홈/소개/복지/P03/인증 화면에 자동 제목 초점·스크롤 없음',async()=>{
  const {page,context}=await setup();
  await page.addInitScript(()=>{window.initialFocusCalls=[];const focus=HTMLElement.prototype.focus;HTMLElement.prototype.focus=function(options){window.initialFocusCalls.push(this.tagName);return focus.call(this,options);};});
  for(const route of ['/','/about','/welfare',guide,'/login','/register','/register/verify','/register/resend']){
   for(const reload of [false,true]){
    if(reload)await page.reload();else await page.goto(base+route);
    await page.getByRole('main').waitFor();await pause(200);
    assert.equal(await page.evaluate(()=>scrollY),0,route);
    assert.equal(await page.evaluate(()=>document.activeElement.matches('h1,h2,h3')),false,route);
    assert.deepEqual(await page.evaluate(()=>window.initialFocusCalls.filter(t=>/^H[1-6]$/.test(t))),[],route);
   }
  }
  // The information form requires the real consent flow; it is not bypassed.
  await page.goto(base+'/register');await page.getByRole('checkbox',{name:'전체 동의',exact:true}).check();
  await page.getByRole('button',{name:'다음',exact:true}).click();await page.waitForURL('**/register/info');
  assert.equal(await page.evaluate(()=>document.activeElement.matches('h1,h2,h3')),false);
  await context.close();
 });
 await check('마우스·터치: 다음 결과가 이미 보이는 경우 제목 초점 및 앱 스크롤 없음',async()=>{
  for(const touch of [false,true]){
   const {page,context}=await setup({height:1800,touch});await fresh(page);
   const answer=q1(page).getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true});
   await answer.scrollIntoViewIfNeeded();await instrument(page);
   if(touch)await answer.tap();else await answer.click();await result(page).waitFor();
   assert.equal(await result(page).evaluate(e=>e===document.activeElement),false);
   const calls=await audit(page);assert.deepEqual(calls.scroll,[]);assert.deepEqual(calls.into,[]);assert.deepEqual(calls.focus,[]);
   await context.close();
  }
 });
 await check('화면 밖 결과: 한 번만 최소 스크롤, 감소된 모션은 즉시, 키보드 읽기 초점 유지',async()=>{
  for(const keyboard of [false,true]){
   const {page,context}=await setup({width:390,height:844});await fresh(page);
   const answer=q1(page).getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true});
   await answer.scrollIntoViewIfNeeded();if(keyboard)await answer.focus();await instrument(page);
   if(keyboard)await answer.press('Enter');else await answer.click();await result(page).waitFor();
   const calls=await audit(page);assert.equal(calls.scroll.length,1);assert.equal(calls.scroll[0][0].behavior,'instant');assert.deepEqual(calls.into,[]);
   const heading=result(page);const box=await heading.boundingBox();assert(box.y>400&&box.y+180<=822,JSON.stringify(box));
   assert.equal(await heading.evaluate(e=>e===document.activeElement),keyboard);
   if(keyboard){assert.match(await heading.evaluate(e=>getComputedStyle(e).boxShadow),/-4px/);await page.keyboard.press('Tab');assert(await page.evaluate(()=>document.activeElement.matches('a,button,input,summary')));}
   assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).scrollBehavior),'auto');await context.close();
  }
 });
 await check('일반 모션: 화면 밖 다음 내용만 부드러운 이동, 예약된 제목 초점 없음',async()=>{
  const {page,context}=await setup({width:390,height:844});await page.emulateMedia({reducedMotion:'no-preference'});await fresh(page);
  const answer=q1(page).getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true});await answer.scrollIntoViewIfNeeded();await instrument(page);await answer.click();
  await result(page).waitFor();const calls=await audit(page);assert.equal(calls.scroll.length,1);assert.equal(calls.scroll[0][0].behavior,'smooth');
  await pause(500);assert.equal((await audit(page)).scroll.length,1);assert.equal(await page.evaluate(()=>document.activeElement.id==='p03-question-2'),false);await context.close();
 });
 await check('1440/390/320: 마우스 제목 표시·키보드 작은 강조·모달 조작 요소 표시와 캡처',async()=>{
  for(const width of [1440,390,320]){
   const {page,context}=await setup({width,height:width===1440?1000:844});await fresh(page);
   await q1(page).getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true}).click();
   assert.equal(await result(page).evaluate(e=>e===document.activeElement),false);await shot(page,`mouse-result-${width}`);
   const opener=page.locator('#p03-result-reference');await opener.click();const heading=page.locator('dialog[open] :is(h1,h2,h3):focus');
   assert.equal(await heading.count(),1);assert.equal(await heading.evaluate(e=>getComputedStyle(e).outlineColor),'rgba(0, 0, 0, 0)');
   await shot(page,`mouse-modal-${width}`);await page.keyboard.press('Escape');
   await fresh(page);const option=q1(page).getByRole('button',{name:'청각장애 등록을 마쳤어요',exact:true});await option.focus();await option.press('Enter');
   assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-question-2');await shot(page,`keyboard-question-${width}`);
   await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement.matches('button,summary')),true);assert.equal(await page.evaluate(()=>document.activeElement.closest('.p03-question')?.getAttribute('aria-labelledby')),'p03-question-2');
   assert.equal(await page.evaluate(()=>getComputedStyle(document.activeElement).outlineStyle),'solid');
   for(const label of ['건강보험에 가입되어 있어요 (가족 포함)','예, 지원금 신청용 보청기 처방전을 받았어요','처음이에요']){const b=page.getByRole('button',{name:label,exact:true});await b.focus();await b.press('Enter');}
   const claim=page.locator('#p03-claim-link');await claim.focus();await claim.press('Enter');
   const fourth=page.locator('#p03-reference-journey-step-4 > summary');assert(await fourth.evaluate(e=>e===document.activeElement));
   assert.equal(await fourth.evaluate(e=>getComputedStyle(e).outlineStyle),'solid');await shot(page,`keyboard-modal-04-${width}`);
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
   assert(await page.locator('.p03-dialog-body').evaluate(e=>e.scrollWidth<=e.clientWidth+1));await context.close();
  }
 });
 await check('복사 실패: 직접 복사 초점 유지, 재시도 가능, 다른 조작/모달 닫기 뒤 늦은 실패는 초점 보존',async()=>{
  const {page,context}=await setup();await fresh(page);await q1(page).getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true}).click();
  await page.evaluate(()=>{Object.defineProperty(navigator.clipboard,'writeText',{configurable:true,writable:true,value:()=>Promise.reject(Error('denied'))});});
  for(let i=0;i<2;i++){await page.getByRole('button',{name:'이 질문 복사하기',exact:true}).click();await until(()=>page.getByRole('textbox',{name:'복사할 질문',exact:true}).evaluate(e=>e===document.activeElement));}
  await page.evaluate(()=>{navigator.clipboard.writeText=()=>new Promise((_,reject)=>{window.rejectCopy=()=>reject(Error('delayed'));});});
  await page.getByRole('button',{name:'이 질문 복사하기',exact:true}).click();await until(()=>page.evaluate(()=>!!window.rejectCopy));
  const checkbox=page.locator('.p03-note-checklist input').first();await checkbox.focus();await instrument(page);await page.evaluate(()=>window.rejectCopy());await pause(100);assert(await checkbox.evaluate(e=>e===document.activeElement));assert.deepEqual((await audit(page)).focus,[]);
  await page.goto(base+guide+'#one-or-two');await page.locator('dialog[open] #bilateral-heading').waitFor();
  await page.evaluate(()=>{window.rejectCopy=null;navigator.clipboard.writeText=()=>new Promise((_,reject)=>{window.rejectCopy=()=>reject(Error('delayed'));});});
  const copy=page.locator('dialog[open]').getByRole('button',{name:/복사/}).first();await copy.click();await until(()=>page.evaluate(()=>!!window.rejectCopy));
  await page.keyboard.press('Escape');const returned=await page.evaluate(()=>document.activeElement.id);await instrument(page);await page.evaluate(()=>window.rejectCopy());await pause(100);
  assert.equal(await page.evaluate(()=>document.activeElement.id),returned);assert.deepEqual((await audit(page)).focus,[]);await context.close();
 });
 for(const kind of ['login','resend'])await check(`${kind}: 목적지 응답 지연으로 폼이 남아도 이탈 승인 후 늦은 응답이 초점·스크롤을 빼앗지 않음`,async()=>{
  const {page,context,state}=await setup();const response=gate(),destination=gate();let requested=false,started=false;
  state[kind]=async route=>{requested=true;await response.promise;await route.fulfill(kind==='login'?{status:401,json:{code:'INVALID_CREDENTIALS',message:'인증 실패',fieldErrors:[]}}:{status:202,json:{status:'ACCEPTED'}});};
  await context.route(url=>url.origin===base&&url.pathname==='/about',async route=>{if(route.request().headers().rsc==='1'){started=true;await destination.promise;}await route.continue();});
  await page.goto(base+(kind==='login'?'/login':'/register/resend'));
  await page.locator('input[name=email]').fill('focus@example.test');
  if(kind==='login')await page.locator('input[name=password]').fill('mock-only');
  await page.getByRole('main').getByRole('button',{name:kind==='login'?'로그인':'인증 메일 재전송 요청',exact:true}).click();await until(()=>requested);
  await page.locator('#iyum-navigation a[href="/about"]').click();await until(()=>started);assert(await page.locator('input[name=email]').isVisible());
  await instrument(page);const before=await page.evaluate(()=>({active:document.activeElement.outerHTML,y:scrollY}));response.release();await pause(250);
  assert(await page.locator('input[name=email]').isVisible());assert.deepEqual(await page.evaluate(()=>({active:document.activeElement.outerHTML,y:scrollY})),before);assert.deepEqual((await audit(page)).focus,[]);
  destination.release();await page.waitForURL(base+'/about');await context.close();
 });
 await check('로그인/재전송 입력 오류의 첫 입력 초점·본문 바로가기·런타임 오류',async()=>{
  const {page,context}=await setup();
  for(const [route,label]of [['/login','로그인'],['/register/resend','인증 메일 재전송 요청']]){
   await page.goto(base+route);await page.getByRole('main').getByRole('button',{name:label,exact:true}).click();await until(()=>page.locator('input[name=email]').evaluate(e=>e===document.activeElement));assert.equal(await page.locator('input[name=email]').getAttribute('aria-invalid'),'true');
  }
  for(const route of ['/','/about','/welfare',guide]){await page.goto(base+route);await page.locator('.iyum-skip-link').focus();await page.keyboard.press('Enter');assert.equal(await page.evaluate(()=>document.activeElement.id),'main-content-start');assert.equal(new URL(page.url()).pathname,route);}
  await context.close();assert.deepEqual(errors,[]);
 });
}catch(error){results.push({name:current,status:'failed',message:error.message});console.error('FAIL',current,error.stack);process.exitCode=1;}
finally{await fs.writeFile(path.join(output,'results.json'),JSON.stringify({base,scope:'production UI; auth mocked; no DB writes',results,errors},null,2));await browser.close();}
