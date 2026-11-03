import type { ReactNode } from "react"
import Link from "@/components/AppLink"
import AdminPostsLink from "@/components/admin/AdminPostsLink"
import AdminAccess from "@/components/admin/AdminAccess"
import "@/styles/admin.css"
export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <main className="admin-shell">
      <aside className="admin-sidebar">
        <p className="admin-brand">
          IYUM <span>관리자</span>
        </p>
        <nav aria-label="관리자 메뉴">
          <AdminPostsLink />
          <Link href="/">서비스로 돌아가기</Link>
        </nav>
      </aside>
      <div className="admin-main">
        <AdminAccess>{children}</AdminAccess>
      </div>
    </main>
  )
}
