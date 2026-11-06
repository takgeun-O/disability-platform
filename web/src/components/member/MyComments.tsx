'use client'
import MemberHiddenCommentDelete from './MemberHiddenCommentDelete'
import WrittenTime from "@/components/WrittenTime"
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import Link from '@/components/AppLink'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { getMemberComments, MemberApiError } from '@/lib/member-api'
import { mergeMemberComments, refreshMemberComments, type MemberCommentSnapshot, type createMemberActivityCache } from '@/lib/member-activity'
import { getPostVersion, usePostVersion } from '@/lib/community-mutations'
import { POST_TOPICS, POST_TYPES } from '@/lib/community-posts'
import type { FeedPosition } from '@/lib/community-feed'
import { activityPosition, restoreActivityPosition } from './activity-position'

type Lease = ReturnType<ReturnType<typeof createMemberActivityCache>['lease']>
const empty: MemberCommentSnapshot = { items: [], hasNext: false, nextCursor: null, segments: 0, version: -1 }
// A separate request/DOM lifecycle keeps inactive tab errors, cursors and in-flight reads independent.
export default function MyComments({ memberId, active, restoring, initial, lease, onSnapshot, onLeaving }: {
  memberId: number; active: boolean; restoring: boolean; initial?: MemberCommentSnapshot; lease: Lease
  onSnapshot: (view: MemberCommentSnapshot) => void; onLeaving: () => void
}) {
  const { captureMemberRequest, memberRequests } = useAuth(), scope = useNavigationRequest(), version = usePostVersion('member-comments')
  const [view, setView] = useState(initial ?? empty), current = useRef(view), visible = useRef(active)
  const [busy, setBusy] = useState<'more' | 'refresh' | null>(null)
  const [error, setError] = useState<{ kind: 'more' | 'refresh'; message: string } | null>(null)
  const [notice, setNotice] = useState('')
  const request = useRef<AbortController | null>(null), failedVersion = useRef<number | null>(null)
  const pendingPosition = useRef<FeedPosition | null>(null), pendingFocus = useRef<number | null>(null)
  const trigger = useRef<HTMLElement | null>(null), keyboard = useRef(false)
  const removed = useRef<{ id: number; trigger: HTMLElement; revision: number } | null>(null)
  const afterRemoval = (id: number, trigger: HTMLElement) => { removed.current = { id, trigger, revision: memberRequests.revision }; setNotice('댓글을 삭제했습니다.') }
  useLayoutEffect(() => {
    const pending = removed.current
    if (!pending) return
    if (!active || !scope.isActive() || !lease.valid() || pending.revision !== memberRequests.revision) { removed.current = null; return }
    if (view.items.some(item => item.commentId === pending.id)) return
    removed.current = null
    if (document.activeElement === document.body || document.activeElement === pending.trigger) document.getElementById('my-tab-comments')?.focus({ preventScroll: true })
  }, [view, active, scope, lease, memberRequests])
  useLayoutEffect(() => {
    request.current?.abort(); request.current = null
    failedVersion.current = null; pendingFocus.current = null
    keyboard.current = false
  }, [memberRequests])
  useLayoutEffect(() => {
    visible.current = active
    if (!active) { keyboard.current = false; pendingFocus.current = null; pendingPosition.current = null }
  }, [active])
  useEffect(() => () => { request.current?.abort(); request.current = null }, [])
  useLayoutEffect(() => {
    const id = pendingFocus.current; pendingFocus.current = null
    if (id === null || !keyboard.current || !active || !scope.isActive() || !lease.valid() || view.version !== getPostVersion('member-comments') || view.requestRevision !== memberRequests.revision) return
    if (trigger.current?.isConnected && document.activeElement !== trigger.current) return
    const target = document.querySelector<HTMLElement>(`[data-member-comment-id="${id}"] .mypage-comment-link, [data-member-comment-id="${id}"] .mypage-comment-delete`)
      ?? document.getElementById('my-comments-end') ?? document.getElementById('my-comments-empty')
    if (target?.getClientRects().length) target.focus({ preventScroll: true })
  }, [view, active, scope, lease, memberRequests])
  useLayoutEffect(() => {
    if (!active || !pendingPosition.current) return
    const target = pendingPosition.current
    const frame = requestAnimationFrame(() => {
      if (!scope.isActive() || !lease.valid() || !visible.current) return
      restoreActivityPosition(target, 'comments'); pendingPosition.current = null
      onSnapshot(current.current)
    })
    return () => cancelAnimationFrame(frame)
  }, [view, active, scope, lease, onSnapshot])
  const load = useCallback(async (kind: 'more' | 'refresh', source?: { trigger: HTMLElement; keyboard: boolean }) => {
    if (memberRequests.paused || !visible.current || !scope.isActive() || !lease.valid() || (request.current && (kind === 'more' || source))) return
    const previous = current.current, requestedVersion = getPostVersion('member-comments')
    if (kind === 'more' && (!previous.hasNext || previous.version !== requestedVersion || previous.requestRevision !== memberRequests.revision)) return
    request.current?.abort()
    const session = captureMemberRequest(memberId)
    if (!session.isCurrent()) return
    const controller = new AbortController(); request.current = controller
    const valid = () => !controller.signal.aborted && request.current === controller && scope.isActive() && lease.valid() && session.isCurrent()
    trigger.current = source?.trigger ?? null; keyboard.current = source?.keyboard ?? false; pendingFocus.current = null
    setBusy(kind); setNotice('')
    try {
      const data = kind === 'more'
        ? { ...await getMemberComments(previous.nextCursor, controller.signal), segments: previous.segments + 1 }
        : await refreshMemberComments(previous.segments, controller.signal)
      if (!valid() || requestedVersion !== getPostVersion('member-comments')) return
      const items = kind === 'more' ? mergeMemberComments(previous.items, data.items) : data.items
      const updated = { ...data, items, version: requestedVersion, requestRevision: memberRequests.revision }
      if (kind === 'refresh' && previous.segments && visible.current) pendingPosition.current = activityPosition('comments')
      if (keyboard.current && visible.current && document.activeElement === trigger.current) pendingFocus.current = items[kind === 'more' ? previous.items.length : 0]?.commentId ?? 0
      current.current = updated; setView(updated); onSnapshot(updated); failedVersion.current = null; setError(null)
      setNotice(kind === 'more' ? `${items.length-previous.items.length}개를 더 불러왔습니다.${data.hasNext ? '' : ' 마지막 댓글입니다.'}` : '내 댓글을 확인했습니다.')
    } catch (cause) {
      if (!valid() || requestedVersion !== getPostVersion('member-comments')) return
      if (cause instanceof MemberApiError && cause.status === 401) { session.expire(); return }
      failedVersion.current = requestedVersion
      setError({ kind, message: cause instanceof Error ? cause.message : '내 댓글을 불러오지 못했습니다. 다시 시도해 주세요.' })
    } finally { if (request.current === controller) { request.current = null; if (scope.isActive() && lease.valid()) setBusy(null) } }
  }, [captureMemberRequest, memberRequests, memberId, scope, lease, onSnapshot])
  useEffect(() => {
    if (active && !restoring && (view.version !== version || view.requestRevision !== memberRequests.revision) && failedVersion.current !== version) void load('refresh')
  }, [active, restoring, version, view.version, view.requestRevision, memberRequests, load])
  return <>
    {!view.segments && (!error || !!busy) && <p role="status" className="mypage-list-message">내 댓글을 불러오고 있습니다.</p>}
    {!!view.segments && !view.items.length && <p id="my-comments-empty" tabIndex={-1} className="mypage-list-message">아직 작성한 댓글이 없어요.</p>}
    <ul className="mypage-post-list mypage-comment-list">{view.items.map(comment => <li key={comment.commentId} data-member-comment-id={comment.commentId} className={comment.visibility !== 'VISIBLE' ? 'mypage-hidden-comment' : undefined}>
      <div className="mypage-post-copy">{comment.visibility === 'VISIBLE' ? <>
        <p className="mypage-post-meta">{POST_TOPICS[comment.post.topic]} · {POST_TYPES[comment.post.type]} · <WrittenTime value={comment.createdAt} />{comment.contentUpdatedAt !== null && <span> · 수정됨</span>}</p>
        <div className="mypage-comment-bubble">
          <span className="mypage-comment-label">내가 쓴 댓글</span>
          <div className="mypage-comment-body">
            <p className="mypage-comment-content">{comment.content}</p>
            <span className="post-like-count mypage-comment-likes" data-liked={comment.liked}><svg width="16" height="16" viewBox="0 0 24 24" fill={comment.liked ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.75" aria-hidden="true" focusable="false"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg><span className="mypage-comment-like-count">좋아요 {comment.likeCount.toLocaleString('ko-KR')}</span><span className="sr-only">{comment.liked ? '개, 내가 좋아요한 댓글' : '개, 내가 좋아요하지 않은 댓글'}</span></span>
          </div>
        </div>
        <Link className="mypage-comment-link" href={`/community/posts/${comment.post.postId}?from=%2Fmypage#comment-${comment.commentId}`} prefetch={false} scroll={false} onNavigate={onLeaving}><span className="mypage-comment-source-label">원문 게시글</span><span>{comment.post.title}</span><span aria-hidden="true">→</span></Link></> : <>
        <p className="mypage-post-meta"><WrittenTime value={comment.createdAt} anchored /></p>
        <p className="mypage-hidden-comment-notice">{comment.visibility === 'PARENT_HIDDEN' ? '원문 게시글 숨김으로 표시가 제한된 댓글입니다.' : '운영 정책에 따라 숨겨진 댓글입니다.'}</p>
        <MemberHiddenCommentDelete key={`${comment.commentId}:${comment.visibility}`} createdAt={comment.createdAt} memberId={memberId} commentId={comment.commentId} postId={comment.post.postId} active={active} onRemoved={afterRemoval} />
      </>}</div>
    </li>)}</ul>
    {busy === 'refresh' && !!view.segments && <p role="status">내 댓글을 갱신하고 있습니다.</p>}
    <div className={error ? 'mypage-list-message' : undefined}>
      {error && <p role="alert">{error.message}</p>}
      {(view.hasNext || error) && <button className={`iyum-button iyum-button-outline${error ? '' : ' mypage-post-more'}`}
        aria-disabled={!!busy || memberRequests.paused || (!error && (view.version !== version || view.requestRevision !== memberRequests.revision))} onBlur={() => { keyboard.current = false }}
        onClick={event => { void load(error?.kind ?? 'more', { trigger: event.currentTarget, keyboard: event.detail === 0 }) }}>
        {busy ? '불러오는 중…' : error ? `내 댓글 ${error.kind === 'more' ? '더 보기 ' : ''}다시 시도` : '더 보기'}
      </button>}
    </div>
    {!!view.items.length && !view.hasNext && <p id="my-comments-end" tabIndex={-1} className="mypage-list-end">마지막 댓글까지 확인했어요.</p>}
    <p className="sr-only" role="status">{notice}</p>
  </>
}
