// Explicit local test: only a verified seed member; nickname always restored in finally.
import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import {seedDirectory,properties,ids,withSeedResources} from './helpers/seed-ownership.mjs'
const{chromium}=await import(process.env.PLAYWRIGHT_MODULE??'playwright')
if(process.env.IYUM_RUN_MEMBER_LOCAL!=='true')throw Error('Explicit local seed account test opt-in required')
const out=process.env.MEMBER_ARTIFACT_DIR??'/private/tmp/iyum-mypage-actual';await fs.mkdir(out,{recursive:true});const results=[]
await withSeedResources(async resources=>{
 const dir=seedDirectory(),m=properties(await fs.readFile(path.join(dir,'manifest.properties'),'utf8')),accounts=JSON.parse(await fs.readFile(path.join(dir,'accounts.json'),'utf8'))
 assert.equal(m.key,'feed-seed-v1');assert.equal(accounts[0].email,'feed-seed-1@iyum.test');const memberId=ids(m.members)[0]
 await resources.launch(chromium,{channel:'chrome',headless:true});const ctx=await resources.context({viewport:{width:1440,height:1000}});const p=await ctx.newPage();p.setDefaultTimeout(15000)
 const api='http://127.0.0.1:8082/api/v1';let original
 try{
  await p.goto('http://127.0.0.1:8443/login?from=%2Fmypage');await p.locator('main input[name=email]').fill(accounts[0].email);await p.locator('main input[name=password]').fill(accounts[0].password);await p.locator('main').getByRole('button',{name:'로그인',exact:true}).click();await p.waitForURL(u=>u.pathname==='/mypage');await p.getByRole('button',{name:'프로필 수정',exact:true}).waitFor()
  const res=await ctx.request.get(api+'/members/me');assert.equal(res.status(),200);original=await res.json();assert.equal(original.memberId,memberId);assert.equal(original.email,accounts[0].email)
  const summary=await(await ctx.request.get(api+'/members/me/activity-summary')).json();assert.equal(await p.locator('.mypage-counts strong').first().innerText(),summary.postCount.toLocaleString('ko-KR'));results.push({name:'real session/profile/summary/CORS',pass:true,memberId})
  const nick='검증이움'+Date.now().toString().slice(-8);await p.getByRole('button',{name:'프로필 수정',exact:true}).click();await p.locator('#my-nickname').fill(nick);const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/members/me/nickname'&&r.request().method()==='PUT');await p.getByRole('button',{name:'저장',exact:true}).click();assert.equal((await response).status(),200);await p.getByText('닉네임을 변경했습니다.').waitFor();assert.equal(await p.locator('.mypage-name h2').innerText(),nick);assert((await p.locator('.iyum-member-name').innerText()).includes(nick));const me=await(await ctx.request.get(api+'/auth/me')).json();assert.equal(me.nickname,nick);results.push({name:'real CSRF PUT200 and profile/header/latest auth me agree',pass:true})
  await p.reload();await p.getByRole('button',{name:'프로필 수정',exact:true}).waitFor();assert.equal(await p.locator('.mypage-name h2').innerText(),nick);results.push({name:'real refresh retains persisted nickname',pass:true})
 }catch(e){results.push({name:'actual verification',pass:false,error:e.name+': '+e.message});process.exitCode=1}
 finally{
  if(original){try{const csrf=await(await ctx.request.get(api+'/auth/csrf')).json();const restored=await ctx.request.put(api+'/members/me/nickname',{headers:{[csrf.headerName]:csrf.token},data:{nickname:original.nickname}});assert.equal(restored.status(),200);assert.equal((await restored.json()).nickname,original.nickname);results.push({name:'original seed nickname restored',pass:true,memberId})}catch(e){results.push({name:'restore failed',pass:false,error:e.name});process.exitCode=1}}
  await resources.finish(()=>fs.writeFile(out+'/results.json',JSON.stringify({kind:'actual Spring local verified seed account',results},null,2)));console.log(JSON.stringify(results))
 }
})
