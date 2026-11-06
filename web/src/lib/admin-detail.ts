import { writtenInstant } from "./written-time";
import { POST_TYPES, POST_TOPICS } from "./community-posts"
import { adminObject, adminCount, isAdminContent, isAdminPageInfo, adminGet, adminQueryParams, AdminApiError, type AdminPost, type AdminQuery, type AdminPageInfo } from "./admin-posts"
export type AdminPostDetail = Omit<AdminPost, "contentPreview"> & { content: string }
export type AdminComment = Pick<AdminPost, "author" | "status" | "createdAt" | "updatedAt" | "hiddenAt" | "deletedAt"> & { commentId: number; content: string; contentUpdatedAt: string | null }
export type AdminCommentPage = AdminPageInfo & { postId: number; postStatus: AdminPost["status"]; items: AdminComment[] }
const positive = (v: unknown): v is number => adminCount(v) && v > 0
export function isAdminPostDetail(v: unknown): v is AdminPostDetail {
  return adminObject(v) && isAdminContent(v) && positive(v.postId) && typeof v.title === "string" && typeof v.content === "string"
    && typeof v.type === "string" && Object.prototype.hasOwnProperty.call(POST_TYPES,v.type)
    && typeof v.topic === "string" && Object.prototype.hasOwnProperty.call(POST_TOPICS,v.topic)
}
export function isAdminCommentPage(v: unknown): v is AdminCommentPage {
  return adminObject(v) && isAdminPageInfo(v) && positive(v.postId)
    && typeof v.postStatus === "string" && ["VISIBLE","HIDDEN","DELETED"].includes(v.postStatus)
    && Array.isArray(v.items) && v.items.length <= v.size
    && v.items.every(c => adminObject(c) && isAdminContent(c) && positive(c.commentId) && typeof c.content === "string"
      && (c.contentUpdatedAt === null || (typeof c.contentUpdatedAt === "string" && writtenInstant(c.contentUpdatedAt) !== null)))
    && new Set(v.items.map(c => c.commentId)).size === v.items.length
}
const invalid = () => new AdminApiError(0,"응답을 확인하지 못했습니다. 다시 시도해 주세요.")
export async function getAdminPost(postId: number, signal: AbortSignal) {
  const body = await adminGet(`/api/v1/admin/posts/${postId}`,signal)
  if (!isAdminPostDetail(body) || body.postId !== postId) throw invalid()
  return body
}
export async function getAdminComments(postId: number, query: AdminQuery, signal: AbortSignal) {
  const body = await adminGet(`/api/v1/admin/posts/${postId}/comments?${adminQueryParams(query)}`,signal,"댓글")
  if (!isAdminCommentPage(body) || body.postId !== postId || body.page !== query.page || body.size !== 20) throw invalid()
  return body
}
