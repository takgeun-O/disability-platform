import { Suspense } from "react"
import AdminMembers from "@/features/AdminMembers"
import "@/styles/admin-members.css"
export default function Page() { return <Suspense fallback={<p role="status">조회 조건을 확인하고 있습니다…</p>}><AdminMembers /></Suspense> }
