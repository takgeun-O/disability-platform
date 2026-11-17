"use client"
import Link from "@/components/admin/AdminReportReturnLink"
import AdminModeration from "@/components/admin/AdminModeration"
import AdminCommentPreview from "@/components/admin/AdminCommentPreview"
import { adminReturnHref, adminSelectionId } from "@/lib/admin-navigation"
import "@/styles/report-comment-management.css"

export default function AdminReportedComment({ id, returnHref }: { id: number; returnHref: string }) {
  const safeReturn = adminReturnHref(returnHref)
  const url = new URL(safeReturn, "http://internal.invalid")
  const fromReport = url.pathname === "/admin/reports" || url.pathname.startsWith("/admin/reports/")
  const reportId = fromReport ? adminSelectionId(url.pathname.startsWith("/admin/reports/") ? url.pathname.slice("/admin/reports/".length) : url.searchParams.get("selected")) : null
  return <AdminModeration reportCommentContext={fromReport}>
    <section className="admin-panel report-comment-management">
      <header className="admin-heading">
        <h1>댓글 #{id} 관리</h1>
        <Link href={safeReturn}>{reportId ? `← 신고 #${reportId}로 돌아가기` : "← 신고 관리로"}</Link>
      </header>
      {fromReport && <p className="report-comment-context">콘텐츠 숨김·숨김 해제를 관리하는 화면입니다. 신고 처리 상태는 변경되지 않습니다.</p>}
      {id > 0 ? <AdminCommentPreview key={id} commentId={id} manage returnHref={safeReturn} managementSections/> : <p role="alert">올바른 댓글 ID가 필요합니다.</p>}
    </section>
  </AdminModeration>
}
