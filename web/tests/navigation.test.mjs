import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';
const source = fs.readFileSync(new URL('../src/lib/navigation.ts', import.meta.url), 'utf8').replace('export function', 'function');
const {safeReturnPath} = vm.runInNewContext(ts.transpileModule(source+';({safeReturnPath})', {compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText, {URL});
test('Next login return path preserves local query/hash and rejects external/executable paths', () => {
 assert.equal(safeReturnPath('/community/posts/create?type=test#form'), '/community/posts/create?type=test#form');
 for(const value of ['', 'javascript:alert(1)', '//example.com', '/\\example.com', 'https://example.com']) assert.equal(safeReturnPath(value), '/');
});

test('로그인으로 되돌아가는 경로와 인코딩된 외부·제어문자 경로를 차단', () => {
  for (const value of ['/login', '/login/?from=/login', '/x/../login#x', '/%6cogin', '/%256cogin',
    '/%2fexample.com', '/%252fexample.com', '/%5cexample.com', '/%0alogin', '/\t/evil.example', '/%']) {
    assert.equal(safeReturnPath(value), '/');
  }
  assert.equal(safeReturnPath('/search?q=%ED%85%8C%EC%8A%A4%ED%8A%B8#results'), '/search?q=%ED%85%8C%EC%8A%A4%ED%8A%B8#results');
});
