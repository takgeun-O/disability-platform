import type { ReactNode } from 'react';
import '@/styles/auth.css';

// 표시만 담당한다. 가입 단계나 인증 성공 여부는 기존 화면의 상태가 결정한다.
export default function AuthFrame({ title, description, step, children }: {
  title: string;
  description?: ReactNode;
  step?: 1 | 2 | 3;
  children: ReactNode;
}) {
  return (
    <main id="main-content" className="auth-screen">
      <div className="auth-panel iyum-card">
        <header className="auth-heading">
          {step && <ol className="auth-steps" aria-label="회원가입 단계">
            {['약관 동의', '정보 입력', '이메일 인증'].map((label, index) => (
              <li key={label} aria-current={step === index + 1 ? 'step' : undefined}>
                <span aria-hidden="true">{index + 1}</span>{label}
              </li>
            ))}
          </ol>}
          <h1>{title}</h1>
          {description && <p>{description}</p>}
        </header>
        {children}
      </div>
    </main>
  );
}
