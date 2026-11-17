"use client"
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationGuard } from "@/components/NavigationGuard"
import { AdminApiError } from "@/lib/admin-posts"
import { dashboardReportHref, getDashboard, type Dashboard } from "@/lib/admin-dashboard"
import AdminReportDetail, { type ReportSuccessNotice } from "@/components/admin/AdminReportDetail"
import CommentSuccessToast from "@/components/community/CommentSuccessToast"
import { openDashboardReport, closeDashboardReport } from "@/lib/dashboard-report-navigation"
import "@/styles/community.css"
import { REPORT_REASONS, ReportMutationError, type ReportDetail } from "@/lib/content-reports"
import { writtenTime } from "@/lib/written-time"

const number = new Intl.NumberFormat("ko-KR")
const cards = [
  ["pendingReportCount", "처리 대기 신고", "전체 기간"],
  ["todayReportCount", "오늘 접수 신고", "한국 시간 기준"],
  ["todayPostCount", "오늘 작성 게시글", "한국 시간 기준"],
  ["todayMemberCount", "오늘 가입 회원", "한국 시간 기준"],
] as const

export default function AdminDashboard({ modalId = null }: { modalId?: number | null }) {
  const auth = useAuth()
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const previousModal = useRef<number | null>(null)
  const [data, setData] = useState<Dashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [denied, setDenied] = useState(false)
  const request = useRef<AbortController | null>(null)

  const load = useCallback(() => {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    const session = capture(actor)
    const valid = () => !controller.signal.aborted && session.isCurrent()
    return getDashboard(controller.signal).then(latest => {
      if (valid()) { setData(latest); setError("") }
    }).catch((failure: unknown) => {
      if (!valid()) return
      if (failure instanceof AdminApiError && failure.status === 401) {
        setData(null)
        session.expire()
      } else if (failure instanceof AdminApiError && failure.status === 403) {
        setData(null)
        setDenied(true)
      } else {
        setError(failure instanceof AdminApiError ? failure.message : "대시보드를 불러오지 못했습니다. 다시 시도해 주세요.")
      }
    }).finally(() => {
      if (request.current === controller) request.current = null
      if (valid()) setLoading(false)
    })
  }, [actor, capture])

  useLayoutEffect(() => () => { request.current?.abort() }, [])

  useEffect(() => {
    void load()
    return () => {
      request.current?.abort()
      request.current = null
    }
  }, [load])

  const refresh = useCallback(() => {
    // A confirmed action must get a new snapshot, not reuse an older in-flight GET.
    request.current?.abort()
    request.current = null
    setLoading(true)
    setError("")
    void load()
  }, [load])
  const protect = useCallback((failure: unknown) => {
    if (failure instanceof AdminApiError || failure instanceof ReportMutationError) {
      if (failure.status === 401) {
        request.current?.abort()
        setData(null)
        capture(actor).expire()
        return true
      }
      if (failure.status === 403 && (!(failure instanceof ReportMutationError) ||
        ["ADMIN_REQUIRED", "ACCESS_DENIED"].includes(failure.code))) {
        request.current?.abort()
        setData(null)
        setDenied(true)
        return true
      }
    }
    return false
  }, [actor, capture])
  useEffect(() => {
    const previous = previousModal.current
    previousModal.current = modalId
    if (modalId !== null) return
    if (previous !== null) {
      const frame = requestAnimationFrame(() => {
        const link = document.querySelector<HTMLElement>(`[data-dashboard-report="${previous}"]`)
        ;(link ?? document.getElementById("dashboard-pending-title"))?.focus({ preventScroll: true })
      })
      return () => cancelAnimationFrame(frame)
    }
  }, [modalId])
  function manualRefresh() {
    if (!loading && !request.current) refresh()
  }

  if (denied) return <div className="admin-access-card" role="alert">
    <h1>관리자 권한이 없습니다</h1>
    <p>대시보드를 조회할 수 없습니다.</p>
    <Link href="/">서비스로 돌아가기</Link>
  </div>

  return <section className="admin-dashboard" aria-labelledby="dashboard-title">
    <header className="admin-heading dashboard-heading">
      <div>
        <h1 id="dashboard-title">관리자 대시보드</h1>
        <p>운영 현황과 처리 대기 신고를 확인합니다.</p>
      </div>
      <button type="button" aria-disabled={loading} onClick={manualRefresh}>
        {loading ? "조회 중…" : "새로고침"}
      </button>
    </header>
    <p className="dashboard-announcement" role="status">
      {loading
        ? data ? "최신 정보를 조회하고 있습니다. 이전 조회 결과를 표시합니다." : "대시보드를 불러오고 있습니다…"
        : !error && data ? "대시보드 조회를 완료했습니다." : ""}
    </p>
    {error && <div className="dashboard-error" role="alert">
      <p>{error}</p>
      {data && <p>이전 조회 결과입니다. 아래 조회 기준 시각을 확인해 주세요.</p>}
      <button type="button" aria-disabled={loading} onClick={manualRefresh}>다시 조회</button>
    </div>}
    {data && <div aria-busy={loading}>
      <div className="dashboard-dates">
        <p>오늘의 기준 날짜 <time dateTime={data.baseDate}>{data.baseDate.replace(/-/g, ".")}</time> · 한국 시간</p>
        <p>조회 기준 <time dateTime={data.queriedAt}>{writtenTime(data.queriedAt).exact}</time></p>
      </div>
      <dl className="dashboard-counts">
        {cards.map(([key, label, hint]) => <div key={key} className={key === "pendingReportCount" ? "dashboard-count is-priority" : "dashboard-count"}>
          <dt>{label}</dt>
          <dd>{number.format(data.counts[key])}<span>{key === "todayMemberCount" ? "명" : "건"}</span></dd>
          <dd className="dashboard-count-hint">{hint}
          {key === "pendingReportCount" && <Link href={dashboardReportHref()}>접수 신고 보기 →</Link>}
          </dd>
        </div>)}
      </dl>
      <p className="dashboard-policy">오늘 집계는 접수·생성 기준이며, 이후 신고 종결·콘텐츠 숨김·삭제·회원 상태 변경과 관계없이 포함합니다.</p>
      <section className="admin-panel dashboard-pending" aria-labelledby="dashboard-pending-title">
        <div className="dashboard-pending-heading">
          <div>
            <h2 id="dashboard-pending-title" tabIndex={-1}>처리 대기 신고</h2>
            <p>오래된 접수 순 · 최대 5건</p>
          </div>
          <p>전체 {number.format(data.counts.pendingReportCount)}건 · 표시 {data.pendingReports.length}건</p>
        </div>
        {data.pendingReports.length === 0 ? <p className="dashboard-empty">표시할 처리 대기 신고가 없습니다.</p> :
          <table>
            <caption className="dashboard-sr-only">처리 대기 신고 목록 · 접수 시각은 한국 시간</caption>
            <thead><tr>{["신고 번호", "신고 대상", "신고 사유", "접수 시각", "상세"].map(label => <th key={label} scope="col">{label}</th>)}</tr></thead>
            <tbody>{data.pendingReports.map(item => <tr key={item.reportId}>
              <th scope="row"><span className="dashboard-mobile-label" aria-hidden="true">신고 번호</span>#{item.reportId}</th>
              <td><span className="dashboard-mobile-label" aria-hidden="true">신고 대상</span>{item.targetType === "POST" ? "게시글" : "댓글"} #{item.targetId}</td>
              <td><span className="dashboard-mobile-label" aria-hidden="true">신고 사유</span>{REPORT_REASONS[item.reasonType]}</td>
              <td><span className="dashboard-mobile-label" aria-hidden="true">접수 시각</span><time dateTime={item.createdAt}>{writtenTime(item.createdAt).exact}</time></td>
              <td><Link href={dashboardReportHref(item.reportId)} data-dashboard-report={item.reportId} scroll={false}
                onNavigate={event => { event.preventDefault(); openDashboardReport(item.reportId) }} aria-label={`신고 #${item.reportId} 상세 보기`}>상세 보기 →</Link></td>
            </tr>)}</tbody>
          </table>}
      </section>
    </div>}
    {modalId !== null && <DashboardReportModal key={modalId} id={modalId} refresh={refresh}
      protect={protect} loading={loading} error={error} manualRefresh={manualRefresh} />}

  </section>
}

function DashboardReportModal({ id, refresh, protect, loading, error, manualRefresh }: {
  id: number
  refresh: () => void
  protect: (failure: unknown) => boolean
  loading: boolean
  error: string
  manualRefresh: () => void
}) {
  const guard = useNavigationGuard()
  const [toast, setToast] = useState<ReportSuccessNotice | null>(null)
  const dismissToast = useCallback(() => setToast(null), [])
  const terminalRefreshRequested = useRef(false)
  const onServerStatus = useCallback((status: ReportDetail["status"]) => {
    if (status === "RECEIVED" || terminalRefreshRequested.current) return
    // One automatic refresh per modal opening, including confirmed POST + GET.
    // Mark before dispatch; failures remain explicit manual retries, never a loop.
    terminalRefreshRequested.current = true
    refresh()
  }, [refresh])
  const notify = useCallback((event: ReportSuccessNotice) => {
    setToast(event)
  }, [])
  return (
    <AdminReportDetail id={id} presentation="modal"
      returnHref={`/admin/reports/${id}?from=dashboard`} notify={notify} protect={protect} onServerStatus={onServerStatus}
      close={() => guard.request(closeDashboardReport)}
      feedback={<>
        <div role="status" aria-atomic="true" className="comment-toast-announcer report-toast">
          {toast && toast.valid() && <CommentSuccessToast key={toast.key} message={toast.message} onDismiss={dismissToast} />}
        </div>
        {loading && <p role="status">대시보드의 최신 집계를 확인하고 있습니다.</p>}
        {error && <div className="dashboard-error" role="alert">
          <p>신고 처리 결과와 별개로 최신 대시보드 집계를 불러오지 못했습니다. 배경에는 이전 조회 결과가 표시됩니다.</p>
          <button type="button" aria-disabled={loading} onClick={manualRefresh}>대시보드 다시 조회</button>
        </div>}
      </>}
    />
  )
}
