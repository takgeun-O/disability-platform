import { emptyRegistry, type RegistryContext } from './registry-content';
import type { Answers, DetailTarget } from './guided-content';

export type ConsultationQuestion = {
  id: string; target: 'center'; question: string; reason: string; detail: DetailTarget;
};
function question(id: string, text: string, reason: string, detail: DetailTarget = 'before-buying'): ConsultationQuestion {
  return { id, target: 'center', question: text, reason, detail };
}
const support = question('VISIT-SUPPORT', '센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?', '센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.', 'claim-method');
const care = question('VISIT-CARE', '구입 후 병원 확인과 센터 관리는 언제 받나요? 관리에 포함되는 서비스와 별도 비용도 알려주세요.', '다음 방문 장소·일정과 관리 범위·비용을 확인해요.');
const model = question('VISIT-MODEL', '제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.', '추천 이유와 비교 대안을 확인해요.');
const cost = question('VISIT-COST', '제가 실제로 내는 총금액과 포함된 서비스는 무엇인가요? 청구를 맡길 수 있는지도 알려주세요.', '제품·관리 비용과 청구를 맡기는 범위를 확인해요.', 'benefit');

// Three useful center questions, after the page has already explained the basic process.
export function getQuestionPlan(answers: Answers, registry: RegistryContext = emptyRegistry) {
  const [registration, coverage, prescription, history] = answers;
  let primary: ConsultationQuestion[];
  if (registration !== 'registered') {
    const stage = registration === 'planning' || registration === 'assessment' || registration === 'waiting' ? registration : 'unknown';
    const context = {
      planning: '아직 등록을 위한 검사 전이에요.', assessment: '장애등록 검사·진단 중이에요.',
      waiting: '등록 심사 결과를 기다리고 있어요.', unknown: '등록 진행 상태를 확인하고 있어요.',
    }[stage];
    primary = [
      question(`VISIT-REG-${stage}`, `${context} 구입을 결정하기 전에 제 청취 불편과 제품을 상담하고 비교해볼 수 있나요?`, '현재 받을 수 있는 상담·제품 비교의 범위를 확인해요.'),
      support,
      question('VISIT-NEXT', '지금 상담한 내용을 이어가려면 어떤 결과나 처방을 확인한 뒤 다시 방문하면 되나요?', '내 상황에 맞는 다음 센터 방문 시점과 준비할 내용을 확인해요.'),
    ];
  } else if (coverage !== 'health') {
    const context = coverage === 'medical-aid' ? '의료급여 대상이에요.' : '보험 자격은 아직 확인 중이에요.';
    const prescriptionContext = prescription === 'yes' ? '지원금 신청용 보청기 처방전은 받았어요.' : prescription === 'no' ? '지원금 신청용 보청기 처방전은 아직 없어요.' : prescription === 'unknown' ? '처방전 여부도 확인 중이에요.' : '';
    const consultation = [context, prescriptionContext, '현재 제품을 상담하고, 제 제도가 확인되면 신청도 도움받을 수 있나요?'].filter(Boolean).join(' ');
    primary = [
      question(`VISIT-COVERAGE-${coverage ?? 'unknown'}`, consultation, '지원 제도를 확정하기 전 상담할 내용과 센터의 신청 지원 범위를 확인해요.', 'scope'),
      question('VISIT-CONDITIONAL-COST', '지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?', '지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.', 'benefit'),
      question('VISIT-CONFIRM-NEXT', history && history !== 'first' ? '보험 자격과 이전 지원 기록을 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?' : '공식기관과 병원에서 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?', '미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.'),
    ];
  } else if (prescription !== 'yes') {
    primary = [
      question(`VISIT-RX-${prescription ?? 'unknown'}`, prescription === 'no' ? '아직 지원금 신청용 보청기 처방전이 없어요. 지금 받을 수 있는 제품 상담과 병원 진료 후 다시 가져올 내용은 무엇인가요?' : '받은 서류가 지원금 신청용 보청기 처방전인지 확인 중이에요. 확인 후 다시 상담할 때 무엇을 가져오면 되나요?', '현재 상담할 범위와 처방 확인 후 준비할 내용을 확인해요.', 'prescription-help'),
      model, support,
    ];
  } else if (history && history !== 'first') {
    primary = [
      history === 'previous'
        ? question('VISIT-HISTORY-previous', '이전에 지원받은 적이 있어요. 구입할 제품을 상담하고, 사용 중인 보청기가 있다면 조절·수리와 새 제품 구입도 비교해볼 수 있나요?', '구입할 제품과, 사용 중인 보청기가 있다면 관리 대안도 비교해요.')
        : question('VISIT-HISTORY-unknown', '이전 지원 여부를 확인 중이에요. 지금 상담할 수 있는 내용과 지원 이력 확인 후 구입 전에 점검할 내용을 알려주세요.', '현재 상담할 범위와 지원 기록 확인 후 점검할 내용을 구분해요.'),
      question('VISIT-HISTORY-COST', '이번 지원 여부가 확인되면 실제 부담액과 관리 서비스를 비교해 주세요. 청구는 어디까지 도와주시나요?', '지원 확인 후의 비용과 센터 지원 범위를 확인해요.', 'benefit'), care,
    ];
  } else {
    const selected = registry.product.status === 'verified' ? registry.product.product : undefined;
    primary = [selected ? question('VISIT-SELECTED-MODEL', `${selected.model}을 제게 추천하는 이유와 비교할 수 있는 다른 제품은 무엇인가요?`, '선택한 모델이 내 생활에 맞는 이유와 대안을 확인해요.') : model, cost, care];
  }
  return { primary, insuranceUnclear: coverage === 'unknown' || coverage === 'medical-aid' };
}
