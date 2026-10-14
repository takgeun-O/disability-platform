// 읽기 전용 opt-in 검증: 기존 로컬 Spring/Next 서버만 사용. 회원/게시글 생성·수정 없음.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
const base=process.env.BASE_URL??'http://127.0.0.1:8443', api=process.env.API_BASE_URL??'http://127.0.0.1:8082';
const output=process.env.COMMUNITY_ARTIFACT_DIR??await fs.mkdtemp(path.join(os.tmpdir(),'iyum-topics-live-'));
await fs.mkdir(output,{recursive:true});
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright');
const spaces=[['hearing-aid','HEARING_AID'],['cochlear-implant','COCHLEAR_IMPLANT'],['daily','DAILY'],['treatment-rehabilitation','TREATMENT_REHABILITATION'],['communication','COMMUNICATION'],['employment','EMPLOYMENT'],['welfare-life','WELFARE_LIFE']];
const results=[];for(const [slug,topic] of spaces){const response=await fetch(`${api}/api/v1/community/posts?topic=${topic}&type=QUESTION&page=0&size=20`);assert.equal(response.status,200);const data=await response.json();assert(data.items.every(p=>p.topic===topic&&p.type==='QUESTION'));results.push({slug,topic,type:'QUESTION',status:response.status,totalElements:data.totalElements,itemCount:data.items.length});}
const browser=await chromium.launch({channel:'chrome',headless:true});try{const page=await browser.newPage();await page.goto(base+'/community/topics/hearing-aid?type=QUESTION');await page.getByRole('heading',{name:'보청기 커뮤니티',exact:true}).waitFor();assert.equal(await page.locator('.community-post-list > li').count(),results[0].itemCount);assert.equal(await page.getByRole('main').getByRole('alert').count(),0);for(const width of [1440,390,320]){await page.setViewportSize({width,height:900});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));if(width<600){const menu=page.getByRole('button',{name:'메뉴 열기'});await menu.focus();await menu.press('Enter');assert(await page.locator('#iyum-navigation a[href="/community"]').isVisible());await page.keyboard.press('Escape');assert(await menu.evaluate(el=>el===document.activeElement));}}results.push({liveDevServer:base,currentTopicUI:true,matchingRealApi:true,widths:[1440,390,320],mobileMenuKeyboard:true});}finally{await browser.close();}
await fs.writeFile(path.join(output,'real-results.json'),JSON.stringify({mode:'Real existing Spring API and 8443 Next dev, public read only',writes:0,authenticatedFlows:'not run; browser mocks cover these',results},null,2));console.log('PASS 7 real combined-filter GETs + live 8443 UI; no data created');
