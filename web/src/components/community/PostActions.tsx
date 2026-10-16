"use client"
// 확정된 서버 변경은 캐시에 반영하되 이전 화면의 후속 이동은 금지한다.
import { useRef, useState } from "react"
import { useRouter } from "next/navigation"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import CommunityDialog from "./CommunityDialog"
import {
  changePost,
  CommunityApiError,
  type PostDetail,
} from "@/lib/community-api"
import { postChanged } from "@/lib/community-mutations"

export default function PostActions({
  post,
  back,
}: {
  post: PostDetail
  back: string
}) {
  const auth = useAuth(),
    router = useRouter(),
    scope = useNavigationRequest()
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false)
  const [error, setError] = useState<CommunityApiError | null>(null)
  const inFlight = useRef(false)
  const owner =
    auth.status ===
      "authenticated" &&
    !auth.pending &&
    auth.member?.memberId ===
      post.author.memberId
  const detail = `/community/posts/${post.id}?from=${encodeURIComponent(back)}`
  function close() {
    if (!inFlight.current) {
      setOpen(false)
      setError(null)
    }
  }
  async function remove() {
    if (inFlight.current || !owner) return
    const current = scope.begin()
    inFlight.current = true
    setBusy(true)
    setError(null)
    try {
      const done = await changePost(
        String(post.id),
        "DELETE",
        undefined,
        current,
      )
      if (!done) return
      if (!current()) {
        postChanged(String(post.id))
        return
      }
      setOpen(false)
      router.replace(back)
      postChanged(String(post.id))
    } catch (e) {
      if (!current()) return
      setError(
        e instanceof CommunityApiError
          ? e
          : new CommunityApiError(
              0,
              "UNEXPECTED_RESPONSE",
              "삭제 결과를 확인하지 못했습니다.",
              {},
              true,
            ),
      )
      if (e instanceof CommunityApiError && e.status === 401)
        void auth.refresh()
    } finally {
      inFlight.current = false
      if (current()) setBusy(false)
    }
  }
  return (
    <>
      {owner && (
        <div className="community-owner-actions" aria-label="내 게시글 관리">
          <Link
            className="community-owner-action community-owner-action-edit"
            href={`/community/posts/${post.id}/edit?from=${encodeURIComponent(back)}`}
            prefetch={false}
          >
            수정
          </Link>
          <button
            className="community-owner-action"
            onClick={() => setOpen(true)}
          >
            삭제
          </button>
        </div>
      )}
      {open && (
        <CommunityDialog
          titleId="delete-title"
          descriptionId="delete-description"
          onCancel={close}
        >
          <h2 id="delete-title">게시글을 삭제할까요?</h2>
          <p id="delete-description">
            삭제하면 목록과 상세에서 더 이상 볼 수 없습니다. 삭제 요청 중에는 이
            창을 닫을 수 없습니다.
          </p>
          {error && (
            <div className="community-error" role="alert">
              <p>{error.message}</p>
              {error.uncertain && (
                <Link
                  href={detail}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="iyum-text-link"
                >
                  새 탭에서 게시글 확인
                </Link>
              )}
            </div>
          )}
          {!owner && !busy && (
            <p role="status">
              현재 로그인 상태를 확인해 주세요. 삭제를 계속하려면 작성자 계정이
              필요합니다.
            </p>
          )}
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              aria-disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              className="iyum-button community-danger"
              disabled={busy || !owner || error?.status === 404}
              onClick={() => void remove()}
            >
              {busy ? "삭제 중…" : "삭제하기"}
            </button>
          </div>
          <p role="status">{busy ? "삭제 요청을 처리하고 있습니다." : ""}</p>
        </CommunityDialog>
      )}
    </>
  )
}
