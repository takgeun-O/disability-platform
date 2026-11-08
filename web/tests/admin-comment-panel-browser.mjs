const openDates=async p=>{if(!await p.locator('.admin-date-range').evaluate(e=>e.open))await p.locator('.admin-date-range summary').click()}
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.COMMENT_PANEL_ARTIFACT_DIR??'/private/tmp/iyum-panel-comments/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`선택 게시글 ${id}`,content:`현재 본문 ${id}\n`+'긴 본문 검증 '.repeat(40),contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:8,targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
const comment=(id,postId=101)=>({commentId:id,postId,postStatus:'HIDDEN',content:`현재 댓글 ${id}\n`+'긴 댓글 본문 '.repeat(80),author:{...author,nickname:'탈퇴한 회원',withdrawn:true},status:'VISIBLE',createdAt:stamp,updatedAt:stamp,contentUpdatedAt:null,hiddenAt:null,deletedAt:null})
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const selectRow=(p,name)=>p.getByRole('row',{name,exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
const preview=p=>p.locator('.admin-preview-post[aria-busy="false"]').waitFor()
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),wait:new Promise(r=>release=r),arrive:()=>arrive(),release:()=>release()}}
async function setup(width=1440){
 const context=await browser.newContext({viewport:{width,height:1000}}),p=await context.newPage(),s={member:{...member},calls:[],errors:[],posts:[post(101),post(102,'HIDDEN'),post(103,'DELETED')],logs:[log(201),log(202),log(203,'COMMENT')],detail:null,list:null,commentDetail:null,commentList:null,write:null,comments:Array.from({length:21},(_,i)=>comment(900+i))}
 p.setDefaultTimeout(8000);p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const f=window.fetch;window.__masterParsed=0;window.fetch=async(u,o)=>{const r=await f(u,String(u).includes('/api/v1/admin/')?{...o,signal:undefined}:o);if(String(u).includes('/api/v1/admin/')&&r.status!==200)setTimeout(()=>window.__masterParsed++,0);if(String(u).includes('/api/v1/admin/')){const json=r.json.bind(r);r.json=async()=>{const v=await json();setTimeout(()=>window.__masterParsed++,0);return v}}return r}})
 await context.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url()),path=u.pathname,method=r.request().method();s.calls.push({path,search:u.search,method})
  if(method==='OPTIONS')return r.fulfill({status:204,headers:{'access-control-allow-origin':base,'access-control-allow-credentials':'true','access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'content-type,x-csrf-token'}})
  if(path.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:{status:401,json:{code:'AUTHENTICATION_REQUIRED'}})
  if(path.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(path.endsWith('/login')){s.member={...member,memberId:99,nickname:'다른 관리자'};return r.fulfill({json:s.member})}
  if(path.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  const single=path.match(/^\/api\/v1\/admin\/comments\/(\d+)$/)
  if(single){const id=Number(single[1]);return s.commentDetail?s.commentDetail(r,id):r.fulfill({json:comment(id)})}
  const commentWrite=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/comments\/(\d+)\/(hide|restore)$/)
  if(commentWrite&&method==='POST'){
   if(s.write)return s.write(r,commentWrite)
   const c=s.comments.find(c=>c.commentId===Number(commentWrite[2]));c.status=commentWrite[3]==='hide'?'HIDDEN':'VISIBLE';c.hiddenAt=c.status==='HIDDEN'?stamp:null
   return r.fulfill({json:{postId:Number(commentWrite[1]),commentId:c.commentId,hidden:c.status==='HIDDEN',hiddenAt:c.hiddenAt,changed:true}})
  }
  const write=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/(hide|restore)$/)
  if(write&&method==='POST'){const item=s.posts.find(x=>x.postId===Number(write[1]));item.status=write[2]==='hide'?'HIDDEN':'VISIBLE';item.hiddenAt=item.status==='HIDDEN'?stamp:null;return r.fulfill({json:{postId:item.postId,hidden:item.status==='HIDDEN',hiddenAt:item.hiddenAt,changed:true}})}
  if(path==='/api/v1/admin/posts'||path==='/api/v1/admin/operation-logs'){
   const page=Number(u.searchParams.get('page')??0),items=path.endsWith('operation-logs')?s.logs:s.posts.filter(x=>u.searchParams.getAll('statuses').includes(x.status))
   const data={items,page,size:20,totalElements:41,totalPages:3,hasNext:page<2,hasPrevious:page>0};return s.list?s.list(r,u,data):r.fulfill({json:data})
  }
  const detail=path.match(/^\/api\/v1\/admin\/posts\/(\d+)$/)
  if(detail){const id=Number(detail[1]);return s.detail?s.detail(r,id):r.fulfill({json:s.posts.find(x=>x.postId===id)??post(id)})}
  const comments=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/comments$/)
  if(comments){
   const postId=Number(comments[1]),page=Number(u.searchParams.get('page')??0),statuses=u.searchParams.getAll('statuses'),keyword=u.searchParams.get('keyword')??''
   const all=s.comments.filter(c=>statuses.includes(c.status)&&c.content.includes(keyword)),totalPages=Math.ceil(all.length/20)
   const data={postId,postStatus:s.posts.find(x=>x.postId===postId)?.status??'VISIBLE',items:all.slice(page*20,page*20+20),page,size:20,totalElements:all.length,totalPages,hasNext:page+1<totalPages,hasPrevious:page>0}
   return s.commentList?s.commentList(r,u,data):r.fulfill({json:data})
  }
  return r.fulfill({status:404,json:{code:'NOT_FOUND'}})
 });return{context,p,s}
}
async function run(name,fn){if(process.env.TEST_FILTER&&!new RegExp(process.env.TEST_FILTER).test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message)}}

const commentReady=p=>p.locator('.admin-preview .admin-comment-results[aria-busy="false"]').waitFor()
const singleReady=p=>p.locator('.admin-preview-comment[aria-busy="false"]').waitFor()
const select=async(p,id,logs=false)=>selectRow(p,logs?`기록 #${id} 상세`:`게시글 #${id} 미리보기`).locator('.admin-badge').click()
const commentReads=s=>s.calls.filter(c=>/^\/api\/v1\/admin\/posts\/\d+\/comments$/.test(c.path))
const singleReads=s=>s.calls.filter(c=>/^\/api\/v1\/admin\/comments\/\d+$/.test(c.path))
const release=async(p,g)=>{const n=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,n);await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))}
for(const width of [1440,320])await run(`comment log current content, parent return and layout ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  await p.goto(base+'/admin/operation-logs');await ready(p)
  await p.getByLabel('대상',{exact:true}).selectOption('COMMENT');await p.getByLabel('조치',{exact:true}).selectOption('HIDE');await p.getByLabel('사유 유형',{exact:true}).selectOption('OTHER');await btn(p,'검색').click();await ready(p)
  await openDates(p);await p.getByLabel('처리 시작일',{exact:true}).fill('2026-11-01');await btn(p,'기간 적용').click();await ready(p)
  await p.getByLabel('처리 시각 정렬',{exact:true}).selectOption('OLDEST');await ready(p);await btn(p,'다음').click();await ready(p)
  assert.equal(singleReads(s).length,0);await select(p,203,true);await singleReady(p)
  assert.equal(singleReads(s).at(-1).path,'/api/v1/admin/comments/900');assert.equal(s.calls.filter(c=>c.path.endsWith('/csrf')).length,0)
  const current=p.locator('.admin-preview-comment');assert((await current.innerText()).includes('현재 댓글 900'));assert((await current.innerText()).includes('탈퇴한 작성자'));assert((await current.innerText()).includes('기록 없음'))
  assert.equal(await current.locator('.admin-preview-statuses .admin-badge').allTextContents().then(x=>x.join(',')),'정상,숨김')
  assert.equal(await p.locator('.admin-log-fields .admin-badge').innerText(),'숨김');assert(await current.locator('.admin-parent-status').isVisible())
  assert.equal(await current.getByRole('button',{name:/숨김/}).count(),0)
  const link=current.getByRole('link',{name:'부모 게시글 상세 관리로 이동'}),url=p.url()
  assert((await link.getAttribute('href')).startsWith('/admin/posts/101?'));assert(!s.calls.some(c=>c.path==='/api/v1/admin/posts/900'))
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/comment-log-${width}.png`})
  await current.locator("h3").evaluate(e=>e.scrollIntoView({block:"start"}));await p.screenshot({path:`${out}/comment-current-${width}.png`})
  await link.click();await p.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor();await p.goBack();await ready(p);await singleReady(p);assert.equal(p.url(),url)
  await p.goForward();await p.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor();await p.getByRole('link',{name:'← 운영 기록으로',exact:true}).click();await ready(p);await singleReady(p);assert.equal(p.url(),url)
  const q=new URL(s.calls.filter(c=>c.path.endsWith('operation-logs')).at(-1).search,'http://local');assert.equal(q.searchParams.get('page'),'1');assert.equal(q.searchParams.get('sort'),'OLDEST');assert.equal(q.searchParams.get('reasonType'),'OTHER')
 }finally{await context.close()}
})
for(const status of ['HIDDEN','DELETED'])await run(`single comment ${status} and body edit timestamp`,async()=>{
 const{p,context,s}=await setup()
 try{
  s.commentDetail=r=>r.fulfill({json:{...comment(900),status,postStatus:'DELETED',contentUpdatedAt:stamp,hiddenAt:stamp,deletedAt:status==='DELETED'?stamp:null}})
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,203,true);await singleReady(p)
  const current=p.locator('.admin-preview-comment');assert.equal(await current.locator('.admin-preview-statuses .admin-badge').allTextContents().then(x=>x.join(',')),status==='HIDDEN'?'숨김,삭제':'삭제,삭제');assert.equal(await current.locator('dt').filter({hasText:'본문 수정'}).locator('..').locator('time').getAttribute('datetime'),stamp)
 }finally{await context.close()}
})
for(const status of [404,503])await run(`single comment ${status} preserves log and retries current target`,async()=>{
 const{p,context,s}=await setup()
 try{
  s.commentDetail=r=>r.fulfill({status,json:{code:status===404?'COMMENT_NOT_FOUND':'SERVER_ERROR'}})
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,203,true);await singleReady(p)
  assert((await p.locator('.admin-log-fields').innerText()).includes('#203'));assert((await p.locator('.admin-preview-comment [role=alert]').innerText()).includes(status===404?'찾을 수 없습니다':'불러오지 못했습니다'))
  s.commentDetail=null;await btn(p,'댓글 정보 다시 시도').click();await singleReady(p);assert((await p.locator('.admin-preview-comment').innerText()).includes('현재 댓글 900'));assert.equal(singleReads(s).length,2)
 }finally{await context.close()}
})
for(const status of [200,401,403,503])await run(`old single comment ${status} after selection cannot affect next panel`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  s.logs=[log(201,'COMMENT'),{...log(202,'COMMENT'),targetId:901}]
  s.commentDetail=async(r,id)=>{if(id===900){g.arrive();await g.wait;return r.fulfill(status===200?{json:comment(id)}:{status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ACCESS_DENIED'}})}return r.fulfill({json:comment(id)})}
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,201,true);await g.arrived;await select(p,202,true);await singleReady(p);await release(p,g)
  assert((await p.locator('.admin-preview-comment').innerText()).includes('현재 댓글 901'));assert(!(await p.locator('.admin-preview-comment').innerText()).includes('현재 댓글 900'));assert.equal(await p.locator('.admin-preview [role=alert]').count(),0);assert(await btn(p,'로그아웃').isVisible())
 }finally{g.release();await context.close()}
})
for(const status of [401,403])await run(`current comment ${status} clears records and pending content`,async()=>{
 const{p,context,s}=await setup()
 try{
  s.commentDetail=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ADMIN_REQUIRED'}})
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,203,true);await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('tbody,.admin-preview').count(),0)
 }finally{await context.close()}
})
for(const width of [1440,320])await run(`lazy comment tab filters, keyboard, paging and target reset ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);assert.equal(commentReads(s).length,0)
  await p.getByRole('tab',{name:'게시글',exact:true}).focus();const beforeTab=await p.evaluate(()=>scrollY);await p.keyboard.press('ArrowRight');await commentReady(p);assert(Math.abs((await p.evaluate(()=>scrollY))-beforeTab)<2,'tab does not move document');assert.equal(await p.getByRole('tab',{name:'댓글',exact:true}).getAttribute('aria-selected'),'true')
  assert.equal(await p.locator('.admin-preview [data-admin-comment-id]').count(),20);assert.equal(singleReads(s).length,0)
  const comments=p.locator('.admin-preview .admin-comments'),group=comments.getByRole('group',{name:'댓글 상태',exact:true})
  await group.getByRole('checkbox',{name:'숨김',exact:true}).uncheck();await commentReady(p);await group.getByRole('checkbox',{name:'삭제',exact:true}).uncheck();await commentReady(p)
  assert.deepEqual(new URL('http://local'+commentReads(s).at(-1).search).searchParams.getAll('statuses'),['VISIBLE'])
  await comments.getByLabel('댓글 본문 검색',{exact:true}).fill('현재 댓글');await btn(p,'댓글 검색').click();await commentReady(p)
  await comments.getByRole('button',{name:'다음',exact:true}).click();await commentReady(p);assert.equal(new URL('http://local'+commentReads(s).at(-1).search).searchParams.get('page'),'1')
  const count=commentReads(s).length;await p.getByRole('tab',{name:'게시글',exact:true}).click();await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p);assert.equal(commentReads(s).length,count);assert.equal(await comments.getByLabel('페이지 바로 이동',{exact:true}).inputValue(),'2')
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/comment-tab-${width}.png`})
  await select(p,102);await preview(p);assert.equal(await p.getByRole('tab',{name:'게시글',exact:true}).getAttribute('aria-selected'),'true');await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p);assert.equal(await p.locator('.admin-preview .admin-comments').getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'');assert.equal(commentReads(s).at(-1).path,'/api/v1/admin/posts/102/comments');assert.equal(new URL('http://local'+commentReads(s).at(-1).search).searchParams.get('page'),'0')
 }finally{await context.close()}
})
await run('comment moderation preserves filters and corrects last page; restore; logs remain read only',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p)
  const comments=p.locator('.admin-preview .admin-comments'),group=comments.getByRole('group',{name:'댓글 상태',exact:true})
  await group.getByRole('checkbox',{name:'숨김',exact:true}).uncheck();await commentReady(p);await group.getByRole('checkbox',{name:'삭제',exact:true}).uncheck();await commentReady(p)
  await comments.getByRole('button',{name:'다음',exact:true}).click();await commentReady(p)
  await comments.getByRole('button',{name:'댓글 숨김 처리',exact:true}).click();await p.getByLabel('처리 사유 (필수)').selectOption('PERSONAL_INFORMATION');await btn(p,'숨김 처리 실행').click();await p.getByRole('dialog').waitFor({state:'detached'});await commentReady(p)
  await p.waitForFunction(()=>document.querySelectorAll('.admin-preview [data-admin-comment-id]').length===20)
  assert.equal(new URL('http://local'+commentReads(s).at(-1).search).searchParams.get('page'),'0');assert.equal(await comments.locator('[data-admin-comment-id="920"]').count(),0);assert.equal(await comments.getByRole('navigation').count(),0)
  await group.getByRole('checkbox',{name:'정상',exact:true}).uncheck();await group.getByRole('checkbox',{name:'숨김',exact:true}).check();await commentReady(p)
  await comments.getByRole('button',{name:'댓글 숨김 해제',exact:true}).click();await p.getByLabel('처리 사유 (필수)').selectOption('NO_VIOLATION');await btn(p,'숨김 해제 실행').click();await p.getByRole('dialog').waitFor({state:'detached'});await commentReady(p);await p.waitForFunction(()=>document.querySelectorAll('.admin-preview [data-admin-comment-id]').length===0)
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.includes('/comments/')).length,2)
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,201,true);await preview(p);await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p);assert.equal(await p.locator('.admin-preview .admin-comments .admin-item-action').count(),0)
 }finally{await context.close()}
})
await run('deleted parent and deleted comment restrictions in tab',async()=>{
 const{p,context,s}=await setup()
 try{
  s.comments=[{...comment(900),status:'DELETED',deletedAt:stamp},comment(901)]
  await p.goto(base+'/admin/posts');await ready(p);await select(p,103);await preview(p);await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p)
  assert.equal(await p.locator('.admin-preview .admin-item-action button').count(),0);assert((await p.locator('.admin-preview .admin-comments').innerText()).includes('게시글이 삭제되어'));assert((await p.locator('.admin-preview .admin-comments').innerText()).includes('삭제된 댓글은 변경할 수 없습니다.'))
 }finally{await context.close()}
})
for(const mode of ['close','logout','other-login','navigate'])for(const status of [200,401])await run(`late comment ${status} after ${mode} is ignored`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  s.commentDetail=async r=>{g.arrive();await g.wait;return r.fulfill(status===200?{json:comment(900)}:{status,json:{code:'AUTHENTICATION_REQUIRED'}})}
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,203,true);await g.arrived
  if(mode==='close')await btn(p,'상세 닫기').click()
  else if(mode==='navigate'){await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await ready(p)}
  else{
   await btn(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
   if(mode==='other-login'){
    await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click();await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Mock-only-password1!');await btn(p,'로그인').click();await ready(p)
   }
  }
  await release(p,g);assert.equal(await p.locator('.admin-preview-comment').count(),0);assert(!(await p.locator('main').innerText()).includes('현재 댓글 900'))
  if(mode!=='logout')assert(await btn(p,'로그아웃').isVisible())
  if(mode==='other-login')assert.equal(s.member.memberId,99)
 }finally{g.release();await context.close()}
})
await run('comment tab pending old page and errors cannot replace changed filters; retry keeps same query',async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p)
  s.commentList=async(r,u,data)=>{if(u.searchParams.get('page')==='1'){g.arrive();await g.wait;return r.fulfill({json:data})}return r.fulfill({json:data})}
  const c=p.locator('.admin-preview .admin-comments');await c.getByRole('button',{name:'다음',exact:true}).click();await g.arrived
  await c.getByLabel('댓글 본문 검색',{exact:true}).fill('현재 댓글 901');await btn(p,'댓글 검색').click();await commentReady(p);await release(p,g)
  assert.equal(await c.locator('[data-admin-comment-id]').count(),1);assert.equal(await c.locator('[data-admin-comment-id]').getAttribute('data-admin-comment-id'),'901')
  s.commentList=r=>r.fulfill({status:503,json:{code:'SERVER_ERROR'}});await btn(p,'댓글 검색').click();await commentReady(p);assert(await c.getByRole('alert').isVisible());assert(await c.getByRole('button',{name:'댓글 숨김 처리',exact:true}).isDisabled())
  s.commentList=null;await btn(p,'댓글 다시 시도').click();await commentReady(p);assert.equal(new URL('http://local'+commentReads(s).at(-1).search).searchParams.get('keyword'),'현재 댓글 901');assert(await c.getByRole('button',{name:'댓글 숨김 처리',exact:true}).isEnabled())
 }finally{g.release();await context.close()}
})
await run('comment moderation rejected keeps state and reason; no automatic repeat',async()=>{
 const{p,context,s}=await setup()
 try{
  s.comments=[comment(900)];s.write=r=>r.fulfill({status:400,json:{code:'INVALID_REASON'}})
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);await p.getByRole('tab',{name:'댓글',exact:true}).click();await commentReady(p)
  await btn(p,'댓글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)').selectOption('OTHER');await p.getByLabel('상세 사유').fill('검증 사유');await btn(p,'숨김 처리 실행').click();await p.getByRole('dialog').getByRole('alert').waitFor()
  assert.equal(await p.getByLabel('상세 사유').inputValue(),'검증 사유');assert.equal(s.comments[0].status,'VISIBLE');assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.includes('/comments/')).length,1)
 }finally{await context.close()}
})
await run('long current comment one desktop scroller exposes tail and parent link',async()=>{
 const{p,context,s}=await setup()
 try{
  s.commentDetail=r=>r.fulfill({json:{...comment(900),content:('긴 댓글 본문\n').repeat(160)+'마지막 내용'}})
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,203,true);await singleReady(p)
  const body=p.locator('.admin-preview-body'),content=p.getByRole('region',{name:'현재 댓글 본문',exact:true})
  await content.evaluate(e=>e.scrollIntoView({block:'start'}));assert(await content.evaluate(e=>e.clientHeight>=e.scrollHeight));await p.screenshot({path:`${out}/comment-long-top.png`})
  await body.evaluate(e=>e.scrollTop=e.scrollHeight);await p.evaluate(()=>new Promise(r=>requestAnimationFrame(r)))
  const tail=await content.evaluate(e=>{const t=e.firstChild,r=document.createRange();r.setStart(t,t.textContent.length-6);r.setEnd(t,t.textContent.length);const b=r.getBoundingClientRect();return{top:b.top,bottom:b.bottom,height:innerHeight}});assert(tail.top>=0&&tail.bottom<=tail.height)
  const link=p.getByRole('link',{name:'부모 게시글 상세 관리로 이동',exact:true});await link.focus();assert(await link.evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight}));assert(await btn(p,'상세 닫기').evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight}));assert.equal(await p.locator('.admin-preview').evaluate(e=>e.scrollTop),0);await p.screenshot({path:`${out}/comment-long-bottom.png`})
 }finally{await context.close()}
})
await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));if(!results.length||results.some(x=>!x.passed))process.exitCode=1
