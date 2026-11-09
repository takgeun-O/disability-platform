import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.VISIBILITY_ARTIFACT_DIR??'/private/tmp/iyum-member-visibility/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-10-27T04:23:41.115818Z',member={memberId:7,nickname:'검증회원',email:'review@example.test',role:'ADMIN'}
const post=id=>({id,visibility:'VISIBLE',createdAt:stamp,type:'QUESTION',topic:'HEARING_AID',title:`검증 게시글 ${id}`,contentPreview:'정상 미리보기',author:{memberId:7,nickname:member.nickname},commentCount:3,likeCount:2,liked:true})
const comment=(id,pid=12)=>({ contentUpdatedAt: null,commentId:id,createdAt:stamp,visibility:'VISIBLE',content:`검증 댓글 ${id}`,updatedAt:stamp,likeCount:3,liked:false,post:{postId:pid,title:`검증 게시글 ${pid}`,type:'QUESTION',topic:'HEARING_AID'}})
const fail=code=>({code,message:'mock failure',fieldErrors:[]})
const gate=()=>{let release,arrive;return {arrived:new Promise(r=>{arrive=r}),response:new Promise(r=>{release=r}),release:v=>release(v),arrive:()=>arrive()}}
const button=(p,name)=>p.getByRole('button',{name,exact:true})
const postRow=(p,id)=>p.locator(`[data-member-post-id="${id}"]`),commentRow=(p,id)=>p.locator(`[data-member-comment-id="${id}"]`)
const count=async(p,selector,n)=>p.waitForFunction(({selector,n})=>document.querySelectorAll(selector).length===n,{selector,n})
async function setup(width=1440){
 const ctx=await browser.newContext({viewport:{width,height:1000}}),p=await ctx.newPage();p.setDefaultTimeout(7000)
 const s={posts:Array.from({length:12},(_,i)=>post(12-i)),comments:[comment(3),comment(2,11),comment(1,10)],hidden:new Set([11]),commentHidden:new Set([3]),calls:[],user:{...member},write:null,read:null,publicRead:null}
 const privatePost=x=>s.hidden.has(x.id)?{...x,type:null,topic:null,title:null,contentPreview:null,author:null,commentCount:null,likeCount:null,liked:null,visibility:'HIDDEN'}:{...x}
 const privateComment=x=>s.hidden.has(x.post.postId)||s.commentHidden.has(x.commentId)?{...x,visibility:s.hidden.has(x.post.postId)?'PARENT_HIDDEN':'HIDDEN',content:null,updatedAt:null,contentUpdatedAt:null,likeCount:null,liked:null,post:{postId:x.post.postId,title:null,type:null,topic:null}}:{...x}
 const page=(u,list,key,convert)=>{const all=list.filter(x=>!u.searchParams.has('afterId')||x[key]<Number(u.searchParams.get('afterId'))),items=all.slice(0,10).map(convert),hasNext=all.length>10;return{items,hasNext,nextCursor:hasNext?{createdAt:stamp,[key==='id'?'postId':'commentId']:items.at(-1)[key]}:null}}
 s.postPage=u=>page(u,s.posts,'id',privatePost)
 const adminPost=id=>{const x=s.posts.find(x=>x.id===id);return {postId:id,type:x.type,topic:x.topic,title:x.title,content:'전체 검증 본문',author:{...x.author,withdrawn:false},status:s.hidden.has(id)?'HIDDEN':'VISIBLE',createdAt:stamp,updatedAt:stamp,hiddenAt:s.hidden.has(id)?stamp:null,deletedAt:null}}
 await p.addInitScript(()=>{const original=window.fetch;window.__handled=0;window.fetch=async(url,options)=>{const r=await original(url,{...options,signal:undefined});const json=r.json.bind(r);r.json=async()=>{const v=await json();queueMicrotask(()=>setTimeout(()=>window.__handled++,0));return v};return r}})
 await ctx.route(u=>u.pathname.startsWith('/api/v1/'),async r=>{
  const u=new URL(r.request().url()),path=u.pathname,method=r.request().method();s.calls.push({path,method,query:u.search})
  if(path.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(path.endsWith('/auth/me'))return r.fulfill(s.user?{json:s.user}:{status:401,json:fail('AUTHENTICATION_REQUIRED')})
  if(path.endsWith('/logout')){s.user=null;return r.fulfill({status:204})}
  if(path.endsWith('/login')){s.user={...member,memberId:8,nickname:'새회원'};return r.fulfill({json:s.user})}
  if(path==='/api/v1/members/me')return r.fulfill({json:{...s.user,createdAt:stamp}})
  if(path.endsWith('/activity-summary'))return r.fulfill({json:{postCount:s.posts.length,commentCount:s.comments.length}})
  if(path==='/api/v1/members/me/posts')return s.read?s.read(r,u):r.fulfill({json:s.postPage(u)})
  if(path==='/api/v1/members/me/comments')return r.fulfill({json:page(u,s.comments,'commentId',privateComment)})
  if(path==='/api/v1/admin/posts')return r.fulfill({json:{items:s.posts.map(x=>({...adminPost(x.id),contentPreview:x.contentPreview})),page:0,size:20,totalElements:s.posts.length,totalPages:1,hasNext:false,hasPrevious:false}})
  if(path.startsWith('/api/v1/admin/posts/')){
   const parts=path.split('/'),id=Number(parts[5]),isComment=parts[6]==='comments'
   if(method==='POST'){assert.equal(r.request().headers()['x-csrf-token'],'mock-only');const hidden=parts.at(-1)==='hide',set=isComment?s.commentHidden:s.hidden,target=isComment?Number(parts[7]):id;if(hidden)set.add(target);else set.delete(target);return r.fulfill({json:{postId:id,...(isComment?{commentId:target}:{}),hidden,hiddenAt:hidden?stamp:null,changed:true}})}
   if(isComment){const items=s.comments.filter(c=>c.post.postId===id).map(c=>({ contentUpdatedAt: c.contentUpdatedAt,commentId:c.commentId,content:c.content,author:{...member,withdrawn:false},status:s.commentHidden.has(c.commentId)?'HIDDEN':'VISIBLE',createdAt:stamp,updatedAt:stamp,hiddenAt:s.commentHidden.has(c.commentId)?stamp:null,deletedAt:null}));return r.fulfill({json:{postId:id,postStatus:s.hidden.has(id)?'HIDDEN':'VISIBLE',items,page:0,size:20,totalElements:items.length,totalPages:items.length?1:0,hasNext:false,hasPrevious:false}})}
   return r.fulfill({json:adminPost(id)})
  }
  if(path.startsWith('/api/v1/community/posts/')){
   if(method==='DELETE'){
    assert.equal(r.request().headers()['x-csrf-token'],'mock-only');if(s.write)return s.write(r,u)
    const parts=path.split('/'),id=Number(parts[5]);if(parts[6]==='comments')s.comments=s.comments.filter(c=>c.commentId!==Number(parts[7]));else{s.posts=s.posts.filter(x=>x.id!==id);s.comments=s.comments.filter(c=>c.post.postId!==id)}
    return r.fulfill({status:204})
   }
   if(s.publicRead)return s.publicRead(r,u)
   return r.fulfill({json:{items:[{ contentUpdatedAt: null,commentId:100,content:'늦은 댓글 내용',createdAt:stamp,updatedAt:stamp,likeCount:0,liked:false,author:member}],hasNext:false,nextCursor:null}})
  }
  return r.fulfill({status:404,json:fail('NOT_FOUND')})
 })
 return{ctx,p,s}
}
async function open(p){await p.goto(base+'/mypage');await count(p,'[data-member-post-id]',10)}
async function comments(p){await p.getByRole('tab',{name:/내 댓글/}).click();await count(p,'[data-member-comment-id]',3)}
async function deleteMenu(p,id){await commentRow(p,id).getByRole('button',{name:'숨김 댓글 관리',exact:true}).click();await commentRow(p,id).getByRole('menuitem',{name:'댓글 삭제',exact:true}).click()}
async function run(name,fn){try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.message});console.log('FAIL',name,e.message);process.exitCode=1;const p=browser.contexts().at(-1)?.pages()[0];if(p)await p.screenshot({path:`${out}/failure-${results.length}.png`}).catch(()=>{})}finally{for(const c of browser.contexts())await c.close()}}
for(const width of [1440,390,320])await run(`mixed redaction, links, readonly counts and hidden cursor ${width}`,async()=>{
 const{p,s}=await setup(width);s.hidden.add(3);await open(p)
 const row=postRow(p,11);assert.match(await row.innerText(),/운영 정책/);assert.equal(await row.locator('a').count(),0);assert.equal(await row.locator('.post-like-count,.post-comment-link').count(),0);assert.equal(await row.locator('time').count(),1)
 assert(await postRow(p,12).getByRole('link',{name:'검증 게시글 12'}).isVisible())
 await button(p,'더 보기').click();await count(p,'[data-member-post-id]',12);assert(s.calls.some(c=>c.query.includes('afterId=3')&&c.query.includes('115818')))
 await p.screenshot({path:`${out}/mixed-posts-${width}.png`,fullPage:true});await comments(p)
 assert.match(await commentRow(p,3).innerText(),/숨겨진 댓글/);assert.match(await commentRow(p,2).innerText(),/원문 게시글 숨김으로/)
 for(const id of [3,2]){assert.equal(await commentRow(p,id).locator('a,.mypage-comment-likes').count(),0);assert.equal(await commentRow(p,id).locator('time').count(),1)}
 assert(await commentRow(p,1).locator('.mypage-comment-likes').isVisible());assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 await p.screenshot({path:`${out}/mixed-comments-${width}.png`,fullPage:true});assert(!s.calls.some(c=>c.path.startsWith('/api/v1/admin/')||c.path.startsWith('/api/v1/community/')))
})
await run('hidden post delete: cancel, confirmed 204, list/count refresh without detail GET',async()=>{
 const{p,s}=await setup();await open(p);const row=postRow(p,11)
 await row.getByRole('button',{name:'게시글 관리'}).click();assert.equal(await row.getByRole('menuitem',{name:'수정'}).count(),0);await row.getByRole('menuitem',{name:'삭제'}).click();assert(!await p.getByRole('dialog').innerText().then(x=>x.includes('검증 게시글 11')));await button(p,'취소').click();assert.equal(s.calls.filter(c=>c.method==='DELETE').length,0)
 await row.getByRole('button',{name:'게시글 관리'}).click();await row.getByRole('menuitem',{name:'삭제'}).click();await button(p,'삭제하기').click();await row.waitFor({state:'detached'});await p.getByRole('tab',{name:'내 게시글 11',exact:true}).waitFor();assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1);assert(!s.calls.some(c=>c.method==='GET'&&c.path.includes('/community/')))
})
for(const id of [3,2])await run(`hidden comment ${id} delete and aggregate refresh`,async()=>{
 const{p,s}=await setup();await open(p);await comments(p);await deleteMenu(p,id);await button(p,'취소').click();assert.equal(s.calls.filter(c=>c.method==='DELETE').length,0)
 await deleteMenu(p,id);await button(p,'댓글 삭제하기').click();await commentRow(p,id).waitFor({state:'detached'});await p.getByRole('tab',{name:'내 댓글 2',exact:true}).waitFor();await p.waitForFunction(()=>document.activeElement?.id==='my-tab-comments');assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1);assert(!s.calls.some(c=>c.method==='GET'&&c.path.includes('/community/')))
})
for(const uncertain of [false,true])await run(`hidden comment delete ${uncertain?'uncertain':'denied'} retains item and avoids replay`,async()=>{
 const{p,s}=await setup();s.write=r=>r.fulfill({status:uncertain?500:403,json:fail(uncertain?'INTERNAL_SERVER_ERROR':'NOT_COMMENT_AUTHOR')});await open(p);await comments(p);await deleteMenu(p,3);await button(p,'댓글 삭제하기').click();await p.getByRole('dialog').getByRole('alert').waitFor();assert.equal(await commentRow(p,3).count(),1);assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1)
 if(uncertain){await button(p,'목록 다시 확인').click();await count(p,'[data-member-comment-id]',3);assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1)}
})
await run('stateful admin hide and restore feed author views; parent precedence remains',async()=>{
 const{p,s}=await setup();s.hidden.clear();s.commentHidden.add(3)
 await open(p);await postRow(p,12).getByRole('button',{name:/^댓글 /}).click();await p.getByText('늦은 댓글 내용',{exact:true}).waitFor()
 async function moderate(name){await button(p,name).click();await p.getByLabel('처리 사유 (필수)').fill('모의 정책 검증');await button(p,name.replace(/^(게시글|댓글) /,'')+' 실행').click();await p.getByRole('dialog').waitFor({state:'hidden'});await p.locator('.admin-post-detail[aria-busy="false"]').waitFor()}
 await p.getByRole('link',{name:'관리자',exact:true}).first().click();await p.getByRole('link',{name:'검증 게시글 12',exact:true}).click();await button(p,'게시글 숨김 처리').waitFor();await moderate('게시글 숨김 처리')
 await p.goBack();await p.waitForURL(u=>u.pathname==='/admin/posts');await p.goBack();await p.waitForURL(u=>u.pathname==='/mypage');await postRow(p,12).getByText('운영 정책에 따라 숨겨진 게시글입니다.').waitFor();assert.match(await postRow(p,12).innerText(),/숨겨진 게시글/);assert.equal(await postRow(p,12).locator('.mypage-inline-comments').count(),0);await comments(p);assert.match(await commentRow(p,3).innerText(),/원문 게시글 숨김으로/)
 await p.goto(base+'/admin/posts/12');await button(p,'게시글 숨김 해제').waitFor();await moderate('게시글 숨김 해제');await p.getByRole('link',{name:'마이페이지',exact:true}).first().click();await count(p,'[data-member-post-id]',10);assert(await postRow(p,12).getByRole('link',{name:'검증 게시글 12'}).isVisible());await comments(p);assert.match(await commentRow(p,3).innerText(),/운영 정책에 따라 숨겨진 댓글/)
 await p.goto(base+'/admin/posts/12');await button(p,'댓글 숨김 해제').waitFor();await moderate('댓글 숨김 해제');await p.getByRole('link',{name:'마이페이지',exact:true}).first().click();await count(p,'[data-member-post-id]',10);await comments(p);assert.match(await commentRow(p,3).innerText(),/검증 댓글 3/);assert.equal(s.calls.filter(c=>c.method==='POST'&&c.path.startsWith('/api/v1/admin/')).length,3)
})
await run('late inline response cannot resurrect hidden post after member refresh',async()=>{
 const{p,s}=await setup(),g=gate();s.publicRead=async r=>{g.arrive();await g.response;await r.fulfill({json:{items:[{ contentUpdatedAt: null,commentId:100,content:'늦은 댓글 내용',createdAt:stamp,updatedAt:stamp,likeCount:0,liked:false,author:member}],hasNext:false,nextCursor:null}})}
 await open(p);await postRow(p,12).getByRole('button',{name:/^댓글 /}).click();await g.arrived
 // A successful deletion of another post invokes the real private-list invalidation path.
 s.hidden.add(12);await postRow(p,11).getByRole('button',{name:'게시글 관리'}).click();await postRow(p,11).getByRole('menuitem',{name:'삭제'}).click();await button(p,'삭제하기').click();await postRow(p,12).getByText('운영 정책에 따라 숨겨진 게시글입니다.').waitFor()
 const before=await p.evaluate(()=>window.__handled);g.release();await p.waitForFunction(n=>window.__handled>n,before);assert.equal(await p.getByText('늦은 댓글 내용',{exact:true}).count(),0);assert.equal(await postRow(p,12).locator('.mypage-inline-comments,a').count(),0)
})
await run('logout while private read waits ignores late visible response',async()=>{
 const{p,s}=await setup(),g=gate();s.read=async(r,u)=>{const payload=s.postPage(u);g.arrive();await g.response;await r.fulfill({json:payload})};await p.goto(base+'/mypage');await g.arrived;await button(p,'로그아웃').first().click();await p.getByText('로그인이 필요해요',{exact:true}).waitFor();const before=await p.evaluate(()=>window.__handled);g.release();await p.waitForFunction(n=>window.__handled>n,before);assert.equal(await p.locator('[data-member-post-id]').count(),0)
})
await run('older private page cannot merge a visible snapshot over confirmed hidden state',async()=>{
 const{p,s}=await setup(),g=gate();await open(p)
 s.read=async(r,u)=>{const payload=s.postPage(u);if(u.searchParams.has('afterId')){g.arrive();await g.response}await r.fulfill({json:payload})}
 await button(p,'더 보기').click();await g.arrived;s.hidden.add(12)
 await postRow(p,11).getByRole('button',{name:'게시글 관리'}).click();await postRow(p,11).getByRole('menuitem',{name:'삭제'}).click();await button(p,'삭제하기').click();await postRow(p,12).getByText('운영 정책에 따라 숨겨진 게시글입니다.').waitFor()
 const before=await p.evaluate(()=>window.__handled);g.release();await p.waitForFunction(n=>window.__handled>n,before)
 assert.equal(await postRow(p,12).locator('a,.post-comment-link').count(),0);assert.equal(await p.locator('[data-member-post-id]').count(),10)
})
await run('all hidden and genuinely empty lists remain distinct',async()=>{
 const{p,s}=await setup();s.posts=s.posts.slice(0,2);s.posts.forEach(x=>s.hidden.add(x.id));s.comments=s.comments.slice(0,2);await p.goto(base+'/mypage');await count(p,'[data-member-post-id]',2)
 assert.equal(await p.getByText('운영 정책에 따라 숨겨진 게시글입니다.',{exact:true}).count(),2);assert.equal(await p.getByText('아직 작성한 게시글이 없어요.').count(),0)
 await p.getByRole('tab',{name:/내 댓글/}).click();await count(p,'[data-member-comment-id]',2);assert.equal(await p.locator('.mypage-comment-likes').count(),0)
 s.posts=[];s.comments=[];await p.reload();await p.getByText('아직 작성한 게시글이 없어요.').waitFor();await p.getByRole('tab',{name:/내 댓글/}).click();await p.getByText('아직 작성한 댓글이 없어요.').waitFor()
})
await run('keyboard more focuses hidden row management without a forbidden detail link',async()=>{
 const{p,s}=await setup();s.hidden.add(2);await open(p);await button(p,'더 보기').focus();await p.keyboard.press('Enter');await count(p,'[data-member-post-id]',12)
 await p.waitForFunction(()=>document.activeElement?.closest('[data-member-post-id]')?.getAttribute('data-member-post-id')==='2')
 assert.equal(await postRow(p,2).locator('a').count(),0);assert.equal(await p.evaluate(()=>document.activeElement?.getAttribute('aria-label')),'게시글 관리')
})
for(const width of [1440,390,320])await run(`hidden comment menu, exact time and compact consecutive rows ${width}`,async()=>{
 const{p,s}=await setup(width);s.comments=Array.from({length:8},(_,i)=>comment(8-i,i%2?11:12));s.comments.forEach(c=>s.commentHidden.add(c.commentId))
 await open(p);await p.getByRole('tab',{name:/내 댓글/}).click();await count(p,'[data-member-comment-id]',8)
 assert.equal(await p.locator('.mypage-hidden-comment').count(),8)
 assert.equal(await p.getByRole('button',{name:'댓글 삭제',exact:true}).count(),0)
 assert.equal(await p.locator('.mypage-comment-likes,.mypage-comment-link').count(),0)
 assert.equal(await p.getByText('원문 게시글 숨김으로 표시가 제한된 댓글입니다.',{exact:true}).count(),4)
 const row=commentRow(p,8),control=row.getByRole('button',{name:'숨김 댓글 관리'}),item=row.getByRole('menuitem',{name:'댓글 삭제'})
 await control.focus();await p.keyboard.press('ArrowDown');await item.waitFor();assert(await item.evaluate(e=>e===document.activeElement))
 await p.keyboard.press('Escape');assert.equal(await control.getAttribute('aria-expanded'),'false');assert(await control.evaluate(e=>e===document.activeElement))
 await control.click();await item.waitFor();await p.getByRole('heading',{name:'내 활동',exact:true}).click();assert.equal(await row.getByRole('menu').count(),0)
 await control.focus();await p.keyboard.press('Enter');await item.waitFor();await p.screenshot({path:`${out}/hidden-menu-${width}.png`,fullPage:true})
 const box=await row.getByRole('menu').boundingBox();assert(box.x>=0&&box.x+box.width<=width)
 await p.keyboard.press('Enter');const dialog=p.getByRole('dialog');await dialog.waitFor();assert.match(await dialog.innerText(),/내용을 확인할 수 없는 댓글입니다. 삭제하면 되돌릴 수 없습니다./);assert.match(await dialog.innerText(),/2026.10.27. 13:23:41 \(한국 시간\)/);assert.doesNotMatch(await dialog.innerText(),/#8|검증 댓글|검증 게시글/)
 assert(await button(p,'취소').evaluate(e=>e===document.activeElement));await p.keyboard.press('Shift+Tab');assert(await button(p,'댓글 삭제하기').evaluate(e=>e===document.activeElement));await p.keyboard.press('Tab');assert(await button(p,'취소').evaluate(e=>e===document.activeElement))
 await p.screenshot({path:`${out}/hidden-confirm-${width}.png`,fullPage:true});await p.keyboard.press('Escape');await dialog.waitFor({state:'detached'});assert(await control.evaluate(e=>e===document.activeElement));assert.equal(s.calls.filter(c=>c.method==='DELETE').length,0)
 const time=row.getByRole('button',{name:/정확한 작성 시각 보기/});await time.focus();await p.keyboard.press('Enter');await row.locator('[popover]:popover-open').waitFor();assert.match(await row.locator('[popover]:popover-open').innerText(),/13:23:41/);await p.keyboard.press('Escape');await time.click();await row.locator('[popover]:popover-open').waitFor();await p.keyboard.press('Escape')
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/hidden-consecutive-${width}.png`,fullPage:true})
})
await run('hidden delete pending lock and late failure after approved browser navigation',async()=>{
 const{p,s}=await setup(),g=gate();await p.goto(base+'/');await p.getByRole('link',{name:'마이페이지',exact:true}).first().click();await count(p,'[data-member-post-id]',10);await comments(p)
 s.write=async r=>{g.arrive();await g.response;await r.fulfill({status:403,json:fail('NOT_COMMENT_AUTHOR')})}
 await deleteMenu(p,3);await button(p,'댓글 삭제하기').click();await g.arrived
 assert(await button(p,'삭제 중…').isDisabled());await p.keyboard.press('Enter');await p.keyboard.press('Escape');assert(await p.getByRole('dialog').isVisible());assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1)
 await p.goBack();await p.waitForURL(u=>u.pathname==='/');const before=await p.evaluate(()=>window.__handled);g.release();await p.waitForFunction(n=>window.__handled>n,before)
 assert.equal(await p.getByRole('dialog').count(),0);assert.equal(await p.getByText('mock failure',{exact:true}).count(),0);assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1)
})
await run('hidden comment general 404 stays an error and never automatically repeats deletion',async()=>{
 const{p,s}=await setup();s.write=r=>r.fulfill({status:404,json:fail('NOT_FOUND')});await open(p);await comments(p);await deleteMenu(p,3);await button(p,'댓글 삭제하기').click();await p.getByRole('dialog').getByRole('alert').waitFor()
 assert.equal(await commentRow(p,3).count(),1);assert.equal(await button(p,'댓글 삭제하기').count(),0);await button(p,'목록 다시 확인').click();await p.getByRole('dialog').waitFor({state:'detached'});await commentRow(p,3).waitFor();assert.equal(s.calls.filter(c=>c.method==='DELETE').length,1)
})
await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));console.log(`${results.filter(x=>x.pass).length}/${results.length}`)
