import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const { createNavigationRequestScope } = await loadTypeScript('../src/lib/navigation-request.ts', import.meta.url);
test('확정된 탐색은 마운트가 유지되어도 요청을 즉시 무효화한다', () => {
  let version = 1; const scope = createNavigationRequestScope(() => version); scope.activate();
  const current = scope.begin(); assert(current()); version++; assert(!current()); assert(!scope.isActive());
});
test('확인창 취소처럼 탐색 버전이 유지되면 요청이 유효하다', () => {
  const scope = createNavigationRequestScope(() => 1); scope.activate(); const current = scope.begin(); assert(current()); assert(scope.isActive());
});
test('새 요청·언마운트·재진입은 이전 요청을 다시 유효하게 만들지 않는다', () => {
  let version = 1; const scope = createNavigationRequestScope(() => version); scope.activate();
  const first = scope.begin(); const second = scope.begin(); assert(!first()); assert(second());
  scope.deactivate(); assert(!second()); version++; scope.activate(); assert(!second()); assert(scope.begin()());
  const nextScreen = createNavigationRequestScope(() => version); nextScreen.activate(); assert(nextScreen.begin()()); assert(!first());
});
