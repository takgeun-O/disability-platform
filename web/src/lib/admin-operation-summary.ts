import { adminCount, adminGet, adminObject, AdminApiError } from "./admin-posts"
import { validAdminDate } from "./admin-dates"
import { writtenInstant } from "./written-time"

export type OperationSummary = {
  queriedAt: string
  baseDate: string
  counts: {
    totalOperationLogCount: number
    todayOperationLogCount: number
    todayHideCount: number
    todayRestoreCount: number
  }
}

export function isOperationSummary(value: unknown): value is OperationSummary {
  if (!adminObject(value) || !adminObject(value.counts)) return false
  const counts = value.counts
  return (
    typeof value.queriedAt === "string" &&
    writtenInstant(value.queriedAt) !== null &&
    typeof value.baseDate === "string" &&
    validAdminDate(value.baseDate) &&
    [
      "totalOperationLogCount",
      "todayOperationLogCount",
      "todayHideCount",
      "todayRestoreCount",
    ].every((key) => adminCount(counts[key]))
  )
}

export async function getOperationSummary(
  signal: AbortSignal,
): Promise<OperationSummary> {
  const data = await adminGet(
    "/api/v1/admin/operation-logs/summary",
    signal,
    "운영 기록 요약",
  )
  if (!isOperationSummary(data)) {
    throw new AdminApiError(
      0,
      "운영 기록 요약 응답을 확인하지 못했습니다. 다시 조회해 주세요.",
    )
  }
  return data
}
