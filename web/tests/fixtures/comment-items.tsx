"use client"
// Copied ONLY into an isolated test build by comment-items-browser.mjs --install.
import { useEffect, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import CommentLikeButton from "@/components/community/CommentLikeButton"
import "@/styles/community.css"
import CommentItem from "@/components/community/CommentItem"
const comment = { contentUpdatedAt: "2026-10-27T05:00:00Z", commentId: 42, content: "공통 댓글 본문\n공백  유지", author: { memberId: 1, nickname: "검증 작성자" }, createdAt: "2026-10-27T04:00:00.123456Z", updatedAt: "2026-10-27T05:00:00Z", likeCount: 3, liked: false }
function Region({ name }: { name: string }) {
  const auth = useAuth()
  useEffect(() => {
    if (auth.status === "authenticated") auth.commentLikes.read().accept([{ id: comment.commentId, likeCount: 3, liked: false }])
  }, [auth.commentLikes, auth.memberRequests.revision, auth.status])
  const [open, setOpen] = useState(false)
  return <section aria-label={name}><ul className="community-comment-list">
    <CommentItem comment={comment} reactions={<CommentLikeButton postId="7" comment={comment} ready={auth.status === "authenticated"} reread={async () => false} />}
      actions={<button onClick={() => setOpen(v => !v)}>내용 확인</button>} />
    {open && <li role="status">{name} 확인됨</li>}
    <CommentItem comment={comment} />
  </ul></section>
}
export default function Fixture() {
  return <main className="community-screen iyum-container community-comments"><Region name="첫 영역" /><Region name="둘째 영역" />
    <ul className="community-comment-list"><CommentItem comment={comment} anchorId="comment-42" /></ul>
  </main>
}
