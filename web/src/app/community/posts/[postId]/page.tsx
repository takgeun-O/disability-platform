import type { Metadata } from "next"
import { notFound } from "next/navigation"
import PostDetail from "@/features/PostDetail"
import { readPublicPost, readPublicComments } from "@/lib/community-server"
import { isMissingPost } from "@/lib/community-load"

type Props = { params: Promise<{ postId: string }> }
async function load(params: Props["params"]) {
  const { postId } = await params
  const result = await readPublicPost(postId)
  if (isMissingPost(result.error)) notFound()
  return { postId, result }
}
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { postId } = await params
  const result = await readPublicPost(postId)
  if (isMissingPost(result.error)) return { title: "게시글을 찾을 수 없어요 | IYUM", robots: { index: false, follow: false } }
  if (!result.data) return { title: "게시글을 불러오지 못했어요 | IYUM" }
  const post = result.data
  const title = `${post.title} | IYUM 커뮤니티`
  const description = Array.from(post.content.replace(/\s+/g, " ").trim()).slice(0, 160).join("")
  // 배포 주소를 추정하거나 요청 Host를 신뢰하지 않는다. 설정 전에는 URL 메타를 생략한다.
  const site = process.env.IYUM_SITE_URL
  const url = site && /^https?:\/\//.test(site) ? new URL(`/community/posts/${postId}`, site).toString() : undefined
  return {
    title, description, ...(url ? { alternates: { canonical: url } } : {}),
    openGraph: { type: "article", title, description, locale: "ko_KR", siteName: "IYUM", url,
      authors: [post.author.nickname], publishedTime: post.createdAt, modifiedTime: post.updatedAt },
    twitter: { card: "summary", title, description },
  }
}
export default async function Page({ params }: Props) {
  const { postId, result } = await load(params)
  // 무JS 공개 열람을 위해 첫 댓글까지 해결한 뒤 렌더링한다. 게시글 404 판단은 먼저 유지한다.
  const comments = await readPublicComments(postId)
  return <PostDetail key={postId} initial={result} initialComments={comments} />
}
