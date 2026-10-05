'use client';

import { createContext, useContext, useState, type ReactNode } from 'react';
import type { SignupAgreement } from '@/lib/signup-api';

type SignupFlow = {
  agreements: SignupAgreement[] | null;
  status: 'PENDING' | null;
  chooseAgreements: (agreements: SignupAgreement[]) => void;
  completeSignup: () => void;
};
const SignupFlowContext = createContext<SignupFlow | null>(null);

// 가입 경로 사이에서 약관 선택과 확인된 가입 결과만 공유합니다.
// 비밀번호는 입력 화면의 state에만 있고 여기나 브라우저 저장소에 보관하지 않습니다.
export function SignupFlowProvider({ children }: { children: ReactNode }) {
  const [agreements, setAgreements] = useState<SignupAgreement[] | null>(null);
  const [status, setStatus] = useState<'PENDING' | null>(null);
  return (
    <SignupFlowContext.Provider value={{
      agreements,
      status,
      chooseAgreements: (selected) => { setAgreements(selected); setStatus(null); },
      completeSignup: () => setStatus('PENDING'),
    }}>
      {children}
    </SignupFlowContext.Provider>
  );
}

export function useSignupFlow() {
  const flow = useContext(SignupFlowContext);
  if (!flow) throw new Error('SignupFlowProvider is required');
  return flow;
}
