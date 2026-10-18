# GoServe v2.3 local API contract

This is the implemented source contract, not a deployment claim. Apply the foundation once, then migrations in filename order. Existing offices, profile IDs, role values and bootstrap fields remain authoritative. No services, fees, schedules, legal text or policies are seeded.

## Transport and errors

Call PostgreSQL functions using authenticated `POST /rest/v1/rpc/<function>` with the exact named arguments below. Responses are the listed JSON value, without a generic `Result` wrapper. Expected SQL domain failures use SQLSTATE `P0001` and a stable uppercase `message`. PostgREST returns `{code,message,details,hint}`; native code must map `message` to the domain code and must not display SQL details. Invalid scalar casts/constraints (`22P02`, `23502`, `23514`) map to `VALIDATION_FAILED`; uniqueness (`23505`) maps to conflict; deadlock/serialization failures (`40P01`, `40001`) are retryable with the same operation key. No failed SQL operation commits partial changes.

Edge failures are `{ok:false,error:{code}}`, with HTTP 401 for authentication, 403 forbidden, 404 unavailable record, 422 invalid input/file, 409 state conflict, and 503 missing configuration/provider or temporary failure. Success is the endpoint's documented object. All Edge replies use `Cache-Control: no-store`.

Codes: `AUTH_REQUIRED`, `AUTH_INVALID`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_FAILED`, `PROFILE_INCOMPLETE`, `TERMS_REQUIRED`, `CONFIGURATION_REQUIRED`, `STALE_VERSION`, `QUOTE_STALE`, `IDEMPOTENCY_CONFLICT`, `INVALID_STATE`, `PAYMENT_REQUIRED`, `ALREADY_PAID`, `CODE_INVALID`, `CODE_EXPIRED`, `CODE_CONSUMED`, `CAPACITY_FULL`, `POLICY_BLOCKED`, `UPLOAD_INVALID`, `DOWNLOAD_UNCONFIRMED`, `CONSENT_REQUIRED`, `PROVIDER_UNAVAILABLE`, `CLEANUP_REQUIRED`, `TEMPORARILY_UNAVAILABLE`, `IMMUTABLE_RECORD`.

UUID arguments and idempotency keys are UUID strings; versions are integer JSON numbers; timestamps are UTC ISO strings. Reuse an operation key only for identical non-secret input. Mutation retries return the original committed result, so refresh the canonical row after replay. Money is a decimal string in quote/service RPCs, request snapshots and Teller projections. Direct payment/service table reads expose PostgreSQL `numeric`; parse their JSON number lexemes with decimal arithmetic, never binary floating point.

## Accounts

`StudentProfile` has exactly `first_name,middle_name,no_middle_name,last_name,student_id,course_program,year_level,mobile_number,gender`. All fields except middle name are nonblank strings; `no_middle_name` is boolean. Middle name is a nonblank string, or null/empty with the explicit exception. There are no invented ID, phone, program, year or gender enums. Email is Auth-owned and cannot be changed through these RPCs.

| Interface | Arguments | Return |
|---|---|---|
| Edge `register-student` | `{email,password,profile:StudentProfile,terms_version,idempotency_key}` | `{account_created:true,email,next_action:'sign_in'}`; never a session |
| Edge `bootstrap-user` | Empty POST, Bearer access token | Legacy eight fields plus `Account` below |
| RPC `account_view` | none | `Account` |
| RPC `update_account` | `p_expected_version:bigint,p_terms_version:text|null,p_student_profile:jsonb|null,p_idempotency_key:uuid` | `Account` |
| Edge `recovery-request` | `{email}` | Always `{request_received:true}` after a valid JSON command; does not assert delivery |

`Account` is the flat `profiles` row (including profile fields/version) plus `profile_complete:boolean`, `terms:{current_version,accepted_version?,accepted_at?}`, and `next_action:'accept_terms'|'complete_profile'|'home'`. It deliberately does not nest another duplicate profile. Student completion is computed in SQL. Existing privileged accounts keep their provisioned assignments; trusted provisioning must establish their completion. Existing unversioned consent does not become Terms/AI consent. Acceptance requires the active approved Terms document, but previously accepted versions do not trigger an invented future reacceptance policy.

Terms read: `/rest/v1/terms_documents?active=eq.true&select=version,content,content_digest,published_at&limit=1`. No row means configuration required. Reading never accepts. Approved content/digest/publication time are immutable; only the active flag can change.

Registration uses Auth Admin `createUser` with server-only `email_confirm:true`, then a database profile/Terms transaction. The correlation ID is server-controlled app metadata. Attempt records retain no password. Retries reconcile a lost Auth/DB response; compensation cannot delete a committed profile or a preexisting unrelated identity. Unknown Auth-create outcomes stay unavailable and require trusted reconciliation; a retry does not silently create another user. Account-created goes to explicit Sign in with blank password. Auto-confirm is a sandbox flow, not mailbox ownership proof.

Recovery reset stays with Supabase Auth's verified recovery flow (PKCE/OTP and recovery session), then `updateUser({password})`; no GoServe RPC accepts a target account or arbitrary redirect token as reset authority. Android must handle explicit recovery before ordinary routing and clear password state afterward. `GOSERVE_RECOVERY_REDIRECT` must be a trusted configured allowlisted native redirect. Delivery and actual mailbox proof remain integration gates.

## Catalog, requests and payments

| RPC | Arguments | Return |
|---|---|---|
| `save_service` | `p_id:uuid|null,p_expected_version:bigint,p_value:jsonb,p_idempotency_key:uuid` | Service row, decimal-string amount |
| `update_office` | `p_expected_version:bigint,p_status:text,p_note:text|null,p_idempotency_key:uuid` | Assigned office row |
| `save_announcement` | `p_id:uuid|null,p_expected_version:bigint,p_body:text,p_active:boolean,p_idempotency_key:uuid` | Announcement row |
| `quote_request` | `p_service_id:uuid,p_delivery_mode:text` | Quote row plus `service` snapshot and `submittable` |
| `submit_request` | `p_quote_id:uuid,p_evidence_file_ids:uuid[],p_notes:text,p_payment_method:text|null,p_idempotency_key:uuid` | Request row |
| `transition_request` | `p_request_id:uuid,p_expected_version:bigint,p_target_status:text,p_reason:text|null,p_idempotency_key:uuid` | Request row |
| `cash_payment` | `p_reference:text,p_confirm:boolean,p_expected_version:bigint|null,p_idempotency_key:uuid|null` | Safe Teller projection |

`save_service.p_value` has exactly `{name,delivery_modes:['physical'|'digital',...],requirements:[{id,...approved requirement data}],fee_state:'pending'|'free'|'required',amount:decimal-string|null,currency:string|null,active:boolean}`. Supply expected version 0 for new records. `pending` requires null amount/currency; `free` requires zero/null; `required` requires positive amount/nonblank currency. Requirements with an ID require a matching verified evidence file. Evidence policy supplies supported MIME/size/verifier values separately. Staff can manage only their assigned office and permitted capability. No arbitrary office argument can redirect catalog writes.

Quotes are owner-bound snapshots of the catalog version. Submit locks/rechecks that version, active delivery mode, configured fee and owned verified evidence. Changes require a new review. Free creates no payment and accepts only null method. Required cash needs configured office eligibility. eGovPay remains unavailable. Request snapshots and payment amount/currency/method are immutable. Request statuses are exactly `Submitted`, `Awaiting Payment`, `Processing`, `Ready for Pickup/Download`, `Completed`, `Cancelled`, `Rejected`; payment statuses are separately `pending`, `paid`, `failed`, `expired`, `cancelled`.

Office transitions: Submitted/Awaiting Payment → Processing → Ready. Required payment must be paid. Ready digital requires a current verified PDF. Cancelled/Rejected require configured roles/states/window and a nonblank reason; paid records survive. Only delivery functions can complete. Request events are append-only, ordered by per-request `sequence`.

Teller lookup uses `p_confirm:false` and null expected version/key. Confirmation uses the lookup version and a new UUID key. Teller must be provisioned at `OFF-CASH`; configured `cash_offices` limits scope. Projection fields: `payment_id,request_id,reference,office_id,service_name,amount,currency,status,version,confirmed_at`. No notes, evidence or student profile data. Payment confirmation records one financial audit and notifications; it does not advance the request.

Edge `start-egovpay` accepts `{payment_id,idempotency_key}` and returns `PROVIDER_UNAVAILABLE` after authorized lookup. `egovpay-callback` always returns 503 with that code. Neither parses a browser return or accepts a status as proof. Existing eGovPay/eGovAI configuration and keys are untouched. A future approved provider adapter requires an additional reviewed implementation and verified event/reconciliation tests; toggling an existing flag cannot activate these placeholders.

## Private files and delivery

| Interface | Arguments | Return |
|---|---|---|
| Edge `request-file`, prepare | `{service_id,request_id:null|uuid,kind:'evidence'|'deliverable',requirement:null|string,mime_type,size_bytes,idempotency_key}` | File metadata with `id` and pending verification |
| Edge `request-file`, upload | Binary POST with `X-GoServe-Upload:<file id>`, Bearer token | Verified file metadata |
| Edge `request-file`, evidence read | `{kind:'download',file_id}` | Authenticated private bytes |
| RPC `pickup_claim` | `p_request_id:uuid` | `{id,request_id,display_code,qr_payload,expires_at}` |
| RPC `pickup_validate` | `p_code:text` | `{validation_id,request_id,reference,office_id,request_version,claim_expires_at}` |
| RPC `pickup_handover` | `p_validation_id:uuid,p_expected_version:bigint,p_idempotency_key:uuid` | Completed request row |
| Edge `download-deliverable`, begin | `{kind:'begin',file_id}` | `{download_id,file_id,expires_at,sha256}` |
| Edge `download-deliverable`, transfer | `{kind:'transfer',download_id}` | Private PDF stream using the same Bearer session |
| RPC `acknowledge_download` | `p_download_id:uuid,p_sha256:text` | `{download_id,confirmed_at}` |
| RPC `confirm_receipt` | `p_request_id:uuid,p_expected_version:bigint,p_idempotency_key:uuid` | Completed request row |

Upload uses the authenticated Edge endpoint, not reusable signed Storage URLs. Declared byte count, configured MIME/size, actual file signatures and parser decoding are checked before immutable upload. PDF parsing uses pdf-lib; PNG uses CRC-checked pngjs; JPEG uses strict jpeg-js. Deliverables must be PDF. Configure file size/decode limits appropriate to the Edge runtime before activation. A lost upload response is reconciled only against identical verified bytes. Superseding a verified deliverable invalidates previous download eligibility. Upload expiry applies to new/finalized upload authorization, not arbitrary lifetime extension.

`prepare_file` and `begin_download` are authenticated SQL RPCs used by the adapters; arguments match their source functions. Clients should use the Edge interfaces for full file transport. No public metadata table has object paths or pickup codes. Direct authenticated Storage access to `goserve-private` is denied even if another permissive policy exists. Evidence read checks owner/office; deliverable transfers check owner, Ready/current verified PDF and paid/free state each time.

Ready never issues a code. Claim locks the request, creates/reuses one active private 64-hex-character random code, and applies approved expiry/replacement policy. QR payload is the same opaque code, not a URL. Validate only opens a staff-bound review. Handover rechecks current request/payment/claim/office/expiry and consumes once. Codes never enter public tables, notifications, audit, operation ledger or Realtime publication. Native clients must also avoid logging them.

Download issuance is not success. The server verifies stored size/hash, streams the entire object, and records completed transfer only after stream consumption; cancellation/failure does not create a success record. The native client must save fully and verify SHA-256 before acknowledging. Receipt additionally requires that acknowledgement for the current PDF. These two signals avoid treating viewer open/link issuance as receipt. Actual network disconnect behavior remains an integration gate.

## Inquiry, unread state and consultations

| RPC | Arguments | Return |
|---|---|---|
| `send_inquiry` | `p_thread_id:uuid|null,p_office_id:text|null,p_body:text,p_idempotency_key:uuid` | `{thread:Thread,message:Message}` |
| `assign_inquiry` | `p_thread_id:uuid,p_recipient_id:uuid|null,p_expected_version:bigint,p_idempotency_key:uuid` | Thread row |
| `read_inquiry` | `p_thread_id:uuid,p_through_sequence:bigint` | Actor's persisted read row |
| `ack_notifications` | `p_ids:uuid[]` | Number newly acknowledged (retry returns 0) |
| `unread_counts` | `p_thread_ids:uuid[]` (maximum 100) | `{notifications:totalUnread,messages:totalUnread,threads:[{thread_id,unread_count}]}` |
| `save_slot` | `p_id:uuid|null,p_expected_version:bigint,p_office_id:text,p_owner_id:uuid,p_start_at:timestamptz,p_end_at:timestamptz,p_capacity:integer,p_active:boolean,p_idempotency_key:uuid` | Slot row |
| `book_consultation` | `p_slot_id:uuid,p_expected_version:bigint,p_idempotency_key:uuid` | Appointment row |
| `change_appointment` | `p_appointment_id:uuid,p_expected_version:bigint,p_target_slot_id:uuid|null,p_target_version:bigint|null,p_reason:text|null,p_idempotency_key:uuid` | Appointment row |

New inquiry: null thread ID and an active office ID. Replies: thread ID and null office ID. Student owns the thread; scoped staff/assigned faculty share the same immutable messages and safe counterpart read cursors. Read cursor advances only for the actor, never backward/past an existing sequence. `unread_counts` totals count persisted authorized rows independently of the current list page. Notifications contain target/event IDs, never copied document/message bodies.

Guidance capability `guidance` at `OFF-GCC` manages Guidance slots. CCS `faculty` manages their own slots; `secretary` at `DEP-CCS` coordinates faculty slots. These literal internal capability strings must be trusted provisioning data. Changing existing capability assignments is outside these RPCs. Book locks/counts capacity and enforces unique active student/slot booking. Slot changes with reservations are conservatively `POLICY_BLOCKED` until a separate approved schedule-change workflow exists. Cancellation/reschedule use configured roles/notice window. A null target cancels; a nonnull target atomically reserves replacement before releasing the original. Both slots lock in stable order; failure rolls back the entire move.

## Bounded canonical reads and Realtime

No RPC returns an entire office queue, conversation or request history. Read tables via RLS with explicit columns and `limit=50` (client hard maximum 100); never issue an unbounded `select=*` list. A next page exists when the page is full; cursors are the final row's ordering tuple. These are implementation pagination caps, not institutional policy.

| Data | Filter and order | Cursor |
|---|---|---|
| Requests/threads/notifications | Own or RLS-office scope; `created_at.desc,id.desc` | `(created_at,id)` strictly less than last row |
| Messages | `thread_id=eq.ID&sequence=gt.N&order=sequence.asc&limit=50` | Last sequence; initial N=0 |
| Request events | `request_id=eq.ID&sequence=gt.N&order=sequence.asc&limit=50` | Last sequence |
| Slots | Approved office/owner filters; `start_at.asc,id.asc` | `(start_at,id)` strictly greater |
| Appointment events | `appointment_id=eq.ID&order=created_at.asc,id.asc&limit=50` | `(created_at,id)` strictly greater |
| Services/announcements/files/payments/appointments | Relevant parent/office filter; stable `id.asc&limit=50` | `id=gt.lastID` |

For tuple cursors use PostgREST `or=(created_at.lt.TIME,and(created_at.eq.TIME,id.lt.ID))` (reverse operators for ascending pages). Obtain unread totals from `unread_counts`, not the current message/notification page. Slot `reserved` and `capacity` supply remaining capacity. Redacted Teller payments can be read under RLS for permitted cash offices; request content is still hidden.

Publication membership: offices, profiles, services, announcements, requests, request_events, payments, request_files, notifications, inquiry_threads, inquiry_messages, inquiry_reads, consultation_slots, appointments, appointment_events, ai_consent. All have RLS; private tables/quotes/Terms are not published. Use events as invalidation, deduplicate by canonical IDs/versions, and refetch the authorized bounded snapshot after reconnect. Actual Supabase Realtime transport and cross-device delivery require separate tests.

## Optional AI and maintenance

Edge `seryo-control`: `{kind:'readiness'}` or `{kind:'consent',decision:'allow'|'decline'|'revoke',expected_version,idempotency_key}`. Returns `{consent,consent_version,provider_ready:false,can_send:false,can_attach:false,cleanup:'clear'|'unresolved',blockers}`. SQL equivalents: `seryo_state()` and `set_ai_consent(p_decision:text,p_expected_version:bigint,p_idempotency_key:uuid)`. No chat, prompt, response or file content is persisted. Separate consent audit is append-only. Revoke blocks future processing and makes no claim about cancelling prior work.

`seryo-file` and `seryo-send` authenticate, check consent/cleanup and fail closed with `PROVIDER_UNAVAILABLE`. The actual provider request/file/delete protocol is absent, so no upload, analysis or provider cleanup success can be claimed. One PDF/JPG/PNG up to 10 MB remains the required activation contract. `discard_attachment` returns `NOT_FOUND` while uploads are disabled. The minimal private operation schema records unresolved cleanup without retaining conversation content. Full provider dispatch, operational replay and provider-specific deletion need the approved protocol and a further implementation.

Trusted-only RPCs (never call from Android): `registration_operation(p_command,p_email,p_key,p_profile,p_terms_version,p_attempt_id default null)`; `file_server_operation(p_actor,p_file_id,p_command,p_sha256 default null)`; `download_server_operation(p_actor,p_download_id,p_complete default false)`; `cleanup_claim(p_lease_seconds)`; `cleanup_finish(p_job_id,p_lease_id,p_absence_verified)`. Only `service_role` can execute them. The Edge first verifies the session and supplies the verified actor, never an input actor ID.

`cleanup-files` is a protected worker endpoint requiring `GOSERVE_CLEANUP_SECRET` and positive `GOSERVE_CLEANUP_LEASE_SECONDS`. It claims one due file per invocation, removes it, independently lists the exact immutable path to verify absence, then finalizes with the matching unexpired lease. Failure remains retryable. Evidence/deliverables on terminal requests become due after 30 days. Financial records/audit are untouched. Worker cadence, lease duration and abandoned-upload retention require explicit deployment configuration; no financial retention period is invented.
