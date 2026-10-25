"use client"
import { useEffect, useLayoutEffect, useRef, useState } from "react"
import { startToastLifetime } from "@/lib/toast-lifetime"

export default function CommentSuccessToast({ message, onDismiss }: { message: string; onDismiss: () => void }) {
  const [leaving, setLeaving] = useState(false)
  const element = useRef<HTMLDivElement>(null)
  useEffect(() => startToastLifetime(() => setLeaving(true), onDismiss), [onDismiss])
  useLayoutEffect(() => {
    const header = document.querySelector(".iyum-header")
    const position = () => element.current?.style.setProperty("--toast-header-bottom", `${Math.max(0, header?.getBoundingClientRect().bottom ?? 0) + 12}px`)
    position()
    const observer = new ResizeObserver(position)
    if (header) observer.observe(header)
    window.addEventListener("scroll", position, { passive: true })
    window.addEventListener("resize", position)
    window.visualViewport?.addEventListener("resize", position)
    return () => {
      observer.disconnect()
      window.removeEventListener("scroll", position)
      window.removeEventListener("resize", position)
      window.visualViewport?.removeEventListener("resize", position)
    }
  }, [])
  return <div ref={element} className={`comment-success-toast${leaving ? " is-leaving" : ""}`}>
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" focusable="false"><path d="m5 12 4 4L19 6" /></svg>
    <span>{message}</span>
  </div>
}
