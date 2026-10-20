import { headers } from "next/headers"
import PostNotFoundView from "@/components/community/PostNotFoundView"
import { safeListReturn } from "@/lib/community-posts"

// Next not-found receives no page props. Proxy overwrites this request-only header.
export default async function NotFound() {
  const back = safeListReturn((await headers()).get("x-iyum-post-return"))
  return <PostNotFoundView back={back} />
}
