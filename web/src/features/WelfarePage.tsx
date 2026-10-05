import Link from '@/components/AppLink';

export default function WelfarePage() {
  return (
    <main id="main-content">
      <div className="max-w-6xl mx-auto px-4 sm:px-6" style={{ paddingTop: 44, paddingBottom: 64 }}>

        {/* Page heading */}
        <div style={{ maxWidth: 720, marginBottom: 36 }}>
          <h1
            style={{
              fontSize: 22,
              fontWeight: 700,
              color: '#1A1918',
              letterSpacing: '-0.02em',
              marginBottom: 8,
            }}
          >
            복지·지원정보
          </h1>
          <p style={{ fontSize: 14, color: '#908D88', lineHeight: 1.6 }}>
            청각장애 관련 복지 지원 안내를 준비하고 있어요. 현재는 아래 항목만 제공됩니다.
          </p>
        </div>

        {/* P03: the one real item */}
        <div
          style={{
            maxWidth: 720,
            borderTop: '2px solid #1A1918',
            borderBottom: '1px solid #D0CEC9',
            borderLeft: '1px solid #D0CEC9',
            borderRight: '1px solid #D0CEC9',
            borderRadius: 2,
            overflow: 'hidden',
            marginBottom: 32,
          }}
        >
          <div style={{ padding: '24px 24px' }}>
            <p
              style={{
                fontSize: 10,
                color: '#908D88',
                marginBottom: 6,
                fontFamily: "'DM Mono', monospace",
                letterSpacing: '0.04em',
              }}
            >
              지원 안내 · 건강보험
            </p>
            <h2
              style={{
                fontSize: 17,
                fontWeight: 700,
                color: '#1A1918',
                marginBottom: 8,
                letterSpacing: '-0.01em',
              }}
            >
              보청기 건강보험 지원 안내
            </h2>
            <p style={{ fontSize: 13, color: '#4A4845', lineHeight: 1.65, marginBottom: 18 }}>
              센터 방문 전, 지원 과정과 내 상황을 확인하고 물어볼 질문을 준비하세요. 청각장애
              등록 단계와 보험 자격에 따른 절차를 안내하고, 보청기센터에서 확인할 질문을 정리해
              드려요.
            </p>
            <Link
              href="/validation/hearing-aid-health-insurance"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                minHeight: 44,
                padding: '10px 18px',
                fontSize: 14,
                fontWeight: 600,
                color: '#FAFAF8',
                backgroundColor: '#1A1918',
                borderRadius: 2,
                textDecoration: 'none',
              }}
            >
              지원 안내 보기 →
            </Link>
          </div>
        </div>

        {/* Coming soon notice */}
        <div
          style={{
            maxWidth: 720,
            padding: '14px 18px',
            backgroundColor: '#F0EEE9',
            borderRadius: 2,
          }}
        >
          <p style={{ fontSize: 13, color: '#908D88', lineHeight: 1.6 }}>
            장애인 보조기기 교부사업, 수어통역 서비스 등 다른 지원 항목은 단계적으로 추가될
            예정이에요. 커뮤니티에서 관련 경험을 공유하거나 검색으로 먼저 알아볼 수 있어요.
          </p>
          <div style={{ display: 'flex', gap: 12, marginTop: 12, flexWrap: 'wrap' }}>
            <Link
              href="/community"
              style={{ fontSize: 13, color: '#1E3A8A', textDecoration: 'none', fontWeight: 500 }}
            >
              커뮤니티 보기 →
            </Link>
            <Link
              href="/search"
              style={{ fontSize: 13, color: '#1E3A8A', textDecoration: 'none', fontWeight: 500 }}
            >
              검색하기 →
            </Link>
          </div>
        </div>

      </div>
    </main>
  )
}
