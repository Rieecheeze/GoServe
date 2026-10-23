# Native request actions

Request details now open a retained review/confirmation dialog. Staff can review processing/readiness updates for their assigned office. Owner cancellation and staff rejection/cancellation appear only when the same-version workflow options permit them; cancellation/rejection require a reason. The server remains authoritative for role, policy, payment and private deliverable checks. Completion is reserved for delivery operations.

Review performs no network write. Identical actor, scope, request version, target and reason retain the operation key. Explicit confirmation calls transition_request with its five exact arguments, validates the receipt, then reloads the request through the original owner/office scope. A later canonical status may supersede the receipt, but an older or mismatched response never becomes success.

The state machine prevents duplicate confirmation, freezes uncertain intents for exact replay, clears replaced accounts, and requires a fresh request after definite version/policy/payment/file denial. Post-write parsing/reload uncertainty preserves the operation. Editing a reason after failed option loading keeps retry available. The dialog only closes on canonical success or a safe user exit; an uncertain write cannot be edited or dismissed. Review text distinguishes an unsent review from a submitted/uncertain operation.

Changed files: domain/RequestData.java, data/RequestRepository.java, ui/RequestActionViewModel.java, ui/RequestActionDialog.java, ui/RequestDetailFragment.java and their XML/strings. SharedMotion supplies0.97 press scale over80ms and1.0 release over120ms, respecting reduced motion. Brand loading is reused; no loading mascot was added.

Repository12 and state12 cases observed RED before implementation. Six initial UI cases observed RED, then three capture/offline cases and two reproduced regressions covered truthful copy, large-text note placement and retained read retry. The final full gate passes559 tests/48 suites, zero failures/errors/skips, assembly and lint0errors/99warnings in173seconds. Two visual batches cover light/dark and320×480 at2×. Proof: output/validation/request-actions/validation.json and its PNGs.

APK: android/app/build/outputs/apk/debug/app-debug.apk,11,690,594bytes, SHA25639b73ff8dc0b46266a7169065c3f313e779d6b90f59dd996f4e3cc8f348ef4f5.

This is synthetic native verification, not installed/live role acceptance. No live mutation, backend deployment, Terms activation or Git/GitHub work occurred. Staff queue filters/review hierarchy, actual private evidence/PDF delivery, pickup and remaining Tasks5/6 are still required.
