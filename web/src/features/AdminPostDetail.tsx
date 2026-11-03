"use client"
import { useEffect, useState } from "react"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { AdminApiError } from "@/lib/admin-posts"
import { getAdminPost, type AdminPostDetail as Detail } from "@/lib/admin-detail"
import { POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import AdminComments from "@/components/admin/AdminComments"
import AdminContentMeta, { AdminAuthor, AdminStatusBadge } from "@/components/admin/AdminContentMeta"
import AdminModeration, { useAdminModeration } from "@/components/admin/AdminModeration"
export default function AdminPostDetail(props: {postId:number;returnHref:string}) {
  return <AdminModeration><DetailContent {...props}/></AdminModeration>
}
function DetailContent({postId,returnHref}: {postId:number;returnHref:string}) {
  const auth=useAuth(),scope=useNavigationRequest()
  const { revision, readCurrent, locked, completed, open, deny: onForbidden } = useAdminModeration()
  const capture=auth.captureMemberRequest,memberId=auth.member!.memberId
  const [retry,setRetry]=useState(0)
  const [result,setResult]=useState<{attempt:number;revision:number;data?:Detail;error?:string;notFound?:boolean}|null>(null)
  useEffect(()=>{
    if(!postId)return
    const controller=new AbortController(),navigation=scope.begin(),session=capture(memberId)
    const current=()=>readCurrent(revision)&&!controller.signal.aborted&&navigation()&&session.isCurrent()
    void getAdminPost(postId,controller.signal).then(data=>{if(current())setResult({attempt:retry,revision,data})}).catch(error=>{
      if(!current())return
      if(error instanceof AdminApiError&&error.status===401){session.expire();return}
      if(error instanceof AdminApiError&&error.status===403){onForbidden();return}
      const notFound=error instanceof AdminApiError&&error.status===404
      setResult(previous=>({data:notFound?undefined:previous?.data,attempt:retry,revision,notFound,error:notFound?"게시글을 찾을 수 없습니다.":error instanceof Error?error.message:"게시글을 가져오지 못했습니다."}))
    })
    return()=>controller.abort()
  },[postId,retry,capture,memberId,scope,onForbidden,revision,readCurrent])
  const active=result?.attempt===retry && result.revision===revision?result:null
  const data=result?.data, busy=!active
  return <div className="admin-detail-view">
    <nav className="admin-breadcrumb" aria-label="현재 위치"><ol><li>관리자</li><li><Link href={returnHref}>게시글 관리</Link></li><li aria-current="page">#{postId}</li></ol></nav>
    <header className="admin-heading admin-detail-heading"><div><h1>게시글 상세</h1><p>게시글과 댓글의 상태를 확인하고 관리하세요.</p></div><Link className="admin-back" href={returnHref}>← 목록으로</Link></header>
    {!postId?<p role="alert">게시글을 찾을 수 없습니다.</p>:<>
      {busy && <p role="status">게시글을 불러오고 있습니다…</p>}
      {active?.error && <div className="admin-panel" role="alert"><p>{completed && "변경 결과는 확인됐지만 최신 내용을 불러오지 못했습니다. "}{active.error}</p>{!active.notFound&&<button onClick={()=>setRetry(n=>n+1)}>게시글 다시 시도</button>}</div>}
      {data && <>
      <article className="admin-panel admin-post-detail" aria-busy={busy}>
        <div className="admin-post-topline"><p>{POST_TOPICS[data.topic]} · {POST_TYPES[data.type]} · #{data.postId}</p><AdminStatusBadge status={data.status}/></div>
        <h2>{data.title}</h2><AdminAuthor author={data.author}/>
        <div className="admin-full-content">{data.content}</div>
        <footer className="admin-post-footer"><div><AdminContentMeta item={data}/><p className="admin-timezone">한국 시간 (KST)</p></div><div className="admin-item-action">
        {data.status === "DELETED" ? <p>삭제된 게시글은 숨김 또는 숨김 해제할 수 없습니다.</p> : <button className="admin-moderation-primary" disabled={busy || locked || !!active?.error} onClick={()=>open({postId,label:data.title,action:data.status === "HIDDEN" ? "restore" : "hide"})}>게시글 {data.status === "HIDDEN" ? "숨김 해제" : "숨김 처리"}</button>}
        </div></footer>
      </article>
      <AdminComments postId={postId} onForbidden={onForbidden} parentUnavailable={busy || !!active?.error}/>
      </>}
    </>}
  </div>
}
