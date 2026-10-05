"use client";

import { useEffect, useRef, useState } from "react";
import {
  getGuidance,
  getOverviewState,
  replaceAnswer,
  answersForPreviousQuestion,
  type Answer,
  questions,
  type Guidance,
} from "./guided-content";
import styles from "./page.module.css";
import { focusContent, updateOverviewOffset } from "./DetailNavigation";
import AfterPurchaseGuide from "./AfterPurchaseGuide";
import VisitOverview from "./VisitOverview";
import QuestionTakeaway from "./QuestionTakeaway";
import { makeQuestionNote } from "./question-note";
import ResultJourney from "./ResultJourney";
import { nhisPersonalConsultUrl, referenceReviewedAt } from "./content";
import { getQuestionPlan } from "./consultation-content";
import ProductLookup from "./ProductLookup";
import { ReturnToTask, type FlowView } from "./flow-context";
import { flushSync } from "react-dom";
import OfficialRegistryLookup from "./OfficialRegistryLookup";
import {
  isRegistryStale,
  type RegistryContext,
  type RegistryProduct,
} from "./registry-content";

// Private, transient state only. No storage, URL serialization, logging or requests.
export default function GuidedCheck({
  view,
  onResult,
  onRestart,
}: {
  view: FlowView;
  onResult: (value: boolean) => void;
  onRestart: () => void;
}) {
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState<Answer[]>([]);
  const [result, setResult] = useState<Guidance | null>(null);
  const [transition, setTransition] = useState(0);
  const [productSearch, setProductSearch] = useState<{
    searched: boolean;
    selected?: RegistryProduct;
    found?: boolean;
  }>({ searched: false });
  const [simulated, setSimulated] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const refresh = () => setNow(Date.now());
    const beforePrint = () => flushSync(refresh);
    const timer = window.setInterval(refresh, 30_000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("beforeprint", beforePrint);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("beforeprint", beforePrint);
    };
  }, []);
  const registry: RegistryContext = {
    product: {
      status: !productSearch.searched
        ? "idle"
        : productSearch.selected
          ? simulated || isRegistryStale(productSearch.selected.checkedAt, now)
            ? "stale"
            : "verified"
          : productSearch.found
            ? "ambiguous"
            : "not-found",
      product: productSearch.selected,
      simulated: !!productSearch.selected && simulated,
    },
  };
  const heading = useRef<HTMLHeadingElement>(null);
  const resultHeading = useRef<HTMLHeadingElement>(null);
  const question = questions[step];

  useEffect(() => {
    // Move focus only after an explicit interaction, never on initial hydration.
    if (transition === 0) return;
    const target = result ? resultHeading.current : heading.current;
    if (target) updateOverviewOffset(target);
    target?.focus({ preventScroll: true });
    const scrollTarget = result ? target : target?.closest('#guided-question-start');
    scrollTarget?.scrollIntoView({
      block: "start",
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
    });
  }, [transition, result]);

  function choose(value: Answer) {
    // A changed answer invalidates downstream context, including recommendations.
    const nextAnswers = replaceAnswer(answers, step, value);
    const guidance = getGuidance(nextAnswers);
    setAnswers(nextAnswers);
    setResult(guidance);
    onResult(!!guidance);
    if (!guidance && step < questions.length - 1) {
      setStep(step + 1);
    }
    setTransition(value => value + 1);
  }

  function back() {
    setAnswers(answersForPreviousQuestion(answers, step));
    setResult(null);
    setStep(Math.max(0, step - 1));
    onResult(false);
    setTransition((value) => value + 1);
  }

  function restart() {
    onRestart();
  }

  const plan = getQuestionPlan(answers, registry);
  const showingResult = (view === "guided" || view === "result") && result;
  const overview = getOverviewState(step, result);
  return (
    <>
      <div hidden={view !== "guided" && view !== "result"}>
        <section
          id="guided-check"
          aria-labelledby="guided-title"
          className={styles.guided}
          data-guided-flow
          tabIndex={-1}
        >
          <a className={styles.quietLink} href="#summary">← 30초 요약</a>
          <p className={styles.eyebrow}>빠르게 확인하기 · IYUM 안내</p>
          <h2 id="guided-title">센터 방문 전, 내 상황부터 알아볼까요?</h2>
          <p className={styles.scope}>
            아는 내용만 선택해 주세요. 필요한 질문만 확인하고, 센터에서 물어볼 질문을 정리해 드릴게요.
          </p>
          <p className={styles.scope}>보호자가 대신 확인한다면, 보청기를 사용할 분의 등록 상태와 보험 자격을 기준으로 답해 주세요.</p>
          <VisitOverview {...overview} />
          <div className={styles.questionPanel}>
            <div id="guided-question-start">
                <div className={styles.questionMeta}>
                <p className={styles.label}>
                  {showingResult ? "선택한 답변" : question.shortTitle}
                </p>
                {step > 0 && <button type="button" className={styles.textButton} onClick={back}>← 이전 질문</button>}
                </div>
                <h3 id="guided-question" ref={heading} tabIndex={-1}>
                  {question.title}
                </h3>
                <div id="guided-support"><p>{question.support}</p>{question.hint && <p className={styles.scope}>{question.hint}</p>}</div>
                {!showingResult && step > 1 && answers[1] === 'unknown' && <p className={styles.pendingNotice}>보험 자격은 <strong>미확인</strong>으로 유지하고 있어요.</p>}
                <div
                  role="group"
                  aria-labelledby="guided-question"
                  aria-describedby="guided-support"
                  className={styles.choices}
                >
                  {question.choices.map((choice) => (
                    <button
                      type="button"
                      key={choice.label}
                      aria-pressed={answers[step] === choice.value}
                      onClick={() => choose(choice.value)}
                    >
                      {choice.label}
                      {answers[step] === choice.value && (
                        <span className={styles.selected}> · 선택됨</span>
                      )}
                    </button>
                  ))}
                </div>
            </div>
            {showingResult && (
              <div className={styles.inlineResult} key={answers.join(',')}>
                <p className={styles.label}>답변으로 정리한 내 현재 상태</p>
                <h2 id="guided-result" ref={resultHeading} tabIndex={-1}>{result.summary}</h2>
                <div className={styles.nextActionPreview}>
                  <p><strong>다음 행동:</strong> {result.action.institution} · {result.action.instruction}</p>
                  <a className={styles.quietLink} href="#guided-next-action" onClick={event => {
                    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
                    event.preventDefault();
                    focusContent('guided-next-action');
                  }}>지금 할 일 보기 ↓</a>
                </div>
                <dl className={styles.resultFacts}>{result.facts.map(fact => <div key={fact.text}><dt>{fact.label}</dt><dd>{fact.text}</dd></div>)}</dl>
                <ResultJourney answers={answers} result={result} />
                <section className={styles.visitNext} aria-labelledby="guided-next-action">
                  <h3 id="guided-next-action" tabIndex={-1}>지금 할 일</h3><p><strong>{result.action.institution}</strong></p><p>{result.action.instruction}</p>
                  <blockquote className={styles.actionQuote}><strong>이렇게 물어보세요</strong><p>“{result.action.ask}”</p></blockquote>
                  {result.action.online && <p className={styles.scope}><a href={nhisPersonalConsultUrl} target="_blank" rel="noopener noreferrer">공단 온라인 개인 상담 (로그인 · 새 탭)</a><br />전화 대신 글로 문의할 수 있어요. 공단 홈페이지의 ‘국민소통·참여 → 온라인 상담문의 → 개인 상담’에서도 찾을 수 있어요.</p>}
                  {result.secondary && <p>{result.secondary}</p>}
                  <div className={styles.centerBoundary}><strong>센터 상담에서 도움받을 일</strong><p>{result.center}</p></div>
                </section>
                <section className={styles.visitQuestions} aria-labelledby="visit-questions-title">
                  <h3 id="visit-questions-title">센터에서 물어볼 질문 3개</h3>
                  <ol>{plan.primary.map(q => <li key={q.id}><p>{q.question}</p><p className={styles.questionReason}><strong>확인할 것</strong> · {q.reason}</p></li>)}</ol>
                  <QuestionTakeaway key={answers.join(',') + registry.product.status + registry.product.product?.id} text={makeQuestionNote(result, plan.primary)} />
                </section>
                <p><a href="#one-or-two">아동·청소년의 양쪽 지원 알아보기</a><br /><span className={styles.scope}>보호자를 위한 건강보험 공통 안내예요. 지금 답변의 자격이나 지원액을 확정하지 않아요.</span></p>
                {result.showHealthDetails && <AfterPurchaseGuide historyNeedsChecking={answers[3] !== 'first'} />}
                <div className={styles.visitExtras}>
                  <details className={styles.disclosure}><summary>추천받은 모델이 있다면 확인하기</summary>
                    <p className={styles.scope}>아직 모델을 몰라도 상담할 수 있어요. 추천받은 뒤 정확한 모델명으로 확인하세요.</p>
                    <ProductLookup registry={registry} onProductChange={(searched, selected, found) => {
                      setNow(Date.now()); setProductSearch({ searched, selected, found });
                    }} />
                  </details>
                  <p><a href="#full-criteria">전체 기준과 공식 출처 보기</a><br /><span className={styles.scope}>등록·급여 기준 확인: <time dateTime={referenceReviewedAt}>{referenceReviewedAt}</time></span></p>
                </div>
              </div>
            )}
            <div className={styles.guidedControls}>
              {(answers.length > 0 || step > 0 || result) && (
                <button type="button" className={styles.textButton} onClick={restart}>
                  처음부터 확인하기
                </button>
              )}
            </div>
            <p className={styles.scope}>답변은 서버에 저장·전송되지 않으며 새로고침하면 사라져요.</p>
          </div>
          <noscript>
            <p>
              질문형 안내에는 JavaScript가 필요합니다.{" "}
              <a href="#full-criteria">답변 없이 전체 기준을 확인하세요.</a>
            </p>
          </noscript>
        </section>
      </div>
      <div hidden={view !== "registry"}>
        <ReturnToTask />
        {view === "registry" && <OfficialRegistryLookup
          registry={registry}
          simulated={simulated}
          onSimulate={setSimulated}
          onProductChange={(searched, selected, found) => {
            setNow(Date.now());
            setProductSearch({ searched, selected, found });
          }}
        />}
      </div>
    </>
  );
}
