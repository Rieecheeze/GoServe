# GoServe Account Recovery Design

Status: revised on 24 October 2026 to follow the user's explicit instruction: recovery codes only; remove TOTP and any requirement to download an Authenticator app. This replaces the earlier Authenticator design. Implementation and verification remain outstanding.

## Requested Outcome

New student registration uses a verified exact-domain tip.edu.ph Google identity. Existing provisioned and demo password sign-in remains available. Password reset uses a previously saved, account-bound recovery code. It does not use SMTP, TOTP, an Authenticator app or a new second-factor prompt during ordinary sign-in.

While signed in, the person generates and saves ten unique recovery codes. Initial generation and an explicit I Have Saved My Codes acknowledgment are required before protected Home access. More → Account → Recovery Codes shows the remaining count and a replacement action. Plaintext appears only when a new set is generated.

Forgot Password asks for the account email and one saved recovery code. Once proof is verified, the person sets a new GoServe password. Only after an authoritative completed reset does Android perform its ordinary password sign-in and apply all normal account gates.

## Registration And Access

- Keep verified exact-domain TIP Google checks in Before User Created and Access Token hooks. The chooser's hd hint is not authorization.
- Retire public student email/password creation, including the old service-role Edge path. Preserve existing provisioned credentials and roles.
- After current Terms acceptance and profile completion, account_view returns setup_recovery until an active code set is acknowledged and has at least one unused code.
- Enforce the same readiness in the protected actor helper. Existing demos complete their own setup; do not fabricate acceptance, profiles, codes or acknowledgment for participants.
- Status and acknowledgment need a current session. Generating or replacing codes also requires normal password or TIP Google authentication within the previous five minutes.
- A person who uses their last code can reset/sign in, then must generate and save a new set before protected Home access.

## Unique Recovery Codes

Generate ten independent codes from 20 cryptographically random bytes each: 160 bits, Base32, grouped for readability. Reserve globally unique domain-separated SHA-256 digests, including consumed/revoked history. Retry generation only on a transactionally rolled-back uniqueness conflict.

Store owner, generation, digest and used/revoked state. A code must match both its owner and active generation. Consume it atomically at successful verification; concurrent requests cannot reuse it.

Generation invalidates older unused codes and their unclaimed reset grants through a security-version change. Regeneration requires recent normal authentication; it does not require TOTP or an old recovery code. Do not retrieve or store old plaintext.

Show plaintext once in memory after generation. Never log it, add it to analytics/preferences or automatically save/upload a screenshot. Explain secure saving and the device/cloud-sync implications of a screenshot. Require explicit saved-code acknowledgment. If the generation response is lost, require a protected replacement set; the server cannot redisplay it.

## Password Reset Boundary

1. Android creates a cryptographically random memory-only installation verifier and sends its S256 challenge with the account email and saved code.
2. The server resolves Auth-owned canonical account identity. Unknown accounts, absent setup and wrong/used codes receive the same invalid-proof response. Recovery never creates an account.
3. Durable account and trusted-ingress attempt limits apply: initially five per normalized account and thirty per trusted client IP per fifteen minutes. A supplied forwarded header cannot create an unrestricted bypass. Verify the hosted ingress trust before enabling recovery.
4. Correct proof atomically consumes the code and issues a five-minute, 256-bit opaque reset grant, stored only as a digest and bound to owner, password-reset purpose, security version and app verifier.
5. Validate the new password and request bounds before claiming the one-use grant. Serialize password mutations per account.
6. A server-only Supabase Admin call changes only the selected owner's password and a namespaced operation marker. Never accept a caller-selected user ID, role, office or arbitrary metadata.
7. Return completed, pending or failed with the bound operation ID, never an unrestricted session. Android signs in normally only after completed. A failed sign-in remains an honest sign-in state.

## Races And Session Revocation

Database and Auth mutations cannot share a transaction. A provider timeout must not release a consumed grant or repeat a password update. Preserve the same operation and query/reconcile its server-owned marker. Pending work blocks another password mutation until its outcome is known.

Use provider password-update session invalidation and a GoServe session_id/owner membership check against auth.sessions. Verify actual deployed behavior before readiness enforcement. No custom Auth triggers.

All inputs have bounded JSON and exact fields. Responses use safe allowlisted errors and no-store. Never log passwords, code plaintext, grants, sessions or email-bearing recovery bodies. Private audit contains only owner/operation IDs, event and server time.

## Fixed Interfaces And Isolation

Backend owner publishes the exact revised backend/recovery-contract.md first.

- Authenticated recovery-control: status, generate_codes, acknowledge_codes. Server identity selects the owner.
- Public account-recovery: verify with email/code/S256 challenge, reset, result. No method selector or TOTP path.
- Account password change uses the same recovery-code reset proof and normal sign-in.
- Retire app SMTP actions and old email-recovery deep links; preserve Google OAuth callbacks.
- No OTPAuth/Vault authenticator secrets, QR/manual key, accepted time counters or factor replacement code remain in this new recovery implementation.
- Recovery tables/RPCs use private schema, RLS, narrow service grants and fixed operations. Neither Android nor Seryo gets unrestricted access.
- Preview never reads, creates or acknowledges real codes.

## Rollout And Policy

Apply reviewed additive schema/functions with readiness staged separately. Build Android support first, then enforce the gate; older APKs require the new version. Preserve existing credentials and unrelated services. No further Git operations or pushes without the user's command.

Update the concrete draft test Terms/operating-policy version for code-only recovery and present it before activation. Preserve already accepted versions unchanged.

## Verification

Prove global code uniqueness, cross-owner denial, atomic single use, stale generation rejection, exhaustion gating, recent authentication for generation, rate limits and spoofed headers, expired/mismatched/replayed grants, uncertain/concurrent mutation reconciliation, revoked-session denial, current Terms gating, TIP Google-only registration and retired signup/SMTP paths.

Verify Android exact DTOs, account-switch cancellation, setup/save acknowledgment, count-only later visits, lost-response handling, code-only Forgot Password, pending query-only reset, normal sign-in, preview isolation and narrow/large-font/keyboard layouts. Complete remaining screenshot fixes and generate the final APK through Android Studio. Do not claim live participant enrollment or physical-device reset testing without evidence.
