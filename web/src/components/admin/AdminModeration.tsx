"use client";
import {
  createContext,
  useCallback,
  useContext,
  useId,
  useLayoutEffect,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { getAdminPost, getAdminComment } from "@/lib/admin-detail";
import { AdminApiError } from "@/lib/admin-posts";
import { useAuth } from "@/components/AuthProvider";
import { useNavigationRequest, useNavigationGuard } from "@/components/NavigationGuard";
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
import AdminContentMeta, { AdminAuthor, AdminStatusBadge, AdminCommentMeta } from "./AdminContentMeta";
import { writtenTime } from "@/lib/written-time";
import { characterCount, normalizeTitle } from "@/lib/community-posts";

type Actions = {
  originScroll: number;
  revision: number;
  readCurrent: (revision: number) => boolean;
  locked: boolean;
  editing: ModerationTarget | null;
  busy: boolean;
  readLocked: boolean;
  recoveryFeedback: ReactNode;
  completed: boolean;
  completion: {target:ModerationTarget;message:string}|null;
  open: (target: ModerationTarget) => void;
  deny: () => void;
  inlineTarget: ModerationTarget | null;
  form: ReactNode;
  feedback: ReactNode;
  requestExit: (action: () => void) => void;
  confirmation: ReactNode;
};
const Context = createContext<Actions | null>(null);
export const useAdminModeration = () => useContext(Context)!;
export default function AdminModeration({ children, inline = false, onDenied, recovery, onRecovery }: { children: ReactNode; inline?: boolean; onDenied?:()=>void; recovery?:ModerationTarget|null; onRecovery?:(target:ModerationTarget|null)=>void }) {
  const guard = useNavigationGuard();
  const [exit, setExit] = useState<(() => void) | null>(null);
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
  const [completion,setCompletion]=useState<{target:ModerationTarget;message:string}|null>(null);
  const [originScroll,setOriginScroll]=useState(0);
  const returnTarget=useRef<ModerationTarget|null>(null);
  const [completed, setCompleted] = useState(false),
    [needsCheck, setNeedsCheck] = useState(!!recovery);
  const [checking,setChecking]=useState(false),[checkError,setCheckError]=useState("");
  const checkFlight=useRef(false);
  const [validationAttempt,setValidationAttempt]=useState(0);
  const invalidField=useRef<string>("");
  const trigger=useRef<HTMLElement|null>(null),returnPending=useRef(false);
  useLayoutEffect(()=>{
    if(!validationAttempt)return;
    const field=document.getElementById(invalidField.current);
    if(field)focusModerationField(field);
  },[validationAttempt]); // Only submit attempts move focus, never typing.
  useLayoutEffect(()=>{
    if(target||!returnPending.current)return;
    const restore=()=>{
      const previous=returnTarget.current;
      const marker=previous?document.querySelector<HTMLElement>(`[data-moderation-location="${previous.postId}:${previous.commentId??'post'}"]`):null;
      const button=trigger.current;
      const fallback=previous?.commentId?document.querySelector<HTMLElement>('.admin-comment-results, .admin-preview-comment'):null;
      const region=marker?.closest('[aria-busy]')??fallback;
      if(completion&&region?.getAttribute('aria-busy')==='true')return;
      const destination=completion?(!inline?document.querySelector<HTMLElement>('[data-admin-feedback]'):marker??fallback??document.querySelector<HTMLElement>('#admin-content-dialog-title')):button?.isConnected&&!button.matches(':disabled')?button:marker??fallback;
      if(destination){destination.focus({preventScroll:true});revealModerationField(destination);}
      returnPending.current=false;observer.disconnect();
    };
    const observer=new MutationObserver(restore);
    observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['aria-busy']});restore();
    return()=>observer.disconnect();
  },[target,completion,inline]);
  useEffect(()=>()=>{checkFlight.current=false},[]);
  const dismiss = useCallback(() => setToast(""), []);
  const readCurrent = useCallback(
    (version: number) => !blocked.current && version === epoch.current,
    [],
  );
  const deny = useCallback(() => {
    onRecovery?.(null);
    onDenied?.();
    blocked.current = true;
    epoch.current++;
    setDenied(true);
    setTarget(null);
    setToast("");
    setNotice("");
  }, [onDenied,onRecovery]);
  function refresh() {
    setNeedsCheck(false);
    epoch.current++;
    setRevision(epoch.current);
  }
  async function checkLatest() {
    if(!recovery||checkFlight.current||flight.current)return;
    checkFlight.current=true;setChecking(true);setCheckError("");
    const navigation=scope.begin(),session=auth.captureMemberRequest(auth.member!.memberId);
    const current=()=>navigation()&&session.isCurrent()&&!blocked.current;
    try {
      if(recovery.commentId===undefined)await getAdminPost(recovery.postId,AbortSignal.timeout(15000));
      else await getAdminComment(recovery.commentId,AbortSignal.timeout(15000));
      if(!current())return;
      onRecovery?.(null);setTarget(null);setNotice("최신 상태를 확인했습니다. 이전 요청의 성공 여부를 의미하지는 않습니다.");refresh();
    } catch(e) {
      if(!current())return;
      if(e instanceof AdminApiError&&e.status===401){session.expire();return}
      if(e instanceof AdminApiError&&e.status===403){deny();return}
      if(e instanceof AdminApiError&&e.status===404){onRecovery?.(null);setTarget(null);setNotice("현재 대상을 찾을 수 없습니다. 목록을 다시 확인해 주세요.");refresh();return}
      setCheckError("최신 상태를 확인하지 못했습니다. 다시 시도해 주세요.");
    } finally {checkFlight.current=false;if(current())setChecking(false)}
  }
  const dirty = inline && !!target && (!!reasonType || !!reason || busy);
  const setDirty = guard.setDirty;
  useLayoutEffect(() => { if (!inline) return; setDirty(dirty); return () => setDirty(false); }, [inline, dirty, setDirty]);
  function requestExit(action: () => void) {
    if (flight.current) return;
    if (dirty) setExit(() => action); else { if(inline){setTarget(null);setNotice("");setToast("")} action(); }
  }
  function close() { requestExit(() => {returnPending.current=true;setTarget(null)}); }

  async function submit() {
    if (!target || flight.current) return;
    const validation = moderationReasonError(reason, reasonType, target.action);
    if (validation) {
      setError(new ModerationError(400, "INVALID_REASON", validation));
      invalidField.current=`${uid}-${reasonType==="OTHER"?"reason":"reason-type"}`;
      setValidationAttempt(n=>n+1);
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
      returnPending.current=true;
      setCompletion({target,message});
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
      if(failure.code==="INVALID_REASON"){invalidField.current=`${uid}-${reasonType==="OTHER"?"reason":"reason-type"}`;setValidationAttempt(n=>n+1);}
      if (failure.refresh) {
        if (target.commentId === undefined) postChanged(String(target.postId));
        else {
          postCommentsChanged(String(target.postId));
          postListChanged();
        }
        setNeedsCheck(true);
        if(inline)onRecovery?.({...target});
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
  const form = target && <section className="admin-inline-moderation" aria-labelledby={`${uid}-title`}>
          <h2 id={`${uid}-title`}>
            {target.commentId === undefined ? "게시글" : "댓글"} #{target.commentId ?? target.postId} {operation}
          </h2>
          <p className="admin-moderation-target">
            {target.label}
          </p>
          <p id={`${uid}-description`}>
            {target.commentId === undefined
              ? target.action==='hide' ? "이 게시글을 일반 사용자에게 숨깁니다. 개별 댓글의 숨김 상태는 변경되지 않습니다." : "이 게시글의 숨김을 해제합니다. 개별 댓글의 숨김 상태는 변경되지 않습니다."
              : target.action==='hide' ? "이 댓글을 일반 사용자에게 숨깁니다." : "이 댓글의 숨김을 해제합니다."}
            {target.commentId!==undefined&&target.action==='restore'&&target.postStatus==='HIDDEN'&&" 소속 게시글이 숨김 상태여서 댓글은 일반 사용자에게 보이지 않습니다."}
          </p>
          <p className="admin-moderation-help">상태가 변경되면 처리 사유가 운영 기록에 저장됩니다.</p>
          {error&&error.code!=="INVALID_REASON"&&<p className="admin-form-error" role="alert">{error.message}</p>}
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
            {error?.code==="INVALID_REASON"&&reasonType!=="OTHER"&&<p id={`${uid}-error`} className="admin-field-error" role="alert"><span aria-hidden="true">⚠ </span><span>{error.message}</span></p>}
            {reasonType === "OTHER" && (
              <>
                <label htmlFor={`${uid}-reason`}>상세 사유 (필수)</label>
                <textarea
                  id={`${uid}-reason`}
                  value={reason}
                  disabled={busy}
                  onChange={(e) => {
                    setReason(e.target.value);
                    if (error?.code === "INVALID_REASON") {const validation=moderationReasonError(e.target.value,reasonType,target.action);setError(validation?new ModerationError(400,"INVALID_REASON",validation):null);}
                  }}
                  aria-invalid={error?.code === "INVALID_REASON"}
                  aria-describedby={error?.code==="INVALID_REASON" ? `${uid}-error ${uid}-hint` : `${uid}-hint`}
                />
              </>
            )}
            {error?.code==="INVALID_REASON"&&reasonType==="OTHER" && (
              <p id={`${uid}-error`} className="admin-field-error" role="alert">
                <span aria-hidden="true">⚠ </span><span>{error.message}</span>
              </p>
            )}
            <p id={`${uid}-hint`}>
              {reasonType === "OTHER"
                ? `${characterCount(normalizeTitle(reason))} / 500자 · 앞뒤 공백 제외`
                : "기타 사유는 상세 입력이 필요합니다."}
            </p>
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
                  disabled={checking}
                  onClick={() => {
                    if(inline){void checkLatest();return}
                    setNotice(error.message);
                    setTarget(null);
                    refresh();
                  }}
                >
                  최신 상태 확인
                </button>
              ) : (
                <button className="admin-manage-primary" type="submit" disabled={busy}>
                  {busy ? "처리 중…" : `${operation} 실행`}
                </button>
              )}
            </div>
          </form>
</section>;
  const recoveryFeedback = inline&&recovery&&<section className="admin-recovery" role="status" aria-label="조치 결과 확인 필요"><p>{recovery.commentId===undefined?"게시글":"댓글"} #{recovery.commentId??recovery.postId} · {recovery.action==="hide"?"숨김 처리":"숨김 해제"} 결과 확인이 필요합니다. 현재 상태를 확인하기 전에는 추가 변경을 할 수 없습니다.</p>{checkError&&<p role="alert">{checkError}</p>}{!target&&<button disabled={checking} onClick={()=>void checkLatest()}>{checking?"확인 중…":"최신 상태 확인"}</button>}</section>;
  const feedback = <>{recoveryFeedback}{notice && !completion && <div className="admin-inline-feedback" role="status"><p>{notice}</p>{!inline && needsCheck && !target && <button onClick={refresh}>최신 상태 확인</button>}</div>}</>;
  const confirmation = (exit || (inline && guard.pending)) && <AdminExitConfirmation>
    <p>{busy ? "처리 중입니다. 완료 후 이동해 주세요." : "작성한 처리 사유를 버리고 이동할까요?"}</p>
    <button type="button" onClick={() => {setExit(null);guard.cancel()}}>계속 작성</button>
    {!busy && <button type="button" onClick={() => {const next=exit;setExit(null);setTarget(null);setDirty(false);if(next)next();else guard.proceed()}}>버리고 이동</button>}
  </AdminExitConfirmation>;
  return (
    <Context.Provider
      value={{
        inlineTarget: inline ? target : null, form, feedback, requestExit, confirmation,
        editing:target,busy,originScroll,
        revision,
        readCurrent,
        recoveryFeedback: recoveryFeedback || (notice&&<p role="status">{notice}</p>),
        readLocked: !!target || busy,
        locked: !!target || busy || needsCheck || checking,
        completed, completion,
        deny,
        open: (next) => {
          if (!flight.current && !target && !needsCheck) {
            trigger.current=document.activeElement instanceof HTMLElement?document.activeElement:null;
            setOriginScroll(document.querySelector<HTMLElement>(".admin-dialog-body")?.scrollTop??0);
            returnTarget.current=next;setCompletion(null);
            setTarget(next);
            setReason("");
            setReasonType("");
            setError(null);
          }
        },
      }}
    >
      {!inline && <><div
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
      </>}
      {children}
      {!inline && target && <OverlayDialog titleId={`${uid}-title`} descriptionId={`${uid}-description`} className="admin-moderation-dialog" onClose={close} fallbackFocus="[data-admin-feedback]" closeOnBackdropClick={false}>{form}</OverlayDialog>}
    </Context.Provider>
  );
}

export function AdminModerationSlot({postId,commentId}:{postId:number;commentId?:number}) {
  const {completion}=useAdminModeration();
  const match=completion?.target.postId===postId&&completion.target.commentId===commentId;
  return <div tabIndex={-1} data-moderation-location={`${postId}:${commentId??'post'}`} className="admin-moderation-result">{match&&<p role="status">{completion.message}</p>}</div>;
}
export function AdminModerationMissingResult({postId,ids}:{postId:number;ids:number[]}) {
  const {completion}=useAdminModeration();
  const id=completion?.target.commentId;
  return completion?.target.postId===postId&&id!==undefined&&!ids.includes(id)?<p role="status">{completion.message} 현재 댓글 결과에 포함되지 않습니다.</p>:null;
}
export function AdminModerationWorkspace() {
  const {inlineTarget:target,form}=useAdminModeration();
  const field=useRef<HTMLDivElement>(null);
  useLayoutEffect(()=>{field.current?.querySelector<HTMLElement>('.admin-action-content h3')?.focus({preventScroll:true})},[target]);
  if(!target)return null;
  const data=target.content,kind=target.commentId===undefined?'게시글':'댓글';
  return <div ref={field} className="admin-action-workspace">
    <section className="admin-action-content" aria-label={`조치 대상 ${kind}`}>
      <div className="admin-post-topline"><h3 tabIndex={-1}>조치 대상 {kind} #{target.commentId??target.postId}</h3>{data&&<AdminStatusBadge status={data.status}/>}</div>
      {data&&<>{'title' in data&&<h4 className="admin-preview-title">{data.title}</h4>}<div className="admin-compact-meta"><AdminAuthor author={data.author}/><time dateTime={data.createdAt}>작성 {writtenTime(data.createdAt).exact}</time></div>
      <div className="admin-comment-body"><span className="admin-comment-body-label">현재 {kind} 본문</span><div className="admin-full-content">{data.content}</div></div>
      </>}
    </section>
    <div className="admin-action-form">{form}</div>
    {data&&<details className="admin-detail-metadata admin-action-metadata"><summary>수정·처리 시각</summary>{'contentUpdatedAt' in data?<AdminCommentMeta item={data} omitCreated/>:<AdminContentMeta item={data} omitCreated/>}</details>}
  </div>;
}
function AdminExitConfirmation({children}:{children:ReactNode}) {
  const ref=useRef<HTMLElement>(null);
  useLayoutEffect(()=>{const previous=document.activeElement;ref.current?.querySelector('button')?.focus({preventScroll:true});return()=>{if(previous instanceof HTMLElement&&previous.isConnected)previous.focus({preventScroll:true})}},[]);
  return <section ref={ref} className="admin-exit-confirm" role="alert" aria-label="입력 유실 확인">{children}</section>;
}

// Scroll only the dialog body, not the background document; run after error DOM commits.
function focusModerationField(field:HTMLElement) {
  field.focus({preventScroll:true});revealModerationField(field);
}
function revealModerationField(field:HTMLElement) {
  const body=field.closest<HTMLElement>('.admin-dialog-body, .admin-moderation-dialog');
  if(!body)return;
  const rect=field.getBoundingClientRect(),bounds=body.getBoundingClientRect();
  const label=field.id?document.querySelector<HTMLElement>(`label[for="${CSS.escape(field.id)}"]`):null;
  const error=field.getAttribute('aria-describedby')?.split(' ').map(id=>document.getElementById(id)).find(e=>e?.getAttribute('role')==='alert');
  const top=Math.min(rect.top,label?.getBoundingClientRect().top??rect.top),bottom=Math.max(rect.bottom,error?.getBoundingClientRect().bottom??rect.bottom);
  if(top>=bounds.top+12&&bottom<=bounds.bottom-12)return;
  const delta=top<bounds.top+12?top-bounds.top-16:Math.min(bottom-bounds.bottom+16,top-bounds.top-16);
  body.scrollTo({top:body.scrollTop+delta,behavior:'instant'});
}
export function AdminActionLabel({postId,commentId,children}:{postId:number;commentId?:number;children:ReactNode}) {
  const {editing,busy}=useAdminModeration();
  const active=editing?.postId===postId&&editing.commentId===commentId;
  return active?<><span className="sr-only">{children}</span><span aria-hidden="true">{busy?'처리 중…':'사유 입력 중'}</span></>:children;
}
