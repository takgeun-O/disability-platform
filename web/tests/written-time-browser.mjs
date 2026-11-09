// Production Next + synthetic API + Playwright clock. No real accounts or DB writes.
import http from 'node:http'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8466',out=process.env.TIME_ARTIFACT_DIR??'/private/tmp/iyum-written-time'
await fs.mkdir(out,{recursive:true})
const now=Date.parse('2026-10-27T04:24:00Z'),recent='2026-10-27T04:23:30.115818Z',old='2026-10-20T04:24:30.115818Z'
const member={memberId:321,nickname:'검토회원',email:'review@example.test',role:'USER'}
const post={id:7,type:'QUESTION',topic:'HEARING_AID',title:'작성 시각 표시 검토 게시글',content:'원문 내용입니다.\n'.repeat(12),contentPreview:'목록의 본문 미리보기입니다.',createdAt:recent,updatedAt:recent,author:{memberId:321,nickname:member.nickname},commentCount:1,likeCount:3,liked:true}
const comment={ contentUpdatedAt: null,commentId:11,content:'작성 시각을 확인하는 댓글입니다.',createdAt:old,updatedAt:recent,author:post.author,likeCount:2,liked:false}
const page=items=>({items,hasNext:false,nextCursor:null})
function read(path){
 if(path.endsWith('/comments'))return page([comment])
 if(path.endsWith('/feed'))return page([{...post,visibility:"VISIBLE"}])
 return post
}
const server=http.createServer((req,res)=>{assert(!req.headers.cookie);res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(read(new URL(req.url,'http://mock').pathname)))})
await new Promise(r=>server.listen(8858,'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[],errors=[]
async function check(name,fn){try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:String(e)});console.error('FAIL',name,e);process.exitCode=1}}
async function fixture(width=1440,js=true){
 const ctx=await browser.newContext({viewport:{width,height:900},hasTouch:width<600,javaScriptEnabled:js,timezoneId:'America/Los_Angeles'}),p=await ctx.newPage(),calls=[]
 p.setDefaultTimeout(8000)
 p.on('pageerror',e=>errors.push(String(e)));p.on('console',m=>{if(m.type()==='error'&&/hydration|hydrated|mismatch/i.test(m.text()))errors.push(m.text())})
 if(js)await p.clock.install({time:new Date(now)})
 await p.route(u=>u.origin==='http://127.0.0.1:8082',r=>{
  const path=new URL(r.request().url()).pathname;calls.push({path,method:r.request().method()})
  if(path.endsWith('/auth/me'))return r.fulfill({json:member})
  if(path.endsWith('/members/me'))return r.fulfill({json:{...member,createdAt:'2026-09-30T16:00:00Z'}})
  if(path.endsWith('/activity-summary'))return r.fulfill({json:{postCount:1,commentCount:1}})
  if(path.endsWith('/members/me/posts'))return r.fulfill({json:page([{...post,visibility:"VISIBLE"}])})
  if(path.endsWith('/members/me/comments'))return r.fulfill({json:page([{ contentUpdatedAt: null,visibility:"VISIBLE",commentId:comment.commentId,content:comment.content,createdAt:comment.createdAt,updatedAt:comment.updatedAt,likeCount:2,liked:false,post:{postId:post.id,title:post.title,type:post.type,topic:post.topic}}])})
  return r.fulfill({json:read(path)})
 })
 return {ctx,p,calls}
}
const text=p=>p.locator('.written-time time')
async function label(locator,value){await locator.filter({hasText:value}).waitFor();assert.equal(await locator.textContent(),value)}
async function shot(p,n){await p.screenshot({path:out+'/'+n+'.png',fullPage:true})}
try {
 for(const width of [1440,390,320])await check(`all five surfaces, exact time keyboard/touch and bounds ${width}`,async()=>{
  const {ctx,p}=await fixture(width)
  try {
   await p.goto(base+'/community/posts');await label(text(p).first(),'방금 전')
   const trigger=p.locator('.written-time-trigger').first();const url=p.url()
   if(width===1440){await trigger.focus();await p.keyboard.press('Enter')}else await trigger.tap()
   const note=p.locator('.written-time-exact:popover-open');await note.waitFor();assert((await note.textContent()).includes('2026.10.27. 13:23:30 (한국 시간)'))
   const bounds=await note.boundingBox();assert(bounds.x>=0&&bounds.x+bounds.width<=width)
   assert.equal(p.url(),url);await shot(p,'popover-'+width)
   await p.keyboard.press('Escape');assert.equal(await p.locator(':popover-open').count(),0)
   if(width===1440)assert(await trigger.evaluate(e=>e===document.activeElement))
   await p.getByRole('link',{name:post.title,exact:true}).click();await p.waitForURL(u=>u.pathname==='/community/posts/7')
   await label(p.locator('.community-post-meta .written-time time'),'방금 전')
   await label(p.locator('#comment-11 .written-time time'),'6일 전')
   assert(await p.getByText('수정됨',{exact:true}).isVisible());await shot(p,'detail-'+width)
   await p.goto(base+'/mypage');await label(p.locator('[data-member-post-id] .written-time time'),'방금 전')
   assert(await p.getByText('2026년 10월',{exact:false}).isVisible());await shot(p,'my-posts-'+width)
   await p.getByRole('tab',{name:/내 댓글/}).click();await label(p.locator('[data-member-comment-id] .written-time time'),'6일 전')
   assert.equal(await p.locator('.written-time [aria-live]').count(),0)
   assert.equal(await p.locator('.written-time-trigger').first().getAttribute('type'),'button')
   await shot(p,'my-comments-'+width)
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  }finally{await ctx.close()}
 })
 await check('minute transitions, resume, no API calls/remount/focus/scroll change',async()=>{
  const {ctx,p,calls}=await fixture()
  try {
   await p.goto(base+'/community/posts/7');await label(text(p).first(),'방금 전');await label(p.locator('#comment-11 .written-time time'),'6일 전')
   const input=p.getByLabel('댓글 남기기',{exact:true});await input.fill('시간 갱신 중 유지할 댓글 초안');await input.focus()
   const handle=await input.elementHandle(),scroll=await p.evaluate(()=>scrollY),n=calls.length
   await p.clock.runFor(60_000)
   await label(text(p).first(),'1분 전');await label(p.locator('#comment-11 .written-time time'),'2026.10.20.')
   assert.equal(calls.length,n);assert(await handle.evaluate(e=>e.isConnected&&e===document.activeElement));assert.equal(await input.inputValue(),'시간 갱신 중 유지할 댓글 초안')
   assert.equal(await p.evaluate(()=>scrollY),scroll)
   await p.evaluate(()=>{Object.defineProperty(document,'visibilityState',{configurable:true,get:()=> 'hidden'});document.dispatchEvent(new Event('visibilitychange'))})
   await p.clock.setSystemTime(new Date(now+7_200_000))
   await label(text(p).first(),'1분 전')
   await p.evaluate(()=>{Object.defineProperty(document,'visibilityState',{configurable:true,get:()=> 'visible'});document.dispatchEvent(new Event('visibilitychange'))})
   await label(text(p).first(),'2시간 전');assert.equal(calls.length,n)
   assert(await handle.evaluate(e=>e===document.activeElement));assert.equal(await p.evaluate(()=>scrollY),scroll)
   await shot(p,'clock-after-resume')
  }finally{await ctx.close()}
 })
 await check('large future exact label fits 320px without overlap',async()=>{
  const {ctx,p}=await fixture(320)
  try {
   post.createdAt='2026-10-28T04:23:30.115818Z';post.updatedAt=post.createdAt
   await p.goto(base+'/community/posts');await label(text(p).first(),'2026.10.28. 13:23:30 (한국 시간)')
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await shot(p,'future-320')
  } finally {post.createdAt=recent;post.updatedAt=recent;await ctx.close()}
 })
 await check('no JavaScript public dates/body/exact popover and comment links remain usable',async()=>{
  const {ctx,p}=await fixture(390,false)
  try{
   const response=await p.goto(base+'/community/posts/7');assert.equal(response.status(),200)
   await label(text(p).first(),'2026.10.27.');await label(p.locator('#comment-11 .written-time time'),'2026.10.20.')
   assert(await p.getByRole('heading',{name:post.title}).isVisible());assert(await p.getByText(comment.content,{exact:true}).isVisible())
   await p.locator('.written-time-trigger').first().tap();await p.locator('.written-time-exact:popover-open').waitFor();await shot(p,'nojs')
   const html=await response.text();assert(html.includes(recent));assert(!html.includes('suppressHydrationWarning'))
   await p.keyboard.press('Escape');await p.goto(base+'/community/posts')
   await p.locator('a[href*="#comments-title"]').click();await p.waitForURL(u=>u.hash==='#comments-title')
   assert(await p.locator('#comments-title').isVisible())
  }finally{await ctx.close()}
 })
 assert.deepEqual(errors,[])
}finally{
 await fs.writeFile(out+'/results.json',JSON.stringify({mocked:true,results,errors},null,2));await browser.close();await new Promise(r=>server.close(r))
}
