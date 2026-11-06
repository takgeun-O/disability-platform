'use client'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import CommentSuccessToast from '@/components/community/CommentSuccessToast'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { changeComment, validateComment, requiredContent, CommentError } from '@/lib/community-comments'
import { characterCount } from '@/lib/community-posts'
import { postCommentsChanged, postListChanged } from '@/lib/community-mutations'
import { useInlineCommentAuthNotice } from './InlineCommentAuthNotice'

export default function InlineCommentForm({ postId, memberId, draft, onDraft, onSuccess }: {
  postId: number; memberId: number; draft: string; onDraft: (value: string) => void; onSuccess: (text: string) => void
}) {
  const auth = useAuth(), scope = useNavigationRequest(), id = useId()
  const onAuthExpired = useInlineCommentAuthNotice()
  const [busy, setBusy] = useState(false), [field, setField] = useState(''), [error, setError] = useState('')
  const [notice, setNotice] = useState(0)
  const noticeSequence = useRef(0)
  const flight = useRef<object | null>(null), mounted = useRef(false)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (flight.current || auth.pending || auth.memberRequests.paused || !scope.isActive()) return
    const session = auth.captureMemberRequest(memberId)
    if (!session.isCurrent()) return
    const invalid = validateComment(draft)
    setField(invalid); setError('')
    if (invalid) {
      if (invalid === requiredContent) setNotice(++noticeSequence.current)
      return
    }
    setNotice(0)
    const operation = {}; flight.current = operation; setBusy(true)
    const receipt = auth.commentCreated.capture(memberId, postId)
    const text = draft, navigation = scope.begin()
    const current = () => mounted.current && flight.current === operation && navigation() && session.isCurrent()
    try {
      const result = await changeComment(String(postId), 'POST', null, text, current)
      if (result === null) return
      if (session.isCurrent()) { postCommentsChanged(String(postId)); postListChanged() }
      if (!current()) return
      receipt.record(result, text)
      onDraft(''); onSuccess(text)
    } catch (cause) {
      const failure = cause instanceof CommentError ? cause : new CommentError(0, 'UNKNOWN', '결과를 확인하지 못했습니다. 댓글 새로고침으로 확인해 주세요.', '', true)
      if (session.isCurrent() && (failure.uncertain || failure.code === 'POST_NOT_FOUND')) {
        postCommentsChanged(String(postId)); postListChanged()
      }
      if (session.isCurrent() && failure.status === 404 && failure.code === 'POST_NOT_FOUND') { auth.commentDrafts.discardDeleted(memberId, postId); auth.commentCreated.discardDeleted(memberId, postId) }
      if (!current()) return
      if (failure.status === 401 && failure.code === 'AUTHENTICATION_REQUIRED' && onAuthExpired) {
        if (session.expire('expired', { notice: false })) onAuthExpired()
        return
      }
      setField(failure.field); setError(failure.field ? '' : failure.message)
      if (failure.status === 401) session.expire()
    } finally {
      if (flight.current === operation) { flight.current = null; if (mounted.current && scope.isActive()) setBusy(false) }
    }
  }
  const inlineField = field && field !== requiredContent ? field : ''
  return <form className="mypage-comment-preview" onSubmit={submit} aria-busy={busy}>
    <div role="status" aria-atomic="true">
      {notice > 0 && <CommentSuccessToast key={notice} variant="notice" message="내용을 입력해 주세요." onDismiss={() => setNotice(current => current === notice ? 0 : current)} />}
    </div>
    <label className="sr-only" htmlFor={id}>댓글 작성</label>
    <div className="mypage-comment-preview-field">
      <textarea id={id} rows={2} value={draft} disabled={busy || !!auth.pending || auth.memberRequests.paused}
        placeholder="댓글로 이야기를 나눠보세요" aria-invalid={!!field} aria-describedby={`${id}-count${inlineField ? ` ${id}-error` : ''}`
        } onChange={e => { onDraft(e.target.value); setNotice(0); if (field) setField(validateComment(e.target.value)) }} />
      <button type="submit" disabled={busy || !!auth.pending || auth.memberRequests.paused}>{busy ? '등록 중…' : '등록'}</button>
    </div>
    <p id={`${id}-count`}>{characterCount(draft).toLocaleString('ko-KR')} / 2,000자</p>
    {inlineField && <p id={`${id}-error`} role="alert" className="community-error">{inlineField}</p>}
    {error && <p role="alert" className="community-error">{error}</p>}
  </form>
}
