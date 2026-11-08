import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.SCROLL_ARTIFACT_DIR??'/private/tmp/iyum-panel-scroll/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`선택 게시글 ${id}`,content:`현재 본문 ${id}\n`+'긴 본문 검증 '.repeat(40),contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:8,targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
const preview=p=>p.locator('.admin-preview-post[aria-busy="false"]').waitFor()
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),wait:new Promise(r=>release=r),arrive:()=>arrive(),release:()=>release()}}
async function setup(width=1440,height=1000){
 const context=await browser.newContext({viewport:{width,height}}),p=await context.newPage(),s={member:{...member},calls:[],errors:[],posts:[post(101),post(102,'HIDDEN'),post(103,'DELETED')],logs:[log(201),log(202),log(203,'COMMENT')],detail:null,list:null}
 p.setDefaultTimeout(8000);p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const f=window.fetch;window.__masterParsed=0;window.fetch=async(u,o)=>{const r=await f(u,String(u).includes('/api/v1/admin/')?{...o,signal:undefined}:o);if(String(u).includes('/api/v1/admin/')){const json=r.json.bind(r);r.json=async()=>{const v=await json();setTimeout(()=>window.__masterParsed++,0);return v}}return r}})
 await context.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url()),path=u.pathname,method=r.request().method();s.calls.push({path,search:u.search,method})
  if(method==='OPTIONS')return r.fulfill({status:204,headers:{'access-control-allow-origin':base,'access-control-allow-credentials':'true','access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'content-type,x-csrf-token'}})
  if(path.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:{status:401,json:{code:'AUTHENTICATION_REQUIRED'}})
  if(path.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(path.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  const write=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/(hide|restore)$/)
  if(write&&method==='POST'){const item=s.posts.find(x=>x.postId===Number(write[1]));item.status=write[2]==='hide'?'HIDDEN':'VISIBLE';item.hiddenAt=item.status==='HIDDEN'?stamp:null;return r.fulfill({json:{postId:item.postId,hidden:item.status==='HIDDEN',hiddenAt:item.hiddenAt,changed:true}})}
  if(path==='/api/v1/admin/posts'||path==='/api/v1/admin/operation-logs'){
   const page=Number(u.searchParams.get('page')??0),items=path.endsWith('operation-logs')?s.logs:s.posts.filter(x=>u.searchParams.getAll('statuses').includes(x.status))
   const data={items,page,size:20,totalElements:41,totalPages:3,hasNext:page<2,hasPrevious:page>0};return s.list?s.list(r,u,data):r.fulfill({json:data})
  }
  const detail=path.match(/^\/api\/v1\/admin\/posts\/(\d+)$/)
  if(detail){const id=Number(detail[1]);return s.detail?s.detail(r,id):r.fulfill({json:s.posts.find(x=>x.postId===id)??post(id)})}
  const comments=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/comments$/)
  if(comments)return r.fulfill({json:{postId:Number(comments[1]),postStatus:'VISIBLE',items:[],page:0,size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false}})
  return r.fulfill({status:404,json:{code:'NOT_FOUND'}})
 });return{context,p,s}
}
async function run(name,fn){if(process.env.TEST_FILTER&&!new RegExp(process.env.TEST_FILTER).test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message)}}

const viewport=async locator=>locator.evaluate(e=>{const r=e.getBoundingClientRect();return{top:r.top,bottom:r.bottom,left:r.left,right:r.right,height:r.height,scrollY:window.scrollY,viewportHeight:innerHeight,viewportWidth:innerWidth}})
const visible=r=>r.top>=0&&r.bottom<=r.viewportHeight&&r.left>=0&&r.right<=r.viewportWidth
for(const [width,height] of [[1440,1000],[1920,1000],[1440,500]])for(const kind of ['posts','logs'])await run(`${kind} bottom selection and single panel scroll ${width}x${height}`,async()=>{
 const{p,context,s}=await setup(width,height),path=kind==='posts'?'/admin/posts':'/admin/operation-logs'
 const evidence=[]
 try{
  s.posts=Array.from({length:20},(_,i)=>({...post(101+i),content:('길고 긴 본문입니다.\n').repeat(160)+`본문 마지막 ${101+i}`}))
  s.logs=Array.from({length:20},(_,i)=>({...log(201+i),reason:('긴 처리 사유입니다.\n').repeat(70)+`사유 마지막 ${201+i}`,reasonDetail:null}))
  await p.goto(base+path);await ready(p);await p.evaluate(()=>document.fonts.ready);assert.equal(await p.locator('tbody tr').count(),20)
  const row=p.locator('tbody tr').last(),heading=p.locator('.admin-preview-heading h2')
  await row.evaluate(e=>e.scrollIntoView({block:'center'}));const before=await p.evaluate(()=>scrollY)
  await p.screenshot({path:`${out}/${kind}-${width}-${height}-before.png`})
  await row.getByRole('link').first().click();await preview(p)
  const top=await viewport(heading);evidence.push({stage:'selected',before,...top})
  await p.screenshot({path:`${out}/${kind}-${width}-${height}-selected.png`})
  assert(visible(top),'selected panel heading must be inside viewport');assert(Math.abs(top.scrollY-before)<3,'selection must preserve document scroll')
  const id=kind==='posts'?120:220
  assert(visible(await viewport(p.locator(kind==='posts'?'.admin-preview .admin-post-topline':'.admin-log-fields > div:first-child'))),'selected identity must be visible')
  assert((await p.locator('.admin-preview').innerText()).includes('#'+id))
  const body=p.locator('.admin-preview-body'),close=btn(p,'상세 닫기')
  assert.equal(await body.evaluate(e=>e.scrollTop),0)
  assert(await body.evaluate(e=>e.scrollHeight>e.clientHeight))
  for(const selector of ['.admin-full-content','.admin-reason-text'])for(const element of await p.locator('.admin-preview '+selector).all())assert(await element.evaluate(e=>e.scrollHeight<=e.clientHeight+1),'no nested body/reason scroll containers')
  await body.evaluate(e=>e.scrollTop=e.scrollHeight);await p.evaluate(()=>new Promise(r=>requestAnimationFrame(r)))
  assert(visible(await viewport(close)));assert(Math.abs((await p.evaluate(()=>scrollY))-before)<3)
  const content=p.locator('.admin-preview .admin-full-content')
  // The final text line, not just its scroll container, must be visible.
  const tail=await content.evaluate(e=>{const t=e.lastChild,r=document.createRange();r.setStart(t,Math.max(0,t.textContent.length-10));r.setEnd(t,t.textContent.length);const b=r.getBoundingClientRect();return{top:b.top,bottom:b.bottom,height:innerHeight}})
  assert(tail.top>=0&&tail.bottom<=tail.height)
  if(kind==='posts'){
   const action=btn(p,'게시글 숨김 처리');assert(visible(await viewport(action)));await action.click();await p.getByRole('dialog').waitFor();await p.keyboard.press('Escape');await p.getByRole('dialog').waitFor({state:'detached'})
  }else{
   const reason=p.locator('.admin-log-fields .admin-reason-text').first();await reason.evaluate(e=>e.scrollIntoView({block:'end'}));
   const tail=await reason.evaluate(e=>{const t=e.lastChild,r=document.createRange();r.setStart(t,t.textContent.length-12);r.setEnd(t,t.textContent.length);const b=r.getBoundingClientRect();return{top:b.top,bottom:b.bottom,height:innerHeight}});assert(tail.top>=0&&tail.bottom<=tail.height)
   await body.evaluate(e=>e.scrollTop=e.scrollHeight)
  }
  await p.screenshot({path:`${out}/${kind}-${width}-${height}-bottom.png`});evidence.push({stage:'bottom',panel:await viewport(p.locator('.admin-preview')),body:await body.evaluate(e=>({scrollTop:e.scrollTop,height:e.clientHeight,scrollHeight:e.scrollHeight})),scrollY:await p.evaluate(()=>scrollY)})
  const retained=await body.evaluate(e=>e.scrollTop);await row.getByRole('link').first().click();assert.equal(await body.evaluate(e=>e.scrollTop),retained,'same selection retains reading position')
  const previous=p.locator('tbody tr').nth(18);await previous.getByRole('link').first().click();await preview(p)
  assert.equal(await body.evaluate(e=>e.scrollTop),0);assert(visible(await viewport(heading)));assert.equal(new URL(p.url()).searchParams.get('selected'),String(kind==='posts'?119:219))
  if(kind==='logs')assert((await p.locator('.admin-log-fields').innerText()).includes('게시글 #101'),'different logIds for the same post')
  await body.evaluate(e=>e.scrollTop=0);const detail=p.locator('.admin-preview-links a').first();await detail.focus();assert(visible(await viewport(detail)))
  const beforeClose=await p.evaluate(()=>scrollY);await close.click();assert(await previous.evaluate(e=>e===document.activeElement));assert(Math.abs((await p.evaluate(()=>scrollY))-beforeClose)<3)
  assert.equal(await p.locator('.admin-preview-body').count(),0)
  await btn(p,'다음').click();await ready(p);assert.equal(new URL(p.url()).searchParams.get('selected'),null);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 }catch(e){await p.screenshot({path:`${out}/${kind}-${width}-${height}-failure.png`});throw e}finally{await fs.writeFile(`${out}/${kind}-${width}-${height}-geometry.json`,JSON.stringify(evidence,null,2));await context.close()}
})

for(const kind of ['posts','logs'])await run(`${kind} loading error retry and short panel keep document position`,async()=>{
 const{p,context,s}=await setup(),g=gate(),path=kind==='posts'?'/admin/posts':'/admin/operation-logs'
 try{
  s.posts=Array.from({length:20},(_,i)=>({...post(101+i),content:'짧은 본문'}));s.logs=Array.from({length:20},(_,i)=>({...log(201+i),reason:'짧은 사유',reasonDetail:null}))
  await p.goto(base+path);await ready(p)
  const row=p.locator('tbody tr').last();await row.evaluate(e=>e.scrollIntoView({block:'center'}));const before=await p.evaluate(()=>scrollY)
  s.detail=async r=>{g.arrive();await g.wait;return r.fulfill({status:503,json:{code:'SERVER_ERROR'}})}
  await row.getByRole('link').first().click();await g.arrived
  assert(visible(await viewport(p.locator('.admin-preview-heading'))));assert(Math.abs((await p.evaluate(()=>scrollY))-before)<3)
  await p.screenshot({path:`${out}/${kind}-loading.png`});g.release();await p.locator('.admin-preview [role=alert]').waitFor()
  assert(visible(await viewport(btn(p,'게시글 다시 시도'))));assert(Math.abs((await p.evaluate(()=>scrollY))-before)<3)
  await p.screenshot({path:`${out}/${kind}-error.png`});s.detail=null;await btn(p,'게시글 다시 시도').click();await preview(p)
  const body=p.locator('.admin-preview-body');assert.equal(await body.evaluate(e=>e.scrollTop),0);assert(Math.abs((await p.evaluate(()=>scrollY))-before)<3)
  if(kind==='posts')assert(await body.evaluate(e=>e.scrollHeight<=e.clientHeight+1),'short content does not create a scroll bar')
  const top=await body.evaluate(e=>e.scrollTop);await row.getByRole('link').first().click();assert.equal(await body.evaluate(e=>e.scrollTop),top);assert(Math.abs((await p.evaluate(()=>scrollY))-before)<3)
  await p.screenshot({path:`${out}/${kind}-short.png`})
 }finally{g.release();await context.close()}
})
for(const kind of ['posts','logs'])await run(`${kind} narrow layout has no desktop height cap 320`,async()=>{
 const{p,context,s}=await setup(320,700),path=kind==='posts'?'/admin/posts':'/admin/operation-logs'
 try{
  s.posts=Array.from({length:20},(_,i)=>post(101+i));s.logs=Array.from({length:20},(_,i)=>log(201+i))
  await p.goto(base+path);await ready(p);const row=p.locator('tbody tr').last();await row.getByRole('link').first().click();await preview(p)
  await p.screenshot({path:`${out}/${kind}-320-position.png`});await fs.writeFile(`${out}/${kind}-320-position.json`,JSON.stringify({heading:await viewport(p.locator('.admin-preview-heading')),title:await viewport(p.locator('.admin-preview-heading h2')),panel:await viewport(p.locator('.admin-preview'))},null,2))
  assert(visible(await viewport(p.locator('.admin-preview-heading'))))
  const styles=await p.locator('.admin-preview').evaluate(e=>({position:getComputedStyle(e).position,maxHeight:getComputedStyle(e).maxHeight}))
  assert.equal(styles.position,'static');assert.equal(styles.maxHeight,'none');assert.equal(await p.locator('.admin-preview-body').evaluate(e=>getComputedStyle(e).overflowY),'visible')
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/${kind}-320.png`})
  await btn(p,'상세 닫기').click();assert(await row.evaluate(e=>e===document.activeElement))
 }finally{await context.close()}
})

await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));if(!results.length||results.some(x=>!x.passed))process.exitCode=1
