export type DetailTarget = 'registration-help' | 'eligibility' | 'scope' | 'prescription' | 'prescription-help' | 'document-details' | 'claim-method' | 'user-preparation' | 'previous-benefit' | 'steps' | 'before-buying' | 'benefit' | 'sources';
export type Answer = 'planning' | 'assessment' | 'waiting' | 'registered' | 'unknown' | 'health' | 'medical-aid' | 'yes' | 'no' | 'first' | 'previous';
export type Answers = readonly Answer[];
export type StageState = { kind: 'done' | 'current' | 'check' | 'later'; label: string };
export type Guidance = {
  summary: string; phase: number | null; position: string;
  facts: { label: string; text: string }[]; pending: string[];
  action: { institution: string; instruction: string; ask: string; online?: boolean };
  secondary?: string; center: string; showHealthDetails: boolean;
};
export type Choice = { label: string; value: Answer };
export const prescriptionExplanation = '장애등록을 위한 진단서나 보청기를 권유받은 것과 달라요. 지원금 신청에 쓰는 공식 서류는 ‘보조기기 처방전’이에요.';
export const questions: { title: string; support: string; hint?: string; shortTitle: string; choices: Choice[] }[] = [
  {
    title: '청각장애 진단·등록은 어디까지 진행하셨나요?', shortTitle: '진단·등록 상태',
    support: '병원 검사·진단 뒤에는 주민센터에 자료를 내고 심사를 거쳐야 등록돼요.',
    hint: '주민센터에서 청각장애 등록 완료 안내를 받았는지 떠올려 보세요. 다른 장애로 등록된 것과는 구분해요.',
    choices: [
      { label: '아직 등록을 위한 검사·신청 전이에요', value: 'planning' },
      { label: '병원에서 검사·진단을 받고 있어요', value: 'assessment' },
      { label: '등록 신청 후 심사 결과를 기다려요', value: 'waiting' },
      { label: '청각장애 등록을 마쳤어요', value: 'registered' },
      { label: '어디까지 진행됐는지 잘 모르겠어요', value: 'unknown' },
    ],
  },
  {
    title: '건강보험과 의료급여 중 어디에 해당하나요?', shortTitle: '보험 자격',
    support: '직장·지역 건강보험이나 가족의 건강보험에 포함되어 있다면 건강보험이에요. 의료급여는 생활이 어려운 분 등의 의료비를 지원하는 별도 제도예요.',
    hint: '모르면 처방전 등 아는 내용부터 이어서 확인해요. 보험 자격은 미확인으로 남겨둘게요.',
    choices: [{ label: '건강보험에 가입되어 있어요 (가족 포함)', value: 'health' }, { label: '의료급여 대상이에요', value: 'medical-aid' }, { label: '잘 모르겠어요', value: 'unknown' }],
  },
  {
    title: '이비인후과에서 지원금 신청용 보청기 처방전을 받았나요?', shortTitle: '지원금 신청용 처방전',
    support: prescriptionExplanation, hint: '서류 이름이 기억나지 않으면 ‘잘 모르겠어요’를 선택하세요.',
    choices: [{ label: '예, 지원금 신청용 보청기 처방전을 받았어요', value: 'yes' }, { label: '아직 안 받았어요', value: 'no' }, { label: '잘 모르겠어요', value: 'unknown' }],
  },
  {
    title: '이전에 보청기 지원금을 받은 적이 있나요?', shortTitle: '이전 지원 이력',
    support: '처음 신청하는지, 이전 지원 이력을 확인해야 하는지 알아보기 위한 질문이에요.',
    choices: [{ label: '처음이에요', value: 'first' }, { label: '받은 적 있어요', value: 'previous' }, { label: '기억나지 않아요', value: 'unknown' }],
  },
];

// Used by both UI and regression checks. Editing invalidates downstream answers.
export function replaceAnswer(answers: Answers, index: number, value: Answer): Answer[] {
  return [...answers.slice(0, index), value];
}
export function answersForPreviousQuestion(answers: Answers, index: number): Answer[] {
  return answers.slice(0, Math.max(0, index - 1) + 1);
}

const registrationGuides: Partial<Record<Answer, Guidance>> = {
  planning: {
    summary: '청각장애 등록을 위한 검사·신청을 아직 시작하지 않았어요.', phase: 0, position: '등록 절차 알아보기',
    facts: [{ label: '아직 시작 전', text: '장애등록을 위한 검사·신청' }], pending: ['등록 신청 방법'],
    action: { institution: '주소지 주민센터', instruction: '방문해서 청각장애 등록 신청 방법과 진단받을 병원을 안내받으세요.', ask: '청각장애 등록을 처음 알아보고 있어요. 어떤 병원에서 검사받고, 검사 후에는 어디에 자료를 내면 되나요?' },
    center: '센터에서는 불편한 청취 상황과 제품을 상담할 수 있어요. 실제 구입은 등록·처방과 지원 절차를 확인한 뒤 결정하세요.', showHealthDetails: false,
  },
  assessment: {
    summary: '병원에서 청각장애 등록을 위한 검사·진단을 진행 중이에요.', phase: 0, position: '등록 과정 중 검사·진단',
    facts: [{ label: '진행 중', text: '병원 검사·진단' }, { label: '확인할 일', text: '남은 검사와 주민센터에 제출할 시점' }], pending: ['남은 검사·진단 일정'],
    action: { institution: '지금 진료받는 이비인후과', instruction: '다음 진료 때 남은 검사와 등록 신청용 자료를 받을 시점을 확인하세요.', ask: '청각장애 등록을 위한 검사를 진행 중이에요. 남은 검사와 진단 자료를 받는 날짜, 주민센터에 제출할 시점을 알려주세요.' },
    center: '검사 중에도 제품 상담은 받을 수 있어요. 상담 때 검사 중이라고 알리고, 실제 구입은 등록 결과와 지원금 신청용 처방을 확인한 뒤 준비하세요.', showHealthDetails: false,
  },
  waiting: {
    summary: '등록 신청을 마치고 청각장애 심사 결과를 기다리고 있어요.', phase: 0, position: '등록 과정 중 심사·결과 대기',
    facts: [{ label: '마친 일', text: '등록 신청' }, { label: '기다리는 일', text: '심사 결과와 등록 여부 안내' }], pending: ['등록 심사 결과'],
    action: { institution: '신청한 주민센터', instruction: '방문하거나 안내받은 연락 방법으로 결과 통지 방법과 추가 자료 요청 여부를 확인하세요.', ask: '청각장애 등록 심사 결과를 기다리고 있어요. 결과는 어떤 방법으로 안내받고, 지금 추가로 제출할 자료가 있나요?' },
    center: '결과를 기다리는 동안 제품·비용 상담은 받을 수 있어요. 등록되면 지원금 신청용 처방을 확인하고 구입을 준비해요.', showHealthDetails: false,
  },
  unknown: {
    summary: '청각장애 등록이 어디까지 진행됐는지 확인이 필요해요.', phase: 0, position: '등록 진행 상태 확인 필요',
    facts: [{ label: '확인 필요', text: '등록 신청 기록과 청각장애 등록 여부' }], pending: ['등록 진행 상태'],
    action: { institution: '주소지 주민센터', instruction: '방문해서 등록 신청 기록과 결과가 있는지 확인하세요. 아래 문장을 보여줘도 돼요.', ask: '제가 청각장애 등록을 신청한 기록이 있나요? 심사 중인지, 등록이 완료됐는지와 다음에 제가 할 일을 확인해 주세요.' },
    secondary: '신청 기록이 없고 병원 검사만 받았다면, 그 병원에서 장애등록용 검사였는지 확인하세요.',
    center: '센터에서는 알고 있는 검사·진료 내용으로 상담받고, 현재 상태에 맞춰 도와줄 수 있는 범위를 물어보세요.', showHealthDetails: false,
  },
};

// Only supplied facts become completed steps. This guide never decides eligibility.
export function getGuidance(answers: Answers): Guidance | null {
  const [registration, coverage, prescription, history] = answers;
  if (!registration) return null;
  if (registrationGuides[registration]) return registrationGuides[registration]!;
  if (!coverage) return null;
  if (coverage === 'medical-aid') return {
    summary: '청각장애 등록을 마쳤고, 의료급여 대상이라고 답하셨어요.', phase: null, position: '의료급여 사전 절차 확인 필요',
    facts: [{ label: '마친 일', text: '청각장애 등록' }, { label: '확인 필요', text: '의료급여의 구입 전 신청·승인 절차' }], pending: ['의료급여 사전 절차'],
    action: { institution: '주소지 주민센터', instruction: '구입 전에 방문해서 의료급여 보청기 지원의 신청·승인 순서를 확인하세요.', ask: '청각장애 등록을 마친 의료급여 대상자예요. 보청기를 사기 전에 처방과 신청·승인을 어떤 순서로 진행해야 하나요?' },
    center: '센터에서는 의료급여 신청을 돕는 범위와 제품 상담을 받을 수 있어요. 건강보험의 금액·청구 절차를 그대로 적용하지 않아요.', showHealthDetails: false,
  };
  // Unknown coverage continues through the common prescription/history questions.
  if (!prescription || (prescription === 'yes' && !history)) return null;
  const facts: Guidance['facts'] = [{ label: '마친 일', text: prescription === 'yes' ? '청각장애 등록 · 지원금 신청용 보청기 처방전 받기' : '청각장애 등록' }];
  if (prescription === 'no') facts.push({ label: '아직 받기 전', text: '지원금 신청용 보청기 처방전' });
  if (prescription === 'unknown') facts.push({ label: '확인 필요', text: '지원금 신청용 보청기 처방전을 받았는지' });
  if (history === 'first') facts.push({ label: '답변한 내용', text: '보청기 지원금은 처음 신청' });
  if (history === 'previous') facts.push({ label: '이전 지원 있음', text: '지원받은 날짜와 이번에 다시 지원받을 수 있는지 확인 필요' });
  if (history === 'unknown') facts.push({ label: '확인 필요', text: '이전 보청기 지원 이력' });
  if (coverage === 'unknown') {
    facts.push({ label: '미확인', text: '건강보험·의료급여 중 해당하는 제도' });
    return {
      summary: prescription === 'yes' ? '청각장애 등록과 지원금 신청용 처방은 마쳤고, 보험 자격은 확인이 필요해요.' : '청각장애 등록은 마쳤고, 보험 자격은 확인이 필요해요.',
      phase: null, position: '보험 자격 확인 필요', facts,
      pending: ['보험 자격', ...(prescription === 'unknown' ? ['처방전 여부'] : []), ...(history && history !== 'first' ? ['이전 지원 이력'] : [])],
      action: { institution: '국민건강보험공단', instruction: '온라인 개인 상담이나 지사 방문으로 현재 보험 자격과 보청기 지원 절차를 확인하세요.', ask: `제가 현재 건강보험과 의료급여 중 어디에 해당하나요? 보청기 지원을 받으려면 구입 전에 무엇을 확인해야 하나요?${history && history !== 'first' ? ' 이전 보청기 지원 기록도 어디에서 확인할 수 있나요?' : ''}`, online: true },
      secondary: prescription === 'unknown' ? '처방전 여부도 미확인이에요. 진료받은 병원에 “지원금 신청용 보청기 처방전을 발급받았나요?”라고 물어보세요.' : prescription === 'no' ? '처방전은 아직 받기 전이에요. 보험 자격을 확인한 뒤 해당 절차에 맞춰 이비인후과 진료를 준비하세요.' : undefined,
      center: '보험 자격을 확인하는 동안 제품 상담은 받을 수 있어요. 센터에는 자격이 미확인이라고 알리고, 지원액과 실제 구입 시점은 자격 확인 후 정하세요.', showHealthDetails: false,
    };
  }
  if (prescription !== 'yes') return {
    summary: prescription === 'no' ? '청각장애 등록은 마쳤고, 지원금 신청용 보청기 처방전은 아직 받기 전이에요.' : '청각장애 등록은 마쳤고, 받은 서류가 지원금 신청용 보청기 처방전인지 확인이 필요해요.',
    phase: 1, position: prescription === 'no' ? '병원 처방 전' : '처방전 여부 확인 필요', facts, pending: [prescription === 'no' ? '지원금 신청용 처방전' : '처방전 여부'],
    action: { institution: prescription === 'no' ? '진료받을 이비인후과' : '진료받은 이비인후과', instruction: prescription === 'no' ? '방문 전 접수처에 지원금 신청용 보청기 처방 진료와 예약·준비사항을 문의하세요.' : '받은 서류가 있으면 병원 접수처에 보여주고, 지원금 신청용 처방전 발급 여부를 확인하세요.', ask: prescription === 'no' ? '청각장애 등록을 마쳤어요. 보청기 건강보험 지원을 위한 처방 진료를 받으려면 어떻게 예약하고 무엇을 가져가면 되나요?' : '제가 받은 서류가 지원금 신청용 보청기 처방전인가요? 아니라면 어떤 진료가 더 필요한가요?' },
    center: '센터에서는 제품과 비용을 미리 상담할 수 있어요. 실제 구입 전에 처방전과 지원 절차를 확인하세요.', showHealthDetails: false,
  };
  if (history !== 'first') return {
    summary: history === 'previous' ? '지원금 신청용 처방전은 받았고, 이전 지원 날짜와 재지원 조건을 확인해야 해요.' : '지원금 신청용 처방전은 받았고, 이전 지원 이력은 확인이 필요해요.',
    phase: null, position: '구입 전, 이전 지원 이력 확인 필요', facts, pending: ['이전 지원 이력'],
    action: {
      institution: '국민건강보험공단',
      instruction: history === 'previous'
        ? '온라인 개인 상담이나 지사 방문으로 이전 보청기 구입·지원 기록과 이번 지원 가능 시점을 확인하세요.'
        : '온라인 개인 상담이나 지사 방문으로 이전 보청기 지원 기록이 있는지부터 확인하세요.',
      ask: history === 'previous'
        ? '이전에 보청기 건강보험 지원을 받은 기록과 구입 날짜를 확인해 주세요. 이번에 다시 지원받으려면 어떤 조건을 확인해야 하나요?'
        : '이전에 보청기 건강보험 지원을 받은 기록이 있는지 확인해 주세요. 기록이 있다면 지원받은 날짜와 이번 신청 전에 확인할 조건을 알려주세요.',
      online: true,
    },
    center: history === 'previous'
      ? '센터에서는 제품과 관리 조건을 상담하고, 사용 중인 보청기가 있다면 조절·수리도 함께 물어보세요. 실제 구입은 이력 확인 후 결정해요. 내구연한은 5년이지만, 기간이 지났다고 재지원이 확정되지는 않아요.'
      : '센터에서는 지금 상담할 수 있는 내용과 지원 이력 확인 후 구입 전에 점검할 내용을 물어보세요. 실제 구입은 기록 유무와 적용 조건을 확인한 뒤 결정해요.',
    showHealthDetails: true,
  };
  return {
    summary: '청각장애 등록과 지원금 신청용 처방을 마쳤고, 지원금은 처음 신청한다고 답하셨어요.',
    phase: 2, position: '구입 전 제품·비용 상담', facts, pending: [],
    action: { institution: '상담할 보청기센터', instruction: '제품 추천 이유와 실제 부담할 금액, 신청을 도와주는 범위를 상담하세요. 공단 등록 업소·제품인지도 함께 확인해요.', ask: '지원금은 처음 신청해요. 공단 등록 업소와 제품인지, 제가 내는 총금액과 포함된 관리 서비스, 구입 전에 직접 할 일을 알려주세요.' },
    center: '상담한 뒤 제품과 구입 시점을 결정하세요. 구입 이후에도 병원 확인과 센터 관리가 이어져요.', showHealthDetails: true,
  };
}

export const overviewStages = [
  { label: '등록 확인', words: ['등록', '확인'], institution: '이비인후과 · 주민센터 · 국민연금공단', description: '검사·진단 자료를 제출하고 심사를 거쳐 청각장애 등록 결과를 확인해요.', icon: 'office' },
  { label: '병원 처방', words: ['병원', '처방'], institution: '이비인후과', description: '장애등록용 진단과 별도로, 지원금 신청용 보청기 처방전을 받아요.', icon: 'hospital' },
  { label: '구입·착용', words: ['구입', '착용'], institution: '보청기센터 · 판매업소', description: '제품과 비용을 상담하고, 지원 절차를 확인한 뒤 구입해 착용해요.', icon: 'hearing' },
  { label: '검수·청구', words: ['검수', '청구'], institution: '이비인후과 · 판매업소 · 국민건강보험공단', description: '구입 후 병원에서 착용 효과를 확인받고 지원금을 청구해요.', icon: 'document' },
  { label: '이후 관리', words: ['이후', '관리'], institution: '보청기센터', description: '구입 후에도 보청기를 조절·관리하며 다음 방문 일정을 정해요.', icon: 'care' },
] as const;

export function getJourneyStates(answers: Answers): StageState[] {
  const [registration, coverage, prescription, history] = answers;
  const later: StageState = { kind: 'later', label: '이후 과정' };
  const registered = registration === 'registered';
  return [
    registered ? { kind: 'done', label: '등록 완료' } : registration === 'unknown' ? { kind: 'check', label: '진행 상태 확인 필요' } : { kind: 'current', label: registration === 'waiting' ? '심사 결과 대기' : registration === 'assessment' ? '검사·진단 중' : '시작 전' },
    prescription === 'yes' ? { kind: 'done', label: '처방전 받음' } : prescription === 'unknown' ? { kind: 'check', label: '처방전 여부 확인 필요' } : prescription === 'no' ? { kind: 'current', label: '처방전 받기 전' } : later,
    registered && (coverage !== 'health' || (history && history !== 'first')) ? { kind: 'check', label: '구입 전 확인 필요' } : prescription === 'yes' && history === 'first' ? { kind: 'current', label: '제품·비용 상담' } : later,
    later, later,
  ];
}

// Question topic and actual state are distinct. Scroll never changes either.
export function getOverviewState(questionIndex: number, result: Guidance | null) {
  const phase = result ? result.phase : [0, null, 1, null][questionIndex];
  return { phase, label: result ? '내 현재 상태' : '지금 확인하는 내용', text: result?.position ?? questions[questionIndex].shortTitle };
}
