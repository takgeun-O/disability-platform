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

function position(): FeedPosition {
  const rows = [...document.querySelectorAll<HTMLElement>('[data-member-post-id]')].filter(row => row.getClientRects().length)
  const index = rows.findIndex(row => row.getBoundingClientRect().bottom > 90), row = rows[index]
  return { id: Number(row?.dataset.memberPostId) || undefined, next: Number(rows[index+1]?.dataset.memberPostId) || undefined,
    previous: Number(rows[index-1]?.dataset.memberPostId) || undefined, offset: row?.getBoundingClientRect().top ?? 0, y: window.scrollY }
}
const empty: MemberPostSnapshot = { items: [], hasNext: false, nextCursor: null, segments: 0, version: -1, tab: 'posts' }
export default function MyActivity({ memberId, summary, layoutReady }: { memberId: number; summary?: ActivitySummary; layoutReady: boolean }) {
  const { activityCache, expire } = useAuth(), scope = useNavigationRequest(), version = usePostVersion('list')
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
    if (entry.current && lease.valid() && document.body.style.position !== 'fixed') lease.write(entry.current, { ...current.current, position: position() })
  }, [lease])
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
      const row = [target.id, target.next, target.previous].map(id => document.querySelector<HTMLElement>(`[data-member-post-id="${id}"]`)).find(Boolean)
      if (row && target.y > 0) window.scrollBy({ top: row.getBoundingClientRect().top - target.offset, behavior: 'instant' })
      else window.scrollTo({ top: target.y, behavior: 'instant' })
      pendingPosition.current = null; setRestoring(false); save()
    }) })
    return () => cancelAnimationFrame(frame)
  }, [view, ready, layoutReady, lease, scope, save])
  useLayoutEffect(() => {
    const id = pendingFocus.current
    pendingFocus.current = null
    if (id === null || !keyboardMore.current || !scope.isActive() || !lease.valid() || view.tab !== 'posts' || view.version !== getPostVersion('list')) return
    // A completed last page can remove the action button. Otherwise it must still own focus.
    if (moreTrigger.current?.isConnected && document.activeElement !== moreTrigger.current) return
    const target = document.querySelector<HTMLElement>(`[data-member-post-id="${id}"] h3 a`)
      ?? document.getElementById('my-posts-end') ?? document.getElementById('my-posts-empty')
    if (target?.getClientRects().length) target.focus({ preventScroll: true })
  }, [view, lease, scope])
  const load = useCallback(async (kind: 'more' | 'refresh', source?: { trigger: HTMLElement; keyboard: boolean }) => {
    if (!scope.isActive() || !lease.valid() || (request.current && (kind === 'more' || source))) return
    const previous = current.current
    if (kind === 'more' && (!previous.hasNext || previous.version !== getPostVersion('list'))) return
    request.current?.abort()
    moreTrigger.current = source?.trigger ?? null; keyboardMore.current = source?.keyboard ?? false
    pendingFocus.current = null
    const controller = new AbortController(); request.current = controller
    const valid = () => !controller.signal.aborted && request.current === controller && scope.isActive() && lease.valid()
    const requestedVersion = getPostVersion('list')
    // Keep the retry action mounted (and focusable) until this request settles.
    setBusy(kind); setNotice('')
    try {
      const data = kind === 'more' ? { ...await getMemberPosts(memberId, previous.nextCursor, controller.signal), segments: previous.segments + 1 }
        : await refreshMemberPosts(memberId, previous.segments, controller.signal)
      if (!valid() || requestedVersion !== getPostVersion('list')) return
      const items = kind === 'more' ? mergeFeed(previous.items, data.items) : data.items
      // Tab selection made while a request was pending wins over the old request's snapshot.
      const updated: MemberPostSnapshot = { ...data, items, segments: data.segments,
        version: requestedVersion, tab: current.current.tab }
      if (kind === 'refresh' && previous.segments) { pendingPosition.current = position(); setRestoring(true) }
      if (keyboardMore.current && document.activeElement === moreTrigger.current && current.current.tab === 'posts') {
        pendingFocus.current = items[kind === 'more' ? previous.items.length : 0]?.id ?? 0
      }
      setError(null)
      current.current = updated; setView(updated); lease.write(entry.current, { ...updated, position: position() }); failedVersion.current = null
      setNotice(kind === 'more' ? `${items.length - previous.items.length}개를 더 불러왔습니다.${data.hasNext ? '' : ' 마지막 게시글입니다.'}` : '내 게시글을 확인했습니다.')
    } catch (cause) {
      if (!valid() || requestedVersion !== getPostVersion('list')) return
      if (cause instanceof MemberApiError && cause.status === 401) { expire(memberId); return }
      failedVersion.current = requestedVersion
      setError({ kind, message: cause instanceof Error ? cause.message : '내 게시글을 불러오지 못했습니다. 다시 시도해 주세요.' })
    } finally {
      if (valid()) { request.current = null; setBusy(null) }
    }
  }, [expire, lease, memberId, scope])
  useEffect(() => {
    if (ready && !restoring && view.version !== version && failedVersion.current !== version) void load('refresh')
  }, [ready, restoring, version, view.version, load])
  function chooseTab(tab: ActivityTab) {
    keyboardMore.current = false; pendingFocus.current = null
    const next = { ...current.current, tab }; current.current = next; setView(next)
    if (entry.current) lease.write(entry.current, { ...next, position: position() })
  }
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
        <span className="mypage-post-comments"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true" focusable="false"><path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z" strokeLinecap="round" strokeLinejoin="round" /></svg>댓글 {post.commentCount.toLocaleString('ko-KR')}</span>
      </li>)}</ul>
      {busy === 'refresh' && !!view.segments && <p role="status">내 게시글을 갱신하고 있습니다.</p>}
      <div className={error ? 'mypage-list-message' : undefined}>
        {error && <p role="alert">{error.message}</p>}
        {/* One button survives more → error → retry; aria-disabled keeps its keyboard position. */}
        {(view.hasNext || error) && <button className={`iyum-button iyum-button-outline${error ? '' : ' mypage-post-more'}`}
          aria-disabled={!!busy || (!error && view.version !== version)}
          onBlur={() => { keyboardMore.current = false }}
          onClick={event => { void load(error?.kind ?? 'more', { trigger: event.currentTarget, keyboard: event.detail === 0 }) }}>
          {busy ? '불러오는 중…' : error ? `내 게시글 ${error.kind === 'more' ? '더 보기 ' : ''}다시 시도` : '더 보기'}
        </button>}
      </div>
      {!!view.items.length && !view.hasNext && <p id="my-posts-end" tabIndex={-1} className="mypage-list-end">마지막 게시글까지 확인했어요.</p>}
      <p className="sr-only" role="status">{notice}</p>
    </div>
    <div id="my-panel-comments" role="tabpanel" aria-labelledby="my-tab-comments" hidden={view.tab !== 'comments'} tabIndex={0}><p>내 댓글 목록은 준비 중이에요.</p><p>활동 내역을 이곳에서 확인할 수 있도록 준비하고 있습니다.</p></div>
  </section>
}
