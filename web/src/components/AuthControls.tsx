'use client';

import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import Link from '@/components/AppLink';
import { useAuth } from '@/components/AuthProvider';
import { startPausableToastLifetime } from '@/lib/toast-lifetime';
import { AuthApiError } from '@/lib/auth-api';

type Feedback = { id: number; message: string; error: boolean; memberId: number | null };

// The owner keeps the timer when the same notice moves between header and mobile drawer.
function FeedbackNotice({ feedback, leaving, onDismiss, observe }: { feedback: Feedback & { info?: boolean }; leaving: boolean; onDismiss: () => void; observe: (node: HTMLDivElement) => () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const header = document.querySelector('.iyum-header');
    const position = () => { if (ref.current) ref.current.style.top = `${Math.max(0, header?.getBoundingClientRect().bottom ?? 0) + 16}px`; };
    position(); const observer = new ResizeObserver(position); if (header) observer.observe(header);
    window.addEventListener('resize', position); window.addEventListener('scroll', position, { passive: true });
    return () => { observer.disconnect(); window.removeEventListener('resize', position); window.removeEventListener('scroll', position); };
  }, []);
  useEffect(() => {
    const node = ref.current;
    if (node) return observe(node);
  }, [observe]);
  return <div ref={ref} role={feedback.error ? 'alert' : 'status'} aria-atomic="true"
    className={`iyum-auth-notice${feedback.error ? ' is-error' : ' is-compact'}${feedback.info ? ' is-info' : ''}${leaving ? ' is-leaving' : ''}`}>
    {!feedback.error && <svg className="iyum-auth-notice-icon" width="22" height="22" viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" />{feedback.info ? <path d="M12 10v7m0-10v1" /> : <path d="m7 12 3 3 7-7" />}</svg>}
    <span className="iyum-notice-message">{feedback.message}</span>
    <button type="button" aria-label="알림 닫기" onClick={onDismiss} className="iyum-notice-close"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg></button>
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
  if (feedback && !feedback.error && auth.status === 'authenticated') setFeedback(null);
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
            {auth.member.role === 'ADMIN' && !auth.pending && !auth.memberRequests.paused && <Link href="/admin/posts" onNavigate={event => { if (pathname === "/admin/posts") { event.preventDefault(); onCurrentPage?.(); } }} className="iyum-button" aria-current={pathname.startsWith('/admin') ? 'page' : undefined}>관리자</Link>}
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
  const selected: (Feedback & { info?: boolean }) | null = auth.withdrawalNotice
    ? { id: -1, message: '회원 탈퇴가 완료되었습니다.', error: false, memberId: null }
    : auth.expiryNotice && auth.status === 'anonymous'
      ? { id: auth.expiryNotice.id, message: '로그인 시간이 만료되었어요. 다시 로그인해 주세요.', error: false, info: true, memberId: null }
      : feedbackVisible ? feedback : null;
  const noticeKey = selected ? `${auth.withdrawalNotice ? 'withdrawal' : selected.info ? 'expired' : 'feedback'}:${selected.id}` : '';
  const isError = selected?.error ?? false;
  const [leavingKey, setLeavingKey] = useState('');
  const lifetime = useRef<ReturnType<typeof startPausableToastLifetime> | null>(null);
  const pointer = useRef<{ key: string; x: number; y: number } | null>(null);
  const pauseOwner = useRef<{ key: string; node: HTMLDivElement; paused: boolean } | null>(null);
  const observeNotice = useCallback((node: HTMLDivElement) => {
    // Coordinates only belong to this notice, including its mobile DOM relocations.
    if (pointer.current?.key !== noticeKey) pointer.current = null;
    const owner = { key: noticeKey, node, paused: false };
    pauseOwner.current = owner;
    const reconcile = () => {
      if (pauseOwner.current !== owner) return;
      const point = pointer.current;
      const hovered = point ? node.contains(document.elementFromPoint(point.x, point.y)) : node.matches(':hover');
      owner.paused = hovered || node.contains(document.activeElement);
      if (owner.paused) lifetime.current?.pause(); else lifetime.current?.resume();
    };
    // A remounted node may already be under the pointer without a mouseenter.
    // Read actual DOM state after the dialog's focus/position layout effects too.
    const trackPointer = (event: PointerEvent) => {
      if (event.pointerType === 'touch') return;
      pointer.current = { key: noticeKey, x: event.clientX, y: event.clientY };
      reconcile();
    };
    const leaveDocument = () => { pointer.current = null; reconcile(); };
    document.addEventListener('pointermove', trackPointer, { passive: true });
    document.addEventListener('pointerleave', leaveDocument);
    reconcile();
    const frame = requestAnimationFrame(reconcile);
    const events = ['mouseenter', 'mouseleave', 'focusin', 'focusout'] as const;
    for (const event of events) node.addEventListener(event, reconcile);
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener('pointermove', trackPointer);
      document.removeEventListener('pointerleave', leaveDocument);
      for (const event of events) node.removeEventListener(event, reconcile);
      if (pauseOwner.current !== owner) return;
      pauseOwner.current = null;
      lifetime.current?.resume();
    };
  }, [noticeKey]);
  const dismissWithdrawal = auth.dismissWithdrawalNotice, dismissExpiry = auth.dismissExpiryNotice;
  const dismissNotice = useCallback((key: string) => {
    const [kind, id] = key.split(':');
    if (kind === 'withdrawal') dismissWithdrawal();
    else if (kind === 'expired') dismissExpiry(Number(id));
    else dismissFeedback(Number(id));
  }, [dismissWithdrawal, dismissExpiry, dismissFeedback]);
  useEffect(() => {
    if (!noticeKey) { pointer.current = null; return; }
    if (isError) return;
    const timer = startPausableToastLifetime(value => setLeavingKey(value ? noticeKey : ''), () => dismissNotice(noticeKey));
    lifetime.current = timer;
    if (pauseOwner.current?.key === noticeKey && pauseOwner.current.paused) timer.pause();
    return () => { timer.dispose(); if (lifetime.current === timer) lifetime.current = null; };
  }, [noticeKey, isError, dismissNotice]);
  const notice = selected ? <FeedbackNotice key={noticeKey} feedback={selected} leaving={leavingKey === noticeKey} onDismiss={() => dismissNotice(noticeKey)} observe={observeNotice} /> : null;
  return render ? render({ member, logout, notice }) : <div className="iyum-auth-controls" aria-label="회원 메뉴">{member}{logout}{notice}</div>;
}
