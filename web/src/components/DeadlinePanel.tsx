import Link from './AppLink';

// 공고 API가 없는 현재는 빈 상태가 기본이다. 아래 내용은 펼쳐 보는 고정된 UI 예시뿐이다.
const EXAMPLES = [
  { name: '예시 지원사업 A', date: '2026-10-15', displayDate: '2026.10.15 · 시간 미정', remaining: 'D-2' },
  { name: '예시 지원사업 B', date: '2026-10-18', displayDate: '2026.10.18 · 18:00', remaining: 'D-5' },
  { name: '예시 지원사업 C', date: '2026-10-20', displayDate: '2026.10.20 · 시간 미정', remaining: 'D-7' },
];

export default function DeadlinePanel() {
  return <section className="iyum-card iyum-deadlines" aria-labelledby="deadline-heading">
    <p className="iyum-eyebrow">놓치기 전에 확인하세요</p>
    <h2 id="deadline-heading">마감 임박 복지·지원정보</h2>
    <div className="iyum-empty-state">
      <p className="iyum-empty-title">아직 표시할 공고가 없어요.</p>
      <p>마감 공고 연계를 준비 중입니다. 현재 제공되는 지원 안내를 먼저 살펴보세요.</p>
      <Link className="iyum-text-link" href="/welfare">복지·지원정보 보기 <span aria-hidden="true">→</span></Link>
    </div>
    <details className="iyum-deadline-examples">
      <summary>공고 화면 예시 보기</summary>
      <p className="iyum-example-note">모두 가상 예시이며 실제 지원사업이나 신청 정보가 아닙니다. 날짜와 D-day는 2026.10.13을 기준으로 고정되어 있습니다.</p>
      <ul className="iyum-deadline-list">
        {EXAMPLES.map(item => <li key={item.name}>
          <div className="iyum-deadline-title"><h3>{item.name}</h3><span className="iyum-deadline-badge">{item.remaining}</span></div>
          <p>예시 지역 · 예시 대상자</p><time dateTime={item.date}>{item.displayDate}</time>
          <details className="iyum-example-detail"><summary>상세보기<span className="sr-only">: {item.name}</span></summary>
            <p>공고의 지역·대상·마감일을 보여 주기 위한 화면 예시입니다. 실제 신청이나 공식 공고 연결은 제공하지 않습니다.</p>
          </details>
        </li>)}
      </ul>
    </details>
  </section>;
}
