'use client'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import Link from '@/components/AppLink'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { getMemberComments, MemberApiError } from '@/lib/member-api'
import { mergeMemberComments, refreshMemberComments, type MemberCommentSnapshot, type createMemberActivityCache } from '@/lib/member-activity'
import { getPostVersion, usePostVersion } from '@/lib/community-mutations'
import { POST_TOPICS, POST_TYPES, postDate } from '@/lib/community-posts'
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
    const target = document.querySelector<HTMLElement>(`[data-member-comment-id="${id}"] .mypage-comment-link`)
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
    <ul className="mypage-post-list mypage-comment-list">{view.items.map(comment => <li key={comment.commentId} data-member-comment-id={comment.commentId}>
      <div className="mypage-post-copy">
        <p className="mypage-post-meta">{POST_TOPICS[comment.post.topic]} · {POST_TYPES[comment.post.type]} · <time dateTime={comment.createdAt}>{postDate(comment.createdAt)}</time></p>
        <div className="mypage-comment-bubble">
          <span className="mypage-comment-label">내가 쓴 댓글</span>
          <p className="mypage-comment-content">{comment.content}</p>
        </div>
        <Link className="mypage-comment-link" href={`/community/posts/${comment.post.postId}?from=%2Fmypage#comment-${comment.commentId}`} prefetch={false} scroll={false} onNavigate={onLeaving}><span className="mypage-comment-source-label">원문 게시글</span><span>{comment.post.title}</span><span aria-hidden="true">→</span></Link>
      </div>
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
