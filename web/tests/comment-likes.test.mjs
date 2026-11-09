import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const api=await loadTypeScript('../src/lib/community-comments.ts',import.meta.url)
const {createPostLikes:createCommentLikes}=await loadTypeScript('../src/lib/post-likes.ts',import.meta.url)
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}})
const item={ contentUpdatedAt: null,commentId:5,content:' 원문\n',author:{memberId:1,nickname:'검토'},createdAt:'2026-10-27T00:00:00.123456Z',updatedAt:'2026-10-27T00:00:00.123456Z',likeCount:2,liked:true}
async function fetchWith(fn,run){const old=globalThis.fetch,base=process.env.NEXT_PUBLIC_API_BASE_URL;process.env.NEXT_PUBLIC_API_BASE_URL='http://api.test';globalThis.fetch=fn;try{await run()}finally{globalThis.fetch=old;if(base===undefined)delete process.env.NEXT_PUBLIC_API_BASE_URL;else process.env.NEXT_PUBLIC_API_BASE_URL=base}}
for(const liked of [true,false])test(`comment ${liked?'PUT':'DELETE'} uses dynamic CSRF, session, no body; server count`,async()=>{
 let calls=0
 await fetchWith(async(url,o)=>{calls++;if(url.endsWith('/csrf'))return json({headerName:'X-CSRF-TOKEN',token:'test-only'});assert.equal(url,'http://api.test/api/v1/community/posts/7/comments/5/like');assert.equal(o.method,liked?'PUT':'DELETE');assert.equal(o.credentials,'include');assert.equal(o.cache,'no-store');assert.equal(o.body,undefined);assert.equal(o.headers['X-CSRF-TOKEN'],'test-only');return json({postId:7,commentId:5,likeCount:19,liked})},async()=>assert.deepEqual(await api.changeCommentLike('7',5,liked,()=>true),{likeCount:19,liked}))
 assert.equal(calls,2)
})
for(const value of [undefined,null,-1,1.5,'2',Number.MAX_SAFE_INTEGER+1])test(`comment count rejects ${String(value)}`,()=>assert.equal(api.isCommentPage({items:[{...item,likeCount:value}],hasNext:false,nextCursor:null}),false))
for(const value of [undefined,null,1,'false'])test(`comment liked rejects ${String(value)}`,()=>assert.equal(api.isCommentPage({items:[{...item,liked:value}],hasNext:false,nextCursor:null}),false))
for(const bad of [{postId:8,commentId:5,likeCount:1,liked:true},{postId:7,commentId:6,likeCount:1,liked:true},{postId:7,commentId:5,likeCount:-1,liked:true},{postId:7,commentId:5,likeCount:1,liked:'true'}])test('like response identity/type mismatch is uncertain, never retried',async()=>{
 let n=0;await fetchWith(async url=>url.endsWith('/csrf')?json({headerName:'X-CSRF-TOKEN',token:'test-only'}):(n++,json(bad)),async()=>await assert.rejects(api.changeCommentLike('7',5,true,()=>true),e=>e.uncertain));assert.equal(n,1)
})
for(const [status,code] of [[401,'AUTHENTICATION_REQUIRED'],[403,'CSRF_TOKEN_INVALID'],[403,'COMMENT_LIKE_NOT_ALLOWED'],[404,'POST_NOT_FOUND'],[404,'COMMENT_NOT_FOUND'],[500,'SERVER_ERROR']])test(`${code} remains distinct without mutation replay`,async()=>{
 let n=0;await fetchWith(async url=>url.endsWith('/csrf')?json({headerName:'X-CSRF-TOKEN',token:'test-only'}):(n++,json({code},status)),async()=>await assert.rejects(api.changeCommentLike('7',5,true,()=>true),e=>e.code===code&&e.status===status));assert.equal(n,1)
})
test('session changes while obtaining CSRF: do not send change',async()=>{
 let current=true,writes=0;await fetchWith(async url=>{if(url.endsWith('/csrf')){current=false;return json({headerName:'X-CSRF-TOKEN',token:'test-only'})}writes++},async()=>assert.equal(await api.changeCommentLike('7',5,true,()=>current),null));assert.equal(writes,0)
})
test('SSR omits credentials; browser includes; cursor remains raw, GET never asks for CSRF',async()=>{
 for(const browser of [false,true]){
  if(browser)globalThis.window={}
  try{await fetchWith(async(url,o)=>{assert.equal(o.credentials,browser?'include':'omit');assert.equal(o.cache,'no-store');const u=new URL(url);assert.equal(u.searchParams.get('afterCreatedAt'),item.createdAt);assert.equal(u.searchParams.get('afterId'),'4');assert.equal(u.searchParams.has('viewerId'),false);return json({items:[item],hasNext:false,nextCursor:null})},async()=>assert.equal((await api.getComments('7',{commentId:4,createdAt:item.createdAt,sort:'OLDEST',likeCount:null})).items[0].content,item.content))}finally{delete globalThis.window}
 }
})
test('comment locks independent; buffered/read-before-mutation cannot restore old state',()=>{
 const s=createCommentLikes();s.setContext('A:1');s.read().accept([{...item,id:5},{...item,id:6}]);const buffer=s.read(),change=s.change(5);assert.equal(s.change(5),null);const other=s.change(6);assert(other);change.commit({liked:false,likeCount:18});change.finish();buffer.accept([{...item,id:5}]);assert.equal(s.get(5).likeCount,18);assert.equal(s.get(5).liked,false);other.finish()
})
for(const next of ['anonymous','A:2','B:3'])test(`comment old buffered/success/finally rejected after ${next}`,()=>{
 const s=createCommentLikes();s.setContext('A:1');s.read().accept([{...item,id:5}]);const read=s.read(),op=s.change(5);s.setContext(next);assert(!read.valid());s.read().accept([{...item,id:5,liked:false,likeCount:7}]);const fresh=s.change(5);op.commit({liked:true,likeCount:99});op.finish();read.accept([{...item,id:5}]);assert.equal(s.get(5).likeCount,7);assert(s.get(5).pending);fresh.finish()
})
