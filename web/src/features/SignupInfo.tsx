"use client";

import { useState, useId, useRef, useEffect } from 'react'
import { useNavigationRequest } from '@/components/NavigationGuard';
import { useRouter } from 'next/navigation';
import { useSignupFlow } from './SignupFlow';
import AuthFrame from '@/components/auth/AuthFrame';
import { FieldLabel, FieldError, PasswordInput } from '@/components/auth/AuthFields';
import { getCsrfToken, signup, SignupApiError } from '@/lib/signup-api';
import { buildSignupRequest, mapSignupApiError, validateSignupForm, type SignupFormErrors } from '@/lib/signup-form';

type FormState = 'idle' | 'processing'

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function SignupInfo() {
  const router = useRouter()
  const requestScope = useNavigationRequest()
  const uid = useId()
  const { agreements, status, completeSignup } = useSignupFlow()

  const [formState, setFormState] = useState<FormState>('idle')

  const [email, setEmail]                       = useState('')
  const [password, setPassword]                 = useState('')
  const [passwordConfirm, setPasswordConfirm]   = useState('')
  const [nickname, setNickname]                 = useState('')
  const [showPw, setShowPw]                     = useState(false)
  const [showPwConfirm, setShowPwConfirm]       = useState(false)

  const [errors, setErrors] = useState<SignupFormErrors>({})
  const emailError = errors.email ?? ''
  const passwordError = errors.password ?? ''
  const passwordConfirmError = errors.passwordConfirm ?? ''
  const nicknameError = errors.nickname ?? ''

  // state는 표시용, ref는 같은 렌더 사이의 연속 제출도 즉시 막는 용도입니다.
  const submitting = useRef(false)
  const completed = useRef(false)
  const csrfToken = useRef<string | null>(null)
  const pendingFocus = useRef<keyof SignupFormErrors | null>(null)
  const formErrorRef = useRef<HTMLDivElement>(null)

  const emailRef      = useRef<HTMLInputElement>(null)
  const passwordRef   = useRef<HTMLInputElement>(null)
  const pwConfirmRef  = useRef<HTMLInputElement>(null)
  const nicknameRef   = useRef<HTMLInputElement>(null)

  const isProcessing = formState === 'processing'

  const emailErrId      = `${uid}-email-err`
  const passwordErrId   = `${uid}-password-err`
  const pwConfirmErrId  = `${uid}-pwconfirm-err`
  const nicknameErrId   = `${uid}-nickname-err`

  // 실패 후 fieldset이 다시 활성화된 다음 오류가 있는 입력란으로 초점을 옮깁니다.
  useEffect(() => {
    if (!requestScope.isActive() || isProcessing || !pendingFocus.current) return
    const refs = { email: emailRef, password: passwordRef, passwordConfirm: pwConfirmRef, nickname: nicknameRef, form: formErrorRef }
    refs[pendingFocus.current].current?.focus()
    pendingFocus.current = null
  }, [errors, isProcessing, requestScope])

  function showErrors(nextErrors: SignupFormErrors) {
    const fields = ['email', 'password', 'passwordConfirm', 'nickname', 'form'] as const
    pendingFocus.current = fields.find(field => nextErrors[field]) ?? null
    setErrors(nextErrors)
  }

  function clearFieldError(field: keyof SignupFormErrors) {
    setErrors(previous => ({ ...previous, [field]: undefined }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!requestScope.isActive() || submitting.current || completed.current || status === 'PENDING') return

    const fields = { email, password, passwordConfirm, nickname }
    const validationErrors = validateSignupForm(fields, agreements)
    if (Object.keys(validationErrors).length > 0 || !agreements) {
      showErrors(validationErrors)
      return
    }

    const isCurrent = requestScope.begin()
    submitting.current = true
    setFormState('processing')
    setErrors({})
    try {
      // GET으로 받은 토큰은 메모리에만 보관합니다. 쿠키는 브라우저가 관리합니다.
      const csrf = csrfToken.current ?? await getCsrfToken()
      // CSRF 대기 중 확정된 이탈은 아직 폼이 남아 있어도 새 POST를 막는다.
      if (!isCurrent()) return
      csrfToken.current = csrf
      const request = buildSignupRequest(fields, agreements)
      await signup(request, csrfToken.current)

      if (!isCurrent()) return
      completed.current = true
      csrfToken.current = null
      setPassword('')
      setPasswordConfirm('')
      setShowPw(false)
      setShowPwConfirm(false)
      completeSignup() // API가 201 + PENDING을 반환한 경우에만 완료 상태를 만듭니다.
      router.replace('/register/verify')
    } catch (error) {
      if (!isCurrent()) return
      if (error instanceof SignupApiError && error.status === 403 && error.code === 'CSRF_TOKEN_INVALID') {
        csrfToken.current = null
        try {
          const refreshed = await getCsrfToken()
          if (!isCurrent()) return
          csrfToken.current = refreshed
          showErrors({ form: '보안 확인 정보를 갱신했습니다. 가입하기를 다시 눌러 주세요.' })
        } catch {
          if (!isCurrent()) return
          showErrors({ form: '보안 확인 정보를 갱신하지 못했습니다. 서버 연결을 확인한 뒤 다시 시도해 주세요.' })
        }
        // 토큰만 갱신합니다. 가입 POST는 사용자가 다시 제출해야 실행됩니다.
      } else if (error instanceof SignupApiError) {
        showErrors(mapSignupApiError(error))
      } else {
        showErrors({ form: '가입 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.' })
      }
    } finally {
      if (isCurrent()) {
        submitting.current = false
        setFormState('idle')
      }
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <AuthFrame title="회원정보 입력" step={2} description="가입에 사용할 이메일과 비밀번호, 닉네임을 입력해 주세요.">
      <p className="auth-copy">{agreements ? '앞 단계에서 선택한 필수 약관 동의 정보를 함께 전송합니다.' : '가입 전에 이전 단계에서 필수 약관에 동의해 주세요.'}</p>
      {errors.form && <div ref={formErrorRef} tabIndex={-1} role="alert" className="auth-feedback is-error"><p>{errors.form}</p></div>}
      <p role="status" aria-live="polite" className="sr-only">{isProcessing ? '회원가입 요청을 처리하고 있습니다.' : ''}</p>
      <form onSubmit={handleSubmit} noValidate aria-busy={isProcessing}>
        <fieldset disabled={isProcessing || status === 'PENDING'}>
          <legend className="sr-only">회원정보 입력</legend>
          <div className="auth-field">
            <FieldLabel htmlFor={`${uid}-email`} text="이메일" />
            <input className="auth-input" ref={emailRef} id={`${uid}-email`} type="email" autoComplete="email"
              value={email} onChange={e => { setEmail(e.target.value); if (emailError) clearFieldError('email'); }}
              placeholder="example@email.com" aria-invalid={!!emailError} aria-describedby={emailError ? emailErrId : undefined} aria-required="true" />
            <FieldError id={emailErrId} msg={emailError} />
          </div>
          <div className="auth-field">
            <FieldLabel htmlFor={`${uid}-password`} text="비밀번호" />
            <PasswordInput id={`${uid}-password`} value={password}
              onChange={v => { setPassword(v); if (passwordError) clearFieldError('password'); }}
              show={showPw} onToggle={() => setShowPw(p => !p)} hasError={!!passwordError}
              describedBy={passwordError ? passwordErrId : `${uid}-password-hint`} inputRef={passwordRef} disabled={isProcessing} />
            {!passwordError && <p id={`${uid}-password-hint`} className="auth-hint">8자 이상, 영문·숫자 포함, UTF-8 기준 최대 72바이트입니다.</p>}
            <FieldError id={passwordErrId} msg={passwordError} />
          </div>
          <div className="auth-field">
            <FieldLabel htmlFor={`${uid}-pwconfirm`} text="비밀번호 확인" />
            <PasswordInput id={`${uid}-pwconfirm`} value={passwordConfirm}
              onChange={v => { setPasswordConfirm(v); if (passwordConfirmError) clearFieldError('passwordConfirm'); }}
              show={showPwConfirm} onToggle={() => setShowPwConfirm(p => !p)} hasError={!!passwordConfirmError}
              describedBy={passwordConfirmError ? pwConfirmErrId : undefined} inputRef={pwConfirmRef} disabled={isProcessing} />
            <FieldError id={pwConfirmErrId} msg={passwordConfirmError} />
          </div>
          <div className="auth-field">
            <FieldLabel htmlFor={`${uid}-nickname`} text="닉네임" />
            <input className="auth-input" ref={nicknameRef} id={`${uid}-nickname`} type="text" autoComplete="nickname"
              value={nickname} onChange={e => { setNickname(e.target.value); if (nicknameError) clearFieldError('nickname'); }}
              placeholder="커뮤니티에서 사용할 닉네임" aria-invalid={!!nicknameError}
              aria-describedby={nicknameError ? nicknameErrId : `${uid}-nickname-hint`} aria-required="true" />
            {!nicknameError && <p id={`${uid}-nickname-hint`} className="auth-hint">2~20자의 한글·영문·숫자·밑줄. 예약된 이름은 사용할 수 없습니다.</p>}
            <FieldError id={nicknameErrId} msg={nicknameError} />
          </div>
          <div className="auth-actions">
            <button className="iyum-button iyum-button-outline" type="button" onClick={() => router.push('/register')} disabled={isProcessing}>이전</button>
            <button className="iyum-button iyum-button-primary" type="submit" disabled={isProcessing || status === 'PENDING'}>
              {isProcessing ? '가입 중…' : status === 'PENDING' ? '가입 완료' : '가입하기'}
            </button>
          </div>
        </fieldset>
      </form>
    </AuthFrame>
  )
}
