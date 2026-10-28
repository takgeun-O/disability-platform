"use client"
// 데이터/인증을 저장하지 않는다. 이 탭에서 변경한 글과 목록만 재조회한다.
import { useSyncExternalStore } from "react"
let identityVersion = 0
export function publicIdentityChanged() { identityVersion++; listeners.forEach(listener => listener()) }
const versions = new Map<string, number>()
const listeners = new Set<() => void>()
export function postChanged(id: string) {
  for (const key of [id, `comments:${id}`, "list", "member-comments"])
    versions.set(key, (versions.get(key) ?? 0) + 1)
  listeners.forEach((listener) => listener())
}
// 댓글 변경은 상세 버전을 건드리지 않는다. 편집 중인 폼/초점을 유지한다.
export function postListChanged() {
  for (const key of ["list", "member-comments"]) versions.set(key, (versions.get(key) ?? 0) + 1)
  listeners.forEach((listener) => listener())
}
// Content-only comment edits invalidate the private comments list, not counts or detail forms.
export function memberCommentsChanged() {
  versions.set("member-comments", (versions.get("member-comments") ?? 0) + 1)
  listeners.forEach((listener) => listener())
}
const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
export function usePostVersion(key: string) {
  return useSyncExternalStore(
    subscribe,
    () => getPostVersion(key),
    () => 0,
  )
}

export const getPostVersion = (key: string) => (versions.get(key) ?? 0) + identityVersion

// Comment content/membership or an uncertain write needs a post-local reread.
// This is deliberately separate from the detail version and private activity list.
export function postCommentsChanged(postId: string) {
  const key = `comments:${postId}`
  versions.set(key, (versions.get(key) ?? 0) + 1)
  listeners.forEach(listener => listener())
}
