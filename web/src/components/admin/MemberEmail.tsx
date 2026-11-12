"use client"
import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"

// Keyed by the detail opening's member. Unmount revokes both clipboard promises
// and the last-success timer, including close → reopen of the same member.
export default function MemberEmail({email}: {email: string | null}) {
  const auth = useAuth(), scope = useNavigationRequest()
  const [notice, setNotice] = useState<"success" | "failure" | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  async function copy() {
    if (email === null || !auth.member) return
    const session = auth.captureMemberRequest(auth.member.memberId), current = scope.begin()
    try {
      await navigator.clipboard.writeText(email)
      if (!current() || !session.isCurrent()) return
      if (timer.current) clearTimeout(timer.current)
      setNotice("success")
      timer.current = setTimeout(() => { if (scope.isActive() && session.isCurrent()) setNotice(null) }, 3000)
    } catch {
      if (current() && session.isCurrent()) { if (timer.current) clearTimeout(timer.current); setNotice("failure") }
    }
  }
  return <>
    <h3 className="admin-member-email-title">이메일</h3>
    <div className="admin-member-email-detail"><span>{email ?? "이메일 정보 없음"}</span>
      {email !== null && <button aria-label={notice === "success" ? "✓ 복사됨" : "이메일 복사"} className="admin-member-copy" onClick={() => void copy()}><span role="status" aria-live="polite">{notice === "success" ? "✓ 복사됨" : "이메일 복사"}</span></button>}
    </div>
    {notice === "failure" && <p className="admin-member-copy-error" role="status">복사하지 못했습니다. 이메일을 직접 선택해 복사해 주세요.</p>}
  </>
}
