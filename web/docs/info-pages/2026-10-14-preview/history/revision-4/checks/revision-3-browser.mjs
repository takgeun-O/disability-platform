import {fileURLToPath} from 'node:url';import fs from 'node:fs/promises';import assert from 'node:assert/strict';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright');
const root=fileURLToPath(new URL('../',import.meta.url)).replace(/\/$/,''),capture=root+'/captures/'+(process.env.CAPTURE_REVISION??'revision-4');await fs.mkdir(capture,{recursive:true});
const b=await chromium.launch({channel:'chrome',headless:true});const p=await b.newPage({viewport:{width:1440,height:1000},permissions:['clipboard-read','clipboard-write']});p.setDefaultTimeout(10000);
const results=[],errors=[],api=[];p.on('pageerror',e=>errors.push(e.message));p.on('request',r=>{if(r.url().includes('/api/'))api.push(r.url());});
await p.addInitScript(()=>{window.scrollRequests=[];const native=Element.prototype.scrollIntoView;Element.prototype.scrollIntoView=function(arg){window.scrollRequests.push({id:this.id,arg});return native.call(this,arg);};});
const check=async(name,fn)=>{try{const detail=await fn();results.push({name,status:'passed',...(detail?{detail}:{})});console.log('PASS',name);}catch(e){results.push({name,status:'failed',message:e.message});throw e;}};
let navigationSeq=0;
const fresh=async(v,w=1440)=>{await p.setViewportSize({width:w,height:w===1440?1000:844});await p.goto(`http://127.0.0.1:8450/?revision=3&capture=${w}-${v}&fresh=${++navigationSeq}#${v}`);await p.locator('h1').waitFor();await p.evaluate(()=>document.fonts.ready);};
const choose=async(i,a)=>{await p.locator(`[data-question="${i}"][data-answer="${a}"]`).click();await p.waitForTimeout(750);};
const colors=()=>p.locator('.journey-step').evaluateAll(es=>es.map(e=>({status:e.dataset.state,bg:getComputedStyle(e).backgroundColor,current:e.getAttribute('aria-current')})));
try{
 await check('실제 기존 P03: 완료/현재 색상과 단계별 토글을 브라우저에서 재확인 (읽기 전용)',async()=>{
  const live=await b.newPage({viewport:{width:1440,height:1000}});try{
   await live.goto('http://127.0.0.1:8443/validation/hearing-aid-health-insurance');await live.getByRole('button',{name:'내 상황 확인하기 →',exact:true}).click();
   await live.getByRole('button',{name:'청각장애 등록을 마쳤어요',exact:true}).click();await live.getByRole('button',{name:'건강보험에 가입되어 있어요 (가족 포함)',exact:true}).click();
   const states=await live.locator('.p03-journey-step').evaluateAll(es=>es.map(e=>({text:e.textContent.trim(),bg:getComputedStyle(e).backgroundColor})));
   assert.equal(states[0].bg,'rgb(236, 253, 245)');assert.equal(states[1].bg,'rgb(254, 243, 199)');
   await live.locator('#p03-journey-step-3').click();assert(await live.locator('#p03-journey-detail-3').isVisible());await live.locator('#p03-journey-step-3').click();assert(!(await live.locator('#p03-journey-detail-3').isVisible()));
   return {scope:'기존 서비스 P03 UI, 인증/게시글 API 검증 아님',states};
  }finally{await live.close();}
 });
 await check('소개 정체성·핵심 2개 행동·주제·현재/향후 구분 (1440/390/320)',async()=>{
  for(const w of [1440,390,320]){await fresh('about',w);
   assert((await p.locator('.about-lead').innerText()).includes('정보·커뮤니티 플랫폼'));
   assert(!(await p.locator('.about-lead').innerText()).includes('청각장애'));
   assert.equal(await p.locator('.about-lead .actions a').count(),2);
   for(const text of ['정보 찾기','질문과 경험 공유','지식 축적','복지·지원','보조기기','병원·기관','취업·교육·생활','공식 정보와 개인 경험은 구분합니다'])assert((await p.locator('main').innerText()).includes(text));
   assert((await p.locator('.scope-panel').innerText()).includes('현재 제공하는 기능은 아닙니다'));
   assert((await p.locator('.scope-panel').innerText()).includes('시각·지체·발달장애'));
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  }
 });
 await check('답변에 따라 완료 초록/현재 노랑/확인 필요 회청/이후 과정 구분',async()=>{
  await fresh('guide');await choose(0,'registered');await choose(1,'health');let c=await colors();assert.equal(c[0].bg,'rgb(236, 253, 245)');assert.equal(c[1].bg,'rgb(254, 243, 199)');assert.notEqual(c[2].bg,c[0].bg);
  await choose(2,'yes');await choose(3,'previous');c=await colors();assert.equal(c[2].status,'check-needed');assert.equal(c[2].bg,'rgb(254, 243, 199)');assert.equal(c[2].current,null);
  await fresh('guide');await choose(0,'registered');await choose(1,'unknown');await choose(2,'no');c=await colors();assert.equal(c[1].status,'check-needed');assert.equal(c[1].bg,'rgb(237, 241, 244)');assert.equal(c[1].current,null);
 });
 await check('5개 단계 토글·키보드·현재 위치 불변·미노출 설명 초점 제외',async()=>{
  await fresh('result');const before=await colors();
  for(let i=0;i<5;i++){
   const button=p.locator(`#journey-step-${i}`);await button.focus();await p.keyboard.press('Enter');
   assert.equal(await button.getAttribute('aria-expanded'),'true');assert(await p.locator(`#journey-detail-${i}`).isVisible());assert((await p.locator(`#journey-detail-${i}`).innerText()).includes('절차'));
   assert.equal(await p.locator('.journey-detail:visible').count(),1);assert.deepEqual(await colors(),before);
   await p.keyboard.press('Space');assert.equal(await button.getAttribute('aria-expanded'),'false');assert.equal(await p.locator('.journey-detail:visible').count(),0);
  }
  assert.equal(await p.locator('[data-question-block]').count(),4);assert.equal(await p.locator('#result-heading').innerText(),'구입 전 제품·비용 상담');
 });
 await check('모바일 절차 펼침 유지·설명 추적과 상세 복귀·취소한 펼침 유지',async()=>{
  await fresh('guide',390);assert.equal(await p.locator('#journey-overview').getAttribute('open'),null);
  await p.locator('#journey-overview>summary').click();await p.locator('#journey-step-0').click();
  await choose(0,'registered');await choose(1,'health');assert.equal(await p.locator('#journey-step-1').getAttribute('aria-expanded'),'true');assert(await p.locator('#journey-detail-1').isVisible());
  await p.locator('[data-detail=criteria]').first().click();await p.waitForURL(u=>u.hash==='#detail');await p.locator('#detail-back').click();await p.locator('#journey-step-1').waitFor();assert.equal(await p.locator('#journey-step-1').getAttribute('aria-expanded'),'true');
  await p.locator('#journey-step-1').click();await choose(2,'yes');assert.equal(await p.locator('.journey-step[aria-expanded=true]').count(),0);
  await choose(3,'first');assert.equal(await p.locator('[data-question-block]').count(),4);
 });
 await check('답변 후 실제 부드러운 스크롤과 초점; 질문 유지, 결과 제목 도착',async()=>{
  await fresh('guide',390);await choose(0,'registered');await choose(1,'health');await choose(2,'yes');
  await p.locator('[data-answer=first]').evaluate(e=>e.scrollIntoView({block:'center',behavior:'instant'}));
  await p.evaluate(()=>{window.scrollRequests=[];window.scrollSamples=[];const t=performance.now();function sample(){window.scrollSamples.push(scrollY);if(performance.now()-t<1300)requestAnimationFrame(sample);}requestAnimationFrame(sample);});
  await p.locator('[data-answer=first]').click();await p.waitForTimeout(1400);
  const observation=await p.evaluate(()=>({requests:window.scrollRequests,positions:[...new Set(window.scrollSamples)],top:document.querySelector('#result-heading').getBoundingClientRect().top,active:document.activeElement.id}));
  assert(observation.requests.some(x=>x.id==='result-heading'&&x.arg.behavior==='smooth'&&x.arg.block==='start'));
  assert(observation.positions.length>5,'scroll must animate, not merely request smooth');assert(Math.abs(observation.top-20)<4);assert.equal(observation.active,'result-heading');assert.equal(await p.locator('[data-question-block]').count(),4);
  return observation;
 });
 await check('움직임 줄이기에서 즉시 이동·결과 초점·화면 이탈 후 후속 이동 없음',async()=>{
  await p.emulateMedia({reducedMotion:'reduce'});await fresh('guide',320);await choose(0,'planning');
  assert(await p.evaluate(()=>window.scrollRequests.some(r=>r.id==='result-heading'&&r.arg.behavior==='instant')));assert(await p.locator('#result-heading').evaluate(e=>e===document.activeElement));
  await p.selectOption('#screen','about');await p.waitForTimeout(450);assert(await p.locator('h1').evaluate(e=>e===document.activeElement));await p.emulateMedia({reducedMotion:'no-preference'});
 });
 await check('기존 목록 강조 제거·정책 결과·금액 조건 유지',async()=>{
  for(const v of ['welfare','expanded']){await fresh(v);assert.equal(await p.locator('.highlight').count(),0);}
  await fresh('guide');for(const [i,a]of ['registered','medical-aid','yes','unknown','not-applied'].entries())await choose(i,a);
  assert.equal(await p.locator('#result-heading').innerText(),'주민센터 신청 단계');assert(!(await p.locator('main').innerText()).includes('131만'));assert.equal(await p.locator('[data-question-block]').count(),5);
 });
 await check('1440/390/320 소개·시작·결과 캡처와 가로 넘침 검사',async()=>{
  for(const w of [1440,390,320])for(const v of ['about','guide','result']){
   await fresh(v,w);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await p.screenshot({path:`${capture}/${v}-${w}.png`,fullPage:true});if(w!==320)await p.screenshot({path:`${capture}/${v}-${w}-top.png`});
   if(v==='result'){
    if(w<900)await p.locator('#journey-overview>summary').click();await p.locator('#journey-step-2').click();
    await p.locator('#journey-overview').evaluate(e=>e.scrollIntoView({block:'start',behavior:'instant'}));await p.screenshot({path:`${capture}/journey-expanded-${w}.png`});
    await p.locator('.question-card').evaluate(e=>e.scrollIntoView({block:'start',behavior:'instant'}));await p.screenshot({path:`${capture}/question-and-result-${w}.png`});
   }
  }
 });
 await check('시안 API 호출·저장소 기록·런타임 오류 없음',async()=>{assert.deepEqual(errors,[]);assert.deepEqual(api,[]);assert.equal(await p.evaluate(()=>localStorage.length+sessionStorage.length),0);});
}catch(e){console.error(e);process.exitCode=1;}finally{await fs.writeFile(root+'/revision-3-check-results.json',JSON.stringify({date:'2026-10-14',scope:'독립 시안 회귀 + 기존 P03 UI 읽기 전용 확인. 인증/게시글 실제 API 검증 아님',results,errors,apiCalls:api.length},null,2));await b.close();}
