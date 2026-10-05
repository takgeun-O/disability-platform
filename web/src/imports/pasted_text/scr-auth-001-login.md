# SCR-AUTH-001 Login — Desktop Low-Fidelity Figma Make Prompt

## Objective

Create the Desktop Low-Fidelity implementation for:

SCR-AUTH-001 — Login

for the IYUM accessibility-focused platform.

Use the existing IYUM Figma Make project and its current Home, Community, Post, Search, shared Header, Global Navigation, Footer, typography hierarchy, form patterns, buttons, spacing conventions, and general Low-Fidelity visual language as the primary visual reference.

This task is NOT a redesign of the existing product.

Do not modify or redesign existing Home, Community, Post, Search, or other completed screens except for the minimum prototype connections required to reach or return from Login.

The goal is to create and validate the Login screen structure, form hierarchy, major states, accessibility behavior, and prototype navigation.

Keep this implementation Low-Fidelity.

Do not make new Product Decisions.

---

# 1. Screen Contract

Screen ID:
SCR-AUTH-001

Screen Name:
로그인

Screen Type:
Standalone Form Screen

Access:
Public

MVP:
Required

Phase:
Phase 1

Authentication method:
Email + Password

Login persistence control:
Optional Checkbox labeled exactly:

로그인 유지

Direct login success destination:
Home

If a valid Return Context exists:
Preserve the user's original purpose according to the navigation rules below.

This screen is NOT a modal.

---

# 2. Reuse the Existing IYUM Shell

Reuse the existing Public shared shell already used by the current IYUM prototype.

Preserve the existing:

- Header
- Logo
- Global Navigation
- Public account/navigation treatment
- Search access if it is part of the existing shared Header
- Footer
- overall page width conventions
- Low-Fidelity visual language

Do not invent a new authentication-specific Header or navigation system.

Logo navigation should continue to lead to Home according to the existing prototype behavior.

Do not redesign the existing Header or Footer.

---

# 3. Breadcrumb

Do NOT add a Breadcrumb to SCR-AUTH-001.

Login is a standalone Form Screen.

Do not represent Return Context as a Breadcrumb.

Do not use Breadcrumb as a substitute for Browser Back or authentication return behavior.

---

# 4. Main Desktop Layout

Create a clear, restrained Desktop Form Layout.

Recommended information hierarchy:

Shared Header
Global Navigation

Main
  Login Form Area

    H1: 로그인

    이메일
    [ Email Field ]

    비밀번호
    [ Password Field ]

    [ ] 로그인 유지

    [ 로그인 ]

    회원가입
    비밀번호 찾기

Footer

Keep the Login Form visually constrained and easy to locate.

Do not stretch the form unnecessarily across the page.

Do not introduce a large marketing Hero.

Do not introduce promotional illustrations.

Do not make the screen visually resemble an advertising landing page.

Exact form width, spacing, border radius, shadows, and Card treatment are NOT final decisions.

Use the existing Low-Fidelity conventions where possible.

---

# 5. Page Heading

Use:

로그인

as the primary page heading.

It should function as the clear H1 or equivalent page heading.

Do not add unnecessary marketing copy or a long service introduction.

If a short supporting description is needed for layout balance, keep it neutral and clearly non-final.

Do not create new Product copy.

---

# 6. Email Field

Create a required Email Field.

Visible label:

이메일

Requirements:

- The label must remain visible.
- Do not use Placeholder as a replacement for the label.
- Use an email-appropriate input.
- Support normal browser email autocomplete semantics where possible.
- Preserve a valid email value during retry/error states where appropriate.

The Low-Fidelity prototype should be capable of representing:

- Empty email validation
- Blank/whitespace-only validation concept
- Invalid email format
- Valid email input

Do not expose whether an account exists for a specific email address.

Do not define a final email validation regex.

Do not make Placeholder copy a Product Decision.

---

# 7. Password Field

Create a required Password Field.

Visible label:

비밀번호

Requirements:

- Default presentation is masked.
- The label must remain visible.
- Do not use Placeholder as a replacement for the label.
- Preserve browser/password-manager-friendly form semantics where possible.

A show/hide password control is NOT a required MVP Product Contract.

If you include one because it fits the existing form conventions, treat it only as an optional supporting control.

If included:

- make its purpose understandable,
- provide an accessible name,
- do not treat its inclusion as a new Product Decision.

Do not expose password values in URLs, state labels, debug text, or other visible UI.

Do not invent a final policy for whether the password remains or clears after authentication failure.

---

# 8. Login Persistence

Include an Optional Checkbox.

The visible label must be exactly:

로그인 유지

Do NOT rename it to:

자동 로그인

The user must be able to log in without selecting it.

For the representative Default / Initial frame, show the Checkbox as unchecked.

Important:

This unchecked presentation is only the recommended representative prototype state.

It does NOT establish the official Product default.

Do not define:

- session duration,
- persistent session duration,
- cookie expiration,
- technical Remember-Me behavior.

None of those belong in the UI.

---

# 9. Primary Action

Create one clear Primary Action:

로그인

This is the primary Form Submit action.

The design must support the concept of:

- validation before authentication,
- Processing after a valid submission,
- preventing duplicate submission while Processing,
- success redirect,
- authentication failure,
- network/system failure.

Do NOT establish a final Product rule for whether the Login button is enabled or disabled when fields are empty.

Use a reasonable Low-Fidelity representation without treating it as a permanent Product contract.

---

# 10. Supporting Navigation

Provide two secondary navigation actions:

회원가입

and

비밀번호 찾기

Prototype destinations:

회원가입
→ SCR-AUTH-002

비밀번호 찾기
→ SCR-AUTH-006

Treat these as Navigation rather than additional primary form actions.

They should have lower visual priority than the Login Primary Action.

Do not embed the Sign-up form into this screen.

Do not embed Password Reset fields into this screen.

Do not turn Login into a multi-purpose authentication wizard.

---

# 11. Default / Initial State

Create a representative Default / Initial state.

Recommended representative values:

Entry Context:
Direct entry from the Home shared Header

Email:
Empty

Password:
Empty and masked

로그인 유지:
Unchecked

Form State:
Default / Initial

Login Primary Action:
Visible

Success Destination:
Home

This is the primary representative frame for SCR-AUTH-001.

Do not interpret the unchecked Checkbox or button presentation as a new Product default.

---

# 12. Validation Error State

Support a Validation Error state.

Client-side validation concepts:

- Email required
- Password required
- Email format validation

When validation fails:

- remain on SCR-AUTH-001,
- do not navigate,
- do not begin authentication,
- show the error near the relevant field,
- preserve other valid input where appropriate,
- do not communicate the error by color alone.

The prototype should be able to represent:

A. Email empty
B. Password empty
C. Both fields empty
D. Invalid email format

Exact final validation copy is NOT fully defined.

Use restrained Low-Fidelity error copy or annotations without treating them as final Product copy.

Do not use Toast, Modal, or Dialog as the primary field-validation mechanism.

---

# 13. Authentication Failure State

Create a distinct Authentication Failure state for valid-form submissions where credentials are not accepted.

Use this official example message:

이메일 또는 비밀번호를 확인해 주세요.

This should be presented as a Form-level authentication error.

Requirements:

- Remain on SCR-AUTH-001.
- Keep the email value.
- Allow the user to edit and retry.
- Do not reveal whether the email account exists.
- Do not identify the password specifically as incorrect.
- Do not treat this as an email format error.

The password retain/clear behavior is not yet a final Product Decision.

Do not establish a new permanent policy for it.

---

# 14. Network / System Error State

Create a distinct Network / System Error state.

Conceptual flow:

Login Request
→ Network/System Error
→ Stay on Login
→ Explain the failure
→ Allow Retry

This state must be semantically distinct from Authentication Failure.

The message must not imply that the user's credentials are incorrect.

Recommended Low-Fidelity presentation:

- Form-level system error area
- concise error explanation
- visible retry action or retry concept
- Login Form remains available

A separate Retry button versus reuse of the Login button is NOT a final Product Decision.

Choose a restrained prototype representation without turning it into a permanent contract.

Do not require a Modal, Dialog, or Toast.

---

# 15. Processing State

Represent the existence of a Processing state.

Purpose:

- communicate that Login is being processed,
- prevent duplicate submission,
- make the state understandable visually and semantically.

A Low-Fidelity Processing state may use:

- a temporary Login button state,
- short processing text,
- a simple loading indicator,
- or a clear state annotation.

Do not make the exact spinner, animation, wording, or timing a final visual decision.

Do not introduce a long artificial delay that could make the Figma Make preview unstable.

The important prototype requirement is that the Processing state exists and is inspectable.

If a real timed transition is unreliable in Figma Make, provide an inspectable static Processing variant instead.

---

# 16. Restricted State

Support the concept of a Restricted account state without creating a large set of unnecessary screens.

Possible documented account-state examples include:

- Email verification pending
- Limited account
- Suspended account
- Withdrawn account
- Temporary restriction after repeated failed attempts

For this Low-Fidelity task:

Create at most ONE representative Restricted state if useful for state verification.

The remaining Restricted states may be represented as annotations or documented state variants.

Do not invent new account states.

Do not invent unsupported recovery actions.

Do not make Restricted states the primary focus of the Login screen.

---

# 17. Success / Redirect Behavior

Do NOT create a separate Login Success completion page.

Success should be represented through Redirect behavior.

## A. Direct Login

Home Header
→ SCR-AUTH-001
→ Successful Login
→ Home

If there is no Return Context, Home is the official destination.

Do not redirect to a newly invented Member Dashboard.

Do not invent a "default member page."

## B. Protected Navigation

Example:

Post Create requested
→ Authentication required
→ SCR-AUTH-001
→ Successful Login
→ Post Create

Navigation intent continues after authentication.

## C. Protected State-changing Action

Example:

User selects Save / Recommend / Report on Post Detail
→ Authentication required
→ SCR-AUTH-001
→ Successful Login
→ Return to the original Post Detail context
→ Do NOT automatically execute the original action
→ User must select the action again

This distinction is critical.

Do not treat successful authentication as renewed consent to perform a state-changing action.

---

# 18. Return Context

Represent Return Context through prototype navigation only.

Do NOT display:

- return URL,
- redirect query parameters,
- session keys,
- internal routing state,
- technical context identifiers.

The Login UI should not contain a "return URL" field or technical navigation information.

Technical storage and restoration of Return Context belong to Frontend implementation.

For Figma, validate the navigation concept.

---

# 19. Authentication vs Authorization

Do not imply that Login success grants every permission.

Authentication confirms the user's identity.

Authorization still determines:

- role,
- ownership,
- author permissions,
- resource state,
- administrator access,
- account restrictions.

Do not create UI that implies:

Login Success
= All Actions Authorized

Do not automatically reveal author-only or administrator-only actions merely because Login succeeded unless the existing prototype already has the correct authorized state.

---

# 20. Session / Cookie Technology Must Stay Invisible

The Web MVP uses:

Server-side Session
+ Session ID Cookie

This is an implementation contract, NOT Login UI.

Do NOT add:

- JWT
- Access Token
- Refresh Token
- Session ID
- Cookie value
- Token expiration UI
- Token management controls

Do not expose technical authentication architecture in the interface.

---

# 21. Accessibility Requirements

Accessibility is a core requirement of this prototype.

## Labels

- Email and Password must have persistent visible labels.
- Placeholder must not replace labels.
- Required fields must be understandable as required.

## Errors

- Associate errors with the relevant fields conceptually.
- Do not rely on color alone.
- Provide readable error text or another meaningful indicator.
- Distinguish Validation Error, Authentication Failure, and System Error.

## Keyboard

Maintain a logical keyboard order.

Recommended order:

Email
→ Password
→ 로그인 유지
→ 로그인
→ 회원가입
→ 비밀번호 찾기

All interactive controls must have visible Focus treatment.

The Checkbox must be keyboard operable.

Supporting Navigation must be keyboard accessible.

Do not introduce a Login-specific Focus Trap because this is not a Modal.

## Password

Default to masking.

If a show/hide control is included, provide a meaningful accessible name.

## Processing

Make the Processing state understandable to assistive technology conceptually.

Actual live-region behavior will be validated during Frontend implementation.

---

# 22. Responsive Preparation

The current task is Desktop Low-Fidelity.

However, keep the structure compatible with later Tablet and Mobile layouts.

Do not create Desktop-only dependencies that prevent a Single Column Mobile form.

Expected Mobile information order:

로그인
→ 이메일
→ 비밀번호
→ 로그인 유지
→ 로그인
→ 회원가입
→ 비밀번호 찾기

Do not add Mobile-only Social Login.

Do not define exact breakpoints in this task.

---

# 23. Prototype Connections

Where feasible without redesigning completed screens, prepare or preserve the following prototype concepts:

### Direct Login

Existing Home Header
→ Login
→ SCR-AUTH-001
→ successful Login
→ Home

### Protected Navigation

Existing protected navigation example
→ SCR-AUTH-001
→ successful Login
→ original destination

Use Post Create as the representative example if an appropriate existing entry already exists.

### Protected State-changing Action

Existing Post Detail state-changing action
→ SCR-AUTH-001
→ successful Login
→ original Post Detail context
→ original action remains unexecuted

Do not redesign Home, Post Create, or Post Detail.

Only add the minimum interaction needed if safe within the current prototype.

If connecting an existing screen risks changing its approved design, preserve the existing screen and represent the intended Login return flow through the Login prototype/state structure instead.

---

# 24. Low-Fidelity State Verification

Make the following states inspectable in the prototype or implementation:

1. Default / Initial
2. Validation Error
3. Authentication Failure
4. Network / System Error
5. Retry concept
6. Processing
7. Success / Redirect
8. One representative Restricted state if practical

These do not all need to be visually presented simultaneously on the primary screen.

Prefer state variants, controlled prototype states, or clearly inspectable alternate frames.

Do not clutter the Default screen with all error states at once.

---

# 25. Figma vs Implementation Boundary

Figma Low-Fidelity should validate:

- information architecture,
- form hierarchy,
- Email and Password fields,
- persistent labels,
- password masking,
- 로그인 유지 Checkbox,
- Login Primary Action,
- Supporting Navigation,
- major error states,
- Processing existence,
- Retry concept,
- Redirect concepts,
- Return Context concepts,
- Desktop hierarchy,
- basic accessibility.

Do NOT attempt to fully validate in Figma:

- real authentication requests,
- real credentials,
- real session creation,
- real Cookie behavior,
- real password verification,
- actual loading lifecycle timing,
- browser autofill behavior,
- password manager behavior,
- complete Browser Back restoration,
- real session expiration,
- actual backend error mapping,
- HTTP status codes,
- backend authorization,
- rate limiting.

Those belong to Frontend/API/Backend implementation.

---

# 26. Explicitly Do Not Add

Do NOT add any of the following:

- Login Modal
- Social Login
- OAuth Login
- Kakao Login
- Naver Login
- Google Login
- separate username Login
- phone-number Login
- MFA
- Passkey
- Guest Continue
- CAPTCHA
- JWT UI
- Access Token UI
- Refresh Token UI
- Session ID UI
- Cookie management UI
- Sign-up Form inside Login
- Password Reset Form inside Login
- large marketing Hero
- promotional illustration-driven layout
- new Account Recovery features
- new Login methods
- new Screen IDs
- new Redirect rules
- automatic execution of protected state-changing actions after Login
- administrator UI inside the normal Login form
- High-Fidelity branding decisions

Do NOT rename:

로그인 유지

to:

자동 로그인

---

# 27. Do Not Finalize These Details

Do not turn the following into Product Decisions:

- final brand colors,
- final typography,
- exact grid,
- exact spacing,
- exact pixels,
- radius,
- shadow,
- placeholder copy,
- mandatory password show/hide control,
- Login button disabled policy,
- default state of 로그인 유지,
- password retention after authentication failure,
- final Error Summary pattern,
- exact Processing copy,
- exact loading indicator,
- exact form maximum width,
- breakpoints,
- session timeout,
- cookie Max-Age,
- Return Context storage implementation,
- endpoint,
- DTO,
- HTTP status,
- error code.

Use reasonable Low-Fidelity placeholders or prototype behavior only where necessary.

---

# 28. Visual Direction

Keep the screen:

- simple,
- functional,
- calm,
- accessible,
- consistent with the existing IYUM Low-Fidelity prototype,
- easy to scan,
- easy to operate by keyboard,
- clearly structured around one primary task.

The hierarchy should make the following immediately understandable:

1. This is Login.
2. Enter Email.
3. Enter Password.
4. Optionally choose 로그인 유지.
5. Submit Login.
6. Use 회원가입 or 비밀번호 찾기 when needed.

Do not over-design.

Do not introduce decorative complexity.

---

# 29. Completion Criteria

The first SCR-AUTH-001 Desktop Low-Fidelity implementation is ready for review when:

- the existing IYUM shared shell is preserved,
- Login is a standalone screen,
- H1 로그인 is clear,
- Email and Password have visible labels,
- Password is masked by default,
- 로그인 유지 exists as an optional Checkbox,
- 로그인 is the clear Primary Action,
- 회원가입 and 비밀번호 찾기 are secondary navigation,
- Breadcrumb is absent,
- Validation Error can be inspected,
- Authentication Failure can be inspected,
- Network/System Error and Retry can be inspected,
- Processing exists as an inspectable state,
- success redirects rather than creating a Login completion screen,
- Direct Login can conceptually return to Home,
- Protected Navigation can conceptually continue to its destination,
- Protected State-changing Action does not automatically execute after Login,
- accessibility fundamentals are represented,
- no excluded authentication methods or technical authentication UI have been added.

Build only what is necessary for this Low-Fidelity validation.