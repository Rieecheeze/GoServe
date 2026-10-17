# GoServe State Save — 15 October 2026

Historical save point. Resumed on 16 October; the current UI work is complete for review. See GoServe-current-ui-handoff.md and GoServe-resume-checkpoint-2026-10-16.json for the latest state.

**Status: paused at user's explicit “save state” request. Resume only when the user says resume.**

## Latest task and boundary

Figma first. The user must finalize and approve the UI before Android Java/XML implementation. No Android code, provider calls, commits, pushes or deployment in this UI phase.

Latest requests still in progress:

- Remove separate Temporary file analysis / AI Upload screens. Photo/PDF attachment lives inside Seryo chat, with Explain and Extract text drafts.
- AI privacy needs Allow and Decline, with complete yes/no/reconsideration flows.
- Double-check missing panels before coding.
- Long panels must scroll.
- Add more effects/depth so the UI does not look flat. Use restrained raised surfaces, soft offset shadows and clearer focus/pressed states; preserve Montserrat/Karla, monochrome identity, light/dark contrast and plain chat replies.

App GoServe; chatbot Seryo. Menu contains only Attach a file and AI privacy. Office inquiries are separate. Payment intent shows Open Cashiering in the reply; named-office intent gives one-click native office redirect.

## Resume files

- Latest machine checkpoint: `docs/GoServe-resume-checkpoint-2026-10-15.json` (15 runtime data stores, current organization, general states, v2.4 evidence and v2.5 allocation).
- Latest Seryo ledger: `docs/GoServe-seryo-screen-ledger.json`.
- Flow specification: `docs/GoServe-seryo-inline-attachment-flow.md`.
- Conversation specification: `docs/GoServe-seryo-conversation-ui.md`.
- Builder ready: `scripts/figma_seryo_inline_v2_5.js` (25.6 KB; syntax previously passed; root removed forbidden setPluginData/getPluginData).
- Prior comprehensive auth checkpoint: `docs/GoServe-session-checkpoint-2026-10-15.json`. Historical, very large. Process with context-mode and print selected fields only.
- Prior current-ui-handoff/final-verification docs predate this unfinished inline revision. Do not claim their old passing evidence covers new states.

## Figma identity

File: https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno
Active page 14:2; review index175:7272. Archive0:1 untouched.
Seryo Section189:7634. Actual G12 Section**189:7822**.
**Important correction:** inline helper currently hardcodes section189:7777, which is G09. Fix helper to189:7822 on resume and reparent its14 newly allocated state roots from G09 before organization. Do not duplicate roots. Overlay roots were correctly allocated under Seryo.

## Exactly where work stopped

The Figma call already in flight when the user requested save completed **PHASE=create only**. It allocated14 blank state frames +2 blank overlay frames. **No v2.5 state was rendered; no overlay was filled.** Existing privacy/upload/error/G12 interiors remain old. New blank frames are not yet in the organization plan/review index.

PHASE=create result: errors[]; all38 mapped phone frames rendered=false.

New Light IDs:
- G12.picker274:8360
- G12.picker-cancelled274:8361
- G12.explain-draft274:8362
- G12.extract-draft274:8363
- G12.explain-result274:8364
- G12.extract-result274:8365
- G12.no-readable-text274:8366

New Dark IDs:
- G12.picker274:8367
- G12.picker-cancelled274:8368
- G12.explain-draft274:8369
- G12.extract-draft274:8370
- G12.explain-result274:8371
- G12.extract-result274:8372
- G12.no-readable-text274:8373

Blank tools overlays: Light274:8374; Dark274:8375.

Reused pairs to render:
- Privacy95:2 /97:162
- Allowed empty G12.1:145:1726 /145:1728
- AI off G12.2:145:1730 /145:1732
- PDF inline G12.3:145:1734 /145:1736
- Invalid G12.4:145:1738 /145:1740
- Busy G12.5:145:1742 /145:1744
- Refusal G12.6:145:1746 /145:1748
- Rate limit G12.7:145:1750 /145:1752
- Cleanup unresolved G12.8:145:1754 /145:1756
- Cleanup verified G12.9:145:1758 /145:1760
- Old upload24:82 /36:850 becomes inline image-ready chat; builder will rename roots shared/assistant-image-ready
- Old error24:111 /36:879 becomes retryable inline error

## Builder execution after resume

Use current Figma16 skill. Each use_figma call must set page14:2 first; skillNames includes figma-use,figma-generate-design,figma-generate-library. Helper itself sets page. Tool code max50KB; result max20KB. Return all affected IDs; store large raw results and expose counts only.

1. Fix section ID and safely move14 new roots from G09 toG12.
2. PHASE='render' + FLOW_KEYS batches4–6cases. All destinations already allocated. Re-running create is safe but unnecessary.
3. PHASE='overlay' after render. Transparent390×844 full-phone overlay uses explicit dim backdrop with CLOSE and menu Close with CLOSE. Never set readonly Frame.overlayPositionType/overlayBackground/overlayBackgroundInteraction.
4. Update active upload aliases and routes. Main initial Attach→privacy is already patched; consented overlay Attach→picker.
5. Review long panel scrolling/elevation as below.
6. Focused compact/enlarged proofs, geometry/routes/coverage audit, one composition screenshot batch; fix actual findings once, confirm once, stop.
7. Merge source metadata/review/organization/proofs/ledgers/handoff. UI approval remains false.

### Consent and attachment contracts

Allow→EMPTY allowed chat; never automatic picker/file selection/send. Decline→AI off; normal app navigation remains available; review privacy allows later.
Exactly one PDF/JPG/PNG≤10MB, explicitly selected after consent. Drafts say “Explain this file.” / “Extract text from this file.”
Explain/Extract prepare unsent draft. Send disabled until approved provider contract/procedures. Figma file picker is explicitly an example fixture; it does not read OS files.
No fake provider replies/timed success/history. Results use only actual supplied Settings screenshot text: “Settings” and “Light and dark appearance”. Off-canvas fixture labels required.
Unresolved cleanup blocks further AI processing; navigation must not clear the actual native block. Verified-cleanup UI requires server evidence when implemented.

## Scrolling/elevation steering — not yet applied

Existing safe structures:
- Auth scroll viewport→centered content→floating card.
- General Scrollable Android content→Hug content.
- Seryo / Scrollable chat viewport→Inline state content, with composer/nav siblings.
- GoServe tools / Scrollable menu viewport.

Read-only review confirmed v2.5 helper uses VERTICAL + clipsContent + FILL chat viewport and HUG growing state content. Long privacy/picker scroll by design. Tools menu has own bounded scroll viewport.

Outstanding risks:
- At320×720 /2× text, file chip + composer + setup note could consume the viewport. Verify viewport.height>0; refresh centered minHeight after resizing.
- Overlay roots remain390×844 until explicitly resized for proofs; child STRETCH constraints alone do not resize roots.
- All long main panels still need scoped audit; flag unsafe flat structures before wrapping rather than hiding content.

Suggested theme shadows (black, soft blur, offset):
- Inline image/document preview, system picker: offset0,2 blur8, opacity .06 Light/.18 Dark.
- Composer: offset0,4 blur16, opacity .10/.24.
- Existing tools component: offset0,8 blur24, opacity .14/.35.
- Auth floating card/nav/context cards/dialogs follow consistent restrained hierarchy. Preserve plain replies/bubbles without shadows, contrast and48dp touch targets.

Agent ui_gap_spec was building `scripts/figma_ui_scroll_elevation.js` but was **interrupted on save**; Test-Path=false. No such file existed at save. Follow up after resume or write it from safe structures above. Do not assume it is ready.

## Completed before unfinished v2.5

### Auth
78auth frames/39theme pairs;22recovery frames/11cases. Forgot password below Password in3paired login variants plus proofs.
Terms viewer213:7486 /213:7515 linked from all6Settings roles; BACK returns origin. Draft terms visible.
Registration Terms→credentials/password+confirmation→full scrollable profile→account created→explicit sign-in. Google→same profile completion. Password≥6characters including letter,number,symbol,match confirmation. First/middle(no-middle exception)/last/studentID/program/year/mobile/gender/email. Existing valid complete session routes dashboard. “Welcome” replaces “Welcome back.”
General canonical states100 /200theme frames; Android still held.

### Prior visual fixes
Removed all16requested Home/Requests demo-preview notices; source cleanup idempotent.
Fixed6Settings Appearance rows, and4More→Settings cards gear/text overlap. Screenshots/geometry passed.
Nav six destinations inclAI, compact centered; logo-only; Montserrat/Karla retained; light/dark contrasts.

### Seryo v2.4
Main empty24:51 /36:819.
Conversation40:2 /40:33.
Full-phone menu examples: Light254:8044(empty),254:8149(conversation); Dark254:8262(empty),254:8367(conversation).
Menu components254:7812 /254:7928,350×200; only Attach a file + AI privacy.
Student bubbles right; plain unboxed Seryo reply; scrollable thread; pinned composer; Send disabled.
Three exchanges: payments→Open Cashiering, declared free→skip payment guidance, Registrar→Open Registrar.
Office targets: Registrar19:175/36:173; StudentAccounting60:70/60:314; Cashiering60:2/60:246; Guidance60:104/60:348; CCS60:138/60:382.
Fixture annotations254:8480 /254:8481.
12current responsive Seryo proofs:257:7924,7995,8066,8145,8224,8295,8366,8445,8524,8633,8742,8851.
Deleted4superseded proofs170:7048,7118,7446,7516. Old original proof store may still contain them; filter before overall ledger merge.
v2.4 evidence: geometry22roots314texts0issues;8source+12proof acceptance0issues; global446roots793sources3104links398destinations,0bad routes/overflow. One duplicate generated Home caption189:7518 deleted.
**These checks do not cover the new blank/unrendered v2.5 states or upcoming effects.**

## Practical tool notes

- use_figma code plain top-level await/return, no IIFE. Load current fonts before text edit/reparent/remove. resize before FILL/HUG. minWidth/minHeight=null unsets; zero invalid.
- No setPluginData. No figma.notify/loadAllPagesAsync. Narrow node types before property access.
- Cache page nodes instead of hundreds of repeated page.findOne traversals.
- Return all affected IDs but expose summaries to model to avoid tooloutput limits.
- screenshot enableBase64Response:true; forward individual image blocks, never printbase64.
- context-mode for large JSON/file analysis. Do not dump historical checkpoint (≈198k tokens).
- Current workspace all untracked historically; no commit/push requested.

## Skills and agent state

Prior applicable skills already read/announced; use concise caveman communication. Impeccable context launcher previously failed, fallback announced; existing project context used. Most recent craft-floor and Figma effect-style API reread before last allocation. Do not rerun failed launcher.
ui_format_review completed helper and read-only scroll review. ui_gap_spec interrupted on save; no scroll/elevation file created.
No running functions cell remains. No new development permitted before resume.

STATE SAVED: AWAITING RESET.

