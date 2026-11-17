import { adminCount, adminGet, adminObject, AdminApiError } from "./admin-posts"
import { validAdminDate } from "./admin-dates"
import { writtenInstant } from "./written-time"

export type ContentSummary = {
  queriedAt: string
  baseDate: string
  counts: {
    todayPostCount: number
    todayCommentCount: number
    hiddenPostCount: number
    hiddenCommentCount: number
  }
}

export function isContentSummary(value: unknown): value is ContentSummary {
  if (!adminObject(value) || !adminObject(value.counts)) return false
  const counts = value.counts
  return (
    typeof value.queriedAt === "string" &&
    writtenInstant(value.queriedAt) !== null &&
    typeof value.baseDate === "string" &&
    validAdminDate(value.baseDate) &&
    [
      "todayPostCount",
      "todayCommentCount",
      "hiddenPostCount",
      "hiddenCommentCount",
    ].every((key) => adminCount(counts[key]))
  )
}

export async function getContentSummary(
  signal: AbortSignal,
): Promise<ContentSummary> {
  const data = await adminGet(
    "/api/v1/admin/content/summary",
    signal,
    "콘텐츠 요약",
  )
  if (!isContentSummary(data)) {
    throw new AdminApiError(
      0,
      "콘텐츠 요약 응답을 확인하지 못했습니다. 다시 조회해 주세요.",
    )
  }
  return data
}
