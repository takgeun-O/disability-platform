import { adminCount, adminGet, adminObject, AdminApiError } from "./admin-posts"
import { validAdminDate } from "./admin-dates"
import { writtenInstant } from "./written-time"

export type ReportSummary = {
  queriedAt: string
  baseDate: string
  counts: {
    pendingReportCount: number
    todayReportCount: number
    todayHandledReportCount: number
  }
}

export function isReportSummary(value: unknown): value is ReportSummary {
  if (!adminObject(value) || !adminObject(value.counts)) return false
  const counts = value.counts
  return typeof value.queriedAt === "string" && writtenInstant(value.queriedAt) !== null &&
    typeof value.baseDate === "string" && validAdminDate(value.baseDate) &&
    ["pendingReportCount", "todayReportCount", "todayHandledReportCount"]
      .every(key => adminCount(counts[key]))
}

export async function getReportSummary(signal: AbortSignal): Promise<ReportSummary> {
  const data = await adminGet("/api/v1/admin/reports/summary", signal, "신고 요약")
  if (!isReportSummary(data)) {
    throw new AdminApiError(0, "신고 요약 응답을 확인하지 못했습니다. 다시 조회해 주세요.")
  }
  return data
}
