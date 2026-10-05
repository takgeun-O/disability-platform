'use client';

import { Disclosure, Section, SourceLink } from './components';
import { BenefitMoney, CostReductionAmount } from './BenefitBreakdown';
import { benefitBasis, benefitExamples, initialBasis, productBasis } from './benefit-content';
import { bilateralReviewedAt, bilateralRuleUrl, caregiverQuestions, caregiverQuestionNote, childHearingProgramUrl } from './bilateral-content';
import QuestionTakeaway from './QuestionTakeaway';
import { focusContent } from './DetailNavigation';
import { ReturnToTask } from './flow-context';
import styles from './page.module.css';

function AmountExample({ example }: { example: typeof benefitExamples[number] }) {
  return <dl className={styles.bilateralAmountList}>
    <div><dt>처음 청구 · 제품 + 초기 관리</dt><dd>
      <p className={styles.benefitAmount}><strong>최대 <BenefitMoney amount={example.two.initial} /></strong></p>
      <p className={styles.scope}>한쪽 최대 <BenefitMoney amount={example.one.initial} /> × 2개</p>
    </dd></div>
    <div><dt>이후 관리 · 실제 관리 후 별도 청구</dt><dd>
      <p className={styles.benefitAmount}><strong>연 최대 <BenefitMoney amount={example.two.annual} /></strong></p>
      <p className={styles.scope}>한쪽 연 최대 <BenefitMoney amount={example.one.annual} /> × 2개</p>
      <p>최대 {benefitBasis.followupCount}회 합계 <BenefitMoney amount={example.two.followup} /></p>
    </dd></div>
    <div><dt>전체 합계 · 초기·후기 관리 포함</dt><dd>
      <p className={styles.benefitAmount}><strong>총 최대 <BenefitMoney amount={example.two.total} /></strong></p>
      <p className={styles.scope}>처음 <BenefitMoney amount={example.two.initial} /> + 후기 <BenefitMoney amount={example.two.followup} /></p>
    </dd></div>
  </dl>;
}

export default function BilateralGuide() {
  return <Section id="one-or-two" title="아이의 보청기, 양쪽 모두 지원받을 수 있나요?">
    <ReturnToTask />
    <p className={styles.eyebrow}>아동·청소년 보호자 안내</p>
    <p>이 건강보험 제도의 보청기 지원은 기본적으로 한쪽 기준이에요. <strong>19세 미만의 청각장애 등록자</strong>는 추가 검사 조건을 모두 충족하면 양쪽 보청기 지원이 가능해요.</p>
    <p><strong>나이만으로 양쪽 지원이 확정되지는 않아요.</strong></p>
    <p className={styles.scope}>아래는 공통 안내예요. 개인별 지원 자격과 실제 금액은 확인이 필요해요.</p>

    <section className={styles.visitNext} aria-labelledby="caregiver-next-action">
      <h3 id="caregiver-next-action">지금 확인할 일</h3>
      <p>이비인후과에서 아이가 양쪽 지원 조건에 해당하는지 물어보세요. 검사 수치를 직접 해석하지 않아도 괜찮아요.</p>
      <a className={styles.quietLink} href="#caregiver-questions-title" onClick={event => {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        focusContent('caregiver-questions-title');
      }}>병원·센터 질문 보기 ↓</a>
    </section>

    <Disclosure question="병원에서는 어떤 검사 조건을 확인하나요?">
      <p>기본적으로 청력장애로 등록된 청각장애인 중 건강보험 가입자·피부양자여야 하며, 보청기 사용이 일상생활에 도움이 된다고 이비인후과 전문의가 판단해야 해요. 이 한쪽 지원 기본 요건에 더해 다음 조건을 <strong>모두</strong> 충족해야 해요.</p>
      <ul className={styles.bilateralConditions}>
        <li>19세 미만</li>
        <li>양쪽 모두 80dB 미만의 난청 <span className={styles.scope}>— dB(데시벨)는 청력검사에서 사용하는 소리 크기 단위예요.</span></li>
        <li>양쪽 말소리명료도 각각 50% 이상 <span className={styles.scope}>— 들은 말소리를 얼마나 정확히 구별하는지 보는 검사예요.</span></li>
        <li>양쪽 순음청력역치 차이 15dB 이하 <span className={styles.scope}>— 검사 소리를 겨우 들을 수 있는 크기가 두 귀에서 얼마나 다른지 봐요.</span></li>
        <li>양쪽 말소리명료도 차이 20% 이하</li>
      </ul>
      <p className={styles.scope}>의식이 명료하지 않거나 보청기를 사용할 수 없다고 이비인후과 전문의가 판단한 경우에는 한쪽·양쪽 모두 적용에서 제외돼요.</p>
      <p className={styles.referenceSource}><SourceLink sourceKey="notice" /> · 별표 2 제4호</p>
    </Disclosure>

    <section className={styles.benefitBreakdown} aria-labelledby="bilateral-amount-title">
      <h3 id="bilateral-amount-title">양쪽 보청기 2개 합산 최대 금액 예시</h3>
      <p><strong>양쪽 지원 대상자로 인정된 경우</strong>의 예시예요. 두 보청기 각각에 최대 지급 기준이 적용되고 후기 관리 조건까지 충족했을 때의 합계예요.</p>
      <p className={styles.scope}>공식 기준에 따라 계산한 양쪽 최대 금액 예시예요. 실제 지원액은 자격과 제품·관리 조건에 따라 달라져요.</p>
      <p className={styles.scope}>초기·후기 관리는 보청기를 잘 사용할 수 있도록 조절·관리하는 서비스예요.</p>
      <div className={styles.bilateralAmounts}>
        {benefitExamples.map(example => <div key={example.id} className={styles.bilateralAmountCard}>
          {example.id === 'reduced' ? <CostReductionAmount><AmountExample example={example} /></CostReductionAmount> : <>
            <p className={styles.benefitEligibility}>{example.label} · {example.percent}%</p>
            <AmountExample example={example} />
          </>}
        </div>)}
      </div>
      <ul className={styles.bilateralConditions}>
        <li>실제 지급액은 각 제품의 고시금액·구입금액과 관리 조건 등에 따라 달라져요. 100%도 지원 대상으로 인정되는 금액 기준이에요.</li>
        <li>처음 비용은 구입 후 1개월이 지난 뒤 병원 검수확인을 받고 청구해요. 후기 관리비는 구입 1년 후부터 5년까지, 매년 1회 이상 실제 관리를 받은 뒤 연 1회씩 따로 청구해요. <strong>전체 합계가 한 번에 지급되는 것은 아니에요.</strong></li>
        <li><strong>아동이라는 이유만으로 100% 지원 자격이 되는 것은 아니에요.</strong> 본인부담경감 적용 여부는 국민건강보험공단에 확인하세요.</li>
      </ul>
    </section>

    <Disclosure question="공식 근거와 양쪽 금액 계산 방법 보기">
      <h3>공식 자료에 직접 명시된 기준</h3>
      <ul className={styles.bilateralConditions}>
        <li>시행규칙 별표 7 제1호 라목 및 라목 1): 내구연한 내 1인당 한 번이라는 일반원칙의 예외로, 보청기를 양쪽에 장착하면 각각을 1회로 봐요.</li>
        <li>같은 별표 제3호: 기준액·제품별 고시금액·실구입금액 중 가장 낮은 지급기준금액에 일반 대상 90%, 해당 본인부담경감 대상 100%를 적용해요.</li>
        <li>고시 별표 2 제4호: 양쪽 지원에는 기본 대상 요건과 연령·검사 조건을 모두 충족해야 해요.</li>
        <li>고시 별표 5 아목 10): 보청기 한쪽 기준액 <BenefitMoney amount={benefitBasis.total} />에는 적합관리급여 <BenefitMoney amount={benefitBasis.initialManagement + benefitBasis.annualFollowup * benefitBasis.followupCount} />이 포함돼요. 내구연한은 5년이에요.</li>
        <li>고시 제5조의2·별표 4: 초기 관리 <BenefitMoney amount={benefitBasis.initialManagement} />은 제품 급여와 함께 지급해요. 제품별 고시금액에는 초기 관리비가 이미 포함돼요. 후기 관리는 보청기 급여를 받은 기기를 지속적으로 사용하고 실제 관리를 받은 경우, 연 <BenefitMoney amount={benefitBasis.annualFollowup} /> 기준 최대 {benefitBasis.followupCount}회 별도로 청구해요.</li>
      </ul>
      <h3>이 기준을 적용해 계산한 금액</h3>
      <p>아래는 공식 기준을 적용해 IYUM에서 계산한 합산 상한이에요. 고시에 이 양쪽 합계 금액이 직접 적혀 있다는 뜻은 아니에요.</p>
      <p>한쪽 제품 비용 <BenefitMoney amount={productBasis} /> = 전체 기준액 <BenefitMoney amount={benefitBasis.total} /> − 초기·후기 관리 기준액 <BenefitMoney amount={benefitBasis.initialManagement + benefitBasis.annualFollowup * benefitBasis.followupCount} />. 처음 청구 기준액 <BenefitMoney amount={initialBasis} /> = 제품 <BenefitMoney amount={productBasis} /> + 초기 관리 <BenefitMoney amount={benefitBasis.initialManagement} />.</p>
      <ul className={styles.bilateralConditions}>{benefitExamples.map(example => <li key={example.id}>
        <strong>{example.label} · {example.percent}%</strong><br />
        처음: <BenefitMoney amount={initialBasis} /> × {example.percent}% × 2개 = 최대 <BenefitMoney amount={example.two.initial} /><br />
        후기: <BenefitMoney amount={benefitBasis.annualFollowup} /> × {example.percent}% × 2개 × {benefitBasis.followupCount}회 = 최대 <BenefitMoney amount={example.two.followup} /><br />
        전체: <BenefitMoney amount={example.two.initial} /> + <BenefitMoney amount={example.two.followup} /> = 최대 <BenefitMoney amount={example.two.total} />
      </li>)}</ul>
      <p>위 합산 금액은 각 기기의 최대 지급 기준과 관리 조건을 충족한다는 전제로 산출했어요. 개인별 지급 여부와 실제 금액은 공단에서 확인해야 해요.</p>
      <p className={styles.benefitSources}><a href={bilateralRuleUrl} target="_blank" rel="noopener noreferrer">국민건강보험법 시행규칙 · 별표 7 제1호 라목·제3호 (PDF · 새 탭)</a><br />
        <SourceLink sourceKey="notice" /> · 제5조의2, 별표 2·4·5<br />
        양쪽 지원 근거 확인일: <time dateTime={bilateralReviewedAt}>{bilateralReviewedAt}</time>. 시행규칙은 2026-08-11 시행본의 별표 7(2026-03-25 개정), 고시는 제2026-56호(2026-03-25 시행)를 확인했어요.
      </p>
    </Disclosure>

    <section className={styles.visitQuestions} aria-labelledby="caregiver-questions-title">
      <h3 id="caregiver-questions-title" className={styles.questionJumpTarget} tabIndex={-1}>병원과 센터에서 이렇게 물어보세요</h3>
      <ol>{caregiverQuestions.map(({ institution, question }) => <li key={institution}><h4>{institution}</h4><p>{question}</p></li>)}</ol>
      <QuestionTakeaway text={caregiverQuestionNote} successMessage="보호자 질문을 기관명과 함께 복사했어요. 휴대폰 메모 등에 붙여 넣어 보관하세요." />
    </section>
    <aside className={styles.referenceInset} aria-labelledby="child-program-title">
      <h3 id="child-program-title">장애등록 기준에 해당하지 않는 아이는요?</h3>
      <p>청각장애 등록 기준에 해당하지 않는 아이도 별도의 난청 보청기 지원사업 대상일 수 있어요. <strong>건강보험 보청기 급여와는 다른 사업</strong>이므로 해당 사업의 조건을 확인하세요.</p>
      <p><a href={childHearingProgramUrl} target="_blank" rel="noopener noreferrer">복지로 · 선천성 난청검사 및 보청기 지원 (새 탭)</a></p>
    </aside>
    <ReturnToTask />
  </Section>;
}
