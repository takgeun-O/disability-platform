import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-modal-followup/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`보청기 적응 경험을 나눕니다 ${id}`,content:`새 보청기를 사용한 지 한 달이 되었습니다. 🦻\n조용한 곳에서는 대화가 한결 편해졌지만, 사람이 많은 장소에서는 아직 적응이 필요합니다.\n\n`+'착용 시간을 조금씩 늘리며 느낀 점을 기록합니다. 여러분의 경험도 알려 주세요.\n'.repeat(32)+'마지막 게시글 문장입니다.',contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:8,currentTarget:null,targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
const comment=(id,postId=101)=>({commentId:id,postId,postStatus:'HIDDEN',content:`저도 비슷한 경험이 있어요. 🦻\n처음에는 조용한 곳에서 대화를 연습했습니다.\n\n`+'꾸준히 착용하면서 조금씩 적응하고 있습니다.\n'.repeat(32)+'공백없는긴문자열'.repeat(50)+'\n끝까지 읽어 주셔서 감사합니다.',author:{...author,nickname:'탈퇴한 회원',withdrawn:true},status:'VISIBLE',createdAt:stamp,updatedAt:stamp,contentUpdatedAt:null,hiddenAt:null,deletedAt:null})
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const selectRow=(p,name)=>p.getByRole('row',{name,exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
const preview=p=>p.locator('.admin-preview-post[aria-busy="false"]').waitFor()
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

const singleReady=p=>p.locator('.admin-preview-comment[aria-busy="false"]').waitFor()
const select=async(p,id,logs=false)=>selectRow(p,logs?`기록 #${id} 상세`:`게시글 #${id} 미리보기`).locator('.admin-badge').click()

const noOverflow=async p=>assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&Array.from(document.querySelectorAll('.admin-content-dialog,.admin-dialog-body,.admin-log-panel,.admin-log-panel-body')).every(e=>e.scrollWidth<=e.clientWidth+1)))
for(const width of [1440,320])for(const kind of ['POST','COMMENT'])for(const action of ['hide','restore'])await run(`action workspace ${width} ${kind} ${action}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.posts[0].status=action==='hide'?'VISIBLE':'HIDDEN';s.comments[0].status=action==='hide'?'VISIBLE':'HIDDEN'
  await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,kind==='POST'?201:203,true);if(kind==='POST')await preview(p);else await singleReady(p)
  await p.locator('#admin-log-panel-title').waitFor()
  const operation=action==='hide'?'숨김 처리':'숨김 해제',trigger=btn(p,kind==='POST'?`게시글 ${operation}`:`댓글 #900 ${operation}`)
  await trigger.click();const area=p.locator('.admin-action-workspace'),form=area.locator('.admin-inline-moderation'),body=p.locator('.admin-log-panel-body'),field=form.getByLabel('처리 사유 (필수)',{exact:true}),submit=btn(p,`${operation} 실행`)
  await area.waitFor();assert.equal(await area.locator('.admin-full-content').textContent(),kind==='POST'?s.posts[0].content:s.comments[0].content)
  assert.equal(await btn(p,'댓글 관리로 이동').count(),0);assert.equal(await trigger.count(),0)
  const geometry=await area.evaluate(e=>{const a=e.querySelector('.admin-action-content').getBoundingClientRect(),b=e.querySelector('.admin-action-form').getBoundingClientRect();return{a:{x:a.x,y:a.y,width:a.width,right:a.right,bottom:a.bottom},b:{x:b.x,y:b.y,width:b.width}}})
  assert(geometry.b.y>=geometry.a.bottom,'현재 우측 패널은 본문 다음에 조치 폼을 배치한다');assert(Math.abs(geometry.a.width-geometry.b.width)<2)
  await p.screenshot({path:`${out}/action-${kind}-${action}-${width}.png`})
  await field.focus();await p.waitForFunction(()=>{const e=document.querySelector('.admin-action-form select'),r=e.getBoundingClientRect(),b=e.closest('.admin-log-panel-body').getBoundingClientRect();return r.top>=b.top&&r.bottom<=Math.min(b.bottom,innerHeight)})
  assert(await body.evaluate(e=>e.clientHeight>0))
  await submit.click();await form.getByText('처리 사유를 선택해 주세요.',{exact:true}).waitFor();assert(await field.evaluate(e=>e===document.activeElement));assert.equal(s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length,0)
  await field.selectOption(action==='hide'?'PERSONAL_INFORMATION':'NO_VIOLATION')
  s.write=r=>r.fulfill({status:400,json:{code:'INVALID_REASON'}})
  for(let i=0;i<2;i++){await submit.click();await form.getByText('선택한 처리 사유를 확인해 주세요.',{exact:true}).waitFor();await p.waitForFunction(()=>document.querySelector('.admin-action-form select')===document.activeElement);assert.equal(await field.getAttribute('aria-invalid'),'true')}
  await field.selectOption('OTHER');const detail=form.getByLabel('상세 사유 (필수)',{exact:true});await detail.fill('검토한 상세 내용');await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));const documentPosition=await p.evaluate(()=>scrollY);await submit.click();await form.getByText('상세 사유를 확인해 주세요. 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.',{exact:true}).waitFor();assert(await detail.evaluate(e=>e===document.activeElement));
  const metrics=await detail.evaluate(e=>{const b=e.closest('.admin-log-panel-body').getBoundingClientRect(),r=e.getBoundingClientRect(),error=document.getElementById(e.getAttribute('aria-describedby').split(' ')[0]).getBoundingClientRect();return{top:r.top,bottom:error.bottom,bt:b.top,bb:b.bottom,scroll:scrollY,border:getComputedStyle(e).borderColor}})
  assert(metrics.top>=metrics.bt&&metrics.bottom<=metrics.bb,JSON.stringify(metrics));assert.equal(metrics.scroll,documentPosition);assert.equal(metrics.border,'rgb(180, 35, 53)')
  await noOverflow(p);await p.screenshot({path:`${out}/server-error-${kind}-${action}-${width}.png`})
  s.write=null;await detail.fill('최종 확인한 상세 내용');await submit.click();await area.waitFor({state:'detached'});if(kind==='POST')await preview(p);else await singleReady(p)
  const marker=p.locator(`[data-moderation-location="101:${kind==='POST'?'post':900}"]`);await marker.getByRole('status').waitFor();await p.waitForFunction(()=>document.activeElement?.hasAttribute('data-moderation-location'))
  assert.equal(await marker.getAttribute('data-moderation-location'),await p.evaluate(()=>document.activeElement.getAttribute('data-moderation-location')))
  await p.screenshot({path:`${out}/completed-${kind}-${action}-${width}.png`});await btn(p,'상세 닫기').click();await p.locator('#admin-log-panel-title').waitFor({state:'detached'});assert(await p.locator(`[data-log-id="${kind==='POST'?201:203}"]`).evaluate(e=>e===document.activeElement))
 }finally{await context.close()}
})
for(const width of [1440,320])await run(`comment list location filter and cancel ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);await btn(p,'댓글 관리로 이동').click();await p.locator('.admin-comment-results[aria-busy="false"]').waitFor()
  const filters=p.locator('.admin-comments');await filters.getByRole('checkbox',{name:'숨김',exact:true}).uncheck();await filters.getByRole('checkbox',{name:'삭제',exact:true}).uncheck();await btn(p,'댓글 검색').click();await p.locator('.admin-comment-results[aria-busy="false"]').waitFor()
  const trigger=btn(p,'댓글 #902 숨김 처리');await trigger.scrollIntoViewIfNeeded();const before=await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop)
  await trigger.click();await p.locator('.admin-action-workspace').waitFor();await btn(p,'취소').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});assert(await trigger.evaluate(e=>e===document.activeElement));assert(Math.abs(await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop)-before)<3)
  await trigger.click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});await p.getByText('현재 댓글 결과에 포함되지 않습니다.',{exact:false}).waitFor();await p.waitForFunction(()=>document.activeElement?.classList.contains('admin-comment-results'))
  assert.equal(await trigger.count(),0);assert.equal(await filters.getByRole('checkbox',{name:'숨김',exact:true}).isChecked(),false);assert.equal(await filters.getByRole('checkbox',{name:'삭제',exact:true}).isChecked(),false)
  assert.equal(s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length,1);await p.screenshot({path:`${out}/filtered-complete-${width}.png`})
 }finally{await context.close()}
})
for(const size of [{width:1440,height:600},{width:720,height:500}])await run(`short viewport ${size.width}x${size.height}`,async()=>{
 const{p,context}=await setup(size.width)
 try{await p.setViewportSize(size);await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);await btn(p,'게시글 숨김 처리').click();await p.locator('.admin-action-workspace').waitFor();assert.equal(await p.locator('.admin-action-form').evaluate(e=>getComputedStyle(e).position),'static');await btn(p,'숨김 처리 실행').click();await p.getByText('처리 사유를 선택해 주세요.',{exact:true}).waitFor();assert(await p.getByLabel('처리 사유 (필수)',{exact:true}).evaluate(e=>{const r=e.getBoundingClientRect(),b=e.closest('.admin-dialog-body').getBoundingClientRect();return e===document.activeElement&&r.top>=b.top&&r.bottom<=b.bottom}));await noOverflow(p);await p.screenshot({path:`${out}/short-${size.width}.png`})}finally{await context.close()}
})
for(const width of [1440,320])await run(`short content post-list entry ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.posts[0].content='새 보청기를 사용한 지 한 달이 되었습니다. 🦻\n조용한 곳에서는 대화가 한결 편해졌지만, 사람이 많은 장소에서는 아직 적응이 필요합니다.\n\n처음에는 착용 시간이 짧았는데 조금씩 늘려 가고 있습니다. 사용하면서 느낀 점과 적응 과정을 함께 나누고 싶습니다.\n\n비슷한 경험이 있는 분들은 어떤 방법이 도움이 되었는지 알려 주세요.'
  await p.goto(base+'/admin/posts');await ready(p);await select(p,101);await preview(p);await btn(p,'게시글 숨김 처리').click();await p.locator('.admin-action-workspace').waitFor()
  assert(await p.locator('.admin-action-content h3').evaluate(e=>e===document.activeElement));assert.equal(await p.locator('.admin-dialog-body').evaluate(e=>e.scrollTop),0)
  assert.equal(await p.locator('.admin-action-content .admin-full-content').textContent(),s.posts[0].content)
  await noOverflow(p);if(width===1440)assert(await p.locator('.admin-content-dialog').evaluate(e=>e.getBoundingClientRect().height<innerHeight-180));await p.screenshot({path:`${out}/short-post-${width}.png`});await btn(p,'취소').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});assert(await btn(p,'게시글 숨김 처리').evaluate(e=>e===document.activeElement))
 }finally{await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
