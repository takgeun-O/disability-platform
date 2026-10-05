export const bilateralReviewedAt = '2026-09-25';
export const bilateralRuleUrl = 'https://www.law.go.kr/flDownload.do?bylClsCd=110201&flSeq=162807869&gubun=';
export const childHearingProgramUrl = 'https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=WLF00001130';

export const caregiverQuestions = [
  { institution: '병원 · 이비인후과', question: '아이의 검사 결과가 건강보험 양쪽 보청기 지원 조건을 모두 충족하나요? 양쪽 지원을 위한 처방이 가능한가요?' },
  { institution: '보청기센터 · 비용', question: '양쪽을 구입하면 각 보청기의 지원액과 제가 부담할 총금액은 얼마인가요?' },
  { institution: '보청기센터 · 관리', question: '양쪽 보청기의 초기·후기 관리에 포함되는 서비스와 별도 비용, 다음 방문 일정은 어떻게 되나요?' },
] as const;

export const caregiverQuestionNote = [
  'IYUM · 보호자의 양쪽 보청기 지원 상담 질문',
  '건강보험 공통 안내를 바탕으로 준비한 질문이며, 아이의 지원 자격이나 금액을 확정한 결과가 아니에요.',
  ...caregiverQuestions.map(({ institution, question }, index) => `\n${index + 1}. ${institution}\n${question}`),
].join('\n');
