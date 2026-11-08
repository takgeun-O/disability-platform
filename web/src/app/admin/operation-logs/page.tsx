import { Suspense } from "react"
import AdminOperationLogs from "@/features/AdminOperationLogs"
export default function Page() {
  return <Suspense fallback={<p role="status">조회 조건을 확인하고 있습니다…</p>}><AdminOperationLogs /></Suspense>
}
