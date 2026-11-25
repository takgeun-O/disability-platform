"use client"

import { useEffect, useEffectEvent, useLayoutEffect, useId, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { changeComment, CommentError, commentCursorKey, getReplies, validateComment, type Comment, mergeComments, type CommentPage } from "@/lib/community-comments"
import { characterCount } from "@/lib/community-posts"
import CommentLikeButton from "./CommentLikeButton"
import CommentItem from "./CommentItem"
import CommunityDialog from "./CommunityDialog"
import ReplyOwnerMenu from "./ReplyOwnerMenu"
import { memberCommentsChanged, postCommentsChanged, postListChanged } from "@/lib/community-mutations"

export type ReplyReceipt = { commentId: number; content: string }

export type ReplyCheck = "checking" | "failed" | "checked"
export type ReadReplies = () => Promise<boolean | undefined>

export type ReplyFormProps = {
  parentId: number; nickname: string; value: string; error: string
  busy: boolean; permitted: boolean; expired: boolean; unavailable: boolean
  onChange: (value: string) => void; onCancel: () => void; onLogin: () => void
  check?: ReplyCheck
  onSubmit: (onRegistered: (receipt: ReplyReceipt) => void, readReplies: ReadReplies) => Promise<void>
  onRecheck: (readReplies: ReadReplies) => Promise<void>
}

export function ReplyForm({ parentId, nickname, value, error, busy, permitted, expired, unavailable,
  onChange, onCancel, onLogin, onSubmit, onRegistered, check, onRecheck, readReplies }: ReplyFormProps & {
    readReplies?: ReadReplies
    onRegistered?: (receipt: ReplyReceipt) => void
  }) {
  const inputId = `reply-input-${parentId}`
  return <form className="community-comment-form" onSubmit={event => {
    event.preventDefault()
    if (onRegistered && readReplies && !unavailable && check !== "checking" && check !== "failed")
      void onSubmit(onRegistered, readReplies)
  }}>
    <label htmlFor={inputId}>{nickname}님에게 답글 남기기</label>
    <textarea id={inputId} value={value} disabled={busy} readOnly={unavailable}
      aria-describedby={`${inputId}-count${error ? " " + inputId + "-error" : ""}`}
      onChange={event => onChange(event.target.value)} />
    <span id={`${inputId}-count`}>{characterCount(value)} / 2,000자</span>
    {error && <p role="alert" id={`${inputId}-error`} className="community-error">{error}</p>}
    {check && <p role="status">{check === "checking" ? "답글 수와 공개 목록을 확인하고 있어요."
      : check === "failed" ? "답글 목록을 확인하지 못했어요."
      : "공개 목록을 확인했어요. 다시 등록하기 전에 답글을 확인해 주세요."}</p>}
    {unavailable && <p>현재 목록에서 이 원댓글에 답글을 작성할 수 없어요. 작성한 내용은 유지했어요.</p>}
    <div className="community-dialog-actions">
      <button type="button" className="iyum-button iyum-button-outline" disabled={busy} onClick={onCancel}>취소</button>
      {!unavailable && (expired && !permitted
        ? <button type="button" className="iyum-button" onClick={onLogin}>로그인하기</button>
        : (check === "failed" || check === "checking")
          ? <button type="button" className="iyum-button" disabled={busy || check === "checking" || !permitted}
              onClick={() => { if (readReplies) void onRecheck(readReplies) }}>답글 목록 다시 확인</button>
        : <button type="submit" className="iyum-button iyum-button-primary" disabled={busy || !permitted}>{check === "checked" ? "답글 다시 등록" : "답글 등록"}</button>)}
    </div>
  </form>
}

export default function CommentReplies({ postId, parentId, count, revision, destination, arrivalId, onArrival, onMissing, refreshBest, form, checkCount, showToast, keepDraft, showUnavailableDraft, forgetDraft }: {
  postId: string; parentId: number; count: number; revision: number
  destination?: number; arrivalId?: number
  onArrival: (target: HTMLElement, id: number) => void
  onMissing: () => void; refreshBest: () => void
  showToast: (message: string, valid: () => boolean) => void
  keepDraft: (id: number, content: string, valid: () => boolean, endEdit: () => void) => void
  showUnavailableDraft: (id: number, valid: () => boolean) => void
  forgetDraft: (id: number) => void
  form: ReplyFormProps | null; checkCount: (onCount: (count: number) => void) => void
}) {
  const auth = useAuth()
  const scope = useNavigationRequest()
  const listId = useId()
  const [open, setOpen] = useState(false)
  const expanded = useRef(false)
  const [page, setPage] = useState<CommentPage | null>(null)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState<"first" | "more" | "missing" | null>(null)
  const [receipts, setReceipts] = useState<ReplyReceipt[]>([])
  const [action, setAction] = useState<{ id: number; method: "PUT" | "DELETE"; content: string } | null>(null)
  const [message, setMessage] = useState("")
  // Only a successful verification page can supply the cursor for the next check.
  const [verification, setVerification] = useState<{ acknowledged: boolean; prefix: CommentPage | null } | null>(null)
  const readOnlyRetry = verification !== null
  const [saving, setSaving] = useState(false)
  const operation = useRef<{ read?: AbortController } | null>(null)
  const writing = useRef(false)
  const active = useRef<AbortController | null>(null)
  const generation = useRef(0)
  useEffect(() => () => { generation.current++; active.current?.abort() }, [])

  const [needsDeleteRead, setNeedsDeleteRead] = useState(false)
  const pendingDeletion = useRef(false)
  const deletionCount = useRef<number | null>(null)
  const lastRevision = useRef(revision)
  const latestCount = useRef(count)
  const loadedPages = useRef(0)
  const pageCursors = useRef(new Set<string>())

  function invalidateCache(confirmed: number) {
    generation.current++
    active.current?.abort()
    active.current = null
    loadedPages.current = 0
    setPage(null)
    setLoading(false)
    setFailed(null)
    if (confirmed === 0) {
      setReceipts([])
      expanded.current = false
      setOpen(false)
    } else if (expanded.current) {
      return load(null, false)
    }
  }

  async function load(previous = page, verifyCount = true, onPage?: (result: CommentPage) => void,
    controller = new AbortController()): Promise<boolean | undefined> {
    if (active.current) return
    const countAtStart = latestCount.current
    const ticket = ++generation.current
    const likeRead = auth.commentLikes.read()
    const session = auth.member && auth.captureMemberRequest(auth.member.memberId)
    const current = () => !controller.signal.aborted && scope.isActive() && likeRead.valid() && (!session || session.isCurrent())
    active.current = controller
    setLoading(true)
    setFailed(null)
    try {
      const seen = new Set(previous ? pageCursors.current : [])
      async function readPage(after: CommentPage["nextCursor"]) {
        const result = await getReplies(postId, parentId, after, controller.signal)
        if (result.nextCursor) {
          const key = commentCursorKey(result.nextCursor)
          if (seen.has(key)) throw new Error("답글 목록의 다음 커서가 반복되었습니다.")
          seen.add(key)
        }
        return result
      }
      let next = await readPage(previous?.nextCursor ?? null)
      let pages = previous ? loadedPages.current + 1 : 1
      let fetched = next.items
      while (current() && next.hasNext && pages < loadedPages.current) {
        next = await readPage(next.nextCursor)
        fetched = mergeComments(fetched, next.items)
        pages++
      }
      if (current()) {
        const items = mergeComments(previous?.items ?? [], fetched)
        // A parent read may have finished while this cursor request was in flight.
        const confirmed = latestCount.current
        const countChanged = confirmed !== countAtStart
        const stale = next.hasNext ? confirmed < countAtStart : confirmed !== items.length
        if (countChanged && stale) {
          return await invalidateCache(confirmed)
        }
        if (likeRead.accept(fetched.map(reply => ({ ...reply, id: reply.commentId })))) refreshBest()
        pageCursors.current = seen
        loadedPages.current = pages
        const result = { ...next, items }
        setPage(result)
        onPage?.(result)
        setReceipts(previous => next.hasNext
          ? previous.filter(r => !items.some(c => c.commentId === r.commentId))
          : [])
        if (!previous && pendingDeletion.current) {
          pendingDeletion.current = false
          setNeedsDeleteRead(false)
          checkCount(() => {})
        } else if (verifyCount && !countChanged && !next.hasNext && (previous || confirmed !== items.length)) {
          checkCount(confirmed => {
            if (ticket === generation.current && confirmed !== items.length)
              void invalidateCache(confirmed)
          })
        }
        return true
      }
    } catch (error) {
      if (current()) {
        setFailed(error instanceof CommentError && error.status === 404 ? "missing" : previous ? "more" : "first")
        if (error instanceof CommentError && error.status === 401) session?.expire()
        return false
      }
    } finally {
      if (active.current === controller && !controller.signal.aborted) {
        active.current = null
        setLoading(false)
      }
    }
  }

  const refresh = useEffectEvent(() => {
    generation.current++
    active.current?.abort()
    active.current = null
    setPage(null)
    setLoading(false)
    setFailed(null)
    if (open) void load(null, false)
    else loadedPages.current = 0
  })
  const acceptCount = useEffectEvent((previous: number, confirmed: number) => {
    const ownDeletion = deletionCount.current === confirmed
    deletionCount.current = null
    // The loaded prefix was already replaced from fresh cursors after our delete.
    if (ownDeletion && page?.hasNext) return
    const stale = page && (page.hasNext ? confirmed < previous : page.items.length !== confirmed)
    if (confirmed === 0 || stale)
      void invalidateCache(confirmed)
  })
  useLayoutEffect(() => {
    const previousCount = latestCount.current
    latestCount.current = count
    if (revision !== lastRevision.current) {
      lastRevision.current = revision
      refresh()
    } else if (previousCount !== count) {
      acceptCount(previousCount, count)
    }
  }, [revision, count])

  function readFromStart() {
    generation.current++
    active.current?.abort()
    active.current = null
    expanded.current = true
    setOpen(true)
    setPage(null)
    return load(null, false)
  }

  function revealRegistered(registered: ReplyReceipt) {
    setReceipts(previous => [...previous, registered])
    void readFromStart()
  }

  function focusAfterRemoval(id: number) {
    const index = page?.items.findIndex(reply => reply.commentId === id) ?? -1
    const sibling = page?.items[index + 1] ?? page?.items[index - 1]
    const next = sibling && document.querySelector<HTMLElement>(`[data-comment-item="${sibling.commentId}"]`)
    const fallback = document.getElementById(`reply-write-${parentId}`) ?? document.getElementById("comments-title")
    const target = next || fallback
    target?.focus({ preventScroll: true })
  }

  const deletedFocus = useRef<(() => void) | null>(null)
  const returnToMenu = useRef<number | null>(null)
  const editingId = action?.method === "PUT" ? action.id : null
  useLayoutEffect(() => {
    if (editingId !== null) document.getElementById(`reply-edit-${editingId}`)?.focus({ preventScroll: true })
  }, [editingId])
  useEffect(() => {
    if (!action && deletedFocus.current) {
      deletedFocus.current()
      deletedFocus.current = null
    }
  }, [action])
  useLayoutEffect(() => {
    if (saving || action || returnToMenu.current === null) return
    const menu = document.getElementById(`reply-more-${returnToMenu.current}`)
    const target = menu ?? document.getElementById(`reply-toggle-${parentId}`) ?? document.getElementById("comments-title")
    returnToMenu.current = null
    target?.focus({ preventScroll: true })
  }, [action, parentId, saving])

  function closeAction(id: number) {
    forgetDraft(id)
    returnToMenu.current = id
    setAction(null)
    setMessage("")
    setVerification(null)
  }

  function beginAction(reply: Comment, method: "PUT" | "DELETE") {
    setMessage("")
    setVerification(null)
    operation.current = {}
    setAction({ id: reply.commentId, method, content: reply.content })
  }

  async function manageReply(readOnly = false) {
    const currentOperation = operation.current
    if (!currentOperation || !action || writing.current || !scope.isActive() || !auth.member || auth.pending) return
    const { id, method, content } = action
    const session = auth.captureMemberRequest(auth.member.memberId)
    const valid = () => operation.current === currentOperation && scope.isActive() && session.isCurrent()
    const field = method === "PUT" && !readOnly && validateComment(content)
    if (field) {
      setMessage(field)
      document.getElementById(`reply-edit-${id}`)?.focus({ preventScroll: true })
      return
    }
    writing.current = true
    setSaving(true)
    let confirmed = readOnly && !!verification?.acknowledged
    try {
      if (!readOnly) {
        if (method === "PUT") keepDraft(id, content, session.isCurrent, () => {
          if (!valid()) return
          operation.current = null
          if (currentOperation.read && active.current === currentOperation.read) {
            currentOperation.read.abort()
            active.current = null
            setLoading(false)
          }
          writing.current = false
          setSaving(false)
          setAction(null)
          setVerification(null)
          setMessage("")
        })
        try {
          if (await changeComment(postId, method, id, content, valid) === null) return
          confirmed = true
        } catch (error) {
          if (!valid()) return
          if (!(error instanceof CommentError) || (!error.uncertain && error.code !== "COMMENT_NOT_FOUND")) {
            forgetDraft(id)
            setMessage(error instanceof Error ? error.message : "답글을 변경하지 못했어요.")
            if (error instanceof CommentError && error.status === 401) session.expire()
            return
          }
        }
        if (session.isCurrent()) {
          postCommentsChanged(postId)
          if (method === "DELETE") postListChanged()
          else memberCommentsChanged()
        }
      }
      if (!valid()) return
      if (!readOnly) {
        setVerification({ acknowledged: confirmed, prefix: null })
        if (confirmed) {
          refreshBest()
          showToast(method === "PUT" ? "답글을 수정했습니다." : "답글을 삭제했습니다.", session.isCurrent)
          if (method === "DELETE") {
            pendingDeletion.current = true
            setNeedsDeleteRead(true)
            deletionCount.current = latestCount.current - 1
            setAction(null)
            setVerification(null)
            active.current?.abort()
            active.current = null
            setPage(previous => previous && { ...previous, items: previous.items.filter(reply => reply.commentId !== id) })
            deletedFocus.current = () => focusAfterRemoval(id)
            void load(null, false)
            return
          }
        }
      }
      setMessage("변경 결과를 공개 답글 목록에서 확인하고 있어요.")
      active.current?.abort()
      active.current = null
      const previous = readOnly && verification?.prefix === page ? page : null
      currentOperation.read = new AbortController()
      const read = await load(previous, false, result => {
        if (!valid()) return
        const latest = result.items.find(reply => reply.commentId === id)
        if (!latest && result.hasNext) {
          setVerification({ acknowledged: confirmed, prefix: result })
          setMessage("읽은 범위에 답글이 없어요. 다음 페이지를 확인해 주세요.")
          return
        }
        setVerification(null)
        if (method === "PUT" && latest && (confirmed || latest.content === content.trim())) {
          if (!confirmed) { refreshBest(); showToast("수정한 내용을 확인했습니다.", session.isCurrent) }
          closeAction(id)
        } else if (!latest) {
          if (!confirmed) { refreshBest(); showToast("답글이 더 이상 표시되지 않습니다.", session.isCurrent) }
          setAction(null)
          if (method === "PUT") showUnavailableDraft(id, session.isCurrent)
          else {
            if (!confirmed || readOnly) requestAnimationFrame(() => { if (valid()) focusAfterRemoval(id) })
          }
        } else {
          forgetDraft(id)
          setMessage("공개 답글을 다시 확인했어요. 내용을 확인해 주세요.")
        }
        if (!latest || method === "DELETE") checkCount(confirmedCount => {
          if (deletionCount.current !== confirmedCount) deletionCount.current = null
        })
      }, currentOperation.read)
      if (read === false && valid()) setMessage("답글 목록을 다시 불러와 주세요.")
    } finally {
      if (operation.current === currentOperation) {
        writing.current = false
        if (valid()) setSaving(false)
      }
    }
  }

  const lastDestination = useRef<number | undefined>(undefined)
  const followReply = useEffectEvent(() => {
    if (!destination || !scope.isActive()) return
    const newSearch = lastDestination.current !== destination
    lastDestination.current = destination
    if (newSearch && failed) {
      setFailed(null)
      return
    }
    if (!open) {
      expanded.current = true
      setOpen(true)
      return
    }
    if (failed === "missing") { onMissing(); return }
    if (failed || active.current) return
    const target = document.getElementById(`comment-${parentId}-reply-${destination}`)
    if (target) onArrival(target, destination)
    else if (!page || page.hasNext) void load(page, false)
    else onMissing()
  })
  useEffect(() => {
    if (!destination) { lastDestination.current = undefined; return }
    const frame = requestAnimationFrame(() => followReply())
    return () => cancelAnimationFrame(frame)
  }, [destination, open, page, loading, failed])

  function toggle() {
    expanded.current = !open
    setOpen(!open)
    if (open) {
      active.current?.abort()
      active.current = null
      setLoading(false)
    } else if (!page || needsDeleteRead) {
      void load(null)
    }
  }

  const showList = count > 0 || receipts.length > 0 || (open && (loading || failed || !!page?.items.length || !!form?.check))
  return <>
    {form && <ReplyForm {...form} onRegistered={revealRegistered} readReplies={readFromStart} />}
    {showList && <div className="comment-replies">
      {count > 0 && <button id={`reply-toggle-${parentId}`} type="button" className="iyum-text-link" aria-expanded={open}
        aria-controls={listId} onClick={toggle}>{`답글 ${count}개 ${open ? "접기" : "보기"}`}</button>}
      <div hidden={!open} id={listId}>
        {receipts.map(receipt => <div key={receipt.commentId} data-created-reply-id={receipt.commentId}>
          <strong>방금 등록한 답글</strong>
          <p className="community-comment-body">{receipt.content}</p>
        </div>)}
        {!!page?.items.length && <ul className="comment-reply-list">
          {page.items.map(reply => <CommentItem showAvatar key={reply.commentId} comment={reply}
            anchorId={`comment-${parentId}-reply-${reply.commentId}`} arrived={arrivalId === reply.commentId}
            more={auth.status === "authenticated" && auth.member?.memberId === reply.author?.memberId &&
              <ReplyOwnerMenu id={reply.commentId} disabled={!!auth.pending || saving || !!action}
                onEdit={() => beginAction(reply, "PUT")} onDelete={() => beginAction(reply, "DELETE")} />}
            body={action?.id === reply.commentId && action.method === "PUT" ? <form className="community-comment-form"
              onSubmit={event => { event.preventDefault(); void manageReply(readOnlyRetry) }}>
              <label htmlFor={`reply-edit-${reply.commentId}`}>답글 수정</label>
              <textarea id={`reply-edit-${reply.commentId}`} value={action.content} readOnly={saving || readOnlyRetry}
                onChange={event => { setAction({ ...action, content: event.target.value }); setMessage("") }} />
              <span>{characterCount(action.content)} / 2,000자</span>
              {message && <p role="status">{message}</p>}
              <div className="community-dialog-actions">
                <button className="iyum-button iyum-button-outline" type="button" disabled={saving} onClick={() => closeAction(reply.commentId)}>취소</button>
                <button className="iyum-button iyum-button-primary" disabled={saving || (!readOnlyRetry && action.content === reply.content)}>
                  {readOnlyRetry ? "답글 다시 확인" : "저장"}</button>
              </div>
            </form> : undefined}
            reactions={<CommentLikeButton postId={postId} comment={reply} parentId={parentId}
              ready onChanged={refreshBest} reread={() => load(null)} />} />)}
        </ul>}
        {action?.method === "PUT" && !page?.items.some(reply => reply.commentId === action.id) && <div>
          <p role="status">{message || "수정 중인 답글을 현재 목록에서 확인할 수 없어요."}</p>
          <p className="community-comment-body">{action.content}</p>
          <button type="button" disabled={saving} onClick={() => { forgetDraft(action.id); setAction(null); focusAfterRemoval(action.id) }}>수정 취소</button>
          <button type="button" disabled={saving} onClick={() => void manageReply(true)}>답글 다시 확인</button>
        </div>}
        {action?.method === "DELETE" && !verification?.acknowledged && <CommunityDialog titleId={`reply-delete-${action.id}`}
          onCancel={() => { if (!saving) closeAction(action.id) }}>
          <h2 id={`reply-delete-${action.id}`}>답글을 삭제할까요?</h2>
          {message && <p role="status">{message}</p>}
          <div className="community-dialog-actions">
            <button className="iyum-button iyum-button-outline" data-initial-focus type="button" disabled={saving} onClick={() => closeAction(action.id)}>취소</button>
            <button className="iyum-button iyum-button-primary" type="button" disabled={saving} onClick={() => void manageReply(readOnlyRetry)}>
              {readOnlyRetry ? "답글 다시 확인" : "답글 삭제하기"}</button>
          </div>
        </CommunityDialog>}
        {loading && <p role="status">답글을 불러오는 중입니다.</p>}
        {page && !loading && !failed && !needsDeleteRead && !page.items.length && <p>표시할 답글이 없습니다.</p>}
        {failed && <p role="alert">{verification || needsDeleteRead ? "답글 목록을 다시 불러와 주세요." : "답글을 불러오지 못했습니다."}</p>}
        {((failed && form?.check !== "checking" && form?.check !== "failed") || page?.hasNext) && <button type="button" className="iyum-text-link"
          data-reply-retry={failed ? true : undefined} disabled={loading} onClick={async event => {
            if (readOnlyRetry && action) return void manageReply(true)
            const retry = event.currentTarget
            const restoreFocus = needsDeleteRead && document.activeElement === retry
            if (await load(failed && failed !== "more" ? null : page) && restoreFocus) {
              requestAnimationFrame(() => {
                if (scope.isActive() && !retry.isConnected && document.activeElement === document.body)
                  focusAfterRemoval(-1)
              })
            }
          }}>{failed ? "답글 다시 시도" : "답글 더 보기"}</button>}
      </div>
    </div>}
  </>
}
