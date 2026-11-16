"use client"
import { writtenTime } from "@/lib/written-time"
import { useEffect, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { getAdminComment, type AdminCommentDetail } from "@/lib/admin-detail"
import AdminPostPreview from "./AdminPostPreview"
import { AdminApiError } from "@/lib/admin-posts"
import { AdminAuthor, AdminCommentMeta, AdminStatusBadge } from "./AdminContentMeta"
import { AdminActionLabel, AdminModerationSlot, useAdminModeration } from "./AdminModeration"

export default function AdminCommentPreview({commentId,manage=false,blocked=false,onParent,onManage,parentManagement=false,returnHref="/admin/operation-logs",showHeading=true,managementSections=false}:{commentId:number;manage?:boolean;blocked?:boolean;onParent?:(postId:number)=>void;onManage?:()=>void;parentManagement?:boolean;returnHref?:string;showHeading?:boolean;managementSections?:boolean}) {
  const auth=useAuth(),scope=useNavigationRequest(),{deny,readCurrent,revision,locked,completed,open}=useAdminModeration()
  const capture=auth.captureMemberRequest,memberId=auth.member!.memberId
  const [retry,setRetry]=useState(0)
  const [result,setResult]=useState<{id:number;attempt:number;revision:number;data?:AdminCommentDetail;error?:string}|null>(null)
  useEffect(()=>{
    const controller=new AbortController(),navigation=scope.begin(),session=capture(memberId)
    const current=()=>!controller.signal.aborted&&navigation()&&session.isCurrent()&&readCurrent(revision)
    void getAdminComment(commentId,controller.signal).then(data=>{if(current())setResult({id:commentId,attempt:retry,revision,data})}).catch(error=>{
      if(!current())return
      if(error instanceof AdminApiError&&error.status===401){session.expire();return}
      if(error instanceof AdminApiError&&error.status===403){deny();return}
      setResult(previous=>({id:commentId,attempt:retry,revision,data:error instanceof AdminApiError&&error.status===404?undefined:previous?.id===commentId?previous.data:undefined,error:error instanceof AdminApiError&&error.status===404?"현재 댓글 정보를 찾을 수 없습니다.":"댓글 정보를 불러오지 못했습니다. 다시 시도해 주세요."}))
    })
    return()=>controller.abort()
  },[commentId,retry,scope,capture,memberId,deny,readCurrent,revision])
  const active=result?.id===commentId&&result.attempt===retry&&result.revision===revision?result:null,data=result?.id===commentId?result.data:undefined
  const confirmed=result?.id===commentId?result.data:undefined
  const unavailable=!active||!!active.error, actionLocked=locked||unavailable||blocked
  if(parentManagement&&confirmed)return <><p>소속 게시글 #{confirmed.postId}</p>{!active&&<p role="status">현재 댓글과 소속 게시글 상태를 확인하고 있습니다…</p>}{active?.error&&<div role="alert"><p>{active.error} 이전 상태를 표시하며 조치를 제한합니다.</p><button onClick={()=>setRetry(n=>n+1)}>댓글 정보 다시 시도</button></div>}<AdminPostPreview key={confirmed.postId} postId={confirmed.postId} returnHref={returnHref} blocked={!active||!!active.error}/></>
  return <section className="admin-preview-comment" aria-label="현재 댓글 내용" tabIndex={-1} aria-busy={!active}>
    {showHeading&&<h3>{managementSections ? "댓글 정보" : "현재 댓글"}</h3>}
    {!active&&<p role="status">댓글 정보를 불러오고 있습니다…</p>}
    {active?.error&&<div role="alert"><p>{completed&&"조치는 완료됐지만 최신 내용을 확인하지 못했습니다. "}{active.error}</p><button type="button" onClick={()=>setRetry(n=>n+1)}>댓글 정보 다시 시도</button></div>}
    {data&&<article className="admin-comment-card" data-admin-comment-id={data.commentId}>
      {unavailable&&<p className="admin-comment-stale" role="status">{active?.error?"최신 상태를 확인하지 못해 이전 내용을 표시합니다.":"최신 상태를 확인하는 동안 이전 내용을 표시합니다."}</p>}
      <div className="admin-comment-heading"><strong>댓글 #{data.commentId}</strong><AdminStatusBadge status={data.status}/><AdminAuthor author={data.author}/></div>
      <p className="admin-comment-created">작성 <time dateTime={data.createdAt}>{writtenTime(data.createdAt).exact}</time></p>
      <div className="admin-comment-body"><span className="admin-comment-body-label">댓글 본문</span><div className="admin-full-content" tabIndex={0} role="region" aria-label="현재 댓글 본문">{data.content}</div></div>
      {managementSections&&<h3 className="report-comment-operations">운영 조치</h3>}
      <div className="admin-comment-actions">
        {onManage&&<button className="admin-manage-primary" type="button" disabled={unavailable} onClick={()=>{if(!unavailable)onManage()}}>이 댓글 관리</button>}
        {manage&&(data.status==='DELETED'||data.postStatus==='DELETED'?<p>{data.status==='DELETED'?"삭제됨 · 변경 불가":"소속 게시글 삭제 · 변경 불가"}</p>:<button type="button" aria-label={`댓글 #${data.commentId} ${data.status==='HIDDEN'?'숨김 해제':'숨김 처리'}`} disabled={actionLocked} onClick={()=>{if(!actionLocked)open({postId:data.postId,content:data,commentId:data.commentId,postStatus:data.postStatus,label:data.content.slice(0,120),action:data.status==='HIDDEN'?'restore':'hide'})}}><AdminActionLabel postId={data.postId} commentId={data.commentId}>{data.status==='HIDDEN'?'숨김 해제':'숨김 처리'}</AdminActionLabel></button>)}
      </div>
      {manage&&<AdminModerationSlot postId={data.postId} commentId={data.commentId}/>}
      <div className="admin-comment-parent">{managementSections&&<h3>소속 게시글 및 부가 정보</h3>}<p>소속 게시글 #{data.postId} <AdminStatusBadge status={data.postStatus}/></p>{onParent&&<button type="button" disabled={unavailable} onClick={()=>{if(!unavailable)onParent(data.postId)}}>소속 게시글 보기</button>}</div>
      {data.postStatus!=="VISIBLE"&&<p className="admin-parent-status">소속 게시글이 {data.postStatus==="HIDDEN"?"숨겨져":"삭제되어"} 있어 댓글도 일반 사용자에게 표시되지 않습니다.</p>}
      <details className="admin-detail-metadata"><summary>수정·처리 시각</summary><AdminCommentMeta item={data} omitCreated/></details>
    </article>}
  </section>
}
