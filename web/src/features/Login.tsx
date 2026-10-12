"use client";

import { useEffect, useState, useId, useRef, type FormEvent } from 'react';
import Link from '@/components/AppLink';
import { useRouter } from 'next/navigation';
import { safeReturnPath } from '@/lib/navigation';
import { useQueryParams } from '@/lib/useQueryParams';
import { useAuth } from '@/components/AuthProvider';
import { useNavigationGuard } from '@/components/NavigationGuard';
import { AuthApiError, getAuthCsrf } from '@/lib/auth-api';
import { validateLoginForm } from '@/lib/login-form';

type Feedback = { kind: 'error' | 'success'; message: string; needsVerification?: boolean; canContinue?: boolean };

function inputStyle(hasError: boolean, extra: React.CSSProperties = {}): React.CSSProperties {
  const c = hasError ? '#B91C1C' : '#1A1918'
  return {
    width: '100%',
    boxSizing: 'border-box',
    borderTop: `1.5px solid ${c}`,
    borderBottom: `1.5px solid ${c}`,
    borderLeft: `1.5px solid ${c}`,
    borderRight: `1.5px solid ${c}`,
    borderRadius: 2,
    padding: '10px 12px',
    fontSize: 14,
    backgroundColor: '#FAFAF8',
    color: '#1A1918',
    outline: 'none',
    fontFamily: 'inherit',
    ...extra,
  }
}

function labelStyle(): React.CSSProperties {
  return { display: 'block', fontSize: 12, fontWeight: 600, color: '#4A4845', marginBottom: 6 }
}

function fieldErrorStyle(): React.CSSProperties {
  return { fontSize: 12, color: '#B91C1C', marginTop: 5, margin: 0 }
}

export default function Login() {
  const [searchParams] = useQueryParams();
  const router = useRouter();
  const auth = useAuth();
  const guard = useNavigationGuard();
  const uid = useId();
  const destination = safeReturnPath(searchParams.get('from') ?? '');
  const hasReturn = destination !== '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [pwError, setPwError] = useState('');
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [preparing, setPreparing] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const pwRef = useRef<HTMLInputElement>(null);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const inFlightRef = useRef(false);
  const mountedRef = useRef(false);
  const isProcessing = auth.pending !== null || preparing;

  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);
  useEffect(() => {
    if (emailError) emailRef.current?.focus();
    else if (pwError) pwRef.current?.focus();
    else feedbackRef.current?.focus();
  }, [emailError, pwError, feedback]);

  function navigateAfterLogin() {
    guard.request(() => router.replace(destination));
  }
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!mountedRef.current || inFlightRef.current || auth.pending) return;
    const values = { email, password }; // 비밀번호 원문으로 요청 객체를 만든다.
    const errors = validateLoginForm(values);
    setEmailError(errors.email ?? '');
    setPwError(errors.password ?? '');
    setFeedback(null);
    if (Object.keys(errors).length) {
      (errors.email ? emailRef : pwRef).current?.focus();
      return;
    }
    inFlightRef.current = true;
    try {
      const result = await auth.signIn(values, () => {
        if (!mountedRef.current) return;
        setPassword('');
        setShowPw(false);
        setFeedback({ kind: 'success', message: '로그인 상태를 확인했습니다. 보안 확인 정보를 갱신하고 있습니다.' });
      });
      if (!mountedRef.current || result.kind === 'ignored') return;
      if (result.csrfReady && !result.recovered) navigateAfterLogin();
      else setFeedback({ kind: 'success', canContinue: true,
        message: result.recovered
          ? '로그인 응답을 확인하지 못했지만 현재 로그인된 세션을 확인했습니다. 회원 정보를 확인한 뒤 계속해 주세요.'
          : '로그인은 완료되었습니다. 이후 요청을 위한 보안 확인 정보를 가져오지 못했습니다. 다시 확인하거나 계속할 수 있습니다.' });
    } catch (error) {
      if (!mountedRef.current) return;
      if (error instanceof AuthApiError) {
        setEmailError(error.fieldErrors.email ?? '');
        setPwError(error.fieldErrors.password ?? '');
        setFeedback({ kind: 'error', message: error.message, needsVerification: error.code === 'EMAIL_VERIFICATION_REQUIRED' });
      } else {
        setFeedback({ kind: 'error', message: '로그인 결과를 확인하지 못했습니다. 현재 로그인 상태를 다시 확인해 주세요.' });
      }
    } finally { inFlightRef.current = false; }
  }
  async function prepareAndContinue() {
    if (inFlightRef.current || isProcessing) return;
    inFlightRef.current = true;
    setPreparing(true);
    try {
      await getAuthCsrf();
      if (mountedRef.current) navigateAfterLogin();
    } catch {
      if (mountedRef.current) setFeedback({ kind: 'success', canContinue: true,
        message: '로그인 상태는 유지됩니다. 보안 확인 정보를 아직 가져오지 못했습니다. 연결을 확인하거나 계속해 주세요.' });
    } finally {
      inFlightRef.current = false;
      if (mountedRef.current) setPreparing(false);
    }
  }

  return (
    <main>
      <div style={{ maxWidth: 440, margin: '0 auto', padding: '60px 24px 80px' }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1A1918', letterSpacing: '-0.01em', marginBottom: hasReturn ? 10 : 36 }}>로그인</h1>
        {hasReturn && <p style={{ fontSize: 13, color: '#908D88', marginBottom: 32 }}>로그인 후 이전 페이지로 이동합니다.</p>}
        {feedback && <div ref={feedbackRef} tabIndex={-1} role={feedback.kind === 'error' ? 'alert' : 'status'}
          style={{ border: '1px solid #D0CEC9', borderRadius: 2, padding: '11px 14px', marginBottom: 24,
            fontSize: 13, lineHeight: 1.7, color: feedback.kind === 'error' ? '#B91C1C' : '#4A4845' }}>
          <p style={{ margin: 0 }}>{feedback.message}</p>
          {feedback.needsVerification && <Link href="/register/resend" style={{ textDecoration: 'underline' }}>인증 메일 다시 요청하기</Link>}
        </div>}
        {auth.status === 'authenticated' && auth.member ? (
          <div>
            <p style={{ fontSize: 14, marginBottom: 20 }}>{auth.member.nickname}님으로 로그인되어 있습니다.</p>
            <button type="button" disabled={isProcessing} onClick={navigateAfterLogin}
              style={{ padding: '12px 20px', background: '#1A1918', color: '#FAFAF8', borderRadius: 2, cursor: isProcessing ? 'wait' : 'pointer' }}>
              {isProcessing ? '보안 확인 중…' : hasReturn ? '이전 페이지로 계속' : '홈으로 이동'}
            </button>
            {feedback?.canContinue && <button type="button" disabled={isProcessing} onClick={() => { void prepareAndContinue(); }}
              style={{ display: 'block', marginTop: 16, fontSize: 13, textDecoration: 'underline', cursor: 'pointer' }}>보안 정보 다시 확인</button>}
          </div>
        ) : (
          <form onSubmit={handleSubmit} noValidate aria-label="로그인 양식" aria-busy={isProcessing}>
          {/* ── Email field ────────────────────────────────────────── */}
          <div style={{ marginBottom: 20 }}>
            <label htmlFor={`${uid}-email`} style={labelStyle()}>
              이메일{' '}
              <span aria-hidden="true" style={{ color: '#B91C1C', fontWeight: 400 }}>(필수)</span>
            </label>
            <input
              ref={emailRef}
              id={`${uid}-email`}
              type="email"
              name="email"
              autoComplete="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                if (emailError) setEmailError('')
              }}
              placeholder="이메일 주소"
              aria-required="true"
              aria-invalid={emailError ? true : undefined}
              aria-describedby={emailError ? `${uid}-email-err` : undefined}
              disabled={isProcessing}
              style={inputStyle(!!emailError)}
            />
            {emailError && (
              <p id={`${uid}-email-err`} role="alert" style={fieldErrorStyle()}>
                {emailError}
              </p>
            )}
          </div>

          {/* ── Password field ─────────────────────────────────────── */}
          <div style={{ marginBottom: 20 }}>
            <label htmlFor={`${uid}-pw`} style={labelStyle()}>
              비밀번호{' '}
              <span aria-hidden="true" style={{ color: '#B91C1C', fontWeight: 400 }}>(필수)</span>
            </label>
            <div style={{ position: 'relative' }}>
              <input
                ref={pwRef}
                id={`${uid}-pw`}
                type={showPw ? 'text' : 'password'}
                name="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value)
                  if (pwError) setPwError('')
                }}
                placeholder="비밀번호"
                aria-required="true"
                aria-invalid={pwError ? true : undefined}
                aria-describedby={pwError ? `${uid}-pw-err` : undefined}
                disabled={isProcessing}
                style={inputStyle(!!pwError, { paddingRight: 60 })}
              />
              <button
                type="button"
                onClick={() => setShowPw((v) => !v)}
                aria-label={showPw ? '비밀번호 숨기기' : '비밀번호 표시'}
                style={{
                  position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)',
                  background: 'none', border: 'none', cursor: 'pointer',
                  fontSize: 11, color: '#908D88', fontFamily: 'inherit', padding: '2px 4px', lineHeight: 1,
                }}
              >
                {showPw ? '숨기기' : '표시'}
              </button>
            </div>
            {pwError && (
              <p id={`${uid}-pw-err`} role="alert" style={fieldErrorStyle()}>
                {pwError}
              </p>
            )}
          </div>

            {isProcessing && <p role="status" aria-live="polite" style={{ fontSize: 13, marginBottom: 12 }}>로그인 요청을 처리하고 있습니다.</p>}
            <button type="submit" disabled={isProcessing} aria-label={isProcessing ? '로그인 처리 중' : '로그인'}
              style={{ width: '100%', backgroundColor: isProcessing ? '#908D88' : '#1A1918', color: '#FAFAF8',
                border: 'none', borderRadius: 2, padding: '12px 0', fontSize: 15, fontWeight: 700,
                cursor: isProcessing ? 'wait' : 'pointer', fontFamily: 'inherit', letterSpacing: '-0.01em' }}>
              {isProcessing ? '처리 중...' : '로그인'}
            </button>
          </form>
        )}
        {/* ── Supporting navigation ──────────────────────────────── */}
        <div
          role="navigation"
          aria-label="계정 관련 이동"
          style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 20, marginTop: 24 }}
        >
          <Link
            href="/register"
            style={{ fontSize: 13, color: '#4A4845', textDecoration: 'none' }}
            onMouseEnter={(e) => { e.currentTarget.style.textDecoration = 'underline' }}
            onMouseLeave={(e) => { e.currentTarget.style.textDecoration = 'none' }}
          >
            회원가입
          </Link>
          <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
          <Link
            href="/forgot-password"
            style={{ fontSize: 13, color: '#4A4845', textDecoration: 'none' }}
            onMouseEnter={(e) => { e.currentTarget.style.textDecoration = 'underline' }}
            onMouseLeave={(e) => { e.currentTarget.style.textDecoration = 'none' }}
          >
            비밀번호 찾기
          </Link>
        </div>

      </div>
    </main>
  );
}
