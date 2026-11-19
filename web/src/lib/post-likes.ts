import type { LikeState } from './community-api'

// Provider-owned, ephemeral personalized state. Neither browser storage nor a server singleton.
export function createPostLikes() {
  type Entry = LikeState & { pending: boolean; order: number; needsRead?: boolean }
  let epoch = 0, clock = 0, context = ''
  let returnPosition: { href: string; y: number } | null = null
  const entries = new Map<number, Entry>(), pending = new Map<number, symbol>(), listeners = new Set<() => void>()
  const emit = () => listeners.forEach(listener => listener())
  return {
    rememberReturn(href: string, y: number) { returnPosition = { href, y } },
    takeReturn(href: string) { if (returnPosition?.href !== href) return null; const y = returnPosition.y; returnPosition = null; return y },
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    get(id: number) { return entries.get(id) },
    setContext(key: string) {
      if (key === context) return
      context = key; epoch++; entries.clear(); pending.clear(); emit()
    },
    read() {
      const started = ++clock, captured = epoch
      const canAccept = (id: number) => captured === epoch && !pending.has(id) && (entries.get(id)?.order ?? 0) <= started
      return { valid: () => captured === epoch, canAccept, accept(items: (LikeState & { id: number })[]) {
        if (captured !== epoch) return false
        let recoveredChange = false
        for (const item of items) {
          if (!canAccept(item.id)) continue
          const previous = entries.get(item.id)
          if (previous?.needsRead && (previous.liked !== item.liked || previous.likeCount !== item.likeCount)) recoveredChange = true
          entries.set(item.id, { liked: item.liked, likeCount: item.likeCount, pending: false, order: started })
        }
        emit()
        return recoveredChange
      } }
    },
    change(id: number) {
      const old = entries.get(id)
      if (!old || pending.has(id)) return null
      const token = Symbol(), captured = epoch
      pending.set(id, token); entries.set(id, { ...old, pending: true, order: ++clock }); emit()
      const valid = () => captured === epoch && pending.get(id) === token
      return { valid, commit(state: LikeState) {
        if (!valid()) return
        entries.set(id, { ...state, pending: true, order: ++clock }); emit()
      }, uncertain() {
        if (valid()) { entries.set(id, { ...entries.get(id)!, needsRead: true, order: ++clock }); emit() }
      }, finish() {
        if (!valid()) return
        pending.delete(id)
        const latest = entries.get(id)!
        // Also fence GETs started during the mutation when its result was uncertain.
        entries.set(id, { ...latest, pending: false, order: ++clock }); emit()
      } }
    },
  }
}
