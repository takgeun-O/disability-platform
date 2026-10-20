'use client';

import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import Link from '@/components/AppLink';
import { useAuth } from '@/components/AuthProvider';
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
    className={`iyum-auth-notice${feedback.error ? ' is-error' : ''}`}>
    <span className="iyum-notice-message">{feedback.message}</span>
    <button type="button" aria-label="알림 닫기" onClick={() => onDismiss(feedback.id)}
      className="iyum-notice-close">닫기</button>
  </div>;
}

export default function AuthControls({ render, onLogoutSuccess, onCurrentPage }: {
  render?: (parts: { member: ReactNode; logout: ReactNode; notice: ReactNode }) => ReactNode;
  onLogoutSuccess?: () => void;
  onCurrentPage?: () => void;
}) {
  const pathname = usePathname();
  const auth = useAuth();
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

  async function handleLogout() {
    if (inFlightRef.current || auth.pending) return;
    inFlightRef.current = true;
    // 로그아웃은 탐색이 아니다. 현재 폼의 dirty/요청 수명과 URL·스크롤을 유지한다.
    setFeedback(null);
    try {
      const result = await auth.signOut();
      if (!mountedRef.current) return;
      if (result.kind === 'ignored') return;
      showFeedback({ error: false, memberId: null, message: result.confirmedBy === 'logout'
        ? '로그아웃되었습니다.' : '로그아웃 응답을 확인하지 못했지만 현재 비로그인 상태임을 확인했습니다.' });
      onLogoutSuccess?.();
    } catch (error) {
      if (mountedRef.current) showFeedback({ error: true, memberId: auth.member?.memberId ?? null,
        message: error instanceof AuthApiError ? error.message : '로그아웃 결과를 확인하지 못했습니다. 로그인 상태를 다시 확인해 주세요.' });
    } finally { inFlightRef.current = false; }
  }
  const feedbackVisible = feedback && (feedback.error
    ? auth.status === 'error' || feedback.memberId === (auth.member?.memberId ?? null)
    : auth.status === 'anonymous');

  const member = <div className="iyum-member-menu">
        {auth.status === 'checking' || (auth.pending === 'login' && auth.status !== 'authenticated') ? (
          <span role="status" className="iyum-auth-status">
            {auth.pending === 'login' ? '로그인 처리 중…' : '로그인 확인 중…'}
          </span>
        ) : auth.status === 'error' ? (
          <div className="iyum-auth-status" role="status">
            <p>로그인 상태를 확인하지 못했습니다.</p>
            <button type="button" onClick={() => { setFeedback(null); void auth.refresh(); }} disabled={!!auth.pending}
              className="iyum-auth-status-retry">상태 다시 확인</button>
          </div>
        ) : auth.status === 'authenticated' && auth.member ? (
          <>
            <span className="iyum-member-name"><span>{auth.member.nickname}</span><span className="iyum-member-honorific">님</span></span>
            <Link href="/mypage" onNavigate={event => { if (pathname === "/mypage") { event.preventDefault(); onCurrentPage?.(); } }} className="iyum-button iyum-mypage-link" aria-current={pathname === '/mypage' ? 'page' : undefined}>마이페이지</Link>
          </>
        ) : (
          <>
            <Link href="/login" className="iyum-button iyum-auth-login">로그인</Link>
            <Link href="/register" className="iyum-button iyum-button-outline">회원가입</Link>
          </>
        )}
      </div>;
  const logout = auth.status === 'authenticated' && auth.member ? <button type="button" disabled={!!auth.pending}
    onClick={() => { void handleLogout(); }} className="iyum-button iyum-auth-logout">
    {auth.pending === 'logout' ? '로그아웃 중…' : '로그아웃'}
  </button> : null;
  const notice = auth.withdrawalNotice ? <FeedbackNotice feedback={{ id: -1, message: '회원 탈퇴가 완료되었습니다.', error: false, memberId: null }} onDismiss={auth.dismissWithdrawalNotice} /> : feedbackVisible ? <FeedbackNotice key={feedback.id} feedback={feedback} onDismiss={dismissFeedback} /> : null;
  return render ? render({ member, logout, notice }) : <div className="iyum-auth-controls" aria-label="회원 메뉴">{member}{logout}{notice}</div>;
}
