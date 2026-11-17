"use client"
// Next's native-history adapter copies its own tree. Pass only our metadata:
// passing __NA ourselves would bypass its pathname update. The guard owns its index.
// The document token deliberately expires on reload: hard entry uses the page.
let documentToken = ""
const key = "__iyumDashboardReport"
const eventName = "iyum-dashboard-report-navigation"
function token() {
  return documentToken ||= crypto.randomUUID()
}
export function dashboardModalSnapshot(): string {
  const entry = history.state?.[key]
  if (!entry || entry.document !== token() || !Number.isSafeInteger(entry.id) || entry.id <= 0 ||
    location.pathname !== `/admin/reports/${entry.id}`) return ""
  return `${entry.id}:${entry.close === "back" ? "back" : "replace"}`
}
export function subscribeDashboardModal(listener: () => void) {
  window.addEventListener(eventName, listener)
  // NavigationGuard captures and stops rejected/restoring pops first.
  window.addEventListener("popstate", listener)
  return () => {
    window.removeEventListener(eventName, listener)
    window.removeEventListener("popstate", listener)
  }
}
export function openDashboardReport(id: number, returning = false) {
  if (!Number.isSafeInteger(id) || id <= 0) return
  history.pushState({ [key]: {
    document: token(), id, close: !returning && location.pathname === "/admin" ? "back" : "replace",
  } }, "", `/admin/reports/${id}${returning ? "?from=dashboard" : ""}`)
  window.dispatchEvent(new Event(eventName))
}
export function closeDashboardReport() {
  const entry = dashboardModalSnapshot()
  if (!entry) return
  if (entry.endsWith(":back")) history.back()
  else {
    history.replaceState({}, "", "/admin")
    window.dispatchEvent(new Event(eventName))
  }
}
