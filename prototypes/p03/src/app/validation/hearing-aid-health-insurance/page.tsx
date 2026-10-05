import type { Metadata } from 'next';
import { reviewedAt } from './content';
import styles from './page.module.css';
import FocusedFlow from './FocusedFlow';
import ReferenceGuide from './ReferenceGuide';
import { benefitBasis, formatBenefitWon } from './benefit-content';

export const metadata: Metadata = {
  title: '보청기 건강보험 급여 | 대상·지원금액·신청 순서 | IYUM',
  description: '보청기 건강보험 급여의 대상 가능성, 구입 전 확인사항, 지원금액과 진행 절차를 공식 근거와 함께 살펴보세요.',
  robots: { index: false, follow: false },
};

export default function HearingAidValidationPage() {
  return <div className={styles.page}>
    <a className={styles.skip} href="#content">본문 바로가기</a>
    <header className={styles.header}><span className={styles.brand}>IYUM</span><span>생활에 필요한 정보를, 이해하기 쉽게</span></header>
    <main id="content" tabIndex={-1} className={styles.main}>
      <FocusedFlow summary={<section id="summary" aria-labelledby="page-title" className={styles.hero} tabIndex={-1}>
        <p className={styles.eyebrow}>건강보험 · 보청기</p>
        <p className={styles.kicker}>보청기 건강보험 급여</p>
        <h1 id="page-title">보청기센터에 가기 전,<br />지원 과정부터 알아보세요</h1>
        <p className={styles.intro}>청각장애 검사 중이거나 등록 결과를 기다리고 있어도 괜찮아요. 전체 과정에서 내 위치를 알아보고, 센터에서 물어볼 질문을 준비하세요.</p>
        <p className={styles.review}>등록·급여 기준 확인 <time dateTime={reviewedAt}>{reviewedAt}</time></p>
        <div className={styles.actions}><a href="#guided-check">내 상황 확인하기</a><a href="#full-criteria">전체 기준 보기</a></div>
        <a className={styles.quietLink} href="#one-or-two">아동·청소년의 양쪽 지원 알아보기</a>
        <div className={styles.summary}><h2>30초 요약</h2><ul><li>등록 확인 → 처방 → 구입·착용 → 검수·청구 → 이후 관리로 이어져요.</li><li>센터에서는 제품 상담과 신청 지원 범위를 물어보세요.</li><li>한쪽 지원 기준액 {formatBenefitWon(benefitBasis.total)}은 제품·관리 비용을 나누어 지원하는 구조예요.</li></ul></div>
        <p className={styles.scope}>최종 급여 자격을 판정하는 페이지가 아닙니다. 개인별 적용은 공단에서 확인하세요.</p>
      </section>} reference={<ReferenceGuide />} />
    </main>
    <footer className={styles.footer}><strong>IYUM</strong><p>공식 기준과 쉬운 설명을 함께 확인하세요.</p></footer>
  </div>;
}
