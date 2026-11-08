const openDates=async p=>{if(!await p.locator('.admin-date-range').evaluate(e=>e.open))await p.locator('.admin-date-range summary').click()}
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.MASTER_ARTIFACT_DIR??'/private/tmp/iyum-master-detail/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T03:04:05Z',member={memberId:7,email:'mock@example.test',nickname:'검증 관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증 작성자',withdrawn:false}
const post=(id,status='VISIBLE')=>({postId:id,type:'GENERAL',topic:'DAILY',title:`선택 게시글 ${id}`,content:`현재 본문 ${id}\n`+'긴 본문 검증 '.repeat(40),contentPreview:'미리보기',author,status,createdAt:stamp,updatedAt:stamp,hiddenAt:status==='HIDDEN'?stamp:null,deletedAt:status==='DELETED'?stamp:null})
const log=(id,targetType='POST')=>({logId:id,actorMemberId:8,targetType,targetId:targetType==='POST'?101:900,action:'HIDE',reasonType:id===201?'LEGACY':'OTHER',reasonDetail:id===201?null:`상세 사유 ${id}`,reason:`저장 원문 ${id}\n`+'긴 사유 '.repeat(30),createdAt:stamp})
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const selectRow=(p,name)=>p.getByRole('row',{name,exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
const preview=p=>p.locator('.admin-preview-post[aria-busy="false"]').waitFor()
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),wait:new Promise(r=>release=r),arrive:()=>arrive(),release:()=>release()}}
async function setup(width=1440){
 const context=await browser.newContext({viewport:{width,height:1000}}),p=await context.newPage(),s={member:{...member},calls:[],errors:[],posts:[post(101),post(102,'HIDDEN'),post(103,'DELETED')],logs:[log(201),log(202),log(203,'COMMENT')],detail:null,list:null}
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
const details=s=>s.calls.filter(c=>/^\/api\/v1\/admin\/posts\/\d+$/.test(c.path))
for(const width of [1440,320])for(const kind of ['posts','logs'])await run(`${kind} selection, navigation, filters and layout ${width}`,async()=>{
 const{p,context,s}=await setup(width);const path=kind==='posts'?'/admin/posts':'/admin/operation-logs',id=kind==='posts'?101:201,prefix=kind==='posts'?'작성':'처리',select=kind==='posts'?`게시글 #${id} 미리보기`:`기록 #${id} 상세`
 try{
  await p.goto(base+path);await ready(p);assert.equal(details(s).length,0)
  await openDates(p);await p.getByLabel(`${prefix} 시작일`,{exact:true}).fill('2026-11-01');await openDates(p);await btn(p,'기간 적용').click();await ready(p)
  await p.getByLabel(`${prefix} 시각 정렬`,{exact:true}).selectOption('OLDEST');await ready(p);await btn(p,'다음').click();await ready(p)
  const reads=s.calls.filter(c=>c.path==='/api/v1'+path).length
  await selectRow(p,select).focus();await p.keyboard.press('Enter');await preview(p)
  assert.equal(s.calls.filter(c=>c.path==='/api/v1'+path).length,reads);assert.equal(new URL(p.url()).searchParams.get('selected'),String(id));assert.equal(details(s).length,1)
  assert(await p.locator('.admin-preview-heading h2').evaluate(e=>e===document.activeElement));assert.equal(await selectRow(p,select).getAttribute('aria-selected'),'true')
  assert((await p.locator('.admin-preview').innerText()).includes('현재 본문 101'))
  await p.screenshot({path:`${out}/${kind}-${width}-selected.png`,fullPage:true})
  const source=p.url(),rows=await p.locator('tbody').innerText()
  await p.getByRole('link',{name:'상세 관리로 이동',exact:true}).click();await p.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor()
  await p.goBack();await ready(p);await preview(p);assert.equal(p.url(),source);assert.equal(await selectRow(p,select).getAttribute('aria-selected'),'true');assert.equal(await p.locator('tbody').innerText(),rows)
  await p.goForward();await p.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor();await p.getByRole('link',{name:kind==='posts'?'← 목록으로':'← 운영 기록으로',exact:true}).click();await ready(p);await preview(p);assert.equal(p.url(),source)
  await p.reload();await ready(p);await preview(p);assert.equal(await selectRow(p,select).getAttribute('aria-selected'),'true')
  await btn(p,'상세 닫기').click();assert.equal(new URL(p.url()).searchParams.get('selected'),null);assert(await selectRow(p,select).evaluate(e=>e===document.activeElement));assert.equal(await p.getByLabel('페이지 바로 이동',{exact:true}).inputValue(),'2')
  await selectRow(p,select).locator('.admin-badge').click();await preview(p);await p.getByLabel(`${prefix} 시각 정렬`,{exact:true}).selectOption('LATEST');await ready(p);assert.equal(await p.locator('.admin-preview-post').count(),0);assert.equal(new URL(p.url()).searchParams.get('selected'),null)
  await p.evaluate(()=>document.fonts.ready);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(s.errors,[])
  if(kind==='posts'&&width===1440)assert(await p.locator('.admin-table-wrap').evaluate(e=>e.scrollWidth<=e.clientWidth))
  assert(await p.locator('.iyum-footer').isHidden());assert(await p.getByRole('button',{name:width===320?'메뉴 열기':'로그아웃',exact:true}).isVisible())
 }catch(e){await p.screenshot({path:`${out}/${kind}-${width}-failure.png`,fullPage:true});throw e}finally{await fs.writeFile(`${out}/${kind}-${width}-requests.json`,JSON.stringify(s.calls,null,2));await context.close()}
})
await run('rapid selection ignores completed old detail and does not display stale content',async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await p.goto(base+'/admin/posts');await ready(p)
  s.detail=async(r,id)=>{if(id===101){g.arrive();await g.wait}return r.fulfill({json:post(id)})}
  await selectRow(p,'게시글 #101 미리보기').locator('.admin-badge').click();await g.arrived;await selectRow(p,'게시글 #102 미리보기').locator('.admin-badge').click();await preview(p)
  assert((await p.locator('.admin-preview').innerText()).includes('현재 본문 102'));const n=await p.evaluate(()=>window.__masterParsed);g.release();await p.waitForFunction(n=>window.__masterParsed>n,n);await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
  assert(!(await p.locator('.admin-preview').innerText()).includes('현재 본문 101'));assert.equal(new URL(p.url()).searchParams.get('selected'),'102')
 }finally{g.release();await context.close()}
})
await run('log identity, LEGACY, target failure, retry and comment has no fabricated links',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/operation-logs');await ready(p);s.detail=r=>r.fulfill({status:404,json:{code:'POST_NOT_FOUND'}})
  await selectRow(p,'기록 #201 상세').locator('.admin-badge').click();await p.locator('.admin-preview [role=alert]').waitFor();assert((await p.locator('.admin-log-fields').innerText()).includes('저장 원문 201'));assert((await p.locator('.admin-log-fields').innerText()).includes('기존 자유입력 사유'))
  s.detail=null;await btn(p,'게시글 다시 시도').click();await preview(p);assert((await p.locator('.admin-preview').innerText()).includes('현재 본문 101'))
  await selectRow(p,'기록 #202 상세').locator('.admin-badge').click();await preview(p);assert((await p.locator('.admin-log-fields').innerText()).includes('저장 원문 202'));assert(!(await p.locator('.admin-log-fields').innerText()).includes('저장 원문 201'))
  const n=details(s).length;await selectRow(p,'기록 #203 상세').locator('.admin-badge').click();await p.locator('.admin-preview-comment[aria-busy="false"]').waitFor();assert((await p.locator('.admin-preview-comment').innerText()).includes('현재 댓글 정보를 찾을 수 없습니다.'));assert.equal(s.calls.filter(c=>c.path==='/api/v1/admin/comments/900').length,1);assert.equal(await p.locator('.admin-preview a').count(),0);assert.equal(details(s).length,n);assert((await p.locator('.admin-log-fields').innerText()).includes('댓글 #900'))
 }finally{await context.close()}
})
await run('post retry and hide/restore refresh shared list, then remove filtered selection',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/posts');await ready(p);s.detail=r=>r.fulfill({status:503,json:{code:'INTERNAL_SERVER_ERROR'}})
  await selectRow(p,'게시글 #101 미리보기').locator('.admin-badge').click();await p.locator('.admin-preview [role=alert]').waitFor();s.detail=null;await btn(p,'게시글 다시 시도').click();await preview(p)
  for(const action of ['숨김 처리','숨김 해제']){
   await btn(p,'게시글 '+action).click();await p.getByLabel('처리 사유 (필수)').selectOption(action==='숨김 처리'?'SPAM_ADVERTISING':'NO_VIOLATION');await btn(p,action+' 실행').click();await p.getByRole('dialog').waitFor({state:'detached'});await ready(p);await preview(p)
   assert.equal(await p.locator('tr[data-selected=true] .admin-badge').innerText(),action==='숨김 처리'?'숨김':'정상')
  }
  await p.getByRole('group',{name:'게시글 상태',exact:true}).getByRole('checkbox',{name:'숨김',exact:true}).uncheck();await ready(p);await selectRow(p,'게시글 #101 미리보기').locator('.admin-badge').click();await preview(p)
  await btn(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)').selectOption('SPAM_ADVERTISING');await btn(p,'숨김 처리 실행').click();await p.getByRole('dialog').waitFor({state:'detached'});await ready(p)
  assert.equal(await selectRow(p,'게시글 #101 미리보기').count(),0);assert.equal(await p.locator('.admin-preview-post').count(),0);assert.equal(new URL(p.url()).searchParams.get('selected'),null)
  assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.endsWith('/hide')).length,2)
 }finally{await context.close()}
})
for(const kind of ['posts','logs'])for(const status of [401,403])await run(`${kind} detail ${status} removes all administrator data`,async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+(kind==='posts'?'/admin/posts':'/admin/operation-logs'));await ready(p)
  s.detail=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ACCESS_DENIED'}})
  await selectRow(p,kind==='posts'?'게시글 #101 미리보기':'기록 #201 상세').locator('.admin-badge').click();await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor()
  assert.equal(await p.locator('tbody').count(),0);assert.equal(await p.locator('.admin-preview').count(),0)
 }finally{await context.close()}
})
await run('invalid or missing selection is not fetched; public shell remains unchanged',async()=>{
 const{p,context,s}=await setup()
 try{
  await p.goto(base+'/admin/posts?selected=999');await ready(p);assert.equal(details(s).length,0);assert.equal(new URL(p.url()).searchParams.get('selected'),null)
  await p.getByRole('link',{name:'서비스로 돌아가기',exact:true}).click();await p.waitForURL(base+'/');assert(await p.locator('.iyum-footer').isVisible());assert(await p.getByRole('navigation',{name:'주 메뉴',exact:true}).isVisible())
 }finally{await context.close()}
})
for(const lateStatus of [200,401])await run(`late selected detail ${lateStatus} after logout cannot restore data`,async()=>{
 const{p,context,s}=await setup(),g=gate()
 try{
  await p.goto(base+'/admin/posts');await ready(p)
  s.detail=async r=>{g.arrive();await g.wait;return r.fulfill(lateStatus===200?{json:post(101)}:{status:401,json:{code:'AUTHENTICATION_REQUIRED'}})}
  await selectRow(p,'게시글 #101 미리보기').locator('.admin-badge').click();await g.arrived
  await btn(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
  const finished=p.waitForEvent('requestfinished',{predicate:r=>new URL(r.url()).pathname==='/api/v1/admin/posts/101'})
  g.release();await finished;await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
  assert.equal(await p.locator('.admin-preview,tbody').count(),0)
  assert(!(await p.locator('main').innerText()).includes('현재 본문 101'))
 }finally{g.release();await context.close()}
})
await run('hidden and deleted preview preserve restrictions and clear on empty selection',async()=>{
 const{p,context}=await setup()
 try{
  await p.goto(base+'/admin/posts');await ready(p)
  await selectRow(p,'게시글 #102 미리보기').locator('.admin-badge').click();await preview(p);assert(await btn(p,'게시글 숨김 해제').isVisible());assert.equal(await p.locator('.admin-preview').getByRole('link',{name:'서비스에서 보기'}).count(),0)
  await p.screenshot({path:`${out}/post-hidden.png`,fullPage:true})
  await selectRow(p,'게시글 #103 미리보기').locator('.admin-badge').click();await preview(p);assert.equal(await p.locator('.admin-preview .admin-moderation-primary').count(),0);assert.equal(await p.locator('.admin-preview').getByRole('link',{name:'서비스에서 보기'}).count(),0)
  await p.screenshot({path:`${out}/post-deleted.png`,fullPage:true})
  for(const option of ['숨김','삭제'])await p.getByRole('group',{name:'게시글 상태',exact:true}).getByRole('checkbox',{name:option,exact:true}).uncheck()
  await ready(p);await selectRow(p,'게시글 #101 미리보기').locator('.admin-badge').click();await preview(p)
  await p.getByRole('group',{name:'게시글 상태',exact:true}).getByRole('checkbox',{name:'정상',exact:true}).uncheck()
  await p.getByText('표시할 상태를 선택해 주세요.',{exact:true}).waitFor();assert.equal(await p.locator('.admin-preview-post,tbody').count(),0);assert.equal(new URL(p.url()).searchParams.get('selected'),null)
  await p.getByRole('group',{name:'게시글 상태',exact:true}).getByRole('checkbox',{name:'정상',exact:true}).check();await ready(p);assert.equal(await p.locator('.admin-preview-post').count(),0)
 }finally{await context.close()}
})
for(const width of [1440,320])for(const kind of ['posts','logs'])await run(`${kind} row click preserves controls and locks ${width}`,async()=>{
 const{p,context,s}=await setup(width),g=gate(),path=kind==='posts'?'/admin/posts':'/admin/operation-logs',id=kind==='posts'?101:201
 const row=()=>p.locator('tbody tr').first(),select=kind==='posts'?`게시글 #${id} 미리보기`:`기록 #${id} 상세`
 try{
  await p.goto(base+path);await ready(p)
  assert.equal(await p.locator('.admin-select-row').count(),0);assert.equal(await p.getByRole('button',{name:/미리보기|기록 상세/}).count(),0)
  await row().locator('.admin-badge').click({modifiers:['Control']});assert.equal(await p.locator('.admin-preview-post').count(),0)
  await row().locator('.admin-badge').evaluate(e=>{const r=document.createRange();r.selectNodeContents(e);const s=window.getSelection();s.removeAllRanges();s.addRange(r)})
  await row().locator('.admin-badge').dispatchEvent('click',{button:0});assert.equal(await p.locator('.admin-preview-post').count(),0)
  await p.evaluate(()=>window.getSelection().removeAllRanges())
  await row().locator('.admin-badge').click();await preview(p);assert.equal(new URL(p.url()).pathname,path);assert.equal(new URL(p.url()).searchParams.get('selected'),String(id));assert.equal(await selectRow(p,select).getAttribute('aria-selected'),'true')
  const count=details(s).length;await row().locator('.admin-badge').click();assert.equal(details(s).length,count)
  await p.screenshot({path:`${out}/${kind}-${width}-row-click.png`,fullPage:true})
  await btn(p,'상세 닫기').click()
  s.list=async(r,u,data)=>{g.arrive();await g.wait;return r.fulfill({json:data})}
  await p.getByLabel(kind==='posts'?'작성 시각 정렬':'처리 시각 정렬',{exact:true}).selectOption('OLDEST');await g.arrived
  await row().locator('.admin-badge').click();assert.equal(await p.locator('.admin-preview-post').count(),0);assert.equal(await selectRow(p,select).getAttribute('data-selectable'),'false')
  g.release();await ready(p);s.list=null
  await selectRow(p,select).focus();await p.keyboard.press('Space');await preview(p);assert.equal(await selectRow(p,select).getAttribute('aria-selected'),'true')
  await btn(p,'상세 닫기').click();await row().getByRole('link').first().click();await preview(p);assert.equal(new URL(p.url()).pathname,path);await p.getByRole('link',{name:'상세 관리로 이동',exact:true}).click();await p.waitForURL(url=>/^\/admin\/posts\/\d+$/.test(url.pathname));await p.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor()
  assert.deepEqual(s.errors,[])
 }finally{g.release();await context.close()}
})

for(const width of [1440,1920,320])for(const kind of ['posts','logs'])await run(`${kind} compact toolbar and title links ${width}`,async()=>{
 const{p,context,s}=await setup(width),path=kind==='posts'?'/admin/posts':'/admin/operation-logs',prefix=kind==='posts'?'작성':'처리'
 try{
  s.posts[0]={...s.posts[0],title:'긴 제목 검증 '.repeat(8),author:{...author,nickname:'긴닉네임'.repeat(10)},content:'공백없는본문'.repeat(200)+'\n줄바꿈 유지'}
  await p.goto(base+path);await ready(p);await p.evaluate(()=>document.fonts.ready)
  const summary=p.locator('.admin-date-range summary'),start=p.getByLabel(`${prefix} 시작일`,{exact:true})
  assert(await start.isHidden());assert((await summary.innerText()).includes('전체 기간'))
  await p.screenshot({path:`${out}/${kind}-${width}-empty.png`,fullPage:true})
  const reads=s.calls.filter(c=>c.path==='/api/v1'+path).length
  await summary.click();await openDates(p);await start.fill('2026-11-02');await btn(p,'기간 설정 닫기').click()
  assert(await summary.evaluate(e=>e===document.activeElement));assert((await summary.innerText()).includes('전체 기간'));assert.equal(s.calls.filter(c=>c.path==='/api/v1'+path).length,reads)
  await summary.click();assert.equal(await start.inputValue(),'2026-11-02');await p.screenshot({path:`${out}/${kind}-${width}-date-open.png`,fullPage:true})
  await p.keyboard.press('Escape');assert(await start.isHidden())
  const link=p.locator('tbody a').first(),href=await link.getAttribute('href')
  await link.evaluate(e=>{const r=document.createRange();r.selectNodeContents(e);const selection=window.getSelection();selection.removeAllRanges();selection.addRange(r)});await link.dispatchEvent('click',{button:0});assert.equal(await p.locator('.admin-preview-post').count(),0);await p.evaluate(()=>window.getSelection().removeAllRanges())
  await link.focus();await p.keyboard.press('Enter');await preview(p);assert.equal(new URL(p.url()).pathname,path);assert.equal(s.calls.filter(c=>c.path==='/api/v1'+path).length,reads)
  await btn(p,'상세 닫기').click();await link.click();await preview(p);assert.equal(s.calls.filter(c=>c.path==='/api/v1'+path).length,reads)
  const popupPromise=context.waitForEvent('page');await link.click({button:'middle'});const popup=await popupPromise;await popup.waitForURL(base+href);await popup.close()
  const modifiedPromise=context.waitForEvent('page');await link.click({modifiers:['Meta']});const modified=await modifiedPromise;await modified.waitForURL(base+href);await modified.close()
  await p.screenshot({path:`${out}/${kind}-${width}-final.png`,fullPage:true})
  const geometry=await p.evaluate(()=>{const rect=s=>{const r=document.querySelector(s).getBoundingClientRect();return{x:r.x,y:r.y,width:r.width,height:r.height}};return{toolbar:rect('.admin-compact-toolbar'),list:rect('.admin-master-list'),panel:rect('.admin-preview'),scrollWidth:document.documentElement.scrollWidth,width:innerWidth}})
  assert(geometry.scrollWidth<=width);if(width>1100){assert(Math.abs(geometry.list.y-geometry.panel.y)<2);assert(geometry.toolbar.height<240);assert(geometry.panel.x>geometry.list.x)}
  await fs.writeFile(`${out}/${kind}-${width}-geometry.json`,JSON.stringify(geometry,null,2));assert.deepEqual(s.errors,[])
 }finally{await context.close()}
})


await run('log panel preserves reason text without duplicate detail or null placeholder',async()=>{
 const{p,context,s}=await setup()
 try{
  s.logs=[log(201,'COMMENT'),{...log(202,'COMMENT'),reasonDetail:log(202).reason},log(203,'COMMENT')]
  await p.goto(base+'/admin/operation-logs');await ready(p)
  for(const id of [201,202,203]){
   await p.locator(`[data-log-id="${id}"] .admin-badge`).click()
   const fields=p.locator('.admin-log-fields')
   assert.equal(await fields.getByText('처리 사유',{exact:true}).count(),1)
   assert.equal(await fields.getByText('상세 사유',{exact:true}).count(),id===203?1:0)
   assert.equal(await fields.getByText('없음',{exact:true}).count(),0)
   assert.equal(await fields.locator('.admin-reason-text').first().textContent(),s.logs.find(x=>x.logId===id).reason)
   assert.equal(await p.locator('.admin-preview a').count(),0)
  }
 }finally{await context.close()}
})

await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));if(!results.length||results.some(x=>!x.passed))process.exitCode=1
