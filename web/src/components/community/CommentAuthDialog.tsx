"use client";

import CommunityDialog from "./CommunityDialog";

/** Shared presentation. Request/session ownership stays with each screen. */
export default function CommentAuthDialog({ idPrefix, action, hasDraft, status, pending,
  onClose, onLogin, onRetry, dismissLabel = "그대로 머무르기" }: {
  idPrefix: string;
  action: "login" | "create" | "edit";
  hasDraft: boolean;
  status: "checking" | "authenticated" | "anonymous" | "error";
  pending: boolean;
  onClose: () => void;
  onLogin: () => void;
  onRetry: () => void;
  dismissLabel?: "그대로 머무르기" | "닫기";
}) {
  const expired = action !== "login";
  return <CommunityDialog className="comment-auth-dialog" titleId={`${idPrefix}-title`}
    descriptionId={`${idPrefix}-description`} onCancel={onClose}>
    <button type="button" className="comment-auth-close" aria-label="닫기" onClick={onClose}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
    </button>
    <svg className="comment-auth-icon" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V6a4 4 0 0 1 8 0v4M12 14v3" />
    </svg>
    <h2 id={`${idPrefix}-title`}>{expired ? "다시 로그인이 필요해요" : "로그인이 필요해요"}</h2>
    <div className="comment-auth-description" id={`${idPrefix}-description`}>
      <p>{expired ? `로그인 시간이 만료되어 댓글을 ${action === "create" ? "등록" : "수정"}하지 못했어요.`
        : status === "authenticated" && !pending ? "이미 로그인되어 있어요. 현재 화면에서 댓글 작성을 계속할 수 있습니다."
        : "댓글을 등록하려면 로그인이 필요해요. 로그인 화면으로 이동할까요?"}</p>
      {hasDraft && <p className="comment-auth-draft-note">{expired
        ? "이 화면에 머무르면 입력 내용은 유지돼요. 로그인 화면으로 이동하면 입력 내용은 사라져요."
        : "로그인 화면으로 이동하면 입력한 내용은 보관되지 않아요."}</p>}
      {(status === "checking" || pending) && <p role="status">로그인 상태를 확인하고 있습니다.</p>}
      {status === "error" && <p role="status">로그인 상태를 확인하지 못했습니다.</p>}
    </div>
    <div className="community-dialog-actions">
      <button type="button" data-initial-focus className="iyum-button iyum-button-outline" onClick={onClose}>{dismissLabel}</button>
      {status === "error" ? <button type="button" className="iyum-button iyum-button-outline" onClick={onRetry}>로그인 상태 다시 확인</button>
        : <button type="button" className="iyum-button iyum-button-primary" disabled={status === "checking" || pending} onClick={onLogin}>
          {status === "authenticated" ? "댓글 작성 계속하기" : "로그인하기"}
        </button>}
    </div>
  </CommunityDialog>;
}
