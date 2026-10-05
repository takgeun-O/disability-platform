import type { ReactNode } from 'react';
import { sections, sources, type SectionId } from './content';
import styles from './page.module.css';

export function Section({ id, title, children }: { id: SectionId; title: string; children: ReactNode }) {
  const number = sections.findIndex(([key]) => key === id) + 1;
  return <section id={id} aria-labelledby={`${id}-title`} className={styles.section} tabIndex={-1}>
    <p className={styles.eyebrow}>{String(number).padStart(2, '0')} / {sections.length}</p>
    <h2 id={`${id}-title`} tabIndex={-1}>{title}</h2>{children}
  </section>;
}

export function SourceLink({ sourceKey }: { sourceKey: (typeof sources)[number]['key'] }) {
  const source = sources.find((item) => item.key === sourceKey)!;
  return <a href={source.url} target="_blank" rel="noopener noreferrer">{source.title} (새 탭)</a>;
}

export function Disclosure({ id, question, children }: { id?: string; question: string; children: ReactNode }) {
  return <details id={id} className={styles.disclosure}><summary>{question}</summary><div>{children}</div></details>;
}
