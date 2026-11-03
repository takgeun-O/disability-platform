"use client"
import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/components/AuthProvider"
import { useNavigationRequest } from "@/components/NavigationGuard"
import Link from "@/components/AppLink"
import { adminListHref } from "@/lib/admin-navigation"
import AdminPagination from "@/components/admin/AdminPagination"
import WrittenTime from "@/components/WrittenTime"
import { POST_TYPES, POST_TOPICS } from "@/lib/community-posts"
import {
  ADMIN_STATUSES,
  AdminApiError,
  adminKeywordError,
  getAdminPosts,
  normalizeAdminKeyword,
  type AdminQuery,
  type AdminPage,
  type AdminStatus,
} from "@/lib/admin-posts"
export default function AdminPosts({ initialQuery = {status: "ALL", keyword: "", page: 0} }: {initialQuery?: AdminQuery}) {
  const adjusted = useRef(false)
  const auth = useAuth(),
    scope = useNavigationRequest()
  const [query, setQuery] = useState<AdminQuery>(initialQuery)
  const [input, setInput] = useState(initialQuery.keyword),
    [validation, setValidation] = useState(""),
    [retry, setRetry] = useState(0)
  const [result, setResult] = useState<{
    query: AdminQuery
    attempt: number
    data?: AdminPage
    error?: string
    forbidden?: boolean
  } | null>(null)
  useEffect(() => { window.history.replaceState(window.history.state, "", adminListHref(query)) }, [query])
  const memberId = auth.member!.memberId,
    capture = auth.captureMemberRequest
  useEffect(() => {
    const controller = new AbortController(),
      navigation = scope.begin(),
      session = capture(memberId)
    const current = () =>
      !controller.signal.aborted && navigation() && session.isCurrent()
    void getAdminPosts(query, controller.signal)
      .then((data) => {
        if (current()) {
          if (!adjusted.current && query.page > 0 && query.page >= data.totalPages) {
            adjusted.current = true; setQuery({...query, page: Math.max(0,data.totalPages-1)}); return
          }
          setResult({ query, attempt: retry, data })
        }
      })
      .catch((error) => {
        if (!current()) return
        if (error instanceof AdminApiError && error.status === 401) {
          session.expire()
          return
        }
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
  }, [query, retry, memberId, capture, scope])
  const active = result?.query === query && result.attempt === retry ? result : null,
    busy = !active
  const change = (next: AdminQuery) => {
    adjusted.current = false
    setQuery((old) =>
      old.status === next.status &&
      old.keyword === next.keyword &&
      old.page === next.page
        ? old
        : next,
    )
  }
  return (
    <>
      <header className="admin-heading">
        <p>관리자</p>
        <h1>게시글 관리</h1>
        <p>게시글의 제목과 공개 상태를 확인합니다.</p>
      </header>
      <section className="admin-panel" aria-label="게시글 목록 관리">
        <form
          className="admin-filters"
          onSubmit={(event) => {
            event.preventDefault()
            const error = adminKeywordError(input)
            setValidation(error)
            if (!error)
              change({
                ...query,
                keyword: normalizeAdminKeyword(input),
                page: 0,
              })
          }}
        >
          <div className="admin-filter-status">
            <label htmlFor="admin-status">처리 상태</label>
            <select
              id="admin-status"
              value={query.status}
              onChange={(e) =>
                change({
                  ...query,
                  status: e.target.value as AdminStatus,
                  page: 0,
                })
              }
            >
              {Object.entries(ADMIN_STATUSES).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <label className="admin-search">
            게시글 제목 검색
            <input
              value={input}
              onChange={(e) => {
                setInput(e.target.value)
                setValidation("")
              }}
              aria-invalid={!!validation}
              aria-describedby={validation ? "admin-search-error" : undefined}
              placeholder="게시글 제목 검색"
            />
          </label>
          <button type="submit">검색</button>
          <button
            type="button"
            className="admin-secondary"
            onClick={() => {
              setInput("")
              setValidation("")
              change({ status: "ALL", keyword: "", page: 0 })
            }}
          >
            초기화
          </button>
        </form>
        {validation && (
          <p id="admin-search-error" role="alert">
            {validation}
          </p>
        )}
        <div className="admin-results" aria-busy={busy}>
          {busy ? (
            <p role="status">게시글을 불러오고 있습니다…</p>
          ) : active?.error ? (
            <div role="alert">
              <p>{active.error}</p>
              {!active.forbidden && (
                <button
                  onClick={() => {
                    setRetry((n) => n + 1)
                  }}
                >
                  다시 시도
                </button>
              )}
            </div>
          ) : (
            active?.data && (
              <>
                <p className="admin-total">
                  검색 결과{" "}
                  <strong>
                    {active.data.totalElements.toLocaleString("ko-KR")}
                  </strong>
                  개
                </p>
                {!active.data.items.length ? (
                  <p>
                    {query.keyword || query.status !== "ALL"
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
                        {active.data.items.map((post) => (
                          <tr key={post.postId}>
                            <td>{post.postId}</td>
                            <td className="admin-post-title"><Link href={`/admin/posts/${post.postId}?from=${encodeURIComponent(adminListHref(query))}`}>{post.title}</Link></td>
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

              </>
            )
          )}
        </div>
        {result?.data && !active?.forbidden && <AdminPagination data={result.data} busy={busy} failed={!!active?.error}
          onPage={page => { if (!busy && !active?.error) change({ ...query, page }) }} />}
      </section>
    </>
  )
}
