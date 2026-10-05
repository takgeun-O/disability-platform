import { claimRuleUrl } from './content';
import styles from './page.module.css';

export default function ClaimDocuments() {
  return <>
    <dl className={styles.documents}>
      <div><dt>병원</dt><dd>지원금 신청용 보청기 처방전·검사 자료와 구입 후 검수확인서를 받아요.</dd></div>
      <div><dt>판매업소</dt><dd>구매 계약서·영수증 등 구입 증빙과 제품 바코드 확인 자료를 받아요.</dd></div>
      <div><dt>국민건강보험공단</dt><dd>지급청구서와 위 자료를 제출해요. 내 상황의 추가 서류와 접수 방법은 제출 전에 확인하세요.</dd></div>
    </dl>
    <p>판매업소에 위임한다면 위임장·신분증 사본 등 본인이 제공할 자료를 안내받으세요. 병원에서 받아야 할 자료까지 센터가 모두 대신해 주는 것은 아니에요.</p>
    <p><a href={claimRuleUrl} target="_blank" rel="noopener noreferrer">청구 서류의 공식 기준 (새 탭)</a></p>
  </>;
}
