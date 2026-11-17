import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const nav=await loadTypeScript('../src/lib/admin-navigation.ts',import.meta.url)
const dates=await loadTypeScript('../src/lib/admin-dates.ts',import.meta.url)
const record=href=>{const p=new URL(href,'https://local').searchParams;return Object.fromEntries([...new Set(p.keys())].map(k=>[k,p.getAll(k).length===1?p.get(k):p.getAll(k)]))}
test('operation URL round trip keeps applied filters/page and separates post namespace',()=>{
 const q={targetType:'COMMENT',action:'RESTORE',reasonType:'NO_VIOLATION',startDate:'0001-01-01',endDate:'9999-12-31',sort:'OLDEST',page:2}
 assert.deepEqual(nav.adminLogQuery(record(nav.adminLogHref(q))),q)
 assert(!nav.adminLogHref(q).includes('size='));assert.equal(nav.adminReturnHref(nav.adminLogHref(q)),nav.adminLogHref(q))
 assert.equal(nav.adminLogQuery({reasonType:'LEGACY'}).reasonType,'LEGACY')
})
test('invalid URL dates, enums, arrays and oversized page normalize safely',()=>{
 for(const parse of [nav.adminListQuery,nav.adminLogQuery]){
  for(const page of ['-1','Infinity','999999999999999999','1.5',['1','2']])assert.equal(parse({page}).page,0)
  const q=parse({startDate:'2026-11-09',endDate:'2026-11-01',sort:'javascript:alert(1)'})
  assert.equal(q.startDate,'');assert.equal(q.endDate,'');assert.equal(q.sort,'LATEST')
  assert.equal(parse({startDate:'2026-02-30'}).startDate,'')
 }
 assert.deepEqual(nav.adminListQuery({statuses:['HIDDEN','evil']}).statuses,['VISIBLE','HIDDEN','DELETED'])
 const log=nav.adminLogQuery({targetType:'evil',action:['HIDE','RESTORE'],reasonType:'UNKNOWN'})
 assert.equal(log.targetType,'ALL');assert.equal(log.action,'ALL');assert.equal(log.reasonType,'')
})
test('applied period summaries describe the actual range without changing API strings',()=>{
 for(const [startDate,endDate,expected] of [['','','전체 기간'],['2026-11-01','','2026.11.01부터'],['','2026-11-07','2026.11.07까지'],['2026-11-01','2026-11-07','2026.11.01 – 2026.11.07']])assert.equal(dates.formatAdminPeriod({startDate,endDate}),expected)
})

test('return paths allow only normalized internal lists',()=>{
 for(const value of [undefined,[], 'https://evil.test/admin/posts', '//evil.test/admin/posts','/admin/posts/4','/admin/operation-logs/','/admin/operation-logs-evil','/admin/posts?x=1#evil','/admin/posts\\evil'])assert.equal(nav.adminReturnHref(value),'/admin/posts')
 assert.equal(nav.adminReturnHref('/admin/operation-logs?sort=bad&page=-1&startDate=2026-02-30'),nav.adminLogHref(nav.adminLogQuery({})))
 assert.equal(nav.adminReturnHref('/admin/posts?statuses=HIDDEN&statuses=VISIBLE&page=2&sort=OLDEST'),nav.adminListHref(nav.adminListQuery({statuses:['HIDDEN','VISIBLE'],page:'2',sort:'OLDEST'})))
})

test('master selection survives normalized return URLs but never enters API filters',async()=>{
 const api=await loadTypeScript('../src/lib/admin-posts.ts',import.meta.url)
 const logs=await loadTypeScript('../src/lib/admin-operation-logs.ts',import.meta.url)
 for(const value of ['0','-1','1.5','9007199254740992',['1','2'],undefined])assert.equal(nav.adminSelectionId(value),null)
 for(const [parse,href,params] of [[nav.adminListQuery,nav.adminListHref,api.adminQueryParams],[nav.adminLogQuery,nav.adminLogHref,logs.logQueryParams]]){
  const q=parse({page:'2',sort:'OLDEST',startDate:'2026-11-01'}),url=href(q,101)
  assert.equal(nav.adminReturnHref(url),url);assert.equal(record(url).selected,'101');assert(!params(parse(record(url))).has('selected'))
 }
})

test("신고 전용 상세 복귀는 안전한 정수 ID의 정확한 내부 경로만 허용한다", () => {
  assert.equal(nav.adminReturnHref("/admin/reports/91"), "/admin/reports/91")
  for (const value of ["/admin/reports/0", "/admin/reports/-1", "/admin/reports/01", "/admin/reports/9007199254740992", "/admin/reports/91?from=evil", "/admin/reports/91#evil", "/admin/reports/91/", "//evil.test/admin/reports/91", "/admin/reports/%39%31", "/admin/reports/91/../92"])
    assert.equal(nav.adminReturnHref(value), "/admin/posts")
})

test("대시보드 모달 복귀 힌트는 정확한 내부 신고 주소만 허용한다", () => {
  const href = "/admin/reports/91?from=dashboard"
  assert.equal(nav.adminReturnHref(href), href)
  assert.equal(nav.dashboardReturnReportId(href), 91)
  for (const value of ["/admin/reports/91", "/admin/reports/91?from=dashboard&next=https://evil.test", "/admin/reports/91?from=dashboard#x", "/admin/reports/91?from=other", "/admin/reports/0?from=dashboard", "/admin/reports/9007199254740992?from=dashboard", "https://evil.test/admin/reports/91?from=dashboard"])
    assert.equal(nav.dashboardReturnReportId(value), null)
})
