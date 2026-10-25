"use client"
import { useSyncExternalStore } from "react"
import { useAuth } from "@/components/AuthProvider"
import Link from "@/components/AppLink"
import type { PostSummary } from "@/lib/community-api"

export default function PostListReactions({ post, href, onLeaving }: { post: PostSummary; href: string; onLeaving: () => void }) {
  const { likes, status, pending } = useAuth()
  const state = useSyncExternalStore(likes.subscribe, () => likes.get(post.id), () => undefined)
  const liked = status === "authenticated" && !pending && !!state?.liked
  return <div className="post-reactions">
    <span className="post-like-count" data-liked={liked}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill={liked ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.75" aria-hidden="true" focusable="false"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>
      {liked && <span className="sr-only">내가 좋아요한 게시글 · </span>}
      좋아요 {(state?.likeCount ?? post.likeCount).toLocaleString("ko-KR")}
    </span>
    <Link className="post-comment-link" href={`${href}#comments-title`} scroll={false} prefetch={false} onNavigate={onLeaving}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true" focusable="false"><path d="M21 11.5a9 9 0 0 1-9 9 10 10 0 0 1-4-.8L3 21l1.3-4.9a9 9 0 1 1 16.7-4.6Z"/></svg>
      댓글 {post.commentCount.toLocaleString("ko-KR")}
    </Link>
  </div>
}
