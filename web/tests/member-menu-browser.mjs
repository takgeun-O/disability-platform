import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8443',out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-mypage-menu'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[],captureEvidence=[]
const member={memberId:321,nickname:'검토회원',email:'review@example.test',role:'USER'}
const profile={...member,createdAt:'2026-09-30T16:00:00Z'}
const error=code=>({code,message:'test error',fieldErrors:[]})
const pause=ms=>new Promise(r=>setTimeout(r,ms))
async function fixture({width=390,height=844,...options}={}){
 const ctx=await browser.newContext({viewport:{width,height},reducedMotion:'no-preference'}),p=await ctx.newPage();p.setDefaultTimeout(8000)
 const state={member:{...member},profile:{...profile},summary:{postCount:12,commentCount:38},calls:[],...options}
 await p.route(u=>u.pathname.startsWith('/api/v1/'),async r=>{
  const req=r.request(),u=new URL(req.url());state.calls.push({path:u.pathname,method:req.method()})
  if(u.pathname==='/api/v1/auth/me')return state.me?state.me(r):r.fulfill(state.member?{json:state.member}:{status:401,json:error('AUTHENTICATION_REQUIRED')})
  if(u.pathname.endsWith('/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'mock-only'}})
  if(u.pathname.endsWith('/logout')){if(state.logout)return state.logout(r);state.member=null;return r.fulfill({status:204})}
  if(u.pathname==='/api/v1/members/me/nickname'){
   if(state.put)return state.put(r)
   state.profile.nickname=req.postDataJSON().nickname.trim();state.member.nickname=state.profile.nickname;return r.fulfill({json:state.profile})
  }
  if(u.pathname==='/api/v1/members/me/posts')return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}})
  if(u.pathname.startsWith('/api/v1/community/'))return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}})
  if(u.pathname==='/api/v1/members/me')return r.fulfill({json:state.profile})
  if(u.pathname.endsWith('/activity-summary'))return r.fulfill({json:state.summary})
  throw Error('Unexpected browser API: '+u.pathname)
 })
 await p.goto(base+'/mypage/settings');return{ctx,p,state}
}
const edit=p=>p.getByRole('button',{name:'프로필 수정',exact:true})
const panel=p=>p.getByRole('dialog',{name:'전체 메뉴',exact:true})
const editor=p=>p.getByRole('dialog',{name:'닉네임 수정',exact:true})
const menu=p=>p.getByRole('button',{name:'메뉴 열기',exact:true})
// Settle fonts/layout before screenshots; each canonical size starts in a fresh context.
async function settle(p){await p.evaluate(async()=>{await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))})}
async function ready(p){await p.locator('.mypage-name h2').waitFor()}
async function open(p){await menu(p).click();await panel(p).waitFor();await pause(220)}
async function clean(p){assert.equal(await p.evaluate(()=>document.body.style.position),'');assert.equal(await p.evaluate(()=>document.documentElement.style.overflow),'')}
async function focusWithin(p,el){assert(await el.evaluate(e=>e.contains(document.activeElement)))}
async function check(name,fn){try{await fn();results.push({name,pass:true});console.log('PASS',name)}catch(e){results.push({name,pass:false,error:e.message});console.log('FAIL',name,e.message);process.exitCode=1}finally{for(const c of browser.contexts())await c.close()}}
try{
 await check('desktop information panel: 2-column links, no layout shift, keyboard and outside close',async()=>{
  for(const width of [1440,1024]) {
   const{p,ctx}=await fixture({width,height:900});await ready(p)
   const before=await p.locator('main').boundingBox(),button=p.getByRole('button',{name:'정보 찾기'}),dropdown=p.locator('#iyum-information-menu')
   await button.focus();await p.keyboard.press('Enter');await dropdown.waitFor();await settle(p)
   assert.equal(await dropdown.getByRole('link').count(),4)
   assert.equal(await dropdown.getByRole('link',{name:/이용 가이드/}).getAttribute('href'),'/validation/hearing-aid-health-insurance')
   assert.deepEqual(await p.locator('main').boundingBox(),before)
   const bounds=await dropdown.boundingBox();assert(bounds.x>=0 && bounds.x+bounds.width<=width)
   assert.equal(await p.locator('.iyum-information-links').evaluate(e=>getComputedStyle(e).gridTemplateColumns.split(' ').length),2)
   await p.screenshot({path:`${out}/information-panel-pointer-${width}.png`})
   await p.keyboard.press('Tab');assert(await dropdown.getByRole('link').first().evaluate(e=>e===document.activeElement))
   await p.screenshot({path:`${out}/information-panel-${width}.png`})
   await p.keyboard.press('Escape');assert(await button.evaluate(e=>e===document.activeElement));assert(!await dropdown.isVisible())
   await button.click();await p.mouse.click(4,200);assert(!await dropdown.isVisible());await ctx.close()
  }
 })
 await check('1440/1024/390/320 page + one responsive edit form; layout unchanged behind modal',async()=>{
  for(const width of [1440,1024,390,320]){
   const{p,ctx}=await fixture({width,height:1000});await ready(p);await settle(p);await p.evaluate(()=>scrollTo(0,0));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.screenshot({path:`${out}/page-${width}.png`,fullPage:true,animations:'disabled'})
   const before=await p.locator('.mypage-profile').boundingBox();await edit(p).click();await editor(p).waitFor()
   assert.equal(await p.locator('#my-nickname').count(),1);assert(await p.locator('#my-nickname').evaluate(e=>e===document.activeElement));const after=await p.locator('.mypage-profile').boundingBox();assert.equal(before.y,after.y);assert.equal(before.height,after.height)
   const box=await editor(p).boundingBox();assert(box.width<=width);assert(box.y>=0);if(width>600)assert(Math.abs(box.width-440)<1);else assert(Math.abs(box.y+box.height-1000)<2)
   await p.screenshot({path:`${out}/edit-${width}.png`,animations:'disabled'});await p.keyboard.press('Escape');await editor(p).waitFor({state:'hidden'});assert(await edit(p).evaluate(e=>e===document.activeElement));await clean(p);await ctx.close()
  }
 })
 for(const input of ['mouse','keyboard','unfocused-link'])await check(`desktop current information link closes without navigation (${input})`,async()=>{
  const{p}=await fixture({width:1440,height:600});await ready(p);await p.goto(base+'/welfare');await p.evaluate(()=>scrollTo(0,24));
  const button=p.getByRole('button',{name:'정보 찾기'}),dropdown=p.locator('#iyum-information-menu'),link=dropdown.getByRole('link',{name:/복지·지원정보/});
  if(input==='keyboard'){await button.focus();await p.keyboard.press('Enter');await p.keyboard.press('Tab');assert(await dropdown.getByRole('link',{name:/이용 가이드/}).evaluate(e=>e===document.activeElement));await p.keyboard.press('Tab');assert(await link.evaluate(e=>e===document.activeElement))}else await button.click();
  const before=await p.evaluate(()=>({url:location.href,y:scrollY,history:history.length,time:performance.timeOrigin}));assert(before.y>0);
  let navigations=0;const navigated=frame=>{if(frame===p.mainFrame())navigations++};p.on('framenavigated',navigated);
  if(input==='keyboard')await p.keyboard.press('Enter');else if(input==='mouse')await link.click();else await link.evaluate(e=>{document.querySelector('.iyum-information button').focus();e.click()});
  await dropdown.waitFor({state:'hidden'});assert(await button.evaluate(e=>e===document.activeElement));
  assert.deepEqual(await p.evaluate(()=>({url:location.href,y:scrollY,history:history.length,time:performance.timeOrigin})),before);assert.equal(navigations,0);p.off('framenavigated',navigated);
 })
 await check('information destination preserves query/hash distinction and modified new-tab click',async()=>{
  const{p}=await fixture({width:1440});await ready(p);
  for(const suffix of ['?review=1','#main-content-start']){
   await p.goto(base+'/welfare'+suffix);await p.getByRole('button',{name:'정보 찾기'}).click();await p.locator('#iyum-information-menu').getByRole('link',{name:/복지·지원정보/}).click();
   await p.waitForURL(u=>u.pathname==='/welfare'&&!u.search&&!u.hash);await p.locator('#iyum-information-menu').waitFor({state:'hidden'});
  }
  await p.getByRole('button',{name:'정보 찾기'}).click();const before=p.url();
  // Modified clicks open a tab without an opener: observe the browser context.
  const popupPromise=p.context().waitForEvent('page');await p.locator('#iyum-information-menu').getByRole('link',{name:/복지·지원정보/}).click({modifiers:[process.platform==='darwin'?'Meta':'Control']});
  const popup=await popupPromise;await popup.waitForURL(u=>u.pathname==='/welfare');assert.equal(p.url(),before);assert(await p.locator('#iyum-information-menu').isVisible());await popup.close();
 })
 await check('desktop different information destination keeps dirty form/menu on cancel then navigates on approval',async()=>{
  const{p}=await fixture({width:1440});await ready(p);await p.goto(base+'/community/posts/create');await p.locator('input[name=title]').fill('PC 메뉴 이탈 보호');
  const button=p.getByRole('button',{name:'정보 찾기'}),dropdown=p.locator('#iyum-information-menu');await button.click();await dropdown.getByRole('link',{name:/복지·지원정보/}).click();
  const guard=p.getByRole('dialog',{name:'작성 중인 내용이 있습니다.'});await guard.waitFor();await guard.getByRole('button',{name:'계속 작성'}).click();
  assert(await dropdown.isVisible());assert.equal(new URL(p.url()).pathname,'/community/posts/create');assert.equal(await p.locator('input[name=title]').inputValue(),'PC 메뉴 이탈 보호');assert(await dropdown.evaluate(e=>e.contains(document.activeElement)));
  await dropdown.getByRole('link',{name:/복지·지원정보/}).click();await guard.getByRole('button',{name:/^나가기/}).click();await p.waitForURL(u=>u.pathname==='/welfare');await dropdown.waitFor({state:'hidden'});
  await button.click();await dropdown.getByRole('link',{name:/기기 정보/}).click();await p.waitForURL(u=>u.pathname==='/devices');await dropdown.waitFor({state:'hidden'});
 })
 await check('mobile current information link closes drawer and restores scroll/focus',async()=>{
  const{p}=await fixture();await ready(p);await p.goto(base+'/welfare');await p.evaluate(()=>scrollTo(0,200));const before=await p.evaluate(()=>({url:location.href,y:scrollY,history:history.length}));
  await menu(p).evaluate(e=>e.click());await panel(p).waitFor();await panel(p).getByRole('link',{name:/복지·지원정보/}).click();await panel(p).waitFor({state:'hidden'});await clean(p);
  assert.deepEqual(await p.evaluate(()=>({url:location.href,y:scrollY,history:history.length})),before);assert(await menu(p).evaluate(e=>e===document.activeElement));
 })
 await check('fresh 1440 header DOM + viewport/full-page after reload, editor, resize and scroll unlock',async()=>{
  const{p}=await fixture({width:1440,height:1000});await ready(p);
  async function capture(name){
   await settle(p);await clean(p);
   const state=await p.evaluate(()=>({headers:document.querySelectorAll('header.iyum-header').length,banners:document.querySelectorAll('[role=banner]').length,header:document.querySelector('header.iyum-header').getBoundingClientRect().toJSON(),position:getComputedStyle(document.querySelector('header.iyum-header')).position,width:innerWidth,height:innerHeight,scrollY,documentHeight:document.documentElement.scrollHeight,footer:document.querySelector('.iyum-footer').getBoundingClientRect().toJSON()}));
   assert.equal(state.headers,1);assert.equal(state.banners,1);assert.equal(state.header.y,0);assert.equal(state.position,'relative');assert(state.footer.y>=state.header.bottom);captureEvidence.push({name,...state});
   await p.screenshot({path:`${out}/header-${name}-viewport.png`,animations:'disabled'});await p.screenshot({path:`${out}/header-${name}-full.png`,fullPage:true,animations:'disabled'});
  }
  await capture('fresh');await p.reload();await ready(p);await capture('reload');
  await edit(p).click();await editor(p).waitFor();await p.keyboard.press('Escape');await editor(p).waitFor({state:'hidden'});await capture('after-edit');
  await p.setViewportSize({width:390,height:700});await p.evaluate(()=>scrollTo(0,200));const y=await p.evaluate(()=>scrollY);await menu(p).evaluate(e=>e.click());await panel(p).waitFor();await p.keyboard.press('Escape');await panel(p).waitFor({state:'hidden'});assert.equal(await p.evaluate(()=>scrollY),y);await p.setViewportSize({width:1440,height:1000});await p.evaluate(()=>scrollTo(0,0));await capture('after-menu-resize');
  await p.setViewportSize({width:390,height:700});await open(p);await p.setViewportSize({width:1440,height:1000});await panel(p).waitFor({state:'hidden'});await p.evaluate(()=>scrollTo(0,0));await capture('resize-open-menu');
 })
 await check('explicit desktop identity / active mypage link and long names at 1024',async()=>{
  const{p}=await fixture({width:1024,member:{...member,nickname:'가'.repeat(20)},profile:{...profile,nickname:'가'.repeat(20),email:'very-long-address-for-overflow-check@example.test'},summary:{postCount:9007199254740991,commentCount:9007199254740991}});await ready(p)
  await p.getByRole('link',{name:'← 마이페이지로 돌아가기',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await p.locator('.mypage-counts strong').first().waitFor();const nav=p.locator('.iyum-desktop-auth');assert.equal(await nav.getByRole('link',{name:'마이페이지',exact:true}).getAttribute('aria-current'),'page');assert.equal(await nav.locator('.iyum-member-name > span').count(),2)
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const a=await nav.boundingBox(),b=await p.locator('.iyum-nav-items').boundingBox();assert(a.x>=b.x+b.width-1)
  assert(await p.locator('.mypage-counts strong').evaluateAll(nodes=>nodes.every(n=>{const range=document.createRange();range.selectNodeContents(n);const box=n.parentElement.getBoundingClientRect();return [...range.getClientRects()].every(r=>r.left>=box.left+8&&r.right<=box.right-8)})));await p.screenshot({path:`${out}/long-1024.png`,fullPage:true});await p.setViewportSize({width:320,height:840});assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await p.screenshot({path:`${out}/long-320.png`,fullPage:true});await open(p);assert(await panel(p).evaluate(e=>e.scrollWidth<=e.clientWidth));await p.screenshot({path:`${out}/menu-long-320.png`,animations:'disabled'})
 })
 await check('modal trap, cancel/backdrop/Escape and save error once with field focus',async()=>{
  const{p,state}=await fixture({width:1440});await ready(p);await edit(p).focus();await p.keyboard.press('Enter');await p.locator('#my-nickname').fill('admin');await p.getByRole('button',{name:'저장',exact:true}).click();assert.equal(await p.getByText('사용할 수 없는 닉네임입니다.',{exact:true}).count(),1)
  for(let i=0;i<8;i++){await p.keyboard.press(i%2?'Shift+Tab':'Tab');await focusWithin(p,editor(p))}
  await p.locator('#my-nickname').fill('중복닉네임');state.put=r=>r.fulfill({status:409,json:error('NICKNAME_ALREADY_EXISTS')});await p.getByRole('button',{name:'저장',exact:true}).click();await p.locator('#nickname-error').waitFor();assert.equal(await editor(p).getByRole('alert').count(),1);assert(await p.locator('#my-nickname').evaluate(e=>e===document.activeElement));assert.equal(await p.locator('#my-nickname').inputValue(),'중복닉네임')
  await p.mouse.click(5,5);await editor(p).waitFor({state:'hidden'});assert(await edit(p).evaluate(e=>e===document.activeElement));assert.equal(await p.locator('.mypage-name h2').innerText(),member.nickname);await clean(p)
 })
 await check('saving blocks close/cancel/Escape/backdrop; success synchronizes profile/header/menu',async()=>{
  let release;const wait=new Promise(r=>release=r);const{p,state}=await fixture({put:async r=>{await wait;state.member.nickname='변경회원';state.profile.nickname='변경회원';await r.fulfill({json:state.profile})}});await ready(p);await edit(p).click();await p.locator('#my-nickname').fill('변경회원');await p.getByRole('button',{name:'저장',exact:true}).dblclick();await p.getByText('닉네임을 저장하고 있습니다.').waitFor();assert(await p.getByRole('button',{name:'취소',exact:true}).isDisabled());await p.keyboard.press('Escape');assert(await p.getByRole('button',{name:'닉네임 수정 닫기'}).isDisabled());await p.getByRole('button',{name:'닉네임 수정 닫기'}).focus();await p.keyboard.press('Enter');await p.mouse.click(2,2);assert(await editor(p).isVisible());release();await p.getByText('닉네임을 변경했습니다.').waitFor();assert.equal(state.calls.filter(c=>c.method==='PUT').length,1);assert.equal(await p.locator('.mypage-name h2').innerText(),'변경회원');assert((await p.locator('.iyum-desktop-auth').innerText()).includes('변경회원'));await open(p);assert((await panel(p).innerText()).includes('변경회원'));await panel(p).getByRole('link',{name:'마이페이지',exact:true}).click();await panel(p).waitFor({state:'hidden'});await clean(p);await p.waitForURL(u=>u.pathname==='/mypage');assert.equal(new URL(p.url()).pathname,'/mypage')
 })
 await check('drawer open/close preserves scrolled body, traps focus, no hidden links; backdrop',async()=>{
  const{p}=await fixture();await ready(p);await p.evaluate(()=>scrollTo(0,300));const y=await p.evaluate(()=>scrollY),before=await p.locator('.mypage-account').boundingBox();await menu(p).evaluate(e=>e.click());await pause(220);const after=await p.locator('.mypage-account').boundingBox();assert(Math.abs(before.y-after.y)<2);assert.equal(await p.evaluate(()=>document.body.style.position),'fixed')
  for(let i=0;i<20;i++){await p.keyboard.press('Tab');await focusWithin(p,panel(p))}for(let i=0;i<12;i++){await p.keyboard.press('Shift+Tab');await focusWithin(p,panel(p))}
  await p.mouse.move(10,400);await p.mouse.wheel(0,600);await pause(100);assert.equal((await p.locator('.mypage-account').boundingBox()).y,after.y);await p.keyboard.press('Escape');await panel(p).waitFor({state:'hidden'});assert.equal(await p.evaluate(()=>scrollY),y);assert(await menu(p).evaluate(e=>e===document.activeElement));await clean(p);assert.equal(await p.locator('.iyum-menu-panel a').count(),0)
  await menu(p).evaluate(e=>e.click());await pause(220);await p.mouse.click(4,350);await panel(p).waitFor({state:'hidden'});assert.equal(await p.evaluate(()=>scrollY),y)
 })
 await check('drawer short height internally scrolls; resize desktop unlocks; reduced motion',async()=>{
  const{p}=await fixture({height:420});await ready(p);await open(p);assert(await panel(p).evaluate(e=>e.scrollHeight>e.clientHeight));await panel(p).getByRole('button',{name:'로그아웃',exact:true}).scrollIntoViewIfNeeded();assert(await panel(p).evaluate(e=>e.scrollTop>0));await p.screenshot({path:`${out}/menu-short-390.png`,animations:'disabled'});await p.setViewportSize({width:1024,height:768});await panel(p).waitFor({state:'hidden'});await clean(p);await p.setViewportSize({width:390,height:844});await p.emulateMedia({reducedMotion:'reduce'});await open(p);assert.equal(await panel(p).evaluate(e=>getComputedStyle(e).animationName),'none');await p.screenshot({path:`${out}/menu-390.png`,animations:'disabled'});await p.setViewportSize({width:320,height:740});await p.screenshot({path:`${out}/menu-320.png`,animations:'disabled'})
 })
 await check('mobile edit narrow/low visual viewport and rotation keeps input accessible',async()=>{
  const{p}=await fixture({width:320,height:360});await ready(p);await edit(p).click();await p.locator('#my-nickname').fill('회전회원');await p.getByRole('button',{name:'저장',exact:true}).scrollIntoViewIfNeeded();let box=await p.getByRole('button',{name:'저장',exact:true}).boundingBox();assert(box.y>=0&&box.y+box.height<=360);await p.screenshot({path:`${out}/edit-short-320.png`,animations:'disabled'});await p.setViewportSize({width:740,height:320});assert.equal(await p.locator('#my-nickname').inputValue(),'회전회원');await p.getByRole('button',{name:'취소',exact:true}).click();await clean(p)
 })
 await check('dirty post: leave cancel keeps drawer + draft; confirm closes both and unlocks',async()=>{
  const{p}=await fixture();await ready(p);await p.goto(base+'/community/posts/create');await p.locator('input[name=title]').fill('보존할 작성 내용');await open(p);await panel(p).getByRole('link',{name:'IYUM 소개',exact:true}).click();const guard=p.getByRole('dialog',{name:'작성 중인 내용이 있습니다.'});await guard.waitFor();assert(await guard.getByRole('button',{name:'계속 작성'}).evaluate(e=>e===document.activeElement));for(let i=0;i<5;i++){await p.keyboard.press('Tab');await focusWithin(p,guard)}await guard.getByRole('button',{name:'계속 작성'}).click();assert(await panel(p).isVisible());assert.equal(await p.locator('input[name=title]').inputValue(),'보존할 작성 내용');await focusWithin(p,panel(p));await panel(p).getByRole('link',{name:'IYUM 소개',exact:true}).click();await guard.getByRole('button',{name:/^나가기/}).click();await p.waitForURL(u=>u.pathname==='/about');await panel(p).waitFor({state:'hidden'});await clean(p)
 })
 await check('browser back while drawer open: cancel keeps state then approve releases lock',async()=>{
  const{p}=await fixture();await ready(p);await open(p);await panel(p).getByRole('link',{name:'커뮤니티',exact:true}).click();await p.waitForURL(u=>u.pathname==='/community');await p.getByRole('link',{name:/글쓰기/}).first().click();await p.waitForURL(u=>u.pathname==='/community/posts/create');await p.locator('input[name=title]').fill('뒤로가기 초안');await open(p);await p.evaluate(()=>history.back());const guard=p.getByRole('dialog',{name:'작성 중인 내용이 있습니다.'});await guard.waitFor();await guard.getByRole('button',{name:'계속 작성'}).click();assert(await panel(p).isVisible());await p.evaluate(()=>history.back());await guard.waitFor();await guard.getByRole('button',{name:/^나가기/}).click();await p.waitForURL(u=>u.pathname==='/community');await clean(p)
 })
 await check('logout success closes drawer and visible notice survives; privacy cleared',async()=>{
  const{p}=await fixture();await ready(p);await open(p);await panel(p).getByRole('button',{name:'로그아웃',exact:true}).click();await p.getByText('로그아웃되었습니다.',{exact:true}).waitFor();await panel(p).waitFor({state:'hidden'});assert(await p.getByText('로그인이 필요해요').isVisible());assert.equal(await p.getByText(member.email,{exact:true}).count(),0);await clean(p);await p.waitForURL(u=>u.pathname==='/mypage/settings');assert.equal(new URL(p.url()).pathname,'/mypage/settings')
 })
 await check('logout failure stays authenticated in drawer; close keeps error visible',async()=>{
  const{p}=await fixture({logout:r=>r.fulfill({status:403,json:error('CSRF_TOKEN_INVALID')})});await ready(p);await open(p);await panel(p).getByRole('button',{name:'로그아웃',exact:true}).click();await panel(p).getByRole('alert').waitFor();assert(await panel(p).getByRole('button',{name:'로그아웃',exact:true}).isVisible());assert.equal(await p.getByText('로그아웃되었습니다.',{exact:true}).count(),0);await panel(p).getByRole('button',{name:'메뉴 닫기',exact:true}).click();await p.locator('.iyum-global-notice [role=alert]').waitFor();assert.equal(await p.locator('.mypage-name h2').innerText(),member.nickname)
 })
 await check('pending logout can close drawer; later success stays visible outside',async()=>{
  let release;const wait=new Promise(r=>release=r);const{p,state}=await fixture({logout:async r=>{await wait;state.member=null;await r.fulfill({status:204})}});await ready(p);await open(p);await panel(p).getByRole('button',{name:'로그아웃',exact:true}).click();await panel(p).getByRole('button',{name:'로그아웃 중…'}).waitFor();await p.keyboard.press('Escape');await clean(p);release();await p.getByText('로그아웃되었습니다.',{exact:true}).waitFor();assert(!await panel(p).isVisible())
 })
 await check('drawer anonymous / checking / auth error and retry',async()=>{
  const{p,state}=await fixture({member:null});await p.getByText('로그인이 필요해요').waitFor();await open(p);assert(await panel(p).getByRole('link',{name:'로그인',exact:true}).isVisible());assert(await panel(p).getByRole('link',{name:'회원가입',exact:true}).isVisible());await p.keyboard.press('Escape');let release;const wait=new Promise(r=>release=r);state.me=async r=>{await wait;await r.fulfill({status:500,json:error('SERVER_ERROR')})};await p.reload();await open(p);assert(await panel(p).getByText('로그인 확인 중…').isVisible());release();await panel(p).getByRole('button',{name:'상태 다시 확인'}).waitFor();state.me=null;state.member={...member,nickname:'전환회원'};state.profile={...profile,nickname:'전환회원'};await panel(p).getByRole('button',{name:'상태 다시 확인'}).click();await panel(p).getByText('전환회원',{exact:true}).waitFor();assert.equal(await panel(p).getByRole('link',{name:'로그인',exact:true}).count(),0)
 })
 for(const width of [1440,390])await check(`no-JS public menu real navigation ${width}`,async()=>{
  const ctx=await browser.newContext({javaScriptEnabled:false,viewport:{width,height:900}}),p=await ctx.newPage();await p.goto(base+'/about');const nav=p.getByRole('navigation',{name:'주 메뉴',exact:true});assert(await nav.getByRole('link',{name:'IYUM 소개'}).isVisible());assert(await p.getByRole('link',{name:'복지·지원정보',exact:true}).isVisible());await p.getByRole('link',{name:'복지·지원정보',exact:true}).click();await p.waitForURL(u=>u.pathname==='/welfare');assert(await p.locator('main h1').isVisible());await p.screenshot({path:`${out}/nojs-menu-${width}.png`,fullPage:true})
 })
}finally{await browser.close();await fs.writeFile(out+'/results.json',JSON.stringify({kind:'mock API / local Next; no real phone keyboard',results,captureEvidence},null,2))}
