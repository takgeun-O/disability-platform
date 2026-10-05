import BenefitBreakdown, { BenefitSources } from './BenefitBreakdown';
import ClaimDocuments from './ClaimDocuments';
import { benefitBasis, formatBenefitWon } from './benefit-content';
import styles from './page.module.css';

// Optional health-insurance details come after the personalized center questions.
export default function AfterPurchaseGuide({ historyNeedsChecking }: { historyNeedsChecking: boolean }) {
  return <section className={styles.afterPurchase} aria-labelledby="after-purchase-title">
    <p className={styles.futureLabel}>필요할 때 펼쳐보기 · 건강보험 기준</p>
    <h3 id="after-purchase-title">구입 후 절차와 지원 비용</h3>
    <p>한쪽 기준액 {formatBenefitWon(benefitBasis.total)}은 제품과 여러 해의 관리 비용을 합한 금액이에요. 한 번에 받는 돈은 아니에요.</p>
    {historyNeedsChecking && <p className={styles.scope}>아래는 일반 기준이에요. 이번에 지원받을 수 있는지는 이전 이력을 확인한 뒤 알 수 있어요.</p>}
    <details className={styles.disclosure}>
      <summary>구입 후 병원 확인·청구·관리는 어떻게 이어지나요?</summary>
      <div>
        <h4>병원에서 착용 효과 확인</h4>
        <p>구입 후 1개월이 지난 뒤 이비인후과에서 보청기가 듣는 데 도움이 되는지 확인받아요. 이때 받는 검수확인서가 지원금 청구에 필요해요.</p>
        <h4>처음 지원금 청구</h4>
        <p>제품 비용과 초기 관리 비용을 함께 청구해요.</p>
        <dl className={styles.claimContacts}><div><dt>위임 청구 시</dt><dd>판매업소에 맡겨요.</dd></div><div><dt>직접 청구 시</dt><dd>국민건강보험공단에 신청해요.</dd></div></dl>
        <h4>센터에서 조절·관리 계속하기</h4>
        <p>구입 후 첫 1년은 초기 관리, 이후 5년까지는 후기 관리 구간이에요. 후기 관리는 구입 1년 후부터 매년 1회 이상 실제 관리를 받은 뒤, 연 1회씩 최대 4회 따로 청구해요.</p>
        <p>센터에서 다음 관리 일정과 후기 관리비 청구 방법을 확인하세요.</p>
      </div>
    </details>
    <details className={styles.disclosure}>
      <summary>지원 금액과 지급 조건 자세히 보기</summary>
      <div className={styles.benefitDetails}><BenefitBreakdown showSources={false} headingLevel={4} /></div>
    </details>
    <details className={styles.disclosure}>
      <summary>직접 청구한다면 서류는 어디서 받나요?</summary>
      <div><ClaimDocuments /></div>
    </details>
    <BenefitSources includeClaim />
  </section>;
}
