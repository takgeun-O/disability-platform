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
} from "react"
import { useSearchParams } from "next/navigation"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import {
  useNavigationGuard,
  useNavigationRequest,
} from "@/components/NavigationGuard"
import CommentSuccessToast from "@/components/community/CommentSuccessToast"
import "@/styles/community.css"
import OverlayDialog from "@/components/OverlayDialog"
import AdminPagination from "@/components/admin/AdminPagination"
import ReportContent from "@/components/admin/ReportContent"
import AdminSelectionPanel from "@/components/admin/AdminSelectionPanel"
import { AdminApiError } from "@/lib/admin-posts"
import { dateRangeError } from "@/lib/admin-dates"
import { writtenTime } from "@/lib/written-time"
import {
  REPORT_REASONS,
  REPORT_STATUSES,
  REPORT_TARGET_STATUSES,
  reportTargetStatus,
  REPORT_OUTCOMES,
  defaultReportQuery,
  reportQuery,
  reportHref,
  getReports,
  getReport,
  handleReport,
  handlingError,
  handlingText,
  ReportMutationError,
  type ReportQuery,
  type ReportPage,
  type ReportDetail,
  type ReportAction,
} from "@/lib/content-reports"

type ListSnapshot = {
  key: string
  data: ReportPage
}
type SuccessNotice = {
  key: string
  message: string
  valid: () => boolean
}
export default function AdminReports() {
  const auth = useAuth()
  const [toast, setToast] = useState<SuccessNotice | null>(null)
  const dismissToast = useCallback(() => setToast(null), [])
  const notify = useCallback((event: SuccessNotice) => {
    setToast((previous) =>
      previous?.key === event.key ? previous : event,
    )
  }, [])
  const params = useSearchParams()
  const [snapshot, setSnapshot] = useState<ListSnapshot | null>(null)
  const remember = useCallback(
    (key: string, data: ReportPage) => setSnapshot({ key, data }),
    [],
  )
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
        dismissToast={dismissToast}
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
  dismissToast,
}: {
  notify: (event: SuccessNotice) => void
  dismissToast: () => void
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
    [loading, setLoading] = useState(true),
    [denied, setDenied] = useState(false)
  const [showList, setShowList] = useState(false)
  const uid = useId(),
    actor = auth.member!.memberId,
    capture = auth.captureMemberRequest
  const queryRef = useRef(query)
  const protect = useCallback(
    (e: unknown) => {
      if (e instanceof AdminApiError || e instanceof ReportMutationError) {
        if (e.status === 401) {
          dismissToast()
          capture(actor).expire()
          return true
        }
        if (
          e.status === 403 &&
          (!(e instanceof ReportMutationError) ||
            ["ADMIN_REQUIRED", "ACCESS_DENIED"].includes(e.code))
        ) {
          dismissToast()
          setDenied(true)
          return true
        }
      }
      return false
    },
    [capture, actor, dismissToast],
  )
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
  if (denied)
    return (
      <div role="alert">
        <h1>관리자 권한이 없습니다</h1>
        <p>이전 신고 정보는 제거되었습니다.</p>
      </div>
    )
  return (
    <section className="admin-reports">
      <header className="admin-heading">
        <h1>신고 관리</h1>
        <p>신고 당시 내용과 현재 콘텐츠를 확인하고 신고를 처리합니다.</p>
      </header>
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
            <ReportPanel
              id={selected}
              query={query}
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
function ReportPanel({
  id,
  query,
  absent,
  refreshList,
  notify,
  showStatus,
  protect,
  close,
}: {
  id: number
  query: ReportQuery
  absent: boolean
  notify: (event: SuccessNotice) => void
  showStatus: (status: "RESOLVED" | "DISMISSED") => void
  refreshList: () => Promise<void>
  protect: (e: unknown) => boolean
  close: () => void
}) {
  const auth = useAuth(),
    guard = useNavigationGuard(),
    scope = useNavigationRequest(),
    uid = useId()
  const [data, setData] = useState<ReportDetail | null>(null),
    [loading, setLoading] = useState(true),
    [readError, setReadError] = useState(""),
    [listOnlyReadFailure, setListOnlyReadFailure] = useState(false),
    [action, setAction] = useState<ReportAction | null>(null),
    [reason, setReason] = useState(""),
    [error, setError] = useState(""),
    [fieldError, setFieldError] = useState(false),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [needsCheck, setNeedsCheck] = useState(false),
    [localExit, setLocalExit] = useState(false)
  const flight = useRef(false),
    field = useRef<HTMLTextAreaElement>(null),
    generation = useRef(0),
    completed = useRef(false),
    resultHeading = useRef<HTMLHeadingElement>(null),
    actionHeading = useRef<HTMLHeadingElement>(null),
    focusResult = useRef(false)
  const reveal = (element: HTMLElement | null) => {
    if (!element) return
    element.focus({ preventScroll: true })
    const scroller = element.closest(".admin-preview-body")
    if (scroller) scroller.scrollTop += element.getBoundingClientRect().top - scroller.getBoundingClientRect().top - 12
  }
  useLayoutEffect(() => {
    if (focusResult.current && data?.status !== "RECEIVED" && resultHeading.current) {
      focusResult.current = false
      reveal(resultHeading.current)
    }
  }, [data?.status])
  const actor = auth.member!.memberId,
    capture = auth.captureMemberRequest,
    setDirty = guard.setDirty
  useLayoutEffect(() => {
    setDirty(busy || !!reason)
    return () => setDirty(false)
  }, [busy, reason, setDirty])
  const load = useCallback(
    async (both = false) => {
      const current = scope.begin(),
        session = capture(actor),
        ticket = ++generation.current
      const valid = () =>
        current() && session.isCurrent() && ticket === generation.current
      const results = await Promise.allSettled([
        getReport(id, AbortSignal.timeout(15000)),
        ...(both ? [refreshList()] : []),
      ])
      if (!valid()) return
      const failure = results.find((r) => r.status === "rejected")
      const detailSucceeded = results[0].status === "fulfilled"
      // Recompute for each settled read, never infer freshness from error text.
      setListOnlyReadFailure(detailSucceeded && !!failure)
      if (failure?.status === "rejected") {
        if (protect(failure.reason)) return
        setReadError(
          detailSucceeded
            ? "상세 정보는 갱신됐지만 목록을 갱신하지 못했습니다. 다시 조회해 주세요."
            : failure.reason instanceof AdminApiError &&
                failure.reason.status === 404
              ? "신고 기록을 찾을 수 없습니다."
            : completed.current
              ? "조치는 완료됐습니다. 최신 정보를 불러오지 못했습니다. 다시 조회해 주세요."
              : "최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.",
        )
        setNeedsCheck(true)
        if (
          !detailSucceeded &&
          failure.reason instanceof AdminApiError &&
          failure.reason.status === 404
        )
          setData(null)
      } else {
        setReadError("")
        setNeedsCheck(false)
        if (both) {
          setError("")
          setFieldError(false)
        }
      }
      if (results[0].status === "fulfilled") {
        const latest = results[0].value as ReportDetail
        setData(latest)
        if (latest.status !== "RECEIVED") {
          setAction(null)
          setReason("")
          setDirty(false)
        }
      }
      setLoading(false)
    },
    [scope, capture, actor, id, refreshList, protect, setDirty],
  )
  useEffect(() => {
    const controller = new AbortController(),
      current = scope.begin(),
      session = capture(actor)
    getReport(id, controller.signal)
      .then((value) => {
        if (current() && session.isCurrent()) {
          setData(value)
          setLoading(false)
        }
      })
      .catch((e) => {
        if (current() && session.isCurrent() && !controller.signal.aborted) {
          if (!protect(e)) {
            setReadError(
              e instanceof AdminApiError && e.status === 404
                ? "신고 기록을 찾을 수 없습니다."
                : "신고 상세를 불러오지 못했습니다.",
            )
            setNeedsCheck(true)
          }
          setLoading(false)
        }
      })
    return () => controller.abort()
  }, [id, scope, capture, actor, protect])
  function focusError() {
    requestAnimationFrame(() => {
      field.current?.focus({ preventScroll: true })
      const input = field.current,
        container = input?.closest<HTMLElement>(".admin-preview-body")
      const feedback = document.getElementById(`${uid}-error`)
      if (input && container) {
        const bounds = container.getBoundingClientRect(),
          top = input.getBoundingClientRect().top
        const bottom =
          feedback?.getBoundingClientRect().bottom ??
          input.getBoundingClientRect().bottom
        if (top < bounds.top || bottom > bounds.bottom)
          container.scrollTop += top - bounds.top - 12
      }
    })
  }
  async function submit() {
    if (
      !data ||
      data.status !== "RECEIVED" ||
      !action ||
      flight.current ||
      needsCheck ||
      loading ||
      (action === "hide-and-resolve" &&
        (!data.currentTarget || !!data.currentTarget.deletedAt))
    )
      return
    const issue = handlingError(reason)
    if (issue) {
      setError(issue)
      setFieldError(true)
      focusError()
      return
    }
    const navigation = scope.begin(),
      session = capture(actor),
      valid = () => navigation() && session.isCurrent()
    if (!valid()) return
    flight.current = true
    setBusy(true)
    setError("")
    setNotice("")
    let reread = false
    try {
      const result = await handleReport(id, action, reason, valid)
      if (!valid() || !result) return
      completed.current = true
      focusResult.current = true
      // Keep the confirmed result; current target state still comes from GET.
      setData({ ...data, ...result })
      const targetLabel = `${
        data.targetType === "POST" ? "게시글" : "댓글"
      } #${data.targetId}`
      notify({
        key: `${result.reportId}:${result.handledAt}`,
        valid: session.isCurrent,
        message: `신고 #${result.reportId} ${
          result.status === "DISMISSED" ? "기각 완료" : "처리 완료"
        } · ${
          result.handlingOutcome === "CONTENT_HIDDEN"
            ? `${targetLabel}을 숨겼습니다.`
            : result.handlingOutcome === "CONTENT_ALREADY_HIDDEN"
              ? `${targetLabel}의 기존 숨김을 유지했습니다.`
              : "콘텐츠 공개 상태는 변경하지 않았습니다."
        }`,
      })
      setAction(null)
      setReason("")
      setDirty(false)
      setNeedsCheck(true)
      reread = true
    } catch (e) {
      if (!valid()) return
      if (protect(e)) return
      const failure =
        e instanceof ReportMutationError
          ? e
          : new ReportMutationError(
              0,
              "UNCERTAIN",
              "처리 결과를 확인하지 못했습니다.",
              true,
            )
      setError(failure.message)
      setFieldError(failure.code === "INVALID_HANDLING_REASON")
      if (failure.code === "INVALID_HANDLING_REASON") focusError()
      if (failure.uncertain || failure.status === 409 || failure.status === 404)
        setNeedsCheck(true)
      if (failure.code === "REPORT_ALREADY_HANDLED") {
        setNotice(
          "이미 종결된 신고입니다. 이번 요청의 성공이 아닙니다. 서버의 최신 결과를 확인합니다.",
        )
        reread = true
      } else if (
        [
          "POST_DELETED",
          "COMMENT_DELETED",
          "POST_NOT_FOUND",
          "COMMENT_NOT_FOUND",
        ].includes(failure.code)
      ) {
        setNotice(failure.message)
        reread = true
      } else if (failure.uncertain) {
        setNotice(
          "이번 요청의 처리 결과는 확실하지 않습니다. 다시 조회한 현재 상태와 저장된 처리 이력을 확인해 주세요.",
        )
      }
    } finally {
      flight.current = false
      if (valid()) {
        setBusy(false)
        guard.cancel()
      }
    }
    if (reread && valid()) {
      setLoading(true)
      await load(true)
    }
  }
  const contentStatus = data ? reportTargetStatus(data.currentTarget) : null
  const contentStatusLabel = contentStatus === null ? "확인 불가" : REPORT_TARGET_STATUSES[contentStatus]
  const staleMessage = loading
    ? "최신 상태를 확인하고 있습니다. 이전 조회 정보입니다."
    : !listOnlyReadFailure && (needsCheck || readError)
      ? "최신 상태를 확인하지 못했습니다. 현재 콘텐츠는 이전 조회 정보입니다."
      : ""
  const target = data?.currentTarget
  const sameContent = !!target && !!data && target.content === data.contentSnapshot &&
    (data.targetType === "COMMENT" || target.title === data.titleSnapshot)
  const hideUnavailable = !target
    ? "현재 대상을 찾을 수 없어 숨김·처리 완료를 실행할 수 없습니다."
    : target.deletedAt
      ? "삭제된 대상은 숨김·처리 완료를 실행할 수 없습니다."
      : ""
  const actionName =
    action === "hide-and-resolve"
      ? "숨김·처리 완료"
      : action === "resolve"
        ? "추가 조치 없이 처리 완료"
        : "신고 기각"
  const returnHref = reportHref(query, id)
  const back = () => {
    if (busy || reason) setLocalExit(true)
    else {
      setAction(null)
      setError("")
    }
  }
  return (
    <>
      <AdminSelectionPanel
        id={id}
        title={`신고 #${id} 상세`}
        focusOnOpen
        onClose={close}
        headerActions={data?.status === "RECEIVED" ? <button type="button" onClick={() => reveal(actionHeading.current)}>처리 영역으로 이동</button> : undefined}
      >
        {loading && !data && <p role="status">신고 상세를 불러오고 있습니다…</p>}
        {readError && <p role="alert">{readError}</p>}
        {notice && <p role="status">{notice}</p>}
        {needsCheck && (
          <div className="report-recovery">
            <p>최신 정보 확인 전에는 처리할 수 없습니다.</p>
            <button
              disabled={busy || loading}
              onClick={() => {
                setLoading(true)
                void load(true)
              }}
            >
              최신 정보 다시 조회
            </button>
          </div>
        )}
        {data && (
          <div className="report-detail" aria-busy={loading}>
            {absent &&
              (query.status === "RECEIVED" && data.status !== "RECEIVED" ? (
                <div className="report-filter-notice" role="status">
                  <p>처리가 완료되어 미처리 목록에서 제외됐습니다.</p>
                  <button
                    type="button"
                    onClick={() =>
                      showStatus(data.status as "RESOLVED" | "DISMISSED")
                    }
                  >
                    {data.status === "DISMISSED"
                      ? "기각 목록 보기"
                      : "처리 완료 목록 보기"}
                  </button>
                </div>
              ) : (
                <p>이 신고는 현재 목록 조건에 포함되어 있지 않습니다.</p>
              ))}
            <section>
              <h3>신고 기본 정보</h3>
              <dl className="report-status-summary">
                <div>
                  <dt>신고 상태</dt>
                  <dd><span className="report-badge" data-status={data.status}>{REPORT_STATUSES[data.status]}</span></dd>
                </div>
                <div>
                  <dt>현재 콘텐츠</dt>
                  <dd><span className="report-content-badge" data-content-status={contentStatus ?? "UNAVAILABLE"}>{contentStatusLabel}</span></dd>
                </div>
              </dl>
              {staleMessage && <p className="report-status-freshness" role="status">{staleMessage}</p>}
              <p>
                {data.targetType === "POST" ? "게시글" : "댓글"} #
                {data.targetId} · 신고자: 회원 #{data.reporterMemberId}
              </p>
              <p>접수: {writtenTime(data.createdAt).exact}</p>
              <p>신고 사유: {REPORT_REASONS[data.reasonType]}</p>
              {data.reasonDetail !== null && (
                <p className="report-body">{data.reasonDetail}</p>
              )}
            </section>
            {data.status !== "RECEIVED" && (
                <section className="report-handling-summary" aria-labelledby={`${uid}-result`}>
                  <h3 id={`${uid}-result`} ref={resultHeading} tabIndex={-1}>신고 처리 결과</h3>
                  <p>
                    신고 상태:{" "}
                    <span className="report-badge" data-status={data.status}>
                      {REPORT_STATUSES[data.status]}
                    </span>
                  </p>
                  <p>
                    처리 결과:{" "}
                    {data.handlingOutcome === null
                      ? "아직 처리되지 않음"
                      : REPORT_OUTCOMES[data.handlingOutcome]}
                  </p>
                  <p>
                    처리 당시 저장된 결과입니다. 현재 콘텐츠의 공개·숨김·삭제
                    상태와 다를 수 있습니다.
                  </p>
                  <p>처리 관리자: 회원 #{data.handledByMemberId}</p>
                  <p>처리: {writtenTime(data.handledAt!).exact}</p>
                  <h4>처리 사유</h4>
                  <div className="report-body">{data.handlingReason}</div>
                </section>
            )}
            <section>
              <h3>신고 당시 콘텐츠</h3>
              <ReportContent key={`snapshot-${id}`} title={data.titleSnapshot} content={data.contentSnapshot}/>
            </section>
            <section>
              <h3 className="report-current-heading">
                현재 콘텐츠
                <span className="report-content-badge" data-content-status={contentStatus ?? "UNAVAILABLE"}>{contentStatusLabel}</span>
              </h3>
              <p>
                {(loading || (!listOnlyReadFailure && (needsCheck || !!readError)))
                  ? "최신 조회 전 정보입니다. 처리 후 현재 콘텐츠 상태를 다시 확인해 주세요."
                  : listOnlyReadFailure
                    ? "상세 정보는 갱신됐습니다. 목록과 상세가 일시적으로 다를 수 있습니다."
                    : "신고 이후 내용과 상태가 변경될 수 있습니다."}
              </p>
              {target ? (
                <>
                  <p>
                    현재 작성자: {target.author.nickname} · 회원 #
                    {target.author.memberId}
                  </p>
                  {!loading && !needsCheck && !readError && <p className="report-comparison">
                    {sameContent
                      ? "신고 당시와 내용이 같습니다." : "신고 당시와 내용이 다릅니다."}
                  </p>}
                  <ReportContent key={`current-${id}`} title={target.title} content={target.content}
                    duplicate={sameContent}/>

                  <Link
                    className="report-management-link"
                    href={`/admin/${
                      data.targetType === "COMMENT" ? "comments" : "posts"
                    }/${target.targetId}?from=${encodeURIComponent(returnHref)}`}
                    prefetch={false}
                  >
                    {data.targetType === "POST"
                      ? "게시글 관리로 이동"
                      : "신고된 댓글 관리로 이동"}
                  </Link>
                  {target.hiddenAt && !target.deletedAt && (
                    <p>숨김 해제는 콘텐츠 관리 화면에서 할 수 있습니다.</p>
                  )}
                  {data.targetType === "POST" &&
                    !target.hiddenAt &&
                    !target.deletedAt && (
                      <Link
                        href={`/community/posts/${target.targetId}`}
                        prefetch={false}
                      >
                        원문 보기
                      </Link>
                    )}
                </>
              ) : (
                <p>
                  현재 대상을 찾을 수 없습니다. 신고 기록과 당시 내용은
                  유지됩니다.
                </p>
              )}
            </section>
            {data.status === "RECEIVED" && <section>
              <h3 ref={actionHeading} tabIndex={-1}>신고 처리</h3>
              {data.status === "RECEIVED" && !action && (
                <p>이 신고에 적용할 처리 방법을 선택하세요.</p>
              )}
              {!action ? (
                <div>
                  <p>처리 결과: 아직 처리되지 않음</p>
                  {hideUnavailable && (
                    <p id={`${uid}-hide-unavailable`}>{hideUnavailable}</p>
                  )}
                  {target?.hiddenAt && !target.deletedAt && (
                    <p>
                      이미 숨겨진 대상입니다. 기존 숨김을 유지하고 신고를 처리
                      완료할 수 있습니다.
                    </p>
                  )}
                  <div className="report-actions">
                    <div className="report-action-choice">
                      <button
                        aria-describedby={`${uid}-hide-and-resolve-effect ${hideUnavailable ? `${uid}-hide-unavailable` : ""}`}
                        disabled={loading || needsCheck || !!hideUnavailable}
                        onClick={() => setAction("hide-and-resolve")}
                      >
                        숨김·처리 완료
                      </button>
                      <p id={`${uid}-hide-and-resolve-effect`}>콘텐츠를 숨기고 신고를 종결합니다.</p>
                    </div>
                    <div className="report-action-choice">
                      <button
                        aria-describedby={`${uid}-resolve-effect`}
                        disabled={loading || needsCheck}
                        onClick={() => setAction("resolve")}
                      >
                        추가 조치 없이 처리 완료
                      </button>
                      <p id={`${uid}-resolve-effect`}>공개 상태를 유지하고 신고를 종결합니다.</p>
                    </div>
                    <div className="report-action-choice">
                      <button
                        aria-describedby={`${uid}-dismiss-effect`}
                        disabled={loading || needsCheck}
                        onClick={() => setAction("dismiss")}
                      >
                        신고 기각
                      </button>
                      <p id={`${uid}-dismiss-effect`}>신고를 받아들이지 않고 종결합니다.</p>
                    </div>
                  </div>
                </div>
              ) : (
                <form
                  className="report-handling"
                  noValidate
                  onSubmit={(e) => {
                    e.preventDefault()
                    void submit()
                  }}
                >
                  <h4>
                    신고 #{id} · {actionName} 확인
                  </h4>
                  <p>
                    처리 대상: {data.targetType === "POST" ? "게시글" : "댓글"}{" "}
                    #{data.targetId}
                  </p>
                  <p>
                    {action === "hide-and-resolve"
                      ? target?.hiddenAt
                        ? "기존 숨김을 유지하고 이 신고만 처리 완료합니다. 새로 숨기거나 다른 신고를 함께 처리하지 않습니다."
                        : "이 콘텐츠를 숨기고 이 신고를 함께 처리 완료합니다. 다른 신고나 회원 상태는 변경하지 않습니다."
                      : action === "resolve"
                        ? "콘텐츠 공개 상태를 변경하지 않고 이 신고만 종결합니다. 추가 조치가 필요하지 않은 이유를 입력해 주세요."
                        : "이 신고만 기각합니다. 숨겨진 콘텐츠를 자동 복구하지 않습니다."}
                  </p>
                  {action === "hide-and-resolve" && hideUnavailable && (
                    <p role="alert">{hideUnavailable}</p>
                  )}
                  <label htmlFor={`${uid}-reason`}>처리 사유 (필수)</label>
                  <textarea
                    ref={field}
                    id={`${uid}-reason`}
                    rows={5}
                    value={reason}
                    disabled={busy || loading}
                    aria-invalid={fieldError}
                    aria-describedby={`${uid}-count${
                      error ? ` ${uid}-error` : ""
                    }`}
                    onChange={(e) => {
                      setReason(e.target.value)
                      setDirty(!!e.target.value)
                      if (fieldError) {
                        setError("")
                        setFieldError(false)
                      }
                    }}
                  />
                  <p id={`${uid}-count`}>
                    {[...handlingText(reason)].length} / 500자 · 앞뒤 공백 제외
                  </p>
                  {error && (
                    <p
                      id={`${uid}-error`}
                      className="report-error"
                      role="alert"
                    >
                      ⚠ {error}
                    </p>
                  )}
                  <div className="report-actions">
                    <button type="button" disabled={busy} onClick={back}>
                      취소
                    </button>
                    <button
                      className="report-primary"
                      type="submit"
                      disabled={busy || loading || needsCheck || (action === "hide-and-resolve" && !!hideUnavailable)}
                    >
                      {busy
                        ? "처리 중…"
                        : action === "hide-and-resolve"
                          ? "숨김·처리 완료 확정"
                          : action === "resolve"
                            ? "처리 완료 확정"
                            : "기각 확정"}
                    </button>
                  </div>
                </form>
              )}
            </section>}
          </div>
        )}
      </AdminSelectionPanel>
      {(guard.pending || localExit) && (
        <OverlayDialog
          className="report-exit-dialog"
          titleId={`${uid}-exit`}
          onClose={() => {
            guard.cancel()
            setLocalExit(false)
          }}
        >
          <h2 id={`${uid}-exit`}>
            {busy ? "처리 중입니다" : "입력한 사유를 버리고 이동할까요?"}
          </h2>
          <p>
            {busy
              ? "요청 완료 후 이동할 수 있습니다."
              : "이동하면 작성한 처리 사유가 사라집니다."}
          </p>
          <button
            data-initial-focus
            onClick={() => {
              guard.cancel()
              setLocalExit(false)
            }}
          >
            {busy ? "머무르기" : "계속 작성"}
          </button>
          {!busy && (
            <button
              onClick={() => {
                setReason("")
                setAction(null)
                setError("")
                setDirty(false)
                if (localExit) setLocalExit(false)
                else guard.proceed()
              }}
            >
              입력 버리고 이동
            </button>
          )}
        </OverlayDialog>
      )}
    </>
  )
}
