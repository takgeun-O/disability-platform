import type { Metadata } from "next"
import { notFound, redirect } from "next/navigation"
import { topicBySlug, topicPath } from "@/lib/community-topics"
import { listQuery, listHref } from "@/lib/community-posts"
import { readPublicPosts } from "@/lib/community-server"
import PostList from "@/features/PostList"
import type { ListPageProps } from "@/components/community/CommunityListPage"

type Props = ListPageProps & { params: Promise<{ slug: string }> }
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const space = topicBySlug((await params).slug)
  if (!space) return { title: "주제 공간을 찾을 수 없어요 | IYUM" }
  const title = `${space.name} 커뮤니티 | IYUM`
  return {
    title,
    description: space.description,
    openGraph: { title, description: space.description, type: "website" },
  }
}
export default async function TopicPage({ params, searchParams }: Props) {
  const space = topicBySlug((await params).slug)
  if (!space) notFound()
  const search = await searchParams
  const query = listQuery(
    {
      get: (key) => {
        const value = search[key]
        return (Array.isArray(value) ? value[0] : value) ?? null
      },
    },
    topicPath(space),
  )
  // 경로가 주제를 결정한다. 중복/충돌 topic 쿼리를 없애고 유형·페이지는 유지한다.
  if (search.topic !== undefined) redirect(listHref(query, topicPath(space)))
  return (
    <PostList
      key={space.slug}
      space={space}
      initial={{ ...(await readPublicPosts(query)), query }}
    />
  )
}
