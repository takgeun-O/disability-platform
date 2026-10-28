type ClockEnvironment = {
  now: () => number
  visible: () => boolean
  everyMinute: (tick: () => void) => () => void
  onResume: (tick: () => void) => () => void
}
// One timer per browser module, independent of item count. No API/data dependencies.
export function createWrittenClock(environment: ClockEnvironment) {
  const listeners = new Set<() => void>()
  let snapshot: number | null = null
  let stopTimer: (() => void) | undefined, stopEvents: (() => void) | undefined
  function tick() {
    snapshot = environment.now()
    listeners.forEach(listener => listener())
  }
  function resume() {
    stopTimer?.(); stopTimer = undefined
    if (environment.visible()) { tick(); stopTimer = environment.everyMinute(tick) }
  }
  return {
    getSnapshot: () => snapshot,
    getServerSnapshot: () => null,
    subscribe(listener: () => void) {
      listeners.add(listener)
      if (listeners.size === 1) { stopEvents = environment.onResume(resume); resume() }
      return () => {
        listeners.delete(listener)
        if (!listeners.size) {
          stopTimer?.(); stopEvents?.(); stopTimer = stopEvents = undefined; snapshot = null
        }
      }
    },
  }
}
export const writtenClock = createWrittenClock({
  now: () => Date.now(),
  visible: () => document.visibilityState !== "hidden",
  everyMinute(tick) { const timer = window.setInterval(tick, 60_000); return () => window.clearInterval(timer) },
  onResume(tick) {
    document.addEventListener("visibilitychange", tick); window.addEventListener("focus", tick)
    return () => { document.removeEventListener("visibilitychange", tick); window.removeEventListener("focus", tick) }
  },
})
