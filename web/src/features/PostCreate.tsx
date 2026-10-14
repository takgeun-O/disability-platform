"use client"
// 로그아웃 이탈 동의 뒤 같은 화면에서 새로 입력한 내용도 다시 보호한다.
// 이미 이탈을 승인했다면 지연된 응답으로 화면을 이동하지 않는다.
// 세션이 만료되거나 로그아웃되어도 이미 입력한 폼은 화면에 남긴다.
import { useEffect, useRef, useState, type FormEvent } from "react"
import { flushSync } from "react-dom"
import { useRouter, useSearchParams } from "next/navigation"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import { useUnsavedChanges } from "@/components/NavigationGuard"
import CommunityDialog from "@/components/community/CommunityDialog"
import { CommunityBanner } from "@/components/community/CommunityParts"
import { CommunityApiError, createPost } from "@/lib/community-api"
import {
  POST_TYPES,
  POST_TOPICS,
  parsePostQuery,
  safeListReturn,
  listHref,
  characterCount,
  normalizeTitle,
  validatePostForm,
  type PostForm,
  type PostField,
  type PostFieldErrors,
} from "@/lib/community-posts"

export default function PostCreate() {
  const router = useRouter()
  const params = useSearchParams()
  const auth = useAuth()
  const back = safeListReturn(params.get("from"))
  const [values, setValues] = useState<PostForm>(() => {
    const { type, topic } = parsePostQuery(params)
    return { type, topic, title: "", content: "" }
  })
  const [errors, setErrors] = useState<PostFieldErrors>({})
  const [feedback, setFeedback] = useState<CommunityApiError | null>(null)
  const [submittedListHref, setSubmittedListHref] = useState("/community/posts")
  const [submitting, setSubmitting] = useState(false)
  const [dirty, setDirty] = useState(false)
  const blocker = useUnsavedChanges(dirty)
  const formRef = useRef<HTMLFormElement>(null)
  const feedbackRef = useRef<HTMLDivElement>(null)
  const inFlight = useRef(false)
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  useEffect(() => {
    const first = Object.keys(errors)[0]
    if (first)
      formRef.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus()
    else if (feedback) feedbackRef.current?.focus()
  }, [errors, feedback])
  const from =
    "/community/posts/create" +
    (params.size
      ? "?" +
        params.toString()
      : "")
  const loginHref =
    "/login?from=" +
    encodeURIComponent(from)

  function edit(field: PostField, value: string) {
    setValues((previous) => ({ ...previous, [field]: value }))
    setDirty(true)
    blocker.markDirty()
  }
  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (
      inFlight.current ||
      auth.pending ||
      auth.status !==
        "authenticated"
    )
      return
    const nextErrors = validatePostForm(values)
    setErrors(nextErrors)
    setFeedback(null)
    if (Object.keys(nextErrors).length) return
    // 결과 확인은 복귀 경로/현재 편집값이 아닌 이번 요청의 조건으로 고정한다.
    const submitted = { ...values }
    const approvalVersion = blocker.getApprovalVersion()
    const canHandleResult = () => mounted.current && blocker.getApprovalVersion() === approvalVersion
    setSubmittedListHref(listHref({ type: submitted.type, topic: submitted.topic, page: 0 }))
    inFlight.current = true
    setSubmitting(true)
    try {
      const result = await createPost(submitted)
      // Next가 목적지를 읽는 동안 폼이 마운트되어 있어도 승인된 이탈을 덮어쓰지 않는다.
      if (!canHandleResult()) return
      flushSync(() => {
        setDirty(false)
        setSubmitting(false)
      })
      router.replace(
        `/community/posts/${result.id}?from=${encodeURIComponent(back)}`,
      )
    } catch (error) {
      if (!canHandleResult()) return
      const failure =
        error instanceof
        CommunityApiError
          ? error
          : new CommunityApiError(
              0,
              "UNEXPECTED_RESPONSE",
              "등록 결과를 확인하지 못했습니다. 목록을 확인해 주세요.",
              {},
              true,
            )
      setErrors(failure.fieldErrors)
      setFeedback(failure)
      if (
        failure.status ===
        401
      )
        void auth.refresh()
    } finally {
      // 요청 종료의 busy 상태만 정리한다. 승인 후 실패 안내/초점/인증 갱신은 실행하지 않는다.
      inFlight.current = false
      if (mounted.current) setSubmitting(false)
    }
  }
  const field = (name: PostField) => ({
    id: `post-${name}`,
    name,
    "aria-invalid": !!errors[name],
    "aria-describedby": errors[name] ? `post-${name}-error` : undefined,
  })
  const errorText = (name: PostField) =>
    errors[name] && (
      <p className="iyum-field-error" id={`post-${name}-error`}>
        {errors[name]}
      </p>
    )
  const showForm = auth.status === "authenticated" || dirty
  return (
    <main id="main-content" className="community-screen">
      <CommunityBanner
        title="게시글 작성"
        description="궁금한 점과 나누고 싶은 경험을 들려주세요."
      />
      <div className="iyum-container community-writing">
        <Link
          className="iyum-text-link community-back"
          href={back}
          scroll={false}
        >
          ← 목록으로
        </Link>
        {auth.status === "checking" && (
          <p className="community-session-note" role="status">
            로그인 상태를 확인하고 있습니다.
          </p>
        )}
        {auth.status === "error" && (
          <div className="community-error" role="alert">
            <p>로그인 상태를 확인하지 못했습니다. 입력 내용은 유지됩니다.</p>
            <button
              className="iyum-button iyum-button-outline"
              onClick={() => void auth.refresh()}
            >
              로그인 상태 다시 확인
            </button>
          </div>
        )}
        {auth.status === "anonymous" && (
          <div className="community-session-note">
            <h2>로그인 후 글을 작성할 수 있어요.</h2>
            <p>
              {dirty
                ? "작성한 내용은 이 화면에 유지됩니다. 로그인 화면으로 이동하면 이탈 확인을 거칩니다."
                : "게시글은 누구나 읽을 수 있습니다. 로그인한 뒤 이야기를 나눠 주세요."}
            </p>
            <Link className="iyum-button iyum-button-primary" href={loginHref}>
              로그인하고 글쓰기
            </Link>
          </div>
        )}
        {showForm && (
          <form
            className="community-create-form"
            ref={formRef}
            onSubmit={handleSubmit}
            noValidate
            aria-label="게시글 작성"
          >
            <p className="community-help">
              모든 항목을 입력해 주세요. 이미지 첨부 기능은 준비 중입니다.
            </p>
            {feedback && (
              <div
                className="community-error"
                role="alert"
                tabIndex={-1}
                ref={feedbackRef}
              >
                <p>{feedback.message}</p>
                {feedback.uncertain && (
                  <Link
                    className="iyum-text-link"
                    href={submittedListHref}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    새 탭에서 목록 확인
                  </Link>
                )}
              </div>
            )}
            <fieldset disabled={submitting}>
              <legend className="sr-only">게시글 내용</legend>
              <div className="community-form-grid">
                <div>
                  <label htmlFor="post-type">글 유형</label>
                  <select
                    {...field("type")}
                    value={values.type}
                    onChange={(e) => edit("type", e.target.value)}
                  >
                    <option value="">선택해 주세요</option>
                    {Object.entries(POST_TYPES).map(([key, label]) => (
                      <option key={key} value={key}>
                        {label}
                      </option>
                    ))}
                  </select>
                  {errorText("type")}
                </div>
                <div>
                  <label htmlFor="post-topic">주제</label>
                  <select
                    {...field("topic")}
                    value={values.topic}
                    onChange={(e) => edit("topic", e.target.value)}
                  >
                    <option value="">선택해 주세요</option>
                    {Object.entries(POST_TOPICS).map(([key, label]) => (
                      <option key={key} value={key}>
                        {label}
                      </option>
                    ))}
                  </select>
                  {errorText("topic")}
                </div>
              </div>
              <div>
                <div className="community-field-heading">
                  <label htmlFor="post-title">제목</label>
                  <span>
                    {characterCount(normalizeTitle(values.title))} / 120자
                  </span>
                </div>
                <input
                  {...field("title")}
                  value={values.title}
                  placeholder="제목을 입력하세요."
                  onChange={(e) => edit("title", e.target.value)}
                />
                {errorText("title")}
              </div>
              <div>
                <div className="community-field-heading">
                  <label htmlFor="post-content">내용</label>
                  <span>{characterCount(values.content)} / 10,000자</span>
                </div>
                <textarea
                  {...field("content")}
                  value={values.content}
                  rows={12}
                  placeholder="내용을 입력하세요."
                  onChange={(e) => edit("content", e.target.value)}
                />
                {errorText("content")}
              </div>
            </fieldset>
            <p className="community-help">
              줄바꿈은 그대로 저장됩니다. 개인정보나 연락처가 포함되지 않았는지
              확인해 주세요.
            </p>
            <div className="community-form-actions">
              <button
                type="button"
                className="iyum-button iyum-button-outline"
                onClick={() =>
                  blocker.request(() => router.push(back, { scroll: false }))
                }
              >
                취소
              </button>
              <button
                className="iyum-button iyum-button-primary"
                type="submit"
                disabled={
                  submitting ||
                  auth.status !== "authenticated" ||
                  !!auth.pending
                }
              >
                {submitting ? "등록 중…" : "등록"}
              </button>
            </div>
            <p className="sr-only" role="status">
              {submitting
                ? "게시글을 등록하고 있습니다. 잠시 기다려 주세요."
                : ""}
            </p>
          </form>
        )}
      </div>
      {blocker.state === "blocked" && (
        <CommunityDialog
          titleId="unsaved-title"
          descriptionId="unsaved-desc"
          onCancel={blocker.reset}
        >
          <h2 id="unsaved-title">작성 중인 내용이 있습니다.</h2>
          <p id="unsaved-desc">
            페이지를 나가면 입력한 내용이 사라집니다.
            {submitting ? " 등록 요청은 이미 처리되고 있을 수 있습니다." : ""}
          </p>
          <div className="community-dialog-actions">
            <button
              className="iyum-button iyum-button-outline"
              type="button"
              onClick={blocker.proceed}
              aria-label="나가기 — 입력한 내용이 사라집니다"
            >
              나가기
            </button>
            <button
              data-initial-focus
              className="iyum-button iyum-button-primary"
              type="button"
              onClick={blocker.reset}
            >
              계속 작성
            </button>
          </div>
        </CommunityDialog>
      )}
    </main>
  )
}
