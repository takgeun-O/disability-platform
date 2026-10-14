import Link from '@/components/AppLink';

export default function WelfarePage() {
  return <main className="iyum-info iyum-info-container">
    <nav className="info-breadcrumb" aria-label="현재 위치"><Link href="/">홈</Link><span aria-hidden="true">›</span><span aria-current="page">복지·지원정보</span></nav>
    <header className="info-page-head"><h1>복지·지원정보</h1><p className="info-intro">지원 제도와 절차를 이해하고, 다음에 확인할 일을 준비하세요.</p></header>
    <div className="info-list-title"><h2>현재 제공하는 안내 <span>1건</span></h2><span>제도 안내</span></div>
    <article className="iyum-card info-welfare-card">
      <div><div className="info-actions"><span className="iyum-badge">제도 안내</span><span className="iyum-badge">보청기</span></div><h2>보청기 건강보험 지원 안내</h2><p>센터 방문 전, 지원 과정과 내 상황을 확인하고 물어볼 질문을 준비하세요.</p><dl><dt>안내 범위</dt><dd>청각장애 등록 단계 · 보험 자격별 절차</dd></dl><p className="info-source">공식 출처: 보건복지부·국민건강보험공단 등<br />자료별 확인일과 적용 기준은 상세 안내에서 확인하세요.</p></div>
      <div className="info-card-action"><p>단계별 안내 · 질문 정리</p><Link className="iyum-button iyum-button-outline" href="/validation/hearing-aid-health-insurance">안내 보기 →</Link></div>
    </article>
    <div className="info-notice"><strong>모집 공고 연계는 준비 중입니다.</strong><p>현재는 보청기 지원 안내 한 건을 제공합니다. 제도 안내에는 모집 마감일이나 D-day를 붙이지 않습니다.</p></div>
    <Link href="/community" className="iyum-text-link">관련 경험을 커뮤니티에서 나누기 →</Link>
  </main>;
}
