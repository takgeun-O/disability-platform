// Read-only production browser + existing local Spring/DB. No test records are created.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const api=process.env.API_BASE_URL??'http://127.0.0.1:8082',base=process.env.BASE_URL??'http://127.0.0.1:8452'
const output=process.env.COMMUNITY_ARTIFACT_DIR??'/private/tmp/iyum-feed-local'
await fs.mkdir(output,{recursive:true})
const results=[],browser=await chromium.launch({headless:true,channel:'chrome'})
async function check(name,run){try{await run();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.message});process.exitCode=1;console.log('FAIL',name,e.message)}}
let first
try{
 await check('Spring first and next cursor, raw precision, ordered unique IDs',async()=>{
  const r=await fetch(api+'/api/v1/community/posts/feed?size=2');assert.equal(r.status,200);first=await r.json();assert(first.items.length>0)
  if(!first.hasNext)throw Error('insufficient existing data for next-cursor check')
  const cursor=first.nextCursor;const q=new URLSearchParams({size:'2',afterCreatedAt:cursor.createdAt,afterId:String(cursor.postId)})
  const r2=await fetch(api+'/api/v1/community/posts/feed?'+q);assert.equal(r2.status,200);const next=await r2.json();assert(next.items.length>0);assert(next.items.every(p=>!first.items.some(a=>a.id===p.id)));assert.equal(q.get('afterCreatedAt'),cursor.createdAt)
 })
 await check('Spring topic+type feed and production SSR/noJS count/preview',async()=>{
  const p=first.items[0],q=new URLSearchParams({topic:p.topic,type:p.type,size:'20'});const data=await(await fetch(api+'/api/v1/community/posts/feed?'+q)).json();assert(data.items.every(i=>i.type===p.type&&i.topic===p.topic))
  const ctx=await browser.newContext({javaScriptEnabled:false}),page=await ctx.newPage()
  try{assert.equal((await page.goto(base+'/community?'+q)).status(),200);const row=page.locator(`[data-feed-id="${p.id}"]`);assert(await row.isVisible());assert.equal(await row.locator('h3').innerText(),p.title);assert.equal(await row.locator('.feed-preview').innerText(),p.contentPreview);assert.equal(await row.locator('.post-comment-link').innerText(),'댓글 '+p.commentCount)}finally{await ctx.close()}
 })
 await check('Spring noJS detail/body/comment visible, metadata intact',async()=>{
  const all=await(await fetch(api+'/api/v1/community/posts/feed?size=20')).json();const chosen=all.items.find(p=>p.commentCount>0);assert(chosen,'existing commented post needed')
  const post=await(await fetch(api+'/api/v1/community/posts/'+chosen.id)).json(),comments=await(await fetch(api+`/api/v1/community/posts/${chosen.id}/comments?size=20`)).json();assert(comments.items.length)
  const ctx=await browser.newContext({javaScriptEnabled:false}),p=await ctx.newPage()
  try{assert.equal((await p.goto(base+'/community/posts/'+chosen.id)).status(),200);assert.equal(await p.locator('.community-post-body').innerText(),post.content);assert((await p.title()).includes(post.title));assert(await p.locator('#comment-'+comments.items[0].commentId).isVisible());assert((await p.locator('#comment-'+comments.items[0].commentId).innerText()).includes(comments.items[0].content))}finally{await ctx.close()}
 })
 await check('dev8443 reflects feed source / anonymous header / actual browser feed CORS',async()=>{
  const ctx=await browser.newContext(),p=await ctx.newPage();try{await p.goto('http://127.0.0.1:8443/community');await p.locator('.feed-navigation').waitFor();await p.locator('[data-feed-id]').first().waitFor();assert(await p.getByRole('heading',{name:'전체 베스트'}).isVisible());assert(await p.locator('.iyum-header').getByRole('link',{name:'로그인',exact:true}).isVisible());const reads=await p.evaluate(async api=>{const first=await fetch(api+'/api/v1/community/posts/feed?size=2',{credentials:'omit'});const body=await first.json();const q=new URLSearchParams({size:'2',afterCreatedAt:body.nextCursor.createdAt,afterId:String(body.nextCursor.postId)});const next=await fetch(api+'/api/v1/community/posts/feed?'+q,{credentials:'omit'});return{first:first.status,next:next.status,firstCount:body.items.length,nextCount:(await next.json()).items.length}},api);assert.deepEqual(reads,{first:200,next:200,firstCount:2,nextCount:2})}finally{await ctx.close()}
 })
}finally{await browser.close();await fs.writeFile(output+'/results.json',JSON.stringify({kind:'actual Spring, production SSR, read only',writes:0,results},null,2))}
