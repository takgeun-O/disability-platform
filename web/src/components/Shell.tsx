import Image from 'next/image';
import Link from '@/components/AppLink';
import GlobalNav from './GlobalNav';

export function Header() {
  return <header className="iyum-header" role="banner">
    <a className="iyum-skip-link" href="#main-content-start">본문 바로가기</a>
    <div className="iyum-container iyum-header-inner">
      {/* 사용자 첨부 홈 시안의 심벌을 SVG로 옮긴 표시용 자산. 원본 브랜드 보드는 보존한다. */}
      <Link href="/" className="iyum-wordmark" aria-label="IYUM 홈으로 이동"><Image className="iyum-brand-symbol" src="/images/logo/iyum-symbol-reference-v1.svg" width={48} height={54} alt="" /><span>IYUM</span></Link>
      <GlobalNav />
    </div>
  </header>;
}

export function Footer() {
  return <footer className="iyum-footer" role="contentinfo" aria-label="사이트 정보">
    <div className="iyum-container iyum-footer-inner">
      <div><Link href="/" className="iyum-wordmark"><Image className="iyum-brand-symbol" src="/images/logo/iyum-symbol-reference-v1.svg" width={48} height={54} alt="" /><span>IYUM</span></Link>
        <p>복잡한 정보는 쉽게, 필요한 경험은 가까이.</p></div>
      <div><nav aria-label="사이트 부가 메뉴">
        <Link href="/about">서비스 소개</Link><Link href="/welfare">지원 안내</Link><Link href="/community">커뮤니티</Link>
      </nav><p>이용약관·개인정보처리방침·운영정책·문의·접근성 안내 페이지는 준비 중입니다.</p></div>
    </div>
  </footer>;
}
