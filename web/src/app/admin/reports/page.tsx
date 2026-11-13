import { Suspense } from "react"
import AdminReports from "@/features/AdminReports"
import "@/styles/content-reports.css"
export default function Page() {
  return (
    <Suspense
      fallback={<p role="status">신고 관리 화면을 준비하고 있습니다…</p>}
    >
      <AdminReports />
    </Suspense>
  )
}
