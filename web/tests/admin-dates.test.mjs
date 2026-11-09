import test from 'node:test'
import assert from 'node:assert/strict'
import {loadTypeScript} from './helpers/load-typescript.mjs'
const dates=await loadTypeScript('../src/lib/admin-dates.ts',import.meta.url)
const api=await loadTypeScript('../src/lib/admin-posts.ts',import.meta.url)
const nav=await loadTypeScript('../src/lib/admin-navigation.ts',import.meta.url)
test('Gregorian ranges preserve raw dates including year boundaries and leap days',()=>{
 for(const value of ['0001-01-01','9999-12-31','2000-02-29','2024-02-29'])assert(dates.validAdminDate(value),value)
 for(const value of ['0000-01-01','10000-01-01','1900-02-29','2026-02-29','2026-04-31','2026-1-01','2026-01-01T00:00:00Z'])assert(!dates.validAdminDate(value),value)
 for(const range of [{startDate:'0001-01-01',endDate:''},{startDate:'',endDate:'9999-12-31'},{startDate:'2026-11-01',endDate:'2026-11-01'}])assert.equal(dates.dateRangeError(range),'')
 assert(dates.dateRangeError({startDate:'2026-11-02',endDate:'2026-11-01'}))
 assert.equal(dates.shiftCalendarDate('2024-03-01',-1),'2024-02-29');assert.equal(dates.shiftCalendarDate('2026-01-01',-6),'2025-12-26')
 assert.equal(dates.calendarWeekday(2026,11,8),0);assert.equal(dates.calendarWeekday(1,1,1),1)
})
test('Seoul quick ranges include today, independent of process timezone',()=>{
 const previous=process.env.TZ
 try{for(const tz of ['Asia/Seoul','America/Los_Angeles','Pacific/Honolulu']){
  process.env.TZ=tz;const now=new Date('2026-11-07T15:30:00Z')
  assert.equal(dates.seoulToday(now),'2026-11-08')
  for(const [days,start] of [[1,'2026-11-08'],[7,'2026-11-02'],[30,'2026-10-10']])assert.deepEqual(dates.quickDateRange(days,now),{startDate:start,endDate:'2026-11-08'})
  assert.deepEqual(dates.quickDateRange(0,now),{startDate:'',endDate:''})
 }}finally{if(previous===undefined)delete process.env.TZ;else process.env.TZ=previous}
})
test('post dates/sort request uses repeated statuses and raw dates, rejects invalid input before fetch',async t=>{
 process.env.NEXT_PUBLIC_API_BASE_URL='http://127.0.0.1:8082'
 const calls=[];t.mock.method(globalThis,'fetch',async(u,o)=>{calls.push({u,o});return Response.json({items:[],page:0,size:20,totalElements:0,totalPages:0,hasNext:false,hasPrevious:false})})
 const base={statuses:['HIDDEN','VISIBLE'],keyword:' 제목 ',page:0,sort:'OLDEST'}
 for(const range of [{startDate:'0001-01-01',endDate:''},{startDate:'',endDate:'9999-12-31'},{startDate:'2026-11-08',endDate:'2026-11-08'}]){
  await api.getAdminPosts({...base,...range},new AbortController().signal);const params=new URL(calls.at(-1).u).searchParams
  assert.deepEqual(params.getAll('statuses'),['VISIBLE','HIDDEN']);assert(!params.has('status'));assert.equal(params.get('sort'),'OLDEST')
  for(const key of ['startDate','endDate'])assert.equal(params.get(key),range[key]||null)
  assert.equal(calls.at(-1).o.cache,'no-store');assert.equal(calls.at(-1).o.credentials,'include')
 }
 const count=calls.length
 await assert.rejects(api.getAdminPosts({...base,startDate:'2026-11-09',endDate:'2026-11-08'},new AbortController().signal));assert.equal(calls.length,count)
})
test('list/detail return URL restores dates and sort without leaking unsupported values',()=>{
 const q={statuses:['HIDDEN'],keyword:'제목',page:2,startDate:'2026-11-01',endDate:'2026-11-08',sort:'OLDEST'}
 const href=nav.adminListHref(q);assert.equal(nav.adminReturnHref(href),href)
 assert.deepEqual(nav.adminListQuery(Object.fromEntries(new URL(href,'https://local').searchParams)),q)
 const bad=nav.adminListQuery({startDate:'2026-11-09',endDate:'2026-11-08',sort:'RANDOM'})
 assert.equal(bad.startDate,'');assert.equal(bad.endDate,'');assert.equal(bad.sort,'LATEST')
})
