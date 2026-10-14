import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const { readingScrollDelta } = await loadTypeScript('../src/lib/reading-focus.ts', import.meta.url);
test('이미 읽을 수 있는 다음 제목과 짧은 문맥은 이동하지 않는다',()=>{
 assert.equal(readingScrollDelta(200,50,844),0);
 assert.equal(readingScrollDelta(640,40,844),0);
});
test('화면 아래 내용은 필요한 만큼만 올리고 제목을 맨 위로 맞추지 않는다',()=>{
 assert.equal(readingScrollDelta(760,50,844),120);
 assert.equal(760-readingScrollDelta(760,50,844),640);
});
test('위로 돌아가거나 작은 화면에서 읽을 공간을 확보한다',()=>{
 assert.equal(readingScrollDelta(-100,50,844),-124);
 assert.equal(readingScrollDelta(100,50,200),76);
});
