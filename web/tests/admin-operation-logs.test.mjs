import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const api = await loadTypeScript("../src/lib/admin-operation-logs.ts", import.meta.url)
const item = {logId: 10, actorMemberId: 88,currentTarget:null, targetType: "COMMENT", targetId: 42, action: "HIDE", reasonType:"LEGACY",reasonDetail:null,reason: "원문  공백\n보존", createdAt: "2026-11-03T18:04:05.123456Z"}
const page = {items: [item], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false}
for (const date of ["0001-01-01", "9999-12-31", "2000-02-29", "2024-02-29", "2026-12-31"])
 test(`calendar accepts ${date}`, () => assert(api.validLogDate(date)))
for (const date of ["0000-01-01", "10000-01-01", "1900-02-29", "2026-02-29", "2026-04-31", "2026-00-01", "2026-13-01", "2026-01-00", "2026-1-1", "2026-01-01Z", "", " 2026-01-01"])
 test(`calendar rejects ${JSON.stringify(date)}`, () => assert(!api.validLogDate(date)))
test("filters keep single dates and boundary strings unchanged; ALL action omitted", () => {
 assert.equal(api.logQueryParams(api.DEFAULT_LOG_QUERY).toString(), "targetType=ALL&page=0&size=20&sort=LATEST")
 for (const filters of [{startDate:"0001-01-01"}, {endDate:"9999-12-31"}, {startDate:"2026-11-01",endDate:"2026-11-01",targetType:"POST",action:"RESTORE"}]) {
  const query={...api.DEFAULT_LOG_QUERY,...filters},params=api.logQueryParams(query)
  for (const [key,value] of Object.entries(filters)) assert.equal(params.get(key),value)
 }
 assert(api.logFilterError({...api.DEFAULT_LOG_QUERY,startDate:"2026-11-02",endDate:"2026-11-01"}))
 assert(api.logFilterError({...api.DEFAULT_LOG_QUERY,action:"INVALID"}))
 for (const value of [-1,1.5,"1",107374183]) assert.throws(()=>api.logQueryParams({...api.DEFAULT_LOG_QUERY,page:value}))
})
test("strict DTO accepts both targets/actions, empty and out of range pages", () => {
 for(const targetType of ["POST","COMMENT"]) for(const action of ["HIDE","RESTORE"]) assert(api.isOperationLogPage({...page,items:[{...item,targetType,action}]}))
 assert(api.isOperationLogPage({...page,items:[],totalElements:0,totalPages:0}))
 assert(api.isOperationLogPage({...page,items:[],page:5,hasPrevious:true}))
 assert.equal(api.isOperationLogPage({...page,items:[item,item]}),false)
})
test("missing fields, enums, malformed dates, unsafe ids and page metadata are rejected", () => {
 for(const key of Object.keys(item)){const bad={...item};delete bad[key];assert(!api.isOperationLogPage({...page,items:[bad]}),key)}
 for(const key of ["logId","actorMemberId","targetId"]) for(const value of [0,-1,1.2,"7",null,Number.MAX_SAFE_INTEGER+1]) assert(!api.isOperationLogPage({...page,items:[{...item,[key]:value}]}))
 for(const bad of [{targetType:"ALL"},{action:"ALL"},{createdAt:"2026-02-30T00:00:00Z"},{reasonType:"LEGACY",reasonDetail:null,reason:null}]) assert(!api.isOperationLogPage({...page,items:[{...item,...bad}]}))
 for(const bad of [{hasNext:true},{hasPrevious:true},{page:"0"},{size:51},{totalPages:2},{totalElements:-1},{items:null}]) assert(!api.isOperationLogPage({...page,...bad}))
})
test("private GET preserves supplied dates, requests no CSRF, validates requested page/size", async t => {
 process.env.NEXT_PUBLIC_API_BASE_URL="http://127.0.0.1:8082"
 const calls=[];let body=page
 t.mock.method(globalThis,"fetch",async(u,o)=>{calls.push({u,o});return new Response(JSON.stringify(body),{headers:{"Content-Type":"application/json"}})})
 assert.deepEqual(await api.getOperationLogs({...api.DEFAULT_LOG_QUERY,startDate:"0001-01-01",endDate:"9999-12-31"},new AbortController().signal),page)
 assert.equal(calls.length,1);const {u,o}=calls[0];const url=new URL(u)
 assert.equal(url.pathname,"/api/v1/admin/operation-logs");assert.equal(url.searchParams.get("endDate"),"9999-12-31")
 assert(!url.searchParams.has("action"));assert.equal(o.credentials,"include");assert.equal(o.cache,"no-store");assert.equal(o.method,"GET")
 body={...page,page:1,items:[],hasPrevious:true};await assert.rejects(api.getOperationLogs(api.DEFAULT_LOG_QUERY,new AbortController().signal))
 body={...page,size:10};await assert.rejects(api.getOperationLogs(api.DEFAULT_LOG_QUERY,new AbortController().signal))
})
test("401, both 403 codes, 400, server and malformed responses stay errors without retries", async t => {
 process.env.NEXT_PUBLIC_API_BASE_URL="http://127.0.0.1:8082"
 for(const [status,code] of [[401,"AUTHENTICATION_REQUIRED"],[403,"ACCESS_DENIED"],[403,"ADMIN_REQUIRED"],[400,"INVALID_DATE_RANGE"],[500,"INTERNAL_SERVER_ERROR"],[200,"broken"]]) {
  let calls=0;t.mock.method(globalThis,"fetch",async()=>{calls++;return new Response(JSON.stringify({code}),{status,headers:{"Content-Type":"application/json"}})})
  await assert.rejects(api.getOperationLogs(api.DEFAULT_LOG_QUERY,new AbortController().signal),e=>e.status===(status===200?0:status));assert.equal(calls,1)
 }
})

test('reason and server sort are independent, explicit null required',()=>{
 const params=api.logQueryParams({...api.DEFAULT_LOG_QUERY,action:'RESTORE',reasonType:'SPAM_ADVERTISING',sort:'OLDEST',page:2})
 assert.equal(params.get('reasonType'),'SPAM_ADVERTISING');assert.equal(params.get('action'),'RESTORE');assert.equal(params.get('sort'),'OLDEST');assert.equal(params.get('page'),'2')
 assert(!api.logQueryParams(api.DEFAULT_LOG_QUERY).has('reasonType'))
 assert.equal(api.logQueryParams({...api.DEFAULT_LOG_QUERY,reasonType:'LEGACY'}).get('reasonType'),'LEGACY')
 for(const reasonType of ['ALL','unknown',null])assert(!api.isOperationLogPage({...page,items:[{...item,reasonType}]}))
 for(const reasonDetail of [undefined,0,{}])assert(!api.isOperationLogPage({...page,items:[{...item,reasonDetail}]}))
 assert(api.isOperationLogPage({...page,items:[{...item,reasonType:'OTHER',reasonDetail:'😀'}]}))
})


test("currentTarget is required nullable, author is distinct, comment title is explicit null", () => {
 const target={author:{memberId:43,nickname:"탈퇴한 회원",withdrawn:true},title:null,contentPreview:"현재 댓글 😀"}
 assert(api.isOperationLogPage({...page,items:[{...item,currentTarget:target}]}))
 assert(api.isOperationLogPage({...page,items:[{...item,targetType:"POST",currentTarget:{...target,title:"현재 제목"}}]}))
 for(const currentTarget of [undefined,{}, {...target,title:undefined},{...target,title:"댓글에 제목"},{...target,contentPreview:null},{...target,author:{...target.author,memberId:0}},{...target,author:{...target.author,withdrawn:undefined}}])
  assert(!api.isOperationLogPage({...page,items:[{...item,currentTarget}]}))
 for(const key of ["memberId","nickname","withdrawn"]){const author={...target.author};delete author[key];assert(!api.isOperationLogPage({...page,items:[{...item,currentTarget:{...target,author}}]}))}
 assert(!api.isOperationLogPage({...page,items:[{...item,targetType:"POST",currentTarget:target}]}))
})
