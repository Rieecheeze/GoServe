> Historical pause snapshot. Work resumed after this save. Current status: Figma review candidate awaiting user finalization; Android implementation remains on hold. See [GoServe UI review](GoServe-ui-review.md) and the [Figma review index](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno/GoServe?node-id=175-7272).

# GoServe state save current UI phase

Paused immediately at the user's explicit save state request on15October2026. Do not continue until the user explicitly resumes.

## Governing workflow

The user has explicitly changed the order: revise and finalize the UI in FIGMA FIRST, review it together, and only AFTER UI finalization proceed with Android code. Android implementation, builds and backend feature development are on hold. Existing code written earlier remains in place; do not revert it, resume it, or claim it matches the final design yet.

Conversation: Caveman, concise but retain technical facts. UI skills: antislop during implementation (mode already chosen), antislop-ui, antislop-layoutmobile, frontend-design, impeccable at C:/Users/Rieecheeze/.agents/skills/impeccable/SKILL.md, unslop. Code skills apply when code resumes: karpathy-guidelines and antislop-code. All were read earlier. Impeccable context launcher was unavailable earlier; project/native guidance fallback was already used. Do not repeat mode/setup questions. Read the relevant Impeccable playbook and craft floor before UI edits; use bounded visual passes. Figma-use/generate-design/library are mandatory for relevant Figma operations. Native apply_patch persists file edits; context-mode processes large output.

## Latest user decisions

1. Brand GoServe, not iServe. Supabase only, no Firebase.
2. NEW FONT PAIR SUPERSEDES ARVO/LATO: Montserrat ExtraBold800 headings; Karla Regular400 body and SemiBold600 UI labels. Use actual font faces/weights.
3. Home header: supplied logo ONLY, no adjacent GoServe wordmark. Authentication loading still has the logo/name/status; the user removal request concerned the Home masthead.
4. Student navigation: Home, Requests, Consult, Inbox, Seryo AI Chat, More. Final direction is ICONS ONLY,56dp tall,22dp icons centered in40dp-high slots,36dp selected circle,48dp minimum whole touch targets. No empty label row. Accessible labels and long-press tooltips when implemented.
5. Navbar contrast: light mode uses dark #222626 capsule/light icons and white selected circle/dark selected icon. Dark mode uses light #F4F4F4 capsule/dark icons and dark selected circle/light selected icon.
6. Controls must be smaller and consistent: Search48px; filter pills32px visual inside48px tap target; See all is text-only64x48 target; single-line primary/secondary actions48px minimum, growing if text wraps.
7. Home/directory scroll, all five offices reachable; selected filters distinct. Payments lists Cashiering only; other offices send required payments to Cashiering.
8. Offices declare FREE/REQUIRED/PENDING fees. Free skips payment. Completed phases use green checks plus Complete, current/future distinct, initial display letters uppercase. Cards grow to fit copy.
9. NEW accounts have NO active-request card or fake requests. Preserve existing-request design as a populated-account variant. Other new-account data-empty states also need coverage.
10. Seryo IS the AI chatbot, using eGovAI. Main tab style follows user's Codex-style reference: open chat canvas, centered welcome, rounded composer anchored above navbar. No coding/model/voice controls without real functionality. Provider actions stay disabled until user supplies Seryo tasks/procedures and API contract.
11. User explicitly requested the skills to audit missing panels against the project plan and finalize the UI format before code.

## Figma current state

File key SvT9gBUFjdLgbrJ80LPDno; page14:2, currently named GoServe Android v2.1. Archive page0:1 left unchanged. Current core/new panels are native editable Frames/Text/Vectors/Instances, not flattened screenshots.

### Typography and palette

- Same nine GoServe text style IDs reused. Four heading roles now Montserrat/ExtraBold800; Body/Support Karla/Regular400; Label/Caption/Navigation Karla/SemiBold600.
- Figma exact available style names verified: Montserrat ExtraBold, Karla Regular, Karla SemiBold.
-748 current style-bound/Arvo/Lato text nodes migrated; later focused audit544visible/current text nodes found no height/bounds issues. Old Roboto/Archivo in other legacy/unrevised flows still exists and should be considered during the format audit; OS status-bar text may remain platform type. Do not claim every legacy panel is fully reformatted.
- GoServe Light/Dark variable collections and semantic colors exist. Critical resolved bug: binding a paint from black fallback left LIGHT backgrounds literally black in the live canvas even if export resolved the alias. Paint helpers now seed actual variable RGB BEFORE setBoundVariableForPaint. All updated roots/descendants audited; retain both literal color and binding.
- Critical resolved text bug: textAutoResize HEIGHT followed by resize(width,1) resets auto to NONE and creates1px text. Correct order is resize explicit width, then HUG/HEIGHT and characters. Use native auto-layout for related children.

### Main screens and states

| State | Light ID | Dark ID |
| --- | --- | --- |
| New-account Home default |19:93|36:93|
| Active-request Home preserved |127:1124|127:1329|
| Empty Requests default |23:32|36:377|
| Populated Requests preserved |127:1254|127:1459|
| Offices all five |33:54|36:1766|
| Documents Registrar only |45:2|46:2|
| Payments Cashiering only |45:54|46:30|
| Required/Paid/Processing request |23:95|36:440|
| Free/Ready request |106:662|107:1063|
| Pending office fee decision |106:663|107:1064|
| Verified file free preview |23:144|36:489|
| Sign-in |19:2|36:2|
| Seryo main chat |24:51|36:819|
| Seryo privacy |95:2|97:162|
| Seryo attachment pending |24:82|36:850|
| Seryo planned response |40:2|40:33|
| Seryo planned unavailable |24:111|36:879|
| Authentication loading |106:642|106:652|

New-account default Home removes the card without an empty slot: before latest control shrink body1442->1254 and office heading568->380; after compact Home controls body1234(default)/1422(active) was reported. Empty Requests says No requests yet and Browse offices, contains no fake request cards; compact320 andfont2 bounds passed. Populated examples have three request cases with correct detail routes. Root patched cloned active Home Requests tab to populated list and populated-list Home tab back to active Home in both themes, preserving prototype context. Original default navigation continues to empty roots.

### Seryo chat Figma contract

Source scripts/figma_seryo_chat_v2_3.js.390x844 reference. Header x20,y24,w350,minH64; title Seryo, small eGovAI setup status,48px Privacy action. Chat x20,y104,w350,h638 ending742. Welcome viewport468 high, centered310x284 group with muted AI icon, How can Seryo help you, campus guidance and Browse offices. Dock y588,h154; composer350x130, radius24, attachment48, disabled Send48, setup no-send note below. Navbar y758,h56,16px gap before it.

Light/dark node pairs: header123:1128/1162; chat123:1137/1171; welcome viewport123:1138/1172; welcome123:1140/1174; dock123:1148/1182; composer123:1149/1183; privacy target123:1132/1166; attach123:1153/1187; send123:1158/1192; Browse123:1145/1179. Privacy/attachment/Browse have real prototype destinations; Send has none. Native both-theme composition passed;320width bounds fixed with Browse FILL sizing. Atfont2 with conservative300dp IME reserve, welcome viewport can shrink to14dp: future enabled-input implementation must collapse welcome while keyboard is open. Current Figma input disabled pending provider setup. No Android implementation of this redesigned chat has been started.

### Navigation components

Light six main components: AI Chat95:12; Home98:322; Requests98:393; Consult98:464; Inbox98:535; More98:606.
Dark counterparts: AI Chat112:1275; Home112:1309; Requests112:1343; Consult112:1377; Inbox112:1411; More112:1445.
All live components now56px high. Labels remain as hidden design nodes, visible=false, to remove the label row. All tabs48high, icons22 center(24,20) inside48x40slot, circle36 same center; tab centers allow4vertical inset within48target. Instances at y758. Root scripts need a quick source/live consistency check if rerun, because several live refinements were applied after the original builder. AI links checked: Home/directory light->24:51 and dark->36:819. Main/data-state prototype links preserved.

### Latest reported flaws and fixes

1. Pending status supporting copy clipped: repaired cards19:321/36:319 in payment-pending roots19:312/36:310. Native vertical auto-layout, decorative dot absolute, title Pending, Karla label/body. Final card350x90, supporting copy40high fully visible with bottom padding. Visual check passed.
2. Directory buttons oversized: six directory Search controls were reduced, filters are now32px visible pills within48px transparent targets. Control script scripts/figma_goserve_controls_v2_3.js.
3. Staff queue row overflow/right edge: rows24:239/36:1007 now350x48; content-sized chips distribute remaining width; final right edge exactly350. Visual32px pills,48px targets. Visual check passed.
4. Home controls still inconsistent: agent just installed reusable components on all four default/active Homes. Search set132:1260 (Light variant132:1256, Dark132:1258); Filter set132:1273 with four theme/selection variants; InlineAction set132:1278. Search48; filter32visual/48target; See all64x48 text-only. All links preserved. Agent was normalizing16older owned actions to48 and reported all measured48.
5. Root normalized10Seryo auxiliary actions from56 to48; directory six Search frames got strokesIncludedInLayout=false to avoid an extra2px from the border. IDs107:749/801/829/1172/1224/1252. Last agent message said they were50px with strokesIncludedInLayout=true, BUT its snapshot preceded/overlapped root's successful false assignment. On resume MEASURE once before fixing again. Expected48, not yet freshly verified after root assignment.
6. Loading preview originally showed a pie/clock-shaped ellipse arc. Replaced with open32px SVG ring paths in both auth loading roots; source helper updated. New ring nodes123:1124/123:1126, old ellipse106:650/106:660 removed.

## Missing panel audit JUST completed

Read docs/GoServe-ui-panel-audit.md FIRST on resume. It is18.4KB and records148screenframes=74light+74dark from the audit snapshot, G01-G14 finite gaps, existing IDs and all38unique acceptance criteria mappings. This is a UI inventory, NOT proof those acceptance criteria pass in code.

Confirmed necessary gaps include:

- Clean new-account Inbox/Notifications/Appointments, role queue/history empties; current old screens mix populated rows and empty copy.
- Session-expired/access-denied recovery; directory search-empty/load-failure/retry.
- Student free/required/pending review and submission states; current old new-request CTA always goes Continue to payment choice.
- Staff-only optional fee declaration/editor (free/required/pending) with authorized office ownership.
- Student cancel with reason; staff reject/cancel and validation; paid-request manual Cashiering follow-up, no automatic refund.
- Payment failed/expired/cancelled/confirmed UI; teller no-result/duplicate/receipt states.
- Booked consultation detail, full-slot conflict, cancel and reschedule confirmation.
- File validation/private access states; readable pickup code and success/error states.
- Planned Seryo consent/file/busy/refusal/rate-limit UI, without inventing agent procedures or active provider results.
- Office publishing states and inquiry send/assignment states.

Existing broad primary screens and AI setup/privacy/upload/response/error/manual-camera fallback were credited rather than duplicated. Legacy OSA references outside the confirmed five-office scope were flagged for review. These missing groups have NOT yet been built in Figma. Finish the necessary panels and consistent format before presenting UI as complete. Do not add unsupported official fees, AI tasks, API fields or production-success claims.

## Files and agent ownership

- scripts/figma_goserve_v2_2.js: root helpers, Seryo old auxiliary panels, nav builders, auth loading. Updated font names, token fallback, compact actions and nav direction; do not rerun old main-chat builder over the new Seryo design.
- scripts/figma_goserve_controls_v2_3.js: root fixes status/dirfilters/queue.
- scripts/figma_goserve_content_v2_2.js: foundation_review content builder, current fonts and defaults; agent updating compact Home components/actions.
- scripts/figma_new_account_states.js: new/active account variants.
- scripts/figma_seryo_chat_v2_3.js: new Seryo chat design.
- docs/GoServe-figma-content-validation.md:20screen ledger, typography/new account/Seryo evidence; may need final compact-control notes.
- docs/GoServe-ui-panel-audit.md: newly completed14group missing-panel/AT map.
- docs/GoServe-ui-review.md and docs/GoServe-typography-and-brand.md: current decisions, but align final control sizing and explicitly keep UI-under-review status.
- Earlier docs/GoServe-state-save-2026-10-15.md is HISTORICAL and points at the UI-first workflow; do not follow its old code-first next steps.

All active agents were interrupted for this save. foundation_review was finishing compact-control QA. domain finished read-only audit. android_ui no longer had a live agent; its plan work is an unfinished draft.

## Android and server context retained for AFTER UI finalization

Workspace C:/Users/Rieecheeze/Documents/ChatGPT/GoServe. Java/XML project android, package ph.edu.tip.goserve, min26/target35, Gradle8.9/AGP8.7.3/Java17. Auth/consent/live office directory foundation and guarded request previews exist; many live operations remain planned. Native CapsuleNavigationView, request preview classes and old AssistantFragment setup form exist. The new chat design has NOT been implemented.

Before user switched to Figma-first, root downloaded official Google Fonts files montserrat_variable.ttf744936bytes and karla_variable.ttf94016bytes; added font-family XML resources montserrat_extra_bold800, karla_regular400, karla_semibold600 with pinned variation settings. styles.xml and TypographyFontTest were updated; licenses copied under third-party and assets/licenses. These changes are UNVERIFIED. Earlier Arvo-specific focused test passed before the new font switch; do not cite that as Montserrat proof.

Root also changed native navbar to icons-only56,22icons36circle, inverse dark resources and logo-only Home before workflow freeze. Source retained. SeryoInteractionTest.java persisted (4tests/35populated scenarios/capture helpers) but targets the old setup form and needs adapting only AFTER new UI approval. Home identity strings/catalog consistency test was written; any failure still needs investigation later.

Last earlier full verified build was28JUnit/0failures,105layoutscenarios,131domain assertions,lint0errors/38warnings, before latest changes. Domain preview workflow68new+131existing assertions had passed. Attempted root full build .tools/goserve-current-ui-build.log/session40098 returned exit1 when stopped/polled after user workflow change; cause was not inspected. No passing current combined gate. All builds now stopped/held. output/android/GoServe-foundation-debug.apk is OLD; no new APK delivery.

Build helper .tools/build-android.ps1 requires elevation for JVM sockets; Corretto21 C:/Program Files/Amazon Corretto/jdk21.0.12_12, SDK .tools/android-sdk, sockettemp .tools/tmp. No new SDK license accepted. Git remains uncommitted/unpushed with origin Rieecheeze/GoServe. No real device/emulator or real provider transactions verified.

Supabase project uuvexkewiuxdbuelwogy, URL https://uuvexkewiuxdbuelwogy.supabase.co. Corrected eGovPay token/template and supplied eGovAI key are saved encrypted in Vault, server-role-only RPCs. Boolean checks confirmed saved/restricted. eGovPay demo-only true/integration disabled pending callbacks/status/amount contracts; eGovAI Seryo procedures blank/integration disabled pending user tasks and provider contract. NEVER echo keys. No preset demo Auth accounts exist. No Firebase.

Versioned v2.2 project plan DOCX/Markdown/PDF drafts exist, preserving sections0-16 and AT01-38. Native Word export had produced a PDF; packaged render_docx lacked soffice.exe. All-page final visual QA/current-font updates/current-progress paragraph are unfinished. Do not deliver these as final, or claim current Android verification. Original v2.1 retained unchanged.

## Exact next steps after explicit resume

1. Stay in FIGMA-ONLY phase. Inspect latest compact control dimensions once (especially directory Search border50->48) and finish foundation_review's interrupted QA; preserve current approved choices and prototype state variants.
2. Read G01-G14 panel audit and add the necessary missing Figma states/dialogs/sheets using reusable controls and the current design system. Resolve legacy OSA scope; preserve existing valid panels and populated examples.
3. Standardize current panels: Montserrat/Karla, correct literal+bound theme colors, compact48actions/32filters, no clipped text/overflow, readable uppercase initial states, data-empty defaults, icons-only56nav. Use one batched visual pass and one concrete-fix pass; no open-ended polishing.
4. Present concrete Figma links/screenshots and the UI gap/format summary for the user's final UI review. Wait for finalization/confirmation before starting or continuing Android code.
5. Only after UI is finalized: implement approved Seryo/new-account/control/fee flows in Android, update meaningful tests, then build/test/lint and inspect native captures. Confirm provider instructions before enabling Seryo/eGovPay.
6. Revise project plan/guide/README with accurate implementation vs UI vs planned status and verified results; render/review final documents; package APK only after successful current gate.

STATE SAVED: AWAITING RESET.
