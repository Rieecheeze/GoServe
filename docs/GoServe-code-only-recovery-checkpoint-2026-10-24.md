# GoServe Code-Only Recovery Checkpoint

Updated 25 October 2026. The current release record is [GoServe Revision Delivery](GoServe-Revision-Delivery-2026-10-25.md). It supersedes the packaging/activation-pending notes below, which are retained as history.

## Final Packaging And Activation

Android Studio Generate APKs completed successfully; Build Output shows `BUILD SUCCESSFUL in 15s`. The shareable file is `output/android/GoServe-v2.3-test-2026-10-25.apk`, 12,611,564 bytes, SHA256 `cfbdb0d6215396916222d77b008edff3907fd50d367cdb07a83876b14f512498`, version2.3-test/code2. APK v2 signature verification and packaged public Supabase configuration passed. Proof: `output/validation/android-studio-apk-success-2026-10-25.jpg`.

The approved v2 Terms activation SQL was executed after packaging. Readback proves v2 active with exact digest `d000c6b52c8788f44eedfad8b4a2f1a2a1bfea1ed3241b513ad6f6db91a618ea`; v1 is inactive and its content/digest are unchanged. The separate readiness gate is enabled; both enforcement flags are true. All six demo logins, trusted scopes and revoked-session denials passed again after activation (`output/validation/demo-login-code-recovery-2026-10-25.json`). Each participant must still accept Terms, complete their own profile where required, and explicitly save codes. No live participant password mutation, code acknowledgment or Terms acceptance was fabricated.

Final tests:1,028 Android tests,0failures/errors/skips; lint0errors/189warnings. Backend437 checks pass. The artifact is for supervised testing; eGovPay remains TEST MODE. Real participant Google/recovery phone acceptance and separate-connection PostgreSQL contention remain unverified. No further Git commands were run. Sources were unchanged after the final full test gate. No code-writing or test runner remains active.

## 25 October Continuation

Latest packaging checkpoint: the final full Android unit/lint run completed successfully in 4m44s. All 1,028 tests passed with zero failures, errors or skips; lint reported zero errors and 189 warnings. Evidence: `tmp/codes-only-android-release-gate.log` and the XML results under `android/app/build/test-results/testDebugUnitTest`. No production source edits followed that run.

Android Studio is now open with the correct GoServe `android` project. The user handled the Trust Project dialog, including the Defender option. Project import is in progress. Generate the revised APK through the IDE after import, verify its version and hash, then activate the exact approved v2 Terms and readiness gate. No new APK or policy activation is claimed at this checkpoint.

The CF-only ingress correction is deployed and the user-approved setting is0. Live proof now passes for two independently controlled clients: the first client's three accepted variants share one bucket matching platform CF logs; two CF-header spoofs are denied upstream; the second client's two variants share a distinct matching bucket. Proof: output/validation/recovery-ingress-verified-2026-10-25.json. Fourteen live public-boundary checks also pass, including retired routes, no-session denial, no TOTP selector and the five-attempt account limit. The older disabled-state and correction-pending paragraphs below are historical.

Full backend after the header correction passes437 checks (207 runner tests plus230 custom assertions). Full Android passed1025 tests and lint0errors/189warnings, then a final expired-Google-attempt cleanup race was reproduced and fixed;45 related auth tests pass. The final full unit/lint run including that last guard is now session75172, log tmp/codes-only-android-release-gate.log. Native sources are frozen and all workers are finished.

Approved v2 activation SQL is prepared at backend/recovery-rollout/activate-approved-terms-v2.sql, SHA256 d000c6b52c8788f44eedfad8b4a2f1a2a1bfea1ed3241b513ad6f6db91a618ea. It has NOT been executed. Active Terms remainv1 and recovery readiness remainsfalse until the revised APK is available. No live recovery codes or participant acceptances were created. The six demo login/revocation proof remains valid; passwords are preserved.

Android Studio is installed at D:/Android Studio/bin/studio64.exe but currently closed. After final gates, launch it with Computer Use, select/open the GoServe android project and Generate APKs through the IDE. Then verify/copy the new APK, activate exact approved Terms and readiness, rerun demo readiness without accepting Terms, inspect runtime UI if feasible, and deliver exact APK path. No new APK has been built yet for this revision. No Git operations are authorized.

## Current User Decisions

- Recovery codes only. Remove TOTP, Authenticator apps and SMTP from app password recovery.
- Generate ten unique owner-bound single-use codes while signed in, show plaintext only when generated, require explicit saved-code acknowledgment. More → Account → Recovery Codes later shows count and protected regeneration.
- New registration requires verified exact-domain TIP Google. Preserve Google sign-in in light/dark and existing/demo password sign-in.
- User approved the concrete code-only Terms/operating bundle goserve-test-2026-10-24-v2. It is not active yet; do not fabricate participant acceptance.
- Baseline e62418c was pushed as explicitly requested. NO further Git commands, commits, pushes, worktrees or PRs without another user command.

## Live State

- Project uuvexkewiuxdbuelwogy: additive account_recovery_codes migration applied successfully. No TOTP/Vault enrollment was ever deployed.
- Strict session checks enabled; recovery-readiness enforcement remains false until the updated APK is available.
- Seven functions deployed: recovery-control v1, account-recovery v1, register-student v3, recovery-request v4, password-change v2, request-file v3, download-deliverable v3. Old app signup/SMTP endpoints fail closed; file routes now enforce protected readiness.
- All six demo logins and trusted role/office/CCS assignments verified live. Local logout of each verification session caused both bootstrap and recovery status to reject its old bearer. Passwords and participant Terms/profile/code acknowledgment were not changed.
- Active Terms remain goserve-test-2026-10-22-v1. The user-approved v2 files are docs/legal/GoServe-Test-Terms-and-Privacy-v2.md and docs/GoServe-test-operating-policies-v2.md.
- No recovery codes have been generated live.
- User approved saving GOSERVE_RECOVERY_PROXY_HOPS=0 and controlled ingress verification. Automatic approval initially rejected this exact setting; explicit user approval resolved it. Saving 0 succeeded, but LIVE PROOF FOUND XFF SPOOFABLE. As pre-approved on failed verification, the setting was replaced with disabled (SHA25617eb3c...), and public verify now returns503 TEMPORARILY_UNAVAILABLE. Do not reopen with the old XFF implementation.

## Ingress Fix In Progress

The initial probe report is output/validation/recovery-ingress-probe-2026-10-24.json. Baseline and forged XFF both returned422; their platform CF/x-real address digests were identical, but private IP-budget digests differed. A request also forging CF-Connecting-IP returned403 before function execution. Thus rightmost XFF is unsafe.

Backend owner revision_auth_core is writing a narrow RED→GREEN fix: select a single valid CF-Connecting-IP only under explicitly configured activation0, reject other/missing activation; update contract/docs. Do not trust this until a redeployed runtime's private budget digest matches platform-observed CF address across spoof variants and a second controlled client. No new SQL migration is required. Preserve existing keys and disabled state until checked. The user approval covers the setting and verification, with failed checks remaining closed.

## Verification And Source Fixes

- Full backend after file readiness fix:432 checks pass (202 runner tests plus230 bootstrap/SQL/adapter assertions), zero failures/skips. Log tmp/codes-only-backend-file-ready-full.log. Gateway correction still needs fresh focused tests/typecheck and relevant full gate.
- Code-only client enforces strict DTOs, one-use grants, uncertain reset query-only, ordinary sign-in after completed reset, and account-generation cancellation.
- Fresh independent review found/fixed: atomic auth-start CAS; newer Google PKCE pending-state deletion race; file readiness bypass; MainActivity password-change teardown; rotation during pending sign-in; immutable checked-owner handoff.
- Targeted final native host/transport run passed (3 host tests and7 transport tests). Log tmp/recovery-host-oauth-final-green.log. Previous122 focused tests had118 passing before these final auth fixes; Legal table formatting and Seryo reduced-window proof now pass.
- FULL Android unit+lint is running, session81461, log tmp/codes-only-android-full.log. Native source is frozen. No parallel PGlite/heavy runner. Backend owner may run only small Node/Edge/Deno checks while it runs.
- UI revisions include flat surfaces, compact phone sign-in, formatted Terms headings and table categories, grouped More/Account/Seryo settings, attachment/camera-only plus menu, preview send with local consent, and aligned responsive Seryo composer. No AI file analysis is falsely claimed; selected bytes remain local.

## Remaining Work

1. Finish full Android unit/lint; fix actual regressions and reverify affected boundaries. Inspect fresh Terms/More/Account/recovery/light/dark/narrow/font/keyboard captures.
2. Finish and independently re-review the CF ingress correction, redeploy the two recovery functions, verify spoof protection and rate-key identity, then keep approved activation0 only if proof passes.
3. Activate the exact approved v2 Terms without changing old content/acceptances; verify current-version routing. Enable recovery readiness only with the revised APK available.
4. Complete live anonymous/retired-route and revoked-session checks. Actual participant recovery setup and provider password mutation have not been performed. Do not fake acknowledgments. Source evidence for deployed GoTrue v2.197.0 shows transactional password/operation-marker update and session logout; do not label it a runtime password test.
5. Generate final APK THROUGH ANDROID STUDIO, verify new hash/path, and deliver it. Current old APKs do not contain this revision. SDK adb found no connected device; existing GoServe_API_26 AVD can be assessed after heavy gates finish. No new SDK/security changes without need.
6. No further Git work. Preserve cleanup30days/test eGovPay/Seryo integrations and the six credentials.

## Evidence And Recovery

Pre-rollout function/schema backups: output/validation/recovery-pre-rollout-functions-2026-10-24.json and recovery-pre-rollout-schema-2026-10-24.json. Live login/revocation proof: output/validation/demo-login-code-recovery-2026-10-24.json. The saved configuration screenshot reflects the earlier candidate0, not its later disabled state; capture a fresh final proof after successful correction.

Native Supabase authenticated password updates follow provider policy. The GoServe recovery lock serializes its fixed reset endpoints, not every independently authorized native Auth or administrative operation. PGlite Promise.all is serialized; native PostgreSQL concurrency was blocked by Windows0xC0E90002. Do not claim separate-connection contention or physical-device recovery was verified.
