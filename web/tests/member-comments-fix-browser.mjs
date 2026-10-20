// Production Next + controlled Spring-shaped API; no real DB writes.
import http from 'node:http'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8452',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-comments-browser'
await fs.mkdir(out,{recursive:true})
const stamp='2026-10-20T00:00:00.123456Z',member={memberId:321,nickname:'검토회원',email:'review@example.test',role:'USER'}
const fail=code=>({code,message:'test error',fieldErrors:[]})
const makePost=id=>({id,type:'QUESTION',topic:'HEARING_AID',title:`[검토 예시] 원본 게시글 ${id}`,content:'상세 본문\n'.repeat(30),contentPreview:'본문 미리보기',author:{memberId:321,nickname:'검토회원'},createdAt:stamp,updatedAt:stamp,commentCount:0})
let posts=[],comments=[],publicFailure=false
const makeComment=(id,index)=>({commentId:id,content:`[검토 예시] 내가 나눈 경험 ${id}\n도움이 되었던 경험을 함께 나눠요.`,createdAt:stamp,updatedAt:stamp,postId:1000-Math.floor(index/3)})
const owned=()=>comments.filter(c=>posts.some(p=>p.id===c.postId))
const dto=c=>{const p=posts.find(p=>p.id===c.postId);return{commentId:c.commentId,content:c.content,createdAt:c.createdAt,updatedAt:c.updatedAt,post:{postId:p.id,title:p.title,type:p.type,topic:p.topic}}}
function listing(u){let items=owned().filter(c=>!u.searchParams.has('afterId')||c.commentId<Number(u.searchParams.get('afterId')));const hasNext=items.length>10;items=items.slice(0,10).map(dto);return{items,hasNext,nextCursor:hasNext?{createdAt:items.at(-1).createdAt,commentId:items.at(-1).commentId}:null}}
function publicRead(u){
 if(publicFailure)return{status:503,json:fail("SERVER_ERROR")}
 const id=Number(u.pathname.split('/')[5]),post=posts.find(p=>p.id===id)
 if(u.pathname.endsWith('/feed'))return{json:{items:posts.slice(0,10),hasNext:false,nextCursor:null}}
 if(!post)return{status:404,json:fail('POST_NOT_FOUND')}
 if(u.pathname.endsWith('/comments'))return{json:{items:owned().filter(c=>c.postId===id).map(c=>({commentId:c.commentId,content:c.content,createdAt:c.createdAt,updatedAt:c.updatedAt,author:post.author})),hasNext:false,nextCursor:null}}
 const detail={...post};delete detail.commentCount;delete detail.contentPreview;return{json:detail}
}
const server=http.createServer((req,res)=>{assert(!req.headers.cookie);assert(!req.headers.authorization);const d=publicRead(new URL(req.url,'http://mock'));res.writeHead(d.status??200,{'Content-Type':'application/json'});res.end(JSON.stringify(d.json))})
await new Promise(r=>server.listen(8858,'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn){for(let i=0;i<120;i++){if(await fn())return;await pause(50)}throw Error('condition not reached')}
const rows=p=>p.locator('[data-member-comment-id]'),panel=p=>p.locator('#my-panel-comments')
const more=p=>panel(p).getByRole('button',{name:'더 보기',exact:true})
const retry=p=>panel(p).getByRole('button',{name:/^내 댓글 (더 보기 )?다시 시도$/})
const calls=s=>s.calls.filter(c=>c.path==='/api/v1/members/me/comments')
async function fixture({width=1440,count=27,...options}={}){
 publicFailure=false;posts=Array.from({length:10},(_,i)=>makePost(1000-i));comments=Array.from({length:count},(_,i)=>makeComment(2000-i,i))
 const ctx=await browser.newContext({viewport:{width,height:900},reducedMotion:'no-preference'}),p=await ctx.newPage();p.setDefaultTimeout(8000)
 const state={member:{...member},profile:{...member,createdAt:stamp},calls:[],get:null,summary:null,write:null,...options}
 await p.route(u=>u.pathname.startsWith('/api/v1/'),async r=>{
  const req=r.request(),u=new URL(req.url()),method=req.method();state.calls.push({path:u.pathname,url:u.href,method})
  if(u.pathname==='/api/v1/auth/me')return r.fulfill(state.member?{json:state.member}:{status:401,json:fail('AUTHENTICATION_REQUIRED')})
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'test-only'}})
  if(u.pathname.endsWith('/logout')){state.member=null;return r.fulfill({status:204})}
  if(u.pathname.endsWith('/login')){state.member=state.profile;return r.fulfill({json:state.member})}
  if(u.pathname==='/api/v1/members/me')return r.fulfill({json:state.profile})
  if(u.pathname.endsWith('/activity-summary'))return state.summary?state.summary(r):r.fulfill({json:{postCount:posts.length,commentCount:owned().length}})
  if(u.pathname==='/api/v1/members/me/posts'){let items=posts.filter(x=>!u.searchParams.has('afterId')||x.id<Number(u.searchParams.get('afterId')));const hasNext=items.length>10;items=items.slice(0,10);return r.fulfill({json:{items,hasNext,nextCursor:hasNext?{createdAt:stamp,postId:items.at(-1).id}:null}})}
  if(u.pathname==='/api/v1/members/me/comments')return state.get?state.get(r,u):r.fulfill({json:listing(u)})
  if(method==='GET')return r.fulfill(publicRead(u))
  assert.equal(req.headers()['x-csrf-token'],'test-only')
  if(state.write)return state.write(r,u)
  const id=Number(u.pathname.split('/')[5]),post=posts.find(p=>p.id===id)
  if(u.pathname.endsWith('/comments')&&method==='POST'){comments.unshift({...makeComment(3000,0),postId:id,content:req.postDataJSON().content});return r.fulfill({status:201,json:{commentId:3000}})}
  if(u.pathname.includes('/comments/')){const cid=Number(u.pathname.split('/').at(-1));if(method==='DELETE')comments=comments.filter(c=>c.commentId!==cid);else{const c=comments.find(c=>c.commentId===cid);c.content=req.postDataJSON().content;c.updatedAt='2026-10-20T01:00:00.123456Z'}return r.fulfill({status:204})}
  if(method==='DELETE'){posts=posts.filter(p=>p.id!==id);return r.fulfill({status:204})}
  Object.assign(post,req.postDataJSON());return r.fulfill({status:204})
 })
 await p.goto(base+'/mypage');await p.getByRole('button',{name:'프로필 수정',exact:true}).waitFor();await until(async()=>await p.locator('[data-member-post-id]').count()===10)
 return{p,state,ctx}
}
async function open(p,n=10){await p.getByRole('tab',{name:/내 댓글/}).click();await ready(p,n)}
async function ready(p,n=10){await until(async()=>await rows(p).count()===n)}
async function all(p){await more(p).click();await ready(p,20);await more(p).click();await ready(p,27)}
async function check(name,fn){if(process.env.CASE_FILTER&&!name.includes(process.env.CASE_FILTER))return;try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.stack});console.log('FAIL',name,e.message);process.exitCode=1;const p=browser.contexts().at(-1)?.pages()[0];if(p)await p.screenshot({path:`${out}/failure-${results.length}.png`}).catch(()=>{})}finally{for(const c of browser.contexts())await c.close()}}
try{
 await check('404 return refresh failure retains 27; retry rereads three pages atomically',async()=>{
  const{p,state}=await fixture();await open(p);await all(p);await rows(p).nth(19).evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-90));posts=posts.filter(x=>x.id!==994)
  await rows(p).nth(19).locator('a').click();await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).waitFor()
  state.get=(r,u)=>u.searchParams.has('afterId')?r.fulfill({status:503,json:fail('SERVER_ERROR')}):r.fulfill({json:listing(u)})
  await p.getByRole('link',{name:'목록으로 돌아가기',exact:true}).click();await retry(p).waitFor();assert.equal(await rows(p).count(),27)
  state.get=null;await retry(p).click();await ready(p,24);assert.equal(await rows(p).filter({hasText:'원본 게시글 994'}).count(),0)
 })
 for(const mode of ['back','link'])await check(`posts origin three-page missing post ${mode}`,async()=>{
  const{p,state}=await fixture();posts=Array.from({length:27},(_,i)=>makePost(1000-i));await p.reload();await until(async()=>await p.locator('[data-member-post-id]').count()===10)
  const postRows=p.locator('[data-member-post-id]'),button=p.locator('#my-panel-posts').getByRole('button',{name:'더 보기',exact:true})
  await button.click();await until(async()=>await postRows.count()===20);await button.click();await until(async()=>await postRows.count()===27)
  await postRows.nth(19).evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-90));const offset=await postRows.nth(19).evaluate(e=>e.getBoundingClientRect().top)
  posts=posts.filter(x=>x.id!==981);await p.evaluate(()=>window.__privateMemoryProbe='posts');await postRows.nth(19).locator('h3 a').click();await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).waitFor();assert.equal(await p.evaluate(()=>window.__privateMemoryProbe),'posts')
  if(mode==='back')await p.goBack();else await p.getByRole('link',{name:'목록으로 돌아가기',exact:true}).click()
  await until(async()=>await postRows.count()===26);assert.equal(await p.getByRole('tab',{name:/내 게시글/}).getAttribute('aria-selected'),'true');assert.equal(await p.locator('[data-member-post-id="981"]').count(),0)
  await until(async()=>Math.abs(await p.locator('[data-member-post-id="980"]').evaluate(e=>e.getBoundingClientRect().top)-offset)<10);assert.match(await p.getByRole('tab',{name:/내 게시글/}).innerText(),/26/);assert(!calls(state).length)
 })
 for(const javascript of [false,true])for(const width of [390,1440])await check(`direct/reload 404 visible HTML JS=${javascript} width=${width}`,async()=>{
  const ctx=await browser.newContext({javaScriptEnabled:javascript,viewport:{width,height:900}}),p=await ctx.newPage()
  const url=base+'/community/posts/999999?from='+encodeURIComponent('https://evil.example/mypage')
  for(const reload of [false,true]){const response=reload?await p.reload():await p.goto(url);assert.equal(response.status(),404);assert(await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).isVisible());const link=p.getByRole('link',{name:'목록으로 돌아가기',exact:true});assert(await link.isVisible());assert.equal(await link.getAttribute('href'),'/community/posts');assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))}
  await p.screenshot({path:`${out}/404-${javascript}-${width}.png`});await p.getByRole('link',{name:'목록으로 돌아가기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/community/posts')
 })
 await check('RSC missing transport 200 text/x-component; document remains 404',async()=>{
  const{p}=await fixture();await open(p);posts=posts.filter(x=>x.id!==1000);const responses=[]
  p.on('response',r=>{if(new URL(r.url()).pathname==='/community/posts/1000')responses.push({status:r.status(),rsc:r.request().headers().rsc,type:r.headers()['content-type']})})
  await rows(p).first().locator('a').click();await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).waitFor()
  await fs.writeFile(out+'/transport-debug.json',JSON.stringify(responses,null,2));assert(responses.some(r=>r.rsc==='1'&&r.status===200&&r.type?.includes('text/x-component')));assert(!responses.some(r=>r.type?.includes('text/html')))
  await fs.writeFile(out+'/transport.json',JSON.stringify(responses,null,2))
 })
 await check('valid private from rendered in no-JS initial HTML',async()=>{
  const ctx=await browser.newContext({javaScriptEnabled:false}),p=await ctx.newPage();const r=await p.goto(base+'/community/posts/999999?from=%2Fmypage');assert.equal(r.status(),404)
  const link=p.getByRole('link',{name:'목록으로 돌아가기',exact:true});assert(await link.isVisible());assert.equal(await link.getAttribute('href'),'/mypage');await link.click();await p.waitForURL(u=>u.pathname==='/mypage')
 })
 await check('503 is an error, not deletion: private cache stays valid',async()=>{
  const{p,state}=await fixture();await open(p);await all(p);const count=calls(state).length;publicFailure=true
  await rows(p).nth(19).locator('a').click();await p.locator('.community-error').waitFor();assert.equal(await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).count(),0)
  publicFailure=false;await p.goBack();await ready(p,27);assert.equal(calls(state).length,count)
 })
}finally{await browser.close();await new Promise(r=>server.close(r));await fs.writeFile(out+'/results.json',JSON.stringify({kind:'controlled mock API production; no DB writes',results},null,2))}
