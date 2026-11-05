"use client"
import { usePathname } from "next/navigation"
import Link from "@/components/AppLink"
export default function AdminOperationLogsLink() {
  const active = usePathname() === "/admin/operation-logs"
  return <Link href="/admin/operation-logs" aria-current={active ? "page" : undefined}
    onNavigate={event => { if (active) event.preventDefault() }}>운영 기록</Link>
}
