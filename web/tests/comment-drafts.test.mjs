import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const {createCommentDrafts}=await loadTypeScript('../src/lib/comment-drafts.ts',import.meta.url)
test('drafts survive page-boundary movement and another post success; only confirmed deletion discards its owner draft',()=>{
 const store=createCommentDrafts();store.setOwner(1);store.set(1,991,' 한글\n ');store.set(1,1000,'B')
 // A new first page excludes 991; reads have no permission to discard drafts.
 const page=Array.from({length:10},(_,i)=>1001-i);assert(!page.includes(991))
 store.set(1,1000,'');assert.deepEqual(store.snapshot(),{991:' 한글\n '})
 store.discardDeleted(2,991);assert.equal(store.snapshot()[991],' 한글\n ')
 store.discardDeleted(1,1000);assert.equal(store.snapshot()[991],' 한글\n ')
 store.discardDeleted(1,991);assert.deepEqual(store.snapshot(),{})
})
test('same owner and failed logout keep drafts; confirmed identity changes and approved departure clear them',()=>{
 const store=createCommentDrafts();store.setOwner(1);store.set(1,10,'A');store.setOwner(1);assert.equal(store.snapshot()[10],'A')
 store.setOwner(null);store.setOwner(2);store.set(1,10,'late');store.discardDeleted(1,20);assert.deepEqual(store.snapshot(),{})
 store.set(2,20,'new');store.clear();assert.deepEqual(store.snapshot(),{})
})
