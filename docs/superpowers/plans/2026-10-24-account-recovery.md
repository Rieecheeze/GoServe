# Account Recovery Implementation Plan

Revised 24 October 2026: recovery codes only, following the user's latest instruction. Earlier TOTP work is superseded and must be removed before delivery.

Goal: required recovery-code setup, safe code-only password reset, immediate ordinary sign-in, and a new APK generated through Android Studio.

Spec: docs/superpowers/specs/2026-10-24-account-recovery-design.md.

## Constraints

No Git commands, commits, pushes, worktrees or PRs after checkpoint e62418c without another user command. Preserve the six demo credentials, roles, CCS secretary routing and configured keys. New registration is verified exact-domain TIP Google. No SMTP/TOTP/Authenticator app. Serialize native/SQL heavy runners and freeze native source while compiling.

## Task 1: Backend

Owner: revision_auth_core.

- [ ] Revise backend/recovery-contract.md first; coordinate exact DTOs with Android owners.
- [ ] Remove new TOTP/OTPAuth/Vault-secret enrollment code and tests. Keep ten independent 160-bit owner-bound single-use codes with globally reserved hashes.
- [ ] Fixed authenticated status/generate_codes/acknowledge_codes; require recent normal authentication for generation. Exhaustion or missing acknowledgment gates protected access.
- [ ] Fixed public verify/reset/result with safe errors, durable throttles, five-minute verifier-bound grant and serialized/reconciled mutation.
- [ ] Preserve current Terms/session checks; retire old public registration and SMTP routes. Separate readiness activation.
- [ ] Observe RED, run focused/full backend tests and type checks, document additive deployment order. No live mutation by agent.

## Task 2: Android Client

Owner: revision_seryo_chat.

- [ ] Match the revised contract; remove Authenticator DTOs, enrollment/secret APIs and method selection.
- [ ] Implement strict status/code/grant/job validation, memory-only sensitive state and account-generation cancellation.
- [ ] Retire app SMTP/public email registration/deep-link paths; preserve ordinary password sign-in and Google OAuth.
- [ ] Implement setup_recovery routing and normal sign-in only after authoritative completed reset.
- [ ] Observe RED and run focused client tests in the serialized native slot.

## Task 3: Android Screens

Owner: revision_ui_shell.

- [ ] Remove Authenticator/QR/manual-key screens and Account Authenticator action.
- [ ] Required generate/save-code setup; later Account → Recovery Codes shows count and a replacement action after recent normal sign-in.
- [ ] Forgot Password asks only email and saved code; password change uses the same proof. Explain no external app.
- [ ] Preserve flat surfaces, Title Case, icon-only navigation, preview isolation, and narrow/large-font/keyboard behavior.
- [ ] Test initial generation/ack, exhaustion, lost response, protected replacement, pending query-only reset and canonical sign-in; fresh screenshots.

## Task 4: Integration And Delivery

Owner: root.

- [ ] Review code and obtain fresh independent security review after owners finish.
- [x] Revise concrete Terms/operating-policy bundle for code-only recovery and actual Seryo attachment behavior; preserve prior accepted versions. User approved the concrete v2 bundle on 24 October 2026. Live activation awaits verification.
- [ ] Verify ingress/session/provider behavior, then deploy reviewed additive schema/functions. Activate readiness only with the updated app available.
- [ ] Recheck six demo logins without changing credentials, inventing profiles or accepting participant agreements.
- [ ] Run native/backend affected gates, light/dark/narrow/large-font/keyboard visual checks, and live anonymous/cross-owner/retired-route checks.
- [ ] Generate final APK through Android Studio, verify hash and provide exact path. Report remaining participant-owned setup and actual test limits.
