# GoServe Updated Program and Implementation Plan

Version 2.3 | 16 October 2026

A Native Android Application for Campus Operations, Services, and Consultations

## 1. Document authority and project boundary

This plan replaces Version 2.2 as the current program and implementation contract. The approved Version 2.3 requirements control authentication, fees, live updates, student-initiated pickup, digital receipt, and implementation order. Earlier documents remain historical evidence where their requirements conflict with this plan.

| Item | Contract |
| --- | --- |
| Deployment | Sandbox demonstration for TIP Manila, Casal Campus and Arlegui Campus only |
| Android | Native Java 17, XML ConstraintLayout, Android Studio and Gradle; minimum API 26, compile/target API 35; installable APK |
| Backend | Supabase Authentication, PostgreSQL, Storage, Realtime and Edge Functions only |
| Actors | Student, Staff and Teller; Guidance, Faculty and CCS secretary are Staff capabilities |
| Payments | Request-type-specific fees; free requests skip payment; eGovPay and teller-confirmed cash for configured paid requests |
| Assistance | Seryo powered by eGovAI; approved guidance and one temporary inline file, subject to consent and provider gates |
| Schedule | Milestone gates; deadline, team size and individual assignments have not been provided |
| Current gate | M1 UI approval is pending; new pickup and digital receipt panels require review before Android feature implementation |

Use three evidence labels throughout implementation: [CONFIRMED] is an agreed requirement; [DEMO] is clearly labeled non-official demonstration content; [OPEN] is an input that must be supplied or approved. Requirements describe the intended complete system and do not prove that the system already implements them.

Do not invent official TIP policies, fees, service lists, student-ID formats, processing times, schedules, personnel, refund rules or provider payloads. Keep unconfigured operations visibly unavailable. Do not put credentials, access codes, raw tokens or real student information in this document, source control, logs or the APK. Configuration of credentials is not evidence that a feature has been tested.

The demonstration does not connect to an official student information system or alter official registrar grade records. TIP Quezon City, other institutions, web portals, cross-platform mobile frameworks, public privileged signup, platform-admin screens, automatic refunds, typing indicators, presence, operational email notifications, device push, persistent Seryo history, broad ChatGPT tools and autonomous Seryo writes are outside scope. Authentication recovery email remains allowed.

## 2. Program objectives and complete P1 scope

GoServe lets students find office availability, submit complete service requests, track progress, pay declared fees, receive private documents, arrange consultations and contact the correct office. Office personnel manage authorized work and publish current information. Tellers confirm eligible cash transactions. All committed cross-role operations must become visible through the same Supabase records without manual refresh.

All professor-facing proposal features remain P1. A provider or policy gate delays activation of the affected operation; it does not remove that operation from the required final demonstration. If capacity or time becomes insufficient, renegotiate scope explicitly instead of silently dropping features.

| ID | Required program capability | Completion evidence |
| --- | --- | --- |
| P1-01 | Office directory, availability, notes and announcements | Staff changes persist and authorized student views update live |
| P1-02 | Requests, private evidence and progress history | Student submission reaches the owning office and both roles see committed changes |
| P1-03 | Per-request-type fees and payments | Pending/free/required branches, frozen quotes, teller cash and verified eGovPay outcomes |
| P1-04 | Guidance consultations | Configured capacity, atomic booking, cancellation and safe rescheduling |
| P1-05 | CCS faculty consultations | Faculty availability and authorized secretary coordination |
| P1-06 | Office inquiries | One persisted conversation shared by student and authorized office with messages and read states |
| P1-07 | Physical pickup | Student Claim Request, server-issued QR/manual code, staff review and explicit handover confirmation |
| P1-08 | Digital delivery | Office-verified private PDF, successful student download and explicit receipt confirmation |
| P1-09 | Seryo | Grounded guidance, separate AI consent, one temporary file, refusal, retry and verified cleanup |
| P1-10 | Realtime and native access | Account-scoped live views, reconnect recovery and verified APK/device behavior |

## 3. Actors, capabilities and office coverage

| Actor | Authorized work | Restriction |
| --- | --- | --- |
| Student | Own profile, requests, files, payments, appointments, inquiries, notification/read states and Seryo consent | Cannot grant roles, change fees, verify payments or release office documents |
| Staff | Assigned office requests, evidence, status, verified releases, inquiries, announcements and availability | Office and capability boundaries apply to every database, file and trusted operation |
| Staff: Guidance | Guidance availability, consultation bookings and assigned inquiries | No unrelated office records |
| Staff: Faculty | Own CCS faculty availability, consultations and assigned work | No unrestricted faculty or office administration |
| Staff: CCS secretary | Authorized CCS schedule coordination and assigned requests/inquiries | Capability is provisioned by trusted administration |
| Teller | Eligible cash lookup and confirmation using authoritative amount | No unrelated request-content or office-release authority |

| Office ID | Demo office | Coverage |
| --- | --- | --- |
| OFF-REG | Registrar | Casal / Arlegui |
| OFF-ACC | Student Accounting | Casal / Arlegui |
| OFF-CASH | Cashiering | Casal / Arlegui |
| OFF-GCC | Guidance and Counseling Center | Casal / Arlegui |
| DEP-CCS | College of Computer Studies | CCS secretary and faculty coordination, Casal / Arlegui |

The office list is demonstration coverage, not a claim that every service exists at both campuses. Office status values remain Open, On Lunch Break, Cut-Off Reached and Temporarily Closed. Authorized staff changes record actor, timestamp and optional note. Do not infer office status from a device clock or invent a schedule. Payments directory filtering shows Cashiering only; another office can link to Cashiering when an actual fee applies.

## 4. Authentication, profile and Terms

### 4.1 Email registration and explicit sign-in

The first email registration flow is Terms agreement, email/password/confirmation, one complete personal-information form, Create account, Account created, then explicit Sign in. The Sign in password field is blank. Account-created UI must not imply automatic access to Home. The sandbox does not insert an email-verification detour. The example student1@demo.com is supported as a demonstration registration address; it does not prove that its mailbox can receive recovery email.

Before the profile form opens, validate a well-formed email, password of at least six total characters containing a letter, a digit and a symbol, and matching confirmation. Show the password rule and adjacent errors. Sign-in checks the actual existing password; a matching password format alone is not authentication. Support visibility controls and preserve appropriate form input on correctable failures. Do not retain or prefill a password after the account-created transition.

The combined profile requires first name, middle name or an explicit No middle name exception, last name, student ID, course/program, year level, mobile number, self-reported gender and email. The No middle name control starts unchecked; checking it records the exception without fabricating a name. Labels omit required/mandatory suffixes while validation remains mandatory. Do not invent fixed student-ID lengths, phone formats, program catalogs, year enums or binary gender values. Use approved constraints when supplied.

New email accounts are Students. Staff and Teller identities and capabilities require trusted provisioning. Client metadata, a role picker, altered API input or direct database writes cannot grant privilege. Fresh accounts begin with empty request, payment and inquiry lists; demo fixtures must never appear as the student's real records.

### 4.2 Google and session routing

New Google registration is Terms agreement, Google provider through Supabase OAuth with PKCE, the same combined profile form, successful profile save, then Home. Accept verified institutional @tip.edu.ph Google identities only. The provider email is read-only. Cancellation, wrong domain, unverified identity, provider failure and interrupted profile save need explicit recovery states without protected access.

Returning complete accounts skip onboarding. Valid persisted sessions with complete profiles reopen directly to the correct role destination without an authentication-form flash. Incomplete accounts resume the missing agreement/profile step. Sign-out and nonrecoverable expiry return to Sign in. Store profile and agreement completion on the server, not in a device first-launch flag. Protect refresh credentials through Android Keystore and clear account-specific caches/subscriptions on sign-out or account change.

Registration Terms are separate from optional AI consent. Record the accepted Terms version, server timestamp and actor against the identity. Provide a read-only Terms viewer in Settings for every role. Final institutional Terms/privacy text and any future reacceptance policy require approval; do not fabricate them.

### 4.3 Password recovery

Place Forgot password below the password field. A recovery request requires a real reachable mailbox, configured delivery and validated redirect/deep-link handling. Use a neutral request-sent state without revealing whether an account exists. A valid recovery context permits password/confirmation reset under the approved rule; invalid or expired links offer a new request or Sign in. Do not expose raw tokens.

Handle an explicit recovery intent before ordinary completed-session routing. An ordinary session or local navigation is not reset authority. After reset, route to explicit Sign in with blank password and apply normal profile/agreement guards. Do not claim successful recovery for demo.com without verified mailbox access. This recovery flow does not add registration email verification.

## 5. Offices, request types and fee authority

Authorized offices manage their own request-type catalog, delivery modes, evidence requirements and fee declarations. Demonstration content stays labeled non-official until approved. The catalog must distinguish three fee states for each request type.

| Fee state | Meaning | Student behavior |
| --- | --- | --- |
| pending | Office has not supplied an approved fee declaration | Show fee unavailable/pending; block Submit |
| free | Office explicitly declares no fee | Show zero, skip payment selection and do not create a payment record |
| required | Office explicitly declares a positive fee with currency and version | Show authoritative amount and applicable eGovPay/cash choices |

Null or missing configuration is not free. Office-level assumptions cannot replace a request-type declaration. The server loads the current fee and produces the review quote. On submission it atomically validates the quote/version and freezes amount, currency, fee state and catalog version on the request. Client-supplied amounts are ignored or rejected. If the review quote is stale, require review again before submission.

Later catalog changes apply to future submissions only. They do not silently reprice an existing request or payment. Display the frozen request snapshot consistently to student, office and teller. Audit catalog changes and test concurrent quote/submission edits. A paid example may be configured only after an authorized office declares a labeled demo fee; no amount is invented here.

## 6. Requests and separate state machines

The request form shows the selected office/type, configured requirements, evidence, notes, delivery mode and current server quote. Submission rechecks identity, completed profile, catalog availability, evidence authorization and fee version. Use an idempotency key or equivalent server uniqueness control so retries create one request. The office queue and student Requests view observe the same persisted record and history.

| Exact request status | Meaning and allowed progression |
| --- | --- |
| Submitted | Accepted submission awaiting authorized office/payment handling |
| Awaiting Payment | Required fee is not yet authoritatively paid; release remains blocked |
| Processing | Authorized office is working on the eligible request |
| Ready for Pickup/Download | Office has prepared the selected physical or digital deliverable; the request is not completed |
| Completed | Physical handover or digital receipt confirmation completed through its trusted operation |
| Cancelled | Allowed cancellation with mandatory reason and actor/time |
| Rejected | Authorized office rejection with mandatory reason and actor/time |

Payment status remains exactly pending, paid, failed, expired or cancelled. Store it separately from request progress. Payment success does not mean document completion. A failed payment does not erase a request or authorize a release. Server transition rules enforce actor, office, current state and fee eligibility; clients cannot bypass them by editing a status field.

Keep append-only status events with from/to state, actor, reason and server time. Cancellation and rejection require reasons. Applicable cancellation windows and actors must follow approved policy; missing policy keeps that action unavailable. A paid record survives cancellation/rejection, with manual office follow-up and no automatic refund or void. Terminal states are Completed, Cancelled and Rejected.

### 6.1 Physical pickup: student initiates the claim

1. The office marks the physical request Ready for Pickup/Download. This does not generate a QR or code.
2. When the student wants to claim, the student taps Claim Request. The server validates ownership, physical delivery mode, Ready status, required payment and configured pickup validity.
3. Generate or reuse one active pickup code for that request. Repeated taps and concurrent retries must not create competing active codes. Expiry is required approved configuration; no arbitrary time-to-live is specified in this plan.
4. Display the QR and manual-entry representation privately to the owner. Store a protected hash for validation and a protected private display representation sufficient to redisplay the same active code. Do not publish raw codes through Realtime, logs or public files.
5. Authorized office staff scan with CameraX/QR decoding after camera permission, or enter the code manually. Server validation checks the code, expiry, request, office, current state and payment. Scanning opens review; scanning alone does not complete the request.
6. Staff explicitly confirm actual handover. A single atomic operation consumes the code, records actor/time and changes the request to Completed. Both roles receive the committed completion through Realtime.

Reject unknown, expired, wrong-office, reused, cancelled, ineligible and concurrent duplicate attempts. An expired code can be replaced only through the authorized student claim flow after revalidation. Permission denial or unavailable camera retains manual entry with identical server checks. Do not imply that code possession grants office authority.

### 6.2 Digital delivery: student confirms receipt

Authorized office staff upload and verify a PDF into private Storage and mark the digital request Ready for Pickup/Download. The owner can download only after status and required payment gates pass. Use short-lived authorized access and enforce ownership at the server, including direct object-access attempts.

A successful student download enables Confirm receipt. The student explicitly confirms receipt, and the server checks the successful authorized download record and current eligibility before atomically recording receipt and Completed. Both student and office update live. Opening a viewer, issuing a link, starting a transfer or a failed download is not completion. A retry must not create duplicate completion events. Digital completion never requires a pickup QR.

## 7. Payments and reconciliation

Payments exist only for requests whose frozen fee state is required. The method is egovpay or cash. All amount/currency checks use the request snapshot. Payment attempts, provider events and financial audit remain available independently of request-file cleanup.

### 7.1 Teller cash

The Teller searches an eligible request/reference, reviews its authoritative amount and pending cash state, and explicitly confirms payment. A trusted operation rechecks Teller authority and pending state in a transaction, records confirmedBy/confirmedAt and applies paid exactly once. Duplicate or concurrent confirmation is rejected without a second financial event. Students and ordinary Staff cannot perform this operation. Student and office views receive the committed payment update live.

### 7.2 eGovPay

A trusted Edge Function creates a transaction from the request snapshot and server secrets. The provider callback must be authenticated, stored idempotently and reconciled server-side using the approved provider contract. Only verified callback/reconciliation outcomes may set paid. Android browser return merely triggers canonical status retrieval; it is never payment proof.

Handle pending, paid, failed, expired and cancelled through verified status mappings. Validate transaction identity, event uniqueness, amount and currency; reject mismatches and forged/replayed events. Retry and late/out-of-order callbacks must preserve one authoritative result and complete audit. Reconciliation must recover missed callbacks without trusting a client claim.

Supplied server configuration exists, but checkout payloads, signing rules, callback authentication/schema, reconciliation and amount canonicalization remain gated inputs. Transport stays disabled until contracts and tests pass. Any simulator must be labeled, isolated from real authority and tested against the internal state contract. A user-reported provider test control is not a verified provider capability.

## 8. Consultations and shared inquiries

Guidance owns guidance availability. CCS faculty own their availability, with authorized CCS secretary coordination. Slot start/end, duration, capacity and policy are configured demo data until approved. The server books capacity atomically and rejects overbooking, including simultaneous clients. Student cancellation and rescheduling record actor/time and notify authorized participants in-app.

Rescheduling is atomic: validate and reserve the target before releasing the original booking. If the new slot is full, unavailable or the operation fails, preserve the old booking. Cancellation/rescheduling windows remain approved configuration, not guessed rules. Changes to schedules and appointments update both roles through Realtime.

An inquiry is one persisted thread connecting a student, destination office and optional assigned authorized recipient. Store messages, sender, timestamp and per-user read state against that thread. Student Inbox and office Inbox use those same records; do not maintain isolated mock conversations. Assignment can route to office personnel, Guidance, CCS secretary or a faculty member within capability boundaries.

Students read only their own threads. Staff read only assigned/office-authorized threads. Message retries deduplicate; read-state updates persist and reach authorized views. Notifications link to actual records without copying private content to unauthorized audiences. Messaging is asynchronous; no typing/presence feature is added.

## 9. Realtime and native architecture

### 9.1 Shared authenticated data source

Use Java/XML with AndroidX fragments, ViewModel/LiveData and view binding. HTTPS operations run off the UI thread. A shared authenticated RealtimeDataSource owns session-bound subscriptions, token refresh, lifecycle cleanup and reconnect recovery. Feature repositories read/write canonical Supabase records and expose consistent role views. Realtime infrastructure must precede dependent operational modules.

Correct publication membership and table-specific RLS are required for every operational table. A subscription is not an authorization boundary by itself. Validate cross-student, cross-office, faculty, secretary and Teller boundaries for database reads/writes, Realtime delivery, Storage and trusted operations. Use database change delivery or authorized private channels without sensitive broadcast payloads.

| Committed operation | Required live observation |
| --- | --- |
| Request submission/status/cancel/reject | Student detail/list and owning office queue/history |
| Fee/catalog declaration | Future request forms/review; frozen submitted snapshots remain stable |
| Inquiry/message/read state | Student and authorized office thread/list/read indicators |
| Cash or verified provider payment | Student, owning office and authorized Teller views |
| Consultation or reschedule | Student, schedule owner and authorized coordinator |
| Availability/announcement | Authorized directory/detail/notification views |
| Physical handover/digital receipt | Both request views show one Completed event |

After reconnect, retrieve a canonical snapshot to close missed-event gaps. Deduplicate by record/event identity and version, ignore stale asynchronous responses, and handle ordering without rolling a newer state backward. Reauthentication updates channel credentials. Sign-out/account switch cancels subscriptions, in-flight account work and caches before another account renders. Loading/offline/error indicators must reflect actual work and never fabricate success.

### 9.2 Public interface groups

These are proposed application contracts, not provider endpoint payloads or a claim of deployed functions. Final naming can be normalized without weakening the rules.

| Interface group | Operations and trusted checks |
| --- | --- |
| Identity/profile | Register Student, bootstrap verified session, save complete profile, accept/version Terms, recovery and session routing |
| Office/catalog | Read authorized directory; capability-scoped status, announcement, request-type and fee updates |
| Request lifecycle | Quote/review, submit with quote validation, transition with reason, history and idempotent retry |
| Inquiry | Create/read authorized thread, send message, assignment and per-user read state |
| Payment | Create attempt, Teller cash confirmation, authenticated callback, reconciliation and audit |
| Delivery | Verify PDF, authorize/record download, confirm receipt; student issue/reuse claim, validate code, confirm handover |
| Consultation | Availability, atomic book/cancel/reschedule and coordinator authorization |
| Seryo | Separate consent, approved knowledge, validated temporary file, bounded provider call and verified cleanup |
| Retention | Eligible request-file cleanup, immediate temporary-file cleanup and non-sensitive audit |

Supabase Auth verifies the actual identity; trusted bootstrap returns the server-managed profile, role, office and capability. PostgreSQL/Storage policies use auth.uid() and authoritative assignments. Service credentials and provider secrets remain server-only. Trusted transactions or RPCs enforce multi-record invariants; a UI sequence cannot substitute for atomic server checks.

## 10. Data records, files and privacy

| Record group | Required records and controls |
| --- | --- |
| Identity | Profiles with required fields, role/capability/office, profile completion; versioned Terms acceptance and separate AI consent audit |
| Office catalog | Offices, statuses/actor/time, announcements, request types, fee declaration/version/currency, requirements, delivery mode and demo labels |
| Requests | Ownership/office/type, immutable submitted fee snapshot, delivery mode, reference, current state, terminal time and status events |
| Payments | Request/method/snapshot amount, attempt status, provider reference, confirmation actor/time, unique provider events and reconciliation audit |
| Files | Owner/request, private object path, kind, MIME/content validation, size, verification, expiry and cleanup result |
| Delivery | Pickup hash/private display/expiry/consumption; authorized download success and student receipt confirmation audit |
| Consultations | Availability/capacity, faculty/office, appointments, cancellation/reschedule history and transaction controls |
| Inquiries | Student/office/assignment threads, deduplicated messages, read states and authorized notifications |
| Seryo/configuration | Approved knowledge, procedure/configuration readiness, optional consent, temporary processing/cleanup state; no retained chat or original-file history |
| Audit | Actor, target, action, time, idempotency and outcome without secret/token/document/prompt/response contents |

Planned records are not deployed simply because this table names them. Foundation profiles/schema need migration to the new contract after M1/M2. Keep one authoritative identity/profile model rather than duplicating students across competing user tables.

Office assets may be public-read only when approved. Profile images, request evidence, verified PDF deliverables and temporary AI files remain private. Evidence type/size rules must come from approved request configuration. Signed access remains short-lived and subject to ownership/status/payment checks; public URLs are not acceptable for private evidence or deliverables.

Delete eligible request evidence and deliverables 30 days after Completed, Cancelled or Rejected. Record terminal time and cleanup results; retry failed cleanup and verify deletion. Financial records and financial audit have independent retention and must not disappear with files. Do not invent a financial retention period.

Temporary AI objects are deleted immediately after analysis or handled failure. Cleanup must be server-verified; unknown cleanup blocks further processing. Record only the minimum non-sensitive operational outcome. Review applicable privacy and TIP requirements before any real deployment; the sandbox plan is not approval to process production data.

## 11. Seryo program contract

Seryo is eGovAI assistance using approved knowledge or the consenting user's current temporary file. Responses are labeled guidance and cannot invent unsupported institutional answers. Unsupported scope directs the user to the relevant office. Provider request/response rules, approved procedures, agent/task identifiers, limits and permitted file capabilities remain [OPEN]. Server credentials alone cannot enable Send, uploads or transport.

AI consent is optional and separate from registration Terms. Allow opens an empty allowed chat without selecting a file, opening a picker or sending a question. Decline opens an AI-off chat with ordinary navigation available. AI privacy permits reconsideration. Recheck current consent/session/provider/cleanup state before each processing operation. Revoking consent blocks new processing and does not falsely claim that an in-flight operation was cancelled.

The plus menu contains only Attach a file and AI privacy. Accept one PDF, JPG or PNG up to 10 MB inside the chat composer. Validate count, size, MIME and actual content on the server, including corrupt/unreadable files and inaccessible Android URIs. No separate AI Upload page, camera capture, bulk upload or saved file gallery is added.

Explain and Extract text prepare an unsent question using the current attachment. Selection alone never analyzes. The student must explicitly Send after readiness checks pass. Picker cancellation preserves the actual thread/draft/operation/previous attachment. Replacement retains one attachment. Invalid selection preserves a prior valid attachment. Remove clears the attachment and associated operation while preserving unrelated typed text.

Preserve current conversation context across picker/privacy returns and lifecycle changes without adding persistent chat history. Retry restores an unsent operation draft; it never silently resends or bypasses unresolved cleanup. Busy, timeout, offline, auth failure, rate limit, refusal, unreadable text and provider failure need honest states. Do not fabricate a result, confidence score, retry delay or deletion success.

Contextual reply buttons such as Open Cashiering or a named office navigate only. They cannot submit an inquiry/request, book a consultation, declare a fee, start checkout or mark paid. File contents are untrusted data to explain/extract, not instructions granting tools or cross-account access. Broad ChatGPT tools and autonomous writes remain excluded.

## 12. Interface and accessibility contract

Student navigation remains Home, Requests, Consult, Inbox, AI Chat and More. AI Chat opens Seryo directly. Staff and Teller destinations are role-scoped. Shared screens cover Welcome, Sign in, Terms, registration, combined profile, account-created, recovery and Settings. Settings includes the read-only Terms viewer for all roles.

Requests cover quote review, pending fee, free/paid branching, evidence, history, cancellation/rejection reasons, Claim Request, private active code, staff scan/manual review, explicit handover, verified PDF download and student Confirm receipt. The latest pickup and digital receipt panels must be added to M1 review; earlier ready-to-QR prototypes do not satisfy Version 2.3.

Use logo-only identity, Montserrat headings and Karla body/UI copy, monochrome Light/Dark themes, compact floating authentication composition and consistent theme-aware shadows. Preserve supplied logo aspect ratio. Loading corresponds to real work without fabricated progress or delays. Use sentence case, clear adjacent errors and visible focus/pressed/error/disabled states.

Use minimum 48dp touch targets, system/IME insets, bounded vertical scrolling for long content, readable wrapping and reachable final controls. Reflow at compact widths and with 2x fonts. Preserve visible chat content above the composer and bottom navigation. Test keyboard avoidance, focus order, TalkBack labels/announcements, contrast and state visibility on actual devices. Figma visual variants do not prove native accessibility or lifecycle behavior.

## 13. Honest progress baseline

As of this version, earlier Android/Supabase foundation work includes Java/XML hosts, themes, navigation, authentication/session plumbing, role routing, directory/settings and foundation schema/RLS. Those foundations follow earlier policies and are not proof of the new registration/profile requirements or operational modules.

The historical October 15 Android XML report records 41 tests, 39 passing and two failing. A fresh October 16 baseline run was blocked by Gradle's "Unable to establish loopback connection" error, including installed JBR 25 and Corretto 21 attempts. This is a blocked rerun, not a new passing or failing test result. The current evidence is recorded in docs/GoServe-native-baseline-2026-10-16.md and its matching JSON. Physical-device verification of the integrated P1 workflow is pending. Realtime infrastructure and operational request, payment, inquiry, consultation and delivery tables/workflows are not implemented by this document.

Earlier Figma evidence records 19 Seryo cases, 38 themed frames plus two overlays, 42 responsive proofs and 468 scroll-audited viewports. These figures describe the inspected historical design before the latest student-initiated pickup and digital receipt changes. They do not prove that the latest delivery rules are designed, approved or implemented. M1 approval remains pending.

The separate Version 2.3 Figma page is a concise professor-facing overview: 16 key Light wireframes across authentication, student services and office operations. Detailed edge cases remain in this plan and the existing design inventory; P1 scope is unchanged. Review: https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=305-2. UI approval remains pending.

eGovPay/eGovAI server configuration exists with transports disabled. UI previews do not authenticate, create accounts, submit requests, confirm payments, process files or verify deletion. No native feature completion or provider test is claimed by saving this plan/PDF.

## 14. Milestones and implementation gates

No fixed deadline, duration, team allocation or completion percentage is invented. Assign people and calendar dates when provided. Each milestone exits with reviewable evidence; dependent work cannot treat a failed gate as complete.

| Gate | Work package | Exit condition and dependencies |
| --- | --- | --- |
| M1 | UI approval | Review latest auth/fees/live states, student Claim Request and digital receipt panels in Light/Dark; user approves before Android feature code |
| M2 | Baseline verification | Capture current build/test/lint results, resolve or explicitly record failures, inspect existing auth/schema and establish migration baseline; after M1 |
| M3 | Auth and profiles | Email/Google flows, required combined profile, versioned Terms, explicit sign-in, recovery/session guards, privilege denial and RLS pass; after M2 |
| M4 | Realtime foundation | Shared authenticated RealtimeDataSource, publications/RLS, reconnect snapshot, stale-response/dedupe/account-switch tests; after M3 |
| M5 | Office catalog and fees | Scoped availability/announcements/types, pending/free/required, quote version and immutable snapshot tests; after M4 |
| M6 | Requests and evidence | Idempotent submit, office queue, private evidence, transitions/reasons and two-role live proof; after M5 |
| M7 | Inquiries | Shared persisted threads/messages/read states, assignment, notifications and live two-role proof; after M4/M5 |
| M8 | Payments | Exactly-once Teller cash; eGovPay only after provider contract, callback/reconciliation tests and snapshot amount checks; after M6 |
| M9 | Delivery | Student-initiated active pickup code, explicit office handover; private PDF/download/student receipt; concurrency and live completion proof; after M6/M8 gates |
| M10 | Consultations | Guidance/CCS schedules, atomic capacity and safe rescheduling, role checks and live proof; after M4/M5 |
| M11 | Seryo | Approved contract/procedures/limits, optional consent, inline file validation, unsent drafts, bounded guidance and verified cleanup; after relevant identity/file foundations |
| M12 | Integration and documentation | Full acceptance/security/device/reconnect/retention evidence, APK package, source/migrations and matching final documentation; after M3-M11 |

Within each package implement one complete persisted role-to-role flow, verify its negative/retry cases, then finish the remaining approved states. Do not substitute fixtures for integration evidence. Keep unavailable provider operations explicitly gated while preparing the local contract and tests.

## 15. Acceptance and traceability plan

Maintain a matrix linking each P1 capability and the tests below to UI panels, Android classes, database policies/migrations, trusted operations and evidence files. A test is pending until executed against the relevant implementation. Record account roles, device/build, environment, expected/actual outcome and sanitized evidence.

| ID | Acceptance scenario | Required result |
| --- | --- | --- |
| AT-01 | First email registration | Terms, valid password/confirmation, single complete profile, Create account, Created, explicit Sign in with blank password; no verification detour |
| AT-02 | Missing/invalid profile or password | Adjacent errors; middle-name exception works; labels have no required suffix; invalid input cannot create complete profile |
| AT-03 | Fresh Student and privileged signup attempt | Empty actual records; no role escalation from UI/metadata/direct API |
| AT-04 | New institutional Google | Verified @tip.edu.ph, Terms/profile/save then Home; personal/unverified/cancel/failure blocked safely |
| AT-05 | Returning session, restart and account switch | Complete accounts skip onboarding; incomplete resumes; no form flash, leaked cache or old subscription |
| AT-06 | Terms and recovery | Server version/time audit; read-only viewer all roles; reachable-mailbox valid/invalid/expired recovery; blank-password Sign in |
| AT-07 | Cross-student/office/capability access | Database, Storage, Realtime and trusted operations deny unauthorized reads/writes/subscriptions |
| AT-08 | Availability and announcements | Authorized staff write persists and student sees update without refresh |
| AT-09 | Fee states | Pending blocks Submit; free shows zero/no payment; required uses office-declared positive amount/currency |
| AT-10 | Quote stale or catalog edited | Review again if stale; submitted snapshot unchanged by later edits or client amount tampering |
| AT-11 | Submit and retry/concurrent submit | Exactly one authorized request/history; office queue and student state update live |
| AT-12 | Evidence and private PDF access | Approved type/size/content only; owner/office boundaries; direct unauthorized object access denied |
| AT-13 | Request transitions | Exact seven statuses; actor/state guards; cancellation/rejection reason mandatory |
| AT-14 | Paid cancellation/rejection | Paid financial record preserved, manual follow-up, no automatic refund |
| AT-15 | Shared inquiry and replies | Same persisted thread/messages/read states visible to student and authorized office; retry deduped |
| AT-16 | Teller cash | Correct amount; Teller-only; concurrent/repeated confirmation applies paid once and updates both roles live |
| AT-17 | Provider callback and reconciliation | Authentication/replay/amount/currency checks; missed/duplicate/out-of-order events handled; browser return never proves paid |
| AT-18 | Payment outcomes and simulator | Five separate payment statuses; labeled isolated simulation; failed/expired/cancelled cannot release unpaid request |
| AT-19 | Office marks physical Ready | No QR/code is generated automatically |
| AT-20 | Student Claim Request | Owner/Ready/physical/fee/config checks; generate or reuse one active code; no competing retry codes |
| AT-21 | Scan/manual entry and review | Authorized office only; permission denial supports manual entry; scan does not complete |
| AT-22 | Explicit physical handover | Atomic consume and Completed exactly once; both roles live; expired/wrong-office/reused/concurrent cases rejected |
| AT-23 | Digital delivery and receipt | Verified private PDF; successful download then explicit student confirmation; failed/open-only does not complete; both roles live |
| AT-24 | Guidance/Faculty capacity | Simultaneous booking cannot overbook; only appropriate owner/coordinator manages schedule |
| AT-25 | Reschedule/cancellation | Target capacity atomic; failed reschedule preserves old booking; approved rules and live notifications |
| AT-26 | Realtime interruption/order | Every committed operation updates authorized second role; reconnect canonical refresh closes gaps; stale events/responses cannot regress state |
| AT-27 | AI consent/privacy | Allow empty; Decline off; reconsider; separate from Terms; no automatic picker/send or persistent history |
| AT-28 | Inline file and drafts | One PDF/JPG/PNG <=10 MB; invalid/multiple/corrupt blocked; cancel/replace/remove/context preserved; Explain/Extract unsent |
| AT-29 | Seryo provider and scope | Disabled without approved inputs; grounded guidance/refusal; contextual office buttons navigate only; no autonomous writes |
| AT-30 | Seryo failures and cleanup | Rate/offline/auth/provider/unreadable/retry cases honest; immediate verified cleanup; unresolved cleanup blocks processing |
| AT-31 | Retention | Request files deleted 30 days after terminal state; failures retried/audited; financial audit retained independently |
| AT-32 | Native device and lifecycle | APK installs; API coverage, rotation/process return, keyboard/insets, 2x fonts, TalkBack, contrast, focus and 48dp targets verified |
| AT-33 | Complete demonstration | Free digital, configured paid cash physical, contracted online payment, consultations, inquiry and Seryo flows produce traceable evidence |

## 16. Inputs, risks and activation controls

| Input or risk | Required decision/control |
| --- | --- |
| Deadline and team | Provide final date, people and responsibilities before assigning a calendar |
| Terms/privacy and content owners | Approved versioned text and authorized institutional reviewers before release |
| Catalog/fees/evidence | Office-approved service names, delivery modes, pending/free/required declarations, currency and evidence limits; unavailable until configured |
| Consultation rules | Approved schedules, capacity, availability and cancel/reschedule policy |
| Pickup validity | Required approved expiry configuration and replacement policy; do not invent TTL |
| Request cancellation policy | Approved eligible states/actors/windows; preserve paid audit and manual follow-up |
| eGovPay contract | Checkout/signature/callback/reconciliation/status/amount contract, sandbox capability and evidence before enabling transport |
| eGovAI contract | Request/response, procedures, tasks/tools, limits, knowledge approval and file/deletion contract before enabling processing |
| Real recovery mailbox | Reachable test address, delivery and redirect configuration; demo address is insufficient proof |
| Devices and final submission | Approved Android test devices/API coverage and subject-specific packaging requirements |
| Live authorization leakage | Negative RLS/Storage/Realtime tests and cleared session subscriptions before account changes |
| Concurrency/data loss | Server transactions, event uniqueness, idempotency and retry tests for payment, claim, completion and capacity |
| Retention failure | Scheduled cleanup evidence, retry/audit and verified deletion; block AI when cleanup is unconfirmed |

Seed only the five demo offices. Provision trusted Student, second Student, Registrar, Accounting, Teller, Guidance, CCS secretary and CCS faculty test identities for positive/negative cases. Suggested aliases are test inputs, not claims of created accounts. Use no real student records. Keep demo services, fees, schedules, knowledge and estimates visibly non-official. Do not invent institutional data to make a screenshot look complete.

## 17. Final deliverable package and completion rule

Deliver the installable APK and identified build variant; Java/XML source and reproducible build instructions; versioned Supabase migrations, RLS/Storage/Realtime configuration and trusted-operation source; approved Figma panels/prototype links; this editable Markdown and matching PDF; a role-based demonstration guide; requirement-to-implementation/test traceability; configuration checklist without secrets; and sanitized test/device/provider/cleanup evidence with known limitations.

Completion requires the M12 evidence gate, not a percentage estimate or a configured credential. Every P1 flow must be implemented and demonstrated under its approved gates. Record any remaining unavailable input or failed test plainly. Update this plan and the matching PDF when an approved contract changes; retain historical versions as evidence without letting their superseded rules control new work.
