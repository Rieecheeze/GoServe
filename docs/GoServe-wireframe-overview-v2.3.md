# GoServe wireframe overview v2.3

Updated 17 October 2026. **Ready for UI review; approval is pending.**

[Open the professor-facing Figma overview](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=315-364) · [Separate wireframe page](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=305-2)

The overview uses16 main panels in four rows. It explains the program in general, as requested, with short notes for omitted states. The existing detailed Light/Dark design remains on page14:2. The new page is **GoServe • Wireframes v2.3**,305:2.

## Panel inventory

| Row | Panels | Purpose |
| --- | --- | --- |
|1. Account setup |01 Sign in; 02 Terms; 03 Credentials; 04 Student profile | Email and Google paths, first-time agreement, password confirmation, complete profile and returning-session behavior |
|2. Student request |05 Home; 06 Review/submit; 07 Ready/Claim; 08 Pickup QR | Per-type fee rules, one persisted request, office processing, Ready before QR and student-triggered code generation |
|3. Student services |09 Digital receipt; 10 Consultation; 11 Inquiry; 12 Seryo | Download before receipt confirmation, capacity-controlled booking, shared office conversation and separate consented AI assistance |
|4. Office and account |13 Office workspace; 14 Handover; 15 Cashiering; 16 Settings | Assigned work, explicit physical handover, teller-only payment confirmation and read-only Terms access |

## Main paths explained

- Email: Terms → credentials and matching confirmation → full profile → account-created confirmation → explicit Sign in.
- First-time Google: Terms → TIP Google → same profile → confirmed save → Home. Complete restored accounts open Home directly.
- Request: selected type and declared fee → review/evidence → committed submission → office queue. Paid work waits for authoritative payment; free work skips payment.
- Physical result: office Ready → student Claim request → server generates or retrieves one active code → authorized scan/manual entry → office review and actual-handover confirmation → Completed on both sides.
- Digital result: authorized PDF release → Ready for Download → successful download → student confirms receipt → Completed on both sides.
- Inquiry: student message commits → authorized office inbox updates → office reply commits → student thread updates. Consultation changes follow the same committed-data synchronization rule.
- Seryo: separate Allow/Decline consent, inline file, approved guidance and navigation-only contextual actions. Provider, procedure and cleanup gates remain.

## Evidence boundary

The page is an editable, static wireframe overview. It does not submit data, authenticate, confirm payments or process AI files. Phone prototype reactions were cleared so the overview does not jump into the existing UI page. Long phone content has bounded vertical scrolling. The header includes Light/Dark navigation examples without duplicating every main panel.

The latest check found16 phones, all 390×844, each with a scroll viewport, and no overview structural issues. Composition was visually reviewed on 16 October. The17October wording correction keeps the same note height: sign-out clears account-scoped **local caches** and subscriptions; persisted records remain on the server.

Original UI evidence retains470 recorded frame dimensions and 2,990 navigation links to 264 valid destinations. Of 137 pre-copy source fingerprints,136 match exactly in original-page context. The remaining Seryo source is visually equivalent across 64 nodes to its preserved wireframe copy; strict byte identity for that source is not claimed. Full verification details are in[the JSON report](GoServe-wireframe-verification-2026-10-17.json).

## Related deliverables and next gate

- [Detailed editable project plan](GoServe-project-plan-v2.3.md)
- [Matching13-page PDF](../output/pdf/GoServe_Project_Plan_v2.3.pdf)
- [Implementation milestone status](GoServe-implementation-progress-v2.3.md)
- [Android baseline report](GoServe-native-baseline-2026-10-16.md)

M1 requires explicit UI approval before native feature coding. Fresh Android baseline execution is blocked by Gradle's loopback-connection failure. The 41 tests / 39 passed / 2 failures result is historical from 15 October, not a new passing run. No Android/backend feature changes or provider calls were made during this wireframe revision.

