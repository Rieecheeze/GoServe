# v2.3 source deployment and verification gates

On21October the owner explicitly approved the staged core/account/photo rollout. Remote20261021005108/goserve_v23_core_accounts_workflows_photos applies the first nine source migrations plus the eleventh photo migration in one transaction. The independently deployed tenth, remote20261020222356/profile_department, is preserved and was skipped. Bootstrap v2, register-student v1 and recovery-request v1 are ACTIVE; other Edge sources below are not deployed by this rollout. Original six-profile/five-office checksums match, and all six demo Auth/profile/account_view/bootstrap API checks pass with test sessions closed. Terms documents/acceptances/profile completion and provider transports remain inactive. See ../output/validation/account-rollout/live-rollout.json and http-readiness.json. Do not rerun the bundled sources, the department source or `schema.sql` over this managed installation. Existing TIP-only Auth hooks, provider configuration and keys are preserved.

## Ordered source

1. On a new isolated database only: foundation `schema.sql`.
2. Apply all eleven migrations in filename order, from `migrations/202610170001_v23_accounts.sql` through `20261020233835_profile_avatars.sql`. The session-authority migration is required for the institutional Google authorization boundary; workflow options must follow it and precede native discovery consumption. The ninth supplies safe Teller detail by payment ID. The tenth adds nullable trusted department affiliation without office capability grants or Student backfill. The eleventh adds versioned private profile pictures, immutable upload paths and retired-object cleanup permissions. See [department rollout](../docs/GoServe-ccs-department-rollout.md) and [profile pictures](../docs/GoServe-profile-photo-rollout.md). Migrations are transactional, intentionally not rerunnable, and require existing `auth`, `storage` and `supabase_realtime` infrastructure. They preserve existing profile/office columns and office IDs. Review/stage in a real PostgreSQL17 Supabase environment before deployment. The options and cash-ID migrations add only read-only RPC code/grants and need no data rollback. After photos are used, preserve the bucket, registry and profile pointers; do not drop stored pictures as an automatic rollback.
3. Deploy bootstrap plus new Edge sources and shared `lib/` / `functions/_shared/` modules. Pin imports exactly as source. Bootstrap v2 now depends on `account_view`, so database first, Edge second. Preserve previous deployed function for rollback until verification passes.
4. Set gateway JWT verification false for functions that perform explicit `getUser` verification and for public register/recovery/callback endpoints. Protected functions still require the Bearer token in source. `cleanup-files` uses its separate worker secret. Confirm the deployment gateway configuration with real HTTP tests; no local project configuration or secrets are modified by this package.
5. Keep provider transports disabled. Configure only approved legal/catalog/policy data through trusted administration. The native repositories must use [API-v2.3.md](API-v2.3.md), not the earlier proposal signatures.

For the existing GoServe installation, all eleven source migrations are now represented by the independent department entry and the ten-source rollout entry. The managed migration history intentionally does not contain separate versions for each bundled source. Use output/validation/account-rollout/source-manifest.json to reconcile exact hashes; do not use a timestamp-only push that would apply them again. Fresh installations apply all eleven source files in filename order. The Secretary-only department rules in sources1/4/5 are included in the live bundle: contacts, assignments and schedules require Secretary authority and matching trusted Student affiliation. Guidance and Registrar routes remain available.

The first nine prescribed timestamp filenames were retained. The tenth and eleventh were generated with pinned Supabase CLI2.121.0 using `migration new profile_department` and `migration new profile_avatars`, then placed in the existing ordered core chain. No `db pull` was run. The approved managed rollout uses the transactional bundle and exact deployment JSON files under output/validation/account-rollout/, with previous-bootstrap.json retained for rollback. Rehearse backup/recovery and migrate forward; dropping tables or populated profile metadata after use would destroy data and is not an acceptable automatic rollback.

## Configuration gates

Every row below is approved server data in `goserve_private.policies`, not client input. No example values are installed. `service_role` alone can manage these records. Missing configuration returns the documented domain code and blocks only its dependent action.

| Name | JSON structure | Scope |
|---|---|---|
| `cash_offices` | Array of eligible office IDs | Cash submission and Teller scope |
| `pickup` | `{validity_seconds:positive integer,replace_expired:boolean}` | Claim expiry/replacement |
| `request_cancelled`, `request_rejected` | `{roles:[...],states:[...],window_seconds:nonnegative number}` | Cancellation/rejection window from request creation; reason required |
| `consultation_offices` | Array of approved office IDs | Schedule creation |
| `consultation_cancel`, `consultation_reschedule` | `{roles:[...],minimum_notice_seconds:nonnegative number}` | Appointment changes relative to original slot start |
| `file_<service UUID>_evidence`, `file_<service UUID>_deliverable` | `{mime_types:[...],max_bytes:positive integer,upload_seconds:positive number,verifier:'goserve-v1',max_pixels:positive integer for images}` | File transport and actual-byte verification |
| `download` | `{validity_seconds:positive integer}` | Owner authenticated transfer lifetime |
| `ai_notice` | `{version:approved notice version}` | Optional consent audit reference; does not enable transport |

Only approved text goes in `terms_documents`. Its SHA-256 digest must match the approved text. Activation is trusted administration. Existing legacy consent is not backfilled into Terms. Existing Staff/Teller completion is not fabricated; trusted provisioning must explicitly establish completion under approved role requirements. Staff capability literals remain `guidance`, `faculty`, `secretary` for compatibility. Ordinary preexisting Staff with null capability retain assigned-office catalog/request/inquiry authority only outside department offices. Department access requires Secretary capability; legacy Faculty assignments or explicit grants cannot bypass that rule. Extra approved grants can be stored in private `capabilities(profile_id,office_id,capability)` using `catalog`, `requests` or `inquiries`, without changing role/office or bypassing the department boundary.

Environment: standard Supabase URL/service-role/anon keys; approved `GOSERVE_RECOVERY_REDIRECT`; separate worker-only `GOSERVE_CLEANUP_SECRET` and `GOSERVE_CLEANUP_LEASE_SECONDS`. Configure invocation cadence externally. Never expose service/worker credentials in Android or logs. Provider flags/credentials alone cannot enable unavailable adapters.

## Local verification

Use Node 24. Install pinned dependencies with `npm ci --prefix backend --ignore-scripts`. Run `npm test --prefix backend`; tests are serialized, with one PGlite instance. On a memory-constrained host use `node --v8-pool-size=1 --wasm-num-compilation-tasks=1 backend/tests/v23.test.mjs` without concurrent Gradle/emulator jobs.

The SQL harness creates synthetic Auth/Storage roles and relations, runs the unmodified foundation and ordered migrations, and exercises actual PostgreSQL/PLpgSQL/RLS/transaction constraints. PGlite 0.4.6 reports PostgreSQL **17.5**, while the connected database reports **17.11**. Matching the major version is useful but does not prove patch-level equivalence. Fixture roles/schema do not reproduce the entire managed service. Financial amounts, policies, identities, Terms and schedules in tests are synthetic and are never migration seeds.

The original bootstrap/eGovPay tests remain. New tests execute the production adapters with injected external Auth/Storage boundaries. They prove compensation, fail-closed behavior and stream control locally; no provider or mailbox calls are made. File parser imports must additionally be exercised in the actual Deno Edge runtime.

## Required integration evidence before release

- Real PostgreSQL 17.11 migrations/advisors and concurrent separate-session races: stale catalog/submit, cash confirmation, claim/handover, digital replacement/receipt, last slot and cross-slot reschedule.
- Supabase Auth signup/Google/getUser and the registration saga under lost responses, timeout, retries and failed compensation; neutral public errors, edge rate limits and abuse controls; provisioned Staff/Teller compatibility.
- Recovery email to an actual mailbox, configured redirect/PKCE validation, expired/invalid contexts and blank-password Sign in.
- Real Storage denial, actual byte parsing under Edge memory/time limits, interrupted upload/download and verified cleanup including retries and worker lease loss. Abandoned unbound uploads need an approved retention policy and cleanup extension; current cleanup covers request-associated terminal evidence/deliverables only.
- Realtime cross-role delivery, RLS denial, JWT refresh, reconnect ordering and account-switch cache/subscription clearing. Publication membership alone is insufficient.
- Approved eGovPay signing/callback/status/amount/reconciliation contract and eGovAI request/knowledge/procedures/file/delete contract, followed by reviewed transport implementations and acceptance tests. These are deliberately unavailable now, not simulated.
- Native/device integration, lifecycle/accessibility/performance and release evidence remain later tasks.

Registration attempts in `creating` without a correlated Auth row or failed compensation remain durably inspectable. A trusted repair process must reconcile them; never delete an arbitrary email match. Provider processing and AI attachments cannot be created while readiness is false. No provider deletion or AI analysis result is claimed by this source.
