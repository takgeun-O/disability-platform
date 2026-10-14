'use client';

import { createContext, useCallback, useContext, useRef, type ReactNode } from 'react';
import { useNavigationGuard } from './NavigationGuard';

type Handoff = { publish: () => void; read: () => number | null; consume: (ticket: number) => void };
const Context = createContext<Handoff | null>(null);

// 같은 탭의 확인된 인증 → 로그인 탐색 한 번만 잇는다. 계정/토큰/비밀번호는 저장하지 않는다.
// 새로고침 시 사라지며 다른 탐색이 먼저 일어나면 버전 불일치로 폐기한다.
export function VerificationNoticeProvider({ children }: { children: ReactNode }) {
  const { getApprovalVersion } = useNavigationGuard();
  const pending = useRef<number | null>(null);
  const publish = useCallback(() => { pending.current = getApprovalVersion(); }, [getApprovalVersion]);
  const read = useCallback(() => pending.current === getApprovalVersion() ? pending.current : null, [getApprovalVersion]);
  const consume = useCallback((ticket: number) => { if (pending.current === ticket) pending.current = null; }, []);
  return <Context.Provider value={{ publish, read, consume }}>{children}</Context.Provider>;
}
export function useVerificationNotice() {
  const value = useContext(Context);
  if (!value) throw new Error('VerificationNoticeProvider is required');
  return value;
}
