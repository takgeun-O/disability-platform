const DOCUMENTS = [
  { name: "보조기기급여비 지급청구서", from: "공단 서식을 받아 작성해요." },
  { name: "보조기기 처방전", from: "이비인후과에서 받아요." },
  { name: "보조기기 검수확인서", from: "구입 후 검수한 이비인후과에서 받아요." },
  {
    name: "세금계산서 또는 카드·현금카드 전표",
    from: "구입한 판매업소에서 받아요. 카드·현금카드 전표는 거래명세서도 함께 준비해요.",
  },
  { name: "보험급여용 보청기 구매 표준계약서", from: "구입한 판매업소에서 받아요." },
  {
    name: "바코드를 확인할 수 있는 보청기 사진",
    from: "제품 바코드가 보이도록 준비해요. 판매업소에 촬영·확인 도움을 요청할 수 있어요.",
  },
];

export default function DirectClaimGuide({ id }: { id: string }) {
  return (
    <details id={id} className="p03-direct-claim">
      <summary>
        직접 청구 방법과 준비 서류
        <span className="p03-direct-claim-scope">건강보험의 첫 청구 기준</span>
      </summary>
      <div className="p03-direct-claim-content">
        <section aria-labelledby={`${id}-submit`}>
          <p id={`${id}-submit`} className="p03-direct-claim-title">어디에 제출하나요?</p>
          <p>
            병원에서 검수확인을 받은 뒤, 아래 서류를 모아 국민건강보험공단 지사에
            방문하거나 우편으로 제출해요.
          </p>
          <a href="https://www.nhis.or.kr/nhis/about/retrieveBranchList.do" target="_blank" rel="noopener noreferrer">
            가까운 공단 지사 찾기 ↗ (새 탭)
          </a>
        </section>
        <section aria-labelledby={`${id}-documents`}>
          <p id={`${id}-documents`} className="p03-direct-claim-title">무엇을 준비하나요?</p>
          <dl className="p03-direct-claim-documents">
            {DOCUMENTS.map(({ name, from }) => (
              <div key={name}>
                <dt>{name}</dt>
                <dd>{from}</dd>
              </div>
            ))}
          </dl>
          <a href="https://www.nhis.or.kr/static/html/wbma/c/wbmac0206_n1.hwp">
            지급청구서 받기 · HWP
          </a>
        </section>
        <section aria-labelledby={`${id}-question`}>
          <p id={`${id}-question`} className="p03-direct-claim-title">센터에서 이렇게 요청하세요</p>
          <blockquote>
            “공단에 직접 청구하려고 해요. 판매업소에서 받아야 할 계약서와 구입 증빙,
            바코드 사진을 준비해 주세요. 병원에서 추가로 받아야 할 자료도 알려주세요.”
          </blockquote>
        </section>
        <p className="p03-direct-claim-source">
          출처: 국민건강보험공단 · 확인일: 2026-09-28
          <a href="https://www.nhis.or.kr/static/html/wbma/c/wbmac0206_2.html" target="_blank" rel="noopener noreferrer">
            보청기 제출서류 공식 안내 ↗ (새 탭)
          </a>
          <a href="https://www.nhis.or.kr/lm/lmxsrv/main/moviePopup.do?seqMovie=72" target="_blank" rel="noopener noreferrer">
            방문·우편 접수 공식 안내 ↗ (새 탭)
          </a>
        </p>
      </div>
    </details>
  );
}
