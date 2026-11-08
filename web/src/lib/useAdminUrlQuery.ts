"use client"
import { useSearchParams } from "next/navigation"
import { useNavigationGuard } from "@/components/NavigationGuard"

// Native filter replaceState updates searchParams without a route transition.
// Real navigation changes the existing guard's lifetime: remount only then, so
// returning pages restore applied URL inputs while same-page drafts survive.
export function useAdminUrlQuery() {
  const params = useSearchParams()
  const { getApprovalVersion } = useNavigationGuard()
  const values: Record<string, string | string[]> = {}
  for (const key of params.keys()) {
    const all = params.getAll(key)
    values[key] = all.length === 1 ? all[0] : all
  }
  return { values, navigation: getApprovalVersion() }
}
