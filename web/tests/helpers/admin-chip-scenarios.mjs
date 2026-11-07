import assert from "node:assert/strict"

export async function adminChipScenarios({run,setup,base,out,kind,ready,reads,template}) {
  const comments=kind==='comments',groupName=comments?'댓글 상태':'게시글 상태'
  const searchName=comments?'댓글 본문 검색':'게시글 제목 검색'
  const submitName=comments?'댓글 검색':'검색', resetName=comments?'댓글 검색 초기화':'초기화'
  const area=comments?'.admin-comment-results':'.admin-results'
  const slot=comments?'comments':'read',url=comments?'/admin/posts/2':'/admin/posts'
  const statuses=['VISIBLE','HIDDEN','DELETED'],labels=['정상','숨김','삭제']
  function body(u) {
    const sample=template(0), selected=u.searchParams.getAll('statuses')
    assert(!u.searchParams.has('status'));assert(selected.length)
    assert.deepEqual(selected,statuses.filter(s=>selected.includes(s)))
    const all=Array.from({length:27},(_,i)=>({...sample.items[0],status:statuses[i%3],
      ...(comments?{commentId:i+1,content:`검증 댓글 ${i+1}`}:{postId:i+1,title:`검증 게시글 ${i+1}`})}))
    const filtered=u.searchParams.get('keyword')==='없음'?[]:all.filter(v=>selected.includes(v.status))
    const page=Number(u.searchParams.get('page')),totalPages=Math.ceil(filtered.length/20)
    return {...sample,items:filtered.slice(page*20,page*20+20),page,totalElements:filtered.length,totalPages,hasPrevious:page>0,hasNext:page+1<totalPages}
  }
  async function open(width=1440) {
    const x=await setup('ADMIN',width)
    x.state[slot]=(r,u)=>r.fulfill({json:body(u)})
    await x.p.goto(base+url);await ready(x.p)
    x.field=x.p.getByRole('group',{name:groupName,exact:true})
    x.box=name=>x.field.getByRole('checkbox',{name,exact:true})
    x.input=x.p.getByLabel(searchName,{exact:true})
    x.params=()=>new URLSearchParams(reads(x.state).at(-1).search)
    return x
  }
  async function freeze(p) {
    await p.clock.install({time:new Date('2030-01-01T00:00:00Z')})
    await p.clock.pauseAt(new Date('2030-01-01T00:00:01Z'))
  }
  for(const width of [1440,320]) await run(`chips debounce, search, reset, empty and IME ${width}`,async()=>{
    const {context,p,state,box,input,params}=await open(width)
    try {
      for(const label of labels)assert(await box(label).isChecked())
      await p.getByRole('button',{name:'다음',exact:true}).click();await ready(p)
      await freeze(p)
      const initial=reads(state).length
      await input.fill('  적용 검색어  ')
      await box('삭제').uncheck();await p.clock.runFor(100);await box('숨김').uncheck()
      assert.equal(await box('정상').isChecked(),true);assert.equal(reads(state).length,initial)
      await p.clock.runFor(199);assert.equal(reads(state).length,initial)
      await p.clock.runFor(1);await ready(p)
      assert.equal(reads(state).length,initial+1);assert.deepEqual(params().getAll('statuses'),['VISIBLE'])
      assert.equal(params().has('keyword'),false);assert.equal(params().get('page'),'0')
      assert.equal(await p.locator('.admin-pagination').count(),0)
      // Search flushes the current selection and cancels its pending timer.
      await box('숨김').check();await p.clock.runFor(100);await input.press('Enter');await ready(p)
      assert.equal(params().get('keyword'),'적용 검색어');assert.deepEqual(params().getAll('statuses'),['VISIBLE','HIDDEN'])
      const afterSearch=reads(state).length;await p.clock.runFor(250);assert.equal(reads(state).length,afterSearch)
      // Composition-confirming Enter cannot submit.
      await input.dispatchEvent('compositionstart');await input.fill('미적용 입력')
      await input.press('Enter');await p.clock.runFor(250);assert.equal(reads(state).length,afterSearch)
      await input.dispatchEvent('compositionend')
      await box('정상').uncheck();await p.clock.runFor(200);await ready(p)
      assert.equal(params().get('keyword'),'적용 검색어');assert.deepEqual(params().getAll('statuses'),['HIDDEN'])
      assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))
      await p.screenshot({path:`${out}/chips-${kind}-${width}.png`,fullPage:true})
      await box('숨김').uncheck()
      await p.getByText('표시할 상태를 선택해 주세요.',{exact:true}).waitFor()
      const emptyCalls=reads(state).length
      await p.getByRole('button',{name:submitName,exact:true}).click();await input.press('Enter');await p.clock.runFor(500)
      assert.equal(reads(state).length,emptyCalls)
      assert.equal(await p.locator('.admin-pagination').count(),0)
      assert.equal(await p.locator(comments?'.admin-comment-list > li':'.admin-panel tbody tr').count(),0)
      assert.equal(await p.getByText(/검색 결과 .*개/).count(),0)
      assert.equal(await input.inputValue(),'미적용 입력')
      await p.screenshot({path:`${out}/chips-empty-${kind}-${width}.png`,fullPage:true})
      await box('삭제').check();await p.clock.runFor(200);await ready(p)
      assert.equal(params().get('keyword'),'적용 검색어');assert.deepEqual(params().getAll('statuses'),['DELETED'])
      // Reset flushes, cancels debounce, clears both keyword values and forces one request.
      await box('숨김').check();await p.getByRole('button',{name:resetName,exact:true}).click();await ready(p)
      const afterReset=reads(state).length;await p.clock.runFor(250);assert.equal(reads(state).length,afterReset)
      assert.deepEqual(params().getAll('statuses'),statuses);assert.equal(params().has('keyword'),false);assert.equal(await input.inputValue(),'')
      await box('정상').focus();await p.keyboard.press('Space');assert(!await box('정상').isChecked())
      assert(await box('정상').evaluate(e=>e===document.activeElement))
      await p.clock.runFor(200);await ready(p)
      assert.equal(reads(state).length,afterReset+1)
      await input.fill('없음');await input.press('Enter');await ready(p)
      assert.equal(await p.locator('.admin-pagination').count(),0)
      assert.equal(await p.getByText('표시할 상태를 선택해 주세요.',{exact:true}).count(),0)
    } finally {await context.close()}
  })
  for(const late of ['success','error']) for(const empty of [false,true]) await run(`chips reject ${late} delivery during ${empty?'empty':'debounce'} even without abort`,async()=>{
    const {context,p,state,box,input,params}=await open()
    let release
    try {
      await p.evaluate(()=>{const fetch=window.fetch;window.fetch=(u,o)=>fetch(u,{...o,signal:undefined})})
      await freeze(p)
      let arrive;const arrived=new Promise(r=>arrive=r),held=new Promise(r=>release=r)
      state[slot]=async(r,u)=>{arrive();await held;await r.fulfill(late==='error'?{status:401,json:{code:'AUTHENTICATION_REQUIRED'}}:{json:{...body(u),totalElements:999,totalPages:50,hasNext:true}})}
      await input.fill('지연');await input.press('Enter');await arrived
      await box('삭제').uncheck()
      if(empty){await box('정상').uncheck();await box('숨김').uncheck()}
      const delivered=p.waitForEvent('requestfinished',{predicate:r=>r.url().includes('keyword=')})
      release();await delivered;await p.clock.runFor(50)
      assert.equal(await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).count(),0)
      assert.equal(await p.getByText('999',{exact:true}).count(),0)
      assert.equal(await p.locator(area).getByRole('alert').count(),0)
      if(empty){assert.equal(await p.locator(area).getAttribute('aria-busy'),'false');await p.getByText('표시할 상태를 선택해 주세요.',{exact:true}).waitFor()}
      else assert.equal(await p.locator(area).getAttribute('aria-busy'),'true')
      state[slot]=(r,u)=>r.fulfill({json:body(u)})
      if(empty)await box('정상').check()
      await p.clock.runFor(200);await ready(p)
      assert.deepEqual(params().getAll('statuses'),empty?['VISIBLE']:['VISIBLE','HIDDEN'])
      assert.equal(params().get('keyword'),'지연')
    } finally {release?.();await context.close()}
  })
  await run('chips pending timer is cleared on logout',async()=>{
    const {context,p,state,box}=await open()
    try {
      await freeze(p);await box('삭제').uncheck();const count=reads(state).length
      await p.getByRole('button',{name:'로그아웃',exact:true}).click()
      await p.getByRole('heading',{name:'로그인이 필요합니다',exact:true}).waitFor()
      await p.clock.runFor(1000);assert.equal(reads(state).length,count)
      assert.equal(await p.locator('.admin-status-filter').count(),0)
    } finally {await context.close()}
  })
}
