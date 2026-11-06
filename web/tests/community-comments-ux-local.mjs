// Actual local Spring/seed verification. Private credentials are read, never logged.
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
if (process.env.IYUM_RUN_LOCAL_COMMENT_UX !== 'true') throw new Error('Explicit local test opt-in required')
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
const out=process.env.COMMUNITY_ARTIFACT_DIR??'/private/tmp/iyum-comment-ux-20261018/actual'
await fs.mkdir(out,{recursive:true})
import {withSeedResources,fixture,beginComment,recordComment,recordSoftDelete,atomicWrite} from './helpers/seed-ownership.mjs'
const base='http://127.0.0.1:8443',api='http://127.0.0.1:8082',results=[],created=[],deleted=[],receipts=[]
await withSeedResources(async resources=>{
const seed=await fixture(api)
const account=seed.accounts[0],representative=seed.cases[0].id,detail=`/community/posts/${representative}`
await resources.launch(chromium,{channel:'chrome',headless:true})
const ctx=await resources.context({viewport:{width:1440,height:1000},reducedMotion:'reduce'}),p=await ctx.newPage()
p.setDefaultTimeout(15000)
const save=()=>atomicWrite(out+'/results.json',JSON.stringify({kind:'actual Spring + owned fixture',representative,results,createdCommentIds:created,ownedCommentIds:receipts.filter(r=>r.entry.recorded).map(r=>r.entry.commentId),softDeletedCommentIds:deleted,recoveryJournals:receipts.map(r=>r.file)},null,2))
let stage='login'
try{
 await p.goto(base+'/login?from='+encodeURIComponent(detail))
 await p.locator('main input[name=email]').fill(account.email);await p.locator('main input[name=password]').fill(account.password)
 await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname===detail)
 await p.getByRole('button',{name:'댓글 등록',exact:true}).waitFor()
 const me=await(await ctx.request.get(api+'/api/v1/auth/me')).json();assert.equal(me.memberId,seed.members[0])
 results.push({name:'actual session login',pass:true})
 stage='owned representative auto cursor'
 const total=seed.cases[0].count
 for(let count=Math.min(40,total); count>20; count=count===total?0:Math.min(count+20,total)){
  await p.locator('.community-comment-sentinel').evaluate(el=>window.scrollTo(0,el.getBoundingClientRect().top+scrollY-innerHeight-120))
  await p.waitForFunction(n=>document.querySelectorAll('.community-comment-list > li').length===n,count)
 }
 const ids=await p.locator('.community-comment-list > li').evaluateAll(es=>es.map(e=>e.id));assert.equal(new Set(ids).size,total)
 results.push({name:'actual owned representative cursor count without duplicate',pass:true})
 stage='boundary seeds'
 for(const {id:post,count} of seed.cases.slice(1)){
  await p.goto(base+'/community/posts/'+post);await p.getByRole('button',{name:'댓글 등록',exact:true}).waitFor()
  if(count<=20) assert.equal(await p.locator('.community-comment-list > li').count(),Math.min(count,20))
  if(count>20){if(await p.locator('.community-comment-sentinel').count()) await p.locator('.community-comment-sentinel').evaluate(el=>window.scrollTo(0,el.getBoundingClientRect().top+scrollY-innerHeight-120));await p.waitForFunction(n=>document.querySelectorAll('.community-comment-list > li').length===n,count)}
  results.push({name:`actual seed ${post}: ${count} boundary`,pass:true})
 }
 stage='two create receipts'
 await p.goto(base+detail);await p.getByRole('button',{name:'댓글 등록',exact:true}).waitFor()
 for(const text of ['댓글 UX 실제 검증 — 첫 등록','댓글 UX 실제 검증 — 연속 등록']){
  const receipt=await beginComment(seed,representative,seed.members[0],text);receipts.push(receipt)
  await p.locator('#comment-draft').fill(receipt.content)
  const response=p.waitForResponse(r=>new URL(r.url()).pathname===`/api/v1/community/posts/${representative}/comments`&&r.request().method()==='POST')
  await p.getByRole('button',{name:'댓글 등록',exact:true}).click()
  const res=await response;assert.equal(res.status(),201);const id=(await res.json()).commentId;assert(Number.isSafeInteger(id));created.push(id);await recordComment(seed,receipt,id);await save()
  await p.locator(`[data-created-comment-id="${id}"]`).waitFor();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
 }
 assert.equal(await p.locator('.community-comment-list > li').count(),20)
 for(const width of [1440,390,320]){await p.setViewportSize({width,height:950});await p.locator('.community-comment-receipts').screenshot({path:out+`/actual-receipts-${width}.png`});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))}
 results.push({name:'actual two 201/id receipts without loading unseen middle',pass:true})
 stage='cleanup'
 const csrf=await (await ctx.request.get(api+'/api/v1/auth/csrf')).json()
 for(const id of created){const res=await ctx.request.delete(api+`/api/v1/community/posts/${representative}/comments/${id}`,{headers:{[csrf.headerName]:csrf.token}});assert.equal(res.status(),204);deleted.push(id);await recordSoftDelete(receipts.find(r=>r.entry.commentId===id));await save()}
 await p.locator('#comments-refresh').click();await p.waitForFunction(()=>!document.querySelector('#comments-refresh').disabled)
 assert.equal(receipts.filter(r=>r.entry.recorded).length,created.length)
 results.push({name:'only recorded newly created comments soft-deleted with 204; ownership retained',pass:true})
 stage='logout and modal'
 await p.setViewportSize({width:1440,height:1000})
 await p.locator('#comment-draft').fill('전송하지 않는 보존 초안')
 await p.getByLabel('회원 메뉴').getByRole('button',{name:'로그아웃',exact:true}).click()
 const button=p.getByRole('button',{name:'댓글 등록',exact:true});await button.waitFor()
 for(const width of [1440,390,320]){await p.setViewportSize({width,height:950});await button.click();await p.getByRole('dialog').screenshot({path:out+`/actual-login-confirm-${width}.png`});await p.keyboard.press('Escape');assert.equal(new URL(p.url()).pathname,detail);assert.equal(await p.locator('#comment-draft').inputValue(),'전송하지 않는 보존 초안');assert(await button.evaluate(e=>document.activeElement===e))}
 await button.click();await p.getByRole('button',{name:'로그인하기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/login'&&u.searchParams.get('from')?.startsWith(detail))
 results.push({name:'actual logout keeps draft; modal cancel and single confirmed login at all widths',pass:true})
}catch(e){results.push({name:stage,pass:false,errorKind:e.name});process.exitCode=1;console.error('FAIL actual stage',stage,e.name,e.message);throw e}
finally{
 // Clean up only successful IDs from this run if an earlier assertion failed.
 if(created.some(id=>!deleted.includes(id))){try{const csrf=await(await ctx.request.get(api+'/api/v1/auth/csrf')).json();for(const id of created.filter(id=>!deleted.includes(id)&&receipts.some(r=>r.entry.commentId===id&&r.entry.recorded))){const r=await ctx.request.delete(api+`/api/v1/community/posts/${representative}/comments/${id}`,{headers:{[csrf.headerName]:csrf.token}});if(r.status()===204){deleted.push(id);await recordSoftDelete(receipts.find(r=>r.entry.commentId===id))}}}catch{results.push({name:'cleanup requires follow-up for recorded IDs',pass:false})}}
 await resources.finish(async()=>{await save();console.log(JSON.stringify({results,created,deleted}))})
}

})
