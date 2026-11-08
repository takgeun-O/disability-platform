import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-modal-followup/browser'
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

const singleReady=p=>p.locator('.admin-preview-comment[aria-busy="false"]').waitFor()
const select=async(p,id,logs=false)=>selectRow(p,logs?`기록 #${id} 상세`:`게시글 #${id} 미리보기`).locator('.admin-badge').click()

const dialog=p=>p.getByRole('dialog')
const noOverflow=async p=>assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&Array.from(document.querySelectorAll('.admin-content-dialog,.admin-dialog-body')).every(e=>e.scrollWidth<=e.clientWidth+1)))
const close=async p=>{await btn(p,'닫기').click();await dialog(p).waitFor({state:'detached'})}
for(const width of [1440,320])for(const kind of ['POST','COMMENT'])for(const action of ['hide','restore'])await run(`form errors focus cancel header ${width} ${kind} ${action}`,async()=>{
 const{p,context,s}=await setup(width),g=gate()
 try{
  s.posts[0].status=action==='hide'?'VISIBLE':'HIDDEN';s.comments[0].status=action==='hide'?'VISIBLE':'HIDDEN'
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,kind==='POST'?201:203,true);if(kind==='POST')await preview(p);else await singleReady(p)
  await btn(p,kind==='POST'?'이 게시글 관리':'이 댓글 관리').click();if(kind==='POST')await preview(p);else await singleReady(p)
  const target=kind==='POST'?'게시글':'댓글',id=kind==='POST'?101:900,operation=action==='hide'?'숨김 처리':'숨김 해제',trigger=btn(p,kind==='POST'?`게시글 ${operation}`:`댓글 #900 ${operation}`)
  await trigger.click();const form=p.locator('.admin-inline-moderation'),selectField=form.getByLabel('처리 사유 (필수)',{exact:true}),submit=btn(p,`${operation} 실행`)
  await form.getByRole('heading',{name:`${target} #${id} ${operation}`,exact:true}).waitFor();assert.equal(await trigger.count(),0);await p.getByRole('region',{name:`조치 대상 ${target}`,exact:true}).waitFor()
  if(kind==='COMMENT'){assert((await form.innerText()).includes(action==='hide'?'이 댓글을 일반 사용자에게 숨깁니다.':'이 댓글의 숨김을 해제합니다.'));assert.equal((await form.innerText()).includes('소속 게시글이 숨김 상태여서'),action==='restore')}
  const visibleError=async field=>{
   assert(await field.evaluate(e=>e===document.activeElement));assert.equal(await field.getAttribute('aria-invalid'),'true')
   const m=await field.evaluate(e=>{const body=e.closest('.admin-dialog-body'),r=e.getBoundingClientRect(),b=body.getBoundingClientRect(),err=document.getElementById(e.getAttribute('aria-describedby').split(' ')[0]),er=err.getBoundingClientRect();return{labelTop:document.querySelector(`label[for="${CSS.escape(e.id)}"]`).getBoundingClientRect().top,top:r.top,bottom:r.bottom,bt:b.top,bb:b.bottom,et:er.top,eb:er.bottom,border:getComputedStyle(e).borderColor,color:getComputedStyle(err).color,scroll:scrollY}})
   assert(m.labelTop>=m.bt&&m.top>=m.bt&&m.bottom<=m.bb&&m.et>=m.bt&&m.eb<=m.bb,JSON.stringify(m));assert.equal(m.border,'rgb(180, 35, 53)');assert.equal(m.color,'rgb(158, 32, 48)');assert.equal(m.scroll,0)
  }
  for(let i=0;i<2;i++){await submit.click();await form.getByText('처리 사유를 선택해 주세요.',{exact:true}).waitFor();await visibleError(selectField)}
  await noOverflow(p);await p.screenshot({path:`${out}/missing-${kind}-${action}-${width}.png`})
  await selectField.selectOption('OTHER');assert.equal(await selectField.getAttribute('aria-invalid'),'false');const detail=form.getByLabel('상세 사유 (필수)',{exact:true})
  for(const value of ['', '   ', '🦻'.repeat(501)]){
   await detail.fill(value);await submit.click();await visibleError(detail);await form.getByText(value.length>10?'상세 사유는 앞뒤 공백을 제외하고 500자 이하여야 합니다.':'상세 사유를 입력해 주세요.',{exact:true}).waitFor()
  }
  assert.equal(s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length,0)
  await p.screenshot({path:`${out}/detail-error-${kind}-${action}-${width}.png`});await detail.fill('정상 상세 사유');assert.equal(await detail.getAttribute('aria-invalid'),'false')
  await btn(p,'취소').click();await btn(p,'계속 작성').click();assert.equal(await detail.inputValue(),'정상 상세 사유');await btn(p,'취소').click();await btn(p,'버리고 이동').click();await form.waitFor({state:'detached'});assert(await trigger.evaluate(e=>e===document.activeElement))
  await trigger.click();await selectField.selectOption('OTHER');await detail.fill('보존할 사유')
  await btn(p,'운영 기록으로 돌아가기').click();await btn(p,'계속 작성').click();assert.equal(await detail.inputValue(),'보존할 사유')
  await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop=e.scrollHeight);const back=btn(p,'운영 기록으로 돌아가기');assert(await back.evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight}))
  await noOverflow(p);await p.screenshot({path:`${out}/fixed-header-${kind}-${action}-${width}.png`})
  s.write=async r=>{g.arrive();await g.wait;return r.fulfill({json:{postId:101,...(kind==='COMMENT'?{commentId:900}:{}),hidden:action==='hide',hiddenAt:action==='hide'?stamp:null,changed:false}})}
  await submit.click();await g.arrived;await form.getByRole('status').filter({hasText:'처리 중입니다.'}).waitFor();assert(await form.locator('button[type=submit]').isDisabled());const url=p.url();await back.click();assert.equal(p.url(),url);assert.equal(await form.count(),1);g.release();await form.waitFor({state:'detached'})
  await back.click();await dialog(p).getByRole('heading',{name:kind==='POST'?'운영 기록 · #201':'운영 기록 · #203',exact:true}).waitFor()
  if(kind==='COMMENT'){await singleReady(p);await btn(p,'소속 게시글 보기').click();await preview(p);await btn(p,'댓글 관리로 돌아가기').click();await singleReady(p)}
  await close(p);assert.equal(s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length,1)
 }finally{g.release();await context.close()}
})
for(const width of [1440,320])await run(`standalone form error and cancel focus ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  await p.goto(base+'/admin/posts/101');await btn(p,'게시글 숨김 처리').waitFor();const trigger=btn(p,'게시글 숨김 처리');await trigger.click()
  const form=p.locator('.admin-inline-moderation'),field=form.getByLabel('처리 사유 (필수)',{exact:true});await btn(p,'숨김 처리 실행').click()
  await form.getByText('처리 사유를 선택해 주세요.',{exact:true}).waitFor();assert(await field.evaluate(e=>e===document.activeElement));assert.equal(await field.evaluate(e=>getComputedStyle(e).borderColor),'rgb(180, 35, 53)')
  await btn(p,'취소').click();await form.waitFor({state:'detached'});assert(await trigger.evaluate(e=>e===document.activeElement));assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,0)
 }finally{await context.close()}
})
await run('header return discard confirmation uses existing history flow',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/operation-logs?sort=OLDEST&page=1');await ready(p);await select(p,201,true);await preview(p);await btn(p,'이 게시글 관리').click();await preview(p)
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.getByLabel('상세 사유 (필수)',{exact:true}).fill('보존 또는 폐기할 사유')
  await btn(p,'운영 기록으로 돌아가기').click();await btn(p,'계속 작성').click();assert.equal(await p.getByLabel('상세 사유 (필수)',{exact:true}).inputValue(),'보존 또는 폐기할 사유')
  await btn(p,'운영 기록으로 돌아가기').click();await btn(p,'버리고 이동').click();await dialog(p).getByRole('heading',{name:'운영 기록 · #201',exact:true}).waitFor();assert.equal(await p.locator('.admin-inline-moderation').count(),0)
  await p.goBack();await dialog(p).getByRole('heading',{name:'게시글 #101 관리',exact:true}).waitFor();assert.equal(await p.locator('.admin-inline-moderation').count(),0);await p.goForward();await dialog(p).getByRole('heading',{name:'운영 기록 · #201',exact:true}).waitFor()
  await close(p);await ready(p);assert.equal(new URL(p.url()).searchParams.get('sort'),'OLDEST');assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.equal(s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length,0)
 }finally{await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
