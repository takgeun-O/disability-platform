'use client';

import { useEffect, useRef } from 'react';
import { overviewStages } from './guided-content';
import { updateOverviewOffset } from './DetailNavigation';
import styles from './page.module.css';

const paths = {
  office: 'M3 21h18M5 21V8l7-5 7 5v13M9 21v-6h6v6M9 10h.01M15 10h.01',
  hospital: 'M4 21V5h16v16M2 21h20M10 21v-5h4v5M12 7v6M9 10h6',
  hearing: 'M7 9a5 5 0 0 1 10 0c0 4-4 4-4 8a3 3 0 0 1-6 0M10 9a2 2 0 0 1 4 0c0 2-3 2-3 4',
  document: 'M14 2H5v20h14V7l-5-5ZM14 2v6h5M8 13l2 2 5-5M8 18h8',
  care: 'M4 7h16M4 17h16M8 4v6M16 14v6',
};

export default function VisitOverview({ phase, label, text }: { phase: number | null; label: string; text: string }) {
  const overview = useRef<HTMLElement>(null);
  useEffect(() => {
    const element = overview.current;
    if (!element) return;
    // Keep focused content below the diagram even after text or viewport resizing.
    const update = () => updateOverviewOffset(element);
    const observer = new ResizeObserver(update);
    observer.observe(element); window.addEventListener('resize', update); update();
    return () => { observer.disconnect(); window.removeEventListener('resize', update); };
  }, []);
  return <section ref={overview} data-process-overview aria-label="보청기 지원 전체 과정" className={styles.visitOverview}>
    <h3>보청기 지원 전체 과정</h3>
    <ol className={styles.overviewSteps}>
      {overviewStages.map((stage, index) => <li key={stage.label} aria-current={phase === index ? 'step' : undefined} aria-label={`${index + 1}단계 ${stage.label}${phase === index ? ' · 현재 표시' : ''}`}>
        <span className={styles.overviewIcon} aria-hidden="true">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d={paths[stage.icon]} /></svg>
        </span>
        <span className={styles.overviewName} aria-hidden="true">{stage.words.map(word => <span key={word}>{word}</span>)}</span>
        {index < overviewStages.length - 1 && <span className={styles.overviewArrow} aria-hidden="true">→</span>}
      </li>)}
    </ol>
    <p className={styles.overviewCurrent}><span>{label}</span><strong>{text}</strong></p>
  </section>;
}
