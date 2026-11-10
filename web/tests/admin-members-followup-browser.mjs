import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-followup/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-10T01:00:00Z',path='/api/v1/admin/members'
const admin={memberId:7,nickname:'검증관리자',email:'admin@example.test',role:'ADMIN'}
const item=(id,extra={})=>({memberId:id,nickname:`소리나무 ${id}`,email:`member.${id}@example.test`,status:'ACTIVE',role:'USER',createdAt:stamp,...extra})
const detail=id=>({...item(id),updatedAt:'2026-11-10T02:00:00Z',withdrawnAt:null})
const page=(n=0,items=null)=>({items:items??Array.from({length:n?4:20},(_,i)=>item(100-n*20-i)),page:n,size:20,totalElements:24,totalPages:2,hasNext:n===0,hasPrevious:n>0})
const error=(status,code)=>({status,json:{code,message:'mock failure',fieldErrors:[]}})
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),promise:new Promise(r=>release=r),arrive,release}}
const contexts=[]
async function setup(width=1440){
 const c=await browser.newContext({viewport:{width,height:1000}});contexts.push(c)
 const p=await c.newPage();p.setDefaultTimeout(10000)
 const s={calls:[],list:null,detail:null,member:admin,errors:[]};p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const original=window.fetch;window.__memberHandled=[];window.fetch=async(u,o)=>{const relevant=String(u).includes('/admin/members');const r=await original(u,relevant?{...o,signal:undefined}:o);if(relevant){const json=r.json.bind(r);r.json=async()=>{const value=await json();queueMicrotask(()=>window.__memberHandled.push(String(u)));return value};if(r.status!==200)queueMicrotask(()=>window.__memberHandled.push(String(u)))}return r}})
 await c.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url());s.calls.push({url:u.pathname+u.search,method:r.request().method()})
  if(u.pathname.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:error(401,'AUTHENTICATION_REQUIRED'))
  if(u.pathname===path)return s.list?s.list(r,u):r.fulfill({json:page(Number(u.searchParams.get('page')))})
  if(u.pathname.startsWith(path+'/'))return s.detail?s.detail(r,u):r.fulfill({json:detail(Number(u.pathname.split('/').at(-1)))})
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  if(u.pathname==='/api/v1/admin/posts')return r.fulfill({json:{items:[],page:0,size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false}})
  return r.fulfill(error(404,'NOT_FOUND'))
 })
 return{p,s,c}
}
const button=(p,name)=>p.getByRole('button',{name,exact:true})
const label=(p,name)=>p.getByLabel(name,{exact:true})
const ready=p=>p.locator('.admin-members-list[aria-busy="false"]').waitFor()
const dready=p=>p.locator('#admin-member-detail[aria-busy="false"]').waitFor()
const select=(p,id)=>p.locator(`[data-member-select="${id}"]`).click()
const reads=s=>s.calls.filter(c=>c.url.startsWith(path+'?'))
const frames=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
async function run(name,fn){if(process.env.TEST_FILTER&&!new RegExp(process.env.TEST_FILTER).test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message);for(const c of contexts)for(const p of c.pages())await p.screenshot({path:`${out}/failure-${results.length}.png`,fullPage:true}).catch(()=>{})}finally{await Promise.all(contexts.splice(0).map(c=>c.close()))}}
const listCalls=s=>reads(s).length
const waitLoading=p=>p.locator('.admin-members-list[aria-busy="true"]').waitFor({timeout:3000})
await run('followup empty explicit search refreshes without history',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members');await ready(p)
 await p.evaluate(()=>{window.__submits=0;document.addEventListener('submit',()=>window.__submits++,true)})
 const n=listCalls(s),length=await p.evaluate(()=>history.length),g=gate();s.list=async r=>{g.arrive();await g.promise;return r.fulfill({json:page(0,[item(50)])})}
 await button(p,'검색').click();await frames(p)
 await fs.writeFile(out+'/empty-search-trace.json',JSON.stringify({before:{calls:n,history:length},after:{calls:listCalls(s),history:await p.evaluate(()=>history.length),submits:await p.evaluate(()=>window.__submits),url:p.url()}},null,2))
 assert.equal(await p.evaluate(()=>history.length),length,'same condition must not push history');await waitLoading(p);assert.equal(listCalls(s),n+1);await p.screenshot({path:out+'/same-search-loading.png'});g.release();await ready(p);assert(await p.locator('[data-member-select="50"]').isVisible())
})
await run('followup same keyword and Enter requery once, new keyword resets page and selection',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members');await ready(p)
 await label(p,'검색어').fill('소리');await button(p,'검색').click();await ready(p);assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('keyword'),'소리')
 const n=listCalls(s),length=await p.evaluate(()=>history.length);const response=p.waitForResponse(r=>r.url().includes(path+'?'));await label(p,'검색어').press('Enter');await response;await ready(p);assert.equal(listCalls(s),n+1);assert.equal(await p.evaluate(()=>history.length),length)
 await button(p,'다음').click();await ready(p);await select(p,80);await dready(p);await label(p,'검색어').fill('새로운 검색');await button(p,'검색').click();await ready(p);assert.equal(new URL(p.url()).searchParams.get('page'),'0');assert.equal(new URL(p.url()).searchParams.has('selected'),false);assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('keyword'),'새로운 검색')
 await select(p,100);await dready(p);const before=listCalls(s);await button(p,'검색').click();await ready(p);await p.locator('#admin-member-detail').waitFor({state:'detached'});assert.equal(listCalls(s),before+1)
})
await run('followup reentry shows loading not cached success and failure clears old detail',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members');await ready(p);await select(p,100);await dready(p);await button(p,'닫기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'})
 const g=gate();s.detail=async r=>{g.arrive();await g.promise;return r.fulfill(error(500,'INTERNAL_ERROR'))}
 await select(p,100);await g.arrived;assert.equal(await p.locator('#admin-member-detail').getAttribute('aria-busy'),'true');assert.equal(await p.locator('#admin-member-detail').getByText('소리나무 100',{exact:true}).count(),0)
 g.release();await dready(p);assert(await p.locator('#admin-member-detail [role="alert"]').isVisible());assert.equal(await p.locator('#admin-member-detail').getByText('소리나무 100',{exact:true}).count(),0)
 s.detail=r=>r.fulfill({json:{...detail(100),nickname:'최신 회원'}});await button(p,'상세 다시 시도').click();await dready(p);assert(await p.locator('#admin-member-detail').getByText('최신 회원',{exact:true}).isVisible())
})
for(const height of [1000,600])await run('followup bottom member detail visible '+height,async()=>{
 const{p}=await setup(1920);await p.setViewportSize({width:1920,height});await p.goto(base+'/admin/members');await ready(p)
 await p.mouse.move(700,height-80);await p.mouse.wheel(0,5000);await p.waitForFunction(()=>{const r=document.querySelector('[data-member-select="81"]').getBoundingClientRect();return r.top>0&&r.bottom<innerHeight})
 const before=await p.evaluate(()=>scrollY);await p.screenshot({path:out+`/bottom-before-${height}.png`});await select(p,81);await dready(p);await frames(p)
 const m=await p.evaluate(()=>{const panel=document.querySelector('#admin-member-detail'),head=panel.querySelector('header');return{scrollY,viewport:innerHeight,panel:panel.getBoundingClientRect().toJSON(),heading:head.getBoundingClientRect().toJSON(),overflow:document.documentElement.scrollWidth>innerWidth}})
 await fs.writeFile(out+`/bottom-${height}.json`,JSON.stringify({before,...m},null,2));await p.screenshot({path:out+`/bottom-after-${height}.png`})
 assert(m.heading.top>=0&&m.heading.bottom<=height,'selected detail heading must be in viewport');assert(m.panel.bottom<=height,'detail must fit available viewport');assert(Math.abs(m.scrollY-before)<2,'list position must remain');assert(!m.overflow)
 const body=p.locator('.admin-member-detail-body');await body.focus();await p.keyboard.press('End');await p.waitForFunction(()=>{const e=document.querySelector('.admin-member-detail-body');return e.scrollHeight-e.clientHeight-e.scrollTop<3});assert(await button(p,'닫기').isVisible());await button(p,'닫기').click();assert.equal(await p.locator('[data-member-select="81"]').evaluate(e=>e===document.activeElement),true)
})
await run('followup IME event sequence blocks composition Enter only; click remains usable',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members');await ready(p);const field=label(p,'검색어'),n=listCalls(s)
 await field.fill('소리');await field.dispatchEvent('compositionstart',{data:'소'});const prevented=await field.evaluate(e=>{const event=new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true,cancelable:true,isComposing:true});e.dispatchEvent(event);return event.defaultPrevented});assert(prevented);await frames(p);assert.equal(listCalls(s),n)
 await field.dispatchEvent('compositionend',{data:'소리'});await field.press('Enter');await ready(p);assert.equal(listCalls(s),n+1)
 await field.fill('새소리');await field.dispatchEvent('compositionstart',{data:'리'});await button(p,'검색').click();await ready(p);assert.equal(listCalls(s),n+2,'pointer submission cannot be trapped by a stale composition flag')
 await field.fill('새소리');await field.dispatchEvent('compositionend',{data:'리'});const response=p.waitForResponse(r=>r.url().includes(path+'?'));await field.press('Enter');await response;await ready(p);assert.equal(listCalls(s),n+3)
})
await run('followup repeated search response race and applied criteria',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members');await ready(p);await label(p,'검색어').fill('적용 검색');await button(p,'검색').click();await ready(p)
 await label(p,'검색어').fill('미적용 검색');await label(p,'회원 상태').selectOption('ACTIVE');await ready(p);assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('keyword'),'적용 검색')
 const g=gate();let count=0;s.list=async r=>{if(count++===0){g.arrive();await g.promise;return r.fulfill({json:page(0,[item(33)])})}return r.fulfill({json:page(0,[item(44)])})}
 await button(p,'검색').click();await g.arrived;const response=p.waitForResponse(r=>r.url().includes(path+'?'));await button(p,'검색').click();await response;await ready(p);const handled=await p.evaluate(()=>window.__memberHandled.length);g.release();await p.waitForFunction(n=>window.__memberHandled.length>n,handled);await frames(p);assert(await p.locator('[data-member-select="44"]').isVisible());assert.equal(await p.locator('[data-member-select="33"]').count(),0)
 assert((await p.locator('.admin-member-applied').innerText()).includes('적용 검색'));assert(!(await p.locator('.admin-member-applied').innerText()).includes('미적용 검색'))
})
await run('followup A B A newest detail request owns result and scroll resets',async()=>{
 const{p,s}=await setup(1920);await p.setViewportSize({width:1920,height:600});await p.goto(base+'/admin/members');await ready(p);await select(p,100);await dready(p)
 const body=p.locator('.admin-member-detail-body');await body.focus();await p.keyboard.press('End');await p.waitForFunction(()=>document.querySelector('.admin-member-detail-body').scrollTop>0)
 const a=gate(),b=gate();s.detail=async(r,u)=>{if(u.pathname.endsWith('/99')){b.arrive();await b.promise;return r.fulfill({json:detail(99)})}a.arrive();await a.promise;return r.fulfill({json:{...detail(100),nickname:'마지막 요청 회원'}})}
 await select(p,99);await b.arrived;await select(p,100);await a.arrived;assert.equal(await p.locator('#admin-member-detail').getAttribute('aria-busy'),'true');assert.equal(await body.evaluate(e=>e.scrollTop),0)
 a.release();await dready(p);const n=await p.evaluate(()=>window.__memberHandled.length);b.release();await p.waitForFunction(n=>window.__memberHandled.length>n,n);await frames(p);assert(await p.getByText('마지막 요청 회원',{exact:true}).isVisible())
})
await run('followup withdrawn labels enum requests and nonrepeated detail metadata',async()=>{
 const{p,s}=await setup(1920);s.list=(r,u)=>r.fulfill({json:page(0,[item(100,{nickname:'탈퇴한 회원',status:u.searchParams.get('status')??'WITHDRAWN',email:u.searchParams.get('status')==='DELETED'?null:'former@example.test'})])});s.detail=(r)=>r.fulfill({json:{...detail(100),nickname:'탈퇴한 회원',status:new URL(reads(s).at(-1).url,'http://mock').searchParams.get('status')??'WITHDRAWN',email:new URL(reads(s).at(-1).url,'http://mock').searchParams.get('status')==='DELETED'?null:'former@example.test'}})
 await p.goto(base+'/admin/members');await ready(p);assert.equal(await label(p,'회원 상태').locator('option[value=WITHDRAWN]').textContent(),'탈퇴·정리 대기');assert.equal(await label(p,'회원 상태').locator('option[value=DELETED]').textContent(),'탈퇴 완료')
 for(const [status,explanation]of [['WITHDRAWN','서비스 이용이 종료되었으며 계정 정보 정리가 완료되지 않은 상태입니다.'],['DELETED','탈퇴가 완료되어 이메일과 닉네임 등 계정 식별 정보가 제거되었습니다.']]){
  await label(p,'회원 상태').selectOption(status);await ready(p);assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('status'),status);await select(p,100);await dready(p);assert(await p.getByText(explanation,{exact:true}).isVisible());assert.equal(await p.locator('#admin-member-detail').getByText('탈퇴한 회원',{exact:true}).count(),1);assert(!(await p.locator('#admin-member-detail').innerText()).includes('(한국 시간)'));assert.equal(await button(p,'이메일 복사').count(),status==='DELETED'?0:1);await p.screenshot({path:out+`/${status}-detail.png`});await button(p,'닫기').click()
 }
})
await run('followup search after menu and browser navigation, narrow return',async()=>{
 const{p,s}=await setup(390);await p.goto(base+'/admin/members');await ready(p);await label(p,'검색어').fill('처음');await button(p,'검색').click();await ready(p);await label(p,'권한').selectOption('USER');await ready(p);await p.goBack();await ready(p);await p.goForward();await ready(p)
 await label(p,'검색어').fill('복귀 후');await button(p,'검색').click();await ready(p);assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('keyword'),'복귀 후')
 await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);await p.getByRole('link',{name:'회원 관리',exact:true}).click();await ready(p);await label(p,'검색어').fill('메뉴 이후');await button(p,'검색').click();await ready(p);assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('keyword'),'메뉴 이후')
 await p.locator('[data-member-select="81"]').scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY);await select(p,81);await dready(p);await p.screenshot({path:out+'/narrow-detail.png',fullPage:true});await button(p,'회원 목록으로 돌아가기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});assert(Math.abs((await p.evaluate(()=>scrollY))-y)<2);assert.equal(await p.locator('[data-member-select="81"]').evaluate(e=>e===document.activeElement),true)
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));console.log(JSON.stringify({total:results.length,passed:results.filter(r=>r.passed).length,failed:results.filter(r=>!r.passed).length}));if(results.some(r=>!r.passed))process.exitCode=1
