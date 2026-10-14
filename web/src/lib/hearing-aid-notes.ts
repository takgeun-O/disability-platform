import type { ActionGuidance } from '@/data/hearingAidActionGuidance';

export interface InquiryInfo {
  currentState: string; institution: string; contactQuestion?: string | null;
  nextAction: string; nextActionNote?: string | null; secondary?: string | null;
}

/** A consultation memo only; never feeds the eligibility/journey calculation. */
export function buildInquiryMemo(info: InquiryInfo, guidance: ActionGuidance, checked: ReadonlySet<string>): string {
  const list = (kind: 'ask' | 'tasks') => guidance.checklist[kind]
    .map((text, index) => `${checked.has(`${kind}-${index}`) ? '[x]' : '[ ]'} ${text}`).join('\n');
  return [
    '보청기 지원 안내 · 문의 메모', `현재 상황\n${info.currentState}`,
    `01. 먼저 확인할 곳\n${info.institution}`, `02. 이곳에서 물어볼 질문\n${info.contactQuestion ?? ''}`,
    `문의 후 확인할 내용\n${list('ask')}`, `03. 할 일과 준비할 것\n${info.nextAction}\n${list('tasks')}`,
    [info.nextActionNote, info.secondary, guidance.caution].filter(Boolean).join('\n'),
    `04. 이번 단계 다음에는\n${guidance.afterTitle}\n${guidance.after}`,
    '[ ] 아직 확인 전 / [x] 내가 확인한 항목\n개인 확인 메모이며 지원 자격이나 절차 완료를 판정하지 않습니다.',
  ].filter(Boolean).join('\n\n');
}
