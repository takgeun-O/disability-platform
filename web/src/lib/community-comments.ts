import { writtenInstant } from "./written-time";
import { isLikeState, type LikeState } from "./community-api";
import { getCsrfContext } from "./signup-api";
import { characterCount, normalizeTitle } from "./community-posts";
export type Comment = LikeState & {
  commentId: number;
  content: string;
  author: { memberId: number; nickname: string };
  createdAt: string;
  updatedAt: string;
  contentUpdatedAt: string | null;
};
export type CommentSort = "OLDEST" | "LATEST" | "POPULAR";
export type CommentCursor = {
  createdAt: string;
  commentId: number;
  sort: CommentSort;
  likeCount: number | null;
};
export const commentCursorKey = (v: CommentCursor | null) =>
  JSON.stringify(v && [v.sort, v.createdAt, v.commentId, v.likeCount]);
export type CommentPage = {
  items: Comment[];
  hasNext: boolean;
  nextCursor: CommentCursor | null;
};
export const requiredContent = "댓글 내용은 필수입니다.";
export const longContent = "댓글 내용은 2,000자 이하여야 합니다.";
export function validateComment(content: string) {
  return !normalizeTitle(content)
    ? requiredContent
    : characterCount(content) > 2000
      ? longContent
      : "";
}
export class CommentError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public field = "",
    public uncertain = false,
  ) {
    super(message);
  }
}
const obj = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);
const id = (v: unknown): v is number =>
  Number.isSafeInteger(v) && Number(v) > 0;
const instant = (v: unknown): v is string =>
  typeof v === "string" &&
  /^\d{4}-\d\d-\d\dT.+Z$/.test(v) &&
  Number.isFinite(Date.parse(v));
const cursor = (v: unknown): v is CommentCursor =>
  obj(v) &&
  id(v.commentId) &&
  instant(v.createdAt) &&
  (["OLDEST", "LATEST"].includes(String(v.sort))
    ? v.likeCount === null
    : v.sort === "POPULAR" &&
      Number.isSafeInteger(v.likeCount) &&
      Number(v.likeCount) >= 0);
const comment = (v: unknown): v is Comment =>
  obj(v) &&
  typeof v.content === "string" &&
  instant(v.updatedAt) &&
  (v.contentUpdatedAt === null || (typeof v.contentUpdatedAt === "string" && writtenInstant(v.contentUpdatedAt) !== null)) &&
  obj(v.author) &&
  id(v.author.memberId) &&
  typeof v.author.nickname === "string" &&
  id(v.commentId) &&
  instant(v.createdAt) &&
  isLikeState(v);
export function isCommentPage(
  v: unknown,
  sort: CommentSort = "OLDEST",
): v is CommentPage {
  if (
    !obj(v) ||
    !Array.isArray(v.items) ||
    v.items.length > 20 ||
    !v.items.every(comment) ||
    typeof v.hasNext !== "boolean"
  )
    return false;
  const last = v.items.at(-1);
  return v.hasNext
    ? cursor(v.nextCursor) &&
        v.nextCursor.sort === sort &&
        !!last &&
        (sort !== "POPULAR" || v.nextCursor.likeCount === last.likeCount) &&
        v.nextCursor.commentId === last.commentId &&
        v.nextCursor.createdAt === last.createdAt
    : v.nextCursor === null;
}
const uncertain =
  "요청 결과를 확인하지 못했습니다. 댓글 목록을 확인한 뒤 다시 판단해 주세요. 자동으로 재전송하지 않습니다.";
export function commentError(status: number, body: unknown, writing: boolean) {
  const code =
    obj(body) && typeof body.code === "string"
      ? body.code
      : "UNEXPECTED_RESPONSE";
  const messages: Record<string, string> = {
    AUTHENTICATION_REQUIRED:
      "로그인이 필요하거나 만료되었습니다. 입력은 유지됩니다. 로그인 상태를 확인해 주세요.",
    CSRF_TOKEN_INVALID:
      "보안 확인에 실패했습니다. 입력은 유지됩니다. 다시 요청하면 보안 정보를 새로 확인합니다.",
    COMMENT_LIKE_NOT_ALLOWED:
      "현재 회원 상태에서는 댓글 좋아요를 변경할 수 없습니다.",
    COMMENT_WRITE_NOT_ALLOWED:
      "현재 회원 상태에서는 댓글을 변경할 수 없습니다.",
    ACCESS_DENIED: "이 댓글을 변경할 권한이 없습니다.",
    POST_NOT_FOUND: "게시글이 없거나 삭제되어 댓글을 이용할 수 없습니다.",
    COMMENT_NOT_FOUND: "댓글이 없거나 이미 삭제되었습니다.",
    VALIDATION_FAILED: "댓글 입력을 확인해 주세요.",
    INVALID_REQUEST_BODY: "댓글 요청 형식을 확인해 주세요.",
    INVALID_REQUEST_PARAMETER: "댓글 조회 조건을 확인해 주세요.",
    INVALID_PAGE_REQUEST: "댓글 조회 조건을 확인해 주세요.",
    INVALID_CURSOR:
      "목록 위치가 유효하지 않습니다. 처음부터 다시 불러와 주세요.",
  };
  let field = "";
  if (
    code === "VALIDATION_FAILED" &&
    obj(body) &&
    Array.isArray(body.fieldErrors)
  ) {
    const error = body.fieldErrors.find((v) => obj(v) && v.field === "content");
    if (error)
      field = [requiredContent, longContent].includes(error.message)
        ? error.message
        : "댓글 입력을 확인해 주세요.";
  }
  const known =
    status >= 400 &&
    status < 500 &&
    Object.prototype.hasOwnProperty.call(messages, code);
  return new CommentError(
    status,
    code,
    known
      ? messages[code]
      : writing
        ? uncertain
        : "댓글을 불러오지 못했습니다. 다시 시도해 주세요.",
    field,
    writing && !known,
  );
}
async function request(
  postId: string,
  suffix: string,
  options: RequestInit,
  writing: boolean,
  base = process.env.NEXT_PUBLIC_API_BASE_URL,
) {
  if (!base || !/^[1-9]\d*$/.test(postId))
    throw new CommentError(
      0,
      "API_NOT_CONFIGURED",
      "댓글 서비스에 연결할 수 없습니다.",
    );
  let response: Response;
  try {
    response = await fetch(
      base.replace(/\/$/, "") +
        `/api/v1/community/posts/${postId}/comments` +
        suffix,
      {
        ...options,
        credentials:
          writing || typeof window !== "undefined" ? "include" : "omit",
        cache: "no-store",
        redirect: "error",
        referrerPolicy: "no-referrer",
        headers: { Accept: "application/json", ...options.headers },
        signal: options.signal
          ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)])
          : AbortSignal.timeout(15000),
      },
    );
  } catch {
    throw commentError(0, null, writing);
  }
  if (
    !suffix.endsWith("/like") &&
    response.status === 204 &&
    ["PUT", "DELETE"].includes(options.method ?? "")
  )
    return undefined;
  if (
    !suffix.endsWith("/like") &&
    response.ok &&
    ["PUT", "DELETE"].includes(options.method ?? "")
  )
    throw commentError(response.status, null, writing);
  let body: unknown;
  try {
    if (!response.headers.get("content-type")?.includes("application/json"))
      throw Error();
    body = await response.json();
  } catch {
    throw commentError(response.status, null, writing);
  }
  if (!response.ok) throw commentError(response.status, body, writing);
  if (response.status !== (options.method === "POST" ? 201 : 200))
    throw commentError(response.status, null, writing);
  return body;
}
export async function getBestComments(postId: string, signal?: AbortSignal, base?: string): Promise<Comment[]> {
  const body = await request(postId, "/best", { method: "GET", signal }, false, base);
  if (!obj(body) || !Array.isArray(body.items) || body.items.length > 3 || !body.items.every(comment))
    throw commentError(200, null, false);
  return body.items;
}

export async function getComments(
  postId: string,
  after: CommentCursor | null = null,
  signal?: AbortSignal,
  base?: string,
  sort: CommentSort = "OLDEST",
) {
  if (after && (!cursor(after) || after.sort !== sort))
    throw commentError(400, { code: "INVALID_CURSOR" }, false);
  const params = new URLSearchParams({ size: "20", sort });
  // API cursor is opaque: never round its microseconds through Date/toISOString.
  if (after) {
    params.set("afterCreatedAt", after.createdAt);
    params.set("afterId", String(after.commentId));
    params.set("afterSort", after.sort);
    if (sort === "POPULAR")
      params.set("afterLikeCount", String(after.likeCount));
  }
  const body = await request(
    postId,
    `?${params}`,
    { method: "GET", signal },
    false,
    base,
  );
  if (
    !isCommentPage(body, sort) ||
    (after &&
      body.nextCursor &&
      commentCursorKey(body.nextCursor) === commentCursorKey(after))
  )
    throw commentError(200, null, false);
  return body;
}
export async function changeComment(
  postId: string,
  method: "POST" | "PUT" | "DELETE",
  commentId: number | null,
  content: string,
  isCurrent: () => boolean,
) {
  if (!isCurrent()) return null;
  if (method !== "DELETE") {
    const field = validateComment(content);
    if (field) throw new CommentError(400, "VALIDATION_FAILED", field, field);
  }
  let csrf;
  try {
    csrf = await getCsrfContext();
  } catch {
    throw new CommentError(
      0,
      "CSRF_PREPARATION_FAILED",
      "보안 정보를 확인하지 못해 변경 요청을 보내지 않았습니다. 입력은 유지됩니다.",
    );
  }
  if (!isCurrent()) return null;
  const body = await request(
    postId,
    commentId === null ? "" : `/${commentId}`,
    {
      method,
      headers: {
        [csrf.headerName]: csrf.token,
        ...(method === "DELETE" ? {} : { "Content-Type": "application/json" }),
      },
      ...(method === "DELETE" ? {} : { body: JSON.stringify({ content }) }),
    },
    true,
  );
  if (method === "POST") {
    if (!obj(body) || !id(body.commentId)) throw commentError(201, null, true);
    return body.commentId;
  }
  return commentId;
}
export function mergeComments(existing: Comment[], incoming: Comment[]) {
  const ids = new Set(existing.map((c) => c.commentId));
  return [
    ...existing,
    ...incoming.filter((c) => {
      if (ids.has(c.commentId)) return false;
      ids.add(c.commentId);
      return true;
    }),
  ];
}

// No mutation retry: an uncertain outcome is resolved by the public cursor list.
export async function changeCommentLike(
  postId: string,
  commentId: number,
  liked: boolean,
  isCurrent: () => boolean,
): Promise<LikeState | null> {
  if (!isCurrent()) return null;
  if (!id(commentId)) throw commentError(400, null, true);
  let csrf;
  try {
    csrf = await getCsrfContext();
  } catch {
    throw new CommentError(
      0,
      "CSRF_PREPARATION_FAILED",
      "보안 정보를 확인하지 못해 좋아요 요청을 보내지 않았습니다. 다시 시도해 주세요.",
    );
  }
  if (!isCurrent()) return null;
  const body = await request(
    postId,
    `/${commentId}/like`,
    {
      method: liked ? "PUT" : "DELETE",
      headers: { [csrf.headerName]: csrf.token },
    },
    true,
  );
  if (
    !obj(body) ||
    body.postId !== Number(postId) ||
    body.commentId !== commentId ||
    !isLikeState(body)
  )
    throw commentError(200, null, true);
  return { liked: body.liked, likeCount: body.likeCount };
}
