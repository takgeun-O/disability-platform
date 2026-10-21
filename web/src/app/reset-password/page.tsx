import type { Metadata } from 'next';
import PasswordReset from '@/features/PasswordReset';
import ResetLinkFailure from '@/features/ResetLinkFailure';
import { ResetLinkInvalid } from '@/components/auth/ResetLinkStatus';
import { resetToken } from '@/lib/password-reset';
import { validateResetLink } from '@/lib/password-reset-validation';

export const metadata: Metadata = { title: '새 비밀번호 설정 | IYUM', referrer: 'no-referrer' };
export default async function Page({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const token = resetToken((await searchParams).token);
  if (!token) return <ResetLinkInvalid />;
  // 무JS 문서에서도 보이도록 Suspense/loading 경계 없이 확인 결과까지 기다린다.
  // 쿠키·CSRF를 발급/전달하지 않는 공개 조회이며 매 진입/새로고침/재시도에 다시 확인한다.
  const result = await validateResetLink(token, process.env.IYUM_API_INTERNAL_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL);
  if (result === 'invalid') return <ResetLinkInvalid />;
  if (result !== 'valid') return <ResetLinkFailure token={token} limited={result === 'limited'} />;
  return <PasswordReset key={token} mode="complete" token={token} />;
}
