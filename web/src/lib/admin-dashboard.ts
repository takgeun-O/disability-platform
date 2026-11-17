import { adminCount, adminGet, adminObject, AdminApiError } from "./admin-posts"
import { validAdminDate } from "./admin-dates"
import {
  defaultReportQuery,
  isReportReason,
  reportHref,
  type ReportReason,
} from "./content-reports"
import { writtenInstant } from "./written-time"

export type Dashboard = {
  queriedAt: string
  baseDate: string
  counts: {
    pendingReportCount: number
    todayReportCount: number
    todayPostCount: number
    todayMemberCount: number
  }
  pendingReports: {
    reportId: number
    targetType: "POST" | "COMMENT"
    targetId: number
    reasonType: ReportReason
    createdAt: string
  }[]
}
const instant = (value: unknown): value is string =>
  typeof value === "string" && writtenInstant(value) !== null

export function isDashboard(value: unknown): value is Dashboard {
  if (!adminObject(value) || !adminObject(value.counts)) return false
  const counts = value.counts
  return (
    instant(value.queriedAt) &&
    typeof value.baseDate === "string" && validAdminDate(value.baseDate) &&
    ["pendingReportCount", "todayReportCount", "todayPostCount", "todayMemberCount"]
      .every(key => adminCount(counts[key])) &&
    Array.isArray(value.pendingReports) &&
    value.pendingReports.length <= 5 &&
    value.pendingReports.every(item =>
      adminObject(item) &&
      adminCount(item.reportId) && item.reportId > 0 &&
      adminCount(item.targetId) && item.targetId > 0 &&
      (item.targetType === "POST" || item.targetType === "COMMENT") &&
      isReportReason(item.reasonType) && instant(item.createdAt),
    )
  )
}

export async function getDashboard(signal: AbortSignal): Promise<Dashboard> {
  const data = await adminGet("/api/v1/admin/dashboard", signal, "대시보드 정보")
  if (!isDashboard(data))
    throw new AdminApiError(0, "대시보드 응답을 확인하지 못했습니다. 다시 시도해 주세요.")
  return data
}

// Fresh conditions, independent of filters left on the report-management screen.
export function dashboardReportHref(reportId: number | null = null) {
  if (reportId !== null) return `/admin/reports/${reportId}`
  return reportHref({ ...defaultReportQuery, status: "RECEIVED", sort: "OLDEST" })
}
