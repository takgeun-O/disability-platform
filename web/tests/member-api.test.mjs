import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const api=await loadTypeScript('../src/lib/member-api.ts',import.meta.url)
const {createAuthSession}=await loadTypeScript('../src/lib/auth-session.ts',import.meta.url)
process.env.NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8082'
const profile={memberId:1,nickname:'기존회원',email:'member@example.test',createdAt:'2026-09-30T16:00:00Z'}
const csrf={headerName:'X-CSRF-TOKEN',token:'test-only'}
const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}})
test('private reads use session/no-store and no CSRF; 0 is valid',async t=>{
 const calls=[];t.mock.method(globalThis,'fetch',async(u,o)=>{calls.push({u,o});return json(u.endsWith('activity-summary')?{postCount:0,commentCount:3}:profile)})
 assert.deepEqual(await api.getProfile(1),profile);assert.deepEqual(await api.getActivitySummary(),{postCount:0,commentCount:3})
 assert.equal(calls.length,2);for(const {u,o}of calls){assert(!u.includes('csrf'));assert.equal(o.credentials,'include');assert.equal(o.cache,'no-store')}
})
test('summary rejects missing/string/null/negative/fractional/unsafe counts',()=>{
 for(const value of [undefined,null,'0',-1,1.2,Number.MAX_SAFE_INTEGER+1]){assert(!api.isSummary({postCount:value,commentCount:0}));assert(!api.isSummary({postCount:0,commentCount:value}))}
})
test('profile response must match current member and valid instant',async t=>{
 t.mock.method(globalThis,'fetch',async()=>json(profile));await assert.rejects(api.getProfile(2),e=>e.code==='UNEXPECTED_RESPONSE')
 assert(!api.isProfile({...profile,createdAt:'unknown'}));assert.equal(api.joinedMonth(profile.createdAt),'2026년 10월')
})
test('nickname policy strips Java whitespace, rejects reserved and NBSP',()=>{
 assert.equal(api.normalizeNickname('　새이름　'),'새이름');assert.equal(api.nicknameError('　새이름　'),'')
 for(const value of ['',' ','a','관리자','AdMiN','가'.repeat(21),'한 글','😀이름','\u00a0새이름\u00a0'])assert(api.nicknameError(value))
})
test('nickname PUT uses issued CSRF, raw nickname and 200 profile; never retries',async t=>{
 const calls=[];t.mock.method(globalThis,'fetch',async(u,o)=>{calls.push({u,o});return json({...profile,nickname:'새이름'})})
 assert.equal((await api.updateNickname(1,' 새이름 ',csrf)).nickname,'새이름');assert.equal(calls.length,1);assert.equal(calls[0].o.method,'PUT');assert.deepEqual(JSON.parse(calls[0].o.body),{nickname:' 새이름 '});assert.equal(calls[0].o.headers[csrf.headerName],csrf.token)
})
test('duplicate and validation are field errors; 401/CSRF/permission differ',async t=>{
 let code='NICKNAME_ALREADY_EXISTS',status=409
 t.mock.method(globalThis,'fetch',async()=>json({code,message:'private',fieldErrors:[{field:'nickname',message:'private'}]},status))
 await assert.rejects(api.updateNickname(1,'새이름',csrf),e=>!!e.field&&!e.message)
 for(const pair of [[400,'VALIDATION_FAILED'],[400,'INVALID_NICKNAME'],[401,'AUTHENTICATION_REQUIRED'],[403,'CSRF_TOKEN_INVALID'],[403,'MEMBER_NOT_ACTIVE']]){[status,code]=pair;await assert.rejects(api.updateNickname(1,'새이름',csrf),e=>e.status===status&&e.code===code&&(status===400?!!e.field:!!e.message))}
})
test('204/HTML/broken JSON/network are not nickname success',async t=>{
 let response=new Response(null,{status:204});let n=0
 t.mock.method(globalThis,'fetch',async()=>{n++;if(!response)throw Error('offline');return response})
 for(const r of [response,new Response('<html>down</html>',{status:200}),new Response('{',{status:200,headers:{'Content-Type':'application/json'}}),null]){response=r;await assert.rejects(api.updateNickname(1,'새이름',csrf))}assert.equal(n,4)
})
test('auth store updates only current authenticated member; ignores logout and wrong account',async()=>{
 const m={...profile,role:'USER'};const s=createAuthSession({getCurrentMember:async()=>m,getAuthCsrf:async()=>csrf,logout:async()=>{},login:async()=>m});s.start();await s.refresh()
 assert.equal(s.updateNickname(2,'wrong'),false);assert.equal(s.updateNickname(1,'새이름'),true);assert.equal(s.getSnapshot().member.nickname,'새이름');await s.signOut();assert.equal(s.updateNickname(1,'late'),false);assert.equal(s.getSnapshot().member,null);s.stop()
})

test('private API expiry is member-scoped and prevents late nickname restoration',async()=>{
 const m={...profile,role:'USER'};const s=createAuthSession({getCurrentMember:async()=>m,getAuthCsrf:async()=>csrf,logout:async()=>{},login:async()=>m});s.start();await s.refresh();s.expire(2);assert.equal(s.getSnapshot().status,'authenticated');s.expire(1);assert.equal(s.getSnapshot().status,'anonymous');assert.equal(s.updateNickname(1,'late'),false);s.stop()
})
