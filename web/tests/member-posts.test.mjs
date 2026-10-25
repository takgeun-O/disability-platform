import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const {getMemberPosts}=await loadTypeScript('../src/lib/member-api.ts',import.meta.url)
const {createMemberActivityCache,refreshMemberPosts}=await loadTypeScript('../src/lib/member-activity.ts',import.meta.url)
const {safeListReturn}=await loadTypeScript('../src/lib/community-posts.ts',import.meta.url)
const stamp='2026-10-20T00:00:00.123456Z'
const item=id=>({id,type:'QUESTION',topic:'HEARING_AID',title:'내 글',contentPreview:'미리보기',likeCount: 0, liked: false, author:{memberId:1,nickname:'회원'},createdAt:stamp,commentCount:0})
const page={items:[item(10),item(9)],hasNext:true,nextCursor:{createdAt:stamp,postId:9}}
process.env.NEXT_PUBLIC_API_BASE_URL='http://test.invalid'
test('private posts: size10, session/no-store/no CSRF, raw microsecond cursor and postId -> afterId',async t=>{
 const calls=[];t.mock.method(globalThis,'fetch',async(url,options)=>{calls.push({url,options});return Response.json(page)})
 assert.deepEqual(await getMemberPosts(1,{createdAt:stamp,postId:11}),page)
 const {url,options}=calls[0],u=new URL(url);assert.equal(u.pathname,'/api/v1/members/me/posts');assert.equal(u.searchParams.get('size'),'10');assert.equal(u.searchParams.get('afterCreatedAt'),stamp);assert.equal(u.searchParams.get('afterId'),'11');assert.equal(options.cache,'no-store');assert.equal(options.credentials,'include');assert.equal(calls.length,1)
})
test('private posts rejects broken cursor/count/order/owner; empty and final accepted',async t=>{
 let value; t.mock.method(globalThis,'fetch',async()=>Response.json(value))
 const rejects=[{...page,nextCursor:null},{...page,nextCursor:{createdAt:stamp,id:9}},{...page,nextCursor:{createdAt:stamp,postId:8}},{...page,items:[item(9),item(10)]},{...page,items:[item(10),item(10)]},{...page,items:[{...item(10),author:{memberId:2,nickname:'다른회원'}}]},...[-1,1.2,'0',null,Number.MAX_SAFE_INTEGER+1].map(commentCount=>({...page,items:[{...item(10),commentCount}]})),{...page,hasNext:false},{...page,items:[]},{...page,items:[{...item(10),createdAt:'invalid'}]}]
 for(value of rejects)await assert.rejects(getMemberPosts(1),e=>e.code==='UNEXPECTED_RESPONSE')
 value={items:[],hasNext:false,nextCursor:null};assert.deepEqual(await getMemberPosts(1),value)
 value={items:[item(10)],hasNext:false,nextCursor:null};assert.deepEqual(await getMemberPosts(1),value)
})
test('private posts 401, malformed JSON and network stay errors, never retry',async t=>{
 let response, calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;if(response===null)throw Error('offline');return response})
 response=Response.json({code:'AUTHENTICATION_REQUIRED'},{status:401});await assert.rejects(getMemberPosts(1),e=>e.status===401)
 response=new Response('{',{headers:{'Content-Type':'application/json'}});await assert.rejects(getMemberPosts(1),e=>e.code==='UNEXPECTED_RESPONSE')
 response=null;await assert.rejects(getMemberPosts(1),e=>e.code==='NETWORK_ERROR');assert.equal(calls,3)
})
test('private cache clears on expiry/account switch, invalidates old same-member leases and return pointers',()=>{
 const cache=createMemberActivityCache();cache.setOwner(1);const old=cache.lease(1);old.write('visit',page);old.markReturn('visit');assert.equal(old.read('visit'),page)
 cache.setOwner(null);assert(!old.valid());assert.equal(old.read('visit'),undefined);cache.setOwner(1);const fresh=cache.lease(1);old.write('late',page);assert.equal(fresh.read('late'),undefined);assert.equal(fresh.takeReturn(),null)
 fresh.write('new',page);cache.setOwner(2);assert.equal(cache.lease(2).read('new'),undefined);assert.equal(fresh.read('new'),undefined)
})
test('refresh rebuilds exact server page boundaries; failed later page does not mutate cached list',async()=>{
 const calls=[],signal=new AbortController().signal
 const result=await refreshMemberPosts(1,3,signal,async(id,cursor)=>{calls.push(cursor);return calls.length===1?page:{items:[item(8)],hasNext:false,nextCursor:null}})
 assert.deepEqual(calls,[null,page.nextCursor]);assert.deepEqual(result.items.map(p=>p.id),[10,9,8]);assert.equal(result.hasNext,false);assert.equal(result.nextCursor,null);assert.equal(result.segments,2)
 const before=structuredClone(page);let n=0;await assert.rejects(refreshMemberPosts(1,3,signal,async()=>{if(n++)throw Error('failed');return page}));assert.deepEqual(page,before)
})
test('only exact mypage is allowed as private detail return; public paths retain filters',()=>{
 assert.equal(safeListReturn('/mypage'),'/mypage')
 for(const v of ['/mypage/evil','//evil.test/mypage','/mypage?url=https://evil.test','/mypage#bad','/mypage\\evil'])assert.equal(safeListReturn(v),'/community/posts')
 assert.equal(safeListReturn('/community/topics/hearing-aid?type=REVIEW'),'/community/topics/hearing-aid?type=REVIEW')
})
