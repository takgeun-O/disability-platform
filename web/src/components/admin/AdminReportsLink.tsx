"use client"
import { usePathname } from "next/navigation"
import Link from "@/components/AppLink"
export default function AdminReportsLink() {
  const active = usePathname() === "/admin/reports"
  return (
    <Link
      href="/admin/reports"
      aria-current={active ? "page" : undefined}
      onNavigate={(e) => {
        if (active) e.preventDefault()
      }}
    >
      신고 관리
    </Link>
  )
}
