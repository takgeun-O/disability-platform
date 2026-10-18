// Read-only browser verification against the running local UI and Spring API.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443'
assert.equal(new URL(base).hostname,'127.0.0.1')
const out=process.env.COMMUNITY_ARTIFACT_DIR??'/private/tmp/iyum-scroll-local'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
try{
 for(const width of [1440,390]){
  const ctx=await browser.newContext({viewport:{width,height:900},reducedMotion:'no-preference'})
  await ctx.addInitScript(()=>{delete window.IntersectionObserver})
  const p=await ctx.newPage()
  try{
   await p.goto(base+'/community');await p.locator('[data-feed-id]').first().waitFor()
   assert.equal(await p.evaluate(()=>getComputedStyle(document.documentElement).scrollBehavior),'auto','Running server must deliver current instant-scroll CSS')
   for(const n of [40,60]){await p.getByRole('button',{name:'게시글 더 보기',exact:true}).click();await p.waitForFunction(n=>document.querySelectorAll('[data-feed-id]').length===n,n)}
   for(const mode of ['link','back']){
    const row=p.locator('[data-feed-id]').nth(54),id=await row.getAttribute('data-feed-id')
    await row.evaluate(el=>scrollBy({top:el.getBoundingClientRect().top-150,behavior:'instant'}))
    const before=await p.evaluate(()=>scrollY)
    await p.evaluate(id=>{window.__frames=[];window.__watch=true;const tick=()=>{if(!window.__watch)return;if(location.pathname===`/community/posts/${id}`&&document.querySelector('.community-post-body'))window.__frames.push(scrollY);requestAnimationFrame(tick)};requestAnimationFrame(tick)},id)
    await row.locator('h3 a').click();await p.locator('.community-post-body').waitFor();await p.waitForFunction(()=>window.__frames.length>=20)
    const frames=await p.evaluate(()=>{window.__watch=false;return window.__frames})
    assert(frames.every(y=>y===0),`First visible detail frames: ${frames}`)
    await p.screenshot({path:`${out}/detail-${width}-${mode}.png`})
    if(mode==='link')await p.locator('.community-back').click();else await p.goBack()
    await p.waitForFunction(()=>document.querySelectorAll('[data-feed-id]').length===60)
    await p.waitForFunction(y=>Math.abs(scrollY-y)<12,before)
    results.push({width,mode,pass:true,detailFrames:frames,restoredCount:60,restoredY:await p.evaluate(()=>scrollY)})
   }
  }finally{await ctx.close()}
 }
}catch(e){results.push({pass:false,error:e.message});process.exitCode=1}
finally{await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify({kind:'actual local UI and Spring, read-only, normal motion',results},null,2));console.log(JSON.stringify(results))}
