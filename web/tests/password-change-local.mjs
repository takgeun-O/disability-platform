// Opt-in local seed account only. Secrets stay in memory and are never written to artifacts.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { withSeedResources, seedDirectory, properties, ids } from './helpers/seed-ownership.mjs';
if (process.env.IYUM_RUN_PASSWORD_CHANGE_LOCAL !== 'true') throw Error('Explicit local seed account opt-in required');
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright');
const out = process.env.PASSWORD_CHANGE_LOCAL_DIR ?? '/private/tmp/iyum-password-change-local';
await fs.mkdir(out, { recursive: true });
const api = 'http://127.0.0.1:8082/api/v1', results = [];
await withSeedResources(async resources => {
  const dir = seedDirectory(), manifest = properties(await fs.readFile(path.join(dir, 'manifest.properties'), 'utf8'));
  assert.equal(manifest.key, 'feed-seed-v1');
  const accounts = JSON.parse(await fs.readFile(path.join(dir, 'accounts.json'), 'utf8'));
  const index = 1, account = accounts[index], memberId = ids(manifest.members)[index];
  assert.equal(account.email, 'feed-seed-2@iyum.test'); assert(Number.isSafeInteger(memberId));
  await resources.launch(chromium, { channel: 'chrome', headless: true });
  const a = await resources.context({ viewport: { width: 1440, height: 900 } }), b = await resources.context({});
  const csrf = async ctx => { const r = await ctx.request.get(api + '/auth/csrf'); assert.equal(r.status(), 200); return r.json(); };
  async function login(ctx, password) { const c = await csrf(ctx); return ctx.request.post(api + '/auth/login', { headers: { [c.headerName]: c.token }, data: { email: account.email, password } }); }
  const newPassword = randomUUID() + 'Aa1!'; let changed = false, stage = 'login'; const responses = [];
  try {
    for (const ctx of [a,b]) {
      assert.equal((await login(ctx, account.password)).status(), 200);
      const r = await ctx.request.get(api + '/members/me'); assert.equal(r.status(), 200); const profile = await r.json();
      assert.equal(profile.memberId, memberId); assert.equal(profile.email, account.email);
    }
    results.push({ name: 'two separate contexts authenticated as manifest-owned seed account', pass: true });
    const oldCsrf = await csrf(a);
    const preflight = await a.request.fetch(api+'/members/me/password',{method:'OPTIONS',headers:{Origin:'http://127.0.0.1:8443','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'content-type,x-csrf-token'}});
    const corsReady = preflight.headers()['access-control-allow-origin'] === 'http://127.0.0.1:8443';
    if (!corsReady) {
      results.push({name:'real browser CORS unavailable; POST was not sent by UI',pass:null,preflightStatus:preflight.status()});
      stage='direct API request (browser CORS blocked)';
      const r=await a.request.post(api+'/members/me/password',{headers:{[oldCsrf.headerName]:oldCsrf.token},data:{currentPassword:account.password,newPassword,newPasswordConfirm:newPassword}});
      responses.push({path:'/api/v1/members/me/password',status:r.status()});
      assert.equal(r.status(),200); changed=true; assert.deepEqual(await r.json(),{reloginRequired:false});
      results.push({name:'direct API POST200 only; browser CORS remains unverified',pass:true});
    } else {
      stage = 'browser preparation'; const p=await a.newPage(); p.setDefaultTimeout(15000);
      await p.goto('http://127.0.0.1:8443/mypage'); await p.locator('#password-change-entry').click();
      await p.locator('#change-currentPassword').fill(account.password); await p.locator('#change-newPassword').fill(newPassword); await p.locator('#change-newPasswordConfirm').fill(newPassword);
      stage='password POST'; const response=p.waitForResponse(r=>new URL(r.url()).pathname==='/api/v1/members/me/password'&&r.request().method()==='POST');
      await p.getByRole('dialog',{name:'비밀번호 변경',exact:true}).getByRole('button',{name:'비밀번호 변경',exact:true}).click();
      const r=await response; assert.equal(r.status(),200); changed=true; assert.deepEqual(await r.json(),{reloginRequired:false});
      await p.getByRole('dialog',{name:'비밀번호 변경',exact:true}).getByRole('button',{name:'닫기',exact:true}).click();
      results.push({name:'real browser POST200/CORS, retained-session notice and fresh CSRF',pass:true});
    }
    assert.equal((await a.request.get(api+'/members/me')).status(),200); assert.equal((await b.request.get(api+'/members/me')).status(),401);
    results.push({name:'A protected GET200; B next protected GET401',pass:true});
    const stale = await a.request.post(api+'/members/me/password',{headers:{[oldCsrf.headerName]:oldCsrf.token},data:{}});
    assert.equal(stale.status(),403);assert.equal((await stale.json()).code,'CSRF_TOKEN_INVALID');
    const fresh=await csrf(a), valid=await a.request.post(api+'/members/me/password',{headers:{[fresh.headerName]:fresh.token},data:{}});
    assert.equal(valid.status(),400);assert.equal((await valid.json()).code,'VALIDATION_FAILED');
    results.push({name:'old CSRF403; fresh CSRF passes to DTO validation400 without mutation',pass:true});
    assert.equal((await login(b,account.password)).status(),401);assert.equal((await login(b,newPassword)).status(),200);
    results.push({name:'old password login401; new password login200',pass:true});
  } catch(e) { results.push({name:'actual Spring verification',pass:false,errorType:e.name,stage,responses});process.exitCode=1; }
  finally {
    if(changed) {
      try {
        const c=await csrf(a),r=await a.request.post(api+'/members/me/password',{headers:{[c.headerName]:c.token},data:{currentPassword:newPassword,newPassword:account.password,newPasswordConfirm:account.password}});
        assert.equal(r.status(),200);assert.deepEqual(await r.json(),{reloginRequired:false});
        assert.equal((await login(b,account.password)).status(),200);
        results.push({name:'original seed password restored and login verified; credential version advanced twice',pass:true});
      }catch(e){results.push({name:'RESTORATION NEEDS ATTENTION',pass:false,errorType:e.name});process.exitCode=1;}
    }
    await resources.finish(()=>fs.writeFile(path.join(out,'results.json'),JSON.stringify({mode:'actual Spring, owned local seed account only',results},null,2)));
    console.log(JSON.stringify(results));
  }
});
