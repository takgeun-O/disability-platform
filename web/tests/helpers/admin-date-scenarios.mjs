const openDates=async p=>{if(!await p.locator('.admin-date-range').evaluate(e=>e.open))await p.locator('.admin-date-range summary').click()}
import assert from 'node:assert/strict'
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const field=(p,name)=>p.getByLabel(name,{exact:true})
export async function adminDateScenarios({run,setup,base,out,kind,ready,reads,template}){
 const label=kind==='posts'?'작성':'처리',path=kind==='posts'?'/admin/posts':'/admin/operation-logs'
 for(const width of [1440,320])await run(`date/sort ${kind} ${width}: applied drafts, calendar, presets, loading and reset`,async()=>{
  const setupResult=await setup('ADMIN',width),{p,context}=setupResult,s=setupResult.state??setupResult.s
  await p.clock.setFixedTime(new Date('2026-11-07T15:30:00Z'))
  try{
   await p.goto(base+path,{waitUntil:'domcontentloaded'});await ready(p)
   const params=()=>new URLSearchParams(reads(s).at(-1).search)
   const start=field(p,`${label} 시작일`),end=field(p,`${label} 종료일`),sort=field(p,`${label} 시각 정렬`)
   const draftOther=kind==='posts'?field(p,'게시글 제목 검색'):field(p,'대상')
   if(kind==='posts')await draftOther.fill('미제출 제목');else await draftOther.selectOption('COMMENT')
   await openDates(p);await start.fill('2026-11-01');await openDates(p);await end.fill('2026-11-08');assert.equal(reads(s).length,1)
   await openDates(p);await btn(p,'기간 적용').click();await ready(p)
   assert.equal(params().get('startDate'),'2026-11-01');assert.equal(params().get('endDate'),'2026-11-08')
   assert.equal(params().get(kind==='posts'?'keyword':'targetType'),kind==='posts'?null:'ALL')
   const count=reads(s).length;await openDates(p);await btn(p,'기간 적용').click();assert.equal(reads(s).length,count)
   await openDates(p);await start.fill('2026-10-01');await sort.selectOption('OLDEST');await ready(p)
   assert.equal(params().get('sort'),'OLDEST');assert.equal(params().get('page'),'0');assert.equal(params().get('startDate'),'2026-11-01');assert.equal(await start.inputValue(),'2026-10-01')
   if(kind==='posts'){
    assert.equal(await draftOther.inputValue(),'미제출 제목')
    const changed=p.waitForResponse(r=>{const u=new URL(r.url());return u.pathname==='/api/v1/admin/posts'&&u.searchParams.getAll('statuses').length===2})
    await p.getByRole('group',{name:'게시글 상태',exact:true}).getByRole('checkbox',{name:'삭제',exact:true}).uncheck();await changed;await ready(p)
    assert.equal(params().get('startDate'),'2026-11-01');assert.equal(params().get('sort'),'OLDEST');assert(!params().has('keyword'));assert.equal(await start.inputValue(),'2026-10-01')
   }else assert.equal(await draftOther.inputValue(),'COMMENT')
   await btn(p,'다음').click();await ready(p);assert.equal(params().get('page'),'1');assert.equal(params().get('startDate'),'2026-11-01')
   await openDates(p);await btn(p,'오늘').click();await ready(p);assert.equal(params().get('startDate'),'2026-11-08');assert.equal(params().get('endDate'),'2026-11-08');assert.equal(params().get('page'),'0')
   for(const [name,date] of [['최근 7일','2026-11-02'],['최근 30일','2026-10-10']]){await openDates(p);await btn(p,name).click();await ready(p);assert.equal(params().get('startDate'),date);assert.equal(params().get('endDate'),'2026-11-08')}
   await openDates(p);await btn(p,`${label} 시작일 지우기`).click();const before=reads(s).length;assert.equal(await start.inputValue(),'');assert.equal(reads(s).length,before)
   await openDates(p);await btn(p,'기간 적용').click();await ready(p);assert(!params().has('startDate'));assert.equal(params().get('endDate'),'2026-11-08')
   await openDates(p);await start.fill('2026-11-09');await openDates(p);await btn(p,'기간 적용').click();await p.locator('.admin-date-error').waitFor();assert.equal(await p.locator('.admin-date-error').evaluate(e=>{const copy=e.cloneNode(true);copy.querySelectorAll('[aria-hidden]').forEach(n=>n.remove());return copy.textContent}),'종료일을 시작일과 같거나 이후로 선택해 주세요.');assert.equal(await start.getAttribute('aria-invalid'),'true')
   assert.equal(reads(s).length,before+1);await p.screenshot({path:`${out}/date-${kind}-${width}-error.png`,fullPage:true})
   await openDates(p);await start.fill('2026-11-08');await btn(p,`${label} 시작일 달력 열기`).click()
   const dialog=p.getByRole('dialog');await dialog.waitFor();assert(await dialog.getByRole('button',{name:'2026년 11월 8일',exact:true}).evaluate(n=>n===document.activeElement))
   await p.keyboard.press('ArrowLeft');assert(await dialog.getByRole('button',{name:'2026년 11월 7일',exact:true}).evaluate(n=>n===document.activeElement))
   const box=await dialog.boundingBox();assert(box.x>=0&&box.x+box.width<=width)
   await p.screenshot({path:`${out}/date-${kind}-${width}-calendar.png`,fullPage:false})
   await p.keyboard.press('Enter');await dialog.waitFor({state:'detached'});assert.equal(await start.inputValue(),'2026-11-07');assert(await btn(p,`${label} 시작일 달력 열기`).evaluate(n=>n===document.activeElement))
   await btn(p,`${label} 종료일 달력 열기`).click();await p.keyboard.press('Escape');await dialog.waitFor({state:'detached'});assert(await btn(p,`${label} 종료일 달력 열기`).evaluate(n=>n===document.activeElement))
   await openDates(p);await btn(p,'기간 적용').click();await ready(p)
   // Hold the next request: old rows remain while dates and sort remain interactive.
   let arrive,release;const arrived=new Promise(r=>arrive=r),gate=new Promise(r=>release=r)
   s.read=async(r,u)=>{arrive();await gate;return r.fulfill({json:template(Number(u.searchParams.get('page')))})}
   const rows=await p.locator('tbody tr').count();await openDates(p);await btn(p,'전체 기간').click();await arrived
   assert.equal(await p.locator('tbody tr').count(),rows);assert.equal(await p.locator('.admin-results').getAttribute('aria-busy'),'true')
   assert(await p.getByText('목록을 갱신하고 있습니다…',{exact:true}).isVisible());assert.equal(await btn(p,'다음').getAttribute('aria-disabled'),'true')
   await openDates(p);assert(await start.isEnabled());assert(await sort.isEnabled())
   await p.screenshot({path:`${out}/date-${kind}-${width}-loading.png`,fullPage:true});release();await ready(p)
   assert(!params().has('startDate'));assert(!params().has('endDate'));assert.equal(params().get('sort'),'OLDEST')
   s.read=null;await btn(p,'전체 초기화').click();await ready(p);assert.equal(params().get('sort'),'LATEST');assert.equal(params().get('page'),'0');assert.equal(await start.inputValue(),'');assert.equal(await end.inputValue(),'')
   const resetCount=reads(s).length;await btn(p,'전체 초기화').click();assert.equal(reads(s).length,resetCount)
   if(kind==='posts'){assert.deepEqual(params().getAll('statuses'),['VISIBLE','HIDDEN','DELETED']);assert(!params().has('status'))}else assert.equal(params().get('targetType'),'ALL')
   await p.evaluate(()=>document.fonts.ready);assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await openDates(p);for(const control of await p.locator('.admin-date-range input,.admin-date-range button,.admin-sort-control select').all()){const b=await control.boundingBox();assert(b.x>=0&&b.x+b.width<=width)}
   await p.screenshot({path:`${out}/date-${kind}-${width}.png`,fullPage:true});assert.deepEqual(s.errors,[])
  }finally{await context.close()}
 })
 for(const lateStatus of [200,401])await run(`date/sort ${kind}: old ${lateStatus} delivery cannot override newest sort or auth`,async()=>{
  const result=await setup(),{p,context}=result,s=result.state??result.s
  let release
  try{
   await p.addInitScript(()=>{const original=window.fetch;window.__dateHandled=0;window.fetch=async(u,o)=>{
    const match=String(u).includes('/api/v1/admin/');const r=await original(u,match?{...o,signal:undefined}:o)
    if(match){if(r.status===200){const json=r.json.bind(r);r.json=async()=>{const data=await json();setTimeout(()=>window.__dateHandled++,0);return data}}else setTimeout(()=>window.__dateHandled++,0)}return r
   }})
   await p.goto(base+path,{waitUntil:'domcontentloaded'});await ready(p)
   let arrive;const arrived=new Promise(r=>arrive=r),gate=new Promise(r=>release=r)
   s.read=async(r,u)=>{if(u.searchParams.get('sort')==='OLDEST'){arrive();await gate;return r.fulfill(lateStatus===200?{json:template(0)}:{status:401,json:{code:'AUTHENTICATION_REQUIRED'}})}return r.fulfill({json:template(0)})}
   await openDates(p);await field(p,`${label} 시작일`).fill('2026-11-01');await field(p,`${label} 시각 정렬`).selectOption('OLDEST');await arrived
   await field(p,`${label} 시각 정렬`).selectOption('LATEST');await ready(p)
   const count=await p.evaluate(()=>window.__dateHandled);release();await p.waitForFunction(n=>window.__dateHandled>n,count)
   await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
   assert.equal(await field(p,`${label} 시각 정렬`).inputValue(),'LATEST');assert.equal(await field(p,`${label} 시작일`).inputValue(),'2026-11-01')
   assert.equal(await p.locator('.admin-results').getAttribute('aria-busy'),'false');assert.equal(await p.locator('.admin-results [role=alert]').count(),0)
   assert(await btn(p,'로그아웃').isVisible());assert((await p.locator('tbody tr').count())>0)
   // Same-condition reset retries after failure; a healthy no-op reset does not fetch.
   s.read=r=>r.fulfill({status:503,json:{code:'INTERNAL_SERVER_ERROR'}})
   await openDates(p);await btn(p,'기간 적용').click();await p.locator('.admin-results [role=alert]').waitFor();assert.equal(await btn(p,'다음').getAttribute('aria-disabled'),'true')
   await btn(p,'전체 초기화').click();await ready(p);const before=reads(s).length
   s.read=(r,u)=>r.fulfill({json:template(Number(u.searchParams.get('page')))})
   await btn(p,'전체 초기화').click();await ready(p);assert.equal(reads(s).length,before+1);assert.equal(await p.locator('.admin-results [role=alert]').count(),0)
  }finally{release?.();await context.close()}
 })

}
