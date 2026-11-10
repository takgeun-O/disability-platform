import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-polish/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-10T01:00:00Z',path='/api/v1/admin/members'
const admin={memberId:7,nickname:'검증관리자',email:'admin@example.test',role:'ADMIN'}
const item=(id,extra={})=>({memberId:id,nickname:`소리나무 ${id}`,email:`member.${id}@example.test`,status:'ACTIVE',role:'USER',createdAt:stamp,...extra})
const detail=id=>({...item(id),updatedAt:'2026-11-10T02:00:00Z',withdrawnAt:null})
const page=(n=0,items=null)=>({items:items??Array.from({length:n?4:20},(_,i)=>item(100-n*20-i)),page:n,size:20,totalElements:24,totalPages:2,hasNext:n===0,hasPrevious:n>0})
const error=(status,code)=>({status,json:{code,message:'mock failure',fieldErrors:[]}})
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

await run('search help follows draft type and preserves AND applied filters',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members');await ready(p)
 const field=label(p,'검색어'),help=p.locator('#member-search-help')
 for(const [type,text]of [['NICKNAME','입력한 문자가 포함된 닉네임을 검색합니다.'],['EMAIL','이메일 전체 주소가 일치하는 회원을 검색합니다.'],['MEMBER_ID','회원 ID가 일치하는 회원을 검색합니다.']]){
  await label(p,'검색 기준').selectOption(type);assert((await help.innerText()).includes(text));assert((await field.getAttribute('aria-describedby')).split(' ').includes('member-search-help'));assert.equal((await help.innerText()).includes('표시용 이름'),type==='NICKNAME')
 }
 const beforeInvalid=listCalls(s);await field.fill('잘못된 ID');await button(p,'검색').click();assert.equal(await field.getAttribute('aria-invalid'),'true');assert((await field.getAttribute('aria-describedby')).includes('member-search-error'));assert.equal(listCalls(s),beforeInvalid)
 await label(p,'검색 기준').selectOption('NICKNAME');await field.fill('탈퇴');await button(p,'검색').click();await ready(p);await field.fill('미적용');await label(p,'회원 상태').selectOption('DELETED');await ready(p);await label(p,'권한').selectOption('USER');await ready(p)
 const q=new URL(reads(s).at(-1).url,'http://mock').searchParams;assert.equal(q.get('keyword'),'탈퇴');assert.equal(q.get('status'),'DELETED');assert.equal(q.get('role'),'USER');assert((await p.locator('.admin-member-applied').innerText()).includes('탈퇴'));assert(!(await p.locator('.admin-member-applied').innerText()).includes('미적용'))
})
await run('status explanations follow profile before email with distinct shared badges',async()=>{
 const{p,s}=await setup(1920);let status='ACTIVE';s.list=r=>r.fulfill({json:page(0,[item(100,{status,email:status==='DELETED'?null:'sample@example.test'})])});s.detail=r=>r.fulfill({json:{...detail(100),status,email:status==='DELETED'?null:'sample@example.test'}})
 await p.goto(base+'/admin/members');await ready(p);await p.screenshot({path:out+'/full-list.png'})
 const colors=[]
 for(const [state,desc]of [['ACTIVE',null],['PENDING','이메일 인증이 완료되지 않은 회원입니다.'],['WITHDRAWN','서비스 이용이 종료되었으며 계정 정보 정리가 완료되지 않은 상태입니다.'],['DELETED','탈퇴가 완료되어 이메일과 닉네임 등 계정 식별 정보가 제거되었습니다.']]){
  status=state;await label(p,'회원 상태').selectOption(state);await ready(p);await select(p,100);await dready(p)
  const note=p.locator('.admin-member-state-note');assert.equal(await note.count(),desc?1:0)
  if(desc){assert.equal(await note.textContent(),desc);assert(await note.evaluate(e=>e.previousElementSibling.classList.contains('admin-member-profile')&&e.nextElementSibling.textContent==='이메일'))}
  assert.equal(await button(p,'이메일 복사').count(),state==='DELETED'?0:1)
  const color=await p.locator('.admin-member-profile .admin-member-badge').evaluate(e=>getComputedStyle(e).backgroundColor);colors.push(color);assert.equal(color,await p.locator('tbody .admin-member-badge').evaluate(e=>getComputedStyle(e).backgroundColor))
  await p.screenshot({path:out+'/'+state+'-detail.png'});await button(p,'닫기').click()
 }
 assert.equal(new Set(colors).size,4)
})
for(const width of [1920,1440,390,320])await run('long member data column allocation '+width,async()=>{
 const{p,s}=await setup(width);const long=item(123456789012345,{nickname:'길고긴회원이름'.repeat(12),email:'long.address.'.repeat(14)+'@example.test'});s.list=r=>r.fulfill({json:page(0,[long,item(99,{status:'WITHDRAWN'})])});s.detail=r=>r.fulfill({json:{...long,updatedAt:stamp,withdrawnAt:null}})
 await p.goto(base+'/admin/members');await ready(p);await frames(p)
 async function measure(stage){
  const m=await p.evaluate(()=>{const t=document.querySelector('table'),cells=[...t.querySelectorAll('tbody tr:first-child td')];return {width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,table:t.getBoundingClientRect().toJSON(),cells:cells.map(e=>({rect:e.getBoundingClientRect().toJSON(),overflow:e.scrollWidth>e.clientWidth+1}))}})
  assert(!m.overflow);assert(m.cells.every(c=>!c.overflow));if(width>=1440){assert(m.cells[0].rect.width<=80);assert(m.cells[2].rect.width>=120);assert(m.cells[3].rect.width>=90);assert(m.cells[4].rect.width>=105);assert(m.cells[1].rect.width>m.cells[0].rect.width)}
  await fs.writeFile(out+`/${stage}-${width}.json`,JSON.stringify(m,null,2));await p.screenshot({path:out+`/${stage}-${width}.png`,fullPage:true})
 }
 await measure('long-list');await select(p,long.memberId);await dready(p);await frames(p)
 assert.equal(await p.locator('.admin-member-profile h3').textContent(),long.nickname);assert.equal(await p.locator('.admin-member-email-detail>span').textContent(),long.email);assert(!(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth)))
 if(width>=1440)await measure('long-split');else await p.screenshot({path:out+`/narrow-detail-${width}.png`,fullPage:true})
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));console.log(JSON.stringify({total:results.length,passed:results.filter(r=>r.passed).length,failed:results.filter(r=>!r.passed).length}));if(results.some(r=>!r.passed))process.exitCode=1
