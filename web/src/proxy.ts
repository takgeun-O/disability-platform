import { NextResponse, type NextRequest } from "next/server"
import { CommunityApiError, getPost } from "@/lib/community-api"
import { topicBySlug } from "@/lib/community-topics"

const missingPostPath = "/community/post-not-found"
const missingTopicPath = "/community/topic-not-found"

// Next 16.3.6의 렌더 중 notFound() 복구 문서는 무JS에서 body가 비어 있다.
// 고정 주제는 설정으로, 게시글은 공개 API의 POST_NOT_FOUND로 렌더 전에 판정한다.
export async function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname
  if (path === missingPostPath) return NextResponse.next({ status: 404 })
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
        return NextResponse.rewrite(target, { status: 404 })
      }
      // 장애·타임아웃·손상 응답은 상세 페이지의 기존 재시도 안내로 처리한다.
    }
    return NextResponse.next()
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
  matcher: ["/community/topics/:slug", "/community/topic-not-found", "/community/posts/:postId", "/community/post-not-found"],
}
