import assert from 'node:assert/strict'
import { test } from 'node:test'
import { loadTypeScript } from './helpers/load-typescript.mjs'
const api = await loadTypeScript('../src/lib/community-api.ts', import.meta.url)
const model = await loadTypeScript('../src/lib/community-posts.ts', import.meta.url)
const cache = await loadTypeScript('../src/lib/community-feed.ts', import.meta.url)
const query = {type:'QUESTION',topic:'HEARING_AID',page:0}
const row = id => ({id,type:query.type,topic:query.topic,title:'실제 텍스트',contentPreview:'<b>일반 텍스트</b>',author:{memberId:1,nickname:'회원'},createdAt:'2026-10-18T00:00:00.123456Z',commentCount:0})
const page = {items:[row(30),row(29)],hasNext:true,nextCursor:{createdAt:row(29).createdAt,postId:29}}
async function response(body, run) {
 const original=globalThis.fetch
 globalThis.fetch=async (url,options)=>{ assert.equal(options.credentials,'omit');assert.equal(options.cache,'no-store');return new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}}) }
 try { await run() } finally {globalThis.fetch=original}
}
const read=cursor=>api.getFeed(query,cursor,undefined,'https://api.example.test')
test('feed opaque microsecond cursor/filter URL, no page, no credentials', async()=>{
 const original=globalThis.fetch;let count=0
 globalThis.fetch=async(url,options)=>{count++;const u=new URL(url);assert.equal(u.pathname,'/api/v1/community/posts/feed');assert.equal(u.searchParams.get('type'),'QUESTION');assert.equal(u.searchParams.get('topic'),'HEARING_AID');assert.equal(u.searchParams.get('size'),'20');assert.equal(u.searchParams.has('page'),false);assert.equal(u.searchParams.get('afterCreatedAt'),count===1?null:'2026-10-18T00:00:00.123456Z');assert.equal(u.searchParams.get('afterId'),count===1?null:'29');assert.equal(options.credentials,'omit');return new Response(JSON.stringify(count===1?page:{items:[row(28)],hasNext:false,nextCursor:null}),{headers:{'Content-Type':'application/json'}})}
 try{const first=await read();assert.equal((await read(first.nextCursor)).items[0].id,28);assert.equal(count,2)}finally{globalThis.fetch=original}
})
for(const [name,bad] of [
 ['missing preview',{...row(30),contentPreview:undefined}],['long preview',{...row(30),contentPreview:'😀'.repeat(201)}],
 ...[undefined,null,'0',-1,1.5,Number.MAX_SAFE_INTEGER+1].map(v=>['count '+v,{...row(30),commentCount:v}]),
 ['bad author',{...row(30),author:null}],['unsafe id',{...row(30),id:Number.MAX_SAFE_INTEGER+1}],['wrong filter',{...row(30),type:'GENERAL'}]
])test('feed rejects '+name,()=>response({...page,items:[bad,row(29)]},()=>assert.rejects(read())))
for(const [name,bad] of [
 ['duplicate ids',{...page,items:[row(29),row(29)]}],['wrong order',{...page,items:[row(28),row(29)]}],['missing cursor',{...page,nextCursor:null}],['empty next',{...page,items:[]}],['cursor not last',{...page,nextCursor:{...page.nextCursor,postId:30}}],['terminal cursor',{...page,hasNext:false}],['nonboolean',{...page,hasNext:1}]
])test('feed rejects '+name,()=>response(bad,()=>assert.rejects(read())))
test('repeated/older request cursor cannot loop',()=>response(page,()=>assert.rejects(read(page.nextCursor))))
test('empty terminal and unicode200 accepted',async()=>{await response({items:[],hasNext:false,nextCursor:null},async()=>assert.equal((await read()).items.length,0));await response({items:[{...row(1),contentPreview:'😀'.repeat(200),commentCount:Number.MAX_SAFE_INTEGER}],hasNext:false,nextCursor:null},async()=>assert.equal((await read()).items[0].commentCount,Number.MAX_SAFE_INTEGER))})
test('cursor links preserve precision and safe route, legacy page stripped',()=>{const href=model.listHref(query,'/community',page.nextCursor);assert.deepEqual(model.parsePostCursor(new URLSearchParams(href.split('?')[1])),page.nextCursor);assert.equal(model.safeListReturn(href),href);assert.equal(model.safeListReturn('/community/topics/hearing-aid?page=99&type=QUESTION'),'/community/topics/hearing-aid?type=QUESTION');assert.equal(model.parsePostCursor(new URLSearchParams('afterId=29')),null)})
test('merge de-duplicates IDs without replacing existing loaded row',()=>assert.deepEqual(cache.mergeFeed([row(2)],[{...row(2),title:'new'},row(1)]),[row(2),row(1)]))
test('cache keyed by history visit, bounded8 entries and1000 rows',()=>{const snap={...page,version:1,segments:1};cache.cacheFeed('first','/community',snap);cache.cacheFeed('second','/community',{...snap,position:{y:120,offset:0}});assert.equal(cache.readFeedCache('first','/community').position,undefined);assert.equal(cache.readFeedCache('second','/community').position.y,120);assert.equal(cache.readFeedCache('first','/community?type=GENERAL'),null);for(let i=0;i<9;i++)cache.cacheFeed('visit'+i,'/community',snap);assert.equal(cache.readFeedCache('first','/community'),null);cache.cacheFeed('huge','/community',{...snap,items:Array.from({length:1001},(_,i)=>row(i+1))});assert.equal(cache.readFeedCache('huge','/community'),null)})
test('reconcile60: server counts, removal, no new top insertion, bounded reads',async()=>{
 const previous={items:Array.from({length:60},(_,i)=>row(100-i)),hasNext:true,nextCursor:{createdAt:row(41).createdAt,postId:41},segments:3,version:0}
 const fresh=[row(101),...previous.items.filter(p=>p.id!==70).map(p=>({...p,commentCount:5})),row(40),row(39)]
 let calls=0
 const result=await cache.reconcileFeed(query,null,previous,new AbortController().signal,async(_q,cursor)=>{calls++;const rest=fresh.filter(p=>!cursor||p.id<cursor.postId),items=rest.slice(0,20),hasNext=rest.length>20;return{items,hasNext,nextCursor:hasNext?{createdAt:items.at(-1).createdAt,postId:items.at(-1).id}:null}})
 assert.equal(calls,3);assert.equal(result.items.length,59);assert(!result.items.some(p=>p.id===101||p.id===70));assert(result.items.every(p=>p.commentCount===5));assert.equal(result.nextCursor.postId,41)
})
test('too many new posts bounds refresh and fails instead of erasing snapshot',async()=>{let calls=0;const previous={...page,segments:1,version:0};await assert.rejects(cache.reconcileFeed(query,null,previous,new AbortController().signal,async()=>{calls++;return{...page,items:[row(100)]}}),/목록 변화/);assert.equal(calls,3);assert.equal(previous.items.length,2)})
