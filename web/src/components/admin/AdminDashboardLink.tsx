"use client"
import { usePathname } from "next/navigation"
import Link from "@/components/AppLink"
export default function AdminDashboardLink() {
  const active = usePathname() === "/admin"
  return <Link href="/admin" aria-current={active ? "page" : undefined}
    onNavigate={event => { if (active) event.preventDefault() }}>
    대시보드
  </Link>
}
