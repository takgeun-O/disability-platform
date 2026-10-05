Refine the **existing Community Post Detail desktop low-fidelity screen**.

This is a **targeted second-pass refinement**, not a redesign.

The current Post Detail structure is already successful and should be preserved.

This iteration should improve only the following:

1. Add the official Breadcrumb navigation.
2. Replace contextual `더보기` text buttons with proper contextual More Menu triggers.
3. Clarify Comment / Reply hierarchy.
4. Preserve everything that is already working well.

All user-facing UI text must remain in **Korean**.

---

# 1. Preserve the Existing Prototype

Do NOT redesign:

* Home
* Community Home
* Post List
* Post Detail from scratch
* shared Header
* shared Global Navigation
* shared Footer

Preserve the current Post Detail structure:

**Shared Header
→ Global Navigation
→ Post Header
→ Post Body
→ Post Actions
→ Comment Section
→ Footer**

Keep the existing:

* Post Type / Topic hierarchy
* title
* author/date metadata
* readable body width
* recommendation
* save
* share
* Comment Form
* Comment List
* one-level Reply structure

Modify only the requested areas.

---

# 2. Add Breadcrumb Navigation

Add a Breadcrumb above the Post classification metadata.

Use the official structure:

**커뮤니티 > 전체 게시글**

Navigation behavior:

* `커뮤니티` → existing Community Home
* `전체 게시글` → existing Post List

The Breadcrumb represents **IA hierarchy**, not browser history.

Do not make it dynamically change depending on whether the user entered from:

* Search
* Notification
* Home
* saved posts
* external URL

The Post Detail always belongs structurally to:

**커뮤니티 > 전체 게시글**

Do not include the current Post Title in the Breadcrumb because the Post Title already serves as the H1.

---

# 3. Breadcrumb Information Hierarchy

The top of the Post Detail should conceptually follow this order:

**커뮤니티 > 전체 게시글**

**일반 · 자유·일상**

# 오늘 아이 첫 보청기 착용했어요. 많이 낯설어하더라고요.

작성자 · 작성일

Do not combine Breadcrumb with:

* Post Type
* Topic
* Question Status

These are different concepts.

Breadcrumb = navigation hierarchy.

Post Type / Topic = content classification.

---

# 4. Breadcrumb Visual Treatment

Keep the Breadcrumb visually lightweight and secondary.

It should not compete with the Post Title.

Prefer:

* restrained typography
* simple text links
* subtle separator
* comfortable spacing

Do not turn Breadcrumb items into buttons, cards, or pills.

Do not add a large standalone `뒤로가기` button next to the Breadcrumb.

Browser Back remains separate from Breadcrumb navigation.

---

# 5. Breadcrumb Accessibility

Structure the Breadcrumb so it can later support:

* navigation landmark semantics
* meaningful accessible name
* keyboard-accessible links
* visible focus states
* non-color-only link distinction
* separators that are not unnecessarily repeated by screen readers

Do not add verbose accessibility text to the visible UI.

---

# 6. Preserve Browser Back Behavior

Do not replace Browser Back with the Breadcrumb.

The conceptual behavior remains:

**Post List
→ Post Detail
→ Browser Back
→ restore previous Post List context**

Preserve conceptually:

* search keyword
* Post Type
* Topic
* Sort
* Page
* scroll position where practical

Breadcrumb `전체 게시글` is a direct IA navigation link, not a history-based Back command.

---

# 7. Replace Contextual `더보기`

The current Post Detail uses visible `더보기` text buttons for contextual object actions.

These should no longer use the same `더보기` wording used for additional-content navigation.

Replace contextual `더보기` text buttons with a compact More Menu trigger:

**⋯**

Apply this to:

* Post contextual actions
* Comment contextual actions
* Reply contextual actions

Do NOT change valid Additional Content labels elsewhere in the product.

For example, labels such as:

* 게시글 더보기
* 커뮤니티 더보기
* 지원정보 더보기

may remain when they actually mean showing or navigating to more content.

---

# 8. More Menu Accessible Names

Although the visible Low-Fidelity trigger may be:

**⋯**

the interaction must conceptually have meaningful accessible names.

Use:

### Post

**게시글 메뉴**

### Comment

**댓글 메뉴**

### Reply

**답글 메뉴**

Do not rely on the symbol `⋯` alone to communicate purpose.

Do not display these accessible names as large visible labels unless needed for Low-Fidelity annotation.

---

# 9. Post More Menu — Non-Author

For a logged-in viewer who is not the author:

**⋯ 게시글 메뉴**

contains:

* 신고

Do not show:

* 수정
* 삭제

as disabled options.

Only show actions the current user is allowed to perform.

---

# 10. Post More Menu — Author Variant

For the Post author:

**⋯ 게시글 메뉴**

contains:

* 수정
* 삭제

Do not show `신고` for the user's own Post.

Delete continues to use the existing Confirmation Dialog policy.

Do not create the author variant as a separate full screen unless necessary for interaction annotation.

---

# 11. Comment More Menu

For the Comment author:

**⋯ 댓글 메뉴**

contains:

* 수정
* 삭제

For another user's Comment:

**⋯ 댓글 메뉴**

contains:

* 신고

The existing visible `답글` Action may remain separate from the More Menu.

Do not move every Comment action into the More Menu if it harms discoverability.

---

# 12. Reply More Menu

For the Reply author:

**⋯ 답글 메뉴**

contains:

* 수정
* 삭제

For another user's Reply:

**⋯ 답글 메뉴**

contains:

* 신고

Do not create additional Reply-specific functionality.

---

# 13. Do Not Create Reaction Bars

Contextual menus must remain secondary controls.

Do not redesign Post, Comment, or Reply actions into:

* social-media reaction bars
* icon toolbars
* persistent action strips
* floating menus

The current restrained information-oriented structure should remain.

---

# 14. Refine Comment / Reply Hierarchy

The current Reply structure should remain **maximum one visual level deep**.

Conceptually:

Comment
└── Reply

If someone replies to an existing Reply, do NOT create a deeper nested level.

Additional replies remain at the same Reply level.

---

# 15. Do Not Rely on Indentation Alone

Improve the visual relationship between Comment and Reply.

Keep the current indentation, but add at least one lightweight secondary hierarchy cue.

Possible low-fidelity cues:

* subtle vertical guide
* lightweight divider
* grouped spacing
* parent/reply grouping
* small textual `답글` relationship cue

Choose a restrained solution appropriate for the current design.

Do NOT use:

* large colored backgrounds
* nested cards
* deep tree lines
* heavy visual containers

---

# 16. Preferred Reply Treatment

For this Low-Fidelity iteration, prefer a simple structure such as:

Comment

────────

 │ Reply

or another similarly lightweight relationship treatment.

The purpose is to make the parent/reply relationship easier to understand without making the Comment Section visually heavy.

Do not let indentation significantly reduce the readable width of Reply content.

---

# 17. Preserve Semantic Reading Order

The visual structure should support a logical reading order.

For each Comment / Reply:

1. 작성자
2. 작성일
3. 내용
4. Actions

Do not place contextual controls before the main content.

The Reply relationship should remain understandable to assistive technology in future implementation.

---

# 18. Deleted Parent Comment Structure

The hierarchy should remain capable of supporting:

**삭제된 댓글입니다.**

with normal Replies still visually grouped underneath.

Do not disconnect surviving Replies from their deleted parent structure.

Do not display backend soft-delete terminology.

---

# 19. Preserve Current Post Header

Do not redesign the existing Post Header.

Keep:

**Post Type · Primary Topic**

**Post Title**

작성자 · 작성일

The newly added Breadcrumb should appear above this structure.

Do not add:

* 조회 수
* 추천 수
* 댓글 수
* activity score
* author profile statistics

---

# 20. Preserve the Post Body

Keep the current readable article layout.

Do not change the body into:

* cards
* columns
* rich multimedia blocks
* sidebar content

Do not add unsupported Rich Text UI.

---

# 21. Preserve Post Actions

Keep the existing core Post Actions:

* 추천
* 저장
* 공유

Replace only the contextual text `더보기` with the new More Menu trigger.

Do not otherwise change their hierarchy.

Do not add counts.

Do not rename:

* 추천 → 좋아요
* 저장 → 북마크

The current user-facing labels remain:

* 추천
* 저장
* 공유

---

# 22. Preserve Comment Form

Keep:

**댓글 작성**

Textarea

**등록**

The existing disabled state when the Textarea is empty may remain.

Do not add:

* Rich Text toolbar
* attachments
* emoji picker
* media upload
* formatting controls

---

# 23. Preserve Comment Content Density

Do not increase Comment metadata unnecessarily.

Keep primarily:

* 작성자
* 작성일
* 내용
* 답글
* contextual More Menu

Do not add:

* Comment likes
* recommendation count
* user score
* avatar-heavy profile UI
* badges
* ranking indicators

---

# 24. Preserve Question Status Contract

Do not reintroduce legacy question-state labels.

Official MVP labels remain:

* 답변 대기
* 답변 있음
* 해결됨

Do NOT use:

* 답변 완료
* CLOSED
* 채택됨
* 베스트 답변

The primary Post Detail screen may remain the current GENERAL post.

Do not redesign it into a QUESTION post for this refinement.

---

# 25. Accessibility Refinement

Preserve the current accessibility-oriented structure.

In addition:

### Breadcrumb

* keyboard accessible
* visible focus-state potential
* meaningful navigation structure

### More Menu

* meaningful accessible name
* keyboard operable
* focus returns appropriately after menu/dialog close

### Reply hierarchy

* not communicated through color or indentation alone
* logical reading order
* clear action labels

Do not make icon-only controls excessively small.

---

# 26. Desktop Scope

This remains a **Desktop Low-Fidelity refinement**.

Do not create Tablet or Mobile screens.

Do not finalize:

* exact line thickness
* exact hierarchy-guide color
* exact Breadcrumb typography
* exact spacing tokens
* final More icon design

Use neutral Low-Fidelity representations.

---

# 27. Do Not Add New Features

Do NOT add:

* standalone Back button
* Previous Post / Next Post
* related posts
* popular posts
* recommendations
* Sidebar
* AI summary
* AI recommendations
* ads
* Board UI
* Category
* Tag
* Comment likes
* accepted answer
* nested Reply deeper than one level
* author profile card
* additional contextual actions

This is a refinement of existing behavior only.

---

# 28. Preserve Prototype Navigation

Keep all existing prototype routes and interactions.

Preserve:

Home
→ Community Home
→ Post List
→ Post Detail

Add or confirm:

### Breadcrumb

`커뮤니티` → Community Home

`전체 게시글` → Post List

### Post Detail

Logo → Home

GNB `홈` → Home

GNB `커뮤니티` → Community Home

Browser Back → actual previous browsing context

Do not modify the existing Home, Community Home, or Post List structure.

---

# Final Goal

Refine the existing Post Detail without redesigning it.

The final screen should improve three things:

## 1. Navigation clarity

Add:

**커뮤니티 > 전체 게시글**

as a lightweight IA-based Breadcrumb.

## 2. Contextual action clarity

Replace contextual text:

**더보기**

with:

**⋯**

using meaningful accessible names such as:

* 게시글 메뉴
* 댓글 메뉴
* 답글 메뉴

## 3. Comment / Reply hierarchy

Keep one-level Replies but make the relationship clearer using:

**indentation + one additional lightweight hierarchy cue**

Do not change the successful reading structure, Post Actions, Comment Form, or overall Low-Fidelity visual language.

Modify only the existing Post Detail screen in this iteration.
