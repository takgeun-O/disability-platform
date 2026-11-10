"use client"
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react"
import { useAdminUrlQuery } from "@/lib/useAdminUrlQuery"
import { useSearchParams } from "next/navigation"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import AdminPagination from "@/components/admin/AdminPagination"
import { AdminApiError } from "@/lib/admin-posts"
import { writtenTime } from "@/lib/written-time"
import { defaultMemberQuery, getMember, getMembers, memberHref, memberKeyword, memberParams, memberQuery, memberSelection, MEMBER_ROLES, MEMBER_SEARCHES, MEMBER_STATUSES, type AdminMemberDetail, type MemberPage, type MemberQuery } from "@/lib/admin-members"

export default function AdminMembers() {
  const { navigation } = useAdminUrlQuery()
  return <MemberWorkspace key={navigation} />
}
function MemberWorkspace() {
  const params = useSearchParams()
  const queryKey = memberParams(memberQuery(new URLSearchParams(params.toString()))).toString()
  const query = useMemo(() => memberQuery(new URLSearchParams(queryKey)), [queryKey])
  const selected = memberSelection(params.get("selected"))
  const auth = useAuth(), scope = useNavigationRequest(), detailScope = useNavigationRequest(), copyScope = useNavigationRequest()
  const memberId = auth.member!.memberId, capture = auth.captureMemberRequest
  const [list, setList] = useState<{key: string; attempt: number; data?: MemberPage; error?: string} | null>(null)
  const [detail, setDetail] = useState<{id: string; attempt: number; data?: AdminMemberDetail; error?: string} | null>(null)
  // Clearing on every selection transition includes close → reopen and A → B → A.
  // An old completed result must never stand in for the new request's loading state.
  const [detailSelection, setDetailSelection] = useState(selected)
  if (detailSelection !== selected) { setDetailSelection(selected); setDetail(null) }
  const [retry, setRetry] = useState(0), [detailRetry, setDetailRetry] = useState(0)
  const [denied, setDenied] = useState(false), blocked = useRef(false)
  const [compact, setCompact] = useState(false)
  const workspace = useRef<HTMLDivElement>(null), heading = useRef<HTMLHeadingElement>(null), detailHeading = useRef<HTMLHeadingElement>(null)
  const panel = useRef<HTMLElement>(null), detailBody = useRef<HTMLDivElement>(null)
  const origin = useRef<{id: string; y: number} | null>(null), previous = useRef(selected)
  const [copy, setCopy] = useState<{id: string; text: string} | null>(null)
  const active = list?.key === queryKey && list.attempt === retry ? list : null
  const current = detailSelection === selected && detail?.id === selected && detail.attempt === detailRetry ? detail : null
  const busy = !active, data = active?.data
  useLayoutEffect(() => {
    const node = workspace.current
    if (!node) return
    const observer = new ResizeObserver(([entry]) => setCompact(entry.contentRect.width < 1050))
    observer.observe(node); return () => observer.disconnect()
  }, [])
  useLayoutEffect(() => {
    if (detailBody.current) detailBody.current.scrollTop = 0
  }, [selected])
  useLayoutEffect(() => {
    const element = panel.current
    if (!element || compact) return
    let frame = 0
    const measure = () => {
      frame = 0
      // The shared header scrolls away. Reserve its visible bottom, not a fixed
      // header height; include the panel's actual top and a 16px bottom gutter.
      const headerBottom = document.querySelector(".iyum-header")?.getBoundingClientRect().bottom ?? 0
      const top = Math.max(16, headerBottom + 16)
      element.style.setProperty("--member-panel-top", `${top}px`)
      const available = innerHeight - Math.max(top, element.getBoundingClientRect().top) - 16
      element.style.setProperty("--member-panel-height", `${Math.max(0, available)}px`)
    }
    const schedule = () => { if (!frame) frame = requestAnimationFrame(measure) }
    const observer = new ResizeObserver(schedule)
    const header = document.querySelector(".iyum-header")
    if (header) observer.observe(header)
    if (workspace.current) observer.observe(workspace.current)
    window.addEventListener("resize", schedule)
    window.addEventListener("scroll", schedule, {passive: true})
    measure()
    return () => {
      observer.disconnect(); cancelAnimationFrame(frame)
      window.removeEventListener("resize", schedule); window.removeEventListener("scroll", schedule)
      element.style.removeProperty("--member-panel-top"); element.style.removeProperty("--member-panel-height")
    }
  }, [selected, compact])
  useLayoutEffect(() => {
    if (selected && selected !== previous.current) {
      detailHeading.current?.focus({preventScroll: true})
      if (compact) detailHeading.current?.scrollIntoView({block: "start", behavior: "instant"})
    }
    if (!selected && previous.current) {
      const id = previous.current
      const button = document.querySelector<HTMLButtonElement>(`[data-member-select="${id}"]`)
      ;(button ?? heading.current)?.focus({preventScroll: true})
      if (origin.current?.id === id) window.scrollTo({top: origin.current.y, behavior: "instant"})
    }
    previous.current = selected
  }, [selected, compact])
  useEffect(() => {
    if (denied) return
    const controller = new AbortController(), navigation = scope.begin(), session = capture(memberId)
    let live = true
    const valid = () => live && !blocked.current && navigation() && session.isCurrent()
    void getMembers(query, controller.signal).then(data => { if (valid()) setList({key: queryKey, attempt: retry, data}) }).catch(error => {
      if (!valid()) return
      if (error instanceof AdminApiError && [401,403].includes(error.status)) {
        blocked.current = true; setList(null); setDetail(null)
        if (error.status === 401) session.expire(); else setDenied(true)
        return
      }
      setList({key: queryKey, attempt: retry, error: error instanceof Error ? error.message : "회원 목록을 가져오지 못했습니다."})
    })
    return () => { live = false; controller.abort() }
  }, [query, queryKey, retry, capture, memberId, scope, denied])
  useEffect(() => {
    if (!selected || denied) return
    const controller = new AbortController(), navigation = detailScope.begin(), session = capture(memberId)
    let live = true
    const valid = () => live && !blocked.current && navigation() && session.isCurrent()
    void getMember(selected, controller.signal).then(data => { if (valid()) setDetail({id: selected, attempt: detailRetry, data}) }).catch(error => {
      if (!valid()) return
      if (error instanceof AdminApiError && [401,403].includes(error.status)) {
        blocked.current = true; setList(null); setDetail(null)
        if (error.status === 401) session.expire(); else setDenied(true)
        return
      }
      setDetail({id: selected, attempt: detailRetry, error: error instanceof AdminApiError && error.status === 404 ? "회원을 찾을 수 없습니다." : error instanceof Error ? error.message : "회원 정보를 가져오지 못했습니다."})
    })
    return () => { live = false; controller.abort() }
  }, [selected, detailRetry, capture, memberId, detailScope, denied])
  function apply(q: MemberQuery) {
    if (blocked.current || !scope.isActive()) return
    const nextKey = memberParams(q).toString()
    scope.begin() // Immediately revoke the previous request, including before effect cleanup.
    if (nextKey !== queryKey) {
      // The changed query itself triggers exactly one read; do not also bump retry.
      history.pushState(null, "", memberHref(q))
    } else {
      // Explicit same-condition search/reset is a read, not another history entry.
      if (selected) history.replaceState(null, "", memberHref(q))
      setRetry(n => n + 1)
    }
  }
  function select(id: string) {
    if (busy || blocked.current) return
    origin.current = {id, y: scrollY}
    history.pushState({adminMemberOrigin: {id, y: scrollY}}, "", memberHref(query, id))
    setCopy(null)
  }
  function close() { history.replaceState({adminMemberOrigin: origin.current ?? history.state?.adminMemberOrigin}, "", memberHref(query)) }
  if (denied) return <div className="admin-access-card" role="alert"><h1>관리자 권한이 없습니다</h1><p>회원 정보를 조회할 수 없습니다.</p></div>
  const m = current?.data
  return <section className="admin-members" ref={workspace} data-compact={compact}>
    <header className="admin-members-heading"><h1 ref={heading} tabIndex={-1}>회원 관리</h1><p>회원의 기본 정보와 계정 상태를 확인합니다.</p></header>
    <div hidden={compact && !!selected}>
      <MemberFilters key={queryKey} query={query} onApply={apply} />
    </div>
    <p className="admin-member-applied" hidden={compact && !!selected}>적용 조건: {query.keyword ? `${MEMBER_SEARCHES[query.searchType]} ‘${query.keyword}’` : "전체 검색"} · {query.status ? MEMBER_STATUSES[query.status as keyof typeof MEMBER_STATUSES] : "전체 상태"} · {query.role ? MEMBER_ROLES[query.role as keyof typeof MEMBER_ROLES] : "전체 권한"} · {query.sort === "OLDEST" ? "오래된순" : "최신순"}</p>
    <div className="admin-members-layout" data-selected={!!selected}>
      <section className="admin-members-list" hidden={compact && !!selected} aria-label="회원 목록" aria-busy={busy}>
        <div className="admin-members-toolbar"><h2>{data ? <>검색 결과 <strong>{data.totalElements.toLocaleString("ko-KR")}명</strong></> : "회원 목록"}</h2><label>가입일 기준<select aria-label="가입일 정렬" value={query.sort} onChange={e => apply({...query, sort: e.target.value as MemberQuery["sort"], page: 0})}><option value="LATEST">최신순</option><option value="OLDEST">오래된순</option></select></label></div>
        {busy ? <p role="status">회원 목록을 불러오고 있습니다…</p> : active.error ? <div role="alert"><p>{active.error}</p><button onClick={() => setRetry(n => n+1)}>목록 다시 시도</button></div> : data && <>
          {data.items.length === 0 ? <p className="admin-members-empty">조회 결과가 없습니다.</p> : <table><thead><tr><th>회원 ID</th><th>회원</th><th>상태</th><th>권한</th><th>가입일</th></tr></thead><tbody>{data.items.map(member => <tr key={member.memberId} data-selected={selected === String(member.memberId)} onClick={e => {if (!(e.target as HTMLElement).closest("button,a")) select(String(member.memberId))}}>
            <td data-label="회원 ID">#{member.memberId}</td><td data-label="회원"><button data-member-select={member.memberId} aria-pressed={selected === String(member.memberId)} aria-controls="admin-member-detail" onClick={() => select(String(member.memberId))}>{member.nickname}</button><span className="admin-member-email">{member.email ?? "이메일 정보 없음"}</span></td><td data-label="상태"><MemberBadge status={member.status}/></td><td data-label="권한">{MEMBER_ROLES[member.role]}</td><td data-label="가입일"><time dateTime={member.createdAt} title={writtenTime(member.createdAt).exact}>{writtenTime(member.createdAt).exact.split(" ")[0]}</time></td>
          </tr>)}</tbody></table>}
          {data.totalPages > 1 && <AdminPagination label="회원 페이지" data={data} busy={busy} failed={!!active.error} onPage={page => apply({...query, page})}/>}
        </>}
      </section>
      {selected && <section ref={panel} id="admin-member-detail" className="admin-member-detail" aria-labelledby="admin-member-title" aria-busy={!current}>
        <header><h2 id="admin-member-title" ref={detailHeading} tabIndex={-1}>회원 상세</h2><button onClick={close}>{compact ? "회원 목록으로 돌아가기" : "닫기"}</button></header>
        <div ref={detailBody} className="admin-member-detail-body" role="region" aria-label="회원 상세 정보" tabIndex={0}>
        {!current ? <p role="status">회원 정보를 불러오고 있습니다…</p> : current.error ? <div role="alert"><p>{current.error}</p><button onClick={() => setDetailRetry(n => n+1)}>상세 다시 시도</button></div> : m && <>
          <div className="admin-member-profile"><span aria-hidden="true"><svg width="34" height="34" viewBox="0 0 32 32" fill="none" stroke="currentColor" strokeWidth="1.7"><circle cx="16" cy="10" r="5"/><path d="M5 28v-3a11 11 0 0 1 22 0v3Z"/></svg></span><div><h3>{m.nickname}</h3><p>회원 #{m.memberId}</p><MemberBadge status={m.status}/> <span className="admin-member-role">{MEMBER_ROLES[m.role]}</span></div></div>
          {m.status === "PENDING" && <p className="admin-member-state-note">이메일 인증이 완료되지 않은 회원입니다.</p>}
          {m.status === "WITHDRAWN" && <p className="admin-member-state-note">서비스 이용이 종료되었으며 계정 정보 정리가 완료되지 않은 상태입니다.</p>}
          {m.status === "DELETED" && <p className="admin-member-state-note">탈퇴가 완료되어 이메일과 닉네임 등 계정 식별 정보가 제거되었습니다.</p>}
          <h3>이메일</h3><div className="admin-member-email-detail"><span>{m.email ?? "이메일 정보 없음"}</span>{m.email !== null && <button className="admin-member-copy" onClick={async () => {
            const session = capture(memberId), navigation = copyScope.begin()
            try { await navigator.clipboard.writeText(m.email!); if (session.isCurrent() && navigation() && !blocked.current) setCopy({id: selected, text: "이메일을 복사했습니다."}) } catch { if (session.isCurrent() && navigation() && !blocked.current) setCopy({id: selected, text: "복사하지 못했습니다. 이메일을 직접 선택해 복사해 주세요."}) }
          }}>이메일 복사</button>}</div>
          {copy?.id === selected && <p role="status">{copy.text}</p>}
          <h3>가입·변경 정보</h3><dl><dt>가입 시각</dt><dd><MemberTime value={m.createdAt}/></dd><dt>최종 변경 시각</dt><dd><MemberTime value={m.updatedAt}/></dd><dt>탈퇴 시각</dt><dd>{m.withdrawnAt ? <MemberTime value={m.withdrawnAt}/> : "해당 없음"}</dd></dl><p className="admin-member-timezone">한국 시간(KST) 기준</p>
        </>}
        </div>
      </section>}
    </div>
  </section>
}
function MemberTime({value}: {value: string}) { return <time dateTime={value}>{writtenTime(value).exact.replace(" (한국 시간)", "")}</time> }
function MemberBadge({status}: {status: keyof typeof MEMBER_STATUSES}) { return <span className="admin-member-badge" data-status={status}>{MEMBER_STATUSES[status]}</span> }
function MemberFilters({query, onApply}: {query: MemberQuery; onApply: (q: MemberQuery) => void}) {
  const [type, setType] = useState(query.searchType), [input, setInput] = useState(query.keyword), [error, setError] = useState("")
  const field = useRef<HTMLInputElement>(null)
  return <form className="admin-member-filters" noValidate onSubmit={e => {e.preventDefault(); try { const keyword = memberKeyword(type, input); setError(""); onApply({...query, searchType: type, keyword, page: 0}) } catch (e) {setError((e as Error).message); field.current?.focus()} }}>
    <label>검색 기준<select aria-label="검색 기준" value={type} onChange={e => {setType(e.target.value as MemberQuery["searchType"]); setError("")}}>{Object.entries(MEMBER_SEARCHES).map(([v,label]) => <option key={v} value={v}>{label}</option>)}</select></label>
    <label className="admin-member-search">검색어<input ref={field} value={input} onChange={e => {setInput(e.target.value); setError("")}} onKeyDown={e => {if(e.key === "Enter" && (e.nativeEvent.isComposing || e.keyCode === 229)) e.preventDefault()}} aria-invalid={!!error} aria-describedby={`member-search-help${error ? " member-search-error" : ""}`} placeholder={type === "EMAIL" ? "이메일 전체 주소를 입력하세요" : type === "MEMBER_ID" ? "회원 ID를 입력하세요" : "닉네임을 입력하세요"} inputMode={type === "MEMBER_ID" ? "numeric" : "text"}/></label>
    <button type="submit" className="admin-member-primary">검색</button>
    <label>회원 상태<select aria-label="회원 상태" value={query.status} onChange={e => onApply({...query, status: e.target.value, page: 0})}><option value="">전체</option>{Object.entries(MEMBER_STATUSES).map(([v,label]) => <option key={v} value={v}>{label}</option>)}</select></label>
    <label>권한<select aria-label="권한" value={query.role} onChange={e => onApply({...query, role: e.target.value, page: 0})}><option value="">전체</option>{Object.entries(MEMBER_ROLES).map(([v,label]) => <option key={v} value={v}>{label}</option>)}</select></label>
    <button type="button" className="admin-filter-clear" onClick={() => {setInput(""); setType("NICKNAME"); setError(""); onApply({...defaultMemberQuery})}}>전체 초기화</button>
    <p id="member-search-help">{type === "NICKNAME" ? <>입력한 문자가 포함된 닉네임을 검색합니다.<span className="admin-member-display-name-help">‘탈퇴한 회원’은 표시용 이름입니다. 탈퇴 회원은 검색어를 비우고 회원 상태로 조회하세요.</span></> : type === "EMAIL" ? "이메일 전체 주소가 일치하는 회원을 검색합니다." : "회원 ID가 일치하는 회원을 검색합니다."}</p>{error && <p id="member-search-error" role="alert">{error}</p>}
  </form>
}
