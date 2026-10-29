import test from 'node:test'
import assert from 'node:assert/strict'
import { loadTypeScript } from './helpers/load-typescript.mjs'
const { writtenTime, writtenInstant } = await loadTypeScript('../src/lib/written-time.ts', import.meta.url)
const { createWrittenClock } = await loadTypeScript('../src/lib/written-clock.ts', import.meta.url)
const value='2026-10-27T04:23:41.115818Z', start=writtenInstant(value)
for(const [boundary,expected] of [[60_000,['방금 전','1분 전','1분 전']],[3_600_000,['59분 전','1시간 전','1시간 전']],[86_400_000,['23시간 전','1일 전','1일 전']],[604_800_000,['6일 전','2026.10.27.','2026.10.27.']]]) {
 for(const [i,offset] of [-1,0,1].entries()) test(`elapsed ${boundary}ms ${offset}: ${expected[i]}`,()=>assert.equal(writtenTime(value,start+boundary+offset).label,expected[i]))
}
test('floor by elapsed duration, not Seoul midnight; exact time and original precision',()=>{
 assert.equal(writtenTime(value,start+179_999).label,'2분 전')
 assert.equal(writtenTime(value,start+7_199_999).label,'1시간 전')
 assert.equal(writtenTime('2026-10-27T14:59:59Z',Date.parse('2026-10-27T15:00:01Z')).label,'방금 전')
 assert.equal(writtenTime(value).exact,'2026.10.27. 13:23:41 (한국 시간)')
 assert.equal(value,'2026-10-27T04:23:41.115818Z')
 assert.equal(writtenTime(value,Date.parse('2026-10-27T04:24:41.115Z')).label,'방금 전')
})
for(const [raw,date] of [['2026-10-31T15:00:00Z','2026.11.01.'],['2026-12-31T15:00:00Z','2027.01.01.'],['2024-02-28T15:00:00Z','2024.02.29.'],['2024-02-29T15:00:00Z','2024.03.01.'],['2026-10-27T00:00:00+09:00','2026.10.27.']])test('Seoul absolute '+raw,()=>assert.equal(writtenTime(raw).label,date))
test('future within one minute clamps; larger future gives exact date/time',()=>{
 for(const diff of [1,59_999,60_000])assert.equal(writtenTime(value,start-diff).label,'방금 전')
 assert.equal(writtenTime(value,start-60_001).label,'2026.10.27. 13:23:41 (한국 시간)')
 assert.equal(writtenTime(value,NaN).label,'2026.10.27.')
})
test('invalid date/zone and non-string inputs have safe fallback',()=>{
 for(const raw of ['',null,undefined,7,'bad','2026-02-30T00:00:00Z','2023-02-29T00:00:00Z','2026-10-27','2026-10-27T00:00:00','2026-10-27T25:00:00Z','2026-10-27T00:00:00+25:00']){
  assert.deepEqual(writtenTime(raw,Date.now()),{label:'작성일 확인 불가',exact:'작성 시각을 확인할 수 없습니다.',valid:false})
 }
})
test('Seoul date independent of device timezone',()=>{
 const old=process.env.TZ
 try { for(const zone of ['UTC','America/Los_Angeles','Pacific/Honolulu']){process.env.TZ=zone;assert.equal(writtenTime('2026-12-31T15:01:00Z').exact,'2027.01.01. 00:01:00 (한국 시간)')} }
 finally {if(old===undefined)delete process.env.TZ;else process.env.TZ=old}
})
test('one clock timer; visibility resume; last unsubscribe and Strict Mode cleanup',()=>{
 let now=100,visible=true,timers=new Set(),resume,events=0,ticks=0
 const clock=createWrittenClock({now:()=>now,visible:()=>visible,everyMinute:fn=>{timers.add(fn);return()=>timers.delete(fn)},onResume:fn=>{resume=fn;events++;return()=>{events--;resume=null}}})
 assert.equal(clock.getServerSnapshot(),null);assert.equal(clock.getSnapshot(),null)
 const a=clock.subscribe(()=>ticks++), b=clock.subscribe(()=>ticks++)
 assert.equal(timers.size,1);assert.equal(events,1);assert.equal(clock.getSnapshot(),100)
 now=60_100;for(const fn of timers)fn();assert.equal(clock.getSnapshot(),now)
 visible=false;resume();assert.equal(timers.size,0)
 now=900_100;visible=true;resume();assert.equal(clock.getSnapshot(),now);assert.equal(timers.size,1)
 a();assert.equal(timers.size,1);b();assert.equal(timers.size,0);assert.equal(events,0);assert.equal(clock.getSnapshot(),null)
 const c=clock.subscribe(()=>ticks++);assert.equal(timers.size,1);c();assert.equal(timers.size,0);assert.equal(events,0);assert(ticks>0)
})
