import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8479',out=process.env.LOGIN_UI_ARTIFACT_DIR??'/private/tmp/iyum-login-ui/browser'
await fs.mkdir(out,{recursive:true});const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
const member={memberId:7,nickname:'검토회원',email:'review@example.test',role:'USER'},stamp='2026-10-27T04:23:41.115818Z'
const failure=(status,code)=>({status,json:{code,message:'mock failure',fieldErrors:[]}})
const gate=()=>{let release,arrive;return{arrived:new Promise(r=>{arrive=r}),promise:new Promise(r=>{release=r}),arrive:()=>arrive(),release:()=>release()}}
const card=p=>p.locator('.mypage-login-card'),toast=p=>p.locator('.iyum-auth-notice'),button=(p,n)=>p.getByRole('button',{name:n,exact:true})
async function setup(width=1440,anonymous=false){
 const c=await browser.newContext({viewport:{width,height:1000}}),p=await c.newPage();p.setDefaultTimeout(8000)
 const s={anonymous,calls:[],me:null,profile:null,summary:null,logout:null}
 await p.addInitScript(()=>{const original=fetch;window.__handled=0;window.fetch=async(u,o)=>{const res=await original(u,{...o,signal:undefined}),json=res.json.bind(res);res.json=async()=>{const v=await json();setTimeout(()=>window.__handled++,0);return v};return res}})
 await p.route(u=>u.pathname.startsWith('/api/v1/'),async r=>{const path=new URL(r.request().url()).pathname;s.calls.push({path,method:r.request().method()});
  if(path.endsWith('/auth/me'))return s.me?s.me(r):r.fulfill(s.anonymous?failure(401,'AUTHENTICATION_REQUIRED'):{json:member})
  if(path.endsWith('/auth/login')){s.anonymous=false;return r.fulfill({json:member})}
  if(path.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(path.endsWith('/logout')){if(s.logout)return s.logout(r);s.anonymous=true;return r.fulfill({status:204})}
  if(path==='/api/v1/members/me')return s.profile?s.profile(r):r.fulfill({json:{...member,createdAt:stamp}})
  if(path.endsWith('/activity-summary'))return s.summary?s.summary(r):r.fulfill({json:{postCount:0,commentCount:0}})
  if(path==='/api/v1/members/me/posts'||path==='/api/v1/members/me/comments')return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}})
  return r.fulfill(failure(404,'NOT_FOUND'))
 });return{p,s}
}
async function logout(p,width=1440){if(width<900)await button(p,'메뉴 열기').click();await button(p,'로그아웃').click()}
async function run(name,fn){try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.message});console.log('FAIL',name,e.message);process.exitCode=1}finally{for(const c of browser.contexts())await c.close()}}
for(const width of [1440,390,320]){
 await run(`anonymous card, safe links and no notification ${width}`,async()=>{const{p,s}=await setup(width,true);await p.goto(base+'/mypage');await card(p).waitFor();assert.equal(await toast(p).count(),0);assert.equal(await p.getByRole('dialog').count(),0);assert.equal(await card(p).getByRole('link',{name:'로그인하기'}).getAttribute('href'),'/login?from=%2Fmypage');assert.equal(await card(p).getByRole('link',{name:'커뮤니티 둘러보기'}).getAttribute('href'),'/community');assert(!s.calls.some(c=>c.path.startsWith('/api/v1/members/')));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/anonymous-${width}.png`,fullPage:true});await card(p).getByRole('link',{name:'로그인하기'}).click();await p.waitForURL(u=>u.pathname==='/login'&&u.searchParams.get('from')==='/mypage')})
 await run(`confirmed logout clears private content; compact success ${width}`,async()=>{const{p,s}=await setup(width);await p.goto(base+'/mypage');await p.locator('.mypage-name').waitFor();await logout(p,width);await card(p).waitFor();await toast(p).waitFor();assert.equal(await toast(p).getAttribute('role'),'status');assert.equal(await toast(p).locator('.iyum-notice-message').innerText(),'로그아웃되었습니다.');assert.equal(await p.locator('.mypage-profile,.mypage-workspace').count(),0);const box=await toast(p).boundingBox(),header=await p.locator('.iyum-header').boundingBox();assert(box.y>=header.y+header.height);assert(box.x>=0&&box.x+box.width<=width);assert.equal(await toast(p).locator('.iyum-notice-close').evaluate(e=>document.activeElement===e),false);assert.equal(s.calls.filter(c=>c.path.endsWith('/logout')).length,1);await p.screenshot({path:`${out}/logout-${width}.png`,fullPage:true});await button(p,'알림 닫기').click();await toast(p).waitFor({state:'detached'});await p.reload();await card(p).waitFor();assert.equal(await toast(p).count(),0)})
 await run(`explicit private 401 expiration once, neutral icon ${width}`,async()=>{const{p,s}=await setup(width),g=gate();s.profile=async r=>{g.arrive();await g.promise;await r.fulfill(failure(401,'AUTHENTICATION_REQUIRED'))};await p.goto(base+'/mypage');await g.arrived;g.release();await card(p).waitFor();await toast(p).waitFor();assert.match(await toast(p).innerText(),/로그인 시간이 만료되었어요/);assert(await toast(p).evaluate(e=>e.classList.contains('is-info')));assert.equal(await toast(p).count(),1);assert.equal(s.calls.filter(c=>c.path.endsWith('/logout')).length,0);await p.screenshot({path:`${out}/expiry-${width}.png`,fullPage:true});await button(p,'알림 닫기').click();await toast(p).waitFor({state:'detached'})})
}
await run('checking and auth lookup failure never render anonymous card',async()=>{const{p,s}=await setup(),g=gate();s.me=async r=>{g.arrive();await g.promise;await r.fulfill(failure(500,'INTERNAL_SERVER_ERROR'))};await p.goto(base+'/mypage');await g.arrived;assert.equal(await card(p).count(),0);g.release();await p.getByText('로그인 상태를 확인하지 못했어요.').waitFor();assert.equal(await card(p).count(),0);assert.equal(await toast(p).count(),0);s.me=null;await button(p,'로그인 상태 다시 확인').click();await p.locator('.mypage-profile').waitFor()})
await run('logout CSRF failure stays authenticated with persistent error',async()=>{const{p,s}=await setup();s.logout=r=>r.fulfill(failure(403,'CSRF_TOKEN_INVALID'));await p.goto(base+'/mypage');await p.locator('.mypage-profile').waitFor();await logout(p);await toast(p).waitFor();assert.equal(await toast(p).getAttribute('role'),'alert');assert.equal(await card(p).count(),0);assert.equal(await p.getByText('로그아웃되었습니다.',{exact:true}).count(),0)})
await run('private 403 and server failure are not expiration',async()=>{const{p,s}=await setup();s.profile=r=>r.fulfill(failure(403,'ACCESS_DENIED'));await p.goto(base+'/mypage');await button(p,'회원 정보 다시 시도').waitFor();assert.equal(await card(p).count(),0);assert.equal(await toast(p).count(),0)})
await run('late private response after logout cannot reveal member again',async()=>{const{p,s}=await setup(),g=gate();s.profile=async r=>{g.arrive();await g.promise;await r.fulfill({json:{...member,createdAt:stamp}})};await p.goto(base+'/mypage');await g.arrived;await logout(p);await card(p).waitFor();const before=await p.evaluate(()=>window.__handled);g.release();await p.waitForFunction(n=>window.__handled>n,before);assert.equal(await p.locator('.mypage-profile').count(),0);assert.equal(await toast(p).count(),1)})
await run('hover and focus independently pause; fade and route change never restart timer',async()=>{const{p}=await setup();await p.clock.install({time:new Date('2030-01-01T00:00:00Z')});await p.goto(base+'/mypage');await p.locator('.mypage-profile').waitFor();await p.clock.pauseAt(new Date('2030-01-02T00:00:00Z'));await logout(p);await toast(p).waitFor();await p.clock.runFor(2000);await toast(p).hover();await button(p,'알림 닫기').focus();await p.clock.runFor(10000);assert(await toast(p).isVisible());await p.mouse.move(0,0);await p.clock.runFor(6000);assert(await toast(p).isVisible());await card(p).getByRole('link',{name:'로그인하기'}).focus();await p.clock.runFor(2999);assert(await toast(p).isVisible());await p.clock.runFor(1);assert.match(await toast(p).getAttribute('class'),/is-leaving/);await p.clock.runFor(200);await toast(p).waitFor({state:'detached'})})
await run('success toast survives client route change without duplicate or restart; reduced motion',async()=>{const{p}=await setup();await p.emulateMedia({reducedMotion:'reduce'});await p.clock.install({time:new Date('2030-01-01T00:00:00Z')});await p.goto(base+'/mypage');await p.locator('.mypage-profile').waitFor();await p.clock.pauseAt(new Date('2030-01-02T00:00:00Z'));await logout(p);await toast(p).waitFor();await p.clock.runFor(4000);await card(p).getByRole('link',{name:'로그인하기'}).click();await p.waitForURL(u=>u.pathname==='/login');assert.equal(await toast(p).count(),1);assert(await toast(p).evaluate(e=>parseFloat(getComputedStyle(e).transitionDuration)<=0.00001 && getComputedStyle(e).transform==='none'));await p.clock.runFor(1200);await toast(p).waitFor({state:'detached'})})
async function mobileNotice(){
 const {p}=await setup(390)
 await p.clock.install({time:new Date('2030-01-01T00:00:00Z')})
 await p.goto(base+'/mypage');await p.locator('.mypage-profile').waitFor()
 await p.clock.pauseAt(new Date('2030-01-02T00:00:00Z'))
 await logout(p,390);await toast(p).waitFor();await p.mouse.move(0,0)
 await p.clock.runFor(2000);p.noticeBox=await toast(p).boundingBox();await button(p,'메뉴 열기').click();await p.getByRole('dialog').waitFor()
 return p
}
for(const interaction of ['hover','focus']) await run(`mobile ${interaction} owner is released on Escape; remaining budget survives relocation`,async()=>{
 const p=await mobileNotice()
 if(interaction==='hover')await toast(p).hover();else await button(p,'알림 닫기').focus()
 await p.clock.runFor(10000);assert(await toast(p).isVisible())
 await p.keyboard.press('Escape');await p.getByRole('dialog').waitFor({state:'detached'})
 await p.mouse.move(0,0);await p.clock.runFor(32)
 assert.equal(await toast(p).count(),1)
 assert.equal(await toast(p).evaluate(e=>e.contains(document.activeElement)),false)
 await p.clock.runFor(2800);assert(await toast(p).isVisible())
 await p.clock.runFor(400);await toast(p).waitFor({state:'detached'})
})
await run('replacement DOM actual hover and focus pause independently, manual dismiss cleans timer',async()=>{
 const p=await mobileNotice();await button(p,'알림 닫기').focus()
 await p.keyboard.press('Escape');await p.getByRole('dialog').waitFor({state:'detached'})
 await toast(p).hover();await button(p,'알림 닫기').focus()
 await p.clock.runFor(10000);assert(await toast(p).isVisible())
 await p.mouse.move(0,0);await p.clock.runFor(10000);assert(await toast(p).isVisible())
 await button(p,'메뉴 열기').focus();await toast(p).hover()
 await p.clock.runFor(10000);assert(await toast(p).isVisible())
 await button(p,'알림 닫기').click();await toast(p).waitFor({state:'detached'})
 await p.clock.runFor(10000);assert.equal(await toast(p).count(),0)
})
await run('pointer already over replacement DOM pauses without a new mouseenter',async()=>{
 const p=await mobileNotice();await p.mouse.move(p.noticeBox.x+p.noticeBox.width/2,p.noticeBox.y+p.noticeBox.height/2)
 await p.keyboard.press('Escape');await p.getByRole('dialog').waitFor({state:'detached'})
 await p.clock.runFor(32)
 assert(await toast(p).evaluate((e,b)=>e.contains(document.elementFromPoint(b.x+b.width/2,b.y+b.height/2)),p.noticeBox))
 await p.clock.runFor(10000);assert(await toast(p).isVisible())
 await p.mouse.move(0,0);await p.clock.runFor(3200);await toast(p).waitFor({state:'detached'})
})
for(const underPointer of [false,true]) await run(`new notice ignores previous coordinates; actual interaction ${underPointer}`,async()=>{
 const {p,s}=await setup()
 await p.clock.install({time:new Date('2030-01-01T00:00:00Z')})
 await p.goto(base+'/mypage');await p.locator('.mypage-profile').waitFor()
 await p.clock.pauseAt(new Date('2030-01-02T00:00:00Z'))
 await logout(p);await toast(p).waitFor();const box=await toast(p).boundingBox()
 await button(p,'알림 닫기').click();await toast(p).waitFor({state:'detached'})
 const point=underPointer?{x:box.x+box.width/2,y:box.y+box.height/2}:{x:0,y:0}
 await p.mouse.move(point.x,point.y)
 const marker=await p.evaluate(()=>window.__navigationMarker=Math.random())
 // Keyboard navigation keeps the pointer stationary throughout the new login/logout.
 const link=card(p).getByRole('link',{name:'로그인하기'})
 await link.focus();await link.press('Enter');await p.waitForURL(u=>u.pathname==='/login')
 await p.locator('[name=email]').fill('review@example.test');await p.locator('[name=password]').fill('Mock-only-Password1!')
 await p.getByRole('main').getByRole('button',{name:'로그인',exact:true}).press('Enter')
 await p.waitForURL(u=>u.pathname==='/mypage');await p.locator('.mypage-profile').waitFor()
 assert.equal(await p.evaluate(()=>window.__navigationMarker),marker)
 const g=gate();s.logout=async r=>{g.arrive();await g.promise;s.anonymous=true;await r.fulfill({status:204})}
 await button(p,'로그아웃').focus();await button(p,'로그아웃').press('Enter');await g.arrived;g.release()
 await toast(p).waitFor();await card(p).waitFor();await p.clock.runFor(32)
 assert.equal(await toast(p).evaluate((e,q)=>e.contains(document.elementFromPoint(q.x,q.y)),point),underPointer)
 assert.equal(await toast(p).evaluate(e=>e.contains(document.activeElement)),false)
 if(underPointer){
  await p.clock.runFor(10000);assert(await toast(p).isVisible())
  await button(p,'알림 닫기').focus();await p.mouse.move(0,0)
  await p.clock.runFor(10000);assert(await toast(p).isVisible())
  await card(p).getByRole('link',{name:'로그인하기'}).focus()
 }
 await p.clock.runFor(4900);assert(await toast(p).isVisible())
 await p.clock.runFor(300);await toast(p).waitFor({state:'detached'})
 assert.equal(s.calls.filter(c=>c.path.endsWith('/logout')).length,2)
})
await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));console.log(`${results.filter(r=>r.pass).length}/${results.length}`)
