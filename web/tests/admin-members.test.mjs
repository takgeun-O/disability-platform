import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const a = await loadTypeScript("../src/lib/admin-members.ts", import.meta.url)
const q = a.defaultMemberQuery
const item = {memberId:42,nickname:"탈퇴한 회원",email:null,status:"DELETED",role:"USER",createdAt:"2026-11-10T01:00:00Z"}
test("member keyword follows Java strip, codepoints and exact string Long range",()=>{
 assert.equal(a.memberKeyword("NICKNAME","　 가　"),"가")
 assert.equal(a.memberKeyword("NICKNAME","\u00a0가\u00a0"),"\u00a0가\u00a0")
 assert.equal(a.memberKeyword("NICKNAME","😀".repeat(100)),"😀".repeat(100))
 assert.throws(()=>a.memberKeyword("NICKNAME","😀".repeat(101)))
 assert.equal(a.memberKeyword("EMAIL","  A@EXAMPLE.TEST  "),"a@example.test")
 assert.equal(a.memberKeyword("EMAIL","member"),"member")
 assert.throws(()=>a.memberKeyword("EMAIL","a".repeat(255)))
 assert.equal(a.memberKeyword("MEMBER_ID","9223372036854775807"),"9223372036854775807")
 assert.equal(a.memberKeyword("MEMBER_ID","00104"),"104")
 for(const v of ["0","-1","+1","1.2","9223372036854775808","00000000000000000001"])assert.throws(()=>a.memberKeyword("MEMBER_ID",v))
})
test("member query omits empty search and ALL filters; selection is UI only",()=>{
 assert.equal(a.memberParams(q).toString(),"sort=LATEST&page=0&size=20")
 const p=a.memberParams({...q,searchType:"MEMBER_ID",keyword:"9223372036854775807",status:"ACTIVE",role:"ADMIN",sort:"OLDEST",page:2})
 assert.equal(p.get("keyword"),"9223372036854775807");assert.equal(p.get("status"),"ACTIVE");assert.equal(p.get("role"),"ADMIN");assert.equal(p.get("page"),"2")
 const href=a.memberHref({...q,keyword:"소리",page:2},"42")
 assert.equal(a.memberQuery(new URL(href,"http://local").searchParams).page,2)
 assert.equal(a.memberSelection("https://evil"),null)
 assert.equal(a.memberQuery(new URLSearchParams("page=999999999999&role=ALL&status=ALL&sort=bad")).page,0)
})
test("member response requires nullable fields, enums, timestamps and server pagination",()=>{
 assert(a.isAdminMember(item));assert(a.isAdminMember({...item,status:"WITHDRAWN",email:"former@example.test"}))
 for(const bad of [{...item,email:undefined},{...item,status:"UNKNOWN"},{...item,createdAt:"bad"},{...item,memberId:9007199254740992}])assert(!a.isAdminMember(bad))
 assert(a.isMemberDetail({...item,updatedAt:item.createdAt,withdrawnAt:null}))
 assert(!a.isMemberDetail({...item,updatedAt:item.createdAt}))
 const page={items:[item],page:0,size:20,totalElements:1,totalPages:1,hasNext:false,hasPrevious:false}
 assert(a.isMemberPage(page));assert(!a.isMemberPage({...page,totalPages:2}))
})

test("withdrawal badges distinguish reserved cleanup from completed withdrawal",()=>{
 assert.equal(a.MEMBER_STATUSES.WITHDRAWN,"탈퇴·정리 대기")
 assert.equal(a.MEMBER_STATUSES.DELETED,"탈퇴 완료")
 for(const status of ["WITHDRAWN","DELETED"])assert.equal(a.memberParams({...q,status}).get("status"),status)
})
