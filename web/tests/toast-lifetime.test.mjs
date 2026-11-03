import test from 'node:test'
import assert from 'node:assert/strict'
import { loadTypeScript } from './helpers/load-typescript.mjs'
const { startToastLifetime } = await loadTypeScript('../src/lib/toast-lifetime.ts', import.meta.url)
test('success remains 5 seconds then fades for 250ms', t => {
  t.mock.timers.enable({apis:['setTimeout']})
  const events=[]; const dispose=startToastLifetime(()=>events.push('leave'),()=>events.push('remove'))
  t.mock.timers.tick(4999);assert.deepEqual(events,[])
  t.mock.timers.tick(1);assert.deepEqual(events,['leave'])
  t.mock.timers.tick(249);assert.deepEqual(events,['leave'])
  t.mock.timers.tick(1);assert.deepEqual(events,['leave','remove']);dispose()
})
test('replacement during exit and Strict Mode cleanup cannot remove the next event', t => {
  t.mock.timers.enable({apis:['setTimeout']});const events=[]
  const first=startToastLifetime(()=>events.push('old-leave'),()=>events.push('old-remove'))
  t.mock.timers.tick(5000);first()
  const replay=startToastLifetime(()=>events.push('replay'),()=>events.push('replay-remove'));replay()
  const next=startToastLifetime(()=>events.push('new-leave'),()=>events.push('new-remove'))
  t.mock.timers.tick(4999);assert.deepEqual(events,['old-leave'])
  t.mock.timers.tick(1);t.mock.timers.tick(250);assert.deepEqual(events,['old-leave','new-leave','new-remove']);next()
})
test('unmount cancels visible and exit timers',t=>{
  t.mock.timers.enable({apis:['setTimeout']});const events=[]
  const dispose=startToastLifetime(()=>events.push('leave'),()=>events.push('remove'));dispose()
  t.mock.timers.tick(10000);assert.deepEqual(events,[])
})

const { startPausableToastLifetime } = await loadTypeScript('../src/lib/toast-lifetime.ts', import.meta.url)
test('auth toast pauses remaining budget and fades for 200ms',t=>{
 t.mock.timers.enable({apis:['setTimeout','Date']});const events=[];const life=startPausableToastLifetime(v=>events.push(v),()=>events.push('remove'),Date.now);
 t.mock.timers.tick(3000);life.pause();t.mock.timers.tick(10000);assert.deepEqual(events,[]);life.resume();t.mock.timers.tick(1999);assert.deepEqual(events,[]);t.mock.timers.tick(1);assert.deepEqual(events,[true]);t.mock.timers.tick(200);assert.deepEqual(events,[true,'remove']);life.dispose();
});
test('focus during fade cancels removal until resume; replacement cancels queued old removal',t=>{
 t.mock.timers.enable({apis:['setTimeout','Date']});const events=[];const old=startPausableToastLifetime(v=>events.push(v),()=>events.push('old'),Date.now);
 t.mock.timers.tick(5000);old.pause();t.mock.timers.tick(10000);assert.deepEqual(events,[true,false]);old.resume();t.mock.timers.tick(0);old.dispose();const next=startPausableToastLifetime(()=>{},()=>events.push('new'),Date.now);t.mock.timers.tick(200);assert(!events.includes('old'));t.mock.timers.tick(4800);t.mock.timers.tick(200);assert(events.includes('new'));next.dispose();
});
test('auth lifetime unmount/Strict Mode cleanup never removes replacement',t=>{
 t.mock.timers.enable({apis:['setTimeout']});let removed=0;const first=startPausableToastLifetime(()=>{},()=>removed++);first.dispose();const replay=startPausableToastLifetime(()=>{},()=>removed++);replay.dispose();t.mock.timers.tick(10000);assert.equal(removed,0);
});
