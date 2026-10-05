import { getJourneyStates, overviewStages, type Answers, type Guidance } from './guided-content';
import RegistrationSequence from './RegistrationSequence';
import styles from './page.module.css';

export default function ResultJourney({ answers, result }: { answers: Answers; result: Guidance }) {
  const states = getJourneyStates(answers);
  const health = answers[1] === 'health';
  return <section className={styles.resultJourney} aria-labelledby="result-journey-title">
    <h3 id="result-journey-title">전체 절차 속 내 위치</h3>
    {!health && <p className={styles.scope}>{answers[1] === 'medical-aid'
      ? '아래는 기관별 큰 흐름이에요. 의료급여의 신청·승인과 청구 순서는 구입 전 주민센터에서 확인하세요.'
      : '아래는 기본 흐름이에요. 보험 자격에 따라 구입 전 승인과 청구 절차가 달라질 수 있어요.'}</p>}
    {result.phase === null && <p className={styles.pendingNotice}><strong>구입 전 확인 필요</strong> · {result.pending.join(' · ')}</p>}
    <ol className={styles.resultStages}>
      {overviewStages.map((stage, index) => <li key={stage.label} data-state={states[index].kind} aria-current={result.phase === index ? 'step' : undefined}>
        <span className={styles.stageNumber} aria-hidden="true">{index + 1}</span>
        <div className={styles.stageContent}>
          <div className={styles.stageHeading}><h4>{stage.label}</h4><span className={styles.stageState}>{states[index].label}</span></div>
          <p className={styles.stageInstitution}>{index === 3
            ? answers[1] === 'medical-aid' ? '이비인후과 · 청구 절차는 주민센터에서 확인'
              : health ? '이비인후과 · 위임 청구: 판매업소 / 직접 청구: 국민건강보험공단'
                : '이비인후과 · 건강보험 청구: 판매업소 위임 또는 국민건강보험공단'
            : stage.institution}</p>
          <p>{stage.description}</p>
          {index === 0 && answers[0] !== 'registered' && <>
            <p className={styles.scope}>처음이라면 주소지 주민센터에서 신청 방법을 안내받으세요.</p>
            <RegistrationSequence registration={answers[0]} compact />
          </>}
        </div>
      </li>)}
    </ol>
  </section>;
}
