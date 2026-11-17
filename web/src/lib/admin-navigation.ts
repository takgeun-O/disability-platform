import { reportHref, reportQuery } from "./content-reports"
import { logQueryParams, type LogQuery } from "./admin-operation-logs"
import { isModerationReasonType } from "./moderation-reasons"
import { dateRangeError, validAdminDate } from "./admin-dates"
import { ADMIN_CONTENT_STATUSES, adminKeywordError, normalizeAdminKeyword, normalizeAdminStatuses, type AdminContentStatus, type AdminQuery } from "./admin-posts"
export function adminListQuery(params: Record<string, string | string[] | undefined>): AdminQuery {
  const values = params.statuses === undefined ? (params.status && params.status !== "ALL" ? [params.status].flat() : ADMIN_CONTENT_STATUSES) : [params.statuses].flat()
  const valid = values.filter((value): value is AdminContentStatus => ADMIN_CONTENT_STATUSES.some(status => status === value))
  const statuses = normalizeAdminStatuses(valid.length === values.length ? valid : ADMIN_CONTENT_STATUSES)
  const keyword = typeof params.keyword === "string" && !adminKeywordError(params.keyword) ? normalizeAdminKeyword(params.keyword) : ""
  const page = typeof params.page === "string" && /^\d+$/.test(params.page) ? Number(params.page) : 0
  const startDate=typeof params.startDate==="string"&&validAdminDate(params.startDate)?params.startDate:""
  const endDate=typeof params.endDate==="string"&&validAdminDate(params.endDate)?params.endDate:""
  const range=dateRangeError({startDate,endDate})?{startDate:"",endDate:""}:{startDate,endDate}
  return {...range,sort:params.sort==="OLDEST"?"OLDEST":"LATEST",statuses,keyword,page: Number.isSafeInteger(page) && page * 20 <= 2147483647 ? page : 0}
}
export function adminListHref(query: AdminQuery, selected: number | null = null) {
  const params=new URLSearchParams()
  for (const status of normalizeAdminStatuses(query.statuses)) params.append("statuses", status)
  params.set("page",String(query.page))
  if(query.startDate)params.set("startDate",query.startDate)
  if(query.endDate)params.set("endDate",query.endDate)
  params.set("sort",query.sort??"LATEST")
  if(query.keyword) params.set("keyword",query.keyword)
  if(selected)params.set("selected",String(selected))
  return `/admin/posts?${params}`
}
export function adminReturnHref(value: unknown) {
  const modalId = dashboardReturnReportId(value)
  if (modalId !== null) return `/admin/reports/${modalId}?from=dashboard`
  // Only canonical positive safe-integer detail paths and known lists are allowed.
  if (typeof value === "string" && /^\/admin\/reports\/[1-9]\d*$/.test(value)) {
    const id = adminSelectionId(value.slice("/admin/reports/".length))
    if (id !== null) return `/admin/reports/${id}`
  }
  // Never navigate to raw input.
  if(typeof value !== "string" || !/^\/admin\/(posts|operation-logs|reports)(?:\?|$)/.test(value))return "/admin/posts"
  const url=new URL(value,"http://internal.invalid")
  if(url.origin!=="http://internal.invalid" || url.hash || !["/admin/posts","/admin/operation-logs","/admin/reports"].includes(url.pathname))return "/admin/posts"
  const params: Record<string,string|string[]> = {}
  for(const key of url.searchParams.keys()) {
    const values=url.searchParams.getAll(key)
    params[key]=values.length===1?values[0]:values
  }
  if(url.pathname === "/admin/reports") return reportHref(reportQuery(url.searchParams),adminSelectionId(params.selected))
  return url.pathname==="/admin/operation-logs" ? adminLogHref(adminLogQuery(params),adminSelectionId(params.selected)) : adminListHref(adminListQuery(params),adminSelectionId(params.selected))
}

// Operation log conditions never share the post-list return URL namespace.
export function adminLogQuery(params: Record<string, string | string[] | undefined>): LogQuery {
  const targetType = params.targetType === "POST" || params.targetType === "COMMENT" ? params.targetType : "ALL"
  const action = params.action === "HIDE" || params.action === "RESTORE" ? params.action : "ALL"
  const reasonType = isModerationReasonType(params.reasonType) ? params.reasonType : ""
  const {startDate,endDate,sort,page} = adminListQuery(params)
  return {targetType,action,reasonType,startDate:startDate??"",endDate:endDate??"",sort:sort??"LATEST",page}
}
export function adminLogHref(query: LogQuery, selected: number | null = null) {
  const params = logQueryParams(query)
  params.delete("size")
  if(selected)params.set("selected",String(selected))
  return `/admin/operation-logs?${params}`
}

// UI selection is never passed to list API query builders.
export function adminSelectionId(value: unknown): number | null {
  if(typeof value!=="string" || !/^[1-9]\d*$/.test(value))return null
  const id=Number(value)
  return Number.isSafeInteger(id)?id:null
}

// A durable, exact internal return hint; hard entry still renders the detail page.
export function dashboardReturnReportId(value: unknown): number | null {
  if (typeof value !== "string") return null
  const match = /^\/admin\/reports\/([1-9]\d*)\?from=dashboard$/.exec(value)
  return match ? adminSelectionId(match[1]) : null
}
