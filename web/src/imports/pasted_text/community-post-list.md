Extend the **existing desktop low-fidelity prototype** by creating a new **Post List** screen for the Community section.

This is the same Korean accessibility-focused community + information platform as the existing Home and Community Home.

## Critical Prototype Preservation Requirements

Preserve the existing prototype exactly where it already works.

Do NOT:

* redesign or replace the existing Home
* redesign or replace the existing Community Home
* rebuild the shared Header
* rebuild the shared Global Navigation
* rebuild the shared Footer
* create a new Community-specific site shell
* introduce a new visual language

Create the Post List as a **separate new screen/route** within the same prototype.

All user-facing UI text and realistic sample content must remain in **Korean**.

---

# 1. Screen Role

Create the Community **Post List** screen.

This is not the Community Home and not a social-media feed.

Its role is:

* browsing all posts or posts under a selected condition
* searching Community posts
* filtering by Post Type
* filtering by Primary Topic
* showing Community-wide notices/pinned posts
* browsing a paginated list
* entering Post Detail
* entering Post Create

The intended flow is:

**Community Home
→ 전체 게시글 / 글 유형 / 주제 선택
→ Post List
→ 검색 / Filter
→ Post Detail**

The Post List should feel like a purposeful information-browsing screen rather than an entertainment feed.

---

# 2. Official MVP Classification Model

Use only the following Community classification model.

## Post Type

Exactly one per post.

UI labels:

* 일반
* 질문·답변
* 경험·후기

For filtering, also provide:

* 전체

`전체` is only the UI state meaning no Post Type filter is applied.

Do not treat it as an actual Post Type.

## Primary Topic

Exactly one per post.

MVP topics:

* 자유·일상
* 보청기
* 인공와우
* 치료·재활
* 의사소통
* 취업·직장
* 복지·생활

For filtering, also provide:

* 전체

`전체` is only the UI state meaning no Topic filter is applied.

Do not add Board, Category, or Tag as Community filters.

---

# 3. Existing Navigation Connections

Preserve the current existing prototype.

## Existing Home

Keep unchanged.

Its Community entry points should continue to lead to Community Home.

## Existing Community Home

Keep unchanged.

Connect:

* `전체 게시글 보기` → Post List default state
* `일반` → Post List with `일반` selected
* `질문·답변` → Post List with `질문·답변` selected
* `경험·후기` → Post List with `경험·후기` selected
* each Topic → Post List with that Topic selected

Do not create separate screens for each filter state.

Use the same Post List screen with different state.

---

# 4. Shared Header

Reuse the existing shared Header.

Do not redesign it.

The Header search represents **Global Search** across the entire service.

Global Search may include:

* 커뮤니티
* 복지·지원정보
* 병원·전문기관
* 보조기기

Do not confuse this with the Community Search inside the Post List content area.

---

# 5. Global Navigation

Reuse the existing Global Navigation exactly as the shared service navigation.

Keep:

* 홈
* 커뮤니티
* 복지·지원정보
* 병원·전문기관
* 보조기기

Indicate that the user is currently within the **커뮤니티** section.

The current state must not rely on color alone.

The `커뮤니티` Global Navigation item should conceptually lead to Community Home, not directly to Post List.

Do not create a new local GNB or Board Navigation.

---

# 6. Page Header

Create a simple Post List page header.

Default state:

### H1

**커뮤니티**

### Primary CTA

**글쓰기**

Place `글쓰기` in the Page Header as the primary action.

Do not use a Floating Action Button.

Do not place the Post Create form inside this screen.

For a Topic-filtered entry, the selected Topic may be reflected in the H1 if the layout supports it naturally, for example:

**인공와우**

However, do not generate separate screens for each Topic.

Post Type should normally be represented through filter selection rather than changing the H1.

A descriptive paragraph is optional and should remain short if used.

Do not add Breadcrumbs in this first Low-Fidelity version.

---

# 7. Community Search

Add a dedicated **Community Search** control inside the main content area.

It must be visually and semantically distinguishable from the shared Header Global Search.

Community Search applies only to Community posts.

The search scope may include:

* 제목
* 본문
* 글 유형
* 대표 주제

Do not create a separate Community Search Results screen.

Searching should update the same Post List screen.

Support conceptually:

* keeping the entered search keyword visible
* clearing the keyword
* combining search with Post Type and Topic filters
* resetting to page 1 when the search condition changes

Use a clear visible Korean label explaining that this search is for Community posts.

Do not rely on placeholder text alone to communicate search scope.

The exact final placeholder is not a finalized design decision.

---

# 8. Post Type Filter

Provide a lightweight, clearly labeled filter group for:

**글 유형**

Options:

* 전체
* 일반
* 질문·답변
* 경험·후기

Only one may be selected at a time.

The selected state must be clear through more than color alone.

Users must be able to:

* see the currently selected Post Type
* change the Post Type
* return to `전체`

Do not represent Post Types as large cards.

Do not create separate pages for each Type.

---

# 9. Topic Filter

Provide a lightweight filter group for:

**주요 주제**

Options:

* 전체
* 자유·일상
* 보청기
* 인공와우
* 치료·재활
* 의사소통
* 취업·직장
* 복지·생활

Only one Primary Topic is applied at a time.

Users must be able to:

* see the currently selected Topic
* change the Topic
* return to `전체`

The selected state must not rely on color alone.

Do not represent Topics as Boards.

Do not create:

* Board Navigation
* category trees
* sidebar navigation
* independent Topic pages
* large Topic cards

A compact inline desktop filter pattern is preferred.

---

# 10. Filter Combination

Post Type and Topic may be applied together.

Examples:

**질문·답변 + 인공와우**

**경험·후기 + 보청기**

When search, Post Type, or Topic changes:

* reset conceptually to page 1
* update the same Post List
* preserve the current remaining conditions

Users must be able to clear individual filter groups through `전체`.

Do not add Tag filtering.

---

# 11. Active Conditions

For the default state, do not create an additional large Active Conditions bar.

The existing Search, Post Type, and Topic controls should already make the current state understandable.

If multiple non-default conditions are applied, a lightweight summary/reset action may be structurally supported.

Do not duplicate every selected filter as an extra chip or badge.

Avoid redundant UI.

---

# 12. Sort

The confirmed MVP default sort is:

**최신순**

Represent the current sort state.

Do not populate the interface with unconfirmed sort options.

Do NOT add:

* 인기순
* 추천순
* 댓글순
* 좋아요순
* 조회순

as established MVP choices.

If a control is shown, it should communicate that the current list is in `최신순`, without implying unconfirmed options.

Do not let the Sort control dominate the content.

---

# 13. Community-Wide Notice / Pinned Posts

The MVP supports **Community-wide notices and minimal pinned posts**.

Do not create Topic-specific notices.

Do not create Board notices.

Display Community-wide notices or pinned items above regular posts.

Use a restrained list-item variant rather than a large promotional banner.

Clearly identify them using text such as:

**공지**

or an equivalent explicit text label.

Do not distinguish them through color alone.

Keep notices visually connected to the Post List rather than creating a separate large dashboard section.

---

# 14. Post List Structure

Use a **wide single-column list**.

Do not use:

* card grid
* social-media feed cards
* masonry
* image-heavy previews
* profile-driven feed layout

Each Post Item should prioritize scanability.

Required information:

1. 글 유형
2. 대표 주제
3. 게시글 제목
4. 작성자
5. 작성일
6. 댓글 수

Recommended hierarchy:

**글 유형 · 주제**

**게시글 제목**

작성자 · 작성일 · 댓글 수

Example:

**질문·답변 · 인공와우**

**인공와우 수술 후 소리 적응에 얼마나 걸리셨나요?**

홍길동 · 2026.08.15 · 댓글 12

Use simple dividers and whitespace between items.

Do not turn every post into a large rounded card.

---

# 15. Conditional Post Metadata

Only show additional state information when needed.

Examples:

* 질문 상태 for `질문·답변` posts
* `공지` for notice posts
* pinned state when required

Do not display all available metadata.

Do NOT show by default:

* Tag
* Board
* Community Category
* 조회 수
* 좋아요 수
* 추천 수
* attachment previews
* unnecessary icons

Keep metadata restrained.

---

# 16. Post Title Interaction

Each Post Title must be a clearly recognizable Link.

Selecting the title should conceptually lead to the future Post Detail screen.

Do not rely solely on making the entire row or container clickable.

The whole row may support interaction if appropriate, but the title itself must remain an explicit semantic link.

Do not create the Post Detail screen in this iteration.

---

# 17. Do Not Add Detail Actions

Do not place the following actions directly inside Post List items:

* 좋아요
* 추천
* 저장
* 신고
* 공유
* 댓글 입력
* 질문 해결
* 수정
* 삭제

These belong to the future Post Detail screen.

The Post List is for discovery and navigation.

---

# 18. Pagination

Use **desktop page-number Pagination**.

Conceptual form:

**‹  1  2  3  4  5  ›**

Support:

* current page indication
* previous / next
* direct page selection
* preserving filters and search while changing page
* resetting to page 1 when filters/search change
* restoring page state when returning from Post Detail

Do not use Infinite Scroll.

Do not use Load More for this Desktop iteration.

Mobile may later use another pattern, but that is outside this screen.

---

# 19. List State Preservation

The architecture should support this conceptual flow:

Post List

Search = current keyword
Post Type = selected value
Topic = selected value
Sort = 최신순
Page = current page

→ Post Detail
→ Back
→ restore the previous Post List state

Preserve conceptually:

* search keyword
* Post Type
* Topic
* sort
* page
* scroll position where practical

Do not reset the user to the beginning of the Community experience after viewing a post.

---

# 20. Writing Flow

Keep `글쓰기` visible in the Page Header.

For a logged-out user:

**글쓰기
→ 로그인
→ Post Create**

After successful authentication, preserve the original intention.

If the user entered the Post List with a Post Type or Topic already selected, conceptually pass that context to Post Create as the default value.

Do not create Login or Post Create screens in this iteration.

Only prepare the navigation structure for them.

---

# 21. Default State for This Iteration

Design the primary screen in this default state:

* Post Type = 전체
* Topic = 전체
* Search keyword = none
* Sort = 최신순
* Page = 1
* regular posts exist
* Community-wide notice/pinned item may appear at the top

Do not mix Loading, Empty, Error, or No Result state into the Default screen.

---

# 22. Empty / No Result Architecture

Do not create all state screens in this iteration unless needed for structural support.

However, the Post List architecture must be able to support these later.

## Empty

Community has no posts at all.

Possible message:

**아직 작성된 게시글이 없습니다.**

Possible CTA:

**첫 게시글 작성**

## No Result

Posts exist, but the current search/filter combination has no match.

Support conceptually:

* keeping the current search/filter visible
* clearing the search keyword
* clearing the current filter
* resetting all conditions

Do not treat Empty and No Result as the same state.

---

# 23. Loading Architecture

For future Loading states:

* keep Header
* keep Global Navigation
* keep Page Header
* keep filters where possible
* update only the Post List result area

Post-item Skeletons may be used later if helpful.

Do not Skeleton the entire page by default.

Do not create a dedicated Loading screen in this iteration.

---

# 24. Error Architecture

If the Post List data fails:

* preserve shared Header and Navigation
* preserve Page Header
* show the error inside the Post List result area
* provide a clear `다시 시도` action

Do not replace the entire product with a full-page error unless the entire application shell is unavailable.

Do not show Error in the Default state.

---

# 25. Accessibility

Accessibility is a core structural requirement.

Ensure the screen supports:

* one clear H1
* logical section headings
* semantic list structure
* explicit Post Title links
* visible labels for Community Search
* visible labels for Post Type and Topic filters
* visible/current Pagination state
* keyboard-accessible controls
* logical focus order
* visible focus-state design potential
* selected filter state beyond color alone
* readable metadata
* textual notice/question-state labels
* text enlargement and reflow
* screen-reader understandable hierarchy

Do not use placeholder-only search labeling.

Do not reduce metadata to extremely small or very low-contrast text.

Do not use icon-only controls as primary actions.

---

# 26. Desktop Layout

This iteration is **Desktop-first**.

Prefer:

* same content width/alignment as existing Home and Community Home
* Page Header with H1 and `글쓰기`
* wide Community Search field
* compact inline Post Type filter
* compact inline Topic filter
* restrained Sort indicator/control
* wide single-column Post List
* page-number Pagination
* shared Footer

Do NOT add a Sidebar.

Do NOT add extra content simply to fill desktop space.

---

# 27. Future Responsive Behavior

Do not create full Tablet or Mobile screens yet.

However, ensure the Desktop structure can later adapt.

On smaller screens:

* Community Search remains available
* Post Type remains available
* Topic filtering remains available
* long Topic lists may move into a Filter button, Drawer, or Bottom Sheet
* Post metadata may wrap to multiple lines
* writing remains discoverable
* Pagination may later be simplified

Do not create a permanently horizontal scrolling topic bar as the only mobile solution.

---

# 28. Low-Fidelity Scope

This is still a **Low-Fidelity structural iteration**.

Do not finalize:

* brand colors
* final typography
* exact grid
* exact spacing tokens
* exact pixel dimensions
* border radius system
* shadows
* gradients
* illustrations
* final iconography
* animation
* exact breakpoints

Use the current restrained low-fidelity visual language of the existing prototype.

Focus on:

* structure
* information hierarchy
* search/filter relationships
* post scanability
* CTA hierarchy
* pagination
* navigation continuity
* accessibility

---

# 29. Explicit Exclusions

Do NOT add:

* Board UI
* Board Navigation
* Community Category filter
* Tag UI
* Tag filter
* Disability Type filter
* Community Space
* Sidebar
* Infinite Scroll
* social-media feed layout
* Trending
* Ranking
* personalized recommendations
* AI recommendations
* AI summaries
* advertisements
* promotional banners
* comment previews
* comment input
* reaction controls
* bookmark controls
* report controls
* Post Create form
* additional future-feature placeholders

Do not infer functionality simply because it is common on community websites.

---

# 30. Prototype Navigation Requirements

Extend the existing prototype rather than creating an isolated page.

Preserve:

**Home** — unchanged

**Community Home** — unchanged

Add:

**Post List** — new screen

Connections:

### Community Home → Post List

* `전체 게시글 보기` → default Post List
* `일반` → Post List / 일반
* `질문·답변` → Post List / 질문·답변
* `경험·후기` → Post List / 경험·후기
* each Topic → Post List / selected Topic

### Post List

* Logo → existing Home
* GNB `홈` → existing Home
* GNB `커뮤니티` → existing Community Home
* Post Title → future Post Detail
* `글쓰기` → future authentication / Post Create flow

Do not generate Post Detail, Login, or Post Create full screens yet.

---

# Final Goal

Create a **Desktop Low-Fidelity Community Post List** that feels like a natural continuation of the existing Home and Community Home.

The screen should make it easy for a user to answer:

**“What posts am I currently looking at, how can I narrow them down, and which post should I open?”**

Prioritize:

**current context
→ search
→ Post Type / Topic filtering
→ scanability
→ notice visibility
→ pagination
→ state preservation
→ accessibility**

Preserve the existing prototype and create only the Post List in this iteration.
