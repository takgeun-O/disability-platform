"use client"
import Link from "@/components/AppLink"
import AdminModeration from "@/components/admin/AdminModeration"
import AdminCommentPreview from "@/components/admin/AdminCommentPreview"
export default function AdminReportedComment({id,returnHref}:{id:number;returnHref:string}) {
  return <AdminModeration><section className="admin-panel">
    <header className="admin-heading"><h1>댓글 #{id} 관리</h1><Link href={returnHref}>← 신고 관리로</Link></header>
    {id > 0 ? <AdminCommentPreview key={id} commentId={id} manage returnHref={returnHref}/> : <p role="alert">올바른 댓글 ID가 필요합니다.</p>}
  </section></AdminModeration>
}
