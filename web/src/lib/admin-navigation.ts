import { ADMIN_CONTENT_STATUSES, adminKeywordError, normalizeAdminKeyword, normalizeAdminStatuses, type AdminContentStatus, type AdminQuery } from "./admin-posts"
export function adminListQuery(params: Record<string, string | string[] | undefined>): AdminQuery {
  const values = params.statuses === undefined ? (params.status && params.status !== "ALL" ? [params.status].flat() : ADMIN_CONTENT_STATUSES) : [params.statuses].flat()
  const valid = values.filter((value): value is AdminContentStatus => ADMIN_CONTENT_STATUSES.some(status => status === value))
  const statuses = normalizeAdminStatuses(valid.length === values.length ? valid : ADMIN_CONTENT_STATUSES)
  const keyword = typeof params.keyword === "string" && !adminKeywordError(params.keyword) ? normalizeAdminKeyword(params.keyword) : ""
  const page = typeof params.page === "string" && /^\d+$/.test(params.page) ? Number(params.page) : 0
  return {statuses,keyword,page: Number.isSafeInteger(page) && page * 20 <= 2147483647 ? page : 0}
}
export function adminListHref(query: AdminQuery) {
  const params=new URLSearchParams()
  for (const status of normalizeAdminStatuses(query.statuses)) params.append("statuses", status)
  params.set("page",String(query.page))
  if(query.keyword) params.set("keyword",query.keyword)
  return `/admin/posts?${params}`
}
export function adminReturnHref(value: unknown) {
  if(typeof value !== "string" || !value.startsWith("/admin/posts?"))return "/admin/posts"
  const url=new URL(value,"http://internal.invalid")
  if(url.pathname !== "/admin/posts")return "/admin/posts"
  const params: Record<string,string|string[]> = Object.fromEntries(url.searchParams)
  if (url.searchParams.has("statuses")) params.statuses = url.searchParams.getAll("statuses")
  return adminListHref(adminListQuery(params))
}
