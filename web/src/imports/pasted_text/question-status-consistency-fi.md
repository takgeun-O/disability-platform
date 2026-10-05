Make one focused consistency fix to Question Status across the existing Community Post List and Post Detail screens.

Do NOT redesign either screen.

The current Post List, Post Detail, Post Create, navigation, comments, actions, Breadcrumb, layout, and prototype flows are already correct and must be preserved.

All user-facing UI text must remain in Korean.

---

# 1. Fix Missing Question Status on Existing Post Detail

There is currently an inconsistency in the prototype:

A Question Post can display a Question Status such as:

`답변 대기`

in the Post List, but when the user opens that same post, the Question Status disappears from Post Detail.

This is incorrect.

For every post whose Post Type is:

`질문·답변`

the Question Status shown in Post List must also be shown in Post Detail.

The status must represent the SAME underlying post state.

Example:

Post List:

`질문·답변 · 보청기 · 답변 대기`

→ open that post

Post Detail:

`질문·답변 · 보청기 · 답변 대기`

Do not drop the Question Status during navigation from Post List to Post Detail.

---

# 2. Apply the Same Rule to All Question Posts

Use the official Question Status contract consistently:

OPEN
→ `답변 대기`

ANSWERED
→ `답변 있음`

RESOLVED
→ `해결됨`

Therefore:

Post List `답변 대기`
→ Post Detail `답변 대기`

Post List `답변 있음`
→ Post Detail `답변 있음`

Post List `해결됨`
→ Post Detail `해결됨`

The status must not change simply because the user navigates from the list to the detail screen.

Do not use:

- 답변 완료
- CLOSED
- 채택 완료
- 답변 채택

---

# 3. Preserve Question Status for Newly Created Posts

The newly created Question Post already displays:

`답변 대기`

on Post Detail after successful submission.

Preserve this behavior.

For a newly created `질문·답변` post with no valid comments/answers:

Post Create
→ 등록
→ Newly Created Post Detail
→ `답변 대기`

Do not remove this behavior while fixing existing posts.

---

# 4. Make Question Status Styling Consistent Across List and Detail

The Question Status Badge/Label on Post Detail currently does not visually match the corresponding Question Status Badge/Label used in Post List.

Fix this inconsistency.

The same Question Status must use the same visual treatment across:

- Post List
- Post Detail reached from Post List
- Newly Created Post Detail

Reuse the existing Question Status visual language already established in Post List.

Do not create a separate Detail-only status style.

For example:

`답변 대기`

must use the same:

- semantic color treatment,
- border treatment,
- text treatment,
- general badge/label character

wherever it appears.

Likewise, preserve consistent visual treatment for:

- `답변 있음`
- `해결됨`

The component may adapt slightly to its surrounding layout, but its semantic appearance must remain recognizably the same.

---

# 5. Treat Question Status as a Shared UI Pattern

Conceptually treat Question Status as one reusable UI pattern rather than separately styled text on each screen.

The same state should not look like two unrelated components.

Use:

Question Status
- 답변 대기
- 답변 있음
- 해결됨

consistently throughout the Community UI.

Do not create different colors or badge meanings for the same state depending on the screen.

---

# 6. Preserve the Existing Post Detail Information Hierarchy

Keep the existing Post Detail hierarchy:

Breadcrumb

→ Post Type / Topic / Question Status

→ Post Title

→ Author / Date

→ Body

For example:

`질문·답변 · 보청기 · 답변 대기`

or:

`질문·답변 · 의사소통 · 답변 있음`

or:

`질문·답변 · 치료·재활 · 해결됨`

Question Status should remain compact and subordinate to the Post Title.

Do not create a separate Question Status section.

Do not place a large status banner above the post.

---

# 7. Non-Question Posts Must Remain Unchanged

Question Status applies only to:

`질문·답변`

Do not show Question Status for:

- 일반
- 경험·후기

Keep their existing metadata structure unchanged.

Examples:

`일반 · 자유·일상`

`경험·후기 · 인공와우`

Do not add an empty status placeholder to these posts.

---

# 8. Preserve Status Accessibility

Question Status must always contain visible textual information.

Use:

`답변 대기`
`답변 있음`
`해결됨`

Do not communicate Question Status through color alone.

Color and border treatment may reinforce the state, but the textual label is required.

Maintain sufficient visual distinction and readable contrast.

Do not make the badge excessively subtle simply because this is a Low-Fidelity prototype.

---

# 9. Do Not Modify Other Post Detail Elements

Do not change:

- Header
- Global Navigation
- Breadcrumb
- Post Type
- Topic
- Post Title
- Author
- Date
- Body
- Attached images
- 추천
- 저장
- 공유
- Contextual Action Menu
- Comment composer
- Comments
- Replies
- Footer

Do not change the existing Post Create → Post Detail flow.

Do not change the existing Post List layout.

Only fix Question Status persistence and visual consistency.

---

# 10. Preserve Existing Navigation

Do not create new screens.

Do not duplicate Post Detail screens unnecessarily.

When an existing Question Post is selected from Post List, its existing Question Status must be carried into and represented correctly on Post Detail.

When a newly created Question Post is submitted, its initial `답변 대기` status must continue to appear correctly.

---

# 11. Low-Fidelity Scope

This remains a Low-Fidelity prototype.

Do not introduce:

- final brand colors
- new decorative status systems
- gradients
- shadows
- animations
- oversized badges
- new cards
- additional Question features

This change is specifically about state consistency.

---

# Final Goal

Fix Question Status consistency across the Community flow.

The intended behavior is:

Question Post in Post List
→ open Post Detail
→ same Question Status is visible

and:

Post Create for a Question Post
→ successful submission
→ Newly Created Post Detail
→ `답변 대기`

The same status must use the same semantic visual treatment across Post List and Post Detail.

Specifically fix the two current problems:

1. Existing Question Posts can show `답변 대기` in Post List but lose the status when opened in Post Detail.

2. Newly Created Post Detail shows `답변 대기`, but its Badge/Label styling does not match the established `답변 대기` styling in Post List.

Preserve everything else unchanged.