# Current acceptance inventory — 22 October 2026

Fresh local gate: **964 native tests / 82 suites**, zero failures/errors/skips; **307 backend checks**, zero failures/skips. Debug assembly passed; lint reported zero errors and 138 warnings. [Exact proof](../output/validation/final-2026-10-22/verification.json), [delivery and source map](GoServe-delivery-v2.3-2026-10-22.md), [original required results](GoServe-acceptance-traceability-v2.3.md).

Every release row remains Pending until its live evidence is obtained. Native classes below are in `android/app/src/test/java/ph/edu/tip/goserve/ui/`, with repository/integration checks in adjacent `data/`. Backend checks are in `backend/tests/`. The listed local checks are part of the fresh full run; they do not substitute for live services or competing transactions.

| AT | Implementation and local evidence | Remaining release evidence |
| --- | --- | --- |
| 01 | AuthViewModel/AuthRepository, AuthLifecycleTest, AccountIntegrationTest; registration Edge/SQL | Approved active Terms, actual completed signup and installed flow |
| 02 | Adjacent field/password/profile errors and retained forms; registration/account/native checks | Real invalid-input attempts, device focus/accessibility |
| 03 | Student-only signup, server-owned role/department, production empty states; bootstrap/SQL | Independent live privilege denial and new Student records |
| 04 | PKCE/nonce and verified exact TIP domain; Google-domain hooks/backend/native checks | Real institutional/personal/cancel/error device OAuth outcomes |
| 05 | Session restoration, account leases/generation guards; account-switch/lifecycle checks | Installed restart/process/expiry, independent live users |
| 06 | Server-version Terms, read-only viewer, verified recovery and password change checks | Text approval/activation and reachable recovery mailbox |
| 07 | RLS/grants/private registry SQL, scoped repository and Storage boundary checks | Actual managed Storage/Realtime denials across users |
| 08 | OfficeManagementRepository/VM/native, OfficeViewModel, OfficeAnnouncementsViewModel, OfficeInformationInteractionTest | Real staff/student persistence. Readable changes invalidate immediately; RLS-hidden notices reconcile at the next 30-second foreground read |
| 09 | Pending/free/required catalogue/quote/native editor and SQL checks | Actual office-approved declarations, fees and currencies |
| 10 | Frozen quotes/snapshots, stale rejection, live choice refresh preserving notes; RequestCompose/SQL | Concurrent live catalogue edits/submission |
| 11 | Explicit reviewed idempotent submit, scoped request list/queue and canonical reread | Student/office Realtime and competing live submissions |
| 12 | Bounded verified evidence/private viewer, repository/native/Edge parsing checks | Approved file policy, staged function deployment and managed Storage/Edge proof |
| 13 | Seven statuses, scoped reviewed reasons/transitions; RequestAction/native/SQL | Real authorized and denied transitions |
| 14 | Paid audit preserved separately; cancellation/rejection/payment SQL checks | Approved operating policy and live manual-follow-up proof |
| 15 | Persisted inquiry repository/VM/inbox/thread, exact retry and visible-text read evidence | Live two-role exchange/read and race proof. No fabricated assignment roster |
| 16 | Teller-scoped lookup/cash/history, expected version/key; native/repository/SQL | Configured cash scope and simultaneous Teller confirmations |
| 17 | Adapter signature/amount/status checks and disabled endpoint guards | Approved eGovPay protocol, reviewed transport and real callback/reconciliation |
| 18 | Five payment states; browser return never declares paid | Approved isolated simulator/provider demonstration remains unavailable |
| 19 | Ready never generates a code automatically; native/delivery SQL guards | Actual office Ready transition |
| 20 | Owner Claim, real server-code ZXing QR, stable retry/expiry/policy checks | Approved pickup policy and competing real claims |
| 21 | Optional Google code scanner, manual fallback and explicit review; PickupInteractionTest/PickupQrTest | Installed scanner/module/network and actual permission/fallback cases |
| 22 | Explicit atomic handover/consume; pickup/native/SQL checks | Simultaneous live handovers, expiry/reuse/wrong-office and two-role update |
| 23 | Verified PDF release, saved download acknowledgement, explicit receipt; Delivery/StudentDelivery checks | Staged functions, approved rules, real transfer/denial/replacement races |
| 24 | Guidance/matching CCS Secretary slot/booking UI, server capacity checks | Actual approved slots/policies and competing sessions; Secretary-only scope supersedes Faculty contact |
| 25 | Reviewed cancel/reschedule, stable unknown retry, failed move keeps booking; ConsultationVM/native/SQL | Approved notice rules, live races and notifications |
| 26 | Shared JWT Realtime, order/generation/account guards, coalesced reload and lifecycle cleanup | Real two-role interruption/JWT/hidden-row RLS proof |
| 27 | Separate AI consent, immutable decision/version/key retry, no auto-send; SeryoVM/consent/native | Staged consent endpoint, approved notice and real audit |
| 28 | One bounded local PDF/JPG/PNG, cancel/replace/remove, unsent Explain/Extract; attachment/draft checks | Approved provider file protocol, real transport/deletion |
| 29 | Truthful unavailable state, no generated fake response or autonomous writes | Approved knowledge/procedures/limits and implemented provider scope/refusal proof |
| 30 | Honest timeout/offline/uncertain states and cleanup blocking; local Edge/state checks | Provider retries and verified immediate deletion/recovery |
| 31 | Terminal-file cleanup SQL/worker state and separate financial audit checks | Live schedule/object absence/retries; approved abandoned-upload policy and extension |
| 32 | Fresh debug APK, native layout/lifecycle/motion; Light/Dark and 320dp/2x controls inspected | Installed smoke scope is separate; physical devices/full API/IME/TalkBack/focus/contrast coverage open |
| 33 | Integrated native code, identified APK and checks, source map and demo preparation | Full live legal/configured cash/digital/inquiry/consultation/provider/cleanup demonstration |

Exactly six live demo identities are authorized: Registrar, Accounting, Tellering, Guidance, CCS Secretary and CCS Student. No Faculty demo exists. The Student's trusted department is `DEP-CCS`; department contact is only the matching Secretary. No additional test users or business records were created during finalization.

The core/account/photo schema and three account functions were previously deployed with approval. All other file/Seryo/cleanup transports and provider activation remain outside that rollout. Demo accounts return `accept_terms` while approved Terms are inactive. Local code does not bypass this gate.
