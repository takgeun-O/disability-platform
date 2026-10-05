// Static maximum examples, not a calculator of an individual's entitlement.
// Notice 2026-56, annexes 4/5; NHIS Rule annex 7(1)(d)(1), (3).
export const benefitBasis = {
  total: 1_310_000,
  initialManagement: 200_000,
  annualFollowup: 50_000,
  followupCount: 4,
} as const;
export const followupBasis = benefitBasis.annualFollowup * benefitBasis.followupCount;
export const initialBasis = benefitBasis.total - followupBasis;
export const productBasis = initialBasis - benefitBasis.initialManagement;

function maximumExample(percent: 90 | 100, count: 1 | 2) {
  const initial = initialBasis * percent / 100 * count;
  const annual = benefitBasis.annualFollowup * percent / 100 * count;
  const followup = annual * benefitBasis.followupCount;
  return { initial, annual, followup, total: initial + followup };
}

export const benefitExamples = [
  { id: 'general', label: '건강보험 일반 대상', percent: 90, one: maximumExample(90, 1), two: maximumExample(90, 2) },
  { id: 'reduced', label: '차상위 본인부담경감 대상자', percent: 100, one: maximumExample(100, 1), two: maximumExample(100, 2) },
] as const;

export function formatBenefitWon(amount: number) {
  const man = Math.floor(amount / 10_000);
  const cheon = (amount % 10_000) / 1_000;
  return `${[man ? `${man}만` : '', cheon ? `${cheon}천` : ''].filter(Boolean).join(' ')} 원`;
}
