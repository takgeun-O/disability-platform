Extend the **existing desktop low-fidelity prototype** by creating a new **Community Home** screen.

This is the same Korean community + information platform as the existing Home screen.

## Critical Existing-Prototype Requirement

**Preserve the existing Home screen exactly as it is.**

Do not redesign, replace, remove, or visually modify the existing Home screen.

Create the Community Home as a **separate new screen/page/route within the same prototype**.

Connect the existing Community entry points on Home to the new Community Home.

At minimum:

* Global Navigation `커뮤니티` → Community Home
* Core Service Shortcut `커뮤니티` → Community Home

On the new Community Home:

* Global Navigation `홈` → existing Home
* Logo → existing Home
* Global Navigation `커뮤니티` should clearly indicate the current location

If the current prototype already has routing/navigation conventions, preserve and extend them rather than rebuilding them.

All user-facing UI text and realistic sample content must remain in **Korean**.

---

# 1. Goal of the Community Home

The Community Home is the **entry point and discovery hub for the community area**.

It is NOT the full post-list page and NOT an infinite social feed.

The primary user journey should be:

**Community entry
→ Discover post types and major topics
→ Discover recent posts
→ Navigate to a post list or post detail
→ Read or participate**

Users should quickly understand:

* what kinds of experiences and information are shared,
* what major topics exist,
* what recent content is available,
* where to browse all posts,
* and where to start writing a post.

The Community Home should remain relatively lightweight and discovery-oriented.

---

# 2. Relationship with Other Community Screens

Keep the responsibilities of each screen clearly separated.

### Community Home — current screen

Responsible for:

* introducing the community area,
* providing the main `글쓰기` entry,
* discovering post types and major topics,
* showing a limited preview of recent posts,
* linking to the full post list,
* linking directly to individual post details.

### Post List — future separate screen

Responsible for:

* browsing all posts,
* community-specific search,
* post type/topic filters,
* sorting,
* pagination or load-more behavior,
* preserving list exploration state.

Do NOT implement these Post List functions on the Community Home.

### Post Detail — future separate screen

Responsible for:

* full post content,
* comments and replies,
* reactions/recommendations,
* saving,
* sharing,
* reporting,
* author actions.

Do NOT expose these detailed actions on Community Home previews.

### Post Create — future separate screen

Responsible for:

* post type/topic selection,
* title,
* body,
* optional tags/attachments,
* validation,
* submission.

Do NOT place a post creation form directly on the Community Home.

---

# 3. Shared Global UI

The Community Home must clearly look like another screen of the **same service as the existing Home**.

Reuse the existing Home's visual and structural language wherever applicable.

Preserve consistency for:

* Header
* Logo treatment
* Global Navigation
* authentication area
* content width
* typography hierarchy
* spacing philosophy
* buttons
* links
* list items
* metadata
* dividers
* Footer
* accessibility behavior

Do not create a visually independent community microsite.

---

# 4. Header

Reuse the existing Home Header structure.

Do not redesign it.

Use the logged-out state for this Low-Fidelity iteration unless the existing prototype requires otherwise.

Maintain:

* Logo
* Login
* Sign Up

Unlike the Home Hero, the Community Home does not need a second large search-centered Hero.

If the shared Header architecture requires global search access on non-Home pages, it may be represented in a restrained way consistent with the existing service architecture.

Do not create a large Community-specific search interface here.

Community-specific search belongs primarily to the future Post List screen.

---

# 5. Global Navigation

Reuse exactly the same primary service navigation structure:

* 홈
* 커뮤니티
* 복지·지원정보
* 병원·전문기관
* 보조기기

Clearly indicate **커뮤니티** as the current section.

The current state must not rely on color alone.

Do not duplicate these service-level navigation items inside Community Navigation.

---

# 6. Community Page Header

Create a clear page header near the beginning of the main content.

Include:

### H1

**커뮤니티**

A short supporting description may explain that this is a place where users share questions, experiences, and practical information.

Keep the description concise.

### Primary CTA

**글쓰기**

`글쓰기` is the primary action of the Community Home.

Give it clear visual priority without making it oversized or promotional.

For this prototype:

* browsing the Community Home does not require authentication,
* public posts remain accessible without login,
* selecting `글쓰기` while logged out should conceptually trigger authentication,
* after successful login, the user should return to the post creation flow.

Do not place the writing form on this screen.

---

# 7. Post Type & Topic Discovery

Provide a simple way for users to discover the major community content classifications.

The documentation currently separates community classification into:

**Post Type + Major Topic + optional Tags**

For this Low-Fidelity Community Home, prioritize **Post Type and Major Topic**.

Do not expose a complex tag system.

## Post Types

Use the current MVP structure:

* 일반
* 질문·답변
* 경험·후기

These should help users understand the nature of community content.

## Major Topics

Use the current IA labels as provisional MVP topics:

* 자유·일상
* 보청기
* 인공와우
* 치료·재활
* 의사소통
* 취업·직장
* 복지·생활

These labels are still provisional, so do not design them as a rigid permanent taxonomy.

Selecting a post type or topic should conceptually lead to the future Post List screen with that condition applied.

Do NOT create a separate topic-detail page.

---

# 8. Avoid Over-Complex Community Navigation

The MVP community is expected to begin with a relatively limited amount of content.

Therefore:

* avoid large multi-level category trees,
* avoid excessive tabs,
* avoid complex filters,
* avoid multiple nested navigation systems,
* avoid displaying every classification as a large card,
* avoid spreading a small amount of content across too many sections.

Use a simple, highly scannable structure.

Post types and topics should be visually understandable without dominating the entire page.

Do not imitate Reddit, Discord, Facebook, or a large mature forum.

---

# 9. Recent Posts — Primary Content Section

The default and primary content section should be:

**최근 게시글**

Show only a limited number of recent posts as previews.

This is NOT the full post list.

Each preview must include:

* 게시글 제목
* 대표 주제 or 글 유형
* a clearly identifiable title link leading to the future Post Detail screen

Optional metadata may include only a restrained subset of:

* 작성자
* 작성일
* 댓글 수
* 짧은 Summary

Do not display every available metadata field at once.

Prioritize scanability.

A possible conceptual structure is:

**게시글 제목**

`질문 · 인공와우`

작성자 · 작성일 · 댓글 수

Post titles must remain visually recognizable as links.

Do not rely only on making an entire card clickable.

---

# 10. User-Generated Content Character

The Community represents **user-generated experiences, opinions, questions, and discussions**.

Preserve this character visually and semantically.

Do not present community posts as if they were official medical, welfare, institutional, or product information.

Official information about:

* welfare programs,
* hospitals/institutions,
* assistive devices

belongs to their dedicated service areas.

The Community Home should feel trustworthy while still making it clear that the content represents user experiences and discussions.

---

# 11. Full Post List Entry

Provide a clear secondary navigation action:

**전체 게시글 보기**

This should conceptually lead to the future Post List screen.

The hierarchy should be clear:

**Primary Action: 글쓰기**

**Secondary Navigation: 전체 게시글 보기**

Do not give both actions identical visual weight.

---

# 12. Optional Sections

Do NOT automatically add extra content sections.

The following are optional and should only be included if there is a strong structural reason:

* 인기 게시글
* 질문·답변 Preview
* 경험·후기 Preview
* 공지 또는 고정 안내

For this initial Low-Fidelity Community Home, **prefer Recent Posts as the only main post-content section**.

Do not add optional sections merely to fill desktop space.

Do not add:

* Trending
* Ranking
* personalized feeds
* recommended topics
* user rankings
* AI summaries
* AI recommendations

---

# 13. Search, Filter, Sort and Pagination

Do not overload Community Home with Post List functionality.

### Allowed

* Shared Global Search architecture
* Post Type discovery
* Topic discovery

### Do NOT add as primary Community Home controls

* dedicated Community Search field
* detailed filters
* sort controls
* tag filters
* date filters
* pagination
* load more
* recommendation sorting
* popularity sorting
* comment sorting

These belong primarily to the future Post List screen.

---

# 14. Content Density

The Community Home should be easy to scan.

Avoid excessive:

* cards,
* badges,
* pills,
* icons,
* metadata,
* statistics,
* nested containers.

Do not make every post an oversized card.

Prefer:

* simple lists,
* clear section headings,
* lightweight dividers,
* restrained metadata,
* spacing,
* typography hierarchy,
* alignment.

Use visual structure rather than decoration.

---

# 15. Accessibility

Accessibility remains a core requirement.

Design the structure so it supports:

* a clear `H1 커뮤니티`,
* logical H2 section headings,
* keyboard-accessible navigation,
* visible focus states,
* meaningful link and button labels,
* post titles represented as explicit links,
* status/type information not communicated by color alone,
* readable metadata,
* sufficient interactive target areas,
* text enlargement and reflow,
* screen-reader-friendly post-list semantics.

Do not make the entire post card the only clickable target.

Do not use icon-only controls as major navigation.

Do not reduce metadata to extremely small or low-contrast text.

---

# 16. Loading / Empty / Error Architecture

The Default state is the primary screen to design in this iteration.

However, structure the page so individual data-driven sections could later support:

* Loading
* Partial Empty
* Partial Error

The Header, Global Navigation, Community Page Header, and Topic Navigation should not disappear simply because Recent Posts fail to load.

If a content section fails, the rest of the Community Home should remain usable.

Do not create separate state screens in this iteration unless needed for structural clarity.

---

# 17. Desktop Layout

This iteration is **Desktop-first**.

The Community Page Header and `글쓰기` CTA may be arranged horizontally where appropriate.

Post Type and Topic Navigation may be directly visible on desktop.

Recent Posts should use a wide, readable list structure.

A limited multi-column structure may be used for topic discovery if it improves scanning.

Do not add extra content simply because desktop space is available.

---

# 18. Future Responsive Behavior

Do not create full Tablet or Mobile screens in this iteration.

However, ensure the Desktop structure can later adapt naturally.

On Mobile, the expected information order is conceptually:

**Header
→ Community Page Header
→ 글쓰기
→ Post Type / Topic Discovery
→ 최근 게시글
→ 전체 게시글 보기
→ Footer**

The topic structure must be capable of collapsing into a more compact Menu, Drawer, Select, or other appropriate pattern if necessary.

Do not create an endlessly scrolling horizontal tab bar containing every topic.

---

# 19. Low-Fidelity Scope

This is still a **Low-Fidelity structural prototype**.

Do not introduce final branding or visual polish yet.

Do not finalize:

* brand colors,
* exact typography scale,
* exact spacing tokens,
* exact grid dimensions,
* border-radius system,
* shadows,
* gradients,
* illustrations,
* detailed iconography,
* animation,
* visual branding.

Use the same restrained neutral Low-Fidelity character as the existing Home.

Focus on:

* information hierarchy,
* navigation,
* content relationships,
* CTA priority,
* scanability,
* screen responsibilities,
* and future responsive behavior.

---

# 20. Explicit Exclusions

Do NOT add:

* AI assistant
* AI summary
* AI recommendations
* AI-generated related posts
* personalized feed
* interest-based recommendations
* Trending
* Ranking
* user ranking
* follower/following features
* advertisements
* promotional banners
* infinite social feed
* complex category trees
* complex filter systems
* detailed sorting controls
* Post Detail functionality
* comment threads
* reaction controls
* save/bookmark controls on previews
* report controls on previews
* Post Create form
* future-feature placeholders

Do not infer functionality simply because it is common on community websites.

---

# 21. Prototype Navigation Requirements

This Community Home should extend the existing prototype rather than exist in isolation.

Preserve the existing Home screen unchanged.

Create the Community Home as a separate destination.

Connect:

**Existing Home**

* Global Navigation `커뮤니티` → Community Home
* Core Service Shortcut `커뮤니티` → Community Home

**Community Home**

* Logo → existing Home
* Global Navigation `홈` → existing Home
* Global Navigation `커뮤니티` → current Community Home

For elements that point to screens that have not yet been created:

* Post Type / Topic → future Post List
* `전체 게시글 보기` → future Post List
* Post Title → future Post Detail
* `글쓰기` → future Authentication/Post Create flow

Do NOT generate all of those future screens in this iteration.

If the prototype environment requires a destination to create an interaction, leave those future interactions structurally prepared rather than inventing complete screens.

---

# Final Goal

Create a **Desktop Low-Fidelity Community Home** that feels like a natural continuation of the existing Home.

The screen should answer:

**“Can a user quickly understand what the community contains, discover relevant topics and recent posts, and clearly choose whether to browse posts or start writing?”**

Prioritize:

**discovery
→ scanability
→ clear navigation
→ CTA hierarchy
→ accessibility
→ consistency with the existing Home**

Do not redesign the existing Home.

Do not create additional full screens beyond the Community Home in this iteration.
