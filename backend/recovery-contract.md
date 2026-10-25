# GoServe Recovery Codes API

This contract supersedes the earlier Authenticator draft. Recovery uses saved codes only: no TOTP, authenticator application, QR, Vault secret or SMTP.

All endpoints accept POST with Content-Type application/json, at most 8192 UTF-8 bytes and exactly the documented fields. Responses use Cache-Control no-store. Errors are {"ok":false,"error":{"code":"ALLOWLISTED_CODE"}}; private provider messages are never returned. Sensitive values remain in app memory and never enter logs or analytics.

## Authenticated recovery-control

Use the current GoServe bearer. The server derives its owner from verified Auth/session identity. status and acknowledge_codes require a current session. Only generate_codes requires first-factor authentication within five minutes: repeat normal password or TIP Google sign-in if necessary. Regeneration does not require an old recovery code; fresh legitimate first-factor authentication is the authority. Generation and acknowledgment also require the current Terms and completed profile, without recursively requiring recovery readiness.

Status request: {"action":"status"}.

Status response: {"codes_configured":false,"codes_acknowledged":false,"remaining_codes":0,"generation":null,"requires_setup":true}. These are exactly five keys. generation is the active set UUID or null. No old plaintext is retrievable.

Generate/regenerate request: {"action":"generate_codes"}.

Generation response: {"generation":"UUID","codes":["XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"],"remaining_codes":10,"codes_configured":true,"codes_acknowledged":false}. These are exactly five keys. There are exactly ten independent 160-bit codes. Plaintext is returned once; all earlier unused codes become invalid. Historical digests remain globally reserved. A lost response requires a fresh protected generation, never plaintext retrieval.

Save acknowledgment request: {"action":"acknowledge_codes","generation":"UUID"}. Response is the five-key status object. Invoke only after the person selects I Have Saved My Codes. Recommend secure saving; screenshots are their explicit choice and are never automatically saved or uploaded.

## Public account-recovery

No authenticated session is needed. Android generates 32 cryptographically random verifier bytes, encoded as 43 base64url characters without padding. Keep code_verifier in memory and send its SHA-256 base64url digest as code_challenge.

Verify request: {"action":"verify","email":"actual-account-email","code":"one-saved-recovery-code","code_challenge":"43_BASE64URL_CHARACTERS","code_challenge_method":"s256"}. There is no method field. Unknown account, unconfigured recovery and wrong/used codes all return RECOVERY_PROOF_INVALID. Recovery never creates an account. The lookup uses the Auth-owned confirmed canonical email.

Verify response: {"reset_grant":"43_BASE64URL_CHARACTERS","expires_at":"ISO8601_UTC"}. These are exactly two keys. The opaque grant expires after five minutes and binds the owner, purpose, security version and installation verifier. It is not a Supabase session. Its code is consumed atomically on issuance.

Reset request: {"action":"reset","reset_grant":"43_BASE64URL_CHARACTERS","code_verifier":"43_BASE64URL_CHARACTERS","password":"new-password-verbatim","operation_id":"UUID"}. Password rules: at least six code points with a Unicode letter, decimal digit and punctuation/symbol; at most 72 UTF-8 bytes. Validate before claiming the grant. No old password is required, including a Google-only account setting its first GoServe password.

Result request: {"action":"result","reset_grant":"43_BASE64URL_CHARACTERS","code_verifier":"43_BASE64URL_CHARACTERS","operation_id":"UUID"}. This queries/reconciles an already claimed job and never repeats a password mutation. A claimed job can be queried for 24 hours after claim even when its original five-minute deadline has passed.

Reset/result response: {"state":"completed","operation_id":"UUID"}. These are exactly two keys. state is completed, pending or failed. pending means an uncertain provider outcome: retain the same operation/grant/verifier and query result; do not submit a second reset. failed is a definitive rejection and needs a new proof/grant. No session is returned. After completed, Android immediately calls its existing normal signInPassword(email,newPassword); ordinary Terms/profile/recovery/role routing follows. A sign-in failure remains a truthful sign-in state, never Home based on reset alone.

## Readiness, rate limits and errors

account_view adds recovery with the five-key status object above. requires_setup is also true when remaining_codes is zero, so the last-code reset routes to a fresh saved set after normal sign-in. The separately staged gate returns next_action setup_recovery after current Terms/profile checks and before home. Protected SQL actor readiness enforces the same requirement, including unfinished demo onboarding. New student registration requires exact-domain verified TIP Google; existing provisioned password sign-in is preserved.

Verification has durable atomic limits of five attempts per account and thirty per trusted client IP per fifteen minutes, including unknown accounts. The existing GOSERVE_RECOVERY_PROXY_HOPS setting now accepts only the exact string 0: it activates a single valid CF-Connecting-IP address after its hosted provenance has been verified. The name is retained for configuration compatibility; it no longer selects a forwarded hop. X-Forwarded-For and X-Real-IP never select or replace the budget address. Absent, disabled, malformed or nonzero settings, or a missing/invalid/multi-address CF header, fail closed with TEMPORARILY_UNAVAILABLE before SQL budgets or code consumption. Root must match the Edge-selected private budget digest to platform-observed CF ingress and test spoof variants and a second controlled client before enabling public recovery. The local parser tests do not establish hosted header trust.

Public verification returns CONFIGURATION_REQUIRED before consuming a code when strict server session checks have not been enabled. New reset claims enforce the same condition. The rate-limit counters still commit for preactivation verification requests. Existing claimed result queries remain available for reconciliation if readiness is later disabled.

Safe codes: AUTH_REQUIRED, AUTH_INVALID, RECENT_AUTH_REQUIRED, RECOVERY_SETUP_REQUIRED, RECOVERY_PROOF_INVALID, RECOVERY_GRANT_INVALID, RECOVERY_OPERATION_PENDING, PROFILE_INCOMPLETE, TERMS_REQUIRED, RATE_LIMITED, VALIDATION_FAILED, PASSWORD_TOO_LONG, PASSWORD_WEAK, GOOGLE_REGISTRATION_REQUIRED, RECOVERY_METHOD_REQUIRED, CONFIGURATION_REQUIRED, TEMPORARILY_UNAVAILABLE. HTTP 401 is invalid authentication, 429 throttling, 422 rejected input/proof, 409 conflicting/stale state, and 503 missing configuration or temporary failure. Private upstream messages are never returned.
