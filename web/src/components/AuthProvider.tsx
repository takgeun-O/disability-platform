'use client';

import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { createMemberActivityCache } from '@/lib/member-activity';
import { createAuthSession, type AuthState } from '@/lib/auth-session';

type Session = ReturnType<typeof createAuthSession>;
type AuthContextValue = { activityCache: ReturnType<typeof createMemberActivityCache> } & AuthState & Pick<Session, 'refresh' | 'signIn' | 'signOut' | 'updateNickname' | 'expire'>;
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [{ session, activityCache }] = useState(() => {
    const session = createAuthSession(), activityCache = createMemberActivityCache();
    // Clear synchronously on auth transitions, before any private screen can reuse old data.
    session.subscribe(() => {
      const state = session.getSnapshot();
      activityCache.setOwner(state.status === 'authenticated' && !state.pending ? state.member?.memberId ?? null : null);
    });
    return { session, activityCache };
  });
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getServerSnapshot);
  useEffect(() => {
    session.start(); // 초기 진입·새로고침마다 서버 쿠키로 /me 조회
    return () => { session.stop(); activityCache.setOwner(null); };
  }, [session, activityCache]);
  return (
    <AuthContext.Provider value={{ ...state, activityCache, refresh: session.refresh, signIn: session.signIn, signOut: session.signOut, updateNickname: session.updateNickname, expire: session.expire }}>
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('AuthProvider is required');
  return auth;
}
