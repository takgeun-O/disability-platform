Refine the **existing Community Home** with a small, targeted second-pass improvement.

This is **NOT a redesign**.

The current Community Home structure is already good and should be preserved. Make only the specific refinements described below.

## Critical Prototype Preservation Requirements

Preserve the existing prototype architecture.

Do NOT:

* redesign or replace the existing Home
* redesign the Community Home from scratch
* remove existing routes
* rebuild the shared Header, Global Navigation, or Footer
* change the overall visual language
* create additional full screens
* introduce new features

Keep all existing working navigation between Home and Community Home intact.

All user-facing UI text must remain in Korean.

---

## 1. Preserve the Current Community Home Structure

Keep the current overall structure:

**Shared Header
→ Global Navigation
→ Community Page Header + 글쓰기
→ Post Type Discovery
→ Major Topic Discovery
→ 최근 게시글
→ Footer**

Do not change this section order.

Preserve the current strengths:

* simple Community page header
* clear `글쓰기` primary CTA
* lightweight Post Type discovery
* lightweight Major Topic discovery
* list-based Recent Posts
* restrained metadata
* minimal use of cards
* simple dividers
* clear post-title links
* shared visual language with the existing Home
* current Home ↔ Community navigation

The goal is refinement, not structural experimentation.

---

## 2. Remove the Duplicate “전체 게시글 보기” Action

The current Recent Posts area contains two entry points for viewing all posts:

* `전체 게시글 보기 →` near the Recent Posts section heading
* another larger `전체 게시글 보기` button below the post list

This is unnecessary duplication.

Keep only the lightweight section-level navigation link:

**최근 게시글　　　　　　　　　전체 게시글 보기 →**

Remove the larger duplicate `전체 게시글 보기` button below the list.

This should strengthen the CTA hierarchy:

**Primary action for the Community Home:** `글쓰기`

**Secondary navigation:** `전체 게시글 보기 →`

Do not give `전체 게시글 보기` the same visual weight as `글쓰기`.

---

## 3. Clarify the Difference Between “글 유형” and “주요 주제”

The current classification structure is correct and should remain:

### 글 유형

* 일반
* 질문·답변
* 경험·후기

### 주요 주제

* 자유·일상
* 보청기
* 인공와우
* 치료·재활
* 의사소통
* 취업·직장
* 복지·생활

However, the conceptual difference between these two groups may not be immediately obvious to users.

Add a short, subtle supporting description under each section heading.

For example:

### 글 유형

**어떤 형태의 글을 찾고 있나요?**

### 주요 주제

**관심 있는 주제로 둘러보세요.**

These descriptions should remain visually secondary.

Do not turn them into large explanatory blocks.

The intended distinction is:

**글 유형 = what kind of post it is**

**주요 주제 = what the post is about**

Communicate this distinction naturally through the Korean interface without adding unnecessary complexity.

---

## 4. Preserve the Lightweight Classification UI

Do not redesign Post Types or Major Topics into large cards.

Keep them compact and easy to scan.

For the current MVP, the topic controls may remain as lightweight chips, pills, links, or similarly restrained controls.

However, do not treat this exact visual treatment as a permanent taxonomy design.

The architecture must remain extensible because additional disability-related topics may be introduced in later phases.

Avoid:

* multi-level category trees
* large category cards
* oversized topic icons
* excessive badges
* horizontally endless tab systems
* complex filter panels

This screen is for discovery, not advanced filtering.

---

## 5. Preserve the Recent Posts Design

The current Recent Posts list is working well.

Keep the existing list-based approach.

Do not convert posts into large individual cards.

Each post should continue to prioritize:

1. Post Type / Major Topic
2. Post Title
3. Restrained metadata

Possible metadata may include:

* 작성자
* 작성일
* 댓글 수

Do not add additional metadata simply to make the list look richer.

Do not add:

* 조회 수
* 추천 수
* reaction controls
* bookmark controls
* report controls
* share controls
* images
* excessive tags

Post titles should remain clearly identifiable as links to the future Post Detail screen.

Do not make an invisible whole-row or whole-card click target the only way to open a post.

---

## 6. Preserve the Community Page Header

Keep the current structure:

**커뮤니티　　　　　　　　　　　　글쓰기**

with the concise supporting description below the page title.

`글쓰기` should remain the single strongest action on this screen.

Do not make it oversized.

Do not add additional competing primary buttons.

Do not add Community Search next to the writing CTA.

Detailed Community Search belongs to the future Post List screen.

---

## 7. Preserve Shared Service Consistency

The Community Home must continue to feel like part of the same product as the existing Home.

Keep the shared:

* Header
* Logo treatment
* Global Navigation
* content width
* alignment
* typography hierarchy
* spacing philosophy
* link treatment
* button treatment
* dividers
* metadata style
* Footer

Keep `커뮤니티` clearly indicated as the current Global Navigation location.

Keep:

* Logo → existing Home
* `홈` → existing Home
* Home `커뮤니티` entries → Community Home

Do not modify the existing Home while making this refinement.

---

## 8. Improve Hierarchy Through Restraint

If minor visual adjustments are needed after adding the supporting descriptions, use only:

* spacing
* alignment
* typography hierarchy
* subtle dividers
* content grouping

Do not introduce visual decoration to solve hierarchy problems.

Avoid adding:

* large rounded containers
* shadows
* gradients
* background illustrations
* decorative color blocks
* oversized icons
* excessive pills or badges

The page should remain calm, readable, and information-focused.

---

## 9. Preserve Accessibility

Maintain the current accessibility-oriented structure.

Ensure:

* `커뮤니티` remains the clear H1
* section headings follow a logical hierarchy
* Post Type and Topic controls have meaningful text labels
* post titles remain explicit links
* `글쓰기` remains a semantic action
* `전체 게시글 보기` remains recognizable as navigation
* keyboard navigation order follows the visual content order
* visible focus states remain possible
* important information does not rely on color alone
* metadata remains readable
* text enlargement does not break the layout

The newly added supporting descriptions should have sufficient readability and should not become extremely small or excessively low-contrast helper text.

---

## 10. Maintain Low-Fidelity Scope

This is still a **Desktop Low-Fidelity structural prototype**.

Do not introduce or finalize:

* brand colors
* final visual branding
* final logo treatment
* exact typography system
* exact spacing tokens
* exact grid values
* final iconography
* shadows
* gradients
* illustrations
* animations
* high-fidelity styling

Do not try to make the interface more visually exciting in this iteration.

The current neutral appearance is intentional.

---

## 11. Do Not Add New Features

Do not add:

* Community-specific search field
* detailed filters
* sorting controls
* pagination
* Load More
* popular posts
* trending posts
* recommended posts
* personalized feeds
* user rankings
* AI functionality
* AI summaries
* advertisements
* notification widgets
* sidebars
* follower systems
* Post Detail actions
* comment interfaces
* Post Create form
* additional pages

These are outside the scope of this refinement.

---

## Final Goal

Make a **small refinement to the existing Community Home**, not a redesign.

The main changes are:

**1. Remove the duplicate bottom `전체 게시글 보기` button.**

**2. Add concise supporting descriptions that clarify the difference between `글 유형` and `주요 주제`.**

**3. Preserve everything that is already working well.**

After the refinement, the screen should make this hierarchy immediately understandable:

**커뮤니티**
→ understand the community
→ choose `글쓰기` if the user wants to participate
→ discover content by Post Type or Major Topic
→ scan Recent Posts
→ use `전체 게시글 보기 →` to enter the full Post List

Preserve the existing Home and all current working navigation.

Modify only the existing Community Home in this iteration.
