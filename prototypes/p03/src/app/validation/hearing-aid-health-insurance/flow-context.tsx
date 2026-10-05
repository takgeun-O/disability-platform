'use client';

import { createContext, useContext } from 'react';
import styles from './page.module.css';
export type FlowView = 'summary' | 'guided' | 'result' | 'registry' | 'reference';
export const FlowReturnContext = createContext({ label: '30초 요약으로 돌아가기', back: () => {} });
export function ReturnToTask() {
  const { label, back } = useContext(FlowReturnContext);
  return <div className={styles.returnNavigation}><button type="button" onClick={back}>← {label}</button></div>;
}
