import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
const base=process.env.BASE_URL??'http://127.0.0.1:8457'
const out=process.env.WITHDRAWAL_ARTIFACT_DIR??'/private/tmp/iyum-withdrawal-browser'
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'chrome',headless:true}),results=[]
async function fixture(width=1440) {
 const context=await browser.newContext({viewport:{width,height:900},hasTouch:width<600}),page=await context.newPage()
 page.setDefaultTimeout(8000)
 const state={member:{memberId:7,email:'review@example.test',nickname:'검토회원',role:'USER'},calls:[],withdraw:null}
 await context.route(u=>u.pathname.startsWith('/api/v1/'),async route=>{
  const req=route.request(),p=new URL(req.url()).pathname;state.calls.push({path:p,method:req.method()})
  if(p.endsWith('/auth/me'))return route.fulfill(state.member?{json:state.member}:{status:401,json:{code:'AUTHENTICATION_REQUIRED',message:'로그인이 필요합니다.',fieldErrors:[]}})
  if(p.endsWith('/csrf'))return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'test-only'}})
  if(p.endsWith('/withdrawal')) {assert.equal(req.headers()['x-csrf-token'],'test-only');assert.deepEqual(req.postDataJSON(),{currentPassword:' raw password ',confirmed:true});if(state.withdraw)return state.withdraw(route);state.member=null;return route.fulfill({status:204})}
  if(p.endsWith('/activity-summary'))return route.fulfill({json:{postCount:3,commentCount:4}})
  if(p.endsWith('/members/me'))return route.fulfill({json:{...state.member,createdAt:'2026-10-01T00:00:00Z'}})
  if(p.endsWith('/posts')||p.endsWith('/comments'))return route.fulfill({json:{items:[],hasNext:false,nextCursor:null}})
  return route.fulfill({status:401,json:{code:'AUTHENTICATION_REQUIRED',message:'로그인이 필요합니다.',fieldErrors:[]}})
 })
 await page.goto(base+'/mypage');await page.locator('#withdrawal-entry').click()
 return {context,page,state,dialog:page.getByRole('dialog')}
}
async function ready(f){await f.page.locator('#withdrawal-password').fill(' raw password ');await f.dialog.getByRole('checkbox').check()}
async function check(name,fn){try{await fn();results.push({name,passed:true})}catch(e){results.push({name,passed:false,error:e.message});console.error(name,e.message)}}
try {
 for(const width of [1440,390,320])await check(`layout keyboard close ${width}`,async()=>{
  const f=await fixture(width);try {
   const {page:p,dialog:d}=f
   assert.equal(await p.locator('#withdrawal-close').evaluate(el=>document.activeElement===el),true)
   assert.equal(await d.getByRole('button',{name:'회원 탈퇴',exact:true}).isDisabled(),true)
   assert.equal(await p.locator('#withdrawal-password').getAttribute('type'),'password')
   await p.locator('#withdrawal-password').fill('가'.repeat(25));await d.getByRole('checkbox').check()
   assert.equal(await d.getByRole('button',{name:'회원 탈퇴',exact:true}).isDisabled(),true)
   await ready(f)
   const toggle=d.getByRole('button',{name:'비밀번호 표시',exact:true})
   await toggle.click();assert.equal(await p.locator('#withdrawal-password').getAttribute('type'),'text')
   await d.getByRole('button',{name:'비밀번호 숨기기',exact:true}).click()
   assert.equal(await p.locator('#withdrawal-password').inputValue(),' raw password ')
   assert.equal(f.state.calls.filter(c=>c.path.endsWith('/withdrawal')).length,0)
   if(width<600)await p.touchscreen.tap(2,2);else await p.mouse.click(2,2)
   assert(await d.isVisible());assert.equal(await p.locator('#withdrawal-password').inputValue(),' raw password ');assert(await d.getByRole('checkbox').isChecked())
   await p.screenshot({path:`${out}/dialog-${width}.png`})
   assert(await d.evaluate(el=>el.scrollWidth<=el.clientWidth))
   assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
   await p.keyboard.press('Escape');assert.equal(await p.locator('#withdrawal-entry').evaluate(el=>document.activeElement===el),true)
   await p.screenshot({path:`${out}/page-${width}.png`,fullPage:true});await p.locator('#withdrawal-entry').click();assert.equal(await p.locator('#withdrawal-password').inputValue(),'')
   for(const name of ['취소','회원 탈퇴 닫기']) {
    await ready(f);await d.getByRole('button',{name,exact:true}).click()
    assert.equal(await p.locator('#withdrawal-entry').evaluate(el=>document.activeElement===el),true)
    await p.locator('#withdrawal-entry').click();assert.equal(await p.locator('#withdrawal-password').inputValue(),'');assert.equal(await d.getByRole('checkbox').isChecked(),false)
   }
   await p.setViewportSize({width,height:420});await p.locator('#withdrawal-password').scrollIntoViewIfNeeded();await d.getByRole('button',{name:'회원 탈퇴',exact:true}).scrollIntoViewIfNeeded();assert(await d.evaluate(el=>el.scrollHeight>=el.clientHeight));await p.screenshot({path:`${out}/short-${width}.png`})
  }finally{await f.context.close()}
 })
 await check('nickname dialog retains default backdrop close',async()=>{
  const f=await fixture();try {
   await f.page.keyboard.press('Escape');await f.page.getByRole('button',{name:'프로필 수정',exact:true}).click()
   assert(await f.page.getByRole('dialog').isVisible());await f.page.mouse.click(2,2)
   assert.equal(await f.page.getByRole('dialog').count(),0)
   assert(await f.page.getByRole('button',{name:'프로필 수정',exact:true}).evaluate(el=>el===document.activeElement))
  }finally{await f.context.close()}
 })
 await check('mismatch once then successful 204; no duplicate; close blocked; home notice; back private state gone',async()=>{
  const f=await fixture();try{
   await ready(f);f.state.withdraw=r=>r.fulfill({status:400,json:{code:'PASSWORD_MISMATCH',fieldErrors:[{field:'currentPassword',message:'현재 비밀번호가 일치하지 않습니다.'}]}})
   await f.dialog.getByRole('button',{name:'회원 탈퇴',exact:true}).click();await f.page.locator('#withdrawal-password-error').waitFor()
   assert.equal(await f.page.getByText('현재 비밀번호가 일치하지 않습니다.',{exact:true}).count(),1);await f.page.screenshot({path:`${out}/password-error.png`})
   assert.equal(await f.page.locator('#withdrawal-password').evaluate(el=>document.activeElement===el),true)
   let release,arrived;const arrival=new Promise(r=>arrived=r)
   f.state.withdraw=async r=>{arrived();await new Promise(resolve=>release=resolve);f.state.member=null;await r.fulfill({status:204})}
   await f.dialog.getByRole('button',{name:'회원 탈퇴',exact:true}).click();await arrival
   await f.page.keyboard.press('Enter');await f.page.keyboard.press('Escape');await f.page.locator('#withdrawal-close').focus();await f.page.keyboard.press('Enter')
   await f.page.mouse.click(2,2);assert(await f.dialog.getByRole('button',{name:'취소',exact:true}).isDisabled());assert(await f.dialog.getByRole('button',{name:'비밀번호 표시',exact:true}).isDisabled());assert(await f.dialog.isVisible());assert.equal(f.state.calls.filter(c=>c.path.endsWith('/withdrawal')).length,2)
   release();await f.page.waitForURL(u=>u.pathname==='/');await f.page.getByText('회원 탈퇴가 완료되었습니다.',{exact:true}).waitFor()
   await f.page.screenshot({path:`${out}/completed-1440.png`})
   await f.page.goto(base+'/mypage');await f.page.getByRole('heading',{name:'로그인이 필요해요'}).waitFor();assert.equal(await f.page.locator('#withdrawal-entry').count(),0)
  }finally{await f.context.close()}
 })
 for(const [status,code] of [[403,'CSRF_TOKEN_INVALID'],[403,'MEMBER_NOT_ACTIVE'],[500,'INTERNAL_SERVER_ERROR'],[401,'AUTHENTICATION_REQUIRED']]) await check(`error ${code}`,async()=>{
  const f=await fixture();try{
   f.state.withdraw=r=>r.fulfill({status,json:{code,fieldErrors:[]}});await ready(f);await f.dialog.getByRole('button',{name:'회원 탈퇴',exact:true}).click()
   if(status===401){await f.page.getByRole('heading',{name:'로그인이 필요해요'}).waitFor()}
   else {await f.dialog.getByRole('alert').waitFor();assert.equal(await f.page.locator('#withdrawal-password').inputValue(),' raw password ');assert(await f.dialog.isVisible());assert.equal(await f.page.getByText('회원 탈퇴가 완료되었습니다.',{exact:true}).count(),0)}
   assert.equal(f.state.calls.filter(c=>c.path.endsWith('/withdrawal')).length,1)
  }finally{await f.context.close()}
 })
 await check('network failure keeps session; explicit retry renews CSRF and succeeds on mobile',async()=>{
  const f=await fixture(390);try{
   f.state.withdraw=r=>r.abort('failed');await ready(f);await f.dialog.getByRole('button',{name:'회원 탈퇴',exact:true}).click()
   await f.dialog.getByRole('alert').waitFor();assert(await f.dialog.isVisible());assert.equal(f.state.calls.filter(c=>c.path.endsWith('/withdrawal')).length,1)
   f.state.withdraw=null;await f.dialog.getByRole('button',{name:'회원 탈퇴',exact:true}).click()
   await f.page.waitForURL(u=>u.pathname==='/');await f.page.getByText('회원 탈퇴가 완료되었습니다.',{exact:true}).waitFor()
   assert.equal(f.state.calls.filter(c=>c.path.endsWith('/withdrawal')).length,2)
   assert.equal(f.state.calls.filter(c=>c.path.endsWith('/csrf')).length,2)
   await f.page.screenshot({path:`${out}/completed-390.png`})
  }finally{await f.context.close()}
 })
 await check('confirmed error stays next to checkbox and keyboard stays in dialog',async()=>{
  const f=await fixture();try{
   f.state.withdraw=r=>r.fulfill({status:400,json:{code:'VALIDATION_FAILED',fieldErrors:[{field:'confirmed',message:'untrusted'}]}})
   await ready(f);await f.dialog.getByRole('button',{name:'회원 탈퇴',exact:true}).click();await f.page.locator('#withdrawal-consent-error').waitFor()
   assert.equal(await f.page.getByText('탈퇴 안내를 확인하고 동의해 주세요.',{exact:true}).count(),1)
   for(let i=0;i<10;i++){await f.page.keyboard.press('Tab');assert(await f.dialog.evaluate(el=>el.contains(document.activeElement)))}
  }finally{await f.context.close()}
 })

}finally{await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2))}
console.log(JSON.stringify(results,null,2));if(results.some(r=>!r.passed))process.exitCode=1
