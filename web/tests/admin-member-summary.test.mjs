import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const api = await loadTypeScript(
  "../src/lib/admin-member-summary.ts",
  import.meta.url,
)
const fixture = () => ({
  queriedAt: "2026-11-16T15:00:00Z",
  baseDate: "2026-11-17",
  counts: {
    currentMemberCount: 7,
    todayMemberCount: 1000,
    pendingMemberCount: 3,
    suspendedMemberCount: 5,
  },
})
test("회원 요약은 각 수치의 0과 양수를 보존한다", () => {
  assert(api.isMemberSummary(fixture()))
  assert(
    api.isMemberSummary({
      ...fixture(),
      counts: {
        currentMemberCount: 0,
        todayMemberCount: 0,
        pendingMemberCount: 0,
        suspendedMemberCount: 0,
      },
    }),
  )
})
test("회원 요약의 누락·null·잘못된 숫자와 날짜를 거부한다", () => {
  for (const location of ["root", "counts"]) {
    for (const key of Object.keys(
      location === "root" ? fixture() : fixture().counts,
    )) {
      for (const mode of ["missing", "null"]) {
        const value = fixture(),
          holder = location === "root" ? value : value.counts
        if (mode === "missing") delete holder[key]
        else holder[key] = null
        assert(!api.isMemberSummary(value), `${location}.${key} ${mode}`)
      }
    }
  }
  for (const key of Object.keys(fixture().counts)) {
    for (const value of [
      -1,
      0.5,
      "7",
      true,
      NaN,
      Infinity,
      Number.MAX_SAFE_INTEGER + 1,
    ]) {
      assert(
        !api.isMemberSummary({
          ...fixture(),
          counts: { ...fixture().counts, [key]: value },
        }),
      )
    }
  }
  assert(!api.isMemberSummary({ ...fixture(), baseDate: "2026-02-30" }))
  assert(!api.isMemberSummary({ ...fixture(), queriedAt: "2026-11-17" }))
})
test("회원 요약은 조건 없는 세션 GET과 no-store로 조회하고 오류를 보존한다", async () => {
  const oldFetch = globalThis.fetch,
    oldBase = process.env.NEXT_PUBLIC_API_BASE_URL
  process.env.NEXT_PUBLIC_API_BASE_URL = "http://summary.test"
  try {
    globalThis.fetch = async (url, options) => {
      assert.equal(url, "http://summary.test/api/v1/admin/members/summary")
      assert.equal(options.method, "GET")
      assert.equal(options.credentials, "include")
      assert.equal(options.cache, "no-store")
      assert.equal(options.body, undefined)
      assert.deepEqual(options.headers, { Accept: "application/json" })
      return Response.json(fixture())
    }
    assert.deepEqual(
      await api.getMemberSummary(new AbortController().signal),
      fixture(),
    )
    for (const status of [401, 403, 500]) {
      globalThis.fetch = async () => Response.json({}, { status })
      await assert.rejects(
        api.getMemberSummary(new AbortController().signal),
        (error) => error.status === status,
      )
    }
    globalThis.fetch = async () => Response.json({})
    await assert.rejects(api.getMemberSummary(new AbortController().signal))
  } finally {
    globalThis.fetch = oldFetch
    if (oldBase === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL
    else process.env.NEXT_PUBLIC_API_BASE_URL = oldBase
  }
})
