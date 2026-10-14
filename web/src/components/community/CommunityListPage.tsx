import PostList from "@/features/PostList"
import { parsePostQuery } from "@/lib/community-posts"
import { readPublicPosts } from "@/lib/community-server"

export type ListPageProps = { searchParams: Promise<Record<string, string | string[] | undefined>> }
export default async function CommunityListPage({ searchParams }: ListPageProps) {
  const params = await searchParams
  const query = parsePostQuery({ get: (key) => {
    const value = params[key]
    return (Array.isArray(value) ? value[0] : value) ?? null
  } })
  return <PostList initial={{ ...await readPublicPosts(query), query }} />
}
