import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MODAL_ARTIFACT_DIR??'/private/tmp/iyum-operation-summary-review/browser'
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
 const context=await browser.newContext({viewport:{width,height:1000},timezoneId:'America/Los_Angeles'}),p=await context.newPage(),s={member:{...member},calls:[],errors:[],posts:[post(101),post(102,'HIDDEN'),post(103,'DELETED')],logs:[log(201),log(202),log(203,'COMMENT')],detail:null,list:null,commentDetail:null,commentList:null,write:null,comments:Array.from({length:21},(_,i)=>comment(900+i))}
 p.setDefaultTimeout(8000);p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const f=window.fetch;window.__masterParsed=0;window.fetch=async(u,o)=>{const r=await f(u,String(u).includes('/api/v1/admin/')?{...o,signal:undefined}:o);if(String(u).includes('/api/v1/admin/')&&r.status!==200)setTimeout(()=>window.__masterParsed++,0);if(String(u).includes('/api/v1/admin/')){const json=r.json.bind(r);r.json=async()=>{const v=await json();setTimeout(()=>window.__masterParsed++,0);return v}}return r}})
 await context.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url()),path=u.pathname,method=r.request().method();s.calls.push({path,search:u.search,method,payload:method==='POST'?r.request().postDataJSON():null})
  if(method==='OPTIONS')return r.fulfill({status:204,headers:{'access-control-allow-origin':base,'access-control-allow-credentials':'true','access-control-allow-methods':'GET, POST, OPTIONS','access-control-allow-headers':'content-type,x-csrf-token'}})
  if(path==='/api/v1/admin/operation-logs/summary')return s.summary?s.summary(r):r.fulfill({json:summary()})
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

const summary=(n=2000)=>({queriedAt:'2026-11-17T15:00:00Z',baseDate:'2026-11-18',counts:{totalOperationLogCount:n,todayOperationLogCount:7,todayHideCount:3,todayRestoreCount:5}})
const reads=s=>s.calls.filter(c=>c.path==='/api/v1/admin/operation-logs/summary')
const counts=p=>p.locator('.operation-summary .dashboard-count dd:first-of-type')
const summaryReady=p=>p.locator('.operation-summary [aria-busy="false"]').waitFor()
const select=async p=>{await p.locator('[data-log-id="201"]').click();await p.locator('.admin-log-panel [aria-busy="false"]').first().waitFor()}
const frames=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
for(const width of [1920,1440,390,320])await run(`운영 기록 요약 매핑과 날짜·긴 숫자·키보드 ${width}`,async()=>{
 const{p,s,context}=await setup(width);try{
 await p.goto(base+'/admin/operation-logs');await ready(p);await summaryReady(p)
 assert.deepEqual(await p.locator('.operation-summary dt').allTextContents(),['전체 운영 기록','오늘 운영 기록','오늘 숨김','오늘 숨김 해제'])
 assert.deepEqual(await counts(p).allTextContents(),['2,000건','7건','3건','5건'])
 assert.equal(await p.locator('.operation-summary time').first().textContent(),'2026.11.18')
 assert.match(await p.locator('.operation-summary time').last().textContent(),/00:00:00.*한국 시간/)
 assert.equal(await p.locator('.operation-summary a').count(),0)
 await p.screenshot({path:`${out}/normal-${width}.png`,fullPage:true})
 s.summary=r=>r.fulfill({json:summary(Number.MAX_SAFE_INTEGER)})
 await btn(p,'요약 새로고침').focus();await p.keyboard.press('Enter');await summaryReady(p)
 assert.equal(await counts(p).first().textContent(),'9,007,199,254,740,991건')
 assert(await btn(p,'요약 새로고침').evaluate(e=>e===document.activeElement))
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 assert(await counts(p).evaluateAll(nodes=>nodes.every(n=>{const range=document.createRange();range.selectNodeContents(n);const b=n.closest('.dashboard-count').getBoundingClientRect();return [...range.getClientRects()].every(r=>r.left>=b.left&&r.right<=b.right)})))
 await p.screenshot({path:`${out}/long-${width}.png`,fullPage:true});assert.deepEqual(s.errors,[])
 }finally{await context.close()}
})
await run('최초 로딩과 실패는 0이 아니며 목록은 사용 가능하고 재시도로 실제 0을 표시한다',async()=>{
 const{p,s,context}=await setup();try{const g=gate();s.summary=async r=>{g.arrive();await g.wait;return r.fulfill({status:500,json:{code:'ERROR'}})}
 await p.goto(base+'/admin/operation-logs');await ready(p);await g.arrived
 assert.deepEqual(await counts(p).allTextContents(),['—','—','—','—'])
 await btn(p,'요약 새로고침').focus();await p.keyboard.press('Enter');await frames(p);assert.equal(reads(s).length,1)
 await p.screenshot({path:out+'/loading.png',fullPage:true});g.release();await summaryReady(p);await p.locator('.operation-summary [role="alert"]').waitFor()
 await p.screenshot({path:out+'/initial-error.png',fullPage:true})
 s.summary=r=>r.fulfill({json:{...summary(0),counts:{totalOperationLogCount:0,todayOperationLogCount:0,todayHideCount:0,todayRestoreCount:0}}})
 await btn(p,'요약 다시 조회').click();await summaryReady(p);assert.deepEqual(await counts(p).allTextContents(),['0건','0건','0건','0건'])
 }finally{await context.close()}
})
await run('필터·정렬·페이지는 요약을 재조회하지 않고 수동 갱신은 패널과 사유를 보존한다',async()=>{
 const{p,s,context}=await setup();try{
 await p.goto(base+'/admin/operation-logs');await ready(p);await summaryReady(p)
 await p.locator('#admin-log-target').selectOption('POST');await p.locator('#admin-log-action').selectOption('HIDE');await p.locator('#admin-log-reason').selectOption('LEGACY');await btn(p,'검색').click();await ready(p)
 await p.locator('#admin-log-sort').selectOption('OLDEST');await ready(p);await btn(p,'다음').click();await ready(p)
 assert.equal(reads(s).length,1);await select(p);await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.locator('.admin-inline-moderation textarea').fill('보존할 사유')
 const url=p.url();s.summary=r=>r.fulfill({json:summary(42)})
 await btn(p,'요약 새로고침').scrollIntoViewIfNeeded();await frames(p)
 assert(await btn(p,'요약 새로고침').evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight}),'조치 입력 중에도 요약 영역으로 스크롤할 수 있다')
 await btn(p,'요약 새로고침').click();await summaryReady(p)
 assert.equal(await counts(p).first().textContent(),'42건');assert.equal(p.url(),url);assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'보존할 사유');assert.equal(s.calls.filter(c=>c.method==='POST').length,0)
 }finally{await context.close()}
})
for(const action of ['hide','restore'])await run(`운영 패널 ${action} 성공은 요약을 재조회하고 실패한 요약은 GET만 재시도한다`,async()=>{
 const{p,s,context}=await setup();try{
 s.posts[0]=post(101,action==='hide'?'VISIBLE':'HIDDEN');await p.goto(base+'/admin/operation-logs');await ready(p);await summaryReady(p);await select(p)
 const label=action==='hide'?'숨김 처리':'숨김 해제';await btn(p,`게시글 ${label}`).click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption(action==='hide'?'SPAM_ADVERTISING':'NO_VIOLATION')
 s.summary=r=>r.fulfill({status:500,json:{code:'ERROR'}});await btn(p,`${label} 실행`).click();await p.locator('.operation-summary [role="alert"]').waitFor();await ready(p)
 assert.equal(reads(s).length,2);assert.equal(await counts(p).first().textContent(),'2,000건');assert(p.url().includes('selected=201'));assert.equal(s.calls.filter(c=>c.method==='POST').length,1)
 await p.screenshot({path:out+`/action-${action}-summary-error.png`,fullPage:true})
 s.summary=r=>r.fulfill({json:summary(47)});await btn(p,'요약 다시 조회').click();await summaryReady(p);assert.equal(await counts(p).first().textContent(),'47건');assert.equal(s.calls.filter(c=>c.method==='POST').length,1)
 }finally{await context.close()}
})
await run('조치 실패는 요약 갱신과 POST 재전송 없이 사유를 보존한다',async()=>{
 const{p,s,context}=await setup();try{await p.goto(base+'/admin/operation-logs');await ready(p);await summaryReady(p);await select(p);await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('OTHER');await p.locator('.admin-inline-moderation textarea').fill('유효한 사유');s.write=r=>r.fulfill({status:400,json:{code:'INVALID_REASON',message:'사유를 확인해주세요.'}});await btn(p,'숨김 처리 실행').click();await p.locator('.admin-inline-moderation [role="alert"]').waitFor();assert.equal(reads(s).length,1);assert.equal(s.calls.filter(c=>c.method==='POST').length,1);assert.equal(await p.locator('.admin-inline-moderation textarea').inputValue(),'유효한 사유')
 }finally{await context.close()}
})
await run('조치 이전 지연 요약 응답은 조치 이후 최신 수치를 덮지 않는다',async()=>{
 const{p,s,context}=await setup();try{const g=gate();s.summary=async r=>{g.arrive();await g.wait;return r.fulfill({json:summary(1)})};await p.goto(base+'/admin/operation-logs');await ready(p);await g.arrived;await select(p);await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)',{exact:true}).selectOption('SPAM_ADVERTISING');s.summary=r=>r.fulfill({json:summary(55)});await btn(p,'숨김 처리 실행').click();await summaryReady(p);assert.equal(await counts(p).first().textContent(),'55건');const before=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,before);await frames(p);assert.equal(await counts(p).first().textContent(),'55건')
 }finally{await context.close()}
})
for(const status of [401,403])await run(`요약 ${status}는 이전 관리자 정보를 제거한다`,async()=>{
 const{p,s,context}=await setup();try{await p.goto(base+'/admin/operation-logs');await ready(p);await summaryReady(p);s.summary=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ACCESS_DENIED'}});await btn(p,'요약 새로고침').click();await p.locator('.operation-summary').waitFor({state:'detached'});assert.equal(await p.locator('[data-log-id]').count(),0)
 }finally{await context.close()}
})
await run('화면 이탈 후 늦은 권한 오류는 새 화면에 영향을 주지 않는다',async()=>{
 const{p,s,context}=await setup();try{const g=gate();s.summary=async r=>{g.arrive();await g.wait;return r.fulfill({status:403,json:{code:'ACCESS_DENIED'}})};await p.goto(base+'/admin/operation-logs');await ready(p);await g.arrived;await p.locator('.admin-sidebar').getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(u=>u.pathname==='/admin/posts');const before=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,before);await frames(p);assert.equal(await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).count(),0);await p.getByRole('heading',{name:'게시글 관리',exact:true}).waitFor()
 }finally{await context.close()}
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));console.log(`${results.filter(r=>r.passed).length}/${results.length} passed`);if(results.some(r=>!r.passed))process.exitCode=1
