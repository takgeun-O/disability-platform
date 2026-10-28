import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import {createRequire} from 'node:module'
import ts from 'typescript'
function setup(){
 const exports={}
 const code=ts.transpileModule(fs.readFileSync(new URL('../src/lib/community-mutations.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
 vm.runInNewContext(code,{exports,require:createRequire(import.meta.url)})
 return exports
}
test('comment invalidation is post-local, separate from detail and private counts',()=>{
 const m=setup();m.postCommentsChanged('A');assert.equal(m.getPostVersion('comments:A'),1)
 for(const key of ['comments:B','A','B','list','member-comments'])assert.equal(m.getPostVersion(key),0)
 m.memberCommentsChanged();assert.equal(m.getPostVersion('member-comments'),1);assert.equal(m.getPostVersion('comments:B'),0)
})
test('parent mutation affects its own comments; identity invalidates all',()=>{
 const m=setup();m.postChanged('A')
 for(const key of ['A','comments:A','list','member-comments'])assert.equal(m.getPostVersion(key),1)
 assert.equal(m.getPostVersion('comments:B'),0)
 m.publicIdentityChanged();assert.equal(m.getPostVersion('comments:A'),2);assert.equal(m.getPostVersion('comments:B'),1)
})
