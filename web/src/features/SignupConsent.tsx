"use client";

import { useState, useRef, useId, useEffect } from 'react'
import { useRouter } from 'next/navigation';
import { useNavigationGuard } from '@/components/NavigationGuard';
import { useSignupFlow } from './SignupFlow';
import { createAgreements } from '@/lib/signup-form';
import AuthFrame from '@/components/auth/AuthFrame';

// ─── Consent item row ─────────────────────────────────────────────────────────

function ConsentItem({
  id, label, badge, checked, onChange, disabled = false, errorId,
}: {
  id: string
  label: string
  badge: '필수' | '선택'
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
  errorId?: string
}) {
  const isRequired = badge === '필수'
  return (
    <div className="auth-consent-row">
      <input id={id} type="checkbox" checked={checked} disabled={disabled}
        aria-invalid={!!errorId} aria-describedby={errorId} onChange={e => onChange(e.target.checked)} />
      <label htmlFor={id}>{label}<span aria-label={isRequired ? '필수 동의 항목' : '선택 동의 항목'}>[{badge}]</span></label>
    </div>
  )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function SignupConsent() {
  const router = useRouter()
  const guard = useNavigationGuard()
  const uid = useId()
  const { agreements, chooseAgreements } = useSignupFlow()

  const [terms, setTerms] = useState(agreements?.find(item => item.termsCode === 'SERVICE_TERMS')?.agreed ?? false)
  const [privacy, setPrivacy] = useState(agreements?.find(item => item.termsCode === 'PRIVACY_COLLECTION_USE')?.agreed ?? false)
  const [showError, setShowError] = useState(false)

  const allChecked    = terms && privacy
  const someChecked   = terms || privacy
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

  function handleNext() {
    if (!requiredReady) {
      setShowError(true)
      return
    }
    chooseAgreements(createAgreements(terms, privacy))
    router.push('/register/info')
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <AuthFrame title="회원가입" step={1} description="서비스 이용을 위해 약관을 확인하고 동의해 주세요.">
      {/* 실제 체크박스와 fieldset을 유지해 Space/Tab 조작 및 일부 동의 상태를 전달한다. */}
      <fieldset>
        <legend className="sr-only">약관 동의</legend>
        <div className="auth-consent-row is-all">
          <input ref={allRef} id={`${uid}-all`} type="checkbox" checked={allChecked} onChange={handleAllConsent} />
          <label htmlFor={`${uid}-all`}>전체 동의</label>
        </div>
        <ConsentItem id={`${uid}-terms`} label="이용약관" badge="필수" checked={terms} onChange={handleTerms}
          errorId={showError && !terms ? `${uid}-consent-error` : undefined} />
        <ConsentItem id={`${uid}-privacy`} label="개인정보 수집 · 이용" badge="필수" checked={privacy} onChange={handlePrivacy}
          errorId={showError && !privacy ? `${uid}-consent-error` : undefined} />
        <ConsentItem id={`${uid}-marketing`} label="마케팅 정보 수신 동의" badge="선택" checked={false} disabled onChange={() => {}} />
        <p className="auth-hint auth-consent-note">현재 약관은 개발용 버전(dev-v1)입니다. 마케팅 정보 수신 동의는 준비 중입니다.</p>
        {showError && <p id={`${uid}-consent-error`} role="alert" className="auth-field-error">필수 약관에 동의해야 가입할 수 있습니다.</p>}
      </fieldset>
      <div className="auth-actions">
        <button className="iyum-button iyum-button-outline" type="button" onClick={() => guard.request(() => router.push('/'))}>취소</button>
        <button className="iyum-button iyum-button-primary" type="button" onClick={handleNext}>다음</button>
      </div>
    </AuthFrame>
  )
}
