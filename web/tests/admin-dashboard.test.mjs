import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const a = await loadTypeScript("../src/lib/admin-dashboard.ts", import.meta.url)
const fixture = () => ({
  queriedAt: "2026-11-16T15:00:00Z", baseDate: "2026-11-17",
  counts: { pendingReportCount: 7, todayReportCount: 1000, todayPostCount: 3, todayMemberCount: 4 },
  pendingReports: [{ reportId: 91, targetType: "COMMENT", targetId: 31, reasonType: "OTHER", createdAt: "2026-11-15T01:00:00Z" }],
})
test("전체 개수와 목록 길이가 달라도 서버 응답과 빈 결과를 허용한다", () => {
  assert(a.isDashboard(fixture()))
  assert(a.isDashboard({ ...fixture(), counts: { pendingReportCount: 0, todayReportCount: 0, todayPostCount: 0, todayMemberCount: 0 }, pendingReports: [] }))
  assert(a.isDashboard({ ...fixture(), counts: { ...fixture().counts, pendingReportCount: 0 } }))
  assert(a.isDashboard({ ...fixture(), pendingReports: Array.from({ length: 5 }, (_, i) => ({ ...fixture().pendingReports[0], reportId: i + 1 })) }))
  assert(!a.isDashboard({ ...fixture(), pendingReports: Array(6).fill(fixture().pendingReports[0]) }))
})
test("필수 필드 누락과 null 및 잘못된 응답 타입을 거부한다", () => {
  for (const location of ["root", "counts", "item"]) {
    const sample = fixture()
    const holder = location === "root" ? sample : location === "counts" ? sample.counts : sample.pendingReports[0]
    for (const key of Object.keys(holder)) for (const mode of ["missing", "null"]) {
      const data = fixture()
      const target = location === "root" ? data : location === "counts" ? data.counts : data.pendingReports[0]
      if (mode === "missing") delete target[key]
      else target[key] = null
      assert(!a.isDashboard(data), `${location}.${key} ${mode}`)
    }
  }
  for (const value of [-1, 0.5, "7", true, Number.MAX_SAFE_INTEGER + 1])
    for (const key of Object.keys(fixture().counts)) assert(!a.isDashboard({ ...fixture(), counts: { ...fixture().counts, [key]: value } }))
  for (const change of [{ targetType: "ALL" }, { reasonType: "LEGACY" }, { reportId: 0 }, { targetId: "31" }, { createdAt: "not-time" }])
    assert(!a.isDashboard({ ...fixture(), pendingReports: [{ ...fixture().pendingReports[0], ...change }] }))
  assert(!a.isDashboard({ ...fixture(), baseDate: "2026-02-30" }))
  assert(!a.isDashboard({ ...fixture(), queriedAt: "2026-11-17" }))
})
test("신고 링크는 기존 필터와 무관한 전체 기간의 접수 목록과 신고 ID를 사용한다", () => {
  assert.equal(a.dashboardReportHref(91), "/admin/reports/91")
  for (const id of [null]) {
    const url = new URL(a.dashboardReportHref(id), "http://local")
    assert.equal(url.pathname, "/admin/reports")
    assert.equal(url.searchParams.get("status"), "RECEIVED")
    assert.equal(url.searchParams.get("targetType"), "ALL")
    assert.equal(url.searchParams.get("page"), "0")
    assert.equal(url.searchParams.get("sort"), "OLDEST")
    assert.equal(url.searchParams.get("selected"), id === null ? null : String(id))
    for (const key of ["startDate", "endDate", "reasonType", "size"]) assert(!url.searchParams.has(key))
  }
})
test("대시보드 GET은 세션과 no-store만 사용하고 응답을 그대로 검증한다", async () => {
  const oldFetch = globalThis.fetch, oldBase = process.env.NEXT_PUBLIC_API_BASE_URL
  process.env.NEXT_PUBLIC_API_BASE_URL = "http://dashboard.test"
  try {
    let calls = 0
    globalThis.fetch = async (url, options) => {
      calls++
      assert.equal(url, "http://dashboard.test/api/v1/admin/dashboard")
      assert.equal(options.method, "GET")
      assert.equal(options.credentials, "include")
      assert.equal(options.cache, "no-store")
      assert.equal(options.body, undefined)
      assert.deepEqual(options.headers, { Accept: "application/json" })
      return Response.json(fixture())
    }
    assert.deepEqual(await a.getDashboard(new AbortController().signal), fixture())
    assert.equal(calls, 1)
    for (const status of [401, 403, 500]) {
      globalThis.fetch = async () => Response.json({ message: "내용으로 판정하지 않음" }, { status })
      await assert.rejects(a.getDashboard(new AbortController().signal), error => error.status === status)
    }
    globalThis.fetch = async () => Response.json({})
    await assert.rejects(a.getDashboard(new AbortController().signal))
  } finally {
    globalThis.fetch = oldFetch
    if (oldBase === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL
    else process.env.NEXT_PUBLIC_API_BASE_URL = oldBase
  }
})
