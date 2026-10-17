# GoServe Figma organization guide

October 15, 2026. File `SvT9gBUFjdLgbrJ80LPDno`, current design page `14:2`.

## Start here

Open the [Start section](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7484), then the [review index](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=175-7272). The index is the entry point for reviewing the current UI candidate. Screen names and original node IDs remain intact, so existing direct links continue to identify the same objects.

The canvas now groups **597 original top-level objects into 11 top-level sections and 29 sections in total**. There are **174 light/dark pairs: 348 visible mobile frames**, plus the review index. Related themes and states are grouped together instead of spread across a single undifferentiated canvas.

## Section map

| Section | Contents / use |
| --- | --- |
| [Start](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7484) | Review index and the starting point for design review. |
| [Components](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7485) | Existing navigation and reusable component definitions. |
| [Authentication](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7504) | Student Google entry, assigned-account access, consent and loading. |
| [Student](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7513) | Four subsections for Home, Requests, Consult and Inbox/Account. |
| [Staff](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7582) | Office work, queues, schedules, inquiries and authorized staff actions. |
| [Cashiering](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7621) | Teller lookup, payment confirmation and history. |
| [Seryo](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7634) | Current inactive chatbot entry and its privacy/setup surfaces. |
| [States](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7660) | Fourteen G01–G14 family sections containing 188 contextual state frames. |
| [Phone proofs](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7863) | Editable compact-width and simulated large-text proof frames. |
| [Demo](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7645) | Explicit demonstration/reference material, separate from initial-account content. |
| [Retained](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7878) | Hidden retained material outside the active review flow. |

### Student subsections

- [Home](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7514): initial and populated Home references and office discovery.
- [Requests](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7535): request creation, tracking, fee/payment context, files and pickup.
- [Consult](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7560): consultation and appointment flows.
- [Inbox and Account](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=189-7565): inquiries, notifications, More and account-related screens.

## What changed, and what was retained

The organization added **395 section/caption nodes**. Original screen, annotation and component IDs and names were retained. Six excluded OSA frames remain hidden, and 18 obsolete canvas labels remain retained but hidden. These items are not part of the active mobile review flow.

The complete state inventory remains **192 light/dark frames**. Four default Inbox/Notifications frames stay in the main Inbox and Account subsection so initial-account flows open the correct empty state; the remaining 188 are in the States library. Eight prototype flow labels were made unique and clearer, retaining their IDs.

The organization write compared local geometry and reaction signatures for **355 original frames and 18 component/component-set objects**. Its result was **373 unchanged and zero changed**: original frame sizes and component contents matched that local comparison. Canvas grouping and placement changed; this result concerns preservation of the existing screen/component internals and reactions during that write.

The final organization audit passed: all 597 source objects were grouped into 29 sections with 11 at the top level, and all 355 original frame sizes were preserved. Across all 355 original frame roots, including the review index and phone proofs, **2,622 prototype links reached 318 unique destinations**, with zero missing/hidden destination findings and zero section-overflow or section-overlap findings. This broader organization audit has a different scope from the earlier visible-mobile-only audit.

## Working with the organized file

Use the relevant section and compare its light/dark pair. Keep new-account empty states separate from populated examples. Keep provider-dependent future states marked Planned, including Seryo processing examples. Grouping a screen does not enable its backend operation or approve its design.

Five active helpers have been updated to support screens and components inside sections:

- `scripts/figma_ui_auth_typography.js`
- `scripts/figma_ui_final_context_routes.js`
- `scripts/figma_ui_gap_routes_v2_3.js`
- `scripts/figma_ui_review_index.js`
- `scripts/figma_ui_student_nav_reflow.js`

Section-aware lookup must distinguish three different relationships:

1. **Page membership:** walk ancestors to the owning PAGE and confirm page `14:2`.
2. **Screen ownership:** find the nearest named mobile FRAME, including when that frame is inside a SECTION. Use this owner for self-navigation protection.
3. **Global discovery:** search descendants for exact screen/component names instead of assuming every object is a direct child of the page. Keep screen-local child queries local.

Historical builders may still assume `page.children` contains every screen or that `screen.parent` is the page. Update their ancestor/descendant lookup before reuse. Do not rerun a historical whole-canvas builder over the organized candidate: it can duplicate or overwrite current work.

## Source records

- [Canvas inventory before organization](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/docs/GoServe-figma-canvas-before-organization.json): the original object inventory.
- [Organization plan](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/scripts/figma_ui_organization_plan.json): the section and placement plan.
- [Organizer script](C:/Users/Rieecheeze/Documents/ChatGPT/GoServe/scripts/figma_ui_organize_canvas.js): the operation used to group the current page.

## Handoff status

The UI remains a review candidate. **User UI finalization is pending, and Android implementation remains on hold.** Figma grouping, editable phone proofs and local geometry comparisons do not prove Android device rendering, native text scaling, backend authorization or provider behavior.



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

