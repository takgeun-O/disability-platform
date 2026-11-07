"use client"
import { useId } from "react"
import { ADMIN_CONTENT_STATUSES, ADMIN_STATUSES, normalizeAdminStatuses, type AdminContentStatus } from "@/lib/admin-posts"
export default function AdminStatusFilter({label, value, onChange}: {
  label: string; value: readonly AdminContentStatus[]; onChange: (value: AdminContentStatus[]) => void
}) {
  const id = useId()
  return <fieldset className="admin-status-filter" aria-label={label}>
    <legend>{label}<span className="admin-multiple-hint">복수 선택</span></legend>
    <div className="admin-status-options">{ADMIN_CONTENT_STATUSES.map(status => <label key={status} htmlFor={`${id}-${status}`}>
      <input id={`${id}-${status}`} type="checkbox" checked={value.includes(status)} onChange={event => onChange(normalizeAdminStatuses(event.target.checked ? [...value,status] : value.filter(v => v !== status)))}/>
      {ADMIN_STATUSES[status]}
    </label>)}</div>
  </fieldset>
}
