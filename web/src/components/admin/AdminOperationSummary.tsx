"use client"

// 목록 URL과 별개로 유지하여 필터·상세 선택 시 재조회하지 않는다.
// Filter changes must neither restart this GET nor invalidate its pending response.

import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react"
import { useAuth } from "@/components/AuthProvider"
import { AdminApiError } from "@/lib/admin-posts"
import {
  getOperationSummary,
  type OperationSummary,
} from "@/lib/admin-operation-summary"
import { writtenTime } from "@/lib/written-time"
import "@/styles/admin-dashboard.css"
import "@/styles/admin-operation-summary.css"

const number = new Intl.NumberFormat("ko-KR")
const cards = [
  ["totalOperationLogCount", "전체 운영 기록", "전체 기간 · 숨김·숨김 해제"],
  ["todayOperationLogCount", "오늘 운영 기록", "오늘 저장된 운영 기록"],
  ["todayHideCount", "오늘 숨김", "오늘 저장된 숨김 기록"],
  ["todayRestoreCount", "오늘 숨김 해제", "오늘 저장된 숨김 해제 기록"],
] as const
export function useOperationSummary(protect: (error: unknown) => boolean) {
  const auth = useAuth()
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const [data, setData] = useState<OperationSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const request = useRef<AbortController | null>(null)
  const load = useCallback(() => {
    request.current?.abort()
    const controller = new AbortController()
    request.current = controller
    const session = capture(actor)
    const valid = () =>
      request.current === controller &&
      !controller.signal.aborted &&
      session.isCurrent()
    void getOperationSummary(
      AbortSignal.any([controller.signal, AbortSignal.timeout(15000)]),
    )
      .then((latest) => {
        if (valid()) {
          setData(latest)
          setError("")
        }
      })
      .catch((failure: unknown) => {
        if (!valid()) return
        if (protect(failure)) {
          setData(null)
          return
        }
        setError(
          failure instanceof AdminApiError
            ? failure.message
            : "운영 기록 요약을 불러오지 못했습니다. 다시 조회해 주세요.",
        )
      })
      .finally(() => {
        if (valid()) setLoading(false)
        if (request.current === controller) request.current = null
      })
  }, [actor, capture, protect])
  useLayoutEffect(() => () => request.current?.abort(), [])
  useEffect(() => {
    load()
    return () => {
      request.current?.abort()
    }
  }, [load])
  const refresh = useCallback(() => {
    setLoading(true)
    setError("")
    load()
  }, [load])
  const manualRefresh = () => {
    if (!loading && !request.current) refresh()
  }
  return { data, loading, error, refresh, manualRefresh }
}

export default function AdminOperationSummary({
  data,
  loading,
  error,
  manualRefresh,
}: ReturnType<typeof useOperationSummary>) {
  return (
    <section className="operation-summary" aria-label="운영 기록 요약">
      <div className="operation-summary-heading">
        <div className="operation-summary-context">
          <p>목록 필터와 무관한 게시글·댓글 숨김·숨김 해제 기록 기준</p>
          {data && (
            <div className="operation-summary-dates">
              <p>
                집계 기준 날짜{" "}
                <time dateTime={data.baseDate}>
                  {data.baseDate.replace(/-/g, ".")}
                </time>
              </p>
              <p>
                조회 기준{" "}
                <time dateTime={data.queriedAt}>
                  {writtenTime(data.queriedAt).exact}
                </time>
              </p>
            </div>
          )}
        </div>
        <button type="button" aria-disabled={loading} onClick={manualRefresh}>
          요약 새로고침
        </button>
      </div>
      <p
        role="status"
        className={loading ? "operation-summary-status" : "sr-only"}
      >
        {loading
          ? data
            ? "운영 기록 요약을 갱신하고 있습니다. 이전 조회 결과를 표시합니다."
            : "운영 기록 요약을 불러오고 있습니다…"
          : !error
            ? "운영 기록 요약 조회를 완료했습니다."
            : ""}
      </p>
      {error && (
        <div role="alert" className="dashboard-error">
          <p>{error}</p>
          {data && (
            <p>
              최신 요약 조회에 실패했습니다. 이전 수치와 조회 시각을 표시합니다.
            </p>
          )}
          <button type="button" aria-disabled={loading} onClick={manualRefresh}>
            요약 다시 조회
          </button>
        </div>
      )}
      <div aria-busy={loading}>
        <dl className="dashboard-counts operation-summary-counts">
          {cards.map(([key, label, hint]) => (
            <div key={key} className="dashboard-count">
              <dt>{label}</dt>
              <dd>
                {data ? (
                  <>
                    {number.format(data.counts[key])}
                    <span>건</span>
                  </>
                ) : (
                  <span aria-label="미조회">—</span>
                )}
              </dd>
              <dd className="dashboard-count-hint">{hint}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  )
}
