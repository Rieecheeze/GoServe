# Account settings changes

- Added More → Change password using the existing native theme and account input styles. Current/new/confirmation/code values stay in ViewModel memory, survive rotation, and are excluded from view hierarchy state. Discarded views and invalidated accounts clear their fields.
- Added explicit Supabase reauthentication code requests. The code panel appears only when required, and its acknowledgement says the code was requested. Busy requests block duplicate taps and Back navigation. Transport or malformed password-update responses never produce a success notice or automatic retry.
- Added trusted one-time Sign-in feedback after confirmed or uncertain password updates. Password recovery remains a separate, verified recovery-context flow. GoServe password copy does not imply changing a Google password.
- Added nullable, trusted Student department affiliation. The CCS demo uses `DEP-CCS` with the Student role and null office/capability. Profile edits preserve affiliation, older account responses remain compatible, and user metadata cannot assign it. The migration does not backfill Students or create demo users.

Native implementation:

```text
android/app/src/main/
  java/ph/edu/tip/goserve/
    data/AuthRepository.java
    domain/SessionProfile.java
    ui/PasswordChangeViewModel.java
    ui/PasswordChangeFragment.java
    ui/MoreFragment.java
    ui/AuthViewModel.java
    ui/SignInFragment.java
    ui/SharedMotion.java
  res/layout/
    fragment_password_change.xml
    fragment_more.xml
    fragment_sign_in.xml
  res/values/
    password_strings.xml
    profile_strings.xml
backend/
  migrations/20261020162841_profile_department.sql
  tests/profile-department.test.mjs
```

The new screen uses `SharedMotion.install`: press scale0.97 over80ms, release scale1.0 over120ms. Disabled controls return to scale1.0. Reduced-motion system settings remove these transforms. Loading feedback uses the existing logo-only `BrandLoadingView`; the approved initial loading screen keeps its mascot-free design.

Local proof covers password API/session boundaries, retained ViewModel work, explicit email actions, rotation, discarded-view clearing, safe feedback, account replacement, both themes and320dp/2x text. Six new SQL department cases observed RED before implementation, then passed; the full backend278 gate passed. Native build evidence and current APK identity are recorded in `output/validation/native-gate-2026-10-21.json` once the final visibility check/build passes.

Live boundaries remain explicit: only the separately authorized Google Auth hooks/configuration have been deployed. The ten core migrations and revised account Edge Functions are pending. No real password update, recovery email delivery, personal Google sign-in or authenticated app-data session is claimed. No demo users/passwords have been created; the earlier access approval remains pending. Owner-scoped profile-photo changes are the next settings task. Git/GitHub remains deferred.
