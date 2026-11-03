"use client"
import { useId, useLayoutEffect, useRef, useState } from "react"
import type { AdminPageInfo } from "@/lib/admin-posts"
export default function AdminPagination({ data, busy, failed, onPage, label = "게시글 페이지" }: {
  data: AdminPageInfo; label?: string; busy: boolean; failed: boolean; onPage: (page: number) => void
}) {
  const uid = useId()
  const [input, setInput] = useState(String(data.page + 1))
  const [error, setError] = useState("")
  const [displayed, setDisplayed] = useState(data)
  const region = useRef<HTMLElement>(null)
  const summary = useRef<HTMLSpanElement>(null)
  // Keep the controls mounted; only a newly accepted server page resets their value.
  if (displayed !== data) {
    setDisplayed(data); setInput(String(data.page + 1)); setError("")
  }
  useLayoutEffect(() => {
    if (busy || failed) return
    const focused = document.activeElement
    if (focused instanceof HTMLButtonElement && region.current?.contains(focused)
      && focused.dataset.boundary === "true") summary.current?.focus({ preventScroll: true })
  }, [data, busy, failed])
  const empty = data.totalPages === 0
  const locked = busy || failed || empty
  const go = (page: number, boundary = false) => { if (!locked && !boundary) { setError(""); onPage(page) } }
  return <nav ref={region} className="admin-pagination" aria-label={label} aria-busy={busy}>
    <div className="admin-page-buttons">
      <button aria-disabled={locked || !data.hasPrevious} data-boundary={!data.hasPrevious} onClick={() => go(0,!data.hasPrevious)}>맨 처음</button>
      <button aria-disabled={locked || !data.hasPrevious} data-boundary={!data.hasPrevious} onClick={() => go(data.page-1,!data.hasPrevious)}>이전</button>
      <span ref={summary} tabIndex={-1} role="status" aria-atomic="true">{busy ? "페이지를 불러오고 있습니다…" : failed ? "페이지 조회에 실패했습니다. 다시 시도해 주세요." : `${empty ? 0 : data.page+1} / ${data.totalPages} 페이지`}</span>
      <button aria-disabled={locked || !data.hasNext} data-boundary={!data.hasNext} onClick={() => go(data.page+1,!data.hasNext)}>다음</button>
      <button aria-disabled={locked || !data.hasNext} data-boundary={!data.hasNext} onClick={() => go(data.totalPages-1,!data.hasNext)}>맨 마지막</button>
    </div>
    <form className="admin-page-jump" noValidate onSubmit={event => {
      event.preventDefault()
      if (locked) return
      const page=Number(input)
      if (!/^\d+$/.test(input) || !Number.isSafeInteger(page) || page<1 || page>data.totalPages) {
        setError(`1부터 ${data.totalPages}까지 페이지 번호를 입력해 주세요.`); return
      }
      go(page-1)
    }}>
      <label htmlFor={`${uid}-input`}>페이지 바로 이동</label>
      <input id={`${uid}-input`} inputMode="numeric" value={empty ? "" : input} readOnly={busy || failed} disabled={empty}
        aria-invalid={!!error} aria-describedby={error ? `${uid}-error` : undefined}
        onChange={event => {setInput(event.target.value); setError("")}} />
      <button aria-disabled={locked} type="submit">이동</button>
      {error && <p id={`${uid}-error`} role="alert">{error}</p>}
    </form>
  </nav>
}
