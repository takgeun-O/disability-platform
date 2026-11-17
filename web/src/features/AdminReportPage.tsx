"use client"
import { useCallback, useState } from "react"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import AdminReportDetail, { type ReportSuccessNotice } from "@/components/admin/AdminReportDetail"
import CommentSuccessToast from "@/components/community/CommentSuccessToast"
import { AdminApiError } from "@/lib/admin-posts"
import { defaultReportQuery, reportHref, ReportMutationError } from "@/lib/content-reports"
import "@/styles/community.css"

export default function AdminReportPage({ id }: { id: number | null }) {
  const auth = useAuth()
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const [denied, setDenied] = useState(false)
  const [toast, setToast] = useState<ReportSuccessNotice | null>(null)
  const dismissToast = useCallback(() => setToast(null), [])
  const protect = useCallback((error: unknown) => {
    if (error instanceof AdminApiError || error instanceof ReportMutationError) {
      if (error.status === 401) {
        setToast(null)
        capture(actor).expire()
        return true
      }
      if (error.status === 403 && (!(error instanceof ReportMutationError) ||
        ["ADMIN_REQUIRED", "ACCESS_DENIED"].includes(error.code))) {
        setToast(null)
        setDenied(true)
        return true
      }
    }
    return false
  }, [actor, capture])
  if (denied) return <section className="admin-access-card"><h1>관리자 권한이 없습니다.</h1></section>
  return (
    <section className="admin-reports report-page">
      <nav className="report-page-navigation" aria-label="신고 상세 탐색">
        <Link href="/admin">대시보드로 돌아가기</Link>
        <Link href={reportHref(defaultReportQuery)}>신고 목록 보기</Link>
      </nav>
      <div role="status" aria-atomic="true" className="comment-toast-announcer report-toast">
        {toast && toast.valid() && <CommentSuccessToast key={toast.key} message={toast.message} onDismiss={dismissToast} />}
      </div>
      {id === null ? <div className="report-page-content"><h1>신고 상세</h1><p role="alert">올바른 신고 ID가 필요합니다.</p></div> : (
        <AdminReportDetail key={id} id={id} presentation="page" returnHref={`/admin/reports/${id}`} notify={setToast} protect={protect} />
      )}
    </section>
  )
}
