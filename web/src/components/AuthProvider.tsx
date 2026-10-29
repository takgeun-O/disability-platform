'use client';

import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { createCommentCreated } from '@/lib/comment-created';
import { createCommentDrafts } from '@/lib/comment-drafts';
import { createPostLikes } from '@/lib/post-likes';
import { createMemberActivityCache } from '@/lib/member-activity';
import { createAuthSession, type AuthState } from '@/lib/auth-session';

type Session = ReturnType<typeof createAuthSession>;
type AuthContextValue = { commentCreated: ReturnType<typeof createCommentCreated>; commentDrafts: ReturnType<typeof createCommentDrafts>; commentLikes: ReturnType<typeof createPostLikes>; likes: ReturnType<typeof createPostLikes>; memberRequests: ReturnType<Session['getMemberRequestSnapshot']>; withdrawalNotice: boolean; dismissWithdrawalNotice: () => void; completeWithdrawal: (memberId: number) => boolean; activityCache: ReturnType<typeof createMemberActivityCache> } & AuthState & Pick<Session, 'refresh' | 'signIn' | 'signOut' | 'updateNickname' | 'expire' | 'captureMemberRequest' | 'beginCredentialChange'>;
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [{ session, activityCache, likes, commentLikes, commentDrafts, commentCreated }] = useState(() => {
    const session = createAuthSession(), activityCache = createMemberActivityCache(), likes = createPostLikes(), commentLikes = createPostLikes(), commentDrafts = createCommentDrafts(), commentCreated = createCommentCreated();
    // Clear synchronously on auth transitions, before any private screen can reuse old data.
    session.subscribe(() => {
      const state = session.getSnapshot();
      if (state.pending === 'login' || state.status === 'anonymous') { commentDrafts.setOwner(null); commentCreated.setOwner(null); }
      else if (state.status === 'authenticated') { commentDrafts.setOwner(state.member?.memberId ?? null); commentCreated.setOwner(state.member?.memberId ?? null); }
      likes.setContext(`${session.getMemberRequestSnapshot().revision}:${state.status}:${state.pending}:${state.member?.memberId}`);
      commentLikes.setContext(`${session.getMemberRequestSnapshot().revision}:${state.status}:${state.pending}:${state.member?.memberId}`);
      activityCache.setOwner(state.status === 'authenticated' && !state.pending ? state.member?.memberId ?? null : null);
    });
    return { session, activityCache, likes, commentLikes, commentDrafts, commentCreated };
  });
  const [withdrawalNotice, setWithdrawalNotice] = useState(false);
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getServerSnapshot);
  const memberRequests = useSyncExternalStore(session.subscribe, session.getMemberRequestSnapshot, session.getServerMemberRequestSnapshot);
  useEffect(() => {
    session.start(); // 초기 진입·새로고침마다 서버 쿠키로 /me 조회
    return () => { session.stop(); activityCache.setOwner(null); };
  }, [session, activityCache]);
  return (
    <AuthContext.Provider value={{ ...state, memberRequests, activityCache, likes, commentLikes, commentDrafts, commentCreated, withdrawalNotice, dismissWithdrawalNotice: () => setWithdrawalNotice(false), completeWithdrawal: (memberId) => { if (!session.expire(memberId)) return false; setWithdrawalNotice(true); return true; }, refresh: session.refresh, signIn: session.signIn, signOut: session.signOut, updateNickname: session.updateNickname, expire: session.expire, captureMemberRequest: session.captureMemberRequest, beginCredentialChange: session.beginCredentialChange }}>
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('AuthProvider is required');
  return auth;
}
