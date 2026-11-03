/** Each displayed event owns its timers; disposal also guards queued callbacks. */
export function startToastLifetime(leave: () => void, remove: () => void) {
  let disposed = false
  let exit: ReturnType<typeof setTimeout> | undefined
  const visible = setTimeout(() => {
    if (disposed) return
    leave()
    exit = setTimeout(() => { if (!disposed) remove() }, 250)
  }, 5000)
  return () => { disposed = true; clearTimeout(visible); clearTimeout(exit) }
}

/** Pausable lifetime for dismissible auth notices; existing comment timers stay unchanged. */
export function startPausableToastLifetime(leave: (value: boolean) => void, remove: () => void, now = () => performance.now()) {
  let remaining = 5000, started = now(), disposed = false, paused = false, exiting = false
  let timer: ReturnType<typeof setTimeout> | undefined
  function schedule() {
    started = now()
    timer = setTimeout(() => {
      if (disposed || paused) return
      exiting = true; leave(true)
      timer = setTimeout(() => { if (!disposed && !paused) remove() }, 200)
    }, remaining)
  }
  schedule()
  return {
    pause() {
      if (disposed || paused) return
      paused = true; clearTimeout(timer)
      remaining = exiting ? 0 : Math.max(0, remaining - (now() - started))
      if (exiting) { exiting = false; leave(false) }
    },
    resume() { if (!disposed && paused) { paused = false; schedule() } },
    dispose() { disposed = true; clearTimeout(timer) },
  }
}
