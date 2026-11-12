import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const a=await loadTypeScript('../src/lib/admin-member-moderation.ts',import.meta.url)
const log={logId:1,actorMemberId:7,targetMemberId:100,action:'SUSPEND',beforeStatus:'ACTIVE',afterStatus:'SUSPENDED',reason:'검토 사유',createdAt:'2026-11-12T01:00:00Z'}
const page={items:[log],page:0,size:20,totalElements:1,totalPages:1,hasNext:false,hasPrevious:false}
test('member reason follows Java whitespace and codepoint boundaries',()=>{
 for(const value of ['',' \n\t','\u3000\u001c'])assert(a.memberReasonError(value))
 for(const value of ['\u00a0','\u2007','\u202f','\ufeff']){assert.equal(a.memberReason(value),value);assert.equal(a.memberReasonError(value),'')}
 assert.equal(a.memberReason('　 첫째\n  둘째　'),'첫째\n  둘째')
 assert.equal(a.memberReasonError('😀'.repeat(500)), '')
 assert(a.memberReasonError('😀'.repeat(501)))
})
test('restriction checks self, admin and latest status',()=>{
 for(const status of ['PENDING','RESTRICTED','WITHDRAWN','DELETED'])assert(a.memberActionRestriction({memberId:100,role:'USER',status},7))
 for(const status of ['ACTIVE','SUSPENDED'])assert.equal(a.memberActionRestriction({memberId:100,role:'USER',status},7),'')
 assert(a.memberActionRestriction({memberId:7,role:'USER',status:'ACTIVE'},7));assert(a.memberActionRestriction({memberId:100,role:'ADMIN',status:'ACTIVE'},7))
})
test('action result checks target, boolean and resulting status',()=>{
 assert(a.isMemberActionResult({memberId:100,status:'SUSPENDED',changed:false},'100','suspend'))
 for(const extra of [{memberId:101},{status:'ACTIVE'},{changed:'false'},{changed:undefined}])assert(!a.isMemberActionResult({memberId:100,status:'SUSPENDED',changed:true,...extra},'100','suspend'))
})
test('history validates saved transitions and target ownership',()=>{
 assert(a.isMemberLogPage(page,'100',0))
 for(const extra of [{targetMemberId:99},{action:'OTHER'},{beforeStatus:'SUSPENDED'},{afterStatus:'ACTIVE'},{reason:null},{createdAt:'bad'},{actorMemberId:0}])assert(!a.isMemberLogPage({...page,items:[{...log,...extra}]},'100',0))
 assert(!a.isMemberLogPage({...page,size:10},'100',0));assert(!a.isMemberLogPage(page,'100',1))
 assert(a.isMemberLogPage({...page,items:[{...log,action:'LIFT_SUSPENSION',beforeStatus:'SUSPENDED',afterStatus:'ACTIVE'}]},'100',0))
})
test('history GET sends credentials and pagination without CSRF or sort',async()=>{
 const original=globalThis.fetch,base=process.env.NEXT_PUBLIC_API_BASE_URL;process.env.NEXT_PUBLIC_API_BASE_URL='http://example.test'
 try{globalThis.fetch=async(url,options)=>{assert.equal(url,'http://example.test/api/v1/admin/members/100/moderation-logs?page=0&size=20');assert.equal(options.credentials,'include');assert.equal(options.headers['X-CSRF-TOKEN'],undefined);return Response.json(page)};assert.deepEqual(await a.getMemberLogs('100',0,new AbortController().signal),page)}finally{globalThis.fetch=original;if(base===undefined)delete process.env.NEXT_PUBLIC_API_BASE_URL;else process.env.NEXT_PUBLIC_API_BASE_URL=base}
})
test('invalid input and outdated owner send no requests',async()=>{
 const original=globalThis.fetch;let calls=0;globalThis.fetch=async()=>{calls++;throw Error('unexpected')}
 try{await assert.rejects(a.moderateMember('100','suspend',' ',()=>true),{code:'INVALID_REASON'});assert.equal(await a.moderateMember('100','suspend','사유',()=>false),null);assert.equal(calls,0)}finally{globalThis.fetch=original}
})
