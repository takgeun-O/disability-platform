"use client"
import { useId, useLayoutEffect, useRef, useState } from "react"
import OverlayDialog from "@/components/OverlayDialog"
import {
  calendarDate,
  formatAdminPeriod,
  calendarWeekday,
  dateRangeError,
  monthDays,
  quickDateRange,
  seoulToday,
  shiftCalendarDate,
  validAdminDate,
  type DateRange,
} from "@/lib/admin-dates"

function Calendar({
  label,
  value,
  onSelect,
  onClose,
}: {
  label: string
  value: string
  onSelect: (date: string) => void
  onClose: () => void
}) {
  const uid = useId(),
    [cursor, setCursor] = useState(() =>
      validAdminDate(value) ? value : seoulToday(),
    ),
    focusDay = useRef(false),
    grid = useRef<HTMLDivElement>(null)
  const [year, month, day] = cursor.split("-").map(Number),
    offset = calendarWeekday(year, month, 1)
  useLayoutEffect(() => {
    if (focusDay.current) {
      grid.current
        ?.querySelector<HTMLButtonElement>(`[data-date="${cursor}"]`)
        ?.focus({ preventScroll: true })
      focusDay.current = false
    }
  }, [cursor])
  const move = (delta: number) => {
    focusDay.current = true
    setCursor(shiftCalendarDate(cursor, delta))
  }
  const moveMonth = (delta: number) => {
    const index = (year - 1) * 12 + month - 1 + delta
    if (index < 0 || index >= 9999 * 12) return
    const y = Math.floor(index / 12) + 1,
      m = (index % 12) + 1
    setCursor(calendarDate(y, m, Math.min(day, monthDays(y, m))))
  }
  return (
    <OverlayDialog
      className="admin-calendar"
      titleId={`${uid}-title`}
      onClose={onClose}
    >
      <header>
        <h2 id={`${uid}-title`}>{label} 선택</h2>
        <button type="button" aria-label="달력 닫기" onClick={onClose}>
          ×
        </button>
      </header>
      <div className="admin-calendar-month">
        <button
          type="button"
          aria-label="이전 달"
          disabled={year === 1 && month === 1}
          onClick={() => moveMonth(-1)}
        >
          ‹
        </button>
        <label>
          <span className="sr-only">연도</span>
          <input
            type="number"
            min={1}
            max={9999}
            value={year}
            onChange={(e) => {
              const y = Number(e.target.value)
              if (Number.isInteger(y) && y >= 1 && y <= 9999)
                setCursor(
                  calendarDate(y, month, Math.min(day, monthDays(y, month))),
                )
            }}
          />
        </label>
        <label>
          <span className="sr-only">월</span>
          <select
            value={month}
            onChange={(e) => {
              const m = Number(e.target.value)
              setCursor(
                calendarDate(year, m, Math.min(day, monthDays(year, m))),
              )
            }}
          >
            {Array.from({ length: 12 }, (_, i) => (
              <option key={i} value={i + 1}>
                {i + 1}월
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          aria-label="다음 달"
          disabled={year === 9999 && month === 12}
          onClick={() => moveMonth(1)}
        >
          ›
        </button>
      </div>
      <div
        className="admin-calendar-grid"
        ref={grid}
        aria-label={`${year}년 ${month}월`}
      >
        {["일", "월", "화", "수", "목", "금", "토"].map((d) => (
          <span key={d} aria-hidden="true">
            {d}
          </span>
        ))}
        {Array.from({ length: offset }, (_, i) => (
          <span key={`blank-${i}`} />
        ))}
        {Array.from({ length: monthDays(year, month) }, (_, i) => {
          const date = calendarDate(year, month, i + 1)
          return (
            <button
              key={date}
              type="button"
              data-date={date}
              data-initial-focus={date === cursor ? true : undefined}
              tabIndex={date === cursor ? 0 : -1}
              aria-label={`${year}년 ${month}월 ${i + 1}일`}
              aria-pressed={date === value}
              onClick={() => onSelect(date)}
              onKeyDown={(e) => {
                const moves: Record<string, number> = {
                  ArrowLeft: -1,
                  ArrowRight: 1,
                  ArrowUp: -7,
                  ArrowDown: 7,
                  Home: -((offset + i) % 7),
                  End: 6 - ((offset + i) % 7),
                }
                const delta = moves[e.key]
                if (delta !== undefined) {
                  e.preventDefault()
                  move(delta)
                }
              }}
            >
              {i + 1}
            </button>
          )
        })}
      </div>
      <p>방향키로 날짜를 이동하고 Enter로 선택하세요.</p>
    </OverlayDialog>
  )
}
export default function AdminDateRange({
  label,
  applied,
  onApply,
}: {
  label: "작성" | "처리"
  applied: DateRange
  onApply: (range: DateRange) => void
}) {
  const disclosure = useRef<HTMLDetailsElement>(null)
  const uid = useId(),
    [draft, setDraft] = useState(applied),
    [error, setError] = useState(""),
    [calendar, setCalendar] = useState<"startDate" | "endDate" | null>(null)
  const dirty =
    draft.startDate !== applied.startDate || draft.endDate !== applied.endDate
  const edit = (key: keyof DateRange, value: string) => {
    setDraft({ ...draft, [key]: value })
    setError("")
  }
  const apply = (range: DateRange) => {
    const message = dateRangeError(range)
    setError(message)
    if (!message) {
      onApply({ ...range })
      if (disclosure.current) {
        disclosure.current.open = false
        disclosure.current.querySelector("summary")?.focus({preventScroll:true})
      }
    }
  }
  return (
    <details className="admin-date-range" ref={disclosure} onKeyDown={event=>{if(event.key==="Escape"&&!calendar){event.stopPropagation();if(disclosure.current){disclosure.current.open=false;disclosure.current.querySelector("summary")?.focus()}}}}>
      <summary>{label} 기간: {formatAdminPeriod(applied)}</summary>
      <div className="admin-date-popover">
      <fieldset>
        <legend>{label} 기간</legend>
        <div className="admin-date-inputs">
          {(["startDate", "endDate"] as const).map((key, index) => (
            <div className="admin-date-field" key={key}>
              <label htmlFor={`${uid}-${key}`}>
                {label} {index ? "종료일" : "시작일"}
              </label>
              <div className="admin-date-control" data-invalid={!!error}>
                <input
                  id={`${uid}-${key}`}
                  value={draft[key]}
                  placeholder="YYYY-MM-DD"
                  maxLength={10}
                  aria-invalid={!!error}
                  aria-describedby={error ? `${uid}-error` : undefined}
                  onChange={(e) => edit(key, e.target.value)}
                />
                <button
                  type="button"
                  aria-label={`${label} ${
                    index ? "종료일" : "시작일"
                  } 달력 열기`}
                  onClick={() => setCalendar(key)}
                >
                  <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v5m10-5v5M3 11h18"/></svg>
                </button>
                {draft[key] && <button
                  type="button"
                  aria-label={`${label} ${index ? "종료일" : "시작일"} 지우기`}
                  onClick={() => edit(key, "")}
                >
                  <span aria-hidden="true">×</span>
                </button>}
              </div>
            </div>
          ))}
          <button type="button" className="admin-period-apply" onClick={() => apply(draft)}>
            기간 적용
          </button>
        </div>
        {error && <p className="admin-date-error" id={`${uid}-error`} role="alert"><span aria-hidden="true">!</span>{error}</p>}
      </fieldset>
      <div className="admin-date-quick" aria-label="기간 빠른 선택">
        {([0, 1, 7, 30] as const).map((days, i) => (
          <button
            type="button"
            key={days}
            aria-pressed={applied.startDate===quickDateRange(days).startDate && applied.endDate===quickDateRange(days).endDate}
            onClick={() => {
              const range = quickDateRange(days)
              setDraft(range)
              apply(range)
            }}
          >
            {["전체 기간", "오늘", "최근 7일", "최근 30일"][i]}
          </button>
        ))}
      </div>
      <p className="admin-date-summary">
        <span>적용 기간: {formatAdminPeriod(applied)}</span>
        {dirty && <strong>변경한 기간을 적용해 주세요</strong>}
      </p>

      <button type="button" className="admin-date-close" onClick={()=>{if(disclosure.current){disclosure.current.open=false;disclosure.current.querySelector("summary")?.focus()}}}>기간 설정 닫기</button>
      </div>
      {calendar && (
        <Calendar
          label={`${label} ${calendar === "startDate" ? "시작일" : "종료일"}`}
          value={draft[calendar]}
          onClose={() => setCalendar(null)}
          onSelect={(date) => {
            edit(calendar, date)
            setCalendar(null)
          }}
        />
      )}
    </details>
  )
}
