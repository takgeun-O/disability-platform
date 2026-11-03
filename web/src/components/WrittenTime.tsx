"use client"
import { useEffect, useId, useRef, useSyncExternalStore } from "react"
import { writtenTime } from "@/lib/written-time"
import { writtenClock } from "@/lib/written-clock"
import "@/styles/written-time.css"

export default function WrittenTime({ value, anchored = false }: { value: string; anchored?: boolean }) {
  const id = useId()
  const trigger = useRef<HTMLButtonElement>(null)
  const popup = useRef<HTMLSpanElement>(null)
  useEffect(() => {
    if (!anchored) return
    const close = () => { if (popup.current?.matches(":popover-open")) popup.current.hidePopover() }
    window.addEventListener("scroll", close, true)
    window.addEventListener("resize", close)
    return () => { window.removeEventListener("scroll", close, true); window.removeEventListener("resize", close) }
  }, [anchored])
  const now = useSyncExternalStore(writtenClock.subscribe, writtenClock.getSnapshot, writtenClock.getServerSnapshot)
  const display = writtenTime(value, now)
  return <span className="written-time">
    <button ref={trigger} type="button" className="written-time-trigger" popoverTarget={id}
      aria-label={`${display.label}, 정확한 작성 시각 보기`} aria-describedby={id}>
      <time dateTime={display.valid ? value : undefined}>{display.label}</time>
    </button>
    <span ref={popup} id={id} popover="auto" className="written-time-exact" role="note"
      style={anchored ? { inset: "auto", margin: 0, visibility: "hidden" } : undefined}
      onToggle={event => {
        if (!anchored || event.newState !== "open" || !trigger.current) return
        const panel = event.currentTarget, box = trigger.current.getBoundingClientRect()
        const bounds = panel.getBoundingClientRect()
        panel.style.left = `${Math.max(8, Math.min(box.right - bounds.width, window.innerWidth - bounds.width - 8))}px`
        panel.style.top = `${Math.max(8, box.bottom + bounds.height + 8 <= window.innerHeight ? box.bottom + 6 : box.top - bounds.height - 6)}px`
        panel.style.visibility = "visible"
      }}>작성 시각: {display.exact}</span>
  </span>
}
