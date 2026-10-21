import type { Metadata } from 'next';
import { connection } from 'next/server';
import PasswordResetComplete from '@/features/PasswordResetComplete';
export const metadata: Metadata = { title: '비밀번호 변경 완료 | IYUM', referrer: 'no-referrer' };
export default async function Page() { await connection(); return <PasswordResetComplete />; }
