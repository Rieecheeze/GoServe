# Google Auth live verification

The owner authorized Google activation and required `tip.edu.ph` only on20October. Existing Supabase app keys were preserved. Google credentials were initially absent; the owner created a Google Cloud Web OAuth client and entered its credentials directly in Supabase. No OAuth secret is saved in source or this report.

Current verified state:

- Project `uuvexkewiuxdbuelwogy` is healthy and matches the generated Android debug configuration.
- Auth settings returns200 with `external.google=true`. The Google provider is visibly ENABLED. Nonce checks remain enabled and users without email remain disallowed.
- Google PKCE authorization returns302 to `accounts.google.com`, with the expected Supabase callback and TIP domain hint.
- Supabase allows `goserve://auth/callback` and `goserve://auth/callback[?]nonce=*`. An intentionally cancelled diagnostic returns302 to the exact Android callback with its nonce preserved. No Google account was signed in or created by that diagnostic.
- Both Postgres Auth hook slots are visibly ENABLED: `goserve_auth.before_user_created` and `goserve_auth.access_token`.
- CLI-generated source: `backend/auth-restrictions/supabase/migrations/20261020152605_tip_google_auth_hooks.sql`. Supabase recorded the applied migration as version`20261020152853`, name`tip_google_auth_hooks`. The nine core v2.3 migrations and revised Edge Functions remain undeployed.
- Live SQL calls admit exact TIP/ASCII uppercase and reject Gmail, subdomains, suffix attacks, Unicode lookalikes and an unknown OAuth actor with a forged TIP claim. Metadata confirms invoker functions, empty search paths, Auth-role execution and no anon/authenticated execution. The two existing advisory notices are unchanged; no new hook warning appeared.
- The database API responds401/42501 to a zero-row anonymous profile request, preserving the restriction. This proves reachability, not a signed-in data session.

The15 hook tests produced6passes/9behavioral failures against permissive scaffolds, then15/15GREEN. The full backend suite passes272checks. The latest full Android gate passes294tests/30suites, zero failures/errors/skips, debug assembly and lint0errors/78warnings in1m50. APK11,589,437bytes, SHA256`db3624717c90914b7a0e6944381be077f91974eb6bb04c3df68211f69f4e4302`.

Limits: no eligible personal Google sign-in, successful code exchange, or actual Auth-role hook invocation through a real Google session is claimed. Managed SQL cannot `SET ROLE supabase_auth_admin`; that attempted read-only probe was denied, so live checks used the owner role plus verified grants. Live `bootstrap-user` v1 still returns an older flat profile. Revised native code requires `version`, `profile_complete`, `terms` and `next_action`; `terms_documents`, `account_view` and `update_account` remain absent. Reconcile that account backend and approved institutional Terms before claiming full in-app sign-in acceptance.

Repeat the provider/cancel-return check after a debug build:

```powershell
& 'C:/Program Files/nodejs/node.exe' scripts/check-supabase-google.mjs
```

Exit0 proves provider-start configuration and nonce-preserving callback routing. End-to-end sign-in and signed-in database access remain explicitly unverified. The checker prints no keys, tokens, state, secrets or account information. It starts and cancels an unauthenticated diagnostic attempt and sends no mail.

Evidence: `output/validation/google-auth/server-check-2026-10-20.json`, `tip-domain-hooks-enabled.png`, `google-provider-enabled.png`, `android-redirects.png`. [Hook operation and rollback](../backend/auth-restrictions/README.md).

References: [Supabase Google sign-in](https://supabase.com/docs/guides/auth/social-login/auth-google), [redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls), [Google audience](https://support.google.com/cloud/answer/15549945?hl=en).
