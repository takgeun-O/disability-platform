import Link from '@/components/AppLink';
export { default as GlobalNav } from "./GlobalNav";

const FOOTER_LINKS = ['서비스 소개', '이용약관', '개인정보처리방침', '운영정책', '문의', '접근성 안내']

export function Header() {
  return (
    <header style={{ borderBottom: '1px solid #D0CEC9', backgroundColor: '#FAFAF8' }} role="banner">
      <div className="max-w-6xl mx-auto px-6 flex items-center justify-between h-14">
        <Link
          href="/"
          className="flex items-center gap-2"
          style={{ textDecoration: 'none' }}
          aria-label="장애인플랫폼 홈으로 이동"
        >
          <div style={{ width: 28, height: 28, backgroundColor: '#1E3A8A', borderRadius: 2 }} aria-hidden="true" />
          <span className="font-semibold text-base" style={{ color: '#1A1918', letterSpacing: '-0.01em' }}>
            장애인플랫폼
          </span>
        </Link>
        <div className="flex items-center gap-2">
          <Link
            href="/login"
            className="text-sm font-medium px-3 py-1.5 hover:underline transition-colors"
            style={{ color: '#1A1918', textDecoration: 'none' }}
          >
            로그인
          </Link>
          <Link
            href="/register"
            className="text-sm font-medium px-3 py-1.5"
            style={{ backgroundColor: '#1E3A8A', color: '#FFFFFF', borderRadius: 2, textDecoration: 'none' }}
          >
            회원가입
          </Link>
        </div>
      </div>
    </header>
  )
}

export function Footer() {
  return (
    <footer
      role="contentinfo"
      aria-label="사이트 정보"
      style={{ borderTop: '2px solid #1A1918', backgroundColor: '#FAFAF8', paddingTop: 32, paddingBottom: 32 }}
    >
      <div className="max-w-6xl mx-auto px-6">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 32, alignItems: 'start' }}>
          <div>
            <div className="flex items-center gap-2 mb-3">
              <div style={{ width: 20, height: 20, backgroundColor: '#1A1918', borderRadius: 2 }} aria-hidden="true" />
              <span className="font-semibold text-sm">장애인플랫폼</span>
            </div>
            <p className="text-xs leading-relaxed" style={{ color: '#908D88', maxWidth: 360 }}>
              운영 주체: (사)장애인정보접근지원협회 · 대표: 홍길동<br />
              서울특별시 마포구 월드컵북로 400, 000호<br />
              사업자등록번호: 000-00-00000 · 문의: contact@example.or.kr
            </p>
          </div>
          <nav aria-label="사이트 부가 메뉴">
            <ul style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 20px', justifyContent: 'flex-end', listStyle: 'none', padding: 0, margin: 0 }}>
              {FOOTER_LINKS.map((item) => (
                <li key={item}>
                  <a href="#" className="text-xs hover:underline" style={{ color: '#1A1918', textDecoration: 'none' }}>
                    {item}
                  </a>
                </li>
              ))}
            </ul>
            <p className="text-xs mt-4 text-right" style={{ color: '#908D88' }}>
              © 2026 장애인플랫폼. All rights reserved.
            </p>
          </nav>
        </div>
      </div>
    </footer>
  )
}
