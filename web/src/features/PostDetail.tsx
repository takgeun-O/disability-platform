"use client";

import { useParams, useSearchParams } from 'next/navigation';
import { type CreatedPost, useCreatedPost } from '@/lib/mock-posts';
import { useState, useRef, useEffect } from 'react'
import Link from '@/components/AppLink';

// ─── Sample post data ─────────────────────────────────────────────────────────

const SAMPLE_POST = {
  id: 8,
  type: '일반',
  topic: '자유·일상',
  title: '오늘 아이 첫 보청기 착용했어요. 많이 낯설어하더라고요.',
  author: '조○○',
  date: '2026.08.12',
  edited: false,
  body: [
    '오늘 드디어 아이 첫 보청기를 착용했습니다. 한 달 넘게 기다리다가 겨우 맞춤 제작이 완료됐는데, 막상 착용하고 나니 아이가 너무 낯설어하더라고요.',
    '처음에는 귀에 뭔가 끼워지는 게 싫은지 계속 손으로 빼려고 했어요. 보청기사 선생님 말씀으로는 처음 1~2주는 하루 1~2시간씩만 착용해서 천천히 적응시키는 게 좋다고 하셨어요.',
    '그래도 소리를 들을 때 눈이 커지는 게 느껴졌어요. 엄마 목소리에 반응하는 거 보고 눈물이 날 뻔했습니다. 같은 경험 하신 분들, 적응 기간이 얼마나 걸리셨나요? 혹시 처음에 잘 쓰게 하는 방법이 있으면 알려주시면 감사하겠습니다.',
  ],
}

// ─── Sample comments ──────────────────────────────────────────────────────────

interface Reply {
  id: number
  author: string
  date: string
  body: string
  isOwn?: boolean
}

interface Comment {
  id: number
  author: string
  date: string
  body: string
  isOwn?: boolean
  replies?: Reply[]
}

const INITIAL_COMMENTS: Comment[] = [
  {
    id: 1,
    author: '김○○',
    date: '2026.08.12',
    body: '저도 비슷한 경험이 있어요. 처음 2주가 제일 힘들었는데, 좋아하는 노래나 동요를 보청기 착용한 채로 틀어주는 게 효과가 있었어요. 소리에 좋은 기억을 연결시켜 주면 거부감이 좀 줄어드는 것 같더라고요.',
    replies: [
      {
        id: 11,
        author: '조○○',
        date: '2026.08.12',
        body: '동요 틀어주는 방법 꼭 해볼게요! 감사합니다.',
        isOwn: true,
      },
    ],
  },
  {
    id: 2,
    author: '이○○',
    date: '2026.08.13',
    body: '우리 아이는 한 달쯤 지나니까 스스로 "귀 달아줘"라고 하더라고요. 처음엔 정말 힘드셨겠지만, 곧 익숙해질 거예요. 화이팅입니다!',
  },
]

// ─── Sub-components ───────────────────────────────────────────────────────────

function MoreMenu({
  items,
  ariaLabel,
}: {
  items: Array<{ label: string; danger?: boolean; onClick: () => void }>
  ariaLabel: string
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded={open}
        aria-label={ariaLabel}
        onClick={() => setOpen((v) => !v)}
        style={{
          fontSize: 16,
          letterSpacing: '0.05em',
          padding: '4px 8px',
          border: '1px solid #D0CEC9',
          borderRadius: 2,
          backgroundColor: 'transparent',
          color: '#4A4845',
          cursor: 'pointer',
          fontFamily: 'inherit',
          lineHeight: 1,
        }}
      >
        ⋯
      </button>
      {open && (
        <div
          role="menu"
          style={{
            position: 'absolute',
            right: 0,
            top: 'calc(100% + 4px)',
            backgroundColor: '#FAFAF8',
            border: '1px solid #D0CEC9',
            borderRadius: 2,
            minWidth: 100,
            zIndex: 10,
            boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
          }}
        >
          {items.map((item) => (
            <button
              key={item.label}
              role="menuitem"
              type="button"
              onClick={() => { item.onClick(); setOpen(false) }}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                fontSize: 13,
                padding: '9px 14px',
                border: 'none',
                backgroundColor: 'transparent',
                cursor: 'pointer',
                color: item.danger ? '#B91C1C' : '#1A1918',
                fontFamily: 'inherit',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#EAE8E3')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function ActionButton({
  label,
  active,
  activeLabel,
  onClick,
}: {
  label: string
  active?: boolean
  activeLabel?: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      style={{
        fontSize: 13,
        fontWeight: active ? 600 : 500,
        padding: '6px 14px',
        border: active ? '1.5px solid #1A1918' : '1px solid #D0CEC9',
        borderRadius: 2,
        backgroundColor: active ? '#1A1918' : 'transparent',
        color: active ? '#FAFAF8' : '#4A4845',
        cursor: 'pointer',
        fontFamily: 'inherit',
        transition: 'background-color 0.12s',
      }}
    >
      {active && activeLabel ? activeLabel : label}
    </button>
  )
}

function CommentItem({
  comment,
  onReply,
}: {
  comment: Comment
  onReply: (commentId: number) => void
}) {
  return (
    <li style={{ borderTop: '1px solid #E8E6E1' }}>
      <div style={{ padding: '14px 0' }}>
        {/* Author + date */}
        <div className="flex items-center gap-2" style={{ marginBottom: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: '#1A1918' }}>{comment.author}</span>
          <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
          <time style={{ fontSize: 12, color: '#908D88' }} dateTime={comment.date.replace(/\./g, '-')}>
            {comment.date}
          </time>
        </div>
        {/* Body */}
        <p style={{ fontSize: 14, lineHeight: 1.65, color: '#1A1918', margin: 0, marginBottom: 10 }}>
          {comment.body}
        </p>
        {/* Actions */}
        <div className="flex items-center gap-3">
          {!comment.isOwn && (
            <button
              type="button"
              onClick={() => onReply(comment.id)}
              style={{ fontSize: 12, color: '#908D88', background: 'none', border: 'none', cursor: 'pointer', padding: 0, fontFamily: 'inherit' }}
            >
              답글
            </button>
          )}
          {comment.isOwn ? (
            <MoreMenu ariaLabel="댓글 메뉴" items={[
              { label: '수정', onClick: () => {} },
              { label: '삭제', danger: true, onClick: () => {} },
            ]} />
          ) : (
            <MoreMenu ariaLabel="댓글 메뉴" items={[{ label: '신고', danger: true, onClick: () => {} }]} />
          )}
        </div>
      </div>

      {/* Replies */}
      {comment.replies && comment.replies.length > 0 && (
        <ol
          aria-label={`${comment.author}의 댓글에 대한 답글`}
          style={{ borderTop: '1px solid #E8E6E1', margin: 0, padding: 0, listStyle: 'none' }}
        >
          {comment.replies.map((reply) => (
            <li
              key={reply.id}
              style={{ paddingLeft: 24, borderLeft: '2px solid #E8E6E1', marginLeft: 16, paddingTop: 12, paddingBottom: 12 }}
            >
              <div className="flex items-center gap-2" style={{ marginBottom: 5 }}>
                <span style={{ fontSize: 11, fontWeight: 500, color: '#908D88', letterSpacing: '0.03em', textTransform: 'uppercase' }}>답글</span>
                <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#1A1918' }}>{reply.author}</span>
                <span style={{ color: '#D0CEC9', fontSize: 11 }} aria-hidden="true">·</span>
                <time style={{ fontSize: 12, color: '#908D88' }} dateTime={reply.date.replace(/\./g, '-')}>
                  {reply.date}
                </time>
              </div>
              <p style={{ fontSize: 14, lineHeight: 1.65, color: '#1A1918', margin: 0, marginBottom: 8 }}>
                {reply.body}
              </p>
              <div className="flex items-center gap-3">
                {reply.isOwn ? (
                  <MoreMenu ariaLabel="답글 메뉴" items={[
                    { label: '수정', onClick: () => {} },
                    { label: '삭제', danger: true, onClick: () => {} },
                  ]} />
                ) : (
                  <MoreMenu ariaLabel="답글 메뉴" items={[{ label: '신고', danger: true, onClick: () => {} }]} />
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </li>
  )
}

// ─── Screen ──────────────────────────────────────────────────────────────────

export default function PostDetail() {
  const cp = useCreatedPost();
  const {postId} = useParams<{postId: string}>();
  const params = useSearchParams();
  const incomingStatus = params.get('questionStatus');
  useEffect(() => {
    if (cp) history.replaceState({...history.state, iyumMockPost: {id: postId, post: cp}}, '');
  }, [cp, postId]);
  return <PostDetailContent key={`${postId}:${cp ? 'created' : 'sample'}`} cp={cp} incomingStatus={incomingStatus} />;
}

function PostDetailContent({cp, incomingStatus}: {cp: CreatedPost | null; incomingStatus: string | null}) {
  const [recommended, setRecommended] = useState(false)
  const [saved, setSaved] = useState(false)
  const [shareCopied, setShareCopied] = useState(false)
  const [commentText, setCommentText] = useState('')
  const [comments, setComments] = useState<Comment[]>(cp ? [] : INITIAL_COMMENTS)
  const [replyingTo, setReplyingTo] = useState<number | null>(null)
  const [replyText, setReplyText] = useState('')
  const commentFormRef = useRef<HTMLTextAreaElement>(null)

  const post = cp
    ? {
        ...SAMPLE_POST,
        type: cp.type,
        topic: cp.topic,
        title: cp.title,
        author: cp.author,
        date: cp.date,
        edited: false,
        body: cp.bodyText.split(/\n{2,}/).map((p) => p.trim()).filter(Boolean).length > 0
          ? cp.bodyText.split(/\n{2,}/).map((p) => p.trim()).filter(Boolean)
          : [cp.bodyText],
      }
    : SAMPLE_POST

  function handleShare() {
    setShareCopied(true)
    setTimeout(() => setShareCopied(false), 2000)
  }

  function handleSubmitComment(e: React.FormEvent) {
    e.preventDefault()
    if (!commentText.trim()) return
    const newComment: Comment = {
      id: Date.now(),
      author: '나 (로그인 중)',
      date: '2026.08.17',
      body: commentText.trim(),
      isOwn: true,
    }
    setComments((prev) => [...prev, newComment])
    setCommentText('')
  }

  function handleSubmitReply(e: React.FormEvent, parentId: number) {
    e.preventDefault()
    if (!replyText.trim()) return
    const newReply: Reply = {
      id: Date.now(),
      author: '나 (로그인 중)',
      date: '2026.08.17',
      body: replyText.trim(),
      isOwn: true,
    }
    setComments((prev) =>
      prev.map((c) =>
        c.id === parentId
          ? { ...c, replies: [...(c.replies ?? []), newReply] }
          : c,
      ),
    )
    setReplyText('')
    setReplyingTo(null)
  }

  function handleReply(commentId: number) {
    setReplyingTo((prev) => (prev === commentId ? null : commentId))
    setReplyText('')
  }

  const totalComments = comments.reduce((acc, c) => acc + 1 + (c.replies?.length ?? 0), 0)

  // Priority: status carried from Post List > fallback derived from comments
  const questionStatus: string | null =
    post.type === '질문·답변'
      ? (incomingStatus ?? (totalComments === 0 ? '답변 대기' : '답변 있음'))
      : null

  // Matches PostList badge style exactly — same color+border tokens, no background fill
  const statusStyle: Record<string, React.CSSProperties> = {
    '답변 대기': { color: '#92400E', border: '1px solid #92400E' },
    '답변 있음': { color: '#1E3A8A', border: '1px solid #1E3A8A' },
    '해결됨':   { color: '#166534', border: '1px solid #166534' },
  }

  return (
    <main id="main-content">
      <div style={{ maxWidth: 720, margin: '0 auto', padding: '40px 24px 72px' }}>

        {/* ─── BREADCRUMB ───────────────────────────────────────────── */}
        <nav aria-label="경로" style={{ marginBottom: 20 }}>
          <ol style={{ display: 'flex', alignItems: 'center', gap: 6, listStyle: 'none', padding: 0, margin: 0 }}>
            <li>
              <Link href="/community" style={{ fontSize: 13, color: '#908D88', textDecoration: 'none' }}>
                커뮤니티
              </Link>
            </li>
            <li aria-hidden="true" style={{ fontSize: 12, color: '#D0CEC9' }}>›</li>
            <li>
              <Link href="/community/posts" style={{ fontSize: 13, color: '#908D88', textDecoration: 'none' }}>
                전체 게시글
              </Link>
            </li>
          </ol>
        </nav>

        {/* ─── POST CONTEXT ─────────────────────────────────────────── */}
        <div className="flex items-center gap-1.5" style={{ marginBottom: 12 }}>
          <span style={{ fontSize: 13, fontWeight: 500, color: '#1E3A8A' }}>{post.type}</span>
          <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
          <span style={{ fontSize: 13, color: '#908D88' }}>{post.topic}</span>
          {questionStatus && (
            <>
              <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 500,
                  padding: '1px 6px',
                  borderRadius: 2,
                  ...statusStyle[questionStatus],
                }}
                aria-label={`질문 상태: ${questionStatus}`}
              >
                {questionStatus}
              </span>
            </>
          )}
        </div>

        {/* ─── TITLE (H1) ───────────────────────────────────────────── */}
        <h1
          style={{
            fontSize: 24,
            fontWeight: 700,
            lineHeight: 1.35,
            color: '#1A1918',
            margin: 0,
            marginBottom: 14,
          }}
        >
          {post.title}
        </h1>

        {/* ─── AUTHOR + DATE ────────────────────────────────────────── */}
        <div className="flex items-center gap-2" style={{ marginBottom: 32, paddingBottom: 20, borderBottom: '1px solid #E8E6E1' }}>
          <span style={{ fontSize: 14, fontWeight: 600, color: '#1A1918' }}>{post.author}</span>
          <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
          <time style={{ fontSize: 13, color: '#908D88' }} dateTime={post.date.replace(/\./g, '-')}>
            {post.date}
          </time>
          {post.edited && (
            <>
              <span style={{ color: '#D0CEC9', fontSize: 12 }} aria-hidden="true">·</span>
              <span style={{ fontSize: 12, color: '#908D88' }}>수정됨</span>
            </>
          )}
        </div>

        {/* ─── POST BODY ────────────────────────────────────────────── */}
        <article aria-label="게시글 본문" style={{ marginBottom: 36 }}>
          {post.body.map((para, i) => (
            <p
              key={i}
              style={{
                fontSize: 15,
                lineHeight: 1.8,
                color: '#1A1918',
                margin: 0,
                marginBottom: i < post.body.length - 1 ? 18 : 0,
              }}
            >
              {para}
            </p>
          ))}
          {cp?.previewImage && (
            <div style={{ marginTop: 24 }}>
              <img
                src={cp.previewImage}
                alt="첨부 이미지"
                style={{
                  maxWidth: '100%',
                  border: '1px solid #D0CEC9',
                  borderRadius: 2,
                  display: 'block',
                }}
              />
            </div>
          )}
        </article>

        {/* ─── POST ACTIONS ─────────────────────────────────────────── */}
        <div
          style={{ display: 'flex', alignItems: 'center', gap: 8, paddingTop: 20, paddingBottom: 20, borderTop: '1px solid #E8E6E1', borderBottom: '1px solid #E8E6E1', marginBottom: 40 }}
          role="group"
          aria-label="게시글 액션"
        >
          <ActionButton
            label="추천"
            activeLabel="추천됨"
            active={recommended}
            onClick={() => setRecommended((v) => !v)}
          />
          <ActionButton
            label="저장"
            activeLabel="저장됨"
            active={saved}
            onClick={() => setSaved((v) => !v)}
          />
          <button
            type="button"
            onClick={handleShare}
            style={{
              fontSize: 13,
              fontWeight: 500,
              padding: '6px 14px',
              border: '1px solid #D0CEC9',
              borderRadius: 2,
              backgroundColor: shareCopied ? '#F0EEE9' : 'transparent',
              color: '#4A4845',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
            aria-live="polite"
            aria-label={shareCopied ? '링크가 복사되었습니다' : '공유 — 링크 복사'}
          >
            {shareCopied ? '링크 복사됨' : '공유'}
          </button>

          <div style={{ marginLeft: 'auto' }}>
            <MoreMenu ariaLabel="게시글 메뉴" items={[{ label: '신고', danger: true, onClick: () => {} }]} />
          </div>
        </div>

        {/* ─── COMMENT SECTION ──────────────────────────────────────── */}
        <section aria-labelledby="comments-heading">
          <h2
            id="comments-heading"
            style={{ fontSize: 15, fontWeight: 600, color: '#1A1918', marginBottom: 20 }}
          >
            댓글 <span style={{ fontWeight: 400, color: '#908D88', fontSize: 14 }}>{totalComments}</span>
          </h2>

          {/* Comment form */}
          <form
            onSubmit={handleSubmitComment}
            aria-label="댓글 작성"
            style={{ marginBottom: 32, paddingBottom: 28, borderBottom: '1px solid #E8E6E1' }}
          >
            <label
              htmlFor="comment-textarea"
              style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#4A4845', marginBottom: 8 }}
            >
              댓글 작성
            </label>
            <textarea
              id="comment-textarea"
              ref={commentFormRef}
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="댓글을 입력하세요."
              rows={3}
              style={{
                width: '100%',
                border: '1.5px solid #D0CEC9',
                borderRadius: 2,
                padding: '10px 12px',
                fontSize: 14,
                lineHeight: 1.6,
                color: '#1A1918',
                backgroundColor: '#FAFAF8',
                resize: 'vertical',
                fontFamily: 'inherit',
                outline: 'none',
                boxSizing: 'border-box',
                marginBottom: 10,
              }}
              onFocus={(e) => (e.currentTarget.style.borderColor = '#1A1918')}
              onBlur={(e) => (e.currentTarget.style.borderColor = '#D0CEC9')}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button
                type="submit"
                disabled={!commentText.trim()}
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  padding: '7px 18px',
                  border: 'none',
                  borderRadius: 2,
                  backgroundColor: commentText.trim() ? '#1E3A8A' : '#D0CEC9',
                  color: '#FFFFFF',
                  cursor: commentText.trim() ? 'pointer' : 'not-allowed',
                  fontFamily: 'inherit',
                }}
              >
                등록
              </button>
            </div>
          </form>

          {/* Comment list */}
          <ol aria-label="댓글 목록" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {comments.map((comment) => (
              <CommentItem
                key={comment.id}
                comment={comment}
                onReply={handleReply}
              />
            ))}

            {/* Inline reply form — appears below the target comment */}
            {replyingTo !== null && (
              <li style={{ borderTop: '1px solid #E8E6E1' }}>
                <form
                  onSubmit={(e) => handleSubmitReply(e, replyingTo)}
                  aria-label="답글 작성"
                  style={{ paddingLeft: 24, borderLeft: '2px solid #E8E6E1', marginLeft: 16, padding: '12px 0 12px 24px' }}
                >
                  <label
                    htmlFor="reply-textarea"
                    style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#4A4845', marginBottom: 6 }}
                  >
                    답글 작성
                  </label>
                  <textarea
                    id="reply-textarea"
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    placeholder="답글을 입력하세요."
                    rows={2}
                    style={{
                      width: '100%',
                      border: '1.5px solid #D0CEC9',
                      borderRadius: 2,
                      padding: '8px 12px',
                      fontSize: 14,
                      lineHeight: 1.6,
                      color: '#1A1918',
                      backgroundColor: '#FAFAF8',
                      resize: 'vertical',
                      fontFamily: 'inherit',
                      outline: 'none',
                      boxSizing: 'border-box',
                      marginBottom: 8,
                    }}
                    onFocus={(e) => (e.currentTarget.style.borderColor = '#1A1918')}
                    onBlur={(e) => (e.currentTarget.style.borderColor = '#D0CEC9')}
                    autoFocus
                  />
                  <div className="flex items-center gap-2">
                    <button
                      type="submit"
                      disabled={!replyText.trim()}
                      style={{
                        fontSize: 12,
                        fontWeight: 600,
                        padding: '6px 14px',
                        border: 'none',
                        borderRadius: 2,
                        backgroundColor: replyText.trim() ? '#1E3A8A' : '#D0CEC9',
                        color: '#FFFFFF',
                        cursor: replyText.trim() ? 'pointer' : 'not-allowed',
                        fontFamily: 'inherit',
                      }}
                    >
                      등록
                    </button>
                    <button
                      type="button"
                      onClick={() => setReplyingTo(null)}
                      style={{
                        fontSize: 12,
                        padding: '6px 12px',
                        border: '1px solid #D0CEC9',
                        borderRadius: 2,
                        backgroundColor: 'transparent',
                        color: '#908D88',
                        cursor: 'pointer',
                        fontFamily: 'inherit',
                      }}
                    >
                      취소
                    </button>
                  </div>
                </form>
              </li>
            )}
          </ol>
        </section>

      </div>
    </main>
  )
}
