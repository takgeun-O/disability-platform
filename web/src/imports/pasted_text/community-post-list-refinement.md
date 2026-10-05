Refine the **existing Community Post List desktop low-fidelity screen** rather than redesigning it from scratch.

The current Post List is structurally successful and should be preserved.

This is a **small second-pass refinement** focused on:

* clarifying the distinction between Community Home and Post List,
* slightly improving vertical density,
* removing redundant notice labeling,
* preserving the current information hierarchy,
* and maintaining consistency with the existing Home and Community Home.

Do not introduce new features or a new visual direction.

All user-facing UI text must remain in Korean.

---

# 1. Preserve the Existing Screen

Keep the current Post List structure and visual language.

Preserve:

* existing shared Header
* existing Global Navigation
* current content width and alignment
* Page Header structure
* `글쓰기` CTA
* dedicated Community Search
* Post Type filter
* Primary Topic filter
* Community-wide notice / pinned-post treatment
* wide single-column Post List
* current restrained metadata structure
* page-number Pagination
* shared Footer
* neutral low-fidelity styling

Do not rebuild these components.

Do not modify the existing Home or Community Home.

This iteration should modify only the existing Post List screen.

---

# 2. Clarify the Page Identity

The current Post List should be more clearly distinguishable from the Community Home.

For the default Post List state, change the primary H1 from:

**커뮤니티**

to:

**전체 게시글**

The Community section context is already communicated by the active Global Navigation state and the previous navigation flow.

The default Page Header should therefore communicate the immediate purpose of this screen: browsing posts.

Keep:

**글쓰기**

as the primary Page Header action.

A short supporting description may remain if the current design already uses one, but do not add a large introductory Hero or marketing message.

The Page Header should remain compact and functional.

---

# 3. Support Filtered Context Without Creating New Screens

The Post List is one reusable screen with different states.

Do not create separate pages for each Post Type or Topic.

For the default state:

**전체 게시글**

For a Topic-filtered state, the architecture may support the selected Topic becoming the page title where appropriate.

Example:

**인공와우**

However, do not create these additional variants as separate screens in this iteration unless they already exist as prototype states.

Post Type selections such as:

* 일반
* 질문·답변
* 경험·후기

should normally remain represented through the Post Type filter rather than replacing the H1.

---

# 4. Slightly Reduce Vertical Density Above the List

The current Search and Filter architecture is correct.

Do not remove or combine these controls.

However, slightly reduce unnecessary vertical spacing between:

* Page Header
* Community Search
* Post Type filter
* Primary Topic filter
* list controls
* Post List

The goal is to allow users to reach actual posts slightly sooner while preserving comfortable readability and accessibility.

Do not solve this by:

* shrinking text excessively,
* reducing touch/click targets,
* hiding filters,
* collapsing important controls,
* or creating a dense dashboard-like toolbar.

Use modest spacing refinement only.

The hierarchy should remain:

**Page Header
→ Community Search
→ Post Type / Topic Filters
→ List Context / Sort
→ Notices / Posts**

---

# 5. Preserve the Community Search

Keep the dedicated Community Search.

It must remain clearly distinguishable from the Global Search in the shared Header.

Do not move Community Search into the Header.

Do not replace it with an icon-only search action.

Keep a visible text label explaining its Community-specific purpose.

The search field may become slightly more compact vertically if needed, but it should remain a prominent functional control.

---

# 6. Preserve the Filter Model

Keep exactly these Post Type options:

**글 유형**

* 전체
* 일반
* 질문·답변
* 경험·후기

Keep exactly these Primary Topic options:

**주요 주제**

* 전체
* 자유·일상
* 보청기
* 인공와우
* 치료·재활
* 의사소통
* 취업·직장
* 복지·생활

Do not add:

* Board
* Category
* Tag
* Disability Type

Do not transform the filters into large cards.

Keep the current lightweight inline desktop treatment.

The selected state must remain understandable without relying on color alone.

---

# 7. Avoid Redundant Active-Filter UI

The existing Post Type and Topic controls already communicate the current selection.

Do not add an additional large Active Conditions section unless necessary.

Do not duplicate every selected condition into extra chips or badges.

Keep the interface restrained.

---

# 8. Keep the Sort Treatment Minimal

Preserve the confirmed default:

**최신순**

Do not introduce unconfirmed sorting options.

Do not add:

* 인기순
* 추천순
* 댓글순
* 조회순
* 좋아요순

The Sort treatment should remain visually secondary to Search and Filters.

---

# 9. Remove Redundant Notice Labeling

Refine Community-wide notice and pinned-post presentation.

Avoid displaying the notice state twice.

For example, do NOT produce:

**[공지] [공지] 커뮤니티 이용 규칙 안내**

or:

**공지 · [공지] 커뮤니티 이용 규칙 안내**

Instead, use a dedicated textual state label plus a clean post title.

Preferred conceptual structure:

**공지**

**커뮤니티 이용 규칙 안내**

Likewise, if a pinned state is shown, avoid duplicating `고정` inside both the state label and title.

The title itself should remain clean content text.

Status belongs to UI metadata, not inside the post title.

---

# 10. Keep Notices Visually Restrained

Community-wide notices should remain above regular posts when appropriate.

Do not redesign them as:

* banners
* promotional cards
* colored alerts
* Hero sections
* oversized containers

They should remain part of the Post List hierarchy.

Use:

* textual state label
* typography
* subtle divider
* restrained emphasis

to distinguish them from ordinary posts.

Do not rely on color alone.

---

# 11. Preserve the Post Item Information Hierarchy

The current Post Item structure is good.

Keep the general hierarchy:

**글 유형 · 대표 주제 · conditional state**

**게시글 제목**

**작성자 · 작성일 · 댓글 수**

Example:

**질문·답변 · 인공와우 · 답변 완료**

**인공와우 수술 후 소리 적응에 얼마나 걸리셨나요?**

작성자 · 작성일 · 댓글 수

Do not add unnecessary metadata.

Do not add by default:

* 조회 수
* 추천 수
* 좋아요 수
* Tag
* Board
* thumbnails
* attachment previews
* reaction buttons
* bookmark buttons
* report buttons

The Post List should remain optimized for scanning.

---

# 12. Treat Question Status as Provisional UI Copy

Question posts may continue to show a lightweight textual question status where the current design already uses it.

Examples currently used may include:

* 답변 대기
* 답변 완료

Keep this treatment visually secondary.

Do not expand it into a complex question workflow.

Do not add:

* 해결 버튼
* 답변 채택 action
* progress indicators
* answer controls

The exact final Korean terminology for question state may be finalized later.

For this low-fidelity iteration, preserve the current simple textual status pattern without making it a dominant visual element.

---

# 13. Preserve Explicit Post Title Links

Keep each post title as the primary navigation target.

The title should remain clearly recognizable as interactive.

Do not make interaction dependent only on clicking the entire row.

Do not add detail-screen actions to the list.

---

# 14. Preserve Desktop Pagination

Keep the current page-number Pagination.

Support conceptually:

* previous
* page numbers
* current page
* next

Do not replace it with:

* Infinite Scroll
* Load More
* auto-loading content

Pagination should remain visually secondary and appear naturally after the list.

---

# 15. Preserve Accessibility

Do not sacrifice accessibility to make the screen more compact.

Maintain:

* one clear H1
* visible Search label
* visible Filter group labels
* readable Post Titles
* readable Metadata
* explicit textual states
* selected filter indication beyond color
* keyboard-accessible controls
* visible focus-state potential
* clear Pagination current state
* logical focus order
* sufficient control spacing
* text enlargement / reflow compatibility

Do not reduce important text to very small or low-contrast gray typography.

Do not rely on icons alone.

---

# 16. Preserve Low-Fidelity Scope

This remains a **Desktop Low-Fidelity structural prototype**.

Do not introduce:

* brand colors
* final typography
* polished iconography
* gradients
* illustrations
* decorative imagery
* shadows
* animation
* marketing graphics
* elaborate card styling

Do not attempt to make the screen visually “finished.”

The goal is to improve structural clarity before High-Fidelity design.

---

# 17. Do Not Add New Features

Do not add:

* Board Navigation
* Community Category
* Tag filtering
* Disability Type filtering
* Sidebar
* Trending
* Ranking
* recommended posts
* personalized feed
* AI recommendations
* AI summaries
* advertisements
* promotional sections
* comment previews
* comment input
* reactions
* bookmarks
* report actions
* Post Create form
* Infinite Scroll
* additional future-feature placeholders

Do not infer new features from common community-site conventions.

---

# 18. Preserve Existing Prototype Navigation

Keep the existing prototype relationships intact.

### Home

Keep unchanged.

### Community Home

Keep unchanged.

Existing Community Home entry points should continue to lead to the Post List with the appropriate state.

### Post List

Keep:

* Logo → existing Home
* GNB `홈` → existing Home
* GNB `커뮤니티` → existing Community Home
* Post Title → future Post Detail
* `글쓰기` → future authentication / Post Create flow

Do not create Post Detail, Login, or Post Create screens in this iteration.

---

# Final Goal

This is a **refinement, not a redesign**.

Preserve the successful current Post List architecture while improving four things:

1. **Clarify page identity**

   * Default H1: `전체 게시글`

2. **Improve vertical rhythm**

   * Slightly reduce unnecessary spacing above the actual post list

3. **Remove redundant notice labeling**

   * Status belongs in UI metadata, not repeated inside the title

4. **Preserve the strong existing information hierarchy**

   * Search
   * Post Type
   * Topic
   * notices
   * scan-friendly posts
   * Pagination

The final result should feel like a natural continuation of the existing Home and Community Home and remain a calm, accessible, information-oriented Korean Community Post List.

Modify only the existing Post List screen in this iteration.
