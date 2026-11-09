import type { MouseEvent, KeyboardEvent } from "react"

// Select rows without taking over links,
// date controls, modified clicks or selecting text for copying.
export function isAdminRowSelection(event: MouseEvent<HTMLTableRowElement>) {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false
  const target = event.target
  if (!(target instanceof Element) || target.closest('a,button,input,select,textarea,label,[role="button"],[contenteditable="true"]')) return false
  return !window.getSelection()?.toString()
}

export function isAdminRowKeyboardSelection(event: KeyboardEvent<HTMLTableRowElement>) {
  if (event.target !== event.currentTarget || event.defaultPrevented || event.repeat || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return false
  if (event.key !== "Enter" && event.key !== " ") return false
  event.preventDefault()
  return true
}
