"use client"
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
import { postDate, safeListReturn, requestListScrollRestore, listenForListReturn } from "@/lib/community-posts"

export default function PostDetail({ initial }: { initial: InitialPostDetail }) {
  const { postId } = useParams<{ postId: string }>()
  const params = useSearchParams()
  const back = safeListReturn(params.get("from"))
  useEffect(listenForListReturn, [])
  const [retry, setRetry] = useState(0)
  const key = `${postId}:${retry}`
  const [result, setResult] = useState<{
    key: string
    post?: Detail
    error?: ReadError
  }>({ key, post: initial.data ?? undefined, error: initial.error ?? undefined })
  useEffect(() => {
    if (retry === 0) return
    const controller = new AbortController()
    getPost(postId, controller.signal)
      .then((post) => {
        if (!controller.signal.aborted) setResult({ key, post })
      })
      .catch((error) => {
        if (!controller.signal.aborted)
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
  }, [postId, key, retry])
  const loading = result.key !== key
  const post = !loading ? result.post : undefined
  const error = !loading ? result.error : undefined
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
          onNavigate={() => requestListScrollRestore(back)}
          scroll={false}
        >
          ← 목록으로
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
              <div className="community-post-meta">
                <span>{post.author.nickname}</span>
                <span aria-hidden="true">·</span>
                <time dateTime={post.createdAt}>
                  {postDate(post.createdAt)}
                </time>
              </div>
            </header>
            <div className="community-post-body">{post.content}</div>
            <p className="community-help community-feature-note">
              댓글·좋아요 기능은 준비 중입니다.
            </p>
          </article>
        )}
        {post && (
          <Link
            className="iyum-button iyum-button-outline"
            href={back}
          onNavigate={() => requestListScrollRestore(back)}
            scroll={false}
          >
            목록으로 돌아가기
          </Link>
        )}
      </div>
    </main>
  )
}
