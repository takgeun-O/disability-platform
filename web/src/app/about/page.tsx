import type { Metadata } from 'next';
import AboutPage from '@/features/AboutPage';
export const metadata: Metadata = {
  title: 'IYUM 소개 | IYUM',
  description: '장애 관련 정보와 질문·경험을 연결하는 IYUM의 목적, 현재 제공 기능과 앞으로의 방향을 소개합니다.',
};
export default function Page() { return <AboutPage />; }
