"use client";

import { useEffect, useRef, useState, type ComponentProps } from "react";
import { usePathname } from "next/navigation";
import Link from "./AppLink";
import AuthControls from "./AuthControls";
import OverlayDialog from "./OverlayDialog";
import { FeedAvatar } from "./community/FeedNavigation";
import { useNavigationGuard } from "./NavigationGuard";

const INFORMATION = [
  { label: "복지·지원정보", href: "/welfare", description: "지원 안내 살펴보기" },
  { label: "기기 정보", href: "/devices", description: "준비 중" },
  { label: "병원·전문기관", href: "/hospitals", description: "준비 중" },
];
const DESKTOP_INFORMATION = [
  { label: "이용 가이드", href: "/validation/hearing-aid-health-insurance", description: "보청기 지원 절차를 차근차근 확인해요", icon: "guide" },
  { ...INFORMATION[0], description: "현재 제공하는 복지·지원 안내를 살펴봐요", icon: "document" },
  { ...INFORMATION[1], description: "보조기기 정보를 준비하고 있어요", icon: "device" },
  { ...INFORMATION[2], description: "병원·전문기관 정보를 준비하고 있어요", icon: "hospital" },
];
function InformationIcon({ kind }: { kind: string }) {
  return <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
    {kind === "guide" ? <path d="M4 4h16v15H7l-3 2V4Z" /> : kind === "document" ? <><path d="M6 3h8l4 4v14H6Z M14 3v5h4 M9 12h6 M9 16h6" /></> : kind === "device" ? <><path d="M7 9a5 5 0 0 1 10 0c0 4-4 4-4 7a3 3 0 0 1-6 0 M10 9a2 2 0 0 1 4 0c0 2-3 2-3 4" /></> : <><path d="M4 21V6l8-3 8 3v15 M9 21v-6h6v6 M8 8v2 M16 8v2 M11 8h2" /></>}
  </svg>;
}
export default function GlobalNav() {
  const pathname = usePathname();
  const guard = useNavigationGuard();
  const subscribeNavigation = guard.subscribeNavigation;
  const [mobileOpen, setMobileOpen] = useState(false);
  const [information, setInformation] = useState<string | null>(null);
  const infoOpen = information === pathname;
  const infoRef = useRef<HTMLDivElement>(null);
  const infoButton = useRef<HTMLButtonElement>(null);
  const close = () => setMobileOpen(false);
  const onNavigate = (href: string, returnToInformation = false): ComponentProps<typeof Link>["onNavigate"] => event => {
    // Compare the selected destination, including search/hash, rather than focus.
    // Next invokes onNavigate only for same-tab navigation (not modified clicks).
    if (new URL(href, window.location.href).href !== window.location.href) return;
    event.preventDefault(); // AppLink must not notify/invalidate requests for a no-op.
    setInformation(null);
    close();
    if (returnToInformation) infoButton.current?.focus({ preventScroll: true });
  };
  const menuLink = (href: string, label: string, current = pathname === href) => <Link className="iyum-nav-link" href={href}
    onNavigate={onNavigate(href)} aria-current={current ? "page" : undefined}>{label}</Link>;
  useEffect(() => {
    if (!infoOpen || guard.pending) return;
    const pointer = (event: PointerEvent) => { if (!infoRef.current?.contains(event.target as Node)) setInformation(null); };
    const keyboard = (event: KeyboardEvent) => { if (event.key === "Escape") { setInformation(null); infoButton.current?.focus(); } };
    document.addEventListener("pointerdown", pointer); document.addEventListener("keydown", keyboard);
    return () => { document.removeEventListener("pointerdown", pointer); document.removeEventListener("keydown", keyboard); };
  }, [infoOpen, guard.pending]);
  useEffect(() => subscribeNavigation(() => setInformation(null)), [subscribeNavigation]);
  return <div className="iyum-nav">
    <noscript><style>{`
      .iyum-mobile-toggle, .iyum-information, .iyum-auth-controls { display:none!important; }
      .iyum-nav-items { display:flex!important; flex-wrap:wrap; }
      .iyum-nojs-information { display:flex; flex-wrap:wrap; gap:12px; grid-column:1 / -1; }
      @media(max-width:900px) { .iyum-nav-items { flex-direction:column; } }
    `}</style></noscript>
    <button className="iyum-mobile-toggle" type="button" aria-expanded={mobileOpen} aria-controls="iyum-mobile-navigation"
      aria-label="메뉴 열기" onClick={() => { setInformation(null); setMobileOpen(true); }}>
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true" focusable="false"><path d="M3 5h18M3 12h18M3 19h18" /></svg>
    </button>
    <nav className="iyum-nav-items" aria-label="주 메뉴">
      {menuLink("/about", "IYUM 소개")}{menuLink("/community", "커뮤니티", pathname.startsWith("/community"))}
      <div ref={infoRef} className="iyum-information">
        <button ref={infoButton} type="button" className="iyum-nav-link" aria-expanded={infoOpen} aria-controls="iyum-information-menu"
          aria-current={INFORMATION.some(item => pathname === item.href) || pathname.startsWith("/validation/") ? true : undefined}
          onClick={() => setInformation(infoOpen ? null : pathname)}>정보 찾기 <span className={infoOpen ? "iyum-chevron is-open" : "iyum-chevron"} aria-hidden="true">⌄</span></button>
        <div id="iyum-information-menu" className="iyum-information-menu" hidden={!infoOpen}>
          <div className="iyum-information-intro">
            <p className="iyum-eyebrow">INFORMATION</p>
            <h2>필요한 정보를<br />상황에 맞게 찾아보세요.</h2>
            <p>이용 안내부터 지원 정보까지,<br />현재 제공하는 내용을 확인해 보세요.</p>
          </div>
          <div className="iyum-information-links">
            {DESKTOP_INFORMATION.map(item => <Link key={item.href} href={item.href} onNavigate={onNavigate(item.href, true)} aria-current={pathname === item.href ? "page" : undefined}>
              <InformationIcon kind={item.icon} /><span className="iyum-information-copy"><span>{item.label}</span><small>{item.description}</small></span>
            </Link>)}
          </div>
        </div>
      </div>
    </nav>
    <noscript><div className="iyum-nojs-information" aria-label="정보 찾기">{INFORMATION.map(item => <a className="iyum-nav-link" key={item.href} href={item.href}>{item.label}</a>)}</div></noscript>
    <AuthControls onLogoutSuccess={close} onCurrentPage={close} render={({ member, logout, notice }) => <>
      <div className="iyum-auth-controls iyum-desktop-auth" aria-label="회원 메뉴">{member}{logout}</div>
      {!mobileOpen && <div className="iyum-global-notice">{notice}</div>}
      {mobileOpen && <OverlayDialog titleId="iyum-menu-title" className="iyum-menu-panel" onClose={close} returnFocus=".iyum-mobile-toggle" mobileOnly>
        <div id="iyum-mobile-navigation" className="iyum-menu-content" aria-label="회원 메뉴" onClickCapture={event => {
          if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
          const link = (event.target as HTMLElement).closest<HTMLAnchorElement>('a[href]');
          if (link?.href === window.location.href && (!link.target || link.target === '_self')) { event.preventDefault(); close(); }
        }}>
          <div className="iyum-menu-heading"><h2 id="iyum-menu-title">전체 메뉴</h2><button type="button" data-initial-focus className="iyum-overlay-close" aria-label="메뉴 닫기" onClick={close}>×</button></div>
          <div className="iyum-menu-member"><FeedAvatar />{member}</div>
          <nav aria-label="모바일 주 메뉴">
            {menuLink("/about", "IYUM 소개")}{menuLink("/community", "커뮤니티", pathname.startsWith("/community"))}
            <details className="iyum-menu-information" open><summary>정보 찾기</summary><div>
              {INFORMATION.map(item => <Link key={item.href} href={item.href} onNavigate={onNavigate(item.href)} aria-current={pathname === item.href ? "page" : undefined}><span>{item.label}</span><small>{item.description}</small></Link>)}
            </div></details>
          </nav>
          <div className="iyum-menu-bottom">{logout}</div>
          {notice}
        </div>
      </OverlayDialog>}
    </>} />
  </div>;
}
