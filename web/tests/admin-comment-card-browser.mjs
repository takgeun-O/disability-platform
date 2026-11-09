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

const dialog=p=>p.getByRole('dialog')
const openPost=async(p,id=101)=>{await p.goto(base+'/admin/posts');await ready(p);await select(p,id);await preview(p)}
const openLog=async(p,id=203)=>{await p.goto(base+'/admin/operation-logs');await ready(p);await select(p,id,true);await singleReady(p)}
const noOverflow=async p=>assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&Array.from(document.querySelectorAll('.admin-content-dialog,.admin-dialog-body')).every(e=>e.scrollWidth<=e.clientWidth+1)))
const close=async p=>{await btn(p,'닫기').click();await dialog(p).waitFor({state:'detached'})}
for(const action of ['hide','restore'])for(const failure of [503,'network'])await run(`card stale ${action} ${failure}`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  s.comments[0].status=action==='hide'?'VISIBLE':'HIDDEN'
  await openLog(p);await p.getByRole('button',{name:/^(현재 댓글 관리|이 댓글 관리)$/}).click();await singleReady(p)
  s.commentDetail=r=>failure==='network'?r.abort('failed'):r.fulfill({status:503,json:{code:'INTERNAL_SERVER_ERROR'}})
  await p.getByRole('button',{name:action==='hide'?/^댓글(?: #900)? 숨김 처리$/:/^댓글(?: #900)? 숨김 해제$/}).click()
  await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption(action==='hide'?'SPAM_ADVERTISING':'NO_VIOLATION')
  await btn(p,action==='hide'?'숨김 처리 실행':'숨김 해제 실행').click();await btn(p,'댓글 정보 다시 시도').waitFor()
  await p.screenshot({path:`${out}/${action}-${failure}-failed-get.png`})
  const stale=p.getByRole('button',{name:action==='hide'?/^댓글(?: #900)? 숨김 처리$/:/^댓글(?: #900)? 숨김 해제$/})
  assert.equal(await stale.isDisabled(),true,'stale action must stay disabled after successful POST and failed GET')
  assert.equal(await p.getByText('최신 상태를 확인하지 못해 이전 내용을 표시합니다.',{exact:true}).count(),1);assert((await p.locator('.admin-preview-comment').getByRole('alert').innerText()).includes('조치는 완료됐지만'))
  s.commentDetail=async r=>{g.arrive();await g.wait;return r.fulfill({json:{...s.comments[0],postStatus:'VISIBLE'}})}
  await btn(p,'댓글 정보 다시 시도').click();await g.arrived;assert(await stale.isDisabled())
  await stale.evaluate(b=>b.dispatchEvent(new MouseEvent('click',{bubbles:true})));assert.equal(await p.getByLabel('처리 사유 (필수)',{exact:true}).count(),0)
  g.release();await singleReady(p)
  assert(await p.getByRole('button',{name:action==='hide'?/^댓글(?: #900)? 숨김 해제$/:/^댓글(?: #900)? 숨김 처리$/}).isEnabled())
  assert.equal(s.calls.filter(c=>c.method==='POST'&&/\/(hide|restore)$/.test(c.path)).length,1)
 }finally{g.release();await context.close()}
})
for(const width of [1440,320])await run(`cards body actions metadata and record navigation ${width}`,async()=>{
 const{p,context,s}=await setup(width)
 try{
  s.comments[1].author={...author,nickname:'긴작성자이름'.repeat(18)};s.comments[0].content='짧은 댓글 🦻\n두 번째 줄';s.comments[1].status='HIDDEN';s.comments[1].hiddenAt=stamp;s.comments[1].content='긴문자열'.repeat(100)+'\n마지막 줄 🦻';s.comments[2].status='DELETED';s.comments[2].deletedAt=stamp
  await openPost(p);assert.equal(await p.locator('.admin-preview-post > .admin-comment-body').getByText('게시글 본문',{exact:true}).count(),1);await p.screenshot({path:`${out}/post-body-${width}.png`});const dates=p.locator('.admin-preview-post > details');assert.equal(await dates.locator('summary').innerText(),'수정·처리 시각');assert.equal(await dates.locator('dt').filter({hasText:'작성'}).count(),0);assert.equal(await dates.locator('dt').filter({hasText:'본문 수정'}).count(),0);assert.equal(await dates.locator('dt').filter({hasText:'최종 변경'}).count(),1);await btn(p,'댓글 관리로 이동').click();await commentReady(p)
  const card=p.locator('[data-admin-comment-id="900"]'),hidden=p.locator('[data-admin-comment-id="901"]'),deleted=p.locator('[data-admin-comment-id="902"]')
  assert.equal(await card.getByText('댓글 본문',{exact:true}).count(),1);assert.equal(await card.locator('.admin-full-content').innerText(),s.comments[0].content)
  assert(await card.getByRole('button',{name:'댓글 #900 숨김 처리',exact:true}).isEnabled());assert(await hidden.getByRole('button',{name:'댓글 #901 숨김 해제',exact:true}).isEnabled())
  assert.equal(await deleted.getByText('삭제됨 · 변경 불가',{exact:true}).count(),1);assert.equal(await deleted.getByRole('button').count(),0)
  await card.scrollIntoViewIfNeeded();await noOverflow(p)
  const geometry=await card.evaluate(e=>{const body=e.querySelector('.admin-comment-body').getBoundingClientRect(),button=e.querySelector('button').getBoundingClientRect(),full=e.querySelector('.admin-full-content');return{body:body.toJSON(),button:button.toJSON(),nestedScroll:full.scrollHeight>full.clientHeight+1}})
  for(const item of [card,hidden,deleted]){const metrics=await item.evaluate(e=>{const b=e.querySelector('.admin-comment-body').getBoundingClientRect(),r=e.getBoundingClientRect(),style=getComputedStyle(e);return{width:b.width,expected:e.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight),left:b.left-r.left}});assert(Math.abs(metrics.width-metrics.expected)<2,JSON.stringify(metrics))}
  await card.getByRole('button',{name:'댓글 #900 숨김 처리',exact:true}).focus();await p.keyboard.press('Tab');assert(await card.locator('summary').evaluate(e=>e===document.activeElement));
  assert.equal(geometry.nestedScroll,false);if(width===320)assert(geometry.button.top>=geometry.body.bottom);else assert(geometry.button.top<geometry.body.top)
  await fs.writeFile(`${out}/card-geometry-${width}.json`,JSON.stringify(geometry,null,2));await p.screenshot({path:`${out}/cards-${width}.png`})
  await card.locator('summary').focus();await p.keyboard.press('Enter');assert(await card.getByText('기록 없음',{exact:true}).isVisible());await p.screenshot({path:`${out}/times-${width}.png`})
  await card.getByRole('button',{name:'댓글 #900 숨김 처리',exact:true}).click();await p.locator('.admin-action-form').getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.locator('.admin-action-form').getByLabel('상세 사유 (필수)',{exact:true}).fill('검증용 사유')
  await noOverflow(p);await p.screenshot({path:`${out}/reason-${width}.png`});await btn(p,'취소').click();await btn(p,'버리고 이동').click();assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,0)
  await hidden.locator('summary').scrollIntoViewIfNeeded();assert.equal(await hidden.locator('.admin-full-content').innerText(),s.comments[1].content);assert(await hidden.locator('.admin-full-content').evaluate(e=>e.scrollHeight<=e.clientHeight+1));await p.screenshot({path:`${out}/long-hidden-${width}.png`});await deleted.scrollIntoViewIfNeeded();await p.screenshot({path:`${out}/deleted-${width}.png`});await noOverflow(p)
  await close(p);assert.equal(await selectRow(p,'게시글 #101 미리보기').evaluate(e=>e===document.activeElement),true);await openLog(p);assert.equal(await dialog(p).getByRole('heading',{name:'당시 운영 조치',exact:true}).count(),1);assert.equal(await dialog(p).getByRole('heading',{name:'현재 댓글',exact:true}).count(),1)
  assert.equal(await dialog(p).getByText('조회한 현재 내용이며 조치 당시와 다를 수 있습니다.',{exact:true}).count(),1)
  assert.equal(await p.locator('.admin-comment-card').getByRole('button',{name:'이 댓글 관리',exact:true}).count(),1)
  assert.equal(await btn(p,'이 댓글 관리').evaluate(e=>getComputedStyle(e).backgroundColor),'rgb(0, 125, 149)');assert.notEqual(await btn(p,'소속 게시글 보기').evaluate(e=>getComputedStyle(e).backgroundColor),'rgb(0, 125, 149)');await noOverflow(p);await p.screenshot({path:`${out}/record-comment-${width}.png`})
  await btn(p,'소속 게시글 보기').click();await preview(p);assert((await dialog(p).innerText()).includes('게시글 #101 관리'));await btn(p,'운영 기록으로 돌아가기').click();await singleReady(p)
  await btn(p,'이 댓글 관리').click();await singleReady(p);await dialog(p).getByRole('heading',{name:'댓글 #900 관리',exact:true}).waitFor();await p.screenshot({path:`${out}/manage-comment-${width}.png`});await close(p)
  await select(p,201,true);await preview(p);await dialog(p).getByRole('heading',{name:'현재 게시글',exact:true}).waitFor();assert((await dialog(p).innerText()).includes('기존 자유입력 사유'));assert.equal(await p.locator('.admin-reason-text').first().innerText(),s.logs[0].reason)
  await btn(p,'이 게시글 관리').waitFor();assert.equal(await btn(p,'이 게시글 관리').evaluate(e=>getComputedStyle(e).backgroundColor),'rgb(0, 125, 149)');await noOverflow(p);await p.screenshot({path:`${out}/record-post-${width}.png`})
  assert.deepEqual(s.errors,[])
 }finally{await context.close()}
})
for(const status of [401,403,404])await run(`card refresh ${status} removes stale content`,async()=>{
 const{p,context,s}=await setup()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await singleReady(p)
  s.commentDetail=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':status===403?'ADMIN_REQUIRED':'COMMENT_NOT_FOUND'}})
  await btn(p,'댓글 #900 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click()
  if(status===404){await p.getByText('현재 댓글 정보를 찾을 수 없습니다.',{exact:false}).waitFor();assert.equal(await p.locator('.admin-comment-card').count(),0)}
  else{await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await dialog(p).count(),0)}
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})

await run('card latest retry applies deleted parent restriction',async()=>{
 const{p,context,s}=await setup()
 try{
  await openLog(p);await btn(p,'이 댓글 관리').click();await singleReady(p);s.commentDetail=r=>r.fulfill({status:503,json:{code:'INTERNAL_SERVER_ERROR'}})
  await btn(p,'댓글 #900 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await btn(p,'댓글 정보 다시 시도').waitFor()
  s.commentDetail=r=>r.fulfill({json:{...s.comments[0],postStatus:'DELETED'}});await btn(p,'댓글 정보 다시 시도').click();await singleReady(p)
  assert.equal(await p.getByText('소속 게시글 삭제 · 변경 불가',{exact:true}).count(),1);assert.equal(await p.getByRole('button',{name:/댓글 #900 숨김/}).count(),0)
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,1)
 }finally{await context.close()}
})
await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await browser.close();console.log(`${results.filter(x=>x.passed).length}/${results.length} passed`);if(results.some(x=>!x.passed))process.exitCode=1
