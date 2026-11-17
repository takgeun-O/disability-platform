"use client"
import { usePathname, useSearchParams } from "next/navigation"
import type { ModerationTarget } from "@/lib/admin-moderation"
import { isAdminRowSelection, isAdminRowKeyboardSelection } from "@/lib/admin-row-selection"
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import { useAdminUrlQuery } from "@/lib/useAdminUrlQuery"
import { adminLogQuery, adminLogHref, adminSelectionId } from "@/lib/admin-navigation"
import AdminModeration, { useAdminModeration } from "@/components/admin/AdminModeration"
import { syncAdminListUrl } from "@/components/admin/AdminContentDialog"
import AdminLogPanel from "@/components/admin/AdminLogPanel"
import AdminDateRange from "@/components/admin/AdminDateRange"
import AdminPagination from "@/components/admin/AdminPagination"
import { AdminApiError } from "@/lib/admin-posts"
import { writtenTime } from "@/lib/written-time"
import { DEFAULT_LOG_QUERY, LOG_SORTS, LOG_TARGETS, LOG_ACTIONS, getOperationLogs, logFilterError, type LogFilters, type LogQuery, type OperationLog, type OperationLogPage } from "@/lib/admin-operation-logs"
import "@/styles/admin-operation-logs.css"
import AdminOperationSummary, { useOperationSummary } from "@/components/admin/AdminOperationSummary"

import { MODERATION_REASONS, isModerationReasonType } from "@/lib/moderation-reasons"

export default function AdminOperationLogs() {
  const auth = useAuth(), actor = auth.member!.memberId, capture = auth.captureMemberRequest
  const [denied, setDenied] = useState(false)
  const deny = useCallback(() => setDenied(true), [])
  const protect = useCallback((failure: unknown) => {
    if (!(failure instanceof AdminApiError)) return false
    if (failure.status === 401) { capture(actor).expire(); return true }
    if (failure.status === 403) { deny(); return true }
    return false
  }, [actor, capture, deny])
  const summary = useOperationSummary(protect)
  const {values,navigation}=useAdminUrlQuery()
  const url=usePathname()+"?"+useSearchParams().toString()
  const [entry,setEntry]=useState({url,navigation})
  // Approval invalidates requests immediately, but only an actual URL transition
  // may remount the list. Remounting at approval could overwrite the destination URL.
  if(entry.url!==url)setEntry({url,navigation})
  const [recovery,setRecovery]=useState<ModerationTarget|null>(null)
  if (denied) return <div role="alert"><h1>관리자 권한이 없습니다</h1><p>이전 운영 기록 정보는 제거되었습니다.</p></div>
  return <div className="admin-operation-summary-view">
    <header className="admin-heading"><h1 tabIndex={-1} data-admin-list-heading>운영 기록</h1><p>게시글과 댓글의 숨김·복원 처리 내역을 확인하세요.</p></header>
    <AdminOperationSummary {...summary} />
    <AdminModeration inline exitDialog onSuccess={summary.refresh} onDenied={deny} recovery={recovery} onRecovery={setRecovery} key={entry.navigation}><OperationLogResults initialQuery={adminLogQuery(values)} initialSelected={adminSelectionId(values.selected)} /></AdminModeration>
  </div>
}
function OperationLogResults({initialQuery,initialSelected}:{initialQuery:LogQuery;initialSelected:number|null}) {
  const auth = useAuth(), scope = useNavigationRequest()
  const {deny,revision,readCurrent,recoveryFeedback,requestExit,readLocked,completion}=useAdminModeration()
  const memberId = auth.member!.memberId, capture = auth.captureMemberRequest
  const [query, setQuery] = useState<LogQuery>(initialQuery)
  const [input, setInput] = useState<LogFilters>(initialQuery)
  const [validation, setValidation] = useState("")
  const [retry, setRetry] = useState(0)
  const adjusted = useRef(false),generation=useRef(0),request=useRef<AbortController|null>(null)
  const [dateReset,setDateReset]=useState(0)
  const [lastSuccess,setLastSuccess]=useState<{data:OperationLogPage;query:LogQuery}|null>(null)
  const [result, setResult] = useState<{ query: LogQuery; attempt: number; revision:number; data?: OperationLogPage; error?: string; forbidden?: boolean } | null>(null)
  const workspace=useRef<HTMLDivElement>(null),listPosition=useRef(0),returnFocus=useRef<number|null>(null)
  const [compact,setCompact]=useState(false),[expanded,setExpanded]=useState(false),[detailView,setDetailView]=useState(true)
  useLayoutEffect(()=>{
    const node=workspace.current;if(!node)return
    const observer=new ResizeObserver(([entry])=>setCompact(entry.contentRect.width<1100))
    observer.observe(node);return()=>observer.disconnect()
  },[])
  const [selectedId,setSelectedId]=useState(initialSelected)
  const [urlSelected,setUrlSelected]=useState(initialSelected)
  // Native history changes update URL selection; local filter changes clear it atomically with the query.
  if(urlSelected!==initialSelected){setUrlSelected(initialSelected);setSelectedId(initialSelected)}
  const select=(log:OperationLog)=>requestExit(()=>{listPosition.current=window.scrollY;setDetailView(true);setSelectedId(log.logId);history.pushState({},"",adminLogHref(query,log.logId))})
  const closeSelection=()=>requestExit(()=>{returnFocus.current=selectedId;setSelectedId(null);history.pushState({},"",adminLogHref(query))})
  useEffect(() => { if(scope.isActive()&&location.pathname==="/admin/operation-logs") syncAdminListUrl(adminLogHref(query,selectedId)) },[query,scope,selectedId])
  useEffect(() => {
    if(location.pathname!=="/admin/operation-logs")return
    const controller = new AbortController(), navigation = scope.begin(), session = capture(memberId)
    request.current=controller
    const owner=generation.current
    const current = () => owner===generation.current && !controller.signal.aborted && navigation() && session.isCurrent() && readCurrent(revision)
    void getOperationLogs(query, controller.signal).then(data => {
      if (!current()) return
      // At most one correction per explicit search/page action, even if counts keep changing.
      if (!adjusted.current && query.page > 0 && query.page >= data.totalPages) {
        adjusted.current = true
        setQuery({ ...query, page: Math.max(0, data.totalPages - 1) })
        return
      }
      setLastSuccess({data,query})
      setResult({ query, attempt: retry, revision, data })
    }).catch(error => {
      if (!current()) return
      if (error instanceof AdminApiError && error.status === 401) { setLastSuccess(null); session.expire(); return }
      const forbidden = error instanceof AdminApiError && error.status === 403
      if(forbidden){deny();return}
      if(forbidden||error instanceof AdminApiError&&error.status===404)setLastSuccess(null)
      setResult(previous => ({ query, attempt: retry, revision, forbidden,
        data: forbidden ? undefined : previous?.data,
        error: error instanceof Error ? error.message : "운영 기록을 불러오지 못했습니다." }))
    })
    return () => controller.abort()
  }, [query, retry, memberId, capture, scope,deny,revision,readCurrent])
  const active = result?.query === query && result.attempt === retry && result.revision===revision ? result : null
  const busy = !active
  const change = (next: LogQuery) => requestExit(() => {
    const same=(Object.keys(DEFAULT_LOG_QUERY) as (keyof LogQuery)[]).every(key=>query[key]===next[key])
    if(same&&!active?.error)return
    setSelectedId(null)
    syncAdminListUrl(adminLogHref(next))
    adjusted.current = false
    generation.current++;request.current?.abort()
    if(same)setRetry(n=>n+1)
    else setQuery({...next})
  })
  const shown=lastSuccess?.data,displayQuery=lastSuccess?.query??query,stale=busy||!!active?.error
  const selectedLog=selectedId?shown?.items.find(log=>log.logId===selectedId):undefined
  const single=!!selectedLog&&(expanded||compact)
  const showingDetail=single&&detailView
  const previousDetail=useRef(false)
  useLayoutEffect(()=>{
    if(showingDetail&&!previousDetail.current){window.scrollTo({top:(workspace.current?.getBoundingClientRect().top??0)+window.scrollY-16,behavior:'instant'})}
    if(!showingDetail&&(previousDetail.current||returnFocus.current)){if(previousDetail.current)window.scrollTo({top:listPosition.current,behavior:'instant'});document.getElementById(`log-select-${returnFocus.current??selectedId}`)?.focus({preventScroll:true});returnFocus.current=null}
    previousDetail.current=showingDetail
  },[showingDetail,selectedId])
  const changeLayout=()=>{if(!single&&!expanded)listPosition.current=window.scrollY;setExpanded(v=>!v);setDetailView(true)}
  const showSelected=()=>{listPosition.current=window.scrollY;setDetailView(true)}
  const edit = (next: LogFilters) => { setInput(next); setValidation("") }
  const filtered = !!displayQuery.reasonType || displayQuery.targetType !== "ALL" || displayQuery.action !== "ALL" || !!displayQuery.startDate || !!displayQuery.endDate
  const sortControl = <div className="admin-sort-control"><label htmlFor="admin-log-sort">처리 시각 정렬</label><select id="admin-log-sort" value={query.sort} onChange={e=>{const sort=e.target.value;if(sort==="LATEST"||sort==="OLDEST")change({...query,sort,page:0})}}>{Object.entries(LOG_SORTS).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></div>
  return <div className="admin-operation-view">
    {!selectedId&&recoveryFeedback}<section ref={workspace} className="admin-panel admin-workspace" aria-label="운영 기록 조회">
      <div className="admin-compact-toolbar" hidden={showingDetail}><form className="admin-filters admin-log-filters" noValidate onSubmit={event => {
        event.preventDefault()
        const next={...query,targetType:input.targetType,action:input.action,reasonType:input.reasonType,page:0}
        const error = logFilterError(next)
        setValidation(error)
        if (!error) change(next)
      }}>
        <div className="admin-log-select"><label htmlFor="admin-log-target">대상</label><select id="admin-log-target" value={input.targetType} onChange={event => {
          const value = event.target.value
          if (value === "ALL" || value === "POST" || value === "COMMENT") edit({ ...input, targetType: value })
        }}>{Object.entries(LOG_TARGETS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>
        <div className="admin-log-select"><label htmlFor="admin-log-action">조치</label><select id="admin-log-action" value={input.action} onChange={event => {
          const value = event.target.value
          if (value === "ALL" || value === "HIDE" || value === "RESTORE") edit({ ...input, action: value })
        }}>{Object.entries(LOG_ACTIONS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>
        <div className="admin-log-select"><label htmlFor="admin-log-reason">사유 유형</label><select id="admin-log-reason" value={input.reasonType} onChange={event => {
          const value = event.target.value
          if (value === "" || isModerationReasonType(value)) edit({ ...input, reasonType: value })
        }}><option value="">전체 사유</option>{Object.entries(MODERATION_REASONS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>
        <button type="submit">검색</button>
      </form>
      <AdminDateRange key={dateReset} label="처리" applied={{startDate:query.startDate,endDate:query.endDate}} onApply={range=>change({...query,...range,page:0})}/>
      <div className="admin-filter-footer"><button type="button" className="admin-filter-clear" title="모든 검색 조건 초기화" onClick={() => { setInput(DEFAULT_LOG_QUERY); setDateReset(n=>n+1); setValidation(""); change(DEFAULT_LOG_QUERY) }}><span aria-hidden="true">↻</span> 전체 초기화</button></div>
      {validation && <p role="alert" id="admin-log-date-error">{validation}</p>}
      </div><div className="admin-master-detail admin-log-workspace" data-single={single} data-compact={compact}><div className="admin-master-list" hidden={showingDetail}>{single&&<button type="button" className="admin-log-return-selection" onClick={showSelected}>선택한 상세 보기</button>}<div className="admin-results" aria-busy={busy}>
        <div className="admin-result-toolbar">{shown&&<p role="status">{stale?"이전 결과":"검색 결과"} <strong>{shown.totalElements.toLocaleString("ko-KR")}</strong>개</p>}{sortControl}</div>
        {shown&&<p className="admin-refresh-notice" role="status" style={{visibility:busy?"visible":"hidden"}}>목록을 갱신하고 있습니다…</p>}
        {busy&&!shown&&<p role="status">운영 기록을 불러오고 있습니다…</p>}
        {active?.error&&<div role="alert">{shown&&<p>갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.</p>}<p>{active.error}</p>{!active.forbidden&&<button onClick={()=>setRetry(n=>n+1)}>다시 시도</button>}</div>}
        {shown&&<>
          <div className="admin-log-summary"><p>한국 시간 기준</p></div>
          <p className="admin-log-help">조치는 처리 당시의 기록이며, 현재 콘텐츠 상태와 다를 수 있습니다.</p>
          {!shown.items.length ? <p className="admin-log-empty">{shown.totalElements > 0 ? "이 페이지에는 기록이 없습니다. 다른 페이지를 선택해 주세요." : filtered ? "검색 결과가 없습니다." : "운영 기록이 없습니다."}</p> :
            <table className="admin-log-table" role="table">
              <caption className="sr-only">운영 기록 목록 (처리 시각 {LOG_SORTS[displayQuery.sort]})</caption>
              <thead role="rowgroup"><tr role="row">{["처리 시각", "콘텐츠 작성자", "내용", "조치", "처리 사유"].map(label => <th role="columnheader" scope="col" key={label}>{label}</th>)}</tr></thead>
              <tbody role="rowgroup">{shown.items.map(log => <tr role="row" id={`log-select-${log.logId}`} tabIndex={stale?-1:0} aria-label={`기록 #${log.logId} 상세`} aria-selected={selectedId===log.logId} onKeyDown={event=>{if(!stale&&isAdminRowKeyboardSelection(event))select(log)}} key={log.logId} data-log-id={log.logId} data-selected={selectedId===log.logId} data-selectable={!stale} onClick={event=>{if(!stale&&isAdminRowSelection(event))select(log)}}>
                <td role="cell" data-label="처리 시각"><time dateTime={log.createdAt}>{writtenTime(log.createdAt).exact.replace(" (한국 시간)", "")}</time></td>
                <td role="cell" data-label="콘텐츠 작성자"><div>{log.currentTarget?.author.nickname??"—"}{log.currentTarget?.author.withdrawn&&<span className="admin-log-secondary">탈퇴 회원</span>}</div></td>
                <td role="cell" data-label="내용"><div className="admin-log-content">{log.currentTarget ? <>{log.targetType==="POST"&&<strong>{log.currentTarget.title}</strong>}<p className="admin-log-excerpt">{log.currentTarget.contentPreview||"본문 미리보기가 없습니다."}</p></> : <p>현재 내용을 조회할 수 없습니다</p>}<small>{log.targetType === "POST" ? <Link href={`/admin/posts/${log.targetId}?from=${encodeURIComponent(adminLogHref(query,log.logId))}`} onNavigate={event=>{event.preventDefault();if(!stale&&!window.getSelection()?.toString())select(log)}}>게시글 #{log.targetId}</Link> : `댓글 #${log.targetId}`}</small></div></td>
                <td role="cell" data-label="조치"><span className={`admin-badge ${log.action === "HIDE" ? "is-hidden" : "is-visible"}`}>{LOG_ACTIONS[log.action]}</span></td>
                <td role="cell" data-label="처리 사유" className="admin-log-reason"><div><strong>{MODERATION_REASONS[log.reasonType]}</strong><div className="admin-log-inline-reason">
                  {!(log.reasonType !== "LEGACY" && log.reasonDetail === null && log.reason === MODERATION_REASONS[log.reasonType]) && <p>{log.reason}</p>}{log.reasonDetail !== null && log.reasonDetail !== log.reason && <p>상세: {log.reasonDetail}</p>}
                </div></div></td>
              </tr>)}</tbody>
            </table>}
        </>}
      </div>
      {shown && !active?.forbidden && <AdminPagination label="운영 기록 페이지" data={shown} busy={busy} failed={!!active?.error} disabled={readLocked}
        onPage={page => { if (!busy && !readLocked && !active?.error) change({ ...query, page }) }} />}
      </div>
      {selectedId&&!selectedLog&&!busy&&<p role="status">현재 조회 결과에서 선택한 운영 기록을 찾을 수 없습니다. <button onClick={closeSelection}>선택 해제</button></p>}
      {selectedLog?<AdminLogPanel key={selectedLog.logId} log={selectedLog} listHref={adminLogHref(query,selectedLog.logId)} blocked={stale} onClose={closeSelection} hidden={single&&!detailView} expanded={expanded} compact={compact} onLayout={changeLayout} onList={()=>setDetailView(false)}/>:!selectedId&&!compact&&<aside className="admin-log-panel admin-preview-empty">기록을 선택하면 현재 내용과 운영 조치를 확인할 수 있습니다.</aside>}
      {selectedId&&!selectedLog&&!busy&&completion&&<p role="status">{completion.message} 선택한 기록이 현재 페이지에 포함되지 않습니다.</p>}

      </div>
    </section>
  </div>
}
