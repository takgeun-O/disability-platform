// Real Next UI/P03, with anonymous /me mocked only. No policy/backend mutation.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import vm from 'node:vm';
import ts from 'typescript';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base = process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const output = process.env.INFO_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(), 'iyum-info-'));
await fs.mkdir(output, {recursive:true});
const sourceText = await fs.readFile(new URL('../src/features/HearingAidGuide.tsx',import.meta.url),'utf8');
const source=ts.createSourceFile('p03.tsx',sourceText,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
const names=['Q1_OPTS','Q2_OPTS','Q3_OPTS','Q4_OPTS','Q5_OPTS','RESULT_INFO','getCurrentStep','computeResult','getResultInfo'];
const decls=source.statements.filter(n=>names.includes(n.name?.text)||ts.isVariableStatement(n)&&n.declarationList.declarations.some(d=>names.includes(d.name.text)));
const model=vm.runInNewContext(ts.transpileModule(decls.map(n=>n.getText(source)).join('\n')+'\n;({'+names.join(',')+'})',{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,{});
const browser=await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL??'chrome',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',reducedMotion:'reduce',permissions:['clipboard-read','clipboard-write']});
await context.route('**/api/v1/auth/me',route=>route.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED',message:'로그인이 필요합니다.',fieldErrors:[]}}));
const page=await context.newPage();page.setDefaultTimeout(15000);
const results=[],errors=[];let current;
page.on('pageerror',e=>errors.push(e.message));
const guide='/validation/hearing-aid-health-insurance';
const check=async(name,fn)=>{current=name;await fn();results.push({name,status:'passed'});console.log('PASS',name);};
const fresh=async(width=1440)=>{await page.setViewportSize({width,height:width===1440?1000:844});await page.goto(base+guide);await page.locator('#p03-question-1').waitFor();};
const answer=async(values,keyboard=false)=>{for(const [index,value]of values.entries())if(value!==null){const button=page.locator(`.p03-question[aria-labelledby="p03-question-${index+1}"]`).getByRole('button',{name:model['Q'+(index+1)+'_OPTS'].find(option=>option.value===value).label,exact:true});if(keyboard){await button.focus();await button.press('Enter');}else await button.click();}};
const visibleText=()=>page.locator('.p03-guided').innerText();
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function settled(fn) { const until=Date.now()+6000; while(Date.now()<until){if(await fn())return;await pause(30);}throw Error('UI condition timed out'); }
try {
 await check('소개·복지 초기 HTML과 JavaScript 없는 실제 본문·링크, metadata/noindex',async()=>{
  const nojs=await browser.newContext({javaScriptEnabled:false});const p=await nojs.newPage();
  for(const [route,title,content]of [['/about','정보와 경험이 이어져,','정보·커뮤니티 플랫폼'],['/welfare','복지·지원정보','보청기 건강보험 지원 안내']]){
   const response=await p.goto(base+route);assert.equal(response.status(),200);assert((await p.getByRole('main').innerText()).includes(content));assert((await p.getByRole('heading',{level:1}).innerText()).includes(title));assert((await p.title()).includes('IYUM'));
   assert((await p.locator('meta[name=robots]').getAttribute('content')).includes('noindex'));
   assert(await p.getByRole('main').getByRole('link').count()>1);
  }
  await p.getByRole('link',{name:'안내 보기 →',exact:true}).click();assert(new URL(p.url()).pathname===guide);await nojs.close();
 });
 await check('1440/390/320 메뉴 순서·하위 3개·현재 표시·Escape 초점',async()=>{
  for(const width of [1440,390,320]){
   await page.setViewportSize({width,height:844});await page.goto(base+'/about');
   if(width<900)await page.getByRole('button',{name:'메뉴 열기',exact:true}).click();
   assert.deepEqual(await page.locator('#iyum-navigation > a, #iyum-navigation > div > button').allTextContents(),['IYUM 소개','커뮤니티','정보 찾기 ⌄']);
   assert.equal(await page.locator('#iyum-navigation a[href="/about"]').getAttribute('aria-current'),'page');
   await page.getByRole('button',{name:/정보 찾기/}).click();assert.deepEqual(await page.locator('#iyum-information-menu a > span').allTextContents(),['복지·지원정보','기기 정보','병원·전문기관']);
   await page.keyboard.press('Escape');assert.equal(await page.locator('#iyum-information-menu').isVisible(),false);assert((await page.evaluate(()=>document.activeElement.textContent)).includes('정보 찾기'));
  }
 });
 await check('첫 진입·새로고침 미선택, 답변 후 다음 질문·결과 초점, 단계 색상/토글',async()=>{
  await fresh();assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),0);assert.equal(await page.locator('#p03-result-heading').count(),0);
  const before=await page.locator('#p03-journey-step-1').evaluate(e=>getComputedStyle(e).backgroundColor);
  await answer(['registered'],true);assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-question-2');
  await settled(async()=>await page.locator('#p03-journey-step-1').evaluate(e=>getComputedStyle(e).backgroundColor)!==before);
  for(let i=1;i<=5;i++){await page.locator('#p03-journey-step-'+i).click();assert(await page.locator('#p03-journey-detail-'+i).isVisible());await page.locator('#p03-journey-step-'+i).click();assert.equal(await page.locator('#p03-journey-detail-'+i).isVisible(),false);}
  await page.reload();assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),0);
 });
 await check('28개 실제 결과 조합: 정책 안내·의료급여/모름/이력·금액 조건·링크·센터 질문 범위',async()=>{
  const cases=['planning','assessment','waiting','unknown'].map(a=>[a,null,null,null,null]);
  for(const insurance of ['health','medical-aid','unknown']){
   for(const prescription of ['no','unknown'])cases.push(['registered',insurance,prescription,null,null]);
   for(const history of ['first','previous','unknown']){
    if(insurance==='medical-aid')for(const status of ['approved','waiting','not-applied','unknown'])cases.push(['registered',insurance,'yes',history,status]);
    else cases.push(['registered',insurance,'yes',history,null]);
   }
  }
  assert.equal(cases.length,28);
  for(const values of cases){
   await fresh(390);await answer(values,true);const code=model.computeResult(...values);const info=model.getResultInfo(code,values[1],values[3],values[4]);
   assert.equal(await page.locator('#p03-result-heading').innerText(),info.title);assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-result-heading');
   assert.equal(await page.locator('.p03-action-question blockquote').innerText(),info.contactQuestion);
   assert.equal(await page.locator('.p03-action-prepare .p03-action-main').innerText(),info.nextAction);
   const text=await visibleText();if(info.nextActionNote)assert(text.includes(info.nextActionNote));if(info.secondary)assert(text.includes(info.secondary));
   assert.equal(text.includes('건강보험 지원 금액 안내'),info.hasHealthDetail);
   assert.equal(await page.locator('.p03-extra-questions').count(),['R08','R18'].includes(code)?1:0);
   assert.equal(await page.locator('.p03-action-prepare a,.p03-action-prepare button').count(),0);
   assert.equal(await page.locator('#p03-claim-link').count(),code==='R08'?1:0);
   assert.equal(await page.locator('.p03-note-checklist input:checked').count(),0);
   assert.equal(await page.locator('.p03-action-place a[href*="nhis.or.kr"]').count(),info.onlineLink?1:0);
   if(!info.onlineLink)assert.equal(await page.locator('.p03-action-place a[href*="129.go.kr"]').count(),values[1]==='medical-aid'?1:0);
  }
 });
 await check('개인 체크·양식 복사·복사 실패 직접 선택 대안',async()=>{
  await fresh();await answer(['assessment']);await page.locator('.p03-note-checklist input').first().check();await page.locator('.p03-action-prepare input').nth(1).check();
  const title=await page.locator('#p03-result-heading').innerText();
  await page.getByRole('button',{name:'이 질문 복사하기',exact:true}).click();const memo=await page.evaluate(()=>navigator.clipboard.readText());
  for(const text of ['[x] 남은 검사와 진단 자료를 받는 날짜','[ ] 주민센터에 제출할 시점','[x] 등록 신청용 검사·진단 자료 받기','01. 먼저','04. 이번'])assert(memo.includes(text));
  assert.equal(await page.locator('#p03-result-heading').innerText(),title);
  await page.evaluate(()=>{Object.defineProperty(navigator.clipboard,'writeText',{configurable:true,value:async()=>{throw new Error('clipboard denied');}});});
  await page.getByRole('button',{name:'이 질문 복사하기',exact:true}).click();const area=page.getByRole('textbox',{name:'복사할 질문',exact:true});await area.waitFor();await settled(()=>area.evaluate(e=>e===document.activeElement));assert.equal(await area.inputValue(),memo);assert.equal(await area.evaluate(e=>e.selectionEnd-e.selectionStart),memo.length);
 });
 await check('전체 기준: 절차 우선·목적지 펼침·체크/답변/스크롤/초점 복원',async()=>{
  await fresh();await answer(['assessment']);await page.locator('.p03-note-checklist input').first().check();
  const origin=page.locator('#p03-result-reference');await origin.scrollIntoViewIfNeeded();await origin.focus();const y=await page.evaluate(()=>scrollY);
  await origin.click();assert(await page.locator('.p03-reference').isVisible());assert.equal(await page.locator('.p03-reference > section').first().getAttribute('id'),'steps');assert.equal(await page.locator('#p03-reference-journey-step-1').getAttribute('open'),'');
  await page.getByRole('button',{name:'안내 닫기',exact:true}).click();assert.equal(await page.locator('.p03-note-checklist input:checked').count(),1);assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-result-reference');assert(Math.abs((await page.evaluate(()=>scrollY))-y)<4);
  await page.locator('#p03-sidebar-reference').click();await page.getByRole('link',{name:'지원 대상',exact:true}).click();assert(await page.locator('#eligibility').isVisible());
  await page.getByRole('button',{name:'안내 닫기',exact:true}).click();assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),1);
 });
 await check('R08 청구 연결·직접 청구 설명, 실제 제품 검색/선택 및 추가 질문 유지',async()=>{
  await fresh();await answer(['registered','health','yes','first']);await page.locator('#p03-claim-link').click();assert.equal(await page.locator('#p03-reference-journey-step-4').getAttribute('open'),'');assert.equal(await page.evaluate(()=>document.activeElement.closest('details')?.id),'p03-reference-journey-step-4');
  assert.equal(await page.locator('#p03-reference-journey-direct-claim').getAttribute('open'),'');assert((await page.locator('#p03-reference-journey-direct-claim').innerText()).includes('무엇을 준비하나요?'));
  await page.getByRole('button',{name:'안내 닫기',exact:true}).click();
  const details=page.locator('#p03-result-registry-product');
  // The existing RegistryCheck form stays functional, not a prototype link.
  await page.locator('#p03-result-registry-product > summary').click();
  const input=page.locator('.p03-guided .p03-registry-search input');await input.fill('포낙');await page.locator('.p03-guided .p03-registry-search button').click();
  assert(await page.locator('.p03-guided .p03-registry-results button').count()>0);await page.locator('.p03-guided .p03-registry-results button').first().click();assert(await page.locator('.p03-guided .p03-registry-selected').isVisible());
  await page.locator('.p03-extra-questions > summary').click();assert(await page.locator('.p03-guided .p03-registry-stale').isVisible());assert(!(await page.locator('.p03-extra-questions').innerText()).includes('선택한 제품이 반영됐어요.')); // Existing snapshot cutoff (2026-09-30), not changed by design work.
  assert.equal(await page.locator('.p03-guided a[href*="localhost"]').count(),0);assert(await details.isVisible());
 });
 await check('답변 수정은 이후 답변/체크 초기화, 같은 코드라도 서로 다른 이력 메모 재사용 없음',async()=>{
  await fresh();await answer(['registered','medical-aid','yes','first','not-applied']);await page.locator('.p03-note-checklist input').first().check();
  await page.getByRole('button',{name:'변경',exact:true}).nth(3).focus();await page.keyboard.press('Enter');assert.equal(await page.locator('#p03-result-heading').count(),0);assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-question-4');
  await page.getByRole('button',{name:'기억나지 않아요',exact:true}).click();await page.getByRole('button',{name:'아직 신청하지 않았어요',exact:true}).click();assert.equal(await page.locator('.p03-note-checklist input:checked').count(),0);
  assert((await visibleText()).includes('기록이 있다면'));
  await page.getByRole('button',{name:'변경',exact:true}).first().click();assert.equal(await page.locator('#p03-result-heading').count(),0);assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),0);
 });
 await check('안내·목록·소개 키보드 본문 바로가기: 현재 화면/답변/체크 유지',async()=>{
  for(const route of ['/about','/welfare',guide]){
   await page.goto(base+route);if(route===guide){await answer(['assessment']);await page.locator('.p03-note-checklist input').first().check();}
   const before=await page.getByRole('main').innerText();await page.locator('.iyum-skip-link').focus();await page.keyboard.press('Enter');
   assert.equal(new URL(page.url()).pathname,route);assert.equal(await page.evaluate(()=>document.activeElement.id),'main-content-start');assert.equal(await page.getByRole('main').innerText(),before);
   if(route===guide)assert.equal(await page.locator('.p03-note-checklist input:checked').count(),1);
  }
 });
 await check('기존 query/hash 직접 진입 및 한쪽/양쪽 기준 보존',async()=>{
  for(const hash of ['full-criteria','registration-help','eligibility','benefit','steps','center-product-check','before-buying','sources','one-or-two']){
   await page.goto(base+guide+'?from=home#'+hash);await pause(150);assert(new URL(page.url()).search==='?from=home');
   assert(await page.locator(hash==='one-or-two'?'#bilateral-heading':'#'+hash).isVisible());assert.equal(await page.locator('.p03-option[aria-pressed=true]').count(),0);
  }
  assert((await page.locator('dialog[open]').innerText()).includes('19세 미만'));
  await fresh();await answer(['assessment']);await page.locator('.p03-note-checklist input').first().check();
  await page.locator('#p03-sidebar-bilateral').click();assert(await page.locator('.p03-reference').isVisible());assert(await page.locator('#benefit input[value=bilateral]').isChecked());assert((await page.locator('#benefit').innerText()).includes('19세 미만'));
  await page.getByRole('button',{name:'안내 닫기',exact:true}).click();assert.equal(await page.locator('.p03-note-checklist input:checked').count(),1);assert.equal(await page.evaluate(()=>document.activeElement.id),'p03-sidebar-bilateral');
 });
 await check('기본 목록은 안내 1건·필터/가상 공고 없음, 홈 앵커와 준비 중 페이지',async()=>{
  await page.goto(base+'/welfare');assert.equal(await page.locator('main article').count(),1);assert.equal(await page.locator('main input,main select').count(),0);assert(!(await page.getByRole('main').innerText()).includes('가상 공고'));
  await page.goto(base+'/#iyum-about');await page.locator('#iyum-about a[href="/about"]').click();await page.waitForURL('**/about');
  for(const route of ['/devices','/hospitals']){await page.goto(base+route);assert((await page.getByRole('main').innerText()).includes('준비 중'));}
 });
 await check('1440/390/320 실제 서비스 캡처·가로 넘침·미구현 시안 요소 비노출',async()=>{
  for(const width of [1440,390,320]){
   await page.setViewportSize({width,height:width===1440?1000:844});
   for(const [route,name]of [['/about','about'],['/welfare','welfare'],[guide,'guide-start']]){
    await page.goto(base+route);await pause(200);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width,name+width);
    assert.equal(await page.locator('main a[href*="localhost"]').count(),0);assert(!(await page.getByRole('main').innerText()).includes('검토 시안'));await page.screenshot({path:path.join(output,`${name}-${width}.png`),fullPage:true});
   }
   await answer(['assessment']);await page.locator('.p03-note-checklist input').first().check();await page.screenshot({path:path.join(output,`guide-result-${width}.png`),fullPage:true});
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
   await page.locator('#p03-result-reference').click();await page.screenshot({path:path.join(output,`guide-reference-${width}.png`),fullPage:true});
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width);
  }
 });
 await check('새 소개·복지 메뉴의 작성 이탈 취소/승인과 키보드 초점 복귀',async()=>{
  const ctx=await browser.newContext({viewport:{width:390,height:844}});
  await ctx.route('**/api/v1/auth/me',route=>route.fulfill({json:{memberId:123,email:'review@example.test',nickname:'검토회원',role:'USER'}}));
  const p=await ctx.newPage();
  try{for(const destination of ['/about','/welfare']){
   await p.goto(base+'/community/posts/create');await p.getByPlaceholder('제목을 입력하세요.').fill('이탈 보호 확인');await p.getByPlaceholder('내용을 입력하세요.').fill('보존해야 할 초안');
   await p.getByRole('button',{name:'메뉴 열기',exact:true}).click();
   if(destination==='/welfare')await p.getByRole('button',{name:/정보 찾기/}).click();
   const link=p.locator('#iyum-navigation a[href="'+destination+'"]');await link.focus();await p.keyboard.press('Enter');
   await p.getByRole('button',{name:'계속 작성',exact:true}).click();assert.equal(new URL(p.url()).pathname,'/community/posts/create');assert.equal(await p.getByPlaceholder('내용을 입력하세요.').inputValue(),'보존해야 할 초안');assert(await link.evaluate(e=>e===document.activeElement));
   await p.keyboard.press('Enter');await p.getByRole('button',{name:'나가기 — 입력한 내용이 사라집니다',exact:true}).click();await p.waitForURL(base+destination);assert(await p.getByRole('heading',{level:1}).isVisible());
  }}finally{await ctx.close();}
 });
 await check('일반 모션은 답변 뒤 부드러운 이동, 모바일 절차 토글/리사이즈 접근 유지',async()=>{
  await fresh(390);await page.getByRole('button',{name:'전체 절차 펼치기',exact:true}).click();assert(await page.locator('#p03-journey-step-1').isVisible());
  await page.locator('#p03-journey-step-1').focus();await page.keyboard.press('Enter');assert(await page.locator('#p03-journey-detail-1').isVisible());
  await page.getByRole('button',{name:'전체 절차 접기',exact:true}).click();await page.setViewportSize({width:1440,height:1000});assert(await page.locator('#p03-journey-step-1').isVisible());
  await page.emulateMedia({reducedMotion:'no-preference'});await page.evaluate(()=>{window.infoScrolls=[];window.addEventListener('scroll',()=>window.infoScrolls.push(scrollY));});
  await answer(['assessment']);await pause(700);assert(new Set(await page.evaluate(()=>window.infoScrolls.map(y=>Math.round(y)))).size>2);
  await page.emulateMedia({reducedMotion:'reduce'});
 });
 await check('런타임 오류 없음',async()=>assert.deepEqual(errors,[]));
} catch(error) { results.push({name:current,status:'failed',message:error.message});console.error('FAIL',current,error.message);process.exitCode=1; }
finally {await fs.writeFile(path.join(output,'results.json'),JSON.stringify({base,scope:'Next service UI; anonymous /me mock; no Spring writes',results,errors},null,2));await browser.close();}
