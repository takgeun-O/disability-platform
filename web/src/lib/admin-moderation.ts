import type { AdminPostDetail, AdminComment } from "./admin-detail"
import { ACTION_REASONS, type ModerationReasonType } from "./moderation-reasons"
import { getCsrfContext, SignupApiError } from "./signup-api"
import { normalizeTitle, characterCount } from "./community-posts"
import { adminObject } from "./admin-posts"
import { writtenInstant } from "./written-time"
export type ModerationTarget = { postId: number; commentId?: number; postStatus?: "VISIBLE" | "HIDDEN" | "DELETED"; label: string; content?: AdminPostDetail | AdminComment; action: "hide" | "restore" }
export type ModerationResult = { postId: number; commentId?: number; hidden: boolean; hiddenAt: string | null; changed: boolean }
export function moderationReasonError(value: string, reasonType: ModerationReasonType | "", action: ModerationTarget["action"]) {
  if (!reasonType || !ACTION_REASONS[action].includes(reasonType)) return "처리 사유를 선택해 주세요."
  if (reasonType !== "OTHER") return ""
  const reason = normalizeTitle(value)
  return !reason ? "상세 사유를 입력해 주세요." : characterCount(reason) > 500 ? "상세 사유는 앞뒤 공백을 제외하고 500자 이하여야 합니다." : ""
}
export class ModerationError extends Error {
  constructor(public status: number, public code: string, message: string, public uncertain = false, public refresh = false) { super(message) }
}
const uncertain = () => new ModerationError(0, "UNCERTAIN", "처리 결과를 확인하지 못했습니다. 최신 상태를 확인한 뒤 판단해 주세요. 변경 요청은 자동으로 반복하지 않습니다.", true, true)
export function isModerationResult(v: unknown, target: ModerationTarget): v is ModerationResult {
  return adminObject(v) && v.postId === target.postId && (target.commentId === undefined || v.commentId === target.commentId)
    && typeof v.changed === "boolean" && v.hidden === (target.action === "hide")
    && (v.hidden ? typeof v.hiddenAt === "string" && writtenInstant(v.hiddenAt) !== null : v.hiddenAt === null)
}
export async function moderate(target: ModerationTarget, reason: { reasonType: ModerationReasonType | ""; reasonDetail: string }, current: () => boolean) {
  const error = moderationReasonError(reason.reasonDetail, reason.reasonType, target.action)
  if (error) throw new ModerationError(400, "INVALID_REASON", error)
  if (!current()) return null
  let csrf
  try { csrf = await getCsrfContext() } catch (e) {
    if (e instanceof SignupApiError && e.status === 401) throw new ModerationError(401, e.code, "로그인이 필요합니다.")
    throw new ModerationError(0, "CSRF_PREPARATION_FAILED", "보안 정보를 확인하지 못해 변경 요청을 보내지 않았습니다. 다시 시도해 주세요.")
  }
  if (!current()) return null
  const path = `/api/v1/admin/posts/${target.postId}${target.commentId === undefined ? "" : `/comments/${target.commentId}`}/${target.action}`
  let response: Response, body: unknown
  try {
    response = await fetch((process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, "") + path, {
      method: "POST", credentials: "include", cache: "no-store", redirect: "error", referrerPolicy: "no-referrer",
      headers: { Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token },
      body: JSON.stringify({ reasonType: reason.reasonType, ...(reason.reasonType === "OTHER" ? { reasonDetail: normalizeTitle(reason.reasonDetail) } : {}) }), signal: AbortSignal.timeout(15000),
    })
  } catch { throw uncertain() }
  try {
    body = /^application\/json(?:;|$)/i.test(response.headers.get("content-type") ?? "") ? await response.json() : null
  } catch { body = null }
  if (response.status === 200) {
    if (!isModerationResult(body, target)) throw uncertain()
    return body
  }
  const code = adminObject(body) && typeof body.code === "string" ? body.code : "UNEXPECTED_RESPONSE"
  const messages: Record<string, string> = {
    INVALID_REASON: reason.reasonType === "OTHER"
      ? "상세 사유를 확인해 주세요. 앞뒤 공백을 제외하고 1~500자로 입력해 주세요."
      : "선택한 처리 사유를 확인해 주세요.",
    CSRF_TOKEN_INVALID: "보안 확인에 실패했습니다. 사유는 유지됩니다. 다시 실행하면 보안 정보를 새로 확인합니다.",
    POST_NOT_FOUND: "게시글을 찾을 수 없습니다. 최신 상태를 확인해 주세요.",
    COMMENT_NOT_FOUND: "댓글을 찾을 수 없습니다. 최신 상태를 확인해 주세요.",
    POST_DELETED: "삭제된 게시글은 숨김 또는 숨김 해제할 수 없습니다.",
    COMMENT_DELETED: "삭제된 댓글은 숨김 또는 숨김 해제할 수 없습니다.",
  }
  if (response.status >= 500 || (response.status >= 200 && response.status < 300)) throw uncertain()
  throw new ModerationError(response.status, code, messages[code] ?? "요청을 처리할 수 없습니다. 최신 상태를 확인해 주세요.", false, response.status === 404 || response.status === 409)
}
