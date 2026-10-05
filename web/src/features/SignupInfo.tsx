"use client";

import { useState, useId, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation';
import { useSignupFlow } from './SignupFlow';
import { getCsrfToken, signup, SignupApiError } from '@/lib/signup-api';
import { buildSignupRequest, mapSignupApiError, validateSignupForm, type SignupFormErrors } from '@/lib/signup-form';

type FormState = 'idle' | 'processing'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function fieldBorder(hasError: boolean) {
  const c = hasError ? '#B91C1C' : '#D0CEC9'
  return {
    borderTop: `1.5px solid ${c}`,
    borderBottom: `1.5px solid ${c}`,
    borderLeft: `1.5px solid ${c}`,
    borderRight: `1.5px solid ${c}`,
  }
}

const BASE_INPUT: React.CSSProperties = {
  width: '100%',
  boxSizing: 'border-box' as const,
  borderRadius: 2,
  padding: '10px 12px',
  fontSize: 14,
  backgroundColor: '#FAFAF8',
  color: '#1A1918',
  outline: 'none',
  fontFamily: 'inherit',
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function FieldLabel({ htmlFor, text }: { htmlFor: string; text: string }) {
  return (
    <label
      htmlFor={htmlFor}
      style={{
        display: 'block',
        fontSize: 12,
        fontWeight: 600,
        color: '#4A4845',
        marginBottom: 6,
        letterSpacing: '0.01em',
      }}
    >
      {text}{' '}
      <span aria-hidden="true" style={{ color: '#B91C1C', fontWeight: 400 }}>
        (필수)
      </span>
    </label>
  )
}

function FieldError({ id, msg }: { id: string; msg: string }) {
  if (!msg) return null
  return (
    <p
      id={id}
      role="alert"
      style={{ fontSize: 12, color: '#B91C1C', marginTop: 5, marginBottom: 0 }}
    >
      {msg}
    </p>
  )
}

function PasswordInput({
  id,
  value,
  onChange,
  show,
  onToggle,
  hasError,
  describedBy,
  placeholder,
  inputRef,
  disabled,
}: {
  id: string
  value: string
  onChange: (v: string) => void
  show: boolean
  onToggle: () => void
  hasError: boolean
  describedBy?: string
  placeholder?: string
  inputRef?: React.RefObject<HTMLInputElement | null>
  disabled?: boolean
}) {
  return (
    <div style={{ position: 'relative' }}>
      <input
        ref={inputRef}
        id={id}
        type={show ? 'text' : 'password'}
        autoComplete="new-password"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        aria-invalid={hasError}
        aria-describedby={describedBy}
        aria-required="true"
        style={{
          ...BASE_INPUT,
          ...fieldBorder(hasError),
          paddingRight: 44,
        }}
      />
      <button
        type="button"
        onClick={onToggle}
        disabled={disabled}
        aria-label={show ? '비밀번호 숨기기' : '비밀번호 표시'}
        style={{
          position: 'absolute',
          right: 10,
          top: '50%',
          transform: 'translateY(-50%)',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: 4,
          color: '#908D88',
          fontSize: 11,
          fontFamily: "'DM Mono', monospace",
          letterSpacing: '0.03em',
        }}
      >
        {show ? 'HIDE' : 'SHOW'}
      </button>
    </div>
  )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function SignupInfo() {
  const router = useRouter()
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
    if (isProcessing || !pendingFocus.current) return
    const refs = { email: emailRef, password: passwordRef, passwordConfirm: pwConfirmRef, nickname: nicknameRef, form: formErrorRef }
    refs[pendingFocus.current].current?.focus()
    pendingFocus.current = null
  }, [errors, isProcessing])

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
    if (submitting.current || completed.current || status === 'PENDING') return

    const fields = { email, password, passwordConfirm, nickname }
    const validationErrors = validateSignupForm(fields, agreements)
    if (Object.keys(validationErrors).length > 0 || !agreements) {
      showErrors(validationErrors)
      return
    }

    submitting.current = true
    setFormState('processing')
    setErrors({})
    try {
      // GET으로 받은 토큰은 메모리에만 보관합니다. 쿠키는 브라우저가 관리합니다.
      if (!csrfToken.current) csrfToken.current = await getCsrfToken()
      const request = buildSignupRequest(fields, agreements)
      await signup(request, csrfToken.current)

      completed.current = true
      csrfToken.current = null
      setPassword('')
      setPasswordConfirm('')
      setShowPw(false)
      setShowPwConfirm(false)
      completeSignup() // API가 201 + PENDING을 반환한 경우에만 완료 상태를 만듭니다.
      router.replace('/register/verify')
    } catch (error) {
      if (error instanceof SignupApiError && error.status === 403 && error.code === 'CSRF_TOKEN_INVALID') {
        csrfToken.current = null
        try {
          csrfToken.current = await getCsrfToken()
          showErrors({ form: '보안 확인 정보를 갱신했습니다. 가입하기를 다시 눌러 주세요.' })
        } catch {
          showErrors({ form: '보안 확인 정보를 갱신하지 못했습니다. 서버 연결을 확인한 뒤 다시 시도해 주세요.' })
        }
        // 토큰만 갱신합니다. 가입 POST는 사용자가 다시 제출해야 실행됩니다.
      } else if (error instanceof SignupApiError) {
        showErrors(mapSignupApiError(error))
      } else {
        showErrors({ form: '가입 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.' })
      }
    } finally {
      submitting.current = false
      setFormState('idle')
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <main>
      <div style={{ maxWidth: 500, margin: '0 auto', padding: '60px 24px 80px' }}>

        {/* Context label */}
        <p
          style={{
            fontSize: 11,
            color: '#908D88',
            fontFamily: "'DM Mono', monospace",
            letterSpacing: '0.04em',
            marginBottom: 8,
          }}
        >
          회원가입 2/3
        </p>

        <h1
          style={{
            fontSize: 22,
            fontWeight: 700,
            color: '#1A1918',
            letterSpacing: '-0.01em',
            marginBottom: 8,
          }}
        >
          회원정보 입력
        </h1>

        <p style={{ fontSize: 13, color: '#908D88', lineHeight: 1.7, marginBottom: 36 }}>
          가입에 사용할 이메일과 비밀번호, 닉네임을 입력해 주세요.
        </p>

        <p style={{ fontSize: 12, color: '#908D88', marginBottom: 20 }}>
          {agreements ? '앞 단계에서 선택한 필수 약관 동의 정보를 함께 전송합니다.' : '가입 전에 이전 단계에서 필수 약관에 동의해 주세요.'}
        </p>

        {errors.form && (
          <div
            ref={formErrorRef}
            tabIndex={-1}
            role="alert"
            style={{ border: '1.5px solid #92400E', borderRadius: 2, padding: '12px 14px', marginBottom: 24, backgroundColor: '#FFFBEB' }}
          >
            <p style={{ fontSize: 13, color: '#92400E', margin: 0 }}>{errors.form}</p>
          </div>
        )}
        <p role="status" aria-live="polite" className="sr-only">
          {isProcessing ? '회원가입 요청을 처리하고 있습니다.' : ''}
        </p>

        {/* Form */}
        <form onSubmit={handleSubmit} noValidate aria-busy={isProcessing}>
          <fieldset style={{ border: 'none', padding: 0, margin: 0 }} disabled={isProcessing || status === 'PENDING'}>

            {/* ── 이메일 ──────────────────────────────────────────────── */}
            <div style={{ marginBottom: 20 }}>
              <FieldLabel htmlFor={`${uid}-email`} text="이메일" />
              <input
                ref={emailRef}
                id={`${uid}-email`}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => { setEmail(e.target.value); if (emailError) clearFieldError('email') }}
                placeholder="example@email.com"
                aria-invalid={!!emailError}
                aria-describedby={emailError ? emailErrId : undefined}
                aria-required="true"
                style={{ ...BASE_INPUT, ...fieldBorder(!!emailError) }}
              />
              <FieldError id={emailErrId} msg={emailError} />
            </div>

            {/* ── 비밀번호 ─────────────────────────────────────────────── */}
            <div style={{ marginBottom: 20 }}>
              <FieldLabel htmlFor={`${uid}-password`} text="비밀번호" />
              <PasswordInput
                id={`${uid}-password`}
                value={password}
                onChange={(v) => { setPassword(v); if (passwordError) clearFieldError('password') }}
                show={showPw}
                onToggle={() => setShowPw((p) => !p)}
                hasError={!!passwordError}
                describedBy={passwordError ? passwordErrId : `${uid}-password-hint`}
                inputRef={passwordRef}
                disabled={isProcessing}
              />
              {!passwordError && (
                <p
                  id={`${uid}-password-hint`}
                  style={{ fontSize: 11, color: '#908D88', marginTop: 5, marginBottom: 0 }}
                >
                  8자 이상, 영문·숫자 포함, UTF-8 기준 최대 72바이트입니다.
                </p>
              )}
              <FieldError id={passwordErrId} msg={passwordError} />
            </div>

            {/* ── 비밀번호 확인 ─────────────────────────────────────────── */}
            <div style={{ marginBottom: 20 }}>
              <FieldLabel htmlFor={`${uid}-pwconfirm`} text="비밀번호 확인" />
              <PasswordInput
                id={`${uid}-pwconfirm`}
                value={passwordConfirm}
                onChange={(v) => { setPasswordConfirm(v); if (passwordConfirmError) clearFieldError('passwordConfirm') }}
                show={showPwConfirm}
                onToggle={() => setShowPwConfirm((p) => !p)}
                hasError={!!passwordConfirmError}
                describedBy={passwordConfirmError ? pwConfirmErrId : undefined}
                inputRef={pwConfirmRef}
                disabled={isProcessing}
              />
              <FieldError id={pwConfirmErrId} msg={passwordConfirmError} />
            </div>

            {/* ── 닉네임 ───────────────────────────────────────────────── */}
            <div style={{ marginBottom: 32 }}>
              <FieldLabel htmlFor={`${uid}-nickname`} text="닉네임" />
              <input
                ref={nicknameRef}
                id={`${uid}-nickname`}
                type="text"
                autoComplete="nickname"
                value={nickname}
                onChange={(e) => { setNickname(e.target.value); if (nicknameError) clearFieldError('nickname') }}
                placeholder="커뮤니티에서 사용할 닉네임"
                aria-invalid={!!nicknameError}
                aria-describedby={nicknameError ? nicknameErrId : `${uid}-nickname-hint`}
                aria-required="true"
                style={{ ...BASE_INPUT, ...fieldBorder(!!nicknameError) }}
              />
              {!nicknameError && (
                <p
                  id={`${uid}-nickname-hint`}
                  style={{ fontSize: 11, color: '#908D88', marginTop: 5, marginBottom: 0 }}
                >
                  2~20자의 한글·영문·숫자·밑줄. 예약된 이름은 사용할 수 없습니다.
                </p>
              )}
              <FieldError id={nicknameErrId} msg={nicknameError} />
            </div>

            {/* ── Action area ──────────────────────────────────────────── */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                alignItems: 'center',
                gap: 12,
              }}
            >
              <button
                type="button"
                onClick={() => router.push('/register')}
                disabled={isProcessing}
                style={{
                  fontSize: 14,
                  color: isProcessing ? '#C8C5BF' : '#4A4845',
                  background: 'none',
                  border: 'none',
                  cursor: isProcessing ? 'default' : 'pointer',
                  fontFamily: 'inherit',
                  padding: '10px 16px',
                }}
              >
                이전
              </button>

              <button
                type="submit"
                disabled={isProcessing || status === 'PENDING'}
                style={{
                  fontSize: 14,
                  fontWeight: 700,
                  color: '#FAFAF8',
                  backgroundColor: isProcessing ? '#4A4845' : '#1A1918',
                  border: 'none',
                  borderRadius: 2,
                  padding: '10px 28px',
                  cursor: isProcessing ? 'default' : 'pointer',
                  fontFamily: 'inherit',
                  letterSpacing: '-0.01em',
                  minWidth: 72,
                }}
              >
                {isProcessing ? '가입 중…' : status === 'PENDING' ? '가입 완료' : '가입하기'}
              </button>
            </div>

          </fieldset>
        </form>


      </div>
    </main>
  )
}
