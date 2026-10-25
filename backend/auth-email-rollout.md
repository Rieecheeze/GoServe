# Retired Email Recovery Routes

The codes-only recovery release replaces this earlier rollout. `recovery-request` and `password-change` now fail closed with `RECOVERY_METHOD_REQUIRED`; they send no messages and cannot update a password. The earlier SMTP, email OTP, template and redirect instructions no longer apply.

Use the [recovery codes contract](recovery-contract.md) and [ordered rollout](recovery-rollout/README.md). Preserve existing project keys and unrelated Auth/provider settings. This release needs no SMTP or external authenticator application.
