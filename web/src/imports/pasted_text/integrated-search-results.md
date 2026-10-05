Extend the existing desktop low-fidelity prototype by creating a new
Integrated Search Results screen:

SCR-SEARCH-001 / 통합 검색 결과

This screen belongs to the same Korean accessibility-focused community + information platform as the existing:

- Home
- Community Home
- Post List
- Post Detail
- Post Create

Create only the Integrated Search Results screen and the minimum prototype states/interactions required for this iteration.

Do NOT redesign any existing screen.

All user-facing UI text must remain in Korean.

---

# 1. Critical Prototype Preservation Requirements

Preserve the existing prototype.

Do NOT redesign:

- Home
- Community Home
- Post List
- Post Detail
- Post Create
- shared Header
- shared Global Navigation
- shared Footer

Reuse the existing low-fidelity visual language.

Do not introduce:

- new branding
- new navigation architecture
- a Search-specific site shell
- a dashboard-style layout

This should feel like the next natural screen in the existing product.

---

# 2. Screen Role

Create:

SCR-SEARCH-001
통합 검색 결과

This screen allows users to:

- keep and edit the current global search query
- see representative results from multiple service domains
- understand which domain each result belongs to
- move to a domain-specific full result screen
- open the original detail page for a result

This screen is NOT:

- a domain-specific full search result page
- an AI answer page
- a recommendation dashboard
- a Search-only detail page

The purpose is:

Query
→ Integrated Search Results
→ Domain Preview
→ Domain Result or Original Detail

---

# 3. Public Access

Integrated Search is publicly accessible.

Do not require login to:

- search
- view search results
- open public original detail pages

Authentication may be required later only for member-only actions inside the original detail screen.

Do not add a login gate to Search.

---

# 4. Default Prototype State

Use the following primary Low-Fidelity state:

Desktop

Query:
`인공와우`

Search Area:
`전체`

State:
Results Available

The Search Field must already contain:

`인공와우`

Do not make the user re-enter the query after navigating from Home Search.

---

# 5. Shared Header

Reuse the existing shared Header.

Do not redesign it.

Keep:

- Logo
- Global Search architecture
- authentication/account area

The Search screen should visually belong to the same product.

---

# 6. Global Navigation

Reuse the existing Global Navigation.

Keep:

- 홈
- 커뮤니티
- 복지 · 지원정보
- 병원 · 전문기관
- 보조기기

The Search screen is a global Search context.

Do not incorrectly mark one content domain as the active page merely because results from that domain are visible.

Do not create a new Search navigation item unless it already exists in the current shared navigation.

---

# 7. No Breadcrumb

Do not add Breadcrumb navigation to the Integrated Search Results screen.

This is an independent Search Result screen rather than a Detail hierarchy.

Do not add:

- 홈 > 검색
- 통합 검색 > 결과
- other artificial Breadcrumbs

---

# 8. Search Field

Provide a prominent Search Field near the top of the main content.

The current query must remain visible:

`인공와우`

Include:

- persistent visible Search label or clear accessible naming
- editable query
- Search execution
- query clear action

Support conceptually:

- Enter
- Search button

Do not rely only on a magnifying-glass icon.

Do not rely on placeholder text as the only Search label.

---

# 9. Search Query Persistence

Preserve the current query when navigating within Search.

Example:

Home
→ search `인공와우`
→ Integrated Search Results
→ Search Field still contains `인공와우`

Search Area change:

`전체`
→ `커뮤니티`

The query:

`인공와우`

must remain unchanged.

Do not reset the query when the Search Area changes.

---

# 10. Result Summary

Below or near the Search Field, clearly communicate what the user is viewing.

Use an H1 such as:

`"인공와우" 검색 결과`

or an equivalent structure consistent with the current Product documentation.

Do not show a total result count unless the prototype intentionally uses a reliable mock count.

Do not invent an arbitrary precise result count.

---

# 11. Search Area Navigation

Provide the official Search Area controls.

Use exactly:

- 전체
- 커뮤니티
- 복지 · 지원정보
- 병원 · 전문기관
- 보조기기

Use the official concept:

`검색 영역`

Do not call these:

- 카테고리
- 콘텐츠 유형
- 검색 카테고리
- 서비스 카테고리

The selected state must remain understandable without relying on color alone.

For the default screen:

`전체`

is selected.

---

# 12. Search Area Is Not a Community Filter

Do not confuse Search Area with Community filtering.

Search Area:

- 전체
- 커뮤니티
- 복지 · 지원정보
- 병원 · 전문기관
- 보조기기

Community-specific concepts:

- Post Type
- Primary Topic

Do not show Community Post Type or Topic filters on the default `전체` Integrated Search screen.

Those belong to the domain-specific Search Area Result screen later.

---

# 13. Integrated Result Structure

For:

Search Area = 전체

show representative Preview sections for all four domains.

Recommended hierarchy:

H2 커뮤니티

Result Item
Result Item
...

`커뮤니티 결과 더보기`


H2 복지 · 지원정보

Result Item
Result Item
...

`복지 · 지원정보 결과 더보기`


H2 병원 · 전문기관

Result Item
Result Item
...

`병원 · 전문기관 결과 더보기`


H2 보조기기

Result Item
Result Item
...

`보조기기 결과 더보기`

Do not show every result from every domain.

This screen is a Preview hub.

---

# 14. Do Not Use One Universal Card for Every Domain

Each domain contains different decision-making information.

Do not force every result into an identical large card structure.

Use consistent overall spacing and typography, but allow domain-specific metadata.

Prefer:

- restrained list items
- subtle dividers
- text hierarchy
- minimal status labels

Avoid large rounded cards around every result.

---

# 15. Community Result Structure

Community Search results should use the current official Community model.

Required information:

- Post Type
- Primary Topic
- title
- 작성일

Optional when useful:

- short Summary
- 댓글 수
- 작성자

For QUESTION posts only, support Question Status.

Official states:

OPEN
→ `답변 대기`

ANSWERED
→ `답변 있음`

RESOLVED
→ `해결됨`

Do NOT use:

- 답변 완료
- CLOSED
- accepted answer

Example:

`질문 · 답변 · 인공와우 · 답변 있음`

`인공와우 수술 후 재활은 어떻게 진행하셨나요?`

`수술 이후 재활 과정과 경험을 공유합니다…`

`2026.08.10 · 댓글 8`

Do not show Question Status on GENERAL or EXPERIENCE posts.

---

# 16. Community Result Exclusions

Do not add:

- Board
- Community Category
- Tag
- Disability Type
- Community Space
- likes
- view count
- social-media reactions

Do not redesign Community Search results as a social feed.

---

# 17. Welfare & Support Result Structure

For Welfare & Support Information results, show enough information for the user to judge relevance before opening the detail.

Required:

- 사업명
- 지원 대상
- 신청 기간
- 현재 상태

Optional:

- 담당 기관

Example conceptual structure:

`접수 중`

`청각장애인 보조기기 지원사업`

`지원 대상: 등록 청각장애인`

`신청 기간: 2026.08.01 ~ 2026.09.30`

Use textual status labels.

Do not communicate application status using color alone.

---

# 18. Welfare Result Exclusions

Do not add:

- AI eligibility score
- personalized qualification prediction
- in-service application completion
- advertising
- promotional banners

Keep the result informational.

---

# 19. Hospital & Professional Institution Result Structure

Required:

- 기관명
- 기관 유형
- 지역
- 주요 서비스

Optional:

- 전문분야, only if appropriate as mock data

Example:

`서울○○청각센터`

`청각센터 · 서울`

`청력검사 · 보청기 상담 · 청능재활`

Keep the UI architecture general enough to support broader disability-related institutions later.

---

# 20. Institution Result Exclusions

Do not add:

- 현재 위치
- 내 주변
- 거리
- 지도
- 별점
- 예약
- 실시간 진료 가능 여부

Do not imply unsupported location functionality.

---

# 21. Assistive Device Result Structure

Required:

- 제품명
- 제조사
- 제품 유형
- 대표 정보

Optional:

- 후기 정보

Example:

`오픈형 보청기 A`

`제조사 ○○ · 보청기`

`경도부터 중고도 난청 사용자를 위한 제품`

Keep the result informational rather than commercial.

---

# 22. Assistive Device Result Exclusions

Do not add:

- 가격
- 할인
- 구매
- 장바구니
- 별점 중심 UI
- 판매처 우선 노출
- 광고
- 프로모션 Badge

The product should feel like an information database, not an online store.

---

# 23. Result Title Interaction

Each result title should be a clear navigation link.

Selecting the result should conceptually lead to the original domain Detail.

Examples:

Community Result
→ Post Detail

Welfare Result
→ Welfare Detail

Institution Result
→ Institution Detail

Assistive Device Result
→ Device Detail

Do not create a Search-specific duplicate Detail page.

---

# 24. Domain “More Results” Navigation

At the end of each Preview section, provide a clear link such as:

- 커뮤니티 결과 더보기
- 복지 · 지원정보 결과 더보기
- 병원 · 전문기관 결과 더보기
- 보조기기 결과 더보기

These links lead conceptually to:

SCR-SEARCH-002 / 검색 영역별 결과

with:

- the current query preserved
- the selected Search Area applied

Do not build the complete SCR-SEARCH-002 UI in this iteration.

Only prepare the navigation connection.

---

# 25. No Detailed Filters on the Integrated Preview Screen

Do not show domain-specific detailed filters on SCR-SEARCH-001.

Do not add:

Community:
- Post Type filter
- Topic filter

Welfare:
- 대상
- 지역
- 상태 filters

Institution:
- 지역
- 기관 유형
- 서비스 filters

Device:
- 제품 유형
- 제조사 filters

These belong to SCR-SEARCH-002 after Product decisions are finalized.

---

# 26. No Sort on This Screen

Do not add Sort controls to the Integrated Preview screen.

Do not show:

- 관련도순
- 최신순
- 인기순

The Search documentation intentionally defers Sort to the domain-specific result screen where applicable.

---

# 27. No Pagination on This Screen

Do not add Pagination to SCR-SEARCH-001.

This screen shows a small representative Preview from each domain.

Pagination belongs to SCR-SEARCH-002 or future implementation decisions.

Do not add:

- page numbers
- Load More for all integrated results
- Infinite Scroll

---

# 28. Result Count

Result counts are optional.

Do not invent precise counts.

If a count is shown, use only intentional mock data and keep it visually secondary.

Do not make statistics a major visual feature.

---

# 29. Search Result Preview Density

Keep each domain Preview concise.

The exact number of preview items is not finalized.

For Low-Fidelity, use a small representative sample sufficient to verify hierarchy.

For example:

2–3 results per domain

is acceptable as a prototype representation, but do not turn this into a final Product rule.

---

# 30. Loading State

Create or structurally support a Loading state.

Preserve:

- Header
- GNB
- current Search query
- Search Area Navigation

The result region may display:

- simple Loading indicator
- restrained Skeleton structure where appropriate

Do not Skeleton the entire application shell.

Communicate that Search is in progress.

---

# 31. No Result State

Create or support a No Result state.

Example:

`"xxxxx"에 대한 검색 결과가 없습니다.`

`검색어를 확인하거나 다른 조건으로 검색해 보세요.`

Allow the user to:

- modify the query
- search again

Do not:

- automatically clear the query
- replace zero results with unrelated recommendations
- disguise recommended content as Search results

---

# 32. Partial Error State

This state is important for Integrated Search.

If one domain fails, do not replace the whole screen with an error.

Example:

커뮤니티
→ normal results

복지 · 지원정보
→ normal results

병원 · 전문기관
→ error

`병원 · 전문기관 검색 결과를 불러오지 못했습니다.`

`다시 시도`

보조기기
→ normal results

Only the failed domain should show its Error state.

Keep successful domain results available.

---

# 33. Total Error State

If the entire Search operation fails:

Show a clear message such as:

`검색 결과를 불러오지 못했습니다.`

`잠시 후 다시 시도해 주세요.`

Action:

`다시 시도`

Preserve the current query.

Do not send the user back to Home.

---

# 34. Invalid Empty Query

If Search is executed with no query:

Use a Field-level error such as:

`검색어를 입력해 주세요.`

Keep focus in the Search Field.

Do not send an empty request.

Do not display unrelated content as Search results.

Do not finalize a minimum character rule in the primary Low-Fidelity prototype.

---

# 35. Navigation & Search State

Conceptually preserve Search context.

If the user enters:

Query = `인공와우`

and opens a Result Detail:

Search Results
→ Original Detail
→ Browser Back

the architecture should later support restoring:

- Query
- Search Area
- Filter when applicable
- Sort when applicable
- Page when applicable
- scroll position where practical

Do not attempt to fully implement dynamic state restoration in Figma if it creates unnecessary prototype complexity.

---

# 36. Prototype Data Limitation

Low-Fidelity Figma does not need to implement a complete Search backend.

Use representative static/mock data.

The prototype should verify:

- Search IA
- Search Field
- Search Area
- Domain Preview hierarchy
- differences between Result types
- major Search states
- navigation structure

The following may be verified later using Frontend Mock/API data:

- actual dynamic search results
- actual result counts
- multi-domain API composition
- Pagination datasets
- domain filter combinations
- ranking
- URL/state synchronization
- Browser Back state restoration
- every result-to-detail data match
- real Partial API Failure behavior

Do not over-engineer Figma to simulate a production Search engine.

---

# 37. Accessibility

Support:

- visible or meaningful Search label
- Search Field accessible name
- Search Button
- keyboard Enter search
- query clear action with meaningful name
- logical focus order
- visible focus indicators
- accessible Search Area single-selection pattern
- selected Search Area beyond color alone
- H1 for the current search result context
- H2 for each domain Preview section
- explicit result-title links
- textual Question Status
- textual Welfare Status
- accessible Loading feedback
- accessible Error feedback
- text enlargement and reflow
- readable metadata
- no color-only meaning

Do not finalize exact accessibility numeric tokens yet.

---

# 38. Desktop Layout

This iteration is Desktop-first.

Prefer a calm, readable vertical structure.

Conceptual layout:

Shared Header
Global Navigation

Search Field

"인공와우" 검색 결과

Search Area
[전체] [커뮤니티] [복지 · 지원정보] [병원 · 전문기관] [보조기기]

커뮤니티
Result
Result
커뮤니티 결과 더보기

복지 · 지원정보
Result
Result
복지 · 지원정보 결과 더보기

병원 · 전문기관
Result
Result
병원 · 전문기관 결과 더보기

보조기기
Result
Result
보조기기 결과 더보기

Footer

Do not add a Sidebar.

---

# 39. Preserve Low-Fidelity Visual Direction

Use the same restrained design direction as the existing prototype.

Prioritize:

- typography hierarchy
- spacing
- dividers
- metadata
- lightweight labels
- scanability

Avoid:

- heavy cards
- large shadows
- gradients
- decorative backgrounds
- oversized badges
- promotional artwork
- dashboard widgets

---

# 40. Responsive Architecture

Do not create full Tablet or Mobile screens yet.

However, preserve a structure that can later adapt.

Mobile conceptually remains:

Search Field
→ Search Area Navigation
→ vertical result sections

Do not create a desktop-only structure that depends on a wide multi-column dashboard.

---

# 41. Existing Prototype Connections

Preserve all existing screens.

Add the Search screen.

Connections:

### Home

Home Hero Search
→ SCR-SEARCH-001
→ entered query remains populated

### Header Global Search

Search
→ SCR-SEARCH-001
→ query remains populated

### Integrated Search

Result
→ original Detail

Domain result more link
→ future SCR-SEARCH-002

Do not redesign the target Detail screens.

---

# 42. Do Not Build SCR-SEARCH-002 Yet

SCR-SEARCH-002 is not yet Product-ready.

Do not create the full domain-specific result screen.

Do not invent:

- Welfare final filters
- Institution final filters
- Device final filters
- default Sort
- Pagination pattern
- Query-change filter policy

Only create conceptual navigation targets if needed for prototype linking.

---

# 43. Explicit MVP Exclusions

Do NOT add:

- AI Search
- AI Answer
- AI Summary
- Semantic Search UI
- AI recommendations
- personalized ranking
- interest-based results
- Tag Search
- Disability Type Filter
- Community Space Filter
- Board Filter
- location-based recommendations
- nearby institutions
- map
- reservation
- advertisements
- promoted results
- voice search
- typo correction
- saved search
- search subscription

Do not add recent searches, autocomplete, or popular searches to the primary frame.

---

# 44. Do Not Add Commerce Patterns

For Assistive Device results, do not add:

- price
- discount
- purchase buttons
- cart
- checkout
- retailer promotion

The Search experience remains informational.

---

# 45. Low-Fidelity State Coverage

Prioritize:

SCR-SEARCH-001 / Results Available

Useful supporting states:

- Loading
- No Result
- Partial Error
- Total Error

These may be implemented as state variants rather than independent product pages.

Do not create excessive state screens.

---

# Final Goal

Create a Desktop Low-Fidelity Integrated Search Results screen that helps the user understand:

“What did I search for, which service areas have relevant results, and where should I go next?”

Prioritize:

Query persistence
→ Search Area clarity
→ domain-specific result preview
→ scanability
→ original-detail navigation
→ partial-failure resilience
→ accessibility

The primary screen should represent:

Query = `인공와우`
Search Area = `전체`
State = Results Available

Preserve all existing screens.

Create only SCR-SEARCH-001 and its necessary Low-Fidelity states in this iteration.