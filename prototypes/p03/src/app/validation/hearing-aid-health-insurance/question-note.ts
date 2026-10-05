import type { ConsultationQuestion } from './consultation-content';
import type { Guidance } from './guided-content';

export function makeQuestionNote(result: Guidance, questions: readonly ConsultationQuestion[]) {
  return [
    'IYUM · 센터 방문 전 상담 질문', `내 상황: ${result.summary}`,
    ...result.facts.map(fact => `${fact.label}: ${fact.text}`),
    '', `먼저 확인할 곳: ${result.action.institution}`, `문의할 내용: ${result.action.ask}`,
    '', ...questions.flatMap((question, i) => [`${i + 1}. ${question.question}`, `   확인할 것: ${question.reason}`]),
  ].join('\n');
}
