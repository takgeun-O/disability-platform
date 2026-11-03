import { ADMIN_STATUSES, adminKeywordError, normalizeAdminKeyword, type AdminQuery } from "./admin-posts"
export function adminListQuery(params: Record<string, string | string[] | undefined>): AdminQuery {
  const status = typeof params.status === "string" && Object.prototype.hasOwnProperty.call(ADMIN_STATUSES,params.status) ? params.status as AdminQuery["status"] : "ALL"
  const keyword = typeof params.keyword === "string" && !adminKeywordError(params.keyword) ? normalizeAdminKeyword(params.keyword) : ""
  const page = typeof params.page === "string" && /^\d+$/.test(params.page) ? Number(params.page) : 0
  return {status,keyword,page: Number.isSafeInteger(page) && page * 20 <= 2147483647 ? page : 0}
}
export function adminListHref(query: AdminQuery) {
  const params=new URLSearchParams({status:query.status,page:String(query.page)})
  if(query.keyword) params.set("keyword",query.keyword)
  return `/admin/posts?${params}`
}
export function adminReturnHref(value: unknown) {
  if(typeof value !== "string" || !value.startsWith("/admin/posts?"))return "/admin/posts"
  const url=new URL(value,"http://internal.invalid")
  if(url.pathname !== "/admin/posts")return "/admin/posts"
  return adminListHref(adminListQuery(Object.fromEntries(url.searchParams)))
}
