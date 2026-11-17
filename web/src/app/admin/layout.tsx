import type { ReactNode } from "react"
import Link from "@/components/AppLink"
import AdminDashboardLink from "@/components/admin/AdminDashboardLink"
import AdminPostsLink from "@/components/admin/AdminPostsLink"
import AdminOperationLogsLink from "@/components/admin/AdminOperationLogsLink"
import AdminReportsLink from "@/components/admin/AdminReportsLink"
import AdminMembersLink from "@/components/admin/AdminMembersLink"
import AdminDashboardRouteHost from "@/components/admin/AdminDashboardRouteHost"
import AdminAccess from "@/components/admin/AdminAccess"
import "@/styles/admin.css"
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <main className="admin-shell">
      <aside className="admin-sidebar">
        <p className="admin-brand">
          관리자 메뉴
        </p>
        <nav aria-label="관리자 메뉴">
          <AdminDashboardLink />
          <AdminPostsLink />
          <AdminOperationLogsLink />
          <AdminMembersLink />
          <AdminReportsLink />
          <Link href="/">서비스로 돌아가기</Link>
        </nav>
      </aside>
      <div className="admin-main">
        <AdminAccess><AdminDashboardRouteHost>{children}</AdminDashboardRouteHost></AdminAccess>
      </div>
    </main>
  )
}
