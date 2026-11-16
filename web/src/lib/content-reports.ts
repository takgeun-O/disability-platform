import {
  adminGet,
  adminObject,
  isAdminPageInfo,
  AdminApiError,
  type AdminPageInfo,
} from "./admin-posts"
import { normalizeTitle } from "./community-posts"
import { dateRangeError, validAdminDate } from "./admin-dates"
import { getCsrfContext, SignupApiError } from "./signup-api"
import { writtenInstant } from "./written-time"

export const REPORT_REASONS = {
  SPAM_ADVERTISING: "광고·홍보·스팸",
  ABUSE_HATE: "욕설·비방·혐오",
  PERSONAL_INFORMATION: "개인정보 노출",
  DUPLICATE_FLOODING: "도배·중복 게시",
  OFF_TOPIC: "게시판 주제와 무관",
  OTHER: "기타",
} as const
export const REPORT_STATUSES = {
  RECEIVED: "접수",
  RESOLVED: "처리 완료",
  DISMISSED: "기각",
} as const
export const REPORT_TARGET_STATUSES = {
  NORMAL: "정상",
  HIDDEN: "숨김",
  DELETED: "삭제",
} as const
export type ReportTargetStatus = keyof typeof REPORT_TARGET_STATUSES
export type ReportReason = keyof typeof REPORT_REASONS
export type ReportStatus = keyof typeof REPORT_STATUSES
export type ReportAction = "resolve" | "dismiss" | "hide-and-resolve"
export const REPORT_OUTCOMES = {
  CONTENT_HIDDEN: "콘텐츠 숨김",
  CONTENT_ALREADY_HIDDEN: "기존 숨김 유지",
  NO_CONTENT_ACTION: "추가 콘텐츠 조치 없음",
  LEGACY_UNKNOWN: "과거 기록 · 조치 여부 확인 불가",
} as const
export type ReportOutcome = keyof typeof REPORT_OUTCOMES
export type ReportHandlingResponse = {
  reportId: number
  status: "RESOLVED" | "DISMISSED"
  handledByMemberId: number
  handledAt: string
  handlingReason: string
  handlingOutcome: Exclude<ReportOutcome, "LEGACY_UNKNOWN">
}
export type ReportAuthor = {
  memberId: number
  nickname: string
  withdrawn: boolean
}
export type ReportItem = {
  reportId: number
  targetType: "POST" | "COMMENT"
  targetId: number
  status: ReportStatus
  reasonType: ReportReason
  titleSnapshot: string | null
  contentSnapshotPreview: string
  createdAt: string
  currentAuthor: ReportAuthor | null
  currentTargetStatus: ReportTargetStatus | null
}
export type ReportPage = AdminPageInfo & { items: ReportItem[] }
export type ReportDetail = Omit<ReportItem, "contentSnapshotPreview" | "currentAuthor" | "currentTargetStatus"> & {
  reporterMemberId: number
  reasonDetail: string | null
  contentSnapshot: string
  currentTarget: {
    targetId: number
    parentPostId: number | null
    author: ReportAuthor
    title: string | null
    content: string
    hiddenAt: string | null
    deletedAt: string | null
  } | null
  handledByMemberId: number | null
  handledAt: string | null
  handlingReason: string | null
  handlingOutcome: ReportOutcome | null
}
// Validated detail data only: a missing target is distinct from an unloaded detail.
export function reportTargetStatus(target: ReportDetail["currentTarget"]): ReportTargetStatus | null {
  if (target === null) return null
  if (target.deletedAt !== null) return "DELETED"
  if (target.hiddenAt !== null) return "HIDDEN"
  return "NORMAL"
}
export type ReportQuery = {
  targetType: "ALL" | "POST" | "COMMENT"
  status: "ALL" | ReportStatus
  reasonType: "" | ReportReason
  startDate: string
  endDate: string
  sort: "LATEST" | "OLDEST"
  page: number
}
export const defaultReportQuery: ReportQuery = {
  targetType: "ALL",
  status: "ALL",
  reasonType: "",
  startDate: "",
  endDate: "",
  sort: "LATEST",
  page: 0,
}
export const isReportReason = (v: unknown): v is ReportReason =>
  typeof v === "string" &&
  Object.prototype.hasOwnProperty.call(REPORT_REASONS, v)
const status = (v: unknown): v is ReportStatus =>
  typeof v === "string" &&
  Object.prototype.hasOwnProperty.call(REPORT_STATUSES, v)
const id = (v: unknown): v is number =>
  typeof v === "number" && Number.isSafeInteger(v) && v > 0
const instant = (v: unknown): v is string =>
  typeof v === "string" && writtenInstant(v) !== null
const nullableText = (v: unknown) => v === null || typeof v === "string"
const author = (v: unknown) =>
  adminObject(v) &&
  id(v.memberId) &&
  typeof v.nickname === "string" &&
  typeof v.withdrawn === "boolean"
const base = (v: unknown): v is Record<string, unknown> =>
  adminObject(v) &&
  id(v.reportId) &&
  id(v.targetId) &&
  ["POST", "COMMENT"].includes(String(v.targetType)) &&
  status(v.status) &&
  isReportReason(v.reasonType) &&
  nullableText(v.titleSnapshot) &&
  (v.targetType !== "COMMENT" || v.titleSnapshot === null) &&
  instant(v.createdAt)
export function isReportPage(v: unknown): v is ReportPage {
  return (
    isAdminPageInfo(v) &&
    Array.isArray(v.items) &&
    v.items.length <= v.size &&
    v.items.every(
      (x) =>
        base(x) &&
        typeof x.contentSnapshotPreview === "string" &&
        (x.currentAuthor === null || author(x.currentAuthor)) &&
        (x.currentTargetStatus === null ||
          (typeof x.currentTargetStatus === "string" &&
            Object.prototype.hasOwnProperty.call(
              REPORT_TARGET_STATUSES, x.currentTargetStatus,
            ))),
    )
  )
}
export function isReportDetail(v: unknown): v is ReportDetail {
  if (
    !base(v) ||
    !id(v.reporterMemberId) ||
    !nullableText(v.reasonDetail) ||
    typeof v.contentSnapshot !== "string"
  )
    return false
  const t = v.currentTarget
  if (
    t !== null &&
    !(
      adminObject(t) &&
      t.targetId === v.targetId &&
      author(t.author) &&
      typeof t.content === "string" &&
      nullableText(t.title) &&
      (t.hiddenAt === null || instant(t.hiddenAt)) &&
      (t.deletedAt === null || instant(t.deletedAt)) &&
      (v.targetType === "POST"
        ? t.parentPostId === null && typeof t.title === "string"
        : id(t.parentPostId) && t.title === null)
    )
  )
    return false
  return v.status === "RECEIVED"
    ? v.handledByMemberId === null &&
        v.handledAt === null &&
        v.handlingReason === null &&
        v.handlingOutcome === null
    : id(v.handledByMemberId) &&
        instant(v.handledAt) &&
        typeof v.handlingReason === "string" &&
        typeof v.handlingOutcome === "string" &&
        Object.prototype.hasOwnProperty.call(REPORT_OUTCOMES, v.handlingOutcome)
}
export function reportQuery(p: URLSearchParams): ReportQuery {
  const startDate = validAdminDate(p.get("startDate") ?? "")
      ? p.get("startDate")!
      : "",
    endDate = validAdminDate(p.get("endDate") ?? "") ? p.get("endDate")! : ""
  const page = Number(p.get("page") ?? 0),
    s = p.get("status"),
    r = p.get("reasonType")
  return {
    targetType:
      p.get("targetType") === "POST"
        ? "POST"
        : p.get("targetType") === "COMMENT"
          ? "COMMENT"
          : "ALL",
    status: s === "ALL" ? "ALL" : status(s) ? s : "ALL",
    reasonType: isReportReason(r) ? r : "",
    ...(dateRangeError({ startDate, endDate })
      ? { startDate: "", endDate: "" }
      : { startDate, endDate }),
    sort: p.get("sort") === "OLDEST" ? "OLDEST" : "LATEST",
    page:
      Number.isSafeInteger(page) && page >= 0 && page * 20 <= 2147483647
        ? page
        : 0,
  }
}
export function reportParams(q: ReportQuery) {
  const p = new URLSearchParams({
    targetType: q.targetType,
    sort: q.sort,
    page: String(q.page),
    size: "20",
  })
  if (q.status !== "ALL") p.set("status", q.status)
  if (q.reasonType) p.set("reasonType", q.reasonType)
  if (q.startDate) p.set("startDate", q.startDate)
  if (q.endDate) p.set("endDate", q.endDate)
  return p
}
export function reportHref(q: ReportQuery, selected: number | null = null) {
  const p = reportParams(q)
  p.delete("size")
  p.set("status", q.status)
  if (selected) p.set("selected", String(selected))
  return `/admin/reports?${p}`
}
export async function getReports(q: ReportQuery, signal: AbortSignal) {
  const v = await adminGet(
    "/api/v1/admin/reports?" + reportParams(q),
    signal,
    "신고 목록",
  )
  if (!isReportPage(v) || v.page !== q.page || v.size !== 20)
    throw new AdminApiError(0, "신고 목록 응답을 확인하지 못했습니다.")
  return v
}
export async function getReport(reportId: number, signal: AbortSignal) {
  const v = await adminGet(
    `/api/v1/admin/reports/${reportId}`,
    signal,
    "신고 상세",
  )
  if (!isReportDetail(v) || v.reportId !== reportId)
    throw new AdminApiError(0, "신고 상세 응답을 확인하지 못했습니다.")
  return v
}
export const reportDetailText = normalizeTitle
export const handlingText = (v: string) =>
  v.replace(/^\p{White_Space}+|\p{White_Space}+$/gu, "")
export function reportInputError(reason: string, detail: string) {
  if (!isReportReason(reason)) return "신고 사유를 선택해 주세요."
  const text = reportDetailText(detail)
  if (reason === "OTHER" && !text) return "상세 사유를 입력해 주세요."
  return [...text].length > 500
    ? "상세 사유는 앞뒤 공백을 제외하고 500자 이하로 입력해 주세요."
    : ""
}
export function handlingError(v: string) {
  const text = handlingText(v)
  return !text
    ? "처리 사유를 입력해 주세요."
    : [...text].length > 500
      ? "처리 사유는 앞뒤 공백을 제외하고 500자 이하로 입력해 주세요."
      : ""
}
export class ReportMutationError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public uncertain = false,
  ) {
    super(message)
  }
}
const uncertain = () =>
  new ReportMutationError(
    0,
    "UNCERTAIN",
    "처리 결과를 확인하지 못했습니다. 요청은 자동으로 다시 보내지 않습니다.",
    true,
  )
async function post(
  path: string,
  body: object,
  expected: number,
  validate: (v: unknown) => boolean,
  current: () => boolean,
) {
  if (!current()) return null
  let csrf
  try {
    csrf = await getCsrfContext()
  } catch (e) {
    if (e instanceof SignupApiError && e.status === 401)
      throw new ReportMutationError(401, e.code, "로그인이 필요합니다.")
    throw new ReportMutationError(
      0,
      "CSRF_PREPARATION_FAILED",
      "보안 정보를 확인하지 못해 요청을 보내지 않았습니다. 다시 시도해 주세요.",
    )
  }
  if (!current()) return null
  let response: Response, value: unknown
  try {
    response = await fetch(
      (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").replace(/\/$/, "") + path,
      {
        method: "POST",
        credentials: "include",
        cache: "no-store",
        redirect: "error",
        referrerPolicy: "no-referrer",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(15000),
      },
    )
  } catch {
    throw uncertain()
  }
  try {
    value = /^application\/json(?:;|$)/i.test(
      response.headers.get("content-type") ?? "",
    )
      ? await response.json()
      : null
  } catch {
    value = null
  }
  if (response.status === expected && validate(value)) return value
  if (response.ok || response.status >= 500) throw uncertain()
  const code =
    adminObject(value) && typeof value.code === "string"
      ? value.code
      : "UNEXPECTED_RESPONSE"
  const messages: Record<string, string> = {
    REPORT_ALREADY_EXISTS:
      "이미 신고한 콘텐츠입니다. 처리 완료·기각 이후에도 다시 신고할 수 없습니다.",
    REPORT_ALREADY_HANDLED:
      "이미 처리된 신고입니다. 최신 처리 결과를 확인해 주세요.",
    SELF_REPORT_NOT_ALLOWED: "자신이 작성한 콘텐츠는 신고할 수 없습니다.",
    CSRF_TOKEN_INVALID:
      "보안 확인에 실패했습니다. 입력은 유지됩니다. 다시 실행하면 보안 정보를 확인합니다.",
    INVALID_REASON: "신고 사유와 상세 내용을 확인해 주세요.",
    INVALID_HANDLING_REASON:
      "처리 사유를 확인해 주세요. 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.",
    POST_NOT_FOUND: "대상 게시글 또는 댓글의 부모 게시글을 찾을 수 없습니다.",
    COMMENT_NOT_FOUND: "대상 댓글을 찾을 수 없습니다.",
    POST_DELETED:
      "대상 게시글 또는 댓글의 부모 게시글이 삭제되어 숨길 수 없습니다.",
    COMMENT_DELETED: "대상 댓글이 삭제되어 숨길 수 없습니다.",
    REPORT_NOT_FOUND: "신고 기록을 찾을 수 없습니다.",
  }
  throw new ReportMutationError(
    response.status,
    code,
    messages[code] ??
      "요청을 처리하지 못했습니다. 인증 및 현재 상태를 확인해 주세요.",
  )
}
export async function createReport(
  postId: string,
  commentId: number | undefined,
  reason: string,
  detail: string,
  current: () => boolean,
) {
  const error = reportInputError(reason, detail)
  if (error) throw new ReportMutationError(400, "INVALID_REASON", error)
  const text = reportDetailText(detail)
  return post(
    `/api/v1/community/posts/${postId}${
      commentId ? `/comments/${commentId}` : ""
    }/reports`,
    { reasonType: reason, ...(text ? { reasonDetail: text } : {}) },
    201,
    (v) =>
      adminObject(v) &&
      id(v.reportId) &&
      v.status === "RECEIVED" &&
      instant(v.createdAt),
    current,
  )
}
export async function handleReport(
  reportId: number,
  action: ReportAction,
  reason: string,
  current: () => boolean,
) {
  const error = handlingError(reason)
  if (error)
    throw new ReportMutationError(400, "INVALID_HANDLING_REASON", error)
  const result = await post(
    `/api/v1/admin/reports/${reportId}/${action}`,
    { handlingReason: handlingText(reason) },
    200,
    (v) =>
      adminObject(v) &&
      v.reportId === reportId &&
      v.status === (action === "dismiss" ? "DISMISSED" : "RESOLVED") &&
      id(v.handledByMemberId) &&
      instant(v.handledAt) &&
      typeof v.handlingReason === "string" &&
      (action === "hide-and-resolve"
        ? v.handlingOutcome === "CONTENT_HIDDEN" ||
          v.handlingOutcome === "CONTENT_ALREADY_HIDDEN"
        : v.handlingOutcome === "NO_CONTENT_ACTION"),
    current,
  )
  return result as ReportHandlingResponse | null
}
