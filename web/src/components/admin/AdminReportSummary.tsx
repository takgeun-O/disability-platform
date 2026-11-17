"use client"
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { AdminApiError } from "@/lib/admin-posts"
import { getReportSummary, type ReportSummary } from "@/lib/admin-report-summary"
import { writtenTime } from "@/lib/written-time"
import "@/styles/admin-dashboard.css"

const number = new Intl.NumberFormat("ko-KR")
const cards = [
  ["pendingReportCount", "처리 대기", "전체 기간 · 접수 상태"],
  ["todayReportCount", "오늘 접수", "오늘 접수된 전체 신고"],
  ["todayHandledReportCount", "오늘 종결", "처리 완료·기각 포함"],
] as const

// Owned by the report-management page, outside its URL-keyed list/detail screen.
// Filter changes must neither restart this GET nor invalidate its pending response.
export function useReportSummary(protect: (error: unknown) => boolean) {
  const auth = useAuth()
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const [data, setData] = useState<ReportSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const request = useRef<AbortController | null>(null)
  const load = useCallback(() => {
    request.current?.abort()
    const controller = new AbortController()
    request.current = controller
    const session = capture(actor)
    const valid = () => request.current === controller &&
      !controller.signal.aborted && session.isCurrent()
    void getReportSummary(AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]))
      .then(latest => {
        if (valid()) setData(latest)
      })
      .catch((failure: unknown) => {
        if (!valid()) return
        if (protect(failure)) {
          setData(null)
          return
        }
        setError(failure instanceof AdminApiError
          ? failure.message
          : "신고 요약을 불러오지 못했습니다. 다시 조회해 주세요.")
      })
      .finally(() => {
        if (valid()) setLoading(false)
        if (request.current === controller) request.current = null
      })
  }, [actor, capture, protect])
  useLayoutEffect(() => () => request.current?.abort(), [])
  useEffect(() => {
    load()
    return () => { request.current?.abort() }
  }, [load])
  const refresh = useCallback(() => {
    setLoading(true)
    setError("")
    load()
  }, [load])
  const manualRefresh = () => {
    if (!loading && !request.current) refresh()
  }
  return { data, loading, error, refresh, manualRefresh }
}

export default function AdminReportSummary({ data, loading, error, manualRefresh }: ReturnType<typeof useReportSummary>) {
  return <section className="report-summary" aria-label="신고 요약">
    <div className="report-summary-heading">
      <div className="report-summary-context">
        <p>목록 필터와 무관한 전체 신고 기준</p>
        {data && <div className="report-summary-dates">
          <p>집계 기준 날짜 <time dateTime={data.baseDate}>{data.baseDate.replace(/-/g, ".")}</time></p>
          <p>조회 기준 <time dateTime={data.queriedAt}>{writtenTime(data.queriedAt).exact}</time></p>
        </div>}
      </div>
      <button type="button" aria-disabled={loading} onClick={manualRefresh}>요약 새로고침</button>
    </div>
    <p role="status" className={loading ? "report-summary-status" : "sr-only"}>
      {loading ? data ? "신고 요약을 갱신하고 있습니다. 이전 조회 결과를 표시합니다." : "신고 요약을 불러오고 있습니다…"
        : !error ? "신고 요약 조회를 완료했습니다." : ""}
    </p>
    {error && <div role="alert" className="dashboard-error">
      <p>{error}</p>
      {data && <p>최신 요약 조회에 실패했습니다. 이전 수치와 조회 시각을 표시합니다.</p>}
      <button type="button" aria-disabled={loading} onClick={manualRefresh}>요약 다시 조회</button>
    </div>}
    {data && <div aria-busy={loading}>
      <dl className="dashboard-counts report-summary-counts">
        {cards.map(([key, label, hint]) => <div key={key} className={`dashboard-count${key === "pendingReportCount" ? " is-priority" : ""}`}>
          <dt>{label}</dt>
          <dd>{number.format(data.counts[key])}<span>건</span></dd>
          <dd className="dashboard-count-hint">{hint}</dd>
        </div>)}
      </dl>
    </div>}
  </section>
}
