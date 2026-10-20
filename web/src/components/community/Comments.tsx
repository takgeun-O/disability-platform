"use client"
// Refresh the visible prefix, keeping editor snapshots separate from server data.
import { useEffect, useEffectEvent, useRef, useState, type FormEvent } from "react"
import { memberCommentsChanged, postListChanged, usePostVersion } from "@/lib/community-mutations"
import { useRouter } from "next/navigation"
import { useAuth } from "@/components/AuthProvider"
import {
  useNavigationGuard,
  useNavigationRequest,
  useUnsavedChanges,
} from "@/components/NavigationGuard"
import CommunityDialog from "./CommunityDialog"
import {
  changeComment,
  CommentError,
  getComments,
  mergeComments,
  validateComment,
  type Comment,
  type CommentPage,
} from "@/lib/community-comments"
import type { PublicResult } from "@/lib/community-load"
import { characterCount, postDate } from "@/lib/community-posts"

type Editor = { id: number; original: string; value: string; notFound?: boolean }
export default function Comments({
  postId,
  initial,
  returnTo,
}: {
  postId: string
  returnTo: string
  initial: PublicResult<CommentPage>
}) {
  const identityVersion = usePostVersion("public-identity")
  const auth = useAuth()
  const router = useRouter(), guard = useNavigationGuard()
  const scope = useNavigationRequest()
  const reads = useRef(0),
    reading = useRef(false),
    writing = useRef(false),
    pageCount = useRef(1)
  const removed = useRef(new Set<number>())
  const [page, setPage] = useState<CommentPage>(
    (identityVersion === 0 ? initial.data : null) ?? { items: [], hasNext: false, nextCursor: null },
  )
  const [readError, setReadError] = useState(
    initial.error?.message ??
      "",
  )
  const [fields, setFields] = useState<Record<string, string>>({})
  const [error, setError] = useState<CommentError | null>(null)
  const [draft, setDraft] = useState("")
  const [editor, setEditor] = useState<Editor | null>(null)
  const [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(false)
  const [notice, setNotice] = useState("")
  const [created, setCreated] = useState<{ commentId: number; content: string }[]>([])
  const [loginPrompt, setLoginPrompt] = useState(false)
  const loginMoving = useRef(false)
  const [readMore, setReadMore] = useState(false)
  const section = useRef<HTMLElement>(null), sentinel = useRef<HTMLDivElement>(null), composer = useRef<HTMLDivElement>(null)
  const buffered = useRef<{ cursor: string; page: CommentPage } | null>(null)
  const [inputFocused, setInputFocused] = useState(false)
  const [deleting, setDeleting] = useState<Comment | null>(null)
  const [discard, setDiscard] = useState(false)
  const [focusTarget, setFocusTarget] = useState<{ id: string } | null>(null)
  const requestFocus = (id: string | null) => setFocusTarget(id ? { id } : null)
  const blocker = useUnsavedChanges(
    draft.length >
      0 ||
      (!!editor &&
        editor.value !==
          editor.original),
  )
  const permitted =
    auth.status ===
      "authenticated" &&
    !auth.pending
  const owner = (c: Comment) =>
    permitted &&
    auth.member?.memberId ===
      c.author.memberId
  useEffect(() => {
    if (focusTarget && scope.isActive())
      document.getElementById(focusTarget.id)?.focus({ preventScroll: true })
  }, [focusTarget, scope])
  useEffect(
    () => () => {
      reads.current++
    },
    [],
  )

  const refreshIdentity = useEffectEvent(() => { void load() })
  useEffect(() => { if (identityVersion > 0) refreshIdentity() }, [identityVersion])

  function pauseAuto() {
    // 입력 중 도착한 자동 GET도 폼을 밀어내지 않는다. 수동 추가는 명시적 조작이다.
    const area = section.current
    return !area || !!area.querySelector('dialog[open], textarea[id^="comment-edit-"]') ||
      !!area.querySelector<HTMLTextAreaElement>('#comment-draft')?.value ||
      !!area.querySelector('.community-comment-form:focus-within') ||
      (composer.current?.getBoundingClientRect().top ?? Infinity) < window.innerHeight
  }
  const autoLoad = useEffectEvent(() => {
    if (!pauseAuto()) void load(true, true)
  })
  useEffect(() => {
    if (!page.hasNext || loading || busy || readError || draft || editor || inputFocused || loginPrompt || deleting || discard ||
      !sentinel.current || !("IntersectionObserver" in window)) return
    let near = false
    const observer = new IntersectionObserver(entries => {
      near = entries.some(entry => entry.isIntersecting)
      if (near) autoLoad()
    }, { rootMargin: "320px 0px" })
    // 폼이 보일 때 멈춘 뒤 위로 이동하면, 감시 영역을 벗어나지 않아도 재평가한다.
    const onScroll = () => { if (near) autoLoad() }
    observer.observe(sentinel.current)
    window.addEventListener("scroll", onScroll, { passive: true })
    return () => { observer.disconnect(); window.removeEventListener("scroll", onScroll) }
  }, [page.hasNext, page.nextCursor, loading, busy, readError, draft, editor, inputFocused, loginPrompt, deleting, discard])

  async function load(more = false, automatic = false) {
    if (reading.current || writing.current || !scope.isActive()) return
    if (more && (!page.hasNext || !page.nextCursor)) return
    reading.current = true
    const ticket = ++reads.current
    const current = () =>
      scope.isActive() &&
      ticket ===
        reads.current
    setLoading(true)
    setReadError("")
    setReadMore(more)
    try {
      let result: CommentPage
      if (more) {
        const cursor = JSON.stringify(page.nextCursor)
        result = buffered.current?.cursor === cursor ? buffered.current.page : await getComments(postId, page.nextCursor)
        if (!current()) return
        if (automatic && pauseAuto()) {
          buffered.current = { cursor, page: result }
          return
        }
        buffered.current = null
        result = { ...result, items: mergeComments(page.items, result.items) }
        pageCount.current++
      } else {
        buffered.current = null
        result = await getComments(postId)
        for (let n = 1; n < pageCount.current && result.hasNext; n++) {
          if (!current()) return
          const next = await getComments(postId, result.nextCursor)
          result = { ...next, items: mergeComments(result.items, next.items) }
        }
      }
      if (current()) {
        if (editor && !result.items.some(c => c.commentId === editor.id) && document.activeElement?.id === `comment-edit-${editor.id}`) requestFocus(`comment-edit-${editor.id}`)
        setCreated(receipts => receipts.filter(receipt => !result.items.some(c => c.commentId === receipt.commentId)))
        setPage({
          ...result,
          items: result.items.filter((c) => !removed.current.has(c.commentId)),
        })
      }
    } catch (e) {
      if (current())
        setReadError(
          e instanceof CommentError ? e.message : "댓글을 불러오지 못했습니다.",
        )
    } finally {
      if (ticket === reads.current) {
        reading.current = false
        if (current()) setLoading(false)
      }
    }
  }
  function edit(c: Comment) {
    setEditor({ id: c.commentId, original: c.content, value: c.content })
    setFields(f => ({ ...f, [String(c.commentId)]: "" }))
    setError(null)
    requestFocus(`comment-edit-${c.commentId}`)
  }
  function cancelEdit() {
    if (editor?.value !== editor?.original) setDiscard(true)
    else finishEdit()
  }
  function finishEdit() {
    const id = editor?.id
    setEditor(null)
    setDiscard(false)
    setError(null)
    requestFocus(page.items.some(c => c.commentId === id) ? `comment-edit-button-${id}` : "comments-refresh")
  }
  function removeItem(id: number, preserveEditor = false) {
    removed.current.add(id)
    setCreated(receipts => receipts.filter(receipt => receipt.commentId !== id))
    setPage((p) => ({ ...p, items: p.items.filter((c) => c.commentId !== id) }))
    setDeleting(null)
    if (editor?.id === id) {
      if (preserveEditor) setEditor({ ...editor, notFound: true })
      else setEditor(null)
    }
    requestFocus(preserveEditor ? `comment-edit-${id}` : "comments-refresh")
  }
  async function mutate(
    method: "POST" | "PUT" | "DELETE",
    id: number | null,
    content: string,
    event?: FormEvent,
  ) {
    event?.preventDefault()
    if (writing.current || !permitted || !scope.isActive()) return
    const field = method === "DELETE" ? "" : validateComment(content)
    if (field) {
      setFields(f => ({ ...f, [id ?? "draft"]: field }))
      setError(null)
      requestFocus(id ? `comment-edit-${id}` : "comment-draft")
      document
        .getElementById(id ? `comment-edit-${id}` : "comment-draft")
        ?.focus()
      return
    }
    writing.current = true
    buffered.current = null
    reads.current++
    reading.current = false
    setFields(f => ({ ...f, [id ?? "draft"]: "" }))
    setBusy(true)
    setLoading(false)
    setError(null)
    setNotice("")
    const current = scope.begin()
    let refresh = false
    try {
      const result = await changeComment(postId, method, id, content, current)
      if (result === null) return
      // 이미 보낸 요청의 성공은 이탈 후에도 목록에 반영하되 이전 폼은 건드리지 않는다.
      if (method === "POST" || method === "DELETE") postListChanged()
      else memberCommentsChanged()
      if (!current()) return
      if (method === "DELETE") {
        removeItem(result)
        setNotice("댓글을 삭제했습니다.")
      }
      if (method === "POST") {
        setDraft(value => value === content ? "" : value)
        setCreated(receipts => [...receipts.filter(receipt => receipt.commentId !== result), { commentId: result, content }])
        setNotice("댓글을 등록했습니다.")
        refresh = true
      }
      if (method === "PUT") {
        setEditor(null)
        setNotice("댓글을 수정했습니다.")
        requestFocus(`comment-edit-button-${id}`)
        refresh = true
      }
    } catch (e) {
      const failure =
        e instanceof CommentError
          ? e
          : new CommentError(
              0,
              "UNKNOWN",
              "결과를 확인하지 못했습니다. 목록을 확인해 주세요.",
              "",
              true,
            )
      // 불확실한 결과/없어진 댓글은 서버 목록으로 확인한다. 변경 요청 재전송은 하지 않는다.
      if (failure.uncertain || failure.code === "COMMENT_NOT_FOUND") postListChanged()
      if (!current()) return
      if (failure.code === "COMMENT_NOT_FOUND" && id) {
        removeItem(id, method === "PUT")
        setNotice(method === "PUT" ? "서버에서 수정할 댓글을 찾지 못했습니다. 수정 초안은 유지했습니다." : "댓글이 없거나 이미 삭제되었습니다.")
      } else {
        setError(failure.field ? null : failure)
        if (failure.field) {
          setFields(f => ({ ...f, [id ?? "draft"]: failure.field }))
          const target = id ? `comment-edit-${id}` : "comment-draft"
          requestFocus(target)
          document.getElementById(target)?.focus()
        }
      }
      if (failure.status === 401) void auth.refresh()
    } finally {
      writing.current = false
      if (current()) {
        setBusy(false)
        if (refresh) void load()
      }
    }
  }
  const fieldFor = (id: number | null) => fields[id ?? "draft"] ?? ""
  function clearField(id: number | null, value: string) {
    setFields(f => f[id ?? "draft"] ? { ...f, [id ?? "draft"]: validateComment(value) } : f)
  }
  const detached = !!editor && !page.items.some(c => c.commentId === editor.id)
  function editForm(missing: boolean) {
    if (!editor) return null
    const errorId = `comment-edit-${editor.id}-error`
    return <form className="community-comment-form" onSubmit={e => { if (missing) e.preventDefault(); else void mutate("PUT", editor.id, editor.value, e) }}>
      <label htmlFor={`comment-edit-${editor.id}`}>{missing ? "보관 중인 수정 초안" : "댓글 수정"}</label>
      <textarea id={`comment-edit-${editor.id}`} value={editor.value} disabled={busy}
        aria-invalid={!!fieldFor(editor.id)}
        aria-describedby={fieldFor(editor.id) ? errorId : undefined}
        onChange={e => { blocker.markDirty(); clearField(editor.id, e.target.value); setEditor({ ...editor, value: e.target.value }) }} />
      {fieldFor(editor.id) && <p id={errorId} className="community-comment-field-error" role="alert">{fieldFor(editor.id)}</p>}
      <span>{characterCount(editor.value)} / 2,000자</span>
      {missing && <p className="community-help">아래 버튼으로 초안을 선택한 뒤 복사하세요. 키보드에서는 Ctrl+C 또는 ⌘C, 모바일에서는 선택한 텍스트의 복사 메뉴를 사용할 수 있습니다.</p>}
      <div className="community-comment-actions">
        {missing && <button type="button" className="iyum-button iyum-button-outline" disabled={busy} onClick={() => {
          const input = document.getElementById(`comment-edit-${editor.id}`) as HTMLTextAreaElement | null
          input?.focus({ preventScroll: true }); input?.select()
        }}>초안 전체 선택</button>}
        <button className="iyum-button iyum-button-outline" type="button" disabled={busy} onClick={cancelEdit}>{missing ? "편집 종료" : "수정 취소"}</button>
        {!missing && <button className="iyum-button iyum-button-primary" disabled={busy || !page.items.some(c => c.commentId === editor.id && owner(c)) || editor.value === editor.original}>{busy ? "처리 중…" : "댓글 저장"}</button>}
      </div>
    </form>
  }
  return (
    <section ref={section} className="community-comments" aria-labelledby="comments-title"
      onFocusCapture={e => setInputFocused(!!(e.target as HTMLElement).closest('.community-comment-form'))}
      onBlurCapture={e => { if (!(e.relatedTarget instanceof HTMLElement) || !e.relatedTarget.closest('.community-comment-form')) setInputFocused(false) }}>
      <div className="community-list-heading">
        <h2 id="comments-title">댓글</h2>
        <button
          id="comments-refresh"
          className="iyum-text-link community-comments-refresh"
          disabled={loading || busy}
          onClick={() => void load()}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M20 7v5h-5M4 17v-5h5" /><path d="M6.1 6.1A8 8 0 0 1 19.5 9M4.5 15A8 8 0 0 0 17.9 17.9" /></svg><span>목록 새로고침</span>
        </button>
      </div>
      <p className="community-help">
        오래된 댓글부터 표시합니다. 좋아요 기능은 준비 중입니다.
      </p>
      <p role="status" className="community-comments-status">
        {notice}
        {loading ? " 댓글을 불러오고 있습니다." : ""}
      </p>
      {readError && (
        <div className="community-error" role="alert">
          <p>{readError} 기존 목록과 입력은 유지됩니다.</p>
          <button
            className="iyum-text-link"
            disabled={loading || busy}
            onClick={() => void load(readMore)}
          >
            댓글 조회 다시 시도
          </button>
        </div>
      )}
      {!readError && !loading && page.items.length === 0 && (
        <p>아직 댓글이 없습니다. 첫 이야기를 나눠 주세요.</p>
      )}
      <ul className="community-comment-list">
        {page.items.map((c) => (
          <li key={c.commentId} id={`comment-${c.commentId}`}>
            <div className="community-comment-meta">
              <strong>{c.author.nickname}</strong>
              <time dateTime={c.createdAt}>{postDate(c.createdAt)}</time>
              {c.updatedAt !== c.createdAt && <span>수정됨</span>}
            </div>
            {editor?.id === c.commentId ? (
              editForm(false)
            ) : (
              <>
                <p className="community-comment-body">{c.content}</p>
                {owner(c) && (
                  <div className="community-comment-actions">
                    <button
                      id={`comment-edit-button-${c.commentId}`}
                      className="community-owner-action community-owner-action-edit"
                      disabled={busy || !!editor}
                      onClick={() => edit(c)}
                    >
                      댓글 수정
                    </button>
                    <button
                      className="community-owner-action"
                      disabled={busy}
                      onClick={() => {
                        setDeleting(c)
                        setError(null)
                        requestFocus(null)
                      }}
                    >
                      댓글 삭제
                    </button>
                  </div>
                )}
              </>
            )}
          </li>
        ))}
      </ul>
      {page.hasNext && <div ref={sentinel} className="community-comment-sentinel" aria-hidden="true" />}
      {detached && <aside className="community-comment-detached" aria-labelledby="comment-detached-title">
        <h3 id="comment-detached-title">수정 초안을 보관하고 있어요</h3>
        <p role="status">{editor?.notFound ? "서버에서 수정할 댓글을 찾지 못했습니다." : "현재 조회한 목록에서 수정 중인 댓글을 찾지 못했습니다. 이 결과만으로 삭제 여부를 확정할 수는 없습니다."} 입력한 내용은 아래에서 확인·복사하거나 편집을 종료할 수 있습니다.</p>
        {editForm(true)}
      </aside>}
      {page.hasNext && (
        <button
          className="iyum-button iyum-button-outline"
          disabled={loading || busy}
          onClick={() => void load(true)}
        >
          댓글 더 보기
        </button>
      )}
      {error && !deleting && (
        <div className="community-error" role="alert">
          <p>{error.message}</p>
          {error.uncertain && (
            <p>
              자동 재시도하지 않습니다. 위의 목록 새로고침과 더 보기로 확인해
              주세요.
            </p>
          )}
        </div>
      )}
      <div ref={composer} className="community-comment-composer">
      {created.some(receipt => !page.items.some(c => c.commentId === receipt.commentId)) && (
        <section className="community-comment-receipts" aria-labelledby="comment-receipts-title">
          <h3 id="comment-receipts-title">댓글을 등록했어요</h3>
          <p className="community-help">방금 등록한 댓글입니다. 목록에서 불러오면 해당 위치에 함께 표시됩니다.</p>
          <ul>{created.filter(receipt => !page.items.some(c => c.commentId === receipt.commentId)).map(receipt => (
            <li key={receipt.commentId} data-created-comment-id={receipt.commentId}>
              <span>방금 등록한 댓글</span><p className="community-comment-body">{receipt.content}</p>
            </li>
          ))}</ul>
        </section>
      )}
      <form
        className="community-comment-form"
        onSubmit={(e) => void mutate("POST", null, draft, e)}
      >
        <label htmlFor="comment-draft">댓글 남기기</label>
        <textarea
          id="comment-draft"
          value={draft}
          disabled={busy}
          aria-invalid={!!fieldFor(null)}
          aria-describedby={
            ["comment-count", fieldFor(null) && "comment-draft-error", !permitted && "comment-auth-help"].filter(Boolean).join(" ")
          }
          onChange={(e) => {
            blocker.markDirty()
            clearField(null, e.target.value)
            setDraft(e.target.value)
          }}
        />
        {fieldFor(null) && <p id="comment-draft-error" className="community-comment-field-error" role="alert">{fieldFor(null)}</p>}
        <span id="comment-count">{characterCount(draft)} / 2,000자</span>
        {!permitted && (
          <p id="comment-auth-help" role="status">
            {auth.status === "checking" || auth.pending ? (
              auth.pending === "logout" ? "로그아웃하고 있습니다. 입력한 내용은 유지됩니다." : "로그인 상태를 확인하고 있습니다."
            ) : auth.status === "error" ? (
              "로그인 상태를 확인하지 못했습니다. 다시 확인해 주세요. 입력한 내용은 유지됩니다."
            ) : (
              "댓글을 등록하려면 로그인해 주세요. 로그인 화면으로 이동하면 입력은 보관되지 않습니다."
            )}
          </p>
        )}
        {auth.status === "anonymous" && !auth.pending ? (
          <button type="button" className="iyum-button iyum-button-primary" aria-describedby="comment-auth-help"
            onClick={() => { if (scope.isActive() && !loginMoving.current) setLoginPrompt(true) }}>
            로그인하고 댓글 등록
          </button>
        ) : auth.status === "error" && !auth.pending ? (
          <button type="button" className="iyum-button iyum-button-outline"
            disabled={busy} onClick={() => void auth.refresh()} aria-describedby="comment-auth-help">
            로그인 상태 다시 확인
          </button>
        ) : (
          <button className="iyum-button iyum-button-primary" disabled={!permitted || busy}>
            {busy ? "처리 중…" : auth.pending === "logout" ? "로그아웃 중…" : !permitted ? "로그인 확인 중…" : "댓글 등록"}
          </button>
        )}
      </form>
      </div>
      {loginPrompt && <CommunityDialog titleId="comment-login-title" descriptionId="comment-login-description" onCancel={() => setLoginPrompt(false)}>
        <h2 id="comment-login-title">로그인이 필요해요</h2>
        <div id="comment-login-description">
          <p>{auth.status === "authenticated" && !auth.pending ? "이미 로그인되어 있어요. 현재 화면에서 댓글 작성을 계속할 수 있습니다." : "댓글을 등록하려면 로그인이 필요해요. 로그인 화면으로 이동할까요?"}</p>
          {(draft.length > 0 || (editor && editor.value !== editor.original)) && <p>로그인 화면으로 이동하면 작성·수정 중인 댓글 입력은 보관되지 않습니다.</p>}
          {(auth.status === "checking" || auth.pending) && <p role="status">로그인 상태를 확인하고 있습니다.</p>}
          {auth.status === "error" && <p role="status">로그인 상태를 확인하지 못했습니다.</p>}
        </div>
        <div className="community-dialog-actions">
          <button type="button" data-initial-focus className="iyum-button iyum-button-outline" onClick={() => setLoginPrompt(false)}>그대로 머무르기</button>
          {auth.status === "error" ? <button type="button" className="iyum-button iyum-button-outline" onClick={() => void auth.refresh()}>로그인 상태 다시 확인</button> :
            <button type="button" className="iyum-button iyum-button-primary" disabled={auth.status === "checking" || !!auth.pending} onClick={() => {
              if (!scope.isActive() || auth.pending || loginMoving.current || auth.status === "checking") return
              setLoginPrompt(false)
              if (auth.status === "authenticated") { requestFocus("comment-draft"); return }
              if (auth.status !== "anonymous") return
              loginMoving.current = true
              guard.confirmNavigation(() => router.push(`/login?from=${encodeURIComponent(returnTo)}`))
            }}>{auth.status === "authenticated" ? "댓글 작성 계속하기" : "로그인하기"}</button>}
        </div>
      </CommunityDialog>}
      {deleting && (
        <CommunityDialog
          titleId="comment-delete-title"
          descriptionId="comment-delete-description"
          onCancel={() => {
            if (!busy) setDeleting(null)
          }}
        >
          <h2 id="comment-delete-title">댓글을 삭제할까요?</h2>
          <p id="comment-delete-description">
            삭제하면 목록에서 더 이상 볼 수 없습니다. 요청 중에는 이 창을 닫을
            수 없습니다.
          </p>
          {error && <p role="alert">{error.message}</p>}
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              aria-disabled={busy}
              onClick={() => {
                if (!busy) setDeleting(null)
              }}
            >
              취소
            </button>
            <button
              className="iyum-button community-danger"
              disabled={busy || !owner(deleting)}
              onClick={() => void mutate("DELETE", deleting.commentId, "")}
            >
              {busy ? "삭제 중…" : "댓글 삭제하기"}
            </button>
          </div>
        </CommunityDialog>
      )}
      {discard && (
        <CommunityDialog
          titleId="comment-discard-title"
          onCancel={() => setDiscard(false)}
        >
          <h2 id="comment-discard-title">수정한 내용을 버릴까요?</h2>
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              onClick={() => setDiscard(false)}
            >
              계속 수정
            </button>
            <button
              className="iyum-button iyum-button-primary"
              onClick={finishEdit}
            >
              수정 내용 버리기
            </button>
          </div>
        </CommunityDialog>
      )}
      {blocker.state === "blocked" && (
        <CommunityDialog titleId="comment-leave-title" onCancel={blocker.reset}>
          <h2 id="comment-leave-title">작성 중인 댓글을 두고 나갈까요?</h2>
          <p>
            화면의 입력은 보관되지 않습니다. 이미 전송한 요청은 서버에서 처리될
            수 있습니다.
          </p>
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              onClick={blocker.reset}
            >
              계속 작성
            </button>
            <button
              className="iyum-button iyum-button-primary"
              onClick={blocker.proceed}
            >
              나가기
            </button>
          </div>
        </CommunityDialog>
      )}
    </section>
  )
}
