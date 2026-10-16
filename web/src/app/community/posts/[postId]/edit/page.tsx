import type { Metadata } from "next"
import PostEdit from "@/features/PostEdit"
import { readPublicPost } from "@/lib/community-server"
export const metadata: Metadata = {
  title: "게시글 수정 | IYUM",
  robots: { index: false, follow: false },
}
export default async function Page({
  params,
}: {
  params: Promise<{ postId: string }>
}) {
  const { postId } = await params
  return (
    <PostEdit key={postId} id={postId} initial={await readPublicPost(postId)} />
  )
}
