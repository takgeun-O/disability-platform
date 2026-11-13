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
import OverlayDialog from "@/components/OverlayDialog"
import AdminPagination from "@/components/admin/AdminPagination"
import AdminSelectionPanel from "@/components/admin/AdminSelectionPanel"
import { AdminApiError } from "@/lib/admin-posts"
import { dateRangeError } from "@/lib/admin-dates"
import { writtenTime } from "@/lib/written-time"
import {
  REPORT_REASONS,
  REPORT_STATUSES,
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
export default function AdminReports() {
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
    <ReportsScreen
      key={params.toString()}
      params={new URLSearchParams(params.toString())}
      initialList={snapshot?.key === queryKey ? snapshot.data : null}
      remember={remember}
    />
  )
}
function ReportsScreen({
  params,
  initialList,
  remember,
}: {
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
          capture(actor).expire()
          return true
        }
        if (
          e.status === 403 &&
          (!(e instanceof ReportMutationError) ||
            ["ADMIN_REQUIRED", "ACCESS_DENIED"].includes(e.code))
        ) {
          setDenied(true)
          return true
        }
      }
      return false
    },
    [capture, actor],
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
            { ...queryRef.current, page: Math.max(0, data.totalPages - 1) },
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
              { ...query, page: Math.max(0, data.totalPages - 1) },
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
                {l}
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
                    <th>신고·대상</th>
                    <th>신고 사유·상태</th>
                    <th>접수 시각</th>
                  </tr>
                </thead>
                <tbody>
                  {list.items.map((item) => (
                    <tr
                      key={item.reportId}
                      data-selected={item.reportId === selected}
                      onClick={(e) => {
                        if (
                          !(e.target as HTMLElement).closest("button") &&
                          !loading &&
                          !error
                        )
                          navigate(query, item.reportId)
                      }}
                    >
                      <td>
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
                        <strong>{item.titleSnapshot}</strong>
                        <p>{item.contentSnapshotPreview}</p>
                        {item.currentAuthor && (
                          <small>
                            현재 작성자: {item.currentAuthor.nickname}
                          </small>
                        )}
                      </td>
                      <td>
                        {REPORT_REASONS[item.reasonType]}
                        <span
                          className="report-badge"
                          data-status={item.status}
                        >
                          {REPORT_STATUSES[item.status]}
                        </span>
                      </td>
                      <td>
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
  protect,
  close,
}: {
  id: number
  query: ReportQuery
  absent: boolean
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
    generation = useRef(0)
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
      if (failure?.status === "rejected") {
        if (protect(failure.reason)) return
        setReadError(
          failure.reason instanceof AdminApiError &&
            failure.reason.status === 404
            ? "신고 기록을 찾을 수 없습니다."
            : "최신 정보를 불러오지 못했습니다. 다시 조회해 주세요.",
        )
        setNeedsCheck(true)
        if (
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
      loading
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
      setNotice(
        action === "resolve"
          ? "신고 처리를 완료했습니다. 콘텐츠 공개 상태는 변경하지 않았습니다."
          : "신고를 기각했습니다. 콘텐츠 공개 상태는 변경하지 않았습니다.",
      )
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
      if (failure.status === 409) {
        setNotice("다른 처리와 충돌했습니다. 서버의 최신 결과를 확인합니다.")
        reread = true
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
  const target = data?.currentTarget
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
      >
        {loading && <p role="status">신고 상세를 불러오고 있습니다…</p>}
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
            {absent && (
              <p>이 신고는 현재 목록 조건에 포함되어 있지 않습니다.</p>
            )}
            <section>
              <h3>
                신고 기본 정보{" "}
                <span className="report-badge" data-status={data.status}>
                  {REPORT_STATUSES[data.status]}
                </span>
              </h3>
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
            <section>
              <h3>신고 당시 콘텐츠</h3>
              {data.titleSnapshot !== null && <h4>{data.titleSnapshot}</h4>}
              <div className="report-body">{data.contentSnapshot}</div>
            </section>
            <section>
              <h3>현재 콘텐츠</h3>
              <p>신고 이후 내용과 상태가 변경될 수 있습니다.</p>
              {target ? (
                <>
                  <p>
                    현재 작성자: {target.author.nickname} · 회원 #
                    {target.author.memberId}
                  </p>
                  <p>
                    대상 상태:{" "}
                    {target.deletedAt
                      ? "삭제"
                      : target.hiddenAt
                        ? "숨김"
                        : "정상"}
                  </p>
                  {target.title !== null && <h4>{target.title}</h4>}
                  <div className="report-body">{target.content}</div>
                  <Link
                    href={`/admin/${data.targetType === "COMMENT" ? "comments" : "posts"}/${target.targetId}?from=${encodeURIComponent(returnHref)}`}
                    prefetch={false}
                  >
                    {data.targetType === "POST"
                      ? "게시글 관리로 이동"
                      : "신고된 댓글 관리로 이동"}
                  </Link>
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
            <section>
              <h3>신고 처리</h3>
              <p>
                이 신고 한 건의 검토 결과를 기록합니다. 콘텐츠 숨김이나 회원
                제재는 실행하지 않습니다.
              </p>
              {data.status !== "RECEIVED" ? (
                <>
                  <p>
                    {REPORT_STATUSES[data.status]} · 처리 관리자: 회원 #
                    {data.handledByMemberId}
                  </p>
                  <p>처리: {writtenTime(data.handledAt!).exact}</p>
                  <div className="report-body">{data.handlingReason}</div>
                </>
              ) : !action ? (
                <div className="report-actions">
                  <button
                    disabled={loading || needsCheck}
                    onClick={() => setAction("resolve")}
                  >
                    추가 조치 없이 처리 완료
                  </button>
                  <button
                    disabled={loading || needsCheck}
                    onClick={() => setAction("dismiss")}
                  >
                    신고 기각
                  </button>
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
                    신고 #{id} {action === "resolve" ? "처리 완료" : "기각"}
                  </h4>
                  <p>{action === "resolve" ? "콘텐츠 공개 상태를 변경하지 않고 이 신고만 종결합니다. 추가 조치가 필요하지 않은 이유를 입력해 주세요." : "이 신고만 기각합니다. 숨겨진 콘텐츠를 자동 복구하지 않습니다."}</p>
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
                      disabled={busy || loading || needsCheck}
                    >
                      {busy
                        ? "처리 중…"
                        : action === "resolve"
                          ? "처리 완료 확정"
                          : "기각 확정"}
                    </button>
                  </div>
                </form>
              )}
            </section>
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
