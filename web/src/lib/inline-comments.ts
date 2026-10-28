import { getComments, mergeComments, type CommentPage } from './community-comments'
export type InlineCommentsSnapshot = CommentPage & {
  open: boolean; segments: number; commentsVersion: number; revision: number
  error?: { kind: 'more' | 'refresh'; message: string; attempt: string; missing?: boolean }
}
export const emptyInlineComments: InlineCommentsSnapshot = { items: [], hasNext: false, nextCursor: null, open: false, segments: 0, commentsVersion: -1, revision: -1 }
// Build a complete replacement before publishing it. Never mix partially refreshed pages.
export async function refreshInlineComments(postId: string, segments: number, signal: AbortSignal, targetId?: number, read = getComments) {
  let page = await read(postId, null, signal), loaded = 1
  while (page.hasNext && (loaded < Math.max(1, segments) || (targetId !== undefined && !page.items.some(c => c.commentId === targetId)))) {
    const next = await read(postId, page.nextCursor, signal)
    page = { ...next, items: mergeComments(page.items, next.items) }; loaded++
  }
  return { ...page, segments: loaded }
}
