"use client"
import { type Ref, useImperativeHandle, useLayoutEffect, useRef, useState } from "react"
import { getWeeklyBest, type FeedItem } from "@/lib/community-api"
import type { PublicResult } from "@/lib/community-load"
import Link from "@/components/AppLink"
import { COMMUNITY_TOPICS, topicByValue, topicPath } from "@/lib/community-topics"
import { listHref, type PostQuery } from "@/lib/community-posts"

export function FeedNavigation({ query }: { query: PostQuery }) {
  const [expanded, setExpanded] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)
  // CSS로 숨긴 링크는 Tab 순서에서도 제외한다. 선택한 주제는 항상 접힌 행에 남는다.
  const primary = query.topic || COMMUNITY_TOPICS[0].topic
  const secondary = COMMUNITY_TOPICS.find(space => space.topic !== primary)?.topic
  return <nav className="feed-navigation" aria-label="커뮤니티 카테고리" onKeyDown={e => {
    if (e.key === "Escape" && expanded) { setExpanded(false); toggle.current?.focus() }
  }}>
    <noscript><style>{`.feed-tabs:not(.is-expanded) a { display:flex!important; } .feed-expand { display:none!important; } .feed-navigation { position:static!important; }`}</style></noscript>
    <div id="feed-categories" className={`feed-tabs${expanded ? " is-expanded" : ""}`}>
      <Link className="is-compact-primary" href={listHref({ ...query, topic: "", page: 0 }, "/community")} aria-current={!query.topic ? "page" : undefined}>전체</Link>
      {COMMUNITY_TOPICS.map(space => <Link key={space.slug} className={space.topic === primary ? "is-compact-primary" : space.topic === secondary ? "is-compact-secondary" : undefined} href={listHref({ ...query, page: 0 }, topicPath(space))} aria-current={query.topic === space.topic ? "page" : undefined}>{space.name}</Link>)}
    </div>
    <button ref={toggle} className="feed-expand" aria-controls="feed-categories" aria-expanded={expanded} aria-label={expanded ? "카테고리 접기" : "전체 카테고리 펼치기"} onClick={() => setExpanded(v => !v)}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true" focusable="false"><path d={expanded ? "m6 15 6-6 6 6" : "m6 9 6 6 6-6"}/></svg>
    </button>
  </nav>
}
export function FeedBest({ initial, topic, href, onLeaving, ref }: {
  topic: PostQuery["topic"]
  ref: Ref<() => void>
  initial: PublicResult<FeedItem[]>
  href: string
  onLeaving: () => void
}) {
  const [items, setItems] = useState(initial.data)
  const [busy, setBusy] = useState(false)
  const sequence = useRef(0)
  const [failed, setFailed] = useState(initial.data === null)
  const section = useRef<HTMLElement>(null)
  const oldBottom = useRef<number | null>(null)
  useLayoutEffect(() => {
    if (oldBottom.current !== null && section.current) {
      window.scrollBy({ top: section.current.getBoundingClientRect().bottom - oldBottom.current, behavior: "instant" })
      oldBottom.current = null
    }
  }, [items, failed])

  function saveOffset() {
    const bottom = section.current?.getBoundingClientRect().bottom
    if (bottom !== undefined && (bottom < 0 || document.activeElement?.closest(".feed-posts"))) {
      oldBottom.current = bottom
    }
  }

  async function refresh() {
    const id = ++sequence.current
    setBusy(true)
    try {
      const result = await getWeeklyBest(topic)
      if (id !== sequence.current) return
      saveOffset()
      setItems(result)
      setFailed(false)
    } catch {
      if (id !== sequence.current) return
      if (!failed) saveOffset()
      setFailed(true)
    } finally {
      if (id === sequence.current) setBusy(false)
    }
  }
  useImperativeHandle(ref, () => refresh)

  const space = topicByValue(topic)
  const location = space ? `${space.name} 토픽` : "커뮤니티"
  const failureMessage = items === null ? "주간 베스트를 불러오지 못했어요."
    : items.length ? "순위를 갱신하지 못했어요. 이전 순위를 표시하고 있어요."
    : "순위를 갱신하지 못했어요. 이전 조회에서는 주간 베스트에 오른 글이 없었어요."

  return <aside ref={section} className="feed-best" aria-labelledby="feed-best-title">
    <h2 id="feed-best-title">주간 베스트</h2>
    {items && items.length > 0 && <p>최근 7일 동안 {location}에 작성된 글을 좋아요가 많은 순서로 보여드려요.</p>}
    {failed && <div>
      <p role="status">{busy ? "주간 베스트를 불러오고 있어요." : failureMessage}</p>
      <button type="button" className="iyum-text-link" disabled={busy} onClick={refresh}>다시 시도</button>
    </div>}
    {items && (items.length === 0 ? !failed && <p>최근 7일 동안 {location}에 작성된 글 중 현재 좋아요가 있는 글이 없어요.</p> :
      <ol>{items.map((post, index) => <li key={post.id}>
        <Link href={`/community/posts/${post.id}?from=${encodeURIComponent(href)}`} onNavigate={onLeaving} prefetch={false}>
          <span className="feed-best-rank">{index + 1}<span className="sr-only">위</span></span>
          <strong>{post.title}</strong>
          <span className="feed-best-count">좋아요 {post.likeCount}개</span>
        </Link>
      </li>)}</ol>)}
  </aside>
}
export function FeedAvatar() {
  return <span className="feed-avatar" aria-hidden="true"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" focusable="false"><circle cx="12" cy="8" r="3.3"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/></svg></span>
}
