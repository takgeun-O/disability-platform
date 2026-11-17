"use client"
import { useLayoutEffect, useRef, useState, type CSSProperties } from "react"
import { LOG_ACTIONS, type OperationLog } from "@/lib/admin-operation-logs"
import { writtenTime } from "@/lib/written-time"
import AdminLogPreview from "./AdminLogPreview"
import { AdminModerationWorkspace, revealModerationField, useAdminModeration } from "./AdminModeration"

type Props={log:OperationLog;listHref:string;blocked:boolean;onClose:()=>void;hidden:boolean;expanded:boolean;compact:boolean;onLayout:()=>void;onList:()=>void}
export default function AdminLogPanel({log,listHref,blocked,onClose,hidden,expanded,compact,onLayout,onList}:Props) {
  const [record]=useState(log)
  const {inlineTarget,feedback,busy}=useAdminModeration()
  const panel=useRef<HTMLElement>(null),body=useRef<HTMLDivElement>(null),heading=useRef<HTMLHeadingElement>(null)
  const readScroll=useRef(0),wasAction=useRef(false)
  const [panelHeight,setPanelHeight]=useState<number>(),[readHeight,setReadHeight]=useState(0),[reasonOpen,setReasonOpen]=useState(false)
  const positions=useRef(new Map<boolean,{body:number;reading:number}>())
  const rememberScroll=()=>positions.current.set(expanded,{body:body.current?.scrollTop??0,reading:panel.current?.querySelector('.admin-action-content')?.scrollTop??0})
  useLayoutEffect(()=>{
    if(hidden)return
    heading.current?.focus({preventScroll:true})
    const saved=positions.current.get(expanded)
    if(!saved)return
    let second=0
    const first=requestAnimationFrame(()=>{second=requestAnimationFrame(()=>{
      if(body.current)body.current.scrollTop=saved.body
      const reading=panel.current?.querySelector('.admin-action-content');if(reading)reading.scrollTop=saved.reading
    })})
    return()=>{cancelAnimationFrame(first);cancelAnimationFrame(second)}
  },[hidden,expanded])
  useLayoutEffect(()=>{
    if(!body.current)return
    if(inlineTarget&&!wasAction.current){
      readScroll.current=body.current.scrollTop;body.current.scrollTop=0
    }
    if(!inlineTarget&&wasAction.current)body.current.scrollTop=readScroll.current
    wasAction.current=!!inlineTarget
  },[inlineTarget])
  useLayoutEffect(()=>{
    const node=panel.current
    if(!node)return
    const form=node.querySelector<HTMLElement>('.admin-action-form')
    const history=node.querySelector<HTMLElement>('.admin-log-action-history')
    const header=node.querySelector<HTMLElement>('.admin-preview-heading')
    if(!header)return
    const reading=node.querySelector<HTMLElement>('.admin-action-content')
    const workspace=node.querySelector<HTMLElement>('.admin-action-workspace')
    if(!body.current)return
    const px=(value:string)=>parseFloat(value)||0
    let revealFrame=0
    const measure=(reveal=true)=>{
      if(hidden)return
      const box=getComputedStyle(node),bodyBox=getComputedStyle(body.current!)
      // The panel may start below the viewport top (e.g. compact detail).
      // Reading uses the available viewport; actions reserve the full viewport
      // before bringing the panel to the top. Subtract measured box-model costs.
      const capacity=Math.max(0,window.innerHeight-(inlineTarget?16:Math.max(16,node.getBoundingClientRect().top))-16)
      setPanelHeight(old=>old===capacity?old:capacity)
      if(!form||!reading||!workspace)return
      const space=getComputedStyle(workspace)
      const headerBox=getComputedStyle(header)
      const chrome=px(box.paddingTop)+px(box.paddingBottom)+px(box.borderTopWidth)+px(box.borderBottomWidth)
        +header.getBoundingClientRect().height+px(headerBox.marginTop)+px(headerBox.marginBottom)
      const beforeReading=reading.getBoundingClientRect().top-body.current!.getBoundingClientRect().top+body.current!.scrollTop
      const available=capacity-chrome-beforeReading-px(bodyBox.paddingBottom)
        -form.getBoundingClientRect().height-px(space.rowGap)-px(space.paddingBottom)
      const height=reasonOpen||node.clientWidth<400||available<240?0:Math.floor(available)
      setReadHeight(old=>old===height?old:height)
      // Summary/filter height and viewport changes must not strand a docked
      // action form below the viewport. Reserve its height before scrolling.
      if(!reveal)return
      cancelAnimationFrame(revealFrame)
      revealFrame=requestAnimationFrame(()=>{
        if(!node.isConnected||hidden)return
        const top=node.getBoundingClientRect().top
        if(top>16)window.scrollBy({top:top-16,behavior:'instant'})
      })
    }
    const observer=new ResizeObserver(()=>measure())
    for(const element of [form,history,header,node,workspace])if(element)observer.observe(element)
    const resized=()=>measure(),scrolled=()=>measure(false)
    window.addEventListener('resize',resized);window.addEventListener('scroll',scrolled,{passive:true});measure()
    return()=>{cancelAnimationFrame(revealFrame);observer.disconnect();window.removeEventListener('resize',resized);window.removeEventListener('scroll',scrolled)}
  },[inlineTarget,hidden,reasonOpen])
  const docked=!!inlineTarget&&readHeight>0
  useLayoutEffect(()=>{
    const root=panel.current,outer=body.current
    const reading=root?.querySelector<HTMLElement>('.admin-action-content')
    const text=reading?.querySelector('.admin-full-content')?.firstChild
    // The safe full-content renderer supplies one plain Text node. Keep a text
    // offset, not a pixel scroll offset: line wrapping may change on resize.
    if(!root||!outer||!reading||!(text instanceof Text)||hidden)return
    let anchor:{offset:number;inset:number}|null=null,frame=0
    const scrollport=()=>root.dataset.docked==='true'?reading:outer
    const shape=()=>`${root.clientWidth}/${outer.clientHeight}/${reading.clientHeight}/${root.dataset.docked}`
    let previousShape=shape()
    const activeField=()=>{
      const active=document.activeElement
      return active instanceof HTMLElement&&root.contains(active)&&active.matches('input,select,textarea')?active:null
    }
    const rangeAt=(offset:number)=>{
      const range=document.createRange()
      range.setStart(text,offset);range.setEnd(text,Math.min(text.length,offset+((text.data.codePointAt(offset)??0)>0xffff?2:1)))
      return range.getBoundingClientRect()
    }
    const capture=()=>{
      if(activeField()||shape()!==previousShape)return
      const container=scrollport().getBoundingClientRect(),full=text.parentElement!.getBoundingClientRect()
      const top=Math.max(0,container.top,outer.getBoundingClientRect().top),bottom=Math.min(innerHeight,container.bottom)
      if(full.bottom<=top||full.top>=bottom){anchor=null;return}
      let low=0,high=text.length-1
      while(low<high){const middle=(low+high)>>1;if(rangeAt(middle).bottom<=top+2)low=middle+1;else high=middle}
      // Do not store half a supplementary-plane character.
      if(low>0&&text.data.charCodeAt(low)>=0xdc00&&text.data.charCodeAt(low)<=0xdfff)low--
      const rect=rangeAt(low)
      anchor={offset:low,inset:Math.max(0,rect.top-top)}
    }
    const restore=()=>{
      if(shape()===previousShape)return
      previousShape=shape()
      const field=activeField()
      if(field)revealModerationField(field)
      else if(anchor&&text.isConnected){
        const container=scrollport(),bounds=container.getBoundingClientRect(),top=Math.max(0,bounds.top,outer.getBoundingClientRect().top)
        container.scrollTop+=rangeAt(anchor.offset).top-top-Math.min(anchor.inset,container.clientHeight/2)
      }
    }
    const resized=()=>{cancelAnimationFrame(frame);frame=requestAnimationFrame(restore)}
    const observer=new ResizeObserver(resized)
    for(const element of [root,outer,reading])observer.observe(element)
    reading.addEventListener('scroll',capture,{passive:true});outer.addEventListener('scroll',capture,{passive:true})
    capture()
    return()=>{observer.disconnect();reading.removeEventListener('scroll',capture);outer.removeEventListener('scroll',capture);cancelAnimationFrame(frame)}
  },[inlineTarget,hidden])
  return <aside ref={panel} hidden={hidden} className="admin-log-panel" data-docked={docked} aria-labelledby="admin-log-panel-title" style={{'--admin-log-reading-height':`${readHeight}px`,'--admin-log-panel-height':panelHeight===undefined?undefined:`${panelHeight}px`} as CSSProperties}>
    <header className="admin-preview-heading">
      <h2 ref={heading} id="admin-log-panel-title" tabIndex={-1}>운영 기록 #{record.logId}<span className="admin-log-secondary">{record.targetType==='POST'?'게시글':'댓글'} #{record.targetId}</span></h2>
      <div className="admin-log-panel-controls">
        {compact&&<button type="button" onClick={()=>{rememberScroll();onList()}}>목록으로 돌아가기</button>}
        {!compact&&<button type="button" onClick={()=>{rememberScroll();onLayout()}}>{expanded?'기본 배치로':'상세 넓게 보기'}</button>}
        <button type="button" disabled={busy} onClick={onClose}>상세 닫기</button>
      </div>
    </header>
    <div ref={body} className="admin-log-panel-body" role="region" aria-label="운영 기록 상세 내용" tabIndex={0} onFocusCapture={event=>{
      const field=event.target
      if(field instanceof HTMLElement&&field.matches('input,select,textarea'))requestAnimationFrame(()=>{if(field.isConnected&&document.activeElement===field)revealModerationField(field)})
    }}>
      {feedback}
      {inlineTarget&&<>
        <section className="admin-log-action-history" aria-label="과거 조치 요약">
          <p><strong>당시 {LOG_ACTIONS[record.action]}</strong> · {writtenTime(record.createdAt).exact}</p>
          <details open={reasonOpen} onToggle={e=>setReasonOpen(e.currentTarget.open)}><summary><span>당시 사유</span><span className="admin-log-history-excerpt">{record.reason}</span></summary><p className="admin-reason-text">{record.reason}</p>{record.reasonDetail!==null&&record.reasonDetail!==record.reason&&<p className="admin-reason-text">{record.reasonDetail}</p>}</details>
        </section>
        <AdminModerationWorkspace stacked/>
      </>}
      <div hidden={!!inlineTarget}><AdminLogPreview log={record} returnHref={listHref} direct blocked={blocked}/></div>
    </div>
  </aside>
}
