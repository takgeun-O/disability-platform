"use client"
// 입력 중인 조건은 이 폼의 로컬 상태다. 적용하기 전에는 URL이나 목록 요청을 바꾸지 않는다.
// URL 전환이 커밋되기 전에도 이전 응답을 무효화한다.
import { usePostVersion } from "@/lib/community-mutations"
import { useEffect, useId, useRef, useState, type FormEvent } from "react"
import { usePathname, useSearchParams } from "next/navigation"
import Link from "@/components/AppLink"
import CommunityDialog from "@/components/community/CommunityDialog"
import { useNavigationGuard } from "@/components/NavigationGuard"
import { focusForReading } from "@/lib/reading-focus"
import {
  BestPosts,
  CommunityBanner,
  PostTags,
} from "@/components/community/CommunityParts"
import { CommunityApiError, getPosts, type PostPage } from "@/lib/community-api"
import {
  POST_TYPES,
  POST_TOPICS,
  listQuery,
  listHref,
  queryLabel,
  postDate,
  rememberListScroll,
  restoreListScroll,
  clearListScroll,
  type PostQuery,
} from "@/lib/community-posts"

import { TopicHeader, TopicSpaces } from "@/components/community/TopicSpaces"
import type { CommunityTopic } from "@/lib/community-topics"

import { pageIsOutOfRange, type InitialPostList } from "@/lib/community-load"

function FilterForm({
  initial,
  onApply,
  onCancel,
  fixedTopic,
}: {
  initial: PostQuery
  fixedTopic?: CommunityTopic
  onApply: (query: PostQuery) => void
  onCancel?: () => void
}) {
  const [type, setType] = useState(initial.type)
  const [topic, setTopic] = useState(initial.topic)
  const id = useId()
  function submit(event: FormEvent) {
    event.preventDefault()
    onApply({ type, topic: fixedTopic?.topic ?? topic, page: 0 })
  }
  return (
    <form
      className="community-filter-form"
      onSubmit={submit}
      aria-label="게시글 필터"
    >
      <div>
        <label htmlFor={`${id}-type`}>글 유형</label>
        <select
          data-initial-focus
          id={`${id}-type`}
          value={type}
          onChange={(e) => setType(e.target.value as PostQuery["type"])}
        >
          <option value="">전체 유형</option>
          {Object.entries(POST_TYPES).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {!fixedTopic && <div>
        <label htmlFor={`${id}-topic`}>주제</label>
        <select
          id={`${id}-topic`}
          value={topic}
          onChange={(e) => setTopic(e.target.value as PostQuery["topic"])}
        >
          <option value="">전체 주제</option>
          {Object.entries(POST_TOPICS).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
      </div>}
      <div className="community-filter-actions">
        {onCancel && (
          <button
            type="button"
            className="iyum-button iyum-button-outline"
            onClick={onCancel}
          >
            취소
          </button>
        )}
        <button className="iyum-button iyum-button-primary" type="submit">
          적용
        </button>
      </div>
    </form>
  )
}


type Snapshot = { key: string; query: PostQuery; page: PostPage }
type Result = { key: string; snapshot: Snapshot | null; error: string | null }
export default function PostList({ initial, space }: { initial: InitialPostList; space?: CommunityTopic }) {
  const params = useSearchParams()
  const pathname = usePathname()
  const query = listQuery(params, pathname)
  const href = listHref(query, pathname)
  const [retry, setRetry] = useState(0)
  const mutation = usePostVersion("list")
  const requestKey = `${href}:${retry}:${mutation}`
  const [result, setResult] = useState<Result>({
    key: `${listHref(initial.query, pathname)}:0:0`,
    snapshot: initial.data ? { key: listHref(initial.query, pathname), query: initial.query, page: initial.data } : null,
    error: initial.error?.message ?? null,
  })
  const [filterOpen, setFilterOpen] = useState(false)
  const mobileFilterButton = useRef<HTMLButtonElement>(null)
  const { getApprovalVersion } = useNavigationGuard()
  const keyboardInteraction = useRef(false)
  const generation = useRef(0)
  const firstRequest = useRef(true)
  const heading = useRef<HTMLHeadingElement>(null)
  const movement = useRef<{ href: string; kind: "start" | "history"; y?: number; keyboard?: boolean; navigationVersion?: number; owner?: Element | null } | null>(null)
  const entryPositions = useRef(new Map<string, number>())
  const entryId = useRef("")
  useEffect(() => {
    // Next가 사용하는 history.state는 보존하고 이 목록의 entry 식별자만 추가한다.
    const id = window.history.state?.iyumListEntry ?? crypto.randomUUID()
    window.history.replaceState({ ...window.history.state, iyumListEntry: id }, "")
    entryId.current = id
    const remember = () => entryPositions.current.set(entryId.current, window.scrollY)
    const pop = () => {
      generation.current++
      entryId.current = window.history.state?.iyumListEntry ?? ""
      movement.current = { href: listHref(listQuery(new URLSearchParams(location.search), location.pathname), location.pathname),
        kind: "history", y: entryPositions.current.get(entryId.current) }
    }
    window.addEventListener("scroll", remember, { passive: true })
    window.addEventListener("popstate", pop)
    return () => {
      window.removeEventListener("scroll", remember)
      window.removeEventListener("popstate", pop)
    }
  }, [])
  const loading =
    result.key !==
    requestKey
  const snapshot = result.key.endsWith(`:${mutation}`) ? result.snapshot : null
  const displayedQuery =
    snapshot?.query ??
    query
  const error = !loading ? result.error : null
  const queryType = query.type,
    queryTopic = query.topic,
    queryPage = query.page

  useEffect(() => {
    // Strict Mode에서도 초기 SSR 결과를 다시 요청하지 않는다.
    if (firstRequest.current && requestKey === `${listHref(initial.query, pathname)}:0:0`) return
    firstRequest.current = false
    const ticket = ++generation.current
    const controller = new AbortController()
    const requested = { type: queryType, topic: queryTopic, page: queryPage }
    getPosts(requested, controller.signal)
      .then((page) => {
        if (
          ticket ===
            generation.current &&
          !controller.signal.aborted
        ) {
          setResult({
            key: requestKey,
            snapshot: { key: href, query: requested, page },
            error: null,
          })
        }
      })
      .catch((error) => {
        if (
          ticket ===
            generation.current &&
          !controller.signal.aborted
        ) {
          setResult((previous) => ({
            key: requestKey,
            snapshot: previous.key.endsWith(`:${mutation}`) ? previous.snapshot : null,
            error:
              error instanceof
              CommunityApiError
                ? error.message
                : "게시글을 불러오지 못했습니다. 다시 시도해 주세요.",
          }))
        }
      })
    return () => {
      controller.abort()
    }
  }, [href, requestKey, queryType, queryTopic, queryPage, initial.query, pathname, mutation])

  useEffect(() => {
    if (loading || error || snapshot?.key !== href) return
    const pending = movement.current
    if (pending?.href === href) {
      const frame = requestAnimationFrame(() => {
        if (movement.current !== pending) return
        movement.current = null
        if (pending.kind === "start") {
          if (pending.navigationVersion !== getApprovalVersion()) return
          clearListScroll(href)
          if (heading.current && pending.keyboard &&
              (document.activeElement === pending.owner || document.activeElement === document.body))
            focusForReading(heading.current, true)
          heading.current?.scrollIntoView({ block: "start", behavior: "instant" })
        } else if (pending.y !== undefined) {
          window.scrollTo({ top: pending.y, behavior: "instant" })
        }
      })
      return () => cancelAnimationFrame(frame)
    }
    // 상세에서 명시적으로 기록한 복귀 위치만 사용한다. 초기 진입에는 이동하지 않는다.
    return restoreListScroll(href)
  }, [loading, error, snapshot, href, getApprovalVersion])

  function navigate(next: PostQuery) {
    generation.current++
    firstRequest.current = false
    const destination = listHref(next, pathname)
    movement.current = { href: destination, kind: "start", keyboard: keyboardInteraction.current, navigationVersion: getApprovalVersion(), owner: filterOpen ? mobileFilterButton.current : document.activeElement }
    entryPositions.current.set(entryId.current, window.scrollY)
    if (destination === href) setRetry((value) => value + 1)
    else {
      entryId.current = crypto.randomUUID()
      // Next와 동기화되는 History API: 이 화면 안의 조회는 브라우저에서 한 번만 실행한다.
      window.history.pushState({ iyumListEntry: entryId.current }, "", destination)
    }
    setFilterOpen(false)
  }
  const returnHref = snapshot?.key ?? href
  const createQuery = new URLSearchParams({ from: returnHref })
  if (displayedQuery.type) createQuery.set("type", displayedQuery.type)
  if (displayedQuery.topic) createQuery.set("topic", displayedQuery.topic)
  const totalPages = snapshot?.page.totalPages ?? 0
  const outOfRange = snapshot ? pageIsOutOfRange(snapshot.page) : false
  const currentPage = displayedQuery.page
  const firstPage = Math.max(0, Math.min(currentPage - 1, totalPages - 3))
  return (
    <main id="main-content" className={`community-screen${space ? " community-topic-screen" : ""}`}
      onPointerDownCapture={() => { keyboardInteraction.current = false }}
      onKeyDownCapture={() => { keyboardInteraction.current = true }}
      onClickCapture={event => { keyboardInteraction.current = event.detail === 0 }}
    >
      {space ? <TopicHeader space={space} /> : <><CommunityBanner writeHref={`/community/posts/create?${createQuery}`} /><TopicSpaces /></>}
      <div className="iyum-container community-grid">
        {!space && <BestPosts />}
        <section className="community-list-column" aria-label="게시글 탐색">
          {space && <div className="topic-list-actions"><p>{space.name}의 질문과 경험</p><Link className="iyum-button iyum-button-primary" onNavigate={() => rememberListScroll(returnHref)} href={`/community/posts/create?${createQuery}`}>＋ 글쓰기</Link></div>}
          <div className="community-desktop-filters">
            <FilterForm key={href} initial={query} onApply={navigate} fixedTopic={space} />
          </div>
          <div className="community-mobile-filters iyum-card">
            <span>{queryLabel(displayedQuery)}</span>
            <button
              ref={mobileFilterButton}
              className="iyum-button iyum-button-outline"
              aria-haspopup="dialog"
              onClick={() => setFilterOpen(true)}
            >
              <span aria-hidden="true">☷</span> 필터
            </button>
          </div>
          <div className="community-list-heading">
            <div>
              <h2 ref={heading} tabIndex={-1} id="community-results-heading" aria-describedby="community-result-status">{space ? `${space.name} 게시글` : "전체 게시글"}</h2>
              <p className="community-applied">{queryLabel(displayedQuery)}</p>
            </div>
            <span>최신순</span>
          </div>
          <div
            id="community-result-status"
            className="community-request-status"
            role="status"
            aria-live="polite"
          >
            {loading
              ? `${queryLabel(query)} 게시글을 불러오고 있습니다.${
                  snapshot ? " 이전 결과를 보여드리고 있어요." : ""
                }`
              : !error && snapshot
                ? outOfRange ? "요청한 페이지가 현재 목록 범위를 벗어났습니다." : `${snapshot.page.totalElements}개의 글 · ${
                    snapshot.page.totalPages ? snapshot.page.page + 1 : 0
                  } / ${snapshot.page.totalPages}페이지`
                : ""}
          </div>
          {error && (
            <div className="community-error" role="alert">
              <p>{error}</p>
              <p>
                {queryLabel(query)} · {query.page + 1}페이지 조회에
                실패했습니다.
                {snapshot ? " 아래 목록은 이전 조회 결과입니다." : ""}
              </p>
              <button
                className="iyum-button iyum-button-outline"
                onClick={() => navigate(query)}
              >
                다시 시도
              </button>
            </div>
          )}
          <div aria-busy={loading}>
            {snapshot && snapshot.page.items.length > 0 && (
              <ol className="community-post-list" aria-label="게시글 목록">
                {snapshot.page.items.map((post) => (
                  <li key={post.id}>
                    <PostTags type={post.type} topic={post.topic} />
                    <Link
                      href={`/community/posts/${post.id}?from=${encodeURIComponent(returnHref)}`}
                      onClick={() => rememberListScroll(returnHref)}
                    >
                      {post.title}
                    </Link>
                    <div className="community-post-meta">
                      <span>{post.author.nickname}</span>
                      <span aria-hidden="true">·</span>
                      <time dateTime={post.createdAt}>
                        {postDate(post.createdAt)}
                      </time>
                      <span className="community-comment-count">댓글 {post.commentCount}</span>
                    </div>
                  </li>
                ))}
              </ol>
            )}
            {snapshot && outOfRange && (
              <div className="community-empty">
                <h3>요청한 페이지를 표시할 수 없어요.</h3>
                <p>게시글이 삭제되었거나 페이지 범위를 벗어났습니다. 같은 조건의 첫 페이지에서 다시 확인해 주세요.</p>
                <button className="iyum-button iyum-button-outline" onClick={() => navigate({ ...displayedQuery, page: 0 })}>
                  같은 조건의 첫 페이지로
                </button>
              </div>
            )}
            {snapshot && !outOfRange && snapshot.page.items.length === 0 && (
              <div className="community-empty">
                <h3>아직 표시할 게시글이 없어요.</h3>
                <p>{space ? `${space.name}에 관한 첫 이야기를 나눠 주세요. 글 유형을 바꿔 다른 글을 살펴볼 수도 있어요.` : "다른 조건으로 살펴보거나 첫 이야기를 나눠 주세요."}</p>
                {space && <Link className="iyum-button iyum-button-primary" onNavigate={() => rememberListScroll(returnHref)} href={`/community/posts/create?${createQuery}`}>이 주제로 글쓰기</Link>}
                <button
                  className="iyum-button iyum-button-outline"
                  onClick={() => navigate({ type: "", topic: space?.topic ?? "", page: 0 })}
                >
                  {space ? "이 주제의 모든 글 유형 보기" : "전체 게시글 보기"}
                </button>
              </div>
            )}
            {!snapshot && loading && (
              <div className="community-loading" aria-hidden="true">
                목록을 준비하고 있어요.
              </div>
            )}
          </div>
          {totalPages > 0 && !outOfRange && (
            <nav className="community-pagination" aria-label="게시글 페이지">
              <button
                disabled={currentPage === 0}
                aria-label="이전 페이지"
                onClick={() =>
                  navigate({ ...displayedQuery, page: currentPage - 1 })
                }
              >
                ‹
              </button>
              {Array.from(
                { length: Math.min(3, totalPages) },
                (_, index) => firstPage + index,
              ).map((page) => (
                <button
                  key={page}
                  aria-label={`${page + 1}페이지`}
                  aria-current={page === currentPage ? "page" : undefined}
                  onClick={() => navigate({ ...displayedQuery, page })}
                >
                  {page + 1}
                </button>
              ))}
              <button
                disabled={!snapshot?.page.hasNext}
                aria-label="다음 페이지"
                onClick={() =>
                  navigate({ ...displayedQuery, page: currentPage + 1 })
                }
              >
                ›
              </button>
            </nav>
          )}
        </section>
        {space && <TopicSpaces current={space} />}
      </div>
      {filterOpen && (
        <CommunityDialog
          titleId="community-filter-heading"
          onCancel={() => setFilterOpen(false)}
        >
          <h2 id="community-filter-heading">게시글 필터</h2>
          <p>조건을 선택하고 적용해 주세요.</p>
          <FilterForm
            initial={query}
            fixedTopic={space}
            onApply={navigate}
            onCancel={() => setFilterOpen(false)}
          />
        </CommunityDialog>
      )}
    </main>
  )
}
