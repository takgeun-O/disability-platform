import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-frontend/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const stamp='2026-11-10T01:00:00Z',path='/api/v1/admin/members'
const admin={memberId:7,nickname:'검증관리자',email:'admin@example.test',role:'ADMIN'}
const item=(id,extra={})=>({memberId:id,nickname:`소리나무 ${id}`,email:`member.${id}@example.test`,status:'ACTIVE',role:'USER',createdAt:stamp,...extra})
const detail=id=>({...item(id),updatedAt:'2026-11-10T02:00:00Z',withdrawnAt:null})
const page=(n=0,items=null)=>({items:items??Array.from({length:n?4:20},(_,i)=>item(100-n*20-i)),page:n,size:20,totalElements:24,totalPages:2,hasNext:n===0,hasPrevious:n>0})
const error=(status,code)=>({status,json:{code,message:'mock failure',fieldErrors:[]}})
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),promise:new Promise(r=>release=r),arrive,release}}
const contexts=[]
async function setup(width=1440){
 const c=await browser.newContext({viewport:{width,height:1000}});contexts.push(c)
 const p=await c.newPage();p.setDefaultTimeout(10000)
 const s={calls:[],list:null,detail:null,member:admin,errors:[]};p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const original=window.fetch;window.__memberHandled=[];window.fetch=async(u,o)=>{const relevant=String(u).includes('/admin/members');const r=await original(u,relevant?{...o,signal:undefined}:o);if(relevant){const json=r.json.bind(r);r.json=async()=>{const value=await json();queueMicrotask(()=>window.__memberHandled.push(String(u)));return value};if(r.status!==200)queueMicrotask(()=>window.__memberHandled.push(String(u)))}return r}})
 await c.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url());s.calls.push({url:u.pathname+u.search,method:r.request().method()})
  if(u.pathname.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:error(401,'AUTHENTICATION_REQUIRED'))
  if(u.pathname===path)return s.list?s.list(r,u):r.fulfill({json:page(Number(u.searchParams.get('page')))})
  if(u.pathname.endsWith('/moderation-logs'))return r.fulfill({json:{items:[],page:Number(u.searchParams.get('page')),size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false}})
  if(u.pathname.startsWith(path+'/'))return s.detail?s.detail(r,u):r.fulfill({json:detail(Number(u.pathname.split('/').at(-1)))})
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  if(u.pathname==='/api/v1/admin/posts')return r.fulfill({json:{items:[],page:0,size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false}})
  return r.fulfill(error(404,'NOT_FOUND'))
 })
 return{p,s,c}
}
const button=(p,name)=>p.getByRole('button',{name,exact:true})
const label=(p,name)=>p.getByLabel(name,{exact:true})
const ready=p=>p.locator('.admin-members-list[aria-busy="false"]').waitFor()
const dready=p=>p.locator('#admin-member-detail[aria-busy="false"]').waitFor()
const select=(p,id)=>p.locator(`[data-member-select="${id}"]`).click()
const reads=s=>s.calls.filter(c=>c.url.startsWith(path+'?'))
const frames=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
async function run(name,fn){if(process.env.TEST_FILTER&&!new RegExp(process.env.TEST_FILTER).test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message);for(const c of contexts)for(const p of c.pages())await p.screenshot({path:`${out}/failure-${results.length}.png`,fullPage:true}).catch(()=>{})}finally{await Promise.all(contexts.splice(0).map(c=>c.close()))}}
await run('search parameters, draft separation, sort, pagination, reset and no prefetch',async()=>{
 const{p,s}=await setup();await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p)
 assert.equal(reads(s).length,1);assert.equal(s.calls.filter(c=>/^\/api\/v1\/admin\/members\//.test(c.url)).length,0)
 assert.equal(new URLSearchParams(reads(s)[0].url.split('?')[1]).get('status'),null)
 await label(p,'검색 기준').selectOption('MEMBER_ID');await label(p,'검색어').fill('9223372036854775807');await button(p,'검색').click();await ready(p)
 assert.equal(new URLSearchParams(reads(s).at(-1).url.split('?')[1]).get('keyword'),'9223372036854775807')
 await label(p,'검색어').fill('unapplied');assert.equal(reads(s).length,2)
 await label(p,'회원 상태').selectOption('WITHDRAWN');await ready(p)
 let q=new URLSearchParams(reads(s).at(-1).url.split('?')[1]);assert.equal(q.get('keyword'),'9223372036854775807');assert.equal(q.get('status'),'WITHDRAWN')
 await label(p,'권한').selectOption('ADMIN');await ready(p);await label(p,'가입일 정렬').selectOption('OLDEST');await ready(p)
 await button(p,'다음').click();await ready(p);q=new URLSearchParams(reads(s).at(-1).url.split('?')[1]);assert.equal(q.get('page'),'1');assert.equal(q.get('sort'),'OLDEST');assert.equal(q.get('role'),'ADMIN')
 await label(p,'검색 기준').selectOption('EMAIL');await label(p,'검색어').fill(' MEMBER@EXAMPLE.TEST ');await label(p,'검색어').press('Enter');await ready(p);q=new URLSearchParams(reads(s).at(-1).url.split('?')[1]);assert.equal(q.get('page'),'0');assert.equal(q.get('keyword'),'member@example.test')
 await button(p,'전체 초기화').click();await ready(p);q=new URLSearchParams(reads(s).at(-1).url.split('?')[1]);assert.equal(q.has('status'),false);assert.equal(q.has('role'),false);assert.equal(q.has('keyword'),false);assert.equal(q.get('sort'),'LATEST')
 await label(p,'검색 기준').selectOption('MEMBER_ID');await label(p,'검색어').fill('0');const n=reads(s).length;await button(p,'검색').click();await p.locator('.admin-members').getByRole('alert').waitFor();assert.equal(reads(s).length,n);assert.equal(await label(p,'검색어').evaluate(e=>e===document.activeElement),true)
 assert.deepEqual(s.errors,[])
})
await run('selection, null email, withdrawn email, clipboard failure and close focus',async()=>{
 const{p,s}=await setup(1920);s.list=(r)=>r.fulfill({json:page(0,[item(100,{status:'WITHDRAWN',nickname:'탈퇴한 회원',email:'former@example.test'}),item(99,{status:'DELETED',nickname:'탈퇴한 회원',email:null})])})
 s.detail=(r,u)=>{const id=Number(u.pathname.split('/').at(-1));return r.fulfill({json:{...detail(id),nickname:'탈퇴한 회원',status:id===99?'DELETED':'WITHDRAWN',email:id===99?null:'former@example.test'}})}
 await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);assert(await p.getByText('former@example.test',{exact:true}).isVisible())
 await select(p,99);await dready(p);assert.equal(await p.locator('#admin-member-detail').getByText('이메일 정보 없음',{exact:true}).count(),1);assert.equal(await button(p,'이메일 복사').count(),0);await p.screenshot({path:out+'/deleted-detail.png',fullPage:true})
 await button(p,'닫기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});assert.equal(await p.locator('[data-member-select="99"]').evaluate(e=>e===document.activeElement),true)
 await select(p,100);await dready(p);await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{throw Error('mock failure')}}}));await button(p,'이메일 복사').click();await p.getByText('복사하지 못했습니다. 이메일을 직접 선택해 복사해 주세요.').waitFor()
 assert.equal(reads(s).length,1)
})
await run('history restores member conditions, selection, direct URLs and menu navigation',async()=>{
 const{p}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p)
 await label(p,'검색어').fill('소리');await button(p,'검색').click();await ready(p);await label(p,'가입일 정렬').selectOption('OLDEST');await ready(p);await button(p,'다음').click();await ready(p)
 await select(p,80);await dready(p);const url=p.url();await p.goBack();await ready(p);assert.equal(await p.locator('#admin-member-detail').count(),0);await p.goForward();await dready(p);assert.equal(p.url(),url)
 await p.reload({waitUntil:"domcontentloaded"});await dready(p);await button(p,'닫기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});assert.equal(new URL(p.url()).searchParams.get('page'),'1');assert.equal(new URL(p.url()).searchParams.get('keyword'),'소리')
 await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);await p.goBack();await ready(p);assert.equal(await label(p,'가입일 정렬').inputValue(),'OLDEST')
})
await run('late list and detail responses ignored even when abort is ignored',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p)
 const g=gate();s.detail=async(r,u)=>{if(u.pathname.endsWith('/100')){g.arrive();await g.promise}return r.fulfill({json:detail(Number(u.pathname.split('/').at(-1)))})}
 await select(p,100);await g.arrived;await select(p,99);await dready(p);g.release();await p.waitForFunction(()=>window.__memberHandled.some(u=>u.endsWith('/100')));await frames(p);assert(await p.locator('#admin-member-detail').getByText('소리나무 99',{exact:true}).first().isVisible());assert.equal(await p.locator('#admin-member-detail').getByText('소리나무 100',{exact:true}).count(),0)
 const h=gate();s.list=async(r,u)=>{if(u.searchParams.get('status')==='SUSPENDED'){h.arrive();await h.promise;return r.fulfill(error(500,'INTERNAL_ERROR'))}return r.fulfill({json:page(0,[item(55)])})}
 await label(p,'회원 상태').selectOption('SUSPENDED');await h.arrived;await label(p,'회원 상태').selectOption('ACTIVE');await ready(p);h.release();await p.waitForFunction(()=>window.__memberHandled.some(u=>u.includes('status=SUSPENDED')));await frames(p);assert(await p.locator('[data-member-select="55"]').isVisible());assert.equal(await p.locator('.admin-members').getByRole('alert').count(),0)
})
for(const code of ['ACCESS_DENIED','ADMIN_REQUIRED','AUTHENTICATION_REQUIRED'])await run('security clears both regions '+code,async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);s.detail=r=>r.fulfill(error(code==='AUTHENTICATION_REQUIRED'?401:403,code));await select(p,100);await p.getByRole('heading',{name:code==='AUTHENTICATION_REQUIRED'?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('tbody tr').count(),0);assert.equal(await p.locator('#admin-member-detail').count(),0)
})
await run('detail 404 and list network failure have explicit retry, empty is separate',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);s.detail=r=>r.fulfill(error(404,'MEMBER_NOT_FOUND'));await select(p,100);await p.getByText('회원을 찾을 수 없습니다.',{exact:true}).waitFor();s.detail=null;await button(p,'상세 다시 시도').click();await dready(p);await button(p,'닫기').click()
 s.list=r=>r.abort('failed');await label(p,'회원 상태').selectOption('ACTIVE');await p.locator('.admin-members').getByRole('alert').waitFor();await p.screenshot({path:out+'/list-error.png',fullPage:true})
 s.list=r=>r.fulfill({json:{items:[],page:0,size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false}});await button(p,'목록 다시 시도').click();await ready(p);await p.getByText('조회 결과가 없습니다.').waitFor();assert.equal(await p.locator('.admin-members').getByRole('alert').count(),0);await p.screenshot({path:out+'/empty.png',fullPage:true})
})
for(const width of [1920,1440,1280,1024,768,390,320])await run('layout and keyboard '+width,async()=>{
 const{p,s}=await setup(width);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);await frames(p);await p.screenshot({path:`${out}/list-${width}.png`,fullPage:true})
 await p.locator('[data-member-select="100"]').focus();await p.keyboard.press('Enter');await dready(p);await frames(p)
 const metrics=await p.evaluate(()=>({width:document.querySelector('.admin-members').getBoundingClientRect().width,compact:document.querySelector('.admin-members').dataset.compact,overflow:document.documentElement.scrollWidth>innerWidth,detail:document.querySelector('#admin-member-detail').getBoundingClientRect().toJSON()}));assert.equal(metrics.overflow,false);assert.equal(metrics.compact,String(metrics.width<1050))
 assert(metrics.detail.width>0);await p.screenshot({path:`${out}/detail-${width}.png`,fullPage:true});await fs.writeFile(`${out}/measure-${width}.json`,JSON.stringify(metrics,null,2))
 await button(p,metrics.compact==='true'?'회원 목록으로 돌아가기':'닫기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});assert.equal(await p.locator('[data-member-select="100"]').evaluate(e=>e===document.activeElement),true);assert.equal(reads(s).length,1);assert.deepEqual(s.errors,[])
})
await run('resize keeps selection and does not duplicate API requests',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);await select(p,100);await dready(p);const n=s.calls.length,url=p.url();await p.setViewportSize({width:390,height:844});await button(p,'회원 목록으로 돌아가기').waitFor();await p.setViewportSize({width:1920,height:1080});await button(p,'닫기').waitFor();assert.equal(s.calls.length,n);assert.equal(p.url(),url)
})
await run('late detail after close cannot reopen selection; late list success cannot replace latest',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p)
 const g=gate();s.detail=async r=>{g.arrive();await g.promise;return r.fulfill({json:detail(100)})}
 await select(p,100);await g.arrived;await button(p,'닫기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});g.release();await p.waitForFunction(()=>window.__memberHandled.some(u=>u.endsWith('/100')));await frames(p);assert.equal(await p.locator('#admin-member-detail').count(),0)
 const h=gate();s.list=async(r,u)=>{if(u.searchParams.get('role')==='ADMIN'){h.arrive();await h.promise;return r.fulfill({json:page(0,[item(77)])})}return r.fulfill({json:page(0,[item(66)])})}
 await label(p,'권한').selectOption('ADMIN');await h.arrived;await label(p,'권한').selectOption('USER');await ready(p);h.release();await p.waitForFunction(()=>window.__memberHandled.some(u=>u.includes('role=ADMIN')));await frames(p);assert.equal(await p.locator('[data-member-select="77"]').count(),0);assert(await p.locator('[data-member-select="66"]').isVisible())
})
await run('logout clears member data and ignores delayed 401 after navigation',async()=>{
 const{p,s}=await setup(1920);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p)
 const g=gate();s.detail=async r=>{g.arrive();await g.promise;return r.fulfill(error(401,'AUTHENTICATION_REQUIRED'))}
 await select(p,100);await g.arrived;await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);await p.locator('.admin-results[aria-busy="false"]').waitFor();g.release();await p.waitForFunction(()=>window.__memberHandled.some(u=>u.endsWith('/100')));await frames(p);assert.equal(await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).count(),0)
 await p.getByRole('link',{name:'회원 관리',exact:true}).click();await ready(p);await button(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor();assert.equal(await p.locator('tbody tr').count(),0)
})
await run('clipboard success, network detail retry, selected URL and narrow scroll restore',async()=>{
 const{p,s}=await setup(390);await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);await p.locator('[data-member-select="81"]').scrollIntoViewIfNeeded();const before=await p.evaluate(()=>scrollY);await select(p,81);await dready(p)
 await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async value=>{window.__copied=value}}}));await button(p,'이메일 복사').click();await button(p,'✓ 복사됨').waitFor();assert.equal(await p.evaluate(()=>window.__copied),'member.81@example.test')
 await button(p,'회원 목록으로 돌아가기').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});assert(Math.abs((await p.evaluate(()=>scrollY))-before)<2);assert.equal(await p.locator('[data-member-select="81"]').evaluate(e=>e===document.activeElement),true)
 s.detail=r=>r.abort('failed');await select(p,81);await p.locator('#admin-member-detail [role="alert"]').waitFor();s.detail=null;await button(p,'상세 다시 시도').click();await dready(p);assert.equal(new URL(p.url()).searchParams.get('selected'),'81')
})
for(const role of [null,'USER'])await run('non administrator access '+role,async()=>{
 const{p,s}=await setup();s.member=role?{...admin,role}:null;await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await p.getByRole('heading',{name:role?'관리자 권한이 없습니다':'로그인이 필요합니다',exact:true}).waitFor();assert.equal(reads(s).length,0);assert.equal(await p.locator('.admin-members').count(),0)
})
for(const width of [1920,390])await run('long member fields remain readable '+width,async()=>{
 const{p,s}=await setup(width),nickname='함께 소통하는 회원 '.repeat(8),email='member.'+'a'.repeat(180)+'@example.test'
 s.list=r=>r.fulfill({json:page(0,[item(100,{nickname,email,status:'RESTRICTED'})])});s.detail=r=>r.fulfill({json:{...detail(100),nickname,email,status:'RESTRICTED'}})
 await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await select(p,100);await dready(p);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);const emailField=p.locator('#admin-member-detail .admin-member-email-detail>span');assert.equal(await emailField.evaluate(e=>e.firstChild.textContent),email);assert(await emailField.isVisible());await p.screenshot({path:`${out}/long-${width}.png`,fullPage:true})
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));console.log(JSON.stringify({total:results.length,passed:results.filter(r=>r.passed).length,failed:results.filter(r=>!r.passed).length}));if(results.some(r=>!r.passed))process.exitCode=1
