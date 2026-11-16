"use client"
import { useId, useLayoutEffect, useRef, useState } from "react"

// Measure natural content, not string length: wrapping also changes at narrow widths.
export default function ReportContent({ title, content, duplicate = false }: {
  title: string | null; content: string; duplicate?: boolean
}) {
  const id = useId(), natural = useRef<HTMLDivElement>(null)
  const [expanded, setExpanded] = useState(false)
  const [overflows, setOverflows] = useState(false)
  useLayoutEffect(() => {
    const element = natural.current
    if (!element) return
    const measure = () => {
      const limit = parseFloat(getComputedStyle(element.parentElement!).getPropertyValue("--report-content-limit"))
      setOverflows(element.getBoundingClientRect().height > limit + 1)
    }
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    measure()
    return () => observer.disconnect()
  }, [])
  return <div className="report-content" data-expanded={expanded} data-duplicate={duplicate}>
    <div id={id} className="report-content-viewport" aria-hidden={duplicate && !expanded ? true : undefined}>
      <div ref={natural}>
        {title !== null && <h4>{title}</h4>}
        <div className="report-body">{content}</div>
      </div>
    </div>
    {(duplicate || overflows) && <button type="button" aria-expanded={expanded} aria-controls={id} onClick={() => setExpanded(value => !value)}>
      {expanded ? "접기" : "전체 내용 펼치기"}
    </button>}
  </div>
}
