import {adminDateScenarios} from "./helpers/admin-date-scenarios.mjs"
import { adminStableListScenarios } from "./helpers/admin-stable-list-scenarios.mjs"
import { adminChipScenarios } from "./helpers/admin-chip-scenarios.mjs"
import assert from "node:assert/strict"
import fs from "node:fs/promises"
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? "playwright")
const base = process.env.BASE_URL ?? "http://127.0.0.1:8479",
  out =
    process.env.ADMIN_ARTIFACT_DIR ?? "/private/tmp/iyum-admin-results/browser"
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
    if (u.pathname === "/api/v1/admin/posts")
      return state.read
        ? state.read(r, u)
        : r.fulfill({ json: payload(Number(u.searchParams.get("page"))) })
    return r.fulfill({ status: 404, json: fail("NOT_FOUND") })
  })
  return { context, p, state }
}
const reads = (s) => s.calls.filter((c) => c.path === "/api/v1/admin/posts")
const ready = (p) => p.locator('.admin-results[aria-busy="false"]').waitFor()
async function run(name, fn) {
  if (process.env.TEST_FILTER && !new RegExp(process.env.TEST_FILTER).test(name)) return
  try {
    await fn()
    results.push({ name, passed: true })
    console.log("PASS", name)
  } catch (e) {
    results.push({ name, passed: false, error: e.message })
    console.log("FAIL", name, e.message)
  }
}
await run(
  "checking, anonymous and USER direct access never expose rows or request admin data",
  async () => {
    for (const role of [null, "USER"]) {
      const { context, p, state } = await setup(role)
      const g = gate()
      state.me = async (r) => {
        g.arrive()
        await g.response
        await r.fulfill(
          state.member
            ? { json: state.member }
            : { status: 401, json: fail("AUTHENTICATION_REQUIRED") },
        )
      }
      await p.goto(base + "/admin/posts")
      await g.arrived
      assert.equal(await p.locator(".admin-panel").count(), 0)
      assert.equal(
        await p.getByRole("link", { name: "관리자", exact: true }).count(),
        0,
      )
      g.release()
      await p
        .getByRole("heading", {
          name: role ? "관리자 권한이 없습니다" : "로그인이 필요합니다",
          exact: true,
        })
        .waitFor()
      assert.equal(reads(state).length, 0)
      if (!role)
        assert.equal(
          await p
            .locator(".admin-main")
            .getByRole("link", { name: "로그인", exact: true })
            .getAttribute("href"),
          "/login?from=%2Fadmin%2Fposts",
        )
      await context.close()
    }
  },
)
await run(
  "ADMIN entry/default query, server paging, search on Enter, filters and reset",
  async () => {
    const { context, p, state } = await setup()
    await p.goto(base + "/admin")
    await ready(p)
    assert.equal(new URL(p.url()).pathname, "/admin/posts")
    assert.equal(reads(state).length, 1)
    assert.equal(reads(state)[0].search, "?statuses=VISIBLE&statuses=HIDDEN&statuses=DELETED&page=0&size=20&sort=LATEST")
    await p.getByRole("link", { name: "관리자", exact: true }).waitFor()
    await p.locator(".admin-post-secondary").filter({hasText:"탈퇴한 회원"}).waitFor()
    await p.getByRole("button", { name: "다음", exact: true }).click()
    await ready(p)
    assert.equal(
      new URLSearchParams(reads(state).at(-1).search).get("page"),
      "1",
    )
    await p.getByLabel("게시글 제목 검색", { exact: true }).fill("　검색　")
    assert.equal(reads(state).length, 2)
    await p.getByLabel("게시글 제목 검색", { exact: true }).press("Enter")
    await ready(p)
    let q = new URLSearchParams(reads(state).at(-1).search)
    assert.equal(q.get("keyword"), "검색")
    assert.equal(q.get("page"), "0")
    await selectStatuses(p, '게시글 상태', ['HIDDEN'])
    await ready(p)
    q = new URLSearchParams(reads(state).at(-1).search)
    assert.equal(q.getAll("statuses").join(), "HIDDEN")
    assert.equal(q.get("page"), "0")
    await p.getByRole("button", { name: "전체 초기화", exact: true }).click()
    await ready(p)
    assert.equal(
      new URLSearchParams(reads(state).at(-1).search).has("keyword"),
      false,
    )
    assert.deepEqual(state.errors, [])
    await context.close()
  },
)
await run(
  "503 retry/empty/no results, 403 preserves session, 401 expires",
  async () => {
    const { context, p, state } = await setup()
    state.read = (r) =>
      r.fulfill({ status: 503, json: fail("INTERNAL_SERVER_ERROR") })
    await p.goto(base + "/admin/posts")
    await p.getByRole("button", { name: "다시 시도", exact: true }).waitFor()
    state.read = (r) =>
      r.fulfill({
        json: {
          ...payload(),
          items: [],
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
        },
      })
    await p.getByRole("button", { name: "다시 시도", exact: true }).click()
    await p.getByText("게시글이 없습니다.", { exact: true }).waitFor()
    await p.getByLabel("게시글 제목 검색", { exact: true }).fill("없는 제목")
    await p.getByRole("button", { name: "검색", exact: true }).click()
    await p.getByText("검색 결과가 없습니다.", { exact: true }).waitFor()
    state.read = (r) => r.fulfill({ status: 403, json: fail("ACCESS_DENIED") })
    await selectStatuses(p, '게시글 상태', ['HIDDEN'])
    await p.getByRole("heading", { name: "관리자 권한이 없습니다", exact: true }).waitFor()
    assert(
      await p
        .getByRole("button", { name: "로그아웃", exact: true })
        .isVisible(),
    )
    assert.equal(await p.locator("tbody tr").count(), 0)
    state.read = (r) =>
      r.fulfill({ status: 401, json: fail("AUTHENTICATION_REQUIRED") })
    // Access denial removes both the list and preview. Re-enter to check 401 separately.
    await p.reload()
    await p
      .getByRole("heading", { name: "로그인이 필요합니다", exact: true })
      .waitFor()
    assert.equal(await p.locator(".admin-panel").count(), 0)
    await context.close()
  },
)
for (const logout of [false, true])
  await run(
    `late response ignored with transport ignoring abort: logout=${logout}`,
    async () => {
      const { context, p, state } = await setup()
      await p.addInitScript(() => {
        const original = window.fetch
        window.fetch = (url, options) =>
          original(
            url,
            String(url).includes("/admin/posts")
              ? { ...options, signal: undefined }
              : options,
          )
      })
      const g = gate()
      state.read = async (r, u) => {
        if (u.searchParams.getAll("statuses").length === 3) {
          g.arrive()
          await g.response
          await r.fulfill({ json: payload() })
        } else
          await r.fulfill({
            json: { ...payload(), items: [item(9, "HIDDEN")] },
          })
      }
      await p.goto(base + "/admin/posts")
      await g.arrived
      if (logout) {
        await p.getByRole("button", { name: "로그아웃", exact: true }).click()
        await p
          .getByRole("heading", { name: "로그인이 필요합니다", exact: true })
          .waitFor()
      } else {
        await selectStatuses(p, '게시글 상태', ['HIDDEN'])
        await p.getByText("검증 게시글 9", { exact: true }).waitFor()
      }
      const done = p.waitForEvent("requestfinished", {
        predicate: (q) => q.url().includes("/admin/posts?statuses=VISIBLE&statuses=HIDDEN&statuses=DELETED"),
      })
      g.release()
      await done
      await p.evaluate(
        () =>
          new Promise((r) =>
            requestAnimationFrame(() => requestAnimationFrame(r)),
          ),
      )
      assert.equal(await p.locator("tbody tr").count(), logout ? 0 : 1)
      if (!logout) {
        assert(await p.getByText("검증 게시글 9", { exact: true }).isVisible())
        assert.equal(
          await p.locator(".admin-results").getAttribute("aria-busy"),
          "false",
        )
      }
      await context.close()
    },
  )
for (const sameMember of [true, false])
  await run(`late ADMIN response after new login: sameMember=${sameMember}`, async () => {
    const { context, p, state } = await setup()
    await p.addInitScript(() => {
      const original = window.fetch
      window.fetch = (url, options) => original(url, String(url).includes('/admin/posts') ? { ...options, signal: undefined } : options)
    })
    const g = gate()
    let first = true
    state.read = async r => {
      if (first) { first = false; g.arrive(); await g.response; return r.fulfill({ json: payload() }) }
      return r.fulfill({ json: { ...payload(), items: [item(90)] } })
    }
    await p.goto(base + '/admin/posts')
    await g.arrived
    await p.getByRole('button', { name: '로그아웃', exact: true }).click()
    await p.getByRole('heading', { name: '로그인이 필요합니다', exact: true }).waitFor()
    state.loginMember = sameMember ? { ...member } : { ...member, memberId: 88, role: 'USER', nickname: '새일반회원' }
    await p.locator('.admin-main').getByRole('link', { name: '로그인', exact: true }).click()
    await p.locator('input[name=email]').fill('mock@example.test')
    await p.locator('input[name=password]').fill('Mock-only-password1!')
    await p.getByRole('button', { name: '로그인', exact: true }).click()
    if (sameMember) await p.getByText('검증 게시글 90', { exact: true }).waitFor()
    else await p.getByRole('heading', { name: '관리자 권한이 없습니다', exact: true }).waitFor()
    const before = reads(state).length
    const finished = p.waitForEvent('requestfinished', { predicate: r => r.url().includes('/admin/posts?statuses=VISIBLE&statuses=HIDDEN&statuses=DELETED') })
    g.release()
    await finished
    await p.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))))
    assert.equal(reads(state).length, before)
    assert.equal(await p.getByText('검증 게시글 1', { exact: true }).count(), 0)
    if (sameMember) assert(await p.getByText('검증 게시글 90', { exact: true }).isVisible())
    else { assert.equal(await p.locator('tbody tr').count(), 0); assert.equal(await p.getByRole('link', { name: '관리자', exact: true }).count(), 0) }
    await context.close()
  })
await run("page jump validation, first/last and anchored time", async () => {
  const { context, p, state } = await setup()
  await p.goto(base + '/admin/posts'); await ready(p)
  const jump = p.getByLabel('페이지 바로 이동', { exact: true })
  await jump.fill('0'); await jump.press('Enter')
  await p.getByRole('alert').filter({ hasText: '페이지 번호' }).waitFor()
  assert.equal(reads(state).length, 1)
  await jump.fill('2'); await jump.press('Enter'); await ready(p)
  assert.equal(new URLSearchParams(reads(state).at(-1).search).get('page'), '1')
  assert(await p.getByRole('button', { name: '맨 마지막', exact: true }).isDisabled())
  await p.getByRole('button', { name: '맨 처음', exact: true }).click(); await ready(p)
  assert.equal(new URLSearchParams(reads(state).at(-1).search).get('page'), '0')
  await p.getByRole('button', { name: '맨 마지막', exact: true }).click(); await ready(p)
  assert.equal(new URLSearchParams(reads(state).at(-1).search).get('page'), '1')
  const time = p.locator('.written-time-trigger').first()
  await time.click()
  const panel = p.locator('.written-time-exact:popover-open')
  await panel.waitFor({state:'visible'})
  const a = await time.boundingBox(), b = await panel.boundingBox()
  assert(Math.abs(b.y - (a.y+a.height)) < 12 || Math.abs(b.y+b.height-a.y) < 12)
  await p.keyboard.press('Escape'); assert.equal(await p.locator(':popover-open').count(),0)
  await context.close()
})
for (const entry of ['sidebar', 'header', 'mobile'])
 await run(`current menu during loading and later search: ${entry}`, async () => {
  const {context,p,state}=await setup('ADMIN', entry==='mobile'?390:1440)
  const g=gate();let first=true
  state.read=async(r,u)=>{if(first){first=false;g.arrive();await g.response}await r.fulfill({json:payload(Number(u.searchParams.get('page')))})}
  await p.goto(base+'/admin/posts');await g.arrived
  const clickMenu=async()=>{
   if(entry==='sidebar')await p.getByRole('link',{name:'게시글 관리',exact:true}).click()
   else if(entry==='header')await p.getByRole('link',{name:'관리자',exact:true}).click()
   else {await p.getByRole('button',{name:'메뉴 열기'}).click();await p.locator('#iyum-mobile-navigation').getByRole('link',{name:'관리자',exact:true}).click()}
  }
  await clickMenu();g.release();await ready(p);assert.equal(reads(state).length,1)
  await clickMenu()
  await p.getByLabel('게시글 제목 검색',{exact:true}).fill('메뉴 유지')
  await p.getByRole('button',{name:'검색',exact:true}).click();await ready(p)
  await selectStatuses(p, '게시글 상태', ['HIDDEN']);await ready(p)
  await p.getByRole('button',{name:'다음',exact:true}).click();await ready(p)
  assert.equal(new URLSearchParams(reads(state).at(-1).search).get('page'),'1')
  await context.close()
 })
await run('page input focus survives gated page completion',async()=>{
 const {context,p,state}=await setup();await p.goto(base+'/admin/posts');await ready(p)
 const g=gate();state.read=async(r)=>{g.arrive();await g.response;await r.fulfill({json:payload(1)})}
 const input=p.getByLabel('페이지 바로 이동',{exact:true})
 await input.fill('2');await input.press('Enter');await g.arrived
 await input.press('Enter');await input.press('Enter');assert.equal(reads(state).length,2)
 g.release();await ready(p)
 assert(await input.evaluate(e=>e===document.activeElement),'page input focus lost')
 assert.equal(await input.inputValue(),'2');assert.equal(reads(state).length,2)
 await context.close()
})
await run('page buttons retain focus, boundary fallback, focus diversion, error retry',async()=>{
 const {context,p,state}=await setup()
 const pages=n=>({...payload(n),items:[item(100+n)],totalElements:60,totalPages:3,hasNext:n<2,hasPrevious:n>0})
 state.read=(r,u)=>r.fulfill({json:pages(Number(u.searchParams.get('page')))})
 await p.goto(base+'/admin/posts');await ready(p)
 const button=name=>p.getByRole('button',{name,exact:true})
 const focused=locator=>locator.evaluate(e=>e===document.activeElement)
 await button('다음').focus();await p.keyboard.press('Enter');await ready(p)
 assert(await focused(button('다음')))
 await p.keyboard.press('Enter');await ready(p)
 assert(await focused(p.locator('.admin-page-buttons [role=status]')))
 await button('이전').focus();await p.keyboard.press('Enter');await ready(p);assert(await focused(button('이전')))
 await button('맨 처음').focus();await p.keyboard.press('Enter');await ready(p)
 assert(await focused(p.locator('.admin-page-buttons [role=status]')))
 await button('맨 마지막').focus();await p.keyboard.press('Enter');await ready(p)
 assert(await focused(p.locator('.admin-page-buttons [role=status]')))
 const g=gate();state.read=async(r)=>{g.arrive();await g.response;await r.fulfill({json:pages(1)})}
 const input=p.getByLabel('페이지 바로 이동',{exact:true}),search=p.getByLabel('게시글 제목 검색',{exact:true})
 await input.fill('2');await input.press('Enter');await g.arrived
 await search.focus();g.release();await ready(p);assert(await focused(search))
 state.read=r=>r.fulfill({status:503,json:fail('INTERNAL_SERVER_ERROR')})
 await input.fill('1');await input.press('Enter');await ready(p)
 assert(await focused(input));assert.equal(await input.inputValue(),'1')
 const before=reads(state).length
 for(const name of ['게시글 관리','관리자'])await p.getByRole('link',{name,exact:true}).click()
 assert.equal(reads(state).length,before)
  state.read=r=>r.fulfill({json:pages(0)})
 await button('다시 시도').focus();await p.keyboard.press('Enter');await ready(p)
 assert.equal(await input.inputValue(),'1');assert.equal(reads(state).length,before+1)
 await context.close()
})
await run('real navigation discards late admin response; modified click preserves original screen',async()=>{
 const {context,p,state}=await setup()
 await p.addInitScript(()=>{const original=window.fetch;window.fetch=(url,options)=>original(url,String(url).includes('/admin/posts')?{...options,signal:undefined}:options)})
 const g=gate();state.read=async r=>{g.arrive();await g.response;if(!r.request().frame().page().isClosed())await r.fulfill({json:payload()})}
 await p.goto(base+'/admin/posts');await g.arrived
 const popup=context.waitForEvent('page')
 await p.getByRole('link',{name:'게시글 관리',exact:true}).click({modifiers:['Meta']})
 const tab=await popup;await tab.close()
 assert.equal(new URL(p.url()).pathname,'/admin/posts')
 await p.getByRole('link',{name:'서비스로 돌아가기',exact:true}).click()
 await p.waitForURL(base+'/')
 const done=p.waitForEvent('requestfinished',{predicate:r=>r.url().includes('/admin/posts?statuses=VISIBLE&statuses=HIDDEN&statuses=DELETED')})
 g.release();await done
 await p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
 assert.equal(await p.locator('.admin-panel').count(),0)
 assert.equal(new URL(p.url()).pathname,'/')
 await context.close()
})
for (const width of [1440, 390, 320])
  await run(`layout and keyboard ${width}`, async () => {
    const { context, p, state } = await setup("ADMIN", width)
    await p.goto(base + "/admin/posts")
    await ready(p)
    await p.getByLabel("게시글 제목 검색", { exact: true }).focus()
    await p.keyboard.type("긴 제목")
    await p.keyboard.press("Enter")
    await ready(p)
    assert(
      await p
        .getByLabel("게시글 제목 검색", { exact: true })
        .evaluate((e) => e === document.activeElement),
    )
    assert(
      await p.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    )
    assert.deepEqual(state.errors, [])
    await p.screenshot({ path: `${out}/admin-${width}.png`, fullPage: true })
    await context.close()
  })

await adminChipScenarios({run,setup,base,out,kind:'posts',ready:ready,reads:reads,template:payload})

await adminStableListScenarios({run,setup,base,out,kind:'posts',ready:ready,reads:reads,template:payload})

await adminDateScenarios({run,setup,base,out,kind:'posts',ready,reads,template:payload})
await browser.close()
await fs.writeFile(`${out}/results.json`, JSON.stringify(results, null, 2))
if (results.some((r) => !r.passed)) process.exitCode = 1
