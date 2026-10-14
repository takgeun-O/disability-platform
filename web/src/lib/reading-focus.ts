// Opt-in reading anchors; never alter the native Tab order of controls.
const CONTROL = 'summary,button,a[href],input,select,textarea,[contenteditable="true"],[tabindex]:not([tabindex="-1"])';
export function focusForReading(target: HTMLElement, keyboard: boolean): void {
  if (!target.matches(CONTROL)) {
    if (!target.hasAttribute('tabindex')) target.tabIndex = -1;
    target.dataset.readingFocus = keyboard ? 'keyboard' : 'pointer';
  }
  target.focus({preventScroll: true});
}

// Reserve a short reading area below the heading, not the entire long section.
// If it is already visible, no scroll is issued (including smooth-scroll CSS).
export function readingScrollDelta(top: number, height: number, viewportHeight: number): number {
  const margin = 24;
  const readingHeight = Math.min(Math.max(height, 180), viewportHeight - margin * 2);
  if (top < margin) return top - margin;
  return Math.max(0, top + readingHeight - (viewportHeight - margin));
}

export function revealForReading(target: HTMLElement): void {
  const rect = target.getBoundingClientRect();
  const delta = readingScrollDelta(rect.top, rect.height, window.innerHeight);
  if (Math.abs(delta) < 1) return;
  window.scrollTo({top: window.scrollY + delta,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'});
}
