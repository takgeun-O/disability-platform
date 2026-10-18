"use client"
import { useRef, useState } from "react"
import Link from "@/components/AppLink"
import { COMMUNITY_TOPICS, topicPath } from "@/lib/community-topics"
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
export function FeedBest({ name }: { name: string }) {
  return <aside className="feed-best" aria-labelledby="feed-best-title">
    <div><h2 id="feed-best-title">{name} 베스트</h2><button type="button" disabled aria-label="이번 주 기간 선택 준비 중">이번 주 <span aria-hidden="true">⌄</span> · 준비 중</button></div>
    <p>인기 게시글을 준비하고 있어요.</p>
  </aside>
}
export function FeedAvatar() {
  return <span className="feed-avatar" aria-hidden="true"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" focusable="false"><circle cx="12" cy="8" r="3.3"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/></svg></span>
}
