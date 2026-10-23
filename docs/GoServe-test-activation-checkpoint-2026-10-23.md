# GoServe test activation checkpoint

## Final current checkpoint (23 October)

The current record is [GoServe-test-rollout-final-2026-10-23.md](GoServe-test-rollout-final-2026-10-23.md) and `output/validation/test-rollout-final-2026-10-23.json`. Full Android 1,003 tests / 84 suites passed; APK assembled; lint zero errors / 141 warnings. Full backend 403 checks passed. Nineteen live boundary checks passed. Seryo v4 and all four eGovPay endpoints are ACTIVE, and both connections are ready with payments restricted to tests. The new r2 APK is `output/android/GoServe-v2.3-test-2026-10-23-r2.apk`, SHA-256 `21da95ccb8de9fee6e39ee692c6f784f97c597adc9d01e5a04e66915c35402e2`.

Profiles, Terms acceptances and catalogue entries remain zero; participants must complete onboarding and office staff must enter intended test services. No app-bound live payment or physical-device test was fabricated. Native PostgreSQL concurrency testing remains unexecuted because Windows blocked its existing binaries. No runners remain active. No Git operations were performed. The notes below are historical and do not restore a separate-engine requirement or disable the working test payment flow.

## Earlier continuation notes (historical)

This section supersedes the earlier custom-engine dependency below. The user clarified that Seryo is GoServe's persona/agent powered by the existing eGovAI API. `seryo-send` v4 and the additive provider-selection migration are deployed; the connection is ready. Raw user messages are now replaced with fixed supported service intents before provider dispatch. The exact requested identity policy is stored server-side, identity questions are answered locally, and provider identities are blocked in chat output. A separate review confirmed the three reported guard gaps were fixed; 26 focused checks passed. Full final gates will run after payment code freezes.

The navigation caption row and item labels are removed; accessible destination descriptions/tooltips and 48dp targets remain. Five navigation tests passed, covering every role at 320/360/390/600dp and 2x text. New payment screens are being validated separately.

The user explicitly approved the TEST MODE Pay Now click and acceptance of its Terms/Data Privacy notice. An authenticated provider GET verified TEST / PAID, PHP 1.0000, a nonempty paid_at, and the original synthetic transaction UUID/reference. This diagnostic transaction is not bound to an app payment and credited no user payment. Its visible total was PHP 1.01 including the channel fee. Evidence is in `output/validation/seryo-and-egovpay-provider-evidence-2026-10-23.json`.

Backend and Android payment implementations are in progress in parallel. Root review requires: ready checkouts resume with a fresh screen key without duplicate provider creation; verified late payments are recorded even if the request was cancelled, without reopening the request; ambiguous creation uses CHECKOUT_UNCONFIRMED rather than a configuration error; a freshly opened native screen reconciles pending provider status and reloads the canonical payment. Native PostgreSQL race tools were blocked by Windows before startup; no security setting was changed and the prepared opt-in race harness is not reported as passing. No Git operations, profile completion or participant consent were fabricated.

## Earlier checkpoint (historical)

Saved 23 October 2026, 00:49 Manila. No Git or GitHub operations were performed. Existing credentials and all six demo accounts are preserved. The student remains CCS; department contact is the matching Secretary only.

## Verified work

- User-approved test Terms `goserve-test-2026-10-22-v1` are active. Their content digest is `e98c47b74dd61b5ebda9605f164dc7e87575a68b120d1fefe087366344d9a4f8`. Eleven approved operating policies plus the dedicated cleanup-worker policy are configured. Participant Terms acceptances and completed profiles remain zero; none were fabricated.
- Private request-file and download-deliverable functions are deployed. The department file-authority fix requires the Secretary capability at both trusted staff paths. Future explicitly created demo services receive approved technical file defaults; official services, fees and schedules were not invented.
- User first declined automatic cleanup, then explicitly requested it after 30 days. The current schedule checks every five minutes for request files whose request has been terminal for 30 days. Dedicated worker authorization is stored in Vault and checked against a private digest. Live worker requests returned HTTP 200 with no eligible jobs. Three consecutive scheduled database runs succeeded. Current profile pictures and abandoned unbound uploads are outside this terminal-request retention rule.
- Android now supports a protected Seryo send, a pending thinking animation and a session-only transcript. Drafts and messages clear on account change. Consent remains separate from registration Terms. The loading screen still has no mascot.
- Seryo uses the official Custom AI Engine transport. It buffers answers until completion, rejects tool confirmations/events, bounds requests and responses, discards provider sessions and checks output before release. Context contains only a fixed public service-fact projection, filtered by department. User identity fields cannot be supplied as extra send properties. Consent is checked again after generation. The operation marker stores no question or answer text.
- The eGovPay adapter now uses a test-decorated header, the undecorated merchant key for HMAC, and canonical decimal spelling without floating-point conversion. The actual sandbox accepted the corrected payload and returned HTTP 201. A server-side details lookup returned TEST / INITIAL with PHP and the expected amount. No real funds were used.

## Verification and build

The full Android gate passed: 969 tests across 82 suites, zero failures/errors, APK assembled and lint completed with zero errors and 138 warnings. The full current backend suite passed 364 checks. Five custom-engine protocol tests and five Seryo handler tests are included. Twelve live HTTP checks verified anonymous/invalid-token denials, worker-secret denial and incomplete-profile gates. Actual PDF/PNG/JPEG parsers passed six Deno checks earlier in this continuation; the final custom-engine/file function type check also passed.

Test APK: `output/android/GoServe-v2.3-test-2026-10-23.apk`, 13,776,608 bytes. SHA256: `76e470f986b7d85a4ae477807bd2a73b547a8ab1b1a8b49eb8b53e08c5595c44`.

Evidence: `tmp/activation-native-gates-current.log`, `tmp/activation-backend-final.log`, `tmp/activation-live-edge-access.log`, `tmp/activation-engine-deno.log`, and `output/validation/test-activation-2026-10-23.json`. The new chat was tested with native UI tests, not a successful live custom-engine conversation on a device.

## Provider dependencies and unfinished work

The user explicitly wants Seryo powered by eGovAI. Do not substitute a different provider or silently enable a deterministic guide. A proposed guide fallback was not approved; the user asked to double-check and implement eGovAI as Seryo.

The signed-in Platforms catalog exposes the hackathon access-code AI Assistant route. A live token exchange worked. A live public service-guidance test returned HTTP 200 but identified itself as Kuya A and explicitly refused the Seryo/GoServe campus role. Do not try to bypass that provider boundary or merely relabel its output.

The [official custom-engine route](https://egov-ai.e.gov.ph/developers) requires an approved workspace, engine code and scoped API key/secret. The user cannot sign in to that separate portal. No custom-engine credentials are configured. `seryo-send` v2 is deployed for this route, but provider readiness remains false. The old hackathon access code is unchanged. See `docs/GoServe-Seryo-eGovAI-integration-2026-10-23.md`, `docs/seryo/Seryo-Engine-Instructions.md` and `docs/seryo/GoServe-Public-Service-Guide.md`. An authorized representative or program organizer must enable the appropriate workspace; do not invent agency registration details or accept provider agreements for them.

eGovPay's final transaction status and callback verification remain incomplete. The app's payment submission, checkout-success handling and callback bridge still need implementation and testing after the final provider contract is verified. They remain disabled. A checkout creation probe is not evidence of an end-to-end in-app payment. Never treat a browser return or callback body as payment proof.

Pending user action: finish the marked Test Mode checkout and reply `test checkout completed`. Its diagnostic URL is `https://egovpay-pgi-dev.oueg.info/a30b3a1c-101e-4b32-b357-d7fb653412fb`; the prepared AliPay test option shows a simulated PHP 1.01 total including a PHP 0.01 fee. Clicking Pay Now accepts provider Terms/Data Privacy, so the user was asked to review and complete it. No agent click or acceptance occurred. Reconcile its final state with an authenticated provider GET, then implement stable transaction binding, exact amount/currency/reference checks, idempotent financial updates, callback-triggered server verification and missed-callback reconciliation before enabling the app workflow.

Live request-file happy paths still require a participant to complete their own profile, accept Terms and use a staff-created test service. Do not bypass onboarding or seed fake profile data to make a test pass.

## Live migration evidence

Remote history names and actual versions: file_department_authority `20261022153345`; test_operating_defaults `20261022153352`; role_specific_request_policy `20261022154124`; cleanup_worker_authority `20261022155001`; seryo_service_gateway `20261022160200`; seryo_custom_engine_configuration `20261022162631`. Local source filenames use the installed CLI's generated timestamps. Do not replay the whole migration chain against live.

## Operational notes

The Supabase security advisor reports intentional fixed authenticated SECURITY DEFINER RPCs and private RLS tables with deny-by-default access, plus leaked-password protection disabled. No blanket security-clean claim was made. The vendor-owned pg_net tables retain default SQL ACLs; postgres could not revoke the owner's PUBLIC grants. The net schema is not exposed through the Data API, and neither Android nor Seryo has a raw SQL path. Do not expose that schema or claim those vendor ACLs were hardened.

An attempted combined AI probe and deletion of an internal token-response record was rejected by automatic approval review. The non-destructive probe succeeded; the record was left unchanged. No response logs were deleted. A short-lived token exists only in the internal HTTP response store, never in chat, source, Android or local validation output.

## Resume rules

Use context-mode for derived output and native apply_patch for source edits. Do not read secrets into tool output. Do not run Git. Serialize full Gradle and PGlite runs. Current sources have passed their relevant gates; repeat broad checks only after new changes or failures. Existing auditors hit quota; do not repeatedly respawn them.

Heavy runners are finished. CUA browser ID2 / IAB is bound as `browser`; signed-in Platforms tab ID1 is `aiTab`; marked diagnostic checkout tab ID2 is `payProbeTab`. Restore CUA documentation after compaction before using UI. Keep marked tabs only while the pending review is needed.
