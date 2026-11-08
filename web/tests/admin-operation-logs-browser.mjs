const openDates=async p=>{if(!await p.locator('.admin-date-range').evaluate(e=>e.open))await p.locator('.admin-date-range summary').click()}
import {adminDateScenarios} from "./helpers/admin-date-scenarios.mjs"
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.OPERATION_ARTIFACT_DIR??'/private/tmp/iyum-operation-review/browser'
const testFilter=process.env.TEST_FILTER ? new RegExp(process.env.TEST_FILTER) : null
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const path='/api/v1/admin/operation-logs',stamp='2026-11-03T18:04:05.123456Z'
const member={memberId:7,email:'mock@example.test',nickname:'검증관리자',role:'ADMIN'}
const item=(id,overrides={})=>({logId:id,actorMemberId:88,targetType:id%2?'POST':'COMMENT',targetId:id+100,action:id%2?'HIDE':'RESTORE',reasonType:"LEGACY",reasonDetail:null,reason:`검증 처리 사유 ${id}\n원문  공백 유지`,createdAt:stamp,...overrides})
const payload=(page=0,total=45,overrides={})=>({items:Array.from({length:Math.max(0,Math.min(20,total-page*20))},(_,i)=>item(total-page*20-i)),page,size:20,totalElements:total,totalPages:Math.ceil(total/20),hasNext:(page+1)*20<total,hasPrevious:page>0,...overrides})
const failure=(status,code)=>({status,json:{code,message:'mock error',fieldErrors:[]}})
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),promise:new Promise(r=>release=r),arrive:()=>arrive(),release:()=>release()}}
async function setup(role='ADMIN',width=1440){
 const context=await browser.newContext({viewport:{width,height:1000},timezoneId:'America/Los_Angeles'}),p=await context.newPage()
 p.setDefaultTimeout(8000)
 const s={member:role?{...member,role}:null,calls:[],read:null,errors:[],loginMember:null,me:null}
 p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{
  const original=window.fetch;window.__logHandled=[]
  window.fetch=async(u,o)=>{
   const isLog=String(u).includes('/admin/operation-logs')
   const response=await original(u,isLog?{...o,signal:undefined}:o)
   if(isLog){const json=response.json.bind(response);response.json=async()=>{const data=await json();setTimeout(()=>window.__logHandled.push(String(u)),0);return data};if(response.status!==200)setTimeout(()=>window.__logHandled.push(String(u)),0)}
   return response
  }
 })
 await context.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url());s.calls.push({path:u.pathname,search:u.search,method:r.request().method()})
  if(u.pathname.endsWith('/auth/me'))return s.me?s.me(r):r.fulfill(s.member?{json:s.member}:failure(401,'AUTHENTICATION_REQUIRED'))
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  if(u.pathname.endsWith('/login')){s.member=s.loginMember??{...member};return r.fulfill({json:s.member})}
  if(u.pathname===path)return s.read?s.read(r,u):r.fulfill({json:payload(Number(u.searchParams.get('page')))})
  if(u.pathname==='/api/v1/admin/posts')return r.fulfill({json:{...payload(0,0),items:[]}})
  if(/^\/api\/v1\/admin\/posts\/\d+$/.test(u.pathname))return r.fulfill(failure(404,'POST_NOT_FOUND'))
  return r.fulfill(failure(404,'NOT_FOUND'))
 })
 return{context,p,s}
}
const reads=s=>s.calls.filter(c=>c.path===path)
const button=(p,name)=>p.getByRole('button',{name,exact:true})
const label=(p,name)=>p.getByLabel(name,{exact:true})
const ready=p=>p.locator('.admin-results[aria-busy="false"]').waitFor()
const search=async p=>{await button(p,'검색').click();await ready(p)}
const focus=e=>e.evaluate(n=>n===document.activeElement)
const frames=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
async function releaseHandled(p,g,predicate){const count=await p.evaluate(()=>window.__logHandled.length);g.release();await p.waitForFunction(n=>window.__logHandled.length>n,count);await frames(p);if(predicate)await predicate()}
async function run(name,fn){if(testFilter&&!testFilter.test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message)}}
await run('ADMIN default GET, exact KST seconds, past actor and target semantics; no extra APIs',async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 assert.equal(reads(s).length,1);assert.equal(reads(s)[0].search,'?targetType=ALL&page=0&size=20&sort=LATEST')
 assert.equal(await p.getByRole('link',{name:'운영 기록',exact:true}).getAttribute('aria-current'),'page')
 assert.equal(await p.getByRole('link',{name:'게시글 관리',exact:true}).getAttribute('aria-current'),null)
 assert.equal(await p.locator('tbody tr').count(),20)
 assert.equal(await p.locator('time').first().textContent(),'2026.11.04. 03:04:05')
 assert.equal(await p.locator('time').first().getAttribute('datetime'),stamp)
 assert.equal(await p.getByRole('link',{name:'게시글 #145',exact:true}).getAttribute('href'),'/admin/posts/145?from='+encodeURIComponent('/admin/operation-logs?targetType=ALL&page=0&sort=LATEST&selected=45'))
 assert.equal(await p.getByRole('link',{name:'댓글 #144',exact:true}).count(),0)
 assert.equal(await p.locator('.admin-log-reason p').first().textContent(),'검증 처리 사유 45\n원문  공백 유지')
 assert.equal(s.calls.filter(c=>c.path.includes('/csrf')).length,0)
 assert.equal(s.calls.filter(c=>c.path.startsWith('/api/v1/admin/')).length,1)
 await p.locator('[data-log-id="44"] .admin-badge').click();assert(await p.locator('.admin-log-fields').getByText('회원 #88',{exact:true}).isVisible());assert.equal(s.calls.filter(c=>c.path.startsWith('/api/v1/admin/')).length,1)
 await p.getByRole('link',{name:'운영 기록',exact:true}).click();assert.equal(reads(s).length,1)
 await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);await ready(p)
 assert.equal(await p.getByRole('link',{name:'게시글 관리',exact:true}).getAttribute('aria-current'),'page')
 await context.close()
})
for(const role of [null,'USER'])await run(`checking and ${role} access without log request`,async()=>{
 const{context,p,s}=await setup(role),g=gate()
 s.me=async r=>{g.arrive();await g.promise;return r.fulfill(s.member?{json:s.member}:failure(401,'AUTHENTICATION_REQUIRED'))}
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await g.arrived
 assert.equal(await p.locator('.admin-operation-view').count(),0);assert.equal(reads(s).length,0)
 g.release();await p.getByRole('heading',{name:role?'관리자 권한이 없습니다':'로그인이 필요합니다',exact:true}).waitFor()
 assert.equal(reads(s).length,0)
 if(!role)assert.equal(await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).getAttribute('href'),'/login?from=%2Fadmin%2Foperation-logs')
 await context.close()
})
await run('draft filters apply on submit only, page uses applied conditions, reset omits action',async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 await label(p,'대상').selectOption('COMMENT');await label(p,'조치').selectOption('HIDE');await openDates(p);await label(p,'처리 시작일').fill('2026-11-01');await openDates(p);await label(p,'처리 종료일').fill('2026-11-04')
 assert.equal(reads(s).length,1);await openDates(p);await button(p,'기간 적용').click();await ready(p);assert.equal(new URLSearchParams(reads(s).at(-1).search).get('targetType'),'ALL');await search(p)
 const q=new URLSearchParams(reads(s).at(-1).search)
 assert.equal(q.get('targetType'),'COMMENT');assert.equal(q.get('action'),'HIDE');assert.equal(q.get('startDate'),'2026-11-01');assert.equal(q.get('endDate'),'2026-11-04');assert.equal(q.get('page'),'0')
 await label(p,'대상').selectOption('POST');await openDates(p);await label(p,'처리 시작일').fill('2026-10-01')
 await button(p,'다음').click();await ready(p)
 const next=new URLSearchParams(reads(s).at(-1).search);assert.equal(next.get('page'),'1');assert.equal(next.get('targetType'),'COMMENT');assert.equal(next.get('startDate'),'2026-11-01')
 await search(p);assert.equal(new URLSearchParams(reads(s).at(-1).search).get('page'),'0')
 await button(p,'전체 초기화').click();await ready(p);assert.equal(reads(s).at(-1).search,'?targetType=ALL&page=0&size=20&sort=LATEST')
 for(const name of ['처리 시작일','처리 종료일'])assert.equal(await label(p,name).inputValue(),'')
 await context.close()
})
await run('single dates, minimum maximum, invalid dates and reversed range never sent',async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 for(const [name,value,missing] of [['처리 시작일','0001-01-01','endDate'],['처리 종료일','9999-12-31','startDate']]){
  await button(p,'전체 초기화').click();await ready(p);await openDates(p);await label(p,name).fill(value);await openDates(p);await button(p,'기간 적용').click();await ready(p)
  const q=new URLSearchParams(reads(s).at(-1).search);assert.equal(q.get(name==='처리 시작일'?'startDate':'endDate'),value);assert(!q.has(missing))
 }
 for(const value of ['0000-01-01','2026-02-29','2026-04-31','10000-01-01','abc']){
  const n=reads(s).length;await openDates(p);await label(p,'처리 시작일').fill(value);await openDates(p);await button(p,'기간 적용').click();await p.locator('.admin-date-range [role=alert]').waitFor();assert.equal(reads(s).length,n)
 }
 await openDates(p);await label(p,'처리 시작일').fill('2026-11-05');await openDates(p);await label(p,'처리 종료일').fill('2026-11-04');const n=reads(s).length;await openDates(p);await button(p,'기간 적용').click()
 await p.locator('.admin-date-error').waitFor();assert.equal(await p.locator('.admin-date-error').evaluate(e=>{const copy=e.cloneNode(true);copy.querySelectorAll('[aria-hidden]').forEach(n=>n.remove());return copy.textContent}),'종료일을 시작일과 같거나 이후로 선택해 주세요.');assert.equal(reads(s).length,n)
 await context.close()
})
await run('server failure, malformed result, empty/no-results and same applied retry',async()=>{
 const{context,p,s}=await setup();s.read=r=>r.fulfill(failure(503,'INTERNAL_SERVER_ERROR'))
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await button(p,'다시 시도').waitFor()
 await label(p,'대상').selectOption('POST');s.read=r=>r.fulfill({json:{malformed:true}})
 await button(p,'다시 시도').click();await p.getByText('운영 기록 응답을 확인하지 못했습니다. 다시 시도해 주세요.',{exact:true}).waitFor()
 assert.equal(reads(s).at(-1).search,'?targetType=ALL&page=0&size=20&sort=LATEST')
 s.read=r=>r.fulfill({json:payload(0,0)});await button(p,'다시 시도').click();await p.getByText('운영 기록이 없습니다.',{exact:true}).waitFor()
 await search(p);await p.getByText('검색 결과가 없습니다.',{exact:true}).waitFor()
 assert.equal(await button(p,'다음').getAttribute('aria-disabled'),'true')
 await context.close()
})
for(const [status,code] of [[401,'AUTHENTICATION_REQUIRED'],[403,'ACCESS_DENIED'],[403,'ADMIN_REQUIRED'],[400,'INVALID_DATE_RANGE']])await run(`API ${status} ${code}`,async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 s.read=r=>r.fulfill(failure(status,code));await label(p,'조치').selectOption('RESTORE');await button(p,'검색').click()
 if(status===401){await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor();assert.equal(await p.locator('.admin-operation-view').count(),0)}
 else if(status===403){await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('tbody tr,.admin-preview').count(),0);assert(await button(p,'로그아웃').isVisible());assert.equal(await button(p,'다시 시도').count(),0)}
 else {await ready(p);await p.locator('.admin-operation-view').getByRole('alert').waitFor();assert.equal(await p.locator('tbody tr').count(),status===403?0:20);if(status===400){assert(await p.getByText('갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.',{exact:true}).isVisible());assert.equal(await button(p,'다음').getAttribute('aria-disabled'),'true')}assert(await button(p,'로그아웃').isVisible());if(status===403)assert.equal(await button(p,'다시 시도').count(),0);else assert(await button(p,'다시 시도').isVisible())}
 assert.equal(s.calls.filter(c=>c.path.endsWith('/logout')).length,0);await context.close()
})
await run('page focus, duplicate submit, boundary focus, diversion and error recovery',async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 const input=label(p,'페이지 바로 이동'),g=gate()
 s.read=async(r,u)=>{g.arrive();await g.promise;return r.fulfill({json:payload(Number(u.searchParams.get('page')))})}
 await input.fill('2');await input.press('Enter');await g.arrived;await input.press('Enter');await input.press('Enter');assert.equal(reads(s).length,2)
 g.release();await ready(p);assert(await focus(input));assert.equal(await input.inputValue(),'2')
 s.read=(r,u)=>r.fulfill({json:payload(Number(u.searchParams.get('page')))})
 for(const name of ['맨 마지막','맨 처음']){await button(p,name).focus();await p.keyboard.press('Enter');await ready(p);assert(await focus(p.locator('.admin-page-buttons [role=status]')))}
 await button(p,'다음').focus();await p.keyboard.press('Enter');await ready(p);assert(await focus(button(p,'다음')))
 await button(p,'이전').focus();await p.keyboard.press('Enter');await ready(p);assert(await focus(p.locator('.admin-page-buttons [role=status]')))
 const g2=gate();s.read=async r=>{g2.arrive();await g2.promise;return r.fulfill({json:payload(1)})}
 await input.fill('2');await input.press('Enter');await g2.arrived;await openDates(p);await label(p,'처리 시작일').focus();g2.release();await ready(p);assert(await focus(label(p,'처리 시작일')))
 s.read=r=>r.fulfill(failure(503,'INTERNAL_SERVER_ERROR'));await input.fill('3');await input.press('Enter');await button(p,'다시 시도').waitFor();assert(await focus(input))
 s.read=r=>r.fulfill({json:payload(2)});await button(p,'다시 시도').focus();await p.keyboard.press('Enter');await ready(p);assert.equal(await input.inputValue(),'3')
 await input.fill('0');await input.press('Enter');await p.getByRole('alert').filter({hasText:'페이지 번호'}).waitFor()
 await context.close()
})
await run('out of range corrects once using server total, no auto-query loop',async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 s.read=(r,u)=>r.fulfill({json:payload(Number(u.searchParams.get('page')),21)})
 await button(p,'맨 마지막').click();await p.getByText('2 / 2 페이지',{exact:true}).waitFor();await ready(p)
 assert.deepEqual(reads(s).slice(1).map(c=>new URLSearchParams(c.search).get('page')),['2','1'])
 await context.close()
})
for(const oldStatus of [200,401,403])await run(`latest search wins over late ${oldStatus}, transport ignores abort`,async()=>{
 const{context,p,s}=await setup(),g=gate();let first=true
 s.read=async(r,u)=>{if(first){first=false;g.arrive();await g.promise;return r.fulfill(oldStatus===200?{json:payload(0,1,{items:[item(999)]})}:failure(oldStatus,oldStatus===401?'AUTHENTICATION_REQUIRED':'ADMIN_REQUIRED'))}return r.fulfill({json:payload(Number(u.searchParams.get('page')),1,{items:[item(123)]})})}
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await g.arrived
 await p.getByRole('link',{name:'운영 기록',exact:true}).click();assert.equal(reads(s).length,1)
 await label(p,'조치').selectOption('HIDE');await search(p);await p.locator('[data-log-id="123"]').waitFor()
 await releaseHandled(p,g);assert.equal(await p.locator('[data-log-id="999"]').count(),0);assert(await p.locator('[data-log-id="123"]').isVisible());assert(await button(p,'로그아웃').isVisible());assert.equal(await p.locator('.admin-operation-view').getByRole('alert').count(),0)
 await context.close()
})
await run('old finally cannot end latest loading',async()=>{
 const{context,p,s}=await setup(),a=gate(),b=gate()
 s.read=async(r,u)=>{const g=u.searchParams.has('action')?b:a;g.arrive();await g.promise;return r.fulfill({json:payload(0,1,{items:[item(g===a?999:123)]})})}
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await a.arrived;await label(p,'조치').selectOption('HIDE');await button(p,'검색').click();await b.arrived
 await releaseHandled(p,a);assert.equal(await p.locator('.admin-results').getAttribute('aria-busy'),'true');assert.equal(await p.locator('tbody tr').count(),0)
 b.release();await ready(p);await p.locator('[data-log-id="123"]').waitFor();await context.close()
})
for(const mode of ['navigate','logout','same-login'])await run(`late response after ${mode} is ignored`,async()=>{
 const{context,p,s}=await setup(),g=gate();let first=true
 s.read=async r=>{if(first){first=false;g.arrive();await g.promise;return r.fulfill({json:payload(0,1,{items:[item(999)]})})}return r.fulfill({json:payload(0,1,{items:[item(123)]})})}
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await g.arrived
 if(mode==='navigate'){await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);await ready(p)}
 else{
  await button(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
  if(mode.endsWith('login')){
   s.loginMember=mode==='same-login'?{...member}:{...member,memberId:99,nickname:'다른관리자'}
   await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click()
   await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Mock-only-password1!');await button(p,'로그인').click();await p.locator('[data-log-id="123"]').waitFor()
  }
 }
 const n=reads(s).length;await releaseHandled(p,g);assert.equal(reads(s).length,n);assert.equal(await p.locator('[data-log-id="999"]').count(),0)
 if(mode.endsWith('login'))assert(await p.locator('[data-log-id="123"]').isVisible());else assert.equal(await p.locator('.admin-operation-view').count(),0)
 await context.close()
})
await run('late response after other-login is ignored',async()=>{
 const {context,p,s}=await setup(),oldSuccess=gate(),oldError=gate(),next=gate()
 try {
  s.read=async(r,u)=>{
   if(s.member?.memberId===99){next.arrive();await next.promise;return r.fulfill({json:payload(0,1,{items:[item(123)]})})}
   if(!u.searchParams.has('action'))return r.fulfill({json:payload(0,1,{items:[item(777)]})})
   const g=u.searchParams.get('action')==='HIDE'?oldSuccess:oldError
   g.arrive();await g.promise
   return r.fulfill(g===oldSuccess?{json:payload(0,1,{items:[item(999)]})}:failure(403,'ADMIN_REQUIRED'))
  }
  await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p);await p.locator('[data-log-id="777"]').waitFor()
  await label(p,'조치').selectOption('HIDE');await button(p,'검색').click();await oldSuccess.arrived
  await label(p,'조치').selectOption('RESTORE');await button(p,'검색').click();await oldError.arrived
  await button(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
  assert.equal(await p.locator('.admin-operation-view').count(),0);assert.equal(await p.locator('[data-log-id="777"]').count(),0)
  s.loginMember={...member,memberId:99,nickname:'다른관리자'}
  await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click()
  await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Mock-only-password1!');await button(p,'로그인').click();await next.arrived
  assert.equal(s.member.memberId,99)
  await p.locator('.admin-results[aria-busy="true"]').waitFor()
  const count=reads(s).length
  // The transport ignores abort. Wait for the old JSON consumer and React frames,
  // rather than treating fulfill/abort as proof of UI ownership.
  await releaseHandled(p,oldSuccess)
  assert.equal(await p.locator('.admin-results').getAttribute('aria-busy'),'true')
  assert.equal(await p.locator('[data-log-id="777"],[data-log-id="999"]').count(),0)
  assert.equal(await p.locator('.admin-operation-view').getByRole('alert').count(),0)
  next.release();await ready(p);await p.locator('[data-log-id="123"]').waitFor()
  await releaseHandled(p,oldError)
  assert.equal(await p.locator('.admin-results').getAttribute('aria-busy'),'false')
  assert.deepEqual(await p.locator('[data-log-id]').evaluateAll(es=>es.map(e=>e.dataset.logId)),['123'])
  assert.equal(await p.locator('.admin-operation-view').getByRole('alert').count(),0)
  assert(await button(p,'로그아웃').isVisible());assert.equal(reads(s).length,count);assert.deepEqual(s.errors,[])
 } finally {
  oldSuccess.release();oldError.release();next.release();await context.close()
 }
})
await run('network failure retries only on request and retains applied filters',async()=>{
 const{context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 s.read=r=>r.abort('failed');await label(p,'대상').selectOption('COMMENT');await label(p,'조치').selectOption('RESTORE');await search(p)
 await button(p,'다시 시도').waitFor();assert.equal(reads(s).length,2)
 await frames(p);assert.equal(reads(s).length,2)
 s.read=(r,u)=>r.fulfill({json:payload(Number(u.searchParams.get('page')),1,{items:[item(2)]})})
 await button(p,'다시 시도').click();await ready(p)
 const q=new URLSearchParams(reads(s).at(-1).search);assert.equal(q.get('targetType'),'COMMENT');assert.equal(q.get('action'),'RESTORE');assert.equal(reads(s).length,3)
 assert(await p.locator('[data-log-id="2"]').isVisible());await context.close()
})
for(const width of [1440,1024,390,320])await run(`layout ${width}: long reason/IDs, keyboard, screenshot`,async()=>{
 const{context,p,s}=await setup('ADMIN',width)
 s.read=r=>r.fulfill({json:payload(0,3,{items:[item(3,{actorMemberId:9007199254740991,targetId:9007199254740990,reasonType:"LEGACY",reasonDetail:null,reason:'긴 처리 사유입니다. 원문의 줄바꿈과 공백을 유지합니다.\n'+ '공백없는긴문자열'.repeat(16)}),item(2,{reasonType:"LEGACY",reasonDetail:null,reason:'사유를 확인한 후 숨김을 해제했습니다.'}),item(1,{reasonType:"LEGACY",reasonDetail:null,reason:'<script>alert("실행 금지")</script>'})]})})
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 assert.equal(await p.locator('.admin-log-reason script').count(),0)
 assert.equal(await p.getByRole('columnheader').count(),width>1100?4:5)
 await openDates(p);await label(p,'처리 시작일').focus();await p.keyboard.press('Tab');assert(await focus(button(p,'처리 시작일 달력 열기')))
 await p.keyboard.press('Tab');assert(await focus(label(p,'처리 종료일')))
 for(const control of await p.locator('.admin-log-filters input,.admin-log-filters select,.admin-log-filters button').all()){
  const b=await control.boundingBox();assert(b.x>=0&&b.x+b.width<=width)
 }
 assert.deepEqual(s.errors,[])
 await p.screenshot({path:`${out}/operation-${width}.png`,fullPage:true});await context.close()
})
await run('sort and dates preserve draft reason filters, explicit search applies them; reject late old results',async()=>{
 const {context,p,s}=await setup();await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 await label(p,'조치').selectOption('RESTORE');await label(p,'사유 유형').selectOption('SPAM_ADVERTISING');await label(p,'처리 시각 정렬').selectOption('OLDEST')
 await ready(p);assert(!new URLSearchParams(reads(s).at(-1).search).has('reasonType'));await openDates(p);await label(p,'처리 시작일').fill('0001-01-01');await openDates(p);await button(p,'기간 적용').click();await ready(p);await search(p)
 let params=new URLSearchParams(reads(s).at(-1).search)
 for(const [key,value] of Object.entries({action:'RESTORE',reasonType:'SPAM_ADVERTISING',sort:'OLDEST',page:'0',startDate:'0001-01-01'}))assert.equal(params.get(key),value)
 await button(p,'다음').click();await ready(p);params=new URLSearchParams(reads(s).at(-1).search);assert.equal(params.get('page'),'1');assert.equal(params.get('reasonType'),'SPAM_ADVERTISING');assert.equal(params.get('sort'),'OLDEST')
 const g=gate();s.read=async(r,u)=>{if(u.searchParams.get('reasonType')==='LEGACY'){g.arrive();await g.promise;return r.fulfill({json:payload(0,1,{items:[item(999)]})})}return r.fulfill({json:payload(0,1,{items:[item(888,{reasonType:'OTHER',reasonDetail:'상세 원문',reason:'상세 원문'})]})})}
 await label(p,'사유 유형').selectOption('LEGACY');await button(p,'검색').click();await g.arrived
 await label(p,'사유 유형').selectOption('OTHER');await search(p);await p.locator('[data-log-id="888"]').waitFor()
 await releaseHandled(p,g);assert.equal(await p.locator('[data-log-id="999"]').count(),0);assert.equal(await p.locator('.admin-log-reason p').textContent(),'상세 원문')
 s.read=null;await button(p,'전체 초기화').click();await ready(p);params=new URLSearchParams(reads(s).at(-1).search);assert(!params.has('reasonType'));assert(!params.has('action'));assert.equal(params.get('sort'),'LATEST')
 await context.close()
})
for(const width of [1440,320])await run(`stored reason exact-match display policy ${width}`,async()=>{
 const {context,p,s}=await setup('ADMIN',width)
 s.read=r=>r.fulfill({json:payload(0,6,{items:[
 item(6,{reasonType:'SPAM_ADVERTISING',reasonDetail:null,reason:'광고·홍보·스팸'}),
 item(5,{reasonType:'SPAM_ADVERTISING',reasonDetail:null,reason:'  과거 광고 사유\n원문  '}),
 item(4,{reasonType:'LEGACY',reasonDetail:null,reason:'기존 자유입력 사유'}),
 item(3,{reasonType:'OTHER',reasonDetail:'동일 상세',reason:'동일 상세'}),
 item(2,{reasonType:'OTHER',reasonDetail:'다른 상세',reason:'저장된 문구'}),
 item(1,{reasonType:'SPAM_ADVERTISING',reasonDetail:null,reason:'광고·홍보·스팸 '})]})})
 await p.goto(base+'/admin/operation-logs',{waitUntil:'domcontentloaded'});await ready(p)
 const reason=id=>p.locator(`[data-log-id="${id}"] .admin-log-reason`)
 assert.equal(await reason(6).locator('p').count(),0);assert.equal(await reason(6).locator('strong').textContent(),'광고·홍보·스팸');assert.equal(await reason(6).locator('.admin-log-inline-reason').textContent(),'')
 assert.equal(await reason(5).locator('p').textContent(),'  과거 광고 사유\n원문  ')
 assert.equal(await reason(4).locator('strong').textContent(),'기존 자유입력 사유');assert.equal(await reason(4).locator('p').textContent(),'기존 자유입력 사유')
 assert.equal(await reason(3).locator('p').count(),1);assert.equal(await reason(3).locator('p').textContent(),'동일 상세')
 assert.deepEqual(await reason(2).locator('p').allTextContents(),['저장된 문구','상세: 다른 상세']);assert.equal(await reason(1).locator('p').textContent(),'광고·홍보·스팸 ')
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/reason-display-${width}.png`,fullPage:true});await context.close()
})
await adminDateScenarios({run,setup,base,out,kind:'logs',ready,reads,template:payload})
await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));if(!results.length)throw new Error('TEST_FILTER matched no tests');if(results.some(r=>!r.passed))process.exitCode=1
