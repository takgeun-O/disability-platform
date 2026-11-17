import AdminReportPage from "@/features/AdminReportPage"
import { adminSelectionId } from "@/lib/admin-navigation"
import "@/styles/content-reports.css"

export default async function Page({ params }: {
  params: Promise<{ reportId: string }>
}) {
  const { reportId } = await params
  return <AdminReportPage key={reportId} id={adminSelectionId(reportId)} />
}
