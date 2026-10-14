// Real P03 component and policy; only the header's anonymous /me is mocked.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const output = process.env.INFO_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-p03-dialog-'));
await fs.mkdir(output, {recursive:true});
const browser = await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL ?? 'chrome',headless:true});
const context = await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',reducedMotion:'reduce'});
await context.route('**/api/v1/auth/me',route=>route.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED',message:'로그인이 필요합니다.',fieldErrors:[]}}));
const page = await context.newPage();page.setDefaultTimeout(10000);
const errors=[],results=[];let current;
page.on('pageerror',e=>errors.push(e.message));
const guide='/validation/hearing-aid-health-insurance';
const dialog=page.locator('dialog[open]');
const close=()=>page.getByRole('button',{name:'안내 닫기',exact:true}).click();
const fresh=async(width=1440)=>{await page.setViewportSize({width,height:width===1440?1000:844});await page.goto(base+guide);await page.locator('#p03-question-1').waitFor();};
const check=async(name,fn)=>{current=name;await fn();results.push({name,status:'passed'});console.log('PASS',name);};
const survey=()=>page.locator('.p03-guided').evaluate(el=>({
 text:el.innerText, checked:[...el.querySelectorAll('input[type=checkbox]')].map(e=>e.checked),
 answers:[...el.querySelectorAll('[aria-pressed=true]')].map(e=>e.textContent),
 details:[...el.querySelectorAll('details')].map(e=>[e.id,e.open]),
}));
const position=()=>page.evaluate(()=>({x:scrollX,y:scrollY}));
const openAt=async(id)=>{const button=page.locator('#'+id);await button.scrollIntoViewIfNeeded();await button.focus();const pos=await position();await page.keyboard.press('Enter');await dialog.waitFor();return pos;};
const assertRestored=async(id,pos,state)=>{
 assert.equal(await dialog.count(),0);assert.equal(await page.evaluate(()=>document.activeElement.id),id);
 const actual=await position();assert(Math.abs(actual.y-pos.y)<4,`scroll ${actual.y} != ${pos.y}`);assert.equal(actual.x,pos.x);
 assert.deepEqual(await survey(),state);assert.equal(await page.evaluate(()=>document.body.style.position),'');
};
try{
 await check('30초 요약: 승인된 간결한 내용·기존 소개 화면 제거·설문과 URL 유지',async()=>{
  await fresh();const state=await survey();const url=page.url();const pos=await openAt('p03-sidebar-summary');
  assert.equal(await dialog.getByRole('heading',{name:'30초 요약',exact:true}).count(),1);
  assert((await dialog.innerText()).includes('등록 확인 → 처방 → 구입·착용 → 검수·청구 → 이후 관리'));
  assert(!(await dialog.innerText()).includes('보청기센터에 가기 전,'));assert(!(await dialog.innerText()).includes('131만'));
  assert(await page.locator('.p03-guided').isVisible());assert.equal(page.url(),url);
  await close();await assertRestored('p03-sidebar-summary',pos,state);
 });
 await check('질문 진행 중 안내 열기: 양쪽 모드·목차 이동 후 질문/답변/스크롤 복귀',async()=>{
  await fresh();await page.locator('.p03-question[aria-labelledby="p03-question-1"]').getByRole('button',{name:'청각장애 등록을 마쳤어요',exact:true}).click();
  const state=await survey();const url=page.url();const pos=await openAt('p03-sidebar-bilateral');
  assert(await dialog.locator('#benefit input[value=bilateral]').isChecked());assert((await dialog.innerText()).includes('19세 미만'));
  await dialog.getByRole('link',{name:'지원 대상',exact:true}).click();assert.equal(page.url(),url);
  assert.equal(await page.evaluate(()=>document.activeElement.closest('section').id),'eligibility');
  await close();await assertRestored('p03-sidebar-bilateral',pos,state);
 });
 await check('결과의 모든 안내 버튼: 체크·펼침·내용 유지, 요약→전체 기준도 원래 버튼으로 복귀',async()=>{
  await fresh();await page.getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true}).click();
  await page.locator('.p03-note-checklist input').first().check();await page.locator('.p03-action-prepare input').last().check();
  const state=await survey();const url=page.url();
  for(const id of ['p03-result-reference','p03-result-bilateral','p03-sidebar-summary','p03-sidebar-reference','p03-sidebar-bilateral']){
   const pos=await openAt(id);
   if(id==='p03-sidebar-summary')await dialog.locator('#p03-summary-reference').click();
   assert.equal(await page.locator('dialog').count(),1);assert.equal(page.url(),url);
   await page.keyboard.press('Escape');await assertRestored(id,pos,state);
  }
 });
 await check('키보드 순환·배경 초점 차단·모달 바깥 클릭 시 답변 보존',async()=>{
  await fresh();await openAt('p03-sidebar-summary');
  const first=dialog.getByRole('button',{name:'안내 닫기',exact:true});const last=dialog.locator('#p03-summary-bilateral');
  await first.focus();await page.keyboard.press('Shift+Tab');assert(await last.evaluate(e=>e===document.activeElement));
  await page.keyboard.press('Tab');assert(await first.evaluate(e=>e===document.activeElement));
  await page.locator('.iyum-wordmark').first().evaluate(e=>e.focus());assert(await dialog.evaluate(e=>e.contains(document.activeElement)));
  await page.mouse.click(2,2);assert.equal(await dialog.count(),1);assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),0);
  await first.click();
 });
 await check('모달 내부 이동과 스크롤만 변경, 닫기 버튼은 긴 안내에서도 항상 접근',async()=>{
  await fresh();const pos=await openAt('p03-sidebar-reference');const state=await survey();
  const original=await page.locator('.p03-guided').boundingBox();
  await dialog.getByRole('link',{name:'공식 출처',exact:true}).click();assert(await dialog.locator('.p03-dialog-body').evaluate(e=>e.scrollTop>0));
  assert.deepEqual(await page.locator('.p03-guided').boundingBox(),original);
  const closeBox=await dialog.getByRole('button',{name:'안내 닫기',exact:true}).boundingBox();assert(closeBox.y>=0&&closeBox.y+closeBox.height<=1000);
  await close();await assertRestored('p03-sidebar-reference',pos,state);
 });
 await check('기존 query/hash 직접 진입: 목표 설명 모달·미선택 설문·닫은 뒤 바로 질문',async()=>{
  for(const hash of ['full-criteria','steps','benefit','registration-help','eligibility','sources','already-purchased','one-or-two']){
   await page.goto(base+guide+'?from=home#'+hash);await dialog.waitFor();
   const id=hash==='one-or-two'?'bilateral-heading':hash==='already-purchased'?'p03-reference-already-purchased':hash;
   assert(await dialog.locator('#'+id).isVisible());assert.equal(new URL(page.url()).search,'?from=home');
   await close();assert.equal(await dialog.count(),0);assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),0);assert(await page.locator('#p03-question-1').isVisible());
  }
 });
 await check('1440·390·320 모달 캡처·가로 넘침 없음·리사이즈 후 닫기와 설문 보존',async()=>{
  for(const width of [1440,390,320]){
   await fresh(width);await page.getByRole('button',{name:'병원에서 검사·진단을 받고 있어요',exact:true}).click();await page.locator('.p03-note-checklist input').first().check();const state=await survey();
   for(const [id,name]of [['p03-sidebar-summary','summary'],['p03-result-reference','criteria'],['p03-sidebar-bilateral','bilateral']]){
    const pos=await openAt(id);
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
    assert(await dialog.locator('.p03-dialog-body').evaluate(e=>e.scrollWidth<=e.clientWidth+1));
    await page.screenshot({path:path.join(output,`${name}-${width}.png`)});
    await close();await assertRestored(id,pos,state);
   }
   await page.screenshot({path:path.join(output,`survey-restored-${width}.png`)});
  }
  await openAt('p03-sidebar-reference');await page.setViewportSize({width:1440,height:1000});await page.setViewportSize({width:390,height:844});await close();assert.equal(await page.locator('.p03-note-checklist input:checked').count(),1);
 });
 await check('브라우저 뒤로 가기/페이지 이탈은 스크롤 잠금 정리, 다시 진입·반복 열기 정상',async()=>{
  await page.goto(base+'/welfare');await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));await page.getByRole('link',{name:'안내 보기 →',exact:true}).click();await page.locator('#p03-question-1').waitFor();await openAt('p03-sidebar-summary');
  await page.goBack();await page.waitForURL(base+'/welfare');await page.getByRole('heading',{name:'복지·지원정보',exact:true}).waitFor();assert.equal(await page.evaluate(()=>scrollY),0);assert.equal(await page.locator('dialog[open]').count(),0);assert.equal(await page.evaluate(()=>document.body.style.position),'');assert.equal(await page.evaluate(()=>document.documentElement.style.overflow),'');
  await page.goForward();await page.locator('#p03-question-1').waitFor();
  for(let i=0;i<3;i++){await openAt('p03-sidebar-summary');await page.keyboard.press('Escape');assert.equal(await dialog.count(),0);}
 });
 await check('04 검수·청구: Enter/Space 접기 뒤 05→Shift+Tab→04, 재열기·목차 후에도 순서 유지',async()=>{
  await fresh();
  for(const label of ['청각장애 등록을 마쳤어요','건강보험에 가입되어 있어요 (가족 포함)','예, 지원금 신청용 보청기 처방전을 받았어요','처음이에요'])await page.getByRole('button',{name:label,exact:true}).click();
  for(const keyboard of [false,true]){
   const link=page.locator('#p03-claim-link');if(keyboard){await link.focus();await link.press('Enter');}else await link.click();
   const fourth=dialog.locator('#p03-reference-journey-step-4 > summary');
   assert.equal(await fourth.getAttribute('tabindex'),null);assert.equal(await fourth.evaluate(e=>e.tabIndex),0);
   assert(await fourth.evaluate(e=>e===document.activeElement));
   for(const key of ['Enter','Space']){
    if(!(await dialog.locator('#p03-reference-journey-step-4').evaluate(e=>e.open)))await fourth.press(key);
    await fourth.press(key);assert.equal(await dialog.locator('#p03-reference-journey-step-4').evaluate(e=>e.open),false);
    await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement.closest('details')?.id),'p03-reference-journey-step-5');
    await page.keyboard.press('Shift+Tab');assert(await fourth.evaluate(e=>e===document.activeElement));
   }
   await dialog.getByRole('link',{name:'전체 절차',exact:true}).click();
   assert.equal(await fourth.getAttribute('tabindex'),null);
   await dialog.locator('#p03-reference-journey-step-5 > summary').focus();await page.keyboard.press('Shift+Tab');assert(await fourth.evaluate(e=>e===document.activeElement));
   await page.keyboard.press('Escape');assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-claim-link');
  }
 });
 await check('런타임 오류 없음',async()=>assert.deepEqual(errors,[]));
}catch(error){results.push({name:current,status:'failed',message:error.message});console.error('FAIL',current,error.message);process.exitCode=1;}
finally{await fs.writeFile(path.join(output,'results.json'),JSON.stringify({base,scope:'P03 Next UI; anonymous header /me mocked; no API writes',results,errors},null,2));await browser.close();}
