"use client"
import { useCallback, useEffect, useId, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import CommunityDialog from "./CommunityDialog"
import CommentSuccessToast from "./CommentSuccessToast"
import {
  REPORT_REASONS,
  createReport,
  reportInputError,
  reportDetailText,
  ReportMutationError,
} from "@/lib/content-reports"
import "@/styles/content-reports.css"

export default function ReportButton({
  postId,
  commentId,
  authorId,
  label,
}: {
  postId: string
  commentId?: number
  authorId: number
  label: string
}) {
  const auth = useAuth(), [open, setOpen] = useState(false)
  return <ReportEntry open={open} setOpen={setOpen} key={`${postId}:${commentId}:${auth.memberRequests.revision}`} postId={postId} commentId={commentId} authorId={authorId} label={label}/>
}
function ReportEntry({postId, commentId, authorId, label, open, setOpen}: {postId:string;commentId?:number;authorId:number;label:string;open:boolean;setOpen:(value:boolean)=>void}) {
  const auth = useAuth(), scope = useNavigationRequest()
  const [menu, setMenu] = useState(false), [toast, setToast] = useState(false)
  const root = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null), item = useRef<HTMLButtonElement>(null)
  const dismissToast = useCallback(() => setToast(false), [])
  const title = commentId ? "댓글" : "게시글"
  useEffect(() => {
    if (!menu) return
    item.current?.focus({preventScroll:true})
    const outside = (e: PointerEvent) => {if (!root.current?.contains(e.target as Node)) setMenu(false)}
    document.addEventListener("pointerdown", outside)
    return () => document.removeEventListener("pointerdown", outside)
  }, [menu])
  if (auth.member?.memberId === authorId) return null
  const openForm = () => {
    trigger.current?.focus({preventScroll:true})
    setMenu(false)
    setOpen(true)
  }
  return <div ref={root} className="content-more" onClick={e => e.stopPropagation()}>
    <button ref={trigger} type="button" className="content-more-trigger" aria-label={`${title} 더보기`} aria-haspopup="menu" aria-expanded={menu}
      disabled={auth.pending !== null || auth.status === "checking"}
      onClick={() => setMenu(v => !v)} onKeyDown={e => {if (e.key === "ArrowDown") {e.preventDefault();setMenu(true)}}}>⋯</button>
    {menu && <div className="content-more-menu" role="menu" aria-label={`${title} 메뉴`} onKeyDown={e => {
      if (e.key === "Escape") {e.preventDefault();e.stopPropagation();setMenu(false);trigger.current?.focus()}
      if (e.key === "Tab") setMenu(false)
      if (["ArrowDown","ArrowUp","Home","End"].includes(e.key)) {e.preventDefault();item.current?.focus()}
    }}><button ref={item} role="menuitem" onClick={openForm}>신고</button></div>}
    {open && <ReportForm postId={postId} commentId={commentId} label={label} close={() => setOpen(false)} success={() => {setOpen(false);setToast(true)}}/>}
    <div role="status" aria-atomic="true" className="comment-toast-announcer">
      {toast && scope.isActive() && <CommentSuccessToast message="신고가 접수되었습니다. 검토 후 필요한 조치를 진행합니다." onDismiss={dismissToast}/>}
    </div>
  </div>
}

function ReportForm({
  postId,
  commentId,
  label,
  close,
  success,
}: {
  postId: string
  commentId?: number
  label: string
  close: () => void
  success: () => void
}) {
  const auth = useAuth(),
    scope = useNavigationRequest(),
    uid = useId()
  const [reason, setReason] = useState(""),
    [detail, setDetail] = useState(""),
    [error, setError] = useState<ReportMutationError | null>(null),
    [done, setDone] = useState(""),
    [busy, setBusy] = useState(false)
  const flight = useRef(false),
    select = useRef<HTMLInputElement>(null),
    field = useRef<HTMLTextAreaElement>(null)
  const target = commentId ? "댓글" : "게시글"
  const dismiss = () => {
    if (!flight.current) close()
  }
  async function submit() {
    if (
      flight.current ||
      done ||
      error?.uncertain ||
      auth.status !== "authenticated" ||
      auth.pending ||
      !auth.member
    )
      return
    const message = reportInputError(reason, detail)
    if (message) {
      setError(new ReportMutationError(400, "INVALID_REASON", message))
      ;(reason ? field : select).current?.focus()
      return
    }
    const navigation = scope.begin(),
      session = auth.captureMemberRequest(auth.member.memberId),
      valid = () => navigation() && session.isCurrent()
    flight.current = true
    setBusy(true)
    setError(null)
    try {
      const result = await createReport(
        postId,
        commentId,
        reason,
        detail,
        valid,
      )
      if (valid() && result) success()
    } catch (e) {
      if (!valid()) return
      const failure =
        e instanceof ReportMutationError
          ? e
          : new ReportMutationError(
              0,
              "UNCERTAIN",
              "접수 결과를 확인하지 못했습니다.",
              true,
            )
      if (failure.status === 401) {
        session.expire()
        return
      }
      if (failure.code === "REPORT_ALREADY_EXISTS") setDone(failure.message)
      else {
        setError(failure)
        if (failure.code === "INVALID_REASON")
          (reason ? field : select).current?.focus()
      }
    } finally {
      flight.current = false
      if (valid()) setBusy(false)
    }
  }
  return (
    <CommunityDialog
      className="content-report-dialog"
      titleId={`${uid}-title`}
      onCancel={dismiss}
    >
      <button type="button" className="report-dialog-close" aria-label="신고 창 닫기" disabled={busy} onClick={dismiss}>×</button>
      <h2 id={`${uid}-title`}>
        {auth.status === "anonymous" ? "로그인이 필요해요" : `${target} 신고`}
      </h2>
      {auth.status === "anonymous" ? (
        <>
          <p>콘텐츠를 신고하려면 로그인이 필요해요.</p>
          <div className="community-dialog-actions">
            <button data-initial-focus onClick={dismiss}>
              그대로 머무르기
            </button>
            <Link
              className="iyum-button iyum-button-primary"
              href={`/login?from=${encodeURIComponent(`/community/posts/${postId}`)}`}
            >
              로그인하기
            </Link>
          </div>
        </>
      ) : auth.status !== "authenticated" ? (
        <>
          <p>로그인 상태를 확인하지 못했습니다.</p>
          <button onClick={() => void auth.refresh()}>다시 확인</button>
          <button onClick={dismiss}>닫기</button>
        </>
      ) : done ? (
        <>
          <p role="status">{done}</p>
          <button data-initial-focus onClick={dismiss}>
            닫기
          </button>
        </>
      ) : (
        <form
          noValidate
          onSubmit={(e) => {
            e.preventDefault()
            void submit()
          }}
        >
          <p className="report-target-label">{label}</p>
          <p>신고 접수만으로 콘텐츠가 숨김·삭제되지는 않습니다.</p>
          <fieldset role="radiogroup" aria-invalid={error?.code === "INVALID_REASON" && !reason} className="report-reasons" aria-describedby={error?.code === "INVALID_REASON" && !reason ? `${uid}-error` : undefined}>
            <legend>신고 사유 (필수)</legend>
            {Object.entries(REPORT_REASONS).map(([v, l], index) => (
              <label key={v}><input type="radio" name={`${uid}-reason`} value={v} checked={reason === v}
                ref={index === 0 ? select : undefined} data-initial-focus={index === 0 || undefined}
                disabled={busy}
                onChange={() => {setReason(v);if(error?.code === "INVALID_REASON")setError(null)}}/><span>{l}</span></label>
            ))}
          </fieldset>
          <label htmlFor={`${uid}-detail`}>
            상세 사유 ({reason === "OTHER" ? "필수" : "선택"})
          </label>
          <textarea
            ref={field}
            id={`${uid}-detail`}
            rows={3}
            value={detail}
            disabled={busy}
            aria-invalid={error?.code === "INVALID_REASON" && !!reason}
            aria-describedby={`${uid}-count${error ? ` ${uid}-error` : ""}`}
            onChange={(e) => {
              setDetail(e.target.value)
              if (error?.code === "INVALID_REASON") setError(null)
            }}
          />
          <p id={`${uid}-count`}>
            {[...reportDetailText(detail)].length} / 500자 · 앞뒤 공백 제외
          </p>
          {error && (
            <p id={`${uid}-error`} role="alert" className="report-error">
              ⚠ {error.message}
              {error.uncertain && " 입력을 수정해도 다시 접수하지 않습니다. 잠시 후 창을 닫고 명시적으로 다시 시도할 수 있으며, 이미 접수됐다면 기존 신고 사유는 유지되고 중복 신고 안내가 표시됩니다."}
            </p>
          )}
          <div className="community-dialog-actions">
            <button type="button" disabled={busy} onClick={dismiss}>
              취소
            </button>
            <button
              type="submit"
              className="iyum-button iyum-button-primary"
              disabled={
                busy ||
                error?.uncertain ||
                error?.status === 404 ||
                error?.code === "SELF_REPORT_NOT_ALLOWED"
              }
            >
              {busy ? "접수 중…" : "신고하기"}
            </button>
          </div>
        </form>
      )}
    </CommunityDialog>
  )
}
