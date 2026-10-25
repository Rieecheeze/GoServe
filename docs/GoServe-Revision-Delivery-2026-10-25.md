# GoServe Android Test Release

25 October 2026, Asia/Manila. This is the current release record and supersedes older APK, SMTP, TOTP and recovery-activation notes.

## APK And Source

The APK was generated with **Android Studio → Generate APKs**, with `:app:assembleDebug` shown in Build Output. The IDE reported `BUILD SUCCESSFUL in 15s`, with 67 actionable tasks. No command-line APK assembly substituted for this build.

- Shareable APK: `C:\Users\Rieecheeze\Documents\ChatGPT\GoServe\output\android\GoServe-v2.3-test-2026-10-25.apk`
- Original IDE output: `C:\Users\Rieecheeze\Documents\ChatGPT\GoServe\android\app\build\outputs\apk\debug\app-debug.apk`
- Application: `ph.edu.tip.goserve`, version `2.3-test`, version code `2`.
- Android 8.0/API 26 minimum; target and compile API 35.
- Size: **12,611,564 bytes**.
- SHA-256: `cfbdb0d6215396916222d77b008edff3907fd50d367cdb07a83876b14f512498`.
- Built at `2026-10-24T17:39:59.8674698Z`, or 25 October in Manila.
- APK Signature Scheme v2 verification passed with one signer.
- IDE proof: [Android Studio build output](../output/validation/android-studio-apk-success-2026-10-25.jpg).

Complete runnable source remains in [android](../android), with backend migrations, fixed handlers and tests in [backend](../backend). Open the `android` folder in Android Studio. Existing local configuration is preserved. The packaged app targets the configured GoServe Supabase project using its publishable key.

The requested baseline was pushed before these revisions. **No additional Git commands, commits or pushes were performed during this finalization.**

## Changelog

- Replaced SMTP/TOTP recovery with ten independently generated, unique, account-bound, single-use recovery codes. Plaintext is returned only when a set is generated; the backend stores digests. Saving the set requires an explicit acknowledgment before protected app access.
- Added Forgot Password and Account Change Password flows using an email address and saved code, followed by a new password and ordinary sign-in after the server confirms completion. Uncertain responses reconcile the existing operation instead of repeating a password change. The last used code forces replacement setup.
- Preserved **Continue With Google** in both themes. New registration requires a verified Google identity with the exact `tip.edu.ph` domain. Existing and demo password sign-in remains available.
- Fixed concurrent sign-in and recovery ownership races, stale Google attempt cleanup, account transitions and rotation during a pending sign-in. Old or revoked sessions cannot use protected file or recovery routes.
- Rendered legal headings in bold and body text normally, removed visible Markdown heading markers, and rendered legal table categories without raw table pipes. Approved legal content and its digest remain unchanged by presentation formatting.
- Reduced ordinary phone sign-in spacing; placed a smaller Forgot Password action beneath Password. Accessibility layouts retain scrolling when screen height, keyboard space or text size requires it.
- Reorganized More into Account, Seryo AI, Terms And Conditions and About, with Appearance beneath those groups. Account contains profile information, profile photo, password change and recovery codes.
- Kept the navigation capsule icon-only, removed corner elevation that caused dark patches, and aligned the revised light/dark surfaces and static headline capitalization.
- Fixed Seryo composer sizing and reduced-height layout. Preview chat has a local consent decision and labeled local guide responses. Authenticated chat uses the protected live transport.
- Reduced the chat plus menu to Attach File and Open Camera. Moved privacy, consent, settings and help into More → Seryo AI. A saved consent decision is read-only in its record; withdrawal is a separate settings action.
- Kept Seryo focused on GoServe public service information through fixed, validated server operations. User text does not become SQL. Private account records, files and credentials are not supplied to the provider. Input, intent, identity and output checks remain in place.
- Preserved the existing eGovPay **TEST MODE** checkout and verification workflow, plus private request-file handling and the approved cleanup schedule for files belonging to requests terminal for 30 days.

## Recovery And Supabase Activation

The additive recovery schema and reviewed Edge Functions are deployed. Current Terms are **`goserve-test-2026-10-24-v2`**, matching the approved document SHA-256:

`d000c6b52c8788f44eedfad8b4a2f1a2a1bfea1ed3241b513ad6f6db91a618ea`

The v1 document is inactive and retains its original digest `e98c47b74dd61b5ebda9605f164dc7e87575a68b120d1fefe087366344d9a4f8`. Both `enforce_sessions` and `enforce_readiness` are now true. No participant acceptance, profile information or saved-code acknowledgment was fabricated during verification.

The approved ingress setting is `GOSERVE_RECOVERY_PROXY_HOPS=0`. It activates a single validated `CF-Connecting-IP` value, with no `X-Forwarded-For` or `X-Real-IP` fallback. Two controlled clients proved stable per-client buckets and separate client budgets; forged CF headers were rejected upstream. Public recovery has durable account and IP limits and generic failure responses.

Evidence: [ingress proof](../output/validation/recovery-ingress-verified-2026-10-25.json), [public boundaries](../output/validation/recovery-boundaries-2026-10-25.json), [live account verification](../output/validation/demo-login-code-recovery-2026-10-25.json), [release verification](../output/validation/revision-release-2026-10-25.json).

## Demo Accounts

All six accounts passed fresh live password sign-in, trusted role/office/department checks, and session revocation checks after activation. Existing passwords were preserved.

| Account | Login |
| --- | --- |
| Registrar | `registrar@demo.goserve.test` |
| Accounting | `accounting@demo.goserve.test` |
| Tellering | `teller@demo.goserve.test` |
| Guidance | `guidance@demo.goserve.test` |
| CCS Secretary | `secretary.ccs@demo.goserve.test` |
| CCS Student | `student.ccs@demo.goserve.test` |

These are test login identifiers, not email inboxes. Each tester must accept current Terms and save their recovery codes. The CCS student must also complete their profile. Student contact with the department routes through the CCS secretary. Verification signed out only its own temporary sessions and did not change any password.

## Main Files

| Responsibility | Source |
| --- | --- |
| Sign-in, TIP Google and recovery host | [AuthActivity.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/AuthActivity.java), [AuthRepository.java](../android/app/src/main/java/ph/edu/tip/goserve/data/AuthRepository.java) |
| Account and grouped navigation | [AccountFragment.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/AccountFragment.java), [MoreFragment.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/MoreFragment.java) |
| Recovery UI and state | [RecoverySecurityFragment.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/RecoverySecurityFragment.java), [RecoverySecurityViewModel.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/RecoverySecurityViewModel.java), [RecoveryRepository.java](../android/app/src/main/java/ph/edu/tip/goserve/data/RecoveryRepository.java) |
| Legal formatting | [LegalTextRenderer.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/LegalTextRenderer.java) |
| Seryo screen state and transport | [SeryoViewModel.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/SeryoViewModel.java), [SeryoRepository.java](../android/app/src/main/java/ph/edu/tip/goserve/data/SeryoRepository.java) |
| Recovery server contract | [recovery-contract.md](../backend/recovery-contract.md), [recovery.ts](../backend/lib/recovery.ts), [account-recovery](../backend/functions/account-recovery/index.ts), [recovery-control](../backend/functions/recovery-control/index.ts) |
| Schema and rollout | [account recovery migration](../backend/migrations/20261024003558_account_recovery.sql), [rollout guide](../backend/recovery-rollout/README.md) |

## Animation Details

- [SharedMotion.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/SharedMotion.java): clickable controls scale to `0.97` over **80 ms** when pressed and return to `1.0` over **120 ms**. Edit fields are excluded. Disabled system animations leave scale at one.
- [BrandLoadingView.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/BrandLoadingView.java): the initial loader uses the brand logo without a mascot. A **1,000 ms** repeating linear phase rotates a 90-degree arc through `phase × 360 − 90` degrees. It stops when hidden/detached or reduced motion is active.
- [SeryoMascotView.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/common/SeryoMascotView.java): chat artwork switches between idle/listening and thinking artwork, with a greeting/loading state supported by the component. Cycles are **2,800 ms idle**, **1,100 ms thinking** and **1,400 ms greeting/loading**. Breathing uses `(1 − cos(angle)) / 2`; vertical bob uses a sine wave capped at 3.5% of the view size. Loading/thinking tilt uses sine amplitudes of 2/3 degrees. Visibility and reduced-motion handling stop the loop. These are native pose animations, not a claim of a complete 18-action eight-frame sprite export.

## Verification

| Check | Result |
| --- | --- |
| Final Android unit suite | **PASS: 1,028 tests**, zero failures/errors/skips |
| Android lint | **PASS: zero errors**, 189 warnings remain |
| Backend | **PASS: 437 checks**: 207 runner tests, 42 bootstrap assertions, 159 SQL assertions and 29 Edge/adapter assertions |
| Android Studio APK build | **PASS**, Build Output inspected |
| APK signature/version/hash | **PASS**, v2 signature, version 2.3-test/code 2; original and shareable hashes match |
| Packaged configuration | **PASS**, expected project URL and publishable key; no secret-key prefix, Google OAuth-secret prefix, demo password or private credential file found in the inspected APK content |
| Live demos after activation | **PASS: six of six**, including trusted scopes and revoked-session denial |
| Hosted recovery ingress | **PASS**, two controlled clients and spoofed-header variants |
| Public recovery boundaries | **PASS: 14 checks**, including retired routes and rate limiting |
| Revised UI fixtures | **PASS**, native layout/flow tests and inspected captures cover phone sign-in, Terms rendering, grouped settings, chat height, recovery states, themes and enlarged text |

Native proof is under `android/app/build/outputs/screenshots`; unit and lint reports are under `android/app/build/reports`. Final full-run logs are `tmp/codes-only-android-release-gate.log` and `tmp/codes-only-backend-cf-final.log`.

## Design Gate

The approved Figma/screenshots remain the direction: monochrome campus service screens, ENERGY 1 / RHYTHM 1 / MOTION 2. The bridge logo and Seryo artwork supply identity; press motion confirms touch and mascot state indicates chat activity. Spacing separates tasks, capsule navigation identifies the active destination, and flat surfaces remove the corner-shadow defect.

- **Hard gate PASS for the revised surfaces:** native layout/flow tests and captures cover overflow, actionable controls, empty/loading/error states and both themes. The app uses real service states and approved legal text; no invented usage statistics, testimonials or security certification claims were introduced. Native controls retain focus/accessibility semantics. The APK was built in Android Studio.
- **Purpose gate PASS:** the logo/mascot are user-provided brand references; motion has the purposes above. Monochrome colors and existing brand typography follow the approved direction. No decorative gradients, glows, background grids or glass layers were added. Removed elevation addresses the reported dark corner patches.
- **Liveliness gate PASS:** the declared dials match the restrained screens, with a clear primary action and state-driven accent motion. The bridge mark and chat mascot repeat the product identity.
- **Craftsmanship gate PASS for the requested revisions:** grouped navigation follows the account/help tasks, legal headings and static headlines are consistent, and both themes and reduced-height/font-size layouts have recorded coverage. No unsupported production-readiness or end-to-end device claim is made.

Applied requested code/UI skills during the implementation include antislop, antislop-code, antislop-ui, antislop-layoutmobile, unslop, karpathy-guidelines, frontend-design and impeccable. Parallel implementation/review, Supabase guidance and verification-before-completion supported the work. The final APK was generated with the Computer Use workflow.

## Test Scope And Remaining Acceptance

This is a **supervised test APK**. eGovPay remains TEST MODE; real payments are disabled. Selected chat attachments remain local; no unsupported provider file-analysis behavior is advertised.

Fresh live Google sign-in on a participant's phone and a participant-authorized recovery-code password reset were not performed. The tests do not impersonate acceptance, code saving or a student's profile. To test on a phone, install this APK, sign in with a demo or TIP Google, complete the presented onboarding, save the recovery set, and test a saved code with that account's authorization. A successfully consumed code cannot be reused.

Provider password/session behavior has local adapter coverage and source evidence for the deployed GoTrue version, rather than a claimed live participant password mutation. PGlite checks prove transitions, constraints and idempotence; they are not separate-connection PostgreSQL contention tests. Existing lint warnings remain. Camera cleanup covers success/cancel/account change; process-death orphan cleanup has not been represented as verified.
