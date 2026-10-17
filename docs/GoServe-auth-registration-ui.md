# GoServe authentication and registration UI contract

Updated October 15, 2026. This contract includes the latest user correction: email credentials come before one combined personal-information form. It supersedes the earlier three-form registration sequence, TIP-Google-only student entry and pre-created-demo-account restriction. **Figma first: the UI remains under user review.** Android, Supabase configuration, bootstrap rules, signup guards and session restoration have not been changed to implement this contract. No real account is created by a Figma interaction.

Actual Figma frame IDs and review links will be added after the revised panels are created. Existing frame IDs are reference material only; they do not establish that this contract is implemented.

## Entry methods and account boundary

- Keep both **Continue with Google** and **email/password** access.
- A new email user creates a demo account using their own entered email and password, then signs in with those same credentials before reaching Home. `student1@demo.com` is a permitted design example; the UI must not assume that it has a working inbox.
- Returning Google and email users whose persisted account requirements are complete sign in directly and skip the Terms/profile creation sequence. Registration is not an account picker or a route that assigns a preselected demo identity.
- A first-time Google user completes the same required identity, academic/contact and consent requirements. Google supplies the authenticated account email; the user does not create a GoServe password for that method.
- New self-created profiles are **students**. Staff, teller, office assignment and capabilities are not selectable registration options and are not granted by client input. Existing authorized office/teller account access remains separate.
- Successful authentication alone does not grant access to Home when required consent or profile information is incomplete.
- The main authentication entry heading is **Welcome**. The return-to-sign-in panel after account creation is headed **Sign in**.

**First-time means account state, not first launch on a device.** After authentication, the trusted account/profile lookup determines whether the account's required agreement and profile are complete. Reinstallation, another phone, a new browser session or signing out does not make a completed returning account a new registration. A local first-launch flag cannot replace that account lookup.

## Required flow

### Email registration

1. **Signup gate:** show Terms and conditions and an unchecked mandatory agreement. After agreement, choose email registration; Google remains the other available method.
2. **Email, password and confirmation first:** collect only these sign-in credentials. Validate the email, the confirmed password rule below and a matching confirmation before proceeding. Invalid credentials cannot open the personal-information form.
3. **One scrollable personal-information form:** collect first name, middle name or the no-middle-name exception, last name, student ID, course/program, year level, mobile number and gender together. Do not split these into identity and academic/contact steps.
4. **Create account:** validate every applicable personal-information field, preserve the reviewed information and prevent duplicate submission. Show Creating account… while saving; no success claim before confirmation.
5. **Account created:** display the user's entered email and a **Go to sign in** action. Do not open a dashboard from this panel or display a password.
6. **Sign in:** use the heading Sign in, prefill the same email and leave the password field blank. The user enters the password they just created.
7. **Authentication confirmed:** check the account's required agreement and complete profile. If something is incomplete, resume the relevant guarded step; otherwise open **empty Home**.

Account creation and sign-in are distinct steps even if a future provider can return a session during signup. The requested email flow still requires the explicit sign-in step. No forced email-verification/OTP detour is added to this UI contract; the future provider setup must support the agreed demonstration flow.

### First-time Google registration

1. Signup gate with mandatory agreement.
2. Accepted method choice → **Continue with Google**.
3. Google provider authentication.
4. **The same single scrollable personal-information form**, containing all identity, academic and contact fields listed above. Provider values may prefill matching fields but do not silently satisfy missing information.
5. Finish setup → profile saving → **profile save confirmed**, required agreement confirmed and authenticated account confirmed → **empty Home**.

Display the actual account email returned by the authenticated Google flow as read-only account information. Do not request a password or confirmation password on this branch. Cancelled or failed Google authentication returns to the method entry with a useful retry path.

The main **Continue with Google → Connecting to Google** sign-in entry is intentional for returning users. After provider authentication, look up the persisted account state: completed accounts proceed to their authorized Home; accounts missing required agreement or profile information enter the relevant onboarding steps before dashboard access. Do not force all Google sign-ins through the signup gate. This document specifies no Google provider domain configuration or verification setting.

### Returning users

Offer both Google and email/password sign-in. After confirmed authentication, use the trusted account/profile lookup to route completed returning accounts directly to their authorized Home, retaining their existing records. Skip the Terms/profile creation flow for those accounts. Route a new or incomplete account to only the missing required agreement/profile steps before Home. Do not show registration again because a user logs in on another device, reinstalls the app or starts a fresh session.

### Reopening, sign-out and session recovery

- On reopening, restore/check the persisted session and account/profile state before choosing a destination. Use the existing neutral branded startup/loading presentation while that decision is pending; do not flash an authentication form first.
- A valid persisted session with confirmed complete profile/consent opens the authorized dashboard directly. Do not require another sign-in or restart registration. Preserve the account's existing records.
- An authenticated account with incomplete required information resumes the combined personal-information form or missing agreement step. It does not enter Home first.
- A recoverable expired session may be refreshed before routing. A successful recovery follows the same complete/incomplete account guards.
- Sign-out or an expired, nonrecoverable session routes to Sign in. Sign-out clears authenticated access; it does not delete the account or turn a completed account into a new registration.
- A connection failure while checking session state is not proof of sign-out. Show a useful retry state without claiming successful authentication, exposing protected content or silently discarding an uncertain session.

### Forgot password and password recovery

Place **Forgot password?** below the password input on email/password sign-in panels, including Welcome and the post-registration Sign in panel. It opens the recovery email form; it does not start registration or require returning users to repeat Terms/profile onboarding.

1. **Recovery email:** collect or prefill the user's entered email. Reject blank or malformed email input with an adjacent error. Explain that the user needs an inbox they can access.
2. **Sending request:** prevent duplicate submissions while the reset request is pending.
3. **Email result:** use the same non-enumerating response regardless of whether an eligible account exists: **If an eligible account uses this email, we'll send a password reset link.** This confirms neither account existence nor delivery. Offer return to Sign in and a safe request-again route where allowed.
4. **Request failure:** explain that the request could not be completed, preserve the entered email and offer retry. Do not turn a transport failure into an account-not-found message.
5. **Recovery link:** a supported provider recovery link opens the reset-password form only after the native recovery context is validated. The form collects a new password and confirmation.
6. **Password validation:** use the same rule as registration: at least 6 characters in total, including at least one letter, one number and one symbol. Confirmation must match. Blank, weak or mismatching values show adjacent errors and cannot start the save.
7. **Saving password:** block duplicate save while the authorized update is pending. Do not claim that the password changed before confirmation.
8. **Confirmed completion:** show the password-updated result only after the actual update is confirmed, then return to Sign in with a blank password field. No dashboard shortcut or automatic authenticated-access claim is added by the success preview.
9. **Invalid or expired link:** do not open an editable reset form or expose account details. Explain that the link cannot be used; offer request a new link or Back to Sign in.
10. **Save failure:** preserve appropriate active-form input for correction/retry while the recovery context remains valid. If it has expired, use the expired-link state instead of retrying an unauthorized update.

Real email recovery requires a deliverable address and access to its mailbox. `student1@demo.com` remains the user's registration design example; it does not guarantee a mailbox or a usable reset link. This does not add an email-verification step to registration. Figma cannot send a recovery email, verify delivery or change a password. Future mail delivery, redirect/deep-link configuration and provider recovery support remain unimplemented requirements until UI approval and implementation.

Native guards must distinguish an explicit recovery-link entry from ordinary app reopening. Validate the recovery context before enabling reset, and handle a recovery intent before applying the usual completed-session → dashboard route. An ordinary session, local screen navigation or a Figma link alone is not recovery authority. Do not log or display raw recovery tokens. After recovery, normal sign-in still applies the persisted agreement/profile guards; completed returning accounts skip onboarding.

## Fields and validation

| Field | Requirement | UI behavior |
| --- | --- | --- |
| First name | Required | Blank input prevents account creation/profile completion; show an adjacent error and preserve user spelling. |
| Middle name | Required unless no middle name applies | Provide a **No middle name** checkbox, initially unchecked. Checking it makes the text field inapplicable and records the exception; unchecking restores the requirement. Do not insert a fabricated middle name. |
| Last name | Required | Blank input prevents completion; show an adjacent error. |
| Student ID | Required | Reject blank input and any failure of confirmed institutional validation. Do not invent length, prefix, numeric-only format or uniqueness rules. |
| Course or program | Required | Reject blank input. Use text entry or an approved actual program list, without inventing a catalog. Explain a rejected configured selection beside the control. |
| Year level | Required | Reject blank input; use approved values when available, without inventing a universal enum or range. |
| Mobile number | Required | Reject blank input or a failure of confirmed phone-format validation. Use phone-appropriate input without guessing a fixed length or country-code restriction. |
| Gender | Required, self-reported | Reject blank input. Use text entry or an approved inclusive control, without a binary enum or inference from another field. |
| Email | Required | Reject blank or malformed email input before proceeding. The Google account email is read-only on that branch. Do not assume a working inbox for demo addresses. |
| Password | Required for email registration/sign-in only | Masked input with a visibility control. For registration, require at least 6 characters in total, including at least one letter, one number and one symbol. Show the rule before submission and block the next form when it fails. Sign-in authenticates the existing password rather than treating the creation rule as proof of valid credentials. |
| Confirm password | Required for email registration only | Reject blank or mismatching input before the personal-information form opens. Recheck the match whenever the password changes. Preserve the other entries. |
| Terms agreement | Required before either registration method | Unchecked by default. Explicit acceptance enables continuation to method choice. |

Campus, date of birth and address were not requested and are not registration fields in this contract. Do not add role selection, mandatory profile photo or extra identity documents. Any existing optional profile-image feature remains outside the required registration sequence.

**Keep field labels clean.** Do not append requirement text to labels. Every field remains validated as specified; middle name is the sole conditional personal-information field, controlled by No middle name.

Validate failures beside the relevant control, focus the first problem and keep the rest of the form intact. A failed credential step cannot advance to personal information. A failed personal-information submission cannot create an account or finish Google setup. Back returns to the previous step without replacing values with sample data. The checked no-middle-name exception removes the middle-name requirement; unchecking it restores that requirement.

Password helper: **At least 6 characters, including a letter, a number, and a symbol.** This is the user's confirmed rule, not an inferred provider default. No extra password limit, email-verification policy or institution-specific input format is assumed.

### Failure, retry and form-state preservation

- Cover missing/invalid input for every field above, unchecked terms, the no-middle-name exception and password mismatch. Do not rely on an invisible generic validation result.
- A failed registration/profile save returns to the affected form with entered non-sensitive information and the selected method retained. A recoverable retry must not silently reset the wizard or substitute demonstration values.
- Account creation or profile-save outcomes that are uncertain after a connection failure must be reconciled before a duplicate create/save attempt. Show a confirmed outcome only when known.
- Failed sign-in uses a generic email/password error, preserves the entered email and allows correction/retry. Google cancellation or provider failure returns to its entry with a useful retry route. Neither failure grants dashboard access.
- Keep step/back/retry state during the active wizard. Passwords may be held only as needed for that active flow; do not persist or log plaintext passwords, show them on a receipt or prefill the password after creation.
- Across app reopening, restore the authenticated session and server-known incomplete profile as described above. Do not promise recovery of an unsubmitted password or claim an account exists before creation is confirmed.

## Terms, conditions and consent

- The signup gate includes a **Terms and conditions** button that opens a readable, scrollable panel. Closing it returns to the gate.
- Agreement copy: **I have read and agree to the Terms and conditions.** The checkbox is mandatory and unchecked initially. Continue is disabled until accepted.
- Acceptance must precede new-account registration/profile collection through either method. Do not pre-check agreement or use a Google button as implicit acceptance. Returning accounts with confirmed required agreement and completed profiles skip this registration gate.
- Draft terms content is labeled **Draft terms · UI preview**. Approved terms/privacy notice content is still required before a real registration flow is enabled. This design is not a legal-compliance claim and does not assert that draft copy is an approved policy.
- Existing privacy/profile consent must be preserved as an explicit guard. It may be presented alongside the approved notices in the revised gate rather than duplicated as an already-checked form later. Both methods require the necessary consent and a completed profile before Home.
- Seryo/AI processing consent stays separate and optional. Declining AI consent must not block ordinary account access.
- The future implementation must retain the accepted notice identity/version and confirmation state needed by its guards. No acceptance record or server persistence is claimed in Figma.

### Terms access from Settings

Student, office-staff and Cashiering/teller Settings each include a **Terms and Conditions** button in both Light and Dark themes. It opens a read-only, scrollable Terms viewer. **Back** returns to the originating Settings screen.

This Settings route is separate from registration agreement. It has no agreement checkbox, registration action or reconsent requirement; opening or closing it does not restart onboarding, change an existing account's agreement state or send a returning user to signup. Existing users may read the notice without repeating registration.

When implemented, the viewer and registration gate use the same approved notice content and version. Do not invent a version or claim approval. The current draft remains labeled **Draft terms · UI preview** and represents no real consent or legal-compliance result.

## Floating-card layout

Use a centered, rounded authentication card with one restrained soft shadow. Follow the existing light/dark palette, card treatment and theme-aware contrast. Keep the supplied logo prominent, Montserrat ExtraBold 800 headings, Karla Regular 400 body and Karla SemiBold 600 labels. Use the current typography guide for sizes and line heights.

The card fills the available width inside the existing authentication margins. Center it vertically when its content fits. When the form is taller than the available window, let the whole form scroll from its top; do not vertically center content above the visible area or clip required fields. Keep the step title, field labels, adjacent errors and actions in content-hugging layout groups.

Actions have at least 48dp touch targets and grow for wrapped text. Inputs and cards grow as needed. Respect real system/keyboard insets during Android implementation. No bottom app navigation appears before authenticated access. Check compact 320dp width and larger text after composition; this document does not claim those checks have passed.

Loading uses the existing prominent logo and clear operation text such as Creating account… or Signing in…. Avoid invented percentages or artificial delays. Success panels are distinct from loading/error states.

## Bounded state inventory

| Composition | Required states and routes |
| --- | --- |
| Signup gate | Unchecked/disabled Continue; accepted/enabled Continue; Terms preview open/close. |
| Settings Terms viewer | Student, office staff and Cashiering/teller Settings in both themes → read-only Terms → Back to the originating Settings; no registration or reconsent action. |
| Method choice | Google and email both visible; Back to gate. |
| Email/password first | Empty; malformed email; password under 6 characters or missing a required category; confirmation mismatch; accepted credentials → one personal-information form. |
| Combined personal-information form | All identity, academic and contact fields together; empty/invalid input; checked no-middle-name exception; valid review fixture; scrollable content. |
| Creation operation | Creating, failed/retry preserving information, account created → sign-in only. |
| Sign-in | Welcome on the main entry; Sign in after registration; entered credentials, authentication in progress, failure/correction/retry; Google remains available. |
| Password recovery | Forgot password? → reset email, sending, non-enumerating email result, email validation error or request failure/retry; validated recovery link → new password, validation error, saving, confirmed completion, expired-link or save-failure/retry state. |
| First-time Google profile | Same one combined personal-information form, profile saving, failure/retry and confirmed profile; no password fields. |
| Startup/session guard | Valid persisted session and complete account → dashboard without auth-form flash; missing profile → resume form; recoverable session → check/refresh; signed out or nonrecoverable expiry → Sign in. |
| Guarded completion | Trusted account lookup: missing agreement → gate; incomplete profile → combined form; new completed student → empty Home; completed returning user → authorized Home with existing records. |

These are reusable composition/state families, not a request for a separate full-screen page for every field error. Static prototype examples must not claim that entered text was captured, credentials matched, accounts created or profile data saved by Figma. Actual frame IDs and route targets are pending the root design work.

## Implementation boundary and later verification

The current code's public-signup block and assigned-account assumptions remain unchanged until UI approval and the later implementation task. Supabase Auth setup, trusted bootstrap/profile provisioning, the confirmed password rule, accepted-notice guards, session restoration, password recovery and student-only self-registration need coordinated implementation before real testing. This document does not claim that the backend or Android already enforces the new behavior. Client metadata cannot grant staff/teller authority.

After UI finalization, verify credential-first order, every input error, the 6-character/letter/number/symbol rule and matching confirmation before the combined form; creation alone cannot enter Home; the same email/password must authenticate afterward; both methods enforce first-time agreement/profile completion; completed returning sessions open their dashboard without an auth-form flash; incomplete profiles resume correctly; sign-out/nonrecoverable expiry returns to Sign in; the no-middle-name exception works; retries preserve appropriate input; and new students have no fabricated records. Provider/session, RLS and device checks remain separate from Figma review.

Recovery verification must additionally cover malformed email, a non-enumerating request result, request failure/retry, deliverable-mailbox testing, valid/invalid/expired recovery links, weak and mismatching passwords, save failure/retry, duplicate-action prevention and confirmed completion returning to Sign in. These are future verification criteria, not results established by the UI preview.
