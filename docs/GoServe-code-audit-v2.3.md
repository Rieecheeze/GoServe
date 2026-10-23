# GoServe code and backend audit, 17 October 2026

The owner-approved v2.3 plan supersedes earlier foundation restrictions. The owner confirmed the UI is finished and authorized native implementation.

## Starting point

Native Java 17/XML/ViewBinding, Android API 26–35, Gradle 8.9. Existing encrypted session persistence, PKCE, HTTPS transport, own-profile reads, office directory, theme controls, supplied fonts/assets and guarded debug request previews are retained.

Git originally had an unborn master and untracked project source. Fetching the supplied GitHub repository succeeded and returned no branch heads. A source-only baseline commit was saved on `codex/v2.3-implementation`; ignored keys, SDK settings and build/tool caches remain excluded.

## Findings that drive implementation

1. Password bootstrap rejects unprovisioned identities; v2.3 requires Student self-registration and a complete profile. Revise trusted provisioning without exposing privileged assignment.
2. Live profiles have only foundation fields and a boolean-style legacy consent timestamp. Add complete profile data, server completion and versioned Terms acceptance; optional AI consent stays separate.
3. Live public schema contains only offices and profiles. No workflow/catalogue tables, Storage buckets or realtime publication members exist. Add versioned migrations and first-party operation contracts before wiring native feature screens.
4. Consultations, inbox, notifications, Staff/Teller workspaces and most requests are placeholders. Replace those routes with persisted feature screens and useful real empty/loading/error states.
5. Seryo file/send controls are disabled; no active AI transport exists. Implement picker/draft/consent/readiness/cleanup behavior while preserving provider activation gates.
6. No shared native tactile/loading motion is implemented. Add small, cancellable, reduced-motion-aware feedback using supplied brand assets.
7. Fresh native baseline reproduces the selected navigation-label and inconsistent Home/request-name failures. Preserve canonical identity and accessible navigation behavior when repairing them.

## Evidence and limits

Fresh offline debug APK build succeeded using installed Temurin 21 and an explicit Unix socket temporary directory. The unit task ran 41 tests: 39 passed, two failed. Lint was not reached. This replaces the prior loopback-blocked baseline, but does not claim the revisions pass.

The existing bootstrap-user and eGovPay Node tests exited zero before changes. Live Supabase metadata/RLS/function inspection was read-only; no personal rows or secret values were queried and no remote changes were applied.

Existing public tables have RLS and authenticated SELECT only. Legacy consent uses an owner-locked security-definer RPC with safe search_path. Private configuration/audit tables have no client access. No exploit was established by this audit; new workflows need their own executed authorization and transactional tests.

The execution ledger, task reports and final acceptance traceability will distinguish local proofs from actual device, provider and deployed Supabase integration evidence.
