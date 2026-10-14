import type { PostDetail, PostPage } from "./community-api"
import type { PostQuery } from "./community-posts"

// Server Component → Client Component 경계에는 Error 인스턴스 대신 안전한 공개 필드만 전달한다.
export type ReadError = { status: number; code: string; message: string }
export type PublicResult<T> = { data: T; error: null } | { data: null; error: ReadError }
export type InitialPostList = PublicResult<PostPage> & { query: PostQuery }
export type InitialPostDetail = PublicResult<PostDetail>
export function isMissingPost(error: ReadError | null): boolean {
  return error?.status === 404 && error.code === "POST_NOT_FOUND"
}
export function pageIsOutOfRange(page: Pick<PostPage, "page" | "totalPages">): boolean {
  // 전체 글이 사라졌더라도 2페이지 이상은 더 이상 존재하지 않는 페이지다.
  return page.page > 0 && page.page >= page.totalPages
}
