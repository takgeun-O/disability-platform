"use client"
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from "react"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationGuard, useNavigationRequest } from "@/components/NavigationGuard"
import OverlayDialog from "@/components/OverlayDialog"
import AdminSelectionPanel from "@/components/admin/AdminSelectionPanel"
import ReportContent from "@/components/admin/ReportContent"
import { AdminApiError } from "@/lib/admin-posts"
import { writtenTime } from "@/lib/written-time"
import {
  REPORT_REASONS, REPORT_STATUSES, REPORT_TARGET_STATUSES, REPORT_OUTCOMES,
  reportTargetStatus, defaultReportQuery, getReport, handleReport,
  handlingError, handlingText, ReportMutationError,
  type ReportQuery, type ReportDetail, type ReportAction,
} from "@/lib/content-reports"

export type ReportSuccessNotice = {
  key: string
  message: string
  valid: () => boolean
}

function ReportPageFrame({ title, children, headerActions }: {
  title: string
  children: ReactNode
  headerActions?: ReactNode
}) {
  return (
    <article className="report-page-content" aria-label={title}>
      <header className="admin-heading"><h1>{title}</h1>{headerActions}</header>
      {children}
    </article>
  )
}

function ReportModalFrame({ title, children, headerActions, onClose, feedback }: {
  title: string
  children: ReactNode
  headerActions?: ReactNode
  onClose: () => void
  feedback?: ReactNode
}) {
  const titleId = useId()
  return <OverlayDialog className="report-detail-modal admin-reports" titleId={titleId}
    onClose={onClose} closeOnBackdropClick={false} closeOnNavigation={false}>
    <header className="report-modal-heading">
      <h2 id={titleId} tabIndex={-1} data-initial-focus>{title}</h2>
      <div>{headerActions}<button type="button" onClick={onClose} aria-label="신고 상세 닫기">닫기</button></div>
    </header>
    <div className="report-modal-body">{feedback}{children}</div>
  </OverlayDialog>
}

export default function AdminReportDetail({
  id,
  query = defaultReportQuery,
  absent = false,
  refreshList,
  notify,
  showStatus,
  onServerStatus,
  protect,
  close,
  presentation = "panel",
  feedback,
  returnHref: originHref,
}: {
  id: number
  query?: ReportQuery
  absent?: boolean
  notify: (event: ReportSuccessNotice) => void
  onServerStatus?: (status: ReportDetail["status"]) => void
  showStatus?: (status: "RESOLVED" | "DISMISSED") => void
  refreshList?: () => Promise<void>
  protect: (e: unknown) => boolean
  close?: () => void
  presentation?: "panel" | "page" | "modal"
  feedback?: ReactNode
  returnHref: string
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
    const scroller = element.closest(".admin-preview-body, .report-modal-body")
    if (scroller) scroller.scrollTop += element.getBoundingClientRect().top - scroller.getBoundingClientRect().top - 12
    else element.scrollIntoView({ block: "nearest" })
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
        ...(both && refreshList ? [refreshList()] : []),
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
        onServerStatus?.(latest.status)
        if (latest.status !== "RECEIVED") {
          setAction(null)
          setReason("")
          setDirty(false)
        }
      }
      setLoading(false)
    },
    [scope, capture, actor, id, refreshList, protect, setDirty, onServerStatus],
  )
  useEffect(() => {
    const controller = new AbortController(),
      current = scope.begin(),
      session = capture(actor)
    getReport(id, controller.signal)
      .then((value) => {
        if (current() && session.isCurrent()) {
          setData(value)
          onServerStatus?.(value.status)
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
  }, [id, scope, capture, actor, protect, onServerStatus])
  function focusError() {
    requestAnimationFrame(() => {
      field.current?.focus({ preventScroll: true })
      const input = field.current,
        container = input?.closest<HTMLElement>(".admin-preview-body, .report-modal-body")
      const feedback = document.getElementById(`${uid}-error`)
      if (input && !container) input.scrollIntoView({ block: "center" })
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
      onServerStatus?.(result.status)
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
  const returnHref = originHref
  const back = () => {
    if (busy || reason) setLocalExit(true)
    else {
      setAction(null)
      setError("")
    }
  }
  const Frame = presentation === "modal" ? ReportModalFrame : presentation === "page" ? ReportPageFrame : AdminSelectionPanel
  return (
    <>
      <Frame
        feedback={feedback}
        id={id}
        title={`신고 #${id} 상세`}
        focusOnOpen
        onClose={close ?? (() => {})}
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
                      showStatus?.(data.status as "RESOLVED" | "DISMISSED")
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
                  {!staleMessage && target?.hiddenAt && !target.deletedAt && (
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
                      <p id={`${uid}-hide-and-resolve-effect`}>{staleMessage
                        ? "최신 콘텐츠 상태를 확인한 뒤 실행할 수 있습니다."
                        : hideUnavailable || (target?.hiddenAt
                          ? "기존 숨김을 유지하고 이 신고를 처리 완료합니다."
                          : "콘텐츠를 숨기고 이 신고를 처리 완료합니다.")}</p>
                    </div>
                    <div className="report-action-choice">
                      <button
                        aria-describedby={`${uid}-resolve-effect`}
                        disabled={loading || needsCheck}
                        onClick={() => setAction("resolve")}
                      >
                        추가 조치 없이 처리 완료
                      </button>
                      <p id={`${uid}-resolve-effect`}>현재 콘텐츠 상태를 변경하지 않고 신고를 종결합니다.</p>
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
                      ? staleMessage
                        ? "최신 콘텐츠 상태를 확인한 뒤 실행할 수 있습니다."
                        : target?.hiddenAt
                        ? "기존 숨김을 유지하고 이 신고만 처리 완료합니다. 새로 숨기거나 다른 신고를 함께 처리하지 않습니다."
                        : "이 콘텐츠를 숨기고 이 신고를 함께 처리 완료합니다. 다른 신고나 회원 상태는 변경하지 않습니다."
                      : action === "resolve"
                        ? "현재 콘텐츠 상태를 변경하지 않고 이 신고만 종결합니다. 추가 조치가 필요하지 않은 이유를 입력해 주세요."
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
      </Frame>
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
