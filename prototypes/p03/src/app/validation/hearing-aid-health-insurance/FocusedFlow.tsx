'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import GuidedCheck from './GuidedCheck';
import DetailNavigation, { focusContent } from './DetailNavigation';
import { FlowReturnContext, ReturnToTask, type FlowView } from './flow-context';
import styles from './page.module.css';

const headings: Record<FlowView, string> = { summary: 'page-title', guided: 'guided-question-start', result: 'guided-result', registry: 'registry-title', reference: 'full-criteria' };
const returnLabels: Record<FlowView, string> = { summary: '30초 요약으로 돌아가기', guided: '질문으로 돌아가기', result: '내 확인 결과로 돌아가기', registry: '등록제품 확인으로 돌아가기', reference: '전체 기준으로 돌아가기' };

export default function FocusedFlow({ summary, reference }: { summary: ReactNode; reference: ReactNode }) {
  const [view, setView] = useState<FlowView>('summary');
  const [origin, setOrigin] = useState<FlowView>('summary');
  const [registryFromReference, setRegistryFromReference] = useState(false);
  const [hasResult, setHasResult] = useState(false);
  const [epoch, setEpoch] = useState(0);
  const [entry, setEntry] = useState({ count: 0, target: 'page-title' });
  const navigate = useCallback((next: FlowView, target?: string) => {
    if (next === 'registry' && view !== 'registry' && !(view === 'reference' && origin === 'registry')) setRegistryFromReference(view === 'reference');
    if (next === 'reference' && view !== 'reference' && !(view === 'registry' && registryFromReference)) setOrigin(view === 'guided' && hasResult ? 'result' : view);
    setView(next);
    if (next !== 'reference' && window.location.hash) window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
    setEntry(previous => ({ count: previous.count + 1, target: target ?? headings[next] }));
  }, [view, hasResult, origin, registryFromReference]);
  useEffect(() => { if (entry.count) focusContent(entry.target); }, [entry]);
  function restart() { setEpoch(n => n + 1); setHasResult(false); setOrigin('summary'); setRegistryFromReference(false); navigate('guided'); }
  const returnView = view === 'reference' ? origin : view === 'registry' && registryFromReference ? 'reference' : hasResult ? 'result' : 'summary';
  return <FlowReturnContext.Provider value={{ label: returnLabels[returnView], back: () => navigate(returnView, returnView === 'reference' ? 'before-buying' : undefined) }}>
    <div className={styles.flowShell} data-view={view}>
      <DetailNavigation navigate={navigate} hasResult={hasResult} />
      <div hidden={view !== 'summary'}>{summary}</div>
      <div className={styles.flowTask}>
        <GuidedCheck key={epoch} view={view} onResult={setHasResult} onRestart={restart} />
      </div>
      <div hidden={view !== 'reference'} className={styles.referenceView}>
        {origin !== 'summary' && <ReturnToTask />}
        {origin === 'summary' && <a className={styles.bypass} href="#guided-check">내 상황 확인하기</a>}
        {reference}
      </div>
      <noscript><p>화면 전환에는 JavaScript가 필요합니다.</p></noscript>
    </div>
  </FlowReturnContext.Provider>;
}
