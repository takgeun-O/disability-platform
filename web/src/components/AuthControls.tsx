'use client';

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Link from '@/components/AppLink';
import { useAuth } from '@/components/AuthProvider';
import { useNavigationGuard } from '@/components/NavigationGuard';
import { AuthApiError } from '@/lib/auth-api';

type Feedback = { id: number; message: string; error: boolean; memberId: number | null };

// 알림마다 별도 수명과 타이머를 가진다. 교체·숨김·닫기 시 key에 의해 해제된다.
function FeedbackNotice({ feedback, onDismiss }: { feedback: Feedback; onDismiss: (id: number) => void }) {
  const noticeRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const notice = noticeRef.current;
    if (!notice || feedback.error) return; // 오류는 사용자가 닫거나 후속 동작으로 해소한다.
    let remaining = 5_000;
    let started = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let disposed = false;
    const pause = () => {
      if (timer === undefined) return;
      clearTimeout(timer);
      timer = undefined;
      remaining = Math.max(0, remaining - (performance.now() - started));
    };
    const resume = () => {
      if (disposed || timer !== undefined) return;
      started = performance.now();
      timer = setTimeout(() => {
        timer = undefined;
        if (!disposed) onDismiss(feedback.id);
      }, remaining);
    };
    const onFocusOut = (event: FocusEvent) => {
      if (!notice.contains(event.relatedTarget as Node | null)) resume();
    };
    notice.addEventListener('focusin', pause);
    notice.addEventListener('focusout', onFocusOut);
    if (!notice.contains(document.activeElement)) resume();
    return () => {
      disposed = true;
      pause();
      notice.removeEventListener('focusin', pause);
      notice.removeEventListener('focusout', onFocusOut);
    };
  }, [feedback.id, feedback.error, onDismiss]);

  return <div ref={noticeRef} role={feedback.error ? 'alert' : 'status'} aria-atomic="true"
    style={{ position: 'absolute', right: 0, top: '100%', zIndex: 20, width: 'min(320px, 85vw)',
      padding: 12, background: '#FAFAF8', border: '1px solid #D0CEC9', fontSize: 12, lineHeight: 1.6,
      color: feedback.error ? '#B91C1C' : '#4A4845', display: 'flex', alignItems: 'flex-start', gap: 8 }}>
    <span style={{ flex: 1 }}>{feedback.message}</span>
    <button type="button" aria-label="알림 닫기" onClick={() => onDismiss(feedback.id)}
      style={{ flexShrink: 0, minHeight: 24, padding: '2px 4px', color: '#4A4845', textDecoration: 'underline', cursor: 'pointer' }}>닫기</button>
  </div>;
}

export default function AuthControls() {
  const auth = useAuth();
  const guard = useNavigationGuard();
  const router = useRouter();
  const pathname = usePathname();
  const routeVersionRef = useRef(0);
  useLayoutEffect(() => { routeVersionRef.current++; }, [pathname]);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const feedbackIdRef = useRef(0);
  const dismissFeedback = useCallback((id: number) => {
    // 이미 대기열에 있던 이전 타이머도 새 알림을 지울 수 없다.
    setFeedback(current => current?.id === id ? null : current);
  }, []);
  const showFeedback = (next: Omit<Feedback, 'id'>) => setFeedback({ ...next, id: ++feedbackIdRef.current });
  const mountedRef = useRef(false);
  const inFlightRef = useRef(false);
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

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
      showFeedback({ error: false, memberId: null, message: result.confirmedBy === 'logout'
        ? '로그아웃되었습니다.' : '로그아웃 응답을 확인하지 못했지만 현재 비로그인 상태임을 확인했습니다.' });
      // 요청 이후 이동한 화면은 유지한다. 같은 화면에서도 새 편집의 동의를 다시 받는다.
      // 이 확인을 취소해도 서버에서 완료된 로그아웃 상태는 그대로 유지한다.
      if (routeVersionRef.current === routeVersion) guard.request(() => router.replace('/'));
    } catch (error) {
      restoreGuard();
      if (mountedRef.current) showFeedback({ error: true, memberId: auth.member?.memberId ?? null,
        message: error instanceof AuthApiError ? error.message : '로그아웃 결과를 확인하지 못했습니다. 로그인 상태를 다시 확인해 주세요.' });
    } finally { inFlightRef.current = false; }
  }
  const feedbackVisible = feedback && (feedback.error
    ? auth.status === 'error' || feedback.memberId === (auth.member?.memberId ?? null)
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
      {feedbackVisible && <FeedbackNotice key={feedback.id} feedback={feedback} onDismiss={dismissFeedback} />}
    </div>
  );
}
