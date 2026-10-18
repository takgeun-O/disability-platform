import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { topicBySlug } from "@/lib/community-topics"
import CommunityListPage, { type ListPageProps } from "@/components/community/CommunityListPage"

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
  return <CommunityListPage searchParams={searchParams} space={space} />
}
