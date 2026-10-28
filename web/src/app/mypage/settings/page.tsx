import type { Metadata } from 'next'
import MyPage from '@/features/MyPage'
import '@/styles/mypage.css'
import '@/styles/auth.css'
export const metadata: Metadata = { title: '프로필·계정 설정 | IYUM', robots: { index: false, follow: false } }
// Only the authenticated client fetches private data (no-store).
export default function Page() { return <MyPage settings /> }
