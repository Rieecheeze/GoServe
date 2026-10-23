# Teller confirmation and cash history

21 October 2026. This native slice passes403 tests in38 suites, debug assembly and lint with zero errors and88 warnings. The APK is `android/app/build/outputs/apk/debug/app-debug.apk`,11,628,597bytes, SHA-256 `17e1136ab34cb8e1a8930e63af27a320ee680923686e54c39cc24ec7f5151588`. Full evidence: `output/validation/teller-history/validation.json`.

Search loads a canonical payment and opens Pending with an unchecked receipt acknowledgement. Review confirmation changes only the local view state. Confirm as paid is the separate explicit write. Back from review clears acknowledgement; an uncertain retry keeps its operation identity. Paid lookups remain read-only. Realtime, tab changes and rotation never confirm a payment.

Search and Pending retain one selected payment in an account-pinned activity ViewModel. Empty Pending offers search rather than a fabricated queue. History uses bounded paid-cash pages and only a returned continuation cursor. Failed pagination retries the same cursor; refresh and account changes reject stale responses. Rows show canonical payment ID, exact amount/currency, actor ID and server time. A separate fragment-owned model resolves the canonical reference by payment ID without reading hidden requests or inventing display names.

Teller navigation now has Search, Pending, History and More. Existing destination IDs remain. The compact capsule is56dp with48dp touch targets,22dp icons and36dp selection; other roles retain their current navigation. The approved Figma Pending SVG and check/info/receipt glyph geometry are local Android assets. Export was read-only and changed no Figma nodes.

Buttons reuse `ui/SharedMotion.java`: scale to0.97 over80ms on press and return to1 over120ms on release. System-disabled animations remove the animator and keep scale1. Loading/reconnection feedback describes reads only. The reviewed receipt checkbox is rendered immediately in its checked state.

Focused cases: Teller model26, history model10, Teller interactions16, history interactions9 and navigation4. Initial missing behavior was observed failing before implementation. Two native visual batches caught and resolved an amount-color reset and a2x toolbar squeeze; the end of the warning is reachable above the fixed confirmation action. Render fixtures are synthetic Robolectric SDK34 graphics, including320×480 at2x text. They do not prove installed-device acceptance. The small confirmation image is a partial scrolled viewport, not a claim that all warning text fits simultaneously.

Lint advisories include whole-list refresh notifications, full-precision exported vector paths, root overdraw and now-unused placeholder resources. No performance benchmark or independent reviewer approval is claimed. Cash policies/catalog/fees and Terms/profile readiness remain configuration gates. This validation confirmed no live payment and performed no Git/GitHub task.
