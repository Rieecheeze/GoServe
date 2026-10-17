# GoServe UI panel audit

## Review candidate — October 15, 2026

The current [Figma review index](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=175-7272) links the review candidate. All **14 gap groups, G01–G14, now have 96 contextual state records represented by 192 editable light/dark frames**. Provider-dependent Seryo variants remain Planned; the default Seryo Send control has no prototype reactions.

The final all-visible audit covered **332 current GoServe 2.1 mobile frames and 3,492 ordinary text nodes**. It reported zero geometry issues and zero legacy product fonts. Official Google G marks `56:53` and `56:81` remain explicit brand exceptions; OS text and Material icon glyphs are excluded from product typography checks.

Prototype inspection found **2,280 links to 208 unique destinations**, with no missing or hidden destinations. The six legacy OSA roots are hidden and have no incoming prototype links. Navigation token contrast is **13.90:1 in both themes**.

Fourteen editable compact-width proofs passed the earlier geometry checkpoint at 320-unit logical width with normal and simulated doubled text. After minor copy updates, a focused recheck of the two fee-related proof roots and 34 ordinary text nodes also passed with zero geometry issues and zero legacy fonts. This later result covers those edits without implying that the original checkpoint included them.

This is Figma design and prototype evidence. It does not establish Android rendering, native text scaling, backend authorization, provider operation or acceptance-test success. **User UI finalization is pending; Android implementation remains on hold.** The AT-01–AT-38 mapping below is retained as a handoff map, not a passed-test report.

## Original audit snapshot — historical

The remainder preserves the initial 148-frame audit, including its original gap matrix and acceptance mapping. References to missing or incomplete panels describe that earlier snapshot; use the review-candidate record above for current coverage.

Read-only Figma audit, October 15, 2026. Scope: file `SvT9gBUFjdLgbrJ80LPDno`, page `14:2`, compared with [project plan v2.2](GoServe-project-plan-v2.2.md), Sections 2–7, 9–10 and AT-01–AT-38. No Android code, Gradle, backend, or Figma content was changed for this audit.

The inspected snapshot has **148 screen frames: 74 light and 74 dark**, plus navigation components. Main workflows exist. The remaining work is predominantly distinct states, confirmation panels, and truthful initial-account content. A generic loading/error board does not establish those states on every affected screen.

Latest user direction takes precedence over older plan or frame names: Montserrat headings, Karla body/UI, Home logo without adjacent wordmark, compact icons-only navigation, and genuine empty lists for new accounts. Existing populated examples must remain separate, clearly labeled variants. Root and the foundation agent are updating these areas concurrently; this document records observed gaps, not a claim that their later changes are missing.

## Existing coverage

IDs below are light / dark. Existing does not mean backend-connected or visually approved.

| Area | Existing frames and useful evidence |
| --- | --- |
| Authentication | Sign-in `19:2` / `36:2`; Google sign-up entry `56:38` / `56:66`; assigned-account email `58:2` / `58:22`; student/staff/teller password variants `58:42` / `58:62`, `58:82` / `58:102`, `58:122` / `58:142`; demo-account picker `19:26` / `36:26`. No public password registration is needed. |
| Consent and authentication loading | Privacy/profile `19:61` / `36:61`; authentic loading `106:642` / `106:652`. General consent is separate from optional AI consent. |
| Home and requests, new accounts | Empty/default Home `19:93` / `36:93`; empty Requests `23:32` / `36:377`. Populated examples preserved as Home `127:1124` / `127:1329` and Requests `127:1254` / `127:1459`. |
| Directory and office detail | Directory `33:54` / `36:1766`; Documents `45:2` / `46:2`; Payments `45:54` / `46:30`; Registrar `19:175` / `36:173`; Cashiering `60:2` / `60:246`; Accounting, Guidance, and CCS detail variants also exist. |
| Request creation and payments | New request `19:209` / `36:207`; invalid-evidence selection `19:252` / `36:250`; payment choice `19:281` / `36:279`; pending payment `19:312` / `36:310`; cash instructions `23:2` / `36:347`. |
| Request tracking and verified file | Required/paid example `23:95` / `36:440`; free `106:662` / `107:1063`; fee-pending `106:663` / `107:1064`; verified-file preview `23:144` / `36:489`. Complete/Current/Upcoming labels and separate fee/payment information exist. |
| Student consultation and inquiries | Consult `23:191` / `36:536`; review booking `23:254` / `36:599`; inbox `23:285` / `36:630`; new thread `23:333` / `36:678`; conversation `23:362` / `36:707`; notifications `23:388` / `36:733`. |
| Staff office and request work | Dashboard `24:131` / `36:899`; availability/announcement form `24:186` / `36:954`; queue `24:227` / `36:995`; detail `24:286` / `36:1054`; evidence `24:318` / `36:1086`; PDF upload `24:345` / `36:1113`; status/reason editor `26:2` / `36:1141`. |
| Guidance, faculty, secretary | Guidance schedule/slot `26:112`, `26:166` / `36:1251`, `36:1305`; faculty schedule/slot `26:201`, `26:234` / `36:1340`, `36:1373`; CCS coordination `26:264` / `36:1403`; office inbox/thread `26:293`, `26:339` / `36:1432`, `36:1478`. These are staff capabilities, not extra roles. |
| Pickup | Student QR `23:171` / `36:516`; scanner `26:42` / `36:1181`; denied-camera/manual entry `26:68` / `36:1207`; already-used result `26:92` / `36:1231`. |
| Teller | Search `26:415` / `36:1558`; pending lookup `26:455` / `36:1598`; confirmation `26:500` / `36:1643`; history `26:525` / `36:1668`. |
| Seryo | Pending chat `24:51` / `36:819`; privacy `95:2` / `97:162`; planned attachment `24:82` / `36:850`; planned response `40:2` / `40:33`; provider error `24:111` / `36:879`. No answers or active consent are simulated. |
| Shared recovery and simulator | Generic states board `26:596` / `36:1739` contains loading offices, no requests, and load failure/retry. Simulator `26:563` / `36:1706` lists paid/failed/expired/cancelled outcomes, but is not a student result panel. |

## Finite gap list

**P1**: resolve before UI handoff because the flow, initial state, or authority boundary is unclear. **P2**: required recovery/future integration variants to finish the agreed UI; these are not optional product features. Reuse dialogs, sheets, and state components rather than creating a full page for each message. Provide light/dark variants with working prototype return paths.

| ID | Priority | Necessary missing or incomplete UI | Existing anchor / evidence | Required design boundary |
| --- | --- | --- | --- | --- |
| G01 | P1 | Genuine empty variants for student Inbox, Notifications, and My appointments; staff queue/inbox/schedule; teller pending/history. Show a zero-data dashboard and actual profile fields for a new account. | Inbox `23:285` is populated. Notifications `23:388`, queue `24:227`, office inbox `26:293`, and history `26:525` mix sample records and empty-state copy in one view. Consult has availability, not a separate empty owned-appointments view. | Initial accounts must not see invented personal requests, payments, messages, bookings, counts, or history. Keep existing populated variants for demonstrations. Office-wide data may exist for an assigned staff account; do not empty valid office records solely because the account is new. |
| G02 | P1 | Authentication failure, session-expired, and protected-record access-denied variants. Provide sign-in/retry/back actions without exposing private details. | Auth entries and loading exist. No session-expired or access-denied panel was found. | Distinguish cancelled sign-in, invalid assigned credentials, and denied non-TIP Google identity. Reuse one recovery pattern; never offer public password signup. |
| G03 | P2 | Directory search-empty with clear query; actual directory loading/error/retry variants; no configured services state within office detail. | Directory `33:54` and generic board `26:596`. | Empty search is not a connection failure. Retry preserves query/filter where useful. Do not fabricate services to fill a sparse office. |
| G04 | P1 | Request review/submission variants for free, required fee, and pending office declaration; submitting/failure/retry and successful reference receipt. Include configured request-type selection state. | New request `19:209` always ends with “Continue to payment choice,” despite its free-service note. Fee distinctions currently exist only in tracked examples. | Free requests must have a submission path that skips checkout. Fee-pending requests cannot imply paid or processing. Student sees a read-only office declaration and cannot choose the fee authority or amount. |
| G05 | P1 | Authorized office fee-declaration/configuration panel or sheet, including saving/error state. | Staff dashboard, availability, request detail, and status editor have no fee-declaration control. | Show Free / Fee required / Pending review for the configured service, with a clearly labeled demo fee when authorized. Student UI remains read-only. If declaration is intentionally provisioned outside the app, explicitly document that decision and keep this as a server-configured read-only staff summary instead of inventing student authority. |
| G06 | P1 | Student cancellation with required reason; staff reject/cancel reason validation and explicit confirmation; paid-request manual-follow-up result. | Status editor `26:2` has reason input but Save routes directly to queue. Student detail has no cancellation state. | Show allowed actions only; do not invent cancellation deadlines. A paid record stays paid, and no automatic refund/void is promised. Empty reason must show a field error. |
| G07 | P2 | Student payment results for authoritative paid, failed, expired, and cancelled; refreshing and refresh-failed states. | Pending `19:312` and simulator `26:563` exist. A simulator choice is not the resulting payment UI. | Keep request progress separate. Only verified server results can say paid. Retry appears only where allowed; app return alone stays pending. Current provider-disabled variants must remain honest. |
| G08 | P1 | Teller reference-not-found, already-paid/duplicate-denied, confirmation-in-progress/failure, and confirmed receipt states. Empty pending/history variants are G01. | Search/pending/confirm/history `26:415`–`26:525` exist. “Already paid?” text is guidance, not an actual rejected operation. | Receipt exposes reference, confirmed state, actor/time from server data. Disable repeat confirmation while pending; no student/staff payment-confirm action. |
| G09 | P1 | Distinct booked appointment detail, cancellation confirmation, reschedule selection/review, full-slot conflict, and no available times states. | `23:254` combines “Review booking,” Confirm booking, Cancel appointment, and Reschedule before an existing booking is distinguished. | Keep a new booking separate from managing one. On failed reschedule, retain the original booking. Show capacity conflicts without inventing booking deadlines or office policies. |
| G10 | P1 | Valid selected-evidence review plus upload progress/failure/retry; private verified-file access blocked/unavailable states; expired/removed deliverable explanation. | Invalid evidence `19:252`, staff PDF type error `24:345`, and preview-only file `23:144` exist. | A validation note is not a download-denial state. Unpaid/unauthorized access must not expose file details. No successful upload, issued document, deletion, or private download is claimed before confirmation. |
| G11 | P1 | Student pickup panel with a readable manual code as well as QR; valid pickup completion result; unknown/expired/unauthorized result variants. | `23:171` has a “QR target” placeholder and no readable code. Used-token error `26:92` and denied-camera fallback already exist. | Use visibly non-functional demo placeholders until server tokens exist. Scanner/manual entry share validation. Do not duplicate Android’s own permission dialog as an application screen. |
| G12 | P2 | Planned enabled-AI consent accept/decline state; selected-file/remove and invalid type/count/size states; analyzing/busy, unsupported-question refusal, and rate-limit variants. | Pending chat/privacy/upload, response scaffold, and provider error already exist at the IDs above. | Keep all enabled-future variants marked Planned. Do not invent Seryo tasks, procedures, answers, limits, source content, or cleanup success. No persistent chat/file history. Existing pending controls stay inactive. Root owns ongoing Seryo composition. |
| G13 | P2 | Office update saving/failure/retry and published announcement/status result with actor/time. | Availability/announcement editor `24:186` and student announcement placeholder `19:175`. | One inline state/confirmation pattern is sufficient. Do not claim a successful live publication before server confirmation. |
| G14 | P2 | Inquiry sending/failed/retry states and authorized recipient/assignment selection where assignment is offered. | Student new thread/reply `23:333`, `23:362`; staff reply `26:339`; CCS coordination explains assignment but shows no selection state. | Preserve an unsent inquiry on recoverable send failure. Restrict recipients to authorized destinations. No typing or presence UI. |

### Content corrections, not additional screens

- Cashiering `60:2` and teller search `26:415` still name OSA; separate OSA office/request/payment frames exist at `60:36`, `60:172`, `60:215` and dark mirrors. OSA is not in the five-office plan. Confirm its authorization or remove those active routes/references from this version; do not silently expand the plan.
- Registrar detail says some requests have a fee. Replace policy-like copy with a conditional, office-declared fee explanation. Do not imply all Registrar requests are payable.
- Preserve Cashiering-only Payments filtering even when legacy Accounting category text says Payments.
- Keep first-letter-uppercase human labels, while preserving exact enum wire values where displayed as system states. Do not show raw Material icon names in the final review.
- The current user-reported pending-card clipping, oversized filters, and staff queue width are owned by root’s targeted fixes. Recheck their post-fix composition; they are not new panels in this gap list.

## Acceptance coverage: all 38 criteria

“Exists” below means a design surface exists, not that the acceptance test passes. Server operations and actual Android behavior cannot be proven from Figma.

| AT | UI evidence / necessary gap | Non-UI proof still required later |
| --- | --- | --- |
| AT-01 | Google entry, consent, loading exist; G02 auth failure. | Trusted Google verification and student provisioning. |
| AT-02 | Assigned-account/email/password and role variants exist; G02 failure. | Assigned profile/session verification. |
| AT-03 | No public password-registration flow is needed; keep Google-only sign-up distinct. | Server blocks unprovisioned password accounts. |
| AT-04 | G02 denied non-TIP identity state. | Provider/identity verification and protected-data denial. |
| AT-05 | Student/staff/teller and capability screens exist; G01/G02 initial/access states. | Role and office/capability authorization. |
| AT-06 | G10 private-file denial state. | Cross-student Database/Storage RLS. |
| AT-07 | G02 protected-record denial state. | Cross-office RLS. |
| AT-08 | Office editor exists; G13 save/error/published states. | Actor/time persistence and authorized Realtime. |
| AT-09 | Thread creation/replies exist; G01/G14 empty/send/assignment states. | Conversation ownership and persisted sender/time. |
| AT-10 | Typing and presence remain excluded; no missing panel. | Ensure no persistence or transport for them. |
| AT-11 | Evidence selection exists; G04/G10 review/upload states. | Private upload validation and ownership. |
| AT-12 | Invalid evidence state already exists; make invalid/valid variants distinct in G10. | Configured MIME/size enforcement. |
| AT-13 | Staff PDF upload and student preview exist; G10 real issued-file states. | Private upload and signed URL authorization. |
| AT-14 | G10 unpaid access-blocked state. | Server download/payment gate. |
| AT-15 | G10 no-longer-available file state; no cleanup administration screen needed. | Thirty-day cleanup and audit. |
| AT-16 | Payment choice/handoff/pending exist; G04/G07 branch/results. | Server fee lookup and verified provider contract. |
| AT-17 | G05 fee authority and read-only student amount. | Reject/ignore client amount substitution. |
| AT-18 | No separate callback-admin panel needed. | Callback authentication and idempotency. |
| AT-19 | Pending return explanation exists; G07 refresh/error states. | Authoritative callback/reconciliation. |
| AT-20 | G07 failed/expired/cancelled results. | Approved provider status mapping. |
| AT-21 | Labeled simulator exists; G07 resulting UI variants. | Isolated simulator events and trusted outcomes. |
| AT-22 | Teller review/confirm exists; G08 receipt and busy/failure states. | Teller-only confirmation and actor/time. |
| AT-23 | Do not add student/staff cash confirmation; shared denial G02 can represent rejection. | Server authorization. |
| AT-24 | G08 actual duplicate-denied result. | Atomic duplicate rejection. |
| AT-25 | Staff reason field exists; G06 required-reason error/confirmation. | Transition validation. |
| AT-26 | G06 paid manual-follow-up warning/result. | Preserve paid record and audit reason. |
| AT-27 | Staff full-slot label exists; G09 student full-slot conflict. | Atomic capacity check. |
| AT-28 | Booking controls exist; G09 distinct booked/cancel/reschedule states. | Capacity-safe mutation and in-app updates. |
| AT-29 | Scanner/manual/QR surfaces exist; G11 code and success result. | Single-use token consumption/completion. |
| AT-30 | Already-used token result exists. | Server replay denial. |
| AT-31 | Planned response scaffold exists; G12 enabled-future consent/busy states. | Approved knowledge and supplied Seryo procedures. |
| AT-32 | G12 unsupported-question refusal with relevant-office route. | Grounding/refusal behavior without invented policy. |
| AT-33 | Planned upload/privacy exist; G12 explicit consent/selection/busy states. | Provider integration, no history, immediate deletion. |
| AT-34 | G12 consent-denied/type/count/size validation. | Pre-provider validation. |
| AT-35 | G12 rate-limit state without inventing a limit or retry duration. | Server request limits. |
| AT-36 | Provider error scaffold exists; G12 cleanup-pending/confirmed distinction. | Safe failure and verified deletion. |
| AT-37 | No additional Figma panel proves this criterion. | Build/install and real Android authorized sign-in. |
| AT-38 | Camera-denied/manual entry already exists; preserve it. | Native permission denial and shared server validation. |

## Final UI review gate

1. Complete the finite G01–G14 state groups; reuse components and mark unresolved provider flows Planned.
2. In both themes, start the new-account prototype with no fabricated personal data; keep sample records reachable only through clearly labeled examples.
3. Check selected filter state, inactive/busy actions, reason validation, error recovery, and Back/Cancel destinations. A confirmation must show the correct affected record and return to the originating context.
4. Inspect at 390dp and 320dp with large text: wrapping cards, scrollable content, final action reachable above navigation, no overlap/clipping, and visible completion check/accent. Figma structure alone does not prove native text scaling.
5. Verify student, office staff, guidance, faculty, secretary, and teller routes independently. Do not add platform-admin screens or expose another role’s actions.
6. Obtain user approval of the Figma UI before resuming Android implementation. Backend, provider, device, security, and acceptance-test execution remain separate work.

This is a bounded coverage audit from root inventory and visible text/prototype reads. It is not a visual pixel audit, a backend security result, or a claim that AT-01–AT-38 have passed.
