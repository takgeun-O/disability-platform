'use client';

import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { createAuthSession, type AuthState } from '@/lib/auth-session';

type Session = ReturnType<typeof createAuthSession>;
type AuthContextValue = AuthState & Pick<Session, 'refresh' | 'signIn' | 'signOut'>;
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session] = useState(() => createAuthSession());
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getServerSnapshot);
  useEffect(() => {
    session.start(); // 초기 진입·새로고침마다 서버 쿠키로 /me 조회
    return session.stop;
  }, [session]);
  return (
    <AuthContext.Provider value={{ ...state, refresh: session.refresh, signIn: session.signIn, signOut: session.signOut }}>
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('AuthProvider is required');
  return auth;
}
