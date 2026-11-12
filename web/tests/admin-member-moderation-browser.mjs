import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-moderation-ui/browser'
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
await run('suspend, lift, payload and refreshed list detail history',async()=>{
 const{p,s}=await setup();await open(p);await p.screenshot({path:out+'/active-detail.png'});await form(p);await field(p).fill('  반복적인 이용 규칙 위반\n재검토 기록  ');await btn(p,'이용 정지 실행').click();await p.getByText('이용 정지를 완료했습니다.',{exact:true}).waitFor();await btn(p,'이용 정지 해제').waitFor();await historyReady(p)
 assert.equal(posts(s).length,1);assert.equal(posts(s)[0].body.reason,'반복적인 이용 규칙 위반\n재검토 기록');assert.equal(await field(p).count(),0);assert(await p.locator('.admin-member-profile [data-status="SUSPENDED"]').isVisible());assert(await p.locator('.admin-member-history').getByText('정상 → 이용 정지',{exact:true}).isVisible())
 await form(p,'이용 정지 해제');await p.screenshot({path:out+'/lift-form.png'});await field(p).fill('재검토 후 해제');await btn(p,'이용 정지 해제 실행').click();await p.getByText('이용 정지 해제를 완료했습니다.',{exact:true}).waitFor();await btn(p,'이용 정지').waitFor();await historyReady(p);assert.equal(posts(s).length,2);assert.equal(await p.locator('.admin-member-history li').count(),2);await p.locator('.admin-member-detail-body').evaluate(e=>e.scrollTop=e.scrollHeight);await p.screenshot({path:out+'/history.png'});assert.deepEqual(s.errors,[])
})
await run('invalid reason focus, repeated blank, spaces and emoji boundary',async()=>{
 const{p,s}=await setup();await open(p);await form(p)
 for(const input of ['','　\n\t','😀'.repeat(501)]){await field(p).fill(input);await btn(p,'이용 정지 실행').click();await p.locator('.admin-member-action-error').waitFor();assert.equal(await field(p).getAttribute('aria-invalid'),'true');assert(await field(p).evaluate(e=>e===document.activeElement));assert.equal(posts(s).length,0)}
 await p.screenshot({path:out+'/reason-error.png'});await field(p).fill('😀'.repeat(500));assert.equal(await field(p).getAttribute('aria-invalid'),'false');await btn(p,'이용 정지 실행').click();await p.getByText('이용 정지를 완료했습니다.',{exact:true}).waitFor();assert.equal([...posts(s)[0].body.reason].length,500)
})
await run('changed false reports no transition and refreshes',async()=>{
 const{p,s}=await setup();s.post=r=>{s.members[0].status='SUSPENDED';return r.fulfill({json:{memberId:100,status:'SUSPENDED',changed:false}})};await open(p);await form(p);await field(p).fill('확인');await btn(p,'이용 정지 실행').click();await p.getByText('이미 이용 정지된 회원입니다.',{exact:true}).waitFor();await btn(p,'이용 정지 해제').waitFor();assert.equal(s.logs.length,0);assert.equal(posts(s).length,1)
})
await run('status filter exclusion retains detail and applied query',async()=>{
 const{p}=await setup();await p.goto(base+'/admin/members?status=ACTIVE&sort=OLDEST&page=0');await ready(p);await p.locator('[data-member-select="100"]').click();await detailReady(p);await form(p);await field(p).fill('상태 확인');await btn(p,'이용 정지 실행').click();await p.getByText('회원 상태가 변경되어 현재 목록의 상태 조건에서 제외되었습니다. 선택한 회원 상세는 유지합니다.').waitFor();assert.equal(await p.locator('[data-member-select="100"]').count(),0);assert.equal(new URL(p.url()).searchParams.get('selected'),'100');assert.equal(new URL(p.url()).searchParams.get('status'),'ACTIVE');assert.equal(new URL(p.url()).searchParams.get('sort'),'OLDEST')
})
await run('duplicate submit and pending menu restriction',async()=>{
 const{p,s}=await setup(),g=gate();s.post=async r=>{g.arrive();await g.promise;s.members[0].status='SUSPENDED';return r.fulfill({json:{memberId:100,status:'SUSPENDED',changed:true}})};await open(p);await form(p);await field(p).fill('처리 대기');await btn(p,'이용 정지 실행').click();await g.arrived;assert(await btn(p,'처리 중…').isDisabled());await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.getByRole('heading',{name:'처리 중입니다',exact:true}).waitFor();assert.equal(posts(s).length,1);assert(new URL(p.url()).pathname==='/admin/members');await btn(p,'현재 화면에 머무르기').click();g.release();await btn(p,'이용 정지 해제').waitFor();await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/)
})
await run('post success read failure only retries GET and prevents action',async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('정지');s.detail=r=>r.fulfill(failure(500,'ERROR'));await btn(p,'이용 정지 실행').click();await p.getByText('조치는 완료됐습니다. 최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.').waitFor();assert(await btn(p,'이용 정지').isDisabled());s.detail=null;await btn(p,'최신 상태·이력 다시 조회').click();await btn(p,'이용 정지 해제').waitFor();assert.equal(posts(s).length,1)
})
for(const [code,status] of [['INVALID_REASON',400],['CSRF_TOKEN_INVALID',403],['SELF_MODERATION_NOT_ALLOWED',403],['ADMIN_TARGET_NOT_ALLOWED',403],['MEMBER_STATUS_NOT_ALLOWED',409],['MEMBER_NOT_FOUND',404],['ERROR',500],['NETWORK',0]])await run('error policy '+code,async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('입력 유지');s.post=r=>status?r.fulfill(failure(status,code)):r.abort('failed');await btn(p,'이용 정지 실행').click();await p.locator('.admin-member-action-error').waitFor();assert.equal(await field(p).inputValue(),'입력 유지');assert.equal(posts(s).length,1)
 if(code==='INVALID_REASON'){assert(await field(p).evaluate(e=>e===document.activeElement));assert.equal(await field(p).getAttribute('aria-invalid'),'true')}
 if(code==='CSRF_TOKEN_INVALID')assert(!(await btn(p,'이용 정지 실행').isDisabled()))
 if(['SELF_MODERATION_NOT_ALLOWED','ADMIN_TARGET_NOT_ALLOWED'].includes(code))assert(await btn(p,'이용 정지 실행').isDisabled())
 if(status===500||status===0){assert(await btn(p,'이용 정지 실행').isDisabled());await btn(p,'최신 상태·이력 다시 조회').click();await p.locator('.admin-member-recovery').waitFor({state:'detached'});assert.equal(posts(s).length,1)}
})
for(const [code,status] of [['AUTHENTICATION_REQUIRED',401],['ACCESS_DENIED',403],['ADMIN_REQUIRED',403]])await run('security clears sensitive state '+code,async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('사유');s.post=r=>r.fulfill(failure(status,code));await btn(p,'이용 정지 실행').click();await p.getByRole('heading',{name:status===401?'로그인이 필요합니다':'관리자 권한이 없습니다',exact:true}).waitFor();assert.equal(await p.locator('#admin-member-detail').count(),0);assert.equal(await p.locator('tbody tr').count(),0)
})
await run('all unsupported states and self/admin still read history',async()=>{
 const{p,s}=await setup();for(const [id,status,role] of [[7,'ACTIVE','USER'],[100,'ACTIVE','ADMIN'],[100,'PENDING','USER'],[100,'RESTRICTED','USER'],[100,'WITHDRAWN','USER'],[100,'DELETED','USER']]){s.members=[{...item(id,status),role}];await open(p,id);assert.equal(await btn(p,'이용 정지').count(),0);assert.equal(await btn(p,'이용 정지 해제').count(),0);await p.getByText('회원 조치 이력이 없습니다',{exact:true}).waitFor()}
 assert.equal(posts(s).length,0)
})
await run('dirty cancel, member switch, close and menu approval',async()=>{
 const{p}=await setup();await open(p);await form(p);await field(p).fill('보존할 입력');await btn(p,'취소').click();await btn(p,'계속 작성').click();assert.equal(await field(p).inputValue(),'보존할 입력')
 await p.locator('[data-member-select="99"]').click();await btn(p,'계속 작성').click();assert.equal(new URL(p.url()).searchParams.get('selected'),'100')
 await btn(p,'닫기').click();await btn(p,'계속 작성').click();assert.equal(await field(p).inputValue(),'보존할 입력')
 await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await btn(p,'계속 작성').click();assert.equal(await field(p).inputValue(),'보존할 입력')
 await btn(p,'취소').click();await btn(p,'입력 버리고 이동').click();await field(p).waitFor({state:'detached'});assert(await btn(p,'이용 정지').evaluate(e=>e===document.activeElement))
 await form(p);await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);assert.equal(await p.getByRole('dialog').count(),0)
})
await run('dirty browser Back cancellation and approval preserve URL ownership',async()=>{
 const{p}=await setup();await open(p);await form(p);await field(p).fill('뒤로가기 보호');const url=p.url();await p.goBack();await btn(p,'계속 작성').click();await p.waitForURL(url);assert.equal(await field(p).inputValue(),'뒤로가기 보호');await p.goBack();await btn(p,'입력 버리고 이동').click();await p.locator('#admin-member-detail').waitFor({state:'detached'});assert.equal(new URL(p.url()).pathname,'/admin/members');await p.goForward();await detailReady(p);assert.equal(await field(p).count(),0)
})
await run('dirty menu approval navigates once; selected member switch discards only reason',async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('폐기할 사유');await p.locator('[data-member-select="99"]').click();await btn(p,'입력 버리고 이동').click();await detailReady(p);await btn(p,'이용 정지 해제').waitFor();assert.equal(await field(p).count(),0);assert.equal(new URL(p.url()).searchParams.get('selected'),'99');await form(p,'이용 정지 해제');await field(p).fill('다른 사유');await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await btn(p,'입력 버리고 이동').click();await p.waitForURL(/\/admin\/posts/);await p.locator('.admin-results[aria-busy="false"]').waitFor();assert.equal(await p.getByRole('dialog').count(),0);assert.equal(posts(s).length,0);await p.goBack();await detailReady(p);assert.equal(new URL(p.url()).searchParams.get('selected'),'99');assert.equal(await field(p).count(),0)
})
await run('lift changed false preserves no-op history',async()=>{
 const{p,s}=await setup();s.post=r=>{s.members[1].status='ACTIVE';return r.fulfill({json:{memberId:99,status:'ACTIVE',changed:false}})};await open(p,99);await form(p,'이용 정지 해제');await field(p).fill('해제 확인');await btn(p,'이용 정지 해제 실행').click();await p.getByText('이미 이용 정지가 해제된 회원입니다.',{exact:true}).waitFor();await btn(p,'이용 정지').waitFor();assert.equal(s.logs.length,0);assert.equal(posts(s).length,1)
})
await run('history pagination, failure and late old response',async()=>{
 const{p,s}=await setup();await open(p);const g=gate();s.history=async(r,u)=>{if(u.pathname.includes('/100/')){g.arrive();await g.promise;return r.fulfill(failure(500,'ERROR'))}return r.fulfill({json:page([{logId:1,actorMemberId:8,targetMemberId:99,action:'SUSPEND',beforeStatus:'ACTIVE',afterStatus:'SUSPENDED',reason:'두 번째 회원 이력',createdAt:stamp}],Number(u.searchParams.get('page')),21)})}
 await btn(p,'닫기').click();await p.locator('[data-member-select="100"]').click();await g.arrived;await p.locator('[data-member-select="99"]').click();await detailReady(p);await historyReady(p);await p.locator('.admin-member-history').getByText('사유: 두 번째 회원 이력',{exact:true}).waitFor();g.release();await p.waitForFunction(()=>window.__handled.filter(u=>u.includes('/100/moderation-logs')).length>=2);await frames(p);assert.equal(await p.locator('.admin-member-history [role="alert"]').count(),0)
 await p.locator('.admin-member-history').getByRole('button',{name:'다음',exact:true}).click();await historyReady(p);assert(s.calls.at(-1).url.includes('page=1'));s.history=r=>r.fulfill(failure(500,'ERROR'));await p.locator('.admin-member-history').getByRole('button',{name:'이전',exact:true}).click();await btn(p,'이력 다시 시도').waitFor();s.history=null;await btn(p,'이력 다시 시도').click();await p.getByText('회원 조치 이력이 없습니다',{exact:true}).waitFor()
})
await run('last page correction preserves selected detail and does not loop',async()=>{
 const{p,s}=await setup();s.list=(r,u)=>{const n=Number(u.searchParams.get('page'));return r.fulfill({json:s.members[0].status==='ACTIVE'?page(n?[s.members[0]]:[s.members[1]],n,21):page(n?[]:[s.members[1]],n,1)})}
 await p.goto(base+'/admin/members?status=ACTIVE&sort=OLDEST&page=1');await ready(p);await p.locator('[data-member-select="100"]').click();await detailReady(p);await form(p);await field(p).fill('정지 사유');const before=s.calls.filter(c=>c.url.startsWith(path+'?')).length;await btn(p,'이용 정지 실행').click();await p.waitForURL(u=>u.searchParams.get('page')==='0');await ready(p);await btn(p,'이용 정지 해제').waitFor();assert.equal(new URL(p.url()).searchParams.get('selected'),'100');assert.equal(s.calls.filter(c=>c.url.startsWith(path+'?')).length-before,2);assert.equal(posts(s).length,1)
})
await run('404 removes unavailable detail; 409 refreshes unsupported state',async()=>{
 for(const code of ['MEMBER_NOT_FOUND','MEMBER_STATUS_NOT_ALLOWED']){
  const{p,s}=await setup();await open(p);await form(p);await field(p).fill('상태 확인');s.post=r=>{if(code==='MEMBER_NOT_FOUND')s.members=s.members.filter(m=>m.memberId!==100);else s.members[0].status='PENDING';return r.fulfill(failure(code==='MEMBER_NOT_FOUND'?404:409,code))};await btn(p,'이용 정지 실행').click();if(code==='MEMBER_NOT_FOUND'){await p.getByText('회원을 찾을 수 없습니다.',{exact:true}).waitFor();assert.equal(await p.locator('.admin-member-profile').count(),0)}else await p.locator('.admin-member-profile [data-status="PENDING"]').waitFor();assert(await btn(p,'이용 정지 실행').isDisabled());assert.equal(posts(s).length,1)
 }
})
await run('late POST 401 after account refresh cannot expire the new administrator',async()=>{
 const{p,s}=await setup(),g=gate();s.post=async r=>{g.arrive();await g.promise;return r.fulfill(failure(401,'AUTHENTICATION_REQUIRED'))};await open(p);await form(p);await field(p).fill('이전 계정 사유');await btn(p,'이용 정지 실행').click();await g.arrived;await btn(p,'로그아웃').click();await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor();await p.locator('.admin-main').getByRole('link',{name:'로그인',exact:true}).click();await p.locator('input[name=email]').fill('mock@example.test');await p.locator('input[name=password]').fill('Mock-only-password1!');await btn(p,'로그인').click();await ready(p);await p.locator('[data-member-select="100"]').click();await detailReady(p);g.release();await p.waitForFunction(()=>window.__handled.some(u=>u.endsWith('/100/suspend')));await frames(p);assert.equal(await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).count(),0);assert.equal(await field(p).count(),0);assert(await btn(p,'이용 정지').isVisible())
})
await run('history delayed response after menu navigation cannot clear another screen',async()=>{
 const{p,s}=await setup(),g=gate();s.history=async r=>{g.arrive();await g.promise;return r.fulfill(failure(401,'AUTHENTICATION_REQUIRED'))};await p.goto(base+'/admin/members');await ready(p);await p.locator('[data-member-select="100"]').click();await g.arrived;await p.getByRole('link',{name:'게시글 관리',exact:true}).click();await p.waitForURL(/\/admin\/posts/);g.release();await p.waitForFunction(()=>window.__handled.some(u=>u.includes('/100/moderation-logs')));await frames(p);assert.equal(await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).count(),0)
})
await run('normal-length desktop form screenshot and narrow validation visibility',async()=>{
 const{p}=await setup(1920,1080);await open(p);await form(p);await field(p).fill('반복적인 이용 규칙 위반을 확인했습니다.');await p.mouse.move(800,850);await p.mouse.wheel(0,350);await frames(p);await p.screenshot({path:out+'/suspend-readable-1920.png'});
 const narrow=await setup(320,844);await open(narrow.p);await form(narrow.p);await btn(narrow.p,'이용 정지 실행').click();await narrow.p.locator('.admin-member-action-error').waitFor();assert(await field(narrow.p).evaluate(e=>e===document.activeElement));const visible=await narrow.p.locator('.admin-member-action-error').evaluate(e=>{const r=e.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight});assert(visible);await narrow.p.screenshot({path:out+'/reason-error-320.png'})
})
await run('previous success never labels a later uncertain action as completed',async()=>{
 const{p,s}=await setup();await open(p);await form(p);await field(p).fill('첫 번째 처리');await btn(p,'이용 정지 실행').click();await btn(p,'이용 정지 해제').waitFor();await form(p,'이용 정지 해제');await field(p).fill('두 번째 처리');s.post=r=>r.abort('failed');await btn(p,'이용 정지 해제 실행').click();await p.getByText('처리 결과를 확인하지 못했습니다. 최신 상태와 이력을 확인해 주세요. 요청은 자동으로 반복하지 않습니다.',{exact:true}).waitFor();s.detail=r=>r.fulfill(failure(500,'ERROR'));await btn(p,'최신 상태·이력 다시 조회').click();await p.getByText('최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.',{exact:true}).waitFor();assert.equal(await p.getByText('조치는 완료됐습니다. 최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.',{exact:true}).count(),0);assert.equal(posts(s).length,2);assert.equal(await field(p).inputValue(),'두 번째 처리')
})
for(const width of [1920,1440,1280,390,320])await run('form width, footer keyboard and layout '+width,async()=>{
 const{p,s}=await setup(width,width<500?844:900);s.members[0].nickname='소리를 함께 나누는 회원'.repeat(5);s.members[0].email='long.'.repeat(25)+'member@example.test';await open(p);await frames(p)
 const before=await p.locator('#admin-member-detail').boundingBox();await form(p);await field(p).fill('서로 존중하는 대화를 위한 운영 검토입니다.\n'+'긴사유'.repeat(140));await frames(p)
 const after=await p.locator('#admin-member-detail').boundingBox();assert(Math.abs(before.width-after.width)<1);assert(!(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth)))
 await field(p).focus();await p.keyboard.press('Tab');assert(await btn(p,'취소').evaluate(e=>e===document.activeElement));await p.keyboard.press('Tab');assert(await btn(p,'이용 정지 실행').evaluate(e=>e===document.activeElement));
 const rect=await btn(p,'이용 정지 실행').evaluate(e=>{const r=e.getBoundingClientRect();return{top:r.top,bottom:r.bottom,height:innerHeight,hit:e.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2))}});assert(rect.top>=0&&rect.bottom<=rect.height&&rect.hit,'keyboard must reach visible unobscured submit')
 await p.screenshot({path:out+`/suspend-form-${width}.png`});await fs.writeFile(out+`/layout-${width}.json`,JSON.stringify({before,after,submit:rect},null,2));assert.deepEqual(s.errors,[])
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));console.log(`${results.filter(r=>r.passed).length}/${results.length} passed`);if(results.some(r=>!r.passed))process.exitCode=1
