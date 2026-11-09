import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const api=await loadTypeScript('../src/lib/member-api.ts',import.meta.url)
process.env.NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8082'
const stamp='2026-10-27T04:23:41.115818Z'
const post={id:10,createdAt:stamp,visibility:'HIDDEN',type:null,topic:null,title:null,contentPreview:null,author:null,commentCount:null,likeCount:null,liked:null}
const comment={ contentUpdatedAt: null,commentId:10,createdAt:stamp,visibility:'PARENT_HIDDEN',content:null,updatedAt:null,likeCount:null,liked:null,post:{postId:10,title:null,type:null,topic:null}}
const page=item=>({items:[item],hasNext:false,nextCursor:null})
for(const [name,item,read] of [['post',post,()=>api.getMemberPosts(1)],['comment',comment,()=>api.getMemberComments()]]){
 test(`redacted ${name} is a valid private response`,async t=>{t.mock.method(globalThis,'fetch',async()=>Response.json(page(item)));assert.deepEqual((await read()).items,[item])})
 test(`redacted ${name} must not carry content or invalid visibility`,async t=>{let value;t.mock.method(globalThis,'fetch',async()=>Response.json(page(value)));for(const patch of [{visibility:undefined},{visibility:'UNKNOWN'},{visibility:'VISIBLE'},name==='post'?{title:'leak'}:{content:'leak'},name==='post'?{id:null}:{commentId:null}]){value={...item,...patch};await assert.rejects(read(),e=>e.code==='UNEXPECTED_RESPONSE')}})
}
const visiblePost={...post,visibility:'VISIBLE',type:'QUESTION',topic:'HEARING_AID',title:'제목',contentPreview:'본문',author:{memberId:1,nickname:'회원'},commentCount:2,likeCount:3,liked:true}
const visibleComment={...comment,visibility:'VISIBLE',content:'댓글',updatedAt:stamp,likeCount:3,liked:false,post:{postId:10,title:'원문',type:'QUESTION',topic:'HEARING_AID'}}
test('mixed, all hidden and empty pages preserve redaction and cursor precision',async t=>{
 let body;t.mock.method(globalThis,'fetch',async()=>Response.json(body))
 for(const [items,read,idKey,cursorKey] of [
  [[visiblePost,{...post,id:9}],c=>api.getMemberPosts(1,c),'id','postId'],
  [[visibleComment,{...comment,commentId:9,visibility:'HIDDEN'},{...comment,commentId:8}],c=>api.getMemberComments(c),'commentId','commentId'],
  [[post],c=>api.getMemberPosts(1,c),'id','postId'],[[comment],c=>api.getMemberComments(c),'commentId','commentId']]){
  const last=items.at(-1),cursor={createdAt:stamp,[cursorKey]:last[idKey]}
  body={items,hasNext:true,nextCursor:cursor};assert.deepEqual(await read(null),body)
  body={items:[{...last,[idKey]:last[idKey]-1}],hasNext:false,nextCursor:null};assert.deepEqual((await read(cursor)).items,body.items)
  body={items:[],hasNext:false,nextCursor:null};assert.deepEqual(await read(null),body)
 }
})
test('visible nulls, redacted missing fields, owner mismatch, malformed cursors/order and oversized pages are rejected',async t=>{
 let body;t.mock.method(globalThis,'fetch',async()=>Response.json(body))
 for(const [item,fields,read] of [[visiblePost,['title','author','commentCount','likeCount','liked'],()=>api.getMemberPosts(1)],[visibleComment,['content','updatedAt','likeCount','liked'],()=>api.getMemberComments()]]){
  for(const key of fields){body=page({...item,[key]:null});await assert.rejects(read())}
 }
 for(const [item,read] of [[post,()=>api.getMemberPosts(1)],[comment,()=>api.getMemberComments()]]){
  for(const key of Object.keys(item)){const value={...item};delete value[key];body=page(value);await assert.rejects(read())}
  body={items:Array(11).fill(item),hasNext:false,nextCursor:null};await assert.rejects(read())
  body={items:[item],hasNext:true,nextCursor:{createdAt:stamp,postId:99,commentId:99}};await assert.rejects(read())
  body={items:[item,item],hasNext:false,nextCursor:null};await assert.rejects(read())
 }
 body=page({...visiblePost,author:{memberId:2,nickname:'다른회원'}});await assert.rejects(api.getMemberPosts(1))
 assert.equal(api.isMemberPost({...post,visibility:'PARENT_HIDDEN'}),false)
})
test('incoming visibility replaces previously visible row without preserving redacted data',async()=>{
 const {mergeMemberPosts,mergeMemberComments}=await loadTypeScript('../src/lib/member-activity.ts',import.meta.url)
 assert.deepEqual(mergeMemberPosts([visiblePost],[post]),[post]);assert.deepEqual(mergeMemberPosts([post],[visiblePost]),[visiblePost])
 assert.deepEqual(mergeMemberComments([visibleComment],[comment]),[comment]);assert.deepEqual(mergeMemberComments([comment],[visibleComment]),[visibleComment])
})
