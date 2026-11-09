"use client"
import { createContext, useCallback, useContext, useLayoutEffect, useRef, useState, type ReactNode } from "react"
import { createPortal } from "react-dom"
import { usePathname, useSearchParams } from "next/navigation"
import OverlayDialog from "@/components/OverlayDialog"
import { getAdminDialogReturn, setAdminDialogReturn } from "@/lib/admin-dialog-return"
import { adminReturnHref } from "@/lib/admin-navigation"
import type { OperationLog } from "@/lib/admin-operation-logs"
import AdminPostPreview from "./AdminPostPreview"
import AdminCommentPreview from "./AdminCommentPreview"
import AdminLogPreview from "./AdminLogPreview"
import { AdminModerationWorkspace, useAdminModeration } from "./AdminModeration"

// A flow remembers only list scroll, never a browser-history traversal distance.
const flows=new Map<string,{href:string;x:number;y:number}>()
export function useAdminDialogReturn(navigation:number) {
  const pathname=usePathname(),search=useSearchParams().toString()
  useLayoutEffect(()=>{
    const pending=getAdminDialogReturn()
    if(!pending||pending.href!==location.pathname+location.search)return
    const restore=()=>{
      const row=document.querySelector<HTMLElement>(pending.selector)
      const ready=document.querySelector('.admin-results[aria-busy="false"]')
      if(!row&&!ready)return
      setAdminDialogReturn(null);observer.disconnect()
      ;(row??document.querySelector<HTMLElement>('[data-admin-list-heading]'))?.focus({preventScroll:true})
      window.scrollTo({left:pending.x,top:pending.y,behavior:'instant'})
    }
    const observer=new MutationObserver(restore);observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['aria-busy']});restore()
    return()=>{observer.disconnect();if(getAdminDialogReturn()===pending)setAdminDialogReturn(null)}
  },[navigation,pathname,search])
}
const ManagedPost = createContext<((postId:number)=>void)|null>(null)
export function useAdminManagedPostTitle(postId:number) {
  const report=useContext(ManagedPost)
  useLayoutEffect(()=>{report?.(postId)},[report,postId])
}
const Header = createContext<HTMLElement | null>(null)
export function AdminModalActions({children}:{children:ReactNode}) {
  const destination=useContext(Header)
  return destination?createPortal(children,destination):null
}
// Pass only app-owned metadata: Next copies its router fields itself. Passing
// __NA would bypass Next's native-history integration and leave searchParams stale.
export function openAdminDialog(href:string) {
  const flow=crypto.randomUUID()
  flows.set(flow,{href:location.pathname+location.search,x:scrollX,y:scrollY})
  history.pushState({adminModalFlow:flow},'',href)
}
export function syncAdminListUrl(href:string) {
  const next=new URL(href,location.origin),current=new URL(location.href)
  if(next.searchParams.get('selected')===current.searchParams.get('selected')&&next.searchParams.has('selected')) {
    for(const key of ['view']) {const value=current.searchParams.get(key);if(value)next.searchParams.set(key,value)}
  }
  history.replaceState({adminModalFlow:history.state?.adminModalFlow},'',next.pathname+next.search)
}
export default function AdminContentDialog({id,log,listHref}:{id:number;log?:OperationLog;listHref:string}) {
  const params=useSearchParams(),{requestExit,confirmation,feedback,inlineTarget,originScroll}=useAdminModeration()
  const [header,setHeader]=useState<HTMLDivElement|null>(null)
  const body=useRef<HTMLDivElement>(null),heading=useRef<HTMLHeadingElement>(null),closing=useRef(false)
  const openedScroll=useRef({x:typeof window==='undefined'?0:window.scrollX,y:typeof window==='undefined'?0:window.scrollY})
  const readScroll=useRef(0),wasAction=useRef(false);
  useLayoutEffect(()=>{
    if(!body.current)return;
    if(inlineTarget&&!wasAction.current){readScroll.current=originScroll;body.current.scrollTop=0;}
    if(!inlineTarget&&wasAction.current)body.current.scrollTop=readScroll.current;
    wasAction.current=!!inlineTarget;
  },[inlineTarget,originScroll]);
  const view=log && ['manage','parent'].includes(params.get('view')??'')?params.get('view'):'record'
  const parent=log?.targetType==='COMMENT'&&view==='parent'
  const key=`${id}:${view}:${parent??''}`
  const [resolvedPost,setResolvedPost]=useState<{key:string;id:number}|null>(null)
  const reportPost=useCallback((id:number)=>setResolvedPost({key,id}),[key])
  const title=log ? view==='record'?`운영 기록 · #${log.logId}`:parent?(resolvedPost?.key===key?`게시글 #${resolvedPost.id} 관리`:`소속 게시글 확인 중 · 댓글 #${log.targetId}`):log.targetType==='POST'?`게시글 #${log.targetId} 관리`:`댓글 #${log.targetId} 관리` : `게시글 상세 · #${id}`
  useLayoutEffect(()=>{if(body.current)body.current.scrollTop=0;heading.current?.focus({preventScroll:true})},[key])
  const transition=(view:string)=>requestExit(()=>{
    const url=new URL(location.href);url.searchParams.set('view',view);url.searchParams.delete('parent')
    history.pushState({adminModalFlow:history.state?.adminModalFlow},'',url.pathname+url.search)
  })
  const close=()=>requestExit(()=>{
    if(closing.current)return
    closing.current=true
    const destination=new URL(adminReturnHref(listHref),location.origin)
    for(const key of ['selected','view','parent'])destination.searchParams.delete(key)
    const href=destination.pathname+destination.search,flow=flows.get(history.state?.adminModalFlow)
    const scroll=flow&&new URL(flow.href,location.origin).pathname===destination.pathname?flow:openedScroll.current
    setAdminDialogReturn({href,selector:`#${log?'log':'post'}-select-${id}`,x:scroll.x,y:scroll.y})
    // Explicit close is not Back. Replace only the current list selection; Next's
    // native-history integration keeps searchParams and its router tree in sync.
    history.replaceState({},'',href)
  })
  return <OverlayDialog titleId="admin-content-dialog-title" className="admin-content-dialog" onClose={close} closeOnBackdropClick={false} closeOnNavigation={false} returnFocus={`#${log?'log':'post'}-select-${id}`} fallbackFocus="[data-admin-list-heading]" initialFocus="#admin-content-dialog-title">
    <Header.Provider value={header}>
      <header className="admin-dialog-heading">{log&&view!=='record'&&<nav className="admin-dialog-return" aria-label="관리 화면 이동"><button aria-label="운영 기록으로 돌아가기" onClick={()=>transition('record')}>← 운영 기록 #{log.logId}</button>{parent&&<button onClick={()=>transition('manage')}>댓글 관리로 돌아가기</button>}</nav>}<h2 id="admin-content-dialog-title" ref={heading} tabIndex={-1}>{title}</h2><div className="admin-dialog-actions" hidden={!!inlineTarget} ref={setHeader}/><button type="button" onClick={close}>닫기</button></header>
      {confirmation}
      <div ref={body} className="admin-dialog-body" tabIndex={0} role="region" aria-label="상세 내용">

        <div hidden={!!inlineTarget}>{feedback}</div>
        {inlineTarget&&<AdminModerationWorkspace/>}
        <ManagedPost.Provider value={parent?reportPost:null}><div key={key} hidden={!!inlineTarget}>
          {!log?<AdminPostPreview postId={id} returnHref={listHref}/>:view==='record'?<AdminLogPreview log={log} returnHref={listHref} onManage={()=>transition('manage')} onParent={()=>transition('parent')}/>:log.targetType==='POST'?<AdminPostPreview postId={log.targetId} returnHref={listHref}/>:<AdminCommentPreview commentId={log.targetId} manage parentManagement={parent} returnHref={listHref} onParent={()=>transition('parent')}/>}
        </div></ManagedPost.Provider>
      </div>
    </Header.Provider>
  </OverlayDialog>
}
