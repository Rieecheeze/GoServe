# GoServe project plan and grounding specification

> **Superseding authentication requirements — October 15, 2026.** Keep Google and entered email/password access. First-time email registration is Terms agreement → email/password/confirmation → one scrollable personal-information form → Create account → Account created → explicit Sign in with the same credentials. The password must have at least 6 characters, including a letter, a number and a symbol, and confirmation must match before the personal-information form opens. First-time Google registration is Terms agreement → provider → that same combined form → confirmed profile save. Both forms collect first/middle/last name (middle name exempt only through No middle name), student ID, course/program, year level, mobile number and self-reported gender. Field labels omit requirement suffixes while validation remains mandatory. Main entry is headed Welcome; post-registration entry is Sign in. Completed returning accounts skip onboarding. Valid persisted sessions with complete profiles reopen directly to the dashboard without an authentication-form flash; incomplete accounts resume onboarding; sign-out/nonrecoverable expiry returns to Sign in. Account/profile state is server-persisted, not a device-first-launch flag. New self-created profiles are students; staff/teller authority is not self-selected. See [the current registration UI contract](GoServe-auth-registration-ui.md).
>
> **Figma UI review comes first.** The authentication descriptions, FR-02/AT-03 signup prohibition, pre-created-account policies and implementation/deployment statements below record the earlier scope and source snapshot. They do not override this new design requirement or prove that it has been implemented. Android and Supabase signup configuration, password enforcement and session/profile guards remain unchanged pending UI approval and later implementation. No provider email-verification setting or real account creation is claimed by the UI preview. Other project requirements remain applicable unless separately superseded.

A Native Android Application for Campus Operations, Services, and Consultations

Project Plan and Grounding Specification

| Field | Value |
| --- | --- |
| Project | GoServe: A Native Android Application for Campus Operations, Services, and Consultations |
| Document version | Version 2.2, written October 15, 2026 |
| Deployment context | Sandbox demonstration for TIP Manila only |
| Campus coverage | Casal Campus and Arlegui Campus |
| Front end | Native Android application using Java and Android Studio (APK output). |
| Authentication | Supabase Authentication only; Google OAuth with PKCE and pre-created assigned-account password sign-in |
| Application backend | Supabase PostgreSQL, Supabase Storage, Supabase Realtime, and Supabase Edge Functions |
| Payments | Optional office-declared fees only; free requests skip payment. eGovPay and teller-confirmed cash remain planned payment flows. |
| AI assistance | Seryo, the eGovAI chatbot. Server credentials are configured; API transport and actions remain disabled pending the API contract and user-supplied procedures. |
| Deadline and team size | Not provided [OPEN] |

Purpose: a single source of truth for building and demonstrating GoServe without inventing institutional facts.

Read this first. Every statement carries or sits under one of three labels.

[CONFIRMED] Agreed project scope or technical contract. Treat it as binding for this plan.

[ASSUMPTION] A demo default chosen to keep implementation moving. Keep it visible and verify it before real deployment.

[OPEN] Information not yet issued or confirmed. Do not guess; use a visible placeholder until resolved.

## 0. Rules for AI models and readers using this document

- Use only the decisions and contracts stated in this plan. If a fact is not specified, identify the matching [OPEN] item in Section 15 instead of inventing it.

- Do not invent official TIP policies, fees, services, schedules, processing times, staff names, requirements, or approval rules. Seeded examples must be labeled as non-official demo data.

- Treat GoServe as a sandbox demonstration for TIP Manila's Casal Campus and Arlegui Campus only. TIP Quezon City, outside institutions, and changes to official registrar grade records are out of scope.

- Use the exact role, office-status, request-status, and payment-status values defined in Sections 3, 5, and 6.

- Use Supabase for all authentication, structured records, files, live updates, and trusted server operations. Firebase is excluded from the project.

- Use Supabase PostgreSQL for structured records, Supabase Storage for files, Supabase Realtime for in-app live updates, and Supabase Edge Functions for trusted server operations.

- Treat eGovPay callbacks and server-side reconciliation as authoritative. An Android app return, demo button, or client request cannot mark a payment as paid.

- Use teller-confirmed cash only through an authorized teller account. Keep payment status separate from request progress.

- Keep notifications in the application only. Do not add email notifications or device push notifications.

- Seryo is the eGovAI chatbot. Enable transport only after its API contract and procedures are supplied. Future answers must use approved knowledge or the current consenting user's temporary document, without retained chat history or analysis files.

- Do not add new features merely because a technology supports them. Every professor-facing proposal feature listed in Section 5 is P1, and the agreed exclusions remain excluded.

- When citing or implementing this plan, preserve the [CONFIRMED], [ASSUMPTION], and [OPEN] distinction and quote the relevant section number.

Suggested preamble for another AI model: You are helping build GoServe, a sandbox native Android application for TIP Manila's Casal and Arlegui campuses. Use this plan as the source of project facts. [CONFIRMED] is binding, [ASSUMPTION] is a visible demo default, and [OPEN] must not be guessed. Do not add features, institutions, official data, or integrations that are not stated here.

## 1. Project overview

### 1.1 Problem

Students can lose time visiting several offices to ask about availability, submit requests, follow up on documents, or arrange consultations. Office personnel also need one place to publish availability, receive complete submissions, and respond without exposing records to unrelated users. [CONFIRMED]

### 1.2 Solution

GoServe is a native Java/XML ConstraintLayout Android application for TIP Manila's Casal Campus and Arlegui Campus. The agreed complete demonstration includes office information, document requests and private evidence, optional office-declared fees, eGovPay or cash for paid requests, tracking, verified PDFs, inquiries, and guidance/faculty consultations. Student navigation is Home, Requests, Consult, Inbox, AI Chat, and More. AI Chat opens Seryo directly. Current delivery status is stated in Section 1.5. [CONFIRMED]

### 1.3 Demonstration boundary

The project demonstrates the proposed workflows with non-official data. It does not represent a production deployment, connect to an official student information system, or modify official registrar grade records. [CONFIRMED]

### 1.4 Out of scope

- TIP Quezon City, campuses other than Casal and Arlegui, and users from outside TIP Manila. [CONFIRMED]

- Changes to official registrar grade records or direct integration with an official grade database. [CONFIRMED]

- Public email and password registration, platform-admin screens, email notifications, and device push notifications. [CONFIRMED]

- Automatic refunds or payment voids for cancelled or rejected paid requests. [CONFIRMED]

- Persistent chatbot history, persistent uploaded-document history, typing indicators, and online presence. [CONFIRMED]

- Web browser portals and cross-platform mobile frameworks. [CONFIRMED]

Demo content rule: office services, fees, schedules, requirements, and chatbot knowledge remain non-official demo data until verified by authorized TIP personnel.

### 1.5 Current implementation status

As of October 15, 2026, the Android and Supabase foundation is implemented. Preview screens demonstrate visual structure and navigation only. A preview never authenticates a user, submits a request, books a consultation, confirms a payment, or fabricates an eGovAI answer. The complete P1 application remains under implementation. [CONFIRMED]

| Package | Current state | Remaining work and evidence boundary |
| --- | --- | --- |
| WP-1 Foundation | Implemented; final verification recorded below | Java/XML hosts, light/dark themes, domain states, responsive layouts, Supabase auth/session plumbing, role routing, consent, directory, settings, and bundled brand assets. Physical-device verification remains pending. |
| WP-2 Identity and RLS | Foundation deployed; setup and integration pending | Supabase schema, bootstrap-user, consent RPC, and foundation RLS exist. Configure Google OAuth and create assigned accounts; verify real Android sign-in and all private record/file policies. |
| WP-3 Offices | Directory implemented; publishing planned | Five labeled demo offices, search, selected filters, office detail, and Cashiering-only Payments routing. Live staff status/announcement writes and Realtime remain planned. |
| WP-4 Requests and files | UI preview; live flow planned | My Requests preview and status design do not submit or mutate requests. Implement private evidence, server status history, verified PDF delivery, free/paid branching, and retention. |
| WP-5 Payments | Server configuration prepared; disabled | eGovPay credential rotated and encrypted in Vault. Provider contract, authoritative checkout, authenticated callbacks, reconciliation, teller cash confirmation, audit, and simulator are still pending. |
| WP-6 Consultations | Planned | Implement schedules, atomic capacity, booking, cancellation, and rescheduling for guidance and CCS faculty/secretary. |
| WP-7 Inquiries and notifications | Planned | Implement authorized threads, assignments, messages, read state, and in-app Realtime notifications. |
| WP-8 Pickup | Planned | Implement hashed single-use token issuance/consumption, CameraX QR scan, manual entry, reuse denial, and completion audit. |
| WP-9 Seryo | UI and server configuration prepared; disabled | Direct AI Chat destination and consent/attachment panels exist. eGovAI access code/base URL are encrypted server-side with enabled=false. Procedures are blank; chat transport, uploads, and actions remain disabled. |
| WP-10 Integrated verification | Pending | Complete AT-01 through AT-38, role/RLS negative tests, provider contract tests, physical-device APK installation, browser-return/keyboard/inset checks, and the end-to-end demonstration. |

### 1.6 Verification and next delivery

The current source contains focused domain, authentication lifecycle, layout responsiveness, directory interaction, office/payment routing, navigation, typography, and native-render regression tests. The latest complete Android build, test, and lint gate is being rerun before its results are recorded here. Backend foundation checks and live transactional RLS checks have passed previously; they do not verify the planned integrated workflows. [OPEN latest gate]

Foundation checks and UI previews do not complete AT-01 through AT-38. The next delivery must establish real assigned-account/Google sign-in and finish live office, request/file, payment, consultation, inquiry, pickup, and Seryo contracts in dependency order. No physical Android device or emulator has yet verified the complete integrated workflow. [OPEN]

## 2. Confirmed P1 scope

Every professor-facing proposal feature is required for the demonstration. P1 means the feature must work in the integrated demo; it does not mean the feature is approved for institutional production. [CONFIRMED]

| ID | Capability | P1 demonstration contract |
| --- | --- | --- |
| P1-01 | Office availability and announcements | Staff publish a current office status, optional note, and announcements; students receive live in-app updates. |
| P1-02 | Document requests | Students submit requests and private evidence, track status, and receive verified PDFs. Apply a payment gate only when the office explicitly declares a fee; free requests skip payment. |
| P1-03 | Payments | Payment is optional and office-declared. Free requests skip payment; fee-based requests offer eGovPay or cash through Cashiering once trusted payment operations are implemented. |
| P1-04 | Guidance consultations | Students book available guidance slots subject to configured capacity, cancellation, and rescheduling rules. |
| P1-05 | Faculty consultations | Students book a CCS faculty member through schedules managed by the faculty member or authorized CCS secretary. |
| P1-06 | Inquiry threads | Students and assigned staff exchange asynchronous messages grouped by conversation and timestamp. |
| P1-07 | Physical pickup QR | A ready physical request receives a hashed, single-use pickup token with native Android camera scan and manual entry paths. |
| P1-08 | Seryo eGovAI assistance | The chatbot answers from approved knowledge or one current temporary file, with consent and no saved history. |
| P1-09 | Realtime notifications | Supabase Realtime updates in-app status, announcements, messages, appointments, and notifications. |
| P1-10 | Native Android access | The Java application installs from an APK and uses native layouts and widgets on Android phones. |

## 3. Users, roles, and demo offices

### 3.1 Authorization roles

| Role | Who | Access boundary |
| --- | --- | --- |
| student | Verified TIP student or pre-created demo student | Own profile, requests, payments, appointments, inquiry threads, files, and notifications; public office information. |
| staff | Office personnel, secretary, faculty member, or guidance counselor | Records assigned to the staff member's office or capability; permitted office content and schedules. |
| teller | Cashiering teller | Payment lookup and cash confirmation across configured demo offices; no authority to change unrelated request content. |

Staff capability identifies whether a staff account acts as office personnel, secretary, faculty, or guidance counselor. The role remains staff so authorization policies have one stable role vocabulary. [CONFIRMED]

### 3.2 Demo offices

| ID | Office | Demo coverage | Demonstrated workflows |
| --- | --- | --- | --- |
| OFF-REG | Registrar | Casal Campus / Arlegui Campus | Document requests, evidence review, verified deliverables, inquiries |
| OFF-ACC | Student Accounting | Casal Campus / Arlegui Campus | Accounting inquiries, fee-linked requests, announcements |
| OFF-CASH | Cashiering | Casal Campus / Arlegui Campus | Cash confirmation and payment inquiry demonstration |
| OFF-GCC | Guidance and Counseling Center | Casal Campus / Arlegui Campus | Guidance consultations and inquiry threads |
| DEP-CCS | College of Computer Studies secretary | Casal Campus / Arlegui Campus | Faculty consultation coordination, requests, and inquiries |

The office list supports the agreed demonstration only. It does not claim that each service, schedule, or fee is officially offered at both campuses. [ASSUMPTION]

### 3.3 Office availability

| Status | Meaning |
| --- | --- |
| Open | Office is accepting the demonstrated service or inquiry. |
| On Lunch Break | Office is temporarily unavailable during a manually indicated break. |
| Cut-Off Reached | Office has reached the current service or queue cut-off. |
| Temporarily Closed | Office is unavailable until staff update the status. |

Authorized staff update office status manually. Each update may include a note and must record the actor and timestamp. [CONFIRMED]

## 4. Payment methods and controls

### 4.1 Shared payment contract

- Only an office-declared request fee greater than zero creates a payment step. A free request proceeds without checkout or a payment record; a fee-based request offers egovpay or cash. [CONFIRMED]

- The authorized office declares an optional fee in server-controlled request-type configuration. No office or service is assumed to charge a fee. The Android client cannot supply or change the amount. [CONFIRMED]

- Payment status is stored separately from request progress using pending, paid, failed, expired, or cancelled. [CONFIRMED]

- A payment record and its paymentEvents remain auditable even when the linked request later changes status. [CONFIRMED]

- An authorized office must explicitly declare and label a test fee before demonstrating a paid request. Keep free requests available and bypass payment for them. [CONFIRMED]

### 4.2 eGovPay flow

| Step | User or provider event | Trusted system action |
| --- | --- | --- |
| 1 | Student selects eGovPay only when the owning office declares a fee greater than zero. | The Android client sends only the request identifier over HTTPS to a trusted Edge Function. |
| 2 | Edge Function loads the configured fee. | Server creates the transaction and HMAC signature using secrets unavailable to the Android client. |
| 3 | Student completes the sandbox flow. | Provider transport remains disabled until checkout, signature, callback, status, and amount contracts are verified. A future simulator must be explicitly labeled and isolated from real payment authority. |
| 4 | Provider sends a callback. | Callback is authenticated, stored idempotently in paymentEvents, and cannot be applied twice. |
| 5 | Server reconciles the transaction. | Provider transaction status is checked before the payment becomes paid. |
| 6 | Request continues. | A confirmed paid record allows the request to enter or continue Processing. |

Authoritative payment rule: the eGovPay callback and server-side reconciliation determine payment status. Returning to the Android app or tapping a button cannot mark a payment paid.

### 4.3 Cash flow

| Step | Cash demonstration behavior |
| --- | --- |
| 1 | For an office-declared fee, the student selects cash and receives a reference and server-loaded amount due; a free request skips this flow. |
| 2 | Student pays through the demonstrated cashier process outside the Android application. |
| 3 | An authenticated teller searches the reference and confirms the exact pending amount. |
| 4 | The Edge Function records actor and time, rejects duplicate confirmation, marks payment paid, and advances eligible work. |

### 4.4 Cancellation, rejection, and provider limits

Cancellation or rejection requires a reason. If the request has a paid record, the payment remains paid and the request enters manual office follow-up. The demonstration does not promise an automatic refund or void. [CONFIRMED]

The supplied eGovPay base URL, merchant credential, and settlement template are configured on the server. The replacement credential was rotated successfully and is encrypted in Supabase Vault; no credential appears in the APK or this plan. The adapter remains disabled because callback verification, provider status mapping, signatures, and amount canonicalization are unconfirmed. Hosted checkout test controls are user-reported and remain unverified. [CONFIRMED configuration; OPEN provider contract]

## 5. Functional requirements

All requirements in this section are P1 for the sandbox demonstration. [CONFIRMED]

| ID | Requirement | Rule |
| --- | --- | --- |
| FR-01 | TIP Google sign-in | Supabase Google OAuth with PKCE accepts a verified @tip.edu.ph Google identity. The trusted bootstrap function provisions a student profile; Google provider configuration and real sign-in verification remain pending. |
| FR-02 | Demo sign-in | Supabase password sign-in accepts only pre-created assigned/demo accounts with server-provisioned profiles. The app exposes no public password registration path; account provisioning remains pending. |
| FR-03 | Role authorization | student, staff, and teller routes and records are enforced through verified Supabase sessions, trusted bootstrap, server checks, and auth.uid()-based RLS. |
| FR-04 | Office directory | Students browse the five demo offices and see campus-labeled demo services, availability, notes, and announcements. |
| FR-05 | Office status | Authorized staff select one exact office status and may add a note. The system records actor and time. |
| FR-06 | Announcements | Authorized staff publish office announcements. Relevant students receive in-app Realtime updates. |
| FR-07 | Document request | A student selects a configured request type, enters notes, uploads valid private evidence, and receives a reference number. |
| FR-08 | Request tracker | Students and authorized staff see request progress and history using the exact Section 6 statuses. |
| FR-09 | Payment choice | A free request skips payment. For a fee explicitly declared by the office, the student selects egovpay or cash and the server loads the authoritative amount. Payments navigation lists Cashiering only (OFF-CASH). |
| FR-10 | eGovPay transaction | An Edge Function creates the transaction and signature, processes idempotent callbacks, and reconciles provider status. |
| FR-11 | Cash confirmation | Only teller accounts can confirm a pending cash payment. A second confirmation is rejected. |
| FR-12 | Verified PDF delivery | Authorized staff upload a private PDF. The owning student downloads it only when request and payment conditions allow. |
| FR-13 | Inquiry threads | Students send asynchronous messages to an office or assigned recipient. The app stores conversation, sender, assignment, and time. |
| FR-14 | Guidance consultation | Students book, cancel, or reschedule guidance slots without exceeding capacity. |
| FR-15 | Faculty consultation | Students book a selected CCS faculty member. Authorized faculty or the CCS secretary manages the schedule. |
| FR-16 | Pickup token | A physical request that becomes ready receives a hashed, single-use pickup token for CameraX-based QR scan or manual entry. |
| FR-17 | Seryo grounded answer | A future Edge Function sends approved context to eGovAI for Seryo and labels the response as guidance. The API and Seryo procedures must be supplied and validated before any provider call or action is enabled. |
| FR-18 | Temporary analysis | A consenting user submits one PDF, JPG, or PNG up to 10 MB. The system deletes the private temporary file immediately after analysis. |
| FR-19 | In-app notifications | Supabase Realtime updates the notification list and affected screens. The project sends no email or device push. |
| FR-20 | Retention | A cleanup function deletes request evidence and deliverables 30 days after the request enters a terminal status. |
| FR-21 | Cancellation and rejection | Authorized actors provide a reason. Paid records remain intact and require manual office follow-up. |
| FR-22 | Native Android access | The APK runs on Android phones with native layouts and widgets; manual token entry remains available when camera permission or access is unavailable. |

## 6. State and lifecycle contracts

### 6.1 Request statuses

| Status | Meaning | Set by |
| --- | --- | --- |
| Submitted | The student created the request and the system assigned a reference number. | System after validated submission |
| Awaiting Payment | A configured fee is due and no paid payment record exists. | System after payment method selection |
| Processing | The office is working on the request after authoritative confirmation of an applicable fee, or after bypassing payment for a free request. | System or authorized office staff |
| Ready for Pickup/Download | The office prepared a physical result or verified digital deliverable. | Authorized office staff |
| Completed | The student received the physical result or met the digital completion rule. | Authorized office staff or controlled completion action |
| Cancelled | The request stopped with a required cancellation reason. | Student where allowed or authorized staff |
| Rejected | The office declined the request with a required reason. | Authorized office staff |

Completed, Cancelled, and Rejected are terminal request statuses for retention timing. Every transition stores the previous status, new status, actor, timestamp, and required reason when applicable. [CONFIRMED]

### 6.2 Payment statuses

| Status | Meaning |
| --- | --- |
| pending | A payment path exists but authoritative confirmation has not arrived. |
| paid | A teller confirmation or reconciled provider transaction proves payment for the stored amount. |
| failed | The provider reports a failed attempt or trusted processing cannot complete it. |
| expired | The provider or simulator validity period ended without payment. |
| cancelled | The payment attempt was cancelled without changing any separate paid record. |

Request progress and payment status are separate state machines. A request cannot infer paid status from its own progress, and a payment cannot silently rewrite a terminal request.

### 6.3 Pickup token lifecycle

| Stage | Contract |
| --- | --- |
| Issued | The server creates an opaque token, stores only its hash, links it to one ready physical request, and sets its validity. |
| Presented | Authorized staff scan the QR with CameraX and a QR decoder after Android camera permission is granted, or enter the token manually. |
| Consumed | The server matches the hash, checks request and office authority, records the actor and time, and completes the pickup once. |
| Rejected | The server rejects an unknown, expired, already consumed, or unauthorized token without completing the request. |

## 7. Consultations and inquiry threads

### 7.1 Guidance and faculty consultations

| Type | Owning office | Schedule manager | Capacity basis |
| --- | --- | --- | --- |
| Guidance | Guidance and Counseling Center | Guidance counselor | Configured slot start, duration, and capacity |
| Faculty | College of Computer Studies secretary | Selected CCS faculty member or authorized CCS secretary | Faculty-specific availability and configured capacity |

- The server rejects a booking when the active booking count reaches the configured slot capacity. [CONFIRMED]

- The student may cancel or reschedule within the demonstration rules. Each action records actor and time and generates an in-app notification. [CONFIRMED]

- A reschedule checks the target slot before releasing the original booking so capacity cannot be exceeded. [CONFIRMED]

- Schedules and capacity values remain labeled demo data until authorized TIP personnel verify them. [CONFIRMED]

### 7.2 Asynchronous inquiry threads

An inquiryThread groups messages for one student, one destination office, and an optional assigned recipient. Each message stores the sender and timestamp. Staff assignment can route a thread to office personnel, the guidance counselor, the CCS secretary, or a CCS faculty member. [CONFIRMED]

- Students read only their own conversations.

- Staff read only threads assigned to them or their authorized office capability.

- Messages are asynchronous. The demonstration excludes typing indicators and presence.

- Realtime subscriptions update authorized thread views and create in-app notifications without widening database access.

## 8. Technology and architecture

### 8.1 Responsibility map

| Layer | Technology | Responsibility |
| --- | --- | --- |
| Android client | Java and Android SDK | Java 17 and XML ConstraintLayout with AndroidX fragments, ViewModel/LiveData, and view binding; minimum API 26, compile/target API 35. Native role navigation and lifecycle handling. Provider and service secrets never enter the APK. |
| Build and distribution | Android Studio and Gradle | Android Studio and Gradle build the Java/XML application into an installable APK. Supabase is the sole authentication and application backend; no Firebase dependency is required. |
| Client networking | Java HTTPS client and Supabase Realtime | Runs HTTPS requests off the UI thread, refreshes Supabase sessions, and sends current access tokens to protected APIs. Realtime subscriptions remain planned and must follow the same RLS boundaries. |
| Identity provider | Supabase Authentication | Supabase Google OAuth with PKCE for verified @tip.edu.ph Google users and password sign-in for server-provisioned assigned/demo accounts only. |
| Structured data | Supabase PostgreSQL | Stores all application records, relationships, histories, and audit fields. |
| Authorization | Supabase RLS with Supabase JWTs | Verifies Supabase sessions and uses auth.uid(), server-managed profiles, and RLS to enforce user, office, and capability boundaries. |
| File storage | Supabase Storage | Stores public-read office/service assets and private profile, evidence, deliverable, and temporary analysis files. |
| Live updates | Supabase Realtime | Publishes authorized database changes for in-app notifications, status, announcements, appointments, and messages. |
| Trusted operations | Supabase Edge Functions | Bootstraps users, authorizes operations, calculates charges, handles provider calls, serves eGovAI, and runs cleanup. |
| Online payment | eGovPay API | Targets the supplied eGovPay environment. Credentials are encrypted on the server; transaction/callback/reconciliation transport is disabled until the provider contract is confirmed. |
| AI provider | eGovAI | eGovAI provides Seryo, the chatbot. Server configuration exists, but transport and actions stay disabled until API request/response rules and Seryo procedures are supplied. |

### 8.2 Authentication and authorization flow

| Step | Flow |
| --- | --- |
| 1 | Supabase Auth authenticates Google through OAuth with PKCE or a pre-created assigned account through password sign-in and returns a Supabase session. |
| 2 | The Android client refreshes its Supabase session before protected HTTPS calls and presents the access token to the Data API, Storage, and Edge Functions. Refresh tokens are protected with Android Keystore. |
| 3 | bootstrap-user verifies the exact token through Supabase Auth getUser, provisions only a verified TIP Google student, or loads the server-provisioned assigned profile. Client metadata never grants a role. |
| 4 | PostgreSQL and Storage RLS evaluate the Supabase user UUID, application profile, office assignment, and capability for every protected operation. |
| 5 | Realtime sends only changes the same authenticated user may read under the configured policies. |

### 8.3 Edge Function contracts

| Function | Trusted responsibility |
| --- | --- |
| bootstrap-user | Verify Supabase identity, enforce Google/password account rules, provision verified TIP students, and return the authoritative profile, role, office, capability, and consent state. Foundation source is deployed. |
| create-egovpay-transaction | Load the authoritative fee, create a transaction, generate the HMAC signature, and store the pending attempt. |
| egovpay-callback | Authenticate and store each callback idempotently, then trigger or record reconciliation. |
| reconcile-egovpay | Query the provider transaction endpoint and map the approved provider status to the internal payment status. |
| confirm-cash-payment | Require teller role, verify a pending cash amount, reject duplicates, and record actor and time. |
| ask-seryo (planned) | Planned Seryo/eGovAI gateway: verify the user, approved procedures, grounding, consent, file rules, rate limits, and failure cleanup. Credentials stay in server-only Vault configuration; transport remains disabled. |
| cleanup-files | Delete temporary analysis files immediately and eligible request files 30 days after terminal status. |

Technical references: Supabase Google OAuth, https://supabase.com/docs/guides/auth/social-login/auth-google; Supabase Auth and RLS, https://supabase.com/docs/guides/database/postgres/row-level-security; eGovPay API catalog, https://platforms.e.gov.ph/api-catalogs/egovpay. Provider procedures and payloads remain open until supplied and verified.

Android references: Android network operations, https://developer.android.com/develop/connectivity/network-ops/connecting; CameraX architecture, https://developer.android.com/media/camera/camerax/architecture. Google sign-in uses Supabase OAuth rather than a Firebase SDK.

## 9. Data model, files, and privacy

### 9.1 Structured records

| Table | Key fields |
| --- | --- |
| profiles (foundation); users (planned extension) | Foundation: id (Supabase Auth UUID), email, display_name, role, staff_capability, office_id, is_demo, consent_accepted_at. Future record extensions retain the same ownership boundaries. |
| offices | id, name, campusLabel, status, statusNote, statusBy, statusAt, active |
| announcements | id, officeId, title, body, publishedBy, publishedAt, expiresAt |
| requestTypes | id, officeId, name, optional office-declared fee (null/zero means free), deliveryMode, requirementsText, active, isDemo; trusted server configuration records fee authority. |
| requests | id, referenceNo, studentId, officeId, requestTypeId, status, notes, terminalAt, createdAt, updatedAt |
| requestStatusEvents | id, requestId, fromStatus, toStatus, reason, actorId, createdAt |
| payments | id, requestId, method, amount, status, providerReference, confirmedBy, confirmedAt, createdAt |
| paymentEvents | id, paymentId, providerEventId, eventType, payloadHash, status, receivedAt, reconciledAt |
| files | id, ownerId, requestId, bucket, objectPath, kind, mimeType, sizeBytes, expiresAt, createdAt |
| appointments | id, studentId, officeId, staffId, slotStart, slotEnd, status, createdAt, updatedAt |
| slotSettings | id, officeId, staffId, slotMinutes, capacity, availabilityData, active |
| inquiryThreads | id, studentId, officeId, assignedTo, subject, status, createdAt, updatedAt |
| messages | id, threadId, senderId, body, createdAt |
| pickupTokens | id, requestId, tokenHash, expiresAt, consumedAt, consumedBy |
| notifications | id, recipientId, type, title, body, relatedId, readAt, createdAt |
| knowledgeArticles | id, officeId, title, content, approved, approvedBy, updatedAt |
| auditLogs | id, actorId, action, targetType, targetId, metadata, createdAt |
| appConfig | demoMode, allowedEmailDomain, paymentSimulatorEnabled, providerConfigurationState; server-only eGovPay/eGovAI settings include enabled=false until contracts are verified. Seryo procedures are not populated. |

Field names are implementation contracts for the plan and may be normalized during schema design without changing the stated data ownership, status values, or security rules. [ASSUMPTION]

### 9.2 Storage classes

| Bucket | Visibility | Content | Access |
| --- | --- | --- | --- |
| office-assets | Public read | Office and service images approved for the demo | Staff write; public read |
| profile-images | Private | User profile images | Owner read/write with authorized staff read only where required |
| request-files | Private | Student evidence and staff PDF deliverables | Owning student and authorized office staff under RLS |
| temporary-ai | Private | One current PDF, JPG, or PNG for analysis | Requesting user and Edge Function only |

### 9.3 File validation and retention

- Request evidence follows configured type and size rules before upload metadata is accepted. [CONFIRMED]

- Verified deliverables use PDF and remain private. A student must own the request and meet payment and request-status gates before download. [CONFIRMED]

- Temporary analysis accepts one PDF, JPG, or PNG up to 10 MB after the user accepts the non-sensitive-data consent notice. [CONFIRMED]

- The eGovAI flow deletes the temporary object immediately after analysis, including handled provider failures. [CONFIRMED]

- The cleanup function deletes request evidence and deliverables 30 days after Completed, Cancelled, or Rejected, and records the cleanup result. [CONFIRMED]

### 9.4 Privacy controls

The demonstration collects only the records needed for the agreed workflows. RLS blocks cross-student and cross-office access. Secrets stay in server-side configuration. Audit records identify sensitive actions without storing provider secrets, document contents, or eGovAI prompts and responses. [CONFIRMED]

The team must review the demonstration against applicable privacy requirements and TIP requirements before any real deployment. [ASSUMPTION]

## 10. Screen inventory

| Audience | Screen | Contents |
| --- | --- | --- |
| Shared | Sign in | Google sign-in, pre-created demo email/password sign-in, blocked public registration, error states |
| Student | Consent and profile | Privacy consent, non-sensitive AI notice, private profile image |
| Student | Office directory | Campus labels, office status, note, announcements, demo services |
| Student | Office detail | Request, inquiry, consultation, and current office information |
| Student | New request | Request type, notes, evidence upload, delivery mode, configured fee, payment choice |
| Student | Payment | eGovPay handoff and authoritative status refresh on app return, or cash instructions; separate payment status and retry where allowed |
| Student | My requests | Reference, request status history, payment status, verified download, pickup QR |
| Student | Consultations | Guidance and faculty availability, booking, cancellation, rescheduling |
| Student | Inquiry threads | Conversation list, assigned destination, asynchronous messages |
| Student | Notifications | Realtime in-app events with read state |
| Staff | Office dashboard | Office status, note, announcements, queue totals, appointments, inquiries |
| Staff | Request queue | Office-scoped filters and request details |
| Staff | Request detail | Evidence review, status action, required reason, verified PDF upload, pickup action |
| Staff | Pickup scanner | CameraX QR scan after camera permission, manual token fallback, used-token rejection |
| Guidance | Guidance schedule | Slot configuration, capacity, bookings, cancellation and reschedule events |
| Faculty | Faculty schedule | Own availability, bookings, inquiry assignment |
| Secretary | CCS coordination | Faculty schedules and assigned CCS workflows within authorization |
| Teller | Cash payments | Reference search, authoritative amount, pending state, confirmation, duplicate rejection |
| Demo | Payment simulator | Clearly labeled provider simulator with the same internal statuses and callbacks |
| Shared | Seryo chatbot | Approved knowledge questions, temporary file consent and upload, guidance label, refusal and failure states |

### 10.1 Navigation and current UI delivery

Student tabs are Home, Requests, Consult, Inbox, AI Chat, and More. AI Chat opens Seryo directly; it is not hidden in More. Staff and teller routes retain role-scoped destinations. Directory Payments includes only Cashiering (OFF-CASH), regardless of another office's stored category. Office detail may route a user to Cashiering when a declared fee applies; routing does not create or confirm a payment. [CONFIRMED]

My Requests and Seryo chat, consent, and attachment panels are UI previews with honest disabled states. Seryo is a chatbot powered by eGovAI. Its supplied access code and base URL are encrypted server-side; enabled=false and blank procedures keep provider transport, uploads, and actions disabled until their contracts are issued. [CONFIRMED]

### 10.2 Typography brand and responsive layout

The user-approved Arvo/Lato pairing replaces the original Figma Archivo/Roboto typography. Bundle actual Arvo Bold 700 for primary headlines and headings, Lato Regular 400 for body/UI copy, and Lato Semi-Bold 600 for labels, captions, buttons, and navigation. Android sizes/line heights are headline 30/38sp, section and toolbar 20/28sp, office/card heading 16/24sp, body 16/24sp, supporting copy 14/20sp, label 14/20sp, and caption/navigation 12/16sp. [CONFIRMED]

Use the supplied GoServe square logo at 56dp on sign-in, 112dp in authentic loading states, and 40dp in the Home masthead; preserve aspect ratio and theme contrast. Loading reflects real authentication work and never uses fabricated percentages or delays. Seryo uses the supplied chatbot mark where appropriate. [CONFIRMED]

The monochrome light/dark Figma palette remains the design basis. Use sentence case, natural wrapping, real system/keyboard insets, minimum 48dp touch areas, 24dp auth margins, 20dp main margins, and a centered maximum content width of 600dp. Reflow at 320/360/390/412/480dp and font scales 1.0/1.3/2.0; use one office column at compact widths or large text. Text and final list content must remain reachable above bottom navigation. [CONFIRMED]

## 11. Demonstration configuration

### 11.1 Pre-created accounts

| # | Role | Demo identity | Suggested email | Assignment |
| --- | --- | --- | --- | --- |
| 1 | student | Student A | student.a@goserve.demo | Own records only |
| 2 | student | Student B | student.b@goserve.demo | Own records only; used for cross-student denial |
| 3 | staff | Registrar staff | registrar.staff@goserve.demo | Registrar |
| 4 | staff | Student Accounting staff | accounting.staff@goserve.demo | Student Accounting |
| 5 | teller | Cashiering teller | cashier.teller@goserve.demo | Cashiering payment confirmation |
| 6 | staff | Guidance counselor | guidance.staff@goserve.demo | Guidance capability |
| 7 | staff | CCS secretary | ccs.secretary@goserve.demo | Secretary capability |
| 8 | staff | CCS faculty | ccs.faculty@goserve.demo | Faculty capability |

These are proposed non-production identifiers, not created or verified accounts. Trusted administration must provision Supabase Auth users and matching profiles before real sign-in testing. Passwords, access codes, merchant tokens, and real student information do not appear in this plan. [ASSUMPTION]

### 11.2 Seed-data rules

- Seed only the five demo offices listed in Section 3. [CONFIRMED]

- Label every sample service, requirement, fee, processing estimate, office schedule, and knowledge article as non-official demo data. [CONFIRMED]

- Configure a free request to demonstrate skipping payment. Add a paid example only after an authorized office declares an optional, clearly labeled test fee; do not assume every office charges fees. [CONFIRMED]

- For an explicitly fee-based example, set the office-declared test fee in server-controlled requestTypes data before demonstrating eGovPay or cash through Cashiering. [CONFIRMED]

- Do not enable provider transport merely because credentials exist. Keep all future simulator behavior visibly labeled and separate from real payment authority. [CONFIRMED]

- Create appointment availability for the guidance counselor and CCS faculty member without presenting those times as official schedules. [CONFIRMED]

## 12. Acceptance test plan

| ID | Scenario | Expected result |
| --- | --- | --- |
| AT-01 | Verified TIP Google user signs in for the first time. | Supabase Google identity is verified and the trusted bootstrap function provisions role student. |
| AT-02 | Pre-created demo account signs in with email/password. | The matching demo profile and authorization load. |
| AT-03 | Unknown user attempts email/password registration. | No public signup path exists and account creation is blocked. |
| AT-04 | Personal Google account signs in. | Provisioning is denied and protected data remains unreadable. |
| AT-05 | Each student, office staff, faculty, guidance, secretary, and teller account opens its allowed screens. | Each account sees only its authorized routes and actions. |
| AT-06 | Student A requests Student B's record or private file. | Database and Storage RLS deny access. |
| AT-07 | Registrar staff requests Student Accounting records. | Cross-office RLS denies access. |
| AT-08 | Staff changes office status and publishes an announcement. | Actor/time are stored and authorized students receive Realtime updates. |
| AT-09 | Student opens an inquiry and staff replies. | Messages remain in one authorized thread with sender, assignment, and timestamp. |
| AT-10 | User attempts presence or typing updates. | No such feature or persistent field exists. |
| AT-11 | Student uploads valid private evidence. | Validated file metadata is stored and only authorized users can download it. |
| AT-12 | Student uploads a disallowed type or oversized request file. | Upload is rejected with a clear validation message. |
| AT-13 | Staff uploads a verified PDF for a paid request that is ready. | The owning student receives a private signed download path. |
| AT-14 | Student requests a paid deliverable before payment confirmation. | Download is denied. |
| AT-15 | A request remains terminal for 30 days. | Cleanup deletes eligible evidence and deliverables and records the result. |
| AT-16 | Student starts eGovPay checkout after a test fee is configured. | The Edge Function charges the exact server-loaded amount. |
| AT-17 | Android client submits a different amount. | The server ignores or rejects it and uses the configured fee. |
| AT-18 | The same eGovPay callback arrives twice. | paymentEvents records idempotency and payment is applied once. |
| AT-19 | Android app returns from eGovPay without an authoritative paid result. | Payment remains pending until callback and reconciliation confirm it. |
| AT-20 | Provider reports failed, expired, or cancelled. | Internal payment status maps correctly without false request completion. |
| AT-21 | Labeled simulator runs each supported payment outcome. | Internal events and visible results match the sandbox contract. |
| AT-22 | Teller confirms a pending cash payment. | Payment becomes paid with actor/time and eligible request work continues. |
| AT-23 | Student or staff account attempts cash confirmation. | Authorization denies the operation. |
| AT-24 | Teller confirms an already paid cash record. | Duplicate confirmation is rejected without a second event. |
| AT-25 | Authorized user cancels or rejects a request without a reason. | Transition is rejected. |
| AT-26 | A paid request is cancelled or rejected with a reason. | Paid record remains and the case is flagged for manual office follow-up. |
| AT-27 | Guidance or faculty slot reaches capacity. | Another booking is rejected. |
| AT-28 | Student cancels and reschedules a consultation. | Capacity remains correct and authorized parties receive in-app updates. |
| AT-29 | Staff scans a valid unused pickup QR. | Token is consumed once and the request completes. |
| AT-30 | Staff scans or enters the same pickup token again. | The reused token is rejected. |
| AT-31 | Student asks a question covered by an approved knowledgeArticle. | eGovAI answers from that content and labels the answer as guidance. |
| AT-32 | Student asks an unsupported institutional question. | The assistant refuses to invent an answer and points to the relevant office. |
| AT-33 | User submits one valid temporary PDF, JPG, or PNG at or below 10 MB after consent. | Analysis runs without storing chat history and the file is deleted immediately. |
| AT-34 | User skips consent, submits multiple files, wrong type, or more than 10 MB. | Analysis is blocked before the provider call. |
| AT-35 | User exceeds the configured eGovAI request limit. | The Edge Function rate-limits the request. |
| AT-36 | eGovAI provider fails. | The app shows a safe error, saves no response history, and deletes any temporary file. |
| AT-37 | A tester installs the generated APK on an Android phone and signs in. | The native app launches, shows the account's authorized screens, and accesses the existing backend. |
| AT-38 | Staff denies Android camera permission and opens pickup completion. | Manual token entry remains available and uses the same server validation. |

## 13. Dependency-ordered work plan

The deadline and team allocation are not confirmed, so the schedule uses dependencies instead of dates. Every package is P1 because each proposal feature must work in the demonstration. [CONFIRMED]

| WP | Package | Includes | Depends on |
| --- | --- | --- | --- |
| WP-1 | Foundation | Android Studio Java project, Android SDK setup, APK build, Supabase project, Supabase Auth configuration, configuration boundaries | None |
| WP-2 | Identity and RLS | Supabase sign-in, bootstrap function, roles/capabilities, demo accounts, PostgreSQL and Storage policies | WP-1 |
| WP-3 | Offices and content | Demo offices, office status, announcements, request types, demo-data labels, Realtime subscriptions | WP-2 |
| WP-4 | Requests and files | Request creation, evidence validation, status history, staff queue, verified PDF delivery, retention metadata | WP-2, WP-3 |
| WP-5 | Payments | Server fee lookup, eGovPay transaction/callback/reconciliation, simulator, teller cash confirmation, paymentEvents | WP-2, WP-4 |
| WP-6 | Consultations | Guidance and faculty availability, capacity, booking, cancellation, rescheduling | WP-2, WP-3 |
| WP-7 | Inquiries and notifications | Inquiry threads, assignments, messages, in-app Realtime notification list | WP-2, WP-3 |
| WP-8 | Pickup completion | Hashed single-use token, QR presentation, CameraX scan and permission handling, manual entry, reuse rejection | WP-4 |
| WP-9 | Seryo chatbot | Approved knowledge, consent, temporary file analysis, refusal, limits, provider failure, immediate deletion | WP-2, WP-3, WP-4 |
| WP-10 | Integrated verification | Acceptance tests AT-01 through AT-38, privacy checks, Android phone APK and activity lifecycle QA, demo script, failure-state rehearsal | WP-1 through WP-9 |

Scope warning: all proposal features remain P1. If delivery capacity becomes insufficient, the team must renegotiate scope with the professor instead of silently removing a feature.

## 14. Risks and controls

| ID | Risk | Impact | Control |
| --- | --- | --- | --- |
| R-01 | All proposal features are P1 | Integration may exceed available time. | Build in dependency order, demonstrate thin complete flows early, and raise scope risk with the professor. |
| R-02 | eGovPay provider contract remains unconfirmed | Configured credentials alone do not make payment transport safe or operational. | Keep the adapter disabled until payload, signature, callback, status, and amount contracts pass tests. A future simulator must be clearly labeled and cannot confirm real payments. |
| R-03 | Provider callback details differ from assumptions | Payment mapping or verification may fail. | Keep schema and status mapping [OPEN], validate signatures, store idempotent events, and reconcile server-side. |
| R-04 | Android app return is mistaken for payment proof | An unpaid request could appear paid. | Allow only authenticated callback and reconciliation logic to set paid. |
| R-05 | Fee or service data looks official | The demonstration could misrepresent TIP services. | Label seed records as non-official demo data and require authorized verification before production use. |
| R-06 | Supabase identity and profile mapping is incorrect | RLS could deny valid users or expose records. | Test bootstrap claims, every role, cross-student denial, and cross-office denial before feature demos. |
| R-07 | Private files use public paths | Evidence or deliverables could be exposed. | Use private buckets, RLS, short-lived access, and negative download tests. |
| R-08 | Retention job does not run | Files remain past the stated period. | Log cleanup runs, test eligible dates, and expose failures to the project team. |
| R-09 | Payment and request states become coupled | Cancellation or provider failure could corrupt progress history. | Store separate state machines and connect them through audited server actions. |
| R-10 | Pickup token is copied or reused | The wrong person could complete a pickup. | Store only a hash, bind the token to one request and office, set validity, and reject reuse. |
| R-11 | Consultation capacity changes concurrently | A slot could be overbooked. | Use a transaction or server-side atomic capacity check. |
| R-12 | eGovAI invents institutional answers | Students may treat guidance as official. | Ground only on approved records or the current file, refuse unsupported questions, and label every answer as guidance. |
| R-13 | Sensitive content is sent for analysis | The free demonstration could process unsuitable data. | Show explicit non-sensitive-data consent, validate the file, keep no chat history, and delete the file immediately. |
| R-14 | Realtime subscription exposes another office | Live updates could bypass intended isolation. | Apply the same Supabase-session and profile-based RLS to Realtime reads and test unauthorized subscriptions. |
| R-15 | Android camera permission is denied or unavailable | Staff cannot scan the pickup QR. | Provide manual token entry with the same server validation. |

## 15. Open questions register

These items remain unresolved. Do not answer them on the project owner's behalf. [OPEN]

| ID | Question | Needed for |
| --- | --- | --- |
| Q-01 | What is the final deadline, and how many team members will implement the project? | Schedule and assignment |
| Q-02 | Which official demo services, fees, evidence requirements, processing notes, delivery modes, and office schedules may the team use? | Seed data and presentation accuracy |
| Q-03 | Base URL, rotated credential, and settlement template are configured. What are the exact checkout payload, HMAC rules, callback authentication/schema, status mapping, reconciliation endpoint, and canonical amount representation? | Payment adapter |
| Q-04 | Does the approved eGovPay sandbox expose the described provider-side already-paid demonstration control, and what result does it send? | Demo script; capability must not be promised before testing |
| Q-05 | eGovAI base URL and access code are configured server-side. What are the approved request/response contract, agent/task identifiers, Seryo procedures, limits, and permitted temporary-file capabilities? | Rate limits and provider configuration |
| Q-06 | Which office personnel may approve knowledgeArticles and verify the final demo content? | Chatbot grounding and content governance |
| Q-07 | What submission artifacts does the subject require in addition to the APK, editable plan, and PDF plan; and which physical Android devices will be used for final verification? | Final project package |

## 16. Glossary

| Term | Meaning in this project |
| --- | --- |
| GoServe | GoServe: A Native Android Application for Campus Operations, Services, and Consultations, the sandbox native Android application described in this plan. |
| Supabase Authentication | The sole identity provider in GoServe; it handles verified TIP Google OAuth with PKCE and pre-created assigned/demo password accounts. |
| Supabase session authentication | A Supabase access token binds protected Database, Storage, Realtime, and Edge Function operations to the authenticated user and server-managed profile. |
| RLS | Row Level Security policies that enforce which authenticated user may read or change each record. |
| Supabase PostgreSQL | The database for all structured application records. |
| Supabase Storage | Object storage for public office assets and private user, request, deliverable, and temporary AI files. |
| Supabase Realtime | Live delivery of authorized database changes to the in-app interface. |
| Supabase Edge Functions | Trusted server code for identity bootstrap, payment operations, eGovAI requests, and cleanup. |
| eGovPay | The payment API targeted for the approved sandbox demonstration. |
| Payment simulator | A clearly labeled fallback that reproduces the app's internal payment outcomes when official sandbox access is unavailable. |
| HMAC signature | A server-generated message authentication code used by the payment integration; its secret never reaches the Android client. |
| Idempotent callback | A provider event that the server can receive more than once without applying the payment more than once. |
| Reconciliation | A server-side status check against the provider transaction endpoint before or after applying an event. |
| Teller | The role allowed to confirm a pending cash payment. |
| Request progress | The separate request state: Submitted, Awaiting Payment, Processing, Ready for Pickup/Download, Completed, Cancelled, or Rejected. |
| Payment status | The separate payment state: pending, paid, failed, expired, or cancelled. |
| Terminal request status | Completed, Cancelled, or Rejected; the 30-day request-file retention period starts when the request enters one of these statuses. |
| Pickup token | An opaque, hashed, single-use value that staff validate for one physical pickup. |
| knowledgeArticles | Approved records that supply institutional demo knowledge to the Seryo chatbot. |
| Temporary analysis | One consenting user's PDF, JPG, or PNG, up to 10 MB, processed for the current answer and deleted immediately. |
| Grounded answer | A Seryo response from eGovAI, limited to approved knowledgeArticles or the current consenting user's temporary document and labeled as guidance. |
| Non-official demo data | Sample services, fees, schedules, requirements, and knowledge that TIP personnel have not verified for production. |
