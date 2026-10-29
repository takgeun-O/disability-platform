import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const {createCommentCreated}=await loadTypeScript('../src/lib/comment-created.ts',import.meta.url)
for(const first of ['record','confirm'])test(`server confirmation and creation commute: ${first} first`,()=>{
 const store=createCommentCreated();store.setOwner(1);const ticket=store.capture(1,1000)
 if(first==='record'){ticket.record(7,' 원문\n ');assert.equal(store.snapshot()[1000][0].content,' 원문\n ');ticket.confirm([7])}
 else{ticket.confirm([7]);ticket.record(7,' 원문\n ')}
 assert.deepEqual(store.snapshot(),{});ticket.confirm([8]);ticket.record(7,'late same ID');assert.deepEqual(store.snapshot(),{})
})
test('same content different IDs, partial confirmation, post isolation and empty reads',()=>{
 const store=createCommentCreated();store.setOwner(1);const a=store.capture(1,10),b=store.capture(1,20)
 a.record(1,'same');a.record(2,'same');b.record(3,'other');a.confirm([]);a.confirm([1]);assert.deepEqual(store.snapshot()[10],[{commentId:2,content:'same'}]);assert.equal(store.snapshot()[20][0].commentId,3)
 a.record(2,'overwrite');assert.equal(store.snapshot()[10][0].content,'same');a.record(NaN,'bad');assert.equal(store.snapshot()[10].length,1)
})
test('clear and identity changes reject old records and confirmations, same owner retains memory',()=>{
 const store=createCommentCreated();store.setOwner(1);const old=store.capture(1,10);old.record(1,'old');store.setOwner(1);assert.equal(store.snapshot()[10].length,1)
 store.clear();old.record(2,'late');old.confirm([3]);const fresh=store.capture(1,10);fresh.record(3,'new activity');assert.equal(store.snapshot()[10][0].commentId,3)
 store.setOwner(null);store.setOwner(1);fresh.record(4,'old same member session');assert.deepEqual(store.snapshot(),{})
 store.setOwner(2);old.confirm([5]);store.capture(2,10).record(5,'new user');assert.equal(store.snapshot()[10][0].content,'new user')
})
test('confirmed deletion rejects outstanding tickets only for the owned post',()=>{
 const store=createCommentCreated();store.setOwner(1);const a=store.capture(1,10),b=store.capture(1,20);a.record(1,'a');b.record(2,'b')
 store.discardDeleted(2,10);assert.equal(store.snapshot()[10].length,1)
 store.discardDeleted(1,10);a.record(3,'late');a.confirm([4]);assert.equal(store.snapshot()[10],undefined);assert.equal(store.snapshot()[20].length,1)
})
