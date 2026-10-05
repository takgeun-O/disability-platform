'use client';

import { useId, useRef, useState } from 'react';
import { matchProducts, registryCheckedAt, registryEffectiveAt, registryProducts, registrySource, type RegistryContext, type RegistryProduct } from './registry-content';
import styles from './page.module.css';

export type ProductChange = (searched: boolean, selected?: RegistryProduct, found?: boolean) => void;

export default function ProductLookup({ registry, onProductChange }: { registry: RegistryContext; onProductChange: ProductChange }) {
  const id = useId();
  const [query, setQuery] = useState(registry.product.product?.model ?? '');
  const [candidates, setCandidates] = useState<readonly RegistryProduct[]>([]);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState('');
  const [limit, setLimit] = useState(6);
  const resultHeading = useRef<HTMLHeadingElement>(null);
  const product = registry.product.product;

  function search(event: React.SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!query.trim()) { setError('제품 모델명이나 제품코드를 입력해 주세요.'); return; }
    const matches = matchProducts(query);
    setCandidates(matches); setSearched(true); setError(''); setLimit(6);
    onProductChange(true, undefined, matches.length > 0);
  }
  function choose(item: RegistryProduct) {
    setQuery(item.model); setCandidates([]); setSearched(false); setError('');
    onProductChange(true, item, true);
    requestAnimationFrame(() => resultHeading.current?.focus({ preventScroll: true }));
  }

  return <section className={styles.productLookup} aria-labelledby={`${id}-title`}>
    <h4 id={`${id}-title`}>내 보청기 모델 검색하기</h4>
    <p id={`${id}-coverage`} className={styles.scope}>{registryEffectiveAt} 시행 공식 고시에 실린 {registryProducts.length}개 제품을 검색해요. 실시간 조회가 아닌, 확인일 기준의 목록입니다.</p>
    <form onSubmit={search}>
      <label htmlFor={`${id}-query`}>모델명 · 제품코드 · 업체명</label>
      <div className={styles.productSearchRow}>
        <input id={`${id}-query`} value={query} maxLength={120} autoComplete="off" spellCheck={false} placeholder="예: Picasso, 포낙, D22018010015" aria-describedby={`${id}-coverage ${id}-hint${error ? ` ${id}-error` : ''}`} aria-invalid={!!error} onChange={event => {
          setQuery(event.target.value); setCandidates([]); setSearched(false); setError(''); onProductChange(false);
        }} />
        <button type="submit">제품 검색</button>
      </div>
      <p id={`${id}-hint`} className={styles.scope}>이름 일부만 입력해도 돼요. 상담받은 모델과 결과의 전체 모델명을 비교하세요.</p>
      {error && <p id={`${id}-error`} role="alert">{error}</p>}
    </form>
    <div role="status" aria-live="polite" aria-atomic="true">
      {searched && <p>{candidates.length ? `${candidates.length}개 제품을 찾았어요. 정확한 모델을 선택하세요.` : '이 목록에서 일치하는 제품을 찾지 못했어요.'}</p>}
    </div>
    {searched && !candidates.length && <p>모델명과 제품코드를 다시 확인해 주세요. 검색 결과가 없다는 것만으로 미등록 제품이라고 판단할 수는 없어요.</p>}
    {candidates.length > 0 && <ul className={styles.productCandidates} aria-label="검색된 보청기 모델">
      {candidates.slice(0, limit).map(item => <li key={item.id}>
        <button type="button" onClick={() => choose(item)}>
          <strong>{item.model}</strong><span>{item.company}</span><span>제품코드 {item.id}</span><span>이 모델 확인하기 →</span>
        </button>
      </li>)}
    </ul>}
    {candidates.length > limit && <button type="button" onClick={() => setLimit(value => value + 6)}>검색 결과 더 보기 ({Math.min(limit, candidates.length)} / {candidates.length})</button>}
    {product && <div className={styles.productMatch}>
      <h4 ref={resultHeading} tabIndex={-1}>공식 고시 목록에 있는 제품이에요</h4>
      <p><strong>{product.model}</strong><br />{product.company}<br />제품코드 {product.id}</p>
      <p>고시가격 <strong>{product.price.toLocaleString('ko-KR')}원</strong><br /><span className={styles.scope}>내가 받는 지원금이나 실제 판매가격과는 다를 수 있어요.</span></p>
      {registry.product.status === 'stale' && <p>목록 확인 이후 변경됐을 수 있어요. 구입 전 최신 등록 여부를 다시 확인하세요.</p>}
      <p>제품 등록 확인은 개인의 급여 자격 확인과 달라요. 판매업소 등록도 별도로 확인해야 해요.</p>
    </div>}
    <details className={styles.disclosure}><summary>모델명을 모르겠다면</summary><div><p>보청기 상자·견적서의 모델명이나 제품코드를 살펴보세요. 찾기 어렵다면 판매업소에 이렇게 물어보세요.</p><p>“추천해 주신 보청기의 정확한 모델명과 급여 제품코드를 알려주세요.”</p></div></details>
    <p className={styles.scope}>목록 확인일: {registryCheckedAt} · <a href={registrySource} target="_blank" rel="noopener noreferrer">공식 제품 고시 원문 (새 탭)</a></p>
  </section>;
}
