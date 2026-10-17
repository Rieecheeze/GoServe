# GoServe Figma content validation

Validated on 15 October 2026 in file `SvT9gBUFjdLgbrJ80LPDno`, page `14:2`.

## Updated native screens

| Screen/state | Light node | Dark node |
| --- | --- | --- |
| New-account Home, five offices, no active request | `19:93` | `36:93` |
| Home with an active request | `127:1124` | `127:1329` |
| All offices, five offices | `33:54` | `36:1766` |
| Documents, Registrar only | `45:2` | `46:2` |
| Payments, Cashiering only | `45:54` | `46:30` |
| New-account Requests, empty | `23:32` | `36:377` |
| Requests, three existing-data preview cases | `127:1254` | `127:1459` |
| Required fee, paid demo, Processing | `23:95` | `36:440` |
| Free, Ready for Pickup/Download | `106:662` | `107:1063` |
| Awaiting office fee declaration, Submitted | `106:663` | `107:1064` |
| Verified file, free preview | `23:144` | `36:489` |
| Sign-in | `19:2` | `36:2` |
| Seryo, open chat canvas and bottom composer | `24:51` | `36:819` |

All original screen IDs were retained. Existing Documents and Payments variants were reused. No color collections or text styles were duplicated. The shared nine GoServe text styles and existing Light/Dark color collections are used throughout the content.

## Verification

- Figma visual review passed for a ten-screen light/dark composition of Home, directory, required request, verified file and sign-in. A second four-screen composition verified both Requests lists and the selected Documents/Payments filter states with the compact, contrasting navigation installed by the parent task.
- The audit covered all twenty screens: zero content font issues, zero text sizing/bounds issues, zero mismatched bound-paint fallback colors and zero image fills. Native text, frames and SVG paths remain editable.
- Text is resized to its fixed width before enabling automatic height. The previous reverse order reset `textAutoResize` to `NONE` and compressed paragraphs to one pixel. Fixed-width grid and filter children are inserted without first expanding them with `FILL`.
- Live light backgrounds are explicitly `#F4F4F4` and dark backgrounds `#080808`; bindings are retained. Paint constructors seed the resolved token value, avoiding black fallback backgrounds in the live canvas.
- Home uses the supplied logo without an adjacent GoServe wordmark, and keeps all five offices in its vertical scroll content. Default new-account Home bodies measure 1254 units inside a 654-unit viewport. Active-request variants preserve the 1442-unit bodies. Directory bodies contain all five offices; filtered variants contain only Registrar or Cashiering as appropriate.
- Content viewports end at y=742, leaving sixteen units before the current 56-unit icons-only navigation at y=758. Long content is contained by fixed scroll viewports and uses growing auto-layout sections and cards.
- Existing-data Requests variants route their three cards to the matching required, free and pending detail nodes listed above. The original/default Requests roots show an empty state. Free requests omit Awaiting Payment. Required and free request progress uses green completed checks and explicit Complete/Current/Upcoming labels.
- Preview notices distinguish sample requests, demo payment records and file verification examples from official records. Verified-file preview uses About downloads and explains that it contains no private file. No real charge, payment confirmation, document download or AI answer is simulated.

Source script: `scripts/figma_goserve_content_v2_2.js`. Navigation components are owned separately by the parent task and are preserved by the content script.

## Current typography revision

The user's newer font choice supersedes Arvo/Lato. The same nine style IDs now use Montserrat ExtraBold 800 for Hero, Section, Toolbar and Office, Karla Regular 400 for Body and Support, and Karla SemiBold 600 for Label, Caption and Navigation. Figma's available font faces were verified before loading; the resulting styles report the requested `wght` values of 800, 400 and 600.

Existing GoServe style references and all current Arvo/Lato text were migrated, covering content, Seryo, authentication and navigation typography without changing navigation structure or unrelated legacy Roboto flows. No Arvo/Lato text remains. Widths, automatic text height and growing parent layouts were preserved.

After the parent task removed navigation labels, the final audit covered 544 remaining Montserrat/Karla text nodes and found no text-height or parent-width violations. A Figma Home/Seryo composition in both themes visually confirmed the new faces, wrapping, readable contrast and compact icons-only navigation. No further wrapping changes were needed.

## New-account state revision

The original Home and Requests IDs remain the default destinations for a new account. No active request or example request is inserted into those states. Home's active-request section was removed from its auto-layout body; the office heading moved from y=568 to y=380 with the ordinary sixteen-unit section gap, leaving no empty slot. Home copy now explains starting a first request.

Requests displays “No requests yet”, explains that requests appear after submission to a campus office, and offers Browse offices. Its 698-unit body fits the 718-unit viewport at 390 units wide. The layout reflows at 320 units wide, including font scale 2, where its 912-unit body scrolls. The light/dark Home and empty Requests composition passed a Figma visual review, and all four original roots passed text, font and container-width checks.

The active Home and populated Requests clones retain the previous content and linked navigation instances. Matching existing-data navigation routes are maintained by the parent task. The cloned request cards preserve required/free/pending destinations. Source migration: `scripts/figma_new_account_states.js`; the main content builder now defaults to new-account states.

## Seryo chat composition revision

Only the two main chat roots were redesigned; privacy, upload and other panels were retained. The native editable layout uses the existing color tokens and shared Montserrat/Karla text styles. At 390×844, the header is x=20, y=24, width=350 and minimum height=64. The chat area is x=20, y=104, width=350 and height=638. Its 468-unit welcome viewport centers a 310×284 content stack with a muted AI icon, “How can Seryo help you?”, campus guidance and Browse offices.

The bottom dock starts at y=588 and is 154 units high. Its 350×130 composer uses a 24-unit radius, with a question placeholder, a 48×48 attachment-information target and a disabled 48×48 Send target. The note explicitly states that questions are not sent while setup is pending. The header's 48×48 privacy target, attachment target and Browse offices action route to existing real panels; Send has no prototype reaction. Existing 56-unit navigation instances remain at y=758.

The light/dark Figma composition passed visual review. Container bounds passed at 320 units wide; centered content is capped at 310 units and fills smaller widths. The header grows with larger text, the dock grows with its text, and the remaining welcome region scrolls. A conservative 300-unit keyboard-reserve test retained a sixteen-unit composer-to-navigation gap. The current unavailable field does not open a keyboard; at font scale 2 that hypothetical reduced viewport leaves only fourteen units for welcome content, so an eventual enabled-input IME layout should collapse the welcome section. This limitation is explicit rather than claiming a usable full welcome under those constraints.

Source composition: `scripts/figma_seryo_chat_v2_3.js`. No Android implementation or Gradle build was performed during this Figma-only review phase.

## Final review candidate — 15 October 2026

The earlier measurements above record prior design checkpoints. Current review status and navigation links are maintained in [GoServe UI review](GoServe-ui-review.md). [Open the editable Figma review index](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=175-7272).

- All 14 gap families are composed: 96 state records and 192 Light/Dark frames, with growing content, role navigation and resolved prototype routes.
- The final current-screen audit covered 332 visible mobile frames and 3,492 ordinary text nodes: zero geometry findings and zero unapproved legacy fonts. Official Google G glyphs `56:53` and `56:81` are explicit brand exceptions.
- 2,280 prototype links resolve to 208 unique existing, visible destinations. The six unsupported OSA roots remain hidden with no incoming active links. No archive page was modified.
- Student, staff and teller navigation now share the 56-unit compact capsule, centered 22-unit icons, 36-unit selected circle, and 48-unit minimum destinations. Student navigation uses a flexible inner row: 320 − 24 outer margins − 8 inner padding = 288 units for six 48-unit destinations.
- Navigation color tokens provide 13.90:1 foreground/background contrast in both themes. Light mode uses the dark capsule; Dark mode uses the inverse.
- Fourteen editable 320-unit phone proofs include Home, Seryo, request review, fee declarations and unpaid file access. They passed geometry checks at their recorded ordinary/simulated 2× text settings. The two fee-editor proofs were checked again after the final copy revision: 34 text nodes, zero findings.
- Default new-account Home/Requests/Inbox/Notifications/My appointments are separate from populated examples. Accounting is labeled Account services; Payments lists Cashiering only.
- Free request review shows Purpose, Notes and Supporting evidence rows. Fee editor examples identify the Registrar service context without inventing a fee amount. Blank required amounts keep Save disabled.
- Main Seryo Send nodes `123:1158` and `123:1192` have no reactions. Provider and future chatbot outcomes are design states only, clearly marked Planned where appropriate.
- Authentication edge flows now use shared Montserrat/Karla styles and growing, scrollable content. Original IDs, routes, platform chrome and official brand lettering are retained.

These are Figma geometry, route and selected screenshot checks, not Android device, system-font-scale, keyboard/IME, backend or AT01–38 execution results. The user must finalize the UI before Android Java/XML implementation resumes.

Sources added for this pass include `figma_ui_gap_renderer.js`, `figma_ui_state_flex.js`, `figma_ui_main_responsive.js`, `figma_legacy_format_v2_3.js`, `figma_ui_legacy_final.js`, `figma_ui_auth_typography.js`, `figma_ui_compact_control_reflow.js`, `figma_ui_student_nav_reflow.js`, `figma_ui_geometry_check.js`, `figma_ui_responsive_proofs.js` and `figma_ui_review_index.js`. Their canonical state input and IDs are recorded in `scripts/figma_ui_gap_states.json` and `docs/GoServe-figma-state-ledger.json`.



## Final review checkpoint — 2026-10-15

The Figma candidate is ready for user review. UI approval is still pending; Android implementation remains on hold.

- 434 screen and proof frames are organized in 217 Light/Dark pairs, including 26 phone/text-size proofs. All 777 tracked source objects are grouped inside 29 Sections and 11 top-level Sections. The grouping operation preserved screen sizes, contents and reactions.
- The canonical general state library contains 100 states / 200 theme frames. Authentication contains 39 cases / 78 frames, including 11 password-recovery cases / 22 frames. G02.2/G02.3 overlap the authentication set, so these counts must not be added as disjoint totals.
- Forgot password appears directly below Password in three paired login variants (six main frames), with recovery email, validation, sending, neutral receipt, network failure, reset/confirmation, saving, uncertain outcome, expired-link and confirmed-completion designs.
- Student, Staff and Cashiering Settings in both themes open the read-only Terms viewer and return with Back. Draft terms are visibly marked. Reviewing Terms does not repeat registration or change consent.
- Geometry checks passed for 109 changed/auth/context/index frames and proofs: 1,782 ordinary text nodes, zero issues and zero legacy font mismatches. A follow-up check passed for the 22 Home/Requests/Settings frames affected by the last edits: 458 text nodes, zero issues and zero legacy font mismatches. These sets overlap.
- Auth acceptance passed for 78 source frames, 12 auth proofs and six Settings screens: zero findings. All 2,952 prototype links resolve to visible destinations; no missing source objects, loose tracked objects, overlapping top-level Sections, Section overflow or complete-UI raster images were found.
- The requested Home preview banner and Requests demo disclaimer were removed from all 16 matching copies, including Light/Dark variants and responsive examples. The legacy content source and an idempotent cleanup helper preserve that change. The Settings appearance label no longer overlaps its moon icon.
- Screenshots reviewed: Welcome in both themes; dark Terms viewer; and updated light Home, populated Requests and Settings. Targeted spacing fixes passed post-fix visual review.

These results describe editable Figma designs and simulated text enlargement. They do not prove account creation, email delivery, password reset, native session restoration, Android device/IME behavior, provider calls or institutional approval of the draft Terms.

Evidence: [final UI verification](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-final-ui-verification-2026-10-15.json). Current IDs: [auth ledger](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-auth-screen-ledger.json), [general ledger](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-figma-state-ledger.json).

