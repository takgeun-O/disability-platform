'use client'
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import CommunityDialog from '@/components/community/CommunityDialog'
import { writtenTime } from '@/lib/written-time'
import { changeComment, CommentError } from '@/lib/community-comments'
import { postListChanged, postCommentsChanged } from '@/lib/community-mutations'

// Delete by the private response IDs without attempting to retrieve redacted content.
export default function MemberHiddenCommentDelete({ memberId, postId, commentId, createdAt, active, onRemoved }: {
  memberId: number; postId: number; commentId: number; createdAt: string; active: boolean; onRemoved: (id: number, trigger: HTMLElement) => void
}) {
  const auth = useAuth(), scope = useNavigationRequest(), id = useId()
  const [menu, setMenu] = useState(false), [wasActive, setWasActive] = useState(active)
  const screen = useRef(0)
  const root = useRef<HTMLDivElement>(null), menuItem = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false)
  const [error, setError] = useState<CommentError | null>(null)
  const flight = useRef(false), visible = useRef(active), trigger = useRef<HTMLButtonElement>(null)
  // Keep the in-flight lock across tab switches, but retire the previous screen's UI effects.
  if (wasActive !== active) { setWasActive(active); setMenu(false); setOpen(false); setError(null) }
  useLayoutEffect(() => { screen.current++; visible.current = active; return () => { visible.current = false } }, [active])
  useEffect(() => {
    if (!menu) return
    menuItem.current?.focus({ preventScroll: true })
    const outside = (event: PointerEvent) => {
      if (!(event.target instanceof Node) || root.current?.contains(event.target)) return
      if (document.activeElement === menuItem.current) trigger.current?.focus({ preventScroll: true })
      setMenu(false)
    }
    document.addEventListener('pointerdown', outside)
    return () => document.removeEventListener('pointerdown', outside)
  }, [menu])
  const owner = auth.status === 'authenticated' && !auth.pending && auth.member?.memberId === memberId
  const refresh = () => { postCommentsChanged(String(postId)); postListChanged() }
  function close() { if (!flight.current) { setOpen(false); setError(null) } }
  async function remove() {
    if (!owner || !visible.current || flight.current) return
    const session = auth.captureMemberRequest(memberId), navigation = scope.begin(), startedScreen = screen.current
    const current = () => session.isCurrent() && navigation() && visible.current && screen.current === startedScreen
    if (!current()) return
    flight.current = true; setBusy(true); setError(null)
    try {
      const result = await changeComment(String(postId), 'DELETE', commentId, '', current)
      if (result === null || !session.isCurrent()) return
      if (current()) {
        setOpen(false)
        if (trigger.current) onRemoved(commentId, trigger.current)
      }
      refresh()
    } catch (cause) {
      if (!session.isCurrent()) return
      const failure = cause instanceof CommentError ? cause : new CommentError(0, 'UNEXPECTED_RESPONSE', '삭제 결과를 확인하지 못했습니다. 목록을 다시 확인해 주세요.', '', true)
      if (failure.uncertain || failure.code === 'COMMENT_NOT_FOUND' || failure.code === 'POST_NOT_FOUND') refresh()
      if (!current()) return
      if (failure.status === 401) { session.expire(); return }
      setError(failure)
    } finally {
      flight.current = false
      if (navigation() && visible.current) setBusy(false)
    }
  }
  if (!owner) return null
  return <div ref={root} className="mypage-post-actions mypage-hidden-comment-actions" onBlur={event => {
    if (menu && !event.currentTarget.contains(event.relatedTarget)) setMenu(false)
  }} onKeyDown={event => {
    if (!menu) return
    if (event.key === 'Escape') { event.preventDefault(); setMenu(false); trigger.current?.focus({ preventScroll: true }) }
    if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) { event.preventDefault(); menuItem.current?.focus({ preventScroll: true }) }
  }}>
    <button ref={trigger} type="button" className="mypage-comment-delete" aria-label="숨김 댓글 관리" aria-haspopup="menu" aria-expanded={menu} aria-controls={`${id}-menu`}
      onClick={() => setMenu(!menu)} onKeyDown={event => {
        if (!menu && ['ArrowDown', 'ArrowUp'].includes(event.key)) { event.preventDefault(); setMenu(true) }
      }}>⋯</button>
    {menu && <div id={`${id}-menu`} role="menu" aria-label="숨김 댓글 관리 메뉴" className="mypage-post-menu">
      <button ref={menuItem} type="button" role="menuitem" onClick={() => { trigger.current?.focus({ preventScroll: true }); setMenu(false); setError(null); setOpen(true) }}>댓글 삭제</button>
    </div>}
    {open && active && <CommunityDialog titleId={`${id}-title`} descriptionId={`${id}-description`} onCancel={close}>
      <h2 id={`${id}-title`}>댓글을 삭제할까요?</h2>
      <p id={`${id}-description`}>내용을 확인할 수 없는 댓글입니다. 삭제하면 되돌릴 수 없습니다.</p>
      <p className="mypage-delete-time">작성 시각: <time dateTime={createdAt}>{writtenTime(createdAt).exact}</time></p>
      {error && <p role="alert">{error.message}</p>}
      <div className="community-dialog-actions">
        <button type="button" data-initial-focus className="iyum-button iyum-button-outline" aria-disabled={busy} onClick={close}>취소</button>
        {error?.uncertain || error?.status === 404 ? <button type="button" className="iyum-button iyum-button-outline" onClick={() => { close(); refresh() }}>목록 다시 확인</button> : <button type="button" className="iyum-button community-danger" disabled={busy || !owner} onClick={() => void remove()}>{busy ? '삭제 중…' : '댓글 삭제하기'}</button>}
      </div><p role="status">{busy ? '삭제 요청을 처리하고 있습니다.' : ''}</p>
    </CommunityDialog>}
  </div>
}
