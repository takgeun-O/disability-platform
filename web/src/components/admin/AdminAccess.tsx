"use client"
import { usePathname } from "next/navigation"
import type { ReactNode } from "react"
import { useAuth } from "@/components/AuthProvider"
import Link from "@/components/AppLink"
export default function AdminAccess({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const pathname = usePathname()
  if (auth.status === "checking" || auth.pending || auth.memberRequests.paused)
    return <p role="status">권한을 확인하고 있습니다…</p>
  if (auth.status === "error")
    return (
      <div className="admin-access-card" role="alert">
        <p>로그인 상태를 확인하지 못했습니다.</p>
        <button onClick={() => void auth.refresh()}>다시 확인</button>
      </div>
    )
  if (auth.status !== "authenticated")
    return (
      <div className="admin-access-card">
        <p className="admin-access-eyebrow">IYUM 관리자</p>
        <h1>로그인이 필요합니다</h1>
        <p>관리자 계정으로 로그인하면 게시글 관리를 계속할 수 있어요.</p>
        <div className="admin-access-actions"><Link className="admin-primary-link" href={`/login?from=${encodeURIComponent(pathname)}`}>로그인</Link><Link href="/">서비스로 돌아가기</Link></div>
      </div>
    )
  if (auth.member?.role !== "ADMIN")
    return (
      <div className="admin-access-card" role="alert">
        <h1>관리자 권한이 없습니다</h1>
        <p>이 화면은 관리자만 이용할 수 있습니다.</p>
        <Link href="/">서비스로 돌아가기</Link>
      </div>
    )
  return (
    <div key={`${auth.member.memberId}:${auth.memberRequests.revision}`}>
      {children}
    </div>
  )
}
