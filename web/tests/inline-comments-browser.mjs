// Production Next + controllable mock API; no real Spring or user data.
import http from 'node:http'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
import { loadTypeScript } from './helpers/load-typescript.mjs'
const { isCommentPage } = await loadTypeScript('../src/lib/community-comments.ts', import.meta.url)
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8467',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-inline-browser'
await fs.mkdir(out,{recursive:true})
const stamp='2026-10-28T00:00:00.123456Z',member={memberId:321,nickname:'검증회원',email:'fixture@example.test',role:'USER'}
const failure=code=>({code,message:'mock error',fieldErrors:[]})
const post=id=>({visibility:"VISIBLE",id,type:'QUESTION',topic:'HEARING_AID',title:`검증 게시글 ${id}`,content:'전체 본문\n'.repeat(20),contentPreview:'목록 미리보기',author:member,createdAt:stamp,updatedAt:stamp,commentCount:id===998?0:id===999?1:45,likeCount:0,liked:false})
const comment=(pid,id)=>({commentId:pid*100+id,content:`게시글 ${pid} 댓글 ${id}\n줄바꿈과 긴 문자열 ${'가'.repeat(50)}`,author:member,createdAt:stamp,updatedAt:stamp,likeCount:id%3,liked:false})
let posts=[],comments=new Map()
const cp=(id,u)=>{
 const sort=u.searchParams.get('sort')??'OLDEST',after=Number(u.searchParams.get('afterId')),likes=Number(u.searchParams.get('afterLikeCount'))
 const cmp=(a,b)=>sort==='LATEST'?b.commentId-a.commentId:sort==='POPULAR'?b.likeCount-a.likeCount||a.commentId-b.commentId:a.commentId-b.commentId
 const all=[...(comments.get(id)??[])].sort(cmp).filter(c=>!u.searchParams.has('afterId')||cmp(c,{commentId:after,likeCount:likes})>0),items=all.slice(0,20),hasNext=all.length>20,last=items.at(-1)
 return{items,hasNext,nextCursor:hasNext?{createdAt:last.createdAt,commentId:last.commentId,sort,likeCount:sort==='POPULAR'?last.likeCount:null}:null}
}
function read(u){
 const id=Number(u.pathname.split('/')[5])
 if(u.pathname.endsWith('/feed'))return{json:{items:posts.slice(0,20),hasNext:false,nextCursor:null}}
 const p=posts.find(p=>p.id===id)
 if(!p)return{status:404,json:failure('POST_NOT_FOUND')}
 if(u.pathname.endsWith('/comments'))return{json:cp(id,u)}
 return{json:p}
}
const server=http.createServer((req,res)=>{const r=read(new URL(req.url,'http://mock'));res.writeHead(r.status??200,{'Content-Type':'application/json'});res.end(JSON.stringify(r.json))})
await new Promise(r=>server.listen(Number(process.env.COMMUNITY_SSR_MOCK_PORT??8858),'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
async function fixture(width=1440){
 posts=Array.from({length:27},(_,i)=>post(1000-i));comments=new Map(posts.map(p=>[p.id,Array.from({length:p.commentCount},(_,i)=>comment(p.id,i+1))]))
 const ctx=await browser.newContext({viewport:{width,height:900},reducedMotion:'no-preference'}),p=await ctx.newPage();p.setDefaultTimeout(8000)
 const state={member:{...member},loginMember:{...member},calls:[],get:null,write:null}
 await p.route('http://127.0.0.1:8082/**',async r=>{
  const q=r.request(),u=new URL(q.url()),method=q.method();state.calls.push({path:u.pathname,search:u.search,method})
  if(u.pathname.endsWith('/auth/me'))return r.fulfill(state.member?{json:state.member}:{status:401,json:failure('AUTHENTICATION_REQUIRED')})
  if(u.pathname.endsWith('/csrf')&&state.csrf)return state.csrf(r,u)
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/logout')&&state.logout)return state.logout(r,u)
  if(u.pathname.endsWith('/logout')){state.member=null;return r.fulfill({status:204})}
  if(u.pathname.endsWith('/login')){state.member={...state.loginMember};return r.fulfill({json:state.member})}
  if(u.pathname.endsWith('/members/me'))return r.fulfill({json:{...state.member,createdAt:stamp}})
  if(u.pathname.endsWith('/activity-summary'))return r.fulfill({json:{postCount:posts.length,commentCount:1}})
  if(u.pathname.endsWith('/members/me/posts')){const all=posts.filter(p=>!u.searchParams.has('afterId')||p.id<Number(u.searchParams.get('afterId'))),items=all.slice(0,10),hasNext=all.length>10;return r.fulfill({json:{items,hasNext,nextCursor:hasNext?{createdAt:stamp,postId:items.at(-1).id}:null}})}
  if(u.pathname.endsWith('/members/me/comments'))return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}})
  if(method==='GET')return state.get?state.get(r,u):r.fulfill(read(u))
  if(state.write)return state.write(r,u)
  const id=Number(u.pathname.split('/')[5]),cid=Number(u.pathname.split('/').at(-2)),c=comments.get(id)?.find(c=>c.commentId===cid)
  if(method==='POST'&&u.pathname.endsWith('/comments')){const list=comments.get(id),created={...comment(id,list.length+1),content:q.postDataJSON().content};list.push(created);posts.find(p=>p.id===id).commentCount=list.length;return r.fulfill({status:201,json:{commentId:created.commentId}})}
  if(u.pathname.match(/\/posts\/\d+\/like$/)){const p=posts.find(p=>p.id===id);p.liked=method==='PUT';p.likeCount=p.liked?7:6;return r.fulfill({json:{postId:id,liked:p.liked,likeCount:p.likeCount}})}
  if(u.pathname.endsWith('/like')){c.liked=method==='PUT';c.likeCount=c.liked?9:8;return r.fulfill({json:{postId:id,commentId:cid,liked:c.liked,likeCount:c.likeCount}})}
  return r.fulfill({status:500,json:failure('UNEXPECTED')})
 })
 await p.goto(base+'/mypage');await p.locator('[data-member-post-id="1000"]').waitFor()
 return {ctx,p,state}
}
const row=(p,id)=>p.locator(`[data-member-post-id="${id}"]`),area=(p,id)=>row(p,id).locator('.mypage-inline-comments'),items=(p,id)=>area(p,id).locator('.community-comment-list > li')
const reads=(s,id)=>s.calls.filter(c=>c.method==='GET'&&c.path===`/api/v1/community/posts/${id}/comments`)
async function count(p,id,n){await p.waitForFunction(({id,n})=>document.querySelectorAll(`[data-member-post-id="${id}"] .community-comment-list > li`).length===n,{id,n})}
async function open(p,id,n){await row(p,id).getByRole('button',{name:/^댓글 \d/}).click();await count(p,id,n);await p.waitForFunction(id=>document.querySelector(`[data-member-post-id="${id}"] .mypage-inline-comments`).getAttribute('aria-busy')==='false',id)}
function gate(s,pid){let release,held;const reached=new Promise(resolve=>{s.get=async(r,u)=>{if(u.pathname===`/api/v1/community/posts/${pid}/comments`){held=r.request();s.get=null;await new Promise(done=>{release=async(data)=>{try{await r.fulfill(data??read(u))}finally{done()}};resolve()})}else await r.fulfill(read(u))}});return{reached,get request(){return held},release:data=>release(data)}}
async function check(name,fn,width){const f=await fixture(width);try{await fn(f);results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:String(e)});console.error('FAIL',name,e);process.exitCode=1;await f.p.screenshot({path:`${out}/failure-${results.length}.png`,fullPage:true})}finally{await f.ctx.close()}}
try{
 for(const width of [1440,390,320])await check(`panel hierarchy bottom collapse draft focus and errors ${width}`,async({p,state})=>{
  comments.get(1000)[0].author={...member,nickname:'긴닉네임'.repeat(20)}
  await open(p,998,0);await open(p,999,1);await open(p,1000,20)
  const panel=area(p,1000),toggle=row(p,1000).getByRole('button',{name:/^댓글 \d/})
  await panel.getByRole('textbox',{name:'댓글 작성'}).fill('접어도 유지할 초안\n두 번째 줄')
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  const bounds=await panel.boundingBox(),postBounds=await row(p,1000).boundingBox();assert(bounds.x>postBounds.x)
  await toggle.scrollIntoViewIfNeeded();await p.screenshot({path:`${out}/panel-${width}.png`})
  await p.screenshot({path:`${out}/panels-full-${width}.png`,fullPage:true})
  state.write=r=>r.fulfill({status:403,json:failure('ACCESS_DENIED')})
  await panel.getByRole('button',{name:'등록',exact:true}).click();await panel.getByRole('alert').waitFor()
  assert.equal(await panel.getByRole('textbox',{name:'댓글 작성'}).inputValue(),'접어도 유지할 초안\n두 번째 줄')
  await p.screenshot({path:`${out}/panel-error-${width}.png`})
  const before=reads(state,1000).length
  const close=panel.getByRole('button',{name:'댓글 접기',exact:true});await close.focus();await p.keyboard.press('Enter')
  await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments')?.hidden)
  assert.equal(await toggle.evaluate(e=>e===document.activeElement),true)
  const rect=await toggle.boundingBox(),header=await p.locator('.iyum-header').boundingBox();assert(rect.y>=Math.max(0,header.y+header.height));assert(rect.y+rect.height<=900)
  assert.equal(reads(state,1000).length,before)
  await open(p,1000,20);assert.equal(await panel.getByRole('textbox',{name:'댓글 작성'}).inputValue(),'접어도 유지할 초안\n두 번째 줄')
  assert.equal(reads(state,1000).length,before+1)
  state.get=(r,u)=>r.fulfill(u.pathname.endsWith('/1000/comments')?{status:503,json:failure('SERVER_ERROR')}:read(u))
  await panel.getByRole('button',{name:'댓글 새로고침',exact:true}).click();await panel.getByRole('alert').filter({hasText:'기존 목록'}).waitFor()
  assert.equal(await items(p,1000).count(),20);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  await panel.getByRole('textbox',{name:'댓글 작성'}).fill('');await row(p,999).locator('h3 a').click();await p.locator('.community-comments').waitFor()
  assert.equal(await p.locator('.community-comments.mypage-inline-comments').count(),0)
 },width)

 const receipts=(p,id)=>area(p,id).locator('[data-inline-created-id]')
 async function submitCreated(p,id,text){await area(p,id).getByRole('textbox',{name:'댓글 작성'}).fill(text);await area(p,id).getByRole('button',{name:'등록',exact:true}).click();await p.waitForFunction(id=>document.querySelector(`[data-member-post-id="${id}"] textarea`)?.value==='',id)}
 async function chooseReceiptSort(p,id,label,sort){
  const completed=p.waitForResponse(r=>new URL(r.url()).pathname===`/api/v1/community/posts/${id}/comments`&&new URL(r.url()).searchParams.get('sort')===sort)
  await area(p,id).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,id).getByRole('menuitemradio',{name:label,exact:true}).click();await(await completed).finished()
  const expected=cp(id,new URL(`http://mock?sort=${sort}`)).items[0]?.commentId
  await p.waitForFunction(({id,expected})=>{const a=document.querySelector(`[data-member-post-id="${id}"] .mypage-inline-comments`);return a?.getAttribute('aria-busy')==='false'&&Number(a.querySelector('[data-inline-comment]')?.getAttribute('data-inline-comment'))===expected},{id,expected})
 }
 await check('created toast replaces inline receipts, latest success resets lifetime and preserves other draft',async({p,state})=>{
  await p.clock.install();await open(p,1000,20);await open(p,999,1)
  await area(p,999).getByRole('textbox',{name:'댓글 작성'}).fill('다른 초안');const b=reads(state,999).length
  await submitCreated(p,1000,' 첫 원문\n ');await p.locator('.comment-toast-preview').waitFor()
  assert.equal(await p.locator('.comment-toast-preview').textContent(),' 첫 원문\n ');assert.equal(await receipts(p,1000).count(),0)
  await p.clock.runFor(4000);await submitCreated(p,1000,' 두번째 원문\n ')
  assert.equal(await p.locator('.comment-success-toast').count(),1);assert.equal(await p.locator('.comment-toast-preview').textContent(),' 두번째 원문\n ')
  await p.clock.runFor(1500);assert.equal(await p.locator('.comment-success-toast').count(),1)
  assert.equal(await p.locator('.comment-toast-preview').getAttribute('aria-hidden'),'true')
  assert.equal(await area(p,999).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'다른 초안');assert.equal(reads(state,999).length,b)
  await p.clock.runFor(3500);await p.waitForFunction(()=>!!document.querySelector('.comment-success-toast.is-leaving'))
  await p.clock.runFor(250);await p.waitForFunction(()=>!document.querySelector('.comment-success-toast'))
  assert.equal(state.calls.filter(c=>c.method==='POST').length,2)
 })
 for(const width of [1440,390,320])await check(`created toast layout and selected sort preserved ${width}`,async({p})=>{
  await p.clock.install();await open(p,1000,20);await chooseReceiptSort(p,1000,'인기순','POPULAR')
  const text=' 공백과 줄바꿈\n'+'긴댓글'.repeat(100)+' '
  await submitCreated(p,1000,text);await p.locator('.comment-toast-preview').waitFor()
  assert.equal(await p.locator('.comment-toast-preview').textContent(),text);assert.equal(await receipts(p,1000).count(),0)
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  const box=await p.locator('.comment-success-toast').boundingBox();assert(box.x>=0&&box.x+box.width<=width&&box.height<180)
  await p.screenshot({path:`${out}/toast-${width}.png`})
  await p.clock.runFor(5250);await p.waitForFunction(()=>!document.querySelector('.comment-success-toast'))
  await p.screenshot({path:`${out}/toast-dismissed-${width}.png`})
  await chooseReceiptSort(p,1000,'최신순','LATEST');assert.equal(await area(p,1000).locator('[data-inline-comment="100046"]').count(),1)
 },width)
 for(const [label,sort] of [['등록순','OLDEST'],['최신순','LATEST'],['인기순','POPULAR']])await check(`explicit collapse resets to first server cursor: ${sort}`,async({p,state})=>{
  await open(p,1000,20);if(sort!=='OLDEST')await chooseReceiptSort(p,1000,label,sort)
  await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('접어도 남는 초안')
  for(const n of [40,45]){await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,n)}
  const before=reads(state,1000).length;const t=row(p,1000).getByRole('button',{name:/^댓글 \d/});await t.click()
  const g=gate(state,1000);await t.click();await g.reached
  assert.equal(await items(p,1000).count(),0);assert.equal(new URL(g.request.url()).searchParams.has('afterId'),false)
  assert.equal(new URL(g.request.url()).searchParams.get('sort'),sort)
  await g.release();await count(p,1000,20);await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments').getAttribute('aria-busy')==='false')
  assert.equal(reads(state,1000).length,before+1);assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'접어도 남는 초안')
  const expected=cp(1000,new URL(`http://mock?sort=${sort}`)).nextCursor
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,40)
  const q=new URLSearchParams(reads(state,1000).at(-1).search);assert.equal(q.get('afterId'),String(expected.commentId));assert.equal(q.get('afterCreatedAt'),expected.createdAt)
  if(sort==='POPULAR')assert.equal(q.get('afterLikeCount'),String(expected.likeCount))
 })
 await check('late more response cannot refill reopened first page or clear its busy state',async({p,state})=>{
  await open(p,1000,20)
  await p.evaluate(()=>{const original=fetch;window.oldReadConsumed=false;window.fetch=async(input,options)=>{if(!String(input).includes('/1000/comments')||options?.method!=='GET')return original(input,options);const response=await original(input,{...options,signal:undefined});const json=response.json.bind(response);response.json=async()=>{const value=await json();if(String(input).includes('afterId'))window.oldReadConsumed=true;return value};return response}})
  const old=gate(state,1000);await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await old.reached
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click()
  const fresh=gate(state,1000);await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await fresh.reached
  await old.release();await p.waitForFunction(()=>window.oldReadConsumed);await p.evaluate(()=>new Promise(requestAnimationFrame))
  assert.equal(await area(p,1000).getAttribute('aria-busy'),'true');assert.equal(await items(p,1000).count(),0)
  await fresh.release();await count(p,1000,20);await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments').getAttribute('aria-busy')==='false')
  assert.equal(await area(p,1000).locator('[data-inline-comment]').first().getAttribute('data-inline-comment'),'100001');assert.equal(reads(state,1000).length,3)
 })
 await check('accepted GET before delayed POST completion cannot create a duplicate record',async({p,state})=>{
  await open(p,1000,20);await chooseReceiptSort(p,1000,'최신순','LATEST')
  let release,reach;const reached=new Promise(r=>reach=r)
  state.write=async r=>{const created={...comment(1000,46),content:r.request().postDataJSON().content};comments.get(1000).push(created);reach();await new Promise(r=>release=r);await r.fulfill({status:201,json:{commentId:created.commentId}})}
  await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('조회가 먼저 확인한 원문');await area(p,1000).getByRole('button',{name:'등록',exact:true}).click();await reached
  await area(p,1000).getByRole('button',{name:'댓글 새로고침',exact:true}).click();await p.waitForFunction(()=>!!document.querySelector('[data-inline-comment="100046"]'))
  const finished=p.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/comments'));release();await(await finished).finished()
  await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] textarea')?.value==='')
  assert.equal(await receipts(p,1000).count(),0);assert.equal(await area(p,1000).locator('[data-inline-comment="100046"]').count(),1);assert.equal(state.calls.filter(c=>c.method==='POST').length,1)
 })
 await check('rejected old-sort GET cannot replace current sort when transport ignores abort',async({p,state})=>{
  await open(p,1000,20);await submitCreated(p,1000,'아직 서버 목록에서 확인하지 못한 기록');await p.locator('.comment-toast-preview').waitFor()
  await p.evaluate(()=>{window.receiptReads=[];const fetch=window.fetch;window.fetch=async(input,options)=>{if(!String(input).includes('/1000/comments')||options?.method!=='GET')return fetch(input,options);const record={url:String(input),consumed:false};window.receiptReads.push(record);const response=await fetch(input,{...options,signal:undefined});const json=response.json.bind(response);response.json=async()=>{try{return await json()}finally{record.consumed=true}};return response}})
  const stale=structuredClone(cp(1000,new URL('http://mock?sort=LATEST')));assert.equal(isCommentPage(stale,'LATEST'),true)
  const old=gate(state,1000);await area(p,1000).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,1000).getByRole('menuitemradio',{name:'최신순',exact:true}).click();await old.reached
  await chooseReceiptSort(p,1000,'인기순','POPULAR');assert.equal(await receipts(p,1000).count(),0)
  await old.release({json:stale});await p.waitForFunction(()=>window.receiptReads.some(r=>new URL(r.url).searchParams.get('sort')==='LATEST'&&r.consumed));await p.evaluate(()=>new Promise(requestAnimationFrame))
  assert.equal(await receipts(p,1000).count(),0);assert.equal(await area(p,1000).getByRole('button',{name:'댓글 정렬: 인기순',exact:true}).count(),1)
 })
 await check('creation success remains distinct from GET failure and retry',async({p,state})=>{
  await open(p,1000,20);await chooseReceiptSort(p,1000,'최신순','LATEST')
  const g=gate(state,1000);await submitCreated(p,1000,'확정 성공 원문');await g.reached;await p.locator('.comment-toast-preview').waitFor();await g.release({status:503,json:failure('SERVER_ERROR')})
  await area(p,1000).getByRole('alert').waitFor();assert.equal(await receipts(p,1000).count(),0);assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'')
  await area(p,1000).getByRole('button',{name:'댓글 다시 시도',exact:true}).click();await p.waitForFunction(()=>!!document.querySelector('[data-inline-comment="100046"]'))
  assert.equal(await receipts(p,1000).count(),0);assert.equal(state.calls.filter(c=>c.method==='POST').length,1)
 })
 await check('saved comment does not guard departure or restore inline receipt across boundary',async({p,state})=>{
  await open(p,991,20);await submitCreated(p,991,'경계 밖 성공');await p.locator('.comment-toast-preview').waitFor()
  await open(p,1000,20);state.write=r=>{posts.unshift(post(1001));comments.set(1001,[]);return r.fulfill({status:201,json:{commentId:100046}})}
  await submitCreated(p,1000,'다른 성공');await row(p,1001).waitFor();await p.waitForFunction(()=>!document.querySelector('[data-member-post-id="991"]'))
  await p.getByRole('button',{name:'더 보기',exact:true}).click();await row(p,991).waitFor();await open(p,991,20);assert.equal(await receipts(p,991).count(),0)
  await row(p,999).locator('h3 a').click();await p.waitForURL(/community\/posts\/999/);assert.equal(await p.getByRole('button',{name:'나가기',exact:true}).count(),0)
  await p.goBack();await row(p,991).waitFor();assert.equal(await p.locator('[data-inline-created-id]').count(),0)
 })
 await check('creation followed by failed and confirmed logout does not restore inline receipt',async({p,state})=>{
  await open(p,1000,20);await submitCreated(p,1000,'성공 기록');await p.locator('.comment-toast-preview').waitFor()
  state.logout=r=>r.fulfill({status:403,json:failure('CSRF_TOKEN_INVALID')});await p.getByRole('button',{name:'로그아웃',exact:true}).click()
  await row(p,1000).waitFor();if(await row(p,1000).getByRole('button',{name:/^댓글 \d/}).getAttribute('aria-expanded')!=='true')await open(p,1000,20)
  assert.equal(await receipts(p,1000).count(),0);assert.equal(state.member.memberId,321);assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'');state.logout=null;await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByRole('heading',{name:'로그인이 필요해요'}).waitFor();assert.equal(await p.locator('[data-inline-created-id]').count(),0)
 })
 await check('draft boundary shift: hidden draft guards departure and returns on next page',async({p,state})=>{
  await open(p,991,20);await area(p,991).getByRole('textbox',{name:'댓글 작성'}).fill('경계 밖 초안\n보존')
  await open(p,1000,20);await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('다른 게시글 등록')
  let release,reach;const reached=new Promise(r=>reach=r)
  state.write=async r=>{reach();await new Promise(r=>release=r);posts.unshift(post(1001));comments.set(1001,[]);await r.fulfill({status:201,json:{commentId:100046}})}
  await area(p,1000).getByRole('button',{name:'등록',exact:true}).click();await reached
  const refreshed=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/members/me/posts'&&r.request().method()==='GET')
  release();await (await refreshed).finished();await row(p,1001).waitFor();await p.waitForFunction(()=>!document.querySelector('[data-member-post-id="991"]'))
  await row(p,1000).locator('h3 a').click();await p.getByRole('button',{name:'계속 작성',exact:true}).click();assert(new URL(p.url()).pathname==='/mypage')
  await p.getByRole('button',{name:'더 보기',exact:true}).click();await row(p,991).waitFor();await open(p,991,20)
  assert.equal(await area(p,991).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'경계 밖 초안\n보존')
  assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'');assert.equal(state.calls.filter(c=>c.method==='POST').length,1)
 })
 for(const result of ['success','POST_NOT_FOUND','NOT_FOUND','network'])await check(`draft cleanup requires confirmed parent deletion: ${result}`,async({p,state})=>{
  await open(p,1000,20);await submitCreated(p,1000,'삭제 전 성공 기록');await p.locator('.comment-toast-preview').waitFor();await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('삭제 대상 초안')
  state.write=r=>{if(result==='success'){posts=posts.filter(p=>p.id!==1000);return r.fulfill({status:204})}if(result==='network')return r.abort('failed');return r.fulfill({status:404,json:failure(result)})}
  await row(p,1000).getByRole('button',{name:'게시글 관리',exact:true}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click()
  await p.getByRole('button',{name:'삭제하기',exact:true}).click()
  if(result==='success')await p.waitForFunction(()=>!document.querySelector('[data-member-post-id="1000"]'))
  else {await p.getByRole('dialog').getByRole('alert').waitFor();await p.getByRole('button',{name:'취소',exact:true}).click();assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),result==='POST_NOT_FOUND'?'':'삭제 대상 초안');assert.equal(await receipts(p,1000).count(),0)}
  await row(p,999).locator('h3 a').click()
  if(['success','POST_NOT_FOUND'].includes(result))await p.waitForURL(/community\/posts\/999/)
  else {await p.getByRole('button',{name:'계속 작성',exact:true}).click();assert(new URL(p.url()).pathname==='/mypage')}
 })
 for(const phase of ['csrf','write'])await check(`inline ${phase} delayed across account change cannot write or clear new draft`,async({p,state})=>{
  await open(p,1000,20);await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('이전 회원 초안')
  let release,reach;const reached=new Promise(r=>reach=r)
  const handler=async r=>{state.csrf=null;reach();await new Promise(r=>release=r);await r.fulfill(phase==='csrf'?{json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}}:{status:201,json:{commentId:100046}})}
  if(phase==='csrf')state.csrf=handler;else state.write=handler
  await area(p,1000).getByRole('button',{name:'등록',exact:true}).click();await reached
  await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요',{exact:true}).waitFor()
  state.loginMember={...member,memberId:456,nickname:'새 회원'};posts=posts.map(x=>({...x,author:state.loginMember}))
  await p.getByRole('link',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill('fixture@example.test');await p.locator('input[name=password]').fill('mock-only');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await row(p,1000).waitFor();await open(p,1000,20)
  const input=area(p,1000).getByRole('textbox',{name:'댓글 작성'});assert.equal(await input.inputValue(),'');await input.fill('새 회원 초안')
  const before=reads(state,1000).length,done=p.waitForResponse(r=>r.url().includes(phase==='csrf'?'/csrf':'/1000/comments')&&r.request().method()===(phase==='csrf'?'GET':'POST'))
  release();await(await done).finished();await p.evaluate(()=>new Promise(requestAnimationFrame))
  assert.equal(await input.inputValue(),'새 회원 초안');assert.equal(await p.locator('.comment-success-toast').count(),0);assert.equal(await p.locator('[data-inline-created-id]').count(),0);assert.equal(reads(state,1000).length,before)
  assert.equal(state.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/comments')).length,phase==='csrf'?0:1)
 })
 for(const width of [1440,390,320])await check(`inline create independent drafts, tabs, guard and layout ${width}`,async({p,state})=>{
  await open(p,1000,20);await open(p,999,1)
  const a=area(p,1000).getByRole('textbox',{name:'댓글 작성'}),b=area(p,999).getByRole('textbox',{name:'댓글 작성'})
  await a.fill(' 첫 초안\n한글 입력 ');await b.fill('다른 글 초안')
  await row(p,999).getByRole('button',{name:/^댓글 \d/}).click();await row(p,999).getByRole('button',{name:/^댓글 \d/}).click();assert.equal(await b.inputValue(),'다른 글 초안')
  await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByRole('tab',{name:/내 게시글/}).click();assert.equal(await a.inputValue(),' 첫 초안\n한글 입력 ')
  let release,reach;const reached=new Promise(r=>reach=r)
  state.write=async(r,u)=>{reach();await new Promise(r=>release=r);const id=Number(u.pathname.split('/')[5]);comments.get(id).push({...comment(id,46),content:r.request().postDataJSON().content});posts.find(x=>x.id===id).commentCount++;state.write=null;await r.fulfill({status:201,json:{commentId:100046}})}
  await area(p,1000).getByRole('button',{name:'등록',exact:true}).click();await reached
  assert.equal(await a.isDisabled(),true);assert.equal(await b.isDisabled(),false)
  assert.equal(state.calls.filter(c=>c.method==='POST').length,1)
  release();await p.locator('.comment-success-toast').waitFor();await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] textarea').value==='')
  assert.equal(await b.inputValue(),'다른 글 초안')
  await row(p,1000).locator('h3 a').click();await p.getByRole('button',{name:'계속 작성',exact:true}).click();assert.equal(await b.inputValue(),'다른 글 초안')
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  await b.scrollIntoViewIfNeeded();await p.screenshot({path:`${out}/create-${width}.png`})
  await row(p,1000).locator('h3 a').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await p.waitForURL(/community\/posts\/1000/);await p.goBack();await row(p,999).waitFor()
  await area(p,999).waitFor();assert.equal(await row(p,999).getByRole('button',{name:/^댓글 \d/}).getAttribute('aria-expanded'),'true');assert.equal(await area(p,999).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'')
 },width)
 await check('inline input validation keeps original text and Enter newline',async({p,state})=>{
  await open(p,1000,20);const a=area(p,1000),input=a.getByRole('textbox',{name:'댓글 작성'})
  await a.getByRole('button',{name:'등록',exact:true}).click();await a.getByRole('status').getByText('내용을 입력해 주세요.',{exact:true}).waitFor()
  assert.equal(state.calls.filter(c=>c.method==='POST').length,0)
  await input.fill('   \n');await a.getByRole('button',{name:'등록',exact:true}).click();await a.getByRole('status').getByText('내용을 입력해 주세요.',{exact:true}).waitFor()
  await input.fill('가'.repeat(2001));await a.getByRole('button',{name:'등록',exact:true}).click();assert.equal(state.calls.filter(c=>c.method==='POST').length,0)
  await input.fill('한글');await input.press('Enter');await p.keyboard.insertText('가');assert((await input.inputValue()).includes('\n'));assert.equal(state.calls.filter(c=>c.method==='POST').length,0)
 })
 for(const mode of ['denied','uncertain','csrf','refresh'])await check(`inline create ${mode} preserves distinct mutation outcome`,async({p,state})=>{
  await open(p,1000,20);const a=area(p,1000),input=a.getByRole('textbox',{name:'댓글 작성'});await input.fill('보존할 초안')
  if(mode==='csrf')state.csrf=r=>r.abort('failed')
  else state.write=async r=>{if(mode==='refresh'){state.get=(r,u)=>r.fulfill(u.pathname.endsWith('/comments')?{status:503,json:failure('SERVER_ERROR')}:read(u));await r.fulfill({status:201,json:{commentId:100046}})}else await r.fulfill(mode==='denied'?{status:403,json:failure('ACCESS_DENIED')}:{status:201,json:{malformed:true}})}
  await a.getByRole('button',{name:'등록',exact:true}).click();await a.getByRole('alert').first().waitFor()
  if(mode==='refresh'){await p.locator('.comment-success-toast').waitFor();assert.equal(await input.inputValue(),'')}
  else {assert.equal(await input.inputValue(),'보존할 초안');assert.equal(await p.locator('.comment-success-toast').count(),0);assert.equal(await p.locator('[data-inline-created-id]').count(),0)}
  assert.equal(state.calls.filter(c=>c.method==='POST').length,mode==='csrf'?0:1)
 })
 for(const phase of ['csrf','write'])await check(`inline approved departure during ${phase} never changes destination`,async({p,state})=>{
  await open(p,1000,20);await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('이탈 초안')
  let release,reach;const reached=new Promise(r=>reach=r)
  const handler=async r=>{reach();await new Promise(r=>release=r);await r.fulfill(phase==='csrf'?{json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}}:{status:201,json:{commentId:100046}})}
  if(phase==='csrf')state.csrf=handler;else state.write=handler
  await area(p,1000).getByRole('button',{name:'등록',exact:true}).click();await reached
  await row(p,1000).locator('h3 a').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await p.waitForURL(/community\/posts\/1000/)
  const done=p.waitForResponse(r=>r.url().includes(phase==='csrf'?'/csrf':'/1000/comments')&&r.request().method()===(phase==='csrf'?'GET':'POST'));release();await(await done).finished();await p.evaluate(()=>new Promise(requestAnimationFrame))
  assert.equal(await p.locator('.comment-success-toast').count(),0);assert.equal(await p.locator('[data-inline-created-id]').count(),0);assert.equal(state.calls.filter(c=>c.method==='POST').length,phase==='csrf'?0:1)
 })
 await check('inline logout failure preserves drafts; success clears owner',async({p,state})=>{
  await open(p,1000,20);await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).fill('로그아웃 초안')
  state.logout=r=>r.fulfill({status:403,json:failure('CSRF_TOKEN_INVALID')})
  await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByRole('banner').getByRole('alert').waitFor();await row(p,1000).waitFor();await open(p,1000,20)
  assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'로그아웃 초안')
  state.logout=null;await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요',{exact:true}).waitFor()
  await p.getByRole('link',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill('fixture@example.test');await p.locator('input[name=password]').fill('mock-only');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await row(p,1000).waitFor();await open(p,1000,20)
  assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).inputValue(),'')
 })
 for (const ignoreAbort of [false,true]) await check(`detail read abort vs late delivery ownership ${ignoreAbort?'transport ignores abort':'native abort'}`,async({p,state})=>{
  await p.evaluate(ignore=>{window.readSignals=[];const fetch=window.fetch;window.fetch=async(input,options)=>{const u=new URL(String(input),location.href);if(!u.pathname.match(/\/posts\/\d+\/comments$/)||options?.method!=='GET')return fetch(input,options);const record={url:u.href,aborted:false,settled:false};window.readSignals.push(record);options.signal?.addEventListener('abort',()=>{record.aborted=true});try{const response=await fetch(input,ignore?{...options,signal:undefined}:options);const json=response.json.bind(response);response.json=async()=>{try{return await json()}finally{record.consumed=true}};return response}finally{record.settled=true}}},ignoreAbort)
  await row(p,1000).locator('h3 a').click();await p.waitForURL(/community\/posts\/1000/)
  const detail=p.locator('.community-comments');await detail.locator('#comment-draft').fill('취소 경합 초안');await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
  const a=gate(state,1000)
  await detail.getByRole('button',{name:/댓글 정렬:/}).click();await detail.getByRole('menuitemradio',{name:'최신순',exact:true}).click();await a.reached
  const b=gate(state,1000)
  await detail.getByRole('button',{name:/댓글 정렬:/}).click();await detail.getByRole('menuitemradio',{name:'인기순',exact:true}).click();await b.reached
  await p.waitForFunction(()=>window.readSignals.some(r=>new URL(r.url).searchParams.get('sort')==='LATEST'&&r.aborted))
  await a.release({status:503,json:failure('INTERNAL_SERVER_ERROR')})
  await p.waitForFunction(()=>window.readSignals.some(r=>new URL(r.url).searchParams.get('sort')==='LATEST'&&r.settled))
  await p.evaluate(()=>new Promise(requestAnimationFrame))
  assert.equal(await detail.locator('#comments-refresh').isDisabled(),true);assert.equal(await detail.getByRole('alert').count(),0)
  const response=p.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/1000/comments')&&new URL(r.url()).searchParams.get('sort')==='POPULAR')
  await b.release();await(await response).finished()
  await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled&&document.querySelector('.community-comments .community-comment-list > li')?.id==='comment-100002')
  assert.equal(await detail.locator('#comment-draft').inputValue(),'취소 경합 초안');assert.equal(await detail.getByRole('alert').count(),0)
  // Actual failure remains an error, preserving the previous order and draft.
  const bad=gate(state,1000);await detail.locator('#comments-refresh').click();await bad.reached;await bad.release({status:503,json:failure('INTERNAL_SERVER_ERROR')});await detail.getByRole('alert').waitFor()
  assert.equal(await detail.locator('#comment-draft').inputValue(),'취소 경합 초안')
  await detail.getByRole('button',{name:'댓글 조회 다시 시도'}).click();await p.waitForFunction(()=>!document.querySelector('.community-comments [role="alert"]')&&!document.querySelector('#comments-refresh').disabled)
  if(ignoreAbort){
   const stale=structuredClone(cp(1000,new URL('http://mock?sort=POPULAR')))
   stale.items=stale.items.map(c=>({...c,likeCount:99,liked:true}))
   const last=stale.items.at(-1)
   stale.nextCursor={sort:'POPULAR',createdAt:last.createdAt,commentId:last.commentId,likeCount:last.likeCount}
   assert.equal(stale.hasNext,true)
   assert.equal(isCommentPage(stale,'POPULAR'),true)
   const old=gate(state,1000);await detail.locator('#comments-refresh').click();await old.reached
   const index=await p.evaluate(()=>window.readSignals.length-1)
   await p.getByRole('button',{name:'로그아웃',exact:true}).click()
   await p.waitForFunction(i=>window.readSignals[i].aborted,index)
   await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
   await old.release({json:stale});await p.waitForFunction(i=>window.readSignals[i].settled&&window.readSignals[i].consumed,index)
   await p.evaluate(()=>new Promise(requestAnimationFrame))
   assert.equal(await detail.locator('[data-comment-like="100002"] button').getAttribute('aria-pressed'),'false')
   assert((await detail.locator('[data-comment-like="100002"]').textContent()).includes('좋아요 2'))
   assert.equal(await detail.locator('#comment-draft').inputValue(),'취소 경합 초안')
  }
 })
 await check('detail prefix cancellation stops subsequent pages; cancelled navigation keeps read alive',async({p,state})=>{
  await p.evaluate(()=>{window.readSignals=[];const fetch=window.fetch;window.fetch=async(input,options)=>{if(!String(input).includes('/1000/comments')||options?.method!=='GET')return fetch(input,options);const record={url:String(input),aborted:false,settled:false};window.readSignals.push(record);options.signal?.addEventListener('abort',()=>record.aborted=true);try{return await fetch(input,{...options,signal:undefined})}finally{record.settled=true}}})
  await row(p,1000).locator('h3 a').click();await p.waitForURL(/community\/posts\/1000/)
  const detail=p.locator('.community-comments');await detail.locator('#comment-draft').fill('페이지 재조회 초안');await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
  await detail.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await p.waitForFunction(()=>document.querySelectorAll('.community-comments .community-comment-list > li').length===40)
  await detail.getByRole('button',{name:'댓글 더 보기',exact:true}).click();await p.waitForFunction(()=>document.querySelectorAll('.community-comments .community-comment-list > li').length===45)
  let release,arrive;const reached=new Promise(r=>arrive=r)
  state.get=async(r,u)=>{if(u.pathname.endsWith('/1000/comments')&&u.searchParams.has('afterId')){state.get=null;await new Promise(done=>{release=async()=>{await r.fulfill(read(u));done()};arrive()})}else await r.fulfill(read(u))}
  await detail.locator('#comments-refresh').click();await reached
  const oldCount=reads(state,1000).filter(c=>new URLSearchParams(c.search).has('afterId')).length
  const b=gate(state,1000);await detail.getByRole('button',{name:/댓글 정렬:/}).click();await detail.getByRole('menuitemradio',{name:'인기순',exact:true}).click();await b.reached
  await release();await p.waitForFunction(()=>window.readSignals.some(r=>r.url.includes('afterId')&&r.aborted&&r.settled));await p.evaluate(()=>new Promise(requestAnimationFrame))
  assert.equal(reads(state,1000).filter(c=>new URLSearchParams(c.search).has('afterId')).length,oldCount)
  assert.equal(await detail.locator('#comments-refresh').isDisabled(),true)
  await b.release();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled&&document.querySelector('.community-comment-list > li')?.id==='comment-100002')
  const pending=gate(state,1000);await detail.locator('#comments-refresh').click();await pending.reached
  await p.locator('.community-back').click();await p.getByRole('button',{name:'계속 작성',exact:true}).click()
  assert.equal(await p.evaluate(()=>window.readSignals.at(-1).aborted),false)
  await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await p.waitForURL(/mypage/)
  assert.equal(await p.evaluate(()=>window.readSignals.at(-1).aborted),true)
  await pending.release()
 })
 for (const reduced of [false,true]) await check(`sort animation retains pending list and honors reduced motion ${reduced}`,async({p,state})=>{
  await p.emulateMedia({reducedMotion:reduced?'reduce':'no-preference'})
  await open(p,1000,20)
  await p.evaluate(()=>{window.commentAnimations=[];const animate=Element.prototype.animate;Element.prototype.animate=function(frames,options){if(this.hasAttribute('data-comment-item'))window.commentAnimations.push({id:this.getAttribute('data-comment-item'),frames,options});return animate.call(this,frames,options)}})
  const g=gate(state,1000),before=await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>e.dataset.inlineComment))
  await area(p,1000).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,1000).getByRole('menuitemradio',{name:'인기순',exact:true}).click();await g.reached
  assert.deepEqual(await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>e.dataset.inlineComment)),before)
  await g.release();await p.waitForFunction(()=>document.querySelector('[data-inline-comment]')?.getAttribute('data-inline-comment')==='100002')
  await p.evaluate(async()=>{await Promise.all(document.getAnimations().map(a=>a.finished.catch(()=>{})))})
  const effects=await p.evaluate(()=>window.commentAnimations)
  if(reduced)assert.equal(effects.length,0);else {assert(effects.some(a=>a.frames[0].transform));assert(effects.some(a=>a.frames[0].opacity===0));assert(effects.every(a=>a.options.duration===240))}
  assert.equal(await p.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'댓글 정렬: 인기순')
  assert.equal(reads(state,1000).length,2)
  await p.screenshot({path:`${out}/animation-settled-${reduced}.png`})
 })
 await check('popular failure retries identical cursor; duplicates preserve advancing server cursor',async({p,state})=>{
  await open(p,1000,20)
  await area(p,1000).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,1000).getByRole('menuitemradio',{name:'인기순',exact:true}).click();await p.waitForFunction(()=>document.querySelector('[data-inline-comment]')?.getAttribute('data-inline-comment')==='100002')
  const before=await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>e.dataset.inlineComment))
  let failedUrl,duplicateUrl,finalUrl
  state.get=async(r,u)=>{if(u.pathname.endsWith('/1000/comments')){failedUrl=u.href;state.get=null;return r.fulfill({status:503,json:failure('INTERNAL_SERVER_ERROR')})}return r.fulfill(read(u))}
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await area(p,1000).getByRole('alert').waitFor()
  assert.deepEqual(await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>e.dataset.inlineComment)),before)
  state.get=async(r,u)=>{if(u.pathname.endsWith('/1000/comments')){duplicateUrl=u.href;const c={...comments.get(1000)[1],likeCount:0};state.get=null;return r.fulfill({json:{items:[c],hasNext:true,nextCursor:{createdAt:stamp,commentId:c.commentId,sort:'POPULAR',likeCount:0}}})}return r.fulfill(read(u))}
  await area(p,1000).getByRole('button',{name:'댓글 다시 시도',exact:true}).click();await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).waitFor()
  assert.equal(duplicateUrl,failedUrl);assert.equal(await items(p,1000).count(),20)
  state.get=async(r,u)=>{if(u.pathname.endsWith('/1000/comments')){finalUrl=u.href;state.get=null;return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}})}return r.fulfill(read(u))}
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments').getAttribute('aria-busy')==='false' && ![...document.querySelectorAll('[data-member-post-id="1000"] .mypage-inline-comments button')].some(b=>b.textContent==='댓글 더 보기'||b.textContent==='댓글 다시 시도'))
  assert.equal(new URL(finalUrl).searchParams.get('afterId'),'100002');assert.equal(new URL(finalUrl).searchParams.get('afterLikeCount'),'0');assert.equal(await items(p,1000).count(),20)
 })
 await check('popular uncertain like refresh remains post-local and uses selected sort',async({p,state})=>{
  await open(p,1000,20);await open(p,999,1)
  await area(p,1000).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,1000).getByRole('menuitemradio',{name:'인기순',exact:true}).click();await p.waitForFunction(()=>document.querySelector('[data-inline-comment]')?.getAttribute('data-inline-comment')==='100002')
  const b=reads(state,999).length,n=reads(state,1000).length
  state.write=async(r,u)=>{const id=Number(u.pathname.split('/').at(-2)),c=comments.get(1000).find(c=>c.commentId===id);c.likeCount=99;c.liked=true;return r.fulfill({json:{malformed:true}})}
  await area(p,1000).locator('[data-inline-comment="100002"] button').click()
  await p.waitForFunction(()=>document.querySelector('[data-inline-comment="100002"]')?.textContent.includes('99'))
  assert.equal(reads(state,999).length,b);assert(reads(state,1000).length>n)
  assert(reads(state,1000).slice(n).every(c=>new URLSearchParams(c.search).get('sort')==='POPULAR'))
  assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
 })
 for (const width of [1440,390,320]) await check(`sorting independent popular pages and menu accessibility ${width}`,async({p,state})=>{
  await open(p,1000,20);await open(p,999,1)
  const select=async(id,label)=>{
   const trigger=area(p,id).getByRole('button',{name:/댓글 정렬:/}),same=(await trigger.getAttribute('aria-label'))===`댓글 정렬: ${label}`
   const sort={등록순:'OLDEST',최신순:'LATEST',인기순:'POPULAR'}[label]
   const response=same?null:p.waitForResponse(r=>{const u=new URL(r.url());return u.origin==='http://127.0.0.1:8082'&&u.pathname===`/api/v1/community/posts/${id}/comments`&&u.searchParams.get('sort')===sort&&r.request().method()==='GET'})
   await trigger.click();await area(p,id).getByRole('menuitemradio',{name:label,exact:true}).click()
   if(response){const r=await response;await r.finished();const body=await r.json();await p.waitForFunction(({id,first})=>{const region=document.querySelector(`[data-member-post-id="${id}"] .mypage-inline-comments`);return region.getAttribute('aria-busy')==='false'&&region.querySelector('[data-inline-comment]')?.getAttribute('data-inline-comment')===String(first)},{id,first:body.items[0].commentId})}
  }
  const b=reads(state,999).length
  await select(1000,'최신순');await count(p,1000,20)
  assert.equal(await area(p,1000).locator('[data-inline-comment]').first().getAttribute('data-inline-comment'),'100045')
  await select(1000,'인기순');await count(p,1000,20)
  assert.equal(await area(p,1000).locator('[data-inline-comment]').first().getAttribute('data-inline-comment'),'100002')
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,40)
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,45)
  const next=reads(state,1000).at(-1),params=new URLSearchParams(next.search);assert.equal(params.get('afterSort'),'POPULAR');assert.equal(params.get('afterLikeCount'),'0');assert.equal(params.get('afterCreatedAt'),stamp)
  const ids=await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>Number(e.dataset.inlineComment)))
  assert.deepEqual(ids,[...comments.get(1000)].sort((a,b)=>b.likeCount-a.likeCount||a.commentId-b.commentId).map(c=>c.commentId))
  const n=reads(state,1000).length;await select(1000,'인기순');assert.equal(reads(state,1000).length,n)
  const heart=area(p,1000).locator('[data-inline-comment="100003"] button');await heart.click();await p.waitForFunction(()=>document.querySelector('[data-inline-comment="100003"] button')?.getAttribute('aria-pressed')==='true')
  assert.equal(reads(state,1000).length,n);assert.equal(reads(state,999).length,b)
  assert.deepEqual(await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>Number(e.dataset.inlineComment))),ids)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click()
  await area(p,1000).getByRole('button',{name:'댓글 정렬: 인기순',exact:true}).focus();await p.keyboard.press('ArrowDown');await area(p,1000).getByRole('menuitemradio',{name:'인기순',exact:true}).waitFor()
  assert.equal(await area(p,1000).getByRole('menuitemradio',{name:'인기순',exact:true}).getAttribute('aria-checked'),'true')
  await p.screenshot({path:`${out}/sorting-menu-${width}.png`});await p.keyboard.press('Escape')
  assert.equal(await p.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'댓글 정렬: 인기순')
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  await count(p,1000,20);await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByRole('tab',{name:/내 게시글/}).click();await count(p,1000,20)
  await row(p,1000).locator('h3 a').click();await p.waitForURL(/community\/posts\/1000/);await p.goBack();await count(p,1000,20)
  await area(p,1000).getByRole('button',{name:'댓글 정렬: 인기순',exact:true}).waitFor()
 },width)
 await check('sort request ownership: old response and finally cannot replace latest request',async({p,state})=>{
  await open(p,1000,20)
  const a=gate(state,1000)
  await area(p,1000).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,1000).getByRole('menuitemradio',{name:'최신순',exact:true}).click();await a.reached
  const b=gate(state,1000)
  await area(p,1000).getByRole('button',{name:/댓글 정렬:/}).click();await area(p,1000).getByRole('menuitemradio',{name:'인기순',exact:true}).click();await b.reached
  await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments').getAttribute('aria-busy')==='true')
  await a.release();assert.equal(await area(p,1000).getAttribute('aria-busy'),'true');assert.equal(await items(p,1000).count(),20);assert.equal(await area(p,1000).locator('[data-inline-comment]').first().getAttribute('data-inline-comment'),'100001')
  await b.release()
  await p.waitForFunction(()=>{const region=document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments');return region?.getAttribute('aria-busy')==='false'&&region.querySelector('[data-inline-comment]')?.getAttribute('data-inline-comment')==='100002'})
  assert.deepEqual(await area(p,1000).locator('[data-inline-comment]').evaluateAll(es=>es.map(e=>Number(e.dataset.inlineComment))),[...comments.get(1000)].sort((a,b)=>b.likeCount-a.likeCount||a.commentId-b.commentId).slice(0,20).map(c=>c.commentId))
 })
 await check('detail old sort response cannot clear newer loading or replace order',async({p,state})=>{
  await row(p,1000).locator('h3 a').click();await p.waitForURL(/community\/posts\/1000/)
  const detail=p.locator('.community-comments');await detail.locator('#comment-draft').fill('초안 유지')
  await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
  const a=gate(state,1000)
  await detail.getByRole('button',{name:/댓글 정렬:/}).click();await detail.getByRole('menuitemradio',{name:'최신순',exact:true}).click();await a.reached
  const b=gate(state,1000)
  await detail.getByRole('button',{name:/댓글 정렬:/}).click();await detail.getByRole('menuitemradio',{name:'인기순',exact:true}).click();await b.reached
  await p.waitForFunction(()=>document.querySelector('#comments-refresh').disabled)
  await a.release();assert.equal(await detail.locator('#comments-refresh').isDisabled(),true)
  assert(await detail.locator('.community-comment-list > li').count()>0);assert.equal(await detail.locator('.community-comment-list > li').first().getAttribute('id'),'comment-100001')
  await b.release();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled && document.querySelector('.community-comments .community-comment-list > li')?.id==='comment-100002')
  assert.equal(await detail.locator('.community-comment-list > li').first().getAttribute('id'),'comment-100002')
  assert.equal(await detail.locator('#comment-draft').inputValue(),'초안 유지')
 })
 await check('detail sorting preserves creation and edit drafts while server order changes',async({p,state})=>{
  await row(p,1000).locator('h3 a').click();await p.waitForURL(/community\/posts\/1000/)
  const detail=p.locator('.community-comments');await detail.locator('#comment-draft').fill('새 댓글 초안')
  await detail.getByRole('button',{name:'댓글 수정',exact:true}).first().click();await detail.locator('textarea[id^="comment-edit-"]').fill('수정 초안')
  await detail.getByRole('button',{name:/댓글 정렬:/}).click();await detail.getByRole('menuitemradio',{name:'최신순',exact:true}).click()
  await detail.locator('#comment-100045').waitFor()
  assert.equal(await detail.locator('#comment-draft').inputValue(),'새 댓글 초안')
  assert.equal(await detail.locator('textarea[id^="comment-edit-"]').inputValue(),'수정 초안')
  assert.equal(state.calls.filter(c=>['POST','PUT','DELETE'].includes(c.method)).length,0)
 })

 for(const width of [1440,390,320])await check(`lazy independent regions, zero/one, IDs and layout ${width}`,async({p,state})=>{
  assert.equal(state.calls.filter(c=>c.path.endsWith('/comments')&&c.path.includes('/community/')).length,0)
  await open(p,998,0);await area(p,998).getByText('아직 댓글이 없어요.',{exact:true}).waitFor()
  await open(p,999,1);await open(p,1000,20)
  assert.equal(reads(state,1000).length,1)
  const ids=await p.locator('[id]').evaluateAll(es=>es.map(e=>e.id));assert.equal(ids.length,new Set(ids).size)
  assert.equal(await p.locator('[id^="comment-100"]').count(),0)
  assert.equal(await area(p,1000).locator('textarea:not(:disabled),form').count(),2)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();assert.equal(await area(p,1000).isVisible(),false)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).focus();await p.keyboard.press('Enter');await count(p,1000,20)
  assert.equal(reads(state,1000).length,2)
  assert.equal(new URLSearchParams(reads(state,1000).at(-1).search).has('afterId'),false)
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  assert((await row(p,1000).locator('h3 a').getAttribute('href')).includes('from=%2Fmypage'))
  assert.equal(await row(p,1000).getByRole('link',{name:/상세에서 댓글 보기|게시글 상세 보기/}).count(),0)
  assert.equal(await area(p,1000).getByText('오래된 댓글부터 표시합니다.',{exact:true}).count(),0)
  assert.equal(await area(p,998).getByText('마지막 댓글까지 확인했어요.',{exact:true}).count(),0)
  assert.equal(await area(p,999).getByText('마지막 댓글까지 확인했어요.',{exact:true}).count(),0)
  assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성'}).isDisabled(),false)
  assert.equal(await area(p,1000).getByRole('button',{name:'등록',exact:true}).isDisabled(),false)
  assert.equal(await area(p,1000).evaluate(e=>document.getElementById(e.getAttribute('aria-labelledby'))?.textContent),'검증 게시글 1000 게시글의 댓글')
  await p.screenshot({path:`${out}/expanded-${width}.png`,fullPage:true});await p.screenshot({path:`${out}/viewport-${width}.png`})
 },width)

 await check('mypage post like PUT DELETE uses shared red heart and never writes comments',async({p,state})=>{
  const button=row(p,1000).locator('[data-post-like="1000"] button')
  await button.click();await p.waitForFunction(()=>document.querySelector('[data-post-like="1000"] button')?.getAttribute('aria-pressed')==='true')
  assert.match(await button.innerText(),/좋아요 7/);assert.equal(await button.locator('svg').evaluate(e=>getComputedStyle(e).color),'rgb(196, 63, 80)');assert.equal(await button.evaluate(e=>getComputedStyle(e).backgroundColor),'rgba(0, 0, 0, 0)')
  await button.click();await p.waitForFunction(()=>document.querySelector('[data-post-like="1000"] button')?.getAttribute('aria-pressed')==='false')
  assert.match(await button.innerText(),/좋아요 6/)
  assert.deepEqual(state.calls.filter(c=>['PUT','DELETE','POST'].includes(c.method)).map(c=>[c.method,c.path]),[['PUT','/api/v1/community/posts/1000/like'],['DELETE','/api/v1/community/posts/1000/like']])
  assert.equal(reads(state,1000).length,0)
 })
 for(const outcome of ['confirmed','malformed','network'])await check(`post-local reads: two 40-row regions, ${outcome}`,async({p,state})=>{
  comments.set(999,Array.from({length:45},(_,i)=>comment(999,i+1)))
  await open(p,1000,20);await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,40)
  await open(p,999,20);await area(p,999).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,999,40)
  const before={a:reads(state,1000).length,b:reads(state,999).length}
  const bhtml=await area(p,999).innerHTML()
  if(outcome!=='confirmed')state.write=async r=>{comments.get(1000)[0].likeCount=12;comments.get(1000)[0].liked=true;if(outcome==='network')await r.abort('failed');else await r.fulfill({json:{malformed:true}})}
  await area(p,1000).getByRole('button',{name:'댓글 좋아요 등록',exact:true}).first().click()
  await p.waitForFunction(n=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes(`좋아요 ${n}`),outcome==='confirmed'?9:12)
  await p.waitForFunction(()=>document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments').getAttribute('aria-busy')==='false' && document.querySelector('[data-comment-like="100001"] button').getAttribute('aria-disabled')==='false')
  assert.equal(reads(state,999).length,before.b)
  assert.equal(await area(p,999).innerHTML(),bhtml)
  assert.equal(await items(p,1000).count(),40)
  assert.equal(reads(state,1000).length,before.a+(outcome==='confirmed'?0:2))
  if(outcome==='confirmed'){
   await area(p,1000).getByRole('button',{name:'댓글 좋아요 취소',exact:true}).first().click()
   await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes('좋아요 8') && document.querySelector('[data-comment-like="100001"] button').getAttribute('aria-disabled')==='false')
   assert.equal(reads(state,1000).length,before.a);assert.equal(reads(state,999).length,before.b)
  }
  results.push({name:`GET counts ${outcome}`,pass:true,before,after:{a:reads(state,1000).length,b:reads(state,999).length}})
 })

 for(const method of ['POST','PUT','DELETE'])await check(`collapsed A detail ${method} invalidates A only and preserves B`,async({p,state})=>{
  await open(p,1000,20);await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,40)
  await open(p,999,1)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click()
  const b=reads(state,999).length
  await row(p,1000).locator('h3 a').click();await p.locator('.community-post-body').waitFor();await p.locator('#comment-100001').waitFor()
  state.write=r=>{
   assert.equal(r.request().method(),method)
   if(method==='POST'){comments.get(1000).push({...comment(1000,99),content:'추가된 댓글'});posts[0].commentCount++;return r.fulfill({status:201,json:{commentId:100099}})}
   if(method==='PUT')comments.get(1000)[0].content='수정된 댓글'
   else {comments.get(1000).shift();posts[0].commentCount--}
   return r.fulfill({status:204})
  }
  if(method==='POST'){await p.locator('#comment-draft').fill('추가된 댓글');await p.getByRole('button',{name:'댓글 등록',exact:true}).click()}
  else if(method==='PUT'){await p.locator('#comment-100001').getByRole('button',{name:'댓글 수정',exact:true}).click();await p.locator('#comment-edit-100001').fill('수정된 댓글');await p.getByRole('button',{name:'댓글 저장',exact:true}).click()}
  else {await p.locator('#comment-100001').getByRole('button',{name:'댓글 삭제',exact:true}).click();await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click()}
  await p.getByText(method==='POST'?'댓글을 등록했습니다.':method==='PUT'?'댓글을 수정했습니다.':'댓글을 삭제했습니다.',{exact:true}).waitFor()
  await p.getByRole('link',{name:'← 마이페이지로 돌아가기',exact:true}).click();await row(p,1000).waitFor()
  const before=reads(state,1000).length
  assert.equal(await area(p,1000).isVisible(),false);assert.equal(reads(state,999).length,b)
  const refreshed=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/community/posts/1000/comments' && !new URL(r.url()).searchParams.has('afterId') && r.request().method()==='GET')
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click()
  await (await refreshed).finished()
  await p.waitForFunction(({method})=>{const e=document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments');return e.getAttribute('aria-busy')==='false' && (method==='DELETE'?!e.querySelector('[data-inline-comment="100001"]'):method==='POST'?e.querySelectorAll('[data-inline-comment]').length===20:e.textContent.includes('수정된 댓글'))},{method})
  assert.equal(reads(state,1000).length,before+1);assert.equal(reads(state,999).length,b);assert.equal(await items(p,1000).count(),20)
  assert.match(await row(p,1000).getByRole('button',{name:/^댓글 \d/}).innerText(),new RegExp(String(method==='POST'?46:method==='DELETE'?44:45)))
 })
 await check('late confirmed like after departure updates shared state without list reread',async({p,state})=>{
  await open(p,1000,20);await open(p,999,1)
  let release;const arrived=new Promise(resolve=>{state.write=async r=>{await new Promise(done=>{release=async()=>{comments.get(1000)[0].likeCount=13;comments.get(1000)[0].liked=true;await r.fulfill({json:{postId:1000,commentId:100001,likeCount:13,liked:true}});done()};resolve()})}})
  await area(p,1000).getByRole('button',{name:'댓글 좋아요 등록',exact:true}).first().click();await arrived
  await row(p,1000).locator('h3 a').click();await p.locator('.community-post-body').waitFor();await release()
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes('좋아요 13'))
  const before={a:reads(state,1000).length,b:reads(state,999).length}
  await p.goBack();await count(p,1000,20)
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes('좋아요 13'))
  assert.equal(reads(state,1000).length,before.a);assert.equal(reads(state,999).length,before.b)
 })
 await check('collapse invalidates held read and reopening starts one first-page request',async({p,state})=>{
  const g=gate(state,1000);await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await g.reached
  const toggle=row(p,1000).getByRole('button',{name:/^댓글 \d/});await toggle.click()
  await g.release();assert.equal(await area(p,1000).isVisible(),false);assert.equal(await items(p,1000).count(),0)
  await open(p,999,1);await open(p,1000,20);assert.equal(reads(state,1000).length,2)
  assert(reads(state,1000).every(r=>!new URLSearchParams(r.search).has('afterId')))
 })
 await check('more failure retains prefix and retries exact microsecond cursor; refresh atomic failure',async({p,state})=>{
  await open(p,1000,20)
  state.get=(r,u)=>r.fulfill(u.pathname.endsWith('/comments')?{status:503,json:failure('SERVER_ERROR')}:read(u))
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await area(p,1000).getByRole('alert').waitFor();assert.equal(await items(p,1000).count(),20)
  const failed=reads(state,1000).at(-1).search;assert(failed.includes('123456Z'))
  state.get=null;await area(p,1000).getByRole('button',{name:'댓글 다시 시도',exact:true}).click();await count(p,1000,40);assert.equal(reads(state,1000).at(-1).search,failed)
  await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,45)
  assert.equal(await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).count(),0)
  comments.get(1000)[0].content='새 서버 본문';state.get=(r,u)=>r.fulfill(u.searchParams.has('afterId')?{status:503,json:failure('SERVER_ERROR')}:read(u))
  await area(p,1000).getByRole('button',{name:'댓글 새로고침',exact:true}).click();await area(p,1000).getByRole('alert').waitFor()
  assert.equal(await items(p,1000).count(),45);assert.equal(await area(p,1000).getByText('새 서버 본문',{exact:true}).count(),0)
  state.get=null;await area(p,1000).getByRole('button',{name:'댓글 다시 시도',exact:true}).click();await area(p,1000).getByText('새 서버 본문',{exact:true}).waitFor();assert.equal(await items(p,1000).count(),45)
 })
 for(const width of [1440,390])await check(`tab/detail back restores expanded prefix and position ${width}`,async({p,state})=>{
  await open(p,1000,20);await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,40)
  await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByRole('tab',{name:/내 게시글/}).click();await count(p,1000,40);assert.equal(reads(state,1000).length,2)
  await items(p,1000).nth(25).evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-130));const y=await p.evaluate(()=>scrollY)
  // Trigger native link navigation without Playwright scrolling a far-away title first.
  await row(p,1000).locator('h3 a').evaluate(e=>e.click());await p.locator('.community-post-body').waitFor();await p.goBack();await count(p,1000,40)
  await p.waitForFunction(y=>Math.abs(scrollY-y)<4,y);assert.equal(await row(p,1000).getByRole('button',{name:/^댓글 \d/}).getAttribute('aria-expanded'),'true')
 },width)
 await check('like server result, latest shared state and refresh; no write retry',async({p,state})=>{
  await open(p,1000,20);const button=area(p,1000).getByRole('button',{name:'댓글 좋아요 등록',exact:true}).first()
  await button.click();await area(p,1000).getByRole('button',{name:'댓글 좋아요 취소',exact:true}).first().waitFor()
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]').textContent.includes('좋아요 9'))
  assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
  await area(p,1000).getByRole('button',{name:'댓글 좋아요 취소',exact:true}).first().click()
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]').textContent.includes('좋아요 8'))
  assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1)
 })
 for(const code of ['NOT_FOUND','POST_NOT_FOUND'])await check(`404 distinction ${code}`,async({p,state})=>{
  state.get=(r,u)=>{if(u.pathname.endsWith('/comments')){if(code==='POST_NOT_FOUND')posts=posts.filter(p=>p.id!==1000);return r.fulfill({status:404,json:failure(code)})}return r.fulfill(read(u))}
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click()
  if(code==='POST_NOT_FOUND')await p.waitForFunction(()=>!document.querySelector('[data-member-post-id="1000"]'))
  else{await area(p,1000).getByRole('alert').waitFor();assert.equal(await row(p,1000).count(),1);assert.equal(reads(state,1000).length,1)}
 })
 await check('independent initial failure retry does not clear another open post',async({p,state})=>{
  state.get=(r,u)=>r.fulfill(u.pathname.includes('/1000/comments')?{status:503,json:failure('SERVER_ERROR')}:read(u))
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await area(p,1000).getByRole('alert').waitFor()
  await open(p,999,1);assert.equal(reads(state,1000).length,1)
  state.get=null;await area(p,1000).getByRole('button',{name:'댓글 다시 시도',exact:true}).click();await count(p,1000,20);assert.equal(await items(p,999).count(),1)
 })
 await check('stale refresh cannot overwrite a subsequent successful like',async({p,state})=>{
  await open(p,1000,20);const old=structuredClone(cp(1000,new URL('http://mock'))),g=gate(state,1000)
  await area(p,1000).getByRole('button',{name:'댓글 새로고침',exact:true}).click();await g.reached
  await area(p,1000).getByRole('button',{name:'댓글 좋아요 등록',exact:true}).first().click()
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes('좋아요 9'))
  await g.release({json:old})
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes('좋아요 9') && document.querySelector('.mypage-inline-comments').getAttribute('aria-busy')==='false')
  assert.equal(reads(state,1000).length,2)
  assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
 })
 for(const result of ['malformed','network'])await check(`late uncertain ${result} after departure revalidates cached range`,async({p,state})=>{
  await open(p,1000,20);await area(p,1000).getByRole('button',{name:'댓글 더 보기',exact:true}).click();await count(p,1000,40)
  let release;const arrived=new Promise(resolve=>{state.write=async r=>{await new Promise(done=>{release=async()=>{comments.get(1000)[0].likeCount=11;try{if(result==='network')await r.abort('failed');else await r.fulfill({json:{malformed:true}})}finally{done()}};resolve()})}})
  await area(p,1000).getByRole('button',{name:'댓글 좋아요 등록',exact:true}).first().click();await arrived
  await row(p,1000).locator('h3 a').click();await p.locator('.community-post-body').waitFor();await release();await p.goBack();await count(p,1000,40)
  await p.waitForFunction(()=>document.querySelector('[data-comment-like="100001"]')?.textContent.includes('좋아요 11'))
  assert.equal(state.calls.filter(c=>c.method==='PUT').length,1)
 })
 for(const kind of ['same','other'])await check(`old read ignored after ${kind} member login`,async({p,state})=>{
  const stale=structuredClone(cp(1000,new URL('http://mock'))),g=gate(state,1000)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await g.reached
  await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요',{exact:true}).waitFor()
  state.loginMember={...member,memberId:kind==='same'?321:456,nickname:'새 로그인 회원'}
  posts=posts.map(p=>({...p,author:state.loginMember}));comments.set(1000,comments.get(1000).map(c=>({...c,author:state.loginMember,content:'새 세션 댓글',liked:false})))
  await p.getByRole('link',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill('fixture@example.test');await p.locator('input[name=password]').fill('mock-only');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click()
  await row(p,1000).waitFor();assert.equal(await row(p,1000).getByRole('button',{name:/^댓글 \d/}).getAttribute('aria-expanded'),'false')
  await g.release({json:stale});assert.equal(await items(p,1000).count(),0)
  await open(p,1000,20);assert.equal(reads(state,1000).length,2);assert.equal(await area(p,1000).getByText('새 세션 댓글',{exact:true}).count(),20)
 })
 await check('current inline 401 follows session expiry',async({p,state})=>{
  state.get=(r,u)=>r.fulfill(u.pathname.endsWith('/comments')?{status:401,json:failure('AUTHENTICATION_REQUIRED')}:read(u))
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await p.getByText('로그인이 필요해요',{exact:true}).waitFor()
  assert.equal(await p.locator('[data-member-post-id]').count(),0)
 })
 await check('confirmed parent deletion removes expanded state without touching another region',async({p,state})=>{
  await open(p,1000,20);await open(p,999,1)
  state.write=(r,u)=>{assert.equal(r.request().method(),'DELETE');assert.equal(u.pathname,'/api/v1/community/posts/1000');posts=posts.filter(p=>p.id!==1000);return r.fulfill({status:204})}
  await row(p,1000).getByRole('button',{name:'게시글 관리',exact:true}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click()
  await p.getByRole('dialog').getByRole('button',{name:'삭제하기',exact:true}).click()
  await p.waitForFunction(()=>!document.querySelector('[data-member-post-id="1000"]'))
  assert.equal(await row(p,999).getByRole('button',{name:/^댓글 \d/}).getAttribute('aria-expanded'),'true');await count(p,999,1)
 })
 await check('late read after logout does not repopulate private view',async({p,state})=>{
  const g=gate(state,1000);await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await g.reached
  await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요',{exact:true}).waitFor();await g.release()
  assert.equal(await p.locator('[data-member-post-id]').count(),0)
 })
}finally{await browser.close();await new Promise(r=>server.close(r));await fs.writeFile(`${out}/results.json`,JSON.stringify({kind:'mock',results},null,2))}
