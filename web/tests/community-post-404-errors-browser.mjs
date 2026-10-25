// Production mock API: only contracted POST_NOT_FOUND is rewritten to HTTP404.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import http from 'node:http'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8451',out=process.env.COMMUNITY_ARTIFACT_DIR??'/private/tmp/iyum-feed-followup/error-contract'
await fs.mkdir(out,{recursive:true})
const error=code=>({code,message:'test server message',fieldErrors:[]})
let detailReads=0
const server=http.createServer((req,res)=>{
 assert.equal(req.headers.cookie,undefined);assert.equal(req.headers.authorization,undefined)
 const path=new URL(req.url,'http://mock').pathname,id=path.split('/').at(-1)
 if (/^\/api\/v1\/community\/posts\/\d+$/.test(path)) detailReads++
 if(id==='46'){setTimeout(()=>{if(!res.destroyed){res.writeHead(503);res.end()}},16000);return}
 let status=200,body={items:[],hasNext:false,nextCursor:null},type='application/json'
 if(id==='47') body={id:47,type:'QUESTION',topic:'HEARING_AID',title:'정상 상세 호출 측정',content:'실제 표시되는 정상 본문',likeCount: 0, liked: false, author:{memberId:123,nickname:'검증'},createdAt:'2026-10-17T00:00:00Z',updatedAt:'2026-10-17T00:00:00Z'}
 if(id==='41'){status=404;body=error('POST_NOT_FOUND')}
 if(id==='42'){status=502;body='<html>gateway</html>';type='text/html'}
 if(id==='43'){body='{damaged'}
 if(id==='44'){status=503;body=error('SERVER_ERROR')}
 if(id==='45'){status=404;body=error('OTHER_MISSING_RESOURCE')}
 res.writeHead(status,{'Content-Type':type});res.end(typeof body==='string'?body:JSON.stringify(body))
})
await new Promise(r=>server.listen(8856,'127.0.0.1',r))
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
try{
 for(const js of [false,true]){
  const ctx=await browser.newContext({javaScriptEnabled:js}),p=await ctx.newPage()
  await p.route(u=>u.pathname.startsWith('/api/v1/auth/'),r=>r.fulfill({status:401,json:error('AUTHENTICATION_REQUIRED')}))
  try{detailReads=0;const response=await p.goto(base+'/community/posts/47');assert.equal(response.status(),200);assert(await p.getByText('실제 표시되는 정상 본문',{exact:true}).isVisible());assert.equal(detailReads,Number(process.env.EXPECT_DETAIL_READS??2));results.push({name:'normal detail measured API count',js,count:detailReads,pass:true});console.log('PASS normal detail reads',detailReads,js)}catch(e){results.push({name:'normal detail count',js,pass:false,error:e.message});process.exitCode=1}finally{await ctx.close()}
 }
 for(const id of [41,42,43,44,45,46])for(const js of [false,true]){
 const ctx=await browser.newContext({javaScriptEnabled:js,viewport:{width:390,height:844}}),p=await ctx.newPage()
 try{await p.route(u=>u.pathname.startsWith('/api/v1/auth/'),r=>r.fulfill({status:401,json:error('AUTHENTICATION_REQUIRED')}));const r=await p.goto(base+'/community/posts/'+id,{timeout:45000})
 if(id===41){assert.equal(r.status(),404);assert((await p.locator('meta[name=robots]').first().getAttribute('content')).includes('noindex'));await p.screenshot({path:out+`/404-${js?'js':'nojs'}.png`,fullPage:true});assert(await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.'}).isVisible());assert(await p.getByRole('link',{name:'목록으로 돌아가기'}).isVisible())}
 else{assert.notEqual(r.status(),404);assert(await p.getByRole('heading',{name:'잠시 불러오지 못했어요.'}).isVisible());assert(await p.getByRole('button',{name:'다시 시도',exact:true}).isVisible());assert.equal(await p.getByRole('heading',{name:'게시글을 찾을 수 없어요.'}).count(),0)}
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));results.push({id,js,pass:true});console.log('PASS',id,js)
 }catch(e){results.push({id,js,pass:false,error:e.message});console.log('FAIL',id,js,e.message);process.exitCode=1}finally{await ctx.close()}
}}finally{await browser.close();await new Promise(r=>server.close(r));await fs.writeFile(out+'/results.json',JSON.stringify({kind:'mock public API, production 404 versus failures',results},null,2))}
