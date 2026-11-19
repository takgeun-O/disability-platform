import { Suspense } from 'react';
import HomeRecentPosts from '@/components/HomeRecentPosts';
import { readPublicFeed } from '@/lib/community-server';
import Image from 'next/image';
import HomeSearch from '@/components/HomeSearch';
import DeadlinePanel from '@/components/DeadlinePanel';
import Link from '@/components/AppLink';

import { HOME_RECENT_ID, HOME_RECENT_QUERY, searchHref } from '@/lib/community-posts';

async function RecentPosts() {
  const { data } = await readPublicFeed(HOME_RECENT_QUERY);
  return <HomeRecentPosts initialPosts={data?.items ?? null} />;
}

const SHORTCUTS = [
  { title: '지원 대상이 궁금해요', description: '보청기 지원 조건부터 확인하기', href: '/validation/hearing-aid-health-insurance', icon: 'people' },
  { title: '절차를 알고 싶어요', description: '지금 단계와 다음 할 일 살펴보기', href: '/welfare', icon: 'document' },
  { title: '경험을 듣고 싶어요', description: '비슷한 질문과 경험 찾아보기', href: '/community', icon: 'chat' },
];

function ShortcutIcon({ kind }: { kind: string }) {
  return <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === 'people' ? <><circle cx="12" cy="7" r="3"/><path d="M6 21v-4a6 6 0 0 1 12 0v4M4 9a2 2 0 0 0 0 4m16-4a2 2 0 0 1 0 4M2 21v-3m20 3v-3"/></> : kind === 'document' ? <><path d="M6 2h9l4 4v16H6zM14 2v5h5M9 12h7M9 16h5"/></> : <><path d="M21 12a8 8 0 0 1-8 8H7l-4 3 1-7a8 8 0 1 1 17-4Z"/><path d="M8 11h.01M12 11h.01M16 11h.01"/></>}
  </svg>;
}

export default function HomePage() {
  return <main id="main-content" className="iyum-home">
    <section className="iyum-hero" aria-labelledby="home-heading">
      <div className="iyum-hero-art" aria-hidden="true"><Image src="/images/home/iyum-glass-tree-hero-v1.png" alt="" fill sizes="(max-width: 600px) 600px, 1180px" preload /></div>
      <div className="iyum-container">
        <p className="iyum-eyebrow">경험이 지식으로 이어지는 곳</p>
        <h1 id="home-heading">지금 나에게 필요한<br className="iyum-mobile-break" /> 정보를 찾아보세요.</h1>
        <p className="iyum-hero-intro">복잡한 정보는 쉽게, 필요한 경험은 가까이.</p>
        <HomeSearch />
        <div className="iyum-suggestions"><span>추천 검색어</span>
          {['보청기 지원', '첫 착용', '센터 방문'].map(query => <Link key={query} href={searchHref(query)}>{query}</Link>)}
        </div>
      </div>
    </section>
    <section className="iyum-container iyum-shortcuts" aria-labelledby="shortcuts-heading">
      <h2 id="shortcuts-heading">어디서부터 시작할까요?</h2>
      <div className="iyum-shortcut-grid">{SHORTCUTS.map(item => <Link className="iyum-card iyum-shortcut" key={item.title} href={item.href}>
        <span className="iyum-shortcut-icon"><ShortcutIcon kind={item.icon} /></span>
        <span className="iyum-shortcut-copy"><strong>{item.title}</strong><span>{item.description}</span></span><span className="iyum-arrow" aria-hidden="true">→</span>
      </Link>)}</div>
    </section>
    <div className="iyum-container iyum-home-grid">
      <DeadlinePanel />
      <section id={HOME_RECENT_ID} className="iyum-community-preview" aria-labelledby="community-heading">
        <div className="iyum-section-heading">
          <h2 id="community-heading">커뮤니티</h2>
          <Link className="iyum-text-link" href="/community">전체 보기 <span aria-hidden="true">→</span></Link>
        </div>
        <div className="iyum-community-toolbar"><span>최근 글</span></div>
        <Suspense fallback={<p role="status">최근 글을 불러오는 중입니다.</p>}>
          <RecentPosts />
        </Suspense>
      </section>
      <section className="iyum-guides" aria-labelledby="guides-heading"><h2 id="guides-heading">주요 정보 가이드</h2>
        <Link className="iyum-card iyum-guide-link" href="/validation/hearing-aid-health-insurance">
          <Image className="iyum-guide-art" src="/images/home/iyum-guide-lounge-v1.png" alt="" fill sizes="(max-width: 900px) 600px, 420px" />
          <span className="iyum-shortcut-icon"><ShortcutIcon kind="document" /></span>
          <span className="iyum-guide-copy"><small>지원 안내</small><strong>보청기 건강보험 안내</strong><span>지원 대상부터 신청 절차까지</span><b>가이드 읽기 <span aria-hidden="true">→</span></b></span>
        </Link>
        <div className="iyum-card iyum-guide-upcoming">
          <Image className="iyum-guide-art" src="/images/home/iyum-guide-lounge-v1.png" alt="" fill sizes="(max-width: 900px) 600px, 420px" />
          <span className="iyum-shortcut-icon"><ShortcutIcon kind="chat" /></span>
          <div className="iyum-guide-copy"><span className="iyum-badge">준비 중</span><h3>센터 방문 전 질문</h3><p>상담에서 확인할 내용을 준비하고 있어요.</p></div>
        </div>
      </section>
    </div>
    <section id="iyum-about" className="iyum-about" aria-labelledby="about-heading"><div className="iyum-container"><p className="iyum-eyebrow">IYUM · 이움</p><h2 id="about-heading">정보와 경험이 이어지는 곳</h2>
      <p>장애 관련 정보와 서로의 경험을 한곳에서 살펴보세요.<br />IYUM은 필요한 정보를 찾는 어려움을 줄이고, 더 나은 선택을 돕고자 합니다.</p>
      <p className="iyum-about-note">기기·기관 탐색과 공고 연계는 준비 중입니다. 현재 제공되는 지원 안내와 커뮤니티부터 이용해 보세요.</p>
    <Link href="/about" className="iyum-text-link">IYUM 더 알아보기 →</Link></div></section>
  </main>;
}
