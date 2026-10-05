import type { ReactNode } from 'react';
import { SignupFlowProvider } from '@/features/SignupFlow';

export default function RegisterLayout({ children }: { children: ReactNode }) {
  return <SignupFlowProvider>{children}</SignupFlowProvider>;
}
