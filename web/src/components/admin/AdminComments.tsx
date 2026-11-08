"use client"
import { writtenTime } from "@/lib/written-time"
import { useEffect, useId, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { ADMIN_CONTENT_STATUSES, AdminApiError, type AdminQuery, type AdminContentStatus } from "@/lib/admin-posts"
import { getAdminComments, type AdminCommentPage } from "@/lib/admin-detail"
import { useAdminContentFilters } from "@/lib/useAdminContentFilters"
import AdminStatusFilter from "./AdminStatusFilter"
import AdminPagination from "./AdminPagination"
import { AdminCommentMeta, AdminAuthor, AdminStatusBadge } from "./AdminContentMeta"
import { AdminActionLabel, AdminModerationMissingResult, AdminModerationSlot, useAdminModeration } from "./AdminModeration"
export default function AdminComments({postId,onForbidden,parentUnavailable=false,parentStatus,readOnly=false,onReady,compact=false}: {postId:number;onForbidden:()=>void;parentUnavailable?:boolean;parentStatus?:AdminContentStatus;readOnly?:boolean;onReady?:()=>void;compact?:boolean}) {
  const {revision,readCurrent,locked,completed,open,inlineTarget}=useAdminModeration()
  const adjusted=useRef(0)
  const auth=useAuth(),scope=useNavigationRequest(),uid=useId()
  const capture=auth.captureMemberRequest,memberId=auth.member!.memberId
  const {query,setQuery,statuses,input,setInput,validation,setValidation,pending,empty,beginRead,
    change,selectStatuses,search,reset,composition}=useAdminContentFilters({statuses:ADMIN_CONTENT_STATUSES,keyword:"",page:0})
  const parentState=useRef(parentStatus)
  useEffect(()=>{parentState.current=parentStatus},[parentStatus])
  const [lastSuccess,setLastSuccess]=useState<{data:AdminCommentPage;query:AdminQuery;parentStatus?:AdminContentStatus}|null>(null)
  const [retry,setRetry]=useState(0)
  const [result,setResult]=useState<{query:AdminQuery;attempt:number;revision:number;data?:AdminCommentPage;error?:string;notFound?:boolean}|null>(null)
  useEffect(()=>{
    if(pending||empty||!scope.isActive())return
    const controller=new AbortController(),navigation=scope.begin(),session=capture(memberId),ownsRead=beginRead(controller)
    const current=()=>readCurrent(revision) && ownsRead() && navigation() && session.isCurrent()
    void getAdminComments(postId,query,controller.signal).then(data=>{if(current()){
      if(revision>0 && adjusted.current!==revision && query.page>0 && query.page>=data.totalPages){
        adjusted.current=revision;setQuery({...query,page:Math.max(0,data.totalPages-1)});return
      }
      setLastSuccess({data,query,parentStatus:parentState.current})
      setResult({query,attempt:retry,revision,data})
    }}).catch(error=>{
      if(!current())return
      if(error instanceof AdminApiError && error.status===401){setLastSuccess(null);session.expire();return}
      if(error instanceof AdminApiError && error.status===403){setLastSuccess(null);onForbidden();return}
      const notFound=error instanceof AdminApiError && error.status===404
      if(notFound)setLastSuccess(null)
      setResult(previous=>({query,attempt:retry,revision,data:notFound?undefined:previous?.data,notFound,
        error:notFound?"댓글 대상 게시글을 확인하지 못했습니다. 게시글이 존재하는지 다시 확인해 주세요." : error instanceof Error?error.message:"댓글을 가져오지 못했습니다."}))
    })
    return()=>controller.abort()
  },[postId,query,retry,capture,memberId,scope,onForbidden,revision,readCurrent,pending,empty,beginRead,setQuery])
  const active=!pending&&!empty&&result?.query===query && result.attempt===retry && result.revision===revision?result:null,busy=!empty&&!active

  const shown=!empty&&lastSuccess?.data.postId===postId?lastSuccess:null
  const data=shown?.data,displayQuery=shown?.query??query,stale=busy||!!active?.error
  useEffect(()=>{if(active)onReady?.()},[active,onReady])
  const confirmedParent=parentStatus!==shown?.parentStatus&&parentStatus?parentStatus:data?.postStatus
  return <section className={`admin-panel admin-comments${compact?" admin-comments-compact":""}`} aria-labelledby={`${uid}-heading`}>
    <div className="admin-comments-heading"><h2 id={`${uid}-heading`}>{compact?"댓글":"댓글 관리"}</h2><span>등록순</span></div>
    {data && confirmedParent !== "VISIBLE" && <p className="admin-parent-status">{confirmedParent === "HIDDEN" ? "게시글이 숨겨져 있어 댓글도 일반 사용자에게 표시되지 않습니다." : "게시글이 삭제되어 댓글도 일반 사용자에게 표시되지 않습니다."}</p>}
    <fieldset className="admin-inline-filter-lock" disabled={!!inlineTarget}><form className="admin-filters admin-comment-filters" onSubmit={e=>{e.preventDefault();search()}}>
      <AdminStatusFilter label="댓글 상태" value={statuses} onChange={next=>{if(!next.length){setLastSuccess(null);setResult(null)};selectStatuses(next)}}/>
      <div className="admin-filter-search-row">
        <label className="admin-search" htmlFor={`${uid}-keyword`}>댓글 본문 검색<input id={`${uid}-keyword`} value={input} {...composition} onChange={e=>{setInput(e.target.value);setValidation("")}} placeholder="댓글 본문 검색" aria-invalid={!!validation} aria-describedby={validation?`${uid}-error`:undefined}/></label>
        <button type="submit" aria-label="댓글 검색">검색</button>
      </div>
      <button type="button" className="admin-filter-reset" aria-label="댓글 검색 초기화" onClick={()=>reset()}>초기화</button>
    </form></fieldset>
    {validation&&<p id={`${uid}-error`} role="alert">{validation}</p>}
    <div className="admin-comment-results" tabIndex={-1} aria-busy={busy}>
      {empty?<p className="admin-empty-selection">표시할 상태를 선택해 주세요.</p>:<>
        {data&&<p className="admin-refresh-notice" role="status" style={{visibility:busy?"visible":"hidden"}}>목록을 갱신하고 있습니다…</p>}
        {busy&&!data&&<p role="status">댓글을 불러오고 있습니다…</p>}
        {active?.error&&<div role="alert">{data&&<p>갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.</p>}<p>{completed && "변경 결과는 확인됐지만 최신 댓글을 불러오지 못했습니다. "}{active.error}</p><button onClick={()=>setRetry(n=>n+1)}>댓글 다시 시도</button></div>}
        {data&&<>
        {!busy&&<AdminModerationMissingResult postId={postId} ids={data.items.map(item=>item.commentId)}/>}
        <div className="admin-comment-summary"><p>{stale?"이전 결과":"검색 결과"} <strong>{data.totalElements.toLocaleString("ko-KR")}</strong>개</p><p className="admin-timezone">한국 시간 (KST)</p></div>
        {!data.items.length ? displayQuery.page>0 && displayQuery.page>=data.totalPages ? <p>현재 페이지가 조회 범위를 벗어났습니다. <button disabled={stale} onClick={()=>{if(!stale)change({...query,page:0})}}>댓글 첫 페이지로</button></p> : <p>{displayQuery.keyword||displayQuery.statuses.length!==3?"검색 조건에 맞는 댓글이 없습니다.":"아직 댓글이 없습니다."}</p> :
          <ol className="admin-comment-list">{data.items.map(item=><li key={item.commentId} data-admin-comment-id={item.commentId} className={compact?"admin-comment-card":undefined}>
            <div className="admin-comment-copy"><div className="admin-comment-heading"><h3><AdminAuthor author={item.author}/></h3><span className="admin-comment-id">{compact?"댓글 #":"#"}{item.commentId}</span><AdminStatusBadge status={item.status}/></div>
            {compact&&<p className="admin-comment-created">작성 <time dateTime={item.createdAt}>{writtenTime(item.createdAt).exact.replace(" (한국 시간)","")}</time></p>}
            {compact?<div className="admin-comment-body"><span className="admin-comment-body-label">댓글 본문</span><p className="admin-full-content">{item.content}</p></div>:<p className="admin-full-content">{item.content}</p>}{!compact&&<AdminCommentMeta item={item}/>}</div>
            {!readOnly&&<div className="admin-item-action">{item.status === "DELETED" || confirmedParent === "DELETED" ? <p>{item.status === "DELETED" ? (compact?"삭제됨 · 변경 불가":"삭제된 댓글은 변경할 수 없습니다.") : (compact?"소속 게시글 삭제 · 변경 불가":"게시글이 삭제되어 댓글을 변경할 수 없습니다.")}</p> : <button aria-label={compact?`댓글 #${item.commentId} ${item.status === "HIDDEN" ? "숨김 해제" : "숨김 처리"}`:undefined} disabled={stale || locked || parentUnavailable} onClick={()=>{if(stale||locked||parentUnavailable)return;open({postId,content:item,commentId:item.commentId,postStatus:confirmedParent,label:item.content.slice(0,120),action:item.status === "HIDDEN" ? "restore" : "hide"})}}><AdminActionLabel postId={postId} commentId={item.commentId}>{!compact&&"댓글 "}{item.status === "HIDDEN" ? "숨김 해제" : "숨김 처리"}</AdminActionLabel></button>}
            </div>}
            {!readOnly&&<AdminModerationSlot postId={postId} commentId={item.commentId}/>}
            {compact&&<details className="admin-detail-metadata"><summary>수정·처리 시각</summary><AdminCommentMeta item={item} omitCreated/></details>}
          </li>)}</ol>}
      </>}
      </>}
    </div>
    {data&&data.totalPages>1&&<AdminPagination label="댓글 페이지" data={data} busy={busy||!!inlineTarget} failed={!!active?.error} onPage={page=>{if(!busy&&!inlineTarget&&!active?.error)change({...query,page})}}/>}
  </section>
}
