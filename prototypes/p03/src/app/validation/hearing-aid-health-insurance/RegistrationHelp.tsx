import { SourceLink } from './components';
import styles from './page.module.css';
import RegistrationSequence from './RegistrationSequence';

export default function RegistrationHelp() {
  return <>
    <p><strong>병원에서 난청 진단을 받는 것과 청각장애 등록은 달라요.</strong> 등록은 진단 자료를 제출하고 심사를 거치는 행정 절차예요.</p>
    <p>처음이라면 주소지 주민센터에서 신청 방법과 진단받을 병원을 안내받으세요.</p>
    <RegistrationSequence />
    <p className={styles.scope}>이미 진단서와 검사 자료를 받았다면 주민센터에 제출 가능한지 물어보세요. 등록했는지 모르겠을 때도 주민센터에서 확인할 수 있어요.</p>
    <blockquote className={styles.askExample}><strong>이렇게 물어보세요</strong><p>“청각장애 등록을 처음 신청하려고 해요. 어떤 병원 검사와 서류가 필요한가요?”</p></blockquote>
    <p>등록 심사는 <strong>국민연금공단</strong>, 보청기 건강보험 지원은 <strong>국민건강보험공단</strong>이 담당해요. 등록 후 보청기 지원은 아래 절차로 따로 신청해요.</p>
    <p className={styles.referenceSource}><SourceLink sourceKey="registration" /></p>
  </>;
}
