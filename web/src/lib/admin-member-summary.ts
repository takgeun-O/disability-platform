import { adminCount, adminGet, adminObject, AdminApiError } from "./admin-posts"
import { validAdminDate } from "./admin-dates"
import { writtenInstant } from "./written-time"

export type MemberSummary = {
  queriedAt: string
  baseDate: string
  counts: {
    currentMemberCount: number
    todayMemberCount: number
    pendingMemberCount: number
    suspendedMemberCount: number
  }
}

export function isMemberSummary(value: unknown): value is MemberSummary {
  if (!adminObject(value) || !adminObject(value.counts)) return false
  const counts = value.counts
  return (
    typeof value.queriedAt === "string" &&
    writtenInstant(value.queriedAt) !== null &&
    typeof value.baseDate === "string" &&
    validAdminDate(value.baseDate) &&
    [
      "currentMemberCount",
      "todayMemberCount",
      "pendingMemberCount",
      "suspendedMemberCount",
    ].every((key) => adminCount(counts[key]))
  )
}

export async function getMemberSummary(
  signal: AbortSignal,
): Promise<MemberSummary> {
  const data = await adminGet(
    "/api/v1/admin/members/summary",
    signal,
    "회원 요약",
  )
  if (!isMemberSummary(data)) {
    throw new AdminApiError(
      0,
      "회원 요약 응답을 확인하지 못했습니다. 다시 조회해 주세요.",
    )
  }
  return data
}
