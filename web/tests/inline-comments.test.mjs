import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const {refreshInlineComments}=await loadTypeScript('../src/lib/inline-comments.ts',import.meta.url)
const time='2026-10-28T00:00:00.123456Z', c=id=>({commentId:id}), page=(ids,next)=>({items:ids.map(c),hasNext:!!next,nextCursor:next?{createdAt:time,commentId:next,sort:'OLDEST',likeCount:null}:null})
test('inline refresh retains raw cursors and atomically rebuilds loaded pages with deduplication',async()=>{
 const calls=[],signal=new AbortController().signal
 const result=await refreshInlineComments('7',3,signal,undefined,async(id,cursor,s)=>{calls.push(cursor);assert.equal(id,'7');assert.equal(s,signal);return calls.length===1?page([1,2],2):calls.length===2?page([2,3],3):page([4])})
 assert.deepEqual(result.items.map(c=>c.commentId),[1,2,3,4]);assert.equal(result.segments,3)
 assert.deepEqual(calls,[null,{createdAt:time,commentId:2,sort:'OLDEST',likeCount:null},{createdAt:time,commentId:3,sort:'OLDEST',likeCount:null}])
})
test('inline refresh failure does not publish a partially rebuilt list',async()=>{
 const error=new Error('second page'),snapshot=page([8,9]),calls=[]
 await assert.rejects(refreshInlineComments('8',3,new AbortController().signal,undefined,async()=>{calls.push(1);if(calls.length===2)throw error;return page([1],1)}),e=>e===error)
 assert.deepEqual(snapshot.items,[c(8),c(9)]);assert.equal(calls.length,2)
})
test('inline like reconciliation reads until target or actual end, beyond old page count',async()=>{
 let n=0
 const result=await refreshInlineComments('7',1,new AbortController().signal,3,async()=>{n++;return page([n],n<4?n:undefined)})
 assert.equal(n,3);assert.equal(result.nextCursor.commentId,3)
})
test('inline refresh stops at end even when formerly more pages were loaded',async()=>{
 let n=0;const result=await refreshInlineComments('7',5,new AbortController().signal,99,async()=>{n++;return page([])})
 assert.equal(n,1);assert.equal(result.hasNext,false)
})
test('popular prefix refresh keeps chosen sort and detects cursor cycles without partial publish',async()=>{
 let n=0;await assert.rejects(refreshInlineComments('7',4,new AbortController().signal,undefined,async(_id,_cursor,_signal,_base,sort)=>{assert.equal(sort,'POPULAR');n++;return page([n],n===3?1:n)},'POPULAR'),e=>e.code==='UNEXPECTED_RESPONSE');assert.equal(n,3)
})
test('popular duplicate-only page preserves server cursor and later pages; no cross-page rank assumption',async()=>{
 let n=0;const cursors=[],raw={createdAt:time,commentId:1,sort:'POPULAR',likeCount:2}
 const result=await refreshInlineComments('7',3,new AbortController().signal,undefined,async(_id,cursor)=>{cursors.push(cursor);n++;return n===1?{items:[c(1)],hasNext:true,nextCursor:raw}:n===2?{items:[c(1)],hasNext:true,nextCursor:{...raw,likeCount:0}}:page([2])},'POPULAR')
 assert.deepEqual(cursors,[null,raw,{...raw,likeCount:0}]);assert.deepEqual(result.items,[c(1),c(2)]);assert.equal(result.segments,3)
})
