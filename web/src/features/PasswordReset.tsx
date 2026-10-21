"use client";

import { useActionState, useRef, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import Link from '@/components/AppLink';
import { ResetLinkInvalid } from '@/components/auth/ResetLinkStatus';
import AuthFrame from '@/components/auth/AuthFrame';
import { FieldError, FieldLabel, PasswordInput } from '@/components/auth/AuthFields';
import { useAuth } from '@/components/AuthProvider';
import { useNavigationRequest } from '@/components/NavigationGuard';
import { getAuthCsrf } from '@/lib/auth-api';
import { completePasswordReset, requestPasswordReset, resetErrorState } from '@/lib/password-reset-api';
import { RESET_ACCEPTED, RESET_INITIAL, validateResetEmail, validateResetPassword, type ResetState } from '@/lib/password-reset';
import { completeResetAction, requestResetAction } from '@/app/password-reset-actions';

export default function PasswordReset({ mode, token }: { mode: 'request' | 'complete'; token?: string | null }) {
  const [serverState, action, serverPending] = useActionState(mode === 'request' ? requestResetAction : completeResetAction, RESET_INITIAL);
  const [clientState, setClientState] = useState<ResetState | null>(null);
  const state = clientState ?? serverState;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [show, setShow] = useState(false), [showConfirm, setShowConfirm] = useState(false);
  const [pending, setPending] = useState(false);
  const busy = pending || serverPending, flight = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const auth = useAuth(), router = useRouter(), scope = useNavigationRequest();
  const invalid = mode === 'complete' && (!token || state.status === 'invalid-link');
  function feedback(next: ResetState) {
    setClientState(next);
    const field = Object.keys(next.fields)[0];
    // 입력 오류는 해당 필드로. 일반 오류·성공은 live region으로 안내한다.
    if (field) formRef.current?.querySelector<HTMLInputElement>(`[name="${field}"]`)?.focus({ preventScroll: true });
  }
  function clearField(name: keyof ResetState['fields']) {
    setClientState({ ...state, fields: { ...state.fields, [name]: undefined } });
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (flight.current || busy) return;
    const fields = mode === 'request' ? validateResetEmail(email) : validateResetPassword({ token: token ?? '', newPassword: password, newPasswordConfirm: confirm });
    if (Object.keys(fields).length) { feedback({ status: 'error', fields }); return; }
    if (mode === 'complete' && !token) return;
    flight.current = true; setPending(true); setClientState(RESET_INITIAL);
    try {
      const csrf = await getAuthCsrf();
      if (!scope.isActive()) return;
      if (mode === 'request') {
        await requestPasswordReset(email, csrf);
        if (scope.isActive()) { setEmail(''); feedback({ status: 'accepted', fields: {} }); }
      } else {
        await completePasswordReset({ token: token!, newPassword: password, newPasswordConfirm: confirm }, csrf);
        // 성공 후 인증 캐시도 서버로 재확인한다. 이전 화면에는 늦은 결과를 반영하지 않는다.
        void auth.refresh();
        if (scope.isActive()) {
          setPassword(''); setConfirm(''); setShow(false); setShowConfirm(false);
          router.replace('/reset-password/complete', { scroll: false });
        }
      }
    } catch (error) {
      if (scope.isActive()) {
        const next = resetErrorState(error);
        if (next.status === 'invalid-link') { setPassword(''); setConfirm(''); }
        feedback(next);
      }
    } finally { flight.current = false; if (scope.isActive()) setPending(false); }
  }
  if (invalid) return <ResetLinkInvalid />;
  return <AuthFrame title={mode === 'request' ? '비밀번호를 잊으셨나요?' : '새 비밀번호 설정'}
    description={mode === 'request' ? '가입할 때 사용한 이메일 주소를 입력해 주세요.' : '새로 사용할 비밀번호를 입력해 주세요.'}>
    {state.status === 'accepted' ? <div className="auth-feedback" role="status"><p>{RESET_ACCEPTED}</p><p>메일함과 스팸함을 확인해 주세요.</p><Link href="/forgot-password" className="iyum-text-link" onNavigate={event => { event.preventDefault(); setClientState(RESET_INITIAL); }}>이메일 다시 입력하기</Link></div>
      : <form ref={formRef} action={action} onSubmit={submit} noValidate aria-label={mode === 'request' ? '재설정 메일 요청 양식' : '새 비밀번호 설정 양식'} aria-busy={busy}>
        {mode === 'request' ? <div className="auth-field">
          <FieldLabel htmlFor="reset-email" text="이메일" />
          <input id="reset-email" className="auth-input" type="email" name="email" autoComplete="email" value={email}
            onChange={e => { setEmail(e.target.value); clearField('email'); }} readOnly={busy} aria-required="true"
            aria-invalid={!!state.fields.email} aria-describedby={state.fields.email ? 'reset-email-error' : undefined} />
          <FieldError id="reset-email-error" msg={state.fields.email ?? ''} />
        </div> : <>
          <input type="hidden" name="token" value={token ?? ''} />
          <div className="auth-field">
            <FieldLabel htmlFor="reset-password" text="새 비밀번호" />
            <PasswordInput id="reset-password" name="newPassword" value={password} onChange={v => { setPassword(v); clearField('newPassword'); }}
              show={show} onToggle={() => setShow(v => !v)} hasError={!!state.fields.newPassword}
              describedBy={`reset-password-hint${state.fields.newPassword ? ' reset-password-error' : ''}`} disabled={busy} />
            <p id="reset-password-hint" className="auth-hint" style={{ wordBreak: 'keep-all' }}>8자 이상 · 영문과 숫자 포함</p>
            <FieldError id="reset-password-error" msg={state.fields.newPassword ?? ''} />
          </div>
          <div className="auth-field">
            <FieldLabel htmlFor="reset-confirm" text="새 비밀번호 확인" />
            <PasswordInput id="reset-confirm" name="newPasswordConfirm" value={confirm} onChange={v => { setConfirm(v); clearField('newPasswordConfirm'); }}
              show={showConfirm} onToggle={() => setShowConfirm(v => !v)} hasError={!!state.fields.newPasswordConfirm}
              describedBy={state.fields.newPasswordConfirm ? 'reset-confirm-error' : undefined} disabled={busy} />
            <FieldError id="reset-confirm-error" msg={state.fields.newPasswordConfirm ?? ''} />
          </div>
        </>}
        {state.message && <div className="auth-feedback is-error" role="alert">{state.message}</div>}
        <p className="auth-status" role="status">{busy ? '요청을 처리하고 있습니다…' : ''}</p>
        <button type="submit" className="iyum-button iyum-button-primary auth-submit" disabled={busy}>
          {busy ? '처리 중…' : mode === 'request' ? '재설정 메일 요청' : '비밀번호 변경'}
        </button>
      </form>}
    <nav className="auth-links" aria-label="계정 관련 이동"><Link href="/login" className="iyum-text-link">로그인 화면으로 돌아가기</Link></nav>
  </AuthFrame>;
}
