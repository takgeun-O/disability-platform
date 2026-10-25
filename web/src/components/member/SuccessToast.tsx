"use client"

import { useEffect, useRef, useState } from 'react'

// One keyed success notice: no focus trap, scroll lock or persistent personal data.
export default function SuccessToast({ message, returnFocus, onDismiss }: {
  message: string; returnFocus: string; onDismiss: () => void
}) {
  const root = useRef<HTMLDivElement>(null)
  const [closing, setClosing] = useState(false)
  useEffect(() => {
    if (closing) {
      const exit = window.setTimeout(onDismiss, 180)
      return () => window.clearTimeout(exit)
    }
    const notice = root.current!
    let remaining = 5_000, started = 0, disposed = false
    let timer: number | undefined
    const paused = () => document.hidden || notice.matches(':hover') || notice.contains(document.activeElement)
    function pause() {
      if (timer === undefined) return
      window.clearTimeout(timer); timer = undefined
      remaining = Math.max(0, remaining - (performance.now() - started))
    }
    function resume() {
      if (disposed || timer !== undefined || paused()) return
      started = performance.now()
      timer = window.setTimeout(() => {
        timer = undefined
        if (!disposed && !paused()) setClosing(true)
      }, remaining)
    }
    const focusOut = () => queueMicrotask(resume)
    const visibility = () => { if (document.hidden) pause(); else resume() }
    notice.addEventListener('pointerenter', pause)
    notice.addEventListener('pointerleave', resume)
    notice.addEventListener('focusin', pause)
    notice.addEventListener('focusout', focusOut)
    document.addEventListener('visibilitychange', visibility)
    resume()
    return () => {
      disposed = true; pause()
      notice.removeEventListener('pointerenter', pause)
      notice.removeEventListener('pointerleave', resume)
      notice.removeEventListener('focusin', pause)
      notice.removeEventListener('focusout', focusOut)
      document.removeEventListener('visibilitychange', visibility)
    }
  }, [closing, onDismiss])
  function close() {
    if (closing) return
    if (root.current?.contains(document.activeElement)) document.querySelector<HTMLElement>(returnFocus)?.focus({ preventScroll: true })
    setClosing(true)
  }
  return <div ref={root} className="mypage-toast">
    <div className={`mypage-toast-card${closing ? ' is-leaving' : ''}`}>
      <svg className="mypage-toast-icon" aria-hidden="true" focusable="false" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></svg>
      <p role="status" aria-atomic="true">{message}</p>
      <button type="button" aria-label="알림 닫기" tabIndex={closing ? -1 : 0} onClick={close}><svg aria-hidden="true" focusable="false" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg></button>
    </div>
  </div>
}
