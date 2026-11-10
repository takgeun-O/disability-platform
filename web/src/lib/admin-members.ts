import { adminGet, AdminApiError, adminObject, isAdminPageInfo, type AdminPageInfo } from "./admin-posts"
import { normalizeTitle } from "./community-posts"
import { writtenInstant } from "./written-time"
export const MEMBER_STATUSES = { PENDING: "가입 대기", ACTIVE: "정상", RESTRICTED: "이용 제한", SUSPENDED: "이용 정지", WITHDRAWN: "탈퇴·정리 대기", DELETED: "탈퇴 완료" } as const
export const MEMBER_ROLES = { USER: "일반 회원", ADMIN: "관리자" } as const
export const MEMBER_SEARCHES = { NICKNAME: "닉네임", EMAIL: "이메일", MEMBER_ID: "회원 ID" } as const
export type MemberQuery = { searchType: keyof typeof MEMBER_SEARCHES; keyword: string; status: string; role: string; sort: "LATEST" | "OLDEST"; page: number }
export const defaultMemberQuery: MemberQuery = { searchType: "NICKNAME", keyword: "", status: "", role: "", sort: "LATEST", page: 0 }
export type AdminMember = { memberId: number; nickname: string; email: string | null; status: keyof typeof MEMBER_STATUSES; role: keyof typeof MEMBER_ROLES; createdAt: string }
export type AdminMemberDetail = AdminMember & { updatedAt: string; withdrawnAt: string | null }
export type MemberPage = AdminPageInfo & { items: AdminMember[] }
export function memberKeyword(type: MemberQuery["searchType"], input: string) {
  let value = normalizeTitle(input)
  if (!value) return value
  if (type === "MEMBER_ID") {
    if (!/^[0-9]{1,19}$/.test(value) || BigInt(value) < 1n || BigInt(value) > 9223372036854775807n) throw new Error("회원 ID는 1~9223372036854775807 범위의 숫자(최대 19자리)로 입력해 주세요.")
    value = BigInt(value).toString()
  } else if (type === "EMAIL") {
    value = value.toLowerCase()
    if (value.length > 254) throw new Error("이메일 검색어는 254자 이하로 입력해 주세요.")
  } else if ([...value].length > 100) throw new Error("닉네임 검색어는 100자 이하로 입력해 주세요.")
  return value
}
export function memberParams(q: MemberQuery) {
  const p = new URLSearchParams()
  const keyword = memberKeyword(q.searchType, q.keyword)
  if (keyword) { p.set("searchType", q.searchType); p.set("keyword", keyword) }
  if (q.status && Object.prototype.hasOwnProperty.call(MEMBER_STATUSES, q.status)) p.set("status", q.status)
  if (q.role && Object.prototype.hasOwnProperty.call(MEMBER_ROLES, q.role)) p.set("role", q.role)
  p.set("sort", q.sort); p.set("page", String(q.page)); p.set("size", "20")
  return p
}
export function memberQuery(p: URLSearchParams): MemberQuery {
  const searchType = Object.prototype.hasOwnProperty.call(MEMBER_SEARCHES, p.get("searchType") ?? "") ? p.get("searchType") as MemberQuery["searchType"] : "NICKNAME"
  let keyword = ""
  try { keyword = memberKeyword(searchType, p.get("keyword") ?? "") } catch { /* Invalid URL inputs cannot reach the API. */ }
  const page = Number(p.get("page") ?? 0)
  return { searchType, keyword, status: Object.prototype.hasOwnProperty.call(MEMBER_STATUSES, p.get("status") ?? "") ? p.get("status")! : "", role: Object.prototype.hasOwnProperty.call(MEMBER_ROLES, p.get("role") ?? "") ? p.get("role")! : "", sort: p.get("sort") === "OLDEST" ? "OLDEST" : "LATEST", page: Number.isSafeInteger(page) && page >= 0 && page * 20 <= 2147483647 ? page : 0 }
}
export function memberSelection(value: string | null) { return value && /^[1-9][0-9]{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n ? value : null }
export function memberHref(q: MemberQuery, selected: string | null = null) { const p = memberParams(q); p.delete("size"); if (selected) p.set("selected", selected); return `/admin/members?${p}` }
const instant = (v: unknown): v is string => typeof v === "string" && writtenInstant(v) !== null
export function isAdminMember(v: unknown): v is AdminMember {
  return adminObject(v) && Number.isSafeInteger(v.memberId) && (v.memberId as number) > 0 && typeof v.nickname === "string" && (v.email === null || typeof v.email === "string") && typeof v.status === "string" && Object.prototype.hasOwnProperty.call(MEMBER_STATUSES, v.status) && typeof v.role === "string" && Object.prototype.hasOwnProperty.call(MEMBER_ROLES, v.role) && instant(v.createdAt)
}
export function isMemberDetail(v: unknown): v is AdminMemberDetail { return adminObject(v) && isAdminMember(v) && "updatedAt" in v && "withdrawnAt" in v && instant(v.updatedAt) && (v.withdrawnAt === null || instant(v.withdrawnAt)) }
export function isMemberPage(v: unknown): v is MemberPage { return isAdminPageInfo(v) && Array.isArray(v.items) && v.items.length <= v.size && v.items.every(isAdminMember) }
export async function getMembers(q: MemberQuery, signal: AbortSignal) {
  const v = await adminGet(`/api/v1/admin/members?${memberParams(q)}`, signal, "회원 목록")
  if (!isMemberPage(v) || v.page !== q.page || v.size !== 20) throw new AdminApiError(0, "회원 목록 응답을 확인하지 못했습니다.")
  return v
}
export async function getMember(id: string, signal: AbortSignal) {
  const v = await adminGet(`/api/v1/admin/members/${id}`, signal, "회원 정보")
  if (!isMemberDetail(v) || String(v.memberId) !== id) throw new AdminApiError(0, "회원 상세 응답을 확인하지 못했습니다.")
  return v
}
