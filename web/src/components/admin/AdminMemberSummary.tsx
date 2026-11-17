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
  getMemberSummary,
  type MemberSummary,
} from "@/lib/admin-member-summary"
import { writtenTime } from "@/lib/written-time"
import "@/styles/admin-dashboard.css"
import "@/styles/admin-member-summary.css"

const number = new Intl.NumberFormat("ko-KR")
const cards = [
  ["currentMemberCount", "현재 회원", "전체 기간 · 탈퇴 제외"],
  ["todayMemberCount", "오늘 가입", "오늘 생성된 계정 · 현재 상태 무관"],
  ["pendingMemberCount", "인증 대기", "전체 기간 · 이메일 인증 대기"],
  ["suspendedMemberCount", "이용 정지", "전체 기간 · 현재 이용 정지"],
] as const
export function useMemberSummary(protect: (error: unknown) => boolean) {
  const auth = useAuth()
  const actor = auth.member!.memberId
  const capture = auth.captureMemberRequest
  const [data, setData] = useState<MemberSummary | null>(null)
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
    void getMemberSummary(
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
            : "회원 요약을 불러오지 못했습니다. 다시 조회해 주세요.",
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

export default function AdminMemberSummary({
  data,
  loading,
  error,
  manualRefresh,
}: ReturnType<typeof useMemberSummary>) {
  return (
    <section className="member-summary" aria-label="회원 요약">
      <div className="member-summary-heading">
        <div className="member-summary-context">
          <p>목록 필터와 무관한 전체 회원 기준 · 관리자 포함</p>
          {data && (
            <div className="member-summary-dates">
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
        className={loading ? "member-summary-status" : "sr-only"}
      >
        {loading
          ? data
            ? "회원 요약을 갱신하고 있습니다. 이전 조회 결과를 표시합니다."
            : "회원 요약을 불러오고 있습니다…"
          : !error
            ? "회원 요약 조회를 완료했습니다."
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
        <dl className="dashboard-counts member-summary-counts">
          {cards.map(([key, label, hint]) => (
            <div key={key} className="dashboard-count">
              <dt>{label}</dt>
              <dd>
                {data ? (
                  <>
                    {number.format(data.counts[key])}
                    <span>명</span>
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
