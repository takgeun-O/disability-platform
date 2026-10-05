import type { Answer } from './guided-content';
import styles from './page.module.css';

const steps = [
  { title: '검사·진단', institution: '이비인후과', text: '장애등록에 필요한 검사와 진단 자료를 받아요.' },
  { title: '등록 신청·자료 제출', institution: '주소지 주민센터', text: '신청서와 병원 자료를 접수해요.' },
  { title: '장애정도 심사', institution: '국민연금공단', text: '접수된 자료를 심사해요. 보완 자료를 요청할 수도 있어요.' },
  { title: '등록 결과 확인', institution: '주민센터', text: '심사 결과에 따른 등록 여부를 안내받아요.' },
];

export default function RegistrationSequence({ registration, compact = false }: { registration?: Answer; compact?: boolean }) {
  const current = registration === 'assessment' ? 0 : registration === 'waiting' ? 2 : null;
  return <ol className={`${styles.registrationSequence} ${compact ? styles.compactRegistration : ''}`} aria-label="청각장애 등록 과정">
    {steps.map((step, index) => <li key={step.title} aria-current={current === index ? 'step' : undefined}>
      <span aria-hidden="true">{index + 1}</span><div>
        <strong>{step.title}{current === index && <span className={styles.registrationHere}> · 지금</span>}</strong>
        <span className={styles.stageInstitution}>{step.institution}</span>
        {!compact && <p>{step.text}</p>}
      </div>
    </li>)}
  </ol>;
}
