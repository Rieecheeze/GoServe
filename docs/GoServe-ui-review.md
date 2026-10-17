# GoServe UI review

Status: **Figma review candidate — awaiting user finalization**, 15 October 2026. Android Java/XML implementation and builds remain on hold until the user finalizes the UI.

## Open the design

The canvas is organized into 11 main sections and 29 sections overall, with Light/Dark pairs side by side. Screen notes sit beneath their screens; components, phone-size checks and retained material have separate sections. See the [organization guide](GoServe-figma-organization.md) for direct section links.

[Open the Figma review index](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=175-7272). It links the primary screens, compact-phone examples and all 96 audited state records in Light and Dark.

- [Home](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=19-93)
- [Seryo](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=24-51)
- [Cashiering filter](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=45-54)

## Current design

- Montserrat ExtraBold 800 headings; Karla Regular 400 body and SemiBold 600 UI labels. The supplied logo appears alone in the Home header and in authentication loading states.
- Icons-only student navigation: Home, Requests, Consult, Inbox, AI Chat and More. The capsule is 56dp high, with 22dp icons, 36dp selected circles and at least 48dp targets. Staff and Cashiering navigation use the same treatment.
- Light mode has a dark capsule and light icons; Dark mode uses the inverse. There is no reserved label row.
- Search is 48dp high. Category filters have 32dp visuals inside 48dp targets. See all uses a compact text action. Growing controls preserve text when it wraps.
- Office lists scroll. Home reflows from two office columns to one on a compact phone. Text cards grow and long content scrolls above navigation.
- New-account Home has no active-request card. Requests, Inbox, Notifications and My appointments have empty states; populated examples remain separate.
- Offices declare no payment, payment required, or pending review. Free requests skip checkout; Cashiering handles all payments. Completed request phases use green checks and explicit status labels.
- Seryo has an open welcome canvas and a composer above navigation. Send remains disabled while the provider contract and chatbot procedures are pending.

## Panel coverage

All 14 audit families now have 96 state records represented by 192 editable Light/Dark frames: empty accounts, authentication recovery, directory loading/errors, fee-aware request review, office fee declarations, cancellation/rejection, payment outcomes, Cashiering confirmation, consultation changes, private files, pickup, planned Seryo states, office publishing, and inquiry assignment.

Payment-provider and future Seryo outcomes are labeled Planned. Prototype navigation performs no real account, request, booking, payment, file or AI operation.

## Verification

- 332 visible current mobile frames; 3,492 ordinary text nodes; zero detected geometry issues and zero unapproved legacy body fonts. Two official Google G glyphs retain their brand font.
- 2,280 prototype links to 208 unique destinations; no missing or hidden targets.
- 14 editable compact-width proofs at 320 logical units include ordinary text and simulated 2× text. All passed their geometry checks; the two fee editor proofs were checked again after their service-context copy changed.
- Navigation foreground/background token contrast: 13.90:1 in both themes.
- The default Home, Inbox and Notifications contain no fabricated personal records. The six out-of-scope OSA roots are hidden and have no incoming active links. Seryo Send has no reaction.
- Final visual checks covered the revised request-review information card, authentication typography, compact Home/Seryo examples, fee declarations and review index.

These checks verify Figma structure and selected rendered compositions. They do not establish Android system font scaling, device rendering, keyboard/IME behavior, network behavior, backend authorization or acceptance-test execution.

## Next gate

The user reviews and finalizes the Figma UI. Then implement the approved design in Java/XML, verify it on Android, and update the project plan with implementation evidence. The existing Android source and v2.2 plan remain drafts against this design.

Detailed records: [panel audit](GoServe-ui-panel-audit.md), [typography and brand](GoServe-typography-and-brand.md), [content validation](GoServe-figma-content-validation.md), [state ledger](GoServe-figma-state-ledger.json).

## Authentication, Terms and recovery checkpoint — October 15, 2026

This later checkpoint supplements the historical counts above. Authentication now has **39 paired cases / 78 editable Light/Dark screens**. Start at [Welcome — Light](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=19-2) or [Welcome — Dark](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=36-2); the [authentication contract](GoServe-auth-registration-ui.md) records the required guards.

- Both Google and entered email/password remain available. First-time registration requires agreement before collection, then email credentials before one combined personal-information form. Passwords require at least six characters including a letter, a number and a symbol, with matching confirmation. Google profile completion does not ask for a GoServe password.
- The combined profile collects first, middle and last names, student ID, course/program, year level, mobile number and gender. No middle name is the explicit conditional exception. Account creation returns email users to Sign in; it does not open Home. The startup design requires a confirmed valid session and complete account to open the authorized dashboard without repeating registration or flashing Welcome first.
- **Forgot password?** appears below the password field in three paired email sign-in variants (six screens). Recovery has **11 paired cases / 22 screens**, including validation, sending, non-enumerating request feedback, password entry, save uncertainty, expired-link recovery and a confirmed-result example. Busy previews do not fake a successful email or password update. Recovery returns to sign-in without substituting the registration demo email.
- Read-only Terms viewers are [Light](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=213-7486) and [Dark](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=213-7515). They are linked from Student Settings ([Light](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=33-106), [Dark](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=36-1818)), Staff Settings ([Light](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=33-132), [Dark](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=36-1844)) and Cashiering Settings ([Light](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=33-158), [Dark](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=36-1870)). Back returns to the originating context; viewing Terms does not request agreement again or restart registration.
- Four contextual Light/Dark pairs are being added at this checkpoint: required-fee submission receipt, saved fee declaration, cancelled free-request detail and cancelled paid-request detail. Their final verification evidence will be recorded after the audit.

The Terms text remains a draft requiring institutional approval. These designs register no actual demo accounts and perform no provider operation. **Android implementation and provider calls remain on hold until the user approves the Figma UI.** This checkpoint adds no claim of completed native, backend or provider verification; exact post-change Figma audit results follow separately.



## Final review checkpoint — 2026-10-15

The Figma candidate is ready for user review. UI approval is still pending; Android implementation remains on hold.

- 434 screen and proof frames are organized in 217 Light/Dark pairs, including 26 phone/text-size proofs. All 777 tracked source objects are grouped inside 29 Sections and 11 top-level Sections. The grouping operation preserved screen sizes, contents and reactions.
- The canonical general state library contains 100 states / 200 theme frames. Authentication contains 39 cases / 78 frames, including 11 password-recovery cases / 22 frames. G02.2/G02.3 overlap the authentication set, so these counts must not be added as disjoint totals.
- Forgot password appears directly below Password in three paired login variants (six main frames), with recovery email, validation, sending, neutral receipt, network failure, reset/confirmation, saving, uncertain outcome, expired-link and confirmed-completion designs.
- Student, Staff and Cashiering Settings in both themes open the read-only Terms viewer and return with Back. Draft terms are visibly marked. Reviewing Terms does not repeat registration or change consent.
- Geometry checks passed for 109 changed/auth/context/index frames and proofs: 1,782 ordinary text nodes, zero issues and zero legacy font mismatches. A follow-up check passed for the 22 Home/Requests/Settings frames affected by the last edits: 458 text nodes, zero issues and zero legacy font mismatches. These sets overlap.
- Auth acceptance passed for 78 source frames, 12 auth proofs and six Settings screens: zero findings. All 2,952 prototype links resolve to visible destinations; no missing source objects, loose tracked objects, overlapping top-level Sections, Section overflow or complete-UI raster images were found.
- The requested Home preview banner and Requests demo disclaimer were removed from all 16 matching copies, including Light/Dark variants and responsive examples. The legacy content source and an idempotent cleanup helper preserve that change. The Settings appearance label no longer overlaps its moon icon.
- Screenshots reviewed: Welcome in both themes; dark Terms viewer; and updated light Home, populated Requests and Settings. Targeted spacing fixes passed post-fix visual review.

These results describe editable Figma designs and simulated text enlargement. They do not prove account creation, email delivery, password reset, native session restoration, Android device/IME behavior, provider calls or institutional approval of the draft Terms.

Evidence: [final UI verification](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-final-ui-verification-2026-10-15.json). Current IDs: [auth ledger](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-auth-screen-ledger.json), [general ledger](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-figma-state-ledger.json).

