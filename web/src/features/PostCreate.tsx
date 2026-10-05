"use client";

import { useState, useRef, useEffect, useId } from 'react'
import { flushSync } from 'react-dom'
import { useRouter } from 'next/navigation';
import { useQueryParams } from '@/lib/useQueryParams';
import { useMockPosts } from '@/lib/mock-posts';
import { useUnsavedChanges } from '@/components/NavigationGuard';

// ─── Types ────────────────────────────────────────────────────────────────────

type PostType = '일반' | '질문·답변' | '경험·후기'
type TopicValue =
  | '자유·일상'
  | '보청기'
  | '인공와우'
  | '치료·재활'
  | '의사소통'
  | '취업·직장'
  | '복지·생활'

const POST_TYPES: PostType[] = ['일반', '질문·답변', '경험·후기']
const TOPICS: TopicValue[] = [
  '자유·일상',
  '보청기',
  '인공와우',
  '치료·재활',
  '의사소통',
  '취업·직장',
  '복지·생활',
]

interface AttachedImage {
  id: string
  file: File
  preview: string
  status: 'ready' | 'error'
}

interface FormErrors {
  type?: string
  topic?: string
  title?: string
  body?: string
  image?: string
}

// ─── Unsaved Changes Dialog ───────────────────────────────────────────────────

function UnsavedDialog({
  onContinue,
  onLeave,
}: {
  onContinue: () => void
  onLeave: () => void
}) {
  const panelRef = useRef<HTMLDivElement>(null)
  const continueRef = useRef<HTMLButtonElement>(null)
  const leaveRef = useRef<HTMLButtonElement>(null)
  const prevFocusRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    prevFocusRef.current = document.activeElement as HTMLElement
    // Focus the safe action on open
    continueRef.current?.focus()

    function handleKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault()
        onContinue()
        return
      }
      if (e.key === 'Tab') {
        const buttons = [leaveRef.current, continueRef.current].filter(Boolean) as HTMLButtonElement[]
        if (buttons.length < 2) return
        const [first, last] = [buttons[0], buttons[buttons.length - 1]]
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    }

    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('keydown', handleKey)
      // Restore focus when dialog closes via 계속 작성
      prevFocusRef.current?.focus()
    }
  }, [onContinue])

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="unsaved-title"
      aria-describedby="unsaved-desc"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 50,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {/* Backdrop */}
      <div
        onClick={onContinue}
        style={{ position: 'absolute', inset: 0, backgroundColor: 'rgba(26,25,24,0.4)' }}
        aria-hidden="true"
      />
      {/* Panel */}
      <div
        ref={panelRef}
        style={{
          position: 'relative',
          backgroundColor: '#F6F6F4',
          border: '1px solid #D0CEC9',
          borderRadius: 2,
          padding: '32px 28px 24px',
          maxWidth: 380,
          width: '100%',
        }}
      >
        <p
          id="unsaved-title"
          style={{ fontSize: 16, fontWeight: 700, color: '#1A1918', marginBottom: 8 }}
        >
          작성 중인 내용이 있습니다.
        </p>
        <p
          id="unsaved-desc"
          style={{ fontSize: 14, color: '#4A4845', lineHeight: 1.6, marginBottom: 28 }}
        >
          페이지를 나가면 입력한 내용이 사라집니다.
        </p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button
            ref={leaveRef}
            type="button"
            onClick={onLeave}
            aria-label="나가기 — 입력한 내용이 사라집니다"
            style={{
              fontSize: 13,
              fontWeight: 500,
              padding: '8px 16px',
              border: '1px solid #D0CEC9',
              borderRadius: 2,
              backgroundColor: 'transparent',
              color: '#B91C1C',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            나가기
          </button>
          <button
            ref={continueRef}
            type="button"
            onClick={onContinue}
            style={{
              fontSize: 13,
              fontWeight: 600,
              padding: '8px 16px',
              border: 'none',
              borderRadius: 2,
              backgroundColor: '#1E3A8A',
              color: '#FFFFFF',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            계속 작성
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Field Error ──────────────────────────────────────────────────────────────

function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null
  return (
    <p
      id={id}
      role="alert"
      style={{ fontSize: 12, color: '#B91C1C', marginTop: 6, marginBottom: 0 }}
    >
      {message}
    </p>
  )
}

// ─── Screen ──────────────────────────────────────────────────────────────────

export default function PostCreate() {
  const router = useRouter()
  const {add} = useMockPosts()
  const [searchParams] = useQueryParams()
  const uid = useId()

  const paramType = searchParams.get('type') as PostType | null
  const paramTopic = searchParams.get('topic') as TopicValue | null

  const [postType, setPostType] = useState<PostType | ''>(() =>
    POST_TYPES.includes(paramType as PostType) ? (paramType as PostType) : '',
  )
  const [topic, setTopic] = useState<TopicValue | ''>(() =>
    TOPICS.includes(paramTopic as TopicValue) ? (paramTopic as TopicValue) : '',
  )
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [images, setImages] = useState<AttachedImage[]>([])
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)

  const [isDirty, setIsDirty] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const imagesRef = useRef<AttachedImage[]>([])
  const transferredPreviewRef = useRef<string | null>(null)
  useEffect(() => {imagesRef.current = images}, [images])

  function markDirty() { setIsDirty(true) }

  // ── Router navigation blocker ──────────────────────────────────────────────
  // Intercepts Link clicks (GNB, Logo, Footer), router.push(), and browser back.
  const blocker = useUnsavedChanges(isDirty)

  // Derived: are required fields complete?
  const isComplete =
    postType !== '' && topic !== '' && title.trim() !== '' && body.trim() !== ''

  // ── Validation ─────────────────────────────────────────────────────────────

  function validate(): FormErrors {
    const errs: FormErrors = {}
    if (!postType) errs.type = '글 유형을 선택해 주세요.'
    if (!topic) errs.topic = '주제를 선택해 주세요.'
    if (!title.trim()) errs.title = '제목을 입력해 주세요.'
    if (!body.trim()) errs.body = '내용을 입력해 주세요.'
    return errs
  }

  function validateField(field: keyof FormErrors, value: string) {
    setErrors((prev) => {
      const msgs: Record<string, string> = {
        type: '글 유형을 선택해 주세요.',
        topic: '주제를 선택해 주세요.',
        title: '제목을 입력해 주세요.',
        body: '내용을 입력해 주세요.',
      }
      if (value.trim()) {
        if (!prev[field]) return prev        // no change — bail out
        const next = { ...prev }
        delete next[field]
        return next
      } else if (field !== 'image') {
        const msg = msgs[field]
        if (prev[field] === msg) return prev // no change — bail out
        return { ...prev, [field]: msg }
      }
      return prev
    })
  }

  // ── Image handling ─────────────────────────────────────────────────────────

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (!files.length) return
    markDirty()

    const allowed = ['image/jpeg', 'image/png', 'image/webp']
    const invalid = files.filter((f) => !allowed.includes(f.type))

    if (invalid.length) {
      setErrors((prev) => ({
        ...prev,
        image: '지원하지 않는 이미지 형식입니다. (JPG, JPEG, PNG, WEBP만 가능)',
      }))
    }

    const valid = files.filter((f) => allowed.includes(f.type))
    const newImages: AttachedImage[] = valid.map((file) => ({
      id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file,
      preview: URL.createObjectURL(file),
      status: 'ready',
    }))
    setImages((prev) => [...prev, ...newImages])
    e.target.value = ''
  }

  function removeImage(id: string) {
    setImages((prev) => {
      const img = prev.find((i) => i.id === id)
      if (img) URL.revokeObjectURL(img.preview)
      return prev.filter((i) => i.id !== id)
    })
  }

  // Keep the submitted preview alive for the mock detail screen; release unused files.
  useEffect(() => () => {
    imagesRef.current.forEach(img => {
      if (img.preview !== transferredPreviewRef.current) URL.revokeObjectURL(img.preview)
    })
  }, [])

  // ── Submit ─────────────────────────────────────────────────────────────────

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const errs = validate()
    if (Object.keys(errs).length) {
      setErrors(errs)
      return
    }
    setSubmitting(true)
    await new Promise((res) => setTimeout(res, 1200))
    const now = new Date()
    const date = `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`
    const createdPost = {
      type: postType,
      topic,
      title,
      bodyText: body,
      author: '나 (로그인 중)',
      date,
      previewImage: images[0]?.preview ?? null,
    }
    // flushSync ensures isDirty is false in DOM before navigate fires the blocker
    flushSync(() => {
      setIsDirty(false)
      setSubmitting(false)
    })
    const id = String(now.getTime())
    transferredPreviewRef.current = createdPost.previewImage
    add(id, createdPost)
    router.push(`/community/posts/${id}`)
  }

  // ── Cancel ─────────────────────────────────────────────────────────────────
  // Cancel uses the same unsaved-content dialog as links and browser Back.

  function handleCancel() {
    blocker.request(() => router.back())
  }

  // ── Render helpers ─────────────────────────────────────────────────────────

  const topicId = `${uid}-topic`
  const titleId = `${uid}-title`
  const bodyId = `${uid}-body`
  const typeErrId = `${uid}-type-err`
  const topicErrId = `${uid}-topic-err`
  const titleErrId = `${uid}-title-err`
  const bodyErrId = `${uid}-body-err`
  const imageErrId = `${uid}-image-err`

  return (
    <>
      <main id="main-content">
        <div style={{ maxWidth: 720, margin: '0 auto', padding: '40px 24px 72px' }}>

          {/* ─── PAGE HEADER ─────────────────────────────────────────── */}
          <h1
            style={{
              fontSize: 22,
              fontWeight: 700,
              color: '#1A1918',
              marginBottom: 36,
              paddingBottom: 20,
              borderBottom: '2px solid #1A1918',
            }}
          >
            게시글 작성
          </h1>

          <form onSubmit={handleSubmit} noValidate aria-label="게시글 작성 폼">

            {/* ─── 글 유형 ───────────────────────────────────────────── */}
            <fieldset
              style={{ border: 'none', padding: 0, marginBottom: 28 }}
              aria-describedby={errors.type ? typeErrId : undefined}
            >
              <legend
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1A1918',
                  marginBottom: 10,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                글 유형
                <span style={{ fontSize: 12, color: '#B91C1C', fontWeight: 400 }}>(필수)</span>
              </legend>

              <div style={{ display: 'flex', gap: 10 }}>
                {POST_TYPES.map((t) => {
                  const selected = postType === t
                  return (
                    <label
                      key={t}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '9px 16px',
                        border: selected ? '2px solid #1E3A8A' : '1px solid #D0CEC9',
                        borderRadius: 2,
                        cursor: 'pointer',
                        backgroundColor: selected ? '#EEF2FF' : 'transparent',
                        fontSize: 13,
                        fontWeight: selected ? 600 : 400,
                        color: selected ? '#1E3A8A' : '#1A1918',
                        userSelect: 'none',
                      }}
                    >
                      <input
                        type="radio"
                        name="postType"
                        value={t}
                        checked={selected}
                        required
                        onChange={() => {
                          setPostType(t)
                          markDirty()
                          if (errors.type) setErrors((prev) => { const n = { ...prev }; delete n.type; return n })
                        }}
                        style={{ accentColor: '#1E3A8A' }}
                      />
                      {t}
                    </label>
                  )
                })}
              </div>

              <FieldError id={typeErrId} message={errors.type} />
            </fieldset>

            {/* ─── 주제 ──────────────────────────────────────────────── */}
            <div style={{ marginBottom: 28 }}>
              <label
                htmlFor={topicId}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1A1918',
                  marginBottom: 8,
                }}
              >
                주제
                <span style={{ fontSize: 12, color: '#B91C1C', fontWeight: 400 }}>(필수)</span>
              </label>
              <select
                id={topicId}
                value={topic}
                required
                aria-describedby={errors.topic ? topicErrId : undefined}
                onChange={(e) => {
                  setTopic(e.target.value as TopicValue | '')
                  markDirty()
                  if (errors.topic) setErrors((prev) => { const n = { ...prev }; delete n.topic; return n })
                }}
                style={{
                  width: '100%',
                  maxWidth: 280,
                  border: errors.topic ? '1.5px solid #B91C1C' : '1.5px solid #D0CEC9',
                  borderRadius: 2,
                  padding: '9px 12px',
                  fontSize: 13,
                  color: topic ? '#1A1918' : '#908D88',
                  backgroundColor: '#FAFAF8',
                  fontFamily: 'inherit',
                  cursor: 'pointer',
                  outline: 'none',
                  appearance: 'auto',
                }}
                onFocus={(e) => { e.currentTarget.style.borderColor = '#1A1918' }}
                onBlur={(e) => {
                  e.currentTarget.style.borderColor = errors.topic ? '#B91C1C' : '#D0CEC9'
                  validateField('topic', topic)
                }}
              >
                <option value="">주제 선택</option>
                {TOPICS.map((tp) => (
                  <option key={tp} value={tp}>{tp}</option>
                ))}
              </select>
              <FieldError id={topicErrId} message={errors.topic} />
            </div>

            {/* ─── 제목 ──────────────────────────────────────────────── */}
            <div style={{ marginBottom: 28 }}>
              <label
                htmlFor={titleId}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1A1918',
                  marginBottom: 8,
                }}
              >
                제목
                <span style={{ fontSize: 12, color: '#B91C1C', fontWeight: 400 }}>(필수)</span>
              </label>
              <input
                id={titleId}
                type="text"
                value={title}
                required
                maxLength={200}
                placeholder="제목을 입력하세요."
                aria-describedby={errors.title ? titleErrId : undefined}
                onChange={(e) => {
                  setTitle(e.target.value)
                  markDirty()
                  if (errors.title && e.target.value.trim()) {
                    setErrors((prev) => { const n = { ...prev }; delete n.title; return n })
                  }
                }}
                onBlur={(e) => {
                  e.currentTarget.style.borderColor = errors.title ? '#B91C1C' : '#D0CEC9'
                  validateField('title', title)
                }}
                onFocus={(e) => { e.currentTarget.style.borderColor = '#1A1918' }}
                style={{
                  width: '100%',
                  border: errors.title ? '1.5px solid #B91C1C' : '1.5px solid #D0CEC9',
                  borderRadius: 2,
                  padding: '10px 12px',
                  fontSize: 14,
                  color: '#1A1918',
                  backgroundColor: '#FAFAF8',
                  fontFamily: 'inherit',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
              <FieldError id={titleErrId} message={errors.title} />
            </div>

            {/* ─── 내용 ──────────────────────────────────────────────── */}
            <div style={{ marginBottom: 28 }}>
              <label
                htmlFor={bodyId}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1A1918',
                  marginBottom: 8,
                }}
              >
                내용
                <span style={{ fontSize: 12, color: '#B91C1C', fontWeight: 400 }}>(필수)</span>
              </label>
              <textarea
                id={bodyId}
                value={body}
                required
                rows={10}
                placeholder="내용을 입력하세요."
                aria-describedby={errors.body ? bodyErrId : undefined}
                onChange={(e) => {
                  setBody(e.target.value)
                  markDirty()
                  if (errors.body && e.target.value.trim()) {
                    setErrors((prev) => { const n = { ...prev }; delete n.body; return n })
                  }
                }}
                onBlur={(e) => {
                  e.currentTarget.style.borderColor = errors.body ? '#B91C1C' : '#D0CEC9'
                  validateField('body', body)
                }}
                onFocus={(e) => { e.currentTarget.style.borderColor = '#1A1918' }}
                style={{
                  width: '100%',
                  border: errors.body ? '1.5px solid #B91C1C' : '1.5px solid #D0CEC9',
                  borderRadius: 2,
                  padding: '10px 12px',
                  fontSize: 14,
                  lineHeight: 1.7,
                  color: '#1A1918',
                  backgroundColor: '#FAFAF8',
                  resize: 'vertical',
                  fontFamily: 'inherit',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
              <FieldError id={bodyErrId} message={errors.body} />
            </div>

            {/* ─── 이미지 첨부 ────────────────────────────────────────── */}
            <div style={{ marginBottom: 40, paddingBottom: 36, borderBottom: '1px solid #E8E6E1' }}>
              <p style={{ fontSize: 13, fontWeight: 600, color: '#1A1918', marginBottom: 4 }}>
                이미지 첨부{' '}
                <span style={{ fontSize: 12, fontWeight: 400, color: '#908D88' }}>(선택)</span>
              </p>
              <p style={{ fontSize: 12, color: '#908D88', marginBottom: 12, lineHeight: 1.5 }}>
                JPG, JPEG, PNG, WEBP 형식을 지원합니다.
              </p>

              <input
                ref={fileInputRef}
                type="file"
                id={`${uid}-file`}
                accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                multiple
                onChange={handleFileSelect}
                style={{
                  position: 'absolute',
                  width: 1,
                  height: 1,
                  overflow: 'hidden',
                  clip: 'rect(0,0,0,0)',
                  whiteSpace: 'nowrap',
                }}
                aria-describedby={errors.image ? imageErrId : undefined}
              />
              <label
                htmlFor={`${uid}-file`}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 13,
                  fontWeight: 500,
                  padding: '8px 16px',
                  border: '1px solid #D0CEC9',
                  borderRadius: 2,
                  backgroundColor: 'transparent',
                  color: '#4A4845',
                  cursor: 'pointer',
                }}
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                  <path
                    d="M7 1v8M3.5 4.5L7 1l3.5 3.5"
                    stroke="#4A4845"
                    strokeWidth="1.4"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                  <path
                    d="M1 10v1.5A1.5 1.5 0 002.5 13h9a1.5 1.5 0 001.5-1.5V10"
                    stroke="#4A4845"
                    strokeWidth="1.4"
                    strokeLinecap="round"
                  />
                </svg>
                파일 선택
              </label>

              <FieldError id={imageErrId} message={errors.image} />

              {images.length > 0 && (
                <ul
                  aria-label="첨부된 이미지"
                  style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 10,
                    marginTop: 14,
                    padding: 0,
                    listStyle: 'none',
                  }}
                >
                  {images.map((img) => (
                    <li key={img.id} style={{ position: 'relative' }}>
                      <img
                        src={img.preview}
                        alt={img.file.name}
                        style={{
                          width: 80,
                          height: 80,
                          objectFit: 'cover',
                          border: '1px solid #D0CEC9',
                          borderRadius: 2,
                          display: 'block',
                        }}
                      />
                      <button
                        type="button"
                        onClick={() => removeImage(img.id)}
                        aria-label={`${img.file.name} 삭제`}
                        style={{
                          position: 'absolute',
                          top: -6,
                          right: -6,
                          width: 20,
                          height: 20,
                          borderRadius: '50%',
                          border: '1px solid #D0CEC9',
                          backgroundColor: '#F6F6F4',
                          color: '#4A4845',
                          fontSize: 11,
                          fontWeight: 700,
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontFamily: 'inherit',
                          lineHeight: 1,
                        }}
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              <p
                style={{
                  marginTop: 14,
                  fontSize: 12,
                  color: '#908D88',
                  lineHeight: 1.55,
                  paddingLeft: 10,
                  borderLeft: '2px solid #E8E6E1',
                }}
              >
                이미지에 위치, 기기 식별번호, 개인정보 등이 포함되지 않도록 주의해 주세요.
              </p>
            </div>

            {/* ─── ACTIONS ───────────────────────────────────────────── */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, alignItems: 'center' }}>
              <button
                type="button"
                onClick={handleCancel}
                disabled={submitting}
                style={{
                  fontSize: 13,
                  fontWeight: 500,
                  padding: '9px 20px',
                  border: '1px solid #D0CEC9',
                  borderRadius: 2,
                  backgroundColor: 'transparent',
                  color: '#4A4845',
                  cursor: submitting ? 'not-allowed' : 'pointer',
                  fontFamily: 'inherit',
                  opacity: submitting ? 0.5 : 1,
                }}
              >
                취소
              </button>
              <button
                type="submit"
                disabled={!isComplete || submitting}
                aria-live="polite"
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  padding: '9px 24px',
                  border: 'none',
                  borderRadius: 2,
                  backgroundColor: isComplete && !submitting ? '#1E3A8A' : '#D0CEC9',
                  color: '#FFFFFF',
                  cursor: isComplete && !submitting ? 'pointer' : 'not-allowed',
                  fontFamily: 'inherit',
                  minWidth: 72,
                }}
              >
                {submitting ? '등록 중…' : '등록'}
              </button>
            </div>

          </form>
        </div>
      </main>

      {/* ─── UNSAVED CHANGES DIALOG ─────────────────────────────────────── */}
      {/* Shown whenever the router blocker intercepts a navigation attempt  */}
      {blocker.state === 'blocked' && (
        <UnsavedDialog
          onContinue={() => blocker.reset?.()}
          onLeave={() => blocker.proceed?.()}
        />
      )}
    </>
  )
}
