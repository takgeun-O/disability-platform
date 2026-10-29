'use client'
import { useEffect, useLayoutEffect, useId, useRef, useState } from 'react'
import Link from '@/components/AppLink'
import CommunityDialog from '@/components/community/CommunityDialog'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { changePost, CommunityApiError, type PostSummary } from '@/lib/community-api'
import { postChanged } from '@/lib/community-mutations'

// List controls use the same write client/dialog as detail. Session ownership fences
// cache invalidation; navigation/active tab additionally fence screen effects.
export default function MemberPostActions({ post, open, onOpen, onLeaving, onRemoved, active }: {
  post: PostSummary; open: boolean; onOpen: (id: number | null) => void; onLeaving: () => void
  onRemoved: (id: number, trigger: HTMLElement) => void; active: boolean
}) {
  const auth = useAuth(), scope = useNavigationRequest(), uid = useId()
  const root = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null)
  const [dialog, setDialog] = useState(false), [busy, setBusy] = useState(false)
  const [error, setError] = useState<CommunityApiError | null>(null), flight = useRef(false)
  const visible = useRef(active)
  useLayoutEffect(() => { visible.current = active; return () => { visible.current = false } }, [active])
  const owner = auth.status === 'authenticated' && !auth.pending && auth.member?.memberId === post.author.memberId
  function closeMenu(focus = false) { onOpen(null); if (focus) trigger.current?.focus({ preventScroll: true }) }
  useEffect(() => {
    if (!open) return
    const outside = (e: PointerEvent) => {
      if (root.current?.contains(e.target as Node)) return
      if (!(e.target as Element).closest('a,button,input,textarea,select,[tabindex]')) trigger.current?.focus({ preventScroll: true })
      onOpen(null)
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [open, onOpen])
  function close() { if (!flight.current) { setDialog(false); setError(null) } }
  async function remove() {
    if (!owner || flight.current || !active) return
    const session = auth.captureMemberRequest(post.author.memberId), navigation = scope.begin()
    if (!session.isCurrent()) return
    const current = () => session.isCurrent() && navigation() && visible.current
    flight.current = true; setBusy(true); setError(null)
    try {
      const done = await changePost(String(post.id), 'DELETE', undefined, current)
      if (!done || !session.isCurrent()) return
      auth.commentDrafts.discardDeleted(post.author.memberId, post.id); auth.commentCreated.discardDeleted(post.author.memberId, post.id)
      if (current()) { setDialog(false); if (trigger.current) onRemoved(post.id, trigger.current) }
      postChanged(String(post.id))
    } catch (cause) {
      if (!session.isCurrent()) return
      const failure = cause instanceof CommunityApiError ? cause : new CommunityApiError(0, 'UNEXPECTED_RESPONSE', '삭제 결과를 확인하지 못했습니다.', {}, true)
      if (failure.status === 404 && failure.code === 'POST_NOT_FOUND') { auth.commentDrafts.discardDeleted(post.author.memberId, post.id); auth.commentCreated.discardDeleted(post.author.memberId, post.id) }
      if (failure.uncertain || failure.code === 'POST_NOT_FOUND') postChanged(String(post.id))
      if (!current()) return
      if (failure.status === 401) { session.expire(); return }
      setError(failure)
    } finally {
      flight.current = false
      if (current()) setBusy(false)
    }
  }
  if (!owner) return null
  return <div ref={root} className="mypage-post-actions" onBlur={e => {
    if (open && !e.currentTarget.contains(e.relatedTarget as Node)) onOpen(null)
  }} onKeyDown={e => {
    if (!open) return
    if (e.key === 'Escape') { e.preventDefault(); closeMenu(true) }
    if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) {
      e.preventDefault()
      const controls = [...(root.current?.querySelectorAll<HTMLElement>('[role=menuitem]') ?? [])]
      const index = controls.indexOf(document.activeElement as HTMLElement)
      controls[e.key === 'Home' ? 0 : e.key === 'End' ? controls.length-1 : (index + (e.key === 'ArrowUp' ? -1 : 1) + controls.length) % controls.length]?.focus()
    }
  }}>
    <button ref={trigger} type="button" aria-label="게시글 관리" aria-haspopup="menu" aria-expanded={open} aria-controls={`${uid}-menu`} onClick={() => onOpen(open ? null : post.id)} onKeyDown={e => {
      if (!open && ['ArrowDown','ArrowUp'].includes(e.key)) { e.preventDefault(); onOpen(post.id) }
    }}>⋯</button>
    {open && <ManagementMenu id={`${uid}-menu`}>
      <Link role="menuitem" href={`/community/posts/${post.id}/edit?from=%2Fmypage&activity=1`} prefetch={false} onNavigate={() => { onLeaving(); onOpen(null) }}>수정</Link>
      <button role="menuitem" type="button" onClick={() => { closeMenu(true); setError(null); setDialog(true) }}>삭제</button>
    </ManagementMenu>}
    {dialog && <CommunityDialog titleId={`${uid}-title`} descriptionId={`${uid}-description`} onCancel={close}>
      <h2 id={`${uid}-title`}>게시글을 삭제할까요?</h2>
      <p className="mypage-delete-title">{post.title}</p>
      <p id={`${uid}-description`}>삭제하면 목록과 상세에서 더 이상 볼 수 없습니다. 삭제 요청 중에는 이 창을 닫을 수 없습니다.</p>
      {error && <div role="alert" className="community-error"><p>{error.message}</p>{error.uncertain && <Link href={`/community/posts/${post.id}?from=%2Fmypage`} target="_blank" rel="noopener noreferrer">새 탭에서 게시글 확인</Link>}</div>}
      <div className="community-dialog-actions"><button type="button" data-initial-focus className="iyum-button iyum-button-outline" aria-disabled={busy} onClick={close}>취소</button>
        {error?.uncertain || error?.code === 'POST_NOT_FOUND' ? <button type="button" className="iyum-button iyum-button-outline" onClick={() => { close(); postChanged(String(post.id)) }}>목록 다시 확인</button> : <button type="button" className="iyum-button community-danger" disabled={busy || !owner || error?.status === 404} onClick={() => void remove()}>{busy ? '삭제 중…' : '삭제하기'}</button>}
      </div><p role="status">{busy ? '삭제 요청을 처리하고 있습니다.' : ''}</p>
    </CommunityDialog>}
  </div>
}
function ManagementMenu({ id, children }: { id: string; children: React.ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => { ref.current?.querySelector<HTMLElement>('[role=menuitem]')?.focus({ preventScroll: true }) }, [])
  return <div ref={ref} id={id} role="menu" aria-label="게시글 관리 메뉴" className="mypage-post-menu">{children}</div>
}
