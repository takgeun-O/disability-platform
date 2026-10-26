// Opt-in Spring/browser verification, restricted to existing manifest-owned data.
// Keeps a private intent/receipt and restores this member/comment pair even on failure.
import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { fixture, withSeedResources, atomicWrite, MARK } from './helpers/seed-ownership.mjs'
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
if(process.env.IYUM_RUN_COMMENT_LIKE_LOCAL !== 'true') throw Error('Explicit local seed verification required')
const api='http://127.0.0.1:8082', base='http://127.0.0.1:8443'
const out=process.env.COMMUNITY_ARTIFACT_DIR ?? '/private/tmp/iyum-comment-like-actual'
await fs.mkdir(out,{recursive:true})
const results=[],requests=[]
try {
 await withSeedResources(async resources=>{
  const seed=await fixture(api)
  await resources.launch(chromium,{channel:'chrome',headless:true})
  const ctx=await resources.context({viewport:{width:1440,height:1000}}),p=await ctx.newPage();p.setDefaultTimeout(20000)
  p.on('response',r=>{const u=new URL(r.url());if(u.origin===api&&/\/comments\/\d+\/like$/.test(u.pathname))requests.push({method:r.request().method(),status:r.status()})})
  await p.goto(base+'/login?from=%2Fmypage')
  await p.locator('input[name=email]').fill(seed.accounts[0].email);await p.locator('input[name=password]').fill(seed.accounts[0].password)
  await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage')
  const me=await(await ctx.request.get(api+'/api/v1/members/me')).json();assert.equal(me.memberId,seed.members[0])
  let target
  for(const {id} of seed.cases){
   const rows=(await(await ctx.request.get(`${api}/api/v1/community/posts/${id}/comments`)).json()).items
   const c=rows.find(c=>seed.comments.includes(c.commentId)&&c.author.memberId===me.memberId&&c.content.startsWith(MARK)&&c.liked===false&&Number.isSafeInteger(c.likeCount))
   if(c){target={postId:id,...c};break}
  }
  assert(target,'No verified unliked comment owned by the dedicated seed member')
  const endpoint=`${api}/api/v1/community/posts/${target.postId}/comments`
  const read=async()=>{const r=await ctx.request.get(endpoint);assert.equal(r.status(),200);assert.match(r.headers()['cache-control'],/no-store/);const c=(await r.json()).items.find(c=>c.commentId===target.commentId);assert(c);return c}
  const dir=path.join(seed.directory,'comment-like-journal');await fs.mkdir(dir,{recursive:true,mode:0o700})
  const receipt={kind:'comment-like-frontend-verification',memberId:me.memberId,postId:target.postId,commentId:target.commentId,originalLiked:false,originalCount:target.likeCount,restored:false}
  const file=path.join(dir,randomUUID()+'.json');await atomicWrite(file,JSON.stringify(receipt))
  try {
   await p.goto(`${base}/community/posts/${target.postId}#comment-${target.commentId}`)
   await p.waitForFunction(id=>document.querySelector(`[data-comment-like="${id}"]`)?.dataset.likeReady==='true',target.commentId)
   const button=p.locator(`[data-comment-like="${target.commentId}"] button`)
   const put=p.waitForResponse(r=>r.url()===`${endpoint}/${target.commentId}/like`&&r.request().method()==='PUT')
   await button.click();const response=await put;assert.equal(response.status(),200)
   assert.equal(response.request().postData(),null)
   const changed=await response.json();assert.equal(changed.postId,target.postId);assert.equal(changed.commentId,target.commentId);assert.equal(changed.liked,true);assert.equal(changed.likeCount,target.likeCount+1)
   await p.waitForFunction(id=>document.querySelector(`[data-comment-like="${id}"] button`)?.getAttribute('aria-pressed')==='true',target.commentId)
   const confirmed=await read();assert.equal(confirmed.liked,true);assert.equal(confirmed.likeCount,target.likeCount+1)
   const anon=await(await fetch(endpoint)).json();assert.equal(anon.items.find(c=>c.commentId===target.commentId).liked,false)
   const del=p.waitForResponse(r=>r.url()===`${endpoint}/${target.commentId}/like`&&r.request().method()==='DELETE')
   await button.click();assert.equal((await del).status(),200)
   const final=await read();assert.equal(final.liked,false);assert.equal(final.likeCount,target.likeCount)
   receipt.restored=true;await atomicWrite(file,JSON.stringify(receipt))
   results.push({name:'actual Chrome 8443 → Spring 8082: session/CORS/CSRF PUT, personalized/anonymous GET, DELETE, original state restored',pass:true})
  } finally {
   if(!receipt.restored){
    const current=await read()
    if(current.liked){const csrf=await(await ctx.request.get(api+'/api/v1/auth/csrf')).json();const r=await ctx.request.delete(`${endpoint}/${target.commentId}/like`,{headers:{[csrf.headerName]:csrf.token}});assert.equal(r.status(),200)}
    const final=await read();assert.equal(final.liked,false);assert.equal(final.likeCount,target.likeCount)
    receipt.restored=true;await atomicWrite(file,JSON.stringify(receipt))
   }
  }
 })
}catch(e){results.push({name:'actual verification',pass:false,error:e.message});process.exitCode=1}
await fs.writeFile(out+'/results.json',JSON.stringify({results,requests,notes:'Existing verified seed pair only; no accounts, posts or comments created/deleted. Private ownership receipt retained outside repo. No credentials or payloads included.'},null,2))
console.log(JSON.stringify(results))
