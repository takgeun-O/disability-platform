// 기존 로컬 API를 읽기만 한다. 전체 게시글 복귀 링크·키보드·반응형 회귀.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright');
const base=process.env.BASE_URL??'http://127.0.0.1:8443';
const output=process.env.COMMUNITY_ARTIFACT_DIR??await fs.mkdtemp(path.join(os.tmpdir(),'iyum-all-return-'));
await fs.mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});const checks=[];
try {
 const page=await browser.newPage();
 for(const width of [1440,390,320]) {
  await page.setViewportSize({width,height:900});
  await page.goto(base+'/community/topics/hearing-aid?type=QUESTION&page=1');
  const links=page.getByRole('navigation',{name:'주제별 커뮤니티'});
  const all=links.getByRole('link',{name:'전체 게시글',exact:true});
  assert.equal(await all.getAttribute('href'),'/community');
  assert(await page.getByRole('navigation',{name:'현재 위치'}).getByRole('link',{name:'전체 게시글',exact:true}).isVisible());
  await page.screenshot({path:path.join(output,`all-return-topic-${width}.png`),fullPage:true});
  if(width===320){await all.focus();await all.press('Enter');}else await all.click();
  await page.waitForURL(base+'/community');await page.getByRole('heading',{name:'전체 게시글',exact:true}).waitFor();
  assert.equal(new URL(page.url()).search,'');
  assert.equal(await page.getByRole('navigation',{name:'주제별 커뮤니티'}).getByRole('link').count(),8);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  if(width===1440){assert.equal(await page.getByLabel('주제',{exact:true}).inputValue(),'');assert.equal(await page.getByLabel('글 유형',{exact:true}).inputValue(),'');}
  await page.screenshot({path:path.join(output,`all-return-entrance-${width}.png`),fullPage:true});
  await page.goBack();await page.getByRole('heading',{name:'보청기 커뮤니티',exact:true}).waitFor();assert.equal(new URL(page.url()).search,'?type=QUESTION&page=1');
  checks.push(`${width}px: 전체 게시글 링크·필터 초기화·뒤로 가기·가로 넘침 없음${width===320?'·키보드 Enter':''}`);
 }
 await fs.writeFile(path.join(output,'all-return-results.json'),JSON.stringify({mode:'existing Next dev + real public API reads, no writes',checks},null,2));console.log('PASS',checks);
}finally{await browser.close();}
