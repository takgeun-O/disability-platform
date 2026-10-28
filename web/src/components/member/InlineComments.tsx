'use client'
import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { useCommentTransition } from '@/lib/useCommentTransition'
import CommentSortMenu from '@/components/community/CommentSortMenu'
import CommentItem from '@/components/community/CommentItem'
import CommentLikeButton from '@/components/community/CommentLikeButton'
import PostLikeButton from '@/components/community/PostLikeButton'
import PostListReactions from '@/components/community/PostListReactions'
import { CommentError, getComments, mergeComments, commentCursorKey, commentError, type CommentSort } from '@/lib/community-comments'
import { getPostVersion, postChanged, usePostVersion } from '@/lib/community-mutations'
import { emptyInlineComments, refreshInlineComments, type InlineCommentsSnapshot } from '@/lib/inline-comments'
import type { PostSummary } from '@/lib/community-api'
import '@/styles/community.css'

export default function InlineComments({ post, memberId, initial, active, restoring, onSnapshot, onLeaving }: {
  post: PostSummary; memberId: number; initial?: InlineCommentsSnapshot; active: boolean; restoring: boolean
  onSnapshot: (postId: number, value: InlineCommentsSnapshot) => void; onLeaving: () => void
}) {
  const { list: commentList, capture: captureTransition } = useCommentTransition()
  const uid = useId(), region = useRef<HTMLElement>(null), toggle = useRef<HTMLButtonElement>(null)
  const { captureMemberRequest, commentLikes, memberRequests } = useAuth(), scope = useNavigationRequest(), version = usePostVersion(`comments:${post.id}`)
  const [view, setView] = useState(initial ?? emptyInlineComments), current = useRef(view)
  const [progress, setProgress] = useState<{ revision: number; kind: 'more' | 'refresh' } | null>(null)
  const busy = progress?.revision === memberRequests.revision ? progress.kind : null
  const request = useRef<AbortController | null>(null), mounted = useRef(false)
  const failed = useRef<string | null>(initial?.error?.attempt ?? null)
  const visible = useRef(active), pendingFocus = useRef<{ trigger: HTMLElement; id?: number } | null>(null)
  const href = `/community/posts/${post.id}?from=%2Fmypage`
  const publish = useCallback((next: InlineCommentsSnapshot) => {
    current.current = next; setView(next); onSnapshot(post.id, next)
  }, [onSnapshot, post.id])
  useLayoutEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; request.current?.abort(); request.current = null }
  }, [])
  useLayoutEffect(() => { visible.current = active; if (!active) pendingFocus.current = null }, [active])
  useLayoutEffect(() => {
    request.current?.abort(); request.current = null; pendingFocus.current = null
    // Rendered progress belongs to the old request, not the new auth revision.
  }, [memberRequests])
  const load = useCallback(async (kind: 'more' | 'refresh', targetId?: number, trigger?: HTMLElement) => {
    if (!mounted.current || !scope.isActive() || memberRequests.paused) return false
    if (request.current && kind === 'more') return false
    const before = current.current
    if (kind === 'more' && (!before.hasNext || before.commentsVersion !== getPostVersion(`comments:${post.id}`) || before.revision !== memberRequests.revision)) return false
    const session = captureMemberRequest(memberId)
    if (!session.isCurrent()) return false
    request.current?.abort()
    const controller = new AbortController(); request.current = controller
    const ticket = commentLikes.read(), requestedVersion = getPostVersion(`comments:${post.id}`)
    const valid = () => mounted.current && request.current === controller && !controller.signal.aborted && scope.isActive() && session.isCurrent() && ticket.valid() && current.current.sort === before.sort
    setProgress({ revision: memberRequests.revision, kind }); pendingFocus.current = null
    try {
      const data = kind === 'more'
        ? { ...await getComments(String(post.id), before.nextCursor, controller.signal, undefined, before.sort), segments: before.segments + 1 }
        : await refreshInlineComments(String(post.id), before.segments, controller.signal, targetId, getComments, before.sort)
      if (!valid() || requestedVersion !== getPostVersion(`comments:${post.id}`)) return false
      const cursors = kind === 'more' ? [...(before.cursors ?? [])] : [...('cursors' in data ? data.cursors as string[] : [])]
      if (kind === 'more' && data.nextCursor) {
        const key = commentCursorKey(data.nextCursor)
        if (cursors.includes(key)) throw commentError(200, null, false)
        cursors.push(key)
      }
      ticket.accept(data.items.map(c => ({ ...c, id: c.commentId })))
      const items = kind === 'more' ? mergeComments(before.items, data.items) : data.items
      if (trigger && document.activeElement === trigger && visible.current && current.current.open) {
        pendingFocus.current = { trigger, id: items[before.items.length]?.commentId }
      }
      failed.current = null
      if ((before.displayedSort ?? before.sort) !== before.sort) captureTransition()
      publish({ ...data, displayedSort: before.sort, sort: before.sort, cursors, items, open: current.current.open, revision: memberRequests.revision, commentsVersion: requestedVersion })
      return true
    } catch (cause) {
      if (!valid() || requestedVersion !== getPostVersion(`comments:${post.id}`)) return false
      if (cause instanceof CommentError && cause.status === 401) { session.expire(); return false }
      const missing = cause instanceof CommentError && cause.status === 404 && cause.code === 'POST_NOT_FOUND'
      failed.current = `${memberRequests.revision}:${requestedVersion}`
      publish({ ...current.current, ...(missing ? { items: [], segments: 0, hasNext: false, nextCursor: null } : {}), error: { kind, missing, attempt: `${memberRequests.revision}:${requestedVersion}`, message: cause instanceof Error ? cause.message : '댓글을 불러오지 못했습니다. 다시 시도해 주세요.' } })
      if (missing) postChanged(String(post.id))
      return false
    } finally {
      if (request.current === controller) { request.current = null; if (mounted.current && scope.isActive()) setProgress(null) }
    }
  }, [captureMemberRequest, commentLikes, memberRequests, memberId, post.id, publish, scope, captureTransition])
  useEffect(() => {
    if (!request.current && active && view.open && !restoring && !view.error?.missing && !memberRequests.paused &&
      (view.commentsVersion !== version || view.revision !== memberRequests.revision) &&
      failed.current !== `${memberRequests.revision}:${version}`) void load('refresh')
  }, [active, view.sort, view.open, restoring, view.error?.missing, view.commentsVersion, view.revision, version, memberRequests, load, busy])
  useLayoutEffect(() => {
    const pending = pendingFocus.current; pendingFocus.current = null
    if (!pending || !active || !view.open || !scope.isActive()) return
    if (pending.trigger.isConnected && document.activeElement !== pending.trigger) return
    const target = pending.id ? region.current?.querySelector<HTMLElement>(`[data-inline-comment="${pending.id}"] button`) : null
    ;(target ?? toggle.current)?.focus({ preventScroll: true })
  }, [view, active, scope])
  function switchSort(sort: CommentSort) {
    if (sort === current.current.sort) return
    request.current?.abort(); request.current = null; pendingFocus.current = null; failed.current = null
    setProgress(null)
    publish({ ...emptyInlineComments, items: current.current.items, displayedSort: current.current.displayedSort ?? current.current.sort, open: current.current.open, sort })
  }
  function switchOpen() {
    if (view.open && region.current?.contains(document.activeElement)) toggle.current?.focus({ preventScroll: true })
    pendingFocus.current = null
    publish({ ...current.current, open: !current.current.open })
  }
  return <>
    <PostListReactions post={post} href={href} onLeaving={onLeaving} likeControl={<div className="mypage-post-like"><PostLikeButton post={post} /></div>} commentControl={
      <button ref={toggle} type="button" className="post-comment-link" aria-expanded={view.open} aria-controls={`${uid}-comments`} onClick={switchOpen}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true"><path d="M21 11.5a9 9 0 0 1-9 9 10 10 0 0 1-4-.8L3 21l1.3-4.9a9 9 0 1 1 16.7-4.6Z"/></svg>
        댓글 {post.commentCount.toLocaleString('ko-KR')}
      </button>} />
    <section ref={region} id={`${uid}-comments`} hidden={!view.open} className="mypage-inline-comments" aria-labelledby={`${uid}-title`} aria-busy={!!busy}>
      <div className="mypage-comment-preview" aria-describedby={`${uid}-preview-note`}>
        <label className="sr-only" htmlFor={`${uid}-preview`}>댓글 작성 미리보기</label>
        <div className="mypage-comment-preview-field">
          <textarea id={`${uid}-preview`} disabled rows={1} placeholder="댓글로 이야기를 나눠보세요" aria-describedby={`${uid}-preview-note`} />
          <button type="button" disabled>등록</button>
        </div>
        <p id={`${uid}-preview-note`}>댓글 작성 기능은 준비 중이에요.</p>
      </div>
      <div className="mypage-inline-heading"><h4 id={`${uid}-title`} className="sr-only">{post.title} 게시글의 댓글</h4>
        <CommentSortMenu value={view.sort} onChange={switchSort} disabled={memberRequests.paused} />
        <button type="button" className="iyum-text-link" disabled={!!busy || memberRequests.paused} onClick={() => void load('refresh')}>댓글 새로고침</button></div>
      <p role="status" className={busy ? 'community-help' : 'sr-only'}>{busy ? view.segments ? busy === 'more' ? '댓글을 더 불러오고 있습니다.' : '댓글을 새로고침하고 있습니다.' : '댓글을 불러오고 있습니다.' : ''}</p>
      {view.error && <p role="alert" className="community-error">{view.error.message}{view.items.length > 0 && ' 기존 목록은 유지됩니다.'}</p>}
      {!!view.segments && !view.items.length && !view.error && <p>아직 댓글이 없어요.</p>}
      {(view.displayedSort ?? view.sort) !== view.sort && <p className="community-help">정렬 변경 전 목록을 표시하고 있습니다.</p>}
      <ul ref={commentList} className="community-comment-list">{view.items.map(c => <CommentItem key={c.commentId} comment={c}
        reactions={<div data-inline-comment={c.commentId}><CommentLikeButton postId={String(post.id)} comment={c} returnTo="/mypage"
          ready={view.revision === memberRequests.revision && view.commentsVersion === version}
          reread={id => load('refresh', id)} /></div>} />)}</ul>
      {(view.hasNext || view.error) && <button type="button" className="iyum-button iyum-button-outline" aria-disabled={!!busy || memberRequests.paused}
        onClick={e => { if (!busy) void load(view.error?.kind ?? 'more', undefined, e.detail === 0 ? e.currentTarget : undefined) }}>
        {busy ? '불러오는 중…' : view.error ? '댓글 다시 시도' : '댓글 더 보기'}</button>}
    </section>
  </>
}
