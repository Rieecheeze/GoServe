# Seryo animation

Seryo uses three supplied transparent PNG poses with native Java `ValueAnimator` and Canvas transforms. This animates each whole illustration; it does not independently move eyes, hands or facial parts.

The renderer is [SeryoMascotView.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/common/SeryoMascotView.java). Timings and the XML state attribute are in [seryo_motion.xml](../android/app/src/main/res/values/seryo_motion.xml).

| State | Normal-speed loop | Visible motion |
| --- | --- | --- |
| `LOADING` | 1400 ms | Greeting pose floats up/down, tilts up to 2°, with an indeterminate bar below it |
| `IDLE` | 2800 ms | Idle pose breathes from 1.000 to 1.018 scale with a subtle bob |
| `THINKING` | 1100 ms | Thinking pose tilts up to 3°, bobs, and shows three staggered thinking dots |

For loop phase `p` in [0, 1), the renderer uses `a = 2πp` and `b = (1 − cos(a)) / 2`. Idle scale is `1 + 0.018b`; its vertical offset is `−1.5dp × sin(a)`. Thinking tilt is `3° × sin(a)`, with a vertical amplitude limited to the smaller of 3 dp and 3.5% of the artwork size. Each thinking dot uses `(1 + sin(a − i × 2π/3)) / 2`, so the three pulses are evenly staggered. The periodic formulas join continuously at the loop boundary; breathing slows naturally at both extremes.

Shared button feedback remains in [SharedMotion.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/SharedMotion.java): scale to 0.97 in 80 ms on press and return to 1.0 in 120 ms on release. Disabled controls reset immediately. System reduced motion removes the scale animator.

Animation stops when the view is hidden, detached, its window is hidden, its lifecycle owner stops, its host is inactive, or system animators are disabled. The correct static pose remains when reduced motion is enabled. Re-enabling system animation resumes an eligible view. System duration scale controls normal playback speed. Bitmap decoding is bounded to at most 627 × 627 pixels per pose; a 6 MiB shared bitmap cache stores no Activity or View references.

## Loading screen

The owner-approved Figma revision removes Seryo from startup. [activity_auth.xml](../android/app/src/main/res/layout/activity_auth.xml) now displays the existing 56 dp GoServe logo and “Opening GoServe…” inside the themed loading card. The existing `authLoading` visibility follows real authentication/data restoration work; it blocks underlying content and preserves the authentication accessibility behavior. No mascot or simulated progress appears there. Compact loaders elsewhere retain `BrandLoadingView`.

The renderer still supports `LOADING` for the historical debug component gallery. That state is not used by the revised production startup screen.

## Chat placements and processing state

[AssistantFragment.java](../android/app/src/main/java/ph/edu/tip/goserve/ui/AssistantFragment.java) defaults to an idle avatar beside the introduction in [fragment_assistant.xml](../android/app/src/main/res/layout/fragment_assistant.xml). The alternative header placement remains available. Only the selected placement animates; the header's title relationships remain intact.

```java
AssistantFragment chat = AssistantFragment.create("chat");
chat.setMascotPlacement(AssistantFragment.MascotPlacement.BUBBLE);
// Alternative placement: MascotPlacement.HEADER.
```

The compact avatar occupies 36 × 40 dp. It clips the original illustration rendered in a 64 × 64 dp box at (-14, -9) dp, matching Figma's helmet, hoodie and badge crop. A 10 dp gap separates it from the reply card, with both bottom-aligned. [item_seryo_response.xml](../android/app/src/main/res/layout/item_seryo_response.xml) provides the same geometry for future real message binding; production does not populate example replies. The chat viewport scrolls independently of the fixed header and composer. The plus menu opens exactly Attach a file and AI privacy.

The current provider is unavailable: Send and remote processing stay disabled, and typing or opening panels leaves Seryo idle. File selection requires a real server readiness response permitting attachments; absent or unavailable transport keeps it disabled. Local selection alone never uploads. No production path calls `setProcessing(true)`. A future approved request controller must bind accepted real in-flight work and clear the state for success, error or cancellation:

```java
chat.setProcessing(true);   // Only after a real request is accepted and starts.
chat.setProcessing(false);  // Complete, cancel, fail, or leave the request's account.
```

The existing caption announces that semantic transition once. Avatars do not receive screen-reader focus. Fragment view destruction clears its binding and processing state.

Standalone integrations can call `setState(State.LOADING|IDLE|THINKING)`, `setHostActive(boolean)`, and the `Animatable` `start()`, `stop()`, `isRunning()` API. `stop()` remains stopped across visibility changes until `start()`; normal automatic lifecycle cancellation resumes once eligible.

## Developer preview and checks

The dated results below are historical component evidence. The [22 October delivery report](GoServe-delivery-v2.3-2026-10-22.md) records the latest complete native gate and APK. The local controller now preserves the exact consent decision/version/key through an unknown response, offers explicit retry, cancels an unlaunched picker on stop and retains an already launched picker through recreation. These checks do not activate a provider.

Latest approved-UI proof, 20 October: all 224 native tests pass, debug assembly passes, and lint reports zero errors / 73 warnings. API26 recordings for the compact avatar encode 109 frames in six seconds with motion enabled and one frame during three seconds with reduced motion. Six normal light/dark screens, four 320dp / 2× text screens, and both scroll-to-end captures were inspected. Final guidance and mascot remain reachable with the composer pinned. Display/font/motion settings were restored and the owned emulator stopped. This verifies the approved presentation; live AI/provider and remaining role workflows are still pending.

- [Current idle recording](../output/validation/approved-seryo-ui/approved-seryo-idle.mp4)
- [Current reduced-motion recording](../output/validation/approved-seryo-ui/approved-seryo-reduced-motion.mp4)
- [Current build and device evidence](../output/validation/approved-seryo-ui/native-gate.json)

[MascotPreviewActivity.java](../android/app/src/debug/java/ph/edu/tip/goserve/ui/MascotPreviewActivity.java) exists only in debug builds. It clearly labels all three states as visual previews, reuses the production renderer, and starts no authentication/provider operation. Its exported activity exists only in the [debug manifest](../android/app/src/debug/AndroidManifest.xml); no product navigation opens it.

```text
adb shell am start -n ph.edu.tip.goserve/.ui.MascotPreviewActivity
```

[SeryoMotionTest.java](../android/app/src/test/java/ph/edu/tip/goserve/ui/SeryoMotionTest.java) checks actual pixel changes, animated bar/dots, lifecycle cancellation, state switching, one-loop resumption, static reduced-motion poses and live duration-scale changes. It uses cached Robolectric SDK 34 and manifest NONE. [SeryoInteractionTest.java](../android/app/src/test/java/ph/edu/tip/goserve/ui/SeryoInteractionTest.java) checks truthful idle behavior, both placements, layout/accessibility ownership and fragment destruction; it captures bubble placement at two deterministic phases for narrow large-text/light and normal-text/dark scenarios. [AuthLifecycleTest.java](../android/app/src/test/java/ph/edu/tip/goserve/ui/AuthLifecycleTest.java) holds a real authentication operation to check loading lifetime and underlying-content accessibility suppression.

Fresh verification of the approved revision on 19 October: all four motion tests, all nine chat interaction/layout tests and all nine authentication lifecycle tests pass. The same focused run passed 21 inquiry and 18 consultation tests: 61 total, zero failures/errors/skips, in 2m9s. New checks cover the original-art crop, actual compact pixel motion, 320 dp / 2× text wrapping, a pinned composer, the exact tools menu and brand-only loading. Assembly, lint and fresh device proof are tracked separately in the session ledger.

[SeryoUiPreviewActivity.java](../android/app/src/debug/java/ph/edu/tip/goserve/ui/SeryoUiPreviewActivity.java) is a new debug-only approved-UI fixture. It reuses production layouts, with `screen=loading|welcome|conversation` and `theme=light|dark` intent extras. Example conversation content is confined to debug source. This fixture performs no authentication or provider operations.

The earlier component gallery recording on API26 encoded 233 frames in eight seconds; reduced motion encoded one frame in a three-second observation. These historical recordings predate the compact-avatar UI revision. The original system setting was restored and the task-owned emulator stopped.

- [Native animation recording](../output/validation/seryo/seryo-native-preview.mp4)
- [Native preview screenshot](../output/validation/seryo/seryo-native-preview.png)
- [Reduced-motion recording](../output/validation/seryo/seryo-native-reduced-motion.mp4)

These recordings verify the mascot component on the minimum supported Android version. They do not claim that live authentication, AI-provider requests or the remaining app workflows have been exercised on that device.
