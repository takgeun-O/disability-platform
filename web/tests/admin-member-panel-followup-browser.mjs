import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-followup-20261112/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[],contexts=[]
const path='/api/v1/admin/members',stamp='2026-11-12T01:00:00Z'
const admin={memberId:7,nickname:'검증관리자',email:'admin@example.test',role:'ADMIN'}
const item=(id,status='ACTIVE')=>({memberId:id,nickname:`소리나무 ${id}`,email:`member.${id}@example.test`,status,role:'USER',createdAt:stamp,updatedAt:stamp,withdrawnAt:null})
const page=(items,n=0,total=items.length)=>({items,page:n,size:20,totalElements:total,totalPages:Math.ceil(total/20),hasNext:n+1<Math.ceil(total/20),hasPrevious:n>0})
const failure=(status,code)=>({status,json:{code,message:'mock failure',fieldErrors:[]}})
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),promise:new Promise(r=>release=r),arrive,release}}
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const field=p=>p.getByRole('textbox',{name:'처리 사유 (필수)',exact:true})
const ready=p=>p.locator('.admin-members-list[aria-busy="false"]').waitFor()
const detailReady=p=>p.locator('#admin-member-detail[aria-busy="false"]').waitFor()
const historyReady=p=>p.locator('.admin-member-history[aria-busy="false"]').waitFor()
const frames=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
const posts=s=>s.calls.filter(c=>c.method==='POST'&&c.url.startsWith(path))
async function setup(width=1920,height=1080){
 const c=await browser.newContext({viewport:{width,height}});contexts.push(c);const p=await c.newPage();p.setDefaultTimeout(10000)
 const s={members:[item(100),item(99,'SUSPENDED')],logs:[],calls:[],post:null,list:null,detail:null,history:null,member:admin,errors:[]}
 p.on('pageerror',e=>s.errors.push(e.message))
 await p.addInitScript(()=>{const original=fetch;window.__handled=[];window.fetch=async(u,o)=>{const r=await original(u,String(u).includes('/admin/members')?{...o,signal:undefined}:o);if(!r.ok)queueMicrotask(()=>window.__handled.push(String(u)));const json=r.json.bind(r);r.json=async()=>{const value=await json();queueMicrotask(()=>window.__handled.push(String(u)));return value};return r}})
 await c.route('http://127.0.0.1:8082/**',async r=>{
  const u=new URL(r.request().url()),method=r.request().method();s.calls.push({url:u.pathname+u.search,method,body:method==='POST'?r.request().postDataJSON():undefined})
  if(u.pathname.endsWith('/auth/me'))return r.fulfill(s.member?{json:s.member}:failure(401,'AUTHENTICATION_REQUIRED'))
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/login')){s.member={...admin,memberId:8,nickname:'새 관리자'};return r.fulfill({json:s.member})}
  if(u.pathname.endsWith('/logout')){s.member=null;return r.fulfill({status:204})}
  if(u.pathname===path){if(s.list)return s.list(r,u);const items=s.members.filter(m=>!u.searchParams.get('status')||m.status===u.searchParams.get('status'));return r.fulfill({json:page(items,Number(u.searchParams.get('page')))})}
  if(u.pathname.endsWith('/moderation-logs')){assert.equal(u.searchParams.has('sort'),false);if(s.history)return s.history(r,u);const id=Number(u.pathname.split('/').at(-2));return r.fulfill({json:page(s.logs.filter(l=>l.targetMemberId===id),Number(u.searchParams.get('page')))})}
  if(method==='POST'&&u.pathname.startsWith(path+'/')){
   assert(r.request().headers()['x-csrf-token']);assert.deepEqual(Object.keys(r.request().postDataJSON()),['reason'])
   if(s.post)return s.post(r,u)
   const id=Number(u.pathname.split('/').at(-2)),m=s.members.find(m=>m.memberId===id),status=u.pathname.endsWith('/suspend')?'SUSPENDED':'ACTIVE',changed=m.status!==status,before=m.status
   m.status=status;m.updatedAt='2026-11-12T02:00:00Z'
   if(changed)s.logs.unshift({logId:s.logs.length+1,actorMemberId:7,targetMemberId:id,action:status==='SUSPENDED'?'SUSPEND':'LIFT_SUSPENSION',beforeStatus:before,afterStatus:status,reason:r.request().postDataJSON().reason,createdAt:m.updatedAt})
   return r.fulfill({json:{memberId:id,status,changed}})
  }
  if(u.pathname.startsWith(path+'/')){if(s.detail)return s.detail(r,u);const m=s.members.find(m=>m.memberId===Number(u.pathname.split('/').at(-1)));return r.fulfill(m?{json:m}:failure(404,'MEMBER_NOT_FOUND'))}
  if(u.pathname==='/api/v1/admin/posts')return r.fulfill({json:page([])})
  return r.fulfill(failure(404,'NOT_FOUND'))
 })
 return{p,s,c}
}
async function open(p,id=100){await p.goto(base+'/admin/members');await ready(p);await p.locator(`[data-member-select="${id}"]`).click();await detailReady(p);await historyReady(p)}
async function form(p,action='이용 정지'){await btn(p,action).click();await field(p).waitFor()}
async function run(name,fn){if(process.env.TEST_FILTER&&!new RegExp(process.env.TEST_FILTER).test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message);for(const c of contexts)for(const p of c.pages())await p.screenshot({path:`${out}/failure-${results.length}.png`,fullPage:true}).catch(()=>{})}finally{await Promise.all(contexts.splice(0).map(c=>c.close()))}}
await run('recovery success clears read failure and partial failure stays locked',async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('검토 사유');s.detail=r=>r.fulfill(failure(500,'ERROR'));await btn(p,'이용 정지 실행').click();await btn(p,'최신 상태·이력 다시 조회').waitFor();await p.getByText(/조치는 완료/).waitFor();
 s.detail=null;s.history=r=>r.fulfill(failure(500,'ERROR'));await btn(p,'최신 상태·이력 다시 조회').click();await btn(p,'최신 상태·이력 다시 조회').waitFor();assert(await btn(p,'이용 정지 해제').isDisabled());assert.equal(posts(s).length,1);
 s.history=null;await btn(p,'최신 상태·이력 다시 조회').click();await p.locator('.admin-member-recovery').waitFor({state:'detached'});assert.equal(await p.getByText(/조회에 실패|불러오지 못했습니다/).count(),0);assert(!(await btn(p,'이용 정지 해제').isDisabled()));assert.equal(posts(s).length,1)
})
await run('same member reopen invalidates old history success and error',async()=>{
 const{p,s}=await setup();s.logs=[{logId:1,actorMemberId:8,targetMemberId:100,action:'SUSPEND',beforeStatus:'ACTIVE',afterStatus:'SUSPENDED',reason:'이전 열기의 이력',createdAt:stamp}];await open(p);await btn(p,'닫기').click();const g=gate();s.history=async r=>{g.arrive();await g.promise;return r.fulfill(failure(500,'ERROR'))};await p.locator('[data-member-select="100"]').click();await g.arrived;assert.equal(await p.locator('.admin-member-history').getAttribute('aria-busy'),'true');assert.equal(await p.getByText('사유: 이전 열기의 이력',{exact:true}).count(),0);g.release();await btn(p,'이력 다시 시도').waitFor();await btn(p,'닫기').click();const h=gate();s.history=async r=>{h.arrive();await h.promise;return r.fulfill({json:page([])})};await p.locator('[data-member-select="100"]').click();await h.arrived;assert.equal(await p.locator('.admin-member-history').getAttribute('aria-busy'),'true');assert.equal(await btn(p,'이력 다시 시도').count(),0);h.release();await p.getByText('회원 조치 이력이 없습니다',{exact:true}).waitFor()
})
await run('A B A openings reject delayed success and preserve new loading',async()=>{
 const{p,s}=await setup(),old=gate(),latest=gate();let a=0
 const log=(reason)=>({logId:1,actorMemberId:8,targetMemberId:100,action:'SUSPEND',beforeStatus:'ACTIVE',afterStatus:'SUSPENDED',reason,createdAt:stamp})
 s.history=async(r,u)=>{if(u.pathname.includes('/100/')){const g=++a===1?old:latest;g.arrive();await g.promise;return r.fulfill({json:page([log(g===old?'오래된 응답':'최신 열기의 이력')])})}return r.fulfill({json:page([])})}
 await p.goto(base+'/admin/members');await ready(p);await p.locator('[data-member-select="100"]').click();await old.arrived;await p.locator('[data-member-select="99"]').click();await historyReady(p);await p.locator('[data-member-select="100"]').click();await latest.arrived;old.release();await p.waitForFunction(()=>window.__handled.some(u=>u.includes('/100/moderation-logs')));await frames(p);assert.equal(await p.locator('.admin-member-history').getAttribute('aria-busy'),'true');assert.equal(await p.getByText('사유: 오래된 응답',{exact:true}).count(),0);latest.release();await p.getByText('사유: 최신 열기의 이력',{exact:true}).waitFor();assert.equal(posts(s).length,0)
})
await run('uncertain POST recovery clears outdated error without claiming success',async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('유지되는 사유');s.post=r=>r.abort('failed');await btn(p,'이용 정지 실행').click();await p.locator('.admin-member-action-error').waitFor();await btn(p,'최신 상태·이력 다시 조회').click();await p.locator('.admin-member-recovery').waitFor({state:'detached'});await p.getByText('최신 상태와 이력을 확인했습니다. 현재 표시된 내용을 확인해 주세요.',{exact:true}).waitFor();assert.equal(await p.locator('.admin-member-action-error').count(),0);assert.equal(await p.getByText(/완료했습니다|조치는 완료/).count(),0);assert.equal(await field(p).inputValue(),'유지되는 사유');assert.equal(posts(s).length,1)
})
await run('history page and manual recovery converge once to first page',async()=>{
 const{p,s}=await setup();const log={logId:1,actorMemberId:8,targetMemberId:100,action:'SUSPEND',beforeStatus:'ACTIVE',afterStatus:'SUSPENDED',reason:'이력 페이지 검증',createdAt:stamp};s.history=(r,u)=>r.fulfill({json:page([log],Number(u.searchParams.get('page')),21)});await open(p);await p.locator('.admin-member-history').getByRole('button',{name:'다음',exact:true}).click();await p.locator('.admin-member-history').getByText('2 / 2 페이지',{exact:true}).waitFor();await form(p);await field(p).fill('결과 확인');s.post=r=>r.abort('failed');await btn(p,'이용 정지 실행').click();await p.locator('.admin-member-action-error').waitFor();const g=gate();s.history=async(r,u)=>{g.arrive();await g.promise;return r.fulfill({json:page([log],Number(u.searchParams.get('page')),21)})};const before=s.calls.filter(c=>c.url.includes('/moderation-logs')).length;await btn(p,'최신 상태·이력 다시 조회').click();await g.arrived;assert.equal(await p.locator('.admin-member-history').getAttribute('aria-busy'),'true');g.release();await p.locator('.admin-member-history').getByText('1 / 2 페이지',{exact:true}).waitFor();await frames(p);assert.equal(s.calls.filter(c=>c.url.includes('/moderation-logs')).length-before,1);assert.equal(posts(s).length,1)
 s.history=r=>r.fulfill(failure(500,'ERROR'));await p.locator('.admin-member-history').getByRole('button',{name:'다음',exact:true}).click();await btn(p,'이력 다시 시도').waitFor();s.history=(r,u)=>r.fulfill({json:page([log],Number(u.searchParams.get('page')),21)});await btn(p,'이력 다시 시도').click();await p.locator('.admin-member-history').getByText('2 / 2 페이지',{exact:true}).waitFor()
})
await run('copy success timer restarts and keeps button geometry',async()=>{
 const{p}=await setup();await open(p);await p.evaluate(()=>document.fonts.ready);await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{}}}));await p.clock.install({time:new Date('2026-11-12T03:00:00Z')});await p.clock.pauseAt(new Date('2026-11-12T03:00:01Z'));
 const before=await btn(p,'이메일 복사').boundingBox(),panelBefore=await p.locator('#admin-member-detail').boundingBox();await btn(p,'이메일 복사').click();await btn(p,'✓ 복사됨').waitFor();const after=await btn(p,'✓ 복사됨').boundingBox();assert.equal(before.width,after.width);assert.equal((await p.locator('#admin-member-detail').boundingBox()).height,panelBefore.height);assert.equal(await p.getByText('이메일을 복사했습니다.',{exact:true}).count(),0);assert.equal(await btn(p,'✓ 복사됨').locator('[role=status]').count(),1);
 await p.clock.runFor(2990);assert(await btn(p,'✓ 복사됨').isVisible());await btn(p,'✓ 복사됨').click();await p.clock.runFor(1000);assert(await btn(p,'✓ 복사됨').isVisible());await p.clock.runFor(2000);await btn(p,'이메일 복사').waitFor();await p.screenshot({path:out+'/copy-restored.png'});await btn(p,'이메일 복사').click();await btn(p,'✓ 복사됨').waitFor();await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:()=>new Promise(r=>window.__copyPending=r)}}));await btn(p,'✓ 복사됨').click();await p.clock.runFor(3000);await btn(p,'이메일 복사').waitFor();await p.evaluate(()=>window.__copyPending());await btn(p,'✓ 복사됨').waitFor();await p.clock.runFor(3000);await btn(p,'이메일 복사').waitFor()
})
await run('late clipboard response and previous timer cannot affect another opening',async()=>{
 const{p}=await setup();await open(p);await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:()=>new Promise(r=>window.__resolveCopy=r)}}));await btn(p,'이메일 복사').click();await p.locator('[data-member-select="99"]').click();await detailReady(p);await p.evaluate(()=>window.__resolveCopy());await frames(p);assert.equal(await btn(p,'✓ 복사됨').count(),0);
 await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{}}}));await btn(p,'이메일 복사').click();await btn(p,'✓ 복사됨').waitFor();await btn(p,'닫기').click();await p.locator('[data-member-select="99"]').click();await detailReady(p);assert.equal(await btn(p,'✓ 복사됨').count(),0);assert(await btn(p,'이메일 복사').isVisible());await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{throw Error('mock')}}}));await btn(p,'이메일 복사').click();await p.getByText('복사하지 못했습니다. 이메일을 직접 선택해 복사해 주세요.',{exact:true}).waitFor();assert.equal(await p.locator('.admin-member-email-detail>span').evaluate(e=>getComputedStyle(e).userSelect),'text')
})
for(const width of [1920,1440,1280,390,320])await run('history hierarchy long reasons and collapsed dates '+width,async()=>{
 const{p,s}=await setup(width,width<500?844:900);const reason='상호 존중을 위한 이용 규칙을 검토했습니다.\n반복된 신고 내용을 확인하고 이용 제한 여부를 재검토합니다. 😀\n'+'긴문자열'.repeat(80);s.logs=Array.from({length:5},(_,i)=>({logId:5-i,actorMemberId:8,targetMemberId:100,action:i%2?'SUSPEND':'LIFT_SUSPENSION',beforeStatus:i%2?'ACTIVE':'SUSPENDED',afterStatus:i%2?'SUSPENDED':'ACTIVE',reason,createdAt:stamp}));await open(p);await frames(p);assert.equal(await p.locator('.admin-member-history li').count(),5);assert.equal(await p.locator('.admin-member-dates').getAttribute('open'),null);
 const order=await p.evaluate(()=>{const op=document.querySelector('.admin-member-operation'),h=document.querySelector('.admin-member-history'),d=document.querySelector('.admin-member-dates');return !!(op.compareDocumentPosition(h)&Node.DOCUMENT_POSITION_FOLLOWING)&&!!(h.compareDocumentPosition(d)&Node.DOCUMENT_POSITION_FOLLOWING)});assert(order);assert.equal(await p.locator('.admin-member-history-heading').getByText('총 5건',{exact:true}).count(),1);assert.equal(await p.locator('.admin-member-history time').first().innerText(),'2026.11.12. 10:00:00');assert.equal(await p.locator('.admin-member-history-reason').first().textContent(),'사유: '+reason);assert.equal(await p.locator('.admin-member-history-reason').first().evaluate(e=>getComputedStyle(e).whiteSpace),'pre-wrap');assert.equal(await p.locator('.admin-member-history').evaluate(e=>getComputedStyle(e).overflowY),'visible');assert(!(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth)));
 const badges=await p.locator('.admin-member-action-badge').evaluateAll(es=>es.slice(0,2).map(e=>getComputedStyle(e).backgroundColor));assert.notEqual(badges[0],badges[1]);await p.screenshot({path:out+`/panel-${width}.png`});await p.locator('.admin-member-history-heading').scrollIntoViewIfNeeded();await p.screenshot({path:out+`/history-${width}.png`});const summary=p.locator('.admin-member-dates summary');await summary.focus();await p.keyboard.press('Enter');assert.equal(await p.locator('.admin-member-dates').getAttribute('open'),'');assert(await p.getByText('가입 시각',{exact:true}).isVisible());await p.keyboard.press('Enter');assert.equal(await p.locator('.admin-member-dates').getAttribute('open'),null);assert.deepEqual(s.errors,[])
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));console.log(`${results.filter(r=>r.passed).length}/${results.length} passed`);if(results.some(r=>!r.passed))process.exitCode=1
