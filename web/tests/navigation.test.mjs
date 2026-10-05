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
