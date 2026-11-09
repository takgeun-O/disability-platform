// Production Next + controlled SSR/browser API. No real accounts or DB writes.
import http from 'node:http'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8451',api='http://127.0.0.1:8082'
const output=process.env.COMMUNITY_ARTIFACT_DIR??'/private/tmp/iyum-feed-browser'
await fs.mkdir(output,{recursive:true})
const member={memberId:123,email:'example@example.test',nickname:'검증 회원',role:'USER'}
const failure=code=>({code,message:'private',fieldErrors:[]})
const stamp='2026-10-18T00:00:00.123456Z'
const make=id=>({id,type:'QUESTION',topic:'HEARING_AID',title:`[검증 예시] 경험과 질문 ${id}`,content:'본문 내용을 보존합니다.\n두 번째 줄',contentPreview:'조용한 곳과 시끄러운 곳에서 사용한 경험을 나눕니다. <b>HTML이 아닌 일반 텍스트</b>',likeCount: 0, liked: false, author:{memberId:123,nickname:'검증회원'},createdAt:stamp,updatedAt:stamp,commentCount:0})
let posts=[],comments=[],serverCalls=[]
function response(u){
 if(u.pathname.endsWith('/comments'))return{status:200,json:{items:comments,hasNext:false,nextCursor:null}}
 if(u.pathname.endsWith('/feed')){
  let rows=posts.filter(p=>(!u.searchParams.get('type')||p.type===u.searchParams.get('type'))&&(!u.searchParams.get('topic')||p.topic===u.searchParams.get('topic')))
  if(u.searchParams.has('afterId'))rows=rows.filter(p=>p.id<Number(u.searchParams.get('afterId')))
  const items=rows.slice(0,20),hasNext=rows.length>20
  return{status:200,json:{items,hasNext,nextCursor:hasNext?{createdAt:items.at(-1).createdAt,postId:items.at(-1).id}:null}}
 }
 const p=posts.find(p=>p.id===Number(u.pathname.split('/').at(-1)))
 if(!p)return{status:404,json:failure('POST_NOT_FOUND')}
 const detail={...p};delete detail.commentCount;delete detail.contentPreview
 return{status:200,json:detail}
}
const server=http.createServer((req,res)=>{const u=new URL(req.url,'http://mock');serverCalls.push(u.pathname+u.search);assert.equal(req.headers.cookie,undefined);assert.equal(req.headers.authorization,undefined);const r=response(u);res.writeHead(r.status,{'Content-Type':'application/json'});res.end(JSON.stringify(r.json))})
await new Promise(r=>server.listen(8856,'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn){for(let i=0;i<120;i++){if(await fn())return;await pause(50)}throw Error('condition not reached')}
async function check(name,run){try{await run();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.stack});console.log('FAIL',name,e.message);const p=browser.contexts().at(-1)?.pages()[0];if(p){console.log('DIAGNOSTIC',await p.evaluate(()=>({path:location.pathname,rows:document.querySelectorAll('[data-feed-id]').length,count:document.querySelector('[data-feed-id=\"955\"] .post-comment-link')?.textContent,error:document.querySelector('.community-error')?.textContent,status:document.querySelector('[role=status]')?.textContent})).catch(()=>({})));await p.screenshot({path:output+'/failure-'+results.length+'.png'}).catch(()=>{})}process.exitCode=1}}
async function fixture({count=90,auto=false,path='/community',js=true,width=1440,authenticated=true,motion="reduce"}={}){
 posts=Array.from({length:count},(_,i)=>make(1000-i));comments=[];serverCalls=[]
 const ctx=await browser.newContext({javaScriptEnabled:js,viewport:{width,height:900},reducedMotion:motion})
 if(!auto)await ctx.addInitScript(()=>{delete window.IntersectionObserver})
 const p=await ctx.newPage();p.setDefaultTimeout(7000)
 const state={calls:[],get:null,write:null,errors:[]}
 p.on('pageerror',e=>state.errors.push(e.message))
 await p.route(u=>u.origin===api&&u.pathname.startsWith('/api/v1/'),async r=>{
  const u=new URL(r.request().url()),method=r.request().method();state.calls.push({url:u.href,path:u.pathname,method})
  if(u.pathname.endsWith('/me'))return r.fulfill(authenticated?{json:member}:{status:401,json:failure('AUTHENTICATION_REQUIRED')})
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'test-only'}})
  if(method==='GET')return state.get?state.get(r,u):r.fulfill(response(u))
  if(state.write)return state.write(r,u)
  assert.equal(r.request().headers()['x-csrf-token'],'test-only')
  if(u.pathname.endsWith('/comments')&&method==='POST'){
   const post=posts.find(p=>p.id===Number(u.pathname.split('/').at(-2)));post.commentCount++
   comments.push({ contentUpdatedAt: null,likeCount:0,liked:false,commentId:1,content:r.request().postDataJSON().content,author:post.author,createdAt:stamp,updatedAt:stamp})
   return r.fulfill({status:201,json:{commentId:1}})
  }
  if(u.pathname.includes('/comments/')){if(method==='DELETE'){comments=[];posts.find(p=>p.id===Number(u.pathname.split('/').at(-3))).commentCount--}else comments[0].content=r.request().postDataJSON().content;return r.fulfill({status:204})}
  if(method==='DELETE'){posts=posts.filter(p=>p.id!==Number(u.pathname.split('/').at(-1)));return r.fulfill({status:204})}
  if(method==='PUT'){Object.assign(posts.find(p=>p.id===Number(u.pathname.split('/').at(-1))),r.request().postDataJSON());return r.fulfill({status:204})}
  return r.fulfill({status:500,json:failure('UNEXPECTED')})
 })
 await p.goto(base+path);await p.locator('.feed-posts').waitFor({state:'attached'})
 if(js){await p.waitForFunction(()=>!!history.state?.iyumFeedEntry);if(count)await p.waitForFunction(()=>(document.querySelector('.community-empty')||document.querySelector('.post-like-count'))&&document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false')}
 return{ctx,p,state}
}
const rows=p=>p.locator('[data-feed-id]')
async function more(p,n){for(let i=1;i<n;i++){await p.getByRole('button',{name:'게시글 더 보기',exact:true}).evaluate(b=>b.click());await until(async()=>await rows(p).count()>=20*(i+1))}}
async function enter(p,id){const el=p.locator(`[data-feed-id="${id}"]`);await el.evaluate(e=>window.scrollBy(0,e.getBoundingClientRect().top-120));const y=await p.evaluate(()=>scrollY);await el.locator('h3 a').click();await p.locator('.community-back').waitFor();return y}
try{
 for(const width of [1440,390]) await check(`normal motion ${width}: first painted detail at top; 60-row link/back restoration`,async()=>{
  const {ctx,p}=await fixture({width,motion:"no-preference"})
  try{
   await more(p,3)
   const selected=posts.find(x=>x.id===945);selected.content="긴 상세 본문으로 처음 표시 위치 확인.\n".repeat(150)
   for(const mode of ['link','back']){
    const row=p.locator('[data-feed-id="945"]');await row.evaluate(el=>scrollBy({top:el.getBoundingClientRect().top-150,behavior:'instant'}))
    const before=await p.evaluate(()=>scrollY);assert(before>1000)
    await p.evaluate(()=>{window.__detailFrames=[];window.__watch=true;function tick(){if(!window.__watch)return;if(location.pathname.includes('/posts/945')&&document.querySelector('.community-post-body'))window.__detailFrames.push(scrollY);requestAnimationFrame(tick)}requestAnimationFrame(tick)})
    await row.locator('h3 a').click();await p.locator('.community-post-body').waitFor();await p.waitForFunction(()=>window.__detailFrames.length>=4)
    const frames=await p.evaluate(()=>{window.__watch=false;return window.__detailFrames});assert(frames.every(y=>y===0),JSON.stringify(frames))
    assert.equal(await p.evaluate(()=>getComputedStyle(document.documentElement).scrollBehavior),'auto')
    await p.screenshot({path:output+`/detail-instant-${width}-${mode}.png`})
    await p.evaluate(()=>{location.hash='comments-title'});await p.waitForFunction(()=>document.getElementById('comments-title').getBoundingClientRect().top<innerHeight)
    const y=await p.evaluate(()=>scrollY);assert(y>0)
    await p.locator('#comments-refresh').click();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
    assert(Math.abs(await p.evaluate(()=>scrollY)-y)<4)
    // Hash history is not a page-return entry; remove only this explicit test hash.
    await p.evaluate(()=>history.replaceState(history.state,'',location.pathname+location.search))
    if(mode==='link')await p.locator('.community-back').click();else {await p.goBack();if(new URL(p.url()).pathname.includes('/posts/945'))await p.goBack()}
    await until(async()=>await rows(p).count()===60)
    await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-before)<12)
   }
  }finally{await ctx.close()}
 })
 await check('SSR/noJS visible first20, plain preview, count0, raw cursor next link',async()=>{
  const{ctx,p}=await fixture({js:false});assert.equal(await rows(p).count(),20);assert(await rows(p).first().isVisible());assert.equal(await rows(p).first().locator('.post-comment-link').innerText(),'댓글 0');assert.equal(await rows(p).first().locator('b').count(),0);assert((await rows(p).first().innerText()).includes('<b>'))
  assert.equal(serverCalls.filter(s=>s.includes('/feed')).length,1);assert(!serverCalls.some(s=>/\/\d/.test(s)))
  const next=p.getByRole('link',{name:'다음 게시글 목록',exact:true});assert(await next.isVisible());const url=new URL(await next.getAttribute('href'),base);assert.equal(url.searchParams.get('afterCreatedAt'),stamp);assert.equal(url.searchParams.get('afterId'),'981');await next.click();assert.equal(await rows(p).first().getAttribute('data-feed-id'),'980');await p.screenshot({path:output+'/feed-nojs.png',fullPage:true});await ctx.close()
 })
 await check('manual duplicate click /3 segments / no forced focus-scroll / no per-post GET',async()=>{
  const{ctx,p,state}=await fixture();await more(p,3);assert.equal(await rows(p).count(),60);await p.getByRole('button',{name:'게시글 더 보기',exact:true}).focus();const y=await p.evaluate(()=>scrollY);await p.keyboard.press('Enter');await p.keyboard.press('Enter');await until(async()=>await rows(p).count()===80);assert.equal(await p.evaluate(()=>scrollY),y);assert.equal(await p.evaluate(()=>document.activeElement.textContent),'게시글 더 보기');assert.equal(state.calls.filter(c=>c.path.endsWith('/feed')).length,4);assert.equal(new URL(state.calls.filter(c=>c.path.endsWith('/feed'))[0].url).searchParams.has('afterId'),false);assert(state.calls.filter(c=>c.path.endsWith('/feed')).slice(1).every(c=>new URL(c.url).searchParams.get('afterCreatedAt')===stamp));assert(!state.calls.some(c=>/\/posts\/\d/.test(c.path)));await ctx.close()
 })
 await check('observer loads next once; terminal stops',async()=>{
  const{ctx,p,state}=await fixture({auto:true,count:23});await p.locator('.feed-end').scrollIntoViewIfNeeded();await until(async()=>await rows(p).count()===23);await p.getByText('마지막 게시글까지 확인했어요.').waitFor();await pause(300);assert.equal(state.calls.filter(c=>c.path.endsWith('/feed')).length,2);assert.equal(state.calls.filter(c=>c.path.endsWith('/feed')&&new URL(c.url).searchParams.has('afterId')).length,1);await ctx.close()
 })
 await check('append failure preserves20/cursor, no auto retry; explicit retry',async()=>{
  const{ctx,p,state}=await fixture({auto:true});state.get=(r,u)=>u.pathname.endsWith('/feed')?r.fulfill({status:503,json:failure('SERVER_ERROR')}):r.fulfill(response(u));await p.locator('.feed-end').scrollIntoViewIfNeeded();await p.locator('.community-error').waitFor();const y=await p.evaluate(()=>scrollY);await p.mouse.wheel(0,300);await pause(350);assert.equal(state.calls.filter(c=>c.path.endsWith('/feed')).length,2);assert.equal(await rows(p).count(),20);assert(Math.abs(await p.evaluate(()=>scrollY)-y)<350);const failedCursor=new URL(state.calls.filter(c=>c.path.endsWith('/feed')).at(-1).url).search;state.get=null;await p.getByRole('button',{name:'다시 시도',exact:true}).click();await until(async()=>await rows(p).count()>=40);assert.equal(new URL(state.calls.filter(c=>c.path.endsWith('/feed')).at(-1).url).search,failedCursor);await ctx.close()
 })
 for(const width of [1440,390,320])await check(`manual feed refresh toast ${width}: no inline success, no layout shift, timer replacement`,async()=>{
  const{ctx,p,state}=await fixture({width});try{
   await p.clock.install();assert.equal(await p.locator('.comment-success-toast').count(),0)
   const button=p.getByRole('button',{name:'최신 목록 새로고침',exact:true});await button.focus()
   const before=await rows(p).first().boundingBox();const y=await p.evaluate(()=>scrollY)
   let release,reach;const reached=new Promise(r=>reach=r)
   state.get=async(r,u)=>{reach();await new Promise(r=>release=r);await r.fulfill(response(u))}
   await p.keyboard.press('Enter');await reached;assert.equal(await p.locator('.comment-success-toast').count(),0)
   assert(Math.abs((await rows(p).first().boundingBox()).y-before.y)<2)
   release();const toast=p.locator('.comment-success-toast');await toast.waitFor()
   assert.equal(await p.getByText('게시글 목록을 새로고침했습니다.',{exact:true}).count(),1)
   assert(await button.evaluate(e=>document.activeElement===e));assert(Math.abs(await p.evaluate(()=>scrollY)-y)<2)
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.screenshot({path:`${output}/feed-refresh-toast-${width}.png`})
   await p.clock.runFor(4000);state.get=null;const oldToast=await toast.elementHandle();await button.click();await p.waitForFunction(e=>!e.isConnected,oldToast);await toast.waitFor()
   await p.clock.runFor(1500);assert.equal(await toast.count(),1)
   await p.clock.runFor(3750);assert.equal(await toast.count(),0)
  }finally{await ctx.close()}
 })
 await check('failed feed refresh keeps error and never announces success',async()=>{
  const{ctx,p,state}=await fixture();try{
   state.get=r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})
   await p.getByRole('button',{name:'최신 목록 새로고침',exact:true}).click();await p.locator('.community-error').waitFor()
   assert.equal(await p.locator('.comment-success-toast').count(),0);assert.equal(await rows(p).count(),20)
   state.get=null;await p.getByRole('button',{name:'다시 시도',exact:true}).click();await p.getByText('게시글 목록을 새로고침했습니다.',{exact:true}).waitFor()
   assert.equal(await p.locator('.community-error').count(),0)
  }finally{await ctx.close()}
 })
 await check('latest refresh failure retries latest first segment, not reconciliation',async()=>{
  const{ctx,p,state}=await fixture();await more(p,3);posts.unshift(make(1001));
  state.get=(r,u)=>u.pathname.endsWith('/feed')?r.fulfill({status:503,json:failure('SERVER_ERROR')}):r.fulfill(response(u));
  await p.getByRole('button',{name:'최신 목록 새로고침',exact:true}).click();await p.locator('.community-error').waitFor();assert.equal(await rows(p).count(),60);
  const at=state.calls.length;state.get=null;await p.getByRole('button',{name:'다시 시도',exact:true}).click();await until(async()=>await rows(p).first().getAttribute('data-feed-id')==='1001');
  assert.equal(await rows(p).count(),20);const calls=state.calls.slice(at).filter(c=>c.path.endsWith('/feed'));assert.equal(calls.length,1);assert.equal(new URL(calls[0].url).searchParams.has('afterId'),false);await ctx.close()
 })
 for(const attempt of [1,2,3]) await check(`reconcile failure retries accumulated range and anchor without inserting new top / ${attempt}`,async()=>{
  const{ctx,p,state}=await fixture();await more(p,3);await enter(p,955);
  await p.locator('#comment-draft').fill('범위 갱신 실패 회귀');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await p.getByText('댓글을 등록했습니다.',{exact:true}).waitFor();posts.unshift(make(1001));
  let release, reached
  const arrived=new Promise(resolve=>reached=resolve), gate=new Promise(resolve=>release=resolve)
  state.get=async(r,u)=>{if(u.pathname.endsWith('/feed')){reached();await gate;await r.fulfill({status:503,json:failure('SERVER_ERROR')})}else await r.fulfill(response(u))}
  const failed=p.waitForResponse(r=>new URL(r.url()).origin===api&&new URL(r.url()).pathname==='/api/v1/community/posts/feed'&&r.request().method()==='GET'&&r.status()===503)
  await p.locator('.community-back').click();await arrived
  await p.waitForFunction(()=>document.querySelectorAll('[data-feed-id]').length===60&&Math.abs(document.querySelector('[data-feed-id="955"]').getBoundingClientRect().top-120)<10)
  const before=await p.locator('[data-feed-id="955"]').evaluate(e=>e.getBoundingClientRect().top)
  release();await (await failed).finished()
  await p.waitForFunction(()=>document.querySelector('.community-error')&&document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false')
  const after=await p.locator('[data-feed-id="955"]').evaluate(e=>e.getBoundingClientRect().top)
  await fs.writeFile(output+`/anchor-${attempt}.json`,JSON.stringify({before,after,rows:await rows(p).count()},null,2))
  await p.screenshot({path:output+`/anchor-error-${attempt}.png`})
  assert.equal(await rows(p).count(),60);assert(Math.abs(after-120)<10,`error anchor: ${after}`);
  let releaseRetry, reachedRetry
  const retryArrived=new Promise(resolve=>reachedRetry=resolve), retryGate=new Promise(resolve=>releaseRetry=resolve)
  state.get=async(r,u)=>{if(u.pathname.endsWith('/feed')){reachedRetry();await retryGate}await r.fulfill(response(u))}
  await p.getByRole('button',{name:'다시 시도',exact:true}).evaluate(b=>b.click());await retryArrived
  await p.waitForFunction(()=>!document.querySelector('.community-error')&&document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='true')
  assert(Math.abs(await p.locator('[data-feed-id="955"]').evaluate(e=>e.getBoundingClientRect().top)-120)<10)
  // A new user scroll during retry must replace the old retry-click anchor.
  if(attempt===3)await p.mouse.wheel(0,180)
  if(attempt===3)await p.waitForFunction(()=>document.querySelector('[data-feed-id="955"]').getBoundingClientRect().top<0)
  const retryAnchor=await p.locator('[data-feed-id="955"]').evaluate(e=>e.getBoundingClientRect().top)
  releaseRetry()
  await p.waitForFunction(()=>document.querySelector('[data-feed-id="955"] .post-comment-link')?.textContent==='댓글 1'&&document.querySelector('.feed-posts')?.getAttribute('aria-busy')==='false')
  assert.equal(await rows(p).count(),60);assert.equal(await rows(p).first().getAttribute('data-feed-id'),'1000')
  await p.waitForFunction(offset=>Math.abs(document.querySelector('[data-feed-id="955"]').getBoundingClientRect().top-offset)<10,retryAnchor)
  await fs.writeFile(output+`/retry-anchor-${attempt}.json`,JSON.stringify({expected:retryAnchor,actual:await p.locator('[data-feed-id="955"]').evaluate(e=>e.getBoundingClientRect().top)},null,2))
  await ctx.close()
 })
 await check('slow append then topic navigation cannot overwrite current filter',async()=>{
  const{ctx,p,state}=await fixture();let release,reached=false;state.get=async(r,u)=>{if(u.pathname.endsWith('/feed')){reached=true;await new Promise(r=>release=r);try{await r.fulfill(response(u))}catch{/* navigation cancels GET */}}else await r.fulfill(response(u))};await p.getByRole('button',{name:'게시글 더 보기'}).evaluate(b=>b.click());await until(()=>reached);await p.locator('.feed-tabs a').filter({hasText:'인공와우'}).click();await p.getByRole('heading',{name:'인공와우',exact:true}).waitFor();release();await pause(250);assert.equal(await rows(p).count(),0);assert.equal(new URL(p.url()).pathname,'/community/topics/cochlear-implant');await ctx.close()
 })
 for(const mode of ['link','back-forward'])await check('60 loaded detail return anchor / '+mode,async()=>{
  const{ctx,p}=await fixture();await more(p,3);const y=await enter(p,955);if(mode==='link')await p.locator('.community-back').click();else await p.goBack();await until(async()=>await rows(p).count()===60);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<8);if(mode==='back-forward'){await p.goForward();await p.locator('.community-back').waitFor();await p.goBack();await until(async()=>await rows(p).count()===60);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<8)}await ctx.close()
 })
 await check('same URL separate history entries retain separate lengths/positions',async()=>{
  const{ctx,p}=await fixture();await more(p,3);const y=await enter(p,955);await p.locator('.iyum-header').getByRole('link',{name:'커뮤니티',exact:true}).click();await until(async()=>await rows(p).count()===20);await p.goBack();await p.locator('.community-back').waitFor();await p.goBack();await until(async()=>await rows(p).count()===60);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<8);await ctx.close()
 })
 for(const mode of ['link','back'])await check('comment POST refreshes60 counts without new top / '+mode,async()=>{
  const{ctx,p,state}=await fixture();await more(p,3);const y=await enter(p,955);await p.locator('#comment-draft').fill('서버 값 검증');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await p.getByText('댓글을 등록했습니다.',{exact:true}).waitFor();posts.unshift(make(1001));if(mode==='link')await p.locator('.community-back').click();else await p.goBack();await until(async()=>await p.locator('[data-feed-id="955"] .post-comment-link').textContent()==='댓글 1');assert.equal(await rows(p).count(),60);assert.equal(await rows(p).first().getAttribute('data-feed-id'),'1000');await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<8);assert(state.calls.filter(c=>c.path.endsWith('/feed')).length<=7);await ctx.close()
 })
 await check('late comment success after return aborts stale feed read and refreshes60',async()=>{
  const{ctx,p,state}=await fixture();await more(p,3);await enter(p,955);let release;state.write=async(r)=>{await new Promise(r=>release=r);posts.find(p=>p.id===955).commentCount=4;await r.fulfill({status:201,json:{commentId:1}})};await p.locator('#comment-draft').fill('늦은 저장');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await until(()=>release);await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await until(async()=>await rows(p).count()===60);release();await until(async()=>await p.locator('[data-feed-id="955"] .post-comment-link').textContent()==='댓글 4');assert.equal(await rows(p).count(),60);assert.equal(new URL(p.url()).pathname,'/community');await ctx.close()
 })
 await check('post deletion restores neighbor and remaining59 / no stale deleted card',async()=>{
  const{ctx,p}=await fixture();await more(p,3);await enter(p,955);await p.getByRole('button',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await until(async()=>await rows(p).count()===59);assert.equal(await p.locator('[data-feed-id="955"]').count(),0);await until(async()=>Math.abs(await p.locator('[data-feed-id="954"]').evaluate(e=>e.getBoundingClientRect().top)-120)<20);await ctx.close()
 })
 await check('legacy page and topic conflict normalize; filter links reset cursor',async()=>{
  const{ctx,p}=await fixture({path:'/community/topics/hearing-aid?topic=COCHLEAR_IMPLANT&type=QUESTION&page=99'});assert.equal(new URL(p.url()).search,'?type=QUESTION');assert.equal(await rows(p).count(),20);await p.locator('.feed-types').getByRole('link',{name:'일반',exact:true}).click();await p.getByText('아직 표시할 게시글이 없어요.').waitFor();assert.equal(new URL(p.url()).search,'?type=GENERAL');await p.locator('.feed-tabs').getByRole('link',{name:'전체',exact:true}).click();await p.getByRole('heading',{name:'커뮤니티',exact:true}).waitFor();assert.equal(new URL(p.url()).pathname,'/community');await ctx.close()
 })

 await check('topic create defaults / dirty cancel / valid201 detail and safe list return',async()=>{
  const{ctx,p,state}=await fixture({path:'/community/topics/hearing-aid?type=QUESTION'});
  await p.getByRole('link',{name:'＋ 글쓰기',exact:true}).click();await p.getByLabel('제목',{exact:true}).waitFor();
  assert.equal(await p.getByLabel('주제',{exact:true}).inputValue(),'HEARING_AID');assert.equal(await p.getByLabel('글 유형',{exact:true}).inputValue(),'QUESTION');
  await p.getByLabel('제목',{exact:true}).fill('[검증 예시] 새 글');await p.getByLabel('내용',{exact:true}).fill('입력을 보존합니다.');
  await p.getByRole('button',{name:'취소',exact:true}).click();await p.getByRole('button',{name:'계속 작성',exact:true}).click();assert.equal(await p.getByLabel('제목',{exact:true}).inputValue(),'[검증 예시] 새 글');
  state.write=async r=>{assert.equal(r.request().method(),'POST');assert.equal(r.request().headers()['x-csrf-token'],'test-only');posts.unshift({...make(1001),...r.request().postDataJSON()});await r.fulfill({status:201,json:{id:1001}})};
  await p.getByRole('button',{name:'등록',exact:true}).click();await p.locator('.community-post-body').waitFor();assert.equal(new URL(p.url()).pathname,'/community/posts/1001');assert.equal(await p.locator('.community-back').getAttribute('href'),'/community/topics/hearing-aid?type=QUESTION');assert.equal(state.calls.filter(c=>c.method==='POST').length,1);await ctx.close()
 })
 await check('refresh without memory starts fresh20; keyboard final append retains control',async()=>{
  const{ctx,p}=await fixture({count:23});const moreButton=p.getByRole('button',{name:'게시글 더 보기',exact:true});await moreButton.focus();await p.keyboard.press('Enter');await until(async()=>await rows(p).count()===23);assert.equal(await p.evaluate(()=>document.activeElement.textContent),'마지막 게시글까지 확인했어요.');await p.reload();assert.equal(await rows(p).count(),20);await ctx.close()
 })
 await check('mobile category selection visible with no horizontal scrolling; hidden links not tabbable',async()=>{
  const{ctx,p}=await fixture({width:320,path:'/community/topics/welfare-life?type=QUESTION'});
  for(const width of [320,390]){
   await p.setViewportSize({width,height:850});const tabs=p.locator('.feed-tabs');assert(await tabs.getByRole('link',{name:'전체',exact:true}).isVisible());assert(await tabs.getByRole('link',{name:'복지·생활',exact:true}).isVisible());
   assert(await tabs.evaluate(e=>e.scrollWidth<=e.clientWidth));const toggle=p.locator('.feed-expand');await toggle.focus();await p.keyboard.press('Enter');assert.equal(await tabs.getByRole('link').count(),8);for(const link of await tabs.getByRole('link').all())assert(await link.isVisible());
   await p.keyboard.press('Escape');assert(await toggle.evaluate(e=>e===document.activeElement));await p.keyboard.press('Shift+Tab');assert(await p.evaluate(()=>!!document.activeElement.closest('.feed-tabs')&&document.activeElement.getBoundingClientRect().width>0));
   const menu=p.getByRole('button',{name:'메뉴 열기',exact:true});await menu.click();assert(await p.locator('.iyum-header').getByRole('button',{name:'로그아웃',exact:true}).isVisible());await p.keyboard.press('Escape');assert(await menu.evaluate(e=>e===document.activeElement));assert.equal(await menu.getAttribute('aria-expanded'),'false');
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  }await ctx.close()
 })
 await check('mobile header anonymous/long nickname, single-row logo SVG toggle and keyboard panel',async()=>{
  const original=member.nickname;member.nickname='긴별명을사용하는개발테스트회원';
  try{for(const authenticated of [false,true]){
   const{ctx,p}=await fixture({width:320,authenticated});try{for(const width of [320,390]){
    await p.setViewportSize({width,height:900});const toggle=p.getByRole('button',{name:'메뉴 열기',exact:true}),logo=p.locator('.iyum-header .iyum-wordmark');
    assert.equal(await toggle.locator('svg').count(),1);assert(Math.abs((await toggle.boundingBox()).y-(await logo.boundingBox()).y)<10);assert((await toggle.boundingBox()).width>=44);
    await toggle.focus();await p.keyboard.press('Enter');assert(await p.getByRole('dialog',{name:'전체 메뉴'}).locator('.iyum-member-menu').isVisible());if(authenticated)assert((await p.getByRole('dialog',{name:'전체 메뉴'}).locator('.iyum-member-name').innerText()).includes(member.nickname));else assert(await p.locator('.iyum-header').getByRole('link',{name:'로그인',exact:true}).isVisible());
    assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:output+`/header-${authenticated?'member':'anonymous'}-${width}.png`});await p.keyboard.press('Escape');assert(await toggle.evaluate(e=>e===document.activeElement));assert.equal(await p.getByRole('dialog',{name:'전체 메뉴'}).locator('.iyum-member-menu').isVisible(),false)
   }}finally{await ctx.close()}
  }}finally{member.nickname=original}
 })
 await check('1440/390/320 long content, tabs keyboard/resize, compact best and basic avatar',async()=>{
  const{ctx,p}=await fixture();posts[0]={...posts[0],title:'[검증 예시] '+ '긴제목'.repeat(25),author:{memberId:123,nickname:'긴닉네임'.repeat(20)},commentCount:Number.MAX_SAFE_INTEGER};await p.reload();await p.waitForFunction(()=>!!history.state?.iyumFeedEntry)
  for(const width of [1440,390,320]){await p.setViewportSize({width,height:950});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.equal(await p.locator('.feed-best button').isDisabled(),true);assert.equal(await p.locator('.feed-avatar img').count(),0);await p.screenshot({path:output+`/feed-all-long-${width}.png`,fullPage:false});if(width<768){const toggle=p.locator('.feed-expand');await toggle.focus();await p.keyboard.press('Enter');assert.equal(await toggle.getAttribute('aria-expanded'),'true');await p.keyboard.press('Escape');assert.equal(await toggle.getAttribute('aria-expanded'),'false');assert.equal(await p.evaluate(()=>document.activeElement.className),'feed-expand');await p.setViewportSize({width:1440,height:950});assert(await p.locator('.feed-tabs').getByRole('link',{name:'복지·생활'}).isVisible())}}
  posts[0]=make(1000)
  for(const width of [1440,390,320]){await p.setViewportSize({width,height:950});await p.goto(base+'/community');await p.screenshot({path:output+`/feed-all-${width}.png`});await p.goto(base+'/community/topics/hearing-aid');await p.screenshot({path:output+`/feed-hearing-aid-${width}.png`});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))}await ctx.close()
 })
}finally{await fs.writeFile(output+'/results.json',JSON.stringify({kind:'mock production',results},null,2));await browser.close();await new Promise(r=>server.close(r))}
