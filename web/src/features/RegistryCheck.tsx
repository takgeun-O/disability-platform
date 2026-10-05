"use client";

import type { RefObject } from "react"

export interface Product {
  id: string
  model: string
  company: string
  price: number
  group: string
}

interface RegistryCheckProps {
  idPrefix: string
  productDetailsRef?: RefObject<HTMLDetailsElement | null>
  metadata: {
    sourceUrl: string
    notice: string
    effectiveAt: string
    checkedAt: string
    products: Product[]
  }
  query: string
  results: Product[]
  page: number
  selectedProduct: Product | null
  searched: boolean
  isDataStale: boolean
  modelQuestion: string | null
  onQueryChange: (value: string) => void
  onSearch: () => void
  onSelect: (product: Product) => void
  onMore: () => void
}

export default function RegistryCheck({
  idPrefix,
  productDetailsRef,
  metadata,
  query,
  results,
  page,
  selectedProduct,
  searched,
  isDataStale,
  modelQuestion,
  onQueryChange,
  onSearch,
  onSelect,
  onMore,
}: RegistryCheckProps) {
  return (
    <div className="p03-registry">
      <p className="p03-registry-intro">
        구입할 센터와 추천받은 제품은 각각 등록 여부를 확인해야 해요.
        센터명·주소와 정확한 제품 모델명을 준비하세요.
      </p>
      <details id={`${idPrefix}-center`} className="p03-registry-disclosure">
        <summary>구입할 센터의 등록 여부 확인하기</summary>
        <div className="p03-registry-content">
          <p>공단 공식 조회 화면에서 구입할 센터를 찾아보세요.</p>
          <a
            className="p03-registry-lookup"
            href="https://www.nhis.or.kr/nhis/policy/retrieveAssistingDevicesRegStoreList.do"
            target="_blank"
            rel="noopener noreferrer"
          >
            공단 등록업소 조회하기 ↗ (새 탭)
          </a>
          <p className="p03-registry-note">로그인 없이 조회할 수 있어요.</p>
          <ol className="p03-registry-lookup-steps">
            <li>취급품목에서 <strong>보청기</strong>를 선택하세요.</li>
            <li>지역이나 정확한 센터명(업소명)을 입력해 검색하세요.</li>
            <li>결과의 <strong>주소가 방문할 센터와 같은지</strong> 확인하세요.</li>
          </ol>
          <p className="p03-registry-note">
            등록 상태는 바뀔 수 있으니 구입 전에 다시 확인하세요.
          </p>
          <details
            id={`${idPrefix}-center-inquiry`}
            className="p03-registry-inquiry"
          >
            <summary>검색으로 확인되지 않나요?</summary>
            <div>
              <p>
                검색 결과가 없다는 것만으로 미등록 업소라고 판단할 수는 없어요.
                센터에 정확한 업소명을 확인하고, 그래도 찾기 어렵거나 주소가
                다르면 공단에 문의하세요.
              </p>
              <blockquote>
                “○○센터, 주소 ○○가 보청기 건강보험 급여 등록 판매업소인지 확인해
                주세요.”
              </blockquote>
              <a
                href="https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do"
                target="_blank"
                rel="noopener noreferrer"
              >
                공단에 등록 여부 문의하기 (로그인 · 새 탭)
              </a>
              <p className="p03-registry-note">
                온라인 개인 상담으로 문의하는 방법이에요. 즉시 조회되는 기능은
                아니에요.
              </p>
            </div>
          </details>
        </div>
      </details>

      <details
        id={`${idPrefix}-product`}
        ref={productDetailsRef}
        className="p03-registry-disclosure"
      >
        <summary>
          추천받은 제품 검색하기
          {selectedProduct && (
            <span className="p03-registry-selection">
              선택한 제품: {selectedProduct.model}
            </span>
          )}
        </summary>
        <div className="p03-registry-content">
          <form
            className="p03-registry-search"
            onSubmit={(event) => {
              event.preventDefault()
              onSearch()
            }}
          >
            <label htmlFor={`${idPrefix}-query`}>
              모델명 · 제품코드 · 업체명
            </label>
            <div>
              <input
                id={`${idPrefix}-query`}
                type="search"
                value={query}
                onChange={(event) => onQueryChange(event.target.value)}
                placeholder="추천받은 모델명을 입력하세요"
                aria-describedby={`${idPrefix}-search-note`}
              />
              <button type="submit">검색</button>
            </div>
          </form>
          <p id={`${idPrefix}-search-note`} className="p03-registry-note">
            보청기 건강보험 급여 고시 제품 목록({metadata.products.length}
            개)에서 검색해요.
          </p>
          <div className="p03-registry-caution">
            <p>
              제품 등록 확인은 개인의 급여 자격 확인과 달라요. 판매업소 등록도
              별도로 확인해야 해요.
            </p>
            {isDataStale && (
              <p className="p03-registry-stale">
                확인일 기준 7일이 지났어요. 구입 전 최신 등록·가격을 다시
                확인하세요.
              </p>
            )}
          </div>
          <p role="status" className="p03-registry-search-status">
            {searched &&
              (results.length
                ? `${results.length}개 결과 · 정확한 모델을 선택하세요`
                : "이 목록에서 일치하는 제품을 찾지 못했어요.")}
          </p>
          {searched && !results.length && (
            <p className="p03-registry-note">
              모델명과 제품코드를 다시 확인해 주세요. 검색 결과가 없다는
              것만으로 미등록 제품이라고 판단할 수는 없어요.
            </p>
          )}
          {results.length > 0 && (
            <>
              <ul className="p03-registry-results">
                {results.slice(0, (page + 1) * 6).map((product) => (
                  <li key={product.id}>
                    <button
                      type="button"
                      aria-pressed={selectedProduct?.id === product.id}
                      onClick={() => onSelect(product)}
                    >
                      <strong>{product.model}</strong>
                      <span>{product.company}</span>
                      <span>{product.id}</span>
                    </button>
                  </li>
                ))}
              </ul>
              {(page + 1) * 6 < results.length && (
                <button
                  type="button"
                  className="p03-registry-more"
                  onClick={onMore}
                >
                  더 보기 ({results.length - (page + 1) * 6}개 남음)
                </button>
              )}
            </>
          )}
          {selectedProduct && (
            <section
              className="p03-registry-selected"
              aria-labelledby={`${idPrefix}-selected-heading`}
            >
              <p className="p03-registry-note">선택한 제품</p>
              <h4 id={`${idPrefix}-selected-heading`}>
                {selectedProduct.model}
              </h4>
              <p>{selectedProduct.company}</p>
              <p className="p03-registry-note">
                코드: {selectedProduct.id} · 그룹: {selectedProduct.group}
              </p>
              <p className="p03-registry-price">
                <span>고시가격</span>
                <strong>
                  {selectedProduct.price.toLocaleString("ko-KR")}원
                </strong>
              </p>
              <p className="p03-registry-note">
                내가 받는 지원금이나 실제 판매가격과는 다를 수 있어요.
              </p>
              {modelQuestion && (
                <div className="p03-registry-question">
                  <p>내 상담 질문에 반영돼요 (건강보험 + 처음 신청 결과)</p>
                  <p>“{modelQuestion}”</p>
                </div>
              )}
            </section>
          )}
          <div className="p03-registry-source">
            <p>
              고시: {metadata.notice} · 시행 {metadata.effectiveAt} · 목록 확인{" "}
              {metadata.checkedAt}
            </p>
            <a
              href={metadata.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              공식 제품 목록에서 확인하기 ↗ (새 탭)
            </a>
          </div>
        </div>
      </details>
    </div>
  )
}
