// 실제 Spring의 기존 공개 데이터만 읽는다. 회원/게시글 생성·수정·삭제 없음.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base=process.env.BASE_URL ?? 'http://127.0.0.1:8443';
const api=process.env.API_BASE_URL ?? 'http://127.0.0.1:8082';
const output=process.env.COMMUNITY_PUBLIC_ARTIFACT_DIR ?? await fs.mkdtemp(path.join(os.tmpdir(),'iyum-community-public-'));
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL ?? 'chrome',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1000},locale:'ko-KR',reducedMotion:'reduce'});
const page=await context.newPage();page.setDefaultTimeout(20000);
const checks=[], errors=[], writes=[];
page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(r.url().startsWith(api)&&r.method()!=='GET'&&r.method()!=='OPTIONS')writes.push(r.method());});
async function check(name,run){await run();checks.push(name);console.log('PASS REAL READ',name);}
async function ready(){await page.waitForFunction(()=>document.querySelector('.community-list-column [aria-busy]')?.getAttribute('aria-busy')==='false');}
async function shot(name){await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:path.join(output,name+'.png'),fullPage:true});}
try {
 const response=await fetch(api+'/api/v1/community/posts?page=0&size=20');assert.equal(response.status,200);const listing=await response.json();assert(listing.items.length>0);
 const post=listing.items.find(p=>p.title.startsWith('[로컬 UI 검증]')) ?? listing.items[0];
 const query='type='+post.type+'&topic='+post.topic;
 await check('기존 DB 목록·초기 HTML·hydration 중복 요청 없음',async()=>{
  let publicReads=0;const count=r=>{if(r.url().includes('/api/v1/community/posts'))publicReads++;};page.on('request',count);
  await page.goto(base+'/community/posts');await ready();assert.equal(await page.locator('.community-post-list > li').count(),listing.items.length);
  await page.waitForTimeout(250);assert.equal(publicReads,0);page.off('request',count);
  const nojs=await browser.newContext({javaScriptEnabled:false});const p=await nojs.newPage();await p.goto(base+'/community/posts');assert(await p.getByRole('link',{name:post.title,exact:true}).isVisible());await nojs.close();
 });
 await check('실제 공개 상세·작성자/본문 초기 HTML·필터 복귀',async()=>{
  await page.goto(base+'/community/posts?'+query);await ready();await page.getByRole('link',{name:post.title,exact:true}).click();await page.locator('.community-post-body').waitFor();
  assert.equal((await page.locator('.community-article h2').innerText()),post.title);await page.getByRole('link',{name:'← 목록으로',exact:true}).click();await ready();assert.equal(new URL(page.url()).search.slice(1),query);
  const nojs=await browser.newContext({javaScriptEnabled:false});const p=await nojs.newPage();await p.goto(base+'/community/posts/'+post.id);assert(await p.locator('.community-post-body').isVisible());assert((await p.locator('.community-article').innerText()).includes(post.author.nickname));await nojs.close();
 });
 await check('실제 범위 밖 페이지·같은 조건의 첫 페이지',async()=>{
  await page.goto(base+'/community/posts?'+query+'&page=99');await page.getByRole('heading',{name:'요청한 페이지를 표시할 수 없어요.'}).waitFor();await page.getByRole('button',{name:'같은 조건의 첫 페이지로'}).click();await ready();assert.equal(new URL(page.url()).search.slice(1),query);assert((await page.locator('.community-post-list > li').count())>0);assert.equal(await page.getByRole('main').getByRole('alert').count(),0);
 });
 await check('실제 404 HTTP 상태·안내·복귀 필터',async()=>{
  const response=await page.goto(base+'/community/posts/999999999?from='+encodeURIComponent('/community/posts?'+query));assert.equal(response.status(),404);await page.getByRole('heading',{name:'게시글을 찾을 수 없어요.'}).waitFor();
  assert.equal(await page.getByRole('link',{name:'목록으로 돌아가기'}).getAttribute('href'),'/community/posts?'+query);
 });
 await check('1440/390/320 실제 목록·상세 레이아웃과 모바일 필터',async()=>{
  for(const width of [1440,390,320]){
   await page.setViewportSize({width,height:width>760?1000:844});await page.goto(base+'/community/posts?'+query);await ready();
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.equal(await page.locator('.community-best').isVisible(),width>760);
   assert(await page.getByRole('link',{name:'글쓰기',exact:true}).isVisible());await shot('real-list-'+width);
   if(width<760){await page.getByRole('button',{name:'필터',exact:true}).click();await page.getByRole('dialog').waitFor();await page.keyboard.press('Escape');assert(await page.getByRole('button',{name:'필터',exact:true}).evaluate(el=>el===document.activeElement));}
   if(width!==320){await page.goto(base+'/community/posts/'+post.id);await page.locator('.community-post-body').waitFor();await shot('real-detail-'+width);}
  }
 });
 await check('공개 비로그인 작성 안내·런타임 오류 없음·실제 쓰기 없음',async()=>{
  await page.goto(base+'/community/posts/create');await page.getByRole('link',{name:'로그인하고 글쓰기'}).waitFor();assert.deepEqual(writes,[]);assert.deepEqual(errors,[]);
 });
 await fs.writeFile(path.join(output,'real-read-results.json'),JSON.stringify({mode:'real existing Spring/PostgreSQL public reads; no data mutations',checks,errors,writeRequests:writes.length},null,2));
 console.log(checks.length+' real public read checks passed');
}finally{await browser.close();}
