import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-modal-followup/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`선택 게시글 ${id}`,content:`현재 본문 ${id}\n`+'긴 본문 검증 '.repeat(40),contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:8,currentTarget:null,targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
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

const dialog=p=>p.getByRole('dialog')
const openPost=async(p,id=101)=>{await p.goto(base+'/admin/posts');await ready(p);await select(p,id);await preview(p)}
const openLog=async(p,id=203)=>{await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,id,true);await singleReady(p)}
const noOverflow=async p=>assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&Array.from(document.querySelectorAll('.admin-content-dialog,.admin-dialog-body')).every(e=>e.scrollWidth<=e.clientWidth+1)))
const close=async p=>{await btn(p,'닫기').click();await dialog(p).waitFor({state:'detached'})}
await run('followup uncertain close keeps recovery available without automatic POST',async()=>{
 const{p,context,s}=await setup()
 try{
  await openPost(p);s.write=r=>r.fulfill({status:500,json:{code:'INTERNAL_SERVER_ERROR'}})
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await btn(p,'최신 상태 확인').waitFor()
  await btn(p,'닫기').click();await btn(p,'계속 작성').click();assert.equal(await dialog(p).count(),1)
  await btn(p,'닫기').click();await btn(p,'버리고 이동').click();await dialog(p).waitFor({state:'detached'});await ready(p)
  await btn(p,'최신 상태 확인').waitFor();assert((await p.locator('.admin-recovery').innerText()).includes('#101'))
  await select(p,102);await preview(p);assert.equal(await btn(p,'게시글 숨김 해제').isDisabled(),true);await close(p)
  await btn(p,'최신 상태 확인').click();await p.getByText('최신 상태를 확인했습니다.',{exact:false}).waitFor();await ready(p)
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})
await run('followup parent management retains comment conditions after changed=false',async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await singleReady(p);await btn(p,'소속 게시글 보기').click();await preview(p);await btn(p,'댓글 관리로 이동').click();await commentReady(p)
  await p.getByLabel('댓글 본문 검색',{exact:true}).fill('현재');await btn(p,'댓글 검색').click();await commentReady(p)
  await p.locator('.admin-comment-results').waitFor();await p.getByRole('navigation',{name:'댓글 페이지'}).getByRole('button',{name:'다음',exact:true}).click();await commentReady(p)
  s.commentDetail=async r=>{g.arrive();await g.wait;return r.fulfill({json:comment(900)})}
  s.write=r=>r.fulfill({json:{postId:101,hidden:true,hiddenAt:stamp,changed:false}})
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await g.arrived
  assert.equal(await p.getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'현재');g.release();await preview(p);await commentReady(p)
  assert.equal(await p.getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'현재');assert.equal(new URLSearchParams(commentReads(s).at(-1).search).get('page'),'1')
 }finally{g.release();await context.close()}
})
await run('followup explicit close exits entire internal history before Back',async()=>{
 const{p,context}=await setup()
 try{
  await p.goto(base+'/admin/posts');await ready(p);await p.getByRole('link',{name:'운영 기록',exact:true}).click();await ready(p)
  await select(p,203,true);await singleReady(p);await btn(p,'이 댓글 관리').click();await singleReady(p);await btn(p,'소속 게시글 보기').click();await preview(p)
  await close(p);await ready(p);await p.goBack();await ready(p);assert.equal(await dialog(p).count(),0);assert.equal(new URL(p.url()).pathname,'/admin/posts')
 }finally{await context.close()}
})
async function uncertain(p,s,status=500){
 await openPost(p);s.write=r=>status==='network'?r.abort('failed'):r.fulfill({status:status===200?200:status,json:status===200?{broken:true}:{code:status===404?'POST_NOT_FOUND':status===409?'POST_DELETED':'INTERNAL_SERVER_ERROR'}})
 await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.getByLabel('상세 사유 (필수)',{exact:true}).fill('확인할 사유');await btn(p,'숨김 처리 실행').click();await btn(p,'최신 상태 확인').waitFor()
}
for(const status of [404,409,200,'network'])await run(`followup recovery ${status} cancel reason, failed GET, retry, never repeats POST`,async()=>{
 const{p,context,s}=await setup()
 try{
  await uncertain(p,s,status);await btn(p,'취소').click();await btn(p,'계속 작성').click();assert.equal(await p.getByLabel('상세 사유 (필수)',{exact:true}).inputValue(),'확인할 사유')
  await btn(p,'취소').click();await btn(p,'버리고 이동').click();await btn(p,'최신 상태 확인').waitFor()
  s.detail=r=>r.fulfill({status:503,json:{code:'INTERNAL_SERVER_ERROR'}});await btn(p,'최신 상태 확인').click();await p.getByText('최신 상태를 확인하지 못했습니다. 다시 시도해 주세요.',{exact:true}).waitFor();assert.equal(await btn(p,'게시글 숨김 처리').isDisabled(),true)
  s.detail=null;await btn(p,'최신 상태 확인').click();await preview(p);await p.getByText('최신 상태를 확인했습니다.',{exact:false}).waitFor();assert.equal(await btn(p,'게시글 숨김 처리').isEnabled(),true)
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})
await run('followup uncertain Back confirm preserves recovery outside modal',async()=>{
 const{p,context,s}=await setup()
 try{
  await uncertain(p,s);const url=p.url();await p.evaluate(()=>history.back());await btn(p,'계속 작성').waitFor();await p.waitForFunction(url=>location.href===url,url);await btn(p,'계속 작성').click();assert.equal(p.url(),url)
  await p.evaluate(()=>history.back());await btn(p,'버리고 이동').waitFor();await p.waitForFunction(url=>location.href===url,url);await btn(p,'버리고 이동').click();await dialog(p).waitFor({state:'detached'});await ready(p);await btn(p,'최신 상태 확인').waitFor()
  assert((await p.locator('.admin-recovery').innerText()).includes('#101'));assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})
for(const width of [1440,320])await run(`followup compact comments and historical/current summary ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.posts[0].content='본문과 이모지 🦻\n'.repeat(80);await openPost(p);await btn(p,'댓글 관리로 이동').click();await commentReady(p)
  assert.equal(await dialog(p).getByRole('heading',{name:'댓글',exact:true}).count(),1);assert.equal(await dialog(p).getByRole('heading',{name:'댓글 관리',exact:true}).count(),0)
  const item=p.locator('[data-admin-comment-id="900"]');assert.equal(await item.getByText('기록 없음',{exact:true}).isVisible(),false)
  await item.locator('summary').focus();await p.keyboard.press('Enter');assert.equal(await item.getByText('기록 없음',{exact:true}).isVisible(),true)
  await noOverflow(p);await p.screenshot({path:`${out}/comments-expanded-${width}.png`});await item.locator('summary').press('Enter');await p.screenshot({path:`${out}/comments-collapsed-${width}.png`});await close(p)
  await openLog(p);assert.equal(await p.getByText('기록 ID',{exact:true}).count(),0);assert.equal(await dialog(p).getByRole('heading',{name:'현재 댓글',exact:true}).count(),1);assert.equal(await p.getByText('조회한 현재 내용이며 조치 당시와 다를 수 있습니다.',{exact:true}).count(),1)
  await noOverflow(p);await p.screenshot({path:`${out}/log-comment-${width}.png`});await close(p)
  await select(p,201,true);await preview(p);assert.equal(await dialog(p).getByRole('heading',{name:'현재 게시글',exact:true}).count(),1);await noOverflow(p);await p.screenshot({path:`${out}/log-post-${width}.png`});await close(p)
  await uncertain(p,s);await btn(p,'닫기').click();await btn(p,'버리고 이동').click();await dialog(p).waitFor({state:'detached'});await ready(p);await p.screenshot({path:`${out}/uncertain-recovery-${width}.png`})
 }finally{await context.close()}
})
await run('followup close after reload does not assume previous list; forward can revisit closed flow',async()=>{
 const{p,context}=await setup()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await singleReady(p);await close(p);await ready(p)
  await p.goForward();await singleReady(p);assert.equal(await dialog(p).count(),1)
  await p.reload();await singleReady(p);await close(p);await ready(p);assert(!new URL(p.url()).searchParams.has('selected'))
  await p.goto(base+'/admin/operation-logs?selected=203&view=manage&sort=OLDEST');await singleReady(p);await close(p);await ready(p);assert.equal(new URL(p.url()).searchParams.get('sort'),'OLDEST')
 }finally{await context.close()}
})

await run('followup parent comment action corrects last page without resetting applied keyword/status',async()=>{
 const{p,context,s}=await setup()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await singleReady(p);await btn(p,'소속 게시글 보기').click();await preview(p);await btn(p,'댓글 관리로 이동').click();await commentReady(p)
  await p.getByRole('checkbox',{name:'숨김',exact:true}).uncheck();await p.getByRole('checkbox',{name:'삭제',exact:true}).uncheck();await p.getByLabel('댓글 본문 검색',{exact:true}).fill('현재');await btn(p,'댓글 검색').click();await commentReady(p)
  await p.getByRole('navigation',{name:'댓글 페이지'}).getByRole('button',{name:'다음',exact:true}).click();await p.locator('[data-admin-comment-id="920"]').waitFor();await commentReady(p)
  await p.locator('[data-admin-comment-id="920"]').getByRole('button',{name:'댓글 #920 숨김 처리',exact:true}).click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click()
  await p.waitForFunction(()=>document.querySelector('[data-admin-comment-id="900"]')&&!document.querySelector('[data-admin-comment-id="920"]'));await commentReady(p)
  assert.equal(await p.getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'현재');assert.equal(await p.getByRole('checkbox',{name:'숨김',exact:true}).isChecked(),false)
  const q=new URLSearchParams(commentReads(s).at(-1).search);assert.equal(q.get('page'),'0');assert.equal(q.get('keyword'),'현재');assert.deepEqual(q.getAll('statuses'),['VISIBLE'])
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})
await run('followup parent anchor refresh failure keeps subtree and locks actions until retry',async()=>{
 const{p,context,s}=await setup()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await singleReady(p);await btn(p,'소속 게시글 보기').click();await preview(p);await btn(p,'댓글 관리로 이동').click();await commentReady(p)
  await p.getByLabel('댓글 본문 검색',{exact:true}).fill('현재');await btn(p,'댓글 검색').click();await commentReady(p)
  s.commentDetail=r=>r.fulfill({status:503,json:{code:'INTERNAL_SERVER_ERROR'}})
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await btn(p,'댓글 정보 다시 시도').waitFor();await preview(p)
  assert.equal(await p.getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'현재');assert.equal(await btn(p,'게시글 숨김 해제').isDisabled(),true)
  s.commentDetail=null;await btn(p,'댓글 정보 다시 시도').click();await btn(p,'게시글 숨김 해제').waitFor();await p.waitForFunction(()=>!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='게시글 숨김 해제')?.disabled)
  assert.equal(await p.getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'현재')
 }finally{await context.close()}
})
await run('followup corrected list page survives explicit close and browser history',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/operation-logs');await ready(p);await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL('**/admin/posts?**');await ready(p)
  await p.getByRole('navigation',{name:'게시글 페이지'}).getByRole('button',{name:'다음',exact:true}).click();await ready(p);await select(p,101);await preview(p)
  s.list=(r,u,data)=>r.fulfill({json:{...data,items:Number(u.searchParams.get('page'))>0?[]:s.posts,totalElements:3,totalPages:1,hasNext:false,hasPrevious:Number(u.searchParams.get('page'))>0}})
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await p.waitForFunction(()=>new URL(location.href).searchParams.get('page')==='0');await preview(p)
  await close(p);await ready(p);assert.equal(new URL(p.url()).searchParams.get('page'),'0');await p.goBack();await ready(p);assert.equal(new URL(p.url()).pathname,'/admin/posts');assert.equal(new URL(p.url()).searchParams.get('page'),'0');assert.equal(await dialog(p).count(),0)
  await p.goForward();await ready(p);assert.equal(new URL(p.url()).pathname,'/admin/posts');assert.equal(new URL(p.url()).searchParams.get('page'),'0');assert.equal(await dialog(p).count(),0)
 }finally{await context.close()}
})

for(const status of [200,401,403,404])await run(`followup direct confirmation GET ${status} never replays action`,async()=>{
 const{p,context,s}=await setup()
 try{
  await uncertain(p,s);if(status!==200){if(status===401)s.member=null;s.detail=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':status===403?'ACCESS_DENIED':'POST_NOT_FOUND'}})}
  await btn(p,'최신 상태 확인').click()
  if(status===401||status===403){await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await dialog(p).count(),0);assert.equal(await p.locator('.admin-recovery').count(),0)}
  else {await p.getByText(status===200?'최신 상태를 확인했습니다.':'현재 대상을 찾을 수 없습니다.',{exact:false}).waitFor();assert.equal(await p.getByLabel('처리 사유 (필수)',{exact:true}).count(),0);await preview(p);if(status===200)assert(await btn(p,'게시글 숨김 처리').isEnabled());else assert.equal(await btn(p,'게시글 숨김 처리').count(),0)}
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})

await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
