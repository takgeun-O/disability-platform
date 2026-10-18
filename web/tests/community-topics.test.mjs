import assert from "node:assert/strict"
import { test } from "node:test"
import { loadTypeScript } from "./helpers/load-typescript.mjs"
const model = await loadTypeScript(
  "../src/lib/community-posts.ts",
  import.meta.url,
)
const spaces = await loadTypeScript(
  "../src/lib/community-topics.ts",
  import.meta.url,
)
test("모든 기존 주제는 고유한 slug·설명·이미지·이용 안내를 명시적으로 가진다", () => {
  assert.deepEqual(
    spaces.COMMUNITY_TOPICS.map((s) => s.topic).sort(),
    Object.keys(model.POST_TOPICS).sort(),
  )
  assert.equal(new Set(spaces.COMMUNITY_TOPICS.map((s) => s.slug)).size, 7)
  for (const space of spaces.COMMUNITY_TOPICS) {
    assert.equal(spaces.topicBySlug(space.slug).topic, space.topic)
    assert.equal(spaces.topicByPath(spaces.topicPath(space)).topic, space.topic)
    assert.ok(
      space.description && space.guidance && space.image.endsWith(".svg"),
    )
  }
})
test("주제 경로가 충돌 쿼리보다 우선하며 유형·페이지는 보존한다", () => {
  const path = "/community/topics/cochlear-implant"
  const query = model.listQuery(
    new URLSearchParams("topic=HEARING_AID&type=QUESTION&page=3"),
    path,
  )
  assert.deepEqual(query, {
    type: "QUESTION",
    topic: "COCHLEAR_IMPLANT",
    page: 3,
  })
  assert.equal(model.listHref(query, path), path + "?type=QUESTION")
  assert.equal(
    model.safeListReturn(path + "?topic=HEARING_AID&type=QUESTION&page=3"),
    path + "?type=QUESTION",
  )
})
test("주제 복귀 경로는 정확한 허용 목록만 인정한다", () => {
  for (const value of [
    "/community/topics/unknown",
    "/community/topics/hearing-aid/extra",
    "/community/topics/__proto__",
    "/community/topics/%68earing-aid",
    "//evil.test/community/topics/hearing-aid",
    "/community/posts/create",
    "/community/posts/1",
    "/community-evil",
    "/community/topics/hearing-aid\\evil",
  ])
    assert.equal(model.safeListReturn(value), "/community/posts")
  assert.equal(
    model.safeListReturn(null, "/community/topics/hearing-aid"),
    "/community/topics/hearing-aid",
  )
  assert.equal(
    model.safeListReturn(null, "https://evil.test"),
    "/community/posts",
  )
})
test("기존 한글 주제 쿼리와 페이지 방어는 그대로 유지한다", () => {
  assert.equal(
    model.safeListReturn("/community?topic=보청기&type=일반&page=2"),
    "/community?type=GENERAL&topic=HEARING_AID",
  )
  for (const page of ["-1", "abc", "999999999999"])
    assert.equal(
      model.listQuery(new URLSearchParams({ page }), "/community/topics/daily")
        .page,
      0,
    )
})
