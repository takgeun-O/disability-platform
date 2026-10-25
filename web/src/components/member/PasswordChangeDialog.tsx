'use client';

import { useEffect, useRef, useState, type FormEvent } from 'react';
import OverlayDialog from '@/components/OverlayDialog';
import CommunityDialog from '@/components/community/CommunityDialog';
import '@/styles/community.css';
import { useAuth } from '@/components/AuthProvider';
import { useNavigationRequest, useUnsavedChanges } from '@/components/NavigationGuard';
import { FieldError, FieldLabel, PasswordInput } from '@/components/auth/AuthFields';
import { getAuthCsrf, AuthApiError } from '@/lib/auth-api';
import { changePassword, PasswordChangeError, PASSWORD_CHANGE_UNCERTAIN } from '@/lib/password-change-api';
import { PASSWORD_CHANGE_FIELDS, remainingSeconds, validatePasswordChange, type PasswordChangeField, type PasswordChangeFields, type PasswordChangeValues } from '@/lib/password-change';

const empty = (): PasswordChangeValues => ({ currentPassword: '', newPassword: '', newPasswordConfirm: '' });
const labels = { currentPassword: '현재 비밀번호', newPassword: '새 비밀번호', newPasswordConfirm: '새 비밀번호 확인' };
const id = (field: PasswordChangeField) => `change-${field}`;
export default function PasswordChangeDialog({ memberId, onClose, onNotice }: { memberId: number; onClose: (completed: boolean) => void; onNotice: (message: string) => void }) {
  const auth = useAuth(), scope = useNavigationRequest();
  const [values, setValues] = useState(empty), [shown, setShown] = useState<Partial<Record<PasswordChangeField, boolean>>>({});
  const [fields, setFields] = useState<PasswordChangeFields>({}), [error, setError] = useState('');
  const pendingErrorFocus = useRef<PasswordChangeField | null>(null);
  const [saving, setSaving] = useState(false), busy = useRef(false);
  const [completed, setCompleted] = useState(false), [preparation, setPreparation] = useState('');
  const [retryAt, setRetryAt] = useState<number | null>(null), [remaining, setRemaining] = useState(0);
  const blocker = useUnsavedChanges(saving || Object.values(values).some(Boolean));
  useEffect(() => {
    if (retryAt === null) return;
    const timer = window.setInterval(() => setRemaining(remainingSeconds(retryAt)), 250);
    return () => window.clearInterval(timer);
  }, [retryAt]);
  useEffect(() => {
    if (saving) return;
    const field = pendingErrorFocus.current;
    pendingErrorFocus.current = null;
    if (field && scope.isActive()) document.getElementById(id(field))?.focus({ preventScroll: true });
  }, [fields, saving, scope]);
  function close() { if (!busy.current) onClose(completed); }
  function reportFieldErrors(errors: PasswordChangeFields) {
    // Focus once per validation result, never when typing clears an existing error.
    pendingErrorFocus.current = PASSWORD_CHANGE_FIELDS.find(field => errors[field]) ?? null;
    setFields(errors);
  }
  function clear() { pendingErrorFocus.current = null; setValues(empty()); setShown({}); setFields({}); }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy.current || completed || !scope.isActive() || (retryAt !== null && remainingSeconds(retryAt) > 0)) return;
    const errors = validatePasswordChange(values); reportFieldErrors(errors); setError('');
    if (Object.keys(errors).length) return;
    const session = auth.beginCredentialChange(memberId);
    if (!session) return;
    busy.current = true; setSaving(true); setPreparation('');
    const navigationCurrent = scope.begin();
    const valid = () => navigationCurrent() && session.isCurrent();
    try {
      const csrf = await getAuthCsrf();
      if (!valid()) return;
      const result = await changePassword(values, csrf);
      if (!valid()) return;
      // A validated 200 is final. Clear secrets BEFORE any follow-up GET.
      clear(); setCompleted(true);
      if (result.reloginRequired) {
        onNotice('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요.');
        session.expire(); // generation-bound; never send a logout POST
        return;
      }
      try { await getAuthCsrf(); }
      catch {
        if (valid()) setPreparation('비밀번호 변경은 완료되었습니다. 다음 요청을 위한 보안 정보를 가져오지 못했습니다. 다음 작업에서 다시 확인합니다.');
      }
    } catch (e) {
      if (!valid()) return;
      if ((e instanceof PasswordChangeError || e instanceof AuthApiError) && e.status === 401) {
        clear(); onNotice('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.'); session.expire(); return;
      }
      if (e instanceof PasswordChangeError) {
        reportFieldErrors(e.fields); setError(e.message); setRetryAt(e.retryAt); setRemaining(e.retryAt === null ? 0 : remainingSeconds(e.retryAt));
        if (e.status === 403 && e.code === 'CSRF_TOKEN_INVALID') {
          try { await getAuthCsrf(); }
          catch { if (valid()) setPreparation('보안 정보를 가져오지 못했습니다. 연결을 확인해 주세요. 다시 제출할 때 새로 조회합니다.'); }
        }
      } else setError(e instanceof AuthApiError ? e.message : PASSWORD_CHANGE_UNCERTAIN);
    } finally {
      // Releasing the read barrier must also happen after an approved departure.
      // The operation owns its auth generation; finish cannot touch a newer login.
      session.finish();
      busy.current = false; if (valid()) setSaving(false);
    }
  }
  return <>
    <OverlayDialog titleId="password-change-title" descriptionId="password-change-policy" className="mypage-edit-dialog password-change-dialog" initialFocus="#password-change-close" returnFocus="#password-change-entry" closeOnBackdropClick={false} onClose={close}>
      <div className="mypage-edit-heading"><h2 id="password-change-title">비밀번호 변경</h2><button id="password-change-close" type="button" className="iyum-overlay-close" aria-label="비밀번호 변경 닫기" aria-disabled={saving} onClick={close}>×</button></div>
      {completed && <p role="status" className="password-change-success"><strong>비밀번호가 변경되었습니다.</strong>{' '}<span>현재 로그인은 유지됩니다.</span></p>}
      <p id="password-change-policy" className={completed ? 'password-change-help' : undefined}>{completed ? '다른 로그인 세션은 다음 요청부터 사용할 수 없습니다.' : '변경 후 현재 로그인은 유지되며, 다른 로그인 세션은 다음 요청부터 사용할 수 없습니다.'}</p>
      <form onSubmit={submit} noValidate aria-busy={saving}>
        {!completed && <>
          <p id="password-change-rule" className="password-change-help">새 비밀번호: 8자 이상 · 영문과 숫자 포함 · UTF-8 기준 72바이트 이하</p>
          {PASSWORD_CHANGE_FIELDS.map(field => <div className="password-change-field" key={field}>
            <FieldLabel htmlFor={id(field)} text={labels[field]} />
            <PasswordInput id={id(field)} name={field} value={values[field]} autoComplete={field === 'currentPassword' ? 'current-password' : 'new-password'} disabled={saving} toggleAppearance="icon"
              show={!!shown[field]} onToggle={() => { if (!busy.current) setShown(current => ({ ...current, [field]: !current[field] })); }}
              onChange={value => { if (!busy.current) { setValues(current => ({ ...current, [field]: value })); setFields(current => ({ ...current, [field]: '' })); setError(''); blocker.markDirty(); } }}
              hasError={!!fields[field]} describedBy={`${field !== 'currentPassword' ? 'password-change-rule ' : ''}${fields[field] ? id(field) + '-error' : ''}`.trim() || undefined} />
            <FieldError id={id(field) + '-error'} msg={fields[field] ?? ''} />
          </div>)}
        </>}
        {error && <p role="alert" className="auth-field-error">{error}</p>}
        {preparation && <p role="alert" className="password-change-help">{preparation}</p>}
        {remaining > 0 && <p role="status">{remaining}초 후 다시 시도할 수 있습니다.</p>}
        <div className="password-change-actions"><button type="button" className="iyum-button iyum-button-outline" disabled={saving} onClick={close}>{completed ? '닫기' : '취소'}</button>{!completed && <button className="iyum-button iyum-button-primary" disabled={saving || remaining > 0}>{saving ? '변경 중…' : '비밀번호 변경'}</button>}</div>
        {saving && <p role="status">{completed ? '다음 요청을 위한 보안 정보를 확인하고 있습니다.' : '비밀번호를 변경하고 있습니다.'}</p>}
      </form>
    </OverlayDialog>
    {blocker.state === 'blocked' && <CommunityDialog titleId="password-change-leave-title" onCancel={blocker.reset}>
      <h2 id="password-change-leave-title">비밀번호 변경 화면을 나갈까요?</h2><p>입력한 내용은 저장되지 않습니다.{saving ? ' 이미 보낸 변경 요청은 처리되었을 수 있습니다.' : ''}</p>
      <div className="community-dialog-actions"><button className="iyum-button iyum-button-outline" onClick={blocker.proceed}>나가기</button><button data-initial-focus className="iyum-button iyum-button-primary" onClick={blocker.reset}>계속 입력</button></div>
    </CommunityDialog>}
  </>;
}
