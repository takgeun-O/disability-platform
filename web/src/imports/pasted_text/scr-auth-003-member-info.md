# IYUM — SCR-AUTH-003 Member Information
## Desktop Low-Fidelity Figma Make Prompt

### 1. Objective

Create the Desktop Low-Fidelity prototype for:

- Screen ID: SCR-AUTH-003
- Screen Name: 회원정보 입력
- Type: Form
- Access: Public
- MVP: Required
- Phase: Phase 1
- Layout: Form Layout
- Responsive: Common

This screen is the member-information step in the IYUM sign-up flow.

Official sign-up flow:

SCR-AUTH-002
Required Consent
→
SCR-AUTH-003
Member Information
→
Validation
→
Email / Nickname Duplicate Validation
→
PENDING Account creation
→
SCR-AUTH-004
Email Verification
→
Verification Success
→
ACTIVE
→
SCR-AUTH-005
Sign-up Complete

The goal of this Low-Fidelity prototype is to validate:

- the four official fields,
- field order,
- required validation,
- email format validation,
- password validation,
- password confirmation validation,
- submit-time Email Duplicate Error,
- submit-time Nickname Duplicate Error,
- Processing state,
- Network / System Error,
- Retry concept,
- Previous navigation,
- successful Next navigation to SCR-AUTH-004,
- and basic accessibility.

Do not redesign unrelated IYUM screens.

Do not introduce new Product Decisions.

---

# 2. Critical Screen Boundary

SCR-AUTH-003 is ONLY the member-information form step.

The screen must contain exactly these four official fields, in this exact order:

1. 이메일
2. 비밀번호
3. 비밀번호 확인
4. 닉네임

All four fields are Required.

Do NOT add any additional member-information fields.

Do NOT merge this screen with SCR-AUTH-002 or SCR-AUTH-004.

SCR-AUTH-002 owns:
- Terms consent
- Privacy collection/use consent
- Optional marketing consent
- Required-consent validation

SCR-AUTH-004 owns:
- Email verification target information
- Verification code input
- Resend
- Verification validation
- Verification success
- ACTIVE transition

Do not display consent checkboxes or email-verification controls on SCR-AUTH-003.

---

# 3. Existing Product Shell

Reuse the existing IYUM Low-Fidelity visual language and the documented authentication/public screen context.

Preserve existing shared patterns where appropriate.

Do not invent:

- complex new global navigation,
- marketing hero content,
- new sign-up navigation,
- sidebar layouts,
- a modal version of this form,
- new header behavior,
- new footer behavior.

If the current prototype has an existing authentication/public shell, reuse it consistently.

Do not redesign SCR-AUTH-001 or SCR-AUTH-002.

---

# 4. Main Desktop Structure

Use a focused, single-column Form Layout.

Recommended hierarchy:

Page Context:
회원가입

Current Step:
회원정보 입력

Optional:
Sign-up Step Indicator

Form:

이메일
[ Email Input ]

비밀번호
[ Password Input ]

비밀번호 확인
[ Password Confirmation Input ]

닉네임
[ Nickname Input ]

Conditional Form-level Error Area

Action Area:
[ 이전 ] [ 다음 ]

The field order must not change.

Do not place Email and Nickname in horizontal rows with duplicate-check buttons.

Do not add unrelated profile sections.

---

# 5. Page Context

Use:

회원가입

as the broader process context.

Use:

회원정보 입력

as the current-step identification.

Do not invent detailed promotional copy.

A short supporting description may be used only if needed for Low-Fidelity hierarchy.

Do not treat supporting copy as final Product copy.

A Step Indicator is optional.

Do not invent:
- an exact number of sign-up steps,
- exact step labels,
- a final Stepper design,
unless an already existing reusable pattern clearly supports it.

---

# 6. Email Field

Visible label:

이메일

Required:
Yes

Purpose:
- Login credential
- Email-verification target for SCR-AUTH-004

Validation:
- Required
- Email format
- Trim leading/trailing whitespace concept
- Duplicate not allowed

Official Email Format Error:

올바른 이메일 주소를 입력해 주세요.

Official Email Duplicate Error:

이미 가입된 이메일입니다.

Critical duplicate-check behavior:

Email duplication is checked ONLY when the user submits the form with 다음.

Do NOT add:

- 중복 확인 button
- duplicate-check button beside the field
- Blur duplicate validation
- live duplicate validation while typing
- availability success message
- "사용 가능한 이메일입니다."
- availability badge
- availability icon

Prototype duplicate behavior must be simulated through the normal form Submit flow.

---

# 7. Password Field

Visible label:

비밀번호

Required:
Yes

Component:
Password Field

Default:
Masked

Official rule:

- at least 8 characters,
- includes letters,
- includes numbers.

Official Error:

비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.

Do NOT add:

- mandatory special-character requirement,
- Password Strength Meter,
- arbitrary maximum length,
- arbitrary whitespace restriction,
- arbitrary Unicode restriction.

A password show/hide control is not a required Product Contract.

If one already exists as a reusable pattern and is included, do not treat it as a new Product Decision.

---

# 8. Password Confirmation Field

Visible label:

비밀번호 확인

Required:
Yes

Component:
Password Field

Purpose:
Cross-field validation against 비밀번호.

Official Error:

비밀번호가 일치하지 않습니다.

Do NOT add:

- password-match success badge,
- green success indicator,
- separate confirm button,
- new verification interaction.

Do not render it as an independent stored credential.

---

# 9. Nickname Field

Visible label:

닉네임

Required:
Yes

Purpose:
Public profile / community identity.

Official rule:

- 2 to 20 characters,
- duplicate not allowed.

Official Nickname Duplicate Error:

이미 사용 중인 닉네임입니다.

Critical duplicate-check behavior:

Nickname duplication is checked ONLY when the user submits with 다음.

Do NOT add:

- 중복 확인 button,
- Blur duplicate validation,
- live duplicate validation,
- availability success message,
- success badge or icon.

Do not invent exact allowed-character rules.

Do not invent whitespace rules.

---

# 10. Required Validation

All four fields are Required.

The prototype must support Required Validation.

When one or more Required fields are empty and the user submits:

- stay on SCR-AUTH-003,
- do not navigate,
- display the relevant error near the affected field,
- preserve other valid values where appropriate,
- allow the user to correct and resubmit.

The exact Korean copy for generic Required errors is not finalized.

Use a restrained prototype-only required error representation if needed.

Do not treat prototype wording as final Product copy.

Do not rely only on color.

---

# 11. Email Format Validation

Testable behavior:

Invalid email
→ 다음
→ remain on SCR-AUTH-003
→ show:

올바른 이메일 주소를 입력해 주세요.

Do not proceed to duplicate validation success.

Do not navigate to SCR-AUTH-004.

---

# 12. Password Format Validation

Testable behavior:

Password shorter than 8 characters
OR
Password without letters
OR
Password without numbers

→ 다음
→ remain on SCR-AUTH-003
→ show:

비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.

Do not add a special-character error.

---

# 13. Password Confirmation Validation

Testable behavior:

비밀번호
≠
비밀번호 확인

→ 다음
→ remain on SCR-AUTH-003
→ show:

비밀번호가 일치하지 않습니다.

Preserve other valid fields where possible.

---

# 14. Submit-time Duplicate Validation

Email and Nickname use the same interaction model.

Official flow:

User fills all four fields
→ clicks 다음
→ normal field validation
→ Email / Nickname Duplicate Validation
→ Success or Field-level Error

Do not expose duplicate-check controls before Submit.

Do not create separate availability states.

---

# 15. Email Duplicate Error State

Create an interactively testable prototype scenario for Email Duplicate Error.

Expected behavior:

Valid-looking four fields
→ 다음
→ Processing
→ Email Duplicate Error

Result:

- stay on SCR-AUTH-003,
- show Email Field-level Error:
  이미 가입된 이메일입니다.
- preserve the other normal field values where possible,
- allow the user to modify Email and resubmit.

Do not navigate to SCR-AUTH-004.

Do not show:
- "Email unavailable" badges,
- separate duplicate-check controls.

Use a deterministic prototype-only input condition if needed.

Recommended prototype-only test fixture:

Email:
duplicate@example.com

Password:
password123

Password Confirmation:
password123

Nickname:
iyumuser

Expected:
Email Duplicate Error

These values exist only for prototype verification.

They are not Product requirements or real accounts.

---

# 16. Nickname Duplicate Error State

Create an interactively testable prototype scenario for Nickname Duplicate Error.

Expected flow:

Valid-looking four fields
→ 다음
→ Processing
→ Nickname Duplicate Error

Result:

- remain on SCR-AUTH-003,
- show Nickname Field-level Error:
  이미 사용 중인 닉네임입니다.
- preserve other valid field values where possible,
- allow the user to modify Nickname and resubmit.

Recommended prototype-only test fixture:

Email:
newuser@example.com

Password:
password123

Password Confirmation:
password123

Nickname:
duplicate

Expected:
Nickname Duplicate Error

These values are prototype-only deterministic test conditions.

Do not treat them as Product data.

---

# 17. Processing State

A Processing state must exist.

Official concept:

다음 Submit
→ Processing
→ duplicate Submit prevented
→ Success or Failure

The prototype must make Processing inspectable.

It may use:

- temporary button state,
- simple text,
- minimal loading indicator,
- static Processing variant.

Do not finalize:

- exact loading text,
- spinner style,
- animation,
- loading duration,
- full-field disable behavior.

Do not use a long artificial delay that could destabilize Figma Make preview.

If timed Processing is unreliable, use an inspectable deterministic state instead.

---

# 18. Network / System Error State

Create an interactively testable Network / System Error state.

Official Form-level message:

가입을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.

Expected behavior:

Submit
→ Processing
→ Network / System Error
→ stay on SCR-AUTH-003

The error must:

- be Form-level,
- remain visually distinct from Field Validation,
- preserve normal input values where possible,
- expose a Retry concept,
- not display technical error codes.

Do not use:
- modal-only error handling,
- dialog-only error handling,
- toast-only error handling.

Use a deterministic prototype-only condition if needed.

Recommended test fixture:

Email:
error@example.com

Password:
password123

Password Confirmation:
password123

Nickname:
iyumuser

Expected:
Network / System Error

---

# 19. Retry Behavior

The Network / System Error must include an actual testable Retry action.

The exact final Product label is not finalized.

For prototype verification, a restrained label such as:

다시 시도

may be used as prototype-only copy.

Expected prototype behavior:

Network / System Error
→ Retry
→ Processing
→ recoverable SCR-AUTH-003 form state

Do NOT automatically complete sign-up after Retry.

Do NOT automatically navigate to SCR-AUTH-004 unless the subsequent state is a valid successful submission.

Preserve entered values where possible.

Do not treat Retry wording as a new final Product copy decision.

---

# 20. Successful Submit

Create a deterministic successful prototype path.

Recommended prototype-only successful test fixture:

Email:
success@example.com

Password:
password123

Password Confirmation:
password123

Nickname:
iyumuser

Expected flow:

Valid fields
→ 다음
→ Processing
→ Success
→ SCR-AUTH-004

Do NOT create an intermediate success screen.

Do NOT display:

- 회원정보 입력 완료
- 계정 생성 완료
- PENDING 계정 생성 완료
- Member ID
- account status badge

The visible result of success is navigation to SCR-AUTH-004.

---

# 21. SCR-AUTH-004 Destination

If SCR-AUTH-004 already exists, navigate to the existing screen.

If SCR-AUTH-004 does not yet exist, create only the minimum temporary prototype destination needed to verify navigation.

The temporary destination must:

- clearly identify SCR-AUTH-004,
- clearly identify 이메일 인증,
- remain minimal,
- not define the real SCR-AUTH-004 UI,
- not invent verification-code Product Decisions,
- not create detailed verification flows.

Do not design the actual SCR-AUTH-004 in this task.

---

# 22. Previous Navigation

Secondary Action:

이전

Official destination:

SCR-AUTH-002

Expected flow:

SCR-AUTH-003
→ 이전
→ SCR-AUTH-002

This is navigation, not cancel.

Do NOT:

- navigate to Home,
- navigate to Login,
- treat it as form cancellation,
- create a new confirmation dialog,
- invent dirty-form exit policy.

Input-preservation implementation is a later Frontend concern.

For Low-Fidelity, validate the navigation concept.

---

# 23. No Cancel Action

SCR-AUTH-003 does not have a separate official Cancel action.

Do NOT add:

취소

Do NOT add a third action beside 이전 and 다음.

---

# 24. State Separation

Keep the following states distinct:

Default / Initial
≠
Validation Error
≠
Email Duplicate Error
≠
Nickname Duplicate Error
≠
Processing
≠
Network / System Error
≠
Success / Next Step

Do not merge every failure into one generic error state.

Do not show all state messages at once on the default screen.

---

# 25. Accessibility Requirements

Accessibility is a core requirement.

## Labels

Each input must have a persistent visible label.

Do not use Placeholder as a replacement for Label.

## Required meaning

Required state must be understandable using text or semantics.

Do not rely only on:
- red color,
- asterisk color,
- border color.

## Errors

Errors must appear near the related Field.

Use readable Error Text.

Do not rely only on color.

Keep Email Duplicate Error attached conceptually to Email.

Keep Nickname Duplicate Error attached conceptually to Nickname.

## Keyboard

Maintain a logical order:

이메일
→ 비밀번호
→ 비밀번호 확인
→ 닉네임
→ 이전
→ 다음

Provide visible Focus treatment.

Do not create a Focus Trap.

## Processing

Processing must be understandable visually and conceptually to assistive technology.

Actual live-region implementation is a Frontend responsibility.

## Password

Default to masked fields.

Do not interfere with Password Manager / Autofill concepts.

---

# 26. Desktop Low-Fidelity Visual Direction

Keep the screen:

- simple,
- calm,
- accessible,
- task-focused,
- single-column,
- easy to scan,
- consistent with existing IYUM Low-Fidelity screens.

Prioritize:

1. Page context
2. Current step
3. Field hierarchy
4. Required meaning
5. Error location
6. Previous / Next hierarchy
7. State verification

Do not prioritize:

- decorative illustration,
- brand-heavy styling,
- marketing content,
- motion,
- complex layout,
- visual experimentation.

---

# 27. Do Not Finalize These Details

Do not turn any of the following into a Product Decision:

- Password show/hide requirement
- Password retention after error
- Password maximum length
- Special-character requirement
- whitespace policy
- Unicode policy
- Email normalize algorithm
- Email maximum length
- Nickname allowed characters
- Nickname whitespace policy
- exact Required error copy
- simultaneous Email/Nickname duplicate-error presentation
- exact Processing copy
- exact Spinner
- exact Animation
- field-disable policy
- Retry final label
- form maximum width
- breakpoints
- Browser Back behavior
- Refresh behavior
- sign-up completion auto-login
- PENDING account login policy
- exact Global Header structure

Use reasonable Low-Fidelity treatment only where necessary.

---

# 28. Explicit Exclusions

Do NOT add:

- Email duplicate-check button
- Nickname duplicate-check button
- Blur duplicate validation
- Live duplicate validation
- Duplicate success messages
- Duplicate success badges
- Social Sign-up
- OAuth
- Kakao
- Naver
- Google
- Apple
- phone number
- phone verification
- real-name verification
- birth date
- gender
- address
- disability type
- disability severity
- disability registration
- health information
- medical information
- treatment information
- assistive-device information
- guardian information
- interest selection
- region personalization
- profile image
- bio
- member-type selection
- role selection
- consent checkboxes
- marketing consent
- verification-code input
- resend button
- verification timer
- MFA
- passkeys
- Password Strength Meter
- mandatory special-character rule
- SCR-AUTH-003 success page
- Cancel action
- PENDING status UI
- ACTIVE status UI
- Member ID
- Session UI
- Cookie UI
- JWT
- Access Token
- Refresh Token

---

# 29. Figma vs Implementation Boundary

Figma Low-Fidelity should validate:

- exact four-field structure,
- field order,
- required state,
- field labels,
- Email Format Error,
- Password Format Error,
- Password Mismatch,
- Email Duplicate Error,
- Nickname Duplicate Error,
- Processing state existence,
- Network/System Error,
- Retry concept,
- Previous navigation,
- Success navigation,
- basic accessibility hierarchy.

Frontend/API will later validate:

- actual form state,
- real API request,
- real duplicate validation,
- actual loading lifecycle,
- duplicate-submit prevention,
- actual input preservation,
- Password preservation/reset,
- Browser Back,
- Refresh,
- Focus management,
- Autofill,
- Password Manager,
- API error mapping,
- route state,
- consent context.

Backend will later validate:

- Email uniqueness,
- Nickname uniqueness,
- Password hashing,
- PENDING Account creation,
- ACTIVE transition,
- Verification token,
- DB constraints,
- rate limiting,
- security,
- audit/logging.

Do not simulate backend architecture in the UI.

---

# 30. Required Interactive Test Scenarios

After implementation, the prototype should support these scenarios.

## Test 1 — Default

Open SCR-AUTH-003.

Verify:

- four fields exist,
- exact field order,
- all four are required,
- Password fields are masked,
- 이전 and 다음 exist,
- no initial validation error,
- no duplicate buttons,
- no email-verification UI.

## Test 2 — Required Validation

Leave one or more fields empty.

Click 다음.

Verify:

- remain on SCR-AUTH-003,
- relevant Required error appears,
- no navigation,
- other valid values remain where possible.

## Test 3 — Invalid Email

Enter an invalid email.

Click 다음.

Verify:

올바른 이메일 주소를 입력해 주세요.

No navigation.

## Test 4 — Invalid Password

Enter a password that violates the official rule.

Click 다음.

Verify:

비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.

No special-character requirement must appear.

## Test 5 — Password Mismatch

Enter different values in Password and Password Confirmation.

Click 다음.

Verify:

비밀번호가 일치하지 않습니다.

## Test 6 — Email Duplicate

Use prototype-only test input:

duplicate@example.com
password123
password123
iyumuser

Click 다음.

Verify:

- Processing is observable,
- remain on SCR-AUTH-003,
- Email Error:
  이미 가입된 이메일입니다.
- no separate duplicate button.

## Test 7 — Nickname Duplicate

Use prototype-only test input:

newuser@example.com
password123
password123
duplicate

Click 다음.

Verify:

- Processing is observable,
- remain on SCR-AUTH-003,
- Nickname Error:
  이미 사용 중인 닉네임입니다.

## Test 8 — Network / System Error

Use prototype-only test input:

error@example.com
password123
password123
iyumuser

Click 다음.

Verify:

가입을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.

Stay on SCR-AUTH-003.

## Test 9 — Retry

From Network / System Error:

click prototype Retry action.

Verify:

- Processing,
- recoverable form state,
- values preserved where possible.

Do not automatically complete sign-up.

## Test 10 — Success

Use:

success@example.com
password123
password123
iyumuser

Click 다음.

Verify:

- Processing,
- no intermediate success page,
- navigate to SCR-AUTH-004.

## Test 11 — Previous

Click 이전.

Verify:

- navigate to SCR-AUTH-002,
- do not navigate Home or Login,
- do not create a Cancel flow.

---

# 31. Acceptance Criteria

SCR-AUTH-003 Low-Fidelity is ready for review when:

- Screen ID is SCR-AUTH-003.
- It is a member-information Form screen.
- Exactly four official fields are present.
- Field order is correct.
- All four fields are Required.
- Password fields are masked by default.
- Email duplicate-check button does not exist.
- Nickname duplicate-check button does not exist.
- Duplicate Validation occurs through normal 다음 submission.
- Official Email Format Error can be tested.
- Official Password Format Error can be tested.
- Password Mismatch can be tested.
- Email Duplicate Error can be tested.
- Nickname Duplicate Error can be tested.
- Processing exists.
- Network/System Error exists.
- Retry is testable.
- 이전 navigates to SCR-AUTH-002.
- successful 다음 navigates to SCR-AUTH-004.
- no intermediate success page is created.
- PENDING is not rendered in the UI.
- no consent controls are added.
- no email-verification controls are added.
- no disability, medical, member-type, or social sign-up fields are added.
- accessibility fundamentals are represented.
- existing validated IYUM screens are not redesigned.

---

# 32. Final Guardrails

The most important rules are:

1. Use exactly four fields:
   이메일 → 비밀번호 → 비밀번호 확인 → 닉네임

2. All four are Required.

3. Email and Nickname duplicate validation occurs only on 다음 Submit.

4. Do not add duplicate-check buttons.

5. Do not add Blur or live duplicate validation.

6. Use the official Email Duplicate Error:
   이미 가입된 이메일입니다.

7. Use the official Nickname Duplicate Error:
   이미 사용 중인 닉네임입니다.

8. Use the official Password rule:
   at least 8 characters with letters and numbers.

9. Do not require special characters.

10. 이전 → SCR-AUTH-002.

11. Successful 다음 → SCR-AUTH-004.

12. Do not render PENDING or ACTIVE states.

13. Do not add consent UI.

14. Do not add email-verification UI.

15. Do not add disability, medical, member-type, or personalization fields.

16. Do not add new Product Decisions.

17. Do not redesign existing validated screens.

18. Keep the implementation Low-Fidelity and focused on structure, state, interaction, validation, accessibility, and navigation.