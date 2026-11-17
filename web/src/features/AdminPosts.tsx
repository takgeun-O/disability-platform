"use client"
import type { ModerationTarget } from "@/lib/admin-moderation"
import { isAdminRowSelection, isAdminRowKeyboardSelection } from "@/lib/admin-row-selection"
import { useCallback, useEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import { adminListHref, adminListQuery, adminSelectionId } from "@/lib/admin-navigation"
import { useAdminContentFilters } from "@/lib/useAdminContentFilters"
import { useAdminUrlQuery } from "@/lib/useAdminUrlQuery"
import AdminModeration, { useAdminModeration } from "@/components/admin/AdminModeration"
import AdminContentDialog, { openAdminDialog, syncAdminListUrl, useAdminDialogReturn } from "@/components/admin/AdminContentDialog"
import AdminDateRange from "@/components/admin/AdminDateRange"
import { ADMIN_SORTS } from "@/lib/admin-dates"
import AdminStatusFilter from "@/components/admin/AdminStatusFilter"
import AdminPagination from "@/components/admin/AdminPagination"
import { writtenTime } from "@/lib/written-time"
import { POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import {
  ADMIN_STATUSES,
  AdminApiError,
  getAdminPosts,
  type AdminQuery,
  type AdminPage,
  ADMIN_CONTENT_STATUSES,
} from "@/lib/admin-posts"
import AdminContentSummary, { useContentSummary } from "@/components/admin/AdminContentSummary"
export default function AdminPosts() {
  const auth = useAuth()
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const [denied, setDenied] = useState(false)
  const deny = useCallback(() => setDenied(true), [])
  const protect = useCallback((failure: unknown) => {
    if (!(failure instanceof AdminApiError)) return false
    if (failure.status === 401) { capture(actor).expire(); return true }
    if (failure.status === 403) { deny(); return true }
    return false
  }, [actor, capture, deny])
  const summary = useContentSummary(protect)
  const {values,navigation}=useAdminUrlQuery()
  useAdminDialogReturn(navigation)
  const [recovery,setRecovery]=useState<ModerationTarget|null>(null)
  if (denied) return <div role="alert"><h1>관리자 권한이 없습니다</h1><p>이전 콘텐츠 정보는 제거되었습니다.</p></div>
  return <>
    <header className="admin-heading">
      <h1 tabIndex={-1} data-admin-list-heading>게시글 관리</h1>
      <p>게시글의 제목과 공개 상태를 확인합니다.</p>
    </header>
    <AdminContentSummary {...summary} />
    <AdminModeration onSuccess={summary.refresh} onDenied={deny} inline recovery={recovery} onRecovery={setRecovery} key={navigation}><AdminPostResults initialQuery={adminListQuery(values)} initialSelected={adminSelectionId(values.selected)} /></AdminModeration></>
}
function AdminPostResults({ initialSelected = null, initialQuery = {statuses: ADMIN_CONTENT_STATUSES, keyword: "", page: 0,startDate:"",endDate:"",sort:"LATEST"} }: {initialQuery?: AdminQuery;initialSelected?:number|null}) {
  const {revision,readCurrent,readLocked:locked,deny,recoveryFeedback}=useAdminModeration()
  const [dateReset,setDateReset]=useState(0)
  const adjusted = useRef(false)
  const auth = useAuth(),
    scope = useNavigationRequest()
  const {query, setQuery, statuses, input, setInput, validation, setValidation, pending,
    empty, beginRead, change: apply, selectStatuses, search, reset, composition} = useAdminContentFilters(initialQuery)
  const [lastSuccess, setLastSuccess] = useState<{data: AdminPage; query: AdminQuery} | null>(null)
  const [retry, setRetry] = useState(0)
  const [result, setResult] = useState<{
    query: AdminQuery
    attempt: number
    revision: number
    data?: AdminPage
    error?: string
    forbidden?: boolean
  } | null>(null)
  const selectedId=initialSelected
  const select=(id:number)=>{openAdminDialog(adminListHref(query,id))}
  useEffect(() => { if (scope.isActive()) syncAdminListUrl(adminListHref(query,selectedId)) }, [query, empty, scope,selectedId])
  const memberId = auth.member!.memberId,
    capture = auth.captureMemberRequest
  useEffect(() => {
    if (pending || empty || !scope.isActive()) return
    const controller = new AbortController(),
      navigation = scope.begin(),
      session = capture(memberId),
      ownsRead = beginRead(controller)
    const current = () =>
      ownsRead() && navigation() && session.isCurrent() && readCurrent(revision)
    void getAdminPosts(query, controller.signal)
      .then((data) => {
        if (current()) {
          if (!adjusted.current && query.page > 0 && query.page >= data.totalPages) {
            adjusted.current = true; setQuery({...query, page: Math.max(0,data.totalPages-1)}); return
          }
          setLastSuccess({data, query})
          setResult({ query, attempt: retry, revision, data })
        }
      })
      .catch((error) => {
        if (!current()) return
        if (error instanceof AdminApiError && error.status === 401) {
          setLastSuccess(null)
          session.expire()
          return
        }
        if(error instanceof AdminApiError&&error.status===403){deny();return}
        if (error instanceof AdminApiError && (error.status === 403 || error.status === 404)) setLastSuccess(null)
        setResult(previous => ({
          data: error instanceof AdminApiError && error.status === 403 ? undefined : previous?.data,
          query,
          attempt: retry,
          revision,
          error:
            error instanceof Error ? error.message : "조회에 실패했습니다.",
          forbidden: error instanceof AdminApiError && error.status === 403,
        }))
      })
    return () => controller.abort()
  }, [query, retry, memberId, capture, scope, pending, empty, beginRead, setQuery, revision,readCurrent,deny])
  const active = !pending && !empty && result?.query === query && result.attempt === retry && result.revision===revision ? result : null,
    busy = !empty && !active
  const shown = empty ? null : lastSuccess, data = shown?.data, displayQuery = shown?.query ?? query
  const stale = busy || !!active?.error
  const change = (next: AdminQuery) => {
    adjusted.current = false
    apply(next)
  }
  const sortControl = <div className="admin-sort-control"><label htmlFor="admin-post-sort">작성 시각 정렬</label><select id="admin-post-sort" value={query.sort??"LATEST"} onChange={e=>{const sort=e.target.value;if(sort==="LATEST"||sort==="OLDEST"){adjusted.current=false;apply({...query,statuses,sort,page:0},!!active?.error)}}}>{Object.entries(ADMIN_SORTS).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></div>
  return (
    <>
      {!selectedId&&recoveryFeedback}
      <section className="admin-panel admin-workspace admin-posts-panel" aria-label="게시글 목록 관리">
        <div className="admin-compact-toolbar"><form className="admin-filters admin-post-filters" onSubmit={event => {event.preventDefault(); adjusted.current = false; search(!!active?.error)}}>
          <div className="admin-filter-search-row">
            <label className="admin-search">게시글 제목 검색
              <input value={input} {...composition} onChange={e=>{setInput(e.target.value);setValidation("")}}
                aria-invalid={!!validation} aria-describedby={validation ? "admin-search-error" : undefined} placeholder="게시글 제목 검색"/>
            </label>
            <button type="submit">검색</button>
          </div>
          <div className="admin-filter-state-row">
            <AdminStatusFilter label="게시글 상태" value={statuses} onChange={next=>{adjusted.current=false;if (!next.length) {setLastSuccess(null);setResult(null)};selectStatuses(next)}}/>
          </div>
        </form>
        <AdminDateRange key={dateReset} label="작성" applied={{startDate:query.startDate??"",endDate:query.endDate??""}} onApply={range=>{adjusted.current=false;apply({...query,...range,statuses,page:0},!!active?.error)}}/>
        <div className="admin-filter-footer"><button type="button" className="admin-filter-clear" title="모든 검색 조건 초기화" onClick={()=>{adjusted.current=false;setDateReset(n=>n+1);reset(!!active?.error)}}><span aria-hidden="true">↻</span> 전체 초기화</button></div>
        {validation && (
          <p id="admin-search-error" role="alert">
            {validation}
          </p>
        )}
        </div><div className="admin-master-detail"><div className="admin-master-list"><div className="admin-results" aria-busy={busy}>
          {empty ? <p className="admin-empty-selection">표시할 상태를 선택해 주세요.</p> : <>
            <div className="admin-result-toolbar">{data && (<p className="admin-total">
                  {stale ? "이전 결과" : "검색 결과"}{" "}
                  <strong>
                    {data.totalElements.toLocaleString("ko-KR")}
                  </strong>
                  개
                </p>)} {sortControl}</div>
            {data && <p className="admin-refresh-notice" role="status" style={{visibility:busy?"visible":"hidden"}}>목록을 갱신하고 있습니다…</p>}
            {busy && !data && <p role="status">게시글을 불러오고 있습니다…</p>}
            {active?.error && <div role="alert">
              {data && <p>갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.</p>}
              <p>{active.error}</p>
              {!active.forbidden && <button onClick={()=>setRetry(n=>n+1)}>다시 시도</button>}
            </div>}
            {data && <>
                {!data.items.length ? (
                  <p>
                    {displayQuery.keyword || displayQuery.startDate || displayQuery.endDate || displayQuery.statuses.length !== 3
                      ? "검색 결과가 없습니다."
                      : "게시글이 없습니다."}
                  </p>
                ) : (
                  <div className="admin-table-wrap">
                    <table>
                      <caption className="sr-only">관리자 게시글 목록</caption>
                      <thead>
                        <tr>
                          {[
                            "ID",
                            "제목",
                            "작성자",
                            "글 유형·주제",
                            "상태",
                            "작성 시각",
                          ].map((v) => (
                            <th key={v} scope="col">
                              {v}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {data.items.map((post) => (
                          <tr id={`post-select-${post.postId}`} tabIndex={stale||locked?-1:0} aria-label={`게시글 #${post.postId} 미리보기`} aria-selected={selectedId===post.postId} onKeyDown={event=>{if(!stale&&!locked&&isAdminRowKeyboardSelection(event))select(post.postId)}} key={post.postId} data-selected={selectedId===post.postId} data-selectable={!stale&&!locked} onClick={event=>{if(!stale&&!locked&&isAdminRowSelection(event))select(post.postId)}}>
                            <td>{post.postId}</td>
                            <td className="admin-post-title"><Link href={`/admin/posts/${post.postId}?from=${encodeURIComponent(adminListHref(displayQuery,post.postId))}`} onNavigate={event=>{event.preventDefault();if(!stale&&!locked&&!window.getSelection()?.toString())select(post.postId)}}>{post.title}</Link><span className="admin-post-secondary">{post.author.nickname} · {POST_TYPES[post.type]} · {POST_TOPICS[post.topic]}</span></td>
                            <td>{post.author.nickname}</td>
                            <td>
                              {POST_TYPES[post.type]}
                              <br />
                              {POST_TOPICS[post.topic]}
                            </td>
                            <td>
                              <span
                                className={`admin-badge is-${post.status.toLowerCase()}`}
                              >
                                {ADMIN_STATUSES[post.status]}
                              </span>
                            </td>
                            <td>
                              <time dateTime={post.createdAt}>{writtenTime(post.createdAt).exact.replace(" (한국 시간)", "")}</time>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

              </>}
          </>}
        </div>
        {data && data.totalPages > 1 && !active?.forbidden && <AdminPagination data={data} busy={busy} failed={!!active?.error}
          onPage={page => { if (!busy && !active?.error) change({ ...query, page }) }} />}
        </div>
        {selectedId&&<AdminContentDialog key={selectedId} id={selectedId} listHref={adminListHref(query)}/>}

        </div>
      </section>
    </>
  )
}
