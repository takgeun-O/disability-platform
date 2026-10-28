// Production Next + controllable mock API; no real Spring or user data.
import http from 'node:http'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8467',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-inline-browser'
await fs.mkdir(out,{recursive:true})
const stamp='2026-10-28T00:00:00.123456Z',member={memberId:321,nickname:'검증회원',email:'fixture@example.test',role:'USER'}
const failure=code=>({code,message:'mock error',fieldErrors:[]})
const post=id=>({id,type:'QUESTION',topic:'HEARING_AID',title:`검증 게시글 ${id}`,content:'전체 본문\n'.repeat(20),contentPreview:'목록 미리보기',author:member,createdAt:stamp,updatedAt:stamp,commentCount:id===998?0:id===999?1:45,likeCount:0,liked:false})
const comment=(pid,id)=>({commentId:pid*100+id,content:`게시글 ${pid} 댓글 ${id}\n줄바꿈과 긴 문자열 ${'가'.repeat(50)}`,author:member,createdAt:stamp,updatedAt:stamp,likeCount:id%3,liked:false})
let posts=[],comments=new Map()
const cp=(id,u)=>{const all=(comments.get(id)??[]).filter(c=>!u.searchParams.has('afterId')||c.commentId>Number(u.searchParams.get('afterId'))),items=all.slice(0,20),hasNext=all.length>20;return{items,hasNext,nextCursor:hasNext?{createdAt:stamp,commentId:items.at(-1).commentId}:null}}
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
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/logout')){state.member=null;return r.fulfill({status:204})}
  if(u.pathname.endsWith('/login')){state.member={...state.loginMember};return r.fulfill({json:state.member})}
  if(u.pathname.endsWith('/members/me'))return r.fulfill({json:{...state.member,createdAt:stamp}})
  if(u.pathname.endsWith('/activity-summary'))return r.fulfill({json:{postCount:posts.length,commentCount:1}})
  if(u.pathname.endsWith('/members/me/posts')){const all=posts.filter(p=>!u.searchParams.has('afterId')||p.id<Number(u.searchParams.get('afterId'))),items=all.slice(0,10),hasNext=all.length>10;return r.fulfill({json:{items,hasNext,nextCursor:hasNext?{createdAt:stamp,postId:items.at(-1).id}:null}})}
  if(u.pathname.endsWith('/members/me/comments'))return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}})
  if(method==='GET')return state.get?state.get(r,u):r.fulfill(read(u))
  if(state.write)return state.write(r,u)
  const id=Number(u.pathname.split('/')[5]),cid=Number(u.pathname.split('/').at(-2)),c=comments.get(id)?.find(c=>c.commentId===cid)
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
 for(const width of [1440,390,320])await check(`lazy independent regions, zero/one, IDs and layout ${width}`,async({p,state})=>{
  assert.equal(state.calls.filter(c=>c.path.endsWith('/comments')&&c.path.includes('/community/')).length,0)
  await open(p,998,0);await area(p,998).getByText('아직 댓글이 없어요.',{exact:true}).waitFor()
  await open(p,999,1);await open(p,1000,20)
  assert.equal(reads(state,1000).length,1)
  const ids=await p.locator('[id]').evaluateAll(es=>es.map(e=>e.id));assert.equal(ids.length,new Set(ids).size)
  assert.equal(await p.locator('[id^="comment-100"]').count(),0)
  assert.equal(await area(p,1000).locator('textarea:not(:disabled),form').count(),0)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();assert.equal(await area(p,1000).isVisible(),false)
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).focus();await p.keyboard.press('Enter');await count(p,1000,20)
  assert.equal(reads(state,1000).length,1)
  assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  assert((await row(p,1000).locator('h3 a').getAttribute('href')).includes('from=%2Fmypage'))
  assert.equal(await row(p,1000).getByRole('link',{name:/상세에서 댓글 보기|게시글 상세 보기/}).count(),0)
  assert.equal(await area(p,1000).getByText('오래된 댓글부터 표시합니다.',{exact:true}).count(),0)
  assert.equal(await area(p,998).getByText('마지막 댓글까지 확인했어요.',{exact:true}).count(),0)
  assert.equal(await area(p,999).getByText('마지막 댓글까지 확인했어요.',{exact:true}).count(),0)
  assert.equal(await area(p,1000).getByRole('textbox',{name:'댓글 작성 미리보기'}).isDisabled(),true)
  assert.equal(await area(p,1000).getByRole('button',{name:'등록',exact:true}).isDisabled(),true)
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
  const refreshed=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/community/posts/1000/comments' && new URL(r.url()).searchParams.has('afterId') && r.request().method()==='GET')
  await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click()
  await (await refreshed).finished()
  await p.waitForFunction(({method})=>{const e=document.querySelector('[data-member-post-id="1000"] .mypage-inline-comments');return e.getAttribute('aria-busy')==='false' && (method==='DELETE'?!e.querySelector('[data-inline-comment="100001"]'):method==='POST'?e.querySelectorAll('[data-inline-comment]').length===40:e.textContent.includes('수정된 댓글'))},{method})
  assert.equal(reads(state,1000).length,before+2);assert.equal(reads(state,999).length,b);assert.equal(await items(p,1000).count(),40)
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
 await check('gated read, close/reopen does not duplicate or reopen on response',async({p,state})=>{
  const g=gate(state,1000);await row(p,1000).getByRole('button',{name:/^댓글 \d/}).click();await g.reached
  const toggle=row(p,1000).getByRole('button',{name:/^댓글 \d/});await toggle.click();await toggle.click();await toggle.click()
  assert.equal(reads(state,1000).length,1);await open(p,999,1)
  await g.release();await count(p,1000,20);assert.equal(await area(p,1000).isVisible(),false)
  assert.equal(await toggle.getAttribute('aria-expanded'),'false')
  await toggle.click();assert.equal(reads(state,1000).length,1)
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
