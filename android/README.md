# GoServe Android foundation

Open this **android** directory in Android Studio. The project uses Java and XML ConstraintLayout, not Compose. `AuthActivity` hosts sign-in and privacy consent; `MainActivity` hosts role navigation. [Architecture and feature map](../docs/GoServe-architecture.md) describes the complete planned app and identifies the first delivery's scope.

## What this source implements

- Light/dark design resources, bundled Montserrat ExtraBold headings, Karla body/UI fonts, supplied brand logos and native Android widgets.
- Responsive sign-in, assigned-account email/password, consent, student home, searchable office directory/detail, and theme settings.
- Supabase Authentication only: Google OAuth with PKCE, exact callback validation, assigned-account password sign-in, refreshed sessions and Android Keystore protected refresh tokens.
- Authentication resumes after process restart, queues browser callbacks during restoration, prevents callback replay on recreation and keeps consent failures retryable.
- Server-returned student/staff/teller routing, exact project status types, loading/error states and a clearly labeled debug design preview.
- Supabase account/office schema, RLS, consent RPC and trusted account bootstrap source in `../backend`.
- Six student destinations with responsive capsule navigation and a direct AI Chat tab for Seryo.
- Guarded request list/detail/file design previews with office-declared optional-fee contracts and visible completed-phase checks.
- Seryo chat, privacy and planned attachment panels. eGovAI transport stays disabled pending provider contracts and user procedures.

The screens labeled as upcoming packages are explicit placeholders. Live request submission, private file release, payment processing, staff tools, bookings, messages, Realtime and QR pickup remain unimplemented. Seryo is GoServe's eGovAI chatbot; its visible setup panels do not transmit questions or files. This is not the complete integrated P1 application or a production release.

eGovPay demo credentials are encrypted in Supabase Vault. The Android app contains no merchant token. `BuildConfig.EGOVPAY_CONFIGURED` stays false while callback verification, completed provider statuses and amount canonicalization remain unconfirmed. The server adapter and its offline tests are in `../backend`; it has no active payment endpoint yet.

## Build setup

Compile/target SDK 35, minimum SDK 26 (Android 8.0), Gradle 8.9, Android Gradle Plugin 8.7.3, Java source level 17. JDK 17 or 21 works with this Gradle version. Do not select Studio's bundled JDK 25 for Gradle 8.9.

The local SDK was installed in `../.tools/android-sdk`. `local.properties` points there and is excluded from version control. On another PC, select that PC's SDK in Android Studio. In Android Studio settings, select a compatible Gradle JDK. On this PC, Corretto 21 is installed at `C:/Program Files/Amazon Corretto/jdk21.0.12_12`.

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

Copy `goserve.properties.example` to `goserve.properties` to change the public client configuration. Never enter a service-role key, eGovAI access code or payment credential. Seryo's access code is stored in Supabase Vault, and its server configuration is disabled until procedures and the provider contract are supplied.

## Responsive behavior

390 x 844 is the Figma reference, not a fixed Android canvas. Content fills phone width with design margins, centers within a 600dp maximum on wider windows, uses `sp` text, and scrolls when height is limited. Home cards switch from two columns to one below 360dp or above 1.3 font scale. Bottom navigation uses six centered icons with native accessibility labels and long-press tooltips. System/keyboard/cutout insets are applied to Activity roots.

Tests passed at widths 320, 360, 390, 412 and 480dp with font scales 1.0, 1.3 and 2.0: 105 layout scenarios. Six native Robolectric screenshots cover light/dark sign-in and home plus compact screens with doubled text. These renders use Android API 34, not a physical phone.

## Verification

October 14, 2026: `assembleDebug`, `testDebugUnitTest` and `lintDebug` passed. Fifteen JUnit tests include 131 domain assertions, 105 layout scenarios, six rendered screenshots and seven authentication lifecycle regressions. The lifecycle tests simulate only storage and HTTP boundaries; repository, ViewModel, Activity and consent behavior run as implemented. Android lint reports zero errors and 39 warnings. Backend tests passed 39 bootstrap scenarios and 15 eGovPay adapter tests. Live Supabase checks passed profile privacy, write denial, consent idempotence and server-only eGovPay configuration; synthetic fixtures were rolled back. The merchant token is absent from Android source and the APK. Google provider setup and demo accounts remain pending. No physical device or emulator has been tested yet.

## Design and asset sources

Figma file `SvT9gBUFjdLgbrJ80LPDno`, page `14:2`; inspected frames are listed in the architecture document. Office illustrations are native XML shapes. Montserrat and Karla font licenses are in `third-party` and packaged under `assets/licenses`. Original logo SVGs are preserved in `brand`; Android uses theme-qualified native vectors. No reference screenshots are embedded as app assets. The complete hierarchy and logo rules are in `../docs/GoServe-typography-and-brand.md`.
