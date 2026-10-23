# GoServe v2.3 code delivery — 22 October 2026

Native Android implementation and local verification handoff. Git/GitHub was left untouched. Existing project configuration and credentials were preserved. This report supersedes earlier foundation-only implementation descriptions; it does not replace the approved institutional plan or declare live release acceptance complete.

Fresh full results: **964 native tests / 82 suites** and **307 backend checks** passed, zero failures/errors/skips. Debug assembly passed; lint reported **zero errors / 138 warnings**. The APK installed and started on the owned API26 emulator. Six-account authenticated API checks passed; Google is enabled in the configured Supabase project. Accounts remain at the inactive Terms gate.

## Runnable project

Open [android/](../android) in Android Studio. Java/XML/ViewBinding, Java source 17, Gradle 8.9, AGP 8.7.3, minimum API 26, target/compile API 35. Use an installed compatible JDK 17–22; verification uses JDK 21. Keep the existing SDK and Supabase configuration. No service-role or provider secret belongs in Android.

From the workspace root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1
npm test --prefix backend
```

The Android launcher uses cached dependencies, one worker and the workspace socket directory. First-workstation dependency setup is in [android/README.md](../android/README.md). Serialize Android tests, backend PGlite tests and the emulator on this machine.

Install the debug test build at [app-debug.apk](../android/app/build/outputs/apk/debug/app-debug.apk). It is a debug build, not a signed production distribution. Its exact test totals, size, SHA-256, lint findings and installed-device scope are recorded in [final verification](../output/validation/final-2026-10-22/verification.json). Full native and backend logs are retained under `tmp/goserve-final-*-2026-10-22.log`.

APK: **13,776,162 bytes**, SHA-256 `84ff0789ca2d1de05faa279f8d81aabe4f8711bb454e73046035953b50b63d25`. Installed Light/Dark loading/chat captures are in the same verification folder. The [six-second idle recording](../output/validation/final-2026-10-22/installed-seryo-idle.mp4) contains 118 encoded video samples; the [three-second reduced-motion recording](../output/validation/final-2026-10-22/installed-seryo-reduced-motion.mp4) contains one. This verifies the installed presentation fixture, with sample chat confined to debug source. Display/font/motion settings were restored and the owned emulator stopped. No on-device account or business-flow success is claimed.

The lint warnings include unused resources, overdraw, typography/RTL, input/accessibility suggestions, fixed portrait orientation, backup/extraction guidance and library suggestions. They remain in the exact report rather than being suppressed to produce a clean count. Physical-device/accessibility review and a production signing/package review are still required.

Use the [role-based demonstration guide](GoServe-demo-guide-v2.3.md) for preparation and honest test boundaries.

## Changelog

- Replaced request, office queue, inquiry, notification, consultation and pickup destinations with native forms backed by the reviewed Supabase contracts. Server identifiers, versions and canonical rereads drive displayed results.
- Added assigned-office status/notes, announcements and service/fee management. New notices/services start hidden. Every write needs review and explicit confirmation. Pending, free and required fees remain distinct; submitted request pricing/requirements remain frozen.
- Added shared inquiry conversations and actual read evidence. A long message cannot be acknowledged merely by jumping past unseen text. Opening a notification is separate from explicitly marking it read.
- Added Guidance and matching CCS Secretary consultation routes, availability, bookings, cancellation and rescheduling. Unknown writes preserve the exact reviewed command/key; failed rescheduling keeps the original booking. No Faculty demo or department contact route was introduced.
- Added physical Claim Request, real server-code QR rendering, optional Google code scanner, manual entry, office review and explicit handover. Scanning alone never completes a request. Added private verified PDF release/download acknowledgement and explicit student receipt.
- Added private profile-photo replacement, password change and verified recovery handling. TIP Google identity restrictions remain server enforced. Exactly six approved demo roles are preserved.
- Preserved unsent edits through view recreation and account-bound restoration where supported; cleared protected content and subscriptions on account replacement. Realtime refresh coalesces overlapping reads and rejects stale generations.
- Added foreground reconciliation for active announcements hidden by RLS. Normal readable changes invalidate immediately; a hidden-row update may need the next 30-second canonical read. No polling runs while the office detail screen is paused.
- Kept startup mascot-free and the animated Seryo avatar in chat. Seryo consent retries preserve the exact decision/version/key. Local file/draft controls do not invent remote analysis or chat history.
- Fixed role-menu startup behavior, current office-detail snapshots, missing-office request actions, large-text reachability and bounded decimal formatting. No invented office prices, rosters, schedules, capacities or policies were installed.

## Source structure

All runnable files are in the workspace. These are the main entry points; adjacent test classes and XML resources are part of the complete project.

```text
android/app/src/main/
  java/ph/edu/tip/goserve/
    data/       AccountGateway, AuthRepository, RealtimeDataSource,
                OfficeManagementRepository, RequestRepository, DeliveryRepository,
                InquiryRepository, ConsultationRepository, PaymentRepository,
                ProfilePhotoRepository, SeryoRepository
    domain/     Immutable account/request/payment/delivery models and wire enums
    ui/         AuthActivity, MainActivity, retained feature ViewModels,
                native fragments/dialogs, SharedMotion, BrandLoadingView
    ui/common/  SeryoMascotView
  res/layout/   Native account, request, office, inquiry, pickup, consultation,
                payment, file and Seryo layouts
  res/values*/  Light/Dark tokens, fonts, strings and animation timing resources
android/app/src/test/       Native/domain/parser/lifecycle/interaction tests
android/app/src/debug/      Clearly labeled visual fixtures; no provider calls
backend/migrations/        Ordered account/workflow/delivery/inquiry/consultation,
                           bounded-read/session/department/photo migrations
backend/functions/         Trusted account, file, payment, Seryo and cleanup sources
backend/tests/             SQL/RLS/transaction and injected Edge/adapter checks
```

| Feature | Native code | Backend contract |
| --- | --- | --- |
| Accounts, photo, password | [AuthRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/AuthRepository.java), [ProfilePhotoRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/ProfilePhotoRepository.java), [PasswordChangeViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/PasswordChangeViewModel.java) | `account_view`, register/profile/Terms operations; private photo registry |
| Offices and services | [OfficeManagementRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/OfficeManagementRepository.java), [OfficeManagementFragment](../android/app/src/main/java/ph/edu/tip/goserve/ui/OfficeManagementFragment.java), [OfficeDetailFragment](../android/app/src/main/java/ph/edu/tip/goserve/ui/OfficeDetailFragment.java) | `update_office`, `save_announcement`, `save_service`; bounded RLS reads |
| Requests and evidence | [RequestRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/RequestRepository.java), [RequestComposeFragment](../android/app/src/main/java/ph/edu/tip/goserve/ui/RequestComposeFragment.java), [RequestActionViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/RequestActionViewModel.java) | Frozen quotes, submit/transition RPCs; `request-file` staged source |
| Cash and payment history | [PaymentRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/PaymentRepository.java), [TellerPaymentViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/TellerPaymentViewModel.java) | Teller-scoped lookup/confirmation and immutable financial audit |
| Pickup and digital delivery | [PickupViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/PickupViewModel.java), [PickupQr](../android/app/src/main/java/ph/edu/tip/goserve/ui/PickupQr.java), [DeliveryRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/DeliveryRepository.java) | Claim/review/handover, private PDF verification, download acknowledgement/receipt |
| Inquiry and notifications | [InquiryViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/InquiryViewModel.java), [ThreadFragment](../android/app/src/main/java/ph/edu/tip/goserve/ui/ThreadFragment.java), [NotificationsViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/NotificationsViewModel.java) | Persisted scoped threads/messages/read boundaries; actual notification targets |
| Consultations | [ConsultationRepository](../android/app/src/main/java/ph/edu/tip/goserve/data/ConsultationRepository.java), [ConsultationViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/ConsultationViewModel.java) | Slot versions/capacity, atomic book/change/cancel and matching department authority |
| Seryo | [SeryoViewModel](../android/app/src/main/java/ph/edu/tip/goserve/ui/SeryoViewModel.java), [AssistantFragment](../android/app/src/main/java/ph/edu/tip/goserve/ui/AssistantFragment.java) | Readiness/consent source; provider operations deliberately unavailable |

Exact signatures, argument/response shapes and error codes: [API-v2.3.md](../backend/API-v2.3.md). Existing-project deployment history and new-project ordering: [DEPLOYMENT-v2.3.md](../backend/DEPLOYMENT-v2.3.md). Do not replay the migration bundle over the managed project.

## Database and retry behavior

Native repositories use explicit columns, bounded pages, authenticated account leases and the assigned office/owner scope. Read/write responses are validated before publication. Successful writes require canonical rereads rather than optimistic success labels. Transport uncertainty retains the original immutable payload, expected version and idempotency key for an explicit retry. Local discard is labeled as clearing a local draft; it does not undo a server transaction.

The approved core/account/photo rollout is already live in `uuvexkewiuxdbuelwogy`: bundled migration `20261021005108/goserve_v23_core_accounts_workflows_photos`, with independent `20261020222356/profile_department` preserved. The prior rollout verified unchanged six-profile/five-office fingerprints, RLS on 18 public tables and a private JPEG avatar bucket. Bootstrap v2, register-student v1 and recovery-request v1 are active. No additional deployment was made during this finalization.

Fresh HTTP verification signs into only the six approved demo accounts, reads each own profile/account/bootstrap result and closes the temporary session. It changes no passwords, profile data, photos, policies or Terms. See [account connectivity evidence](../output/validation/account-rollout/http-readiness.json). This proves Auth and authenticated API connectivity from the workstation; installed-device Google/recovery/business flows require their own evidence.

## Animation details

- [SharedMotion.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/SharedMotion.java): native press state scales both axes to **0.97 in 80 ms** and returns to **1.0 in 120 ms**. Disabled controls reset immediately; ripple/focus behavior remains native. System reduced motion removes scale transforms.
- [BrandLoadingView.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/BrandLoadingView.java): a logo-centered arc uses phase `p=t/1000ms mod 1`, angle `360p−90°` and a 90° sweep. It runs only while attached and shown. Startup's approved logo card follows actual authentication work and contains no mascot or fabricated percentage.
- [SeryoMascotView.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/common/SeryoMascotView.java): idle loop **2800 ms**, `a=2πp`, `b=(1−cos(a))/2`, scale `1+0.018b`, vertical offset `−1.5dp sin(a)`. Thinking loop **1100 ms**, tilt `3° sin(a)` and three dot pulses `(1+sin(a−i·2π/3))/2`. The continuous periodic formulas join at the loop boundary. The original PNG pose is animated as a whole; eyes/limbs are not separately rigged.
- Seryo stops when hidden, detached, inactive or system motion is disabled. Only the selected header/bubble placement animates. Thinking is available to a future approved request controller; unavailable AI transport does not pretend to process a question. Full details and dated component recordings: [Seryo animation](GoServe-Seryo-animation.md).

## Remaining live activation and release evidence

1. **Terms:** [Terms draft](legal/GoServe-Terms-and-Conditions-DRAFT.md) and [Privacy draft](legal/GoServe-Privacy-Notice-DRAFT.md) name GoServe and `GoServeSupport@GoServe.Test`. They remain drafts, with no approved version activated. The reserved test address is not proof of a reachable mailbox. All six demo accounts currently return `accept_terms`; profile completion and app Home access must not bypass this gate.
2. **Operating data:** actual offices must approve service declarations, fees/currencies, document policies, cash scope, pickup expiry/replacement and consultation/cancellation rules. The forms support entered data; blank configuration remains unavailable. No official values were fabricated.
3. **File/Seryo/cleanup rollout:** request-file, download-deliverable, Seryo and cleanup Edge sources are reviewable locally. They were outside the prior live rollout approval. Review exact deployment configuration, Storage denial, byte verification, limits and cleanup before activation. Abandoned unbound file retention needs an approved policy and implementation; current terminal cleanup does not cover that additional class.
4. **Providers:** eGovPay checkout/callback/reconciliation and eGovAI request/knowledge/file/delete protocols still need approved contracts and reviewed transport implementations. The current start/callback/send sources deliberately return unavailable. There is no fake paid state, response generator or simulated provider demonstration.
5. **Release acceptance:** real simultaneous sessions, two-role Realtime/Storage and cleanup tests, reachable recovery mailbox, institutional Google flow and physical-device/accessibility/API coverage remain separate gates. Local passing tests and a debug installation do not close them. See the [AT-01–AT-33 matrix](GoServe-acceptance-traceability-v2.3.md).

## Review scope

Requested skills applied to the code/review workflow: antislop, antislop-code (comment hygiene), karpathy-guidelines and unslop (handoff prose); UI work used antislop-ui, antislop-layoutmobile, frontend-design and impeccable. Existing approved design/implementation plans and Supabase/Figma instructions guided the feature work; TDD and verification-before-completion supplied behavioral proof. No Git/worktree/branch-finalization action was taken because the user's instruction excludes it.

Final source review examined account/office scope, immutable command restoration, canonical response publication, Realtime lifecycle/reconnect paths, hidden-row visibility, stale office actions, money resource bounds, unsent-note preservation, role navigation and provider/Terms gates. Concrete findings were fixed and checked. No independent final review is claimed: the delegated workers exhausted their available quota. No Git operation, push, commit, PR or repository fetch was performed.
