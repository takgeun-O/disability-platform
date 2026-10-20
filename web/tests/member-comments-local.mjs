// Read-only business-data check; credentials are read from the private owned seed folder, never logged.
import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import {seedDirectory,properties,ids,withSeedResources} from './helpers/seed-ownership.mjs'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
if(process.env.IYUM_RUN_MEMBER_LOCAL!=='true')throw Error('Explicit local seed account opt-in required')
const out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-comments-actual',results=[]
await fs.mkdir(out,{recursive:true})
await withSeedResources(async resources=>{
 const dir=seedDirectory(),manifest=properties(await fs.readFile(path.join(dir,'manifest.properties'),'utf8')),accounts=JSON.parse(await fs.readFile(path.join(dir,'accounts.json'),'utf8'))
 assert.equal(manifest.key,'feed-seed-v1');const memberId=ids(manifest.members)[0],ownedPosts=new Set(ids(manifest.posts)),ownedComments=new Set(ids(manifest.comments))
 await resources.launch(chromium,{channel:'chrome',headless:true});const ctx=await resources.context({viewport:{width:1440,height:1000}}),p=await ctx.newPage();p.setDefaultTimeout(15000)
 const api='http://127.0.0.1:8082/api/v1',responses=[],requests=[]
 p.on('response',r=>{if(new URL(r.url()).pathname==='/api/v1/members/me/comments')responses.push(r)})
 p.on('request',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/v1/members/me'))requests.push({path:u.pathname,method:r.method()})})
 try{
  await p.goto('http://127.0.0.1:8443/login?from=%2Fmypage');await p.locator('main input[name=email]').fill(accounts[0].email);await p.locator('main input[name=password]').fill(accounts[0].password);await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await p.getByRole('button',{name:'프로필 수정'}).waitFor()
  const profile=await(await ctx.request.get(api+'/members/me')).json();assert.equal(profile.memberId,memberId);assert.equal(profile.email,accounts[0].email)
  const first=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/members/me/comments');await p.getByRole('tab',{name:/내 댓글/}).click();await first;await p.waitForFunction(()=>document.querySelector('[data-member-comment-id]')||document.getElementById('my-comments-empty'))
  for(let i=0;i<2;i++){const b=p.locator('#my-panel-comments').getByRole('button',{name:'더 보기',exact:true});if(!await b.count())break;const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/members/me/comments');await b.click();assert.equal((await response).status(),200);await p.waitForFunction(()=>!document.querySelector('#my-panel-comments button[aria-disabled="true"]'))}
  const bodies=await Promise.all(responses.map(async r=>{assert.equal(r.status(),200);assert(r.headers()['cache-control'].includes('no-store'));return r.json()})),items=bodies.flatMap(b=>b.items)
  for(const c of items){assert(ownedPosts.has(c.post.postId),'post not in seed ownership');assert(ownedComments.has(c.commentId),'comment not in seed ownership')}
  for(let i=1;i<bodies.length;i++){const u=new URL(responses[i].url());assert.equal(u.searchParams.get('afterCreatedAt'),bodies[i-1].nextCursor.createdAt);assert.equal(u.searchParams.get('afterId'),String(bodies[i-1].nextCursor.commentId))}
  assert.equal(await p.locator('[data-member-comment-id]').count(),items.length);assert(requests.every(r=>r.method==='GET'))
  results.push({name:'actual Spring session/CORS/no-store/member-owned comments/raw cursor',pass:true,memberId,pages:bodies.length,items:items.length,commentIds:items.map(c=>c.commentId)})
  if(items.length){const row=p.locator('[data-member-comment-id]').last();await row.scrollIntoViewIfNeeded();const y=await p.evaluate(()=>scrollY),n=responses.length;await row.locator('a').click();await p.locator('.community-post-body').waitFor();assert.equal(await p.evaluate(()=>scrollY),0);await p.goBack();await p.waitForFunction(count=>document.querySelectorAll('[data-member-comment-id]').length===count,items.length);await p.waitForFunction(y=>Math.abs(scrollY-y)<12,y);assert.equal(responses.length,n);results.push({name:'actual detail top and back comments tab/list/position without reread',pass:true})}
  for(const width of [1440,390,320]){await p.setViewportSize({width,height:1000});await p.evaluate(async()=>{scrollTo(0,0);await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/actual-comments-${width}.png`})}
 }catch(e){results.push({name:'actual verification',pass:false,error:e.message});process.exitCode=1}
 finally{await resources.finish(()=>fs.writeFile(out+'/results.json',JSON.stringify({kind:'actual Spring / existing owned seed account / no business-data mutation',createdData:[],results},null,2)));console.log(JSON.stringify(results))}
})
