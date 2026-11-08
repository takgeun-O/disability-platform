"use client"
import { useEffect, useState } from "react"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { getAdminPost, type AdminPostDetail } from "@/lib/admin-detail"
import { AdminApiError } from "@/lib/admin-posts"
import { POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import { useAdminModeration } from "./AdminModeration"
import AdminContentMeta, { AdminAuthor, AdminStatusBadge } from "./AdminContentMeta"
export default function AdminPostPreview({postId,returnHref,blocked=false,readOnly=false}:{postId:number;returnHref:string;blocked?:boolean;readOnly?:boolean}) {
  const auth=useAuth(),scope=useNavigationRequest()
  const {revision,readCurrent,locked,completed,open,deny}=useAdminModeration()
  const capture=auth.captureMemberRequest,memberId=auth.member!.memberId
  const [retry,setRetry]=useState(0)
  const [result,setResult]=useState<{revision:number;retry:number;data?:AdminPostDetail;error?:string}|null>(null)
  useEffect(()=>{
    const controller=new AbortController(),navigation=scope.begin(),session=capture(memberId)
    const current=()=>!controller.signal.aborted&&navigation()&&session.isCurrent()&&readCurrent(revision)
    void getAdminPost(postId,controller.signal).then(data=>{if(current())setResult({revision,retry,data})}).catch(error=>{
      if(!current())return
      if(error instanceof AdminApiError&&error.status===401){session.expire();return}
      if(error instanceof AdminApiError&&error.status===403){deny();return}
      setResult({revision,retry,error:error instanceof AdminApiError&&error.status===404?"현재 게시글을 찾을 수 없습니다.":"게시글을 불러오지 못했습니다."})
    })
    return()=>controller.abort()
  },[postId,revision,retry,scope,capture,memberId,readCurrent,deny])
  const active=result?.revision===revision&&result.retry===retry?result:null,data=active?.data
  return <section className="admin-preview-post" aria-label={readOnly?"현재 게시글 내용":"게시글 미리보기"} aria-busy={!active}>
    {readOnly&&<h3>현재 게시글 내용</h3>}
    {readOnly&&<p className="admin-preview-help">조치 당시의 본문이 아닌 현재 내용입니다.</p>}
    <div className="admin-preview-links"><Link href={`/admin/posts/${postId}?from=${encodeURIComponent(returnHref)}`}>상세 관리로 이동</Link>{data?.status==="VISIBLE"&&<Link href={`/community/posts/${postId}`}>서비스에서 보기</Link>}</div>
    {!active&&<p role="status">게시글을 불러오고 있습니다…</p>}
    {active?.error&&<div role="alert"><p>{completed&&"조치는 완료됐지만 최신 내용을 확인하지 못했습니다. "}{active.error}</p><button onClick={()=>setRetry(n=>n+1)}>게시글 다시 시도</button></div>}
    {data&&<>
      <div className="admin-post-topline"><h3 className="admin-preview-title">{data.title}</h3><AdminStatusBadge status={data.status}/></div>
      <AdminAuthor author={data.author}/><p className="admin-preview-help">{POST_TOPICS[data.topic]} · {POST_TYPES[data.type]} · #{data.postId}</p>
      <AdminContentMeta item={data}/><p className="admin-timezone">한국 시간 (KST)</p>
      <div className="admin-full-content" tabIndex={0} role="region" aria-label="게시글 본문">{data.content}</div>
      {!readOnly&&(data.status==="DELETED"?<p>삭제된 게시글은 숨김 또는 숨김 해제할 수 없습니다.</p>:<button className="admin-moderation-primary" disabled={blocked||locked} onClick={()=>{if(!blocked&&!locked)open({postId,label:data.title,action:data.status==="HIDDEN"?"restore":"hide"})}}>게시글 {data.status==="HIDDEN"?"숨김 해제":"숨김 처리"}</button>)}
    </>}

  </section>
}
