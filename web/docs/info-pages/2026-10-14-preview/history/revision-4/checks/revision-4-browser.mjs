import {fileURLToPath} from 'node:url';import fs from 'node:fs/promises';import vm from 'node:vm';import assert from 'node:assert/strict';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright');const root=fileURLToPath(new URL('../',import.meta.url)).replace(/\/$/,''),captures=root+'/captures/revision-4';await fs.mkdir(captures,{recursive:true});
const context={window:{}};vm.runInNewContext(await fs.readFile(root+'/preview/p03-snapshot.js','utf8'),context);const cases=Object.entries(context.window.P03.states).filter(([,s])=>s.code);
const browser=await chromium.launch({channel:'chrome',headless:true}),ctx=await browser.newContext({permissions:['clipboard-read','clipboard-write'],reducedMotion:'reduce'}),p=await ctx.newPage();p.setDefaultTimeout(10000);
const results=[],errors=[],calls=[];p.on('pageerror',e=>errors.push(e.message));p.on('request',r=>{if(r.url().includes('/api/'))calls.push(r.url());});let seq=0;
const fresh=async(v,width=1440)=>{await p.setViewportSize({width,height:width===1440?1000:844});await p.goto(`http://127.0.0.1:8450/?revision=4&run=${++seq}#${v}`);await p.locator('h1').waitFor();};
const check=async(name,fn)=>{try{const detail=await fn();results.push({name,status:'passed',...(detail?{detail}:{})});console.log('PASS',name);}catch(e){results.push({name,status:'failed',message:e.message});throw e;}};
try{
 await check('실제 원본 28개 완료 조합: 4순서·기관/문의/행동·기존 조건·금액 노출 유지',async()=>{
  for(const [key,state] of cases){await fresh('guide',390);for(const [i,answer]of JSON.parse(key).entries()){if(answer!==null)await p.locator(`[data-question="${i}"][data-answer="${answer}"]`).click();}
   assert.deepEqual(await p.locator('.action-sequence>li').evaluateAll(es=>es.map(e=>e.dataset.action)),['place','question','prepare','after']);
   assert.equal(await p.locator('.place-heading p').textContent(),state.info.institution);assert.equal(await p.locator('.action-question blockquote').textContent(),state.info.contactQuestion);assert.equal(await p.locator('.action-prepare .action-main').textContent(),state.info.nextAction);
   if(state.info.nextActionNote)assert((await p.locator('.action-prepare').innerText()).includes(state.info.nextActionNote));if(state.info.secondary)assert((await p.locator('.action-prepare').innerText()).includes(state.info.secondary));
   assert.equal(await p.locator('.extra-questions').count(),['R08','R18'].includes(state.code)?1:0);
   assert.equal(await p.getByRole('heading',{name:/센터에서 물어볼 질문/}).count(),0);
   if(!state.info.hasHealthDetail)assert(!(await p.locator('main').innerText()).includes('131만'));
   assert.equal(await p.locator('[data-question-block]').count(),JSON.parse(key).filter(Boolean).length);
   assert(await p.evaluate(()=>document.querySelector('#questionnaire').getBoundingClientRect().bottom<document.querySelector('#result-section').getBoundingClientRect().top));
  }return{completedCombinations:cases.length};
 });
 await check('등록 전: 주민센터→등록 문의→진단 자료 확인→병원 검사/자료 제출/심사',async()=>{
  await fresh('result-registration');assert.equal(await p.locator('.place-heading p').innerText(),'주소지 주민센터');assert((await p.locator('.action-question blockquote').innerText()).includes('어떤 병원에서 검사받고'));
  assert((await p.locator('.action-prepare').innerText()).includes('이미 진단 자료가 있다면'));assert((await p.locator('.action-after').innerText()).includes('검사·진단 자료를 받은 뒤 주민센터'));
  assert.equal(await p.locator('.extra-questions').count(),0);assert.equal(await p.locator('.place-heading svg').getAttribute('aria-hidden'),'true');
 });
 await check('센터 상담에서만 추가 제품/비용 질문 토글·현재 문의 복사와 기존 상담 질문 복사 구분',async()=>{
  await fresh('result');await p.locator('#copy').click();let text=await p.evaluate(()=>navigator.clipboard.readText());assert(text.includes('처음 신청'));assert(text.includes('먼저 확인할 곳: 상담할 보청기센터'));assert(text.includes('이곳에서 물어볼 질문:'));assert(!(text.includes('1. 제 청력')));
  assert.equal(await p.locator('.extra-questions').getAttribute('open'),null);await p.locator('.extra-questions>summary').focus();await p.keyboard.press('Enter');assert.equal(await p.locator('.extra-questions .questions-list li').count(),3);await p.locator('#copy-center').click();text=await p.evaluate(()=>navigator.clipboard.readText());assert.equal(text,context.window.P03.states[JSON.stringify(['registered','health','yes','first',null])].copy);
  await p.locator('.extra-questions>summary').click();assert(!(await p.locator('#copy-center').isVisible()));
 });
 await check('의료급여/모름: 신청·결정 통보의 조건 유지, 기억나지 않음을 첫 신청/승인으로 처리하지 않음',async()=>{
  await fresh('result-medical');assert((await p.locator('.action-after').innerText()).includes('신청했다고 바로 구입하지 않아요'));assert((await p.locator('.action-prepare').innerText()).includes('처방전'));
  await p.locator('[data-answer=unknown][data-question="4"]').click();assert((await p.locator('.action-after').innerText()).includes('실제 구입은 이번 결정 통보를 받은 뒤'));
  await fresh('guide');for(const [i,a]of ['registered','health','yes','unknown'].entries())await p.locator(`[data-question="${i}"][data-answer="${a}"]`).click();assert((await p.locator('.action-after').innerText()).includes('첫 신청으로 처리하지 않아요'));
 });
 await check('복사 권한 실패 시 현재 단계 문의의 직접 복사·키보드 초점 제공',async()=>{
  await fresh('result-registration',320);await p.evaluate(()=>Object.defineProperty(navigator.clipboard,'writeText',{value:async()=>{throw Error('test denied');}}));await p.locator('#copy').click();assert(await p.locator('#copy-fallback').isVisible());assert(await p.locator('#copy-fallback').evaluate(e=>e===document.activeElement));assert((await p.locator('#copy-fallback').inputValue()).includes('주소지 주민센터'));assert((await p.locator('#copy-status').innerText()).includes('직접 복사'));
 });
 await check('수정·상세 왕복 시 기관/질문/다음 단계가 답변과 일치하고 4순서 유지',async()=>{
  await fresh('result-registration',390);await p.getByRole('button',{name:'전체 기준·공식 출처 보기 →'}).click();await p.waitForURL(u=>u.hash==='#detail');await p.locator('#detail-back').click();await p.locator('.place-heading p').waitFor();assert.equal(await p.locator('.place-heading p').innerText(),'주소지 주민센터');
  await p.locator('[data-answer=assessment]').click();assert.equal(await p.locator('.place-heading p').innerText(),'지금 진료받는 이비인후과');assert((await p.locator('.action-after').innerText()).includes('자료를 받으면 주민센터'));
 });
 await check('1440/390/320 결과 4종 캡처·가로 넘침·버튼 최소44px·순서와 대비',async()=>{
  for(const w of [1440,390,320])for(const [v,name]of [['result-registration','registration'],['result-hospital','hospital'],['result-medical','medical'],['result','center']]){
   await fresh(v,w);await p.evaluate(()=>document.fonts.ready);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert((await p.locator('#copy').boundingBox()).height>=44);
   const boxes=await p.locator('.action-sequence>li').evaluateAll(es=>es.map(e=>({top:e.getBoundingClientRect().top,bottom:e.getBoundingClientRect().bottom})));for(let i=1;i<boxes.length;i++)assert(boxes[i].top>boxes[i-1].bottom);
   await p.screenshot({path:`${captures}/${name}-${w}.png`,fullPage:true});await p.locator('.action-guidance').screenshot({path:`${captures}/${name}-actions-${w}.png`});
   await p.locator('#result-heading').evaluate(e=>e.scrollIntoView({block:'start',behavior:'instant'}));await p.screenshot({path:`${captures}/${name}-result-top-${w}.png`});
  }
 });
 await check('시안 API 요청·저장소·런타임 오류 없음',async()=>{assert.deepEqual(errors,[]);assert.deepEqual(calls,[]);assert.equal(await p.evaluate(()=>localStorage.length+sessionStorage.length),0);});
}catch(e){console.error(e);process.exitCode=1;}finally{await fs.writeFile(root+'/revision-4-check-results.json',JSON.stringify({scope:'검토 시안 브라우저 검사, 실제 API/정책 최신성 검증 아님',results,errors,apiCalls:calls.length},null,2));await browser.close();}
