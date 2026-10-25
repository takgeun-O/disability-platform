import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE);
const out='/private/tmp/iyum-account-card-visual';await fs.mkdir(out,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const results=[];
try {
 for (const width of [1440,390,320]) {
  const ctx=await browser.newContext({viewport:{width,height:1000},hasTouch:width<600});const p=await ctx.newPage();
  const member={memberId:7,nickname:'검토회원',email:'review@example.test',role:'USER',createdAt:'2026-10-01T00:00:00Z'};
  await ctx.route(u=>u.pathname.startsWith('/api/v1/'),r=>{
   const path=new URL(r.request().url()).pathname;
   assert.equal(r.request().method(),'GET','No mutation required for UI review');
   if(path.endsWith('/auth/me')||path.endsWith('/members/me'))return r.fulfill({json:member});
   if(path.endsWith('/activity-summary'))return r.fulfill({json:{postCount:0,commentCount:0}});
   if(path.endsWith('/posts')||path.endsWith('/comments'))return r.fulfill({json:{items:[],hasNext:false,nextCursor:null}});
   throw Error('Unexpected request '+path);
  });
  await p.goto('http://127.0.0.1:8459/mypage');await p.getByText('review@example.test',{exact:true}).waitFor();await p.getByText('아직 작성한 게시글이 없어요.',{exact:true}).waitFor();
  const account=p.locator('.mypage-account'),card=p.locator('.mypage-account-card'),password=p.getByRole('button',{name:'비밀번호 변경',exact:true}),withdraw=p.locator('#withdrawal-entry');
  assert.equal(await card.locator('dl').count(),1);assert.equal(await card.locator('#password-change-entry').count(),1);assert.equal(await card.locator('#withdrawal-entry').count(),0);
  const bounds=await card.boundingBox(),pb=await password.boundingBox(),wb=await withdraw.boundingBox();assert(pb.height>=44&&wb.height>=44);assert(wb.y>=bounds.y+bounds.height);assert(Math.abs(wb.x+wb.width/2-(bounds.x+bounds.width/2))<2);
  assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await p.screenshot({path:`${out}/page-${width}.png`,fullPage:true});await account.screenshot({path:`${out}/account-${width}.png`});
  await password.focus();await p.keyboard.press('Tab');assert(await withdraw.evaluate(e=>e===document.activeElement));await p.keyboard.press('Shift+Tab');assert(await password.evaluate(e=>e===document.activeElement));assert(await password.evaluate(e=>getComputedStyle(e).outlineStyle!=='none'));
  await account.screenshot({path:`${out}/keyboard-${width}.png`});await p.keyboard.press('Enter');await p.getByRole('dialog',{name:'비밀번호 변경',exact:true}).waitFor();await p.keyboard.press('Escape');assert(await password.evaluate(e=>e===document.activeElement));
  await p.keyboard.press('Tab');await p.keyboard.press('Enter');await p.getByRole('dialog',{name:'회원 탈퇴',exact:true}).waitFor();await p.keyboard.press('Escape');assert(await withdraw.evaluate(e=>e===document.activeElement));
  if(width===320){member.email='very-long-review-account-address-for-wrapping@example.test';member.nickname='긴닉네임'.repeat(5);await p.reload();await p.getByText(member.email,{exact:true}).waitFor();assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await account.screenshot({path:`${out}/long-email-320.png`});}
  results.push({width,pass:true,cardContainsEmailAndPassword:true,withdrawalSeparateCentered:true,passwordHeight:pb.height,withdrawalHeight:wb.height,keyboardOpenCloseFocusRestored:true});await ctx.close();
 }
} finally {await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify({mode:'production Next / mock GET only / no real account mutation',results},null,2));}
