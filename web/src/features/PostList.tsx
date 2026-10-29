"use client"
import WrittenTime from "@/components/WrittenTime"
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react"
import CommentSuccessToast from "@/components/community/CommentSuccessToast"
import PostListReactions from "@/components/community/PostListReactions"
import { useAuth } from "@/components/AuthProvider"
import { useRouter } from "next/navigation"
import Link from "@/components/AppLink"
import { useNavigationRequest } from "@/components/NavigationGuard"
import { FeedAvatar, FeedBest, FeedNavigation } from "@/components/community/FeedNavigation"
import { getPostVersion, usePostVersion } from "@/lib/community-mutations"
import { CommunityApiError, getFeed } from "@/lib/community-api"
import { cacheFeed, readFeedCache, setFeedReturn, takeFeedReturn, mergeFeed, reconcileFeed, MAX_FEED_SEGMENTS, type FeedSnapshot, type FeedPosition } from "@/lib/community-feed"
import { consumeListReturn, listHref, POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import { topicByValue, type CommunityTopic } from "@/lib/community-topics"
import type { InitialFeed } from "@/lib/community-load"

function position(): FeedPosition {
  const rows = [...document.querySelectorAll<HTMLElement>("[data-feed-id]")]
  const index = rows.findIndex(el => el.getBoundingClientRect().bottom > 90)
  const row = rows[index]
  return { id: row ? Number(row.dataset.feedId) : undefined, next: Number(rows[index+1]?.dataset.feedId) || undefined,
    previous: Number(rows[index-1]?.dataset.feedId) || undefined, offset: row?.getBoundingClientRect().top ?? 0, y: window.scrollY }
}
export default function PostList({ initial, space }: { initial: InitialFeed; space?: CommunityTopic }) {
  const { likes, memberRequests, status, pending } = useAuth()
  const authKey = `${memberRequests.revision}:${status}:${pending}`, loadedAuth = useRef("")
  const router = useRouter(), scope = useNavigationRequest()
  const version = usePostVersion("list"), href = initial.href
  const [view, setView] = useState<FeedSnapshot>({ ...(initial.data ?? { items: [], hasNext: false, nextCursor: null }), segments: 1, version: 0 })
  const currentView = useRef(view)
  const [ready, setReady] = useState(false), [restoring, setRestoring] = useState(false)
  const [busy, setBusy] = useState<"more" | "refresh" | null>(null)
  const [error, setError] = useState<{ kind: "more" | "refresh"; latest: boolean; message: string } | null>(initial.error ? { kind: "refresh", latest: false, message: initial.error.message } : null)
  const [auto, setAuto] = useState(true)
  const toastSequence = useRef(0)
  const [toast, setToast] = useState<{ id: number; valid: () => boolean; dismiss: () => void } | null>(null)
  const entry = useRef(""), pendingPosition = useRef<FeedPosition | null>(null), request = useRef<AbortController | null>(null)
  const sentinel = useRef<HTMLDivElement>(null), busyRef = useRef(false)
  const layoutPosition = useRef<FeedPosition | null>(null)
  const save = useCallback(() => {
    if (entry.current) cacheFeed(entry.current, href, { ...currentView.current, position: position() })
  }, [href])
  useLayoutEffect(() => {
    let initialized = false
    const frame = requestAnimationFrame(() => {
    if (!scope.isActive()) return
    // 복귀 의도는 취소되지 않은 초기화에서만 소비한다.
    // dev Strict Mode의 setup→cleanup→setup에서도 첫 예약이 의도를 잃어버리지 않는다.
    const explicit = consumeListReturn(href)
    const oldId = window.history.state?.iyumFeedEntry as string | undefined
    const returnId = explicit ? takeFeedReturn(href) : null
    const saved = (oldId && readFeedCache(oldId, href)) || (returnId && readFeedCache(returnId, href))
    entry.current = oldId && readFeedCache(oldId, href) ? oldId : crypto.randomUUID()
    history.replaceState({ ...history.state, iyumFeedEntry: entry.current }, "")
    if (saved) {
      currentView.current = saved; setView(saved); pendingPosition.current = saved.position ?? null
      setRestoring(!!saved.position); setAuto(false); setError(null)
    }
    initialized = true
    setReady(true)
    if (!saved) save()
    })
    const scroll = () => { if (initialized && scope.isActive()) save() }
    window.addEventListener("scroll", scroll, { passive: true })
    return () => { cancelAnimationFrame(frame); if (initialized && scope.isActive()) save(); request.current?.abort(); busyRef.current = false; window.removeEventListener("scroll", scroll) }
  }, [href, save, scope])
  useLayoutEffect(() => {
    if (!ready || !pendingPosition.current) return
    const target = pendingPosition.current
    let frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => {
        if (!scope.isActive()) return
        const row = [target.id, target.next, target.previous].map(id => document.querySelector<HTMLElement>(`[data-feed-id="${id}"]`)).find(Boolean)
        if (row) window.scrollBy({ top: row.getBoundingClientRect().top - target.offset, behavior: "instant" })
        else window.scrollTo({ top: target.y, behavior: "instant" })
        pendingPosition.current = null; setRestoring(false); save()
      })
    })
    return () => cancelAnimationFrame(frame)
  }, [view, ready, scope, save])
  // Inline status changes must preserve the current reading anchor in the same
  // commit, not after the next network response (the reader may scroll meanwhile).
  useLayoutEffect(() => {
    const target = layoutPosition.current
    layoutPosition.current = null
    if (!target || !scope.isActive()) return
    const row = document.querySelector<HTMLElement>(`[data-feed-id="${target.id}"]`)
    if (row) window.scrollBy({ top: row.getBoundingClientRect().top - target.offset, behavior: "instant" })
    save()
  }, [error, busy, scope, save])
  // 복귀 직후 observer는 멈추고 사용자의 다음 스크롤부터 재개한다.
  useEffect(() => {
    const arm = () => { if (!restoring && !error) setAuto(true) }
    window.addEventListener("wheel", arm, { passive: true }); window.addEventListener("touchmove", arm, { passive: true })
    const key = (e: KeyboardEvent) => { if (["PageDown", "ArrowDown", "End", " "].includes(e.key)) arm() }
    window.addEventListener("keydown", key)
    return () => { window.removeEventListener("wheel", arm); window.removeEventListener("touchmove", arm); window.removeEventListener("keydown", key) }
  }, [restoring, error])
  const load = useCallback(async (kind: "more" | "refresh", latest = false) => {
    if (status === "checking" || pending || memberRequests.paused || !scope.isActive() || (busyRef.current && kind === "more")) return
    const previous = currentView.current
    if (kind === "more" && (!previous.hasNext || previous.segments >= MAX_FEED_SEGMENTS)) return
    request.current?.abort()
    const controller = new AbortController(); request.current = controller
    const likeRead = likes.read()
    const valid = () => likeRead.valid() && !controller.signal.aborted && scope.isActive() && request.current === controller
    const requestedVersion = getPostVersion("list")
    layoutPosition.current = position()
    busyRef.current = true; setBusy(kind); setError(null)
    try {
      const data = kind === "more" ? await getFeed(initial.query, previous.nextCursor, controller.signal) : latest ?
        await getFeed(initial.query, null, controller.signal) : await reconcileFeed(initial.query, initial.cursor, previous, controller.signal)
      if (!valid() || requestedVersion !== getPostVersion("list")) return
      const refreshForAuth = loadedAuth.current !== authKey && previous.version === requestedVersion
      likeRead.accept(data.items); loadedAuth.current = authKey
      // Preserve the current anchor, including scrolling during the request.
      if (kind === "refresh" && !latest) { pendingPosition.current = position(); setRestoring(true); if (!refreshForAuth) setAuto(false) }
      const updated = { ...data, items: kind === "more" ? mergeFeed(previous.items, data.items) : data.items,
        segments: kind === "more" ? previous.segments + 1 : latest ? 1 : previous.segments, version: requestedVersion }
      currentView.current = updated; setView(updated)
      cacheFeed(entry.current, href, { ...updated, position: position() })
      if (kind === "refresh" && latest) {
        const id = ++toastSequence.current
        setToast({ id, valid: () => likeRead.valid() && scope.isActive(), dismiss: () => setToast(previous => previous?.id === id ? null : previous) })
      }
    } catch (e) {
      if (valid()) { layoutPosition.current = position(); loadedAuth.current = authKey; setAuto(false); setError({ kind, latest, message: e instanceof CommunityApiError ? e.message : "게시글을 불러오지 못했습니다. 다시 시도해 주세요." }) }
    } finally {
      if (request.current === controller) { busyRef.current = false; if (scope.isActive()) setBusy(null) }
    }
  }, [initial.query, initial.cursor, href, scope, likes, authKey, status, pending, memberRequests.paused])
  useEffect(() => {
    if (ready && !restoring && (currentView.current.version !== version || loadedAuth.current !== authKey)) void load("refresh")
  }, [ready, restoring, version, authKey, load])
  useEffect(() => {
    if (!ready || restoring || view.version !== version || busy || error || !auto || !view.hasNext || view.segments >= MAX_FEED_SEGMENTS || !sentinel.current || !("IntersectionObserver" in window)) return
    const observer = new IntersectionObserver(entries => { if (entries.some(e => e.isIntersecting)) void load("more") }, { rootMargin: "120px" })
    observer.observe(sentinel.current)
    return () => observer.disconnect()
  }, [ready, restoring, version, view.version, busy, error, auto, view.hasNext, view.segments, load])
  function leaving() { save(); setFeedReturn(entry.current, href) }
  const selected = space ?? topicByValue(initial.query.topic), name = selected?.name ?? "전체"
  const pathname = href.split("?")[0]
  const create = new URLSearchParams({ from: href }); if (initial.query.type) create.set("type", initial.query.type); if (initial.query.topic) create.set("topic", initial.query.topic)
  return <main id="main-content" className="community-screen community-feed">
    <div className="feed-container">
      <header className="feed-heading"><div><h1>{selected?.name ?? "커뮤니티"}</h1><p>{selected ? selected.guidance : "서로의 질문과 경험을 나눠요. 공식 정보와 개인 경험은 구분하고, 개인정보는 보호해 주세요."}</p></div>
        <Link className="iyum-button iyum-button-primary" href={`/community/posts/create?${create}`} onNavigate={leaving}>＋ 글쓰기</Link></header>
      <FeedNavigation query={initial.query} />
      <FeedBest name={name} />
      <section aria-label="게시글 피드"><div className="feed-list-heading"><h2>{name} 게시글</h2><div className="feed-heading-actions"><span>최신순</span>        <div className="feed-refresh"><button className="feed-refresh-button" aria-label="최신 목록 새로고침" aria-busy={busy === "refresh"} aria-disabled={!!busy} onClick={() => {
          if (busyRef.current) return
          if (initial.cursor) router.push(listHref(initial.query, pathname))
          else { setAuto(false); void load("refresh", true) }
        }}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 6.1a8 8 0 0 1 13.5 5.4M4.4 12.5a8 8 0 0 0 13.5 5.4"/></svg><span>{busy === "refresh" ? "새로고침 중…" : "최신 목록 새로고침"}</span></button></div>
</div></div>
        <div className="feed-tools"><nav className="feed-types" aria-label="글 유형"><Link href={listHref({ ...initial.query, type: "", page: 0 }, pathname)} aria-current={!initial.query.type ? "page" : undefined}>전체 유형</Link>
          {Object.entries(POST_TYPES).map(([type, label]) => <Link key={type} href={listHref({ ...initial.query, type: type as keyof typeof POST_TYPES, page: 0 }, pathname)} aria-current={initial.query.type === type ? "page" : undefined}>{label}</Link>)}
        </nav>
        </div>
        <div role="status" aria-atomic="true" className="comment-toast-announcer">
          {toast && toast.valid() && <CommentSuccessToast key={toast.id} message="게시글 목록을 새로고침했습니다." onDismiss={toast.dismiss} />}
        </div>
        <p role="status" className={busy === "refresh" ? "sr-only" : "community-help"}>{busy === "more" ? "다음 게시글을 불러오고 있습니다." : busy ? "목록을 갱신하고 있습니다. 기존 글은 유지됩니다." : ""}</p>
        {error && <div className="community-error" role="alert"><p>{error.message}</p><button className="iyum-text-link" onClick={() => void load(error.kind, error.latest)} disabled={!!busy}>다시 시도</button></div>}
        {!error && !busy && !view.items.length && <div className="community-empty"><h3>아직 표시할 게시글이 없어요.</h3><p>{selected ? `${name}에 관한 첫 이야기를 나눠 주세요.` : "첫 질문이나 경험을 나눠 주세요."}</p><Link className="iyum-text-link" href={`/community/posts/create?${create}`} onNavigate={leaving}>글쓰기</Link></div>}
        <ol className="feed-posts" aria-label="게시글 목록" aria-busy={!!busy}>
          {view.items.map(post => <li key={post.id} data-feed-id={post.id}><article>
            <FeedAvatar/><div className="feed-post-content"><div className="feed-author"><strong>{post.author.nickname}</strong><div className="feed-meta"><span>{POST_TOPICS[post.topic]}</span><span>{POST_TYPES[post.type]}</span><WrittenTime value={post.createdAt} /></div></div>
            <h3><Link href={`/community/posts/${post.id}?from=${encodeURIComponent(href)}`} onNavigate={leaving} prefetch={false}>{post.title}</Link></h3>
            <p className="feed-preview">{post.contentPreview}</p>
            <PostListReactions post={post} href={`/community/posts/${post.id}?from=${encodeURIComponent(href)}`} onLeaving={leaving}/>
          </div></article></li>)}
        </ol>
        <div ref={sentinel} className="feed-end">
          {(view.hasNext || view.items.length > 0) && view.segments < MAX_FEED_SEGMENTS ? <>
            <button type="button" className="iyum-button iyum-button-outline" onClick={() => void load("more")} aria-disabled={!!busy || !view.hasNext}>{view.hasNext ? "게시글 더 보기" : "마지막 게시글까지 확인했어요."}</button>
            {view.hasNext && <noscript><p><a href={listHref(initial.query, pathname, view.nextCursor)}>다음 게시글 목록</a></p></noscript>}
          </> : view.items.length > 0 && <p>{view.hasNext ? "1,000개까지 읽었습니다. 다음 구간은 새 목록에서 이어서 볼 수 있어요." : "마지막 게시글까지 확인했어요."}</p>}
          {view.hasNext && view.segments >= MAX_FEED_SEGMENTS && <Link href={listHref(initial.query, pathname, view.nextCursor)}>다음 구간으로</Link>}
        </div>
      </section>
    </div>
  </main>
}
