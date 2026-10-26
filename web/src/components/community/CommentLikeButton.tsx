"use client"
import { useId, useState, useSyncExternalStore } from "react"
import { useRouter } from "next/navigation"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationGuard, useNavigationRequest } from "@/components/NavigationGuard"
import { changeCommentLike, CommentError, type Comment } from "@/lib/community-comments"
import CommunityDialog from "./CommunityDialog"

export default function CommentLikeButton({ postId, comment, ready, reread }: {
  postId: string; comment: Comment; ready: boolean; reread: (commentId: number) => Promise<boolean | undefined>
}) {
  const auth = useAuth(), store = auth.commentLikes, scope = useNavigationRequest(), guard = useNavigationGuard(), router = useRouter(), uid = useId()
  const state = useSyncExternalStore(store.subscribe, () => store.get(comment.commentId), () => undefined)
  const [prompt, setPrompt] = useState(false)
  const [checking, setChecking] = useState<number | null>(null)
  const [feedback, setFeedback] = useState<{ revision: number; message: string; uncertain: boolean } | null>(null)
  const error = feedback?.revision === auth.memberRequests.revision ? feedback : null
  const authenticated = auth.status === "authenticated" && !!auth.member && !auth.pending
  const liked = authenticated && !!state?.liked
  const needsRead = !ready || !state || state.needsRead || error?.uncertain
  const blocked = auth.status === "checking" || !!auth.pending || auth.memberRequests.paused || !!state?.pending || checking === auth.memberRequests.revision
  async function act() {
    if (blocked || !scope.isActive()) return
    if (!authenticated || !auth.member) { setPrompt(true); return }
    const session = auth.captureMemberRequest(auth.member.memberId)
    const current = () => scope.isActive() && session.isCurrent()
    if (!current()) return
    setFeedback(null)
    if (needsRead) {
      setChecking(auth.memberRequests.revision)
      try {
        const ok = await reread(comment.commentId)
        if (current() && ok === false) setFeedback({ revision: auth.memberRequests.revision, message: "댓글 좋아요 상태를 확인하지 못했습니다. 다시 확인해 주세요.", uncertain: true })
      } finally { if (current()) setChecking(null) }
      return
    }
    const operation = store.change(comment.commentId)
    if (!operation) return
    try {
      const result = await changeCommentLike(postId, comment.commentId, !liked, current)
      if (result && current() && operation.valid()) operation.commit(result)
      else if (operation.valid()) operation.uncertain()
    } catch (cause) {
      if (!operation.valid()) return
      if (!current()) { operation.uncertain(); return }
      const e = cause instanceof CommentError ? cause : null
      const uncertain = !e || e.uncertain || e.code === "COMMENT_NOT_FOUND" || e.code === "POST_NOT_FOUND"
      if (uncertain) operation.uncertain()
      if (e?.status === 401) { session.expire(); setPrompt(true); return }
      setFeedback({ revision: auth.memberRequests.revision, message: e?.message ?? "좋아요 처리 결과를 확인하지 못했습니다. 댓글 목록에서 현재 상태를 다시 확인해 주세요.", uncertain })
    } finally { operation.finish() }
  }
  return <div className="post-like comment-like" data-comment-like={comment.commentId} data-like-ready={ready && !!state}>
    <button type="button" className="post-like-button" aria-pressed={liked} aria-disabled={blocked}
      aria-label={authenticated && needsRead ? "댓글 좋아요 상태 다시 확인" : liked ? "댓글 좋아요 취소" : "댓글 좋아요 등록"}
      aria-describedby={`${uid}-count${blocked ? ` ${uid}-pending` : ""}${error ? ` ${uid}-error` : ""}`} onClick={() => void act()}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill={liked ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>
      <span id={`${uid}-count`}>좋아요 {(state?.likeCount ?? comment.likeCount).toLocaleString("ko-KR")}</span>
      {blocked && <span id={`${uid}-pending`} className="sr-only">처리 중</span>}
    </button>
    {error && <p id={`${uid}-error`} role="alert" className="post-like-error">{error.message}{error.uncertain && " 좋아요 버튼으로 현재 상태를 다시 확인해 주세요. 자동으로 재전송하지 않습니다."}</p>}
    {prompt && <CommunityDialog titleId={`${uid}-title`} descriptionId={`${uid}-description`} onCancel={() => setPrompt(false)}>
      <h2 id={`${uid}-title`}>로그인이 필요해요</h2>
      <p id={`${uid}-description`}>댓글 좋아요를 등록하려면 로그인해 주세요. 로그인 후 자동으로 등록하지 않습니다.</p>
      <div className="community-dialog-actions">
        <button type="button" data-initial-focus className="iyum-button iyum-button-outline" onClick={() => setPrompt(false)}>그대로 머무르기</button>
        <button type="button" className="iyum-button iyum-button-primary" disabled={!!auth.pending || auth.status === "checking"} onClick={() => {
          if (auth.status === "error") { void auth.refresh(); return }
          if (authenticated) { setPrompt(false); return }
          const destination = location.pathname + location.search + `#comment-${comment.commentId}`
          guard.request(() => { setPrompt(false); router.push(`/login?from=${encodeURIComponent(destination)}`) })
        }}>{auth.status === "error" ? "로그인 상태 다시 확인" : authenticated ? "현재 화면에서 계속하기" : "로그인하기"}</button>
      </div>
    </CommunityDialog>}
  </div>
}
