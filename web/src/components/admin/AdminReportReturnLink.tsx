"use client"
import type { ComponentProps } from "react"
import Link from "@/components/AppLink"
import { useNavigationGuard } from "@/components/NavigationGuard"
import { dashboardReturnReportId } from "@/lib/admin-navigation"
import { openDashboardReport } from "@/lib/dashboard-report-navigation"

export default function AdminReportReturnLink(props: ComponentProps<typeof Link>) {
  const guard = useNavigationGuard()
  return <Link {...props} onNavigate={event => {
    const id = dashboardReturnReportId(props.href)
    if (id === null) return
    event.preventDefault()
    guard.request(() => openDashboardReport(id, true))
  }} />
}
