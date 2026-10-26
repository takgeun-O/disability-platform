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
let posts=[],comments=[],publicPageSize=Infinity
const makeComment=(id,index)=>({commentId:id,content:`[검토 예시] 내가 나눈 경험 ${id}\n도움이 되었던 경험을 함께 나눠요.`,createdAt:stamp,updatedAt:stamp,postId:1000-Math.floor(index/3)})
const owned=()=>comments.filter(c=>posts.some(p=>p.id===c.postId))
const dto=c=>{const p=posts.find(p=>p.id===c.postId);return{commentId:c.commentId,content:c.content,createdAt:c.createdAt,updatedAt:c.updatedAt,post:{postId:p.id,title:p.title,type:p.type,topic:p.topic}}}
function listing(u){let items=owned().filter(c=>!u.searchParams.has('afterId')||c.commentId<Number(u.searchParams.get('afterId')));const hasNext=items.length>10;items=items.slice(0,10).map(dto);return{items,hasNext,nextCursor:hasNext?{createdAt:items.at(-1).createdAt,commentId:items.at(-1).commentId}:null}}
function publicRead(u){
 const id=Number(u.pathname.split('/')[5]),post=posts.find(p=>p.id===id)
 if(u.pathname.endsWith('/feed'))return{json:{items:posts.slice(0,10),hasNext:false,nextCursor:null}}
 if(!post)return{status:404,json:fail('POST_NOT_FOUND')}
 if(u.pathname.endsWith('/comments')) {
  const all=owned().filter(c=>c.postId===id),start=u.searchParams.has('afterId')?all.findIndex(c=>String(c.commentId)===u.searchParams.get('afterId'))+1:0
  const slice=all.slice(start,start+publicPageSize),hasNext=start+slice.length<all.length
  return{json:{items:slice.map(c=>({commentId:c.commentId,content:c.content,createdAt:c.createdAt,updatedAt:c.updatedAt,likeCount:0,liked:false,author:post.author})),hasNext,nextCursor:hasNext?{createdAt:slice.at(-1).createdAt,commentId:slice.at(-1).commentId}:null}}
 }
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
const focused=h=>h.evaluate(e=>e.isConnected&&e===document.activeElement)
async function fixture({width=1440,count=27,...options}={}){
 publicPageSize=Infinity;posts=Array.from({length:10},(_,i)=>makePost(1000-i));comments=Array.from({length:count},(_,i)=>makeComment(2000-i,i))
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
  if(u.pathname==='/api/v1/members/me/posts')return r.fulfill({json:{items:posts,hasNext:false,nextCursor:null}})
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
function held(state){let release;const requested=new Promise(resolve=>{state.get=async(r,u)=>{await new Promise(done=>{release=async payload=>{await r.fulfill(payload??{json:listing(u)});done()};resolve()})}});return{requested,respond:payload=>release(payload)}}
async function failMore(p,state){state.get=r=>r.fulfill({status:503,json:fail('SERVER_ERROR')});await more(p).focus();await p.keyboard.press('Enter');await retry(p).waitFor()}
async function detail(p,index=0){const link=rows(p).nth(index).locator('a');await link.scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY);await link.click();await p.waitForURL(u=>/^\/community\/posts\/\d+$/.test(u.pathname));await p.locator('.community-post-body').waitFor();await until(()=>p.evaluate(()=>document.activeElement?.id===location.hash.slice(1))); assert.equal(new URL(p.url()).searchParams.get('from'),'/mypage');assert.match(new URL(p.url()).hash,/^#comment-[1-9]\d*$/);return y}
async function back(p,kind='back'){if(kind==='back')await p.goBack();else {const link=p.getByRole('link',{name:'← 마이페이지로 돌아가기',exact:true});assert.equal(await link.getAttribute('href'),'/mypage');await link.click()}await p.waitForURL(u=>u.pathname==='/mypage');await until(async()=>await p.getByRole('tab',{name:/내 댓글/}).getAttribute('aria-selected')==='true')}
async function check(name,fn){if(process.env.CASE_FILTER&&!name.includes(process.env.CASE_FILTER))return;try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.stack});console.log('FAIL',name,e.message);process.exitCode=1;const p=browser.contexts().at(-1)?.pages()[0];if(p)await p.screenshot({path:`${out}/failure-${results.length}.png`}).catch(()=>{})}finally{for(const c of browser.contexts())await c.close()}}
try{
 await check('activity/account layout and explicit comment/source labels',async()=>{
  const{p}=await fixture();await open(p)
  await more(p).click();await ready(p,20);await more(p).click();await ready(p,27)
  for(const width of [1440,1024,390,320]) {
   await p.setViewportSize({width,height:900});await p.evaluate(()=>scrollTo(0,0))
   const account=await p.locator('.mypage-account').boundingBox(),activity=await p.locator('.mypage-activity').boundingBox()
   if(width>900){assert(account.x>=activity.x+activity.width);assert(Math.abs(account.y-activity.y)<2)}else assert(account.y+account.height<=activity.y)
   const first=rows(p).first();assert(await first.getByText('내가 쓴 댓글',{exact:true}).isVisible());assert(await first.getByText('원문 게시글',{exact:true}).isVisible())
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.screenshot({path:`${out}/workspace-${width}.png`})
   if(width>900){await rows(p).nth(20).scrollIntoViewIfNeeded();const sticky=await p.locator('.mypage-account').boundingBox();assert(sticky.y>=0 && sticky.y+sticky.height<900);await p.screenshot({path:`${out}/workspace-scrolled-${width}.png`})}
  }
 })
 for(const width of [1440,390]) await check(`exact comment across cursor pages and verified email ${width}`,async()=>{
  const{p,state}=await fixture({width});publicPageSize=10;comments.forEach(c=>c.postId=1000)
  await p.getByText('이메일 인증 완료',{exact:true}).waitFor();assert.equal(await p.getByText('읽기 전용',{exact:true}).count(),0)
  await open(p);await all(p);await detail(p,22)
  assert.equal(await p.evaluate(()=>document.activeElement.id),'comment-1978')
  await until(async()=>{const box=await p.locator('#comment-1978').boundingBox();return box.y>=0&&box.y+box.height<=900})
  const reads=state.calls.filter(c=>c.path==='/api/v1/community/posts/1000/comments')
  assert(reads.some(c=>new URL(c.url).searchParams.get('afterId')==='1991'));assert(reads.some(c=>new URL(c.url).searchParams.get('afterId')==='1981'))
  await p.screenshot({path:`${out}/exact-comment-${width}.png`})
  await back(p);await ready(p,27)
 })
 await check('missing linked comment explains absence without inventing a target',async()=>{
  const{p}=await fixture();await open(p);comments=comments.filter(c=>c.commentId!==2000)
  await rows(p).first().locator('a').click();await p.getByText('해당 댓글을 찾을 수 없어요.',{exact:false}).waitFor()
  assert.equal(await p.locator('#comment-2000').count(),0)
 })
 await check('lazy independent DTO, same-post distinct comments, no GET CSRF or memberId',async()=>{
  const{p,state}=await fixture();assert.equal(calls(state).length,0);await open(p);assert((await rows(p).first().innerText()).includes('보청기 · 질문·답변'));assert((await rows(p).first().innerText()).includes('내가 나눈 경험'));assert.equal(await rows(p).nth(0).locator('a').getAttribute('href'),'/community/posts/1000?from=%2Fmypage#comment-2000');assert.equal(await rows(p).nth(1).locator('a').getAttribute('href'),'/community/posts/1000?from=%2Fmypage#comment-1999')
  assert(!state.calls.some(c=>c.path.endsWith('/csrf')));assert.equal(new URL(calls(state)[0].url).search,'?size=10');await p.getByRole('tab',{name:/내 게시글/}).click();await p.getByRole('tab',{name:/내 댓글/}).click();assert.equal(calls(state).length,1)
 })
 await check('three pages raw microsecond commentId cursor, duplicates blocked and final focus',async()=>{
  const{p,state}=await fixture();await open(p);const gate=held(state);await more(p).focus();await p.keyboard.press('Enter');await gate.requested;await p.keyboard.press('Enter');assert.equal(calls(state).length,2);await gate.respond();await ready(p,20);assert.equal(await p.evaluate(()=>document.activeElement.closest('[data-member-comment-id]')?.dataset.memberCommentId),'1990');const u=new URL(calls(state)[1].url);assert.equal(u.searchParams.get('afterId'),'1991');assert.equal(u.searchParams.get('afterCreatedAt'),stamp)
  state.get=null;await more(p).focus();await p.keyboard.press('Enter');await ready(p,27);assert.equal(await more(p).count(),0);assert.equal(await p.evaluate(()=>document.activeElement.closest('[data-member-comment-id]')?.dataset.memberCommentId),'1980')
 })
 await check('stable keyboard more failure, pending retry, refailure and exact-cursor success',async()=>{
  const{p,state}=await fixture();await open(p);const h=await more(p).elementHandle();await failMore(p,state);assert(await focused(h));const failed=calls(state).at(-1).url
  for(const failing of [true,false]){const gate=held(state);await p.keyboard.press('Enter');await gate.requested;assert(await focused(h));await gate.respond(failing?{status:503,json:fail('SERVER_ERROR')}:undefined);if(failing){await retry(p).waitFor();assert(await focused(h))}else await ready(p,20)}
  assert.equal(calls(state).at(-1).url,failed);assert.equal(await p.evaluate(()=>document.activeElement.closest('[data-member-comment-id]')?.dataset.memberCommentId),'1990')
 })
 await check('retry empty last page focuses end, initial failed retry empty focuses empty notice',async()=>{
  const{p,state}=await fixture();await open(p);await failMore(p,state);const gate=held(state);await p.keyboard.press('Enter');await gate.requested;await gate.respond({json:{items:[],hasNext:false,nextCursor:null}});await until(async()=>await p.evaluate(()=>document.activeElement.id)==='my-comments-end')
  await p.reload();state.get=r=>r.fulfill({status:503,json:fail('SERVER_ERROR')});await p.getByRole('tab',{name:/내 댓글/}).click();await retry(p).waitFor();const initial=held(state),h=await retry(p).elementHandle();await retry(p).focus();await p.keyboard.press('Enter');await initial.requested;assert(await focused(h));await initial.respond({json:{items:[],hasNext:false,nextCursor:null}});await until(async()=>await p.evaluate(()=>document.activeElement.id)==='my-comments-empty')
 })
 for(const outcome of ['success','failure'])for(const away of ['Tab','Shift+Tab','click','tab'])await check(`pending retry ${outcome}, user ${away}: no focus theft`,async()=>{
  const{p,state}=await fixture();await open(p);await failMore(p,state);const gate=held(state);await p.keyboard.press('Enter');await gate.requested
  if(away==='tab')await p.getByRole('tab',{name:/내 게시글/}).click();else if(away==='click')await p.locator('#my-activity-title').click();else await p.keyboard.press(away)
  const handle=await p.evaluateHandle(()=>document.activeElement);await gate.respond(outcome==='failure'?{status:503,json:fail('SERVER_ERROR')}:undefined);await pause(100);assert(await handle.evaluate(e=>document.activeElement===e));if(away==='tab')assert.equal(await p.getByRole('tab',{name:/내 게시글/}).getAttribute('aria-selected'),'true')
 })
 await check('mouse retry never focuses result link',async()=>{const{p,state}=await fixture();await open(p);await failMore(p,state);state.get=null;await retry(p).click();await ready(p,20);assert.equal(await p.evaluate(()=>document.activeElement.classList.contains('mypage-comment-link')),false)})
 await check('zero summary and summary error do not hide comments; actual empty remains distinct',async()=>{
  const{p,state}=await fixture({summary:r=>r.fulfill({json:{postCount:10,commentCount:0}})});await open(p);assert.equal(await rows(p).count(),10);state.summary=r=>r.fulfill({status:503,json:fail('SERVER_ERROR')});await p.reload();await open(p);assert(await p.getByRole('button',{name:'활동 개수 다시 시도'}).isVisible());comments=[];await p.reload();await open(p,0);await p.getByText('아직 작성한 댓글이 없어요.').waitFor()
 })
 for(const kind of ['back','link'])await check(`27 comments exact comment focus + ${kind} tab/list/scroll restore and no reread`,async()=>{
  const{p,state}=await fixture();await open(p);await all(p);const n=calls(state).length,y=await detail(p,22);await back(p,kind);await ready(p,27);await until(async()=>Math.abs(await p.evaluate(()=>scrollY)-y)<4);assert.equal(calls(state).length,n);assert.equal(await rows(p).nth(22).getAttribute('data-member-comment-id'),'1978')
 })
 await check('PUT comment refreshes content not count and preserves form until return',async()=>{
  const{p,state}=await fixture();await open(p);await all(p);await detail(p,0);await p.getByRole('button',{name:'댓글 수정',exact:true}).first().click();await p.locator('#comment-edit-2000').fill('수정한 내 댓글');await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await p.getByText('댓글을 수정했습니다.',{exact:true}).waitFor();await p.locator('#comment-edit-2000').waitFor({state:'detached'});await back(p);await until(async()=>await rows(p).first().innerText().then(t=>t.includes('수정한 내 댓글')));assert.equal(await rows(p).count(),27);assert.equal(await p.locator('.mypage-counts strong').nth(1).innerText(),'27');assert.equal(calls(state).length,6)
 })
 await check('POST and DELETE comment refresh list/summary, no speculative count',async()=>{
  const{p}=await fixture();await open(p);await detail(p);await p.locator('#comment-draft').fill('새로운 검토 댓글');await p.getByRole('button',{name:'댓글 등록',exact:true}).click();await p.getByText('댓글을 등록했습니다.',{exact:true}).waitFor();await until(async()=>await p.locator('#comment-draft').inputValue()==='');await back(p);await until(async()=>await rows(p).first().innerText().then(t=>t.includes('새로운 검토 댓글')));assert.equal(await p.locator('.mypage-counts strong').nth(1).innerText(),'28');await detail(p);await p.getByRole('button',{name:'댓글 삭제',exact:true}).first().click();await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click();await until(()=>!comments.some(c=>c.commentId===3000));await back(p);await until(async()=>await p.locator('.mypage-counts strong').nth(1).innerText()==='27');await until(async()=>await rows(p).filter({hasText:'새로운 검토 댓글'}).count()===0)
 })
 await check('post edit refreshes original title/type/topic; post delete excludes all its comments',async()=>{
  const{p}=await fixture();await open(p);await detail(p);await p.getByRole('link',{name:'수정',exact:true}).click();await p.getByLabel('제목',{exact:true}).fill('변경된 원문');await p.getByLabel('글 유형',{exact:true}).selectOption('REVIEW');await p.getByLabel('주제',{exact:true}).selectOption('COCHLEAR_IMPLANT');await p.getByRole('button',{name:'수정 저장',exact:true}).click();await p.locator('.community-post-body').waitFor();await back(p,'link');await until(async()=>await rows(p).first().innerText().then(t=>t.includes('변경된 원문')&&t.includes('인공와우')));await detail(p);await p.getByRole('button',{name:'삭제',exact:true}).click();await p.getByRole('button',{name:'삭제하기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await until(async()=>await p.locator('.mypage-counts strong').nth(1).innerText()==='24');await until(async()=>await rows(p).filter({hasText:'변경된 원문'}).count()===0)
 })
 await check('refresh intermediate failure preserves old three pages; retry replaces atomically',async()=>{
  const{p,state}=await fixture();await open(p);await all(p);await detail(p);await p.getByRole('button',{name:'댓글 수정',exact:true}).first().click();await p.locator('#comment-edit-2000').fill('원자 갱신');await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await p.getByText('댓글을 수정했습니다.',{exact:true}).waitFor();await p.locator('#comment-edit-2000').waitFor({state:'detached'});state.get=(r,u)=>r.fulfill(u.searchParams.has('afterId')?{status:503,json:fail('SERVER_ERROR')}:{json:listing(u)});await back(p);await retry(p).waitFor();assert.equal(await rows(p).count(),27);assert(!(await rows(p).first().innerText()).includes('원자 갱신'));state.get=null;await retry(p).click();await until(async()=>await rows(p).first().innerText().then(t=>t.includes('원자 갱신')));assert.equal(await rows(p).count(),27)
 })
 await check('401 clears both lists, late request after logout cannot restore private data',async()=>{
  const{p,state}=await fixture();await open(p);const gate=held(state);await more(p).click();await gate.requested;await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요').waitFor();await gate.respond();await pause(100);assert.equal(await rows(p).count(),0);assert.equal(await p.locator('[data-member-post-id]').count(),0)
  state.member={...member};state.get=r=>r.fulfill({status:401,json:fail('AUTHENTICATION_REQUIRED')});await p.reload();await p.getByRole('button',{name:'프로필 수정'}).waitFor();await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByText('로그인이 필요해요').waitFor();assert.equal(await rows(p).count(),0)
 })
 await check('pending first read tab switch retains independent posts; later comments show response',async()=>{
  const{p,state}=await fixture();const gate=held(state);await p.getByRole('tab',{name:/내 댓글/}).click();await gate.requested;await p.getByRole('tab',{name:/내 게시글/}).click();await gate.respond();assert.equal(await p.locator('[data-member-post-id]').count(),10);await open(p);assert.equal(calls(state).length,1)
 })
 await check('old pending read after leaving and new session cannot mix into fresh account comments',async()=>{
  const{p,state}=await fixture();await open(p);const gate=held(state);await more(p).click();await gate.requested;await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그인이 필요해요').waitFor();const old=listing(new URL('http://test?afterId=1991'))
  state.profile={...state.profile,memberId:322,email:'second@example.test',nickname:'다른회원'};state.get=null;comments=[{...makeComment(4000,0),content:'새 회원 댓글'}];posts=posts.map(post=>({...post,author:{memberId:322,nickname:'다른회원'}}))
  await p.getByRole('link',{name:'로그인하기',exact:true}).click();await p.locator('input[name=email]').fill(state.profile.email);await p.locator('input[name=password]').fill('test-only');await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await p.getByRole('button',{name:'프로필 수정'}).waitFor();await open(p,1);await gate.respond({json:old});await pause(100);assert.equal(await rows(p).count(),1);assert((await rows(p).innerText()).includes('새 회원 댓글'))
 })
 await check('late successful PUT invalidates new comments visit; older GET cannot overwrite fresh data',async()=>{
  const{p,state}=await fixture();await open(p);await detail(p);let send
  state.write=async r=>{await new Promise(resolve=>{send=async()=>{comments[0].content='늦은 저장 최종 내용';comments[0].updatedAt='2026-10-20T01:00:00.123456Z';await r.fulfill({status:204});resolve()}})}
  await p.getByRole('button',{name:'댓글 수정',exact:true}).first().click();await p.locator('#comment-edit-2000').fill('늦은 저장 최종 내용');await p.getByRole('button',{name:'댓글 저장',exact:true}).click();await until(()=>send);await p.locator('.community-back').click();await p.getByRole('button',{name:'나가기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await ready(p)
  const gate=held(state),old=listing(new URL('http://test?afterId=1991'));await more(p).click();await gate.requested;state.get=null;await send();await until(async()=>await rows(p).first().innerText().then(t=>t.includes('늦은 저장 최종 내용')));await gate.respond({json:old});await pause(100);assert.equal(await rows(p).count(),10);assert((await rows(p).first().innerText()).includes('늦은 저장 최종 내용'));assert.equal(new URL(p.url()).pathname,'/mypage')
 })
 await check('deleted anchor uses adjacent commentId after refresh, without resetting to top',async()=>{
  const{p}=await fixture();await open(p);await all(p);const row=rows(p).nth(20);await row.evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-80));const id=Number(await row.getAttribute('data-member-comment-id')),next=rows(p).nth(21),nextId=await next.getAttribute('data-member-comment-id');const offset=await row.evaluate(e=>e.getBoundingClientRect().top)
  await row.locator('a').click();await p.locator('.community-post-body').waitFor();await p.locator(`#comment-edit-button-${id}`).waitFor();const item=p.locator(`#comment-edit-button-${id}`).locator('..');await item.getByRole('button',{name:'댓글 삭제',exact:true}).click();await p.getByRole('button',{name:'댓글 삭제하기',exact:true}).click();await p.getByText('댓글을 삭제했습니다.',{exact:true}).waitFor();await back(p,'link');await ready(p,26);await until(async()=>Math.abs(await p.locator(`[data-member-comment-id="${nextId}"]`).evaluate(e=>e.getBoundingClientRect().top)-offset)<10);assert.equal(await p.locator(`[data-member-comment-id="${id}"]`).count(),0)
 })
 for(const width of [1440,390])for(const mode of ['back','link'])await check(`missing original ${width} ${mode}: memory, three pages, group deletion and anchor`,async()=>{
  const{p,state}=await fixture({width});await open(p);await all(p)
  await p.evaluate(()=>{window.__privateMemoryProbe='alive'})
  const link=rows(p).nth(19).locator('a');await rows(p).nth(19).evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-90))
  const anchor=await rows(p).nth(19).evaluate(e=>({id:e.dataset.memberCommentId,top:e.getBoundingClientRect().top}))
  const id=Number((await link.getAttribute('href')).split('/').at(-1).split('?')[0]);posts=posts.filter(x=>x.id!==id)
  const before=calls(state).length;await link.click();await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.',exact:true}).waitFor()
  assert.equal(await p.evaluate(()=>window.__privateMemoryProbe),'alive','must not reload document')
  if(mode==='back')await p.goBack();else await p.getByRole('link',{name:'목록으로 돌아가기',exact:true}).click()
  await ready(p,24);assert.equal(await p.getByRole('tab',{name:/내 댓글/}).getAttribute('aria-selected'),'true')
  assert.equal(calls(state).length-before,3);assert.equal(await rows(p).filter({hasText:`원본 게시글 ${id}`}).count(),0)
  await until(async()=>Math.abs(await p.locator('[data-member-comment-id="1979"]').evaluate(e=>e.getBoundingClientRect().top)-anchor.top)<10)
  assert.match(await p.getByRole('tab',{name:/내 댓글/}).innerText(),/24/)
  await p.screenshot({path:`${out}/missing-return-${width}-${mode}.png`})
 })

 await check('1440/390/320 long content/title safe text, mobile menu and screenshots',async()=>{
  const{p}=await fixture();comments[0].content='<script>alert(1)</script> '+ '가'.repeat(1000);posts[0].title='긴원문제목'.repeat(24);await open(p)
  for(const width of [1440,390,320]){await p.setViewportSize({width,height:900});await p.evaluate(()=>scrollTo(0,0));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/comments-${width}.png`,fullPage:true});assert.equal(await rows(p).first().locator('script').count(),0)}
 })
}finally{await browser.close();await new Promise(r=>server.close(r));await fs.writeFile(out+'/results.json',JSON.stringify({kind:'controlled mock API on production Next; no DB writes',results},null,2))}
