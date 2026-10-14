import Link from "@/components/AppLink"
import {
  POST_TYPES,
  POST_TOPICS,
  type PostType,
  type PostTopic,
} from "@/lib/community-posts"

export function CommunityBanner({
  title = "커뮤니티",
  description = "질문과 경험을 함께 나누는 공간",
  writeHref,
}: {
  title?: string
  description?: string
  writeHref?: string
}) {
  return (
    <header className="community-banner">
      <div className="iyum-container community-banner-inner">
        <div>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        {writeHref && (
          <Link className="iyum-button iyum-button-primary" href={writeHref}>
            <span aria-hidden="true">＋</span> 글쓰기
          </Link>
        )}
      </div>
    </header>
  )
}
export function PostTags({ type, topic }: { type: PostType; topic: PostTopic }) {
  return (
    <div className="community-tags">
      <span>{POST_TYPES[type]}</span>
      <span>{POST_TOPICS[topic]}</span>
    </div>
  )
}
export function BestPosts() {
  return (
    <aside className="community-best iyum-card" aria-labelledby="best-heading">
      <h2 id="best-heading">
        <svg
          aria-hidden="true"
          width="28"
          height="28"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.7"
        >
          <path d="m12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z" />
        </svg>
        베스트글
      </h2>
      <p>베스트글을 준비하고 있어요.</p>
      <p className="community-help">선정 기준을 준비한 뒤 소개할게요.</p>
    </aside>
  )
}
