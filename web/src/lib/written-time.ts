// Display only: never normalize or replace API timestamps/cursors.
const seoul = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
  hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
})
export function writtenInstant(value: string): number | null {
  if (typeof value !== "string") return null
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$/.exec(value)
  if (!m) return null
  const [, y, mo, d, h, mi, s, fraction = "", zone] = m
  const year = Number(y), month = Number(mo), day = Number(d)
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (!year || month < 1 || month > 12 || day < 1 || day > days[month - 1] || Number(h) > 23 || Number(mi) > 59 || Number(s) > 59) return null
  if (zone !== "Z" && (Number(zone.slice(1, 3)) > 23 || Number(zone.slice(4)) > 59)) return null
  const ms = Date.parse(value)
  if (!Number.isFinite(ms)) return null
  // Date.parse truncates submilliseconds; preserve them for relative boundaries only.
  return ms + (fraction ? Number("0." + fraction) * 1000 - Number(fraction.slice(0, 3).padEnd(3, "0")) : 0)
}
export function writtenTime(value: string, now: number | null = null) {
  const instant = writtenInstant(value)
  if (instant === null) return { label: "작성일 확인 불가", exact: "작성 시각을 확인할 수 없습니다.", valid: false }
  const parts = Object.fromEntries(seoul.formatToParts(new Date(instant)).map(p => [p.type, p.value]))
  const date = `${parts.year.padStart(4, "0")}.${parts.month}.${parts.day}.`
  const exact = `${date} ${parts.hour}:${parts.minute}:${parts.second} (한국 시간)`
  let label = date
  if (now !== null && Number.isFinite(now)) {
    const elapsed = now - instant
    if (elapsed < -60_000) label = exact
    else if (elapsed < 60_000) label = "방금 전"
    else if (elapsed < 3_600_000) label = `${Math.floor(elapsed / 60_000)}분 전`
    else if (elapsed < 86_400_000) label = `${Math.floor(elapsed / 3_600_000)}시간 전`
    else if (elapsed < 604_800_000) label = `${Math.floor(elapsed / 86_400_000)}일 전`
  }
  return { label, exact, valid: true }
}
