import { redirect } from "next/navigation"
import PostList from "@/features/PostList"
import { listQuery, listHref, parsePostCursor } from "@/lib/community-posts"
import { readPublicFeed } from "@/lib/community-server"
import { topicPath, type CommunityTopic } from "@/lib/community-topics"

export type ListPageProps = { searchParams: Promise<Record<string, string | string[] | undefined>> }
export default async function CommunityListPage({ searchParams, space, pathname = "/community" }: ListPageProps & { space?: CommunityTopic; pathname?: string }) {
  const raw = await searchParams
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(raw)) if (value !== undefined) params.set(key, Array.isArray(value) ? value[0] : value)
  const path = space ? topicPath(space) : pathname
  const query = { ...listQuery(params, path), page: 0 }, cursor = parsePostCursor(params)
  const href = listHref(query, path, cursor)
  // 과거 page 링크는 주제/유형을 보존한 첫 피드로 보낸다. 불완전한 URL 커서도 제거한다.
  if (params.has("page") || (space && params.has("topic"))) redirect(listHref(query, path))
  if ((params.has("afterId") || params.has("afterCreatedAt")) && !cursor) redirect(listHref(query, path))
  return <PostList key={href} space={space} initial={{ ...await readPublicFeed(query, cursor), query, cursor, href }} />
}
