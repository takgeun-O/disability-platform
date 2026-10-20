import type { ActivityTab } from '@/lib/member-activity'
import type { FeedPosition } from '@/lib/community-feed'
type ActivityPosition = FeedPosition & { neighbors?: number[] }
export function activityPosition(tab: ActivityTab): ActivityPosition {
  const attribute = tab === 'posts' ? 'data-member-post-id' : 'data-member-comment-id'
  const rows = [...document.querySelectorAll<HTMLElement>(`[${attribute}]`)].filter(row => row.getClientRects().length)
  const index = rows.findIndex(row => row.getBoundingClientRect().bottom > 90), row = rows[index]
  return { neighbors: [...rows.slice(index + 1), ...rows.slice(0, index).reverse()].map(row => Number(row.getAttribute(attribute))), id: Number(row?.getAttribute(attribute)) || undefined, next: Number(rows[index+1]?.getAttribute(attribute)) || undefined,
    previous: Number(rows[index-1]?.getAttribute(attribute)) || undefined, offset: row?.getBoundingClientRect().top ?? 0, y: window.scrollY }
}
export function restoreActivityPosition(target: ActivityPosition, tab: ActivityTab) {
  const attribute = tab === 'posts' ? 'data-member-post-id' : 'data-member-comment-id'
  const row = [target.id, target.next, ...(target.neighbors ?? []), target.previous].map(id => document.querySelector<HTMLElement>(`[${attribute}="${id}"]`)).find(Boolean)
  if (row && target.y > 0) window.scrollBy({ top: row.getBoundingClientRect().top - target.offset, behavior: 'instant' })
  else window.scrollTo({ top: target.y, behavior: 'instant' })
}
