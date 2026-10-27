import { isFeedItem, olderThan, type FeedPage } from "./community-api";
import { validateComment } from "./community-comments";
import {
  normalizeTitle,
  characterCount,
  POST_TYPES,
  POST_TOPICS,
  parsePostCursor,
  type PostType,
  type PostTopic,
  type PostCursor,
} from "./community-posts";
import { getAuthCsrf } from "./auth-api";
import type { CsrfContext } from "./signup-api";
export { getAuthCsrf as getMemberCsrf };
export type Profile = {
  memberId: number;
  email: string;
  nickname: string;
  createdAt: string;
};
export type ActivitySummary = { postCount: number; commentCount: number };
export class MemberApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public field = "",
  ) {
    super(message);
    this.name = "MemberApiError";
  }
}
const object = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);
const count = (v: unknown) => Number.isSafeInteger(v) && Number(v) >= 0;
export function isProfile(v: unknown): v is Profile {
  return (
    object(v) &&
    Number.isSafeInteger(v.memberId) &&
    Number(v.memberId) > 0 &&
    typeof v.email === "string" &&
    /^[^\s@]+@[^\s@]+$/.test(v.email) &&
    typeof v.nickname === "string" &&
    !!v.nickname.trim() &&
    typeof v.createdAt === "string" &&
    /^\d{4}-\d\d-\d\dT.+Z$/.test(v.createdAt) &&
    Number.isFinite(Date.parse(v.createdAt))
  );
}
export function isSummary(v: unknown): v is ActivitySummary {
  return object(v) && count(v.postCount) && count(v.commentCount);
}
// Java String.strip()/Character.isWhitespace: NBSP is not stripped by the server.
export const normalizeNickname = (value: string) =>
  value.replace(
    /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g,
    "",
  );
export function nicknameError(value: string): string {
  const name = normalizeNickname(value);
  if (!name) return "닉네임을 입력해 주세요.";
  if (!/^[가-힣A-Za-z0-9_]{2,20}$/.test(name))
    return "닉네임은 한글·영문·숫자·밑줄로 2~20자여야 합니다.";
  if (
    ["관리자", "운영자", "admin", "administrator", "moderator"].includes(
      name.toLowerCase(),
    )
  )
    return "사용할 수 없는 닉네임입니다.";
  return "";
}
export const joinedMonth = (date: string) =>
  new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "long",
  }).format(new Date(date));
function responseError(status: number, body: unknown): MemberApiError {
  const code =
    object(body) && typeof body.code === "string"
      ? body.code
      : "UNEXPECTED_RESPONSE";
  if (status === 401)
    return new MemberApiError(
      status,
      code,
      "로그인 시간이 만료되었어요. 다시 로그인해 주세요.",
    );
  if (status === 409 && code === "NICKNAME_ALREADY_EXISTS")
    return new MemberApiError(
      status,
      code,
      "",
      "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해 주세요.",
    );
  if (
    status === 400 &&
    (code === "INVALID_NICKNAME" ||
      (code === "VALIDATION_FAILED" &&
        object(body) &&
        Array.isArray(body.fieldErrors) &&
        body.fieldErrors.some((f) => object(f) && f.field === "nickname")))
  )
    return new MemberApiError(
      status,
      code,
      "",
      "사용할 수 있는 닉네임인지 확인해 주세요. 한글·영문·숫자·밑줄 2~20자이며 예약어는 사용할 수 없습니다.",
    );
  if (status === 403 && code === "CSRF_TOKEN_INVALID")
    return new MemberApiError(
      status,
      code,
      "보안 확인 정보가 만료되었어요. 저장을 다시 누르면 새로 확인합니다.",
    );
  if (status === 403)
    return new MemberApiError(
      status,
      code,
      "현재 계정으로 이 작업을 할 수 없습니다. 계정 상태를 확인해 주세요.",
    );
  return new MemberApiError(
    status,
    code,
    "응답을 확인하지 못했습니다. 잠시 후 다시 확인해 주세요.",
  );
}
async function request(
  path: string,
  options: RequestInit,
  signal?: AbortSignal,
  expectedStatus = 200,
): Promise<unknown> {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!base)
    throw new MemberApiError(
      0,
      "API_NOT_CONFIGURED",
      "회원 서비스에 연결할 수 없습니다.",
    );
  let response: Response;
  try {
    response = await fetch(
      base.replace(/\/$/, "") + "/api/v1/members/me" + path,
      {
        ...options,
        credentials: "include",
        cache: "no-store",
        redirect: "error",
        referrerPolicy: "no-referrer",
        headers: { Accept: "application/json", ...options.headers },
        signal: signal
          ? AbortSignal.any([signal, AbortSignal.timeout(15000)])
          : AbortSignal.timeout(15000),
      },
    );
  } catch {
    throw new MemberApiError(
      0,
      "NETWORK_ERROR",
      options.method === "POST"
        ? "탈퇴 결과를 확인하지 못했습니다. 이미 처리되었을 수 있으니 로그인 상태를 확인한 뒤 다시 판단해 주세요."
        : options.method === "PUT"
          ? "저장 결과를 확인하지 못했습니다. 이미 변경되었을 수 있으니 프로필을 다시 확인해 주세요."
          : "연결 문제로 정보를 가져오지 못했습니다. 다시 시도해 주세요.",
    );
  }
  if (expectedStatus === 204 && response.status === 204) return null;
  let body: unknown = null;
  if (
    /^application\/json(?:;|$)/i.test(
      response.headers.get("content-type") ?? "",
    )
  ) {
    try {
      body = await response.json();
    } catch {}
  }
  if (path === "/withdrawal" && response.status !== 204)
    throw withdrawalError(response.status, body);
  if (response.status !== expectedStatus)
    throw responseError(response.status, body);
  return body;
}
function profile(body: unknown, memberId: number): Profile {
  if (!isProfile(body) || body.memberId !== memberId)
    throw new MemberApiError(
      200,
      "UNEXPECTED_RESPONSE",
      "회원 응답을 확인하지 못했습니다. 다시 확인해 주세요.",
    );
  return {
    memberId: body.memberId,
    email: body.email,
    nickname: body.nickname,
    createdAt: body.createdAt,
  };
}
export async function getProfile(memberId: number, signal?: AbortSignal) {
  return profile(await request("", { method: "GET" }, signal), memberId);
}
export async function getActivitySummary(
  signal?: AbortSignal,
): Promise<ActivitySummary> {
  const body = await request("/activity-summary", { method: "GET" }, signal);
  if (!isSummary(body))
    throw new MemberApiError(
      200,
      "UNEXPECTED_RESPONSE",
      "활동 개수 응답을 확인하지 못했습니다.",
    );
  return { postCount: body.postCount, commentCount: body.commentCount };
}
export async function updateNickname(
  memberId: number,
  nickname: string,
  csrf: CsrfContext,
  signal?: AbortSignal,
) {
  return profile(
    await request(
      "/nickname",
      {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ nickname }),
      },
      signal,
    ),
    memberId,
  );
}

export const MEMBER_POST_SIZE = 10;
// Public and private lists share item fields, but this endpoint must only return the session member's posts.
export async function getMemberPosts(
  memberId: number,
  cursor: PostCursor | null = null,
  signal?: AbortSignal,
): Promise<FeedPage> {
  const params = new URLSearchParams({ size: String(MEMBER_POST_SIZE) });
  if (cursor) {
    params.set("afterCreatedAt", cursor.createdAt);
    params.set("afterId", String(cursor.postId));
  }
  const body = await request("/posts?" + params, { method: "GET" }, signal);
  const invalid = () =>
    new MemberApiError(
      200,
      "UNEXPECTED_RESPONSE",
      "내 게시글 응답을 확인하지 못했습니다. 다시 시도해 주세요.",
    );
  if (
    !object(body) ||
    !Array.isArray(body.items) ||
    body.items.length > MEMBER_POST_SIZE ||
    !body.items.every(isFeedItem) ||
    typeof body.hasNext !== "boolean"
  )
    throw invalid();
  const items = body.items,
    last = items.at(-1);
  if (
    items.some(
      (item, index) =>
        item.author.memberId !== memberId ||
        !parsePostCursor(
          new URLSearchParams({
            afterCreatedAt: item.createdAt,
            afterId: String(item.id),
          }),
        ) ||
        (cursor && !olderThan(item, cursor)) ||
        (index > 0 &&
          !olderThan(item, {
            createdAt: items[index - 1].createdAt,
            postId: items[index - 1].id,
          })),
    ) ||
    (body.hasNext
      ? !last ||
        !object(body.nextCursor) ||
        body.nextCursor.createdAt !== last.createdAt ||
        body.nextCursor.postId !== last.id
      : body.nextCursor !== null)
  )
    throw invalid();
  return body as FeedPage;
}

export type MemberCommentCursor = { createdAt: string; commentId: number };
export type MemberCommentItem = {
  commentId: number;
  content: string;
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  liked: boolean;
  post: { postId: number; title: string; type: PostType; topic: PostTopic };
};
export type MemberCommentPage = {
  items: MemberCommentItem[];
  hasNext: boolean;
  nextCursor: MemberCommentCursor | null;
};
const positiveId = (v: unknown): v is number =>
  Number.isSafeInteger(v) && Number(v) > 0;
const instant = (v: unknown): v is string =>
  typeof v === "string" &&
  !!parsePostCursor(new URLSearchParams({ afterCreatedAt: v, afterId: "1" }));
function isMemberComment(v: unknown): v is MemberCommentItem {
  return (
    object(v) &&
    positiveId(v.commentId) &&
    count(v.likeCount) &&
    typeof v.liked === "boolean" &&
    typeof v.content === "string" &&
    !validateComment(v.content) &&
    instant(v.createdAt) &&
    instant(v.updatedAt) &&
    object(v.post) &&
    positiveId(v.post.postId) &&
    typeof v.post.title === "string" &&
    !!normalizeTitle(v.post.title) &&
    characterCount(normalizeTitle(v.post.title)) <= 120 &&
    typeof v.post.type === "string" &&
    Object.prototype.hasOwnProperty.call(POST_TYPES, v.post.type) &&
    typeof v.post.topic === "string" &&
    Object.prototype.hasOwnProperty.call(POST_TOPICS, v.post.topic)
  );
}
// The authenticated server session selects the owner and determines each comment's liked state.
export async function getMemberComments(
  cursor: MemberCommentCursor | null = null,
  signal?: AbortSignal,
): Promise<MemberCommentPage> {
  const params = new URLSearchParams({ size: "10" });
  if (cursor) {
    params.set("afterCreatedAt", cursor.createdAt);
    params.set("afterId", String(cursor.commentId));
  }
  const body = await request("/comments?" + params, { method: "GET" }, signal);
  const invalid = () =>
    new MemberApiError(
      200,
      "UNEXPECTED_RESPONSE",
      "내 댓글 응답을 확인하지 못했습니다. 다시 시도해 주세요.",
    );
  if (
    !object(body) ||
    !Array.isArray(body.items) ||
    body.items.length > 10 ||
    !body.items.every(isMemberComment) ||
    typeof body.hasNext !== "boolean"
  )
    throw invalid();
  const items = body.items,
    last = items.at(-1);
  if (
    items.some(
      (item, index) =>
        (cursor &&
          !olderThan(
            { id: item.commentId, createdAt: item.createdAt },
            { postId: cursor.commentId, createdAt: cursor.createdAt },
          )) ||
        (index > 0 &&
          !olderThan(
            { id: item.commentId, createdAt: item.createdAt },
            {
              postId: items[index - 1].commentId,
              createdAt: items[index - 1].createdAt,
            },
          )),
    ) ||
    (body.hasNext
      ? !last ||
        !object(body.nextCursor) ||
        body.nextCursor.createdAt !== last.createdAt ||
        body.nextCursor.commentId !== last.commentId
      : body.nextCursor !== null)
  )
    throw invalid();
  return body as MemberCommentPage;
}

export class WithdrawalError extends MemberApiError {
  constructor(
    status: number,
    code: string,
    message: string,
    public fields: Partial<
      Record<"currentPassword" | "confirmed", string>
    > = {},
  ) {
    super(status, code, message);
  }
}
function withdrawalError(status: number, body: unknown): WithdrawalError {
  const code =
    object(body) && typeof body.code === "string"
      ? body.code
      : "UNEXPECTED_RESPONSE";
  const fields: WithdrawalError["fields"] = {};
  if (code === "PASSWORD_MISMATCH")
    fields.currentPassword = "현재 비밀번호가 일치하지 않습니다.";
  else if (code === "INVALID_PASSWORD")
    fields.currentPassword = "현재 비밀번호를 올바르게 입력해 주세요.";
  if (
    code === "VALIDATION_FAILED" &&
    object(body) &&
    Array.isArray(body.fieldErrors)
  ) {
    for (const field of body.fieldErrors) {
      if (object(field) && field.field === "currentPassword")
        fields.currentPassword =
          "현재 비밀번호를 입력하고 UTF-8 기준 72바이트 이하인지 확인해 주세요.";
      if (object(field) && field.field === "confirmed")
        fields.confirmed = "탈퇴 안내를 확인하고 동의해 주세요.";
    }
  }
  const message = Object.keys(fields).length
    ? ""
    : status === 401
      ? "로그인 시간이 만료되었어요. 다시 로그인해 주세요."
      : code === "CSRF_TOKEN_INVALID"
        ? "보안 확인 정보가 만료되었어요. 내용을 확인하고 다시 제출하면 새 보안 정보를 확인합니다."
        : code === "MEMBER_NOT_ACTIVE"
          ? "활성 상태의 회원만 탈퇴할 수 있습니다. 계정 상태를 확인해 주세요."
          : "탈퇴 결과를 확인하지 못했습니다. 이미 처리되었을 수 있으니 로그인 상태를 확인한 뒤 다시 판단해 주세요.";
  return new WithdrawalError(status, code, message, fields);
}
export async function withdrawMember(
  currentPassword: string,
  csrf: CsrfContext,
): Promise<void> {
  await request(
    "/withdrawal",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify({ currentPassword, confirmed: true }),
    },
    undefined,
    204,
  );
}
