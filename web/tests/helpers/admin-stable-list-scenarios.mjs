import assert from 'node:assert/strict'
import fs from 'node:fs/promises'

export async function adminStableListScenarios({run,setup,base,out,kind,ready,reads,template}) {
 const comments=kind==='comments',slot=comments?'comments':'read'
 const area=comments?'.admin-comment-results':'.admin-results',rows=comments?'.admin-comment-list li':'.admin-table-wrap tbody tr'
 const form=comments?'.admin-comment-filters':'.admin-post-filters',group=comments?'댓글 상태':'게시글 상태'
 const url=comments?'/admin/posts/2':'/admin/posts'
 const metrics=p=>p.evaluate(({area,form})=>{
  const a=document.querySelector(area).getBoundingClientRect(),f=document.querySelector(form).getBoundingClientRect()
  return {height:a.height,filterY:f.y,scrollY:scrollY}
 },{area,form})
 function stable(before,after){for(const key of ['height','filterY','scrollY'])assert(Math.abs(after[key]-before[key])<2,`${key}: ${before[key]} -> ${after[key]}`)}
 for(const width of [1440,320])await run(`retained ${kind} rows, failure and locks ${width}`,async()=>{
  const {context,p,state}=await setup('ADMIN',width);let release
  const body=u=>{
   const data=template(Number(u.searchParams.get('page')))
   return {...data,items:Array.from({length:20},(_,i)=>({...data.items[0],...(comments?{commentId:i+1,content:`유지할 댓글 ${i+1}\n`+'긴 본문 '.repeat(12)}:{postId:i+1,title:`유지할 게시글 ${i+1}`} )})),totalElements:40,totalPages:2,hasNext:data.page===0,hasPrevious:data.page>0}
  }
  state[slot]=(r,u)=>r.fulfill({json:body(u)})
  try {
   await p.goto(base+url);await ready(p);await p.evaluate(()=>document.fonts.ready)
   await p.clock.install({time:new Date('2030-01-01T00:00:00Z')});await p.clock.pauseAt(new Date('2030-01-01T00:00:01Z'))
   const field=p.getByRole('group',{name:group,exact:true}),chip=field.getByRole('checkbox',{name:'삭제',exact:true})
   await chip.scrollIntoViewIfNeeded();await chip.focus();await p.clock.runFor(32)
   const before=await metrics(p),parent=comments?await p.locator('.admin-parent-status').textContent():null
   let arrive;const arrived=new Promise(r=>arrive=r),gate=new Promise(r=>release=r)
   state[slot]=async(r,u)=>{arrive();await gate;await r.fulfill({json:body(u)})}
   const start=reads(state).length
   await chip.press('Space');await p.clock.runFor(32)
   assert.equal(reads(state).length,start);assert.equal(await p.locator(rows).count(),20)
   assert.equal(await p.locator(area).getAttribute('aria-busy'),'true');stable(before,await metrics(p))
   if(comments)assert.equal(await p.locator('.admin-parent-status').textContent(),parent)
   await p.clock.runFor(168);await arrived
   stable(before,await metrics(p));assert.equal(await p.locator(rows).count(),20)
   assert(await chip.evaluate(e=>e===document.activeElement))
   for(const button of await p.locator('.admin-pagination button').all())assert.equal(await button.getAttribute('aria-disabled'),'true')
   if(comments){assert.equal(await p.locator('.admin-comment-list button:disabled').count(),20);await p.locator('.admin-comment-list button').first().evaluate(e=>e.click());assert.equal(await p.getByRole('dialog').count(),0)}
   assert.equal(await p.locator(form).locator('button:disabled,input:disabled').count(),0)
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.screenshot({path:`${out}/retained-${kind}-${width}.png`})
   await fs.writeFile(`${out}/retained-${kind}-${width}.json`,JSON.stringify({before,pending:await metrics(p)},null,2))
   release();await ready(p)
   // Page navigation from near the bottom must not collapse the long list or move focus.
   const next=p.getByRole('button',{name:'다음',exact:true});await next.scrollIntoViewIfNeeded();await next.focus();await p.clock.runFor(32)
   const pageBefore=await metrics(p);let pageArrive;const pageArrived=new Promise(r=>pageArrive=r),pageGate=new Promise(r=>release=r)
   state[slot]=async(r,u)=>{pageArrive();await pageGate;await r.fulfill({json:body(u)})}
   await next.press('Enter');await pageArrived;await p.clock.runFor(32)
   stable(pageBefore,await metrics(p));assert(await next.evaluate(e=>e===document.activeElement))
   const count=reads(state).length;await next.press('Enter');assert.equal(reads(state).length,count)
   release();await ready(p)
   // General failure retains rows but all row actions / page changes remain locked.
   state[slot]=r=>r.fulfill({status:500,json:{code:'INTERNAL_SERVER_ERROR'}})
   await p.locator(form).locator('input:not([type=checkbox])').fill('실패 조건')
   await p.locator(form).locator('button[type=submit]').click();await ready(p)
   await p.getByText('갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.',{exact:true}).waitFor()
   assert.equal(await p.locator(rows).count(),20)
   assert.equal(await next.getAttribute('aria-disabled'),'true')
   if(comments)assert.equal(await p.locator('.admin-comment-list button:disabled').count(),20)
   state[slot]=(r,u)=>r.fulfill({json:body(u)})
   await p.locator(area).getByRole('button',{name:comments?'댓글 다시 시도':'다시 시도',exact:true}).click();await ready(p)
   assert.equal(new URLSearchParams(reads(state).at(-1).search).get('keyword'),'실패 조건')
   assert.equal(await p.locator(area).getByRole('alert').count(),0)
   // Empty selection discards the snapshot, including on reselection before its response.
   for(const label of ['정상','숨김'])await field.getByRole('checkbox',{name:label,exact:true}).uncheck()
   assert.equal(await p.locator(rows).count(),0);assert.equal(await p.locator('.admin-pagination').count(),0)
   let emptyArrive;const emptyArrived=new Promise(r=>emptyArrive=r),emptyGate=new Promise(r=>release=r)
   state[slot]=async(r,u)=>{emptyArrive();await emptyGate;await r.fulfill({json:body(u)})}
   await field.getByRole('checkbox',{name:'정상',exact:true}).check();await p.clock.runFor(200);await emptyArrived
   assert.equal(await p.locator(rows).count(),0);release();await ready(p)
   // Confirmed 404 clears old results, and a new search cannot resurrect them.
   state[slot]=r=>r.fulfill({status:404,json:{code:'POST_NOT_FOUND'}})
   await p.locator(form).locator('button[type=submit]').click();await ready(p)
   assert.equal(await p.locator(rows).count(),0);assert.equal(await p.locator('.admin-pagination').count(),0)
  } finally {release?.();await context.close()}
 })
 for(const status of [401,403])await run(`retained ${kind} snapshot is removed on ${status}`,async()=>{
  const {context,p,state}=await setup()
  try{
   await p.goto(base+url);await ready(p);assert((await p.locator(rows).count())>0)
   state[slot]=r=>r.fulfill({status,json:{code:status===401?'AUTHENTICATION_REQUIRED':'ACCESS_DENIED'}})
   await p.locator(form).locator('button[type=submit]').click()
   if(status===401)await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
   else if(comments)await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor()
   else await ready(p)
   assert.equal(await p.locator(rows).count(),0);assert.equal(await p.locator('.admin-pagination').count(),0)
   if(status===403)assert(await p.getByRole('button',{name:'로그아웃',exact:true}).isVisible())
  }finally{await context.close()}
 })
 if(comments)await run('deleted parent notice survives pending comment filter',async()=>{
  const {context,p,state}=await setup();let release
  try{
   state.comments=(r,u)=>r.fulfill({json:{...template(Number(u.searchParams.get('page'))),postStatus:'DELETED'}})
   await p.goto(base+url);await ready(p)
   await p.clock.install();await p.clock.pauseAt(new Date(Date.now()+1000))
   const before=await p.locator('.admin-parent-status').textContent()
   state.comments=async(r,u)=>{await new Promise(resolve=>release=resolve);await r.fulfill({json:{...template(Number(u.searchParams.get('page'))),postStatus:'DELETED'}})}
   await p.getByRole('group',{name:'댓글 상태',exact:true}).getByRole('checkbox',{name:'삭제',exact:true}).uncheck()
   assert.equal(await p.locator('.admin-parent-status').textContent(),before)
   assert(before.includes('삭제되어'))
  }finally{release?.();await context.close()}
 })
}
