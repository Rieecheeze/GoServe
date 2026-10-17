# GoServe UI format review

October 15, 2026. Review and migration proposal only. No Figma or Android changes were made for this review.

## Scope

The user has selected Montserrat ExtraBold 800 for headings and Karla Regular 400 / SemiBold 600 for body and controls. Figma remains the deliverable until the user finalizes it. Preserve the current light/dark identity, compact controls, logo-only header, genuine new-account states, and six-destination icon navigation.

## Source findings

- `scripts/figma_iserve_android_v2_1.js:8` loads Archivo, Roboto and Material Symbols Rounded. Its text helper at line 43 defaults to Roboto; its hero and section headings use Archivo at lines 163 and 107. This is the historical generator, not the authority for current screen regeneration.
- Material Symbols text is icon content, not ordinary typography. Replacing its family with Karla would display glyph names such as `chevron_right` as words. Preserve these runs until a known vector equivalent is available. Do not invent a second icon style.
- Legacy buttons (lines 67–72), link rows (74–80), fields (88–95), choices (97–103), and status rows (112–117) use fixed-height/manual text positions. Replacing fonts alone cannot make them responsive. Their wrappers must grow with their text.
- `scripts/figma_goserve_v2_2.js:15` demonstrates the safe current text sequence: apply role style, resize to available width, append, set FILL/HUG, then enable `textAutoResize='HEIGHT'`. Resizing afterward to one pixel reintroduces the previously fixed clipping bug.
- `scripts/figma_goserve_content_v2_2.js:21` uses separate horizontal FIXED-width/AUTO-height and vertical AUTO-height/FIXED-width auto-layout modes. Follow this structure instead of the historical `hrow` sizing heuristic.
- `docs/GoServe-typography-and-brand.md` is the type-role authority. Use existing nine GoServe styles; do not create competing text styles.

## Surgical migration

1. Inventory active current-page screen roots and prototype destinations. Exclude archive page `0:1`, off-canvas annotations, hidden historical variants, OS chrome, and icon-font runs. Report mixed-font runs separately rather than overwriting them blindly.
2. Capture a manifest before mutation: root ID/name, node ID/name, characters, fonts, style ID, width/height, resize mode, parent layout mode, FILL/HUG, clipping, reactions and component identity. Keep the original root/node IDs where possible so existing links remain valid.
3. Match semantic roles from explicit node names and parent context. Hero/headline becomes Hero; app-bar title becomes Toolbar; section heading becomes Section; office/card heading becomes the existing card-title role; paragraphs become Body; helper/detail becomes Support; action/input label becomes Label; compact metadata becomes Caption. Do not classify a heading from size alone.
4. Convert fixed content wrappers before applying the new style. A link/status/choice row is a horizontal container with a growing vertical text column, fixed icon/indicator, adequate gap and 16-unit padding. Text width fills only its column. Actions and fields have 48-unit minimum targets and HUG height; important text is not ellipsized. A compact filter keeps its 32-unit visual pill inside a separate 48-unit target.
5. Preserve the original actionable wrapper and reactions. Reparent its content into a new inner stack if conversion is needed. Icons may be centered or absolute decorative children; they must not reserve a fixed text height.
6. Resize text to the available width before enabling automatic height. Apply the role, set characters, then reflow. Never set HUG before a later `resize(width,1)`. For nested FILL nodes, establish the fixed parent/column width first.
7. Preserve resolved semantic color bindings and their literal RGB seed. Typography work must not restore the earlier black-fallback light-theme bug.
8. Apply both themes in the same bounded batch. Return the changed node IDs, role counts, exceptions and reaction differences. Source changes should be a targeted current-page migration script, not a rerun of the historical whole-file builder or old Seryo main-screen builder.

Suggested helper contract: `migrateText(node, role)` accepts an allowlisted ordinary-content node; `growRow(wrapper, textColumn, fixedDecorations)` preserves wrapper identity; `assertFormat(root)` returns structured violations without silently shrinking type.

## Meaningful Figma QA

Use temporary clones on a dedicated QA area and remove them after recording results. Never resize the production root just to measure it. Clone at 390 and 320 units without using proportional scaling. Exercise text-scale factors 1 and 2. A smaller normal-height phone is also needed for long forms and docked actions.

For each clone:

1. Reflow the root, content width, column widths, header and scroll viewport at the target width. Recompute viewport top from the growing header and viewport bottom from the navigation/action dock. Do not scale icons, spacing, navigation or touch targets with text.
2. Multiply each ordinary text run's font size and fixed pixel line height from its original value. Support mixed runs with range setters. Do not repeatedly multiply an already-scaled value. Icon text and OS chrome are excluded. HUG paragraphs should recalculate height naturally.
3. Check every visible text node against its nearest non-scroll content wrapper, including padding. Check the rendered line count/natural height on a temporary same-width text clone where truncation or `NONE` resizing is present. Allow deliberate viewport clipping only when its body is vertically scrollable and the content remains reachable.
4. Check sibling overlap in auto-layout content; fixed icon overlap with text; negative available width; row content beyond either edge; final right padding; and action targets below 48 units in either dimension. Separate visual pill size from actual target size.
5. Check the final content/action is reachable through vertical scrolling and is not trapped below navigation. A body taller than its viewport is valid when overflow is vertical, content is HUG-height and bottom clearance is sufficient. Important text beyond a fixed, non-scrollable card is a defect.
6. Check role/style/font exceptions, hidden stale labels, label case, selected filter/nav contrast, completed-phase checks, and theme literal RGB versus semantic aliases. Preserve accessible names for all icon-only destinations in the handoff.
7. Check prototype graph destinations: no self-loop as a fake mutation, no missing Back route, no new-account path into populated samples, no student path into staff/teller controls, no unsupported OSA route.
8. Export representative light/dark pairs in one visual batch: longest office name, fee-pending status, request history, dense staff form, new-account empty state, and Seryo. Fix the recorded defects in one batch, confirm once, then stop.

For Seryo only, reserve a conservative keyboard region when evaluating the future enabled composer. At narrow width/large text collapse the welcome area while retaining the composer and escape route. Current disabled input does not open a keyboard. Figma font multiplication is a design stress test; it is not evidence of Android's nonlinear system font scaling or IME behavior.

## Office scope: OSA

The original plan generator's section 3.2 lists exactly five offices at `scripts/build_iserve_project_plan_v2_1.py:440`: Registrar, Student Accounting, Cashiering, Guidance and Counseling Center, and College of Computer Studies secretary. It does not authorize OSA.

The panel audit identifies active OSA references in Cashiering `60:2` and teller lookup `26:415`, plus standalone OSA office/request/payment frames `60:36`, `60:172`, `60:215` and dark mirrors.

Recommended action: remove OSA entries from active office/service choices and payment summaries; disconnect all prototype actions targeting OSA frames; preserve the original frames in an explicitly labeled out-of-scope archive section on the current page. Keep archive page `0:1` unchanged. Do not substitute a different office into an OSA request or payment record, because that fabricates ownership. Update any active route-source data that could recreate these links.

The active directory must still expose the five confirmed offices. Payments continues to show Cashiering only. OSA can be restored only if the user expands the project scope.

## Evidence limits

This review inspected local scripts, plan source and saved audits. It did not execute a live Figma scan, mutate Figma, run Android, or prove any backend/provider behavior. The root agent owns live Figma measurements and changes.
