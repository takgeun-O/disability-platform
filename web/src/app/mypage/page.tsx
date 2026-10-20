import type { Metadata } from 'next'
import MyPage from '@/features/MyPage'
import '@/styles/mypage.css'
import '@/styles/auth.css'
export const metadata: Metadata = { title: '마이페이지 | IYUM', description: '내 프로필과 활동을 확인하세요.', robots: { index: false, follow: false } }
// No private data is fetched on the server or embedded in cached HTML.
export default function Page() { return <MyPage /> }
