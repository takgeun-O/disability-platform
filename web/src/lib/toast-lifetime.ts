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
