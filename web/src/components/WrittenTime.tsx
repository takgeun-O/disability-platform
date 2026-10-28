"use client"
import { useId, useSyncExternalStore } from "react"
import { writtenTime } from "@/lib/written-time"
import { writtenClock } from "@/lib/written-clock"
import "@/styles/written-time.css"

export default function WrittenTime({ value }: { value: string }) {
  const id = useId()
  const now = useSyncExternalStore(writtenClock.subscribe, writtenClock.getSnapshot, writtenClock.getServerSnapshot)
  const display = writtenTime(value, now)
  return <span className="written-time">
    <button type="button" className="written-time-trigger" popoverTarget={id}
      aria-label={`${display.label}, 정확한 작성 시각 보기`} aria-describedby={id}>
      <time dateTime={display.valid ? value : undefined}>{display.label}</time>
    </button>
    <span id={id} popover="auto" className="written-time-exact" role="note">작성 시각: {display.exact}</span>
  </span>
}
