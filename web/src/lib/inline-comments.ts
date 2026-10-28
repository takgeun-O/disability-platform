import { getComments, mergeComments, commentCursorKey, commentError, type CommentSort, type CommentPage } from './community-comments'
export type InlineCommentsSnapshot = CommentPage & {
  displayedSort?: CommentSort; sort: CommentSort; cursors?: string[]; open: boolean; segments: number; commentsVersion: number; revision: number
  error?: { kind: 'more' | 'refresh'; message: string; attempt: string; missing?: boolean }
}
export const emptyInlineComments: InlineCommentsSnapshot = { sort: 'OLDEST', items: [], hasNext: false, nextCursor: null, open: false, segments: 0, commentsVersion: -1, revision: -1 }
// Build a complete replacement before publishing it. Never mix partially refreshed pages.
export async function refreshInlineComments(postId: string, segments: number, signal: AbortSignal, targetId?: number, read = getComments, sort: CommentSort = 'OLDEST') {
  let page = await read(postId, null, signal, undefined, sort), loaded = 1
  const cursors = new Set<string>()
  if (page.nextCursor) cursors.add(commentCursorKey(page.nextCursor))
  while (page.hasNext && (loaded < Math.max(1, segments) || (targetId !== undefined && !page.items.some(c => c.commentId === targetId)))) {
    const next = await read(postId, page.nextCursor, signal, undefined, sort)
    if (next.nextCursor && cursors.has(commentCursorKey(next.nextCursor))) throw commentError(200, null, false)
    if (next.nextCursor) cursors.add(commentCursorKey(next.nextCursor))
    page = { ...next, items: mergeComments(page.items, next.items) }; loaded++
  }
  return { ...page, segments: loaded, cursors: [...cursors] }
}
