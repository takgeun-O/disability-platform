"use client"

import { useEffect, useState } from "react"
import { useSearchParams } from "next/navigation"
import "@/styles/community.css"
import Link from "@/components/AppLink"
import HomeSearch from "@/components/HomeSearch"
import { CommunityBanner } from "@/components/community/CommunityParts"
import { CommunityApiError, getPosts, type PostPage, type PostListItem } from "@/lib/community-api"
import {
  DEFAULT_QUERY, SEARCH_PAGE_SIZE, POST_TYPES, POST_TOPICS, type PostType,
  normalizeTitle, postDate, rememberListScroll, restoreListScroll,
  searchHref, searchKeywordError, searchPage, searchType,
} from "@/lib/community-posts"

type SearchType = PostType | ""
const types: SearchType[] = ["", "QUESTION", "REVIEW", "GENERAL"]

function highlightedText(text: string, pattern: RegExp) {
  return text.split(pattern).map((part, index) => index % 2
    ? <mark key={index} className="rounded-sm bg-amber-100 font-bold text-inherit">{part}</mark>
    : part)
}

function ResultRow({ post, pattern, href }: {
  post: PostListItem; pattern: RegExp; href: string
}) {
  return <li>
    <Link className="relative block rounded-sm py-4 pl-2 pr-9 [overflow-wrap:anywhere] hover:bg-[var(--iyum-mist)]"
      aria-label={post.title} prefetch={false}
      href={`/community/posts/${post.id}?${new URLSearchParams({ from: href })}`}
      onNavigate={() => rememberListScroll(href)}>
      <span className="font-semibold text-[var(--iyum-ink)]">
        {highlightedText(post.title, pattern)}
      </span>
      {post.matchedContentExcerpt !== null && (
        <p className="mt-1 whitespace-normal text-sm">{highlightedText(post.matchedContentExcerpt, pattern)}</p>
      )}
      <p className="mt-1 flex flex-wrap gap-x-3 text-sm text-[var(--iyum-muted)]">
        <span>{POST_TYPES[post.type]}</span><span>{POST_TOPICS[post.topic]}</span>
        <time dateTime={post.createdAt}>작성일 {postDate(post.createdAt)}</time>
      </p>
      <span aria-hidden="true" className="absolute right-3 top-1/2 -translate-y-1/2 text-xl text-[var(--iyum-muted)]">›</span>
    </Link>
  </li>
}

function ResultPage({ data, keyword, type, page, href }: {
  data: PostPage; keyword: string; type: SearchType; page: number; href: string
}) {
  useEffect(() => restoreListScroll(href), [href])
  if (data.totalElements === 0) {
    return type
      ? <Link className="iyum-text-link" href={searchHref(keyword)}>전체 유형에서 검색</Link>
      : <p>다른 검색어나 더 짧은 단어로 검색해 보세요.</p>
  }
  const pattern = new RegExp("(" + keyword.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "iu")
  return <>
    <p role="status">총 {data.totalElements.toLocaleString("ko-KR")}건</p>
    {data.items.length ? <ol className="divide-y divide-[var(--iyum-border)]">
      {data.items.map(post => <ResultRow key={post.id} post={post} pattern={pattern} href={href} />)}
    </ol> : <p>이 페이지에 표시할 게시글이 없습니다.</p>}
    {(page > 1 || data.hasNext) && <nav aria-label="검색 결과 페이지" className="mt-4 flex flex-wrap items-center gap-4">
      {page > 1 && <Link className="iyum-button iyum-button-outline" href={searchHref(keyword, Math.min(page - 1, Math.max(1, data.totalPages)), type)}>이전 페이지</Link>}
      <span aria-current="page">{page}페이지</span>
      {data.hasNext && <Link className="iyum-button iyum-button-outline" href={searchHref(keyword, page + 1, type)}>다음 페이지</Link>}
    </nav>}
  </>
}

function SearchPanel({ keyword, page, type }: { keyword: string; page: number; type: SearchType }) {
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<PostPage | CommunityApiError | null>(null)
  const href = searchHref(keyword, page, type)
  const inputError = searchKeywordError(keyword)
  const empty = result !== null && !(result instanceof CommunityApiError) && result.totalElements === 0
  const invalidKeyword = result instanceof CommunityApiError && result.status === 400 && result.code === "INVALID_SEARCH_KEYWORD"

  useEffect(() => {
    if (inputError) return
    const controller = new AbortController()
    getPosts({ ...DEFAULT_QUERY, q: keyword, type, page: page - 1, size: SEARCH_PAGE_SIZE }, controller.signal)
      .then(data => {
        if (!controller.signal.aborted) setResult(data)
      })
      .catch(error => {
        if (!controller.signal.aborted) setResult(error instanceof CommunityApiError
          ? error : new CommunityApiError(0, "UNEXPECTED_RESPONSE", "검색 결과를 불러오지 못했습니다."))
      })
    return () => controller.abort()
  }, [keyword, page, type, inputError, attempt])

  return <>
    <HomeSearch initialQuery={keyword} type={type} keywordError={invalidKeyword ? result.message : inputError} />
    {!inputError && <>
      <nav aria-label="글 유형" className="mt-4 flex flex-wrap gap-2">
        {types.map(value => <Link key={value} prefetch={false} href={searchHref(keyword, 1, value)}
          aria-current={type === value ? "page" : undefined}
          className={`iyum-button ${type === value ? "iyum-button-primary font-bold" : "iyum-button-outline"}`}>
          {value ? POST_TYPES[value] : "전체"}
        </Link>)}
      </nav>
      <section className="mt-6" aria-labelledby="search-heading">
        <div role="status">
          <h2 id="search-heading" className="break-all text-xl">
            {empty
              ? type ? `${POST_TYPES[type]}에서 ‘${keyword}’에 해당하는 글이 없어요.` : `‘${keyword}’에 해당하는 게시글이 없어요.`
              : `‘${keyword}’ 검색 결과`}
          </h2>
        </div>
        {!result ? <p role="status">검색 결과를 불러오는 중입니다.</p>
          : result instanceof CommunityApiError ? !invalidKeyword && <div className="community-error" role="alert">
            <p>{result.message}</p>
            <button className="iyum-button iyum-button-outline" onClick={() => {
              setResult(null)
              setAttempt(n => n + 1)
            }}>다시 시도</button>
          </div>
          : <ResultPage data={result} keyword={keyword} type={type} page={page} href={href} />}
      </section>
    </>}
  </>
}

export default function SearchResults() {
  const params = useSearchParams()
  const keyword = normalizeTitle(params.get("q") ?? "")
  const page = searchPage(params)
  const type = searchType(params)
  return <main id="main-content" className="community-screen">
    <CommunityBanner title="게시글 검색" description="커뮤니티에서 질문과 경험을 찾아보세요." />
    <div className="iyum-container community-reading pt-6">
      <SearchPanel key={searchHref(keyword, page, type)} keyword={keyword} type={type} page={page} />
    </div>
  </main>
}
