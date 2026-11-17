"use client"
import { usePathname } from "next/navigation"
import Link from "@/components/AppLink"
export default function AdminReportsLink() {
  const pathname = usePathname()
  const active = pathname === "/admin/reports" || pathname.startsWith("/admin/reports/")
  return (
    <Link
      href="/admin/reports"
      aria-current={active ? "page" : undefined}
      onNavigate={(e) => {
        if (pathname === "/admin/reports") e.preventDefault()
      }}
    >
      신고 관리
    </Link>
  )
}
