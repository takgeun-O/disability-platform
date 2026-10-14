import Image from "next/image"
import Link from "@/components/AppLink"
import {
  COMMUNITY_TOPICS,
  topicPath,
  type CommunityTopic,
} from "@/lib/community-topics"

export function TopicSpaces({ current }: { current?: CommunityTopic }) {
  return (
    <nav
      className={
        current
          ? "topic-spaces topic-spaces-side"
          : "iyum-container topic-spaces"
      }
      aria-label="주제별 커뮤니티"
    >
      <h2>
        {current ? "커뮤니티 둘러보기" : "어떤 이야기를 나누고 싶으세요?"}
      </h2>
      <ul>
        <li>
          <Link href="/community" aria-current={!current ? "page" : undefined}>
            <svg aria-hidden="true" width="40" height="36" viewBox="0 0 40 36" fill="none" stroke="currentColor" strokeWidth="1.5">
              <rect x="7" y="5" width="10" height="10" rx="2" />
              <rect x="23" y="5" width="10" height="10" rx="2" />
              <rect x="7" y="21" width="10" height="10" rx="2" />
              <rect x="23" y="21" width="10" height="10" rx="2" />
            </svg>
            <span>전체 게시글</span><span aria-hidden="true">↗</span>
          </Link>
        </li>
        {COMMUNITY_TOPICS.map((space) => (
          <li key={space.slug}>
            <Link
              href={topicPath(space)}
              aria-current={space.slug === current?.slug ? "page" : undefined}
            >
              <Image src={space.image} alt="" width={48} height={40} />
              <span>{space.name}</span>
              <span aria-hidden="true">↗</span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
export function TopicHeader({ space }: { space: CommunityTopic }) {
  return (
    <header className="topic-header">
      <div className="iyum-container">
        <nav aria-label="현재 위치" className="topic-breadcrumb">
          <Link href="/community">전체 게시글</Link>
          <span aria-hidden="true"> / </span>
          <span aria-current="page">{space.name}</span>
        </nav>
        <div className="topic-hero">
          <div>
            <h1>{space.name} 커뮤니티</h1>
            <p>{space.description}</p>
          </div>
          <Image src={space.image} alt="" width={200} height={144} priority />
        </div>
        <div className="topic-guidance">
          <strong>이용 안내</strong>
          <p>{space.guidance}</p>
        </div>
      </div>
    </header>
  )
}
