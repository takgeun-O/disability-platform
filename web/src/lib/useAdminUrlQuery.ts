"use client"
import { getAdminDialogReturn } from "./admin-dialog-return"
import { useSearchParams, usePathname } from "next/navigation"
import { useNavigationGuard } from "@/components/NavigationGuard"

// Native filter replaceState updates searchParams without a route transition.
// Real navigation changes the existing guard's lifetime: remount only then, so
// returning pages restore applied URL inputs while same-page drafts survive.
export function useAdminUrlQuery() {
  const search = useSearchParams(),pathname=usePathname(),pending=getAdminDialogReturn()
  // A known modal flow can return to an older page index after server page correction.
  // Initialize from the approved destination before list effects synchronize the URL.
  const params = pending&&pathname+"?"+search.toString()===pending.baseHref ? new URLSearchParams(pending.href.split("?")[1]??"") : search
  const { getApprovalVersion } = useNavigationGuard()
  const values: Record<string, string | string[]> = {}
  for (const key of params.keys()) {
    const all = params.getAll(key)
    values[key] = all.length === 1 ? all[0] : all
  }
  return { values, navigation: getApprovalVersion() }
}
