import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-current-target-front/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`보청기 적응 경험을 나눕니다 ${id}`,content:`새 보청기를 사용한 지 한 달이 되었습니다. 🦻\n조용한 곳에서는 대화가 한결 편해졌지만, 사람이 많은 장소에서는 아직 적응이 필요합니다.\n\n`+'착용 시간을 조금씩 늘리며 느낀 점을 기록합니다. 여러분의 경험도 알려 주세요.\n'.repeat(32)+'마지막 게시글 문장입니다.',contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:88,currentTarget:{author,title:targetType==='POST'?'현재 제목':null,contentPreview:'목록 미리보기 🦻'},targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
const comment=(id,postId=101)=>({commentId:id,postId,postStatus:'HIDDEN',content:`저도 비슷한 경험이 있어요. 🦻\n처음에는 조용한 곳에서 대화를 연습했습니다.\n\n`+'꾸준히 착용하면서 조금씩 적응하고 있습니다.\n'.repeat(32)+'공백없는긴문자열'.repeat(50)+'\n끝까지 읽어 주셔서 감사합니다.',author:{...author,nickname:'탈퇴한 회원',withdrawn:true},status:'VISIBLE',createdAt:stamp,updatedAt:stamp,contentUpdatedAt:null,hiddenAt:null,deletedAt:null})
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor({state:'attached'})
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),wait:new Promise(r=>release=r),arrive:()=>arrive(),release:()=>release()}}
async function setup(width=1440){
 const context=await browser.newContext({viewport:{width,height:1000}}),p=await context.newPage(),s={member:{...member},calls:[],errors:[],posts:[post(101),post(102,'HIDDEN'),post(103,'DELETED')],logs:[log(201),log(202),log(203,'COMMENT')],detail:null,list:null,commentDetail:null,commentList:null,write:null,comments:Array.from({length:21},(_,i)=>comment(900+i))}
 p.setDefaultTimeout(8000);p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const f=window.fetch;window.__masterParsed=0;window.fetch=async(u,o)=>{const r=await f(u,String(u).includes('/api/v1/admin/')?{...o,signal:undefined}:o);if(String(u).includes('/api/v1/admin/')&&r.status!==200)setTimeout(()=>window.__masterParsed++,0);if(String(u).includes('/api/v1/admin/')){const json=r.json.bind(r);r.json=async()=>{const v=await json();setTimeout(()=>window.__masterParsed++,0);return v}}return r}})
 await context.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url()),path=u.pathname,method=r.request().method();s.calls.push({path,search:u.search,method,payload:method==='POST'?r.request().postDataJSON():null})
  if(method==='OPTIONS')return r.fulfill({status:204,headers:{'access-control-allow-origin':base,'access-control-allow-credentials':'true','access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'content-type,x-csrf-token'}})
  if(path==='/api/v1/admin/operation-logs/summary')return r.fulfill({json:{queriedAt:'2026-11-17T15:00:00Z',baseDate:'2026-11-18',counts:{totalOperationLogCount:3,todayOperationLogCount:3,todayHideCount:2,todayRestoreCount:1}}})
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


const select=async(p,id)=>{await p.locator(`[data-log-id="${id}"]`).click();await p.locator('#admin-log-panel-title').filter({hasText:`운영 기록 #${id}`}).waitFor()}
const detailReady=async p=>{await p.locator('.admin-log-panel [aria-busy="false"]').first().waitFor()}
const reason=p=>p.locator('.admin-inline-moderation select')
const countWrites=s=>s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length
const noOverflow=async p=>assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&Array.from(document.querySelectorAll('.admin-log-panel,.admin-log-panel-body')).every(e=>e.scrollWidth<=e.clientWidth+1)))
for(const width of [1920,1440,1100,320])await run(`currentTarget list + direct comment action ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.logs[1].currentTarget=null;s.logs[2].currentTarget.author={...author,nickname:'탈퇴한 회원',withdrawn:true}
  await p.goto(base+'/admin/operation-logs');await ready(p)
  assert.equal(await p.locator('tbody tr').count(),3);assert(await p.getByText('현재 내용을 조회할 수 없습니다',{exact:true}).isVisible())
  assert.equal(s.calls.filter(c=>/admin\/(posts|comments)\/\d+$/.test(c.path)).length,0)
  await select(p,203);await detailReady(p);assert.equal(await p.getByRole('dialog').count(),0)
  assert(await p.locator('.admin-log-panel').getByText('회원 #88',{exact:true}).isVisible())
  assert(s.calls.some(c=>c.path==='/api/v1/admin/comments/900'));assert(!s.calls.some(c=>c.path==='/api/v1/admin/posts/900'))
  await p.screenshot({path:`${out}/comment-panel-${width}.png`})
  await btn(p,'댓글 #900 숨김 처리').click();assert.equal(await p.locator('.admin-action-content .admin-full-content').textContent(),s.comments[0].content)
  await btn(p,'숨김 처리 실행').click();assert.equal(countWrites(s),0)
  assert(await reason(p).evaluate(e=>e===document.activeElement));assert.equal(await reason(p).getAttribute('aria-invalid'),'true')
  assert(await reason(p).evaluate(e=>{const r=e.getBoundingClientRect(),style=getComputedStyle(e);return r.width>180&&r.top>=0&&r.bottom<=innerHeight&&style.borderTopColor==='rgb(180, 35, 53)'}))
  assert(await p.locator('.admin-action-workspace').evaluate(e=>{const a=e.querySelector('.admin-action-content').getBoundingClientRect(),b=e.querySelector('.admin-action-form').getBoundingClientRect();return b.top>=a.bottom&&Math.abs(a.width-b.width)<2}))
  await noOverflow(p);await p.screenshot({path:`${out}/reason-error-${width}.png`})
  await reason(p).selectOption('OTHER');await btn(p,'숨김 처리 실행').click();assert.equal(countWrites(s),0);assert(await p.locator('.admin-inline-moderation textarea').evaluate(e=>e===document.activeElement))
  await p.locator('.admin-inline-moderation textarea').fill('운영 정책 확인');if(await btn(p,'목록으로 돌아가기').isVisible())await btn(p,'목록으로 돌아가기').click();await p.locator('[data-log-id="201"]').click();await p.getByRole('dialog').waitFor();await btn(p,'계속 작성').click();if(await btn(p,'선택한 상세 보기').isVisible())await btn(p,'선택한 상세 보기').click();assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'운영 정책 확인')
  await p.screenshot({path:`${out}/action-${width}.png`});await btn(p,'숨김 처리 실행').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});await detailReady(p);await ready(p)
  assert.equal(countWrites(s),1);assert(await btn(p,'댓글 #900 숨김 해제').isEnabled());assert((await p.locator('.admin-log-fields').textContent()).includes('저장 원문 203'))
  assert.equal(new URL(p.url()).searchParams.get('selected'),'203');await p.screenshot({path:`${out}/completed-${width}.png`})
  await btn(p,'상세 닫기').click();await p.locator('#admin-log-panel-title').waitFor({state:'detached'});assert(await p.locator('[data-log-id="203"]').evaluate(e=>e===document.activeElement))
  assert.equal(s.errors.length,0)
 }finally{await context.close()}
})
await run('post direct action, restore, applied filters and selected record removed',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/operation-logs?targetType=POST&sort=OLDEST&page=1');await ready(p);await select(p,201);await detailReady(p)
  assert.equal(s.calls.filter(c=>/\/posts\/101\/comments$/.test(c.path)).length,0)
  await p.screenshot({path:`${out}/post-panel.png`});await btn(p,'게시글 숨김 처리').click();await reason(p).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});await detailReady(p);await ready(p)
  assert(await btn(p,'게시글 숨김 해제').isEnabled());assert((await p.locator('.admin-log-fields').textContent()).includes('저장 원문 201'))
  const q=new URL('http://mock'+s.calls.filter(c=>c.path==='/api/v1/admin/operation-logs').at(-1).search);assert.equal(q.searchParams.get('sort'),'OLDEST');assert.equal(q.searchParams.get('page'),'1')
  await btn(p,'게시글 숨김 해제').click();await reason(p).selectOption('NO_VIOLATION');s.logs=s.logs.filter(x=>x.logId!==201);await btn(p,'숨김 해제 실행').click();await p.locator('#admin-log-panel-title').waitFor({state:'detached'});await p.getByText('현재 조회 결과에서 선택한 운영 기록을 찾을 수 없습니다.',{exact:false}).waitFor();assert.equal(countWrites(s),2)
 }finally{await context.close()}
})
await run('server reason error keeps form; dirty close cancel; filter clears selection',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,201);await detailReady(p)
  await btn(p,'게시글 숨김 처리').click();await reason(p).selectOption('SPAM_ADVERTISING')
  assert.equal(await p.getByText('페이지 조회에 실패했습니다. 다시 시도해 주세요.',{exact:true}).count(),0)
  s.write=r=>r.fulfill({status:400,json:{code:'INVALID_REASON'}})
  await btn(p,'숨김 처리 실행').click();await p.getByText('선택한 처리 사유를 확인해 주세요.',{exact:true}).waitFor();assert(await reason(p).evaluate(e=>e===document.activeElement));assert.equal(await reason(p).inputValue(),'SPAM_ADVERTISING');assert.equal(countWrites(s),1)
  await btn(p,'상세 닫기').click();await btn(p,'계속 작성').click();assert.equal(await reason(p).inputValue(),'SPAM_ADVERTISING')
  await btn(p,'취소').click();await btn(p,'입력 버리고 이동').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'})
  await p.getByLabel('처리 시각 정렬',{exact:true}).selectOption('OLDEST');await ready(p);await p.locator('#admin-log-panel-title').waitFor({state:'detached'});assert.equal(new URL(p.url()).searchParams.has('selected'),false);assert.equal(countWrites(s),1)
 }finally{await context.close()}
})
await run('late detail ignores cancellation and cannot overwrite next log; history restores',async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  s.detail=async(r,id)=>{g.arrive();await g.wait;return r.fulfill({json:post(id)})}
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,201);await g.arrived;await select(p,203);await detailReady(p)
  const before=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,before)
  assert.equal(await p.locator('.admin-log-panel .admin-preview-post').count(),0);assert(await p.locator('#admin-log-panel-title').filter({hasText:'댓글 #900'}).isVisible())
  s.detail=null;await p.goBack();await ready(p);await detailReady(p);assert.equal(new URL(p.url()).searchParams.get('selected'),'201');await p.goForward();await ready(p);await detailReady(p);assert.equal(new URL(p.url()).searchParams.get('selected'),'203')
 }finally{g.release();await context.close()}
})
await run('detail failure preserves record; post-action failure locks until GET retry',async()=>{
 const{p,context,s}=await setup()
 try{
  s.commentDetail=r=>r.fulfill({status:404,json:{code:'COMMENT_NOT_FOUND'}})
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,203);await detailReady(p);assert(await p.getByText('현재 댓글 정보를 찾을 수 없습니다.',{exact:true}).isVisible());assert(await p.locator('.admin-log-fields').isVisible())
  s.commentDetail=null;await btn(p,'댓글 정보 다시 시도').click();await detailReady(p);await btn(p,'댓글 #900 숨김 처리').click();await reason(p).selectOption('SPAM_ADVERTISING')
  s.commentDetail=r=>r.fulfill({status:503,json:{code:'SERVER_ERROR'}});await btn(p,'숨김 처리 실행').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});await detailReady(p);assert(await btn(p,'댓글 #900 숨김 처리').isDisabled());assert.equal(countWrites(s),1)
  s.commentDetail=null;await btn(p,'댓글 정보 다시 시도').click();await detailReady(p);assert(await btn(p,'댓글 #900 숨김 해제').isEnabled());assert.equal(countWrites(s),1)
 }finally{await context.close()}
})
for(const status of [401,403])await run(`detail ${status} clears list and panel`,async()=>{
 const{p,context,s}=await setup()
 try{
  s.detail=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ADMIN_REQUIRED'}})
  await p.goto(base+'/admin/operation-logs');await ready(p);await p.locator('[data-log-id="201"]').click();await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('tbody tr,.admin-log-panel').count(),0)
 }finally{await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
