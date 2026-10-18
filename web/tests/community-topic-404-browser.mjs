// 프로덕션 + 기존 Spring 공개 조회. 실제 표시 DOM을 검사하며 DB 쓰기는 하지 않는다.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BASE_URL??'http://127.0.0.1:8451';
const output=process.env.COMMUNITY_404_ARTIFACT_DIR??await fs.mkdtemp(path.join(os.tmpdir(),'iyum-topic404-'));
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const results=[];
async function check(name,run){try{await run();results.push({name,status:'passed'});console.log('PASS',name);}catch(error){results.push({name,status:'failed',message:error.message});console.error('FAIL',name,error.message);process.exitCode=1;}}
try {
 for(const js of [false,true]) for(const width of [1440,390]) {
  const context=await browser.newContext({javaScriptEnabled:js,viewport:{width,height:900}});const page=await context.newPage();page.setDefaultTimeout(10000);
  await check(`${js?'JS':'무JS'} ${width}px: 직접 진입·새로고침·HTTP404·실제 안내·키보드 복귀`,async()=>{
   const pathname='/community/topics/unknown';
   for(let attempt=0;attempt<2;attempt++){
    const response=attempt?await page.reload():await page.goto(base+pathname+'?topic=HEARING_AID');
    assert.equal(response.status(),404);assert.equal(new URL(page.url()).pathname,pathname);assert.equal(response.headers().location,undefined);
    assert(await page.getByRole('heading',{name:'주제 공간을 찾을 수 없어요.',exact:true}).isVisible());
    assert(await page.getByText('주소를 확인하거나 전체 커뮤니티에서 주제를 선택해 주세요.',{exact:true}).isVisible());
    assert(await page.getByRole('link',{name:'전체 커뮤니티로',exact:true}).isVisible());
    assert((await page.locator('meta[name=robots]').evaluateAll(nodes=>nodes.map(n=>n.content))).some(c=>c.includes('noindex')));
   }
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await page.screenshot({path:path.join(output,`topic-404-${js?'js':'nojs'}-${width}.png`),fullPage:true});
   const link=page.getByRole('link',{name:'전체 커뮤니티로',exact:true});assert.equal(await link.getAttribute('href'),'/community');
   let focused=false;for(let i=0;i<24;i++){await page.keyboard.press('Tab');if(await link.evaluate(el=>el===document.activeElement)){focused=true;break;}}
   assert(focused,'Tab 순회로 전체 커뮤니티 링크에 도달해야 합니다.');await page.keyboard.press('Enter');await page.waitForURL(url=>url.pathname==='/community'&&url.search==='');
   await page.getByRole('heading',{name:'전체 게시글',exact:true}).waitFor();
  });await context.close();
 }
 const context=await browser.newContext({javaScriptEnabled:false,userAgent:'Twitterbot/1.0'});const page=await context.newPage();
 await check('크롤러 UA 무JS HTTP404·본문·복귀 링크',async()=>{
  const response=await page.goto(base+'/community/topics/unknown');assert.equal(response.status(),404);assert(await page.getByRole('heading',{name:'주제 공간을 찾을 수 없어요.'}).isVisible());await page.getByRole('link',{name:'전체 커뮤니티로'}).click();assert.equal(new URL(page.url()).pathname,'/community');
 });
 await check('안내 내부 경로 직접 접근도 HTTP404이며 정상 주제로 위장하지 않음',async()=>{
  const response=await page.goto(base+'/community/topic-not-found');assert.equal(response.status(),404);assert(await page.getByRole('heading',{name:'주제 공간을 찾을 수 없어요.'}).isVisible());
 });
 await check('정상 주제 HTTP200·무JS 제목/소개/게시글·충돌 쿼리 정규화',async()=>{
  const response=await page.goto(base+'/community/topics/hearing-aid?topic=DAILY&type=QUESTION');assert.equal(response.status(),200);
  assert.equal(new URL(page.url()).pathname,'/community/topics/hearing-aid');assert.equal(new URL(page.url()).search,'?type=QUESTION');
  assert(await page.getByRole('heading',{name:'보청기',exact:true}).isVisible());assert(await page.locator('.feed-heading p').isVisible());assert(await page.locator('.feed-heading p').isVisible());assert((await page.locator('.feed-heading p').innerText()).includes('제품 추천은 사용 환경과 개인 경험을 함께 적어 주세요.'));
  // 기존 로컬 검증 데이터가 있는 환경: 실제 표시되는 목록 행으로 확인한다.
  assert(await page.locator('.feed-posts > li').count()>0,'실제 공개 조회에 맞는 기존 보청기 질문 게시글이 필요합니다.');
 });await context.close();
}finally{await browser.close();await fs.writeFile(path.join(output,'topic-404-results.json'),JSON.stringify({mode:'production Next + existing Spring public reads, no DB writes',results},null,2));}
