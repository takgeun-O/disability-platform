import { adminGet, adminObject, isAdminPageInfo, AdminApiError, type AdminPageInfo } from "./admin-posts"
import { normalizeTitle } from "./community-posts"
import { getCsrfContext, SignupApiError } from "./signup-api"
import { writtenInstant } from "./written-time"
import type { AdminMemberDetail } from "./admin-members"

export type MemberAction = "suspend" | "lift-suspension"
export type MemberActionResult = { memberId: number; status: "ACTIVE" | "SUSPENDED"; changed: boolean }
export type MemberLog = { logId: number; actorMemberId: number; targetMemberId: number; action: "SUSPEND" | "LIFT_SUSPENSION"; beforeStatus: "ACTIVE" | "SUSPENDED"; afterStatus: "ACTIVE" | "SUSPENDED"; reason: string; createdAt: string }
export type MemberLogPage = AdminPageInfo & { items: MemberLog[] }
export const memberReason = normalizeTitle
export function memberReasonError(value: string) {
  const reason = memberReason(value)
  return !reason ? "처리 사유를 입력해 주세요." : [...reason].length > 500 ? "처리 사유는 앞뒤 공백을 제외하고 500자 이하로 입력해 주세요." : ""
}
export function memberActionRestriction(member: AdminMemberDetail, actorId: number) {
  if (member.memberId === actorId) return "자기 자신에게는 운영 조치를 할 수 없습니다."
  if (member.role === "ADMIN") return "관리자 회원에게는 운영 조치를 할 수 없습니다."
  return ["ACTIVE", "SUSPENDED"].includes(member.status) ? "" : "현재 회원 상태에서는 이용 정지 또는 해제할 수 없습니다."
}
export function isMemberActionResult(v: unknown, id: string, action: MemberAction): v is MemberActionResult {
  return adminObject(v) && Number.isSafeInteger(v.memberId) && String(v.memberId) === id && typeof v.changed === "boolean" && v.status === (action === "suspend" ? "SUSPENDED" : "ACTIVE")
}
export function isMemberLogPage(v: unknown, id: string, page: number): v is MemberLogPage {
  return isAdminPageInfo(v) && v.page === page && v.size === 20 && Array.isArray(v.items) && v.items.length <= v.size && v.items.every(log =>
    adminObject(log) && Number.isSafeInteger(log.logId) && (log.logId as number) > 0 && Number.isSafeInteger(log.actorMemberId) && (log.actorMemberId as number) > 0 && Number.isSafeInteger(log.targetMemberId) && String(log.targetMemberId) === id && typeof log.reason === "string" && typeof log.createdAt === "string" && writtenInstant(log.createdAt) !== null &&
    (log.action === "SUSPEND" ? log.beforeStatus === "ACTIVE" && log.afterStatus === "SUSPENDED" : log.action === "LIFT_SUSPENSION" && log.beforeStatus === "SUSPENDED" && log.afterStatus === "ACTIVE"))
}
export async function getMemberLogs(id: string, page: number, signal: AbortSignal) {
  const value = await adminGet(`/api/v1/admin/members/${id}/moderation-logs?page=${page}&size=20`, signal, "회원 조치 이력")
  if (!isMemberLogPage(value, id, page)) throw new AdminApiError(0, "회원 조치 이력 응답을 확인하지 못했습니다.")
  return value
}
export class MemberActionError extends Error {
  constructor(public status: number, public code: string, message: string, public uncertain = false) { super(message) }
}
const uncertain = () => new MemberActionError(0, "UNCERTAIN", "처리 결과를 확인하지 못했습니다. 최신 상태와 이력을 확인해 주세요. 요청은 자동으로 반복하지 않습니다.", true)
export async function moderateMember(id: string, action: MemberAction, input: string, current: () => boolean) {
  const validation = memberReasonError(input)
  if (validation) throw new MemberActionError(400, "INVALID_REASON", validation)
  if (!current()) return null
  let csrf
  try { csrf = await getCsrfContext() } catch (e) {
    if (e instanceof SignupApiError && e.status === 401) throw new MemberActionError(401, e.code, "로그인이 필요합니다.")
    throw new MemberActionError(0, "CSRF_PREPARATION_FAILED", "보안 정보를 확인하지 못해 요청을 보내지 않았습니다. 다시 시도해 주세요.")
  }
  if (!current()) return null
  let response: Response, body: unknown
  try {
    response = await fetch((process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, "") + `/api/v1/admin/members/${id}/${action}`, {
      method: "POST", credentials: "include", cache: "no-store", redirect: "error", referrerPolicy: "no-referrer",
      headers: { Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token },
      body: JSON.stringify({ reason: memberReason(input) }), signal: AbortSignal.timeout(15000),
    })
  } catch { throw uncertain() }
  try { body = /^application\/json(?:;|$)/i.test(response.headers.get("content-type") ?? "") ? await response.json() : null } catch { body = null }
  if (response.status === 200) { if (!isMemberActionResult(body, id, action)) throw uncertain(); return body }
  if (response.status >= 500 || response.ok) throw uncertain()
  const code = adminObject(body) && typeof body.code === "string" ? body.code : "UNEXPECTED_RESPONSE"
  const messages: Record<string, string> = {
    INVALID_REASON: "처리 사유를 확인해 주세요. 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.",
    SELF_MODERATION_NOT_ALLOWED: "자기 자신에게는 운영 조치를 할 수 없습니다.",
    ADMIN_TARGET_NOT_ALLOWED: "관리자 회원에게는 운영 조치를 할 수 없습니다.",
    MEMBER_STATUS_NOT_ALLOWED: "현재 회원 상태에서는 처리할 수 없습니다. 최신 정보를 확인해 주세요.",
    MEMBER_NOT_FOUND: "회원을 찾을 수 없습니다. 목록을 다시 확인해 주세요.",
    CSRF_TOKEN_INVALID: "보안 확인에 실패했습니다. 입력은 유지됩니다. 다시 실행하면 인증·토큰을 재확인합니다.",
  }
  throw new MemberActionError(response.status, code, messages[code] ?? "요청을 처리하지 못했습니다. 인증 및 현재 상태를 확인해 주세요.")
}
