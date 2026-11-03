import test from "node:test"
import assert from "node:assert/strict"
import {loadTypeScript} from "./helpers/load-typescript.mjs"
const api=await loadTypeScript('../src/lib/admin-moderation.ts',import.meta.url)
const target={postId:1,action:'hide',label:'검증'},stamp='2026-11-01T00:00:00Z'
const success={postId:1,hidden:true,hiddenAt:stamp,changed:true}
test('reason uses Java strip and 500 codepoints, preserves inner whitespace',()=>{
 assert(api.moderationReasonError('　 '));assert.equal(api.moderationReasonError('😀'.repeat(500)),'');assert(api.moderationReasonError('😀'.repeat(501)))
})
test('mutation result strictly checks target, booleans, hidden timestamp and action',()=>{
 assert(api.isModerationResult(success,target));assert(api.isModerationResult({...success,changed:false},target))
 for(const bad of [{...success,postId:2},{...success,changed:'true'},{...success,hiddenAt:null},{...success,hidden:false}])assert(!api.isModerationResult(bad,target))
 assert(!api.isModerationResult({...success,commentId:2},{...target,commentId:3}))
 assert(api.isModerationResult({...success,hidden:false,hiddenAt:null},{...target,action:'restore'}))
})
test('CSRF before one POST, normalization, cancellation during preparation sends no write',async t=>{
 process.env.NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8082';const calls=[];let current=true
 t.mock.method(globalThis,'fetch',async(u,o)=>{calls.push({u,o});return new Response(JSON.stringify(u.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'synthetic'}:success),{headers:{'Content-Type':'application/json'}})})
 await api.moderate(target,'　사유  내부　',()=>current)
 assert.equal(calls.length,2);assert.equal(calls[1].o.method,'POST');assert.deepEqual(JSON.parse(calls[1].o.body),{reason:'사유  내부'});assert.equal(calls[1].o.credentials,'include');assert.equal(calls[1].o.headers['X-CSRF-TOKEN'],'synthetic')
 calls.length=0;t.mock.method(globalThis,'fetch',async()=>{current=false;return new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'synthetic'}),{headers:{'Content-Type':'application/json'}})})
 assert.equal(await api.moderate(target,'사유',()=>current),null);assert.equal(calls.length,0)
})
test('CSRF rejection, conflicts and uncertain transport never retry mutation',async t=>{
 process.env.NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8082'
 for(const [status,code,uncertain] of [[403,'CSRF_TOKEN_INVALID',false],[403,'ADMIN_REQUIRED',false],[409,'POST_DELETED',false],[404,'COMMENT_NOT_FOUND',false],[500,'INTERNAL_SERVER_ERROR',true],[200,'malformed',true]]){
  let posts=0;t.mock.method(globalThis,'fetch',async(u)=>{if(u.endsWith('/csrf'))return new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'synthetic'}),{headers:{'Content-Type':'application/json'}});posts++;return new Response(JSON.stringify({code}),{status,headers:{'Content-Type':'application/json'}})})
  await assert.rejects(api.moderate(target,'사유',()=>true),e=>e.uncertain===uncertain);assert.equal(posts,1)
 }
})
test('CSRF preparation network failure differs from write timeout',async t=>{
 let posted=false;t.mock.method(globalThis,'fetch',async()=>{throw new Error('network')})
 await assert.rejects(api.moderate(target,'사유',()=>true),e=>e.code==='CSRF_PREPARATION_FAILED'&&!e.uncertain)
 t.mock.method(globalThis,'fetch',async(u)=>{if(u.endsWith('/csrf'))return new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'synthetic'}),{headers:{'Content-Type':'application/json'}});posted=true;throw new DOMException('timeout','TimeoutError')})
 await assert.rejects(api.moderate(target,'사유',()=>true),e=>e.uncertain);assert(posted)
})
test('malformed error body still preserves HTTP authentication/permission status',async t=>{
 for(const status of [401,403]){
  t.mock.method(globalThis,'fetch',async u=>u.endsWith('/csrf')?new Response(JSON.stringify({headerName:'X-CSRF-TOKEN',token:'synthetic'}),{headers:{'Content-Type':'application/json'}}):new Response('{',{status,headers:{'Content-Type':'application/json'}}))
  await assert.rejects(api.moderate(target,'사유',()=>true),e=>e.status===status&&!e.uncertain)
 }
})
