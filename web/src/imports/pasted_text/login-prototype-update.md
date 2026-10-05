Update the existing SCR-AUTH-001 Login Desktop Low-Fidelity prototype.

This is a targeted prototype-verification update only.

DO NOT redesign the Login screen.
DO NOT change the approved Product contract.
DO NOT modify or regress any Login behavior that has already been validated.

==================================================
1. STRICT REGRESSION PROTECTION
==================================================

The following behaviors have already been tested and PASSED.

Preserve them exactly as they currently work:

1. Validation Error
   - Required email validation
   - Required password validation
   - Invalid email-format validation
   - Field-level error presentation
   - Remaining on the Login screen after validation failure
   - Preservation of valid input where currently supported

2. Authentication Failure
   - Incorrect credentials remain on SCR-AUTH-001
   - The user-facing message remains:
     "이메일 또는 비밀번호를 확인해 주세요."
   - The "로그인 유지" checkbox state remains preserved
   - Authentication Failure remains distinct from field validation errors

3. Processing
   - Preserve the currently implemented Processing behavior and UI
   - Do not remove, replace, or redesign the existing Processing state

Also preserve:

- Current SCR-AUTH-001 page layout
- Header / GNB / Footer
- Email field
- Password field
- Password masking behavior
- Existing password visibility control
- "로그인 유지" checkbox
- Login primary action
- 회원가입 navigation
- 비밀번호 찾기 navigation
- Existing accessibility behavior
- Existing visual hierarchy

Do not refactor working prototype logic unless absolutely necessary to add the three scenarios below.

==================================================
2. PURPOSE OF THIS UPDATE
==================================================

Add exactly three deterministic Low-Fidelity test scenarios:

A. Successful Login
B. Restricted Account
C. Network / System Error → Retry

These scenarios exist only so that the approved Login states and navigation concepts can be verified in the Figma Make prototype.

They are NOT real backend accounts.

Do not implement a real authentication service.

Do not imply that these credentials are Product requirements.

==================================================
3. DETERMINISTIC TEST CREDENTIALS
==================================================

Use the following prototype-only credentials so each scenario can be reproduced reliably.

A. Successful Login

Email:
success@example.com

Password:
success1234

Expected flow:

Valid input
→ Login submit
→ existing Processing state
→ successful authentication simulation
→ Home

The destination must be the existing Home screen.

This represents a direct Login with no Return Context.

Do not introduce a new member landing page, dashboard, or account page.

--------------------------------------------------

B. Restricted Account

Email:
restricted@example.com

Password:
restricted1234

Expected flow:

Valid input
→ Login submit
→ existing Processing state
→ Restricted state
→ remain on SCR-AUTH-001

The Restricted state must:

- Clearly communicate that Login cannot currently proceed because of an account restriction
- Be visually and semantically distinct from Authentication Failure
- Be visually and semantically distinct from Network / System Error
- Not claim that the email or password is incorrect
- Not navigate away from SCR-AUTH-001
- Preserve the entered email where possible
- Preserve the "로그인 유지" checkbox state

Use neutral Korean copy appropriate for a Low-Fidelity prototype.

Do not invent a specific legal, disciplinary, suspension, deletion, or security reason unless already defined by the existing Product contract.

A neutral message such as the following is acceptable:

"현재 이 계정으로 로그인할 수 없습니다."

Supporting text may tell the user to check account status or seek support, but do not invent new Product policy.

--------------------------------------------------

C. Network / System Error

Email:
error@example.com

Password:
error1234

Expected flow:

Valid input
→ Login submit
→ existing Processing state
→ Network / System Error
→ remain on SCR-AUTH-001

Display a system-level message such as:

"로그인 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."

Provide an interactive Retry action:

"다시 시도"

The error must:

- Not be presented as an email field error
- Not be presented as a password field error
- Not use the Authentication Failure message
- Remain on SCR-AUTH-001
- Preserve the entered email where possible
- Preserve the "로그인 유지" checkbox state
- Clearly expose the Retry action

==================================================
4. RETRY PROTOTYPE BEHAVIOR
==================================================

The "다시 시도" action must be genuinely interactive in the prototype.

Do not create a real API retry.

For deterministic Low-Fidelity verification, implement the following simulation:

First submission using:

error@example.com
+
error1234

→ Processing
→ Network / System Error

Then:

Click "다시 시도"
→ Processing
→ return to the normal Login form state with the existing email preserved where possible

The purpose is to demonstrate:

Network / System Error
→ Retry Action
→ Processing
→ recoverable Login state

Do NOT automatically treat Retry as a successful login.

Do NOT navigate to Home after Retry unless the user subsequently submits the Successful Login test credentials.

==================================================
5. STATE SEPARATION
==================================================

Keep the following states conceptually and visually distinct:

Validation Error
≠ Authentication Failure
≠ Restricted
≠ Network / System Error
≠ Processing
≠ Success / Redirect

Validation Error:
Input itself is invalid.

Authentication Failure:
Credentials are syntactically valid but authentication fails.

Restricted:
Credentials reach an account state that cannot currently log in.

Network / System Error:
Authentication cannot complete because of a simulated technical failure.

Success:
Authentication succeeds and the approved redirect behavior is executed.

Do not merge these states into one generic error.

==================================================
6. LOGIN PERSISTENCE
==================================================

For all three new test scenarios, preserve the current behavior of:

"로그인 유지"

The checkbox is an optional user control.

Do not change its default selection state as part of this update.

Do not implement or simulate:

- Session duration
- Cookie expiration
- Remember-Me backend behavior
- Token storage
- JWT
- Persistent authentication timing

Those are outside this Low-Fidelity verification scope.

==================================================
7. PROTOTYPE TEST CONTROLS
==================================================

The existing "PROTOTYPE · 상태 확인" section must not be treated as real Product UI.

If its current items are static labels, they do not need to become functional.

Do not convert the entire section into a prototype control panel.

The three scenarios in this request must be testable through the normal Login form using the deterministic prototype-only credentials defined above.

The prototype helper section may remain temporarily during Low-Fidelity verification.

Do not visually promote it into part of the actual Login experience.

==================================================
8. DO NOT ADD
==================================================

Do not add:

- Social Login
- OAuth
- Kakao Login
- Naver Login
- Google Login
- Phone-number Login
- Username Login
- Guest Login
- MFA
- Passkeys
- CAPTCHA
- New authentication methods
- Login Modal
- New account-management flows
- New Product Decisions
- Real API calls
- Real backend authentication
- JWT or token UI
- Session/Cookie configuration UI
- Toast-only error handling
- Modal/Dialog-based Login error handling

Do not modify Sign-up or Password Recovery screens beyond preserving their existing navigation links.

==================================================
9. LOW-FIDELITY CONSTRAINT
==================================================

Keep this as a Desktop Low-Fidelity prototype.

Prioritize:

- State clarity
- Interaction verification
- Navigation verification
- Error distinction
- Retry behavior
- Accessibility semantics

Do not introduce High-Fidelity visual styling.

Do not spend effort on animation, decorative graphics, branding refinement, or visual polish.

==================================================
10. REQUIRED POST-UPDATE REPORT
==================================================

After implementing the update, report exactly:

1. Whether all previously validated behaviors were preserved:
   - Validation Error
   - Authentication Failure
   - Processing

2. How to test Successful Login:
   - exact email
   - exact password
   - expected Processing behavior
   - expected final destination

3. How to test Restricted Account:
   - exact email
   - exact password
   - expected Restricted message/state
   - expected navigation behavior

4. How to test Network / System Error:
   - exact email
   - exact password
   - expected error message/state

5. How to test Retry:
   - what to click
   - what state appears next
   - what input/checkbox state is preserved

6. Confirm that these credentials are prototype-only deterministic test conditions and are not real Product authentication requirements.

7. Confirm that no new Product Decision was introduced.

8. Confirm that no unrelated Login UI or behavior was redesigned.

The goal of this update is only to make the remaining approved SCR-AUTH-001 Login states reliably testable without regressing the states that have already passed verification.