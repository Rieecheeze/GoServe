# Assigned-office request queue

Staff queue filters now query the server for All, Submitted, Processing or Ready. Each page keeps the assigned-office restriction, explicit columns,50-row bound and paired created_at/id cursor. Replies containing another selected status are rejected. Student history stays owner scoped and cannot select an office category.

Changing category clears its predecessor's rows and cursors and supersedes queued responses. Failed pagination retains the selected category and exact failed cursor; refresh/realtime invalidation starts at that category's head. Account loss clears the category and hides its controls.

The queue uses the saved Figma24:227 typography/spacing hierarchy, actual office label (or its ID while unavailable), selected-state tokens, horizontal48dp filters and Staff navigation. Empty copy refers to the selected status rather than falsely claiming the entire office has no requests. Canonical fee snapshots remain distinct from payment confirmation; no per-row fake payment/evidence labels were added.

Three repository and five state cases were added; seven observed RED while the negative owner guard already passed. The office binding observed RED before implementation. A large-text fixture was corrected to scroll vertically as well as horizontally and uses a synthetic ready Staff/Secretary profile for the actual five-destination role navigation. Focused97/3 suites pass. Full572 tests/48 suites,0failures/errors/skips, assembly and lint0errors99warnings pass in167seconds. Two visual batches cover light/dark and320×480 at2×.

Proof: output/validation/office-queue/validation.json. APK android/app/build/outputs/apk/debug/app-debug.apk,11,694,106bytes/SHA2566f9d99e497df9df8279cbb50f3f8d19869fc0646036eb18a999e9ae3e75eb712.

No live write/deployment, Terms activation or Git/GitHub work occurred. These are synthetic native checks; installed/live role workflows and staff-specific evidence, PDF and pickup integration remain open.
