import type { RefObject } from 'react';

export function FieldLabel({ htmlFor, text }: { htmlFor: string; text: string }) {
  return <label className="auth-label" htmlFor={htmlFor}>{text} <span aria-hidden="true" className="auth-required">(필수)</span></label>;
}
export function FieldError({ id, msg }: { id: string; msg: string }) {
  return msg ? <p id={id} role="alert" className="auth-field-error">{msg}</p> : null;
}

// 표시 여부와 입력값은 부모가 관리한다. ref/오류 연결/자동완성도 원래 입력에 전달한다.
export function PasswordInput({ id, value, onChange, show, onToggle, hasError, describedBy,
  placeholder, inputRef, disabled, name, autoComplete = 'new-password',
}: {
  id: string; value: string; onChange: (value: string) => void;
  show: boolean; onToggle: () => void; hasError: boolean; describedBy?: string;
  placeholder?: string; inputRef?: RefObject<HTMLInputElement | null>; disabled?: boolean;
  name?: string; autoComplete?: 'new-password' | 'current-password';
}) {
  return <div className="auth-password">
    <input className="auth-input" ref={inputRef} id={id} name={name} type={show ? 'text' : 'password'}
      autoComplete={autoComplete} value={value} onChange={event => onChange(event.target.value)}
      placeholder={placeholder} disabled={disabled} aria-required="true"
      aria-invalid={hasError} aria-describedby={describedBy} />
    <button className="auth-password-toggle" type="button" onClick={onToggle} disabled={disabled}
      aria-label={show ? '비밀번호 숨기기' : '비밀번호 표시'} aria-pressed={show} aria-controls={id}>
      {show ? '숨기기' : '표시'}
    </button>
  </div>;
}
