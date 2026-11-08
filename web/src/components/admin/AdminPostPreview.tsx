"use client"
import { useCallback, useEffect, useRef, useState } from "react"
import Link from "@/components/AppLink"
import { writtenTime } from "@/lib/written-time"
import { AdminModalActions, useAdminManagedPostTitle } from "./AdminContentDialog"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { getAdminPost, type AdminPostDetail } from "@/lib/admin-detail"
import { AdminApiError } from "@/lib/admin-posts"
import { POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import { AdminActionLabel, AdminModerationSlot, useAdminModeration } from "./AdminModeration"
import AdminComments from "./AdminComments"
import AdminContentMeta, { AdminAuthor, AdminStatusBadge } from "./AdminContentMeta"
export default function AdminPostPreview({postId,blocked=false,readOnly=false,showHeading=true,onManage}:{postId:number;returnHref:string;blocked?:boolean;readOnly?:boolean;showHeading?:boolean;onManage?:()=>void}) {
  useAdminManagedPostTitle(postId)
  const auth=useAuth(),scope=useNavigationRequest()
  const {revision,readCurrent,locked,completed,open,deny}=useAdminModeration()
  const capture=auth.captureMemberRequest,memberId=auth.member!.memberId
  const commentsRegion=useRef<HTMLElement>(null),commentsReady=useRef(false),jumpPending=useRef(false)
  const [commentsOpened,setCommentsOpened]=useState(false)
  const scrollComments=useCallback(()=>{
    const region=commentsRegion.current,body=region?.closest<HTMLElement>('.admin-dialog-body')
    if(region&&body)body.scrollTo({top:body.scrollTop+region.getBoundingClientRect().top-body.getBoundingClientRect().top-16,behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'})
  },[])
  const onCommentsReady=useCallback(()=>{
    commentsReady.current=true
    if(jumpPending.current&&document.activeElement===commentsRegion.current)scrollComments()
    jumpPending.current=false
  },[scrollComments])
  const jumpComments=()=>{
    jumpPending.current=!commentsReady.current
    setCommentsOpened(true)
    scrollComments()
    commentsRegion.current?.focus({preventScroll:true})
  }

  const [retry,setRetry]=useState(0)
  const [result,setResult]=useState<{revision:number;retry:number;data?:AdminPostDetail;error?:string;notFound?:boolean}|null>(null)
  useEffect(()=>{
    const controller=new AbortController(),navigation=scope.begin(),session=capture(memberId)
    const current=()=>!controller.signal.aborted&&navigation()&&session.isCurrent()&&readCurrent(revision)
    void getAdminPost(postId,controller.signal).then(data=>{if(current())setResult({revision,retry,data})}).catch(error=>{
      if(!current())return
      if(error instanceof AdminApiError&&error.status===401){session.expire();return}
      if(error instanceof AdminApiError&&error.status===403){deny();return}
      setResult(previous=>({revision,retry,data:error instanceof AdminApiError&&error.status===404?undefined:previous?.data,notFound:error instanceof AdminApiError&&error.status===404,error:error instanceof AdminApiError&&error.status===404?"현재 게시글을 찾을 수 없습니다.":"게시글을 불러오지 못했습니다."}))
    })
    return()=>controller.abort()
  },[postId,revision,retry,scope,capture,memberId,readCurrent,deny])
  const active=result?.revision===revision&&result.retry===retry?result:null,data=result?.data
  useEffect(()=>{
    if(readOnly||!data)return
    const element=commentsRegion.current
    if(!element)return
    const observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){setCommentsOpened(true);observer.disconnect()}},{root:element.closest('.admin-dialog-body'),rootMargin:'160px'})
    observer.observe(element)
    return()=>observer.disconnect()
  },[readOnly,data])
  return <section className="admin-preview-post" aria-label={readOnly?"현재 게시글 내용":"게시글 상세 내용"} aria-busy={!active}>
    <AdminModalActions>{!readOnly&&<button type="button" onClick={jumpComments}>댓글 관리로 이동</button>}{data?.status==="VISIBLE"&&<Link href={`/community/posts/${postId}`}>원문 보기</Link>}</AdminModalActions>
    {readOnly&&showHeading&&<h3>현재 게시글 내용</h3>}
    {!active&&<p role="status">게시글을 불러오고 있습니다…</p>}
    {active?.error&&<div role="alert"><p>{completed&&"조치는 완료됐지만 최신 내용을 확인하지 못했습니다. "}{active.error}</p><button onClick={()=>setRetry(n=>n+1)}>게시글 다시 시도</button></div>}
    {data&&<>
      <div className="admin-post-topline"><h3 className="admin-preview-title">{data.title}</h3><AdminStatusBadge status={data.status}/></div>
      <div className="admin-compact-meta"><AdminAuthor author={data.author}/><span>{POST_TOPICS[data.topic]} · {POST_TYPES[data.type]}</span><time dateTime={data.createdAt}>{writtenTime(data.createdAt).exact}</time></div>
      <div className="admin-comment-body"><span className="admin-comment-body-label">게시글 본문</span><div className="admin-full-content" role="region" aria-label="게시글 본문">{data.content}</div></div>
      {onManage&&<button className="admin-manage-primary" type="button" disabled={!active||!!active.error} onClick={()=>{if(active&&!active.error)onManage()}}>이 게시글 관리</button>}
      <details className="admin-detail-metadata"><summary>수정·처리 시각</summary><AdminContentMeta item={data} omitCreated/></details>
      {!readOnly&&(data.status==="DELETED"?<p>삭제된 게시글은 숨김 또는 숨김 해제할 수 없습니다.</p>:<button className="admin-moderation-primary" disabled={blocked||locked||!active||!!active.error} onClick={()=>{if(!blocked&&!locked&&active&&!active.error)open({postId,content:data,label:data.title,action:data.status==="HIDDEN"?"restore":"hide"})}}><AdminActionLabel postId={postId}>게시글 {data.status==="HIDDEN"?"숨김 해제":"숨김 처리"}</AdminActionLabel></button>)}
      {!readOnly&&<AdminModerationSlot postId={postId}/>}
    </>}
    {!readOnly&&<section ref={commentsRegion} className="admin-dialog-comments" aria-label="댓글 영역" tabIndex={-1}>
      {!commentsOpened&&<h3>댓글</h3>}
      {commentsOpened&&data?<AdminComments compact key={postId} postId={postId} parentStatus={active?.data?.status} parentUnavailable={blocked||!active||!!active.error} onForbidden={deny} onReady={onCommentsReady}/>:<p>댓글 영역에 도달하면 목록을 불러옵니다.</p>}
    </section>}
  </section>
}
