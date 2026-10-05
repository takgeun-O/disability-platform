'use client';

import { useId, useState, type ReactNode } from 'react';
import { claimRuleUrl, referenceReviewedAt, sources } from './content';
import { benefitBasis, benefitExamples, followupBasis, initialBasis, productBasis, formatBenefitWon } from './benefit-content';
import styles from './page.module.css';

const [general, reduced] = benefitExamples;
export function BenefitMoney({ amount }: { amount: number }) {
  return <span className={styles.moneyAmount}>{formatBenefitWon(amount)}</span>;
}

// One inline disclosure per occurrence; useId also distinguishes the two views
// mounted together by FocusedFlow. No eligibility questions or persistent state.
export function CostReductionAmount({ children }: { children: ReactNode }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  return <div className={styles.benefitPaymentGroup}>
    <div className={styles.benefitEligibility}>
      <span>{reduced.label}</span>{' '}
      <button type="button" className={styles.benefitHelpButton} aria-label="차상위 본인부담경감 대상자 설명" aria-expanded={open} aria-controls={`${id}-body`} onClick={() => setOpen(value => !value)}><span aria-hidden="true">?</span></button>
      <span className={styles.benefitRate}> · {reduced.percent}%</span>
    </div>
    {children}
    <div id={`${id}-body`} className={styles.benefitHelpBody} hidden={!open} role="region" aria-labelledby={`${id}-title`}>
      <p id={`${id}-title`}><strong>차상위 본인부담경감 대상자란?</strong></p>
      <p>소득·재산, 부양 요건과 질환·연령 등의 기준을 심사받아, 건강보험 의료비의 본인부담을 줄여주는 지원 대상으로 인정된 분이에요.</p>
      <p>청각장애 등록만으로 자동 적용되지는 않아요. 의료급여와는 다른 건강보험의 지원 제도예요.</p>
      <p>대상인지 모르겠다면 국민건강보험공단에 “제가 차상위 본인부담경감 대상자로 등록되어 있나요?”라고 확인해 주세요.</p>
      <p className={styles.benefitSources}><a href="https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=29" target="_blank" rel="noopener noreferrer">국민건강보험법 시행규칙 · 제14·15조 (새 탭)</a></p>
    </div>
  </div>;
}

const basisExplanation = '이 금액이 그대로 지급되는 것은 아니에요. 자격별 최대 지원액을 확인하세요.';
export function BenefitConditions() {
  return <>
    <p>건강보험은 기본적으로 지급기준금액의 90%를 지원해요. 제품의 고시가격이나 실제 구입가격이 낮으면 지원액도 줄어들 수 있어요. 후기 관리도 실제 비용과 지급 조건을 확인해야 해요.</p>
    <p>차상위 본인부담경감 대상 등은 같은 건강보험 안에서 100% 지원이 적용돼요. 기준을 넘는 비용까지 모두 지원한다는 뜻은 아니에요. 본인의 경감 적용 여부는 국민건강보험공단에서 확인하세요. 의료급여는 별도 절차를 확인하세요.</p>
    <p>제품별 고시가격에는 초기 관리비가 이미 포함되어 있어요. 고시가격에 20만 원을 다시 더하지 않아요.</p>
    <p>날짜가 지나면 자동 입금되는 방식이 아니에요. 개인별 청구 가능일과 지급받을 계좌는 판매업소·공단에 확인하세요. 위임 청구 시에는 판매업소가 지급받을 수 있어요.</p>
  </>;
}
export function BenefitSources({ includeClaim = false }: { includeClaim?: boolean }) {
  return <p className={styles.benefitSources}>금액·지급 기준 확인일: <time dateTime={referenceReviewedAt}>{referenceReviewedAt}</time><br />
    <a href="https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=87&SEQ_HISTORY=613408" target="_blank" rel="noopener noreferrer">공식 고시 · 제5조의2, 별표 4·5 (새 탭)</a><br />
    <a href={sources.find(source => source.key === 'easy')!.url} target="_blank" rel="noopener noreferrer">법제처 · 90%·100% 지급 기준 안내 (새 탭)</a>
    {includeClaim && <><br /><a href={claimRuleUrl} target="_blank" rel="noopener noreferrer">직접·위임 청구 근거 (새 탭)</a></>}
  </p>;
}

// Amounts and timing rechecked 2026-09-24 against notice 2026-56, Article 5-2,
// annexes 4 and 5 (NHIS history 613408), and the statutory 90%/100% payment rates.
export default function BenefitBreakdown({ showSources = true, headingLevel = 3 }: { showSources?: boolean; headingLevel?: 3 | 4 }) {
  const id = useId();
  const Heading = headingLevel === 3 ? 'h3' : 'h4';
  return <section className={styles.benefitBreakdown} aria-label="지원 비용 구성과 청구 시점">
    <p className={styles.label}>보청기 한쪽(1개) 기준</p>
    <a className={styles.quietLink} href="#one-or-two">19세 미만 아이의 양쪽 지원 알아보기</a>
    <p className={styles.scope}>기준액은 건강보험 일반 대상과 차상위 본인부담경감 대상자가 공통으로 사용하는 지원액 계산 한도예요. ‘적합관리’는 보청기를 잘 사용할 수 있도록 조절·관리하는 서비스예요.</p>
    <p className={styles.scope}>실제 지원액은 제품별 고시금액과 구입금액 등에 따라 달라져요. 100% 지원도 지원 대상으로 인정되는 금액 기준이에요.</p>
    <div className={styles.benefitCards}>
      <section className={styles.benefitCard} aria-labelledby={`${id}-initial`}>
        <div className={styles.benefitComposition}>
          <Heading id={`${id}-initial`}>처음 청구</Heading>
          <p className={styles.benefitCalculationTitle}>지원금 계산 기준</p>
          <p className={styles.benefitBasis}>기준액 <BenefitMoney amount={initialBasis} /></p>
          <p className={styles.benefitBasisNote}>{basisExplanation}</p>
          <p>제품 <BenefitMoney amount={productBasis} /> + 초기 관리 <BenefitMoney amount={benefitBasis.initialManagement} /></p>
          <p className={styles.benefitTiming}>구입 후 1개월이 지난 뒤 병원 검수확인을 받고 청구해요.<br />두 비용은 함께 지급돼요.</p>
        </div>
        <div className={styles.benefitPayments}>
          <p className={styles.benefitPaymentTitle}><strong>자격별 최대 지원액</strong></p>
          <div className={styles.benefitPaymentGroup}>
            <p>{general.label} · {general.percent}%</p>
            <p className={styles.benefitAmount}><strong>최대 <BenefitMoney amount={general.one.initial} /></strong></p>
          </div>
          <CostReductionAmount>
            <p className={styles.benefitAmount}><strong>최대 <BenefitMoney amount={reduced.one.initial} /></strong></p>
          </CostReductionAmount>
        </div>
      </section>
      <section className={styles.benefitCard} aria-labelledby={`${id}-followup`}>
        <div className={styles.benefitComposition}>
          <Heading id={`${id}-followup`}>이후 청구</Heading>
          <p className={styles.benefitCalculationTitle}>관리비 계산 기준</p>
          <p>후기 적합관리</p>
          <p>연 <BenefitMoney amount={benefitBasis.annualFollowup} /> × 최대 {benefitBasis.followupCount}회</p>
          <p className={styles.benefitBasis}>기준액 합계 <BenefitMoney amount={followupBasis} /></p>
          <p className={styles.benefitBasisNote}>{basisExplanation}</p>
          <p className={styles.benefitTiming}>구입 1년 후부터 5년까지의 관리 비용이에요.<br />매년 1회 이상 실제 관리를 받은 뒤, 연 1회씩 따로 청구해요.</p>
        </div>
        <div className={styles.benefitPayments}>
          <p className={styles.benefitPaymentTitle}><strong>자격별 최대 지원액</strong></p>
          <div className={styles.benefitPaymentGroup}>
            <p>{general.label} · {general.percent}%</p>
            <p className={styles.benefitAmount}><strong>연 최대 <BenefitMoney amount={general.one.annual} /></strong></p>
            <p>{benefitBasis.followupCount}회 합계 최대 <BenefitMoney amount={general.one.followup} /></p>
          </div>
          <CostReductionAmount>
            <p className={styles.benefitAmount}><strong>연 최대 <BenefitMoney amount={reduced.one.annual} /></strong></p>
            <p>{benefitBasis.followupCount}회 합계 최대 <BenefitMoney amount={reduced.one.followup} /></p>
          </CostReductionAmount>
        </div>
      </section>
    </div>
    <section className={`${styles.benefitCard} ${styles.benefitTotal}`} aria-labelledby={`${id}-total`}>
      <div className={styles.benefitComposition}>
        <Heading id={`${id}-total`}>전체 합계</Heading>
        <p className={styles.benefitCalculationTitle}>전체 지원금 계산 기준</p>
        <p className={styles.benefitBasis}>전체 기준액 <BenefitMoney amount={benefitBasis.total} /></p>
        <p className={styles.benefitBasisNote}>{basisExplanation}</p>
        <p className={styles.scope}>처음 청구와 이후 관리 비용을 합한 금액이며, 한 번에 지급되는 돈은 아니에요.</p>
      </div>
      <div className={styles.benefitPayments}>
        <p className={styles.benefitPaymentTitle}><strong>자격별 최대 지원액</strong></p>
        <div className={styles.benefitPaymentGroup}>
          <p>{general.label} · {general.percent}%</p>
          <p className={styles.benefitAmount}><strong>총 최대 <BenefitMoney amount={general.one.total} /></strong></p>
        </div>
        <CostReductionAmount>
          <p className={styles.benefitAmount}><strong>총 최대 <BenefitMoney amount={reduced.one.total} /></strong></p>
        </CostReductionAmount>
      </div>
    </section>
    <p className={styles.scope}>초기 관리는 구입일부터 1년간, 후기 관리는 그 이후의 관리예요.</p>
    <details className={styles.disclosure}>
      <summary>내 지급액이 안내된 최대 금액과 달라질 수 있나요?</summary>
      <div>
        <BenefitConditions />
      </div>
    </details>
    {showSources && <BenefitSources />}
  </section>;
}
