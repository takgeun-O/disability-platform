'use client';

import { useId, useState } from 'react';
import styles from './page.module.css';

export default function QuestionTakeaway({ text, disabled = false, successMessage = '내 상황과 질문을 복사했어요. 휴대폰 메모 등에 붙여 넣어 보관하세요.' }: { text: string; disabled?: boolean; successMessage?: string }) {
  const id = useId();
  const [message, setMessage] = useState('');
  const [fallback, setFallback] = useState(false);
  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setFallback(false);
      setMessage(successMessage);
    } catch {
      setFallback(true);
      setMessage('자동 복사가 되지 않았어요. 아래 내용을 선택해 복사해 주세요.');
    }
  }
  return <div className={styles.questionTakeaway}>
    <button className={styles.copyButton} type="button" onClick={copy} disabled={disabled}>질문 복사하기</button>
    <p role="status" aria-live="polite">{message}</p>
    {fallback && <div><label htmlFor={id}>복사할 질문</label><textarea id={id} readOnly value={text} rows={7} onFocus={event => event.target.select()} /></div>}
  </div>;
}
