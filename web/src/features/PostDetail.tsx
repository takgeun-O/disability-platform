"use client"
import WrittenTime from "@/components/WrittenTime"
import PostLikeButton from "@/components/community/PostLikeButton"
import { useAuth } from "@/components/AuthProvider"
import Comments from "@/components/community/Comments"
import type { CommentPage } from "@/lib/community-comments"
import type { PublicResult } from "@/lib/community-load"
import PostActions from "@/components/community/PostActions"
import { usePostVersion } from "@/lib/community-mutations"
import { useEffect, useState } from "react"
import { useParams, useSearchParams } from "next/navigation"
import Link from "@/components/AppLink"
import {
  CommunityBanner,
  PostTags,
} from "@/components/community/CommunityParts"
import {
  CommunityApiError,
  getPost,
  type PostDetail as Detail,
} from "@/lib/community-api"
import { isMissingPost, type InitialPostDetail, type ReadError } from "@/lib/community-load"
import { HOME_RECENT_HREF, safeListReturn, listReturnLabel, requestListScrollRestore, listenForListReturn } from "@/lib/community-posts"

import { topicByValue, topicPath } from "@/lib/community-topics"

export default function PostDetail({ initial, initialComments }: { initial: InitialPostDetail; initialComments: PublicResult<CommentPage> }) {
  const { postId } = useParams<{ postId: string }>()
  const { likes, memberRequests, status, pending } = useAuth()
  const params = useSearchParams()
  useEffect(listenForListReturn, [])
  // Only an explicit comments destination opts into motion; ordinary detail entry
  // and background refreshes retain their existing scroll policy.
  useEffect(() => {
    if (location.hash !== "#comments-title") return
    const frame = requestAnimationFrame(() => {
      const heading = document.getElementById("comments-title")
      if (!heading) return
      heading.focus({ preventScroll: true })
      heading.scrollIntoView({ behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth", block: "start" })
    })
    return () => cancelAnimationFrame(frame)
  }, [postId])

  const [retry, setRetry] = useState(0)
  const mutation = usePostVersion(postId)
  const key = `${postId}:${retry}:${mutation}`
  const [result, setResult] = useState<{
    key: string
    post?: Detail
    error?: ReadError
  }>({ key: `${postId}:0:0`, post: initial.data ?? undefined, error: initial.error ?? undefined })
  useEffect(() => {
    if ((retry === 0 && mutation === 0) || status === "checking" || pending || memberRequests.paused) return
    const controller = new AbortController()
    const likeRead = likes.read()
    getPost(postId, controller.signal)
      .then((post) => {
        if (!controller.signal.aborted && likeRead.valid()) { setResult({ key, post }); likeRead.accept([post]) }
      })
      .catch((error) => {
        if (!controller.signal.aborted && likeRead.valid())
          setResult({
            key,
            error:
              error instanceof CommunityApiError
                ? error
                : new CommunityApiError(
                    0,
                    "UNEXPECTED_RESPONSE",
                    "게시글을 불러오지 못했습니다.",
                  ),
          })
      })
    return () => controller.abort()
  }, [postId, key, retry, mutation, likes, memberRequests, status, pending])
  useEffect(() => {
    if (retry !== 0 || mutation !== 0 || status === "checking" || pending || memberRequests.paused) return
    const controller = new AbortController(), ticket = likes.read()
    getPost(postId, controller.signal).then(post => { if (!controller.signal.aborted) ticket.accept([post]) }).catch(() => { /* The button offers an explicit read retry; body remains available. */ })
    return () => controller.abort()
  }, [postId, retry, mutation, likes, memberRequests, status, pending])
  const loading = result.key !== key
  const post = !loading ? result.post : undefined
  const error = !loading ? result.error : undefined
  const space = post && topicByValue(post.topic)
  const back = safeListReturn(params.get("from"), space ? topicPath(space) : "/community/posts")
  const returningHome = back === HOME_RECENT_HREF
  const restoreList = returningHome ? undefined : () => requestListScrollRestore(back)
  const backLabel = listReturnLabel(back)
  return (
    <main id="main-content" className="community-screen">
      <CommunityBanner
        title="게시글"
        description="서로의 질문과 경험을 읽어보세요."
      />
      <div className="iyum-container community-reading">
        <Link
          className="iyum-text-link community-back"
          href={back}
          onNavigate={restoreList}
          scroll={returningHome}
        >
          ← {backLabel}
        </Link>
        {loading && (
          <p className="community-loading" role="status">
            게시글을 불러오고 있습니다.
          </p>
        )}
        {error && (
          <section className="community-error" role="alert">
            <h2>
              {isMissingPost(error)
                ? "게시글을 찾을 수 없어요."
                : "잠시 불러오지 못했어요."}
            </h2>
            <p>{error.message}</p>
            {!isMissingPost(error) && (
              <button
                className="iyum-button iyum-button-outline"
                onClick={() => setRetry((n) => n + 1)}
              >
                다시 시도
              </button>
            )}
          </section>
        )}
        {post && (
          <article className="community-article">
            <header>
              <PostTags type={post.type} topic={post.topic} />
              <h2>{post.title}</h2>
              <div className="community-article-meta-row">
              <div className="community-post-meta">
                <span>{post.author.nickname}</span>
                <span aria-hidden="true">·</span>
                <WrittenTime value={post.createdAt} />
              </div>
              <PostActions post={post} back={back} />
              </div>
            </header>
            <div className="community-post-body">{post.content}</div>
            <PostLikeButton post={post} />

          </article>
        )}
        {(initial.data || result.post) && (
          <Comments key={postId} postId={postId} initial={initialComments} returnTo={`/community/posts/${postId}?from=${encodeURIComponent(back)}`} />
        )}
        {post && (
          <Link
            className="iyum-button iyum-button-outline"
            href={back}
            onNavigate={restoreList}
            scroll={returningHome}
          >
            {backLabel}
          </Link>
        )}
      </div>
    </main>
  )
}
