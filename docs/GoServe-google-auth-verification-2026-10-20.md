# Google sign-in verification

Superseding live result: Google is now enabled, both TIP-only Auth hooks are active and the Android cancellation return preserves the nonce. See [current live verification](GoServe-google-auth-live-2026-10-20.md). The disabled-provider/sign-in-pending paragraphs below retain the initial diagnostic history only.

The owner authorized activation of Google authentication in the existing GoServe Supabase project on20October. This does not authorize unrelated provider activation or Git/GitHub work.

Live checks used the existing generated Android debug configuration without printing or changing its publishable key:

- Project `uuvexkewiuxdbuelwogy` is `ACTIVE_HEALTHY` and matches the app URL.
- `GET /auth/v1/settings` returns200, with `external.google=false`.
- A zero-row `profiles` request through the existing app key reaches PostgREST and returns401/42501, permission denied for the table. This proves database API reachability and enforces the existing anonymous profile restriction; it does not prove a signed-in database session. No profile records were requested or returned.
- Starting the app's Google PKCE flow returns400, `validation_failed`, because the provider is disabled.
- The dashboard requires sign-in. Its in-app browser tab was kept open and sign-in requested. No provider setting has been changed yet.
- Android registers `goserve://auth/callback` and `goserve://auth/recovery`. Google authorization uses the callback with a random nonce and an S256 PKCE challenge. The saved verifier, exact callback, nonce, attempt age and one-time consumption are checked before token exchange.

The live account backend also predates the revised app. Its sole deployed Edge Function is `bootstrap-user` version1, which returns the older flat profile. The native parser requires `version`, `profile_complete`, `terms` and `next_action`. Metadata-only inspection confirms that the planned `terms_documents`, `account_view` and `update_account` objects are absent. Therefore provider activation alone cannot prove successful use of the revised app. No schema or function was deployed during this inspection.

Focused Android verification passes104tests with zero failures/errors: AccountFlow12, AccountIntegration13, AuthLifecycle9, PaymentLookup8, PaymentRepository26, TellerPaymentViewModel24 and TellerPaymentInteraction12. These exercise controlled boundaries, not an actual Google account. The saved checker was also executed against the live project and returned the disabled-provider result above. Its first restricted-network execution failed without contacting Auth; the authorized network execution succeeded. No approval review rejected an action.

Fresh full Android gate passes294tests/30suites, zero failures/errors/skips, debug assembly and lint0errors/78warnings in1m50. APK11,589,437bytes, SHA256`db3624717c90914b7a0e6944381be077f91974eb6bb04c3df68211f69f4e4302`. This build is not evidence of a live Google login.

Next, after dashboard sign-in, inspect the saved Google credentials and redirect allowlist, activate the provider when configured, and repeat the start check. Google Cloud must allow the Supabase provider callback `https://uuvexkewiuxdbuelwogy.supabase.co/auth/v1/callback`; Supabase must allow the Android return URL while preserving the nonce. An actual device sign-in with the owner's eligible TIP account must then prove the return, code exchange and account bootstrap. Do not bypass the revised account contract or invent institutional Terms to make that test pass.

Repeat the read-only server-start check after a debug build:

```powershell
& 'C:/Program Files/nodejs/node.exe' scripts/check-supabase-google.mjs
```

The checker prints safe status fields and exits1 while Google cannot start. Exit0 proves only provider-start configuration. It deliberately reports end-to-end sign-in and redirect allowlist as unverified until independently tested. No credentials, tokens, authorization state or account information are printed.

Configuration references: [Supabase Google sign-in](https://supabase.com/docs/guides/auth/social-login/auth-google), [mobile deep linking](https://supabase.com/docs/guides/auth/native-mobile-deep-linking).
