"use client";

import { useState, useRef, useId } from 'react'
import Link from '@/components/AppLink';
import { useQueryParams } from '@/lib/useQueryParams';

// ─── Constants ────────────────────────────────────────────────────────────────

const SEARCH_AREAS = ['전체', '커뮤니티', '복지·지원정보', '병원·전문기관', '보조기기'] as const
type SearchArea = (typeof SEARCH_AREAS)[number]

const PAGE_SIZE = 5

const FILTER_OPTIONS: Partial<Record<SearchArea, { key: string; label: string; options: string[] }[]>> = {
  '커뮤니티': [
    { key: 'postType', label: '글 유형', options: ['일반', '질문·답변', '경험·후기'] },
    { key: 'topic', label: '주제', options: ['자유·일상', '보청기', '인공와우', '치료·재활', '의사소통', '취업·직장', '복지·생활'] },
  ],
  '복지·지원정보': [
    { key: 'wfStatus', label: '신청 상태', options: ['접수 중', '접수 예정', '종료'] },
    { key: 'region', label: '지역', options: ['서울', '경기', '인천', '부산', '대구', '광주', '전국'] },
  ],
  '병원·전문기관': [
    { key: 'region', label: '지역', options: ['서울', '경기', '인천', '부산', '대구', '광주'] },
    { key: 'instType', label: '기관 유형', options: ['청각언어센터', '이비인후과', '재활의학과', '복지관', '발달센터'] },
  ],
  '보조기기': [
    { key: 'productType', label: '제품 유형', options: ['인공와우 프로세서', '인공와우 주변기기', '보청기', '보조청취기기', '진단기기'] },
    { key: 'maker', label: '제조사', options: ['○○메디컬', '△△테크', '□□사운드', '◇◇바이오'] },
  ],
}

function getDomainFilterKeys(a: SearchArea): string[] {
  switch (a) {
    case '커뮤니티': return ['postType', 'topic']
    case '복지·지원정보': return ['wfStatus', 'region']
    case '병원·전문기관': return ['region', 'instType']
    case '보조기기': return ['productType', 'maker']
    default: return []
  }
}

// ─── Interfaces ───────────────────────────────────────────────────────────────

interface CommunityResult {
  id: number
  type: '일반' | '질문·답변' | '경험·후기'
  topic: string
  questionStatus?: '답변 대기' | '답변 있음' | '해결됨'
  title: string
  snippet: string
  date: string
  comments: number
}

interface WelfareResult {
  id: number
  status: '접수 중' | '접수 예정' | '종료'
  name: string
  target: string
  period: string
  agency?: string
  region?: string
}

interface HospitalResult {
  id: number
  name: string
  type: string
  region: string
  services: string[]
}

interface DeviceResult {
  id: number
  product: string
  maker: string
  category: string
  description: string
}

// ─── Mock data: SCR-001 preview (3 items per domain) ─────────────────────────

const COMMUNITY_RESULTS: CommunityResult[] = [
  {
    id: 101,
    type: '질문·답변',
    topic: '인공와우',
    questionStatus: '답변 있음',
    title: '인공와우 수술 후 재활은 어떻게 진행하셨나요?',
    snippet: '수술 이후 재활 과정과 경험을 나눠봅니다. 청능훈련 기간이나 언어치료 연계 방법이 궁금합니다.',
    date: '2026.08.10',
    comments: 8,
  },
  {
    id: 111,
    type: '경험·후기',
    topic: '인공와우',
    title: '인공와우 수술 6개월 후기 — 생각보다 적응이 빨랐어요',
    snippet: '처음 소리를 들었을 때의 감동부터 일상 복귀까지, 솔직한 경험을 공유합니다.',
    date: '2026.07.28',
    comments: 12,
  },
  {
    id: 102,
    type: '질문·답변',
    topic: '인공와우',
    questionStatus: '답변 대기',
    title: '소아 인공와우 수술 연령 기준이 어떻게 되나요?',
    snippet: '아이 수술을 고려 중인데 적합 연령 기준과 검사 절차가 궁금합니다.',
    date: '2026.08.15',
    comments: 0,
  },
]

const WELFARE_RESULTS: WelfareResult[] = [
  {
    id: 201,
    status: '접수 중',
    name: '인공와우 의료비 지원사업',
    target: '청각장애 1~2급 등록자',
    period: '2026.07.01 ~ 2026.09.30',
    agency: '국립재활원',
    region: '전국',
  },
  {
    id: 202,
    status: '접수 예정',
    name: '인공와우 재활치료 연계 지원',
    target: '수술 후 1년 이내 아동 및 보호자',
    period: '2026.10.01 ~ 2026.11.30',
    agency: '보건복지부',
    region: '전국',
  },
  {
    id: 203,
    status: '종료',
    name: '청각장애인 보조기기 구입비 지원',
    target: '등록 청각장애인',
    period: '2026.04.01 ~ 2026.06.30',
    region: '전국',
  },
]

const HOSPITAL_RESULTS: HospitalResult[] = [
  {
    id: 301,
    name: '서울○○청각언어센터',
    type: '청각언어센터',
    region: '서울',
    services: ['인공와우 수술 전후 평가', '청능재활', '언어치료'],
  },
  {
    id: 303,
    name: '경기△△이비인후과',
    type: '이비인후과',
    region: '경기',
    services: ['청력검사', '인공와우 적합', '보청기 상담'],
  },
  {
    id: 304,
    name: '부산□□재활의학과',
    type: '재활의학과',
    region: '부산',
    services: ['인공와우 재활', '언어 발달 평가'],
  },
]

const DEVICE_RESULTS: DeviceResult[] = [
  {
    id: 401,
    product: '인공와우 프로세서 A형',
    maker: '○○메디컬',
    category: '인공와우 프로세서',
    description: '경도부터 고도 난청까지 적합 가능한 외부 음향 프로세서',
  },
  {
    id: 402,
    product: '인공와우 충전기 세트 B',
    maker: '△△테크',
    category: '인공와우 주변기기',
    description: '인공와우 프로세서 전용 표준 충전 세트 (2구 충전기 포함)',
  },
]

// ─── Mock data: SCR-002 full lists ────────────────────────────────────────────

const COMMUNITY_FULL: CommunityResult[] = [
  // 질문·답변 + 인공와우 (10 items) — forms page 1 (1–5) and page 2 (6–10)
  { id: 101, type: '질문·답변', topic: '인공와우', questionStatus: '답변 있음',  title: '인공와우 수술 후 재활은 어떻게 진행하셨나요?', snippet: '수술 이후 재활 과정과 경험을 나눠봅니다. 청능훈련 기간이나 언어치료 연계 방법이 궁금합니다.', date: '2026.08.10', comments: 8 },
  { id: 102, type: '질문·답변', topic: '인공와우', questionStatus: '답변 대기',  title: '소아 인공와우 수술 연령 기준이 어떻게 되나요?', snippet: '아이 수술을 고려 중인데 적합 연령 기준과 검사 절차가 궁금합니다.', date: '2026.08.15', comments: 0 },
  { id: 103, type: '질문·답변', topic: '인공와우', questionStatus: '해결됨',     title: '인공와우 외부 프로세서 방수 등급은 어느 정도인가요?', snippet: '수영이나 샤워 시 착용 가능한지, 방수 모델별 차이가 궁금합니다.', date: '2026.08.05', comments: 5 },
  { id: 104, type: '질문·답변', topic: '인공와우', questionStatus: '답변 있음',  title: '인공와우 배터리 교체 주기와 비용이 궁금합니다', snippet: '현재 보험 적용 범위와 자기부담금이 어느 정도인지 알려주세요.', date: '2026.07.30', comments: 3 },
  { id: 105, type: '질문·답변', topic: '인공와우', questionStatus: '답변 대기',  title: '편측 인공와우 수술 후 양이청 훈련 경험 있으신 분 계신가요?', snippet: '한쪽만 수술한 후 반대쪽 보청기와 병용하고 계신 분의 경험이 궁금합니다.', date: '2026.08.14', comments: 1 },
  { id: 106, type: '질문·답변', topic: '인공와우', questionStatus: '답변 있음',  title: '인공와우 수술 전 청능평가는 어디서 받나요?', snippet: '수술 적합 여부를 판단하는 청능평가 기관과 검사 절차가 궁금합니다.', date: '2026.08.01', comments: 6 },
  { id: 107, type: '질문·답변', topic: '인공와우', questionStatus: '해결됨',     title: '인공와우 전자기 간섭(EMI) 주의사항이 있나요?', snippet: 'MRI 촬영이나 공항 보안 검색대 통과 시 주의할 점이 있는지 알고 싶습니다.', date: '2026.07.22', comments: 4 },
  { id: 108, type: '질문·답변', topic: '인공와우', questionStatus: '답변 있음',  title: '인공와우 재활치료 기간은 보통 얼마나 되나요?', snippet: '성인과 아동의 재활 기간 차이가 있는지, 언어치료와 청능치료 병행 여부가 궁금합니다.', date: '2026.07.18', comments: 9 },
  { id: 109, type: '질문·답변', topic: '인공와우', questionStatus: '답변 대기',  title: '인공와우 수술 후 학교 복귀 시 주의사항이 있나요?', snippet: '초등학생 자녀의 수술 후 학교 생활 적응 관련 경험을 나눠 주세요.', date: '2026.08.13', comments: 2 },
  { id: 110, type: '질문·답변', topic: '인공와우', questionStatus: '답변 있음',  title: '인공와우 기종 선택 기준이 무엇인가요?', snippet: '국내에서 사용 가능한 기종 간 차이점과 선택 기준이 궁금합니다.', date: '2026.08.07', comments: 7 },
  // 경험·후기 + 인공와우
  { id: 111, type: '경험·후기', topic: '인공와우', title: '인공와우 수술 6개월 후기 — 생각보다 적응이 빨랐어요', snippet: '처음 소리를 들었을 때의 감동부터 일상 복귀까지, 솔직한 경험을 공유합니다.', date: '2026.07.28', comments: 12 },
  { id: 112, type: '경험·후기', topic: '인공와우', title: '인공와우 착용 3년차 — 음악 감상은 이렇게 달라졌어요', snippet: '음악의 질감이 처음과 얼마나 달라졌는지, 좋아하는 장르와 함께 이야기합니다.', date: '2026.07.10', comments: 17 },
  // 일반 + 보청기
  { id: 113, type: '일반', topic: '보청기', title: '보청기 선택 전 고려해야 할 사항 정리', snippet: '처음 보청기를 고려하는 분들을 위해 기본 정보를 공유합니다.', date: '2026.08.09', comments: 4 },
  // 질문·답변 + 보청기
  { id: 114, type: '질문·답변', topic: '보청기', questionStatus: '답변 있음', title: '보청기 보험 적용 범위가 어떻게 되나요?', snippet: '보청기 구입 시 건강보험이나 장애인 지원을 받을 수 있는지 궁금합니다.', date: '2026.08.11', comments: 3 },
]

const WELFARE_FULL: WelfareResult[] = [
  { id: 201, status: '접수 중',   name: '인공와우 의료비 지원사업',          target: '청각장애 1~2급 등록자',           period: '2026.07.01 ~ 2026.09.30', agency: '국립재활원',       region: '전국' },
  { id: 202, status: '접수 예정', name: '인공와우 재활치료 연계 지원',        target: '수술 후 1년 이내 아동 및 보호자',  period: '2026.10.01 ~ 2026.11.30', agency: '보건복지부',       region: '전국' },
  { id: 203, status: '종료',      name: '청각장애인 보조기기 구입비 지원',    target: '등록 청각장애인',                  period: '2026.04.01 ~ 2026.06.30',                             region: '전국' },
  { id: 204, status: '접수 중',   name: '서울시 청각장애인 재활치료비 지원',  target: '서울시 거주 청각장애 아동',        period: '2026.06.01 ~ 2026.08.31', agency: '서울특별시',       region: '서울' },
  { id: 205, status: '접수 예정', name: '경기도 보청기 지원사업',             target: '경기도 거주 등록 청각장애인',      period: '2026.09.01 ~ 2026.10.31', agency: '경기도청',         region: '경기' },
  { id: 206, status: '접수 중',   name: '인공와우 언어재활 치료비 지원',      target: '만 12세 이하 인공와우 착용 아동', period: '2026.05.01 ~ 2026.10.31', agency: '보건복지부',       region: '전국' },
  { id: 207, status: '종료',      name: '부산시 장애인 보조기기 수리비 지원', target: '부산시 거주 등록 장애인',          period: '2026.01.01 ~ 2026.03.31', agency: '부산광역시',       region: '부산' },
  { id: 208, status: '접수 중',   name: '장애인 정보통신 보조기기 보급사업',  target: '등록 청각장애인',                  period: '2026.07.15 ~ 2026.09.15', agency: '한국정보화진흥원', region: '전국' },
]

const HOSPITAL_FULL: HospitalResult[] = [
  { id: 301, name: '서울○○청각언어센터',         type: '청각언어센터', region: '서울', services: ['인공와우 수술 전후 평가', '청능재활', '언어치료'] },
  { id: 302, name: '서울△△대학병원 이비인후과',  type: '이비인후과',   region: '서울', services: ['인공와우 수술', '청각장애 진단', '청각재활'] },
  { id: 303, name: '경기△△이비인후과',           type: '이비인후과',   region: '경기', services: ['청력검사', '인공와우 적합', '보청기 상담'] },
  { id: 304, name: '부산□□재활의학과',            type: '재활의학과',   region: '부산', services: ['인공와우 재활', '언어 발달 평가'] },
  { id: 305, name: '인천◇◇청각발달센터',          type: '발달센터',     region: '인천', services: ['소아 청각 발달 평가', '청능훈련', '부모 교육'] },
  { id: 306, name: '대구○○복지관 청각지원팀',     type: '복지관',       region: '대구', services: ['보청기 적합', '청능훈련', '수어 통역 연계'] },
  { id: 307, name: '광주△△청각언어재활원',        type: '청각언어센터', region: '광주', services: ['인공와우 후 언어재활', '청력 평가', '청각보조기기 상담'] },
]

const DEVICE_FULL: DeviceResult[] = [
  { id: 401, product: '인공와우 프로세서 A형',      maker: '○○메디컬', category: '인공와우 프로세서',   description: '경도부터 고도 난청까지 적합 가능한 외부 음향 프로세서' },
  { id: 402, product: '인공와우 충전기 세트 B',      maker: '△△테크',   category: '인공와우 주변기기',   description: '인공와우 프로세서 전용 표준 충전 세트 (2구 충전기 포함)' },
  { id: 403, product: '인공와우 프로세서 C형 (방수)', maker: '○○메디컬', category: '인공와우 프로세서',   description: 'IP68 방수 등급 외부 음향 프로세서, 수영 활동 지원' },
  { id: 404, product: '보청기 D-Pro 시리즈',         maker: '□□사운드', category: '보청기',               description: '귀걸이형 디지털 보청기, 블루투스 연동 지원' },
  { id: 405, product: '청각보조기기 E 리시버',        maker: '◇◇바이오', category: '보조청취기기',         description: '강의실·회의 환경에서 사용하는 개인용 음성 수신기' },
  { id: 406, product: '휴대용 청력 선별 검사기 F',   maker: '△△테크',   category: '진단기기',             description: '기본 청력 선별 검사를 위한 휴대형 검사 장치' },
]

// ─── Shared badge components ──────────────────────────────────────────────────

const qStatusColor: Record<string, { color: string; border: string }> = {
  '답변 대기': { color: '#92400E', border: '#92400E' },
  '답변 있음': { color: '#1E3A8A', border: '#1E3A8A' },
  '해결됨':   { color: '#166534', border: '#166534' },
}

function QuestionStatusBadge({ status }: { status: string }) {
  const s = qStatusColor[status] ?? qStatusColor['답변 대기']
  return (
    <span
      style={{ fontSize: 11, fontWeight: 500, padding: '1px 6px', border: `1px solid ${s.border}`, color: s.color, borderRadius: 2 }}
      aria-label={`질문 상태: ${status}`}
    >
      {status}
    </span>
  )
}

const welfareStatusStyle: Record<WelfareResult['status'], { color: string; border: string }> = {
  '접수 중':   { color: '#166534', border: '#166534' },
  '접수 예정': { color: '#1E3A8A', border: '#1E3A8A' },
  '종료':      { color: '#908D88', border: '#D0CEC9' },
}

function WelfareStatusBadge({ status }: { status: WelfareResult['status'] }) {
  const s = welfareStatusStyle[status]
  return (
    <span
      style={{
        fontSize: 11, fontWeight: 500, padding: '1px 6px',
        border: `1px solid ${s.border}`, color: s.color,
        borderRadius: 2, display: 'inline-block', marginBottom: 6,
      }}
    >
      {status}
    </span>
  )
}

// ─── SCR-001 shared components ────────────────────────────────────────────────

function DomainSection({
  title, moreLabel, moreHref, children,
}: {
  title: string; moreLabel: string; moreHref: string; children: React.ReactNode
}) {
  return (
    <section aria-labelledby={`section-${title}`} style={{ paddingTop: 36, paddingBottom: 4 }}>
      <h2
        id={`section-${title}`}
        style={{
          fontSize: 14, fontWeight: 700, color: '#1A1918',
          marginBottom: 16, paddingBottom: 10,
          borderBottom: '2px solid #1A1918',
          display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
        }}
      >
        {title}
      </h2>
      <ol style={{ listStyle: 'none', padding: 0, margin: 0 }}>{children}</ol>
      <div style={{ paddingTop: 14, paddingBottom: 20, borderTop: '1px solid #E8E6E1' }}>
        <Link
          href={moreHref}
          style={{ fontSize: 13, color: '#1E3A8A', textDecoration: 'none', fontWeight: 500 }}
          onMouseEnter={(e) => { e.currentTarget.style.textDecoration = 'underline' }}
          onMouseLeave={(e) => { e.currentTarget.style.textDecoration = 'none' }}
        >
          {moreLabel} →
        </Link>
      </div>
    </section>
  )
}

// ─── Result item components ───────────────────────────────────────────────────

function CommunityResultItem({ item }: { item: CommunityResult }) {
  return (
    <li style={{ borderBottom: '1px solid #E8E6E1', padding: '14px 0' }}>
      <div className="flex items-center gap-1.5" style={{ marginBottom: 5 }}>
        <span style={{ fontSize: 12, fontWeight: 500, color: '#1E3A8A' }}>{item.type}</span>
        <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
        <span style={{ fontSize: 12, color: '#908D88' }}>{item.topic}</span>
        {item.type === '질문·답변' && item.questionStatus && (
          <>
            <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
            <QuestionStatusBadge status={item.questionStatus} />
          </>
        )}
      </div>
      <Link
        href={`/community/posts/${item.id}`}
        style={{ display: 'block', fontSize: 15, fontWeight: 500, color: '#1A1918', textDecoration: 'none', marginBottom: 4, lineHeight: 1.4 }}
        onMouseEnter={(e) => { e.currentTarget.style.color = '#1E3A8A'; e.currentTarget.style.textDecoration = 'underline' }}
        onMouseLeave={(e) => { e.currentTarget.style.color = '#1A1918'; e.currentTarget.style.textDecoration = 'none' }}
      >
        {item.title}
      </Link>
      <p style={{ fontSize: 13, color: '#4A4845', lineHeight: 1.55, margin: 0, marginBottom: 6 }}>{item.snippet}</p>
      <div className="flex items-center gap-1.5">
        <time style={{ fontSize: 12, color: '#908D88' }} dateTime={item.date.replace(/\./g, '-')}>{item.date}</time>
        <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
        <span style={{ fontSize: 12, color: '#908D88' }}>댓글 {item.comments}</span>
      </div>
    </li>
  )
}

function WelfareResultItem({ item }: { item: WelfareResult }) {
  return (
    <li style={{ borderBottom: '1px solid #E8E6E1', padding: '14px 0' }}>
      <WelfareStatusBadge status={item.status} />
      <Link
        href={`/welfare/${item.id}`}
        style={{ display: 'block', fontSize: 15, fontWeight: 500, color: '#1A1918', textDecoration: 'none', marginBottom: 5, lineHeight: 1.4 }}
        onMouseEnter={(e) => { e.currentTarget.style.color = '#1E3A8A'; e.currentTarget.style.textDecoration = 'underline' }}
        onMouseLeave={(e) => { e.currentTarget.style.color = '#1A1918'; e.currentTarget.style.textDecoration = 'none' }}
      >
        {item.name}
      </Link>
      <p style={{ fontSize: 13, color: '#4A4845', margin: 0, marginBottom: 3 }}>지원 대상: {item.target}</p>
      <p style={{ fontSize: 13, color: '#4A4845', margin: 0, marginBottom: item.agency || item.region ? 3 : 0 }}>
        신청 기간: {item.period}
      </p>
      {(item.agency || item.region) && (
        <p style={{ fontSize: 12, color: '#908D88', margin: 0 }}>
          {[item.region, item.agency].filter(Boolean).join(' · ')}
        </p>
      )}
    </li>
  )
}

function HospitalResultItem({ item }: { item: HospitalResult }) {
  return (
    <li style={{ borderBottom: '1px solid #E8E6E1', padding: '14px 0' }}>
      <Link
        href={`/hospitals/${item.id}`}
        style={{ display: 'block', fontSize: 15, fontWeight: 500, color: '#1A1918', textDecoration: 'none', marginBottom: 4, lineHeight: 1.4 }}
        onMouseEnter={(e) => { e.currentTarget.style.color = '#1E3A8A'; e.currentTarget.style.textDecoration = 'underline' }}
        onMouseLeave={(e) => { e.currentTarget.style.color = '#1A1918'; e.currentTarget.style.textDecoration = 'none' }}
      >
        {item.name}
      </Link>
      <p style={{ fontSize: 13, color: '#908D88', margin: 0, marginBottom: 5 }}>{item.type} · {item.region}</p>
      <div className="flex items-center" style={{ flexWrap: 'wrap', gap: '4px 0' }}>
        {item.services.map((s, i) => (
          <span key={s} style={{ fontSize: 12, color: '#4A4845' }}>
            {s}{i < item.services.length - 1 && <span style={{ margin: '0 5px', color: '#D0CEC9' }}>·</span>}
          </span>
        ))}
      </div>
    </li>
  )
}

function DeviceResultItem({ item }: { item: DeviceResult }) {
  return (
    <li style={{ borderBottom: '1px solid #E8E6E1', padding: '14px 0' }}>
      <Link
        href={`/devices/${item.id}`}
        style={{ display: 'block', fontSize: 15, fontWeight: 500, color: '#1A1918', textDecoration: 'none', marginBottom: 4, lineHeight: 1.4 }}
        onMouseEnter={(e) => { e.currentTarget.style.color = '#1E3A8A'; e.currentTarget.style.textDecoration = 'underline' }}
        onMouseLeave={(e) => { e.currentTarget.style.color = '#1A1918'; e.currentTarget.style.textDecoration = 'none' }}
      >
        {item.product}
      </Link>
      <p style={{ fontSize: 13, color: '#908D88', margin: 0, marginBottom: 4 }}>{item.maker} · {item.category}</p>
      <p style={{ fontSize: 13, color: '#4A4845', margin: 0 }}>{item.description}</p>
    </li>
  )
}

// ─── SCR-002 sub-components ───────────────────────────────────────────────────

function FilterSelect({
  id, label, value, options, onChange, onReset,
}: {
  id: string; label: string; value: string
  options: string[]; onChange: (v: string) => void; onReset: () => void
}) {
  const isActive = value !== ''
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <label htmlFor={id} style={{ fontSize: 12, color: '#4A4845', whiteSpace: 'nowrap', fontWeight: isActive ? 600 : 400 }}>
        {label}
      </label>
      <select
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label={`${label} 필터`}
        style={{
          fontSize: 13,
          color: '#1A1918',
          borderTop: `1px solid ${isActive ? '#1A1918' : '#D0CEC9'}`,
          borderBottom: `1px solid ${isActive ? '#1A1918' : '#D0CEC9'}`,
          borderLeft: `1px solid ${isActive ? '#1A1918' : '#D0CEC9'}`,
          borderRight: `1px solid ${isActive ? '#1A1918' : '#D0CEC9'}`,
          borderRadius: 2,
          padding: '4px 8px',
          backgroundColor: isActive ? '#F0EDE8' : '#FAFAF8',
          cursor: 'pointer',
          fontFamily: 'inherit',
          outline: 'none',
        }}
      >
        <option value="">전체</option>
        {options.map((o) => <option key={o} value={o}>{o}</option>)}
      </select>
      {isActive && (
        <button
          type="button"
          onClick={onReset}
          aria-label={`${label} 필터 초기화`}
          style={{
            fontSize: 11, color: '#908D88', background: 'none', border: 'none',
            cursor: 'pointer', padding: '2px 0', textDecoration: 'underline', fontFamily: 'inherit',
          }}
        >
          초기화
        </button>
      )}
    </div>
  )
}

function Pagination({
  currentPage, totalPages, onPageChange,
}: { currentPage: number; totalPages: number; onPageChange: (p: number) => void }) {
  if (totalPages <= 1) return null
  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)
  const btnBase: React.CSSProperties = {
    minWidth: 32, height: 32, border: '1px solid #E8E6E1',
    backgroundColor: 'transparent', color: '#1A1918',
    borderRadius: 2, cursor: 'pointer', fontFamily: 'inherit',
    fontSize: 13, padding: '0 8px', lineHeight: '30px',
  }
  return (
    <nav aria-label="페이지 이동" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 4, paddingTop: 36, paddingBottom: 16 }}>
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
        aria-label="이전 페이지"
        style={{ ...btnBase, color: currentPage === 1 ? '#D0CEC9' : '#1A1918', cursor: currentPage === 1 ? 'default' : 'pointer' }}
      >
        ←
      </button>
      {pages.map((p) => (
        <button
          key={p}
          onClick={() => onPageChange(p)}
          aria-current={p === currentPage ? 'page' : undefined}
          style={{
            ...btnBase,
            border: p === currentPage ? '1.5px solid #1A1918' : '1px solid #E8E6E1',
            backgroundColor: p === currentPage ? '#1A1918' : 'transparent',
            color: p === currentPage ? '#FAFAF8' : '#1A1918',
            fontWeight: p === currentPage ? 700 : 400,
          }}
        >
          {p}
        </button>
      ))}
      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
        aria-label="다음 페이지"
        style={{ ...btnBase, color: currentPage === totalPages ? '#D0CEC9' : '#1A1918', cursor: currentPage === totalPages ? 'default' : 'pointer' }}
      >
        →
      </button>
    </nav>
  )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

export default function SearchResults() {
  const [searchParams, setSearchParams] = useQueryParams()
  const uid = useId()

  const query    = searchParams.get('q')    ?? ''
  const area     = (searchParams.get('area') ?? '전체') as SearchArea
  const pageNum  = Math.max(1, parseInt(searchParams.get('page') ?? '1', 10))
  const simState = searchParams.get('simState') ?? ''

  const [inputState, setInputState] = useState({query, value: query});
  const inputValue = inputState.query === query ? inputState.value : query;
  const setInputValue = (value: string) => setInputState({query, value});
  const [inputError, setInputError] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)


  // ── Search handlers ────────────────────────────────────────────────────────

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    if (!inputValue.trim()) {
      setInputError('검색어를 입력해 주세요.')
      inputRef.current?.focus()
      return
    }
    setInputError('')
    // Query change: preserve area, reset domain filters + page
    setSearchParams({ q: inputValue.trim(), area })
  }

  function handleClear() {
    setInputValue('')
    setInputError('')
    inputRef.current?.focus()
  }

  function handleAreaChange(newArea: SearchArea) {
    // Area change: preserve query, drop all domain filters + page
    setSearchParams({ q: query, area: newArea })
  }

  // ── SCR-002 filter handlers ────────────────────────────────────────────────

  function buildFilterParams(overrides: Record<string, string> = {}) {
    const filterKeys = getDomainFilterKeys(area)
    const params: Record<string, string> = { q: query, area }
    filterKeys.forEach((k) => {
      const v = overrides[k] ?? searchParams.get(k) ?? ''
      if (v) params[k] = v
    })
    return params
  }

  function handleFilterChange(key: string, value: string) {
    const params = buildFilterParams({ [key]: value })
    if (!value) delete params[key]
    params.page = '1'
    setSearchParams(params)
  }

  function handleResetAll() {
    setSearchParams({ q: query, area, page: '1' })
  }

  function handleRetry() {
    // Simulate successful recovery: mark as retried so results render
    const params = buildFilterParams()
    params.simState = 'retried'
    params.page = '1'
    setSearchParams(params)
  }

  function handlePageChange(p: number) {
    const params = buildFilterParams()
    params.page = String(p)
    setSearchParams(params)
  }

  // ── SCR-002 data ───────────────────────────────────────────────────────────

  const filterKeys = getDomainFilterKeys(area)
  const hasActiveFilters = filterKeys.some((k) => !!searchParams.get(k))

  function getDomainResults() {
    switch (area) {
      case '커뮤니티': {
        const postTypeFilter = searchParams.get('postType') ?? ''
        const topicFilter    = searchParams.get('topic')    ?? ''
        let r = COMMUNITY_FULL
        if (postTypeFilter) r = r.filter((x) => x.type  === postTypeFilter)
        if (topicFilter)    r = r.filter((x) => x.topic === topicFilter)
        return r
      }
      case '복지·지원정보': {
        const wfStatusFilter = searchParams.get('wfStatus') ?? ''
        const regionFilter   = searchParams.get('region')   ?? ''
        let r = WELFARE_FULL
        if (wfStatusFilter) r = r.filter((x) => x.status === wfStatusFilter)
        if (regionFilter) {
          r = r.filter((x) =>
            x.region === regionFilter ||
            (regionFilter !== '전국' && x.region === '전국')
          )
        }
        return r
      }
      case '병원·전문기관': {
        const regionFilter   = searchParams.get('region')   ?? ''
        const instTypeFilter = searchParams.get('instType') ?? ''
        let r = HOSPITAL_FULL
        if (regionFilter)   r = r.filter((x) => x.region === regionFilter)
        if (instTypeFilter) r = r.filter((x) => x.type   === instTypeFilter)
        return r
      }
      case '보조기기': {
        const productTypeFilter = searchParams.get('productType') ?? ''
        const makerFilter       = searchParams.get('maker')       ?? ''
        let r = DEVICE_FULL
        if (productTypeFilter) r = r.filter((x) => x.category === productTypeFilter)
        if (makerFilter)       r = r.filter((x) => x.maker    === makerFilter)
        return r
      }
      default: return []
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const allDomainResults: any[] = getDomainResults()
  const totalResults = allDomainResults.length
  const totalPages   = Math.max(1, Math.ceil(totalResults / PAGE_SIZE))
  const safePage     = Math.min(pageNum, totalPages)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const pageItems: any[] = allDomainResults.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  // ── Derived ────────────────────────────────────────────────────────────────

  const moreUrl = (domainArea: string) =>
    `/search?q=${encodeURIComponent(query)}&area=${encodeURIComponent(domainArea)}`

  const hasQuery  = query.trim().length > 0
  const showAll   = area === '전체'
  const isMockError    = query === '검색오류테스트' && simState !== 'retried'
  const isError        = isMockError
  const isMockNoResult = query === '존재하지않는검색어12345'
  const isNoResult     = !isError && hasQuery && !showAll && (allDomainResults.length === 0 || isMockNoResult)

  // Active filter summary text for AND clarity
  const filterSummary = (() => {
    const opts = FILTER_OPTIONS[area] ?? []
    const parts = opts
      .map((f) => {
        const v = searchParams.get(f.key)
        return v ? `${f.label}: ${v}` : null
      })
      .filter(Boolean)
    return parts.length > 1 ? parts.join('  AND  ') : parts[0] ?? ''
  })()

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <main id="main-content">
      <div style={{ maxWidth: 800, margin: '0 auto', padding: '36px 24px 80px' }}>

        {/* ── SEARCH FIELD ─────────────────────────────────────────── */}
        <form role="search" aria-label="통합 검색" onSubmit={handleSearch} style={{ marginBottom: 28 }}>
          <label
            htmlFor={`${uid}-q`}
            style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#4A4845', marginBottom: 6 }}
          >
            검색어
          </label>
          <div style={{ display: 'flex', alignItems: 'stretch', maxWidth: 600 }}>
            <div style={{ position: 'relative', flex: 1 }}>
              <input
                ref={inputRef}
                id={`${uid}-q`}
                type="search"
                value={inputValue}
                onChange={(e) => {
                  setInputValue(e.target.value)
                  if (inputError) setInputError('')
                }}
                placeholder="검색어를 입력하세요."
                aria-describedby={inputError ? `${uid}-q-err` : undefined}
                style={{
                  width: '100%',
                  borderTop:    inputError ? '1.5px solid #B91C1C' : '1.5px solid #1A1918',
                  borderBottom: inputError ? '1.5px solid #B91C1C' : '1.5px solid #1A1918',
                  borderLeft:   inputError ? '1.5px solid #B91C1C' : '1.5px solid #1A1918',
                  borderRight: 'none',
                  borderRadius: '2px 0 0 2px',
                  padding: '10px 36px 10px 12px',
                  fontSize: 14,
                  backgroundColor: '#FAFAF8',
                  color: '#1A1918',
                  outline: 'none',
                  boxSizing: 'border-box',
                  fontFamily: 'inherit',
                }}
              />
              {inputValue && (
                <button
                  type="button"
                  onClick={handleClear}
                  aria-label="검색어 지우기"
                  style={{
                    position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)',
                    background: 'none', border: 'none', cursor: 'pointer',
                    fontSize: 16, color: '#908D88', padding: 2, lineHeight: 1,
                  }}
                >
                  ×
                </button>
              )}
            </div>
            <button
              type="submit"
              style={{
                backgroundColor: '#1A1918', color: '#FAFAF8',
                border: '1.5px solid #1A1918',
                borderRadius: '0 2px 2px 0',
                padding: '10px 20px', fontSize: 14, fontWeight: 600,
                cursor: 'pointer', whiteSpace: 'nowrap', fontFamily: 'inherit',
              }}
            >
              검색
            </button>
          </div>
          {inputError && (
            <p id={`${uid}-q-err`} role="alert" style={{ fontSize: 12, color: '#B91C1C', marginTop: 6 }}>
              {inputError}
            </p>
          )}
        </form>

        {/* ── H1 ───────────────────────────────────────────────────── */}
        {hasQuery && (
          <h1 style={{ fontSize: 20, fontWeight: 700, color: '#1A1918', marginBottom: 20 }}>
            <span style={{ fontWeight: 400, color: '#4A4845' }}>&quot;</span>
            {query}
            <span style={{ fontWeight: 400, color: '#4A4845' }}>&quot;</span>
            {' '}검색 결과
          </h1>
        )}

        {/* ── SEARCH AREA TABS ──────────────────────────────────────── */}
        <div
          role="group"
          aria-label="검색 영역"
          style={{ display: 'flex', gap: 0, borderBottom: '1px solid #D0CEC9', marginBottom: 0 }}
        >
          {SEARCH_AREAS.map((a) => {
            const selected = area === a
            return (
              <button
                key={a}
                type="button"
                aria-pressed={selected}
                onClick={() => handleAreaChange(a)}
                style={{
                  fontSize: 13,
                  fontWeight: selected ? 700 : 400,
                  color: selected ? '#1A1918' : '#908D88',
                  backgroundColor: 'transparent',
                  border: 'none',
                  borderBottom: selected ? '2px solid #1A1918' : '2px solid transparent',
                  padding: '10px 14px',
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                  marginBottom: -1,
                }}
              >
                {a}
                {selected && <span className="sr-only"> (선택됨)</span>}
              </button>
            )
          })}
        </div>

        {/* ── NO QUERY STATE ────────────────────────────────────────── */}
        {!hasQuery && (
          <div style={{ paddingTop: 60, textAlign: 'center' }}>
            <p style={{ fontSize: 15, color: '#908D88' }}>검색어를 입력하면 결과가 표시됩니다.</p>
          </div>
        )}

        {/* ── SCR-001: INTEGRATED 전체 RESULTS ─────────────────────── */}
        {hasQuery && showAll && (
          <>
            <DomainSection title="커뮤니티" moreLabel="커뮤니티 결과 더보기" moreHref={moreUrl('커뮤니티')}>
              {COMMUNITY_RESULTS.map((item) => <CommunityResultItem key={item.id} item={item} />)}
            </DomainSection>
            <DomainSection title="복지·지원정보" moreLabel="복지·지원정보 결과 더보기" moreHref={moreUrl('복지·지원정보')}>
              {WELFARE_RESULTS.map((item) => <WelfareResultItem key={item.id} item={item} />)}
            </DomainSection>
            <DomainSection title="병원·전문기관" moreLabel="병원·전문기관 결과 더보기" moreHref={moreUrl('병원·전문기관')}>
              {HOSPITAL_RESULTS.map((item) => <HospitalResultItem key={item.id} item={item} />)}
            </DomainSection>
            <DomainSection title="보조기기" moreLabel="보조기기 결과 더보기" moreHref={moreUrl('보조기기')}>
              {DEVICE_RESULTS.map((item) => <DeviceResultItem key={item.id} item={item} />)}
            </DomainSection>
          </>
        )}

        {/* ── SCR-002: SINGLE DOMAIN FULL RESULTS ──────────────────── */}
        {hasQuery && !showAll && (
          <div style={{ paddingTop: 24 }}>

            {/* Filter controls */}
            <div
              style={{
                display: 'flex', alignItems: 'center', gap: 20, flexWrap: 'wrap',
                paddingBottom: 16, borderBottom: '1px solid #D0CEC9', marginBottom: 20,
              }}
            >
              {(FILTER_OPTIONS[area] ?? []).map((filter) => (
                <FilterSelect
                  key={filter.key}
                  id={`filter-${filter.key}`}
                  label={filter.label}
                  value={searchParams.get(filter.key) ?? ''}
                  options={filter.options}
                  onChange={(v) => handleFilterChange(filter.key, v)}
                  onReset={() => handleFilterChange(filter.key, '')}
                />
              ))}
              {hasActiveFilters && (
                <button
                  type="button"
                  onClick={handleResetAll}
                  style={{
                    fontSize: 12, color: '#908D88', background: 'none', border: 'none',
                    cursor: 'pointer', padding: '4px 0', textDecoration: 'underline',
                    fontFamily: 'inherit', marginLeft: 'auto',
                  }}
                >
                  전체 초기화
                </button>
              )}
            </div>

            {/* AND filter summary */}
            {filterSummary && (
              <p style={{ fontSize: 12, color: '#4A4845', marginBottom: 16, fontFamily: "'DM Mono', monospace" }}>
                {filterSummary}
              </p>
            )}

            {/* ── Total Error state ──────────────────────────────────── */}
            {isError && (
              <div style={{ paddingTop: 60, textAlign: 'center' }}>
                <p style={{ fontSize: 15, fontWeight: 500, color: '#1A1918', marginBottom: 8 }}>
                  검색 결과를 불러오지 못했습니다.
                </p>
                <p style={{ fontSize: 13, color: '#908D88', marginBottom: 28 }}>
                  잠시 후 다시 시도해 주세요.
                </p>
                <button
                  type="button"
                  onClick={handleRetry}
                  aria-label="검색 결과 다시 시도"
                  style={{
                    fontSize: 14, fontWeight: 600, color: '#FAFAF8',
                    backgroundColor: '#1A1918', border: 'none', borderRadius: 2,
                    padding: '10px 28px', cursor: 'pointer', fontFamily: 'inherit',
                  }}
                >
                  다시 시도
                </button>
              </div>
            )}

            {/* ── No result state ─────────────────────────────────────── */}
            {isNoResult && (
              <div style={{ paddingTop: 60, textAlign: 'center' }}>
                <p style={{ fontSize: 15, fontWeight: 500, color: '#1A1918', marginBottom: 8 }}>
                  검색 결과가 없습니다.
                </p>
                <p style={{ fontSize: 13, color: '#908D88', marginBottom: 24 }}>
                  {isMockNoResult
                    ? '다른 검색어를 입력해 보세요.'
                    : '검색어를 수정하거나 필터를 조정해 보세요.'}
                </p>
                {hasActiveFilters && !isMockNoResult && (
                  <button
                    type="button"
                    onClick={handleResetAll}
                    style={{
                      fontSize: 13, fontWeight: 600, color: '#1A1918',
                      backgroundColor: 'transparent',
                      borderTop: '1.5px solid #1A1918', borderBottom: '1.5px solid #1A1918',
                      borderLeft: '1.5px solid #1A1918', borderRight: '1.5px solid #1A1918',
                      borderRadius: 2, padding: '10px 24px',
                      cursor: 'pointer', fontFamily: 'inherit',
                    }}
                  >
                    필터 전체 초기화
                  </button>
                )}
              </div>
            )}

            {/* ── Results available ──────────────────────────────────── */}
            {!isError && !isNoResult && (
              <>
                <p style={{ fontSize: 12, color: '#908D88', marginBottom: 14 }}>
                  총 {totalResults}건 · {safePage}/{totalPages} 페이지
                </p>
                <ol style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                  {area === '커뮤니티' && pageItems.map((item: CommunityResult) => (
                    <CommunityResultItem key={item.id} item={item} />
                  ))}
                  {area === '복지·지원정보' && pageItems.map((item: WelfareResult) => (
                    <WelfareResultItem key={item.id} item={item} />
                  ))}
                  {area === '병원·전문기관' && pageItems.map((item: HospitalResult) => (
                    <HospitalResultItem key={item.id} item={item} />
                  ))}
                  {area === '보조기기' && pageItems.map((item: DeviceResult) => (
                    <DeviceResultItem key={item.id} item={item} />
                  ))}
                </ol>
                <Pagination
                  currentPage={safePage}
                  totalPages={totalPages}
                  onPageChange={handlePageChange}
                />
              </>
            )}
          </div>
        )}
      </div>
    </main>
  )
}
