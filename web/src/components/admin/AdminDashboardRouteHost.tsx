"use client"
import { useSyncExternalStore, type ReactNode } from "react"
import { usePathname } from "next/navigation"
import AdminDashboard from "@/features/AdminDashboard"
import { dashboardModalSnapshot, subscribeDashboardModal } from "@/lib/dashboard-report-navigation"
import "@/styles/admin-dashboard.css"
import "@/styles/content-reports.css"

const serverSnapshot = () => ""
export default function AdminDashboardRouteHost({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  const modal = useSyncExternalStore(subscribeDashboardModal, dashboardModalSnapshot, serverSnapshot)
  const id = modal ? Number(modal.split(":")[0]) : null
  const modalId = id !== null && pathname === `/admin/reports/${id}` ? id : null
  // Other routes never keep an old modal or dashboard mounted behind them.
  if (pathname === "/admin" || modalId !== null) {
    return <AdminDashboard modalId={modalId} />
  }
  return children
}
