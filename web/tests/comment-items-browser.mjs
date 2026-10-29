import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'
import {fileURLToPath} from 'node:url'
// Never install a test route into the application checkout.
if (process.argv[2] === '--install') {
  const root = await fs.realpath(process.argv[3])
  const checkout = await fs.realpath(fileURLToPath(new URL('..', import.meta.url)))
  assert(!root.startsWith(checkout) && root.startsWith('/private/tmp/'), 'use an isolated /private/tmp build')
  const dir = path.join(root, 'src/app/test-comment-items')
  await fs.mkdir(dir, {recursive:true})
  await fs.copyFile(new URL('./fixtures/comment-items.tsx', import.meta.url), path.join(dir, 'page.tsx'))
} else {
  const {chromium} = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')
  const out = process.env.COMMUNITY_ARTIFACT_DIR ?? '/private/tmp/iyum-comment-items'
  await fs.mkdir(out,{recursive:true})
  const browser = await chromium.launch({channel:'chrome',headless:true}), results=[]
  try {
    for (const width of [1440,390,320]) for(const js of [true,false]) {
      const context = await browser.newContext({viewport:{width,height:900},javaScriptEnabled:js,hasTouch:width<600})
      try {
        const p=await context.newPage(),errors=[]
        p.on('pageerror', e=>errors.push(String(e)))
        const writes=[]
        await p.route('http://127.0.0.1:8082/**',r=>{
          const request=r.request(),url=new URL(request.url())
          if(url.pathname.endsWith('/auth/me')) return r.fulfill({json:{memberId:1,email:'fixture@example.test',nickname:'검증 작성자',role:'USER'}})
          if(url.pathname.endsWith('/csrf')) return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'fixture-token'}})
          if(url.pathname.endsWith('/comments/42/like')) {
            writes.push(request.method())
            const liked=request.method()==='PUT'
            return r.fulfill({json:{postId:7,commentId:42,likeCount:liked?4:3,liked}})
          }
          return r.fulfill({status:404,json:{code:'NOT_FOUND'}})
        })
        await p.goto((process.env.BASE_URL??'http://127.0.0.1:8466')+'/test-comment-items')
        const rows=p.locator('main .community-comment-list > li[id]')
        assert.equal(await rows.count(),5)
        assert.equal(await p.locator('main .community-comment-body:visible').count(),5)
        const ids=await p.locator('[id]').evaluateAll(nodes=>nodes.map(n=>n.id))
        assert.equal(ids.length,new Set(ids).size)
        assert.equal(await p.locator('#comment-42').count(),1)
        assert.equal(await p.locator('main textarea, main form, main dialog').count(),0)
        assert.equal(await p.locator('main .community-comment-meta').filter({hasText:'수정됨'}).count(),5)
        assert.equal(await p.locator('main time').first().getAttribute('datetime'),commentDate())
        if(js) {
          const first=p.getByRole('region',{name:'첫 영역'}),second=p.getByRole('region',{name:'둘째 영역'})
          await first.getByRole('button',{name:'댓글 좋아요 등록',exact:true}).click()
          await second.getByRole('button',{name:'댓글 좋아요 취소',exact:true}).waitFor()
          assert.equal(await first.locator('[data-comment-like] span').first().textContent(),'좋아요 4')
          assert.equal(await second.locator('[data-comment-like] span').first().textContent(),'좋아요 4')
          await second.getByRole('button',{name:'댓글 좋아요 취소',exact:true}).click()
          await first.getByRole('button',{name:'댓글 좋아요 등록',exact:true}).waitFor()
          assert.deepEqual(writes,['PUT','DELETE'])
          await first.getByRole('button',{name:'내용 확인'}).click()
          await first.getByRole('status').waitFor()
          assert.equal(await second.getByRole('status').count(),0)
          const button=second.locator('.written-time-trigger').first()
          await button.focus();await p.keyboard.press('Enter')
          const target=await button.getAttribute('popovertarget')
          assert.equal(await p.locator('[popover]:popover-open').getAttribute('id'),target)
          await p.keyboard.press('Escape')
          assert.equal(await first.locator('[popover]:popover-open').count(),0)
          await p.locator('#comment-42').focus()
          assert.equal(await p.evaluate(()=>document.activeElement.id),'comment-42')
        }
        assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
        assert.deepEqual(errors,[])
        await p.screenshot({path:`${out}/${width}-${js}.png`,fullPage:true})
        results.push({width,js,pass:true})
      } catch(e) {results.push({width,js,pass:false,error:String(e)});process.exitCode=1}
      finally {await context.close()}
    }
  } finally {await browser.close();await fs.writeFile(`${out}/results.json`,JSON.stringify(results,null,2));console.log(results)}
}
function commentDate(){return '2026-10-27T04:00:00.123456Z'}
