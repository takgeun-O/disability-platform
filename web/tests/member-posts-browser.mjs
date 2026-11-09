// Controlled Spring-shaped responses for browser + Next server reads. Never touches the real DB.
import http from 'node:http'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8452',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-posts-browser'
await fs.mkdir(out,{recursive:true})
const stamp='2026-10-20T00:00:00.123456Z',member={memberId:321,nickname:'검토회원',email:'review@example.test',role:'USER'}
const make=id=>({visibility:"VISIBLE",id,type:'QUESTION',topic:'HEARING_AID',title:`[검토 예시] 일상에서 나눈 질문 ${id}`,content:'상세 본문\n'.repeat(30),contentPreview:'서로의 경험을 나누며 일상에 도움이 되는 방법을 찾고 있어요.',likeCount: 0, liked: false, author:{memberId:321,nickname:'검토회원'},createdAt:stamp,updatedAt:stamp,commentCount:id%3})
const failure=code=>({code,message:'test error',fieldErrors:[]})
let posts=[],comments=[]
function publicRead(u){
 if(u.pathname.endsWith('/comments'))return{json:{items:comments,hasNext:false,nextCursor:null}}
 if(u.pathname.endsWith('/feed')){const items=posts.slice(0,20);return{json:{items,hasNext:posts.length>20,nextCursor:posts.length>20?{createdAt:stamp,postId:items.at(-1).id}:null}}}
 const post=posts.find(p=>p.id===Number(u.pathname.split('/').at(-1)))
 if(!post)return{status:404,json:failure('POST_NOT_FOUND')}
 const detail={...post};delete detail.commentCount;delete detail.contentPreview;return{json:detail}
}
const server=http.createServer((req,res)=>{assert(!req.headers.cookie);assert(!req.headers.authorization);const data=publicRead(new URL(req.url,'http://mock'));res.writeHead(data.status??200,{'Content-Type':'application/json'});res.end(JSON.stringify(data.json))})
await new Promise(r=>server.listen(8858,'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function until(fn){for(let i=0;i<120;i++){if(await fn())return;await pause(50)}throw Error('condition not reached')}
const rows=p=>p.locator('[data-member-post-id]')
const calls=s=>s.calls.filter(c=>c.path==='/api/v1/members/me/posts')
function listing(u){let items=posts.filter(p=>!u.searchParams.has('afterId')||p.id<Number(u.searchParams.get('afterId')));const hasNext=items.length>10;items=items.slice(0,10);return{items,hasNext,nextCursor:hasNext?{createdAt:items.at(-1).createdAt,postId:items.at(-1).id}:null}}
async function fixture({width=1440,count=27,...options}={}){
 posts=Array.from({length:count},(_,i)=>make(1000-i));comments=[]
 const ctx=await browser.newContext({viewport:{width,height:900},reducedMotion:'no-preference'}),p=await ctx.newPage();p.setDefaultTimeout(8000)
 const state={member:{...member},profile:{...member,createdAt:stamp},calls:[],get:null,write:null,summary:null,...options}
 await p.route(u=>u.pathname.startsWith('/api/v1/'),async r=>{
  const req=r.request(),u=new URL(req.url()),method=req.method();state.calls.push({path:u.pathname,url:u.href,method})
  if(u.pathname==='/api/v1/auth/me')return r.fulfill(state.member?{json:state.member}:{status:401,json:failure('AUTHENTICATION_REQUIRED')})
  if(u.pathname.endsWith('/csrf')&&state.csrf)return state.csrf(r)
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'test-only'}})
  if(u.pathname.endsWith('/logout')){state.member=null;return r.fulfill({status:204})}
  if(u.pathname.endsWith('/login')){state.member=state.profile;return r.fulfill({json:state.member})}
  if(u.pathname==='/api/v1/members/me')return r.fulfill({json:state.profile})
  if(u.pathname.endsWith('/activity-summary'))return state.summary?state.summary(r):r.fulfill({json:{postCount:posts.length,commentCount:comments.length}})
  if(u.pathname==='/api/v1/members/me/posts')return state.get?state.get(r,u):r.fulfill({json:listing(u)})
  if(u.pathname==='/api/v1/members/me/nickname'){state.member.nickname=req.postDataJSON().nickname;state.profile.nickname=state.member.nickname;return r.fulfill({json:state.profile})}
  if(u.pathname==='/api/v1/members/me/comments')return r.fulfill({json:{items:state.ownedComments?.()??[],hasNext:false,nextCursor:null}})
  if(method==='GET')return r.fulfill(publicRead(u))
  assert.equal(req.headers()['x-csrf-token'],'test-only')
  if(state.write)return state.write(r,u)
  const id=Number(u.pathname.split('/')[5]),post=posts.find(p=>p.id===id)
  if(u.pathname.endsWith('/comments')&&method==='POST'){post.commentCount++;comments.push({ contentUpdatedAt: null,likeCount:0,liked:false,commentId:1,content:req.postDataJSON().content,author:post.author,createdAt:stamp,updatedAt:stamp});return r.fulfill({status:201,json:{commentId:1}})}
  if(u.pathname.includes('/comments/')){if(method==='DELETE'){post.commentCount--;comments=[]}else comments[0].content=req.postDataJSON().content;return r.fulfill({status:204})}
  if(method==='DELETE'){posts=posts.filter(p=>p.id!==id);return r.fulfill({status:204})}
  if(method==='PUT'){Object.assign(post,req.postDataJSON());post.contentPreview=post.content.slice(0,200);return r.fulfill({status:204})}
  if(method==='POST'){posts.unshift({...make(1001),...req.postDataJSON()});return r.fulfill({status:201,json:{id:1001}})}
  return r.fulfill({status:500,json:failure('UNEXPECTED')})
 })
 await p.goto(base+'/mypage');return{ctx,p,state}
}
async function ready(p,n=10){await until(async()=>await rows(p).count()===n);await p.getByRole('link',{name:'프로필·계정 설정',exact:true}).waitFor()}
async function more(p,n){for(let i=1;i<n;i++){await p.getByRole('button',{name:'더 보기',exact:true}).click();await until(async()=>await rows(p).count()===Math.min((i+1)*10,posts.length))}}
async function enter(p,id=976){const row=p.locator(`[data-member-post-id="${id}"]`);await row.evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-120));const y=await p.evaluate(()=>scrollY);await p.evaluate(id=>{window.__frames=[];window.__watch=true;const tick=()=>{if(!window.__watch)return;if(location.pathname===`/community/posts/${id}`&&document.querySelector('.community-post-body'))window.__frames.push(scrollY);requestAnimationFrame(tick)};requestAnimationFrame(tick)},id);await row.locator('h3 a').click();await p.locator('.community-post-body').waitFor();await p.waitForFunction(()=>window.__frames.length>=5);const frames=await p.evaluate(()=>{window.__watch=false;return window.__frames});assert(frames.every(y=>y===0),JSON.stringify(frames));assert.equal(new URL(p.url()).searchParams.get('from'),'/mypage');return y}
async function back(p,kind='back'){if(kind==='back')await p.goBack();else {const link=p.getByRole('link',{name:'← 마이페이지로 돌아가기',exact:true});assert.equal(await link.getAttribute('href'),'/mypage');await link.click()}await p.waitForURL(u=>u.pathname==='/mypage')}
// The response is released by the test, never by a timing assumption.
function heldPosts(state) {
 let release
 const requested=new Promise(resolve=>{state.get=async(r,u)=>{await new Promise(done=>{release=async(payload)=>{await r.fulfill(payload??{json:listing(u)});done()};resolve()})}})
 return{requested,respond:payload=>release(payload)}
}
const retry=p=>p.getByRole('button',{name:/^내 게시글 (더 보기 )?다시 시도$/})
const focused=handle=>handle.evaluate(e=>e.isConnected&&document.activeElement===e)
async function failedMore(p,state,keyboard=false) {
 state.get=r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})
 const button=p.getByRole('button',{name:'더 보기',exact:true})
 if(keyboard){await button.focus();await p.keyboard.press('Enter')}else await button.click()
 await retry(p).waitFor()
}
async function beginRetry(p,state,keyboard=true) {
 const gate=heldPosts(state),button=retry(p),handle=await button.elementHandle()
 if(keyboard){await button.focus();await p.keyboard.press('Enter')}else await button.click()
 await gate.requested
 return{gate,handle}
}
async function check(name,fn){try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.stack});console.log('FAIL',name,e.message);process.exitCode=1;const p=browser.contexts().at(-1)?.pages()[0];if(p){await p.screenshot({path:`${out}/failure-${results.length}.png`}).catch(()=>{});console.log('STATE',await p.evaluate(()=>({url:location.href,y:scrollY,rows:document.querySelectorAll('[data-member-post-id]').length,alerts:[...document.querySelectorAll('[role=alert]')].map(e=>e.textContent)})).catch(()=>({})))}}finally{for(const c of browser.contexts())await c.close()}}
try{
 await check('management: CSRF preparation failure sends no deletion and can retry',async()=>{
  const{p,state}=await fixture();await ready(p);state.csrf=r=>r.abort('failed');await rows(p).first().getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await p.getByRole('dialog').getByRole('alert').waitFor();assert.equal(state.calls.filter(c=>c.method==='DELETE').length,0);assert.equal(calls(state).length,1);state.csrf=null;await p.getByRole('button',{name:'삭제하기',exact:true}).click();await until(async()=>!await p.locator('[data-member-post-id="1000"]').count())
 })
 await check('management: deletion refresh failure preserves range; retry and related comments refresh',async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3)
  state.ownedComments=()=>posts.some(x=>x.id===978)?[{ contentUpdatedAt: null,visibility:"VISIBLE",commentId:12,content:'내 댓글',createdAt:stamp,updatedAt:stamp,likeCount:2,liked:true,post:{postId:978,title:'원문',topic:'HEARING_AID',type:'QUESTION'}}]:[]
  await p.getByRole('tab',{name:/내 댓글/}).click();await p.locator('[data-member-comment-id="12"]').waitFor();await p.getByRole('tab',{name:/내 게시글/}).click();state.get=r=>r.fulfill({status:503,json:failure('SERVICE_UNAVAILABLE')})
  await p.locator('[data-member-post-id="978"]').getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await retry(p).waitFor();assert.equal(await rows(p).count(),27);assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1)
  state.get=null;await retry(p).click();await ready(p,26);await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByText('아직 작성한 댓글이 없어요.').waitFor();assert.equal(await p.locator('[data-member-comment-id]').count(),0);assert.equal(state.calls.filter(c=>c.path==='/api/v1/members/me/comments').length,2)
 })
 for(const way of ['cancel','back'])await check(`management: edit ${way} keeps unsaved confirmation and range`,async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3);await rows(p).nth(22).getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'수정',exact:true}).click();await p.locator('input[name=title]').fill('취소 초안');if(way==='back')await p.goBack();else await p.getByRole('button',{name:'취소',exact:true}).click();await p.getByRole('dialog',{name:'수정 중인 내용이 있습니다.'}).waitFor();await p.getByRole('button',{name:'나가기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await ready(p,27);assert.equal(state.calls.filter(c=>c.method==='PUT').length,0)
 })
 for(const width of [1440,390,320])await check(`management: keyboard/menu/layout ${width}`,async()=>{
  const{p,state}=await fixture({width});await ready(p);const first=rows(p).first(),button=first.getByRole('button',{name:'게시글 관리',exact:true})
  await button.focus();await p.keyboard.press('Enter');await p.getByRole('menu').waitFor();const menuBox=await p.getByRole('menu').boundingBox();assert(menuBox.x>=0&&menuBox.x+menuBox.width<=width);assert.equal(await p.getByRole('menu').count(),1);assert(await p.getByRole('menuitem',{name:'수정',exact:true}).evaluate(e=>e===document.activeElement))
  await p.keyboard.press('ArrowDown');assert(await p.getByRole('menuitem',{name:'삭제',exact:true}).evaluate(e=>e===document.activeElement));await p.screenshot({path:`${out}/menu-${width}.png`})
  await p.keyboard.press('Escape');assert(await button.evaluate(e=>e===document.activeElement));assert.equal(await p.getByRole('menu').count(),0)
  await button.click();await rows(p).nth(1).getByRole('button',{name:'게시글 관리',exact:true}).click();assert.equal(await p.getByRole('menu').count(),1);await p.getByRole('heading',{name:'내 활동',exact:true}).click();assert.equal(await p.getByRole('menu').count(),0)
  await button.click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('dialog',{name:'게시글을 삭제할까요?'}).waitFor();assert(await p.locator('.mypage-delete-title').getByText(posts[0].title,{exact:true}).isVisible());assert.equal(state.calls.filter(c=>c.method==='DELETE').length,0);await p.screenshot({path:`${out}/delete-${width}.png`})
  await p.getByRole('button',{name:'취소',exact:true}).click();assert(await button.evaluate(e=>e===document.activeElement));assert.equal(new URL(p.url()).pathname,'/mypage');assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/feed-${width}.png`})
 })
 for(const id of [1000,978])await check(`management: confirmed delete ${id} retains range/neighbor focus`,async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3);const row=p.locator(`[data-member-post-id="${id}"]`);await row.evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-120));const before=calls(state).length
  await row.getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await until(async()=>await rows(p).count()===26)
  await p.waitForFunction(id=>document.activeElement?.closest('[data-member-post-id]')?.getAttribute('data-member-post-id')===String(id-1),id)
  assert.equal(calls(state).length-before,3);assert.equal(await row.count(),0);assert.equal(await p.locator('.mypage-counts strong').first().innerText(),'26');assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1)
  if(id===978)assert(Math.abs(await p.locator('[data-member-post-id="977"]').evaluate(e=>e.getBoundingClientRect().top)-120)<20)
 })
 await check('management: final post deletion focuses empty state',async()=>{
  const{p}=await fixture({count:1});await ready(p,1);await rows(p).getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await p.waitForFunction(()=>document.activeElement?.id==='my-posts-empty');assert.equal(await rows(p).count(),0);await p.screenshot({path:`${out}/empty-after-delete.png`})
 })
 for(const [status,code] of [[403,'ACCESS_DENIED'],[403,'CSRF_TOKEN_INVALID'],[404,'OTHER_NOT_FOUND']])await check(`management: definite ${code} keeps row and error`,async()=>{
  const{p,state}=await fixture();await ready(p);state.write=r=>r.fulfill({status,json:failure(code)});await rows(p).first().getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await p.getByRole('dialog').getByRole('alert').waitFor();assert.equal(await rows(p).count(),10);assert.equal(calls(state).length,status===404?2:1);assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1);assert.equal(await p.getByRole('heading',{name:'로그인이 필요해요'}).count(),0)
  if(status!==404){state.write=null;await p.getByRole('button',{name:'삭제하기',exact:true}).click();await until(async()=>!await p.locator('[data-member-post-id="1000"]').count());assert.equal(state.calls.filter(c=>c.method==='DELETE').length,2)}
 })
 await check('management: current 401 clears private lists',async()=>{
  const{p,state}=await fixture();await ready(p);state.write=r=>r.fulfill({status:401,json:failure('AUTHENTICATION_REQUIRED')});await rows(p).first().getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await p.getByRole('heading',{name:'로그인이 필요해요'}).waitFor();assert.equal(await rows(p).count(),0)
 })
 for(const outcome of ['network','500','missing'])await check(`management: ${outcome} rereads server without replay`,async()=>{
  const{p,state}=await fixture();await ready(p);state.write=async r=>{if(outcome!=='500')posts=posts.filter(x=>x.id!==1000);return outcome==='network'?r.abort('failed'):r.fulfill({status:outcome==='missing'?404:500,json:failure(outcome==='missing'?'POST_NOT_FOUND':'INTERNAL_SERVER_ERROR')})}
  await rows(p).first().getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await until(()=>calls(state).length===2)
  if(outcome==='500'){await p.getByRole('dialog').getByRole('alert').waitFor();assert.equal(await rows(p).count(),10);assert.equal(await p.getByRole('button',{name:'삭제하기',exact:true}).count(),0)}else await until(async()=>!await p.locator('[data-member-post-id="1000"]').count())
  assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1)
 })
 await check('management: pending delete blocks duplicate and close',async()=>{
  const{p,state}=await fixture();await ready(p);let release,arrived;const gate=new Promise(r=>release=r),request=new Promise(r=>arrived=r);state.write=async r=>{arrived();await gate;posts=posts.filter(x=>x.id!==1000);await r.fulfill({status:204})}
  await rows(p).first().getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).dblclick();await request;await p.keyboard.press('Escape');await p.getByRole('button',{name:'취소',exact:true}).evaluate(e=>e.click());assert(await p.getByRole('dialog').isVisible());assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1);release();await until(async()=>!await p.locator('[data-member-post-id="1000"]').count())
 })
 for(const outcome of ['success','network','401'])for(const authChange of ['leave','logout','same','other'])await check(`management: late ${outcome} deletion after ${authChange}`,async()=>{
  const{p,state}=await fixture();await ready(p);await p.getByRole('link',{name:'IYUM 홈으로 이동',exact:true}).click();await p.locator('.iyum-desktop-auth').getByRole('link',{name:'마이페이지',exact:true}).click();await ready(p)
  let release,arrived,done;const gate=new Promise(r=>release=r),request=new Promise(r=>arrived=r),finished=new Promise(r=>done=r);state.write=async r=>{arrived();await gate;if(outcome!=='401')posts=posts.filter(x=>x.id!==1000);if(outcome==='network')await r.abort('failed');else await r.fulfill(outcome==='401'?{status:401,json:failure('AUTHENTICATION_REQUIRED')}:{status:204});done()}
  await rows(p).first().getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await request;await p.goBack();await p.waitForURL(u=>u.pathname==='/')
  if(authChange!=='leave'){await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그아웃되었습니다.',{exact:true}).waitFor()}
  if(['same','other'].includes(authChange)){if(authChange==='other'){state.profile={...state.profile,memberId:322,nickname:'다른회원'};posts=posts.map((_,i)=>({...make(2000-i),author:{memberId:322,nickname:'다른회원'}}))};await p.locator('.iyum-desktop-auth').getByRole('link',{name:'로그인',exact:true}).click();await p.locator('input[name=email]').fill(member.email);await p.locator('input[name=password]').fill('test-only');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname!=='/login');await p.locator('.iyum-desktop-auth').getByRole('link',{name:'마이페이지',exact:true}).click();await ready(p)}
  const before=calls(state).length;release();await finished;await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));assert.equal(calls(state).length,before);assert.equal(await p.getByRole('dialog').count(),0);assert.equal(state.calls.filter(c=>c.method==='DELETE').length,1);if(['same','other'].includes(authChange)){assert.equal(await p.locator(`[data-member-post-id="${authChange==='other'?2000:1000}"]`).count(),1);assert.equal(await p.locator('.mypage-name h2').innerText(),state.profile.nickname)}
  if(authChange==='leave'){await p.locator('.iyum-desktop-auth').getByRole('link',{name:'마이페이지',exact:true}).click();await ready(p);if(outcome!=='401')await until(async()=>!await p.locator('[data-member-post-id="1000"]').count());else assert.equal(await p.locator('[data-member-post-id="1000"]').count(),1)}
 })
 await check('management: edit uses full detail, failure/draft guard, success returns full range',async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3);const row=p.locator('[data-member-post-id="978"]');await row.evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-120));await row.getByRole('button',{name:'게시글 관리'}).click();await p.getByRole('menuitem',{name:'수정',exact:true}).click();await p.locator('textarea[name=content]').waitFor();assert.equal(await p.locator('textarea[name=content]').inputValue(),posts.find(x=>x.id===978).content)
  await p.locator('input[name=title]').fill('관리 메뉴에서 바꾼 제목');await p.getByRole('button',{name:'취소',exact:true}).click();await p.getByRole('dialog',{name:'수정 중인 내용이 있습니다.'}).waitFor();await p.getByRole('button',{name:'계속 수정',exact:true}).click();assert.equal(await p.locator('input[name=title]').inputValue(),'관리 메뉴에서 바꾼 제목')
  state.write=r=>r.fulfill({status:403,json:failure('ACCESS_DENIED')});await p.getByRole('button',{name:'수정 저장',exact:true}).click();await p.getByRole('form',{name:'게시글 수정'}).getByRole('alert').waitFor();assert.equal(await p.locator('input[name=title]').inputValue(),'관리 메뉴에서 바꾼 제목');state.write=null;await p.getByRole('button',{name:'수정 저장',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await ready(p,27);await p.getByRole('link',{name:'관리 메뉴에서 바꾼 제목',exact:true}).waitFor();assert(Math.abs(await p.locator('[data-member-post-id="978"]').evaluate(e=>e.getBoundingClientRect().top)-120)<20)
 })

 for(const way of ['back','link'])await check(`settings round trip retains three post pages and position / ${way}`,async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3)
  await p.locator('[data-member-post-id="978"]').evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-120));const y=await p.evaluate(()=>scrollY),before=calls(state).length
  await p.getByRole('link',{name:'프로필·계정 설정',exact:true}).evaluate(e=>e.click());await p.getByRole('button',{name:'프로필 수정',exact:true}).waitFor()
  await back(p,way);await ready(p,27);await p.waitForFunction(y=>Math.abs(scrollY-y)<4,y)
  assert.equal(await p.getByRole('tab',{name:/내 게시글/}).getAttribute('aria-selected'),'true');assert.equal(calls(state).length,before);assert.equal(await rows(p).count(),27)
 })
 await check('first ten real fields/order and zero count; no CSRF/per-post/comment list reads',async()=>{
  const{p,state}=await fixture();await ready(p);assert((await rows(p).first().innerText()).includes('보청기 · 질문·답변'));assert((await rows(p).first().innerText()).includes('서로의 경험'));assert(await p.getByText('댓글 0',{exact:true}).first().isVisible());assert.equal(calls(state).length,1);assert(!state.calls.some(c=>c.path.endsWith('/csrf')||c.path.includes('/posts/')))
  await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByText('아직 작성한 댓글이 없어요.').waitFor();await p.getByRole('tab',{name:/내 게시글/}).click();assert.equal(calls(state).length,1);assert.equal(await rows(p).count(),10)
 })
 await check('summary zero does not suppress posts; summary failure does not hide list',async()=>{
  const{p,state}=await fixture({summary:r=>r.fulfill({json:{postCount:0,commentCount:0}})});await ready(p);assert.equal(await p.locator('.mypage-counts strong').first().innerText(),'0');state.summary=r=>r.fulfill({status:503,json:failure('SERVER_ERROR')});await p.reload();await ready(p);await p.getByRole('button',{name:'활동 개수 다시 시도'}).waitFor();assert.equal(await rows(p).count(),10)
 })
 await check('actual empty vs first error retry, profile remains',async()=>{
  const{p,state}=await fixture({count:0,get:r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})});await p.getByRole('button',{name:'내 게시글 다시 시도',exact:true}).waitFor();assert.equal(await p.getByText('아직 작성한 게시글이 없어요.').count(),0);assert(await p.getByRole('link',{name:'프로필·계정 설정'}).isVisible());state.get=null;await p.getByRole('button',{name:'내 게시글 다시 시도',exact:true}).click();await p.getByText('아직 작성한 게시글이 없어요.').waitFor()
 })
 await check('more failure retains ten and exact cursor; retry/duplicate click; final keyboard focus',async()=>{
  const{p,state}=await fixture();await ready(p);state.get=r=>r.fulfill({status:500,json:failure('SERVER_ERROR')});await p.getByRole('button',{name:'더 보기',exact:true}).click();await p.getByRole('button',{name:'내 게시글 더 보기 다시 시도'}).waitFor();assert.equal(await rows(p).count(),10);const failed=calls(state).at(-1).url
  state.get=null;await p.getByRole('button',{name:'내 게시글 더 보기 다시 시도'}).click();await ready(p,20);assert.equal(calls(state).at(-1).url,failed);assert.equal(new URL(failed).searchParams.get('afterCreatedAt'),stamp);assert.equal(new URL(failed).searchParams.get('afterId'),'991')
  let release;state.get=async(r,u)=>{await new Promise(resolve=>release=resolve);await r.fulfill({json:listing(u)})};await p.getByRole('button',{name:'더 보기',exact:true}).focus();await p.keyboard.press('Enter');await until(()=>release);await p.keyboard.press('Enter');const n=calls(state).length;release();await ready(p,27);assert.equal(calls(state).length,n);assert.equal(await p.getByRole('button',{name:'더 보기',exact:true}).count(),0);assert.equal(await p.evaluate(()=>document.activeElement.closest('[data-member-post-id]')?.dataset.memberPostId),'980');assert(await p.getByText('7개를 더 불러왔습니다. 마지막 게시글입니다.').isVisible())
 })
 await check('keyboard more failure preserves the same button; retry pending and repeated failure retain focus; success focuses new title',async()=>{
  const{p,state}=await fixture();await ready(p)
  const button=p.getByRole('button',{name:'더 보기',exact:true}),handle=await button.elementHandle(),first=heldPosts(state)
  await button.focus();await p.keyboard.press('Enter');await first.requested;assert(await focused(handle),'pending more lost focus')
  await first.respond({status:503,json:failure('SERVER_ERROR')});await retry(p).waitFor();assert(await focused(handle),'failed more removed focused button')
  const failed=calls(state).at(-1).url,{gate}=await beginRetry(p,state);assert(await focused(handle),'pending retry removed focused button')
  const n=calls(state).length;await p.keyboard.press('Enter');assert.equal(calls(state).length,n,'duplicate retry')
  await gate.respond({status:503,json:failure('SERVER_ERROR')});await retry(p).waitFor();assert(await focused(handle),'repeated failure lost focus');assert.equal(await rows(p).count(),10)
  const again=await beginRetry(p,state);assert(await focused(handle));const y=await p.evaluate(()=>scrollY)
  await again.gate.respond();await ready(p,20);assert.equal(calls(state).at(-1).url,failed)
  assert.equal(await p.evaluate(()=>document.activeElement.closest('[data-member-post-id]')?.dataset.memberPostId),'990');assert(Math.abs(await p.evaluate(()=>scrollY)-y)<3,'completion scrolled unnecessarily')
 })
 await check('keyboard retry with no added final items focuses the end notice',async()=>{
  const{p,state}=await fixture();await ready(p);await failedMore(p,state)
  const{gate,handle}=await beginRetry(p,state);assert(await focused(handle));await gate.respond({json:{items:[],hasNext:false,nextCursor:null}})
  await p.getByText('마지막 게시글까지 확인했어요.').waitFor();assert.equal(await rows(p).count(),10);assert.equal(await p.evaluate(()=>document.activeElement.id),'my-posts-end')
 })
 for(const count of [27,0])await check(`first query failure keyboard retry pending/repeated failure/success preserves focus (${count} items)`,async()=>{
  const{p,state}=await fixture({count,get:r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})});await retry(p).waitFor()
  const{gate,handle}=await beginRetry(p,state);assert(await focused(handle),'initial retry pending lost focus')
  await p.keyboard.press('Enter');assert.equal(calls(state).length,2,'initial retry duplicated request')
  await gate.respond({status:503,json:failure('SERVER_ERROR')});await retry(p).waitFor();assert(await focused(handle))
  const again=await beginRetry(p,state);assert(await focused(handle));await again.gate.respond()
  if(count){await ready(p);assert.equal(await p.evaluate(()=>document.activeElement.closest('[data-member-post-id]')?.dataset.memberPostId),'1000')}
  else{await p.getByText('아직 작성한 게시글이 없어요.').waitFor();assert.equal(await p.evaluate(()=>document.activeElement.textContent),'아직 작성한 게시글이 없어요.');assert(await p.evaluate(()=>document.activeElement.getClientRects().length>0))}
 })
 for(const initial of [false,true])for(const result of ['success','failure'])for(const move of ['Tab','Shift+Tab','click'])await check(`${initial?'first':'more'} keyboard retry: ${move} while pending keeps user's focus after ${result}`,async()=>{
  const{p,state}=await fixture(initial?{get:r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})}:{})
  if(initial)await retry(p).waitFor();else{await ready(p);await failedMore(p,state)}
  const{gate,handle}=await beginRetry(p,state);assert(await focused(handle))
  if(move==='click')await p.getByRole('tab',{name:/내 게시글/}).click();else await p.keyboard.press(move)
  const destination=await p.evaluateHandle(()=>document.activeElement);assert(!await focused(handle),'test did not move focus');assert(await destination.evaluate(e=>e!==document.body))
  await gate.respond(result==='failure'?{status:503,json:failure('SERVER_ERROR')}:undefined)
  if(result==='failure')await retry(p).waitFor();else await ready(p,initial?10:20)
  assert(await focused(destination),'response stole focus from user-selected element')
 })
 for(const result of ['success','failure'])await check(`keyboard retry: comments tab remains focused and posts stay hidden after ${result}`,async()=>{
  const{p,state}=await fixture();await ready(p);await failedMore(p,state);const{gate,handle}=await beginRetry(p,state);assert(await focused(handle))
  const tab=p.getByRole('tab',{name:/내 댓글/});await tab.click();const destination=await tab.elementHandle()
  await gate.respond(result==='failure'?{status:503,json:failure('SERVER_ERROR')}:undefined)
  await p.waitForFunction(()=>document.querySelector('#my-panel-posts > div > button[aria-disabled]')?.getAttribute('aria-disabled')==='false')
  assert(await focused(destination));assert.equal(await p.locator('#my-panel-posts').isVisible(),false)
  await p.getByRole('tab',{name:/내 게시글/}).click();assert.equal(await rows(p).count(),result==='failure'?10:20)
 })
 await check('leaving retry focus and returning before success does not re-arm automatic title focus',async()=>{
  const{p,state}=await fixture();await ready(p);await failedMore(p,state);const{gate,handle}=await beginRetry(p,state);assert(await focused(handle))
  await p.keyboard.press('Shift+Tab');assert(!await focused(handle));await p.keyboard.press('Tab');assert(await focused(handle))
  await gate.respond();await ready(p,20);assert(await focused(handle),'returning focus must not re-arm request focus')
 })
 for(const initial of [false,true])await check(`mouse ${initial?'first':'more'} retry never forces title focus`,async()=>{
  const{p,state}=await fixture(initial?{get:r=>r.fulfill({status:503,json:failure('SERVER_ERROR')})}:{})
  if(initial)await retry(p).waitFor();else{await ready(p);await failedMore(p,state)}
  const{gate,handle}=await beginRetry(p,state,false);assert(await handle.evaluate(e=>e.isConnected),'mouse retry button removed while pending')
  await gate.respond();await ready(p,initial?10:20);assert.equal(await p.evaluate(()=>document.activeElement.matches('[data-member-post-id] h3 a')),false)
 })
 for(const destination of ['leave','logout'])await check(`delayed keyboard retry after ${destination} cannot move focus into old list`,async()=>{
  const{p,state}=await fixture();await ready(p);await failedMore(p,state);const{gate,handle}=await beginRetry(p,state);assert(await focused(handle))
  if(destination==='leave'){await p.locator('.iyum-header').getByRole('link',{name:'IYUM 소개',exact:true}).click();await p.waitForURL(u=>u.pathname==='/about')}
  else{await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요').waitFor()}
  const active=await p.evaluateHandle(()=>document.activeElement);await gate.respond();await p.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
  assert.equal(await rows(p).count(),0);assert(await focused(active));assert.equal(new URL(p.url()).pathname,destination==='leave'?'/about':'/mypage')
 })
 for(const width of [1440,390,320])await check(`${width}: 3 pages, detail immediate top and back/link position, tabs, capture`,async()=>{
  const{p,state}=await fixture({width});await ready(p);await more(p,3);const requests=calls(state).length
  await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByRole('tab',{name:/내 게시글/}).click();assert.equal(calls(state).length,requests)
  const y=await enter(p);await back(p);await ready(p,27);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<12);assert.equal(calls(state).length,requests)
  await enter(p);await back(p,'link');await ready(p,27);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<12);assert.equal(calls(state).length,requests)
  await p.evaluate(async()=>{scrollTo(0,0);await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/mypage-posts-${width}.png`});await p.locator('#my-activity-title').scrollIntoViewIfNeeded();await p.screenshot({path:`${out}/posts-${width}.png`})
 })
 await check('long title/preview/large counts stay within320 and keyboard link/newtab semantics',async()=>{
  const{p}=await fixture({width:320});posts[0].title='긴제목'.repeat(25);posts[0].contentPreview='x'.repeat(200);posts[0].commentCount=Number.MAX_SAFE_INTEGER;await p.reload();await ready(p);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const a=rows(p).first().locator('h3 a');assert.equal(await a.getAttribute('href'),'/community/posts/1000?from=%2Fmypage');await a.focus();assert(await a.evaluate(e=>e.matches(':focus-visible')));await p.screenshot({path:`${out}/long-keyboard-320.png`})
 })
 for(const kind of ['link','back'])await check(`comment create updates count and summary on ${kind}, edit preserves count`,async()=>{
  const{p}=await fixture();await ready(p);await more(p,3);const y=await enter(p);const before=posts.find(p=>p.id===976).commentCount
  await p.locator('#comment-draft').fill('검토용 댓글');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await p.getByText('댓글을 등록했습니다.',{exact:true}).waitFor();await back(p,kind);await ready(p,27);await until(async()=>await p.locator('[data-member-post-id="976"] .post-comment-link').innerText()===`댓글 ${before+1}`);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<12);assert.equal(await p.locator('.mypage-counts strong').last().innerText(),'1')
  await enter(p);await p.getByRole('button',{name:'댓글 수정',exact:true}).click();await p.locator('#comment-edit-1').fill('수정한 댓글');await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await p.getByText('댓글을 수정했습니다.',{exact:true}).waitFor();await back(p,'link');await ready(p,27);assert.equal(await p.locator('[data-member-post-id="976"] .post-comment-link').innerText(),`댓글 ${before+1}`)
  await enter(p);await p.getByRole('button',{name:'댓글 삭제',exact:true}).click();await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click();await p.getByText('댓글을 삭제했습니다.',{exact:true}).waitFor();await back(p,'link');await ready(p,27);await until(async()=>await p.locator('[data-member-post-id="976"] .post-comment-link').innerText()===`댓글 ${before}`)
 })
 await check('post update fields and deletion refresh loaded list/count; deleted anchor uses next row',async()=>{
  const{p}=await fixture();await ready(p);await more(p,3);await enter(p,985)
  await p.getByRole('link',{name:'수정',exact:true}).click();await p.getByLabel('제목',{exact:true}).fill('변경된 내 게시글');await p.getByLabel('주제',{exact:true}).selectOption('COCHLEAR_IMPLANT');await p.getByLabel('글 유형',{exact:true}).selectOption('REVIEW');await p.getByRole('button',{name:'수정 저장',exact:true}).click();await p.locator('.community-post-body').waitFor();await back(p,'link');await ready(p,27);await p.getByRole('link',{name:'변경된 내 게시글',exact:true}).waitFor();assert((await p.locator('[data-member-post-id="985"]').innerText()).includes('인공와우 · 경험·후기'))
  await enter(p,985);await p.getByRole('button',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await ready(p,26);assert.equal(await p.locator('[data-member-post-id="985"]').count(),0);await until(async()=>Math.abs(await p.locator('[data-member-post-id="984"]').evaluate(e=>e.getBoundingClientRect().top)-120)<20);assert.equal(await p.locator('.mypage-counts strong').first().innerText(),'26')
 })
 await check('late comment success after approved return updates list; earlier refresh response cannot overwrite count',async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3);await enter(p);let release
  state.write=async(r)=>{await new Promise(resolve=>release=resolve);posts.find(p=>p.id===976).commentCount=9;await r.fulfill({status:201,json:{commentId:1}})}
  await p.locator('#comment-draft').fill('늦은 댓글');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await until(()=>release);await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await ready(p,27);release();await until(async()=>await p.locator('[data-member-post-id="976"] .post-comment-link').innerText()==='댓글 9');assert.equal(new URL(p.url()).pathname,'/mypage')
 })
 await check('posts401 expires session, logout/account switch ignores late list and clears old restore',async()=>{
  const{p,state}=await fixture();await ready(p);let release
  state.get=async(r,u)=>{const data=listing(u);await new Promise(resolve=>release=resolve);await r.fulfill({json:data})};await p.getByRole('button',{name:'더 보기',exact:true}).click();await until(()=>release);await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요').waitFor();release();await pause(100);assert.equal(await rows(p).count(),0)
  state.profile={...state.profile,memberId:322,nickname:'다른회원',email:'other@example.test'};state.get=null;posts=[{...make(2000),author:{memberId:322,nickname:'다른회원'}}];await p.getByRole('link',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill('other@example.test');await p.locator('input[name=password]').fill('test-only');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await ready(p,1);assert.equal(await rows(p).first().getAttribute('data-member-post-id'),'2000');assert.equal(await p.getByText('review@example.test',{exact:true}).count(),0)
  state.get=r=>r.fulfill({status:401,json:failure('AUTHENTICATION_REQUIRED')});await p.reload();await p.getByText('로그인이 필요해요').waitFor();assert.equal(await rows(p).count(),0)
 })
 await check('post create changes summary/list without changing the header UI',async()=>{
  const{p}=await fixture();await ready(p);await p.locator('.iyum-header').getByRole('link',{name:'커뮤니티',exact:true}).click();await p.getByRole('link',{name:'＋ 글쓰기',exact:true}).click();await p.locator('input[name=title]').waitFor();await p.getByLabel('글 유형',{exact:true}).selectOption('QUESTION');await p.getByLabel('주제',{exact:true}).selectOption('HEARING_AID');await p.getByLabel('제목',{exact:true}).fill('새로 작성한 내 글');await p.getByLabel('내용',{exact:true}).fill('작성 후 개인 목록에서 확인합니다.');await p.getByRole('button',{name:'등록',exact:true}).click();await p.locator('.community-post-body').waitFor();await p.locator('.iyum-desktop-auth').getByRole('link',{name:'마이페이지',exact:true}).click();await ready(p);await p.getByRole('link',{name:'새로 작성한 내 글',exact:true}).waitFor();assert.equal(await p.locator('.mypage-counts strong').first().innerText(),'28');assert.equal(await rows(p).first().getAttribute('data-member-post-id'),'1001')
 })
 await check('overlapping late successes: abort older GET, latest count wins; no detail draft reset',async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3);await enter(p);const initial=posts.find(p=>p.id===976).commentCount;const releases=[]
  state.write=async(r)=>{const n=releases.length;await new Promise(resolve=>releases.push(resolve));posts.find(p=>p.id===976).commentCount=initial+n+1;await r.fulfill({status:201,json:{commentId:n+1}})}
  await p.locator('#comment-draft').fill('첫 요청');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await until(()=>releases.length===1);await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await ready(p,27)
  await enter(p);await p.locator('#comment-draft').fill('두 번째 요청');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await until(()=>releases.length===2);await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await ready(p,27)
  let staleRelease;state.get=async(r,u)=>{const old=listing(u);await new Promise(resolve=>staleRelease=resolve);try{await r.fulfill({json:old})}catch{}};releases[0]();await until(()=>staleRelease);state.get=null;releases[1]();await until(async()=>await p.locator('[data-member-post-id="976"] .post-comment-link').innerText()===`댓글 ${initial+2}`);staleRelease();await pause(150);assert.equal(await p.locator('[data-member-post-id="976"] .post-comment-link').innerText(),`댓글 ${initial+2}`);assert.equal(await rows(p).count(),27)
 })
 await check('mutation refresh failure keeps loaded list and summary separate; retry rebuilds all3',async()=>{
  const{p,state}=await fixture();await ready(p);await more(p,3);await enter(p);await p.locator('#comment-draft').fill('변경 확인');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await p.getByText('댓글을 등록했습니다.',{exact:true}).waitFor();state.get=(r,u)=>u.searchParams.has('afterId')?r.fulfill({status:503,json:failure('SERVER_ERROR')}):r.fulfill({json:listing(u)});await back(p,'link');await p.getByRole('button',{name:'내 게시글 다시 시도',exact:true}).waitFor();assert.equal(await rows(p).count(),27);assert.equal(await p.locator('.mypage-counts strong').last().innerText(),'1');state.get=null;await p.getByRole('button',{name:'내 게시글 다시 시도',exact:true}).click();await until(async()=>await p.locator('[data-member-post-id="976"] .post-comment-link').innerText()==='댓글 2');assert.equal(await rows(p).count(),27)
 })
 await check('empty final page after deletion keeps keyboard focus on visible end notice',async()=>{
  const{p,state}=await fixture();await ready(p);state.get=r=>r.fulfill({json:{items:[],hasNext:false,nextCursor:null}});await p.getByRole('button',{name:'더 보기',exact:true}).focus();await p.keyboard.press('Enter');await p.getByText('마지막 게시글까지 확인했어요.').waitFor();assert.equal(await rows(p).count(),10);assert.equal(await p.evaluate(()=>document.activeElement.id),'my-posts-end')
 })
 await check('nickname and mobile menu remain functional with populated list',async()=>{
  const{p}=await fixture({width:390});await ready(p);await p.getByRole('link',{name:'프로필·계정 설정'}).click();await p.getByRole('button',{name:'프로필 수정',exact:true}).click();await p.locator('#my-nickname').fill('새로운회원');await p.getByRole('button',{name:'저장',exact:true}).click();await p.getByText('닉네임을 변경했습니다.').waitFor();assert.equal(await p.locator('.mypage-name h2').innerText(),'새로운회원');await p.getByRole('link',{name:'← 마이페이지로 돌아가기',exact:true}).click();await ready(p);assert.equal(await p.locator('.mypage-name h2').innerText(),'새로운회원');await rows(p).nth(6).scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY);await p.getByRole('button',{name:'메뉴 열기'}).evaluate(e=>e.click());await p.getByRole('dialog',{name:'전체 메뉴'}).waitFor();assert((await p.getByRole('dialog',{name:'전체 메뉴'}).innerText()).includes('새로운회원'));await p.keyboard.press('Escape');assert(Math.abs(await p.evaluate(()=>scrollY)-y)<3);assert.equal(await rows(p).count(),10)
 })
}finally{await browser.close();await new Promise(r=>server.close(r));await fs.writeFile(out+'/results.json',JSON.stringify({kind:'mock API / production Next',results},null,2))}
