"use client";
import { useCommentTransition } from "@/lib/useCommentTransition";
import CommentSortMenu from "./CommentSortMenu";
import ReportButton from "./ReportButton";
import CommentItem from "./CommentItem";
// Refresh the visible prefix, keeping editor snapshots separate from server data.
import {
  useCallback,
  useLayoutEffect,
  useEffect,
  useEffectEvent,
  useRef,
  useState,
  type FormEvent,
} from "react";
import {
  memberCommentsChanged,
  postListChanged,
  postCommentsChanged,
  usePostVersion,
} from "@/lib/community-mutations";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import {
  useNavigationGuard,
  useNavigationRequest,
  useUnsavedChanges,
} from "@/components/NavigationGuard";
import CommunityDialog from "./CommunityDialog";
import CommentAuthDialog from "./CommentAuthDialog";
import CommentLikeButton from "./CommentLikeButton";
import CommentSuccessToast from "./CommentSuccessToast";
import {
  changeComment,
  CommentError,
  getComments,
  commentCursorKey,
  commentError,
  type CommentSort,
  mergeComments,
  validateComment,
  requiredContent,
  type Comment,
  type CommentPage,
} from "@/lib/community-comments";
import type { PublicResult } from "@/lib/community-load";
import { characterCount } from "@/lib/community-posts";

type EditorAction =
  | { kind: "edit" | "delete"; comment: Comment }
  | { kind: "cancel" };
type Editor = {
  id: number;
  original: string;
  value: string;
  memberId: number;
  postId: string;
  contentUpdatedAt: string | null;
  confirmed?: Comment;
};
export default function Comments({
  postId,
  initial,
  returnTo,
}: {
  postId: string;
  returnTo: string;
  initial: PublicResult<CommentPage>;
}) {
  const identityVersion = usePostVersion("public-identity");
  const auth = useAuth();
  const likeContext = `${auth.memberRequests.revision}:${auth.memberRequests.paused}:${auth.status}:${auth.pending}`;
  const [loadedContext, setLoadedContext] = useState("");
  const router = useRouter(),
    guard = useNavigationGuard();
  const subscribeNavigation = guard.subscribeNavigation;
  const scope = useNavigationRequest();
  const readController = useRef<AbortController | null>(null);
  const reads = useRef(0),
    reading = useRef(false),
    writing = useRef(false),
    pageCount = useRef(1);
  const [sort, setSort] = useState<CommentSort>("OLDEST");
  const sortRef = useRef<CommentSort>("OLDEST"),
    cursors = useRef<string[]>([]),
    autoStalled = useRef(false);
  const { list: commentList, capture: captureTransition } =
    useCommentTransition();
  const [displayedSort, setDisplayedSort] = useState<CommentSort>("OLDEST");
  const removed = useRef(new Set<number>());
  const [page, setPage] = useState<CommentPage>(
    (identityVersion === 0 ? initial.data : null) ?? {
      items: [],
      hasNext: false,
      nextCursor: null,
    },
  );
  const [readError, setReadError] = useState(initial.error?.message ?? "");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [error, setError] = useState<CommentError | null>(null);
  const [createError, setCreateError] = useState<CommentError | null>(null);
  const [draft, setDraft] = useState("");
  const [editor, setEditor] = useState<Editor | null>(null);
  const [expiredAction, setExpiredAction] = useState<"create" | "edit" | null>(null);
  if (expiredAction && (auth.status === "authenticated" || auth.pending === "login")) setExpiredAction(null);
  // Transient auth checks/logout failures preserve drafts; confirmed identity loss does not.
  if (editor && (editor.postId !== postId || auth.status === "anonymous" || auth.pending === "login" ||
    (auth.status === "authenticated" && auth.member?.memberId !== editor.memberId))) {
    setEditor(null);
  }
  const [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(false);
  const toastSequence = useRef(0);
  const [toast, setToast] = useState<{
    id: number;
    message: string;
    variant: "success" | "notice";
    valid: () => boolean;
    dismiss: () => void;
  } | null>(null);
  const [notice, setNotice] = useState("");
  const [linkedComment, setLinkedComment] = useState<number | null>(null);
  const [linkNotice, setLinkNotice] = useState("");
  const [created, setCreated] = useState<
    { commentId: number; content: string }[]
  >([]);
  const [loginPrompt, setLoginPrompt] = useState(false);
  const loginMoving = useRef(false);
  const [readMore, setReadMore] = useState(false);
  const section = useRef<HTMLElement>(null),
    sentinel = useRef<HTMLDivElement>(null),
    composer = useRef<HTMLDivElement>(null);
  const buffered = useRef<{
    cursor: string;
    page: CommentPage;
    ticket: ReturnType<typeof auth.commentLikes.read>;
  } | null>(null);
  const cancelRead = useCallback(() => {
    reads.current++;
    readController.current?.abort();
    readController.current = null;
    reading.current = false;
    buffered.current = null;
  }, []);
  // Only approved navigation is published. Cancelling the dirty dialog does not abort.
  useEffect(
    () => subscribeNavigation(cancelRead),
    [subscribeNavigation, cancelRead],
  );
  useLayoutEffect(() => {
    cancelRead();
  }, [auth.memberRequests, cancelRead]);
  const [inputFocused, setInputFocused] = useState(false);
  const [deleting, setDeleting] = useState<Comment | null>(null);
  const [discard, setDiscard] = useState<EditorAction | null>(null);
  const [focusTarget, setFocusTarget] = useState<{ id: string; scroll?: boolean } | null>(null);
  const requestFocus = (id: string | null) =>
    setFocusTarget(id ? { id } : null);
  // original is the edit-start snapshot; confirmed is the latest accepted server body.
  // Every save/discard/departure decision uses this same comparison.
  const editDirty = !!editor && editor.value !== (editor.confirmed?.content ?? editor.original);
  const blocker = useUnsavedChanges(draft.length > 0 || editDirty);
  const permitted = auth.status === "authenticated" && !auth.pending;
  const owner = (c: Comment) =>
    permitted && auth.member?.memberId === c.author.memberId;
  function pauseAuto() {
    // 입력 중 도착한 자동 GET도 폼을 밀어내지 않는다. 수동 추가는 명시적 조작이다.
    const area = section.current;
    const composerBounds = composer.current?.getBoundingClientRect();
    return (
      !area ||
      !!area.querySelector('dialog[open], textarea[id^="comment-edit-"]') ||
      !!area.querySelector<HTMLTextAreaElement>("#comment-draft")?.value ||
      !!area.querySelector(".community-comment-form:focus-within") ||
      (!!composerBounds &&
        composerBounds.top < window.innerHeight &&
        composerBounds.bottom > 0)
    );
  }
  function showToast(
    message: string,
    valid: () => boolean,
    variant: "success" | "notice" = "success",
  ) {
    const id = ++toastSequence.current;
    setToast({
      id,
      message,
      variant,
      valid,
      dismiss: () =>
        setToast((previous) => (previous?.id === id ? null : previous)),
    });
  }
  async function load(
    more = false,
    automatic = false,
    targetId?: number,
    announce = false,
  ) {
    if (
      reading.current ||
      writing.current ||
      !scope.isActive() ||
      auth.status === "checking" ||
      auth.pending ||
      auth.memberRequests.paused
    )
      return;
    if (automatic && autoStalled.current) return;
    if (more && (!page.hasNext || !page.nextCursor)) return;
    reading.current = true;
    const requestedSort = sortRef.current;
    const ticket = ++reads.current;
    const controller = new AbortController();
    readController.current = controller;
    let likeRead = auth.commentLikes.read();
    const session = auth.member
      ? auth.captureMemberRequest(auth.member.memberId)
      : null;
    const current = () =>
      scope.isActive() &&
      readController.current === controller &&
      !controller.signal.aborted &&
      likeRead.valid() &&
      (!session || session.isCurrent()) &&
      ticket === reads.current;
    setLoading(true);
    setReadError("");
    setReadMore(more);
    try {
      const seen = new Set(more ? cursors.current : []);
      const track = (data: CommentPage) => {
        if (!data.nextCursor) return;
        const key = commentCursorKey(data.nextCursor);
        if (seen.has(key)) throw commentError(200, null, false);
        seen.add(key);
      };
      let result: CommentPage;
      let fetched: Comment[] = [];
      let segments = pageCount.current;
      if (more) {
        const cursor = JSON.stringify(page.nextCursor);
        if (
          buffered.current?.cursor === cursor &&
          buffered.current.ticket.valid()
        ) {
          result = buffered.current.page;
          likeRead = buffered.current.ticket;
        } else
          result = await getComments(
            postId,
            page.nextCursor,
            controller.signal,
            undefined,
            requestedSort,
          );
        fetched = result.items;
        if (!current()) return;
        if (automatic && pauseAuto()) {
          buffered.current = { cursor, page: result, ticket: likeRead };
          return;
        }
        buffered.current = null;
        track(result);
        result = { ...result, items: mergeComments(page.items, result.items) };
        segments++;
      } else {
        buffered.current = null;
        result = await getComments(
          postId,
          null,
          controller.signal,
          undefined,
          requestedSort,
        );
        track(result);
        segments = 1;
        while (
          result.hasNext &&
          (segments < pageCount.current ||
            (!!targetId && !result.items.some((c) => c.commentId === targetId)))
        ) {
          if (!current()) return;
          const next = await getComments(
            postId,
            result.nextCursor,
            controller.signal,
            undefined,
            requestedSort,
          );
          track(next);
          result = { ...next, items: mergeComments(result.items, next.items) };
          segments++;
        }
        fetched = result.items;
      }

      if (current()) {
        // 이번 유효한 서버 조회에서 확인된 댓글만 제외 상태 해제
        // 여기서 result.items보다 fetched를 쓰는 것이 중요
        // fetched : 이번 요청에서 서버로부터 실제로 조회한 댓글
        // result.items: '더 보기'에서는 이전 화면 목록과 새 응답을 합친 결과
        for (const comment of fetched) {
          removed.current.delete(comment.commentId);
        }

        // Functional update preserves typing performed while the GET was pending.
        setEditor(previous => {
          if (!previous) return previous;
          const found = fetched.find(c => c.commentId === previous.id);
          if (found) {
            return { ...previous,
              contentUpdatedAt: found.contentUpdatedAt,
              confirmed: found };
          }
          return previous;
        });
        likeRead.accept(fetched.map((c) => ({ ...c, id: c.commentId })));

        setLoadedContext(likeContext);

        // A moving popularity cursor may return only known IDs. Keep its cursor,
        // but require explicit pagination instead of an automatic request loop.
        autoStalled.current =
          more &&
          fetched.every((c) =>
            page.items.some((old) => old.commentId === c.commentId),
          );
        cursors.current = [...seen];
        pageCount.current = segments;

        if (
          editor &&
          page.items.some((c) => c.commentId === editor.id) !==
            result.items.some((c) => c.commentId === editor.id) &&
          document.activeElement?.id === `comment-edit-${editor.id}`
        )
          requestFocus(`comment-edit-${editor.id}`);

        setCreated((receipts) =>
          receipts.filter(
            (receipt) =>
              !result.items.some((c) => c.commentId === receipt.commentId),
          ),
        );
        if (displayedSort !== requestedSort) captureTransition();
        setDisplayedSort(requestedSort);

        setPage({
          ...result,
          items: result.items.filter((c) => !removed.current.has(c.commentId)),
        });

        if (announce)
          showToast(
            "댓글 목록을 새로고침했습니다.",
            () => likeRead.valid() && scope.isActive(),
          );
        return true;
      }
    } catch (e) {
      if (current()) {
        if (e instanceof CommentError && e.status === 401) session?.expire();
        setReadError(
          e instanceof CommentError ? e.message : "댓글을 불러오지 못했습니다.",
        );
      }
      return false;
    } finally {
      if (ticket === reads.current && readController.current === controller) {
        reading.current = false;
        readController.current = null;
        if (scope.isActive()) setLoading(false);
      }
    }
  }

  function switchSort(next: CommentSort) {
    if (next === sortRef.current || writing.current) return;
    sortRef.current = next;
    setSort(next);
    cancelRead();
    pageCount.current = 1;
    cursors.current = [];
    autoStalled.current = false;
    setPage((previous) => ({ ...previous, hasNext: false, nextCursor: null }));
    setLoadedContext("");
    setReadError("");
    setLinkNotice("");
    void load();
  }

  useEffect(() => {
    if (focusTarget && scope.isActive()) {
      const element = document.getElementById(focusTarget.id);
      element?.focus({ preventScroll: true });
      if (focusTarget.scroll) element?.scrollIntoView({ block: "center", behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth" });
    }
  }, [focusTarget, scope]);
  useEffect(() => cancelRead, [cancelRead]);

  // An explicit comment link may point beyond the first public cursor page.
  useEffect(() => {
    const select = () => {
      const match = /^#comment-([1-9]\d*)$/.exec(location.hash);
      const id = match ? Number(match[1]) : NaN;
      setLinkedComment(Number.isSafeInteger(id) ? id : null);
      setLinkNotice("");
    };
    select();
    const cancel = () => setLinkedComment(null);
    window.addEventListener("hashchange", select);
    window.addEventListener("pointerdown", cancel);
    window.addEventListener("keydown", cancel);
    return () => {
      window.removeEventListener("hashchange", select);
      window.removeEventListener("pointerdown", cancel);
      window.removeEventListener("keydown", cancel);
    };
  }, [postId]);
  const followComment = useEffectEvent(() => {
    if (
      !linkedComment ||
      !scope.isActive() ||
      reading.current ||
      writing.current ||
      readError
    )
      return;
    const target = document.getElementById(`comment-${linkedComment}`);
    if (target) {
      target.focus({ preventScroll: true });
      target.scrollIntoView({
        behavior: matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
        block: "center",
      });
      setLinkedComment(null);
    } else if (page.hasNext && page.nextCursor) {
      void load(true);
    } else {
      setLinkNotice(
        "해당 댓글을 찾을 수 없어요. 삭제되었거나 더 이상 표시되지 않는 댓글일 수 있습니다.",
      );
      setLinkedComment(null);
    }
  });
  useEffect(() => {
    const frame = requestAnimationFrame(() => followComment());
    return () => cancelAnimationFrame(frame);
  }, [linkedComment, page, loading, readError]);

  const refreshIdentity = useEffectEvent(() => {
    cancelRead();
    setLoading(false);
    void load();
  });
  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) refreshIdentity();
    });
    return () => {
      active = false;
    };
  }, [identityVersion, likeContext]);

  const autoLoad = useEffectEvent(() => {
    if (!pauseAuto()) void load(true, true);
  });
  useEffect(() => {
    if (
      !page.hasNext ||
      loading ||
      busy ||
      readError ||
      draft ||
      editor ||
      inputFocused ||
      loginPrompt ||
      deleting ||
      discard ||
      !sentinel.current ||
      !("IntersectionObserver" in window)
    )
      return;
    let near = false;
    const observer = new IntersectionObserver(
      (entries) => {
        near = entries.some((entry) => entry.isIntersecting);
        if (near) autoLoad();
      },
      { rootMargin: "320px 0px" },
    );
    // 폼이 보일 때 멈춘 뒤 위로 이동하면, 감시 영역을 벗어나지 않아도 재평가한다.
    const onScroll = () => {
      if (near) autoLoad();
    };
    observer.observe(sentinel.current);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", onScroll);
    };
  }, [
    page.hasNext,
    page.nextCursor,
    loading,
    busy,
    readError,
    draft,
    editor,
    inputFocused,
    loginPrompt,
    deleting,
    discard,
  ]);

  function edit(c: Comment) {
    if (!auth.member) return;
    setEditor({ id: c.commentId, original: c.content, value: c.content, memberId: auth.member.memberId,
      postId, contentUpdatedAt: c.contentUpdatedAt, confirmed: c });
    setFields((f) => ({ ...f, [String(c.commentId)]: "" }));
    setError(null);
    requestFocus(`comment-edit-${c.commentId}`);
  }
  function transitionEditor(action: EditorAction) {
    if (writing.current || busy || !scope.isActive()) return;
    if (editDirty) {
      setDiscard(action);
      requestFocus(null);
      return;
    }
    finishEdit(action);
  }
  function cancelEdit() {
    transitionEditor({ kind: "cancel" });
  }
  function finishEdit(action: EditorAction = { kind: "cancel" }) {
    if (writing.current || busy || !scope.isActive()) return;
    // Recheck the live list/ownership after a confirmation, rather than acting
    // on a stale comment snapshot or a previous login.
    const target =
      action.kind === "cancel"
        ? null
        : page.items.find((c) => c.commentId === action.comment.commentId);
    if (action.kind !== "cancel" && (!target || !owner(target))) {
      setDiscard(null);
      setNotice(
        "해당 댓글을 수정하거나 삭제할 수 없습니다. 목록을 다시 확인해 주세요.",
      );
      requestFocus(editor ? `comment-edit-${editor.id}` : "comments-refresh");
      return;
    }
    const id = editor?.id;
    setEditor(null);
    setDiscard(null);
    setError(null);
    if (action.kind === "edit" && target) edit(target);
    else if (action.kind === "delete" && target) {
      requestFocus(null);
      setDeleting(target);
    } else
      requestFocus(
        page.items.some((c) => c.commentId === id)
          ? `comment-edit-button-${id}`
          : "comments-refresh",
      );
  }
  function removeItem(id: number) {
    removed.current.add(id);

    setCreated((receipts) =>
      receipts.filter((receipt) => receipt.commentId !== id),
    );

    setPage((p) => ({
      ...p,
      items: p.items.filter((c) => c.commentId !== id),
    }));

    setDeleting(null);

    setEditor(previous => previous?.id === id ? null : previous);
    requestFocus("comments-refresh");
  }

  async function mutate(
    method: "POST" | "PUT" | "DELETE",
    id: number | null,
    content: string,
    event?: FormEvent,
  ) {
    event?.preventDefault();
    if (writing.current || !permitted || !scope.isActive()) return;
    const field = method === "DELETE" ? "" : validateComment(content);
    if (method === "POST" && field === requiredContent) {
      setFields((f) => ({ ...f, draft: "" }));
      if (method === "POST") setCreateError(null);
      else setError(null);
      const session = auth.member
        ? auth.captureMemberRequest(auth.member.memberId)
        : null;
      showToast(
        "내용을 입력해 주세요.",
        () => !!session?.isCurrent(),
        "notice",
      );
      return;
    }
    if (field) {
      setFields((f) => ({ ...f, [id ?? "draft"]: field }));
      if (method === "POST") setCreateError(null);
      else setError(null);
      requestFocus(id ? `comment-edit-${id}` : "comment-draft");
      document
        .getElementById(id ? `comment-edit-${id}` : "comment-draft")
        ?.focus();
      return;
    }
    writing.current = true;
    cancelRead();
    setFields((f) => ({ ...f, [id ?? "draft"]: "" }));
    setBusy(true);
    setLoading(false);
    if (method === "POST") setCreateError(null);
    else setError(null);
    setNotice("");
    const current = scope.begin();
    const session = auth.member
      ? auth.captureMemberRequest(auth.member.memberId)
      : null;
    let refresh = false;
    try {
      const result = await changeComment(postId, method, id, content, () => current() && !!session?.isCurrent());
      if (result === null) return;
      // 이미 보낸 요청의 성공은 이탈 후에도 목록에 반영하되 이전 폼은 건드리지 않는다.
      if (session?.isCurrent()) {
        postCommentsChanged(postId);
        if (method === "POST" || method === "DELETE") postListChanged();
        else memberCommentsChanged();
      }
      if (!current() || !session?.isCurrent()) return;
      if (session?.isCurrent()) {
        const message =
          method === "POST"
            ? "댓글을 등록했습니다."
            : method === "PUT"
              ? "댓글을 수정했습니다."
              : "댓글을 삭제했습니다.";
        showToast(message, session.isCurrent);
      }
      if (method === "DELETE") {
        removeItem(result);
      }
      if (method === "POST") {
        setDraft((value) => (value === content ? "" : value));
        setCreated((receipts) => [
          ...receipts.filter((receipt) => receipt.commentId !== result),
          { commentId: result, content },
        ]);
        refresh = true;
      }
      if (method === "PUT") {
        setEditor(null);
        requestFocus(`comment-edit-button-${id}`);
        refresh = true;
      }
    } catch (e) {
      const failure =
        e instanceof CommentError
          ? e
          : new CommentError(
              0,
              "UNKNOWN",
              "결과를 확인하지 못했습니다. 목록을 확인해 주세요.",
              "",
              true,
            );
      // 불확실한 결과/없어진 댓글은 서버 목록으로 확인한다. 변경 요청 재전송은 하지 않는다.
      if (
        session?.isCurrent() &&
        (failure.uncertain ||
          failure.code === "COMMENT_NOT_FOUND" ||
          failure.code === "POST_NOT_FOUND")
      ) {
        postCommentsChanged(postId);
        postListChanged();
      }
      if (!current() || !session?.isCurrent()) return;
      if ((method === "PUT" || method === "POST") && failure.status === 401 && failure.code === "AUTHENTICATION_REQUIRED") {
        // Check ownership before expiry invalidates this session's requests.
        if (session.expire("expired", { notice: false })) {
          setEditor(null);
          setError(null);
          setToast(null);
          setNotice("");
          setDiscard(null);
          setDeleting(null);
          setLoginPrompt(false);
          setCreateError(null);
          setExpiredAction(method === "POST" ? "create" : "edit");
        }
        return;
      }
      if (method === "PUT" && id && failure.status === 404 &&
          (failure.code === "COMMENT_NOT_FOUND" || failure.code === "POST_NOT_FOUND")) {
        removeItem(id);
        setError(null);
        refresh = true;
        // Refresh is disabled while rereading; the persistent heading remains focusable.
        requestFocus("comments-title");
        showToast("현재 이 댓글을 수정할 수 없어요.", session.isCurrent, "notice");
      } else if (method === "DELETE" && failure.code === "COMMENT_NOT_FOUND" && id) {
        removeItem(id);
        setNotice("댓글이 없거나 이미 삭제되었습니다.");
      } else {
        if (method === "POST") setCreateError(failure.field ? null : failure);
        else setError(failure.field ? null : failure);
        if (failure.field) {
          setFields((f) => ({ ...f, [id ?? "draft"]: failure.field }));
          const target = id ? `comment-edit-${id}` : "comment-draft";
          requestFocus(target);
          document.getElementById(target)?.focus();
        }
      }
      if (failure.status === 401) void auth.refresh();
    } finally {
      writing.current = false;
      if (current()) {
        setBusy(false);
        if (refresh) void load();
      }
    }
  }
  const fieldFor = (id: number | null) => fields[id ?? "draft"] ?? "";
  function clearField(id: number | null, value: string) {
    setFields((f) =>
      f[id ?? "draft"] ? { ...f, [id ?? "draft"]: validateComment(value) } : f,
    );
  }
  const outsidePage = !!editor && !page.items.some(c => c.commentId === editor.id);
  const alreadyMatches = !!editor && editor.confirmed?.content === editor.value;
  const serverChanged = !!editor && !!editor.confirmed && editor.confirmed.content !== editor.original && !alreadyMatches;
  function editForm() {
    if (!editor) return null;
    const errorId = `comment-edit-${editor.id}-error`;
    return (
      <form className="community-comment-form" onSubmit={e => void mutate("PUT", editor.id, editor.value, e)}>
        <label htmlFor={`comment-edit-${editor.id}`}>댓글 수정</label>
        <textarea id={`comment-edit-${editor.id}`} value={editor.value} disabled={busy}
          aria-invalid={!!fieldFor(editor.id)}
          aria-describedby={[fieldFor(editor.id) && errorId, error && `${errorId}-request`].filter(Boolean).join(" ") || undefined}
          onChange={e => {
            blocker.markDirty();
            clearField(editor.id, e.target.value);
            setEditor({ ...editor, value: e.target.value });
          }} />
        {fieldFor(editor.id) && <p id={errorId} className="community-comment-field-error" role="alert">{fieldFor(editor.id)}</p>}
        {error && <p id={`${errorId}-request`} role="alert" className="community-error">{error.uncertain ? "수정 결과를 확인하지 못했어요. 잠시 후 다시 시도해 주세요." : error.message}</p>}
        <span>{characterCount(editor.value)} / 2,000자</span>
        {outsidePage && <p className="community-help">현재 목록에 없는 댓글을 수정하고 있어요. 입력한 내용은 유지했어요.</p>}
        {serverChanged && <p className="community-help">댓글 내용이 변경됐어요. 입력한 내용은 유지했어요.</p>}
        {alreadyMatches && !!error?.uncertain && <p className="community-help">현재 댓글에 같은 내용이 반영되어 있어요.</p>}
        {!busy && !editDirty && <p id={`comment-edit-${editor.id}-save-help`} className="community-help">내용을 변경하면 저장할 수 있어요.</p>}
        <div className="community-comment-actions">
          <button className="iyum-button iyum-button-outline" type="button" disabled={busy} onClick={cancelEdit}>수정 취소</button>
          <button className="iyum-button iyum-button-primary community-comment-save" aria-busy={busy}
            aria-describedby={!busy && !editDirty ? `comment-edit-${editor.id}-save-help` : undefined}
            disabled={busy || !permitted || editor.memberId !== auth.member?.memberId ||
              (!!editor.confirmed && !owner(editor.confirmed)) || !editDirty}>
            {busy ? "처리 중…" : "댓글 저장"}
          </button>
        </div>
      </form>
    );
  }

  return (
    <section
      ref={section}
      className="community-comments"
      aria-labelledby="comments-title"
      onFocusCapture={(e) =>
        setInputFocused(
          !!(e.target as HTMLElement).closest(".community-comment-form"),
        )
      }
      onBlurCapture={(e) => {
        if (
          !(e.relatedTarget instanceof HTMLElement) ||
          !e.relatedTarget.closest(".community-comment-form")
        )
          setInputFocused(false);
      }}
    >
      <div className="community-list-heading">
        <h2 id="comments-title" tabIndex={-1}>
          댓글
        </h2>
        <button
          id="comments-refresh"
          className="iyum-text-link community-comments-refresh"
          disabled={loading || busy}
          aria-label="목록 새로고침"
          aria-busy={loading && !readMore}
          onClick={() => void load(false, false, undefined, true)}
        >
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            focusable="false"
          >
            <path d="M20 7v5h-5M4 17v-5h5" />
            <path d="M6.1 6.1A8 8 0 0 1 19.5 9M4.5 15A8 8 0 0 0 17.9 17.9" />
          </svg>
          <span>{loading && !readMore ? "새로고침 중…" : "목록 새로고침"}</span>
        </button>
      </div>
      <div ref={composer} className="community-comment-composer">
        {created.some(
          (receipt) =>
            !page.items.some((c) => c.commentId === receipt.commentId),
        ) && (
          <section
            className="community-comment-receipts"
            aria-labelledby="comment-receipts-title"
          >
            <h3 id="comment-receipts-title">댓글을 등록했어요</h3>
            <p className="community-help">
              방금 등록한 댓글입니다. 목록에서 불러오면 해당 위치에 함께
              표시됩니다.
            </p>
            <ul>
              {created
                .filter(
                  (receipt) =>
                    !page.items.some((c) => c.commentId === receipt.commentId),
                )
                .map((receipt) => (
                  <li
                    key={receipt.commentId}
                    data-created-comment-id={receipt.commentId}
                  >
                    <span>방금 등록한 댓글</span>
                    <p className="community-comment-body">{receipt.content}</p>
                  </li>
                ))}
            </ul>
          </section>
        )}
        <form
          className="community-comment-form"
          onSubmit={(e) => void mutate("POST", null, draft, e)}
        >
          <label htmlFor="comment-draft">댓글 남기기</label>
          <textarea
            id="comment-draft"
            placeholder={auth.status === "anonymous" && !auth.pending ? "로그인 후 댓글을 남길 수 있어요" : "댓글로 이야기를 나눠보세요"}
            value={draft}
            disabled={busy}
            aria-invalid={!!fieldFor(null)}
            aria-describedby={[
              "comment-count",
              createError && "comment-create-error",
              fieldFor(null) && "comment-draft-error",
              (auth.status === "checking" || auth.status === "error" || !!auth.pending) && "comment-auth-help",
            ]
              .filter(Boolean)
              .join(" ")}
            onChange={(e) => {
              blocker.markDirty();
              setToast((previous) =>
                previous?.variant === "notice" ? null : previous,
              );
              clearField(null, e.target.value);
              setDraft(e.target.value);
            }}
          />
          {fieldFor(null) && (
            <p
              id="comment-draft-error"
              className="community-comment-field-error"
              role="alert"
            >
              {fieldFor(null)}
            </p>
          )}
          {createError && (
            <div
              id="comment-create-error"
              className="community-error"
              role="alert"
            >
              <p>{createError.message}</p>
              {createError.uncertain && (
                <p>
                  자동 재등록하지 않습니다. 댓글 목록 새로고침과 더 보기로 등록
                  결과를 확인해 주세요.
                </p>
              )}
            </div>
          )}
          <span id="comment-count">{characterCount(draft)} / 2,000자</span>
          {(auth.status === "checking" || auth.status === "error" || !!auth.pending) && (
            <p id="comment-auth-help" role="status">
              {auth.status === "checking" || auth.pending
                ? auth.pending === "logout"
                  ? "로그아웃하고 있습니다. 입력한 내용은 유지됩니다."
                  : "로그인 상태를 확인하고 있습니다."
                : auth.status === "error"
                  ? "로그인 상태를 확인하지 못했습니다. 다시 확인해 주세요. 입력한 내용은 유지됩니다."
                  : null}
            </p>
          )}
          {auth.status === "anonymous" && !auth.pending ? (
            <button
              type="button"
              className="iyum-button iyum-button-primary"
              onClick={() => {
                if (scope.isActive() && !loginMoving.current)
                  setLoginPrompt(true);
              }}
            >
              댓글 등록
            </button>
          ) : auth.status === "error" && !auth.pending ? (
            <button
              type="button"
              className="iyum-button iyum-button-outline"
              disabled={busy}
              onClick={() => void auth.refresh()}
              aria-describedby="comment-auth-help"
            >
              로그인 상태 다시 확인
            </button>
          ) : (
            <button
              className="iyum-button iyum-button-primary"
              disabled={!permitted || busy}
            >
              {busy
                ? "처리 중…"
                : auth.pending === "logout"
                  ? "로그아웃 중…"
                  : !permitted
                    ? "로그인 확인 중…"
                    : "댓글 등록"}
            </button>
          )}
        </form>
      </div>
      <CommentSortMenu
        value={sort}
        onChange={switchSort}
        disabled={busy || !!auth.pending || auth.memberRequests.paused}
      />
      {linkNotice && (
        <p role="status" className="community-help">
          {linkNotice}
        </p>
      )}
      <div role="status" aria-atomic="true" className="comment-toast-announcer">
        {toast && toast.valid() && scope.isActive() && (
          <CommentSuccessToast
            key={toast.id}
            message={toast.message}
            variant={toast.variant}
            onDismiss={toast.dismiss}
          />
        )}
      </div>
      <p
        role="status"
        className={`community-comments-status${notice ? "" : " sr-only"}`}
      >
        {notice}
        {loading ? " 댓글을 불러오고 있습니다." : ""}
      </p>
      {readError && (
        <div className="community-error" role="alert">
          <p>{readError} 기존 목록과 입력은 유지됩니다.</p>
          <button
            className="iyum-text-link"
            disabled={loading || busy}
            onClick={() => void load(readMore, false, undefined, !readMore)}
          >
            댓글 조회 다시 시도
          </button>
        </div>
      )}
      {!readError && !loading && page.items.length === 0 && (
        <p>아직 댓글이 없습니다. 첫 이야기를 나눠 주세요.</p>
      )}
      {displayedSort !== sort && (
        <p role="status" className="community-help">
          {loading
            ? "정렬을 변경하고 있습니다. 이전 목록을 표시합니다."
            : "정렬 변경 전 목록입니다. 다시 조회해 주세요."}
        </p>
      )}
      {outsidePage && editForm()}
      <ul ref={commentList} className="community-comment-list">
        {page.items.map((c) => (
          <CommentItem
            key={c.commentId}
            comment={c}
            anchorId={`comment-${c.commentId}`}
            body={
              editor?.id === c.commentId
                ? editForm()
                : undefined
            }
            reactions={
              <CommentLikeButton
                postId={postId}
                comment={c}
                ready={loadedContext === likeContext}
                reread={(id) => load(false, false, id)}
              />
            }
            more={<ReportButton postId={postId} commentId={c.commentId} authorId={c.author.memberId} label={c.content.slice(0,120)} />}
            actions={
              owner(c) &&
              editor?.id !== c.commentId ? (
                <div className="community-comment-tools">
                  <button
                    id={`comment-edit-button-${c.commentId}`}
                    type="button"
                    className="community-comment-tool"
                    aria-label="댓글 수정"
                    disabled={busy}
                    onClick={() =>
                      transitionEditor({ kind: "edit", comment: c })
                    }
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    className="community-comment-tool"
                    aria-label="댓글 삭제"
                    disabled={busy}
                    onClick={() =>
                      transitionEditor({ kind: "delete", comment: c })
                    }
                  >
                    삭제
                  </button>
                </div>
              ) : null
            }
          />
        ))}
      </ul>
      {page.hasNext && (
        <div
          ref={sentinel}
          className="community-comment-sentinel"
          aria-hidden="true"
        />
      )}
      {page.hasNext && (
        <button
          className="iyum-button iyum-button-outline"
          disabled={loading || busy}
          onClick={() => void load(true)}
        >
          댓글 더 보기
        </button>
      )}
      {error && !deleting && !editor && (
        <div className="community-error" role="alert">
          <p>{error.message}</p>
          {error.uncertain && (
            <p>
              자동 재시도하지 않습니다. 댓글 목록 새로고침과 더 보기로 확인해
              주세요.
            </p>
          )}
        </div>
      )}
      {(expiredAction || loginPrompt) && scope.isActive() && !guard.pending && (
        <CommentAuthDialog idPrefix={expiredAction ? "comment-expired" : "comment-login"}
          action={expiredAction ?? "login"} hasDraft={draft.length > 0}
          status={auth.status} pending={!!auth.pending}
          onClose={() => {
            if (expiredAction) requestFocus(expiredAction === "create" ? "comment-draft" : "comments-title");
            setExpiredAction(null); setLoginPrompt(false);
          }}
          onRetry={() => void auth.refresh()}
          onLogin={() => {
            if (!scope.isActive() || auth.pending || loginMoving.current || auth.status === "checking") return;
            setExpiredAction(null); setLoginPrompt(false);
            if (auth.status === "authenticated") { requestFocus("comment-draft"); return; }
            if (auth.status !== "anonymous") return;
            loginMoving.current = true;
            // This dialog already explains loss of the independent new-comment draft.
            guard.confirmNavigation(() => router.push(`/login?from=${encodeURIComponent(returnTo)}`));
          }} />
      )}
      {deleting && (
        <CommunityDialog
          titleId="comment-delete-title"
          descriptionId="comment-delete-description"
          onCancel={() => {
            if (!busy) setDeleting(null);
          }}
        >
          <h2 id="comment-delete-title">댓글을 삭제할까요?</h2>
          <p id="comment-delete-description">
            삭제하면 목록에서 더 이상 볼 수 없습니다. 요청 중에는 이 창을 닫을
            수 없습니다.
          </p>
          {error && <p role="alert">{error.message}</p>}
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              aria-disabled={busy}
              onClick={() => {
                if (!busy) setDeleting(null);
              }}
            >
              취소
            </button>
            <button
              className="iyum-button community-danger"
              disabled={busy || !owner(deleting)}
              onClick={() => void mutate("DELETE", deleting.commentId, "")}
            >
              {busy ? "삭제 중…" : "댓글 삭제하기"}
            </button>
          </div>
        </CommunityDialog>
      )}
      {discard && (
        <CommunityDialog
          titleId="comment-discard-title"
          onCancel={() => setDiscard(null)}
        >
          <h2 id="comment-discard-title">수정한 내용을 버릴까요?</h2>
          <p>
            저장하지 않은 수정 내용은 사라집니다.
            {discard.kind === "edit"
              ? " 다른 댓글 수정으로 이동합니다."
              : discard.kind === "delete"
                ? " 선택한 댓글의 삭제 확인으로 이동합니다."
                : ""}
          </p>
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              onClick={() => setDiscard(null)}
            >
              계속 수정
            </button>
            <button
              className="iyum-button iyum-button-primary"
              onClick={() => finishEdit(discard)}
            >
              수정 내용 버리기
            </button>
          </div>
        </CommunityDialog>
      )}
      {blocker.state === "blocked" && (
        <CommunityDialog titleId="comment-leave-title" onCancel={blocker.reset}>
          <h2 id="comment-leave-title">작성 중인 댓글을 두고 나갈까요?</h2>
          <p>
            화면의 입력은 보관되지 않습니다. 이미 전송한 요청은 서버에서 처리될
            수 있습니다.
          </p>
          <div className="community-dialog-actions">
            <button
              data-initial-focus
              className="iyum-button iyum-button-outline"
              onClick={blocker.reset}
            >
              계속 작성
            </button>
            <button
              className="iyum-button iyum-button-primary"
              onClick={blocker.proceed}
            >
              나가기
            </button>
          </div>
        </CommunityDialog>
      )}
    </section>
  );
}
