"use client"
import { isAdminRowSelection, isAdminRowKeyboardSelection } from "@/lib/admin-row-selection"
import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import { useAdminUrlQuery } from "@/lib/useAdminUrlQuery"
import { adminLogQuery, adminLogHref, adminSelectionId } from "@/lib/admin-navigation"
import AdminModeration, { useAdminModeration } from "@/components/admin/AdminModeration"
import AdminSelectionPanel from "@/components/admin/AdminSelectionPanel"
import AdminLogPreview from "@/components/admin/AdminLogPreview"
import AdminDateRange from "@/components/admin/AdminDateRange"
import AdminPagination from "@/components/admin/AdminPagination"
import { AdminApiError } from "@/lib/admin-posts"
import { writtenTime } from "@/lib/written-time"
import { DEFAULT_LOG_QUERY, LOG_SORTS, LOG_TARGETS, LOG_ACTIONS, getOperationLogs, logFilterError, type LogFilters, type LogQuery, type OperationLogPage } from "@/lib/admin-operation-logs"
import "@/styles/admin-operation-logs.css"

import { MODERATION_REASONS, isModerationReasonType } from "@/lib/moderation-reasons"

export default function AdminOperationLogs() {
  const {values,navigation}=useAdminUrlQuery()
  return <AdminModeration key={navigation}><OperationLogResults initialQuery={adminLogQuery(values)} initialSelected={adminSelectionId(values.selected)} /></AdminModeration>
}
function OperationLogResults({initialQuery,initialSelected}:{initialQuery:LogQuery;initialSelected:number|null}) {
  const auth = useAuth(), scope = useNavigationRequest()
  const {deny}=useAdminModeration()
  const [selection,setSelection]=useState<{query:LogQuery;id:number;focus:boolean}|null>(initialSelected?{query:initialQuery,id:initialSelected,focus:false}:null)
  const memberId = auth.member!.memberId, capture = auth.captureMemberRequest
  const [query, setQuery] = useState<LogQuery>(initialQuery)
  const [input, setInput] = useState<LogFilters>(initialQuery)
  const [validation, setValidation] = useState("")
  const [retry, setRetry] = useState(0)
  const adjusted = useRef(false),generation=useRef(0),request=useRef<AbortController|null>(null)
  const [dateReset,setDateReset]=useState(0)
  const [lastSuccess,setLastSuccess]=useState<{data:OperationLogPage;query:LogQuery}|null>(null)
  const [result, setResult] = useState<{ query: LogQuery; attempt: number; data?: OperationLogPage; error?: string; forbidden?: boolean } | null>(null)
  const selectedId=selection?.query===query&&!(result?.query===query&&result.attempt===retry&&result.data&&!result.data.items.some(log=>log.logId===selection.id))?selection.id:null
  useEffect(() => { if(scope.isActive()) window.history.replaceState(null,"",adminLogHref(query,selectedId)) },[query,scope,selectedId])
  useEffect(() => {
    const controller = new AbortController(), navigation = scope.begin(), session = capture(memberId)
    request.current=controller
    const owner=generation.current
    const current = () => owner===generation.current && !controller.signal.aborted && navigation() && session.isCurrent()
    void getOperationLogs(query, controller.signal).then(data => {
      if (!current()) return
      // At most one correction per explicit search/page action, even if counts keep changing.
      if (!adjusted.current && query.page > 0 && query.page >= data.totalPages) {
        adjusted.current = true
        setQuery({ ...query, page: Math.max(0, data.totalPages - 1) })
        return
      }
      setLastSuccess({data,query})
      setResult({ query, attempt: retry, data })
    }).catch(error => {
      if (!current()) return
      if (error instanceof AdminApiError && error.status === 401) { setLastSuccess(null); session.expire(); return }
      const forbidden = error instanceof AdminApiError && error.status === 403
      if(forbidden){deny();return}
      if(forbidden||error instanceof AdminApiError&&error.status===404)setLastSuccess(null)
      setResult(previous => ({ query, attempt: retry, forbidden,
        data: forbidden ? undefined : previous?.data,
        error: error instanceof Error ? error.message : "운영 기록을 불러오지 못했습니다." }))
    })
    return () => controller.abort()
  }, [query, retry, memberId, capture, scope,deny])
  const active = result?.query === query && result.attempt === retry ? result : null
  const busy = !active
  const change = (next: LogQuery) => {
    const same=(Object.keys(DEFAULT_LOG_QUERY) as (keyof LogQuery)[]).every(key=>query[key]===next[key])
    if(same&&!active?.error)return
    adjusted.current = false
    generation.current++;request.current?.abort()
    if(same)setRetry(n=>n+1)
    else setQuery({...next})
  }
  const shown=lastSuccess?.data,displayQuery=lastSuccess?.query??query,stale=busy||!!active?.error
  const selectedLog=shown?.items.find(log=>log.logId===selectedId)
  const edit = (next: LogFilters) => { setInput(next); setValidation("") }
  const filtered = !!displayQuery.reasonType || displayQuery.targetType !== "ALL" || displayQuery.action !== "ALL" || !!displayQuery.startDate || !!displayQuery.endDate
  const sortControl = <div className="admin-sort-control"><label htmlFor="admin-log-sort">처리 시각 정렬</label><select id="admin-log-sort" value={query.sort} onChange={e=>{const sort=e.target.value;if(sort==="LATEST"||sort==="OLDEST")change({...query,sort,page:0})}}>{Object.entries(LOG_SORTS).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></div>
  return <div className="admin-operation-view">
    <header className="admin-heading"><h1>운영 기록</h1><p>게시글과 댓글의 숨김·복원 처리 내역을 확인하세요.</p></header>
    <section className="admin-panel admin-workspace" aria-label="운영 기록 조회">
      <div className="admin-compact-toolbar"><form className="admin-filters admin-log-filters" noValidate onSubmit={event => {
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
      </div><div className="admin-master-detail"><div className="admin-master-list"><div className="admin-results" aria-busy={busy}>
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
              <thead role="rowgroup"><tr role="row">{["처리 시각", "처리 관리자", "대상", "조치", "처리 사유"].map(label => <th role="columnheader" scope="col" key={label}>{label}</th>)}</tr></thead>
              <tbody role="rowgroup">{shown.items.map(log => <tr role="row" id={`log-select-${log.logId}`} tabIndex={stale?-1:0} aria-label={`기록 #${log.logId} 상세`} aria-selected={selectedId===log.logId} onKeyDown={event=>{if(!stale&&isAdminRowKeyboardSelection(event))setSelection({query,id:log.logId,focus:true})}} key={log.logId} data-log-id={log.logId} data-selected={selectedId===log.logId} data-selectable={!stale} onClick={event=>{if(!stale&&isAdminRowSelection(event))setSelection({query,id:log.logId,focus:true})}}>
                <td role="cell" data-label="처리 시각"><time dateTime={log.createdAt}>{writtenTime(log.createdAt).exact.replace(" (한국 시간)", "")}</time></td>
                <td role="cell" data-label="처리 관리자">회원 #{log.actorMemberId}</td>
                <td role="cell" data-label="대상">{log.targetType === "POST" ? <Link href={`/admin/posts/${log.targetId}?from=${encodeURIComponent(adminLogHref(query,log.logId))}`} onNavigate={event=>{event.preventDefault();if(!stale&&!window.getSelection()?.toString())setSelection({query,id:log.logId,focus:true})}}>게시글 #{log.targetId}</Link> : `댓글 #${log.targetId}`}</td>
                <td role="cell" data-label="조치"><span className={`admin-badge ${log.action === "HIDE" ? "is-hidden" : "is-visible"}`}>{LOG_ACTIONS[log.action]}</span></td>
                <td role="cell" data-label="처리 사유" className="admin-log-reason"><div><strong>{MODERATION_REASONS[log.reasonType]}</strong><div className="admin-log-inline-reason">
                  {!(log.reasonType !== "LEGACY" && log.reasonDetail === null && log.reason === MODERATION_REASONS[log.reasonType]) && <p>{log.reason}</p>}{log.reasonDetail !== null && log.reasonDetail !== log.reason && <p>상세: {log.reasonDetail}</p>}
                </div></div></td>
              </tr>)}</tbody>
            </table>}
        </>}
      </div>
      {shown && !active?.forbidden && <AdminPagination label="운영 기록 페이지" data={shown} busy={busy} failed={!!active?.error}
        onPage={page => { if (!busy && !active?.error) change({ ...query, page }) }} />}
      </div>
      {selectedLog?<AdminSelectionPanel key={selectedLog.logId} id={selectedLog.logId} title="운영 기록 상세" focusOnOpen={selection?.focus??false} onClose={()=>{setSelection(null);document.getElementById(`log-select-${selectedLog.logId}`)?.focus({preventScroll:true})}}><AdminLogPreview log={selectedLog} returnHref={adminLogHref(query,selectedLog.logId)}/></AdminSelectionPanel>:<aside className="admin-preview admin-preview-empty"><h2>운영 기록 상세</h2><p>기록을 선택하면 처리 내용을 확인할 수 있습니다.</p></aside>}
      </div>
    </section>
  </div>
}
