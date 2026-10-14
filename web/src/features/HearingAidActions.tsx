'use client';

import { useState, type ReactNode } from 'react';
import { HEARING_AID_ACTION_GUIDANCE } from '@/data/hearingAidActionGuidance';
import { buildInquiryMemo, type InquiryInfo } from '@/lib/hearing-aid-notes';

function ActionIcon({ kind }: { kind: 'office' | 'hospital' | 'store' | 'question' | 'tasks' | 'next' }) {
  const paths = {
    hospital: 'M4 21V5h16v16M2 21h20M10 21v-5h4v5M12 7v6m-3-3h6',
    store: 'M3 10h18l-2-6H5l-2 6Zm1 0v11h16V10M9 21v-7h6v7M2 21h20',
    office: 'M3 21h18M5 21V8l7-4 7 4v13M9 21v-5h6v5M8 10h1m6 0h1m-8 3h1m6 0h1',
    question: 'M21 11a8 8 0 0 1-8 8H7l-4 3v-6a8 8 0 0 1-1-5 9 9 0 0 1 19 0ZM9 8a3 3 0 0 1 6 0c0 2-3 2-3 4m0 3v.1',
    tasks: 'M5 4h15v18H5ZM9 4V2h7v2M8 11l2 2 4-4m-5 8h7',
    next: 'M4 6h7a5 5 0 0 1 5 5v7M11 14l5 5 5-5',
  };
  return <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[kind]} /></svg>;
}

// The parent keys this component by all answers, not merely the result code.
// It stays mounted while reading the reference: checks are memory-only personal notes.
export default function HearingAidActions({ code, info, medicalAid, onClaim, renderCopy, children }: {
  code: string; info: InquiryInfo & { onlineLink?: boolean }; medicalAid: boolean;
  onClaim: () => void; renderCopy: (text: string) => ReactNode; children?: ReactNode;
}) {
  const [checked, setChecked] = useState<ReadonlySet<string>>(() => new Set());
  const guidance = HEARING_AID_ACTION_GUIDANCE[code];
  if (!guidance) return null;
  function notes(kind: 'ask' | 'tasks', title: string) {
    return <fieldset className="p03-note-checklist"><legend>{title}</legend><div>{guidance.checklist[kind].map((text, index) => {
      const id = `${kind}-${index}`;
      return <label key={id}><input type="checkbox" checked={checked.has(id)} onChange={event => {
        const selected = event.currentTarget.checked;
        setChecked(previous => { const next = new Set(previous); if (selected) next.add(id); else next.delete(id); return next; });
      }} /><span>{text}</span></label>;
    })}</div></fieldset>;
  }
  return <section className="p03-actions" aria-labelledby="today-tasks">
    <h3 id="today-tasks" tabIndex={-1}>지금 할 일, 이 순서로 확인하세요</h3>
    <ol className="p03-action-sequence">
      <li className="p03-action-card p03-action-place"><div className="p03-action-label"><span>01</span><h4>먼저 확인할 곳</h4></div><div className="p03-place-heading"><ActionIcon kind={info.institution.includes("이비인후과") ? "hospital" : ["R08", "R18"].includes(code) ? "store" : "office"} /><p>{info.institution}</p></div>
        {info.onlineLink ? <div><a href="https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do" target="_blank" rel="noopener noreferrer">공단에 글로 문의하기 · 로그인 필요 ↗ (새 탭)</a><p className="p03-small">개인별 이력·자격 문의는 공단에서 확인해 주세요.</p></div> : medicalAid ? <div><a href="https://129.go.kr/counsel/counsel03.do" target="_blank" rel="noopener noreferrer">129 채팅상담 이용 안내 ↗ (새 탭)</a><p className="p03-small">일반 제도 상담 경로입니다. 이번 신청·결정 상태는 주민센터 담당자에게 확인해 주세요.</p></div> : null}
      </li>
      <li className="p03-action-card p03-action-question"><div className="p03-action-label"><span>02</span><h4>이곳에서 무엇을 물어볼까요?</h4><ActionIcon kind="question" /></div><p className="p03-small">{info.institution}에 이렇게 물어보세요.</p><blockquote>{info.contactQuestion}</blockquote>
        {notes('ask', '문의 후, 이것을 알게 됐나요?')}<p className="p03-small">답을 확인한 항목을 직접 체크해 보세요.</p>
        {renderCopy(buildInquiryMemo(info, guidance, checked))}<p className="p03-small">현재 상황·질문·체크 항목·다음 단계를 메모 양식으로 복사해요.</p>
        {children}
      </li>
      <li className="p03-action-card p03-action-prepare"><div className="p03-action-label"><span>03</span><h4>할 일과 준비할 것</h4><ActionIcon kind="tasks" /></div><p className="p03-action-main">{info.nextAction}</p>
        {notes('tasks', '확인하고 준비한 만큼 체크하세요')}
        {info.nextActionNote && <p className="p03-action-condition">{info.nextActionNote}</p>}{info.secondary && <p className="p03-action-condition">{info.secondary}</p>}{guidance.caution && <p className="p03-action-condition">{guidance.caution}</p>}
        <p className="p03-small">체크는 개인 확인 메모이며 자격 판정이나 절차 완료에 영향을 주지 않아요. 답변을 바꾸거나 새로고침하면 초기화돼요.</p>
      </li>
      <li className="p03-action-card p03-action-after"><div className="p03-action-label"><span>04</span><h4>이번 단계 다음에는</h4><ActionIcon kind="next" /></div><p className="p03-action-main">{guidance.afterTitle}</p><p>{guidance.after}</p>
        {code === 'R08' && <button className="iyum-text-link" type="button" id="p03-claim-link" onClick={onClaim}>판매업소 위임·직접 청구 방법 보기 →</button>}
      </li>
    </ol>
  </section>;
}
