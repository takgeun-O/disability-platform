"use client";

import { useRef, useState, type RefObject } from "react"

export type BenefitMode = "single" | "bilateral"

// Fixed examples from the existing P03 content; this does not assess eligibility.
const AMOUNTS = {
  single: {
    summary: "한쪽 보청기 기준으로, 처음 청구 기준액 111만 원과 이후 적합관리 기준액 20만 원을 합쳐 전체 기준액 131만 원이에요.",
    firstBasis: "기준액 111만 원 (제품 91만 원 + 초기 관리 20만 원)",
    firstGeneral: "최대 99만 9천 원",
    firstReduced: "최대 111만 원",
    laterBasis: "연 5만 원 × 최대 4회 (기준액 합계 20만 원)",
    laterGeneral: "연 최대 4만 5천 원 · 4회 합계 최대 18만 원",
    laterReduced: "연 최대 5만 원 · 4회 합계 최대 20만 원",
    totalBasis: "전체 기준액 131만 원",
    totalGeneral: "총 최대 117만 9천 원",
    totalReduced: "총 최대 131만 원",
  },
  bilateral: {
    summary: "양쪽 합산 예시로, 처음 청구 기준액 222만 원과 이후 적합관리 기준액 40만 원을 합쳐 전체 기준액 262만 원이에요.",
    firstBasis:
      "한쪽 기준액 111만 원 × 2 (한쪽 제품 91만 원 + 초기 관리 20만 원)",
    firstGeneral: "최대 199만 8천 원",
    firstReduced: "최대 222만 원",
    laterBasis: "한쪽 연 5만 원 × 최대 4회 × 2 (기준액 합산 40만 원)",
    laterGeneral: "연 최대 9만 원 · 4회 합계 최대 36만 원",
    laterReduced: "연 최대 10만 원 · 4회 합계 최대 40만 원",
    totalBasis: "한쪽 전체 기준액 131만 원 × 2 (합산 262만 원)",
    totalGeneral: "총 최대 235만 8천 원",
    totalReduced: "총 최대 262만 원",
  },
} as const

function BenefitAmountCards({ mode }: { mode: BenefitMode }) {
  const amounts = AMOUNTS[mode]
  const scope = mode === "single" ? "한쪽 기준" : "양쪽 합산 예시"
  const cards = [
    {
      title: "처음 청구",
      basisLabel: "지원금 계산 기준",
      basis: amounts.firstBasis,
      timing:
        "구입 후 1개월이 지난 뒤 병원 검수확인을 받고 청구해요. 제품과 초기 관리 비용은 함께 지급돼요.",
      general: amounts.firstGeneral,
      reduced: amounts.firstReduced,
    },
    {
      title: "이후 청구 · 후기 적합관리",
      basisLabel: "관리비 계산 기준",
      basis: amounts.laterBasis,
      timing:
        "구입 1년 후부터 5년까지의 관리 비용이에요. 매년 1회 이상 실제 관리를 받은 뒤, 연 1회씩 따로 청구해요.",
      general: amounts.laterGeneral,
      reduced: amounts.laterReduced,
    },
    {
      title: "전체 합계",
      basisLabel: "전체 지원금 계산 기준",
      basis: amounts.totalBasis,
      timing:
        "처음 청구와 이후 관리 비용을 합한 금액이며, 한 번에 지급되는 돈은 아니에요.",
      general: amounts.totalGeneral,
      reduced: amounts.totalReduced,
    },
  ]
  return (
    <div className="p03-benefit-cards">
      {cards.map((card) => (
        <div className="p03-benefit-card" key={card.title}>
          <div className="p03-benefit-composition">
            <h4>
              {card.title} <span>· {scope}</span>
            </h4>
            <p className="p03-benefit-basis-label">{card.basisLabel}</p>
            <p>{card.basis}</p>
            <p className="p03-benefit-timing">{card.timing}</p>
          </div>
          <div className="p03-benefit-limits">
            <p className="p03-benefit-limits-title">자격별 최대 지원액</p>
            <div>
              <p>건강보험 일반 대상 · 90%</p>
              <strong>{card.general}</strong>
            </div>
            <div>
              <p>차상위 본인부담경감 대상자 · 100%</p>
              <strong>{card.reduced}</strong>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

export function BenefitGuide({
  mode,
  onModeChange,
  idPrefix,
  collapsible = false,
}: {
  mode: BenefitMode
  onModeChange: (mode: BenefitMode) => void
  idPrefix: string
  collapsible?: boolean
}) {
  const detailsRef = useRef<HTMLDetailsElement>(null)
  const headingId = `${idPrefix}-mode-heading`
  const contentId = `${idPrefix}-content`

  function changeMode(nextMode: BenefitMode) {
    onModeChange(nextMode)
    // Keep focus on the native radio; reveal the selected example in place.
    if (nextMode === "bilateral" && detailsRef.current) {
      detailsRef.current.open = true
    }
  }

  const content = (
    <>
      <BenefitAmountCards mode={mode} />
      <p className="p03-benefit-intro">
        실제 지원액은 제품별 고시금액과 구입금액 등에 따라 달라져요. 100% 지원도
        지원 대상으로 인정되는 금액 기준이에요.
      </p>
      <ReducedCopayHelp idPrefix={idPrefix} />
      <AfterPurchaseHelp idPrefix={idPrefix} />
      <PaymentConditions idPrefix={idPrefix} />
      {mode === "bilateral" && (
        <>
          <p className="p03-benefit-action">
            이비인후과에서 아이가 양쪽 지원 조건에 해당하는지 물어보세요. 검사
            수치를 직접 해석하지 않아도 괜찮아요.
          </p>
          <BilateralExamConditions idPrefix={`${idPrefix}-bilateral`} />
          <BilateralSources idPrefix={`${idPrefix}-bilateral`} />
          <details
            id={`${idPrefix}-bilateral-consultation`}
            className="p03-benefit-questions"
          >
            <summary>보호자가 병원·센터에서 물어볼 질문 보기</summary>
            <div>
              <BilateralQuestions
                idPrefix={`${idPrefix}-bilateral`}
                headingLevel={4}
              />
            </div>
          </details>
          <BilateralOtherSupport />
        </>
      )}
    </>
  )

  return (
    <div className="p03-benefit-guide">
      <p className="p03-benefit-intro">
        건강보험 보청기 지원은 기본적으로 한쪽 기준이에요. 아동·청소년의 양쪽
        지원은 추가 조건을 확인해야 해요.
      </p>
      <fieldset className="p03-benefit-switch">
        <legend>금액 안내 보기</legend>
        {([
          ["single", "한쪽 기준"],
          ["bilateral", "아동·청소년 양쪽 지원 예시"],
        ] as const).map(([value, label]) => (
          <label key={value}>
            <input
              type="radio"
              name={`${idPrefix}-mode`}
              value={value}
              checked={mode === value}
              onChange={() => changeMode(value)}
              aria-controls={contentId}
            />
            <span>{label}</span>
          </label>
        ))}
      </fieldset>
      <div id={contentId} role="region" aria-labelledby={headingId}>
        <h4
          id={headingId}
          className="p03-benefit-mode-heading"
          aria-live="polite"
          aria-atomic="true"
        >
          {mode === "single"
            ? "한쪽 보청기 지원 금액"
            : "양쪽 보청기 지원 금액 예시"}
        </h4>
        {mode === "bilateral" && (
          <>
            <BilateralEligibility compact />
            <BilateralExampleNotice />
          </>
        )}
        <p className="p03-benefit-intro">{AMOUNTS[mode].summary}</p>
        <p className="p03-benefit-intro">
          기준액은 자격별 지원액을 계산하는 공통 한도이며, 그대로 지급되는 금액은
          아니에요. ‘적합관리’는 보청기 조절·관리 서비스예요.
        </p>
        {collapsible ? (
          <details id={idPrefix} ref={detailsRef} className="p03-benefit-details">
            <summary>지원 금액과 지급 조건 자세히 보기</summary>
            <div>{content}</div>
          </details>
        ) : content}
      </div>
    </div>
  )
}

export function BilateralEligibility({
  compact = false,
}: {
  compact?: boolean
}) {
  if (compact) {
    return (
      <p className="p03-benefit-intro">
        <strong>19세 미만의 청각장애 등록자</strong>가 추가 검사 조건을 모두
        충족하면 양쪽 지원이 가능해요.
        <strong> 나이만으로 양쪽 지원이 확정되지는 않아요.</strong>
      </p>
    )
  }
  return (
    <div style={{ marginBottom: 24 }}>
      <p
        style={{
          fontSize: 13,
          color: "#4A4845",
          lineHeight: 1.7,
          marginBottom: 8,
        }}
      >
        이 건강보험 제도의 보청기 지원은 기본적으로 한쪽 기준이에요.{" "}
        <strong>19세 미만의 청각장애 등록자</strong>는 추가 검사 조건을 모두
        충족하면 양쪽 보청기 지원이 가능해요.
      </p>
      <p
        style={{
          fontSize: 13,
          fontWeight: 700,
          color: "#1A1918",
          marginBottom: 8,
        }}
      >
        나이만으로 양쪽 지원이 확정되지는 않아요.
      </p>
      <p style={{ fontSize: 13, color: "#908D88", lineHeight: 1.6 }}>
        아래는 공통 안내예요. 개인별 지원 자격과 실제 금액은 확인이 필요해요.
      </p>
    </div>
  )
}

export function BilateralExamConditions({ idPrefix }: { idPrefix: string }) {
  return (
    <details
      id={`${idPrefix}-exams`}
      style={{
        marginTop: 12,
        marginBottom: 12,
        background: "#f3f6fb",
        border: "1px solid #dce3ed",
        borderRadius: 8,
      }}
    >
      <summary
        style={{
          padding: "12px 14px",
          fontSize: 14,
          fontWeight: 600,
          lineHeight: 1.6,
          color: "#1a1918",
          cursor: "pointer",
          userSelect: "none",
        }}
      >
        병원에서는 어떤 검사 조건을 확인하나요?
      </summary>
      <div
        style={{
          padding: "0 14px 16px 28px",
          fontSize: 14,
          color: "#4a4845",
          lineHeight: 1.8,
          overflowWrap: "anywhere",
        }}
      >
        <p style={{ marginBottom: 8 }}>
          청력장애로 등록된 청각장애인 중 건강보험 가입자·피부양자이며
          이비인후과 전문의가 일상생활에 도움이 된다고 판단하는 기본 요건에 더해
          다음 모두가 필요해요.
        </p>
        <ul
          style={{
            margin: 0,
            padding: 0,
            listStyle: "none",
            display: "flex",
            flexDirection: "column",
            gap: 6,
          }}
        >
          {[
            "19세 미만",
            "양쪽 모두 80dB 미만의 난청 — dB(데시벨)는 청력검사에서 사용하는 소리 크기 단위",
            "양쪽 말소리명료도 각각 50% 이상 — 들은 말소리를 얼마나 정확히 구별하는지 보는 검사",
            "양쪽 순음청력역치 차이 15dB 이하 — 검사 소리를 겨우 들을 수 있는 크기의 두 귀 차이",
            "양쪽 말소리명료도 차이 20% 이하",
          ].map((item, i) => (
            <li
              key={i}
              style={{
                paddingLeft: 12,
                borderLeft: "2px solid #D0CEC9",
                fontSize: 13,
                lineHeight: 1.6,
              }}
            >
              {item}
            </li>
          ))}
        </ul>
        <p style={{ marginTop: 10, fontSize: 12, color: "#908D88" }}>
          의식이 명료하지 않거나 보청기를 사용할 수 없다고 전문의가 판단하면
          한쪽·양쪽 모두 적용에서 제외될 수 있어요. (출처: 고시 별표 2 제4호)
        </p>
      </div>
    </details>
  )
}

function BilateralExampleNotice() {
  return (
    <>
      <p className="p03-benefit-intro">
        공식 기준을 적용한 합산 예시예요. 양쪽 지원 대상자로 인정되고, 두
        보청기 각각의 최대 지급·후기 관리 조건을 충족해야 해요.
      </p>
      <p className="p03-benefit-intro">
        아동이라는 이유만으로 100% 지원 자격이 되지 않아요.
      </p>
    </>
  )
}

export function BilateralAmountExample({
  compact = false,
}: {
  compact?: boolean
}) {
  return (
    <div style={{ marginBottom: 24 }}>
      {compact ? (
        <BilateralExampleNotice />
      ) : (
        <>
          <p
            style={{
              fontSize: 13,
              color: "#4A4845",
              lineHeight: 1.6,
              marginBottom: 6,
              fontWeight: 600,
            }}
          >
            양쪽 지원 대상자로 인정된 경우의 예시예요.
          </p>
          <p
            style={{
              fontSize: 13,
              color: "#4A4845",
              lineHeight: 1.6,
              marginBottom: 8,
            }}
          >
            두 보청기 각각에 최대 지급 기준이 적용되고 후기 관리 조건까지
            충족했을 때의 합계예요.
          </p>
          <p
            style={{
              fontSize: 12,
              color: "#908D88",
              lineHeight: 1.5,
              marginBottom: 6,
            }}
          >
            공식 기준에 따라 계산한 양쪽 최대 금액 예시예요. 실제 지원액은 각
            제품의 고시금액·구입금액과 관리 조건에 따라 달라져요.
          </p>
          <p
            style={{
              fontSize: 12,
              color: "#908D88",
              lineHeight: 1.5,
              marginBottom: 14,
            }}
          >
            100%도 지원 대상으로 인정되는 금액 기준이에요. 아동이라는 이유만으로
            100% 지원 자격이 되지 않아요.
          </p>
        </>
      )}
      <BenefitAmountCards mode="bilateral" />
      {!compact && (
        <p
          style={{
            fontSize: 12,
            color: "#908D88",
            lineHeight: 1.5,
            marginTop: 10,
          }}
        >
          전체 합계는 한 번에 지급되지 않아요. 처음은 구입 후 1개월 경과 및 병원
          검수 후 청구, 후기는 구입 1년 후부터 5년까지 매년 실제 관리 후 따로
          청구해요.
        </p>
      )}
    </div>
  )
}

export function BilateralSources({ idPrefix }: { idPrefix: string }) {
  return (
    <details
      id={`${idPrefix}-sources`}
      style={{
        marginTop: 12,
        marginBottom: 12,
        background: "#f3f6fb",
        border: "1px solid #dce3ed",
        borderRadius: 8,
      }}
    >
      <summary
        style={{
          padding: "12px 14px",
          fontSize: 14,
          fontWeight: 600,
          lineHeight: 1.6,
          color: "#1a1918",
          cursor: "pointer",
          userSelect: "none",
        }}
      >
        공식 근거와 양쪽 금액 계산 방법 보기
      </summary>
      <div
        style={{
          padding: "0 14px 16px 28px",
          fontSize: 14,
          color: "#4a4845",
          lineHeight: 1.8,
          overflowWrap: "anywhere",
        }}
      >
        <p style={{ marginBottom: 6 }}>
          아래는 공식 기준을 적용해 IYUM에서 계산한 합산 상한이에요. 고시에 이
          양쪽 합계 금액이 직접 적혀 있다는 뜻은 아니에요.
        </p>
        <p style={{ marginBottom: 6 }}>
          직접 명시된 기준: 시행규칙 별표 7 제1호 라목·라목 1)의 내구연한 내
          1인당 한 번 일반원칙과 양쪽 장착 각각 1회 예외; 제3호의
          기준액·고시금액·실구입금액 중 가장 낮은 지급기준금액과 90%/100%; 고시
          별표 2 제4호의 양쪽 조건; 별표 5 아목 10)의 한쪽 기준액 131만
          원(적합관리 40만 원 포함)·내구연한 5년; 제5조의2/별표 4의 초기 20만
          원, 후기 연 5만 원·최대 4회.
        </p>
        <p style={{ marginBottom: 6 }}>
          한쪽 제품 91만 원 = 131만 원 − 관리 40만 원. 처음 111만 원 = 제품 91만
          원 + 초기 20만 원.
        </p>
        <p style={{ marginBottom: 6 }}>
          일반: 처음 111만 원 × 90% × 2 = 199만 8천 원. 후기: 5만 원 × 90% × 2 ×
          4 = 36만 원. 전체 235만 8천 원.
        </p>
        <p style={{ marginBottom: 10 }}>
          경감: 처음 111만 원 × 100% × 2 = 222만 원. 후기: 5만 원 × 100% × 2 × 4
          = 40만 원. 전체 262만 원.
        </p>
        <p style={{ fontSize: 12, color: "#908D88", marginBottom: 6 }}>
          후기 관리는 급여를 받은 기기를 지속적으로 사용하고 실제 관리받는
          조건이에요. 각 기기의 최대 지급·관리 조건을 충족한다는 전제이며 개인별
          지급 여부와 실제 금액은 공단 확인이 필요해요.
        </p>
        <p style={{ fontSize: 12, color: "#908D88", marginBottom: 6 }}>
          확인일 2026-09-25. 원문 자료 (새 탭):
        </p>
        <ul
          style={{
            margin: 0,
            padding: 0,
            listStyle: "none",
            display: "flex",
            flexDirection: "column",
            gap: 4,
          }}
        >
          <li>
            <a
              href="https://www.law.go.kr/flDownload.do?bylClsCd=110201&flSeq=162807869&gubun="
              target="_blank"
              rel="noopener noreferrer"
              style={{ fontSize: 12, color: "#1E3A8A" }}
            >
              국민건강보험법 시행규칙 별표 7 (PDF)
            </a>
          </li>
          <li>
            <a
              href="https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=87&SEQ_HISTORY=613408"
              target="_blank"
              rel="noopener noreferrer"
              style={{ fontSize: 12, color: "#1E3A8A" }}
            >
              장애인보조기기 보험급여 기준 등 세부사항 (공단)
            </a>
          </li>
        </ul>
      </div>
    </details>
  )
}

export function BilateralQuestions({
  idPrefix,
  headingRef,
  headingLevel = 3,
}: {
  idPrefix: string
  headingRef?: RefObject<HTMLHeadingElement | null>
  headingLevel?: 3 | 4
}) {
  const Heading = headingLevel === 4 ? "h4" : "h3"
  return (
    <div id={`${idPrefix}-questions`} style={{ scrollMarginTop: 20 }}>
      <Heading
        ref={headingRef}
        id={`${idPrefix}-questions-heading`}
        tabIndex={-1}
        style={{
          fontSize: 16,
          fontWeight: 700,
          color: "#1A1918",
          marginBottom: 10,
          outline: "none",
          scrollMarginTop: 20,
        }}
      >
        병원과 센터에서 이렇게 물어보세요
      </Heading>
      <ol
        style={{
          listStyle: "none",
          padding: 0,
          margin: 0,
          display: "flex",
          flexDirection: "column",
          gap: 10,
          marginBottom: 0,
        }}
      >
        {[
          {
            institution:
              "병원·이비인후과에서 / 양쪽 보청기 지원 가능 여부 문의",
            q: "아이의 검사 결과가 건강보험 양쪽 보청기 지원 조건을 모두 충족하나요? 양쪽 지원을 위한 처방이 가능한가요?",
          },
          {
            institution: "보청기센터에서 / 양쪽 보청기 비용 문의",
            q: "양쪽을 구입하면 각 보청기의 지원액과 제가 부담할 총금액은 얼마인가요?",
          },
          {
            institution: "보청기센터에서 / 사후 관리 문의",
            q: "양쪽 보청기의 초기·후기 관리에 포함되는 서비스와 별도 비용, 다음 방문 일정은 어떻게 되나요?",
          },
        ].map((item, i) => (
          <li
            key={i}
            style={{
              padding: "14px 16px",
              border: "1px solid #D0CEC9",
              borderRadius: 2,
            }}
          >
            <p
              style={{
                fontSize: 11,
                fontWeight: 600,
                color: "#908D88",
                marginBottom: 4,
              }}
            >
              {item.institution}
            </p>
            <p
              style={{
                fontSize: 14,
                color: "#1A1918",
                lineHeight: 1.6,
              }}
            >
              <span style={{ fontWeight: 700, marginRight: 6 }}>{i + 1}.</span>
              {item.q}
            </p>
          </li>
        ))}
      </ol>
      <BilateralCopyButton
        successMsg="보호자 질문을 기관명과 함께 복사했어요. 휴대폰 메모 등에 붙여 넣어 보관하세요."
        copyText={[
          "IYUM · 보호자의 양쪽 보청기 지원 상담 질문",
          "건강보험 공통 안내를 바탕으로 준비한 질문이며, 아이의 지원 자격이나 금액을 확정한 결과가 아니에요.",
          "",
          "1. 병원 · 이비인후과",
          "아이의 검사 결과가 건강보험 양쪽 보청기 지원 조건을 모두 충족하나요? 양쪽 지원을 위한 처방이 가능한가요?",
          "",
          "2. 보청기센터 · 비용",
          "양쪽을 구입하면 각 보청기의 지원액과 제가 부담할 총금액은 얼마인가요?",
          "",
          "3. 보청기센터 · 관리",
          "양쪽 보청기의 초기·후기 관리에 포함되는 서비스와 별도 비용, 다음 방문 일정은 어떻게 되나요?",
        ].join("\n")}
      />
    </div>
  )
}

export function BilateralOtherSupport() {
  return (
    <div
      style={{
        marginTop: 32,
        borderTop: "1px solid #E8E6E1",
        paddingTop: 20,
      }}
    >
      <p
        style={{
          fontSize: 13,
          fontWeight: 600,
          color: "#4A4845",
          marginBottom: 6,
        }}
      >
        장애등록 기준에 해당하지 않는 아이는요?
      </p>
      <p
        style={{
          fontSize: 13,
          color: "#908D88",
          lineHeight: 1.6,
          marginBottom: 8,
        }}
      >
        아래는 건강보험 보청기 급여와는 다른 사업이에요.
      </p>
      <a
        href="https://www.bokjiro.go.kr/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do?wlfareInfoId=WLF00001130"
        target="_blank"
        rel="noopener noreferrer"
        style={{
          fontSize: 13,
          color: "#1E3A8A",
          textDecoration: "underline",
        }}
      >
        복지로 · 선천성 난청검사 및 보청기 지원 (새 탭)
      </a>
    </div>
  )
}

function ReducedCopayHelp({ idPrefix }: { idPrefix: string }) {
  return (
    <details
      id={`${idPrefix}-reduced-copay`}
      style={{
        marginTop: 12,
        marginBottom: 12,
        background: "#f3f6fb",
        border: "1px solid #dce3ed",
        borderRadius: 8,
      }}
    >
      <summary
        style={{
          padding: "12px 14px",
          fontSize: 14,
          fontWeight: 600,
          lineHeight: 1.6,
          color: "#1a1918",
          cursor: "pointer",
          userSelect: "none",
        }}
      >
        차상위 본인부담경감 대상자란?
      </summary>
      <div
        style={{
          padding: "0 14px 16px 28px",
          fontSize: 14,
          color: "#4a4845",
          lineHeight: 1.8,
          overflowWrap: "anywhere",
        }}
      >
        <p style={{ marginBottom: 6 }}>
          소득·재산, 부양 요건과 질환·연령 등의 기준을 심사받아,
          건강보험 의료비의 본인부담을 줄여주는 지원 대상으로 인정된
          분이에요.
        </p>
        <p style={{ marginBottom: 6 }}>
          청각장애 등록만으로 자동 적용되지는 않아요. 의료급여와는
          다른 건강보험의 지원 제도예요.
        </p>
        <p>
          대상인지 모르겠다면 국민건강보험공단에 &quot;제가 차상위
          본인부담경감 대상자로 등록되어 있나요?&quot;라고 확인해 주세요.
        </p>
      </div>
    </details>
  )
}

function AfterPurchaseHelp({ idPrefix }: { idPrefix: string }) {
  return (
    <details
      id={`${idPrefix}-after-purchase`}
      style={{
        marginTop: 12,
        marginBottom: 12,
        background: "#f3f6fb",
        border: "1px solid #dce3ed",
        borderRadius: 8,
      }}
    >
      <summary
        style={{
          padding: "12px 14px",
          fontSize: 14,
          fontWeight: 600,
          lineHeight: 1.6,
          color: "#1a1918",
          cursor: "pointer",
          userSelect: "none",
        }}
      >
        구입 후 병원 확인·청구·관리는 어떻게 이어지나요?
      </summary>
      <div
        style={{
          padding: "0 14px 16px 28px",
          fontSize: 14,
          color: "#4a4845",
          lineHeight: 1.8,
          overflowWrap: "anywhere",
        }}
      >
        구입 후 1개월이 지난 뒤 이비인후과에서 착용 효과
        확인(검수확인)을 받고 지원금을 청구해요. 청구는 판매업소에
        위임하거나 직접 공단에 청구할 수 있어요. 이후 구입 1년 후부터
        5년까지 매년 1회 이상 실제 관리를 받은 뒤 후기 관리비를
        청구해요. 날짜가 지난다고 자동 입금되는 방식이 아니에요.
        개인별 청구 가능일과 계좌는 판매업소·공단에 확인하세요.
      </div>
    </details>
  )
}

function PaymentConditions({ idPrefix }: { idPrefix: string }) {
  return (
    <details
      id={`${idPrefix}-payment-conditions`}
      style={{
        marginTop: 12,
        marginBottom: 12,
        background: "#f3f6fb",
        border: "1px solid #dce3ed",
        borderRadius: 8,
      }}
    >
      <summary
        style={{
          padding: "12px 14px",
          fontSize: 14,
          fontWeight: 600,
          lineHeight: 1.6,
          color: "#1a1918",
          cursor: "pointer",
          userSelect: "none",
        }}
      >
        내 지급액이 안내된 최대 금액과 달라질 수 있나요?
      </summary>
      <div
        style={{
          padding: "0 14px 16px 28px",
          fontSize: 14,
          color: "#4a4845",
          lineHeight: 1.8,
          overflowWrap: "anywhere",
        }}
      >
        <p style={{ marginBottom: 8 }}>
          건강보험은 기본적으로 지급기준금액의 90%를 지원해요. 제품의
          고시가격이나 실제 구입가격이 낮으면 지원액도 줄어들 수 있어요.
          후기 관리도 실제 비용과 지급 조건을 확인해야 해요.
        </p>
        <p style={{ marginBottom: 8 }}>
          차상위 본인부담경감 대상 등은 같은 건강보험 안에서 100% 지원이
          적용돼요. 기준을 넘는 비용까지 모두 지원한다는 뜻은 아니에요. 본인의
          경감 적용 여부는 국민건강보험공단에서 확인하세요. 의료급여는 별도
          절차를 확인하세요.
        </p>
        <p>
          제품별 고시가격에는 초기 관리비가 이미 포함되어 있어요. 고시가격에
          20만 원을 다시 더하지 않아요.
        </p>
      </div>
    </details>
  )
}

function BilateralCopyButton({
  copyText,
  successMsg,
}: {
  copyText: string
  successMsg: string
}) {
  const [status, setStatus] = useState<"idle" | "success" | "fail">("idle")
  const [showFallback, setShowFallback] = useState(false)
  const fallbackRef = useRef<HTMLTextAreaElement>(null)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(copyText)
      setStatus("success")
      setShowFallback(false)
    } catch {
      setStatus("fail")
      setShowFallback(true)
      setTimeout(() => {
        fallbackRef.current?.focus()
        fallbackRef.current?.select()
      }, 50)
    }
  }

  return (
    <div style={{ marginTop: 12 }}>
      <button
        type="button"
        onClick={handleCopy}
        style={{
          display: "inline-flex",
          alignItems: "center",
          minHeight: 44,
          padding: "10px 18px",
          border: "1.5px solid #1A1918",
          borderRadius: 2,
          backgroundColor: "transparent",
          color: "#1A1918",
          fontSize: 14,
          fontWeight: 600,
          cursor: "pointer",
          fontFamily: "inherit",
        }}
      >
        질문 복사하기
      </button>
      <div
        role="status"
        aria-live="polite"
        style={{ marginTop: 8, fontSize: 13 }}
      >
        {status === "success" && (
          <span style={{ color: "#166534" }}>{successMsg}</span>
        )}
        {status === "fail" && (
          <span style={{ color: "#92400E" }}>
            자동 복사가 되지 않았어요. 아래 내용을 선택해 복사해 주세요.
          </span>
        )}
      </div>
      {showFallback && (
        <textarea
          ref={fallbackRef}
          readOnly
          value={copyText}
          aria-label="복사할 질문"
          onFocus={(e) => e.target.select()}
          rows={10}
          style={{
            display: "block",
            width: "100%",
            marginTop: 8,
            fontSize: 12,
            lineHeight: 1.7,
            padding: "10px 12px",
            fontFamily: "inherit",
            border: "1px solid #D0CEC9",
            borderRadius: 2,
            backgroundColor: "#FAFAF8",
            resize: "vertical",
            boxSizing: "border-box",
          }}
        />
      )}
    </div>
  )
}
