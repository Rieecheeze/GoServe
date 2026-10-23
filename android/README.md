# GoServe Android v2.3

Open this **android** directory in Android Studio. The project uses Java, XML and ViewBinding. `AuthActivity` hosts accounts and recovery; `MainActivity` hosts role navigation. The [23 October activation checkpoint](../docs/GoServe-test-activation-checkpoint-2026-10-23.md) records current verification and remaining live release gates. Older delivery reports describe earlier milestones.

## What this source implements

- Light/dark design resources, bundled Montserrat ExtraBold headings, Karla body/UI fonts, supplied brand logos and native Android widgets.
- Responsive sign-in, Student registration/profile/Terms, Google OAuth with PKCE, password recovery/change, private profile photos and theme settings.
- Authentication resumes after process restart, queues browser callbacks during restoration, prevents callback replay on recreation and keeps consent failures retryable.
- Server-returned student/staff/teller routing, exact project status types, loading/error states and a clearly labeled debug design preview.
- Supabase account/office schema, RLS, trusted account operations and private photo source in `../backend`.
- Six student destinations with responsive capsule navigation and a direct AI Chat tab for Seryo.
- Canonical service/fee review, idempotent request submission, private evidence, request history, scoped office queue and reviewed transitions.
- Office availability/notes, announcements and service/fee editors; current directory and active announcements; shared authenticated Realtime with account and reconnect guards.
- Teller cash confirmation/history, server-issued physical pickup QR/manual review and explicit handover; verified PDF release, download acknowledgement and explicit student receipt.
- Shared inquiries/read states/notifications, Guidance and matching CCS Secretary consultation entry points, atomic server booking/cancel/reschedule operations.
- Animated Seryo chat avatar, separate consent/retry, local unsent draft/attachment controls and truthful readiness. Startup uses the brand logo without a mascot.

The approved test Terms and operating policies are active. Private request-file/download functions and Seryo endpoints are deployed. Request-file cleanup checks every five minutes for files whose request has been terminal for 30 days. Each participant must still accept Terms and complete their own profile. Staff must configure actual test services and schedules; none are fabricated. No preview or fixture bypass is used for a live account.

The Android app contains no merchant or AI secret. Seryo is GoServe's app persona/agent, connected to the existing eGovAI API through the authenticated backend. Only a fixed supported service intent and approved public facts reach the provider; raw messages are not forwarded. Identity questions are handled locally and disclosure checks run before replies reach chat. A separate custom engine is optional. Temporary AI file analysis remains disabled. The eGovPay checkout and callback/reconciliation flow is deployed and enabled for supervised tests. The actual provider sandbox returned TEST/PAID for the approved diagnostic transaction, which is not an app-bound payment. A browser return cannot declare payment success. The [final rollout record](../docs/GoServe-test-rollout-final-2026-10-23.md) lists current verification and first-test steps.

## Build setup

Compile/target SDK 35, minimum SDK 26 (Android 8.0), Gradle 8.9, Android Gradle Plugin 8.7.3, Java source level 17. JDK 17 or 21 works with this Gradle version. Do not select Studio's bundled JDK 25 for Gradle 8.9.

Use the SDK already configured by `local.properties` or `ANDROID_HOME`; preserve existing values. On another PC, select that PC's SDK in Android Studio and choose a compatible Gradle JDK. Final local checks use the installed Adoptium JDK 21. The launcher discovers compatible installations without changing the saved configuration.

```powershell
# From the GoServe workspace root (uses installed tools and cached dependencies):
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1
# An existing Gradle installation can live anywhere:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -GradleHome C:/Tools/gradle-8.9 -JavaHome C:/Tools/jdk-21
```

The committed launcher discovers a compatible installed JDK 17–22, prefers the local Gradle 8.9 installation, then checks the standard wrapper distribution cache. It runs unit tests, debug assembly and lint offline with one worker. It preserves local settings and restores its process environment when finished. It does not install tools or download dependencies. The explicit quoted, forward-slash workspace socket directory avoids the Windows Java temporary socket-path failure.

For a new workstation, install a compatible JDK and Android SDK 35 through the normal tools, and point `local.properties` or `ANDROID_HOME` to that SDK. The standard `gradlew`, `gradlew.bat`, wrapper JAR and SHA-256-pinned Gradle 8.9 distribution URL are committed. If Gradle/dependencies are not cached yet, perform this **manual first online build** from the repository root before using the offline launcher:

```powershell
$env:JAVA_HOME = 'C:/Tools/jdk-21' # Use your existing JDK directory.
$env:GRADLE_USER_HOME = Join-Path $PWD '.tools/gradle-cache'
$socketDirectory = (Join-Path $PWD '.tools/tmp').Replace('\', '/')
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
$env:JAVA_TOOL_OPTIONS = '"-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"'
& ./android/gradlew.bat -p android --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

That explicit bootstrap downloads the official wrapper distribution and required dependencies; the offline launcher subsequently discovers the cached distribution. An already installed Gradle 8.9 directory can instead be supplied with `-GradleHome`. The APK output is `app/build/outputs/apk/debug/app-debug.apk`. Physical-device verification of installation, browser return, keyboards and system bars remains a separate gate.

## Supabase setup

The project is configured for `https://uuvexkewiuxdbuelwogy.supabase.co` using its **public publishable key**. Public keys are intended for clients; all authorization is still enforced by server authentication and RLS. No secret/service-role key belongs in this app.

1. The foundation schema and `bootstrap-user` are already deployed to the designated project. On a new project, follow `../backend/README.md`.
2. Enable Google in Supabase Authentication providers. Configure a Google OAuth web client and secret in the Supabase dashboard. Google's authorized redirect URI is `https://uuvexkewiuxdbuelwogy.supabase.co/auth/v1/callback`.
3. Allow the mobile redirect `goserve://auth/callback?nonce=*` in Supabase Auth URL configuration. The application validates exact scheme, host, path, one-time nonce and PKCE verifier before exchange. The pattern permits the per-attempt nonce query. A production App Link needs a verified domain, which has not been supplied.
4. Pre-create assigned/demo Auth accounts through trusted administration and corresponding `profiles` rows. The app has no public password registration. Staff/teller roles can only be assigned through trusted server administration.
5. Google users require verified `@tip.edu.ph` identity and are provisioned as students by the bootstrap function. The Google `hd` hint does not grant authority.

Copy `goserve.properties.example` to `goserve.properties` only to change the public client configuration. Never enter a service-role key, eGovAI access code or payment credential. Existing configuration is preserved. Provider credentials stay in Supabase Vault, and server-only configuration RPCs are unavailable to app clients.

## Responsive behavior

390 x 844 is the Figma reference, not a fixed Android canvas. Content fills phone width with design margins, centers within a 600dp maximum on wider windows, uses `sp` text, and scrolls when height is limited. Home cards switch from two columns to one below 360dp or above 1.3 font scale. Bottom navigation uses six centered icons with native accessibility labels and long-press tooltips. System/keyboard/cutout insets are applied to Activity roots.

Tests passed at widths 320, 360, 390, 412 and 480dp with font scales 1.0, 1.3 and 2.0: 105 layout scenarios. Six native Robolectric screenshots cover light/dark sign-in and home plus compact screens with doubled text. These renders use Android API 34, not a physical phone.

## Verification

October 23, 2026: the full gate passed 1,003 Android tests across 84 suites, debug APK assembly and lint (zero errors, 141 warnings). The full backend suite passed 403 checks. Nineteen deployed access/callback/return checks passed, with zero financial events created by forged callbacks. Light/dark payment and icon-only navigation screenshots were inspected, including 320dp with doubled text. These are Android runtime test renders, not a physical-device test. A complete app-bound live payment still requires participant onboarding and a staff-configured test service. The prepared native PostgreSQL race harness could not execute because Windows blocked its existing binaries; deterministic SQL replay and cancellation tests passed. See the final rollout record for evidence paths and limits.

## Design and asset sources

Figma file `SvT9gBUFjdLgbrJ80LPDno`, page `14:2`; inspected frames are listed in the architecture document. Office illustrations are native XML shapes. Montserrat and Karla font licenses are in `third-party` and packaged under `assets/licenses`. Original logo SVGs are preserved in `brand`; Android uses theme-qualified native vectors. No reference screenshots are embedded as app assets. The complete hierarchy and logo rules are in `../docs/GoServe-typography-and-brand.md`.
