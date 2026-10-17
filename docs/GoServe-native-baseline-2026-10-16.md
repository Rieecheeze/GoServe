# Native baseline — 2026-10-16

The fresh baseline is **blocked before test execution**. Gradle 8.9 cannot establish a local loopback connection, including after escalation. No current pass/fail count can be claimed.

Run from `C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/android` using the existing SDK configured by `local.properties` at `D:/ANDROID STUDIO FILES`:

```powershell
$env:JAVA_HOME='D:/Android Studio/jbr'
& ./gradlew.bat testDebugUnitTest --offline --rerun-tasks --console=plain
```

Both sandbox and escalated attempts exited 1 with `java.io.IOException: Unable to establish loopback connection`. Inspection identifies the installed Studio runtime as OpenJDK 25.0.3, rather than the assumed JDK 17.

Retried with the existing JDK 21 and no persistent daemon:

```powershell
$env:JAVA_HOME='C:/Program Files/Amazon Corretto/jdk21.0.12_12'
& ./gradlew.bat testDebugUnitTest --offline --rerun-tasks --no-daemon --console=plain
```

The sandbox attempt exited 1 because the Gradle wrapper lock was inaccessible. The escalated attempt exited 1 with the same loopback exception. Gradle reported that JVM settings still require a single-use daemon. No dependencies were installed or downloaded.

The existing XML files remain dated **2026-10-15 00:05:52 UTC**. They contain 12 suites, **41 tests, 39 passed, 2 failures, 0 errors, 0 skipped**. These are historical results, and both failures match the prior stored record:

| Test | Source location | Recorded assertion |
| --- | --- | --- |
| `CapsuleNavigationTest.allSixStudentDestinationsHaveTouchTargetsAtLargeText` | `android/app/src/test/java/ph/edu/tip/goserve/ui/CapsuleNavigationTest.java:52` | Expected selected label visible (0), actual gone (8). |
| `SeryoInteractionTest.homePreviewAndOpenedRequestUseTheSameCatalogIdentity` | `android/app/src/test/java/ph/edu/tip/goserve/ui/SeryoInteractionTest.java:100` | Expected `Enrollment certificate`, actual `Certificate of enrollment`. |

Current test inspection confirms that line 52 requires the selected label to be visible and line 100 compares the Home request title against `RequestPreviewCatalog.REQUIRED_ID`. The current resources still contain differing titles: `strings_requests.xml:40` supplies `Enrollment certificate`, while `strings_logic.xml:21` supplies `Certificate of enrollment`.

Restore host Gradle loopback operation and rerun the offline command to establish a fresh baseline. After revised Figma UI approval, the minimal repairs are to align the navigation assertion with the approved label behavior while retaining touch-target/accessibility checks, and to use the same catalog identity for Home preview and request detail. The catalog identity test should remain meaningful. Neither repair was applied.

Only this report and its JSON counterpart were written. Native/backend source edits, provider calls, installs, secrets, and commits were outside this baseline task.
