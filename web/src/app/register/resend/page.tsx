import type { Metadata } from 'next';
import EmailVerificationResend from '@/features/EmailVerificationResend';

export const metadata: Metadata = { title: '인증 메일 재전송 | IYUM', referrer: 'no-referrer' };

export default function Page() {
  return <EmailVerificationResend />;
}
