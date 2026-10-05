import { claimRuleUrl, nhisPersonalConsultUrl, referenceReviewedAt, sections, sources } from './content';
import { Disclosure, Section, SourceLink } from './components';
import RegistrationHelp from './RegistrationHelp';
import BenefitBreakdown from './BenefitBreakdown';
import BilateralGuide from './BilateralGuide';
import { benefitBasis, formatBenefitWon } from './benefit-content';
import ClaimDocuments from './ClaimDocuments';
import { overviewStages, prescriptionExplanation } from './guided-content';
import styles from './page.module.css';

export default function ReferenceGuide() {
  return <>
    <div id="full-criteria" tabIndex={-1} className={styles.detailIntro}>
      <h2>전체 기준 · 처음부터 차근차근</h2>
      <p>청각장애 등록부터 보청기 구입 후 관리까지, 센터에 가기 전에 알아둘 내용이에요.</p>
      <p className={styles.scope}>아래는 건강보험 기준이에요. ‘급여’는 정해진 조건에 따라 비용을 지원한다는 뜻이에요.</p>
    </div>
    <div className={styles.readingLayout}>
      <nav aria-label="이 페이지에서 찾기" className={styles.toc}>
        <a className={styles.quietLink} href="#summary">← 30초 요약</a>
        <p className={styles.label}>이 페이지에서 찾기</p>
        <ol>{sections.map(([id, title]) => <li key={id}><a href={`#${id}`}>{title}</a></li>)}</ol>
      </nav>
      <div className={styles.article}>
        <Section id="registration-help" title="청각장애 등록은 어떻게 하나요?">
          <RegistrationHelp />
        </Section>
        <Section id="eligibility" title="보청기 지원은 누가 받을 수 있나요?">
          <p><strong>청력장애로 등록된 청각장애인 중 건강보험 가입자와 피부양자</strong>가 대상이에요. 피부양자는 가족의 건강보험에 등록된 사람을 말해요.</p>
          <p>이비인후과 전문의가 보청기가 도움이 된다고 판단하고 지원금 신청용 보청기 처방전을 발급해야 해요. 이후 등록 제품·업소에서 구입하고 검수와 청구를 거쳐요.</p>
          <Disclosure id="previous-benefit" question="예전에 지원받았다면 언제 다시 받을 수 있나요?">
            <p>보청기의 내구연한은 5년이에요. 지원받은 기기를 사용하는 기간의 기준으로, 5년마다 자동 입금된다는 뜻은 아니에요.</p>
            <p>새 보청기를 사기 전에 국민건강보험공단에 이전 구입·급여 이력과 다시 지원받을 수 있는 시점을 확인하세요. 기간 안에 교체가 필요한 경우도 사유에 따라 별도 확인이 필요해요.</p>
          </Disclosure>
          <Disclosure id="scope" question="의료급여 대상이거나 보험 자격을 모르겠어요">
            <p>의료급여는 건강보험과 신청 절차가 달라요. <strong>구입 전에 주소지 주민센터에</strong> 보청기 지원 신청과 사전 절차를 문의하세요.</p>
            <p>건강보험 자격이나 본인부담 경감 적용 여부는 국민건강보험공단에 확인할 수 있어요.</p><p><a href={nhisPersonalConsultUrl} target="_blank" rel="noopener noreferrer">공단 온라인 개인 상담 (로그인 · 새 탭)</a>에서 글로 문의하거나 지사에 방문하세요.</p>
          </Disclosure>
          <p className={styles.referenceSource}><SourceLink sourceKey="notice" /> · 별표 2·5</p>
        </Section>
        <BilateralGuide />
        <Section id="benefit" title="얼마를, 언제 지원받나요?">
          <p><strong>한쪽 기준액 {formatBenefitWon(benefitBasis.total)}은 한 번에 받는 돈이 아니에요.</strong> 제품과 초기 관리 비용, 이후 여러 해의 관리 비용을 합한 기준액이에요.</p>
          <BenefitBreakdown showSources={false} />
        </Section>
        <Section id="steps" title="등록부터 이후 관리까지, 5단계로 이어져요">
          <p>처음 지원받는 경우의 흐름이에요. 센터 상담은 구입 전에도 받을 수 있지만, 실제 구입은 등록·처방 등 지원 절차를 확인한 뒤 진행하세요.</p>
          <ol className={styles.referenceSteps}>
            {overviewStages.map((stage, index) => <li key={stage.label} id={index === 1 ? 'prescription-help' : index === 3 ? 'claim-method' : undefined} tabIndex={index === 1 || index === 3 ? -1 : undefined}>
              <span className={styles.stepNumber} aria-hidden="true">{index + 1}</span><div>
                <h3>{stage.label}</h3>
                <p className={styles.stageInstitution}>{stage.institution}</p>
                <p>{stage.description}</p>
                {index === 0 && <p>등록 심사는 국민연금공단, 건강보험 자격·이전 지원 기록 확인은 국민건강보험공단이 담당해요.</p>}
                {index === 1 && <p className={styles.scope}>{prescriptionExplanation}</p>}
                {index === 2 && <p>공단 등록 판매업소와 급여 등록 제품인지 각각 확인해요. 실제 부담할 금액과 관리 조건도 비교하세요.</p>}
                {index === 3 && <><p><strong>구입 후 1개월이 지난 뒤</strong> 병원 검수를 받아요. 검수확인서를 포함한 자료로 처음 지원금을 청구해요.</p><dl className={styles.claimContacts}><div><dt>위임 청구 시</dt><dd>판매업소에 맡겨요.</dd></div><div><dt>직접 청구 시</dt><dd>국민건강보험공단에 신청해요.</dd></div></dl></>}
                {index === 4 && <><p>구입 후 첫 1년은 초기 관리, 그 이후 5년까지는 후기 관리 구간이에요.</p><p>후기 관리는 <strong>구입 1년 후부터 매년 1회 이상 실제로 받은 뒤, 연 1회씩 최대 4회</strong> 따로 청구해요.</p></>}
              </div>
            </li>)}
          </ol>
          <Disclosure question="이미 구입했는데 등록·처방 절차를 거치지 않았어요">
            <p>구입했다고 무조건 지원되거나 지원이 불가능하다고 단정할 수는 없어요. 처방일·구입일·장애등록일과 구입 자료를 가지고 국민건강보험공단에 적용 가능 여부를 먼저 확인하세요.</p>
          </Disclosure>
          <p className={styles.referenceSource}><SourceLink sourceKey="notice" /> · 제5조의2·별표 4<br /><a href={claimRuleUrl} target="_blank" rel="noopener noreferrer">직접·위임 청구 기준 (새 탭)</a></p>
        </Section>
        <Section id="before-buying" title="센터에서는 이 세 가지를 물어보세요">
          <ol className={styles.consultationPrompts}>
            <li><h3>지원되는 제품·업소인지</h3><p>“공단 등록 판매업소인가요? 추천해 주신 정확한 모델명과 급여 등록 여부도 알려주세요.”</p></li>
            <li><h3>실제로 낼 비용과 관리 범위</h3><p>“제가 내는 총금액은 얼마인가요? 초기·후기 관리와 수리 비용은 어떻게 나뉘나요?”</p></li>
            <li><h3>센터가 도와주는 일과 내가 할 일</h3><p>“청구를 맡길 수 있나요? 제가 직접 병원에 가거나 받아올 서류는 무엇이고, 다음 방문은 언제인가요?”</p></li>
          </ol>
          <p><a href="#registry-check">추천받은 모델이 있다면 급여 제품 확인하기 →</a></p>
          <Disclosure id="documents" question="직접 청구하려면 서류를 어디서 받나요?">
            <ClaimDocuments />
          </Disclosure>
        </Section>
        <Section id="sources" title="공식 출처와 확인일">
          <p>등록·급여 기준 재확인: <time dateTime={referenceReviewedAt}>{referenceReviewedAt}</time>. 개인별 지급 여부와 금액은 국민건강보험공단에서 최종 확인해요.</p>
          <ul className={styles.sources}>{sources.map(source => <li key={source.key}><p className={styles.review}>{source.agency}</p><a href={source.url} target="_blank" rel="noopener noreferrer">{source.title} <span className={styles.sourceTabHint}>(새 탭)</span></a><p>{source.note}</p></li>)}</ul>
        </Section>
      </div>
    </div>
  </>;
}
