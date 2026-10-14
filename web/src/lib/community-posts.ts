import { topicByPath, isPostListPath } from "./community-topics"
// 서버의 PostType / PostTopic / PostInputPolicy와 대응한다. 표시 문구와 전송 값을 분리한다.
export const POST_TYPES = {
  GENERAL: "일반",
  QUESTION: "질문·답변",
  REVIEW: "경험·후기",
} as const
export const POST_TOPICS = {
  DAILY: "자유·일상",
  HEARING_AID: "보청기",
  COCHLEAR_IMPLANT: "인공와우",
  TREATMENT_REHABILITATION: "치료·재활",
  COMMUNICATION: "의사소통",
  EMPLOYMENT: "취업·직장",
  WELFARE_LIFE: "복지·생활",
} as const
export type PostType = keyof typeof POST_TYPES
export type PostTopic = keyof typeof POST_TOPICS
export type PostForm = {
  type: PostType | ""
  topic: PostTopic | ""
  title: string
  content: string
}
export type PostField = keyof PostForm
export type PostFieldErrors = Partial<Record<PostField, string>>
export type PostQuery = {
  type: PostType | ""
  topic: PostTopic | ""
  page: number
}
export const PAGE_SIZE = 20
export const DEFAULT_QUERY: PostQuery = { type: "", topic: "", page: 0 }

function enumOrLabel<T extends string>(
  value: string | null,
  labels: Record<T, string>,
): T | "" {
  return (
    (Object.keys(labels) as T[]).find(
      (key) => key === value || labels[key] === value,
    ) ?? ""
  )
}
export function parsePostQuery(
  params: Pick<URLSearchParams, "get">,
): PostQuery {
  const page = params.get("page") ?? "0"
  return {
    type: enumOrLabel(params.get("type"), POST_TYPES),
    topic: enumOrLabel(params.get("topic"), POST_TOPICS),
    page:
      /^\d+$/.test(page) && Number(page) <= Math.floor(2147483647 / PAGE_SIZE)
        ? Number(page)
        : 0,
  }
}
export function postQueryString(query: PostQuery): string {
  const params = new URLSearchParams()
  if (query.type) params.set("type", query.type)
  if (query.topic) params.set("topic", query.topic)
  if (query.page) params.set("page", String(query.page))
  return params.toString()
}
export function listHref(
  query: PostQuery,
  pathname = "/community/posts",
): string {
  const space = topicByPath(pathname)
  const search = postQueryString(space ? { ...query, topic: "" } : query)
  return `${pathname}${search ? "?" + search : ""}`
}
export function listQuery(params: Pick<URLSearchParams, "get">, pathname: string): PostQuery {
  const query = parsePostQuery(params)
  const space = topicByPath(pathname)
  return space ? { ...query, topic: space.topic } : query
}
export function safeListReturn(value: string | null, fallback = "/community/posts"): string {
  const defaultPath = isPostListPath(fallback) ? fallback : "/community/posts"
  // 알려진 목록/주제 경로와 검증한 조건만 허용한다. 상세·작성·미등록 slug는 제외한다.
  if (!value) return defaultPath
  try {
    const url = new URL(value, "https://iyum.invalid")
    if (
      !value.startsWith("/") ||
      url.origin !== "https://iyum.invalid" ||
      !isPostListPath(url.pathname) ||
      /[\\\u0000-\u001f]/.test(value)
    )
      return defaultPath
    return listHref(listQuery(url.searchParams, url.pathname), url.pathname)
  } catch {
    return defaultPath
  }
}
export function queryLabel(query: PostQuery): string {
  return `${query.type ? POST_TYPES[query.type] : "전체 유형"} · ${
    query.topic ? POST_TOPICS[query.topic] : "전체 주제"
  }`
}
export function characterCount(value: string): number {
  return [...value].length
}
// Java String.strip / isBlank는 NBSP 등을 공백으로 처리하지 않는다. JS trim과 차이를 맞춘다.
const javaWhitespace =
  "[\\u0009-\\u000d\\u001c-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200a\\u2028\\u2029\\u205f\\u3000]"
export function normalizeTitle(value: string): string {
  return value.replace(
    new RegExp(`^${javaWhitespace}+|${javaWhitespace}+$`, "g"),
    "",
  )
}
export function validatePostForm(values: PostForm): PostFieldErrors {
  const errors: PostFieldErrors = {}
  if (!Object.prototype.hasOwnProperty.call(POST_TYPES, values.type))
    errors.type = "글 유형을 선택해 주세요."
  if (!Object.prototype.hasOwnProperty.call(POST_TOPICS, values.topic))
    errors.topic = "주제를 선택해 주세요."
  const title = normalizeTitle(values.title)
  if (!title) errors.title = "제목을 입력해 주세요."
  else if (characterCount(title) > 120)
    errors.title = "제목은 120자 이하여야 합니다."
  if (!normalizeTitle(values.content)) errors.content = "내용을 입력해 주세요."
  else if (characterCount(values.content) > 10_000)
    errors.content = "본문은 10,000자 이하여야 합니다."
  return errors
}
export function postDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value))
}
const SCROLL_PREFIX = "iyum-community-scroll:"
export function rememberListScroll(href: string): void {
  try {
    sessionStorage.setItem(SCROLL_PREFIX + href, String(window.scrollY))
  } catch {
    /* 저장소 제한 시 기본 브라우저 복귀 사용 */
  }
}
// 복귀 의도는 메모리에만 둔다. 주소 직접 입력/새로고침은 과거 저장 위치를 소비하지 않는다.
let returningToList: string | null = null
export function requestListScrollRestore(href: string): void { returningToList = href }
export function listenForListReturn(): () => void {
  const pop = () => {
    if (isPostListPath(location.pathname))
      requestListScrollRestore(listHref(listQuery(new URLSearchParams(location.search), location.pathname), location.pathname))
  }
  window.addEventListener("popstate", pop)
  return () => window.removeEventListener("popstate", pop)
}
export function restoreListScroll(href: string): () => void {
  if (returningToList !== href) return () => {}
  let frame = 0
  try {
    const saved = sessionStorage.getItem(SCROLL_PREFIX + href)
    if (
      saved !== null &&
      Number.isFinite(Number(saved)) &&
      Number(saved) >= 0
    ) {
      frame = requestAnimationFrame(() => {
        returningToList = null
        window.scrollTo({ top: Number(saved), behavior: "instant" })
        sessionStorage.removeItem(SCROLL_PREFIX + href)
      })
    }
  } catch {
    /* 브라우저 저장소에 접근하지 못해도 목록은 조회한다. */
  }
  return () => cancelAnimationFrame(frame)
}

export function clearListScroll(href: string): void {
  if (returningToList === href) returningToList = null
  try { sessionStorage.removeItem(SCROLL_PREFIX + href) } catch { /* 저장소 제한 */ }
}
