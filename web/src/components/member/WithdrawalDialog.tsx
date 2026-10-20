'use client'

import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import OverlayDialog from '@/components/OverlayDialog'
import { useAuth } from '@/components/AuthProvider'
import { useNavigationRequest } from '@/components/NavigationGuard'
import { FieldError, PasswordInput } from '@/components/auth/AuthFields'
import { getMemberCsrf, withdrawMember, WithdrawalError } from '@/lib/member-api'
import { validateCurrentPassword } from '@/lib/login-form'
import { publicIdentityChanged } from '@/lib/community-mutations'

export default function WithdrawalDialog({ memberId, onClose }: { memberId: number; onClose: () => void }) {
  const auth = useAuth(), scope = useNavigationRequest(), router = useRouter()
  const [password, setPassword] = useState(''), [confirmed, setConfirmed] = useState(false), [show, setShow] = useState(false)
  const [saving, setSaving] = useState(false), busy = useRef(false)
  const [fields, setFields] = useState<WithdrawalError['fields']>({}), [error, setError] = useState('')
  const input = useRef<HTMLInputElement>(null), consent = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (!saving && scope.isActive()) {
      if (fields.currentPassword) input.current?.focus({ preventScroll: true })
      else if (fields.confirmed) consent.current?.focus({ preventScroll: true })
    }
  }, [fields, saving, scope])
  function close() {
    if (busy.current) return
    setPassword(''); setConfirmed(false); setShow(false); onClose()
  }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy.current || !scope.isActive()) return
    const passwordError = validateCurrentPassword(password)
    if (passwordError || !confirmed) {
      setFields({ currentPassword: passwordError, confirmed: confirmed ? '' : '탈퇴 안내를 확인하고 동의해 주세요.' })
      ;(passwordError ? input : consent).current?.focus({ preventScroll: true }); return
    }
    busy.current = true; setSaving(true); setFields({}); setError('')
    const valid = scope.begin()
    try {
      const csrf = await getMemberCsrf()
      if (!valid()) return
      await withdrawMember(password, csrf)
      // Success is a committed server action; never retry it or infer success from a transport error.
      if (!valid()) return
      setPassword(''); setConfirmed(false); setShow(false)
      if (auth.completeWithdrawal(memberId)) {
        publicIdentityChanged()
        router.replace('/')
        router.refresh()
      }
    } catch (e) {
      if (!valid()) return
      if (e && typeof e === 'object' && 'status' in e && e.status === 401) {
        setPassword(''); setConfirmed(false); auth.expire(memberId); return
      }
      if (e instanceof WithdrawalError && Object.keys(e.fields).length) {
        setFields(e.fields)
      } else setError(e instanceof Error ? e.message : '탈퇴 결과를 확인하지 못했습니다. 로그인 상태를 확인해 주세요.')
    } finally { busy.current = false; if (valid()) setSaving(false) }
  }
  const validation = password ? validateCurrentPassword(password) : ''
  return <OverlayDialog titleId="withdrawal-title" descriptionId="withdrawal-description" className="mypage-edit-dialog withdrawal-dialog" initialFocus="#withdrawal-close" returnFocus="#withdrawal-entry" closeOnBackdropClick={false} onClose={close}>
    <button id="withdrawal-close" type="button" className="iyum-overlay-close withdrawal-close" aria-label="회원 탈퇴 닫기" aria-disabled={saving} onClick={close}>×</button>
    <header className="withdrawal-heading"><svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round"><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2Z"/></svg><h2 id="withdrawal-title">회원 탈퇴</h2><p id="withdrawal-description">탈퇴 전 아래 내용을 확인해 주세요.</p></header>
    <ul className="withdrawal-guidance"><li>이메일·닉네임·비밀번호 정보가 삭제되며, 계정은 복구할 수 없어요.</li><li>작성한 게시글과 댓글은 남아 있어요.</li><li>작성자 이름은 ‘탈퇴한 회원’으로 표시돼요.</li></ul>
    <p className="withdrawal-help">삭제할 글과 댓글이 있다면 탈퇴 전에 정리해 주세요.</p>
    <form onSubmit={submit} noValidate aria-busy={saving}>
      <label className="auth-label" htmlFor="withdrawal-password">현재 비밀번호</label>
      <PasswordInput toggleAppearance="icon" id="withdrawal-password" value={password} onChange={value => { if (!busy.current) { setPassword(value); setFields(current => ({ ...current, currentPassword: '' })); setError('') } }} show={show} onToggle={() => { if (!busy.current) setShow(value => !value) }} disabled={saving} autoComplete="current-password" placeholder="현재 비밀번호를 입력해 주세요" inputRef={input} hasError={!!fields.currentPassword || !!validation} describedBy={fields.currentPassword || validation ? 'withdrawal-password-error' : undefined} />
      <FieldError id="withdrawal-password-error" msg={fields.currentPassword || validation} />
      <label className="withdrawal-consent"><input ref={consent} type="checkbox" checked={confirmed} disabled={saving} onChange={event => { setConfirmed(event.target.checked); setFields(current => ({ ...current, confirmed: '' })) }} aria-invalid={!!fields.confirmed} aria-describedby={fields.confirmed ? 'withdrawal-consent-error' : undefined} /><span>탈퇴 안내를 확인했으며, 회원 탈퇴에 동의합니다.</span></label>
      <FieldError id="withdrawal-consent-error" msg={fields.confirmed ?? ''} />
      {error && <div role="alert" className="auth-field-error"><p>{error}</p><button type="button" className="iyum-text-link" disabled={saving} onClick={() => { setPassword(''); void auth.refresh() }}>로그인 상태 다시 확인</button></div>}
      <div className="withdrawal-actions"><button type="button" className="iyum-button iyum-button-outline" disabled={saving} onClick={close}>취소</button><button className="iyum-button withdrawal-submit" disabled={saving || !confirmed || !!validateCurrentPassword(password)}>{saving ? '탈퇴 처리 중…' : '회원 탈퇴'}</button></div>
      {saving && <p role="status">탈퇴 처리 중입니다. 잠시 기다려 주세요.</p>}
    </form>
  </OverlayDialog>
}
