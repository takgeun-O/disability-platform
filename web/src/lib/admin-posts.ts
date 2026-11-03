import { writtenInstant } from "./written-time"
import {
  normalizeTitle,
  characterCount,
  POST_TYPES,
  POST_TOPICS,
  type PostType,
  type PostTopic,
} from "./community-posts"
export const ADMIN_STATUSES = {
  ALL: "전체",
  VISIBLE: "정상",
  HIDDEN: "숨김",
  DELETED: "삭제",
} as const
export type AdminStatus = keyof typeof ADMIN_STATUSES
export type AdminQuery = {
  status: AdminStatus
  keyword: string
  page: number
}
export type AdminPost = {
  postId: number
  type: PostType
  topic: PostTopic
  title: string
  contentPreview: string
  author: {
    memberId: number
    nickname: string
    withdrawn: boolean
  }
  status: Exclude<AdminStatus, "ALL">
  createdAt: string
  updatedAt: string
  hiddenAt: string | null
  deletedAt: string | null
}
export type AdminPage = {
  items: AdminPost[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}
export { normalizeTitle as normalizeAdminKeyword }
export const adminKeywordError = (value: string) =>
  characterCount(normalizeTitle(value)) > 100
    ? "검색어는 100자 이하로 입력해 주세요."
    : ""
export const adminObject = (v: unknown): v is Record<string, unknown> =>
  !!v && typeof v === "object"
export const adminCount = (v: unknown): v is number =>
  Number.isSafeInteger(v) && v as number >= 0
const instant = (v: unknown): v is string => typeof v === "string" && writtenInstant(v) !== null
export function isAdminContent(v: unknown): v is Record<string, unknown> & Pick<AdminPost, "author" | "status" | "createdAt" | "updatedAt" | "hiddenAt" | "deletedAt"> {
  return adminObject(v) && adminObject(v.author) && adminCount(v.author.memberId) && v.author.memberId > 0
    && typeof v.author.nickname === "string" && typeof v.author.withdrawn === "boolean"
    && typeof v.status === "string" && ["VISIBLE","HIDDEN","DELETED"].includes(v.status)
    && instant(v.createdAt) && instant(v.updatedAt)
    && (v.hiddenAt === null || instant(v.hiddenAt)) && (v.deletedAt === null || instant(v.deletedAt))
}
export type AdminPageInfo = Omit<AdminPage, "items">
export function isAdminPageInfo(v: unknown): v is Record<string, unknown> & AdminPageInfo {
  return adminObject(v) && adminCount(v.page) && adminCount(v.size) && v.size >= 1 && v.size <= 50
    && adminCount(v.totalElements) && adminCount(v.totalPages)
    && v.totalPages === Math.ceil(v.totalElements / v.size)
    && v.hasNext === (v.page + 1 < v.totalPages) && v.hasPrevious === (v.page > 0)
}
export function isAdminPage(v: unknown): v is AdminPage {
  if (
    !adminObject(v) ||
    !adminCount(v.page) ||
    !adminCount(v.size) ||
    v.size < 1 ||
    v.size > 50 ||
    !adminCount(v.totalElements) ||
    !adminCount(v.totalPages) ||
    typeof v.hasNext !== "boolean" ||
    typeof v.hasPrevious !== "boolean" ||
    !Array.isArray(v.items)
  )
    return false
  return (
    v.totalPages === Math.ceil(v.totalElements / v.size) &&
    v.hasNext === v.page + 1 < v.totalPages &&
    v.hasPrevious === v.page > 0 &&
    v.items.length <= v.size &&
    v.items.every(
      (p) =>
        adminObject(p) &&
        adminCount(p.postId) &&
        p.postId > 0 &&
        typeof p.type === "string" &&
        Object.prototype.hasOwnProperty.call(POST_TYPES, p.type) &&
        typeof p.topic === "string" &&
        Object.prototype.hasOwnProperty.call(POST_TOPICS, p.topic) &&
        typeof p.title === "string" &&
        typeof p.contentPreview === "string" &&
        adminObject(p.author) &&
        adminCount(p.author.memberId) &&
        p.author.memberId > 0 &&
        typeof p.author.nickname === "string" &&
        typeof p.author.withdrawn === "boolean" &&
        ["VISIBLE", "HIDDEN", "DELETED"].includes(String(p.status)) &&
        instant(p.createdAt) &&
        instant(p.updatedAt) &&
        (p.hiddenAt === null || instant(p.hiddenAt)) &&
        (p.deletedAt === null || instant(p.deletedAt)),
    )
  )
}
export class AdminApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}
export function adminQueryParams(query: AdminQuery) {
  const params = new URLSearchParams({status: query.status, page: String(query.page), size: "20"})
  const keyword = normalizeTitle(query.keyword)
  if (adminKeywordError(keyword)) throw new AdminApiError(400, adminKeywordError(keyword))
  if (keyword) params.set("keyword", keyword)
  return params
}
export async function adminGet(path: string, signal: AbortSignal, target = "게시글"): Promise<unknown> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL
  if (!base) throw new AdminApiError(0, "관리자 서비스에 연결할 수 없습니다.")
  let response: Response
  try {
    response = await fetch(
      base.replace(/\/$/, "") + path,
      {
        method: "GET",
        credentials: "include",
        cache: "no-store",
        redirect: "error",
        referrerPolicy: "no-referrer",
        headers: { Accept: "application/json" },
        signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]),
      },
    )
  } catch {
    throw new AdminApiError(
      0,
      `연결 문제로 ${target}을 가져오지 못했습니다. 다시 시도해 주세요.`,
    )
  }
  if (response.status !== 200)
    throw new AdminApiError(
      response.status,
      response.status === 403
        ? "관리자 권한이 없습니다."
        : response.status === 401
          ? "로그인이 필요합니다."
          : `${target}을 가져오지 못했습니다. 다시 시도해 주세요.`,
    )
  let body: unknown
  try {
    body = /^application\/json(?:;|$)/i.test(
      response.headers.get("content-type") ?? "",
    )
      ? await response.json()
      : null
  } catch {
    body = null
  }
  return body
}
export async function getAdminPosts(query: AdminQuery, signal: AbortSignal): Promise<AdminPage> {
  const body = await adminGet("/api/v1/admin/posts?" + adminQueryParams(query), signal)
  if (!isAdminPage(body) || body.page !== query.page || body.size !== 20)
    throw new AdminApiError(0, "응답을 확인하지 못했습니다. 다시 시도해 주세요.")
  return body
}
