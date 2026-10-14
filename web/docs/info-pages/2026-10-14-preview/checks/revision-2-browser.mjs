import {fileURLToPath} from 'node:url';
import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright');
const out=fileURLToPath(new URL('../',import.meta.url)).replace(/\/$/,'');
const captures=out+'/captures/revision-2';await fs.mkdir(captures,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const page=await browser.newPage({permissions:['clipboard-read','clipboard-write']});
page.setDefaultTimeout(10000);
const results=[],errors=[],api=[];
page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().includes('/api/'))api.push(r.url());});
const fresh=async(view,width=1440)=>{await page.setViewportSize({width,height:width===1440?1000:844});await page.goto(`http://127.0.0.1:8450/?revision=2&view=${view}&width=${width}#${view}`);await page.locator('h1').waitFor();};
const check=async(name,fn)=>{try{await fn();results.push({name,status:'passed'});console.log('PASS',name);}catch(e){results.push({name,status:'failed',error:e.message});throw e;}};
const choose=async(i,value)=>page.locator(`[data-question="${i}"][data-answer="${value}"]`).click();
const retained=async(count)=>{
 assert.equal(await page.locator('[data-question-block]').count(),count);
 for(let i=0;i<count;i++)assert(await page.locator(`[data-question-block="${i}"] h2`).isVisible());
};
try {
 await check('현재·확장 목록: 보청기 상단 강조 제거, 실제 안내 카드 유지 (1440/390/320)',async()=>{
  for(const width of [1440,390,320])for(const view of ['welfare','expanded']){
   await fresh(view,width);assert.equal(await page.locator('.highlight').count(),0);
   assert(!(await page.locator('main').innerText()).includes('보청기 지원, 내 상황부터 확인해 보세요'));
   assert.equal(await page.getByRole('heading',{name:'보청기 건강보험 지원 안내',exact:true}).count(),1);
  }
 });
 await check('질문부터 시작, 완료 후 질문 4개와 마지막 선택지를 유지하고 안내는 아래 표시',async()=>{
  for(const width of [1440,390,320]){
   await fresh('guide',width);assert.equal(await page.locator('#result-section').count(),0);
   const path=['registered','health','yes','first'];
   for(let i=0;i<path.length;i++){
    await choose(i,path[i]);await retained(Math.min(i+2,4));
    if(i<3)assert.equal(await page.locator('#result-section').count(),0);
   }
   assert.equal(await page.locator('#result-heading').textContent(),'구입 전 제품·비용 상담');
   assert.equal(await page.locator('[data-question="3"][data-answer="first"]').getAttribute('aria-pressed'),'true');
   assert(await page.evaluate(()=>document.querySelector('#questionnaire').getBoundingClientRect().bottom<document.querySelector('#result-section').getBoundingClientRect().top));
   assert(await page.locator('#result-heading').evaluate(e=>e===document.activeElement));
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  }
 });
 await check('이전 질문 키보드 변경: 앞 답변 유지, 이후 답변/결과 초기화, 의료급여 5문항 결과 하단 표시',async()=>{
  const edit=page.getByRole('button',{name:'질문 2 답변 변경',exact:true});await edit.focus();await page.keyboard.press('Enter');
  assert.equal(await page.locator('#result-section').count(),0);await retained(2);
  assert((await page.locator('[data-question-block="0"]').innerText()).includes('청각장애 등록을 마쳤어요'));
  assert(await page.locator('#question-heading').evaluate(e=>e===document.activeElement));
  await choose(1,'medical-aid');await choose(2,'yes');await choose(3,'unknown');await choose(4,'not-applied');
  await retained(5);assert.equal(await page.locator('#result-heading').innerText(),'주민센터 신청 단계');
  assert(!(await page.locator('main').innerText()).includes('131만'));
 });
 await check('상세 왕복과 질문 복사 후에도 질문/선택 내용 유지; 1문항 조기 결과도 질문 아래 표시',async()=>{
  await page.getByRole('button',{name:'전체 기준·공식 출처 보기 →',exact:true}).click();
  await page.waitForURL(u=>u.hash==='#detail');await page.locator('#detail-back').click();await page.locator('#result-heading').waitFor();
  await retained(5);
  await fresh('result',390);await page.locator('#copy').click();assert((await page.evaluate(()=>navigator.clipboard.readText())).includes('처음 신청'));await retained(4);
  await fresh('guide',390);await choose(0,'planning');await retained(1);assert.equal(await page.locator('#result-section').count(),1);
  assert.equal(await page.locator('[data-answer="planning"]').getAttribute('aria-pressed'),'true');
  assert(await page.evaluate(()=>document.querySelector('#questionnaire').getBoundingClientRect().bottom<document.querySelector('#result-section').getBoundingClientRect().top));
 });
 await check('수정 시안 캡처: 4화면 × 1440/390/320, 가로 넘침 없음',async()=>{
  for(const width of [1440,390,320])for(const view of ['welfare','expanded','guide','result']){
   await fresh(view,width);await page.evaluate(()=>document.fonts.ready);
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await page.screenshot({path:`${captures}/${view}-${width}.png`,fullPage:true});
   if(width!==320)await page.screenshot({path:`${captures}/${view}-${width}-top.png`});
   if(view==='result'){
    await page.evaluate(()=>{const q=document.querySelector('.question-card');window.scrollTo(0,q.getBoundingClientRect().top+scrollY-20);});
    await page.screenshot({path:`${captures}/question-and-result-${width}.png`});
   }
  }
 });
 await check('시안의 API·저장소 사용·런타임 오류 없음',async()=>{assert.deepEqual(api,[]);assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>localStorage.length+sessionStorage.length),0);});
} catch(e){console.error(e);process.exitCode=1;} finally {
 await fs.writeFile(out+'/revision-2-check-results.json',JSON.stringify({date:'2026-10-14',scope:'독립 시안 2차 수정: 실제 API/서비스 통합 검증 아님',results,errors,apiCalls:api.length},null,2));await browser.close();
}
