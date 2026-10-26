// Production Next + controlled Spring-shaped API; no real DB writes.
import http from 'node:http'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8452',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-comments-browser'
await fs.mkdir(out,{recursive:true})
const stamp='2026-10-20T00:00:00.123456Z',member={memberId:321,nickname:'검토회원',email:'review@example.test',role:'USER'}
const fail=code=>({code,message:'test error',fieldErrors:[]})
const makePost=id=>({id,type:'QUESTION',topic:'HEARING_AID',title:`[검토 예시] 원본 게시글 ${id}`,content:'상세 본문\n'.repeat(30),contentPreview:'본문 미리보기',likeCount: 0, liked: false, author:{memberId:321,nickname:'검토회원'},createdAt:stamp,updatedAt:stamp,commentCount:0})
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
 if(u.pathname.endsWith('/comments'))return{json:{items:owned().filter(c=>c.postId===id).map(c=>({commentId:c.commentId,content:c.content,createdAt:c.createdAt,updatedAt:c.updatedAt,likeCount:0,liked:false,author:post.author})),hasNext:false,nextCursor:null}}
 const detail={...post};delete detail.commentCount;delete detail.contentPreview;return{json:detail}
}
let race=null,trace=[]
const arm=id=>{race={id,seen:0};trace=[]}
const server=http.createServer((req,res)=>{
 assert(!req.headers.cookie);assert(!req.headers.authorization)
 const u=new URL(req.url,'http://mock');let d=publicRead(u)
 if(race&&u.pathname===`/api/v1/community/posts/${race.id}`){
  race.seen++;if(race.seen===1){assert.equal(d.status??200,200);posts=posts.filter(p=>p.id!==race.id)}
  trace.push({request:race.seen,path:u.pathname,status:d.status??200,code:d.json.code??null})
 }
 res.writeHead(d.status??200,{'Content-Type':'application/json'});res.end(JSON.stringify(d.json))
})
await new Promise(r=>server.listen(8858,'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn){for(let i=0;i<120;i++){if(await fn())return;await pause(50)}throw Error('condition not reached')}
const rows=p=>p.locator('[data-member-comment-id]'),panel=p=>p.locator('#my-panel-comments')
const more=p=>panel(p).getByRole('button',{name:'더 보기',exact:true})
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
 for(const width of [1440,390])for(const mode of ['link','back'])await check(`race comments ${width} ${mode}`,async()=>{
  const{p,state}=await fixture({width});await open(p);await all(p);await rows(p).nth(19).evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-90))
  const top=await rows(p).nth(19).evaluate(e=>e.getBoundingClientRect().top),before=calls(state).length
  await p.evaluate(()=>window.__documentMarker='same');arm(994)
  const transport=[];p.on('response',r=>{if(new URL(r.url()).pathname==='/community/posts/994')transport.push({status:r.status(),rsc:r.request().headers().rsc??null,type:r.headers()['content-type']})})
  await rows(p).nth(19).locator('a').click()
  const link=p.getByRole('link',{name:'목록으로 돌아가기',exact:true});await link.waitFor()
  await fs.writeFile(`${out}/trace-${width}-${mode}.json`,JSON.stringify({trace,transport},null,2))
  assert.deepEqual(trace.slice(0,2).map(t=>t.status),[200,404]);assert.equal(await link.getAttribute('href'),'/mypage');assert.equal(await p.evaluate(()=>window.__documentMarker),'same')
  if(mode==='back')await p.goBack();else await link.click()
  await ready(p,24);assert.equal(await p.getByRole('tab',{name:/내 댓글/}).getAttribute('aria-selected'),'true');assert.equal(calls(state).length-before,3)
  assert.equal(await rows(p).filter({hasText:'원본 게시글 994'}).count(),0);assert.match(await p.getByRole('tab',{name:/내 댓글/}).innerText(),/24/)
  await until(async()=>Math.abs(await p.locator('[data-member-comment-id="1979"]').evaluate(e=>e.getBoundingClientRect().top)-top)<10)
  await p.screenshot({path:`${out}/return-${width}-${mode}.png`})
 })
 for(const width of [1440,390])for(const javascript of [false,true])await check(`race document JS=${javascript}${width===1440?'':' width=390'}`,async()=>{
  posts=[makePost(994)];comments=[];arm(994)
  const suffix=`${javascript}${width===1440?'':'-390'}`;const ctx=await browser.newContext({javaScriptEnabled:javascript,viewport:{width,height:900}}),p=await ctx.newPage();await p.route(u=>u.pathname==='/api/v1/auth/me',r=>r.fulfill({status:401,json:fail('AUTHENTICATION_REQUIRED')}));const errors=[];p.on('pageerror',e=>errors.push(e.message));const response=await p.goto(base+'/community/posts/994?from=%2Fmypage');await fs.writeFile(`${out}/document-${suffix}.html`,await response.text())
  await fs.writeFile(`${out}/document-${suffix}.json`,JSON.stringify({status:response.status(),url:p.url(),trace},null,2))
  await p.screenshot({path:`${out}/document-${suffix}.png`});await fs.writeFile(`${out}/dom-${suffix}.json`,JSON.stringify(await p.evaluate(()=>({bodyText:document.body.innerText,links:[...document.querySelectorAll('a')].filter(a=>a.textContent.includes('목록으로')).map(a=>({href:a.getAttribute('href'),visible:!!a.getClientRects().length,html:a.outerHTML})),heading:[...document.querySelectorAll('h2')].filter(h=>h.textContent.includes('게시글을 찾을')).map(h=>({visible:!!h.getClientRects().length,html:h.outerHTML}))})),null,2));assert.equal(response.status(),404);assert.deepEqual(trace.slice(0,2).map(t=>t.status),[200,404])
  assert(await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).isVisible());const link=p.getByRole('link',{name:'목록으로 돌아가기',exact:true});assert(await link.isVisible());assert.equal(await link.getAttribute('href'),'/mypage');await p.screenshot({path:`${out}/document-${suffix}.png`})
  assert(await p.getByText('삭제되었거나 없는 글입니다.',{exact:true}).isVisible())
  // A reload is another document request, not Flight; repeat the ordered race.
  posts=[makePost(994)];arm(994);const reload=await p.reload();assert.equal(reload.status(),404)
  assert.deepEqual(trace.slice(0,2).map(t=>t.status),[200,404]);assert(await link.isVisible());assert.equal(await link.getAttribute('href'),'/mypage')
  await fs.writeFile(`${out}/reload-${suffix}.json`,JSON.stringify({status:reload.status(),trace,errors},null,2))
  assert.deepEqual(errors,[])
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  await link.click();await until(()=>new URL(p.url()).pathname==='/mypage')
 })

 for(const width of [1440,390])for(const mode of ['link','back'])await check(`race posts ${width} ${mode}`,async()=>{
  const{p}=await fixture({width});posts=Array.from({length:27},(_,i)=>makePost(1000-i));await p.reload();const pr=p.locator('[data-member-post-id]'),more=p.locator('#my-panel-posts').getByRole('button',{name:'더 보기',exact:true});await until(async()=>await pr.count()===10)
  await more.click();await until(async()=>await pr.count()===20);await more.click();await until(async()=>await pr.count()===27)
  await pr.nth(19).evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-90));const top=await pr.nth(19).evaluate(e=>e.getBoundingClientRect().top);await p.evaluate(()=>window.__documentMarker='posts');arm(981)
  await pr.nth(19).locator('h3 a').click();const link=p.getByRole('link',{name:'목록으로 돌아가기',exact:true});await link.waitFor();assert.equal(await link.getAttribute('href'),'/mypage');assert.deepEqual(trace.slice(0,2).map(t=>t.status),[200,404]);assert.equal(await p.evaluate(()=>window.__documentMarker),'posts')
  if(mode==='back')await p.goBack();else await link.click();await until(async()=>await pr.count()===26);assert.equal(await p.getByRole('tab',{name:/내 게시글/}).getAttribute('aria-selected'),'true');assert.equal(await p.locator('[data-member-post-id="981"]').count(),0)
  await until(async()=>Math.abs(await p.locator('[data-member-post-id="980"]').evaluate(e=>e.getBoundingClientRect().top)-top)<10);assert.match(await p.getByRole('tab',{name:/내 게시글/}).innerText(),/26/)
 })
 for(const javascript of [false,true])for(const [from,expected]of [
  ['/community?type=QUESTION','/community?type=QUESTION'],
  ['/community/topics/hearing-aid?type=REVIEW','/community/topics/hearing-aid?type=REVIEW'],
  ['/community/posts?type=REVIEW&topic=DAILY','/community/posts?type=REVIEW&topic=DAILY'],
  [null,'/community/posts'],['https://evil.example/mypage','/community/posts'],['//evil.example','/community/posts'],['/mypage?anything=1','/community/posts'],['/community/topics/unknown','/community/posts']
 ])await check(`race from JS=${javascript} ${from}`,async()=>{
  posts=[makePost(994)];comments=[];arm(994);const ctx=await browser.newContext({javaScriptEnabled:javascript,extraHTTPHeaders:{'x-iyum-post-return':'/mypage'}}),p=await ctx.newPage()
  await p.route(u=>u.pathname==='/api/v1/auth/me',r=>r.fulfill({status:401,json:fail('AUTHENTICATION_REQUIRED')}));const response=await p.goto(base+'/community/posts/994'+(from===null?'':'?from='+encodeURIComponent(from)));assert.equal(response.status(),404);const link=p.getByRole('link',{name:'목록으로 돌아가기',exact:true});await link.waitFor();assert.equal(await link.getAttribute('href'),expected);assert.deepEqual(trace.slice(0,2).map(t=>t.status),[200,404])
 })
}finally{await browser.close();await new Promise(r=>server.close(r));await fs.writeFile(out+'/results.json',JSON.stringify({kind:'production Next with sequential controlled public API 200 then 404, no DB',results},null,2))}
