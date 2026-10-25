import { CommunityApiError, getFeed, olderThan, type FeedPage } from "./community-api"
import type { PostCursor, PostQuery } from "./community-posts"

export type FeedPosition = { id?: number; next?: number; previous?: number; offset: number; y: number }
export type FeedSnapshot = FeedPage & { segments: number; version: number; position?: FeedPosition }
export const MAX_FEED_SEGMENTS = 50 // 1,000개/방문, 최대 8개 방문을 메모리에만 보관
const entries = new Map<string, { href: string; snapshot: FeedSnapshot }>()
let returnEntry: { id: string; href: string } | null = null
export function cacheFeed(id: string, href: string, snapshot: FeedSnapshot) {
  if (snapshot.items.length > 1000) { entries.delete(id); return }
  entries.delete(id); entries.set(id, { href, snapshot: { ...snapshot, items: snapshot.items.map(item => ({ ...item, liked: false })) } })
  while (entries.size > 8) entries.delete(entries.keys().next().value!)
}
export function readFeedCache(id: string, href: string) {
  const entry = entries.get(id)
  if (!entry || entry.href !== href) return null
  entries.delete(id); entries.set(id, entry)
  return entry.snapshot
}
export function setFeedReturn(id: string, href: string) { returnEntry = { id, href } }
export function takeFeedReturn(href: string) {
  if (returnEntry?.href !== href) return null
  const result = returnEntry.id; returnEntry = null; return result
}
export function mergeFeed(existing: FeedPage["items"], incoming: FeedPage["items"]) {
  const ids = new Set(existing.map(p => p.id))
  return [...existing, ...incoming.filter(p => { if (ids.has(p.id)) return false; ids.add(p.id); return true })]
}
// 변경 후 이미 읽은 범위를 서버에서 확인한다. 새 글을 앞에 끼워 넣지 않는다.
// 최대 기존 구간+2, 52회로 제한한다. 변화가 너무 많으면 기존 목록을 보존하고 명시적 새로고침을 안내한다.
export async function reconcileFeed(query: PostQuery, start: PostCursor | null, previous: FeedSnapshot, signal: AbortSignal,
  read: typeof getFeed = getFeed): Promise<FeedPage> {
  if (!previous.items.length) return read(query, start, signal)
  const tail = previous.items.at(-1)!, ids = new Set(previous.items.map(p => p.id))
  let cursor = start, items: FeedPage["items"] = []
  for (let n = 0; n < Math.min(previous.segments + 2, MAX_FEED_SEGMENTS + 2); n++) {
    const page = await read(query, cursor, signal)
    items = mergeFeed(items, page.items)
    const last = page.items.at(-1)
    if (!page.hasNext || (last && (last.id === tail.id || olderThan(last, { createdAt: tail.createdAt, postId: tail.id })))) {
      const retained = items.filter(p => ids.has(p.id)), end = retained.at(-1)
      if (!end) return { items: [], hasNext: page.hasNext, nextCursor: page.nextCursor }
      const hasNext = page.hasNext || items.some(p => olderThan(p, { createdAt: end.createdAt, postId: end.id }))
      return { items: retained, hasNext, nextCursor: hasNext ? { createdAt: end.createdAt, postId: end.id } : null }
    }
    cursor = page.nextCursor
  }
  throw new CommunityApiError(0, "FEED_CHANGED", "목록 변화가 많아 기존 위치에서 갱신하지 못했습니다. ‘최신 목록 새로고침’으로 확인해 주세요.")
}
