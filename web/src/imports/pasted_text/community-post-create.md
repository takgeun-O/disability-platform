Extend the **existing desktop low-fidelity prototype** by creating a new **Community Post Create** screen.

This is the same Korean accessibility-focused community + information platform as the existing:

* Home
* Community Home
* Post List
* Post Detail

Create the new Post Create screen as the next step in the existing prototype.

All user-facing UI text must remain in **Korean**.

---

# 1. Critical Prototype Preservation Requirements

Preserve all existing screens.

Do NOT redesign:

* Home
* Community Home
* Post List
* Post Detail
* shared Header
* shared Global Navigation
* shared Footer

Do not introduce a new visual language.

Create Post Create as a **separate new screen/route**.

Existing navigation and layout conventions should be reused.

---

# 2. Screen Role

Create the **게시글 작성** screen.

Screen purpose:

* allow an authenticated Community member to create a new post
* select one Post Type
* select one Primary Topic
* enter a title
* enter body content
* optionally attach images
* validate required input
* submit the post
* cancel and return to the previous context

This is a Form screen.

Do not treat it as:

* Post Detail
* Post Edit
* social-media composer
* rich content editor
* dashboard

---

# 3. Authentication Requirement

Post Create requires login.

Logged-out flow:

**글쓰기
→ 로그인
→ 인증 성공
→ Post Create**

After login, return to the requested Post Create context.

Do not send the user to Home after successful authentication.

Direct access to the Post Create route while logged out should conceptually require authentication.

---

# 4. Shared Header

Reuse the existing shared Header.

Keep:

* Logo
* Global Search
* authentication/account area

Do not create a Post Create-specific Header.

---

# 5. Global Navigation

Reuse the existing Global Navigation.

Keep:

* 홈
* 커뮤니티
* 복지·지원정보
* 병원·전문기관
* 보조기기

Indicate `커뮤니티` as the current section.

Keep:

* Logo → Home
* `홈` → Home
* `커뮤니티` → Community Home

Do not create local Board Navigation.

---

# 6. No Breadcrumb

Do **not** add Breadcrumb navigation to Post Create.

The official Breadcrumb pattern currently applies to Detail screens such as Post Detail.

Do not add:

* `커뮤니티 > 전체 게시글 > 글쓰기`
* `커뮤니티 > 글쓰기`

Post Create navigation is handled by:

* Global Navigation
* Cancel
* Browser Back
* authentication return context

---

# 7. Page Header

Use:

# 게시글 작성

as the single H1.

Do not rename it to:

* 새 글
* 글쓰기 Editor
* 포스트 만들기
* 새 게시글

Do not add a large explanatory Hero.

Keep the Page Header simple and functional.

---

# 8. Form Structure

Use a vertically structured Form.

Recommended order:

1. 글 유형
2. 주제
3. 제목
4. 내용
5. 이미지 첨부 — 선택
6. Action Area

   * 취소
   * 등록

Keep all fields within a centered readable Form container.

Do not use a Sidebar.

---

# 9. Post Type

Label:

**글 유형**

Required.

Use exactly these options:

* 일반
* 질문·답변
* 경험·후기

Only one may be selected.

For Low-Fidelity Desktop, use an accessible **Radio Group** or an equivalent single-choice pattern.

Do not add:

* 정보
* Board
* Category
* Tag

---

# 10. Post Type Default State

If the user enters Post Create without any Community context:

* no Post Type should be preselected

Do not automatically select `일반`.

The system should not assume user intent.

---

# 11. Post Type Context Pre-fill

If Post Create was entered from a filtered Community context, pre-fill the corresponding Post Type.

Example:

**Post List
Post Type = 질문·답변
→ 글쓰기
→ Post Create
→ 질문·답변 already selected**

The user may still change the selected value.

Do not force the user to select the same value again.

---

# 12. Primary Topic

Label:

**주제**

Required.

Use exactly these MVP topics:

* 자유·일상
* 보청기
* 인공와우
* 치료·재활
* 의사소통
* 취업·직장
* 복지·생활

Only one Primary Topic may be selected.

For Desktop Low-Fidelity, prefer a simple single **Select** control.

Do not represent Topic as:

* Board
* Category
* multi-select Tags
* large cards

---

# 13. Topic Default State

If there is no incoming context:

* Topic remains unselected

Do not automatically select `자유·일상`.

---

# 14. Topic Context Pre-fill

If the user enters from a Topic-specific Post List or Community Home context, pre-fill that Topic.

Example:

**Topic = 인공와우
→ 글쓰기
→ Post Create
→ 인공와우 already selected**

The user may still change the Topic before submitting.

---

# 15. Post Type and Topic Are Different Concepts

Keep these controls visually and structurally distinct.

**글 유형**
= what kind of post the user is writing

**주제**
= what the post is about

Do not merge them into one category selector.

Do not add long explanatory text unless needed for basic clarity.

---

# 16. Question Post Behavior

If the user selects:

**질문·답변**

do NOT add extra fields.

Do not add:

* Question Status Selector
* 답변 대기 Selector
* 해결됨 Selector
* accepted answer
* best answer
* answer-selection UI

New QUESTION posts automatically start as:

**OPEN → 답변 대기**

after creation.

Question Status should not be editable during Post Create.

---

# 17. Title Field

Label:

**제목**

Required.

Use a standard single-line text field.

Do not rely on placeholder text as the label.

Conceptually validate:

* required
* not blank-only
* minimum / maximum length

Do not display exact character limits because the final Product Contract does not yet define the numbers.

Do not add a character counter in the default Low-Fidelity screen.

---

# 18. Body Field

Label:

**내용**

Required.

Use a multi-line Textarea.

Support plain text and line breaks.

Do not add a Rich Text toolbar.

Do not add:

* Bold
* Heading controls
* Quote
* Link toolbar
* Lists
* Code
* Table
* Embed
* Emoji picker

Do not create an advanced editor.

---

# 19. Image Attachment

Provide a separate optional section:

**이미지 첨부 (선택)**

This is separate from the body Textarea.

Support conceptually:

* file selection
* JPG / JPEG / PNG / WEBP
* image preview
* upload progress
* upload failure
* retry
* remove image

A Desktop Drag & Drop area may exist only as a supplementary method.

Always keep a clear file-selection control available.

Do not require Drag & Drop.

---

# 20. Image Privacy Guidance

The attachment area may include a short restrained warning that users should avoid exposing sensitive information in images.

Examples of possible concerns:

* 위치
* 기기 식별번호
* 개인정보

Keep this guidance short.

Do not create a large warning banner.

---

# 21. Image Attachment Exclusions

Do not add:

* PDF
* documents
* video
* audio
* GIF picker
* image editor
* crop tool
* advanced image ordering
* image insertion inside body text

Do not finalize:

* maximum file size
* maximum image count

because those numbers are not yet final Product contracts.

---

# 22. No Tag Field

Tag is Phase 2+.

Do NOT add:

* Tag Field
* 추천 Tag
* 자동 Tag
* hashtag UI

---

# 23. Default Screen State

The primary Post Create frame should represent:

* Post Type: unselected
* Topic: unselected
* Title: empty
* Body: empty
* Image: none
* Submit: Disabled or clearly unavailable until required data is valid

Do not pre-fill:

* GENERAL
* 자유·일상

without incoming context.

---

# 24. Context Pre-filled Variant

The architecture should support an additional state such as:

**질문·답변 + 인공와우**

already selected.

This represents entry from a filtered Community context.

Do not create a completely separate screen.

Treat it as the same Post Create with pre-filled state.

---

# 25. Primary and Secondary Actions

At the bottom of the Form:

### Secondary

**취소**

### Primary

**등록**

Do not use:

* 게시글 등록
* 게시
* 작성 완료
* 저장

as the primary label.

Official label:

**등록**

---

# 26. Submit Disabled State

In the empty Default State, `등록` may appear disabled because required fields are incomplete.

Do not finalize the exact enable/disable implementation details in Low-Fidelity.

The visual relationship should simply communicate:

Required information is incomplete → submit not currently available.

---

# 27. Submit Processing

Support a Processing state.

Conceptually:

**등록 → 등록 중…**

During Processing:

* prevent duplicate submission
* keep entered data visible
* avoid turning the entire page into a blocking Loading screen

If images are uploading, individual image upload states may remain visible.

---

# 28. Successful Submission

Success flow:

**Post Create
→ 등록 성공
→ newly created Post Detail**

Do not add:

* Success page
* Community Home redirect
* Post List redirect

The created Post Detail is the confirmation result.

Do not add a mandatory success Toast in the primary Low-Fidelity flow.

---

# 29. Cancel

`취소` is a secondary action.

If no Form changes have been made:

* immediately return to the entry context

Examples:

Community Home → Post Create → 취소 → Community Home

Post List → Post Create → 취소 → previous Post List context

Preserve browsing state where appropriate.

---

# 30. Unsaved Changes Protection

Unsaved-changes protection is an MVP requirement.

If the user has actually modified the Form and attempts to leave:

* 취소
* Logo
* GNB
* Browser Back
* another navigation link

show an Unsaved Changes confirmation dialog.

Do not show the dialog when the Form is still untouched.

---

# 31. Unsaved Changes Dialog

Create or prepare this overlay:

**작성 중인 내용이 있습니다.**

**페이지를 나가면 입력한 내용이 사라집니다.**

Actions:

### Primary/continuation

**계속 작성**

### Destructive navigation

**나가기**

`계속 작성`
→ close Dialog
→ return focus to Form context

`나가기`
→ discard unsaved data
→ continue to the selected destination

Do not add:

* 임시저장
* Draft 저장
* 자동저장

to this Dialog.

---

# 32. Draft / Auto-save Exclusion

Do NOT add in MVP:

* 임시저장
* 자동저장
* Draft 목록
* 이어쓰기
* `자동 저장됨`

These are later-phase features.

The MVP protection mechanism is:

**Unsaved Changes warning**

---

# 33. Validation Errors

Support Field-level errors.

Possible Korean messages:

* 글 유형을 선택해 주세요.
* 주제를 선택해 주세요.
* 제목을 입력해 주세요.
* 내용을 입력해 주세요.
* 지원하지 않는 이미지 형식입니다.

Use Text Error near the relevant field.

Do not rely on:

* red border only
* color only
* icon only

Do not clear valid user input after validation failure.

---

# 34. Validation Timing

Avoid excessive immediate errors while the user is typing.

The architecture may support:

* helpful validation after leaving a field
* full validation on `등록`

Do not show all validation errors in the Default empty screen.

Create Validation Error as a separate state/variant if necessary.

---

# 35. Upload Error

Support an Upload Error variant.

When one image upload fails:

* keep title
* keep body
* keep other fields
* identify the failed image
* allow retry
* allow removal

Do not reset the full Form.

---

# 36. Network / Server Error

If submission fails:

* remain on Post Create
* preserve entered data
* show understandable feedback
* allow retry

Do not redirect to an Error page for ordinary submit failure.

Do not expose backend exception names or internal codes.

---

# 37. Authentication Expiration

The architecture should support:

**Post Create
→ authentication expires
→ Login required
→ re-login
→ Post Create return
→ existing input preserved where possible
→ user submits again**

Do not automatically submit after re-login.

Do not wipe Form contents by default.

---

# 38. Required / Optional Labels

Make required and optional status understandable through text/semantic structure.

Do not rely only on:

* color
* asterisk

The image field should clearly communicate:

**이미지 첨부 (선택)**

Required Form fields should remain clearly identifiable.

---

# 39. Accessibility

Support:

* one clear H1
* persistent Field Labels
* semantic required state
* label/helper/error relationships
* keyboard-operable Radio Group
* keyboard-operable Select
* keyboard-accessible File Input
* visible focus states
* logical Tab order
* accessible image-remove/retry controls
* accessible Processing feedback
* accessible Error feedback
* Unsaved Changes Dialog focus management
* text zoom and reflow
* no mouse-only Drag & Drop requirement

Do not use placeholder-only Field identification.

---

# 40. Keyboard Order

Conceptual focus order:

**Header / GNB
→ H1
→ 글 유형
→ 주제
→ 제목
→ 내용
→ 이미지 첨부
→ image actions if present
→ 취소
→ 등록
→ Footer**

Do not create Keyboard Traps.

Textarea Enter should remain normal text entry.

Do not make Enter automatically submit the Form.

---

# 41. Desktop Layout

This iteration is Desktop-first.

Use:

**Shared Header
Global Navigation**

then:

**게시글 작성**

then a centered Form container:

**글 유형**

**주제**

**제목**

**내용**

**이미지 첨부 (선택)**

**취소　　　　등록**

then:

**Footer**

Use a restrained vertical Form layout.

Do not add Sidebar content.

---

# 42. Form Width

Use a readable constrained Form width consistent with the existing product.

Do not stretch all text inputs across the entire desktop viewport.

Do not finalize exact max-width pixels yet.

---

# 43. Existing Prototype Navigation

Preserve:

**Home**

**Community Home**

**Post List**

**Post Detail**

Add:

**Post Create**

Connect:

### Community Home

`글쓰기` → authentication if required → Post Create

### Post List

`글쓰기` → authentication if required → Post Create

### Post Create

`취소` → previous entry context

`등록` → newly created Post Detail

Logo / GNB navigation → apply Unsaved Changes protection if the Form is dirty.

---

# 44. Preserve Existing Screens

Do not modify or redesign existing screens in this iteration.

Only add required navigation connections.

Do not change:

* Home layout
* Community Home layout
* Post List layout
* Post Detail layout

---

# 45. Low-Fidelity State Coverage

Prioritize these Post Create states:

### Primary

**SCR-COM-004 / Default**

### Useful variants

* Context Pre-filled
* Validation Error
* Processing
* Upload Error
* Authentication Required
* Unsaved Changes Dialog

These do not need to become independent site pages.

Use the same Form as states/component variants where possible.

---

# 46. Explicit Exclusions

Do NOT add:

* Breadcrumb
* Board
* Community Category
* Tag
* Disability Type
* Community Space
* Question Status Selector
* CLOSED
* 답변 완료
* accepted answer
* Rating
* product selector
* institution selector
* Rich Text toolbar
* Emoji picker
* GIF picker
* PDF attachment
* document attachment
* video upload
* audio upload
* AI writing assistant
* AI autocomplete
* AI summary
* automatic Topic suggestion
* manual Draft
* Auto-save
* Sidebar
* popular posts
* recommended posts
* advertisements
* Phase 2+ placeholders

---

# 47. Low-Fidelity Scope

Do not finalize:

* brand colors
* final typography
* exact spacing tokens
* exact grid
* exact pixel values
* border radius
* shadows
* final icons
* animation
* responsive breakpoints
* exact validation-length numbers
* exact upload-size/count limits

Focus only on:

**Form hierarchy
→ required/optional inputs
→ Post Type / Topic
→ Title / Body
→ Image Attachment
→ Validation
→ Submit / Cancel
→ Unsaved Changes
→ Authentication
→ navigation continuity
→ accessibility**

---

# Final Goal

Create a **Desktop Low-Fidelity Community Post Create** screen that feels like a natural continuation of the existing Community experience.

The screen should answer:

**“What do I need to enter, what is required, how do I publish it, and what happens if I leave before finishing?”**

Prioritize:

**clear Form hierarchy
→ Post Type / Topic distinction
→ simple writing experience
→ safe image attachment
→ accessible Validation
→ clear 등록 / 취소 hierarchy
→ unsaved-changes protection
→ preservation of existing prototype navigation**

Create only Post Create and its necessary state variants in this iteration.

Preserve all existing screens.
