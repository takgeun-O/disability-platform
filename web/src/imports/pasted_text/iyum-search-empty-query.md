Update the existing IYUM Search Low-Fidelity prototype to support the finalized Empty Query Validation behavior.

This is a focused revision only.

Do NOT redesign the Home page or Search Results page.
Do NOT change the existing Search information architecture, result layout, Search Areas, domain previews, typography hierarchy, spacing system, or overall Low-Fidelity visual style.

The purpose of this revision is only to add the finalized Empty Query Validation behavior consistently to the existing search interactions.

---

## 1. Empty Query Validation

Apply the same validation rule to:

- the Global Search field on Home
- the Search field on SCR-SEARCH-001
- any existing Search Result search interaction that uses the same search component

Treat the query as empty when:

- the field contains no text, or
- the field contains only whitespace

When the user attempts to submit an empty query:

- do NOT navigate to the Search Results page
- do NOT replace the current page or current search results
- do NOT enter a Loading state
- keep the user on the current screen
- keep focus on the Search field when possible
- show a field-level validation message near the Search field

Use this exact Korean validation message:

"검색어를 입력해 주세요."

---

## 2. Submission Methods

The same Empty Query Validation must apply regardless of how search is submitted.

Support the same behavior for:

- clicking the Search button
- pressing Enter while focused on the Search field, if the prototype supports keyboard submission

Do not create different validation behavior for Click and Enter.

---

## 3. Home Search Behavior

On the existing Home page:

Empty Search Field
→ user selects Search or presses Enter
→ remain on Home
→ show "검색어를 입력해 주세요."
→ do not navigate to SCR-SEARCH-001

Once the user enters a valid query:

- clear the validation error
- allow the existing search flow to proceed normally
- preserve the current valid-query navigation behavior

Do not modify the existing successful search interaction.

---

## 4. SCR-SEARCH-001 Behavior

On the existing Search Results page:

If the user clears the current query and submits the empty Search field:

- remain on SCR-SEARCH-001
- preserve the existing displayed results
- do not replace the results with an empty state
- do not navigate elsewhere
- do not simulate a new search
- show "검색어를 입력해 주세요." near the Search field

Once a valid query is entered:

- clear the validation error
- allow search submission again

Important:

Empty Query is NOT the same as No Result.

Do not create or show a No Result state for Empty Query.

---

## 5. Error Presentation

Use field-level validation.

The validation message must be visually associated with the Search field.

Use:

"검색어를 입력해 주세요."

The error must not rely on color alone.

A visible text message is required.

You may use a simple Low-Fidelity error border or error treatment on the Search field if it is consistent with the existing form validation patterns already used in this prototype.

Do NOT introduce:

- Toast
- Modal
- Dialog
- Snackbar
- Alert popup

for Empty Query Validation.

Do not over-design the error state.

Exact final color, iconography, animation, and Design System styling are not required at this Low-Fidelity stage.

---

## 6. Validation Recovery

When the user starts entering a valid non-blank query after the error:

- remove the Empty Query validation state
- return the Search field to its normal state
- allow normal search submission

Do not leave a stale error message after the query becomes valid.

---

## 7. Preserve Existing Search Design

Do not modify the existing:

- Search Area tabs
- "전체" selection
- Community preview
- Welfare & Support Information preview
- Hospitals & Professional Institutions preview
- Assistive Devices preview
- result metadata
- Question Status badges
- domain "결과 더보기" links
- existing valid-query Search Results layout
- Header / GNB
- Footer

Do not add:

- new Search Areas
- filters
- sort controls
- pagination
- AI Search
- semantic search
- tags
- personalization
- advertising
- map UI
- new Search states unrelated to Empty Query Validation

---

## 8. Prototype Verification

After the revision, verify these interactions.

### Case A — Home Empty Query

Home
→ Search field is empty
→ click Search
→ remain on Home
→ show "검색어를 입력해 주세요."
→ no Search Results navigation

### Case B — Home Valid Query

Home
→ enter "인공와우"
→ submit Search
→ existing Search Results flow works normally

### Case C — Search Results Empty Query

SCR-SEARCH-001 with existing results
→ clear the Search field
→ submit Search
→ remain on SCR-SEARCH-001
→ preserve existing results
→ show "검색어를 입력해 주세요."

### Case D — Validation Recovery

Empty Query error is visible
→ enter a valid query
→ validation error disappears
→ Search can be submitted normally

### Case E — Keyboard Submission

If keyboard interaction is supported:

Empty Search Field
→ press Enter
→ same behavior as clicking Search

---

## 9. Important Prototype Boundary

This Low-Fidelity prototype only needs to demonstrate the UX behavior.

Do not attempt to implement real API validation, actual network request prevention, backend validation, or production search state management.

Those behaviors will be verified during Frontend/API implementation.

The prototype must visually and interactively communicate this contract:

Empty / Blank Query
→ Validation Error
→ No Loading
→ No Navigation
→ Current Context Preserved

Valid Query
→ Existing Search Flow

Make only the minimum changes required to support this contract.