import { getComments, CommentError, type CommentPage } from "./community-comments"
import "server-only"
import { cache } from "react"
import { CommunityApiError, getPost, getPosts, getFeed } from "./community-api"
import type { PostQuery, PostCursor } from "./community-posts"
import type { PublicResult } from "./community-load"

// 내부 주소는 이 server-only 모듈에서만 읽는다. 쿠키/Authorization을 전달하지 않는다.
const apiBase = () => process.env.IYUM_API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL
async function read<T>(request: () => Promise<T>): Promise<PublicResult<T>> {
  try {
    return { data: await request(), error: null }
  } catch (error) {
    return { data: null, error: error instanceof CommunityApiError
      ? { status: error.status, code: error.code, message: error.message }
      : { status: 0, code: "UNEXPECTED_RESPONSE", message: "게시글을 불러오지 못했습니다. 다시 시도해 주세요." } }
  }
}
// no-store: 요청 간 데이터 캐시는 없다. cache()는 같은 렌더의 metadata/page 조회만 합친다.
export const readPublicPost = cache((id: string) => read(() => getPost(id, undefined, apiBase())))
export const readPublicPosts = (query: PostQuery) => read(() => getPosts(query, undefined, apiBase()))

// 댓글 장애는 게시글 오류/404로 승격하지 않는다. 초기 조회에도 인증 정보를 보내지 않는다.
export async function readPublicComments(id: string): Promise<PublicResult<CommentPage>> {
  try { return { data: await getComments(id, null, undefined, apiBase()), error: null } }
  catch (e) { return { data: null, error: { status: e instanceof CommentError ? e.status : 0, code: e instanceof CommentError ? e.code : "UNEXPECTED_RESPONSE", message: e instanceof CommentError ? e.message : "댓글을 불러오지 못했습니다." } } }
}

export const readPublicFeed = (query: PostQuery, cursor: PostCursor | null = null) => read(() => getFeed(query, cursor, undefined, apiBase()))
