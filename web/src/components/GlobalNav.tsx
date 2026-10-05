"use client";
import { usePathname } from "next/navigation";
import Link from "./AppLink";
const NAV_ITEMS = [
  { label: '홈', to: '/' },
  { label: '커뮤니티', to: '/community' },
  { label: '복지·지원정보', to: '/welfare' },
  { label: '병원·전문기관', to: '/hospitals' },
  { label: '보조기기', to: '/devices' },
]

export default function GlobalNav() {
  const pathname = usePathname()

  return (
    <nav aria-label="주 메뉴" style={{ borderBottom: '1px solid #D0CEC9', backgroundColor: '#FAFAF8' }}>
      <div className="max-w-6xl mx-auto px-6">
        <ul className="flex items-center" role="list" style={{ margin: 0, padding: 0, listStyle: 'none' }}>
          {NAV_ITEMS.map((item) => {
            const current = item.to === '/' ? pathname === '/' : pathname.startsWith(item.to)
            return (
              <li key={item.label}>
                <Link
                  href={item.to}
                  aria-current={current ? 'page' : undefined}
                  style={{
                    display: 'inline-block',
                    fontSize: 14,
                    fontWeight: 500,
                    padding: '12px 16px',
                    textDecoration: 'none',
                    color: current ? '#1E3A8A' : '#1A1918',
                    borderBottom: current ? '2px solid #1E3A8A' : '2px solid transparent',
                  }}
                >
                  {item.label}
                </Link>
              </li>
            )
          })}
        </ul>
      </div>
    </nav>
  )
}

