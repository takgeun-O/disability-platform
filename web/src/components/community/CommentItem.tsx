"use client"

import { useId, type ReactNode } from "react"
import { FeedAvatar } from "./FeedNavigation"
import WrittenTime from "@/components/WrittenTime"
import type { Comment } from "@/lib/community-comments"

/** Presentation only: the owner supplies mutations/forms and their request scope.
 * Only the detail owner opts into the public comment-{id} hash namespace.
 * Repeated copies of the same comment otherwise receive independent DOM IDs.
 */
export default function CommentItem({ comment, showAvatar, anchorId, arrived, body, reactions, actions, more, children }: {
  comment: Comment
  showAvatar?: boolean
  anchorId?: string
  arrived?: boolean
  body?: ReactNode
  reactions?: ReactNode
  actions?: ReactNode
  children?: ReactNode
  more?: ReactNode
}) {
  const instanceId = useId()
  const withAvatar = showAvatar && !!comment.author
  const byline = <>
    {comment.author && <strong>{comment.author.nickname}</strong>}
    <WrittenTime value={comment.createdAt} />
    {comment.contentUpdatedAt !== null && <span>수정됨</span>}
  </>
  return <li className={arrived ? "comment-arrived" : undefined} data-comment-item={comment.commentId} id={anchorId ?? `comment-item-${instanceId}`} tabIndex={-1}>
    <div className={withAvatar ? "community-comment-meta comment-author-header" : "community-comment-meta"}>
      {withAvatar ? <><FeedAvatar /><div className="comment-byline">{byline}</div></> : byline}
      {more}
    </div>
    {body ?? <p className="community-comment-body">{comment.content}</p>}
    {!comment.deleted && (reactions || actions) && <div className="community-comment-toolbar">
      {reactions}
      {actions}
    </div>}
    {children}
  </li>
}
