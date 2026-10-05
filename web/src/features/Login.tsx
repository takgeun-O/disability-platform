"use client";

import { useState, useId, useRef } from 'react'
import Link from '@/components/AppLink';
import { useRouter } from 'next/navigation';
import { safeReturnPath } from '@/lib/navigation';
import { useQueryParams } from '@/lib/useQueryParams';

// ─── Types ────────────────────────────────────────────────────────────────────

type FormState = 'idle' | 'processing' | 'authFail' | 'networkError' | 'restricted'

// ─── Helpers ──────────────────────────────────────────────────────────────────

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

// ─── Mock sentinels (prototype-only, not real Product credentials) ───────────
//
//   success@example.com   / success1234   → Login success → Home
//   restricted@example.com / restricted1234 → Restricted state
//   error@example.com     / error1234     → Network/System Error
//   anything else (valid form)            → Authentication Failure

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function Login() {
  const [searchParams] = useQueryParams()
  const router = useRouter()
  const uid = useId()

  // `from` carries the encoded return path (not displayed to user)
  const fromParam  = searchParams.get('from')  ?? ''
  // `state` enables static state inspection via URL for Lo-Fi review
  const stateParam = searchParams.get('state') ?? ''

  const initFormState = (): FormState => {
    switch (stateParam) {
      case 'authFail':     return 'authFail'
      case 'networkError': return 'networkError'
      case 'restricted':   return 'restricted'
      case 'processing':   return 'processing'
      default:             return 'idle'
    }
  }

  // Pre-populate email for static state inspection frames
  const initEmail = (): string => {
    switch (stateParam) {
      case 'restricted':   return 'restricted@example.com'
      case 'authFail':
      case 'networkError':
      case 'processing':   return 'user@example.com'
      default:             return ''
    }
  }

  const [formState, setFormState]     = useState<FormState>(initFormState)
  const [email, setEmail]             = useState(initEmail)
  const [password, setPassword]       = useState('')
  const [keepLogin, setKeepLogin]     = useState(false)
  const [showPw, setShowPw]           = useState(false)
  const [emailError, setEmailError]   = useState('')
  const [pwError, setPwError]         = useState('')

  const emailRef = useRef<HTMLInputElement>(null)
  const pwRef    = useRef<HTMLInputElement>(null)

  const isProcessing = formState === 'processing'
  const hasReturn    = !!fromParam

  // ── Validation ─────────────────────────────────────────────────────────────

  function validate(): boolean {
    let firstRef: React.RefObject<HTMLInputElement | null> | null = null

    const trimmed = email.trim()
    if (!trimmed) {
      setEmailError('이메일을 입력해 주세요.')
      if (!firstRef) firstRef = emailRef
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setEmailError('올바른 이메일 형식을 입력해 주세요.')
      if (!firstRef) firstRef = emailRef
    } else {
      setEmailError('')
    }

    if (!password) {
      setPwError('비밀번호를 입력해 주세요.')
      if (!firstRef) firstRef = pwRef
    } else {
      setPwError('')
    }

    if (firstRef) firstRef.current?.focus()
    return firstRef === null
  }

  // ── Submit ─────────────────────────────────────────────────────────────────

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (isProcessing) return

    // Reset form-level state and field errors before new attempt
    setFormState('idle')
    setEmailError('')
    setPwError('')

    if (!validate()) return

    setFormState('processing')

    setTimeout(() => {
      const trimmed = email.trim()

      if (trimmed === 'success@example.com' && password === 'success1234') {
        const dest = safeReturnPath(fromParam)
        router.push(dest)
        return
      }
      if (trimmed === 'restricted@example.com' && password === 'restricted1234') {
        setFormState('restricted')
        return
      }
      if (trimmed === 'error@example.com' && password === 'error1234') {
        setFormState('networkError')
        return
      }
      // Default mock: credentials not accepted → Authentication Failure
      setFormState('authFail')
    }, 400)
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  const formAreaLabel: Record<FormState, string | null> = {
    idle:         null,
    processing:   null,
    authFail:     '이메일 또는 비밀번호를 확인해 주세요.',
    networkError: '로그인 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.',
    restricted:   '현재 이 계정으로 로그인할 수 없습니다.',
  }

  return (
    <main>
      <div style={{ maxWidth: 440, margin: '0 auto', padding: '60px 24px 80px' }}>

        {/* H1 */}
        <h1 style={{ fontSize: 22, fontWeight: 700, color: '#1A1918', letterSpacing: '-0.01em', marginBottom: hasReturn ? 10 : 36 }}>
          로그인
        </h1>

        {/* Return context hint — no technical URL displayed */}
        {hasReturn && (
          <p style={{ fontSize: 13, color: '#908D88', marginBottom: 32 }}>
            로그인 후 이전 페이지로 이동합니다.
          </p>
        )}

        <form onSubmit={handleSubmit} noValidate aria-label="로그인 양식">

          {/* ── Auth Failure (form-level) ──────────────────────────── */}
          {formState === 'authFail' && (
            <div
              role="alert"
              style={{
                borderTop: '1px solid #B91C1C', borderBottom: '1px solid #B91C1C',
                borderLeft: '1px solid #B91C1C', borderRight: '1px solid #B91C1C',
                borderRadius: 2, padding: '11px 14px', marginBottom: 24,
              }}
            >
              <p style={{ fontSize: 13, color: '#B91C1C', margin: 0 }}>
                {formAreaLabel.authFail}
              </p>
            </div>
          )}

          {/* ── Network / System Error (form-level) ───────────────── */}
          {formState === 'networkError' && (
            <div
              role="alert"
              style={{
                borderTop: '1px solid #92400E', borderBottom: '1px solid #92400E',
                borderLeft: '1px solid #92400E', borderRight: '1px solid #92400E',
                borderRadius: 2, padding: '11px 14px', marginBottom: 24,
              }}
            >
              <p style={{ fontSize: 13, color: '#92400E', margin: 0, marginBottom: 8 }}>
                {formAreaLabel.networkError}
              </p>
              <button
                type="button"
                onClick={() => {
                  // Retry: show Processing briefly, then return to recoverable idle state
                  setFormState('processing')
                  setTimeout(() => setFormState('idle'), 400)
                }}
                aria-label="로그인 다시 시도"
                style={{
                  fontSize: 12, color: '#92400E', background: 'none', border: 'none',
                  padding: 0, cursor: 'pointer', textDecoration: 'underline', fontFamily: 'inherit',
                }}
              >
                다시 시도
              </button>
            </div>
          )}

          {/* ── Restricted account (form-level) ───────────────────── */}
          {formState === 'restricted' && (
            <div
              role="alert"
              style={{
                borderTop: '1px solid #D0CEC9', borderBottom: '1px solid #D0CEC9',
                borderLeft: '1px solid #D0CEC9', borderRight: '1px solid #D0CEC9',
                borderRadius: 2, padding: '11px 14px', marginBottom: 24,
              }}
            >
              <p style={{ fontSize: 13, color: '#4A4845', margin: 0, marginBottom: 4 }}>
                {formAreaLabel.restricted}
              </p>
              <p style={{ fontSize: 12, color: '#908D88', margin: 0 }}>
                계정 상태를 확인하거나 문의해 주세요.
              </p>
            </div>
          )}

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

          {/* ── 로그인 유지 ────────────────────────────────────────── */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 28 }}>
            <input
              id={`${uid}-keep`}
              type="checkbox"
              checked={keepLogin}
              onChange={(e) => setKeepLogin(e.target.checked)}
              disabled={isProcessing}
              style={{ width: 16, height: 16, cursor: isProcessing ? 'default' : 'pointer', accentColor: '#1A1918', flexShrink: 0 }}
            />
            <label
              htmlFor={`${uid}-keep`}
              style={{ fontSize: 13, color: '#4A4845', cursor: isProcessing ? 'default' : 'pointer', userSelect: 'none' }}
            >
              로그인 유지
            </label>
          </div>

          {/* ── Primary action ─────────────────────────────────────── */}
          <button
            type="submit"
            disabled={isProcessing}
            aria-disabled={isProcessing}
            aria-label={isProcessing ? '로그인 처리 중' : '로그인'}
            style={{
              width: '100%',
              backgroundColor: isProcessing ? '#908D88' : '#1A1918',
              color: '#FAFAF8',
              border: 'none',
              borderRadius: 2,
              padding: '12px 0',
              fontSize: 15,
              fontWeight: 700,
              cursor: isProcessing ? 'default' : 'pointer',
              fontFamily: 'inherit',
              letterSpacing: '-0.01em',
            }}
          >
            {isProcessing ? '처리 중...' : '로그인'}
          </button>

        </form>

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

        {/* ── Lo-Fi prototype state inspection guide ─────────────── */}
        <div style={{ marginTop: 52, paddingTop: 24, borderTop: '1px solid #E8E6E1' }}>
          <p
            style={{
              fontSize: 11, color: '#D0CEC9', marginBottom: 14,
              fontFamily: "'DM Mono', monospace", letterSpacing: '0.04em',
            }}
          >
            PROTOTYPE · 상태 확인
          </p>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 9 }}>
            {(
              [
                { label: '기본 (Default)',              to: '/login' },
                { label: '인증 실패',                   to: '/login?state=authFail' },
                { label: '네트워크 / 시스템 오류',       to: '/login?state=networkError' },
                { label: '계정 제한됨',                 to: '/login?state=restricted' },
                { label: '처리 중 — 정적 확인',          to: '/login?state=processing' },
                {
                  label: '보호된 이동 — 글쓰기 진입',
                  to: `/login?from=${encodeURIComponent('/community/posts/create')}`,
                },
                {
                  label: '보호된 동작 — 게시글 상세 복귀',
                  to: `/login?from=${encodeURIComponent('/community/posts/101')}`,
                },
              ] as { label: string; to: string }[]
            ).map((item) => (
              <li key={item.to}>
                <Link
                  href={item.to}
                  style={{
                    fontSize: 11, color: '#908D88',
                    fontFamily: "'DM Mono', monospace",
                    textDecoration: 'underline',
                    textDecorationStyle: 'dotted',
                  }}
                >
                  {item.label}
                </Link>
              </li>
            ))}
            <li style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace", marginTop: 4 }}>
              유효성 오류 — 이메일 없이 제출
            </li>
            <li style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace" }}>
              유효성 오류 — 형식 오류 (예: &quot;abc&quot; 입력 후 제출)
            </li>
            <li style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace", marginTop: 4 }}>
              ── 폼 직접 테스트 (프로토타입 전용 계정) ──
            </li>
            <li style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace" }}>
              성공: success@example.com / success1234
            </li>
            <li style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace" }}>
              계정 제한: restricted@example.com / restricted1234
            </li>
            <li style={{ fontSize: 11, color: '#908D88', fontFamily: "'DM Mono', monospace" }}>
              오류 / 재시도: error@example.com / error1234
            </li>
          </ul>
        </div>

      </div>
    </main>
  )
}
