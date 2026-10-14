// Prototype regressions only. Actual Next integration is tests/info-pages-browser.mjs.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const base=process.env.PREVIEW_URL ?? 'http://127.0.0.1:8450';
const browser=await chromium.launch({channel:'chrome',headless:true});
const page=await browser.newPage({viewport:{width:390,height:844},reducedMotion:'reduce'});
const results=[];
try {
 for (const view of ['about','welfare','guide']) {
  await page.goto(`${base}/?review-fix=${view}#${view}`);
  if(view==='guide') { await page.locator('[data-answer=assessment]').click(); await page.locator('[data-note]').first().check(); }
  const before=await page.locator('main').innerHTML(); const url=page.url();
  await page.locator('.skip').focus(); await page.keyboard.press('Enter');
  assert.equal(page.url(),url);assert.equal(await page.locator('main').innerHTML(),before);
  assert.equal(await page.evaluate(()=>document.activeElement.id),'main');
  results.push(`PASS ${view} keyboard skip: route, answers, checks and result preserved`);
 }
 await page.goto(`${base}/?review-fix=filters#expanded`);
 const panel=page.locator('.filter-disclosure');
 assert.equal(await panel.getAttribute('open'),null);
 await page.setViewportSize({width:1440,height:1000});
 assert(await panel.locator('summary').isVisible());await panel.locator('summary').click();
 await page.getByLabel('콘텐츠 유형').selectOption('notice');await page.getByRole('button',{name:'적용',exact:true}).click();
 await page.getByLabel('지역',{exact:true}).selectOption('A');
 const before=await page.locator('#list-results').innerText();
 await panel.locator('summary').click();await page.setViewportSize({width:390,height:844});
 assert(await panel.locator('summary').isVisible());await panel.locator('summary').click();
 assert.equal(await page.getByLabel('지역',{exact:true}).inputValue(),'A');assert.equal(await page.getByLabel('콘텐츠 유형').inputValue(),'notice');assert.equal(await page.locator('#list-results').innerText(),before);
 await page.setViewportSize({width:1440,height:1000});assert(await page.getByLabel('지역',{exact:true}).isVisible());
 results.push('PASS mobile → desktop → mobile → desktop: closed filter access, pending and applied conditions preserved');
 console.log(results.join('\n'));
} finally { await browser.close(); if(process.env.RESULT_FILE) await fs.writeFile(process.env.RESULT_FILE,JSON.stringify({scope:'prototype only',results},null,2)); }
