"use client";

import { useState, useCallback } from 'react'
import Link from '@/components/AppLink';
import { useQueryParams } from '@/lib/useQueryParams';

// ─── Data ────────────────────────────────────────────────────────────────────

type PostType = '일반' | '질문·답변' | '경험·후기'
type Topic =
  | '자유·일상'
  | '보청기'
  | '인공와우'
  | '치료·재활'
  | '의사소통'
  | '취업·직장'
  | '복지·생활'

type QuestionStatus = '답변 대기' | '답변 있음' | '해결됨'

interface Post {
  id: number
  type: PostType
  topic: Topic
  title: string
  author: string
  date: string
  comments: number
  pinned?: boolean
  notice?: boolean
  questionStatus?: QuestionStatus
}

const ALL_POSTS: Post[] = [
  // Notices / pinned (always at top)
  {
    id: 0,
    type: '일반',
    topic: '자유·일상',
    title: '커뮤니티 이용 규칙 안내 (2026년 8월 개정)',
    author: '운영팀',
    date: '2026.08.01',
    comments: 0,
    notice: true,
  },
  {
    id: 100,
    type: '일반',
    topic: '자유·일상',
    title: '게시글 신고 및 처리 절차 변경 안내',
    author: '운영팀',
    date: '2026.07.15',
    comments: 2,
    pinned: true,
  },
  // Regular posts
  {
    id: 1,
    type: '질문·답변',
    topic: '보청기',
    title: '보청기 보험 급여 기준이 바뀐 것 같은데, 혹시 아시는 분 계세요?',
    author: '이○○',
    date: '2026.08.15',
    comments: 14,
    questionStatus: '답변 대기',
  },
  {
    id: 2,
    type: '경험·후기',
    topic: '인공와우',
    title: '세브란스병원 인공와우 수술 후기 공유합니다 (6개월 경과)',
    author: '김○○',
    date: '2026.08.15',
    comments: 23,
  },
  {
    id: 3,
    type: '질문·답변',
    topic: '취업·직장',
    title: '직장에서 청각장애를 고지해야 할 때 어떻게 하셨나요?',
    author: '박○○',
    date: '2026.08.14',
    comments: 31,
    questionStatus: '해결됨',
  },
  {
    id: 4,
    type: '경험·후기',
    topic: '복지·생활',
    title: '2026 장애인 보조기기 교부사업 신청 완료했어요. 생각보다 간단했습니다.',
    author: '최○○',
    date: '2026.08.14',
    comments: 8,
  },
  {
    id: 5,
    type: '질문·답변',
    topic: '의사소통',
    title: '수어 통역 앱 중에 실제로 쓸 만한 게 있나요?',
    author: '정○○',
    date: '2026.08.13',
    comments: 17,
    questionStatus: '답변 있음',
  },
  {
    id: 6,
    type: '질문·답변',
    topic: '치료·재활',
    title: '청능재활 치료 몇 회쯤 받으면 효과가 느껴지나요?',
    author: '윤○○',
    date: '2026.08.13',
    comments: 9,
    questionStatus: '해결됨',
  },
  {
    id: 7,
    type: '일반',
    topic: '보청기',
    title: '오티콘 모어 vs 스타키 에볼브 AI, 어느 쪽 쓰시나요?',
    author: '강○○',
    date: '2026.08.12',
    comments: 20,
  },
  {
    id: 8,
    type: '일반',
    topic: '자유·일상',
    title: '오늘 아이 첫 보청기 착용했어요. 많이 낯설어하더라고요.',
    author: '조○○',
    date: '2026.08.12',
    comments: 12,
  },
  {
    id: 9,
    type: '경험·후기',
    topic: '치료·재활',
    title: '언어치료 1년 후기 — 아이의 발음이 많이 좋아졌습니다',
    author: '임○○',
    date: '2026.08.11',
    comments: 5,
  },
  {
    id: 10,
    type: '질문·답변',
    topic: '인공와우',
    title: '인공와우 수술 후 소리 적응에 얼마나 걸리셨나요?',
    author: '한○○',
    date: '2026.08.11',
    comments: 19,
    questionStatus: '해결됨',
  },
  {
    id: 11,
    type: '일반',
    topic: '복지·생활',
    title: '장애인 복지카드 사용처가 생각보다 다양하네요',
    author: '오○○',
    date: '2026.08.10',
    comments: 7,
  },
  {
    id: 12,
    type: '경험·후기',
    topic: '취업·직장',
    title: '공공기관 장애인 채용 전형 준비 경험 공유합니다',
    author: '서○○',
    date: '2026.08.10',
    comments: 28,
  },
  {
    id: 13,
    type: '질문·답변',
    topic: '보청기',
    title: '보청기 건전지 vs 충전식, 장기적으로 어느 게 더 편한가요?',
    author: '권○○',
    date: '2026.08.09',
    comments: 11,
    questionStatus: '답변 대기',
  },
  {
    id: 14,
    type: '일반',
    topic: '의사소통',
    title: '직장 동료에게 청각장애를 설명할 때 어떤 표현을 쓰시나요?',
    author: '황○○',
    date: '2026.08.09',
    comments: 16,
  },
  {
    id: 15,
    type: '경험·후기',
    topic: '자유·일상',
    title: '처음으로 수어 수업을 들었어요 — 생각보다 재미있어서 놀랐습니다',
    author: '안○○',
    date: '2026.08.08',
    comments: 6,
  },
  {
    id: 16,
    type: '질문·답변',
    topic: '복지·생활',
    title: '청각장애 2급에서 1급으로 재판정받으려면 어떤 절차가 필요한가요?',
    author: '신○○',
    date: '2026.08.08',
    comments: 3,
    questionStatus: '답변 대기',
  },
  {
    id: 17,
    type: '일반',
    topic: '인공와우',
    title: '인공와우 착용 후 음악 감상이 가능해진 분 계세요?',
    author: '유○○',
    date: '2026.08.07',
    comments: 22,
  },
  {
    id: 18,
    type: '경험·후기',
    topic: '의사소통',
    title: '음성 인식 자막 앱을 6개월 써본 솔직한 후기',
    author: '전○○',
    date: '2026.08.07',
    comments: 13,
  },
]

const POST_TYPES: Array<'전체' | PostType> = ['전체', '일반', '질문·답변', '경험·후기']
const TOPICS: Array<'전체' | Topic> = [
  '전체',
  '자유·일상',
  '보청기',
  '인공와우',
  '치료·재활',
  '의사소통',
  '취업·직장',
  '복지·생활',
]

const PAGE_SIZE = 10

// ─── Helpers ─────────────────────────────────────────────────────────────────

function filterPosts(
  posts: Post[],
  type: string,
  topic: string,
  keyword: string,
): Post[] {
  return posts.filter((p) => {
    if (p.notice || p.pinned) return false
    if (type !== '전체' && p.type !== type) return false
    if (topic !== '전체' && p.topic !== topic) return false
    if (keyword.trim()) {
      const q = keyword.trim().toLowerCase()
      if (!p.title.toLowerCase().includes(q) && !p.type.includes(q) && !p.topic.includes(q)) return false
    }
    return true
  })
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function FilterChip({
  label,
  selected,
  onClick,
}: {
  label: string
  selected: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      style={{
        fontSize: 13,
        fontWeight: selected ? 600 : 400,
        padding: '4px 11px',
        border: selected ? '1.5px solid #1A1918' : '1px solid #C8C5BF',
        borderRadius: 2,
        backgroundColor: selected ? '#1A1918' : '#FAFAF8',
        color: selected ? '#FAFAF8' : '#1A1918',
        cursor: 'pointer',
        transition: 'background-color 0.12s, border-color 0.12s',
        fontFamily: 'inherit',
      }}
    >
      {label}
    </button>
  )
}

function NoticeItem({ post }: { post: Post }) {
  return (
    <li style={{ borderTop: '1px solid #E8E6E1', backgroundColor: '#F6F4EF' }}>
      <div style={{ padding: '12px 0', display: 'flex', alignItems: 'baseline', gap: 10 }}>
        <span
          style={{
            flexShrink: 0,
            fontSize: 11,
            fontWeight: 600,
            padding: '2px 6px',
            border: '1px solid #908D88',
            color: '#4A4845',
            borderRadius: 2,
            letterSpacing: '0.02em',
          }}
          aria-label={post.notice ? '공지' : '고정'}
        >
          {post.notice ? '공지' : '고정'}
        </span>
        <Link
          href={`/community/posts/${post.id}`}
          style={{
            fontSize: 14,
            fontWeight: 500,
            color: '#1A1918',
            textDecoration: 'none',
            lineHeight: 1.4,
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = '#1E3A8A'
            e.currentTarget.style.textDecoration = 'underline'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = '#1A1918'
            e.currentTarget.style.textDecoration = 'none'
          }}
        >
          {post.title}
        </Link>
        <span style={{ marginLeft: 'auto', flexShrink: 0, fontSize: 12, color: '#908D88' }}>
          {post.date}
        </span>
      </div>
    </li>
  )
}

function PostItem({ post }: { post: Post }) {
  return (
    <li style={{ borderTop: '1px solid #E8E6E1' }}>
      <div style={{ padding: '14px 0' }}>
        {/* Type · Topic */}
        <div className="flex items-center gap-1.5" style={{ marginBottom: 5 }}>
          <span style={{ fontSize: 12, fontWeight: 500, color: '#1E3A8A' }}>{post.type}</span>
          <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
          <span style={{ fontSize: 12, color: '#908D88' }}>{post.topic}</span>
          {post.type === '질문·답변' && post.questionStatus && (
            <>
              <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 500,
                  padding: '1px 6px',
                  border: post.questionStatus === '해결됨'
                    ? '1px solid #166534'
                    : post.questionStatus === '답변 있음'
                    ? '1px solid #1E3A8A'
                    : '1px solid #92400E',
                  color: post.questionStatus === '해결됨'
                    ? '#166534'
                    : post.questionStatus === '답변 있음'
                    ? '#1E3A8A'
                    : '#92400E',
                  borderRadius: 2,
                }}
                aria-label={`질문 상태: ${post.questionStatus}`}
              >
                {post.questionStatus}
              </span>
            </>
          )}
        </div>

        {/* Title link */}
        <Link
          href={`/community/posts/${post.id}${post.questionStatus ? `?questionStatus=${encodeURIComponent(post.questionStatus)}` : ""}`}
          style={{
            display: 'block',
            fontSize: 15,
            fontWeight: 500,
            color: '#1A1918',
            textDecoration: 'none',
            lineHeight: 1.45,
            marginBottom: 6,
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = '#1E3A8A'
            e.currentTarget.style.textDecoration = 'underline'
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = '#1A1918'
            e.currentTarget.style.textDecoration = 'none'
          }}
        >
          {post.title}
        </Link>

        {/* Metadata */}
        <div className="flex items-center gap-1.5">
          <span style={{ fontSize: 12, color: '#908D88' }}>{post.author}</span>
          <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
          <time style={{ fontSize: 12, color: '#908D88' }} dateTime={post.date.replace(/\./g, '-')}>
            {post.date}
          </time>
          <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
          <span style={{ fontSize: 12, color: '#908D88' }} aria-label={`댓글 ${post.comments}개`}>
            댓글 {post.comments}
          </span>
        </div>
      </div>
    </li>
  )
}

function Pagination({
  current,
  total,
  onChange,
}: {
  current: number
  total: number
  onChange: (p: number) => void
}) {
  if (total <= 1) return null

  const pages = Array.from({ length: total }, (_, i) => i + 1)

  return (
    <nav aria-label="페이지 탐색" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 4, paddingTop: 32, paddingBottom: 8 }}>
      <button
        onClick={() => onChange(current - 1)}
        disabled={current === 1}
        aria-label="이전 페이지"
        style={{
          width: 36,
          height: 36,
          border: '1px solid #D0CEC9',
          borderRadius: 2,
          backgroundColor: 'transparent',
          cursor: current === 1 ? 'not-allowed' : 'pointer',
          color: current === 1 ? '#C8C5BF' : '#1A1918',
          fontSize: 14,
          fontFamily: 'inherit',
        }}
      >
        ‹
      </button>

      {pages.map((p) => (
        <button
          key={p}
          onClick={() => onChange(p)}
          aria-label={`${p}페이지`}
          aria-current={p === current ? 'page' : undefined}
          style={{
            width: 36,
            height: 36,
            border: p === current ? '1.5px solid #1A1918' : '1px solid #D0CEC9',
            borderRadius: 2,
            backgroundColor: p === current ? '#1A1918' : 'transparent',
            color: p === current ? '#FAFAF8' : '#1A1918',
            fontWeight: p === current ? 600 : 400,
            fontSize: 14,
            cursor: 'pointer',
            fontFamily: 'inherit',
          }}
        >
          {p}
        </button>
      ))}

      <button
        onClick={() => onChange(current + 1)}
        disabled={current === total}
        aria-label="다음 페이지"
        style={{
          width: 36,
          height: 36,
          border: '1px solid #D0CEC9',
          borderRadius: 2,
          backgroundColor: 'transparent',
          cursor: current === total ? 'not-allowed' : 'pointer',
          color: current === total ? '#C8C5BF' : '#1A1918',
          fontSize: 14,
          fontFamily: 'inherit',
        }}
      >
        ›
      </button>
    </nav>
  )
}

// ─── Screen ──────────────────────────────────────────────────────────────────

export default function PostList() {
  const [searchParams, setSearchParams] = useQueryParams()

  const typeFilter = searchParams.get('type') ?? '전체';
  const topicFilter = searchParams.get('topic') ?? '전체';
  const page = Math.max(1, Number(searchParams.get('page') ?? '1') || 1);
  const [searchInput, setSearchInput] = useState('');
  const [activeKeyword, setActiveKeyword] = useState('');

  const updateParams = useCallback(
    (type: string, topic: string, p: number) => {
      const params: Record<string, string> = {}
      if (type !== '전체') params.type = type
      if (topic !== '전체') params.topic = topic
      if (p > 1) params.page = String(p)
      setSearchParams(params, { replace: true })
    },
    [setSearchParams],
  )

  const handleTypeChange = (t: string) => {
    updateParams(t, topicFilter, 1)
  }

  const handleTopicChange = (tp: string) => {
    updateParams(typeFilter, tp, 1)
  }

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setActiveKeyword(searchInput)
    updateParams(typeFilter, topicFilter, 1)
  }

  const clearKeyword = () => {
    setSearchInput('')
    setActiveKeyword('')
    updateParams(typeFilter, topicFilter, 1)
  }

  const handlePageChange = (p: number) => {
    updateParams(typeFilter, topicFilter, p)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  // Derive list
  const notices = ALL_POSTS.filter((p) => p.notice || p.pinned)
  const filtered = filterPosts(ALL_POSTS, typeFilter, topicFilter, activeKeyword)
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const paginated = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  const hasActiveFilter = typeFilter !== '전체' || topicFilter !== '전체' || activeKeyword

  // Derive heading: use topic name when topic is filtered, otherwise "전체 게시글"
  const pageTitle = topicFilter !== '전체' ? topicFilter : '전체 게시글'

  return (
    <main id="main-content">

      {/* ─── PAGE HEADER ──────────────────────────────────────────── */}
      <div style={{ borderBottom: '1px solid #D0CEC9', backgroundColor: '#F0EEE9', paddingTop: 28, paddingBottom: 28 }}>
        <div className="max-w-6xl mx-auto px-6">
          <div className="flex items-center justify-between gap-6">
            <div>
              <h1 className="font-bold" style={{ fontSize: 24, lineHeight: 1.25, color: '#1A1918' }}>
                {pageTitle}
              </h1>
              {topicFilter !== '전체' && (
                <p style={{ fontSize: 13, color: '#908D88', marginTop: 4 }}>커뮤니티 · {topicFilter} 주제 게시글</p>
              )}
            </div>
            <Link
              href={`/community/posts/create${
                typeFilter !== '전체' || topicFilter !== '전체'
                  ? `?${new URLSearchParams([
                      ...(typeFilter !== '전체' ? [['type', typeFilter]] : []),
                      ...(topicFilter !== '전체' ? [['topic', topicFilter]] : []),
                    ]).toString()}`
                  : ''
              }`}
              style={{
                flexShrink: 0,
                backgroundColor: '#1E3A8A',
                color: '#FFFFFF',
                borderRadius: 2,
                padding: '10px 20px',
                fontSize: 14,
                fontWeight: 600,
                marginTop: 2,
                textDecoration: 'none',
                display: 'inline-block',
              }}
            >
              글쓰기
            </Link>
          </div>
        </div>
      </div>

      {/* ─── SEARCH + FILTERS ─────────────────────────────────────── */}
      <div style={{ borderBottom: '1px solid #D0CEC9', paddingTop: 18, paddingBottom: 18 }}>
        <div className="max-w-6xl mx-auto px-6">

          {/* Community Search */}
          <form onSubmit={handleSearch} role="search" aria-label="커뮤니티 게시글 검색" style={{ marginBottom: 16 }}>
            <label
              htmlFor="community-search"
              style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#4A4845', marginBottom: 6, letterSpacing: '0.01em' }}
            >
              커뮤니티 게시글 검색
            </label>
            <div style={{ display: 'flex', maxWidth: 560 }}>
              <div style={{ position: 'relative', flex: 1 }}>
                <input
                  id="community-search"
                  type="search"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  placeholder="제목, 본문, 글 유형, 주제 검색"
                  style={{
                    width: '100%',
                    border: '1.5px solid #1A1918',
                    borderRight: 'none',
                    borderRadius: '2px 0 0 2px',
                    padding: '9px 36px 9px 12px',
                    fontSize: 14,
                    backgroundColor: '#FAFAF8',
                    color: '#1A1918',
                    outline: 'none',
                    fontFamily: 'inherit',
                  }}
                />
                {searchInput && (
                  <button
                    type="button"
                    onClick={() => setSearchInput('')}
                    aria-label="검색어 지우기"
                    style={{
                      position: 'absolute',
                      right: 10,
                      top: '50%',
                      transform: 'translateY(-50%)',
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      color: '#908D88',
                      fontSize: 16,
                      lineHeight: 1,
                      padding: 0,
                    }}
                  >
                    ✕
                  </button>
                )}
              </div>
              <button
                type="submit"
                style={{
                  backgroundColor: '#1A1918',
                  color: '#FAFAF8',
                  border: '1.5px solid #1A1918',
                  borderRadius: '0 2px 2px 0',
                  padding: '9px 18px',
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  fontFamily: 'inherit',
                }}
              >
                검색
              </button>
            </div>
          </form>

          {/* Filters row */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-start' }}>

            {/* Post Type filter */}
            <fieldset style={{ border: 'none', padding: 0, margin: 0 }}>
              <legend style={{ fontSize: 12, fontWeight: 500, color: '#4A4845', marginBottom: 8, letterSpacing: '0.01em' }}>
                글 유형
              </legend>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {POST_TYPES.map((t) => (
                  <FilterChip
                    key={t}
                    label={t}
                    selected={typeFilter === t}
                    onClick={() => handleTypeChange(t)}
                  />
                ))}
              </div>
            </fieldset>

            {/* Divider */}
            <div style={{ width: 1, height: 48, backgroundColor: '#D0CEC9', alignSelf: 'center', flexShrink: 0 }} aria-hidden="true" />

            {/* Topic filter */}
            <fieldset style={{ border: 'none', padding: 0, margin: 0, flex: 1 }}>
              <legend style={{ fontSize: 12, fontWeight: 500, color: '#4A4845', marginBottom: 8, letterSpacing: '0.01em' }}>
                주요 주제
              </legend>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {TOPICS.map((tp) => (
                  <FilterChip
                    key={tp}
                    label={tp}
                    selected={topicFilter === tp}
                    onClick={() => handleTopicChange(tp)}
                  />
                ))}
              </div>
            </fieldset>
          </div>
        </div>
      </div>

      {/* ─── POST LIST ────────────────────────────────────────────── */}
      <div className="max-w-6xl mx-auto px-6" style={{ paddingTop: 20, paddingBottom: 56 }}>

        {/* List meta: result count + sort */}
        <div
          style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 4 }}
        >
          <p style={{ fontSize: 13, color: '#908D88' }}>
            {hasActiveFilter
              ? `${filtered.length}개의 게시글`
              : `전체 ${filtered.length}개의 게시글`}
            {activeKeyword && (
              <>
                {' — "'}
                <strong style={{ color: '#1A1918', fontWeight: 500 }}>{activeKeyword}</strong>
                {'" 검색 결과 '}
                <button
                  onClick={clearKeyword}
                  style={{ fontSize: 12, color: '#1E3A8A', background: 'none', border: 'none', cursor: 'pointer', padding: 0, fontFamily: 'inherit', textDecoration: 'underline' }}
                  aria-label="검색어 초기화"
                >
                  검색어 초기화
                </button>
              </>
            )}
          </p>
          <span style={{ fontSize: 12, color: '#908D88' }} aria-label="정렬 기준: 최신순">
            최신순
          </span>
        </div>

        {/* Notices — always shown regardless of filter state (unless search keyword hides them) */}
        {!activeKeyword && typeFilter === '전체' && topicFilter === '전체' && (
          <ol aria-label="공지 및 고정 게시글">
            {notices.map((post) => (
              <NoticeItem key={post.id} post={post} />
            ))}
          </ol>
        )}

        {/* Regular posts */}
        {paginated.length > 0 ? (
          <ol aria-label="게시글 목록">
            {paginated.map((post) => (
              <PostItem key={post.id} post={post} />
            ))}
          </ol>
        ) : (
          <div
            style={{
              borderTop: '1px solid #D0CEC9',
              borderBottom: '1px solid #D0CEC9',
              padding: '48px 0',
              textAlign: 'center',
            }}
            role="status"
            aria-live="polite"
          >
            <p style={{ fontSize: 15, color: '#4A4845', marginBottom: 8 }}>
              {activeKeyword
                ? `"${activeKeyword}"에 해당하는 게시글이 없습니다.`
                : '해당 조건에 맞는 게시글이 없습니다.'}
            </p>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 16 }}>
              {activeKeyword && (
                <button
                  onClick={clearKeyword}
                  style={{ fontSize: 13, padding: '6px 14px', border: '1px solid #D0CEC9', borderRadius: 2, background: '#FAFAF8', cursor: 'pointer', color: '#1A1918', fontFamily: 'inherit' }}
                >
                  검색어 초기화
                </button>
              )}
              {(typeFilter !== '전체' || topicFilter !== '전체') && (
                <button
                  onClick={() => { handleTypeChange('전체'); handleTopicChange('전체') }}
                  style={{ fontSize: 13, padding: '6px 14px', border: '1px solid #D0CEC9', borderRadius: 2, background: '#FAFAF8', cursor: 'pointer', color: '#1A1918', fontFamily: 'inherit' }}
                >
                  필터 초기화
                </button>
              )}
            </div>
          </div>
        )}

        <Pagination current={safePage} total={totalPages} onChange={handlePageChange} />
      </div>

    </main>
  )
}
