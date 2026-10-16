"use client"
// 폼은 조회 성공 후 별도 컴포넌트에 고정하며 재시도는 오류 화면에서만 제공한다.
import { useEffect, useRef, useState, type FormEvent } from "react"
import { flushSync } from "react-dom"
import { useRouter, useSearchParams } from "next/navigation"
import Link from "@/components/AppLink"
import { useAuth } from "@/components/AuthProvider"
import {
  useNavigationRequest,
  useUnsavedChanges,
} from "@/components/NavigationGuard"
import { CommunityBanner } from "@/components/community/CommunityParts"
import CommunityDialog from "@/components/community/CommunityDialog"
import PostFields from "@/components/community/PostFields"
import {
  CommunityApiError,
  getPost,
  changePost,
  type PostDetail,
} from "@/lib/community-api"
import { isMissingPost, type InitialPostDetail } from "@/lib/community-load"
import { postChanged, usePostVersion } from "@/lib/community-mutations"
import {
  safeListReturn,
  validatePostForm,
  type PostForm,
  type PostField,
  type PostFieldErrors,
} from "@/lib/community-posts"
import { topicByValue, topicPath } from "@/lib/community-topics"

export default function PostEdit({
  id,
  initial,
}: {
  id: string
  initial: InitialPostDetail
}) {
  const auth = useAuth(),
    scope = useNavigationRequest(),
    params = useSearchParams()
  const [post, setPost] = useState(initial.data)
  const [failure, setFailure] = useState(initial.error)
  const [retry, setRetry] = useState(0)
  const mutationVersion = usePostVersion(id)
  // 진입 시 알려진 변경만 조회한다. 폼을 연 뒤의 변경 알림은 이전 요청의
  // 완료일 수 있으므로 현재 입력과 dirty의 원본 스냅샷을 교체하지 않는다.
  // 새 진입은 새 버전을 확인하고, 상세·목록은 계속 변경 알림을 구독한다.
  const [version] = useState(mutationVersion)
  const [loading, setLoading] = useState(false)
  const [loadedVersion, setLoadedVersion] = useState(0)
  useEffect(() => {
    if (!retry && !version) return
    const controller = new AbortController(),
      current = scope.begin()
    getPost(id, controller.signal)
      .then((data) => {
        if (current() && !controller.signal.aborted) {
          setPost(data)
          setFailure(null)
          setLoading(false)
          setLoadedVersion(version)
        }
      })
      .catch((error) => {
        if (current() && !controller.signal.aborted) {
          setFailure(error)
          setLoading(false)
          setLoadedVersion(version)
        }
      })
    return () => controller.abort()
  }, [id, retry, version, scope])
  const space = post && topicByValue(post.topic)
  const back = safeListReturn(
    params.get("from"),
    space ? topicPath(space) : "/community/posts",
  )
  const detail = `/community/posts/${id}?from=${encodeURIComponent(back)}`
  const editPath = `/community/posts/${id}/edit?from=${encodeURIComponent(back)}`
  return (
    <main id="main-content" className="community-screen">
      <CommunityBanner
        title="게시글 수정"
        description="나눠 주신 이야기를 수정해 주세요."
      />
      <div className="iyum-container community-writing">
        <Link className="iyum-text-link community-back" href={detail}>
          ← 게시글로
        </Link>
        {loading || loadedVersion !== version ? (
          <p role="status">게시글을 불러오고 있습니다.</p>
        ) : failure ? (
          <section className="community-error" role="alert">
            <h2>
              {isMissingPost(failure)
                ? "게시글을 찾을 수 없어요."
                : "게시글을 불러오지 못했어요."}
            </h2>
            <p>{failure.message}</p>
            {isMissingPost(failure) ? (
              <Link href={back}>목록으로</Link>
            ) : (
              <button
                className="iyum-button iyum-button-outline"
                onClick={() => {
                  setLoading(true)
                  setRetry((n) => n + 1)
                }}
              >
                다시 시도
              </button>
            )}
          </section>
        ) : (
          post && (
            <EditForm
              key={post.id}
              post={post}
              detail={detail}
              editPath={editPath}
            />
          )
        )}
        {!post && !failure && <p role="status">게시글을 확인하고 있습니다.</p>}
        {auth.status === "error" && (
          <button
            className="iyum-button iyum-button-outline"
            onClick={() => void auth.refresh()}
          >
            로그인 상태 다시 확인
          </button>
        )}
      </div>
    </main>
  )
}
function EditForm({
  post,
  detail,
  editPath,
}: {
  post: PostDetail
  detail: string
  editPath: string
}) {
  const auth = useAuth(),
    router = useRouter(),
    scope = useNavigationRequest()
  const [values, setValues] = useState<PostForm>(() => ({
    type: post.type,
    topic: post.topic,
    title: post.title,
    content: post.content,
  }))
  const [saved, setSaved] = useState(false)
  const dirty =
    !saved &&
    (Object.keys(values) as PostField[]).some(
      (key) => values[key] !== post[key],
    )
  const blocker = useUnsavedChanges(dirty)
  const [errors, setErrors] = useState<PostFieldErrors>({}),
    [error, setError] = useState<CommunityApiError | null>(null)
  const [busy, setBusy] = useState(false),
    inFlight = useRef(false)
  const form = useRef<HTMLFormElement>(null),
    alert = useRef<HTMLDivElement>(null)
  const owner =
    auth.status === "authenticated" &&
    auth.member?.memberId === post.author.memberId
  useEffect(() => {
    if (!scope.isActive()) return
    const first = Object.keys(errors)[0]
    if (first)
      form.current?.querySelector<HTMLElement>(`[name="${first}"]`)?.focus()
    else if (error) alert.current?.focus()
  }, [errors, error, scope])
  function edit(field: PostField, value: string) {
    setValues((v) => ({ ...v, [field]: value }))
    blocker.markDirty()
  }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (inFlight.current || !owner || auth.pending || !scope.isActive()) return
    const fields = validatePostForm(values)
    setErrors(fields)
    setError(null)
    if (Object.keys(fields).length) return
    const current = scope.begin(),
      submitted = { ...values }
    inFlight.current = true
    setBusy(true)
    try {
      if (!(await changePost(String(post.id), "PUT", submitted, current)))
        return
      if (!current()) {
        postChanged(String(post.id))
        return
      }
      flushSync(() => {
        setSaved(true)
        setBusy(false)
      })
      router.replace(detail)
      postChanged(String(post.id))
    } catch (e) {
      if (!current()) return
      const failure =
        e instanceof CommunityApiError
          ? e
          : new CommunityApiError(
              0,
              "UNEXPECTED_RESPONSE",
              "수정 결과를 확인하지 못했습니다. 입력 내용은 유지됩니다.",
              {},
              true,
            )
      setErrors(failure.fieldErrors)
      setError(failure)
      if (failure.status === 401) void auth.refresh()
    } finally {
      inFlight.current = false
      if (current()) setBusy(false)
    }
  }
  return (
    <>
      {auth.status === "checking" && (
        <p role="status">로그인 상태를 확인하고 있습니다.</p>
      )}
      {auth.status === "anonymous" && (
        <div className="community-session-note">
          <h2>로그인 후 게시글을 수정할 수 있어요.</h2>
          <p>
            {dirty
              ? "입력 내용은 이 화면에 유지됩니다. 로그인 화면으로 이동하면 이탈 확인을 거칩니다."
              : "작성자 계정으로 로그인해 주세요."}
          </p>
          <Link
            className="iyum-button iyum-button-primary"
            href={`/login?from=${encodeURIComponent(editPath)}`}
          >
            로그인하고 수정하기
          </Link>
        </div>
      )}
      {auth.status === "error" && (
        <p role="alert">
          로그인 상태를 확인하지 못했습니다. 입력 내용은 유지됩니다.
        </p>
      )}
      {auth.status === "authenticated" && !owner && (
        <p className="community-error" role="alert">
          작성자만 이 게시글을 수정할 수 있습니다.
        </p>
      )}
      {(owner || dirty) && (
        <form
          ref={form}
          onSubmit={submit}
          noValidate
          aria-label="게시글 수정"
          className="community-create-form"
        >
          <p className="community-help">
            모든 항목을 입력해 주세요. 주제를 변경해도 이전 목록의 조건은 그대로
            유지됩니다.
          </p>
          {error && (
            <div
              ref={alert}
              tabIndex={-1}
              role="alert"
              className="community-error"
            >
              <p>{error.message}</p>
              {error.uncertain && (
                <Link
                  className="iyum-text-link"
                  href={detail}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  새 탭에서 게시글 확인
                </Link>
              )}
            </div>
          )}
          <PostFields
            values={values}
            errors={errors}
            disabled={busy || !owner || !!auth.pending}
            onChange={edit}
          />
          <div className="community-form-actions">
            <button
              type="button"
              className="iyum-button iyum-button-outline"
              onClick={() => blocker.request(() => router.push(detail))}
            >
              취소
            </button>
            <button
              className="iyum-button iyum-button-primary"
              disabled={
                busy || !owner || !!auth.pending || error?.status === 404
              }
            >
              {busy ? "저장 중…" : "수정 저장"}
            </button>
          </div>
          <p role="status">{busy ? "수정 내용을 저장하고 있습니다." : ""}</p>
        </form>
      )}
      {blocker.state === "blocked" && (
        <CommunityDialog
          titleId="edit-unsaved-title"
          descriptionId="edit-unsaved-desc"
          onCancel={blocker.reset}
        >
          <h2 id="edit-unsaved-title">수정 중인 내용이 있습니다.</h2>
          <p id="edit-unsaved-desc">
            페이지를 나가면 입력한 내용이 사라집니다.
            {busy ? " 저장 요청은 이미 처리되고 있을 수 있습니다." : ""}
          </p>
          <div className="community-dialog-actions">
            <button
              className="iyum-button iyum-button-outline"
              onClick={blocker.proceed}
            >
              나가기
            </button>
            <button
              data-initial-focus
              className="iyum-button iyum-button-primary"
              onClick={blocker.reset}
            >
              계속 수정
            </button>
          </div>
        </CommunityDialog>
      )}
    </>
  )
}
