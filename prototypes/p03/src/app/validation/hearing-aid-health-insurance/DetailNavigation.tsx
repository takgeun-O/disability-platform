'use client';

import { useEffect, useRef } from 'react';
import type { FlowView } from './flow-context';

const referenceIds = new Set(['full-criteria', 'eligibility', 'registration-help', 'scope', 'one-or-two', 'benefit', 'steps', 'prescription-help', 'before-buying', 'previous-benefit', 'documents', 'claim-method', 'sources']);
// Keep saved links useful after merging redundant reference sections.
const referenceAliases = new Map([
  ['prescription', 'prescription-help'], ['document-details', 'documents'],
  ['user-preparation', 'before-buying'], ['faq', 'before-buying'], ['experiences', 'full-criteria'],
]);
export function updateOverviewOffset(target: Element) {
  const guided = target.closest<HTMLElement>('[data-guided-flow]');
  const overview = guided?.querySelector<HTMLElement>('[data-process-overview]');
  if (!guided || !overview) return;
  const height = overview.getBoundingClientRect().height;
  if (!height) return;
  guided.style.setProperty('--overview-offset', `${getComputedStyle(overview).position === 'sticky' ? height + 16 : 16}px`);
}
export function focusContent(id: string) {
  const target = document.getElementById(id);
  if (!target) return;
  if (target instanceof HTMLDetailsElement) target.open = true;
  let ancestor = target.parentElement;
  while (ancestor) { if (ancestor instanceof HTMLDetailsElement) ancestor.open = true; ancestor = ancestor.parentElement; }
  const heading = target instanceof HTMLDetailsElement ? target.querySelector<HTMLElement>('summary') ?? target : target.matches('h1,h2,h3,h4') ? target : target.querySelector<HTMLElement>('h1,h2,h3,h4') ?? target;
  updateOverviewOffset(target);
  heading.tabIndex = -1; heading.focus({ preventScroll: true });
  target.scrollIntoView({ block: 'start', behavior: 'instant' });
}

// Only public Reference locations enter the URL. Answers and view state never do.
export default function DetailNavigation({ navigate, hasResult }: { navigate: (view: FlowView, target?: string) => void; hasResult: boolean }) {
  const initialized = useRef(false);
  useEffect(() => {
    function route(id: string) {
      id = referenceAliases.get(id) ?? id;
      if (referenceIds.has(id)) { navigate('reference', id); return true; }
      const views: Record<string, FlowView> = { summary: 'summary', 'guided-check': 'guided', 'guided-result': hasResult ? 'result' : 'guided', 'registry-check': 'registry', 'consultation-preparation': hasResult ? 'result' : 'guided' };
      if (views[id]) { navigate(views[id]); return true; }
      return false;
    }
    function hashChange() { const id = window.location.hash.slice(1); if (id) route(id); else navigate('summary'); }
    function click(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = event.target instanceof Element ? event.target.closest('a') : null;
      const href = anchor?.getAttribute('href');
      if (!href?.startsWith('#') || anchor?.target) return;
      const id = href.slice(1);
      if (!referenceIds.has(id) && !referenceAliases.has(id) && !['summary','guided-check','guided-result','registry-check','consultation-preparation'].includes(id)) return;
      event.preventDefault();
      // A bounded public anchor replacement, not a history entry per local view.
      if (referenceIds.has(id) || referenceAliases.has(id)) window.history.replaceState(window.history.state, '', `#${referenceAliases.get(id) ?? id}`);
      route(id);
    }
    if (!initialized.current) { initialized.current = true; if (window.location.hash) hashChange(); }
    window.addEventListener('hashchange', hashChange); document.addEventListener('click', click);
    return () => { window.removeEventListener('hashchange', hashChange); document.removeEventListener('click', click); };
  }, [navigate, hasResult]);
  return null;
}
