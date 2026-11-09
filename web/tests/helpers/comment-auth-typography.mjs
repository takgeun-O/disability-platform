import assert from 'node:assert/strict'
import fs from 'node:fs/promises'

// Measure one rendered dialog in a single frame after fonts are ready.
export async function checkCommentAuthTypography(page, output, label) {
  await page.evaluate(() => document.fonts.ready)
  const result = await page.locator('dialog.comment-auth-dialog[open]').evaluate(dialog => {
    const rect = el => { const r=el.getBoundingClientRect(); return {x:r.x,y:r.y,right:r.right,bottom:r.bottom,width:r.width,height:r.height} }
    const typography = el => { const s=getComputedStyle(el); return Object.fromEntries(['fontSize','fontWeight','lineHeight','marginTop','marginRight','marginBottom','marginLeft'].map(k=>[k,s[k]])) }
    const title=dialog.querySelector('h2'), body=dialog.querySelector('.comment-auth-description > p'), note=dialog.querySelector('.comment-auth-draft-note')
    return {title:typography(title),body:typography(body),note:note?typography(note):null,dialog:rect(dialog),titleRect:rect(title),close:rect(dialog.querySelector('.comment-auth-close')),
      elements:[title,...dialog.querySelectorAll('p,button')].map(el=>({tag:el.tagName,rect:rect(el),overflow:el.scrollWidth>el.clientWidth+1})),viewport:innerWidth,pageWidth:document.documentElement.scrollWidth}
  })
  const style=(size,weight,line,top,bottom)=>({fontSize:size,fontWeight:weight,lineHeight:line,marginTop:top,marginRight:'0px',marginBottom:bottom,marginLeft:'0px'})
  assert.deepEqual(result.title,style('22px','700','33px','0px','12px'))
  assert.deepEqual(result.body,style('16px','400','27.2px','0px','0px'))
  if(result.note)assert.deepEqual(result.note,style('14px','400','23.8px','12px','0px'))
  assert(result.pageWidth<=result.viewport)
  for(const {rect,overflow} of result.elements){assert(!overflow);assert(rect.x>=result.dialog.x&&rect.right<=result.dialog.right);assert(rect.y>=result.dialog.y&&rect.bottom<=result.dialog.bottom)}
  const overlap=(a,b)=>a.x<b.right&&a.right>b.x&&a.y<b.bottom&&a.bottom>b.y
  assert(!overlap(result.titleRect,result.close))
  const buttons=result.elements.filter(e=>e.tag==='BUTTON')
  for(let i=0;i<buttons.length;i++)for(let j=i+1;j<buttons.length;j++)assert(!overlap(buttons[i].rect,buttons[j].rect))
  await fs.writeFile(`${output}/typography-${label}.json`,JSON.stringify(result,null,2))
  await page.screenshot({path:`${output}/typography-${label}.png`})
}
