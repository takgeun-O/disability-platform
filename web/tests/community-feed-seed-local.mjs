// Explicit local verification against the fixture created by tools/community-seed.
// Existing user data is read-only. One new fixture comment is created, edited, soft-deleted,
// and added to the private ownership manifest. No credentials/cookies are logged.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
if(process.env.IYUM_RUN_FEED_SEED_TEST!=='true')throw Error('Opt in with IYUM_RUN_FEED_SEED_TEST=true; requires the local fixture')
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',api='http://127.0.0.1:8082'
assert.equal(new URL(base).hostname,'127.0.0.1')
import {withSeedResources,fixture,beginComment,recordComment,recordSoftDelete,atomicWrite} from './helpers/seed-ownership.mjs'
await withSeedResources(async resources=>{
const seed=await fixture(api)
const owned=seed.posts,members=seed.members,representative=seed.cases[0].id,baseline=seed.cases[0].count
const output=process.env.COMMUNITY_ARTIFACT_DIR??'/private/tmp/iyum-feed-followup/real'
await fs.mkdir(output,{recursive:true})
const results=[],errors=[]
await resources.launch(chromium,{channel:'chrome',headless:true})
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn){for(let i=0;i<100;i++){if(await fn())return;await pause(60)}throw Error('Condition not reached')}
async function check(name,run){try{await run();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.message});console.log('FAIL',name,e.message);process.exitCode=1}}
async function collect(url,kind){let cursor=null,items=[],sizes=[];do{const u=new URL(api+url);u.searchParams.set('size','20');if(cursor){u.searchParams.set('afterCreatedAt',cursor.createdAt);u.searchParams.set('afterId',String(kind==='post'?cursor.postId:cursor.commentId))}const r=await fetch(u);assert.equal(r.status,200);const d=await r.json();assert.equal(d.hasNext,!!d.nextCursor);items.push(...d.items);sizes.push(items.length);cursor=d.nextCursor}while(cursor);assert.equal(new Set(items.map(p=>kind==='post'?p.id:p.commentId)).size,items.length);return{items,sizes}}
const ctx=await resources.context({viewport:{width:1440,height:950},reducedMotion:'reduce'})
await ctx.addInitScript(()=>{delete window.IntersectionObserver})
const p=await ctx.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>errors.push(e.message))
const rows=()=>p.locator('[data-feed-id]'),commentRows=()=>p.locator('.community-comment-list > li')
let ha,commentRead,createdComment=null,receipt=null
async function loadPosts(){while(await p.getByRole('button',{name:'게시글 더 보기',exact:true}).count()){const before=await rows().count();await p.getByRole('button',{name:'게시글 더 보기',exact:true}).click();await until(async()=>await rows().count()>before)}}
async function loadComments(){while(await p.getByRole('button',{name:'댓글 더 보기',exact:true}).count()){const before=await commentRows().count();await p.getByRole('button',{name:'댓글 더 보기',exact:true}).evaluate(e=>e.click());await until(async()=>await commentRows().count()>before)}}
try{
 await check('real API: 85 owned posts, HA65 + existing, type45, unique IDs and tied timestamps',async()=>{
  const all=await collect('/api/v1/community/posts/feed','post');assert.equal(all.items.filter(p=>owned.includes(p.id)).length,85)
  ha=await collect('/api/v1/community/posts/feed?topic=HEARING_AID','post');assert.equal(ha.items.filter(p=>owned.includes(p.id)).length,65)
  const type=await collect('/api/v1/community/posts/feed?topic=HEARING_AID&type=QUESTION','post');assert.equal(type.items.filter(p=>owned.includes(p.id)).length,45)
  for(let i=1;i<ha.items.length;i++){const a=ha.items[i-1],b=ha.items[i];assert(Date.parse(a.createdAt)>=Date.parse(b.createdAt));if(a.createdAt===b.createdAt)assert(a.id>b.id)}
  assert(ha.items.some((p,i)=>i>0&&p.createdAt===ha.items[i-1].createdAt));results.push({info:'actual HA accumulated counts',counts:ha.sizes,existing:ha.items.filter(p=>!owned.includes(p.id)).map(p=>p.id)})
 })
 await check('real comment API: manifest cases with current counts, ID ASC ties',async()=>{
  for(const[i,{id,count}]of seed.cases.entries()){const d=await collect(`/api/v1/community/posts/${id}/comments`,'comment');assert.equal(d.items.length,count);if(i===0){commentRead=d;assert.deepEqual(d.sizes,Array.from({length:Math.ceil(count/20)},(_,j)=>Math.min((j+1)*20,count)));for(let j=1;j<d.items.length;j++){const a=d.items[j-1],b=d.items[j];assert(Date.parse(a.createdAt)<=Date.parse(b.createdAt));if(a.createdAt===b.createdAt)assert(a.commentId<b.commentId)}}}
 })
 await check('real browser: feed accumulates/ends, detail and link/back preserve >60 and position',async()=>{
  await p.goto(base+'/community/topics/hearing-aid');await rows().first().waitFor();await loadPosts();assert.equal(await rows().count(),ha.items.length)
  for(const mode of ['link','back']){const row=p.locator(`[data-feed-id="${owned[30]}"]`);await row.evaluate(e=>window.scrollBy(0,e.getBoundingClientRect().top-130));const y=await p.evaluate(()=>scrollY);await row.locator('h3 a').click();await p.locator('.community-post-body').waitFor();if(mode==='link')await p.locator('.community-back').click();else await p.goBack();await until(async()=>await rows().count()===ha.items.length);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<12)}
  await p.reload();await rows().first().waitFor();assert.equal(await rows().count(),20);await loadPosts()
 })
 await check('real browser: comments accumulate/stop with exact IDs; noJS visible first20',async()=>{
  await p.goto(base+'/community/posts/'+representative);await commentRows().first().waitFor();assert.equal(await commentRows().count(),20)
  await loadComments();assert.equal(await commentRows().count(),baseline);assert.deepEqual(await commentRows().evaluateAll(es=>es.map(e=>Number(e.id.replace('comment-','')))),commentRead.items.map(c=>c.commentId));assert.equal(await p.getByRole('button',{name:'댓글 더 보기',exact:true}).count(),0)
  const nc=await resources.context({javaScriptEnabled:false}),np=await nc.newPage();try{await np.goto(base+'/community/posts/'+representative);assert(await np.locator('.community-post-body').isVisible());assert(await np.locator('#comment-'+commentRead.items[0].commentId).isVisible())}finally{await resources.finish(()=>nc.close())}
 })
 await check('fixture login then UI comment create/edit/delete; list counts and return context',async()=>{
  await p.goto(base+'/login');await p.locator('main input[name=email]').fill(seed.accounts[0].email);await p.locator('main input[name=password]').fill(seed.accounts[0].password);await p.getByRole('main').getByRole('button',{name:'로그인',exact:true}).click();await p.locator('.iyum-header').getByRole('button',{name:'로그아웃',exact:true}).waitFor()
  await p.goto(base+'/community/topics/hearing-aid');await rows().first().waitFor();await loadPosts();await p.locator(`[data-feed-id="${representative}"] h3 a`).click();await p.locator('#comment-draft').waitFor();
  const me=await(await ctx.request.get(api+'/api/v1/auth/me')).json();assert.equal(me.memberId,members[0]);receipt=await beginComment(seed,representative,members[0],'피드 댓글 수 UI 검증용 임시 댓글');
  const sent=p.waitForResponse(r=>new URL(r.url()).pathname===`/api/v1/community/posts/${representative}/comments`&&r.request().method()==='POST');
  await p.locator('#comment-draft').fill(receipt.content);await p.getByRole('button',{name:'댓글 등록',exact:true}).click();const response=await sent;assert.equal(response.status(),201);createdComment=(await response.json()).commentId;
  await recordComment(seed,receipt,createdComment);
  await atomicWrite(output+'/created-receipt.json',JSON.stringify({createdComment,ownedComment:createdComment,recoveryJournal:receipt.file}));
  await p.getByText('댓글을 등록했습니다.',{exact:true}).waitFor();await p.locator('.community-back').click();await until(async()=>await p.locator(`[data-feed-id="${representative}"] .feed-comment-count`).textContent()===`댓글 ${baseline+1}`);assert.equal(await rows().count(),ha.items.length)
  await p.locator(`[data-feed-id="${representative}"] h3 a`).click();await commentRows().first().waitFor();await loadComments();const row=p.locator('#comment-'+createdComment);await row.getByRole('button',{name:'댓글 수정',exact:true}).click();await p.locator('#comment-edit-'+createdComment).fill('[개발 테스트] 수정해도 댓글 개수는 그대로입니다.');await row.getByRole('button',{name:'댓글 저장',exact:true}).click();await p.getByText('댓글을 수정했습니다.',{exact:true}).waitFor();
  assert.equal((await collect(`/api/v1/community/posts/${representative}/comments`,'comment')).items.length,baseline+1)
  await row.getByRole('button',{name:'댓글 삭제',exact:true}).click();await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click();await p.getByText('댓글을 삭제했습니다.',{exact:true}).waitFor();await recordSoftDelete(receipt);
  await p.goBack();await until(async()=>await p.locator(`[data-feed-id="${representative}"] .feed-comment-count`).textContent()===`댓글 ${baseline}`);assert.equal(await rows().count(),ha.items.length)
 })
 await check('1440/390/320 actual all/topic captures, header and category keyboard, other routes',async()=>{
  for(const width of [1440,390,320])for(const scope of ['all','hearing-aid']){
   await p.setViewportSize({width,height:950});await p.goto(base+(scope==='all'?'/community':'/community/topics/hearing-aid'));await rows().first().waitFor();assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert(await p.locator('.feed-tabs').evaluate(e=>e.scrollWidth<=e.clientWidth));
   if(width<768){await p.getByRole('button',{name:'메뉴 열기',exact:true}).click();assert(await p.locator('.iyum-member-name').isVisible());await p.keyboard.press('Escape');assert(await p.getByRole('button',{name:'메뉴 열기',exact:true}).evaluate(e=>e===document.activeElement));await p.locator('.feed-expand').focus();await p.keyboard.press('Enter');await p.keyboard.press('Escape')}
   await p.screenshot({path:output+`/feed-${scope}-${width}.png`})
  }
  for(const route of ['/','/about','/welfare','/login','/register']){await p.goto(base+route);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const menu=p.getByRole('button',{name:'메뉴 열기',exact:true});await menu.click();assert(await p.locator('.iyum-nav-items').isVisible());await p.keyboard.press('Escape');assert(await menu.evaluate(e=>e===document.activeElement))}
 })
 await check('runtime errors and final representative count unchanged',async()=>{assert.deepEqual(errors,[]);assert.equal((await collect(`/api/v1/community/posts/${representative}/comments`,'comment')).items.length,baseline)})
}finally{await resources.finish(()=>atomicWrite(output+'/results.json',JSON.stringify({kind:'actual Spring/local fixture + dev8443',representative,ownedPostIds:owned,fixtureMembers:members,createdComment,ownedComment:receipt?.entry.recorded?createdComment:null,softDeletedComment:receipt?.entry.softDeleted?createdComment:null,recoveryJournal:receipt?.file,results,errors},null,2)))}

})
