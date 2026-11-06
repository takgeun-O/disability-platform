import { writtenTime } from "@/lib/written-time"
import { ADMIN_STATUSES, type AdminPost } from "@/lib/admin-posts"
import { FeedAvatar } from "@/components/community/FeedNavigation"
export function AdminStatusBadge({status}: {status: AdminPost["status"]}) {
  return <span className={`admin-badge is-${status.toLowerCase()}`}>{ADMIN_STATUSES[status]}</span>
}
export function AdminAuthor({author}: {author: AdminPost["author"]}) {
  return <span className="admin-author"><span className="admin-author-avatar" aria-hidden="true"><FeedAvatar /></span><span>{author.nickname}{author.withdrawn && <span className="admin-author-withdrawn"> · 탈퇴한 작성자</span>}</span></span>
}
export default function AdminContentMeta({item}: {item: Pick<AdminPost,"createdAt"|"updatedAt"|"hiddenAt"|"deletedAt">}) {
  const dates = [["작성",item.createdAt],["수정",item.updatedAt],["숨김",item.hiddenAt],["삭제",item.deletedAt]] as const
  return <div className="admin-content-meta"><dl>{dates.map(([label,value]) => value && <div key={label}><dt>{label}<span className="sr-only"> 일시</span></dt><dd><time dateTime={value}>{writtenTime(value).exact.replace(" (한국 시간)", "")}</time></dd></div>)}</dl></div>
}

// Comment body edits and moderation/entity changes have separate meanings.
export function AdminCommentMeta({item}: {item: Pick<AdminPost,"createdAt"|"updatedAt"|"hiddenAt"|"deletedAt"> & {contentUpdatedAt: string | null}}) {
  const dates = [["작성",item.createdAt],["본문 수정",item.contentUpdatedAt],["최종 변경",item.updatedAt],["숨김",item.hiddenAt],["삭제",item.deletedAt]] as const
  return <div className="admin-content-meta"><dl>{dates.map(([label,value]) => (value || label === "본문 수정") && <div key={label}><dt>{label}<span className="sr-only"> 일시</span></dt><dd>{value ? <time dateTime={value}>{writtenTime(value).exact.replace(" (한국 시간)", "")}</time> : "기록 없음"}</dd></div>)}</dl></div>
}
