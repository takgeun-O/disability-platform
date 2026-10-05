// Registration and core benefit rules rechecked; product catalog has its own date.
export const reviewedAt = '2026-09-24';
export const referenceReviewedAt = reviewedAt;
// Public NHIS menu and its login route rechecked 2026-09-24; no personal inquiry sent.
export const nhisPersonalConsultUrl = 'https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do';
export const sections = [
  ['registration-help', '청각장애 등록'], ['eligibility', '지원 대상'],
  ['one-or-two', '아동·청소년 양쪽 지원'],
  ['benefit', '지원 금액'], ['steps', '5단계 진행 절차'],
  ['before-buying', '센터 상담 준비'], ['sources', '공식 출처'],
] as const;
export type SectionId = (typeof sections)[number][0];
export const claimRuleUrl = 'https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0026&lsiSeq=288087&urlMode=lsScJoRltInfoR';
export const sources = [
  { key: 'registration', agency: '보건복지부', title: '장애인등록·장애정도 심사 안내', url: 'https://www.mohw.go.kr/menu.es?mid=a10710010900', note: '주민센터 신청, 병원 진단, 국민연금공단 심사와 등록 결과 안내. 2026-09-24 재확인.' },
  { key: 'notice', agency: '보건복지부 · 국민건강보험공단', title: '장애인보조기기 보험급여 기준 등 세부사항', url: 'https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=87&SEQ_HISTORY=613408', note: '제2026-56호 · 2026-03-25 시행. 별표 2의 대상 기준, 별표 4의 관리비, 별표 5의 기준액·내구연한을 2026-09-24 재확인.' },
  { key: 'rule', agency: '국가법령정보센터', title: '국민건강보험법 시행규칙 제26조', url: claimRuleUrl, note: '2026-08-11 시행. 직접·위임 청구와 제출 서류의 근거. 2026-09-24 재확인.' },
  { key: 'products', agency: '보건복지부 · 국민건강보험공단', title: '보청기 급여제품 및 결정가격 고시', url: 'https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=1619&SEQ_HISTORY=', note: '제품 목록은 2026-04-16 시행 고시를 2026-09-23 확인한 자료예요. 구입 전 최신 모델 등록·가격을 다시 확인하세요.' },
  { key: 'easy', agency: '법제처 찾기쉬운 생활법령정보', title: '장애인 보조기기 지원 안내', url: 'https://easylaw.go.kr/CSP/CnpClsMain.laf?ccfNo=3&cciNo=4&cnpClsNo=1&csmSeq=1063&popMenu=ov', note: '건강보험의 90% 지원과 본인부담 경감 대상의 적용 기준을 설명해요.' },
  { key: 'consult', agency: '국민건강보험공단', title: '온라인 개인 상담 (로그인 필요)', url: nhisPersonalConsultUrl, note: '국민소통·참여 → 온라인 상담문의 → 개인 상담. 메뉴와 로그인 연결을 2026-09-24 확인했어요.' },
] as const;
