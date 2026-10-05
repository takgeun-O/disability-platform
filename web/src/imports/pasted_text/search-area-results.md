# IYUM — SCR-SEARCH-002 Search Area Results
## Low-Fidelity Figma Make Implementation Brief

Create the next desktop low-fidelity screen for the IYUM MVP:

SCR-SEARCH-002 — Search Area Results

This screen represents the full search results for one specific search area.

This is NOT a redesign of the existing search experience.

Preserve the existing IYUM prototype, visual language, navigation structure, shared header, GNB, search patterns, and all previously completed screens.

The purpose of this task is to add SCR-SEARCH-002 as the next screen in the existing prototype and connect it to the existing SCR-SEARCH-001 Unified Search Results screen.

Do not introduce new Product Decisions.

Use the following contract as the implementation source of truth.

---

# 1. Existing Prototype Context

The following desktop low-fidelity screens already exist:

- Home
- Community Home
- Post List
- Post Detail
- Post Create
- SCR-SEARCH-001 Unified Search Results

Do not redesign, replace, or visually reinterpret these existing screens.

Reuse their existing:

- shared header
- logo
- global navigation
- global search pattern
- typography hierarchy
- spacing rhythm
- low-fidelity visual language
- interaction conventions

Add SCR-SEARCH-002 to this existing prototype.

---

# 2. Screen Definition

Screen ID:

SCR-SEARCH-002

Screen name:

Search Area Results

Purpose:

Display the complete search results for one selected search area.

Access:

Public

Authentication is not required.

Search areas:

- 전체
- 커뮤니티
- 복지 · 지원정보
- 병원 · 전문기관
- 보조기기

Navigation rule:

- 전체 → SCR-SEARCH-001
- 커뮤니티 → SCR-SEARCH-002 / Community
- 복지 · 지원정보 → SCR-SEARCH-002 / Welfare
- 병원 · 전문기관 → SCR-SEARCH-002 / Institution
- 보조기기 → SCR-SEARCH-002 / Device

SCR-SEARCH-002 is one shared screen structure with domain-specific variants.

Do not create separate unrelated page architectures for each domain.

---

# 3. Primary Desktop Frame

Create the first representative desktop frame using this state:

Query:
인공와우

Search Area:
커뮤니티

Post Type:
질문 · 답변

Primary Topic:
인공와우

Result State:
Results Available

Current Page:
2

This frame should make the following concepts immediately understandable:

- current search query
- selected Search Area
- two active domain filters
- AND relationship between the filter axes
- search result hierarchy
- Question Status where applicable
- page-number pagination
- navigation to original content detail

This is the primary representative state for SCR-SEARCH-002.

---

# 4. Overall Desktop Structure

Use a clear vertical hierarchy.

Recommended structure:

1. Existing Shared Header
2. Existing Global Navigation
3. Search Section
4. Search Result Summary
5. Search Area Navigation
6. Domain Filter Controls
7. Result List
8. Page-number Pagination
9. Existing Footer, if the current prototype uses one

Do not introduce a sidebar as the default layout.

There are only two filter axes per domain, so prefer a compact inline filter structure on desktop.

Keep this screen visually consistent with SCR-SEARCH-001.

---

# 5. Shared Header and GNB

Reuse the existing IYUM header and GNB without redesigning them.

Do not create a new Search-specific header.

The page remains part of the global Search context.

Do not incorrectly treat the selected Search Area as if the user had navigated to that domain's top-level service page.

For example:

Selecting 커뮤니티 as a Search Area does not mean this is Community Home.

It remains a Search Result screen.

---

# 6. Breadcrumb

Do NOT add a breadcrumb.

SCR-SEARCH-002 has no breadcrumb in the MVP contract.

Do not copy the Post Detail breadcrumb pattern into Search.

---

# 7. Search Field

Reuse the established search field pattern from the existing Search experience.

The current query must remain visible:

인공와우

The user must be able to:

- edit the query
- submit using the Search button
- submit using Enter
- clear the query

Do not replace the existing Search interaction pattern with a new component.

---

# 8. Empty Query Validation

If the user submits an empty or whitespace-only query:

- remain on the current screen
- keep the existing results visible
- do not navigate
- do not enter Loading
- show a field-level validation error near the Search Field

Exact Korean error message:

검색어를 입력해 주세요.

Do not use:

- Toast
- Modal
- Dialog
- full-page error

for Empty Query Validation.

The Search Field and error must remain clearly associated.

Do not rely on color alone to communicate the error.

---

# 9. Search Result Summary

Provide a lightweight result heading or summary that clearly communicates the current search context.

For example, a structure equivalent to:

"인공와우" 검색 결과

may be used if it matches the existing Search visual language.

Result Count is optional.

Do not invent a precise total result count if the prototype does not have reliable data for it.

The hierarchy should prioritize:

Query
→ Search Area
→ Filters
→ Results

---

# 10. Search Area Navigation

Display all five Search Areas:

- 전체
- 커뮤니티
- 복지 · 지원정보
- 병원 · 전문기관
- 보조기기

For the primary frame:

커뮤니티

must be visibly selected.

The selected state must not rely on color alone.

Use the same Search Area navigation pattern established in SCR-SEARCH-001 whenever possible.

Interaction:

전체
→ navigate to SCR-SEARCH-001 while preserving Query

Other specific domains
→ switch to the corresponding SCR-SEARCH-002 domain variant

Search Area change behavior:

Preserve:
- Query

Reset:
- previous domain filters
- previous page

Then:
- load the new domain in its neutral filter state
- start at Page 1

Never carry Community-specific filters into another domain.

---

# 11. Common Filter Contract

Each domain has exactly two MVP filter axes.

All filter axes are:

- Single-select
- optional / neutral by default

Different filter axes combine using AND.

Example:

Post Type = 질문 · 답변
AND
Primary Topic = 인공와우

Do not use Multi-select.

Provide:

- individual filter reset
- Reset All

When a filter is changed or reset:

→ Page 1

Preserve:

- Query
- Search Area

Do not add a third filter.

Do not create an advanced filter panel.

---

# 12. Filter UI Direction

For desktop Low-Fidelity:

Prefer a compact inline filter group.

The UI should clearly communicate:

- filter axis label
- current selection
- ability to change selection
- ability to reset
- relationship between the two axes

Do not over-design the controls.

A simple Low-Fidelity Select, compact selector, or equivalent established control may be used.

Do not make a new Product Decision about the final control style.

Avoid unnecessary filter chips unless they materially improve clarity.

Do not create a large filter sidebar.

---

# 13. Community Variant

Official Community filters:

## Post Type

Options:

- 일반
- 질문 · 답변
- 경험 · 후기

Single-select.

Primary frame selection:

질문 · 답변

## Primary Topic

Options:

- 자유 · 일상
- 보청기
- 인공와우
- 치료 · 재활
- 의사소통
- 취업 · 직장
- 복지 · 생활

Single-select.

Primary frame selection:

인공와우

Combination:

질문 · 답변
AND
인공와우

Do NOT use Question Status as a filter.

Do NOT add:

- Board
- Community Category
- Tag
- Disability Type
- Community Space

---

# 14. Welfare Variant

Official filters:

## 신청 상태

Single-select.

## 지역

Single-select.

Do not invent new filtering axes.

지원 대상 is NOT a filter.

It remains result metadata.

Do not add advanced eligibility filters.

---

# 15. Institution Variant

Official filters:

## 지역

Single-select.

## 기관 유형

Single-select.

Do NOT use the following as filters:

- 제공 서비스
- 장애 유형
- 운영 여부

Do NOT add:

- current location
- near me
- distance
- map
- reservation
- real-time appointment availability

This is not a map-based local search interface.

---

# 16. Assistive Device Variant

Official filters:

## 제품 유형

Single-select.

## 제조사

Single-select.

Do NOT add filters for:

- price
- discount
- seller
- delivery
- purchase availability
- Bluetooth
- waterproofing
- battery
- technical specifications

This is not an e-commerce search interface.

---

# 17. Reset Behavior

Provide an understandable way to reset filters.

Individual Reset:

Reset only one filter axis.

Reset All:

Reset both filters for the current domain.

Preserve:

- Query
- Search Area

After any reset:

→ Page 1

Do not reset the Search Area when Reset All is used.

Do not clear the Query when Reset All is used.

---

# 18. Query Change Behavior

When the user changes the Query:

Preserve:
- current Search Area

Reset:
- all filters for the current domain
- current Page

Then:

→ Page 1
→ execute the new Query

Example:

Before:

Query = 인공와우
Search Area = 커뮤니티
Post Type = 질문 · 답변
Primary Topic = 인공와우
Page = 2

User changes Query to:

보청기

Resulting context:

Query = 보청기
Search Area = 커뮤니티
Post Type = neutral
Primary Topic = neutral
Page = 1

Do not preserve hidden filters after a Query change.

---

# 19. Sort

Do NOT add a user-facing Sort Control.

Do not add:

- 관련도순
- 최신순
- 마감 임박순
- 이름순
- 최근 등록순
- any other Sort selector

Do not create a disabled Sort placeholder.

Backend default ordering is not a user-facing UI feature.

---

# 20. Result List

Use a list-oriented structure.

Prioritize:

- readable hierarchy
- scanning
- domain-specific metadata
- clear clickable result title
- restrained use of containers

Prefer:

- spacing
- typography hierarchy
- dividers
- lightweight grouping

over excessive card nesting.

Do not turn every result into a large promotional card.

The result list should feel related to SCR-SEARCH-001 while providing the denser structure appropriate for a full result list.

---

# 21. Community Result Item

Required metadata:

- 제목
- Post Type
- Primary Topic
- 작성일

For QUESTION posts only:

- Question Status

Official Question Status labels:

- 답변 대기
- 답변 있음
- 해결됨

Optional metadata when useful:

- Summary
- 댓글 수
- 작성자

Do not display Question Status on GENERAL or EXPERIENCE posts.

Use representative QUESTION results in the primary Community frame so the official Question Status treatment can be verified.

Question Status must remain text-readable and must not rely on color alone.

Reuse the established Question Status pattern from existing Community screens where possible.

---

# 22. Welfare Result Item

Required metadata:

- 사업명
- 지원 대상
- 신청 기간
- 현재 상태

Optional:

- 담당 기관

Do not confuse result metadata with filter controls.

Current Status should be understandable through text.

---

# 23. Institution Result Item

Required metadata:

- 기관명
- 기관 유형
- 지역
- 주요 서비스

Optional:

- 전문 분야

Do not add:

- distance
- map preview
- star rating
- booking CTA
- real-time availability

---

# 24. Assistive Device Result Item

Required metadata:

- 제품명
- 제조사
- 제품 유형
- 대표 정보

Optional:

- 후기 정보

Do not make price or commerce information the primary content.

Do not add:

- discount
- Add to Cart
- Buy Now
- seller
- shipping
- marketplace UI

---

# 25. Result Navigation

Each result must navigate to its original domain detail.

Community Result
→ Post Detail

Welfare Result
→ Welfare Detail

Institution Result
→ Institution Detail

Device Result
→ Device Detail

Do not create a Search-specific Detail page.

For prototype connections that do not yet have an existing destination screen, preserve the intended interaction concept without redesigning unrelated screens.

Do not fabricate a new detailed Product screen solely to satisfy the prototype link.

---

# 26. Pagination

Use Page-number Pagination.

Provide the conceptual structure for:

- Previous
- Page numbers
- Current Page
- Next

Primary representative frame:

Current Page = 2

The current page must be distinguishable without relying on color alone.

Do not determine the final Page Size.

Do not use:

- Infinite Scroll
- Load More
- endless content
- Domain Preview-style 더보기

as the pagination mechanism.

---

# 27. Pagination Behavior

When Query changes:

→ Page 1

When Search Area changes:

→ Page 1

When Filter changes:

→ Page 1

When an individual Filter is reset:

→ Page 1

When Reset All is used:

→ Page 1

When the user navigates back from a result detail:

restore conceptually:

- Query
- Search Area
- Filter
- Page
- scroll position when possible

Full Browser Back state restoration will be verified during Frontend implementation.

Do not over-engineer this limitation in Figma.

---

# 28. Loading State

Create or support a representative Loading state for SCR-SEARCH-002.

Preserve the user's Search context:

- Header
- GNB
- Search Field
- Query
- Search Area
- Filter context

The result area may transition into a Low-Fidelity loading representation.

Do not make a final decision between Skeleton and Spinner styling.

Do not redesign the entire page during Loading.

---

# 29. Results Available State

The primary frame should use:

Query:
인공와우

Search Area:
커뮤니티

Post Type:
질문 · 답변

Primary Topic:
인공와우

Page:
2

State:
Results Available

Use enough representative results to validate:

- information hierarchy
- Question Status
- list density
- pagination
- result navigation

Do not imply that the static Figma data represents real backend search results.

---

# 30. No Result State

Create or support a representative No Result state.

Definition:

Valid Query + valid Search execution + 0 results

Preserve:

- Query
- Search Area
- active Filters

Allow the user to understand that they can:

- modify the Query
- reset an individual Filter
- Reset All
- change Search Area

Do not replace the empty result with unrelated recommendation content.

Do not confuse No Result with Empty Query Validation.

---

# 31. Total Error / Retry

SCR-SEARCH-002 is a single-domain full result screen.

Use:

- Total Error
- Retry

Do NOT use Partial Error.

Partial Error belongs to SCR-SEARCH-001 because that screen combines multiple domain previews.

In the Error state, preserve the user's Search context where possible:

- Query
- Search Area
- Filter state

Provide a clear Retry action.

Do not create a new Error architecture.

---

# 32. Accessibility

Maintain the accessibility principles already established in the IYUM project.

Search Field:

- accessible label
- Search button purpose
- Enter submission
- clear action
- field-level validation relationship

Search Area:

- understandable single selected state
- keyboard accessible
- selected state not communicated by color alone

Filters:

- visible or accessible labels
- Single-select semantics
- understandable selected state
- keyboard interaction
- visible focus
- understandable Reset actions

Results:

- clear title hierarchy
- meaningful links
- readable metadata
- text-based status information

Pagination:

- Previous / Next purpose
- Current Page communicated semantically
- keyboard accessible
- visible focus

States:

- Loading
- No Result
- Total Error
- Retry

must be understandable without relying only on visual styling.

Do not define final accessibility token values in Low-Fidelity.

---

# 33. Responsive Awareness

The current implementation target is:

Desktop Low-Fidelity

Do not create a full responsive redesign unless required for the existing prototype.

However, keep the structure compatible with future Tablet and Mobile layouts.

Tablet:

- allow filter controls to reflow
- maintain readable result width
- preserve Search Area navigation
- preserve Pagination

Mobile:

- Single Column
- Search Field
- Search Area
- Filter
- Result List
- Pagination

Future Mobile Filter presentation may use:

- Compact Control
- Drawer
- Bottom Sheet

Do not select one as the final Product pattern in this task.

There is no Sort Control on Desktop, Tablet, or Mobile.

---

# 34. Prototype Connections

Connect SCR-SEARCH-002 to the existing prototype where practical.

From SCR-SEARCH-001:

Domain `결과 더보기`
→ corresponding SCR-SEARCH-002 variant

Search Area `전체`:
→ SCR-SEARCH-001

Specific Search Area:
→ corresponding SCR-SEARCH-002 variant

Within SCR-SEARCH-002:

Search Area changes
→ preserve Query
→ reset previous domain Filters
→ Page 1

Result:
→ original domain Detail

Browser Back:
→ conceptually restore previous Search context

Do not modify the visual design of SCR-SEARCH-001 merely to support these connections.

Only add the minimum interaction necessary.

---

# 35. Prototype Verification Scope

The Low-Fidelity prototype should allow us to evaluate the concept of:

- Search Area switching
- Domain variants
- two domain Filters
- Single-select behavior
- AND relationship
- Filter selection
- Individual Reset
- Reset All
- Query-change reset concept
- Search Area-change reset concept
- Result Item hierarchy
- Page-number Pagination
- Empty Query Validation
- No Result
- Total Error / Retry
- Result → Original Detail
- Desktop information hierarchy

Do not attempt to simulate a real Search backend.

The following belong to Frontend Mock/API or Backend verification:

- actual search results
- actual AND filtering
- real result count
- real pagination data
- real URL serialization
- complete Browser Back restoration
- actual backend errors
- actual page-reset logic
- backend validation
- backend ordering
- real domain data

Figma limitations must not change the Product contract.

---

# 36. Important Figma Make Constraints

Do NOT:

- redesign Home
- redesign Community Home
- redesign Post List
- redesign Post Detail
- redesign Post Create
- redesign SCR-SEARCH-001
- create a new Screen ID
- create a new Search Domain
- add Breadcrumb
- add Sort Control
- add Multi-select
- add Infinite Scroll
- use Load More as SCR-SEARCH-002 pagination
- add more than two filters per Domain
- add Tag
- add Board
- add Disability Type
- add Community Space
- add AI Search
- add Semantic Search UI
- add personalized Search
- add location-based Search
- add map UI
- add reservation UI
- add commerce filters
- add advertisements
- create a Search-specific Detail page
- turn every result into a large card
- make High-Fidelity branding decisions

Do not introduce functionality just because it is common in commercial search products.

Follow the IYUM MVP contract.

---

# 37. Low-Fidelity Boundaries

Do not finalize:

- brand colors
- final typography
- exact spacing
- exact grid
- exact pixel dimensions
- radius
- shadow
- final Filter control style
- final Mobile Filter pattern
- Page Size
- URL parameter names
- Backend ordering
- Search Engine
- Ranking algorithm
- Index strategy

Focus on:

- information architecture
- Search hierarchy
- Domain variants
- Filter hierarchy
- Single-select behavior
- Reset behavior
- Result hierarchy
- Pagination
- Search states
- navigation
- accessibility
- prototype verification

---

# 38. Implementation Priority

Prioritize the following in order:

1. Preserve the existing IYUM prototype.
2. Add SCR-SEARCH-002 without redesigning existing screens.
3. Build the primary Community Results Available frame.
4. Make the two-filter structure and selected state clear.
5. Make Question Status visible on QUESTION results.
6. Implement Page-number Pagination with Page 2 selected.
7. Connect Search Area navigation.
8. Connect SCR-SEARCH-001 → SCR-SEARCH-002 where practical.
9. Support Individual Reset and Reset All conceptually.
10. Support Empty Query Validation.
11. Support No Result.
12. Support Total Error / Retry.
13. Represent the other three Domain Variants using the same shared screen architecture.

If prototype limitations prevent full dynamic behavior, prefer clear representative states over inventing new Product behavior.

---

# 39. Expected Result

The final Low-Fidelity implementation should make it immediately clear that:

SCR-SEARCH-001
= unified multi-domain preview

while:

SCR-SEARCH-002
= full results for one selected Search Area

and that SCR-SEARCH-002 follows this core structure:

Query
→ Search Area
→ Domain Filters
→ Result List
→ Page-number Pagination

with:

- exactly two filters per domain
- Single-select
- AND across filter axes
- no user Sort
- domain-specific result metadata
- original Detail navigation
- accessible Search states

The primary Community frame must clearly demonstrate:

인공와우
→ 커뮤니티
→ 질문 · 답변
AND
→ 인공와우
→ Results Available
→ Page 2

Do not expand the MVP beyond this contract.