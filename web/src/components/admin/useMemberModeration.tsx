"use client"
import { useEffect, useId, useLayoutEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationGuard, useNavigationRequest } from "@/components/NavigationGuard"
import OverlayDialog from "@/components/OverlayDialog"
import AdminPagination from "./AdminPagination"
import { AdminApiError } from "@/lib/admin-posts"
import { MEMBER_STATUSES, type AdminMemberDetail } from "@/lib/admin-members"
import { getMemberLogs, moderateMember, memberActionRestriction, memberReason, memberReasonError, MemberActionError, type MemberAction, type MemberLogPage } from "@/lib/admin-member-moderation"
import { writtenTime } from "@/lib/written-time"

type State = { id: string | null; action: MemberAction | null; reason: string; error: MemberActionError | null; notice: string; completionNotice: string; uncertain: boolean; busy: boolean; checking: boolean; needsCheck: boolean; completed: boolean; restricted: boolean; page: number; retry: number }
const initial = (id: string | null): State => ({ id, action: null, reason: "", error: null, notice: "", completionNotice: "", uncertain: false, busy: false, checking: false, needsCheck: false, completed: false, restricted: false, page: 0, retry: 0 })
export default function useMemberModeration({selected, member, refresh, deny}: {
  selected: string | null; member?: AdminMemberDetail; refresh: () => Promise<void>; deny: () => void;
}) {
  const auth = useAuth(), guard = useNavigationGuard(), scope = useNavigationRequest(), logScope = useNavigationRequest()
  const actorId = auth.member!.memberId, capture = auth.captureMemberRequest, uid = useId()
  // A new opening has its own identity, even for close → A or A → B → A.
  const [opening, setOpening] = useState({id: selected})
  if (opening.id !== selected) setOpening({id: selected})
  const [state, setState] = useState(() => initial(selected))
  if (state.id !== selected) setState(initial(selected))
  const s = state.id === selected ? state : initial(selected)
  const [logs, setLogs] = useState<{opening: object; id: string; page: number; retry: number; data?: MemberLogPage; error?: string} | null>(null)
  const [exit, setExit] = useState<(() => void) | null>(null)
  const field = useRef<HTMLTextAreaElement>(null), trigger = useRef<HTMLButtonElement>(null), feedback = useRef<HTMLDivElement>(null)
  const flight = useRef(false), checking = useRef<object | null>(null), identity = useRef(opening), refreshRef = useRef(refresh), denyRef = useRef(deny)
  const manualHistory = useRef<{opening: object; retry: number} | null>(null)
  const [validation, setValidation] = useState(0)
  const returnFocus = useRef(false), blockedWhileBusy = useRef(false)
  useLayoutEffect(() => { identity.current = opening; refreshRef.current = refresh; denyRef.current = deny }, [opening, refresh, deny])
  useLayoutEffect(() => { checking.current = null; manualHistory.current = null }, [opening])
  const dirty = !!s.action && (!!s.reason || s.busy)
  const setDirty = guard.setDirty
  useLayoutEffect(() => { setDirty(dirty); return () => setDirty(false) }, [dirty, setDirty])
  useLayoutEffect(() => {
    if (!validation || !field.current) return
    field.current.focus({preventScroll: true})
    // Only reveal within the existing detail scroller; never scroll the list.
    const container = field.current.closest<HTMLElement>(".admin-member-detail-body")
    const error = document.getElementById(`${uid}-error`)
    if (container && error) {
      const top = field.current.getBoundingClientRect().top, bottom = error.getBoundingClientRect().bottom, bounds = container.getBoundingClientRect()
      if (getComputedStyle(container).overflowY === "auto") {
        if (top < bounds.top || bottom > bounds.bottom) container.scrollTop += top - bounds.top - 12
      } else if (top < 0 || bottom > innerHeight) {
        field.current.scrollIntoView({block: "center", behavior: "instant"})
      }
    }
  }, [validation, uid])
  useLayoutEffect(() => {
    if (!s.action && returnFocus.current && member) {
      const destination = trigger.current && !trigger.current.disabled ? trigger.current : feedback.current
      if (destination) { destination.focus({preventScroll: true}); returnFocus.current = false }
    }
  }, [s.action, member])
  useEffect(() => {
    if (s.busy && (exit || guard.pending)) blockedWhileBusy.current = true
    if (!s.busy && blockedWhileBusy.current) { blockedWhileBusy.current = false; setExit(null); guard.cancel() }
  }, [s.busy, exit, guard])
  const logState = logs?.opening === opening && logs.id === selected && logs.page === s.page && logs.retry === s.retry ? logs : null
  useEffect(() => {
    if (!selected) return
    const manual = manualHistory.current
    manualHistory.current = null
    if (manual?.opening === opening && s.page === 0 && manual.retry === s.retry) return
    const controller = new AbortController(), navigation = logScope.begin(), session = capture(actorId)
    let live = true
    const valid = () => live && identity.current === opening && navigation() && session.isCurrent()
    void getMemberLogs(selected, s.page, controller.signal).then(data => {
      if (valid()) setLogs({opening, id: selected, page: s.page, retry: s.retry, data})
    }).catch(error => {
      if (!valid()) return
      if (error instanceof AdminApiError && error.status === 401) { session.expire(); return }
      if (error instanceof AdminApiError && error.status === 403) { denyRef.current(); return }
      setLogs({opening, id: selected, page: s.page, retry: s.retry, error: error instanceof Error ? error.message : "조치 이력을 가져오지 못했습니다."})
    })
    return () => { live = false; controller.abort() }
  }, [selected, opening, s.page, s.retry, capture, actorId, logScope])
  function requestExit(action: () => void) {
    if (flight.current || dirty) setExit(() => action)
    else { setState(initial(selected)); action() }
  }
  function cancel() {
    requestExit(() => { setState(initial(selected)); returnFocus.current = true })
  }
  async function checkLatest(completed = s.completed) {
    if (!selected || checking.current) return
    const ticket = {}
    checking.current = ticket
    const id = selected, navigation = scope.begin(), session = capture(actorId)
    const valid = () => navigation() && session.isCurrent() && identity.current === opening
    setState(v => ({...v, checking: true, needsCheck: true}))
    // Invalidate a previous history request before issuing the recovery read.
    logScope.begin()
    setLogs(null)
    const results = await Promise.allSettled([refreshRef.current(), getMemberLogs(id, 0, AbortSignal.timeout(15000))])
    const accepted = valid()
    if (checking.current === ticket) checking.current = null
    if (!accepted) return
    const historyResult = results[1]
    const failures = results.filter(r => r.status === "rejected")
    for (const failure of failures) {
      const e = failure.reason
      if (e instanceof AdminApiError && e.status === 401) { session.expire(); return }
      if (e instanceof AdminApiError && e.status === 403) { denyRef.current(); return }
    }
    if (s.page !== 0) manualHistory.current = {opening, retry: s.retry}
    setState(v => ({...v, page: 0, checking: false, needsCheck: failures.length > 0,
      notice: failures.length
        ? completed ? "조치는 완료됐습니다. 최신 정보를 불러오지 못했습니다. 다시 조회해 주세요." : "최신 정보를 불러오지 못했습니다. 다시 조회해 주세요."
        : completed ? v.completionNotice : v.uncertain ? "최신 상태와 이력을 확인했습니다. 현재 표시된 내용을 확인해 주세요." : "최신 정보를 확인했습니다.",
      error: failures.length || (!completed && !v.uncertain) ? v.error : null,
    }))
    if (historyResult.status === "fulfilled") setLogs({opening, id, page: 0, retry: s.retry, data: historyResult.value})
    else setLogs({opening, id, page: 0, retry: s.retry, error: "조치 이력을 가져오지 못했습니다."})
  }
  async function submit() {
    if (!selected || !member || !s.action || flight.current || s.needsCheck || s.restricted || memberActionRestriction(member, actorId)) return
    const validationError = memberReasonError(s.reason)
    if (validationError) { setState(v => ({...v, error: new MemberActionError(400, "INVALID_REASON", validationError)})); setValidation(v => v + 1); return }
    flight.current = true
    const id = selected, action = s.action, navigation = scope.begin(), session = capture(actorId)
    const valid = () => navigation() && session.isCurrent() && identity.current === opening
    setState(v => ({...v, busy: true, completed: false, completionNotice: "", uncertain: false, error: null, notice: ""}))
    let reload = false, completed = false
    try {
      const result = await moderateMember(id, action, s.reason, valid)
      if (!valid() || !result) return
      completed = true; reload = true; returnFocus.current = true
      const message = result.changed
        ? action === "suspend" ? "이용 정지를 완료했습니다." : "이용 정지 해제를 완료했습니다."
        : action === "suspend" ? "이미 이용 정지된 회원입니다." : "이미 이용 정지가 해제된 회원입니다."
      setState(v => ({...v, action: null, reason: "", completed: true, uncertain: false, needsCheck: true, notice: message, completionNotice: message}))
    } catch (error) {
      if (!valid()) return
      const e = error instanceof MemberActionError ? error : new MemberActionError(0, "UNCERTAIN", "처리 결과를 확인하지 못했습니다. 최신 상태와 이력을 확인해 주세요.", true)
      if (e.status === 401) { session.expire(); return }
      if (e.status === 403 && ["ACCESS_DENIED", "ADMIN_REQUIRED"].includes(e.code)) { denyRef.current(); return }
      setState(v => ({...v, error: e, uncertain: e.uncertain, needsCheck: e.uncertain || e.status === 404 || e.status === 409,
        restricted: ["SELF_MODERATION_NOT_ALLOWED", "ADMIN_TARGET_NOT_ALLOWED"].includes(e.code)}))
      if (e.code === "INVALID_REASON") setValidation(v => v + 1)
      if (e.status === 404 || e.status === 409) reload = true
    } finally {
      flight.current = false
      if (valid()) setState(v => ({...v, busy: false}))
    }
    if (reload && valid()) await checkLatest(completed)
  }
  const operation = s.action === "lift-suspension" ? "이용 정지 해제" : "이용 정지"
  const restriction = member ? memberActionRestriction(member, actorId) : ""
  const errorField = s.error?.code === "INVALID_REASON"
  const controls = <section className="admin-member-operation" aria-label="회원 운영">
    <h3>회원 운영</h3>
    <div ref={feedback} tabIndex={-1}>{s.notice && <p role="status">{s.notice}</p>}</div>
    {s.needsCheck && <div className="admin-member-recovery"><p>최신 상태 확인 전에는 추가 조치를 할 수 없습니다.</p><button disabled={s.checking || s.busy} onClick={() => void checkLatest()}>{s.checking ? "확인 중…" : "최신 상태·이력 다시 조회"}</button></div>}
    {member && !s.action && (restriction ? <p>{restriction}</p> : <button ref={trigger} disabled={s.needsCheck || s.checking} onClick={() => setState(v => ({...v, action: member.status === "ACTIVE" ? "suspend" : "lift-suspension", error: null, notice: "", restricted: false, completed: false}))}>{member.status === "ACTIVE" ? "이용 정지" : "이용 정지 해제"}</button>)}
    {s.action && <form className="admin-member-action-form" data-action={s.action} noValidate onSubmit={e => {e.preventDefault(); void submit()}}>
      <h4>{operation}</h4><p>{member?.nickname ?? "선택한 회원"} · 회원 #{selected}</p>
      <p>{member ? MEMBER_STATUSES[member.status] : "상태 확인 필요"} → {s.action === "suspend" ? "이용 정지" : "정상"}</p>
      <p>{s.action === "suspend" ? "이 회원의 로그인과 인증이 필요한 서비스 이용을 제한합니다." : "이 회원의 이용 정지를 해제합니다. 서비스 이용을 위해 다시 로그인해야 합니다."}</p>
      {s.action === "suspend" && <p>기존 게시글·댓글의 공개 상태는 변경되지 않습니다.</p>}
      {s.error && !errorField && <p className="admin-member-action-error" role="alert">{s.error.message}</p>}
      <label htmlFor={`${uid}-reason`}>처리 사유 (필수)</label>
      <textarea id={`${uid}-reason`} ref={field} rows={4} value={s.reason} disabled={s.busy || s.checking} aria-invalid={errorField} aria-describedby={`${uid}-count${errorField ? ` ${uid}-error` : ""}`} onChange={e => {setState(v => ({...v, reason: e.target.value, error: v.error?.code === "INVALID_REASON" ? null : v.error})); setDirty(!!e.target.value)}}/>
      <p id={`${uid}-count`} className="admin-member-reason-count">{[...memberReason(s.reason)].length} / 500자</p>
      {errorField && <p id={`${uid}-error`} className="admin-member-action-error" role="alert">⚠ {s.error!.message}</p>}
      <p className="admin-member-action-help">처리 사유는 조치 이력에 저장됩니다.</p>
      <div className="admin-member-action-buttons"><button type="button" disabled={s.busy || s.checking} onClick={cancel}>취소</button><button type="submit" className={`admin-member-primary${s.action === "suspend" ? " admin-member-suspend" : ""}`} disabled={s.busy || s.checking || s.needsCheck || s.restricted || !!restriction || !member}>{s.busy ? "처리 중…" : `${operation} 실행`}</button></div>
    </form>}
  </section>
  const history = selected && <section className="admin-member-history" aria-label="회원 조치 이력" aria-busy={!logState}>
    <header className="admin-member-history-heading"><h3>회원 조치 이력</h3><div>{logState?.data && <span>총 {logState.data.totalElements}건</span>}<span>최신순 · 한국 시간(KST)</span></div></header>
    {!logState ? <p role="status">조치 이력을 불러오고 있습니다…</p> : logState.error ? <div role="alert"><p>{logState.error}</p><button disabled={s.busy || s.checking} onClick={() => {if (!flight.current && !checking.current) setState(v => ({...v, retry: v.retry + 1}))}}>이력 다시 시도</button></div> : logState.data && <>
      {logState.data.items.length === 0 ? <p>회원 조치 이력이 없습니다</p> : <ol>{logState.data.items.map(log => <li key={log.logId}>
        <div className="admin-member-history-meta"><strong className="admin-member-action-badge" data-action={log.action}>{log.action === "SUSPEND" ? "이용 정지" : "이용 정지 해제"}</strong><time dateTime={log.createdAt}>{writtenTime(log.createdAt).exact.replace(" (한국 시간)", "")}</time></div>
        <p>{MEMBER_STATUSES[log.beforeStatus]} → {MEMBER_STATUSES[log.afterStatus]}</p><p className="admin-member-history-reason">사유: {log.reason}</p><p>처리 관리자: 회원 #{log.actorMemberId}</p>
      </li>)}</ol>}
      {logState.data.totalPages > 1 && <AdminPagination label="회원 조치 이력 페이지" data={logState.data} busy={s.busy || s.checking} failed={false} onPage={page => {if (!flight.current && !checking.current) setState(v => ({...v, page}))}}/>}
    </>}
  </section>
  const dismiss = () => { setExit(null); guard.cancel() }
  const confirmation = (exit || guard.pending) && <OverlayDialog titleId={`${uid}-exit-title`} descriptionId={`${uid}-exit-text`} className="admin-member-exit-dialog" onClose={dismiss} fallbackFocus="#admin-member-title">
    <h2 id={`${uid}-exit-title`}>{s.busy ? "처리 중입니다" : "입력한 사유를 버리고 이동할까요?"}</h2>
    <p id={`${uid}-exit-text`}>{s.busy ? "요청이 완료된 뒤 이동할 수 있습니다." : "이동하면 작성한 처리 사유는 사라집니다."}</p>
    <div><button data-initial-focus onClick={dismiss}>{s.busy ? "현재 화면에 머무르기" : "계속 작성"}</button>{!s.busy && <button onClick={() => { const next = exit; setExit(null); setState(initial(selected)); setDirty(false); if (next) next(); else guard.proceed() }}>입력 버리고 이동</button>}</div>
  </OverlayDialog>
  return { controls, history, confirmation, requestExit, busy: s.busy, needsCheck: s.needsCheck, completed: s.completed }
}
