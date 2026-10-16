"use client"
// 데이터/인증을 저장하지 않는다. 이 탭에서 변경한 글과 목록만 재조회한다.
import { useSyncExternalStore } from "react"
const versions = new Map<string, number>()
const listeners = new Set<() => void>()
export function postChanged(id: string) {
  for (const key of [id, "list"])
    versions.set(key, (versions.get(key) ?? 0) + 1)
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
    () => versions.get(key) ?? 0,
    () => 0,
  )
}
