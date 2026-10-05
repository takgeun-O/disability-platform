Update the existing SCR-SEARCH-002 Low-Fidelity prototype to support a representative Total Error → Retry state.

This is a prototype-only mock interaction for validating the SCR-SEARCH-002 error experience.

Do not implement a real API failure, network simulation, backend logic, or real search engine.

## Goal

Add a deterministic Total Error state for SCR-SEARCH-002 and allow the user to recover from it through a Retry action.

The prototype must demonstrate:

Search request
→ Total Error
→ Retry
→ Results Available

Preserve all existing validated SCR-SEARCH-002 screens, Domain variants, filters, pagination behavior, Empty Query Validation, No Result state, navigation, and Low-Fidelity visual structure.

Do not redesign or rebuild the existing prototype.

---

## 1. Scope

This change applies only to SCR-SEARCH-002, the domain-specific search result screen.

The supported Domain variants remain:

- Community
- Welfare & Support Information
- Hospitals & Professional Institutions
- Assistive Devices

Do not add Partial Error to SCR-SEARCH-002.

Partial Error belongs only to SCR-SEARCH-001, where multiple Domain previews are composed on one screen.

---

## 2. Representative Mock Error Trigger

Create one deterministic prototype-only way to enter the Total Error state.

Use the following query as the representative mock trigger:

"검색오류테스트"

When this query is submitted from a SCR-SEARCH-002 Domain variant:

- Keep the user on SCR-SEARCH-002.
- Keep the submitted query visible in the Search Field.
- Keep the current Search Area selected.
- Keep the current Domain-specific Filter controls visible.
- Do not display stale result items from the previous query.
- Do not display pagination.
- Replace the result area with the Total Error state.

This trigger exists only for Figma prototype verification.

Do not dynamically interpret arbitrary queries as errors.

---

## 3. Total Error State

Display a clear error state in the result content area.

Use the primary Korean message:

"검색 결과를 불러오지 못했습니다."

Use supporting text such as:

"잠시 후 다시 시도해 주세요."

Provide a visible Retry action labeled:

"다시 시도"

The Retry action must be clearly distinguishable as an actionable control.

Do not use a modal or dialog for this state.

Do not navigate to a separate error page.

Do not replace the entire application shell with an error screen.

The Header, Search Field, Search Area navigation, current Domain context, and Footer should remain available.

---

## 4. Retry Interaction

When the user activates:

"다시 시도"

simulate successful recovery.

For this representative prototype flow:

Total Error
→ Retry
→ Results Available

After Retry:

- Remove the Total Error message.
- Restore a valid representative result set for the current Domain.
- Restore pagination when the representative result set requires it.
- Keep the user on the same Search Area.
- Preserve the existing SCR-SEARCH-002 information hierarchy.

It is acceptable for the prototype to use static Mock Data after Retry.

Do not attempt a real network retry.

---

## 5. Error State vs No Result

Keep Total Error visually and behaviorally distinct from the existing No Result state.

No Result means:

Valid Search Request
→ successful response
→ zero results

No Result uses:

"검색 결과가 없습니다."

and must NOT provide Retry.

Total Error means:

Search Request
→ request/result retrieval failure

Total Error uses:

"검색 결과를 불러오지 못했습니다."

and MUST provide:

"다시 시도"

Do not merge these two states.

---

## 6. Error State vs Empty Query Validation

Do not change the existing Empty Query Validation behavior.

Empty Query remains:

Empty or blank Query
→ Field-level Validation Error
→ no Search Request
→ no Loading
→ no Navigation
→ existing context preserved

The existing validation message remains:

"검색어를 입력해 주세요."

Do not treat an Empty Query as Total Error.

---

## 7. Filters and Pagination During Total Error

Keep the current Domain-specific Filter controls visible so the user can understand the current Search context.

However:

- Do not display stale result items.
- Do not display a result count based on stale data.
- Do not display pagination while Total Error is active.

After successful Retry, restore the appropriate result information and pagination.

Do not add any Sort control.

---

## 8. Accessibility

The Total Error state must not rely on color alone.

The failure must be communicated with explicit text.

The Retry action must:

- have a clear accessible name,
- be keyboard operable,
- have a visible focus state.

The error message and Retry action should have a clear semantic and visual relationship.

Do not introduce unnecessary animation.

---

## 9. Low-Fidelity Constraint

Keep this implementation Low-Fidelity.

Do not introduce:

- decorative illustrations,
- elaborate error graphics,
- animation-heavy loading patterns,
- new visual design systems,
- new navigation patterns,
- new Product Decisions.

Use the existing typography, spacing, controls, borders, and visual language already established in the Search prototype.

---

## 10. Do Not Change

Do not change the already validated:

- SCR-SEARCH-001 structure,
- SCR-SEARCH-002 Domain variants,
- Search Area navigation,
- Domain-specific Filter contracts,
- Single-select behavior,
- AND Filter relationship,
- Individual Reset,
- Reset All,
- Page-number Pagination,
- Query change reset behavior,
- Search Area change reset behavior,
- Empty Query Validation,
- No Result state,
- Result item metadata,
- Result → Original Detail navigation,
- Header,
- GNB,
- Footer.

Do not add:

- Partial Error to SCR-SEARCH-002,
- user Sort controls,
- Infinite Scroll,
- Load More pagination,
- AI Search,
- personalized Search,
- location-based Search,
- commerce features.

---

## 11. Expected Prototype Verification Flow

The final prototype should allow this representative test:

1. Open any SCR-SEARCH-002 Domain variant.
2. Enter "검색오류테스트".
3. Submit Search.
4. Remain on the same Domain Search Area.
5. Show the Total Error state.
6. Show:
   "검색 결과를 불러오지 못했습니다."
7. Show supporting guidance.
8. Show the "다시 시도" action.
9. Do not show stale results or pagination.
10. Activate "다시 시도".
11. Recover to Results Available.
12. Remain within the same SCR-SEARCH-002 Domain context.

This is a deterministic prototype mock for state verification only.

Actual API failure detection, HTTP error handling, retry requests, Loading behavior, and backend recovery will be implemented and verified later during Frontend/API integration.