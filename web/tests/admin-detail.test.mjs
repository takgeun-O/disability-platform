import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const api=await loadTypeScript("../src/lib/admin-detail.ts",import.meta.url)
const nav=await loadTypeScript("../src/lib/admin-navigation.ts",import.meta.url)
const stamp="2026-11-01T00:00:00.123456Z"
const common={author:{memberId:7,nickname:"검증",withdrawn:true},status:"HIDDEN",createdAt:stamp,updatedAt:stamp,hiddenAt:stamp,deletedAt:null}
const post={...common,postId:1,title:"제목",content:"전체\n본문",type:"GENERAL",topic:"DAILY"}
const page={postId:1,postStatus:"DELETED",items:[{ contentUpdatedAt: null,...common,commentId:3,content:"댓글"}],page:0,size:20,totalElements:1,totalPages:1,hasNext:false,hasPrevious:false}
test("admin detail/comment contracts preserve full content, parent status and nullable instants",()=>{
 assert(api.isAdminPostDetail(post));assert(api.isAdminCommentPage(page))
 for(const field of ['content','author','createdAt','updatedAt','hiddenAt','deletedAt']){const bad={...post};delete bad[field];assert(!api.isAdminPostDetail(bad),field)}
 for(const status of ['ALL',null,'PUBLIC'])assert(!api.isAdminCommentPage({...page,postStatus:status}))
 assert(!api.isAdminCommentPage({...page,items:[page.items[0],page.items[0]]}))
 assert(!api.isAdminPostDetail({...post,createdAt:'invalid'}))
 assert(!api.isAdminCommentPage({...page,hasNext:true}))
 assert(api.isAdminCommentPage({...page,page:4,items:[],hasPrevious:true}))
})
test("admin reads validate requested identity/page/size and use only private GET",async t=>{
 process.env.NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8082'
 let body=post;const calls=[]
 t.mock.method(globalThis,'fetch',async(u,o)=>{calls.push({u,o});return new Response(JSON.stringify(body),{headers:{'Content-Type':'application/json'}})})
 const signal=new AbortController().signal,q={statuses:['HIDDEN'],keyword:'  본문  내부  ',page:0}
 assert.deepEqual(await api.getAdminPost(1,signal),post)
 body=page;assert.deepEqual(await api.getAdminComments(1,q,signal),page)
 assert.equal(new URL(calls[1].u).searchParams.get('keyword'),'본문  내부')
 assert(calls.every(c=>c.o.method==='GET'&&c.o.credentials==='include'&&c.o.cache==='no-store'))
 for(const bad of [{...page,postId:2},{...page,page:1,hasPrevious:true},{...page,size:10}]){body=bad;await assert.rejects(api.getAdminComments(1,q,signal))}
 body={...post,postId:2};await assert.rejects(api.getAdminPost(1,signal))
})
test("list return carries only safe filter/search/page state",()=>{
 const q={statuses:['HIDDEN'],keyword:'검색  내부',page:2}
 assert.deepEqual(nav.adminListQuery(Object.fromEntries(new URL(nav.adminListHref(q),'https://local').searchParams)),q)
 for(const bad of ['https://evil.test','//evil.test','/admin/posts/2','/mypage',null])assert.equal(nav.adminReturnHref(bad),'/admin/posts')
 assert.equal(nav.adminListQuery({page:'-1',status:'bad'}).page,0)
})

test("multi-status return URL preserves repeated values and accepts legacy single status",()=>{
 const q={statuses:['VISIBLE','HIDDEN'],keyword:'보청기',page:2}
 const href=nav.adminListHref(q)
 assert.equal(nav.adminReturnHref(href),href)
 assert.deepEqual(nav.adminListQuery({statuses:['HIDDEN','VISIBLE','HIDDEN'],keyword:'보청기',page:'2'}),q)
 assert.deepEqual(nav.adminListQuery({status:'HIDDEN'}).statuses,['HIDDEN'])
})
