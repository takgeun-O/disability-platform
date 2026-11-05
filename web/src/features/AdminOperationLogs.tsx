"use client"
import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import AdminPagination from "@/components/admin/AdminPagination"
import { AdminApiError } from "@/lib/admin-posts"
import { writtenTime } from "@/lib/written-time"
import { DEFAULT_LOG_QUERY, LOG_TARGETS, LOG_ACTIONS, getOperationLogs, logFilterError, type LogFilters, type LogQuery, type OperationLogPage } from "@/lib/admin-operation-logs"
import "@/styles/admin-operation-logs.css"

export default function AdminOperationLogs() {
  const auth = useAuth(), scope = useNavigationRequest()
  const memberId = auth.member!.memberId, capture = auth.captureMemberRequest
  const [query, setQuery] = useState<LogQuery>(DEFAULT_LOG_QUERY)
  const [input, setInput] = useState<LogFilters>(DEFAULT_LOG_QUERY)
  const [validation, setValidation] = useState("")
  const [retry, setRetry] = useState(0)
  const adjusted = useRef(false)
  const [result, setResult] = useState<{ query: LogQuery; attempt: number; data?: OperationLogPage; error?: string; forbidden?: boolean } | null>(null)
  useEffect(() => {
    const controller = new AbortController(), navigation = scope.begin(), session = capture(memberId)
    const current = () => !controller.signal.aborted && navigation() && session.isCurrent()
    void getOperationLogs(query, controller.signal).then(data => {
      if (!current()) return
      // At most one correction per explicit search/page action, even if counts keep changing.
      if (!adjusted.current && query.page > 0 && query.page >= data.totalPages) {
        adjusted.current = true
        setQuery({ ...query, page: Math.max(0, data.totalPages - 1) })
        return
      }
      setResult({ query, attempt: retry, data })
    }).catch(error => {
      if (!current()) return
      if (error instanceof AdminApiError && error.status === 401) { session.expire(); return }
      const forbidden = error instanceof AdminApiError && error.status === 403
      setResult(previous => ({ query, attempt: retry, forbidden,
        data: forbidden ? undefined : previous?.data,
        error: error instanceof Error ? error.message : "운영 기록을 불러오지 못했습니다." }))
    })
    return () => controller.abort()
  }, [query, retry, memberId, capture, scope])
  const active = result?.query === query && result.attempt === retry ? result : null
  const busy = !active
  const change = (next: LogQuery) => {
    adjusted.current = false
    if (JSON.stringify(query) === JSON.stringify(next)) { if (!busy) setRetry(n => n + 1) }
    else setQuery(next)
  }
  const edit = (next: LogFilters) => { setInput(next); setValidation("") }
  const filtered = query.targetType !== "ALL" || query.action !== "ALL" || !!query.startDate || !!query.endDate
  return <div className="admin-operation-view">
    <header className="admin-heading"><p>관리자</p><h1>운영 기록</h1><p>게시글과 댓글의 숨김·복원 처리 내역을 확인하세요.</p></header>
    <section className="admin-panel" aria-label="운영 기록 조회">
      <form className="admin-filters admin-log-filters" noValidate onSubmit={event => {
        event.preventDefault()
        const error = logFilterError(input)
        setValidation(error)
        if (!error) change({ ...input, page: 0 })
      }}>
        <div className="admin-log-select"><label htmlFor="admin-log-target">대상</label><select id="admin-log-target" value={input.targetType} onChange={event => {
          const value = event.target.value
          if (value === "ALL" || value === "POST" || value === "COMMENT") edit({ ...input, targetType: value })
        }}>{Object.entries(LOG_TARGETS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>
        <div className="admin-log-select"><label htmlFor="admin-log-action">조치</label><select id="admin-log-action" value={input.action} onChange={event => {
          const value = event.target.value
          if (value === "ALL" || value === "HIDE" || value === "RESTORE") edit({ ...input, action: value })
        }}>{Object.entries(LOG_ACTIONS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></div>
        <label>시작일<input type="text" inputMode="numeric" placeholder="YYYY-MM-DD" value={input.startDate}
          aria-invalid={!!validation} aria-describedby={validation ? "admin-log-date-error" : "admin-log-date-help"}
          onChange={event => edit({ ...input, startDate: event.target.value })} /></label>
        <label>종료일<input type="text" inputMode="numeric" placeholder="YYYY-MM-DD" value={input.endDate}
          aria-invalid={!!validation} aria-describedby={validation ? "admin-log-date-error" : "admin-log-date-help"}
          onChange={event => edit({ ...input, endDate: event.target.value })} /></label>
        <button type="submit">검색</button>
        <button type="button" onClick={() => { setInput(DEFAULT_LOG_QUERY); setValidation(""); change(DEFAULT_LOG_QUERY) }}>초기화</button>
      </form>
      <p id="admin-log-date-help" className="admin-log-help">날짜는 YYYY-MM-DD 형식으로 입력해 주세요. 시작일과 종료일은 각각 생략할 수 있습니다.</p>
      {validation && <p role="alert" id="admin-log-date-error">{validation}</p>}
      <div className="admin-results" aria-busy={busy}>
        {busy ? <p role="status">운영 기록을 불러오고 있습니다…</p> : active.error ? <div role="alert"><p>{active.error}</p>
          {!active.forbidden && <button onClick={() => setRetry(n => n + 1)}>다시 시도</button>}</div> : active.data && <>
          <div className="admin-log-summary"><p role="status">검색 결과 <strong>{active.data.totalElements.toLocaleString("ko-KR")}</strong>개</p><p>한국 시간 기준</p></div>
          <p className="admin-log-help">조치는 처리 당시의 기록이며, 현재 콘텐츠 상태와 다를 수 있습니다.</p>
          {!active.data.items.length ? <p className="admin-log-empty">{active.data.totalElements > 0 ? "이 페이지에는 기록이 없습니다. 다른 페이지를 선택해 주세요." : filtered ? "검색 결과가 없습니다." : "운영 기록이 없습니다."}</p> :
            <table className="admin-log-table" role="table">
              <caption className="sr-only">운영 기록 목록 (처리 시각 내림차순)</caption>
              <thead role="rowgroup"><tr role="row">{["처리 시각", "처리 관리자", "대상", "조치", "처리 사유"].map(label => <th role="columnheader" scope="col" key={label}>{label}</th>)}</tr></thead>
              <tbody role="rowgroup">{active.data.items.map(log => <tr role="row" key={log.logId} data-log-id={log.logId}>
                <td role="cell" data-label="처리 시각"><time dateTime={log.createdAt}>{writtenTime(log.createdAt).exact.replace(" (한국 시간)", "")}</time></td>
                <td role="cell" data-label="처리 관리자">회원 #{log.actorMemberId}</td>
                <td role="cell" data-label="대상">{log.targetType === "POST" ? <Link href={`/admin/posts/${log.targetId}`}>게시글 #{log.targetId}</Link> : `댓글 #${log.targetId}`}</td>
                <td role="cell" data-label="조치"><span className={`admin-badge ${log.action === "HIDE" ? "is-hidden" : "is-visible"}`}>{LOG_ACTIONS[log.action]}</span></td>
                <td role="cell" data-label="처리 사유" className="admin-log-reason">{log.reason}</td>
              </tr>)}</tbody>
            </table>}
        </>}
      </div>
      {result?.data && !active?.forbidden && <AdminPagination label="운영 기록 페이지" data={result.data} busy={busy} failed={!!active?.error}
        onPage={page => { if (!busy && !active?.error) change({ ...query, page }) }} />}
    </section>
  </div>
}
