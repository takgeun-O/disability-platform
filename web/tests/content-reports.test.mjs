import test from "node:test"
import assert from "node:assert/strict"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const a = await loadTypeScript("../src/lib/content-reports.ts", import.meta.url)
const stamp = "2026-11-13T01:00:00Z"
const detail = {
  reportId: 12,
  targetType: "COMMENT",
  targetId: 88,
  status: "RECEIVED",
  reasonType: "OTHER",
  titleSnapshot: null,
  contentSnapshot: "접수 당시 내용",
  reasonDetail: "사유",
  createdAt: stamp,
  reporterMemberId: 4,
  currentTarget: {
    targetId: 88,
    parentPostId: 7,
    author: { memberId: 6, nickname: "작성자", withdrawn: false },
    title: null,
    content: "현재 내용",
    hiddenAt: null,
    deletedAt: null,
  },
  handledByMemberId: null,
  handledAt: null,
  handlingReason: null,
}
test("create and handling whitespace policies differ; normalized codepoints and inner newlines", () => {
  assert.equal(a.reportDetailText("\u00a0 내용\u00a0"), "\u00a0 내용\u00a0")
  assert.equal(a.handlingText("\u00a0 내용\u00a0"), "내용")
  assert.equal(a.reportDetailText("　 첫 줄\n둘째 줄　"), "첫 줄\n둘째 줄")
  for (const type of Object.keys(a.REPORT_REASONS))
    assert.equal(a.reportInputError(type, type === "OTHER" ? "설명" : ""), "")
  assert(a.reportInputError("", "내용"))
  assert(a.reportInputError("OTHER", "　 "))
  assert.equal(a.reportInputError("OTHER", " 😀".trim().repeat(500)), "")
  assert(a.reportInputError("OTHER", "😀".repeat(501)))
  assert(a.handlingError("　\u00a0"))
  assert.equal(a.handlingError("😀".repeat(500)), "")
  assert(a.handlingError("😀".repeat(501)))
})
test("UI defaults RECEIVED; API all omits status, selected never sent; URL round trip", () => {
  assert.equal(a.reportParams(a.defaultReportQuery).get("status"), "RECEIVED")
  const q = {
    ...a.defaultReportQuery,
    status: "ALL",
    reasonType: "OFF_TOPIC",
    startDate: "2026-11-01",
    endDate: "2026-11-13",
    sort: "OLDEST",
    page: 2,
  }
  assert(!a.reportParams(q).has("status"))
  assert(!a.reportParams(q).has("selected"))
  assert.deepEqual(
    a.reportQuery(new URL(a.reportHref(q, 12), "http://local").searchParams),
    q,
  )
  assert.equal(
    a.reportQuery(new URLSearchParams("status=LEGACY&page=-1&sort=bad")).status,
    "RECEIVED",
  )
})
test("detail requires explicit nullable fields and correct target identity", () => {
  assert(a.isReportDetail(detail))
  assert(a.isReportDetail({ ...detail, currentTarget: null }))
  for (const change of [
    { currentTarget: undefined },
    { handledAt: undefined },
    {
      currentTarget: { ...detail.currentTarget, title: "not a comment title" },
    },
    { currentTarget: { ...detail.currentTarget, targetId: 99 } },
    { status: "RESOLVED" },
  ])
    assert(!a.isReportDetail({ ...detail, ...change }))
  assert(
    a.isReportDetail({
      ...detail,
      status: "RESOLVED",
      handledByMemberId: 9,
      handledAt: stamp,
      handlingReason: "검토",
    }),
  )
})
test("list preserves nullable current author and server pagination", () => {
  const {
    reporterMemberId,
    reasonDetail,
    contentSnapshot,
    currentTarget,
    handledByMemberId,
    handledAt,
    handlingReason,
    ...base
  } = detail
  void [
    reporterMemberId,
    reasonDetail,
    contentSnapshot,
    currentTarget,
    handledByMemberId,
    handledAt,
    handlingReason,
  ]
  const item = {
      ...base,
      contentSnapshotPreview: "당시 요약",
      currentAuthor: null,
    },
    page = {
      items: [item],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    }
  assert(a.isReportPage(page))
  assert(
    !a.isReportPage({
      ...page,
      items: [{ ...item, currentAuthor: undefined }],
    }),
  )
  assert(!a.isReportPage({ ...page, totalPages: 0 }))
})
test("POST uses current session CSRF, exact paths and payloads; stale request sends no mutation", async () => {
  const previousBase = process.env.NEXT_PUBLIC_API_BASE_URL
  process.env.NEXT_PUBLIC_API_BASE_URL = "http://example.test"
  const original = globalThis.fetch,
    calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options })
    return new Response(
      JSON.stringify(
        String(url).endsWith("/csrf")
          ? { headerName: "X-CSRF-TOKEN", token: "synthetic" }
          : { reportId: 12, status: "RECEIVED", createdAt: stamp },
      ),
      {
        status: String(url).endsWith("/csrf") ? 200 : 201,
        headers: { "Content-Type": "application/json" },
      },
    )
  }
  try {
    await a.createReport("7", 88, "PERSONAL_INFORMATION", "", () => true)
    assert(calls.at(-1).url.endsWith("/posts/7/comments/88/reports"))
    assert.deepEqual(JSON.parse(calls.at(-1).options.body), {
      reasonType: "PERSONAL_INFORMATION",
    })
    assert.equal(calls.at(-1).options.credentials, "include")
    const n = calls.length
    assert.equal(
      await a.createReport("7", undefined, "OTHER", "내용", () => false),
      null,
    )
    assert.equal(calls.length, n)
    await assert.rejects(() =>
      a.createReport("7", undefined, "OTHER", " ", () => true),
    )
    assert.equal(calls.length, n)
  } finally {
    globalThis.fetch = original
    if (previousBase === undefined) delete process.env.NEXT_PUBLIC_API_BASE_URL
    else process.env.NEXT_PUBLIC_API_BASE_URL = previousBase
  }
})
