"use client"
import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import { adminListHref } from "@/lib/admin-navigation"
import { useAdminContentFilters } from "@/lib/useAdminContentFilters"
import AdminStatusFilter from "@/components/admin/AdminStatusFilter"
import AdminPagination from "@/components/admin/AdminPagination"
import WrittenTime from "@/components/WrittenTime"
import { POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import {
  ADMIN_STATUSES,
  AdminApiError,
  getAdminPosts,
  type AdminQuery,
  type AdminPage,
  ADMIN_CONTENT_STATUSES,
} from "@/lib/admin-posts"
export default function AdminPosts({ initialQuery = {statuses: ADMIN_CONTENT_STATUSES, keyword: "", page: 0} }: {initialQuery?: AdminQuery}) {
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
    data?: AdminPage
    error?: string
    forbidden?: boolean
  } | null>(null)
  useEffect(() => { if (!empty) window.history.replaceState(window.history.state, "", adminListHref(query)) }, [query, empty])
  const memberId = auth.member!.memberId,
    capture = auth.captureMemberRequest
  useEffect(() => {
    if (pending || empty || !scope.isActive()) return
    const controller = new AbortController(),
      navigation = scope.begin(),
      session = capture(memberId),
      ownsRead = beginRead(controller)
    const current = () =>
      ownsRead() && navigation() && session.isCurrent()
    void getAdminPosts(query, controller.signal)
      .then((data) => {
        if (current()) {
          if (!adjusted.current && query.page > 0 && query.page >= data.totalPages) {
            adjusted.current = true; setQuery({...query, page: Math.max(0,data.totalPages-1)}); return
          }
          setLastSuccess({data, query})
          setResult({ query, attempt: retry, data })
        }
      })
      .catch((error) => {
        if (!current()) return
        if (error instanceof AdminApiError && error.status === 401) {
          setLastSuccess(null)
          session.expire()
          return
        }
        if (error instanceof AdminApiError && (error.status === 403 || error.status === 404)) setLastSuccess(null)
        setResult(previous => ({
          data: error instanceof AdminApiError && error.status === 403 ? undefined : previous?.data,
          query,
          attempt: retry,
          error:
            error instanceof Error ? error.message : "조회에 실패했습니다.",
          forbidden: error instanceof AdminApiError && error.status === 403,
        }))
      })
    return () => controller.abort()
  }, [query, retry, memberId, capture, scope, pending, empty, beginRead, setQuery])
  const active = !pending && !empty && result?.query === query && result.attempt === retry ? result : null,
    busy = !empty && !active
  const shown = empty ? null : lastSuccess, data = shown?.data, displayQuery = shown?.query ?? query
  const stale = busy || !!active?.error
  const change = (next: AdminQuery) => {
    adjusted.current = false
    apply(next)
  }
  return (
    <>
      <header className="admin-heading">
        <p>관리자</p>
        <h1>게시글 관리</h1>
        <p>게시글의 제목과 공개 상태를 확인합니다.</p>
      </header>
      <section className="admin-panel admin-posts-panel" aria-label="게시글 목록 관리">
        <form className="admin-filters admin-post-filters" onSubmit={event => {event.preventDefault(); adjusted.current = false; search()}}>
          <div className="admin-filter-search-row">
            <label className="admin-search">게시글 제목 검색
              <input value={input} {...composition} onChange={e=>{setInput(e.target.value);setValidation("")}}
                aria-invalid={!!validation} aria-describedby={validation ? "admin-search-error" : undefined} placeholder="게시글 제목 검색"/>
            </label>
            <button type="submit">검색</button>
          </div>
          <div className="admin-filter-state-row">
            <AdminStatusFilter label="게시글 상태" value={statuses} onChange={next=>{adjusted.current=false;if (!next.length) {setLastSuccess(null);setResult(null)};selectStatuses(next)}}/>
            <button type="button" className="admin-filter-reset" onClick={()=>{adjusted.current=false;reset()}}>초기화</button>
          </div>
        </form>
        {validation && (
          <p id="admin-search-error" role="alert">
            {validation}
          </p>
        )}
        <div className="admin-results" aria-busy={busy}>
          {empty ? <p className="admin-empty-selection">표시할 상태를 선택해 주세요.</p> : <>
            {data && <p className="admin-refresh-notice" role="status" style={{visibility:busy?"visible":"hidden"}}>목록을 갱신하고 있습니다…</p>}
            {busy && !data && <p role="status">게시글을 불러오고 있습니다…</p>}
            {active?.error && <div role="alert">
              {data && <p>갱신에 실패했습니다. 이전 결과를 표시하고 있습니다.</p>}
              <p>{active.error}</p>
              {!active.forbidden && <button onClick={()=>setRetry(n=>n+1)}>다시 시도</button>}
            </div>}
            {data && <>
                <p className="admin-total">
                  {stale ? "이전 결과" : "검색 결과"}{" "}
                  <strong>
                    {data.totalElements.toLocaleString("ko-KR")}
                  </strong>
                  개
                </p>
                {!data.items.length ? (
                  <p>
                    {displayQuery.keyword || displayQuery.statuses.length !== 3
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
                          <tr key={post.postId}>
                            <td>{post.postId}</td>
                            <td className="admin-post-title"><Link href={`/admin/posts/${post.postId}?from=${encodeURIComponent(adminListHref(displayQuery))}`}>{post.title}</Link></td>
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
                              <WrittenTime value={post.createdAt} anchored />
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
      </section>
    </>
  )
}
