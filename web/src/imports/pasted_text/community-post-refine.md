Refine the existing Community Post Create screen rather than redesigning it.

This is a SECOND-PASS refinement of the existing Desktop Low-Fidelity Post Create screen.

The current overall structure, form hierarchy, visual language, navigation shell, image attachment behavior, validation behavior, and context pre-fill behavior are already correct and should be preserved.

Do NOT redesign the screen from scratch.

All user-facing UI text must remain in Korean.

---

# 1. Preserve the Existing Post Create Structure

Keep the current structure exactly as the foundation:

Shared Header
→ Global Navigation
→ 게시글 작성
→ 글 유형
→ 주제
→ 제목
→ 내용
→ 이미지 첨부
→ 취소 / 등록
→ Footer

Do not add new sections.

Do not add a Breadcrumb.

Do not introduce a Sidebar.

Do not change the existing low-fidelity visual language.

---

# 2. Preserve the Existing Form Controls

Keep the current Post Type Radio Group:

- 일반
- 질문·답변
- 경험·후기

Keep the current Topic Select with the existing MVP topics.

Keep:

- 제목 as a single-line text field
- 내용 as a multi-line textarea
- 이미지 첨부 as an optional file attachment area
- 취소 as the secondary action
- 등록 as the primary action

Do not change these controls into cards, tabs, chips, or other patterns.

---

# 3. Preserve Context Pre-fill Behavior

The current context-aware pre-fill behavior is correct.

Preserve it.

If the user enters Post Create from a filtered Community context, the corresponding Post Type and/or Topic should remain pre-filled.

For example:

Post List
질문·답변 + 인공와우
→ 글쓰기
→ Post Create
→ 질문·답변 + 인공와우 pre-filled

The user must still be allowed to change the pre-filled values.

Do not reset or remove this behavior.

If there is no incoming Community context, do not invent a default Post Type or Topic.

---

# 4. Preserve Submit Validation Behavior

The current submit validation behavior is correct.

Preserve it.

When required fields are incomplete, the `등록` action should remain unavailable or disabled as currently implemented.

Required fields are:

- 글 유형
- 주제
- 제목
- 내용

Do not weaken the current validation behavior.

Do not redesign the validation system.

---

# 5. Remove the Redundant Required-Field Note

Remove the bottom helper text:

`* 표시 항목은 필수 입력 사항입니다.`

This explanation is redundant because each field already communicates its state directly using:

`(필수)`

or:

`(선택)`

Continue using explicit field-level labels such as:

- 글 유형 (필수)
- 주제 (필수)
- 제목 (필수)
- 내용 (필수)
- 이미지 첨부 (선택)

Do not replace these labels with asterisk-only notation.

Do not rely on color alone to communicate required status.

---

# 6. Preserve the Existing Image Attachment Experience

The current image attachment structure is good and should remain.

Keep:

- 이미지 첨부 (선택)
- file selection control
- image preview
- image removal
- supported-format guidance
- short privacy guidance

Do not enlarge the attachment area unnecessarily.

Do not turn it into a large upload dashboard.

If supported-format text is shown, use:

`JPG, JPEG, PNG, WEBP 형식을 지원합니다.`

Keep the privacy guidance concise.

Do not add additional upload functionality.

---

# 7. Main Interaction Fix: Protect Dirty Forms Across ALL Navigation Exits

The current Unsaved Changes Dialog works correctly when the user selects `취소`.

Extend the SAME dirty-form protection consistently to every navigation action that would leave Post Create.

If the form has been modified, attempting any of the following must trigger the Unsaved Changes Dialog BEFORE navigation occurs:

- 취소
- Logo
- 홈 in Global Navigation
- 커뮤니티 in Global Navigation
- 복지·지원정보 in Global Navigation
- 병원·전문기관 in Global Navigation
- 보조기기 in Global Navigation
- authentication/account navigation if it leaves the page
- Footer navigation links
- Browser Back
- any other internal navigation that leaves Post Create

The user must not lose entered content immediately when selecting these navigation actions.

Do not navigate first and then show the Dialog.

The Dialog must interrupt the attempted navigation.

---

# 8. Dirty Form Definition

Only show the Unsaved Changes Dialog when the form is actually dirty.

The form becomes dirty when the user changes meaningful form data, such as:

- Post Type
- Topic
- Title
- Body
- Image attachment

Context-pre-filled values alone should not automatically be treated as a user modification.

If the user enters Post Create with pre-filled Post Type or Topic and makes no changes, navigation should not unnecessarily trigger the Unsaved Changes Dialog.

The warning exists to protect actual user edits.

---

# 9. Reuse the Existing Unsaved Changes Dialog

Do not create a new Dialog design.

Reuse the existing Dialog that already appears when `취소` is selected on a dirty form.

Keep the existing concept:

Title:

`작성 중인 내용이 있습니다.`

Description:

`페이지를 나가면 입력한 내용이 사라집니다.`

Actions:

`계속 작성`

`나가기`

Do not add:

- 임시저장
- 저장 후 나가기
- 자동저장
- Draft

---

# 10. Continue Writing Behavior

When the user selects:

`계속 작성`

the system should:

- close the Dialog
- cancel the attempted navigation
- remain on Post Create
- preserve all entered data
- preserve attached images
- return focus appropriately to the previous interaction context

Do not reset any form values.

---

# 11. Leave Behavior

When the user selects:

`나가기`

the system should:

- discard unsaved changes
- continue to the originally requested destination

The destination must depend on the action that originally triggered the Dialog.

Examples:

Dirty Post Create
→ 홈
→ Dialog
→ 나가기
→ Home

Dirty Post Create
→ 커뮤니티
→ Dialog
→ 나가기
→ Community Home

Dirty Post Create
→ Logo
→ Dialog
→ 나가기
→ Home

Dirty Post Create
→ Browser Back
→ Dialog
→ 나가기
→ previous browser-history destination

Dirty Post Create
→ 취소
→ Dialog
→ 나가기
→ original entry context

Do not always send the user to one fixed page.

Preserve the original navigation intent.

---

# 12. Browser Back Is Especially Important

Browser Back must participate in the same unsaved-changes protection.

Current incorrect behavior:

Dirty Post Create
→ Browser Back
→ immediate navigation
→ entered content lost

Required behavior:

Dirty Post Create
→ Browser Back
→ Unsaved Changes Dialog

Then:

`계속 작성`
→ stay on Post Create

or:

`나가기`
→ perform the intended Back navigation

Do not replace Browser Back with an artificial on-page Back button.

No new Back button is required for Post Create.

---

# 13. Clean Form Navigation

If the user has not changed the form, navigation should remain immediate.

Examples:

Untouched Post Create
→ 홈
→ Home

Untouched Post Create
→ 커뮤니티
→ Community Home

Untouched Post Create
→ Browser Back
→ previous page

Untouched Post Create
→ 취소
→ entry context

Do not show an unnecessary confirmation Dialog for a clean form.

---

# 14. Successful Submission Must Not Trigger the Leave Warning

After successful submission:

Post Create
→ 등록
→ success
→ newly created Post Detail

This transition should not trigger the Unsaved Changes Dialog.

Successful submission resolves the dirty state.

Do not treat the redirect to Post Detail as accidental navigation away from an unsaved form.

---

# 15. Preserve Form Data During Validation and Errors

Do not change the existing data-preservation behavior.

If:

- validation fails
- image upload fails
- network submission fails

keep the user's entered form data.

Do not trigger the Unsaved Changes Dialog merely because an error occurs.

The Dialog is specifically for attempted navigation away from a dirty form.

---

# 16. Accessibility for the Unsaved Changes Dialog

Ensure the existing Dialog can support:

- clear Dialog title
- understandable description
- keyboard navigation
- visible focus
- initial focus on a safe action
- focus containment while open
- Escape behavior if appropriate
- focus restoration when `계속 작성` is selected

`계속 작성` should remain the safe action.

`나가기` should clearly communicate that the user is choosing to discard the current unsaved work and leave.

Do not communicate this distinction through color alone.

---

# 17. Preserve Existing Visual Hierarchy

Do not visually redesign the form.

Keep the current restrained Desktop Low-Fidelity layout.

Preserve:

- centered readable form width
- vertical field hierarchy
- simple Radio Group
- simple Select
- large readable Body textarea
- compact image attachment area
- secondary `취소`
- primary `등록`

Do not add decorative cards around each field.

Do not add:

- shadows
- gradients
- illustrations
- large icons
- promotional elements
- excessive background containers

---

# 18. Do Not Add New Features

Do not add:

- Breadcrumb
- manual Back button
- Draft
- 임시저장
- Auto-save
- Rich Text Editor
- Tags
- Board
- Community Category
- Question Status selector
- AI writing
- AI suggestions
- image editor
- additional attachment types
- Sidebar
- preview mode
- future-feature placeholders

This is a refinement, not a feature expansion.

---

# 19. Do Not Modify Existing Screens

Preserve the existing:

- Home
- Community Home
- Post List
- Post Detail

Do not visually redesign them.

Only modify navigation behavior where necessary so that attempts to leave a dirty Post Create screen correctly trigger the existing Unsaved Changes Dialog.

---

# Final Goal

Refine the existing Post Create screen without redesigning it.

The primary goal of this iteration is:

Dirty Post Create
→ any attempt to leave
→ Unsaved Changes Dialog
→ continue writing OR confirm navigation

The user must not accidentally lose a partially written post through:

- GNB navigation
- Logo navigation
- Footer navigation
- Browser Back
- Cancel
- or any other internal navigation.

At the same time:

- preserve the current successful validation behavior
- preserve context-aware Post Type / Topic pre-fill
- preserve the current image attachment experience
- remove the redundant bottom required-field note
- keep the existing low-fidelity structure unchanged

Modify only what is necessary for these refinements.