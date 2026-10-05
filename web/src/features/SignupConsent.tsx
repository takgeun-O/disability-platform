"use client";

import { useState, useRef, useId, useEffect } from 'react'
import { useRouter } from 'next/navigation';

// ─── Consent item row ─────────────────────────────────────────────────────────

function ConsentItem({
  id, label, badge, checked, onChange,
}: {
  id: string
  label: string
  badge: '필수' | '선택'
  checked: boolean
  onChange: (v: boolean) => void
}) {
  const isRequired = badge === '필수'
  return (
    <div
      style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '11px 0', borderBottom: '1px solid #E8E6E1',
      }}
    >
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        style={{ width: 17, height: 17, cursor: 'pointer', accentColor: '#1A1918', flexShrink: 0 }}
      />
      <label
        htmlFor={id}
        style={{
          fontSize: 14, color: '#1A1918', cursor: 'pointer',
          flex: 1, display: 'flex', alignItems: 'center', gap: 8,
          userSelect: 'none',
        }}
      >
        {label}
        <span
          style={{
            fontSize: 11, fontWeight: 500,
            color: isRequired ? '#B91C1C' : '#908D88',
          }}
          aria-label={isRequired ? '필수 동의 항목' : '선택 동의 항목'}
        >
          [{badge}]
        </span>
      </label>
    </div>
  )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function SignupConsent() {
  const router = useRouter()
  const uid = useId()

  const [terms, setTerms]         = useState(false)
  const [privacy, setPrivacy]     = useState(false)
  const [marketing, setMarketing] = useState(false)
  const [showError, setShowError] = useState(false)

  const allChecked    = terms && privacy && marketing
  const someChecked   = terms || privacy || marketing
  const requiredReady = terms && privacy

  const allRef = useRef<HTMLInputElement>(null)

  // Indeterminate visual state for 전체 동의 when only some items are checked
  useEffect(() => {
    if (!allRef.current) return
    allRef.current.indeterminate = someChecked && !allChecked
  }, [someChecked, allChecked])

  // ── Handlers ───────────────────────────────────────────────────────────────

  function handleAllConsent(e: React.ChangeEvent<HTMLInputElement>) {
    const v = e.target.checked
    setTerms(v)
    setPrivacy(v)
    setMarketing(v)
    if (v) setShowError(false)
  }

  function handleTerms(v: boolean) {
    setTerms(v)
    // Clear error once required condition becomes satisfied
    if (v && privacy) setShowError(false)
  }

  function handlePrivacy(v: boolean) {
    setPrivacy(v)
    if (v && terms) setShowError(false)
  }

  function handleMarketing(v: boolean) {
    setMarketing(v)
    // Marketing is optional — never affects the error state
  }

  function handleNext() {
    if (!requiredReady) {
      setShowError(true)
      return
    }
    router.push('/register/info')
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <main>
      <div style={{ maxWidth: 500, margin: '0 auto', padding: '60px 24px 80px' }}>

        {/* H1 */}
        <h1
          style={{
            fontSize: 22, fontWeight: 700, color: '#1A1918',
            letterSpacing: '-0.01em', marginBottom: 8,
          }}
        >
          회원가입
        </h1>
        <p style={{ fontSize: 13, color: '#908D88', marginBottom: 40 }}>
          서비스 이용을 위해 약관을 확인하고 동의해 주세요.
        </p>

        {/* Agreement group — fieldset for semantic grouping */}
        <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
          <legend className="sr-only">약관 동의</legend>

          {/* ── 전체 동의 ─────────────────────────────────────────── */}
          <div
            style={{
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '13px 16px',
              borderTop: '1px solid #D0CEC9',
              borderBottom: '1px solid #D0CEC9',
              borderLeft: '1px solid #D0CEC9',
              borderRight: '1px solid #D0CEC9',
              borderRadius: 2,
              marginBottom: 16,
            }}
          >
            <input
              ref={allRef}
              id={`${uid}-all`}
              type="checkbox"
              checked={allChecked}
              onChange={handleAllConsent}
              style={{ width: 17, height: 17, cursor: 'pointer', accentColor: '#1A1918', flexShrink: 0 }}
            />
            <label
              htmlFor={`${uid}-all`}
              style={{
                fontSize: 15, fontWeight: 600, color: '#1A1918',
                cursor: 'pointer', flex: 1, userSelect: 'none',
              }}
            >
              전체 동의
            </label>
          </div>

          {/* ── Individual consent items ───────────────────────────── */}
          <div>
            <ConsentItem
              id={`${uid}-terms`}
              label="이용약관"
              badge="필수"
              checked={terms}
              onChange={handleTerms}
            />
            <ConsentItem
              id={`${uid}-privacy`}
              label="개인정보 수집 · 이용"
              badge="필수"
              checked={privacy}
              onChange={handlePrivacy}
            />
            <ConsentItem
              id={`${uid}-marketing`}
              label="마케팅 정보 수신 동의"
              badge="선택"
              checked={marketing}
              onChange={handleMarketing}
            />
          </div>

          {/* ── Required consent validation error ─────────────────── */}
          {showError && (
            <p
              role="alert"
              style={{ fontSize: 12, color: '#B91C1C', marginTop: 12, marginBottom: 0 }}
            >
              필수 약관에 동의해야 가입할 수 있습니다.
            </p>
          )}

        </fieldset>

        {/* ── Action area ────────────────────────────────────────────── */}
        <div
          style={{
            display: 'flex', justifyContent: 'flex-end',
            alignItems: 'center', gap: 12, marginTop: 36,
          }}
        >
          {/*
            취소 — Cancel destination is unresolved per Product Contract.
            This button is intentionally non-navigating in the prototype.
          */}
          <button
            type="button"
            style={{
              fontSize: 14, color: '#4A4845',
              background: 'none', border: 'none',
              cursor: 'pointer', fontFamily: 'inherit',
              padding: '10px 16px',
            }}
          >
            취소
          </button>

          {/* 다음 — primary action → SCR-AUTH-003 when required consent is met */}
          <button
            type="button"
            onClick={handleNext}
            style={{
              fontSize: 14, fontWeight: 700, color: '#FAFAF8',
              backgroundColor: '#1A1918',
              border: 'none', borderRadius: 2,
              padding: '10px 28px',
              cursor: 'pointer', fontFamily: 'inherit',
              letterSpacing: '-0.01em',
            }}
          >
            다음
          </button>
        </div>

      </div>
    </main>
  )
}
