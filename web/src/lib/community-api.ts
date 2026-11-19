import { getCsrfContext } from "./signup-api"
import {
  POST_TYPES,
  POST_TOPICS,
  PAGE_SIZE,
  postQueryString,
  normalizeTitle,
  type PostType,
  type PostTopic,
  type PostForm,
  type PostFieldErrors,
  type PostQuery,
  type PostCursor,
} from "./community-posts"

type PostCommon = {
  id: number
  type: PostType
  topic: PostTopic
  title: string
  author: { memberId: number; nickname: string }
  createdAt: string
  likeCount: number
  liked: boolean
}
export type PostSummary = PostCommon & { commentCount: number }
export type PostListItem = PostSummary & { matchedContentExcerpt: string | null }
export type PostDetail = PostCommon & { content: string; updatedAt: string }
export type FeedItem = PostSummary & { contentPreview: string }
export type FeedPage = { items: FeedItem[]; hasNext: boolean; nextCursor: PostCursor | null }
export type PostPage = {
  items: PostListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}
export class CommunityApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: PostFieldErrors = {},
    public uncertain = false,
  ) {
    super(message)
    this.name = "CommunityApiError"
  }
}
const object = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v)
const positiveId = (v: unknown): v is number =>
  Number.isSafeInteger(v) && Number(v) > 0
const nonnegative = (v: unknown): v is number =>
  Number.isSafeInteger(v) && Number(v) >= 0
const instant = (v: unknown): v is string =>
  typeof v === "string" &&
  /^\d{4}-\d\d-\d\dT.+Z$/.test(v) &&
  Number.isFinite(Date.parse(v))
export type LikeState = { likeCount: number; liked: boolean }
export function isLikeState(v: unknown): v is LikeState {
  return object(v) && nonnegative(v.likeCount) && typeof v.liked === "boolean"
}
function isCommon(v: unknown): v is PostCommon {
  return (
    object(v) &&
    positiveId(v.id) &&
    typeof v.type === "string" &&
    Object.prototype.hasOwnProperty.call(POST_TYPES, v.type) &&
    typeof v.topic === "string" &&
    Object.prototype.hasOwnProperty.call(POST_TOPICS, v.topic) &&
    typeof v.title === "string" &&
    !!normalizeTitle(v.title) &&
    object(v.author) &&
    positiveId(v.author.memberId) &&
    typeof v.author.nickname === "string" &&
    !!v.author.nickname.trim() &&
    instant(v.createdAt) && isLikeState(v)
  )
}
function isSummary(v: unknown): v is PostSummary {
  return object(v) && nonnegative(v.commentCount) && isCommon(v)
}
export function isFeedItem(v: unknown): v is FeedItem {
  return object(v) && typeof v.contentPreview === "string" && [...v.contentPreview].length <= 200 && isSummary(v)
}
function isPage(v: unknown, query: PostQuery, size: number): v is PostPage {
  return (
    object(v) &&
    Array.isArray(v.items) &&
    v.items.every((item: unknown): item is PostListItem =>
      isSummary(item) && "matchedContentExcerpt" in item &&
      (item.matchedContentExcerpt === null || typeof item.matchedContentExcerpt === "string"),
    ) &&
    v.page === query.page &&
    v.size === size &&
    nonnegative(v.totalElements) &&
    nonnegative(v.totalPages) &&
    v.totalPages === Math.ceil(v.totalElements / size) &&
    typeof v.hasNext === "boolean" &&
    v.hasNext === query.page + 1 < v.totalPages &&
    v.items.length <= size &&
    v.items.length ===
      Math.max(
        0,
        Math.min(size, v.totalElements - query.page * size),
      ) &&
    new Set(v.items.map((item) => item.id)).size === v.items.length &&
    v.items.every(
      (item) =>
        (!query.type || item.type === query.type) &&
        (!query.topic || item.topic === query.topic),
    )
  )
}
function isApiError(
  v: unknown,
): v is {
  code: string
  message: string
  fieldErrors: { field: string; message: string }[]
} {
  return (
    object(v) &&
    typeof v.code === "string" &&
    typeof v.message === "string" &&
    Array.isArray(v.fieldErrors) &&
    v.fieldErrors.every(
      (field) =>
        object(field) &&
        typeof field.field === "string" &&
        typeof field.message === "string",
    )
  )
}
const uncertainMessage =
  "등록 결과를 확인하지 못했습니다. 글이 이미 저장되었을 수 있으니 목록에서 먼저 확인한 뒤 다시 시도해 주세요."
function responseError(
  status: number,
  body: unknown,
  writing: boolean,
): CommunityApiError {
  const code = isApiError(body) ? body.code : ""
  if (status === 400 && code === "INVALID_SEARCH_KEYWORD")
    return new CommunityApiError(status, code, "검색어는 100자 이하여야 합니다.")
  if (status === 401 && code === "AUTHENTICATION_REQUIRED")
    return new CommunityApiError(
      status,
      code,
      "로그인이 만료되었거나 필요합니다. 입력 내용은 유지됩니다. 로그인 상태를 확인해 주세요.",
    )
  if (status === 403 && code === "CSRF_TOKEN_INVALID")
    return new CommunityApiError(
      status,
      code,
      "보안 확인 정보가 만료되었습니다. 입력 내용은 유지됩니다. 다시 등록하면 새 정보로 요청합니다.",
    )
  if (
    status === 403 &&
    (code === "POST_WRITE_NOT_ALLOWED" || code === "ACCESS_DENIED")
  )
    return new CommunityApiError(
      status,
      code,
      "현재 계정 상태에서는 글을 작성할 수 없습니다. 계정 상태를 확인해 주세요.",
    )
  if (status === 404 && code === "POST_NOT_FOUND")
    return new CommunityApiError(
      status,
      code,
      "게시글을 찾을 수 없습니다. 삭제되었거나 없는 글입니다.",
    )
  if (
    status === 400 &&
    [
      "VALIDATION_FAILED",
      "INVALID_REQUEST_BODY",
      "INVALID_PAGE_REQUEST",
      "INVALID_REQUEST_PARAMETER",
      "INVALID_CURSOR",
    ].includes(code)
  ) {
    const fields: PostFieldErrors = {}
    if (isApiError(body))
      for (const { field, message } of body.fieldErrors) {
        // 알려진 검증 메시지만 전달한다. 임의의 서버 내용/입력 원문은 화면에 복사하지 않는다.
        const allowed: Record<string, string[]> = {
          type: ["글 유형은 필수입니다."],
          topic: ["주제는 필수입니다."],
          title: ["제목은 필수입니다.", "제목은 120자 이하여야 합니다."],
          content: ["본문은 필수입니다.", "본문은 10,000자 이하여야 합니다."],
        }
        if (Object.prototype.hasOwnProperty.call(allowed, field))
          fields[(field as keyof PostFieldErrors)] = allowed[field].includes(
            message,
          )
            ? message
            : "입력값을 확인해 주세요."
      }
    return new CommunityApiError(
      status,
      code,
      writing ? "입력값을 확인해 주세요." : "조회 조건을 확인해 주세요.",
      fields,
    )
  }
  return new CommunityApiError(
    status,
    status >= 500 ? "SERVER_ERROR" : "UNEXPECTED_RESPONSE",
    writing
      ? uncertainMessage
      : "게시글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    {},
    writing,
  )
}
async function request(
  path: string,
  options: RequestInit,
  writing = false,
  baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL,
): Promise<{ response: Response; body: unknown }> {
  const base = baseUrl
  if (!base)
    throw new CommunityApiError(
      0,
      "API_NOT_CONFIGURED",
      "커뮤니티 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
    )
  let response: Response
  try {
    response = await fetch(
      base.replace(/\/$/, "") + "/api/v1/community/posts" + path,
      {
        ...options,
        // Server reads stay anonymous; only browser reads carry the current session.
        credentials: writing || typeof window !== "undefined" ? "include" : "omit",
        cache: "no-store",
        redirect: "error",
        referrerPolicy: "no-referrer",
        headers: { Accept: "application/json", ...options.headers },
        signal: options.signal
          ? AbortSignal.any([options.signal, AbortSignal.timeout(15_000)])
          : AbortSignal.timeout(15_000),
      },
    )
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new CommunityApiError(
      0,
      "NETWORK_ERROR",
      writing
        ? uncertainMessage
        : "서버에 연결하지 못했습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요.",
      {},
      writing,
    )
  }
  let body: unknown = null
  if (
    response.status !== 204 &&
    /^application\/json(?:;|$)/i.test(
      response.headers.get("content-type") ?? "",
    )
  ) {
    try {
      body = await response.json()
    } catch {
      /* HTML/빈 응답/잘못된 JSON을 성공으로 처리하지 않는다. */
    }
  }
  return { response, body }
}
export async function getPosts(
  query: PostQuery & { q?: string; size?: number },
  signal?: AbortSignal,
  baseUrl?: string,
): Promise<PostPage> {
  const params = new URLSearchParams(postQueryString(query))
  if (query.q) params.set("q", query.q)
  params.set("page", String(query.page))
  const size = query.size ?? PAGE_SIZE
  params.set("size", String(size))
  const { response, body } = await request("?" + params, {
    method: "GET",
    signal,
  }, false, baseUrl)
  if (response.status !== 200 || !isPage(body, query, size))
    throw responseError(response.status, body, false)
  return body
}
export async function getPost(
  id: string,
  signal?: AbortSignal,
  baseUrl?: string,
): Promise<PostDetail> {
  if (!/^[1-9]\d*$/.test(id) || !positiveId(Number(id)))
    throw responseError(
      404,
      { code: "POST_NOT_FOUND", message: "", fieldErrors: [] },
      false,
    )
  const { response, body } = await request("/" + id, { method: "GET", signal }, false, baseUrl)
  if (
    response.status !== 200 ||
    !object(body) ||
    typeof body.content !== "string" ||
    !normalizeTitle(body.content) ||
    !instant(body.updatedAt) ||
    !isCommon(body) ||
    body.id !== Number(id)
  )
    throw responseError(response.status, body, false)
  return body as PostDetail
}
export async function createPost(values: PostForm): Promise<{ id: number }> {
  let csrf
  try {
    csrf = await getCsrfContext()
  } catch {
    throw new CommunityApiError(
      0,
      "CSRF_PREPARATION_FAILED",
      "보안 확인 정보를 가져오지 못해 글을 보내지 않았습니다. 연결 상태를 확인해 주세요.",
    )
  }
  const { response, body } = await request(
    "",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify({
        type: values.type,
        topic: values.topic,
        title: normalizeTitle(values.title),
        content: values.content,
      }),
    },
    true,
  )
  if (response.status !== 201 || !object(body) || !positiveId(body.id))
    throw responseError(response.status, body, true)
  return { id: body.id }
}

// 수정·삭제는 매번 CSRF를 조회하며 변경 요청을 자동 재전송하지 않는다.
export async function changePost(id: string, method: "PUT" | "DELETE", values: PostForm | undefined,
  isCurrent: () => boolean = () => true): Promise<boolean> {
  let csrf
  try { csrf = await getCsrfContext() }
  catch { throw new CommunityApiError(0, "CSRF_PREPARATION_FAILED", "보안 확인 정보를 가져오지 못해 요청을 보내지 않았습니다. 다시 시도해 주세요.") }
  // CSRF 대기 중 확정된 이탈 뒤에는 변경 요청을 새로 보내지 않는다.
  if (!isCurrent()) return false
  try {
    const { response, body } = await request("/" + id, {
      method,
      headers: { [csrf.headerName]: csrf.token, ...(method === "PUT" ? { "Content-Type": "application/json" } : {}) },
      ...(method === "PUT" && values ? { body: JSON.stringify({ type: values.type, topic: values.topic, title: normalizeTitle(values.title), content: values.content }) } : {}),
    }, true)
    if (response.status !== 204) throw responseError(response.status, body, true)
    return true
  } catch (error) {
    if (!(error instanceof CommunityApiError)) throw error
    if (error.uncertain) error.message = `${method === "PUT" ? "수정" : "삭제"} 결과를 확인하지 못했습니다. 이미 처리되었을 수 있으니 새 탭에서 게시글을 먼저 확인해 주세요.`
    else if (error.code === "CSRF_TOKEN_INVALID") error.message = "보안 확인 정보가 만료되었습니다. 다시 시도하면 새 정보로 요청합니다. 입력 내용은 유지됩니다."
    else if (error.code === "ACCESS_DENIED") error.message = "이 게시글을 변경할 권한이 없습니다. 작성자 계정을 확인해 주세요."
    else if (error.code === "POST_WRITE_NOT_ALLOWED") error.message = "현재 계정 상태에서는 게시글을 변경할 수 없습니다."
    throw error
  }
}

// 정렬 비교에만 소수점 자릿수를 맞춘다. 전송할 커서 원문은 바꾸지 않는다.
function instantKey(value: string) { return value.replace(/(?:\.(\d+))?Z$/, (_, fraction) => "." + (fraction ?? "").padEnd(9, "0") + "Z") }
export function olderThan(item: { createdAt: string; id: number }, cursor: PostCursor) {
  const time = instantKey(item.createdAt), boundary = instantKey(cursor.createdAt)
  return time < boundary || (time === boundary && item.id < cursor.postId)
}
export async function getFeed(query: PostQuery & { size?: number }, cursor: PostCursor | null = null, signal?: AbortSignal, baseUrl?: string): Promise<FeedPage> {
  const params = new URLSearchParams(postQueryString({ ...query, page: 0 }))
  const size = query.size ?? PAGE_SIZE
  params.set("size", String(size))
  if (cursor) { params.set("afterCreatedAt", cursor.createdAt); params.set("afterId", String(cursor.postId)) }
  const { response, body } = await request("/feed?" + params, { method: "GET", signal }, false, baseUrl)
  if (response.status !== 200 || !object(body) || !Array.isArray(body.items) || body.items.length > size ||
    !body.items.every(isFeedItem) ||
    typeof body.hasNext !== "boolean") throw responseError(response.status, body, false)
  const items = body.items as FeedItem[], last = items.at(-1)
  if (new Set(items.map(p => p.id)).size !== items.length || items.some((p, i) =>
    (query.type && p.type !== query.type) || (query.topic && p.topic !== query.topic) ||
    (cursor && !olderThan(p, cursor)) || (i > 0 && !olderThan(p, { createdAt: items[i-1].createdAt, postId: items[i-1].id }))) ||
    (body.hasNext ? (!last || !object(body.nextCursor) || body.nextCursor.createdAt !== last.createdAt || body.nextCursor.postId !== last.id) : body.nextCursor !== null))
    throw responseError(response.status, null, false)
  return body as FeedPage
}

// Explicit, bodyless PUT/DELETE. Never retry an ambiguous mutation automatically.
export async function changePostLike(id: number, liked: boolean, isCurrent: () => boolean): Promise<LikeState | null> {
  if (!positiveId(id)) throw new CommunityApiError(0, "INVALID_POST_ID", "게시글을 확인해 주세요.")
  let csrf
  try { csrf = await getCsrfContext() }
  catch { throw new CommunityApiError(0, "CSRF_PREPARATION_FAILED", "보안 정보를 확인하지 못해 좋아요 요청을 보내지 않았습니다. 다시 시도해 주세요.") }
  if (!isCurrent()) return null
  const { response, body } = await request(`/${id}/like`, { method: liked ? "PUT" : "DELETE", headers: { [csrf.headerName]: csrf.token } }, true).catch(error => {
    if (error instanceof CommunityApiError && error.uncertain) error.message = "좋아요 처리 결과를 확인하지 못했습니다. 현재 상태를 다시 확인한 뒤 시도해 주세요."
    throw error
  })
  if (response.status === 200 && object(body) && body.postId === id && isLikeState(body)) return { liked: body.liked, likeCount: body.likeCount }
  if (response.status === 403 && isApiError(body) && body.code === "POST_LIKE_NOT_ALLOWED")
    throw new CommunityApiError(403, body.code, "현재 계정 상태에서는 좋아요를 변경할 수 없습니다.")
  const error = responseError(response.status, body, true)
  if (error.uncertain) error.message = "좋아요 처리 결과를 확인하지 못했습니다. 현재 상태를 다시 확인한 뒤 시도해 주세요."
  if (error.code === "CSRF_TOKEN_INVALID") error.message = "보안 확인 정보가 만료되었습니다. 좋아요를 다시 누르면 새 정보로 요청합니다."
  throw error
}
