'use client'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import Link from '@/components/AppLink'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { getMemberPosts, MemberApiError, type ActivitySummary } from '@/lib/member-api'
import { refreshMemberPosts, type ActivityTab, type MemberPostSnapshot } from '@/lib/member-activity'
import { mergeFeed, type FeedPosition } from '@/lib/community-feed'
import { consumeListReturn, POST_TOPICS, POST_TYPES, postDate } from '@/lib/community-posts'
import { getPostVersion, usePostVersion } from '@/lib/community-mutations'
import PostListReactions from '@/components/community/PostListReactions'
import MyComments from './MyComments'
import { activityPosition, restoreActivityPosition } from './activity-position'
import type { MemberCommentSnapshot } from '@/lib/member-activity'

const empty: MemberPostSnapshot = { items: [], hasNext: false, nextCursor: null, segments: 0, version: -1, tab: 'posts' }
export default function MyActivity({ memberId, summary, layoutReady }: { memberId: number; summary?: ActivitySummary; layoutReady: boolean }) {
  const { activityCache, captureMemberRequest, memberRequests, likes } = useAuth(), scope = useNavigationRequest(), version = usePostVersion('list')
  const [lease] = useState(() => activityCache.lease(memberId))
  const [view, setView] = useState<MemberPostSnapshot>(empty), current = useRef(view)
  const [ready, setReady] = useState(false), [restoring, setRestoring] = useState(false)
  const [busy, setBusy] = useState<'more' | 'refresh' | null>(null)
  const [error, setError] = useState<{ kind: 'more' | 'refresh'; message: string } | null>(null)
  const [notice, setNotice] = useState('')
  const entry = useRef(''), request = useRef<AbortController | null>(null), failedVersion = useRef<number | null>(null)
  const pendingPosition = useRef<FeedPosition | null>(null), pendingFocus = useRef<number | null>(null)
  const moreTrigger = useRef<HTMLElement | null>(null), keyboardMore = useRef(false)
  const save = useCallback(() => {
    if (entry.current && lease.valid() && document.body.style.position !== 'fixed') lease.write(entry.current, { ...current.current, position: activityPosition(current.current.tab) })
  }, [lease])
  useLayoutEffect(() => {
    request.current?.abort(); request.current = null
    failedVersion.current = null; pendingFocus.current = null
    keyboardMore.current = false
  }, [memberRequests])
  useLayoutEffect(() => {
    let initialized = false
    const frame = requestAnimationFrame(() => {
      if (!scope.isActive() || !lease.valid()) return
      const explicit = consumeListReturn('/mypage'), returnId = explicit ? lease.takeReturn() : null
      const oldId = history.state?.iyumMemberEntry as string | undefined
      const savedId = oldId && lease.read(oldId) ? oldId : returnId
      const saved = savedId ? lease.read(savedId) : undefined
      entry.current = savedId || crypto.randomUUID()
      history.replaceState({ ...history.state, iyumMemberEntry: entry.current }, '')
      if (saved) { current.current = saved; setView(saved); pendingPosition.current = saved.position ?? null; setRestoring(!!saved.position) }
      initialized = true; setBusy(null); setReady(true)
    })
    const scroll = () => { if (initialized && scope.isActive()) save() }
    window.addEventListener('scroll', scroll, { passive: true })
    return () => { cancelAnimationFrame(frame); request.current?.abort(); request.current = null; window.removeEventListener('scroll', scroll); if (initialized && scope.isActive()) save() }
  }, [lease, save, scope])
  useLayoutEffect(() => {
    if (!ready || !layoutReady || !pendingPosition.current) return
    const target = pendingPosition.current
    let frame = requestAnimationFrame(() => { frame = requestAnimationFrame(() => {
      if (!scope.isActive() || !lease.valid()) return
      restoreActivityPosition(target, current.current.tab)
      pendingPosition.current = null; setRestoring(false); save()
    }) })
    return () => cancelAnimationFrame(frame)
  }, [view, ready, layoutReady, lease, scope, save])
  useLayoutEffect(() => {
    const id = pendingFocus.current
    pendingFocus.current = null
    if (id === null || !keyboardMore.current || !scope.isActive() || !lease.valid() || view.tab !== 'posts' || view.version !== getPostVersion('list') || view.requestRevision !== memberRequests.revision) return
    // A completed last page can remove the action button. Otherwise it must still own focus.
    if (moreTrigger.current?.isConnected && document.activeElement !== moreTrigger.current) return
    const target = document.querySelector<HTMLElement>(`[data-member-post-id="${id}"] h3 a`)
      ?? document.getElementById('my-posts-end') ?? document.getElementById('my-posts-empty')
    if (target?.getClientRects().length) target.focus({ preventScroll: true })
  }, [view, lease, scope, memberRequests])
  const load = useCallback(async (kind: 'more' | 'refresh', source?: { trigger: HTMLElement; keyboard: boolean }) => {
    if (memberRequests.paused || !scope.isActive() || !lease.valid() || (request.current && (kind === 'more' || source))) return
    const previous = current.current
    if (kind === 'more' && (!previous.hasNext || previous.version !== getPostVersion('list') || previous.requestRevision !== memberRequests.revision)) return
    request.current?.abort()
    moreTrigger.current = source?.trigger ?? null; keyboardMore.current = source?.keyboard ?? false
    pendingFocus.current = null
    const session = captureMemberRequest(memberId)
    if (!session.isCurrent()) return
    const controller = new AbortController(); request.current = controller
    const valid = () => !controller.signal.aborted && request.current === controller && scope.isActive() && lease.valid() && session.isCurrent()
    const likeRead = likes.read()
    const requestedVersion = getPostVersion('list')
    // Keep the retry action mounted (and focusable) until this request settles.
    setBusy(kind); setNotice('')
    try {
      const data = kind === 'more' ? { ...await getMemberPosts(memberId, previous.nextCursor, controller.signal), segments: previous.segments + 1 }
        : await refreshMemberPosts(memberId, previous.segments, controller.signal)
      if (!valid() || requestedVersion !== getPostVersion('list')) return
      likeRead.accept(data.items)
      const items = kind === 'more' ? mergeFeed(previous.items, data.items) : data.items
      // Tab selection made while a request was pending wins over the old request's snapshot.
      const updated: MemberPostSnapshot = { ...data, items, segments: data.segments,
        version: requestedVersion, requestRevision: memberRequests.revision, tab: current.current.tab, comments: current.current.comments }
      if (kind === 'refresh' && previous.segments && current.current.tab === 'posts') { pendingPosition.current = activityPosition(current.current.tab); setRestoring(true) }
      if (keyboardMore.current && document.activeElement === moreTrigger.current && current.current.tab === 'posts') {
        pendingFocus.current = items[kind === 'more' ? previous.items.length : 0]?.id ?? 0
      }
      setError(null)
      current.current = updated; setView(updated); lease.write(entry.current, { ...updated, position: activityPosition(current.current.tab) }); failedVersion.current = null
      setNotice(kind === 'more' ? `${items.length - previous.items.length}개를 더 불러왔습니다.${data.hasNext ? '' : ' 마지막 게시글입니다.'}` : '내 게시글을 확인했습니다.')
    } catch (cause) {
      if (!valid() || requestedVersion !== getPostVersion('list')) return
      if (cause instanceof MemberApiError && cause.status === 401) { session.expire(); return }
      failedVersion.current = requestedVersion
      setError({ kind, message: cause instanceof Error ? cause.message : '내 게시글을 불러오지 못했습니다. 다시 시도해 주세요.' })
    } finally {
      if (request.current === controller) { request.current = null; if (scope.isActive() && lease.valid()) setBusy(null) }
    }
  }, [captureMemberRequest, memberRequests, lease, memberId, scope, likes])
  useEffect(() => {
    if (ready && view.tab === 'posts' && !restoring && (view.version !== version || view.requestRevision !== memberRequests.revision) && failedVersion.current !== version) void load('refresh')
  }, [ready, restoring, version, view.version, view.requestRevision, memberRequests, view.tab, load])
  function chooseTab(tab: ActivityTab) {
    keyboardMore.current = false; pendingFocus.current = null; pendingPosition.current = null; setRestoring(false)
    const next = { ...current.current, tab }; current.current = next; setView(next)
    if (entry.current) lease.write(entry.current, { ...next, position: activityPosition(current.current.tab) })
  }
  const saveComments = useCallback((comments: MemberCommentSnapshot) => {
    if (!scope.isActive() || !lease.valid()) return
    current.current = { ...current.current, comments }
    if (entry.current) lease.write(entry.current, { ...current.current, position: activityPosition(current.current.tab) })
  }, [lease, scope])
  function leaving() { save(); lease.markReturn(entry.current) }
  return <section className="mypage-activity" aria-labelledby="my-activity-title">
    <h2 id="my-activity-title">내 활동</h2>
    <div className="mypage-tabs"><div role="tablist" aria-label="내 활동">
      {(['posts', 'comments'] as const).map((kind, index) => <button key={kind} id={`my-tab-${kind}`} role="tab" aria-selected={view.tab === kind} aria-controls={`my-panel-${kind}`} tabIndex={view.tab === kind ? 0 : -1}
        onClick={() => chooseTab(kind)} onKeyDown={event => {
          if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
          event.preventDefault(); const next = event.key === 'Home' ? 'posts' : event.key === 'End' ? 'comments' : index === 0 ? 'comments' : 'posts'
          chooseTab(next); document.getElementById(`my-tab-${next}`)?.focus()
        }}>{kind === 'posts' ? '내 게시글' : '내 댓글'}{summary && <span>{(kind === 'posts' ? summary.postCount : summary.commentCount).toLocaleString('ko-KR')}</span>}</button>)}
    </div><span className="mypage-sort">최신순</span></div>
    <div id="my-panel-posts" className="mypage-posts" role="tabpanel" aria-labelledby="my-tab-posts" hidden={view.tab !== 'posts'}>
      {(!ready || (!view.segments && (!error || !!busy))) && <p role="status" className="mypage-list-message">내 게시글을 불러오고 있습니다.</p>}
      {!!view.segments && !view.items.length && <p id="my-posts-empty" tabIndex={-1} className="mypage-list-message">아직 작성한 게시글이 없어요.</p>}
      <ul className="mypage-post-list">{view.items.map(post => <li key={post.id} data-member-post-id={post.id}>
        <div className="mypage-post-copy"><p className="mypage-post-meta">{POST_TOPICS[post.topic]} · {POST_TYPES[post.type]} · <time dateTime={post.createdAt}>{postDate(post.createdAt)}</time></p>
          <h3><Link href={`/community/posts/${post.id}?from=%2Fmypage`} prefetch={false} onNavigate={leaving}>{post.title}</Link></h3>
          <p className="mypage-post-preview">{post.contentPreview}</p></div>
        <PostListReactions post={post} href={`/community/posts/${post.id}?from=%2Fmypage`} onLeaving={leaving}/>
      </li>)}</ul>
      {busy === 'refresh' && !!view.segments && <p role="status">내 게시글을 갱신하고 있습니다.</p>}
      <div className={error ? 'mypage-list-message' : undefined}>
        {error && <p role="alert">{error.message}</p>}
        {/* One button survives more → error → retry; aria-disabled keeps its keyboard position. */}
        {(view.hasNext || error) && <button className={`iyum-button iyum-button-outline${error ? '' : ' mypage-post-more'}`}
          aria-disabled={!!busy || memberRequests.paused || (!error && (view.version !== version || view.requestRevision !== memberRequests.revision))}
          onBlur={() => { keyboardMore.current = false }}
          onClick={event => { void load(error?.kind ?? 'more', { trigger: event.currentTarget, keyboard: event.detail === 0 }) }}>
          {busy ? '불러오는 중…' : error ? `내 게시글 ${error.kind === 'more' ? '더 보기 ' : ''}다시 시도` : '더 보기'}
        </button>}
      </div>
      {!!view.items.length && !view.hasNext && <p id="my-posts-end" tabIndex={-1} className="mypage-list-end">마지막 게시글까지 확인했어요.</p>}
      <p className="sr-only" role="status">{notice}</p>
    </div>
    <div id="my-panel-comments" className="mypage-posts" role="tabpanel" aria-labelledby="my-tab-comments" hidden={view.tab !== 'comments'}>
      {ready && <MyComments memberId={memberId} active={view.tab === 'comments'} restoring={restoring || !layoutReady} initial={view.comments}
        lease={lease} onSnapshot={saveComments} onLeaving={leaving} />}
    </div>
  </section>
}
