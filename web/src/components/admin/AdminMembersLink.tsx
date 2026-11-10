"use client"
import { usePathname } from "next/navigation"
import Link from "@/components/AppLink"
export default function AdminMembersLink() {
  const active = usePathname() === "/admin/members"
  return <Link href="/admin/members" aria-current={active ? "page" : undefined} onNavigate={event => { if (active) event.preventDefault() }}>회원 관리</Link>
}
