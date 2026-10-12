'use client';

import { useEffect, useRef, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Link from '@/components/AppLink';
import { useAuth } from '@/components/AuthProvider';
import { useNavigationGuard } from '@/components/NavigationGuard';
import { AuthApiError } from '@/lib/auth-api';

type Feedback = { message: string; error: boolean; memberId: number | null };

export default function AuthControls() {
  const auth = useAuth();
  const guard = useNavigationGuard();
  const router = useRouter();
  const pathname = usePathname();
  const routeVersionRef = useRef(0);
  useEffect(() => { routeVersionRef.current++; }, [pathname]);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const mountedRef = useRef(false);
  const inFlightRef = useRef(false);
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);
  useEffect(() => { feedbackRef.current?.focus(); }, [feedback]);

  async function handleLogout(restoreDirty: boolean) {
    if (inFlightRef.current || auth.pending) return;
    inFlightRef.current = true;
    const routeVersion = routeVersionRef.current;
    const restoreGuard = () => {
      // 기다리는 동안 다른 화면으로 이동했다면 이전 작성 화면의 dirty를 되살리지 않는다.
      if (restoreDirty && mountedRef.current && routeVersionRef.current === routeVersion) guard.setDirty(true);
    };
    setFeedback(null);
    try {
      const result = await auth.signOut();
      if (!mountedRef.current) return;
      if (result.kind === 'ignored') { restoreGuard(); return; }
      setFeedback({ error: false, memberId: null, message: result.confirmedBy === 'logout'
        ? '로그아웃되었습니다.' : '로그아웃 응답을 확인하지 못했지만 현재 비로그인 상태임을 확인했습니다.' });
      // 작성 중 이탈 확인은 POST 전에 수행한다. 성공하면 공개 경로로 이동한다.
      router.replace('/');
    } catch (error) {
      restoreGuard();
      if (mountedRef.current) setFeedback({ error: true, memberId: auth.member?.memberId ?? null,
        message: error instanceof AuthApiError ? error.message : '로그아웃 결과를 확인하지 못했습니다. 로그인 상태를 다시 확인해 주세요.' });
    } finally { inFlightRef.current = false; }
  }
  const feedbackVisible = feedback && (feedback.error
    ? feedback.memberId === (auth.member?.memberId ?? null)
    : auth.status === 'anonymous');

  return (
    <div style={{ position: 'relative', maxWidth: 280 }}>
      <div className="flex items-center gap-2" aria-label="회원 메뉴">
        {auth.status === 'checking' || (auth.pending === 'login' && auth.status !== 'authenticated') ? (
          <span role="status" className="text-sm" style={{ color: '#4A4845' }}>
            {auth.pending === 'login' ? '로그인 처리 중…' : '로그인 확인 중…'}
          </span>
        ) : auth.status === 'error' ? (
          <div className="text-xs" role="status">
            <p>로그인 상태를 확인하지 못했습니다.</p>
            <button type="button" onClick={() => { setFeedback(null); void auth.refresh(); }} disabled={!!auth.pending}
              style={{ textDecoration: 'underline', cursor: 'pointer' }}>상태 다시 확인</button>
          </div>
        ) : auth.status === 'authenticated' && auth.member ? (
          <>
            <span className="text-sm" style={{ overflowWrap: 'anywhere' }}>{auth.member.nickname}님</span>
            <button type="button" disabled={!!auth.pending} onClick={() => {
              if (inFlightRef.current || auth.pending) return;
              // NavigationGuard의 기존 작성 중 확인창에서 취소하면 POST도 보내지 않는다.
              const wasDirty = guard.isDirty();
              guard.request(() => { void handleLogout(wasDirty); });
            }} className="text-sm font-medium px-3 py-1.5"
              style={{ color: '#1A1918', cursor: auth.pending ? 'wait' : 'pointer', whiteSpace: 'nowrap' }}>
              {auth.pending === 'logout' ? '로그아웃 중…' : '로그아웃'}
            </button>
          </>
        ) : (
          <>
            <Link href="/login" className="text-sm font-medium px-3 py-1.5 hover:underline transition-colors"
              style={{ color: '#1A1918', textDecoration: 'none' }}>로그인</Link>
            <Link href="/register" className="text-sm font-medium px-3 py-1.5"
              style={{ backgroundColor: '#1E3A8A', color: '#FFFFFF', borderRadius: 2, textDecoration: 'none' }}>회원가입</Link>
          </>
        )}
      </div>
      {feedbackVisible && <p ref={feedbackRef} tabIndex={-1} role={feedback.error ? 'alert' : 'status'}
        style={{ position: 'absolute', right: 0, top: '100%', zIndex: 20, width: 'min(320px, 85vw)',
          padding: 12, background: '#FAFAF8', border: '1px solid #D0CEC9', fontSize: 12, lineHeight: 1.6,
          color: feedback.error ? '#B91C1C' : '#4A4845' }}>{feedback.message}</p>}
    </div>
  );
}
