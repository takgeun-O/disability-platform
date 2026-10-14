"use client";

import { useState, useRef, useEffect, useLayoutEffect, useMemo, useSyncExternalStore } from "react";
import Link from '@/components/AppLink';
import { useNavigationGuard } from '@/components/NavigationGuard';
import PRODUCTS_RAW from "@/data/hearingAidProducts.json";
import {
  BenefitGuide,
  BilateralEligibility,
  BilateralExamConditions,
  BilateralAmountExample,
  BilateralSources,
  BilateralQuestions,
  BilateralOtherSupport,
  type BenefitMode,
} from "./HearingAidBenefits";
import DirectClaimGuide from "./DirectClaimGuide";
import RegistryCheck, { type Product } from "./RegistryCheck";
import HearingAidActions from "./HearingAidActions";
import HearingAidReferenceDialog from "./HearingAidReferenceDialog";
import { focusForReading, revealForReading } from "@/lib/reading-focus";
import { useGuidanceClipboard } from "./useGuidanceClipboard";
import "./HearingAidGuide.css";

/* ─── Types ─────────────────────────────────────────────────────────────────── */

type Q1 = "planning" | "assessment" | "waiting" | "registered" | "unknown";
type Q2 = "health" | "medical-aid" | "unknown";
type Q3 = "yes" | "no" | "unknown";
type Q4 = "first" | "previous" | "unknown";
type Q5 = "approved" | "waiting" | "not-applied" | "unknown";
type ResultCode =
  | "R01"
  | "R02"
  | "R03"
  | "R04"
  | "R05"
  | "R06"
  | "R07"
  | "R08"
  | "R09"
  | "R10"
  | "R11"
  | "R12"
  | "R13"
  | "R14"
  | "R15"
  | "R16"
  | "R17"
  | "R18"
  | "R19"
  | "R20"
  | "R21";
type PageView = "summary" | "guided" | "reference" | "bilateral";
type GuidedStep = "Q1" | "Q2" | "Q3" | "Q4" | "Q5" | "done";
type StepStatus = "done" | "current" | "check-needed" | "future";

type AnswerFactStatus =
  | "done"
  | "in-progress"
  | "check-needed"
  | "neutral"
  | "unknown";

interface AnswerFact {
  label: string;
  value: string;
  status: AnswerFactStatus;
}

const FACT_ICONS: Record<AnswerFactStatus, string | null> = {
  done: "✓",
  "in-progress": "◷",
  "check-needed": "!",
  neutral: null,
  unknown: "?",
};

interface JStep {
  name: string;
  institution: string;
  desc: string;
  status: StepStatus;
  // A first check can be highlighted without treating an unknown status as confirmed.
  highlight?: boolean;
  statusLabel: string;
  detailLines: string[];
  showDirectClaim?: boolean;
}

interface ResultInfo {
  title: string;
  currentState: string;
  institution: string;
  nextAction: string;
  nextActionNote?: string;
  contactQuestion: string | null;
  centerNote: string | null;
  questions: { text: string; purpose: string }[] | null;
  hasHealthDetail: boolean;
  onlineLink?: boolean;
  secondary?: string | null;
}

/* ─── Option data ───────────────────────────────────────────────────────────── */

const Q1_OPTS: { label: string; value: Q1 }[] = [
  { label: "아직 등록을 위한 검사·신청 전이에요", value: "planning" },
  { label: "병원에서 검사·진단을 받고 있어요", value: "assessment" },
  { label: "등록 신청 후 심사 결과를 기다려요", value: "waiting" },
  { label: "청각장애 등록을 마쳤어요", value: "registered" },
  { label: "어디까지 진행됐는지 잘 모르겠어요", value: "unknown" },
];
const Q2_OPTS: { label: string; value: Q2 }[] = [
  { label: "건강보험에 가입되어 있어요 (가족 포함)", value: "health" },
  { label: "의료급여 대상이에요", value: "medical-aid" },
  { label: "잘 모르겠어요", value: "unknown" },
];
const Q3_OPTS: { label: string; value: Q3 }[] = [
  { label: "예, 지원금 신청용 보청기 처방전을 받았어요", value: "yes" },
  { label: "아직 안 받았어요", value: "no" },
  { label: "잘 모르겠어요", value: "unknown" },
];
const Q4_OPTS: { label: string; value: Q4 }[] = [
  { label: "처음이에요", value: "first" },
  { label: "받은 적 있어요", value: "previous" },
  { label: "기억나지 않아요", value: "unknown" },
];
const Q5_OPTS: { label: string; value: Q5 }[] = [
  { label: "지원 가능하다는 안내를 받았어요", value: "approved" },
  { label: "신청하고 기다리고 있어요", value: "waiting" },
  { label: "아직 신청하지 않았어요", value: "not-applied" },
  { label: "잘 모르겠어요", value: "unknown" },
];

/* ─── Journey base steps ────────────────────────────────────────────────────── */

const BASE = [
  {
    name: "등록 확인",
    institution: "이비인후과 · 주민센터 · 국민연금공단",
    desc: "검사·진단 자료를 제출하고 심사를 거쳐 청각장애 등록 결과를 확인해요.",
  },
  {
    name: "병원 처방",
    institution: "이비인후과",
    desc: "장애등록용 진단과 별도로, 지원금 신청용 보청기 처방전을 받아요.",
  },
  {
    name: "구입·착용",
    institution: "보청기센터 · 판매업소",
    desc: "제품과 비용을 상담하고, 지원 절차를 확인한 뒤 구입해 착용해요.",
  },
  {
    name: "검수·청구",
    institution: "이비인후과 / 국민건강보험공단",
    desc: "구입 후 1개월이 지난 뒤, 병원 검수확인을 받고 지원금을 청구해요.",
  },
  {
    name: "이후 관리",
    institution: "보청기센터",
    desc: "구입 후에도 보청기를 조절·관리하며 다음 방문 일정을 정해요.",
  },
];

/* ─── Result info (all 15) ──────────────────────────────────────────────────── */

const RESULT_INFO: Record<ResultCode, ResultInfo> = {
  R01: {
    title: "등록 절차 알아보기",
    currentState: "청각장애 등록을 위한 검사·신청을 아직 시작하지 않았어요.",
    institution: "주소지 주민센터",
    nextAction:
      "방문해서 청각장애 등록 신청 방법과 진단받을 병원을 안내받으세요.",
    contactQuestion:
      "청각장애 등록을 처음 알아보고 있어요. 어떤 병원에서 검사받고, 검사 후에는 어디에 자료를 내면 되나요?",
    centerNote:
      "센터에서는 불편한 청취 상황과 제품을 상담할 수 있어요. 실제 구입은 등록·처방과 지원 절차를 확인한 뒤 결정하세요.",
    questions: [
      {
        text: "아직 등록을 위한 검사 전이에요. 구입을 결정하기 전에 제 청취 불편과 제품을 상담하고 비교해볼 수 있나요?",
        purpose: "현재 받을 수 있는 상담·제품 비교의 범위를 확인해요.",
      },
      {
        text: "보청기센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose:
          "보청기센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
      {
        text: "지금 상담한 내용을 이어가려면 어떤 결과나 처방을 확인한 뒤 다시 방문하면 되나요?",
        purpose: "내 상황에 맞는 다음 센터 방문 시점과 준비할 내용을 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R02: {
    title: "등록 과정 중 검사·진단",
    currentState: "병원에서 청각장애 등록을 위한 검사·진단을 진행 중이에요.",
    institution: "지금 진료받는 이비인후과",
    nextAction:
      "다음 진료 때 남은 검사와 등록 신청용 자료를 받을 시점을 확인하세요.",
    contactQuestion:
      "청각장애 등록을 위한 검사를 진행 중이에요. 남은 검사와 진단 자료를 받는 날짜, 주민센터에 제출할 시점을 알려주세요.",
    centerNote:
      "검사 중에도 제품 상담은 받을 수 있어요. 상담 때 검사 중이라고 알리고, 실제 구입은 등록 결과와 지원금 신청용 처방을 확인한 뒤 준비하세요.",
    questions: [
      {
        text: "장애등록 검사·진단 중이에요. 구입을 결정하기 전에 제 청취 불편과 제품을 상담하고 비교해볼 수 있나요?",
        purpose: "현재 받을 수 있는 상담·제품 비교의 범위를 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
      {
        text: "지금 상담한 내용을 이어가려면 어떤 결과나 처방을 확인한 뒤 다시 방문하면 되나요?",
        purpose: "내 상황에 맞는 다음 센터 방문 시점과 준비할 내용을 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R03: {
    title: "등록 과정 중 심사·결과 대기",
    currentState: "등록 신청을 마치고 청각장애 심사 결과를 기다리고 있어요.",
    institution: "신청한 주민센터",
    nextAction:
      "방문하거나 안내받은 연락 방법으로 결과 통지 방법과 추가 자료 요청 여부를 확인하세요.",
    contactQuestion:
      "청각장애 등록 심사 결과를 기다리고 있어요. 결과는 어떤 방법으로 안내받고, 지금 추가로 제출할 자료가 있나요?",
    centerNote:
      "결과를 기다리는 동안 제품·비용 상담은 받을 수 있어요. 등록되면 지원금 신청용 처방을 확인하고 구입을 준비해요.",
    questions: [
      {
        text: "등록 심사 결과를 기다리고 있어요. 구입을 결정하기 전에 제 청취 불편과 제품을 상담하고 비교해볼 수 있나요?",
        purpose: "현재 받을 수 있는 상담·제품 비교의 범위를 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
      {
        text: "지금 상담한 내용을 이어가려면 어떤 결과나 처방을 확인한 뒤 다시 방문하면 되나요?",
        purpose: "내 상황에 맞는 다음 센터 방문 시점과 준비할 내용을 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R04: {
    title: "등록 진행 상태 확인 필요",
    currentState: "청각장애 등록이 어디까지 진행됐는지 확인이 필요해요.",
    institution: "주소지 주민센터",
    nextAction:
      "방문해서 등록 신청 기록과 결과가 있는지 확인하세요. 아래 문장을 보여줘도 돼요.",
    contactQuestion:
      "제가 청각장애 등록을 신청한 기록이 있나요? 심사 중인지, 등록이 완료됐는지와 다음에 제가 할 일을 확인해 주세요.",
    centerNote:
      "센터에서는 알고 있는 검사·진료 내용으로 상담받고, 현재 상태에 맞춰 도와줄 수 있는 범위를 물어보세요.",
    questions: [
      {
        text: "등록 진행 상태를 확인하고 있어요. 구입을 결정하기 전에 제 청취 불편과 제품을 상담하고 비교해볼 수 있나요?",
        purpose: "현재 받을 수 있는 상담·제품 비교의 범위를 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
      {
        text: "지금 상담한 내용을 이어가려면 어떤 결과나 처방을 확인한 뒤 다시 방문하면 되나요?",
        purpose: "내 상황에 맞는 다음 센터 방문 시점과 준비할 내용을 확인해요.",
      },
    ],
    hasHealthDetail: false,
    secondary:
      "신청 기록이 없고 병원 검사만 받았다면, 그 병원에서 장애등록용 검사였는지 확인하세요.",
  },
  R05: {
    title: "의료급여 사전 절차 확인 필요",
    currentState: "청각장애 등록을 마쳤고, 의료급여 대상이라고 답하셨어요.",
    institution: "주소지 주민센터",
    nextAction:
      "구입 전에 방문해서 의료급여 보청기 지원의 신청·승인 순서를 확인하세요.",
    contactQuestion:
      "청각장애 등록을 마친 의료급여 대상자예요. 보청기를 사기 전에 처방과 신청·승인을 어떤 순서로 진행해야 하나요?",
    centerNote:
      "센터에서는 의료급여 신청을 돕는 범위와 제품 상담을 받을 수 있어요. 건강보험의 금액·청구 절차를 그대로 적용하지 않아요.",
    questions: [
      {
        text: "의료급여 대상이에요. 처방과 주민센터 신청 절차를 진행 중이에요. 지금 제품을 상담하고, 신청 절차를 마친 뒤 구입을 준비할 수 있나요?",
        purpose: "현재 상담할 범위와 센터의 신청 지원 범위를 확인해요.",
      },
      {
        text: "지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?",
        purpose: "지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.",
      },
      {
        text: "공식기관과 병원에서 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?",
        purpose: "미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R06: {
    title: "병원 처방 전",
    currentState:
      "청각장애 등록은 마쳤고, 지원금 신청용 보청기 처방전은 아직 받기 전이에요.",
    institution: "진료받을 이비인후과",
    nextAction:
      "방문 전 접수처에 지원금 신청용 보청기 처방 진료와 예약·준비사항을 문의하세요.",
    contactQuestion:
      "청각장애 등록을 마쳤어요. 보청기 건강보험 지원을 위한 처방 진료를 받으려면 어떻게 예약하고 무엇을 가져가면 되나요?",
    centerNote:
      "센터에서는 제품과 비용을 미리 상담할 수 있어요. 실제 구입 전에 처방전과 지원 절차를 확인하세요.",
    questions: [
      {
        text: "아직 지원금 신청용 보청기 처방전이 없어요. 지금 받을 수 있는 제품 상담과 병원 진료 후 다시 가져올 내용은 무엇인가요?",
        purpose: "현재 상담할 범위와 처방 확인 후 준비할 내용을 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R07: {
    title: "처방전 여부 확인 필요",
    currentState:
      "청각장애 등록은 마쳤고, 지원금 신청용 보청기 처방전을 발급받았는지 확인이 필요해요.",
    institution: "진료받은 이비인후과",
    nextAction:
      "진료받은 이비인후과에 보청기 지원금 신청용 처방전을 발급받았는지 확인하세요.",
    nextActionNote:
      "그 병원에서 받은 청력검사 결과지·진단서·처방전 등이 있다면 함께 보여주세요. 가지고 있는 서류가 없다면, 발급받은 기록이 있는지 물어보세요.",
    contactQuestion:
      "제가 보청기 지원금 신청용 처방전을 발급받았나요? 아직 발급받지 않았다면 어떤 진료나 검사가 필요한가요?",
    centerNote:
      "센터에서는 제품과 비용을 미리 상담할 수 있어요. 실제 구입 전에 처방전과 지원 절차를 확인하세요.",
    questions: [
      {
        text: "지원금 신청용 보청기 처방전을 발급받았는지 병원에 확인 중이에요. 지금 상담할 수 있는 내용과, 구입 전에 준비할 것을 알려주세요.",
        purpose: "현재 상담할 범위와 처방 확인 후 준비할 내용을 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R08: {
    title: "구입 전 제품·비용 상담",
    currentState:
      "청각장애 등록과 지원금 신청용 처방을 마쳤고, 지원금은 처음 신청한다고 답하셨어요.",
    institution: "상담할 보청기센터",
    nextAction:
      "제품 추천 이유와 실제 부담할 금액, 신청을 도와주는 범위를 상담하세요. 공단 등록 업소·제품인지도 함께 확인해요.",
    contactQuestion:
      "지원금은 처음 신청해요. 공단 등록 업소와 제품인지, 제가 내는 총금액과 포함된 관리 서비스, 구입 전에 직접 할 일을 알려주세요.",
    centerNote:
      "상담한 뒤 제품과 구입 시점을 결정하세요. 구입 이후에도 병원 확인과 센터 관리가 이어져요.",
    questions: [
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "제가 실제로 내는 총금액과 포함된 서비스는 무엇인가요? 청구를 맡길 수 있는지도 알려주세요.",
        purpose: "제품·관리 비용과 청구를 맡기는 범위를 확인해요.",
      },
      {
        text: "구입 후 병원 확인과 센터 관리는 언제 받나요? 관리에 포함되는 서비스와 별도 비용도 알려주세요.",
        purpose: "다음 방문 장소·일정과 관리 범위·비용을 확인해요.",
      },
    ],
    hasHealthDetail: true,
  },
  R09: {
    title: "구입 전, 이전 지원 이력 확인 필요",
    currentState:
      "지원금 신청용 처방전은 받았고, 이전 지원 날짜와 재지원 조건을 확인해야 해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 이전 보청기 구입·지원 기록과 이번 지원 가능 시점을 확인하세요.",
    contactQuestion:
      "이전에 보청기 건강보험 지원을 받은 기록과 구입 날짜를 확인해 주세요. 이번에 다시 지원받으려면 어떤 조건을 확인해야 하나요?",
    centerNote:
      "센터에서는 제품과 관리 조건을 상담하고, 사용 중인 보청기가 있다면 조절·수리도 함께 물어보세요. 실제 구입은 이력 확인 후 결정해요. 내구연한은 5년이지만, 기간이 지났다고 재지원이 확정되지는 않아요.",
    questions: [
      {
        text: "이전에 지원받은 적이 있어요. 구입할 제품을 상담하고, 사용 중인 보청기가 있다면 조절·수리와 새 제품 구입도 비교해볼 수 있나요?",
        purpose:
          "구입할 제품과, 사용 중인 보청기가 있다면 관리 대안도 비교해요.",
      },
      {
        text: "이번 지원 여부가 확인되면 실제 부담액과 관리 서비스를 비교해 주세요. 청구는 어디까지 도와주시나요?",
        purpose: "지원 확인 후의 비용과 센터 지원 범위를 확인해요.",
      },
      {
        text: "구입 후 병원 확인과 센터 관리는 언제 받나요? 관리에 포함되는 서비스와 별도 비용도 알려주세요.",
        purpose: "다음 방문 장소·일정과 관리 범위·비용을 확인해요.",
      },
    ],
    hasHealthDetail: true,
    onlineLink: true,
  },
  R10: {
    title: "구입 전, 이전 지원 이력 확인 필요",
    currentState:
      "지원금 신청용 처방전은 받았고, 이전 지원 이력은 확인이 필요해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 이전 보청기 지원 기록이 있는지부터 확인하세요.",
    contactQuestion:
      "이전에 보청기 건강보험 지원을 받은 기록이 있는지 확인해 주세요. 기록이 있다면 지원받은 날짜와 이번 신청 전에 확인할 조건을 알려주세요.",
    centerNote:
      "센터에서는 지금 상담할 수 있는 내용과 지원 이력 확인 후 구입 전에 점검할 내용을 물어보세요. 실제 구입은 기록 유무와 적용 조건을 확인한 뒤 결정해요.",
    questions: [
      {
        text: "이전 지원 여부를 확인 중이에요. 지금 상담할 수 있는 내용과 지원 이력 확인 후 구입 전에 점검할 내용을 알려주세요.",
        purpose: "현재 상담할 범위와 지원 기록 확인 후 점검할 내용을 구분해요.",
      },
      {
        text: "이번 지원 여부가 확인되면 실제 부담액과 관리 서비스를 비교해 주세요. 청구는 어디까지 도와주시나요?",
        purpose: "지원 확인 후의 비용과 센터 지원 범위를 확인해요.",
      },
      {
        text: "구입 후 병원 확인과 센터 관리는 언제 받나요? 관리에 포함되는 서비스와 별도 비용도 알려주세요.",
        purpose: "다음 방문 장소·일정과 관리 범위·비용을 확인해요.",
      },
    ],
    hasHealthDetail: true,
    onlineLink: true,
  },
  R11: {
    title: "보험 자격 확인 필요",
    currentState: "청각장애 등록은 마쳤고, 보험 자격은 확인이 필요해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 현재 보험 자격과 보청기 지원 절차를 확인하세요.",
    contactQuestion:
      "제가 현재 건강보험과 의료급여 중 어디에 해당하나요? 보청기 지원을 받으려면 구입 전에 무엇을 확인해야 하나요?",
    centerNote:
      "보험 자격을 확인하는 동안 제품 상담은 받을 수 있어요. 센터에는 자격이 미확인이라고 알리고, 지원액과 실제 구입 시점은 자격 확인 후 정하세요.",
    questions: [
      {
        text: "보험 자격은 아직 확인 중이에요. 지원금 신청용 보청기 처방전은 아직 없어요. 현재 제품을 상담하고, 제 제도가 확인되면 신청도 도움받을 수 있나요?",
        purpose:
          "지원 제도를 확정하기 전 상담할 내용과 센터의 신청 지원 범위를 확인해요.",
      },
      {
        text: "지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?",
        purpose: "지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.",
      },
      {
        text: "공식기관과 병원에서 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?",
        purpose: "미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.",
      },
    ],
    hasHealthDetail: false,
    onlineLink: true,
    secondary:
      "처방전은 아직 받기 전이에요. 보험 자격을 확인한 뒤 해당 절차에 맞춰 이비인후과 진료를 준비하세요.",
  },
  R12: {
    title: "보험 자격 확인 필요",
    currentState: "청각장애 등록은 마쳤고, 보험 자격은 확인이 필요해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 현재 보험 자격과 보청기 지원 절차를 확인하세요.",
    contactQuestion:
      "제가 현재 건강보험과 의료급여 중 어디에 해당하나요? 보청기 지원을 받으려면 구입 전에 무엇을 확인해야 하나요?",
    centerNote:
      "보험 자격을 확인하는 동안 제품 상담은 받을 수 있어요. 센터에는 자격이 미확인이라고 알리고, 지원액과 실제 구입 시점은 자격 확인 후 정하세요.",
    questions: [
      {
        text: "보험 자격은 아직 확인 중이에요. 처방전 여부도 확인 중이에요. 현재 제품을 상담하고, 제 제도가 확인되면 신청도 도움받을 수 있나요?",
        purpose:
          "지원 제도를 확정하기 전 상담할 내용과 센터의 신청 지원 범위를 확인해요.",
      },
      {
        text: "지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?",
        purpose: "지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.",
      },
      {
        text: "공식기관과 병원에서 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?",
        purpose: "미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.",
      },
    ],
    hasHealthDetail: false,
    onlineLink: true,
    secondary:
      '처방전 여부도 미확인이에요. 진료받은 병원에 "지원금 신청용 보청기 처방전을 발급받았나요?"라고 물어보세요.',
  },
  R13: {
    title: "보험 자격 확인 필요",
    currentState:
      "청각장애 등록과 지원금 신청용 처방은 마쳤고, 보험 자격은 확인이 필요해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 현재 보험 자격과 보청기 지원 절차를 확인하세요.",
    contactQuestion:
      "제가 현재 건강보험과 의료급여 중 어디에 해당하나요? 보청기 지원을 받으려면 구입 전에 무엇을 확인해야 하나요?",
    centerNote:
      "보험 자격을 확인하는 동안 제품 상담은 받을 수 있어요. 센터에는 자격이 미확인이라고 알리고, 지원액과 실제 구입 시점은 자격 확인 후 정하세요.",
    questions: [
      {
        text: "보험 자격은 아직 확인 중이에요. 지원금 신청용 보청기 처방전은 받았어요. 현재 제품을 상담하고, 제 제도가 확인되면 신청도 도움받을 수 있나요?",
        purpose:
          "지원 제도를 확정하기 전 상담할 내용과 센터의 신청 지원 범위를 확인해요.",
      },
      {
        text: "지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?",
        purpose: "지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.",
      },
      {
        text: "공식기관과 병원에서 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?",
        purpose: "미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.",
      },
    ],
    hasHealthDetail: false,
    onlineLink: true,
  },
  R14: {
    title: "보험 자격 확인 필요",
    currentState:
      "청각장애 등록과 지원금 신청용 처방은 마쳤고, 보험 자격은 확인이 필요해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 현재 보험 자격과 보청기 지원 절차를 확인하세요.",
    contactQuestion:
      "제가 현재 건강보험과 의료급여 중 어디에 해당하나요? 보청기 지원을 받으려면 구입 전에 무엇을 확인해야 하나요? 이전 보청기 지원 기록도 어디에서 확인할 수 있나요?",
    centerNote:
      "보험 자격을 확인하는 동안 제품 상담은 받을 수 있어요. 센터에는 자격이 미확인이라고 알리고, 지원액과 실제 구입 시점은 자격 확인 후 정하세요.",
    questions: [
      {
        text: "보험 자격은 아직 확인 중이에요. 지원금 신청용 보청기 처방전은 받았어요. 현재 제품을 상담하고, 제 제도가 확인되면 신청도 도움받을 수 있나요?",
        purpose:
          "지원 제도를 확정하기 전 상담할 내용과 센터의 신청 지원 범위를 확인해요.",
      },
      {
        text: "지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?",
        purpose: "지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.",
      },
      {
        text: "보험 자격과 이전 지원 기록을 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?",
        purpose: "미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.",
      },
    ],
    hasHealthDetail: false,
    onlineLink: true,
  },
  R15: {
    title: "보험 자격 확인 필요",
    currentState:
      "청각장애 등록과 지원금 신청용 처방은 마쳤고, 보험 자격은 확인이 필요해요.",
    institution: "국민건강보험공단",
    nextAction:
      "온라인 개인 상담이나 지사 방문으로 현재 보험 자격과 보청기 지원 절차를 확인하세요.",
    contactQuestion:
      "제가 현재 건강보험과 의료급여 중 어디에 해당하나요? 보청기 지원을 받으려면 구입 전에 무엇을 확인해야 하나요? 이전 보청기 지원 기록도 어디에서 확인할 수 있나요?",
    centerNote:
      "보험 자격을 확인하는 동안 제품 상담은 받을 수 있어요. 센터에는 자격이 미확인이라고 알리고, 지원액과 실제 구입 시점은 자격 확인 후 정하세요.",
    questions: [
      {
        text: "보험 자격은 아직 확인 중이에요. 지원금 신청용 보청기 처방전은 받았어요. 현재 제품을 상담하고, 제 제도가 확인되면 신청도 도움받을 수 있나요?",
        purpose:
          "지원 제도를 확정하기 전 상담할 내용과 센터의 신청 지원 범위를 확인해요.",
      },
      {
        text: "지원 여부가 확인되면 제가 내는 금액과 포함된 관리 서비스를 나눠 설명해 주실 수 있나요?",
        purpose: "지원이 확인된 뒤 비교할 실제 부담액과 서비스를 확인해요.",
      },
      {
        text: "보험 자격과 이전 지원 기록을 확인한 뒤 어떤 내용을 가지고 다시 상담하면 되나요?",
        purpose: "미확인 내용을 해결한 뒤 이어갈 상담과 다음 방문을 준비해요.",
      },
    ],
    hasHealthDetail: false,
    onlineLink: true,
  },
  R16: {
    title: "병원 처방 상담 단계",
    currentState:
      "청각장애 등록을 마쳤고, 의료급여 대상이에요. 지원금 신청용 보청기 처방전은 아직 받기 전이에요.",
    institution: "진료받을 이비인후과",
    nextAction:
      "의료급여 보청기 지원을 위한 처방 진료를 예약하고, 필요한 검사와 준비사항을 문의하세요.",
    contactQuestion:
      "의료급여 대상 청각장애인이에요. 보청기 지원을 위한 처방 진료를 받으려면 어떻게 예약하고 무엇을 준비해야 하나요?",
    centerNote:
      "처방 전에도 제품과 비용을 상담할 수 있어요. 실제 구입은 처방과 주민센터 신청·결정 통보 이후에 진행해요.",
    questions: [
      {
        text: "아직 지원금 신청용 보청기 처방전이 없어요. 처방 진료 후 다시 방문할 때 무엇을 가져오면 되나요?",
        purpose: "현재 상담할 범위와 처방 확인 후 준비할 내용을 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R17: {
    title: "처방전 여부 확인 필요",
    currentState:
      "청각장애 등록을 마쳤고, 의료급여 대상이에요. 의료급여 보청기 지원용 처방전을 발급받았는지 확인이 필요해요.",
    institution: "진료받은 이비인후과",
    nextAction:
      "진료받은 이비인후과에 의료급여 보청기 지원용 처방전을 발급받았는지 확인하세요.",
    nextActionNote:
      "그 병원에서 받은 청력검사 결과지·진단서·처방전 등이 있다면 함께 보여주세요. 가지고 있는 서류가 없다면, 발급받은 기록이 있는지 물어보세요.",
    contactQuestion:
      "제가 의료급여 보청기 지원용 처방전을 발급받았나요? 아직 발급받지 않았다면 어떤 진료나 검사가 필요한가요?",
    centerNote:
      "처방 확인 전에도 제품 상담은 받을 수 있어요. 실제 구입은 처방과 주민센터 신청·결정 통보 이후에 진행해요.",
    questions: [
      {
        text: "의료급여 보청기 지원용 처방전을 발급받았는지 병원에 확인 중이에요. 지금 상담할 수 있는 내용과, 구입 전에 준비할 것을 알려주세요.",
        purpose: "현재 상담할 범위와 처방 확인 후 준비할 내용을 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R18: {
    title: "센터 상담·구입 준비 단계",
    currentState:
      "처방을 마쳤고, 주민센터에서 보청기 지원 가능 안내를 받으셨어요. 이제 센터에서 제품과 비용을 상담하세요.",
    institution: "상담할 보청기센터",
    nextAction:
      "안내받은 지원 조건을 바탕으로 센터에서 제품·비용과 구입 전 확인 사항을 상담하세요.",
    contactQuestion:
      "의료급여 보청기 지원 결정 통보를 받았어요. 통보서를 보여드릴게요. 제 조건에 맞는 제품과 제가 실제로 내는 금액, 구입 절차를 설명해 주세요.",
    centerNote:
      "상담한 뒤 제품과 구입 시점을 결정하세요. 구입 후에도 병원 검수 확인과 청구 절차가 이어져요.",
    questions: [
      {
        text: "의료급여 보청기 지원 결정을 받았어요. 제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "제가 실제로 내는 금액과 포함된 서비스는 무엇인가요? 구입 후 청구 절차도 안내해 주세요.",
        purpose: "실제 부담액, 포함된 서비스, 청구 방법을 확인해요.",
      },
      {
        text: "구입 후 병원 검수 확인과 센터 관리는 언제 받나요? 관리에 포함되는 서비스와 별도 비용도 알려주세요.",
        purpose: "다음 방문 장소·일정과 관리 범위·비용을 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R19: {
    title: "주민센터 신청 결과 대기 중",
    currentState:
      "처방을 마치고 주민센터에 보청기 지원을 신청한 상태예요. 결과 안내를 기다리고 있어요.",
    institution: "신청한 주민센터·시군구 담당자",
    nextAction:
      "신청을 접수한 담당자에게 처리 상태와 결과 통지 방법을 확인하세요.",
    contactQuestion:
      "보청기 의료급여 지원을 신청하고 기다리고 있어요. 처리 상태와 결과는 어떤 방법으로 안내받나요? 지금 추가로 제출할 자료가 있나요?",
    centerNote:
      "대기 중에도 제품 상담은 받을 수 있어요. 실제 구입은 주민센터 결정 통보를 받은 뒤 진행해요.",
    questions: [
      {
        text: "주민센터에 신청하고 기다리고 있어요. 지금 제품을 상담하고, 결정 통보 후 구입 절차도 미리 안내받을 수 있나요?",
        purpose: "현재 상담할 범위와 결정 이후 구입 절차를 미리 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R20: {
    title: "주민센터 신청 단계",
    currentState:
      "처방을 마쳤고, 주민센터에 보청기 지원 신청을 아직 하지 않았어요.",
    institution: "주소지 주민센터",
    nextAction:
      "처방전을 가져가 주민센터에 의료급여 보청기 지원 신청 방법을 문의하세요.",
    contactQuestion:
      "청각장애 등록과 보청기 처방전을 마쳤어요. 의료급여 보청기 지원을 신청하려면 어떤 서류를 가져와야 하고, 신청 후 어떤 순서로 진행되나요?",
    centerNote:
      "신청 전에도 제품 상담은 받을 수 있어요. 실제 구입은 주민센터 결정 통보를 받은 뒤 진행해요.",
    questions: [
      {
        text: "아직 주민센터에 신청하지 않았어요. 지금 제품을 상담하고, 신청 후 구입 절차도 미리 안내받을 수 있나요?",
        purpose: "현재 상담할 범위와 신청 이후 구입 절차를 미리 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
  R21: {
    title: "주민센터 신청 상태 확인 필요",
    currentState:
      "처방을 마쳤고, 주민센터에 보청기 지원을 신청했는지와 처리 상태를 확인해야 해요.",
    institution: "주소지 주민센터·시군구 담당자",
    nextAction:
      "주민센터 담당자에게 이번 보청기 지원의 신청 기록과 처리 상태를 확인하세요.",
    contactQuestion:
      "청각장애 등록과 보청기 처방을 마쳤어요. 보청기 의료급여 지원을 신청한 기록이 있는지, 있다면 처리 상태를 확인해 주세요.",
    centerNote:
      "신청 상태를 확인하는 동안 제품 상담은 받을 수 있어요. 실제 구입은 주민센터 결정 통보를 받은 뒤 진행해요.",
    questions: [
      {
        text: "신청 상태를 확인 중이에요. 지금 제품을 상담하고, 결정 통보 후 구입 절차도 미리 안내받을 수 있나요?",
        purpose: "현재 상담할 범위와 결정 이후 구입 절차를 미리 확인해요.",
      },
      {
        text: "제 청력과 생활환경에 왜 이 제품을 추천하나요? 비교할 수 있는 다른 제품도 알려주세요.",
        purpose: "추천 이유와 비교 대안을 확인해요.",
      },
      {
        text: "센터에서 신청을 도와주는 범위와 제가 직접 방문하거나 서명할 일은 무엇인가요?",
        purpose: "센터의 지원 범위와 내가 직접 해야 할 일을 나눠 확인해요.",
      },
    ],
    hasHealthDetail: false,
  },
};

/* ─── Product data ──────────────────────────────────────────────────────────── */

const PRODUCTS_META = PRODUCTS_RAW as {
  sourceUrl: string;
  notice: string;
  effectiveAt: string;
  checkedAt: string;
  products: Product[];
};

const NHIS_ONLINE_URL =
  "https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do";

function normalizeSearch(s: string): string {
  return s
    .normalize("NFKC")
    .toLowerCase()
    .replace(/[\s\-]/g, "");
}

const BRAND_ALIASES: Record<string, string[]> = {
  starkey: ["스타키"],
  phonak: ["포낙"],
};

function searchProducts(query: string): Product[] {
  const q = normalizeSearch(query);
  if (!q) return [];
  const aliasQ = Object.entries(BRAND_ALIASES).reduce((acc, [eng, kor]) => {
    kor.forEach((k) => {
      if (normalizeSearch(k) === q || normalizeSearch(k).includes(q))
        acc = normalizeSearch(eng);
    });
    if (normalizeSearch(eng) === q || normalizeSearch(eng).includes(q)) {
      kor.forEach((k) => {
        acc = normalizeSearch(k);
      });
    }
    return acc;
  }, q);

  const exact: Product[] = [];
  const partial: Product[] = [];
  PRODUCTS_META.products.forEach((p) => {
    const nm = normalizeSearch(p.model);
    const id = normalizeSearch(p.id);
    const co = normalizeSearch(p.company);
    if (nm === q || id === q || nm === aliasQ || id === aliasQ) {
      exact.push(p);
    } else if (
      nm.includes(q) ||
      id.includes(q) ||
      co.includes(q) ||
      nm.includes(aliasQ) ||
      id.includes(aliasQ) ||
      co.includes(aliasQ)
    ) {
      partial.push(p);
    }
  });
  return [...exact, ...partial];
}

/* ─── Step institution/detail helpers (per-insurance) ──────────────────────── */

function stepInstitution(idx: number, q2: Q2 | null): string {
  if (idx === 3) {
    if (q2 === "medical-aid")
      return "이비인후과 · 청구 절차는 주민센터에서 확인";
    if (q2 === "health")
      return "이비인후과 · 위임 청구: 판매업소 / 직접 청구: 국민건강보험공단";
    return "이비인후과 · 건강보험 청구: 판매업소 위임 또는 국민건강보험공단";
  }
  return BASE[idx].institution;
}

function stepDetails(idx: number, q1: Q1 | null, q2: Q2 | null): string[] {
  if (idx === 0) {
    const base = [
      "내부 흐름: 검사·진단(이비인후과) → 등록 신청·자료 제출(주소지 주민센터) → 장애정도 심사(국민연금공단) → 등록 결과 확인(주민센터)",
      "단순 난청 진단은 청각장애 등록 완료가 아니에요.",
    ];
    if (q1 === "assessment") base.push("현재 위치: 검사·진단 중");
    if (q1 === "waiting") base.push("현재 위치: 장애정도 심사 대기");
    return base;
  }
  if (idx === 1) {
    return [
      "장애등록용 진단서·검사 자료와 지원금 신청용 처방전(보조기기 처방전)은 별도 서류예요.",
    ];
  }
  if (idx === 2) {
    if (q2 === "medical-aid") {
      return [
        "의료급여는 구입 전에 주민센터에서 지원 신청을 하고 결정 통보를 받는 절차가 있어요.",
        "결정 통보를 받은 뒤 센터에서 제품을 구입해요.",
        "공단 등록 업소·제품인지 확인해요.",
      ];
    }
    return [
      "공단 등록 업소·제품인지 확인해요.",
      "제품 추천 이유, 실제 부담 금액, 신청 지원 범위를 상담하세요.",
      "상담 후 제품과 구입 시점을 결정해요.",
    ];
  }
  if (idx === 3) {
    if (q2 === "medical-aid")
      return [
        "이비인후과에서 착용 효과 확인(검수확인)을 받아요.",
        "의료급여 청구 절차는 구입 전에 주민센터에서 확인하세요.",
      ];
    if (q2 === "health")
      return [
        "구입 후 1개월이 지난 뒤 이비인후과에서 착용 효과 확인(검수확인)을 받아요.",
        "위임 청구: 판매업소에 청구를 맡겨요. 위임장·신분증 사본 등 필요 자료를 안내받으세요.",
        "직접 청구: 지급청구서와 자료를 국민건강보험공단에 제출해요.",
      ];
    return [
      "이비인후과에서 보청기 착용에 따른 청력 개선 효과를 확인받아요.",
      "검수확인 후 판매업소에 청구를 맡기거나 국민건강보험공단에 직접 청구해요.",
      "지원금은 공단의 확인을 거쳐 지급돼요.",
    ];
  }
  if (idx === 4) {
    const lines = [
      "구입 후 1년부터 5년까지 보청기센터에서 조절·관리를 받아요.",
    ];
    if (q2 === "health") {
      lines.push("연 1회 이상 실제 관리를 받은 뒤 관리비를 따로 청구해요.");
    }
    return lines;
  }
  return [];
}

/* ─── Computation helpers ───────────────────────────────────────────────────── */

function getCurrentStep(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  q5?: Q5 | null,
): GuidedStep {
  if (!q1) return "Q1";
  if (q1 !== "registered") return "done";
  if (!q2) return "Q2";
  if (!q3) return "Q3";
  if (q3 !== "yes") return "done";
  if (!q4) return "Q4";
  if (q2 === "medical-aid" && !q5) return "Q5";
  return "done";
}

function getLastAnsweredQ(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  q5?: Q5 | null,
): "Q1" | "Q2" | "Q3" | "Q4" | "Q5" | null {
  if (!q1) return null;
  if (q1 !== "registered") return "Q1";
  if (!q2) return null;
  if (!q3) return q2 === "medical-aid" ? "Q2" : null;
  if (q3 !== "yes") return "Q3";
  if (!q4) return q2 === "medical-aid" ? "Q3" : null;
  if (q2 === "medical-aid" && q5) return "Q5";
  return "Q4";
}

function computeResult(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  q5?: Q5 | null,
): ResultCode | null {
  if (!q1) return null;
  if (q1 === "planning") return "R01";
  if (q1 === "assessment") return "R02";
  if (q1 === "waiting") return "R03";
  if (q1 === "unknown") return "R04";
  if (!q2) return null;
  if (q2 === "medical-aid") {
    if (!q3) return null;
    if (q3 === "no") return "R16";
    if (q3 === "unknown") return "R17";
    if (!q4 || !q5) return null;
    if (q5 === "approved") return "R18";
    if (q5 === "waiting") return "R19";
    if (q5 === "not-applied") return "R20";
    return "R21";
  }
  if (!q3) return null;
  if (q3 === "no") return q2 === "health" ? "R06" : "R11";
  if (q3 === "unknown") return q2 === "health" ? "R07" : "R12";
  if (!q4) return null;
  if (q4 === "first") return q2 === "health" ? "R08" : "R13";
  if (q4 === "previous") return q2 === "health" ? "R09" : "R14";
  return q2 === "health" ? "R10" : "R15";
}

function getResultInfo(
  code: ResultCode,
  insurance: Q2 | null,
  history: Q4 | null,
  application: Q5 | null,
): ResultInfo {
  const info = RESULT_INFO[code];
  if (
    insurance !== "medical-aid" ||
    !history ||
    !application ||
    !["R18", "R19", "R20", "R21"].includes(code)
  ) return info;

  if (history === "first") {
    return {
      ...info,
      contactQuestion: `보청기 지원금은 처음 신청해요. ${info.contactQuestion}`,
    };
  }

  const historyStatement = history === "previous"
    ? "이전에 보청기 지원금을 받은 적이 있어요."
    : "이전에 보청기 지원금을 받았는지 기억나지 않아요.";
  const recordQuestion = history === "previous"
    ? "이전에 지원받은 날짜와 이번 지원에 적용되는 조건도 알려주세요."
    : "이전에 지원받은 기록이 있는지 확인해 주세요. 기록이 있다면 지원받은 날짜와 이번 지원에 적용되는 조건도 알려주세요.";
  const approved = application === "approved";
  const historyNote = approved
    ? history === "previous"
      ? "구입은 이번 결정 통보의 조건을 기준으로 준비해요. 이전 지원 날짜와 관련해 궁금한 점은 통보한 주민센터·시군구 담당자에게 확인하세요."
      : "구입은 이번 결정 통보의 조건을 기준으로 준비해요. 이전 지원 기록이 있는지와 이번 안내에 반영됐는지는 통보한 주민센터·시군구 담당자에게 확인할 수 있어요."
    : history === "previous"
      ? "주민센터·시군구 담당자에게 이전 지원 날짜와 이번 신청에 적용되는 조건도 함께 확인하세요."
      : "주민센터·시군구 담당자에게 이전 지원 기록이 있는지부터 확인하세요. 기록이 있다면 지원받은 날짜와 이번 신청 조건도 함께 물어보세요.";
  const centerQuestion = approved
    ? `${historyStatement} 이번 결정 통보에 맞춰 구입 전에 점검할 내용을 알려주세요.`
    : `${historyStatement} 이전 지원 이력은 담당자에게 확인할게요. 구입 전에 센터에서 함께 점검할 내용도 알려주세요.`;

  return {
    ...info,
    nextActionNote: historyNote,
    contactQuestion: `${info.contactQuestion} ${approved ? centerQuestion : `${historyStatement} ${recordQuestion}`}`,
    questions: info.questions?.map((question, index) => index === 0
      ? { ...question, text: `${question.text} ${centerQuestion}` }
      : question) ?? null,
  };
}

function makeStep(
  idx: number,
  status: StepStatus,
  statusLabel: string,
  q1: Q1 | null,
  q2: Q2 | null,
): JStep {
  return {
    ...BASE[idx],
    institution: stepInstitution(idx, q2),
    status,
    statusLabel,
    detailLines: stepDetails(idx, q1, q2),
    showDirectClaim: idx === 3 && q2 === "health",
  };
}

function computeJourney(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  q5?: Q5 | null,
): JStep[] {
  const F = (i: number) => makeStep(i, "future", "이후 과정", q1, q2);

  if (!q1 || q1 === "unknown") {
    const s0status: StepStatus = q1 === "unknown" ? "check-needed" : "future";
    const s0label = q1 === "unknown" ? "진행 상태 확인 필요" : "이후 과정";
    return [makeStep(0, s0status, s0label, q1, q2), F(1), F(2), F(3), F(4)];
  }
  if (q1 === "planning")
    return [
      makeStep(0, "current", "검사 전·신청 전", q1, q2),
      F(1),
      F(2),
      F(3),
      F(4),
    ];
  if (q1 === "assessment")
    return [
      makeStep(0, "current", "검사·진단 중", q1, q2),
      F(1),
      F(2),
      F(3),
      F(4),
    ];
  if (q1 === "waiting")
    return [
      makeStep(0, "current", "심사 결과 대기", q1, q2),
      F(1),
      F(2),
      F(3),
      F(4),
    ];

  const s0 = makeStep(0, "done", "등록 완료", q1, q2);

  if (!q2) return [s0, F(1), F(2), F(3), F(4)];

  if (q2 === "medical-aid") {
    if (!q3) {
      return [s0, F(1), F(2), F(3), F(4)];
    }
    if (q3 === "no") {
      return [
        s0,
        makeStep(1, "current", "처방 받기 전", q1, q2),
        F(2),
        F(3),
        F(4),
      ];
    }
    if (q3 === "unknown") {
      const prescription = {
        ...makeStep(1, "check-needed", "처방 확인 필요 · 먼저 확인", q1, q2),
        highlight: true,
      };
      return [s0, prescription, F(2), F(3), F(4)];
    }
    // q3 === "yes"
    const s1done = makeStep(1, "done", "처방 완료", q1, q2);
    const applicationStep = (status: StepStatus, label: string): JStep => ({
      ...makeStep(2, status, label, q1, q2),
      institution: "주민센터·시군구",
      desc: "구입 전 신청·결정 확인",
    });
    if (!q5) {
      return [
        s0,
        s1done,
        applicationStep("check-needed", "구입 전 신청 상태 확인 필요"),
        F(3),
        F(4),
      ];
    }
    if (q5 === "approved") {
      return [
        s0,
        s1done,
        makeStep(2, "current", "센터 상담·구입 준비", q1, q2),
        F(3),
        F(4),
      ];
    }
    if (q5 === "waiting") {
      return [
        s0,
        s1done,
        applicationStep("current", "구입 전 신청 결과 대기"),
        F(3),
        F(4),
      ];
    }
    if (q5 === "not-applied") {
      return [
        s0,
        s1done,
        applicationStep("current", "구입 전 주민센터 신청"),
        F(3),
        F(4),
      ];
    }
    return [
      s0,
      s1done,
      applicationStep("check-needed", "구입 전 신청 상태 확인 필요"),
      F(3),
      F(4),
    ];
  }

  if (!q3) return [s0, F(1), F(2), F(3), F(4)];

  if (q3 === "no") {
    const label = q2 === "health" ? "처방전 받기 전" : "보험·처방 확인 필요";
    const status: StepStatus = q2 === "health" ? "current" : "check-needed";
    return [s0, makeStep(1, status, label, q1, q2), F(2), F(3), F(4)];
  }
  if (q3 === "unknown") {
    const label =
      q2 === "health" ? "처방전 여부 확인 필요" : "보험·처방 확인 필요";

    const prescription = {
      ...makeStep(1, "check-needed", label, q1, q2),
      highlight: q2 === "health",
    };

    return [s0, prescription, F(2), F(3), F(4)];
  }

  const s1 = makeStep(1, "done", "처방전 받음", q1, q2);

  if (!q4) return [s0, s1, F(2), F(3), F(4)];

  if (q2 === "health") {
    if (q4 === "first")
      return [
        s0,
        s1,
        makeStep(2, "current", "제품·비용 상담", q1, q2),
        F(3),
        F(4),
      ];
    const label = q4 === "previous" ? "이전 이력 확인 필요" : "이력 확인 필요";
    const purchasePreparation = {
      ...makeStep(2, "check-needed", label, q1, q2),
      highlight: true,
    };

    return [s0, s1, purchasePreparation, F(3), F(4)];
  }
  const label =
    q4 === "previous" ? "보험·이력 확인 필요" : "보험 자격 확인 필요";
  return [s0, s1, makeStep(2, "check-needed", label, q1, q2), F(3), F(4)];
}

function getJourneyNote(code: ResultCode): string | null {
  if (["R06", "R07", "R08", "R09", "R10"].includes(code)) return null;
  if (code === "R05")
    return "아래는 기관별 큰 흐름이에요. 의료급여의 신청·승인과 청구 순서는 구입 전 주민센터에서 확인하세요.";
  return "아래는 기본 흐름이에요. 보험 자격에 따라 구입 전 승인과 청구 절차가 달라질 수 있어요.";
}

function computeTopBarSteps(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  step: GuidedStep,
  q5?: Q5 | null,
): JStep[] {
  const F = (i: number) => makeStep(i, "future", "", q1, q2);

  if (step === "Q1")
    return [
      makeStep(0, "current", "지금 확인 중", q1, q2),
      F(1),
      F(2),
      F(3),
      F(4),
    ];

  const s0 = makeStep(0, "done", "등록 완료", q1, q2);
  if (step === "Q2") return [s0, F(1), F(2), F(3), F(4)];
  if (step === "Q3")
    return [
      s0,
      makeStep(1, "current", "지금 확인 중", q1, q2),
      F(2),
      F(3),
      F(4),
    ];
  if (step === "Q4")
    return [s0, makeStep(1, "done", "처방전 받음", q1, q2), F(2), F(3), F(4)];
  if (step === "Q5") return computeJourney(q1, q2, q3, q4, q5);

  return computeJourney(q1, q2, q3, q4, q5);
}

// Follow the next question, or the first step that needs attention in a result.
// This selects an explanation only; completion and highlight rules stay separate.
function getAnswerJourneyStep(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  q5: Q5 | null,
): number | null {
  switch (getCurrentStep(q1, q2, q3, q4, q5)) {
    case "Q1":
    case "Q2":
      return 0;
    case "Q3":
      return 1;
    case "Q4":
    case "Q5":
      return 2;
    case "done": {
      const index = computeJourney(q1, q2, q3, q4, q5).findIndex(
        (step) => step.status === "current" || step.status === "check-needed",
      );
      return index < 0 ? null : index;
    }
  }
}

function computeFacts(
  q1: Q1 | null,
  q2: Q2 | null,
  q3: Q3 | null,
  q4: Q4 | null,
  q5?: Q5 | null,
): AnswerFact[] {
  if (!q1) return [];

  const registration: Record<Q1, Omit<AnswerFact, "label">> = {
    planning: { value: "등록 검사·신청 전", status: "check-needed" },
    assessment: { value: "병원 검사·진단 중", status: "in-progress" },
    waiting: { value: "등록 신청 후 심사 대기", status: "in-progress" },
    registered: { value: "등록 완료", status: "done" },
    unknown: { value: "진행 상태 미확인 · 확인 필요", status: "unknown" },
  };
  const facts: AnswerFact[] = [
    { label: "청각장애 등록", ...registration[q1] },
  ];
  if (q1 !== "registered") return facts;

  if (q2) {
    const insurance: Record<Q2, Omit<AnswerFact, "label">> = {
      health: { value: "건강보험", status: "neutral" },
      "medical-aid": { value: "의료급여", status: "neutral" },
      unknown: { value: "미확인 · 확인 필요", status: "unknown" },
    };
    facts.push({ label: "보험 자격", ...insurance[q2] });
  }
  if (q3) {
    const prescription: Record<Q3, Omit<AnswerFact, "label">> = {
      yes: { value: "받았어요", status: "done" },
      no: { value: "아직 받지 않음", status: "check-needed" },
      unknown: { value: "발급 여부 미확인 · 확인 필요", status: "unknown" },
    };
    facts.push({ label: "지원금 신청용 처방전", ...prescription[q3] });
  }
  if (q3 === "yes" && q4) {
    const history: Record<Q4, Omit<AnswerFact, "label">> = {
      first: { value: "처음 신청", status: "done" },
      previous: { value: "이전에 지원받음 · 조건 확인 필요", status: "check-needed" },
      unknown: { value: "이전 지원 여부 미확인 · 확인 필요", status: "unknown" },
    };
    facts.push({ label: "지원 이력", ...history[q4] });
  }
  if (q2 === "medical-aid" && q3 === "yes" && q4 && q5) {
    const application: Record<Q5, Omit<AnswerFact, "label">> = {
      approved: { value: "지원 가능 안내 받음", status: "done" },
      waiting: { value: "신청 결과 대기 중", status: "in-progress" },
      "not-applied": { value: "신청 전", status: "check-needed" },
      unknown: { value: "신청 상태 미확인 · 확인 필요", status: "unknown" },
    };
    facts.push({ label: "의료급여 신청", ...application[q5] });
  }
  return facts;
}

function buildCopyText(
  info: ResultInfo,
  facts: AnswerFact[],
): string {
  if (!info.questions || !info.contactQuestion) return "";
  return [
    "IYUM · 센터 방문 전 상담 질문",
    `내 상황: ${info.title}`,
    ...facts.map((f) => `${f.label}: ${f.value}`),
    "",
    `먼저 확인할 곳: ${info.institution}`,
    `문의할 내용: ${info.contactQuestion}`,
    "",
    ...info.questions.flatMap((q, i) => [
      `${i + 1}. ${q.text}`,
      `   확인할 것: ${q.purpose}`,
    ]),
  ].join("\n");
}

/* ─── Step color helpers ─────────────────────────────────────────────────────── */

function isHighlighted(step: JStep) {
  return step.highlight ?? step.status === "current";
}

function stepColors(step: JStep) {
  switch (isHighlighted(step) ? "current" : step.status) {
    case "done":
      return {
        bg: "#ECFDF5", // 연초록 배경
        text: "#065F46", // 진초록 단계명·체크 표시
        badge: "#047857", // 초록색 완료 상태 문구
        border: "var(--iyum-border)",
      };
    case "current":
      return {
        bg: "#FEF3C7",
        text: "#78350F",
        badge: "#92400E",
        border: "#FCD34D",
      };
    case "check-needed":
      return {
        bg: "var(--iyum-mist)",
        text: "var(--iyum-muted)",
        badge: "var(--iyum-muted)",
        border: "var(--iyum-border)",
      };
    case "future":
      return {
        bg: "var(--iyum-surface)",
        text: "var(--iyum-muted)",
        badge: "var(--iyum-muted)",
        border: "var(--iyum-border)",
      };
  }
}

function subscribeJourneyWidth(notify: () => void) {
  const query = window.matchMedia("(min-width: 901px)");
  query.addEventListener("change", notify);
  return () => query.removeEventListener("change", notify);
}
const getJourneyWide = () => window.matchMedia("(min-width: 901px)").matches;

/* ─── GuidedJourney — reading a step never changes the answer-derived location ─ */

function GuidedJourney({
  steps,
  note,
  expandedStep,
  onExpand,
}: {
  steps: JStep[];
  note: string | null;
  expandedStep: number | null;
  onExpand: (step: number | null) => void;
}) {
  const wide = useSyncExternalStore(subscribeJourneyWidth, getJourneyWide, () => false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const expanded = wide || mobileOpen;
  const current = steps.find(step => isHighlighted(step));
  return (
    <section className="p03-journey" aria-labelledby="p03-journey-heading">
      <div className="p03-journey-title"><h3 id="p03-journey-heading">전체 절차와 내 위치</h3>
        {current && <span className="p03-current-location">{current.name} · {current.statusLabel || "먼저 확인"}</span>}
        <button type="button" className="p03-journey-toggle" aria-label={expanded ? "전체 절차 접기" : "전체 절차 펼치기"} aria-expanded={expanded} aria-controls="p03-journey-content" onClick={() => setMobileOpen(value => !value)}>{expanded ? "접기 −" : "펼치기 +"}</button>
      </div><div id="p03-journey-content" hidden={!expanded}>
      <p className="p03-journey-hint">
        단계를 누르면 해당 절차를 볼 수 있어요.
      </p>
      {note && <p className="p03-journey-note">{note}</p>}
      <ol className="p03-journey-steps">
        {steps.map((step, i) => {
          const colors = stepColors(step);
          const expanded = expandedStep === i;
          return (
            <li key={i}>
              <button
                type="button"
                id={`p03-journey-step-${i + 1}`}
                className="p03-journey-step"
                aria-current={step.status === "current" ? "step" : undefined}
                aria-expanded={expanded}
                aria-controls={`p03-journey-detail-${i + 1}`}
                onClick={() => onExpand(expanded ? null : i)}
                style={{ backgroundColor: colors.bg, color: colors.text }}
              >
                <span className="p03-journey-number" aria-hidden="true">
                  {step.status === "done" ? "✓" : `0${i + 1}`}
                </span>
                <span
                  className="p03-journey-name"
                  style={{ fontWeight: isHighlighted(step) ? 700 : 600 }}
                >
                  {step.name}
                </span>
                <span
                  className="p03-journey-status"
                  style={{ color: colors.badge }}
                >
                  {step.statusLabel}
                </span>
                <span className="p03-journey-chevron" aria-hidden="true">
                  {expanded ? "▴" : "▾"}
                </span>
              </button>
            </li>
          );
        })}
      </ol>
      {steps.map((step, i) => (
        <div
          key={i}
          id={`p03-journey-detail-${i + 1}`}
          className="p03-journey-detail"
          style={{ backgroundColor: stepColors(step).bg }}
          role="region"
          aria-labelledby={`p03-journey-detail-heading-${i + 1}`}
          hidden={expandedStep !== i}
        >
          <h4 id={`p03-journey-detail-heading-${i + 1}`}>{step.name} 절차</h4>
          <p className="p03-journey-institution">{step.institution}</p>
          <p>{step.desc}</p>
          {step.detailLines.length > 0 && (
            <ul>
              {step.detailLines.map((line, j) => (
                <li key={j}>{line}</li>
              ))}
            </ul>
          )}
          {step.showDirectClaim && (
            <DirectClaimGuide id="p03-guided-direct-claim" />
          )}
        </div>
      ))}
      </div>
    </section>
  );
}

/* ─── ExpandableJourneySteps ────────────────────────────────────────────────── */

function ExpandableJourneySteps({
  steps,
  idPrefix,
}: {
  steps: JStep[];
  idPrefix: string;
}) {
  return (
    <div
      style={{
        borderTop: "1px solid var(--iyum-border)",
        borderBottom: "1px solid var(--iyum-border)",
        borderLeft: "1px solid var(--iyum-border)",
        borderRight: "1px solid var(--iyum-border)",
        borderRadius: 6,
        overflow: "hidden",
      }}
    >
      {steps.map((step, i) => {
        const c = stepColors(step);
        return (
          <details
            key={i}
            id={`${idPrefix}-step-${i + 1}`}
            open={idPrefix === "p03-reference-journey" && i === 0}
            style={{
              backgroundColor: c.bg,
              borderTop: i > 0 ? "1px solid var(--iyum-border)" : "none",
            }}
          >
            <summary
              aria-current={step.status === "current" ? "step" : undefined}
              style={{
                display: "flex",
                alignItems: "flex-start",
                gap: 10,
                padding: "12px 14px",
                cursor: "pointer",
                listStyle: "none",
                userSelect: "none",
              }}
            >
              <span
                style={{
                  fontSize: 14,
                  fontFamily: "'DM Mono', monospace",
                  color: c.badge,
                  minWidth: 22,
                  flexShrink: 0,
                  marginTop: 2,
                }}
              >
                {step.status === "done" ? "✓" : `0${i + 1}`}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    display: "flex",
                    alignItems: "baseline",
                    flexWrap: "wrap",
                    gap: 6,
                    marginBottom: 2,
                  }}
                >
                  <span
                    style={{
                      fontSize: 14,
                      fontWeight: isHighlighted(step) ? 700 : 600,
                      color: c.text,
                    }}
                  >
                    {step.name}
                  </span>
                  {step.statusLabel && (
                    <span
                      style={{
                        fontSize: 14,
                        color: c.badge,
                        fontWeight: 500,
                      }}
                    >
                      {step.statusLabel}
                    </span>
                  )}
                </div>
                <div style={{ fontSize: 14, color: c.badge, lineHeight: 1.4 }}>
                  {step.institution}
                </div>
              </div>
              <span
                aria-hidden="true"
                style={{
                  fontSize: 14,
                  color: c.badge,
                  flexShrink: 0,
                  marginTop: 2,
                }}
              >
                ▾
              </span>
            </summary>
            <div
              style={{
                padding: "0 14px 14px 46px",
              }}
            >
              <p
                style={{
                  fontSize: 14,
                  color: c.text,
                  lineHeight: 1.6,
                  marginBottom: step.detailLines.length ? 8 : 0,
                }}
              >
                {step.desc}
              </p>
              {step.detailLines.length > 0 && (
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
                  {step.detailLines.map((line, j) => (
                    <li
                      key={j}
                      style={{
                        fontSize: 14,
                        color: c.badge,
                        lineHeight: 1.6,
                        paddingLeft: 10,
                        borderLeft: `2px solid ${c.border}`,
                      }}
                    >
                      {line}
                    </li>
                  ))}
                </ul>
              )}
              {step.showDirectClaim && (
                <DirectClaimGuide id={`${idPrefix}-direct-claim`} />
              )}
            </div>
          </details>
        );
      })}
    </div>
  );
}

/* ─── CopyButton ────────────────────────────────────────────────────────────── */

function CopyButton({ copyText, label = "질문 복사하기" }: { copyText: string; label?: string }) {
  const { status, showFallback, fallbackRef, handleCopy } = useGuidanceClipboard(copyText);

  return (
    <div style={{ marginTop: 16 }}>
      <button
        type="button"
        onClick={handleCopy}
        style={{
          display: "inline-flex",
          alignItems: "center",
          minHeight: 44,
          padding: "10px 18px",
          border: "1.5px solid var(--iyum-ink)",
          borderRadius: 6,
          backgroundColor: "transparent",
          color: "var(--iyum-ink)",
          fontSize: 14,
          fontWeight: 600,
          cursor: "pointer",
          fontFamily: "inherit",
        }}
      >
        {label}
      </button>
      <div
        role="status"
        aria-live="polite"
        style={{ marginTop: 8, fontSize: 13 }}
      >
        {status === "success" && (
          <span style={{ color: "#166534" }}>
            내 상황과 질문을 복사했어요. 휴대폰 메모 등에 붙여 넣어 보관하세요.
          </span>
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
          rows={12}
          style={{
            display: "block",
            width: "100%",
            marginTop: 8,
            fontSize: 14,
            lineHeight: 1.7,
            padding: "10px 12px",
            fontFamily: "inherit",
            border: "1px solid var(--iyum-border)",
            borderRadius: 6,
            backgroundColor: "var(--iyum-surface)",
            resize: "vertical",
            boxSizing: "border-box",
          }}
        />
      )}
    </div>
  );
}

/* ─── PreviousAnswer ────────────────────────────────────────────────────────── */

function PreviousAnswer({
  num,
  questionText,
  selectedLabel,
  onEdit,
}: {
  num: number;
  questionText: string;
  selectedLabel: string;
  onEdit: () => void;
}) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "flex-start",
        gap: 12,
        padding: "10px 14px",
        marginBottom: 8,
        borderTop: "1px solid var(--iyum-border)",
        borderBottom: "1px solid var(--iyum-border)",
        borderLeft: "1px solid var(--iyum-border)",
        borderRight: "1px solid var(--iyum-border)",
        borderRadius: 6,
        backgroundColor: "var(--iyum-mist)",
      }}
    >
      <span
        style={{
          fontSize: 14,
          fontFamily: "'DM Mono', monospace",
          color: "var(--iyum-muted)",
          marginTop: 3,
          flexShrink: 0,
          minWidth: 28,
        }}
      >
        Q{String(num).padStart(2, "0")}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: 14,
            color: "var(--iyum-muted)",
            marginBottom: 1,
            lineHeight: 1.3,
          }}
        >
          {questionText}
        </div>
        <div
          style={{
            fontSize: 14,
            color: "var(--iyum-ink)",
            fontWeight: 500,
            lineHeight: 1.4,
          }}
        >
          {selectedLabel}
        </div>
      </div>
      <button
        type="button"
        onClick={onEdit}
        style={{
          fontSize: 14,
          color: "var(--iyum-primary)",
          background: "none",
          border: "none",
          cursor: "pointer",
          fontFamily: "inherit",
          padding: "2px 0",
          minHeight: 44,
          flexShrink: 0,
          textDecoration: "underline",
          textDecorationStyle: "dotted",
        }}
      >
        변경
      </button>
    </div>
  );
}

/* ─── InsuranceEligibilityHelp — reference only; does not select an answer ──── */

function InsuranceEligibilityHelp() {
  return (
    <details id="p03-insurance-eligibility-help" className="p03-insurance-help">
      <summary>건강보험·의료급여 뜻과 내 자격 확인 방법</summary>
      <div className="p03-insurance-help-content">
        <section aria-labelledby="p03-health-eligibility-heading">
          <h3 id="p03-health-eligibility-heading">건강보험인지 확인하려면</h3>
          <p>
            직장·지역 건강보험 가입자와 가족의 건강보험에 피부양자로 등록된 분이
            해당해요. 본인이 보험료를 직접 내지 않아도 건강보험에 포함될 수
            있어요.
          </p>
          <p>
            <strong>확인 방법</strong>
            <br />
            공단의 ‘자격확인서’ 안내에서 ‘발급하기’를 선택해 확인하세요.
            로그인·본인인증이 필요해요.
          </p>
          <a
            href="https://www.nhis.or.kr/nhis/minwon/minwonServiceBoard.do?articleNo=10945782&mode=view"
            target="_blank"
            rel="noopener noreferrer"
          >
            건강보험 자격확인서 안내 보기 ↗ (새 탭)
          </a>
        </section>

        <section aria-labelledby="p03-medical-aid-eligibility-heading">
          <h3 id="p03-medical-aid-eligibility-heading">의료급여 대상자란?</h3>
          <p>
            생활이 어려운 분 등 법에서 정한 대상자의 의료비를 국가가 지원하는
            제도예요. 건강보험과 별도이며, 의료급여 수급권자로 선정된 분이
            해당해요. 청각장애 등록만으로 자동 적용되지는 않아요.
          </p>
          <p>
            <strong>확인 방법</strong>
            <br />
            주소지 주민센터에 이렇게 물어보세요.
          </p>
          <blockquote>
            “제가 현재 의료급여 수급권자로 등록되어 있나요?”
          </blockquote>
          <a
            href="https://www.bokjiro.go.kr/ssis-tbu/twatca/wlfcl/wlfclPage.do"
            target="_blank"
            rel="noopener noreferrer"
          >
            복지로에서 주민센터 찾기 ↗ (새 탭)
          </a>
          <p className="p03-insurance-help-note">
            ‘주소검색’에서 거주 지역을 설정하고 ‘공공기관 → 주민센터’를
            선택하세요. 찾은 곳이 주소지 담당 주민센터인지 확인하세요.
          </p>
        </section>

        <section aria-labelledby="p03-eligibility-inquiry-heading">
          <h3 id="p03-eligibility-inquiry-heading">
            어디에 해당하는지 여전히 모르겠다면
          </h3>
          <p>공단에 글로 물어볼 수 있어요.</p>
          <blockquote>
            “제가 현재 건강보험과 의료급여 중 어디에 해당하는지 확인하고
            싶어요.”
          </blockquote>
          <a href={NHIS_ONLINE_URL} target="_blank" rel="noopener noreferrer">
            공단에 글로 문의하기 ↗ (로그인 · 새 탭)
          </a>
        </section>
      </div>
    </details>
  );
}

/* ─── QuestionBlock ─────────────────────────────────────────────────────────── */

function QuestionBlock({
  num,
  question,
  desc,
  subtext,
  help,
  options,
  selected,
  onSelect,
}: {
  num: number;
  question: string;
  desc?: string;
  subtext?: string;
  help?: React.ReactNode;
  options: { label: string; value: string }[];
  selected: string | null;
  onSelect: (v: string) => void;
}) {
  return (
    <section className="p03-question" aria-labelledby={`p03-question-${num}`}>
      <div
        style={{
          padding: "18px 20px",
          borderTop: "1.5px solid var(--iyum-ink)",
          borderBottom: "1.5px solid var(--iyum-ink)",
          borderLeft: "1.5px solid var(--iyum-ink)",
          borderRight: "1.5px solid var(--iyum-ink)",
          borderRadius: 6,
        }}
      >
        <p
          style={{
            fontSize: 14,
            color: "var(--iyum-muted)",
            marginBottom: 6,
            fontFamily: "'DM Mono', monospace",
            letterSpacing: "0.04em",
          }}
        >
          질문 {String(num).padStart(2, "0")}
        </p>
        <h2 id={`p03-question-${num}`} tabIndex={-1}
          style={{
            fontSize: 15,
            fontWeight: 700,
            color: "var(--iyum-ink)",
            marginBottom: desc ? 8 : 14,
            lineHeight: 1.4,
            letterSpacing: "-0.01em",
          }}
        >
          {question}
        </h2>
        {desc && (
          <p
            style={{
              fontSize: 14,
              color: "var(--iyum-muted)",
              lineHeight: 1.6,
              marginBottom: 6,
            }}
          >
            {desc}
          </p>
        )}
        {subtext && (
          <p
            style={{
              fontSize: 14,
              color: "var(--iyum-muted)",
              lineHeight: 1.5,
              marginBottom: 14,
            }}
          >
            {subtext}
          </p>
        )}
        {help}
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {options.map((opt) => {
            const isSel = selected === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => onSelect(opt.value)}
                className="p03-option"
                aria-pressed={isSel}
                style={{
                  display: "block",
                  width: "100%",
                  minHeight: 44,
                  padding: "10px 14px",
                  textAlign: "left",
                  fontSize: 14,
                  color: isSel ? "var(--iyum-surface)" : "var(--iyum-ink)",
                  backgroundColor: isSel ? "var(--iyum-ink)" : "var(--iyum-surface)",
                  borderTop: isSel
                    ? "1.5px solid var(--iyum-ink)"
                    : "1px solid var(--iyum-border)",
                  borderBottom: isSel
                    ? "1.5px solid var(--iyum-ink)"
                    : "1px solid var(--iyum-border)",
                  borderLeft: isSel
                    ? "1.5px solid var(--iyum-ink)"
                    : "1px solid var(--iyum-border)",
                  borderRight: isSel
                    ? "1.5px solid var(--iyum-ink)"
                    : "1px solid var(--iyum-border)",
                  borderRadius: 6,
                  cursor: "pointer",
                  fontFamily: "inherit",
                  lineHeight: 1.4,
                  fontWeight: isSel ? 600 : 400,
                }}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/* ─── ResultSection ─────────────────────────────────────────────────────────── */

function ResultSection({
  code,
  info,
  facts,
  headingRef,
  onReset,
  onGoTo,
  overrideQ1,
  registryCheck,
  onEditProduct,
  benefitMode,
  onBenefitModeChange,
  medicalAid,
}: {
  code: ResultCode;
  info: ResultInfo;
  facts: AnswerFact[];
  headingRef: React.RefObject<HTMLHeadingElement | null>;
  onReset: () => void;
  onGoTo: (v: PageView, triggerId?: string, targetId?: string) => void;
  medicalAid: boolean;
  overrideQ1?: string | null;
  registryCheck: React.ReactNode;
  onEditProduct: () => void;
  benefitMode: BenefitMode;
  onBenefitModeChange: (mode: BenefitMode) => void;
}) {
  const effectiveQuestions =
    info.questions && overrideQ1
      ? [
          { text: overrideQ1, purpose: "추천 이유와 비교 대안을 확인해요." },
          ...info.questions.slice(1),
        ]
      : info.questions;
  const effectiveInfo =
    effectiveQuestions !== info.questions
      ? { ...info, questions: effectiveQuestions }
      : info;
  const copyText = buildCopyText(effectiveInfo, facts);
  return (
    <div style={{ marginTop: 28 }}>
      {/* Result heading — receives programmatic focus on first result appearance */}
      <div
        style={{
          borderTop: "2px solid var(--iyum-ink)",
          paddingTop: 24,
          marginBottom: 20,
          scrollMarginTop: 80,
        }}
      >
        <h2
          ref={headingRef}
          id="p03-result-heading"
          tabIndex={-1}
          style={{
            scrollMarginTop: 80,
            fontSize: 20,
            fontWeight: 700,
            color: "var(--iyum-ink)",
            letterSpacing: "-0.02em",
            marginBottom: 8,
          }}
        >
          {info.title}
        </h2>
        <p style={{ fontSize: 14, color: "var(--iyum-muted)", lineHeight: 1.6 }}>
          {info.currentState}
        </p>
        {facts.length > 0 && (
          <section
            className="p03-result-summary"
            aria-labelledby="p03-result-facts-heading"
          >
            <h3 id="p03-result-facts-heading">선택한 답변 요약</h3>
            <dl className="p03-result-facts">
              {facts.map((fact) => (
                <div key={fact.label}>
                  <dt>{fact.label}</dt>
                  <dd>
                    <span className={`p03-fact-status p03-fact-status--${fact.status}`}>
                      {FACT_ICONS[fact.status] && (
                        <span className="p03-fact-icon" aria-hidden="true">
                          {FACT_ICONS[fact.status]}
                        </span>
                      )}
                      <span>{fact.value}</span>
                    </span>
                  </dd>
                </div>
              ))}
            </dl>
          </section>
        )}
      </div>

      <HearingAidActions code={code} info={info} medicalAid={medicalAid}
        onClaim={() => onGoTo("reference", "p03-claim-link", "p03-reference-journey-step-4")}
        renderCopy={text => <CopyButton key={text} copyText={text} label="이 질문 복사하기" />}>
        {(code === "R08" || code === "R18") && effectiveInfo.questions && (
          <details className="p03-extra-questions"><summary>제품·비용 상담 질문 더 보기</summary>
            {overrideQ1 && <p>선택한 제품이 반영됐어요. <button className="iyum-text-link" type="button" id="p03-result-suggested-product" onClick={onEditProduct}>제품 변경 ↓</button></p>}
            <ol>{effectiveInfo.questions.map((question, index) => <li key={index}><p>{question.text}</p><p className="p03-small">확인할 것: {question.purpose}</p></li>)}</ol>
            {copyText && <CopyButton key={copyText} copyText={copyText} label="추가 상담 질문 복사하기" />}
          </details>
        )}
      </HearingAidActions>

      {/* Health detail — R08/R09/R10 only */}
      {info.hasHealthDetail && (
        <div
          style={{
            borderTop: "1px solid var(--iyum-border)",
            paddingTop: 24,
            marginBottom: 32,
          }}
        >
          <h3
            style={{
              fontSize: 15,
              fontWeight: 700,
              color: "var(--iyum-ink)",
              marginBottom: 4,
            }}
          >
            건강보험 지원 금액 안내
          </h3>
          {(code === "R09" || code === "R10") && (
            <p
              style={{
                fontSize: 14,
                color: "#92400E",
                lineHeight: 1.5,
                marginBottom: 10,
                padding: "8px 12px",
                backgroundColor: "#FFFBEB",
                borderRadius: 6,
              }}
            >
              아래는 일반 기준이에요. 이번에 지원받을 수 있는지는 이전 이력을
              확인한 뒤 알 수 있어요.
            </p>
          )}
          <BenefitGuide
            mode={benefitMode}
            onModeChange={onBenefitModeChange}
            idPrefix="p03-result-benefit"
            collapsible
          />
        </div>
      )}

      <section
        className="p03-result-registry"
        aria-labelledby="p03-result-registry-heading"
      >
        <h3 id="p03-result-registry-heading">구입 전 센터·제품 등록 확인</h3>
        {registryCheck}
      </section>

      {/* Navigation to reference/bilateral */}
      <div
        style={{
          borderTop: "1px solid var(--iyum-border)",
          paddingTop: 20,
          marginBottom: 16,
        }}
      >
        <p
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: "var(--iyum-muted)",
            marginBottom: 10,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
          }}
        >
          더 알아보기
        </p>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          <button
            type="button"
            id="p03-result-reference" aria-haspopup="dialog"
            onClick={() => onGoTo("reference", "p03-result-reference")}
            style={{
              minHeight: 44,
              padding: "10px 16px",
              fontSize: 14,
              color: "var(--iyum-ink)",
              backgroundColor: "transparent",
              border: "1px solid var(--iyum-border)",
              borderRadius: 6,
              cursor: "pointer",
              fontFamily: "inherit",
            }}
          >
            전체 기준 보기
          </button>
          <button
            type="button"
            id="p03-result-bilateral" aria-haspopup="dialog"
            onClick={() => onGoTo("bilateral", "p03-result-bilateral")}
            style={{
              minHeight: 44,
              padding: "10px 16px",
              fontSize: 14,
              color: "var(--iyum-ink)",
              backgroundColor: "transparent",
              border: "1px solid var(--iyum-border)",
              borderRadius: 6,
              cursor: "pointer",
              fontFamily: "inherit",
            }}
          >
            아동·청소년 양쪽 지원
          </button>
        </div>
      </div>

      {/* Reset */}
      <div style={{ borderTop: "1px solid var(--iyum-border)", paddingTop: 16 }}>
        <button
          type="button"
          onClick={onReset}
          style={{
            fontSize: 14,
            color: "var(--iyum-muted)",
            background: "none",
            border: "none",
            cursor: "pointer",
            padding: "8px 0",
            fontFamily: "inherit",
            textDecoration: "underline",
            textDecorationStyle: "dotted",
            minHeight: 44,
          }}
        >
          처음부터 확인하기
        </button>
      </div>
    </div>
  );
}

/* ─── Main component ────────────────────────────────────────────────────────── */

const VIEW_HEADING_IDS: Record<PageView, string> = {
  summary: "p03-summary-heading",
  guided: "p03-guided-heading",
  reference: "full-criteria",
  bilateral: "bilateral-heading",
};

function subscribeProductFreshness(notify: () => void) {
  const timer = window.setInterval(notify, 60_000);
  return () => window.clearInterval(timer);
}
function getProductFreshness() {
  return Date.now() >= new Date("2026-09-30T00:00:00+09:00").getTime();
}

export default function HearingAidGuide() {
  const { getApprovalVersion } = useNavigationGuard();
  const [view, setView] = useState<PageView>("guided");
  const [benefitMode, setBenefitMode] =
    useState<BenefitMode>("single");
  const [dialogTriggerId, setDialogTriggerId] = useState<string | undefined>();
  const [q1, setQ1] = useState<Q1 | null>(null);
  const [q2, setQ2] = useState<Q2 | null>(null);
  const [q3, setQ3] = useState<Q3 | null>(null);
  const [q4, setQ4] = useState<Q4 | null>(null);
  const [q5, setQ5] = useState<Q5 | null>(null);
  const [expandedJourneyStep, setExpandedJourneyStep] = useState<number | null>(
    null,
  );
  const [productQuery, setProductQuery] = useState("");
  const [productResults, setProductResults] = useState<Product[]>([]);
  const [productPage, setProductPage] = useState(0);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [productSearched, setProductSearched] = useState(false);

  const guidedHeadingRef = useRef<HTMLHeadingElement>(null);
  const resultHeadingRef = useRef<HTMLHeadingElement>(null);
  const bilateralQsRef = useRef<HTMLHeadingElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  const resultProductDetailsRef = useRef<HTMLDetailsElement>(null);
  const keyboardInteraction = useRef(false);
  const followQuestionRef = useRef(false);
  const pendingNavigationRef = useRef<{ hashId: string } | null>(null);

  function focusReference(id: string) {
    const section = document.getElementById(id);
    if (!section) return;
    let ancestor: HTMLElement | null = section;
    while (ancestor) {
      if (ancestor instanceof HTMLDetailsElement) ancestor.open = true;
      ancestor = ancestor.parentElement;
    }
    if (id === "p03-reference-journey-step-4") {
      const claim = section.querySelector<HTMLDetailsElement>(".p03-direct-claim");
      if (claim) claim.open = true;
    }
    if (id === "steps" || id === "full-criteria") {
      const first = document.getElementById("p03-reference-journey-step-1") as HTMLDetailsElement | null;
      if (first) first.open = true;
    }
    const target = (section.matches("details") ? section.querySelector("summary") : section.matches("h1,h2,h3") ? section : section.querySelector("h1,h2,h3")) as HTMLElement | null;
    const focus = target ?? section;
    // Native summary/button/link tab order must survive programmatic focus.
    focusForReading(focus, keyboardInteraction.current);
    // Scroll only the dialog body: the survey behind it never changes position.
    const scroller = focus.closest<HTMLElement>(".p03-dialog-body");
    if (scroller) {
      scroller.scrollTo({
        top: scroller.scrollTop + focus.getBoundingClientRect().top - scroller.getBoundingClientRect().top - 24,
        behavior: "instant",
      });
    }
  }

  // Legacy query strings are left intact. Recognized reference hashes open the dialog;
  // the common #main-content-start skip target must never become a page route.
  useEffect(() => {
    const navigationVersion = getApprovalVersion();
    const openHash = () => {
      const id = window.location.hash.slice(1);
      const referenceIds = ["full-criteria", "registration-help", "eligibility", "benefit", "steps", "center-product-check", "before-buying", "sources", "already-purchased", "p03-reference-history", "p03-reference-insurance", "p03-reference-journey-step-4", "p03-reference-journey-direct-claim"];
      if (id === "one-or-two" || referenceIds.includes(id)) {
        const target = id === "one-or-two" ? "bilateral-heading" : id === "already-purchased" ? "p03-reference-already-purchased" : id;
        pendingNavigationRef.current = {hashId: target};
        setView(id === "one-or-two" ? "bilateral" : "reference");
        // For a hash change inside the already open content, no React transition is needed.
        const section = document.getElementById(target);
        if (section?.getClientRects().length) focusReference(target);
      }
    };
    const frame = requestAnimationFrame(() => {
      if (navigationVersion === getApprovalVersion()) openHash();
    });
    window.addEventListener("hashchange", openHash);
    return () => {cancelAnimationFrame(frame); window.removeEventListener("hashchange", openHash);};
  }, [getApprovalVersion]);

  const currentStep = getCurrentStep(q1, q2, q3, q4, q5);
  const resultCode =
    currentStep === "done" ? computeResult(q1, q2, q3, q4, q5) : null;
  const lastAnsweredQ = getLastAnsweredQ(q1, q2, q3, q4, q5);
  const topBarSteps = computeTopBarSteps(q1, q2, q3, q4, currentStep, q5);

  const showQ2 = q1 === "registered";
  const showQ3 = q1 === "registered" && q2 !== null;
  const showQ4 = showQ3 && q3 === "yes";
  const showQ5 = showQ4 && q2 === "medical-aid" && q4 !== null;

  const q1Label = Q1_OPTS.find((o) => o.value === q1)?.label ?? "";
  const q2Label = Q2_OPTS.find((o) => o.value === q2)?.label ?? "";
  const q3Label = Q3_OPTS.find((o) => o.value === q3)?.label ?? "";
  const q4Label = Q4_OPTS.find((o) => o.value === q4)?.label ?? "";
  const q5Label = Q5_OPTS.find((o) => o.value === q5)?.label ?? "";

  function followAnsweredJourney(
    nextQ1: Q1 | null,
    nextQ2: Q2 | null,
    nextQ3: Q3 | null,
    nextQ4: Q4 | null,
    nextQ5: Q5 | null,
  ) {
    followQuestionRef.current = true;
    const nextStep = getAnswerJourneyStep(
      nextQ1,
      nextQ2,
      nextQ3,
      nextQ4,
      nextQ5,
    );
    // Only answering follows the journey; reading another step or returning
    // from a reference view must not override the user's open explanation.
    setExpandedJourneyStep((openStep) => (openStep === null ? null : nextStep));
  }

  function handleQ1(v: string) {
    setQ1(v as Q1);
    setQ2(null);
    setQ3(null);
    setQ4(null);
    setQ5(null);
    followAnsweredJourney(v as Q1, null, null, null, null);
  }
  function handleQ2(v: string) {
    setQ2(v as Q2);
    setQ3(null);
    setQ4(null);
    setQ5(null);
    followAnsweredJourney(q1, v as Q2, null, null, null);
  }
  function handleQ3(v: string) {
    setQ3(v as Q3);
    setQ4(null);
    setQ5(null);
    followAnsweredJourney(q1, q2, v as Q3, null, null);
  }
  function handleQ4(v: string) {
    setQ4(v as Q4);
    setQ5(null);
    followAnsweredJourney(q1, q2, q3, v as Q4, null);
  }
  function handleQ5(v: string) {
    setQ5(v as Q5);
    followAnsweredJourney(q1, q2, q3, q4, v as Q5);
  }

  // The dialog opens before this parent layout effect. Open the requested
  // disclosure and focus it; survey state and document scroll stay untouched.
  useLayoutEffect(() => {
    const pending = pendingNavigationRef.current;
    if (!pending || view === "guided") return;
    pendingNavigationRef.current = null;
    focusReference(pending.hashId);
  }, [view]);

  // Only an explicit answer/edit/reset moves to new reading content. No delayed
  // timer remains to steal focus after a later interaction or route transition.
  useLayoutEffect(() => {
    if (!followQuestionRef.current) return;
    followQuestionRef.current = false;
    if (view !== "guided") return;
    const el = currentStep === "done" ? resultHeadingRef.current : document.getElementById(`p03-question-${currentStep.slice(1)}`);
    if (!el) return;
    // Pointer readers keep their current control. Keyboard readers get the new
    // heading as a reading anchor (next Tab continues to the following control).
    if (keyboardInteraction.current) focusForReading(el, true);
    revealForReading(el);
  }, [q1, q2, q3, q4, q5, currentStep, view]);

  function handleReset() {
    followQuestionRef.current = true;
    setExpandedJourneyStep(null);
    setQ1(null);
    setQ2(null);
    setQ3(null);
    setQ4(null);
    setQ5(null);
  }

  function closeReference() {
    pendingNavigationRef.current = null;
    setView("guided");
  }

  function goTo(v: PageView, triggerId?: string, targetId?: string) {
    if (v === "guided") { closeReference(); return; }
    // New bilateral links share the full criteria. The legacy #one-or-two
    // address remains supported. Neither option hides/unmounts the survey.
    const destination = v === "bilateral" ? "reference" : v;
    const target = v === "bilateral" ? "benefit" : targetId ?? (v === "summary" ? "p03-summary-heading" : "steps");
    if (v === "bilateral") setBenefitMode("bilateral");
    if (view === "guided") setDialogTriggerId(triggerId);
    if (destination === view) { focusReference(target); return; }
    pendingNavigationRef.current = {hashId: target};
    setView(destination);
  }

  function handleProductSearch() {
    setSelectedProduct(null);
    if (!productQuery.trim()) {
      setProductSearched(false);
      setProductResults([]);
      return;
    }
    const results = searchProducts(productQuery);
    setProductResults(results);
    setProductPage(0);
    setProductSearched(true);
  }

  const isDataStale = useSyncExternalStore(subscribeProductFreshness, getProductFreshness, () => false);

  const r08ModelQuestion = useMemo(() => {
    if (!selectedProduct || isDataStale) return null;
    return `${selectedProduct.model}을 제게 추천하는 이유와 비교할 수 있는 다른 제품은 무엇인가요?`;
  }, [selectedProduct, isDataStale]);

  const registryCheckProps = {
    metadata: PRODUCTS_META,
    query: productQuery,
    results: productResults,
    page: productPage,
    selectedProduct,
    searched: productSearched,
    isDataStale,
    modelQuestion: resultCode === "R08" ? r08ModelQuestion : null,
    onQueryChange: (value: string) => {
      setProductQuery(value);
      setSelectedProduct(null);
      setProductSearched(false);
      setProductResults([]);
      setProductPage(0);
    },
    onSearch: handleProductSearch,
    onSelect: setSelectedProduct,
    onMore: () => setProductPage((page) => page + 1),
  };

  function editSelectedProduct() {
    const details = resultProductDetailsRef.current;
    if (!details) return;
    details.open = true;
    const summary = details.querySelector("summary");
    if (summary) {
      focusForReading(summary, keyboardInteraction.current);
      revealForReading(summary);
    }
  }

  const inner: React.CSSProperties = { margin: "0 auto" };

  /* Q display helpers — last answered Q stays open with options visible */
  const q1IsOpen = currentStep === "Q1" || lastAnsweredQ === "Q1";
  const q1IsCollapsed = q1 !== null && !q1IsOpen;
  const q2IsOpen = showQ2 && (currentStep === "Q2" || lastAnsweredQ === "Q2");
  const q2IsCollapsed = showQ2 && q2 !== null && !q2IsOpen;
  const q3IsOpen = showQ3 && (currentStep === "Q3" || lastAnsweredQ === "Q3");
  const q3IsCollapsed = showQ3 && q3 !== null && !q3IsOpen;
  const q4IsOpen = showQ4 && (currentStep === "Q4" || lastAnsweredQ === "Q4");
  const q4IsCollapsed = showQ4 && q4 !== null && !q4IsOpen;
  const q5IsOpen = showQ5 && (currentStep === "Q5" || lastAnsweredQ === "Q5");
  const q5IsCollapsed = showQ5 && q5 !== null && !q5IsOpen;

  return (
    <main id="main-content" className="p03-page" ref={mainRef}
      onPointerDownCapture={() => { keyboardInteraction.current = false; }}
      onKeyDownCapture={() => { keyboardInteraction.current = true; }}
      onClickCapture={event => { keyboardInteraction.current = event.detail === 0; }}
    >
      {/* Screen-reader live region for result changes */}
      <div
        role="status"
        aria-live="polite"
        aria-atomic="true"
        style={{
          position: "absolute",
          width: 1,
          height: 1,
          overflow: "hidden",
          clip: "rect(0,0,0,0)",
          whiteSpace: "nowrap",
        }}
      >
        {resultCode
          ? `결과가 업데이트됐어요: ${RESULT_INFO[resultCode].title}`
          : ""}
      </div>

      <div
        className="p03-container"
        style={{ paddingTop: 28, paddingBottom: 64 }}
      >
        {/* Breadcrumb */}
        <div style={{ ...inner, marginBottom: 20 }}>
          <nav aria-label="현재 위치">
            <ol
              style={{
                display: "flex",
                alignItems: "center",
                gap: 6,
                listStyle: "none",
                padding: 0,
                margin: 0,
                fontSize: 14,
                color: "var(--iyum-muted)",
              }}
            >
              <li>
                <Link
                  href="/welfare"
                  style={{ color: "var(--iyum-primary)", textDecoration: "none" }}
                >
                  복지·지원정보
                </Link>
              </li>
              <li aria-hidden="true">›</li>
              <li aria-current="page">보청기 건강보험 지원 안내</li>
            </ol>
          </nav>
        </div>

        <HearingAidReferenceDialog
          open={view !== "guided"}
          titleId={VIEW_HEADING_IDS[view]}
          returnFocusId={dialogTriggerId}
          onClose={closeReference}
          onSectionNavigate={focusReference}
        >
          {/* Approved compact summary, not the former standalone landing page. */}
          <section hidden={view !== "summary"} className="p03-quick-summary">
            <h2 id="p03-summary-heading" tabIndex={-1}>30초 요약</h2>
            <ol>
              <li><strong>전체 흐름</strong><p>등록 확인 → 처방 → 구입·착용 → 검수·청구 → 이후 관리로 이어져요.</p></li>
              <li><strong>센터에서 확인할 것</strong><p>센터에서는 제품 상담과 신청 지원 범위를 물어보세요.</p></li>
            </ol>
            <p className="p03-small">최종 급여 자격을 판정하는 페이지가 아닙니다. 개인별 적용은 공단에서 확인하세요.</p>
            <div className="p03-summary-links">
              <button type="button" id="p03-summary-reference" className="iyum-text-link" onClick={() => goTo("reference")}>전체 기준 보기 →</button>
              <button type="button" id="p03-summary-bilateral" className="iyum-text-link" onClick={() => goTo("bilateral")}>아동·청소년 양쪽 지원 →</button>
            </div>
          </section>

        {/* ── Reference (전체 기준) ── */}
        {(
          <div hidden={view !== "reference"} className="p03-reference" style={inner}>
            <h1
              id="full-criteria"
              tabIndex={-1}
              style={{
                fontSize: 20,
                fontWeight: 700,
                color: "var(--iyum-ink)",
                marginBottom: 8,
                letterSpacing: "-0.02em",
              }}
            >
              전체 절차와 지원 기준
            </h1>
            <p
              style={{
                fontSize: 14,
                color: "var(--iyum-muted)",
                lineHeight: 1.6,
                marginBottom: 28,
              }}
            >
              필요한 기준을 확인한 뒤 닫으면, 답변과 체크를 남겨 둔 설문으로 돌아갈 수 있어요.
            </p>

            {/* 목차 */}
            <nav
              aria-label="전체 기준 목차"
              style={{
                marginBottom: 32,
                padding: "14px 16px",
                border: "1px solid var(--iyum-border)",
                borderRadius: 6,
              }}
            >
              <p
                style={{
                  fontSize: 14,
                  fontWeight: 600,
                  color: "var(--iyum-muted)",
                  marginBottom: 8,
                  letterSpacing: "0.06em",
                  textTransform: "uppercase",
                }}
              >
                목차
              </p>
              <ol
                style={{
                  margin: 0,
                  padding: 0,
                  listStyle: "none",
                  display: "flex",
                  flexDirection: "column",
                  gap: 4,
                }}
              >
                {[
                  ["#steps", "전체 절차"],
                  ["#registration-help", "청각장애 등록"],
                  ["#eligibility", "지원 대상"],
                  ["#benefit", "지원 금액 · 한쪽/양쪽"],
                  ["#center-product-check", "구입 전 센터·제품 등록 확인"],
                  ["#before-buying", "센터 상담 준비"],
                  ["#sources", "공식 출처"],
                ].map(([href, label]) => (
                  <li key={href}>
                    <a
                      href={href}
                      style={{
                        fontSize: 14,
                        color: "var(--iyum-primary)",
                        textDecoration: "underline",
                        textDecorationStyle: "dotted",
                      }}
                    >
                      {label}
                    </a>
                  </li>
                ))}
              </ol>
            </nav>

            {/* 전체 절차: 질문·결과에서 처음 열리는 본문 */}
            <section id="steps">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                  scrollMarginTop: 20,
                }}
              >
                전체 절차
              </h3>
              <ExpandableJourneySteps
                idPrefix="p03-reference-journey"
                steps={BASE.map((b, i) => ({
                  ...b,
                  institution: stepInstitution(i, null),
                  status: "future" as StepStatus,
                  statusLabel: "",
                  detailLines: stepDetails(i, null, null),
                  showDirectClaim: i === 3,
                }))}
              />
              <details
                id="p03-reference-already-purchased"
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
                  이미 구입했는데 등록·처방 절차를 거치지 않았어요
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
                  구입했다고 무조건 지원되거나 지원이 불가능하다고 단정할 수는
                  없어요. 처방일·구입일·장애등록일과 구입 자료를 가지고
                  국민건강보험공단에 적용 가능 여부를 먼저 확인하세요.
                </div>
              </details>
            </section>

            {/* 1. 청각장애 등록 */}
            <section id="registration-help">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                  scrollMarginTop: 20,
                }}
              >
                청각장애 등록
              </h3>
              <p
                style={{
                  fontSize: 14,
                  color: "var(--iyum-muted)",
                  lineHeight: 1.7,
                  marginBottom: 10,
                }}
              >
                병원에서 난청 진단을 받는 것과 청각장애 등록은 달라요. 진단
                자료를 제출하고 심사를 거치는 행정 절차예요.
              </p>
              <p
                style={{
                  fontSize: 14,
                  color: "var(--iyum-muted)",
                  lineHeight: 1.7,
                  marginBottom: 10,
                }}
              >
                처음이면 주소지 주민센터에서 신청 방법과 진단받을 병원을
                안내받으세요. <br /> 이미 진단 자료가 있다면 제출 가능 여부를
                확인하세요.
              </p>
              <div style={{ marginTop: 20, marginBottom: 20 }}>
                <p
                  style={{
                    fontSize: 14,
                    fontWeight: 600,
                    marginBottom: 12,
                  }}
                >
                  청각장애 등록은 이렇게 진행돼요
                </p>

                <ol
                  className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4"
                  style={{
                    listStyle: "none",
                    padding: 0,
                    margin: 0,
                  }}
                >
                  {[
                    {
                      icon: "🏥",
                      title: "검사·진단",
                      institution: "이비인후과",
                    },
                    {
                      icon: "📄",
                      title: "등록 신청·자료 제출",
                      institution: "주소지 주민센터",
                    },
                    {
                      icon: "🔎",
                      title: "장애정도 심사",
                      institution: "국민연금공단",
                    },
                    {
                      icon: "✅",
                      title: "등록 결과 확인",
                      institution: "주민센터",
                    },
                  ].map((step, index) => (
                    <li
                      key={step.title}
                      style={{
                        padding: 16,
                        border: "1px solid #d0cec9",
                        borderRadius: 8,
                        backgroundColor: "#fafaf8",
                      }}
                    >
                      <span
                        aria-hidden="true"
                        style={{
                          display: "block",
                          fontSize: 26,
                          marginBottom: 10,
                        }}
                      >
                        {step.icon}
                      </span>

                      <p
                        style={{
                          fontSize: 14,
                          color: "#666",
                          marginBottom: 4,
                        }}
                      >
                        {index + 1}단계
                      </p>

                      <p
                        style={{
                          fontSize: 14,
                          fontWeight: 600,
                          lineHeight: 1.5,
                          marginBottom: 6,
                        }}
                      >
                        {step.title}
                      </p>

                      <p
                        style={{
                          fontSize: 14,
                          color: "#4a4845",
                          margin: 0,
                        }}
                      >
                        {step.institution}
                      </p>
                    </li>
                  ))}
                </ol>
              </div>
              <p
                style={{
                  fontSize: 14,
                  color: "var(--iyum-muted)",
                  lineHeight: 1.7,
                  padding: "12px 14px",
                  backgroundColor: "var(--iyum-mist)",
                  borderRadius: 6,
                }}
              >
                문의 예:{" "}
                <em>
                  &quot;청각장애 등록을 처음 신청하려고 해요. 어떤 병원 검사와 서류가
                  필요한가요?&quot;
                </em>
              </p>
            </section>

            {/* 2. 지원 대상 */}
            <section id="eligibility">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                  scrollMarginTop: 20,
                }}
              >
                건강보험 보청기 지원 대상
              </h3>
              <div
                style={{
                  fontSize: 14,
                  color: "#4a4845",
                  lineHeight: 1.7,
                }}
              >
                <p style={{ marginBottom: 6 }}>
                  아래 두 조건에 모두 해당해야 해요.
                </p>

                <ul
                  style={{
                    listStyleType: "disc",
                    paddingLeft: 20,
                    margin: "0 0 18px",
                  }}
                >
                  <li>청력장애로 등록된 청각장애인</li>
                  <li>건강보험 가입자 또는 피부양자</li>
                </ul>

                <p style={{ fontWeight: 600, marginBottom: 6 }}>
                  지원받으려면 다음 절차도 필요해요.
                </p>

                <ul
                  style={{
                    listStyleType: "disc",
                    paddingLeft: 20,
                    margin: 0,
                  }}
                >
                  <li style={{ marginBottom: 6 }}>
                    <strong>병원 처방:</strong> 전문의의 보청기 필요 판단과
                    지원금 신청용 처방전
                  </li>
                  <li style={{ marginBottom: 6 }}>
                    <strong>제품 구입:</strong> 급여 등록 제품·판매업소 확인
                  </li>
                  <li>
                    <strong>구입 후:</strong> 검수확인과 지원금 청구
                  </li>
                </ul>
              </div>

              <details
                id="p03-reference-history"
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
                  예전에 지원받았다면 언제 다시 받을 수 있나요?
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
                  보청기의 내구연한은 5년이에요. 지원받은 기기를 사용하는 기간의
                  기준으로, 5년마다 자동 입금된다는 뜻은 아니에요. 새 구입 전
                  공단에 이전 구입·급여 이력과 다시 지원받을 시점을 확인하세요.
                  <div
                    style={{
                      marginTop: 12,
                      marginBottom: 8,
                      padding: "12px 14px",
                      background: "#efeeea",
                      borderRadius: 6,
                    }}
                  >
                    <strong>이렇게 물어보세요</strong>
                    <p style={{ margin: "4px 0 0" }}>
                      “이전에 보청기 건강보험 지원을 받은 기록과 지원받은 날짜,
                      이번 신청 전에 확인할 조건을 알려주세요.”
                    </p>
                  </div>
                  <a
                    href="https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do"
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      minHeight: 44,
                      color: "#1e3a8a",
                      textDecoration: "underline",
                      textUnderlineOffset: 3,
                    }}
                  >
                    이전 지원 기록 문의하기 ↗ (로그인 · 새 탭)
                  </a>
                </div>
              </details>
              <details
                id="p03-reference-insurance"
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
                  의료급여 대상이거나 보험 자격을 모르겠어요
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
                  <div
                    style={{
                      paddingBottom: 20,
                      marginBottom: 20,
                      borderBottom: "1px solid #dedbd5",
                    }}
                  >
                    <h4
                      style={{
                        margin: "0 0 8px",
                        fontSize: 14,
                        fontWeight: 700,
                        color: "#1a1918",
                      }}
                    >
                      의료급여 대상이에요
                    </h4>

                    <p style={{ margin: "0 0 12px" }}>
                      <strong>보청기를 구입하기 전에</strong> 주소지
                      주민센터에서 신청·승인 순서를 확인하세요.
                    </p>

                    <div
                      style={{
                        padding: "12px 14px",
                        marginBottom: 8,
                        background: "#efeeea",
                        borderRadius: 6,
                      }}
                    >
                      <strong>이렇게 물어보세요</strong>
                      <p style={{ margin: "4px 0 0" }}>
                        “의료급여 대상자로 보청기 지원을 신청하려고 해요. 구입
                        전에 처방전 발급과 신청·승인을 어떤 순서로 진행해야
                        하나요?”
                      </p>
                    </div>

                    <a
                      href="https://www.bokjiro.go.kr/ssis-tbu/twatca/wlfcl/wlfclPage.do"
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        minHeight: 44,
                        color: "#1e3a8a",
                        textDecoration: "underline",
                        textUnderlineOffset: 3,
                      }}
                    >
                      복지로에서 주민센터 찾기 ↗ (새 탭)
                    </a>

                    <p style={{ margin: "0", fontSize: 13 }}>
                      ‘주소검색’에서 거주 지역을 설정하고, ‘공공기관 →
                      주민센터’를 선택하세요. 찾은 곳이 주소지 담당 주민센터인지
                      확인하세요.
                    </p>
                  </div>

                  <div>
                    <h4
                      style={{
                        margin: "0 0 8px",
                        fontSize: 14,
                        fontWeight: 700,
                        color: "#1a1918",
                      }}
                    >
                      건강보험인지 의료급여인지 모르겠어요
                    </h4>

                    <p style={{ margin: "0 0 12px" }}>
                      먼저 현재 보험 자격을 확인하세요. 국민건강보험공단에 글로
                      문의할 수 있어요.
                    </p>

                    <div
                      style={{
                        padding: "12px 14px",
                        marginBottom: 8,
                        background: "#efeeea",
                        borderRadius: 6,
                      }}
                    >
                      <strong>이렇게 물어보세요</strong>
                      <p style={{ margin: "4px 0 0" }}>
                        “제가 현재 건강보험과 의료급여 중 어디에 해당하나요?
                        건강보험이라면 차상위 본인부담경감 대상자로 등록되어
                        있는지도 확인해 주세요.”
                      </p>
                    </div>

                    <a
                      href="https://www.nhis.or.kr/nhis/minwon/retrieveCvaplCstInfoColctAgreView.do"
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        minHeight: 44,
                        color: "#1e3a8a",
                        textDecoration: "underline",
                        textUnderlineOffset: 3,
                      }}
                    >
                      공단에 글로 문의하기 ↗ (새 탭)
                    </a>

                    <p style={{ margin: "0", fontSize: 13 }}>
                      로그인이 필요해요. 공단 홈페이지의 ‘국민소통·참여 → 온라인
                      상담문의 → 개인 상담’에서도 찾을 수 있어요.
                    </p>
                  </div>

                  <div
                    style={{
                      marginTop: 20,
                      paddingTop: 16,
                      borderTop: "1px solid #dedbd5",
                    }}
                  >
                    <p style={{ margin: 0 }}>
                      제도나 문의처가 헷갈린다면 채팅으로 물어볼 수 있어요.
                    </p>

                    <a
                      href="https://129.go.kr/counsel/counsel03.do"
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        minHeight: 44,
                        color: "#1e3a8a",
                        textDecoration: "underline",
                        textUnderlineOffset: 3,
                      }}
                    >
                      129 채팅상담 안내 보기 ↗ (새 탭)
                    </a>
                  </div>
                </div>
              </details>
            </section>

            <section id="benefit">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                }}
              >
                지원 금액
              </h3>
              <BenefitGuide
                mode={benefitMode}
                onModeChange={setBenefitMode}
                idPrefix="p03-reference"
              />
            </section>

            {/* 구입 전 센터·제품 등록 확인: 기존 검색 기능 재사용 */}
            <section id="center-product-check">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                  scrollMarginTop: 20,
                }}
              >
                구입 전 센터·제품 등록 확인
              </h3>
              <RegistryCheck
                {...registryCheckProps}
                idPrefix="p03-reference-registry"
              />
            </section>

            {/* 7. 센터 상담 준비 */}
            <section id="before-buying">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                  scrollMarginTop: 20,
                }}
              >
                센터 상담 준비
              </h3>
              <p
                style={{
                  fontSize: 14,
                  color: "var(--iyum-muted)",
                  lineHeight: 1.7,
                  marginBottom: 14,
                }}
              >
                센터 방문 전에 공통으로 물어볼 질문 3개예요.
              </p>
              <ol
                style={{
                  listStyle: "none",
                  padding: 0,
                  margin: 0,
                  display: "flex",
                  flexDirection: "column",
                  gap: 10,
                  marginBottom: 16,
                }}
              >
                {[
                  "공단 등록 판매업소인가요? 추천해 주신 정확한 모델명과 급여 등록 여부도 알려주세요.",
                  "제가 내는 총금액은 얼마인가요? 초기·후기 관리와 수리 비용은 어떻게 나뉘나요?",
                  "청구를 맡길 수 있나요? 제가 직접 병원에 가거나 받아올 서류는 무엇이고, 다음 방문은 언제인가요?",
                ].map((q, i) => (
                  <li
                    key={i}
                    style={{
                      padding: "12px 16px",
                      border: "1px solid var(--iyum-border)",
                      borderRadius: 6,
                      fontSize: 14,
                      color: "var(--iyum-ink)",
                      lineHeight: 1.6,
                    }}
                  >
                    <span style={{ fontWeight: 700, marginRight: 6 }}>
                      {i + 1}.
                    </span>
                    {q}
                  </li>
                ))}
              </ol>
            </section>

            {/* 7. 출처 */}
            <section id="sources">
              <h3
                style={{
                  fontSize: 16,
                  fontWeight: 700,
                  color: "var(--iyum-ink)",
                  marginBottom: 10,
                  scrollMarginTop: 20,
                }}
              >
                공식 출처
              </h3>
              <p
                style={{
                  fontSize: 14,
                  color: "var(--iyum-muted)",
                  lineHeight: 1.5,
                  marginBottom: 12,
                }}
              >
                아래는 이번 안내에 사용한 공식 자료예요. 링크는 새 탭으로
                열려요.
              </p>
              <ul
                style={{
                  listStyle: "none",
                  padding: 0,
                  margin: 0,
                  display: "flex",
                  flexDirection: "column",
                  gap: 8,
                }}
              >
                {[
                  {
                    label: "장애인등록·장애정도 심사 안내 · 보건복지부",
                    href: "https://www.mohw.go.kr/menu.es?mid=a10710010900",
                    date: "2026-09-24 재확인",
                  },
                  {
                    label:
                      "장애인보조기기 보험급여 기준 등 세부사항 · 보건복지부·공단",
                    href: "https://www.nhis.or.kr/lm/lmxsrv/law/lawFullView.do?SEQ=87&SEQ_HISTORY=613408",
                    date: "제2026-56호 · 2026-09-24 재확인",
                  },
                  {
                    label: "국민건강보험법 시행규칙 제26조 · 국가법령정보센터",
                    href: "https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0026&lsiSeq=288087&urlMode=lsScJoRltInfoR",
                    date: "2026-08-11 시행 · 2026-09-24 재확인",
                  },
                  {
                    label: "보청기 급여제품 및 결정가격 고시 · 보건복지부·공단",
                    href: "https://www.nhis.or.kr/lm/lmxsrv/law/lawFullContent.do?SEQ=1619&SEQ_HISTORY=613524",
                    date: "제2026-86호 · 제품 목록 2026-09-23 확인",
                  },
                  {
                    label: "장애인 보조기기 지원 안내 · 법제처 생활법령정보",
                    href: "https://easylaw.go.kr/CSP/CnpClsMain.laf?ccfNo=3&cciNo=4&cnpClsNo=1&csmSeq=1063&popMenu=ov",
                    date: "",
                  },
                  {
                    label: "양쪽 지원: 국민건강보험법 시행규칙 별표 7",
                    href: "https://www.law.go.kr/flDownload.do?bylClsCd=110201&flSeq=162807869&gubun=",
                    date: "2026-09-25 확인 · PDF 새 탭",
                  },
                ].map(({ label, href, date }) => (
                  <li key={href} style={{ fontSize: 14, lineHeight: 1.5 }}>
                    <a
                      href={href}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{ color: "var(--iyum-primary)" }}
                    >
                      {label}
                    </a>
                    {date && (
                      <span
                        style={{
                          color: "var(--iyum-muted)",
                          fontSize: 14,
                          marginLeft: 8,
                        }}
                      >
                        {date}
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </section>

            <div style={{ borderTop: "1px solid var(--iyum-border)", paddingTop: 20 }}>
              <button
                type="button"
                onClick={closeReference}
                style={{
                  fontSize: 14,
                  color: "var(--iyum-primary)",
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  padding: "8px 0",
                  minHeight: 44,
                }}
              >
                닫고 설문으로 돌아가기
              </button>
            </div>
          </div>
        )}

        {/* ── Bilateral (보호자·양쪽 지원) ── */}
        {(
          <div hidden={view !== "bilateral"} style={inner}>
            <h2
              id="bilateral-heading"
              tabIndex={-1}
              style={{
                fontSize: 20,
                fontWeight: 700,
                color: "var(--iyum-ink)",
                marginBottom: 8,
                letterSpacing: "-0.02em",
              }}
            >
              아이의 보청기, 양쪽 모두 지원받을 수 있나요?
            </h2>

            <BilateralEligibility />

            {/* 지금 확인할 일 */}
            <div
              style={{
                padding: "16px 18px",
                backgroundColor: "#FFFBEB",
                borderRadius: 6,
                marginBottom: 24,
                borderTop: "1px solid #FDE68A",
                borderBottom: "1px solid #FDE68A",
                borderLeft: "1px solid #FDE68A",
                borderRight: "1px solid #FDE68A",
              }}
            >
              <p
                style={{
                  fontSize: 14,
                  fontWeight: 700,
                  color: "#78350F",
                  marginBottom: 6,
                }}
              >
                지금 확인할 일
              </p>
              <p
                style={{
                  fontSize: 14,
                  color: "var(--iyum-ink)",
                  lineHeight: 1.6,
                  marginBottom: 8,
                }}
              >
                이비인후과에서 아이가 양쪽 지원 조건에 해당하는지 물어보세요.
                검사 수치를 직접 해석하지 않아도 괜찮아요.
              </p>
              <button
                type="button"
                onClick={() => {
                  const el = bilateralQsRef.current;
                  if (!el) return;
                  focusReference(el.id);
                }}
                style={{
                  fontSize: 14,
                  color: "var(--iyum-primary)",
                  textDecoration: "underline",
                  fontWeight: 600,
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  padding: 0,
                  minHeight: 44,
                  display: "inline-block",
                }}
              >
                병원·센터 질문 보기 ↓
              </button>
            </div>

            {/* 검사 조건 */}
            <BilateralExamConditions idPrefix="p03-bilateral" />

            {/* 금액 예시 */}
            <BilateralAmountExample />

            <BilateralSources idPrefix="p03-bilateral" />

            {/* 질문 */}
            <BilateralQuestions
              idPrefix="bilateral"
              headingRef={bilateralQsRef}
            />

            {/* 별도 사업 안내 */}
            <BilateralOtherSupport />

            <div
              style={{
                borderTop: "1px solid var(--iyum-border)",
                paddingTop: 20,
                marginTop: 24,
              }}
            >
              <button
                type="button"
                onClick={closeReference}
                style={{
                  fontSize: 14,
                  color: "var(--iyum-primary)",
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  fontFamily: "inherit",
                  padding: "8px 0",
                  minHeight: 44,
                }}
              >
                닫고 설문으로 돌아가기
              </button>
            </div>
          </div>
        )}

        </HearingAidReferenceDialog>

        {/* ── Guided: stays visible and mounted behind the modal. ── */}
        {(
          <div className="p03-guided" style={inner}>
            <header className="p03-guide-head"><p className="iyum-eyebrow">보청기 건강보험 지원 안내</p>
              <h1 ref={guidedHeadingRef} id="p03-guided-heading" tabIndex={-1}>내 상황부터 확인해 보세요</h1>
              <p>아는 내용만 선택해 주세요. 답한 질문은 그대로 남고, 필요한 답변을 마치면 아래에서 지금 할 일을 확인할 수 있어요.</p>
            </header>

            {/* One process overview, shared by the questions and result. */}
            <GuidedJourney
              steps={topBarSteps}
              note={resultCode ? getJourneyNote(resultCode) : null}
              expandedStep={expandedJourneyStep}
              onExpand={setExpandedJourneyStep}
            />

            <div className="p03-guide-layout"><div>
            {/* Q1 */}
            {q1IsCollapsed && (
              <PreviousAnswer
                num={1}
                questionText="청각장애 진단·등록은 어디까지 진행하셨나요?"
                selectedLabel={q1Label}
                onEdit={() => {
                  followQuestionRef.current = true;
                  setQ1(null);
                  setQ2(null);
                  setQ3(null);
                  setQ4(null);
                  setQ5(null);
                }}
              />
            )}
            {q1IsOpen && (
              <QuestionBlock
                num={1}
                question="청각장애 진단·등록은 어디까지 진행하셨나요?"
                desc="병원 검사·진단 뒤에는 주민센터에 자료를 내고 심사를 거쳐야 등록돼요."
                subtext="주민센터에서 청각장애 등록 완료 안내를 받았는지 떠올려 보세요. 다른 장애로 등록된 것과는 구분해요."
                options={Q1_OPTS}
                selected={q1}
                onSelect={handleQ1}
              />
            )}

            {/* Q2 */}
            {q2IsCollapsed && (
              <PreviousAnswer
                num={2}
                questionText="건강보험과 의료급여 중 어디에 해당하나요?"
                selectedLabel={q2Label}
                onEdit={() => {
                  followQuestionRef.current = true;
                  setQ2(null);
                  setQ3(null);
                  setQ4(null);
                  setQ5(null);
                }}
              />
            )}
            {q2IsOpen && (
              <QuestionBlock
                num={2}
                question="건강보험과 의료급여 중 어디에 해당하나요?"
                desc="보청기를 사용할 분의 현재 자격으로 선택해 주세요."
                subtext="모르면 ‘잘 모르겠어요’를 선택하고 계속할 수 있어요."
                help={<InsuranceEligibilityHelp />}
                options={Q2_OPTS}
                selected={q2}
                onSelect={handleQ2}
              />
            )}

            {/* Q3 */}
            {q3IsCollapsed && (
              <PreviousAnswer
                num={3}
                questionText="이비인후과에서 지원금 신청용 보청기 처방전을 받았나요?"
                selectedLabel={q3Label}
                onEdit={() => {
                  followQuestionRef.current = true;
                  setQ3(null);
                  setQ4(null);
                  setQ5(null);
                }}
              />
            )}
            {q3IsOpen && (
              <QuestionBlock
                num={3}
                question="이비인후과에서 지원금 신청용 보청기 처방전을 받았나요?"
                desc="장애등록을 위한 진단서나 보청기를 권유받은 것과 달라요. 지원금 신청에 쓰는 공식 서류는 '보조기기 처방전'이에요."
                subtext="서류 이름이 기억나지 않으면 '잘 모르겠어요'를 선택하세요."
                options={Q3_OPTS}
                selected={q3}
                onSelect={handleQ3}
              />
            )}

            {/* Q4 */}
            {q4IsCollapsed && (
              <PreviousAnswer
                num={4}
                questionText="이전에 보청기 지원금을 받은 적이 있나요?"
                selectedLabel={q4Label}
                onEdit={() => {
                  followQuestionRef.current = true;
                  setQ4(null);
                  setQ5(null);
                }}
              />
            )}
            {q4IsOpen && (
              <QuestionBlock
                num={4}
                question="이전에 보청기 지원금을 받은 적이 있나요?"
                desc="처음 신청하는지, 이전 지원 이력을 확인해야 하는지 알아보기 위한 질문이에요."
                subtext={q2 === "medical-aid"
                  ? "예전에 건강보험으로 지원받은 경우도 포함해 주세요. 다음 질문에서 이번 의료급여 신청 상태도 확인해요."
                  : undefined}
                options={Q4_OPTS}
                selected={q4}
                onSelect={handleQ4}
              />
            )}

            {/* Q5 */}
            {q5IsCollapsed && (
              <PreviousAnswer
                num={5}
                questionText="주민센터에서 이번 보청기 지원 신청 결과를 안내받았나요?"
                selectedLabel={q5Label}
                onEdit={() => { followQuestionRef.current = true; setQ5(null); }}
              />
            )}
            {q5IsOpen && (
              <QuestionBlock
                num={5}
                question="주민센터에서 이번 보청기 지원 신청 결과를 안내받았나요?"
                desc="의료급여 수급자 선정 안내가 아니라, 이번 보청기 지원 신청에 대한 결과를 말해요."
                options={Q5_OPTS}
                selected={q5}
                onSelect={handleQ5}
              />
            )}

            {/* Result */}
            {currentStep === "done" && resultCode && (
              <ResultSection
                key={[q1,q2,q3,q4,q5].join("|")}
                medicalAid={q2 === "medical-aid"}
                code={resultCode}
                info={getResultInfo(resultCode, q2, q4, q5)}
                facts={computeFacts(q1, q2, q3, q4, q5)}
                headingRef={resultHeadingRef}
                onReset={handleReset}
                onGoTo={goTo}
                overrideQ1={resultCode === "R08" ? r08ModelQuestion : null}
                registryCheck={
                  <RegistryCheck
                    {...registryCheckProps}
                    idPrefix="p03-result-registry"
                    productDetailsRef={resultProductDetailsRef}
                  />
                }
                onEditProduct={editSelectedProduct}
                benefitMode={benefitMode}
                onBenefitModeChange={setBenefitMode}
              />
            )}
            </div><aside className="p03-sidebar" aria-labelledby="p03-sidebar-heading">
              <h2 id="p03-sidebar-heading">알아두세요</h2>
              <p>최종 급여 자격을 판정하는 페이지가 아닙니다. 개인별 적용은 공단에서 확인해요.</p>
              <p>보호자가 대신 확인한다면, 보청기를 사용할 분의 등록 상태와 보험 자격을 기준으로 답해 주세요.</p>
              <p>답변과 체크는 서버에 저장·전송되지 않으며 새로고침하면 사라져요.</p>
              <button type="button" id="p03-sidebar-summary" aria-haspopup="dialog" className="iyum-text-link" onClick={() => goTo("summary", "p03-sidebar-summary")}>30초 요약</button>
              <button type="button" id="p03-sidebar-reference" aria-haspopup="dialog" className="iyum-text-link" onClick={() => goTo("reference", "p03-sidebar-reference")}>전체 기준 보기 →</button>
              <button type="button" id="p03-sidebar-bilateral" aria-haspopup="dialog" className="iyum-text-link" onClick={() => goTo("bilateral", "p03-sidebar-bilateral")}>아동·청소년 양쪽 지원 →</button>
            </aside></div>
          </div>
        )}
      </div>
    </main>
  );
}
