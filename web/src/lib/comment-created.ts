export type CreatedComment = Readonly<{ commentId: number; content: string }>

// Activity-scoped memory, separate from unsaved drafts and server-sorted lists.
export function createCommentCreated() {
  let owner: number | null = null, generation = 0
  let records: Readonly<Record<number, readonly CreatedComment[]>> = {}
  const seen = new Map<number, Set<number>>()
  const deleted = new Map<number, number>()
  const listeners = new Set<() => void>()
  const publish = (next: typeof records) => { records = next; listeners.forEach(fn => fn()) }
  function clear() { generation++; seen.clear(); deleted.clear(); publish({}) }
  return {
    subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn) } },
    snapshot: () => records,
    setOwner(id: number | null) { if (owner !== id) { owner = id; clear() } },
    clear,
    discardDeleted(id: number, postId: number) {
      if (id !== owner) return
      deleted.set(postId, (deleted.get(postId) ?? 0) + 1); seen.delete(postId)
      if (postId in records) { const next = { ...records }; delete next[postId]; publish(next) }
    },
    capture(id: number, postId: number) {
      const epoch = generation, postEpoch = deleted.get(postId) ?? 0
      const valid = () => owner === id && epoch === generation && postEpoch === (deleted.get(postId) ?? 0)
      return {
        // Called only after the caller's auth/navigation/request acceptance checks.
        record(commentId: number, content: string) {
          if (!valid() || !Number.isSafeInteger(commentId) || commentId <= 0 || seen.get(postId)?.has(commentId) || records[postId]?.some(c => c.commentId === commentId)) return
          publish({ ...records, [postId]: [...(records[postId] ?? []), { commentId, content }] })
        },
        confirm(ids: readonly number[]) {
          if (!valid()) return
          const confirmed = seen.get(postId) ?? new Set<number>()
          ids.forEach(value => confirmed.add(value)); seen.set(postId, confirmed)
          const previous = records[postId]
          if (!previous) return
          const remaining = previous.filter(c => !confirmed.has(c.commentId))
          if (remaining.length === previous.length) return
          const next = { ...records }; if (remaining.length) next[postId] = remaining; else delete next[postId]
          publish(next)
        },
      }
    },
  }
}
