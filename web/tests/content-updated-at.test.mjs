import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const publicApi=await loadTypeScript('../src/lib/community-comments.ts',import.meta.url)
const memberApi=await loadTypeScript('../src/lib/member-api.ts',import.meta.url)
const adminApi=await loadTypeScript('../src/lib/admin-detail.ts',import.meta.url)
const createdAt='2026-10-27T04:23:41.115818Z',updatedAt='2026-11-05T03:00:00Z'
const common={commentId:1,content:'본문\n 원문',createdAt,updatedAt,contentUpdatedAt:null,likeCount:0,liked:false}
const pub={...common,author:{memberId:7,nickname:'검증'}}
const own={...common,visibility:'VISIBLE',post:{postId:2,title:'제목',type:'GENERAL',topic:'DAILY'}}
const admin={...common,author:{memberId:7,nickname:'검증',withdrawn:false},status:'HIDDEN',hiddenAt:updatedAt,deletedAt:null}
const checkPublic=c=>publicApi.isCommentPage({items:[c],hasNext:false,nextCursor:null},'OLDEST')
const checkAdmin=c=>adminApi.isAdminCommentPage({items:[c],postId:2,postStatus:'HIDDEN',page:0,size:20,totalElements:1,totalPages:1,hasNext:false,hasPrevious:false})
for(const [name,check,item] of [['public',checkPublic,pub],['member',memberApi.isMemberComment,own],['admin',checkAdmin,admin]]) {
 test(`${name}: contentUpdatedAt required nullable real instant, independent of entity update`,()=>{
  assert(check(item));assert(check({...item,contentUpdatedAt:createdAt}));
  for(const value of [undefined,42,false,'','invalid','2026-02-30T00:00:00Z','2026-11-05','2026-11-05T25:00:00Z'])assert.equal(check({...item,contentUpdatedAt:value}),false,String(value))
  const missing={...item};delete missing.contentUpdatedAt;assert.equal(check(missing),false)
 })
}
test('member hidden states require redacted contentUpdatedAt, including formerly edited comments',()=>{
 for(const visibility of ['HIDDEN','PARENT_HIDDEN']) {
  const hidden={...own,visibility,content:null,updatedAt:null,contentUpdatedAt:null,likeCount:null,liked:null,post:{postId:2,title:null,type:null,topic:null}}
  assert(memberApi.isMemberComment(hidden))
  for(const value of [undefined,createdAt])assert.equal(memberApi.isMemberComment({...hidden,contentUpdatedAt:value}),false)
 }
})
test('public and member reads preserve raw content edit time and creation cursor precision',async t=>{
 process.env.NEXT_PUBLIC_API_BASE_URL='http://api.test'
 const edit='2026-10-28T05:06:07.987654Z';let body={items:[{...pub,contentUpdatedAt:edit}],hasNext:false,nextCursor:null}
 t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}}))
 const page=await publicApi.getComments('2');assert.equal(page.items[0].contentUpdatedAt,edit);assert.equal(page.items[0].createdAt,createdAt)
 body={items:[{...own,contentUpdatedAt:edit}],hasNext:false,nextCursor:null}
 assert.equal((await memberApi.getMemberComments()).items[0].contentUpdatedAt,edit)
})
test('204 identical-body update invents no timestamp; subsequent GET remains authoritative',async t=>{
 process.env.NEXT_PUBLIC_API_BASE_URL='http://api.test'
 const edit='2026-10-28T05:06:07.987654Z',calls=[]
 t.mock.method(globalThis,'fetch',async(url,options)=>{
  calls.push({url,options})
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'mock-only'})
  if(options.method==='PUT'){assert.equal(JSON.parse(options.body).content,pub.content);return new Response(null,{status:204})}
  return Response.json({items:[{...pub,contentUpdatedAt:edit}],hasNext:false,nextCursor:null})
 })
 assert.equal(await publicApi.changeComment('2','PUT',1,pub.content,()=>true),1)
 assert.equal((await publicApi.getComments('2')).items[0].contentUpdatedAt,edit)
 assert.equal(calls.filter(c=>c.options.method==='PUT').length,1)
})
