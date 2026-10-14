import { NextResponse, type NextRequest } from "next/server"
import { topicBySlug } from "@/lib/community-topics"

const missingTopicPath = "/community/topic-not-found"

// Next 16.3.6의 렌더 중 notFound() 복구 문서는 무JS에서 body가 비어 있다.
// 고정 주제의 존재 여부만 렌더 전에 판정한다. API/세션 조회는 하지 않는다.
export function proxy(request: NextRequest) {
  // 안내 경로를 직접 열어도 오류 화면을 HTTP 200으로 제공하지 않는다.
  if (request.nextUrl.pathname === missingTopicPath)
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
  matcher: ["/community/topics/:slug", "/community/topic-not-found"],
}
