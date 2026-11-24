"use client"

import { useEffect, useRef, useState } from "react"

export default function ReplyOwnerMenu({ id, disabled, onEdit, onDelete }: {
  id: number; disabled: boolean; onEdit: () => void; onDelete: () => void
}) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const first = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    if (!open) return
    first.current?.focus({ preventScroll: true })
    const outside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener("pointerdown", outside)
    return () => document.removeEventListener("pointerdown", outside)
  }, [open])
  return <div className="content-more" ref={root} onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
  }}>
    <button type="button" id={`reply-more-${id}`} ref={trigger} className="content-more-trigger"
      aria-label="답글 더보기" aria-expanded={open} disabled={disabled}
      onClick={() => setOpen(value => !value)}>⋯</button>
    {open && <div className="content-more-menu" onKeyDown={event => {
      if (event.key === "Escape") { setOpen(false); trigger.current?.focus() }
    }}>
      <button ref={first} type="button" onClick={() => { setOpen(false); onEdit() }}>수정</button>
      <button type="button" onClick={() => { setOpen(false); onDelete() }}>삭제</button>
    </div>}
  </div>
}
