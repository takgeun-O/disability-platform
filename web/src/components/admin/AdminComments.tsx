"use client"
import { useEffect, useId, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { ADMIN_STATUSES, AdminApiError, adminKeywordError, normalizeAdminKeyword, type AdminQuery } from "@/lib/admin-posts"
import { getAdminComments, type AdminCommentPage } from "@/lib/admin-detail"
import AdminPagination from "./AdminPagination"
import { AdminCommentMeta, AdminAuthor, AdminStatusBadge } from "./AdminContentMeta"
import { useAdminModeration } from "./AdminModeration"
export default function AdminComments({postId,onForbidden,parentUnavailable=false}: {postId:number;onForbidden:()=>void;parentUnavailable?:boolean}) {
  const {revision,readCurrent,locked,completed,open}=useAdminModeration()
  const adjusted=useRef(0)
  const auth=useAuth(),scope=useNavigationRequest(),uid=useId()
  const capture=auth.captureMemberRequest,memberId=auth.member!.memberId
  const [query,setQuery]=useState<AdminQuery>({status:"ALL",keyword:"",page:0})
  const [input,setInput]=useState(""),[validation,setValidation]=useState(""),[retry,setRetry]=useState(0)
  const [result,setResult]=useState<{query:AdminQuery;attempt:number;revision:number;data?:AdminCommentPage;error?:string;notFound?:boolean}|null>(null)
  useEffect(()=>{
    const controller=new AbortController(),navigation=scope.begin(),session=capture(memberId)
    const current=()=>readCurrent(revision) && !controller.signal.aborted && navigation() && session.isCurrent()
    void getAdminComments(postId,query,controller.signal).then(data=>{if(current()){
      if(revision>0 && adjusted.current!==revision && query.page>0 && query.page>=data.totalPages){
        adjusted.current=revision;setQuery({...query,page:Math.max(0,data.totalPages-1)});return
      }
      setResult({query,attempt:retry,revision,data})
    }}).catch(error=>{
      if(!current())return
      if(error instanceof AdminApiError && error.status===401){session.expire();return}
      if(error instanceof AdminApiError && error.status===403){onForbidden();return}
      const notFound=error instanceof AdminApiError && error.status===404
      setResult(previous=>({query,attempt:retry,revision,data:notFound?undefined:previous?.data,notFound,
        error:notFound?"댓글 대상 게시글을 확인하지 못했습니다. 게시글이 존재하는지 다시 확인해 주세요." : error instanceof Error?error.message:"댓글을 가져오지 못했습니다."}))
    })
    return()=>controller.abort()
  },[postId,query,retry,capture,memberId,scope,onForbidden,revision,readCurrent])
  const active=result?.query===query && result.attempt===retry && result.revision===revision?result:null,busy=!active
  const change=(next:AdminQuery)=>setQuery(old=>old.status===next.status&&old.keyword===next.keyword&&old.page===next.page?old:next)
  const data=active?.data
  return <section className="admin-panel admin-comments" aria-labelledby={`${uid}-heading`}>
    <div className="admin-comments-heading"><h2 id={`${uid}-heading`}>댓글 관리</h2><span>등록순</span></div>
    {data && data.postStatus !== "VISIBLE" && <p className="admin-parent-status">{data.postStatus === "HIDDEN" ? "게시글이 숨겨져 있어 댓글도 일반 사용자에게 표시되지 않습니다." : "게시글이 삭제되어 댓글도 일반 사용자에게 표시되지 않습니다."}</p>}
    <form className="admin-filters" onSubmit={e=>{e.preventDefault();const error=adminKeywordError(input);setValidation(error);if(!error)change({...query,keyword:normalizeAdminKeyword(input),page:0})}}>
      <div className="admin-filter-status"><label htmlFor={`${uid}-status`}>댓글 상태</label><select id={`${uid}-status`} value={query.status} onChange={e=>change({...query,status:e.target.value as AdminQuery["status"],page:0})}>{Object.entries(ADMIN_STATUSES).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></div>
      <label className="admin-search" htmlFor={`${uid}-keyword`}>댓글 본문 검색<input id={`${uid}-keyword`} value={input} onChange={e=>{setInput(e.target.value);setValidation("")}} placeholder="댓글 본문 검색" aria-invalid={!!validation} aria-describedby={validation?`${uid}-error`:undefined}/></label>
      <button type="submit" aria-label="댓글 검색">검색</button><button type="button" aria-label="댓글 검색 초기화" onClick={()=>{setInput("");setValidation("");change({status:"ALL",keyword:"",page:0})}}>초기화</button>
    </form>
    {validation&&<p id={`${uid}-error`} role="alert">{validation}</p>}
    <div className="admin-comment-results" aria-busy={busy}>
      {busy?<p role="status">댓글을 불러오고 있습니다…</p>:active?.error?<div role="alert"><p>{completed && "변경 결과는 확인됐지만 최신 댓글을 불러오지 못했습니다. "}{active.error}</p><button onClick={()=>setRetry(n=>n+1)}>댓글 다시 시도</button></div>:data&&<>
        <div className="admin-comment-summary"><p>검색 결과 <strong>{data.totalElements.toLocaleString("ko-KR")}</strong>개</p><p className="admin-timezone">한국 시간 (KST)</p></div>
        {!data.items.length ? query.page>0 && query.page>=data.totalPages ? <p>현재 페이지가 조회 범위를 벗어났습니다. <button onClick={()=>change({...query,page:0})}>댓글 첫 페이지로</button></p> : <p>{query.keyword||query.status!=="ALL"?"검색 조건에 맞는 댓글이 없습니다.":"아직 댓글이 없습니다."}</p> :
          <ol className="admin-comment-list">{data.items.map(item=><li key={item.commentId} data-admin-comment-id={item.commentId}>
            <div className="admin-comment-copy"><div className="admin-comment-heading"><h3><AdminAuthor author={item.author}/></h3><span className="admin-comment-id">#{item.commentId}</span><AdminStatusBadge status={item.status}/></div>
            <p className="admin-full-content">{item.content}</p><AdminCommentMeta item={item}/></div>
            <div className="admin-item-action">{item.status === "DELETED" || data.postStatus === "DELETED" ? <p>{item.status === "DELETED" ? "삭제된 댓글은 변경할 수 없습니다." : "게시글이 삭제되어 댓글을 변경할 수 없습니다."}</p> : <button disabled={locked || parentUnavailable} onClick={()=>open({postId,commentId:item.commentId,label:item.content.slice(0,120),action:item.status === "HIDDEN" ? "restore" : "hide"})}>댓글 {item.status === "HIDDEN" ? "숨김 해제" : "숨김 처리"}</button>}
            </div>
          </li>)}</ol>}
      </>}
    </div>
    {result?.data&&<AdminPagination label="댓글 페이지" data={result.data} busy={busy} failed={!!active?.error} onPage={page=>{if(!busy&&!active?.error)change({...query,page})}}/>}
  </section>
}
