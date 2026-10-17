# GoServe foundation implementation plan

**Goal:** Deliver the Java/XML foundation requested first, ready to open in Android Studio, connected to the user-designated Supabase project.

**Architecture:** Two Activity hosts with lifecycle-aware fragments and ViewModels. Supabase Auth uses Google PKCE and assigned password accounts. Repositories keep network work off the main thread; the server assigns authority. Source specification: `docs/GoServe-architecture.md` and the supplied plan/Figma.

**Constraints:** GoServe name; Java 17; ConstraintLayout XML; light/dark Figma tokens; relative widths, scroll reflow and sp type; no Firebase; student/staff/teller roles; no password registration; no client payment approval. Scope of this initial delivery is the foundation, not all 38 integrated acceptance tests.

- [x] Read all 17 PDF pages and identify exact roles/status values, missing provider contracts and exclusions.
- [x] Inspect high-fidelity Figma login, email entry, consent, home, directory, settings and dark theme.
- [x] Build project configuration and Android manifest with portrait phone orientation and system insets.
- [x] Implement responsive XML, themes, Archivo headings, Java activities/fragments, Google PKCE/password authentication and encrypted session storage.
- [x] Implement typed domain models; first observe missing API failure, then pass exact-value/domain assertions.
- [x] Add office directory filtering/detail and explicit design preview. Keep unavailable feature routes clearly labeled.
- [x] Prepare and review minimal Supabase schema/RLS, privacy consent RPC and trusted user bootstrap.
- [x] Verify SDK license eligibility; build APK, run Java/domain and layout reflow checks, run Android lint.
- [x] Review results, fix authentication lifecycle and release-preview findings, record setup requirements and verification limits, prepare project/source delivery.

## Remaining feature packages

Requests/private files and retention, authoritative payments, staff operations, atomic consultation capacity, inquiries/notifications with Realtime, pickup token scanning/consumption, grounded Gemini and temporary analysis cleanup, and integrated AT-01 through AT-38 remain separate packages after this foundation. External provider values and official institutional policies are not invented.
