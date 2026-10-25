"use client"
import { useId, useState, useSyncExternalStore, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationGuard, useNavigationRequest } from "@/components/NavigationGuard"
import { changePostLike, getPost, CommunityApiError, type LikeState } from "@/lib/community-api"
import { requestListScrollRestore } from "@/lib/community-posts"
import { postChanged } from "@/lib/community-mutations"
import CommunityDialog from "./CommunityDialog"

export default function PostLikeButton({ post, onLeaving }: { post: LikeState & { id: number }; onLeaving?: () => void }) {
  const auth = useAuth(), { likes } = auth, scope = useNavigationRequest(), guard = useNavigationGuard(), router = useRouter(), uid = useId()
  const state = useSyncExternalStore(likes.subscribe, () => likes.get(post.id), () => undefined)
  const [prompt, setPrompt] = useState(false)
  const [feedback, setFeedback] = useState<{ revision: number; message: string; uncertain: boolean } | null>(null)
  const error = feedback?.revision === auth.memberRequests.revision ? feedback : null
  const authenticated = auth.status === "authenticated" && !!auth.member && !auth.pending
  const liked = authenticated && (state?.liked ?? false), count = state?.likeCount ?? post.likeCount
  const blocked = auth.status === "checking" || !!auth.pending || auth.memberRequests.paused || !!state?.pending
  useEffect(() => {
    if (!state || !scope.isActive()) return
    const destination = location.pathname + location.search
    const y = likes.takeReturn(destination)
    if (y !== null) requestAnimationFrame(() => { if (scope.isActive()) window.scrollTo({ top: y, behavior: "instant" }) })
  }, [state, likes, scope])
  async function act() {
    if (blocked || !scope.isActive()) return
    if (!authenticated || !auth.member) { setPrompt(true); return }
    const session = auth.captureMemberRequest(auth.member.memberId)
    const current = () => session.isCurrent() && scope.isActive()
    if (!current()) return
    setFeedback(null)
    // An unknown outcome must be read back before another explicit mutation.
    if (!state || state.needsRead || error?.uncertain) {
      const ticket = likes.read()
      try { const data = await getPost(String(post.id)); if (current()) ticket.accept([data]) }
      catch (cause) {
        if (!current()) return
        if (cause instanceof CommunityApiError && cause.status === 401) { session.expire(); setPrompt(true); return }
        if (cause instanceof CommunityApiError && cause.code === "POST_NOT_FOUND") postChanged(String(post.id))
        setFeedback({ revision: auth.memberRequests.revision, message: cause instanceof CommunityApiError ? cause.message : "좋아요 상태를 확인하지 못했습니다. 다시 확인해 주세요.", uncertain: true })
      }
      return
    }
    const operation = likes.change(post.id)
    if (!operation) return
    try {
      const result = await changePostLike(post.id, !liked, current)
      if (result && session.isCurrent() && operation.valid()) operation.commit(result)
    } catch (cause) {
      if (!session.isCurrent() || !operation.valid()) return
      const e = cause instanceof CommunityApiError ? cause : null
      if (!e || e.uncertain) operation.uncertain()
      if (!current()) return
      if (e?.status === 401) { session.expire(); setPrompt(true); return }
      if (e?.code === "POST_NOT_FOUND") postChanged(String(post.id))
      setFeedback({ revision: auth.memberRequests.revision, message: e?.message ?? "좋아요 처리 결과를 확인하지 못했습니다. 현재 상태를 다시 확인해 주세요.", uncertain: e?.uncertain ?? true })
    } finally { operation.finish() }
  }
  return <div className="post-like" data-post-like={post.id} data-like-ready={!authenticated || !!state}>
    <button type="button" className="post-like-button" aria-pressed={liked} aria-disabled={blocked} aria-describedby={error ? `${uid}-error` : undefined}
      aria-label={(error?.uncertain || state?.needsRead || (authenticated && !state)) ? "좋아요 상태 다시 확인" : liked ? "좋아요 취소" : "좋아요 등록"} onClick={() => void act()}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill={liked ? "currentColor" : "none"} stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/></svg>
      <span>좋아요 {count.toLocaleString("ko-KR")}</span>{state?.pending && <span className="sr-only">처리 중</span>}
    </button>
    {error && <p id={`${uid}-error`} role="alert" className="post-like-error">{error.message}</p>}
    {prompt && <CommunityDialog titleId={`${uid}-title`} descriptionId={`${uid}-description`} onCancel={() => setPrompt(false)}>
      <h2 id={`${uid}-title`}>로그인이 필요해요</h2><p id={`${uid}-description`}>좋아요를 등록하려면 로그인해 주세요. 로그인 후 자동으로 등록하지 않습니다.</p>
      <div className="community-dialog-actions"><button type="button" data-initial-focus className="iyum-button iyum-button-outline" onClick={() => setPrompt(false)}>그대로 머무르기</button>
        <button type="button" className="iyum-button iyum-button-primary" disabled={!!auth.pending || auth.status === "checking"} onClick={() => {
          if (auth.status === "error") { void auth.refresh(); return }
          if (authenticated) { setPrompt(false); return }
          const destination = location.pathname + location.search
          guard.request(() => { onLeaving?.(); if (onLeaving) requestListScrollRestore(destination); likes.rememberReturn(destination, window.scrollY); setPrompt(false); router.push(`/login?from=${encodeURIComponent(destination)}`) })
        }}>{auth.status === "error" ? "로그인 상태 다시 확인" : authenticated ? "현재 화면에서 계속하기" : "로그인하기"}</button></div>
    </CommunityDialog>}
  </div>
}
