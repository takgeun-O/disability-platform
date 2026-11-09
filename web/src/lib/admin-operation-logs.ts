import { validAdminDate, dateRangeError } from "./admin-dates"
import { isModerationReasonType, type ModerationReasonType } from "./moderation-reasons"
import { adminCount, adminGet, adminObject, AdminApiError, isAdminPageInfo, type AdminPageInfo } from "./admin-posts"
import { writtenInstant } from "./written-time"

export const LOG_TARGETS = { ALL: "전체", POST: "게시글", COMMENT: "댓글" } as const
export const LOG_ACTIONS = { ALL: "전체", HIDE: "숨김", RESTORE: "복원" } as const
export const LOG_SORTS = { LATEST: "최신순", OLDEST: "오래된순" } as const
export type LogFilters = { reasonType: ModerationReasonType | ""; sort: keyof typeof LOG_SORTS; targetType: keyof typeof LOG_TARGETS; action: keyof typeof LOG_ACTIONS; startDate: string; endDate: string }
export type LogQuery = LogFilters & { page: number }
export const DEFAULT_LOG_QUERY: LogQuery = { reasonType: "", sort: "LATEST", targetType: "ALL", action: "ALL", startDate: "", endDate: "", page: 0 }
export type CurrentLogTarget = { author: { memberId: number; nickname: string; withdrawn: boolean }; title: string | null; contentPreview: string }
export type OperationLog = {
  currentTarget: CurrentLogTarget | null
  logId: number; actorMemberId: number; targetType: "POST" | "COMMENT"; targetId: number
  action: "HIDE" | "RESTORE"; reasonType: ModerationReasonType; reasonDetail: string | null; reason: string; createdAt: string
}
export type OperationLogPage = AdminPageInfo & { items: OperationLog[] }
// Calendar-only validation: never round-trip filter values through Date/UTC.
export const validLogDate = validAdminDate
export function logFilterError(filters: LogFilters): string {
  if (!Object.prototype.hasOwnProperty.call(LOG_TARGETS, filters.targetType) || !Object.prototype.hasOwnProperty.call(LOG_ACTIONS, filters.action)) return "대상과 조치를 다시 선택해 주세요."
  if ((filters.reasonType !== "" && !isModerationReasonType(filters.reasonType)) || !Object.prototype.hasOwnProperty.call(LOG_SORTS, filters.sort)) return "사유와 정렬을 다시 선택해 주세요."
  return dateRangeError(filters)
}
export function logQueryParams(query: LogQuery) {
  const error = logFilterError(query)
  if (error) throw new AdminApiError(400, error)
  if (!adminCount(query.page) || query.page * 20 > 2147483647) throw new AdminApiError(400, "조회할 수 있는 페이지 범위를 벗어났습니다.")
  const params = new URLSearchParams({ targetType: query.targetType, page: String(query.page), size: "20" })
  params.set("sort", query.sort)
  if (query.reasonType) params.set("reasonType", query.reasonType)
  if (query.action !== "ALL") params.set("action", query.action)
  if (query.startDate) params.set("startDate", query.startDate)
  if (query.endDate) params.set("endDate", query.endDate)
  return params
}
const positiveId = (v: unknown): v is number => adminCount(v) && v > 0
function isCurrentTarget(value: unknown, type: unknown): value is CurrentLogTarget | null {
  return value === null || (adminObject(value) && adminObject(value.author)
    && positiveId(value.author.memberId) && typeof value.author.nickname === "string" && typeof value.author.withdrawn === "boolean"
    && (type === "COMMENT" ? value.title === null : typeof value.title === "string") && typeof value.contentPreview === "string")
}
export function isOperationLogPage(value: unknown): value is OperationLogPage {
  if (!isAdminPageInfo(value) || !Array.isArray(value.items) || value.items.length > value.size || value.items.length > value.totalElements) return false
  if (value.page >= value.totalPages && value.items.length !== 0) return false
  return value.items.every(item => adminObject(item) && positiveId(item.logId) && positiveId(item.actorMemberId)
    && positiveId(item.targetId) && (item.targetType === "POST" || item.targetType === "COMMENT")
    && (item.action === "HIDE" || item.action === "RESTORE") && typeof item.reason === "string"
    && isModerationReasonType(item.reasonType) && (item.reasonDetail === null || typeof item.reasonDetail === "string")
    && isCurrentTarget(item.currentTarget, item.targetType)
    && typeof item.createdAt === "string" && writtenInstant(item.createdAt) !== null)
    && new Set(value.items.map(item => item.logId)).size === value.items.length
}
export async function getOperationLogs(query: LogQuery, signal: AbortSignal): Promise<OperationLogPage> {
  try {
    const data = await adminGet("/api/v1/admin/operation-logs?" + logQueryParams(query), signal, "운영 기록")
    if (!isOperationLogPage(data) || data.page !== query.page || data.size !== 20)
      throw new AdminApiError(0, "운영 기록 응답을 확인하지 못했습니다. 다시 시도해 주세요.")
    return data
  } catch (error) {
    if (error instanceof AdminApiError && error.status === 400)
      throw new AdminApiError(400, "조회 조건을 확인해 주세요. 날짜 범위와 페이지가 올바른지 확인한 뒤 다시 검색해 주세요.")
    throw error
  }
}
