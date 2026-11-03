"use client"
import { usePathname } from "next/navigation"
import Link from "@/components/AppLink"
export default function AdminPostsLink() {
  const pathname = usePathname()
  return <Link href="/admin/posts" aria-current={pathname === "/admin/posts" ? "page" : /^\/admin\/posts\/[1-9]\d*$/.test(pathname) ? "location" : undefined}
    onNavigate={event => { if (pathname === "/admin/posts") event.preventDefault() }}>게시글 관리</Link>
}
