import { isModerationReasonType, type ModerationReasonType } from "./moderation-reasons"
import { adminCount, adminGet, adminObject, AdminApiError, isAdminPageInfo, type AdminPageInfo } from "./admin-posts"
import { writtenInstant } from "./written-time"

export const LOG_TARGETS = { ALL: "전체", POST: "게시글", COMMENT: "댓글" } as const
export const LOG_ACTIONS = { ALL: "전체", HIDE: "숨김", RESTORE: "복원" } as const
export const LOG_SORTS = { LATEST: "최신순", OLDEST: "오래된순" } as const
export type LogFilters = { reasonType: ModerationReasonType | ""; sort: keyof typeof LOG_SORTS; targetType: keyof typeof LOG_TARGETS; action: keyof typeof LOG_ACTIONS; startDate: string; endDate: string }
export type LogQuery = LogFilters & { page: number }
export const DEFAULT_LOG_QUERY: LogQuery = { reasonType: "", sort: "LATEST", targetType: "ALL", action: "ALL", startDate: "", endDate: "", page: 0 }
export type OperationLog = {
  logId: number; actorMemberId: number; targetType: "POST" | "COMMENT"; targetId: number
  action: "HIDE" | "RESTORE"; reasonType: ModerationReasonType; reasonDetail: string | null; reason: string; createdAt: string
}
export type OperationLogPage = AdminPageInfo & { items: OperationLog[] }
// Calendar-only validation: never round-trip filter values through Date/UTC.
export function validLogDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [year, month, day] = value.split("-").map(Number)
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return year >= 1 && year <= 9999 && month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1]
}
export function logFilterError(filters: LogFilters): string {
  if (!Object.prototype.hasOwnProperty.call(LOG_TARGETS, filters.targetType) || !Object.prototype.hasOwnProperty.call(LOG_ACTIONS, filters.action)) return "대상과 조치를 다시 선택해 주세요."
  if ((filters.reasonType !== "" && !isModerationReasonType(filters.reasonType)) || !Object.prototype.hasOwnProperty.call(LOG_SORTS, filters.sort)) return "사유와 정렬을 다시 선택해 주세요."
  if ((filters.startDate && !validLogDate(filters.startDate)) || (filters.endDate && !validLogDate(filters.endDate)))
    return "날짜는 0001-01-01부터 9999-12-31까지 YYYY-MM-DD 형식으로 입력해 주세요."
  if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) return "시작일은 종료일보다 늦을 수 없습니다."
  return ""
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
export function isOperationLogPage(value: unknown): value is OperationLogPage {
  if (!isAdminPageInfo(value) || !Array.isArray(value.items) || value.items.length > value.size || value.items.length > value.totalElements) return false
  if (value.page >= value.totalPages && value.items.length !== 0) return false
  return value.items.every(item => adminObject(item) && positiveId(item.logId) && positiveId(item.actorMemberId)
    && positiveId(item.targetId) && (item.targetType === "POST" || item.targetType === "COMMENT")
    && (item.action === "HIDE" || item.action === "RESTORE") && typeof item.reason === "string"
    && isModerationReasonType(item.reasonType) && (item.reasonDetail === null || typeof item.reasonDetail === "string")
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
