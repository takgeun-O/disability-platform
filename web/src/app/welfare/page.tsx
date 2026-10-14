import type { Metadata } from 'next';
import WelfarePage from '@/features/WelfarePage';
export const metadata: Metadata = {
  title: '복지·지원정보 | IYUM',
  description: '지원 제도와 절차를 이해하고, 다음에 확인할 일을 준비하세요. 현재 보청기 건강보험 지원 안내를 제공합니다.',
};
export default function Page() { return <WelfarePage />; }
