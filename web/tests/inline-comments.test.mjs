import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const {refreshInlineComments}=await loadTypeScript('../src/lib/inline-comments.ts',import.meta.url)
const time='2026-10-28T00:00:00.123456Z', c=id=>({commentId:id}), page=(ids,next)=>({items:ids.map(c),hasNext:!!next,nextCursor:next?{createdAt:time,commentId:next}:null})
test('inline refresh retains raw cursors and atomically rebuilds loaded pages with deduplication',async()=>{
 const calls=[],signal=new AbortController().signal
 const result=await refreshInlineComments('7',3,signal,undefined,async(id,cursor,s)=>{calls.push(cursor);assert.equal(id,'7');assert.equal(s,signal);return calls.length===1?page([1,2],2):calls.length===2?page([2,3],3):page([4])})
 assert.deepEqual(result.items.map(c=>c.commentId),[1,2,3,4]);assert.equal(result.segments,3)
 assert.deepEqual(calls,[null,{createdAt:time,commentId:2},{createdAt:time,commentId:3}])
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
