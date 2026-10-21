import type { Metadata } from 'next';
import { connection } from 'next/server';
import PasswordReset from '@/features/PasswordReset';
export const metadata: Metadata = { title: '비밀번호 찾기 | IYUM', referrer: 'no-referrer' };
export default async function Page() { await connection(); return <PasswordReset mode="request" />; }
