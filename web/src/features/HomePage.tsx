import HomeSearch from "@/components/HomeSearch";
import Link from '@/components/AppLink';

const COMMUNITY_POSTS = [
  { id: 1, title: '보청기 보험 급여 기준 바뀐 거 아시나요?', category: '복지·제도', date: '2026.08.14', comments: 12 },
  { id: 2, title: '서울 강남구 청각장애인 수화통역 서비스 후기', category: '병원·기관 후기', date: '2026.08.13', comments: 7 },
  { id: 3, title: '직장 면접 시 청각장애 고지 어떻게 하셨나요?', category: '일상·경험', date: '2026.08.12', comments: 24 },
  { id: 4, title: '2026년 장애인 보조기기 교부사업 신청 완료했어요', category: '복지·제도', date: '2026.08.11', comments: 9 },
  { id: 5, title: '인공와우 수술 전 꼭 알아야 할 것들', category: '정보 공유', date: '2026.08.10', comments: 18 },
]

const WELFARE_ITEMS = [
  { id: 1, title: '장애인 보조기기 교부사업', target: '등록 장애인 (소득 기준 충족자)', period: '2026.03.01 – 2026.10.31', status: '접수 중', statusType: 'open' },
  { id: 2, title: '청각장애인 수어통역 서비스', target: '청각·언어 장애인', period: '연중 상시', status: '상시 운영', statusType: 'open' },
  { id: 3, title: '장애인 고용 장려금 지원', target: '장애인 고용 사업주', period: '2026.01.01 – 2026.12.31', status: '접수 중', statusType: 'open' },
  { id: 4, title: '발달재활서비스 바우처', target: '만 18세 미만 장애 아동', period: '2026.09.01 부터', status: '접수 예정', statusType: 'upcoming' },
]

const HOSPITALS = [
  { id: 1, name: '서울대학교병원 이비인후과', type: '대학병원', region: '서울 종로구' },
  { id: 2, name: '세브란스병원 청각언어장애클리닉', type: '대학병원', region: '서울 서대문구' },
  { id: 3, name: '한국청각언어장애인교육재활협회', type: '전문기관', region: '서울 마포구' },
]

const DEVICES = [
  { id: 1, name: '오티콘 모어 1 미니 RITE', maker: '오티콘(Oticon)', type: '귀걸이형 보청기', info: '블루투스 스트리밍 지원' },
  { id: 2, name: '시그니아 스타일레토 IX', maker: '시그니아(Signia)', type: '귀속형 보청기', info: '인공지능 소음 처리' },
  { id: 3, name: '코클리어 누클레우스 8', maker: '코클리어(Cochlear)', type: '인공와우', info: '방수 IP57 등급' },
]

const CORE_SERVICES = [
  {
    label: '커뮤니티',
    to: '/community',
    desc: '실제 경험과 정보를 나누는 공간',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6l-4 3V4Z" />
      </svg>
    ),
  },
  {
    label: '복지·지원정보',
    to: '/welfare',
    desc: '복지 프로그램과 지원 사업 탐색',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="2" width="14" height="16" rx="1.5" />
        <path d="M7 7h6M7 10.5h6M7 14h4" />
      </svg>
    ),
  },
  {
    label: '병원·전문기관',
    to: '/hospitals',
    desc: '전문 병원과 재활기관 안내',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 18V8l7-5 7 5v10" />
        <path d="M8 18v-5h4v5" />
        <path d="M10 8v3m-1.5-1.5h3" />
      </svg>
    ),
  },
  {
    label: '보조기기',
    to: '/devices',
    desc: '보청기, 인공와우 등 기기 정보',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="10" cy="10" r="7" />
        <path d="M10 7v3l2 2" />
        <circle cx="10" cy="10" r="1" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
]

function StatusBadge({ status, type }: { status: string; type: string }) {
  const styles: Record<string, string> = {
    open: 'border border-blue-700 text-blue-800 bg-blue-50',
    upcoming: 'border border-amber-700 text-amber-800 bg-amber-50',
    closed: 'border border-gray-400 text-gray-600 bg-gray-100',
  }
  return (
    <span className={`inline-block text-xs font-medium px-2 py-0.5 ${styles[type] ?? styles.open}`} aria-label={`상태: ${status}`}>
      {status}
    </span>
  )
}

export default function HomePage() {
  return (
    <main id="main-content">
      {/* ─── HERO ───────────────────────────────────────────────── */}
      <section
        aria-label="서비스 소개 및 검색"
        style={{ borderBottom: '1px solid #D0CEC9', backgroundColor: '#F0EEE9', paddingTop: 44, paddingBottom: 44 }}
      >
        <div className="max-w-6xl mx-auto px-6">
          <h1 className="font-bold mb-4" style={{ fontSize: 28, lineHeight: 1.3, color: '#1A1918', maxWidth: 520 }}>
            필요한 장애 관련 정보를 찾아보세요
          </h1>
          <HomeSearch />
        </div>
      </section>

      {/* ─── CORE SERVICE SHORTCUTS ─────────────────────────────── */}
      <section aria-labelledby="services-heading" style={{ borderBottom: '1px solid #D0CEC9', paddingTop: 40, paddingBottom: 40 }}>
        <div className="max-w-6xl mx-auto px-6">
          <h2 id="services-heading" className="sr-only">핵심 서비스</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)' }}>
            {CORE_SERVICES.map((service, i) => (
              <Link
                key={service.label}
                href={service.to}
                style={{
                  display: 'block',
                  padding: '20px 20px',
                  borderRight: i < 3 ? '1px solid #D0CEC9' : 'none',
                  textDecoration: 'none',
                  color: '#1A1918',
                  transition: 'background-color 0.15s',
                }}
                data-hover="iyum-hover-0"
              >
                <span className="block mb-2.5">{service.icon}</span>
                <span className="block font-semibold text-sm mb-1">{service.label}</span>
                <span className="block text-xs leading-snug" style={{ color: '#908D88' }}>{service.desc}</span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ─── COMMUNITY + WELFARE GRID ───────────────────────────── */}
      <div
        className="max-w-6xl mx-auto px-6"
        style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', paddingTop: 44, paddingBottom: 44 }}
      >
        {/* Community Preview */}
        <section aria-labelledby="community-heading" style={{ paddingRight: 48, borderRight: '1px solid #D0CEC9' }}>
          <div className="flex items-baseline justify-between mb-5">
            <h2 id="community-heading" className="font-semibold text-base">커뮤니티</h2>
            <Link href="/community" style={{ fontSize: 13, color: '#1E3A8A', textDecoration: 'none', fontWeight: 500 }}>
              커뮤니티 더보기 →
            </Link>
          </div>
          <ol aria-label="최근 게시글 목록">
            {COMMUNITY_POSTS.map((post, i) => (
              <li key={post.id} style={{ borderTop: i > 0 ? '1px solid #E8E6E1' : 'none' }}>
                <Link
                  href={`/community/posts/${post.id}`}
                  style={{ display: 'block', padding: '12px 0', textDecoration: 'none', color: '#1A1918' }}
                  data-hover="iyum-hover-1"
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="block font-medium leading-snug" style={{ fontSize: 14, flex: 1 }}>{post.title}</span>
                    <span className="shrink-0 text-xs" style={{ color: '#908D88', marginTop: 1 }} aria-label={`댓글 ${post.comments}개`}>
                      댓글 {post.comments}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs font-medium" style={{ color: '#1E3A8A' }}>{post.category}</span>
                    <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
                    <time className="text-xs" style={{ color: '#908D88' }} dateTime={post.date.replace(/\./g, '-')}>{post.date}</time>
                  </div>
                </Link>
              </li>
            ))}
          </ol>
        </section>

        {/* Welfare Preview */}
        <section aria-labelledby="welfare-heading" style={{ paddingLeft: 48 }}>
          <div className="flex items-baseline justify-between mb-5">
            <h2 id="welfare-heading" className="font-semibold text-base">복지·지원정보</h2>
            <Link href="/welfare" style={{ fontSize: 13, color: '#1E3A8A', textDecoration: 'none', fontWeight: 500 }}>
              지원정보 더보기 →
            </Link>
          </div>
          <ol aria-label="복지 지원사업 목록">
            {/* P03: 보청기 건강보험 지원 안내 */}
            <li style={{ borderTop: 'none' }}>
              <Link
                href="/validation/hearing-aid-health-insurance"
                style={{ display: 'block', padding: '12px 0', textDecoration: 'none', color: '#1A1918' }}
                data-hover="iyum-hover-2"
              >
                <div className="flex items-start justify-between gap-3 mb-1.5">
                  <span className="block font-medium leading-snug" style={{ fontSize: 14, flex: 1 }}>
                    보청기 건강보험 지원 안내
                  </span>
                  <span
                    className="inline-block text-xs font-medium px-2 py-0.5"
                    style={{
                      border: '1px solid #1E3A8A',
                      color: '#1E3A8A',
                      backgroundColor: '#EFF6FF',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    지원 안내
                  </span>
                </div>
                <p style={{ fontSize: 12, color: '#908D88', lineHeight: 1.5 }}>
                  센터 방문 전, 지원 과정과 내 상황을 확인하고 물어볼 질문을 준비하세요.
                </p>
              </Link>
            </li>
            {WELFARE_ITEMS.map((item) => (
              <li key={item.id} style={{ borderTop: '1px solid #E8E6E1' }}>
                <a
                  href={`#welfare-${item.id}`}
                  style={{ display: 'block', padding: '12px 0', textDecoration: 'none', color: '#1A1918' }}
                  data-hover="iyum-hover-3"
                >
                  <div className="flex items-start justify-between gap-3 mb-1.5">
                    <span className="block font-medium leading-snug" style={{ fontSize: 14, flex: 1 }}>{item.title}</span>
                    <StatusBadge status={item.status} type={item.statusType} />
                  </div>
                  <dl style={{ fontSize: 12, color: '#908D88', display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <div><dt className="sr-only">지원 대상</dt><dd>{item.target}</dd></div>
                    <div><dt className="sr-only">신청 기간</dt><dd>{item.period}</dd></div>
                  </dl>
                </a>
              </li>
            ))}
          </ol>
        </section>
      </div>

      {/* ─── HOSPITALS + DEVICES ────────────────────────────────── */}
      <div style={{ borderTop: '2px solid #D0CEC9', backgroundColor: '#F0EEE9' }}>
        <div
          className="max-w-6xl mx-auto px-6"
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', paddingTop: 48, paddingBottom: 56 }}
        >
          {/* Hospitals */}
          <section aria-labelledby="hospitals-heading" style={{ paddingRight: 48, borderRight: '1px solid #D0CEC9' }}>
            <h2 id="hospitals-heading" className="font-semibold text-base mb-1">병원·전문기관</h2>
            <p className="text-sm mb-5" style={{ color: '#908D88' }}>필요한 진료·재활·전문기관 정보를 찾아보세요.</p>
            <ol aria-label="전문기관 목록" style={{ marginBottom: 20 }}>
              {HOSPITALS.map((h, i) => (
                <li key={h.id} style={{ borderTop: i > 0 ? '1px solid #E8E6E1' : 'none' }}>
                  <a
                    href={`#hospital-${h.id}`}
                    style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '11px 0', textDecoration: 'none', color: '#1A1918', gap: 12 }}
                    data-hover="iyum-hover-4"
                  >
                    <div>
                      <span className="block font-medium" style={{ fontSize: 14 }}>{h.name}</span>
                      <div className="flex items-center gap-1.5 mt-0.5">
                        <span className="text-xs" style={{ color: '#908D88' }}>{h.type}</span>
                        <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
                        <span className="text-xs" style={{ color: '#908D88' }}>{h.region}</span>
                      </div>
                    </div>
                    <span style={{ color: '#908D88', fontSize: 16 }} aria-hidden="true">›</span>
                  </a>
                </li>
              ))}
            </ol>
            <a
              href="#hospitals-list"
              className="inline-flex items-center gap-1 font-semibold text-sm"
              style={{ border: '1.5px solid #1A1918', padding: '8px 16px', borderRadius: 2, textDecoration: 'none', color: '#1A1918', transition: 'background-color 0.15s' }}
              data-hover="iyum-hover-5"
            >
              기관 찾기
            </a>
          </section>

          {/* Devices */}
          <section aria-labelledby="devices-heading" style={{ paddingLeft: 48 }}>
            <h2 id="devices-heading" className="font-semibold text-base mb-1">보조기기</h2>
            <p className="text-sm mb-5" style={{ color: '#908D88' }}>보청기, 인공와우, 청각 관련 보조기기 제품 정보를 확인하세요.</p>
            <ol aria-label="보조기기 목록" style={{ marginBottom: 20 }}>
              {DEVICES.map((d, i) => (
                <li key={d.id} style={{ borderTop: i > 0 ? '1px solid #E8E6E1' : 'none' }}>
                  <a
                    href={`#device-${d.id}`}
                    style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '11px 0', textDecoration: 'none', color: '#1A1918', gap: 12 }}
                    data-hover="iyum-hover-6"
                  >
                    <div>
                      <span className="block font-medium" style={{ fontSize: 14 }}>{d.name}</span>
                      <div className="flex items-center gap-1.5 mt-0.5">
                        <span className="text-xs" style={{ color: '#908D88' }}>{d.type}</span>
                        <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
                        <span className="text-xs" style={{ color: '#908D88' }}>{d.maker}</span>
                        <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
                        <span className="text-xs" style={{ color: '#908D88' }}>{d.info}</span>
                      </div>
                    </div>
                    <span style={{ color: '#908D88', fontSize: 16 }} aria-hidden="true">›</span>
                  </a>
                </li>
              ))}
            </ol>
            <a
              href="#devices-list"
              className="inline-flex items-center gap-1 font-semibold text-sm"
              style={{ border: '1.5px solid #1A1918', padding: '8px 16px', borderRadius: 2, textDecoration: 'none', color: '#1A1918', transition: 'background-color 0.15s' }}
              data-hover="iyum-hover-7"
            >
              제품 찾기
            </a>
          </section>
        </div>
      </div>
    </main>
  )
}
