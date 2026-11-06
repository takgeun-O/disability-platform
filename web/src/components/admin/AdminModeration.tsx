"use client";
import {
  createContext,
  useCallback,
  useContext,
  useId,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useAuth } from "@/components/AuthProvider";
import { useNavigationRequest } from "@/components/NavigationGuard";
import OverlayDialog from "@/components/OverlayDialog";
import CommentSuccessToast from "@/components/community/CommentSuccessToast";
import {
  postChanged,
  postCommentsChanged,
  postListChanged,
} from "@/lib/community-mutations";
import {
  moderate,
  moderationReasonError,
  ModerationError,
  type ModerationTarget,
} from "@/lib/admin-moderation";

import {
  ACTION_REASONS,
  MODERATION_REASONS,
  isModerationReasonType,
  type ModerationReasonType,
} from "@/lib/moderation-reasons";
import { characterCount, normalizeTitle } from "@/lib/community-posts";

type Actions = {
  revision: number;
  readCurrent: (revision: number) => boolean;
  locked: boolean;
  completed: boolean;
  open: (target: ModerationTarget) => void;
  deny: () => void;
};
const Context = createContext<Actions | null>(null);
export const useAdminModeration = () => useContext(Context)!;
export default function AdminModeration({ children }: { children: ReactNode }) {
  const auth = useAuth(),
    scope = useNavigationRequest(),
    uid = useId();
  const epoch = useRef(0),
    flight = useRef<object | null>(null),
    blocked = useRef(false);
  const [revision, setRevision] = useState(0),
    [denied, setDenied] = useState(false);
  const [target, setTarget] = useState<ModerationTarget | null>(null),
    [reason, setReason] = useState("");
  const [reasonType, setReasonType] = useState<ModerationReasonType | "">("");
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<ModerationError | null>(null);
  const [notice, setNotice] = useState(""),
    [toast, setToast] = useState("");
  const [completed, setCompleted] = useState(false),
    [needsCheck, setNeedsCheck] = useState(false);
  const dismiss = useCallback(() => setToast(""), []);
  const readCurrent = useCallback(
    (version: number) => !blocked.current && version === epoch.current,
    [],
  );
  const deny = useCallback(() => {
    blocked.current = true;
    epoch.current++;
    setDenied(true);
    setTarget(null);
    setToast("");
    setNotice("");
  }, []);
  function refresh() {
    setNeedsCheck(false);
    epoch.current++;
    setRevision(epoch.current);
  }
  function close() {
    if (!flight.current) setTarget(null);
  }
  async function submit() {
    if (!target || flight.current) return;
    const validation = moderationReasonError(reason, reasonType, target.action);
    if (validation) {
      setError(new ModerationError(400, "INVALID_REASON", validation));
      return;
    }
    const owner = {},
      navigation = scope.begin(),
      session = auth.captureMemberRequest(auth.member!.memberId);
    const current = () =>
      flight.current === owner &&
      !blocked.current &&
      navigation() &&
      session.isCurrent();
    flight.current = owner;
    setBusy(true);
    setError(null);
    setCompleted(false);
    setNotice("");
    setToast("");
    try {
      const response = await moderate(
        target,
        { reasonType, reasonDetail: reason },
        current,
      );
      if (!current() || !response) return;
      if (target.commentId === undefined) postChanged(String(target.postId));
      else {
        postCommentsChanged(String(target.postId));
        postListChanged();
      }
      const message = response.changed
        ? `${target.commentId === undefined ? "게시글" : "댓글"} ${target.action === "hide" ? "숨김 처리를" : "숨김 해제를"} 완료했습니다.`
        : "이미 요청한 상태입니다. 추가 상태 변경과 운영 기록은 발생하지 않았습니다.";
      setCompleted(true);
      setNotice(message);
      setToast(message);
      setTarget(null);
      refresh();
    } catch (e) {
      if (!current()) return;
      const failure =
        e instanceof ModerationError
          ? e
          : new ModerationError(
              0,
              "UNCERTAIN",
              "처리 결과를 확인하지 못했습니다. 최신 상태를 확인해 주세요.",
              true,
              true,
            );
      if (failure.status === 401) {
        session.expire();
        return;
      }
      if (failure.status === 403 && failure.code !== "CSRF_TOKEN_INVALID") {
        deny();
        return;
      }
      setError(failure);
      if (failure.refresh) {
        if (target.commentId === undefined) postChanged(String(target.postId));
        else {
          postCommentsChanged(String(target.postId));
          postListChanged();
        }
        setNeedsCheck(true);
        setNotice(failure.message);
      }
    } finally {
      if (flight.current === owner) {
        flight.current = null;
        if (navigation()) setBusy(false);
      }
    }
  }
  if (denied)
    return (
      <div className="admin-access-card" role="alert">
        <h1>관리자 권한이 없습니다</h1>
        <p>게시글과 댓글 데이터를 제거했습니다.</p>
      </div>
    );
  const operation = target?.action === "hide" ? "숨김 처리" : "숨김 해제";
  return (
    <Context.Provider
      value={{
        revision,
        readCurrent,
        locked: !!target || busy || needsCheck,
        completed,
        deny,
        open: (next) => {
          if (!flight.current && !target && !needsCheck) {
            setTarget(next);
            setReason("");
            setReasonType("");
            setError(null);
          }
        },
      }}
    >
      <div
        data-admin-feedback
        tabIndex={-1}
        className="admin-moderation-feedback"
      >
        {notice ? (
          <p>{notice}</p>
        ) : (
          <span className="sr-only">게시글·댓글 관리</span>
        )}
        {needsCheck && !target && (
          <button onClick={refresh}>최신 상태 확인</button>
        )}
      </div>
      <div role="status" aria-live="polite">
        {toast && <CommentSuccessToast message={toast} onDismiss={dismiss} />}
      </div>
      {children}
      {target && (
        <OverlayDialog
          titleId={`${uid}-title`}
          descriptionId={`${uid}-description`}
          className="admin-moderation-dialog"
          onClose={close}
          returnFocus="[data-admin-feedback]"
          closeOnBackdropClick={false}
        >
          <h2 id={`${uid}-title`}>
            {target.commentId === undefined ? "게시글" : "댓글"} {operation}
          </h2>
          <p className="admin-moderation-target">
            #{target.commentId ?? target.postId} · {target.label}
          </p>
          <p id={`${uid}-description`}>
            {target.commentId === undefined
              ? "게시글의 공개 상태를 변경합니다. 개별 숨김 댓글의 상태는 바뀌지 않습니다."
              : "이 댓글 자체의 숨김 상태를 변경합니다. 부모 게시글이 숨김 상태라면 숨김을 해제해도 일반 사용자에게 공개되지 않습니다."}{" "}
            처리 사유는 실제 상태 변경 시 운영 기록에 저장됩니다.
          </p>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            <label htmlFor={`${uid}-reason-type`}>처리 사유 (필수)</label>
            <select
              data-initial-focus
              id={`${uid}-reason-type`}
              value={reasonType}
              disabled={busy}
              aria-invalid={
                error?.code === "INVALID_REASON" && reasonType !== "OTHER"
              }
              aria-describedby={
                error?.code === "INVALID_REASON" && reasonType !== "OTHER"
                  ? `${uid}-error`
                  : `${uid}-hint`
              }
              onChange={(e) => {
                const value = e.target.value;
                if (value === "" || isModerationReasonType(value))
                  setReasonType(value);
                if (error?.code === "INVALID_REASON") setError(null);
              }}
            >
              <option value="">처리 사유를 선택해 주세요</option>
              {ACTION_REASONS[target.action].map((value) => (
                <option key={value} value={value}>
                  {MODERATION_REASONS[value]}
                </option>
              ))}
            </select>
            {reasonType === "OTHER" && (
              <>
                <label htmlFor={`${uid}-reason`}>상세 사유 (필수)</label>
                <textarea
                  id={`${uid}-reason`}
                  value={reason}
                  disabled={busy}
                  onChange={(e) => {
                    setReason(e.target.value);
                    if (error?.code === "INVALID_REASON") setError(null);
                  }}
                  aria-invalid={error?.code === "INVALID_REASON"}
                  aria-describedby={error ? `${uid}-error` : `${uid}-hint`}
                />
              </>
            )}
            <p id={`${uid}-hint`}>
              {reasonType === "OTHER"
                ? `${characterCount(normalizeTitle(reason))} / 500자 · 앞뒤 공백을 제외한 상세 사유를 입력해 주세요.`
                : "처리 사유를 선택해 주세요. 기타 사유는 상세 입력이 필요합니다."}
            </p>
            {error && (
              <p id={`${uid}-error`} role="alert">
                {error.message}
              </p>
            )}
            {busy && (
              <p role="status">
                처리 중입니다. 완료 전에는 확인창을 닫을 수 없습니다. 화면을
                떠나도 서버 처리는 취소되지 않을 수 있습니다.
              </p>
            )}
            <div className="admin-moderation-actions">
              <button type="button" disabled={busy} onClick={close}>
                취소
              </button>
              {error?.refresh ? (
                <button
                  type="button"
                  onClick={() => {
                    setNotice(error.message);
                    setTarget(null);
                    refresh();
                  }}
                >
                  최신 상태 확인
                </button>
              ) : (
                <button type="submit" disabled={busy}>
                  {busy ? "처리 중…" : `${operation} 실행`}
                </button>
              )}
            </div>
          </form>
        </OverlayDialog>
      )}
    </Context.Provider>
  );
}
