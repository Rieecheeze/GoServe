# Canonical request list and detail

Production Requests now reads the signed-in Student's requests or an authorized Staff member's assigned-office queue. Department Staff requires Secretary capability. Debug examples remain explicitly separate. Ready-account and current-lease checks guard queued work, publication, navigation and account replacement.

The list distinguishes loading, successful empty, safe retry, cached rows, paired timestamp/ID pagination and unavailable accounts. Its scrolling header/footer keeps controls accessible on small screens. Cards show the actual service snapshot, office label (or canonical ID when public labels are unavailable), reference, status and exact decimal fee. No sample services or invented amounts enter production.

Detail reloads the exact scoped request and independently reads its payment. Payment confirmation never changes server request progress. Partial payment failures and failed refreshes cannot retain an unverified paid claim. Incompatible fee/workflow snapshots fail validation before rendering. Request/payment subscriptions only invalidate reads and close when the screen stops. Real file actions await private delivery integration.

Shared buttons use the existing 0.97 press scale over80ms and return over120ms; reduced-motion settings disable these transforms. The brand-only loading indicator rotates a90-degree arc once per1000ms, stops when hidden/detached and respects system animation settings. No loading mascot was added.

Full native gate:450 tests in41 suites, zero failures/errors/skips; debug APK assembly passed; lint0errors/95warnings;159seconds. APK11,645,614bytes, SHA-25684acf30c80a7c0771d0eee79130832305863f357c0303bc5b8325c7fc68f81f7. Detailed proof: output/validation/request-ui/validation.json. The15 UI cases run actual non-preview MainActivity with test-only synthetic Auth/server boundaries, never a production bypass. Two native render batches cover both themes, all fee variants and320×480/2× scrolling controls. These are SDK34 synthetic renders, not installed-device acceptance or live service/payment writes.

This completes the read-only request screens. The application is still in progress: catalogue and creation, office actions, payments/private delivery and remaining inquiry/consultation/assistant integration follow. The approved live backend and inactive Terms gates are unchanged. No Git/GitHub operation occurred.
