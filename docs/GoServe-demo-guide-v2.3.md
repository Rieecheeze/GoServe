# GoServe role demonstration guide

Preparation guide for the verified 22 October debug build. Steps requiring inactive Terms, unconfigured office policies, undeployed functions or unavailable providers cannot yet be demonstrated live. Do not use the debug fixture as a substitute for a successful real transaction.

## Accounts

| Role | Login | Trusted scope |
| --- | --- | --- |
| Registrar | registrar@demo.goserve.test | OFF-REG |
| Accounting | accounting@demo.goserve.test | OFF-ACC |
| Tellering | teller@demo.goserve.test | OFF-CASH, Teller role |
| Guidance | guidance@demo.goserve.test | OFF-GCC, Guidance capability |
| CCS Secretary | secretary.ccs@demo.goserve.test | DEP-CCS, Secretary capability |
| CCS Student | student.ccs@demo.goserve.test | Student, trusted DEP-CCS affiliation |

Use the existing private credentials handoff in Documents. No passwords are embedded in app source or this guide. No Faculty account/contact is part of this demonstration. Only the matching Secretary connects the Student to the department.

## What can be checked now

1. Open the `android` project, build with the preserved local configuration and install [app-debug.apk](../android/app/build/outputs/apk/debug/app-debug.apk).
2. Confirm the logo-only loading treatment, welcome/sign-in screen, Light/Dark settings where reachable, and accessible errors for blank input. The [API26 evidence](../output/validation/final-2026-10-22/verification.json) covers installation/startup and debug presentation only.
3. Sign in with an approved demo account. Auth/profile/account/bootstrap connectivity was freshly verified for all six. **Expected current result: Terms gate**, because there is no active approved Terms document. This is the correct result, not a completed Home demonstration.
4. Google is enabled; the button requests institutional Google login. A real institution-approved `@tip.edu.ph` flow still needs device/account evidence. Merely opening Google does not prove sign-in success. Recovery needs a real reachable mailbox; `.test` addresses are not inboxes.
5. Review the [animated avatar recording](../output/validation/final-2026-10-22/installed-seryo-idle.mp4) and [reduced-motion recording](../output/validation/final-2026-10-22/installed-seryo-reduced-motion.mp4). Example conversation text is debug-only and is never an AI-provider response.

## Live role sequence after the stated gates are resolved

Use only explicitly approved, labeled demo records. Capture actual server references, versions, expected/actual outcomes and sanitized screenshots; exclude passwords, tokens, private files and pickup codes.

1. **Registrar/Accounting:** open the workspace's office information editor, enter an actual status/note, review and save. Publish an approved demo announcement with its visibility checkbox. Use Services and fees to enter the approved service, delivery options, required documents and fee declaration. Confirm from a separate Student session. Verify pending blocks submission; free and required fees show their correct distinct states. Hidden announcements can require the next 30-second foreground reconciliation because RLS may omit the hidden-row event.
2. **Student request:** choose the actual office/service/delivery, review the canonical quote and approved evidence requirements, add approved test evidence only if its function/policy is deployed, enter notes, review and explicitly submit. Record the real reference. A stale catalogue must require a fresh review; retry must not create a second request.
3. **Tellering:** for an actual configured cash request, look up its real reference, review the server amount/version and explicitly confirm payment. Check history and the separate Student/office session. Repeated confirmation must preserve one financial result. Do not declare a required payment paid from a browser return.
4. **Physical office delivery:** process the request and mark it Ready. Confirm that no code exists until the Student explicitly claims it. Scan or manually enter the actual server code, review the request, then explicitly hand over. Scanning alone must leave the request incomplete. The code and completion must be consumed once.
5. **Digital office delivery:** use a configured service and deployed file/download functions. Upload an approved test PDF, review/publish the verified deliverable, then have the owner save the authenticated download successfully. Explicit Student receipt is a separate review/confirmation. Failed download or simply opening the file cannot complete the request.
6. **Guidance/CCS Secretary:** configure only approved actual slot times/capacity and rules. Student contacts Guidance or the matching CCS Secretary, books an available slot and reviews any cancellation/reschedule. A failed move must retain the original booking. No Faculty roster/subject endpoint or fabricated appointment is implied.
7. **Inquiry and notifications:** Student sends an explicit message to the chosen authorized office/Secretary. The office replies in the same persisted thread. Read through the displayed text before acknowledging; scrolling past unseen text must not mark it read. Open a real notification target, then explicitly mark read if desired.
8. **Settings:** use an authorized, completed account to change/remove its own photo and, for a GoServe password account, change its password through the actual reauthentication flow. Recovery and password changes require separately authorized real account actions; fresh local checks did not change the demo credentials.
9. **Seryo/payment providers:** keep unavailable until reviewed protocols, knowledge/procedures, file deletion and payment authority are implemented. Consent and an unsent local draft are not proof of a provider response. No live online-payment/AI demonstration is currently available.

After each role change, verify the old protected content/subscriptions disappear. Record network interruption/reconnect behavior and independent competing-session cases before marking an acceptance row complete. Final release gates and source/deployment references are in the [delivery report](GoServe-delivery-v2.3-2026-10-22.md) and [current acceptance inventory](GoServe-acceptance-current-2026-10-22.md).
