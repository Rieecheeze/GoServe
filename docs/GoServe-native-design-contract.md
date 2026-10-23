# GoServe native design contract

The owner-approved Figma file is the visual authority. This contract records implementation decisions; it does not replace or redesign the supplied screens.

Reading this as: a campus service Android app for TIP Manila students and assigned office users, in the supplied monochrome GoServe visual language, with ENERGY 1 / RHYTHM 1 / MOTION 2. Motion confirms taps and indicates real work; it never delays completion.

## Tokens and reasons

| Decision | Source and purpose |
| --- | --- |
| Light background `#F4F4F4`, surface `#FFFFFF`, text/primary `#171717`, secondary `#595959`, border `#D5D5D5` | Figma sign-in `19:2`; contrasting surfaces group the task without introducing an unrelated palette. |
| Existing dark theme | Preserve the supplied paired dark screens and native appearance controls; verify both themes when changing a shared component. |
| Montserrat ExtraBold heading, Karla labels/body | Supplied Figma typography and existing local fonts preserve GoServe's identity. Authentication heading is 30sp with 38sp line height; labels 14/20; body 16/24. Respect Android font scaling. |
| Authentication card radius 28dp, input radius 16dp, action radius 24dp | Figma `19:2` differentiates grouped content, editable fields and actions. Other screen geometry comes from its own high-fidelity context. |
| Authentication outer/card padding 24dp; card gaps 16dp; field gaps 8dp | Figma spacing preserves reading hierarchy; content scrolls when text/keyboard/phone size requires it. |
| Minimum control hit area 48dp | Existing Android/Figma controls remain usable with touch and accessibility services. |
| Monochrome primary emphasis | Black/white inversion is the supplied deliberate action accent; do not add a decorative brand color. |
| Existing GoServe logo, office icons and local assets | Reuse the owner's supplied identity. No new logo, avatar, stock art or fictional operational content. |
| Capsule navigation and selected-destination label | Preserve supplied navigation hierarchy and accessible route names across font sizes. Reserve bottom/safe-area space. |
| 0.97 button press scale, 80ms press, 120ms release | A small state change provides tactile confirmation without moving surrounding layout or delaying the click. Preserve native ripple and keyboard focus. |
| Loading animation only during active work | Existing brand asset and progress communicate fetching/authentication; stop on completion/detach and respect disabled system animations. |

## Functional and visual checks

New data screens provide actual empty, loading, error and retry states. Missing legal/service/provider configuration is stated truthfully, never replaced with invented content. Forms retain drafts when a transient operation fails.

Use Android layout units and the system insets rather than copying Figma's illustrative status/gesture bars. Verify narrow phone widths, large text, keyboard/Back behavior, focus and TalkBack labels, both themes and reduced animation settings. Record executed checks separately from inspection-only checks.

Original design: https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=14-2

Approved v2.3 workflow overview: https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=305-2
