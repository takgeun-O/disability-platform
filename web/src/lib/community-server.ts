import "server-only"
import { cache } from "react"
import { CommunityApiError, getPost, getPosts } from "./community-api"
import type { PostQuery } from "./community-posts"
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
