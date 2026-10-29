// Memory-only drafts survive transient auth checks, never confirmed identity changes.
export function createCommentDrafts() {
  let owner: number | null = null
  let drafts: Readonly<Record<number, string>> = {}
  const listeners = new Set<() => void>()
  const publish = (next: Readonly<Record<number, string>>) => { drafts = next; listeners.forEach(fn => fn()) }
  return {
    subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } },
    snapshot: () => drafts,
    setOwner(id: number | null) { if (id !== owner) { owner = id; publish({}) } },
    clear() { publish({}) },
    set(id: number, post: number, text: string) { if (owner !== id) return; const next = { ...drafts }; if (text) next[post] = text; else delete next[post]; publish(next) },
    // Only call with confirmed deletion evidence; absence from a page is not deletion.
    discardDeleted(id: number, post: number) { if (owner !== id || !(post in drafts)) return; const next = { ...drafts }; delete next[post]; publish(next) },
  }
}
