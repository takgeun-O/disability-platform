"use client";
import { useEffect } from 'react';
import AuthFrame from '@/components/auth/AuthFrame';
import Link from '@/components/AppLink';
import { useAuth } from '@/components/AuthProvider';
export default function PasswordResetComplete() {
  const { refresh } = useAuth();
  useEffect(() => { void refresh(); }, [refresh]);
  return <AuthFrame title="비밀번호가 변경되었습니다">
    <p className="auth-feedback" role="status">새 비밀번호로 다시 로그인해 주세요.</p>
    <Link href="/login" className="iyum-button iyum-button-primary auth-submit">로그인으로 이동</Link>
  </AuthFrame>;
}
