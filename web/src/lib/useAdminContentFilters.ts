"use client"
import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react"
import { ADMIN_CONTENT_STATUSES, adminKeywordError, normalizeAdminKeyword, normalizeAdminStatuses, sameAdminStatuses, type AdminQuery, type AdminContentStatus } from "./admin-posts"

// Post/comment reads only: moderation writes keep their independent ownership.
export function useAdminContentFilters(initial: AdminQuery) {
  const [query, setQuery] = useState(initial)
  const [statuses, setStatuses] = useState(initial.statuses)
  const [input, setInput] = useState(initial.keyword)
  const [validation, setValidation] = useState("")
  const [pending, setPending] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const generation = useRef(0)
  const read = useRef<AbortController | null>(null)
  const composing = useRef(false)
  const invalidate = useCallback(() => {
    generation.current++
    read.current?.abort()
    if (timer.current !== null) clearTimeout(timer.current)
    timer.current = null
  }, [])
  useEffect(() => invalidate, [invalidate])
  const beginRead = useCallback((controller: AbortController) => {
    read.current = controller
    const owner = generation.current
    return () => owner === generation.current && !controller.signal.aborted
  }, [])
  const change = (next: AdminQuery, force = true) => {
    if(!force&&!pending&&statuses.length&&sameAdminStatuses(query.statuses,next.statuses)&&query.keyword===next.keyword&&query.page===next.page&&(query.sort??"LATEST")===(next.sort??"LATEST")&&(query.startDate??"")===(next.startDate??"")&&(query.endDate??"")===(next.endDate??""))return
    invalidate()
    setPending(false)
    setQuery({...next, statuses: [...next.statuses]})
  }
  const selectStatuses = (values: readonly AdminContentStatus[]) => {
    invalidate() // Also reject delivery during the debounce window, even if transport ignores abort.
    const next = normalizeAdminStatuses(values)
    setStatuses(next)
    setPending(next.length > 0)
    if (!next.length) return
    const owner = generation.current
    timer.current = setTimeout(() => {
      if (owner !== generation.current) return
      timer.current = null
      setQuery({...query, statuses: [...next], page: 0})
      setPending(false)
    }, 200)
  }
  const search = (force = true) => {
    if (composing.current || !statuses.length) return
    const error = adminKeywordError(input)
    setValidation(error)
    if (error) return
    change({...query,statuses, keyword: normalizeAdminKeyword(input), page: 0},force)
  }
  const reset = (force = true) => {
    setStatuses([...ADMIN_CONTENT_STATUSES]); setInput(""); setValidation("")
    change({...initial, ...(initial.sort?{sort:"LATEST",startDate:"",endDate:""}:{}), statuses: ADMIN_CONTENT_STATUSES, keyword: "", page: 0},force)
  }
  const composition = {
    onCompositionStart: () => { composing.current = true },
    onCompositionEnd: () => { composing.current = false },
    onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => {
      if (event.key === "Enter" && (composing.current || event.nativeEvent.isComposing || event.keyCode === 229)) event.preventDefault()
    },
  }
  return {query, setQuery, statuses, input, setInput, validation, setValidation, pending,
    empty: !statuses.length, beginRead, change, selectStatuses, search, reset, composition}
}
