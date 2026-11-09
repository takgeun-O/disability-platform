import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-admin-modal/browser'
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

const select=async(p,id,logs=false)=>selectRow(p,logs?`기록 #${id} 상세`:`게시글 #${id} 미리보기`).locator('.admin-badge').click()

const dialog=p=>p.getByRole('dialog')
const openPost=async(p,id=101)=>{await p.goto(base+'/admin/posts');await ready(p);await select(p,id);await preview(p)}
const close=async p=>{await btn(p,'닫기').click();await dialog(p).waitFor({state:'detached'})}


const trace=async(p,steps,label)=>steps.push(await p.evaluate(label=>({label,url:location.pathname+location.search,index:history.state?.__iyumNavigationIndex,flow:history.state?.adminModalFlow??null,historyLength:history.length,modal:!!document.querySelector('.admin-content-dialog'),focus:document.activeElement?.id}),label))
const postList=async p=>{await p.waitForURL('**/admin/posts?**');await p.locator('[data-admin-list-heading]').filter({hasText:'게시글 관리'}).waitFor();await ready(p)}
const roundtrip=async(p,steps)=>{await p.getByRole('link',{name:'원문 보기',exact:true}).click();await p.waitForURL('**/community/posts/101');if(steps)await trace(p,steps,'public');await p.goBack();await preview(p)}
for(const scenario of ['prior-log','no-prior','conditions','repeated','reload','direct','missing-row'])await run(`public return close ${scenario}`,async()=>{
 const{p,context,s}=await setup(),steps=[]
 try{
  if(['prior-log','direct'].includes(scenario)){
   await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,201,true);await preview(p);await trace(p,steps,'log selected')
   if(scenario==='prior-log'){await p.locator('.admin-sidebar').getByRole('link',{name:'게시글 관리',exact:true}).click();await postList(p)}
  }
  if(scenario==='direct'){await p.goto(base+'/admin/posts?statuses=VISIBLE&page=1&sort=OLDEST&selected=101');await preview(p)}
  else{
   if(scenario!=='prior-log')await p.goto(base+'/admin/posts'+(scenario==='conditions'?'?statuses=VISIBLE&statuses=HIDDEN&keyword=보청기&startDate=2026-10-01&endDate=2026-11-10&sort=OLDEST&page=1':''))
   await postList(p);await trace(p,steps,'post list');await select(p,101);await preview(p)
  }
  const selected=p.url(),expected=new URL(selected);expected.searchParams.delete('selected');await trace(p,steps,'post modal')
  if(scenario!=='direct')for(let i=0;i<(scenario==='repeated'?3:1);i++){await roundtrip(p,steps);assert.equal(p.url(),selected);await trace(p,steps,`back modal ${i}`)}
  if(scenario==='reload'){await p.reload();await preview(p);await trace(p,steps,'reload')}
  if(scenario==='missing-row'){s.posts=s.posts.filter(x=>x.postId!==101);await p.reload();await preview(p);await ready(p);assert.equal(await p.locator('#post-select-101').count(),0)}
  await p.screenshot({path:`${out}/${scenario}-before-close.png`});const entry=await p.evaluate(()=>({index:history.state.__iyumNavigationIndex,length:history.length}))
  await close(p);await postList(p);await trace(p,steps,'closed');await p.screenshot({path:`${out}/${scenario}-closed.png`})
  assert.equal(p.url(),expected.href,'close stays on current list conditions')
  await p.waitForFunction(missing=>missing?document.activeElement?.hasAttribute('data-admin-list-heading'):document.activeElement?.id==='post-select-101',scenario==='missing-row')
  assert.equal(await p.evaluate(()=>history.length),entry.length,'close creates no new entry')
  assert.equal(await p.evaluate(()=>history.state.__iyumNavigationIndex),entry.index,'explicit close replaces current entry; it must not traverse history')
  if(scenario==='conditions'){
   const query=new URLSearchParams(s.calls.filter(x=>x.path==='/api/v1/admin/posts').at(-1).search)
   for(const key of ['keyword','startDate','endDate','sort','page'])assert.equal(query.get(key),expected.searchParams.get(key))
   assert.deepEqual(query.getAll('statuses'),expected.searchParams.getAll('statuses'))
   assert.equal(await p.getByLabel('게시글 제목 검색',{exact:true}).inputValue(),'보청기')
   assert.equal(await p.getByLabel('작성 시작일',{exact:true}).inputValue(),'2026-10-01')
   assert.equal(await p.getByLabel('작성 종료일',{exact:true}).inputValue(),'2026-11-10')
   assert.equal(await p.getByLabel('작성 시각 정렬',{exact:true}).inputValue(),'OLDEST')
   assert.equal(await p.getByLabel('페이지 바로 이동',{exact:true}).inputValue(),'2')
   assert.equal(await p.getByRole('group',{name:'게시글 상태',exact:true}).locator('input:checked').count(),2)
  }
  if(scenario==='prior-log'){
   await p.goBack();await postList(p);assert.equal(await dialog(p).count(),0);await trace(p,steps,'back after close')
   await p.goForward();await postList(p);assert.equal(p.url(),expected.href);assert.equal(await dialog(p).count(),0);await trace(p,steps,'forward after close')
   await p.goForward();await p.waitForURL('**/community/posts/101');if(steps)await trace(p,steps,'public');await p.goBack();await postList(p);assert.equal(await dialog(p).count(),0)
  }
 }finally{await fs.writeFile(`${out}/${scenario}-trace.json`,JSON.stringify(steps,null,2));await context.close()}
})
for(const action of ['close','back'])await run(`dirty ${action} cancellation and approval after public return`,async()=>{
 const{p,context,s}=await setup()
 try{
  await openPost(p);await roundtrip(p);const selected=p.url()
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.getByLabel('상세 사유 (필수)',{exact:true}).fill('검토 중인 사유')
  const attempt=async()=>{if(action==='close')await btn(p,'닫기').click();else await p.goBack();await p.getByRole('alert',{name:'입력 유실 확인'}).waitFor()}
  await attempt();await btn(p,'계속 작성').click();assert.equal(p.url(),selected);assert.equal(await p.getByLabel('상세 사유 (필수)',{exact:true}).inputValue(),'검토 중인 사유')
  await attempt();await btn(p,'버리고 이동').click();await dialog(p).waitFor({state:'detached'});await postList(p);assert(!new URL(p.url()).searchParams.has('selected'));assert.equal(s.calls.filter(x=>x.method==='POST'&&/hide|restore/.test(x.path)).length,0)
 }finally{await context.close()}
})

for(const width of [1440,320])await run(`long list public return restores row scroll ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.posts=Array.from({length:20},(_,i)=>post(101+i));await p.goto(base+'/admin/posts');await postList(p)
  await p.locator('#post-select-120').scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY);assert(y>100)
  await select(p,120);await preview(p);await p.getByRole('link',{name:'원문 보기',exact:true}).click();await p.waitForURL('**/community/posts/120');await p.goBack();await preview(p)
  await close(p);await postList(p);await p.waitForFunction(()=>document.activeElement?.id==='post-select-120')
  assert(Math.abs(await p.evaluate(()=>scrollY)-y)<2,'original list scroll is restored without focusing to top')
  await p.screenshot({path:`${out}/long-list-return-${width}.png`})
 }finally{await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
