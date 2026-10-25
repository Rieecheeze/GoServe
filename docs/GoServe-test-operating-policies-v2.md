# GoServe Supervised Test Operating Policies

Version: goserve-test-2026-10-24-v2. Operator: GoServe. The user approved this code-only bundle with the matching Terms/Privacy version on 24 October 2026: “Approve Code-Only Policy Bundle.” Live activation remains pending implementation verification. Scope: supervised test/demo only; no real funds, official school records or invented institutional fees. These rules do not constitute TIP policy. The previously accepted v1 document remains immutable.

| Control | Test Rule |
| --- | --- |
| Existing Accounts | Preserve the six provisioned demo logins, passwords, roles and office assignments. No Faculty demo/contact. Each participant completes their own Terms, profile and recovery setup. |
| New Registration | Verified Google identity with the exact tip.edu.ph domain only. Public student email/password signup is retired. Existing provisioned password sign-in remains available. Domain enforcement is server-side; the account chooser hint is not authorization. |
| Recovery Setup | Required before protected access. Generate ten codes while signed in and explicitly acknowledge saving them. No TOTP, Authenticator app, SMTP or extra second-factor prompt during normal sign-in. |
| Recovery Codes | Ten independently random 160-bit codes per set. Globally unique stored digests, owner binding and atomic single use. Regeneration after recent normal password or TIP Google sign-in invalidates old unused codes. Plaintext appears only when generated; later visits show remaining count and replacement. No shared demo set or automatic screenshot/upload. |
| Exhausted Codes | After using the last code to reset/sign in, generate and save a new set before returning to protected app features. |
| Password Reset | Account email plus one saved recovery code. Generic invalid-proof errors, account/IP attempt limits and a five-minute one-use, app-verifier-bound reset grant. Confirm the server mutation before normal password sign-in; never invent a session or bypass onboarding. |
| Uncertain Reset | Keep the same operation and query its result. Do not repeat a password mutation after a lost response. Pending server work blocks another mutation until reconciled. Revoked sessions must fail protected-session checks. |
| Lost Recovery Access | A signed-in person may replace codes after recent normal sign-in. Linked TIP Google remains a sign-in route. Knowing an email is not sufficient to bypass recovery. Shared office/demo code custody requires the supervising operator's agreement. |
| Department | The CCS Student has trusted DEP-CCS affiliation. Only the matching CCS Secretary handles departmental contact. |
| Cash Scope | Registrar, Accounting, Guidance and CCS Secretary offices may declare a labeled demo cash service; only OFF-CASH Teller confirms it. Actual service amount/currency must be explicitly entered. |
| Catalogue | New services/announcements start hidden. Staff review and confirm changes. Pending fee blocks submission; free creates no payment; required positive amount/currency stays frozen in the submitted snapshot. No official fee or service name is invented. |
| Pickup | Codes last 15 minutes. The owner may replace an expired code; one active code is reused at a time. Scanning never completes handover. These are test technical choices. |
| Student Cancellation | Student may request cancellation while Submitted or Awaiting Payment within 24 hours of creation, with a reason. Paid audit remains; no automatic refund. |
| Staff Cancellation/Rejection | Assigned authorized Staff may cancel/reject eligible Submitted, Awaiting Payment or Processing requests within seven days, with a reason. Completed records cannot be rewritten. |
| Consultations | Guidance and matching CCS Secretary only. Staff enter actual test slot start/end and capacity. Cancel/reschedule at least 60 minutes before the original start; failed rescheduling retains the original booking. |
| Evidence | A specifically configured demo service may accept PDF/JPEG/PNG up to 10 MiB with server byte/content verification, images up to 20 million pixels and 10-minute upload authorization. No per-service policy means unavailable. |
| Deliverables | PDF up to 10 MiB, server verified, 10-minute upload authorization, released only for a matching authorized request. |
| Download | Owner transfer authorization lasts five minutes. Successful save/hash/acknowledgment precedes separate explicit receipt confirmation. |
| Request File Retention | Clean eligible request files 30 days after terminal state, with verified deletion, retries and audit. Financial audit is separate. No claim of automatic cleanup for abandoned uploads or recovery/security records. |
| Seryo Scope | Seryo provides GoServe service guidance only. No SQL, unrestricted tools, private database context, autonomous transaction or account-permission changes. Scope and authorization are enforced by fixed server code. |
| Seryo Provider Context | Only a canonical allowed service question and minimal public catalogue facts go to the configured eGovAI transport. Original questions, private account data, identifiers, recovery credentials and selected file bytes are excluded. |
| Seryo Guardrails | Normalize and bound input, reject disclosure/override/out-of-scope requests, delimit untrusted content, restate boundaries after input, buffer output and check it before display. Keyword checks supplement structural isolation. |
| Seryo Files And Preview | Attach File/Open Camera selections remain local and do not enable AI file analysis. Service evidence requires a separate reviewed service flow. Labeled local preview uses fixed guidance without provider/account access. |
| AI Consent | Present the first choice before processing. Privacy shows the saved decision; a separate explicit withdrawal control stops new AI processing. Terms and AI consent are separate. |
| Online Payment | Test-only credentials/transactions. Browser/callback data cannot declare paid. Verify authoritative status, transaction, exact amount and currency. No real-money authorization. |
| Secrets And Logs | Server credentials stay server-side. No passwords, codes, reset grants, sessions, raw AI questions/responses or private file content in routine logs. Recovery tables and audits are private and separate from request-file retention. |

Activate only the reviewed, matching versioned bundle. It cannot accept agreements for participants, fabricate profiles, supply official service declarations or turn an unverified provider result into a successful transaction.
