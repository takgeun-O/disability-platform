'use client';

import { useEffect, useRef, useState } from 'react';
import { usePathname } from 'next/navigation';
import Link from './AppLink';
import AuthControls from './AuthControls';
import { useNavigationGuard } from './NavigationGuard';

const INFORMATION = [
  { label: '복지·지원정보', href: '/welfare', description: '지원 안내 살펴보기' },
  { label: '기기 정보', href: '/devices', description: '준비 중' },
  { label: '병원·전문기관', href: '/hospitals', description: '준비 중' },
];

export default function GlobalNav() {
  const pathname = usePathname();
  const guard = useNavigationGuard();
  const [expanded, setExpanded] = useState({ path: pathname, mobile: false, info: false });
  const mobileOpen = expanded.path === pathname && expanded.mobile;
  const infoOpen = expanded.path === pathname && expanded.info;
  const navRef = useRef<HTMLElement>(null);
  const infoRef = useRef<HTMLDivElement>(null);
  const mobileButton = useRef<HTMLButtonElement>(null);
  const infoButton = useRef<HTMLButtonElement>(null);
  const close = () => {
    // 이탈 확인을 취소하면 기존 대화상자가 이 메뉴 링크로 초점을 돌려준다.
    // 실제 경로가 바뀌기 전까지 dirty 화면의 메뉴를 숨기지 않는다.
    if (!guard.isDirty()) setExpanded({ path: pathname, mobile: false, info: false });
  };

  useEffect(() => {
    if (guard.pending) return;
    function pointer(event: PointerEvent) {
      if (!navRef.current?.contains(event.target as Node)) {
        setExpanded({ path: pathname, mobile: false, info: false });
      } else if (!infoRef.current?.contains(event.target as Node)) {
        setExpanded(current => ({ ...current, info: false }));
      }
    }
    function keyboard(event: KeyboardEvent) {
      if (event.key !== 'Escape') return;
      if (infoOpen) {
        setExpanded(current => ({ ...current, info: false }));
        infoButton.current?.focus();
      } else if (mobileOpen) {
        setExpanded({ path: pathname, mobile: false, info: false });
        mobileButton.current?.focus();
      }
    }
    document.addEventListener('pointerdown', pointer);
    document.addEventListener('keydown', keyboard);
    return () => {
      document.removeEventListener('pointerdown', pointer);
      document.removeEventListener('keydown', keyboard);
    };
  }, [pathname, mobileOpen, infoOpen, guard.pending]);

  return (
    <nav ref={navRef} className={`iyum-nav${mobileOpen ? ' is-open' : ''}`} aria-label="주 메뉴">
      <noscript><style>{`
        .iyum-mobile-toggle, .iyum-information, .iyum-auth-controls { display:none!important; }
        .iyum-nav-items { display:flex!important; flex-wrap:wrap; }
        .iyum-nojs-information { display:flex; flex-wrap:wrap; gap:12px; grid-column:1 / -1; }
        @media(max-width:900px) { .iyum-nav-items { flex-direction:column; } }
      `}</style></noscript>
      <button ref={mobileButton} className="iyum-mobile-toggle" type="button"
        aria-expanded={mobileOpen} aria-controls="iyum-navigation" aria-label={mobileOpen ? '메뉴 닫기' : '메뉴 열기'}
        onClick={() => setExpanded({ path: pathname, mobile: !mobileOpen, info: false })}>
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" focusable="false"><path d={mobileOpen ? 'M6 6l12 12M6 18 18 6' : 'M3 5h18M3 12h18M3 19h18'} /></svg>
      </button>
      <div id="iyum-navigation" className="iyum-nav-items">
        <Link className="iyum-nav-link" href="/about" onClick={close}
          aria-current={pathname === '/about' ? 'page' : undefined}>IYUM 소개</Link>
        <Link className="iyum-nav-link" href="/community" onClick={close}
          aria-current={pathname.startsWith('/community') ? 'page' : undefined}>커뮤니티</Link>
        <div ref={infoRef} className="iyum-information">
          <button ref={infoButton} type="button" className="iyum-nav-link" aria-expanded={infoOpen} aria-controls="iyum-information-menu"
            aria-current={INFORMATION.some(item => pathname === item.href) || pathname.startsWith('/validation/') ? true : undefined}
            onClick={() => setExpanded({ path: pathname, mobile: mobileOpen, info: !infoOpen })}>
            정보 찾기 <span className={infoOpen ? 'iyum-chevron is-open' : 'iyum-chevron'} aria-hidden="true">⌄</span>
          </button>
          <div id="iyum-information-menu" className="iyum-information-menu" hidden={!infoOpen}>
            {INFORMATION.map(item => <Link key={item.href} href={item.href} onClick={close}
              aria-current={pathname === item.href ? 'page' : undefined}>
              <span>{item.label}</span><small>{item.description}</small>
            </Link>)}
          </div>
        </div>
      </div>
      <noscript><div className="iyum-nojs-information" aria-label="정보 찾기">
        {INFORMATION.map(item => <a className="iyum-nav-link" key={item.href} href={item.href}>{item.label}</a>)}
      </div></noscript>
      <AuthControls />
    </nav>
  );
}
