import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-member-ui-finish-20261112/browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[],contexts=[]
const path='/api/v1/admin/members',stamp='2026-11-12T01:00:00Z'
const admin={memberId:7,nickname:'검증관리자',email:'admin@example.test',role:'ADMIN'}
const item=(id,status='ACTIVE')=>({memberId:id,nickname:`소리나무 ${id}`,email:`member.${id}@example.test`,status,role:'USER',createdAt:stamp,updatedAt:stamp,withdrawnAt:null})
const page=(items,n=0,total=items.length)=>({items,page:n,size:20,totalElements:total,totalPages:Math.ceil(total/20),hasNext:n+1<Math.ceil(total/20),hasPrevious:n>0})
const failure=(status,code)=>({status,json:{code,message:'mock failure',fieldErrors:[]}})
const gate=()=>{let arrive,release;return{arrived:new Promise(r=>arrive=r),promise:new Promise(r=>release=r),arrive,release}}
const btn=(p,name)=>p.getByRole('button',{name,exact:true})
const ready=p=>p.locator('.admin-members-list[aria-busy="false"]').waitFor()
const detailReady=p=>p.locator('#admin-member-detail[aria-busy="false"]').waitFor()
const historyReady=p=>p.locator('.admin-member-history[aria-busy="false"]').waitFor()
const frames=p=>p.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))))
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
async function run(name,fn){if(process.env.TEST_FILTER&&!new RegExp(process.env.TEST_FILTER).test(name))return;try{await fn();results.push({name,passed:true});console.log('PASS',name)}catch(e){results.push({name,passed:false,error:e.stack});console.log('FAIL',name,e.message);for(const c of contexts)for(const p of c.pages())await p.screenshot({path:`${out}/failure-${results.length}.png`,fullPage:true}).catch(()=>{})}finally{await Promise.all(contexts.splice(0).map(c=>c.close()))}}

const measurements=[]
async function enter(p){await p.goto(base+'/admin/members',{waitUntil:'domcontentloaded'});await ready(p);await p.locator('[data-member-select="100"]').click();await detailReady(p);await frames(p)}
async function capture(p,name){await p.screenshot({path:out+'/'+name+'.png'});measurements.push({name,...await p.evaluate(()=>{const rect=s=>document.querySelector(s)?.getBoundingClientRect().toJSON();return{width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,profile:rect('.admin-member-profile'),emailTitle:rect('.admin-member-email-detail')?.top,email:rect('.admin-member-email-detail'),operation:rect('.admin-member-operation'),history:rect('.admin-member-history'),dates:rect('.admin-member-dates')}})})}
for(const width of [1440,320])for(const state of ['empty','one','many','loading','error'])await run(`history boundary ${state} ${width}`,async()=>{
 const{p,s}=await setup(width,900);const pending=gate();
 s.logs=Array.from({length:state==='one'?1:state==='many'?3:0},(_,i)=>({logId:i+1,actorMemberId:7,targetMemberId:100,action:'SUSPEND',beforeStatus:'ACTIVE',afterStatus:'SUSPENDED',reason:'반복된 이용 규칙 위반을 확인했습니다.\n재검토 시 참고할 사유입니다.',createdAt:stamp}));
 if(state==='loading')s.history=async r=>{pending.arrive();await pending.promise;return r.fulfill({json:page([])})};
 if(state==='error')s.history=r=>r.fulfill(failure(500,'ERROR'));
 await enter(p);if(state==='loading')await pending.arrived;else await historyReady(p);
 const summary=p.locator('.admin-member-dates summary');assert.equal(await p.locator('.admin-member-dates').getAttribute('open'),null);
 await capture(p,`email-${state}-${width}`);await summary.scrollIntoViewIfNeeded();await capture(p,`boundary-${state}-${width}`);assert(!(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth)));
 if(state==='loading'){assert(await p.getByText('조치 이력을 불러오고 있습니다…',{exact:true}).isVisible());pending.release();await historyReady(p)}
 else if(state==='error'){assert.equal(await p.getByText('회원 조치 이력이 없습니다',{exact:true}).count(),0);assert(await btn(p,'이력 다시 시도').count());s.history=null;await btn(p,'이력 다시 시도').click();await historyReady(p)}
 else assert.equal(await p.locator('.admin-member-history li').count(),s.logs.length);
 await summary.focus();await p.keyboard.press('Enter');assert(await p.getByText('가입 시각',{exact:true}).isVisible());await p.keyboard.press('Enter');assert.equal(await p.locator('.admin-member-dates').getAttribute('open'),null);assert.deepEqual(s.errors,[])
})
for(const width of [1440,320])for(const email of [null,'very.long.email.address.'.repeat(10)+'@example.test'])await run(`email ${email===null?'absent':'long'} ${width}`,async()=>{
 const{p,s}=await setup(width,900);s.members[0].email=email;await enter(p);await historyReady(p);const text=p.locator('.admin-member-email-detail>span');assert.equal(await text.textContent(),email??'이메일 정보 없음');assert.equal(await btn(p,'이메일 복사').count(),email===null?0:1);
 if(email){const b=await btn(p,'이메일 복사').boundingBox();assert(b.height>=42);assert(b.x>=0&&b.x+b.width<=width);assert(await text.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await p.evaluate(()=>Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{}}}));await btn(p,'이메일 복사').click();await btn(p,'✓ 복사됨').waitFor();assert.equal((await btn(p,'✓ 복사됨').boundingBox()).width,b.width)}
 assert(!(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth)));await capture(p,`email-${email===null?'absent':'long'}-${width}`)
})
await run('hover does not select and keyboard selection keeps focus',async()=>{
 const{p}=await setup(1440,900);await enter(p);await historyReady(p);const selected=p.locator('tr[data-selected=true]'),other=p.locator('tr').filter({has:p.locator('[data-member-select="99"]')});
 await other.hover();assert.equal(await selected.locator('[data-member-select]').getAttribute('data-member-select'),'100');await capture(p,'hover-unselected');await selected.hover();assert.equal(await selected.count(),1);await capture(p,'hover-selected');
 await p.locator('[data-member-select="99"]').focus();await p.keyboard.press('Tab');await p.keyboard.press('Shift+Tab');assert(await p.locator('[data-member-select="99"]').evaluate(e=>e===document.activeElement&&e.matches(':focus-visible')));await capture(p,'keyboard-focus');await p.keyboard.press('Enter');await detailReady(p);assert.equal(new URL(p.url()).searchParams.get('selected'),'99')
})
await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify(results,null,2));await fs.writeFile(out+'/measurements.json',JSON.stringify(measurements,null,2));console.log(`${results.filter(r=>r.passed).length}/${results.length} passed`);if(results.some(r=>!r.passed))process.exitCode=1
