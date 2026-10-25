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
