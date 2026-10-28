import fs from 'node:fs/promises'
const original=await import(process.env.PLAYWRIGHT_REAL_MODULE)
export const chromium={async launch(...args){
 const browser=await original.chromium.launch(...args),create=browser.newContext.bind(browser)
 browser.newContext=async(...options)=>{
  const context=await create(...options)
  if(process.env.BROWSER_FONT_CSS) {
   const css=await fs.readFile(process.env.BROWSER_FONT_CSS,'utf8')
   await context.route(u=>u.origin==='https://fonts.googleapis.com' && u.pathname==='/css2',r=>r.fulfill({status:200,contentType:'text/css',body:css}))
  }
  context.on('page',page=>{
   const pending=new Map()
   page.on('request',request=>{const u=new URL(request.url());pending.set(request,{origin:u.origin,path:u.pathname,type:request.resourceType()})})
   page.on('requestfinished',request=>pending.delete(request));page.on('requestfailed',request=>pending.delete(request))
   const goto=page.goto.bind(page)
   page.goto=async(...options)=>{try{return await goto(...options)}catch(error){await fs.appendFile(process.env.BROWSER_DIAGNOSTIC_LOG,JSON.stringify({at:new Date().toISOString(),error:String(error).split('\n')[0],pending:[...pending.values()]})+'\n');throw error}}
  })
  return context
 }
 return browser
}}
