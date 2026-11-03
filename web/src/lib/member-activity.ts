import { getMemberPosts, getMemberComments, type MemberPostItem, type MemberPostPage, type MemberCommentItem, type MemberCommentPage } from './member-api'
import { type FeedPosition } from './community-feed'
import type { InlineCommentsSnapshot } from './inline-comments'


export type ActivityTab = 'posts' | 'comments'
export type MemberCommentSnapshot = MemberCommentPage & { requestRevision?: number; segments: number; version: number }
export type MemberPostSnapshot = MemberPostPage & { requestRevision?: number; inlineComments?: Record<number, InlineCommentsSnapshot>; comments?: MemberCommentSnapshot } & { segments: number; version: number; tab: ActivityTab; position?: FeedPosition }
// Per AuthProvider, memory only. A lease belongs to one authenticated session, even if the same member logs in again.
export function createMemberActivityCache() {
  let owner: number | null = null, generation = 0, returnEntry: string | null = null
  const entries = new Map<string, MemberPostSnapshot>()
  return {
    setOwner(memberId: number | null) {
      if (owner === memberId) return
      owner = memberId; generation++; entries.clear(); returnEntry = null
    },
    lease(memberId: number) {
      const ticket = generation
      const valid = () => owner === memberId && ticket === generation
      return {
        valid,
        read: (id: string) => valid() ? entries.get(id) : undefined,
        write(id: string, snapshot: MemberPostSnapshot) {
          if (!valid()) return
          entries.delete(id); entries.set(id, snapshot)
          while (entries.size > 4) entries.delete(entries.keys().next().value!)
        },
        markReturn(id: string) { if (valid()) returnEntry = id },
        takeReturn() { if (!valid()) return null; const id = returnEntry; returnEntry = null; return id },
      }
    },
  }
}
// Rebuild the previously loaded number of complete server pages, atomically. Never invent a cursor
// from the DOM or mix old/new page boundaries after a deletion. A failed page leaves the old snapshot intact.
export async function refreshMemberPosts(memberId: number, segments: number, signal: AbortSignal,
  read: typeof getMemberPosts = getMemberPosts): Promise<MemberPostPage & { segments: number }> {
  let result: MemberPostPage = { items: [], hasNext: false, nextCursor: null }, loaded = 0
  do {
    const page = await read(memberId, result.nextCursor, signal)
    result = { ...page, items: mergeMemberPosts(result.items, page.items) }; loaded++
  } while (loaded < Math.max(1, segments) && result.hasNext)
  return { ...result, segments: loaded }
}

// Incoming rows replace matching IDs, including a newly redacted visibility state.
export function mergeMemberPosts(old: MemberPostItem[], incoming: MemberPostItem[]) {
  return [...new Map([...old, ...incoming].map(item => [item.id, item])).values()]
}
export function mergeMemberComments(old: MemberCommentItem[], incoming: MemberCommentItem[]) {
  return [...new Map([...old, ...incoming].map(item => [item.commentId, item])).values()]
}
export async function refreshMemberComments(segments: number, signal: AbortSignal,
  read: typeof getMemberComments = getMemberComments): Promise<MemberCommentSnapshot> {
  let result: MemberCommentPage = { items: [], hasNext: false, nextCursor: null }, loaded = 0
  do {
    const page = await read(result.nextCursor, signal)
    result = { ...page, items: mergeMemberComments(result.items, page.items) }; loaded++
  } while (loaded < Math.max(1, segments) && result.hasNext)
  return { ...result, segments: loaded, version: -1 }
}
