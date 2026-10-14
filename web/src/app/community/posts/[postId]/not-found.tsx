import { Suspense } from "react"
import Link from "next/link"
import PostNotFoundReturnLink from "@/components/community/PostNotFound"
import { CommunityBanner } from "@/components/community/CommunityParts"
export default function NotFound() {
  return <main id="main-content" className="community-screen">
    <CommunityBanner title="게시글" description="서로의 질문과 경험을 읽어보세요." />
    <div className="iyum-container community-reading">
      <section className="community-empty">
        <h2>게시글을 찾을 수 없어요.</h2>
        <p>삭제되었거나 없는 글입니다.</p>
        <Suspense fallback={<Link className="iyum-button iyum-button-outline" href="/community/posts">목록으로 돌아가기</Link>}>
          <PostNotFoundReturnLink />
        </Suspense>
      </section>
    </div>
  </main>
}
