# GoServe supervised test operating policies

Version: goserve-test-2026-10-22-v1. Operator: GoServe. Scope: supervised test/demo only; no real funds, official school records or invented institutional fees. These rules make the existing v2.3 controls explicit. They do not constitute TIP policy.

| Control | Test rule |
| --- | --- |
| Accounts | Exactly the six provisioned roles. No Faculty demo/contact. Individual Terms acceptance/profile entry stays explicit. |
| Department | CCS Student has trusted DEP-CCS affiliation. Only the matching CCS Secretary handles departmental contact. |
| Cash scope | Registrar, Accounting, Guidance and CCS Secretary offices may declare a labeled demo cash service; only OFF-CASH Teller confirms it. Actual service amount/currency must be explicitly entered. |
| Catalogue | New services/announcements start hidden. Staff review and confirm changes. Pending fee blocks submission; free creates no payment; required positive amount/currency stays frozen in the submitted snapshot. No official fee or service name is invented. |
| Pickup | A code is valid for 15 minutes. The owner may replace an expired code; only one active code is reused at a time. Scanning never completes handover. These durations are test technical choices, not official collection policy. |
| Student cancellation | A Student may request cancellation while Submitted or Awaiting Payment within 24 hours of creation; a reason is required. Paid audit remains and there is no automatic refund. |
| Staff cancellation/rejection | Assigned authorized Staff may cancel/reject eligible Submitted, Awaiting Payment or Processing requests within seven days, with a reason. Completed records cannot be rewritten. |
| Consultation scope | Guidance and matching CCS Secretary only. Staff must enter actual test slot start/end and capacity; no fabricated schedule or roster. |
| Consultation changes | Student/authorized Staff may cancel/reschedule at least 60 minutes before the original slot starts. Failed rescheduling keeps the original booking. |
| Evidence defaults | Only for a specifically configured demo service: PDF/JPEG/PNG, at most 10 MiB, server byte/content verification, image bound 20 million pixels, upload authorization 10 minutes. A service without its own policy remains unavailable. |
| Deliverables | PDF only, at most 10 MiB, server verification, upload authorization 10 minutes. Release only for a matching authorized request. |
| Download | Authenticated owner transfer authorization lasts five minutes. Successful save/hash/acknowledgement precedes a separate explicit receipt confirmation. |
| Request file retention | Eligible request-associated files are cleaned 30 days after terminal state; cleanup retries/audit preserve truthful status. Financial audit is separate. |
| Abandoned uploads | Must not be represented as automatically retained/deleted under the terminal-request rule. Add and verify a separate expiry/cleanup extension before claiming coverage. |
| Seryo identity/scope | Agent name Seryo. GoServe service guidance only. No raw SQL, arbitrary tools, public search, autonomous transaction, credentials or private database context. |
| Seryo context | Fixed authenticated/RLS-scoped minimal projections; no other-user data, raw IDs or profile/contact records are sent. Catalogue text/documents/questions are untrusted. |
| Seryo guardrails | Bound/normalize input, reject disclosure/override/out-of-scope requests, delimit untrusted data, restate boundaries after input, buffer provider response and validate before displaying it. Regex is supplemental to structural isolation. |
| AI files | One explicit test attachment only after the real upload/deletion protocol is verified. No real confidential records. Unknown cleanup blocks unsafe further processing. |
| Online payment | Sandbox/test-only provider credentials and transactions. Browser/callback payload cannot declare paid; authoritative status, transaction, exact amount and currency must be verified. |
| Secrets/logs | Credentials remain server-side/encrypted. No prompts, responses, private file contents, pickup codes, passwords or tokens in routine logs. |

The versioned policy bundle must be reviewed as the concrete test configuration before activation. It cannot supply official service declarations, accept agreements for participants, fabricate profiles or turn an unverified provider into a successful transaction. Files without an approved per-service policy remain unavailable rather than silently inheriting an invented rule.
