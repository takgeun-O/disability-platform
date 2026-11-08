const openDates=async p=>{if(!await p.locator('.admin-date-range').evaluate(e=>e.open))await p.locator('.admin-date-range summary').click()}
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.HISTORY_ARTIFACT_DIR??'/private/tmp/iyum-history-filter/browser'
const filter=process.env.TEST_FILTER?new RegExp(process.env.TEST_FILTER):null
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-01T00:00:00Z',member={memberId:7,email:'mock@example.test',nickname:'검증관리자',role:'ADMIN'}
const author={memberId:8,nickname:'검증작성자',withdrawn:false}
const metadata={author,status:'HIDDEN',createdAt:stamp,updatedAt:stamp,hiddenAt:stamp,deletedAt:null}
const btn=(p,name)=>p.getByRole('button',{name,exact:true}),label=(p,name)=>p.getByLabel(name,{exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
const mark=u=>`${u.searchParams.get('startDate')??'전체'}·${u.searchParams.get('sort')}·${u.searchParams.get('page')}·${u.searchParams.get('keyword')??u.searchParams.get('reasonType')??'기본'}`
async function setup(width){
 const context=await browser.newContext({viewport:{width,height:1000}}),p=await context.newPage(),s={member:{...member},calls:[],errors:[]}
 p.setDefaultTimeout(8000);p.on('pageerror',e=>s.errors.push(e.message))
 await context.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url()),path=u.pathname;s.calls.push({path,search:u.search})
  if(path.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:{status:401,json:{code:'AUTHENTICATION_REQUIRED'}})
  if(path.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(path.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  const page=Number(u.searchParams.get('page')??0),meta={page,size:20,totalElements:41,totalPages:3,hasNext:page<2,hasPrevious:page>0}
  if(path==='/api/v1/admin/posts'){const data={...meta,items:[{...metadata,postId:501+page,type:'GENERAL',topic:'DAILY',title:'결과 '+mark(u),contentPreview:'검증 본문'}]};return s.read?s.read(r,u,data):r.fulfill({json:data})}
  if(path==='/api/v1/admin/operation-logs'){const data={...meta,items:[{logId:601+page,actorMemberId:8,targetType:'POST',targetId:501+page,action:'HIDE',reasonType:'OTHER',reasonDetail:mark(u),reason:mark(u),createdAt:stamp}]};return s.read?s.read(r,u,data):r.fulfill({json:data})}
  const detail=path.match(/^\/api\/v1\/admin\/posts\/(\d+)$/)
  if(detail)return r.fulfill({json:{...metadata,postId:Number(detail[1]),type:'GENERAL',topic:'DAILY',title:'상세 검증',content:'전체 검증 본문'}})
  const comments=path.match(/^\/api\/v1\/admin\/posts\/(\d+)\/comments$/)
  if(comments)return r.fulfill({json:{postId:Number(comments[1]),postStatus:'HIDDEN',items:[],page:0,size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false}})
  return r.fulfill({status:404,json:{code:'NOT_FOUND'}})
 })
 return{context,p,s}
}
async function run(name,fn){if(filter&&!filter.test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message)}}
for(const kind of ['posts','logs'])for(const width of [1440,320])await run(`${kind} history restores applied conditions ${width}`,async()=>{
 const{context,p,s}=await setup(width),prefix=kind==='posts'?'작성':'처리',path=kind==='posts'?'/admin/posts':'/admin/operation-logs',api='/api/v1'+path
 const reads=()=>s.calls.filter(c=>c.path===api),snapshots=[]
 async function snapshot(stage){const v={stage,url:p.url(),calls:[...reads()],start:await label(p,`${prefix} 시작일`).inputValue(),end:await label(p,`${prefix} 종료일`).inputValue(),sort:await label(p,`${prefix} 시각 정렬`).inputValue(),page:await label(p,'페이지 바로 이동').inputValue(),rows:await p.locator('tbody').innerText()};snapshots.push(v);return v}
 try{
  await p.goto(base+path,{waitUntil:'domcontentloaded'});await ready(p)
  if(kind==='posts'){
   const group=p.getByRole('group',{name:'게시글 상태',exact:true});await group.getByRole('checkbox',{name:'삭제',exact:true}).uncheck()
   await label(p,'게시글 제목 검색').fill('조건 검증');await btn(p,'검색').click();await ready(p)
  }else{await label(p,'대상').selectOption('POST');await label(p,'조치').selectOption('HIDE');await label(p,'사유 유형').selectOption('OTHER');await btn(p,'검색').click();await ready(p)}
  await openDates(p);await label(p,`${prefix} 시작일`).fill('2026-11-01');await openDates(p);await label(p,`${prefix} 종료일`).fill('2026-11-08');await openDates(p);await btn(p,'기간 적용').click();await ready(p)
  await label(p,`${prefix} 시각 정렬`).selectOption('OLDEST');await ready(p)
  await btn(p,'다음').click();await ready(p)
  const expected=await snapshot('before'),request=reads().at(-1).search
  await p.screenshot({path:`${out}/${kind}-${width}-before.png`,fullPage:true})
  await openDates(p);await label(p,`${prefix} 시작일`).fill('2020-01-01')
  if(kind==='posts')await label(p,'게시글 제목 검색').fill('미제출');else await label(p,'대상').selectOption('COMMENT')
  assert.equal(p.url(),expected.url)
  await btn(p,'기간 설정 닫기').click()
  await p.locator('tbody').getByRole('link').first().click();await p.locator('.admin-preview-post[aria-busy="false"]').waitFor()
  const modalUrl=p.url()
  async function restored(stage,count){
   await ready(p);const got=await snapshot(stage)
   assert.equal(got.url,expected.url);assert.equal(got.start,expected.start);assert.equal(got.end,expected.end);assert.equal(got.sort,'OLDEST');assert.equal(got.page,'2');assert.equal(got.rows,expected.rows)
   assert.equal(reads().length,count+1,'restoration sends exactly one read, never a default read');assert.equal(reads().at(-1).search,request)
   if(kind==='posts'){assert.equal(await label(p,'게시글 제목 검색').inputValue(),'조건 검증');assert.deepEqual(await p.getByRole('group',{name:'게시글 상태',exact:true}).locator('input:checked').evaluateAll(es=>es.map(e=>e.nextSibling.textContent)),['정상','숨김'])}
   else{assert.equal(await label(p,'대상').inputValue(),'POST');assert.equal(await label(p,'조치').inputValue(),'HIDE');assert.equal(await label(p,'사유 유형').inputValue(),'OTHER')}
  }
  let n=reads().length;await p.goBack();await restored('back',n)
  await p.screenshot({path:`${out}/${kind}-${width}-back.png`,fullPage:true})
  await p.goForward();await p.locator('.admin-preview-post[aria-busy="false"]').waitFor();assert.equal(p.url(),modalUrl);n=reads().length;await p.goBack();await restored('forward-back',n)
  n=reads().length;await p.reload();await restored('reload',n)
  n=reads().length;await p.goto(expected.url);await restored('direct',n)
  // A genuine middle-click retains the canonical full-detail page and safe return URL.
  const opened=context.waitForEvent('page');await p.locator('tbody a').first().click({button:'middle'});const tab=await opened
  await tab.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor()
  const back=tab.getByRole('link',{name:kind==='posts'?'← 목록으로':'← 운영 기록으로',exact:true})
  assert.equal(await back.getAttribute('href'),modalUrl.replace(base,''));assert.equal(await tab.locator('.admin-breadcrumb').getByRole('link').innerText(),kind==='posts'?'게시글 관리':'운영 기록')
  snapshots.push({stage:'detail-return',url:tab.url(),returnHref:await back.getAttribute('href')})
  await back.click();await ready(tab);await tab.getByRole('dialog').waitFor();assert.equal(tab.url(),modalUrl)
  await btn(tab,'닫기').click();assert.equal(tab.url(),expected.url)
  assert.equal(await label(tab,`${prefix} 시작일`).inputValue(),expected.start);assert.equal(await label(tab,`${prefix} 종료일`).inputValue(),expected.end);assert.equal(await label(tab,`${prefix} 시각 정렬`).inputValue(),'OLDEST');assert.equal(await label(tab,'페이지 바로 이동').inputValue(),'2');assert.equal(await tab.locator('tbody').innerText(),expected.rows);assert.equal(reads().at(-1).search,request)
  await tab.screenshot({path:`${out}/${kind}-${width}-return-link.png`,fullPage:true});await tab.close()
  await openDates(p);await label(p,`${prefix} 시작일`).fill('2026-11-09');await openDates(p);await btn(p,'기간 적용').click();await p.locator('.admin-date-range [role=alert]').waitFor();assert.equal(reads().at(-1).search,request)
  await p.screenshot({path:`${out}/${kind}-${width}-error.png`,fullPage:true})
  await btn(p,`${prefix} 시작일 달력 열기`).click();await p.getByRole('dialog').waitFor();await p.screenshot({path:`${out}/${kind}-${width}-calendar.png`});await p.keyboard.press('Escape')
  assert(await btn(p,`${prefix} 시작일 달력 열기`).evaluate(e=>e===document.activeElement));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(s.errors,[])
 }catch(e){await fs.writeFile(`${out}/${kind}-${width}-failure.json`,JSON.stringify({url:p.url(),errors:s.errors,calls:s.calls,body:await p.locator("body").innerText(),html:await p.content()},null,2));await p.screenshot({path:`${out}/${kind}-${width}-failure.png`,fullPage:true});throw e}
 finally{await fs.writeFile(`${out}/${kind}-${width}-history.json`,JSON.stringify(snapshots,null,2));await context.close()}
})

for(const kind of ['posts','logs']){
 const path=kind==='posts'?'/admin/posts':'/admin/operation-logs',prefix=kind==='posts'?'작성':'처리',api='/api/v1'+path
 await run(`${kind} invalid URL normalizes once without invalid API requests`,async()=>{
  const{p,context,s}=await setup(320)
  try{
   await p.goto(base+path+'?statuses=BAD&targetType=BAD&action=bad&reasonType=UNKNOWN&startDate=2026-02-30&sort=bad&page=-1');await ready(p)
   const reads=s.calls.filter(c=>c.path===api);assert.equal(reads.length,1)
   const q=new URLSearchParams(reads[0].search);assert.equal(q.get('sort'),'LATEST');assert.equal(q.get('page'),'0');assert(!q.has('startDate'));assert(!p.url().includes('BAD'))
   assert((await p.locator('tbody').innerText()).includes('전체·LATEST·0'));assert.equal(await label(p,`${prefix} 시작일`).inputValue(),'')
   if(kind==='posts')assert.deepEqual(q.getAll('statuses'),['VISIBLE','HIDDEN','DELETED']);else{assert.equal(q.get('targetType'),'ALL');assert(!q.has('reasonType'));assert(!q.has('action'))}
  }finally{await context.close()}
 })
 for(const cause of ['logout','expiry'])await run(`${kind} back after ${cause} never restores administrator data`,async()=>{
  const{p,context,s}=await setup(1440)
  try{
   await p.goto(base+path+'?startDate=2026-11-01&sort=OLDEST&page=1');await ready(p)
   if(cause==='logout')await btn(p,'로그아웃').click()
   else{s.member=null;s.read=r=>r.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED'}});await label(p,`${prefix} 시각 정렬`).selectOption('LATEST')}
   await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
   const n=s.calls.filter(c=>c.path===api).length
   await p.getByRole('navigation',{name:'관리자 메뉴'}).getByRole('link',{name:'서비스로 돌아가기',exact:true}).click();await p.waitForURL(base+'/')
   await p.goBack();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
   assert.equal(await p.locator('tbody').count(),0);assert.equal(await p.locator('.admin-date-range').count(),0);assert.equal(s.calls.filter(c=>c.path===api).length,n)
  }finally{await context.close()}
 })
 await run(`${kind} delayed read cannot overwrite returned screen even when abort is ignored`,async()=>{
  const{p,context,s}=await setup(1440);let release
  try{
   await p.addInitScript(()=>{const f=window.fetch;window.__historyHandled=0;window.fetch=async(u,o)=>{const isAdmin=String(u).includes('/api/v1/admin/');const r=await f(u,isAdmin?{...o,signal:undefined}:o);if(isAdmin){const json=r.json.bind(r);r.json=async()=>{const v=await json();setTimeout(()=>window.__historyHandled++,0);return v}}return r}})
   await p.goto(base+path);await ready(p)
   let arrive;const arrived=new Promise(r=>arrive=r),gate=new Promise(r=>release=r);let held=false
   s.read=async(r,u,data)=>{if(!held&&u.searchParams.get('sort')==='OLDEST'){held=true;arrive();await gate;return r.fulfill({json:{...data,items:data.items.map(x=>({...x,title:'과거 응답',reason:'과거 응답',reasonDetail:'과거 응답'}))}})}return r.fulfill({json:data})}
   await label(p,`${prefix} 시각 정렬`).selectOption('OLDEST');await arrived
   await p.getByRole('navigation',{name:'관리자 메뉴'}).getByRole('link',{name:'서비스로 돌아가기',exact:true}).click();await p.waitForURL(base+'/')
   await p.goBack();await ready(p);const latest=await p.locator('tbody').innerText();assert(latest.includes('OLDEST'))
   const handled=await p.evaluate(()=>window.__historyHandled);release();await p.waitForFunction(n=>window.__historyHandled>n,handled)
   await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
   assert.equal(await p.locator('tbody').innerText(),latest);assert.equal(await p.locator('.admin-results').getAttribute('aria-busy'),'false');assert.equal(await p.getByText('과거 응답',{exact:true}).count(),0)
   assert.equal(await p.locator('.admin-results [role=alert]').count(),0)
  }finally{release?.();await context.close()}
 })
}
await run('detail rejects unsafe return URLs and preserves direct-entry fallback',async()=>{
 const {p,context}=await setup(320)
 try{
  for(const from of [null,'https://evil.test/admin/operation-logs','//evil.test/admin/posts','/admin/operation-logs/other','/admin/posts?sort=OLDEST#external']){
   await p.goto(base+'/admin/posts/501'+(from===null?'':'?from='+encodeURIComponent(from)))
   await p.waitForURL(url=>/^\/admin\/posts\/\d+$/.test(url.pathname));await p.getByRole('heading',{name:'게시글 상세',exact:true}).waitFor()
   assert.equal(await p.getByRole('link',{name:'← 목록으로',exact:true}).getAttribute('href'),'/admin/posts')
   assert.equal(await p.locator('.admin-breadcrumb').getByRole('link').innerText(),'게시글 관리')
  }
 }finally{await context.close()}
})
await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));if(!results.length||results.some(r=>!r.passed))process.exitCode=1
