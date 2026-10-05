"use client";

import { useState, useId, useRef } from 'react'
import { useRouter } from 'next/navigation';

type FormState = 'idle' | 'processing' | 'networkError'

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
        tabIndex={-1}
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

  const [formState, setFormState] = useState<FormState>('idle')

  const [email, setEmail]                       = useState('')
  const [password, setPassword]                 = useState('')
  const [passwordConfirm, setPasswordConfirm]   = useState('')
  const [nickname, setNickname]                 = useState('')
  const [showPw, setShowPw]                     = useState(false)
  const [showPwConfirm, setShowPwConfirm]       = useState(false)

  const [emailError, setEmailError]                       = useState('')
  const [passwordError, setPasswordError]                 = useState('')
  const [passwordConfirmError, setPasswordConfirmError]   = useState('')
  const [nicknameError, setNicknameError]                 = useState('')

  const emailRef      = useRef<HTMLInputElement>(null)
  const passwordRef   = useRef<HTMLInputElement>(null)
  const pwConfirmRef  = useRef<HTMLInputElement>(null)
  const nicknameRef   = useRef<HTMLInputElement>(null)

  const isProcessing = formState === 'processing'

  const emailErrId      = `${uid}-email-err`
  const passwordErrId   = `${uid}-password-err`
  const pwConfirmErrId  = `${uid}-pwconfirm-err`
  const nicknameErrId   = `${uid}-nickname-err`

  // ── Validation ─────────────────────────────────────────────────────────────

  function validate(): boolean {
    type ErrorEntry = {
      ref: React.RefObject<HTMLInputElement | null>
      setter: (s: string) => void
      msg: string
    }
    const errors: ErrorEntry[] = []

    const emailTrimmed = email.trim()
    if (!emailTrimmed) {
      errors.push({ ref: emailRef, setter: setEmailError, msg: '이메일을 입력해 주세요.' })
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailTrimmed)) {
      errors.push({ ref: emailRef, setter: setEmailError, msg: '올바른 이메일 주소를 입력해 주세요.' })
    } else {
      setEmailError('')
    }

    if (!password) {
      errors.push({ ref: passwordRef, setter: setPasswordError, msg: '비밀번호를 입력해 주세요.' })
    } else if (
      password.length < 8 ||
      !/[a-zA-Z]/.test(password) ||
      !/[0-9]/.test(password)
    ) {
      errors.push({
        ref: passwordRef,
        setter: setPasswordError,
        msg: '비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.',
      })
    } else {
      setPasswordError('')
    }

    if (!passwordConfirm) {
      errors.push({
        ref: pwConfirmRef,
        setter: setPasswordConfirmError,
        msg: '비밀번호를 한 번 더 입력해 주세요.',
      })
    } else if (password && passwordConfirm !== password) {
      errors.push({
        ref: pwConfirmRef,
        setter: setPasswordConfirmError,
        msg: '비밀번호가 일치하지 않습니다.',
      })
    } else {
      setPasswordConfirmError('')
    }

    const nickTrimmed = nickname.trim()
    if (!nickTrimmed) {
      errors.push({ ref: nicknameRef, setter: setNicknameError, msg: '닉네임을 입력해 주세요.' })
    } else if (nickTrimmed.length < 2 || nickTrimmed.length > 20) {
      errors.push({
        ref: nicknameRef,
        setter: setNicknameError,
        msg: '닉네임은 2자 이상 20자 이하로 입력해 주세요.',
      })
    } else {
      setNicknameError('')
    }

    if (errors.length > 0) {
      errors.forEach(({ setter, msg }) => setter(msg))
      errors[0].ref.current?.focus()
      return false
    }
    return true
  }

  // ── Handlers ───────────────────────────────────────────────────────────────

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (isProcessing) return

    setFormState('idle')
    setEmailError('')
    setPasswordError('')
    setPasswordConfirmError('')
    setNicknameError('')

    if (!validate()) return

    setFormState('processing')

    setTimeout(() => {
      const emailTrimmed = email.trim()
      const nickTrimmed  = nickname.trim()

      if (emailTrimmed === 'duplicate@example.com') {
        setEmailError('이미 가입된 이메일입니다.')
        setFormState('idle')
        emailRef.current?.focus()
        return
      }

      if (nickTrimmed === 'duplicate') {
        setNicknameError('이미 사용 중인 닉네임입니다.')
        setFormState('idle')
        nicknameRef.current?.focus()
        return
      }

      if (emailTrimmed === 'error@example.com') {
        setFormState('networkError')
        return
      }

      router.push('/register/verify')
    }, 400)
  }

  function handleRetry() {
    setFormState('processing')
    setTimeout(() => setFormState('idle'), 400)
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

        {/* Network/System Error banner */}
        {formState === 'networkError' && (
          <div
            role="alert"
            style={{
              borderTop: '1.5px solid #92400E',
              borderBottom: '1.5px solid #92400E',
              borderLeft: '1.5px solid #92400E',
              borderRight: '1.5px solid #92400E',
              borderRadius: 2,
              padding: '12px 14px',
              marginBottom: 24,
              backgroundColor: '#FFFBEB',
            }}
          >
            <p style={{ fontSize: 13, color: '#92400E', marginBottom: 6 }}>
              가입을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.
            </p>
            <button
              type="button"
              onClick={handleRetry}
              style={{
                fontSize: 12,
                color: '#92400E',
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                fontFamily: 'inherit',
                padding: 0,
                textDecoration: 'underline',
                textDecorationStyle: 'dotted',
              }}
            >
              다시 시도
            </button>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} noValidate>
          <fieldset style={{ border: 'none', padding: 0, margin: 0 }} disabled={isProcessing}>

            {/* ── 이메일 ──────────────────────────────────────────────── */}
            <div style={{ marginBottom: 20 }}>
              <FieldLabel htmlFor={`${uid}-email`} text="이메일" />
              <input
                ref={emailRef}
                id={`${uid}-email`}
                type="email"
                value={email}
                onChange={(e) => { setEmail(e.target.value); if (emailError) setEmailError('') }}
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
                onChange={(v) => { setPassword(v); if (passwordError) setPasswordError('') }}
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
                  8자 이상, 영문과 숫자를 포함해야 합니다.
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
                onChange={(v) => { setPasswordConfirm(v); if (passwordConfirmError) setPasswordConfirmError('') }}
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
                value={nickname}
                onChange={(e) => { setNickname(e.target.value); if (nicknameError) setNicknameError('') }}
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
                  2자 이상 20자 이하
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
                {isProcessing ? '처리 중…' : '다음'}
              </button>
            </div>

          </fieldset>
        </form>

        {/* Prototype inspection guide */}
        <div
          style={{
            marginTop: 48,
            paddingTop: 20,
            borderTop: '1px solid #E8E6E1',
          }}
        >
          <p
            style={{
              fontSize: 10,
              color: '#C8C5BF',
              fontFamily: "'DM Mono', monospace",
              letterSpacing: '0.04em',
              marginBottom: 10,
            }}
          >
            PROTOTYPE · 테스트 시나리오
          </p>
          <table style={{ fontSize: 11, color: '#908D88', borderCollapse: 'collapse', width: '100%' }}>
            <thead>
              <tr>
                {['이메일', '비밀번호', '비밀번호 확인', '닉네임', '결과'].map((h) => (
                  <th
                    key={h}
                    style={{
                      textAlign: 'left',
                      fontWeight: 600,
                      color: '#4A4845',
                      paddingBottom: 6,
                      fontFamily: "'DM Mono', monospace",
                      fontSize: 10,
                      letterSpacing: '0.03em',
                      paddingRight: 12,
                    }}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[
                ['duplicate@example.com', 'password123', 'password123', 'iyumuser', '이메일 중복 오류'],
                ['newuser@example.com',   'password123', 'password123', 'duplicate', '닉네임 중복 오류'],
                ['error@example.com',     'password123', 'password123', 'iyumuser', '네트워크 오류 → 재시도'],
                ['success@example.com',   'password123', 'password123', 'iyumuser', '성공 → SCR-AUTH-004'],
              ].map((row) => (
                <tr key={row[0]}>
                  {row.map((cell, i) => (
                    <td
                      key={i}
                      style={{
                        paddingBottom: 5,
                        paddingRight: 12,
                        fontFamily: i < 4 ? "'DM Mono', monospace" : 'inherit',
                        fontSize: i === 4 ? 11 : 10,
                        color: i === 4 ? '#4A4845' : '#908D88',
                      }}
                    >
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

      </div>
    </main>
  )
}
