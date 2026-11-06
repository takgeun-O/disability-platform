import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8479",
  out =
    process.env.ADMIN_DETAIL_ARTIFACT_DIR ?? "/private/tmp/iyum-admin-detail/browser"
await fs.mkdir(out, { recursive: true })
const browser = await chromium.launch({ channel: "chrome", headless: true }),
  results = []
const member = {
    memberId: 7,
    email: "admin@example.test",
    nickname: "검증관리자",
    role: "ADMIN",
  },
  stamp = "2026-11-01T00:00:00.123456Z"
const item = (id, status = "VISIBLE") => ({
  postId: id,
  type: "QUESTION",
  topic: "HEARING_AID",
  title: `검증 게시글 ${id}`,
  contentPreview: "본문",
  author: {
    memberId: 8,
    nickname: status === "DELETED" ? "탈퇴한 회원" : "검증작성자",
    withdrawn: status === "DELETED",
  },
  status,
  createdAt: stamp,
  updatedAt: stamp,
  hiddenAt: status === "HIDDEN" ? stamp : null,
  deletedAt: status === "DELETED" ? stamp : null,
})
const payload = (page = 0) => ({
  items: page ? [item(4)] : [item(1), item(2, "HIDDEN"), item(3, "DELETED")],
  page,
  size: 20,
  totalElements: 21,
  totalPages: 2,
  hasNext: page === 0,
  hasPrevious: page > 0,
})
const fail = (code) => ({ code, message: "mock error", fieldErrors: [] })
const gate = () => {
  let release, arrive
  return {
    response: new Promise((r) => (release = r)),
    arrived: new Promise((r) => (arrive = r)),
    release: () => release(),
    arrive: () => arrive(),
  }
}
async function setup(role = "ADMIN", width = 1440) {
  const context = await browser.newContext({
      viewport: { width, height: 900 },
    }),
    p = await context.newPage()
  p.setDefaultTimeout(8000)
  const state = {
    member: role ? { ...member, role } : null,
    calls: [],
    read: null,
    detail: null,
    comments: null,
    me: null,
    errors: [],
  }
  p.on("pageerror", (e) => state.errors.push(e.message))
  await context.route("http://127.0.0.1:8082/**", async (r) => {
    const u = new URL(r.request().url())
    state.calls.push({
      path: u.pathname,
      search: u.search,
      method: r.request().method(),
    })
    if (u.pathname.endsWith("/auth/me"))
      return state.me
        ? state.me(r)
        : r.fulfill(
            state.member
              ? { json: state.member }
              : { status: 401, json: fail("AUTHENTICATION_REQUIRED") },
          )
    if (u.pathname.endsWith("/csrf"))
      return r.fulfill({
        json: { headerName: "X-CSRF-TOKEN", token: "mock-only" },
      })
    if (u.pathname.endsWith("/login")) {
      state.member = state.loginMember ?? { ...member };
      return r.fulfill({ json: state.member });
    }
    if (u.pathname.endsWith("/logout")) {
      state.member = null
      return r.fulfill({ status: 204 })
    }
    const match=u.pathname.match(/^\/api\/v1\/admin\/posts\/(\d+)(\/comments)?$/)
    if(match) return match[2] ? state.comments ? state.comments(r,u) : r.fulfill({json:commentPage(Number(match[1]),Number(u.searchParams.get('page')))}) : state.detail ? state.detail(r,u) : r.fulfill({json:detail(Number(match[1]))})
    if (u.pathname === "/api/v1/admin/posts")
      return state.read
        ? state.read(r, u)
        : r.fulfill({ json: payload(Number(u.searchParams.get("page"))) })
    return r.fulfill({ status: 404, json: fail("NOT_FOUND") })
  })
  return { context, p, state }
}
const ready = (p) => p.locator('.admin-results[aria-busy="false"]').waitFor()
async function run(name, fn) {
  try {
    await fn()
    results.push({ name, passed: true })
    console.log("PASS", name)
  } catch (e) {
    results.push({ name, passed: false, error: e.message })
    console.log("FAIL", name, e.message)
  }
}

const detail=id=>({...item(id,id===2?'HIDDEN':id===3?'DELETED':'VISIBLE'),content:'전체 본문\n<script>안전한 일반 텍스트</script>\n'+'긴본문'.repeat(100)})
const commentPage=(postId,page=0)=>({postId,postStatus:postId===2?'HIDDEN':'VISIBLE',items:[{ contentUpdatedAt: null,...item(1),commentId:page*20+1,content:'댓글 전체 본문 '+ '긴문자'.repeat(100)}],page,size:20,totalElements:21,totalPages:2,hasNext:page===0,hasPrevious:page>0})
const commentReady=p=>p.locator('.admin-comment-results[aria-busy="false"]').waitFor()
const commentReads=s=>s.calls.filter(c=>c.path.endsWith('/comments'))
const flush=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
await run('comment body edit date and entity update date are separate',async()=>{
 const {context,p,state}=await setup()
 state.comments=r=>{const body=commentPage(2);body.items.push({...body.items[0],commentId:2,contentUpdatedAt:'2026-11-02T01:02:03.123456Z',updatedAt:'2026-11-05T03:00:00Z'});return r.fulfill({json:body})}
 await p.goto(base+'/admin/posts/2');await commentReady(p)
 const rows=p.locator('.admin-comment-list > li')
 assert.equal(await rows.first().getByText('기록 없음',{exact:true}).count(),1)
 const meta=rows.nth(1).locator('.admin-content-meta')
 assert.match(await meta.textContent(),/본문 수정.*2026.11.02. 10:02:03/)
 assert.match(await meta.textContent(),/최종 변경.*2026.11.05. 12:00:00/)
 await p.screenshot({path:out+'/content-time-admin.png',fullPage:true});await context.close()
})
await run('list conditions survive title navigation, direct detail and reload',async()=>{
 const {context,p}=await setup()
 await p.goto(base+'/admin/posts?status=HIDDEN&keyword=검색&page=1');await ready(p)
 assert.equal(await p.getByRole('navigation',{name:'관리자 메뉴'}).getByRole('link',{name:'게시글 관리',exact:true}).getAttribute('aria-current'),'page')
 await p.getByRole('link',{name:'검증 게시글 4',exact:true}).click();await commentReady(p)
 assert(await p.locator('.admin-post-detail').getByText('검증 게시글 4',{exact:true}).isVisible())
 await p.getByRole('link',{name:'← 목록으로'}).click();await ready(p)
 assert.equal(await p.getByLabel('처리 상태',{exact:true}).inputValue(),'HIDDEN')
 assert.equal(await p.getByLabel('게시글 제목 검색',{exact:true}).inputValue(),'검색')
 assert.equal(await p.getByLabel('페이지 바로 이동',{exact:true}).inputValue(),'2')
 for(const id of [1,2,3]){await p.goto(base+'/admin/posts/'+id);await commentReady(p);assert(await p.locator('.admin-post-detail').getByText('<script>',{exact:false}).isVisible());assert.equal(await p.locator('.admin-post-detail script').count(),0)}
 await p.reload();await commentReady(p);assert(await p.locator('.admin-post-detail').getByText('탈퇴한 회원',{exact:false}).isVisible())
 assert.equal(await p.getByRole('link',{name:'← 목록으로'}).getAttribute('href'),'/admin/posts')
 await context.close()
})
await run('comment filters, codepoint search, paging focus, parent vs child status',async()=>{
 const {context,p,state}=await setup();await p.goto(base+'/admin/posts/2');await commentReady(p)
 assert(await p.getByText('게시글이 숨겨져 있어 댓글도 일반 사용자에게 표시되지 않습니다.',{exact:false}).isVisible())
 assert.equal(await p.locator('.admin-comment-list .admin-badge').textContent(),'정상')
 await p.getByLabel('댓글 본문 검색',{exact:true}).fill('  본문  내부  ')
 assert.equal(commentReads(state).length,1)
 await p.getByRole('button',{name:'댓글 검색',exact:true}).click();await commentReady(p)
 assert.equal(new URLSearchParams(commentReads(state).at(-1).search).get('keyword'),'본문  내부')
 await p.getByLabel('댓글 상태',{exact:true}).selectOption('DELETED');await commentReady(p)
 const g=gate();state.comments=async(r,u)=>{g.arrive();await g.response;await r.fulfill({json:commentPage(2,Number(u.searchParams.get('page')))})}
 const input=p.getByLabel('페이지 바로 이동',{exact:true});await input.fill('2');await input.press('Enter');await g.arrived
 const n=commentReads(state).length;await input.press('Enter');assert.equal(commentReads(state).length,n)
 g.release();await commentReady(p);assert(await input.evaluate(e=>e===document.activeElement));assert.equal(await input.inputValue(),'2')
 state.comments=null;await p.getByRole('button',{name:'댓글 검색 초기화'}).click();await commentReady(p)
 assert.equal(new URLSearchParams(commentReads(state).at(-1).search).get('page'),'0')
 await context.close()
})
await run('empty, search none and out-of-range are distinct without automatic loops',async()=>{
 const {context,p,state}=await setup();state.comments=r=>r.fulfill({json:{...commentPage(1),items:[],totalElements:0,totalPages:0,hasNext:false}})
 await p.goto(base+'/admin/posts/1');await p.getByText('아직 댓글이 없습니다.',{exact:true}).waitFor()
 await p.getByLabel('댓글 본문 검색',{exact:true}).fill('없는');await p.getByRole('button',{name:'댓글 검색',exact:true}).click();await p.getByText('검색 조건에 맞는 댓글이 없습니다.',{exact:true}).waitFor()
 state.comments=null;await p.getByRole('button',{name:'댓글 검색 초기화'}).click();await commentReady(p)
 state.comments=(r,u)=>r.fulfill({json:{...commentPage(1,Number(u.searchParams.get('page'))),items:[],totalElements:1,totalPages:1,hasNext:false}})
 await p.getByRole('button',{name:'다음',exact:true}).click();await p.getByRole('button',{name:'댓글 첫 페이지로'}).waitFor()
 const before=commentReads(state).length;await flush(p);assert.equal(commentReads(state).length,before)
 state.comments=null;await p.getByRole('button',{name:'댓글 첫 페이지로'}).click();await commentReady(p);assert.equal(commentReads(state).length,before+1)
 await context.close()
})
for(const area of ['detail','comments'])for(const status of [401,403,404,503,200])await run(`${area} error ${status} and retry/data removal`,async()=>{
 const {context,p,state}=await setup();state[area]=r=>r.fulfill({status,json:status===200?{malformed:true}:fail('ERROR')})
 await p.goto(base+'/admin/posts/1')
 if(status===401){await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor();assert.equal(await p.locator('.admin-post-detail').count(),0)}
 else if(status===403){await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('.admin-post-detail').count(),0);assert(await p.getByRole('button',{name:'로그아웃',exact:true}).isVisible())}
 else{await p.locator('.admin-main').getByRole('alert').waitFor();assert.equal(await p.locator('.admin-post-detail').count(),area==='comments'?1:0)
  if(status===404)await p.getByText(area==='detail'?'게시글을 찾을 수 없습니다.':'댓글 대상 게시글을 확인하지 못했습니다. 게시글이 존재하는지 다시 확인해 주세요.',{exact:true}).waitFor()
  if(status!==404||area==='comments'){state[area]=null;await p.getByRole('button',{name:area==='detail'?'게시글 다시 시도':'댓글 다시 시도',exact:true}).click();await commentReady(p)}
 }
 await context.close()
})
for(const action of ['filter','logout','leave'])await run(`late comment response ignores abort: ${action}`,async()=>{
 const {context,p,state}=await setup()
 await p.addInitScript(()=>{const f=window.fetch;window.fetch=(u,o)=>f(u,String(u).includes('/admin/posts/')?{...o,signal:undefined}:o)})
 const g=gate();let first=true
 state.comments=async(r)=>{if(first){first=false;g.arrive();await g.response;await r.fulfill({json:{...commentPage(1),items:[{...commentPage(1).items[0],content:'이전 비밀 본문'}]}})}else await r.fulfill({json:commentPage(1)})}
 await p.goto(base+'/admin/posts/1');await g.arrived
 if(action==='filter'){await p.getByLabel('댓글 상태',{exact:true}).selectOption('HIDDEN');await commentReady(p)}
 else if(action==='logout'){await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()}
 else{await p.getByRole('link',{name:'서비스로 돌아가기',exact:true}).click();await p.waitForURL(base+'/')}
 const done=p.waitForEvent('requestfinished',{predicate:r=>r.url().includes('/1/comments?status=ALL')});g.release();await done;await flush(p)
 assert.equal(await p.getByText('이전 비밀 본문',{exact:true}).count(),0)
 if(action==='filter')assert.equal(await p.locator('.admin-comment-results').getAttribute('aria-busy'),'false')
 else assert.equal(await p.locator('.admin-post-detail').count(),0)
 await context.close()
})
for(const width of [1440,1024,390,320])for(const [id,status] of [[1,'VISIBLE'],[2,'HIDDEN'],[3,'DELETED']])await run(`detail design ${width} ${status}`,async()=>{
 const {context,p,state}=await setup('ADMIN',width)
 state.comments=r=>r.fulfill({json:{...commentPage(id),postStatus:status,items:['VISIBLE','HIDDEN','DELETED'].map((v,i)=>({ contentUpdatedAt: null,...item(i+1,v),commentId:i+1,content:'댓글 본문\n'+'긴댓글'.repeat(40)}))}})
 state.detail=r=>r.fulfill({json:{...detail(id),title:'긴게시글제목'.repeat(12),author:{memberId:8,nickname:'긴작성자이름'.repeat(12),withdrawn:status==='DELETED'}}})
 await p.goto(base+'/admin/posts/'+id);await commentReady(p)
 const post=p.locator('.admin-post-detail'),comments=p.locator('.admin-comments')
 assert.equal(await p.getByRole('navigation',{name:'관리자 메뉴'}).getByRole('link',{name:'게시글 관리',exact:true}).getAttribute('aria-current'),'location')
 assert.equal(await p.getByRole('navigation',{name:'현재 위치'}).locator('[aria-current=page]').innerText(),'#'+id)
 assert.equal(await comments.getByRole('heading',{name:'댓글 관리',exact:true}).count(),1)
 assert.equal(await p.getByText('한국 시간 (KST)',{exact:true}).count(),2)
 for(const time of await p.locator('.admin-content-meta time').all()){
  assert.match(await time.innerText(),/2026\.11\.01\. 09:00:00/)
  assert.equal(await time.getAttribute('datetime'),stamp)
 }
 assert.equal(await post.locator('time').count(),status==='VISIBLE'?2:3)
 assert.deepEqual(await comments.locator('.admin-badge').allTextContents(),['정상','숨김','삭제'])
 assert.equal(await comments.locator('.admin-parent-status').count(),status==='VISIBLE'?0:1)
 if(status!=='VISIBLE')assert.match(await comments.locator('.admin-parent-status').innerText(),/일반 사용자에게 표시되지 않습니다/)
 assert.equal(await post.getByRole('button').count(),status==='DELETED'?0:1)
 assert.equal(await comments.locator('.admin-comment-list').getByRole('button').count(),status==='DELETED'?0:2)
 const body=await post.locator('.admin-full-content').boundingBox(),footer=await post.locator('.admin-post-footer').boundingBox()
 assert(footer.y>=body.y+body.height)
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 for(const control of await comments.locator('input,select,button').all()){
  const b=await control.boundingBox();if(b)assert(b.x>=0&&b.x+b.width<=width)
 }
 const ids=await p.locator('[id]').evaluateAll(es=>es.map(e=>e.id));assert.equal(new Set(ids).size,ids.length)
 assert.deepEqual(state.errors,[])
 await p.screenshot({path:`${out}/detail-${width}-${status}.png`,fullPage:true});await context.close()
})
await run('late post A cannot replace B after real list navigation',async()=>{
 const {context,p,state}=await setup()
 await p.addInitScript(()=>{const f=window.fetch;window.fetch=(u,o)=>f(u,String(u).includes('/admin/posts/')?{...o,signal:undefined}:o)})
 const g=gate();state.detail=async(r,u)=>{const id=Number(u.pathname.split('/').at(-1));if(id===1){g.arrive();await g.response}await r.fulfill({json:detail(id)})}
 await p.goto(base+'/admin/posts/1');await g.arrived
 await p.getByRole('link',{name:'← 목록으로'}).click();await ready(p)
 await p.getByRole('link',{name:'검증 게시글 2',exact:true}).click();await commentReady(p)
 const done=p.waitForEvent('requestfinished',{predicate:r=>new URL(r.url()).pathname==='/api/v1/admin/posts/1'})
 g.release();await done;await flush(p)
 assert(await p.locator('.admin-post-detail h2').getByText('검증 게시글 2',{exact:true}).isVisible())
 assert.equal(await p.locator('.admin-post-detail').getByText('검증 게시글 1',{exact:true}).count(),0)
 await context.close()
})
for(const same of [true,false])await run(`detail late response after login transition same=${same}`,async()=>{
 const {context,p,state}=await setup()
 await p.addInitScript(()=>{const f=window.fetch;window.fetch=(u,o)=>f(u,String(u).includes('/admin/posts/')?{...o,signal:undefined}:o)})
 const g=gate();let first=true
 state.detail=async r=>{if(first){first=false;g.arrive();await g.response;return r.fulfill({json:{...detail(1),title:'이전 관리자 데이터'}})}return r.fulfill({json:detail(1)})}
 await p.goto(base+'/admin/posts/1');await g.arrived
 await p.getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
 state.loginMember=same?{...member}:{...member,memberId:88,role:'USER'}
 await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click()
 await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Mock-only-password1!')
 await p.getByRole('button',{name:'로그인',exact:true}).click()
 if(same)await commentReady(p);else await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor()
 const done=p.waitForEvent('requestfinished',{predicate:r=>new URL(r.url()).pathname==='/api/v1/admin/posts/1'})
 g.release();await done;await flush(p);assert.equal(await p.getByText('이전 관리자 데이터',{exact:true}).count(),0)
 assert.equal(await p.locator('.admin-post-detail').count(),same?1:0)
 await context.close()
})
await run('comment pagination preserves diverted focus and retry without duplicate request',async()=>{
 const {context,p,state}=await setup();await p.goto(base+'/admin/posts/1');await commentReady(p)
 const g=gate();state.comments=async r=>{g.arrive();await g.response;await r.fulfill({json:commentPage(1,1)})}
 const input=p.getByLabel('페이지 바로 이동',{exact:true}),search=p.getByLabel('댓글 본문 검색',{exact:true})
 await input.fill('2');await input.press('Enter');await g.arrived;await search.focus();g.release();await commentReady(p)
 assert(await search.evaluate(e=>e===document.activeElement))
 state.comments=r=>r.fulfill({status:503,json:fail('SERVER_ERROR')})
 await input.fill('1');await input.press('Enter');await commentReady(p);assert(await input.evaluate(e=>e===document.activeElement))
 state.comments=null;await p.getByRole('button',{name:'댓글 다시 시도'}).focus();await p.keyboard.press('Enter');await commentReady(p)
 assert.equal(await input.inputValue(),'1')
 await p.getByRole('button',{name:'맨 마지막',exact:true}).focus();await p.keyboard.press('Enter');await commentReady(p)
 assert(await p.locator('.admin-page-buttons [role=status]').evaluate(e=>e===document.activeElement))
 await context.close()
})

for(const width of [1440,1024,390,320])await run(`representative design and readable controls ${width}`,async()=>{
 const {context,p,state}=await setup('ADMIN',width)
 state.detail=r=>r.fulfill({json:{...detail(2),title:'보청기 적응 경험을 나눕니다',content:'새 보청기를 사용한 지 한 달이 되었습니다. 처음에는 주변 소리가 낯설었지만 조금씩 적응하고 있어요.\n\n비슷한 경험이 있으신 분들의 적응 방법이 궁금합니다.'}})
 state.comments=r=>r.fulfill({json:{...commentPage(2),items:['DELETED','VISIBLE','HIDDEN'].map((status,i)=>({ contentUpdatedAt: null,...item(i+1,status),commentId:i+1,content:['처음 적응할 때는 조용한 곳에서 조금씩 사용 시간을 늘렸어요.','저도 비슷한 경험이 있어요. 꾸준히 착용하는 게 도움이 됐어요.','센터에서 조절받은 경험도 함께 공유해 주세요.'][i]}))}})
 await p.goto(base+'/admin/posts/2');await commentReady(p)
 await p.screenshot({path:`${out}/representative-${width}.png`,fullPage:true})
 if(width===1440){
  // 200% desktop zoom's 720 CSS-pixel reflow width; not a physical browser zoom test.
  await p.setViewportSize({width:720,height:900})
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
  await p.getByLabel('댓글 본문 검색',{exact:true}).focus()
  assert(await p.getByLabel('댓글 본문 검색',{exact:true}).evaluate(e=>e===document.activeElement))
  await p.screenshot({path:`${out}/reflow-720.png`,fullPage:true})
 }
 await context.close()
})

await browser.close()
await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2))
if(results.some(r=>!r.passed))process.exitCode=1
