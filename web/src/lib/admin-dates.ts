export type DateRange = {
  startDate: string
  endDate: string
}
export const ADMIN_SORTS = { LATEST: "최신순", OLDEST: "오래된순" } as const
export type AdminSort = keyof typeof ADMIN_SORTS
export const monthDays = (year: number, month: number) =>
  [
    31,
    year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28,
    31,
    30,
    31,
    30,
    31,
    31,
    30,
    31,
    30,
    31,
  ][month - 1]
export const calendarDate = (year: number, month: number, day: number) =>
  `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
export function validAdminDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [y, m, d] = value.split("-").map(Number)
  return (
    y >= 1 && y <= 9999 && m >= 1 && m <= 12 && d >= 1 && d <= monthDays(y, m)
  )
}
export function dateRangeError({ startDate, endDate }: DateRange) {
  if (
    (startDate && !validAdminDate(startDate)) ||
    (endDate && !validAdminDate(endDate))
  )
    return "날짜는 0001-01-01부터 9999-12-31까지 YYYY-MM-DD 형식으로 입력해 주세요."
  return startDate && endDate && startDate > endDate
    ? "종료일을 시작일과 같거나 이후로 선택해 주세요."
    : ""
}
export function seoulToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now)
  return `${parts.find((p) => p.type === "year")!.value.padStart(4, "0")}-${parts.find((p) => p.type === "month")!.value}-${parts.find((p) => p.type === "day")!.value}`
}
// Calendar arithmetic, not timezone conversion of an entered date.
export function shiftCalendarDate(value: string, delta: number) {
  let [y, m, d] = value.split("-").map(Number)
  while (delta > 0) {
    if (value === "9999-12-31") return value
    d++
    if (d > monthDays(y, m)) {
      d = 1
      m++
      if (m > 12) {
        m = 1
        y++
      }
    }
    value = calendarDate(y, m, d)
    delta--
  }
  while (delta < 0) {
    if (value === "0001-01-01") return value
    d--
    if (d < 1) {
      m--
      if (m < 1) {
        m = 12
        y--
      }
      d = monthDays(y, m)
    }
    value = calendarDate(y, m, d)
    delta++
  }
  return value
}
export function quickDateRange(
  days: 0 | 1 | 7 | 30,
  now = new Date(),
): DateRange {
  if (!days) return { startDate: "", endDate: "" }
  const today = seoulToday(now)
  return { startDate: shiftCalendarDate(today, 1 - days), endDate: today }
}
export function calendarWeekday(year: number, month: number, day: number) {
  const offsets = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4]
  const y = month < 3 ? year - 1 : year
  return (
    (y +
      Math.floor(y / 4) -
      Math.floor(y / 100) +
      Math.floor(y / 400) +
      offsets[month - 1] +
      day) %
    7
  )
}

export function formatAdminPeriod({startDate,endDate}: DateRange) {
  const pretty=(value:string)=>value.replace(/-/g, ".")
  if(!startDate&&!endDate)return "전체 기간"
  if(!endDate)return `${pretty(startDate)}부터`
  if(!startDate)return `${pretty(endDate)}까지`
  return `${pretty(startDate)} – ${pretty(endDate)}`
}
