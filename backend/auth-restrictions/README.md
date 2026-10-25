# TIP-only Google Auth

The later [recovery migration](../recovery-rollout/README.md) tightens Before User Created to deny every new non-Google account. Existing provisioned password sign-in and refresh remain supported. The original standalone migration below describes the earlier hook installation.

This independent Auth configuration adds no application tables and does not apply the nine v2.3 workflow migrations. Its migration was generated with official Supabase CLI2.121.0 using `migration new tip_google_auth_hooks`. It is deployed separately because the owner requested Google activation before the remaining app/backend rollout.

`goserve_auth.before_user_created(jsonb)` rejects Google signups unless their exact email domain is `tip.edu.ph`. ASCII uppercase is accepted; subdomains, trailing whitespace, multiple at-signs, Unicode lookalikes and suffix attacks are rejected. The provider comes from the Auth event's server-owned `app_metadata`, never user metadata. Existing email/password provisioning remains supported.

`goserve_auth.access_token(jsonb)` checks new OAuth tokens and OAuth AMR retained during refresh. It requires the Auth-owned user email to be confirmed and exactly TIP, a matching Google identity with boolean `email_verified=true`, a non-anonymous user, and no unsupported linked social identity. It validates the event subject and returns all claims unchanged on success. User metadata and claims containing a forged TIP email cannot replace the Auth records. Password sessions keep their claims and are not given student privileges by this hook.

The schema is not exposed through the Data API. Functions use invoker privileges and an empty search path. Only `supabase_auth_admin` receives execution; it already has read access to Auth users and identities. Do not grant application roles access to these hooks or Auth tables.

After applying the migration, select these Postgres hooks under Authentication → Auth Hooks:

| Hook slot | Postgres function |
| --- | --- |
| Before User Created | `goserve_auth.before_user_created` |
| Customize Access Token (JWT) Claims | `goserve_auth.access_token` |

Configure both before enabling Google. Keep nonce verification and email requirements enabled. Google Cloud's Web OAuth client must use `https://uuvexkewiuxdbuelwogy.supabase.co/auth/v1/callback`. Its Internal audience restricts the Cloud project's parent organization; use Internal only when that organization is TIP. Supabase's hooks enforce the exact domain separately.

Run the local behavioral tests:

```powershell
& 'C:/Program Files/nodejs/node.exe' --wasm-num-compilation-tasks=1 --max-old-space-size=384 --test backend/tests/google-domain-hooks.test.mjs
```

The15 tests first produced6passes/9behavioral failures against permissive hook scaffolds, then15/15GREEN after implementation. The normal backend suite includes them and passes272checks. Fixtures are local only. Never install test Auth rows or roles into the live project. Real Google account creation, callback exchange and Auth-role hook execution still need an eligible account/device test after credential setup.

To roll back, disable Google first, detach both hook slots, revoke the Auth role's execution and remove only the new functions/schema once dependencies are checked. Preserve Auth users, identities, profiles, policies and all application records.

References: [Supabase Auth hooks](https://supabase.com/docs/guides/auth/auth-hooks), [Before User Created](https://supabase.com/docs/guides/auth/auth-hooks/before-user-created-hook), [Custom Access Token](https://supabase.com/docs/guides/auth/auth-hooks/custom-access-token-hook), [Google audience](https://support.google.com/cloud/answer/15549945?hl=en).
