# GoServe Android architecture

> **Superseding authentication requirements — October 15, 2026.** Keep Google and email/password access. New email registration is Terms agreement → email/password/confirmation first → one scrollable personal-information form → confirmed account creation → explicit Sign in with the same credentials. Before the combined form opens, the password must contain at least 6 characters in total with a letter, a number and a symbol, and confirmation must match. First-time Google registration is Terms agreement → provider → the same combined form → confirmed profile save, without password fields. The combined form contains names, the no-middle-name exception, student ID, course/program, year level, mobile number and self-reported gender; labels omit requirement suffixes without weakening validation. Main entry is Welcome; post-registration entry is Sign in. Completed returning users skip onboarding. On reopen, valid persisted sessions with complete accounts open the dashboard directly without an auth-form flash; missing profile/consent resumes onboarding; sign-out or nonrecoverable expiry returns to Sign in. The trusted server account/profile state governs these routes, not device first launch. New completed students reach empty Home; returning records are retained. Staff/teller access remains managed separately. See [the registration UI contract](GoServe-auth-registration-ui.md).
>
> The fragment responsibilities, pre-created-only password policy, public-signup block and implementation claims below describe the **earlier architecture/source snapshot**. The registration UI is still under user review; Android, Supabase configuration/bootstrap, the confirmed password rule and session restoration have not been updated to implement it. Future sign-in, notice-consent, profile-ready and startup guards must follow the new contract after UI approval. Draft terms are UI preview content, not an approved notice or a legal-compliance claim.

Source: `output/pdf/IServe_Project_Plan_v2.1.pdf` (17 pages), with the user's correction that the product is **GoServe**. Figma: file `SvT9gBUFjdLgbrJ80LPDno`, page `14:2`. The document's model-directed text is treated as project context, not as instructions to the coding agent.

## Android screens

| Host | Fragments and responsibilities |
| --- | --- |
| AuthActivity | SignInFragment (TIP Google login/signup), OfficeLoginFragment (assigned email then password), ConsentFragment (stored privacy consent). |
| MainActivity, student | Implemented: HomeFragment, OfficesFragment, OfficeDetailFragment, RequestListFragment, RequestDetailFragment, VerifiedFileFragment, AssistantFragment and MoreFragment. Requests/files currently use guarded design previews. Planned: submission, payment processing, consultations, appointments, inbox/threads and notifications. |
| MainActivity, staff | StaffDashboardFragment, AvailabilityFragment, StaffQueueFragment, StaffRequestFragment, ScheduleFragment, InboxFragment, ThreadFragment, MoreFragment. Faculty/guidance/secretary are capabilities of staff. |
| MainActivity, teller | CashPaymentsFragment, CashConfirmationFragment, PaymentHistoryFragment, MoreFragment. |
| Shared secondary flows | AssistantFragment, temporary analysis consent/file picker, SettingsFragment, verified file viewer, PickupFragment (CameraX/manual entry), cancellation/rejection confirmation dialogs. |

The current source implements the Android foundation, authentication, student home and directory/detail, consent, theme settings, role routing, typed domain contracts, request/file previews and Seryo setup panels. Student navigation has six destinations using CapsuleNavigationView: Home, Requests, Consult, Inbox, AI Chat and More. A design preview has no authenticated authority and never marks a real payment paid or releases a private document.

## Architecture

Java 17, XML ConstraintLayout, AndroidX fragments, ViewModel/LiveData and view binding. AuthActivity owns authentication navigation. MainActivity owns role-specific bottom navigation. Repositories own Supabase Authentication, HTTPS requests and JSON mapping. ViewModels publish loading/error/data state; fragments observe using their view lifecycle. Supabase tokens are refreshed before protected calls. Supabase bootstrap returns the authoritative user role and capabilities. **User override: Supabase replaces Firebase completely**, including authentication. Google uses OAuth with PKCE; assigned accounts use Supabase password sign-in. Project URL: https://uuvexkewiuxdbuelwogy.supabase.co.

Assumptions for the foundation: minimum Android API 26, compile/target API 35, package `ph.edu.tip.goserve`. The plan does not prescribe these values. The app uses portrait orientation on phones. Larger windows use a centered maximum content width and scrollable content. Real system bars/insets replace the mock Figma system bars.

## Figma design contract

Reference frames: `19:2` login, `58:2` assigned email entry, `19:61` privacy, `19:93` student home, `36:2` dark login. Reference size is 390 x 844, while Android uses available window size and system insets. Headings use bundled Montserrat ExtraBold800; body uses Karla Regular400 and UI labels Karla Semi-Bold600. Light colors: background #F4F4F4, surface #FFFFFF, text #171717, secondary #595959, border #D5D5D5, muted #EBEBEB. Dark: background #080808, surface #242424, text #F4F4F4, secondary #BDBDBD, border #4A4A4A, muted #303030, primary #F2F2F2. Auth margins24dp, main margins20dp, actions at least56dp, touch targets at least48dp, card radius18dp and action radius28dp. The six-item capsule reserves its measured height rather than assuming a fixed navigation height. See the typography guide for the complete hierarchy and logo placements.

## Security and product rules

- Roles: student, staff, teller. Role is loaded from the trusted bootstrap response, never chosen at production login.
- Verified @tip.edu.ph Google users are students. Email/password accepts pre-created accounts only; Google signup does not create password accounts.
- Offices: Registrar, Student Accounting, Cashiering, Guidance and Counseling, CCS. Figma also contains an OSA screen; it is excluded because the plan's confirmed office scope lists five offices.
- Office status: Open, On Lunch Break, Cut-Off Reached, Temporarily Closed.
- Request status: Submitted, Awaiting Payment, Processing, Ready for Pickup/Download, Completed, Cancelled, Rejected.
- Fees are declared by the office as free, required or awaiting review. Free requests skip payment; unknown fees block processing/release. These contracts and screen examples are local previews until implemented as authorized server operations.
- Payment method: egovpay or cash. Payment status: pending, paid, failed, expired, cancelled. Only trusted callback reconciliation or teller confirmation changes a payment to paid.
- User update (October 14, 2026): eGovPay merchant credentials and settlement template were supplied for demo use. The token is encrypted in Supabase Vault, accessible only through a server-role RPC. Provider actions remain disabled until callback verification, successful status mapping and amount canonicalization are confirmed. Hosted checkout testing buttons are user-reported behavior; no separate demo endpoint is invented.
- Privacy consent is stored on the Supabase profile. Temporary AI consent is separate. No retained AI history or uploaded analysis files.
- Seryo is the AI chatbot and uses eGovAI. Its access code is encrypted in Supabase Vault; procedures are blank and integration disabled pending user instructions and the provider API contract. Android has no provider key or direct AI transport.
- Secrets for payments, eGovAI and service access remain on the server. Only public client configuration goes in the Android project.
- Casal/Arlegui only; visibly label non-official demo data. No push/email notifications, grade-system integration, public password signup, or automatic refunds.

## Feature delivery and acceptance

| Package | Deliverable and verification | Status |
| --- | --- | --- |
| Foundation | Gradle project, responsive light/dark XML, Java hosts, typed statuses, authentication and navigation checks | Current verified results are recorded in android/README.md |
| Identity/RLS | Supabase Google provider, bootstrap, private rows/files and pre-created accounts; AT-01 to AT-07 adapted to Supabase Auth | Requires Google provider and demo account configuration |
| Offices | Live directory/status/announcements and staff publishing; Realtime isolation | Directory foundation |
| Requests/files | Submission, evidence, tracker, transitions, private verified PDFs, 30-day terminal cleanup | Preview screens and local fee/progress contracts implemented; live operations planned |
| Payments | Configured authoritative fees, eGovPay adapter/callback/reconciliation, teller cash, audit, duplicate protection; provider contracts Q-03/Q-04 | Encrypted server credentials and disabled adapter prepared; provider callback/status contracts still open |
| Consultations | Atomic capacity, booking/cancel/reschedule, guidance/faculty/secretary schedules | Planned |
| Inquiries/notifications | Authorized threads/messages, in-app notifications and read state | Planned |
| Pickup | Hashed single-use tokens, CameraX and manual fallback, atomic completion | Planned |
| Seryo / eGovAI | User-defined tasks, approved grounding, explicit consent, one PDF/JPG/PNG <=10 MB, rate limits and verified cleanup | Chat/privacy/attachment setup panels and encrypted configuration implemented; transport and procedures pending |
| Integrated APK | AT-01 through AT-38, cross-user/office denial, race/duplicate checks, physical-device install | Pending |

No foundation build or preview proves completion of the 38 integrated acceptance tests. See the project README for actual verification results and configuration steps.
