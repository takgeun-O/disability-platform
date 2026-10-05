
'use client';

import { registryCheckedAt, registryEffectiveAt, registryProducts, registrySource, registryStatusText, type RegistryContext } from './registry-content';
import ProductLookup, { type ProductChange } from './ProductLookup';
import styles from './page.module.css';

export function RegistrySummary({ registry }: { registry: RegistryContext }) {
  const result = registry.product;
  if (result.status === 'idle') return null;
  return <section className={styles.registrySummary} aria-label="상담 전 공식 등록정보 확인">
    <h3>상담 전 공식 등록정보 확인</h3>
    <p><strong>제품:</strong> {result.status === 'verified' ? '확인일 기준 공식 고시 목록에 포함됨' : registryStatusText[result.status]}{result.simulated && ' (최신성 지연 시뮬레이션)'}</p>
    {result.product && <p>{result.product.model} · {result.product.company}<br />제품코드: {result.product.id}</p>}
    <p>공식 목록: {registryEffectiveAt} 시행 · 확인일: {registryCheckedAt} · <a href={registrySource}>공식 제품 고시 원문</a></p>
    <p>공식 고시 {registryProducts.length}개 제품 기준이며 실시간 조회나 개인 급여 자격 확인이 아닙니다.</p>
  </section>;
}

export default function OfficialRegistryLookup({ registry, simulated, onSimulate, onProductChange }: {
  registry: RegistryContext; simulated: boolean; onSimulate: (value: boolean) => void;
  onProductChange: ProductChange;
}) {
  return <section id="registry-check" className={styles.registry} aria-labelledby="registry-title">
    <p className={styles.eyebrow}>구입 전 사전 확인 · 공식 목록 기준</p>
    <h2 id="registry-title" tabIndex={-1}>센터·제품 공식 등록 확인</h2>
    <p>제품과 판매업소 등록은 각각 확인해야 합니다.</p>
    <p className={styles.privacy}>검색어와 선택 결과는 전송·저장하지 않습니다. 새로고침하면 사라집니다.</p>
    <div className={styles.registryGrid}>
      <section aria-labelledby="center-lookup-title">
        <h3 id="center-lookup-title">보청기센터 등록 여부 확인</h3>
        <div className={styles.registryResult}>
          <h4>센터 등록정보 조회 준비 중</h4>
          <p id="center-coverage">현재 검증용 데모에서는 센터 등록정보 조회를 제공하지 않습니다. 공식 센터 표본이 아직 없습니다.</p>
          <p>미등록 센터이거나 공식 사이트·입력에 오류가 있다는 뜻이 아닙니다. 공식 업소명·주소 표본을 확보한 뒤 조회를 연결할 예정입니다.</p>
        </div>
        <label htmlFor="center-query">센터명 · 현재 조회 불가</label>
        <input id="center-query" disabled aria-describedby="center-coverage" />
        <p className={styles.scope}>센터에는 “건강보험 보청기 급여 등록 업소인가요?”라고 물어보세요.</p>
      </section>
      <ProductLookup registry={registry} onProductChange={onProductChange} />
    </div>
    <details className={styles.disclosure}><summary>검증용 최신성 지연 예시</summary><div>
      <label className={styles.checkLabel}><input type="checkbox" checked={simulated} onChange={event => onSimulate(event.target.checked)} /><span>선택한 제품의 최신성 지연 시뮬레이션 보기</span></label>
      <p>실제 공식 확인일은 그대로 유지합니다. 7일은 이 화면의 검증 기준이며 공단의 갱신 주기가 아닙니다.</p>
    </div></details>
    <p><a href="#before-buying">구입 전 기준 자세히 보기</a></p>
  </section>;
}
