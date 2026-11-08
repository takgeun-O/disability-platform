import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8479",
  out =
    process.env.ADMIN_MODERATION_ARTIFACT_DIR ?? "/private/tmp/iyum-admin-moderation/browser"
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

async function selectStatuses(p, group, statuses) {
  const field = p.getByRole('group', {name: group, exact: true})
  for (const [value,label] of [['VISIBLE','정상'],['HIDDEN','숨김'],['DELETED','삭제']])
    await field.getByRole('checkbox',{name:label,exact:true}).setChecked(statuses.includes(value))
  // State chips apply automatically; callers wait for the resulting read.
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
    if(r.request().method()==="POST" && u.pathname.startsWith("/api/v1/admin/posts/"))return state.write(r,u)
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

async function moderation(width=1440){
 const x=await setup('ADMIN',width),s=x.state;s.hidden=false;s.commentHidden=false;s.deleted=false;s.parentDeleted=false
 s.detail=(r,u)=>r.fulfill({json:{...detail(Number(u.pathname.split('/').at(-1))),status:s.parentDeleted?'DELETED':s.hidden?'HIDDEN':'VISIBLE',hiddenAt:s.hidden?stamp:null,deletedAt:s.parentDeleted?stamp:null}})
 s.comments=(r,u)=>r.fulfill({json:{...commentPage(1,Number(u.searchParams.get('page'))),postStatus:s.parentDeleted?'DELETED':s.hidden?'HIDDEN':'VISIBLE',items:[{...commentPage(1).items[0],status:s.deleted?'DELETED':s.commentHidden?'HIDDEN':'VISIBLE',hiddenAt:s.commentHidden?stamp:null,deletedAt:s.deleted?stamp:null}]}})
 s.write=async(r,u)=>{const comment=u.pathname.includes('/comments/'),hidden=u.pathname.endsWith('/hide');if(comment)s.commentHidden=hidden;else s.hidden=hidden;await r.fulfill({json:{postId:1,...(comment?{commentId:1}:{}),hidden,hiddenAt:hidden?stamp:null,changed:true}})}
 return x
}
const writes=s=>s.calls.filter(c=>c.method==='POST'&&c.path.startsWith('/api/v1/admin/posts/'))
const button=(p,name)=>p.getByRole('button',{name,exact:true})
const dialog=p=>p.getByRole('dialog')
async function execute(p,name){await button(p,name).click();await p.getByLabel('처리 사유 (필수)').selectOption('OTHER');await p.getByLabel('상세 사유 (필수)').fill('검증용 처리 사유');await dialog(p).getByRole('button',{name:name.replace(/^(게시글|댓글) /,'')+' 실행',exact:true}).click()}
const settled=async p=>{await dialog(p).waitFor({state:"hidden"});await p.locator('.admin-post-detail[aria-busy="false"]').waitFor();await commentReady(p)}
await run('post and comment hide/restore; confirmation validation, cancel, one write and shared refresh',async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 await button(p,'게시글 숨김 처리').click();assert.equal(writes(state).length,0)
 await button(p,'숨김 처리 실행').click();await dialog(p).getByText('처리 사유를 선택해 주세요.',{exact:true}).waitFor()
 await p.keyboard.press('Escape');assert.equal(await dialog(p).count(),0);assert.equal(writes(state).length,0)
 await execute(p,'게시글 숨김 처리');await button(p,'게시글 숨김 해제').waitFor();await settled(p)
 assert.equal(state.hidden,true);assert.equal(await p.locator('.admin-parent-status').textContent(),'게시글이 숨겨져 있어 댓글도 일반 사용자에게 표시되지 않습니다.')
 await execute(p,'댓글 숨김 처리');await button(p,'댓글 숨김 해제').waitFor();await settled(p);assert.equal(state.commentHidden,true)
 await execute(p,'게시글 숨김 해제');await button(p,'게시글 숨김 처리').waitFor();await settled(p);assert(state.commentHidden,'parent restore must not restore comment')
 await execute(p,'댓글 숨김 해제');await button(p,'댓글 숨김 처리').waitFor();await settled(p)
 assert.equal(writes(state).length,4);assert(await p.locator('[data-admin-feedback]').evaluate(e=>e===document.activeElement))
 await context.close()
})
await run('deleted parent/comment restrictions and hidden parent allowance',async()=>{
 const {context,p,state}=await moderation();state.parentDeleted=true;await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 assert.equal(await button(p,'게시글 숨김 처리').count(),0);assert.equal(await button(p,'댓글 숨김 처리').count(),0)
 state.parentDeleted=false;state.hidden=true;state.deleted=true;await p.reload();await settled(p)
 assert.equal(await button(p,'댓글 숨김 처리').count(),0);assert(await button(p,'게시글 숨김 해제').isVisible());assert.equal(writes(state).length,0)
 await context.close()
})
await run('pending mutation blocks duplicate submit and Escape; reason resets for next target',async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 const g=gate(),original=state.write;state.write=async(r,u)=>{g.arrive();await g.response;await original(r,u)}
 await execute(p,'게시글 숨김 처리');await g.arrived
 await p.keyboard.press('Enter');await p.keyboard.press('Escape');assert(await dialog(p).isVisible());assert.equal(writes(state).length,1)
 g.release();await button(p,'게시글 숨김 해제').waitFor();await settled(p)
 await button(p,'댓글 숨김 처리').click();assert.equal(await p.getByLabel('처리 사유 (필수)').inputValue(),'')
 await button(p,'취소').click();await context.close()
})
for(const [status,code] of [[401,'AUTHENTICATION_REQUIRED'],[403,'ADMIN_REQUIRED'],[403,'CSRF_TOKEN_INVALID'],[400,'INVALID_REASON'],[404,'COMMENT_NOT_FOUND'],[409,'POST_DELETED'],[500,'INTERNAL_SERVER_ERROR'],[200,'MALFORMED']])await run(`mutation ${status}/${code}`,async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 state.write=r=>r.fulfill({status,json:fail(code)})
 await execute(p,'게시글 숨김 처리')
 if(status===401){await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor();assert.equal(await p.locator('.admin-post-detail').count(),0)}
 else if(status===403&&code==='ADMIN_REQUIRED'){await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('.admin-post-detail').count(),0)}
 else{await dialog(p).getByRole('alert').waitFor();assert.equal(await p.getByLabel('처리 사유 (필수)').inputValue(),'OTHER');assert.equal(await p.getByLabel('상세 사유 (필수)').inputValue(),'검증용 처리 사유');assert(await p.locator('.admin-post-detail').isVisible())
  if([404,409,500,200].includes(status)){await button(p,'최신 상태 확인').click();await settled(p)}
  else if(code==='CSRF_TOKEN_INVALID'){
   const csrfBefore=state.calls.filter(c=>c.path.endsWith('/csrf')).length
   state.write=r=>r.fulfill({json:{postId:1,hidden:true,hiddenAt:stamp,changed:false}})
   await button(p,'숨김 처리 실행').click();await settled(p);assert.equal(state.calls.filter(c=>c.path.endsWith('/csrf')).length,csrfBefore+1)
  }
 }
 assert.equal(writes(state).length,code==='CSRF_TOKEN_INVALID'?2:1);await context.close()
})
await run('successful write with failed GET preserves success and retries only GET',async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 const original=state.detail;state.detail=r=>r.fulfill({status:503,json:fail('SERVER_ERROR')})
 await execute(p,'게시글 숨김 처리');await button(p,'게시글 다시 시도').waitFor()
 await p.locator('[data-admin-feedback]').getByText('게시글 숨김 처리를 완료했습니다.',{exact:true}).waitFor()
 assert(await p.getByText('변경 결과는 확인됐지만 최신 내용을',{exact:false}).isVisible())
 state.detail=original;await button(p,'게시글 다시 시도').click();await settled(p);assert.equal(writes(state).length,1)
 await context.close()
})
await run('comment filtered out on last page adjusts once and preserves conditions',async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 await selectStatuses(p, '댓글 상태', ['VISIBLE']);await commentReady(p)
 await p.getByLabel('댓글 본문 검색',{exact:true}).fill('  적용 검색어  ')
 await button(p,'댓글 검색').click();await commentReady(p)
 await button(p,'다음').click();await commentReady(p)
 // Unsubmitted keyword edits must not leak into either follow-up GET.
 await p.getByLabel('댓글 본문 검색',{exact:true}).fill('아직 적용하지 않은 검색어')
 const before=commentReads(state).length
 const original=state.comments;state.comments=(r,u)=>state.commentHidden?r.fulfill({json:{...commentPage(1,Number(u.searchParams.get('page'))),items:[],totalElements:0,totalPages:0,hasNext:false}}):original(r,u)
 await execute(p,'댓글 숨김 처리');await p.getByText('검색 조건에 맞는 댓글이 없습니다.',{exact:true}).waitFor()
 assert.equal(await p.getByRole('group',{name:'댓글 상태'}).getByRole('checkbox',{name:'정상',exact:true}).isChecked(),true)
 assert.equal(new URLSearchParams(commentReads(state).at(-1).search).get('page'),'0')
 const followups=commentReads(state).slice(before).map(c=>new URLSearchParams(c.search))
 assert.equal(followups.length,2)
 assert.deepEqual(followups.map(q=>q.get('page')),['1','0'])
 for(const q of followups){assert.deepEqual(q.getAll('statuses'),['VISIBLE']);assert.equal(q.get('keyword'),'적용 검색어');assert.equal(q.has('status'),false)}
 assert.equal(await p.getByLabel('댓글 본문 검색',{exact:true}).inputValue(),'아직 적용하지 않은 검색어')
 assert.equal(writes(state).length,1);await context.close()
})
await run('pre-mutation delayed GET ignoring abort cannot overwrite refreshed parent',async()=>{
 const {context,p,state}=await moderation();await p.addInitScript(()=>{const f=window.fetch;window.fetch=(u,o)=>f(u,{...o,signal:undefined})})
 const g=gate(),original=state.comments;let first=true
 state.comments=async(r,u)=>{if(first){first=false;g.arrive();await g.response;return r.fulfill({json:commentPage(1)})}return original(r,u)}
 await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await g.arrived;await execute(p,'게시글 숨김 처리');await settled(p)
 const done=p.waitForEvent('requestfinished',{predicate:r=>r.url().includes('/1/comments?')});g.release();await done;await flush(p)
 assert.equal(await p.locator('.admin-parent-status').textContent(),'게시글이 숨겨져 있어 댓글도 일반 사용자에게 표시되지 않습니다.');await context.close()
})
for(const stage of ['csrf','write'])await run(`approved navigation during ${stage} ignores late work`,async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts',{waitUntil:'domcontentloaded'});await ready(p);await p.getByRole('link',{name:'검증 게시글 1',exact:true}).click();await p.getByRole('link',{name:'상세 관리로 이동',exact:true}).click();await settled(p)
 const g=gate()
 if(stage==='csrf')await context.route('**/api/v1/auth/csrf',async r=>{g.arrive();await g.response;await r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})})
 else state.write=async r=>{g.arrive();await g.response;await r.fulfill({json:{postId:1,hidden:true,hiddenAt:stamp,changed:true}})}
 await execute(p,'게시글 숨김 처리');await g.arrived
 // Actual browser back is allowed even while the modal blocks UI controls.
 await p.goBack();await p.waitForURL(u=>u.pathname!='/admin/posts/1')
 const done=p.waitForEvent('requestfinished',{predicate:r=>stage==='csrf'?r.url().endsWith('/csrf'):r.url().endsWith('/hide')});g.release();await done;await flush(p)
 assert.equal(writes(state).length,stage==='csrf'?0:1);assert.equal(await p.locator('.comment-success-toast').count(),0)
 await context.close()
})
for(const width of [1440,390,320])await run(`modal keyboard and layout ${width}`,async()=>{
 const {context,p,state}=await moderation(width);await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 await button(p,'게시글 숨김 처리').click();assert(await p.getByLabel('처리 사유 (필수)').evaluate(e=>e===document.activeElement))
 await p.getByLabel('처리 사유 (필수)').selectOption('OTHER');await p.getByLabel('상세 사유 (필수)').fill('검토용 사유');await p.screenshot({path:`${out}/confirm-${width}.png`})
 assert(await dialog(p).evaluate(e=>e.scrollWidth<=e.clientWidth))
 await button(p,'취소').focus();await p.keyboard.press('Escape');assert.equal(await dialog(p).count(),0)
 state.write=r=>r.fulfill({status:500,json:fail('SERVER_ERROR')});await execute(p,'게시글 숨김 처리');await dialog(p).getByRole('alert').waitFor();await p.screenshot({path:`${out}/error-${width}.png`})
 assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await context.close()
})
for(const next of ['anonymous','same','other'])await run(`late mutation after auth transition ${next}`,async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts',{waitUntil:'domcontentloaded'});await ready(p);await p.getByRole('link',{name:'검증 게시글 1',exact:true}).click();await p.getByRole('link',{name:'상세 관리로 이동',exact:true}).click();await settled(p)
 const g=gate();state.write=async r=>{g.arrive();await g.response;await r.fulfill({json:{postId:1,hidden:true,hiddenAt:stamp,changed:true}})}
 await execute(p,'게시글 숨김 처리');await g.arrived
 await p.goBack();await ready(p);await button(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
 if(next!=='anonymous'){
  state.loginMember=next==='same'?{...member}:{...member,memberId:99,role:'USER'}
  await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click()
  await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Synthetic-password1!');await button(p,'로그인').click()
  if(next==='same')await ready(p);else await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor()
 }
 const done=p.waitForEvent('requestfinished',{predicate:r=>r.url().endsWith('/1/hide')});g.release();await done;await flush(p)
 assert.equal(await p.locator('.comment-success-toast').count(),0);assert.equal(await dialog(p).count(),0);assert.equal(writes(state).length,1)
 await context.close()
})
await run('CSRF preparation failure sends no mutation; network failure offers GET only',async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 await context.route('**/api/v1/auth/csrf',r=>r.abort('failed'))
 await execute(p,'게시글 숨김 처리');await dialog(p).getByText('보안 정보를 확인하지 못해',{exact:false}).waitFor();assert.equal(writes(state).length,0)
 await context.unroute('**/api/v1/auth/csrf');state.write=r=>r.abort('failed')
 await button(p,'숨김 처리 실행').click();await button(p,'최신 상태 확인').waitFor();assert.equal(writes(state).length,1)
 await button(p,'최신 상태 확인').click();await settled(p);assert.equal(writes(state).length,1);await context.close()
})
await run('success screenshot and long reason validation',async()=>{
 const {context,p,state}=await moderation();await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 await button(p,'게시글 숨김 처리').click();await p.getByLabel('처리 사유 (필수)').selectOption('OTHER');await p.getByLabel('상세 사유 (필수)').fill('😀'.repeat(501));await button(p,'숨김 처리 실행').click()
 await dialog(p).getByText('처리 사유는 500자 이하여야 합니다.',{exact:true}).waitFor();assert.equal(writes(state).length,0)
 await p.getByLabel('처리 사유 (필수)').selectOption('OTHER');await p.getByLabel('상세 사유 (필수)').fill('검토용 사유');await button(p,'숨김 처리 실행').click();await settled(p)
 await p.locator('.comment-success-toast').waitFor();await p.screenshot({path:`${out}/success-1440.png`,fullPage:true});await context.close()
})

await run('permission read failure suppresses pending mutation success',async()=>{
 const {context,p,state}=await moderation(),read=gate(),write=gate()
 state.comments=async r=>{read.arrive();await read.response;await r.fulfill({status:403,json:fail('ACCESS_DENIED')})}
 state.write=async r=>{write.arrive();await write.response;await r.fulfill({json:{postId:1,hidden:true,hiddenAt:stamp,changed:true}})}
 await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await read.arrived;await execute(p,'게시글 숨김 처리');await write.arrived
 read.release();await p.getByRole('heading',{name:'관리자 권한이 없습니다',exact:true}).waitFor()
 const done=p.waitForEvent('requestfinished',{predicate:r=>r.url().endsWith('/hide')});write.release();await done;await flush(p)
 assert.equal(await p.locator('.admin-post-detail').count(),0);assert.equal(await p.locator('.comment-success-toast').count(),0)
 await context.close()
})
await run('list return corrects out of range once after moderation',async()=>{
 const {context,p,state}=await moderation()
 await p.goto(base+'/admin/posts/1?from='+encodeURIComponent('/admin/posts?status=VISIBLE&page=1',{waitUntil:'domcontentloaded'}));await settled(p)
 await execute(p,'게시글 숨김 처리');await settled(p)
 state.read=(r,u)=>{const page=Number(u.searchParams.get('page'));return r.fulfill({json:{...payload(page),items:page?[]:[item(2)],totalElements:1,totalPages:1,hasNext:false,hasPrevious:page>0}})}
 await p.getByRole('link',{name:'← 목록으로'}).click();await ready(p)
 await p.getByRole('link',{name:'검증 게시글 2',exact:true}).waitFor()
 assert.equal(await p.getByLabel('페이지 바로 이동',{exact:true}).count(),0)
 const reads=state.calls.filter(c=>c.path==='/api/v1/admin/posts')
 assert.deepEqual(reads.map(c=>new URLSearchParams(c.search).get('page')),['1','0'])
 assert.equal(new URL(p.url()).searchParams.get('page'),'0')
 await context.close()
})

await run('typed reasons: action choices, omitted stale detail and normalized OTHER for all targets',async()=>{
 const {context,p,state}=await moderation(320);await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 const original=state.write,bodies=[];state.write=async(r,u)=>{bodies.push(r.request().postDataJSON());await original(r,u)}
 for(const [name,reasonType,expected] of [
  ['게시글 숨김 처리','SPAM_ADVERTISING',['','SPAM_ADVERTISING','ABUSE_HATE','PERSONAL_INFORMATION','DUPLICATE_FLOODING','OFF_TOPIC','OTHER']],
  ['게시글 숨김 해제','NO_VIOLATION',['','MODERATION_ERROR','NO_VIOLATION','OTHER']],
  ['댓글 숨김 처리','OTHER',['','SPAM_ADVERTISING','ABUSE_HATE','PERSONAL_INFORMATION','DUPLICATE_FLOODING','OFF_TOPIC','OTHER']],
  ['댓글 숨김 해제','OTHER',['','MODERATION_ERROR','NO_VIOLATION','OTHER']]]){
  await button(p,name).click();const select=p.getByLabel('처리 사유 (필수)')
  assert.equal(await select.inputValue(),'');assert.deepEqual(await select.locator('option').evaluateAll(es=>es.map(e=>e.value)),expected)
  await select.selectOption('OTHER');await p.getByLabel('상세 사유 (필수)').fill('　 상세  원문　')
  await select.selectOption(reasonType)
  if(reasonType!=='OTHER')assert.equal(await p.getByLabel('상세 사유 (필수)').count(),0)
  await button(p,name.replace(/^(게시글|댓글) /,'')+' 실행').click();await settled(p)
  assert.deepEqual(bodies.at(-1),reasonType==='OTHER'?{reasonType,reasonDetail:'상세  원문'}:{reasonType})
 }
 assert.equal(bodies.length,4);await context.close()
})
for(const width of [1440,320])await run(`reason-specific INVALID_REASON and retained inputs ${width}`,async()=>{
 const {context,p,state}=await moderation(width);await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
 const requests=[];state.write=(r,u)=>{requests.push({path:u.pathname,body:r.request().postDataJSON()});return r.fulfill({status:400,json:fail('INVALID_REASON')})}
 await button(p,'댓글 숨김 처리').click();const select=p.getByLabel('처리 사유 (필수)'),detailInput=p.getByLabel('상세 사유 (필수)')
 await select.selectOption('OTHER');await detailInput.fill('보존할 상세');await select.selectOption('PERSONAL_INFORMATION')
 await button(p,'숨김 처리 실행').click();await dialog(p).getByText('선택한 처리 사유를 확인해 주세요.',{exact:true}).waitFor()
 assert.deepEqual(requests,[{path:'/api/v1/admin/posts/1/comments/1/hide',body:{reasonType:'PERSONAL_INFORMATION'}}])
 assert.equal(await select.inputValue(),'PERSONAL_INFORMATION');assert.equal(await select.getAttribute('aria-invalid'),'true');assert.equal(await select.getAttribute('aria-describedby'),await dialog(p).getByRole('alert').getAttribute('id'))
 assert.equal(await detailInput.count(),0);assert(!(await dialog(p).textContent()).includes('1~500'))
 await p.screenshot({path:`${out}/ordinary-error-${width}.png`})
 await select.selectOption('OTHER');assert.equal(await detailInput.inputValue(),'보존할 상세');await button(p,'숨김 처리 실행').click()
 await dialog(p).getByText('상세 사유를 확인해 주세요. 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.',{exact:true}).waitFor()
 assert.equal(await select.getAttribute('aria-invalid'),'false');assert.equal(await detailInput.getAttribute('aria-invalid'),'true');assert.equal(await detailInput.getAttribute('aria-describedby'),await dialog(p).getByRole('alert').getAttribute('id'));assert.equal(await detailInput.inputValue(),'보존할 상세')
 assert.deepEqual(requests[1].body,{reasonType:'OTHER',reasonDetail:'보존할 상세'});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
 await p.screenshot({path:`${out}/other-error-${width}.png`});await context.close()
})

for(const empty of [false,true])await run(`moderation follow-up read cannot undo changed chips: empty=${empty}`,async()=>{
 const {context,p,state}=await moderation()
 const hold=gate()
 try {
  await p.addInitScript(()=>{const f=window.fetch;window.fetch=(u,o)=>f(u,String(u).includes('/comments?')?{...o,signal:undefined}:o)})
  await p.goto(base+'/admin/posts/1',{waitUntil:'domcontentloaded'});await settled(p)
  const original=state.comments
  state.comments=async(r,u)=>{hold.arrive();await hold.response;await original(r,u)}
  await execute(p,'댓글 숨김 처리');await hold.arrived;await dialog(p).waitFor({state:'hidden'})
  const field=p.getByRole('group',{name:'댓글 상태',exact:true})
  await field.getByRole('checkbox',{name:'정상',exact:true}).uncheck()
  if(empty){await field.getByRole('checkbox',{name:'숨김',exact:true}).uncheck();await field.getByRole('checkbox',{name:'삭제',exact:true}).uncheck()}
  state.comments=r=>r.fulfill({json:{...commentPage(1),items:[],totalElements:0,totalPages:0,hasNext:false}})
  const done=p.waitForEvent('requestfinished',{predicate:r=>r.url().includes('/comments?statuses=VISIBLE')})
  hold.release();await done;await commentReady(p)
  assert.equal(writes(state).length,1)
  assert.equal(await p.locator('.admin-comment-list li').count(),0)
  if(empty)await p.getByText('표시할 상태를 선택해 주세요.',{exact:true}).waitFor()
  else {assert.deepEqual(new URLSearchParams(commentReads(state).at(-1).search).getAll('statuses'),['HIDDEN','DELETED']);await p.getByText('검색 조건에 맞는 댓글이 없습니다.',{exact:true}).waitFor()}
 } finally {hold.release();await context.close()}
})

await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));if(results.some(r=>!r.passed))process.exitCode=1
