import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-log-panel-followup/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`보청기 적응 경험을 나눕니다 ${id}`,content:`새 보청기를 사용한 지 한 달이 되었습니다. 🦻\n조용한 곳에서는 대화가 한결 편해졌지만, 사람이 많은 장소에서는 아직 적응이 필요합니다.\n\n`+'착용 시간을 조금씩 늘리며 느낀 점을 기록합니다. 여러분의 경험도 알려 주세요.\n'.repeat(32)+'마지막 게시글 문장입니다.',contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:88,currentTarget:{author,title:targetType==='POST'?'현재 제목':null,contentPreview:'목록 미리보기 🦻'},targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
const comment=(id,postId=101)=>({commentId:id,postId,postStatus:'HIDDEN',content:`저도 비슷한 경험이 있어요. 🦻\n처음에는 조용한 곳에서 대화를 연습했습니다.\n\n`+'꾸준히 착용하면서 조금씩 적응하고 있습니다.\n'.repeat(32)+'공백없는긴문자열'.repeat(50)+'\n끝까지 읽어 주셔서 감사합니다.',author:{...author,nickname:'탈퇴한 회원',withdrawn:true},status:'VISIBLE',createdAt:stamp,updatedAt:stamp,contentUpdatedAt:null,hiddenAt:null,deletedAt:null})
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
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

const menu=p=>p.locator('.admin-sidebar').getByRole('link',{name:'게시글 관리',exact:true})
const moved=async p=>{await p.waitForURL(u=>u.pathname==='/admin/posts');await p.getByRole('heading',{name:'게시글 관리',exact:true}).waitFor()}
const targetButton=(p,kind)=>btn(p,kind==='POST'?'게시글 숨김 처리':'댓글 #900 숨김 처리')
const selectTarget=async(p,kind)=>{await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,kind==='POST'?201:203);await detailReady(p);await targetButton(p,kind).click()}
// Scroll the actual region with the keyboard; never click/scrollIntoView to reveal buttons.
async function reachActions(p){
 const body=p.locator('.admin-log-panel-body')
 const visible=()=>p.locator('.admin-moderation-actions').evaluate(e=>{
  const r=e.getBoundingClientRect(),panel=e.closest('.admin-log-panel').getBoundingClientRect(),b=e.closest('.admin-log-panel-body').getBoundingClientRect()
  return r.top>=b.top&&r.bottom<=Math.min(panel.bottom,b.bottom,innerHeight)&&[...e.querySelectorAll('button')].every(x=>{const q=x.getBoundingClientRect();return x.contains(document.elementFromPoint(q.x+q.width/2,q.y+q.height/2))})
 })
 if(await p.locator('.admin-log-panel').getAttribute('data-docked')==='true')assert(await visible(),'docked actions must already be visible')
 else{
  await body.focus()
  // PageDown scrolls the focused region on macOS too (Control+End does not).
  for(let i=0;i<12;i++){
   await p.keyboard.press('PageDown');await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
   if(await visible())break
  }
  assert(await visible(),'all actions visible and unobscured at scroll end')
 }
 await body.focus();await p.keyboard.press('End');
 // Tab order remains usable without relying on click's automatic scrolling.
 for(let i=0;i<30;i++){await p.keyboard.press('Tab');if(await btn(p,'숨김 처리 실행').evaluate(e=>e===document.activeElement))break}
 assert(await btn(p,'숨김 처리 실행').evaluate(e=>e===document.activeElement));assert(await visible())
}
for(const kind of ['POST','COMMENT'])for(const mode of ['empty','reason','other','cancel','success','failure','busy'])await run(`sidebar ${kind} ${mode}`,async()=>{
 const{p,context,s}=await setup(1440),g=gate()
 try{
  await selectTarget(p,kind)
  if(mode!=='empty')await reason(p).selectOption(mode==='other'?'OTHER':'SPAM_ADVERTISING')
  if(mode==='other')await p.locator('.admin-inline-moderation textarea').fill('작성 중인 상세 사유 🦻')
  if(mode==='cancel'){await btn(p,'취소').click();await btn(p,'입력 버리고 이동').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'})}
  if(mode==='success'){await btn(p,'숨김 처리 실행').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});await detailReady(p);await ready(p)}
  if(mode==='failure'){s.write=r=>r.fulfill({status:400,json:{code:'INVALID_REASON'}});await btn(p,'숨김 처리 실행').click();await p.getByText('선택한 처리 사유를 확인해 주세요.',{exact:true}).waitFor()}
  if(mode==='busy'){
   s.write=async r=>{g.arrive();await g.wait;return r.fulfill({status:400,json:{code:'INVALID_REASON'}})}
   await btn(p,'숨김 처리 실행').click();await g.arrived;await menu(p).click();await p.getByRole('dialog').getByRole('heading',{name:'처리 중입니다',exact:true}).waitFor();assert.equal(await p.getByRole('button',{name:'입력 버리고 이동',exact:true}).count(),0)
   await p.screenshot({path:`${out}/busy-${kind}.png`});g.release();await p.getByRole('dialog').waitFor({state:'detached'});await p.getByText('선택한 처리 사유를 확인해 주세요.',{exact:true}).waitFor()
  }
  await menu(p).click()
  if(['reason','other','failure','busy'].includes(mode)){
   const dialog=p.getByRole('dialog');await dialog.waitFor();assert(await dialog.evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight}));assert(await btn(p,'계속 작성').evaluate(e=>e===document.activeElement))
   if(mode==='other'&&kind==='POST')await p.screenshot({path:`${out}/discard-confirmation.png`});await btn(p,'계속 작성').click();assert.equal(await reason(p).inputValue(),mode==='other'?'OTHER':'SPAM_ADVERTISING');if(mode==='other')assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'작성 중인 상세 사유 🦻')
   await menu(p).click();await btn(p,'입력 버리고 이동').click()
  }
  await moved(p);assert.equal(await p.getByRole('dialog').count(),0);assert.equal(s.errors.length,0)
 }finally{g.release();await context.close()}
})
for(const kind of ['POST','COMMENT'])await run(`history input guard ${kind}`,async()=>{
 const{p,context}=await setup()
 try{
  await selectTarget(p,kind);await reason(p).selectOption('OTHER');await p.locator('.admin-inline-moderation textarea').fill('뒤로가기에도 유지')
  await p.goBack();await btn(p,'계속 작성').waitFor();await btn(p,'계속 작성').click();assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'뒤로가기에도 유지');assert(new URL(p.url()).searchParams.has('selected'))
  await p.goBack();await btn(p,'입력 버리고 이동').click();await p.waitForFunction(()=>!new URL(location.href).searchParams.has('selected'));await ready(p)
  await p.goForward();await detailReady(p);assert.equal(await p.locator('.admin-action-workspace').count(),0)
 }finally{await context.close()}
})
for(const size of [{width:1920,height:1080},{width:1440,height:900},{width:1366,height:768},{width:1280,height:720},{width:1339,height:900},{width:1338,height:900},{width:1337,height:900},{width:320,height:900},{width:1440,height:550}])await run(`stable layout ${size.width}x${size.height}`,async()=>{
 const{p,context,s}=await setup(size.width)
 try{
  await p.setViewportSize(size);s.logs=Array.from({length:20},(_,i)=>log(201+i,i%2?'COMMENT':'POST'))
  await p.goto(base+'/admin/operation-logs');await ready(p);await p.locator('[data-log-id="220"]').scrollIntoViewIfNeeded();const listScroll=await p.evaluate(()=>scrollY)
  await select(p,220);await detailReady(p)
  const compact=await p.locator('.admin-log-workspace').getAttribute('data-compact')==='true'
  const actualWorkspace=await p.locator('.admin-workspace').evaluate(e=>e.clientWidth-parseFloat(getComputedStyle(e).paddingLeft)-parseFloat(getComputedStyle(e).paddingRight))
  assert.equal(compact,actualWorkspace<1100)
  if([1337,1338,1339].includes(size.width))assert.equal(actualWorkspace,size.width-238)
  const measure=()=>p.locator('.admin-master-list').evaluate(e=>({width:e.getBoundingClientRect().width,table:getComputedStyle(e.querySelector('table')).display}))
  const before=await measure();await p.screenshot({path:`${out}/read-${size.width}-${size.height}.png`});await targetButton(p,'COMMENT').click();const after=await measure()
  assert.equal(before.table,after.table);assert(Math.abs(before.width-after.width)<1);if(compact)assert.equal(after.width,0)
  assert.equal(await p.locator('.admin-action-content .admin-full-content').textContent(),s.comments[0].content)
  const content=p.locator('.admin-action-content'),form=p.locator('.admin-action-form')
  assert(await form.evaluate(e=>{const a=e.previousElementSibling.getBoundingClientRect(),b=e.getBoundingClientRect();return b.top>=a.bottom}))
  await btn(p,'숨김 처리 실행').click();assert.equal(countWrites(s),0);assert(await reason(p).evaluate(e=>e===document.activeElement));await noOverflow(p)
  await reason(p).selectOption('OTHER');await p.locator('.admin-inline-moderation textarea').fill('입력 내용은 배치 전환 후에도 유지됩니다.')
  await p.waitForFunction(()=>{const e=document.querySelector('.admin-inline-moderation textarea'),r=e.getBoundingClientRect(),b=e.closest('.admin-log-panel-body').getBoundingClientRect();return r.top>=b.top&&r.bottom<=Math.min(b.bottom,innerHeight)});await p.screenshot({path:`${out}/action-${size.width}-${size.height}.png`})
  const scrollable=await p.locator('.admin-log-panel').getAttribute('data-docked')==='true'?content:p.locator('.admin-log-panel-body')
  await scrollable.evaluate(e=>{
    const text=document.querySelector('.admin-action-content .admin-full-content').firstChild,range=document.createRange();range.setStart(text,text.length-20);range.setEnd(text,text.length);const r=range.getBoundingClientRect(),b=e.getBoundingClientRect();e.scrollTop+=r.bottom-b.bottom+20
  });assert(await content.evaluate(e=>{const text=e.querySelector('.admin-full-content').firstChild,range=document.createRange();range.setStart(text,text.length-20);range.setEnd(text,text.length);const r=range.getBoundingClientRect(),panel=e.closest('.admin-log-panel-body').getBoundingClientRect();return r.top>=panel.top&&r.bottom<=Math.min(panel.bottom,innerHeight)}))
  await p.screenshot({path:`${out}/content-end-${size.width}-${size.height}.png`})
  if(!compact){
  const savedScroll=await scrollable.evaluate(e=>e.scrollTop),readingScroll=await p.locator('.admin-log-panel').getAttribute('data-docked')==='true';await btn(p,'상세 넓게 보기').click();await p.screenshot({path:`${out}/expanded-${size.width}-${size.height}.png`});assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'입력 내용은 배치 전환 후에도 유지됩니다.');await noOverflow(p);await btn(p,'기본 배치로').click();await p.waitForFunction(({savedScroll,readingScroll})=>Math.abs(document.querySelector(readingScroll?'.admin-action-content':'.admin-log-panel-body').scrollTop-savedScroll)<2,{savedScroll,readingScroll});assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'입력 내용은 배치 전환 후에도 유지됩니다.')
  }else {assert.equal(await btn(p,'상세 넓게 보기').count(),0);assert.equal(await btn(p,'기본 배치로').count(),0)}
  await reachActions(p)
  const stable=await p.evaluate(async()=>{const modes=[];for(let i=0;i<8;i++){await new Promise(requestAnimationFrame);modes.push(document.querySelector('.admin-log-panel').dataset.docked)}return modes});assert.equal(new Set(stable).size,1,'scroll mode must settle')
  await p.screenshot({path:`${out}/form-bottom-${size.width}-${size.height}.png`})

  if(compact){await btn(p,'목록으로 돌아가기').click();assert(await p.locator('[data-log-id="220"]').evaluate(e=>e===document.activeElement));console.log('scroll evidence',size.width,{listScroll,returned:await p.evaluate(()=>scrollY)});assert(Math.abs(await p.evaluate(()=>scrollY)-listScroll)<3);assert.equal(new URL(p.url()).searchParams.get('selected'),'220');await btn(p,'선택한 상세 보기').click();assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'입력 내용은 배치 전환 후에도 유지됩니다.')}
  await btn(p,'취소').click();await btn(p,'입력 버리고 이동').click();await p.locator('.admin-action-workspace').waitFor({state:'detached'});await btn(p,'상세 닫기').click();await p.locator('#admin-log-panel-title').waitFor({state:'detached'})
  assert.equal(s.errors.length,0)
  await fs.writeFile(`${out}/measure-${size.width}-${size.height}.json`,JSON.stringify({size,actualWorkspace,compact,before,after,listScroll},null,2))
 }finally{await p.screenshot({path:`${out}/end-${size.width}-${size.height}.png`});console.log('layout end',size.width,await p.evaluate(()=>{const field=document.querySelector('.admin-inline-moderation textarea'),body=document.querySelector('.admin-log-panel-body');return {field:field?.getBoundingClientRect().toJSON(),body:body?.getBoundingClientRect().toJSON(),scroll:body?.scrollTop,docked:document.querySelector('.admin-log-panel')?.dataset.docked,docScroll:scrollY}}));await context.close()}
})
await run('short comment has no forced reading height',async()=>{
 const{p,context,s}=await setup(1920)
 try{
  await p.setViewportSize({width:1920,height:1080});s.comments[0].content='짧은 댓글도 그대로 확인합니다. 🦻'
  await selectTarget(p,'COMMENT');assert(await p.locator('.admin-action-content').evaluate(e=>e.getBoundingClientRect().height<300));assert.equal(await p.locator('.admin-action-content .admin-full-content').textContent(),s.comments[0].content)
  await p.screenshot({path:`${out}/short-comment.png`})
 }finally{await context.close()}
})
for(const status of [200,401])await run(`late refresh ${status} after approved menu departure ignored`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await selectTarget(p,'POST');await reason(p).selectOption('SPAM_ADVERTISING')
  s.list=async(r,u,data)=>{if(!u.pathname.endsWith('/operation-logs'))return r.fulfill({json:data});g.arrive();await g.wait;return status===200?r.fulfill({json:data}):r.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED'}})}
  await btn(p,'숨김 처리 실행').click();await g.arrived;await p.locator('.admin-action-workspace').waitFor({state:'detached'});await menu(p).click();await moved(p)
  const before=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,before);await p.getByRole('heading',{name:'게시글 관리',exact:true}).waitFor();assert.equal(await p.locator('.admin-log-panel').count(),0);assert.equal(await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).count(),0)
 }finally{g.release();await context.close()}
})
await run('actual workspace boundary and live draft resize',async()=>{
 const {p,context,s}=await setup(1600)
 try{
  await selectTarget(p,'COMMENT');await reason(p).selectOption('OTHER');await btn(p,'숨김 처리 실행').click();const error=p.locator('.admin-inline-moderation [role="alert"]').first();await error.waitFor()
  const errorText=await error.textContent(),calls=s.calls.length,writes=countWrites(s),evidence=[]
  for(const width of [1101,1100,1099,1101,1099,1100]){
   const actual=await p.locator('.admin-log-workspace').evaluate(e=>e.parentElement.getBoundingClientRect().width)
   // Observe the same element as the product's ResizeObserver (admin-workspace).
   const measured=await p.locator('.admin-workspace').evaluate(e=>e.clientWidth-parseFloat(getComputedStyle(e).paddingLeft)-parseFloat(getComputedStyle(e).paddingRight))
   await p.setViewportSize({width:Math.round(p.viewportSize().width+width-measured),height:900})
   await p.waitForFunction(({width,compact})=>{const e=document.querySelector('.admin-workspace'),c=getComputedStyle(e),w=e.clientWidth-parseFloat(c.paddingLeft)-parseFloat(c.paddingRight);return Math.abs(w-width)<.1&&document.querySelector('.admin-log-workspace').dataset.compact===String(compact)},{width,compact:width<1100})
   evidence.push({target:width,viewport:p.viewportSize(),previousOuter:actual,measured:await p.locator('.admin-workspace').evaluate(e=>e.clientWidth-parseFloat(getComputedStyle(e).paddingLeft)-parseFloat(getComputedStyle(e).paddingRight))})
   assert.equal(await reason(p).inputValue(),'OTHER');assert.equal(await error.textContent(),errorText);assert.equal(new URL(p.url()).searchParams.get('selected'),'203')
   assert.equal(await btn(p,'상세 넓게 보기').count(),width<1100?0:1);assert.equal(await btn(p,'목록으로 돌아가기').count(),width<1100?1:0)
  }
  assert.equal(s.calls.length,calls);assert.equal(countWrites(s),writes)
  await p.locator('textarea').fill('반응형 전환 중 유지할 상세 사유');await btn(p,'상세 넓게 보기').click()
  await p.setViewportSize({width:1000,height:720});await btn(p,'목록으로 돌아가기').waitFor();await reachActions(p);const priorReading=await p.locator('.admin-log-panel-body').evaluate(e=>e.scrollTop);assert(priorReading>0);assert.equal(await btn(p,'기본 배치로').count(),0)
  await btn(p,'목록으로 돌아가기').click();await btn(p,'선택한 상세 보기').click();await p.waitForFunction(()=>document.querySelector('.admin-log-panel-body').scrollTop>0);assert.equal(await p.locator('textarea').inputValue(),'반응형 전환 중 유지할 상세 사유')
  await p.setViewportSize({width:1600,height:900});await btn(p,'기본 배치로').waitFor();await btn(p,'기본 배치로').click();assert.equal(await p.locator('textarea').inputValue(),'반응형 전환 중 유지할 상세 사유');assert.equal(countWrites(s),writes)
  await fs.writeFile(out+'/actual-boundary.json',JSON.stringify(evidence,null,2))
 }finally{await context.close()}
})
await run('resized textarea history and server error keep actions accessible',async()=>{
 const {p,context,s}=await setup(1920)
 try{
  await p.setViewportSize({width:1920,height:1080});await selectTarget(p,'COMMENT');await reason(p).selectOption('OTHER');await p.locator('textarea').fill('확인한 상세 내용');
  // Wait for ResizeObserver layout before grabbing the native handle. The
  // summary changes the initial scroll position; dragging a stale box misses it.
  await p.locator('textarea').scrollIntoViewIfNeeded()
  assert(await p.locator('textarea').evaluate(e=>new Promise(resolve=>{
   let previous='',stable=0,frames=0
   const sample=()=>{const r=e.getBoundingClientRect(),box=JSON.stringify([r.x,r.y,r.width,r.height]);stable=box===previous?stable+1:0;previous=box
    if(stable>=3)return resolve(r.top>=0&&r.bottom<=innerHeight)
    if(++frames>=60)return resolve(false)
    requestAnimationFrame(sample)
   };requestAnimationFrame(sample)
  })),'native resize handle is visible and settled')
  // Native textarea resize handle, not a style mutation.
  const area=await p.locator('textarea').boundingBox();await p.mouse.move(area.x+area.width-5,area.y+area.height-5);await p.mouse.down();await p.mouse.move(area.x+area.width-5,area.y+area.height+180,{steps:12});await p.mouse.up();assert((await p.locator('textarea').boundingBox()).height>area.height+100,'native textarea resizing increased actual height');
  await reachActions(p)
  const stable=await p.evaluate(async()=>{const modes=[];for(let i=0;i<8;i++){await new Promise(requestAnimationFrame);modes.push(document.querySelector('.admin-log-panel').dataset.docked)}return modes});assert.equal(new Set(stable).size,1,'scroll mode must settle')
  await p.screenshot({path:out+'/textarea-resized.png'})
  s.write=r=>r.fulfill({status:400,json:{code:'INVALID_REASON'}});await p.keyboard.press('Enter');await p.getByText('상세 사유를 확인해 주세요. 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.',{exact:true}).waitFor();assert(await p.locator('textarea').evaluate(e=>e===document.activeElement));assert.equal(countWrites(s),1);await reachActions(p)
  await p.locator('.admin-log-action-history summary').click();await reachActions(p);await p.screenshot({path:out+'/history-open-bottom.png'})
  s.write=null;await p.locator('textarea').fill('다시 확인한 상세 내용');await reachActions(p);await p.keyboard.press('Enter');await p.locator('.admin-action-workspace').waitFor({state:'detached'});assert.equal(countWrites(s),2)
 }finally{await context.close()}
})

for(const ending of ['cancel','success'])await run(`action end height ${ending}`,async()=>{
 const {p,context,s}=await setup(1440)
 try{
  await p.setViewportSize({width:1440,height:550});await selectTarget(p,'COMMENT')
  if(ending==='cancel')await btn(p,'취소').click()
  else{await reason(p).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click()}
  await p.locator('.admin-action-workspace').waitFor({state:'detached'});await detailReady(p)
  if(ending==='success')await btn(p,'댓글 #900 숨김 해제').waitFor()
  const panel=p.locator('.admin-log-panel'),before=await panel.boundingBox(),evidence=[]
  await p.screenshot({path:`${out}/height-${ending}-before.png`})
  for(const height of [1080,650,1000]){
   await p.setViewportSize({width:1440,height});
   await p.waitForFunction(({height})=>{const e=document.querySelector('.admin-log-panel'),r=e.getBoundingClientRect();return r.height>=height-Math.max(16,r.top)-18&&r.bottom<=innerHeight&&r.top>=0},{height})
   evidence.push({height,rect:await panel.boundingBox(),limit:await panel.evaluate(e=>e.style.getPropertyValue('--admin-log-panel-height'))})
  }
  await p.mouse.move(800,200);await p.mouse.wheel(0,350)
  await p.waitForFunction(()=>{const r=document.querySelector('.admin-log-panel').getBoundingClientRect();return r.bottom<=innerHeight&&r.top>=0})
  const region=p.locator('.admin-log-panel-body');await region.focus()
  for(let i=0;i<12;i++){await p.keyboard.press('PageDown');await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))}
  assert(await region.evaluate(e=>{const text=e.querySelector('.admin-full-content').firstChild,range=document.createRange();range.setStart(text,text.length-20);range.setEnd(text,text.length);const r=range.getBoundingClientRect(),b=e.getBoundingClientRect();return r.top>=b.top&&r.bottom<=Math.min(b.bottom,innerHeight)}),'normal detail remains readable to the final text')
  assert.equal(new URL(p.url()).searchParams.get('selected'),'203');assert.equal(countWrites(s),ending==='success'?1:0)
  await p.screenshot({path:`${out}/height-${ending}-after.png`});await fs.writeFile(`${out}/height-${ending}.json`,JSON.stringify({before,evidence},null,2))
 }finally{console.log('height final',ending,await p.locator('.admin-log-panel').evaluate(e=>({rect:e.getBoundingClientRect().toJSON(),limit:e.style.getPropertyValue('--admin-log-panel-height'),viewport:innerHeight})));await p.screenshot({path:`${out}/height-${ending}-final.png`});await context.close()}
})
const passage=(p,mark)=>p.locator('.admin-action-content .admin-full-content').evaluate((e,mark)=>{
 const text=e.firstChild,start=text.textContent.indexOf(mark),range=document.createRange();range.setStart(text,start);range.setEnd(text,start+mark.length)
 const rect=range.getBoundingClientRect(),panel=e.closest('.admin-log-panel'),container=panel.dataset.docked==='true'?e.closest('.admin-action-content'):e.closest('.admin-log-panel-body'),box=container.getBoundingClientRect()
 return {top:rect.top,bottom:rect.bottom,viewportTop:Math.max(0,box.top),viewportBottom:Math.min(innerHeight,box.bottom),visible:rect.top>=Math.max(0,box.top)&&rect.bottom<=Math.min(innerHeight,box.bottom)}
},mark)
await run('reading passage survives both scroll modes and width reflow',async()=>{
 const {p,context,s}=await setup(1920);let mark
 try{
  s.comments[0].content=Array.from({length:40},(_,i)=>`문단 ${String(i+1).padStart(2,'0')}: 보청기 적응 경험을 기록합니다. 조용한 곳과 사람이 많은 곳에서 다르게 느낀 점을 함께 나눕니다. 🦻\n\n`).join('')
  await p.setViewportSize({width:1920,height:1200});await selectTarget(p,'COMMENT');await p.waitForFunction(()=>document.querySelector('.admin-log-panel').dataset.docked==='true')
  const read=p.locator('.admin-action-content'),box=await read.boundingBox();await p.mouse.click(box.x+60,box.y+70);await p.mouse.wheel(0,650)
  await p.waitForFunction(()=>document.querySelector('.admin-action-content').scrollTop>400)
  for(let i=5;i<=30;i++){const candidate=`문단 ${String(i).padStart(2,'0')}:`;if((await passage(p,candidate)).visible){mark=candidate;break}}
  assert(mark,'a distinct middle passage must be visible after user wheel')
  const evidence=[{mode:'reading',mark,...await passage(p,mark)}];await p.screenshot({path:out+'/passage-before.png'})
  for(const size of [{width:1440,height:650},{width:1920,height:1200}]){
   await p.setViewportSize(size);const docked=size.height===1200
   await p.waitForFunction(v=>document.querySelector('.admin-log-panel').dataset.docked===String(v),docked)
   await p.waitForFunction(mark=>{const e=document.querySelector('.admin-action-content .admin-full-content'),t=e.firstChild,start=t.textContent.indexOf(mark),r=document.createRange();r.setStart(t,start);r.setEnd(t,start+mark.length);const q=r.getBoundingClientRect(),panel=e.closest('.admin-log-panel'),b=(panel.dataset.docked==='true'?e.closest('.admin-action-content'):e.closest('.admin-log-panel-body')).getBoundingClientRect();return q.top>=Math.max(0,b.top)&&q.bottom<=Math.min(innerHeight,b.bottom)},mark)
   evidence.push({size,mode:docked?'reading':'panel',mark,...await passage(p,mark)});await p.screenshot({path:`${out}/passage-${size.height}.png`})
  }
  // A fresh user scroll must win over the restored location.
  const last=await read.evaluate(e=>e.scrollTop),r=await read.boundingBox();await p.mouse.move(r.x+80,r.y+80);await p.mouse.wheel(0,180);await p.waitForFunction(n=>document.querySelector('.admin-action-content').scrollTop>n+80,last)
  assert.equal(countWrites(s),0);await fs.writeFile(out+'/passage.json',JSON.stringify(evidence,null,2))
 }finally{if(mark)await fs.writeFile(out+'/passage-final.json',JSON.stringify({mark,...await passage(p,mark)},null,2));await p.screenshot({path:out+'/passage-final.png'});await context.close()}
})
await run('resize keeps active invalid detail ahead of reading restoration',async()=>{
 const {p,context,s}=await setup(1920)
 try{
  await p.setViewportSize({width:1920,height:1200});await selectTarget(p,'COMMENT');await reason(p).selectOption('OTHER');await p.locator('textarea').fill('   ');await btn(p,'숨김 처리 실행').click()
  const field=p.locator('textarea');assert(await field.evaluate(e=>document.activeElement===e));assert.equal(countWrites(s),0)
  for(const size of [{width:1440,height:650},{width:1920,height:1200}]){
   await p.setViewportSize(size)
   await p.waitForFunction(()=>{const e=document.querySelector('.admin-inline-moderation textarea'),b=e.closest('.admin-log-panel-body').getBoundingClientRect(),r=e.getBoundingClientRect(),error=(e.getAttribute('aria-describedby')??'').split(' ').map(id=>document.getElementById(id)).find(n=>n?.getAttribute('role')==='alert');return document.activeElement===e&&e.getAttribute('aria-invalid')==='true'&&r.top>=b.top&&Math.max(r.bottom,error?.getBoundingClientRect().bottom??0)<=Math.min(innerHeight,b.bottom)})
   assert.equal(await field.inputValue(),'   ')
  }
  await p.screenshot({path:out+'/error-resize.png'});await field.fill('내용을 확인한 처리 사유');await reachActions(p);await p.keyboard.press('Enter');await p.locator('.admin-action-workspace').waitFor({state:'detached'});assert.equal(countWrites(s),1)
 }finally{await p.screenshot({path:out+'/error-resize-final.png'});console.log('invalid detail bounds',await p.evaluate(()=>{const e=document.querySelector('textarea'),b=e?.closest('.admin-log-panel-body');return {field:e?.getBoundingClientRect().toJSON(),body:b?.getBoundingClientRect().toJSON(),active:document.activeElement?.tagName,scroll:scrollY,inner:innerHeight,docked:document.querySelector('.admin-log-panel')?.dataset.docked,error:document.querySelector('.admin-inline-moderation [role=alert]')?.getBoundingClientRect().toJSON()}}));await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
