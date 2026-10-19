// Read-only verification of the current Spring endpoint using an existing owned seed account.
import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import {seedDirectory,properties,ids,withSeedResources} from './helpers/seed-ownership.mjs'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
if(process.env.IYUM_RUN_MEMBER_LOCAL!=='true')throw Error('Explicit local seed account test opt-in required')
const out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-posts-actual'
await fs.mkdir(out,{recursive:true});const results=[]
await withSeedResources(async resources=>{
 const dir=seedDirectory(),manifest=properties(await fs.readFile(path.join(dir,'manifest.properties'),'utf8')),accounts=JSON.parse(await fs.readFile(path.join(dir,'accounts.json'),'utf8'))
 assert.equal(manifest.key,'feed-seed-v1');assert.equal(accounts[0].email,'feed-seed-1@iyum.test');const memberId=ids(manifest.members)[0]
 await resources.launch(chromium,{channel:'chrome',headless:true});const ctx=await resources.context({viewport:{width:1440,height:1000}}),p=await ctx.newPage();p.setDefaultTimeout(15000)
 const api='http://127.0.0.1:8082/api/v1',pages=[],requests=[]
 p.on('response',r=>{if(new URL(r.url()).pathname==='/api/v1/members/me/posts')pages.push(r)})
 p.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/v1/members/me'))requests.push({path:new URL(r.url()).pathname,method:r.method(),url:r.url()})})
 try{
  await p.goto('http://127.0.0.1:8443/login?from=%2Fmypage');await p.locator('main input[name=email]').fill(accounts[0].email);await p.locator('main input[name=password]').fill(accounts[0].password);await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await p.locator('[data-member-post-id]').first().waitFor()
  const profile=await(await ctx.request.get(api+'/members/me')).json();assert.equal(profile.memberId,memberId);assert.equal(profile.email,accounts[0].email)
  for(let n=0;n<2;n++){if(await p.getByRole('button',{name:'더 보기',exact:true}).count()){await p.waitForFunction(()=>document.querySelector('.mypage-post-more')?.getAttribute('aria-disabled')==='false');const before=await p.locator('[data-member-post-id]').count();const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/members/me/posts');await p.getByRole('button',{name:'더 보기',exact:true}).click();assert.equal((await response).status(),200);await p.waitForFunction(before=>document.querySelectorAll('[data-member-post-id]').length>before,before)}}
  const bodies=await Promise.all(pages.map(async r=>{assert.equal(r.status(),200);assert(r.headers()['cache-control'].includes('no-store'));return r.json()}));assert(bodies.length>=3)
  for(let n=0;n<bodies.length;n++){const body=bodies[n];assert(body.items.every(item=>item.author.memberId===memberId));if(n){const u=new URL(pages[n].url());assert.equal(u.searchParams.get('afterCreatedAt'),bodies[n-1].nextCursor.createdAt);assert.equal(u.searchParams.get('afterId'),String(bodies[n-1].nextCursor.postId))}}
  const count=bodies.reduce((sum,b)=>sum+b.items.length,0);await p.waitForFunction(count=>document.querySelectorAll('[data-member-post-id]').length===count,count);assert.equal(new Set(bodies.flatMap(b=>b.items.map(i=>i.id))).size,count)
  const row=p.locator('[data-member-post-id]').nth(count-3);await row.evaluate(e=>scrollBy(0,e.getBoundingClientRect().top-120));const y=await p.evaluate(()=>scrollY),reads=pages.length;await p.evaluate(()=>{window.__frames=[];window.__watch=true;const tick=()=>{if(!window.__watch)return;if(location.pathname.startsWith('/community/posts/')&&document.querySelector('.community-post-body'))window.__frames.push({y:scrollY,behavior:getComputedStyle(document.documentElement).scrollBehavior});requestAnimationFrame(tick)};requestAnimationFrame(tick)});await row.locator('h3 a').click();await p.locator('.community-post-body').waitFor();await p.waitForFunction(()=>window.__frames.length>=20);const frames=await p.evaluate(()=>{window.__watch=false;return window.__frames});assert(frames.every(f=>f.y===0),JSON.stringify(frames));await p.goBack();await p.waitForFunction(count=>document.querySelectorAll('[data-member-post-id]').length===count,count);await p.waitForFunction(y=>Math.abs(scrollY-y)<12,y);assert.equal(pages.length,reads)
  results.push({name:'actual session/CORS/private GET200/no-store/3 pages/raw cursor/ownership/detail top/back list and position',pass:true,memberId,pages:bodies.length,items:count,detailFrames:frames,restoredY:await p.evaluate(()=>scrollY)})
  assert(requests.every(r=>r.method==='GET'));assert(!requests.some(r=>r.path.endsWith('/comments')))
  for(const width of [1440,390,320]){await p.setViewportSize({width,height:1000});await p.evaluate(async()=>{scrollTo(0,0);await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/actual-${width}.png`})}
  results.push({name:'actual list responsive 1440/390/320; no private comment GET or writes',pass:true})
 }catch(e){results.push({name:'actual verification',pass:false,error:e.message});process.exitCode=1}
 finally{await resources.finish(()=>fs.writeFile(out+'/results.json',JSON.stringify({kind:'actual Spring / existing seed account / read-only business data',createdData:[],results},null,2)));console.log(JSON.stringify(results))}
})
