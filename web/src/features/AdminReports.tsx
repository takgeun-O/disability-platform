"use client"
// URL owns applied conditions and selection. Never rewrite it from an old screen effect.
// Selection-only URL changes retain the same authenticated list's geometry
// while its new request is pending. A filter change never reuses this snapshot.
import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react"
import { useSearchParams } from "next/navigation"
import { useAuth } from "@/components/AuthProvider"
import {
  useNavigationGuard,
  useNavigationRequest,
} from "@/components/NavigationGuard"
import CommentSuccessToast from "@/components/community/CommentSuccessToast"
import "@/styles/community.css"
import AdminReportSummary, { useReportSummary } from "@/components/admin/AdminReportSummary"
import AdminPagination from "@/components/admin/AdminPagination"
import AdminReportDetail, { type ReportSuccessNotice } from "@/components/admin/AdminReportDetail"
import { AdminApiError } from "@/lib/admin-posts"
import { dateRangeError } from "@/lib/admin-dates"
import { writtenTime } from "@/lib/written-time"
import {
  REPORT_REASONS,
  REPORT_STATUSES,
  REPORT_TARGET_STATUSES,
  defaultReportQuery,
  reportQuery,
  reportHref,
  getReports,
  ReportMutationError,
  type ReportQuery,
  type ReportPage,
} from "@/lib/content-reports"

type ListSnapshot = {
  key: string
  data: ReportPage
}
export default function AdminReports() {
  const auth = useAuth()
  const [toast, setToast] = useState<ReportSuccessNotice | null>(null)
  const dismissToast = useCallback(() => setToast(null), [])
  const params = useSearchParams()
  const [snapshot, setSnapshot] = useState<ListSnapshot | null>(null)
  const remember = useCallback(
    (key: string, data: ReportPage) => setSnapshot({ key, data }),
    [],
  )
  const [denied, setDenied] = useState(false)
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const protect = useCallback((failure: unknown) => {
    if (failure instanceof AdminApiError || failure instanceof ReportMutationError) {
      if (failure.status === 401) {
        dismissToast()
        setSnapshot(null)
        capture(actor).expire()
        return true
      }
      if (failure.status === 403 && (!(failure instanceof ReportMutationError) ||
        ["ADMIN_REQUIRED", "ACCESS_DENIED"].includes(failure.code))) {
        dismissToast()
        setSnapshot(null)
        setDenied(true)
        return true
      }
    }
    return false
  }, [actor, capture, dismissToast])
  const summary = useReportSummary(protect)
  const refreshSummary = summary.refresh
  const notify = useCallback((event: ReportSuccessNotice) => {
    setToast(previous => previous?.key === event.key ? previous : event)
    refreshSummary()
  }, [refreshSummary])
  if (denied) return <div role="alert">
    <h1>관리자 권한이 없습니다</h1>
    <p>이전 신고 정보는 제거되었습니다.</p>
  </div>
  const queryKey = reportHref(
    reportQuery(new URLSearchParams(params.toString())),
  )
  return (
    <>
      <div
        role="status"
        aria-atomic="true"
        className="comment-toast-announcer report-toast"
      >
        {toast &&
          auth.member?.role === "ADMIN" &&
          toast.valid() && (
            <CommentSuccessToast
              key={toast.key}
              message={toast.message}
              onDismiss={dismissToast}
            />
          )}
      </div>
      <ReportsScreen
        notify={notify}
        protect={protect}
        summary={<AdminReportSummary {...summary} />}
        key={params.toString()}
        params={new URLSearchParams(params.toString())}
        initialList={
          snapshot?.key === queryKey ? snapshot.data : null
        }
        remember={remember}
      />
    </>
  )
}
function ReportsScreen({
  params,
  initialList,
  remember,
  notify,
  protect,
  summary,
}: {
  notify: (event: ReportSuccessNotice) => void
  protect: (error: unknown) => boolean
  summary: ReactNode
  params: URLSearchParams
  initialList: ReportPage | null
  remember: (key: string, data: ReportPage) => void
}) {
  const auth = useAuth(),
    guard = useNavigationGuard(),
    scope = useNavigationRequest()
  const [query] = useState(() => reportQuery(params)),
    [dates, setDates] = useState({
      startDate: query.startDate,
      endDate: query.endDate,
    }),
    [dateError, setDateError] = useState("")
  const raw = params.get("selected"),
    selected =
      raw && /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(Number(raw))
        ? Number(raw)
        : null
  const queryKey = reportHref(query)
  const [list, setList] = useState<ReportPage | null>(initialList),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true)
  const [showList, setShowList] = useState(false)
  const uid = useId(),
    actor = auth.member!.memberId,
    capture = auth.captureMemberRequest
  const queryRef = useRef(query)
  const refresh = useCallback(async () => {
    const current = scope.begin(),
      session = capture(actor)
    try {
      const data = await getReports(
        queryRef.current,
        AbortSignal.timeout(15000),
      )
      if (!current() || !session.isCurrent()) return
      if (data.page > 0 && data.page >= data.totalPages) {
        history.replaceState(
          {},
          "",
          reportHref(
            {
              ...queryRef.current,
              page: Math.max(0, data.totalPages - 1),
            },
            selected,
          ),
        )
        return
      }
      setError("")
      remember(queryKey, data)
      setList(data)
    } catch (e) {
      if (current() && session.isCurrent()) {
        if (!protect(e))
          setError("신고 목록을 불러오지 못했습니다. 다시 시도해 주세요.")
        throw e
      }
    } finally {
      if (current() && session.isCurrent()) setLoading(false)
    }
  }, [scope, capture, actor, protect, selected, remember, queryKey])
  useEffect(() => {
    const controller = new AbortController(),
      current = scope.begin(),
      session = capture(actor)
    getReports(query, controller.signal)
      .then((data) => {
        if (!current() || !session.isCurrent()) return
        if (data.page > 0 && data.page >= data.totalPages) {
          history.replaceState(
            {},
            "",
            reportHref(
              {
                ...query,
                page: Math.max(0, data.totalPages - 1),
              },
              selected,
            ),
          )
          return
        }
        remember(queryKey, data)
        setList(data)
        setLoading(false)
      })
      .catch((e) => {
        if (current() && session.isCurrent() && !controller.signal.aborted) {
          if (!protect(e))
            setError("신고 목록을 불러오지 못했습니다. 다시 시도해 주세요.")
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [query, selected, scope, capture, actor, protect, remember, queryKey])
  useLayoutEffect(() => {
    if (!selected && !loading && history.state?.reportFocus) {
      const row = document.querySelector<HTMLElement>(
        `[data-report-select="${history.state.reportFocus}"]`,
      )
      ;(row ?? document.getElementById("report-list-title"))?.focus({
        preventScroll: true,
      })
    }
  }, [selected, loading])
  function navigate(q: ReportQuery, id: number | null = null, replace = false) {
    const href = reportHref(q, id)
    // No navigation event for an identical destination: keep the request scope alive.
    if (location.pathname + location.search === href) return
    guard.request(() => {
      if (replace) history.replaceState({ reportFocus: selected }, "", href)
      else if (location.pathname + location.search !== href)
        history.pushState({}, "", href)
    })
  }
  return (
    <section className="admin-reports">
      <header className="admin-heading">
        <h1>신고 관리</h1>
        <p>신고 당시 내용과 현재 콘텐츠를 확인하고 신고를 처리합니다.</p>
      </header>
      {summary}
      <form
        className="report-filters"
        onSubmit={(e) => {
          e.preventDefault()
          const issue = dateRangeError(dates)
          setDateError(issue)
          if (!issue) navigate({ ...query, ...dates, page: 0 })
        }}
      >
        <label>
          대상
          <select
            aria-label="대상"
            value={query.targetType}
            onChange={(e) =>
              navigate({
                ...query,
                targetType: e.target.value as ReportQuery["targetType"],
                page: 0,
              })
            }
          >
            <option value="ALL">전체</option>
            <option value="POST">게시글</option>
            <option value="COMMENT">댓글</option>
          </select>
        </label>
        <label>
          신고 상태
          <select
            aria-label="신고 상태"
            value={query.status}
            onChange={(e) =>
              navigate({
                ...query,
                status: e.target.value as ReportQuery["status"],
                page: 0,
              })
            }
          >
            <option value="ALL">전체</option>
            {Object.entries(REPORT_STATUSES).map(([v, l]) => (
              <option key={v} value={v}>
                {v === "RECEIVED" ? "접수(미처리)" : l}
              </option>
            ))}
          </select>
        </label>
        <label>
          신고 사유
          <select
            aria-label="신고 사유"
            value={query.reasonType}
            onChange={(e) =>
              navigate({
                ...query,
                reasonType: e.target.value as ReportQuery["reasonType"],
                page: 0,
              })
            }
          >
            <option value="">전체</option>
            {Object.entries(REPORT_REASONS).map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        </label>
        <label>
          접수 시작일
          <input
            type="date"
            value={dates.startDate}
            aria-describedby={dateError ? `${uid}-date-error` : undefined}
            onChange={(e) => setDates({ ...dates, startDate: e.target.value })}
          />
        </label>
        <label>
          접수 종료일
          <input
            type="date"
            value={dates.endDate}
            aria-describedby={dateError ? `${uid}-date-error` : undefined}
            onChange={(e) => setDates({ ...dates, endDate: e.target.value })}
          />
        </label>
        <button type="submit">기간 적용</button>
        <button
          type="button"
          onClick={() => {
            setDates({ startDate: "", endDate: "" })
            setDateError("")
            navigate({ ...defaultReportQuery })
          }}
        >
          전체 초기화
        </button>
        {dateError && (
          <p id={`${uid}-date-error`} role="alert">
            {dateError}
          </p>
        )}
      </form>
      <div
        className="report-workspace"
        data-selected={!!selected}
        data-show-list={showList}
      >
        <section className="report-list" aria-busy={loading}>
          <header>
            <h2 id="report-list-title" tabIndex={-1}>
              신고 목록 {list && <span>총 {list.totalElements}건</span>}
            </h2>
            <label>
              접수일 정렬
              <select
                aria-label="접수일 정렬"
                value={query.sort}
                onChange={(e) =>
                  navigate({
                    ...query,
                    sort: e.target.value as ReportQuery["sort"],
                    page: 0,
                  })
                }
              >
                <option value="LATEST">최신순</option>
                <option value="OLDEST">오래된순</option>
              </select>
            </label>
          </header>
          {selected && (
            <button
              className="report-mobile-back"
              onClick={() => setShowList(false)}
            >
              선택한 상세 보기
            </button>
          )}
          <p className="report-list-notice" role="status">
            {loading
              ? list
                ? "목록을 갱신하고 있습니다. 이전 결과입니다."
                : "신고 목록을 불러오고 있습니다…"
              : "요약은 신고 접수 당시 내용입니다. · 한국 시간(KST)"}
          </p>
          {error && (
            <div role="alert">
              <p>{error}</p>
              <button
                onClick={() => {
                  setLoading(true)
                  void refresh().catch(() => {})
                }}
              >
                목록 다시 시도
              </button>
            </div>
          )}
          {list && (
            <>
              <table>
                <thead>
                  <tr>
                    <th scope="col" id={`${uid}-target`}>신고·대상</th>
                    <th scope="col" id={`${uid}-reason`}>신고 사유</th>
                    <th scope="col" id={`${uid}-status`}>신고 상태</th>
                    <th scope="col" id={`${uid}-content-status`}>현재 콘텐츠</th>
                    <th scope="col" id={`${uid}-created`}>접수 시각</th>
                  </tr>
                </thead>
                <tbody>
                  {list.items.map((item) => (
                    <tr
                      key={item.reportId}
                      data-selected={
                        item.reportId ===
                        selected
                      }
                      data-status={item.status}
                      onClick={(e) => {
                        if (
                          !(e.target as HTMLElement).closest("button") &&
                          !loading &&
                          !error
                        )
                          navigate(query, item.reportId)
                      }}
                    >
                      <td headers={`${uid}-target`}>
                        <button
                          data-report-select={item.reportId}
                          aria-pressed={item.reportId === selected}
                          disabled={loading || !!error}
                          onClick={() => navigate(query, item.reportId)}
                        >
                          신고 #{item.reportId} ·{" "}
                          {item.targetType === "POST" ? "게시글" : "댓글"} #
                          {item.targetId}
                        </button>
                        <strong className="report-list-title">{item.titleSnapshot}</strong>
                        <p className="report-list-preview">{item.contentSnapshotPreview}</p>
                        {item.currentAuthor && (
                          <small>
                            현재 작성자: {item.currentAuthor.nickname}
                          </small>
                        )}
                      </td>
                      <td headers={`${uid}-reason`} data-label="신고 사유">
                        <span className="report-cell-label" aria-hidden="true">신고 사유:</span>
                        {REPORT_REASONS[item.reasonType]}
                      </td>
                      <td headers={`${uid}-status`} data-label="신고 상태">
                        <span className="report-cell-label" aria-hidden="true">신고 상태:</span>
                        <span
                          className="report-badge"
                          data-status={item.status}
                        >
                          {REPORT_STATUSES[item.status]}
                        </span>
                      </td>
                      <td headers={`${uid}-content-status`} data-label="현재 콘텐츠">
                        <span className="report-cell-label" aria-hidden="true">현재 콘텐츠:</span>
                        <span className="report-content-badge" data-content-status={item.currentTargetStatus ?? "UNAVAILABLE"}>
                          {item.currentTargetStatus === null
                            ? "확인 불가"
                            : REPORT_TARGET_STATUSES[item.currentTargetStatus]}
                        </span>
                      </td>
                      <td headers={`${uid}-created`}>
                        <time dateTime={item.createdAt}>
                          {writtenTime(item.createdAt).exact.replace(
                            " (한국 시간)",
                            "",
                          )}
                        </time>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!list.items.length && <p>조건에 맞는 신고가 없습니다.</p>}
              {list.totalPages > 1 && (
                <AdminPagination
                  label="신고 목록 페이지"
                  data={list}
                  busy={loading}
                  failed={!!error}
                  onPage={(page) => navigate({ ...query, page })}
                />
              )}
            </>
          )}
        </section>
        {selected ? (
          <div className="report-detail-container">
            <div className="report-mobile-back">
              <button
                onClick={() => {
                  setShowList(true)
                  requestAnimationFrame(() =>
                    document
                      .querySelector<HTMLElement>(
                        `[data-report-select="${selected}"]`,
                      )
                      ?.focus({ preventScroll: true }),
                  )
                }}
              >
                목록으로 돌아가기
              </button>
            </div>
            <AdminReportDetail
              id={selected}
              query={query}
              returnHref={reportHref(query, selected)}
              absent={
                !!list &&
                !loading &&
                !list.items.some((i) => i.reportId === selected)
              }
              refreshList={refresh}
              notify={notify}
              showStatus={(status) =>
                navigate({ ...query, status, page: 0 }, selected)
              }
              protect={protect}
              close={() => navigate(query, null, true)}
            />
          </div>
        ) : (
          <aside className="report-empty">
            신고를 선택하면 상세 내용을 확인할 수 있습니다.
          </aside>
        )}
      </div>
    </section>
  )
}
