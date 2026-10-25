# Device Test Revisions — 23 October 2026

## Authorized Checkpoint

The existing app, backend, scripts and documentation were pushed before functional revisions. GitHub branch `codex/v2.3-implementation` and local HEAD both resolve to `e62418cf43afa99e3e41f8b379c81239d169567c`. Local configuration, secrets, generated APKs, caches and temporary files are excluded.

All subsequent revisions remain local. Do not commit, push, create a PR or perform another Git mutation without a new user command.

## Acceptance Checks

1. All six approved demo logins authenticate and resolve their trusted role and office; the student has DEP-CCS and department contact remains secretary-only. Do not fabricate real users' consent.
2. SUPERSEDED: the user replaced SMTP/email password confirmation with TOTP and unique per-user recovery codes. Authenticator/recovery-code setup is required before new-account onboarding completes. A separate reviewed recovery design must cover server verification, private secrets, one-time codes, rate limits and reset/session issuance. The email-based implementation and its passing tests are historical work, not the final requested authentication flow.
3. Terms headings render bold and body text regular, without Markdown markers. Preserve approved policy content and version.
4. Sign-in fits a standard phone viewport with compact spacing and a smaller Forgot Password action directly below the password. Keyboard and accessibility settings remain usable.
5. Seryo's status, welcome bubble, messages, keyboard and composer do not overlap. Preview accepts messages safely without anonymous live AI access; authenticated testing exercises the guarded provider.
6. Chat's + menu contains Attach File and Open Camera. Files remain validated and private; unsupported provider file-reading functionality must not be presented as implemented.
7. More groups Account, Seryo AI, Terms And Conditions and About. Account routes to information/photo/password controls. Seryo groups settings/privacy/help. Appearance follows the groups; logout and exit preview remain available.
8. Present AI consent before first actual use and show the recorded choice read-only in the privacy summary. Resolve any conflict with the existing withdrawal policy explicitly.
9. Flat, consistent light/dark surfaces; Title Case headlines; existing icon-only capsule navigation and accessible touch targets retained.
10. Focused regressions, full affected native/backend gates, live access checks and screen renders pass. Build the final APK through Android Studio and report its exact path and hash.

## Ownership And Execution

- `revision_auth_core`: authentication/password/recovery core and backend fixes; root reviews live rollout.
- `revision_ui_shell`: login, Terms rendering, More/account grouping, headlines and shadows.
- `revision_seryo_chat`: assistant layout, preview chat, attachment/camera controls and guarded chat behavior.
- Root: live account/configuration diagnosis, integrations, serial test execution, reviews and Android Studio APK.

Serialize Gradle and database test runners. Review changes before live deployment. Preserve API keys and private credentials.
