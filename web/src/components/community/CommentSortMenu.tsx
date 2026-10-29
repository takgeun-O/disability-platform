'use client'
import { useEffect, useId, useRef, useState } from 'react'
import type { CommentSort } from '@/lib/community-comments'
const choices = [['OLDEST', '등록순'], ['LATEST', '최신순'], ['POPULAR', '인기순']] as const
export default function CommentSortMenu({ value, onChange, disabled = false }: {
  value: CommentSort; onChange: (sort: CommentSort) => void; disabled?: boolean
}) {
  const id = useId(), root = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false)
  const shown = open && !disabled
  function close(restore = true) { setOpen(false); if (restore) trigger.current?.focus({ preventScroll: true }) }
  useEffect(() => {
    if (!shown) return
    root.current?.querySelector<HTMLElement>('[aria-checked="true"]')?.focus({ preventScroll: true })
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [shown])
  return <div ref={root} className="comment-sort" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false) }}>
    <button ref={trigger} type="button" className="iyum-text-link" aria-label={`댓글 정렬: ${choices.find(c => c[0] === value)?.[1]}`} aria-haspopup="menu" aria-expanded={shown} aria-controls={shown ? id : undefined} disabled={disabled} onClick={() => setOpen(!shown)} onKeyDown={event => {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); setOpen(true) }
    }}>{choices.find(c => c[0] === value)?.[1]} <span aria-hidden="true">⌄</span></button>
    {shown && <div id={id} role="menu" aria-label="댓글 정렬" className="comment-sort-menu" onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); return }
      const options = [...event.currentTarget.querySelectorAll<HTMLElement>('[role="menuitemradio"]')]
      const index = options.indexOf(document.activeElement as HTMLElement)
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? 2 : event.key === 'ArrowDown' ? (index + 1) % 3 : event.key === 'ArrowUp' ? (index + 2) % 3 : -1
      if (next >= 0) { event.preventDefault(); options[next]?.focus() }
    }}>{choices.map(([sort, label]) => <button key={sort} type="button" role="menuitemradio" aria-checked={value === sort} onClick={() => { close(); onChange(sort) }}><span>{label}</span><span aria-hidden="true">{value === sort ? '✓' : ''}</span></button>)}</div>}
  </div>
}
