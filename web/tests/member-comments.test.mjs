import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const {getMemberComments}=await loadTypeScript('../src/lib/member-api.ts',import.meta.url)
const {refreshMemberComments,mergeMemberComments,createMemberActivityCache}=await loadTypeScript('../src/lib/member-activity.ts',import.meta.url)
const stamp='2026-10-20T00:00:00.123456Z'
const item=commentId=>({ contentUpdatedAt: null,visibility:"VISIBLE",liked:false,likeCount:0,commentId,content:'댓글',createdAt:stamp,updatedAt:stamp,post:{postId:42,title:'원문',type:'QUESTION',topic:'HEARING_AID'}})
const page={items:[item(10),item(9)],hasNext:true,nextCursor:{createdAt:stamp,commentId:9}}
process.env.NEXT_PUBLIC_API_BASE_URL='http://test.invalid'
test('comments use session, no-store and raw commentId cursor without CSRF/member parameter',async t=>{
 const calls=[];t.mock.method(globalThis,'fetch',async(url,options)=>{calls.push({url,options});return Response.json(page)})
 assert.deepEqual(await getMemberComments({createdAt:stamp,commentId:11}),page)
 const {url,options}=calls[0],u=new URL(url);assert.equal(u.pathname,'/api/v1/members/me/comments');assert.deepEqual([...u.searchParams],[['size','10'],['afterCreatedAt',stamp],['afterId','11']]);assert.equal(options.credentials,'include');assert.equal(options.cache,'no-store');assert.equal(calls.length,1)
})
test('comments reject malformed fields, enums, cursor and descending order; final/empty accepted',async t=>{
 let value;t.mock.method(globalThis,'fetch',async()=>Response.json(value))
 const bad=[{...page,nextCursor:null},{...page,nextCursor:{createdAt:stamp,postId:9}},{...page,nextCursor:{createdAt:stamp,commentId:8}},{...page,hasNext:false},{...page,items:[]},{...page,items:[item(9),item(10)]},{...page,items:[item(10),item(10)]}]
 for(const patch of [{commentId:0},{commentId:1.5},{commentId:'10'},{content:null},{content:''},{createdAt:'bad'},{updatedAt:null},{post:{...item(10).post,postId:-1}},{post:{...item(10).post,type:'BAD'}},{post:{...item(10).post,topic:'BAD'}},{post:{...item(10).post,title:null}}])bad.push({...page,items:[{...item(10),...patch}]})
 for(value of bad)await assert.rejects(getMemberComments(),e=>e.code==='UNEXPECTED_RESPONSE')
 for(value of [{items:[],hasNext:false,nextCursor:null},{items:[item(10)],hasNext:false,nextCursor:null}])assert.deepEqual(await getMemberComments(),value)
})
test('microseconds determine order, same post comments remain distinct, dedup uses commentId',async t=>{
 const fine={...item(10),createdAt:'2026-10-20T00:00:00.123457Z'}
 t.mock.method(globalThis,'fetch',async()=>Response.json({items:[fine,item(11)],hasNext:false,nextCursor:null}))
 assert.equal((await getMemberComments()).items.length,2)
 assert.deepEqual(mergeMemberComments([item(10)],[item(10),item(9)]).map(c=>c.commentId),[10,9])
})
test('refresh is atomic, follows exact server cursors and stops at end',async()=>{
 const calls=[],signal=new AbortController().signal
 const result=await refreshMemberComments(3,signal,async cursor=>{calls.push(cursor);return calls.length===1?page:{items:[item(8)],hasNext:false,nextCursor:null}})
 assert.deepEqual(calls,[null,page.nextCursor]);assert.deepEqual(result.items.map(c=>c.commentId),[10,9,8]);assert.equal(result.segments,2)
 const before=structuredClone(page);let n=0;await assert.rejects(refreshMemberComments(3,signal,async()=>{if(n++)throw Error('failed');return page}));assert.deepEqual(page,before)
})
test('private comment snapshot and return pointer cannot survive logout or account switch',()=>{
 const cache=createMemberActivityCache();cache.setOwner(1);const lease=cache.lease(1);lease.write('visit',{comments:page});lease.markReturn('visit');cache.setOwner(null);cache.setOwner(1);lease.write('late',{comments:page});assert.equal(cache.lease(1).read('late'),undefined);assert.equal(cache.lease(1).takeReturn(),null);cache.setOwner(2);assert.equal(cache.lease(2).read('visit'),undefined)
})

// Expected values come from Java Character.isWhitespace / CommentInputPolicy / PostInputPolicy.
test('Java whitespace contract, length and original response strings',async t=>{
 let value;t.mock.method(globalThis,'fetch',async()=>Response.json(value))
 const cases=[['normal',true],['',false],[' \t\n',false],['\u001c',false],['\u00a0',true],['\u202f',true],['\u2007',true],['\ufeff',true],[' \n내용\u00a0\t',true]]
 for(const field of ['content','title'])for(const [text,accepted]of cases){
  const special=item(9);if(field==='content')special.content=text;else special.post.title=text
  value={items:[item(10),special],hasNext:false,nextCursor:null}
  if(accepted)assert.deepEqual(await getMemberComments(),value,field+JSON.stringify(text))
  else await assert.rejects(getMemberComments(),e=>e.code==='UNEXPECTED_RESPONSE')
 }
 for(const [field,length]of [['content',2001],['title',121]]){
  const special=item(9);if(field==='content')special.content='가'.repeat(length);else special.post.title='가'.repeat(length)
  value={items:[special],hasNext:false,nextCursor:null};await assert.rejects(getMemberComments(),e=>e.code==='UNEXPECTED_RESPONSE')
 }
})

test('private comment likeCount is required safe nonnegative integer, preserves per-item counts and liked',async t=>{
 let value;t.mock.method(globalThis,'fetch',async()=>Response.json(value))
 for(const likeCount of [undefined,null,-1,0.5,'3',Number.MAX_SAFE_INTEGER+1]){
  value={items:[{...item(10),likeCount}],hasNext:false,nextCursor:null}
  await assert.rejects(getMemberComments(),e=>e.code==='UNEXPECTED_RESPONSE')
 }
 for(const likeCount of [0,3,Number.MAX_SAFE_INTEGER]){
  value={items:[{...item(10),likeCount},item(9)],hasNext:false,nextCursor:null}
  assert.deepEqual(await getMemberComments(),value)
  assert.equal((await getMemberComments()).items[0].liked,false)
 }
})

test('private comment liked must be a boolean; counts and authorship do not imply liking',async t=>{
 let value;t.mock.method(globalThis,'fetch',async()=>Response.json(value))
 for(const liked of [undefined,null,'true','false',0,1,{},[]]){
  value={items:[{...item(10),liked}],hasNext:false,nextCursor:null}
  await assert.rejects(getMemberComments(),e=>e.code==='UNEXPECTED_RESPONSE')
 }
 value={items:[{...item(10),likeCount:3,liked:true},{...item(9),likeCount:3,liked:false},item(8)],hasNext:false,nextCursor:null}
 assert.deepEqual(await getMemberComments(),value)
})
