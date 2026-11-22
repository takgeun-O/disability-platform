"use client"

import { useId, type ReactNode } from "react"
import WrittenTime from "@/components/WrittenTime"
import type { Comment } from "@/lib/community-comments"

/** Presentation only: the owner supplies mutations/forms and their request scope.
 * Only the detail owner opts into the public comment-{id} hash namespace.
 * Repeated copies of the same comment otherwise receive independent DOM IDs.
 */
export default function CommentItem({ comment, anchorId, arrived, body, reactions, actions, more }: {
  comment: Comment
  anchorId?: string
  arrived?: boolean
  body?: ReactNode
  reactions?: ReactNode
  actions?: ReactNode
  more?: ReactNode
}) {
  const instanceId = useId()
  return <li className={arrived ? "comment-arrived" : undefined} data-comment-item={comment.commentId} id={anchorId ?? `comment-item-${instanceId}`} tabIndex={-1}>
    <div className="community-comment-meta">
      <strong>{comment.author.nickname}</strong>
      <WrittenTime value={comment.createdAt} />
      {comment.contentUpdatedAt !== null && <span>수정됨</span>}
      {more}
    </div>
    {body ?? <p className="community-comment-body">{comment.content}</p>}
    {(reactions || actions) && <div className="community-comment-toolbar">
      {reactions}
      {actions}
    </div>}
  </li>
}
