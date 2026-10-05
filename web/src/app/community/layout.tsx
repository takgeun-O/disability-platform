import {MockPostsProvider} from '@/lib/mock-posts';
export default function CommunityLayout({children}: {children: React.ReactNode}) {return <MockPostsProvider>{children}</MockPostsProvider>;}
