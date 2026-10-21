"use client";
import { useTransition } from 'react';
import { useRouter } from 'next/navigation';
import Link from '@/components/AppLink';
import AuthFrame from '@/components/auth/AuthFrame';

export default function ResetLinkFailure({ token, limited }: { token: string; limited: boolean }) {
  const [pending, startTransition] = useTransition();
  const router = useRouter();
  return <AuthFrame title="재설정 링크 확인" description={limited
    ? '확인 요청이 많습니다. 잠시 후 다시 시도해 주세요.'
    : '링크를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'}>
    {/* 일반 GET 폼으로 무JS에서도 다시 조회한다. 서버에는 토큰만 보내며 자동 재시도하지 않는다. */}
    <form action="/reset-password" method="get" onSubmit={event => { event.preventDefault(); if (!pending) startTransition(() => router.refresh()); }} aria-busy={pending}>
      <input type="hidden" name="token" value={token} />
      <p role="status">{pending ? '재설정 링크를 확인하고 있습니다.' : ''}</p>
      <button className="iyum-button iyum-button-primary auth-submit" disabled={pending}>{pending ? '확인 중…' : '다시 확인'}</button>
    </form>
    <nav className="auth-links" aria-label="계정 관련 이동"><Link href="/login" className="iyum-text-link">로그인 화면으로 돌아가기</Link></nav>
  </AuthFrame>;
}
