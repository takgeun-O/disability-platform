import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import {atomicWrite,acquireSeed,beginComment,recordComment,recordSoftDelete,properties,ids} from './helpers/seed-ownership.mjs'
async function setup(fn){const directory=await fs.mkdtemp(path.join(os.tmpdir(),'iyum-ownership-'));const manifest={key:'feed-seed-v1',members:'701,702,703',posts:'901,902',comments:'111,112'};await atomicWrite(path.join(directory,'manifest.properties'),Object.entries(manifest).map(([k,v])=>`${k}=${v}`).join('\n')+'\n');try{await fn({directory,manifest,members:[701,702,703],posts:[901,902]})}finally{await fs.rm(directory,{recursive:true,force:true})}}
test('seed ownership retains prior IDs, accepts changed seed IDs, soft-delete never removes ownership',()=>setup(async seed=>{const release=await acquireSeed(seed.directory);try{const r=await beginComment(seed,901,701,'test');assert(r.content.startsWith('[개발 테스트]'));await recordComment(seed,r,999);await recordSoftDelete(r);assert.deepEqual(ids(properties(await fs.readFile(path.join(seed.directory,'manifest.properties'),'utf8')).comments),[111,112,999]);assert.equal(properties(await fs.readFile(r.file,'utf8')).softDeleted,'true');await recordComment(seed,r,999);assert.deepEqual(ids(properties(await fs.readFile(path.join(seed.directory,'manifest.properties'),'utf8')).comments),[111,112,999])}finally{await release()}}))
test('seed ownership lock rejects overlapping process',()=>setup(async seed=>{const release=await acquireSeed(seed.directory);await assert.rejects(acquireSeed(seed.directory),{code:'EEXIST'});await release();await(await acquireSeed(seed.directory))()}))
test('manifest failure leaves durable ID and exact intent; original remains intact',()=>setup(async seed=>{const r=await beginComment(seed,901,701,'recover');const file=path.join(seed.directory,'manifest.properties');await fs.rename(file,file+'.saved');await fs.mkdir(file);await assert.rejects(recordComment(seed,r,1001));const journal=properties(await fs.readFile(r.file,'utf8'));assert.equal(journal.commentId,'1001');assert.equal(Buffer.from(journal.contentBase64,'base64').toString(),r.content);assert.equal(properties(await fs.readFile(file+'.saved','utf8')).comments,'111,112')}))
test('interrupted request has durable exact marker before any response ID',()=>setup(async seed=>{const r=await beginComment(seed,901,701,'interrupted');const j=properties(await fs.readFile(r.file,'utf8'));assert(!j.commentId);assert.equal(Number(j.postId),901);assert.equal(Number(j.memberId),701);assert(Buffer.from(j.contentBase64,'base64').toString().includes(`[run:${j.runId}]`))}))
test('unowned target and malformed success ID cannot enter manifest',()=>setup(async seed=>{await assert.rejects(beginComment(seed,14,701,'wrong post'));await assert.rejects(beginComment(seed,901,34,'wrong member'));const r=await beginComment(seed,901,701,'valid');for(const n of [0,-1,NaN,1.5,Number.MAX_SAFE_INTEGER+1])await assert.rejects(recordComment(seed,r,n));assert.equal(properties(await fs.readFile(path.join(seed.directory,'manifest.properties'),'utf8')).comments,'111,112')}))
test('atomic replacement failure preserves existing destination and IDs',()=>setup(async seed=>{await assert.rejects(atomicWrite(path.join(seed.directory,'missing-dir','manifest.properties'),'x'));assert.equal(properties(await fs.readFile(path.join(seed.directory,'manifest.properties'),'utf8')).comments,'111,112')}))

const {withSeedResources}=await import('./helpers/seed-ownership.mjs')
for(const failure of ['launch','context','page','save','close'])test(`resource failure ${failure} always releases lock`,()=>setup(async seed=>{
 const original=new Error(failure),closed=[]
 const context={async newPage(){if(failure==='page')throw original;return{}},async close(){closed.push('context')}}
 const browser={async newContext(){if(failure==='context')throw original;return context},async close(){closed.push('browser');if(failure==='close')throw original}}
 const chromium={async launch(){if(failure==='launch')throw original;return browser}}
 await assert.rejects(withSeedResources(async r=>{await r.launch(chromium,{});const c=await r.context({});await c.newPage();await r.finish(async()=>{if(failure==='save')throw original})},{directory:seed.directory}),e=>e===original||e.errors?.includes(original))
 if(failure!=='launch')assert(closed.includes('browser'))
 const release=await acquireSeed(seed.directory);await release()
}))
test('first failure survives result save and browser close failures',()=>setup(async seed=>{
 const first=new Error('page'),later=new Error('cleanup')
 await assert.rejects(withSeedResources(async r=>{await r.launch({launch:async()=>({newContext:async()=>({newPage:async()=>{throw first},close:async()=>{throw later}}),close:async()=>{throw later}})},{});const c=await r.context({});try{await c.newPage()}finally{await r.finish(async()=>{throw later})}},{directory:seed.directory}),e=>e===first&&e.cleanupErrors.length===3)
 await(await acquireSeed(seed.directory))()
}))
test('owner metadata write failure removes only newly acquired lock',()=>setup(async seed=>{
 const original=new Error('owner write')
 await assert.rejects(acquireSeed(seed.directory,async()=>{throw original}),e=>e===original)
 const release=await acquireSeed(seed.directory)
 await assert.rejects(acquireSeed(seed.directory,async()=>{throw original}),{code:'EEXIST'})
 assert((await fs.stat(path.join(seed.directory,'ownership.lock'))).isDirectory())
 await release();await(await acquireSeed(seed.directory))()
}))
