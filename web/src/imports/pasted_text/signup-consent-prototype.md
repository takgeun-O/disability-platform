# IYUM — SCR-AUTH-002 Sign-up Start · Consent
## Desktop Low-Fidelity Prototype

### 1. Objective

Create the Desktop Low-Fidelity prototype for:

- Screen ID: SCR-AUTH-002
- Screen Name: Sign-up Start · Consent
- Korean screen heading: 회원가입
- Type: Process
- Access: Public
- MVP: Required
- Phase: Phase 1
- Layout: Form Layout
- Responsive: Common
- Presentation: Full-page screen, not a modal

This screen represents only the consent step at the beginning of the sign-up process.

The prototype must validate:

- consent information hierarchy,
- required vs. optional consent,
- overall consent behavior,
- individual consent behavior,
- the difference between Required Consent Ready and All Consent,
- required-consent validation,
- progression to SCR-AUTH-003,
- basic accessibility,
- and the Desktop information hierarchy.

Do not redesign the existing IYUM product shell.
Reuse the existing public shell and visual language already present in the current prototype.

This is a Low-Fidelity prototype.
Do not turn this task into a High-Fidelity redesign.

---

# 2. Critical Screen Boundary

SCR-AUTH-002 is ONLY the consent step.

It must NOT contain:

- email input,
- email duplication check,
- password input,
- password confirmation,
- nickname input,
- name,
- phone number,
- birth date,
- gender,
- address,
- disability information,
- health or medical information,
- assistive device information,
- member type selection,
- backend role selection,
- age verification,
- guardian consent,
- phone verification,
- identity verification,
- email verification code,
- account creation completion UI,
- social sign-up,
- OAuth,
- Kakao,
- Naver,
- Google,
- Apple,
- MFA,
- passkeys,
- login persistence controls,
- session UI,
- JWT or token UI.

Do not merge SCR-AUTH-002 with SCR-AUTH-003.

Member information belongs to:

SCR-AUTH-003 — 회원정보 입력

Do not merge email verification into this screen.

Do not create any new sign-up step or Screen ID.

---

# 3. Existing Public Shell

Reuse the existing IYUM public shell.

Use:

- existing public header,
- existing logo,
- existing global navigation,
- existing logged-out account area,
- existing footer.

Do not create:

- a minimal auth-only header,
- a sign-up-specific header,
- a new navigation system,
- breadcrumbs,
- a modal,
- a dialog,
- a marketing hero,
- a sidebar.

Keep the shell visually consistent with the existing Home, Search, and Login prototype screens.

Do not redesign unrelated header or footer behavior.

---

# 4. Main Page Structure

Create a focused, vertically structured consent process inside the Main content area.

Recommended information hierarchy:

Public Header
Global Navigation

Main
└── Sign-up Consent Process
    ├── Page Header
    │   └── 회원가입
    │
    ├── Agreement Section
    │   ├── Overall Consent
    │   │   └── 전체 동의
    │   │
    │   └── Individual Agreements
    │       ├── 이용약관 [필수]
    │       ├── 개인정보 수집 · 이용 [필수]
    │       └── 마케팅 정보 수신 동의 [선택]
    │
    ├── Validation / Feedback
    │
    └── Action Area
        ├── 취소
        └── 다음

Footer

Use a focused form-like content width appropriate for a Desktop process screen.

Do not make the agreement form unnecessarily wide.

Do not introduce an unrelated two-column layout.

---

# 5. Page Header

Required heading:

회원가입

Use one clear page heading.

A short supporting description may be used only if it helps clarify that the user must review required and optional consent before continuing.

Do not invent detailed marketing copy.

A sign-up Step Indicator is optional.

For the first Low-Fidelity implementation, prefer omitting the Step Indicator unless the existing prototype already has a clearly reusable sign-up process pattern.

Do not invent:

- an exact number of sign-up steps,
- “1/4” or similar step counts,
- new names for subsequent steps,
- a new Stepper component.

---

# 6. Agreement Structure

The official consent hierarchy is:

전체 동의

이용약관 [필수]

개인정보 수집 · 이용 [필수]

마케팅 정보 수신 동의 [선택]

Clearly separate:

1. Overall Consent
2. Individual Consent Items

The Overall Consent control is a convenience control.

It must NOT make optional consent required.

Required and optional status must be visible as text.

Do not rely only on color.

---

# 7. Consent Controls

Use checkbox controls.

## Overall Consent

Label:

전체 동의

Behavior:

- selecting 전체 동의 selects all currently displayed individual consent items,
- after selecting 전체 동의, the user can still deselect an individual item,
- deselecting an individual item changes only that individual item,
- the user must remain able to distinguish overall consent from required consent readiness.

Do not make an indeterminate checkbox state a required Product Contract.

If the implementation can support it safely without complicating the prototype, it may be used as a UI behavior, but do not make the prototype depend on it.

---

# 8. Required Consent — Terms of Service

Label:

이용약관

Status:

[필수]

Control:

Checkbox

Behavior:

- user can select or deselect it independently,
- consent is required to proceed,
- if this item is not selected, the required-consent condition is not satisfied.

The required status must be understandable without relying on color.

---

# 9. Required Consent — Privacy Collection and Use

Label:

개인정보 수집 · 이용

Status:

[필수]

Control:

Checkbox

Behavior:

- user can select or deselect it independently,
- consent is required to proceed,
- if this item is not selected, the required-consent condition is not satisfied.

The required status must be understandable without relying on color.

---

# 10. Optional Consent — Marketing

Label:

마케팅 정보 수신 동의

Status:

[선택]

Control:

Checkbox

Official initial state:

Unchecked / Not consented

This is optional.

The user MUST be able to continue sign-up without selecting this checkbox.

Never treat Marketing Consent as a required condition.

Do not display validation errors merely because Marketing Consent is unchecked.

---

# 11. Critical Consent Rule

The sign-up progression condition is:

이용약관 = Checked

AND

개인정보 수집 · 이용 = Checked

Marketing Consent may be either:

Checked

or

Unchecked

Therefore:

Required Consent Ready
IS NOT THE SAME AS
All Consent.

This distinction must be testable in the prototype.

The following state MUST allow progression:

이용약관 = Checked
개인정보 수집 · 이용 = Checked
마케팅 정보 수신 동의 = Unchecked

The following state must also allow progression:

이용약관 = Checked
개인정보 수집 · 이용 = Checked
마케팅 정보 수신 동의 = Checked

Do not require 전체 동의 itself to be selected in order to continue.

---

# 12. Initial Prototype State

For the first Low-Fidelity test fixture, display all consent checkboxes as unchecked.

This is primarily a prototype test fixture.

Marketing Consent being unchecked is an official Product Contract.

Do not interpret the unchecked initial representation of the two required items as a new Product Decision beyond this prototype fixture.

Initial state:

전체 동의 = Unchecked
이용약관 = Unchecked
개인정보 수집 · 이용 = Unchecked
마케팅 정보 수신 동의 = Unchecked

No validation error should be visible before the user attempts progression.

---

# 13. Required Prototype States

The prototype must support enough interaction to verify the following states.

## State A — Default / Initial

- all consent items visually unchecked,
- Marketing unchecked,
- no validation error,
- required-consent condition not satisfied.

## State B — Partial Consent

Representative example:

- 이용약관 = Checked
- 개인정보 수집 · 이용 = Unchecked
- Marketing = Unchecked

The user has not satisfied the required-consent condition.

## State C — Required Consent Ready

Required:

- 이용약관 = Checked
- 개인정보 수집 · 이용 = Checked

Marketing:

- Unchecked

The user MUST be allowed to continue.

This state is extremely important.

It proves that optional Marketing Consent is not required.

## State D — All Consent

- 전체 동의 selected
- 이용약관 = Checked
- 개인정보 수집 · 이용 = Checked
- Marketing = Checked

The user may continue.

## State E — Required Consent Error

At least one required consent item is unchecked when the user attempts to continue.

Expected behavior:

- remain on SCR-AUTH-002,
- do not navigate to SCR-AUTH-003,
- preserve current checkbox selections,
- show the required-consent error near the Agreement area.

Use this Korean error message:

필수 약관에 동의해야 가입할 수 있습니다.

Do not use a toast, modal, or dialog for this validation.

## State F — Success / Next Step

When both required consent items are selected:

다음
→ SCR-AUTH-003 회원정보 입력

Marketing Consent must not affect this navigation.

---

# 14. Next Action Behavior

Primary action:

다음

The Product Contract defines:

- progression is blocked until both required consent items are selected,
- progression is allowed when both required consent items are selected,
- Marketing Consent is irrelevant to progression eligibility.

However, the exact button-disabled policy is not finalized.

Do NOT turn one of the following implementations into a new Product Decision:

- disabled until valid,
- always enabled and validate on submit,
- hybrid behavior.

For this Low-Fidelity prototype, prioritize testability.

The user must be able to demonstrate:

1. an invalid progression attempt,
2. the required-consent error,
3. correction of the missing consent,
4. successful progression.

Therefore, ensure there is a practical interaction path that allows the Required Consent Error state to be tested.

Do not remove validation testing by making the invalid state completely impossible to submit.

---

# 15. Validation Behavior

When the user attempts to continue without all required consent:

Stay on:

SCR-AUTH-002

Do not navigate.

Preserve:

- 이용약관 checkbox state,
- 개인정보 수집 · 이용 checkbox state,
- Marketing checkbox state.

Show:

필수 약관에 동의해야 가입할 수 있습니다.

Place the error visually close to the Agreement group or progression context.

The error must not rely only on color.

Do not invent:

- exact error color,
- exact error icon,
- animation,
- a modal,
- a toast,
- a full-page error,
- a network error.

Do not automatically reset any consent selection after validation failure.

---

# 16. Error Recovery

After Required Consent Error:

- the user must be able to select the missing required consent item,
- existing selections must remain,
- once both required consent items are selected, the user can attempt 다음 again,
- successful progression then navigates to SCR-AUTH-003.

The error should no longer obstruct progression after the required condition is satisfied.

Do not reset Marketing Consent during error recovery.

---

# 17. Overall Consent Testability

The prototype must allow the following interaction test:

Initial
→ click 전체 동의
→ all individual items become selected

Then:

click 마케팅 정보 수신 동의 again
→ Marketing becomes unchecked
→ required items remain checked
→ user is still allowed to continue

This test is important because it verifies:

전체 동의
does not convert Marketing Consent into a required condition.

Also allow individual required consent items to be changed independently.

---

# 18. Agreement Detail Actions

Agreement-detail navigation is not the focus of this Low-Fidelity prototype.

Do not create new agreement-detail screens.

Do not invent:

- Terms modal,
- Privacy modal,
- Marketing modal,
- drawer,
- disclosure panel,
- legal text content,
- new Screen IDs.

If the existing prototype already has a simple reusable detail-link pattern, a non-connected descriptive link may be shown.

Otherwise, omit agreement-detail actions from this first implementation.

Do not use fake “준비 중입니다.” pages for these optional details.

---

# 19. Action Area

Use two actions.

Secondary:

취소

Primary:

다음

Make their hierarchy visually clear without using High-Fidelity styling.

## 취소

The Cancel destination is unresolved.

Therefore:

- show the action,
- do NOT navigate it to Home,
- do NOT navigate it to Login,
- do NOT navigate it to the previous page,
- do NOT create a placeholder destination,
- do NOT create a “준비 중입니다.” page.

It may remain non-connected in this prototype.

## 다음

Navigate to:

SCR-AUTH-003 회원정보 입력

only after the required-consent condition is satisfied.

---

# 20. SCR-AUTH-003 Prototype Destination

The primary goal is to verify the navigation concept:

SCR-AUTH-002
→ 다음
→ SCR-AUTH-003

If SCR-AUTH-003 already exists in the current prototype, navigate to that existing screen.

If SCR-AUTH-003 does not yet exist:

create only the minimum temporary prototype destination necessary to verify successful navigation.

The temporary destination must:

- clearly identify itself as SCR-AUTH-003,
- clearly display 회원정보 입력,
- remain minimal,
- not define member-information Product Decisions,
- not add new fields or business rules,
- not be treated as the actual SCR-AUTH-003 design.

Do not design the real SCR-AUTH-003 in this task.

---

# 21. Accessibility

The Low-Fidelity prototype must visibly support the following accessibility principles.

Use:

- one clear 회원가입 page heading,
- identifiable checkbox labels,
- explicit [필수] and [선택] text,
- visible checked / unchecked state,
- state cues beyond color,
- clear keyboard-focus indication,
- understandable Primary and Secondary actions,
- an error placed in meaningful relation to the Agreement group,
- sufficiently large interaction targets.

Checkbox labels should be associated with their controls.

The user should be able to understand:

- what is required,
- what is optional,
- what is currently selected,
- why progression failed.

Do not use placeholder text as a substitute for labels.

---

# 22. Desktop Low-Fidelity Layout

Use a clear Desktop form hierarchy.

Recommended composition:

[Existing Public Header]

[Existing Global Navigation]

Main Content

    회원가입

    [Agreement Process]

        □ 전체 동의

        ----------------

        □ 이용약관 [필수]

        □ 개인정보 수집 · 이용 [필수]

        □ 마케팅 정보 수신 동의 [선택]

        [Validation message when applicable]

        [취소] [다음]

[Existing Footer]

The exact visual arrangement may follow the existing IYUM Low-Fidelity language.

Prioritize:

- readability,
- hierarchy,
- clear grouping,
- interaction testability,
- accessibility.

Do not over-design.

---

# 23. Do Not Add Unapproved States

Do not copy Login states from SCR-AUTH-001.

Do NOT add the following to SCR-AUTH-002 unless they already exist as a separately approved Product Contract:

- Login Processing,
- Authentication Failure,
- Restricted Account,
- Login Success,
- credential errors,
- password errors,
- session errors.

Do not invent:

- sign-up Processing,
- network/system error,
- account restriction state,
- backend validation error,
- consent API loading state.

The required prototype state set is focused on consent interaction and required-consent validation.

---

# 24. Low-Fidelity Visual Constraints

Do not make final decisions about:

- brand colors,
- final color tokens,
- final typography,
- exact font sizes,
- exact spacing tokens,
- shadows,
- gradients,
- illustrations,
- decorative icons,
- animations,
- final border radius,
- exact responsive breakpoints.

Use the existing prototype's visual language.

This task validates:

- structure,
- hierarchy,
- controls,
- states,
- interactions,
- navigation,
- validation,
- accessibility.

---

# 25. Preserve Existing Work

Do not modify already validated unrelated prototype screens.

In particular:

- do not redesign SCR-AUTH-001 Login,
- do not redesign Home,
- do not redesign Search,
- do not change existing global navigation behavior unless required to reuse the shell,
- do not modify previously validated Login Validation,
- do not modify Login Authentication Failure,
- do not modify Login Processing,
- do not modify Login Restricted behavior,
- do not modify Login Network/System Error behavior.

This task should add or update only what is necessary for SCR-AUTH-002.

---

# 26. Required Interactive Test Scenarios

After implementation, the prototype must support these scenarios.

## Test 1 — Initial State

Open SCR-AUTH-002.

Verify:

- 전체 동의 unchecked,
- 이용약관 unchecked,
- 개인정보 수집 · 이용 unchecked,
- Marketing unchecked,
- no initial validation error.

## Test 2 — Required Validation

From Initial State:

click 다음.

Verify:

- remain on SCR-AUTH-002,
- no navigation,
- checkbox selections preserved,
- error appears:

필수 약관에 동의해야 가입할 수 있습니다.

## Test 3 — Partial Consent

Select only:

이용약관

Attempt 다음.

Verify:

- remain on SCR-AUTH-002,
- 이용약관 remains selected,
- 개인정보 수집 · 이용 remains unchecked,
- validation is shown.

## Test 4 — Required Consent Ready Without Marketing

Select:

이용약관
개인정보 수집 · 이용

Leave:

마케팅 정보 수신 동의 = unchecked

Click 다음.

Verify:

- progression is allowed,
- navigate to SCR-AUTH-003.

This is a critical acceptance test.

## Test 5 — Overall Consent

Return to SCR-AUTH-002.

Select:

전체 동의

Verify:

- 이용약관 selected,
- 개인정보 수집 · 이용 selected,
- Marketing selected.

## Test 6 — Individual Deselect After Overall Consent

After selecting 전체 동의:

deselect:

마케팅 정보 수신 동의

Verify:

- Marketing becomes unchecked,
- required two items remain checked,
- progression remains allowed.

## Test 7 — All Consent

Select all items.

Click 다음.

Verify:

- navigate to SCR-AUTH-003.

## Test 8 — Cancel

Click 취소.

Verify:

- it does not navigate to an invented destination,
- no Home/Login/placeholder destination is introduced.

---

# 27. Acceptance Criteria

Consider SCR-AUTH-002 Low-Fidelity successful only if all of the following are true:

- SCR-AUTH-002 is a full-page public process screen.
- It contains only the consent step.
- The existing public shell is reused.
- 회원가입 is the page heading.
- 전체 동의 exists.
- 이용약관 is marked required.
- 개인정보 수집 · 이용 is marked required.
- 마케팅 정보 수신 동의 is marked optional.
- Marketing starts unchecked.
- Marketing is not required for progression.
- Required Consent Ready is distinguishable from All Consent.
- Overall Consent can select all individual items.
- An individual item can be deselected after Overall Consent.
- Required Consent Error is testable.
- Error does not reset existing selections.
- The error message is:
  필수 약관에 동의해야 가입할 수 있습니다.
- 다음 can navigate to SCR-AUTH-003 after required consent is satisfied.
- 취소 does not invent a destination.
- No member-information fields are added.
- No email verification UI is added.
- No Social/OAuth flow is added.
- No new legal consent is invented.
- No Login-only state is copied.
- Existing validated screens remain unchanged.

---

# 28. Final Guardrails

Do not infer missing Product Decisions from common sign-up UX conventions.

Do not treat optional items as required.

Do not combine sign-up steps for convenience.

Do not invent backend behavior.

Do not create new legal content.

Do not create new Screen IDs.

Do not redesign unrelated screens.

Do not resolve the Cancel destination.

Do not finalize the Next disabled policy.

Do not make Indeterminate State mandatory.

Do not design the real SCR-AUTH-003 beyond the minimum prototype destination if it does not already exist.

The most important Product rule to preserve is:

Required Consent Ready ≠ All Consent.

A user who agrees to:

- 이용약관,
- 개인정보 수집 · 이용,

but does NOT agree to:

- 마케팅 정보 수신 동의,

must still be able to continue to SCR-AUTH-003.