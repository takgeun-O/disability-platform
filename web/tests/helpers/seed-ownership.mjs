// Private local fixture ownership. No credentials or journal contents should be shared.
import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import {randomUUID} from 'node:crypto'
export const MARK='[개발 테스트]'
export const seedDirectory=()=>process.env.IYUM_SEED_DIR??path.join(os.homedir(),'.local/share/iyum/feed-seed-v1')
export function properties(text){return Object.fromEntries(text.split(/\r?\n/).filter(s=>s&&!s.startsWith('#')).map(s=>{const at=s.indexOf('=');if(at<1)throw Error('Invalid properties');return[s.slice(0,at),s.slice(at+1).replace(/\\(.)/g,'$1')]}))}
export function ids(value){const result=(value??'').split(',').filter(Boolean).map(Number);if(result.some(n=>!Number.isSafeInteger(n)||n<1)||new Set(result).size!==result.length)throw Error('Invalid ownership IDs');return result}
export async function atomicWrite(file,text){
 const tmp=file+'.'+randomUUID()+'.tmp';const handle=await fs.open(tmp,'wx',0o600)
 try{await handle.writeFile(text);await handle.sync()}finally{await handle.close()}
 try{await fs.rename(tmp,file);const directory=await fs.open(path.dirname(file),'r');try{await directory.sync()}finally{await directory.close()}}catch(e){await fs.rm(tmp,{force:true});throw e}
}
export async function acquireSeed(directory=seedDirectory(),writeOwner=atomicWrite){
 // Same exclusive directory is used by CommunitySeed create/status/recover/dry-run/cleanup.
 const lock=path.join(directory,'ownership.lock');await fs.mkdir(lock)
 try{await writeOwner(path.join(lock,'owner.json'),JSON.stringify({pid:process.pid,startedAt:new Date().toISOString()}))}catch(error){try{await fs.rm(lock,{recursive:true})}catch(cleanup){error.cleanupErrors=[cleanup]}throw error}
 return async()=>{await fs.rm(lock,{recursive:true})}
}
export async function fixture(api,directory=seedDirectory()){
 try{await fs.access(path.join(directory,'cleanup-pending.properties'))}catch(error){if(error.code!=='ENOENT')throw error;return verifiedFixture(api,directory)}
 throw Error('Pending cleanup: verify and resume Java cleanup before browser tests')
}
async function verifiedFixture(api,directory){
 if(new URL(api).hostname!=='127.0.0.1')throw Error('Local API required')
 const manifest=properties(await fs.readFile(path.join(directory,'manifest.properties'),'utf8'))
 if(manifest.key!=='feed-seed-v1')throw Error('Unexpected fixture')
 const members=ids(manifest.members),posts=ids(manifest.posts),comments=ids(manifest.comments)
 if(members.length!==3||posts.length!==85)throw Error('Unexpected seed ownership')
 const accounts=JSON.parse(await fs.readFile(path.join(directory,'accounts.json'),'utf8'))
 for(let i=0;i<3;i++)if(accounts[i]?.email!==`feed-seed-${i+1}@iyum.test`||!accounts[i].password)throw Error('Invalid fixture account')
 const cases=[]
 for(let i=0;i<5;i++){
  const id=Number(manifest[`case.${i}`]??posts[i]);if(!posts.includes(id))throw Error('Unowned case')
  const r=await fetch(`${api}/api/v1/community/posts/${id}`);if(r.status!==200)throw Error('Seed post unavailable')
  const post=await r.json()
  if(post.id!==id||post.author.memberId!==members[i%3]||!post.title.startsWith(`${MARK} ${String(i+1).padStart(2,'0')} `)||post.topic!=='HEARING_AID')throw Error('Seed post ownership mismatch')
  let cursor=null,items=[]
  do{const u=new URL(`${api}/api/v1/community/posts/${id}/comments`);u.searchParams.set('size','20');if(cursor){u.searchParams.set('afterCreatedAt',cursor.createdAt);u.searchParams.set('afterId',cursor.commentId)}const cr=await fetch(u);if(cr.status!==200)throw Error('Seed comments unavailable');const page=await cr.json();items.push(...page.items);if(items.length>1000)throw Error('Fixture grew unexpectedly');cursor=page.nextCursor}while(cursor)
  cases.push({id,count:items.length,seedCount:items.filter(c=>comments.includes(c.commentId)&&members.includes(c.author.memberId)&&c.content.startsWith(MARK)).length})
 }
 return{manifest,members,posts,comments,accounts,cases,directory}
}
export async function beginComment(fixture,postId,memberId,label){
 if(!fixture.posts.includes(postId)||!fixture.members.includes(memberId))throw Error('Unowned write target')
 const runId=randomUUID(),content=`${MARK} ${label} [run:${runId}]`
 const dir=path.join(fixture.directory,'comment-journal');await fs.mkdir(dir,{recursive:true,mode:0o700})
 const file=path.join(dir,runId+'.properties')
 const entry={runId,postId,memberId,contentBase64:Buffer.from(content).toString('base64')}
 const persist=()=>atomicWrite(file,Object.entries(entry).map(([k,v])=>`${k}=${v}`).join('\n')+'\n')
 await persist() // durable intent before POST: exact content finds a committed row after response/process loss
 return{content,entry,file,persist}
}
export async function recordComment(fixture,receipt,id){
 if(!Number.isSafeInteger(id)||id<1)throw Error('Invalid created comment ID')
 receipt.entry.commentId=id;await receipt.persist() // durable ID even if manifest update fails
 const file=path.join(fixture.directory,'manifest.properties'),text=await fs.readFile(file,'utf8'),current=properties(text)
 if(current.members!==fixture.manifest.members||current.posts!==fixture.manifest.posts)throw Error('Fixture replaced during test')
 if(!/^comments=.*$/m.test(text))throw Error('Ownership list missing; recover from journal')
 const owned=ids(current.comments);if(!owned.includes(id))owned.push(id)
 await atomicWrite(file,text.replace(/^comments=.*$/m,`comments=${owned.join(',')}`))
 receipt.entry.recorded=true;await receipt.persist()
}
export async function recordSoftDelete(receipt){receipt.entry.softDeleted=true;await receipt.persist()}

// The lock covers ALL initialization. Preserve the first error; always attempt every cleanup.
export async function withSeedResources(run,{directory=seedDirectory(),acquire=acquireSeed}={}) {
 const release=await acquire(directory)
 let browser,result,primary
 const contexts=[],errors=[]
 const resources={
  async launch(chromium,options){browser=await chromium.launch(options);return browser},
  async context(options){const context=await browser.newContext(options);contexts.push(context);return context},
  async finish(fn){try{await fn()}catch(error){errors.push(error)}},
 }
 try{result=await run(resources)}catch(error){primary=error}
 for(const close of [...contexts.reverse().map(context=>()=>context.close()),()=>browser?.close(),release]){
  try{await close()}catch(error){errors.push(error)}
 }
 if(primary){if(errors.length)primary.cleanupErrors=errors;throw primary}
 if(errors.length)throw new AggregateError(errors,'Seed test cleanup failed')
 return result
}
