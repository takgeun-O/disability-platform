"use client";

import { useEffect, useState, useId, useRef, type FormEvent } from 'react';
import Link from '@/components/AppLink';
import AuthFrame from '@/components/auth/AuthFrame';
import { FieldLabel, FieldError, PasswordInput } from '@/components/auth/AuthFields';
import { useRouter } from 'next/navigation';
import { safeReturnPath } from '@/lib/navigation';
import { useQueryParams } from '@/lib/useQueryParams';
import { useVerificationNotice } from '@/components/VerificationNotice';
import { useAuth } from '@/components/AuthProvider';
import { useNavigationGuard, useNavigationRequest } from '@/components/NavigationGuard';
import { AuthApiError, getAuthCsrf } from '@/lib/auth-api';
import { validateLoginForm } from '@/lib/login-form';

type Feedback = { verificationTicket?: number; kind: 'error' | 'success'; message: string; needsVerification?: boolean; canContinue?: boolean };

export default function Login() {
  const [searchParams] = useQueryParams();
  const router = useRouter();
  const auth = useAuth();
  const guard = useNavigationGuard();
  const focusScope = useNavigationRequest();
  const { read, consume } = useVerificationNotice();
  // 읽기는 순수하게 수행하고, 실제로 마운트한 뒤 소비한다(StrictMode/중단된 렌더 대응).
  const [verificationTicket] = useState(read);
  const uid = useId();
  const destination = safeReturnPath(searchParams.get('from') ?? '');
  const hasReturn = destination !== '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [pwError, setPwError] = useState('');
  const [feedback, setFeedback] = useState<Feedback | null>(() => verificationTicket === null ? null : {
    kind: 'success', verificationTicket, message: '이메일 인증이 완료되었습니다. 로그인해 주세요.',
  });
  const [preparing, setPreparing] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const pwRef = useRef<HTMLInputElement>(null);
  const feedbackRef = useRef<HTMLDivElement>(null);
  const inFlightRef = useRef(false);
  const mountedRef = useRef(false);
  const isProcessing = auth.pending !== null || preparing;

  useEffect(() => {
    if (verificationTicket !== null) consume(verificationTicket);
  }, [verificationTicket, consume]);
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);
  useEffect(() => {
    if (!focusScope.isActive()) return;
    if (emailError) emailRef.current?.focus();
    else if (pwError) pwRef.current?.focus();
    else feedbackRef.current?.focus();
  }, [emailError, pwError, feedback, focusScope]);

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

  const visibleFeedback = feedback && (feedback.verificationTicket === undefined
    || feedback.verificationTicket === guard.getApprovalVersion()) ? feedback : null;

  return (
    <AuthFrame title="로그인" description={hasReturn ? '로그인 후 이전 페이지로 이동합니다.' : '이메일과 비밀번호로 로그인해 주세요.'}>
      {visibleFeedback && <div ref={feedbackRef} tabIndex={-1} role={visibleFeedback.kind === 'error' ? 'alert' : 'status'}
        className={`auth-feedback${visibleFeedback.kind === 'error' ? ' is-error' : ''}`}>
        <p>{visibleFeedback.message}</p>
        {visibleFeedback.verificationTicket !== undefined && auth.status === 'authenticated' && <p>현재 로그인된 세션은 유지됩니다. 방금 인증한 계정과 같은 계정인지 이 안내만으로 확인할 수 없습니다.</p>}
        {visibleFeedback.needsVerification && <Link href="/register/resend" className="iyum-text-link">인증 메일 다시 요청하기</Link>}
      </div>}
      {auth.status === 'authenticated' && auth.member ? (
        <div>
          <p className="auth-copy">{auth.member.nickname}님으로 로그인되어 있습니다.</p>
          <button className="iyum-button iyum-button-primary auth-submit" type="button" disabled={isProcessing} onClick={navigateAfterLogin}>
            {isProcessing ? '보안 확인 중…' : hasReturn ? '이전 페이지로 계속' : '홈으로 이동'}
          </button>
          {feedback?.canContinue && <button className="iyum-text-link" type="button" disabled={isProcessing} onClick={() => { void prepareAndContinue(); }}>보안 정보 다시 확인</button>}
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate aria-label="로그인 양식" aria-busy={isProcessing}>
          {/* 입력 상태·검증·오류 초점은 기존 handleSubmit과 ref가 관리한다. */}
          <div className="auth-field">
            <FieldLabel htmlFor={`${uid}-email`} text="이메일" />
            <input className="auth-input" ref={emailRef} id={`${uid}-email`} type="email" name="email" autoComplete="email"
              value={email} onChange={event => { setEmail(event.target.value); if (emailError) setEmailError(''); }}
              placeholder="이메일 주소" aria-required="true" aria-invalid={emailError ? true : undefined}
              aria-describedby={emailError ? `${uid}-email-err` : undefined} disabled={isProcessing} />
            <FieldError id={`${uid}-email-err`} msg={emailError} />
          </div>
          <div className="auth-field">
            <FieldLabel htmlFor={`${uid}-pw`} text="비밀번호" />
            <PasswordInput inputRef={pwRef} id={`${uid}-pw`} name="password" autoComplete="current-password"
              value={password} onChange={value => { setPassword(value); if (pwError) setPwError(''); }}
              placeholder="비밀번호" show={showPw} onToggle={() => setShowPw(value => !value)}
              hasError={!!pwError} describedBy={pwError ? `${uid}-pw-err` : undefined} disabled={isProcessing} />
            <FieldError id={`${uid}-pw-err`} msg={pwError} />
          </div>
          {isProcessing && <p className="auth-status" role="status" aria-live="polite">로그인 요청을 처리하고 있습니다.</p>}
          <button className="iyum-button iyum-button-primary auth-submit" type="submit" disabled={isProcessing} aria-label={isProcessing ? '로그인 처리 중' : '로그인'}>
            {isProcessing ? '처리 중...' : '로그인'}
          </button>
        </form>
      )}
      <nav className="auth-links" aria-label="계정 관련 이동">
        <Link href="/register" className="iyum-text-link">회원가입</Link>
        <Link href="/forgot-password" className="iyum-text-link">비밀번호 찾기</Link>
      </nav>
    </AuthFrame>
  );
}
