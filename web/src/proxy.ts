import { NextResponse, type NextRequest } from "next/server"
import { CommunityApiError, getPost } from "@/lib/community-api"
import { safeListReturn } from "@/lib/community-posts"
import { topicBySlug } from "@/lib/community-topics"

import { resetFormOrigin } from "@/lib/password-reset-request"

const missingPostPath = "/community/post-not-found"
const missingTopicPath = "/community/topic-not-found"

// Next 16.3.6의 렌더 중 notFound() 복구 문서는 무JS에서 body가 비어 있다.
// 고정 주제는 설정으로, 게시글은 공개 API의 POST_NOT_FOUND로 렌더 전에 판정한다.
export async function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname
  if (path === "/forgot-password" || path === "/reset-password") {
    const headers = resetFormOrigin(request.method, path, request.headers, `${request.nextUrl.protocol}//${request.headers.get("host")}`)
    return headers ? NextResponse.next({ request: { headers } }) : NextResponse.next()
  }
  // RSC is a transport for client navigation. A non-2xx Flight response makes
  // Next 16.3.6 fall back to a full document navigation and discard private memory.
  // Actual document requests (including reload/no-JS) still return HTTP 404.
  const postErrorStatus = request.headers.get("rsc") === "1" && request.headers.get("sec-fetch-dest") !== "document" ? 200 : 404
  if (path === missingPostPath) return NextResponse.next({ status: postErrorStatus })
  const returnHeaders = new Headers(request.headers)
  // Never trust an incoming copy of this header. Only one validated query value
  // is forwarded to the no-props not-found boundary, scoped to this request.
  const from = request.nextUrl.searchParams.getAll("from")
  returnHeaders.set("x-iyum-post-return", safeListReturn(from.length === 1 ? from[0] : null))
  const post = /^\/community\/posts\/([^/]+)$/.exec(path)
  if (post?.[1] === "create") return NextResponse.next()
  if (post) {
    // 렌더 중 notFound의 빈 복구 HTML을 피한다. 실제 계약의 404만 오류 경로로 보낸다.
    // 공개 GET에 사용자 Cookie/Authorization을 전달하지 않으며 정상 조회는 no-store다.
    try {
      await getPost(post[1], undefined, process.env.IYUM_API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL)
    } catch (error) {
      if (error instanceof CommunityApiError && error.status === 404 && error.code === "POST_NOT_FOUND") {
        const target = request.nextUrl.clone()
        target.pathname = missingPostPath
        return NextResponse.rewrite(target, { status: postErrorStatus })
      }
      // 장애·타임아웃·손상 응답은 상세 페이지의 기존 재시도 안내로 처리한다.
    }
    return NextResponse.next({ request: { headers: returnHeaders } })
  }
  // 안내 경로를 직접 열어도 오류 화면을 HTTP 200으로 제공하지 않는다.
  if (path === missingTopicPath)
    return NextResponse.next({ status: 404 })

  let slug: string
  try {
    slug = decodeURIComponent(request.nextUrl.pathname.split("/").at(-1) ?? "")
  } catch {
    slug = ""
  }
  if (topicBySlug(slug)) return NextResponse.next()

  const target = request.nextUrl.clone()
  target.pathname = missingTopicPath
  target.search = ""
  // 주소창은 원래 잘못된 주소를 유지한다. 정상 페이지/인증/게시글에는 적용하지 않는다.
  return NextResponse.rewrite(target, { status: 404 })
}

export const config = {
  matcher: ["/forgot-password", "/reset-password", "/community/topics/:slug", "/community/topic-not-found", "/community/posts/:postId", "/community/post-not-found"],
}
