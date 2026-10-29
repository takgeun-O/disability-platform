'use client'
import { useCallback, useLayoutEffect, useMemo, useRef } from 'react'

/** Presentation only. Capture immediately before publishing an accepted server result. */
export function useCommentTransition() {
  const list = useRef<HTMLUListElement>(null)
  const previous = useRef<Map<string, number> | null>(null)
  const animations = useRef<Animation[]>([])
  const cancel = useCallback(() => {
    animations.current.forEach(animation => animation.cancel())
    animations.current = []
  }, [])
  const capture = useCallback(() => {
    const node = list.current
    if (!node || !node.getClientRects().length || matchMedia('(prefers-reduced-motion: reduce)').matches) {
      previous.current = null; cancel(); return
    }
    const top = node.getBoundingClientRect().top
    // Read the current visual position before cancelling an interrupted animation.
    previous.current = new Map([...node.children].map(child => [
      child.getAttribute('data-comment-item') ?? '', child.getBoundingClientRect().top - top,
    ]))
    cancel()
  }, [cancel])
  useLayoutEffect(() => {
    const before = previous.current; previous.current = null
    const node = list.current
    if (!before || !node || !node.getClientRects().length || matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const top = node.getBoundingClientRect().top
    for (const child of node.children) {
      const old = before.get(child.getAttribute('data-comment-item') ?? '')
      const delta = old === undefined ? 0 : old - (child.getBoundingClientRect().top - top)
      if (old !== undefined && Math.abs(delta) < 1) continue
      const animation = child.animate(old === undefined
        ? [{ opacity: 0 }, { opacity: 1 }]
        : [{ transform: `translateY(${delta}px)` }, { transform: 'translateY(0)' }],
      { duration: 240, easing: 'cubic-bezier(.2,.7,.2,1)' })
      animations.current.push(animation)
      animation.onfinish = () => { animations.current = animations.current.filter(a => a !== animation) }
    }
  })
  useLayoutEffect(() => {
    const motion = matchMedia('(prefers-reduced-motion: reduce)')
    const change = () => { if (motion.matches) { previous.current = null; cancel() } }
    motion.addEventListener('change', change)
    return () => { motion.removeEventListener('change', change); previous.current = null; cancel() }
  }, [cancel])
  return useMemo(() => ({ list, capture }), [capture])
}
