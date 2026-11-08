import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-admin-modal/browser'
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
  const u=new URL(r.request().url()),path=u.pathname,method=r.request().method();s.calls.push({path,search:u.search,method,payload:method==='POST'?r.request().postDataJSON():null})
  if(method==='OPTIONS')return r.fulfill({status:204,headers:{'access-control-allow-origin':base,'access-control-allow-credentials':'true','access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'content-type,x-csrf-token'}})
  if(path.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:{status:401,json:{code:'AUTHENTICATION_REQUIRED'}})
  if(path.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(path.endsWith('/login')){s.member={...member,memberId:99,nickname:'다른 관리자'};return r.fulfill({json:s.member})}
  if(path.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  const single=path.match(/^\/api\/v1\/admin\/comments\/(\d+)$/)
  if(single){const id=Number(single[1]);return s.commentDetail?s.commentDetail(r,id):r.fulfill({json:{...(s.comments.find(c=>c.commentId===id)??comment(id)),postId:101,postStatus:s.posts[0].status}})}
  const commentWrite=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/comments\/(\d+)\/(hide|restore)$/)
  if(commentWrite&&method==='POST'){
   if(s.write)return s.write(r,commentWrite)
   const c=s.comments.find(c=>c.commentId===Number(commentWrite[2]));c.status=commentWrite[3]==='hide'?'HIDDEN':'VISIBLE';c.hiddenAt=c.status==='HIDDEN'?stamp:null
   return r.fulfill({json:{postId:Number(commentWrite[1]),commentId:c.commentId,hidden:c.status==='HIDDEN',hiddenAt:c.hiddenAt,changed:true}})
  }
  const write=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/(hide|restore)$/)
  if(write&&method==='POST'){if(s.write)return s.write(r,write);const item=s.posts.find(x=>x.postId===Number(write[1]));item.status=write[2]==='hide'?'HIDDEN':'VISIBLE';item.hiddenAt=item.status==='HIDDEN'?stamp:null;return r.fulfill({json:{postId:item.postId,hidden:item.status==='HIDDEN',hiddenAt:item.hiddenAt,changed:true}})}
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

const commentReady=p=>p.locator('.admin-content-dialog .admin-comment-results[aria-busy="false"]').waitFor()
const singleReady=p=>p.locator('.admin-preview-comment[aria-busy="false"]').waitFor()
const select=async(p,id,logs=false)=>selectRow(p,logs?`기록 #${id} 상세`:`게시글 #${id} 미리보기`).locator('.admin-badge').click()
const commentReads=s=>s.calls.filter(c=>/^\/api\/v1\/admin\/posts\/\d+\/comments$/.test(c.path))
const singleReads=s=>s.calls.filter(c=>/^\/api\/v1\/admin\/comments\/\d+$/.test(c.path))
const release=async(p,g)=>{const n=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,n);await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))}

const dialog=p=>p.getByRole('dialog'),heading=p=>p.locator('#admin-content-dialog-title')
const openPost=async(p,id=101)=>{await p.goto(base+'/admin/posts');await ready(p);await select(p,id);await preview(p)}
const openLog=async(p,id=203)=>{await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,id,true);await singleReady(p)}
const noOverflow=async p=>assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&Array.from(document.querySelectorAll('.admin-content-dialog,.admin-dialog-body')).every(e=>e.scrollWidth<=e.clientWidth+1)))
const close=async p=>{await btn(p,'닫기').click();await dialog(p).waitFor({state:'detached'})}
for(const width of [1440,320])await run(`post modal full list, lazy comments, focus and scroll ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.posts=Array.from({length:20},(_,i)=>post(101+i));s.posts[19].content='긴 본문\n'.repeat(300)+'본문 마지막';s.comments[0].content='댓글 마지막 확인'
  await p.goto(base+'/admin/posts');await ready(p);assert.equal(await p.locator('.admin-preview-empty').count(),0)
  await p.locator('#post-select-120').scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY)
  await p.screenshot({path:`${out}/list-${width}.png`});await select(p,120);await preview(p)
  assert((await heading(p).innerText()).includes('#120'));assert.equal(await dialog(p).count(),1)
  assert.equal(await p.evaluate(()=>getComputedStyle(document.body).position),'fixed')
  assert.equal(commentReads(s).length,0,'long body must not eagerly fetch comments')
  assert.equal(await p.getByRole('tab').count(),0);await noOverflow(p)
  await p.screenshot({path:`${out}/post-top-${width}.png`})
  await btn(p,'댓글 관리로 이동').click();await commentReady(p);assert.equal(commentReads(s).length,1)
  assert.equal(await p.evaluate(()=>document.activeElement.className),'admin-dialog-comments')
  await p.waitForFunction(()=>{const top=document.querySelector('.admin-dialog-comments').getBoundingClientRect().top,header=document.querySelector('.admin-dialog-heading').getBoundingClientRect().bottom;return top>=header-1&&top<header+60})
  const headerBox=await p.locator('.admin-dialog-heading').boundingBox();assert(headerBox.y>=0&&headerBox.y+headerBox.height<1000,'fixed heading stays inside viewport after comment jump');assert.equal(await dialog(p).evaluate(e=>e.scrollTop),0,'dialog itself must not scroll')
  await p.screenshot({path:`${out}/post-comments-${width}.png`})
  assert((await p.locator('.admin-full-content').allTextContents()).join('').includes('본문 마지막'))
  await close(p);assert.equal(await p.evaluate(()=>document.activeElement.id),'post-select-120');assert(Math.abs(await p.evaluate(()=>scrollY)-y)<2)
 }finally{await context.close()}
})
await run('history back forward, direct URL and refresh preserve filters and modal',async()=>{
 const{p,context}=await setup()
 try{
  await p.goto(base+'/admin/posts?statuses=HIDDEN&page=1&sort=OLDEST&startDate=2026-10-01');await ready(p)
  const list=p.url();await select(p,102);await preview(p);const selected=p.url();assert(selected.includes('selected=102'))
  await p.goBack();await ready(p);assert.equal(p.url(),list);assert.equal(await dialog(p).count(),0)
  await p.goForward();await preview(p);assert.equal(p.url(),selected)
  await p.reload();await preview(p);assert.equal(p.url(),selected);await close(p);assert.equal(new URL(p.url()).searchParams.get('page'),'1')
  assert.equal(new URL(p.url()).searchParams.get('sort'),'OLDEST')
 }finally{await context.close()}
})
for(const width of [1440,320])await run(`log comment management parent and internal history ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  await openLog(p);assert.equal(commentReads(s).length,0);assert.equal(singleReads(s).length,1)
  assert.equal(await p.getByText('조회한 현재 내용이며 조치 당시와 다를 수 있습니다.',{exact:true}).count(),1)
  assert.equal(await dialog(p).getByRole('button',{name:/숨김 처리/}).count(),0)
  await p.screenshot({path:`${out}/log-${width}.png`});await btn(p,'이 댓글 관리').click();await p.getByRole('heading',{name:'댓글 #900 관리',exact:true}).waitFor();await singleReady(p)
  assert.equal(singleReads(s).length,2);assert((await heading(p).innerText()).includes('#900'));await noOverflow(p)
  await p.screenshot({path:`${out}/comment-manage-${width}.png`})
  await btn(p,'소속 게시글 보기').click();await preview(p);assert((await heading(p).innerText()).includes('게시글 #101 관리'));assert((await p.locator('.admin-preview-title').innerText()).includes('101'))
  assert(!s.calls.some(c=>c.path==='/api/v1/admin/posts/900'))
  await p.goBack();await singleReady(p);assert((await heading(p).innerText()).includes('#900'))
  await btn(p,'운영 기록으로 돌아가기').click();await singleReady(p);assert((await heading(p).innerText()).includes('#203'))
  assert((await p.locator('.admin-log-fields').innerText()).includes('저장 원문 203'))
  await close(p);assert.equal(await dialog(p).count(),0)
 }finally{await context.close()}
})
await run('post action inline, dirty close/back, preserve input and filtered removal',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/posts?statuses=VISIBLE');await ready(p);await select(p,101);await preview(p)
  await btn(p,'게시글 숨김 처리').click();assert.equal(await dialog(p).count(),1)
  await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.getByLabel('상세 사유 (필수)',{exact:true}).fill('  보관할 사유  ')
  const url=p.url();await btn(p,'닫기').click();await p.getByRole('alert',{name:'입력 유실 확인'}).waitFor();await btn(p,'계속 작성').click()
  assert.equal(await p.getByLabel('상세 사유 (필수)',{exact:true}).inputValue(),'  보관할 사유  ');assert.equal(p.url(),url)
  await p.evaluate(()=>history.back());await p.getByRole('alert',{name:'입력 유실 확인'}).waitFor();await btn(p,'계속 작성').click();assert.equal(p.url(),url)
  await p.screenshot({path:`${out}/post-reason.png`});await btn(p,'숨김 처리 실행').click();await preview(p)
  await p.waitForFunction(()=>document.querySelector('.admin-preview-post .admin-badge')?.textContent==='숨김')
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
  assert.equal(await dialog(p).count(),1,'open detail stays when row no longer matches filter')
  assert.equal(await p.locator('#post-select-101').count(),0)
  await close(p);assert.equal(await p.evaluate(()=>document.activeElement.hasAttribute('data-admin-list-heading')),true)
 }finally{await context.close()}
})
await run('comment action uses parent ID, general reason, changed false and no automatic retry',async()=>{
 const{p,context,s}=await setup()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await p.getByRole('heading',{name:'댓글 #900 관리',exact:true}).waitFor();await singleReady(p)
  await btn(p,'댓글 #900 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('PERSONAL_INFORMATION')
  s.write=r=>r.fulfill({json:{postId:101,commentId:900,hidden:true,hiddenAt:stamp,changed:false}})
  await p.screenshot({path:`${out}/comment-reason.png`});await btn(p,'숨김 처리 실행').click();await p.getByText('이미 요청한 상태입니다. 추가 상태 변경과 운영 기록은 발생하지 않았습니다.',{exact:true}).waitFor()
  const writes=s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide'));assert.equal(writes.length,1);assert.equal(writes[0].path,'/api/v1/admin/posts/101/comments/900/hide');assert.deepEqual(writes[0].payload,{reasonType:'PERSONAL_INFORMATION'})
 }finally{await context.close()}
})
for(const status of [404,503])await run(`log current content ${status} retains record and retry`,async()=>{
 const{p,context,s}=await setup()
 try{
  s.commentDetail=r=>r.fulfill({status,json:{code:status===404?'COMMENT_NOT_FOUND':'SERVER_ERROR'}})
  await openLog(p);assert((await heading(p).innerText()).includes('#203'));assert((await p.locator('.admin-log-fields').innerText()).includes('저장 원문 203'));assert(await p.locator('.admin-preview-comment [role=alert]').isVisible())
  s.commentDetail=null;await btn(p,'댓글 정보 다시 시도').click();await singleReady(p);assert((await p.locator('.admin-preview-comment').innerText()).includes('현재 댓글 900'))
 }finally{await context.close()}
})
for(const status of [401,403])await run(`modal ${status} clears admin data`,async()=>{
 const{p,context,s}=await setup()
 try{
  s.detail=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ADMIN_REQUIRED'}})
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101)
  await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다'}).waitFor()
  assert.equal(await dialog(p).count(),0);assert.equal(await p.locator('#post-select-101').count(),0)
 }finally{await context.close()}
})
for(const status of [200,401,503])await run(`closed modal late response ${status} cannot restore or expire new selection`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  s.detail=async(r,id)=>{if(id===101){g.arrive();await g.wait;return r.fulfill(status===200?{json:post(101)}:{status,json:{code:'AUTHENTICATION_REQUIRED'}})}return r.fulfill({json:post(id)})}
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await g.arrived;await close(p)
  await select(p,102);await preview(p);await release(p,g);assert((await heading(p).innerText()).includes('#102'));assert((await p.locator('.admin-preview-title').innerText()).includes('102'))
 }finally{g.release();await context.close()}
})
await run('lazy comments failure preserves post, filter pending keeps content and scroll',async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await openPost(p);await btn(p,'댓글 관리로 이동').click();await commentReady(p)
  s.commentList=async(r,u,data)=>{g.arrive();await g.wait;return r.fulfill({json:data})}
  const before=await p.locator('.admin-dialog-body').evaluate(e=>({y:e.scrollTop,height:e.scrollHeight}));
  await p.getByRole('group',{name:'댓글 상태'}).getByLabel('정상',{exact:true}).uncheck();await g.arrived
  assert.equal(await p.locator('[data-admin-comment-id]').count(),20)
  const pending=await p.locator('.admin-dialog-body').evaluate(e=>({y:e.scrollTop,height:e.scrollHeight}));assert(Math.abs(before.height-pending.height)<2)
  assert.equal(await p.locator('.admin-comment-list button:enabled').count(),0)
  await release(p,g);await commentReady(p);assert((await p.locator('.admin-preview-title').innerText()).includes('101'))
  s.commentList=r=>r.fulfill({status:503,json:{code:'SERVER_ERROR'}});await p.getByRole('group',{name:'댓글 상태'}).getByLabel('정상',{exact:true}).check();await p.getByText('갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.',{exact:true}).waitFor()
  assert((await p.locator('.admin-preview-title').innerText()).includes('101'))
 }finally{g.release();await context.close()}
})
for(const width of [1440,320])await run(`keyboard modal, backdrop, native link and empty reason close ${width}`,async()=>{
 const{p,context}=await setup(width)
 try{
  await openPost(p);await noOverflow(p)
  const box=await dialog(p).boundingBox();assert(box.x>=7&&box.y>=7);assert(box.x+box.width<=width-7)
  await btn(p,'닫기').focus();await p.keyboard.press('Tab');assert(await p.evaluate(()=>document.activeElement.closest('dialog')!==null))
  await p.mouse.click(1,1);assert.equal(await dialog(p).count(),1)
  await btn(p,'게시글 숨김 처리').click();assert(await p.locator('.admin-action-content h3').evaluate(e=>e===document.activeElement));await p.keyboard.press('Tab');assert(await p.getByLabel('처리 사유 (필수)',{exact:true}).evaluate(e=>e===document.activeElement))
  await p.keyboard.press('Escape');await dialog(p).waitFor({state:'detached'});await select(p,101);await preview(p)
  assert(await btn(p,'게시글 숨김 처리').isEnabled());await close(p)
  const link=p.locator('#post-select-101 a'),href=await link.getAttribute('href')
  const opened=context.waitForEvent('page');await link.click({button:'middle'});const tab=await opened;await tab.waitForURL(base+href);await tab.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor();await tab.close()
  await p.locator('#post-select-101').focus();await p.keyboard.press('Enter');await preview(p);assert.equal(await dialog(p).count(),1)
 }finally{await context.close()}
})
for(const [status,code] of [[400,'INVALID_REASON'],[403,'CSRF_TOKEN_INVALID'],[500,'INTERNAL_SERVER_ERROR'],[200,'MALFORMED']])await run(`inline failure ${status}/${code} preserves reason and never repeats write`,async()=>{
 const{p,context,s}=await setup()
 try{
  await openPost(p);await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.getByLabel('상세 사유 (필수)',{exact:true}).fill('보존한 입력')
  s.write=r=>r.fulfill({status,json:{code}});await btn(p,'숨김 처리 실행').click();await p.locator('.admin-inline-moderation [role=alert]').waitFor()
  assert.equal(await p.getByLabel('상세 사유 (필수)',{exact:true}).inputValue(),'보존한 입력');assert.equal(await dialog(p).count(),1)
  const n=s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length;assert.equal(n,1)
  if(status>=500||code==='MALFORMED'){await btn(p,'최신 상태 확인').click();await preview(p);assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)}
 }finally{await context.close()}
})
await run('pending inline action blocks repeat, Escape and internal switch; original log survives new list',async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await p.getByRole('heading',{name:'댓글 #900 관리',exact:true}).waitFor();await singleReady(p)
  await btn(p,'댓글 #900 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING')
  s.write=async r=>{g.arrive();await g.wait;s.comments[0].status='HIDDEN';s.comments[0].hiddenAt=stamp;s.logs=[log(800)];return r.fulfill({json:{postId:101,commentId:900,hidden:true,hiddenAt:stamp,changed:true}})}
  await btn(p,'숨김 처리 실행').click();await g.arrived;await p.keyboard.press('Enter');await p.keyboard.press('Escape');assert.equal(await dialog(p).count(),1)
  await btn(p,'운영 기록으로 돌아가기').click();assert((await heading(p).innerText()).includes('#900'));assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
  await release(p,g);await btn(p,'댓글 #900 숨김 해제').waitFor();await btn(p,'운영 기록으로 돌아가기').click();await p.getByRole('heading',{name:'운영 기록 · #203',exact:true}).waitFor();await singleReady(p)
  assert((await p.locator('.admin-log-fields').innerText()).includes('저장 원문 203'));assert(!(await p.locator('.admin-log-fields').innerText()).includes('저장 원문 800'))
 }finally{g.release();await context.close()}
})
await run('short post loads visible comments once and paging uses current server conditions',async()=>{
 const{p,context,s}=await setup()
 try{
  s.posts[0].content='짧은 본문';await openPost(p);await commentReady(p);assert.equal(commentReads(s).length,1)
  await btn(p,'댓글 관리로 이동').click();await commentReady(p);assert.equal(commentReads(s).length,1)
  await p.getByRole('navigation',{name:'댓글 페이지'}).getByRole('button',{name:'다음',exact:true}).click();await p.waitForFunction(()=>document.querySelectorAll('[data-admin-comment-id]').length===1);await commentReady(p)
  assert.equal(new URLSearchParams(commentReads(s).at(-1).search).get('page'),'1');assert.equal(await p.locator('[data-admin-comment-id]').count(),1)
 }finally{await context.close()}
})
for(const kind of ['POST','COMMENT'])await run(`deleted ${kind} management has no mutation controls`,async()=>{
 const{p,context,s}=await setup()
 try{
  if(kind==='POST'){await openPost(p,103)}else{s.comments[0].status='DELETED';s.comments[0].deletedAt=stamp;await openLog(p);await btn(p,'이 댓글 관리').click();await p.getByRole('heading',{name:'댓글 #900 관리',exact:true}).waitFor();await singleReady(p)}
  assert.equal(await dialog(p).getByRole('button',{name:/^(게시글|댓글) 숨김 (처리|해제)$/}).count(),0)
  assert.equal(await dialog(p).getByRole('link',{name:'원문 보기'}).count(),0)
 }finally{await context.close()}
})
for(const status of [200,401])await run(`modal closed then real other-login ignores late ${status}`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  s.detail=async(r,id)=>{if(id===101){g.arrive();await g.wait;return r.fulfill(status===200?{json:post(id)}:{status,json:{code:'AUTHENTICATION_REQUIRED'}})}return r.fulfill({json:post(id)})}
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await g.arrived;await close(p)
  await btn(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
  await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click();await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Synthetic-only1!');await btn(p,'로그인').click();await ready(p)
  await select(p,102);await preview(p);await release(p,g);assert((await heading(p).innerText()).includes('#102'));assert((await p.locator('.admin-preview-title').innerText()).includes('102'));assert((await p.locator('.iyum-header').innerText()).includes('다른 관리자'))
 }finally{g.release();await context.close()}
})
await run('log same post different logIds resets scroll and stored reason remains exact',async()=>{
 const{p,context,s}=await setup()
 try{
  s.logs[0].reason='보존할 원문\n'+'긴 사유 '.repeat(600);s.logs[0].reasonDetail=null
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,201,true);await preview(p)
  assert.equal(await p.locator('.admin-log-fields .admin-reason-text').textContent(),s.logs[0].reason)
  await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop=e.scrollHeight);assert(await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop>0))
  await close(p);await select(p,202,true);await preview(p);assert((await heading(p).innerText()).includes('#202'));assert.equal(await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop),0)
 }finally{await context.close()}
})

for(const stage of ['csrf','write'])await run(`live modal ${stage} late completion after concurrent expiry and new login`,async()=>{
 const{p,context,s}=await setup(),g=gate(),cg=gate()
 try{
  s.posts[0].content='긴 글\n'.repeat(300)
  s.commentList=async r=>{cg.arrive();await cg.wait;return r.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED'}})}
  await openPost(p);await btn(p,'댓글 관리로 이동').click();await cg.arrived
  if(stage==='csrf'){let held=false;await context.route('**/api/v1/auth/csrf',async r=>{if(!held){held=true;g.arrive();await g.wait}return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})})}
  else s.write=async r=>{g.arrive();await g.wait;return r.fulfill({json:{postId:101,hidden:true,hiddenAt:stamp,changed:true}})}
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await g.arrived
  cg.release();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor();assert.equal(await dialog(p).count(),0)
  await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click();await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Synthetic-only1!');await btn(p,'로그인').click();await ready(p)
  const response=p.waitForEvent('requestfinished',{predicate:r=>stage==='csrf'?r.url().endsWith('/csrf'):r.url().endsWith('/hide')});g.release();await response;await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,stage==='csrf'?0:1);assert.equal(await dialog(p).count(),0);assert.equal(await p.locator('.admin-inline-feedback,.comment-success-toast').count(),0);assert((await p.locator('.iyum-header').innerText()).includes('다른 관리자'))
 }finally{g.release();cg.release();await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
