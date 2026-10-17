# GoServe remaining UI state specifications

October 15, 2026. Design specification only. Figma is the current deliverable; Android implementation remains on hold until the user finalizes the UI.

This document resolves the finite G01–G14 list in `GoServe-ui-panel-audit.md`. It does not claim that these states are already drawn, approved, implemented, connected, or tested. Existing populated examples remain separate from genuine initial-account states.

## Scope and count

There are **14 gap groups, 31 reusable composition families, and 76 named state records** below. A state record is a contextual/inline/component variant, not automatically a new full screen. For example, failed/expired/cancelled payment results share one composition. A complete two-theme reference of every composition would have **62 base composition frames**; contextual instances and working prototype variants determine the final Figma frame count. Reuse existing screens and overlays rather than generating 152 standalone outcome pages.

| Gap | Composition families | Named states | Proposed family |
| --- | ---: | ---: | --- |
| G01 | 2 | 9 | Empty collection; zero-data office dashboard |
| G02 | 1 | 5 | Contextual access/authentication recovery |
| G03 | 2 | 4 | Directory state; empty office services |
| G04 | 3 | 7 | Service selection; request review; submission state/receipt |
| G05 | 1 | 3 | Office fee editor with save states |
| G06 | 3 | 5 | Reason sheet; confirmation; stopped-request result |
| G07 | 1 | 6 | Payment result with refresh states |
| G08 | 3 | 5 | Teller lookup result; confirmation state; receipt |
| G09 | 4 | 6 | Booked detail; reschedule selection; review; availability feedback |
| G10 | 3 | 6 | Evidence review; upload state; private-file feedback |
| G11 | 1 | 5 | Pickup result; revise existing student pickup composition |
| G12 | 4 | 9 | Consent; attachment review; chat event; cleanup status |
| G13 | 1 | 3 | Existing office editor with inline publication state |
| G14 | 2 | 3 | Authorized assignment sheet; inquiry send state |
| Total | 31 | 76 | |

These are a build inventory, not a demand to multiply screens. Reuse the G02 recovery composition in G03/G09/G10, the G06 confirmation in G09, and the G07 result structure in G08/G11 where the record and authority labels stay correct.

## Shared format

- Native editable Figma content; current page `14:2`, file `SvT9gBUFjdLgbrJ80LPDno`. Preserve archive page `0:1`.
- Montserrat ExtraBold 800 headings; Karla Regular 400 body and SemiBold 600 controls. Reuse existing nine GoServe text styles. Typical roles: title 30/38, section 20/28, card 16/24, body 16/24, support/controls 14/20, metadata 12/16. Sentence case; no automatic lowercase status labels.
- Main content margins 20; cards typically 16 internal padding; related gap 8–16, section gap 24. Cards and sheets hug content vertically. Important copy wraps without ellipsis.
- Search 48 high. Filters 32 visual height inside independent 48-high targets. Primary/secondary single-line actions 48 minimum, growing on wrapping. Text actions keep 48-high targets. No decorative status dots where a check, label, or explicit state does the job.
- Six-item student navigation remains icons only, 56 high, 22 icons, 36 selected circle, whole targets at least 48. Light surfaces use a dark capsule; dark surfaces use a light capsule. Preserve safe area and scroll clearance.
- A page owns vertical scrolling. Bottom actions remain reachable above navigation/IME, including at 320dp and large text. Modal content scrolls when needed; buttons do not cover its final field.
- New-account screens have no invented personal records, counts, messages, receipts, filenames, booking times, or history. Staff may see valid office-wide data even when their own account is new.
- Use plain status text plus icon/shape; do not communicate state with color alone. Completed request phases use green checks and the word Complete; the current phase is distinct from future phases.
- Every variant has a Back/Cancel/Close route to its actual origin. Do not replace a contextual error with a generic state board.

## G01 — Truthful empty collections and dashboard

Composition E is one icon/heading/support/action block inside the existing page shell. Composition D is the existing office dashboard with zero actual counters and no activity rows. Keep populated examples on separately labeled frames.

| State | Title / body | Action and route |
| --- | --- | --- |
| G01.1 Student Inbox | No messages yet / Ask an office when you need help with a service or request. | Ask an office → new inquiry `23:333` / `36:678`; office recipient is required. |
| G01.2 Notifications | You're up to date / Updates about your requests, payments, and appointments will appear here. | Browse offices → all offices `33:54` / `36:1766`. |
| G01.3 My appointments | No appointments yet / Find an available consultation to book your first appointment. | Find a consultation → Consult `23:191` / `36:536`. |
| G01.4 Office dashboard | No activity yet / New requests and appointment updates for your office will appear here. | View queue → empty office queue; Edit availability → existing editor. Real zero counts only; hide unsupported metrics. |
| G01.5 Office queue | No requests in this view / Requests assigned to your office will appear here. | Clear filters only when filters are applied; otherwise Back to dashboard. |
| G01.6 Office Inbox | No inquiries yet / Inquiries sent to your office will appear here. | Back to dashboard. No invented outgoing conversation. |
| G01.7 Office schedule | No appointments for this date / Select another date or manage your available times. | Change date; Manage availability → authorized guidance/faculty slot editor. |
| G01.8 Teller pending | No pending payments / Payments awaiting Cashiering confirmation will appear here. | Search reference → teller search `26:415` / `36:1558`. |
| G01.9 Teller history | No confirmed payments yet / Payments you are authorized to view will appear here after confirmation. | Search reference → teller search. |

Profile is an existing-screen correction, not a tenth empty state: render actual signed-in name/email and configured role/office only; optional unknown fields say Not provided. Do not replace actual office membership with a fabricated demo identity.

## G02 — Contextual recovery

Reuse one recovery composition, with a short title, specific body, primary action and safe secondary/back action. Keep protected-record identifiers/content absent when access is denied.

| State | Title / body | Actions |
| --- | --- | --- |
| G02.1 Cancelled sign-in | Sign-in cancelled / You can try again when you're ready. | Try again → originating sign-in method; Back to sign in. No error on a deliberate cancel unless feedback is needed. |
| G02.2 Invalid assigned credentials | Check your sign-in details / The email or password wasn't accepted. | Keep email; allow password correction. Sign in; Back. Do not say whether an account exists. |
| G02.3 Ineligible Google identity | Use your TIP account / This Google account isn't eligible for student access. | Choose another account; Back to sign in. Do not invent approved domain strings. |
| G02.4 Session expired | Sign in again / Your session has ended. Sign in to continue. | Sign in → authentication; after success return only to an authorized destination. |
| G02.5 Protected access denied | Access unavailable / You don't have permission to open this record. | Back to own records. No file name, owner name, amount, or hidden record details. |

Never add public email/password registration. Preserve Google student onboarding versus assigned staff/teller accounts.

## G03 — Directory and office content states

| State | Title / body | Actions and preserved state |
| --- | --- | --- |
| G03.1 Loading | Loading offices… / Keep the search/filter shell visible; use restrained progress. | Back works. No fake office rows. |
| G03.2 Load failure | Couldn't load offices / Check your connection and try again. | Retry → same query/filter; Back. Do not state a cause the system did not establish. |
| G03.3 Search empty | No matching offices or services / Try a different name or clear your search. | Clear search → same selected category. Actual query stays editable. |
| G03.4 No configured office services | No services listed yet / This office hasn't published request services here. | Ask this office → inquiry; Back to directory. No New request button until a configured type is available. |

Directory all-services still exposes the five confirmed offices. Documents and Payments retain their correct active filters; Payments lists Cashiering alone.

## G04 — Request selection, fee-aware review and submission

Use actual configured service types. The office selects the fee policy; the student sees it read-only. No amount or payment method selector appears for a free service. Payment is routed to Cashiering only after a required fee is declared and a request exists.

| State | Title / body and fields | Action / route |
| --- | --- | --- |
| G04.1 Request type selection | Choose a service / Select a published service from this office. Each row uses its real name and fee declaration. | Select type → existing request form `19:209` / `36:207`. Back to office. No services → G03.4. |
| G04.2 Free review | Review request / Summary of office, service, supplied details and selected evidence. Fee: No payment required. | Submit request → submitting; Edit → form. Skip payment choice. |
| G04.3 Required-fee review | Review request / Same summary. Fee: Payment required, followed by server-declared amount when available; Payments handled by Cashiering. | Submit request → submitting, then reference receipt and payment choice. No student amount field. |
| G04.4 Pending-declaration review | Fee review pending / This office hasn't confirmed whether payment is required. No payment is due yet. | Ask office; Back to request form. Submission is disabled until the pending-submission policy is confirmed. Do not add unsupported Save draft. |
| G04.5 Submitting | Submitting request… / Keep the reviewed summary visible. | Submit disabled; no duplicate tap. Do not display a reference before confirmation. |
| G04.6 Submit failure | Request not submitted / Your details are still here. Try again. | Retry same validated submission; Edit details. If result is uncertain, say Checking submission… and reconcile before another create action. |
| G04.7 Submission receipt | Request submitted / Show confirmed reference, office, service, Submitted state and server time. | Free: View request; required: Choose payment method then Cashiering routes. Back to Requests updates populated state. |

Existing pending-declaration tracked requests remain Submitted/office-review pending; they cannot imply paid or processing. The new-request pending-declaration CTA is deliberately conservative pending the one unresolved policy below.

## G05 — Authorized office fee declaration

One Service fee editor reached from the service configuration summary. This is an office-staff action only. Student and teller views never expose it.

| State | Content | Actions |
| --- | --- | --- |
| G05.1 Editing | Service name; mutually exclusive No payment required / Payment required / Pending office review. Required-fee amount field is visible only to authorized staff and stays blank until entered/loaded. Add office attribution. | Review change → confirmation; Cancel → prior configured value. No invented official fee. Demo values, if supplied, are labeled Demo. |
| G05.2 Saving | Saving fee declaration…; current reviewed value stays visible. | Disable repeat save and mode changes until result. |
| G05.3 Save failed | Fee declaration wasn't saved / Your changes are still here. | Retry; Cancel returns to unchanged server value. Field validation is adjacent to the actual field. |

Success returns to a read-only summary with confirmed declaration and server actor/time, using G13's confirmation pattern. The rule for changing fees after submission is unresolved; do not silently modify existing requests or add a student refund promise.

## G06 — Cancellation/rejection reasons and paid follow-up

Reuse reason and confirmation overlays. Staff may select only allowed server-authorized transitions; student sees Cancel request only when allowed. No invented deadline or eligibility rule.

| State | Title / content | Actions |
| --- | --- | --- |
| G06.1 Reason entry | Cancel request / Required reason; show affected reference/service. Staff rejection changes title to Reject request and keeps the same required-reason pattern. | Review cancellation or Review rejection; Keep request → origin. |
| G06.2 Missing reason | Reason is required shown beneath the field; field is focused. | Same actions; no transition occurs. |
| G06.3 Confirm stop | Cancel this request? or Reject this request? / Review reference, outcome and entered reason. If paid, state Payment remains recorded. Contact Cashiering for payment follow-up. | Confirm cancellation/rejection; Back to reason. |
| G06.4 Confirmed unpaid/free result | Request cancelled or Request rejected / Confirmed state, reason, authorized actor/time. | View request → terminal detail; Back to appropriate queue/list. |
| G06.5 Confirmed paid result | Request cancelled (or rejected) / Payment remains recorded. Contact Cashiering about the payment. | Contact Cashiering → authorized inquiry; View request. No automatic refund/void claim. |

Saving/failure uses the shared operation feedback pattern while preserving reason. Never show success until the transition is confirmed.

## G07 — Student payment outcomes

Use one Payment status composition: reference/service, payment state, applicable amount from server, state explanation, action. Request progress remains a separate section. Existing pending panel `19:312` / `36:310` remains the return destination when no verified result exists.

| State | Title / body | Actions |
| --- | --- | --- |
| G07.1 Authoritative paid | Payment confirmed / Cashiering has a confirmed payment record for this request. | View request; View receipt only if an issued receipt exists. Show confirmed source/actor/time, not fabricated provider details. |
| G07.2 Failed | Payment failed / This payment wasn't confirmed. | Try again only if the server permits a new attempt; otherwise Contact Cashiering. |
| G07.3 Expired | Payment session expired / No payment was confirmed for this session. | Start a new payment session only when allowed; Back to request. |
| G07.4 Cancelled | Payment cancelled / No payment was confirmed for this attempt. Your request is separate from this payment attempt. | Choose payment method when allowed; View request. |
| G07.5 Refreshing | Checking payment status… / Returning from the payment page does not confirm payment. | Disable repeat refresh. Back to request remains available. |
| G07.6 Refresh failure | Couldn't check payment status / The last confirmed status hasn't changed. | Check again; View request. Preserve Pending unless server verified otherwise. |

All provider-dependent prototype outcomes are clearly labeled examples/planned, separate from the default disabled integration. Hosted-page test controls do not establish callback/amount/status contracts.

## G08 — Teller lookup and cash confirmation

| State | Title / body | Actions |
| --- | --- | --- |
| G08.1 Reference not found | No payment found / Check the reference and try again. | Edit reference → lookup field; no student details leaked. |
| G08.2 Already paid / duplicate denied | Payment already confirmed / A second confirmation wasn't recorded. | View existing receipt when authorized; Search another reference. Confirm action absent. |
| G08.3 Confirming | Confirming payment… / Keep amount/reference review visible. | Confirmation disabled; no second operation. |
| G08.4 Confirmation failed | Payment wasn't confirmed / Check the latest status before trying again. | Check status; return to review only if still eligible. Preserve entered review data. |
| G08.5 Confirmed receipt | Payment confirmed / Confirmed reference, amount, Cashiering actor/time and receipt identifier only when supplied by server. | Done → teller history; Search another reference. |

Teller alone confirms cash. Do not add this control to student or office-staff pages. Successful confirmation does not itself complete the service request.

## G09 — Booking versus managing an appointment

Remove Cancel appointment and Reschedule from the pre-booking review. They belong on a confirmed existing booking. Use the G06 confirmation overlay for cancellation.

| State | Title / content | Actions |
| --- | --- | --- |
| G09.1 Booked detail | Appointment booked / Confirmed reference, office/person, date/time, location or valid meeting instruction, current status. | Cancel appointment or Reschedule only when allowed; Ask office. |
| G09.2 Cancellation confirmation | Cancel this appointment? / Review the existing booking. No invented cancellation deadline. | Keep appointment; Confirm cancellation → confirmed cancelled detail. |
| G09.3 Reschedule selection | Choose a new time / Existing booking remains visible; show real available times. | Select time → reschedule review; Keep current appointment. |
| G09.4 Reschedule review | Review new time / Current booking and proposed replacement clearly distinguished. | Confirm change; Back to available times. Replace only after server confirmation. |
| G09.5 Slot conflict | This time is no longer available / Your current appointment hasn't changed. | Choose another time; View current appointment. For first booking, omit the current-appointment claim and return to selection. |
| G09.6 No times | No available times / Try another date or ask the office about availability. | Change date; Ask office; existing booking retained. |

Successful booking/reschedule reuses booked detail; operation loading/failure is inline. Existing schedule/slot forms remain role-specific and do not imply public booking capacity that has not been configured.

## G10 — Evidence and private deliverables

| State | Title / content | Actions |
| --- | --- | --- |
| G10.1 Valid evidence selected | Review attachment / Real selected filename, type and size; no invented uploaded marker. | Replace or Remove; Continue to request review. |
| G10.2 Uploading | Uploading attachment… / Keep selected file visible. Use indeterminate progress unless real byte progress exists. | Block duplicate upload; Cancel only if supported. |
| G10.3 Upload failed | Attachment wasn't uploaded / Your request details are still here. | Retry upload; Replace file; Back. No successful submission claim. |
| G10.4 Private file blocked | File access unavailable / You don't have permission to open this file. For the verified owner with an unpaid applicable fee: Payment is required before this file can be opened. | Unauthorized: Back to own records. Verified owner/payment gate: Go to Cashiering. Do not show filename/content to unauthorized viewers. |
| G10.5 Deliverable not ready | No verified file yet / The office hasn't released a verified document for this request. | View request; Ask office. Download absent. |
| G10.6 Expired/removed deliverable | File no longer available / This document is no longer available to download here. | Ask office; View request. Explain a specific expiry/removal reason only if supplied. Do not invent a fresh document. |

Selected evidence, uploaded evidence, issued verified document and permission to download are four distinct facts. Reuse existing invalid-evidence and staff PDF validation variants, using configured limits rather than invented limits. Do not claim deletion or download success before confirmation.

## G11 — Pickup code and outcomes

Update existing student pickup `23:171` / `36:516`: show a readable manual code alongside QR when a real token exists. In the prototype, use `DEMO — NOT VALID FOR PICKUP` and a visibly nonfunctional example code; no scannable real token is invented. Staff scan/manual entry share one result composition. The existing already-used error stays distinct.

| State | Title / content | Actions |
| --- | --- | --- |
| G11.1 Student pickup token | Ready for pickup / Readable code, QR, request/service and office pickup instructions when supplied. Demo labeling as above until server token exists. | Back to request. Copy code only for a real copyable token; demo copy can be explicitly labeled. |
| G11.2 Valid completion | Pickup confirmed / Show server-confirmed request completion, actor/time and green completion check. | Back to queue; Scan another code. |
| G11.3 Unknown code | Code not found / Check the code and try again. | Enter code; Scan again. No hidden record details. |
| G11.4 Expired code | Pickup code expired / Ask the issuing office to check this request. | Back to request/queue; Enter another code. Do not promise self-service regeneration. |
| G11.5 Unauthorized code | Pickup unavailable / Your account isn't authorized to confirm this pickup. | Back to own queue. No student/file details. |

Preserve denied-camera/manual entry and already-used variants. Never draw a fake native Android camera permission dialog.

## G12 — Seryo future states, all explicitly Planned

Seryo remains inactive on the current default main tab. These variants document future integration boundaries; they do not grant consent, send files, produce answers, invent procedures or simulate successful cleanup. Root owns the Seryo composition.

| State | Title / content | Actions |
| --- | --- | --- |
| G12.1 Optional AI consent | Before using Seryo / Explain that approved questions and selected attachments would be sent to the configured AI provider, separately from required app privacy consent. Link actual privacy information. | Allow for this use; Not now. Planned prototype only until consent/contract/procedures exist. |
| G12.2 Consent declined | Seryo is off / You can still browse offices and contact staff. | Browse offices; Review AI privacy. No blocked core app workflow. |
| G12.3 Selected file | Review attachment / Actual selected filename/type/size; not yet sent. | Remove file; Back to chat. Send disabled in current setup. |
| G12.4 Invalid attachment | Attachment not accepted / Display the server-configured reason for type, count, or size; do not invent numeric limits. | Choose another file; Remove; Back. Consent/type/count/size checked before provider call. |
| G12.5 Analyzing/busy | Seryo is working… / Keep current message/attachment visible without a fabricated answer. | Block duplicate send; Cancel only if actual provider cancellation exists. Mark Planned. |
| G12.6 Unsupported question | Seryo can't answer this request / Ask the relevant office for an authoritative answer. | Browse offices; Ask an office. No sample answer or invented Seryo policy. |
| G12.7 Rate limited | Try again later / Seryo is temporarily unavailable for another request. | Return to chat; Browse offices. Show a retry time only if actually supplied. |
| G12.8 Cleanup unconfirmed | File cleanup not confirmed / The app hasn't received confirmation that the temporary file was removed. | Close; keep controls inactive while unresolved according to eventual contract. Never say deleted. |
| G12.9 Cleanup verified | Temporary file removed / This state may appear only after verified removal. | Back to chat. Planned visual variant only; no persisted chat/file history added. |

Consent text about precise processor, retention, deletion timing and responsibilities requires approved provider/privacy details. Current setup/Privacy/attachment/planned response/provider failure frames remain valid anchors.

## G13 — Office update operation states

One existing availability/announcement editor `24:186` / `36:954`, with an inline operation block. Office-wide publication is an authorized staff action.

| State | Title / content | Actions |
| --- | --- | --- |
| G13.1 Saving | Publishing update… / Keep edited office state/announcement visible. | Disable duplicate Publish; preserve text. |
| G13.2 Failed | Update wasn't published / Your changes are still here. | Retry; Continue editing; Cancel returns to prior published value. |
| G13.3 Confirmed publication | Update published / Show actual new availability/announcement plus server-confirmed actor/time. | View office → corresponding student-visible office variant; Back to dashboard. |

Do not claim live publication from local navigation or a button press alone. Student office page shows last confirmed content.

## G14 — Inquiry sending and assignment

| State | Title / content | Actions |
| --- | --- | --- |
| G14.1 Authorized recipient/assignment | Assign inquiry / List only destinations permitted for the current office/capability. Show current assignment where present. | Review assignment; Cancel preserves current owner. After confirmed save, show assigned destination in thread. |
| G14.2 Sending | Sending message… / Keep message visible and composer locked against duplicate send. | Back remains safe; no delivered/sent check until confirmation. |
| G14.3 Failed | Message wasn't sent / Your message is still here. | Retry; Edit message; Cancel retains unsent draft only where local preservation is actually supported. |

Student recipient selection is an office destination, not staff assignment authority. Staff assignment appears only where authorized. Preserve unsent text on recoverable failure; no typing, presence, read-receipt or persistent draft feature is added.

## Prototype routes required for review

1. New-account Home → Inbox/Notifications/My appointments empty → useful creation/browse action → Back; new account must never enter populated examples accidentally.
2. Office dashboard → queue/inbox/schedule empty → Back; teller pending/history empty → reference lookup. Keep real office-wide data separate from initial personal emptiness.
3. Sign-in method → corresponding failure → correction/retry; session expired → sign in → authorized return; denied record → own list.
4. Directory → loading/error retry preserving filter; search → no matches → clear; office with no services → inquiry.
5. Office → configured service → form → fee-aware review → submitting → receipt. Free branch skips payment; required branch reaches Cashiering payment choice; pending declaration never claims paid/processing.
6. Authorized office service summary → fee editor → review/save/error → confirmed read-only summary. No route from student/teller to fee editing.
7. Allowed request action → reason validation → confirmation → terminal result. Paid result → Cashiering inquiry; request payment record stays paid.
8. Payment pending → Check status → refresh failure or authoritative example result → request; unavailable provider stays disabled on default path.
9. Teller lookup → not found/edit or pending review → confirming → failure/recheck or receipt → history. Already-paid lookup routes to duplicate-denied, not another confirm.
10. Consult → new-booking review → booked detail; booked detail → reschedule selection/review → conflict keeps original or confirmed changed detail. Cancel has explicit keep/confirm routes.
11. File selection → review/remove/replace → upload failure/retry; verified file → blocked/unavailable/expired → correct request/office route. Unauthorized path never exposes preview.
12. Student pickup → readable token; staff scanner/manual entry → matching unknown/expired/used/unauthorized/success outcome → queue/scan again.
13. Seryo current inactive page → Privacy and Browse offices; future planned consent decline returns safely. Planned file/busy/refusal/limit/cleanup variants remain isolated examples.
14. Staff office update → saving/failure/retry/confirmed → office preview. Inquiry → recipient → sending/failure/retry; authorized assignment → review → confirmed thread.

## True unknowns and conservative defaults

- **Pending-fee submission:** whether an unconfigured fee can create a new request is not explicit. Default new-request Submit remains disabled while fee declaration is pending; existing submitted pending records remain viewable. Do not invent a draft feature or processing permission.
- **Fee revisions:** official amounts, staff permissions, and the effect of changes on existing requests need authoritative configuration. No invented amount or retroactive charge/refund.
- **Provider contracts:** eGovPay create/status/callback authentication and amount contracts remain unverified. eGovAI request/file/cleanup contracts and Seryo tasks/procedures remain unspecified. Credentials alone do not resolve them.
- **Attachment constraints:** use confirmed configuration; do not invent count/size/type limits in UI copy.
- **Booking/cancellation policy:** no cancellation window, capacity value or reschedule deadline is invented. Show only server-authorized actions.
- **Scope:** confirmed offices are Registrar, Student Accounting, Cashiering, Guidance and Counseling Center, and College of Computer Studies. Legacy OSA mentions should be removed from the active prototype or explicitly archived pending user confirmation; do not add a sixth office silently.
- **Actor/time/receipts:** values shown on example frames must be labeled demonstration. Live success UI waits for server confirmation.

## Handoff acceptance

Before presenting UI for finalization, every named state must have an appropriate contextual Figma instance/variant and a useful return route in both themes. Check 390dp and 320dp, long office names, large text and growing cards in one bounded visual batch plus one fix batch. Figma inspection does not prove native text scaling, security, provider integration or backend acceptance tests. Obtain the user's UI finalization before Android changes resume.

## Contextual prototype corrections

`scripts/figma_ui_gap_states_extra.json` adds 12 necessary contextual variants beyond the original 76-state inventory. They are design data, not new app features or backend changes.

- Remap **G04.7 → View request → G04.detail-free**. This is a Submitted free request, not the existing Ready/verified-file example. No personal reference is invented. Actual reference, actor and time come from confirmed data.
- **G05.1** Free has no amount field. Payment required selects **G05.required**, with blank amount and disabled Save. Pending selects **G05.pending**, without amount. Do not imply that static Figma fields accept edits or persist declarations.
- Staff rejection uses **G06.staff-reject → G06.staff-reject-error** on empty reason; staff cancellation uses **G06.staff-cancel → G06.staff-cancel-error**. Valid-reason confirmation examples are separate, explicitly labeled fixtures, not evidence that the prototype captured input.
- Staff confirmation/result keys are distinct: **G06.staff-reject-confirm → G06.staff-rejected** and **G06.staff-cancel-confirm → G06.staff-cancelled**. Both use Staff navigation and return to the staff queue. Paid examples remain Paid and offer Cashiering follow-up without refund/void promises.
- Free student cancellation confirmation is **G06.free-confirm → G06.4**, with no paid notice. Keep request returns **G04.detail-free**. Preserve context when returning to reason or terminal result; do not route a completed cancellation back to a Processing example.

These 12 additions bring the named design-state inventory to **88**, before any separately documented renderer-specific contextual variants. The original 31 reusable composition families remain sufficient.
