import test from 'node:test'
import assert from 'node:assert/strict'
import { loadTypeScript } from './helpers/load-typescript.mjs'
const api = await loadTypeScript('../src/lib/member-api.ts', import.meta.url)
const { validateCurrentPassword } = await loadTypeScript('../src/lib/login-form.ts', import.meta.url)
const { createAuthSession } = await loadTypeScript('../src/lib/auth-session.ts', import.meta.url)
const { createMemberActivityCache } = await loadTypeScript('../src/lib/member-activity.ts', import.meta.url)
process.env.NEXT_PUBLIC_API_BASE_URL = 'http://api.example.test'
const csrf = { headerName: 'X-Test-CSRF', token: 'dummy' }
test('withdrawal password: raw whitespace and UTF-8 byte boundary, no signup rules', () => {
  for (const p of ['', ' \n\t', 'a'.repeat(73), '가'.repeat(25)]) assert(validateCurrentPassword(p))
  for (const p of [' x ', 'x', 'a'.repeat(72), '가'.repeat(24)]) assert.equal(validateCurrentPassword(p), '')
})
test('204 is accepted without JSON; exact payload, credentials, dynamic CSRF header', async t => {
  const calls=[]
  t.mock.method(globalThis,'fetch',async (url, options) => {calls.push({url,options}); return new Response(null,{status:204})})
  await api.withdrawMember(' raw password ',csrf)
  assert.equal(calls.length,1)
  const {url,options:o}=calls[0]
  assert.equal(url,'http://api.example.test/api/v1/members/me/withdrawal')
  assert.equal(o.credentials,'include'); assert.equal(o.cache,'no-store');assert.equal(o.method,'POST')
  assert.equal(o.headers['X-Test-CSRF'],'dummy')
  assert.deepEqual(JSON.parse(o.body),{currentPassword:' raw password ',confirmed:true})
})
test('field errors are not repeated globally; other errors never masquerade as success or retry', async t => {
  let response;let calls=0
  t.mock.method(globalThis,'fetch',async()=>{calls++;if(!response)throw Error('offline');return response})
  for (const [status,code,field] of [[400,'PASSWORD_MISMATCH','currentPassword'],[400,'INVALID_PASSWORD','currentPassword'],[400,'VALIDATION_FAILED','confirmed'],[401,'AUTHENTICATION_REQUIRED'],[403,'CSRF_TOKEN_INVALID'],[403,'MEMBER_NOT_ACTIVE'],[500,'INTERNAL_SERVER_ERROR']]) {
    response=new Response(JSON.stringify({code,fieldErrors:field?[{field,message:'untrusted'}]:[]}),{status,headers:{'content-type':'application/json'}})
    await assert.rejects(api.withdrawMember('raw',csrf),e=>e.code===code&&(field?!!e.fields[field]&&!e.message:!!e.message))
  }
  for (const r of [null,new Response('ok',{status:200}),new Response('{',{status:400,headers:{'content-type':'application/json'}})]) {response=r; await assert.rejects(api.withdrawMember('raw',csrf))}
  assert.equal(calls,10)
})
test('confirmed session clear purges private cache owner, ignores late profile updates', async()=>{
 const member={memberId:7,nickname:'fixture',email:'fixture@example.test',role:'USER'}
 const session=createAuthSession({getCurrentMember:async()=>member,getAuthCsrf:async()=>csrf,logout:async()=>{},login:async()=>member})
 const cache=createMemberActivityCache();session.subscribe(()=>{const s=session.getSnapshot();cache.setOwner(s.status==='authenticated'?s.member.memberId:null)})
 session.start();await session.refresh()
 const lease=cache.lease(7);lease.write('review',{items:[],hasNext:false,nextCursor:null,segments:3,version:0,tab:'comments'});assert(lease.read('review'))
 assert.equal(session.expire(8),false);assert.equal(session.getSnapshot().status,'authenticated')
 assert.equal(session.expire(7),true);assert.equal(session.getSnapshot().member,null)
 assert.equal(lease.valid(),false);assert.equal(lease.read('review'),undefined);lease.write('review',{items:[]});cache.setOwner(8);assert.equal(cache.lease(8).read('review'),undefined)
 assert.equal(session.updateNickname(7,'late'),false);session.stop()
})
