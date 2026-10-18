# GoServe backend foundation

The additive v2.3 source is documented in [API-v2.3.md](API-v2.3.md) and
[DEPLOYMENT-v2.3.md](DEPLOYMENT-v2.3.md). It has not been deployed. The historical
foundation instructions below describe the prior baseline; deploy the ordered v2.3
migrations before the updated bootstrap source.

Target project: `uuvexkewiuxdbuelwogy` at `https://uuvexkewiuxdbuelwogy.supabase.co`.
The Android app uses Supabase Auth, Supabase Postgres, and the `bootstrap-user` Edge Function.

## Source and deployment

`schema.sql` creates new tables and policies in a transaction. Apply it once to the verified empty
public schema. It deliberately fails if these objects already exist, so it does not overwrite an
existing installation. The five office seed rows are labeled **non-official demo data**; their
sample `Open` status does not represent actual office availability. Replace samples with approved
office data before a production rollout.

Deploy `functions/bootstrap-user/index.ts` together with its `deno.json`. The function pins the
official `@supabase/supabase-js` package to `2.117.3` and reads `SUPABASE_URL` and
`SUPABASE_SERVICE_ROLE_KEY` from the Edge Function environment. Keep the service key on the server.
The Android app receives only the public project URL and publishable key.

Set this function's gateway `verify_jwt` to **false** at deployment. The function explicitly calls
Supabase Auth `getUser(token)` for every protected request, and it trusts token claims only after
that exact token has been verified. This also supports projects using current asymmetric signing
keys. Disabling gateway verification does not allow unauthenticated profile creation.

The foundation schema and version 1 of `bootstrap-user` were deployed to the designated project on October 13, 2026.
The transactional RLS checks passed on that database and rolled back all synthetic fixtures.
Anonymous HTTP requests to both bootstrap and the office directory returned 401. Google provider configuration
and an actual assigned-account Android sign-in still require setup and device testing.

## Sign-in rules

Enable the Google provider with the institution-approved OAuth client. Add this project's Auth
callback URL in Google Cloud:

```text
https://uuvexkewiuxdbuelwogy.supabase.co/auth/v1/callback
```

Allow the Android callback, including its generated nonce query parameter, in Supabase Auth
redirect settings:

```text
goserve://auth/callback?nonce=*
```

The app's `hd=tip.edu.ph` parameter is an account-selection hint. The server requires a confirmed
Supabase Auth email, a Google provider identity with `email_verified: true`, and an exact
`tip.edu.ph` email domain before creating a student profile. It derives a neutral display name
from the verified email local part. Request bodies and user-editable metadata never select roles,
office assignments, capabilities, demo status, or display names.

Authentication methods come from verified token `amr` claims. A password session requires an
existing, server-provisioned profile. Google sessions may access only student profiles; linking
a Google identity to a staff or teller account does not make Google login valid for that account.

Create authorized demo/student/staff/teller password accounts separately in the Supabase Dashboard
or through a trusted administrator. Provision their matching `public.profiles` rows with the same
Auth user UUID. Set `is_demo=true` for demo accounts; select the intended role and any assigned
office or staff capability there. This source does not seed accounts or passwords. Turn off
unneeded providers and disable unrestricted email/password signups in the deployed Auth settings.
An authenticated account without a provisioned profile cannot access the office directory.

## Android contracts

`POST /functions/v1/bootstrap-user` requires `Authorization: Bearer <Supabase access token>`.
It returns one JSON object:

```json
{
  "id": "<authenticated-user-uuid>",
  "email": "<verified-or-assigned-email>",
  "display_name": "<server-profile-name>",
  "role": "student",
  "office_id": null,
  "staff_capability": null,
  "is_demo": false,
  "consent_accepted_at": null
}
```

Allowed role values are `student`, `staff`, and `teller`. Controlled errors use HTTP 401 for invalid
authentication, 403 for disallowed accounts, 405 for unsupported methods, and 503 for missing
configuration or temporary service failures. Responses are not cached and do not expose raw
service errors or tokens.

`POST /rest/v1/rpc/accept_privacy_consent` takes `{}` and returns the same object with a recorded
`consent_accepted_at`. The RPC has an empty search path, binds every change to `auth.uid()`, and is
executable only by `authenticated`. Its update and private audit row commit atomically. Repeated
acceptance preserves the original timestamp. Clients cannot change profile roles, office scope,
capabilities, demo status, or consent timestamps directly.

The directory request is:

```text
GET /rest/v1/offices?select=id,name,campus,status,note,category,is_demo&active=eq.true&order=name
```

RLS restricts this to active offices and authenticated members with a profile. Office status values
are exactly `Open`, `On Lunch Break`, `Cut-Off Reached`, and `Temporarily Closed`. Status actor and
time columns are reserved for trusted server publishing. This foundation grants no client office
write privileges and implements no request, payment, booking, pickup, or AI operations.

## Deployment verification

Run the source handler checks with Node.js 24 or later:

```text
node backend/tests/bootstrap-user.test.mjs
```

The checks execute the actual TypeScript handler with fake Auth/database/network boundaries.
They validate application decisions rather than live Supabase tokens, deployed RLS, or SDK types.
`tests/rls.sql` is a separate database-owner verification script for an installed schema. It uses
synthetic Auth users and profiles, tests permissions and consent, and ends with `ROLLBACK`.
Run the entire script in one session; if an assertion fails, issue `ROLLBACK` before continuing.

After deployment, verify unauthenticated directory denial; denial of password accounts without a
profile; successful assigned-account bootstrap; exact TIP Google acceptance; Google rejection for
staff/tellers and other domains; own-profile access only; denial of all direct profile updates;
consent RPC idempotence and audit timestamp; and preservation of profiles during simultaneous
student bootstrap requests. Inspect Supabase security advisors before enabling real users.

References: [Supabase identity data](https://supabase.com/docs/guides/auth/identities),
[JWT authentication methods](https://supabase.com/docs/guides/auth/jwt-fields),
[Google sign-in configuration](https://supabase.com/docs/guides/auth/social-login/auth-google),
[supabase-js 2.117.3 release](https://github.com/supabase/supabase-js/releases/tag/v2.117.3).
