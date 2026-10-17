# GoServe current UI handoff

Updated 17 October 2026. **Ready for user UI review; UI approval is still pending.**

## Latest Version 2.3 handoff

Resumed 17 October. The [separate professor-facing wireframe page](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=305-2) contains 16 main panels and concise workflow notes. The [updated plan](GoServe-project-plan-v2.3.md) and 13-page PDF are ready. The [final overview handoff](GoServe-wireframe-overview-v2.3.md) records the panel inventory and checks. See [current implementation status](GoServe-implementation-progress-v2.3.md). UI approval is pending; no native feature implementation started during this revision. The 16 October saved state remains historical.

[Figma review index](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=175-7272) · [Seryo consent and attachment review](https://www.figma.com/design/SvT9gBUFjdLgbrJ80LPDno?node-id=289-9080)

## Completed design work

- Seryo has 19 paired scenarios: 38 Light/Dark phone panels and two transparent context-preserving tools overlays. The separate AI Upload page is gone; selected images/PDFs stay inside chat and its composer.
- Privacy includes Allow → empty allowed chat and Decline → AI off, with a route to reconsider. Normal campus navigation remains available.
- Picker cancellation, invalid files, removal/replacement, Explain/Extract drafts, working, retry, refusal, rate limit, unreadable text, results and cleanup outcomes have explicit review states. Explain/Extract prepares an unsent question.
- The plus menu contains Attach a file and AI privacy. Payment and named-office redirects appear as contextual buttons in replies.
- Long content uses bounded vertical scrolling. The chat composer and bottom navigation remain separate from the scrolling conversation.
- Raised cards, composer, menus and navigation use consistent theme-aware shadows. Two reusable component sets define 16 static field/action interaction variants.
- Earlier authentication work remains: Google or email, first-time terms/profile collection, password and confirmation, forgot-password flows, session restoration, Settings terms viewer, and empty new accounts. Settings icon spacing and the requested notice removals are retained.
- General state source contains 107 cases / 214 themed frames; 42 editable responsive proofs are indexed. G12 uses the dedicated inline Seryo renderer; regenerate it with `scripts/figma_seryo_inline_v2_5.js`.

## Verification

| Check | Result |
|---|---|
| Seryo flow + overlay + responsive acceptance | 38 flow frames, 8 new proofs, 2 overlays; 0 issues |
| New flow/proof text, sizing and navigation geometry | 46 frames; 0 issues |
| Long-panel audit | 468 phone/proof viewports; all enabled; 107 long bodies; 0 issues |
| Depth pass | 350 existing surfaces updated; surrounding geometry unchanged |
| Canvas/source audit | 859 source objects grouped; 470 frame dimensions preserved; 0 issues |
| Prototype links within mobile/proof/overlay frames | 2,990 links, 264 distinct valid destinations |
| New Seryo review links | 40 resolved destinations |
| Visible standalone AI Upload labels | 0 |
| Visual inspection | Light/Dark consent and image chat, OCR reply, enlarged-text attachment, field/action states passed |

Evidence: [verification JSON](GoServe-final-ui-verification-2026-10-16.json) · [Seryo flow and native checklist](GoServe-seryo-inline-attachment-flow.md) · [screen ledger](GoServe-seryo-screen-ledger.json).

## Before implementation

The user must finalize the UI before Android Java/XML coding begins. The Figma prototype demonstrates appearance and navigation; it does not create accounts, grant persisted consent, open a real OS picker, process a file, call a provider or verify server cleanup.

After UI approval, implement and test real validation, sessions, Google/email authentication and recovery, state preservation, scrolling with the keyboard, TalkBack, file validation, access controls and provider outcomes on devices. Provider transport stays disabled until approved contracts/procedures are supplied. Credentials remain server-side. Final institutional Terms/privacy wording still requires approval before release.

The reviewed design covers the declared UI scenarios. Native and provider tests can reveal implementation issues; the design review does not guarantee error-free code.

## Resume record

Latest: `docs/GoServe-resume-checkpoint-2026-10-16.json`. The 15 October State Save is historical and was resumed. No active save-state pause remains. No Android changes, provider calls, commits or deployment were performed for this revision.

